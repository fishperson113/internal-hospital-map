package com.example

import com.example.hospital.domain.model.Floor
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import com.example.hospital.domain.model.RoomCategory
import com.example.hospital.presentation.ui.components.MapGlMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Geometry builders of MapGl.kt (no GL needed), same checks as gl.test.js on the web. */
class MapGlMeshTest {
    private fun pts(vararg xy: Int) = xy.toList().chunked(2).map { Point2D(it[0].toFloat(), it[1].toFloat()) }

    @Test
    fun `route ribbon has whole vertices and distance grows to the end`() {
        val out = MapGlMesh.Buf()
        val len = MapGlMesh.ribbon(out, pts(0, 0, 30, 40, 30, 100), 2f, 4.5f, floatArrayOf(0f, 0f, 1f, 1f), -1f, closed = false, withDist = true)
        val a = out.toArray()
        assertEquals(110f, len, 1e-4f)
        assertEquals(0, a.size % MapGlMesh.STRIDE)
        val dists = a.filterIndexed { i, _ -> i % MapGlMesh.STRIDE == MapGlMesh.STRIDE - 1 }
        assertEquals(110f, dists.max(), 1e-4f)
        assertEquals(0f, dists.min(), 1e-4f)
    }

    @Test
    fun `ear clipping covers concave rooms in either winding`() {
        val l = pts(0, 0, 20, 0, 20, 10, 10, 10, 10, 30, 0, 30) // L shape, area 400
        for (ring in listOf(l, l.reversed())) {
            val tri = MapGlMesh.triangulate(ring)
            assertEquals(12, tri.size)
            val area = tri.toList().chunked(3).sumOf { (a, b, c) ->
                val p = ring[a]; val q = ring[b]; val r = ring[c]
                abs((q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)).toDouble() / 2
            }
            assertEquals(400.0, area, 1e-3)
        }
    }

    @Test
    fun `floor mesh is one batch with every room id and the plate border`() {
        fun room(i: Int, poly: List<Point2D>) = Room(id = "tang-2_$i", code = "$i", name = "R$i", floorLevel = 2, category = RoomCategory.values().first(), nodeId = "n$i", polygon = poly)
        val f = Floor(2, "2", "", listOf(room(0, pts(0, 0, 10, 0, 10, 10, 0, 10)), room(1, pts(20, 0, 30, 0, 30, 10, 20, 10))))
        val mesh = MapGlMesh.floorMesh(f, 30f, 10f)
        assertEquals(0, mesh.size % (MapGlMesh.STRIDE * 3))
        val ids = mesh.filterIndexed { i, _ -> i % MapGlMesh.STRIDE == 9 }.toSet()
        assertTrue(ids.containsAll(listOf(0f, 1f, -2f)))
    }
}
