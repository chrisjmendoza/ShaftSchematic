package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.KeywaySpan
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.ui.resolved.BodyBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Same-math SVG previews of a SHADED body run's S-break, for markup review without an on-device
 * round trip — the house convention for a drawing change (`BlendSvgPreviewTest`,
 * `WearStripWindowSvgPreviewTest`).
 *
 * "Same math" here is literal: the sheet is recorded off the REAL draw pass. [SvgCanvas] is a
 * [Canvas] that transcribes every `drawPath` / `drawLine` / `drawRect` [drawBodyRunsWithBreaks]
 * issues into SVG, so the picture is the production geometry rather than a restatement of it —
 * fill boundaries included, which is the whole point of this preview.
 *
 * The scenarios: **A** the classic centered break, **B** a slender shaft (small amplitude),
 * **C** a gap shifted off a protected keyway window, **D** a blend-flanked run whose flat span
 * is shorter than the run. Each is drawn shaded, so the stub fills are what there is to look at.
 *
 * Doubles as a geometry regression: on every broken run the two stub fills must reach exactly
 * ±√3/6·amplitude past their break lines — the S's own extremes — which is what a square fill to
 * the break line failed to do (on-device report: body shading does not fill cleanly at the S
 * breaks).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyBreakFillSvgPreviewTest {

    /**
     * Robolectric's native graphics stack has to be brought up by a Bitmap/Canvas before a bare
     * [Path] is constructed — a test that reaches for a Path first takes the whole test JVM down
     * at teardown (no exception, no report, just an EOF from the worker).
     */
    @Before
    fun warmNativeGraphics() {
        Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
    }

    private val shadeColor = Color.argb(40, 0, 0, 0)

    /** One transcribed draw call: its sampled points and how it was painted. */
    private data class Op(val pts: List<FloatArray>, val filled: Boolean, val color: Int) {
        val minX get() = pts.minOf { it[0] }
        val maxX get() = pts.maxOf { it[0] }
    }

    /**
     * A [Canvas] that records what it is asked to draw. It still owns a real bitmap so any
     * unoverridden call has somewhere to land; nothing here reads that bitmap back.
     */
    private class SvgCanvas(bmp: Bitmap) : Canvas(bmp) {
        val ops = mutableListOf<Op>()

        override fun drawPath(path: Path, paint: Paint) {
            ops += Op(flatten(path), paint.style != Paint.Style.STROKE, paint.color)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            ops += Op(listOf(floatArrayOf(startX, startY), floatArrayOf(stopX, stopY)), false, paint.color)
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            ops += Op(
                listOf(
                    floatArrayOf(left, top), floatArrayOf(right, top),
                    floatArrayOf(right, bottom), floatArrayOf(left, bottom), floatArrayOf(left, top),
                ),
                paint.style != Paint.Style.STROKE, paint.color,
            )
        }

        /** Walk a path by arc length — the curves come out as the drawn geometry, not as controls. */
        private fun flatten(path: Path): List<FloatArray> {
            val out = mutableListOf<FloatArray>()
            val pm = PathMeasure(path, false)
            val pos = FloatArray(2)
            do {
                val len = pm.length
                if (len > 0f) {
                    val n = kotlin.math.max(2, (len / 0.75f).toInt())
                    for (i in 0..n) {
                        pm.getPosTan(len * i / n, pos, null)
                        out += floatArrayOf(pos[0], pos[1])
                    }
                }
            } while (pm.nextContour())
            return out
        }
    }

    private fun svgColor(c: Int): String =
        "rgb(${Color.red(c)},${Color.green(c)},${Color.blue(c)})"

    private fun opacity(c: Int) = "%.3f".format(Color.alpha(c) / 255f)

    private fun renderSvg(ops: List<Op>, w: Float, h: Float, caption: String): String {
        val sb = StringBuilder()
        sb.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" " +
                "width=\"${w * 1.6f}\" height=\"${h * 1.6f}\" style=\"background:white\">\n",
        )
        ops.forEach { op ->
            val d = op.pts.joinToString(" ") { "${"%.2f".format(it[0])},${"%.2f".format(it[1])}" }
            if (op.filled) {
                sb.append(
                    "<polygon points=\"$d\" fill=\"${svgColor(op.color)}\" " +
                        "fill-opacity=\"${opacity(op.color)}\" stroke=\"none\"/>\n",
                )
            } else {
                sb.append(
                    "<polyline points=\"$d\" fill=\"none\" stroke=\"${svgColor(op.color)}\" " +
                        "stroke-opacity=\"${opacity(op.color)}\" stroke-width=\"1.2\"/>\n",
                )
            }
        }
        sb.append(
            "<text x=\"6\" y=\"${h - 6f}\" font-size=\"9\" fill=\"#888\" " +
                "font-family=\"Helvetica, Arial, sans-serif\">$caption</text>\n",
        )
        sb.append("</svg>\n")
        return sb.toString()
    }

    private val pageW = 520f
    private val pageH = 170f
    private val cy = 78f
    private val xOff = 40f

    /** Records one shaded run exactly as a sheet draws it: `xAt` linear, `rPx` = Ø/2. */
    private fun record(
        body: Body,
        avoid: List<KeywaySpan> = emptyList(),
        blends: List<BodyBlend> = emptyList(),
    ): List<Op> {
        val bmp = Bitmap.createBitmap(pageW.toInt(), pageH.toInt(), Bitmap.Config.ARGB_8888)
        val c = SvgCanvas(bmp)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = shadeColor }
        drawBodyRunsWithBreaks(
            c, listOf(body), cy, { mm -> xOff + mm }, { dia -> dia / 2f }, outline,
            RectF(0f, 0f, pageW, pageH), fill = fill,
            truePtPerMm = 1f, blends = blends, keywayAvoidSpansMm = avoid,
        )
        return c.ops
    }

    /** The two stub shade fills, AFT→FWD. Eyes are a different colour, outlines are not filled. */
    private fun stubFills(ops: List<Op>) =
        ops.filter { it.filled && it.color == shadeColor }.sortedBy { it.minX }

    /**
     * Assert both stubs end on the S: the S reaches ±√3/6·amplitude off its break line, so the
     * AFT stub's fill must overrun leftEnd by that much and the FWD stub's must undercut
     * rightBeg by it. A square fill lands on the break line instead — the reported bug.
     */
    private fun assertFillsFollowTheS(ops: List<Op>, runLenPt: Float, r: Float) {
        val pair = breakPairLayout(
            runLenPt = runLenPt,
            desiredAmplitudePt = r * 0.6f,
            classicGapPt = kotlin.math.min(ZIGZAG_GAP_MAX_PT, 0.25f * runLenPt),
            strokeWidthPt = 1.5f,
        )
        val peak = pair.amplitudePt * kotlin.math.sqrt(3f) / 6f
        val fills = stubFills(ops)
        assertEquals("a broken shaded run draws exactly two stub fills", 2, fills.size)
        val gapLeft = fills[0].maxX
        val gapRight = fills[1].minX
        // The break line each stub ends on sits `peak` inside the fill's own extreme.
        val leftEnd = gapLeft - peak
        val rightBeg = gapRight + peak
        assertEquals("the pair's gap must survive the curved fill", pair.gapPt, rightBeg - leftEnd, 0.5f)
        assertTrue("the aft stub must overrun its break line", gapLeft - leftEnd > 0.5f)
        assertTrue("the fwd stub must undercut its break line", rightBeg - gapRight > 0.5f)
    }

    @Test
    fun `render shaded S-break fill preview SVGs`() {
        val outDir = File("build/reports/body-break-fill").apply { mkdirs() }

        // A) The classic centered break on a stout shaft — the reference picture.
        val a = record(Body(id = "a", startFromAftMm = 0f, lengthMm = 420f, diaMm = 96f))
        File(outDir, "a-centered-break-shaded.svg")
            .writeText(renderSvg(a, pageW, pageH, "A — centered break, shaded body (Ø96 × 420)"))
        assertFillsFollowTheS(a, 420f, 48f)

        // B) A slender shaft: the amplitude follows the radius, so the crescent shrinks with it —
        //    but it must still be the curve the fill ends on, not a straight cap.
        val b = record(Body(id = "b", startFromAftMm = 0f, lengthMm = 420f, diaMm = 34f))
        File(outDir, "b-slender-shaft-shaded.svg")
            .writeText(renderSvg(b, pageW, pageH, "B — slender shaft, small amplitude (Ø34 × 420)"))
        assertFillsFollowTheS(b, 420f, 17f)

        // C) A keyway window over the middle pushes the gap off centre; the stubs are then very
        //    unequal, which is exactly where a fill that ignores the curve reads worst.
        val cOps = record(
            Body(id = "c", startFromAftMm = 0f, lengthMm = 420f, diaMm = 96f),
            avoid = listOf(KeywaySpan(170f, 260f)),
        )
        File(outDir, "c-keyway-shifted-gap-shaded.svg")
            .writeText(renderSvg(cOps, pageW, pageH, "C — gap shifted clear of a keyway window, shaded"))
        assertFillsFollowTheS(cOps, 420f, 48f)
        assertTrue(
            "the fixture must actually shift the gap off centre",
            kotlin.math.abs(stubFills(cOps)[0].maxX - (xOff + 210f)) > 20f,
        )

        // D) Blended faces shorten the FLAT span the break is cut into, and the curves carry their
        //    own fill — the stub fills must still meet the S and nothing else may change.
        val dBody = Body(id = "d", startFromAftMm = 0f, lengthMm = 420f, diaMm = 96f)
        val dOps = record(
            dBody,
            blends = listOf(
                BodyBlend(
                    bodyId = "d", end = LinerAuthoredReference.AFT, faceMm = 0f, lengthMm = 30f,
                    bodyDiaMm = 96f, neighbourDiaMm = 64f, profile = BlendProfile.OGEE,
                ),
                BodyBlend(
                    bodyId = "d", end = LinerAuthoredReference.FWD, faceMm = 420f, lengthMm = 30f,
                    bodyDiaMm = 96f, neighbourDiaMm = 72f, profile = BlendProfile.OGEE,
                ),
            ),
        )
        File(outDir, "d-blend-flanked-shaded.svg")
            .writeText(renderSvg(dOps, pageW, pageH, "D — blend-flanked run, shaded (flat span shortened)"))
        // The blend curves fill too, so the stub fills are picked out by their break-line extremes.
        assertTrue("the blended run still breaks", stubFills(dOps).size >= 2)

        assertEquals(4, outDir.listFiles()!!.count { it.name.endsWith(".svg") })
    }
}
