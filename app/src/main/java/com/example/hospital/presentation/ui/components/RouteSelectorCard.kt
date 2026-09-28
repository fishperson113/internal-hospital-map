package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization

@Composable
fun RouteSelectorCard(
    startRoom: Room?,
    destRoom: Room?,
    language: AppLanguage,
    onStartRoomClick: () -> Unit,
    onDestRoomClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("route_selector_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start Room Column
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onStartRoomClick() }
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = HospitalLocalization.getFromLabel(language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32)
                    )
                    val startName = startRoom?.let { HospitalLocalization.getRoomDisplayName(it.id, it.name, language) }
                        ?: if (language == AppLanguage.VIETNAMESE) "Chọn điểm đi" else "Select Start"
                    Text(
                        text = startName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val startDesc = startRoom?.let { HospitalLocalization.getRoomDescription(it.id, it.description, language) } ?: ""
                    Text(
                        text = "${HospitalLocalization.getFloorLabel(startRoom?.floorLevel ?: 1, language)} • $startDesc",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Arrow / Swap Button
            IconButton(
                onClick = onSwapClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("swap_rooms_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Swap",
                    tint = Color(0xFF0077D7),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Destination Room Column
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDestRoomClick() }
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = HospitalLocalization.getToLabel(language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD32F2F)
                    )
                    val destName = destRoom?.let { HospitalLocalization.getRoomDisplayName(it.id, it.name, language) }
                        ?: if (language == AppLanguage.VIETNAMESE) "Chọn điểm đến" else "Select Dest"
                    Text(
                        text = destName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val destDesc = destRoom?.let { HospitalLocalization.getRoomDescription(it.id, it.description, language) } ?: ""
                    Text(
                        text = "${HospitalLocalization.getFloorLabel(destRoom?.floorLevel ?: 1, language)} • $destDesc",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
