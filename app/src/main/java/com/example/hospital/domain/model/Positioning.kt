package com.example.hospital.domain.model

/**
 * Positioning constants served by the backend in GET /api/floors -> `positioning`
 * (MLAI_Hackathon src/backend/positioning.js), shared with the web (src/web/pdr.js).
 * Never hardcoded in the app: change a number on the server and web + app follow.
 * Meaning of each number: comment at the matching function in pdr.js / the sensor package.
 */
data class Positioning(
    /** Compass reading (degrees) when facing the TOP of the floor plans. Plan heading = compass - this. */
    val planNorthDeg: Float,
    /** Starting step length factor: step = stepK * (acceleration swing)^0.25 m, then learned per user. */
    val stepK: Float,
    val reroute: Reroute,
    val ronin: Ronin,
    val step: StepConfig,
    val learner: LearnerConfig,
    val fusion: FusionConfig,
    val tracker: TrackerConfig
) {
    data class Reroute(val everyM: Float, val pauseMs: Long, val minGapMs: Long)
    data class Ronin(val chunkS: Float, val minSpeedMps: Float, val warmupSamples: Int)
    /** Seconds and m/s² as in pdr.js stepDetector. */
    data class StepConfig(val minGap: Float, val maxGap: Float, val minPeak: Float, val minLen: Float, val maxLen: Float)
    data class LearnerConfig(val minLegM: Float, val gain: Float, val minRatio: Float, val maxRatio: Float, val minK: Float, val maxK: Float)
    data class FusionConfig(
        val warmup: Int, val compassGain: Float, val gateDeg: Float, val relock: Int,
        val routeGain: Float, val routeGateDeg: Float
    )
    data class TrackerConfig(
        val axisSnapDeg: Float, val minTurn: Float, val turnTol: Float, val snapM: Float, val mergeM: Float,
        val offRouteDeg: Float, val offRouteM: Float, val overshootM: Float, val backDeg: Float
    )
}
