package com.android.shaftschematic.ui.screen

/**
 * SpecWarningBanner — Schematic tab document-level problem list.
 *
 * Surfaces `specIssues(spec, unit)` (see `ui/util/SpecIssues.kt`) as a dismissable block above
 * the component carousel. It reads as belonging to the document rather than to any one component
 * card, mirroring the warning color already used per-card in `ComponentCarousel.kt`.
 *
 * Each line NAMES its parts ("FWD Liner overlaps Body size transition taper", "Body #1 runs
 * 4 13/16" past the FWD end") and is a button: a tap selects the part exactly as a tap on the
 * preview does, so the carousel swings to its card (an overlap line selects the first part it
 * names). A count ("1 component extends past shaft length") left the user unable to tell which
 * part was wrong (on-device report). More than [VISIBLE_ISSUE_LINES] problems show that many and
 * a "+N more" that opens the rest.
 *
 * The advisory styling is the contract: **only PROBLEMS the user can act on may reach this
 * banner.** A line stating normal behaviour reads as an error here (on-device report) and
 * cheapens the ones that matter — see `specIssues` for the note that was removed on those grounds.
 *
 * Overlaps block export (the same pair the export gate names); every other line reports a legal,
 * already-committed state (`docs/contracts/OverallLength.md`), and nothing here clamps or
 * refuses anything.
 *
 * Dismissal is keyed to the current message set ([bannerVisible]/[warningSetKey]): dismissing
 * hides the banner for that exact set only, and a changed set (a new or different problem)
 * re-shows it. The dismissed key and the "+N more" expansion are plain Compose view state —
 * never written to the document, `EditState`, or undo history.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.util.SpecIssue
import com.android.shaftschematic.ui.util.bannerVisible
import com.android.shaftschematic.ui.util.specIssues
import com.android.shaftschematic.ui.util.warningSetKey
import com.android.shaftschematic.util.UnitSystem

/** Problem lines shown before the "+N more" row. */
internal const val VISIBLE_ISSUE_LINES = 3

/** Test tag of the [index]th visible problem line (0-based). */
internal fun specWarningLineTag(index: Int) = "spec_warning_line_$index"

@Composable
internal fun SpecWarningBanner(
    spec: ShaftSpec,
    unit: UnitSystem,
    /** Selects a part by stored id — the preview's tap path (`onSelectComponentById`). */
    onSelectComponent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the WHOLE spec, not the component lists: the past-end lines also depend on
    // `overallLengthMm`, so a list-only key left the banner stale when the OAL alone moved.
    val issues = remember(spec, unit) { specIssues(spec, unit) }
    val messages = remember(issues) { issues.map { it.message } }
    var dismissedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (!bannerVisible(messages, dismissedKey)) return

    val shown = if (expanded) issues else issues.take(VISIBLE_ISSUE_LINES)
    val hidden = issues.size - shown.size
    val ink = MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .testTag("spec_warning_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                shown.forEachIndexed { index, issue ->
                    IssueLine(
                        issue = issue,
                        onClick = { issue.componentIds.firstOrNull()?.let(onSelectComponent) },
                        modifier = Modifier.testTag(specWarningLineTag(index)),
                    )
                }
                if (hidden > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClickLabel = "Show all") { expanded = true }
                            .padding(horizontal = 8.dp)
                            .testTag("spec_warning_more"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "+$hidden more",
                            style = MaterialTheme.typography.labelMedium,
                            color = ink,
                        )
                    }
                }
            }
            IconButton(
                onClick = { dismissedKey = warningSetKey(messages) },
                modifier = Modifier.testTag("spec_warning_dismiss"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss warning",
                    tint = ink,
                )
            }
        }
    }
}

/** One tappable problem line: the message, then a "Show" chevron. 48 dp tall at least. */
@Composable
private fun IssueLine(issue: SpecIssue, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Show", onClick = onClick)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = issue.message,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        Text(text = "Show", style = MaterialTheme.typography.labelSmall, color = ink)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(18.dp),
        )
    }
}
