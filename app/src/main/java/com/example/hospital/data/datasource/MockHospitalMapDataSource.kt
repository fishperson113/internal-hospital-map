package com.example.hospital.data.datasource

import com.example.hospital.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockHospitalMapDataSource : HospitalMapDataSource {

    private val hospital = Hospital(
        id = "hoan_my_sg",
        name = "Bệnh viện Đa khoa",
        address = "Sơ đồ toà nhà"
    )

    private val floor1Todo = Floor(
        level = 1,
        name = "Tầng 1",
        description = "Chưa có dữ liệu sơ đồ cho tầng này (TODO)",
        rooms = emptyList(),
        floorId = "tang-1",
        width = 1795f,
        height = 734f,
        isTodo = true
    )

    private val floor2 = BuildingJsonParser.parseFloor(BuildingJsonRawData.TANG_2_JSON)
    private val floor3 = BuildingJsonParser.parseFloor(BuildingJsonRawData.TANG_3_JSON)

    private val allFloors = listOf(floor1Todo, floor2, floor3)

    // Build Graph nodes and edges for compatibility with existing usecases
    private val graphNodes = mutableMapOf<String, MapNode>().apply {
        for (floor in allFloors) {
            for (room in floor.rooms) {
                val poly = room.polygon
                val cx = if (poly.isNotEmpty()) poly.map { it.x }.average().toFloat() else 0f
                val cy = if (poly.isNotEmpty()) poly.map { it.y }.average().toFloat() else 0f
                put(
                    room.nodeId,
                    MapNode(
                        id = room.nodeId,
                        floorLevel = room.floorLevel,
                        x = cx,
                        y = cy,
                        label = room.name,
                        isElevator = room.kind == "elevator",
                        isStairs = room.kind == "stair",
                        roomId = room.id
                    )
                )
            }
        }
    }

    private val graphEdges = mutableListOf<MapEdge>().apply {
        // Multi-floor shafts connecting Floor 2 and Floor 3
        val f2Shafts = floor2.rooms.filter { it.link != null }.associateBy { it.link!! }
        val f3Shafts = floor3.rooms.filter { it.link != null }.associateBy { it.link!! }

        for ((link, r2) in f2Shafts) {
            val r3 = f3Shafts[link]
            if (r3 != null) {
                add(
                    MapEdge(
                        fromNodeId = r2.nodeId,
                        toNodeId = r3.nodeId,
                        distanceMeters = 30f,
                        isCrossFloor = true
                    )
                )
            }
        }
    }

    override fun getHospitalMapData(): Flow<HospitalMapData> {
        return flowOf(
            HospitalMapData(
                hospital = hospital,
                floors = allFloors,
                graph = Graph(graphNodes, graphEdges)
            )
        )
    }
}
