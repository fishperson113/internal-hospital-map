package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.NavigationRoute
import com.example.hospital.domain.pathfinding.PathfindingStrategy
import kotlinx.coroutines.flow.first

class FindHospitalRouteUseCase(
    private val repository: HospitalMapRepository,
    private val pathfindingStrategy: PathfindingStrategy
) {
    suspend operator fun invoke(
        startNodeId: String,
        destNodeId: String,
        preferElevator: Boolean = true
    ): NavigationRoute? {
        val graph = repository.getHospitalGraphStream().first()
        return pathfindingStrategy.findPath(graph, startNodeId, destNodeId, preferElevator)
    }
}
