package com.example.hospital.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase
import com.example.hospital.presentation.state.HospitalMapUiState
import com.example.hospital.presentation.state.TransferPreference
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.RouteStepFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Screen state for the indoor map. Camera (zoom / pan / 3D orbit / 3D↔2D animation) is
 * pure UI state and lives in HospitalCanvasMap — the ViewModel only decides WHICH view
 * is shown (selectedFloorLevel: null = 3D stack, N = 2D floor N).
 */
class HospitalMapViewModel(
    private val getHospitalMapUseCase: GetHospitalMapUseCase,
    private val searchRoomsUseCase: SearchRoomsUseCase,
    private val findHospitalRouteUseCase: FindHospitalRouteUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HospitalMapUiState())
    val uiState: StateFlow<HospitalMapUiState> = _uiState.asStateFlow()

    private var roomsById: Map<String, Room> = emptyMap()
    private var routeJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadMapData()
    }

    private fun loadMapData() {
        viewModelScope.launch {
            getHospitalMapUseCase().collect { mapData ->
                val allRooms = mapData.floors.flatMap { it.rooms }
                roomsById = allRooms.associateBy { it.id }
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
                        selectedFloorLevel = null, // open on the 3D overview of all floors
                        startRoom = start,
                        destRoom = dest
                    )
                }
                calculateRoute(focusMapOnRoute = false)
            }
        }
    }

    // ---------------------------------------------------------------- floors

    /** null = "Tất cả" (3D stack). */
    fun onSelectFloor(level: Int?) {
        _uiState.update { it.copy(selectedFloorLevel = level) }
    }

    // ---------------------------------------------------------------- rooms on the map

    fun onRoomTapped(room: Room) {
        if (_uiState.value.isNavigating) return
        _uiState.update { it.copy(selectedRoom = room, isRouteDetailsExpanded = false) }
    }

    fun onDismissRoomDetails() {
        _uiState.update { it.copy(selectedRoom = null) }
    }

    /** Background tap: close the room sheet / collapse the directions list. */
    fun onMapEmptyTap() {
        _uiState.update { it.copy(selectedRoom = null, isRouteDetailsExpanded = false) }
    }

    fun onRouteToSelectedRoom() {
        val room = _uiState.value.selectedRoom ?: return
        val start = _uiState.value.startRoom
        _uiState.update { it.copy(destRoom = room, selectedRoom = null) }
        if (start == null || start.id == room.id) onOpenSearch(isStartRoom = true)
        else calculateRoute(focusMapOnRoute = true)
    }

    fun onRouteFromSelectedRoom() {
        val room = _uiState.value.selectedRoom ?: return
        val dest = _uiState.value.destRoom
        _uiState.update { it.copy(startRoom = room, selectedRoom = null) }
        if (dest == null || dest.id == room.id) onOpenSearch(isStartRoom = false)
        else calculateRoute(focusMapOnRoute = true)
    }

    // ---------------------------------------------------------------- route selection

    fun onSelectStartRoom(room: Room) {
        _uiState.update { it.copy(startRoom = room, isSearchActive = false, selectedRoom = null, isNavigating = false) }
        calculateRoute(focusMapOnRoute = true)
    }

    fun onSelectDestRoom(room: Room) {
        _uiState.update { it.copy(destRoom = room, isSearchActive = false, selectedRoom = null, isNavigating = false) }
        calculateRoute(focusMapOnRoute = true)
    }

    fun onSwapRooms() {
        val s = _uiState.value
        if (s.startRoom == null || s.destRoom == null) return
        _uiState.update { it.copy(startRoom = s.destRoom, destRoom = s.startRoom) }
        calculateRoute(focusMapOnRoute = true)
    }

    /** Close the route card. The start point is kept so "Đến đây" on a room works immediately. */
    fun onClearRoute() {
        routeJob?.cancel()
        _uiState.update {
            it.copy(
                destRoom = null, currentRoute = null, routeSteps = emptyList(), routeNotFound = false,
                isRouteCalculating = false, isNavigating = false, isRouteDetailsExpanded = false
            )
        }
    }

    fun onSetTransferPreference(preference: TransferPreference) {
        if (_uiState.value.transferPreference == preference) return
        _uiState.update { it.copy(transferPreference = preference) }
        calculateRoute(focusMapOnRoute = true)
    }

    fun onToggleRouteDetails() {
        _uiState.update { it.copy(isRouteDetailsExpanded = !it.isRouteDetailsExpanded) }
    }

    /** Tapping a step in the list moves the map to that step's floor (3D for floor changes). */
    fun onFocusStep(index: Int) {
        val step = _uiState.value.routeSteps.getOrNull(index) ?: return
        _uiState.update { it.copy(selectedFloorLevel = step.focusFloorLevel) }
    }

    // ---------------------------------------------------------------- turn-by-turn

    fun onStartNavigation() {
        val steps = _uiState.value.routeSteps
        if (steps.isEmpty()) return
        _uiState.update {
            it.copy(
                isNavigating = true, navStepIndex = 0, selectedRoom = null, isRouteDetailsExpanded = false,
                selectedFloorLevel = steps[0].focusFloorLevel
            )
        }
    }

    fun onNextStep() {
        val s = _uiState.value
        if (s.navStepIndex >= s.routeSteps.lastIndex) {
            onStopNavigation()
            return
        }
        val next = s.navStepIndex + 1
        _uiState.update { it.copy(navStepIndex = next, selectedFloorLevel = s.routeSteps[next].focusFloorLevel) }
    }

    fun onPreviousStep() {
        val s = _uiState.value
        if (s.navStepIndex <= 0) return
        val prev = s.navStepIndex - 1
        _uiState.update { it.copy(navStepIndex = prev, selectedFloorLevel = s.routeSteps[prev].focusFloorLevel) }
    }

    fun onStopNavigation() {
        _uiState.update { it.copy(isNavigating = false, navStepIndex = 0) }
    }

    /** System back: close the top-most layer first. Returns true if something was closed. */
    fun onBack(): Boolean {
        val s = _uiState.value
        when {
            s.isSearchActive -> onCloseSearch()
            s.isNavigating -> onStopNavigation()
            s.selectedRoom != null -> onDismissRoomDetails()
            s.isRouteDetailsExpanded -> onToggleRouteDetails()
            s.selectedFloorLevel != null -> onSelectFloor(null)
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- search

    fun onOpenSearch(isStartRoom: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = true,
                isSelectingStartRoom = isStartRoom,
                searchQuery = "",
                searchResults = searchLocal("")
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val local = searchLocal(query)
        _uiState.update { it.copy(searchQuery = query, searchResults = local) }
        searchJob?.cancel()
        if (local.isEmpty() && query.isNotBlank()) {
            // fall back to the repository search (e.g. room codes)
            searchJob = viewModelScope.launch {
                val results = searchRoomsUseCase(query)
                _uiState.update { if (it.searchQuery == query) it.copy(searchResults = results) else it }
            }
        }
    }

    fun onSearchResultPicked(room: Room) {
        if (_uiState.value.isSelectingStartRoom) onSelectStartRoom(room) else onSelectDestRoom(room)
    }

    fun onCloseSearch() {
        _uiState.update { it.copy(isSearchActive = false) }
    }

    /** Accent-insensitive search over VI + EN names ("phong kham" matches "PHÒNG KHÁM"). */
    private fun searchLocal(query: String): List<Room> {
        val all = _uiState.value.floors.flatMap { it.rooms }
            .filter { it.name.isNotBlank() && !it.name.all(Char::isDigit) }
        val q = normalize(query).trim()
        if (q.isEmpty()) return all.filter { it.kind == null }.sortedBy { it.floorLevel }
        return all.filter { normalize(it.name).contains(q) || normalize(it.nameEn).contains(q) }
            .sortedWith(compareByDescending<Room> { normalize(it.name).startsWith(q) }.thenBy { it.floorLevel })
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd').replace('Đ', 'D')
            .lowercase()

    // ---------------------------------------------------------------- language

    fun onToggleLanguage() {
        _uiState.update {
            it.copy(language = if (it.language == AppLanguage.VIETNAMESE) AppLanguage.ENGLISH else AppLanguage.VIETNAMESE)
        }
        calculateRoute(focusMapOnRoute = false) // step texts are localized
    }

    // ---------------------------------------------------------------- routing

    private fun calculateRoute(focusMapOnRoute: Boolean) {
        val s = _uiState.value
        val start = s.startRoom
        val dest = s.destRoom
        routeJob?.cancel()
        if (start == null || dest == null) {
            _uiState.update { it.copy(currentRoute = null, routeSteps = emptyList(), routeNotFound = false, isRouteCalculating = false) }
            return
        }
        _uiState.update { it.copy(isRouteCalculating = true, routeNotFound = false) }
        routeJob = viewModelScope.launch {
            val lang = _uiState.value.language
            val kind = _uiState.value.transferPreference.roomKind
            val route = withContext(Dispatchers.Default) {
                findHospitalRouteUseCase(start.id, dest.id, lang, kind)
            }
            val steps = route?.let { RouteStepFormatter.build(it, roomsById, lang) } ?: emptyList()
            _uiState.update {
                it.copy(
                    currentRoute = route,
                    routeSteps = steps,
                    isRouteCalculating = false,
                    routeNotFound = route == null,
                    navStepIndex = 0,
                    selectedFloorLevel = if (focusMapOnRoute && route != null) focusLevelFor(route) else it.selectedFloorLevel
                )
            }
        }
    }

    /** Cross-floor routes are shown on the 3D stack; same-floor routes on that floor in 2D. */
    private fun focusLevelFor(route: HospitalRoute): Int? =
        if (route.transfer != null) null
        else route.segments.firstOrNull()?.floorLevel ?: route.startNode.floorLevel
}
