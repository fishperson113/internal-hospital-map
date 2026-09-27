package com.example.hospital.data.repository

import com.example.hospital.data.datasource.HospitalMapDataSource
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.NodeStatus
import com.example.hospital.domain.model.NodeType
import com.example.hospital.domain.pathfinding.HospitalGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class HospitalMapRepositoryImpl(
    private val dataSource: HospitalMapDataSource
) : HospitalMapRepository {

    override fun getHospitalStream(): Flow<Hospital> = flow {
        emit(dataSource.getHospital())
    }

    override fun getFloorsStream(): Flow<List<Floor>> = flow {
        val hospital = dataSource.getHospital()
        emit(dataSource.getFloors(hospital.id))
    }

    override fun getHospitalGraphStream(): Flow<HospitalGraph> = flow {
        val hospital = dataSource.getHospital()
        emit(dataSource.getHospitalGraph(hospital.id))
    }

    override fun getRoomsOnFloorStream(floorLevel: Int): Flow<List<MapNode>> = flow {
        val hospital = dataSource.getHospital()
        val graph = dataSource.getHospitalGraph(hospital.id)
        val rooms = graph.getNodesOnFloor(floorLevel).filter { it.type == NodeType.ROOM }
        emit(rooms)
    }

    override fun searchRoomsStream(query: String): Flow<List<MapNode>> = flow {
        val hospital = dataSource.getHospital()
        val graph = dataSource.getHospitalGraph(hospital.id)
        val allRooms = graph.getAllRooms()
        if (query.isBlank()) {
            emit(allRooms)
        } else {
            val filtered = allRooms.filter { room ->
                room.name.contains(query, ignoreCase = true) ||
                        room.roomCode.contains(query, ignoreCase = true) ||
                        room.department.contains(query, ignoreCase = true)
            }
            emit(filtered)
        }
    }

    override suspend fun reportNodeIssue(nodeId: String, notes: String) {
        dataSource.reportNodeIssue(nodeId, notes)
    }

    override suspend fun updateNodeStatus(nodeId: String, status: NodeStatus) {
        dataSource.updateNodeStatus(nodeId, status)
    }
}
