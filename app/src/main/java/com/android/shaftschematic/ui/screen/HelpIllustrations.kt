// file: app/src/main/java/com/android/shaftschematic/ui/screen/HelpIllustrations.kt
package com.android.shaftschematic.ui.screen

import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.geom.keywaySpoonBowl
import com.android.shaftschematic.pdf.breakPairLayout
import com.android.shaftschematic.pdf.drawBreakEdge
import com.android.shaftschematic.ui.theme.SheetInk

/**
 * HelpIllustrations — the small figures that sit under a Help topic's text.
 *
 * Two rules hold this file together.
 *
 * **Drawn, never captured.** Each figure is a Canvas built from the SAME shared geometry the
 * real drawing uses — `keywaySpoonBowl` for the spoon, `breakPairLayout` + `drawBreakEdge`
 * for the S-break pair. A screenshot would drift the moment the glyph is retuned; a figure
 * that calls the production math cannot. Where a figure invents its own geometry (the
 * AFT/FWD and undercut-depth diagrams, which illustrate a CONVENTION rather than a glyph)
 * it is deliberately schematic and says so in its caption.
 *
 * **Paper, not chrome.** A figure depicts printed output, so it draws dark ink on a forced
 * white sheet from `SheetInk` — never `MaterialTheme.colorScheme`, whose near-white
 * `onSurface` in dark theme would be invisible ink here. Same rule as the five sheet
 * canvases (see `Appearance.md`). The caption below the sheet is app chrome and stays
 * theme-colored.
 *
 * Figures are decorative elaborations of the topic text: the text alone must carry the
 * explanation, and each figure exposes its caption as its contentDescription so a screen
 * reader is not left with a bare rectangle.
 */

/** Shared figure frame: a white sheet with a caption underneath. */
@Composable
private fun SheetFigure(
    caption: String,
    heightDp: Dp = 108.dp,
    draw: DrawScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { contentDescription = caption },
            ) { draw() }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Sheet-ink label paint, sized in px. */
private fun labelPaint(sizePx: Float, bold: Boolean = false) =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = sizePx
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

private fun DrawScope.sheetText(s: String, x: Float, y: Float, sizePx: Float, bold: Boolean = false) {
    drawIntoCanvas { it.nativeCanvas.drawText(s, x, y, labelPaint(sizePx, bold)) }
}

/** Centered variant — x is the midpoint of the text. */
private fun DrawScope.sheetTextCentered(s: String, cx: Float, y: Float, sizePx: Float, bold: Boolean = false) {
    val p = labelPaint(sizePx, bold)
    drawIntoCanvas { it.nativeCanvas.drawText(s, cx - p.measureText(s) / 2f, y, p) }
}

/** Plain rectangular shaft silhouette. */
private fun DrawScope.shaftOutline(x0: Float, x1: Float, cy: Float, halfH: Float, w: Float) {
    drawLine(SheetInk.Outline, Offset(x0, cy - halfH), Offset(x1, cy - halfH), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(x0, cy + halfH), Offset(x1, cy + halfH), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(x0, cy - halfH), Offset(x0, cy + halfH), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(x1, cy - halfH), Offset(x1, cy + halfH), strokeWidth = w)
}

/** A dimension stub with inward arrowheads at both ends. */
private fun DrawScope.dimArrow(x0: Float, x1: Float, y: Float, w: Float) {
    drawLine(SheetInk.Outline, Offset(x0, y), Offset(x1, y), strokeWidth = w)
    val a = 4.dp.toPx()
    listOf(x0 to 1f, x1 to -1f).forEach { (x, dir) ->
        drawLine(SheetInk.Outline, Offset(x, y), Offset(x + dir * a, y - a * 0.5f), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(x, y), Offset(x + dir * a, y + a * 0.5f), strokeWidth = w)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Figures
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AFT is the left end of every drawing and FWD the right, and a component's Measure From
 * chips pick which end its distance is quoted from. Schematic — this is a convention, not a
 * glyph, so there is no shared geometry to borrow.
 */
@Composable
internal fun AftFwdFigure() = SheetFigure(
    caption = "AFT is always the left end, FWD the right. \"Measure From\" picks which end a " +
        "component's distance is quoted from — the same liner, described from either side.",
    heightDp = 128.dp,
) {
    val w = 1.4.dp.toPx()
    val x0 = 0f
    val x1 = size.width
    // Sits high in the frame: two stacked dimension arrows hang below it.
    val cy = size.height * 0.36f
    val halfH = size.height * 0.17f
    val label = 9.5.dp.toPx()

    shaftOutline(x0, x1, cy, halfH, w)

    // A liner sleeve sitting mid-shaft — the thing being measured to.
    val lx0 = x0 + (x1 - x0) * 0.46f
    val lx1 = x0 + (x1 - x0) * 0.68f
    val lHalf = halfH * 1.3f
    drawRect(
        SheetInk.LinerTint.copy(alpha = 0.18f),
        topLeft = Offset(lx0, cy - lHalf),
        size = Size(lx1 - lx0, lHalf * 2f),
    )
    shaftOutline(lx0, lx1, cy, lHalf, w)

    sheetText("AFT", x0, cy - halfH - 6.dp.toPx(), label, bold = true)
    val fwd = labelPaint(label, bold = true).measureText("FWD")
    sheetText("FWD", x1 - fwd, cy - halfH - 6.dp.toPx(), label, bold = true)

    // From AFT (below), and the same edge from FWD (further below).
    val yA = cy + lHalf + 15.dp.toPx()
    dimArrow(x0, lx0, yA, w)
    sheetTextCentered("from AFT", (x0 + lx0) / 2f, yA - 4.dp.toPx(), label * 0.92f)

    val yF = yA + 20.dp.toPx()
    dimArrow(lx1, x1, yF, w)
    sheetTextCentered("from FWD", (lx1 + x1) / 2f, yF - 4.dp.toPx(), label * 0.92f)

    // Witness lines tying the arrows to the liner edges.
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
    listOf(lx0 to yA, lx1 to yF).forEach { (x, yEnd) ->
        drawLine(
            SheetInk.Outline.copy(alpha = 0.5f),
            Offset(x, cy + lHalf), Offset(x, yEnd),
            strokeWidth = w * 0.8f, pathEffect = dash,
        )
    }
}

/**
 * A spooned keyway keeps the normal slot and ADDS the enlarged bowl at the closed end, with
 * the mill semicircle surviving inside it as a reference line. The bowl comes from
 * `keywaySpoonBowl` — the same call the renderer and the PDF composer make — so this figure
 * tracks `SPOON_BOWL_WIDTH_RATIO` automatically.
 */
@Composable
internal fun SpoonedKeywayFigure() = SheetFigure(
    caption = "A spooned keyway keeps the full-length slot and its milled end, then adds the " +
        "enlarged bowl around the closed end. Drawing only — the keyway's width, depth, and " +
        "length are unchanged.",
    heightDp = 130.dp,
) {
    val w = 1.4.dp.toPx()
    val label = 9.dp.toPx()
    val gutter = 46.dp.toPx()
    val x0 = gutter
    val x1 = size.width
    val halfH = size.height * 0.15f

    fun row(cy: Float, spooned: Boolean) {
        shaftOutline(x0, x1, cy, halfH, w)

        val halfW = halfH * 0.34f
        val kwSetX = x0 + (x1 - x0) * 0.10f
        val kwLetX = x0 + (x1 - x0) * 0.62f
        val letArcCx = kwLetX - halfW
        val arcBox = Size(halfW * 2f, halfW * 2f)
        val bowl = if (spooned) keywaySpoonBowl(kwLetX, dir = 1f, halfW = halfW) else null

        // Void first (the slot erases the surface it cuts), then the outline strokes.
        drawRect(
            Color.White,
            topLeft = Offset(kwSetX + w, cy - halfW),
            size = Size(letArcCx - kwSetX - w, halfW * 2f),
        )
        drawArc(
            Color.White, startAngle = 270f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(letArcCx - halfW, cy - halfW), size = arcBox,
        )
        bowl?.let { drawCircle(Color.White, radius = it.radius, center = Offset(it.cx, cy)) }

        drawLine(SheetInk.Outline, Offset(kwSetX, cy - halfW), Offset(letArcCx, cy - halfW), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(kwSetX, cy + halfW), Offset(letArcCx, cy + halfW), strokeWidth = w)
        drawArc(
            SheetInk.Outline, startAngle = 270f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(letArcCx - halfW, cy - halfW), size = arcBox,
            style = Stroke(width = w),
        )
        bowl?.let {
            drawArc(
                SheetInk.Outline,
                startAngle = it.arcStartDeg, sweepAngle = it.arcSweepDeg, useCenter = false,
                topLeft = Offset(it.cx - it.radius, cy - it.radius),
                size = Size(it.radius * 2f, it.radius * 2f),
                style = Stroke(width = w),
            )
        }
    }

    val cyTop = size.height * 0.26f
    val cyBot = size.height * 0.74f
    row(cyTop, spooned = false)
    row(cyBot, spooned = true)
    sheetText("plain", 0f, cyTop + label * 0.35f, label)
    sheetText("spooned", 0f, cyBot + label * 0.35f, label)
}

/**
 * The S-break pair on a compressed body run — drawn through `breakPairLayout` and
 * `drawBreakEdge`, the production glyph, so the figure changes if the symbol ever does.
 */
@Composable
internal fun SBreakFigure() = SheetFigure(
    caption = "The S-break pair marks a plain body run drawn shorter than it really is. The " +
        "printed dimension is always the true length.",
    heightDp = 96.dp,
) {
    val w = 1.4.dp.toPx()
    val label = 9.dp.toPx()
    val x0 = 0f
    val x1 = size.width
    val cy = size.height * 0.40f
    val halfH = size.height * 0.24f
    val top = cy - halfH
    val bot = cy + halfH

    val runLen = x1 - x0
    val (gap, amp) = breakPairLayout(
        runLenPt = runLen,
        desiredAmplitudePt = halfH * 0.6f,
        classicGapPt = minOf(18.dp.toPx(), 0.25f * runLen),
        strokeWidthPt = w,
    )
    val mid = (x0 + x1) * 0.5f
    val leftEnd = mid - gap / 2f
    val rightBeg = mid + gap / 2f

    // Left stub, break pair, right stub — the composer's order.
    drawLine(SheetInk.Outline, Offset(x0, top), Offset(leftEnd, top), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(x0, bot), Offset(leftEnd, bot), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(x0, top), Offset(x0, bot), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(rightBeg, top), Offset(x1, top), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(rightBeg, bot), Offset(x1, bot), strokeWidth = w)
    drawLine(SheetInk.Outline, Offset(x1, top), Offset(x1, bot), strokeWidth = w)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = w
        color = android.graphics.Color.BLACK
    }
    drawIntoCanvas {
        drawBreakEdge(it.nativeCanvas, leftEnd, top, bot, amp, paint, eyeAtTop = false)
        drawBreakEdge(it.nativeCanvas, rightBeg, top, bot, amp, paint, eyeAtTop = true)
    }

    val y = bot + 17.dp.toPx()
    dimArrow(x0, x1, y, w)
    sheetTextCentered("48.000  (true length)", mid, y - 4.dp.toPx(), label)
}

/**
 * Why a printed undercut looks deeper than its numbers: at true scale the cut is a hairline,
 * so drawn depth is exaggerated while the printed Ø stays the typed value. Schematic — the
 * point is the comparison, not the notch's exact construction.
 */
@Composable
internal fun UndercutDepthFigure() = SheetFigure(
    caption = "A real undercut is a few thousandths deep — invisible at drawing scale. The " +
        "drawing exaggerates the depth so the cut can be seen and tapped; the printed Ø is " +
        "always your measured number.",
    heightDp = 126.dp,
) {
    val w = 1.4.dp.toPx()
    val label = 9.dp.toPx()
    val gutter = 52.dp.toPx()
    val x0 = gutter
    val x1 = size.width
    val halfH = size.height * 0.14f

    fun row(cy: Float, depth: Float) {
        val cx0 = x0 + (x1 - x0) * 0.34f
        val cx1 = x0 + (x1 - x0) * 0.60f
        // Surface, broken over the cut's mouth — the notch is open at the surface.
        drawLine(SheetInk.Outline, Offset(x0, cy - halfH), Offset(cx0, cy - halfH), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(cx1, cy - halfH), Offset(x1, cy - halfH), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(x0, cy + halfH), Offset(x1, cy + halfH), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(x0, cy - halfH), Offset(x0, cy + halfH), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(x1, cy - halfH), Offset(x1, cy + halfH), strokeWidth = w)
        // The cut: two section faces and a floor.
        drawLine(SheetInk.Outline, Offset(cx0, cy - halfH), Offset(cx0, cy - halfH + depth), strokeWidth = w)
        drawLine(SheetInk.Outline, Offset(cx1, cy - halfH), Offset(cx1, cy - halfH + depth), strokeWidth = w)
        drawLine(
            SheetInk.Outline,
            Offset(cx0, cy - halfH + depth), Offset(cx1, cy - halfH + depth),
            strokeWidth = w,
        )
        sheetText("Ø 5.740", cx1 + 8.dp.toPx(), cy + label * 0.35f, label)
    }

    val cyTop = size.height * 0.26f
    val cyBot = size.height * 0.74f
    row(cyTop, depth = w)                 // true scale — a hairline
    row(cyBot, depth = halfH * 0.85f)     // as drawn
    sheetText("true", 0f, cyTop + label * 0.35f, label)
    sheetText("as drawn", 0f, cyBot + label * 0.35f, label)
}
