package com.example.hospital.domain.util

import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.Room
import kotlin.math.atan2
import kotlin.math.hypot

object HospitalGeometry {

    /**
     * Distance from (x, y) to the closest edge of a polygon.
     * Matches the formula in the specification (Section 8).
     */
    fun edgeDist(poly: List<Point2D>, x: Float, y: Float): Float {
        if (poly.isEmpty()) return Float.MAX_VALUE
        var best = Float.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[(i + poly.size - 1) % poly.size]
            val b = poly[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
            val dist = hypot(x - a.x - t * dx, y - a.y - t * dy)
            if (dist < best) best = dist
        }
        return best
    }

    /**
     * Index of the closest named room on a floor, or null.
     * Matches Section 8 specification.
     */
    fun nearestRoom(rooms: List<Room>, x: Float, y: Float): Room? {
        return rooms
            .filter { it.name.isNotBlank() && !it.name.all(Char::isDigit) }
            .minByOrNull { edgeDist(it.polygon, x, y) }
    }

    /**
     * Point in polygon test using ray casting.
     */
    fun pointInPolygon(x: Float, y: Float, poly: List<Point2D>): Boolean {
        if (poly.size < 3) return false
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

    /**
     * Checks if (x, y) is inside the floor's [x1, y1, x2, y2] frame.
     */
    fun pointInFrame(frame: List<Float>, x: Float, y: Float): Boolean {
        if (frame.size < 4) return true // no frame constraint
        val x1 = minOf(frame[0], frame[2])
        val x2 = maxOf(frame[0], frame[2])
        val y1 = minOf(frame[1], frame[3])
        val y2 = maxOf(frame[1], frame[3])
        return x in x1..x2 && y in y1..y2
    }

    /**
     * Closest point on a polyline to point (x, y), along with the distance in pixels.
     */
    fun closestPointOnPolyline(polyline: List<Point2D>, x: Float, y: Float): Pair<Point2D, Float> {
        if (polyline.isEmpty()) return Point2D(x, y) to 0f
        if (polyline.size == 1) {
            val p = polyline[0]
            return p to hypot(x - p.x, y - p.y)
        }

        var bestPoint = polyline[0]
        var bestDist = Float.MAX_VALUE

        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0f, 1f)
            val projX = a.x + t * dx
            val projY = a.y + t * dy
            val d = hypot(x - projX, y - projY)
            if (d < bestDist) {
                bestDist = d
                bestPoint = Point2D(projX, projY)
            }
        }
        return bestPoint to bestDist
    }

    /**
     * Direction in degrees on the plan: 0 = up (-Y), 90 = right (+X), clockwise.
     * Matches formula in Section 7.
     */
    fun segmentAngleDeg(p1: Point2D, p2: Point2D): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val rad = atan2(dx.toDouble(), -dy.toDouble())
        return ((Math.toDegrees(rad) + 360.0) % 360.0).toFloat()
    }
}
