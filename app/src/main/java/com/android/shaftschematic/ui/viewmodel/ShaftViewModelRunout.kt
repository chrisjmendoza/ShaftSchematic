package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan
import com.android.shaftschematic.geom.authoredStationIndexToRemove
import com.android.shaftschematic.geom.currentLocalStationPositions
import com.android.shaftschematic.geom.insertStationPosition
import com.android.shaftschematic.geom.planStationInsertion
import com.android.shaftschematic.geom.removeStationPosition
import com.android.shaftschematic.geom.runoutComponentSpanMm
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.resolved.runoutComponentSpans
import kotlinx.coroutines.flow.update

/**
 * ShaftViewModelRunout — extension functions for the runout sheet's per-job configuration,
 * the per-station readings, and the dragged station placements.
 *
 * Extracted from ShaftViewModel to keep runout mutation grouped by concern. All functions
 * are extensions on ShaftViewModel and access internal-visibility backing fields declared
 * in the primary class file.
 */

// ── Runout sheet configuration ───────────────────────────────────────────────

/**
 * Override the number of runout bubbles for a specific component.
 * Pass `count = null` to remove the override and revert to the computed default.
 * `0` is a valid override — the component is not being measured, so it draws no
 * bubbles (on-device request). Readings keyed to a zeroed component are kept and
 * simply not drawn (the render-layer orphan rule), so raising the count restores
 * them.
 */
fun ShaftViewModel.setRunoutBubbleCount(componentId: String, count: Int?) {
    _runoutConfig.update { cfg ->
        val overrides = cfg.componentOverrides.toMutableMap()
        if (count == null) {
            overrides.remove(componentId)
        } else {
            overrides[componentId] = count.coerceAtLeast(0)
        }
        cfg.copy(componentOverrides = overrides)
    }
}

/** Set the TIR direction label printed at the bottom of the runout sheet. */
fun ShaftViewModel.setTirDirection(direction: TirDirection) {
    _runoutConfig.update { it.copy(tirDirection = direction) }
}

/**
 * "Shaft height" slider — exaggerate or shrink the drawn shaft on every drawing
 * output: schematic, runout, and consolidated sheets (one per-job value). Clamped to
 * the geom slider bounds; the composer additionally hard-caps the drawn height at PROFILE_MAX_SHAFT_HEIGHT_PT
 * and the page budget.
 */
fun ShaftViewModel.setRunoutHeightScale(scale: Float) {
    _runoutConfig.update {
        it.copy(
            heightScale = scale.coerceIn(
                com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN,
                com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX,
            )
        )
    }
}

/** "Keep liners proportional lengthwise" — see [RunoutConfig.linersProportional]. */
fun ShaftViewModel.setLinersProportional(proportional: Boolean) {
    _runoutConfig.update { it.copy(linersProportional = proportional) }
}

/** "Liner compression" slider — see [RunoutConfig.linerCompression]. */
fun ShaftViewModel.setLinerCompression(fraction: Float) {
    _runoutConfig.update { it.copy(linerCompression = fraction.coerceIn(0f, 1f)) }
}

/**
 * "Coupling face" — elect the coupling end view onto the runout/consolidated sheets.
 * Per-job (rides the envelope's `RunoutConfig`), off by default: not every inspection
 * measures the coupling. See [RunoutConfig.showCouplingFace].
 */
fun ShaftViewModel.setShowCouplingFace(show: Boolean) {
    _runoutConfig.update { it.copy(showCouplingFace = show) }
}

// ── Runout per-station readings (bubble value + high-spot marker) ─────────────

/**
 * Upsert the runout reading for a bubble identified by [componentId] + [stationIndex].
 * [valueMm] is canonical mm (UI converts from the active unit before calling);
 * [highSpotHalfHours] is a clock tick in `[0, 23]` (0 = 12 o'clock). Passing both as null
 * clears the reading (the empty entry is not stored).
 */
fun ShaftViewModel.setRunoutReading(
    componentId: String,
    stationIndex: Int,
    valueMm: Float?,
    highSpotHalfHours: Int?,
) {
    _runoutReadings.update { readings ->
        readings.withReading(
            RunoutReading(
                componentId = componentId,
                stationIndex = stationIndex,
                valueMm = valueMm,
                highSpotHalfHours = highSpotHalfHours?.let { ((it % 24) + 24) % 24 },
            )
        )
    }
}

// ── Runout station placement (dragged bubble positions) ──────────────────────

/**
 * Commit a bubble drag: pin ONE station at [localMm] (component-local, from the AFT edge),
 * already clamped by the caller (the drag needs the clamped value for its own live
 * feedback, so re-deriving it here could only disagree).
 *
 * Called once on finger-up, never per drag frame — a per-frame write would flip the
 * unsaved-changes asterisk immediately and push one undo step per coalescing window of
 * continuous dragging.
 */
fun ShaftViewModel.setRunoutStationPosition(componentId: String, stationIndex: Int, localMm: Float) {
    _runoutStationPlacements.update { it.withPosition(componentId, stationIndex, localMm) }
}

/** Un-pin one station ("Undo move" on a first drag) — it derives its position again. */
fun ShaftViewModel.clearRunoutStationPosition(componentId: String, stationIndex: Int) {
    _runoutStationPlacements.update { it.withoutPosition(componentId, stationIndex) }
}

/** Return a component's stations to derived placement, discarding every dragged position. */
fun ShaftViewModel.resetRunoutStationPositions(componentId: String) {
    _runoutStationPlacements.update { it.withoutComponent(componentId) }
}

/**
 * Return EVERY component to derived placement — the "Reset all bubble positions" button
 * under the measurement-station rows. Recoverable: placements are in [EditState], so a
 * session undo brings the dragged positions back.
 */
fun ShaftViewModel.resetAllRunoutStationPositions() {
    _runoutStationPlacements.value = RunoutStationPlacements()
}

/** A component's drawn runs, for the station +/− math. Empty when it no longer resolves. */
private fun ShaftViewModel.runoutRunsFor(componentId: String): List<RunoutComponentSpan> =
    runoutComponentSpans(_resolvedComponents.value).filter { it.id == componentId }

/**
 * Add one measurement station to a component.
 *
 * A fully derived component simply gains a station and re-derives every position, as
 * before. A component with any pinned station instead has its **complete current set**
 * (pins verbatim, siblings at their derived spots — [currentLocalStationPositions])
 * frozen with the new station inserted into the widest gap ([planStationInsertion]) —
 * which for the ordinary two-station component means between the existing pair. The
 * freeze is what keeps every bubble planted while the insert renumbers its neighbours;
 * the readings shift with their stations ([RunoutReadings.withStationInserted]), so every
 * typed TIR stays on the bubble it was measured at.
 */
fun ShaftViewModel.addRunoutStation(componentId: String, currentCount: Int) {
    val placements = _runoutStationPlacements.value
    if (placements.isAuthored(componentId)) {
        val runs = runoutRunsFor(componentId)
        if (runs.isNotEmpty()) {
            val full = currentLocalStationPositions(
                runs, currentCount, placements.positionsFor(componentId),
            )
            val useEdgeInset = runs.first().kind != RunoutComponentKind.BODY
            val insertion =
                planStationInsertion(full, runoutComponentSpanMm(runs), useEdgeInset)
            _runoutStationPlacements.value = placements.withComponent(
                componentId, insertStationPosition(full, insertion),
            )
            _runoutReadings.update { it.withStationInserted(componentId, insertion.index) }
        }
    }
    setRunoutBubbleCount(componentId, currentCount + 1)
}

/**
 * Remove one measurement station from a component.
 *
 * A fully derived component simply loses a station and re-derives every position, as
 * before — its FWD-most reading is left in place as an orphan (never drawn, restored if
 * the count goes back up), matching what a count of 0 already does.
 *
 * A component with any pinned station drops its most redundant unmeasured station from
 * the complete current set ([authoredStationIndexToRemove]) and re-keys the readings
 * above it, so "−" undoes a "+" instead of deleting a bubble the user had dragged into
 * place; the remaining set freezes, keeping every surviving bubble planted through the
 * renumber.
 */
fun ShaftViewModel.removeRunoutStation(componentId: String, currentCount: Int) {
    val placements = _runoutStationPlacements.value
    if (placements.isAuthored(componentId)) {
        val runs = runoutRunsFor(componentId)
        if (runs.isNotEmpty()) {
            val full = currentLocalStationPositions(
                runs, currentCount, placements.positionsFor(componentId),
            )
            val useEdgeInset = runs.first().kind != RunoutComponentKind.BODY
            val readings = _runoutReadings.value
            val index = authoredStationIndexToRemove(
                full, runoutComponentSpanMm(runs), useEdgeInset,
            ) { i -> readings.find(componentId, i) != null }
            if (index >= 0) {
                _runoutStationPlacements.value = placements.withComponent(
                    componentId, removeStationPosition(full, index),
                )
                _runoutReadings.value = readings.withStationRemoved(componentId, index)
            }
        }
    }
    setRunoutBubbleCount(componentId, (currentCount - 1).coerceAtLeast(0))
}
