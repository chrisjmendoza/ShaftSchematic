package com.android.shaftschematic.ui.screen

/**
 * SpecWarningBanner — Schematic tab document-level advisory line.
 *
 * Surfaces `specWarningMessages(spec)` (see `ui/util/ComponentWarnings.kt`) as a dismissable
 * line above the component carousel. It reads as belonging to the document rather than to any
 * one component card, mirroring the warning color already used per-card in
 * `ComponentCarousel.kt`.
 *
 * The advisory styling is the contract: **only messages describing a PROBLEM the user can act
 * on may reach this banner.** A line stating normal behaviour reads as an error here (on-device
 * report) and cheapens the ones that matter — see `specWarningMessages` for the note that was
 * removed on those grounds.
 *
 * Dismissal is keyed to the current warning set ([bannerVisible]/[warningSetKey]): dismissing
 * hides the banner for that exact set of messages only, and a changed set (a new or different
 * warning) re-shows it. The dismissed key is plain Compose view state — never written to the
 * document, `EditState`, or undo history.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.util.bannerVisible
import com.android.shaftschematic.ui.util.specWarningMessages
import com.android.shaftschematic.ui.util.warningSetKey

@Composable
internal fun SpecWarningBanner(
    spec: ShaftSpec,
    modifier: Modifier = Modifier,
) {
    val messages = remember(spec.bodies, spec.tapers, spec.liners, spec.threads) {
        specWarningMessages(spec)
    }
    var dismissedKey by rememberSaveable { mutableStateOf<String?>(null) }

    if (!bannerVisible(messages, dismissedKey)) return

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
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                messages.forEach { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            IconButton(
                onClick = { dismissedKey = warningSetKey(messages) },
                modifier = Modifier
                    .size(28.dp)
                    .testTag("spec_warning_dismiss"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss warning",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
