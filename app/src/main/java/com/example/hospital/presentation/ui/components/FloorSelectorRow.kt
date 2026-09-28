package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Floor
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization

@Composable
fun FloorSelectorRow(
    floors: List<Floor>,
    selectedFloorLevel: Int?, // null for "Tất cả"
    language: AppLanguage = AppLanguage.VIETNAMESE,
    onSelectFloor: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Tất cả / All" chip (Multi-floor stacked view)
        val isAllSelected = selectedFloorLevel == null
        FloorChip(
            label = HospitalLocalization.getAllFloorsLabel(language),
            isSelected = isAllSelected,
            onClick = { onSelectFloor(null) },
            testTag = "floor_chip_all"
        )

        // Floor chips 1..5
        floors.forEach { floor ->
            val isSelected = selectedFloorLevel == floor.level
            FloorChip(
                label = HospitalLocalization.getFloorLabel(floor.level, language),
                isSelected = isSelected,
                onClick = { onSelectFloor(floor.level) },
                testTag = "floor_chip_${floor.level}"
            )
        }
    }
}

@Composable
private fun FloorChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor = if (isSelected) Color(0xFF0077D7) else Color(0xFFF1F5F9)
    val textColor = if (isSelected) Color.White else Color(0xFF475569)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
