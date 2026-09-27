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
    fun `hospital map data loads correctly`() = runTest {
        val mapData = getMapUseCase().first()
        assertEquals("Bệnh viện Đa khoa", mapData.hospital.name)
        assertEquals(5, mapData.floors.size)
        assertTrue(mapData.graph.nodes.isNotEmpty())
        assertTrue(mapData.graph.edges.isNotEmpty())
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
}
