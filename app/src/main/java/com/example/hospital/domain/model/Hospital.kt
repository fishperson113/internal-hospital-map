package com.example.hospital.domain.model

enum class RoomCategory(val displayName: String) {
    EMERGENCY("Cấp cứu"),
    CLINIC("Khám bệnh"),
    DIAGNOSTIC("Chẩn đoán hình ảnh"),
    PHARMACY("Dược & Thuốc"),
    RECEPTION("Tiếp đón & Hành chính"),
    ELEVATOR("Thang máy"),
    STAIRS("Thang bộ"),
    RESTROOM("Vệ sinh"),
    FACILITY("Tiện ích & Khác")
}

data class Hospital(
    val id: String = "hospital_main",
    val name: String = "Bệnh viện Đa khoa",
    val address: String = "Sơ đồ chỉ đường nội viện"
)

data class Room(
    val id: String,
    val code: String,
    val name: String,
    val floorLevel: Int,
    val category: RoomCategory,
    val description: String = "",
    val nodeId: String,
    val boundsLeft: Float = 0f,
    val boundsTop: Float = 0f,
    val boundsWidth: Float = 0f,
    val boundsHeight: Float = 0f
)

data class Floor(
    val level: Int,
    val name: String,
    val description: String,
    val rooms: List<Room>
)

data class MapNode(
    val id: String,
    val floorLevel: Int,
    val x: Float,
    val y: Float,
    val label: String,
    val isElevator: Boolean = false,
    val isStairs: Boolean = false,
    val roomId: String? = null
)

data class MapEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Float,
    val isCrossFloor: Boolean = false
)

data class Graph(
    val nodes: Map<String, MapNode>,
    val edges: List<MapEdge>
)

data class HospitalMapData(
    val hospital: Hospital,
    val floors: List<Floor>,
    val graph: Graph
)
