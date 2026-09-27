package com.example.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screen.components.ApiConfigDialog
import com.example.ui.screen.components.CategoryFilterChips
import com.example.ui.screen.components.InteractiveMapCanvas
import com.example.ui.screen.components.MapControlsOverlay
import com.example.ui.screen.components.MapLayersSheet
import com.example.ui.screen.components.MapSearchBar
import com.example.ui.screen.components.NavigationRouteCard
import com.example.ui.screen.components.PlaceDetailBottomSheet
import com.example.ui.screen.components.SavedPlacesSheet
import com.example.ui.viewmodel.MapViewModel

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.onDismissNotice()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_screen"),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Full-screen Interactive Vector Map Canvas
            InteractiveMapCanvas(
                viewport = uiState.viewport,
                places = uiState.places,
                selectedPlace = uiState.selectedPlace,
                activeRoute = uiState.activeRoute,
                userLocation = uiState.userLocation,
                onPlaceSelected = { viewModel.onPlaceSelected(it) },
                onPan = { deltaLat, deltaLng -> viewModel.onPan(deltaLat, deltaLng) }
            )

            // 2. Top Bar (Search + Categories or Navigation Card)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            ) {
                if (uiState.activeRoute != null) {
                    NavigationRouteCard(
                        route = uiState.activeRoute!!,
                        onStopNavigation = { viewModel.onStopNavigation() }
                    )
                } else {
                    MapSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        onOpenApiConfig = { viewModel.onToggleApiConfigDialog(true) },
                        onOpenSavedPlaces = { viewModel.onToggleSavedSheet(true) },
                        savedCount = uiState.savedPlaces.size
                    )

                    CategoryFilterChips(
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = { viewModel.onCategorySelected(it) }
                    )
                }
            }

            // 3. Right Floating Map Controls
            MapControlsOverlay(
                onCenterMyLocation = { viewModel.onCenterOnMyLocation() },
                onToggleLayers = { viewModel.onToggleLayersSheet(true) },
                onZoomIn = { viewModel.onZoomIn() },
                onZoomOut = { viewModel.onZoomOut() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )

            // 4. Bottom Sheets
            // 4a. Place Detail Sheet
            AnimatedVisibility(
                visible = uiState.selectedPlace != null && uiState.activeRoute == null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                uiState.selectedPlace?.let { place ->
                    PlaceDetailBottomSheet(
                        place = place,
                        onStartNavigation = { viewModel.onStartNavigation(it) },
                        onToggleSave = { viewModel.onToggleSavePlace(it) },
                        onDismiss = { viewModel.onClearSelectedPlace() }
                    )
                }
            }

            // 4b. Map Layers Sheet
            AnimatedVisibility(
                visible = uiState.showLayersSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                MapLayersSheet(
                    currentLayer = uiState.viewport.layerType,
                    onLayerSelected = { viewModel.onLayerSelected(it) },
                    onDismiss = { viewModel.onToggleLayersSheet(false) }
                )
            }

            // 4c. Saved Places Sheet
            AnimatedVisibility(
                visible = uiState.showSavedSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                SavedPlacesSheet(
                    savedPlaces = uiState.savedPlaces,
                    onSelectPlace = { viewModel.onPlaceSelected(it) },
                    onRemoveSaved = { viewModel.onToggleSavePlace(it) },
                    onDismiss = { viewModel.onToggleSavedSheet(false) }
                )
            }

            // 5. Developer API Configuration Modal Dialog
            if (uiState.showApiConfigDialog) {
                ApiConfigDialog(
                    config = uiState.apiConfig,
                    onSaveConfig = { viewModel.onSaveApiConfig(it) },
                    onDismiss = { viewModel.onToggleApiConfigDialog(false) }
                )
            }
        }
    }
}
