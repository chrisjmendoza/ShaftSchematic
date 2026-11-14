package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.SheetDateMode
import com.android.shaftschematic.model.SheetDateSheet
import com.android.shaftschematic.model.sheetPrintsDate
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * The per-job "Date on this sheet" switch — whether ONE sheet prints the job's date (on-device
 * request: the schematic and the consolidated sheet carry the date; the working sheets usually do
 * not). ONE row behind every sheet's PDF options panel and the Undercut tab's Drawing settings.
 * The date itself is chosen on the start card; this row only says whether this sheet prints it.
 *
 * While [enabled] is false (the job's date is None, so no sheet prints one) the switch greys out
 * and keeps its value. [testTag] is the switch's tag — each host keeps its own.
 */
@Composable
internal fun SheetDateOnSheetRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    caption: String,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                SHEET_DATE_ON_SHEET_LABEL,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/**
 * [SheetDateOnSheetRow] bound to the document: [sheet]'s switch, the caption from the job's date
 * choice, greyed while that choice is None. [testTag] defaults to the options panels' tag.
 */
@Composable
internal fun SheetDateOnSheetRow(
    vm: ShaftViewModel,
    sheet: SheetDateSheet,
    modifier: Modifier = Modifier,
    testTag: String = sheetDateOnSheetTag(sheet),
) {
    val mode by vm.sheetDateMode.collectAsState()
    val text by vm.sheetDateText.collectAsState()
    val overrides by vm.sheetDateSheets.collectAsState()
    SheetDateOnSheetRow(
        checked = sheetPrintsDate(sheet, overrides),
        onCheckedChange = { vm.setSheetDateOnSheet(sheet, it) },
        caption = sheetDateOnSheetCaption(mode, text),
        testTag = testTag,
        modifier = modifier,
        enabled = mode != SheetDateMode.NONE,
    )
}

/**
 * The caption under "Date on this sheet", from the job's date choice: what the switch prints, or —
 * while the job's date is None — where that is set. A Custom date not typed yet prints today's,
 * as the sheet does.
 */
internal fun sheetDateOnSheetCaption(mode: SheetDateMode, text: String): String = when {
    mode == SheetDateMode.NONE -> SHEET_DATE_ON_SHEET_NONE_CAPTION
    mode == SheetDateMode.CUSTOM && text.isNotBlank() -> "Prints $text. Saved with this job."
    else -> "Prints today's date. Saved with this job."
}

internal const val SHEET_DATE_ON_SHEET_LABEL = "Date on this sheet"

/** The caption while the job prints no date at all; the switch is greyed and keeps its value. */
internal const val SHEET_DATE_ON_SHEET_NONE_CAPTION =
    "The job's date is set to None on the Shaft & job card."

/** The start card's line under "Date on sheets": the switches exist, and where they start. */
internal const val SHEET_DATE_SHEETS_NOTE =
    "Each sheet's PDF options choose whether it prints the date. " +
        "Off by default on the runout, wear and undercut sheets."

/** The options panels' switch tag: `sheet_date_on_schematic` … `sheet_date_on_final`. */
internal fun sheetDateOnSheetTag(sheet: SheetDateSheet): String =
    "sheet_date_on_${sheet.name.lowercase()}"

/** The same switch on the Undercut tab's Drawing settings. */
internal const val UNDERCUT_DATE_ON_SHEET_TAG = "undercut_date_on_sheet"
