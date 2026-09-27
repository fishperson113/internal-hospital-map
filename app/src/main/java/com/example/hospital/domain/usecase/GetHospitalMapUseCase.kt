package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.pathfinding.HospitalGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HospitalMapData(
    val hospital: Hospital,
    val floors: List<Floor>,
    val graph: HospitalGraph
)

class GetHospitalMapUseCase(
    private val repository: HospitalMapRepository
) {
    operator fun invoke(): Flow<HospitalMapData> {
        return combine(
            repository.getHospitalStream(),
            repository.getFloorsStream(),
            repository.getHospitalGraphStream()
        ) { hospital, floors, graph ->
            HospitalMapData(hospital, floors, graph)
        }
    }
}
