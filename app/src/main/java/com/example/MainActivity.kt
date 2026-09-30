package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.di.ViewModelFactory
import com.example.hospital.presentation.viewmodel.HospitalMapViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val hospitalMapViewModel: HospitalMapViewModel by viewModels {
        val app = application as MapApplication
        ViewModelFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Render latest indoor navigation with polygon floors & isometric 3D view
        setContent {
            MyApplicationTheme {
                com.example.hospital.presentation.ui.HospitalMapScreen(viewModel = hospitalMapViewModel)
            }
        }
    }
}

