package com.android.shaftschematic.ui.viewmodel

import android.util.Log
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BodySplitResult
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.mergeBodiesAround
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.splitBodiesAround
import com.android.shaftschematic.model.syncExcludedThreadPositions
import com.android.shaftschematic.model.withAutoBlend
import com.android.shaftschematic.model.withBodyAt
import com.android.shaftschematic.model.withKeyways180Apart
import com.android.shaftschematic.model.withKeyways90Apart
import com.android.shaftschematic.model.withKeyways90Cw
import com.android.shaftschematic.model.withPhysical
import com.android.shaftschematic.ui.input.oalAfterTaperAddMm
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel.Companion.deriveTaperDiameters
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel.Companion.taperSmallEndAtStart
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.keywayUnitKey
import kotlinx.coroutines.flow.update
import kotlin.math.max

/**
 * ShaftViewModelComponents — extension functions for every component add/update/remove:
 * bodies, tapers, threads, liners, and the reference-only coupler bolt slots, plus their
 * kind-specific setters (labels, keyways, blends, Ø visibility, authored references, and the
 * derived-key keyway/metric-thread unit overrides).
 *
 * Extracted from ShaftViewModel to keep component CRUD grouped by concern. All functions are
 * extensions on ShaftViewModel and access internal-visibility backing fields and helpers
 * declared in the primary class file.
 */

// ────────────────────────────────────────────────────────────────────────────
// Component add/update/remove — newest on top (all params in mm)
// ────────────────────────────────────────────────────────────────────────────

// Bodies
fun ShaftViewModel.addBodyAt(
    startMm: Float,
    lengthMm: Float,
    diaMm: Float,
    keywayWidthMm: Float = 0f,
    keywayDepthMm: Float = 0f,
    keywayLengthMm: Float = 0f,
    keywayOffsetFromEndMm: Float = 0f,
    keywayEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
    keywaySpooned: Boolean = false,
    /**
     * The unit this keyway is authored and printed in, when it differs from the component's.
     * `null` = follows the component (the overwhelmingly common case). Registered as a
     * derived-key override exactly like a metric thread's, so it rides `unit_overrides` with
     * no new field.
     */
    keywayUnit: UnitSystem? = null,
    /** Blended faces (mm) and their profile — drawing only, stored verbatim. */
    blendAftMm: Float = 0f,
    blendFwdMm: Float = 0f,
    blendProfile: BlendProfile = BlendProfile.OGEE,
    blendAftSeal: Boolean = false,
    blendFwdSeal: Boolean = false,
) {
    val id = newId()
    _spec.update { s ->
        s.copy(
            bodies = listOf(
                Body(
                    id = id,
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    diaMm = max(0f, diaMm),
                    keywayWidthMm = max(0f, keywayWidthMm),
                    keywayDepthMm = max(0f, keywayDepthMm),
                    keywayLengthMm = max(0f, keywayLengthMm),
                    keywayOffsetFromEndMm = max(0f, keywayOffsetFromEndMm),
                    keywayEnd = keywayEnd,
                    keywaySpooned = keywaySpooned,
                    // A newly authored explicit body draws at TRUE scale: an authored
                    // section is a named piece of the shaft and reads at its real
                    // proportion unless its card re-enables compression. Only creation
                    // takes this default — a stored document keeps its decoded value,
                    // which is `true` for everything saved before the flag existed.
                    compressOnDrawing = false,
                    blendAftMm = max(0f, blendAftMm),
                    blendFwdMm = max(0f, blendFwdMm),
                    blendProfile = blendProfile,
                    blendAftSeal = blendAftSeal,
                    blendFwdSeal = blendFwdSeal,
                )
            ) + s.bodies
        )
    }
    applyKeywayUnit(id, keywayUnit)
    rememberBodyDefaults(lengthMm = lengthMm, diaMm = diaMm)
    ensureOverall()
    _selectedComponentId.value = id
}

fun ShaftViewModel.updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) {
    _spec.update { s -> s.withBodyAt(index, startMm, lengthMm, diaMm) }
    if (index in _spec.value.bodies.indices) {
        rememberBodyDefaults(lengthMm = lengthMm, diaMm = diaMm)
    }
    ensureOverall()
}

/** Edit a body's keyway in place (mirrors [updateTaperKeyway]). All params in mm. */
fun ShaftViewModel.updateBodyKeyway(
    index: Int,
    widthMm: Float,
    depthMm: Float,
    lengthMm: Float,
    offsetFromEndMm: Float,
    end: LinerAuthoredReference,
    spooned: Boolean,
) = _spec.update { s ->
    if (index !in s.bodies.indices) s else {
        val old = s.bodies[index]
        s.copy(
            bodies = s.bodies.toMutableList().also { list ->
                list[index] = old.copy(
                    keywayWidthMm = max(0f, widthMm),
                    keywayDepthMm = max(0f, depthMm),
                    keywayLengthMm = max(0f, lengthMm),
                    keywayOffsetFromEndMm = max(0f, offsetFromEndMm),
                    keywayEnd = end,
                    keywaySpooned = spooned,
                )
            }
        )
    }
}

/**
 * Set the drawing note that the shaft's keyways are clocked 180° apart. Enabling clears the
 * 90° note — a shaft carries at most one clocking note. Unchanged input is a no-op.
 */
fun ShaftViewModel.setKeyways180Apart(enabled: Boolean) = _spec.update { s -> s.withKeyways180Apart(enabled) }

/**
 * Set the drawing note that the shaft's keyways are clocked 90° apart. Enabling clears the
 * 180° note — a shaft carries at most one clocking note. Unchanged input is a no-op.
 */
fun ShaftViewModel.setKeyways90Apart(enabled: Boolean) = _spec.update { s -> s.withKeyways90Apart(enabled) }

/**
 * Set the 90° clocking direction — true = clockwise viewed from aft. Meaningful only while
 * [setKeyways90Apart] is on; the choice survives toggling the note off and back on.
 */
fun ShaftViewModel.setKeyways90Cw(cw: Boolean) = _spec.update { s -> s.withKeyways90Cw(cw) }

/**
 * Remove a [Body] by its stable [id].
 *
 * The removed body (spec + order) is recoverable via [undoEdit] — the central session
 * history records the post-delete state, so undo restores both the spec and the row order.
 */
fun ShaftViewModel.removeBody(id: String) {
    Log.d("ShaftViewModel", "removeBody invoked for id=$id")
    var removed = false

    _spec.update { s ->
        val idx = s.bodies.indexOfFirst { it.id == id }
        if (idx < 0) {
            Log.w(
                "ShaftViewModel",
                "removeBody: requested id=$id not found. current ids=${s.bodies.map { it.id }}"
            )
            // NOTE: This should never happen during normal UI usage.
            return@update s
        }
        removed = true
        s.copy(
            bodies = s.bodies.toMutableList().apply { removeAt(idx) }
        )
    }

    if (removed) {
        ensureOverall()
        emitDeletedSnack(ComponentKind.BODY)
    }
}

// Tapers
/**
 * Add a taper. [startDiaMm]/[endDiaMm] arrive x-ordered AFT → FWD (the Add dialog orders
 * the typed S.E.T./L.E.T. by the taper's physical half); [reference] records which end the
 * user measured the start from, so the carousel card reopens in that frame.
 */
fun ShaftViewModel.addTaperAt(
    startMm: Float,
    lengthMm: Float,
    startDiaMm: Float,
    endDiaMm: Float,
    rateText: String = "",
    reference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    keywayWidthMm: Float = 0f,
    keywayDepthMm: Float = 0f,
    keywayLengthMm: Float = 0f,
    keywayOffsetFromSetMm: Float = 0f,
    keywaySpooned: Boolean = false,
    /**
     * The unit this keyway is authored and printed in, when it differs from the component's.
     * `null` = follows the component (the overwhelmingly common case). Registered as a
     * derived-key override exactly like a metric thread's, so it rides `unit_overrides` with
     * no new field.
     */
    keywayUnit: UnitSystem? = null,
) {
    val id = newId()
    // Which end is the Small End follows the taper's physical half, judged against the OAL
    // the shaft carries once this taper exists — in auto-OAL mode the add itself can grow
    // the shaft, and the pre-add OAL would derive the missing diameter for the wrong face.
    val smallEndAtStart = taperSmallEndAtStart(
        startMm = startMm,
        lengthMm = lengthMm,
        overallLengthMm = oalAfterTaperAddMm(
            currentOalMm = _spec.value.overallLengthMm,
            overallIsManual = _overallIsManual.value,
            startFromAftMm = startMm,
            lengthMm = lengthMm,
        ),
    )
    _spec.update { s ->
        val split = s.splitBodiesAround(startMm, startMm + lengthMm) { newId() }

        val (resolvedStartDia, resolvedEndDia) = deriveTaperDiameters(
            startDiaMm = startDiaMm, endDiaMm = endDiaMm,
            lengthMm = lengthMm, rateText = rateText,
            smallEndAtStart = smallEndAtStart
        )
        split.spec.copy(
            tapers = listOf(
                Taper(
                    id = id,
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, resolvedStartDia),
                    endDiaMm = max(0f, resolvedEndDia),
                    keywayWidthMm = max(0f, keywayWidthMm),
                    keywayDepthMm = max(0f, keywayDepthMm),
                    keywayLengthMm = max(0f, keywayLengthMm),
                    keywayOffsetFromSetMm = max(0f, keywayOffsetFromSetMm),
                    keywaySpooned = keywaySpooned,
                    taperRateText = rateText,
                    authoredReference = reference,
                )
            ) + split.spec.tapers
        )
    }
    // Read the typed SET/LET back out of the x-ordered pair the same way it was put in, so
    // a FWD-half add seeds the next dialog's SET default from a SET and not from a LET.
    applyKeywayUnit(id, keywayUnit)
    rememberTaperDefaults(
        lengthMm = lengthMm,
        setDiaMm = if (smallEndAtStart) startDiaMm else endDiaMm,
        letDiaMm = if (smallEndAtStart) endDiaMm else startDiaMm,
    )
    ensureOverall()
    _selectedComponentId.value = id
}

fun ShaftViewModel.updateTaper(
    index: Int,
    startMm: Float,
    lengthMm: Float,
    startDiaMm: Float,
    endDiaMm: Float,
    rateText: String = "",
) = _spec.update { s ->
    if (index !in s.tapers.indices) s else {
        val old = s.tapers[index]
        val effectiveRate = rateText.ifBlank { old.taperRateText }

        // Same frame as the add path: the half is judged against the OAL that will cover
        // the edited span, so an edit that pushes the taper past the current end derives
        // the missing diameter for the face the card will label.
        val (resolvedStartDia, resolvedEndDia) = deriveTaperDiameters(
            startDiaMm = startDiaMm, endDiaMm = endDiaMm,
            lengthMm = lengthMm, rateText = effectiveRate,
            smallEndAtStart = taperSmallEndAtStart(
                startMm = startMm,
                lengthMm = lengthMm,
                overallLengthMm = oalAfterTaperAddMm(
                    currentOalMm = s.overallLengthMm,
                    overallIsManual = _overallIsManual.value,
                    startFromAftMm = startMm,
                    lengthMm = lengthMm,
                ),
            )
        )

        s.copy(
            tapers = s.tapers.toMutableList().also { list ->
                list[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, resolvedStartDia),
                    endDiaMm = max(0f, resolvedEndDia),
                    taperRateText = effectiveRate,
                )
            }
        )
    }
}.also {
    if (index in _spec.value.tapers.indices) {
        // The x-ordered pair is read back as SET/LET through the taper's own half — seeding
        // the SET default from startDiaMm alone would take a FWD-half taper's LET.
        val smallEndAtStart = taperSmallEndAtStart(
            startMm = startMm,
            lengthMm = lengthMm,
            overallLengthMm = oalAfterTaperAddMm(
                currentOalMm = _spec.value.overallLengthMm,
                overallIsManual = _overallIsManual.value,
                startFromAftMm = startMm,
                lengthMm = lengthMm,
            ),
        )
        rememberTaperDefaults(
            lengthMm = lengthMm,
            setDiaMm = if (smallEndAtStart) startDiaMm else endDiaMm,
            letDiaMm = if (smallEndAtStart) endDiaMm else startDiaMm,
        )
    }
    ensureOverall()
}

fun ShaftViewModel.updateTaperKeyway(
    index: Int,
    widthMm: Float,
    depthMm: Float,
    lengthMm: Float,
    offsetFromSetMm: Float,
    spooned: Boolean,
) = _spec.update { s ->
    if (index !in s.tapers.indices) s else {
        val old = s.tapers[index]
        val updatedTapers = s.tapers.toMutableList().also { list ->
            list[index] = old.copy(
                keywayWidthMm = max(0f, widthMm),
                keywayDepthMm = max(0f, depthMm),
                keywayLengthMm = max(0f, lengthMm),
                keywayOffsetFromSetMm = max(0f, offsetFromSetMm),
                keywaySpooned = spooned,
            )
        }
        s.copy(tapers = updatedTapers)
    }
}


fun ShaftViewModel.updateTaperAuthoredReference(index: Int, reference: LinerAuthoredReference) = _spec.update { s ->
    if (index !in s.tapers.indices) s else {
        val old = s.tapers[index]
        if (old.authoredReference == reference) return@update s
        s.copy(
            tapers = s.tapers.toMutableList().also { l ->
                l[index] = old.copy(authoredReference = reference)
            }
        )
    }
}

/** Remove a [Taper] by id. Recoverable via [undoEdit] (spec + order restored together). */
fun ShaftViewModel.removeTaper(id: String) {
    Log.d("ShaftViewModel", "removeTaper invoked for id=$id")
    var removed = false

    _spec.update { s ->
        val idx = s.tapers.indexOfFirst { it.id == id }
        if (idx < 0) {
            Log.w(
                "ShaftViewModel",
                "removeTaper: requested id=$id not found. current ids=${s.tapers.map { it.id }}"
            )
            // NOTE: This should never happen during normal UI usage.
            return@update s
        }
        removed = true

        val taper = s.tapers[idx]
        val afterRemoval = s.copy(tapers = s.tapers.toMutableList().apply { removeAt(idx) })
        val merge = afterRemoval.mergeBodiesAround(taper.startFromAftMm, taper.startFromAftMm + taper.lengthMm) { newId() }
        merge.spec
    }

    if (removed) {
        ensureOverall()
        emitDeletedSnack(ComponentKind.TAPER)
    }
}

// Threads
/**
 * Adds a thread segment.
 *
 * Parameters (mm):
 *  • startMm — axial start from aft face
 *  • lengthMm — axial length
 *  • majorDiaMm — major diameter
 *  • pitchMm — pitch in mm (e.g., 4 TPI ⇒ 6.35 mm)
 *  • excludeFromOAL — when true, thread length is excluded from OAL/measure-space
 *
 * UI contract: Screen & Route pass arguments in exactly this order.
 * We also construct `Threads(...)` with named arguments to avoid pitch/major swaps.
 */
fun ShaftViewModel.addThreadAt(
    startMm: Float,
    lengthMm: Float,
    majorDiaMm: Float,
    pitchMm: Float,
    excludeFromOAL: Boolean = false,
    isAftEnd: Boolean = true,
    metricDesignation: String? = null,
) {
    val id = newId()
    _spec.update { s ->
        // Excluded threads live outside the shaft envelope; they don't split in-shaft bodies.
        val split = if (!excludeFromOAL) s.splitBodiesAround(startMm, startMm + lengthMm) { newId() }
                    else BodySplitResult(s, emptyList(), emptyList())
        split.spec.copy(
            threads = listOf(
                Threads(
                    id = id,
                    startFromAftMm = startMm,
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm),
                    lengthMm = max(0f, lengthMm),
                    excludeFromOAL = excludeFromOAL,
                    isAftEnd = isAftEnd,
                    metricDesignation = metricDesignation?.ifBlank { null },
                )
            ) + split.spec.threads
        )
    }
    // A metric-designation thread keeps its native units — register an implicit mm override
    // so every formatting site resolves it to mm uniformly (see DisplayUnits).
    applyMetricThreadUnit(id, metricDesignation)
    rememberThreadDefaults(lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
    ensureOverall()
    _selectedComponentId.value = id
}

fun ShaftViewModel.updateThread(
    index: Int,
    startMm: Float,
    lengthMm: Float,
    majorDiaMm: Float,
    pitchMm: Float,
    metricDesignation: String? = null,
) = _spec.update { s ->
    if (index !in s.threads.indices) s else {
        val old = s.threads[index]
        val newLength = max(0f, lengthMm)

        // For excluded threads the start position is always derived from isAftEnd + OAL,
        // never from a user-authored startMm. Use the same formula as syncExcludedThreadPositions()
        // so the position is correct inside this single _spec.update call, avoiding a transient
        // wrong position when manual OAL mode prevents ensureOverall() from re-syncing.
        val effectiveStart = if (old.excludeFromOAL) {
            if (old.isAftEnd) -newLength else s.overallLengthMm
        } else startMm

        s.copy(
            threads = s.threads.toMutableList().also { l ->
                l[index] = old.copy(
                    startFromAftMm = effectiveStart,
                    lengthMm = newLength,
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm),
                    metricDesignation = metricDesignation?.ifBlank { null },
                )
            }
        )
    }
}.also {
    if (index in _spec.value.threads.indices) {
        applyMetricThreadUnit(_spec.value.threads[index].id, metricDesignation)
        rememberThreadDefaults(lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
    }
    ensureOverall()
}

/**
 * Keeps a thread's implicit display-unit override in step with its metric designation:
 * a metric thread pins to mm; clearing the designation drops the pin (back to document
 * unit), unless the user has since set an explicit override for that id.
 */
/**
 * Sets (or clears, with null) the unit a component's KEYWAY is authored and printed in.
 *
 * Public counterpart of [applyMetricThreadUnit]: both register a display-unit override under a
 * derived key rather than adding storage, so a metric keyway on an imperial taper travels in
 * the same `unit_overrides` map as everything else.
 */
fun ShaftViewModel.setKeywayUnit(componentId: String, unit: UnitSystem?) {
    if (componentId.isBlank()) return
    applyKeywayUnit(componentId, unit)
}

private fun ShaftViewModel.applyKeywayUnit(componentId: String, unit: UnitSystem?) {
    val key = keywayUnitKey(componentId)
    _unitOverrides.update { if (unit == null) it - key else it + (key to unit) }
}

private fun ShaftViewModel.applyMetricThreadUnit(threadId: String, metricDesignation: String?) {
    if (!metricDesignation.isNullOrBlank()) {
        _unitOverrides.update { it + (threadId to UnitSystem.MILLIMETERS) }
    } else {
        _unitOverrides.update { it - threadId }
    }
}

fun ShaftViewModel.setThreadExcludeFromOal(id: String, excludeFromOAL: Boolean) = _spec.update { s ->
    val idx = s.threads.indexOfFirst { it.id == id }
    if (idx == -1) s
    else s.copy(
        threads = s.threads.toMutableList().also { l ->
            val old = l[idx]
            l[idx] = old.copy(excludeFromOAL = excludeFromOAL)
        }
    ).syncExcludedThreadPositions()
}.also { ensureOverall() }

fun ShaftViewModel.setThreadEndPosition(id: String, isAft: Boolean) = _spec.update { s ->
    val idx = s.threads.indexOfFirst { it.id == id }
    if (idx == -1) s
    else s.copy(
        threads = s.threads.toMutableList().also { l ->
            l[idx] = l[idx].copy(isAftEnd = isAft)
        }
    ).syncExcludedThreadPositions()
}

/** Remove a [Threads] segment by id. Recoverable via [undoEdit] (spec + order together). */
fun ShaftViewModel.removeThread(id: String) {
    Log.d("ShaftViewModel", "removeThread invoked for id=$id")
    var removed = false

    _spec.update { s ->
        val idx = s.threads.indexOfFirst { it.id == id }
        if (idx < 0) {
            Log.w(
                "ShaftViewModel",
                "removeThread: requested id=$id not found. current ids=${s.threads.map { it.id }}"
            )
            // NOTE: This should never happen during normal UI usage.
            return@update s
        }
        removed = true

        val thread = s.threads[idx]
        val afterRemoval = s.copy(threads = s.threads.toMutableList().apply { removeAt(idx) })
        // Only merge bodies around in-shaft threads; excluded threads live outside the envelope.
        val merge = if (!thread.excludeFromOAL)
            afterRemoval.mergeBodiesAround(thread.startFromAftMm, thread.startFromAftMm + thread.lengthMm) { newId() }
        else BodySplitResult(afterRemoval, emptyList(), emptyList())
        merge.spec
    }

    if (removed) {
        // Maintain coverage and show the undo snackbar.
        ensureOverall()
        emitDeletedSnack(ComponentKind.THREAD)
    }
}

// Liners
fun ShaftViewModel.addLinerAt(
    startMm: Float,
    lengthMm: Float,
    odMm: Float,
    reference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    // Shoulders ride the add under the add-dialog-parity rule; all-zero = none.
    shoulderAftLenMm: Float = 0f,
    shoulderAftOdMm: Float = 0f,
    shoulderAftRadiusMm: Float = 0f,
    shoulderFwdLenMm: Float = 0f,
    shoulderFwdOdMm: Float = 0f,
    shoulderFwdRadiusMm: Float = 0f,
) {
    val id = newId()
    _spec.update { s ->
        val len = max(0f, lengthMm)
        val split = s.splitBodiesAround(startMm, startMm + len) { newId() }
        val od = max(0f, odMm)
        val liner = Liner(
            id = id,
            startFromAftMm = startMm,
            lengthMm = len,
            odMm = od,
            endMmPhysical = startMm + len,
            authoredReference = reference,
            shoulderAftLenMm = shoulderAftLenMm,
            shoulderAftOdMm = shoulderAftOdMm,
            shoulderAftRadiusMm = shoulderAftRadiusMm,
            shoulderFwdLenMm = shoulderFwdLenMm,
            shoulderFwdOdMm = shoulderFwdOdMm,
            shoulderFwdRadiusMm = shoulderFwdRadiusMm,
        )
        split.spec.copy(liners = listOf(liner) + split.spec.liners)
    }
    rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
    ensureOverall()
    _selectedComponentId.value = id
}

fun ShaftViewModel.updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
    if (index !in s.liners.indices) s else {
        val old = s.liners[index]
        val len = max(0f, lengthMm)
        val od = max(0f, odMm)
        s.copy(
            liners = s.liners.toMutableList().also { l ->
                l[index] = old.withPhysical(startMmPhysical = startMm, lengthMm = len, odMm = od)
            }
        )
    }
}.also {
    if (index in _spec.value.liners.indices) {
        rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
    }
    ensureOverall()
}

fun ShaftViewModel.updateLinerAuthoredReference(index: Int, reference: LinerAuthoredReference) = _spec.update { s ->
    if (index !in s.liners.indices) s else {
        val old = s.liners[index]
        if (old.authoredReference == reference) return@update s
        s.copy(
            liners = s.liners.toMutableList().also { l ->
                l[index] = old.copy(authoredReference = reference)
            }
        )
    }
}

/**
 * Lens-shaped helper behind the trivial per-kind setters below (label ×4, show-Ø ×2, show-name ×4):
 * bounds-guard [index] against [list], normalize the incoming [newValue], then no-op when the
 * normalized value already matches the stored one — LOAD-BEARING: keeps a recomposition that
 * hands back the same value from ever marking the document dirty or churning the undo recorder.
 * Otherwise builds the item's replacement via [copyField] and threads the updated list back into
 * the spec via [withList] (the per-kind `s.copy(bodies = …)` / `s.copy(liners = …)` / etc.).
 */
private inline fun <T, V> ShaftSpec.withItemField(
    list: List<T>,
    index: Int,
    newValue: V,
    normalize: (V) -> V = { it },
    get: (T) -> V,
    copyField: (T, V) -> T,
    withList: (List<T>) -> ShaftSpec,
): ShaftSpec {
    if (index !in list.indices) return this
    val old = list[index]
    val normalized = normalize(newValue)
    if (get(old) == normalized) return this
    return withList(list.toMutableList().also { it[index] = copyField(old, normalized) })
}

private fun normalizeLabel(label: String?): String? = label?.trim()?.takeIf { it.isNotEmpty() }

fun ShaftViewModel.updateLinerLabel(index: Int, label: String?) = _spec.update { s ->
    s.withItemField(
        list = s.liners, index = index, newValue = label,
        normalize = ::normalizeLabel,
        get = { it.label },
        copyField = { old, v -> old.copy(label = v) },
        withList = { s.copy(liners = it) },
    )
}

fun ShaftViewModel.updateBodyLabel(index: Int, label: String?) = _spec.update { s ->
    s.withItemField(
        list = s.bodies, index = index, newValue = label,
        normalize = ::normalizeLabel,
        get = { it.label },
        copyField = { old, v -> old.copy(label = v) },
        withList = { s.copy(bodies = it) },
    )
}

/**
 * Show/hide this body's Ø callout on the schematic. Draw-only — no geometry, no value
 * rewrite. Routed through [withItemField], whose identity guard no-ops when the flag
 * already matches so a recomposition can never mark the document dirty.
 */
fun ShaftViewModel.updateBodyShowDia(index: Int, show: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.bodies, index = index, newValue = show,
        get = { it.showDiaOnDrawing },
        copyField = { old, v -> old.copy(showDiaOnDrawing = v) },
        withList = { s.copy(bodies = it) },
    )
}

/**
 * Allow/forbid this body foreshortening on a sheet. Draw-only — no geometry, no value
 * rewrite — and the same identity-guarded [withItemField] path as [updateBodyShowDia].
 * Turning it OFF pins the body's stored span at true width and the drawn height yields
 * around it; turning it back ON is the escape hatch for a body long enough that pinning it
 * would starve the rest of the shaft.
 */
fun ShaftViewModel.updateBodyCompressOnDrawing(index: Int, compress: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.bodies, index = index, newValue = compress,
        get = { it.compressOnDrawing },
        copyField = { old, v -> old.copy(compressOnDrawing = v) },
        withList = { s.copy(bodies = it) },
    )
}

/**
 * Show/hide this body's NAME label on the schematic. Draw-only, and the same identity-guarded
 * [withItemField] path as [updateBodyShowDia] — the label text itself is never rewritten.
 */
fun ShaftViewModel.updateBodyShowLabel(index: Int, show: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.bodies, index = index, newValue = show,
        get = { it.showNameOnDrawing },
        copyField = { old, v -> old.copy(showNameOnDrawing = v) },
        withList = { s.copy(bodies = it) },
    )
}

/** Taper mirror of [updateBodyShowLabel]. */
fun ShaftViewModel.updateTaperShowLabel(index: Int, show: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.tapers, index = index, newValue = show,
        get = { it.showNameOnDrawing },
        copyField = { old, v -> old.copy(showNameOnDrawing = v) },
        withList = { s.copy(tapers = it) },
    )
}

/** Thread mirror of [updateBodyShowLabel]. */
fun ShaftViewModel.updateThreadShowLabel(index: Int, show: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.threads, index = index, newValue = show,
        get = { it.showNameOnDrawing },
        copyField = { old, v -> old.copy(showNameOnDrawing = v) },
        withList = { s.copy(threads = it) },
    )
}

/** Liner mirror of [updateBodyShowLabel]. */
fun ShaftViewModel.updateLinerShowLabel(index: Int, show: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.liners, index = index, newValue = show,
        get = { it.showNameOnDrawing },
        copyField = { old, v -> old.copy(showNameOnDrawing = v) },
        withList = { s.copy(liners = it) },
    )
}

/**
 * Shade/bare THIS body on the drawing, overriding the kind's Settings checkbox either way.
 * Draw-only, and the same identity-guarded [withItemField] path as [updateBodyShowDia] — no
 * geometry, no value rewrite. Only the two dimensioned sheets read it; the wear and undercut
 * documents keep one fill per kind.
 */
fun ShaftViewModel.updateBodyShade(index: Int, shade: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.bodies, index = index, newValue = shade,
        get = { it.shadeOnDrawing },
        copyField = { old, v -> old.copy(shadeOnDrawing = v) },
        withList = { s.copy(bodies = it) },
    )
}

/** Taper mirror of [updateBodyShade]. */
fun ShaftViewModel.updateTaperShade(index: Int, shade: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.tapers, index = index, newValue = shade,
        get = { it.shadeOnDrawing },
        copyField = { old, v -> old.copy(shadeOnDrawing = v) },
        withList = { s.copy(tapers = it) },
    )
}

/**
 * Liner mirror of [updateBodyShade]. A consolidated sheet printing measured Ø values inside
 * the profile still draws every liner bare — the knockout-halo rule outranks this override.
 */
fun ShaftViewModel.updateLinerShade(index: Int, shade: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.liners, index = index, newValue = shade,
        get = { it.shadeOnDrawing },
        copyField = { old, v -> old.copy(shadeOnDrawing = v) },
        withList = { s.copy(liners = it) },
    )
}

/**
 * Sets a body's blended faces and their profile.
 *
 * Drawing-only: a blend changes the silhouette and nothing else — not OAL, not resolve,
 * not collision, and no other component's span. The lengths are stored VERBATIM; a value
 * longer than the body is clamped where it is DRAWN, never here.
 */
fun ShaftViewModel.updateBodyBlend(
    index: Int,
    blendAftMm: Float,
    blendFwdMm: Float,
    profile: BlendProfile,
    sealAft: Boolean = false,
    sealFwd: Boolean = false,
) =
    _spec.update { s ->
        if (index !in s.bodies.indices) s else {
            val old = s.bodies[index]
            if (old.blendAftMm == blendAftMm &&
                old.blendFwdMm == blendFwdMm &&
                old.blendProfile == profile &&
                old.blendAftSeal == sealAft &&
                old.blendFwdSeal == sealFwd
            ) return@update s
            s.copy(
                bodies = s.bodies.toMutableList().also { l ->
                    l[index] = old.copy(
                        blendAftMm = blendAftMm.coerceAtLeast(0f),
                        blendFwdMm = blendFwdMm.coerceAtLeast(0f),
                        blendProfile = profile,
                        blendAftSeal = sealAft,
                        blendFwdSeal = sealFwd,
                    )
                }
            )
        }
    }

/**
 * Sets a blended face on ONE auto-body span, keyed in shaft space by an anchor at the span
 * midpoint (the [setAutoSectionDiaMm] posture).
 *
 * Drawing-only, and it never promotes the span: an auto body stays derived, which is the
 * point — a blend anchored to the span survives edits that would strand one authored
 * against a promoted body's fixed boundary. [lengthMm] ≤ 0 clears that face; the value is
 * stored verbatim and clamped only where it is drawn.
 */
fun ShaftViewModel.setAutoBlend(
    spanStartMm: Float,
    spanEndMm: Float,
    end: LinerAuthoredReference,
    lengthMm: Float,
    profile: BlendProfile,
    seal: Boolean = false,
) = _spec.update { s -> s.withAutoBlend(spanStartMm, spanEndMm, end, lengthMm, profile, seal) }

/** Liner mirror of [updateBodyShowDia]. */
fun ShaftViewModel.updateLinerShowDia(index: Int, show: Boolean) = _spec.update { s ->
    s.withItemField(
        list = s.liners, index = index, newValue = show,
        get = { it.showDiaOnDrawing },
        copyField = { old, v -> old.copy(showDiaOnDrawing = v) },
        withList = { s.copy(liners = it) },
    )
}

/**
 * One end's shoulder, stored verbatim (golden rule — no clamp, no snap; the DRAW site
 * clamps what it cannot express). Zeroed length or Ø means "no shoulder on this end".
 */
fun ShaftViewModel.updateLinerShoulder(
    index: Int,
    end: LinerAuthoredReference,
    lenMm: Float,
    odMm: Float,
    radiusMm: Float,
) = _spec.update { s ->
    if (index !in s.liners.indices) s else {
        val old = s.liners[index]
        val new = when (end) {
            LinerAuthoredReference.AFT -> old.copy(
                shoulderAftLenMm = lenMm, shoulderAftOdMm = odMm, shoulderAftRadiusMm = radiusMm)
            LinerAuthoredReference.FWD -> old.copy(
                shoulderFwdLenMm = lenMm, shoulderFwdOdMm = odMm, shoulderFwdRadiusMm = radiusMm)
        }
        if (new == old) return@update s
        s.copy(liners = s.liners.toMutableList().also { l -> l[index] = new })
    }
}

/**
 * Show/hide the bare-shaft Ø callout. One flag for every auto span — the shaft between
 * explicit components is one piece of stock, so it carries one visibility, matching the
 * single [ShaftSpec.autoBodyDiaMm].
 */
fun ShaftViewModel.setShowAutoBodyDia(show: Boolean) = _spec.update { s ->
    if (s.showAutoBodyDia == show) s else s.copy(showAutoBodyDia = show)
}

fun ShaftViewModel.updateTaperLabel(index: Int, label: String?) = _spec.update { s ->
    s.withItemField(
        list = s.tapers, index = index, newValue = label,
        normalize = ::normalizeLabel,
        get = { it.label },
        copyField = { old, v -> old.copy(label = v) },
        withList = { s.copy(tapers = it) },
    )
}

fun ShaftViewModel.updateThreadLabel(index: Int, label: String?) = _spec.update { s ->
    s.withItemField(
        list = s.threads, index = index, newValue = label,
        normalize = ::normalizeLabel,
        get = { it.label },
        copyField = { old, v -> old.copy(label = v) },
        withList = { s.copy(threads = it) },
    )
}

/** Remove a [Liner] by id. Recoverable via [undoEdit] (spec + order restored together). */
fun ShaftViewModel.removeLiner(id: String) {
    Log.d("ShaftViewModel", "removeLiner invoked for id=$id")
    var removed = false

    _spec.update { s ->
        val idx = s.liners.indexOfFirst { it.id == id }
        if (idx < 0) {
            Log.w(
                "ShaftViewModel",
                "removeLiner: requested id=$id not found. current ids=${s.liners.map { it.id }}"
            )
            // NOTE: This should never happen during normal UI usage.
            return@update s
        }
        removed = true

        val liner = s.liners[idx]
        val afterRemoval = s.copy(liners = s.liners.toMutableList().apply { removeAt(idx) })
        val merge = afterRemoval.mergeBodiesAround(liner.startFromAftMm, liner.startFromAftMm + liner.lengthMm) { newId() }
        merge.spec
    }

    if (removed) {
        ensureOverall()
        emitDeletedSnack(ComponentKind.LINER)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Coupler bolt slots — reference cutouts. No body-splitting, no OAL impact,
// no collision. Add/update/remove mirror the other component trios.
// ────────────────────────────────────────────────────────────────────────────

fun ShaftViewModel.addCouplerBoltSlotAt(
    startMm: Float,
    holeDiaMm: Float,
    count: Int,
    spacingMm: Float,
    through: Boolean = true,
    depthMm: Float = 0f,
    reference: SlotAuthoredReference = SlotAuthoredReference.FWD,
) {
    val id = newId()
    _spec.update { s ->
        val slot = CouplerBoltSlot(
            id = id,
            startFromAftMm = max(0f, startMm),
            holeDiaMm = max(0f, holeDiaMm),
            count = count.coerceAtLeast(1),
            spacingMm = max(0f, spacingMm),
            through = through,
            depthMm = max(0f, depthMm),
            authoredReference = reference,
        )
        // Newest-on-top, like the other component lists.
        s.copy(couplerBoltSlots = listOf(slot) + s.couplerBoltSlots)
    }
    rememberSlotDefaults(holeDiaMm = holeDiaMm, spacingMm = spacingMm, depthMm = depthMm, count = count)
    // NOTE: deliberately no ensureOverall() — slots never drive OAL.
    _selectedComponentId.value = id
}

fun ShaftViewModel.updateCouplerBoltSlot(
    index: Int,
    startMm: Float,
    holeDiaMm: Float,
    count: Int,
    spacingMm: Float,
    through: Boolean,
    depthMm: Float,
) = _spec.update { s ->
    if (index !in s.couplerBoltSlots.indices) s else {
        val old = s.couplerBoltSlots[index]
        s.copy(
            couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                l[index] = old.copy(
                    startFromAftMm = max(0f, startMm),
                    holeDiaMm = max(0f, holeDiaMm),
                    count = count.coerceAtLeast(1),
                    spacingMm = max(0f, spacingMm),
                    through = through,
                    depthMm = max(0f, depthMm),
                )
            }
        )
    }
}.also {
    if (index in _spec.value.couplerBoltSlots.indices) {
        rememberSlotDefaults(holeDiaMm = holeDiaMm, spacingMm = spacingMm, depthMm = depthMm, count = count)
    }
}

fun ShaftViewModel.updateCouplerBoltSlotReference(index: Int, reference: SlotAuthoredReference) = _spec.update { s ->
    if (index !in s.couplerBoltSlots.indices) s else {
        val old = s.couplerBoltSlots[index]
        if (old.authoredReference == reference) return@update s
        s.copy(
            couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                l[index] = old.copy(authoredReference = reference)
            }
        )
    }
}

fun ShaftViewModel.updateCouplerBoltSlotShowRail(index: Int, show: Boolean) = _spec.update { s ->
    if (index !in s.couplerBoltSlots.indices) s else {
        val old = s.couplerBoltSlots[index]
        if (old.showDimensionRail == show) return@update s
        s.copy(
            couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                l[index] = old.copy(showDimensionRail = show)
            }
        )
    }
}

/** Remove a [CouplerBoltSlot] by id. Recoverable via [undoEdit] (spec + order together). */
fun ShaftViewModel.removeCouplerBoltSlot(id: String) {
    var removed = false

    _spec.update { s ->
        val idx = s.couplerBoltSlots.indexOfFirst { it.id == id }
        if (idx < 0) return@update s
        removed = true
        // No body merge needed — slots never split bodies.
        s.copy(couplerBoltSlots = s.couplerBoltSlots.toMutableList().apply { removeAt(idx) })
    }

    if (removed) {
        // Slots never affect OAL, so no ensureOverall() here.
        emitDeletedSnack(ComponentKind.COUPLER_BOLT_SLOT)
    }
}
