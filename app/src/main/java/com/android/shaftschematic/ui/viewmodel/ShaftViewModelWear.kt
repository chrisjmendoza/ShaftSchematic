package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.geom.WEAR_TRACE_MIN_DEPTH_FRAC
import com.android.shaftschematic.geom.clampPitAcrossFrac
import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.pdf.WEAR_STRIP_SIZE_FRAC_MAX
import com.android.shaftschematic.pdf.WEAR_STRIP_SIZE_FRAC_MIN
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.min

/**
 * ShaftViewModelWear — extension functions for every wear-record mutator: wear spots, wear
 * pits, measured-Ø readings, worn sections, and the record's own display settings.
 *
 * Extracted from ShaftViewModel to keep the reference-only [WearRecord]'s editing grouped by
 * concern. All functions are extensions on ShaftViewModel and access internal-visibility
 * backing fields declared in the primary class file.
 */

// ── Wear spots (liner wear areas) ────────────────────────────────────────────

/**
 * Add a new wear spot on [linerId] with sensible defaults (start 0, no reading). The
 * default length is 1in (25.4mm), clamped to the liner's own length for tiny liners so
 * the default band is never rejected by [wearSpotSpanIssue] at first render/edit.
 */
fun ShaftViewModel.addWearSpot(linerId: String) {
    val linerLengthMm = _spec.value.liners.firstOrNull { it.id == linerId }?.lengthMm ?: 25.4f
    val defaultLengthMm = min(25.4f, linerLengthMm.coerceAtLeast(0f))
    _wearRecord.update { rec ->
        rec.copy(
            spots = rec.spots + WearSpot(
                linerId = linerId,
                startMm = 0f,
                lengthMm = defaultLengthMm,
                minDiaMm = 0f,
                note = "",
            )
        )
    }
}

/**
 * Update an existing wear spot's fields by [id]. No-op if the id is not found.
 *
 * [startMm]/[lengthMm] are always canonical (liner-local AFT-edge mm) — reference
 * conversion happens in the UI (`LinerWearMath.kt`'s `wearStartToCanonicalMm`) before
 * this is called, and blocking in-span validation (`wearSpotSpanIssue`) happens at the
 * `NumericInputField` layer, so a rejected entry never reaches here. See
 * [updateWearSpotReference] for the separate, geometry-free "Measure from" setter.
 */
fun ShaftViewModel.updateWearSpot(id: String, startMm: Float, lengthMm: Float, minDiaMm: Float, note: String) {
    _wearRecord.update { rec ->
        rec.copy(
            spots = rec.spots.map { spot ->
                if (spot.id != id) spot else spot.copy(
                    startMm = max(0f, startMm),
                    lengthMm = max(0f, lengthMm),
                    minDiaMm = max(0f, minDiaMm),
                    note = note,
                )
            }
        )
    }
}

/**
 * Update a wear spot's authored "Measure from" reference by [id]. Display-only — same
 * pattern as `updateLinerAuthoredReference`/`updateCouplerBoltSlotReference`: it never
 * touches [WearSpot.startMm]/[WearSpot.lengthMm], only which reference point the Start
 * field re-projects against.
 */
fun ShaftViewModel.updateWearSpotReference(id: String, reference: WearSpotReference) {
    _wearRecord.update { rec ->
        rec.copy(
            spots = rec.spots.map { spot ->
                if (spot.id != id || spot.authoredReference == reference) spot
                else spot.copy(authoredReference = reference)
            }
        )
    }
}

/** Remove a wear spot by [id]. Confirm-free, as authored in the detail-view UI. */
fun ShaftViewModel.removeWearSpot(id: String) {
    _wearRecord.update { rec -> rec.copy(spots = rec.spots.filterNot { it.id == id }) }
}

// ── Wear pits (the "X" markers) ───────────────────────────────────────────────
// Stored in the same reference-only [WearRecord] as wear spots (so they ride the same
// autosave/snapshot/import paths), but keyed by *resolved component id* — a pit can sit on
// a liner, taper, or body (explicit or auto), unlike a spot (liner-only). No geometry side
// effects; orphan pits (component no longer resolves) are skipped at the render layer, same
// posture as runout readings. See model/WearSpot.kt (WearPit) and geom/WearPitMath.kt.

/**
 * Drop a new pit "X" on [componentId] at component-local [axialMm] (from the AFT edge) and
 * [acrossFrac] (0 = top outline .. 1 = bottom), with the given [size]. `acrossFrac` is
 * clamped to the interior band ([clampPitAcrossFrac]) and `axialMm` to non-negative.
 */
fun ShaftViewModel.addWearPit(componentId: String, axialMm: Float, acrossFrac: Float, size: PitSize) {
    _wearRecord.update { rec ->
        rec.copy(
            pits = rec.pits + WearPit(
                componentId = componentId,
                axialMm = max(0f, axialMm),
                acrossFrac = clampPitAcrossFrac(acrossFrac),
                size = size,
            )
        )
    }
}

/** Remove a pit by [id]. Confirm-free — the detail canvas removes a pit by tapping its "X". */
fun ShaftViewModel.removeWearPit(id: String) {
    _wearRecord.update { rec -> rec.copy(pits = rec.pits.filterNot { it.id == id }) }
}

// ── Wear diameter readings (measured-Ø callouts) ─────────────────────────────
// Same reference-only posture and storage as pits: keyed by resolved component id,
// component-local axial position, no geometry side effects, render-layer orphans.
// See model/WearSpot.kt (WearDiaReading) and geom/WearDiaCalloutLayout.kt.

/**
 * Record a measured diameter [diaMm] on [componentId] at component-local [axialMm]
 * (from the AFT edge). [diaMm] is stored verbatim — user inputs are sacred; only the
 * tap-derived [axialMm] is coerced non-negative (coarse-gesture clamp).
 */
fun ShaftViewModel.addWearDiaReading(componentId: String, axialMm: Float, diaMm: Float) {
    _wearRecord.update { rec ->
        rec.copy(
            diaReadings = rec.diaReadings + WearDiaReading(
                componentId = componentId,
                axialMm = max(0f, axialMm),
                diaMm = diaMm,
            )
        )
    }
}

/** Replace an existing reading's measured value by [id]. No-op if the id is absent. */
fun ShaftViewModel.updateWearDiaReading(id: String, diaMm: Float) {
    _wearRecord.update { rec ->
        rec.copy(diaReadings = rec.diaReadings.map { if (it.id == id) it.copy(diaMm = diaMm) else it })
    }
}

/** Remove a reading by [id]. Confirm-free — deleted from its edit dialog. */
fun ShaftViewModel.removeWearDiaReading(id: String) {
    _wearRecord.update { rec -> rec.copy(diaReadings = rec.diaReadings.filterNot { it.id == id }) }
}

// ── Wear sheet display settings ──────────────────────────────────────────────

/**
 * Pin this job's worn-profile trace exaggeration ([WearRecord.traceDepthFrac]), clamped to
 * [WEAR_TRACE_MIN_DEPTH_FRAC]..[WEAR_TRACE_MAX_DEPTH_FRAC]; `null` clears the override so
 * the document follows the Settings → Drawing default again.
 *
 * Display-only styling for the wear drawing, the undercut-exaggeration posture: the trace
 * never draws shallower than true scale and stored/printed Ø values never move, so the
 * golden rule is untouched — but it is per-document, so it lives in the record.
 */
fun ShaftViewModel.setWearTraceDepthFrac(frac: Float?) {
    val clamped = frac?.coerceIn(WEAR_TRACE_MIN_DEPTH_FRAC, WEAR_TRACE_MAX_DEPTH_FRAC)
    _wearRecord.update { rec ->
        if (rec.traceDepthFrac == clamped) rec else rec.copy(traceDepthFrac = clamped)
    }
}

/**
 * Elect which components get a broken-out detail strip on the wear sheet
 * ([WearRecord.stripComponentIds] — resolved component ids: liners, tapers, bodies).
 * `null` restores the default election (every drawable liner); an empty list prints no
 * strips at all.
 *
 * Layout-only, reference-only: no geometry side effects, and ids that no longer resolve are
 * skipped when the sheet is drawn rather than pruned here (the pit/reading posture).
 */
fun ShaftViewModel.setWearStripComponents(ids: List<String>?) {
    _wearRecord.update { rec ->
        if (rec.stripComponentIds == ids) rec else rec.copy(stripComponentIds = ids)
    }
}

/**
 * Show or hide the wear sheet's whole-shaft profile band ([WearRecord.showShaftProfile]).
 * Hiding it hands that vertical budget to the detail strips; the header, dye-pen row, and
 * elected strips are unaffected. Layout-only, per document.
 */
fun ShaftViewModel.setWearShowShaftProfile(show: Boolean) {
    _wearRecord.update { rec ->
        if (rec.showShaftProfile == show) rec else rec.copy(showShaftProfile = show)
    }
}

/**
 * Print the wear sheet's detail strips at the shaft's own page scale rather than stretched
 * toward the content width ([WearRecord.compactStrips]). The sheet keeps ONE shared strip scale
 * either way; this only lowers its ceiling. Layout-only, per document.
 */
fun ShaftViewModel.setWearCompactStrips(compact: Boolean) {
    _wearRecord.update { rec ->
        if (rec.compactStrips == compact) rec else rec.copy(compactStrips = compact)
    }
}

/**
 * Scale the wear sheet's detail strips ([WearRecord.stripSizeFrac]), clamped to
 * [WEAR_STRIP_SIZE_FRAC_MIN]..[WEAR_STRIP_SIZE_FRAC_MAX]; 1 is the height the page's own row
 * budget gives a strip (`wearRowHeightCapPt`).
 *
 * Display-only, per document: it moves the strips' height ceiling and nothing else — no stored
 * or printed measurement changes.
 */
fun ShaftViewModel.setWearStripSizeFrac(frac: Float) {
    val clamped = frac.coerceIn(WEAR_STRIP_SIZE_FRAC_MIN, WEAR_STRIP_SIZE_FRAC_MAX)
    _wearRecord.update { rec ->
        if (rec.stripSizeFrac == clamped) rec else rec.copy(stripSizeFrac = clamped)
    }
}

/**
 * Record the dye penetrant inspection's outcome ([WearRecord.dyePenResult]) — printed as
 * an "X" in the matching PASS/FAIL checkbox on the wear sheet; `null` returns both boxes
 * to blank for hand-marking. Reference-only, no geometry effect.
 */
fun ShaftViewModel.setDyePenResult(result: DyePenResult?) {
    _wearRecord.update { rec ->
        if (rec.dyePenResult == result) rec else rec.copy(dyePenResult = result)
    }
}

// ── Worn sections (consolidated runout/wear sheet) ────────────────────────────
// Reference-only, same posture as pits/diaReadings: plain _wearRecord updates, no
// geometry side effects. Shaft-space canonical (no component key → no orphans).
// See model/WornSection.kt and docs/contracts/RunoutSheet.md (Worn Sections).

/**
 * Add a designated worn section. [diaMm] values are the machinist's typed measurements,
 * stored verbatim in list order. Returns the new id so the editor can follow the row.
 */
fun ShaftViewModel.addWornSection(
    startFromAftMm: Float,
    lengthMm: Float,
    diaMm: List<Float>,
    reference: UndercutReference,
): String {
    val section = WornSection(
        startFromAftMm = max(0f, startFromAftMm),
        lengthMm = max(0f, lengthMm),
        diaMm = diaMm,
        authoredReference = reference,
    )
    _wearRecord.update { rec -> rec.copy(wornSections = rec.wornSections + section) }
    return section.id
}

/** Replace a section's span and measured values by [id]. No-op if the id is absent. */
fun ShaftViewModel.updateWornSection(id: String, startFromAftMm: Float, lengthMm: Float, diaMm: List<Float>) {
    _wearRecord.update { rec ->
        rec.copy(wornSections = rec.wornSections.map {
            if (it.id == id) it.copy(
                startFromAftMm = max(0f, startFromAftMm),
                lengthMm = max(0f, lengthMm),
                diaMm = diaMm,
            ) else it
        })
    }
}

/**
 * Switch which S.E.T. the Distance field displays against — display metadata only,
 * canonical position untouched (the WearSpotReference pattern).
 */
fun ShaftViewModel.updateWornSectionReference(id: String, reference: UndercutReference) {
    _wearRecord.update { rec ->
        rec.copy(wornSections = rec.wornSections.map {
            if (it.id == id) it.copy(authoredReference = reference) else it
        })
    }
}

/** Remove a section by [id]. Confirm-free — deleted from its edit dialog. */
fun ShaftViewModel.removeWornSection(id: String) {
    _wearRecord.update { rec -> rec.copy(wornSections = rec.wornSections.filterNot { it.id == id }) }
}
