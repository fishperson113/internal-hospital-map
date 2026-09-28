package com.example.hospital.data.datasource

import com.example.hospital.domain.model.HospitalMapData
import kotlinx.coroutines.flow.Flow

interface HospitalMapDataSource {
    fun getHospitalMapData(): Flow<HospitalMapData>
}
