package com.android.shaftschematic.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max

/**
 * Sets built-up fractions on a [Canvas] — one renderer behind every drawn fraction in the app.
 *
 * Both draw families use it: the PDF composers and the on-screen sheet canvases. A fraction is
 * therefore constructed the same way on the preview and on the paper, the same rule the wear-pit
 * "X", the runout bubble and the spooned keyway bowl already follow.
 *
 * ## The glyph
 * [FractionStyle.STACKED] is the shop-ruler fraction: numerator over denominator,
 * separated by a bar centred on the *math axis* — half the base font's cap height above the
 * baseline, which is where a minus sign sits. [FractionStyle.DIAGONAL] is the compact
 * shipped construction: numerator raised to cap height, denominator on the baseline, a slanted
 * solidus between them. [FractionStyle.INLINE] draws plain `n/d` and exists as an escape hatch.
 *
 * ## The size contract that keeps layout untouched
 * Digits are set at [FractionTextStyle.digitScale] of the base size (0.64 — near a half, the
 * proportion the reference ruler faces use). With the bar on the math axis, the resulting stack
 * spans roughly `0.9·S` above the baseline and `0.18·S` below it, so it lands **inside the base
 * font's own ascent/descent**. Nothing that budgets vertical space for a line of text — the
 * dimension-rail planner, the strip rails, the footer's line pitch — has to change or even know.
 *
 * Width is the only thing that moves, and it moves DOWN: `399/4000` set as a stack is narrower
 * than the same characters inline. Callers must measure with [measureRichText] rather than
 * [Paint.measureText] wherever they draw with [drawRichText] — measuring one way and drawing the
 * other is the bug this pairing exists to prevent, and it shows up as a value that no longer
 * centres in its break in the dimension line.
 *
 * Text with no fraction in it costs one scan and then goes straight to [Canvas.drawText].
 */
enum class FractionStyle {
    /** Numerator over denominator with a horizontal bar — the shop-ruler fraction. */
    STACKED,

    /** Numerator raised, denominator on the baseline, slanted solidus between. The shipped look. */
    DIAGONAL,

    /** Plain `n/d` in the base font — no built-up fraction. */
    INLINE;

    fun uiLabel(): String = when (this) {
        STACKED -> "Stacked"
        DIAGONAL -> "Diagonal"
        INLINE -> "Plain"
    }

    companion object {
        /**
         * The shipped construction — the single source for `PdfPrefs.fractionStyle`'s default,
         * [FractionTextStyle.Default] and [fromName]'s fallback, so a fresh install, an
         * unreadable stored value and the renderer's baseline can never disagree.
         *
         * Diagonal on an on-device verdict: it reads better on screen and in print at the sizes
         * these sheets are actually read at. It costs ~3.6 pt more width than stacked and so
         * seats inline slightly less often, which the Small default arrowhead offsets.
         */
        val Default: FractionStyle = DIAGONAL

        /** Tolerant decode for a persisted name — an unknown value falls back to the shipped look. */
        fun fromName(raw: String?): FractionStyle =
            if (raw.isNullOrBlank()) Default else runCatching { valueOf(raw) }.getOrDefault(Default)
    }
}

/**
 * The style every `measureRichText` / `drawRichText` call uses when the caller names none —
 * which is all of them.
 *
 * A process-wide `@Volatile` mirror of the persisted `PdfPrefs.fractionStyle`, the same shape as
 * `SettingsStore.pdfPrefs` and for the same reason: this is a uniform, app-wide drawing decision
 * with no per-call variation, and threading it by hand through every composer's private draw
 * functions would cost far more than it buys.
 *
 * **`SettingsStore.updatePdfPrefs` is the only writer.** Set it anywhere else and the Settings
 * toggle and the ink disagree.
 */
object FractionTypography {
    @Volatile
    var active: FractionTextStyle = FractionTextStyle.Default
        private set

    /**
     * Applies a persisted style choice. Each style carries its OWN tuned spacing
     * ([FractionTextStyle.forStyle]) — copying one preset's numbers onto another style is the
     * bug this exists to prevent.
     */
    fun setStyle(style: FractionStyle) {
        active = FractionTextStyle.forStyle(style)
    }
}

/**
 * Proportions of a built-up fraction. Every ratio is relative to a font size, never an absolute
 * point value, so one style serves the 7 pt dimension label and the 30 px preview caption alike.
 */
data class FractionTextStyle(
    /**
     * Defaults to STACKED because the spacing fields below carry the STACKED numbers — a bare
     * `FractionTextStyle()` is the stacked preset. Build from a user choice with
     * [FractionTextStyle.forStyle], never by copying a style onto another preset's spacing.
     */
    val style: FractionStyle = FractionStyle.STACKED,
    /**
     * Numerator/denominator size as a fraction of the base text size. Near a half, as asked —
     * pushed up to the largest value that still keeps the stack inside the base font's line box
     * (`FractionTextRendererTest`), because these digits are read at 7.5 pt on paper.
     */
    val digitScale: Float = 0.64f,
    /** Bar thickness, as a fraction of the fraction-digit size. */
    val barThicknessFrac: Float = 0.08f,
    /** Clearance between the bar and each digit row, as a fraction of the fraction-digit size. */
    val barGapFrac: Float = 0.12f,
    /** How far the bar runs past the wider digit row, each end, as a fraction of that size. */
    val barOverhangFrac: Float = 0.08f,
    /**
     * Stacked only: side bearing on each edge of the fraction's advance box, as a fraction of
     * the BASE size. Diagonal applies none — its box opens and closes on glyph ink.
     */
    val sideBearingFrac: Float = 0.045f,
    /**
     * The tightened whole-number space ([FractionText.Run.Gap]), as a fraction of the BASE size.
     * Well under a word space (~0.25) because a mixed number reads as ONE value: `21 5/16` is a
     * single dimension, not two things.
     */
    val gapFrac: Float = 0.14f,
    /** Diagonal only: solidus horizontal run as a fraction of the base cap height. */
    val slashRunFrac: Float = 0.42f,
    /** Diagonal only: kerning on each side of the solidus, as a fraction of the BASE size. */
    val slashKernFrac: Float = 0.015f,
) {
    companion object {
        /**
         * Spacing is per-style, not shared, because the two constructions present different ink
         * at the edges of their box (on-device report: one spacing read well diagonal and
         * slightly off stacked).
         *
         * - **Stacked** leads with the BAR — a horizontal stroke reaching the box edge at
         *   mid-height, which binds to a preceding digit the way a hyphen would — and closes
         *   with a numerator row that a trailing `"` will otherwise attach itself to. It needs
         *   the looser gap and side bearing on both sides.
         * - **Diagonal** leads with the numerator's own glyph at cap height and closes on the
         *   baseline, so it reads correctly with a tighter gap and no side bearing at all — and
         *   being the wider construction, it is the one that benefits from spending less on air.
         */
        val Stacked = FractionTextStyle(style = FractionStyle.STACKED)

        val Diagonal = FractionTextStyle(
            style = FractionStyle.DIAGONAL,
            gapFrac = 0.10f,
        )

        val Plain = FractionTextStyle(style = FractionStyle.INLINE)

        /** The tuned preset for [style] — the only way to build one from a persisted choice. */
        fun forStyle(style: FractionStyle): FractionTextStyle = when (style) {
            FractionStyle.STACKED -> Stacked
            FractionStyle.DIAGONAL -> Diagonal
            FractionStyle.INLINE -> Plain
        }

        /**
         * The shipped preset, off the one [FractionStyle.Default]. This is the BASELINE, not the
         * live setting — read [FractionTypography.active] for what is actually drawing, which the
         * user picks in Settings → Drawing → "Fractions".
         */
        val Default = forStyle(FractionStyle.Default)
    }
}

/** Cap height of the digits in this paint; falls back to a typical ratio when unmeasurable. */
private fun Paint.digitCapHeight(scratch: Rect): Float {
    getTextBounds("0", 0, 1, scratch)
    val measured = -scratch.top.toFloat()
    return if (measured > 0f) measured else textSize * 0.72f
}

/** Advance width of [text] as [drawRichText] would set it, fractions built up. */
fun Paint.measureRichText(text: String, style: FractionTextStyle = FractionTypography.active): Float {
    if (style.style == FractionStyle.INLINE) return measureText(text)
    val runs = FractionText.parse(text)
    if (runs.none { it is FractionText.Run.Frac }) return measureText(text)
    return FractionTextRenderer.layout(this, runs, style).width
}

/**
 * Draws [text] with its fractions built up, honouring `paint.textAlign` exactly as
 * [Canvas.drawText] does. [y] is the baseline of the surrounding text — the fraction centres
 * itself on that line, so a caller passes the same baseline it always did.
 */
fun Canvas.drawRichText(
    text: String,
    x: Float,
    y: Float,
    paint: Paint,
    style: FractionTextStyle = FractionTypography.active,
) {
    if (style.style == FractionStyle.INLINE) {
        drawText(text, x, y, paint)
        return
    }
    val runs = FractionText.parse(text)
    if (runs.none { it is FractionText.Run.Frac }) {
        drawText(text, x, y, paint)
        return
    }
    FractionTextRenderer.draw(this, runs, x, y, paint, style)
}

/**
 * Measurement and ink for [FractionText.Run] sequences. Callers use the [Paint.measureRichText] /
 * [Canvas.drawRichText] pair; this object is the shared implementation behind both, so a change to
 * the glyph can never move the measured width out from under the drawn one.
 */
object FractionTextRenderer {

    /** One laid-out run: where it starts inside the line and how wide it is. */
    internal class Placed(val run: FractionText.Run, val x: Float, val width: Float)

    internal class Layout(val width: Float, val placed: List<Placed>)

    /**
     * Widths only — no ink, no vertical work. Both the measure and the draw path call this, which
     * is what guarantees they agree.
     */
    internal fun layout(paint: Paint, runs: List<FractionText.Run>, style: FractionTextStyle): Layout {
        val scratch = Rect()
        val base = paint.textSize
        val fracSize = base * style.digitScale
        val sideBearing = base * style.sideBearingFrac

        val fracPaint = Paint(paint).apply { textSize = fracSize; textAlign = Paint.Align.LEFT }
        val capBase = paint.digitCapHeight(scratch)

        var cursor = 0f
        val placed = ArrayList<Placed>(runs.size)
        for (run in runs) {
            val w = when (run) {
                is FractionText.Run.Plain -> paint.measureText(run.text)
                FractionText.Run.Gap -> base * style.gapFrac
                is FractionText.Run.Frac -> when (style.style) {
                    FractionStyle.DIAGONAL ->
                        fracPaint.measureText(run.numerator) +
                            base * style.slashKernFrac * 2f +
                            capBase * style.slashRunFrac +
                            fracPaint.measureText(run.denominator)

                    else -> {
                        val digits = max(
                            fracPaint.measureText(run.numerator),
                            fracPaint.measureText(run.denominator),
                        )
                        digits + fracSize * style.barOverhangFrac * 2f + sideBearing * 2f
                    }
                }
            }
            placed += Placed(run, cursor, w)
            cursor += w
        }
        return Layout(cursor, placed)
    }

    /** Draws a laid-out line at [x] (per `paint.textAlign`) on baseline [y]. */
    internal fun draw(
        canvas: Canvas,
        runs: List<FractionText.Run>,
        x: Float,
        y: Float,
        paint: Paint,
        style: FractionTextStyle,
    ) {
        val layout = layout(paint, runs, style)
        val left = when (paint.textAlign) {
            Paint.Align.CENTER -> x - layout.width / 2f
            Paint.Align.RIGHT -> x - layout.width
            else -> x
        }

        val scratch = Rect()
        val base = paint.textSize
        val fracSize = base * style.digitScale
        val capBase = paint.digitCapHeight(scratch)

        // Plain runs draw left-to-right off our own cursor, so the paint's own alignment is
        // consumed above and must not be applied again per run.
        val textPaint = Paint(paint).apply { textAlign = Paint.Align.LEFT }
        val fracPaint = Paint(textPaint).apply { textSize = fracSize }
        val capFrac = fracPaint.digitCapHeight(scratch)

        for (p in layout.placed) {
            val runX = left + p.x
            when (val run = p.run) {
                is FractionText.Run.Plain -> canvas.drawText(run.text, runX, y, textPaint)
                FractionText.Run.Gap -> Unit
                is FractionText.Run.Frac -> when (style.style) {
                    FractionStyle.DIAGONAL ->
                        drawDiagonal(canvas, run, runX, y, textPaint, fracPaint, capBase, capFrac, base, style)

                    else ->
                        drawStacked(canvas, run, runX, p.width, y, textPaint, fracPaint, capBase, capFrac, fracSize, base, style)
                }
            }
        }
    }

    private fun drawStacked(
        canvas: Canvas,
        run: FractionText.Run.Frac,
        runX: Float,
        runWidth: Float,
        baselineY: Float,
        textPaint: Paint,
        fracPaint: Paint,
        capBase: Float,
        capFrac: Float,
        fracSize: Float,
        base: Float,
        style: FractionTextStyle,
    ) {
        // The bar rides the math axis — half the BASE cap height above the baseline — so the
        // fraction reads as centred on the same optical line as the text beside it.
        val axisY = baselineY - capBase / 2f
        val barT = max(0.5f, fracSize * style.barThicknessFrac)
        val gap = fracSize * style.barGapFrac
        val sideBearing = base * style.sideBearingFrac

        val barLeft = runX + sideBearing
        val barRight = runX + runWidth - sideBearing
        val barCx = (barLeft + barRight) / 2f

        val numW = fracPaint.measureText(run.numerator)
        val denW = fracPaint.measureText(run.denominator)

        // Digits have no descender, so the numerator's baseline sits directly above the bar.
        canvas.drawText(run.numerator, barCx - numW / 2f, axisY - barT / 2f - gap, fracPaint)
        canvas.drawText(run.denominator, barCx - denW / 2f, axisY + barT / 2f + gap + capFrac, fracPaint)

        // The bar carries the text paint's style so a knockout/halo pass strokes it too.
        val barPaint = Paint(textPaint)
        canvas.drawRect(barLeft, axisY - barT / 2f, barRight, axisY + barT / 2f, barPaint)
    }

    private fun drawDiagonal(
        canvas: Canvas,
        run: FractionText.Run.Frac,
        runX: Float,
        baselineY: Float,
        textPaint: Paint,
        fracPaint: Paint,
        capBase: Float,
        capFrac: Float,
        base: Float,
        style: FractionTextStyle,
    ) {
        val kern = base * style.slashKernFrac
        val numW = fracPaint.measureText(run.numerator)
        val slashRun = capBase * style.slashRunFrac

        // Numerator's cap aligns with the base cap line; denominator sits on the shared baseline.
        canvas.drawText(run.numerator, runX, baselineY - (capBase - capFrac), fracPaint)

        val sx = runX + numW + kern
        val slashPaint = Paint(textPaint).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = max(0.5f, base * 0.055f)
            strokeCap = Paint.Cap.BUTT
        }
        canvas.drawLine(sx, baselineY, sx + slashRun, baselineY - capBase, slashPaint)

        canvas.drawText(run.denominator, sx + slashRun + kern, baselineY, fracPaint)
    }
}
