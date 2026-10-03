package com.example.hospital.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.sqrt

data class HospitalSensorData(
    val headingDegrees: Float = 0f,
    val totalSteps: Int = 0,
    val stepEventTrigger: Long = 0L, // timestamp of last step to react to
    val pressureHpa: Float? = null,
    val floorChangeHint: Int = 0, // +1 if climbed up, -1 if went down based on barometer
    val isTracking: Boolean = false,
    val activeSensors: List<String> = emptyList()
)

/**
 * Manages all mobile sensors for real-time indoor navigation:
 * - Compass / Orientation: Rotation Vector (preferred) or Accelerometer + Magnetometer.
 * - Steps: ONE source, so a step is never counted twice: hardware Step Detector, else
 *   Linear Acceleration peaks, else raw Accelerometer peaks.
 * - Floor / Altitude: Barometer (Pressure Sensor) tracking elevation changes.
 * No GPS: indoors it jumps around and used to inject fake steps.
 */
class HospitalSensorManager(val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _sensorData = MutableStateFlow(HospitalSensorData())
    val sensorData: StateFlow<HospitalSensorData> = _sensorData.asStateFlow()

    // Sensors
    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearAccelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscopeSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val orientationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    // State calculations
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false

    private var stepSource: Int? = null // sensor type that counts steps
    // Accelerometer / Linear Acceleration fallback step detector
    private var lastAccelMagnitude = 9.8f
    private var lastStepTimeMs = 0L
    private var accelStepPeakDetected = false

    // Linear acceleration step detection
    private var linearStepTroughDetected = false
    private var lastLinearStepTimeMs = 0L

    // Barometer tracking
    private var baselinePressureHpa: Float? = null

    // Filtered heading
    private var currentFilteredHeading: Float = 0f
    private var stepSequenceNumber: Long = 0L

    fun startTracking() {
        if (sensorManager == null) return

        val active = mutableListOf<String>()

        // 1. Compass / Rotation
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            active += "Rotation Vector"
        } else if (accelerometerSensor != null && magneticSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            active += "Compass (Acc+Mag)"
        } else if (orientationSensor != null) {
            sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI)
            active += "Orientation Sensor"
        }

        // 2. Gyroscope for instantaneous rotational stability
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_UI)
            active += "Gyroscope"
        }

        // 3. Steps: first sensor that registers wins. Accelerometer is registered anyway for the compass fallback.
        stepSource = when {
            stepDetectorSensor != null && sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI) -> Sensor.TYPE_STEP_DETECTOR
            linearAccelSensor != null && sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_UI) -> Sensor.TYPE_LINEAR_ACCELERATION
            accelerometerSensor != null && sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI) -> Sensor.TYPE_ACCELEROMETER
            else -> null
        }
        when (stepSource) {
            Sensor.TYPE_STEP_DETECTOR -> active += "Step Detector"
            Sensor.TYPE_LINEAR_ACCELERATION -> active += "Linear Accel PDR"
            Sensor.TYPE_ACCELEROMETER -> active += "Accelerometer PDR"
        }

        // 4. Barometer for vertical floor elevation change detection
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            active += "Barometer"
        }

        _sensorData.update {
            it.copy(
                isTracking = true,
                activeSensors = active
            )
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        _sensorData.update { it.copy(isTracking = false) }
    }

    fun manualStepSimulation() {
        registerStep()
    }

    private fun registerStep() {
        stepSequenceNumber++
        _sensorData.update {
            it.copy(
                totalSteps = it.totalSteps + 1,
                stepEventTrigger = stepSequenceNumber
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                val deg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f
                updateHeading(deg)
            }

            Sensor.TYPE_ORIENTATION -> {
                if (rotationVectorSensor == null) {
                    val azimuth = (event.values[0] + 360f) % 360f
                    updateHeading(azimuth)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Angular speed around z-axis in rad/s
                val zRotSpeed = event.values[2]
                if (abs(zRotSpeed) > 0.05f) {
                    val degDelta = -Math.toDegrees(zRotSpeed.toDouble()).toFloat() * 0.02f
                    val fastDeg = (currentFilteredHeading + degDelta + 360f) % 360f
                    _sensorData.update { it.copy(headingDegrees = fastDeg) }
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, 3)
                hasAccelerometer = true
                if (hasMagnetometer && rotationVectorSensor == null) {
                    computeHeadingFromAccMag()
                }
                if (stepSource == Sensor.TYPE_ACCELEROMETER) detectStepFromAccelerometer(event.values)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (stepSource == Sensor.TYPE_LINEAR_ACCELERATION) detectStepFromLinearAcceleration(event.values)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, 3)
                hasMagnetometer = true
                if (hasAccelerometer && rotationVectorSensor == null) {
                    computeHeadingFromAccMag()
                }
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                registerStep()
            }

            Sensor.TYPE_PRESSURE -> {
                val hpa = event.values[0]
                val base = baselinePressureHpa ?: run {
                    baselinePressureHpa = hpa
                    hpa
                }
                // 1 floor change is ~3.5 meters ≈ 0.4 hPa drop (ascent) or rise (descent)
                val diff = base - hpa
                var floorDelta = 0
                if (diff > 0.35f) {
                    floorDelta = 1 // higher elevation (upstairs)
                } else if (diff < -0.35f) {
                    floorDelta = -1 // lower elevation (downstairs)
                }

                _sensorData.update {
                    it.copy(
                        pressureHpa = hpa,
                        floorChangeHint = floorDelta
                    )
                }
            }
        }
    }

    private fun computeHeadingFromAccMag() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val deg = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f
            updateHeading(deg)
        }
    }

    private fun updateHeading(rawDegrees: Float) {
        // Smooth exponential moving average across the 0/360 wrap
        var diff = (rawDegrees - currentFilteredHeading) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        currentFilteredHeading = (currentFilteredHeading + diff * 0.25f + 360f) % 360f

        _sensorData.update { it.copy(headingDegrees = currentFilteredHeading) }
    }

    private fun detectStepFromLinearAcceleration(values: FloatArray) {
        // Look at vertical or dominant linear acceleration
        val norm = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val now = SystemClock.uptimeMillis()

        if (norm > 1.8f && !linearStepTroughDetected && (now - lastLinearStepTimeMs) > 300) {
            linearStepTroughDetected = true
            lastLinearStepTimeMs = now
            registerStep()
        } else if (norm < 0.6f) {
            linearStepTroughDetected = false
        }
    }

    private fun detectStepFromAccelerometer(values: FloatArray) {
        val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
        val now = SystemClock.uptimeMillis()

        // Detect acceleration peak above 11.2 m/s^2 after falling below 9.2 m/s^2
        if (magnitude > 11.2f && !accelStepPeakDetected && (now - lastStepTimeMs) > 320) {
            accelStepPeakDetected = true
            lastStepTimeMs = now
            registerStep()
        } else if (magnitude < 9.4f) {
            accelStepPeakDetected = false
        }
        lastAccelMagnitude = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
