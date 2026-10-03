package com.example.hospital.sensor

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import kotlin.math.hypot

data class IndoorPdrState(
    val position: Point2D,
    val floorLevel: Int,
    val segmentIndex: Int = 0,
    val hasArrived: Boolean = false
)

/**
 * Moves the user's dot along the route while directions are on.
 * - Start: the first point of the route (= the start place the user picked).
 * - Each detected step slides the dot [STEP_LENGTH_M] further along the current floor's polyline;
 *   the compass is not used, so the dot never leaves the route. It stops at the end of the segment.
 * - Floor change only when the user presses "Bước tiếp theo" / back: [goToStep] jumps the dot
 *   to the shaft (end of segment 0) or to the start of segment 1 on the new floor.
 */
class IndoorPdrTracker {
    companion object {
        // Calibration knob: average walking step. Tune if the dot runs ahead of / behind people.
        const val STEP_LENGTH_M = 0.65f
    }

    private var route: HospitalRoute? = null
    private var segment = 0
    private var walkedPx = 0f
    private var walking = false // false while in the elevator / on the stairs (step "change floor")

    fun initialize(route: HospitalRoute): IndoorPdrState? {
        this.route = route
        return goToStep(0)
    }

    fun onStep(): IndoorPdrState? {
        val r = route ?: return null
        if (!walking) return null
        walkedPx += STEP_LENGTH_M / r.assumptions.metersPerPx.toFloat().coerceAtLeast(0.01f)
        return state()
    }

    /**
     * Steps come from RouteStepFormatter: same floor = [walk to dest];
     * with a transfer = [walk to shaft, change floor, walk to dest].
     */
    fun goToStep(step: Int): IndoorPdrState? {
        val r = route ?: return null
        val atShaft = r.transfer != null && step == 1
        segment = if (r.transfer != null && step >= 2) 1 else 0
        walking = !atShaft
        walkedPx = if (atShaft) Float.MAX_VALUE else 0f // shaft = end of the first segment
        return state()
    }

    private fun state(): IndoorPdrState? {
        val r = route ?: return null
        val seg = r.segments.getOrNull(segment) ?: return null
        if (seg.points.isEmpty()) return null
        val (p, atEnd) = pointAlong(seg.points, walkedPx)
        return IndoorPdrState(p, seg.floorLevel, segment, hasArrived = atEnd && segment == r.segments.lastIndex)
    }
}

/** Point at [dist] plan pixels along [pts], clamped to the last point; second = reached the end. */
internal fun pointAlong(pts: List<Point2D>, dist: Float): Pair<Point2D, Boolean> {
    var left = dist
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        val len = hypot(b.x - a.x, b.y - a.y)
        if (left <= len) {
            val t = if (len == 0f) 0f else left / len
            return Point2D(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) to false
        }
        left -= len
    }
    return pts.last() to true
}
