package com.example.hospital.sensor

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class IndoorPdrState(
    val position: Point2D,
    val floorLevel: Int,
    val segmentIndex: Int = 0,
    val hasArrived: Boolean = false
)

/**
 * Moves the user's dot along the route while directions are on (business rules: the dot starts on
 * the route start, slides along the line, never leaves it; floors change only by the step buttons).
 *
 * Distance comes from the step detector (meters walked). Turns from the gyroscope correct it
 * (map matching), so step-length errors do not pile up:
 * - Every real corner of the line (turn >= [MIN_TURN_DEG]) is a checkpoint.
 * - The dot never passes a corner until the phone has turned that way: it waits there.
 * - If the phone turns that way while the dot is still up to [SNAP_M] before the corner, the dot
 *   jumps to the corner (the user walked further than counted).
 * The gyro yaw is relative (no compass, no north needed); the reference direction of each leg is
 * learned from the first steps on it and follows slow gyro drift.
 */
class IndoorPdrTracker {
    companion object {
        const val MIN_TURN_DEG = 35f     // smaller bends are just followed
        const val TURN_TOLERANCE_DEG = 35f
        const val SNAP_M = 8f            // how far before a corner a turn still means "this corner"
        const val MERGE_M = 1.5f         // bends closer than this form one corner
    }

    private var route: HospitalRoute? = null
    private var mpp = 0.08f // meters per plan pixel
    private var segment = 0
    private var walkedPx = 0f
    private var walking = false // false while in the elevator / on the stairs (step "change floor")
    private var corners: List<Corner> = emptyList()
    private var nextCorner = 0
    private var legYaw: Float? = null

    fun initialize(route: HospitalRoute): IndoorPdrState? {
        this.route = route
        mpp = route.assumptions.metersPerPx.toFloat().coerceAtLeast(0.01f)
        return goToStep(0)
    }

    /**
     * The user walked [meters]. [yawDeg] = gyro heading (relative, clockwise), null = no turn
     * matching (no gyro, or the "+1 bước" test button).
     */
    fun onWalked(meters: Float, yawDeg: Float?): IndoorPdrState? {
        if (route == null || !walking || meters <= 0f) return null
        var target = walkedPx + meters / mpp
        val c = corners.getOrNull(nextCorner)
        if (c != null && yawDeg != null) {
            val ref = legYaw ?: yawDeg
            val turned = wrap(yawDeg - ref)
            if (abs(wrap(turned - c.turnDeg)) <= TURN_TOLERANCE_DEG && c.distPx - walkedPx <= SNAP_M / mpp) {
                // Turned onto the next leg: continue from the corner with this step.
                target = c.distPx + meters / mpp
                nextCorner++
                legYaw = yawDeg
            } else {
                if (target > c.distPx) target = c.distPx // not turned yet: wait at the corner
                // Learn / follow the leg direction while walking straight (not while turning).
                legYaw = if (abs(turned) < 20f) ref + turned * 0.2f else ref
            }
        }
        walkedPx = target
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
        corners = r.segments.getOrNull(segment)?.let { corners(it.points, MERGE_M / mpp) }.orEmpty()
        nextCorner = 0
        legYaw = null
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

/** A checkpoint on a polyline: [distPx] along it, turn in degrees (clockwise > 0). */
internal data class Corner(val distPx: Float, val turnDeg: Float)

/** Signed angle in (-180, 180]. */
internal fun wrap(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

/** Plan heading of a->b: 0 = up, 90 = right (clockwise, y grows down). */
private fun heading(a: Point2D, b: Point2D): Float =
    Math.toDegrees(atan2((b.x - a.x).toDouble(), (a.y - b.y).toDouble())).toFloat()

/** Real corners of [pts]: bends within [mergePx] are summed into one, then small turns dropped. */
internal fun corners(pts: List<Point2D>, mergePx: Float): List<Corner> {
    val p = pts.filterIndexed { i, q -> i == 0 || q != pts[i - 1] }
    val raw = mutableListOf<Corner>()
    var dist = 0f
    for (i in 1 until p.size) {
        dist += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y)
        if (i < p.size - 1) raw += Corner(dist, wrap(heading(p[i], p[i + 1]) - heading(p[i - 1], p[i])))
    }
    val merged = mutableListOf<Corner>()
    for (c in raw) {
        val last = merged.lastOrNull()
        if (last != null && c.distPx - last.distPx < mergePx) merged[merged.lastIndex] = Corner(last.distPx, wrap(last.turnDeg + c.turnDeg))
        else merged += c
    }
    return merged.filter { abs(it.turnDeg) >= IndoorPdrTracker.MIN_TURN_DEG }
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
