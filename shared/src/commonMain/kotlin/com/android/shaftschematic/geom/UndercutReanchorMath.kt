package com.android.shaftschematic.geom

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import kotlin.math.abs

/**
 * UndercutReanchorMath — an undercut always keeps its reference position (on-device ruling).
 *
 * A cut is typed as a Distance from the datum its [Undercut.authoredReference] names — a S.E.T.,
 * an edge of its reference liner, or a shoulder of the cut it is nested in — and stored as a
 * shaft-space [Undercut.startFromAftMm]. When an edit to the ORIGINAL shaft moves that datum (an
 * OAL change re-anchoring the FWD taper, a liner moved or resized, an end taper lengthened), the
 * canonical start is re-derived so the Distance read against the moved datum is the number that
 * was typed. [Undercut.lengthMm], the Ø and the stored reference never change; a cut whose datum
 * did not move is returned as the same instance, so float noise never rewrites a stored start.
 *
 * Every conversion goes through the card's own pair, [canonicalToUndercutStartMm] (the Distance
 * against the datums BEFORE the edit) and [undercutStartToCanonicalMm] (the start that gives that
 * Distance against the datums AFTER it), so a FWD-flavoured reference pins the cut's FWD end
 * exactly as the card does. Nothing is clamped or validated: a cut carried off the shaft is the
 * render layer's stale-span case.
 *
 * What a re-anchor leaves alone:
 * - a `LINER_*` cut whose reference liner is missing before or after the edit;
 * - a `PARENT_*` cut whose parent cut is missing, or whose parent chain loops back on itself;
 * - a cut whose datum moved by no more than [UNDERCUT_REANCHOR_EPS_MM].
 *
 * The stored reference is what is followed, never the card's display fallbacks
 * (`effectiveUndercutReference`): a buried liner edge or a parent that no longer contains the cut
 * changes which datum the card SHOWS, not the one the Distance was typed against.
 */

/** A datum that moved by no more than this (mm) has not moved. */
const val UNDERCUT_REANCHOR_EPS_MM = 1e-3f

/**
 * The datums an undercut Distance can be read against on one drawing: the two S.E.T. stations
 * ([undercutSetPositions]) and every stored liner's shaft-space span.
 */
data class UndercutDatums(
    val aftSetXMm: Float,
    val fwdSetXMm: Float,
    val liners: List<UndercutLinerSpan>,
)

/** The datums of [spec] — the S.E.T. stations the undercut sheet uses and every liner's span. */
fun undercutDatumsOf(spec: ShaftSpec): UndercutDatums {
    val (aftSet, fwdSet) = undercutSetPositions(spec)
    return UndercutDatums(
        aftSetXMm = aftSet,
        fwdSetXMm = fwdSet,
        liners = spec.liners.map { UndercutLinerSpan(it.id, it.startFromAftMm, it.startFromAftMm + it.lengthMm) },
    )
}

/**
 * [undercuts] re-anchored from the datums [before] an edit to the datums [after] it. Parents are
 * re-anchored before their children, and a `PARENT_*` cut follows its parent's NEW span. Returns
 * the same list instance when no cut moved.
 */
fun reanchorUndercuts(
    undercuts: List<Undercut>,
    before: UndercutDatums,
    after: UndercutDatums,
): List<Undercut> {
    if (undercuts.isEmpty()) return undercuts
    val byId = HashMap<String, Undercut>()
    undercuts.forEach { u -> byId.getOrPut(u.id) { u } }
    val done = HashMap<String, Undercut>()
    val visiting = HashSet<String>()

    fun resolve(u: Undercut): Undercut {
        done[u.id]?.let { if (byId[u.id] === u) return it }
        // A parent chain that comes back to a cut already being resolved has no datum to follow.
        if (!visiting.add(u.id)) return u
        val out = reanchorOne(u, before, after, byId, ::resolve)
        visiting.remove(u.id)
        if (byId[u.id] === u) done[u.id] = out
        return out
    }

    var changed = false
    val out = undercuts.map { u -> resolve(u).also { if (it !== u) changed = true } }
    return if (changed) out else undercuts
}

/** [record] with its cuts re-anchored from [before] to [after]; the same instance when none moved. */
fun UndercutRecord.reanchoredFor(before: ShaftSpec, after: ShaftSpec): UndercutRecord {
    if (undercuts.isEmpty() || before == after) return this
    val moved = reanchorUndercuts(undercuts, undercutDatumsOf(before), undercutDatumsOf(after))
    return if (moved === undercuts) this else copy(undercuts = moved)
}

private fun reanchorOne(
    u: Undercut,
    before: UndercutDatums,
    after: UndercutDatums,
    byId: Map<String, Undercut>,
    resolve: (Undercut) -> Undercut,
): Undercut {
    val ref = u.authoredReference
    var linerBefore: UndercutLinerSpan? = null
    var linerAfter: UndercutLinerSpan? = null
    var parentBefore: Undercut? = null
    var parentAfter: Undercut? = null
    val datumBefore: Float
    val datumAfter: Float
    when (ref) {
        UndercutReference.AFT_SET -> {
            datumBefore = before.aftSetXMm
            datumAfter = after.aftSetXMm
        }
        UndercutReference.FWD_SET -> {
            datumBefore = before.fwdSetXMm
            datumAfter = after.fwdSetXMm
        }
        UndercutReference.LINER_AFT, UndercutReference.LINER_FWD -> {
            val lb = before.liners.firstOrNull { it.id == u.referenceLinerId } ?: return u
            val la = after.liners.firstOrNull { it.id == u.referenceLinerId } ?: return u
            linerBefore = lb
            linerAfter = la
            val aftEdge = ref == UndercutReference.LINER_AFT
            datumBefore = if (aftEdge) lb.startMm else lb.endMm
            datumAfter = if (aftEdge) la.startMm else la.endMm
        }
        UndercutReference.PARENT_AFT, UndercutReference.PARENT_FWD -> {
            val parentId = u.referenceCutId
            if (parentId.isEmpty() || parentId == u.id) return u
            val pb = byId[parentId] ?: return u
            val pa = resolve(pb)
            parentBefore = pb
            parentAfter = pa
            val aftShoulder = ref == UndercutReference.PARENT_AFT
            datumBefore = if (aftShoulder) pb.startFromAftMm else pb.startFromAftMm + pb.lengthMm
            datumAfter = if (aftShoulder) pa.startFromAftMm else pa.startFromAftMm + pa.lengthMm
        }
    }
    if (abs(datumAfter - datumBefore) <= UNDERCUT_REANCHOR_EPS_MM) return u

    val distanceMm = canonicalToUndercutStartMm(
        reference = ref,
        canonicalStartMm = u.startFromAftMm,
        lengthMm = u.lengthMm,
        aftSetXMm = before.aftSetXMm,
        fwdSetXMm = before.fwdSetXMm,
        linerStartMm = linerBefore?.startMm ?: 0f,
        linerEndMm = linerBefore?.endMm ?: 0f,
        parentStartMm = parentBefore?.startFromAftMm ?: 0f,
        parentEndMm = parentBefore?.let { it.startFromAftMm + it.lengthMm } ?: 0f,
    )
    val newStart = undercutStartToCanonicalMm(
        reference = ref,
        enteredMm = distanceMm,
        lengthMm = u.lengthMm,
        aftSetXMm = after.aftSetXMm,
        fwdSetXMm = after.fwdSetXMm,
        linerStartMm = linerAfter?.startMm ?: 0f,
        linerEndMm = linerAfter?.endMm ?: 0f,
        parentStartMm = parentAfter?.startFromAftMm ?: 0f,
        parentEndMm = parentAfter?.let { it.startFromAftMm + it.lengthMm } ?: 0f,
    )
    return if (newStart == u.startFromAftMm) u else u.copy(startFromAftMm = newStart)
}
