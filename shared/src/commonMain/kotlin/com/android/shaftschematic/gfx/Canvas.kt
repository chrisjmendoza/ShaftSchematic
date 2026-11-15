package com.android.shaftschematic.gfx

/**
 * The drawing surface every sheet and every shared draw helper paints onto.
 *
 * On Android this IS `android.graphics.Canvas` (an `actual typealias`), so a composer makes the
 * very calls it would make against the Android class, a PDF page's canvas and a Compose
 * `nativeCanvas` pass straight in, and a test's recording `Canvas` subclass sees every call. The
 * members below are exactly the ones the drawing code uses; adding a call means adding it here.
 */
expect open class Canvas {
    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint)
    fun drawText(text: String, x: Float, y: Float, paint: Paint)
    fun drawPath(path: Path, paint: Paint)
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)
    fun drawRect(rect: RectF, paint: Paint)
    fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint)
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint)
    fun drawOval(oval: RectF, paint: Paint)
    fun drawColor(color: Int)

    fun save(): Int
    fun restore()
    fun restoreToCount(saveCount: Int)
    fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean
    fun clipPath(path: Path): Boolean
    fun rotate(degrees: Float, px: Float, py: Float)
    fun translate(dx: Float, dy: Float)
}
