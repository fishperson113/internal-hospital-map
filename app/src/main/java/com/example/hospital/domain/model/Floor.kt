package com.example.hospital.domain.model

data class Floor(
    val id: String,
    val hospitalId: String,
    val level: Int,                // e.g. 1, 2, 3, 4, 5
    val name: String,             // e.g. "Tầng 5"
    val subtitle: String = "",    // e.g. "Khoa Ngoại & Chấn thương chỉnh hình"
    val mapWidth: Float = 1000f,  // Map canvas coordinate bounds
    val mapHeight: Float = 600f,
    val hasCourtyard: Boolean = true, // Giếng trời ở giữa
    val lastVerifiedAt: Long = System.currentTimeMillis()
)
