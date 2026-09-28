package com.example.hospital.domain.model

enum class StepIconType {
    WALK,
    ELEVATOR,
    STAIRS,
    DESTINATION
}

data class RouteStep(
    val stepNumber: Int,
    val floorLevel: Int,
    val targetFloorLevel: Int = floorLevel,
    val instruction: String,
    val distanceMeters: Float = 0f,
    val estimatedDurationSeconds: Int = 0,
    val isCrossFloorStep: Boolean = false,
    val iconType: StepIconType = StepIconType.WALK
)

data class HospitalRoute(
    val startNode: MapNode,
    val destNode: MapNode,
    val isCrossFloor: Boolean,
    val totalDistanceMeters: Float,
    val estimatedDurationMinutes: Int,
    val steps: List<RouteStep>,
    val pathNodeIds: List<String>,
    val pathNodes: List<MapNode> = emptyList()
)
