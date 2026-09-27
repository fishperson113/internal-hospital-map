package com.example.data.model

data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int
)

data class RouteInfo(
    val origin: LatLng,
    val destination: LatLng,
    val destinationName: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val waypoints: List<LatLng>,
    val steps: List<RouteStep>
)
