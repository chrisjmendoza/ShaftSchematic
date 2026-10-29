package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.OAL_REANCHOR_SNAP_MM
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.formatLenWithUnit
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs

/** Names an OAL-change summary lists before it counts the rest ("+N more"). */
private const val OAL_MOVE_NAMED_MAX = 2

/**
 * What an OAL commit moved, as the editor's snackbar line — or null when it moved nothing.
 *
 * `withNewOal` keeps every FWD-measured taper, liner and coupler bolt row the same distance from
 * the FWD face, so a shorter shaft carries them aft with the end. That is the design, but it is
 * silent: an OAL correction moved a FWD liner into a mid-shaft taper and the user could not tell
 * why the two now overlapped (on-device report). This says so:
 * `OAL 234 3/16": moved FWD Liner 4 13/16" aft (measured from FWD)` — up to two names, then
 * "+N more"; "aft" or "fwd" by direction.
 *
 * Pure diff of [before] and [after] by id: a part whose stored start moved by more than
 * [OAL_REANCHOR_SNAP_MM] counts (the re-anchor's own float noise does not). Excluded threads are
 * left out — they sit beyond the shaft end and follow it by definition, which surprises nobody.
 * Names are the card titles ([componentCardTitles]); a bolt row takes its card's title. The
 * distance and the new OAL print in [unit] through the footer's length formatter.
 */
fun oalMoveSummary(before: ShaftSpec, after: ShaftSpec, unit: UnitSystem): String? {
    if (before.overallLengthMm == after.overallLengthMm) return null
    data class Moved(val id: String, val newStartMm: Float, val deltaMm: Float)

    val beforeStarts: Map<String, Float> =
        before.tapers.associate { it.id to it.startFromAftMm } +
            before.liners.associate { it.id to it.startFromAftMm } +
            before.couplerBoltSlots.associate { it.id to it.startFromAftMm }
    val afterStarts: List<Pair<String, Float>> =
        after.tapers.map { it.id to it.startFromAftMm } +
            after.liners.map { it.id to it.startFromAftMm } +
            after.couplerBoltSlots.map { it.id to it.startFromAftMm }

    val moved = afterStarts.mapNotNull { (id, start) ->
        val was = beforeStarts[id] ?: return@mapNotNull null
        val delta = start - was
        if (abs(delta) > OAL_REANCHOR_SNAP_MM) Moved(id, start, delta) else null
    }.sortedWith(compareBy({ it.newStartMm }, { it.id }))
    if (moved.isEmpty()) return null

    val titles = componentCardTitles(after) + after.couplerBoltSlots.associate { cs ->
        cs.id to (cs.label ?: if (cs.holeStyle == BoltHoleStyle.CROSS) "Coupler Bolt Hole" else "Coupler Bolt Slot")
    }
    val names = moved.map { titles[it.id] ?: "a component" }
    val listed = when {
        names.size == 1 -> names[0]
        names.size == 2 -> "${names[0]} and ${names[1]}"
        else -> names.take(OAL_MOVE_NAMED_MAX).joinToString(", ") + " +${names.size - OAL_MOVE_NAMED_MAX} more"
    }
    val delta = moved.first().deltaMm
    val direction = if (delta < 0f) "aft" else "fwd"
    val oal = formatLenWithUnit(after.overallLengthMm.toDouble(), unit)
    val distance = formatLenWithUnit(abs(delta).toDouble(), unit)
    return "OAL $oal: moved $listed $distance $direction (measured from FWD)"
}
