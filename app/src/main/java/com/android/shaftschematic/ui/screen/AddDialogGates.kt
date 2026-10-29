package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.ui.util.MUST_BE_NON_NEGATIVE_MSG
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.parseFractionOrDecimal

/**
 * File: AddDialogGates.kt
 * Layer: UI → Screen
 *
 * Confirm-button enablement for the Add dialogs, extracted from the composables in
 * `AddComponentDialogs.kt` so the gates can be unit-tested without a Compose harness.
 *
 * These are the *blocking* gates only — they decide whether "Add" is tappable at all.
 * Non-blocking advisories (collision warnings, the "Add Anyway?" confirmation) run after
 * the button is pressed and are deliberately not modelled here.
 *
 * All measurements are canonical millimeters. Each gate takes already-parsed values, so a
 * field that failed to parse arrives as a sentinel (`-1f` for an unresolvable start, `0f`
 * for an unparsed length/diameter) and fails the gate naturally.
 */

/** A FWD-measured liner or taper whose length would carry it past the AFT face. */
const val ADD_PAST_AFT_END_MSG = "Runs past the AFT end of the shaft"

/**
 * The Liner/Taper dialogs' physical start (mm from AFT) for the typed [enteredMm]: as typed from
 * AFT; `OAL − entered − length` from FWD. `-1f` when the entry is unusable — not entered yet, or
 * a FWD entry that would put the start before the AFT face, which is refused
 * ([ADD_PAST_AFT_END_MSG], [addDialogFwdStartPastAft]) rather than clamped to 0: a clamp stores
 * a different FWD distance from the one typed. With no OAL yet a FWD distance has no face to
 * measure from, and the start is placed at 0.
 */
fun addDialogPhysStartMm(isFwd: Boolean, enteredMm: Float, lengthMm: Float, oalMm: Float): Float {
    if (!isFwd) return enteredMm
    if (enteredMm < 0f || lengthMm <= 0f) return -1f
    if (oalMm <= 0f) return 0f
    val start = oalMm - enteredMm - lengthMm
    return when {
        start >= 0f -> start
        start > -1e-3f -> 0f
        else -> -1f
    }
}

/** True when a FWD-measured [enteredMm] + [lengthMm] would reach past the AFT face. */
fun addDialogFwdStartPastAft(isFwd: Boolean, enteredMm: Float, lengthMm: Float, oalMm: Float): Boolean =
    isFwd && oalMm > 0f && enteredMm >= 0f && lengthMm > 0f && oalMm - enteredMm - lengthMm <= -1e-3f

/**
 * "Must be ≥ 0" under a Start that reads as a negative number ([enteredMm], null when the text is
 * blank or unreadable — those have their own handling). The number pad has a `-` and the dialogs
 * filter nothing, so a typed `-5` otherwise left Add disabled with nothing marked. The cards'
 * wording for the same refusal ([MUST_BE_NON_NEGATIVE_MSG]).
 */
fun addDialogNegativeStartError(enteredMm: Float?): String? =
    if (enteredMm != null && enteredMm < 0f) MUST_BE_NON_NEGATIVE_MSG else null

/**
 * Whether every optional numeric field an Add dialog SHOWS is blank or reads as a length — the
 * fields outside the required set (keyway W/D/L/inset, blend and seal-area lengths, relief length
 * and Ø, shoulder length and Ø, the taper's S.E.T./L.E.T.). A field showing "Not a number" holds
 * Add, as a required field does: submitting it would store 0 in place of the typed value. A null
 * entry is a field whose section is hidden (keyway unticked, relief off, seal unticked) and never
 * gates; blank stays allowed, as before.
 */
fun optionalEntriesReadable(texts: List<String?>): Boolean =
    texts.all { it == null || it.isBlank() || isLengthText(it) }

/** The keyway section's fields for [optionalEntriesReadable]: none while the gate is off. */
fun keywayEntryTexts(
    enabled: Boolean,
    captured: Boolean,
    widthText: String,
    depthText: String,
    lengthText: String,
    insetText: String,
): List<String?> =
    if (!enabled) emptyList()
    else listOf(widthText, depthText, lengthText, insetText.takeIf { captured })

/** The thread-relief section's fields for [optionalEntriesReadable]: none while it is off. */
fun threadReliefEntryTexts(on: Boolean, lengthText: String, diaText: String): List<String?> =
    if (on) listOf(lengthText, diaText) else emptyList()

/** Add Body: needs a placeable start and positive length and diameter. */
fun bodyAddEnabled(startMm: Float, lengthMm: Float, diaMm: Float): Boolean =
    startMm >= 0f && lengthMm > 0f && diaMm > 0f

/**
 * Add Liner: as body, plus [startError] from the AFT/FWD start-overlap validator. A FWD-
 * referenced start that resolves off the shaft arrives as a negative [physStartMm].
 */
fun linerAddEnabled(
    physStartMm: Float,
    lengthMm: Float,
    odMm: Float,
    startError: String?
): Boolean =
    physStartMm >= 0f && lengthMm > 0f && odMm > 0f && startError == null

/**
 * Add Thread: an excluded thread (`countInOal == false`) hangs off the shaft end and has no
 * meaningful start, so its start is not gated — the AFT/FWD chip positions it instead.
 */
fun threadAddEnabled(
    countInOal: Boolean,
    startMm: Float,
    lengthMm: Float,
    majorDiaMm: Float,
    tpi: Float,
    startError: String?
): Boolean =
    (!countInOal || startMm >= 0f) &&
        lengthMm > 0f && majorDiaMm > 0f && tpi > 0f && startError == null

/**
 * Add Coupler Bolt Slot: spacing only matters for a multi-hole pattern, and depth only for
 * a blind hole — gating on either unconditionally would block valid single/through slots.
 */
fun couplerBoltSlotAddEnabled(
    physStartMm: Float,
    holeDiaMm: Float,
    count: Int,
    spacingMm: Float,
    through: Boolean,
    depthMm: Float,
    boundsError: String?
): Boolean =
    physStartMm >= 0f && holeDiaMm > 0f && count >= 1 &&
        (count == 1 || spacingMm > 0f) &&
        (through || depthMm > 0f) &&
        boundsError == null

/**
 * Physical position (from AFT, mm) of a coupler bolt row's aft-most hole center. [enteredMm] is
 * the typed distance from the chosen face to the NEAREST hole center; measured from FWD it
 * locates the fwd-most hole and the row extends aft. `-1f` when the entry is unusable.
 */
fun couplerBoltSlotPhysStartMm(
    oalMm: Float,
    isFwd: Boolean,
    enteredMm: Float,
    count: Int,
    spacingMm: Float,
): Float {
    if (enteredMm < 0f) return -1f
    if (!isFwd) return enteredMm
    val rowSpanMm = (count - 1).coerceAtLeast(0) * spacingMm.coerceAtLeast(0f)
    return (oalMm - enteredMm - rowSpanMm).coerceAtLeast(0f)
}

/**
 * Every hole (center ± radius) must lie on the shaft — mirrors `CouplerBoltSlot.isValid()`.
 * Only checked once an OAL exists and the row is fully entered; null when the row fits.
 */
fun couplerBoltSlotBoundsError(
    oalMm: Float,
    physStartMm: Float,
    holeDiaMm: Float,
    count: Int,
    spacingMm: Float,
): String? {
    if (!(oalMm > 0f && physStartMm >= 0f && holeDiaMm > 0f && count >= 1)) return null
    val rowSpanMm = (count - 1).coerceAtLeast(0) * spacingMm.coerceAtLeast(0f)
    val lastCenterMm = physStartMm + rowSpanMm
    val eps = 1e-3f
    return when {
        physStartMm - holeDiaMm * 0.5f < -eps -> BOLT_ROW_PAST_AFT_MSG
        lastCenterMm + holeDiaMm * 0.5f > oalMm + eps -> "Row extends past the end of the shaft"
        else -> null
    }
}

/** A bolt row whose aft-most hole would sit before the AFT face. */
const val BOLT_ROW_PAST_AFT_MSG = "Row extends past the AFT end of the shaft"

/** The bolt-slot Count rule, on the card and in `AddCouplerBoltSlotDialog` alike. */
const val BOLT_SLOT_COUNT_MSG = "Must be a whole number ≥ 1"

/** A typed bolt-slot count: a whole number ≥ 1, or null. Never coerced — `0` is not `1`. */
fun boltSlotCountOrNull(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 1 }

/**
 * Why a typed bolt-slot Count cannot be used — [BOLT_SLOT_COUNT_MSG] for a number that is not a
 * whole number ≥ 1 (`0`, `2.5`, `1/2`). Null for a valid count, for blank, and for text that is
 * not a number at all (the field's own "not a number" covers that). Shared by the card's
 * validator and the dialog's error line, so `0` is refused and named on both instead of being
 * stored as 1 by the dialog and dropped without a word by the card (emulator QA).
 */
fun boltSlotCountError(text: String): String? = when {
    text.isBlank() || parseFractionOrDecimal(text) == null -> null
    boltSlotCountOrNull(text) == null -> BOLT_SLOT_COUNT_MSG
    else -> null
}

/**
 * The position (mm from the chosen face to the nearest hole center) `AddCouplerBoltSlotDialog`
 * opens with, so it opens on a row that FITS and "Add" is live. From FWD — the default — a
 * seed of 0 put the hole center on the shaft end, half the hole hung off it, and the dialog
 * opened red with "Add" disabled (on-device report). The seed is [fromFaceMm] (1 in) or one
 * hole Ø, whichever is larger; from AFT the suggested start (the end of the last component) is
 * kept whenever the row fits there.
 */
fun couplerBoltSlotSeedMm(
    oalMm: Float,
    isFwd: Boolean,
    suggestedAftStartMm: Float,
    holeDiaMm: Float,
    count: Int,
    spacingMm: Float,
    fromFaceMm: Float,
): Float {
    val faceSeed = maxOf(fromFaceMm, holeDiaMm)
    if (isFwd) return faceSeed
    val fits = suggestedAftStartMm >= 0f && couplerBoltSlotBoundsError(
        oalMm, suggestedAftStartMm, holeDiaMm, count, spacingMm,
    ) == null && suggestedAftStartMm - holeDiaMm * 0.5f >= 0f
    return if (fits) suggestedAftStartMm else faceSeed
}

/**
 * Add Taper: start and length as usual, plus two rate-specific conditions.
 *
 * @param autoRate true when the rate is derived from Length + SET + LET rather than typed.
 * @param autoRateIssue non-null when Auto cannot derive (only one end diameter given).
 * @param manualRateBlock non-null when a typed rate is unusable. Only consulted in Manual.
 * @param hasBothEnds both SET and LET entered — diameters need no derivation.
 * @param hasExactlyOneEnd exactly one of SET/LET entered — the other must come from the rate.
 * @param manualRateParses the typed rate parses unambiguously. A bare "1" does not count:
 *   it is too ambiguous to derive a diameter from, so deriving the missing end needs a real
 *   rate. Callers pass `parseTaperRateText(rateText, allowAmbiguousBareOne = false) != null`.
 */
fun taperAddEnabled(
    physStartMm: Float,
    lengthMm: Float,
    autoRate: Boolean,
    autoRateIssue: String?,
    manualRateBlock: String?,
    hasBothEnds: Boolean,
    hasExactlyOneEnd: Boolean,
    manualRateParses: Boolean
): Boolean {
    val rateModeValid = if (autoRate) autoRateIssue == null else manualRateBlock == null
    val canResolveDiameters =
        hasBothEnds || (hasExactlyOneEnd && !autoRate && manualRateParses)
    return physStartMm >= 0f && lengthMm > 0f && rateModeValid && canResolveDiameters
}
