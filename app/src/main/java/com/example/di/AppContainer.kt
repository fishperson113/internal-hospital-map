package com.example.di

import com.example.data.remote.MapApiService
import com.example.data.repository.MapRepository
import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.domain.pathfinding.PathfindingStrategy
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase

/**
 * Dependency Injection Container interface.
 * Defines dependencies available to the entire application.
 */
interface AppContainer {
    val mapApiService: MapApiService
    val mapRepository: MapRepository

    // Hospital Indoor Navigation Architecture
    val hospitalMapRepository: HospitalMapRepository
    val pathfindingStrategy: PathfindingStrategy
    val getHospitalMapUseCase: GetHospitalMapUseCase
    val searchRoomsUseCase: SearchRoomsUseCase
    val findHospitalRouteUseCase: FindHospitalRouteUseCase
}
