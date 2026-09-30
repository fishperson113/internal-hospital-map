package com.example.di

import com.example.data.remote.MapApiService
import com.example.data.repository.MapRepository
import com.example.data.repository.MapRepositoryImpl
import com.example.hospital.data.datasource.HospitalMapDataSource
import com.example.hospital.data.datasource.MockHospitalMapDataSource
import com.example.hospital.data.repository.HospitalMapRepository
import com.example.hospital.data.repository.HospitalMapRepositoryImpl
import com.example.hospital.domain.pathfinding.DijkstraPathfindingStrategy
import com.example.hospital.domain.pathfinding.PathfindingStrategy
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Concrete Dependency Injection Container.
 * Manages network clients, services, and repositories as singletons.
 */
class DefaultAppContainer(
    private val context: android.content.Context? = null
) : AppContainer {

    private val defaultBaseUrl = "https://api.mapflow.internal/v1/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(defaultBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    override val mapApiService: MapApiService by lazy {
        retrofit.create(MapApiService::class.java)
    }

    override val mapRepository: MapRepository by lazy {
        MapRepositoryImpl(apiService = mapApiService)
    }

    // Hospital Indoor Navigation Architecture Singletons
    private val hospitalMapDataSource: HospitalMapDataSource by lazy {
        MockHospitalMapDataSource()
    }

    override val hospitalMapRepository: HospitalMapRepository by lazy {
        HospitalMapRepositoryImpl(dataSource = hospitalMapDataSource)
    }

    override val pathfindingStrategy: PathfindingStrategy by lazy {
        DijkstraPathfindingStrategy()
    }

    override val getHospitalMapUseCase: GetHospitalMapUseCase by lazy {
        GetHospitalMapUseCase(repository = hospitalMapRepository)
    }

    override val searchRoomsUseCase: SearchRoomsUseCase by lazy {
        SearchRoomsUseCase(repository = hospitalMapRepository)
    }

    override val findHospitalRouteUseCase: FindHospitalRouteUseCase by lazy {
        FindHospitalRouteUseCase(
            repository = hospitalMapRepository,
            pathfindingStrategy = pathfindingStrategy
        )
    }

    override val sensorManager: com.example.hospital.sensor.HospitalSensorManager? by lazy {
        context?.let { com.example.hospital.sensor.HospitalSensorManager(it) }
    }
}
