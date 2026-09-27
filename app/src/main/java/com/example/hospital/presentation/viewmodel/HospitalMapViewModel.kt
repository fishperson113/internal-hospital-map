package com.example.hospital.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HospitalMapViewModel(
    private val getHospitalMapUseCase: GetHospitalMapUseCase,
    private val searchRoomsUseCase: SearchRoomsUseCase,
    private val findHospitalRouteUseCase: FindHospitalRouteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HospitalMapUiState(isLoading = true))
    val uiState: StateFlow<HospitalMapUiState> = _uiState.asStateFlow()

    init {
        loadHospitalData()
    }

    private fun loadHospitalData() {
        viewModelScope.launch {
            getHospitalMapUseCase().collect { data ->
                val roomA = data.graph.getNode("room_101") // Phòng A Tầng 1
                val roomB = data.graph.getNode("room_502") // Phòng B Tầng 5

                _uiState.update { current ->
                    current.copy(
                        hospital = data.hospital,
                        floors = data.floors,
                        graph = data.graph,
                        currentFloorLevel = 5,
                        startNode = roomA,
                        destNode = roomB,
                        isLoading = false
                    )
                }

                // Tự động tính toán lộ trình mẫu A -> B như trong demo hình ảnh
                if (roomA != null && roomB != null) {
                    calculateRoute(roomA.id, roomB.id)
                }
            }
        }
    }

    fun selectFloor(floorLevel: Int) {
        _uiState.update { it.copy(currentFloorLevel = floorLevel) }
    }

    fun setUserStartPin(node: MapNode) {
        _uiState.update { it.copy(startNode = node) }
        val dest = _uiState.value.destNode
        if (dest != null) {
            calculateRoute(node.id, dest.id)
        }
    }

    fun selectDestination(node: MapNode) {
        _uiState.update { it.copy(destNode = node) }
        val start = _uiState.value.startNode
        if (start != null) {
            calculateRoute(start.id, node.id)
        }
    }

    fun calculateRoute(startId: String? = null, destId: String? = null) {
        val sId = startId ?: _uiState.value.startNode?.id ?: return
        val dId = destId ?: _uiState.value.destNode?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val route = findHospitalRouteUseCase(startNodeId = sId, destNodeId = dId, preferElevator = true)
            _uiState.update {
                it.copy(
                    activeRoute = route,
                    isNavigating = route != null,
                    isLoading = false,
                    errorMessage = if (route == null) "Không tìm thấy đường đi giữa hai vị trí" else null
                )
            }
        }
    }

    fun clearRoute() {
        _uiState.update {
            it.copy(
                activeRoute = null,
                isNavigating = false
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            searchRoomsUseCase(query).collect { results ->
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    fun selectNodeOnMap(node: MapNode) {
        _uiState.update { it.copy(selectedNode = node) }
    }

    fun clearSelectedNode() {
        _uiState.update { it.copy(selectedNode = null) }
    }

    fun toggleMultiFloorView() {
        _uiState.update { it.copy(isMultiFloorView = !it.isMultiFloorView) }
    }
}
