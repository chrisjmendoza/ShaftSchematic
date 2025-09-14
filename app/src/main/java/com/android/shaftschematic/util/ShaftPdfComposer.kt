package com.android.shaftschematic.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import java.io.OutputStream
import kotlin.math.roundToInt

object ShaftPdfComposer {

    /**
     * Writes a single-page PDF with the shaft drawing at true physical size.
     *
     * @param pageWidthIn   e.g., 11f
     * @param pageHeightIn  e.g., 8.5f
     * @param drawingWidthIn   e.g., 10f (your requirement)
     * @param drawingMaxHeightIn e.g., 2f (your requirement)
     */
    fun write(
        context: Context,
        target: Uri,
        spec: ShaftSpecMm,
        pageWidthIn: Float = 11f,
        pageHeightIn: Float = 8.5f,
        drawingWidthIn: Float = 10f,
        drawingMaxHeightIn: Float = 2f,
        optsBase: RenderOptions
    ) {
        context.contentResolver.openOutputStream(target)?.use { os ->
            writeInternal(context, os, spec, pageWidthIn, pageHeightIn, drawingWidthIn, drawingMaxHeightIn, optsBase)
        }
    }

    private fun writeInternal(
        context: Context,
        output: OutputStream,
        spec: ShaftSpecMm,
        pageWidthIn: Float,
        pageHeightIn: Float,
        drawingWidthIn: Float,
        drawingMaxHeightIn: Float,
        optsBase: RenderOptions
    ) {
        // PDF coordinate system: 72 points per inch
        val ptsPerIn = 72f
        val pageWidthPts = (pageWidthIn * ptsPerIn).roundToInt()
        val pageHeightPts = (pageHeightIn * ptsPerIn).roundToInt()

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPts, pageHeightPts, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Layout using display metrics (for inch→px conversions); we’ll treat 1pt ~ 1px in PDF canvas
        val dm = context.resources.displayMetrics
        val layout = ShaftLayout(dm)
        val renderer = ShaftRenderer()

        // We want the drawing to be exactly 10" wide and ≤2" tall.
        // We'll create a virtual canvas area in px that corresponds to those inches using dm.xdpi/dm.ydpi.
        val widthPx = (drawingWidthIn * dm.xdpi).roundToInt()
        val heightPx = (drawingMaxHeightIn * dm.ydpi).roundToInt() + (optsBase.paddingPx * 2)

        val opts = optsBase.copy(
            targetWidthInches = drawingWidthIn,
            maxHeightInches = drawingMaxHeightIn
        )

        val layoutResult = layout.layout(
            spec = spec,
            canvasWidthPx = widthPx + opts.paddingPx * 2,   // include padding space
            canvasHeightPx = heightPx + opts.paddingPx * 2,
            opts = opts
        )

        // Compute where to place the drawing on the PDF (centered)
        val drawLeftPts = (pageWidthPts - drawingWidthIn * ptsPerIn) / 2f
        val topMarginPts = 36f // 0.5"
        canvas.save()
        // Scale from px (our layout space) to PDF points so width becomes exactly drawingWidthIn inches
        val scaleX = (drawingWidthIn * ptsPerIn) / (widthPx.toFloat())
        val scaleY = scaleX // uniform scale
        canvas.translate(drawLeftPts, topMarginPts)
        canvas.scale(scaleX, scaleY)

        // Render using the same renderer
        renderer.render(canvas, layoutResult, opts)

        canvas.restore()
        pdf.finishPage(page)
        pdf.writeTo(output)
        pdf.close()
    }
}