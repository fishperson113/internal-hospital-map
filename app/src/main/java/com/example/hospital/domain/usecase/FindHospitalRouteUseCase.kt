package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.*
import com.example.hospital.domain.pathfinding.GridAStarPathfinder
import com.example.hospital.domain.pathfinding.PathfindingStrategy
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import kotlinx.coroutines.flow.first
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
        val mapData = repository.getHospitalMap().first()
        val graph = mapData.graph
        val startRoom = repository.getRoomById(startRoomOrNodeId)
        val destRoom = repository.getRoomById(destRoomOrNodeId)

        val floorsByLevel = mapData.floors.associateBy { it.level }

        // 1. Grid A* Routing if rooms have polygon data
        if (startRoom != null && destRoom != null && startRoom.polygon.isNotEmpty() && destRoom.polygon.isNotEmpty()) {
            val startFloor = floorsByLevel[startRoom.floorLevel]
            val destFloor = floorsByLevel[destRoom.floorLevel]

            if (startFloor != null && destFloor != null && !startFloor.isTodo && !destFloor.isTodo) {
                if (startFloor.level == destFloor.level) {
                    val pathResult = GridAStarPathfinder.findPathOnFloor(startFloor, startRoom, destRoom)
                    if (pathResult != null) {
                        return buildSingleFloorRoute(startFloor, startRoom, destRoom, pathResult, language)
                    }
                } else {
                    val multiFloorRoute = findMultiFloorRoute(startFloor, destFloor, startRoom, destRoom, language)
                    if (multiFloorRoute != null) {
                        return multiFloorRoute
                    }
                }
            }
        }

        // 2. Fallback to Graph-based Dijkstra routing
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

        val steps = buildFallbackSteps(
            simplifiedNodes,
            startDisplayName,
            destDisplayName,
            startRoom?.id ?: startNode.roomId ?: startNode.id,
            destRoom?.id ?: destNode.roomId ?: destNode.id,
            language
        )

        val durationMinutes = ((totalDistance / 1.0f + (if (isCrossFloor) 40f else 0f)) / 60f).roundToInt().coerceAtLeast(1)

        val segment = RouteSegment(
            floorLevel = startNode.floorLevel,
            points = simplifiedNodes.map { Point2D(it.x, it.y) },
            length = totalDistance
        )

        return HospitalRoute(
            startNode = startNode,
            destNode = destNode,
            isCrossFloor = isCrossFloor,
            totalDistanceMeters = totalDistance,
            estimatedDurationMinutes = durationMinutes,
            steps = steps,
            pathNodeIds = simplifiedNodes.map { it.id },
            pathNodes = simplifiedNodes,
            segments = listOf(segment)
        )
    }

    private fun buildSingleFloorRoute(
        floor: Floor,
        startRoom: Room,
        destRoom: Room,
        pathResult: GridAStarPathfinder.FloorPathResult,
        language: AppLanguage
    ): HospitalRoute {
        val startDisplayName = HospitalLocalization.getRoomDisplayName(startRoom.id, startRoom.name, language)
        val destDisplayName = HospitalLocalization.getRoomDisplayName(destRoom.id, destRoom.name, language)
        val distMeters = pathResult.length * 0.1f
        val durationMins = (distMeters / 60f).roundToInt().coerceAtLeast(1)

        val steps = listOf(
            RouteStep(
                stepNumber = 1,
                floorLevel = floor.level,
                targetFloorLevel = floor.level,
                instruction = HospitalLocalization.getStepWalkFromInstruction(startDisplayName, floor.level, language),
                startRoomId = startRoom.id,
                destRoomId = destRoom.id,
                distanceMeters = distMeters * 0.6f,
                estimatedDurationSeconds = (durationMins * 35),
                isCrossFloorStep = false,
                iconType = StepIconType.WALK
            ),
            RouteStep(
                stepNumber = 2,
                floorLevel = floor.level,
                targetFloorLevel = floor.level,
                instruction = HospitalLocalization.getStepDestinationInstruction(destDisplayName, floor.level, language),
                startRoomId = startRoom.id,
                destRoomId = destRoom.id,
                distanceMeters = distMeters * 0.4f,
                estimatedDurationSeconds = (durationMins * 25),
                isCrossFloorStep = false,
                iconType = StepIconType.DESTINATION
            )
        )

        val startNode = MapNode(startRoom.nodeId, floor.level, pathResult.points.first().x, pathResult.points.first().y, startRoom.name, roomId = startRoom.id)
        val destNode = MapNode(destRoom.nodeId, floor.level, pathResult.points.last().x, pathResult.points.last().y, destRoom.name, roomId = destRoom.id)

        val segment = RouteSegment(floor.level, pathResult.points, pathResult.length)

        return HospitalRoute(
            startNode = startNode,
            destNode = destNode,
            isCrossFloor = false,
            totalDistanceMeters = distMeters,
            estimatedDurationMinutes = durationMins,
            steps = steps,
            pathNodeIds = listOf(startNode.id, destNode.id),
            pathNodes = listOf(startNode, destNode),
            segments = listOf(segment),
            transfer = null
        )
    }

    private fun findMultiFloorRoute(
        floorA: Floor,
        floorB: Floor,
        startRoom: Room,
        destRoom: Room,
        language: AppLanguage
    ): HospitalRoute? {
        val shaftsA = floorA.rooms.filter { it.link != null }.associateBy { it.link!! }
        val shaftsB = floorB.rooms.filter { it.link != null }.associateBy { it.link!! }
        val commonLinks = shaftsA.keys.intersect(shaftsB.keys)

        var bestLength = Float.MAX_VALUE
        var bestSeg1: GridAStarPathfinder.FloorPathResult? = null
        var bestSeg2: GridAStarPathfinder.FloorPathResult? = null
        var bestTransfer: TransferInfo? = null

        for (link in commonLinks) {
            val rA = shaftsA[link] ?: continue
            val rB = shaftsB[link] ?: continue

            val s1 = GridAStarPathfinder.findPathOnFloor(floorA, startRoom, rA) ?: continue
            val s2 = GridAStarPathfinder.findPathOnFloor(floorB, rB, destRoom) ?: continue

            val totalLen = s1.length + s2.length
            if (totalLen < bestLength) {
                bestLength = totalLen
                bestSeg1 = s1
                bestSeg2 = s2
                bestTransfer = TransferInfo(link, rA.kind ?: "elevator", rA, rB)
            }
        }

        if (bestSeg1 == null || bestSeg2 == null || bestTransfer == null) return null

        val startDisplayName = HospitalLocalization.getRoomDisplayName(startRoom.id, startRoom.name, language)
        val destDisplayName = HospitalLocalization.getRoomDisplayName(destRoom.id, destRoom.name, language)
        val shaftDisplayName = HospitalLocalization.getRoomDisplayName(bestTransfer.roomA.id, bestTransfer.roomA.name, language)

        val totalMeters = bestLength * 0.1f + 20f
        val durationMins = (totalMeters / 50f).roundToInt().coerceAtLeast(1)
        val isUp = floorB.level > floorA.level

        val steps = listOf(
            RouteStep(
                stepNumber = 1,
                floorLevel = floorA.level,
                targetFloorLevel = floorA.level,
                instruction = "${floorA.name}: " + if (language == AppLanguage.VIETNAMESE) "Đi tới $shaftDisplayName" else "Depart to $shaftDisplayName",
                startRoomId = startRoom.id,
                destRoomId = bestTransfer.roomA.id,
                distanceMeters = bestSeg1.length * 0.1f,
                estimatedDurationSeconds = 30,
                isCrossFloorStep = false,
                iconType = StepIconType.WALK
            ),
            RouteStep(
                stepNumber = 2,
                floorLevel = floorA.level,
                targetFloorLevel = floorB.level,
                instruction = if (bestTransfer.kind == "elevator") {
                    HospitalLocalization.getStepElevatorInstruction(isUp, floorB.level, language)
                } else {
                    HospitalLocalization.getStepStairsInstruction(isUp, floorB.level, language)
                },
                startRoomId = bestTransfer.roomA.id,
                destRoomId = bestTransfer.roomB.id,
                distanceMeters = 20f,
                estimatedDurationSeconds = 25,
                isCrossFloorStep = true,
                iconType = if (bestTransfer.kind == "elevator") StepIconType.ELEVATOR else StepIconType.STAIRS
            ),
            RouteStep(
                stepNumber = 3,
                floorLevel = floorB.level,
                targetFloorLevel = floorB.level,
                instruction = "${floorB.name}: " + HospitalLocalization.getStepDestinationInstruction(destDisplayName, floorB.level, language),
                startRoomId = bestTransfer.roomB.id,
                destRoomId = destRoom.id,
                distanceMeters = bestSeg2.length * 0.1f,
                estimatedDurationSeconds = 25,
                isCrossFloorStep = false,
                iconType = StepIconType.DESTINATION
            )
        )

        val startNode = MapNode(startRoom.nodeId, floorA.level, bestSeg1.points.first().x, bestSeg1.points.first().y, startRoom.name, roomId = startRoom.id)
        val destNode = MapNode(destRoom.nodeId, floorB.level, bestSeg2.points.last().x, bestSeg2.points.last().y, destRoom.name, roomId = destRoom.id)

        val seg1 = RouteSegment(floorA.level, bestSeg1.points, bestSeg1.length)
        val seg2 = RouteSegment(floorB.level, bestSeg2.points, bestSeg2.length)

        return HospitalRoute(
            startNode = startNode,
            destNode = destNode,
            isCrossFloor = true,
            totalDistanceMeters = totalMeters,
            estimatedDurationMinutes = durationMins,
            steps = steps,
            pathNodeIds = listOf(startNode.id, destNode.id),
            pathNodes = listOf(startNode, destNode),
            segments = listOf(seg1, seg2),
            transfer = bestTransfer
        )
    }

    private fun buildFallbackSteps(
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
