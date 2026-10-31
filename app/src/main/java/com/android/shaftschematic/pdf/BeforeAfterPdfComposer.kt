package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.min

/**
 * The Final tab's before/after sheet: the ORIGINAL shaft on the top half of one landscape page,
 * the FINAL shaft on the bottom half, under ONE footer (on-device request: seeing both drawings
 * on one sheet is what shows the change the wear and undercut work made).
 *
 * Each half is a whole schematic — OAL rail, component rails, Ø callouts, names, S-breaks — drawn
 * through the ONE schematic composer ([composeShaftPdfOnCanvas]) inside a [SheetBand], so neither
 * drawing can drift from the schematic's own drawing code or from the other. A band is half a page,
 * so its shaft draws at the scale the band's rails leave room for ([schematicBandLayout]), and
 * both bands share ONE diameter scale: a diameter the work changed reads as a changed height. The bands carry the [BEFORE_AFTER_ORIGINAL_BADGE] /
 * [BEFORE_AFTER_FINAL_BADGE] labels, so the two can never be read for each other.
 *
 * The footer is the FINAL's: it describes the shaft being made, and every caller already marks
 * [project] with the "Final" drawing label, so the job block prints `Drawing: Final` and the FINAL
 * badge exactly as the single final sheet does. Both bands take the same options, prefs, height,
 * liner compression, display units and line weight — the two drawings are tuned alike.
 */
fun composeBeforeAfterPdf(
    page: PdfDocument.Page,
    originalSpec: ShaftSpec,
    finalSpec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    options: PdfExportOptions = PdfExportOptions(),
    originalResolved: List<ResolvedComponent>? = null,
    finalResolved: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1f,
    heightScale: Float = 1f,
    linerMinFracOfTrue: Float = 0f,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) = composeBeforeAfterPdfOnCanvas(
    c = page.canvas,
    pageW = page.info.pageWidth.toFloat(),
    pageH = page.info.pageHeight.toFloat(),
    originalSpec = originalSpec, finalSpec = finalSpec,
    unit = unit, project = project, appVersion = appVersion, filename = filename,
    pdfPrefs = pdfPrefs, options = options,
    originalResolved = originalResolved, finalResolved = finalResolved,
    lineThicknessScale = lineThicknessScale, heightScale = heightScale,
    linerMinFracOfTrue = linerMinFracOfTrue, displayUnits = displayUnits,
)

/**
 * The before/after sheet onto any [Canvas] of the given page size — the seam a test puts a
 * recording canvas under, while the app comes in through [composeBeforeAfterPdf].
 */
internal fun composeBeforeAfterPdfOnCanvas(
    c: Canvas,
    pageW: Float,
    pageH: Float,
    originalSpec: ShaftSpec,
    finalSpec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    options: PdfExportOptions = PdfExportOptions(),
    originalResolved: List<ResolvedComponent>? = null,
    finalResolved: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1f,
    heightScale: Float = 1f,
    linerMinFracOfTrue: Float = 0f,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) {
    // The page is painted once here; the bands never paint, or the second would erase the first.
    c.drawColor(Color.WHITE)

    val bands = beforeAfterBands(
        pageW, pageH, originalSpec, finalSpec, unit, project, pdfPrefs, options, displayUnits,
        heightScale, linerMinFracOfTrue,
    )

    fun band(spec: ShaftSpec, resolved: List<ResolvedComponent>?, band: SheetBand) = composeShaftPdfOnCanvas(
        c, pageW, pageH,
        spec = spec, unit = unit, project = project, appVersion = appVersion, filename = filename,
        pdfPrefs = pdfPrefs, options = options, resolvedComponents = resolved,
        lineThicknessScale = lineThicknessScale, heightScale = heightScale,
        linerMinFracOfTrue = linerMinFracOfTrue, displayUnits = displayUnits,
        band = band,
    )

    band(originalSpec, originalResolved, bands.original)
    band(finalSpec, finalResolved, bands.final)

    // The rule between the two drawings, last so no value halo either band printed can cover it.
    val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BAND_DIVIDER_PT * lineThicknessScale.coerceIn(0.5f, 2f)
        color = BAND_DIVIDER_COLOR
    }
    c.drawLine(SCHEMATIC_PAGE_MARGIN_PT, bands.dividerY, pageW - SCHEMATIC_PAGE_MARGIN_PT, bands.dividerY, rule)
}

/** The before/after sheet's two bands and the rule between them, in page points. */
internal class BeforeAfterBands(val original: SheetBand, val final: SheetBand, val dividerY: Float)

/**
 * Lays the before/after sheet out: the ORIGINAL band above [BeforeAfterBands.dividerY], the FINAL
 * band below it hosting the footer — the FINAL's footer, measured by the same
 * [schematicFooterBlockPt] the full-page sheet reserves, so the rule and the footer it sits above
 * can never disagree.
 *
 * Both bands draw at ONE diameter scale — the smaller of the two scales their rooms allow
 * ([schematicBandLayout]). A comparison is only honest at one scale: a final whose largest
 * diameter changed then prints at a changed height, and one that only gained a rail cannot print
 * shorter than the original above it.
 */
internal fun beforeAfterBands(
    pageW: Float,
    pageH: Float,
    originalSpec: ShaftSpec,
    finalSpec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    pdfPrefs: PdfPrefs,
    options: PdfExportOptions,
    displayUnits: DisplayUnits,
    heightScale: Float,
    linerMinFracOfTrue: Float,
): BeforeAfterBands {
    val footerBlockPt = schematicFooterBlockPt(pageW, finalSpec, unit, project, pdfPrefs, options, displayUnits)
    val dividerY = beforeAfterDividerY(pageH, footerBlockPt)
    val top = SheetBand(SCHEMATIC_PAGE_MARGIN_PT, dividerY, BEFORE_AFTER_ORIGINAL_BADGE, hostsFooter = false)
    val bottom = SheetBand(dividerY, pageH - SCHEMATIC_PAGE_MARGIN_PT, BEFORE_AFTER_FINAL_BADGE, hostsFooter = true)
    val diaPtPerMmCap = min(
        schematicBandLayout(
            pageW, originalSpec, unit, pdfPrefs, options, displayUnits, top,
            footerBlockPt = 0f, heightScale = heightScale, linerMinFracOfTrue = linerMinFracOfTrue,
        ).diaPtPerMm,
        schematicBandLayout(
            pageW, finalSpec, unit, pdfPrefs, options, displayUnits, bottom,
            footerBlockPt = footerBlockPt, heightScale = heightScale, linerMinFracOfTrue = linerMinFracOfTrue,
        ).diaPtPerMm,
    )
    return BeforeAfterBands(
        original = top.copy(diaPtPerMmCap = diaPtPerMmCap),
        final = bottom.copy(diaPtPerMmCap = diaPtPerMmCap),
        dividerY = dividerY,
    )
}

/**
 * Where the rule between the two bands sits: midway down the page's drawing room once the footer
 * is taken off, so both bands get equal room for their drawing and the bottom band's extra height
 * is exactly the footer it hosts.
 */
internal fun beforeAfterDividerY(pageH: Float, footerBlockPt: Float): Float =
    SCHEMATIC_PAGE_MARGIN_PT + (pageH - 2f * SCHEMATIC_PAGE_MARGIN_PT - footerBlockPt) / 2f

/** The top band's label — the shaft as it came in. */
internal const val BEFORE_AFTER_ORIGINAL_BADGE = "ORIGINAL"

/** The bottom band's label — the shaft as it leaves. */
internal const val BEFORE_AFTER_FINAL_BADGE = "FINAL"

/** The rule between the bands, at 100% line weight. */
private const val BAND_DIVIDER_PT = 0.5f
private const val BAND_DIVIDER_COLOR = 0xFF808080.toInt()
