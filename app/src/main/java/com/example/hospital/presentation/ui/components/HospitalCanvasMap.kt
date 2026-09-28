package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import kotlin.math.min

@Composable
fun HospitalCanvasMap(
    floors: List<Floor>,
    selectedFloorLevel: Int?,
    currentRoute: HospitalRoute?,
    startRoom: Room?,
    destRoom: Room?,
    language: AppLanguage,
    zoomLevel: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    onPan: (Float, Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    onRoomClick: (Room) -> Unit,
    onSelectFloor1: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val floor1 = floors.find { it.level == 1 } ?: floors.firstOrNull()

    // When user selects Floor 2, 3, 4, 5: show clean TODO placeholder
    // When user selects "Tất cả" (null) or "Tầng 1" (1): both show the Floor 1 map as requested!
    val isOtherFloor = selectedFloorLevel != null && selectedFloorLevel != 1

    if (isOtherFloor) {
        FloorTodoPlaceholder(
            floorLevel = selectedFloorLevel ?: 2,
            language = language,
            onBackToFloor1 = onSelectFloor1,
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPan(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(floor1, zoomLevel, panOffsetX, panOffsetY) {
                detectTapGestures { tapOffset ->
                    if (floor1 != null) {
                        val squareSize = min(size.width, size.height) * 0.94f * zoomLevel
                        val left = (size.width - squareSize) / 2f + panOffsetX
                        val top = (size.height - squareSize) / 2f + panOffsetY

                        val relativeX = (tapOffset.x - left) / squareSize
                        val relativeY = (tapOffset.y - top) / squareSize

                        val clickedRoom = floor1.rooms.find { room ->
                            relativeX >= room.boundsLeft &&
                                    relativeX <= (room.boundsLeft + room.boundsWidth) &&
                                    relativeY >= room.boundsTop &&
                                    relativeY <= (room.boundsTop + room.boundsHeight)
                        }
                        if (clickedRoom != null) {
                            onRoomClick(clickedRoom)
                        }
                    }
                }
            }
            .testTag("hospital_map_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Subtle background grid
            drawBlueprintGrid(width, height)

            // 2. Responsive 1:1 Square Map Layout
            if (floor1 != null) {
                drawHoanMySquareFloor1Map(
                    canvasWidth = width,
                    canvasHeight = height,
                    floor = floor1,
                    currentRoute = currentRoute,
                    startRoom = startRoom,
                    destRoom = destRoom,
                    language = language,
                    zoom = zoomLevel,
                    panX = panOffsetX,
                    panY = panOffsetY,
                    textMeasurer = textMeasurer
                )
            }
        }

        // Floating Map Controls on Right (Zoom in, Zoom out, Reset view)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(
                icon = Icons.Default.Navigation,
                contentDescription = "Reset View",
                onClick = onResetView,
                testTag = "btn_reset_view"
            )
            MapControlButton(
                icon = Icons.Default.Add,
                contentDescription = "Zoom In",
                onClick = onZoomIn,
                testTag = "btn_zoom_in"
            )
            MapControlButton(
                icon = Icons.Default.Remove,
                contentDescription = "Zoom Out",
                onClick = onZoomOut,
                testTag = "btn_zoom_out"
            )
        }

        // Gesture Drag Hint Badge at bottom right
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 12.dp)
                .shadow(2.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.94f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFF0077D7),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (language == AppLanguage.VIETNAMESE) "Chạm phòng • Kéo di chuyển" else "Tap room • Drag to pan",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            }
        }
    }
}

@Composable
private fun FloorTodoPlaceholder(
    floorLevel: Int,
    language: AppLanguage,
    onBackToFloor1: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Construction,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = HospitalLocalization.getFloorLabel(floorLevel, language).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (language == AppLanguage.VIETNAMESE) "TODO: Đang hoàn thiện sơ đồ tầng này" else "TODO: Floor map under construction",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF57C00),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (language == AppLanguage.VIETNAMESE)
                        "Hiện tại ứng dụng tập trung hiển thị chi tiết sơ đồ Tầng 1 Bệnh viện Hoàn Mỹ."
                    else
                        "Currently, the app focuses on displaying Floor 1 of Hoan My Hospital.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onBackToFloor1,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077D7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == AppLanguage.VIETNAMESE) "Xem Sơ đồ Tầng 1" else "Back to Floor 1",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape)
            .testTag(testTag),
        shape = CircleShape,
        color = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFF0077D7),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun DrawScope.drawBlueprintGrid(width: Float, height: Float) {
    val gridColor = Color(0xFFE2E8F0)
    val step = 32f
    var x = 0f
    while (x < width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < height) {
        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        y += step
    }
}

/**
 * Renders the 1:1 Responsive Square Floor 1 Map
 */
private fun DrawScope.drawHoanMySquareFloor1Map(
    canvasWidth: Float,
    canvasHeight: Float,
    floor: Floor,
    currentRoute: HospitalRoute?,
    startRoom: Room?,
    destRoom: Room?,
    language: AppLanguage,
    zoom: Float,
    panX: Float,
    panY: Float,
    textMeasurer: TextMeasurer
) {
    // Responsive 1:1 Square sizing
    val squareSide = min(canvasWidth, canvasHeight) * 0.92f * zoom
    val left = (canvasWidth - squareSide) / 2f + panX
    val top = (canvasHeight - squareSide) / 2f + panY

    val corner = CornerRadius(14f, 14f)

    // 1. Board Background Shadow & Surface
    drawRoundRect(
        color = Color(0xFFCBD5E1).copy(alpha = 0.6f),
        topLeft = Offset(left, top + 6f),
        size = Size(squareSide, squareSide),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = Size(squareSide, squareSide),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color(0xFF94A3B8),
        topLeft = Offset(left, top),
        size = Size(squareSide, squareSide),
        cornerRadius = corner,
        style = Stroke(2.5f)
    )

    // 2. Header of the Board (Logo and Floor Name cleanly localized)
    val brandName = if (language == AppLanguage.VIETNAMESE) "Hoàn Mỹ" else "Hoan My"
    val logoText = textMeasurer.measure(
        text = AnnotatedString(brandName),
        style = TextStyle(color = Color(0xFF00756A), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    )
    drawCircle(
        color = Color(0xFF00756A),
        radius = 7f,
        center = Offset(left + 20f, top + 20f)
    )
    drawText(textLayoutResult = logoText, topLeft = Offset(left + 32f, top + 11f))

    val floorLabel = if (language == AppLanguage.VIETNAMESE) "TẦNG 1" else "FLOOR 1"
    val floorTitleText = textMeasurer.measure(
        text = AnnotatedString(floorLabel),
        style = TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
    )
    drawText(textLayoutResult = floorTitleText, topLeft = Offset(left + squareSide - floorTitleText.size.width - 20f, top + 11f))

    // 3. Hallways/Corridors Background (Light grey walkable zone)
    drawCorridorBeds(left, top, squareSide)

    // 4. Red Dashed Emergency Exit Path
    drawEmergencyExitDashes(left, top, squareSide, textMeasurer)

    // 5. Draw all Functional Rooms of Floor 1
    drawFloor1RoomBoxes(left, top, squareSide, floor, destRoom, language, textMeasurer)

    // 6. Draw Main Entrance Arrow (TIỀN SẢNH)
    drawMainEntranceIndicator(left, top, squareSide, language, textMeasurer)

    // 7. Draw "Bạn ở đây / You are here" Pin
    drawYouAreHerePin(left, top, squareSide, language, textMeasurer)

    // 8. Draw Navigation Path STRICTLY following Corridor Graph Nodes!
    if (currentRoute != null) {
        drawStrictCorridorNavigationPath(left, top, squareSide, currentRoute, textMeasurer)
    }

    // 9. Bottom Right Legend Box (CHÚ THÍCH | NOTE)
    drawLegendBoxSquare(left, top, squareSide, language, textMeasurer)
}

private fun DrawScope.drawCorridorBeds(
    left: Float,
    top: Float,
    side: Float
) {
    val corridorColor = Color(0xFFF1F5F9)

    // East-West Main Hallway (Y = 0.40f to 0.46f)
    drawRect(
        color = corridorColor,
        topLeft = Offset(left + side * 0.18f, top + side * 0.40f),
        size = Size(side * 0.65f, side * 0.06f)
    )

    // North-South Central Hallway (X = 0.42f to 0.48f)
    drawRect(
        color = corridorColor,
        topLeft = Offset(left + side * 0.42f, top + side * 0.42f),
        size = Size(side * 0.06f, side * 0.36f)
    )

    // Emergency North-South Hallway (X = 0.18f to 0.23f)
    drawRect(
        color = corridorColor,
        topLeft = Offset(left + side * 0.18f, top + side * 0.26f),
        size = Size(side * 0.05f, side * 0.50f)
    )

    // Diagnostic corridor (Y = 0.29f to 0.33f)
    drawRect(
        color = corridorColor,
        topLeft = Offset(left + side * 0.48f, top + side * 0.29f),
        size = Size(side * 0.35f, side * 0.04f)
    )
}

private fun DrawScope.drawEmergencyExitDashes(
    left: Float,
    top: Float,
    side: Float,
    textMeasurer: TextMeasurer
) {
    val exitRed = Color(0xFFDC2626)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)

    val p1 = Offset(left + side * 0.45f, top + side * 0.74f)
    val p2 = Offset(left + side * 0.45f, top + side * 0.43f)
    val p3 = Offset(left + side * 0.20f, top + side * 0.43f)
    val p4 = Offset(left + side * 0.20f, top + side * 0.32f)
    val pExitLeft = Offset(left + side * 0.08f, top + side * 0.32f)

    drawLine(exitRed, p1, p2, strokeWidth = 1.8f, pathEffect = dashEffect)
    drawLine(exitRed, p2, p3, strokeWidth = 1.8f, pathEffect = dashEffect)
    drawLine(exitRed, p3, p4, strokeWidth = 1.8f, pathEffect = dashEffect)
    drawLine(exitRed, p4, pExitLeft, strokeWidth = 1.8f, pathEffect = dashEffect)

    // Emergency exit arrow
    val arrowPath = Path().apply {
        moveTo(pExitLeft.x + 10f, pExitLeft.y - 6f)
        lineTo(pExitLeft.x, pExitLeft.y)
        lineTo(pExitLeft.x + 10f, pExitLeft.y + 6f)
    }
    drawPath(arrowPath, exitRed, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawFloor1RoomBoxes(
    left: Float,
    top: Float,
    side: Float,
    floor: Floor,
    selectedDestRoom: Room?,
    language: AppLanguage,
    textMeasurer: TextMeasurer
) {
    floor.rooms.forEach { room ->
        val rx = left + room.boundsLeft * side
        val ry = top + room.boundsTop * side
        val rw = room.boundsWidth * side
        val rh = room.boundsHeight * side

        val isDestSelected = selectedDestRoom?.id == room.id
        val (bgColor, borderColor, textColor) = when (room.id) {
            "room_emergency_b" -> Triple(Color(0xFFD32F2F), Color(0xFFB71C1C), Color.White)
            "room_reception", "room_minor_surgery" -> Triple(Color(0xFFFFCDD2), Color(0xFFE57373), Color(0xFFB71C1C))
            "room_pharmacy_service", "room_pharmacy_insurance", "room_pharmacy_storage" -> Triple(Color(0xFFB2DFDB), Color(0xFF4DB6AC), Color(0xFF004D40))
            "room_elevator_1" -> Triple(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFE65100))
            "room_stairs_1" -> Triple(Color(0xFFFFCC80), Color(0xFFFB8C00), Color(0xFFE65100))
            "room_cashier" -> Triple(Color(0xFF80CBC4), Color(0xFF26A69A), Color(0xFF004D40))
            "room_dept_office" -> Triple(Color(0xFF1E3A8A), Color(0xFF1E40AF), Color.White)
            "room_you_are_here", "room_main_entrance" -> Triple(Color.Transparent, Color.Transparent, Color.Transparent)
            else -> Triple(Color(0xFFB2EBF2), Color(0xFF4DD0E1), Color(0xFF006064))
        }

        if (rw > 0 && rh > 0 && bgColor != Color.Transparent) {
            drawRoundRect(
                color = bgColor,
                topLeft = Offset(rx, ry),
                size = Size(rw, rh),
                cornerRadius = CornerRadius(4f, 4f)
            )

            val strokeWidth = if (isDestSelected) 3.5f else 1.2f
            val strokeColor = if (isDestSelected) Color(0xFFDC2626) else borderColor
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(rx, ry),
                size = Size(rw, rh),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(strokeWidth)
            )

            // Red cross emblem for Emergency B
            if (room.id == "room_emergency_b") {
                val circleCenter = Offset(rx + rw * 0.22f, ry + rh * 0.50f)
                val cRadius = rh * 0.26f
                drawCircle(Color.White, cRadius, circleCenter)
                val crossHalf = cRadius * 0.55f
                drawLine(Color(0xFFD32F2F), Offset(circleCenter.x - crossHalf, circleCenter.y), Offset(circleCenter.x + crossHalf, circleCenter.y), strokeWidth = 2.5f)
                drawLine(Color(0xFFD32F2F), Offset(circleCenter.x, circleCenter.y - crossHalf), Offset(circleCenter.x, circleCenter.y + crossHalf), strokeWidth = 2.5f)
            }

            // Clean single-language text label
            val localizedName = HospitalLocalization.getRoomDisplayName(room.id, room.name, language)
            val fontSize = if (rw > 60f) 7.5.sp else 6.2.sp

            val textResult = textMeasurer.measure(
                text = AnnotatedString(localizedName),
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )

            val textOffsetX = if (room.id == "room_emergency_b") rx + rw * 0.38f else rx + (rw - textResult.size.width) / 2f
            val textOffsetY = ry + (rh - textResult.size.height) / 2f
            if (rw > 20f && rh > 12f) {
                drawText(textLayoutResult = textResult, topLeft = Offset(textOffsetX, textOffsetY))
            }
        }
    }
}

private fun DrawScope.drawMainEntranceIndicator(
    left: Float,
    top: Float,
    side: Float,
    language: AppLanguage,
    textMeasurer: TextMeasurer
) {
    val entranceX = left + side * 0.45f
    val entranceY = top + side * 0.81f

    val arrowColor = Color(0xFF00695C)
    val arrowPath = Path().apply {
        moveTo(entranceX, entranceY - 12f)
        lineTo(entranceX - 8f, entranceY)
        lineTo(entranceX + 8f, entranceY)
        close()
    }
    drawPath(arrowPath, arrowColor)
    drawRect(arrowColor, Offset(entranceX - 3f, entranceY), Size(6f, 12f))

    val entranceLabel = HospitalLocalization.getMainEntranceLabel(language)
    val entranceText = textMeasurer.measure(
        text = AnnotatedString(entranceLabel),
        style = TextStyle(
            color = Color(0xFF0F172A),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    )
    drawText(
        textLayoutResult = entranceText,
        topLeft = Offset(entranceX - entranceText.size.width / 2f, entranceY + 15f)
    )
}

private fun DrawScope.drawYouAreHerePin(
    left: Float,
    top: Float,
    side: Float,
    language: AppLanguage,
    textMeasurer: TextMeasurer
) {
    val markerX = left + side * 0.45f
    val markerY = top + side * 0.58f

    drawCircle(Color(0xFF0077D7).copy(alpha = 0.25f), 10f, Offset(markerX, markerY))
    drawCircle(Color(0xFF0077D7), 5.5f, Offset(markerX, markerY))
    drawCircle(Color.White, 2f, Offset(markerX, markerY))

    val youText = textMeasurer.measure(
        text = AnnotatedString(HospitalLocalization.getYouAreHereLabel(language)),
        style = TextStyle(color = Color(0xFF0077D7), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    )
    drawText(textLayoutResult = youText, topLeft = Offset(markerX - youText.size.width / 2f, markerY + 8f))
}

/**
 * Strict Corridor Navigation Path Drawing:
 * Traverses node-by-node along the corridor graph, strictly through hallways.
 * Will NEVER intersect or overlap room boxes!
 */
private fun DrawScope.drawStrictCorridorNavigationPath(
    left: Float,
    top: Float,
    side: Float,
    route: HospitalRoute,
    textMeasurer: TextMeasurer
) {
    val nodes = route.pathNodes
    if (nodes.size < 2) return

    val navPath = Path().apply {
        val first = nodes.first()
        moveTo(left + first.x * side, top + first.y * side)
        for (i in 1 until nodes.size) {
            val node = nodes[i]
            lineTo(left + node.x * side, top + node.y * side)
        }
    }

    // Outer glow path
    drawPath(
        path = navPath,
        color = Color(0xFFFFCDD2).copy(alpha = 0.85f),
        style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Solid vivid red route line
    drawPath(
        path = navPath,
        color = Color(0xFFE53935),
        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Start Pin (A)
    val startNode = nodes.first()
    drawPin(Offset(left + startNode.x * side, top + startNode.y * side), "A", Color(0xFF2E7D32), textMeasurer)

    // Destination Pin (B)
    val destNode = nodes.last()
    drawPin(Offset(left + destNode.x * side, top + destNode.y * side), "B", Color(0xFFD32F2F), textMeasurer)
}

private fun DrawScope.drawPin(
    center: Offset,
    label: String,
    color: Color,
    textMeasurer: TextMeasurer
) {
    drawCircle(color.copy(alpha = 0.28f), 12f, center)
    drawCircle(color, 9f, center)
    val txt = textMeasurer.measure(
        text = AnnotatedString(label),
        style = TextStyle(color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    )
    drawText(textLayoutResult = txt, topLeft = Offset(center.x - txt.size.width / 2f, center.y - txt.size.height / 2f))
}

private fun DrawScope.drawLegendBoxSquare(
    left: Float,
    top: Float,
    side: Float,
    language: AppLanguage,
    textMeasurer: TextMeasurer
) {
    val lx = left + side * 0.70f
    val ly = top + side * 0.86f
    val lw = side * 0.26f
    val lh = side * 0.10f

    drawRoundRect(
        color = Color(0xFFFFFBEB),
        topLeft = Offset(lx, ly),
        size = Size(lw, lh),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawRoundRect(
        color = Color(0xFFFDE68A),
        topLeft = Offset(lx, ly),
        size = Size(lw, lh),
        cornerRadius = CornerRadius(5f, 5f),
        style = Stroke(1f)
    )

    val noteTitle = textMeasurer.measure(
        text = AnnotatedString(HospitalLocalization.getLegendLabel(language)),
        style = TextStyle(color = Color(0xFF92400E), fontSize = 7.sp, fontWeight = FontWeight.Bold)
    )
    drawText(textLayoutResult = noteTitle, topLeft = Offset(lx + 6f, ly + 4f))

    val exitRed = Color(0xFFDC2626)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f), 0f)
    drawLine(exitRed, Offset(lx + 6f, ly + 18f), Offset(lx + 24f, ly + 18f), strokeWidth = 1.8f, pathEffect = dashEffect)

    val exitText = textMeasurer.measure(
        text = AnnotatedString(HospitalLocalization.getExitLabel(language)),
        style = TextStyle(color = exitRed, fontSize = 6.5.sp, fontWeight = FontWeight.Bold)
    )
    drawText(textLayoutResult = exitText, topLeft = Offset(lx + 28f, ly + 13f))
}
