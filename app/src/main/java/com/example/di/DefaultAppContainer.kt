package com.example.di

import com.example.data.remote.MapApiService
import com.example.data.repository.MapRepository
import com.example.data.repository.MapRepositoryImpl
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
class DefaultAppContainer : AppContainer {

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
}
