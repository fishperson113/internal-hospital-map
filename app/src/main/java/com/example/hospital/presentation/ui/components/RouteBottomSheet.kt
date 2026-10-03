package com.example.hospital.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.StepIconType
import com.example.hospital.presentation.state.RouteStepUi
import com.example.hospital.presentation.state.TransferPreference
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapAmber
import com.example.ui.theme.MapAmberContainer
import com.example.ui.theme.MapBlueContainer
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapDivider
import com.example.ui.theme.MapGreenSuccess
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted
import com.example.ui.theme.MapInkSubtle
import com.example.ui.theme.MapRedAlert
import com.example.ui.theme.MapRedContainer
import kotlin.math.roundToInt

/** Icon + colors for a step, shared by the sheet, the step list and the navigation banner. */
internal data class StepVisual(val icon: ImageVector, val tint: Color, val container: Color)

internal fun stepVisual(step: RouteStepUi): StepVisual = when (step.iconType) {
    StepIconType.ELEVATOR -> StepVisual(Icons.Default.Elevator, MapAmber, MapAmberContainer)
    StepIconType.STAIRS -> StepVisual(Icons.Default.Stairs, MapAmber, MapAmberContainer)
    StepIconType.DESTINATION -> StepVisual(Icons.Default.Flag, MapRedAlert, MapRedContainer)
    StepIconType.WALK -> StepVisual(Icons.AutoMirrored.Filled.DirectionsWalk, MapBluePrimary, MapBlueContainer)
}

/**
 * Collapsed: ETA, distance, step chips, "Bắt đầu chỉ đường".
 * Expanded (list button or drag handle): full step list; tapping a step moves the map there.
 */
@Composable
fun RouteBottomSheet(
    route: HospitalRoute?,
    steps: List<RouteStepUi>,
    isCalculating: Boolean,
    routeNotFound: Boolean,
    transferPreference: TransferPreference,
    language: AppLanguage,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onStepClick: (Int) -> Unit,
    onStartNavigation: () -> Unit,
    helpStatus: String? = null,
    onAskHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    MapBottomSheetContainer(modifier = modifier.testTag("route_bottom_sheet"), onHandleClick = onToggleExpand) {
        when {
            isCalculating && route == null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MapBluePrimary)
                Text(l.t(language, "Đang tìm đường…", "Finding a route…"), fontSize = 14.sp, color = MapInkMuted)
            }

            routeNotFound || route == null -> Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MapRedAlert, modifier = Modifier.size(24.dp))
                val msg = when (transferPreference) {
                    TransferPreference.ELEVATOR_ONLY -> l.t(language, "Không có thang máy nối hai tầng này. Thử chọn “Nhanh nhất”.", "No elevator connects these floors. Try “Fastest”.")
                    TransferPreference.STAIRS_ONLY -> l.t(language, "Không có thang bộ nối hai tầng này. Thử chọn “Nhanh nhất”.", "No stairs connect these floors. Try “Fastest”.")
                    TransferPreference.FASTEST -> l.t(language, "Không tìm được đường đi giữa hai điểm này.", "No route found between these two points.")
                }
                Text(msg, fontSize = 14.sp, color = Color(0xFF3C4043), lineHeight = 20.sp)
            }

            else -> {
                val via = route.transfer?.let { l.t(language, "qua ", "via ") + l.roomLabel(it.roomA, language) }
                    ?: l.t(language, "cùng tầng", "same floor")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        l.t(language, "${route.estimatedDurationMinutes} phút", "${route.estimatedDurationMinutes} min"),
                        fontSize = 24.sp, fontWeight = FontWeight.Medium, color = MapGreenSuccess,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        "${route.totalDistanceMeters.roundToInt()} m · $via",
                        fontSize = 14.sp, color = MapInkMuted, modifier = Modifier.alignByBaseline()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    steps.forEachIndexed { i, step ->
                        if (i > 0) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MapInkSubtle, modifier = Modifier.size(18.dp))
                        val v = stepVisual(step)
                        Surface(
                            onClick = { onStepClick(i) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MapDivider)
                        ) {
                            Row(
                                modifier = Modifier.height(32.dp).padding(start = 7.dp, end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(v.icon, contentDescription = null, tint = v.tint, modifier = Modifier.size(18.dp))
                                Text(step.chipLabel, fontSize = 13.sp, color = Color(0xFF3C4043), maxLines = 1)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        HorizontalDivider(color = Color(0xFFECEFF1))
                        steps.forEachIndexed { i, step -> StepRow(step, onClick = { onStepClick(i) }) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onStartNavigation,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_navigation_action"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MapBluePrimary)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            l.getStartNavLabel(false, language),
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    OutlinedIconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(48.dp).testTag("btn_toggle_steps"),
                        border = BorderStroke(1.dp, MapDivider)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = l.getDetailedRouteLabel(language), tint = MapBluePrimary
                        )
                    }
                }

            }
        }
        // Web: "Tôi bị lạc" whenever there is a start place, also while loading or on a route error.
        if (onAskHelp != null) {
            HelpAssistanceCard(
                helpStatus = helpStatus,
                language = language,
                onAskHelp = onAskHelp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** No destination yet (web: idleHtml): where the user starts + "Đổi", a hint, and "Tôi bị lạc". */
@Composable
fun IdleSheet(
    startRoom: Room?,
    language: AppLanguage,
    onChangeStart: () -> Unit,
    helpStatus: String?,
    onAskHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val l = HospitalLocalization
    MapBottomSheetContainer(modifier = modifier.testTag("idle_sheet")) {
        if (startRoom != null) {
            PlaceRow(
                l.t(language, "Điểm xuất phát", "Starting point"),
                "${l.roomLabel(startRoom, language)} · ${l.getFloorLabel(startRoom.floorLevel, language)}",
                language, onChangeStart
            )
        }
        Text(
            l.t(language, "Hoặc mở một tầng, chạm vào phòng hoặc chỗ bạn đang đứng trên bản đồ.", "Or open a floor and tap a room or the spot where you stand."),
            fontSize = 13.sp, color = MapInkMuted, lineHeight = 18.sp
        )
        if (startRoom != null) {
            HelpAssistanceCard(helpStatus = helpStatus, language = language, onAskHelp = onAskHelp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** "Label: place   Đổi" (web: hint with a "Đổi" link that opens the search). */
@Composable
internal fun PlaceRow(label: String, place: String, language: AppLanguage, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $place", fontSize = 14.sp, color = MapInk, maxLines = 2, modifier = Modifier.weight(1f))
        TextButton(onClick = onChange) {
            Text(HospitalLocalization.t(language, "Đổi", "Change"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MapBluePrimary)
        }
    }
}

@Composable
private fun StepRow(step: RouteStepUi, onClick: () -> Unit) {
    val v = stepVisual(step)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(v.container), contentAlignment = Alignment.Center) {
            Icon(v.icon, contentDescription = null, tint = v.tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MapInk)
            Text(step.subtitle, fontSize = 13.sp, color = MapInkMuted)
        }
        Icon(Icons.Default.Map, contentDescription = null, tint = MapInkSubtle, modifier = Modifier.size(20.dp))
    }
}
