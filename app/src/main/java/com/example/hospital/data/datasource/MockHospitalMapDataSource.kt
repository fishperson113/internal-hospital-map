package com.example.hospital.data.datasource

import com.example.hospital.domain.model.ConnectorInfo
import com.example.hospital.domain.model.ConnectorType
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.MapEdge
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.NodeStatus
import com.example.hospital.domain.model.NodeType
import com.example.hospital.domain.pathfinding.HospitalGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MockHospitalMapDataSource : HospitalMapDataSource {

    private val hospital = Hospital(
        id = "hosp_01",
        name = "Bệnh viện Đa khoa",
        code = "BVDK",
        address = "201 Nguyễn Chí Thanh, Quận 5, TP. Hồ Chí Minh",
        totalFloors = 5,
        description = "Bệnh viện Đa khoa trung ương với quy mô 5 tầng chuyên khoa"
    )

    private val floors = listOf(
        Floor("f1", hospital.id, 1, "Tầng 1", "Khoa Khám bệnh & Cấp cứu"),
        Floor("f2", hospital.id, 2, "Tầng 2", "Khoa Xét nghiệm & Chẩn đoán hình ảnh"),
        Floor("f3", hospital.id, 3, "Tầng 3", "Khoa Ngoại tổng quát & Thăm dò chức năng"),
        Floor("f4", hospital.id, 4, "Tầng 4", "Khoa Nội tiết, Hô hấp & Da liễu"),
        Floor("f5", hospital.id, 5, "Tầng 5", "Khoa Chấn thương chỉnh hình & Tim mạch")
    )

    private val nodesMap = mutableMapOf<String, MapNode>()
    private val edgesList = mutableListOf<MapEdge>()

    init {
        initializeHospitalGraph()
    }

    private fun initializeHospitalGraph() {
        val allFloorsList = listOf(1, 2, 3, 4, 5)

        // 1. Create Connectors (Thang máy & Thang bộ) on every floor
        for (floor in allFloorsList) {
            val elevA = MapNode(
                id = "elev_a_f$floor",
                floorLevel = floor,
                x = 150f,
                y = 300f,
                name = "Thang máy Tây (Tầng $floor)",
                type = NodeType.CONNECTOR,
                connectorInfo = ConnectorInfo(ConnectorType.ELEVATOR, allFloorsList, transferPenaltySeconds = 8)
            )
            val elevB = MapNode(
                id = "elev_b_f$floor",
                floorLevel = floor,
                x = 850f,
                y = 300f,
                name = "Thang máy Đông (Tầng $floor)",
                type = NodeType.CONNECTOR,
                connectorInfo = ConnectorInfo(ConnectorType.ELEVATOR, allFloorsList, transferPenaltySeconds = 8)
            )
            val stairsA = MapNode(
                id = "stairs_a_f$floor",
                floorLevel = floor,
                x = 100f,
                y = 150f,
                name = "Thang bộ Tây (Tầng $floor)",
                type = NodeType.CONNECTOR,
                connectorInfo = ConnectorInfo(ConnectorType.STAIRS, allFloorsList, transferPenaltySeconds = 20)
            )
            val stairsB = MapNode(
                id = "stairs_b_f$floor",
                floorLevel = floor,
                x = 900f,
                y = 150f,
                name = "Thang bộ Đông (Tầng $floor)",
                type = NodeType.CONNECTOR,
                connectorInfo = ConnectorInfo(ConnectorType.STAIRS, allFloorsList, transferPenaltySeconds = 20)
            )

            nodesMap[elevA.id] = elevA
            nodesMap[elevB.id] = elevB
            nodesMap[stairsA.id] = stairsA
            nodesMap[stairsB.id] = stairsB
        }

        // 2. Connect vertical elevator & stairs shafts across adjacent floors
        for (f in 1..4) {
            val nextF = f + 1
            // Vertical Elevator A edge
            edgesList.add(
                MapEdge(
                    id = "vert_elev_a_${f}_${nextF}",
                    fromNodeId = "elev_a_f$f",
                    toNodeId = "elev_a_f$nextF",
                    distanceMeters = 3.5f,
                    walkingTimeSeconds = 6,
                    isBidirectional = true
                )
            )
            // Vertical Elevator B edge
            edgesList.add(
                MapEdge(
                    id = "vert_elev_b_${f}_${nextF}",
                    fromNodeId = "elev_b_f$f",
                    toNodeId = "elev_b_f$nextF",
                    distanceMeters = 3.5f,
                    walkingTimeSeconds = 6,
                    isBidirectional = true
                )
            )
            // Vertical Stairs A edge
            edgesList.add(
                MapEdge(
                    id = "vert_stairs_a_${f}_${nextF}",
                    fromNodeId = "stairs_a_f$f",
                    toNodeId = "stairs_a_f$nextF",
                    distanceMeters = 12f,
                    walkingTimeSeconds = 18,
                    isBidirectional = true,
                    isWheelchairAccessible = false
                )
            )
            // Vertical Stairs B edge
            edgesList.add(
                MapEdge(
                    id = "vert_stairs_b_${f}_${nextF}",
                    fromNodeId = "stairs_b_f$f",
                    toNodeId = "stairs_b_f$nextF",
                    distanceMeters = 12f,
                    walkingTimeSeconds = 18,
                    isBidirectional = true,
                    isWheelchairAccessible = false
                )
            )
        }

        // 3. Tầng 1 Rooms (including Phòng A)
        val room101 = MapNode(
            id = "room_101",
            floorLevel = 1,
            x = 240f,
            y = 120f,
            name = "Phòng khám Nội tổng quát 1 (Phòng A)",
            type = NodeType.ROOM,
            roomCode = "P.101",
            department = "Khoa Khám bệnh"
        )
        val room102 = MapNode(
            id = "room_102",
            floorLevel = 1,
            x = 380f,
            y = 120f,
            name = "Phòng khám Nội tổng quát 2",
            type = NodeType.ROOM,
            roomCode = "P.102",
            department = "Khoa Khám bệnh"
        )
        val junc1_west = MapNode(
            id = "junc_f1_w",
            floorLevel = 1,
            x = 240f,
            y = 220f,
            name = "Hành lang Tây Tầng 1",
            type = NodeType.JUNCTION
        )
        val junc1_mid = MapNode(
            id = "junc_f1_m",
            floorLevel = 1,
            x = 500f,
            y = 220f,
            name = "Sảnh trung tâm Tầng 1",
            type = NodeType.JUNCTION
        )
        val junc1_east = MapNode(
            id = "junc_f1_e",
            floorLevel = 1,
            x = 800f,
            y = 220f,
            name = "Hành lang Đông Tầng 1",
            type = NodeType.JUNCTION
        )

        nodesMap[room101.id] = room101
        nodesMap[room102.id] = room102
        nodesMap[junc1_west.id] = junc1_west
        nodesMap[junc1_mid.id] = junc1_mid
        nodesMap[junc1_east.id] = junc1_east

        // Tầng 1 Intra-floor edges
        edgesList.add(MapEdge("e1_1", room101.id, junc1_west.id, 10f))
        edgesList.add(MapEdge("e1_2", room102.id, junc1_west.id, 14f))
        edgesList.add(MapEdge("e1_3", junc1_west.id, "elev_a_f1", 12f))
        edgesList.add(MapEdge("e1_4", junc1_west.id, "stairs_a_f1", 15f))
        edgesList.add(MapEdge("e1_5", junc1_west.id, junc1_mid.id, 25f))
        edgesList.add(MapEdge("e1_6", junc1_mid.id, junc1_east.id, 28f))
        edgesList.add(MapEdge("e1_7", junc1_east.id, "elev_b_f1", 12f))
        edgesList.add(MapEdge("e1_8", junc1_east.id, "stairs_b_f1", 15f))

        // 4. Tầng 5 Rooms (matching mockup demo: Phòng B - Chấn thương chỉnh hình 2)
        val room501 = MapNode(
            id = "room_501",
            floorLevel = 5,
            x = 220f,
            y = 120f,
            name = "Phòng khám Chấn thương chỉnh hình 1",
            type = NodeType.ROOM,
            roomCode = "P.501",
            department = "Khoa Chấn thương chỉnh hình"
        )
        val room502 = MapNode(
            id = "room_502",
            floorLevel = 5,
            x = 360f,
            y = 120f,
            name = "Phòng khám Chấn thương chỉnh hình 2 (Phòng B)",
            type = NodeType.ROOM,
            roomCode = "P.502",
            department = "Khoa Chấn thương chỉnh hình"
        )
        val room503 = MapNode(
            id = "room_503",
            floorLevel = 5,
            x = 520f,
            y = 120f,
            name = "Phòng khám Ngoại tổng quát",
            type = NodeType.ROOM,
            roomCode = "P.503",
            department = "Khoa Ngoại"
        )
        val room504 = MapNode(
            id = "room_504",
            floorLevel = 5,
            x = 680f,
            y = 120f,
            name = "Phòng khám Tim mạch",
            type = NodeType.ROOM,
            roomCode = "P.504",
            department = "Khoa Tim mạch"
        )
        val room505 = MapNode(
            id = "room_505",
            floorLevel = 5,
            x = 840f,
            y = 120f,
            name = "Phòng khám Tai Mũi Họng",
            type = NodeType.ROOM,
            roomCode = "P.505",
            department = "Khoa Tai Mũi Họng"
        )

        // Bottom row Tầng 5 (Siêu âm, Xét nghiệm, Căng tin, Mắt)
        val room510 = MapNode(
            id = "room_510",
            floorLevel = 5,
            x = 250f,
            y = 480f,
            name = "Phòng Siêu âm",
            type = NodeType.ROOM,
            roomCode = "P.510",
            department = "Chẩn đoán hình ảnh"
        )
        val room511 = MapNode(
            id = "room_511",
            floorLevel = 5,
            x = 390f,
            y = 480f,
            name = "Phòng Xét nghiệm nhanh",
            type = NodeType.ROOM,
            roomCode = "P.511",
            department = "Khoa Xét nghiệm"
        )
        val room512 = MapNode(
            id = "room_512",
            floorLevel = 5,
            x = 530f,
            y = 480f,
            name = "Căng tin Tầng 5",
            type = NodeType.ROOM,
            roomCode = "P.512",
            department = "Dịch vụ tiện ích"
        )
        val room513 = MapNode(
            id = "room_513",
            floorLevel = 5,
            x = 670f,
            y = 480f,
            name = "Phòng khám Mắt",
            type = NodeType.ROOM,
            roomCode = "P.513",
            department = "Khoa Mắt"
        )

        // Junctions on Tầng 5 around the central Courtyard (Giếng trời)
        val junc5_top_west = MapNode("junc_f5_tw", 5, 250f, 220f, "Hành lang Bắc Tây", NodeType.JUNCTION)
        val junc5_top_mid = MapNode("junc_f5_tm", 5, 360f, 220f, "Hành lang trước P.502", NodeType.JUNCTION)
        val junc5_top_east = MapNode("junc_f5_te", 5, 750f, 220f, "Hành lang Bắc Đông", NodeType.JUNCTION)
        val junc5_bot_west = MapNode("junc_f5_bw", 5, 250f, 380f, "Hành lang Nam Tây", NodeType.JUNCTION)
        val junc5_bot_east = MapNode("junc_f5_be", 5, 750f, 380f, "Hành lang Nam Đông", NodeType.JUNCTION)

        nodesMap[room501.id] = room501
        nodesMap[room502.id] = room502
        nodesMap[room503.id] = room503
        nodesMap[room504.id] = room504
        nodesMap[room505.id] = room505
        nodesMap[room510.id] = room510
        nodesMap[room511.id] = room511
        nodesMap[room512.id] = room512
        nodesMap[room513.id] = room513

        nodesMap[junc5_top_west.id] = junc5_top_west
        nodesMap[junc5_top_mid.id] = junc5_top_mid
        nodesMap[junc5_top_east.id] = junc5_top_east
        nodesMap[junc5_bot_west.id] = junc5_bot_west
        nodesMap[junc5_bot_east.id] = junc5_bot_east

        // Connect Tầng 5 rooms & corridor
        edgesList.add(MapEdge("e5_1", room501.id, junc5_top_west.id, 10f))
        edgesList.add(MapEdge("e5_2", room502.id, junc5_top_mid.id, 8f)) // Entrance to Room B!
        edgesList.add(MapEdge("e5_3", junc5_top_west.id, junc5_top_mid.id, 12f))
        edgesList.add(MapEdge("e5_4", junc5_top_mid.id, junc5_top_east.id, 25f))
        edgesList.add(MapEdge("e5_5", room503.id, junc5_top_east.id, 15f))
        edgesList.add(MapEdge("e5_6", room504.id, junc5_top_east.id, 12f))
        edgesList.add(MapEdge("e5_7", room505.id, junc5_top_east.id, 14f))

        // Connect Elevators to corridor
        edgesList.add(MapEdge("e5_8", "elev_a_f5", junc5_top_west.id, 14f))
        edgesList.add(MapEdge("e5_9", "stairs_a_f5", junc5_top_west.id, 18f))
        edgesList.add(MapEdge("e5_10", "elev_b_f5", junc5_top_east.id, 14f))
        edgesList.add(MapEdge("e5_11", "stairs_b_f5", junc5_top_east.id, 18f))

        // Vertical side corridors around courtyard
        edgesList.add(MapEdge("e5_12", junc5_top_west.id, junc5_bot_west.id, 20f))
        edgesList.add(MapEdge("e5_13", junc5_top_east.id, junc5_bot_east.id, 20f))

        // Bottom corridor
        edgesList.add(MapEdge("e5_14", room510.id, junc5_bot_west.id, 10f))
        edgesList.add(MapEdge("e5_15", room511.id, junc5_bot_west.id, 14f))
        edgesList.add(MapEdge("e5_16", room512.id, junc5_bot_east.id, 16f))
        edgesList.add(MapEdge("e5_17", room513.id, junc5_bot_east.id, 12f))
        edgesList.add(MapEdge("e5_18", junc5_bot_west.id, junc5_bot_east.id, 35f))
    }

    override suspend fun getHospital(): Hospital = withContext(Dispatchers.IO) {
        hospital
    }

    override suspend fun getFloors(hospitalId: String): List<Floor> = withContext(Dispatchers.IO) {
        floors
    }

    override suspend fun getHospitalGraph(hospitalId: String): HospitalGraph = withContext(Dispatchers.IO) {
        HospitalGraph(
            nodes = nodesMap.toMap(),
            edges = edgesList.toList()
        )
    }

    override suspend fun updateNodeStatus(nodeId: String, status: NodeStatus) = withContext(Dispatchers.IO) {
        val node = nodesMap[nodeId] ?: return@withContext
        nodesMap[nodeId] = node.copy(status = status, lastVerifiedAt = System.currentTimeMillis())
    }

    override suspend fun reportNodeIssue(nodeId: String, notes: String) = withContext(Dispatchers.IO) {
        val node = nodesMap[nodeId] ?: return@withContext
        nodesMap[nodeId] = node.copy(reportCount = node.reportCount + 1, notes = notes)
    }
}
