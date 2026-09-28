package com.example.hospital.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
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
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.RouteStep
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization

@Composable
fun DetailedRouteCard(
    route: HospitalRoute?,
    language: AppLanguage,
    isExpanded: Boolean,
    isNavigating: Boolean,
    onToggleExpand: () -> Unit,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("detailed_route_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title & Estimated Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF0077D7),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = HospitalLocalization.getDetailedRouteLabel(language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = HospitalLocalization.getEstimatedTimeLabel(route?.estimatedDurationMinutes ?: 1, language),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand/Collapse",
                        tint = Color(0xFF64748B)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))

                    val steps = route?.steps ?: emptyList()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        steps.forEachIndexed { index, step ->
                            StepItemView(step = step, language = language)
                            if (index < steps.size - 1) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Action Button: Bắt đầu chỉ đường / Dừng chỉ đường
            Button(
                onClick = {
                    if (isNavigating) onStopNavigation() else onStartNavigation()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_navigation_action"),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNavigating) Color(0xFFD32F2F) else Color(0xFF0077D7)
                )
            ) {
                Icon(
                    imageVector = if (isNavigating) Icons.Default.Close else Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = HospitalLocalization.getStartNavLabel(isNavigating, language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StepItemView(step: RouteStep, language: AppLanguage) {
    val stepColor = when {
        step.stepNumber == 1 -> Color(0xFF2E7D32)
        step.iconType == StepIconType.DESTINATION -> Color(0xFFD32F2F)
        else -> Color(0xFF0077D7)
    }

    val stepIcon = when (step.iconType) {
        StepIconType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        StepIconType.ELEVATOR -> Icons.Default.Elevator
        StepIconType.STAIRS -> Icons.Default.Stairs
        StepIconType.DESTINATION -> Icons.Default.LocationOn
    }

    Row(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 180.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(stepColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${step.stepNumber}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column {
            val title = if (step.isCrossFloorStep) {
                "${HospitalLocalization.getFloorLabel(step.floorLevel, language)} -> ${HospitalLocalization.getFloorLabel(step.targetFloorLevel, language)}"
            } else {
                HospitalLocalization.getFloorLabel(step.floorLevel, language)
            }

            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Text(
                text = step.instruction,
                fontSize = 10.sp,
                color = Color(0xFF475569),
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Icon(
                imageVector = stepIcon,
                contentDescription = null,
                tint = stepColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
