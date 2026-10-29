package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collisionPartnerIdsForSpan
import com.android.shaftschematic.util.UnitSystem

/**
 * Returns human-readable warning strings for a proposed component at [startMm] / [lengthMm].
 *
 * Checks existing tapers, non-excluded threads, and liners.
 * Bodies are intentionally skipped — they auto-split to accommodate any new component.
 * Once the shaft has an overall length, also warns if the component falls outside the span —
 * through `outsideShaftSpanMessage` (`ui/util/ComponentWarnings.kt`), the ONE bounds
 * comparison this and the carousel cards' past-OAL chip share. The two messages differ in
 * wording only; do not fork the comparison back out into either surface.
 *
 * Each overlap names its partner by the title its carousel card shows ("Overlaps FWD Taper", a
 * user label when one is set — [overlapsMessage]), one line per partner, AFT → FWD; an index
 * ("Taper 1") left the user guessing which card that was (emulator QA). The partners are the
 * export gate's (`collisionPartnerIdsForSpan`), the same set the dialogs' inline line names.
 *
 * Returns an empty list when everything is clean.  Callers should present the warnings and offer
 * an "Add Anyway" path rather than blocking the add.
 */
fun collectAddWarnings(
    spec: ShaftSpec,
    startMm: Float,
    lengthMm: Float,
    unit: UnitSystem,
): List<String> {
    if (startMm < 0f || lengthMm <= 0f) return emptyList()

    val warnings = mutableListOf<String>()

    outsideShaftSpanMessage(spec, startMm, lengthMm, unit)?.let { warnings.add(it) }

    spec.collisionPartnerIdsForSpan(startMm, lengthMm).forEach { id ->
        overlapsMessage(spec, listOf(id))?.let { warnings.add(it) }
    }

    return warnings
}
