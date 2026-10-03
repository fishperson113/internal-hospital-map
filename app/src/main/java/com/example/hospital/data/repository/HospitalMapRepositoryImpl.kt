package com.example.hospital.data.repository

import com.example.hospital.data.api.HospitalApiService
import com.example.hospital.data.datasource.HospitalMapDataSource
import com.example.hospital.domain.model.Graph
import com.example.hospital.domain.model.HospitalMapData
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RouteFetchResult
import com.example.hospital.presentation.util.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class HospitalMapRepositoryImpl(
    private val dataSource: HospitalMapDataSource,
    private val apiService: HospitalApiService = HospitalApiService()
) : HospitalMapRepository {

    private var cachedMapData: HospitalMapData? = null

    override fun getHospitalMap(): Flow<HospitalMapData> = flow {
        cachedMapData?.let { emit(it) }

        // Fetch from backend API
        val remoteResult = apiService.fetchFloors()
        if (remoteResult.isSuccess) {
            val remoteData = remoteResult.getOrThrow()
            cachedMapData = remoteData
            emit(remoteData)
        } else {
            // Fallback to local / offline datasource
            val localData = dataSource.getHospitalMapData().first()
            if (cachedMapData == null) {
                cachedMapData = localData
                emit(localData)
            }
        }
    }

    override suspend fun getAllRooms(): List<Room> {
        val map = cachedMapData ?: getHospitalMap().first()
        return map.floors.flatMap { it.rooms }
    }

    override suspend fun getRoomById(roomId: String): Room? {
        val mapData = cachedMapData ?: getHospitalMap().first()
        for (floor in mapData.floors) {
            val room = floor.rooms.find {
                it.id == roomId ||
                it.nodeId == roomId ||
                "${it.floorId}:${it.rawId}" == roomId ||
                "${it.floorId}_${it.rawId}" == roomId
            }
            if (room != null) return room
        }
        return null
    }

    override suspend fun searchRooms(query: String): List<Room> {
        if (query.isBlank()) return emptyList()
        val mapData = cachedMapData ?: getHospitalMap().first()
        val lowerQuery = query.trim().lowercase()
        val allRooms = mapData.floors.flatMap { it.rooms }
        return allRooms.filter { room ->
            room.name.lowercase().contains(lowerQuery) ||
            room.nameEn.lowercase().contains(lowerQuery) ||
            room.code.lowercase().contains(lowerQuery) ||
            room.category.displayName.lowercase().contains(lowerQuery)
        }
    }

    override suspend fun getGraph(): Graph {
        val map = cachedMapData ?: getHospitalMap().first()
        return map.graph
    }

    override suspend fun fetchRoute(
        from: String,
        to: String,
        pref: String,
        destName: String?,
        language: AppLanguage
    ): RouteFetchResult {
        val map = cachedMapData ?: getHospitalMap().first()
        return apiService.fetchRoute(
            fromParam = from,
            toParam = to,
            pref = pref,
            floors = map.floors,
            destNameOverride = destName,
            language = language
        )
    }
}

