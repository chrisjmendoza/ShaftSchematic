package com.android.shaftschematic.ui.nav

import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.PdfExportOptions
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
 * sheet judged on screen is byte-for-byte the sheet that prints and the sheet that is written.
 */

/**
 * The two sheets a schematic surface can produce.
 *
 * [PLAIN] is the schematic as it has always been. [WITH_BUBBLES] is the consolidated sheet's
 * **Schematic + Runout** variant over the same geometry — the pre-ship measurement copy, only
 * ever reachable on the FINAL drawing.
 */
internal enum class FinalSheetKind { PLAIN, WITH_BUBBLES }

/**
 * Which sheet a surface draws. Bubbles are a FINAL-drawing election only: the original
 * schematic's stations are the Runout and Consolidated Output tabs' documents, and adding a
 * second way to print them would leave two sheets claiming to be the runout record.
 */
internal fun finalSheetKind(target: SpecTarget, finalRunoutBubbles: Boolean): FinalSheetKind =
    if (target == SpecTarget.FINAL && finalRunoutBubbles) {
        FinalSheetKind.WITH_BUBBLES
    } else {
        FinalSheetKind.PLAIN
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
 * [runoutConfig] arrives already tuned (the preview's live sliders) and supplies the drawn
 * height and liner-compression terms to BOTH branches, so switching the election never changes
 * the size of the drawing.
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
    }
}
