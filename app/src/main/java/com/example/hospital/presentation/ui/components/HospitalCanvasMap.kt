package com.example.hospital.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import kotlin.math.*

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
    onSelectFloor: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Single Floor selection
    val currentFloor = if (selectedFloorLevel != null) {
        floors.find { it.level == selectedFloorLevel }
    } else null

    // If Floor 1 is selected, show TODO placeholder
    if (currentFloor != null && currentFloor.isTodo) {
        FloorTodoPlaceholder(
            floorLevel = currentFloor.level,
            language = language,
            onSelectFloor = onSelectFloor,
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
            .pointerInput(selectedFloorLevel, floors, zoomLevel, panOffsetX, panOffsetY) {
                detectTapGestures { tapOffset ->
                    if (selectedFloorLevel != null && currentFloor != null && !currentFloor.isTodo) {
                        // 2D Single Floor Hit-Test
                        val fw = currentFloor.width.coerceAtLeast(100f)
                        val fh = currentFloor.height.coerceAtLeast(100f)
                        val scale = min((size.width - 32f) / fw, (size.height - 180f) / fh) * zoomLevel
                        val originX = (size.width - fw * scale) / 2f + panOffsetX
                        val originY = (size.height - fh * scale) / 2f + 30f + panOffsetY

                        val fx = (tapOffset.x - originX) / scale
                        val fy = (tapOffset.y - originY) / scale

                        val clicked = currentFloor.rooms.find { room ->
                            if (room.polygon.isNotEmpty()) {
                                pointInPolygon(fx, fy, room.polygon)
                            } else {
                                fx in room.boundsLeft..(room.boundsLeft + room.boundsWidth) &&
                                        fy in room.boundsTop..(room.boundsTop + room.boundsHeight)
                            }
                        }
                        if (clicked != null) {
                            onRoomClick(clicked)
                        }
                    } else if (selectedFloorLevel == null) {
                        // 3D Isometric Multi-Floor Hit-Test
                        val activeFloors = floors.filter { !it.isTodo }
                        // Test upper floor first (Floor 3), then lower (Floor 2)
                        val sortedForHit = activeFloors.sortedByDescending { it.level }
                        val baseScale = computeIsoScale(size.width.toFloat(), size.height.toFloat(), zoomLevel)
                        val elevation = 240f * zoomLevel

                        for (fl in sortedForHit) {
                            for (room in fl.rooms) {
                                if (room.polygon.isEmpty()) continue
                                val isoPoly = room.polygon.map { pt ->
                                    projectIso(
                                        pt.x, pt.y, fl.level,
                                        size.width.toFloat(), size.height.toFloat(),
                                        baseScale, elevation, panOffsetX, panOffsetY
                                    )
                                }
                                if (pointInPolygonOffset(tapOffset.x, tapOffset.y, isoPoly)) {
                                    onRoomClick(room)
                                    return@detectTapGestures
                                }
                            }
                        }
                    }
                }
            }
            .testTag("hospital_map_canvas")
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Subtle Background Blueprint Grid
            drawBlueprintGrid(canvasWidth, canvasHeight)

            if (selectedFloorLevel == null) {
                // 2. ISOMETRIC 3D MULTI-FLOOR VIEW (Tất cả)
                drawIsometricBuildingMap(
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    floors = floors.filter { !it.isTodo },
                    currentRoute = currentRoute,
                    startRoom = startRoom,
                    destRoom = destRoom,
                    language = language,
                    zoom = zoomLevel,
                    panX = panOffsetX,
                    panY = panOffsetY,
                    textMeasurer = textMeasurer,
                    onSelectFloor = onSelectFloor
                )
            } else if (currentFloor != null && !currentFloor.isTodo) {
                // 3. 2D POLYGON SINGLE FLOOR VIEW (Tầng 2 / Tầng 3)
                drawPolygonFloorMap(
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    floor = currentFloor,
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

        // View Mode Badge at top right
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 180.dp, end = 14.dp)
                .shadow(2.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.94f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedFloorLevel == null) Icons.Default.Layers else Icons.Default.Map,
                    contentDescription = null,
                    tint = Color(0xFF0077D7),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedFloorLevel == null) {
                        if (language == AppLanguage.VIETNAMESE) "3D Isometric" else "3D Isometric"
                    } else {
                        HospitalLocalization.getFloorLabel(selectedFloorLevel, language)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
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

// -----------------------------------------------------------------------------
// 1. FLOOR 1 TODO PLACEHOLDER
// -----------------------------------------------------------------------------

@Composable
private fun FloorTodoPlaceholder(
    floorLevel: Int,
    language: AppLanguage,
    onSelectFloor: (Int?) -> Unit,
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Construction,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = HospitalLocalization.getFloorLabel(floorLevel, language).uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (language == AppLanguage.VIETNAMESE) "TODO: Đang hoàn thiện sơ đồ tầng này" else "TODO: Floor map under construction",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF57C00),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (language == AppLanguage.VIETNAMESE)
                        "Sơ đồ Tầng 1 đã chuyển về trạng thái TODO theo yêu cầu. Hiện tại hệ thống hỗ trợ đầy đủ sơ đồ đa giác Tầng 2, Tầng 3 và chế độ xem 3D Isometric."
                    else
                        "Floor 1 is currently in TODO status. The app supports full polygon maps for Floor 2, Floor 3, and 3D Isometric view.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons to go to Floor 2, Floor 3, or 3D view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSelectFloor(2) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077D7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.VIETNAMESE) "Xem Tầng 2" else "Floor 2",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onSelectFloor(3) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (language == AppLanguage.VIETNAMESE) "Xem Tầng 3" else "Floor 3",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { onSelectFloor(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "3D Iso",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0077D7)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. 2D POLYGON SINGLE FLOOR RENDERING (Tầng 2 & Tầng 3)
// -----------------------------------------------------------------------------

private fun DrawScope.drawPolygonFloorMap(
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
    val fw = floor.width.coerceAtLeast(100f)
    val fh = floor.height.coerceAtLeast(100f)
    val scale = min((canvasWidth - 32f) / fw, (canvasHeight - 180f) / fh) * zoom
    val originX = (canvasWidth - fw * scale) / 2f + panX
    val originY = (canvasHeight - fh * scale) / 2f + 30f + panY

    fun toCanvas(px: Float, py: Float): Offset {
        return Offset(originX + px * scale, originY + py * scale)
    }

    // 1. Floor Base Slab / Architectural Card
    drawRoundRect(
        color = Color(0x18000000),
        topLeft = Offset(originX + 4f, originY + 8f),
        size = Size(fw * scale, fh * scale),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawRoundRect(
        color = Color(0xFFFCFDFE),
        topLeft = Offset(originX, originY),
        size = Size(fw * scale, fh * scale),
        cornerRadius = CornerRadius(16f, 16f)
    )
    drawRoundRect(
        color = Color(0xFFCBD5E1),
        topLeft = Offset(originX, originY),
        size = Size(fw * scale, fh * scale),
        cornerRadius = CornerRadius(16f, 16f),
        style = Stroke(2.0f)
    )

    // 2. Building Brand and Floor Title Header
    val brandName = if (language == AppLanguage.VIETNAMESE) "Bệnh viện Đa khoa Hoàn Mỹ" else "Hoan My General Hospital"
    val brandText = textMeasurer.measure(
        text = AnnotatedString(brandName),
        style = TextStyle(color = Color(0xFF00756A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    )
    drawCircle(
        color = Color(0xFF00756A),
        radius = 5f,
        center = Offset(originX + 16f, originY + 16f)
    )
    drawText(textLayoutResult = brandText, topLeft = Offset(originX + 26f, originY + 8f))

    val floorLabel = HospitalLocalization.getFloorLabel(floor.level, language).uppercase()
    val floorText = textMeasurer.measure(
        text = AnnotatedString(floorLabel),
        style = TextStyle(color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Black)
    )
    drawText(textLayoutResult = floorText, topLeft = Offset(originX + fw * scale - floorText.size.width - 16f, originY + 8f))

    // 3. Render all Polygon Rooms
    for (room in floor.rooms) {
        val poly = room.polygon
        if (poly.isEmpty()) continue

        val path = Path().apply {
            val first = toCanvas(poly[0].x, poly[0].y)
            moveTo(first.x, first.y)
            for (i in 1 until poly.size) {
                val pt = toCanvas(poly[i].x, poly[i].y)
                lineTo(pt.x, pt.y)
            }
            close()
        }

        // Room Background Color
        val roomBgColor = try {
            Color(android.graphics.Color.parseColor(room.colorHex))
        } catch (_: Exception) {
            Color(0xFFCFE0E8)
        }
        drawPath(path, roomBgColor)

        // Room Border
        val isStart = room.id == startRoom?.id
        val isDest = room.id == destRoom?.id

        if (isDest) {
            // Glowing Destination Outline
            drawPath(path, Color(0x66EF4444), style = Stroke(width = 6f * zoom.coerceIn(0.8f, 1.5f)))
            drawPath(path, Color(0xFFEF4444), style = Stroke(width = 3.5f))
        } else if (isStart) {
            // Glowing Start Outline
            drawPath(path, Color(0x6610B981), style = Stroke(width = 6f * zoom.coerceIn(0.8f, 1.5f)))
            drawPath(path, Color(0xFF10B981), style = Stroke(width = 3.5f))
        } else {
            // Normal Room Border
            drawPath(path, Color.White.copy(alpha = 0.65f), style = Stroke(width = 1.2f))
        }

        // Room Label & Badge
        val labelCenter = room.labelAt ?: Point2D(
            poly.map { it.x }.average().toFloat(),
            poly.map { it.y }.average().toFloat()
        )
        val canvasLabelPos = toCanvas(labelCenter.x, labelCenter.y)

        // Stair or Elevator Icon / Badge
        if (room.kind == "elevator") {
            drawCircle(Color(0xFF2E7D32), radius = 8f * zoom.coerceIn(0.8f, 1.4f), center = canvasLabelPos)
            val elevText = textMeasurer.measure(
                text = AnnotatedString("EV"),
                style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            drawText(elevText, topLeft = Offset(canvasLabelPos.x - elevText.size.width / 2f, canvasLabelPos.y - elevText.size.height / 2f))
        } else if (room.kind == "stair") {
            drawCircle(Color(0xFFE65100), radius = 8f * zoom.coerceIn(0.8f, 1.4f), center = canvasLabelPos)
            val stairText = textMeasurer.measure(
                text = AnnotatedString("ST"),
                style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )
            drawText(stairText, topLeft = Offset(canvasLabelPos.x - stairText.size.width / 2f, canvasLabelPos.y - stairText.size.height / 2f))
        } else {
            // Normal Room Name
            val displayName = if (language == AppLanguage.ENGLISH && room.nameEn.isNotEmpty()) {
                room.nameEn
            } else {
                room.name
            }

            if (displayName.isNotBlank() && zoom >= 0.7f) {
                val textColor = try {
                    Color(android.graphics.Color.parseColor(room.textColorHex))
                } catch (_: Exception) {
                    Color(0xFF222222)
                }

                val fontSize = (if (displayName.length > 15) 7.5f else 8.5f) * zoom.coerceIn(0.8f, 1.3f)
                val roomTextResult = textMeasurer.measure(
                    text = AnnotatedString(displayName),
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                )

                // Only draw if label fits reasonably
                val rx = canvasLabelPos.x - roomTextResult.size.width / 2f
                val ry = canvasLabelPos.y - roomTextResult.size.height / 2f
                drawText(roomTextResult, topLeft = Offset(rx, ry))
            }
        }

        // Draw Start Pin Badge
        if (isStart) {
            drawCircle(Color(0xFF10B981), radius = 10f, center = Offset(canvasLabelPos.x, canvasLabelPos.y - 14f))
            drawCircle(Color.White, radius = 5f, center = Offset(canvasLabelPos.x, canvasLabelPos.y - 14f))
        }

        // Draw Destination Pin Badge
        if (isDest) {
            drawCircle(Color(0xFFEF4444), radius = 10f, center = Offset(canvasLabelPos.x, canvasLabelPos.y - 14f))
            drawCircle(Color.White, radius = 5f, center = Offset(canvasLabelPos.x, canvasLabelPos.y - 14f))
        }
    }

    // 4. Draw Section Labels from Floor JSON
    for (flLabel in floor.labels) {
        val labelPos = toCanvas(flLabel.x, flLabel.y)
        val textStr = if (language == AppLanguage.ENGLISH && flLabel.nameEn.isNotEmpty()) flLabel.nameEn else flLabel.name
        val meas = textMeasurer.measure(
            text = AnnotatedString(textStr),
            style = TextStyle(
                color = Color(0xFF1E3A8A),
                fontSize = 9.sp * zoom.coerceIn(0.8f, 1.2f),
                fontWeight = FontWeight.Bold
            )
        )
        // Background pill
        val pillPadding = 4f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(labelPos.x - pillPadding, labelPos.y - pillPadding),
            size = Size(meas.size.width + pillPadding * 2, meas.size.height + pillPadding * 2),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawText(meas, topLeft = labelPos)
    }

    // 5. Draw Navigation Path
    if (currentRoute != null) {
        val floorSegments = currentRoute.segments.filter { it.floorLevel == floor.level }
        for (seg in floorSegments) {
            if (seg.points.size >= 2) {
                val routePath = Path()
                val first = toCanvas(seg.points[0].x, seg.points[0].y)
                routePath.moveTo(first.x, first.y)
                for (i in 1 until seg.points.size) {
                    val p = toCanvas(seg.points[i].x, seg.points[i].y)
                    routePath.lineTo(p.x, p.y)
                }

                // Outer cyan glow
                drawPath(
                    path = routePath,
                    color = Color(0x660077D7),
                    style = Stroke(width = 8f * zoom.coerceIn(0.8f, 1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Main route line
                drawPath(
                    path = routePath,
                    color = Color(0xFF0077D7),
                    style = Stroke(width = 4f * zoom.coerceIn(0.8f, 1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw dots along waypoints
                for (p in seg.points) {
                    val cp = toCanvas(p.x, p.y)
                    drawCircle(Color.White, radius = 3.5f, center = cp)
                    drawCircle(Color(0xFF0077D7), radius = 2f, center = cp)
                }
            }
        }
    }

    // 6. Legend Box at Bottom Right
    drawLegendBox(originX + fw * scale - 180f, originY + fh * scale - 70f, language, textMeasurer)
}

// -----------------------------------------------------------------------------
// 3. 3D ISOMETRIC MULTI-FLOOR VIEW (Tất cả / All)
// -----------------------------------------------------------------------------

private fun DrawScope.drawIsometricBuildingMap(
    canvasWidth: Float,
    canvasHeight: Float,
    floors: List<Floor>,
    currentRoute: HospitalRoute?,
    startRoom: Room?,
    destRoom: Room?,
    language: AppLanguage,
    zoom: Float,
    panX: Float,
    panY: Float,
    textMeasurer: TextMeasurer,
    onSelectFloor: (Int?) -> Unit
) {
    val activeFloors = floors.filter { !it.isTodo }.sortedBy { it.level } // Floor 2 then Floor 3
    if (activeFloors.isEmpty()) return

    val baseScale = computeIsoScale(canvasWidth, canvasHeight, zoom)
    val elevation = 240f * zoom

    fun project(fx: Float, fy: Float, level: Int): Offset {
        return projectIso(fx, fy, level, canvasWidth, canvasHeight, baseScale, elevation, panX, panY)
    }

    // 1. Draw Connecting Shafts between Floor 2 and Floor 3 (Stairs & Elevators)
    val f2 = activeFloors.find { it.level == 2 }
    val f3 = activeFloors.find { it.level == 3 }
    if (f2 != null && f3 != null) {
        val f2Shafts = f2.rooms.filter { it.link != null }.associateBy { it.link!! }
        val f3Shafts = f3.rooms.filter { it.link != null }.associateBy { it.link!! }
        val common = f2Shafts.keys.intersect(f3Shafts.keys)

        for (link in common) {
            val r2 = f2Shafts[link]!!
            val r3 = f3Shafts[link]!!

            val c2 = r2.labelAt ?: Point2D(r2.polygon.map { it.x }.average().toFloat(), r2.polygon.map { it.y }.average().toFloat())
            val c3 = r3.labelAt ?: Point2D(r3.polygon.map { it.x }.average().toFloat(), r3.polygon.map { it.y }.average().toFloat())

            val p2 = project(c2.x, c2.y, 2)
            val p3 = project(c3.x, c3.y, 3)

            // Shaft vertical dashed pillar
            val shaftColor = if (r2.kind == "elevator") Color(0x8800B4D8) else Color(0x88FB8500)
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            drawLine(
                color = shaftColor,
                start = p2,
                end = p3,
                strokeWidth = 2.5f,
                pathEffect = dash
            )
        }
    }

    // 2. Render Floors from bottom to top (Floor 2, then Floor 3)
    for (fl in activeFloors) {
        val fw = fl.width
        val fh = fl.height

        // 3D Slab Thickness Extrusion
        val slabCorners = listOf(
            project(0f, 0f, fl.level),
            project(fw, 0f, fl.level),
            project(fw, fh, fl.level),
            project(0f, fh, fl.level)
        )

        // Drop shadow under slab
        val shadowPath = Path().apply {
            moveTo(slabCorners[0].x, slabCorners[0].y + 16f)
            for (i in 1..3) lineTo(slabCorners[i].x, slabCorners[i].y + 16f)
            close()
        }
        drawPath(shadowPath, Color(0x18000000))

        // Side walls (3D thickness: 12px)
        val sideThickness = 12f
        val frontWall = Path().apply {
            moveTo(slabCorners[3].x, slabCorners[3].y)
            lineTo(slabCorners[2].x, slabCorners[2].y)
            lineTo(slabCorners[2].x, slabCorners[2].y + sideThickness)
            lineTo(slabCorners[3].x, slabCorners[3].y + sideThickness)
            close()
        }
        drawPath(frontWall, Color(0xFFCBD5E1))

        val rightWall = Path().apply {
            moveTo(slabCorners[2].x, slabCorners[2].y)
            lineTo(slabCorners[1].x, slabCorners[1].y)
            lineTo(slabCorners[1].x, slabCorners[1].y + sideThickness)
            lineTo(slabCorners[2].x, slabCorners[2].y + sideThickness)
            close()
        }
        drawPath(rightWall, Color(0xFF94A3B8))

        // Slab Top Face
        val topSlab = Path().apply {
            moveTo(slabCorners[0].x, slabCorners[0].y)
            for (i in 1..3) lineTo(slabCorners[i].x, slabCorners[i].y)
            close()
        }
        drawPath(topSlab, Color(0xFFF8FAFC))
        drawPath(topSlab, Color(0xFFCBD5E1), style = Stroke(width = 1.5f))

        // Floating Isometric Floor Tag Badge
        val floorTagPos = slabCorners[0]
        val floorTitle = HospitalLocalization.getFloorLabel(fl.level, language).uppercase()
        val tagResult = textMeasurer.measure(
            text = AnnotatedString("$floorTitle (${fl.rooms.size} phòng)"),
            style = TextStyle(color = Color(0xFF0077D7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(floorTagPos.x - 120f, floorTagPos.y - 12f),
            size = Size(tagResult.size.width + 16f, tagResult.size.height + 10f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color(0xFF0077D7),
            topLeft = Offset(floorTagPos.x - 120f, floorTagPos.y - 12f),
            size = Size(tagResult.size.width + 16f, tagResult.size.height + 10f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(1.2f)
        )
        drawText(tagResult, topLeft = Offset(floorTagPos.x - 112f, floorTagPos.y - 7f))

        // Render Rooms in Isometric
        for (room in fl.rooms) {
            val poly = room.polygon
            if (poly.isEmpty()) continue

            val isoPath = Path().apply {
                val first = project(poly[0].x, poly[0].y, fl.level)
                moveTo(first.x, first.y)
                for (i in 1 until poly.size) {
                    val p = project(poly[i].x, poly[i].y, fl.level)
                    lineTo(p.x, p.y)
                }
                close()
            }

            val roomBgColor = try {
                Color(android.graphics.Color.parseColor(room.colorHex))
            } catch (_: Exception) {
                Color(0xFFCFE0E8)
            }
            drawPath(isoPath, roomBgColor)

            val isStart = room.id == startRoom?.id
            val isDest = room.id == destRoom?.id

            if (isDest) {
                drawPath(isoPath, Color(0x66EF4444), style = Stroke(width = 6f))
                drawPath(isoPath, Color(0xFFEF4444), style = Stroke(width = 3.5f))
            } else if (isStart) {
                drawPath(isoPath, Color(0x6610B981), style = Stroke(width = 6f))
                drawPath(isoPath, Color(0xFF10B981), style = Stroke(width = 3.5f))
            } else {
                drawPath(isoPath, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1f))
            }

            // Room Centroid for label
            val c = room.labelAt ?: Point2D(poly.map { it.x }.average().toFloat(), poly.map { it.y }.average().toFloat())
            val isoCenter = project(c.x, c.y, fl.level)

            if (room.kind == "elevator") {
                drawCircle(Color(0xFF2E7D32), radius = 6f * zoom.coerceIn(0.7f, 1.3f), center = isoCenter)
            } else if (room.kind == "stair") {
                drawCircle(Color(0xFFE65100), radius = 6f * zoom.coerceIn(0.7f, 1.3f), center = isoCenter)
            } else if (zoom >= 0.9f && (room.name.isNotBlank() || room.nameEn.isNotBlank())) {
                val name = if (language == AppLanguage.ENGLISH && room.nameEn.isNotEmpty()) room.nameEn else room.name
                val textColor = try {
                    Color(android.graphics.Color.parseColor(room.textColorHex))
                } catch (_: Exception) {
                    Color(0xFF222222)
                }
                val meas = textMeasurer.measure(
                    text = AnnotatedString(name.take(12)),
                    style = TextStyle(color = textColor, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                )
                drawText(meas, topLeft = Offset(isoCenter.x - meas.size.width / 2f, isoCenter.y - meas.size.height / 2f))
            }

            if (isStart) {
                drawCircle(Color(0xFF10B981), radius = 8f, center = Offset(isoCenter.x, isoCenter.y - 12f))
                drawCircle(Color.White, radius = 4f, center = Offset(isoCenter.x, isoCenter.y - 12f))
            }
            if (isDest) {
                drawCircle(Color(0xFFEF4444), radius = 8f, center = Offset(isoCenter.x, isoCenter.y - 12f))
                drawCircle(Color.White, radius = 4f, center = Offset(isoCenter.x, isoCenter.y - 12f))
            }
        }
    }

    // 3. Draw 3D Multi-Floor Route
    if (currentRoute != null) {
        // Draw segments on each floor
        for (seg in currentRoute.segments) {
            if (seg.points.size >= 2) {
                val isoRoute = Path()
                val first = project(seg.points[0].x, seg.points[0].y, seg.floorLevel)
                isoRoute.moveTo(first.x, first.y)
                for (i in 1 until seg.points.size) {
                    val p = project(seg.points[i].x, seg.points[i].y, seg.floorLevel)
                    isoRoute.lineTo(p.x, p.y)
                }

                drawPath(isoRoute, Color(0x660077D7), style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(isoRoute, Color(0xFF0077D7), style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // If transfer exists, draw vertical climbing laser line between floors
        currentRoute.transfer?.let { transfer ->
            val pA = transfer.roomA.labelAt ?: Point2D(transfer.roomA.polygon.map { it.x }.average().toFloat(), transfer.roomA.polygon.map { it.y }.average().toFloat())
            val pB = transfer.roomB.labelAt ?: Point2D(transfer.roomB.polygon.map { it.x }.average().toFloat(), transfer.roomB.polygon.map { it.y }.average().toFloat())

            val isoA = project(pA.x, pA.y, transfer.roomA.floorLevel)
            val isoB = project(pB.x, pB.y, transfer.roomB.floorLevel)

            drawLine(
                color = Color(0xFF00F5D4),
                start = isoA,
                end = isoB,
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF0077D7),
                start = isoA,
                end = isoB,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

// -----------------------------------------------------------------------------
// HELPER MATH & PROJECTION
// -----------------------------------------------------------------------------

private fun computeIsoScale(canvasWidth: Float, canvasHeight: Float, zoom: Float): Float {
    val fw = 1795f
    val fh = 734f
    val cosA = 0.866f
    val sinA = 0.500f
    val spanX = (fw + fh) * cosA * 0.5f
    val spanY = (fw + fh) * sinA * 0.45f + 260f
    return min((canvasWidth - 60f) / spanX, (canvasHeight - 160f) / spanY) * 0.95f * zoom
}

private fun projectIso(
    fx: Float,
    fy: Float,
    level: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    scale: Float,
    elevation: Float,
    panX: Float,
    panY: Float
): Offset {
    val fw = 1795f
    val fh = 734f
    val cosA = 0.866f
    val sinA = 0.500f

    val u = (fx - fw / 2f) * scale
    val v = (fy - fh / 2f) * scale

    val sx = canvasWidth / 2f + (u - v) * cosA + panX
    val sy = canvasHeight / 2f + 40f + (u + v) * sinA * 0.55f - (level - 2) * elevation + panY
    return Offset(sx, sy)
}

private fun pointInPolygon(x: Float, y: Float, poly: List<Point2D>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val pi = poly[i]
        val pj = poly[j]
        if ((pi.y > y) != (pj.y > y) &&
            x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun pointInPolygonOffset(x: Float, y: Float, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val pi = poly[i]
        val pj = poly[j]
        if ((pi.y > y) != (pj.y > y) &&
            x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun DrawScope.drawBlueprintGrid(width: Float, height: Float) {
    val gridColor = Color(0xFFE2E8F0)
    val step = 32f
    var x = 0f
    while (x < width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 0.8f)
        x += step
    }
    var y = 0f
    while (y < height) {
        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 0.8f)
        y += step
    }
}

private fun DrawScope.drawLegendBox(
    left: Float,
    top: Float,
    language: AppLanguage,
    textMeasurer: TextMeasurer
) {
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(left, top),
        size = Size(170f, 62f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color(0xFFCBD5E1),
        topLeft = Offset(left, top),
        size = Size(170f, 62f),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(1f)
    )

    val title = if (language == AppLanguage.VIETNAMESE) "CHÚ THÍCH" else "LEGEND"
    val t = textMeasurer.measure(AnnotatedString(title), TextStyle(color = Color(0xFF475569), fontSize = 8.sp, fontWeight = FontWeight.Bold))
    drawText(t, topLeft = Offset(left + 8f, top + 5f))

    // Items
    val items = listOf(
        Pair(Color(0xFFCFE0E8), if (language == AppLanguage.VIETNAMESE) "Phòng khám" else "Clinics"),
        Pair(Color(0xFF1F5F63), if (language == AppLanguage.VIETNAMESE) "Phẫu thuật" else "Surgery"),
        Pair(Color(0xFFA9C3A4), if (language == AppLanguage.VIETNAMESE) "Thang máy" else "Elevator"),
        Pair(Color(0xFFE89A3C), if (language == AppLanguage.VIETNAMESE) "Thang bộ" else "Stairs")
    )

    var ix = left + 8f
    var iy = top + 20f
    for ((idx, it) in items.withIndex()) {
        drawCircle(it.first, radius = 4f, center = Offset(ix + 4f, iy + 6f))
        val text = textMeasurer.measure(AnnotatedString(it.second), TextStyle(color = Color(0xFF334155), fontSize = 7.5.sp))
        drawText(text, topLeft = Offset(ix + 12f, iy))
        if (idx % 2 == 1) {
            ix = left + 8f
            iy += 18f
        } else {
            ix += 80f
        }
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(42.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFF1E293B),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
