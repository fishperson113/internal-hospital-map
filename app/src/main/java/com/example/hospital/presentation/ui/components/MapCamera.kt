package com.example.hospital.presentation.ui.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * One orthographic camera drives BOTH views:
 *  - "Tất cả" (all floors) = isometric stack: tilt ≈ 56°, spin ≈ -32°, floors spread on Z.
 *  - Single floor 2D      = tilt 0°, spin 90° on portrait screens (0° on landscape).
 * The 3D → 2D transition is just an interpolation of every field below, so floors
 * physically "fly" from the stack down into the flat plan.
 */
data class MapCamera(
    val tiltDeg: Float,
    val spinDeg: Float,
    val scale: Float,
    val tx: Float,
    val ty: Float
)

/** Screen area (px) covered by floating UI; the camera fits the plan inside what is left. */
data class MapViewportInsets(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val start: Float = 0f,
    val end: Float = 0f
)

/** Per-floor animated elevation (world units) and opacity. */
data class FloorVisual(val z: Float, val alpha: Float)

object MapCameraMath {
    const val ISO_TILT = 56f
    const val ISO_SPIN = -32f
    const val MIN_TILT = 28f
    const val MAX_TILT = 78f
    const val FLOOR_GAP = 680f
    const val SLAB_PAD = 18f
    const val SLAB_THICKNESS = 22f
    const val TRANSITION_MS = 900

    /** Z of floor [index] (sorted bottom → top). Selected floor sits at z = 0; others are pushed away. */
    fun floorZ(index: Int, count: Int, selectedIndex: Int?): Float =
        if (selectedIndex == null) (index - (count - 1) / 2f) * FLOOR_GAP
        else (index - selectedIndex) * FLOOR_GAP * 1.5f

    fun lerp(a: MapCamera, b: MapCamera, t: Float): MapCamera {
        var ds = (b.spinDeg - a.spinDeg) % 360f
        if (ds > 180f) ds -= 360f
        if (ds < -180f) ds += 360f
        return MapCamera(
            tiltDeg = a.tiltDeg + (b.tiltDeg - a.tiltDeg) * t,
            spinDeg = a.spinDeg + ds * t,
            // geometric zoom interpolation feels linear to the eye
            scale = a.scale * (b.scale / a.scale).pow(t),
            tx = a.tx + (b.tx - a.tx) * t,
            ty = a.ty + (b.ty - a.ty) * t
        )
    }

    /**
     * Camera that fits the floor plate(s) at the given Z levels inside the free viewport.
     * [fillWidth2D]: in 2D let the plan be taller than the free area (it slides under the
     * floating cards and can be panned), which makes it much larger on a phone.
     */
    fun fit(
        planW: Float,
        planH: Float,
        zLevels: List<Float>,
        tiltDeg: Float,
        spinDeg: Float,
        viewW: Float,
        viewH: Float,
        insets: MapViewportInsets,
        fillWidth2D: Boolean
    ): MapCamera {
        val a = Math.toRadians(spinDeg.toDouble())
        val t = Math.toRadians(tiltDeg.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val ct = cos(t).toFloat(); val st = sin(t).toFloat()
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE
        var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        val p = SLAB_PAD
        val corners = listOf(-p to -p, planW + p to -p, planW + p to planH + p, -p to planH + p)
        for (z in zLevels.ifEmpty { listOf(0f) }) {
            for ((x, y) in corners) {
                val qx = x - planW / 2f; val qy = y - planH / 2f
                val rx = qx * ca - qy * sa
                val ry = qx * sa + qy * ca
                val vy = ry * ct - z * st
                x0 = min(x0, rx); x1 = max(x1, rx); y0 = min(y0, vy); y1 = max(y1, vy)
            }
        }
        val aw = (viewW - insets.start - insets.end).coerceAtLeast(1f)
        val ah = (viewH - insets.top - insets.bottom).coerceAtLeast(1f)
        val bw = (x1 - x0).coerceAtLeast(1f)
        val bh = (y1 - y0).coerceAtLeast(1f)
        val s = if (fillWidth2D) min(aw / bw, max(ah, viewH * 0.8f) / bh) else min(aw / bw, ah / bh)
        val cx = insets.start + aw / 2f
        val cy = insets.top + ah / 2f
        return MapCamera(
            tiltDeg = tiltDeg,
            spinDeg = spinDeg,
            scale = s,
            tx = cx - viewW / 2f - s * (x0 + x1) / 2f,
            ty = cy - viewH / 2f - s * (y0 + y1) / 2f
        )
    }

    fun zoomAbout(cam: MapCamera, factor: Float, focus: Offset, viewW: Float, viewH: Float, minScale: Float, maxScale: Float): MapCamera {
        val s = (cam.scale * factor).coerceIn(minScale, maxScale)
        val r = s / cam.scale
        return cam.copy(
            scale = s,
            tx = focus.x - viewW / 2f - r * (focus.x - viewW / 2f - cam.tx),
            ty = focus.y - viewH / 2f - r * (focus.y - viewH / 2f - cam.ty)
        )
    }
}

/** Projects plan coordinates (+ floor elevation z) to screen pixels for one frame. */
class MapProjector(
    val cam: MapCamera,
    private val planW: Float,
    private val planH: Float,
    viewW: Float,
    viewH: Float
) {
    private val ca = cos(Math.toRadians(cam.spinDeg.toDouble())).toFloat()
    private val sa = sin(Math.toRadians(cam.spinDeg.toDouble())).toFloat()
    private val ct = cos(Math.toRadians(cam.tiltDeg.toDouble())).toFloat()
    private val st = sin(Math.toRadians(cam.tiltDeg.toDouble())).toFloat()
    private val ox = viewW / 2f + cam.tx
    private val oy = viewH / 2f + cam.ty

    fun project(x: Float, y: Float, z: Float): Offset {
        val qx = x - planW / 2f
        val qy = y - planH / 2f
        val rx = qx * ca - qy * sa
        val ry = qx * sa + qy * ca
        return Offset(ox + cam.scale * rx, oy + cam.scale * (ry * ct - z * st))
    }

    /** Screen → plan coordinates on the plane at height z (the selected floor is z = 0). Null when edge-on. */
    fun unproject(sx: Float, sy: Float, z: Float = 0f): Offset? {
        if (ct < 0.05f) return null
        val vx = (sx - ox) / cam.scale
        val ry = ((sy - oy) / cam.scale + z * st) / ct
        val qx = vx * ca + ry * sa
        val qy = -vx * sa + ry * ca
        return Offset(qx + planW / 2f, qy + planH / 2f)
    }
}
