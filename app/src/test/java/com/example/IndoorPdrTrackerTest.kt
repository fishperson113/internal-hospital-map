package com.example

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.model.RouteAssumptions
import com.example.hospital.domain.model.RouteSegment
import com.example.hospital.domain.model.TransferInfo
import com.example.hospital.sensor.IndoorPdrTracker
import com.example.hospital.sensor.StepDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class IndoorPdrTrackerTest {

    // metersPerPx chosen so one 0.65 m step = 10 plan px
    private val step = 0.65f
    private fun route(segments: List<RouteSegment>, transfer: TransferInfo? = null) = HospitalRoute(
        startNode = MapNode("s", 2, 0f, 0f, "S"), destNode = MapNode("d", 2, 0f, 0f, "D"),
        isCrossFloor = transfer != null, totalDistanceMeters = 0f, estimatedDurationMinutes = 1,
        steps = emptyList(), pathNodeIds = emptyList(), segments = segments, transfer = transfer,
        assumptions = RouteAssumptions(metersPerPx = 0.065)
    )

    // right 100 px, then a clockwise (right) turn, then down 100 px
    private val lShape = route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(100f, 0f), Point2D(100f, 100f)), 13f)))

    private fun walk(pdr: IndoorPdrTracker, n: Int, yaw: Float?) = (1..n).map { pdr.onWalked(step, yaw)!! }.last()

    private fun assertAt(x: Float, y: Float, p: Point2D) {
        assertEquals(x, p.x, 0.01f)
        assertEquals(y, p.y, 0.01f)
    }

    @Test
    fun `without gyro the dot just slides along the line and stops at the end`() {
        val pdr = IndoorPdrTracker()
        assertNull(pdr.onWalked(step, null)) // no directions yet
        assertEquals(Point2D(0f, 0f), pdr.initialize(lShape)!!.position)
        assertAt(100f, 50f, walk(pdr, 15, null).position)
        val end = walk(pdr, 20, null)
        assertEquals(Point2D(100f, 100f), end.position)
        assertTrue(end.hasArrived)
    }

    @Test
    fun `dot waits at a corner until the phone turns`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        assertAt(100f, 0f, walk(pdr, 15, yaw = 30f).position) // counted 150 px, held at the corner
        assertAt(100f, 10f, pdr.onWalked(step, yaw = 120f)!!.position) // turned right 90°
    }

    @Test
    fun `turning shortly before a corner jumps the dot to it`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        assertAt(50f, 0f, walk(pdr, 5, yaw = 0f).position) // 50 px = 3.25 m before the corner
        assertAt(100f, 10f, pdr.onWalked(step, yaw = 85f)!!.position)
    }

    @Test
    fun `turns far from a corner or the wrong way are ignored`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(300f, 0f), Point2D(300f, 100f)), 26f))))
        pdr.onWalked(step, 0f)
        assertAt(20f, 0f, pdr.onWalked(step, 90f)!!.position) // 18 m before the corner: looked around
        assertAt(30f, 0f, pdr.onWalked(step, -90f)!!.position)
    }

    @Test
    fun `facing direction comes from the line plus gyro turn, no compass`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        assertEquals(90f, pdr.planHeading(null)!!, 0.01f) // before any step: along the line (right)
        pdr.onWalked(step, yawDeg = 30f)                 // gyro 30° = "along the line" from now on
        assertEquals(100f, pdr.planHeading(40f)!!, 0.01f)
        walk(pdr, 14, yaw = 30f)                          // waiting at the corner
        assertEquals(180f, pdr.planHeading(120f)!!, 0.01f) // turned right: facing down the next leg
        pdr.onWalked(step, yawDeg = 120f)
        assertEquals(180f, pdr.planHeading(120f)!!, 0.01f)
        assertNull(IndoorPdrTracker().planHeading(0f)) // no directions: no arrow
    }

    private val straight = route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(300f, 0f)), 20f)))

    @Test
    fun `walking away from the line leaves the route and moves freely from where it began`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(straight)
        walk(pdr, 2, yaw = 0f)                       // along the line (right), x = 20
        val left = walk(pdr, 7, yaw = 90f)           // turned right (down on the plan) for 4.55 m
        assertTrue(left.offRoute)
        assertAt(20f, 70f, left.position)            // replayed from where the turn began
        assertAt(20f, 80f, pdr.onWalked(step, 90f)!!.position)
        assertEquals(180f, pdr.planHeading(90f)!!, 0.01f)
    }

    @Test
    fun `free walking stays in corridors and slides along walls`() {
        val pdr = IndoorPdrTracker(walkable = { _, p -> p.y < 50f }) // a room below y = 50
        pdr.initialize(straight)
        walk(pdr, 2, yaw = 0f)
        val s = walk(pdr, 12, yaw = 135f) // diagonal down-left into the room
        assertTrue(s.offRoute)
        assertTrue("y ${s.position.y}", s.position.y < 50f)
        assertTrue("x ${s.position.x}", s.position.x < 20f) // kept moving along the wall
    }

    @Test
    fun `going straight far past a corner also leaves the route`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        val s = walk(pdr, 26, yaw = 0f) // corner at 10 steps, then 16 steps (10.4 m) straight on
        assertTrue(s.offRoute)
        assertTrue("x ${s.position.x}", s.position.x > 150f)
    }

    @Test
    fun `floor changes only through goToStep`() {
        val shaft = Room(id = "a", code = "", name = "", floorLevel = 2, category = RoomCategory.STAIRS, nodeId = "")
        val r = route(
            listOf(
                RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(30f, 0f)), 2f),
                RouteSegment(3, listOf(Point2D(5f, 5f), Point2D(50f, 5f)), 3f)
            ),
            TransferInfo("L1", "stair", shaft, shaft.copy(floorLevel = 3))
        )
        val pdr = IndoorPdrTracker()
        pdr.initialize(r)
        assertEquals(2, walk(pdr, 10, null).floorLevel) // still floor 2, waiting at the shaft

        assertEquals(Point2D(30f, 0f), pdr.goToStep(1)!!.position) // "Lên tầng 3"
        assertNull(pdr.onWalked(step, null)) // in the shaft: steps do not move the dot

        val upstairs = pdr.goToStep(2)!!
        assertEquals(3, upstairs.floorLevel)
        assertEquals(Point2D(5f, 5f), upstairs.position)
        assertAt(15f, 5f, pdr.onWalked(step, null)!!.position)
    }

    // ------------------------------------------------------------ StepDetector (50 Hz synthetic)

    private fun run(seconds: Float, signal: (Float) -> Float): List<Float> {
        val d = StepDetector()
        val out = mutableListOf<Float>()
        var t = 0f
        while (t < seconds) {
            out += d.onSample((t * 1e9).toLong() + 1, 0f, 0f, 9.81f + signal(t))
            t += 0.02f
        }
        return out
    }

    @Test
    fun `walking at 2 steps per second is counted with human step lengths`() {
        val steps = run(5f) { 3f * sin(2 * PI * 2 * it).toFloat() }
        assertTrue("got ${steps.size}", steps.size in 8..10)
        steps.forEach { assertTrue("length $it", it in 0.5f..0.85f) }
    }

    @Test
    fun `a single shake or standing still is not a step`() {
        assertEquals(0, run(5f) { 6f * exp(-((it - 2f) * (it - 2f)) / 0.005f) }.size)
        assertFalse(run(5f) { 0.1f * sin(2 * PI * 7 * it).toFloat() }.isNotEmpty())
    }
}
