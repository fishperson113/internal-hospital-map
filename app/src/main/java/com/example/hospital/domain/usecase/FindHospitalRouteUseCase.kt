package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.RouteStep
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.domain.pathfinding.PathfindingStrategy
import kotlin.math.roundToInt

class FindHospitalRouteUseCase(
    private val repository: HospitalMapRepository,
    private val pathfindingStrategy: PathfindingStrategy
) {

    suspend operator fun invoke(startRoomOrNodeId: String, destRoomOrNodeId: String): HospitalRoute? {
        val graph = repository.getGraph()
        val startRoom = repository.getRoomById(startRoomOrNodeId)
        val destRoom = repository.getRoomById(destRoomOrNodeId)

        val startNodeId = startRoom?.nodeId ?: startRoomOrNodeId
        val destNodeId = destRoom?.nodeId ?: destRoomOrNodeId

        val startNode = graph.nodes[startNodeId] ?: return null
        val destNode = graph.nodes[destNodeId] ?: return null

        val pathNodes = pathfindingStrategy.findPath(graph, startNodeId, destNodeId) ?: return null
        if (pathNodes.isEmpty()) return null

        var totalDistance = 0f
        val edgeMap = graph.edges.associateBy { "${it.fromNodeId}_${it.toNodeId}" }
        val reverseEdgeMap = graph.edges.associateBy { "${it.toNodeId}_${it.fromNodeId}" }

        for (i in 0 until pathNodes.size - 1) {
            val from = pathNodes[i].id
            val to = pathNodes[i + 1].id
            val edge = edgeMap["${from}_${to}"] ?: reverseEdgeMap["${from}_${to}"]
            totalDistance += edge?.distanceMeters ?: 10f
        }

        val isCrossFloor = startNode.floorLevel != destNode.floorLevel
        val steps = buildSteps(pathNodes, startRoom?.name ?: startNode.label, destRoom?.name ?: destNode.label)

        val durationMinutes = ((totalDistance / 1.0f + (if (isCrossFloor) 40f else 0f)) / 60f).roundToInt().coerceAtLeast(1)

        return HospitalRoute(
            startNode = startNode,
            destNode = destNode,
            isCrossFloor = isCrossFloor,
            totalDistanceMeters = totalDistance,
            estimatedDurationMinutes = durationMinutes,
            steps = steps,
            pathNodeIds = pathNodes.map { it.id },
            pathNodes = pathNodes
        )
    }

    private fun buildSteps(
        pathNodes: List<MapNode>,
        startName: String,
        destName: String
    ): List<RouteStep> {
        val steps = mutableListOf<RouteStep>()
        var stepIndex = 1

        if (pathNodes.isEmpty()) return emptyList()

        val startNode = pathNodes.first()
        val destNode = pathNodes.last()

        // 1. Initial Walking Step on start floor
        val firstFloor = startNode.floorLevel
        steps.add(
            RouteStep(
                stepNumber = stepIndex++,
                floorLevel = firstFloor,
                targetFloorLevel = firstFloor,
                instruction = "Đi từ $startName (Tầng $firstFloor)",
                distanceMeters = 30f,
                estimatedDurationSeconds = 25,
                isCrossFloorStep = false,
                iconType = StepIconType.WALK
            )
        )

        // Find floor transitions
        for (i in 0 until pathNodes.size - 1) {
            val curr = pathNodes[i]
            val next = pathNodes[i + 1]

            if (curr.floorLevel != next.floorLevel) {
                val isElevator = curr.isElevator || next.isElevator
                val iconType = if (isElevator) StepIconType.ELEVATOR else StepIconType.STAIRS
                val vehicleName = if (isElevator) "thang máy" else "thang bộ"
                val direction = if (next.floorLevel > curr.floorLevel) "lên" else "xuống"

                steps.add(
                    RouteStep(
                        stepNumber = stepIndex++,
                        floorLevel = curr.floorLevel,
                        targetFloorLevel = next.floorLevel,
                        instruction = "Đi $vehicleName $direction Tầng ${next.floorLevel}",
                        distanceMeters = 0f,
                        estimatedDurationSeconds = if (isElevator) 15 else 35,
                        isCrossFloorStep = true,
                        iconType = iconType
                    )
                )
            }
        }

        // Final Destination Step
        val finalFloor = destNode.floorLevel
        steps.add(
            RouteStep(
                stepNumber = stepIndex++,
                floorLevel = finalFloor,
                targetFloorLevel = finalFloor,
                instruction = "Đi đến $destName (Tầng $finalFloor)",
                distanceMeters = 20f,
                estimatedDurationSeconds = 18,
                isCrossFloorStep = false,
                iconType = StepIconType.DESTINATION
            )
        )

        return steps
    }
}
