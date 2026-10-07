package com.example

import com.example.hospital.domain.model.Point2D
import com.example.hospital.sensor.IndoorPdrState
import com.example.hospital.voice.Cue
import com.example.hospital.voice.VoiceCueDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCueDetectorTest {

    private fun st(
        toTurn: Float? = null, deg: Float? = null, turn: Int = 0, toEnd: Float? = 40f, segment: Int = 0,
        off: Boolean = false, back: Boolean = false, arrived: Boolean = false
    ) = IndoorPdrState(
        Point2D(0f, 0f), 2, segment, hasArrived = arrived, offRoute = off, backward = back,
        turnIndex = turn, nextTurnDeg = deg, metersToTurn = toTurn, metersToEnd = toEnd
    )

    @Test
    fun `turn ahead then at the corner, once each; a slight turn says one cue`() {
        val d = VoiceCueDetector().apply { reset(0) }
        assertEquals(null, d.onState(st(8f, 90f), Cue.ARRIVING, 1_000))
        assertEquals(Cue.TURN_RIGHT_SOON, d.onState(st(4.5f, 90f), Cue.ARRIVING, 2_000))
        assertEquals(null, d.onState(st(3f, 90f), Cue.ARRIVING, 3_000))
        assertEquals(Cue.TURN_RIGHT, d.onState(st(1f, 90f), Cue.ARRIVING, 4_000))
        assertEquals(null, d.onState(st(0.5f, 90f), Cue.ARRIVING, 5_000))
        // next corner, a slight left close behind: no 8 s cooldown between two different corners
        assertEquals(Cue.SLIGHT_LEFT, d.onState(st(4f, -30f, turn = 1), Cue.ARRIVING, 6_000))
        assertEquals(null, d.onState(st(1f, -30f, turn = 1), Cue.ARRIVING, 7_000))
        // a left turn right after it, already within 1.5 m: straight to "turn left"
        assertEquals(Cue.TURN_LEFT, d.onState(st(1f, -90f, turn = 2), Cue.ARRIVING, 7_500))
    }

    @Test
    fun `off route, wrong way and back on route only when the state changes, with a cooldown`() {
        val d = VoiceCueDetector().apply { reset(0) }
        assertEquals(Cue.WRONG_WAY, d.onState(st(back = true), Cue.ARRIVING, 1_000))
        assertEquals(null, d.onState(st(back = true), Cue.ARRIVING, 2_000))
        assertEquals(Cue.BACK_ON_ROUTE, d.onState(st(), Cue.ARRIVING, 3_000))
        assertEquals(null, d.onState(st(back = true), Cue.ARRIVING, 4_000)) // within 8 s of the last "wrong way"
        assertEquals(null, d.onState(st(), Cue.ARRIVING, 5_000))            // and of the last "back on route"
        assertEquals(Cue.OFF_ROUTE, d.onState(st(off = true), Cue.ARRIVING, 6_000))
        assertEquals(null, d.onState(st(off = true), Cue.ARRIVING, 7_000))
    }

    @Test
    fun `end of the route says almost there, then arrived, once`() {
        val d = VoiceCueDetector().apply { reset(0) }
        assertEquals(Cue.ARRIVING, d.onState(st(toEnd = 4f), Cue.ARRIVING, 1_000))
        assertEquals(null, d.onState(st(toEnd = 2f), Cue.ARRIVING, 2_000))
        assertEquals(Cue.ARRIVED, d.onState(st(toEnd = 0f, arrived = true), Cue.ARRIVING, 3_000))
        assertEquals(null, d.onState(st(toEnd = 0f, arrived = true), Cue.ARRIVING, 4_000))
    }

    @Test
    fun `end of the first segment names the shaft`() {
        val d = VoiceCueDetector().apply { reset(0) }
        assertEquals(Cue.ELEVATOR_UP, d.onState(st(toEnd = 3f), Cue.ELEVATOR_UP, 1_000))
    }

    @Test
    fun `go straight every 20 s on a long leg, never close to a turn`() {
        val d = VoiceCueDetector().apply { reset(0) }
        assertEquals(null, d.onState(st(30f, 90f), Cue.ARRIVING, 10_000))
        assertEquals(Cue.GO_STRAIGHT, d.onState(st(30f, 90f), Cue.ARRIVING, 20_000))
        assertEquals(null, d.onState(st(25f, 90f), Cue.ARRIVING, 30_000))
        assertEquals(Cue.GO_STRAIGHT, d.onState(st(20f, 90f), Cue.ARRIVING, 40_000))
        assertEquals(null, d.onState(st(8f, 90f), Cue.ARRIVING, 61_000)) // under 10 m to the turn
    }
}
