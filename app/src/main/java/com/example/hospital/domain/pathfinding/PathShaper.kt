package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Point2D
import kotlin.math.*

/**
 * Shapes the line drawn over the A* cells, the same way as the backend (MLAI_Hackathon/src/backend/routing.js).
 * Works in metres, since the plans are stretched per axis: keeps a person's radius off every wall, runs
 * corridors on their centre line, leaves a room square to the corridor, pulls the line tight across open halls,
 * then rounds the corners.
 *
 * [radiusM] is capped at half the narrowest passage the grid keeps open (MIN_PASSAGE), so it never closes a room off.
 */
class PathShaper(floor: Floor, private val grid: NavigationGrid, radiusM: Float = RADIUS_M) {

    companion object {
        const val RADIUS_M = 0.4f        // default person radius
        const val STEP_M = 0.05f         // march step when moving a point away from the walls
        const val CORRIDOR_HALF_M = 1.5f // a passage up to twice this wide is a corridor (centre line); wider is a hall
        const val ROUND_M = 0.8f         // a corner is rounded over at most this length on each side
        private val BUILDING_M = 80f to 50f // building size on Google Maps, as backend scale.js

        /** Plan px per metre along x and y, as backend scale.js does without a surveyed scale. */
        fun pxPerM(floor: Floor): Pair<Float, Float> {
            val pts = floor.rooms.flatMap { it.polygon }
            if (pts.isEmpty()) return 10f to 10f
            val w = pts.maxOf { it.x } - pts.minOf { it.x }
            val h = pts.maxOf { it.y } - pts.minOf { it.y }
            return (w / BUILDING_M.first * 100).roundToInt() / 100f to (h / BUILDING_M.second * 100).roundToInt() / 100f
        }
    }

    private val scale = pxPerM(floor)
    private val sx = scale.first
    private val sy = scale.second
    private val walls = mutableListOf<FloatArray>() // x1, y1, x2, y2 in metres
    val r: Float
    private val cache = HashMap<Point2D, Float>()

    init {
        val w = floor.width / sx
        val h = floor.height / sy
        walls += floatArrayOf(0f, 0f, w, 0f); walls += floatArrayOf(w, 0f, w, h)
        walls += floatArrayOf(w, h, 0f, h); walls += floatArrayOf(0f, h, 0f, 0f)
        for (room in floor.rooms) {
            val poly = room.polygon
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                walls += floatArrayOf(a.x / sx, a.y / sy, b.x / sx, b.y / sy)
            }
        }
        r = min(radiusM, (GridAStarPathfinder.MIN_PASSAGE / 2 + GridAStarPathfinder.INFLATE) / max(sx, sy))
    }

    private class Near(val d: Float, val x: Float, val y: Float)

    private fun ptSeg(x: Float, y: Float, w: FloatArray): Near {
        val dx = w[2] - w[0]
        val dy = w[3] - w[1]
        val l = dx * dx + dy * dy
        val t = if (l > 0f) (((x - w[0]) * dx + (y - w[1]) * dy) / l).coerceIn(0f, 1f) else 0f
        val px = w[0] + t * dx
        val py = w[1] + t * dy
        return Near(hypot(x - px, y - py), px, py)
    }

    private fun side(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float) =
        sign((bx - ax) * (y - ay) - (by - ay) * (x - ax))

    /** Distance between segment p-q and wall w, 0 when they cross. */
    private fun segSeg(p: Point2D, q: Point2D, w: FloatArray): Float {
        if (side(w[0], w[1], w[2], w[3], p.x, p.y) * side(w[0], w[1], w[2], w[3], q.x, q.y) < 0f &&
            side(p.x, p.y, q.x, q.y, w[0], w[1]) * side(p.x, p.y, q.x, q.y, w[2], w[3]) < 0f
        ) return 0f
        val pq = floatArrayOf(p.x, p.y, q.x, q.y)
        return minOf(minOf(ptSeg(p.x, p.y, w).d, ptSeg(q.x, q.y, w).d), minOf(ptSeg(w[0], w[1], pq).d, ptSeg(w[2], w[3], pq).d))
    }

    fun clear(p: Point2D): Float = cache.getOrPut(p) { walls.minOf { ptSeg(p.x, p.y, it).d } }

    private fun blockedAt(p: Point2D): Boolean {
        val gx = floor(p.x * sx / grid.cellSize).toInt()
        val gy = floor(p.y * sy / grid.cellSize).toInt()
        return gx !in 0 until grid.gw || gy !in 0 until grid.gh || grid.blocked[gy * grid.gw + gx]
    }

    /** p-q stays in the grid's free cells and at least [min] metres from every wall. */
    private fun segOk(p: Point2D, q: Point2D, min: Float): Boolean {
        val n = ceil(hypot((q.x - p.x) * sx, (q.y - p.y) * sy) / (grid.cellSize / 2)).toInt()
        for (k in 1 until n) if (blockedAt(Point2D(p.x + (q.x - p.x) * k / n, p.y + (q.y - p.y) * k / n))) return false
        val x0 = min(p.x, q.x) - min
        val x1 = max(p.x, q.x) + min
        val y0 = min(p.y, q.y) - min
        val y1 = max(p.y, q.y) + min
        return walls.all { w ->
            max(w[0], w[2]) < x0 || min(w[0], w[2]) > x1 || max(w[1], w[3]) < y0 || min(w[1], w[3]) > y1 || segSeg(p, q, w) >= min
        }
    }

    /**
     * Direction that gains room fastest: away from every wall about as near as the nearest one (two at a crease
     * or in a corner), null when they pull opposite ways (a centre line).
     */
    private fun away(p: Point2D): Point2D? {
        val c = clear(p)
        var dx = 0f
        var dy = 0f
        for (w in walls) {
            val n = ptSeg(p.x, p.y, w)
            if (n.d > 0f && n.d <= c + STEP_M / 2) { dx += (p.x - n.x) / n.d; dy += (p.y - n.y) / n.d }
        }
        val l = hypot(dx, dy)
        return if (l < 0.5f) null else Point2D(dx / l, dy / l)
    }

    private class Pushed(val p: Point2D, val ridge: Boolean)

    /**
     * Walks p away from the walls until it is [target] from them or stops gaining room (`ridge`: the centre line
     * of a corridor, or the middle between an obstacle and the wall facing it).
     */
    private fun push(start: Point2D, target: Float): Pushed {
        if (blockedAt(start)) return Pushed(start, true) // a spot inside a room: leave it where it is
        var p = start
        var k = 0
        while (k++ < 400 && clear(p) < target) {
            val u = away(p) ?: return Pushed(p, true)
            val q = Point2D(p.x + u.x * STEP_M, p.y + u.y * STEP_M)
            if (clear(q) < clear(p) + STEP_M / 4 || blockedAt(q)) return Pushed(p, true)
            p = q
        }
        return Pushed(p, false)
    }

    // The line may go no closer to a wall than r, or than its own ends already are (an end next to a wall).
    private fun ok(p: Point2D, q: Point2D) = segOk(p, q, minOf(r, clear(p), clear(q)) - 1e-3f)

    private fun centre(p: Point2D): Point2D? = push(p, CORRIDOR_HALF_M).let { if (it.ridge) it.p else null }

    /** The end itself, out to r from the wall, and (a room's end) onto the corridor's centre line. */
    private fun end(p: Point2D, room: Boolean): List<Point2D> {
        val out = mutableListOf(p)
        val e = push(p, r).p
        if (e != p) out += e
        val c = if (room) centre(out.last()) else null
        if (c != null && c != out.last() && ok(out.last(), c)) out += c
        return out
    }

    private fun dedupe(l: List<Point2D>): List<Point2D> =
        l.filterIndexed { i, p -> i == 0 || hypot(p.x - l[i - 1].x, p.y - l[i - 1].y) > 0.02f }

    /**
     * [cells]: the A* cell centres in plan px. [a] / [b]: the user's exact spot, or null when that end is a room
     * (then the end is the cell next to the room, and the line leaves it square to the corridor). Returns plan px.
     */
    fun shape(cells: List<Point2D>, a: Point2D? = null, b: Point2D? = null): List<Point2D> {
        fun m(p: Point2D) = Point2D(p.x / sx, p.y / sy)
        val head = end(m(a ?: cells.first()), a == null)
        val tail = end(m(b ?: cells.last()), b == null).reversed()

        // String pulling over the cells, each first moved out to r from the walls: keep a point only when the
        // straight line past it would come closer to a wall than r (or leave the free cells).
        val chain = listOf(head.last()) + cells.map { push(m(it), r).p } + tail.first()
        val pulled = mutableListOf(chain[0])
        var k = 0
        while (k < chain.size - 1) {
            var j = chain.size - 1
            while (j > k + 1 && !ok(chain[k], chain[j])) j--
            pulled += chain[j]
            k = j
        }
        val path = (head + pulled.subList(1, pulled.size - 1) + tail).toMutableList()

        // Corners: in a corridor onto its centre line (a square turn), in a hall a little off the obstacle so
        // the rounding below has room. Kept only when both sides still pass.
        for (i in head.size until path.size - tail.size) {
            val p = path[i]
            val c = centre(p) ?: push(p, clear(p) + r / 2).p
            if (c != p && ok(path[i - 1], c) && ok(c, path[i + 1])) path[i] = c
        }
        val pts = dedupe(path)

        // Round each corner with a quadratic curve, shrunk until it stays r from the walls; else keep it sharp.
        val out = mutableListOf(pts.first())
        for (i in 1 until pts.size - 1) {
            val p = pts[i - 1]
            val v = pts[i]
            val q = pts[i + 1]
            val la = hypot(p.x - v.x, p.y - v.y)
            val lb = hypot(q.x - v.x, q.y - v.y)
            var arc: List<Point2D>? = null
            var t = minOf(ROUND_M, la / 2, lb / 2)
            for (tries in 0 until 4) {
                val p0 = Point2D(v.x + (p.x - v.x) * t / la, v.y + (p.y - v.y) * t / la)
                val p2 = Point2D(v.x + (q.x - v.x) * t / lb, v.y + (q.y - v.y) * t / lb)
                val c = (0..6).map { s ->
                    val u = s / 6f
                    val w0 = (1 - u) * (1 - u)
                    val w1 = 2 * u * (1 - u)
                    val w2 = u * u
                    Point2D(w0 * p0.x + w1 * v.x + w2 * p2.x, w0 * p0.y + w1 * v.y + w2 * p2.y)
                }
                if ((1 until c.size).all { segOk(c[it - 1], c[it], r - 1e-3f) }) { arc = c; break }
                t /= 2
            }
            out += arc ?: listOf(v)
        }
        out += pts.last()
        val px = dedupe(out).map { Point2D(it.x * sx, it.y * sy) }.toMutableList()
        if (a != null) px[0] = a // exactly the spot
        if (b != null) px[px.lastIndex] = b
        return px
    }
}
