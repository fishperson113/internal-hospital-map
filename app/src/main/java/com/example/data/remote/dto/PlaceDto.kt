package com.example.data.remote.dto

import com.example.data.model.Place
import com.example.data.model.PlaceCategory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "category") val category: String?,
    @Json(name = "rating") val rating: Double? = 4.5,
    @Json(name = "review_count") val reviewCount: Int? = 0,
    @Json(name = "address") val address: String? = "",
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "description") val description: String? = "",
    @Json(name = "distance_km") val distanceKm: Double? = 0.0,
    @Json(name = "is_open") val isOpen: Boolean? = true,
    @Json(name = "opening_hours") val openingHours: String? = "08:00 - 22:00",
    @Json(name = "phone_number") val phoneNumber: String? = "",
    @Json(name = "tags") val tags: List<String>? = emptyList()
) {
    fun toDomain(): Place {
        return Place(
            id = id,
            name = name,
            category = category?.let { PlaceCategory.fromId(it) } ?: PlaceCategory.ALL,
            rating = rating?.toFloat() ?: 4.5f,
            reviewCount = reviewCount ?: 0,
            address = address ?: "",
            latitude = latitude,
            longitude = longitude,
            description = description ?: "",
            distanceKm = distanceKm ?: 0.0,
            isOpen = isOpen ?: true,
            openingHours = openingHours ?: "08:00 - 22:00",
            phoneNumber = phoneNumber ?: "",
            isSaved = false,
            tags = tags ?: emptyList()
        )
    }
}
