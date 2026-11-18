package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Which whole-shaft sheet the Final preview shows, as ONE choice.
 *
 * It is a reading of the two session elections `finalFullLength` and `finalBeforeAfter`, never a
 * third piece of state: Full length wins over Original + Final (`finalSheetKind`), so the pair
 * names exactly these three sheets.
 */
internal enum class FinalSheetChoice(val label: String, val tag: String) {
    FULL_LENGTH("Full length + schematic", "final_sheet_choice_full_length"),
    BEFORE_AFTER("Original + Final", "final_sheet_choice_before_after"),
    FINAL_ONLY("Final only", "final_sheet_choice_final_only"),
}

/** The choice the two elections add up to. */
internal fun finalSheetChoice(finalFullLength: Boolean, finalBeforeAfter: Boolean): FinalSheetChoice = when {
    finalFullLength -> FinalSheetChoice.FULL_LENGTH
    finalBeforeAfter -> FinalSheetChoice.BEFORE_AFTER
    else -> FinalSheetChoice.FINAL_ONLY
}

/**
 * The two elections that select [choice], as (finalFullLength, finalBeforeAfter). Both are
 * written on every choice: "Final only" has to turn Original + Final off as well as Full length,
 * or the before/after sheet would come up in place of the one asked for.
 */
internal fun finalSheetElections(choice: FinalSheetChoice): Pair<Boolean, Boolean> = when (choice) {
    FinalSheetChoice.FULL_LENGTH -> true to true
    FinalSheetChoice.BEFORE_AFTER -> false to true
    FinalSheetChoice.FINAL_ONLY -> false to false
}

/**
 * The Final preview's sheet choice, in plain view above the page (on-device report: the Full
 * length sheet sat behind the options panel and was not found). Disabled while runout bubbles
 * are elected — that sheet replaces all three.
 */
@Composable
internal fun FinalSheetChoiceRow(
    choice: FinalSheetChoice,
    enabled: Boolean,
    onChoose: (FinalSheetChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .testTag("final_sheet_choice_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinalSheetChoice.entries.forEach { c ->
            val selected = enabled && c == choice
            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = { onChoose(c) },
                label = { Text(c.label, maxLines = 1) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                modifier = Modifier.testTag(c.tag),
            )
        }
    }
}
