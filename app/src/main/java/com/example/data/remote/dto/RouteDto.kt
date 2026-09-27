package com.example.data.remote.dto

import com.example.data.model.LatLng
import com.example.data.model.RouteInfo
import com.example.data.model.RouteStep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LatLngDto(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lng") val lng: Double
)

@JsonClass(generateAdapter = true)
data class RouteStepDto(
    @Json(name = "instruction") val instruction: String,
    @Json(name = "distance_meters") val distanceMeters: Int,
    @Json(name = "duration_seconds") val durationSeconds: Int
)

@JsonClass(generateAdapter = true)
data class RouteResponseDto(
    @Json(name = "distance_km") val distanceKm: Double,
    @Json(name = "duration_minutes") val durationMinutes: Int,
    @Json(name = "waypoints") val waypoints: List<LatLngDto>?,
    @Json(name = "steps") val steps: List<RouteStepDto>?
) {
    fun toDomain(origin: LatLng, destination: LatLng, destName: String): RouteInfo {
        return RouteInfo(
            origin = origin,
            destination = destination,
            destinationName = destName,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            waypoints = waypoints?.map { LatLng(it.lat, it.lng) } ?: listOf(origin, destination),
            steps = steps?.map {
                RouteStep(
                    instruction = it.instruction,
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.durationSeconds
                )
            } ?: emptyList()
        )
    }
}
