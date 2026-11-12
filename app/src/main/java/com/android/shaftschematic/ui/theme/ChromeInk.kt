package com.android.shaftschematic.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.isDarkPreviewGround

/**
 * Chrome inks that a light ground draws as a fixed colour and a dark ground must not.
 *
 * Each helper returns EXACTLY the historical fixed colour on a light ground, so the default Light
 * theme (and high-contrast light) draws what it always did; on a dark ground — the dark scheme
 * and high-contrast dark alike, keyed on the scheme's own `surface` like the preview presets — it
 * returns the role that plays the same part there. App chrome only: nothing here may be used for
 * a white sheet canvas, which draws `SheetInk` in every theme.
 */

/** Whether the current scheme's ground is dark. */
@Composable
@ReadOnlyComposable
fun isDarkChrome(): Boolean = MaterialTheme.colorScheme.isDarkPreviewGround()

/**
 * The selected container (and border) of the cards' high-contrast chips — "Measure From",
 * "Rate mode", "Thread end", the unit chips. Black on a light ground; on a dark one the
 * foreground, so the selected chip is the brightest thing in its row as black is on white — a
 * black chip on a dark card read as a hole, the selection inverted.
 */
@Composable
@ReadOnlyComposable
fun strongChipContainer(): Color =
    if (isDarkChrome()) MaterialTheme.colorScheme.onSurface else Color.Black

/** The label on [strongChipContainer]. */
@Composable
@ReadOnlyComposable
fun strongChipLabel(): Color =
    if (isDarkChrome()) MaterialTheme.colorScheme.surface else Color.White

/** The ✓ that accepts an edit — and the undercut pill's "Recorded" tick. */
@Composable
@ReadOnlyComposable
fun acceptGreen(): Color = if (isDarkChrome()) AcceptGreenDark else AcceptGreenLight

/** Semantic "accept" green on a light ground. */
val AcceptGreenLight = Color(0xFF2E7D32)

/**
 * A colour swatch's ring: none on a light ground; on a dark one a 1 dp outline, so a swatch that
 * is itself dark — Transparent's placeholder, the undercut "Grey" shade's black — keeps its edge.
 */
fun Modifier.swatchRing(): Modifier = composed {
    if (isDarkChrome()) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier
}
