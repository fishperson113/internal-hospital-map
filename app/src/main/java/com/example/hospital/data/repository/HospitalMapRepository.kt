package com.example.hospital.data.repository

import com.example.hospital.domain.model.Graph
import com.example.hospital.domain.model.HospitalMapData
import com.example.hospital.domain.model.Room
import kotlinx.coroutines.flow.Flow

interface HospitalMapRepository {
    fun getHospitalMap(): Flow<HospitalMapData>
    suspend fun getAllRooms(): List<Room>
    suspend fun getRoomById(roomId: String): Room?
    suspend fun searchRooms(query: String): List<Room>
    suspend fun getGraph(): Graph
}
