package com.android.shaftschematic.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.android.shaftschematic.data.EndInfo
import com.android.shaftschematic.data.MetaBlock
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.data.ThreadSpec
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import com.android.shaftschematic.ui.drawing.render.RenderOptions
import com.android.shaftschematic.ui.drawing.render.ShaftLayout
import com.android.shaftschematic.ui.drawing.render.ShaftRenderer
import com.android.shaftschematic.ui.viewmodel.Units
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ShaftPdfComposer {

    /** Create one-page PDF and write to [target]. */
    suspend fun write(
        context: Context,
        target: Uri,
        spec: ShaftSpecMm,
        pageWidthIn: Float,
        pageHeightIn: Float,
        drawingWidthIn: Float,
        drawingMaxHeightIn: Float,
        optsBase: RenderOptions,
        units: Units,
        middle: MetaBlock
    ) {
        val pdf = android.graphics.pdf.PdfDocument()
        val ptsPerIn = 72f
        val pageWpx = (pageWidthIn * ptsPerIn).toInt()
        val pageHpx = (pageHeightIn * ptsPerIn).toInt()

        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWpx, pageHpx, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        // Outer content rect
        val pad = optsBase.paddingPx.toFloat()
        val contentLeft = pad
        val contentRight = pageWpx - pad
        val contentTop = pad
        val contentBottom = pageHpx - pad

        // Drawing rect (leave space at bottom for title block)
        val drawingWpx = drawingWidthIn * ptsPerIn
        val drawingHpx = drawingMaxHeightIn * ptsPerIn
        val drawLeft = contentLeft
        val drawTop = contentTop
        val drawRight = min(contentRight, drawLeft + drawingWpx)
        val drawBottom = min(contentBottom - 180f, drawTop + drawingHpx)

        // ---- Layout shaft (POSITIONAL args to avoid named-parameter mismatch) ----
        val layout = ShaftLayout.compute(
            spec,
            drawLeft,
            drawTop,
            drawRight,
            drawBottom
        )

        // ---- Render geometry (no grid on PDF) ----
        val renderer = ShaftRenderer()
        renderer.render(canvas, layout, optsBase.copy(showGrid = false, targetWidthInches = drawingWidthIn))

        // (Optional) dimensions/labels would be drawn here

        // ---- Derive end panels from spec ----
        val leftEnd = deriveEndInfo(spec, ReferenceEnd.AFT, units)
        val rightEnd = deriveEndInfo(spec, ReferenceEnd.FWD, units)

        // ---- Draw title block (3 panels) ----
        drawTitleBlock3Panels(
            canvas = canvas,
            pageLeft = contentLeft,
            pageRight = contentRight,
            pageBottom = contentBottom,
            middle = middle,
            left = leftEnd,
            right = rightEnd,
            textSizePx = optsBase.textSizePx * 0.9f
        )

        pdf.finishPage(page)
        context.contentResolver.openOutputStream(target)?.use { out -> pdf.writeTo(out) }
        pdf.close()
    }

    // ----------------- End-panel derivation -----------------

    private const val TOUCH_EPS_MM = 1.0f

    private fun deriveEndInfo(spec: ShaftSpecMm, end: ReferenceEnd, units: Units): EndInfo {
        val taper = pickEndTaper(spec, end)
        val thread = pickEndThread(spec, end)

        val (letStr, setStr, rateStr) = if (taper != null) {
            val d0 = taper.startDiaMm
            val d1 = taper.endDiaMm
            val len = max(1e-3f, taper.lengthMm)
            val bigger = max(d0, d1)
            val smaller = min(d0, d1)
            val let = formatLength(bigger, units) // show Ø using length formatter with unit
            val set = formatLength(smaller, units)
            val rate = formatTaperRate(d0, d1, len, units)
            Triple(let, set, rate)
        } else Triple("", "", "")

        val threadsStr = thread?.let {
            val dia = formatLength(it.majorDiaMm, units)
            val pitch = formatLength(it.pitchMm, units)
            val len = formatLength(it.lengthMm, units)
            "$dia × $pitch × $len"
        } ?: ""

        return EndInfo(
            let = letStr,
            set = setStr,
            taperRate = rateStr,
            keyway = "",           // not modeled yet
            threads = threadsStr
        )
    }

    /** Prefer explicit aft/forward taper; else a taper that touches the respective end. */
    private fun pickEndTaper(spec: ShaftSpecMm, end: ReferenceEnd): TaperSpec? =
        when (end) {
            ReferenceEnd.AFT -> {
                spec.aftTaper ?: spec.tapers
                    .filter { it.startFromAftMm <= TOUCH_EPS_MM }
                    .minByOrNull { it.startFromAftMm }
            }
            ReferenceEnd.FWD -> {
                val overall = spec.overallLengthMm
                spec.forwardTaper ?: spec.tapers
                    .filter { (it.startFromAftMm + it.lengthMm) >= overall - TOUCH_EPS_MM }
                    .maxByOrNull { it.startFromAftMm }
            }
        }

    /** Prefer a thread with endLabel=AFT/FWD; else the thread nearest that end. */
    private fun pickEndThread(spec: ShaftSpecMm, end: ReferenceEnd): ThreadSpec? {
        spec.threads.firstOrNull { it.endLabel.equals(end.name, ignoreCase = true) }?.let { return it }
        if (spec.threads.isEmpty()) return null
        return when (end) {
            ReferenceEnd.AFT -> spec.threads.minByOrNull { it.startFromAftMm }
            ReferenceEnd.FWD -> {
                val overall = spec.overallLengthMm
                spec.threads.minByOrNull { abs((it.startFromAftMm + it.lengthMm) - overall) }
            }
        }
    }

    // ----------------- Formatting -----------------

    private fun formatLength(mm: Float, units: Units): String =
        if (units == Units.MM) {
            if (mm >= 100f) "${mm.toInt()} mm" else String.format("%.1f mm", mm)
        } else {
            val inches = mm / 25.4f
            String.format("%.3f in", inches).trimZeros()
        }

    private fun formatTaperRate(d0mm: Float, d1mm: Float, lenMm: Float, units: Units): String {
        val deltaMm = abs(d1mm - d0mm)
        return if (units == Units.MM) {
            val r = deltaMm / lenMm
            String.format("%.4f mm/mm", r).trimZeros()
        } else {
            val r = (deltaMm / 25.4f) / (lenMm / 25.4f)
            String.format("%.4f in/in", r).trimZeros()
        }
    }

    private fun String.trimZeros(): String =
        this.replace(Regex("(\\.\\d*?)0+(\\s*[a-zA-Z/]+)$"), "$1$2")
            .replace(Regex("\\.(\\s*[a-zA-Z/]+)$"), "$1")

    // ----------------- Title block (3 panels) -----------------

    fun drawTitleBlock3Panels(
        canvas: android.graphics.Canvas,
        pageLeft: Float,
        pageRight: Float,
        pageBottom: Float,
        middle: MetaBlock,
        left: EndInfo,
        right: EndInfo,
        textSizePx: Float
    ) {
        val pad = 12f
        val boxH = 140f
        val gap = 10f
        val colW = (pageRight - pageLeft - gap * 2) / 3f
        val leftX = pageLeft
        val midX = leftX + colW + gap
        val rightX = midX + colW + gap
        val top = pageBottom - boxH

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 2f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            textSize = textSizePx
        }

        // column boxes
        canvas.drawRect(leftX, top, leftX + colW, pageBottom, stroke)
        canvas.drawRect(midX, top, midX + colW, pageBottom, stroke)
        canvas.drawRect(rightX, top, rightX + colW, pageBottom, stroke)

        fun drawLabelValueBox(x: Float, title: String, lines: List<Pair<String, String>>) {
            var y = top + pad + text.textSize
            canvas.drawText(title, x + pad, y, text)
            y += text.textSize * 0.7f
            val rowGap = text.textSize * 1.15f
            for ((lbl, v) in lines) {
                y += rowGap
                canvas.drawText("$lbl:", x + pad, y, text)
                if (v.isNotBlank()) {
                    canvas.drawText(v, x + pad + text.measureText("$lbl: ") + 6f, y, text)
                }
            }
        }

        drawLabelValueBox(leftX, "AFT / LEFT END", listOf(
            "L.E.T." to (left.let ?: ""),
            "S.E.T." to (left.set ?: ""),
            "Taper" to (left.taperRate ?: ""),
            "K.W." to (left.keyway ?: ""),
            "Threads" to (left.threads ?: "")
        ))

        drawLabelValueBox(midX, "JOB / META", listOf(
            "Customer" to middle.customer,
            "Vessel" to middle.vessel,
            "Job #" to middle.jobNumber,
            "Port/Stbd" to middle.side,
            "Date" to middle.date
        ))

        drawLabelValueBox(rightX, "FWD / RIGHT END", listOf(
            "L.E.T." to (right.let ?: ""),
            "S.E.T." to (right.set ?: ""),
            "Taper" to (right.taperRate ?: ""),
            "K.W." to (right.keyway ?: ""),
            "Threads" to (right.threads ?: "")
        ))
    }
}
