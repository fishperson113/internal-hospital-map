package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tour
import androidx.compose.ui.graphics.vector.ImageVector

enum class PlaceCategory(val id: String, val displayName: String) {
    ALL("all", "Tất cả"),
    RESTAURANT("restaurant", "Quán ăn"),
    CAFE("cafe", "Cà phê"),
    GAS("gas", "Cây xăng"),
    HOTEL("hotel", "Khách sạn"),
    ATTRACTION("attraction", "Điểm tham quan");

    fun getIcon(): ImageVector {
        return when (this) {
            ALL -> Icons.Default.Place
            RESTAURANT -> Icons.Default.Restaurant
            CAFE -> Icons.Default.Coffee
            GAS -> Icons.Default.LocalGasStation
            HOTEL -> Icons.Default.Apartment
            ATTRACTION -> Icons.Default.Tour
        }
    }

    companion object {
        fun fromId(id: String): PlaceCategory {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: ALL
        }
    }
}
