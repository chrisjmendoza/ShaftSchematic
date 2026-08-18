package com.android.shaftschematic.util

/**
 * Resolves the display unit for a value, given the document's unit, any per-component
 * overrides, and the sheet-wide dual flag.
 *
 * This is a **display axis only** — canonical geometry stays in millimeters everywhere
 * (golden rule). It threads a document's unit decisions to the small set of formatting
 * sites that have a component in hand; sites without one (OAL rail, bare shaft) use
 * [documentUnit] directly.
 *
 * Pure: no Android, no `pdf` dependency. The dual *rendering* (`<primary> [<secondary>]`)
 * lives in `pdf/UnitFormat.kt` (`*Dual`) and on-screen edge helpers; this type only carries
 * the [dual] flag and answers "which unit for this component?".
 *
 * @property documentUnit The document's unit (the existing `preferredUnit`), used as the
 *   default for any component without an override and for un-keyed dimensions.
 * @property overrides Per-component display-unit overrides, keyed by **resolved component id**.
 *   An id that no longer resolves is harmless — [unitFor] just falls back to [documentUnit].
 * @property dual When true, dimensions print both units inline; see `pdf/UnitFormat.kt`.
 */
data class DisplayUnits(
    val documentUnit: UnitSystem,
    val overrides: Map<String, UnitSystem> = emptyMap(),
    val dual: Boolean = false,
) {
    /** The effective display unit for [componentId] — its override, else [documentUnit]. */
    fun unitFor(componentId: String?): UnitSystem =
        componentId?.let { overrides[it] } ?: documentUnit

    companion object {
        /** A resolver with no overrides and no dual — reproduces single-unit behavior exactly. */
        fun single(unit: UnitSystem): DisplayUnits = DisplayUnits(unit)
    }
}
