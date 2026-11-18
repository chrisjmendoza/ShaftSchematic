package com.android.shaftschematic.gfx

// The graphics layer on iOS: plain Kotlin state holders. Pixels and text metrics come from the
// iOS host, which supplies a [CanvasBackend] for each canvas it hands the drawing code and one
// process-wide [TextMeasurer] through [TextMeasurement] — both implemented against
// CoreGraphics / CoreText.

/**
 * Receives every draw and state call an iOS [Canvas] makes, in order. The iOS host implements it
 * against a CoreGraphics context; [save] returns the save count before the save, as
 * [Canvas.save] promises.
 */
interface CanvasBackend {
    fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint)
    fun drawText(text: String, x: Float, y: Float, paint: Paint)
    fun drawPath(path: Path, paint: Paint)
    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint)
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

/**
 * Measures text as a [Paint] would set it (its typeface, size, fake-bold and alignment-free
 * advance). Supplied by the iOS host against CoreText.
 */
interface TextMeasurer {
    /** The advance width of [text]. */
    fun measureText(paint: Paint, text: String): Float

    /** The face's ascent (negative, above the baseline) and descent (positive) at the paint's size. */
    fun fontMetrics(paint: Paint, into: FontMetrics)

    /** The tight integer bounds of `text[start, end)` relative to the baseline origin. */
    fun textBounds(paint: Paint, text: String, start: Int, end: Int, into: Rect)
}

/** The process-wide [TextMeasurer] every iOS [Paint] measures through; the host installs it at startup. */
object TextMeasurement {
    var measurer: TextMeasurer? = null

    internal fun require(): TextMeasurer =
        measurer ?: error("No TextMeasurer installed: the iOS host must set TextMeasurement.measurer")
}

actual open class Canvas(private val backend: CanvasBackend) {
    actual fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) =
        backend.drawLine(startX, startY, stopX, stopY, paint)
    actual fun drawText(text: String, x: Float, y: Float, paint: Paint) = backend.drawText(text, x, y, paint)
    actual fun drawPath(path: Path, paint: Paint) = backend.drawPath(path, paint)
    actual fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        backend.drawRect(left, top, right, bottom, paint)
    actual fun drawRect(rect: RectF, paint: Paint) =
        backend.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
    actual fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) =
        backend.drawArc(oval, startAngle, sweepAngle, useCenter, paint)
    actual fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = backend.drawCircle(cx, cy, radius, paint)
    actual fun drawOval(oval: RectF, paint: Paint) = backend.drawOval(oval, paint)
    actual fun drawColor(color: Int) = backend.drawColor(color)

    actual fun save(): Int = backend.save()
    actual fun restore() = backend.restore()
    actual fun restoreToCount(saveCount: Int) = backend.restoreToCount(saveCount)
    actual fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean =
        backend.clipRect(left, top, right, bottom)
    actual fun clipPath(path: Path): Boolean = backend.clipPath(path)
    actual fun rotate(degrees: Float, px: Float, py: Float) = backend.rotate(degrees, px, py)
    actual fun translate(dx: Float, dy: Float) = backend.translate(dx, dy)
}

/** A backend that draws nothing and only keeps the save count — for dry passes that measure. */
private class DiscardingCanvasBackend : CanvasBackend {
    private var saveCount = 1
    override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {}
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {}
    override fun drawPath(path: Path, paint: Paint) {}
    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {}
    override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {}
    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {}
    override fun drawOval(oval: RectF, paint: Paint) {}
    override fun drawColor(color: Int) {}
    override fun save(): Int = saveCount++
    override fun restore() { if (saveCount > 1) saveCount-- }
    override fun restoreToCount(saveCount: Int) { this.saveCount = saveCount.coerceAtLeast(1) }
    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean = true
    override fun clipPath(path: Path): Boolean = true
    override fun rotate(degrees: Float, px: Float, py: Float) {}
    override fun translate(dx: Float, dy: Float) {}
}

actual fun <R> withArgbScratchCanvas(width: Int, height: Int, block: (Canvas) -> R): R =
    block(Canvas(DiscardingCanvasBackend()))

actual fun alphaScratchCanvas(width: Int, height: Int): Canvas = Canvas(DiscardingCanvasBackend())

actual enum class PaintStyle { FILL, STROKE, FILL_AND_STROKE }

actual enum class PaintAlign { LEFT, CENTER, RIGHT }

actual enum class PaintCap { BUTT, ROUND, SQUARE }

actual open class FontMetrics {
    var ascent: Float = 0f
    var descent: Float = 0f
}

actual val FontMetrics.ascent: Float get() = this.ascent
actual val FontMetrics.descent: Float get() = this.descent

/** A named font face; the iOS host resolves [name] to a CoreText font. */
actual open class Typeface(val name: String)

actual fun fallbackSansMediumTypeface(): Typeface = Typeface("sans-serif-medium")

actual fun fallbackSansTypeface(): Typeface = Typeface("sans-serif")

actual open class PathEffect

actual open class DashPathEffect actual constructor(val intervals: FloatArray, val phase: Float) : PathEffect()

/** One recorded [Path] segment. */
sealed class PathSegment {
    data class MoveTo(val x: Float, val y: Float) : PathSegment()
    data class LineTo(val x: Float, val y: Float) : PathSegment()
    data class CubicTo(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float,
    ) : PathSegment()
    data object Close : PathSegment()
}

/** The outline as the segments it was built from, in order, for the backend to replay. */
actual open class Path {
    private val recorded = mutableListOf<PathSegment>()

    val segments: List<PathSegment> get() = recorded

    actual constructor()

    actual constructor(src: Path?) {
        if (src != null) recorded += src.recorded
    }

    actual fun moveTo(x: Float, y: Float) { recorded += PathSegment.MoveTo(x, y) }
    actual fun lineTo(x: Float, y: Float) { recorded += PathSegment.LineTo(x, y) }
    actual fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        recorded += PathSegment.CubicTo(x1, y1, x2, y2, x3, y3)
    }
    actual fun close() { recorded += PathSegment.Close }
}

actual open class RectF actual constructor(
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
) {
    actual fun width(): Float = right - left
    actual fun height(): Float = bottom - top
}

actual val RectF.left: Float get() = this.left
actual val RectF.top: Float get() = this.top
actual val RectF.right: Float get() = this.right
actual val RectF.bottom: Float get() = this.bottom

actual class Rect actual constructor() {
    var left: Int = 0
    var top: Int = 0
    var right: Int = 0
    var bottom: Int = 0
}

actual val Rect.top: Int get() = this.top
actual val Rect.bottom: Int get() = this.bottom

/**
 * Paint state. Defaults are Android's: 12 pt text, hairline stroke, fill style, opaque black, left
 * aligned, butt caps. The copy constructor copies every field; [setAlpha] replaces only the alpha
 * channel of the colour. Text is measured through [TextMeasurement].
 */
actual open class Paint {
    private var flags: Int = 0
    private var textSizeValue: Float = 12f
    private var strokeWidthValue: Float = 0f
    private var styleValue: PaintStyle = PaintStyle.FILL
    private var colorValue: Int = Color.BLACK
    private var textAlignValue: PaintAlign = PaintAlign.LEFT
    private var typefaceValue: Typeface? = null
    private var fakeBoldText: Boolean = false
    private var strokeCapValue: PaintCap = PaintCap.BUTT
    private var pathEffectValue: PathEffect? = null

    actual constructor()

    actual constructor(flags: Int) {
        this.flags = flags
    }

    actual constructor(paint: Paint) {
        flags = paint.flags
        textSizeValue = paint.textSizeValue
        strokeWidthValue = paint.strokeWidthValue
        styleValue = paint.styleValue
        colorValue = paint.colorValue
        textAlignValue = paint.textAlignValue
        typefaceValue = paint.typefaceValue
        fakeBoldText = paint.fakeBoldText
        strokeCapValue = paint.strokeCapValue
        pathEffectValue = paint.pathEffectValue
    }

    actual fun getTextSize(): Float = textSizeValue
    actual fun setTextSize(textSize: Float) { textSizeValue = textSize }
    actual fun getStrokeWidth(): Float = strokeWidthValue
    actual fun setStrokeWidth(width: Float) { strokeWidthValue = width }
    actual fun getStyle(): PaintStyle = styleValue
    actual fun setStyle(style: PaintStyle) { styleValue = style }
    actual fun getColor(): Int = colorValue
    actual fun setColor(color: Int) { colorValue = color }
    actual fun getAlpha(): Int = colorValue ushr 24
    actual fun setAlpha(a: Int) { colorValue = (colorValue and 0x00FFFFFF) or ((a and 0xFF) shl 24) }
    actual fun getTextAlign(): PaintAlign = textAlignValue
    actual fun setTextAlign(align: PaintAlign) { textAlignValue = align }
    actual fun getTypeface(): Typeface? = typefaceValue
    actual fun setTypeface(typeface: Typeface?): Typeface? {
        typefaceValue = typeface
        return typeface
    }
    actual fun isAntiAlias(): Boolean = flags and ANTI_ALIAS_FLAG != 0
    actual fun setAntiAlias(aa: Boolean) {
        flags = if (aa) flags or ANTI_ALIAS_FLAG else flags and ANTI_ALIAS_FLAG.inv()
    }
    actual fun isFakeBoldText(): Boolean = fakeBoldText
    actual fun setFakeBoldText(fakeBoldText: Boolean) { this.fakeBoldText = fakeBoldText }
    actual fun getStrokeCap(): PaintCap = strokeCapValue
    actual fun setStrokeCap(cap: PaintCap) { strokeCapValue = cap }
    actual fun getPathEffect(): PathEffect? = pathEffectValue
    actual fun setPathEffect(effect: PathEffect?): PathEffect? {
        pathEffectValue = effect
        return effect
    }
    actual fun getFontMetrics(): FontMetrics = FontMetrics().also { TextMeasurement.require().fontMetrics(this, it) }

    actual fun measureText(text: String): Float = TextMeasurement.require().measureText(this, text)
    actual fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) =
        TextMeasurement.require().textBounds(this, text, start, end, bounds)
}
