package com.example.hospital.presentation.state

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Hospital
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room

data class HospitalMapUiState(
    val isLoading: Boolean = true,
    val hospital: Hospital? = null,
    val floors: List<Floor> = emptyList(),
    // null means "Tất cả" (stacked 2.5D view), or 1..5 for single floor view
    val selectedFloorLevel: Int? = 5,
    val startRoom: Room? = null,
    val destRoom: Room? = null,
    val currentRoute: HospitalRoute? = null,
    val searchQuery: String = "",
    val searchResults: List<Room> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSelectingStartRoom: Boolean = false,
    val isNavigating: Boolean = false,
    val zoomLevel: Float = 1.0f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val isRouteDetailsExpanded: Boolean = true,
    val language: com.example.hospital.presentation.util.AppLanguage = com.example.hospital.presentation.util.AppLanguage.VIETNAMESE
)
