package com.android.shaftschematic.settings

import kotlinx.serialization.Serializable

/**
 * RunoutConfig
 *
 * Stores per-job runout-sheet preferences. Saved alongside the shaft spec in the .shaft file.
 *
 * ## Bubble count logic
 * Station counts are **length-driven**: one station per
 * [Companion.RUNOUT_STATION_INTERVAL_MM] (20 inches) of drawn component, resolved by
 * [Companion.defaultStationCount] —
 *   - Taper  → 2, always (one near the SET end, one near the LET end — both inset from the
 *     physical edge; the shop convention, not a density choice)
 *   - Liner  → one per interval, never fewer than 2 (one per edge)
 *   - Body   → one per interval, never fewer than 1
 *   - Thread → 0  (threads are not measured for runout)
 *
 * A body split by liners or tapers is ONE component here: its runs' lengths add up, the count
 * is derived once, and the stations are apportioned across the runs. Users may override the
 * derived count per component via [componentOverrides].
 *
 * ## Edge inset convention
 * For tapers and liners the measurement stations are NOT placed directly on the component
 * edges — that is where the geometry changes and indicator readings are unreliable.
 * Instead they are inset by [RUNOUT_EDGE_INSET_MM] from each edge. Defaults to 1 inch (25.4 mm).
 *
 * @param componentOverrides Map of component ID → user-chosen bubble count. Any component
 *   not in this map uses its default count. Minimum count is 1 for components that
 *   normally get bubbles; 0 hides that component's stations entirely.
 * @param tirDirection Which direction the indicator was run when taking readings.
 *   Printed as "TIR's taken looking: ___" at the bottom of the runout sheet.
 * @param heightScale "Shaft height" slider — a multiplier on the solved profile scale so
 *   the drawn shaft can be exaggerated (grown) or shrunk as a whole, on the runout/
 *   consolidated sheets AND the schematic (one per-job value for every drawing output).
 *   1.0 = the standard convention. Clamped to the geom slider bounds
 *   (`PROFILE_HEIGHT_SCALE_MIN`..`MAX`); the drawn height is hard-capped at
 *   `PROFILE_MAX_SHAFT_HEIGHT_PT` (1.5" on paper — an ABSOLUTE ceiling: a short shaft
 *   that would draw taller keeps proportion and simply doesn't span the page) and by the
 *   page budget (`exaggeratedProfileScale`). Per-job (rides the .shaft envelope) so a
 *   reopened document reprints identically — same posture as the undercut sheet's
 *   exaggeration slider.
 * @param linersProportional "Keep liners proportional lengthwise" — liners hold their
 *   true-scale drawn width up to what the page affords AT the selected drawn height.
 *   The height takes PRECEDENCE (on-device direction): this never lowers the drawn
 *   shaft; when the full request doesn't fit, the liner floors shrink uniformly instead
 *   (`fracFitFactor`). Only keyway-pinned bodies may yield the height. Overrides
 *   [linerCompression] while checked.
 * @param linerCompression "Liner compression" slider — how far liners may foreshorten
 *   below true scale when the page needs the room. 1.0 = fully (down to the
 *   `PROFILE_MIN_LINER_PT` writable floor — the default), 0.0 = not at all
 *   (equivalent to [linersProportional]). Applied as a best-effort per-liner width
 *   floor of (1 − value) × true width; the geometry consumes [linerMinFracOfTrue] and
 *   never trades drawn height for it. Per-job, on the runout/consolidated sheets AND
 *   the schematic, like [heightScale].
 */
@Serializable
data class RunoutConfig(
    val componentOverrides: Map<String, Int> = emptyMap(),
    val tirDirection: TirDirection = TirDirection.UNSET,
    val heightScale: Float = 1.0f,
    val linersProportional: Boolean = false,
    val linerCompression: Float = 1.0f,
) {
    /**
     * The liner width floor as a fraction of true drawn width — what the composers hand
     * to `ProfileFeatureSpan.minWidthFracOfTrue`. 0 = compress freely (floor only),
     * 1 = request full true width (best-effort; λ-fitted, never lowers the drawn
     * height).
     */
    val linerMinFracOfTrue: Float
        get() = if (linersProportional) 1f else (1f - linerCompression).coerceIn(0f, 1f)

    companion object {
        /**
         * Distance in mm from a component's physical edge to the first/last measurement station.
         * 1 inch (25.4 mm) — readings taken right on a taper or liner edge are unreliable.
         */
        const val RUNOUT_EDGE_INSET_MM = 25.4f

        /**
         * Axial distance covered by one measurement station: **one bubble per 20 inches**
         * (on-device request). Replaces the former flat "3 stations per body, whatever its
         * length", which put three readings on a 1–2" leftover run and only three on a
         * 100" line shaft.
         */
        const val RUNOUT_STATION_INTERVAL_MM = 508f

        /**
         * Ceiling on a single component's derived station count. A very long line shaft
         * would otherwise ask for more bubbles than a letter page can seat (the layout
         * engine starts compressing around 27 stations across the whole sheet). Raising a
         * capped component past this is still possible by hand — the cap only bounds what
         * is derived automatically.
         */
        const val MAX_STATIONS_PER_COMPONENT = 10

        /**
         * The derived count itself lives in `geom/RunoutBubbleLayout.kt`
         * (`defaultStationCount`) — beside the [com.android.shaftschematic.geom.RunoutComponentKind]
         * it switches on, and out of this file so `settings` need not depend on `geom`.
         */

        /**
         * Pre-interval default counts, kept ONLY for the decode-time freeze that protects
         * documents authored before the interval existed — see
         * `ShaftDocCodec.freezeLegacyStationCounts`. New defaults come from
         * [defaultStationCount]; do not read these anywhere else.
         */
        const val LEGACY_BODY_DEFAULT_COUNT = 3
        const val LEGACY_TAPER_DEFAULT_COUNT = 2
        const val LEGACY_LINER_DEFAULT_COUNT = 2

        /** Default bubble count for tapers — the shop's two-station convention. */
        const val TAPER_DEFAULT_COUNT = 2

        /** Floor on a liner's station count (one per edge). */
        const val LINER_DEFAULT_COUNT = 2
    }
}

/**
 * The direction the dial indicator was run when taking TIR readings.
 * Printed on the runout sheet so the shop knows how to interpret the high-spot arrows.
 */
enum class TirDirection {
    UNSET,    // Not specified yet — prints a blank fill-in line
    AFT,      // "TIR's taken looking AFT"
    FORWARD,  // "TIR's taken looking FORWARD"
}
