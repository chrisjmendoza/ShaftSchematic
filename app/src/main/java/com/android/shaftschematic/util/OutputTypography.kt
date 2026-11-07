package com.android.shaftschematic.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.android.shaftschematic.R

/**
 * The typeface every printed sheet is set in — Settings → Drawing → "Output font".
 *
 * A shop picks a face once and every document it prints carries it, so this is a device-wide
 * drawing preference in exactly the posture of `PdfPrefs.fractionStyle`: captured by a Drawing
 * profile, reset by "Restore Drawing defaults", never a per-document field. It is deliberately
 * absent from the PDF options sheets — the face is a house style, not a per-sheet decision.
 *
 * The faces are system families, plus ONE bundled font — [HYPERLEGIBLE], Atkinson Hyperlegible
 * (SIL OFL 1.1, `res/font`), loaded once at process start by [BundledFonts]. A device missing a
 * system family resolves it through [Typeface.create], which falls back to the platform's default
 * sans, and a bundled font that failed to load falls back the same way — a legible sheet in the
 * wrong face rather than no sheet, and the reason [STANDARD] is both the default and the
 * documented fallback. A character a face lacks (Atkinson Hyperlegible has no ← → ″ ✓) is drawn
 * from the system fonts by the platform's own fallback.
 *
 * Nothing about the choice enters a layout budget: every measurement the composers take
 * (`measureRichText`, `measureDualLabel`, the rail planner's text metrics) is taken from the
 * live [Paint], so a wider or narrower face is measured as drawn.
 */
enum class OutputFont {
    /** The platform sans — the historical look, and what a missing family falls back to. */
    STANDARD,

    /**
     * The platform sans at medium weight: heavier strokes that hold up on a photocopy and for
     * readers with weak eyes (on-device request). A system family, so nothing is bundled.
     */
    MEDIUM,

    /**
     * Atkinson Hyperlegible — the Braille Institute's face for low-vision readers, which keeps
     * easily confused characters (1 / l / I, 0 / O, 5 / S) distinct; bundled (on-device request).
     */
    HYPERLEGIBLE,

    /** A narrower sans: more value seats inline on a crowded rail. */
    CONDENSED,

    /** A slab/serif face, for shops whose paperwork is set that way. */
    SERIF,

    /** Fixed-pitch: digits column up, which some machinists read faster off a printed sheet. */
    MONOSPACE;

    fun uiLabel(): String = when (this) {
        STANDARD -> "Standard"
        MEDIUM -> "Medium"
        HYPERLEGIBLE -> "Hyperlegible"
        CONDENSED -> "Condensed"
        SERIF -> "Serif"
        MONOSPACE -> "Monospace"
    }

    /**
     * The family this choice draws in. A device that does not carry the named family gets the
     * platform default sans back from [Typeface.create], which is the accepted fallback.
     */
    fun typeface(): Typeface = when (this) {
        STANDARD -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        HYPERLEGIBLE -> BundledFonts.hyperlegible ?: STANDARD.typeface()
        CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    companion object {
        /**
         * The shipped face — the single source for `PdfPrefs.outputFont`'s default and
         * [fromName]'s fallback, so a fresh install and an unreadable stored value can never
         * disagree about what a sheet is set in.
         */
        val Default: OutputFont = STANDARD

        /** Tolerant decode for a persisted name — an unknown value falls back to the shipped face. */
        fun fromName(raw: String?): OutputFont =
            if (raw.isNullOrBlank()) Default else runCatching { valueOf(raw) }.getOrDefault(Default)
    }
}

/**
 * The fonts the app bundles, loaded ONCE at process start (`ShaftSchematicApp.onCreate`, before
 * any ViewModel applies the persisted [OutputFont]) and held here, because [OutputFont.typeface]
 * has no Context and the composers draw off the main thread. A font that fails to load stays
 * `null` and its face draws in [OutputFont.STANDARD], logged once.
 */
object BundledFonts {
    @Volatile
    var hyperlegible: Typeface? = null
        private set

    fun init(context: Context) {
        if (hyperlegible != null) return
        hyperlegible = runCatching {
            ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_regular)
        }.onFailure { AppLog.e("Fonts", "Atkinson Hyperlegible did not load; Standard stands in", it) }
            .getOrNull()
    }
}

/**
 * The typeface every composer's root text [Paint] is built with when the caller names none —
 * which is all of them.
 *
 * A process-wide `@Volatile` mirror of the persisted `PdfPrefs.outputFont`, the sibling of
 * [FractionTypography] and held for the same reason: this is a uniform, app-wide drawing
 * decision with no per-call variation, and threading it by hand through every composer's private
 * draw functions would cost far more than it buys.
 *
 * **`SettingsStore.updatePdfPrefs` is the only writer.** Set it anywhere else and the Settings
 * chips and the ink disagree. And because a mirror is not snapshot state, every preview's
 * render-inputs record must carry the font as a re-render KEY or that tab keeps rasterizing in
 * the face it last drew.
 */
object OutputTypography {
    @Volatile
    var active: Typeface = OutputFont.Default.typeface()
        private set

    /** Applies a persisted font choice to every sheet drawn from here on. */
    fun setFont(font: OutputFont) {
        active = font.typeface()
    }
}
