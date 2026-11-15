package com.android.shaftschematic.gfx

/**
 * Runs [block] on a [width] × [height] full-colour canvas whose pixels nobody reads — a dry pass
 * that only measures — and releases the backing store afterwards, whatever [block] does.
 */
expect fun <R> withArgbScratchCanvas(width: Int, height: Int, block: (Canvas) -> R): R

/**
 * A [width] × [height] alpha-only canvas whose pixels nobody reads, for dry passes that only
 * measure. Its backing store is left to the garbage collector.
 */
expect fun alphaScratchCanvas(width: Int, height: Int): Canvas
