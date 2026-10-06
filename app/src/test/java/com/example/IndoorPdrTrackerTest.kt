package com.example

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Positioning
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.model.RouteAssumptions
import com.example.hospital.domain.model.RouteSegment
import com.example.hospital.domain.model.TransferInfo
import com.example.hospital.sensor.HeadingFusion
import com.example.hospital.sensor.IndoorPdrState
import com.example.hospital.sensor.IndoorPdrTracker
import com.example.hospital.sensor.StepDetector
import com.example.hospital.sensor.StepLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Same cases as the web (MLAI_Hackathon src/web/pdr.test.js). Headings are on the plan:
 * 0 = up, 90 = right, 180 = down (clockwise). mpp = 0.065 so one 0.65 m step = 10 plan px.
 */
class IndoorPdrTrackerTest {

    // The numbers the server sends today (GET /api/floors -> positioning = pdr.js DEFAULTS)
    private val p = Positioning(
        planNorthDeg = 300f, stepK = 0.45f,
        reroute = Positioning.Reroute(10f, 2000, 5000),
        ronin = Positioning.Ronin(0.5f, 0.2f, 8),
        step = Positioning.StepConfig(0.28f, 1.6f, 0.9f, 0.35f, 0.95f),
        learner = Positioning.LearnerConfig(4f, 0.5f, 0.7f, 1.4f, 0.3f, 0.7f),
        fusion = Positioning.FusionConfig(8, 0.02f, 25f, 120, 0.1f, 20f),
        tracker = Positioning.TrackerConfig(20f, 35f, 35f, 8f, 3f, 45f, 4f, 10f, 135f)
    )

    private val step = 0.65f
    private val shaft = Room(id = "a", code = "", name = "", floorLevel = 2, category = RoomCategory.STAIRS, nodeId = "")

    private fun plan(vararg segs: List<Point2D>, pxPerM: Map<String, Pair<Float, Float>> = emptyMap(), mpp: Double = 0.065) = HospitalRoute(
        startNode = MapNode("s", 2, 0f, 0f, "S"), destNode = MapNode("d", 2, 0f, 0f, "D"),
        isCrossFloor = segs.size > 1, totalDistanceMeters = 0f, estimatedDurationMinutes = 1,
        steps = emptyList(), pathNodeIds = emptyList(),
        segments = segs.mapIndexed { i, pts -> RouteSegment(2 + i, pts, 0f, "f$i") },
        transfer = if (segs.size > 1) TransferInfo("L1", "elevator", shaft, shaft.copy(floorLevel = 3)) else null,
        assumptions = RouteAssumptions(metersPerPx = mpp, pxPerM = pxPerM)
    )

    private fun pts(vararg xy: Int) = xy.toList().chunked(2) { Point2D(it[0].toFloat(), it[1].toFloat()) }
    private val lShape = pts(0, 0, 100, 0, 100, 100)
    private val straight = pts(0, 0, 300, 0)

    private fun tracker(route: HospitalRoute, walkable: (Int, Point2D) -> Boolean = { _, _ -> true }) =
        IndoorPdrTracker(route, p.tracker, walkable)

    private fun walk(t: IndoorPdrTracker, n: Int, heading: Float?): IndoorPdrState {
        repeat(n) { t.onWalked(step, heading) }
        return t.state()
    }

    private fun assertAt(x: Float, y: Float, s: IndoorPdrState) {
        assertEquals("x", x, s.position.x, 0.01f)
        assertEquals("y", y, s.position.y, 0.01f)
    }

    @Test
    fun `without heading the dot slides along the line and stops at the end`() {
        val t = tracker(plan(lShape))
        assertAt(100f, 50f, walk(t, 15, null))
        val s = walk(t, 20, null)
        assertAt(100f, 100f, s)
        assertTrue(s.hasArrived)
    }

    @Test
    fun `waits at the corner until the user turns`() {
        val t = tracker(plan(lShape))
        assertAt(100f, 0f, walk(t, 15, 90f))
        assertAt(100f, 10f, walk(t, 1, 180f))
    }

    @Test
    fun `early turn within SNAP_M snaps to the corner`() {
        val t = tracker(plan(lShape))
        assertAt(50f, 0f, walk(t, 5, 95f))
        assertAt(100f, 10f, walk(t, 1, 178f))
    }

    @Test
    fun `a turn far from the corner does not snap`() {
        val t = tracker(plan(pts(0, 0, 300, 0, 300, 100)))
        walk(t, 1, 90f)
        assertAt(20f, 0f, walk(t, 1, 180f))
        assertAt(30f, 0f, walk(t, 1, 0f))
    }

    @Test
    fun `turning away leaves the route, replayed from where the turn began`() {
        val t = tracker(plan(straight))
        walk(t, 2, 90f)
        val s = walk(t, 7, 180f)
        assertTrue(s.offRoute)
        assertAt(20f, 70f, s)
        assertAt(20f, 80f, walk(t, 1, 180f))
        assertNull(t.lineHeading()) // off the line: no route alignment
    }

    @Test
    fun `walking back the same corridor slides the dot back`() {
        val t = tracker(plan(straight))
        walk(t, 5, 90f)
        var s = walk(t, 2, 270f)
        assertAt(30f, 0f, s)
        assertTrue(s.backward && !s.offRoute)
        s = walk(t, 1, 90f) // forward again
        assertAt(40f, 0f, s)
        assertFalse(s.backward)
    }

    @Test
    fun `a U-turn the route makes is a corner, not walking back`() {
        val t = tracker(plan(pts(0, 0, 100, 0, 0, 0)))
        walk(t, 10, 90f)
        val s = walk(t, 3, 270f)
        assertAt(70f, 0f, s)
        assertTrue(!s.backward && !s.offRoute)
    }

    @Test
    fun `back past the route start waits there, then leaves the route`() {
        val t = tracker(plan(straight))
        walk(t, 3, 90f)
        assertAt(0f, 0f, walk(t, 4, 270f))
        val s = walk(t, 7, 270f)
        assertTrue("got ${s.position}", s.offRoute && s.position.x < -30f)
    }

    @Test
    fun `after a corner the dot slides back no further than that corner`() {
        val t = tracker(plan(lShape))
        walk(t, 10, 90f); walk(t, 3, 180f) // turned at the corner, 30 px down the second piece
        assertAt(100f, 10f, walk(t, 2, 0f))
        assertAt(100f, 0f, walk(t, 2, 0f))
        assertFalse(t.state().offRoute)
    }

    @Test
    fun `free walking stays in corridors and slides along walls`() {
        val t = tracker(plan(straight)) { _, q -> q.y < 50f } // a room below y = 50
        walk(t, 2, 90f)
        val s = walk(t, 12, 225f)
        assertTrue("got ${s.position}", s.offRoute && s.position.y < 50f && s.position.x < 20f)
    }

    @Test
    fun `walking straight past a corner for OVERSHOOT_M leaves the route`() {
        val t = tracker(plan(lShape))
        val s = walk(t, 26, 90f)
        assertTrue("got ${s.position}", s.offRoute && s.position.x > 150f)
    }

    @Test
    fun `transfer - walk seg 0, stand at the shaft, then walk seg 1 from its start`() {
        val t = tracker(plan(pts(0, 0, 100, 0), pts(0, 0, 0, 100)))
        assertAt(100f, 0f, walk(t, 10, 90f))
        t.goToStep(1)
        var s = walk(t, 5, 90f)
        assertAt(100f, 0f, s)
        assertEquals(2, s.floorLevel)
        assertNull(t.lineHeading())
        t.goToStep(2)
        assertAt(0f, 0f, t.state())
        s = walk(t, 1, 180f)
        assertAt(0f, 10f, s)
        assertEquals(3, s.floorLevel)
    }

    @Test
    fun `per-axis scale and a closed leg for the step learner`() {
        // x = 20 px/m, y = 10 px/m, so one 0.65 m step = 13 px across, 6.5 px down
        val plan2 = plan(lShape, pxPerM = mapOf("f0" to (20f to 10f)), mpp = 1.0)
        assertAt(26f, 0f, walk(tracker(plan2), 2, 90f))
        val t = tracker(plan2)
        walk(t, 7, 90f) // 91 px, the corner is at 100
        val leg = t.onWalked(step, 180f) // turns within SNAP_M: the leg 0 -> 100 px (5 m) is closed
        assertNotNull(leg)
        assertEquals(5f, leg!!.realM, 1e-4f)
        assertEquals(7 * step, leg.countedM, 1e-4f)
        assertAt(100f, 6.5f, t.state())
    }

    @Test
    fun `course heading stays on the old piece while the dot waits at the corner`() {
        val t = tracker(plan(lShape))
        walk(t, 12, 90f)
        assertEquals(90f, t.courseHeading()!!, 0.01f)
        walk(t, 1, 180f)
        assertEquals(180f, t.courseHeading()!!, 0.01f)
    }

    @Test
    fun `off the route a heading close to a corridor axis walks along it`() {
        val t = tracker(plan(straight))
        walk(t, 2, 90f)
        assertTrue(walk(t, 7, 180f).offRoute)
        assertAt(20f, 80f, walk(t, 1, 170f)) // 170° walks straight down the corridor
    }

    // ------------------------------------------------------------ StepLearner

    @Test
    fun `step learner moves k toward real over counted, ignores short legs, clamps outliers`() {
        val sl = StepLearner(0.45f, p.learner)
        assertFalse(sl.onLeg(2f, 3f)) // too short
        assertTrue(sl.onLeg(12f, 10f)) // the user walked 20% more than counted
        assertEquals(0.45f * sqrt(1.2f), sl.k, 1e-5f)
        assertEquals(1, sl.legs)
        val k1 = sl.k
        sl.onLeg(100f, 10f) // absurd leg: ratio clamped to 1.4
        assertEquals(k1 * sqrt(1.4f), sl.k, 1e-5f)
    }

    // ------------------------------------------------------------ HeadingFusion

    @Test
    fun `heading fusion - warm-up, slow compass, gate and relock`() {
        val hf = HeadingFusion(30f, p.fusion)
        assertEquals(70f, hf.update(100f, 10f)!!, 0.01f)
        assertEquals(85f, hf.update(100f, 40f)!!, 0.01f) // warm-up: offset = mean of the first compass samples (60, 30)
        var h = 0f
        repeat(200) { h = hf.update(130f, 40f)!! }
        assertEquals(100f, h, 0.1f) // slow on purpose: the compass only nudges
        // magnetic disturbance: the compass swings 60° while the gyro holds still -> dropped, heading stays
        repeat(10) { h = hf.update(190f, 40f)!! }
        assertEquals(100f, h, 0.1f)
        assertEquals(10, hf.dropped)
        // it keeps disagreeing for RELOCK samples: the gyro drifted, so warm up again from the compass
        repeat(130) { h = hf.update(190f, 40f)!! }
        assertEquals(160f, h, 0.5f)
        assertEquals(90f, HeadingFusion(10f, p.fusion).update(100f, null)!!, 0.01f) // no gyro
        assertNull(HeadingFusion(0f, p.fusion).update(null, 5f)) // no compass yet: unknown
    }

    @Test
    fun `walking along the route corrects a slightly wrong plan north`() {
        val f = HeadingFusion(0f, p.fusion) // real value would be 15
        assertEquals(105f, f.update(105f, 0f)!!, 0.01f)
        repeat(100) { f.alignTo(lineDeg = 90f, yawDeg = 0f) }
        assertTrue(abs(f.update(null, 0f)!! - 90f) < 0.1f)
    }

    // ------------------------------------------------------------ StepDetector (50 Hz synthetic)

    private fun count(signal: (Float) -> Float): List<Float> {
        val d = StepDetector(0.45f, p.step)
        val out = mutableListOf<Float>()
        for (i in 0..250) {
            val t = i / 50f
            out += d.onSample((i * 20_000_000L), 0f, 0f, 9.81f + signal(t))
        }
        return out
    }

    @Test
    fun `walking at 2 steps per second is counted with human step lengths`() {
        val steps = count { 3f * sin(2 * PI * 2 * it).toFloat() }
        assertTrue("got ${steps.size}", steps.size in 8..10)
        steps.forEach { assertTrue("length $it", it in 0.5f..0.85f) }
    }

    @Test
    fun `a single shake or standing still is not a step`() {
        assertEquals(0, count { 6f * exp(-((it - 2f) * (it - 2f)) / 0.005f) }.size)
        assertEquals(0, count { 0.1f * sin(2 * PI * 7 * it).toFloat() }.size)
    }
}
