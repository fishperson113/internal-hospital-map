package com.example.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.MapRepository
import com.example.hospital.domain.usecase.FindHospitalRouteUseCase
import com.example.hospital.domain.usecase.GetHospitalMapUseCase
import com.example.hospital.domain.usecase.SearchRoomsUseCase
import com.example.hospital.presentation.viewmodel.HospitalMapViewModel

class ViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HospitalMapViewModel::class.java) -> {
                HospitalMapViewModel(
                    getHospitalMapUseCase = appContainer.getHospitalMapUseCase,
                    searchRoomsUseCase = appContainer.searchRoomsUseCase,
                    findHospitalRouteUseCase = appContainer.findHospitalRouteUseCase
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
