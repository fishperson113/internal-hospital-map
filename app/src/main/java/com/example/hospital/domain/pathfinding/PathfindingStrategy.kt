package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.Graph
import com.example.hospital.domain.model.MapNode

interface PathfindingStrategy {
    fun findPath(graph: Graph, startNodeId: String, destNodeId: String): List<MapNode>?
}
