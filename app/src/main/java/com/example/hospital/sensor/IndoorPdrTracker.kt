package com.example.hospital.sensor

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Positioning
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class IndoorPdrState(
    val position: Point2D,
    val floorLevel: Int,
    val segmentIndex: Int = 0,
    val hasArrived: Boolean = false,
    val offRoute: Boolean = false, // walking freely: the caller re-routes from [position]
    val backward: Boolean = false  // walking back the way the user came: the dot slides back
)

/** A leg closed at a corner: its real length on the route and the length counted from steps. */
data class WalkedLeg(val realM: Float, val countedM: Float)

/**
 * Follows the user along the route line (web: PDR.tracker in src/web/pdr.js, same rules and numbers).
 * The dot slides on the line, waits at a corner until the user turns, slides back when the user walks
 * back the same corridor, and leaves the line (free walking, corridors only) when the user clearly goes
 * another way. Floors change only through [goToStep] (the step buttons).
 *
 * Scale per floor and per axis: route.assumptions.pxPerM[floorId] = (sx, sy); old servers send only
 * metersPerPx. [walkable] = true in a corridor.
 */
class IndoorPdrTracker(
    private val route: HospitalRoute,
    private val cfg: Positioning.TrackerConfig,
    private val walkable: (floorLevel: Int, p: Point2D) -> Boolean = { _, _ -> true }
) {
    private val segs = route.segments
    private var segment = 0
    private var walkedPx = 0f
    private var walking = false // false while in the elevator / on the stairs (step "change floor")
    private var corners: List<Corner> = emptyList()
    private var nextCorner = 0
    private var legRef = 0f     // reference direction of the current leg on the plan
    private var offMeters = 0f
    private var overshootPx = 0f
    private var free: Point2D? = null // off route: where the dot is
    private var legStartPx = 0f       // INF = leg unknown (walked blind or backward): it teaches nothing
    private var legM = 0f             // metres counted from steps since legStartPx
    private var cornerPx = 0f         // the last corner passed: the dot slides back no further
    private var backward = false

    private val seg get() = segs[segment]
    private val pts get() = seg.points

    init { goToStep(0) }

    private fun scale(): Pair<Float, Float> =
        route.assumptions.pxPerM[seg.floorId] ?: (1f / route.assumptions.metersPerPx.toFloat()).let { it to it }

    /** Plan px per metre when walking in plan direction [h] (degrees). */
    private fun ppm(h: Float): Float {
        val (sx, sy) = scale()
        val r = Math.toRadians(h.toDouble())
        return hypot(sx * sin(r), sy * cos(r)).toFloat()
    }
    private fun ppmAt(d: Float) = ppm(directionAt(pts, d) ?: 0f)
    private fun meanPpm() = scale().let { (it.first + it.second) / 2f }

    /** Metres along the route between two distances (px), piece by piece. */
    private fun metersAlong(from: Float, to: Float): Float {
        var m = 0f
        var d = 0f
        for (i in 1 until pts.size) {
            val p = pts[i - 1]
            val q = pts[i]
            val len = dist(p, q)
            val lo = max(from, d)
            val hi = min(to, d + len)
            if (hi > lo) m += (hi - lo) / ppm(heading(p, q))
            d += len
        }
        return m
    }

    /** Route step -> segment. With a transfer: 0 = walk seg 0, 1 = at the shaft (steps do not move), 2 = walk seg 1. */
    fun goToStep(step: Int): IndoorPdrState {
        val atShaft = route.transfer != null && step == 1
        segment = (if (route.transfer != null && step >= 2) 1 else 0).coerceAtMost(segs.lastIndex)
        walking = !atShaft
        walkedPx = if (atShaft) Float.POSITIVE_INFINITY else 0f // shaft = end of the first segment
        corners = corners(pts, cfg.mergeM * meanPpm(), cfg.minTurn)
        nextCorner = 0
        legRef = directionAt(pts, walkedPx) ?: 0f
        offMeters = 0f; overshootPx = 0f; free = null
        legStartPx = 0f; legM = 0f; cornerPx = 0f; backward = false
        return state()
    }

    /** Walk from a point, in small sub-steps; blocked by a room, slide along its wall. Inside a room (the start), any move is allowed. */
    private fun move(from: Point2D, h: Float, meters: Float): Point2D {
        val n = max(1, (meters / 0.25f).toInt())
        val (sx, sy) = scale()
        val r = Math.toRadians(h.toDouble())
        val dx = (meters * sx * sin(r)).toFloat() / n
        val dy = (-meters * sy * cos(r)).toFloat() / n
        val floor = seg.floorLevel
        var p = from
        repeat(n) {
            val stuck = !walkable(floor, p)
            p = listOf(Point2D(p.x + dx, p.y + dy), Point2D(p.x + dx, p.y), Point2D(p.x, p.y + dy))
                .firstOrNull { stuck || walkable(floor, it) } ?: p
        }
        return p
    }

    private fun leaveRoute(heading: Float) {
        val turnedAway = offMeters >= cfg.offRouteM
        val back = ppmAt(walkedPx - EPS) // px per metre on the piece the dot is on
        val replay = if (turnedAway) offMeters else overshootPx / back
        val departPx = if (turnedAway) max(0f, walkedPx - offMeters * back) else walkedPx
        free = move(pointAlong(pts, departPx).first, snapToAxis(heading), replay) // redo the off-route part from where it began
    }

    /** Corridors run along the plan axes. Within AXIS_SNAP_DEG of one, walk exactly along it. */
    internal fun snapToAxis(h: Float): Float {
        val a = (h / 90f).roundToInt() * 90f
        return if (abs(wrap(h - a)) <= cfg.axisSnapDeg) norm(a) else h
    }

    /**
     * One step of [meters]; [heading] = plan degrees, or null when unknown.
     * Returns a [WalkedLeg] when a corner closes a leg (feed it to [StepLearner]), else null.
     */
    fun onWalked(meters: Float, heading: Float?): WalkedLeg? {
        if (!walking || meters <= 0f) return null
        // off the route: walk along the corridor axis when the heading is close to one (compass noise)
        free?.let { p -> if (heading != null) free = move(p, snapToAxis(heading), meters); return null }
        var target = walkedPx + meters * ppmAt(walkedPx)
        var leg: WalkedLeg? = null
        // a U-turn the route itself makes (a 180° corner just ahead) is not walking back
        val c0 = corners.getOrNull(nextCorner)
        val atCorner = heading != null && c0 != null && abs(wrap(wrap(heading - legRef) - c0.turnDeg)) <= cfg.turnTol &&
            c0.distPx - walkedPx <= cfg.snapM * ppmAt(walkedPx)
        if (heading != null && !atCorner && abs(wrap(heading - legRef)) >= cfg.backDeg) {
            // walking back the same corridor: slide back along the line, not forward. This leg no longer teaches
            // the step length. Past the last corner the user goes elsewhere: wait there, then free walking.
            backward = true; overshootPx = 0f; legStartPx = Float.POSITIVE_INFINITY
            val back = walkedPx - meters * ppmAt(walkedPx - EPS)
            if (back >= cornerPx) { walkedPx = back; offMeters = 0f; return null }
            offMeters += max(0f, (cornerPx - back) / ppmAt(walkedPx - EPS))
            walkedPx = cornerPx
            if (offMeters >= cfg.offRouteM) free = move(pointAlong(pts, cornerPx).first, snapToAxis(heading), offMeters)
            return null
        }
        backward = false
        if (heading != null) {
            val turned = wrap(heading - legRef)
            val c = corners.getOrNull(nextCorner)
            if (c != null && abs(wrap(turned - c.turnDeg)) <= cfg.turnTol && c.distPx - walkedPx <= cfg.snapM * ppmAt(walkedPx)) {
                // turned at the corner: the leg since the last corner is done; jump to the corner and keep going
                if (legStartPx.isFinite()) leg = WalkedLeg(metersAlong(legStartPx, c.distPx), legM)
                legStartPx = c.distPx; cornerPx = c.distPx; legM = 0f
                target = c.distPx + meters * ppmAt(c.distPx)
                nextCorner++
                legRef = directionAt(pts, target) ?: heading
                offMeters = 0f; overshootPx = 0f
            } else {
                if (c != null && target > c.distPx) { overshootPx += target - c.distPx; target = c.distPx } // not turned yet: wait at the corner
                if (abs(turned) < 20f) legRef += turned * 0.2f // follow the measured heading while walking straight
                offMeters = if (abs(turned) > cfg.offRouteDeg) offMeters + meters else 0f
                if (offMeters >= cfg.offRouteM || overshootPx / ppmAt(target - EPS) >= cfg.overshootM) {
                    walkedPx = target
                    leaveRoute(heading)
                    return null
                }
            }
        } else {
            // no heading: corners passed blindly must not hold the dot back once a heading shows up.
            // The leg is then unknown, so the next corner does not teach the step length.
            while (true) {
                val c = corners.getOrNull(nextCorner) ?: break
                if (c.distPx >= target) break
                cornerPx = c.distPx; nextCorner++; legStartPx = Float.POSITIVE_INFINITY
            }
        }
        legM += meters
        walkedPx = target
        return leg
    }

    fun state(): IndoorPdrState {
        free?.let { return IndoorPdrState(it, seg.floorLevel, segment, offRoute = true) }
        val (p, atEnd) = pointAlong(pts, walkedPx)
        return IndoorPdrState(p, seg.floorLevel, segment, hasArrived = atEnd && segment == segs.lastIndex, backward = backward)
    }

    /** Direction of the route line under the dot while walking on it (pulls the heading to the route); else null. */
    fun lineHeading(): Float? = if (walking && free == null) directionAt(pts, walkedPx) else null

    /**
     * Direction of the piece the dot is on (turns the map during directions). While the dot waits at a
     * corner it stays on the old piece, so the map turns when the user turns, not before. Null off the
     * route or at a shaft: the caller keeps the last direction.
     */
    fun courseHeading(): Float? = if (walking && free == null) directionAt(pts, max(0f, walkedPx - EPS)) else null

    private companion object {
        const val EPS = 1e-3f // px: "just before" a distance, to read the piece the dot is on
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

private fun dist(a: Point2D, b: Point2D) = hypot(b.x - a.x, b.y - a.y)

/** Plan heading of a->b: 0 = up, 90 = right (clockwise, y grows down). */
private fun heading(a: Point2D, b: Point2D): Float =
    Math.toDegrees(atan2((b.x - a.x).toDouble(), (a.y - b.y).toDouble())).toFloat()

/** Turns along [pts]: vertices closer than [mergePx] are merged, then turns under [minTurn] dropped. */
internal fun corners(pts: List<Point2D>, mergePx: Float, minTurn: Float): List<Corner> {
    val q = pts.filterIndexed { i, p -> i == 0 || p != pts[i - 1] }
    val out = mutableListOf<Corner>()
    var d = 0f
    for (i in 1 until q.size - 1) {
        d += dist(q[i - 1], q[i])
        val turn = wrap(heading(q[i], q[i + 1]) - heading(q[i - 1], q[i]))
        val prev = out.lastOrNull()
        if (prev != null && d - prev.distPx < mergePx) out[out.lastIndex] = Corner(prev.distPx, wrap(prev.turnDeg + turn))
        else out += Corner(d, turn)
    }
    return out.filter { abs(it.turnDeg) >= minTurn }
}

/** Plan heading of the piece of [pts] that contains distance [at] (past the end: the last piece), null if no length. */
internal fun directionAt(pts: List<Point2D>, at: Float): Float? {
    var left = at
    var last: Float? = null
    for (i in 1 until pts.size) {
        val len = dist(pts[i - 1], pts[i])
        if (len == 0f) continue
        last = norm(heading(pts[i - 1], pts[i]))
        if (left < len) return last
        left -= len
    }
    return last
}

/** Point [at] plan px along [pts], clamped at the end; second = reached the end. */
internal fun pointAlong(pts: List<Point2D>, at: Float): Pair<Point2D, Boolean> {
    var left = at
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val len = dist(a, b)
        if (left < len) return Point2D(a.x + (b.x - a.x) * left / len, a.y + (b.y - a.y) * left / len) to false
        left -= len
    }
    return pts.last() to true
}
