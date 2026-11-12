package com.android.shaftschematic.util

/**
 * Size of the editor preview's dimension-rail values — Settings → Editor Screen and the editor's
 * Preview options sheet, "Dimension text size: Small | Medium | Large" (on-device request).
 *
 * A device preference for the on-screen preview only: no document carries it, a Drawing profile
 * does not capture it, and no PDF composer reads it — the printed sheets keep their own
 * "Dimension text size" (`PdfPrefs.dimTextScale`). [factor] multiplies the preview rails' value
 * size and the lane pitch that grows with it; Medium is the preview's historical size.
 * Stored by NAME; an unknown or missing name reads as [Default].
 */
enum class PreviewDimTextSize(val factor: Float, val label: String) {
    SMALL(0.8f, "Small"),
    MEDIUM(1f, "Medium"),
    LARGE(1.25f, "Large");

    companion object {
        val Default: PreviewDimTextSize = MEDIUM

        fun fromName(raw: String?): PreviewDimTextSize =
            entries.firstOrNull { it.name == raw } ?: Default
    }
}
