package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec

/**
 * Returns human-readable warning strings for a proposed component at [startMm] / [lengthMm].
 *
 * Checks existing tapers, non-excluded threads, and liners.
 * Bodies are intentionally skipped — they auto-split to accommodate any new component.
 * When [overallIsManual] is true, also warns if the component falls outside the shaft span.
 *
 * Returns an empty list when everything is clean.  Callers should present the warnings and offer
 * an "Add Anyway" path rather than blocking the add.
 */
fun collectAddWarnings(
    spec: ShaftSpec,
    startMm: Float,
    lengthMm: Float,
    overallIsManual: Boolean,
): List<String> {
    if (startMm < 0f || lengthMm <= 0f) return emptyList()

    val warnings = mutableListOf<String>()
    val proposedEnd = startMm + lengthMm
    val eps = 1e-3f

    fun overlaps(bStart: Float, bLen: Float): Boolean {
        val bEnd = bStart + bLen
        return startMm < bEnd - eps && proposedEnd > bStart + eps
    }

    if (overallIsManual && spec.overallLengthMm > 0f) {
        if (startMm < -eps || proposedEnd > spec.overallLengthMm + eps) {
            val oalStr = "%.3f".format(spec.overallLengthMm).trimEnd('0').trimEnd('.')
            warnings.add("Falls outside shaft span (OAL $oalStr mm)")
        }
    }

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
