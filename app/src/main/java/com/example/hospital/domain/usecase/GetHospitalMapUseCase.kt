package com.example.hospital.domain.usecase

import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.model.HospitalMapData
import kotlinx.coroutines.flow.Flow

class GetHospitalMapUseCase(
    private val repository: HospitalMapRepository
) {
    operator fun invoke(): Flow<HospitalMapData> {
        return repository.getHospitalMap()
    }
}
