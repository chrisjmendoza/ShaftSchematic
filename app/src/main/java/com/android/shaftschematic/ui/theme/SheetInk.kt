package com.android.shaftschematic.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed ink colors for the paper-sheet canvases (the undercut / wear / runout drawing
 * surfaces, which draw on a forced-white sheet in every app theme).
 *
 * Sheet ink must never come from the theme: dark theme's near-white `onSurface` would print
 * invisible ink on the white sheet, and high contrast is a UI-chrome concern — the sheet is
 * already maximum-contrast paper. The values pin the app's historical light-theme rendering
 * so enabling dark or high-contrast themes changes nothing on the sheets.
 *
 * Interactive affordances drawn OVER the sheet (tap targets, selection highlights, draft
 * outlines) stay theme-driven — they are UI, not ink.
 */
object SheetInk {
    /** Profile outlines, dimension rails, and sheet text. */
    val Outline = Color.Black

    /** Liner tint on wear/runout sheets — the historical light-theme tertiary (bronze-mauve). */
    val LinerTint = Color(0xFF7D5260)

    /** Wear marks (tints, hatches, pit X's) — the historical light-theme error red. */
    val WearRed = Color(0xFFB3261E)
}
