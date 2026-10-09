package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.isSpot
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapChipSurface
import com.example.ui.theme.MapDivider
import com.example.ui.theme.MapGreenContainer
import com.example.ui.theme.MapGreenOnContainer
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted

/** Sheet shown after tapping a room (or a corridor spot, web: "Vị trí đã chọn") on the 2D map. */
@Composable
fun RoomDetailSheet(
    room: Room,
    language: AppLanguage,
    onClose: () -> Unit,
    onRouteTo: () -> Unit,
    onRouteFrom: () -> Unit,
    helpStatus: String? = null,
    onAskHelp: (() -> Unit)? = null, // web shows "Tôi bị lạc" under the room card too
    ticket: String = "",
    onTicketChange: ((String) -> Unit)? = null, // queue number for the CRM (web: #ticket-input)
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    val spot = room.isSpot
    MapBottomSheetContainer(modifier = modifier.testTag("room_detail_sheet"), hideResetKey = room.id) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (spot) MapBluePrimary else parseHexColor(room.colorHex, Color(0xFF90A4AE)))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val title = if (spot) l.t(language, "Vị trí đã chọn", "Selected spot") else l.roomLabel(room, language)
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = MapInk, lineHeight = 24.sp)
                val alt = if (spot) l.roomLabel(room, language) else l.roomAltLabel(room, language)
                if (alt.isNotBlank()) Text(alt, fontSize = 13.sp, color = MapInkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Tag(l.getFloorLabel(room.floorLevel, language), MapChipSurface, Color(0xFF3C4043))
                    if (room.kind == "elevator" || room.kind == "stair") {
                        Tag(l.t(language, "Nối các tầng", "Connects floors"), MapGreenContainer, MapGreenOnContainer)
                    }
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MapChipSurface)
            ) {
                Icon(Icons.Default.Close, contentDescription = l.getCloseButtonLabel(language), tint = MapInkMuted, modifier = Modifier.size(20.dp))
            }
        }
        // Spot: "I am here" first (web order); room: "Go here" first.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (spot) {
                PrimaryAction(l.t(language, "Tôi đang ở đây", "I am here"), Icons.Default.MyLocation, "btn_route_from_room", onRouteFrom)
                OutlineAction(l.t(language, "Đến đây", "Go here"), Icons.Default.Directions, "btn_route_to_room", onRouteTo)
            } else {
                PrimaryAction(l.t(language, "Đến đây", "Go here"), Icons.Default.Directions, "btn_route_to_room", onRouteTo)
                OutlineAction(l.t(language, "Đi từ đây", "Start here"), Icons.Default.TripOrigin, "btn_route_from_room", onRouteFrom)
            }
        }
        if (onAskHelp != null) {
            HelpAssistanceCard(helpStatus = helpStatus, language = language, onAskHelp = onAskHelp, ticket = ticket, onTicketChange = onTicketChange, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun RowScope.PrimaryAction(text: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(48.dp).testTag(tag),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MapBluePrimary)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RowScope.OutlineAction(text: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(48.dp).testTag(tag),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MapDivider)
    ) {
        Icon(icon, contentDescription = null, tint = MapBluePrimary, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MapBluePrimary, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
