package com.example.hospital.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R
import com.example.hospital.presentation.util.AppLanguage
import com.example.hospital.sensor.IndoorPdrState
import kotlin.math.abs

/** One spoken cue. Clips: res/raw/voice_<name>_<vi|en>.mp3, made by tools/gen_voice_prompts.py. */
enum class Cue {
    START, GO_STRAIGHT, TURN_LEFT_SOON, TURN_RIGHT_SOON, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT,
    WRONG_WAY, OFF_ROUTE, REROUTED, BACK_ON_ROUTE, ELEVATOR_UP, ELEVATOR_DOWN, STAIRS_UP, STAIRS_DOWN,
    ARRIVING, ARRIVED;

    /** Cues worth a short vibration (the user should act now). */
    val buzz: Boolean get() = this in setOf(TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, WRONG_WAY, OFF_ROUTE, ARRIVED)
}

/**
 * Decides what to say from the tracker state, one call per position update. Pure logic, no Android.
 * Turns: "turn left ahead" [SOON_M] before the corner, "turn left" at it, each once per corner.
 * Off route / wrong way / back on route: when that state starts (or ends). End of a segment: "almost there", or
 * the shaft cue before a floor change. While nothing else is said: "go straight" every [STRAIGHT_EVERY_MS] on a
 * long leg. The same cue never repeats within [COOLDOWN_MS], except per-corner turns and the arrival.
 */
class VoiceCueDetector {
    companion object {
        const val SOON_M = 5f
        const val AT_M = 1.5f
        const val SLIGHT_DEG = 50f       // a turn under this is "bear left / right"
        const val STRAIGHT_MIN_M = 10f   // say "go straight" only with at least this far to the next turn / end
        const val STRAIGHT_EVERY_MS = 20_000L
        const val COOLDOWN_MS = 8_000L
    }

    private var prev: IndoorPdrState? = null
    private var segment = -1
    private var soonSaid = -1 // turnIndex already announced
    private var atSaid = -1
    private var endSaid = false
    private var arrivedSaid = false
    private var lastCueMs = 0L
    private val lastSaid = mutableMapOf<Cue, Long>()

    /** A new route or a jump to another step: forget what was said, keep the cooldowns. */
    fun reset(nowMs: Long) {
        prev = null; segment = -1; soonSaid = -1; atSaid = -1; endSaid = false; arrivedSaid = false
        lastCueMs = nowMs
    }

    /** [endCue]: what to say near the end of this segment: [Cue.ARRIVING] on the last one, else the shaft cue. */
    fun onState(s: IndoorPdrState, endCue: Cue?, nowMs: Long): Cue? {
        val p = prev
        prev = s
        if (s.segmentIndex != segment) { segment = s.segmentIndex; soonSaid = -1; atSaid = -1; endSaid = false }
        val cue = pick(s, p, endCue, nowMs) ?: return null
        val repeatable = cue in setOf(Cue.OFF_ROUTE, Cue.WRONG_WAY, Cue.BACK_ON_ROUTE, Cue.GO_STRAIGHT)
        if (repeatable && nowMs - (lastSaid[cue] ?: Long.MIN_VALUE / 2) < COOLDOWN_MS) return null
        lastSaid[cue] = nowMs
        lastCueMs = nowMs
        return cue
    }

    /** A cue said from outside (start, re-route): counts for the cooldowns and the "go straight" pause. */
    fun said(cue: Cue, nowMs: Long) { lastSaid[cue] = nowMs; lastCueMs = nowMs }

    private fun pick(s: IndoorPdrState, p: IndoorPdrState?, endCue: Cue?, nowMs: Long): Cue? {
        if (s.offRoute) return if (p?.offRoute != true) Cue.OFF_ROUTE else null
        if (s.backward) return if (p?.backward != true) Cue.WRONG_WAY else null
        if (p != null && p.backward) return Cue.BACK_ON_ROUTE
        if (s.hasArrived) return if (!arrivedSaid) Cue.ARRIVED.also { arrivedSaid = true } else null

        val deg = s.nextTurnDeg
        val toTurn = s.metersToTurn
        if (deg != null && toTurn != null) {
            val slight = abs(deg) < SLIGHT_DEG
            val right = deg > 0
            if (toTurn <= AT_M && atSaid != s.turnIndex) {
                atSaid = s.turnIndex; soonSaid = s.turnIndex
                return if (slight) (if (right) Cue.SLIGHT_RIGHT else Cue.SLIGHT_LEFT) else if (right) Cue.TURN_RIGHT else Cue.TURN_LEFT
            }
            if (toTurn <= SOON_M && soonSaid != s.turnIndex) {
                soonSaid = s.turnIndex
                if (slight) { atSaid = s.turnIndex; return if (right) Cue.SLIGHT_RIGHT else Cue.SLIGHT_LEFT } // one cue is enough
                return if (right) Cue.TURN_RIGHT_SOON else Cue.TURN_LEFT_SOON
            }
        }
        val toEnd = s.metersToEnd
        if (toEnd != null && toEnd <= SOON_M && (toTurn == null || toTurn > toEnd) && !endSaid && endCue != null) {
            endSaid = true
            return endCue
        }
        val ahead = toTurn ?: toEnd
        if (ahead != null && ahead >= STRAIGHT_MIN_M && nowMs - lastCueMs >= STRAIGHT_EVERY_MS) return Cue.GO_STRAIGHT
        return null
    }
}

/** Plays cues: a short chime, then the clip in the app's language, with ducking and a short vibration. */
@Suppress("DEPRECATION") // the pre-26 / pre-31 audio focus and vibrator calls
class VoicePlayer(context: Context) {
    private val app = context.applicationContext
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
    private val chime = pool.load(app, R.raw.voice_chime, 1)
    private val clips: Map<Pair<Cue, AppLanguage>, Int> = Cue.entries.flatMap { c ->
        val (vi, en) = res(c)
        listOf((c to AppLanguage.VIETNAMESE) to pool.load(app, vi, 1), (c to AppLanguage.ENGLISH) to pool.load(app, en, 1))
    }.toMap()
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus = if (Build.VERSION.SDK_INT >= 26)
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build() else null
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    else app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val dropFocus = Runnable { abandonFocus() }
    private var streams = intArrayOf()

    fun play(cue: Cue, lang: AppLanguage, vibrate: Boolean = true) {
        val clip = clips[cue to lang] ?: return
        main.removeCallbacksAndMessages(null)
        requestFocus()
        streams.forEach(pool::stop) // a newer cue cuts the one still playing
        streams = intArrayOf(pool.play(chime, 0.6f, 0.6f, 1, 0, 1f))
        main.postDelayed({ streams += pool.play(clip, 1f, 1f, 1, 0, 1f) }, CHIME_MS)
        main.postDelayed(dropFocus, CHIME_MS + MAX_CLIP_MS)
        if (vibrate && cue.buzz) buzz()
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        abandonFocus()
        pool.release()
    }

    private fun buzz() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 80, 90), -1))
        else v.vibrate(longArrayOf(0, 90, 80, 90), -1)
    }

    private fun requestFocus() {
        if (focus != null) audio.requestAudioFocus(focus)
        else audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }

    private fun abandonFocus() {
        if (focus != null) audio.abandonAudioFocusRequest(focus)
        else audio.abandonAudioFocus(null)
    }

    private companion object {
        const val CHIME_MS = 380L    // voice_chime.wav is 0.34 s
        const val MAX_CLIP_MS = 3200L // longest clip ~2.8 s

        fun res(c: Cue): Pair<Int, Int> = when (c) {
            Cue.START -> R.raw.voice_start_vi to R.raw.voice_start_en
            Cue.GO_STRAIGHT -> R.raw.voice_go_straight_vi to R.raw.voice_go_straight_en
            Cue.TURN_LEFT_SOON -> R.raw.voice_turn_left_soon_vi to R.raw.voice_turn_left_soon_en
            Cue.TURN_RIGHT_SOON -> R.raw.voice_turn_right_soon_vi to R.raw.voice_turn_right_soon_en
            Cue.TURN_LEFT -> R.raw.voice_turn_left_vi to R.raw.voice_turn_left_en
            Cue.TURN_RIGHT -> R.raw.voice_turn_right_vi to R.raw.voice_turn_right_en
            Cue.SLIGHT_LEFT -> R.raw.voice_slight_left_vi to R.raw.voice_slight_left_en
            Cue.SLIGHT_RIGHT -> R.raw.voice_slight_right_vi to R.raw.voice_slight_right_en
            Cue.WRONG_WAY -> R.raw.voice_wrong_way_vi to R.raw.voice_wrong_way_en
            Cue.OFF_ROUTE -> R.raw.voice_off_route_vi to R.raw.voice_off_route_en
            Cue.REROUTED -> R.raw.voice_rerouted_vi to R.raw.voice_rerouted_en
            Cue.BACK_ON_ROUTE -> R.raw.voice_back_on_route_vi to R.raw.voice_back_on_route_en
            Cue.ELEVATOR_UP -> R.raw.voice_elevator_up_vi to R.raw.voice_elevator_up_en
            Cue.ELEVATOR_DOWN -> R.raw.voice_elevator_down_vi to R.raw.voice_elevator_down_en
            Cue.STAIRS_UP -> R.raw.voice_stairs_up_vi to R.raw.voice_stairs_up_en
            Cue.STAIRS_DOWN -> R.raw.voice_stairs_down_vi to R.raw.voice_stairs_down_en
            Cue.ARRIVING -> R.raw.voice_arriving_vi to R.raw.voice_arriving_en
            Cue.ARRIVED -> R.raw.voice_arrived_vi to R.raw.voice_arrived_en
        }
    }
}
