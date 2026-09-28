package com.example.hospital.data.repository

import com.example.hospital.data.datasource.HospitalMapDataSource
import com.example.hospital.domain.model.Graph
import com.example.hospital.domain.model.HospitalMapData
import com.example.hospital.domain.model.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class HospitalMapRepositoryImpl(
    private val dataSource: HospitalMapDataSource
) : HospitalMapRepository {

    override fun getHospitalMap(): Flow<HospitalMapData> {
        return dataSource.getHospitalMapData()
    }

    override suspend fun getRoomById(roomId: String): Room? {
        val mapData = dataSource.getHospitalMapData().first()
        for (floor in mapData.floors) {
            val room = floor.rooms.find { it.id == roomId || it.nodeId == roomId }
            if (room != null) return room
        }
        return null
    }

    override suspend fun searchRooms(query: String): List<Room> {
        if (query.isBlank()) return emptyList()
        val mapData = dataSource.getHospitalMapData().first()
        val lowerQuery = query.trim().lowercase()
        val allRooms = mapData.floors.flatMap { it.rooms }
        return allRooms.filter { room ->
            room.name.lowercase().contains(lowerQuery) ||
                    room.code.lowercase().contains(lowerQuery) ||
                    room.description.lowercase().contains(lowerQuery) ||
                    room.category.displayName.lowercase().contains(lowerQuery)
        }
    }

    override suspend fun getGraph(): Graph {
        return dataSource.getHospitalMapData().first().graph
    }
}
