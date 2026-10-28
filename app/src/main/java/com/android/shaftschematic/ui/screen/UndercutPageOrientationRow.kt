package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.util.PdfPageOrientation

/**
 * The undercut sheet's per-job "Page" control — Auto | Landscape | Portrait, writing
 * [com.android.shaftschematic.model.UndercutRecord.pageOrientation]. ONE composable behind the
 * tab's Drawing settings and the undercut options panel, so the two read alike.
 *
 * [stripCount] and [resolved] are what the print path computes (`undercutSheetStripCount` /
 * `undercutSheetOrientation`, blank draft included), so the caption names the page that will
 * actually print. It is a per-job control, so its caption ends "Saved with this job." — the
 * marker every per-job control carries on a sheet whose other rows are app-wide.
 */
@Composable
internal fun UndercutPageOrientationRow(
    choice: UndercutPageOrientation,
    stripCount: Int,
    resolved: PdfPageOrientation,
    onChoose: (UndercutPageOrientation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Page", style = MaterialTheme.typography.bodyLarge)
        ChipFlowRow {
            UndercutPageOrientation.entries.forEach { o ->
                WearChip(
                    label = undercutPageOrientationLabel(o),
                    selected = choice == o,
                    modifier = Modifier.testTag(undercutPageOrientationTestTag(o)),
                ) { onChoose(o) }
            }
        }
        Text(
            undercutPageOrientationCaption(choice, stripCount, resolved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("undercut_page_caption"),
        )
    }
}

/** The chip label for [o]. */
internal fun undercutPageOrientationLabel(o: UndercutPageOrientation): String = when (o) {
    UndercutPageOrientation.AUTO -> "Auto"
    UndercutPageOrientation.LANDSCAPE -> "Landscape"
    UndercutPageOrientation.PORTRAIT -> "Portrait"
}

/** The chip's test tag for [o]: `undercut_page_auto` / `_landscape` / `_portrait`. */
internal fun undercutPageOrientationTestTag(o: UndercutPageOrientation): String =
    "undercut_page_" + o.name.lowercase()

/**
 * The caption under the "Page" chips. AUTO explains the rule and names the page this sheet prints
 * with its section count; a forced shape needs only the per-job marker.
 */
internal fun undercutPageOrientationCaption(
    choice: UndercutPageOrientation,
    stripCount: Int,
    resolved: PdfPageOrientation,
): String {
    if (choice != UndercutPageOrientation.AUTO) return "Saved with this job."
    val page = if (resolved == PdfPageOrientation.PORTRAIT) "portrait" else "landscape"
    val sections = if (stripCount == 1) "1 section" else "$stripCount sections"
    return "Auto: one section prints landscape; two or more stack on a portrait page — " +
        "this sheet prints $page ($sections). Saved with this job."
}
