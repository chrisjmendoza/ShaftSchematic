package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionWarningsTest {

    @Test
    fun `empty spec returns no warnings`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(collectAddWarnings(spec, 100f, 200f, false).isEmpty())
    }

    @Test
    fun `clean gap returns no warnings`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f)),
            liners = listOf(Liner(startFromAftMm = 500f, lengthMm = 200f, odMm = 90f)),
        )
        // 200–400 mm — clear of both components
        assertTrue(collectAddWarnings(spec, 200f, 200f, false).isEmpty())
    }

    @Test
    fun `overlap with existing taper produces warning`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f))
        )
        val w = collectAddWarnings(spec, 100f, 200f, false)
        assertTrue(w.any { it.contains("Taper") })
    }

    @Test
    fun `overlap with included thread produces warning`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(startFromAftMm = 200f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f, excludeFromOAL = false)
            )
        )
        val w = collectAddWarnings(spec, 250f, 100f, false)
        assertTrue(w.any { it.contains("Thread") })
    }

    @Test
    fun `excluded thread is never flagged as collision`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(startFromAftMm = -100f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f,
                        excludeFromOAL = true, isAftEnd = true)
            )
        )
        // Proposed overlaps the excluded thread's position but it must not be flagged
        val w = collectAddWarnings(spec, -50f, 200f, false)
        assertFalse(w.any { it.contains("Thread") })
    }

    @Test
    fun `overlap with liner produces warning`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(startFromAftMm = 300f, lengthMm = 200f, odMm = 90f))
        )
        val w = collectAddWarnings(spec, 400f, 100f, false)
        assertTrue(w.any { it.contains("Liner") })
    }

    @Test
    fun `bodies are never flagged as collision`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 1000f, diaMm = 80f))
        )
        // Proposed sits entirely inside the body — should be clean
        assertTrue(collectAddWarnings(spec, 100f, 200f, false).isEmpty())
    }

    @Test
    fun `touching endpoints are not a collision`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 80f, endDiaMm = 60f))
        )
        // Proposed starts exactly where taper ends (touching, not overlapping)
        assertTrue(collectAddWarnings(spec, 100f, 200f, false).isEmpty())
    }

    @Test
    fun `out of bounds when overallIsManual produces bounds warning`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        val w = collectAddWarnings(spec, 400f, 200f, overallIsManual = true)
        assertTrue(w.any { it.contains("outside shaft span") })
    }

    @Test
    fun `out of bounds when not manual produces no bounds warning`() {
        val spec = ShaftSpec(overallLengthMm = 500f)
        val w = collectAddWarnings(spec, 400f, 200f, overallIsManual = false)
        assertFalse(w.any { it.contains("outside shaft span") })
    }

    @Test
    fun `within bounds when overallIsManual produces no bounds warning`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        val w = collectAddWarnings(spec, 100f, 200f, overallIsManual = true)
        assertFalse(w.any { it.contains("outside shaft span") })
    }

    @Test
    fun `multiple overlaps produce multiple warnings`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)),
            liners = listOf(Liner(startFromAftMm = 100f, lengthMm = 200f, odMm = 90f)),
        )
        val w = collectAddWarnings(spec, 50f, 200f, false)
        assertTrue(w.size >= 2)
    }

    @Test
    fun `invalid startMm negative returns empty`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(collectAddWarnings(spec, -1f, 100f, true).isEmpty())
    }

    @Test
    fun `invalid lengthMm zero returns empty`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        assertTrue(collectAddWarnings(spec, 0f, 0f, true).isEmpty())
    }
}
