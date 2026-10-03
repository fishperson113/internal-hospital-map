package com.example

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.model.RouteAssumptions
import com.example.hospital.domain.model.RouteSegment
import com.example.hospital.domain.model.TransferInfo
import com.example.hospital.sensor.HeadingFusion
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

/** Headings are on the plan: 0 = up, 90 = right, 180 = down (clockwise). */
class IndoorPdrTrackerTest {

    // metersPerPx chosen so one 0.65 m step = 10 plan px
    private val step = 0.65f
    private fun route(segments: List<RouteSegment>, transfer: TransferInfo? = null) = HospitalRoute(
        startNode = MapNode("s", 2, 0f, 0f, "S"), destNode = MapNode("d", 2, 0f, 0f, "D"),
        isCrossFloor = transfer != null, totalDistanceMeters = 0f, estimatedDurationMinutes = 1,
        steps = emptyList(), pathNodeIds = emptyList(), segments = segments, transfer = transfer,
        assumptions = RouteAssumptions(metersPerPx = 0.065)
    )

    // right 100 px, then a right turn, then down 100 px
    private val lShape = route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(100f, 0f), Point2D(100f, 100f)), 13f)))
    private val straight = route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(300f, 0f)), 20f)))

    private fun walk(pdr: IndoorPdrTracker, n: Int, heading: Float?) = (1..n).map { pdr.onWalked(step, heading)!! }.last()

    private fun assertAt(x: Float, y: Float, p: Point2D) {
        assertEquals(x, p.x, 0.01f)
        assertEquals(y, p.y, 0.01f)
    }

    @Test
    fun `without heading the dot just slides along the line and stops at the end`() {
        val pdr = IndoorPdrTracker()
        assertNull(pdr.onWalked(step, null)) // no directions yet
        assertEquals(Point2D(0f, 0f), pdr.initialize(lShape)!!.position)
        assertEquals(90f, pdr.lineHeading()!!, 0.01f)
        assertAt(100f, 50f, walk(pdr, 15, null).position)
        val end = walk(pdr, 20, null)
        assertEquals(Point2D(100f, 100f), end.position)
        assertTrue(end.hasArrived)
    }

    @Test
    fun `dot waits at a corner until the user turns`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        assertAt(100f, 0f, walk(pdr, 15, heading = 90f).position) // counted 150 px, held at the corner
        assertAt(100f, 10f, pdr.onWalked(step, heading = 180f)!!.position) // turned right
        assertEquals(180f, pdr.lineHeading()!!, 0.01f)
    }

    @Test
    fun `turning shortly before a corner jumps the dot to it`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        assertAt(50f, 0f, walk(pdr, 5, heading = 95f).position) // 3.25 m before the corner, slight bias
        assertAt(100f, 10f, pdr.onWalked(step, heading = 178f)!!.position)
    }

    @Test
    fun `turning at the start does not matter, only walking direction does`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        // Standing facing the other way is not a step; walking starts along the line right away.
        assertAt(10f, 0f, pdr.onWalked(step, heading = 90f)!!.position)
        assertFalse(walk(pdr, 4, heading = 90f).offRoute)
    }

    @Test
    fun `turns far from a corner or the wrong way are ignored for short distances`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(300f, 0f), Point2D(300f, 100f)), 26f))))
        pdr.onWalked(step, 90f)
        assertAt(20f, 0f, pdr.onWalked(step, 180f)!!.position) // 18 m before the corner: looked around
        assertAt(30f, 0f, pdr.onWalked(step, 0f)!!.position)
    }

    @Test
    fun `walking away from the line leaves the route and moves freely from where it began`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(straight)
        walk(pdr, 2, heading = 90f)                 // along the line, x = 20
        val left = walk(pdr, 7, heading = 180f)     // went down for 4.55 m
        assertTrue(left.offRoute)
        assertAt(20f, 70f, left.position)           // replayed from where the turn began
        assertAt(20f, 80f, pdr.onWalked(step, 180f)!!.position)
        assertNull(pdr.lineHeading())               // off the line: no route alignment
    }

    @Test
    fun `free walking stays in corridors and slides along walls`() {
        val pdr = IndoorPdrTracker(walkable = { _, p -> p.y < 50f }) // a room below y = 50
        pdr.initialize(straight)
        walk(pdr, 2, heading = 90f)
        val s = walk(pdr, 12, heading = 225f) // diagonal down-left into the room
        assertTrue(s.offRoute)
        assertTrue("y ${s.position.y}", s.position.y < 50f)
        assertTrue("x ${s.position.x}", s.position.x < 20f) // kept moving along the wall
    }

    @Test
    fun `going straight far past a corner also leaves the route`() {
        val pdr = IndoorPdrTracker()
        pdr.initialize(lShape)
        val s = walk(pdr, 26, heading = 90f) // corner at 10 steps, then 16 steps (10.4 m) straight on
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
        assertNull(pdr.lineHeading())

        val upstairs = pdr.goToStep(2)!!
        assertEquals(3, upstairs.floorLevel)
        assertEquals(Point2D(5f, 5f), upstairs.position)
        assertAt(15f, 5f, pdr.onWalked(step, null)!!.position)
    }

    // ------------------------------------------------------------ HeadingFusion

    @Test
    fun `heading = compass minus plan north at once, gyro for turns, compass followed slowly`() {
        val f = HeadingFusion(planNorthDeg = 30f)
        assertEquals(70f, f.update(compassDeg = 100f, yawDeg = 10f)!!, 0.01f) // standing still: known at once
        // Turned 30° by gyro while the compass is disturbed (still says 100): gyro wins.
        assertEquals(98.5f, f.update(compassDeg = 100f, yawDeg = 40f)!!, 0.01f)
        // Compass consistent again: converges to 130 - 30 = 100.
        var h = 0f
        repeat(200) { h = f.update(compassDeg = 130f, yawDeg = 40f)!! }
        assertEquals(100f, h, 0.1f)
        assertEquals(90f, HeadingFusion(planNorthDeg = 10f).update(100f, null)!!, 0.01f) // no gyro
        assertNull(HeadingFusion().update(null, 5f)) // no compass yet: unknown, no arrow
    }

    @Test
    fun `walking along the route corrects a slightly wrong plan north`() {
        val f = HeadingFusion(planNorthDeg = 0f) // real value would be 15
        assertEquals(105f, f.update(105f, 0f)!!, 0.01f) // walking along a leg that points right (90)
        repeat(50) { f.alignTo(lineDeg = 90f, yawDeg = 0f) }
        assertEquals(90f, f.update(null, 0f)!!, 0.1f)
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
