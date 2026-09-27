package com.example.ui.viewmodel

import com.example.data.model.ApiConfig
import com.example.data.model.LatLng
import com.example.data.model.MapLayerType
import com.example.data.model.MapViewport
import com.example.data.model.Place
import com.example.data.model.PlaceCategory
import com.example.data.model.RouteInfo

data class MapUiState(
    val places: List<Place> = emptyList(),
    val selectedPlace: Place? = null,
    val activeRoute: RouteInfo? = null,
    val viewport: MapViewport = MapViewport(),
    val userLocation: LatLng = LatLng(10.7769, 106.7009),
    val searchQuery: String = "",
    val selectedCategory: PlaceCategory = PlaceCategory.ALL,
    val savedPlaces: List<Place> = emptyList(),
    val isLoading: Boolean = false,
    val showLayersSheet: Boolean = false,
    val showSavedSheet: Boolean = false,
    val showApiConfigDialog: Boolean = false,
    val apiConfig: ApiConfig = ApiConfig(),
    val userNotice: String? = null
)
