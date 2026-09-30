package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapBlueContainer
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapDivider
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted
import com.example.ui.theme.MapInkSubtle

/** Shared shapes/elevation for everything that floats over the map. */
internal val FloatingCardShape = RoundedCornerShape(20.dp)
internal val FloatingElevation = 3.dp

internal fun parseHexColor(hex: String, fallback: Color): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }

/** Idle state (no destination): big search pill at the top. */
@Composable
fun SearchPill(
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = FloatingElevation,
        modifier = modifier.fillMaxWidth().height(56.dp).testTag("search_room_button")
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MapBluePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = HospitalLocalization.t(language, "Tìm phòng, khoa, dịch vụ…", "Search rooms, departments…"),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MapInk,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = HospitalLocalization.getHospitalName(language),
                    fontSize = 12.sp, color = MapInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MapBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MapBluePrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Small translucent chip under the top cards: what the map is showing + a gesture hint. */
@Composable
fun ViewTitleChip(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 1.dp,
        modifier = modifier.widthIn(max = 260.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MapInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = MapInkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Round 44dp floating button with an icon or a short text (e.g. "VI"). */
@Composable
fun MapFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    tint: Color = MapInk,
    testTag: String = ""
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = FloatingElevation,
        modifier = modifier.size(44.dp).testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
            if (text != null) Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MapBluePrimary)
        }
    }
}

/** Shown in the middle of the screen when the selected floor has no map data yet. */
@Composable
fun FloorEmptyCard(
    floorLevel: Int,
    language: AppLanguage,
    onShowAllFloors: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(shape = FloatingCardShape, color = Color.White, shadowElevation = FloatingElevation, modifier = modifier.widthIn(max = 300.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Construction, contentDescription = null, tint = MapInkSubtle, modifier = Modifier.size(32.dp))
            Text(
                text = HospitalLocalization.t(language, "Tầng $floorLevel chưa có sơ đồ", "Floor $floorLevel has no map yet"),
                fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MapInk, textAlign = TextAlign.Center
            )
            Text(
                text = HospitalLocalization.t(language, "Dữ liệu tầng này đang được cập nhật.", "This floor's data is being prepared."),
                fontSize = 13.sp, color = MapInkMuted, textAlign = TextAlign.Center, lineHeight = 18.sp
            )
            Surface(
                onClick = onShowAllFloors,
                shape = RoundedCornerShape(20.dp),
                color = MapBlueContainer,
                modifier = Modifier.padding(top = 8.dp).height(40.dp)
            ) {
                Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(
                        HospitalLocalization.t(language, "Xem tất cả tầng", "View all floors"),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MapBluePrimary
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet container used by the route / room / navigation sheets.
 * Not a ModalBottomSheet on purpose: the map must stay interactive behind it.
 */
@Composable
fun MapBottomSheetContainer(
    modifier: Modifier = Modifier,
    onHandleClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .then(if (onHandleClick != null) Modifier.clickable(onClick = onHandleClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MapDivider))
            }
            content()
        }
    }
}

/** Floating compass button: needle rotates in real-time, click toggles heading follow / aligns to North. */
@Composable
fun CompassFab(
    headingDegrees: Float,
    isFollowMode: Boolean,
    onClick: () -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isFollowMode) MapBluePrimary else Color.White,
        shadowElevation = FloatingElevation,
        modifier = modifier.size(44.dp).testTag("btn_compass")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rad = Math.toRadians((-headingDegrees).toDouble()).toFloat()
                val needleLen = size.width * 0.42f
                val needleWidth = size.width * 0.16f

                // North red pointer
                val nx = cx + needleLen * kotlin.math.sin(rad)
                val ny = cy - needleLen * kotlin.math.cos(rad)
                val lx = cx + needleWidth * kotlin.math.cos(rad)
                val ly = cy + needleWidth * kotlin.math.sin(rad)
                val rx = cx - needleWidth * kotlin.math.cos(rad)
                val ry = cy - needleWidth * kotlin.math.sin(rad)

                val northPath = Path().apply {
                    moveTo(nx, ny)
                    lineTo(lx, ly)
                    lineTo(rx, ry)
                    close()
                }
                drawPath(northPath, Color(0xFFD93025))

                // South white/grey pointer
                val sx = cx - needleLen * kotlin.math.sin(rad)
                val sy = cy + needleLen * kotlin.math.cos(rad)
                val southPath = Path().apply {
                    moveTo(sx, sy)
                    lineTo(lx, ly)
                    lineTo(rx, ry)
                    close()
                }
                drawPath(southPath, if (isFollowMode) Color.White.copy(alpha = 0.85f) else Color(0xFF9AA0A6))

                // Center pivot dot
                drawCircle(if (isFollowMode) Color.White else Color(0xFF3C4043), radius = 2.dp.toPx(), center = Offset(cx, cy))
            }
        }
    }
}
