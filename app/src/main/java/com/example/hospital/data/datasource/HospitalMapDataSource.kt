package com.example.hospital.data.datasource

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.NodeStatus
import com.example.hospital.domain.pathfinding.HospitalGraph

interface HospitalMapDataSource {
    suspend fun getHospital(): Hospital
    suspend fun getFloors(hospitalId: String): List<Floor>
    suspend fun getHospitalGraph(hospitalId: String): HospitalGraph
    suspend fun updateNodeStatus(nodeId: String, status: NodeStatus)
    suspend fun reportNodeIssue(nodeId: String, notes: String)
}
