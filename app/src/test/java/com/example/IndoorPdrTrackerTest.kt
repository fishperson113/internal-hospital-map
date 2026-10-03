package com.example

import com.example.hospital.domain.model.Point2D
import com.example.hospital.sensor.IndoorPdrTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndoorPdrTrackerTest {

    @Test
    fun `steps move the dot from the picked start along the compass`() {
        val pdr = IndoorPdrTracker()
        assertNull(pdr.onStep(0f)) // no start place yet: nothing to move

        pdr.startAt(Point2D(100f, 100f), floorLevel = 2, floorId = "tang-2")
        val east = pdr.onStep(85f)!! // snapped to 90° = right on the plan
        assertEquals(90f, east.planHeadingDeg, 0f)
        assertEquals(100f, east.position.y, 0.01f)
        assertEquals(100f + 0.65f / 0.08f, east.position.x, 0.01f)

        val north = pdr.onStep(0f)!! // 0° = up on the plan
        assertEquals(east.position.x, north.position.x, 0.01f)
        assertEquals(100f - 0.65f / 0.08f, north.position.y, 0.01f)
    }
}
