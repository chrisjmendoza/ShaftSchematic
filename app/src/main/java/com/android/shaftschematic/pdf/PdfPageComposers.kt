package com.android.shaftschematic.pdf

import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.WEAR_TRACE_MAX_DEPTH_FRAC
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem

// The composers' PdfDocument.Page entry points: each reads the page's canvas and size and hands
// them to the canvas-level composer the sheet is drawn by.

/**
 * PDF Composer — draws the shaft preview and export annotations.
 *
 * Units: millimeters (mm) in model space. Paper units are points (1/72").
 * Axis: AFT → FWD. Measurement origin (x=0) is the first counted AFT surface,
 * respecting end threads flagged `excludeFromOAL` (those shift the measurement window).
 *
 * Layout notes
 * - The page is explicitly painted white for viewer compatibility.
 * - A fixed footer band is reserved at the bottom of the page; all geometry is clamped
 *   to stay above it.
 * - Vertical placement uses a small paper-space bias (see `SHAFT_DOWN_PT`) but is still
 *   clamped to keep the schematic and footer readable.
 *
 * This composer:
 *  • Renders bodies (with centered long-break compression), tapers, threads, and liners.
 *  • Draws **liner-only dimensions** in the PDF: for each liner we show
 *      – offset from SET → near edge, and
 *      – liner length,
 *    with stacked rails; the **top rail is OAL only**.
 *  • Uses PDF-specific styling; preview-only styling options are not consumed here.
 */
fun composeShaftPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs = PdfPrefs(),
    options: PdfExportOptions = PdfExportOptions(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    heightScale: Float = 1.0f,
    linerMinFracOfTrue: Float = 0f,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) = composeShaftPdfOnCanvas(
    c = page.canvas,
    pageW = page.info.pageWidth.toFloat(),
    pageH = page.info.pageHeight.toFloat(),
    spec = spec, unit = unit, project = project, appVersion = appVersion, filename = filename,
    pdfPrefs = pdfPrefs, options = options, resolvedComponents = resolvedComponents,
    lineThicknessScale = lineThicknessScale, heightScale = heightScale,
    linerMinFracOfTrue = linerMinFracOfTrue, displayUnits = displayUnits,
)

/**
 * RunoutPdfComposer
 *
 * Generates a runout measurement sheet PDF page for the given shaft specification, in one
 * of two selectable layouts (landscape US Letter, 792 × 612 pt):
 *
 * ## Consolidated ONE-SHEET (`consolidated = true`, the default)
 *
 * ```
 * ┌─── |←──────────────── OAL (top rail) ────────────────→| ─────────────────┐
 * │      |←─ liner/taper dimension tiers (RailPlanner) ─→|                    │
 * │  [shaft profile: compressed, wear bands/pits, in-profile Ø values]        │
 * │   ╲  ╲  ╲  ╲   ╲  ╲  ╲   ╲  ╲  ╲     ← leader lines (straight/dogleg)  │
 * │   ○     ○      ○      ○      ○        ← row-0 bubbles (closer to shaft) │
 * │      ○      ○     ○      ○       ○    ← row-1 bubbles (alternating)     │
 * │  TIR's taken looking: ______________________              ((◎))  ← coupling │
 * │  [footer: AFT taper | Customer/Vessel/Job#/Date/Side | FWD taper]  end view │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Classic runout sheet (`consolidated = false`)
 *
 * ```
 * ┌─── header: Customer / Vessel / Job# / Date / Side ───────────────────────┐
 * │  |←────────── OAL (AFT SET → FWD SET) ────────────────────→|  ← raised  │
 * │  |  [shaft profile: bodies, tapers, liners, threads, breaks] |  witness  │
 * │                                                                            │
 * │   ╲  ╲  ╲  ╲   ╲  ╲  ╲   ╲  ╲  ╲     ← leader lines (straight/dogleg)  │
 * │   ○     ○      ○      ○      ○      ← bubbles, same engine as above     │
 * │  TIR's taken looking: _______________________                             │
 * └───────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * No dimension rails, no footer, and no wear info in classic mode — the standalone sheet
 * the Runout tab prints (and the Runout document in the Output tab's batch export). Both
 * modes share the compressed profile, the bubble engine, and the "Shaft height" slider.
 *
 * ## Bubble placement convention
 * - **Tapers**: two stations inset from each edge by [RunoutConfig.RUNOUT_EDGE_INSET_MM].
 *   Readings taken right on a taper's SET or LET face are unreliable.
 * - **Liners**: same inset convention as tapers — worn areas rarely reach a liner's very
 *   edges, so near-edge readings are the best runout spots (on-device rule).
 * - **Bodies**: stations spread evenly across each body's DRAWN span with equal gaps, ends
 *   included (`k · width / (count + 1)` in page x, inverted to physical mm through the
 *   compressed mapping) — a body surface is uniform, so the exact physical spot is free, and
 *   drawn-even placement keeps the sheet readable where physical spacing would bunch into a
 *   foreshortened run (on-device request). Cell midpoints read as crowding the faces.
 * - **Threads**: no stations (threads are not measured for runout). Still drawn as
 *   hatched envelopes for visual reference; excluded-from-OAL threads sit outside the
 *   SET-to-SET arrows at their physical position, and the x map spans them
 *   (`drawnExtentMm`) so they stay on the page.
 *
 * ## Bubble rows and leader routing
 * Placement is delegated to the shared engine in `geom/RunoutBubbleLayout.kt` (also used
 * by the RunoutRoute canvas preview, so the two renderings are identical). Within each
 * component, consecutive stations alternate between row 0 (closer to the shaft) and
 * row 1 (further away) — matching the hand-drawn convention in the shop reference
 * drawings — and the engine guarantees that no bubble touches another bubble and no
 * leader line crosses a bubble or another leader. See RunoutBubbleLayout's KDoc for the
 * spacing invariants and the dogleg fallback.
 *
 * ## Keyway reference marker
 * An open square notch straddling each circle's rim at 12-o'clock indicates
 * keyway-at-top centre, matching the hand-drawn shop sheets. A recorded high spot prints
 * as a short dash straddling the rim at its clock position; the notch is the 12-o'clock
 * reference.
 *
 * ## Coupling end view
 * Both sheets can carry the shop's hand-sketched coupling face in the bottom-right of the
 * TIR band — see [drawCouplingFace]. Elected per job (`RunoutConfig.showCouplingFace`, off
 * by default) and gated with the bubbles, since it is runout content.
 *
 * @param page     Target PDF page (US Letter landscape, already started).
 * @param spec     Shaft specification in millimeters.
 * @param config   Runout preferences — bubble count overrides and TIR direction label.
 * @param project  Job information (customer, vessel, job#, side).
 * @param unit     Display unit for the OAL dimension label.
 * @param resolvedComponents Resolved component list from the ViewModel. When provided,
 *                 resolved bodies (subtracted against tapers/liners, split/merged, with
 *                 auto-fill gaps) replace `spec.bodies` for the profile and the stations —
 *                 same contract as `composeShaftPdf`. Raw spec bodies may legally overlap
 *                 tapers/liners; resolution is what turns them into drawable segments.
 */
fun composeRunoutPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    config: RunoutConfig,
    project: ProjectInfo,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    lineThicknessScale: Float = 1.0f,
    runoutReadings: RunoutReadings = RunoutReadings(),
    runoutStationPlacements: RunoutStationPlacements = RunoutStationPlacements(),
    wearRecord: WearRecord = WearRecord(),
    blankValues: Boolean = false,
    consolidated: Boolean = true,
    includeBubbles: Boolean = true,
    includeWearInfo: Boolean = true,
) = composeRunoutPdfOnCanvas(
    c = page.canvas,
    pageW = page.info.pageWidth.toFloat(),
    pageH = page.info.pageHeight.toFloat(),
    spec = spec, config = config, project = project, unit = unit,
    displayUnits = displayUnits, pdfPrefs = pdfPrefs, resolvedComponents = resolvedComponents,
    lineThicknessScale = lineThicknessScale, runoutReadings = runoutReadings,
    runoutStationPlacements = runoutStationPlacements, wearRecord = wearRecord,
    blankValues = blankValues, consolidated = consolidated,
    includeBubbles = includeBubbles, includeWearInfo = includeWearInfo,
)

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
 * The wear / inspection record sheet on [page] (US Letter landscape, already started) — drawn by
 * [composeWearPdfOnCanvas] onto the page's canvas at the page's size.
 */
fun composeWearPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    wearRecord: WearRecord = WearRecord(),
    lineThicknessScale: Float = 1.0f,
    blankValues: Boolean = false,
    traceDepthFrac: Float = WEAR_TRACE_MAX_DEPTH_FRAC,
    heightScale: Float = 1f,
) = composeWearPdfOnCanvas(
    c = page.canvas,
    pageW = page.info.pageWidth.toFloat(),
    pageH = page.info.pageHeight.toFloat(),
    spec = spec, project = project, unit = unit, displayUnits = displayUnits, pdfPrefs = pdfPrefs,
    resolvedComponents = resolvedComponents, wearRecord = wearRecord,
    lineThicknessScale = lineThicknessScale, blankValues = blankValues,
    traceDepthFrac = traceDepthFrac, heightScale = heightScale,
)

/**
 * The undercut record sheet on [page] (US Letter, already started; portrait when its height
 * exceeds its width) — drawn by [composeUndercutPdfOnCanvas] onto the page's canvas at the
 * page's size.
 */
fun composeUndercutPdf(
    page: PdfDocument.Page,
    spec: ShaftSpec,
    project: ProjectInfo,
    unit: UnitSystem,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    pdfPrefs: PdfPrefs = PdfPrefs(),
    resolvedComponents: List<ResolvedComponent>? = null,
    undercutRecord: UndercutRecord = UndercutRecord(),
    lineThicknessScale: Float = 1.0f,
    blankValues: Boolean = false,
) = composeUndercutPdfOnCanvas(
    canvas = page.canvas,
    pageW = page.info.pageWidth,
    pageH = page.info.pageHeight,
    spec = spec, project = project, unit = unit, displayUnits = displayUnits, pdfPrefs = pdfPrefs,
    resolvedComponents = resolvedComponents, undercutRecord = undercutRecord,
    lineThicknessScale = lineThicknessScale, blankValues = blankValues,
)
