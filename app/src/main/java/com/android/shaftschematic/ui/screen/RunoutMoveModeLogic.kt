package com.android.shaftschematic.ui.screen

/**
 * RunoutMoveModeLogic — the pure decisions behind the Runout tab's Move mode.
 *
 * Move mode is the explicit alternative to press-and-hold (on-device request: the long press
 * was not discoverable and slowed down placing several bubbles): a tap selects a bubble or a
 * component, a bubble drags the instant the finger lands on it, and −/+ act on the selected
 * component. Everything here is plain Kotlin — no Compose, no Android — so the selection rules
 * are unit-tested directly; `RunoutRoute` owns the gestures and the drawing.
 *
 * Selection is screen state only. Nothing in this file writes the model: a drag still commits
 * through the same commit-on-release path as a long-press drag, and −/+ call the station
 * editor's own ViewModel actions.
 */

import com.android.shaftschematic.geom.PlacedRunoutBubble
import com.android.shaftschematic.geom.RunoutComponentSpan
import com.android.shaftschematic.geom.pickBubbleAt
import kotlin.math.hypot

/**
 * What Move mode has selected: a component, and optionally one of its stations.
 *
 * @property componentId Station key — the BASE body id for bodies, as `runoutComponentSpans`
 *   and the station editor key them.
 * @property stationIndex The selected bubble's 0-based station index, or null when the
 *   component itself is selected (a tap on its drawn run, or after a count change renumbered
 *   its stations).
 */
data class RunoutMoveSelection(
    val componentId: String,
    val stationIndex: Int?,
)

/**
 * The selection a Move-mode tap produces. A bubble beats the component under it (the bubble
 * is the more specific target), a component run selects the whole component, and a tap on
 * bare paper clears the selection.
 */
fun moveModeSelectionForTap(
    hitBubble: PlacedRunoutBubble?,
    hitComponentId: String?,
): RunoutMoveSelection? = when {
    hitBubble != null -> RunoutMoveSelection(hitBubble.componentId, hitBubble.stationIndex)
    hitComponentId != null -> RunoutMoveSelection(hitComponentId, null)
    else -> null
}

/**
 * The bubble a Move-mode press picks up. The currently selected station wins whenever the
 * press is within reach of it — bubbles sit close together on a busy shaft, and a press that
 * lands between the selected bubble and its neighbour means the one already chosen — and
 * otherwise the nearest bubble in reach wins, exactly as [pickBubbleAt] decides.
 */
fun pickBubbleForMove(
    bubbles: List<PlacedRunoutBubble>,
    radius: Float,
    px: Float,
    py: Float,
    tolerance: Float,
    selected: RunoutMoveSelection?,
): PlacedRunoutBubble? {
    val selectedIndex = selected?.stationIndex
    if (selectedIndex != null) {
        val chosen = bubbles.firstOrNull {
            it.componentId == selected.componentId && it.stationIndex == selectedIndex
        }
        if (chosen != null && hypot(px - chosen.bubbleX, py - chosen.bubbleCenterY) <= radius + tolerance) {
            return chosen
        }
    }
    return pickBubbleAt(bubbles, radius, px, py, tolerance)
}

/**
 * The selection after a −/+ on the selected component. The component stays selected so the
 * next −/+ needs no re-tap, but the station drops: an insert or a removal renumbers the
 * component's stations, so the old index would point at a different bubble.
 */
fun selectionAfterCountChange(sel: RunoutMoveSelection?): RunoutMoveSelection? =
    sel?.copy(stationIndex = null)

/**
 * The selection as it applies to the current drawing. A component that no longer resolves
 * (absent from [stationCounts]) reads as nothing selected; a station index past the
 * component's current count (an undo or a count change elsewhere) falls back to the component.
 * Never written back — the stored selection survives, so a geometry undo that brings the
 * component back brings its selection back with it.
 *
 * @param stationCounts Current station count per live component, keyed like the selection.
 */
fun liveMoveSelection(
    sel: RunoutMoveSelection?,
    stationCounts: Map<String, Int>,
): RunoutMoveSelection? {
    if (sel == null) return null
    val count = stationCounts[sel.componentId] ?: return null
    val index = sel.stationIndex
    return if (index != null && index !in 0 until count) sel.copy(stationIndex = null) else sel
}

/**
 * The component whose drawn run contains the axial position [mm] (shaft space), or null on
 * bare shaft past either end. Runs abut but never overlap — the resolve layer trims a body
 * out from under its liners — so the first match is the only one.
 */
fun runoutSpanIdAt(spans: List<RunoutComponentSpan>, mm: Float): String? =
    spans.firstOrNull { mm >= it.startMm && mm <= it.startMm + it.lengthMm }?.id

/**
 * Display label for a selection, e.g. "Body 2 · Station 3", "Body 2" for a component-only
 * selection, or "Nothing selected". The same label the bubble's reading dialog is titled
 * with, built from the same station-editor rows.
 */
internal fun moveSelectionLabel(
    sel: RunoutMoveSelection?,
    entries: List<RunoutComponentEntry>,
): String {
    if (sel == null) return "Nothing selected"
    val label = entries.firstOrNull { it.id == sel.componentId }?.label ?: "Component"
    return sel.stationIndex?.let { "$label · Station ${it + 1}" } ?: label
}
