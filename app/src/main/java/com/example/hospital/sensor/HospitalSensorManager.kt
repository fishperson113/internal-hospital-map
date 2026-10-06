package com.example.hospital.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.hospital.domain.model.Positioning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

data class HospitalSensorData(
    val headingDegrees: Float? = null, // compass (absolute, magnetic); null = no magnetometer
    val yawDegrees: Float? = null,   // gyro heading (relative, continuous, clockwise) for turn matching
    val relAzimuthDegrees: Float? = null, // same gyro heading in [0, 360), not unwrapped: the RoNIN frame (web: sensor.raw)
    val totalSteps: Int = 0,         // steps that moved the dot
    val walkedMeters: Float = 0f,    // sum of their lengths
    val pressureHpa: Float? = null,  // smoothed barometer
    val isTracking: Boolean = false,
    val activeSensors: List<String> = emptyList(),
    val diag: SensorDiagnostics = SensorDiagnostics()
)

/** Raw counters for the on-site tuning panel (like phyphox). */
data class SensorDiagnostics(
    val accelHz: Int = 0,
    val accelSteps: Int = 0,      // own accelerometer detector
    val hwDetectorSteps: Int = 0, // TYPE_STEP_DETECTOR events: shown to compare only, never moves the dot
    val hwCounterSteps: Int = 0,  // TYPE_STEP_COUNTER since start: shown to compare only
    val lastStepM: Float = 0f
)

/**
 * Indoor motion from phone sensors only (no GPS):
 * - Steps: own accelerometer detector ([StepDetector]) only, the same as the web (pdr.js).
 *   Hardware STEP_COUNTER / STEP_DETECTOR are only counted for the diagnostics panel: they lag up to
 *   ~10 s and report in bursts, and no experiment showed adding them helps (the main Baseline error is
 *   step length, not step count; experiments/denoise in the web repo).
 * - Turns: GAME_ROTATION_VECTOR (gyro + accelerometer, no magnetometer, so steel and elevators do
 *   not bend it). Falls back to ROTATION_VECTOR.
 * - Compass: ROTATION_VECTOR, the absolute reference for [HeadingFusion].
 * - Barometer: smoothed pressure, used to suggest a floor change.
 *
 * Steps are counted only once [configureSteps] gave the server's constants (web: sensors ignored until
 * `positioning` arrives). RoNIN mode ([setRoninMode]): no step counting; accelerometer, gyro and game
 * rotation run at 200 Hz and every accelerometer sample becomes one row for /api/ronin ([drainRoninBatch]).
 */
class HospitalSensorManager(val context: Context) : SensorEventListener {
    companion object {
        // ponytail: 3 sensors x 200 Hz on the main thread; move to a HandlerThread if the UI stutters in RoNIN mode
        const val RONIN_PERIOD_US = 5_000     // 200 Hz
    }

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _sensorData = MutableStateFlow(HospitalSensorData())
    val sensorData: StateFlow<HospitalSensorData> = _sensorData.asStateFlow()

    private val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gameRotation = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotation = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val stepDetector = sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounter = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val pressure = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val gyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var detector: StepDetector? = null
    private val rot = FloatArray(9)

    // RoNIN mode: raw rows [t, ax, ay, az, ra, rb, rg, alpha, beta, gamma] for /api/ronin (format: backend ronin.js)
    private var roninMode = false
    private var gyroDeg: DoubleArray? = null // rotation rate around device z, x, y in deg/s (W3C rotationRate alpha, beta, gamma)
    private var ori: DoubleArray? = null     // game rotation as W3C deviceorientation alpha, beta, gamma (deg)
    private var roninBatch = ArrayList<DoubleArray>()

    // Steps. All callbacks run on the main thread.
    private var accelSteps = 0
    private var hwDetector = 0
    private var hwCounterBase = -1
    private var hwCounter = 0
    private var emitted = 0
    private var walked = 0f
    private var lastLen = 0f

    private var yaw: Float? = null
    private var relAzimuth: Float? = null
    private var lastRawYaw = 0f
    private var compass: Float? = null
    private var accelCount = 0
    private var accelHz = 0
    private var rateWindowMs = 0L
    private var lastPublishMs = 0L
    private var pressureHpa: Float? = null

    /** Safe to call again (e.g. after the ACTIVITY_RECOGNITION permission is granted). */
    fun startTracking() {
        val m = sm ?: return
        m.unregisterListener(this)
        val active = mutableListOf<String>()
        fun reg(s: Sensor?, rate: Int, name: String) {
            if (s != null && m.registerListener(this, s, rate)) active += name
        }
        // RoNIN: 5000 µs = 200 Hz, the rate the model was trained at (needs HIGH_SAMPLING_RATE_SENSORS on Android 12+)
        val fast = if (roninMode) RONIN_PERIOD_US else SensorManager.SENSOR_DELAY_GAME
        reg(accel, fast, "Accelerometer") // ~50 Hz in Baseline
        reg(gameRotation ?: rotation, fast, if (gameRotation != null) "Gyro" else "Rotation")
        if (roninMode) reg(gyro, RONIN_PERIOD_US, "Gyroscope")
        if (gameRotation != null) reg(rotation, SensorManager.SENSOR_DELAY_UI, "Compass")
        if (hasActivityPermission()) {
            reg(stepCounter, SensorManager.SENSOR_DELAY_NORMAL, "Step Counter")
            reg(stepDetector, SensorManager.SENSOR_DELAY_NORMAL, "Step Detector")
        }
        reg(pressure, SensorManager.SENSOR_DELAY_NORMAL, "Barometer")
        _sensorData.update { it.copy(isTracking = true, activeSensors = active) }
    }

    /** Server constants arrived (positioning.pdr.step): start counting steps with length factor [k]. */
    fun configureSteps(cfg: Positioning.StepConfig, k: Float) {
        detector = StepDetector(k, cfg)
    }

    /** Learned step length factor ([StepLearner]). */
    fun setStepK(k: Float) { detector?.k = k }

    /** RoNIN on: stop counting steps, stream raw samples at 200 Hz. Off: back to Baseline. */
    fun setRoninMode(on: Boolean) {
        if (roninMode == on) return
        roninMode = on
        roninBatch = ArrayList()
        if (_sensorData.value.isTracking) startTracking()
    }

    /** RoNIN rows since the last call (called at ~4 Hz). */
    fun drainRoninBatch(): List<DoubleArray> = roninBatch.also { roninBatch = ArrayList() }

    fun stopTracking() {
        sm?.unregisterListener(this)
        _sensorData.update { it.copy(isTracking = false) }
    }

    private fun hasActivityPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        val now = SystemClock.elapsedRealtime()
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelCount++
                if (now - rateWindowMs >= 1000) { accelHz = accelCount; accelCount = 0; rateWindowMs = now }
                if (roninMode) { // raw samples go to the server instead of the step detector
                    val g = gyroDeg
                    val o = ori
                    if (g != null && o != null) roninBatch.add(
                        doubleArrayOf(e.timestamp / 1e9, e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble(), g[0], g[1], g[2], o[0], o[1], o[2])
                    )
                    if (now - lastPublishMs > 250) publish(now)
                    return
                }
                val steps = detector?.onSample(e.timestamp, e.values[0], e.values[1], e.values[2]).orEmpty()
                for (len in steps) {
                    accelSteps++
                    emitted++
                    walked += len
                    lastLen = len
                }
                if (steps.isNotEmpty() || now - lastPublishMs > 250) publish(now)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val a = azimuth(e.values)
                yaw = unwrap(a)
                relAzimuth = (a + 360f) % 360f
                if (roninMode) ori = w3cOrientation() // rot was just filled by azimuth()
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroDeg = doubleArrayOf(deg(e.values[2]), deg(e.values[0]), deg(e.values[1]))
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                val a = azimuth(e.values)
                compass = (a + 360f) % 360f
                if (gameRotation == null) yaw = unwrap(a)
            }
            Sensor.TYPE_STEP_DETECTOR -> hwDetector++
            Sensor.TYPE_STEP_COUNTER -> {
                val v = e.values[0].toInt()
                if (hwCounterBase < 0) hwCounterBase = v
                hwCounter = v - hwCounterBase
            }
            Sensor.TYPE_PRESSURE -> {
                val p = e.values[0]
                pressureHpa = pressureHpa?.let { it + (p - it) * 0.1f } ?: p
            }
        }
    }

    private fun publish(now: Long) {
        lastPublishMs = now
        _sensorData.update {
            it.copy(
                headingDegrees = compass,
                yawDegrees = yaw,
                relAzimuthDegrees = relAzimuth,
                totalSteps = emitted,
                walkedMeters = walked,
                pressureHpa = pressureHpa,
                diag = SensorDiagnostics(accelHz, accelSteps, hwDetector, hwCounter, lastLen)
            )
        }
    }

    /**
     * Horizontal direction the phone points to (clockwise degrees): its top edge when held flat,
     * its back when held upright, whichever lies flatter, so both ways of holding it work.
     */
    private fun azimuth(v: FloatArray): Float {
        SensorManager.getRotationMatrixFromVector(rot, v)
        val topE = rot[1]; val topN = rot[4]
        val backE = -rot[2]; val backN = -rot[5]
        val useTop = topE * topE + topN * topN >= backE * backE + backN * backN
        val e = if (useTop) topE else backE
        val n = if (useTop) topN else backN
        return Math.toDegrees(atan2(e.toDouble(), n.toDouble())).toFloat()
    }

    /**
     * Game rotation -> W3C deviceorientation angles (degrees), R = Rz(alpha) Rx(beta) Ry(gamma),
     * from the rotation matrix [rot] (row-major, device -> world). Formula from ANDROID_INTEGRATION.md 7b.
     */
    private fun w3cOrientation() = doubleArrayOf(
        Math.toDegrees(atan2(-rot[1], rot[4]).toDouble()),
        Math.toDegrees(asin(rot[7].coerceIn(-1f, 1f)).toDouble()),
        Math.toDegrees(atan2(-rot[6], rot[8]).toDouble())
    )

    private fun deg(rad: Float) = Math.toDegrees(rad.toDouble())

    /** Continuous yaw (no jump at ±180). A single big jump (top/back axis switch) is ignored. */
    private fun unwrap(raw: Float): Float {
        val prev = yaw
        var d = raw - lastRawYaw
        lastRawYaw = raw
        if (prev == null) return raw
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return if (abs(d) > 90f) prev else prev + d
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
