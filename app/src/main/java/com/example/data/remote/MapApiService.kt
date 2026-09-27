package com.example.data.remote

import com.example.data.remote.dto.PlaceDto
import com.example.data.remote.dto.RouteResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Standard REST API Interface for Map operations.
 * When the user provides their custom API endpoints, this interface can be updated
 * with their specific path contracts, headers, and request models.
 */
interface MapApiService {

    @GET("places/nearby")
    suspend fun getNearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radiusMeters: Int = 5000,
        @Query("category") category: String? = null
    ): Response<List<PlaceDto>>

    @GET("places/search")
    suspend fun searchPlaces(
        @Query("query") query: String,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("category") category: String? = null
    ): Response<List<PlaceDto>>

    @GET("places/details")
    suspend fun getPlaceDetails(
        @Query("id") placeId: String
    ): Response<PlaceDto>

    @GET("directions")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "driving"
    ): Response<RouteResponseDto>
}
