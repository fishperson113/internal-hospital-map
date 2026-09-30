package com.example.hospital.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
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
    val gpsLocation: Location? = null,
    val isTracking: Boolean = false,
    val activeSensors: List<String> = emptyList()
)

/**
 * Manages all mobile sensors for real-time indoor navigation:
 * - Compass / Orientation: Rotation Vector (preferred) or Accelerometer + Magnetometer.
 * - Steps: Hardware Step Detector / Step Counter + Fallback Accelerometer peak detection.
 * - Floor / Altitude: Barometer (Pressure Sensor) tracking elevation changes.
 * - Geolocation: GPS and Network location providers.
 */
class HospitalSensorManager(private val context: Context) : SensorEventListener, LocationListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _sensorData = MutableStateFlow(HospitalSensorData())
    val sensorData: StateFlow<HospitalSensorData> = _sensorData.asStateFlow()

    // Sensors
    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounterSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
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

    // Hardware Step Counter tracking
    private var initialStepCounterValue: Int? = null
    private var lastReportedStepCounterValue: Int? = null

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

    // Location distance tracking
    private var lastGpsLocation: Location? = null

    @SuppressLint("MissingPermission")
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

        // 3. Step Sensors
        var hardwareStepRegistered = false
        if (stepDetectorSensor != null) {
            hardwareStepRegistered = sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI)
            if (hardwareStepRegistered) active += "Step Detector"
        }
        if (stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI)
            active += "Step Counter"
        }

        // Linear Acceleration (gravity-removed) for precise step detection
        if (linearAccelSensor != null) {
            sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_UI)
            active += "Linear Accel PDR"
        }

        // Accelerometer fallback step detection
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            if (!hardwareStepRegistered && linearAccelSensor == null) {
                active += "Accelerometer PDR"
            }
        }

        // 4. Barometer for vertical floor elevation change detection
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            active += "Barometer"
        }

        // 5. GPS & Network Location
        registerLocationUpdates(active)

        _sensorData.update {
            it.copy(
                isTracking = true,
                activeSensors = active
            )
        }
    }

    fun refreshPermissions() {
        startTracking()
    }

    private fun registerLocationUpdates(activeList: MutableList<String>) {
        if (locationManager == null) return
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 1.5f, this)
                    activeList += "GPS"
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2500L, 3f, this)
                    activeList += "Network Location"
                }
            } catch (_: SecurityException) {
                // Handled gracefully
            }
        }
    }

    fun stopTracking() {
        sensorManager?.unregisterListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}

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
                // Fallback step detection using dynamic magnitude acceleration peaks
                detectStepFromAccelerometer(event.values)
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Values represent acceleration without gravity (x, y, z)
                detectStepFromLinearAcceleration(event.values)
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

            Sensor.TYPE_STEP_COUNTER -> {
                val currentCount = event.values[0].toInt()
                if (initialStepCounterValue == null) {
                    initialStepCounterValue = currentCount
                    lastReportedStepCounterValue = currentCount
                } else {
                    val delta = currentCount - (lastReportedStepCounterValue ?: currentCount)
                    if (delta in 1..20) {
                        lastReportedStepCounterValue = currentCount
                        repeat(delta) { registerStep() }
                    } else if (delta > 20) {
                        lastReportedStepCounterValue = currentCount
                        registerStep()
                    }
                }
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

    // LocationListener callbacks
    override fun onLocationChanged(location: Location) {
        val last = lastGpsLocation
        if (last != null) {
            val dist = location.distanceTo(last)
            // If GPS detects walking movement > 1.8 meters, trigger step update
            if (dist >= 1.8f && dist < 30f) {
                lastGpsLocation = location
                val stepsFromGps = (dist / 0.7f).toInt().coerceIn(1, 4)
                repeat(stepsFromGps) { registerStep() }
            }
        } else {
            lastGpsLocation = location
        }

        // Fuse GPS bearing if moving
        if (location.hasBearing() && location.hasSpeed() && location.speed > 0.6f) {
            updateHeading(location.bearing)
        }

        _sensorData.update { it.copy(gpsLocation = location) }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
