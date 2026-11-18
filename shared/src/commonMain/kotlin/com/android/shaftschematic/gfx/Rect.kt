package com.android.shaftschematic.gfx

/**
 * A float rectangle. On Android this IS `android.graphics.RectF`; its edges are Java fields there,
 * which an `expect` class cannot declare, so the drawing code reads them through the [left] /
 * [top] / [right] / [bottom] extensions, whose Android `actual`s read the fields.
 */
expect open class RectF {
    constructor(left: Float, top: Float, right: Float, bottom: Float)

    fun width(): Float
    fun height(): Float
}

expect val RectF.left: Float
expect val RectF.top: Float
expect val RectF.right: Float
expect val RectF.bottom: Float

/** An integer rectangle — the out-parameter of [Paint.getTextBounds]. `android.graphics.Rect` on Android. */
expect class Rect {
    constructor()
}

expect val Rect.top: Int
expect val Rect.bottom: Int
