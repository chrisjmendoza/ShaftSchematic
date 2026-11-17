package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftEnd
import com.android.shaftschematic.model.ShaftEndHoles
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ThreadSeries
import com.android.shaftschematic.model.endHoles
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.isUnitlessNumberText
import com.android.shaftschematic.util.parseFractionOrDecimal
import com.android.shaftschematic.util.toMmOrNull

/**
 * The start card's "Shaft ends" section: the tapped holes in each end face — the draw-plate bolt
 * pattern ([ShaftEndHoles], on-device request; puller holes are in the propeller, so there are
 * none here). They belong to the shaft END, not to the taper or body that sits there, which is why
 * they live on the start card. Each end has a "Draw plate holes" checkbox that opens its fields; a
 * record holding values keeps its box ticked, and unticking clears them (one undo step).
 *
 * Every value commits on its own like the start card's other fields and is stored verbatim:
 * the thread size as typed text with its NC | NF series on the toggle at the end of its box (default
 * NC), the bolt circle in the document unit. No depth — the shop records
 * a hole's size, count and bolt circle only. A blank field clears its value ("not given"). They
 * print only as lines in that end's footer column.
 */
@Composable
internal fun ShaftEndsSection(
    spec: ShaftSpec,
    unit: UnitSystem,
    onSetEndHoles: (ShaftEnd, ShaftEndHoles) -> Unit,
) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(SHAFT_ENDS_TITLE, style = MaterialTheme.typography.titleSmall)
    Text(
        SHAFT_ENDS_CAPTION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ShaftEnd.values().forEach { end ->
        EndHolesBlock(end, spec.endHoles(end), unit) { onSetEndHoles(end, it) }
    }
}

@Composable
private fun EndHolesBlock(
    end: ShaftEnd,
    holes: ShaftEndHoles,
    unit: UnitSystem,
    onChange: (ShaftEndHoles) -> Unit,
) {
    val tag = end.name.lowercase()
    Text(
        if (end == ShaftEnd.AFT) "AFT end" else "FWD end",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 4.dp),
    )

    // Stored values always hold a section open; a tick alone opens it empty.
    var drawPlateTick by remember(end) { mutableStateOf(false) }
    val drawPlateOpen = drawPlateTick || holes.hasDrawPlate
    EndHoleGateRow(DRAW_PLATE_GATE_LABEL, drawPlateOpen, "end_${tag}_draw_plate") { on ->
        drawPlateTick = on
        if (!on && holes.hasDrawPlate) onChange(holes.withoutDrawPlate())
    }
    if (drawPlateOpen) {
        FieldPair(
            first = {
                CommitNum(
                    label = "Holes",
                    initialDisplay = if (holes.drawPlateCount > 0) holes.drawPlateCount.toString() else "",
                    modifier = Modifier.testTag("end_${tag}_draw_plate_count"),
                    parseValid = { it.isBlank() || isUnitlessNumberText(it) },
                    fractionKey = false,
                    validator = ::endHoleCountError,
                ) { s -> endHoleCountOrNull(s)?.let { onChange(holes.copy(drawPlateCount = it)) } }
            },
            second = {
                CommitTextField(
                    label = "Thread size",
                    initialText = holes.drawPlateSize,
                    onCommit = { onChange(holes.copy(drawPlateSize = it)) },
                    modifier = Modifier.testTag("end_${tag}_draw_plate_size"),
                    trailing = {
                        ThreadSeriesToggle(holes.drawPlateSeries, "end_${tag}_draw_plate_series") {
                            onChange(holes.copy(drawPlateSeries = it))
                        }
                    },
                )
            },
        )
        LengthField("Bolt circle Ø (${abbr(unit)})", holes.drawPlateBoltCircleMm, unit, "end_${tag}_draw_plate_bc") {
            onChange(holes.copy(drawPlateBoltCircleMm = it))
        }
    }
}

/**
 * The NC | NF selector at the end of the thread-size box: BOTH options side by side, the chosen one
 * filled and the other outlined, so it reads as a choice (on-device report: a lone "NC" label did
 * not look tappable). A tap on either selects it and commits at once (one undo step). The sheet
 * prints the size with this series after it.
 */
@Composable
private fun ThreadSeriesToggle(series: ThreadSeries, tag: String, onChange: (ThreadSeries) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .testTag(tag),
    ) {
        ThreadSeries.values().forEach { option ->
            val selected = option == series
            Box(
                modifier = Modifier
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        if (!selected) onChange(option)
                    }
                    .heightIn(min = 36.dp)
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 10.dp)
                    .testTag("${tag}_${option.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A length in the document unit; 0 shows an empty box, and a cleared box commits 0 ("not given"). */
@Composable
private fun LengthField(label: String, mm: Float, unit: UnitSystem, tag: String, onCommit: (Float) -> Unit) {
    CommitNum(
        label = label,
        initialDisplay = if (mm > 0f) formatDisplay(mm, unit) else "",
        modifier = Modifier.testTag(tag),
        parseValid = { it.isBlank() || isLengthText(it) },
    ) { t ->
        val v = if (t.isBlank()) 0f else toMmOrNull(t, unit) ?: return@CommitNum
        onCommit(v)
    }
}

@Composable
private fun EndHoleGateRow(label: String, checked: Boolean, testTag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** A draw-plate hole count: blank (not given) or a whole number ≥ 0. */
internal fun endHoleCountOrNull(raw: String): Int? {
    if (raw.isBlank()) return 0
    val v = parseFractionOrDecimal(raw) ?: return null
    if (v < 0.0 || v != kotlin.math.floor(v) || v > 999.0) return null
    return v.toInt()
}

/** Why a count cannot be stored, or null when it can. */
internal fun endHoleCountError(raw: String): String? =
    if (endHoleCountOrNull(raw) == null) "Enter a whole number of holes" else null

internal const val SHAFT_ENDS_TITLE = "Shaft ends"
internal const val SHAFT_ENDS_CAPTION =
    "Tapped holes in the end faces. They print in that end's footer column."
internal const val DRAW_PLATE_GATE_LABEL = "Draw plate holes"
