package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MapBlueSecondary,
    onPrimary = Color.White,
    primaryContainer = MapBluePrimaryVariant,
    onPrimaryContainer = Color.White,
    secondary = MapTealAccent,
    background = MapBackgroundDark,
    surface = MapSurfaceDark,
    surfaceVariant = MapSurfaceVariantDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MapBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = MapTealAccent,
    background = MapBackgroundLight,
    surface = MapSurfaceLight,
    surfaceVariant = MapSurfaceVariantLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color disabled by default so the map keeps its fixed wayfinding palette
  // (route blue / start green / destination red must not change with the wallpaper).
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
