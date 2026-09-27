package com.example.hospital.domain.model

enum class TurnDirection {
    START,          // Xuất phát từ phòng/điểm pin
    STRAIGHT,       // Đi thẳng theo hành lang
    TURN_LEFT,      // Rẽ trái
    TURN_RIGHT,     // Rẽ phải
    TAKE_ELEVATOR,  // Đi thang máy
    TAKE_STAIRS,    // Đi thang bộ
    TAKE_ESCALATOR, // Đi thang cuốn
    ARRIVE          // Tới phòng đích
}

data class RouteStep(
    val stepIndex: Int,
    val floorLevel: Int,
    val targetFloorLevel: Int? = null,
    val instruction: String,
    val turnDirection: TurnDirection,
    val distanceMeters: Float,
    val durationSeconds: Int,
    val fromNode: MapNode,
    val toNode: MapNode,
    val connectorType: ConnectorType? = null
) {
    val isCrossFloorStep: Boolean
        get() = targetFloorLevel != null && targetFloorLevel != floorLevel
}
