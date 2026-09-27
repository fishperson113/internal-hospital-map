package com.example.data.model

enum class MapLayerType(val title: String) {
    STANDARD("Mặc định"),
    SATELLITE("Vệ tinh"),
    TRAFFIC("Giao thông")
}

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

data class MapViewport(
    val center: LatLng = LatLng(10.7769, 106.7009), // Center on city (e.g. Ho Chi Minh City)
    val zoomLevel: Float = 14f,
    val bearing: Float = 0f,
    val layerType: MapLayerType = MapLayerType.STANDARD
)
