package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.isLengthText
import com.android.shaftschematic.util.toMmOrNull
import kotlin.math.abs

/**
 * The Ø tolerance row — two fields side by side, the upper (`+`) and lower (`−`) deviation — that
 * sits directly under the Ø field on the liner card, the explicit-body card, `AddLinerDialog` and
 * `AddBodyDialog` (add-dialog parity: it is value entry). The fields are slots because a card
 * commits on blur through `CommitNum` while a dialog holds its text until Add; the layout, the
 * labels and the reading of the text are decided here, once, for all four surfaces.
 *
 * Both values are MAGNITUDES in the document unit, stored verbatim in mm; blank reads as 0 (no
 * tolerance on that side). They print as a stacked tolerance after the Ø callout's value.
 */
@Composable
internal fun DiaToleranceRow(
    plus: @Composable (Modifier) -> Unit,
    minus: @Composable (Modifier) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { plus(Modifier.fillMaxWidth()) }
        Box(Modifier.weight(1f)) { minus(Modifier.fillMaxWidth()) }
    }
}

/** The upper-deviation field's label. */
internal fun diaTolPlusLabel(unit: UnitSystem): String = "Ø tol + (${abbr(unit)})"

/** The lower-deviation field's label. */
internal fun diaTolMinusLabel(unit: UnitSystem): String = "Ø tol − (${abbr(unit)})"

/** A stored deviation as its field shows it: blank for none, else the document-unit value. */
internal fun diaTolFieldText(mm: Float, unit: UnitSystem): String = if (mm > 0f) disp(mm, unit) else ""

/** What a tolerance field accepts: blank (no tolerance on that side) or a length. */
internal fun isDiaTolText(raw: String): Boolean = raw.isBlank() || isLengthText(raw)

/**
 * A tolerance field's text in mm — blank is 0; null when it does not read. The field holds a
 * magnitude, so a sign typed out of habit (`-.002` in the `−` field) reads as the same deviation.
 */
internal fun diaTolMmOrNull(raw: String, unit: UnitSystem): Float? =
    if (raw.isBlank()) 0f else toMmOrNull(raw, unit)?.let { abs(it) }
