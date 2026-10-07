package com.example.hospital.presentation.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hospital.presentation.ui.components.FloorEmptyCard
import com.example.hospital.presentation.ui.components.FloorRail
import com.example.hospital.presentation.ui.components.HospitalCanvasMap
import com.example.hospital.presentation.ui.components.IdleSheet
import com.example.hospital.presentation.ui.components.MapFab
import com.example.hospital.presentation.ui.components.MapViewportInsets
import com.example.hospital.presentation.ui.components.NavigationBanner
import com.example.hospital.presentation.ui.components.NavigationSheet
import com.example.hospital.presentation.ui.components.RoomDetailSheet
import com.example.hospital.presentation.ui.components.RoomSearchDialog
import com.example.hospital.presentation.ui.components.RouteBottomSheet
import com.example.hospital.presentation.ui.components.RouteSelectorCard
import com.example.hospital.presentation.ui.components.SearchPill
import com.example.hospital.presentation.ui.components.TransferPreferenceChips
import com.example.hospital.presentation.ui.components.ViewTitleChip
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.hospital.presentation.viewmodel.HospitalMapViewModel
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapCanvasBackground

/**
 * Mobile layout: the map is full-screen (edge-to-edge) and every control FLOATS over it.
 *
 *  ┌ status bar ─────────────────────────┐
 *  │ [Route card: From / To  ✕ ⇅]        │  ← or search pill / navigation banner
 *  │ [Nhanh nhất][Thang máy][Thang bộ]   │
 *  │ [View chip]                  [3D]   │
 *  │                              [ 3]   │  ← FloorRail (vertical, right edge)
 *  │          MAP                 [ 2]   │
 *  │                              [ 1]   │
 *  │                              [◎][VI]│
 *  │┌───────────────────────────────────┐│
 *  ││ bottom sheet: route / room / nav  ││
 *  └┴───────────────────────────────────┴┘
 *
 * The heights of the top block and bottom sheet are measured and passed to the map as
 * insets, so the camera fits the plan into the space that is actually visible.
 */
@Composable
fun HospitalMapScreen(
    viewModel: HospitalMapViewModel,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val lang = ui.language
    val l = HospitalLocalization

    var recenterRequest by remember { mutableIntStateOf(0) }
    var topBlockBottomPx by remember { mutableFloatStateOf(0f) }
    var bottomSheetPx by remember { mutableFloatStateOf(0f) }

    // Step detector needs ACTIVITY_RECOGNITION on Android 10+. No location: position comes from
    // the start place the user picks plus step + compass dead reckoning.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.onPermissionsUpdated()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    val hasRoute = ui.startRoom != null && ui.destRoom != null
    val routeLevels = ui.currentRoute?.segments?.map { it.floorLevel }?.toSet() ?: emptySet()
    val currentFloor = ui.floors.find { it.level == ui.selectedFloorLevel }

    BackHandler(
        enabled = ui.isSearchActive || ui.isNavigating || ui.selectedRoom != null || ui.isRouteDetailsExpanded
    ) { viewModel.onBack() }

    // Keep the screen on during directions (web: wake lock); released when directions end
    val view = LocalView.current
    DisposableEffect(ui.isNavigating) {
        view.keepScreenOn = ui.isNavigating
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier = modifier.fillMaxSize().background(MapCanvasBackground)) {
        val insets = with(density) {
            MapViewportInsets(
                top = if (topBlockBottomPx > 0f) topBlockBottomPx + 56.dp.toPx() else 0f, // + view chip row
                bottom = bottomSheetPx + 12.dp.toPx(),
                start = 16.dp.toPx(),
                end = 72.dp.toPx() // floor rail
            )
        }

        HospitalCanvasMap(
            floors = ui.floors,
            selectedFloorLevel = ui.selectedFloorLevel,
            currentRoute = ui.currentRoute,
            startRoom = ui.startRoom,
            destRoom = ui.destRoom,
            selectedRoom = ui.selectedRoom,
            language = lang,
            viewportInsets = insets,
            recenterRequest = recenterRequest,
            userPosition = ui.userPosition,
            userFloorLevel = ui.userFloorLevel,
            userHeadingDegrees = ui.userPlanHeading,
            isNavigating = ui.isNavigating,
            isFollowing = ui.isFollowing,
            routeCourse = ui.routeCourse,
            onUserMovedMap = viewModel::onUserMovedMap,
            onRoomClick = viewModel::onRoomTapped,
            onSelectFloor = { level -> if (!ui.isNavigating) viewModel.onSelectFloor(level) },
            onEmptyTap = viewModel::onMapEmptyTap,
            modifier = Modifier.fillMaxSize()
        )

        if (ui.isLoading) {
            CircularProgressIndicator(color = MapBluePrimary, modifier = Modifier.align(Alignment.Center))
        }

        if (currentFloor != null && currentFloor.isTodo) {
            FloorEmptyCard(
                floorLevel = currentFloor.level,
                language = lang,
                onShowAllFloors = { viewModel.onSelectFloor(null) },
                modifier = Modifier.align(Alignment.Center).padding(start = 32.dp, end = 80.dp)
            )
        }

        // ---------------- top floating block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.onGloballyPositioned { topBlockBottomPx = it.boundsInRoot().bottom },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    ui.isNavigating -> NavigationBanner(step = ui.routeSteps.getOrNull(ui.navStepIndex))
                    hasRoute -> {
                        RouteSelectorCard(
                            startRoom = ui.startRoom,
                            destRoom = ui.destRoom,
                            language = lang,
                            onStartRoomClick = { viewModel.onOpenSearch(isStartRoom = true) },
                            onDestRoomClick = { viewModel.onOpenSearch(isStartRoom = false) },
                            onSwapClick = viewModel::onSwapRooms,
                            onCloseClick = viewModel::onClearRoute
                        )
                        TransferPreferenceChips(
                            selected = ui.transferPreference,
                            language = lang,
                            onSelect = viewModel::onSetTransferPreference
                        )
                    }
                    else -> SearchPill(language = lang, onClick = { viewModel.onOpenSearch(isStartRoom = false) })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val (title, subtitle) = when {
                    ui.selectedFloorLevel == null && ui.isNavigating -> l.t(lang, "Tất cả các tầng", "All floors") to
                        (ui.currentRoute?.transfer?.let { l.t(lang, "Đổi tầng tại ", "Change floor at ") + l.roomLabel(it.roomA, lang) } ?: "")
                    ui.selectedFloorLevel == null -> l.t(lang, "Tất cả các tầng", "All floors") to
                        l.t(lang, "Chạm một tầng để mở · kéo để xoay", "Tap a floor to open · drag to rotate")
                    currentFloor == null || currentFloor.isTodo -> l.getFloorLabel(ui.selectedFloorLevel ?: 0, lang) to
                        l.t(lang, "Chưa có dữ liệu", "No data yet")
                    else -> l.getFloorLabel(currentFloor.level, lang) to
                        l.t(lang, "${currentFloor.rooms.size} phòng · chạm phòng để xem", "${currentFloor.rooms.size} rooms · tap a room")
                }
                ViewTitleChip(title = title, subtitle = subtitle, modifier = Modifier.weight(1f, fill = false))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    FloorRail(
                        floors = ui.floors,
                        selectedFloorLevel = ui.selectedFloorLevel,
                        routeFloorLevels = routeLevels,
                        language = lang,
                        onSelectFloor = viewModel::onSelectFloor
                    )
                    // Directions with the camera let go: blue, "follow me again" (web: #recenter.resume)
                    val resume = ui.isNavigating && !ui.isFollowing && ui.selectedFloorLevel != null
                    MapFab(
                        contentDescription = if (resume) l.t(lang, "Về giữa", "Re-center") else l.t(lang, "Căn giữa bản đồ", "Recenter map"),
                        icon = if (resume) Icons.Default.Navigation else Icons.Default.CenterFocusStrong,
                        tint = if (resume) Color.White else MapInk,
                        containerColor = if (resume) MapBluePrimary else Color.White,
                        onClick = {
                            if (ui.isNavigating && ui.selectedFloorLevel != null) viewModel.onResumeFollow() else recenterRequest++
                        },
                        testTag = "btn_reset_view"
                    )
                    // Positioning mode (web: #mode-btn): RoNIN = blue with white text
                    MapFab(
                        contentDescription = l.t(lang, "Đổi cách định vị", "Switch positioning mode"),
                        text = if (ui.isRoninMode) "RoNIN" else "Baseline",
                        containerColor = if (ui.isRoninMode) MapBluePrimary else Color.White,
                        textColor = if (ui.isRoninMode) Color.White else MapBluePrimary,
                        onClick = viewModel::onTogglePositioningMode,
                        testTag = "btn_positioning_mode"
                    )
                    MapFab(
                        contentDescription = l.t(lang, "Đổi ngôn ngữ", "Switch language"),
                        text = if (lang == AppLanguage.VIETNAMESE) "VI" else "EN",
                        onClick = viewModel::onToggleLanguage,
                        testTag = "btn_toggle_language"
                    )
                }
            }
        }

        // ---------------- bottom sheet (only one at a time)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomSheetPx = it.height.toFloat() }
        ) {
            val selected = ui.selectedRoom
            when {
                ui.isNavigating -> NavigationSheet(
                    steps = ui.routeSteps,
                    stepIndex = ui.navStepIndex,
                    language = lang,
                    telemetryText = ui.sensorTelemetryText,
                    diagnostics = ui.sensorDiagnostics,
                    floorChangeSuggested = ui.floorChangeSuggested,
                    offRoute = ui.isOffRoute,
                    walkingBack = ui.isWalkingBack,
                    hasArrived = ui.hasArrivedAtDestination,
                    onPrevious = viewModel::onPreviousStep,
                    onNext = viewModel::onNextStep,
                    onExit = viewModel::onStopNavigation,
                    helpStatus = ui.helpStatus,
                    onAskHelp = viewModel::onAskHelp,
                    startLabel = ui.startRoom?.let { l.roomLabel(it, lang) }.orEmpty(),
                    destLabel = ui.destRoom?.let { l.roomLabel(it, lang) }.orEmpty(),
                    onChangeStart = { viewModel.onOpenSearch(isStartRoom = true) },
                    onChangeDest = { viewModel.onOpenSearch(isStartRoom = false) },
                    voiceOn = ui.isVoiceOn,
                    onToggleVoice = viewModel::onToggleVoice
                )
                selected != null -> RoomDetailSheet(
                    room = selected,
                    language = lang,
                    onClose = viewModel::onDismissRoomDetails,
                    onRouteTo = viewModel::onRouteToSelectedRoom,
                    onRouteFrom = viewModel::onRouteFromSelectedRoom,
                    helpStatus = ui.helpStatus,
                    onAskHelp = viewModel::onAskHelp.takeIf { ui.startRoom != null }
                )
                hasRoute -> RouteBottomSheet(
                    route = ui.currentRoute,
                    steps = ui.routeSteps,
                    isCalculating = ui.isRouteCalculating,
                    routeNotFound = ui.routeNotFound,
                    transferPreference = ui.transferPreference,
                    language = lang,
                    isExpanded = ui.isRouteDetailsExpanded,
                    onToggleExpand = viewModel::onToggleRouteDetails,
                    onStepClick = viewModel::onFocusStep,
                    onStartNavigation = viewModel::onStartNavigation,
                    helpStatus = ui.helpStatus,
                    onAskHelp = viewModel::onAskHelp
                )
                else -> IdleSheet(
                    startRoom = ui.startRoom,
                    language = lang,
                    onChangeStart = { viewModel.onOpenSearch(isStartRoom = true) },
                    helpStatus = ui.helpStatus,
                    onAskHelp = viewModel::onAskHelp
                )
            }
        }

        // ---------------- full-screen search
        if (ui.isSearchActive) {
            RoomSearchDialog(
                isSelectingStart = ui.isSelectingStartRoom,
                query = ui.searchQuery,
                rooms = ui.searchResults,
                language = lang,
                floors = ui.floors.filter { !it.isTodo },
                selectedFloorId = ui.searchFloorId,
                onSelectFloor = viewModel::onSearchFloorSelected,
                onSubmit = viewModel::onSearchSubmit,
                onQueryChange = viewModel::onSearchQueryChanged,
                onSelectRoom = viewModel::onSearchResultPicked,
                onDismiss = viewModel::onCloseSearch
            )
        }
    }
}
