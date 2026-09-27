package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.EdgeStatus
import com.example.hospital.domain.model.MapEdge
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.NodeStatus
import com.example.hospital.domain.model.NodeType

data class HospitalGraph(
    val nodes: Map<String, MapNode>,
    val edges: List<MapEdge>
) {
    // Adjacency map for fast lookup
    private val adjacencyMap: Map<String, List<MapEdge>> = buildAdjacencyMap()

    private fun buildAdjacencyMap(): Map<String, List<MapEdge>> {
        val map = mutableMapOf<String, MutableList<MapEdge>>()
        for (edge in edges) {
            if (edge.status == EdgeStatus.BLOCKED) continue
            map.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge)
            if (edge.isBidirectional) {
                val reverseEdge = edge.copy(
                    id = "${edge.id}_rev",
                    fromNodeId = edge.toNodeId,
                    toNodeId = edge.fromNodeId
                )
                map.getOrPut(edge.toNodeId) { mutableListOf() }.add(reverseEdge)
            }
        }
        return map
    }

    fun getNode(id: String): MapNode? = nodes[id]

    fun getOutgoingEdges(nodeId: String): List<MapEdge> {
        return adjacencyMap[nodeId] ?: emptyList()
    }

    fun getNodesOnFloor(floorLevel: Int): List<MapNode> {
        return nodes.values.filter { it.floorLevel == floorLevel && it.status == NodeStatus.ACTIVE }
    }

    fun getConnectors(floorLevel: Int): List<MapNode> {
        return nodes.values.filter { it.floorLevel == floorLevel && it.type == NodeType.CONNECTOR }
    }

    fun getAllRooms(): List<MapNode> {
        return nodes.values.filter { it.type == NodeType.ROOM }
    }
}
