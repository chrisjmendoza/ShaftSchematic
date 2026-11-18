package com.android.shaftschematic.pdf

import com.android.shaftschematic.gfx.ANTI_ALIAS_FLAG
import com.android.shaftschematic.gfx.Canvas
import com.android.shaftschematic.gfx.Color
import com.android.shaftschematic.gfx.Paint
import com.android.shaftschematic.gfx.PaintAlign
import com.android.shaftschematic.gfx.PaintStyle
import com.android.shaftschematic.gfx.RectF
import com.android.shaftschematic.gfx.left
import com.android.shaftschematic.gfx.right
import com.android.shaftschematic.gfx.top
import com.android.shaftschematic.geom.drawnExtentMm
import com.android.shaftschematic.gfx.color
import com.android.shaftschematic.gfx.isFakeBoldText
import com.android.shaftschematic.gfx.strokeWidth
import com.android.shaftschematic.gfx.style
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.autoSectionFiberglass
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.drawing.renderSpanSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.resolvedBodyBaseId
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.max
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// The Final tab's FULL LENGTH sheet: the whole shaft at true length, unbroken, with every
// fiberglass-wrapped section coloured and labelled, above the final schematic (breaks, rails,
// footer) — on-device request: "like the wear drawing, the full length shaft will show without
// breaks and the Schematic view with breaks and measurements will appear above or below it".
//
// The schematic band is the ONE schematic composer drawn into a [SheetBand], the before/after
// sheet's mechanism, so it prints exactly as the final schematic does. The full-length band is
// the ONE simple profile ([drawSimpleShaftProfile]) with its long-run breaks off, at a linear
// x map: lengths to scale. Its diameters are enlarged to a readable height (a 9" shaft 30 ft
// long is a quarter inch tall at true scale), so nothing is ever measured off it — it carries
// no values at all; the schematic below carries them.
//
// Black-and-white printers: the fiberglass colour prints as a mid grey, distinct from the light
// component shade, the crossed winding lines survive any printer, and every section carries the
// word FIBERGLASS — inside it when it fits, above it on a leader when it does not — so the sheet
// reads the same with no colour at all.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The Full length sheet onto any [Canvas] of the given page size — the seam a test puts a
 * recording canvas under, while the app comes in through `composeFullLengthPdf`. [project] is
 * already marked with the "Final" drawing label by every caller, so the footer prints
 * `Drawing: Final` and the FINAL badge as the final schematic does.
 */
fun composeFullLengthPdfOnCanvas(
    c: Canvas,
    pageW: Float,
    pageH: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    options: PdfExportOptions = PdfExportOptions(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1f,
    heightScale: Float = 1f,
    linerMinFracOfTrue: Float = 0f,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) {
    // The page is painted once here; the schematic band never paints, or it would erase the top band.
    c.drawColor(Color.WHITE)

    // Two drawings share the page: the before/after sheet's text caps keep the schematic band's
    // footer and rails inside the room left under the full-length band.
    val prefs = beforeAfterPrefs(pdfPrefs)
    val layout = fullLengthBands(pageW, pageH)

    drawFullLengthBand(
        c, spec, resolvedComponents ?: resolveComponents(spec), layout.fullLength, prefs, lineThicknessScale,
    )

    composeShaftPdfOnCanvas(
        c, pageW, pageH,
        spec = spec, unit = unit, project = project, appVersion = appVersion, filename = filename,
        pdfPrefs = prefs, options = options, resolvedComponents = resolvedComponents,
        lineThicknessScale = lineThicknessScale, heightScale = heightScale,
        linerMinFracOfTrue = linerMinFracOfTrue, displayUnits = displayUnits,
        band = layout.schematic,
    )

    // The rule between the two drawings, last so no halo the schematic printed can cover it.
    val rule = Paint(ANTI_ALIAS_FLAG).apply {
        style = PaintStyle.STROKE
        strokeWidth = FULL_LENGTH_DIVIDER_PT * lineThicknessScale.coerceIn(0.5f, 2f)
        color = FULL_LENGTH_DIVIDER_COLOR
    }
    c.drawLine(SCHEMATIC_PAGE_MARGIN_PT, layout.dividerY, pageW - SCHEMATIC_PAGE_MARGIN_PT, layout.dividerY, rule)
}

/** The Full length sheet's two regions, in page points. */
class FullLengthBands(val fullLength: RectF, val schematic: SheetBand, val dividerY: Float)

/**
 * Lays the sheet out: the full-length band across the top of the content area,
 * [FULL_LENGTH_BAND_PT] tall, and the final schematic in everything under it, hosting the footer.
 */
fun fullLengthBands(pageW: Float, pageH: Float): FullLengthBands {
    val top = SCHEMATIC_PAGE_MARGIN_PT
    val dividerY = top + FULL_LENGTH_BAND_PT
    return FullLengthBands(
        fullLength = RectF(SCHEMATIC_PAGE_MARGIN_PT, top, pageW - SCHEMATIC_PAGE_MARGIN_PT, dividerY),
        schematic = SheetBand(dividerY, pageH - SCHEMATIC_PAGE_MARGIN_PT, badge = null, hostsFooter = true),
        dividerY = dividerY,
    )
}

/**
 * The drawn body runs of [spec] that are wrapped in fiberglass, by resolved id: an explicit run
 * reads its stored body's [com.android.shaftschematic.model.Body.fiberglass] (every fragment of a
 * split body alike — the fragment suffix is stripped), a bare-shaft run its own section anchor
 * ([autoSectionFiberglass]). A wrapped body trimmed around a liner or taper is wrapped only where
 * it is drawn, which is where the shaft is bare.
 */
fun fiberglassBodyRuns(spec: ShaftSpec, resolved: List<ResolvedComponent>): List<ResolvedBody> =
    resolved.filterIsInstance<ResolvedBody>().filter { b ->
        b.endMmPhysical > b.startMmPhysical && b.diaMm > 0f && when (b.source) {
            ResolvedComponentSource.AUTO -> spec.autoSectionFiberglass(b.startMmPhysical, b.endMmPhysical)
            else -> spec.bodies.firstOrNull { it.id == resolvedBodyBaseId(b.id) }?.fiberglass == true
        }
    }

/**
 * Draws the full-length band into [rect]: its title row and (when any section is wrapped) the
 * fiberglass legend, then the whole shaft at a linear x map with no breaks, fiberglass sections
 * filled and wound under the outlines, and each section's FIBERGLASS label last.
 */
internal fun drawFullLengthBand(
    c: Canvas,
    spec: ShaftSpec,
    resolved: List<ResolvedComponent>,
    rect: RectF,
    prefs: PdfPrefs,
    lineThicknessScale: Float,
) {
    val thickness = lineThicknessScale.coerceIn(0.5f, 2f)
    val title = sheetTextPaint(FULL_LENGTH_TITLE_PT).apply { isFakeBoldText = true }
    val note = sheetTextPaint(FULL_LENGTH_NOTE_PT)
    val titleBaseline = rect.top + FULL_LENGTH_TITLE_PT
    c.drawText(FULL_LENGTH_TITLE, rect.left, titleBaseline, title)
    c.drawText(
        FULL_LENGTH_SCALE_NOTE,
        rect.left + title.measureText(FULL_LENGTH_TITLE) + 8f, titleBaseline, note,
    )

    val docSpec = spec.withResolvedBodies(resolved)
    val spanSpec = spec.renderSpanSpec()
    if (spanSpec.overallLengthMm <= 0f) return
    // The drawn extent, not the OAL: a thread excluded from the overall length sits outside
    // 0..OAL, and a map fitted to the OAL alone would draw it off the band's end.
    val extent = spanSpec.drawnExtentMm()
    val maxDiaMm = listOfNotNull(
        docSpec.bodies.maxOfOrNull { it.diaMm },
        docSpec.liners.maxOfOrNull { it.odMm },
        docSpec.tapers.maxOfOrNull { max(it.startDiaMm, it.endDiaMm) },
        docSpec.threads.maxOfOrNull { it.majorDiaMm },
    ).maxOrNull()?.takeIf { it > 0f } ?: return

    val xPerMm = rect.width() / extent.spanMm
    val xAt: (Float) -> Float = { mm -> rect.left + (mm - extent.startMm) * xPerMm }
    val drawnMaxDiaPt = fullLengthDrawnDiaPt(maxDiaMm, xPerMm)
    val rPerMm = drawnMaxDiaPt / maxDiaMm / 2f
    val rPx: (Float) -> Float = { dia -> dia * rPerMm }
    val shaftTop = rect.top + FULL_LENGTH_TITLE_ROW_PT + 2f * FULL_LENGTH_LABEL_ROW_PT
    val cy = shaftTop + drawnMaxDiaPt / 2f

    val outline = Paint(ANTI_ALIAS_FLAG).apply {
        style = PaintStyle.STROKE; strokeWidth = FULL_LENGTH_OUTLINE_PT * thickness; color = Color.BLACK
    }
    fun shade() = Paint(ANTI_ALIAS_FLAG).apply { style = PaintStyle.FILL; color = Color.argb(40, 0, 0, 0) }

    val wrapped = fiberglassBodyRuns(spec, resolved)
    val fill = Paint(ANTI_ALIAS_FLAG).apply { style = PaintStyle.FILL; color = FIBERGLASS_FILL_COLOR }
    val winding = Paint(ANTI_ALIAS_FLAG).apply {
        style = PaintStyle.STROKE; strokeWidth = FIBERGLASS_WINDING_PT; color = FIBERGLASS_WINDING_COLOR
    }
    wrapped.forEach { b ->
        val x0 = xAt(b.startMmPhysical); val x1 = xAt(b.endMmPhysical); val r = rPx(b.diaMm)
        c.drawRect(x0, cy - r, x1, cy + r, fill)
        drawFiberglassWinding(c, x0, x1, cy - r, cy + r, winding)
    }

    drawSimpleShaftProfile(
        c, docSpec, cy, outline, rect, xAt, rPx,
        bodyFill = if (prefs.shadedBodies) shade() else null,
        taperFill = if (prefs.shadedTapers) shade() else null,
        linerFill = if (prefs.shadedLiners) shade() else null,
        dimStrokeWidthPt = FULL_LENGTH_DIM_PT * thickness,
        breakLongRuns = false,
        unfilledBodyIds = wrapped.mapTo(mutableSetOf()) { it.id },
    )

    if (wrapped.isEmpty()) return
    drawFiberglassLegend(c, rect.right, titleBaseline, outline, fill, winding)
    val label = sheetTextPaint(FIBERGLASS_LABEL_PT, PaintAlign.CENTER).apply { isFakeBoldText = true }
    val placements = planFiberglassLabels(
        wrapped.map { b -> FiberglassLabelRun(xAt(b.startMmPhysical), xAt(b.endMmPhysical), 2f * rPx(b.diaMm)) },
        labelWidthPt = label.measureText(FIBERGLASS_LABEL),
        labelHeightPt = FIBERGLASS_LABEL_PT,
        rowCount = 2,
        contentLeft = rect.left,
        contentRight = rect.right,
    )
    val leader = Paint(outline).apply { strokeWidth = FULL_LENGTH_DIM_PT * thickness }
    val halo = Paint(ANTI_ALIAS_FLAG).apply { style = PaintStyle.FILL; color = Color.WHITE }
    val labelHalf = label.measureText(FIBERGLASS_LABEL) / 2f
    fun aboveBaseline(row: Int) = shaftTop - 3f - row * FULL_LENGTH_LABEL_ROW_PT
    // Every leader first, then every label on a sheet-white knockout: a leader dropping from the
    // upper row passes through the lower row, and drawn label by label it would strike through
    // a word already printed there.
    placements.forEachIndexed { i, p ->
        if (p.row < 0) return@forEachIndexed
        val b = wrapped[i]
        val runMid = (xAt(b.startMmPhysical) + xAt(b.endMmPhysical)) / 2f
        c.drawLine(p.centerX, aboveBaseline(p.row) + 2f, runMid, cy - rPx(b.diaMm), leader)
    }
    placements.forEach { p ->
        if (p.row < 0) {
            // Inside: on a knockout of the plain fiberglass colour, so the winding lines never
            // run through the letters.
            val half = labelHalf + FIBERGLASS_LABEL_PAD_PT
            c.drawRect(p.centerX - half, cy - FIBERGLASS_LABEL_PT * 0.6f, p.centerX + half, cy + FIBERGLASS_LABEL_PT * 0.6f, fill)
            c.drawText(FIBERGLASS_LABEL, p.centerX, cy + FIBERGLASS_LABEL_PT * 0.35f, label)
        } else {
            val baseline = aboveBaseline(p.row)
            c.drawRect(
                p.centerX - labelHalf - 1f, baseline - FIBERGLASS_LABEL_PT,
                p.centerX + labelHalf + 1f, baseline + 1.5f, halo,
            )
            c.drawText(FIBERGLASS_LABEL, p.centerX, baseline, label)
        }
    }
}

/**
 * The diameter, in points, the largest Ø on the full-length band draws at: true scale when that
 * is already readable ([FULL_LENGTH_SHAFT_MIN_PT] or more), else enlarged to it — never past
 * [FULL_LENGTH_SHAFT_MAX_PT], the band's room. Lengths are never enlarged.
 */
fun fullLengthDrawnDiaPt(maxDiaMm: Float, xPerMm: Float): Float =
    max(maxDiaMm * xPerMm, FULL_LENGTH_SHAFT_MIN_PT).coerceAtMost(FULL_LENGTH_SHAFT_MAX_PT)

/** Crossed winding lines over one wrapped section — the filament-wound look, legible in grey. */
private fun drawFiberglassWinding(c: Canvas, x0: Float, x1: Float, top: Float, bot: Float, paint: Paint) {
    if (x1 <= x0 || bot <= top) return
    val run = (bot - top) * FIBERGLASS_WINDING_LEAN
    drawThreadHatchStrokes(c, x0, x1, top, bot, paint, FIBERGLASS_WINDING_SPACING_PT, FIBERGLASS_WINDING_LEAN)
    // The opposite hand: mirrored about the section's own centre.
    val saved = c.save()
    c.clipRect(x0, top, x1, bot)
    var hx = x0 - run
    while (hx <= x1 + run) {
        c.drawLine(hx, top, hx + run, bot, paint)
        hx += FIBERGLASS_WINDING_SPACING_PT
    }
    c.restoreToCount(saved)
}

/** The legend at the band's top right: a wrapped swatch and the word "Fiberglass". */
private fun drawFiberglassLegend(c: Canvas, right: Float, baseline: Float, outline: Paint, fill: Paint, winding: Paint) {
    val text = sheetTextPaint(FULL_LENGTH_NOTE_PT, PaintAlign.RIGHT)
    val textW = text.measureText(FIBERGLASS_LEGEND)
    val swRight = right - textW - 5f
    val swLeft = swRight - FIBERGLASS_SWATCH_W_PT
    val swTop = baseline - FIBERGLASS_SWATCH_H_PT
    c.drawRect(swLeft, swTop, swRight, baseline, fill)
    drawFiberglassWinding(c, swLeft, swRight, swTop, baseline, winding)
    c.drawRect(swLeft, swTop, swRight, baseline, Paint(outline).apply { strokeWidth = 0.6f })
    c.drawText(FIBERGLASS_LEGEND, right, baseline, text)
}

/** One wrapped section as the label planner sees it: its drawn x extent and drawn height. */
data class FiberglassLabelRun(val x0: Float, val x1: Float, val heightPt: Float)

/** Where one section's label goes: [row] −1 = inside the section, else that row above the shaft. */
data class FiberglassLabelPlacement(val centerX: Float, val row: Int)

/**
 * Places each wrapped section's FIBERGLASS label: INSIDE the section when the word and its pad
 * fit both its drawn width and height, else centred over it in the first of [rowCount] rows above
 * the shaft where it overlaps no label already there (kept inside the content). With every row
 * taken it takes the last row anyway — a label is never dropped, the sheet's only fiberglass cue
 * on a black-and-white printer.
 */
fun planFiberglassLabels(
    runs: List<FiberglassLabelRun>,
    labelWidthPt: Float,
    labelHeightPt: Float,
    rowCount: Int,
    contentLeft: Float,
    contentRight: Float,
): List<FiberglassLabelPlacement> {
    val taken = List(rowCount) { mutableListOf<ClosedFloatingPointRange<Float>>() }
    val half = labelWidthPt / 2f
    return runs.map { run ->
        val mid = (run.x0 + run.x1) / 2f
        val fitsInside = (run.x1 - run.x0) >= labelWidthPt + 2f * FIBERGLASS_LABEL_PAD_PT + 2f &&
            run.heightPt >= labelHeightPt * 1.3f
        if (fitsInside) return@map FiberglassLabelPlacement(mid, -1)
        val cx = mid.coerceIn(contentLeft + half, max(contentLeft + half, contentRight - half))
        val span = (cx - half - FIBERGLASS_LABEL_GAP_PT)..(cx + half + FIBERGLASS_LABEL_GAP_PT)
        val row = (0 until rowCount).firstOrNull { r ->
            taken[r].none { it.start < span.endInclusive && span.start < it.endInclusive }
        } ?: (rowCount - 1)
        taken[row] += span
        FiberglassLabelPlacement(cx, row)
    }
}

/**
 * The fiberglass fill — the pale sage of the wound wrap in the shop photographs. It prints as a
 * mid grey (≈ 70% luminance) on a black-and-white printer, clearly darker than the 16% component
 * shade around it.
 */
const val FIBERGLASS_FILL_COLOR: Int = 0xFFAEB78F.toInt()

/** The winding lines over a wrapped section: a dark olive, thin, crossed. */
const val FIBERGLASS_WINDING_COLOR: Int = 0xFF5F6A40.toInt()

/** The word every wrapped section carries — the cue that survives a printer with no colour. */
const val FIBERGLASS_LABEL = "FIBERGLASS"

private const val FIBERGLASS_LEGEND = "Fiberglass"
private const val FULL_LENGTH_TITLE = "FULL LENGTH"
private const val FULL_LENGTH_SCALE_NOTE = "lengths to scale"

/** The full-length band's height on the page, title row to the rule under it. */
const val FULL_LENGTH_BAND_PT = 96f
private const val FULL_LENGTH_TITLE_PT = 10f
private const val FULL_LENGTH_NOTE_PT = 8f
private const val FULL_LENGTH_TITLE_ROW_PT = 16f
private const val FULL_LENGTH_LABEL_ROW_PT = 11f

/** The largest Ø on the band draws at least this tall, so a long shaft's steps still read. */
const val FULL_LENGTH_SHAFT_MIN_PT = 36f

/** ... and never taller than this, the room the band leaves under its label rows. */
const val FULL_LENGTH_SHAFT_MAX_PT = 44f

private const val FULL_LENGTH_OUTLINE_PT = 1.0f
private const val FULL_LENGTH_DIM_PT = 0.6f
private const val FIBERGLASS_LABEL_PT = 7f
private const val FIBERGLASS_LABEL_PAD_PT = 2f
private const val FIBERGLASS_LABEL_GAP_PT = 4f
private const val FIBERGLASS_WINDING_PT = 0.35f
private const val FIBERGLASS_WINDING_SPACING_PT = 6f
private const val FIBERGLASS_WINDING_LEAN = 0.55f
private const val FIBERGLASS_SWATCH_W_PT = 18f
private const val FIBERGLASS_SWATCH_H_PT = 7f
private const val FULL_LENGTH_DIVIDER_PT = 0.5f
private const val FULL_LENGTH_DIVIDER_COLOR = 0xFF808080.toInt()
