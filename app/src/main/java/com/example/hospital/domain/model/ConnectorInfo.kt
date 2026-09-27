package com.example.hospital.domain.model

enum class ConnectorType {
    ELEVATOR,  // Thang máy
    STAIRS,    // Thang bộ
    ESCALATOR  // Thang cuốn
}

data class ConnectorInfo(
    val type: ConnectorType,
    val connectedFloorLevels: List<Int>, // Danh sách các tầng thang này kết nối tới (e.g. 1..5)
    val transferPenaltySeconds: Int = 15 // Thời gian chờ / đổi tầng ước tính (giây)
)
