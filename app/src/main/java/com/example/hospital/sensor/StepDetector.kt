package com.example.hospital.sensor

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Step detection from the accelerometer, independent of how the phone is held:
 * |a| is low-passed (~10 Hz) and a slow baseline (gravity) is removed. A peak above the threshold,
 * then a valley below it, then the signal back above 0 = one step.
 *
 * Walking gate: a lone peak (a shake, putting the phone down) never counts. The first step is held
 * until a second one follows within [MAX_GAP_NS]; then both are released.
 *
 * Step length (Weinberg): K * (peak - valley)^(1/4), clamped to a human range.
 */
class StepDetector(
    // Calibration knob: raise if the dot falls behind people, lower if it runs ahead.
    var weinbergK: Float = 0.45f
) {
    companion object {
        const val MIN_GAP_NS = 280_000_000L   // faster than ~3.5 steps/s = same step bouncing
        const val MAX_GAP_NS = 1_600_000_000L // slower than this = not walking (any more)
        const val MIN_PEAK = 0.9f             // m/s² above baseline; adaptive above that
        const val MIN_STEP_M = 0.35f
        const val MAX_STEP_M = 0.95f
    }

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

        if (walking && tNs - lastStepT > MAX_GAP_NS) { walking = false; held = null }
        if (!walking && tNs - lastStepT > 3 * MAX_GAP_NS) typicalPeak += (2f - typicalPeak) * dt // forget old jolts

        val thr = (typicalPeak * 0.4f).coerceIn(MIN_PEAK, 2.5f)
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
        if (gap < MIN_GAP_NS) return emptyList()
        lastStepT = t
        val len = (weinbergK * amplitude.coerceAtLeast(0f).pow(0.25f)).coerceIn(MIN_STEP_M, MAX_STEP_M)
        if (!walking) {
            val first = held
            if (first == null || gap > MAX_GAP_NS) { held = len; return emptyList() }
            walking = true
            held = null
            typicalPeak += (peakHeight - typicalPeak) * 0.25f
            return listOf(first, len)
        }
        typicalPeak += (peakHeight - typicalPeak) * 0.25f
        return listOf(len)
    }
}
