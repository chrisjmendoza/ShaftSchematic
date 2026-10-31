package com.android.shaftschematic.ui.nav

import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.composeBeforeAfterPdf
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.viewmodel.SpecTarget
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem

/**
 * FinalSheetCompose — which sheet a schematic surface actually draws, in ONE place.
 *
 * The schematic PDF has three consumers that must never disagree: the preview's render loop,
 * the preview's Print button, and the SAF export. They all call [composeSchematicSheet], so a
 * sheet judged on screen is byte-for-byte the sheet that prints and the sheet that is written —
 * whichever of the three [FinalSheetKind]s it is.
 */

/**
 * The three sheets a schematic surface can produce.
 *
 * [PLAIN] is the schematic as it has always been. [WITH_BUBBLES] is the consolidated sheet's
 * **Schematic + Runout** variant over the same geometry — the pre-ship measurement copy, only
 * ever reachable on the FINAL drawing. [BEFORE_AFTER] is the FINAL drawing's comparison sheet:
 * the original shaft above the final one, each a whole schematic, under the final's footer.
 */
internal enum class FinalSheetKind { PLAIN, WITH_BUBBLES, BEFORE_AFTER }

/**
 * Which sheet a surface draws. Both elections are FINAL-drawing only: the original schematic's
 * stations are the Runout and Consolidated Output tabs' documents (a second way to print them
 * would leave two sheets claiming to be the runout record), and a before/after comparison needs a
 * final to compare against. The ORIGINAL target is therefore always [FinalSheetKind.PLAIN].
 *
 * Bubbles win over the before/after election: the bubbled sheet is the consolidated composer,
 * which has no band mode to stack the original above it, so with both elected the surface draws
 * [FinalSheetKind.WITH_BUBBLES].
 */
internal fun finalSheetKind(
    target: SpecTarget,
    finalRunoutBubbles: Boolean,
    finalBeforeAfter: Boolean,
): FinalSheetKind = when {
    target != SpecTarget.FINAL -> FinalSheetKind.PLAIN
    finalRunoutBubbles -> FinalSheetKind.WITH_BUBBLES
    finalBeforeAfter -> FinalSheetKind.BEFORE_AFTER
    else -> FinalSheetKind.PLAIN
}

/**
 * Composes the schematic sheet a surface asked for onto [page].
 *
 * Every input is already snapshotted by the caller — this runs on a binder thread under
 * `printShaftPdfPage`, and on an IO dispatcher under the preview's render loop.
 *
 * The bubbled sheet takes NO readings, NO dragged station placements, NO wear record and NO
 * station-count overrides: all four are keyed to the ORIGINAL geometry — they are the
 * inspection of the shaft that came in — so carrying them onto the final would print the old
 * shaft's measurements on the sheet the new ones get taken on. What prints is an empty grid of
 * default stations over the final drawing.
 *
 * The before/after sheet draws [spec] (the final) in its bottom band and [originalSpec] /
 * [originalResolved] in its top band; the two are read for that kind only, and it requires
 * [originalSpec].
 *
 * [runoutConfig] arrives already tuned (the preview's live sliders) and supplies the drawn
 * height and liner-compression terms to EVERY branch, so switching the election never changes
 * how the drawing is tuned.
 */
internal fun composeSchematicSheet(
    page: PdfDocument.Page,
    kind: FinalSheetKind,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: PdfPrefs,
    options: PdfExportOptions,
    resolvedComponents: List<ResolvedComponent>?,
    originalSpec: ShaftSpec? = null,
    originalResolved: List<ResolvedComponent>? = null,
    lineThicknessScale: Float,
    runoutConfig: RunoutConfig,
    displayUnits: DisplayUnits,
) {
    when (kind) {
        FinalSheetKind.PLAIN -> composeShaftPdf(
            page = page,
            spec = spec,
            unit = unit,
            project = project,
            appVersion = appVersion,
            filename = filename,
            pdfPrefs = pdfPrefs,
            options = options,
            resolvedComponents = resolvedComponents,
            lineThicknessScale = lineThicknessScale,
            heightScale = runoutConfig.heightScale,
            linerMinFracOfTrue = runoutConfig.linerMinFracOfTrue,
            displayUnits = displayUnits,
        )

        FinalSheetKind.WITH_BUBBLES -> composeRunoutPdf(
            page = page,
            spec = spec,
            config = runoutConfig.copy(componentOverrides = emptyMap()),
            project = project,
            unit = unit,
            displayUnits = displayUnits,
            pdfPrefs = pdfPrefs,
            resolvedComponents = resolvedComponents,
            lineThicknessScale = lineThicknessScale,
            blankValues = options.blankValues,
            consolidated = true,
            includeBubbles = true,
            includeWearInfo = false,
        )

        FinalSheetKind.BEFORE_AFTER -> composeBeforeAfterPdf(
            page = page,
            originalSpec = requireNotNull(originalSpec) { "before/after sheet needs the original" },
            finalSpec = spec,
            unit = unit,
            project = project,
            appVersion = appVersion,
            filename = filename,
            pdfPrefs = pdfPrefs,
            options = options,
            originalResolved = originalResolved,
            finalResolved = resolvedComponents,
            lineThicknessScale = lineThicknessScale,
            heightScale = runoutConfig.heightScale,
            linerMinFracOfTrue = runoutConfig.linerMinFracOfTrue,
            displayUnits = displayUnits,
        )
    }
}
