package com.android.shaftschematic.settings

import kotlinx.serialization.Serializable

/**
 * RunoutConfig
 *
 * Stores per-job runout-sheet preferences. Saved alongside the shaft spec in the .shaft file.
 *
 * ## Bubble count logic
 * Each component type has a default bubble count computed at PDF render time:
 *   - Taper  → 2  (one near the SET end, one near the LET end — both inset from the physical edge)
 *   - Liner  → 2  (one near each edge, inset from the physical boundary)
 *   - Body   → 3  (evenly distributed across the body length)
 *   - Thread → 0  (threads are not measured for runout)
 *
 * Users may override these per component via [componentOverrides].
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
 * @param linersProportional "Keep liners proportional lengthwise" — liners demand their
 *   full true-scale drawn width (the key measured components stay proportional); the
 *   drawn HEIGHT yields when the page can't fit them, same as keyway-pinned bodies.
 *   Overrides [linerCompression] while checked.
 * @param linerCompression "Liner compression" slider — how far liners may foreshorten
 *   below true scale when the page needs the room. 1.0 = fully (down to the
 *   `PROFILE_MIN_LINER_PT` writable floor — the historical behavior), 0.0 = not at all
 *   (equivalent to [linersProportional]). Applied as a per-liner width floor of
 *   (1 − value) × true width; the geometry consumes [linerMinFracOfTrue]. Per-job, on
 *   the runout/consolidated sheets AND the schematic, like [heightScale].
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
     * 1 = pinned at true scale.
     */
    val linerMinFracOfTrue: Float
        get() = if (linersProportional) 1f else (1f - linerCompression).coerceIn(0f, 1f)

    companion object {
        /**
         * Distance in mm from a component's physical edge to the first/last measurement station.
         * 1 inch (25.4 mm) — readings taken right on a taper or liner edge are unreliable.
         */
        const val RUNOUT_EDGE_INSET_MM = 25.4f

        /** Default bubble count for body sections shorter than this threshold. */
        const val BODY_SHORT_THRESHOLD_MM = 914f  // ~36 inches

        /** Default number of bubbles for a body whose length is below [BODY_SHORT_THRESHOLD_MM]. */
        const val BODY_DEFAULT_COUNT = 3

        /** Default for long bodies — user bumps this up in the app as needed. */
        const val BODY_LONG_COUNT = 3  // keep at 3; user promotes as needed

        /** Default bubble count for tapers. */
        const val TAPER_DEFAULT_COUNT = 2

        /** Default bubble count for liners. */
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
