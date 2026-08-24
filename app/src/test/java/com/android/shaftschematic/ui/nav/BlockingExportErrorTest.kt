package com.android.shaftschematic.ui.nav

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockingExportErrorTest {

    @Test
    fun `clean spec returns null`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(Threads(id = "t1", startFromAftMm = 0f, lengthMm = 80f, majorDiaMm = 60f, pitchMm = 2f))
        )
        assertNull(blockingExportError(spec))
    }

    @Test
    fun `spec with only bodies and tapers returns null`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 80f))
        )
        assertNull(blockingExportError(spec))
    }

    @Test
    fun `overlapping threads produce blocking error`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(id = "a", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f),
                Threads(id = "b", startFromAftMm = 50f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f),
            )
        )
        assertNotNull(blockingExportError(spec))
    }

    @Test
    fun `thread sandwiched between bodies produces blocking error`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 200f, diaMm = 80f),
                Body(id = "b2", startFromAftMm = 400f, lengthMm = 600f, diaMm = 80f),
            ),
            threads = listOf(
                Threads(id = "t1", startFromAftMm = 200f, lengthMm = 200f, majorDiaMm = 60f, pitchMm = 2f)
            )
        )
        assertNotNull(blockingExportError(spec))
    }

    @Test
    fun `overlapping liners produce blocking error`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f),
                Liner(id = "l2", startFromAftMm = 250f, lengthMm = 200f, odMm = 90f),
            )
        )
        assertNotNull(blockingExportError(spec))
    }

    @Test
    fun `adjacent non-overlapping liners are allowed`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f),
                Liner(id = "l2", startFromAftMm = 300f, lengthMm = 200f, odMm = 90f),
            )
        )
        assertNull(blockingExportError(spec))
    }

    @Test
    fun `empty spec returns null`() {
        assertNull(blockingExportError(ShaftSpec(overallLengthMm = 1000f)))
    }

    @Test
    fun `taper overlapping a liner produces blocking error`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(id = "tp1", startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 70f, endDiaMm = 90f)
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 250f, lengthMm = 200f, odMm = 95f)
            )
        )
        assertEquals("Overlaps another component", blockingExportError(spec))
    }

    @Test
    fun `taper overlapping a non-excluded thread produces blocking error`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(id = "tp1", startFromAftMm = 0f, lengthMm = 200f, startDiaMm = 60f, endDiaMm = 90f)
            ),
            threads = listOf(
                Threads(id = "t1", startFromAftMm = 150f, lengthMm = 80f, majorDiaMm = 60f, pitchMm = 2f)
            )
        )
        assertEquals("Overlaps another component", blockingExportError(spec))
    }

    @Test
    fun `taper overlapping another taper produces blocking error`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(id = "tp1", startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 70f, endDiaMm = 90f),
                Taper(id = "tp2", startFromAftMm = 250f, lengthMm = 200f, startDiaMm = 90f, endDiaMm = 70f),
            )
        )
        assertEquals("Overlaps another component", blockingExportError(spec))
    }

    /** Bodies are fillers, not collision participants — the resolve layer trims them. */
    @Test
    fun `taper over a stored body does not block export`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 80f)),
            tapers = listOf(
                Taper(id = "tp1", startFromAftMm = 200f, lengthMm = 300f, startDiaMm = 80f, endDiaMm = 60f)
            )
        )
        assertNull(blockingExportError(spec))
    }

    @Test
    fun `clean spec with tapers returns null`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 700f, diaMm = 80f)),
            tapers = listOf(
                Taper(id = "tp1", startFromAftMm = 700f, lengthMm = 200f, startDiaMm = 80f, endDiaMm = 60f)
            ),
            liners = listOf(Liner(id = "l1", startFromAftMm = 100f, lengthMm = 300f, odMm = 95f)),
            threads = listOf(
                Threads(id = "t1", startFromAftMm = 900f, lengthMm = 100f, majorDiaMm = 55f, pitchMm = 2f)
            )
        )
        assertNull(blockingExportError(spec))
    }

    /** Endpoints touching are not an overlap — the shared predicate's eps rule. */
    @Test
    fun `taper butted against a liner is allowed`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(id = "tp1", startFromAftMm = 100f, lengthMm = 200f, startDiaMm = 70f, endDiaMm = 90f)
            ),
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 300f, lengthMm = 200f, odMm = 95f)
            )
        )
        assertNull(blockingExportError(spec))
    }
}
