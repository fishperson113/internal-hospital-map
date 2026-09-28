package com.example.hospital.domain.pathfinding

import com.example.hospital.domain.model.MapNode
import com.example.hospital.domain.model.Room
import kotlin.math.abs

/**
 * Corridor-aware Path Simplifier.
 *
 * Removes redundant collinear intermediate corridor waypoints along straight hallways
 * (e.g., intermediate door markers on a long corridor) while strictly preserving
 * corridor turn corners to prevent awkward diagonal cuts through walls.
 */
object PathSimplifier {

    fun simplify(
        pathNodes: List<MapNode>,
        rooms: List<Room>,
        startRoomId: String?,
        destRoomId: String?
    ): List<MapNode> {
        if (pathNodes.size <= 2) return pathNodes

        // Filter obstacle rooms (virtual markers do not block paths)
        val obstacles = rooms.filter { room ->
            room.boundsWidth > 0f && room.boundsHeight > 0f &&
                    room.id != "room_you_are_here" &&
                    room.id != "room_main_entrance"
        }

        // Pass 1: Collinear corridor waypoint reduction
        val simplified = mutableListOf<MapNode>()
        simplified.add(pathNodes.first())

        var i = 1
        while (i < pathNodes.size - 1) {
            val prev = simplified.last()
            val curr = pathNodes[i]
            val next = pathNodes[i + 1]

            val isCollinear = prev.floorLevel == curr.floorLevel && curr.floorLevel == next.floorLevel &&
                    ((abs(prev.x - curr.x) < 0.015f && abs(curr.x - next.x) < 0.015f) ||
                     (abs(prev.y - curr.y) < 0.015f && abs(curr.y - next.y) < 0.015f))

            if (isCollinear) {
                val floorObstacles = obstacles.filter { it.floorLevel == prev.floorLevel }
                if (!segmentCollidesWithObstacles(prev.x, prev.y, next.x, next.y, floorObstacles)) {
                    // Skip redundant collinear node
                    i++
                    continue
                }
            }
            simplified.add(curr)
            i++
        }
        simplified.add(pathNodes.last())

        // Pass 2: Clean duplicate consecutive coordinates if any
        val result = mutableListOf<MapNode>()
        for (node in simplified) {
            if (result.isEmpty() || abs(result.last().x - node.x) > 0.005f || abs(result.last().y - node.y) > 0.005f || result.last().floorLevel != node.floorLevel) {
                result.add(node)
            }
        }

        return if (result.size >= 2) result else pathNodes
    }

    private fun segmentCollidesWithObstacles(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        obstacles: List<Room>
    ): Boolean {
        // Room boundary collision with small wall clearance
        val margin = 0.005f
        for (room in obstacles) {
            val left = room.boundsLeft - margin
            val right = room.boundsLeft + room.boundsWidth + margin
            val top = room.boundsTop - margin
            val bottom = room.boundsTop + room.boundsHeight + margin

            if (lineSegmentIntersectsAABB(x1, y1, x2, y2, left, top, right, bottom)) {
                return true
            }
        }
        return false
    }

    /**
     * Standard Liang-Barsky line segment to AABB intersection check
     */
    private fun lineSegmentIntersectsAABB(
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        minX: Float, minY: Float,
        maxX: Float, maxY: Float
    ): Boolean {
        // Check if either endpoint is inside the box
        if (x1 in minX..maxX && y1 in minY..maxY) return true
        if (x2 in minX..maxX && y2 in minY..maxY) return true

        val dx = x2 - x1
        val dy = y2 - y1

        var t0 = 0f
        var t1 = 1f

        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(x1 - minX, maxX - x1, y1 - minY, maxY - y1)

        for (i in 0 until 4) {
            val pi = p[i]
            val qi = q[i]
            if (pi == 0f) {
                if (qi < 0f) return false
            } else {
                val t = qi / pi
                if (pi < 0f) {
                    if (t > t1) return false
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return false
                    if (t < t1) t1 = t
                }
            }
        }

        return t0 <= t1
    }
}
