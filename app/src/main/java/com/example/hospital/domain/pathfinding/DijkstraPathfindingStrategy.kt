package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.ConnectorType
import com.example.hospital.domain.model.MapEdge
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.NavigationRoute
import com.example.hospital.domain.model.NodeType
import com.example.hospital.domain.model.RouteStep
import com.example.hospital.domain.model.TurnDirection
import java.util.PriorityQueue
import kotlin.math.abs

class DijkstraPathfindingStrategy : PathfindingStrategy {

    private data class NodeDistance(
        val nodeId: String,
        val distance: Float
    ) : Comparable<NodeDistance> {
        override fun compareTo(other: NodeDistance): Int = distance.compareTo(other.distance)
    }

    override fun findPath(
        graph: HospitalGraph,
        startNodeId: String,
        destNodeId: String,
        preferElevator: Boolean
    ): NavigationRoute? {
        val startNode = graph.getNode(startNodeId) ?: return null
        val destNode = graph.getNode(destNodeId) ?: return null

        if (startNodeId == destNodeId) {
            return NavigationRoute(
                startNode = startNode,
                destNode = destNode,
                nodesPath = listOf(startNode),
                steps = listOf(
                    RouteStep(
                        stepIndex = 1,
                        floorLevel = startNode.floorLevel,
                        instruction = "Bạn đang ở ngay tại ${startNode.name}",
                        turnDirection = TurnDirection.ARRIVE,
                        distanceMeters = 0f,
                        durationSeconds = 0,
                        fromNode = startNode,
                        toNode = destNode
                    )
                ),
                totalDistanceMeters = 0f,
                totalDurationSeconds = 0
            )
        }

        // Dijkstra implementation
        val distances = mutableMapOf<String, Float>()
        val previousNodes = mutableMapOf<String, String>()
        val previousEdges = mutableMapOf<String, MapEdge?>()
        val priorityQueue = PriorityQueue<NodeDistance>()

        distances[startNodeId] = 0f
        priorityQueue.add(NodeDistance(startNodeId, 0f))

        while (priorityQueue.isNotEmpty()) {
            val current = priorityQueue.poll() ?: break
            val currentId = current.nodeId
            val currentDist = current.distance

            if (currentId == destNodeId) break
            if (currentDist > (distances[currentId] ?: Float.MAX_VALUE)) continue

            val outgoingEdges = graph.getOutgoingEdges(currentId)
            for (edge in outgoingEdges) {
                val neighborId = edge.toNodeId
                val neighborNode = graph.getNode(neighborId) ?: continue

                // Calculate edge weight penalty if it's connector
                var weight = edge.distanceMeters
                val currentNode = graph.getNode(currentId)
                if (currentNode?.type == NodeType.CONNECTOR && neighborNode.type == NodeType.CONNECTOR) {
                    val connectorInfo = currentNode.connectorInfo
                    val penalty = connectorInfo?.transferPenaltySeconds?.toFloat() ?: 15f
                    // If prefer elevator, stairs have higher weight
                    val elevatorBonus = if (preferElevator && connectorInfo?.type == ConnectorType.STAIRS) 20f else 0f
                    weight += penalty + elevatorBonus
                }

                val newDist = currentDist + weight
                if (newDist < (distances[neighborId] ?: Float.MAX_VALUE)) {
                    distances[neighborId] = newDist
                    previousNodes[neighborId] = currentId
                    previousEdges[neighborId] = edge
                    priorityQueue.add(NodeDistance(neighborId, newDist))
                }
            }
        }

        // Reconstruct path
        if (!previousNodes.containsKey(destNodeId) && startNodeId != destNodeId) {
            return null // No path found
        }

        val pathNodeIds = mutableListOf<String>()
        var curr: String? = destNodeId
        while (curr != null) {
            pathNodeIds.add(0, curr)
            curr = previousNodes[curr]
        }

        val nodesList = pathNodeIds.mapNotNull { graph.getNode(it) }
        val steps = buildRouteSteps(nodesList)
        val totalDistance = steps.sumOf { it.distanceMeters.toDouble() }.toFloat()
        val totalDuration = steps.sumOf { it.durationSeconds }

        return NavigationRoute(
            startNode = startNode,
            destNode = destNode,
            nodesPath = nodesList,
            steps = steps,
            totalDistanceMeters = totalDistance,
            totalDurationSeconds = totalDuration
        )
    }

    private fun buildRouteSteps(nodes: List<MapNode>): List<RouteStep> {
        if (nodes.size < 2) return emptyList()

        val steps = mutableListOf<RouteStep>()
        var stepIndex = 1

        var i = 0
        while (i < nodes.size - 1) {
            val fromNode = nodes[i]
            val toNode = nodes[i + 1]

            // Check if cross-floor step (e.g. elevator on floor 1 to floor 5)
            if (fromNode.floorLevel != toNode.floorLevel &&
                fromNode.type == NodeType.CONNECTOR && toNode.type == NodeType.CONNECTOR
            ) {
                // Find all consecutive connector nodes in this vertical shaft
                var j = i + 1
                while (j < nodes.size && nodes[j].type == NodeType.CONNECTOR &&
                       nodes[j].connectorInfo?.type == fromNode.connectorInfo?.type) {
                    j++
                }
                val lastConnectorNode = nodes[j - 1]
                val connectorName = if (fromNode.connectorInfo?.type == ConnectorType.ELEVATOR) "thang máy" else "thang bộ"
                val floorDiff = abs(lastConnectorNode.floorLevel - fromNode.floorLevel)
                val timeSec = floorDiff * (if (fromNode.connectorInfo?.type == ConnectorType.ELEVATOR) 6 else 15)

                steps.add(
                    RouteStep(
                        stepIndex = stepIndex++,
                        floorLevel = fromNode.floorLevel,
                        targetFloorLevel = lastConnectorNode.floorLevel,
                        instruction = "Đi $connectorName lên Tầng ${lastConnectorNode.floorLevel}",
                        turnDirection = if (fromNode.connectorInfo?.type == ConnectorType.ELEVATOR) TurnDirection.TAKE_ELEVATOR else TurnDirection.TAKE_STAIRS,
                        distanceMeters = floorDiff * 3.5f,
                        durationSeconds = timeSec,
                        fromNode = fromNode,
                        toNode = lastConnectorNode,
                        connectorType = fromNode.connectorInfo?.type
                    )
                )
                i = j - 1
                continue
            }

            // Normal intra-floor step
            val dx = toNode.x - fromNode.x
            val dy = toNode.y - fromNode.y
            val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat() * 0.15f
            val time = maxOf(4, (dist / 1.1f).toInt())

            val instruction = when {
                i == 0 -> "Đi từ ${fromNode.name} hướng về ${toNode.name}"
                toNode.type == NodeType.CONNECTOR -> "Đi thẳng tới ${toNode.name}"
                toNode.type == NodeType.ROOM && i == nodes.size - 2 -> "Đến điểm đích: ${toNode.name}"
                else -> "Đi theo hành lang qua ${toNode.name}"
            }

            val direction = when {
                i == 0 -> TurnDirection.START
                toNode.type == NodeType.ROOM && i == nodes.size - 2 -> TurnDirection.ARRIVE
                else -> TurnDirection.STRAIGHT
            }

            steps.add(
                RouteStep(
                    stepIndex = stepIndex++,
                    floorLevel = fromNode.floorLevel,
                    targetFloorLevel = null,
                    instruction = instruction,
                    turnDirection = direction,
                    distanceMeters = dist,
                    durationSeconds = time,
                    fromNode = fromNode,
                    toNode = toNode
                )
            )
            i++
        }

        return steps
    }
}
