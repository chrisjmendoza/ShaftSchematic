package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec

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
 * Returns an empty list when everything is clean.  Callers should present the warnings and offer
 * an "Add Anyway" path rather than blocking the add.
 */
fun collectAddWarnings(
    spec: ShaftSpec,
    startMm: Float,
    lengthMm: Float,
): List<String> {
    if (startMm < 0f || lengthMm <= 0f) return emptyList()

    val warnings = mutableListOf<String>()
    val proposedEnd = startMm + lengthMm
    val eps = 1e-3f

    fun overlaps(bStart: Float, bLen: Float): Boolean {
        val bEnd = bStart + bLen
        return startMm < bEnd - eps && proposedEnd > bStart + eps
    }

    outsideShaftSpanMessage(spec, startMm, lengthMm)?.let { warnings.add(it) }

    spec.tapers.forEachIndexed { i, t ->
        if (overlaps(t.startFromAftMm, t.lengthMm))
            warnings.add("Overlaps Taper ${i + 1}")
    }

    spec.threads.filter { !it.excludeFromOAL }.forEachIndexed { i, t ->
        if (overlaps(t.startFromAftMm, t.lengthMm))
            warnings.add("Overlaps Thread ${i + 1}")
    }

    spec.liners.forEachIndexed { i, l ->
        if (overlaps(l.startFromAftMm, l.lengthMm))
            warnings.add("Overlaps Liner ${i + 1}")
    }

    return warnings
}
