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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.atan2

data class HospitalSensorData(
    val headingDegrees: Float? = null, // compass (absolute, magnetic); null = no magnetometer
    val yawDegrees: Float? = null,   // gyro heading (relative, continuous, clockwise) for turn matching
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
    val hwDetectorSteps: Int = 0, // TYPE_STEP_DETECTOR events
    val hwCounterSteps: Int = 0,  // TYPE_STEP_COUNTER since start
    val catchUpSteps: Int = 0,    // added because the hardware counted more
    val skippedSteps: Int = 0,    // dropped because the hardware counted fewer
    val lastStepM: Float = 0f
)

/**
 * Indoor motion from phone sensors only (no GPS):
 * - Steps: own accelerometer detector ([StepDetector]) is the main source: instant and not
 *   dependent on vendor step sensors. Hardware STEP_COUNTER (else STEP_DETECTOR) cross-checks it:
 *   if the hardware counted more, the missing steps are added; if it counted fewer once the user
 *   has stood still long enough for the counter to catch up (~10 s lag), the extra steps are
 *   skipped from the next ones.
 * - Turns: GAME_ROTATION_VECTOR (gyro + accelerometer, no magnetometer, so steel and elevators do
 *   not bend it). Falls back to ROTATION_VECTOR.
 * - Compass: ROTATION_VECTOR, the absolute reference for [HeadingFusion].
 * - Barometer: smoothed pressure, used to suggest a floor change.
 */
class HospitalSensorManager(val context: Context) : SensorEventListener {
    companion object {
        const val STEP_TOLERANCE = 2         // own vs hardware count difference that is ignored
        const val COUNTER_SETTLE_MS = 12_000L // STEP_COUNTER may lag up to ~10 s
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

    private val detector = StepDetector()
    private val rot = FloatArray(9)

    // Step fusion. All callbacks run on the main thread.
    private var accelSteps = 0
    private var hwDetector = 0
    private var hwCounterBase = -1
    private var hwCounter = 0
    private var lastOwnStepMs = 0L
    private var emitted = 0
    private var walked = 0f
    private var catchUp = 0
    private var skipped = 0
    private var skipDebt = 0
    private var lastLen = 0.65f

    private var yaw: Float? = null
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
        reg(accel, SensorManager.SENSOR_DELAY_GAME, "Accelerometer") // ~50 Hz
        reg(gameRotation ?: rotation, SensorManager.SENSOR_DELAY_GAME, if (gameRotation != null) "Gyro" else "Rotation")
        if (gameRotation != null) reg(rotation, SensorManager.SENSOR_DELAY_UI, "Compass")
        if (hasActivityPermission()) {
            reg(stepCounter, SensorManager.SENSOR_DELAY_NORMAL, "Step Counter")
            reg(stepDetector, SensorManager.SENSOR_DELAY_NORMAL, "Step Detector")
        }
        reg(pressure, SensorManager.SENSOR_DELAY_NORMAL, "Barometer")
        _sensorData.update { it.copy(isTracking = true, activeSensors = active) }
    }

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
                val steps = detector.onSample(e.timestamp, e.values[0], e.values[1], e.values[2])
                for (len in steps) {
                    accelSteps++
                    lastOwnStepMs = now
                    if (skipDebt > 0) { skipDebt--; skipped++ } else emit(len)
                }
                reconcile(now)
                if (steps.isNotEmpty() || now - lastPublishMs > 250) publish(now)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                yaw = unwrap(azimuth(e.values))
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                val a = azimuth(e.values)
                compass = (a + 360f) % 360f
                if (gameRotation == null) yaw = unwrap(a)
            }
            Sensor.TYPE_STEP_DETECTOR -> { hwDetector++; reconcile(now) }
            Sensor.TYPE_STEP_COUNTER -> {
                val v = e.values[0].toInt()
                if (hwCounterBase < 0) hwCounterBase = v
                hwCounter = v - hwCounterBase
                reconcile(now)
            }
            Sensor.TYPE_PRESSURE -> {
                val p = e.values[0]
                pressureHpa = pressureHpa?.let { it + (p - it) * 0.1f } ?: p
            }
        }
    }

    /** Hardware count: STEP_COUNTER is the most accurate once it has counted, else STEP_DETECTOR. */
    private fun hwSteps(): Int? = when {
        hwCounter > 0 -> hwCounter
        hwDetector > 0 -> hwDetector
        else -> null
    }

    private fun reconcile(now: Long) {
        val hw = hwSteps() ?: return
        val diff = hw - emitted
        if (diff > STEP_TOLERANCE) {
            // The accelerometer missed steps (odd carrying position): add them with the last length.
            skipDebt = 0
            val n = diff - STEP_TOLERANCE
            repeat(n) { emit(lastLen) }
            catchUp += n
        } else if (diff < -STEP_TOLERANCE && now - lastOwnStepMs > COUNTER_SETTLE_MS) {
            // Standing still and the hardware has settled with fewer: skip that many upcoming steps.
            skipDebt = maxOf(skipDebt, -diff - STEP_TOLERANCE)
        }
    }

    private fun emit(len: Float) {
        emitted++
        walked += len
        lastLen = len
    }

    private fun publish(now: Long) {
        lastPublishMs = now
        _sensorData.update {
            it.copy(
                headingDegrees = compass,
                yawDegrees = yaw,
                totalSteps = emitted,
                walkedMeters = walked,
                pressureHpa = pressureHpa,
                diag = SensorDiagnostics(accelHz, accelSteps, hwDetector, hwCounter, catchUp, skipped, lastLen)
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
