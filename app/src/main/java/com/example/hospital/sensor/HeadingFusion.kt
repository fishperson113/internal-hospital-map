package com.example.hospital.sensor

/**
 * Direction the user faces on the floor plan (0 = up, 90 = right, clockwise), always available,
 * standing still or walking, on or off a route.
 *
 *   plan heading = gyro yaw + offset
 *
 * - Absolute reference: the compass. offset follows (compass - [planNorthDeg] - yaw) slowly
 *   ([COMPASS_GAIN]), so short magnetic disturbances indoors (steel, elevators, equipment) are
 *   smoothed out while gyro drift is still corrected.
 * - Gyro (GAME_ROTATION_VECTOR, no magnetometer): instant, smooth turns.
 * - Route: walking straight along a leg nudges the offset to the leg's direction ([alignTo]),
 *   which also absorbs a slightly wrong [planNorthDeg] or a locally bent magnetic field.
 */
class HeadingFusion(private val planNorthDeg: Float = PLAN_NORTH_DEG) {
    companion object {
        /**
         * Calibration knob: compass reading (degrees) when facing "up" on the floor plan as the web
         * shows it on a desktop. 0 = placeholder until measured at the hospital.
         */
        const val PLAN_NORTH_DEG = 0f
        const val COMPASS_GAIN = 0.05f // per sensor update (~4 Hz): ~5 s to follow the compass
        const val ROUTE_GAIN = 0.1f    // per step while walking straight along the route
    }

    private var offset: Float? = null

    /** [compassDeg] absolute (null = no magnetometer), [yawDeg] gyro (null = no gyro). */
    fun update(compassDeg: Float?, yawDeg: Float?): Float? {
        if (yawDeg == null) return compassDeg?.let { norm(it - planNorthDeg) } // no gyro: compass only
        if (compassDeg != null) {
            val target = wrap(compassDeg - planNorthDeg - yawDeg)
            offset = offset?.let { it + wrap(target - it) * COMPASS_GAIN } ?: target
        }
        return offset?.let { norm(yawDeg + it) }
    }

    /** The user is walking straight along a route leg pointing [lineDeg] on the plan. */
    fun alignTo(lineDeg: Float, yawDeg: Float?) {
        val y = yawDeg ?: return
        val o = offset ?: return
        offset = o + wrap(lineDeg - y - o) * ROUTE_GAIN
    }

    private fun norm(deg: Float) = ((deg % 360f) + 360f) % 360f
}
