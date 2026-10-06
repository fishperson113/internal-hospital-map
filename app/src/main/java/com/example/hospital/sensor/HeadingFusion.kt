package com.example.hospital.sensor

import com.example.hospital.domain.model.Positioning
import kotlin.math.abs

/**
 * Direction the user faces on the floor plan (0 = up, 90 = right, clockwise). Web: PDR.headingFusion.
 *
 *   plan heading = gyro yaw + offset
 *
 * Gyro yaw is smooth but relative; the compass is absolute but bent indoors by steel and machines
 * (13 to 96 µT measured on site, Earth is ~40). So the compass is filtered three ways:
 *  1. warm-up: the first WARMUP compass samples are averaged to set the offset fast;
 *  2. then the compass only nudges the offset (COMPASS_GAIN), the route line does most of the work ([alignTo]);
 *  3. gate: a compass sample that disagrees with the gyro by more than GATE_DEG is a magnetic disturbance
 *     and is dropped. If it keeps disagreeing for RELOCK samples, the gyro is the one that drifted:
 *     warm up again from the compass.
 * [update] is called at ~4 Hz. [planNorthDeg] = compass reading when facing the TOP of the plan
 * (positioning.plan_north_deg from the server).
 */
class HeadingFusion(private val planNorthDeg: Float, private val cfg: Positioning.FusionConfig) {
    private var offset: Float? = null
    private var n = 0
    private var disagree = 0

    /** Compass samples thrown away as disturbances (shown in the telemetry line). */
    var dropped = 0
        private set

    /** [compassDeg] absolute (null = no magnetometer), [yawDeg] gyro (null = no gyro). */
    fun update(compassDeg: Float?, yawDeg: Float?): Float? {
        if (yawDeg == null) return compassDeg?.let { norm(it - planNorthDeg) } // no gyro: compass only
        if (compassDeg != null) {
            val target = wrap(compassDeg - planNorthDeg - yawDeg)
            val o = offset
            val err = if (o == null) 0f else wrap(target - o)
            when {
                o == null || n < cfg.warmup -> { n++; offset = (o ?: target) + err / n; disagree = 0 }
                abs(err) > cfg.gateDeg -> { dropped++; if (++disagree >= cfg.relock) { n = 0; disagree = 0 } }
                else -> { offset = o + err * cfg.compassGain; disagree = 0 }
            }
        }
        return offset?.let { norm(yawDeg + it) }
    }

    /** The user is walking straight along a route leg pointing [lineDeg] on the plan. */
    fun alignTo(lineDeg: Float, yawDeg: Float?) {
        val y = yawDeg ?: return
        val o = offset ?: return
        offset = o + wrap(lineDeg - y - o) * cfg.routeGain
    }
}

/** Angle in [0, 360). */
internal fun norm(deg: Float) = ((deg % 360f) + 360f) % 360f
