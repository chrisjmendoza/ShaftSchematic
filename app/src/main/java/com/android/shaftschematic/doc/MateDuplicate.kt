package com.android.shaftschematic.doc

import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord

/**
 * MateDuplicate — "Duplicate for mate".
 *
 * A twin-screw job is two near-identical shafts, so the mate's drawing starts as a copy of the
 * one already drawn: same geometry, same authoring decisions, new identity, and **no
 * measurements**.
 */

/**
 * Returns [source] rebuilt as its mate's document.
 *
 * **Copied verbatim** — everything that describes the shaft rather than the inspection: the
 * spec, the unit preference and lock, per-component unit overrides, the dual-unit flag, the
 * per-job [com.android.shaftschematic.settings.RunoutConfig] sheet tuning, the item
 * designation, and the notes. Geometry values are never recomputed here; a duplicated shaft
 * is the same shaft (golden rule).
 *
 * **Reset to empty** — every measurement record: the wear record (spots, pits, Ø readings,
 * worn sections), the runout readings, the dragged station placements that go with them, and
 * the undercut record. These are measurements of ONE physical shaft. They key by resolved
 * component id, so copying them would attach silently to the mate's matching components and
 * present fabricated inspection data as measured — the one failure this transform exists to
 * prevent. The mate is measured on its own.
 *
 * Identity ([jobNumber], [customer], [vessel], [position]) comes from the caller, which is
 * what lets the duplicate dialog change the side and job before the copy exists.
 */
fun mateDuplicate(
    source: ShaftDocCodec.ShaftDocV1,
    jobNumber: String,
    customer: String,
    vessel: String,
    position: ShaftPosition,
): ShaftDocCodec.ShaftDocV1 = source.copy(
    jobNumber = jobNumber,
    customer = customer,
    vessel = vessel,
    shaftPosition = position,
    wearRecord = WearRecord(),
    runoutReadings = RunoutReadings(),
    runoutStationPlacements = RunoutStationPlacements(),
    undercutRecord = UndercutRecord(),
)

/**
 * The side the mate of a shaft at [source] sits on: PORT ↔ STBD.
 *
 * CENTER and OTHER are returned unchanged — a centre shaft has no opposite side, and OTHER
 * says the side was never stated, which a duplicate may not decide on the user's behalf. This
 * is the duplicate dialog's DEFAULT only; the dropdown can always override it.
 */
fun matePosition(source: ShaftPosition): ShaftPosition = when (source) {
    ShaftPosition.PORT -> ShaftPosition.STBD
    ShaftPosition.STBD -> ShaftPosition.PORT
    ShaftPosition.CENTER, ShaftPosition.OTHER -> source
}
