// file: app/src/main/java/com/android/shaftschematic/ui/screen/TaperCalcApply.kt
package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.collisionPartnerIds
import com.android.shaftschematic.model.collisionPartnerIdsForSpan
import com.android.shaftschematic.model.hasAnyKeywayValue
import com.android.shaftschematic.model.maxDiaMm
import com.android.shaftschematic.ui.input.TaperCalcValues
import com.android.shaftschematic.ui.input.TaperSide
import com.android.shaftschematic.ui.input.classifyTaperSideByMidpoint
import com.android.shaftschematic.ui.input.taperAddDiameterOrder
import com.android.shaftschematic.ui.input.taperCalcAutoRateText
import com.android.shaftschematic.ui.input.withTaperCalcValues
import com.android.shaftschematic.ui.util.overlapsMessage
import com.android.shaftschematic.ui.util.taperWarningMessages
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.taperCalcNumberText
import kotlin.math.abs
import kotlin.math.min

/**
 * Where the taper calculator's Apply can send its values and what its confirmation shows — pure,
 * because the calculator cannot be hosted under the Robolectric harness and a write into a document
 * is the last thing to leave untested. The write to an existing taper is
 * [com.android.shaftschematic.ui.input.withTaperCalcValues]; the OAL never moves on either path.
 */

/** Where Apply can send the values. [key] survives a rotation in the confirmation dialog. */
internal sealed interface TaperCalcChoice {
    val key: String

    data class Existing(val taperId: String) : TaperCalcChoice {
        override val key: String get() = "taper:$taperId"
    }

    data class NewAtEnd(val side: TaperSide) : TaperCalcChoice {
        override val key: String get() = "new:${side.name}"
    }
}

/**
 * Every taper on [spec] AFT → FWD, then a new taper at each end that has none. The FWD end is not
 * a place until the OAL has been typed, so it is not offered before then.
 */
internal fun taperCalcChoices(spec: ShaftSpec): List<TaperCalcChoice> {
    val existing = spec.tapers.sortedWith(compareBy<Taper>({ it.startFromAftMm }, { it.id }))
    val taken = existing.map { classifyTaperSideByMidpoint(it, spec.overallLengthMm) }.toSet()
    val ends = buildList {
        if (TaperSide.AFT !in taken) add(TaperSide.AFT)
        if (TaperSide.FWD !in taken && spec.overallLengthMm > 0f) add(TaperSide.FWD)
    }
    return existing.map { TaperCalcChoice.Existing(it.id) } + ends.map { TaperCalcChoice.NewAtEnd(it) }
}

/**
 * The row the confirmation opens on: the taper selected in the editor, else the shaft's only
 * taper, else the only option there is. Two tapers or two free ends and no selection open on
 * nothing, because a guess here writes into the wrong taper.
 */
internal fun defaultTaperCalcChoice(
    choices: List<TaperCalcChoice>,
    selectedComponentId: String?,
): TaperCalcChoice? {
    val existing = choices.filterIsInstance<TaperCalcChoice.Existing>()
    return existing.firstOrNull { it.taperId == selectedComponentId }
        ?: existing.singleOrNull()
        ?: choices.singleOrNull()
}

/**
 * Where a new taper at [side] goes: flush with that end, or the free span nearest it when the end
 * is taken (a thread on it, say). A span that lands in the other half of the shaft is not at that
 * end, and would also swap which diameter is the small end, so the flush start stands and the
 * overlap shows in the confirmation instead.
 */
internal fun taperCalcNewStartMm(spec: ShaftSpec, lengthMm: Float, side: TaperSide): Float {
    val oal = spec.overallLengthMm
    val flush = if (side == TaperSide.FWD) oal - lengthMm else 0f
    val seeded = when (side) {
        TaperSide.FWD -> addStartSeed(spec, lengthMm, fromFwd = true).startMm
        TaperSide.AFT ->
            if (spec.collisionPartnerIdsForSpan(flush, lengthMm).isEmpty()) {
                flush
            } else {
                firstFreeStartMm(spec, lengthMm) ?: flush
            }
    }
    return if (classifyTaperSideByMidpoint(seeded, lengthMm, oal) == side) seeded else flush
}

/**
 * The new taper Apply would add at [side]. It takes the Auto rate mode, the Add dialog's default,
 * and measures from the end it was asked for, so an OAL change keeps it there.
 */
internal fun newTaperFromCalc(spec: ShaftSpec, values: TaperCalcValues, side: TaperSide): Taper {
    val start = taperCalcNewStartMm(spec, values.lengthMm, side)
    val (startDia, endDia) = taperAddDiameterOrder(
        setDiaMm = values.smallDiaMm,
        letDiaMm = values.largeDiaMm,
        side = classifyTaperSideByMidpoint(start, values.lengthMm, spec.overallLengthMm),
    )
    return Taper(
        startFromAftMm = start,
        lengthMm = values.lengthMm,
        startDiaMm = startDia,
        endDiaMm = endDia,
        taperRateText = taperCalcAutoRateText(values.lengthMm, values.smallDiaMm, values.largeDiaMm).orEmpty(),
        authoredReference = if (side == TaperSide.FWD) LinerAuthoredReference.FWD else LinerAuthoredReference.AFT,
        taperRateManual = false,
    )
}

/** One line of the confirmation; [before] is `null` for a taper that does not exist yet. */
internal data class TaperCalcChangeRow(val label: String, val before: String?, val after: String)

/** What the confirmation shows for one choice. */
internal data class TaperCalcApplyPlan(
    val rows: List<TaperCalcChangeRow>,
    val note: String,
    val warnings: List<String>,
)

/**
 * The confirmation's contents for [choice], or `null` when that taper is gone. Values read in the
 * calculator's entry unit, the one the user has just been reading them in; warnings are the card's
 * own, read off the spec as it would stand after the write, in the document unit. They inform and
 * never block, the posture of the Add dialogs and the card.
 */
internal fun planTaperCalcApply(
    spec: ShaftSpec,
    choice: TaperCalcChoice,
    values: TaperCalcValues,
    entryUnit: UnitSystem,
    documentUnit: UnitSystem,
): TaperCalcApplyPlan? {
    val before = (choice as? TaperCalcChoice.Existing)?.let { c ->
        spec.tapers.firstOrNull { it.id == c.taperId } ?: return null
    }
    val after = when (choice) {
        is TaperCalcChoice.Existing -> requireNotNull(before).withTaperCalcValues(values, spec.overallLengthMm)
        is TaperCalcChoice.NewAtEnd -> newTaperFromCalc(spec, values, choice.side)
    }
    val afterSpec = spec.copy(tapers = spec.tapers.filterNot { it.id == after.id } + after)

    val warnings = buildList {
        addAll(taperWarningMessages(afterSpec, after, documentUnit))
        overlapsMessage(afterSpec, afterSpec.collisionPartnerIds(after.id))?.let { add(it) }
        if (after.hasAnyKeywayValue && after.keywayOffsetFromSetMm + after.keywayLengthMm > after.lengthMm) {
            add("The keyway runs past this length")
        }
    }

    val note = when (choice) {
        is TaperCalcChoice.Existing -> existingTaperNote(requireNotNull(before), after)
        is TaperCalcChoice.NewAtEnd -> newTaperNote(choice.side, after, spec.overallLengthMm, entryUnit)
    }

    fun text(mm: Float) = if (mm > 0f) taperCalcNumberText(mm.toDouble(), entryUnit) else "—"
    val unitName = unitName(entryUnit)
    return TaperCalcApplyPlan(
        rows = listOf(
            TaperCalcChangeRow(
                "Large end Ø ($unitName)",
                before?.let { text(it.maxDiaMm) },
                text(values.largeDiaMm),
            ),
            TaperCalcChangeRow(
                "Small end Ø ($unitName)",
                before?.let { text(min(it.startDiaMm, it.endDiaMm)) },
                text(values.smallDiaMm),
            ),
            TaperCalcChangeRow("Length ($unitName)", before?.let { text(it.lengthMm) }, text(values.lengthMm)),
            TaperCalcChangeRow(
                if (after.taperRateManual == true) "Rate (Manual)" else "Rate (Auto)",
                before?.let { it.taperRateText.ifBlank { "—" } },
                after.taperRateText.ifBlank { "—" },
            ),
        ),
        note = note,
        warnings = warnings,
    )
}

private fun unitName(unit: UnitSystem) = if (unit == UnitSystem.INCHES) "in" else "mm"

private fun existingTaperNote(before: Taper, after: Taper): String {
    val moved = when {
        abs(after.lengthMm - before.lengthMm) < LENGTH_UNCHANGED_EPS_MM -> "Its position does not change"
        before.authoredReference == LinerAuthoredReference.FWD ->
            "Measured from FWD, so its FWD end stays and its AFT end moves"
        else -> "Measured from AFT, so its AFT end stays and its FWD end moves"
    }
    return "$moved. The keyway, name and overall length stay as they are."
}

private fun newTaperNote(side: TaperSide, after: Taper, overallLengthMm: Float, unit: UnitSystem): String {
    val fromEnd = if (side == TaperSide.FWD) {
        overallLengthMm - after.startFromAftMm - after.lengthMm
    } else {
        after.startFromAftMm
    }
    val where = if (abs(fromEnd) < LENGTH_UNCHANGED_EPS_MM) {
        "Goes flush with the ${side.name} end"
    } else {
        "Goes ${taperCalcNumberText(fromEnd.toDouble(), unit)} ${unitName(unit)} from the ${side.name} end, " +
            "the nearest free span"
    }
    return "$where, measured from ${side.name}. The overall length stays as it is."
}

private const val LENGTH_UNCHANGED_EPS_MM = 0.01f
