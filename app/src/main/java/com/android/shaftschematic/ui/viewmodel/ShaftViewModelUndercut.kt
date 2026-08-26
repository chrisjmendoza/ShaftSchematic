package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.geom.UNDERCUT_EXAGGERATION_MAX_FRAC
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import kotlinx.coroutines.flow.update

/**
 * ShaftViewModelUndercut — extension functions for every undercut-record mutator: the cut
 * spans themselves, their authored "Measure from" reference, and the sheet's drawn-depth
 * exaggeration.
 *
 * Extracted from ShaftViewModel to keep the reference-only [UndercutRecord]'s editing
 * grouped by concern. All functions are extensions on ShaftViewModel and access
 * internal-visibility backing fields declared in the primary class file.
 */

/**
 * Record a new undercut section at [startFromAftMm] (canonical shaft space) with
 * [lengthMm] and Ø unentered (0). Returns the new undercut's id so the caller can
 * immediately open its detail overlay.
 *
 * [reference]/[referenceLinerId] are the authoring reference the distance was entered
 * against — display metadata only, stored verbatim. They default to the SET-based
 * posture ([UndercutReference.AFT_SET], no liner) used by the tab's global "Add
 * undercut" button; adding from inside a liner's detail strip passes that liner's
 * `LINER_*` reference instead.
 */
fun ShaftViewModel.addUndercut(
    startFromAftMm: Float,
    lengthMm: Float,
    reference: UndercutReference = UndercutReference.AFT_SET,
    referenceLinerId: String = "",
): String {
    val undercut = Undercut(
        startFromAftMm = startFromAftMm,
        lengthMm = lengthMm,
        diaMm = 0f,
        authoredReference = reference,
        referenceLinerId = referenceLinerId,
    )
    _undercutRecord.update { rec -> rec.copy(undercuts = rec.undercuts + undercut) }
    return undercut.id
}

/**
 * Replace an existing undercut's fields by [id]. Fields are stored **verbatim** — golden
 * rule: no snap/round/derive ever rewrites a typed value, including [diaMm] (0 = placed,
 * not yet measured). No-op if the id is not found.
 */
fun ShaftViewModel.updateUndercut(id: String, startFromAftMm: Float, lengthMm: Float, diaMm: Float, note: String) {
    _undercutRecord.update { rec ->
        rec.copy(
            undercuts = rec.undercuts.map { u ->
                if (u.id != id) u else u.copy(
                    startFromAftMm = startFromAftMm,
                    lengthMm = lengthMm,
                    diaMm = diaMm,
                    note = note,
                )
            }
        )
    }
}

/**
 * Update an undercut's authored "Measure from" reference by [id]. Display-only — same
 * pattern as [updateWearSpotReference]: it never touches [Undercut.startFromAftMm],
 * only which reference point the "Distance" field re-projects against.
 *
 * [referenceLinerId] is the liner the distance converts against for the `LINER_*`
 * references; both values are stored **verbatim**. Callers pass an empty id for the
 * SET references, so switching back to a S.E.T. also drops the stale liner key.
 */
fun ShaftViewModel.updateUndercutReference(
    id: String,
    reference: UndercutReference,
    referenceLinerId: String = "",
) {
    _undercutRecord.update { rec ->
        rec.copy(
            undercuts = rec.undercuts.map { u ->
                if (u.id != id ||
                    (u.authoredReference == reference && u.referenceLinerId == referenceLinerId)
                ) u
                else u.copy(authoredReference = reference, referenceLinerId = referenceLinerId)
            }
        )
    }
}

/** Remove an undercut by [id]. Confirm-free, as authored in its edit card. */
fun ShaftViewModel.removeUndercut(id: String) {
    _undercutRecord.update { rec -> rec.copy(undercuts = rec.undercuts.filterNot { it.id == id }) }
}

/**
 * Set this sheet's drawn-depth exaggeration ([UndercutRecord.exaggerationFrac]), clamped
 * to `0..`[UNDERCUT_EXAGGERATION_MAX_FRAC]. Display-only styling for the undercut
 * drawing: the sheet's deepest cut draws at this fraction of its local surface Ø and
 * shallower cuts scale relative to it (`normalizedNotchFloorDiaMm`). It never touches a
 * stored or printed Ø, so the golden rule is untouched — but it is per-document, so it
 * lives in the record rather than in app prefs.
 */
fun ShaftViewModel.setUndercutExaggeration(frac: Float) {
    val clamped = frac.coerceIn(0f, UNDERCUT_EXAGGERATION_MAX_FRAC)
    _undercutRecord.update { rec ->
        if (rec.exaggerationFrac == clamped) rec else rec.copy(exaggerationFrac = clamped)
    }
}
