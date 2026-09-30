package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapBlueContainer
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapChipSurface
import com.example.ui.theme.MapDivider
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted

/**
 * Full-screen search (replaces the old ModalBottomSheet, which left too little room for
 * results once the keyboard opened on a phone). Keyboard opens immediately.
 */
@Composable
fun RoomSearchDialog(
    isSelectingStart: Boolean,
    query: String,
    rooms: List<Room>,
    language: AppLanguage = AppLanguage.VIETNAMESE,
    onQueryChange: (String) -> Unit,
    onSelectRoom: (Room) -> Unit,
    onDismiss: () -> Unit
) {
    val l = HospitalLocalization
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(modifier = Modifier.fillMaxSize().testTag("room_search_screen"), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = l.getCloseButtonLabel(language), tint = MapInk)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MapChipSurface)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = MapInk),
                        cursorBrush = SolidColor(MapBluePrimary),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("search_room_input"),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        if (isSelectingStart) l.t(language, "Bạn đang ở đâu?", "Where are you now?")
                                        else l.t(language, "Bạn muốn đến đâu?", "Where do you want to go?"),
                                        fontSize = 16.sp, color = MapInkMuted
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = l.getClearSearchLabel(language), tint = MapInkMuted)
                        }
                    }
                }
            }

            // Quick filters
            val quick = listOf(
                Triple(l.t(language, "Thang máy", "Elevator"), Icons.Default.Elevator, "thang may"),
                Triple(l.t(language, "Vệ sinh", "Toilet"), Icons.Default.Wc, "ve sinh"),
                Triple(l.t(language, "Khám", "Clinic"), Icons.Default.MedicalServices, "kham"),
                Triple(l.t(language, "Quầy", "Counter"), Icons.Default.SupportAgent, "quay"),
                Triple(l.t(language, "Cách ly", "Isolation"), Icons.Default.Shield, "cach ly")
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quick.forEach { (label, icon, term) ->
                    val active = query.equals(term, ignoreCase = true)
                    Surface(
                        onClick = { onQueryChange(term) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) MapBlueContainer else Color.White,
                        border = BorderStroke(1.dp, MapDivider)
                    ) {
                        Row(
                            modifier = Modifier.height(32.dp).padding(start = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = MapInkMuted, modifier = Modifier.size(18.dp))
                            Text(label, fontSize = 13.sp, color = Color(0xFF3C4043))
                        }
                    }
                }
            }

            Text(
                text = if (query.isBlank()) l.t(language, "TẤT CẢ PHÒNG & KHOA", "ALL ROOMS & DEPARTMENTS")
                else l.t(language, "${rooms.size} KẾT QUẢ", "${rooms.size} RESULTS"),
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MapInkMuted, letterSpacing = 0.3.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (rooms.isEmpty()) {
                Text(
                    l.t(language, "Không tìm thấy phòng phù hợp.", "No matching rooms."),
                    fontSize = 14.sp, color = MapInkMuted, modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(rooms, key = { it.id }) { room ->
                    RoomResultRow(room = room, language = language, onClick = { onSelectRoom(room) })
                }
            }
        }
    }
}

@Composable
private fun RoomResultRow(room: Room, language: AppLanguage, onClick: () -> Unit) {
    val l = HospitalLocalization
    val icon = when (room.kind) {
        "elevator" -> Icons.Default.Elevator
        "stair" -> Icons.Default.Stairs
        else -> Icons.Default.MeetingRoom
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("room_item_${room.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(parseHexColor(room.colorHex, Color(0xFF90A4AE))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = parseHexColor(room.textColorHex, MapInk), modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(l.roomLabel(room, language), fontSize = 15.sp, color = MapInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val alt = l.roomAltLabel(room, language)
            if (alt.isNotBlank()) Text(alt, fontSize = 12.sp, color = MapInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            l.getFloorLabel(room.floorLevel, language),
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3C4043),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MapChipSurface).padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
