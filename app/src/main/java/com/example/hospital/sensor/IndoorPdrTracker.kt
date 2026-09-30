package com.example.hospital.sensor

import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.model.RouteSegment
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

data class IndoorPdrState(
    val position: Point2D,
    val floorLevel: Int,
    val segmentIndex: Int = 0,
    val pointIndex: Int = 0,
    val distanceRemainingMeters: Float = 0f,
    val hasArrived: Boolean = false,
    val isNearTransfer: Boolean = false,
    val transferProgressFraction: Float = 0f
)

/**
 * Pedestrian Dead Reckoning (PDR) & Map Matching Engine for indoor hospital navigation.
 * Advances the user's location along the active walking route with each step registered
 * by the device's sensors (Hardware Step Detector or Accelerometer PDR).
 */
class IndoorPdrTracker {

    private var currentRoute: HospitalRoute? = null
    private var state: IndoorPdrState? = null

    // 1 plan unit = 0.1m, average stride = 0.7m = 7.0f plan units
    private val stepDistanceUnits = 7.0f

    fun initialize(route: HospitalRoute): IndoorPdrState {
        currentRoute = route
        val firstSeg = route.segments.firstOrNull()
        val startPt = firstSeg?.points?.firstOrNull() ?: Point2D(0f, 0f)
        val startFloor = firstSeg?.floorLevel ?: route.startNode.floorLevel

        val newState = IndoorPdrState(
            position = startPt,
            floorLevel = startFloor,
            segmentIndex = 0,
            pointIndex = 0,
            distanceRemainingMeters = route.totalDistanceMeters,
            hasArrived = false,
            isNearTransfer = false
        )
        state = newState
        return newState
    }

    fun onStep(): IndoorPdrState? {
        val s = state ?: return null
        val route = currentRoute ?: return s
        if (s.hasArrived) return s

        val segments = route.segments
        if (segments.isEmpty()) return s

        var segIdx = s.segmentIndex
        var ptIdx = s.pointIndex
        var pos = s.position
        var currentFloor = s.floorLevel
        var remainingAdvance = stepDistanceUnits

        while (remainingAdvance > 0f && segIdx < segments.size) {
            val currentSeg = segments[segIdx]
            currentFloor = currentSeg.floorLevel
            val points = currentSeg.points

            if (ptIdx >= points.lastIndex) {
                // Reached end of current segment!
                if (segIdx < segments.lastIndex) {
                    // Transfer to next floor (Elevator / Stairs)
                    segIdx++
                    ptIdx = 0
                    val nextSeg = segments[segIdx]
                    pos = nextSeg.points.firstOrNull() ?: pos
                    currentFloor = nextSeg.floorLevel
                    remainingAdvance -= stepDistanceUnits * 0.5f // cost of transferring
                } else {
                    // Reached end of final segment = ARRIVED!
                    pos = points.last()
                    val arrivedState = s.copy(
                        position = pos,
                        floorLevel = currentFloor,
                        segmentIndex = segIdx,
                        pointIndex = ptIdx,
                        distanceRemainingMeters = 0f,
                        hasArrived = true,
                        isNearTransfer = false
                    )
                    state = arrivedState
                    return arrivedState
                }
            } else {
                val p1 = pos
                val p2 = points[ptIdx + 1]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val distToNext = hypot(dx, dy)

                if (distToNext <= remainingAdvance) {
                    pos = p2
                    ptIdx++
                    remainingAdvance -= distToNext
                } else {
                    val ratio = remainingAdvance / distToNext
                    pos = Point2D(p1.x + dx * ratio, p1.y + dy * ratio)
                    remainingAdvance = 0f
                }
            }
        }

        // Calculate remaining meters
        var remainingMeters = 0f
        for (i in segIdx until segments.size) {
            val seg = segments[i]
            val pts = seg.points
            if (i == segIdx) {
                if (pts.isNotEmpty()) {
                    val currentNext = pts.getOrNull(ptIdx + 1)
                    if (currentNext != null) {
                        remainingMeters += hypot(currentNext.x - pos.x, currentNext.y - pos.y) * 0.1f
                    }
                    for (j in (ptIdx + 1) until pts.lastIndex) {
                        remainingMeters += hypot(pts[j + 1].x - pts[j].x, pts[j + 1].y - pts[j].y) * 0.1f
                    }
                }
            } else {
                for (j in 0 until pts.lastIndex) {
                    remainingMeters += hypot(pts[j + 1].x - pts[j].x, pts[j + 1].y - pts[j].y) * 0.1f
                }
            }
        }

        val hasArrived = (segIdx >= segments.lastIndex && ptIdx >= (segments.lastOrNull()?.points?.lastIndex ?: 0))
            || remainingMeters <= 1.5f

        val nearTransfer = route.transfer != null && segIdx == 0 && ptIdx >= (segments.firstOrNull()?.points?.size ?: 1) - 3

        val updated = s.copy(
            position = pos,
            floorLevel = currentFloor,
            segmentIndex = segIdx,
            pointIndex = ptIdx,
            distanceRemainingMeters = if (hasArrived) 0f else remainingMeters,
            hasArrived = hasArrived,
            isNearTransfer = nearTransfer
        )
        state = updated
        return updated
    }

    fun onFloorPressureChange(floorOffset: Int): IndoorPdrState? {
        val s = state ?: return null
        val route = currentRoute ?: return s
        // If we are at or near a transfer and barometer detected a floor change upwards (+1) or downwards (-1)
        if (route.transfer != null && s.segmentIndex == 0 && floorOffset != 0) {
            val segments = route.segments
            if (segments.size > 1) {
                val nextSeg = segments[1]
                val newState = s.copy(
                    position = nextSeg.points.firstOrNull() ?: s.position,
                    floorLevel = nextSeg.floorLevel,
                    segmentIndex = 1,
                    pointIndex = 0,
                    isNearTransfer = false
                )
                state = newState
                return newState
            }
        }
        return null
    }

    fun getCurrentState(): IndoorPdrState? = state
}
