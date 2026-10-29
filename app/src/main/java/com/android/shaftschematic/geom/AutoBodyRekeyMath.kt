package com.android.shaftschematic.geom

import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord

/**
 * AutoBodyRekeyMath — keeps the measurements recorded on a bare-shaft (auto) body attached to
 * it when a neighbouring component moves one of its ends.
 *
 * An auto body's id IS its span (`auto_body_<start>_<end>`), so lengthening the liner AFT of
 * a gap by 7/16" gives the gap a new id, and every reading, dragged station, count override,
 * pit and Ø reading keyed to the old id stops drawing (the render-layer orphan rule) — the
 * typed TIRs silently leave the printed sheet (emulator QA). The id scheme itself is stored in
 * every saved document, so instead of changing it the editor's ONE original-spec seam renames
 * the keys whenever an edit gives an auto run a new id ([autoBodySuccessors] +
 * [AutoKeyedData.rekeyedFor]).
 *
 * Only UNAMBIGUOUS successors are followed: a run split in two by an inserted component, or
 * two runs merged by a removed one, has no single answer, so its data stays keyed to the old
 * span — orphaned at render exactly as before, recoverable by Undo. Typed values (TIR, Ø) are
 * never touched; only keys move, and component-local axial positions shift so each mark keeps
 * its shaft position (or, on a gap an OAL change moved whole, its place in the gap).
 *
 * An OAL change moves every FWD-referenced part by the length delta (`withNewOal`), so a gap
 * between two of them, or between one and the FWD end, moves WHOLE: `[a, b]` → `[a + d, b + d]`.
 * Once `|d|` reaches the gap's length the old and new spans no longer overlap, and a gap next to
 * it may overlap the old span instead, so [autoBodySuccessors] pairs rigidly-shifted spans first
 * and only then falls back to overlap.
 *
 * The same transform carries a body through the two explicit actions that swap its identity
 * without moving it ([bodyIdentitySwaps]): PROMOTING a bare-shaft run ("Explicit body" — a new
 * stored body with a fresh id on exactly the run's span) and DEMOTING an explicit body (the auto
 * run that then fills exactly its span). Without it, ticking "Explicit body" to add a keyway took
 * every reading on that run off the sheet.
 *
 * Pure — no Android, no resolve: callers hand in the body runs before and after the edit.
 */

/**
 * How closely two body spans must agree to be the SAME span across a promotion, a demotion or
 * an OAL change's rigid shift — the 0.01 mm slop the bounds checks and [UNDERCUT_TOUCH_EPS_MM]
 * use, wider than the float noise of a span re-derived as `start + (end − start)` or re-anchored
 * by `withNewOal` at shaft scale.
 */
const val BODY_SPAN_MATCH_EPS_MM: Float = 0.01f

/**
 * One body component: its resolved (base) id and its shaft-space extent, mm. A bare-shaft run
 * from `autoBodyRuns`, or an explicit body's stored span from [storedBodyRuns].
 */
data class AutoBodyRun(
    val id: String,
    val startMm: Float,
    val endMm: Float,
    /** Measured length — the summed drawn runs; equals `endMm − startMm` for one run. */
    val lengthMm: Float = endMm - startMm,
)

/**
 * [oldRun]'s data now belongs to [newRun]. [movedWhole] marks a run that moved rigidly with the
 * parts bounding it (an OAL change carrying two FWD-referenced neighbours): its marks move with
 * it rather than staying at their old shaft position.
 */
data class AutoBodySuccessor(
    val oldRun: AutoBodyRun,
    val newRun: AutoBodyRun,
    val movedWhole: Boolean = false,
) {
    /**
     * What a component-local axial value (measured from the AFT edge) gains. For a run whose
     * edge moved under it, `oldStart − newStart`, so the value stays at the same SHAFT position.
     * For a run that [movedWhole], 0: the gap and the parts bounding it moved together, so each
     * mark keeps its place in the gap — holding the shaft position instead would leave every mark
     * outside a gap shifted by more than its own length.
     */
    val axialShiftMm: Float get() = if (movedWhole) 0f else oldRun.startMm - newRun.startMm
}

private fun AutoBodyRun.overlaps(other: AutoBodyRun): Boolean =
    startMm < other.endMm && other.startMm < endMm

/**
 * The auto runs whose id changed under one edit, paired with the run that inherits their data.
 *
 * Two passes over the runs of [before] whose id no longer resolves in [after] ("vanished") and
 * the runs of [after] that are NEW ids, absent from [before] ("fresh"):
 * 1. RIGID SHIFT, only when [shiftMm] ≠ 0 (the OAL delta of an OAL change; 0 for every other
 *    edit): a vanished `[a, b]` pairs with the single fresh run equal to `[a + shiftMm,
 *    b + shiftMm]` within [BODY_SPAN_MATCH_EPS_MM], one-to-one both ways. Such a pair
 *    [AutoBodySuccessor.movedWhole].
 * 2. OVERLAP, over the runs neither side claimed in pass 1: the successor is the single fresh
 *    run that overlaps the vanished one and overlaps no other unclaimed vanished run. No
 *    overlapping run (the gap was filled), two candidates (a split), or a candidate shared with
 *    another vanished run (a merge) maps to nothing.
 *
 * The result is therefore one-to-one, and with [shiftMm] = 0 it is the overlap pass alone.
 */
fun autoBodySuccessors(
    before: List<AutoBodyRun>,
    after: List<AutoBodyRun>,
    shiftMm: Float = 0f,
): List<AutoBodySuccessor> {
    val beforeIds = before.mapTo(HashSet()) { it.id }
    val afterIds = after.mapTo(HashSet()) { it.id }
    val vanished = before.filter { it.id !in afterIds }
    if (vanished.isEmpty()) return emptyList()
    val fresh = after.filter { it.id !in beforeIds }
    if (fresh.isEmpty()) return emptyList()

    val rigid = if (shiftMm == 0f) emptyList() else {
        fun AutoBodyRun.shifted() = copy(startMm = startMm + shiftMm, endMm = endMm + shiftMm)
        vanished.mapNotNull { old ->
            val moved = old.shifted()
            val candidate = fresh.filter { it.sameSpan(moved, BODY_SPAN_MATCH_EPS_MM) }.singleOrNull()
                ?: return@mapNotNull null
            if (vanished.count { it.shifted().sameSpan(candidate, BODY_SPAN_MATCH_EPS_MM) } != 1) {
                return@mapNotNull null
            }
            AutoBodySuccessor(old, candidate, movedWhole = true)
        }
    }
    val claimedOld = rigid.mapTo(HashSet()) { it.oldRun.id }
    val claimedNew = rigid.mapTo(HashSet()) { it.newRun.id }
    val openVanished = vanished.filter { it.id !in claimedOld }
    val openFresh = fresh.filter { it.id !in claimedNew }

    return rigid + openVanished.mapNotNull { old ->
        val candidate = openFresh.filter { it.overlaps(old) }.singleOrNull() ?: return@mapNotNull null
        if (openVanished.count { it.overlaps(candidate) } != 1) return@mapNotNull null
        AutoBodySuccessor(old, candidate)
    }
}

private fun AutoBodyRun.sameSpan(other: AutoBodyRun, epsMm: Float): Boolean =
    kotlin.math.abs(startMm - other.startMm) <= epsMm && kotlin.math.abs(endMm - other.endMm) <= epsMm

/**
 * Bodies whose identity an edit swapped without moving them, paired with the body that takes
 * over their data:
 * - PROMOTION — an auto run that vanished, and a NEW explicit body (absent from
 *   [explicitBefore]) whose stored span is that run's span;
 * - DEMOTION — an explicit body that was removed, and a NEW auto run (absent from [autoBefore])
 *   that now occupies exactly its stored span.
 *
 * Spans match within [epsMm]. A partner shared by two candidates, or two partners for one,
 * pairs nothing. [explicitBefore]/[explicitAfter] are the STORED explicit bodies ([storedBodyRuns]).
 */
fun bodyIdentitySwaps(
    autoBefore: List<AutoBodyRun>,
    autoAfter: List<AutoBodyRun>,
    explicitBefore: List<AutoBodyRun>,
    explicitAfter: List<AutoBodyRun>,
    epsMm: Float = BODY_SPAN_MATCH_EPS_MM,
): List<AutoBodySuccessor> {
    fun pairs(gone: List<AutoBodyRun>, arrived: List<AutoBodyRun>): List<AutoBodySuccessor> =
        gone.mapNotNull { old ->
            val partner = arrived.filter { it.sameSpan(old, epsMm) }.singleOrNull() ?: return@mapNotNull null
            if (gone.count { it.sameSpan(partner, epsMm) } != 1) return@mapNotNull null
            AutoBodySuccessor(old, partner)
        }

    val autoAfterIds = autoAfter.mapTo(HashSet()) { it.id }
    val autoBeforeIds = autoBefore.mapTo(HashSet()) { it.id }
    val explicitAfterIds = explicitAfter.mapTo(HashSet()) { it.id }
    val explicitBeforeIds = explicitBefore.mapTo(HashSet()) { it.id }
    val promotions = pairs(
        gone = autoBefore.filter { it.id !in autoAfterIds },
        arrived = explicitAfter.filter { it.id !in explicitBeforeIds },
    )
    val demotions = pairs(
        gone = explicitBefore.filter { it.id !in explicitAfterIds },
        arrived = autoAfter.filter { it.id !in autoBeforeIds },
    )
    return promotions + demotions
}

/**
 * Every successor one edit produced: auto runs re-identified by a neighbour's edit
 * ([autoBodySuccessors]) and bodies promoted or demoted in place ([bodyIdentitySwaps]). An id
 * that both would claim — as old or as new — pairs nothing, so the result stays one-to-one.
 */
fun bodyKeySuccessors(
    autoBefore: List<AutoBodyRun>,
    autoAfter: List<AutoBodyRun>,
    explicitBefore: List<AutoBodyRun>,
    explicitAfter: List<AutoBodyRun>,
    /** The edit's OAL delta (`after − before`), for [autoBodySuccessors]' rigid-shift pass. */
    shiftMm: Float = 0f,
): List<AutoBodySuccessor> {
    val all = autoBodySuccessors(autoBefore, autoAfter, shiftMm) +
        bodyIdentitySwaps(autoBefore, autoAfter, explicitBefore, explicitAfter)
    val oldCounts = all.groupingBy { it.oldRun.id }.eachCount()
    val newCounts = all.groupingBy { it.newRun.id }.eachCount()
    return all.filter { oldCounts[it.oldRun.id] == 1 && newCounts[it.newRun.id] == 1 }
}

/** The explicit bodies of [spec] as body runs — their STORED spans, keyed by their ids. */
fun storedBodyRuns(spec: ShaftSpec): List<AutoBodyRun> =
    spec.bodies.map { AutoBodyRun(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) }

/**
 * Every document collection keyed by a resolved component id that can name an auto body.
 * (`unit_overrides` is not here: the "Prints in" chip lives on explicit cards only, so no auto
 * id ever reaches it.)
 */
data class AutoKeyedData(
    val readings: RunoutReadings = RunoutReadings(),
    val placements: RunoutStationPlacements = RunoutStationPlacements(),
    val stationCountOverrides: Map<String, Int> = emptyMap(),
    val wearRecord: WearRecord = WearRecord(),
) {
    /** Every component id this data names. */
    fun referencedIds(): Set<String> = buildSet {
        readings.readings.forEach { add(it.componentId) }
        placements.placements.forEach { add(it.componentId) }
        addAll(stationCountOverrides.keys)
        wearRecord.pits.forEach { add(it.componentId) }
        wearRecord.diaReadings.forEach { add(it.componentId) }
        wearRecord.stripComponentIds?.let { addAll(it) }
    }

    /**
     * This data with each successor's keys renamed old → new.
     *
     * - Component-local axial values (dragged stations, pits, Ø readings) shift by
     *   [AutoBodySuccessor.axialShiftMm] so each stays at the same shaft position. A value the
     *   shift leaves outside the new span is stored as-is: the render layer already pulls a
     *   stranded station onto its run and skips what does not fit; storage is never clamped.
     * - A body whose station count is DERIVED (no override) and that carries readings or
     *   dragged stations keeps its old count as an override when the new length would derive a
     *   different one — readings are keyed by station index, so a count change would slide every
     *   typed TIR onto another bubble (the `freezeLegacyStationCounts` reasoning).
     * - A successor whose new id this data already names (stale keys from an earlier state) is
     *   skipped rather than merged: merging would put two sets of values under one key, and
     *   discarding either would delete typed measurements.
     */
    fun rekeyedFor(successors: List<AutoBodySuccessor>): AutoKeyedData {
        if (successors.isEmpty()) return this
        val used = referencedIds()
        val applicable = successors.filter { it.oldRun.id in used && it.newRun.id !in used }
        if (applicable.isEmpty()) return this
        val byOld = applicable.associateBy { it.oldRun.id }

        val overrides = stationCountOverrides.toMutableMap()
        for (s in applicable) {
            val oldId = s.oldRun.id
            val newId = s.newRun.id
            val existing = overrides.remove(oldId)
            if (existing != null) {
                overrides[newId] = existing
                continue
            }
            val hasStationData = readings.readings.any { it.componentId == oldId } ||
                placements.placements.any { it.componentId == oldId }
            if (!hasStationData) continue
            val oldCount = defaultStationCount(RunoutComponentKind.BODY, s.oldRun.lengthMm)
            val newCount = defaultStationCount(RunoutComponentKind.BODY, s.newRun.lengthMm)
            if (oldCount != newCount) overrides[newId] = oldCount
        }

        return AutoKeyedData(
            readings = RunoutReadings(
                readings.readings.map { r ->
                    byOld[r.componentId]?.let { r.copy(componentId = it.newRun.id) } ?: r
                }
            ),
            placements = RunoutStationPlacements(
                placements.placements.map { p ->
                    byOld[p.componentId]?.let {
                        p.copy(componentId = it.newRun.id, axialMm = p.axialMm + it.axialShiftMm)
                    } ?: p
                }
            ),
            stationCountOverrides = overrides,
            wearRecord = wearRecord.copy(
                pits = wearRecord.pits.map { p ->
                    byOld[p.componentId]?.let {
                        p.copy(componentId = it.newRun.id, axialMm = p.axialMm + it.axialShiftMm)
                    } ?: p
                },
                diaReadings = wearRecord.diaReadings.map { d ->
                    byOld[d.componentId]?.let {
                        d.copy(componentId = it.newRun.id, axialMm = d.axialMm + it.axialShiftMm)
                    } ?: d
                },
                stripComponentIds = wearRecord.stripComponentIds?.map { id ->
                    byOld[id]?.newRun?.id ?: id
                },
            ),
        )
    }
}
