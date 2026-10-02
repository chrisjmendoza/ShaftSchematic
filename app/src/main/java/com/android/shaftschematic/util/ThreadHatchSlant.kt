package com.android.shaftschematic.util

import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.sanitizeThreadSlant

/**
 * The thread-hatch slant factor every PDF composer draws with — a process-wide `@Volatile`
 * mirror of the persisted `PdfPrefs.threadSlant` (`geom/ThreadHatchMath.kt`), the sibling of
 * [FractionTypography] and [OutputTypography] and held for the same reason: a uniform, app-wide
 * drawing decision reached from many private draw functions, which threading by hand through
 * every composer would cost far more than it buys.
 *
 * **`SettingsStore.updatePdfPrefs` is the only writer.** Set it anywhere else and the slider and
 * the ink disagree. A mirror is not snapshot state, so every preview's render-inputs record must
 * carry the slant as a re-render KEY or that tab keeps rasterizing the old slant. The Compose
 * canvases (editor preview, wear/undercut detail overlays) take the slant as an explicit
 * parameter from the ViewModel's state flow instead, so a change recomposes them.
 */
object ThreadHatchSlant {
    @Volatile
    var active: Float = THREAD_SLANT_DEFAULT
        private set

    /** Applies a persisted slant factor to every sheet drawn from here on. */
    fun setSlant(slant: Float) {
        active = sanitizeThreadSlant(slant)
    }
}
