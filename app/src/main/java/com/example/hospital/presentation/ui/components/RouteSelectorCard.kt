package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.state.TransferPreference
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapBlueContainer
import com.example.ui.theme.MapBlueContainerStroke
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapGreenContainer
import com.example.ui.theme.MapGreenSuccess
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted
import com.example.ui.theme.MapInkSubtle
import com.example.ui.theme.MapRedAlert

/**
 * Floating From/To card (stacked vertically so long Vietnamese room names fit on a phone).
 * Right column: close route (top) and swap From/To (bottom).
 */
@Composable
fun RouteSelectorCard(
    startRoom: Room?,
    destRoom: Room?,
    language: AppLanguage,
    onStartRoomClick: () -> Unit,
    onDestRoomClick: () -> Unit,
    onSwapClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = FloatingCardShape,
        color = Color.White,
        shadowElevation = FloatingElevation,
        modifier = modifier.fillMaxWidth().testTag("route_selector_card")
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
        ) {
            // From ● ⋮ 📍 To
            Column(
                modifier = Modifier.width(16.dp).fillMaxHeight().padding(top = 19.dp, bottom = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(MapGreenContainer),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(10.dp).clip(CircleShape).background(MapGreenSuccess)) }
                Canvas(modifier = Modifier.weight(1f).width(2.dp).padding(vertical = 3.dp)) {
                    drawLine(
                        color = Color(0xFFBDC1C6),
                        start = Offset(size.width / 2f, 0f), end = Offset(size.width / 2f, size.height),
                        strokeWidth = size.width, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                    )
                }
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MapRedAlert, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                RouteField(
                    label = "${HospitalLocalization.getFromLabel(language)} · ${startRoom?.let { HospitalLocalization.getFloorLabel(it.floorLevel, language) } ?: ""}",
                    value = startRoom?.let { HospitalLocalization.roomLabel(it, language) } ?: HospitalLocalization.getSelectStartPrompt(language),
                    isPlaceholder = startRoom == null,
                    onClick = onStartRoomClick,
                    testTag = "route_from_field"
                )
                HorizontalDivider(color = Color(0xFFECEFF1), modifier = Modifier.padding(horizontal = 8.dp))
                RouteField(
                    label = "${HospitalLocalization.getToLabel(language)} · ${destRoom?.let { HospitalLocalization.getFloorLabel(it.floorLevel, language) } ?: ""}",
                    value = destRoom?.let { HospitalLocalization.roomLabel(it, language) } ?: HospitalLocalization.getSelectDestPrompt(language),
                    isPlaceholder = destRoom == null,
                    onClick = onDestRoomClick,
                    testTag = "route_to_field"
                )
            }
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCloseClick, modifier = Modifier.size(44.dp).testTag("clear_route_button")) {
                    Icon(Icons.Default.Close, contentDescription = HospitalLocalization.getCloseButtonLabel(language), tint = MapInkMuted)
                }
                IconButton(onClick = onSwapClick, modifier = Modifier.size(44.dp).testTag("swap_rooms_button")) {
                    Icon(Icons.Default.SwapVert, contentDescription = HospitalLocalization.t(language, "Đổi chiều", "Swap"), tint = MapInk)
                }
            }
        }
    }
}

@Composable
private fun RouteField(label: String, value: String, isPlaceholder: Boolean, onClick: () -> Unit, testTag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, fontSize = 11.sp, color = MapInkMuted, maxLines = 1)
        Text(
            value, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = if (isPlaceholder) MapInkSubtle else MapInk,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Nhanh nhất / Chỉ thang máy / Chỉ thang bộ" — which shafts the route may use. */
@Composable
fun TransferPreferenceChips(
    selected: TransferPreference,
    language: AppLanguage,
    onSelect: (TransferPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TransferPreference.entries.forEach { pref ->
            val (label, icon) = when (pref) {
                TransferPreference.FASTEST -> HospitalLocalization.t(language, "Nhanh nhất", "Fastest") to Icons.Default.Bolt
                TransferPreference.ELEVATOR_ONLY -> HospitalLocalization.t(language, "Chỉ thang máy", "Elevator only") to Icons.Default.Accessible
                TransferPreference.STAIRS_ONLY -> HospitalLocalization.t(language, "Chỉ thang bộ", "Stairs only") to Icons.Default.Stairs
            }
            val on = pref == selected
            val fg = if (on) MapBluePrimary else Color(0xFF3C4043)
            Surface(
                onClick = { onSelect(pref) },
                shape = RoundedCornerShape(16.dp),
                color = if (on) MapBlueContainer else Color.White,
                border = BorderStroke(1.dp, if (on) MapBlueContainerStroke else Color.White),
                shadowElevation = 1.dp,
                modifier = Modifier.testTag("transfer_pref_${pref.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.height(32.dp).padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg)
                }
            }
        }
    }
}
