package com.example.hospital.domain.model

data class NavigationRoute(
    val startNode: MapNode,
    val destNode: MapNode,
    val nodesPath: List<MapNode>,
    val steps: List<RouteStep>,
    val totalDistanceMeters: Float,
    val totalDurationSeconds: Int
) {
    val isCrossFloor: Boolean
        get() = startNode.floorLevel != destNode.floorLevel

    val floorLevelsInvolved: List<Int>
        get() = nodesPath.map { it.floorLevel }.distinct().sorted()

    fun getStepsForFloor(floorLevel: Int): List<RouteStep> {
        return steps.filter { it.floorLevel == floorLevel }
    }
}
