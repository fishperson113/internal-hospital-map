package com.example.hospital.domain.model

data class Hospital(
    val id: String,
    val name: String,
    val code: String,
    val address: String,
    val totalFloors: Int,
    val description: String = "",
    val lastVerifiedAt: Long = System.currentTimeMillis()
)
