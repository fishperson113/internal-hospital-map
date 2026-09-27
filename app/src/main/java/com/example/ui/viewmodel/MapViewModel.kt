package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ApiConfig
import com.example.data.model.LatLng
import com.example.data.model.MapLayerType
import com.example.data.model.MapViewport
import com.example.data.model.Place
import com.example.data.model.PlaceCategory
import com.example.data.model.RouteInfo
import com.example.data.repository.MapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class MapDataState(
    val places: List<Place>,
    val selectedPlace: Place?,
    val activeRoute: RouteInfo?,
    val viewport: MapViewport,
    val savedPlaces: List<Place>
)

private data class OverlayUiState(
    val showLayersSheet: Boolean = false,
    val showSavedSheet: Boolean = false,
    val showApiConfigDialog: Boolean = false,
    val userNotice: String? = null
)

class MapViewModel(
    private val repository: MapRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(PlaceCategory.ALL)
    private val _selectedPlace = MutableStateFlow<Place?>(null)
    private val _activeRoute = MutableStateFlow<RouteInfo?>(null)
    private val _viewport = MutableStateFlow(MapViewport())
    private val _overlayState = MutableStateFlow(OverlayUiState())
    private val _userLocation = LatLng(10.7769, 106.7009)

    // Reactively query places stream based on category and search query Flow
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _placesStream = combine(_selectedCategory, _searchQuery) { cat, query ->
        Pair(cat, query)
    }.flatMapLatest { (cat, query) ->
        repository.getPlacesStream(cat, query)
    }

    private val _savedPlacesStream = repository.getSavedPlacesStream()
    private val _apiConfigStream = repository.getApiConfigStream()

    private val _mapDataFlow = combine(
        _placesStream,
        _selectedPlace,
        _activeRoute,
        _viewport,
        _savedPlacesStream
    ) { places, selected, route, viewport, saved ->
        MapDataState(places, selected, route, viewport, saved)
    }

    val uiState: StateFlow<MapUiState> = combine(
        _mapDataFlow,
        _selectedCategory,
        _searchQuery,
        _apiConfigStream,
        _overlayState
    ) { data, cat, query, apiConfig, overlay ->
        MapUiState(
            places = data.places,
            selectedPlace = data.selectedPlace,
            activeRoute = data.activeRoute,
            viewport = data.viewport,
            userLocation = _userLocation,
            searchQuery = query,
            selectedCategory = cat,
            savedPlaces = data.savedPlaces,
            isLoading = false,
            showLayersSheet = overlay.showLayersSheet,
            showSavedSheet = overlay.showSavedSheet,
            showApiConfigDialog = overlay.showApiConfigDialog,
            apiConfig = apiConfig,
            userNotice = overlay.userNotice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: PlaceCategory) {
        if (_selectedCategory.value == category) {
            _selectedCategory.value = PlaceCategory.ALL
        } else {
            _selectedCategory.value = category
        }
    }

    fun onPlaceSelected(place: Place) {
        _selectedPlace.value = place
        // Move viewport center slightly towards selected place
        _viewport.update { current ->
            current.copy(
                center = LatLng(place.latitude, place.longitude),
                zoomLevel = maxOf(14.5f, current.zoomLevel)
            )
        }
    }

    fun onClearSelectedPlace() {
        _selectedPlace.value = null
    }

    fun onStartNavigation(place: Place) {
        viewModelScope.launch {
            repository.getRouteStream(_userLocation, place).collect { route ->
                _activeRoute.value = route
                _selectedPlace.value = null
                _overlayState.update { it.copy(userNotice = "Bắt đầu chỉ đường đến ${place.name}") }
            }
        }
    }

    fun onStopNavigation() {
        _activeRoute.value = null
        _overlayState.update { it.copy(userNotice = "Đã dừng điều hướng") }
    }

    fun onToggleSavePlace(placeId: String) {
        viewModelScope.launch {
            repository.toggleSavePlace(placeId)
        }
    }

    fun onCenterOnMyLocation() {
        _viewport.update { it.copy(center = _userLocation, zoomLevel = 15f) }
        _overlayState.update { it.copy(userNotice = "Đã căn giữa vị trí hiện tại") }
    }

    fun onZoomIn() {
        _viewport.update { it.copy(zoomLevel = (it.zoomLevel + 0.5f).coerceAtMost(18f)) }
    }

    fun onZoomOut() {
        _viewport.update { it.copy(zoomLevel = (it.zoomLevel - 0.5f).coerceAtLeast(10f)) }
    }

    fun onPan(deltaLat: Double, deltaLng: Double) {
        _viewport.update { current ->
            current.copy(
                center = LatLng(
                    current.center.latitude + deltaLat,
                    current.center.longitude + deltaLng
                )
            )
        }
    }

    fun onLayerSelected(layer: MapLayerType) {
        _viewport.update { it.copy(layerType = layer) }
        _overlayState.update { it.copy(showLayersSheet = false) }
    }

    fun onToggleLayersSheet(show: Boolean) {
        _overlayState.update { it.copy(showLayersSheet = show) }
    }

    fun onToggleSavedSheet(show: Boolean) {
        _overlayState.update { it.copy(showSavedSheet = show) }
    }

    fun onToggleApiConfigDialog(show: Boolean) {
        _overlayState.update { it.copy(showApiConfigDialog = show) }
    }

    fun onSaveApiConfig(newConfig: ApiConfig) {
        viewModelScope.launch {
            repository.updateApiConfig(newConfig)
            _overlayState.update {
                it.copy(
                    showApiConfigDialog = false,
                    userNotice = "Đã cập nhật cấu hình API!"
                )
            }
        }
    }

    fun onDismissNotice() {
        _overlayState.update { it.copy(userNotice = null) }
    }
}
