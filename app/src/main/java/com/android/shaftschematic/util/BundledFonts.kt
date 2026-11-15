package com.android.shaftschematic.util

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.android.shaftschematic.R

/**
 * The bundled faces, loaded ONCE at process start (`ShaftSchematicApp.onCreate`, before any
 * ViewModel applies the persisted [OutputFont]) into [OutputFontFaces], which is what
 * [OutputFont.typeface] reads, because it has no Context and the composers draw off the main
 * thread. A face that fails to load stays absent and draws in the platform sans, logged once.
 */
object BundledFonts {
    /** The loaded face for [font], or null when it did not load (or [init] has not run). */
    fun get(font: OutputFont): Typeface? = OutputFontFaces.get(font)

    fun init(context: Context) {
        if (OutputFontFaces.size == OutputFont.entries.size) return
        OutputFontFaces.set(
            OutputFont.entries.mapNotNull { font ->
                runCatching { ResourcesCompat.getFont(context, font.fontRes) }
                    .onFailure { AppLog.e("Fonts", "${font.name} did not load; the platform sans stands in", it) }
                    .getOrNull()
                    ?.let { font to it }
            }.toMap(),
        )
    }
}

/** The bundled font file each [OutputFont] is set in (`res/font`). */
@get:FontRes
internal val OutputFont.fontRes: Int
    get() = when (this) {
        OutputFont.STANDARD -> R.font.roboto_regular
        OutputFont.MEDIUM -> R.font.roboto_medium
        OutputFont.HYPERLEGIBLE -> R.font.atkinson_hyperlegible_regular
        OutputFont.HYPERLEGIBLE_BOLD -> R.font.atkinson_hyperlegible_bold
        OutputFont.HYPERLEGIBLE_MONO -> R.font.atkinson_hyperlegible_mono_regular
        OutputFont.B612 -> R.font.b612_regular
    }
