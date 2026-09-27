package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.MapNode
import kotlinx.coroutines.flow.Flow

class SearchRoomsUseCase(
    private val repository: HospitalMapRepository
) {
    operator fun invoke(query: String): Flow<List<MapNode>> {
        return repository.searchRoomsStream(query)
    }
}
