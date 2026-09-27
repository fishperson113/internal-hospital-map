package com.example.ui.screen.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.example.data.model.LatLng
import com.example.data.model.MapLayerType
import com.example.data.model.MapViewport
import com.example.data.model.Place
import com.example.data.model.PlaceCategory
import com.example.data.model.RouteInfo
import kotlin.math.hypot

@Composable
fun InteractiveMapCanvas(
    viewport: MapViewport,
    places: List<Place>,
    selectedPlace: Place?,
    activeRoute: RouteInfo?,
    userLocation: LatLng,
    onPlaceSelected: (Place) -> Unit,
    onPan: (deltaLat: Double, deltaLng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulse animation for user GPS location
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("interactive_map_canvas")
            .pointerInput(viewport.zoomLevel) {
                detectDragGestures { _, dragAmount ->
                    // Sensitivity scaling by zoom level
                    val scaleFactor = 0.000035 / (viewport.zoomLevel / 14f)
                    val deltaLat = dragAmount.y * scaleFactor
                    val deltaLng = -dragAmount.x * scaleFactor
                    onPan(deltaLat, deltaLng)
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Helper to convert lat/lng to canvas offset coordinates
        fun latLngToScreen(point: LatLng): Offset {
            val dLng = point.longitude - viewport.center.longitude
            val dLat = point.latitude - viewport.center.latitude
            val pixelsPerDegree = 4500f * (viewport.zoomLevel / 14f)
            val x = (width / 2f) + (dLng * pixelsPerDegree).toFloat()
            val y = (height / 2f) - (dLat * pixelsPerDegree).toFloat()
            return Offset(x, y)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(places, viewport) {
                    detectTapGestures { tapOffset ->
                        // Check if tap hit any place marker
                        var clickedPlace: Place? = null
                        for (place in places) {
                            val markerCenter = latLngToScreen(LatLng(place.latitude, place.longitude))
                            val hitRadius = 40f
                            val dist = hypot(tapOffset.x - markerCenter.x, tapOffset.y - markerCenter.y)
                            if (dist <= hitRadius) {
                                clickedPlace = place
                                break
                            }
                        }
                        if (clickedPlace != null) {
                            onPlaceSelected(clickedPlace)
                        }
                    }
                }
        ) {
            // 1. Draw Map Base Background
            drawMapBase(viewport.layerType)

            // 2. Draw Geographic Features (Rivers, Parks, Road Grid)
            drawGeography(viewport.layerType, width, height, viewport)

            // 3. Draw Route Polyline if Active
            if (activeRoute != null) {
                drawActiveRoute(activeRoute, ::latLngToScreen)
            }

            // 4. Draw Place Markers
            places.forEach { place ->
                val isSelected = place.id == selectedPlace?.id
                val screenPos = latLngToScreen(LatLng(place.latitude, place.longitude))
                // Only render if roughly within screen bounds
                if (screenPos.x in -100f..(width + 100f) && screenPos.y in -100f..(height + 100f)) {
                    drawPlaceMarker(
                        place = place,
                        center = screenPos,
                        isSelected = isSelected,
                        layerType = viewport.layerType
                    )
                }
            }

            // 5. Draw User GPS Location with Pulse
            val userPos = latLngToScreen(userLocation)
            drawUserLocation(userPos, pulseRadius, pulseAlpha)
        }
    }
}

private fun DrawScope.drawMapBase(layerType: MapLayerType) {
    val bgColor = when (layerType) {
        MapLayerType.STANDARD -> Color(0xFFF3F4F6)
        MapLayerType.SATELLITE -> Color(0xFF1B242C)
        MapLayerType.TRAFFIC -> Color(0xFFEFEFEF)
    }
    drawRect(color = bgColor)
}

private fun DrawScope.drawGeography(
    layerType: MapLayerType,
    width: Float,
    height: Float,
    viewport: MapViewport
) {
    // Water Color
    val waterColor = when (layerType) {
        MapLayerType.STANDARD -> Color(0xFFC7E2F8)
        MapLayerType.SATELLITE -> Color(0xFF0F1E2E)
        MapLayerType.TRAFFIC -> Color(0xFFB9D8F2)
    }
    // Park Green Color
    val parkColor = when (layerType) {
        MapLayerType.STANDARD -> Color(0xFFDCF0DF)
        MapLayerType.SATELLITE -> Color(0xFF17291B)
        MapLayerType.TRAFFIC -> Color(0xFFE2F3E4)
    }
    // Road Color
    val roadMainColor = when (layerType) {
        MapLayerType.STANDARD -> Color(0xFFFFFFFF)
        MapLayerType.SATELLITE -> Color(0xFF3B4856)
        MapLayerType.TRAFFIC -> Color(0xFFFFFFFF)
    }
    val roadSecondaryColor = when (layerType) {
        MapLayerType.STANDARD -> Color(0xFFE7E9ED)
        MapLayerType.SATELLITE -> Color(0xFF2C3742)
        MapLayerType.TRAFFIC -> Color(0xFFE0E3E8)
    }

    // 1. Draw River (Saigon River S-Curve effect)
    val riverPath = Path().apply {
        moveTo(width * 0.75f, 0f)
        cubicTo(
            width * 0.9f, height * 0.25f,
            width * 0.65f, height * 0.6f,
            width * 0.85f, height
        )
    }
    drawPath(
        path = riverPath,
        color = waterColor,
        style = Stroke(width = 65f * (viewport.zoomLevel / 14f), cap = StrokeCap.Round)
    )

    // 2. Draw Parks (Tao Dan Park, 23/9 Park areas)
    drawRoundRect(
        color = parkColor,
        topLeft = Offset(width * 0.12f, height * 0.35f),
        size = Size(width * 0.28f, height * 0.18f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = parkColor,
        topLeft = Offset(width * 0.20f, height * 0.68f),
        size = Size(width * 0.45f, height * 0.08f),
        cornerRadius = CornerRadius(20f, 20f)
    )

    // 3. Draw Road Grid (City avenues & streets)
    val gridStep = 80f * (viewport.zoomLevel / 14f)
    // Horizontal streets
    var y = 40f
    while (y < height) {
        drawLine(
            color = roadSecondaryColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 6f
        )
        y += gridStep
    }
    // Vertical avenues
    var x = 30f
    while (x < width) {
        drawLine(
            color = roadSecondaryColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 6f
        )
        x += gridStep
    }

    // Major Arterial Highways (Thick white/yellow roads)
    drawLine(
        color = roadMainColor,
        start = Offset(0f, height * 0.48f),
        end = Offset(width, height * 0.52f),
        strokeWidth = 14f
    )
    drawLine(
        color = roadMainColor,
        start = Offset(width * 0.45f, 0f),
        end = Offset(width * 0.40f, height),
        strokeWidth = 14f
    )
    drawLine(
        color = roadMainColor,
        start = Offset(0f, height * 0.2f),
        end = Offset(width * 0.75f, height * 0.95f),
        strokeWidth = 12f
    )

    // If Traffic Layer is active, draw live congestion overlays
    if (layerType == MapLayerType.TRAFFIC) {
        // Green fast flow
        drawLine(
            color = Color(0xFF4CAF50),
            start = Offset(0f, height * 0.48f),
            end = Offset(width * 0.45f, height * 0.50f),
            strokeWidth = 6f
        )
        // Amber moderate
        drawLine(
            color = Color(0xFFFF9800),
            start = Offset(width * 0.45f, height * 0.50f),
            end = Offset(width * 0.70f, height * 0.51f),
            strokeWidth = 6f
        )
        // Red heavy traffic
        drawLine(
            color = Color(0xFFE53935),
            start = Offset(width * 0.45f, height * 0.15f),
            end = Offset(width * 0.43f, height * 0.45f),
            strokeWidth = 6f
        )
    }
}

private fun DrawScope.drawActiveRoute(
    route: RouteInfo,
    latLngToScreen: (LatLng) -> Offset
) {
    if (route.waypoints.size < 2) return

    val routePath = Path()
    val startOffset = latLngToScreen(route.waypoints.first())
    routePath.moveTo(startOffset.x, startOffset.y)

    for (i in 1 until route.waypoints.size) {
        val pt = latLngToScreen(route.waypoints[i])
        routePath.lineTo(pt.x, pt.y)
    }

    // Outer glow / shadow
    drawPath(
        path = routePath,
        color = Color(0x330052CC),
        style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Main route line (Royal Blue with gradient effect)
    drawPath(
        path = routePath,
        color = Color(0xFF2979FF),
        style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Destination Pin Flag
    val destOffset = latLngToScreen(route.destination)
    drawCircle(color = Color(0xFF1E88E5), radius = 10f, center = destOffset)
    drawCircle(color = Color.White, radius = 5f, center = destOffset)
}

private fun DrawScope.drawPlaceMarker(
    place: Place,
    center: Offset,
    isSelected: Boolean,
    layerType: MapLayerType
) {
    val categoryColor = when (place.category) {
        PlaceCategory.RESTAURANT -> Color(0xFFE53935) // Red
        PlaceCategory.CAFE -> Color(0xFF8D6E63)       // Coffee Brown
        PlaceCategory.GAS -> Color(0xFFFF9800)        // Orange
        PlaceCategory.HOTEL -> Color(0xFF7E57C2)      // Purple
        PlaceCategory.ATTRACTION -> Color(0xFF00897B) // Teal
        PlaceCategory.ALL -> Color(0xFF1976D2)        // Blue
    }

    val baseRadius = if (isSelected) 22f else 16f

    // Marker drop shadow
    drawCircle(
        color = Color(0x33000000),
        radius = baseRadius + 4f,
        center = Offset(center.x, center.y + 3f)
    )

    // Outer white halo
    drawCircle(
        color = Color.White,
        radius = baseRadius + 2f,
        center = center
    )

    // Main Colored Marker Body
    drawCircle(
        color = categoryColor,
        radius = baseRadius,
        center = center
    )

    // Inner White Dot / Icon indicator
    drawCircle(
        color = Color.White,
        radius = baseRadius * 0.42f,
        center = center
    )

    // If selected, draw animated pulse ring around it
    if (isSelected) {
        drawCircle(
            color = categoryColor.copy(alpha = 0.35f),
            radius = baseRadius + 14f,
            center = center,
            style = Stroke(width = 3f)
        )
    }
}

private fun DrawScope.drawUserLocation(
    center: Offset,
    pulseRadius: Float,
    pulseAlpha: Float
) {
    // 1. Animated radar wave
    drawCircle(
        color = Color(0xFF007AFF).copy(alpha = pulseAlpha),
        radius = pulseRadius,
        center = center
    )

    // 2. Accuracy circle shadow
    drawCircle(
        color = Color(0x33007AFF),
        radius = 18f,
        center = center
    )

    // 3. Crisp white border
    drawCircle(
        color = Color.White,
        radius = 10f,
        center = center
    )

    // 4. Solid Blue Location Dot
    drawCircle(
        color = Color(0xFF007AFF),
        radius = 7.5f,
        center = center
    )
}
