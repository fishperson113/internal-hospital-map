package com.example.data.repository

import com.example.data.model.ApiConfig
import com.example.data.model.LatLng
import com.example.data.model.Place
import com.example.data.model.PlaceCategory
import com.example.data.model.RouteInfo
import kotlinx.coroutines.flow.Flow

interface MapRepository {
    fun getPlacesStream(category: PlaceCategory, searchQuery: String): Flow<List<Place>>
    fun getPlaceByIdStream(placeId: String): Flow<Place?>
    fun getRouteStream(origin: LatLng, destination: Place): Flow<RouteInfo>
    fun getSavedPlacesStream(): Flow<List<Place>>
    suspend fun toggleSavePlace(placeId: String)
    fun getApiConfigStream(): Flow<ApiConfig>
    suspend fun updateApiConfig(config: ApiConfig)
    suspend fun refreshPlaces()
}
