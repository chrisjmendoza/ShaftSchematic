package com.android.shaftschematic.gfx

/**
 * A geometric outline to fill, stroke or clip by. On Android this IS `android.graphics.Path`, so
 * a path a shared helper returns is the Android object a Compose caller converts directly.
 */
expect open class Path {
    constructor()
    constructor(src: Path?)

    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun close()
}

/** `android.graphics.PathEffect` on Android. */
expect open class PathEffect

/** On/off dash intervals, starting [phase] into the pattern. `android.graphics.DashPathEffect` on Android. */
expect open class DashPathEffect : PathEffect {
    constructor(intervals: FloatArray, phase: Float)
}
