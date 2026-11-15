package com.android.shaftschematic.gfx

import android.graphics.Bitmap

// The graphics layer on Android is the Android graphics classes themselves.

actual typealias Canvas = android.graphics.Canvas
actual typealias Paint = android.graphics.Paint
actual typealias PaintStyle = android.graphics.Paint.Style
actual typealias PaintAlign = android.graphics.Paint.Align
actual typealias PaintCap = android.graphics.Paint.Cap
actual typealias FontMetrics = android.graphics.Paint.FontMetrics
actual typealias Path = android.graphics.Path
actual typealias PathEffect = android.graphics.PathEffect
actual typealias DashPathEffect = android.graphics.DashPathEffect
actual typealias RectF = android.graphics.RectF
actual typealias Rect = android.graphics.Rect
actual typealias Typeface = android.graphics.Typeface

// Each reads the Java field of the same name (a member always wins over an extension).
actual val FontMetrics.ascent: Float get() = this.ascent
actual val FontMetrics.descent: Float get() = this.descent
actual val RectF.left: Float get() = this.left
actual val RectF.top: Float get() = this.top
actual val RectF.right: Float get() = this.right
actual val RectF.bottom: Float get() = this.bottom
actual val Rect.top: Int get() = this.top

actual fun <R> withArgbScratchCanvas(width: Int, height: Int, block: (Canvas) -> R): R {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        return block(Canvas(bmp))
    } finally {
        bmp.recycle()
    }
}

actual fun alphaScratchCanvas(width: Int, height: Int): Canvas =
    Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8))

actual fun fallbackSansMediumTypeface(): Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

actual fun fallbackSansTypeface(): Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
