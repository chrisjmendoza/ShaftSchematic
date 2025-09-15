package com.android.shaftschematic.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.android.shaftschematic.data.EndInfo
import com.android.shaftschematic.data.MetaBlock
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout   // ✅ use the new object with compute()
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import java.io.OutputStream
import kotlin.math.roundToInt

object ShaftPdfComposer {

    fun write(
        context: Context,
        target: Uri,
        spec: ShaftSpecMm,
        pageWidthIn: Float = 11f,
        pageHeightIn: Float = 8.5f,
        drawingWidthIn: Float = 10f,
        drawingMaxHeightIn: Float = 2f,
        optsBase: RenderOptions,
        leftEnd: EndInfo = EndInfo(),
        middle: MetaBlock = MetaBlock(),
        rightEnd: EndInfo = EndInfo()
    ) {
        context.contentResolver.openOutputStream(target)?.use { os ->
            writeInternal(
                context, os, spec,
                pageWidthIn, pageHeightIn,
                drawingWidthIn, drawingMaxHeightIn,
                optsBase, leftEnd, middle, rightEnd
            )
        }
    }

    @JvmName("writeLegacy")
    fun write(
        context: Context,
        target: Uri,
        spec: ShaftSpecMm,
        pageWidthIn: Float,
        pageHeightIn: Float,
        drawingWidthIn: Float,
        drawingMaxHeightIn: Float,
        optsBase: RenderOptions
    ) = write(context, target, spec, pageWidthIn, pageHeightIn, drawingWidthIn, drawingMaxHeightIn, optsBase, EndInfo(), MetaBlock(), EndInfo())

    private fun writeInternal(
        context: Context,
        output: OutputStream,
        spec: ShaftSpecMm,
        pageWidthIn: Float,
        pageHeightIn: Float,
        drawingWidthIn: Float,
        drawingMaxHeightIn: Float,
        optsBase: RenderOptions,
        leftEnd: EndInfo,
        middle: MetaBlock,
        rightEnd: EndInfo
    ) {
        val ptsPerIn = 72f
        val pageWidthPts = (pageWidthIn * ptsPerIn).roundToInt()
        val pageHeightPts = (pageHeightIn * ptsPerIn).roundToInt()

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPts, pageHeightPts, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val dm = context.resources.displayMetrics
        val renderer = ShaftRenderer()

        // Virtual canvas for layout (px) based on requested physical inches and device DPI
        val widthPx = (drawingWidthIn * dm.xdpi).roundToInt()
        val heightPx = (drawingMaxHeightIn * dm.ydpi).roundToInt()

        // Layout compute (new API)
        val layout = ShaftLayout.compute(
            spec = spec,
            targetWidthPx = widthPx,
            maxHeightPx = heightPx,
            paddingPx = optsBase.paddingPx
        )

        // Use the same opts but lock in physical intent for the PDF export
        val opts = optsBase.copy(
            targetWidthInches = drawingWidthIn,
            maxHeightInches = drawingMaxHeightIn
        )

        // Scale PX → PDF points so width exactly equals drawingWidthIn on paper
        val drawLeftPts = (pageWidthPts - drawingWidthIn * ptsPerIn) / 2f
        val topMarginPts = 36f // 0.5"
        val scale = (drawingWidthIn * ptsPerIn) / widthPx.toFloat()

        canvas.save()
        canvas.translate(drawLeftPts, topMarginPts)
        canvas.scale(scale, scale)
        renderer.render(canvas, layout, opts)
        canvas.restore()

        // Title block position
        val drawingHeightPts = scale * heightPx.toFloat()
        val titleTopPts = topMarginPts + drawingHeightPts + 24f

        drawTitleBlock3Panels(
            canvas = canvas,
            pageWidthPts = pageWidthPts.toFloat(),
            topY = titleTopPts,
            leftEnd = leftEnd,
            middle = middle,
            rightEnd = rightEnd
        )

        pdf.finishPage(page)
        pdf.writeTo(output)
        pdf.close()
    }

    // --- Title block (unchanged) ---
    private fun drawTitleBlock3Panels(
        canvas: Canvas,
        pageWidthPts: Float,
        topY: Float,
        leftEnd: EndInfo,
        middle: MetaBlock,
        rightEnd: EndInfo
    ) {
        val ptsPerIn = 72f
        val wLeft = 3.5f * ptsPerIn
        val wMiddle = 4.0f * ptsPerIn
        val wRight = 3.5f * ptsPerIn
        val total = wLeft + wMiddle + wRight
        val startX = (pageWidthPts - total) / 2f

        val leftX = startX
        val midX = leftX + wLeft
        val rightX = midX + wMiddle

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            textAlign = Paint.Align.LEFT
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            textAlign = Paint.Align.LEFT
        }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }

        val rowHeight = 18f
        val boxHeight = rowHeight * 7
        val leftRect = RectF(leftX, topY, leftX + wLeft, topY + boxHeight)
        val midRect = RectF(midX, topY, midX + wMiddle, topY + boxHeight)
        val rightRect = RectF(rightX, topY, rightX + wRight, topY + boxHeight)
        canvas.drawRect(leftRect, boxPaint)
        canvas.drawRect(midRect, boxPaint)
        canvas.drawRect(rightRect, boxPaint)

        val hPad = 8f
        val headY = topY + 14f
        canvas.drawText("Aft / Left End", leftX + hPad, headY, headerPaint)
        canvas.drawText("Job / Meta", midX + hPad, headY, headerPaint)
        canvas.drawText("Fwd / Right End", rightX + hPad, headY, headerPaint)

        fun row(panelLeft: Float, label: String, value: String?, colWidth: Float, rowIndex: Int) {
            val baseY = topY + 14f + 6f + rowHeight * (rowIndex + 1)
            val labelX = panelLeft + hPad
            val valueX = panelLeft + colWidth * 0.45f
            canvas.drawText("$label:", labelX, baseY, labelPaint)
            val v = value?.takeIf { it.isNotBlank() } ?: "________"
            canvas.drawText(v, valueX, baseY, valuePaint)
        }

        // Left end
        row(leftX, "L.E.T.", leftEnd.let, wLeft, 0)
        row(leftX, "S.E.T.", leftEnd.set, wLeft, 1)
        row(leftX, "Taper Rate", leftEnd.taperRate, wLeft, 2)
        row(leftX, "K.W.", leftEnd.keyway, wLeft, 3)
        row(leftX, "Threads", leftEnd.threads, wLeft, 4)

        // Middle
        row(midX, "Customer", middle.customer, wMiddle, 0)
        row(midX, "Vessel", middle.vessel, wMiddle, 1)
        row(midX, "Job Number", middle.jobNumber, wMiddle, 2)
        row(midX, "Port/Starboard", middle.side, wMiddle, 3)
        row(midX, "Date", middle.date, wMiddle, 4)

        // Right end
        row(rightX, "L.E.T.", rightEnd.let, wRight, 0)
        row(rightX, "S.E.T.", rightEnd.set, wRight, 1)
        row(rightX, "Taper Rate", rightEnd.taperRate, wRight, 2)
        row(rightX, "K.W.", rightEnd.keyway, wRight, 3)
        row(rightX, "Threads", rightEnd.threads, wRight, 4)
    }
}
