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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HospitalPathfindingTest {

    private val dataSource = MockHospitalMapDataSource()
    private val repository = HospitalMapRepositoryImpl(dataSource)
    private val strategy = DijkstraPathfindingStrategy()
    private val findRouteUseCase = FindHospitalRouteUseCase(repository, strategy)
    private val getMapUseCase = GetHospitalMapUseCase(repository)

    @Test
    fun `floor 1 is marked as todo and has no rooms`() = runTest {
        val mapData = getMapUseCase().first()
        val floor1 = mapData.floors.find { it.level == 1 }
        assertNotNull(floor1)
        assertTrue(floor1!!.isTodo)
        assertTrue(floor1.rooms.isEmpty())
    }

    @Test
    fun `floor 2 and floor 3 have rooms from polygon json`() = runTest {
        val mapData = getMapUseCase().first()
        val floor2 = mapData.floors.find { it.level == 2 }
        val floor3 = mapData.floors.find { it.level == 3 }
        assertNotNull(floor2)
        assertNotNull(floor3)
        assertTrue(floor2!!.rooms.size > 20)
        assertTrue(floor3!!.rooms.size > 20)
        assertTrue(floor2.rooms.all { it.polygon.isNotEmpty() })
        assertTrue(floor3.rooms.all { it.polygon.isNotEmpty() })
    }

    @Test
    fun `intra-floor pathfinding on floor 2 finds valid path`() = runTest {
        // tang-2_48 (Quầy đăng ký khám) to tang-2_59 (Phòng khám nội)
        val route = findRouteUseCase("tang-2_48", "tang-2_59")
        assertNotNull(route)
        assertEquals(false, route!!.isCrossFloor)
        assertTrue(route.steps.isNotEmpty())
        assertTrue(route.segments.isNotEmpty())
        assertTrue(route.segments.first().points.size >= 2)
    }

    @Test
    fun `cross-floor pathfinding from Floor 2 to Floor 3 via shaft`() = runTest {
        // tang-2_48 (Quầy đăng ký khám) to tang-3_10 (Khu vực chờ)
        val route = findRouteUseCase("tang-2_48", "tang-3_10")
        assertNotNull(route)
        assertEquals(true, route!!.isCrossFloor)
        assertEquals(2, route.startNode.floorLevel)
        assertEquals(3, route.destNode.floorLevel)

        // Verify steps contain elevator / stairs vertical transfer
        val crossFloorSteps = route.steps.filter { it.isCrossFloorStep }
        assertTrue("Route must contain at least one vertical floor transition", crossFloorSteps.isNotEmpty())
        val transferStep = crossFloorSteps.first()
        assertEquals(2, transferStep.floorLevel)
        assertEquals(3, transferStep.targetFloorLevel)
    }

    @Test
    fun `bilingual room search works for Floor 2 and Floor 3 in English and Vietnamese`() = runTest {
        val resultsVi = repository.searchRooms("Phòng khám")
        assertTrue("Search for 'Phòng khám' should return clinics", resultsVi.isNotEmpty())

        val resultsEn = repository.searchRooms("ELEVATOR")
        assertTrue("Search for 'ELEVATOR' should return elevator rooms", resultsEn.isNotEmpty())

        val resultsSurgery = repository.searchRooms("PHÒNG MỔ")
        assertTrue("Search for 'PHÒNG MỔ' should return operating room", resultsSurgery.isNotEmpty())
    }

    @Test
    fun `english route instructions use English room names`() = runTest {
        val route = findRouteUseCase(
            "tang-2_48",
            "tang-2_59",
            com.example.hospital.presentation.util.AppLanguage.ENGLISH
        )
        assertNotNull(route)
        val firstStep = route!!.steps.first()
        assertTrue("Instruction must mention Depart from", firstStep.instruction.contains("Depart from") || firstStep.instruction.contains("RECEPTION"))
    }
}
