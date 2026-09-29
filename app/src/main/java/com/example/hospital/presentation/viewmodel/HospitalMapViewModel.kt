package com.example.hospital.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase
import com.example.hospital.presentation.state.HospitalMapUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HospitalMapViewModel(
    private val getHospitalMapUseCase: GetHospitalMapUseCase,
    private val searchRoomsUseCase: SearchRoomsUseCase,
    private val findHospitalRouteUseCase: FindHospitalRouteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HospitalMapUiState())
    val uiState: StateFlow<HospitalMapUiState> = _uiState.asStateFlow()

    init {
        loadMapData()
    }

    private fun loadMapData() {
        viewModelScope.launch {
            getHospitalMapUseCase().collect { mapData ->
                val allRooms = mapData.floors.flatMap { it.rooms }
                val start = allRooms.find { it.id == "tang-2_48" }
                    ?: allRooms.find { it.floorLevel == 2 && it.name.contains("ĐĂNG KÝ") }
                    ?: allRooms.find { it.floorLevel == 2 }
                    ?: allRooms.firstOrNull()
                val dest = allRooms.find { it.id == "tang-2_59" }
                    ?: allRooms.find { it.floorLevel == 2 && it.name.contains("NỘI") }
                    ?: allRooms.find { it.floorLevel == 2 && it.name.contains("SIÊU ÂM") }
                    ?: allRooms.lastOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hospital = mapData.hospital,
                        floors = mapData.floors,
                        selectedFloorLevel = 2,
                        startRoom = start,
                        destRoom = dest
                    )
                }

                if (start != null && dest != null) {
                    calculateRoute(start.id, dest.id)
                }
            }
        }
    }

    fun onSelectFloor(level: Int?) {
        _uiState.update { it.copy(selectedFloorLevel = level) }
    }

    fun onSelectStartRoom(room: Room) {
        _uiState.update {
            it.copy(
                startRoom = room,
                isSearchActive = false
            )
        }
        val dest = _uiState.value.destRoom
        if (dest != null) {
            calculateRoute(room.id, dest.id)
        }
    }

    fun onSelectDestRoom(room: Room) {
        _uiState.update {
            it.copy(
                destRoom = room,
                selectedFloorLevel = room.floorLevel,
                isSearchActive = false
            )
        }
        val start = _uiState.value.startRoom
        if (start != null) {
            calculateRoute(start.id, room.id)
        }
    }

    fun onSwapRooms() {
        val currentStart = _uiState.value.startRoom
        val currentDest = _uiState.value.destRoom
        if (currentStart != null && currentDest != null) {
            _uiState.update {
                it.copy(
                    startRoom = currentDest,
                    destRoom = currentStart,
                    selectedFloorLevel = currentStart.floorLevel
                )
            }
            calculateRoute(currentDest.id, currentStart.id)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                val allRooms = _uiState.value.floors.flatMap { it.rooms }
                _uiState.update { it.copy(searchResults = allRooms) }
            } else {
                val results = searchRoomsUseCase(query)
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    fun onOpenSearch(isStartRoom: Boolean) {
        val allRooms = _uiState.value.floors.flatMap { it.rooms }
        _uiState.update {
            it.copy(
                isSearchActive = true,
                isSelectingStartRoom = isStartRoom,
                searchQuery = "",
                searchResults = allRooms
            )
        }
    }

    fun onCloseSearch() {
        _uiState.update { it.copy(isSearchActive = false) }
    }

    fun onToggleRouteDetails() {
        _uiState.update { it.copy(isRouteDetailsExpanded = !it.isRouteDetailsExpanded) }
    }

    fun onStartNavigation() {
        _uiState.update { it.copy(isNavigating = true) }
    }

    fun onStopNavigation() {
        _uiState.update { it.copy(isNavigating = false) }
    }

    fun onZoomIn() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel * 1.25f).coerceAtMost(3.0f)) }
    }

    fun onZoomOut() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel / 1.25f).coerceAtLeast(0.6f)) }
    }

    fun onResetView() {
        _uiState.update { it.copy(zoomLevel = 1.0f, panOffsetX = 0f, panOffsetY = 0f) }
    }

    fun onToggleLanguage() {
        _uiState.update {
            val newLang = if (it.language == com.example.hospital.presentation.util.AppLanguage.VIETNAMESE) {
                com.example.hospital.presentation.util.AppLanguage.ENGLISH
            } else {
                com.example.hospital.presentation.util.AppLanguage.VIETNAMESE
            }
            it.copy(language = newLang)
        }
        val currentStart = _uiState.value.startRoom
        val currentDest = _uiState.value.destRoom
        if (currentStart != null && currentDest != null) {
            calculateRoute(currentStart.id, currentDest.id)
        }
    }

    fun onPan(dx: Float, dy: Float) {
        _uiState.update {
            it.copy(
                panOffsetX = it.panOffsetX + dx,
                panOffsetY = it.panOffsetY + dy
            )
        }
    }

    private fun calculateRoute(startRoomId: String, destRoomId: String) {
        viewModelScope.launch {
            val route = findHospitalRouteUseCase(startRoomId, destRoomId, _uiState.value.language)
            _uiState.update { it.copy(currentRoute = route) }
        }
    }
}
