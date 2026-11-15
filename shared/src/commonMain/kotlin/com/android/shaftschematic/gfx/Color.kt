package com.android.shaftschematic.gfx

/**
 * Packed ARGB colour ints, the form every [Paint] colour takes. The constants and the packing are
 * `android.graphics.Color`'s own values and bit arithmetic, so a colour comes out as the same int.
 */
object Color {
    const val BLACK: Int = -0x1000000
    const val WHITE: Int = -0x1

    /** Packs [alpha], [red], [green] and [blue] (each 0–255) into one ARGB int. */
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    /** An opaque colour from [red], [green] and [blue] (each 0–255). */
    fun rgb(red: Int, green: Int, blue: Int): Int =
        -0x1000000 or (red shl 16) or (green shl 8) or blue
}
