// file: app/src/main/java/com/android/shaftschematic/ui/screen/SheetPaneLayout.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.adaptive.WindowWidthClass

/**
 * The shared window-width skeleton behind the three sheet tabs (Runout, Wear, Undercut).
 *
 * Each of those tabs is a drawing plus the controls that shape it. The content is identical at
 * every width — ONE set of composables, called from both branches — and only the skeleton
 * changes: a phone stacks canvas over controls, a tablet stands them side by side. Duplicating
 * the blocks per branch is what lets a phone and a tablet drift, so nothing here renders
 * content of its own.
 *
 * See `docs/contracts/Adaptive.md`.
 */

/** The historical phone strip: a shaft drawn across a 200 dp band. */
private val SHEET_CANVAS_COMPACT_HEIGHT = 200.dp

/**
 * Canvas height on a MEDIUM window. A 600–840 dp column draws the shaft nearly twice as wide
 * as a phone's, and at the phone's height the profile flattens into a rule.
 */
private val SHEET_CANVAS_MEDIUM_HEIGHT = 280.dp

/** Tallest the canvas grows in a two-pane layout — past this it crowds out the pane's controls. */
private val SHEET_CANVAS_PANE_MAX_HEIGHT = 420.dp

/**
 * Share of the WINDOW height the pinned canvas may take, as a second cap under
 * [SHEET_CANVAS_PANE_MAX_HEIGHT]. A very wide, short window (a resized desktop or Chromebook
 * frame) solves the aspect ratio to the flat cap and would leave the pane's scrolling half
 * nothing at all — the print buttons under the drawing would be unreachable.
 */
private const val SHEET_CANVAS_PANE_MAX_HEIGHT_FRAC = 0.55f

/** Canvas proportion in a two-pane layout: wide enough for a long shaft, tall enough to read. */
private const val SHEET_CANVAS_PANE_ASPECT = 1.6f

/** Two-pane split: the drawing takes the larger share, its controls the rest. */
internal const val SHEET_PANE_CANVAS_WEIGHT = 0.55f
internal const val SHEET_PANE_CONTROLS_WEIGHT = 0.45f

/** Test tag on the left (drawing + output actions) pane of a two-pane sheet tab. */
internal const val SHEET_PANE_CANVAS_TAG = "sheet_pane_canvas"

/** Test tag on the right (controls) pane of a two-pane sheet tab. */
internal const val SHEET_PANE_CONTROLS_TAG = "sheet_pane_controls"

/**
 * The size modifier for a sheet tab's canvas box at [widthClass] — the ONE place the three tabs
 * take their canvas dimensions from, so they can never disagree.
 *
 * COMPACT and MEDIUM give the box a fixed height and the full column width. EXPANDED sizes it by
 * proportion instead: the pane's width is the free variable there, and a fixed height would print
 * the same flat strip a phone gets. `wrapContentWidth` comes first so the aspect solve is free to
 * fall back to the height cap on a wide pane and still centre what it produces.
 */
@Composable
@ReadOnlyComposable
internal fun sheetCanvasModifier(widthClass: WindowWidthClass): Modifier = when (widthClass) {
    WindowWidthClass.COMPACT -> Modifier.fillMaxWidth().height(SHEET_CANVAS_COMPACT_HEIGHT)
    WindowWidthClass.MEDIUM -> Modifier.fillMaxWidth().height(SHEET_CANVAS_MEDIUM_HEIGHT)
    WindowWidthClass.EXPANDED -> Modifier
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .heightIn(
            max = minOf(
                SHEET_CANVAS_PANE_MAX_HEIGHT,
                LocalConfiguration.current.screenHeightDp.dp * SHEET_CANVAS_PANE_MAX_HEIGHT_FRAC,
            ),
        )
        .aspectRatio(SHEET_CANVAS_PANE_ASPECT)
}

/**
 * The EXPANDED skeleton: the drawing and the actions that print it on the left, everything that
 * shapes it on the right, a rule between.
 *
 * [pinnedCanvas] stays OUT of the left pane's scroll — the whole point of the controls opposite
 * it is watching the drawing change, so the drawing may never scroll away. [canvasPaneScrolling]
 * (the tab's explanatory line, the blank-draft switch, the export gate and the action buttons)
 * scrolls under it when the pane is short. The two panes scroll independently.
 */
@Composable
internal fun ColumnScope.SheetTwoPane(
    pinnedCanvas: @Composable ColumnScope.() -> Unit,
    canvasPaneScrolling: @Composable ColumnScope.() -> Unit,
    controlsPane: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(SHEET_PANE_CANVAS_WEIGHT)
                .fillMaxHeight()
                .testTag(SHEET_PANE_CANVAS_TAG),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = pinnedCanvas,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = canvasPaneScrolling,
            )
        }
        VerticalDivider()
        Column(
            modifier = Modifier
                .weight(SHEET_PANE_CONTROLS_WEIGHT)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag(SHEET_PANE_CONTROLS_TAG),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = controlsPane,
        )
    }
}
