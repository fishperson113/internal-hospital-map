package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.Room

class SearchRoomsUseCase(
    private val repository: HospitalMapRepository
) {
    suspend operator fun invoke(query: String): List<Room> {
        return repository.searchRooms(query)
    }
}
