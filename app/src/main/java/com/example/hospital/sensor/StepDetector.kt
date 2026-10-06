package com.example.hospital.sensor

import com.example.hospital.domain.model.Positioning
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Step detection from the accelerometer, independent of how the phone is held (web: PDR.stepDetector):
 * |a| is low-passed (~10 Hz) and a slow baseline (gravity) is removed. A peak above the threshold,
 * then a valley below it, then the signal back above 0 = one step.
 *
 * Walking gate: a lone peak (a shake, putting the phone down) never counts. The first step is held
 * until a second one follows within MAX_GAP; then both are released.
 *
 * Step length (Weinberg): [k] * (peak - valley)^(1/4), clamped to [MIN_LEN, MAX_LEN].
 * Constants come from the server ([Positioning.step]); [k] starts at positioning.step_k and is then
 * learned per user ([StepLearner]).
 */
class StepDetector(var k: Float, cfg: Positioning.StepConfig) {
    private val minGapNs = (cfg.minGap * 1e9).toLong()   // faster = same step bouncing
    private val maxGapNs = (cfg.maxGap * 1e9).toLong()   // slower = not walking (any more)
    private val minPeak = cfg.minPeak                     // m/s² above baseline; adaptive above that
    private val minLen = cfg.minLen
    private val maxLen = cfg.maxLen

    private var lastT = 0L
    private var smooth = Float.NaN
    private var base = Float.NaN
    private var phase = 0 // 0 = wait for peak, 1 = in peak, 2 = in valley
    private var peak = 0f
    private var valley = 0f
    private var typicalPeak = 2f // running peak height of real steps
    private var lastStepT = 0L
    private var held: Float? = null
    private var walking = false

    /** Feed one accelerometer sample. Returns the lengths (m) of steps it confirms: usually none or one. */
    fun onSample(tNs: Long, ax: Float, ay: Float, az: Float): List<Float> {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        if (smooth.isNaN()) {
            smooth = mag; base = mag; lastT = tNs
            return emptyList()
        }
        val dt = ((tNs - lastT) / 1e9f).coerceIn(0.001f, 0.2f)
        lastT = tNs
        smooth += (mag - smooth) * (dt / (0.016f + dt)) // ~10 Hz low-pass
        base += (mag - base) * (dt / (1.5f + dt))       // gravity + slow drift
        val s = smooth - base

        if (walking && tNs - lastStepT > maxGapNs) { walking = false; held = null }
        if (!walking && tNs - lastStepT > 3 * maxGapNs) typicalPeak += (2f - typicalPeak) * dt // forget old jolts

        val thr = (typicalPeak * 0.4f).coerceIn(minPeak, 2.5f)
        when (phase) {
            0 -> if (s > thr) { phase = 1; peak = s; valley = s }
            1 -> { peak = max(peak, s); if (s < -thr * 0.4f) { phase = 2; valley = s } }
            2 -> {
                valley = min(valley, s)
                if (s > 0f) { // valley over: the step is complete
                    phase = 0
                    return accept(tNs, peak, peak - valley)
                }
            }
        }
        return emptyList()
    }

    private fun accept(t: Long, peakHeight: Float, amplitude: Float): List<Float> {
        val gap = t - lastStepT
        if (gap < minGapNs) return emptyList()
        lastStepT = t
        val len = (k * amplitude.coerceAtLeast(0f).pow(0.25f)).coerceIn(minLen, maxLen)
        if (!walking) {
            val first = held
            if (first == null || gap > maxGapNs) { held = len; return emptyList() }
            walking = true
            held = null
            typicalPeak += (peakHeight - typicalPeak) * 0.25f
            return listOf(first, len)
        }
        typicalPeak += (peakHeight - typicalPeak) * 0.25f
        return listOf(len)
    }
}

/**
 * Per-user step length (web: PDR.stepLearner). At every corner the tracker knows the real length of
 * the leg just walked (route px -> metres) and the length counted from steps. [k] moves part of the
 * way toward real / counted, so one bad leg cannot throw it far. Short legs say little and are skipped.
 */
class StepLearner(k: Float, private val cfg: Positioning.LearnerConfig) {
    var k = k
        private set
    var legs = 0
        private set

    /** Returns true when [k] changed. */
    fun onLeg(realM: Float, countedM: Float): Boolean {
        if (!(realM >= cfg.minLegM) || !(countedM > 0f)) return false
        val ratio = (realM / countedM).coerceIn(cfg.minRatio, cfg.maxRatio)
        k = (k * ratio.pow(cfg.gain)).coerceIn(cfg.minK, cfg.maxK)
        legs++
        return true
    }
}
