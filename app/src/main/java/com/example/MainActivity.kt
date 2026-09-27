package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.di.ViewModelFactory
import com.example.ui.screen.MapScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MapViewModel

class MainActivity : ComponentActivity() {

    private val mapViewModel: MapViewModel by viewModels {
        val app = application as MapApplication
        ViewModelFactory(app.container.mapRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MapScreen(viewModel = mapViewModel)
            }
        }
    }
}

