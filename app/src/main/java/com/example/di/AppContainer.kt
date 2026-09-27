package com.example.di

import com.example.data.remote.MapApiService
import com.example.data.repository.MapRepository

/**
 * Dependency Injection Container interface.
 * Defines dependencies available to the entire application.
 */
interface AppContainer {
    val mapApiService: MapApiService
    val mapRepository: MapRepository
}
