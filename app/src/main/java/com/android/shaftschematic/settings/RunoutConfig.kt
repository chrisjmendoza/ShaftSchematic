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
 */
@Serializable
data class RunoutConfig(
    val componentOverrides: Map<String, Int> = emptyMap(),
    val tirDirection: TirDirection = TirDirection.UNSET,
) {
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
