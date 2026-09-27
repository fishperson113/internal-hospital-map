package com.example.hospital.presentation.viewmodel

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.NavigationRoute
import com.example.hospital.domain.pathfinding.HospitalGraph

data class HospitalMapUiState(
    val hospital: Hospital? = null,
    val floors: List<Floor> = emptyList(),
    val currentFloorLevel: Int = 5,           // Tầng đang xem (mặc định Tầng 5 như demo)
    val graph: HospitalGraph? = null,
    val startNode: MapNode? = null,           // Điểm user tự ghim (User-pinned location)
    val destNode: MapNode? = null,            // Phòng/khoa đích cần tìm
    val activeRoute: NavigationRoute? = null, // Lộ trình dẫn đường
    val isNavigating: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MapNode> = emptyList(),
    val selectedNode: MapNode? = null,        // Node được bấm chọn trên sơ đồ tầng
    val isMultiFloorView: Boolean = false,    // Chế độ hiển thị xếp tầng 3D Isometric
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val roomsOnCurrentFloor: List<MapNode>
        get() = graph?.getNodesOnFloor(currentFloorLevel) ?: emptyList()
}
