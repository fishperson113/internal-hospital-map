package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.NavigationRoute

/**
 * Strategy pattern interface for pathfinding algorithm.
 * Allows seamless switching between Dijkstra, A*, NavMesh, or AI Route recommendation
 * without altering the presentation or domain layers.
 */
interface PathfindingStrategy {
    fun findPath(
        graph: HospitalGraph,
        startNodeId: String,
        destNodeId: String,
        preferElevator: Boolean = true
    ): NavigationRoute?
}
