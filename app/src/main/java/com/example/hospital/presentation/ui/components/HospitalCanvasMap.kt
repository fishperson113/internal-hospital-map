package com.example.hospital.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Elevator
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.presentation.util.HospitalLocalization
import com.example.ui.theme.MapAmber
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapCanvasBackground
import com.example.ui.theme.MapElevatorGreen
import com.example.ui.theme.MapGreenSuccess
import com.example.ui.theme.MapInk
import com.example.ui.theme.MapInkMuted
import com.example.ui.theme.MapInkSubtle
import com.example.ui.theme.MapPlateStroke
import com.example.ui.theme.MapRedAlert
import com.example.ui.theme.MapSlabSide
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen indoor map.
 *
 * - selectedFloorLevel == null → isometric stack of every floor ("Tất cả").
 *   Drag = orbit (spin/tilt), pinch = zoom, tap a floor = open it in 2D.
 * - selectedFloorLevel == N    → flat 2D plan of floor N, auto-rotated 90° on portrait
 *   screens so the wide plan (≈2.4:1) fills the phone. Drag = pan, pinch = zoom, tap room = details.
 *
 * Switching between the two animates one camera (tilt/spin/zoom/pan) + per-floor Z/alpha
 * over [MapCameraMath.TRANSITION_MS] ms, so the chosen floor flies out of the stack and flattens.
 * Room labels are drawn upright in screen space and only when they fit inside their room.
 */
@Composable
fun HospitalCanvasMap(
    floors: List<Floor>,
    selectedFloorLevel: Int?,
    currentRoute: HospitalRoute?,
    startRoom: Room?,
    destRoom: Room?,
    selectedRoom: Room?,
    language: AppLanguage,
    viewportInsets: MapViewportInsets,
    recenterRequest: Int,
    userPosition: Point2D? = null,
    userFloorLevel: Int? = null,
    userHeadingDegrees: Float = 0f,
    isNavigating: Boolean = false,
    isAutoRotateEnabled: Boolean = false,
    onRoomClick: (Room) -> Unit,
    onSelectFloor: (Int?) -> Unit,
    onEmptyTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val elevatorPainter = rememberVectorPainter(Icons.Filled.Elevator)
    val stairsPainter = rememberVectorPainter(Icons.Filled.Stairs)

    val sortedFloors = remember(floors) { floors.sortedBy { it.level } }
    val dataFloor = sortedFloors.firstOrNull { !it.isTodo }
    val planW = dataFloor?.width ?: 1795f
    val planH = dataFloor?.height ?: 734f
    val shafts = remember(sortedFloors) { connectingShafts(sortedFloors) }
    val labelTexts = remember(sortedFloors, language) {
        sortedFloors.flatMap { it.rooms }.associate { it.id to HospitalLocalization.roomLabel(it, language) }
    }
    val routeLevels = remember(currentRoute) { currentRoute?.segments?.map { it.floorLevel }?.toSet() ?: emptySet() }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    var floorVisuals by remember { mutableStateOf<Map<Int, FloorVisual>>(emptyMap()) }
    var isTransitioning by remember { mutableStateOf(false) }
    var fitScale by remember { mutableFloatStateOf(1f) }
    var isoTilt by remember { mutableFloatStateOf(MapCameraMath.ISO_TILT) }
    var isoSpin by remember { mutableFloatStateOf(MapCameraMath.ISO_SPIN) }
    val overlayAlpha = remember { Animatable(1f) }

    val latestInsets by rememberUpdatedState(viewportInsets)
    val latestOnRoomClick by rememberUpdatedState(onRoomClick)
    val latestOnSelectFloor by rememberUpdatedState(onSelectFloor)
    val latestOnEmptyTap by rememberUpdatedState(onEmptyTap)
    val insetsReady = viewportInsets.top > 0f

    // Camera fit + 3D ↔ 2D transition
    LaunchedEffect(selectedFloorLevel, recenterRequest, viewSize, sortedFloors, insetsReady) {
        if (viewSize.width == 0 || viewSize.height == 0 || sortedFloors.isEmpty()) return@LaunchedEffect
        val w = viewSize.width.toFloat()
        val h = viewSize.height.toFloat()
        val selIdx = selectedFloorLevel?.let { l -> sortedFloors.indexOfFirst { it.level == l } }?.takeIf { it >= 0 }
        val targetVis = sortedFloors.mapIndexed { i, f ->
            f.level to FloorVisual(
                z = MapCameraMath.floorZ(i, sortedFloors.size, selIdx),
                alpha = if (selIdx == null || selIdx == i) 1f else 0f
            )
        }.toMap()
        val targetCam = if (selIdx == null) {
            MapCameraMath.fit(planW, planH, targetVis.values.map { it.z }, isoTilt, isoSpin, w, h, latestInsets, fillWidth2D = false)
        } else {
            val spin = if (h > w) 90f else 0f // auto-rotate: portrait → rotate the wide plan
            MapCameraMath.fit(planW, planH, listOf(0f), 0f, spin, w, h, latestInsets, fillWidth2D = true)
        }
        fitScale = targetCam.scale
        val fromCam = camera
        val fromVis = floorVisuals
        if (fromCam == null) {
            camera = targetCam
            floorVisuals = targetVis
            return@LaunchedEffect
        }
        isTransitioning = true
        try {
            val progress = Animatable(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(MapCameraMath.TRANSITION_MS, easing = CubicBezierEasing(0.6f, 0f, 0.2f, 1f))
            ) {
                val k = value
                camera = MapCameraMath.lerp(fromCam, targetCam, k)
                floorVisuals = targetVis.mapValues { (level, to) ->
                    val from = fromVis[level] ?: to
                    val appearing = to.alpha > from.alpha
                    // appearing floors fade in slightly later, leaving floors fade out early
                    val ak = if (appearing) ((k - 0.2f) / 0.55f).coerceIn(0f, 1f) else (k / 0.55f).coerceIn(0f, 1f)
                    FloorVisual(from.z + (to.z - from.z) * k, from.alpha + (to.alpha - from.alpha) * ak)
                }
            }
        } finally {
            isTransitioning = false
        }
    }

    // Labels / pins / floor tags fade back in after each transition
    LaunchedEffect(isTransitioning) {
        if (isTransitioning) overlayAlpha.snapTo(0f) else overlayAlpha.animateTo(1f, tween(240))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "user_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    // When auto-rotate is enabled during navigation in 2D, smoothly align camera spin with user compass heading
    LaunchedEffect(isAutoRotateEnabled, userHeadingDegrees, selectedFloorLevel, isNavigating, isTransitioning) {
        if (isAutoRotateEnabled && isNavigating && selectedFloorLevel != null && !isTransitioning) {
            val c = camera ?: return@LaunchedEffect
            val targetSpin = (userHeadingDegrees + 90f) % 360f
            var diff = (targetSpin - c.spinDeg) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            if (kotlin.math.abs(diff) > 1.5f) {
                camera = c.copy(spinDeg = (c.spinDeg + diff * 0.28f + 360f) % 360f)
            }
        }
    }

    val is3dView = selectedFloorLevel == null

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(is3dView) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val c = camera ?: return@detectTransformGestures
                    if (isTransitioning) return@detectTransformGestures
                    var next = MapCameraMath.zoomAbout(
                        c, zoom, centroid, size.width.toFloat(), size.height.toFloat(),
                        minScale = fitScale * 0.6f, maxScale = fitScale * 7f
                    )
                    next = if (is3dView) {
                        val spin = next.spinDeg + pan.x / density * 0.35f
                        val tilt = (next.tiltDeg - pan.y / density * 0.3f).coerceIn(MapCameraMath.MIN_TILT, MapCameraMath.MAX_TILT)
                        isoSpin = spin
                        isoTilt = tilt
                        next.copy(spinDeg = spin, tiltDeg = tilt)
                    } else {
                        next.copy(tx = next.tx + pan.x, ty = next.ty + pan.y)
                    }
                    camera = next
                }
            }
            .pointerInput(selectedFloorLevel, sortedFloors) {
                detectTapGestures { p ->
                    val c = camera ?: return@detectTapGestures
                    if (isTransitioning) return@detectTapGestures
                    val pj = MapProjector(c, planW, planH, size.width.toFloat(), size.height.toFloat())
                    if (selectedFloorLevel == null) {
                        val hit = sortedFloors.asReversed().firstOrNull { f ->
                            val v = floorVisuals[f.level] ?: return@firstOrNull false
                            pointInPolygon(p, plateCorners(planW, planH).map { pj.project(it.x, it.y, v.z) })
                        }
                        if (hit != null) latestOnSelectFloor(hit.level) else latestOnEmptyTap()
                    } else {
                        val floor = sortedFloors.firstOrNull { it.level == selectedFloorLevel }
                        val world = pj.unproject(p.x, p.y)
                        val room = if (floor != null && !floor.isTodo && world != null) {
                            floor.rooms.lastOrNull { it.polygon.size >= 3 && pointInPolygon(world.x, world.y, it.polygon) }
                        } else null
                        if (room != null) latestOnRoomClick(room) else latestOnEmptyTap()
                    }
                }
            }
            .testTag("hospital_map_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(MapCanvasBackground)
            val cam = camera ?: return@Canvas
            val pj = MapProjector(cam, planW, planH, size.width, size.height)
            val oa = overlayAlpha.value

            for (floor in sortedFloors) {
                val v = floorVisuals[floor.level] ?: continue
                if (v.alpha < 0.01f) continue
                drawFloor(
                    floor = floor, vis = v, pj = pj, planW = planW, planH = planH,
                    route = currentRoute, startRoom = startRoom, destRoom = destRoom, selectedRoom = selectedRoom,
                    highlightPlate = is3dView && floor.level in routeLevels,
                    showTodoText = is3dView && oa > 0f, textMeasurer = textMeasurer, language = language, textAlpha = oa
                )
            }

            if (oa > 0f) {
                if (is3dView) {
                    drawShaftLinks(shafts, floorVisuals, pj, oa)
                    drawTransferLink(currentRoute, floorVisuals, pj, oa)
                }
                drawRoutePins(currentRoute, floorVisuals, pj, oa, elevatorPainter, stairsPainter)
                if (userPosition != null && userFloorLevel != null) {
                    drawUserLocationWithHeading(
                        position = userPosition,
                        floorLevel = userFloorLevel,
                        headingDegrees = userHeadingDegrees,
                        floorVisuals = floorVisuals,
                        pj = pj,
                        alpha = oa,
                        pulseProgress = pulseProgress
                    )
                }
                if (is3dView) {
                    drawFloorTags(sortedFloors, floorVisuals, routeLevels, pj, planW, planH, textMeasurer, language, oa)
                } else {
                    val floor = sortedFloors.firstOrNull { it.level == selectedFloorLevel }
                    if (floor != null && !floor.isTodo) {
                        drawRoomLabels(floor, labelTexts, pj, fitScale, textMeasurer, language, oa)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Floors
// -----------------------------------------------------------------------------

private fun plateCorners(planW: Float, planH: Float): List<Point2D> {
    val p = MapCameraMath.SLAB_PAD
    return listOf(Point2D(-p, -p), Point2D(planW + p, -p), Point2D(planW + p, planH + p), Point2D(-p, planH + p))
}

private fun polyPath(points: List<Point2D>, pj: MapProjector, z: Float): Path = Path().apply {
    points.forEachIndexed { i, pt ->
        val o = pj.project(pt.x, pt.y, z)
        if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
    }
    close()
}

private fun DrawScope.drawFloor(
    floor: Floor,
    vis: FloorVisual,
    pj: MapProjector,
    planW: Float,
    planH: Float,
    route: HospitalRoute?,
    startRoom: Room?,
    destRoom: Room?,
    selectedRoom: Room?,
    highlightPlate: Boolean,
    showTodoText: Boolean,
    textMeasurer: TextMeasurer,
    language: AppLanguage,
    textAlpha: Float
) {
    val a = vis.alpha
    val z = vis.z
    val corners = plateCorners(planW, planH)
    val plate = polyPath(corners, pj, z)

    if (floor.isTodo) {
        drawPath(plate, Color.White.copy(alpha = 0.45f), alpha = a)
        drawPath(
            plate, MapInkSubtle, alpha = a,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())))
        )
        if (showTodoText) {
            val c = pj.project(planW / 2f, planH / 2f, z)
            val text = "${HospitalLocalization.getFloorLabel(floor.level, language)} — " +
                HospitalLocalization.t(language, "chưa có sơ đồ", "no map yet")
            val layout = textMeasurer.measure(text, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MapInkMuted))
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f), alpha = a * textAlpha)
        }
        return
    }

    // 3D slab thickness (only visible while tilted)
    if (pj.cam.tiltDeg > 0.5f) drawPath(polyPath(corners, pj, z - MapCameraMath.SLAB_THICKNESS), MapSlabSide, alpha = a)
    drawPath(plate, Color.White, alpha = a)
    drawPath(
        plate, if (highlightPlate) MapBluePrimary else MapPlateStroke, alpha = a,
        style = Stroke(width = if (highlightPlate) 2.dp.toPx() else 1.dp.toPx())
    )

    val roomStroke = (2.5f * pj.cam.scale).coerceIn(0.5f, 3f)
    val highlighted = ArrayList<Pair<Path, Color>>(3)
    for (room in floor.rooms) {
        if (room.polygon.size < 3) continue
        val path = polyPath(room.polygon, pj, z)
        drawPath(path, cachedColor(room.colorHex, Color(0xFFCFE0E8)), alpha = a)
        val hc = when (room.id) {
            selectedRoom?.id -> MapBluePrimary
            destRoom?.id -> MapRedAlert
            startRoom?.id -> MapGreenSuccess
            else -> null
        }
        if (hc != null) highlighted += path to hc
        else drawPath(path, Color.White, alpha = a, style = Stroke(width = roomStroke, join = StrokeJoin.Round))
    }
    highlighted.forEach { (path, color) ->
        drawPath(path, color, alpha = a, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    }

    route?.segments?.filter { it.floorLevel == floor.level && it.points.size >= 2 }?.forEach { seg ->
        val path = Path()
        seg.points.forEachIndexed { i, pt ->
            val o = pj.project(pt.x, pt.y, z)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, Color.White, alpha = a, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, MapBluePrimary, alpha = a, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// -----------------------------------------------------------------------------
// 3D-only overlays
// -----------------------------------------------------------------------------

private class ShaftLink(val lowLevel: Int, val highLevel: Int, val low: Room, val high: Room)

private fun connectingShafts(sorted: List<Floor>): List<ShaftLink> {
    val out = mutableListOf<ShaftLink>()
    for (i in 0 until sorted.size - 1) {
        val lo = sorted[i]; val hi = sorted[i + 1]
        if (lo.isTodo || hi.isTodo) continue
        val a = lo.rooms.filter { it.link != null }.associateBy { it.link!! }
        val b = hi.rooms.filter { it.link != null }.associateBy { it.link!! }
        a.keys.intersect(b.keys).forEach { k -> out += ShaftLink(lo.level, hi.level, a.getValue(k), b.getValue(k)) }
    }
    return out
}

private fun roomCenter(room: Room): Point2D = room.labelAt ?: Point2D(
    room.polygon.map { it.x }.average().toFloat(),
    room.polygon.map { it.y }.average().toFloat()
)

private fun DrawScope.drawShaftLinks(links: List<ShaftLink>, vis: Map<Int, FloorVisual>, pj: MapProjector, alpha: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    for (l in links) {
        val vl = vis[l.lowLevel] ?: continue
        val vh = vis[l.highLevel] ?: continue
        val cl = roomCenter(l.low); val ch = roomCenter(l.high)
        drawLine(
            color = if (l.low.kind == "elevator") MapElevatorGreen else MapAmber,
            start = pj.project(cl.x, cl.y, vl.z), end = pj.project(ch.x, ch.y, vh.z),
            strokeWidth = 1.5.dp.toPx(), pathEffect = dash, alpha = 0.7f * alpha * min(vl.alpha, vh.alpha)
        )
    }
}

private fun DrawScope.drawTransferLink(route: HospitalRoute?, vis: Map<Int, FloorVisual>, pj: MapProjector, alpha: Float) {
    val t = route?.transfer ?: return
    val va = vis[t.roomA.floorLevel] ?: return
    val vb = vis[t.roomB.floorLevel] ?: return
    val ca = roomCenter(t.roomA); val cb = roomCenter(t.roomB)
    val a = pj.project(ca.x, ca.y, va.z); val b = pj.project(cb.x, cb.y, vb.z)
    drawLine(Color.White, a, b, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round, alpha = alpha)
    drawLine(MapBluePrimary, a, b, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, alpha = alpha)
}

private fun DrawScope.drawFloorTags(
    floors: List<Floor>,
    vis: Map<Int, FloorVisual>,
    routeLevels: Set<Int>,
    pj: MapProjector,
    planW: Float,
    planH: Float,
    tm: TextMeasurer,
    language: AppLanguage,
    alpha: Float
) {
    for (f in floors) {
        val v = vis[f.level] ?: continue
        if (v.alpha < 0.5f) continue
        val left = plateCorners(planW, planH).map { pj.project(it.x, it.y, v.z) }.minByOrNull { it.x } ?: continue
        val onRoute = f.level in routeLevels
        val label = if (f.isTodo) HospitalLocalization.t(language, "Chưa có sơ đồ", "No map yet")
        else HospitalLocalization.t(language, "${f.rooms.size} phòng", "${f.rooms.size} rooms")
        val textLayout = tm.measure(label, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (f.isTodo) MapInkMuted else MapInk))
        val numLayout = tm.measure("${f.level}", TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (onRoute) Color.White else MapInk))
        val h = 28.dp.toPx(); val badge = 20.dp.toPx(); val padL = 4.dp.toPx(); val gap = 6.dp.toPx(); val padR = 10.dp.toPx()
        val w = padL + badge + gap + textLayout.size.width + padR
        val x = max(8.dp.toPx(), left.x - 4.dp.toPx())
        val y = left.y - 10.dp.toPx() - h
        drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(x, y + 1.dp.toPx()), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f), alpha = alpha)
        drawRoundRect(Color.White, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f), alpha = alpha)
        if (onRoute) drawRoundRect(MapBluePrimary, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f), style = Stroke(1.5.dp.toPx()), alpha = alpha)
        val bc = Offset(x + padL + badge / 2f, y + h / 2f)
        drawCircle(if (onRoute) MapBluePrimary else Color(0xFFE8EAED), badge / 2f, bc, alpha = alpha)
        drawText(numLayout, topLeft = Offset(bc.x - numLayout.size.width / 2f, bc.y - numLayout.size.height / 2f), alpha = alpha)
        drawText(textLayout, topLeft = Offset(x + padL + badge + gap, y + (h - textLayout.size.height) / 2f), alpha = alpha)
    }
}

// -----------------------------------------------------------------------------
// Pins (both views)
// -----------------------------------------------------------------------------

private fun DrawScope.drawRoutePins(
    route: HospitalRoute?,
    vis: Map<Int, FloorVisual>,
    pj: MapProjector,
    alpha: Float,
    elevatorPainter: VectorPainter,
    stairsPainter: VectorPainter
) {
    val r = route ?: return
    val first = r.segments.firstOrNull() ?: return
    val last = r.segments.last()
    fun visible(level: Int) = (vis[level]?.alpha ?: 0f) > 0.5f
    fun z(level: Int) = vis[level]?.z ?: 0f

    r.transfer?.let { t ->
        val painter = if (t.kind == "elevator") elevatorPainter else stairsPainter
        listOf(t.roomA, t.roomB).forEach { room ->
            if (!visible(room.floorLevel)) return@forEach
            val c = roomCenter(room)
            val o = pj.project(c.x, c.y, z(room.floorLevel))
            drawCircle(Color.White, 13.dp.toPx(), o, alpha = alpha)
            drawCircle(MapBluePrimary, 13.dp.toPx(), o, style = Stroke(2.dp.toPx()), alpha = alpha)
            val s = 16.dp.toPx()
            translate(o.x - s / 2f, o.y - s / 2f) {
                with(painter) { draw(Size(s, s), alpha = alpha, colorFilter = ColorFilter.tint(MapBluePrimary)) }
            }
        }
    }
    if (visible(first.floorLevel) && first.points.isNotEmpty()) {
        val p = first.points.first()
        val o = pj.project(p.x, p.y, z(first.floorLevel))
        drawCircle(Color.Black.copy(alpha = 0.18f), 10.dp.toPx(), o + Offset(0f, 1.dp.toPx()), alpha = alpha)
        drawCircle(Color.White, 9.dp.toPx(), o, alpha = alpha)
        drawCircle(MapGreenSuccess, 6.dp.toPx(), o, alpha = alpha)
    }
    if (visible(last.floorLevel) && last.points.isNotEmpty()) {
        val p = last.points.last()
        val o = pj.project(p.x, p.y, z(last.floorLevel))
        val rr = 10.dp.toPx()
        val head = Offset(o.x, o.y - 22.dp.toPx())
        val tail = Path().apply {
            moveTo(head.x - rr * 0.8f, head.y + rr * 0.55f)
            lineTo(o.x, o.y)
            lineTo(head.x + rr * 0.8f, head.y + rr * 0.55f)
            close()
        }
        drawPath(tail, MapRedAlert, alpha = alpha)
        drawCircle(MapRedAlert, rr, head, alpha = alpha)
        drawCircle(Color.White, rr * 0.38f, head, alpha = alpha)
    }
}

private fun DrawScope.drawUserLocationWithHeading(
    position: Point2D,
    floorLevel: Int,
    headingDegrees: Float,
    floorVisuals: Map<Int, FloorVisual>,
    pj: MapProjector,
    alpha: Float,
    pulseProgress: Float
) {
    val vis = floorVisuals[floorLevel] ?: return
    if (vis.alpha < 0.2f) return

    val z = vis.z
    val centerScreen = pj.project(position.x, position.y, z)

    // Calculate heading vector in plan coordinates: 0° = North (-Y), 90° = East (+X)
    val rad = Math.toRadians(headingDegrees.toDouble())
    val forwardX = position.x + (30f * kotlin.math.sin(rad)).toFloat()
    val forwardY = position.y - (30f * kotlin.math.cos(rad)).toFloat()
    val forwardScreen = pj.project(forwardX, forwardY, z)

    val dirX = forwardScreen.x - centerScreen.x
    val dirY = forwardScreen.y - centerScreen.y
    val screenAngleRad = kotlin.math.atan2(dirY, dirX)

    val combinedAlpha = alpha * vis.alpha

    // 1. Directional Flashlight Beam / Heading Cone (Cơ chế xoay chỉ hướng theo cảm biến điện thoại)
    val beamRadius = 48.dp.toPx()
    val coneHalfAngle = Math.toRadians(32.0).toFloat()
    val leftAngle = screenAngleRad - coneHalfAngle
    val rightAngle = screenAngleRad + coneHalfAngle

    val beamPath = Path().apply {
        moveTo(centerScreen.x, centerScreen.y)
        val steps = 8
        for (i in 0..steps) {
            val a = leftAngle + (rightAngle - leftAngle) * (i.toFloat() / steps)
            val bx = centerScreen.x + beamRadius * kotlin.math.cos(a)
            val by = centerScreen.y + beamRadius * kotlin.math.sin(a)
            lineTo(bx, by)
        }
        close()
    }

    // Cone radial gradient: vivid soft blue at origin, fading to transparent at outer perimeter
    drawPath(
        path = beamPath,
        brush = Brush.radialGradient(
            colors = listOf(
                MapBluePrimary.copy(alpha = 0.58f * combinedAlpha),
                MapBluePrimary.copy(alpha = 0.20f * combinedAlpha),
                Color.Transparent
            ),
            center = centerScreen,
            radius = beamRadius
        )
    )

    // Heading central beam pointer
    val tipX = centerScreen.x + (beamRadius * 0.72f) * kotlin.math.cos(screenAngleRad)
    val tipY = centerScreen.y + (beamRadius * 0.72f) * kotlin.math.sin(screenAngleRad)
    drawLine(
        color = MapBluePrimary.copy(alpha = 0.85f * combinedAlpha),
        start = centerScreen,
        end = Offset(tipX, tipY),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round
    )

    // 2. Animated Radar Pulse Wave
    val maxPulseRadius = 26.dp.toPx()
    val pulseR = 8.dp.toPx() + (maxPulseRadius - 8.dp.toPx()) * pulseProgress
    val pulseA = (1f - pulseProgress).coerceIn(0f, 1f) * 0.45f * combinedAlpha
    drawCircle(
        color = MapBluePrimary.copy(alpha = pulseA),
        radius = pulseR,
        center = centerScreen
    )

    // 3. User Location Dot with high-contrast halo
    drawCircle(
        color = Color.Black.copy(alpha = 0.22f * combinedAlpha),
        radius = 12.dp.toPx(),
        center = centerScreen + Offset(0f, 1.5.dp.toPx())
    )
    drawCircle(
        color = Color.White,
        radius = 10.5.dp.toPx(),
        center = centerScreen,
        alpha = combinedAlpha
    )
    drawCircle(
        color = MapBluePrimary,
        radius = 7.5.dp.toPx(),
        center = centerScreen,
        alpha = combinedAlpha
    )

    // 4. Directional Navigation Chevron / Pointer right on the location dot
    val arrowLen = 6.dp.toPx()
    val arrowWidth = 4.5.dp.toPx()
    val arrowTipX = centerScreen.x + arrowLen * kotlin.math.cos(screenAngleRad)
    val arrowTipY = centerScreen.y + arrowLen * kotlin.math.sin(screenAngleRad)
    val leftRad = screenAngleRad + Math.toRadians(140.0).toFloat()
    val rightRad = screenAngleRad - Math.toRadians(140.0).toFloat()
    val leftX = centerScreen.x + arrowWidth * kotlin.math.cos(leftRad)
    val leftY = centerScreen.y + arrowWidth * kotlin.math.sin(leftRad)
    val rightX = centerScreen.x + arrowWidth * kotlin.math.cos(rightRad)
    val rightY = centerScreen.y + arrowWidth * kotlin.math.sin(rightRad)

    val chevronPath = Path().apply {
        moveTo(arrowTipX, arrowTipY)
        lineTo(leftX, leftY)
        lineTo(
            centerScreen.x - (arrowLen * 0.25f) * kotlin.math.cos(screenAngleRad),
            centerScreen.y - (arrowLen * 0.25f) * kotlin.math.sin(screenAngleRad)
        )
        lineTo(rightX, rightY)
        close()
    }
    drawPath(chevronPath, Color.White, alpha = combinedAlpha)
}

// -----------------------------------------------------------------------------
// 2D labels — upright, sized by zoom, hidden when they don't fit their room
// -----------------------------------------------------------------------------

private fun DrawScope.drawRoomLabels(
    floor: Floor,
    labelTexts: Map<String, String>,
    pj: MapProjector,
    fitScale: Float,
    tm: TextMeasurer,
    language: AppLanguage,
    alpha: Float
) {
    val ratio = pj.cam.scale / fitScale
    val fs = (9f + (ratio - 1f) * 2.2f).coerceIn(9f, 13f)
    for (room in floor.rooms) {
        if (room.name.isBlank() || room.polygon.size < 3) continue
        val text = labelTexts[room.id] ?: continue
        val box = projectedBox(room.boundsLeft, room.boundsTop, room.boundsLeft + room.boundsWidth, room.boundsTop + room.boundsHeight, pj)
        val c = room.labelAt?.let { pj.project(it.x, it.y, 0f) } ?: Offset((box[0] + box[2]) / 2f, (box[1] + box[3]) / 2f)
        val bw = if (room.labelAt != null) 2f * min(c.x - box[0], box[2] - c.x) else box[2] - box[0]
        val bh = if (room.labelAt != null) 2f * min(c.y - box[1], box[3] - c.y) else box[3] - box[1]
        val style = TextStyle(
            fontSize = fs.sp, lineHeight = (fs * 1.15f).sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, color = cachedColor(room.textColorHex, MapInk)
        )
        drawFittedLabel(tm, text, c, bw, bh, style, alpha)
    }
    for (label in floor.labels) {
        val box = projectedBox(label.x - label.w / 2f, label.y - label.h / 2f, label.x + label.w / 2f, label.y + label.h / 2f, pj)
        val c = pj.project(label.x, label.y, 0f)
        val text = HospitalLocalization.sentenceCase(
            if (language == AppLanguage.ENGLISH && label.nameEn.isNotBlank()) label.nameEn else label.name
        ).uppercase()
        val style = TextStyle(
            fontSize = (fs - 1f).sp, lineHeight = (fs * 1.1f).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center, color = Color(0xFF3C4043),
            shadow = Shadow(Color.White, Offset.Zero, blurRadius = 6f)
        )
        drawFittedLabel(tm, text, c, box[2] - box[0], box[3] - box[1], style, alpha)
    }
}

private fun projectedBox(x0: Float, y0: Float, x1: Float, y1: Float, pj: MapProjector): FloatArray {
    val pts = listOf(pj.project(x0, y0, 0f), pj.project(x1, y0, 0f), pj.project(x1, y1, 0f), pj.project(x0, y1, 0f))
    return floatArrayOf(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
}

private fun DrawScope.drawFittedLabel(
    tm: TextMeasurer,
    text: String,
    center: Offset,
    boxW: Float,
    boxH: Float,
    style: TextStyle,
    alpha: Float
) {
    val margin = 40.dp.toPx()
    if (center.x < -margin || center.y < -margin || center.x > size.width + margin || center.y > size.height + margin) return
    if (boxW < 24.dp.toPx() || boxH < 12.dp.toPx()) return
    // quantize width so the TextMeasurer cache is hit while panning/zooming
    val step = 8
    val maxW = ((boxW - 4.dp.toPx()).toInt() / step) * step
    if (maxW <= 0) return
    val layout = tm.measure(
        text = text, style = style, overflow = TextOverflow.Clip, softWrap = true, maxLines = 3,
        constraints = Constraints(maxWidth = maxW)
    )
    if (layout.hasVisualOverflow || layout.size.height > boxH - 2f) return
    // never break inside a word — hide the label instead
    for (i in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(i)
        if (end in 1 until text.length && text[end - 1] != ' ' && text[end] != ' ') return
    }
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f), alpha = alpha)
}

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

private val colorCache = HashMap<String, Color>()

private fun cachedColor(hex: String, fallback: Color): Color =
    colorCache.getOrPut(hex) {
        try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }
    }

private fun pointInPolygon(x: Float, y: Float, poly: List<Point2D>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val pi = poly[i]; val pj = poly[j]
        if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
        j = i
    }
    return inside
}

private fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]; val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}
