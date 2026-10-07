package com.example

import com.example.hospital.data.datasource.MockHospitalMapDataSource
import com.example.hospital.data.repository.HospitalMapRepositoryImpl
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.pathfinding.GridAStarPathfinder
import com.example.hospital.domain.pathfinding.PathShaper
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/** Shape of the drawn line, checked in metres against the room polygons, independently of PathShaper. */
@RunWith(RobolectricTestRunner::class)
class PathShaperTest {

    private val radius = PathShaper.RADIUS_M

    // Synthetic plans span 1600 x 1000 px, which PathShaper.pxPerM reads as 80 x 50 m: 20 px/m, 1 px = 5 cm.
    private fun rect(id: Int, x0: Float, y0: Float, x1: Float, y1: Float) = Room(
        id = "r$id", code = "", name = "", floorLevel = 0, category = RoomCategory.FACILITY, nodeId = "",
        polygon = listOf(Point2D(x0, y0), Point2D(x1, y0), Point2D(x1, y1), Point2D(x0, y1))
    )
    private fun floor(level: Int, vararg rooms: Room) =
        Floor(level, "", "", rooms.map { it.copy(floorLevel = level) }, width = 1600f, height = 1000f)
    private fun spot(level: Int, x: Float, y: Float) = Room(
        id = "f@$x,$y", code = "", name = "", floorLevel = level, category = RoomCategory.FACILITY, nodeId = "",
        labelAt = Point2D(x, y)
    )

    private fun walls(f: Floor): List<FloatArray> {
        val (sx, sy) = PathShaper.pxPerM(f)
        val w = f.width / sx
        val h = f.height / sy
        val out = mutableListOf(floatArrayOf(0f, 0f, w, 0f), floatArrayOf(w, 0f, w, h), floatArrayOf(w, h, 0f, h), floatArrayOf(0f, h, 0f, 0f))
        for (r in f.rooms) for (i in r.polygon.indices) {
            val a = r.polygon[i]
            val b = r.polygon[(i + 1) % r.polygon.size]
            out += floatArrayOf(a.x / sx, a.y / sy, b.x / sx, b.y / sy)
        }
        return out
    }
    private fun ptSeg(x: Float, y: Float, w: FloatArray): Float {
        val dx = w[2] - w[0]
        val dy = w[3] - w[1]
        val l = dx * dx + dy * dy
        val t = if (l > 0f) (((x - w[0]) * dx + (y - w[1]) * dy) / l).coerceIn(0f, 1f) else 0f
        return hypot(x - w[0] - t * dx, y - w[1] - t * dy)
    }
    private fun orient(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float) = sign((bx - ax) * (y - ay) - (by - ay) * (x - ax))
    private fun segDist(p: Point2D, q: Point2D, w: FloatArray): Float {
        if (orient(w[0], w[1], w[2], w[3], p.x, p.y) * orient(w[0], w[1], w[2], w[3], q.x, q.y) < 0 &&
            orient(p.x, p.y, q.x, q.y, w[0], w[1]) * orient(p.x, p.y, q.x, q.y, w[2], w[3]) < 0) return 0f
        val pq = floatArrayOf(p.x, p.y, q.x, q.y)
        return minOf(minOf(ptSeg(p.x, p.y, w), ptSeg(q.x, q.y, w)), minOf(ptSeg(w[0], w[1], pq), ptSeg(w[2], w[3], pq)))
    }

    private class Checked(val p: List<Point2D>, val i0: Int, val i1: Int)

    /**
     * No segment touches a wall. From the first point that is [radius] off the walls to the last one, every segment
     * stays [radius] off them; before / after that only the short way out from an end next to a wall.
     */
    private fun checkLine(f: Floor, pts: List<Point2D>, label: String = ""): Checked {
        val ws = walls(f)
        val (sx, sy) = PathShaper.pxPerM(f)
        val p = pts.map { Point2D(it.x / sx, it.y / sy) }
        fun clear(a: Point2D, b: Point2D) = ws.minOf { segDist(a, b, it) }
        for (i in 1 until p.size) assertTrue("$label segment $i crosses a wall", clear(p[i - 1], p[i]) > 0f)
        val ok = p.map { clear(it, it) >= radius - 0.01f }
        val i0 = ok.indexOf(true)
        val i1 = ok.lastIndexOf(true)
        assertTrue("$label never gets $radius m off the walls", i0 >= 0)
        for (i in i0 + 1..i1) {
            val c = clear(p[i - 1], p[i])
            assertTrue("$label segment $i is $c m from a wall", c >= radius - 0.01f)
        }
        return Checked(p, i0, i1)
    }

    private fun route(f: Floor, a: Room, b: Room): List<Point2D> {
        val r = GridAStarPathfinder.findPathOnFloor(f, a, b)
        assertNotNull(r)
        assertTrue("fell back to the hand-drawn corridor", r!!.reach > 0f)
        return r.points
    }

    @Test
    fun `corridor into a room with an off-centre door - centre line, through the door, no wall crossed`() {
        // corridor y 100..148 (2.4 m); room x 400..700, y 158..358; door x 600..630 (1.5 m), off the room centre
        val f = floor(
            101, rect(0, 0f, 0f, 1600f, 100f), rect(1, 0f, 148f, 400f, 1000f), rect(2, 400f, 148f, 600f, 158f),
            rect(3, 630f, 148f, 1600f, 158f), rect(4, 700f, 158f, 1600f, 1000f), rect(5, 400f, 358f, 700f, 1000f)
        )
        val pts = route(f, spot(101, 60f, 124f), spot(101, 450f, 300f))
        checkLine(f, pts)
        val along = (1 until pts.size).flatMap { i ->
            (0 until 20).map { k -> Point2D(pts[i - 1].x + (pts[i].x - pts[i - 1].x) * k / 20, pts[i - 1].y + (pts[i].y - pts[i - 1].y) * k / 20) }
        }
        val inCorridor = along.filter { it.x > 100f && it.x < 560f && it.y > 100f && it.y < 148f }
        assertTrue("no point along the corridor", inCorridor.isNotEmpty())
        inCorridor.forEach { assertTrue("$it off the corridor centre line", abs(it.y - 124f) <= 4f) }
        val k = pts.indexOfFirst { it.y > 153f }
        val a = pts[k - 1]
        val b = pts[k]
        val xDoor = a.x + (b.x - a.x) * (153f - a.y) / (b.y - a.y)
        assertTrue("crosses the door line at x=$xDoor", xDoor in 606f..624f)
    }

    @Test
    fun `around an obstacle in the middle of a hall - tight, radius off it`() {
        val f = floor(102, rect(0, 0f, 0f, 10f, 10f), rect(1, 1590f, 990f, 1600f, 1000f), rect(2, 750f, 450f, 850f, 550f))
        val pts = route(f, spot(102, 600f, 500f), spot(102, 1000f, 500f))
        val c = checkLine(f, pts)
        assertEquals(0, c.i0)
        assertEquals(c.p.lastIndex, c.i1) // open hall: the whole line keeps the radius
        val r = radius * 20
        val around = 2 * hypot(150 - r, 50 + r) + 100 + 2 * r // taut string at the radius
        assertTrue("length ${GridAStarPathfinder.polyLength(pts)} px vs taut $around px", GridAStarPathfinder.polyLength(pts) < around * 1.08f)
    }

    @Test
    fun `start right next to a wall - line starts at the spot, gets radius off within a step, then keeps it`() {
        val f = floor(103, rect(0, 0f, 0f, 1600f, 100f), rect(1, 1590f, 990f, 1600f, 1000f))
        val pts = route(f, spot(103, 600f, 102f), spot(103, 1000f, 300f)) // 10 cm from the wall
        assertEquals(Point2D(600f, 102f), pts.first())
        val c = checkLine(f, pts)
        assertTrue("runs along the wall before leaving it", hypot(c.p[c.i0].x - c.p[0].x, c.p[c.i0].y - c.p[0].y) < 0.6f)
    }

    @Test
    fun `every room on floors 2 and 3 is still reachable, and no drawn line crosses a wall`() = runTest {
        val floors = GetHospitalMapUseCase(HospitalMapRepositoryImpl(MockHospitalMapDataSource())).invoke().first().floors
        for (f in floors.filter { !it.isTodo && it.rooms.isNotEmpty() }) {
            val hub = f.rooms.firstOrNull { it.link != null } ?: f.rooms.first()
            for (room in f.rooms) {
                if (room == hub) continue
                checkLine(f, route(f, hub, room), "floor ${f.level} ${room.id} ${room.name}")
            }
        }
    }
}
