package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.Graph
import com.example.hospital.domain.model.MapNode
import java.util.PriorityQueue

class DijkstraPathfindingStrategy : PathfindingStrategy {

    private data class NodeDistance(val nodeId: String, val distance: Float) : Comparable<NodeDistance> {
        override fun compareTo(other: NodeDistance): Int = this.distance.compareTo(other.distance)
    }

    override fun findPath(graph: Graph, startNodeId: String, destNodeId: String): List<MapNode>? {
        if (!graph.nodes.containsKey(startNodeId) || !graph.nodes.containsKey(destNodeId)) {
            return null
        }
        if (startNodeId == destNodeId) {
            return listOfNotNull(graph.nodes[startNodeId])
        }

        // Build adjacency map (bidirectional if needed)
        val adjacency = mutableMapOf<String, MutableList<Pair<String, Float>>>()
        graph.edges.forEach { edge ->
            adjacency.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge.toNodeId to edge.distanceMeters)
            adjacency.getOrPut(edge.toNodeId) { mutableListOf() }.add(edge.fromNodeId to edge.distanceMeters)
        }

        val distances = mutableMapOf<String, Float>().withDefault { Float.MAX_VALUE }
        val previous = mutableMapOf<String, String>()
        val pq = PriorityQueue<NodeDistance>()

        distances[startNodeId] = 0f
        pq.add(NodeDistance(startNodeId, 0f))

        while (pq.isNotEmpty()) {
            val (currentId, currentDist) = pq.poll() ?: break

            if (currentId == destNodeId) break

            if (currentDist > (distances[currentId] ?: Float.MAX_VALUE)) continue

            val neighbors = adjacency[currentId] ?: emptyList()
            for ((neighborId, weight) in neighbors) {
                val newDist = currentDist + weight
                if (newDist < (distances[neighborId] ?: Float.MAX_VALUE)) {
                    distances[neighborId] = newDist
                    previous[neighborId] = currentId
                    pq.add(NodeDistance(neighborId, newDist))
                }
            }
        }

        if (!previous.containsKey(destNodeId)) {
            return null
        }

        // Reconstruct path
        val path = mutableListOf<MapNode>()
        var curr: String? = destNodeId
        while (curr != null) {
            graph.nodes[curr]?.let { path.add(0, it) }
            curr = previous[curr]
        }
        return path
    }
}
