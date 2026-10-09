package com.example.hospital.presentation.ui.components

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.ui.theme.MapBluePrimary
import com.example.ui.theme.MapCanvasBackground
import com.example.ui.theme.MapGreenSuccess
import com.example.ui.theme.MapRedAlert
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * OpenGL ES 2.0 port of the web renderer (MLAI_Hackathon src/web/gl.js): rooms extruded into low 3D
 * blocks, the route as a ribbon with chevrons flowing to the destination, light beams over the
 * destination and the floor-change shaft. Same orthographic camera as [MapProjector], so the Compose
 * overlay (labels, pins, dot) drawn on top stays aligned.
 *
 * Batch rendering: each floor is ONE vertex buffer built once per data load and drawn with ONE draw
 * call. Selection highlight, on-route plate border and fades are shader uniforms, so tapping a room or
 * moving the camera never rebuilds geometry. Route geometry is rebuilt only when the route changes.
 */
object MapGlMesh {
    const val ROOM_H = 26f  // room block height (plan units): low enough to see the route behind
    const val STRIDE = 12   // floats per vertex: pos(3) off(2) col(4) info(2) dist(1)
    // info.x: room index (>= 0), -2 plate border, -1 anything else; beams: side (-1..1).
    // info.y: route side (-1..1) or beam height (0..1). dist: plan units along the route.
    // off: offset in dp along a plan direction (ribbons) or along the screen x axis (beams).

    class Buf {
        var a = FloatArray(4096); var n = 0
        fun vert(x: Float, y: Float, z: Float, ox: Float, oy: Float, col: FloatArray, ix: Float, iy: Float, dist: Float = 0f) {
            if (n + STRIDE > a.size) a = a.copyOf(a.size * 2)
            a[n++] = x; a[n++] = y; a[n++] = z; a[n++] = ox; a[n++] = oy
            a[n++] = col[0]; a[n++] = col[1]; a[n++] = col[2]; a[n++] = col[3]
            a[n++] = ix; a[n++] = iy; a[n++] = dist
        }
        fun toArray(): FloatArray = a.copyOf(n)
    }

    fun hex(h: String?, alpha: Float = 1f): FloatArray {
        val n = (h ?: "").removePrefix("#").padEnd(6, '0').take(6).toIntOrNull(16) ?: 0xCFE0E8
        return floatArrayOf((n shr 16 and 255) / 255f, (n shr 8 and 255) / 255f, (n and 255) / 255f, alpha)
    }

    private fun shade(c: FloatArray, k: Float) = floatArrayOf(c[0] * k, c[1] * k, c[2] * k, c[3])

    fun poly(out: Buf, pts: List<Point2D>, z: Float, col: FloatArray, id: Float) {
        val tri = triangulate(pts)
        for (i in tri) out.vert(pts[i].x, pts[i].y, z, 0f, 0f, col, id, 0f)
    }

    /** Vertical walls along a closed ring, lit by a fixed light so the sides read as a solid block. */
    fun walls(out: Buf, pts: List<Point2D>, z0: Float, z1: Float, col: FloatArray, id: Float) {
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            val len = hypot(b.x - a.x, b.y - a.y).takeIf { it > 0f } ?: 1f
            val c = shade(col, 0.62f + 0.25f * abs((b.y - a.y) / len * 0.8f + (b.x - a.x) / len * 0.6f))
            out.vert(a.x, a.y, z0, 0f, 0f, c, id, 0f); out.vert(b.x, b.y, z0, 0f, 0f, c, id, 0f); out.vert(b.x, b.y, z1, 0f, 0f, c, id, 0f)
            out.vert(a.x, a.y, z0, 0f, 0f, c, id, 0f); out.vert(b.x, b.y, z1, 0f, 0f, c, id, 0f); out.vert(a.x, a.y, z1, 0f, 0f, c, id, 0f)
        }
    }

    /** A line of constant screen width (half width [hw] dp) at height z, round joints. Returns its length. */
    fun ribbon(out: Buf, pts: List<Point2D>, z: Float, hw: Float, col: FloatArray, id: Float, closed: Boolean, withDist: Boolean): Float {
        val n = pts.size
        val segs = if (closed) n else n - 1
        var d = 0f
        for (i in 0 until segs) {
            val a = pts[i]; val b = pts[(i + 1) % n]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len == 0f) continue
            val nx = -(b.y - a.y) / len * hw; val ny = (b.x - a.x) / len * hw; val d1 = d + len
            fun v(p: Point2D, s: Float, dd: Float) = out.vert(p.x, p.y, z, nx * s, ny * s, col, id, if (withDist) s else 0f, if (withDist) dd else 0f)
            v(a, -1f, d); v(b, -1f, d1); v(b, 1f, d1)
            v(a, -1f, d); v(b, 1f, d1); v(a, 1f, d)
            d = d1
            disc(out, b, z, hw, col, id, d, withDist)
        }
        if (!closed && n > 0) disc(out, pts[0], z, hw, col, id, 0f, withDist)
        return d
    }

    private fun disc(out: Buf, p: Point2D, z: Float, r: Float, col: FloatArray, id: Float, dist: Float, withDist: Boolean) {
        val side = if (withDist) 0.9f else 0f // 0.9 keeps chevrons off the joint discs (shader: |side| < 0.8)
        for (k in 0 until 8) {
            val a0 = k / 8f * 2f * Math.PI.toFloat(); val a1 = (k + 1) / 8f * 2f * Math.PI.toFloat()
            out.vert(p.x, p.y, z, 0f, 0f, col, id, side, dist)
            out.vert(p.x, p.y, z, cos(a0) * r, sin(a0) * r, col, id, side, dist)
            out.vert(p.x, p.y, z, cos(a1) * r, sin(a1) * r, col, id, side, dist)
        }
    }

    /** Screen-facing vertical quad: half width [hw] dp, from the floor up to height h (plan units). */
    private fun beam(out: Buf, p: Point2D, hw: Float, h: Float, col: FloatArray) {
        fun v(s: Float, t: Float) = out.vert(p.x, p.y, t * h, s * hw, 0f, col, s, t)
        v(-1f, 0f); v(1f, 0f); v(1f, 1f); v(-1f, 0f); v(1f, 1f); v(-1f, 1f)
    }

    /** One floor -> one vertex array (drawn as one batch). Room info.x = index in [Floor.rooms]. */
    fun floorMesh(f: Floor, planW: Float, planH: Float): FloatArray {
        val out = Buf()
        val p = MapCameraMath.SLAB_PAD
        val plate = listOf(Point2D(-p, -p), Point2D(planW + p, -p), Point2D(planW + p, planH + p), Point2D(-p, planH + p))
        if (f.isTodo) { // no map yet: a see-through plate
            poly(out, plate, 0f, floatArrayOf(1f, 1f, 1f, 0.45f), -1f)
            ribbon(out, plate, 0.5f, 1f, hex("#9aa0a6"), -1f, closed = true, withDist = false)
            return out.toArray()
        }
        val slab = hex("#dfe3e8")
        poly(out, plate, -MapCameraMath.SLAB_THICKNESS, slab, -1f)
        walls(out, plate, -MapCameraMath.SLAB_THICKNESS, 0f, slab, -1f)
        poly(out, plate, 0f, floatArrayOf(1f, 1f, 1f, 1f), -1f)
        ribbon(out, plate, 0.5f, 1.5f, hex("#c4c7c5"), -2f, closed = true, withDist = false)
        f.rooms.forEachIndexed { i, room ->
            if (room.polygon.size < 3) return@forEachIndexed
            val col = hex(room.colorHex)
            val id = i.toFloat()
            walls(out, room.polygon, 0f, ROOM_H, col, id)
            poly(out, room.polygon, ROOM_H, col, id)
            ribbon(out, room.polygon, ROOM_H + 0.3f, 1.1f, floatArrayOf(1f, 1f, 1f, 1f), id, closed = true, withDist = false)
        }
        return out.toArray()
    }

    class RouteMesh(val casing: FloatArray?, val line: FloatArray?, val beams: FloatArray?)

    /** Route geometry per floor level: white casing, blue line with flow, beams (destination red, shaft blue). */
    fun routeMeshes(route: HospitalRoute?): Map<Int, RouteMesh> {
        val segs = route?.segments?.filter { it.points.size >= 2 } ?: return emptyMap()
        if (segs.isEmpty()) return emptyMap()
        val beams = HashMap<Int, Buf>()
        fun addBeam(level: Int, at: Point2D, color: String) = beam(beams.getOrPut(level) { Buf() }, at, 16f, 150f, hex(color, 0.55f))
        addBeam(segs.last().floorLevel, segs.last().points.last(), "#d93025")
        route?.transfer?.let { t ->
            addBeam(t.roomA.floorLevel, roomCenter(t.roomA.polygon, t.roomA.labelAt), "#1a73e8")
            addBeam(t.roomB.floorLevel, roomCenter(t.roomB.polygon, t.roomB.labelAt), "#1a73e8")
        }
        val levels = segs.map { it.floorLevel }.toSet() + beams.keys
        return levels.associateWith { level ->
            val casing = Buf(); val line = Buf()
            for (s in segs) if (s.floorLevel == level) {
                ribbon(casing, s.points, 1.5f, 7f, floatArrayOf(1f, 1f, 1f, 1f), -1f, closed = false, withDist = false)
                ribbon(line, s.points, 2f, 4.5f, hex("#1a73e8"), -1f, closed = false, withDist = true)
            }
            RouteMesh(casing.toArray().takeIf { it.isNotEmpty() }, line.toArray().takeIf { it.isNotEmpty() }, beams[level]?.toArray())
        }
    }

    private fun roomCenter(poly: List<Point2D>, at: Point2D?): Point2D =
        at ?: Point2D(poly.map { it.x }.average().toFloat(), poly.map { it.y }.average().toFloat())

    /** Ear clipping (the web uses earcut). Returns vertex indices, 3 per triangle. */
    fun triangulate(pts: List<Point2D>): IntArray {
        val n = pts.size
        if (n < 3) return IntArray(0)
        var area = 0f
        for (i in 0 until n) { val a = pts[i]; val b = pts[(i + 1) % n]; area += a.x * b.y - b.x * a.y }
        val idx = ArrayList<Int>(n).apply { for (i in 0 until n) add(if (area >= 0f) i else n - 1 - i) }
        fun cross(a: Point2D, b: Point2D, c: Point2D) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        val out = ArrayList<Int>((n - 2) * 3)
        var guard = 0
        while (idx.size > 3 && guard < idx.size) {
            var cut = false
            for (k in idx.indices) {
                val i0 = idx[(k + idx.size - 1) % idx.size]; val i1 = idx[k]; val i2 = idx[(k + 1) % idx.size]
                val a = pts[i0]; val b = pts[i1]; val c = pts[i2]
                val cr = cross(a, b, c)
                if (cr < 0f) continue // reflex corner
                if (cr > 0f && idx.any { j ->
                        pts[j] != a && pts[j] != b && pts[j] != c && cross(a, b, pts[j]) >= 0f && cross(b, c, pts[j]) >= 0f && cross(c, a, pts[j]) >= 0f
                    }) continue
                if (cr > 0f) out += listOf(i0, i1, i2) // collinear corner: drop it, no triangle
                idx.removeAt(k)
                cut = true
                break
            }
            // ponytail: self-intersecting rings leave no ear; fan the rest instead of a full earcut port
            if (!cut) guard = idx.size
        }
        for (k in 1 until idx.size - 1) out += listOf(idx[0], idx[k], idx[k + 1])
        return out.toIntArray()
    }
}

/**
 * GL thread side. Compose hands over meshes and [Frame]s through volatile fields; the GL thread uploads
 * a mesh the first time it sees it (and again after the EGL context is lost).
 */
class MapGlRenderer : GLSurfaceView.Renderer {
    /** hl: room indexes on this floor: selected, destination, start (-9 = none). */
    data class FloorView(val level: Int, val z: Float, val alpha: Float, val onRoute: Boolean, val hlSel: Int, val hlTo: Int, val hlFrom: Int)
    data class Frame(
        val cam: MapCamera, val w: Float, val h: Float, val planW: Float, val planH: Float,
        val density: Float, val raise: Float, val xray: Boolean, val floors: List<FloorView>
    )

    @Volatile var floorMeshes: Map<Int, FloatArray> = emptyMap()
    @Volatile var routeMeshes: Map<Int, MapGlMesh.RouteMesh> = emptyMap()
    @Volatile var frame: Frame? = null

    private class Vbo(val id: Int, val n: Int)
    private var shownFloors: Map<Int, FloatArray>? = null
    private var shownRoute: Map<Int, MapGlMesh.RouteMesh>? = null
    private var floorVbo: Map<Int, Vbo> = emptyMap()
    private var routeVbo: Map<Int, Triple<Vbo?, Vbo?, Vbo?>> = emptyMap()
    private var prog = 0
    private val u = HashMap<String, Int>()
    private val attrs = IntArray(5)
    private val t0 = SystemClock.uptimeMillis()

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // new context: old buffer ids are gone, upload everything again
        shownFloors = null; shownRoute = null; floorVbo = emptyMap(); routeVbo = emptyMap()
        fun sh(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
            val ok = IntArray(1); GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES20.glGetShaderInfoLog(it) }
        }
        prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, sh(GLES20.GL_VERTEX_SHADER, VS))
        GLES20.glAttachShader(prog, sh(GLES20.GL_FRAGMENT_SHADER, FS))
        GLES20.glLinkProgram(prog)
        GLES20.glUseProgram(prog)
        for (n in listOf("uCam", "uRot", "uView", "uZ", "uHl", "uMode", "uAlpha", "uTime", "uOnRoute", "uHlCol", "uDp")) u[n] = GLES20.glGetUniformLocation(prog, n)
        listOf("aPos", "aOff", "aCol", "aInfo", "aDist").forEachIndexed { i, n ->
            attrs[i] = GLES20.glGetAttribLocation(prog, n); GLES20.glEnableVertexAttribArray(attrs[i])
        }
        val hl = listOf(MapBluePrimary, MapRedAlert, MapGreenSuccess).flatMap { listOf(it.red, it.green, it.blue) }.toFloatArray()
        GLES20.glUniform3fv(u.getValue("uHlCol"), 3, hl, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glClearColor(MapCanvasBackground.red, MapCanvasBackground.green, MapCanvasBackground.blue, 1f)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) = GLES20.glViewport(0, 0, width, height)

    private fun upload(a: FloatArray?): Vbo? {
        if (a == null || a.isEmpty()) return null
        val fb = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a)
        fb.position(0)
        val id = IntArray(1).also { GLES20.glGenBuffers(1, it, 0) }[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, a.size * 4, fb, GLES20.GL_STATIC_DRAW)
        return Vbo(id, a.size / MapGlMesh.STRIDE)
    }

    private fun free(vararg v: Vbo?) = v.forEach { if (it != null) GLES20.glDeleteBuffers(1, intArrayOf(it.id), 0) }

    private fun sync() {
        val fl = floorMeshes
        if (fl !== shownFloors) {
            floorVbo.values.forEach { free(it) }
            floorVbo = fl.mapNotNull { (k, a) -> upload(a)?.let { k to it } }.toMap()
            shownFloors = fl
        }
        val rt = routeMeshes
        if (rt !== shownRoute) {
            routeVbo.values.forEach { free(it.first, it.second, it.third) }
            routeVbo = rt.mapValues { (_, m) -> Triple(upload(m.casing), upload(m.line), upload(m.beams)) }
            shownRoute = rt
        }
    }

    private fun draw(v: Vbo?, mode: Int) {
        if (v == null) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, v.id)
        var o = 0
        for ((i, k) in intArrayOf(3, 2, 4, 2, 1).withIndex()) {
            GLES20.glVertexAttribPointer(attrs[i], k, GLES20.GL_FLOAT, false, MapGlMesh.STRIDE * 4, o * 4)
            o += k
        }
        GLES20.glUniform1i(u.getValue("uMode"), mode)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, v.n)
    }

    override fun onDrawFrame(unused: GL10?) {
        sync()
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val f = frame ?: return
        val a = Math.toRadians(f.cam.spinDeg.toDouble()); val t = Math.toRadians(f.cam.tiltDeg.toDouble())
        GLES20.glUniform4f(u.getValue("uCam"), f.cam.scale, f.cam.tx, f.cam.ty, f.raise)
        GLES20.glUniform4f(u.getValue("uRot"), cos(a).toFloat(), sin(a).toFloat(), cos(t).toFloat(), sin(t).toFloat())
        GLES20.glUniform4f(u.getValue("uView"), f.w, f.h, f.planW, f.planH)
        GLES20.glUniform1f(u.getValue("uDp"), f.density)
        // wrapped so the shader keeps precision; the jump every ~17 min is one frame of chevrons
        GLES20.glUniform1f(u.getValue("uTime"), ((SystemClock.uptimeMillis() - t0) % 1_000_000L) / 1000f)
        // lowest floor first: the stack is seen from above, so higher floors are nearer
        for (v in f.floors) {
            if (v.alpha <= 0.01f) continue
            GLES20.glUniform1f(u.getValue("uZ"), v.z)
            GLES20.glUniform1f(u.getValue("uAlpha"), v.alpha)
            GLES20.glUniform1f(u.getValue("uOnRoute"), if (v.onRoute) 1f else 0f)
            GLES20.glUniform3f(u.getValue("uHl"), v.hlSel.toFloat(), v.hlTo.toFloat(), v.hlFrom.toFloat())
            GLES20.glDepthMask(true)
            draw(floorVbo[v.level], 0)
            val r = routeVbo[v.level] ?: continue
            draw(r.first, 1); draw(r.second, 1)
            GLES20.glDepthMask(false)
            if (f.xray) { // the route behind a room block shows through, faint
                GLES20.glDisable(GLES20.GL_DEPTH_TEST)
                GLES20.glUniform1f(u.getValue("uAlpha"), v.alpha * 0.35f)
                draw(r.second, 1)
                GLES20.glEnable(GLES20.GL_DEPTH_TEST)
                GLES20.glUniform1f(u.getValue("uAlpha"), v.alpha)
            }
            draw(r.third, 2)
        }
        GLES20.glDepthMask(true)
    }

    private companion object {
        const val DEPTH = "12000.0" // plan units mapped onto the clip depth range

        val VS = """
            precision highp float; precision mediump int;
            attribute vec3 aPos; attribute vec2 aOff; attribute vec4 aCol; attribute vec2 aInfo; attribute float aDist;
            uniform vec4 uCam;      // s, tx, ty, raise factor for highlighted rooms
            uniform vec4 uRot;      // cos spin, sin spin, cos tilt, sin tilt
            uniform vec4 uView;     // view w, h (px), plan w, h
            uniform float uZ;       // floor z in the stack
            uniform float uDp;      // px per dp
            uniform vec3 uHl;       // highlighted room indexes: selected, destination, start (-9 = none)
            uniform int uMode;      // 0 floor mesh, 1 route ribbon, 2 beam
            varying vec4 vCol; varying vec2 vInfo; varying float vDist; varying float vHl;
            void main() {
              vec3 p = aPos;
              float id = aInfo.x;
              vHl = uMode != 0 || id < 0.0 ? 0.0 : id == uHl.x ? 1.0 : id == uHl.y ? 2.0 : id == uHl.z ? 3.0 : 0.0;
              if (vHl > 0.0) p.z *= uCam.w;
              vec2 dir = uMode == 2 ? vec2(uRot.x, -uRot.y) : aOff; // beams: plan direction that maps to screen x
              float len = uMode == 2 ? aOff.x : 1.0;
              p.xy += dir * len * uDp / uCam.x;                    // dp -> plan units
              float qx = p.x - uView.z * 0.5, qy = p.y - uView.w * 0.5, z = p.z + uZ;
              float rx = qx * uRot.x - qy * uRot.y, ry = qx * uRot.y + qy * uRot.x;
              float vy = ry * uRot.z - z * uRot.w, vz = ry * uRot.w + z * uRot.z;
              vec2 sc = uView.xy * 0.5 + uCam.yz + uCam.x * vec2(rx, vy);
              gl_Position = vec4(sc.x / uView.x * 2.0 - 1.0, 1.0 - sc.y / uView.y * 2.0, -vz / $DEPTH, 1.0);
              vCol = aCol; vInfo = aInfo; vDist = aDist * uCam.x / uDp;   // distance along the route in dp
            }"""

        // highp where the GPU has it: mediump (fp16) breaks the chevron phase on long routes
        val FS = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            precision mediump int;
            uniform int uMode; uniform float uAlpha, uTime, uOnRoute;
            uniform vec3 uHlCol[3];
            varying vec4 vCol; varying vec2 vInfo; varying float vDist; varying float vHl;
            void main() {
              vec4 c = vCol;
              if (uMode == 0) {
                if (vInfo.x == -2.0 && uOnRoute > 0.5) c = vec4(uHlCol[0], 1.0);  // plate border on the route
                if (vHl > 0.0) c.rgb = mix(c.rgb, vHl == 1.0 ? uHlCol[0] : vHl == 2.0 ? uHlCol[1] : uHlCol[2], 0.45);
              } else if (uMode == 1) {
                // chevrons flowing toward the destination: a V across the ribbon, moving with time
                float f = fract((vDist - abs(vInfo.y) * 7.0) / 26.0 - uTime * 1.4);
                float chev = smoothstep(0.0, 0.08, f) * (1.0 - smoothstep(0.22, 0.30, f));
                if (abs(vInfo.y) < 0.8) c.rgb = mix(c.rgb, vec3(1.0), chev * 0.85);
              } else {
                // beam: bright at the floor, fading up, slow pulse, soft sides
                float h = vInfo.y, side = 1.0 - abs(vInfo.x);
                c.a *= (1.0 - h) * (0.65 + 0.35 * sin(uTime * 3.0)) * smoothstep(0.0, 0.6, side);
              }
              gl_FragColor = vec4(c.rgb, c.a * uAlpha);
            }"""
    }
}

/** 4x MSAA when the GPU offers it (room edges alias badly without), else plain RGB888 + depth. */
val msaaConfigChooser = GLSurfaceView.EGLConfigChooser { egl, display ->
    fun pick(samples: Int): EGLConfig? {
        val ms = if (samples > 0) intArrayOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples) else IntArray(0)
        val attrs = intArrayOf(
            EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */
        ) + ms + EGL10.EGL_NONE
        val out = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        return if (egl.eglChooseConfig(display, attrs, out, 1, n) && n[0] > 0) out[0] else null
    }
    pick(4) ?: pick(0) ?: error("no OpenGL ES 2 config")
}
