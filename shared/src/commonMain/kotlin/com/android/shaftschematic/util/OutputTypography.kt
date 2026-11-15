package com.android.shaftschematic.util

import com.android.shaftschematic.gfx.Typeface
import com.android.shaftschematic.gfx.fallbackSansMediumTypeface
import com.android.shaftschematic.gfx.fallbackSansTypeface
import kotlin.concurrent.Volatile

/**
 * The typeface every printed sheet is set in — Settings → Drawing → "Output font", and the "Lines &
 * text" section of both PDF options sheets.
 *
 * A device-wide drawing preference in exactly the posture of `PdfPrefs.fractionStyle`: captured by a
 * Drawing profile, reset by "Restore Drawing defaults", never a per-document field.
 *
 * **Every face is BUNDLED** (`res/font`, loaded once at process start by `BundledFonts`), never a
 * system family: a phone free to substitute its own font for the platform families printed
 * Standard, Condensed, Serif and Monospace identically (on-device report, Samsung), while the bundled
 * Hyperlegible came through. A bundled face prints the same on every phone and tablet in the shop.
 * Licences ship in `assets/licenses` (SIL OFL 1.1; Roboto Apache 2.0). A face whose file failed to
 * load falls back to the platform sans — a legible sheet in the wrong face rather than no sheet.
 * A character a face lacks (the Atkinson faces have no ← → ″ ✓) is drawn from the system fonts by the
 * platform's own fallback.
 *
 * Nothing about the choice enters a layout budget: every measurement the composers take
 * (`measureRichText`, `measureDualLabel`, the rail planner's text metrics) is taken from the
 * live [Paint], so a wider or narrower face is measured as drawn.
 */
enum class OutputFont {
    /** Roboto Regular — the historical look (stock Android's sans), and the documented fallback. */
    STANDARD,

    /** Roboto Medium: heavier strokes that hold up on a photocopy and for readers with weak eyes. */
    MEDIUM,

    /**
     * Atkinson Hyperlegible — the Braille Institute's face for low-vision readers, which keeps
     * easily confused characters (1 / l / I, 0 / O, 5 / S) distinct (on-device request).
     */
    HYPERLEGIBLE,

    /** Atkinson Hyperlegible Bold — the same letterforms at a heavier weight (on-device request). */
    HYPERLEGIBLE_BOLD,

    /**
     * Atkinson Hyperlegible Mono — the Hyperlegible letterforms at fixed pitch, so digits column up
     * (on-device request).
     */
    HYPERLEGIBLE_MONO,

    /**
     * B612 — designed for Airbus cockpit displays, to read figures at a glance in poor light
     * (on-device request).
     */
    B612;

    fun uiLabel(): String = when (this) {
        STANDARD -> "Standard"
        MEDIUM -> "Medium"
        HYPERLEGIBLE -> "Hyperlegible"
        HYPERLEGIBLE_BOLD -> "Hyperlegible Bold"
        HYPERLEGIBLE_MONO -> "Hyperlegible Mono"
        B612 -> "B612"
    }

    /**
     * The face this choice draws in: its bundled font, or — only when that file did not load —
     * the platform sans at the matching weight.
     */
    fun typeface(): Typeface = OutputFontFaces.get(this) ?: when (this) {
        MEDIUM, HYPERLEGIBLE_BOLD -> fallbackSansMediumTypeface()
        else -> fallbackSansTypeface()
    }

    companion object {
        /**
         * The shipped face — the single source for `PdfPrefs.outputFont`'s default and
         * [fromName]'s fallback, so a fresh install and an unreadable stored value can never
         * disagree about what a sheet is set in.
         */
        val Default: OutputFont = STANDARD

        /**
         * Tolerant decode for a persisted name (prefs and Drawing profiles). An unknown value falls
         * back to the shipped face. The retired system-family faces map to their nearest bundled
         * successor — Monospace to Hyperlegible Mono, Condensed and Serif to Standard — so a stored
         * choice still prints, and Monospace keeps its fixed pitch.
         */
        fun fromName(raw: String?): OutputFont = when (raw) {
            null, "" -> Default
            "MONOSPACE" -> HYPERLEGIBLE_MONO
            "CONDENSED", "SERIF" -> STANDARD
            else -> runCatching { valueOf(raw) }.getOrDefault(Default)
        }
    }
}

/**
 * The loaded face for each [OutputFont], filled ONCE at process start by the host (on Android,
 * `BundledFonts.init` from `ShaftSchematicApp.onCreate`, before any ViewModel applies the persisted
 * [OutputFont]) and read by [OutputFont.typeface], which has no platform context and runs on
 * whatever thread a composer draws on. A face that did not load is absent and draws in the
 * platform sans.
 */
object OutputFontFaces {
    @Volatile
    private var faces: Map<OutputFont, Typeface> = emptyMap()

    /** The loaded face for [font], or null when it did not load (or nothing has been loaded yet). */
    fun get(font: OutputFont): Typeface? = faces[font]

    /** How many faces are loaded. */
    val size: Int get() = faces.size

    /** Replaces the loaded faces with [loaded]. */
    fun set(loaded: Map<OutputFont, Typeface>) {
        faces = loaded
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
 *
 * It holds the CHOICE and resolves the face on read, so a read that lands before `BundledFonts`
 * has loaded can never pin the fallback face for the rest of the process.
 */
object OutputTypography {
    @Volatile
    private var font: OutputFont = OutputFont.Default

    /** The face sheets are set in now. */
    val active: Typeface get() = font.typeface()

    /** Applies a persisted font choice to every sheet drawn from here on. */
    fun setFont(font: OutputFont) {
        this.font = font
    }
}
