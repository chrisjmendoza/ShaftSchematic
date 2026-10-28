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
 * The tap and selection affordances drawn OVER the sheet take [Selection] too: they sit on the
 * same white paper, and a theme accent chosen for its own scheme's surfaces read weakly there
 * (on-device report: High contrast's amber on the white wear overview). Error marks on a draft
 * stay theme-driven.
 */
object SheetInk {
    /** Profile outlines, dimension rails, and sheet text. */
    val Outline = Color.Black

    /** Liner tint on wear/runout sheets — the historical light-theme tertiary (bronze-mauve). */
    val LinerTint = Color(0xFF7D5260)

    /** Wear marks (tints, hatches, pit X's) — the historical light-theme error red. */
    val WearRed = Color(0xFFB3261E)

    /**
     * Tap tints, selection highlights and count badges on a sheet — the historical light-theme
     * primary, so the default Light theme draws exactly what it always did.
     */
    val Selection = Color(0xFF6650A4)

    /** Text on a [Selection]-filled badge. */
    val OnSelection = Color.White
}
