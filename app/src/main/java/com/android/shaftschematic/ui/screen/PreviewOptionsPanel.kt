package com.android.shaftschematic.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PreviewOptionsPanel — the PDF options controls of an open preview, as a NON-modal panel in
 * the preview's own layout.
 *
 * Both preview surfaces host it — the schematic / Final `PdfPreviewScreen` and the shared
 * `PdfPreviewOverlay` behind the Output, Runout, Wear and Undercut tabs — placed at
 * `Alignment.BottomCenter` of the preview Box and composed AFTER the page, so it sits above the
 * page in z-order. Being in-layout rather than a separate window is the point: the page above
 * it keeps receiving pinches and drags while the panel is up, so a slider's effect can be
 * zoomed in on and watched (on-device request: zoom on the threads while moving the thread
 * sliders — a modal sheet took every touch outside it as a dismiss). There is no scrim and no
 * tap-outside dismissal; the panel closes by its close button or by Back.
 *
 * Layout contract with `PreviewTuning.kt`: the header row is held to exactly
 * [TUNING_SHEET_CHROME_DP], and the content slot below it is capped at [maxHeightDp] — the
 * panel is the ONE owner of that cap. The hosts budget the page strip from the same two
 * numbers, so the panel can never grow over the strip.
 *
 * Compose chrome, not sheet ink: the panel takes theme colors.
 *
 * @param onClose     Closes the panel — the close button and Back both call it.
 * @param maxHeightDp Cap for the content slot (the header sits outside it).
 * @param content     The options sheet column; it scrolls internally when taller than the cap.
 */
/**
 * The small corner indicator both preview hosts show while a page re-renders BEHIND a page
 * already on screen — a sharp pass after a commit-on-release control. The page itself stays
 * put (and so does the zoom on it); only this says a new one is coming.
 */
@Composable
internal fun PreviewRerenderIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier
            .padding(12.dp)
            .size(20.dp)
            .testTag("preview_rerender_indicator"),
        strokeWidth = 2.dp,
    )
}

@Composable
internal fun PreviewOptionsPanel(
    onClose: () -> Unit,
    maxHeightDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Registered after the host's own Back handling — navigation for the schematic preview,
    // the route's preview-closing handler for the overlay, both composed before this panel —
    // so while the panel is open Back closes the panel first and leaves the preview up.
    BackHandler { onClose() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preview_options_panel")
            // An empty pointer-input node makes the panel a hit-test target across its whole
            // area. Compose dispatches a touch only along the topmost hit path, and a subtree
            // with no pointer-input node at the touch point is skipped — so without this, a
            // finger landing on an empty part of the panel (padding, a caption) would fall
            // through to the page canvas underneath and pan or zoom it.
            .pointerInput(Unit) {},
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Exactly the chrome the strip math reserves outside the content cap.
                    .height(TUNING_SHEET_CHROME_DP.dp)
                    .padding(start = 24.dp, end = 4.dp)
                    .testTag("preview_options_header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PDF options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("preview_options_close"),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close options")
                }
            }
            Box(Modifier.heightIn(max = maxHeightDp)) { content() }
        }
    }
}
