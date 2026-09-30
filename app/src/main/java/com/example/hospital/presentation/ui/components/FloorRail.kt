package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Floor
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkSubtle

/**
 * Vertical floor picker on the right edge, like elevator buttons: 3D ("Tất cả") on top,
 * then floors from highest to lowest. A small blue dot marks floors the current route uses.
 * Replaces the old horizontal FloorSelectorRow, whose tabs got truncated on narrow phones.
 */
@Composable
fun FloorRail(
    floors: List<Floor>,
    selectedFloorLevel: Int?,
    routeFloorLevels: Set<Int>,
    language: AppLanguage,
    onSelectFloor: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = FloatingElevation,
        modifier = modifier.testTag("floor_rail")
    ) {
        Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RailButton(
                isSelected = selectedFloorLevel == null,
                description = HospitalLocalization.t(language, "Tất cả các tầng (3D)", "All floors (3D)"),
                testTag = "floor_rail_all",
                onClick = { onSelectFloor(null) }
            ) { fg ->
                Icon(Icons.Default.ViewInAr, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            }
            floors.sortedByDescending { it.level }.forEach { floor ->
                RailButton(
                    isSelected = selectedFloorLevel == floor.level,
                    isOnRoute = floor.level in routeFloorLevels,
                    isDimmed = floor.isTodo,
                    description = HospitalLocalization.getFloorLabel(floor.level, language),
                    testTag = "floor_rail_${floor.level}",
                    onClick = { onSelectFloor(floor.level) }
                ) { fg ->
                    Text("${floor.level}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = fg)
                }
            }
        }
    }
}

@Composable
private fun RailButton(
    isSelected: Boolean,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    isOnRoute: Boolean = false,
    isDimmed: Boolean = false,
    content: @Composable (Color) -> Unit
) {
    val fg = when {
        isSelected -> Color.White
        isDimmed -> MapInkSubtle
        else -> MapInk
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MapBluePrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                selected = isSelected
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content(fg)
        if (isOnRoute) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else MapBluePrimary)
            )
        }
    }
}
