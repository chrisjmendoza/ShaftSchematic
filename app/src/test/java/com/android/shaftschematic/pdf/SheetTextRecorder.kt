package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument

/** One string a composer inked, with its metric box mapped through the canvas transform. */
internal class RecordedText(val text: String, val rect: RectF, val textSize: Float)

/** One rectangle a composer filled or stroked, in page coordinates (unrotated draws only). */
internal class RecordedRect(val rect: RectF, val style: Paint.Style, val color: Int, val alpha: Int)

/** One straight stroke a composer drew, with its paint's alpha and width. */
internal class RecordedLine(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val alpha: Int, val strokeWidth: Float)

/**
 * A bitmap canvas that records every string, rectangle and straight line drawn through it — capture
 * for composer-level tests that ask WHAT a sheet prints rather than how it looks.
 */
internal class SheetTextRecorder(widthPt: Float, heightPt: Float) :
    Canvas(Bitmap.createBitmap(widthPt.toInt(), heightPt.toInt(), Bitmap.Config.ARGB_8888)) {

    val texts = mutableListOf<RecordedText>()
    val rects = mutableListOf<RecordedRect>()
    val lines = mutableListOf<RecordedLine>()

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        rects += RecordedRect(RectF(left, top, right, bottom), paint.style, paint.color, paint.alpha)
        super.drawRect(left, top, right, bottom, paint)
    }

    override fun drawRect(rect: RectF, paint: Paint) {
        rects += RecordedRect(RectF(rect), paint.style, paint.color, paint.alpha)
        super.drawRect(rect, paint)
    }

    override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        lines += RecordedLine(startX, startY, stopX, stopY, paint.alpha, paint.strokeWidth)
        super.drawLine(startX, startY, stopX, stopY, paint)
    }

    /** Every recorded string, in draw order. */
    val strings: List<String> get() = texts.map { it.text }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        record(text, x, y, paint)
        super.drawText(text, x, y, paint)
    }

    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        record(text.substring(start, end), x, y, paint)
        super.drawText(text, start, end, x, y, paint)
    }

    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        record(text.subSequence(start, end).toString(), x, y, paint)
        super.drawText(text, start, end, x, y, paint)
    }

    override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
        record(String(text, index, count), x, y, paint)
        super.drawText(text, index, count, x, y, paint)
    }

    private fun record(s: String, x: Float, y: Float, paint: Paint) {
        if (s.isBlank()) return
        val w = paint.measureText(s)
        val left = when (paint.textAlign) {
            Paint.Align.CENTER -> x - w / 2f
            Paint.Align.RIGHT -> x - w
            else -> x
        }
        val fm = paint.fontMetrics
        val r = RectF(left, y + fm.ascent, left + w, y + fm.descent)
        val m = Matrix()
        @Suppress("DEPRECATION")
        getMatrix(m)
        m.mapRect(r)
        texts += RecordedText(s, r, paint.textSize)
    }
}

/** A `PdfDocument.Page` over [c] — the page constructor is private. */
internal fun pageOnCanvas(c: Canvas, widthPt: Int, heightPt: Int): PdfDocument.Page {
    val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, 1).create()
    val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(c, info)
}
