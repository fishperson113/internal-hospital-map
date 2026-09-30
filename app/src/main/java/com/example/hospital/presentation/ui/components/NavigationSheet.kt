package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/** Bottom sheet during navigation: progress, remaining time, previous / next step, exit. */
@Composable
fun NavigationSheet(
    steps: List<RouteStepUi>,
    stepIndex: Int,
    language: AppLanguage,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    val isLast = stepIndex >= steps.lastIndex
    MapBottomSheetContainer(modifier = modifier.testTag("navigation_sheet")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    l.t(language, "Bước ${stepIndex + 1}/${steps.size}", "Step ${stepIndex + 1} of ${steps.size}"),
                    fontSize = 13.sp, color = MapInkMuted
                )
                val min = RouteStepFormatter.remainingMinutes(steps, stepIndex)
                Text(
                    l.t(language, "Còn ~$min phút", "~$min min left"),
                    fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MapGreenSuccess
                )
            }
            TextButton(
                onClick = onExit,
                modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(MapRedContainer)
            ) {
                Text(l.t(language, "Thoát", "Exit"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MapRedOnContainer)
            }
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
    }
}
