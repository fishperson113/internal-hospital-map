package com.example

import com.example.hospital.data.datasource.MockHospitalMapDataSource
import com.example.hospital.data.repository.HospitalMapRepositoryImpl
import com.example.hospital.domain.pathfinding.DijkstraPathfindingStrategy
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HospitalPathfindingTest {

    private val dataSource = MockHospitalMapDataSource()
    private val repository = HospitalMapRepositoryImpl(dataSource)
    private val strategy = DijkstraPathfindingStrategy()
    private val findRouteUseCase = FindHospitalRouteUseCase(repository, strategy)
    private val getMapUseCase = GetHospitalMapUseCase(repository)

    @Test
    fun `straight line path down main hallway simplifies redundant nodes`() = runTest {
        // Main entrance to You are here (both along x = 0.45f)
        val route = findRouteUseCase("room_main_entrance", "room_you_are_here")
        assertNotNull(route)
        assertEquals(2, route!!.pathNodes.size)
        assertEquals(0.45f, route.pathNodes.first().x, 0.01f)
        assertEquals(0.45f, route.pathNodes.last().x, 0.01f)
    }

    @Test
    fun `path to emergency B stays on corridors and has valid steps`() = runTest {
        val route = findRouteUseCase("room_you_are_here", "room_emergency_b")
        assertNotNull(route)
        assertTrue(route!!.pathNodes.size >= 2)
        assertEquals("room_you_are_here", route.steps.first().startRoomId)
        assertEquals("room_emergency_b", route.steps.first().destRoomId)
    }

    @Test
    fun `intra-floor pathfinding finds path on floor 1`() = runTest {
        // Room 101 to Room 102
        val route = findRouteUseCase("room_101", "room_102")
        assertNotNull(route)
        assertEquals(false, route!!.isCrossFloor)
        assertTrue(route.steps.isNotEmpty())
        assertTrue(route.totalDistanceMeters > 0)
    }

    @Test
    fun `cross-floor pathfinding from Floor 1 (Room A) to Floor 5 (Room B)`() = runTest {
        // Room 101 (Phòng A) to Room 502 (Phòng B)
        val route = findRouteUseCase("room_101", "room_502")
        assertNotNull(route)
        assertEquals(true, route!!.isCrossFloor)
        assertEquals(1, route.startNode.floorLevel)
        assertEquals(5, route.destNode.floorLevel)

        // Verify steps contain elevator / cross floor step
        val crossFloorSteps = route.steps.filter { it.isCrossFloorStep }
        assertTrue("Route must contain at least one vertical floor transition", crossFloorSteps.isNotEmpty())

        val elevatorStep = crossFloorSteps.first()
        assertEquals(1, elevatorStep.floorLevel)
        assertEquals(5, elevatorStep.targetFloorLevel)
        assertTrue(elevatorStep.instruction.contains("Tầng 5"))
    }

    @Test
    fun `english route instructions use You are here instead of Ban o day`() = runTest {
        val route = findRouteUseCase(
            "room_you_are_here",
            "room_emergency_b",
            com.example.hospital.presentation.util.AppLanguage.ENGLISH
        )
        assertNotNull(route)
        val firstStep = route!!.steps.first()
        assertTrue("First step must mention 'You are here'", firstStep.instruction.contains("You are here"))
        assertTrue("First step must NOT contain Vietnamese 'Bạn ở đây'", !firstStep.instruction.contains("Bạn ở đây"))

        val lastStep = route.steps.last()
        assertTrue("Last step must be in English", lastStep.instruction.contains("EMERGENCY B") || lastStep.instruction.contains("Emergency"))
    }

    @Test
    fun `bilingual room search works in English`() = runTest {
        val results = repository.searchRooms("You are here")
        assertTrue("Search for 'You are here' should return room_you_are_here", results.any { it.id == "room_you_are_here" })

        val emergencyResults = repository.searchRooms("Emergency")
        assertTrue("Search for 'Emergency' should return emergency rooms", emergencyResults.any { it.id == "room_emergency_b" })
    }
}
