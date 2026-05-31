package com.android.shaftschematic.ui.screen

/**
 * EditorTab
 *
 * The three document views available inside the shaft editor.
 * The sidebar nav rail switches between these without changing the NavController route —
 * tab state is local to the editor session and does not affect the back stack.
 *
 * Enabling rules:
 * - [SCHEMATIC] is always enabled (you can edit even an empty spec).
 * - [RUNOUT] and [WEAR] are disabled until the shaft is "built" (has at least one
 *   component and a non-zero OAL), because a blank spec produces a meaningless document.
 */
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
}
