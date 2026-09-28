package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.RouteStep
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.domain.pathfinding.PathfindingStrategy
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import kotlin.math.roundToInt

class FindHospitalRouteUseCase(
    private val repository: HospitalMapRepository,
    private val pathfindingStrategy: PathfindingStrategy
) {

    suspend operator fun invoke(
        startRoomOrNodeId: String,
        destRoomOrNodeId: String,
        language: AppLanguage = AppLanguage.VIETNAMESE
    ): HospitalRoute? {
        val graph = repository.getGraph()
        val startRoom = repository.getRoomById(startRoomOrNodeId)
        val destRoom = repository.getRoomById(destRoomOrNodeId)

        val startNodeId = startRoom?.nodeId ?: startRoomOrNodeId
        val destNodeId = destRoom?.nodeId ?: destRoomOrNodeId

        val startNode = graph.nodes[startNodeId] ?: return null
        val destNode = graph.nodes[destNodeId] ?: return null

        val pathNodes = pathfindingStrategy.findPath(graph, startNodeId, destNodeId) ?: return null
        if (pathNodes.isEmpty()) return null

        val allRooms = repository.getAllRooms()
        val simplifiedNodes = com.example.hospital.domain.pathfinding.PathSimplifier.simplify(
            pathNodes = pathNodes,
            rooms = allRooms,
            startRoomId = startRoom?.id,
            destRoomId = destRoom?.id
        )

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
        val startDisplayName = startRoom?.let { HospitalLocalization.getRoomDisplayName(it.id, it.name, language) }
            ?: HospitalLocalization.getRoomDisplayName(startNode.id, startNode.label, language)
        val destDisplayName = destRoom?.let { HospitalLocalization.getRoomDisplayName(it.id, it.name, language) }
            ?: HospitalLocalization.getRoomDisplayName(destNode.id, destNode.label, language)

        val steps = buildSteps(
            simplifiedNodes,
            startDisplayName,
            destDisplayName,
            startRoom?.id ?: startNode.roomId ?: startNode.id,
            destRoom?.id ?: destNode.roomId ?: destNode.id,
            language
        )

        val durationMinutes = ((totalDistance / 1.0f + (if (isCrossFloor) 40f else 0f)) / 60f).roundToInt().coerceAtLeast(1)

        return HospitalRoute(
            startNode = startNode,
            destNode = destNode,
            isCrossFloor = isCrossFloor,
            totalDistanceMeters = totalDistance,
            estimatedDurationMinutes = durationMinutes,
            steps = steps,
            pathNodeIds = simplifiedNodes.map { it.id },
            pathNodes = simplifiedNodes
        )
    }

    private fun buildSteps(
        pathNodes: List<MapNode>,
        startName: String,
        destName: String,
        startRoomId: String?,
        destRoomId: String?,
        language: AppLanguage
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
                instruction = HospitalLocalization.getStepWalkFromInstruction(startName, firstFloor, language),
                startRoomId = startRoomId,
                destRoomId = destRoomId,
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
                val isUp = next.floorLevel > curr.floorLevel
                val instruction = if (isElevator) {
                    HospitalLocalization.getStepElevatorInstruction(isUp, next.floorLevel, language)
                } else {
                    HospitalLocalization.getStepStairsInstruction(isUp, next.floorLevel, language)
                }

                steps.add(
                    RouteStep(
                        stepNumber = stepIndex++,
                        floorLevel = curr.floorLevel,
                        targetFloorLevel = next.floorLevel,
                        instruction = instruction,
                        startRoomId = startRoomId,
                        destRoomId = destRoomId,
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
                instruction = HospitalLocalization.getStepDestinationInstruction(destName, finalFloor, language),
                startRoomId = startRoomId,
                destRoomId = destRoomId,
                distanceMeters = 20f,
                estimatedDurationSeconds = 18,
                isCrossFloorStep = false,
                iconType = StepIconType.DESTINATION
            )
        )

        return steps
    }
}
