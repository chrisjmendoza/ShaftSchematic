package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.ui.config.AddDefaultsConfig
import com.android.shaftschematic.ui.viewmodel.SessionAddDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.isUnitlessNumberText
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

    /**
     * The dialog opens on its session defaults (0.5" hole, 2 holes at 2") measured from FWD.
     * A FWD seed of 0 put the fwd-most hole center ON the shaft end: half the hole hung off it,
     * "Row extends past the end of the shaft", Add disabled (on-device report). Whatever face
     * the dialog opens on, the seeded row must fit and Add must be live.
     */
    private fun opensValid(oalMm: Float, isFwd: Boolean, suggestedAftStartMm: Float) {
        val d = SessionAddDefaults.initial()
        val entered = couplerBoltSlotSeedMm(
            oalMm = oalMm, isFwd = isFwd, suggestedAftStartMm = suggestedAftStartMm,
            holeDiaMm = d.slotHoleDiaMm, count = d.slotCount, spacingMm = d.slotSpacingMm,
            fromFaceMm = AddDefaultsConfig.SLOT_FROM_END_IN * 25.4f,
        )
        val phys = couplerBoltSlotPhysStartMm(oalMm, isFwd, entered, d.slotCount, d.slotSpacingMm)
        val bounds = couplerBoltSlotBoundsError(oalMm, phys, d.slotHoleDiaMm, d.slotCount, d.slotSpacingMm)
        assertNull("isFwd=$isFwd suggested=$suggestedAftStartMm", bounds)
        assertTrue(
            couplerBoltSlotAddEnabled(phys, d.slotHoleDiaMm, d.slotCount, d.slotSpacingMm, true, d.slotDepthMm, bounds)
        )
    }

    @Test
    fun `the bolt slot dialog opens valid from FWD`() {
        opensValid(oalMm = 9000f, isFwd = true, suggestedAftStartMm = 0f)
        assertEquals(25.4f, couplerBoltSlotSeedMm(9000f, true, 0f, 12.7f, 2, 50.8f, 25.4f), 0f)
    }

    @Test
    fun `the bolt slot dialog opens valid from AFT even when the suggested start is off the shaft`() {
        // No components: the suggested start is the AFT face itself.
        opensValid(oalMm = 9000f, isFwd = false, suggestedAftStartMm = 0f)
        // Components to the FWD end: the suggested start is the OAL.
        opensValid(oalMm = 9000f, isFwd = false, suggestedAftStartMm = 9000f)
        // A suggested start the row fits at is kept.
        assertEquals(3000f, couplerBoltSlotSeedMm(9000f, false, 3000f, 12.7f, 2, 50.8f, 25.4f), 0f)
    }

    @Test
    fun `the seed is never less than one hole diameter from the face`() {
        assertEquals(60f, couplerBoltSlotSeedMm(9000f, true, 0f, 60f, 1, 0f, 25.4f), 0f)
    }

    @Test
    fun `a zero FWD distance is what failed the bounds check`() {
        val phys = couplerBoltSlotPhysStartMm(9000f, isFwd = true, enteredMm = 0f, count = 2, spacingMm = 50.8f)
        assertNotNull(couplerBoltSlotBoundsError(9000f, phys, 12.7f, 2, 50.8f))
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

    // ── Unreadable entries are named under the field ────────────────────────────

    @Test
    fun `non-blank text that does not read is called out, blank is not`() {
        assertEquals(NOT_A_NUMBER_ERROR, unreadableEntryError("12ft", ::isLengthText))
        assertEquals(NOT_A_NUMBER_ERROR, unreadableEntryError("1,500", ::isLengthText))
        assertEquals(NOT_A_NUMBER_ERROR, unreadableEntryError("10--1/4", ::isLengthText))
        assertNull(unreadableEntryError("", ::isLengthText))
        assertNull(unreadableEntryError("   ", ::isLengthText))
    }

    @Test
    fun `the length fields read the same spellings as the cards`() {
        // The card's CommitNum and the dialog's CommitNumField share isLengthText.
        for (typed in listOf("10-1/4", "10-1/4\"", "25-3/16", "260mm", "11\"", "1,5", "12 3/4", "10.25in")) {
            assertNull("$typed must read", unreadableEntryError(typed, ::isLengthText))
        }
    }

    @Test
    fun `unitless fields refuse a unit suffix`() {
        assertEquals(NOT_A_NUMBER_ERROR, unreadableEntryError("4tpi", ::isUnitlessNumberText))
        assertEquals(NOT_A_NUMBER_ERROR, unreadableEntryError("4in", ::isUnitlessNumberText))
        assertNull(unreadableEntryError("4", ::isUnitlessNumberText))
    }

    // ── Bolt-slot Count: a whole number ≥ 1, never coerced ──────────────────────

    /** Coercing `0` to 1 in the dialog, or dropping it without a word on the card, hides the refusal. */
    @Test
    fun `a bolt-slot count that is not a whole number of at least 1 is named, not coerced`() {
        for (typed in listOf("0", "2.5", "1/2", "-1")) {
            assertEquals("$typed is not a count", BOLT_SLOT_COUNT_MSG, boltSlotCountError(typed))
            assertNull("$typed is never coerced", boltSlotCountOrNull(typed))
        }
        assertNull(boltSlotCountError("4"))
        assertEquals(4, boltSlotCountOrNull(" 4 "))
        assertNull("blank is not entered yet", boltSlotCountError(""))
        assertNull("not a number is the field's own line", boltSlotCountError("abc"))
    }

    @Test
    fun `the dialogs name a zero they need positive`() {
        assertEquals("Must be > 0", nonPositiveEntryError("0"))
        assertEquals("Must be > 0", nonPositiveEntryError("0mm"))
        assertNull(nonPositiveEntryError("1/4"))
        assertNull(nonPositiveEntryError(""))
        assertNull(nonPositiveEntryError("abc"))
    }

    // ── FWD-measured Start in the Liner/Taper dialogs ───────────────────────────

    @Test
    fun `a FWD start resolves against the OAL and is never clamped onto the AFT face`() {
        assertEquals(700f, addDialogPhysStartMm(isFwd = true, enteredMm = 100f, lengthMm = 200f, oalMm = 1000f), 0f)
        assertEquals(300f, addDialogPhysStartMm(isFwd = false, enteredMm = 300f, lengthMm = 200f, oalMm = 1000f), 0f)
        // 900 from FWD + 200 long reaches 100 past the AFT face: refused, not stored at 0.
        assertEquals(-1f, addDialogPhysStartMm(isFwd = true, enteredMm = 900f, lengthMm = 200f, oalMm = 1000f), 0f)
        assertTrue(addDialogFwdStartPastAft(isFwd = true, enteredMm = 900f, lengthMm = 200f, oalMm = 1000f))
        assertFalse(addDialogFwdStartPastAft(isFwd = true, enteredMm = 800f, lengthMm = 200f, oalMm = 1000f))
        assertFalse(addDialogFwdStartPastAft(isFwd = false, enteredMm = 900f, lengthMm = 200f, oalMm = 1000f))
        assertFalse("no OAL, no face to be past", addDialogFwdStartPastAft(true, 900f, 200f, 0f))
    }

    // ── Visible optional fields ─────────────────────────────────────────────────

    @Test
    fun `an unreadable keyway width holds Add while the keyway is ticked`() {
        // KW W showed "Not a number" with Add live, and the keyway landed with W = 0.
        assertFalse(optionalEntriesReadable(keywayEntryTexts(true, false, "1,500", "1/4", "3", "")))
        // Unticked, the hidden fields never gate.
        assertTrue(optionalEntriesReadable(keywayEntryTexts(false, false, "1,500", "1/4", "3", "")))
    }

    @Test
    fun `a blank keyway field with the gate on behaves as before`() {
        assertTrue(optionalEntriesReadable(keywayEntryTexts(true, false, "1", "1/2", "", "")))
        assertTrue(optionalEntriesReadable(keywayEntryTexts(true, false, "", "", "", "")))
    }

    @Test
    fun `the captured inset gates only while Captured is on`() {
        assertFalse(optionalEntriesReadable(keywayEntryTexts(true, true, "1", "1/2", "3", "abc")))
        assertTrue(optionalEntriesReadable(keywayEntryTexts(true, false, "1", "1/2", "3", "abc")))
    }

    @Test
    fun `an unreadable relief holds Add Thread, and switching the relief off releases it`() {
        assertFalse(optionalEntriesReadable(threadReliefEntryTexts(true, "0.25", "abc")))
        assertTrue(optionalEntriesReadable(threadReliefEntryTexts(false, "0.25", "abc")))
        assertTrue(optionalEntriesReadable(threadReliefEntryTexts(true, "0.25", "3 1/2")))
    }

    @Test
    fun `hidden fields and blank fields never gate, suffixed lengths read`() {
        assertTrue(optionalEntriesReadable(listOf(null, "", "  ", "2", "50mm", "10-1/4\"")))
        assertFalse(optionalEntriesReadable(listOf(null, "12ft")))
    }

    // ── Negative Start ─────────────────────────────────────────────────────────

    @Test
    fun `a negative Start says why Add is disabled`() {
        assertEquals("Must be ≥ 0", addDialogNegativeStartError(-5f))
        assertEquals(com.android.shaftschematic.ui.util.MUST_BE_NON_NEGATIVE_MSG, addDialogNegativeStartError(-0.1f))
        assertNull(addDialogNegativeStartError(0f))
        assertNull(addDialogNegativeStartError(12f))
        // Blank or unreadable text has its own handling.
        assertNull(addDialogNegativeStartError(null))
        assertFalse(bodyAddEnabled(startMm = -5f, lengthMm = 100f, diaMm = 80f))
    }
}
