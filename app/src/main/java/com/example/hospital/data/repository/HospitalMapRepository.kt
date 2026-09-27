package com.example.hospital.data.repository

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.NodeStatus
import com.example.hospital.domain.pathfinding.HospitalGraph
import kotlinx.coroutines.flow.Flow

interface HospitalMapRepository {
    fun getHospitalStream(): Flow<Hospital>
    fun getFloorsStream(): Flow<List<Floor>>
    fun getHospitalGraphStream(): Flow<HospitalGraph>
    fun getRoomsOnFloorStream(floorLevel: Int): Flow<List<MapNode>>
    fun searchRoomsStream(query: String): Flow<List<MapNode>>
    suspend fun reportNodeIssue(nodeId: String, notes: String)
    suspend fun updateNodeStatus(nodeId: String, status: NodeStatus)
}
