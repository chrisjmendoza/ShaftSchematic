package com.android.shaftschematic.ui.drawing.render

import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.DimensionRailLayout
import com.android.shaftschematic.geom.PreviewRailFit
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.SchematicRailSpans
import com.android.shaftschematic.pdf.dim.DimSpan
import com.android.shaftschematic.pdf.render.PdfDimensionRenderer
import com.android.shaftschematic.pdf.schematicRailSpans
import com.android.shaftschematic.pdf.sheetTextPaint
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.OutputFont
import com.android.shaftschematic.util.PreviewDimTextSize

/*
 * The editor preview's dimension rails (on-device request: "it still just feels weird to have the
 * shaft and nothing else").
 *
 * Nothing here decides a span, a value, a tier or a label position. The spans are the schematic
 * sheet's own (`schematicRailSpans`, the ONE builder behind the composer's rail pass), the labels
 * are placed by the ONE planner (`DimensionRailLayout`) and inked by the ONE rail renderer
 * (`PdfDimensionRenderer`). What is the preview's own:
 *  - the x map: each span maps through the preview's own `ShaftLayout` — the sheet's compressed
 *    map under the sheet layout (`PreviewSheetLayout.kt`), the linear one otherwise — so its ends
 *    land on the component edges the box draws;
 *  - the scale: the rails lay out in sheet POINTS and draw through one canvas scale, one point to
 *    [pxPerPt] pixels — the canvas density, never the sheet layout's miniature scale, so the
 *    values keep their on-screen size whatever the shaft is drawn at — and the planner's point
 *    constants and the renderer's arrow, pad and halo all scale together; the shared path takes
 *    only two preview parameters, the halo fill and the side clearance between values at one
 *    height, both defaulting to the sheet's;
 *  - the value size: [PREVIEW_RAIL_TEXT_PT] times the device preference
 *    [PreviewRailSettings.textSize] (Small | Medium | Large), with the lane pitch scaled alongside;
 *  - the room: the box reserves the strip the rails need ([PreviewRailFit]).
 */

/** Rail value size (pt) at Medium — a little under the sheet's 10 pt; the user zooms the box to read. */
internal const val PREVIEW_RAIL_TEXT_PT = 9f

/** The rail value size (pt) for [size] — the preview's own "Dimension text size", never the sheet's. */
internal fun previewRailTextPt(size: PreviewDimTextSize): Float = PREVIEW_RAIL_TEXT_PT * size.factor

/** Shaft top → the first rail line (pt). The sheet keeps 36 pt; the box cannot afford it. */
internal const val PREVIEW_RAIL_CLEAR_PT = 10f

/** Lane pitch between rails (pt) at Medium, opened to the value height on a stacked-dual drawing. */
internal const val PREVIEW_RAIL_LANE_PT = 14f

/** Air kept between the rail block and the box top (pt). */
internal const val PREVIEW_RAIL_TOP_PAD_PT = 4f

/** Gap between the shaft silhouette and the foot of an extension line (pt). */
internal const val PREVIEW_RAIL_EXT_CLEAR_PT = 3f

/** Dimension-line stroke (pt) — finer than the preview's own outline. */
internal const val PREVIEW_RAIL_STROKE_PT = 0.5f

/** The planner's top bound for rails that overrun the box: far above anything it could place. */
private const val UNBOUNDED_TOP_PT = -1.0e6f

/**
 * Everything the preview's rails read besides the spec: the sheet's units and the look prefs the
 * sheet's rails read, plus the preview's own value size [textSize] (a device preference — no
 * composer reads it). [fractionStyle] and [outputFont] are re-render KEYS only — the paints reach
 * them through the process-wide `FractionTypography`/`OutputTypography` mirrors, which are not
 * snapshot state, so a change has to arrive here to replan.
 */
data class PreviewRailSettings(
    val displayUnits: DisplayUnits,
    val tieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    val arrowSizePt: Float = PdfPrefs().arrowSizePt,
    val dualUnitLayout: DualUnitLayout = DualUnitLayout.INLINE,
    val fractionStyle: FractionStyle = FractionStyle.Default,
    val outputFont: OutputFont = OutputFont.Default,
    val textSize: PreviewDimTextSize = PreviewDimTextSize.Default,
)

/**
 * The spans the preview draws for [spec] — the schematic sheet's, from the same builder. `null`
 * while no overall length is typed: every span is measured from an S.E.T. or face the OAL locates,
 * and the sheet's own rails for a 0 OAL are a zero-width "OAL 0" and liner offsets taken from a
 * FWD datum at the AFT face, which the box should not repeat.
 */
internal fun previewRailSpans(spec: ShaftSpec, settings: PreviewRailSettings): SchematicRailSpans? {
    if (spec.overallLengthMm <= 0f) return null
    return schematicRailSpans(
        spec,
        settings.displayUnits.documentUnit,
        settings.displayUnits,
        PdfPrefs(tieringMode = settings.tieringMode),
    )
}

/** A planned rail block for one preview size: the layout it was planned against and what to ink. */
internal class PreviewRailPlan(
    val layout: ShaftLayout.Result,
    private val renderer: PdfDimensionRenderer,
    val spans: List<DimSpan>,
    val placements: List<DimensionRailLayout.Placement>,
    val keptRails: Int,
    val reservePx: Float,
    private val pxPerPt: Float,
) {
    /** Inks the plan onto [canvas], in layout px (any pan/zoom transform already applied). */
    fun draw(canvas: Canvas) {
        if (spans.isEmpty()) return
        canvas.save()
        canvas.scale(pxPerPt, pxPerPt)
        renderer.drawPlan(canvas, spans, placements)
        canvas.restore()
    }
}

/**
 * Plans [rails] for a preview box whose top edge is [boxTopPx] and height [boxHeightPx].
 *
 * [layoutFor] is the preview's own `ShaftLayout.compute` with `reservePx` added to its top — the
 * preview calls it with 0 when no rails draw, so the rails-off box lays out exactly as before.
 */
internal fun planPreviewRails(
    rails: SchematicRailSpans,
    settings: PreviewRailSettings,
    boxTopPx: Float,
    boxHeightPx: Float,
    pxPerPt: Float,
    railColor: Int,
    haloColor: Int,
    layoutFor: (reservePx: Float) -> ShaftLayout.Result,
): PreviewRailPlan {
    val textScale = settings.textSize.factor
    val textPaint = sheetTextPaint(previewRailTextPt(settings.textSize), color = railColor)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = PREVIEW_RAIL_STROKE_PT
        color = railColor
    }
    val stacked = settings.displayUnits.dual && settings.dualUnitLayout == DualUnitLayout.STACKED
    val componentRails = rails.assignments.maxOfOrNull { it.rail + 1 } ?: 0
    val boxTopPt = boxTopPx / pxPerPt
    val sideClearPt = textPaint.fontMetrics.let { it.descent - it.ascent }

    fun rendererFor(layout: ShaftLayout.Result) = PdfDimensionRenderer(
        pageX = { mm -> layout.xPx((mm + rails.win.measureStartMm).toFloat()) / pxPerPt },
        linePaint = linePaint,
        textPaint = textPaint,
        objectTopY = layout.shaftTopPx / pxPerPt,
        objectClearance = PREVIEW_RAIL_EXT_CLEAR_PT,
        arrowSize = settings.arrowSizePt,
        dualStacked = stacked,
        haloColor = haloColor,
        // A text height of air between two values on one row (emulator QA: "16 1/2"" and
        // "9 17/32"" side by side at the sheet's few points read as one string).
        labelSideClear = sideClearPt,
    )

    class Planned(
        val renderer: PdfDimensionRenderer,
        val spans: List<DimSpan>,
        val placements: List<DimensionRailLayout.Placement>,
        val topPt: Float,
    )

    // Every row the sheet prints; the OAL rail rides one lane above the highest numbered rail,
    // exactly as on the sheet. `bounded` keeps floated values inside the box top; rails that
    // overrun the box anyway are planned unbounded, so their values keep their own rows.
    fun planFor(layout: ShaftLayout.Result, bounded: Boolean): Planned {
        val kept = componentRails
        val r = rendererFor(layout)
        val lane = maxOf(
            PREVIEW_RAIL_LANE_PT * textScale,
            r.labelHeight() + 2f * DimensionRailLayout.LINE_HALF_CLEAR + 2f,
        )
        val baseY = layout.shaftTopPx / pxPerPt - PREVIEW_RAIL_CLEAR_PT
        val keptRows = rails.assignments.filter { it.rail < kept }
        val spans = keptRows.map { it.span } + rails.oalDimSpan
        val inputs = keptRows.map { r.spanInput(it.rail, baseY - lane * it.rail, it.span) } +
            r.spanInput(DimensionRailLayout.TOP_RAIL, baseY - lane * kept, rails.oalDimSpan)
        val plan = r.plan(
            inputs,
            safeTopY = if (bounded) boxTopPt + PREVIEW_RAIL_TOP_PAD_PT else UNBOUNDED_TOP_PT,
        )
        // The block's top: the highest value box or rail line, arrowheads' barbs included.
        val top = plan.placements.minOf { minOf(it.label.top, it.railY - settings.arrowSizePt * 0.5f) }
        return Planned(r, spans, plan.placements, top)
    }

    fun deficitPx(reservePx: Float): Float {
        val planned = planFor(layoutFor(reservePx), bounded = false)
        return (boxTopPt + PREVIEW_RAIL_TOP_PAD_PT - planned.topPt) * pxPerPt
    }

    // What the shaft itself needs: its height at the width-fitted scale (the scale a long shaft
    // draws at whatever the box height) plus the layout's padding and margins, which also hold
    // the selection ring. Everything above that is the rails' to take. Under the sheet layout the
    // shaft's height is the sheet's own (its diameter scale does not depend on the reservation).
    val base = layoutFor(0f)
    val widthFitPxPerMm = (base.contentRightPx - base.contentLeftPx) /
        (base.maxXMm - base.minXMm).coerceAtLeast(1e-3f)
    val shaftPx = if (base.sheet != null) {
        2f * base.rPx(base.maxDiaMm)
    } else {
        maxOf(base.maxDiaMm, LAYOUT_MIN_FIT_DIA_MM) * widthFitPxPerMm
    }
    val maxReserve = PreviewRailFit.maxReservePx(
        boxHeightPx = boxHeightPx,
        layoutChromePx = boxHeightPx - (base.contentBottomPx - base.contentTopPx),
        widthFitShaftPx = shaftPx,
    )
    val fit = PreviewRailFit.fit(maxReserve, ::deficitPx)
    val layout = layoutFor(fit.reservePx)
    val planned = planFor(layout, bounded = fit.fits)
    return PreviewRailPlan(
        layout = layout,
        renderer = planned.renderer,
        spans = planned.spans,
        placements = planned.placements,
        keptRails = componentRails,
        reservePx = fit.reservePx,
        pxPerPt = pxPerPt,
    )
}

/**
 * One cached [PreviewRailPlan] per canvas size. The holder is `remember`ed against every input
 * that changes a plan, so a pan/zoom frame — same inputs, same size — reuses the plan and only
 * a real change replans.
 */
internal class PreviewRailCache {
    private var width = Float.NaN
    private var height = Float.NaN
    private var plan: PreviewRailPlan? = null

    fun get(width: Float, height: Float, compute: () -> PreviewRailPlan): PreviewRailPlan {
        val cached = plan
        if (cached != null && width == this.width && height == this.height) return cached
        return compute().also {
            plan = it
            this.width = width
            this.height = height
        }
    }
}
