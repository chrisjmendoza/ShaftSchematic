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
/**
 * Override key for [componentId]'s keyway — a sub-feature with a unit of its own.
 *
 * Derived ids are suffixed with `#` in this codebase (a split body's runs are `<id>#2`), and this
 * follows that convention. A keyway-bearing body is never fragmented, so a keyway key is always
 * built from a base id and the two suffix spaces cannot collide.
 *
 * Storing it in the same `unit_overrides` map is what keeps this additive: no new envelope field,
 * no codec change, and a key whose component is gone is inert (never pruned — the render-layer
 * orphan posture every reference feature takes).
 */
fun keywayUnitKey(componentId: String): String = "$componentId#kw"

data class DisplayUnits(
    val documentUnit: UnitSystem,
    val overrides: Map<String, UnitSystem> = emptyMap(),
    val dual: Boolean = false,
) {
    /** The effective display unit for [componentId] — its override, else [documentUnit]. */
    fun unitFor(componentId: String?): UnitSystem =
        componentId?.let { overrides[it] } ?: documentUnit

    /**
     * The unit a component's KEYWAY is authored and printed in.
     *
     * Falls back through the whole chain — the keyway's own override, else the component's, else
     * the document unit — so a keyway with no choice of its own behaves exactly as it always did.
     *
     * A keyway is not a component, but it is the other feature (with threads) that arrives metric
     * on an otherwise imperial shaft: European stock comes in whole millimetres, and re-typing a
     * 20 x 12 mm keyway as 0.7874 x 0.4724 in is both tedious and a rounding hazard. So it gets its
     * own override under a derived key, and unlike the component chip it governs ENTRY as well as
     * print — the same posture as a metric thread's designation.
     */
    fun keywayUnitFor(componentId: String?): UnitSystem =
        componentId?.let { overrides[keywayUnitKey(it)] } ?: unitFor(componentId)

    companion object {
        /** A resolver with no overrides and no dual — reproduces single-unit behavior exactly. */
        fun single(unit: UnitSystem): DisplayUnits = DisplayUnits(unit)
    }
}
