package com.android.shaftschematic.ui.drawing.render

import com.android.shaftschematic.geom.CompressedProfileXMap
import com.android.shaftschematic.geom.drawnExtentMm
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.SCHEMATIC_SHEET_CONTENT_WIDTH_PT
import com.android.shaftschematic.pdf.schematicProfileFit
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import kotlin.math.max

/*
 * The editor preview drawn like the schematic sheet (on-device request: "add the S breaks as an
 * option as well so the shaft renders more closely to the output").
 *
 * Nothing here solves a scale or a map. The sheet's own pipeline does (`schematicProfileFit` in
 * `pdf/ShaftPdfComposer.kt` — the sizing curve, the "Shaft height" multiplier, the paper band,
 * the per-kind width floors, the keyway / bolt-row / opted-out-body pins and the liner & taper
 * compression pair), in sheet POINTS over the schematic sheet's own content width
 * (`SCHEMATIC_SHEET_CONTENT_WIDTH_PT`), and the solved profile is drawn into the box under ONE
 * uniform scale — the preview is a miniature of the sheet's shaft. The sheet's width floors are
 * paper measures: solved over a box narrower than the page they would spend the whole box on
 * floors, leave the liner & taper raises no room, and flatten every liner to the same width
 * (on-device report: liners of 45", 31" and 16 1/2" drew the same width on a phone). A box at
 * least as wide as the page solves over its own width at one point per dp, unscaled. What is the
 * preview's own:
 *  - the height budget: the shaft may take at most [PREVIEW_SHEET_SHAFT_MAX_FRAC] of the box's
 *    content height, so the rail block keeps room above it;
 *  - the fallbacks to the linear map: a shaft with no overall length yet, and a window the
 *    problem marks have widened past the drawn extent — a part hanging past the shaft end
 *    cannot print anyway, and the linear map is what puts it in view.
 * The body runs, S-breaks included, are then drawn by the sheet's ONE body pass
 * (`drawBodyRunsWithBreaks`), called from `ShaftRenderer` under the profile's point → pixel canvas
 * scale, so the break decisions, gaps and glyphs are the sheet's at the sheet's proportion. The
 * dimension rails are not part of the miniature: they draw at one point per dp
 * (`PreviewDimensionRails.kt`) and take only span END positions from this map.
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
 * The sheet's profile solved for one preview box: the compressed x map in sheet POINTS from the
 * content left edge ([xMap]), the diameter scale in pt/mm ([diaPtPerMm]) and the ONE uniform
 * point → pixel scale the box draws the profile at ([pxPerPt] — the canvas density times the
 * miniature factor, so the solved sheet width spans the box's content width); the break threshold
 * rides along because the body pass the renderer calls reads it. `ShaftLayout.Result.sheet`
 * carries it to every mm → px mapping on the preview. What must keep its on-screen size does not
 * scale with it: the dimension rails draw at the canvas density, and stroke widths go into the
 * body pass as screen px divided by [pxPerPt].
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
 * The fit is solved over a virtual width `W = max(box width, sheetWidthPt)` in points at
 * [densityPxPerPt] px per point, and the box draws it at the uniform scale `s = box width / W`
 * (≤ 1): the profile's [PreviewSheetProfile.pxPerPt] is `densityPxPerPt × s`, so its map spans
 * exactly the box's content width and every length and diameter keeps the sheet's proportion. The
 * height budget is handed over in the same virtual points (the cap divided by `s`), so the drawn
 * shaft still takes at most [PREVIEW_SHEET_SHAFT_MAX_FRAC] of the box. A box at least as wide as
 * [sheetWidthPt] solves over its own width with `s = 1`.
 *
 * The budget is the box's FULL content height, never the strip left after the rails' reservation:
 * the shaft is never drawn smaller for the rails. [drawnMaxDiaMm] is the largest diameter the box
 * draws, bare shaft included, so the height budget holds a shaft that is wider than (or has none
 * of) its stored components — a new drawing with only an overall length and a shaft diameter.
 */
internal fun previewSheetProfile(
    spec: ShaftSpec,
    settings: PreviewSheetSettings,
    contentWidthPx: Float,
    contentHeightPx: Float,
    densityPxPerPt: Float,
    extentMm: Pair<Float, Float>? = null,
    drawnMaxDiaMm: Float = 0f,
    sheetWidthPt: Float = SCHEMATIC_SHEET_CONTENT_WIDTH_PT,
): PreviewSheetProfile? {
    if (spec.overallLengthMm <= 0f) return null
    if (contentWidthPx <= 1f || contentHeightPx <= 1f || densityPxPerPt <= 0f) return null
    if (extentMm != null) {
        val drawn = spec.drawnExtentMm()
        if (extentMm.first < drawn.startMm - EXTENT_WIDENED_EPS_MM ||
            extentMm.second > drawn.endMm + EXTENT_WIDENED_EPS_MM
        ) return null
    }
    val boxWidthPt = contentWidthPx / densityPxPerPt
    val virtualWidthPt = max(boxWidthPt, sheetWidthPt)
    val miniature = boxWidthPt / virtualWidthPt
    val fit = schematicProfileFit(
        spec,
        settings.pdfPrefs(),
        settings.heightScale,
        settings.linerMinFracOfTrue,
        contentLeft = 0f,
        contentRight = virtualWidthPt,
        budgetCapPt = contentHeightPx / densityPxPerPt * PREVIEW_SHEET_SHAFT_MAX_FRAC / miniature,
        drawnMaxDiaMm = drawnMaxDiaMm,
    )
    return PreviewSheetProfile(fit.xMap, fit.diaPtPerMm, densityPxPerPt * miniature, settings.sBreakThresholdFrac)
}

/**
 * [previewSheetProfile] for a preview box given as its layout rect ([leftPx]…[bottomPx], the rect
 * `ShaftLayout.compute` is handed with no rail reservation): the content box is that rect inside
 * the layout's margins. [layoutSpec] is the spec the box lays out (`renderSpanSpec`), [spec] the
 * authored one whose overall length decides the fallback; [densityPxPerPt] is the canvas density
 * (px per dp, one point per dp). ONE construction behind `ShaftDrawing`
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
    densityPxPerPt: Float,
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
        densityPxPerPt = densityPxPerPt,
        extentMm = extentMm,
        // The linear layout's own radial span: the resolved runs' largest Ø, bare shaft included.
        drawnMaxDiaMm = base.maxDiaMm,
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
