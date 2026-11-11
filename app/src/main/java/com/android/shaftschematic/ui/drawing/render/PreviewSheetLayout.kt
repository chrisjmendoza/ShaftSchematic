package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.geom.CompressedProfileXMap
import com.android.shaftschematic.geom.drawnExtentMm
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.schematicProfileFit
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent

/*
 * The editor preview drawn like the schematic sheet (on-device request: "add the S breaks as an
 * option as well so the shaft renders more closely to the output").
 *
 * Nothing here solves a scale or a map. The sheet's own pipeline does (`schematicProfileFit` in
 * `pdf/ShaftPdfComposer.kt` — the sizing curve, the "Shaft height" multiplier, the paper band,
 * the per-kind width floors, the keyway / bolt-row / opted-out-body pins and the liner & taper
 * compression pair), in sheet POINTS over the preview box at one point per dp — the convention
 * the preview's dimension rails already draw at (`PreviewDimensionRails.kt`). What is the
 * preview's own:
 *  - the height budget: the shaft may take at most [PREVIEW_SHEET_SHAFT_MAX_FRAC] of the box's
 *    content height, so the rail block keeps room above it;
 *  - the fallbacks to the linear map: a shaft with no overall length yet, and a window the
 *    problem marks have widened past the drawn extent — a part hanging past the shaft end
 *    cannot print anyway, and the linear map is what puts it in view.
 * The body runs, S-breaks included, are then drawn by the sheet's ONE body pass
 * (`drawBodyRunsWithBreaks`), called from `ShaftRenderer` under a point → pixel canvas scale.
 */

/**
 * Largest share of the preview box's content height the sheet-layout shaft may take. The sheet's
 * own sizing curve puts an 8" shaft at an inch of paper, which in a phone's 120 dp box would leave
 * the dimension rails no room at all. At 40% the shaft still draws several times taller than the
 * linear preview drew a long one, while a three-rail job (taper, liner, OAL) keeps its whole rail
 * block in the box on a phone — at half, the OAL rail ran off the top of that box (review renders).
 * A job with more rails still runs them off the top, the rails' own rule; the shaft is never
 * drawn smaller for them.
 */
internal const val PREVIEW_SHEET_SHAFT_MAX_FRAC = 0.4f

/** Widening of the window (mm) below which the problem marks count as inside the drawn extent. */
private const val EXTENT_WIDENED_EPS_MM = 0.01f

/**
 * Everything the sheet layout reads besides the spec — the same values `composeShaftPdf` is
 * handed: the app-wide sizing-curve anchors and S-break threshold (`PdfPrefs`), and the per-job
 * "Shaft height" multiplier and liner & taper compression (`RunoutConfig`). A change to any of
 * them re-solves the preview.
 */
data class PreviewSheetSettings(
    val curveLoHeightIn: Float = PdfPrefs().curveLoHeightIn,
    val curveHiHeightIn: Float = PdfPrefs().curveHiHeightIn,
    val sBreakThresholdFrac: Float = PdfPrefs().sBreakThresholdFrac,
    val heightScale: Float = 1f,
    val linerMinFracOfTrue: Float = 0f,
) {
    /** The look prefs the profile solve reads, as the sheet holds them. */
    internal fun pdfPrefs(): PdfPrefs = PdfPrefs(
        curveLoHeightIn = curveLoHeightIn,
        curveHiHeightIn = curveHiHeightIn,
        sBreakThresholdFrac = sBreakThresholdFrac,
    )
}

/**
 * The sheet's profile solved for one preview box: the compressed x map in POINTS from the content
 * left edge ([xMap]), the diameter scale in pt/mm ([diaPtPerMm]) and the canvas density
 * ([pxPerPt]); the break threshold rides along because the body pass the renderer calls reads it.
 * `ShaftLayout.Result.sheet` carries it to every mm → px mapping on the preview.
 */
class PreviewSheetProfile internal constructor(
    val xMap: CompressedProfileXMap,
    val diaPtPerMm: Float,
    val pxPerPt: Float,
    val sBreakThresholdFrac: Float,
)

/**
 * Solves the sheet profile for a preview content box [contentWidthPx] × [contentHeightPx], or
 * returns null where the preview stays linear: no overall length yet (the `renderSpanSpec`
 * fallback draws such a shaft to its coverage end, and the sheet has no OAL to map), or
 * [extentMm] — the problem marks' window — reaching past the drawn extent.
 *
 * The budget is the box's FULL content height, never the strip left after the rails' reservation:
 * the shaft is never drawn smaller for the rails.
 */
internal fun previewSheetProfile(
    spec: ShaftSpec,
    settings: PreviewSheetSettings,
    contentWidthPx: Float,
    contentHeightPx: Float,
    pxPerPt: Float,
    extentMm: Pair<Float, Float>? = null,
): PreviewSheetProfile? {
    if (spec.overallLengthMm <= 0f) return null
    if (contentWidthPx <= 1f || contentHeightPx <= 1f || pxPerPt <= 0f) return null
    if (extentMm != null) {
        val drawn = spec.drawnExtentMm()
        if (extentMm.first < drawn.startMm - EXTENT_WIDENED_EPS_MM ||
            extentMm.second > drawn.endMm + EXTENT_WIDENED_EPS_MM
        ) return null
    }
    val fit = schematicProfileFit(
        spec,
        settings.pdfPrefs(),
        settings.heightScale,
        settings.linerMinFracOfTrue,
        contentLeft = 0f,
        contentRight = contentWidthPx / pxPerPt,
        budgetCapPt = contentHeightPx / pxPerPt * PREVIEW_SHEET_SHAFT_MAX_FRAC,
    )
    return PreviewSheetProfile(fit.xMap, fit.diaPtPerMm, pxPerPt, settings.sBreakThresholdFrac)
}

/**
 * [previewSheetProfile] for a preview box given as its layout rect ([leftPx]…[bottomPx], the rect
 * `ShaftLayout.compute` is handed with no rail reservation): the content box is that rect inside
 * the layout's margins. [layoutSpec] is the spec the box lays out (`renderSpanSpec`), [spec] the
 * authored one whose overall length decides the fallback. ONE construction behind `ShaftDrawing`
 * and the review renders.
 */
internal fun previewSheetProfileForBox(
    spec: ShaftSpec,
    layoutSpec: ShaftSpec,
    settings: PreviewSheetSettings,
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
    pxPerPt: Float,
    resolvedComponents: List<ResolvedComponent>? = null,
    extentMm: Pair<Float, Float>? = null,
): PreviewSheetProfile? {
    val base = ShaftLayout.compute(
        spec = layoutSpec,
        leftPx = leftPx,
        topPx = topPx,
        rightPx = rightPx,
        bottomPx = bottomPx,
        resolvedComponents = resolvedComponents,
        extentMm = extentMm,
    )
    return previewSheetProfile(
        spec = spec,
        settings = settings,
        contentWidthPx = base.contentRightPx - base.contentLeftPx,
        contentHeightPx = base.contentBottomPx - base.contentTopPx,
        pxPerPt = pxPerPt,
        extentMm = extentMm,
    )
}

/**
 * One cached [PreviewSheetProfile] per canvas size — the [PreviewRailCache] posture: `remember`ed
 * against every input that changes the solve, so a pan/zoom frame reuses it.
 */
internal class PreviewSheetCache {
    private var width = Float.NaN
    private var height = Float.NaN
    private var solved = false
    private var profile: PreviewSheetProfile? = null

    fun get(width: Float, height: Float, compute: () -> PreviewSheetProfile?): PreviewSheetProfile? {
        if (solved && width == this.width && height == this.height) return profile
        return compute().also {
            profile = it
            solved = true
            this.width = width
            this.height = height
        }
    }
}
