package com.example.data.model

data class Place(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val rating: Float,
    val reviewCount: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val distanceKm: Double,
    val isOpen: Boolean = true,
    val openingHours: String = "08:00 - 22:00",
    val phoneNumber: String = "+84 28 3822 0000",
    val isSaved: Boolean = false,
    val tags: List<String> = emptyList()
)
