package com.android.shaftschematic.ui.screen

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
