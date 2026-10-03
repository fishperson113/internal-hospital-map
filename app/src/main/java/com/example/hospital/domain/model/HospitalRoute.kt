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
    val startRoomId: String? = null,
    val destRoomId: String? = null,
    val distanceMeters: Float = 0f,
    val estimatedDurationSeconds: Int = 0,
    val isCrossFloorStep: Boolean = false,
    val iconType: StepIconType = StepIconType.WALK
)

data class RouteSegment(
    val floorLevel: Int,
    val points: List<Point2D>,
    val length: Float,
    val floorId: String = "tang-$floorLevel"
)

data class TransferInfo(
    val link: String,
    val kind: String,
    val roomA: Room,
    val roomB: Room
)

data class RouteAssumptions(
    val metersPerPx: Double = 0.08,
    val walkSpeedMps: Double = 1.1,
    val floorChangeSec: Double = 40.0
)

data class HospitalRoute(
    val startNode: MapNode,
    val destNode: MapNode,
    val isCrossFloor: Boolean,
    val totalDistanceMeters: Float,
    val estimatedDurationMinutes: Int,
    val steps: List<RouteStep>,
    val pathNodeIds: List<String>,
    val pathNodes: List<MapNode> = emptyList(),
    val segments: List<RouteSegment> = emptyList(),
    val transfer: TransferInfo? = null,
    val assumptions: RouteAssumptions = RouteAssumptions()
)

sealed interface RouteFetchResult {
    data class Success(val route: HospitalRoute) : RouteFetchResult
    data class Error(val errorType: String, val message: String? = null) : RouteFetchResult
}
