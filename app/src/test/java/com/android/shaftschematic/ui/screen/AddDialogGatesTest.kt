package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Add-dialog confirm-button gates — the blocking half of "blocking-dialog
 * behavior" (TODO §5.2).
 *
 * These decide whether "Add" is tappable. The non-blocking advisories (collision
 * warnings, "Add Anyway?") run after the press and are covered elsewhere.
 */
class AddDialogGatesTest {

    // ── Body ─────────────────────────────────────────────────────────────────────

    @Test
    fun `body needs positive length and diameter`() {
        assertTrue(bodyAddEnabled(startMm = 0f, lengthMm = 100f, diaMm = 80f))
        assertFalse(bodyAddEnabled(startMm = 0f, lengthMm = 0f, diaMm = 80f))
        assertFalse(bodyAddEnabled(startMm = 0f, lengthMm = 100f, diaMm = 0f))
    }

    @Test
    fun `body at the aft face is allowed but a negative start is not`() {
        assertTrue(bodyAddEnabled(startMm = 0f, lengthMm = 100f, diaMm = 80f))
        assertFalse(bodyAddEnabled(startMm = -1f, lengthMm = 100f, diaMm = 80f))
    }

    // ── Liner ────────────────────────────────────────────────────────────────────

    @Test
    fun `liner needs positive length and OD`() {
        assertTrue(linerAddEnabled(physStartMm = 50f, lengthMm = 200f, odMm = 90f, startError = null))
        assertFalse(linerAddEnabled(physStartMm = 50f, lengthMm = 0f, odMm = 90f, startError = null))
        assertFalse(linerAddEnabled(physStartMm = 50f, lengthMm = 200f, odMm = 0f, startError = null))
    }

    @Test
    fun `liner is blocked by a start-overlap error`() {
        assertFalse(
            linerAddEnabled(
                physStartMm = 50f, lengthMm = 200f, odMm = 90f,
                startError = "Overlaps Body #1"
            )
        )
    }

    @Test
    fun `liner whose FWD start resolves off the shaft is blocked`() {
        // AddLinerDialog encodes an unresolvable FWD-referenced start as -1f.
        assertFalse(linerAddEnabled(physStartMm = -1f, lengthMm = 200f, odMm = 90f, startError = null))
    }

    // ── Thread ───────────────────────────────────────────────────────────────────

    @Test
    fun `thread counted in OAL needs a valid start`() {
        assertTrue(
            threadAddEnabled(
                countInOal = true, startMm = 0f, lengthMm = 40f,
                majorDiaMm = 60f, tpi = 8f, startError = null
            )
        )
        assertFalse(
            threadAddEnabled(
                countInOal = true, startMm = -5f, lengthMm = 40f,
                majorDiaMm = 60f, tpi = 8f, startError = null
            )
        )
    }

    @Test
    fun `thread excluded from OAL ignores its start entirely`() {
        // An excluded thread hangs off the shaft end; the AFT/FWD chip positions it, and
        // the Start field is hidden. A leftover negative start must not block the add.
        assertTrue(
            threadAddEnabled(
                countInOal = false, startMm = -5f, lengthMm = 40f,
                majorDiaMm = 60f, tpi = 8f, startError = null
            )
        )
    }

    @Test
    fun `thread still needs length, major diameter and TPI when excluded`() {
        assertFalse(
            threadAddEnabled(
                countInOal = false, startMm = -5f, lengthMm = 0f,
                majorDiaMm = 60f, tpi = 8f, startError = null
            )
        )
        assertFalse(
            threadAddEnabled(
                countInOal = false, startMm = -5f, lengthMm = 40f,
                majorDiaMm = 0f, tpi = 8f, startError = null
            )
        )
        assertFalse(
            threadAddEnabled(
                countInOal = false, startMm = -5f, lengthMm = 40f,
                majorDiaMm = 60f, tpi = 0f, startError = null
            )
        )
    }

    @Test
    fun `thread is blocked by a start-overlap error`() {
        assertFalse(
            threadAddEnabled(
                countInOal = true, startMm = 10f, lengthMm = 40f,
                majorDiaMm = 60f, tpi = 8f, startError = "Overlaps Body #1"
            )
        )
    }

    // ── Coupler bolt slot ────────────────────────────────────────────────────────

    private fun slot(
        physStartMm: Float = 100f,
        holeDiaMm: Float = 12f,
        count: Int = 1,
        spacingMm: Float = 0f,
        through: Boolean = true,
        depthMm: Float = 0f,
        boundsError: String? = null
    ) = couplerBoltSlotAddEnabled(
        physStartMm, holeDiaMm, count, spacingMm, through, depthMm, boundsError
    )

    @Test
    fun `single through slot needs neither spacing nor depth`() {
        assertTrue(slot(count = 1, spacingMm = 0f, through = true, depthMm = 0f))
    }

    @Test
    fun `multi-hole slot needs positive spacing`() {
        assertFalse(slot(count = 3, spacingMm = 0f))
        assertTrue(slot(count = 3, spacingMm = 25f))
    }

    @Test
    fun `blind slot needs positive depth`() {
        assertFalse(slot(through = false, depthMm = 0f))
        assertTrue(slot(through = false, depthMm = 20f))
    }

    @Test
    fun `slot needs a positive hole diameter and at least one hole`() {
        assertFalse(slot(holeDiaMm = 0f))
        assertFalse(slot(count = 0))
    }

    @Test
    fun `slot is blocked by a bounds error`() {
        assertFalse(slot(boundsError = "Extends past the FWD face"))
    }

    // ── Taper ────────────────────────────────────────────────────────────────────

    private fun taper(
        physStartMm: Float = 0f,
        lengthMm: Float = 400f,
        autoRate: Boolean = true,
        autoRateIssue: String? = null,
        manualRateBlock: String? = null,
        hasBothEnds: Boolean = true,
        hasExactlyOneEnd: Boolean = false,
        manualRateParses: Boolean = false
    ) = taperAddEnabled(
        physStartMm, lengthMm, autoRate, autoRateIssue, manualRateBlock,
        hasBothEnds, hasExactlyOneEnd, manualRateParses
    )

    @Test
    fun `auto rate with both ends is addable`() {
        assertTrue(taper(autoRate = true, hasBothEnds = true))
    }

    @Test
    fun `auto rate with only one end is blocked`() {
        // Auto cannot fabricate a rate from a missing end — the dialog says so and the
        // gate must agree.
        assertFalse(
            taper(
                autoRate = true,
                autoRateIssue = "Auto needs Length + SET + LET. Switch to Manual to derive the missing end",
                hasBothEnds = false,
                hasExactlyOneEnd = true
            )
        )
    }

    @Test
    fun `manual rate derives the missing end when the rate parses`() {
        assertTrue(
            taper(
                autoRate = false,
                hasBothEnds = false,
                hasExactlyOneEnd = true,
                manualRateParses = true
            )
        )
    }

    @Test
    fun `manual rate with one end is blocked when the rate does not parse`() {
        // Covers the ambiguous bare "1": too vague to derive a diameter from.
        assertFalse(
            taper(
                autoRate = false,
                hasBothEnds = false,
                hasExactlyOneEnd = true,
                manualRateParses = false
            )
        )
    }

    @Test
    fun `manual rate with no ends at all is blocked`() {
        assertFalse(
            taper(
                autoRate = false,
                hasBothEnds = false,
                hasExactlyOneEnd = false,
                manualRateParses = true
            )
        )
    }

    @Test
    fun `a blocking manual rate message blocks the add`() {
        assertFalse(
            taper(
                autoRate = false,
                manualRateBlock = "Rate cannot be zero",
                hasBothEnds = true
            )
        )
    }

    @Test
    fun `a blocking manual rate message is ignored in auto mode`() {
        // manualRateBlock is only computed in Manual; pin that Auto does not consult it.
        assertTrue(taper(autoRate = true, manualRateBlock = "stale", hasBothEnds = true))
    }

    @Test
    fun `taper needs a placeable start and positive length`() {
        assertFalse(taper(physStartMm = -1f))
        assertFalse(taper(lengthMm = 0f))
    }
}
