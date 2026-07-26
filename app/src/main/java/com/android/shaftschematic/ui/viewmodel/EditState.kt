package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.ui.order.ComponentKey

/**
 * File: EditState.kt
 * Layer: ViewModel
 *
 * The full slice of *drawing-editor* state covered by session undo/redo — everything a
 * single user edit can change and that [SessionHistory] must faithfully restore:
 * - [spec]: the canonical-mm [ShaftSpec] (geometry, keyways, coupler bolt slots, OAL, …).
 * - [wearRecord]: reference-only wear spots + pits (rides the same envelope as the spec).
 * - [runoutReadings]: reference-only per-station TIR values + high-spot markers.
 * - [componentOrder]: the cross-type UI order (so deleting/reordering is undoable, not just
 *   the spec change — a delete restore must bring back the row in its original position).
 * - [overallIsManual]: the OAL manual/auto mode.
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
    val componentOrder: List<ComponentKey>,
    val overallIsManual: Boolean,
)
