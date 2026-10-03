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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorPdrTrackerTest {

    // metersPerPx chosen so one step (0.65 m) = 10 plan px
    private fun route(segments: List<RouteSegment>, transfer: TransferInfo? = null) = HospitalRoute(
        startNode = MapNode("s", 2, 0f, 0f, "S"), destNode = MapNode("d", 2, 0f, 0f, "D"),
        isCrossFloor = transfer != null, totalDistanceMeters = 0f, estimatedDurationMinutes = 1,
        steps = emptyList(), pathNodeIds = emptyList(), segments = segments, transfer = transfer,
        assumptions = RouteAssumptions(metersPerPx = 0.065)
    )

    @Test
    fun `dot starts on the route start and slides along it, never off it`() {
        val pdr = IndoorPdrTracker()
        assertNull(pdr.onStep()) // no directions yet: steps do nothing

        val r = route(listOf(RouteSegment(2, listOf(Point2D(0f, 0f), Point2D(100f, 0f), Point2D(100f, 100f)), 13f)))
        assertEquals(Point2D(0f, 0f), pdr.initialize(r)!!.position)

        var s = pdr.onStep()!!
        repeat(14) { s = pdr.onStep()!! } // 150 px: around the corner
        assertEquals(100f, s.position.x, 0.01f)
        assertEquals(50f, s.position.y, 0.01f)
        assertFalse(s.hasArrived)

        repeat(20) { s = pdr.onStep()!! } // walked past the end: stays on the destination
        assertEquals(Point2D(100f, 100f), s.position)
        assertTrue(s.hasArrived)
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
        repeat(10) { pdr.onStep() }
        assertEquals(2, pdr.onStep()!!.floorLevel) // still floor 2, waiting at the shaft

        val atShaft = pdr.goToStep(1)!! // "Lên tầng 3"
        assertEquals(Point2D(30f, 0f), atShaft.position)
        assertNull(pdr.onStep()) // in the shaft: steps do not move the dot

        val upstairs = pdr.goToStep(2)!!
        assertEquals(3, upstairs.floorLevel)
        assertEquals(Point2D(5f, 5f), upstairs.position)
        assertEquals(15f, pdr.onStep()!!.position.x, 0.01f)
    }
}
