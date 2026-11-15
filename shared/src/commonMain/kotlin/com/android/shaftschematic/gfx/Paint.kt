@file:Suppress("NOTHING_TO_INLINE")

package com.android.shaftschematic.gfx

/**
 * Stroke, fill and text state for a draw call.
 *
 * On Android this IS `android.graphics.Paint` (an `actual typealias`). The class declares the
 * Java accessor methods; the property spellings the drawing code writes (`paint.textSize = …`,
 * `paint.style`, `paint.fontMetrics`) are the inline extensions below, each forwarding to the one
 * accessor the Java synthetic property calls, so an Android build makes the same calls in the
 * same order. The copy constructor copies every piece of state, typeface and path effect included.
 */
expect open class Paint {
    constructor()
    constructor(flags: Int)
    constructor(paint: Paint)

    fun getTextSize(): Float
    fun setTextSize(textSize: Float)
    fun getStrokeWidth(): Float
    fun setStrokeWidth(width: Float)
    fun getStyle(): PaintStyle
    fun setStyle(style: PaintStyle)
    fun getColor(): Int
    fun setColor(color: Int)
    fun getAlpha(): Int
    fun setAlpha(a: Int)
    fun getTextAlign(): PaintAlign
    fun setTextAlign(align: PaintAlign)
    fun getTypeface(): Typeface?
    fun setTypeface(typeface: Typeface?): Typeface?
    fun isAntiAlias(): Boolean
    fun setAntiAlias(aa: Boolean)
    fun isFakeBoldText(): Boolean
    fun setFakeBoldText(fakeBoldText: Boolean)
    fun getStrokeCap(): PaintCap
    fun setStrokeCap(cap: PaintCap)
    fun getPathEffect(): PathEffect?
    fun setPathEffect(effect: PathEffect?): PathEffect?
    fun getFontMetrics(): FontMetrics

    fun measureText(text: String): Float
    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect)
}

/** `Paint.ANTI_ALIAS_FLAG` — the constructor flag that turns anti-aliasing on. */
const val ANTI_ALIAS_FLAG: Int = 1

/** `Paint.Style` on Android. */
expect enum class PaintStyle { FILL, STROKE, FILL_AND_STROKE }

/** `Paint.Align` on Android. */
expect enum class PaintAlign { LEFT, CENTER, RIGHT }

/** `Paint.Cap` on Android. */
expect enum class PaintCap { BUTT, ROUND, SQUARE }

/** `Paint.FontMetrics` on Android: the face's extents at the paint's size, in its own units. */
expect open class FontMetrics

/** Distance from the baseline to the top of the tallest glyph — negative, above the baseline. */
expect val FontMetrics.ascent: Float

/** Distance from the baseline to the bottom of the lowest glyph — positive, below it. */
expect val FontMetrics.descent: Float

inline var Paint.textSize: Float
    get() = getTextSize()
    set(value) { setTextSize(value) }

inline var Paint.strokeWidth: Float
    get() = getStrokeWidth()
    set(value) { setStrokeWidth(value) }

inline var Paint.style: PaintStyle
    get() = getStyle()
    set(value) { setStyle(value) }

inline var Paint.color: Int
    get() = getColor()
    set(value) { setColor(value) }

/** Replaces only the alpha channel of [color]. */
inline var Paint.alpha: Int
    get() = getAlpha()
    set(value) { setAlpha(value) }

inline var Paint.textAlign: PaintAlign
    get() = getTextAlign()
    set(value) { setTextAlign(value) }

inline var Paint.typeface: Typeface?
    get() = getTypeface()
    set(value) { setTypeface(value) }

inline var Paint.isAntiAlias: Boolean
    get() = isAntiAlias()
    set(value) { setAntiAlias(value) }

inline var Paint.isFakeBoldText: Boolean
    get() = isFakeBoldText()
    set(value) { setFakeBoldText(value) }

inline var Paint.strokeCap: PaintCap
    get() = getStrokeCap()
    set(value) { setStrokeCap(value) }

inline var Paint.pathEffect: PathEffect?
    get() = getPathEffect()
    set(value) { setPathEffect(value) }

inline val Paint.fontMetrics: FontMetrics
    get() = getFontMetrics()
