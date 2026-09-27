package com.example.hospital.domain.model

enum class EdgeStatus {
    ACTIVE,   // Hành lang thông thoáng
    BLOCKED   // Hành lang đang thi công / chặn tạm thời
}

data class MapEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Float,
    val walkingTimeSeconds: Int = (distanceMeters / 1.1f).toInt(), // Giả định tốc độ đi bộ 1.1 m/s
    val isBidirectional: Boolean = true,
    val isWheelchairAccessible: Boolean = true,
    val status: EdgeStatus = EdgeStatus.ACTIVE,
    val lastVerifiedAt: Long = System.currentTimeMillis()
)
