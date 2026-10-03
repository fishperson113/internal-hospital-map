package com.example.hospital.sensor

import android.content.Context
import android.os.SystemClock
import com.example.hospital.domain.model.HospitalRoute
import com.example.hospital.domain.model.Point2D
import com.example.hospital.domain.util.HospitalGeometry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class IndoorPdrState(
    val position: Point2D,
    val floorLevel: Int,
    val floorId: String = "tang-$floorLevel",
    val segmentIndex: Int = 0,
    val distanceRemainingMeters: Float = 0f,
    val hasArrived: Boolean = false,
    val isNearTransfer: Boolean = false,
    val isOffRoute: Boolean = false,
    val planHeadingDeg: Float = 0f
)

/**
 * Pedestrian Dead Reckoning (PDR) & Map Matching Engine for indoor hospital navigation.
 * Implements the 7-step PDR specifications (Section 7).
 */
class IndoorPdrTracker(
    private val context: Context? = null,
    private val onOffRoute: ((floorId: String, floorLevel: Int, x: Float, y: Float) -> Unit)? = null
) {
    companion object {
        private const val PREFS_NAME = "hospital_pdr_prefs"
        private const val KEY_STEP_LENGTH = "step_length_m"
        private const val KEY_NORTH_DEG_PREFIX = "north_deg_floor_"
        private const val DEFAULT_STEP_LENGTH_M = 0.65f
        private const val DEFAULT_METERS_PER_PX = 0.08f
        private const val SNAP_THRESHOLD_PX = 40f // ~3m
        private const val OFF_ROUTE_THRESHOLD_PX = 60f // ~5m
        private const val REROUTE_THROTTLE_MS = 5000L
    }

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryPrefs = mutableMapOf<String, Float>()

    private var currentRoute: HospitalRoute? = null
    private var state: IndoorPdrState? = null

    var stepLengthM: Float = prefs?.getFloat(KEY_STEP_LENGTH, DEFAULT_STEP_LENGTH_M) ?: DEFAULT_STEP_LENGTH_M
        private set

    private fun getSavedNorth(floorId: String): Float {
        return prefs?.getFloat(KEY_NORTH_DEG_PREFIX + floorId, 0f)
            ?: memoryPrefs[KEY_NORTH_DEG_PREFIX + floorId]
            ?: 0f
    }

    private fun saveNorth(floorId: String, value: Float) {
        prefs?.edit()?.putFloat(KEY_NORTH_DEG_PREFIX + floorId, value)?.apply()
        memoryPrefs[KEY_NORTH_DEG_PREFIX + floorId] = value
    }

    private fun hasSavedNorth(floorId: String): Boolean {
        return prefs?.contains(KEY_NORTH_DEG_PREFIX + floorId) == true || memoryPrefs.containsKey(KEY_NORTH_DEG_PREFIX + floorId)
    }

    private fun saveStepLength(length: Float) {
        prefs?.edit()?.putFloat(KEY_STEP_LENGTH, length)?.apply()
        memoryPrefs[KEY_STEP_LENGTH] = length
    }

    private var metersPerPx: Float = DEFAULT_METERS_PER_PX

    // Initial heading calibration per floor
    private val initialCompassSamples = mutableListOf<Float>()
    private var isNorthCalibrated = false
    private var lastRerouteTimeMs = 0L

    // Milestone calibration tracking
    private var stepsCountedInSegment = 0
    private var segmentStartDistanceM = 0f

    /**
     * Start free dead reckoning at the place the user said they are (no route yet).
     * ponytail: no wall/corridor-graph snapping without a route; north offset is the last one a
     * route calibrated for this floor (0 = compass north is "up" on the plan until then).
     */
    fun startAt(position: Point2D, floorLevel: Int, floorId: String): IndoorPdrState {
        currentRoute = null
        return IndoorPdrState(position = position, floorLevel = floorLevel, floorId = floorId).also { state = it }
    }

    fun initialize(route: HospitalRoute): IndoorPdrState {
        currentRoute = route
        metersPerPx = route.assumptions.metersPerPx.toFloat().coerceAtLeast(0.01f)

        val firstSeg = route.segments.firstOrNull()
        val startPt = firstSeg?.points?.firstOrNull() ?: Point2D(0f, 0f)
        val startFloor = firstSeg?.floorLevel ?: route.startNode.floorLevel
        val startFloorId = firstSeg?.floorId ?: "tang-$startFloor"

        initialCompassSamples.clear()
        isNorthCalibrated = hasSavedNorth(startFloorId)
        stepsCountedInSegment = 0
        segmentStartDistanceM = firstSeg?.length ?: 0f

        val newState = IndoorPdrState(
            position = startPt,
            floorLevel = startFloor,
            floorId = startFloorId,
            segmentIndex = 0,
            distanceRemainingMeters = route.totalDistanceMeters,
            hasArrived = false,
            isNearTransfer = false,
            isOffRoute = false,
            planHeadingDeg = 0f
        )
        state = newState
        return newState
    }

    /**
     * Advances user position by 1 step given the compass heading.
     */
    fun onStep(compassDeg: Float): IndoorPdrState? {
        val s = state ?: return null
        val route = currentRoute
        if (route == null) {
            val heading = snapToCorridor((compassDeg - getSavedNorth(s.floorId) + 360f) % 360f)
            return s.copy(position = advance(s.position, heading), planHeadingDeg = heading).also { state = it }
        }
        if (s.hasArrived) return s

        val segments = route.segments
        if (segments.isEmpty()) return s

        val segIdx = s.segmentIndex.coerceIn(0, segments.lastIndex)
        val currentSeg = segments[segIdx]
        val points = currentSeg.points
        if (points.isEmpty()) return s

        stepsCountedInSegment++

        // 1. Calibrate northDeg if needed on initial steps
        val effectiveCompassDeg = compassDeg
        if (!isNorthCalibrated && points.size >= 2) {
            initialCompassSamples.add(effectiveCompassDeg)
            if (initialCompassSamples.size >= 3) {
                val avgCompass = initialCompassSamples.average().toFloat()
                val segmentHeading = HospitalGeometry.segmentAngleDeg(points[0], points[1])
                val northDeg = (avgCompass - segmentHeading + 360f) % 360f
                saveNorth(currentSeg.floorId, northDeg)
                isNorthCalibrated = true
            }
        }

        // 2. Convert compass heading to plan heading
        val savedNorthDeg = getSavedNorth(currentSeg.floorId)
        var planHeading = (effectiveCompassDeg - savedNorthDeg + 360f) % 360f

        // 3. Corridor snapping: snap to 0, 90, 180, 270 if within 20 degrees
        planHeading = snapToCorridor(planHeading)

        // 4. Advance position according to heading
        // val px = stepLengthM / metersPerPx
        // x += px * sin(rad), y -= px * cos(rad)
        val moved = advance(s.position, planHeading)
        var newX = moved.x
        var newY = moved.y

        // 5. Route snapping: pull to nearest point on polyline if within 40 px (~3m)
        val (closestPt, distToRoute) = HospitalGeometry.closestPointOnPolyline(points, newX, newY)
        var isOffRoute = false

        if (distToRoute <= SNAP_THRESHOLD_PX) {
            newX = closestPt.x
            newY = closestPt.y
        } else if (distToRoute > OFF_ROUTE_THRESHOLD_PX) {
            isOffRoute = true
            val now = SystemClock.uptimeMillis()
            if (now - lastRerouteTimeMs >= REROUTE_THROTTLE_MS) {
                lastRerouteTimeMs = now
                onOffRoute?.invoke(currentSeg.floorId, currentSeg.floorLevel, newX, newY)
            }
        }

        val updatedPos = Point2D(newX, newY)

        // 6. Check arrival / near milestone
        val lastPt = points.last()
        val distToSegEnd = hypot(newX - lastPt.x, newY - lastPt.y) * metersPerPx

        val isLastSeg = segIdx == segments.lastIndex
        val hasArrived = isLastSeg && (distToSegEnd <= 1.5f)
        val isNearTransfer = !isLastSeg && (distToSegEnd <= 3.0f)

        // Calculate remaining meters along route
        var remainingMeters = distToSegEnd
        for (i in (segIdx + 1) until segments.size) {
            remainingMeters += segments[i].length
        }

        val updated = s.copy(
            position = updatedPos,
            floorLevel = currentSeg.floorLevel,
            floorId = currentSeg.floorId,
            segmentIndex = segIdx,
            distanceRemainingMeters = if (hasArrived) 0f else remainingMeters,
            hasArrived = hasArrived,
            isNearTransfer = isNearTransfer,
            isOffRoute = isOffRoute,
            planHeadingDeg = planHeading
        )
        state = updated
        return updated
    }

    /**
     * Snap to milestone when moving to next step or changing floors.
     * Updates and saves learned step length according to Section 7.
     */
    fun snapToMilestone(nextSegmentIndex: Int): IndoorPdrState? {
        val s = state ?: return null
        val route = currentRoute ?: return s
        val segments = route.segments
        if (nextSegmentIndex !in segments.indices) return s

        // Adapt step length based on actual distance walked
        if (stepsCountedInSegment > 2 && segmentStartDistanceM > 0f) {
            val observedStepLength = (segmentStartDistanceM / stepsCountedInSegment).coerceIn(0.45f, 0.95f)
            stepLengthM = (stepLengthM + observedStepLength) / 2f
            saveStepLength(stepLengthM)
        }

        val nextSeg = segments[nextSegmentIndex]
        val milestonePt = nextSeg.points.firstOrNull() ?: s.position

        stepsCountedInSegment = 0
        segmentStartDistanceM = nextSeg.length

        val updated = s.copy(
            position = milestonePt,
            floorLevel = nextSeg.floorLevel,
            floorId = nextSeg.floorId,
            segmentIndex = nextSegmentIndex,
            isNearTransfer = false,
            isOffRoute = false
        )
        state = updated
        return updated
    }

    /** One step along planHeading (0 = up, clockwise): x += px * sin, y -= px * cos. */
    private fun advance(p: Point2D, planHeading: Float): Point2D {
        val px = (stepLengthM / metersPerPx).coerceAtLeast(1f)
        val rad = Math.toRadians(planHeading.toDouble())
        return Point2D((p.x + px * sin(rad)).toFloat(), (p.y - px * cos(rad)).toFloat())
    }

    /**
     * Corridor snapping: if within 20 degrees of 0, 90, 180, 270, snap to it.
     */
    private fun snapToCorridor(heading: Float): Float {
        val h = (heading + 360f) % 360f
        return when {
            h <= 20f || h >= 340f -> 0f
            abs(h - 90f) <= 20f -> 90f
            abs(h - 180f) <= 20f -> 180f
            abs(h - 270f) <= 20f -> 270f
            else -> h
        }
    }

    fun onFloorPressureChange(floorOffset: Int): IndoorPdrState? {
        val s = state ?: return null
        val route = currentRoute ?: return s
        if (route.transfer != null && s.segmentIndex == 0 && floorOffset != 0) {
            return snapToMilestone(1)
        }
        return null
    }

    fun getCurrentState(): IndoorPdrState? = state
}
