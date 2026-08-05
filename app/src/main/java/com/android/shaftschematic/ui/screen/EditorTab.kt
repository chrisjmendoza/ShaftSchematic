package com.android.shaftschematic.ui.screen

/**
 * EditorTab
 *
 * The document views available inside the shaft editor.
 * The sidebar nav rail switches between these without changing the NavController route —
 * tab state is local to the editor session and does not affect the back stack.
 *
 * Enabling rules:
 * - [SCHEMATIC] is always enabled (you can edit even an empty spec).
 * - [RUNOUT], [WEAR], [UNDERCUT], and [OUTPUT] are disabled until the shaft is "built"
 *   (has at least one component and a non-zero OAL), because a blank spec produces a
 *   meaningless document.
 * - [WEAR] can be hidden via [WEAR_TAB_ENABLED] when full consolidation retires it.
 */

/**
 * Consolidation posture (on-device request): the Wear page stays as the **authoring
 * surface** for wear data — spots, pits, and point Ø readings are placed/edited there —
 * while the Runout sheet is the consolidated **output**, featuring that wear information
 * on its profile (bands, X's, in-profile readings) alongside worn sections and bubbles.
 * This flag exists so a future full consolidation can retire the tab in one line without
 * touching the wear code paths.
 */
const val WEAR_TAB_ENABLED = true

enum class EditorTab(
    /** Short label shown in the expanded sidebar. */
    val label: String,
    /** Material icon name description for accessibility. */
    val contentDescription: String,
) {
    SCHEMATIC(
        label = "Schematic",
        contentDescription = "Shaft schematic editor",
    ),
    RUNOUT(
        label = "Runout Sheet",
        contentDescription = "Runout measurement sheet",
    ),
    WEAR(
        label = "Wear Document",
        contentDescription = "Shaft wear and inspection document",
    ),
    UNDERCUT(
        label = "Undercut Drawing",
        contentDescription = "Shaft undercut sections drawing",
    ),
    OUTPUT(
        label = "Consolidated Output",
        contentDescription = "Consolidated sheet preview, variants, and batch export",
    ),
}
