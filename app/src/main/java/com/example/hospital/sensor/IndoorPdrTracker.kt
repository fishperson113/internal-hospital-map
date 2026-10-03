package com.example.hospital.sensor

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

data class IndoorPdrState(
    val position: Point2D,
    val floorLevel: Int,
    val segmentIndex: Int = 0,
    val hasArrived: Boolean = false,
    val offRoute: Boolean = false // walking freely: the caller re-routes from [position]
)

/**
 * Moves the user's dot while directions are on (the dot starts on the route start; floors change
 * only by the step buttons). Input: meters walked + facing direction on the plan ([HeadingFusion]).
 *
 * On the route: distance from the step detector slides the dot along the line, and turns correct
 * it (map matching), so step-length errors do not pile up:
 * - Every real corner of the line (turn >= [MIN_TURN_DEG]) is a checkpoint.
 * - The dot never passes a corner until the user has turned that way: it waits there.
 * - If the user turns that way while the dot is up to [SNAP_M] before the corner, the dot jumps to it.
 * Turns are measured against the leg's reference direction: the leg's own direction on the plan,
 * then following the measured heading while walking straight (absorbs a small heading bias).
 *
 * Leaving the route (to meet someone, a wrong turn): walking [OFF_ROUTE_M] facing more than
 * [OFF_ROUTE_DEG] away from the leg where it has no matching corner, or [OVERSHOOT_M] straight past
 * a corner, switches to free walking: steps move the dot along the heading, in corridors only
 * ([walkable]: never through rooms; it slides along walls). [IndoorPdrState.offRoute] tells the
 * caller to re-route from there.
 */
class IndoorPdrTracker(
    /** Can a person stand at this plan point on this floor (corridor, inside the floor plan)? */
    private val walkable: (floorLevel: Int, p: Point2D) -> Boolean = { _, _ -> true }
) {
    companion object {
        const val MIN_TURN_DEG = 35f     // smaller bends are just followed
        const val TURN_TOLERANCE_DEG = 35f
        const val SNAP_M = 8f            // how far before a corner a turn still means "this corner"
        const val MERGE_M = 1.5f         // bends closer than this form one corner
        const val OFF_ROUTE_DEG = 45f    // facing this far from the line ...
        const val OFF_ROUTE_M = 4f       // ... for this many meters = left the route
        const val OVERSHOOT_M = 10f      // walked this far straight past a corner = left the route
    }

    private var route: HospitalRoute? = null
    private var mpp = 0.08f // meters per plan pixel
    private var segment = 0
    private var walkedPx = 0f
    private var walking = false // false while in the elevator / on the stairs (step "change floor")
    private var corners: List<Corner> = emptyList()
    private var nextCorner = 0
    private var legRef = 0f // reference direction of the current leg on the plan
    private var offMeters = 0f
    private var overshootPx = 0f
    private var free: Point2D? = null // off route: where the dot is

    fun initialize(route: HospitalRoute): IndoorPdrState? {
        this.route = route
        mpp = route.assumptions.metersPerPx.toFloat().coerceAtLeast(0.01f)
        return goToStep(0)
    }

    /**
     * The user walked [meters] facing [headingDeg] on the plan (0 = up, clockwise; null = unknown:
     * the dot only slides along the line).
     */
    fun onWalked(meters: Float, headingDeg: Float?): IndoorPdrState? {
        val r = route ?: return null
        if (!walking || meters <= 0f) return null
        free?.let { p ->
            if (headingDeg != null) free = move(p, headingDeg, meters, r.segments[segment].floorLevel)
            return state()
        }
        var target = walkedPx + meters / mpp
        if (headingDeg != null) {
            val turned = wrap(headingDeg - legRef)
            val c = corners.getOrNull(nextCorner)
            if (c != null && abs(wrap(turned - c.turnDeg)) <= TURN_TOLERANCE_DEG && c.distPx - walkedPx <= SNAP_M / mpp) {
                // Turned onto the next leg: continue from the corner with this step.
                target = c.distPx + meters / mpp
                nextCorner++
                legRef = lineAt(target) ?: headingDeg
                offMeters = 0f
                overshootPx = 0f
            } else {
                if (c != null && target > c.distPx) { // not turned yet: wait at the corner
                    overshootPx += target - c.distPx
                    target = c.distPx
                }
                // Follow the measured heading while walking straight (not while turning).
                if (abs(turned) < 20f) legRef += turned * 0.2f
                offMeters = if (abs(turned) > OFF_ROUTE_DEG) offMeters + meters else 0f
                if (offMeters >= OFF_ROUTE_M || overshootPx * mpp >= OVERSHOOT_M) {
                    walkedPx = target
                    return leaveRoute(headingDeg)
                }
            }
        }
        walkedPx = target
        return state()
    }

    /** Switch to free walking, replaying the meters walked since the user left the line. */
    private fun leaveRoute(headingDeg: Float): IndoorPdrState? {
        val seg = route?.segments?.getOrNull(segment) ?: return null
        val turnedAway = offMeters >= OFF_ROUTE_M // else: went on straight past a corner
        val replay = if (turnedAway) offMeters else overshootPx * mpp
        val departPx = if (turnedAway) max(0f, walkedPx - offMeters / mpp) else walkedPx
        free = move(pointAlong(seg.points, departPx).first, headingDeg, replay, seg.floorLevel)
        return state()
    }

    /** Walk [meters] along plan heading [deg] in small steps, sliding along walls, never into a room. */
    private fun move(from: Point2D, deg: Float, meters: Float, floor: Int): Point2D {
        val rad = Math.toRadians(deg.toDouble())
        val n = max(1, (meters / 0.25f).toInt())
        val dx = (meters / mpp * sin(rad)).toFloat() / n
        val dy = (-meters / mpp * cos(rad)).toFloat() / n
        var p = from
        repeat(n) {
            val stuckInside = !walkable(floor, p) // e.g. started at a room door: let it out
            p = listOf(Point2D(p.x + dx, p.y + dy), Point2D(p.x + dx, p.y), Point2D(p.x, p.y + dy))
                .firstOrNull { stuckInside || walkable(floor, it) } ?: p
        }
        return p
    }

    private fun lineAt(px: Float): Float? = route?.segments?.getOrNull(segment)?.points?.let { directionAt(it, px) }

    /** Direction of the route line under the dot while walking on it (for [HeadingFusion.alignTo]); else null. */
    fun lineHeading(): Float? = if (walking && free == null) lineAt(walkedPx) else null

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
        legRef = lineAt(walkedPx) ?: 0f
        offMeters = 0f
        overshootPx = 0f
        free = null
        return state()
    }

    private fun state(): IndoorPdrState? {
        val r = route ?: return null
        val seg = r.segments.getOrNull(segment) ?: return null
        if (seg.points.isEmpty()) return null
        free?.let { return IndoorPdrState(it, seg.floorLevel, segment, offRoute = true) }
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

/** Plan heading of the piece of [pts] at [dist] (the last piece past the end), null if no length. */
internal fun directionAt(pts: List<Point2D>, dist: Float): Float? {
    var left = dist
    var last: Float? = null
    for (i in 0 until pts.size - 1) {
        val len = hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        if (len == 0f) continue
        last = (heading(pts[i], pts[i + 1]) + 360f) % 360f
        if (left <= len) return last
        left -= len
    }
    return last
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
