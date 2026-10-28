package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.THREAD_HATCH_FALLBACK_PITCH_MM
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.suggestedThreadReliefAtAft
import com.android.shaftschematic.ui.config.AddDefaultsConfig
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.toMmOrNull

// ─────────────────────────────────────────────────────────────────────────────
// Thread relief — the controls the thread card and `AddThreadDialog` share (add-dialog parity):
// the "Thread relief" switch, Relief length + Relief Ø shown only while it is on, and the
// "Relief at: AFT | FWD" chips. A thread has a relief exactly when its relief length and Ø are
// both > 0 — there is no stored flag, so the switch is derived from the stored length on the
// card and held as local state in the dialog (the captured-keyway posture).
// ─────────────────────────────────────────────────────────────────────────────

/** A relief length + Ø pair, in mm. */
internal data class ThreadReliefValues(val lenMm: Float, val diaMm: Float)

/** The pitch a relief seed reads: the thread's own, or the hatch's fallback when it has none. */
private fun reliefSeedPitchMm(pitchMm: Float): Float =
    if (pitchMm > 0f) pitchMm else THREAD_HATCH_FALLBACK_PITCH_MM

/**
 * Starting relief length for a thread of [pitchMm]:
 * [AddDefaultsConfig.THREAD_RELIEF_LEN_PITCHES] pitches (4 TPI → 0.375 in). A pitch ≤ 0 seeds from
 * the hatch's [THREAD_HATCH_FALLBACK_PITCH_MM]. A starting value the user types over.
 */
internal fun defaultThreadReliefLenMm(pitchMm: Float): Float =
    AddDefaultsConfig.THREAD_RELIEF_LEN_PITCHES * reliefSeedPitchMm(pitchMm)

/**
 * Starting relief Ø for a thread of [majorDiaMm] at [pitchMm]: the major Ø less
 * [AddDefaultsConfig.THREAD_RELIEF_DIA_BELOW_MAJOR_PITCHES] pitches — just below a 60° thread's
 * root. 0 when the thread has no major Ø yet (nothing to seed from; the user types one).
 */
internal fun defaultThreadReliefDiaMm(majorDiaMm: Float, pitchMm: Float): Float {
    if (!(majorDiaMm > 0f)) return 0f
    return (majorDiaMm - AddDefaultsConfig.THREAD_RELIEF_DIA_BELOW_MAJOR_PITCHES * reliefSeedPitchMm(pitchMm))
        .coerceAtLeast(0f)
}

/**
 * The relief values a "Thread relief" switch should commit. OFF clears both to 0 — that is what
 * "no relief" means. ON keeps each value already stored ([currentLenMm]/[currentDiaMm]), else
 * restores the one remembered from this card session ([rememberedLenMm]/[rememberedDiaMm], the
 * values the last OFF cleared or the last typed), else seeds the preset
 * ([defaultThreadReliefLenMm]/[defaultThreadReliefDiaMm]). The golden rule holds: the only writer
 * of these values is the user's own switch or typing.
 */
internal fun threadReliefForSwitch(
    on: Boolean,
    currentLenMm: Float,
    currentDiaMm: Float,
    rememberedLenMm: Float,
    rememberedDiaMm: Float,
    majorDiaMm: Float,
    pitchMm: Float,
): ThreadReliefValues {
    if (!on) return ThreadReliefValues(0f, 0f)
    val len = currentLenMm.takeIf { it > 0f }
        ?: rememberedLenMm.takeIf { it > 0f }
        ?: defaultThreadReliefLenMm(pitchMm)
    val dia = currentDiaMm.takeIf { it > 0f }
        ?: rememberedDiaMm.takeIf { it > 0f }
        ?: defaultThreadReliefDiaMm(majorDiaMm, pitchMm)
    return ThreadReliefValues(len, dia)
}

/**
 * The relief `AddThreadDialog` submits: 0/0 while its switch is OFF, whatever the hidden fields
 * hold; while ON, the parsed field values ([lenMm]/[diaMm], null when blank or unparseable)
 * verbatim, with a missing or negative value submitting 0.
 */
internal fun threadReliefSubmitValues(on: Boolean, lenMm: Float?, diaMm: Float?): ThreadReliefValues =
    if (!on) ThreadReliefValues(0f, 0f)
    else ThreadReliefValues((lenMm ?: 0f).coerceAtLeast(0f), (diaMm ?: 0f).coerceAtLeast(0f))

/**
 * The "Thread relief" switch row — whole-row toggle, 48 dp, the switch itself not separately
 * clickable (the captured-keyway row's construction). ONE row behind the card and the dialog.
 */
@Composable
internal fun ThreadReliefSwitchRow(
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag(testTag)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Thread relief", modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * One "Relief at" chip: its label, whether it is selected, the modifier carrying its test tag,
 * and its click. Lets each host draw the row in its own surface's chip style.
 */
internal typealias ReliefEndChip =
    @Composable (label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) -> Unit

/**
 * The "Relief at: AFT | FWD" chip row. The chips carry `<testTag>_aft` / `<testTag>_fwd`.
 * [chip] draws each chip; null draws the thread card's "Thread end" style. `AddThreadDialog`
 * passes its own chip so the row matches the dialog's other chip rows — the two styles side by
 * side in one dialog read as two kinds of control (on-device report).
 */
@Composable
internal fun ThreadReliefEndChips(
    atAft: Boolean,
    testTag: String,
    chip: ReliefEndChip? = null,
    onSelect: (atAft: Boolean) -> Unit,
) {
    ChipFlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag(testTag)) {
        Text("Relief at:", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val aftModifier = Modifier.testTag("${testTag}_aft")
        val fwdModifier = Modifier.testTag("${testTag}_fwd")
        if (chip != null) {
            chip("AFT", atAft, aftModifier) { onSelect(true) }
            chip("FWD", !atAft, fwdModifier) { onSelect(false) }
        } else {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.Black, selectedLabelColor = Color.White,
                containerColor = Color.Transparent, labelColor = MaterialTheme.colorScheme.onSurface
            )
            FilterChip(selected = atAft,
                onClick = { onSelect(true) },
                label = { ChipLabel("AFT") }, colors = chipColors,
                border = if (atAft) BorderStroke(1.dp, Color.Black) else null,
                modifier = aftModifier)
            FilterChip(selected = !atAft,
                onClick = { onSelect(false) },
                label = { ChipLabel("FWD") }, colors = chipColors,
                border = if (!atAft) BorderStroke(1.dp, Color.Black) else null,
                modifier = fwdModifier)
        }
    }
}

/**
 * The thread card's relief block: the switch (tag `thread_relief`), and while it is on the
 * Relief length / Relief Ø fields and the "Relief at" chips (tag `thread_relief_end`). Values are
 * typed in the document [unit] (entry rule). Every commit goes through [onSetThreadRelief] with
 * the thread's id.
 *
 * The switch is derived from the stored relief length, so stored values hold it open. Switching
 * ON seeds the values and the end ([suggestedThreadReliefAtAft]) only when nothing is stored or
 * remembered; switching OFF commits 0/0 and remembers the values for this card session so
 * switching back on restores them.
 */
@Composable
internal fun ThreadReliefCardSection(
    th: Threads,
    unit: UnitSystem,
    overallLengthMm: Float,
    onSetThreadRelief: (id: String, reliefLenMm: Float, reliefDiaMm: Float, reliefAtAft: Boolean) -> Unit,
) {
    val reliefOn = th.reliefLenMm > 0f
    var rememberedLenMm by remember(th.id) { mutableStateOf(0f) }
    var rememberedDiaMm by remember(th.id) { mutableStateOf(0f) }

    ThreadReliefSwitchRow(checked = reliefOn, testTag = "thread_relief") { on ->
        if (!on) {
            if (th.reliefLenMm > 0f) rememberedLenMm = th.reliefLenMm
            if (th.reliefDiaMm > 0f) rememberedDiaMm = th.reliefDiaMm
        }
        val v = threadReliefForSwitch(
            on, th.reliefLenMm, th.reliefDiaMm, rememberedLenMm, rememberedDiaMm, th.majorDiaMm, th.pitchMm,
        )
        // A first switch-on seeds the end; a restored relief keeps the end it was authored at.
        val atAft = if (on && rememberedLenMm <= 0f) {
            suggestedThreadReliefAtAft(th.startFromAftMm, th.lengthMm, th.excludeFromOAL, th.isAftEnd, overallLengthMm)
        } else th.reliefAtAft
        onSetThreadRelief(th.id, v.lenMm, v.diaMm, atAft)
    }
    if (reliefOn) {
        // A blank or ≤ 0 value is not a way to remove the relief: the switch is the one control
        // that clears it, so such a commit is a no-op.
        CommitNum("Relief length (${abbr(unit)})", disp(th.reliefLenMm, unit)) { s ->
            if (s.isBlank()) return@CommitNum
            val v = toMmOrNull(s, unit) ?: return@CommitNum
            if (v <= 0f) return@CommitNum
            rememberedLenMm = v
            onSetThreadRelief(th.id, v, th.reliefDiaMm, th.reliefAtAft)
        }
        CommitNum("Relief Ø (${abbr(unit)})", disp(th.reliefDiaMm, unit)) { s ->
            if (s.isBlank()) return@CommitNum
            val v = toMmOrNull(s, unit) ?: return@CommitNum
            if (v <= 0f) return@CommitNum
            rememberedDiaMm = v
            onSetThreadRelief(th.id, th.reliefLenMm, v, th.reliefAtAft)
        }
        ThreadReliefEndChips(atAft = th.reliefAtAft, testTag = "thread_relief_end") { aft ->
            onSetThreadRelief(th.id, th.reliefLenMm, th.reliefDiaMm, aft)
        }
    }
}
