package com.example.hospital.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.util.HospitalGeometry
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Screen state for the indoor map. Camera (zoom / pan / 3D orbit / 3D↔2D animation) is
 * pure UI state and lives in HospitalCanvasMap — the ViewModel only decides WHICH view
 * is shown (selectedFloorLevel: null = 3D stack, N = 2D floor N).
 */
class HospitalMapViewModel(
    private val getHospitalMapUseCase: GetHospitalMapUseCase,
    private val searchRoomsUseCase: SearchRoomsUseCase,
    private val findHospitalRouteUseCase: FindHospitalRouteUseCase,
    private val sensorManager: com.example.hospital.sensor.HospitalSensorManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HospitalMapUiState())
    val uiState: StateFlow<HospitalMapUiState> = _uiState.asStateFlow()

    private var roomsById: Map<String, Room> = emptyMap()
    private var routeJob: Job? = null
    private var searchJob: Job? = null
    private var sensorJob: Job? = null
    private val pdrTracker = com.example.hospital.sensor.IndoorPdrTracker()
    private var lastObservedStepTrigger: Long = 0L
    private var live = false // like the web: the CRM sees the user once they pick a place / start / ask help
    private val liveClient = com.example.hospital.data.api.LiveClient(context = sensorManager?.context) { status ->
        _uiState.update { it.copy(helpStatus = status) }
    }

    init {
        loadMapData()
        startSensorObservation()
        // Same as the web's liveSync(): push the state to the doctor CRM whenever it changes.
        viewModelScope.launch {
            uiState.map(::liveJson).distinctUntilChanged().collect { json ->
                if (live && json != null) liveClient.sendState(json)
            }
        }
    }

    private fun startSensorObservation() {
        sensorManager?.startTracking()
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            sensorManager?.sensorData?.collect { data ->
                val heading = data.headingDegrees
                val isTracking = data.isTracking

                // Format telemetry display (Heading, Steps, Pressure, Sensors)
                val headingCompass = getCompassDirection(heading)
                val tele = buildString {
                    append("${heading.toInt()}° $headingCompass")
                    if (data.totalSteps > 0) append(" · ${data.totalSteps} bước")
                    if (data.pressureHpa != null) append(" · ${data.pressureHpa.toInt()} hPa")
                    if (data.activeSensors.isNotEmpty()) append(" · ${data.activeSensors.size} cảm biến")
                }

                // While directions are on, each step slides the dot along the route
                if (data.stepEventTrigger > lastObservedStepTrigger) {
                    val stepDelta = (data.stepEventTrigger - lastObservedStepTrigger).toInt().coerceIn(1, 10)
                    lastObservedStepTrigger = data.stepEventTrigger
                    if (_uiState.value.isNavigating) {
                        var last: com.example.hospital.sensor.IndoorPdrState? = null
                        repeat(stepDelta) { pdrTracker.onStep()?.let { last = it } }
                        last?.let(::applyPdrUpdate)
                    }
                }

                _uiState.update {
                    it.copy(
                        userHeadingDegrees = heading,
                        stepCount = data.totalSteps,
                        isSensorTrackingActive = isTracking,
                        sensorTelemetryText = tele
                    )
                }
            }
        }
    }

    fun onPermissionsUpdated() {
        sensorManager?.startTracking()
    }

    private fun applyPdrUpdate(pdr: com.example.hospital.sensor.IndoorPdrState) {
        _uiState.update {
            it.copy(userPosition = pdr.position, userFloorLevel = pdr.floorLevel, hasArrivedAtDestination = pdr.hasArrived)
        }
    }

    /** Like the web: when not walking, the user's dot IS the start of the route (one marker). */
    private fun placeAtRouteStart(route: HospitalRoute) {
        val seg = route.segments.firstOrNull() ?: return
        val p = seg.points.firstOrNull() ?: return
        _uiState.update { it.copy(userPosition = p, userFloorLevel = seg.floorLevel, hasArrivedAtDestination = false) }
    }

    /** Patient state for the CRM, in the shape src/backend/live.js accepts (rooms as {floor, id}). */
    private fun liveJson(s: HospitalMapUiState): String? {
        val start = s.startRoom ?: return null
        // A picked spot is sent as its nearest room (rawId), like the web's nearestRoom()
        fun room(r: Room?) = r?.takeIf { it.rawId >= 0 }?.let { JSONObject().put("floor", it.floorId).put("id", it.rawId) }
        val pos = s.userPosition?.let { p ->
            val floorId = s.floors.find { it.level == s.userFloorLevel }?.floorId ?: start.floorId
            JSONObject().put("floor", floorId).put("x", p.x.toDouble()).put("y", p.y.toDouble())
        }
        val step = if (s.isNavigating) s.routeSteps.getOrNull(s.navStepIndex) else null
        return JSONObject()
            .put("type", "state")
            .put("from", room(start))
            .put("to", room(s.destRoom))
            .put("at", room(start)) // ponytail: "near" = start place; nearest room to pos when the CRM needs it
            .put("pos", pos)
            .put("nav", s.isNavigating)
            .put("step", s.navStepIndex)
            .put("steps", s.routeSteps.size)
            .put("title", step?.title ?: "")
            .toString()
    }

    /** From now on the CRM sees this user. Sends the current state right away; later changes go via the collector. */
    private fun goLive() {
        live = true
        liveJson(_uiState.value)?.let(liveClient::sendState)
    }

    fun onAskHelp() {
        goLive()
        _uiState.update { it.copy(helpStatus = "sending") }
        liveClient.askHelp()
    }

    private fun getCompassDirection(deg: Float): String {
        val d = (deg + 22.5f) % 360f
        return when ((d / 45f).toInt()) {
            0 -> "B"
            1 -> "ĐB"
            2 -> "Đ"
            3 -> "ĐN"
            4 -> "N"
            5 -> "TN"
            6 -> "T"
            7 -> "TB"
            else -> "B"
        }
    }

    private fun loadMapData() {
        viewModelScope.launch {
            getHospitalMapUseCase().collect { mapData ->
                val allRooms = mapData.floors.flatMap { it.rooms }
                roomsById = allRooms.associateBy { it.id }
                // Same defaults as the web (DEFAULT_FROM / DEFAULT_TO in app.js)
                val start = allRooms.find { it.floorId == "tang-2" && it.rawId == 48 } // "Quầy đăng ký khám"
                val dest = allRooms.find { it.floorId == "tang-3" && it.rawId == 4 }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hospital = mapData.hospital,
                        floors = mapData.floors,
                        selectedFloorLevel = null, // open on the 3D overview of all floors
                        destRoom = dest
                    )
                }
                start?.let(::setStart)
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
    /**
     * Tap outside any room. In 2D inside the floor plan it picks that spot (web: tap()), so the
     * user can say "I am here" anywhere in a corridor; elsewhere it just closes the sheets.
     */
    fun onMapEmptyTap(point: Point2D?) {
        val s = _uiState.value
        val floor = s.floors.find { it.level == s.selectedFloorLevel }
        val fr = floor?.frame
        val inside = point != null && floor != null && !floor.isTodo &&
            (fr == null || fr.size < 4 || (point.x in fr[0]..fr[2] && point.y in fr[1]..fr[3]))
        val spot = if (inside && !s.isNavigating) spotRoom(floor!!, point!!) else null
        _uiState.update { it.copy(selectedRoom = spot, isRouteDetailsExpanded = false) }
    }

    /**
     * ponytail: a tapped spot travels through the app as a Room with no polygon, so every
     * "start / destination" path keeps working. id = the server's "floor@x,y" route target,
     * rawId = nearest named room (what the CRM shows as "near").
     */
    private fun spotRoom(floor: Floor, p: Point2D): Room {
        val near = HospitalGeometry.nearestRoom(floor.rooms, p.x, p.y)
        val x = p.x.roundToInt()
        val y = p.y.roundToInt()
        return Room(
            id = "${floor.floorId}@$x,$y",
            code = "",
            name = "Gần ${near?.name.orEmpty()}".trim(),
            nameEn = near?.nameEn?.takeIf { it.isNotBlank() }?.let { "Near $it" }.orEmpty(),
            floorLevel = floor.level,
            category = RoomCategory.FACILITY,
            nodeId = "",
            labelAt = Point2D(x.toFloat(), y.toFloat()),
            rawId = near?.rawId ?: -1,
            floorId = floor.floorId
        )
    }

    fun onRouteToSelectedRoom() {
        val room = _uiState.value.selectedRoom ?: return
        goLive()
        val start = _uiState.value.startRoom
        _uiState.update { it.copy(destRoom = room, selectedRoom = null) }
        if (start == null || start.id == room.id) onOpenSearch(isStartRoom = true)
        else calculateRoute(focusMapOnRoute = true)
    }

    fun onRouteFromSelectedRoom() {
        val room = _uiState.value.selectedRoom ?: return
        val dest = _uiState.value.destRoom
        goLive()
        setStart(room)
        _uiState.update { it.copy(selectedRoom = null) }
        if (dest == null || dest.id == room.id) onOpenSearch(isStartRoom = false)
        else calculateRoute(focusMapOnRoute = true)
    }

    // ---------------------------------------------------------------- route selection

    /** The user says "I am here": the dot jumps to the room and steps move it from there. */
    private fun setStart(room: Room) {
        val center = room.labelAt ?: com.example.hospital.domain.model.Point2D(
            room.boundsLeft + room.boundsWidth / 2f,
            room.boundsTop + room.boundsHeight / 2f
        )
        _uiState.update { it.copy(startRoom = room, userPosition = center, userFloorLevel = room.floorLevel) }
    }

    fun onSelectStartRoom(room: Room) {
        goLive()
        val resume = _uiState.value.isNavigating
        setStart(room)
        _uiState.update { it.copy(isSearchActive = false, selectedRoom = null, isNavigating = false) }
        calculateRoute(focusMapOnRoute = true, resumeNavigation = resume)
    }

    fun onSelectDestRoom(room: Room) {
        goLive()
        val resume = _uiState.value.isNavigating
        _uiState.update { it.copy(destRoom = room, isSearchActive = false, selectedRoom = null, isNavigating = false) }
        calculateRoute(focusMapOnRoute = true, resumeNavigation = resume)
    }

    fun onSwapRooms() {
        val s = _uiState.value
        if (s.startRoom == null || s.destRoom == null) return
        setStart(s.destRoom)
        _uiState.update { it.copy(destRoom = s.startRoom) }
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
        val route = _uiState.value.currentRoute
        if (steps.isEmpty()) return
        goLive()
        val initialPdr = route?.let(pdrTracker::initialize)

        _uiState.update {
            it.copy(
                isNavigating = true,
                navStepIndex = 0,
                selectedRoom = null,
                isRouteDetailsExpanded = false,
                selectedFloorLevel = initialPdr?.floorLevel ?: steps[0].focusFloorLevel,
                userPosition = initialPdr?.position ?: it.startRoom?.let { room -> room.labelAt ?: com.example.hospital.domain.model.Point2D(room.boundsLeft + room.boundsWidth / 2f, room.boundsTop + room.boundsHeight / 2f) },
                userFloorLevel = initialPdr?.floorLevel ?: it.startRoom?.floorLevel,
                hasArrivedAtDestination = false
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
        pdrTracker.goToStep(next)?.let(::applyPdrUpdate) // floor change happens only here
    }

    fun onPreviousStep() {
        val s = _uiState.value
        if (s.navStepIndex <= 0) return
        val prev = s.navStepIndex - 1
        _uiState.update { it.copy(navStepIndex = prev, selectedFloorLevel = s.routeSteps[prev].focusFloorLevel) }
        pdrTracker.goToStep(prev)?.let(::applyPdrUpdate)
    }

    fun onSimulateStep() {
        if (!_uiState.value.isNavigating) return
        pdrTracker.onStep()?.let(::applyPdrUpdate)
    }

    fun onToggleAutoRotate() {
        _uiState.update { it.copy(isAutoRotateEnabled = !it.isAutoRotateEnabled) }
    }

    fun onStopNavigation() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                navStepIndex = 0,
                isAutoRotateEnabled = false,
                hasArrivedAtDestination = false
            )
        }
        _uiState.value.currentRoute?.let(::placeAtRouteStart)
    }

    override fun onCleared() {
        super.onCleared()
        sensorJob?.cancel()
        sensorManager?.stopTracking()
        liveClient.disconnect()
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

    /** [resumeNavigation]: a place was changed mid-directions; restart directions on the new route (web: reroute()). */
    private fun calculateRoute(focusMapOnRoute: Boolean, resumeNavigation: Boolean = false) {
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
                findHospitalRouteUseCase(start.id, dest.id, lang, kind, preferRemote = true, destName = dest.name)
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
            if (route != null && !_uiState.value.isNavigating) placeAtRouteStart(route)
            if (route != null && resumeNavigation) onStartNavigation()
        }
    }

    /** Cross-floor routes are shown on the 3D stack; same-floor routes on that floor in 2D. */
    private fun focusLevelFor(route: HospitalRoute): Int? =
        if (route.transfer != null) null
        else route.segments.firstOrNull()?.floorLevel ?: route.startNode.floorLevel
}
