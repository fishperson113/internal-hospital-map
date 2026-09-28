package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Search
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
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization

@Composable
fun HospitalTopBar(
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hospital Logo Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0077D7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocalHospital,
                contentDescription = "Hospital Logo",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Titles (Localized cleanly)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = HospitalLocalization.getHospitalName(language),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = HospitalLocalization.getSubtitle(language),
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Language toggle button (VI / EN)
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { onToggleLanguage() }
                .testTag("btn_toggle_language"),
            color = Color(0xFFE2E8F0)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Change Language",
                    tint = Color(0xFF0077D7),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (language == AppLanguage.VIETNAMESE) "VI" else "EN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0077D7)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Search pill button
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onSearchClick() }
                .testTag("search_room_button"),
            color = Color(0xFFF1F5F9),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (language == AppLanguage.VIETNAMESE) "Tìm..." else "Search...",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}
