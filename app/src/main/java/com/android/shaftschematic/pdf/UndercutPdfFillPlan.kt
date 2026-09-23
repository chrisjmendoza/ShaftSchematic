package com.android.shaftschematic.pdf

import com.android.shaftschematic.settings.PdfPrefs

/**
 * Which shade fills the printed undercut drawing draws. Five independent decisions, one per
 * fill the composer builds:
 *
 * - [body] / [taper] / [liner] — the whole-shaft fallback form's component fills, each
 *   following its own "Shade in Components" preference.
 * - [stripLiner] — the detail strips' liner span, which is otherwise shaded whatever the liner
 *   preference says: the notch voids are pure white, and the grey liner is what gives the boxed
 *   cut sections their contrast (on-device request).
 * - [sectionCore] — the undercut section's core between the floor lines, refilled one step
 *   lighter than the liner after the void erases it.
 *
 * Nothing here is a paint or a colour: the composer owns the ink, this owns the decision.
 */
data class UndercutPdfFillPlan(
    val body: Boolean,
    val taper: Boolean,
    val liner: Boolean,
    val stripLiner: Boolean,
    val sectionCore: Boolean,
) {
    /** True when the sheet draws at least one fill; false is the line-art posture. */
    val anyFill: Boolean get() = body || taper || liner || stripLiner || sectionCore
}

/**
 * The undercut sheet's fills for [prefs].
 *
 * `PdfPrefs.undercutLineArt` is absolute: it drops EVERY fill, the strips' always-shaded liner
 * and the section core included, so the sheet reads from the notch construction alone — the void
 * erasing the surface stroke, the full-height section faces, the floor lines. With it off each
 * component kind follows its own shade preference and the two undercut-specific fills stay on,
 * which is the shipped sheet.
 *
 * Print-side only, and deliberately independent of the screen's `util/UndercutStyle.kt` line-art
 * mode — that style never reaches a composer, so the two flags mean the same thing on two
 * surfaces and neither reads the other.
 */
internal fun undercutPdfFillPlan(prefs: PdfPrefs): UndercutPdfFillPlan =
    if (prefs.undercutLineArt) {
        UndercutPdfFillPlan(
            body = false, taper = false, liner = false, stripLiner = false, sectionCore = false,
        )
    } else {
        UndercutPdfFillPlan(
            body = prefs.shadedBodies,
            taper = prefs.shadedTapers,
            liner = prefs.shadedLiners,
            stripLiner = true,
            sectionCore = true,
        )
    }
