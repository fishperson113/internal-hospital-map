package com.example.hospital.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hospital.presentation.ui.components.*
import com.example.hospital.presentation.viewmodel.HospitalMapViewModel

@Composable
fun HospitalMapScreen(
    viewModel: HospitalMapViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC),
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive Map Canvas as background/body
            HospitalCanvasMap(
                floors = uiState.floors,
                selectedFloorLevel = uiState.selectedFloorLevel,
                currentRoute = uiState.currentRoute,
                startRoom = uiState.startRoom,
                destRoom = uiState.destRoom,
                language = uiState.language,
                zoomLevel = uiState.zoomLevel,
                panOffsetX = uiState.panOffsetX,
                panOffsetY = uiState.panOffsetY,
                onPan = { dx, dy -> viewModel.onPan(dx, dy) },
                onZoomIn = { viewModel.onZoomIn() },
                onZoomOut = { viewModel.onZoomOut() },
                onResetView = { viewModel.onResetView() },
                onRoomClick = { room -> viewModel.onSelectDestRoom(room) },
                onSelectFloor = { level -> viewModel.onSelectFloor(level) },
                modifier = Modifier.fillMaxSize()
            )

            // Top Floating Overlay: TopBar + RouteSelector + FloorSelector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.95f))
            ) {
                HospitalTopBar(
                    language = uiState.language,
                    onToggleLanguage = { viewModel.onToggleLanguage() },
                    onSearchClick = { viewModel.onOpenSearch(isStartRoom = false) }
                )

                RouteSelectorCard(
                    startRoom = uiState.startRoom,
                    destRoom = uiState.destRoom,
                    language = uiState.language,
                    onStartRoomClick = { viewModel.onOpenSearch(isStartRoom = true) },
                    onDestRoomClick = { viewModel.onOpenSearch(isStartRoom = false) },
                    onSwapClick = { viewModel.onSwapRooms() }
                )

                FloorSelectorRow(
                    floors = uiState.floors,
                    selectedFloorLevel = uiState.selectedFloorLevel,
                    language = uiState.language,
                    onSelectFloor = { level -> viewModel.onSelectFloor(level) }
                )
            }

            // Bottom Detailed Route Sheet
            DetailedRouteCard(
                route = uiState.currentRoute,
                language = uiState.language,
                isExpanded = uiState.isRouteDetailsExpanded,
                isNavigating = uiState.isNavigating,
                onToggleExpand = { viewModel.onToggleRouteDetails() },
                onStartNavigation = { viewModel.onStartNavigation() },
                onStopNavigation = { viewModel.onStopNavigation() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Room Search Modal Bottom Sheet
            if (uiState.isSearchActive) {
                RoomSearchDialog(
                    isSelectingStart = uiState.isSelectingStartRoom,
                    query = uiState.searchQuery,
                    rooms = uiState.searchResults,
                    language = uiState.language,
                    onQueryChange = { q -> viewModel.onSearchQueryChanged(q) },
                    onSelectRoom = { room ->
                        if (uiState.isSelectingStartRoom) {
                            viewModel.onSelectStartRoom(room)
                        } else {
                            viewModel.onSelectDestRoom(room)
                        }
                    },
                    onDismiss = { viewModel.onCloseSearch() }
                )
            }
        }
    }
}
