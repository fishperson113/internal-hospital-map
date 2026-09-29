package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import java.util.PriorityQueue
import kotlin.math.*

class NavigationGrid(
    val width: Float,
    val height: Float,
    val cellSize: Float,
    val gw: Int,
    val gh: Int,
    val blocked: BooleanArray
) {
    val ringCache = mutableMapOf<String, List<Int>>()
}

object GridAStarPathfinder {

    const val CELL_SIZE = 8f
    const val INFLATE = 3f
    val REACH_STEPS = floatArrayOf(8f, 25f, 50f, 90f)
    const val MIN_PASSAGE = 20f

    private val gridCache = mutableMapOf<Int, NavigationGrid>()

    fun getOrBuildGrid(floor: Floor): NavigationGrid {
        gridCache[floor.level]?.let { return it }

        val w = floor.width
        val h = floor.height
        val gw = ceil(w / CELL_SIZE).toInt()
        val gh = ceil(h / CELL_SIZE).toInt()
        val totalCells = gw * gh

        val blocked = BooleanArray(totalCells)

        // Bounding boxes for each room to speed up point-in-polygon & distance queries
        val roomData = floor.rooms.map { room ->
            val poly = room.polygon
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in poly) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            RoomBBox(room, poly, minX - INFLATE, minY - INFLATE, maxX + INFLATE, maxY + INFLATE)
        }

        // Rasterize rooms onto grid cells
        for (gy in 0 until gh) {
            val cy = gy * CELL_SIZE + CELL_SIZE / 2f
            for (gx in 0 until gw) {
                val cx = gx * CELL_SIZE + CELL_SIZE / 2f
                val idx = gy * gw + gx

                for (r in roomData) {
                    if (cx in r.minX..r.maxX && cy in r.minY..r.maxY) {
                        if (pointInPolygon(cx, cy, r.poly) || distToEdges(cx, cy, r.poly) <= INFLATE) {
                            blocked[idx] = true
                            break
                        }
                    }
                }
            }
        }

        val grid = NavigationGrid(w, h, CELL_SIZE, gw, gh, blocked)
        closeNarrow(grid, floor(MIN_PASSAGE / CELL_SIZE / 2f).toInt())
        gridCache[floor.level] = grid
        return grid
    }

    private data class RoomBBox(
        val room: Room,
        val poly: List<Point2D>,
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    )

    private fun pointInPolygon(x: Float, y: Float, poly: List<Point2D>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            if ((pi.y > y) != (pj.y > y) &&
                x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun distToEdges(px: Float, py: Float, poly: List<Point2D>): Float {
        var minDist = Float.MAX_VALUE
        var j = poly.size - 1
        for (i in poly.indices) {
            val d = distToSegment(px, py, poly[j].x, poly[j].y, poly[i].x, poly[i].y)
            if (d < minDist) minDist = d
            j = i
        }
        return minDist
    }

    private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clampedT = t.coerceIn(0f, 1f)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        return hypot(px - projX, py - projY)
    }

    private fun closeNarrow(g: NavigationGrid, r: Int) {
        if (r < 1) return
        val gw = g.gw
        val gh = g.gh
        val n = gw * gh

        val free = BooleanArray(n) { !g.blocked[it] }

        fun filter(src: BooleanArray, isMin: Boolean): BooleanArray {
            val tmp = BooleanArray(n)
            val out = BooleanArray(n)

            // Horizontal pass
            for (y in 0 until gh) {
                for (x in 0 until gw) {
                    var v = if (isMin) true else false
                    for (d in -r..r) {
                        val nx = x + d
                        val b = if (nx in 0 until gw) src[y * gw + nx] else false
                        v = if (isMin) (v && b) else (v || b)
                    }
                    tmp[y * gw + x] = v
                }
            }

            // Vertical pass
            for (y in 0 until gh) {
                for (x in 0 until gw) {
                    var v = if (isMin) true else false
                    for (d in -r..r) {
                        val ny = y + d
                        val b = if (ny in 0 until gh) tmp[ny * gw + x] else false
                        v = if (isMin) (v && b) else (v || b)
                    }
                    out[y * gw + x] = v
                }
            }
            return out
        }

        // Erode then dilate free space
        val eroded = filter(free, isMin = true)
        val dilated = filter(eroded, isMin = false)

        for (i in 0 until n) {
            g.blocked[i] = !dilated[i]
        }
    }

    private fun roomCells(floor: Floor, room: Room, reach: Float): List<Int> {
        val g = getOrBuildGrid(floor)
        val key = "${room.id}:$reach"
        g.ringCache[key]?.let { return it }

        val poly = room.polygon
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in poly) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        val limit = INFLATE + reach
        val gx0 = max(0, floor((minX - limit) / CELL_SIZE).toInt())
        val gy0 = max(0, floor((minY - limit) / CELL_SIZE).toInt())
        val gx1 = min(g.gw - 1, ceil((maxX + limit) / CELL_SIZE).toInt())
        val gy1 = min(g.gh - 1, ceil((maxY + limit) / CELL_SIZE).toInt())

        val cells = mutableListOf<Int>()
        for (gy in gy0..gy1) {
            val cy = gy * CELL_SIZE + CELL_SIZE / 2f
            for (gx in gx0..gx1) {
                val idx = gy * g.gw + gx
                if (g.blocked[idx]) continue
                val cx = gx * CELL_SIZE + CELL_SIZE / 2f
                if (distToEdges(cx, cy, poly) <= limit) {
                    cells.add(idx)
                }
            }
        }
        g.ringCache[key] = cells
        return cells
    }

    data class FloorPathResult(
        val points: List<Point2D>,
        val length: Float,
        val reach: Float
    )

    fun findPathOnFloor(floor: Floor, startRoom: Room, destRoom: Room): FloorPathResult? {
        val g = getOrBuildGrid(floor)
        for (reach in REACH_STEPS) {
            val starts = roomCells(floor, startRoom, reach)
            val goals = roomCells(floor, destRoom, reach)
            if (starts.isEmpty() || goals.isEmpty()) continue

            val rawCells = astar(g, starts, goals) ?: continue
            val smoothed = smooth(g, rawCells)
            val len = polyLength(smoothed)
            return FloorPathResult(smoothed, len, reach)
        }
        return buildCorridorFallback(floor, startRoom, destRoom)
    }

    private fun buildCorridorFallback(floor: Floor, startRoom: Room, destRoom: Room): FloorPathResult {
        val p1 = startRoom.labelAt ?: if (startRoom.polygon.isNotEmpty()) {
            Point2D(startRoom.polygon.map { it.x }.average().toFloat(), startRoom.polygon.map { it.y }.average().toFloat())
        } else Point2D(startRoom.boundsLeft + startRoom.boundsWidth / 2f, startRoom.boundsTop + startRoom.boundsHeight / 2f)

        val p2 = destRoom.labelAt ?: if (destRoom.polygon.isNotEmpty()) {
            Point2D(destRoom.polygon.map { it.x }.average().toFloat(), destRoom.polygon.map { it.y }.average().toFloat())
        } else Point2D(destRoom.boundsLeft + destRoom.boundsWidth / 2f, destRoom.boundsTop + destRoom.boundsHeight / 2f)

        val corridorY = when (floor.level) {
            2 -> if (p1.y < 350f && p2.y < 350f) 255f else if (p1.y > 500f && p2.y > 500f) 570f else 430f
            3 -> if (p1.y < 300f && p2.y < 300f) 265f else 340f
            else -> (p1.y + p2.y) / 2f
        }

        val pts = mutableListOf<Point2D>()
        pts.add(p1)
        if (abs(p1.y - corridorY) > 10f) {
            pts.add(Point2D(p1.x, corridorY))
        }
        if (abs(p2.x - p1.x) > 10f) {
            pts.add(Point2D(p2.x, corridorY))
        }
        pts.add(p2)

        return FloorPathResult(pts, polyLength(pts), reach = 0f)
    }

    private data class NodeEntry(val f: Float, val cell: Int) : Comparable<NodeEntry> {
        override fun compareTo(other: NodeEntry): Int = f.compareTo(other.f)
    }

    private fun astar(g: NavigationGrid, starts: List<Int>, goals: List<Int>): List<Int>? {
        val gw = g.gw
        val gh = g.gh
        val n = gw * gh

        val isGoal = BooleanArray(n)
        var gx0 = Int.MAX_VALUE
        var gy0 = Int.MAX_VALUE
        var gx1 = Int.MIN_VALUE
        var gy1 = Int.MIN_VALUE

        for (c in goals) {
            isGoal[c] = true
            val x = c % gw
            val y = c / gw
            if (x < gx0) gx0 = x
            if (y < gy0) gy0 = y
            if (x > gx1) gx1 = x
            if (y > gy1) gy1 = y
        }

        fun heuristic(cell: Int): Float {
            val x = cell % gw
            val y = cell / gw
            val dx = max(0, max(gx0 - x, x - gx1)).toFloat()
            val dy = max(0, max(gy0 - y, y - gy1)).toFloat()
            return max(dx, dy) + (sqrt(2.0f) - 1f) * min(dx, dy)
        }

        val dist = FloatArray(n) { Float.POSITIVE_INFINITY }
        val cameFrom = IntArray(n) { -1 }
        val pq = PriorityQueue<NodeEntry>()

        for (s in starts) {
            dist[s] = 0f
            pq.add(NodeEntry(heuristic(s), s))
        }

        val N8 = arrayOf(
            intArrayOf(1, 0, 100), intArrayOf(-1, 0, 100), intArrayOf(0, 1, 100), intArrayOf(0, -1, 100),
            intArrayOf(1, 1, 141), intArrayOf(1, -1, 141), intArrayOf(-1, 1, 141), intArrayOf(-1, -1, 141)
        )

        while (pq.isNotEmpty()) {
            val top = pq.poll() ?: break
            val u = top.cell
            if (top.f - heuristic(u) > dist[u] + 1e-4f) continue

            if (isGoal[u]) {
                val path = mutableListOf<Int>()
                var curr = u
                while (curr != -1) {
                    path.add(curr)
                    curr = cameFrom[curr]
                }
                path.reverse()
                return path
            }

            val ux = u % gw
            val uy = u / gw

            for (dir in N8) {
                val dx = dir[0]
                val dy = dir[1]
                val cost = dir[2] / 100f
                val nx = ux + dx
                val ny = uy + dy

                if (nx !in 0 until gw || ny !in 0 until gh) continue
                val v = ny * gw + nx
                if (g.blocked[v]) continue
                // No corner cutting
                if (dx != 0 && dy != 0 && (g.blocked[uy * gw + nx] || g.blocked[ny * gw + ux])) continue

                val newDist = dist[u] + cost
                if (newDist < dist[v]) {
                    dist[v] = newDist
                    cameFrom[v] = u
                    pq.add(NodeEntry(newDist + heuristic(v), v))
                }
            }
        }
        return null
    }

    private fun clearLine(g: NavigationGrid, ax: Int, ay: Int, bx: Int, by: Int): Boolean {
        val dx = (bx - ax).toFloat()
        val dy = (by - ay).toFloat()
        val steps = max(1, ceil(hypot(dx, dy) * 2f).toInt())
        val gw = g.gw
        val gh = g.gh
        for (k in 0..steps) {
            val cx = round(ax + dx * k / steps).toInt()
            val cy = round(ay + dy * k / steps).toInt()
            if (cx !in 0 until gw || cy !in 0 until gh) return false
            if (g.blocked[cy * gw + cx]) return false
        }
        return true
    }

    private fun smooth(g: NavigationGrid, cells: List<Int>): List<Point2D> {
        if (cells.size <= 2) {
            return cells.map { Point2D((it % g.gw) * CELL_SIZE + CELL_SIZE / 2f, (it / g.gw) * CELL_SIZE + CELL_SIZE / 2f) }
        }

        val gw = g.gw
        val pts = cells.map { Pair(it % gw, it / gw) }
        val out = mutableListOf<Pair<Int, Int>>()
        out.add(pts[0])

        var k = 0
        while (k < pts.size - 1) {
            var j = pts.size - 1
            while (j > k + 1 && !clearLine(g, pts[k].first, pts[k].second, pts[j].first, pts[j].second)) {
                j--
            }
            out.add(pts[j])
            k = j
        }

        return out.map { Point2D(it.first * CELL_SIZE + CELL_SIZE / 2f, it.second * CELL_SIZE + CELL_SIZE / 2f) }
    }

    fun polyLength(points: List<Point2D>): Float {
        var s = 0f
        for (i in 1 until points.size) {
            s += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        return s
    }
}
