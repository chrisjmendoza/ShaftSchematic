package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord

/**
 * File: EditState.kt
 * Layer: ViewModel
 *
 * The full slice of *drawing-editor* state covered by session undo/redo — everything a
 * single user edit can change and that [SessionHistory] must faithfully restore:
 * - [spec]: the canonical-mm [ShaftSpec] (geometry, keyways, coupler bolt slots, OAL, …).
 * - [wearRecord]: reference-only wear spots + pits (rides the same envelope as the spec).
 * - [runoutReadings]: reference-only per-station TIR values + high-spot markers.
 * - [runoutStationPlacements]: reference-only dragged station pins. Undoable because a drag
 *   is direct manipulation — a bubble nudged to the wrong spot should come back with one
 *   undo, not by dragging it home.
 * - [stationCountOverrides]: the per-component station counts, mirroring
 *   `RunoutConfig.componentOverrides`. The ONE slice of `RunoutConfig` that undo covers: a
 *   +/− on a pinned component rewrites placements and readings together with the count, and
 *   restoring the first two without the third left a phantom derived bubble behind every
 *   undo of a "+" (on-device-reachable desync). The REST of `RunoutConfig` (tuning sliders,
 *   TIR direction, coupling face) deliberately stays out; slider commits re-emit through the
 *   recorder but produce an identical [EditState], which [SessionHistory.record] no-ops — so
 *   the history still cannot flood.
 * - [undercutRecord]: reference-only recorded undercut sections.
 *
 * Carousel row order is NOT part of the snapshot: rows are derived from the spec (resolved
 * components in physical order), so restoring the spec restores the order with it.
 *
 * Metadata (customer / vessel / job number / notes / shaft position / unit) is deliberately
 * NOT part of this snapshot — those fields are not undoable. As a plain data class its
 * structural `equals` is what lets [SessionHistory.record] no-op identical states (and lets
 * undo/redo re-emission avoid re-recording).
 */
data class EditState(
    val spec: ShaftSpec,
    val wearRecord: WearRecord,
    val runoutReadings: RunoutReadings,
    val runoutStationPlacements: RunoutStationPlacements,
    val stationCountOverrides: Map<String, Int>,
    val undercutRecord: UndercutRecord,
)
