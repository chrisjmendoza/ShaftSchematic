package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.max
import kotlin.math.roundToInt

internal data class PdfPreviewResult(
    val bitmap: Bitmap,
    val pageWidthPt: Int,
    val pageHeightPt: Int,
)

internal fun renderPdfPreviewPage(
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    appVersion: String,
    filename: String,
    pdfPrefs: com.android.shaftschematic.settings.PdfPrefs,
    options: PdfExportOptions,
    resolvedComponents: List<ResolvedComponent>?,
    pageWidthPt: Int = 792,
    pageHeightPt: Int = 612,
    renderScale: Float = 2f,
): PdfPreviewResult {
    val scale = max(0.1f, renderScale)
    val w = (pageWidthPt * scale).roundToInt()
    val h = (pageHeightPt * scale).roundToInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)

    composeShaftPdfOnCanvas(
        canvas = canvas,
        pageWidthPt = pageWidthPt,
        pageHeightPt = pageHeightPt,
        spec = spec,
        unit = unit,
        project = project,
        appVersion = appVersion,
        filename = filename,
        pdfPrefs = pdfPrefs,
        options = options,
        resolvedComponents = resolvedComponents,
    )

    return PdfPreviewResult(bitmap = bitmap, pageWidthPt = pageWidthPt, pageHeightPt = pageHeightPt)
}
