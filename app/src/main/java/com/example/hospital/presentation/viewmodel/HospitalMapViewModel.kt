package com.example.hospital.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Positioning
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.domain.util.HospitalGeometry
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase
import com.example.hospital.presentation.state.HospitalMapUiState
import com.example.hospital.presentation.state.TransferPreference
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.hospital.data.api.RoninClient
import com.example.hospital.sensor.HeadingFusion
import com.example.hospital.sensor.HospitalSensorData
import com.example.hospital.sensor.IndoorPdrState
import com.example.hospital.sensor.IndoorPdrTracker
import com.example.hospital.sensor.StepLearner
import com.example.hospital.sensor.norm
import com.example.hospital.sensor.wrap
import android.content.Context
import com.example.hospital.presentation.util.RouteStepFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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
    private val prefs = sensorManager?.context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Positioning (web: POS, fusion, learner in app.js). Constants come from the server (GET /api/floors ->
    // positioning); until they arrive the sensors are ignored and the dot stays at the start.
    private var positioning: Positioning? = null
    private var fusion: HeadingFusion? = null
    private var learner: StepLearner? = null
    private var pdrTracker: IndoorPdrTracker? = null // only while directions run
    private var heading: Float? = null // walking direction on the plan (web: S.heading)
    private var walkedSteps = 0        // since directions started (web: S.walked)
    private var walkedM = 0f
    private var lastWalkedMeters = 0f
    private var lastTotalSteps = 0
    private var lastStepMs = 0L

    // RoNIN mode (web: `ronin` in app.js): the server turns raw motion into walking velocity, one pseudo step each.
    // Its frame has no north: the compass over the first warmup samples turns it (roninOffset), then only the
    // route pulls it (roninRoute).
    private var roninOn = prefs?.getString(KEY_POS_MODE, null) == MODE_RONIN
    private val roninWarm = mutableListOf<Double>()
    private var roninOffset: Float? = null
    private var roninRoute = 0f
    private var roninRaw: Float? = null
    private val roninClient = RoninClient(::onRoninVelocity)
    private var offRouteMeters = 0f // walked off route since the last re-route
    private var lastRerouteMs = 0L
    private var pressureAtStart: Float? = null // barometer when the walk on the start floor began
    private var live = false // like the web: the CRM sees the user once they pick a place / start / ask help
    private val liveClient = com.example.hospital.data.api.LiveClient(context = sensorManager?.context) { status ->
        _uiState.update { it.copy(helpStatus = status) }
    }

    init {
        _uiState.update { it.copy(isRoninMode = roninOn) }
        sensorManager?.setRoninMode(roninOn)
        if (roninOn) roninClient.connect()
        loadMapData()
        startSensorObservation()
        startPositioningTicker()
        // Same as the web's liveSync(): push the state to the doctor CRM whenever it changes.
        viewModelScope.launch {
            uiState.map(::liveJson).distinctUntilChanged().collect { json ->
                if (live && json != null) liveClient.sendState(json)
            }
        }
    }

    /** Steps from the sensor manager (Baseline mode only; RoNIN steps come from [onRoninVelocity]). */
    private fun startSensorObservation() {
        sensorManager?.startTracking()
        sensorJob?.cancel()
        sensorJob = viewModelScope.launch {
            sensorManager?.sensorData?.collect { data ->
                val dm = data.walkedMeters - lastWalkedMeters
                val dn = data.totalSteps - lastTotalSteps
                lastWalkedMeters = data.walkedMeters
                lastTotalSteps = data.totalSteps
                // one publish may carry several steps: feed them one by one, like the web
                if (dm > 0f && !roninOn) {
                    val n = maxOf(1, dn)
                    repeat(n) { onStep(dm / n) }
                }
                _uiState.update {
                    it.copy(
                        isSensorTrackingActive = data.isTracking,
                        floorChangeSuggested = floorChangeSuggested(it, data.pressureHpa)
                    )
                }
            }
        }
    }

    /** Server constants arrived: heading fusion, step detector and learner start (web: initPositioning). */
    private fun initPositioning(p: Positioning) {
        positioning = p
        // the step length factor this phone learned last time
        val savedK = prefs?.getFloat(KEY_STEP_K, 0f)?.takeIf { it > 0f } ?: p.stepK
        fusion = HeadingFusion(p.planNorthDeg, p.fusion)
        learner = StepLearner(savedK, p.learner)
        sensorManager?.configureSteps(p.step, savedK)
    }

    /** One step of [len] metres in the direction [heading] (web: onStep). */
    private fun onStep(len: Float) {
        val p = positioning ?: return
        walkedSteps++
        walkedM += len
        lastStepMs = SystemClock.elapsedRealtime()
        val t = pdrTracker ?: return
        if (!_uiState.value.isNavigating) return
        val leg = t.onWalked(len, heading)
        // a corner closed a leg of known length: learn this user's step length from it.
        // RoNIN steps are not this user's steps.
        val l = learner
        if (leg != null && !roninOn && l != null && l.onLeg(leg.realM, leg.countedM)) {
            sensorManager?.setStepK(l.k)
            prefs?.edit()?.putFloat(KEY_STEP_K, l.k)?.apply()
        }
        // walking along the line: let the route fine-tune the heading
        val line = t.lineHeading()
        val h = heading
        if (line != null && h != null && abs(wrap(h - line)) < p.fusion.routeGateDeg) {
            if (roninOn) roninRoute += wrap(line - h) * p.fusion.routeGain
            else fusion?.alignTo(line, sensorManager?.sensorData?.value?.yawDegrees)
        }
        val st = t.state()
        if (st.offRoute) offRouteMeters += len
        applyPdrUpdate(st)
    }

    /** 4 Hz: fuse the heading, RoNIN north and samples, telemetry, reroute when off the route (web: setInterval 250). */
    private fun startPositioningTicker() {
        viewModelScope.launch {
            while (isActive) {
                positioningTick()
                delay(250)
            }
        }
    }

    private fun positioningTick() {
        val p = positioning ?: return
        val d = sensorManager?.sensorData?.value ?: return
        val fused = fusion?.update(d.headingDegrees, d.yawDegrees)
        heading = if (roninOn) roninTick(p, d.headingDegrees, d.relAzimuthDegrees) ?: fused else fused
        // RoNIN gives the walking direction only while walking. The cone shows where the phone points: its relative
        // orientation turned by the same north offset (and route correction), so it turns while the user stands still.
        val o = roninOffset
        val rel = d.relAzimuthDegrees
        val facing = if (roninOn && o != null && rel != null) norm(rel + o - p.planNorthDeg + roninRoute) else heading
        _uiState.update {
            it.copy(
                userPlanHeading = facing,
                stepCount = walkedSteps,
                sensorTelemetryText = telemetryText(p, d.headingDegrees, it.language),
                sensorDiagnostics = diagnosticsText(d)
            )
        }
        maybeReroute(p)
    }

    /** Sends the RoNIN samples, learns north. Returns the RoNIN heading (plan degrees) or null before the first one. */
    private fun roninTick(p: Positioning, compass: Float?, rel: Float?): Float? {
        sensorManager?.drainRoninBatch()?.let { if (it.isNotEmpty()) roninClient.send(it) }
        if (roninOffset == null && compass != null && rel != null) {
            roninWarm += Math.toRadians(wrap(compass - rel).toDouble())
            if (roninWarm.size >= p.ronin.warmupSamples) { // circular mean
                roninOffset = Math.toDegrees(atan2(roninWarm.sumOf { sin(it) }, roninWarm.sumOf { cos(it) })).toFloat()
            }
        }
        return roninRaw?.let { norm(it + roninRoute) }
    }

    /** One velocity from /api/ronin = one pseudo step of speed * chunk_s metres. */
    private fun onRoninVelocity(x: Float, y: Float) {
        val p = positioning ?: return
        val o = roninOffset ?: return // no north yet
        val speed = hypot(x, y)
        if (!roninOn || !(speed >= p.ronin.minSpeedMps)) return // standing
        val raw = norm(Math.toDegrees(atan2(x, y).toDouble()).toFloat() + o - p.planNorthDeg)
        roninRaw = raw
        heading = norm(raw + roninRoute)
        onStep(speed * p.ronin.chunkS)
    }

    private fun roninReset() {
        roninWarm.clear()
        roninOffset = null
        roninRoute = 0f
        roninRaw = null
        sensorManager?.drainRoninBatch()
    }

    /** Map button: Baseline <-> RoNIN. RoNIN learns north again from scratch. The choice is kept. */
    fun onTogglePositioningMode() {
        roninOn = !roninOn
        prefs?.edit()?.putString(KEY_POS_MODE, if (roninOn) MODE_RONIN else MODE_BASELINE)?.apply()
        roninReset()
        sensorManager?.setRoninMode(roninOn)
        if (roninOn) roninClient.connect() else roninClient.close()
        _uiState.update { it.copy(isRoninMode = roninOn) }
    }

    /** Web pdrText(), plus the plan north from the server. */
    private fun telemetryText(p: Positioning, compass: Float?, lang: AppLanguage): String {
        fun t(vi: String, en: String) = HospitalLocalization.t(lang, vi, en)
        val sb = StringBuilder()
        if (roninOn) sb.append("RoNIN").append(if (roninClient.isConnected) "" else " (${t("mất kết nối", "offline")})").append(" · ")
        sb.append("📡 ").append(compass?.roundToInt()?.toString() ?: "—").append("°")
        sb.append(" · ").append(t("bắc sơ đồ", "plan north")).append(" ").append(p.planNorthDeg.roundToInt()).append("°")
        sb.append(" · ").append(walkedSteps).append(" ").append(t("bước", "steps"))
        sb.append(" · ").append("%.1f".format(walkedM)).append(" m")
        if (walkedSteps > 0) sb.append(" · ~").append("%.2f".format(walkedM / walkedSteps)).append(" m/").append(t("bước", "step"))
        learner?.let { l ->
            sb.append(" · k ").append("%.2f".format(l.k))
            if (l.legs > 0) sb.append(" (").append(t("học", "learned")).append(" ").append(l.legs).append(")")
        }
        val dropped = fusion?.dropped ?: 0
        if (dropped > 0) sb.append(" · ").append(t("lọc nhiễu từ", "magnetic noise dropped")).append(" ").append(dropped)
        return sb.toString()
    }

    private fun diagnosticsText(data: HospitalSensorData): String {
        val d = data.diag
        return listOf(
            "Gia tốc ${d.accelHz} Hz · tự đếm ${d.accelSteps} bước · bước cuối ${"%.2f".format(d.lastStepM)} m",
            "Phần cứng (chỉ để so, không dùng): detector ${d.hwDetectorSteps} · counter ${d.hwCounterSteps}",
            "Gyro ${data.yawDegrees?.let { y -> "%.0f°".format(y) } ?: "—"} · áp suất ${data.pressureHpa?.let { p -> "%.2f hPa".format(p) } ?: "—"}",
            "RoNIN: bắc ${roninOffset?.let { "%.0f°".format(it) } ?: "đang học (${roninWarm.size})"} · kéo về tuyến ${"%.0f°".format(roninRoute)}",
            "Cảm biến: ${data.activeSensors.joinToString()}"
        ).joinToString("\n")
    }

    /**
     * Barometer hint: during the steps before arriving on the new floor, the pressure changed by
     * about the floors to climb (~0.4 hPa per floor, up = lower pressure). Floors still change
     * only by the button; this just points at it.
     */
    private fun floorChangeSuggested(s: HospitalMapUiState, p: Float?): Boolean {
        val t = s.currentRoute?.transfer ?: return false
        val p0 = pressureAtStart ?: return false
        if (!s.isNavigating || s.navStepIndex > 1 || p == null) return false
        val floors = t.roomB.floorLevel - t.roomA.floorLevel
        if (floors == 0) return false
        val climbed = (p0 - p) * (if (floors > 0) 1f else -1f)
        return climbed >= FLOOR_HINT_HPA * kotlin.math.abs(floors)
    }

    companion object {
        const val FLOOR_HINT_HPA = 0.3f // calibration knob: pressure change per floor that triggers the hint
        private const val PREFS_NAME = "hospital_pdr_prefs"
        private const val MAX_RESULTS = 60 // web: search shows at most 60 rows
        private const val KEY_STEP_K = "step_k"     // learned step length factor (web: localStorage.stepK)
        private const val KEY_POS_MODE = "pos_mode" // web: localStorage.posMode
        private const val MODE_RONIN = "ronin"
        private const val MODE_BASELINE = "baseline"
        // web app.js DEFAULT_FROM / DEFAULT_TO
        private const val DEFAULT_FROM_FLOOR = "tang-2"
        private const val DEFAULT_FROM_ID = 40
        private const val DEFAULT_TO_FLOOR = "tang-3"
        private const val DEFAULT_TO_ID = 1
    }

    fun onPermissionsUpdated() {
        sensorManager?.startTracking()
    }

    private fun applyPdrUpdate(pdr: IndoorPdrState) {
        _uiState.update {
            it.copy(
                userPosition = pdr.position, userFloorLevel = pdr.floorLevel,
                hasArrivedAtDestination = pdr.hasArrived, isOffRoute = pdr.offRoute, isWalkingBack = pdr.backward,
                // off the route or at a shaft there is no course: keep the last one
                routeCourse = pdrTracker?.courseHeading() ?: it.routeCourse
            )
        }
    }

    /** Corridor test for free walking: inside the floor plan and not inside any room. */
    private fun walkable(level: Int, p: Point2D): Boolean {
        val f = _uiState.value.floors.find { it.level == level } ?: return true
        val fr = f.frame
        if (fr.size >= 4 && (p.x !in fr[0]..fr[2] || p.y !in fr[1]..fr[3])) return false
        return f.rooms.none { HospitalGeometry.pointInPolygon(p.x, p.y, it.polygon) }
    }

    /** Off route: route again from where the dot is (the server takes a "floor@x,y" start), directions go on. */
    private fun maybeReroute(p: Positioning) {
        val s = _uiState.value
        if (!s.isNavigating || !s.isOffRoute || s.isRouteCalculating || offRouteMeters <= 0f) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRerouteMs < p.reroute.minGapMs) return
        if (offRouteMeters < p.reroute.everyM && now - lastStepMs < p.reroute.pauseMs) return
        val pos = s.userPosition ?: return
        val floor = s.floors.find { it.level == s.userFloorLevel } ?: return
        lastRerouteMs = now
        offRouteMeters = 0f
        onSelectStartRoom(spotRoom(floor, pos))
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
        val transfer = s.currentRoute?.transfer
        val at = if (s.isNavigating && transfer != null && s.navStepIndex > 0)
            (if (s.navStepIndex == 1) transfer.roomA else transfer.roomB) else start
        return JSONObject()
            .put("type", "state")
            .put("from", room(start))
            .put("to", room(s.destRoom))
            .put("at", room(at)) // web liveState: the last landmark passed (start, then the shaft)
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

    private fun loadMapData() {
        viewModelScope.launch {
            getHospitalMapUseCase().collect { mapData ->
                val allRooms = mapData.floors.flatMap { it.rooms }
                roomsById = allRooms.associateBy { it.id }
                if (positioning == null) mapData.positioning?.let(::initPositioning)
                // Same defaults as the web (DEFAULT_FROM / DEFAULT_TO in app.js)
                val start = defaultStart()
                val dest = allRooms.find { it.floorId == DEFAULT_TO_FLOOR && it.rawId == DEFAULT_TO_ID } // "Phòng cách ly"

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hospital = mapData.hospital,
                        floors = mapData.floors,
                        // open in 2D on the start floor, never on the 3D stack (only the 3D button opens it)
                        selectedFloorLevel = if (it.isLoading) (start?.floorLevel ?: firstMappedLevel(mapData.floors)) else it.selectedFloorLevel,
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

    /** While navigating off route, a tap on the map = "I am here": correct the position and re-route. */
    fun onRoomTapped(room: Room) {
        val s = _uiState.value
        if (s.isNavigating) {
            if (s.isOffRoute) onSelectStartRoom(room)
            return
        }
        _uiState.update { it.copy(selectedRoom = room, isRouteDetailsExpanded = false) }
    }

    fun onDismissRoomDetails() {
        _uiState.update { it.copy(selectedRoom = null) }
    }

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
        val spot = if (inside) spotRoom(floor!!, point!!) else null
        if (s.isNavigating) {
            if (s.isOffRoute && spot != null) onSelectStartRoom(spot)
            return
        }
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
        _uiState.update { it.copy(destRoom = room, selectedRoom = null) }
        // no start yet: the registration desk, like the web (DEFAULT_FROM), unless that is the destination
        if (_uiState.value.startRoom == null) defaultStart()?.takeIf { it.id != room.id }?.let(::setStart)
        val start = _uiState.value.startRoom
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
        // Directions stay on while the new route loads (web: reroute()); it restarts on the new route.
        _uiState.update { it.copy(isSearchActive = false, selectedRoom = null) }
        calculateRoute(focusMapOnRoute = true, resumeNavigation = resume)
    }

    fun onSelectDestRoom(room: Room) {
        goLive()
        val resume = _uiState.value.isNavigating
        _uiState.update { it.copy(destRoom = room, isSearchActive = false, selectedRoom = null) }
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

    /**
     * Tapping a step moves the map to that step's floor (2D; a floor change shows the shaft's floor).
     * During directions it also jumps to that step, like the web.
     */
    fun onFocusStep(index: Int) {
        val step = _uiState.value.routeSteps.getOrNull(index) ?: return
        if (_uiState.value.isNavigating) {
            _uiState.update { it.copy(navStepIndex = index, selectedFloorLevel = step.focusFloorLevel) }
            pdrTracker?.goToStep(index)?.let(::applyPdrUpdate)
        } else {
            _uiState.update { it.copy(selectedFloorLevel = step.focusFloorLevel) }
        }
    }

    // ---------------------------------------------------------------- turn-by-turn

    fun onStartNavigation() {
        val steps = _uiState.value.routeSteps
        val route = _uiState.value.currentRoute
        if (steps.isEmpty()) return
        goLive()
        walkedSteps = 0
        walkedM = 0f
        offRouteMeters = 0f
        val p = positioning
        pdrTracker = if (p != null && route != null && route.segments.isNotEmpty() && route.segments.all { it.points.isNotEmpty() })
            IndoorPdrTracker(route, p.tracker, ::walkable) else null
        val initialPdr = pdrTracker?.state()
        _uiState.update { it.copy(routeCourse = null) }
        initialPdr?.let(::applyPdrUpdate)
        pressureAtStart = sensorManager?.sensorData?.value?.pressureHpa

        _uiState.update {
            it.copy(
                isNavigating = true,
                isOffRoute = false,
                isWalkingBack = false,
                isFollowing = true,
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
        pdrTracker?.goToStep(next)?.let(::applyPdrUpdate) // floor change happens only here
    }

    fun onPreviousStep() {
        val s = _uiState.value
        if (s.navStepIndex <= 0) return
        val prev = s.navStepIndex - 1
        _uiState.update { it.copy(navStepIndex = prev, selectedFloorLevel = s.routeSteps[prev].focusFloorLevel) }
        pdrTracker?.goToStep(prev)?.let(::applyPdrUpdate)
        if (prev == 0) pressureAtStart = sensorManager?.sensorData?.value?.pressureHpa
    }


    /** The user dragged the map during directions: stop following until "recenter" (web: S.follow = false). */
    fun onUserMovedMap() {
        if (_uiState.value.isFollowing) _uiState.update { it.copy(isFollowing = false) }
    }

    /** Recenter during directions: follow the dot again. */
    fun onResumeFollow() {
        _uiState.update { it.copy(isFollowing = it.isNavigating) }
    }

    fun onStopNavigation() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                isOffRoute = false,
                isWalkingBack = false,
                navStepIndex = 0,
                isFollowing = false,
                routeCourse = null,
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
        roninClient.close()
    }

    /** System back: close the top-most layer first. Returns true if something was closed. */
    fun onBack(): Boolean {
        val s = _uiState.value
        when {
            s.isSearchActive -> onCloseSearch()
            s.isNavigating -> onStopNavigation()
            s.selectedRoom != null -> onDismissRoomDetails()
            s.isRouteDetailsExpanded -> onToggleRouteDetails()
            // never back into the 3D stack on its own (G6): only the 3D button opens it
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
                searchFloorId = null, // opening the search always starts on "all floors" (web)
                searchResults = searchLocal("", null)
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val floorId = _uiState.value.searchFloorId
        val local = searchLocal(query, floorId)
        _uiState.update { it.copy(searchQuery = query, searchResults = local) }
        searchJob?.cancel()
        if (local.isEmpty() && query.isNotBlank()) {
            // fall back to the repository search (e.g. room codes)
            searchJob = viewModelScope.launch {
                val results = searchRoomsUseCase(query).filter { floorId == null || it.floorId == floorId }.take(MAX_RESULTS)
                _uiState.update { if (it.searchQuery == query && it.searchFloorId == floorId) it.copy(searchResults = results) else it }
            }
        }
    }

    /** Floor chip in the search (null = all floors); combines with the query. */
    fun onSearchFloorSelected(floorId: String?) {
        _uiState.update { it.copy(searchFloorId = floorId) }
        onSearchQueryChanged(_uiState.value.searchQuery)
    }

    /** Enter in the search box picks the first result (web). */
    fun onSearchSubmit() {
        _uiState.value.searchResults.firstOrNull()?.let(::onSearchResultPicked)
    }

    fun onSearchResultPicked(room: Room) {
        if (_uiState.value.isSelectingStartRoom) onSelectStartRoom(room) else onSelectDestRoom(room)
    }

    fun onCloseSearch() {
        _uiState.update { it.copy(isSearchActive = false) }
    }

    /** Accent-insensitive search over VI + EN names ("phong kham" matches "PHÒNG KHÁM"). */
    private fun searchLocal(query: String, floorId: String?): List<Room> {
        val all = _uiState.value.floors.filter { floorId == null || it.floorId == floorId }.flatMap { it.rooms }
            .filter { it.name.isNotBlank() && !it.name.all(Char::isDigit) }
        val q = normalize(query).trim()
        if (q.isEmpty()) return all.filter { it.kind == null }.sortedBy { it.floorLevel }.take(MAX_RESULTS)
        return all.filter { normalize(it.name).contains(q) || normalize(it.nameEn).contains(q) }
            .sortedWith(compareByDescending<Room> { normalize(it.name).startsWith(q) }.thenBy { it.floorLevel })
            .take(MAX_RESULTS)
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
            if (resumeNavigation) { if (route != null) onStartNavigation() else onStopNavigation() }
        }
    }

    /** Every route opens in 2D on the start floor, cross-floor too (G6: no automatic 3D). */
    private fun focusLevelFor(route: HospitalRoute): Int =
        route.segments.firstOrNull()?.floorLevel ?: route.startNode.floorLevel

    /** Web DEFAULT_FROM: "Quầy đăng ký khám" (registration desk). */
    private fun defaultStart(): Room? =
        _uiState.value.floors.firstOrNull { it.floorId == DEFAULT_FROM_FLOOR }?.rooms?.firstOrNull { it.rawId == DEFAULT_FROM_ID }
            ?: roomsById["$DEFAULT_FROM_FLOOR:$DEFAULT_FROM_ID"]

    private fun firstMappedLevel(floors: List<Floor>): Int? = (floors.firstOrNull { !it.isTodo } ?: floors.firstOrNull())?.level
}
