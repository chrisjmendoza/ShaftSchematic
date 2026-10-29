package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.ui.order.ComponentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `startOverlapErrorMm` — the inline Start-field validator on the carousel cards and Add dialogs.
 *
 * It flags a placement that would land on another precision component. Bodies are deliberately
 * not obstacles: a body runs under a liner and up against a taper as a matter of course.
 */
class StartOverlapValidationTest {

    @Test
    fun `thread start at 0 is allowed even if body spans from 0`() {
        // Bodies are not obstacles for the start validator — a body runs under/against other
        // components normally, so a thread at the AFT end over a body is fine.
        val sixteenInchesMm = 16f * 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = sixteenInchesMm, diaMm = 80f)),
        )

        val err = startOverlapErrorMm(
            spec = spec,
            selfId = "thNew",
            selfKind = ComponentKind.THREAD,
            selfLengthMm = 50f,
            startMm = 0f,
        )

        assertNull(err)
    }

    @Test
    fun `thread start at 0 is allowed even if a taper spans from 0`() {
        val sixteenInchesMm = 16f * 25.4f
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(Taper(id = "t1", startFromAftMm = 0f, lengthMm = sixteenInchesMm, startDiaMm = 80f, endDiaMm = 60f)),
        )

        val err = startOverlapErrorMm(
            spec = spec,
            selfId = "thNew",
            selfKind = ComponentKind.THREAD,
            selfLengthMm = 50f,
            startMm = 0f,
        )

        assertNull(err)
    }

    @Test
    fun `thread overlapping another thread is rejected`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            threads = listOf(
                Threads(id = "a", startFromAftMm = 0f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f),
                Threads(id = "b", startFromAftMm = 50f, lengthMm = 100f, majorDiaMm = 60f, pitchMm = 2f),
            )
        )

        val err = startOverlapErrorMm(
            spec = spec,
            selfId = "b",
            selfKind = ComponentKind.THREAD,
            selfLengthMm = 100f,
            startMm = 50f,
        )

        // The partner is named by the title its card shows.
        assertEquals("Overlaps AFT Thread", err)
    }

    @Test
    fun `negative start is rejected for all kinds`() {
        val spec = ShaftSpec(overallLengthMm = 1000f)
        listOf(ComponentKind.THREAD, ComponentKind.LINER).forEach { kind ->
            val err = startOverlapErrorMm(spec, "new", kind, 100f, -1f)
            assertEquals("Must be ≥ 0", err)
        }
    }

    @Test
    fun `thread sandwiched between bodies is rejected`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(
                Body(id = "b1", startFromAftMm = 0f, lengthMm = 300f, diaMm = 80f),
                Body(id = "b2", startFromAftMm = 500f, lengthMm = 500f, diaMm = 80f),
            )
        )
        val err = startOverlapErrorMm(spec, "new", ComponentKind.THREAD, 200f, 300f)
        assertEquals("Thread must be at a shaft end, not between components", err)
    }

    @Test
    fun `thread at FWD end adjacent to body is allowed`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 900f, diaMm = 80f))
        )
        // Thread starts at body end (900mm), no body ahead of it → not sandwiched
        val err = startOverlapErrorMm(spec, "new", ComponentKind.THREAD, 100f, 900f)
        assertNull(err)
    }

    @Test
    fun `liner overlapping another liner is rejected`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f))
        )
        val err = startOverlapErrorMm(spec, "new", ComponentKind.LINER, 200f, 250f)
        assertEquals("Overlaps AFT Liner", err)
    }

    @Test
    fun `adjacent liners touching at endpoint are allowed`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f))
        )
        // New liner starts exactly where l1 ends (300mm) — touching but not overlapping
        val err = startOverlapErrorMm(spec, "new", ComponentKind.LINER, 200f, 300f)
        assertNull(err)
    }

    @Test
    fun `bodies and tapers have no collision group and always pass`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 80f))
        )
        assertNull(startOverlapErrorMm(spec, "new", ComponentKind.BODY, 500f, 0f))
        assertNull(startOverlapErrorMm(spec, "new", ComponentKind.TAPER, 500f, 0f))
    }

    /* ── Length must be > 0 (carousel card field gate) ───────────────────────── */

    @Test
    fun `a positive length passes`() {
        assertNull(positiveLengthErrorMm(0.001f))
        assertNull(positiveLengthErrorMm(500f))
    }

    @Test
    fun `zero and negative lengths are rejected`() {
        assertEquals("Must be > 0", positiveLengthErrorMm(0f))
        assertEquals("Must be > 0", positiveLengthErrorMm(-1f))
    }

    /** Unparseable text is the field's own "not a number" notice, not a too-short length. */
    @Test
    fun `an unparsed value is deferred to the field's parse check`() {
        assertNull(positiveLengthErrorMm(null))
    }

    /* ── Diameter must be > 0 (carousel card field gate) ─────────────────────── */

    @Test
    fun `a zero or negative diameter is refused, an unparsed one deferred`() {
        assertEquals("Must be > 0", positiveDiaErrorMm(0f))
        assertEquals("Must be > 0", positiveDiaErrorMm(-2f))
        assertNull(positiveDiaErrorMm(254f))
        assertNull(positiveDiaErrorMm(null))
    }

    /* ── The Length field's span check ───────────────────────────────────────── */

    private val twoLiners = ShaftSpec(
        overallLengthMm = 1000f,
        liners = listOf(
            Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f),
            Liner(id = "l2", startFromAftMm = 500f, lengthMm = 200f, odMm = 90f),
        ),
    )

    /** A Length that grows a liner into its neighbour is refused like the same span as a Start. */
    @Test
    fun `a length that runs into another liner is refused and names it`() {
        assertEquals("Overlaps FWD Liner", spanOverlapErrorMm(twoLiners, "l1", ComponentKind.LINER, 100f, 500f))
        assertNull(spanOverlapErrorMm(twoLiners, "l1", ComponentKind.LINER, 100f, 400f))
    }

    /** Past the OAL is the bounds warning's job (an OAL shrink makes it legitimately), not this gate's. */
    @Test
    fun `a length that only runs past the OAL is not refused`() {
        val one = twoLiners.copy(liners = twoLiners.liners.take(1))
        assertNull(spanOverlapErrorMm(one, "l1", ComponentKind.LINER, 100f, 99_999f))
    }

    @Test
    fun `several partners are all named, AFT to FWD`() {
        val three = twoLiners.copy(
            liners = twoLiners.liners + Liner(id = "l3", startFromAftMm = 800f, lengthMm = 100f, odMm = 90f),
        )
        val titles = componentCardTitles(three)
        assertEquals(
            "Overlaps ${titles["l2"]}, ${titles["l3"]}",
            spanOverlapErrorMm(three, "l1", ComponentKind.LINER, 100f, 850f),
        )
    }

    @Test
    fun `a user label is the name a message uses`() {
        val labelled = twoLiners.copy(liners = listOf(twoLiners.liners[0], twoLiners.liners[1].copy(label = "Stern seal")))
        assertEquals("Overlaps Stern seal", spanOverlapErrorMm(labelled, "l1", ComponentKind.LINER, 100f, 500f))
    }

    /** Tapers have no collision group: a taper's overlaps are its card chip's business. */
    @Test
    fun `tapers and bodies get no span check`() {
        assertNull(spanOverlapErrorMm(twoLiners, "t", ComponentKind.TAPER, 0f, 1000f))
        assertNull(spanOverlapErrorMm(twoLiners, "b", ComponentKind.BODY, 0f, 1000f))
    }

    /* ── The card chip and the dialogs' inline line ──────────────────────────── */

    private val linerOverTaper = ShaftSpec(
        overallLengthMm = 1000f,
        tapers = listOf(Taper(id = "t1", startFromAftMm = 800f, lengthMm = 200f, startDiaMm = 120f, endDiaMm = 100f)),
        liners = listOf(Liner(id = "l1", startFromAftMm = 700f, lengthMm = 200f, odMm = 130f)),
    )

    /** The chip names the partner the way its card is titled ("FWD Taper"), not "another component". */
    @Test
    fun `the card chip names the partner by card title`() {
        assertEquals("Overlaps FWD Taper", collisionChipMessage(linerOverTaper, "l1"))
        assertEquals("Overlaps FWD Liner", collisionChipMessage(linerOverTaper, "t1"))
        assertNull(collisionChipMessage(twoLiners, "l1"))
    }

    @Test
    fun `a proposed span names what it would overlap, and a free one nothing`() {
        assertEquals("Overlaps FWD Taper", proposedSpanOverlapMessage(linerOverTaper, 920f, 80f))
        assertNull(proposedSpanOverlapMessage(linerOverTaper, 0f, 100f))
        assertNull("not entered yet", proposedSpanOverlapMessage(linerOverTaper, -1f, 100f))
    }
}

