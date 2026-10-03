package com.android.shaftschematic.util

import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.sanitizeThreadDensity
import com.android.shaftschematic.geom.sanitizeThreadSlant

/**
 * The thread-hatch style every PDF composer draws with — process-wide `@Volatile` mirrors of the
 * persisted `PdfPrefs.threadSlant` and `PdfPrefs.threadDensity` (`geom/ThreadHatchMath.kt`), the
 * sibling of [FractionTypography] and [OutputTypography] and held for the same reason: a uniform,
 * app-wide drawing decision reached from many private draw functions, which threading by hand
 * through every composer would cost far more than it buys.
 *
 * **`SettingsStore.updatePdfPrefs` is the only writer.** Set either field anywhere else and the
 * slider and the ink disagree. A mirror is not snapshot state, so every preview's render-inputs
 * record must carry both values as re-render KEYS or that tab keeps rasterizing the old hatch.
 * The Compose canvases (editor preview, wear/undercut detail overlays) take both as explicit
 * parameters from the ViewModel's state flows instead, so a change recomposes them.
 */
object ThreadHatchStyle {
    /** Slant factor on each thread's true crest lean. */
    @Volatile
    var slant: Float = THREAD_SLANT_DEFAULT
        private set

    /** Fraction of each thread's true crests the hatch draws. */
    @Volatile
    var density: Float = THREAD_DENSITY_DEFAULT
        private set

    /** Applies a persisted slant factor to every sheet drawn from here on. */
    fun setSlant(slant: Float) {
        this.slant = sanitizeThreadSlant(slant)
    }

    /** Applies a persisted density to every sheet drawn from here on. */
    fun setDensity(density: Float) {
        this.density = sanitizeThreadDensity(density)
    }
}
