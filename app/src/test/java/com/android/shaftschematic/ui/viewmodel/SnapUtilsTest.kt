package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapUtilsTest {

    @Test
    fun `buildSnapAnchors includes 0 and overall length`() {
        val spec = ShaftSpec(
            overallLengthMm = 100f
        )

        val anchors = buildSnapAnchors(spec)

        assertTrue("Anchors should contain aft face (0 mm)", anchors.contains(0f))
        assertTrue("Anchors should contain forward face (overallLengthMm)", anchors.contains(100f))
        // Sorted check
        assertEquals(anchors.sorted(), anchors)
    }

    @Test
    fun `buildSnapAnchors includes segment starts and ends and is sorted`() {
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 10f, lengthMm = 40f, diaMm = 80f), // ends at 50
                Body(id = "b2", startFromAftMm = 50f, lengthMm = 50f, diaMm = 80f)  // ends at 100
            ),
            tapers = listOf(
                Taper(
                    id = "t1",
                    startFromAftMm = 100f,
                    lengthMm = 20f,
                    startDiaMm = 80f,
                    endDiaMm = 60f
                ) // ends at 120
            ),
            threads = listOf(
                Threads(
                    id = "th1",
                    startFromAftMm = 120f,
                    lengthMm = 30f,
                    majorDiaMm = 60f,
                    pitchMm = 5f
                ) // ends at 150
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 150f, lengthMm = 25f, odMm = 90f) // ends at 175
            )
        )

        val anchors = buildSnapAnchors(spec)

        val expected = listOf(
            0f,   // aft
            10f,  // body1 start
            50f,  // body1 end, body2 start
            100f, // body2 end, taper start
            120f, // taper end, thread start
            150f, // thread end, liner start
            175f, // liner end
            200f  // overall
        )

        assertEquals("Anchors should match expected sorted unique list", expected, anchors)
    }

    @Test
    fun `snapPositionMm snaps to nearest anchor within tolerance`() {
        val anchors = listOf(0f, 50f, 100f)
        val config = SnapConfig(toleranceMm = 2f)

        val raw1 = 49.1f
        val snapped1 = snapPositionMm(raw1, anchors, config)
        assertEquals(50f, snapped1, 0.0001f)

        val raw2 = 1.9f
        val snapped2 = snapPositionMm(raw2, anchors, config)
        assertEquals(0f, snapped2, 0.0001f)
    }

    @Test
    fun `snapPositionMm returns raw when outside tolerance`() {
        val anchors = listOf(0f, 50f, 100f)
        val config = SnapConfig(toleranceMm = 1f)

        val raw = 52.1f   // 2.1mm away from 50mm
        val snapped = snapPositionMm(raw, anchors, config)

        assertEquals("Outside tolerance should not snap", raw, snapped, 0.0001f)
    }

    @Test
    fun `snapPositionMm uses nearest of multiple anchors`() {
        val anchors = listOf(0f, 50f, 100f)
        val config = SnapConfig(toleranceMm = 10f)

        val raw = 47f // closer to 50 than to 0
        val snapped = snapPositionMm(raw, anchors, config)

        assertEquals(50f, snapped, 0.0001f)
    }

    @Test
    fun `snapPositionMm with empty anchor list returns raw`() {
        val anchors = emptyList<Float>()
        val raw = 42f
        val snapped = snapPositionMm(raw, anchors)

        assertEquals(raw, snapped, 0.0001f)
    }
}
