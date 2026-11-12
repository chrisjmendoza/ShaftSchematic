// file: app/src/main/java/com/android/shaftschematic/ui/screen/TaperRateEntryChips.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.TaperRateEntry
import com.android.shaftschematic.ui.theme.strongChipContainer
import com.android.shaftschematic.ui.theme.strongChipLabel

/**
 * `PdfPrefs.taperRatePerFoot` as the rate fields see it — where their "Rate as" chips open when a
 * taper has no typed notation of its own. The app root provides the live pref; the default
 * (`false`, the ratio) is what a field outside that provider — a test — gets, so a test host keeps
 * the fields it always had.
 */
val LocalTaperRatePerFoot = compositionLocalOf { false }

/** Label of a rate field read under [entry] — the ratio one is the label the field always had. */
internal fun taperRateFieldLabel(entry: TaperRateEntry): String = when (entry) {
    TaperRateEntry.RATIO -> "Rate (1:12, 3/4\"/ft, or decimal)"
    TaperRateEntry.PER_FOOT -> "Rate, inches per foot (3 3/4)"
}

/** Label of the taper calculator's rate field, a half-width box beside Length. */
internal fun taperCalcRateFieldLabel(entry: TaperRateEntry): String = when (entry) {
    TaperRateEntry.RATIO -> "Taper rate"
    // Short enough for the half-width box on a phone; "Taper rate (in/ft)" wrapped there.
    TaperRateEntry.PER_FOOT -> "Rate (in/ft)"
}

private val RATE_ENTRY_OPTIONS = listOf(TaperRateEntry.RATIO to "1:N", TaperRateEntry.PER_FOOT to "in/ft")

/**
 * "Rate as: 1:N | in/ft" — how a bare number typed in the rate field is read
 * (`util/TaperRateNotation.kt`). ONE row behind the taper card, `AddTaperDialog` and the taper
 * calculator, shown only on an inch drawing (per foot is inch notation). A spelling that names
 * its notation (`1:12`, `3/4"/ft`) reads the same under either chip, and switching never
 * rewrites the field's text.
 *
 * [labelAbove] draws the row in the calculators' chip look ([ChipRow]: label above, outlined
 * equal-width chips), so it matches the calculator's Units row directly above it; the card and
 * the Add dialog keep the inline "Rate as:" row that matches their other chip rows.
 */
@Composable
internal fun TaperRateEntryChips(
    entry: TaperRateEntry,
    onSelect: (TaperRateEntry) -> Unit,
    tagPrefix: String,
    labelAbove: Boolean = false,
) {
    fun tagOf(value: TaperRateEntry) = "${tagPrefix}_rate_as_${value.name.lowercase()}"
    if (labelAbove) {
        ChipRow(
            label = "Rate as",
            options = RATE_ENTRY_OPTIONS.map { it.first },
            selected = entry,
            labelOf = { v -> RATE_ENTRY_OPTIONS.first { it.first == v }.second },
            tagOf = { tagOf(it) },
            onSelect = onSelect,
        )
        return
    }
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = strongChipContainer(),
        selectedLabelColor = strongChipLabel(),
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
    )
    ChipFlowRow(Modifier.fillMaxWidth()) {
        Text(
            "Rate as:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RATE_ENTRY_OPTIONS.forEach { (value, label) ->
            val selected = entry == value
            FilterChip(
                selected = selected,
                onClick = { onSelect(value) },
                label = { ChipLabel(label) },
                colors = colors,
                border = BorderStroke(1.dp, if (selected) strongChipContainer() else MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag(tagOf(value)),
            )
        }
    }
}
