// file: app/src/main/java/com/android/shaftschematic/ui/screen/TaperRateEntryChips.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.TaperRateEntry

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

/**
 * "Rate as: 1:N | in/ft" — how a bare number typed in the rate field is read
 * (`util/TaperRateNotation.kt`). ONE row behind the taper card, `AddTaperDialog` and the taper
 * calculator, shown only on an inch drawing (per foot is inch notation). A spelling that names
 * its notation (`1:12`, `3/4"/ft`) reads the same under either chip, and switching never
 * rewrites the field's text.
 */
@Composable
internal fun TaperRateEntryChips(
    entry: TaperRateEntry,
    onSelect: (TaperRateEntry) -> Unit,
    tagPrefix: String,
) {
    val colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Color.Black,
        selectedLabelColor = Color.White,
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Rate as:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(TaperRateEntry.RATIO to "1:N", TaperRateEntry.PER_FOOT to "in/ft").forEach { (value, label) ->
            val selected = entry == value
            FilterChip(
                selected = selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = colors,
                border = BorderStroke(1.dp, if (selected) Color.Black else MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag("${tagPrefix}_rate_as_${value.name.lowercase()}"),
            )
        }
    }
}
