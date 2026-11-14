package com.android.shaftschematic.pdf

import com.android.shaftschematic.settings.PdfPrefs

/**
 * Which shade fills the printed undercut drawing draws. Independent decisions, one per fill the
 * composer builds:
 *
 * - [body] / [taper] — the whole-shaft fallback form's body and taper fills, each following its
 *   own "Shade in Components" preference.
 * - [liner] / [stripLiner] — the fallback form's liners and the detail strips' liner span. Both
 *   follow the undercut drawing's OWN liner switch, `PdfPrefs.undercutShadeLiners`, never the
 *   schematic's `shadedLiners` (on-device request: switching liner shading off for the undercut
 *   sheet must not change the schematic). Off by default — the liner prints white and the cut
 *   sections carry the grey.
 * - [sectionCore] — the undercut section's core between the floor lines, refilled after the void
 *   erases it.
 * - [sectionCoreByDepth] — whether that core's tone follows the cut's depth
 *   (`PdfPrefs.undercutDepthShade`: shallow light, deep dark — `undercutDepthShadeAlpha`) instead
 *   of the one flat section tone.
 *
 * Nothing here is a paint or a colour: the composer owns the ink, this owns the decision.
 */
data class UndercutPdfFillPlan(
    val body: Boolean,
    val taper: Boolean,
    val liner: Boolean,
    val stripLiner: Boolean,
    val sectionCore: Boolean,
    val sectionCoreByDepth: Boolean = false,
) {
    /** True when the sheet draws at least one fill; false is the line-art posture. */
    val anyFill: Boolean get() = body || taper || liner || stripLiner || sectionCore
}

/**
 * The undercut sheet's fills for [prefs].
 *
 * `PdfPrefs.undercutLineArt` is absolute: it drops EVERY fill, the liners and the section core
 * included, so the sheet reads from the notch construction alone — the void erasing the surface
 * stroke, the full-height section faces, the floor lines.
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
            liner = prefs.undercutShadeLiners,
            stripLiner = prefs.undercutShadeLiners,
            sectionCore = true,
            sectionCoreByDepth = prefs.undercutDepthShade,
        )
    }
