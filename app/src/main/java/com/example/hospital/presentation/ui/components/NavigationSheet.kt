package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.presentation.state.RouteStepUi
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.hospital.presentation.util.RouteStepFormatter
import com.example.ui.theme.MapBlueOnPrimarySoft
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapDivider
import com.example.ui.theme.MapGreenSuccess
import com.example.ui.theme.MapInkMuted
import com.example.ui.theme.MapRedContainer
import com.example.ui.theme.MapRedOnContainer

/** Top banner during turn-by-turn navigation: the current instruction, large. */
@Composable
fun NavigationBanner(step: RouteStepUi?, modifier: Modifier = Modifier) {
    if (step == null) return
    val v = stepVisual(step)
    Surface(
        shape = FloatingCardShape,
        color = MapBluePrimary,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().testTag("navigation_banner")
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(v.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(step.title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Color.White, lineHeight = 24.sp)
                Text(step.subtitle, fontSize = 14.sp, color = MapBlueOnPrimarySoft)
            }
        }
    }
}

/**
 * Bottom sheet during navigation. Collapsed (default, tap the handle to toggle): remaining time,
 * exit, progress, previous / next. Expanded adds sensors, From / To "Đổi" and "Tôi bị lạc".
 * An active help request stays visible even when collapsed.
 */
@Composable
fun NavigationSheet(
    steps: List<RouteStepUi>,
    stepIndex: Int,
    language: AppLanguage,
    telemetryText: String = "",
    diagnostics: String = "",           // shown when the telemetry badge is tapped (on-site tuning)
    floorChangeSuggested: Boolean = false,
    offRoute: Boolean = false,
    walkingBack: Boolean = false,
    hasArrived: Boolean = false,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    helpStatus: String? = null,
    onAskHelp: (() -> Unit)? = null,
    ticket: String = "",
    onTicketChange: ((String) -> Unit)? = null, // queue number for the CRM (web: #ticket-input)
    // Web navHtml: "Từ X Đổi · Đến Y Đổi" — changing a place re-routes and keeps directions on
    startLabel: String = "",
    destLabel: String = "",
    onChangeStart: () -> Unit = {},
    onChangeDest: () -> Unit = {},
    voiceOn: Boolean = true,            // speaker button: voice guidance + vibration
    onToggleVoice: () -> Unit = {},
    onOpenAr: (() -> Unit)? = null,     // camera button: arrow directions on the camera (ArOverlay)
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    val isLast = stepIndex >= steps.lastIndex
    var showDiag by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    MapBottomSheetContainer(modifier = modifier.testTag("navigation_sheet"), onHandleClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (hasArrived) l.t(language, "🎉 Đã tới đích!", "🎉 Arrived at destination!")
                    else l.t(language, "Bước ${stepIndex + 1}/${steps.size}", "Step ${stepIndex + 1} of ${steps.size}"),
                    fontSize = 13.sp, color = if (hasArrived) MapGreenSuccess else MapInkMuted,
                    fontWeight = if (hasArrived) FontWeight.Bold else FontWeight.Normal
                )
                val min = RouteStepFormatter.remainingMinutes(steps, stepIndex)
                Text(
                    if (hasArrived) l.t(language, "Hoàn tất lộ trình", "Route completed")
                    else l.t(language, "Còn ~$min phút", "~$min min left"),
                    fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MapGreenSuccess
                )
            }
            OutlinedIconButton(
                onClick = onToggleVoice,
                modifier = Modifier.padding(end = 8.dp).size(40.dp).testTag("btn_voice"),
                border = BorderStroke(1.dp, MapDivider)
            ) {
                Icon(
                    if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (voiceOn) l.t(language, "Tắt hướng dẫn bằng giọng nói", "Turn voice guidance off")
                    else l.t(language, "Bật hướng dẫn bằng giọng nói", "Turn voice guidance on"),
                    tint = if (voiceOn) MapBluePrimary else MapInkMuted
                )
            }
            if (onOpenAr != null) OutlinedIconButton(
                onClick = onOpenAr,
                modifier = Modifier.padding(end = 8.dp).size(40.dp).testTag("btn_ar"),
                border = BorderStroke(1.dp, MapDivider)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = l.t(language, "Chỉ đường bằng camera", "Camera directions"),
                    tint = MapBluePrimary
                )
            }
            TextButton(
                onClick = onExit,
                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(MapRedContainer)
            ) {
                Text(l.t(language, "Thoát", "Exit"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MapRedOnContainer)
            }
        }

        if (offRoute) {
            Text(
                l.t(language, "Bạn đang đi khác tuyến, đang tìm đường mới từ chỗ bạn. Chạm vào chỗ bạn đứng trên bản đồ nếu chấm bị lệch.",
                    "You left the route; finding a new one from where you are. Tap where you stand on the map if the dot is off."),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MapRedOnContainer, lineHeight = 18.sp,
                modifier = Modifier.testTag("off_route_hint")
            )
        }

        if (walkingBack) {
            Text(
                l.t(language, "Bạn đang đi ngược chiều. Quay lại theo đường màu xanh để tới nơi.",
                    "You are walking the wrong way. Turn around and follow the blue line."),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MapRedOnContainer, lineHeight = 18.sp,
                modifier = Modifier.testTag("walking_back_hint")
            )
        }

        if (floorChangeSuggested && !isLast) {
            Text(
                l.t(language, "Có vẻ bạn đã đổi tầng. Bấm “Bước tiếp theo” khi đã tới nơi.", "Looks like you changed floor. Tap “Next step” when you are there."),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MapBluePrimary
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= stepIndex) MapBluePrimary else Color(0xFFE8EAED))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = onPrevious,
                enabled = stepIndex > 0,
                modifier = Modifier.size(48.dp),
                border = BorderStroke(1.dp, MapDivider)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = l.t(language, "Bước trước", "Previous step"),
                    tint = if (stepIndex > 0) MapBluePrimary else Color(0xFFBDC1C6)
                )
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(48.dp).testTag("btn_next_step"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MapBluePrimary)
            ) {
                Text(
                    if (isLast) l.t(language, "Đã đến nơi", "Arrived") else l.t(language, "Bước tiếp theo", "Next step"),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Icon(
                    if (isLast) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
            }
        }

        if (expanded) {
            PlaceRow(l.t(language, "Từ", "From"), startLabel, language, onChangeStart)
            PlaceRow(l.t(language, "Đến", "To"), destLabel, language, onChangeDest)
            if (telemetryText.isNotBlank()) {
                // Sensor line; tap for the raw counters (on-site tuning)
                Text(
                    telemetryText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569),
                    modifier = Modifier.clickable { showDiag = !showDiag }
                )
                if (showDiag && diagnostics.isNotBlank()) {
                    Text(diagnostics, fontSize = 11.sp, color = Color(0xFF475569), lineHeight = 15.sp, modifier = Modifier.testTag("sensor_diagnostics"))
                }
            }
        }

        val helpActive = helpStatus != null && helpStatus != "done" && helpStatus != "resolved"
        if (onAskHelp != null && (expanded || helpActive)) {
            HelpAssistanceCard(
                helpStatus = helpStatus,
                language = language,
                onAskHelp = onAskHelp,
                ticket = ticket,
                onTicketChange = onTicketChange,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
