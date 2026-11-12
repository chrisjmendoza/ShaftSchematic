package com.android.shaftschematic.ui.drawing.render

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.shaftschematic.geom.bodyKeywayProtectedSpansMm
import com.android.shaftschematic.geom.boltRowProtectedSpansMm
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.pdf.bodyForPdf
import com.android.shaftschematic.pdf.SCHEMATIC_SHEET_CONTENT_WIDTH_PT
import com.android.shaftschematic.pdf.bodyRunBreakGapsX
import com.android.shaftschematic.pdf.schematicProfileFit
import com.android.shaftschematic.ui.drawing.compose.previewTapHitId
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.bodyBlends
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The editor preview's sheet layout (`PreviewSheetLayout.kt`): the sheet's compressed x map and
 * diameter scale behind `ShaftLayout`, the fallbacks to the linear map, the S-breaks drawn by the
 * sheet's own body pass — the runs the sheet breaks, and not a body that opted out of compression —
 * a keyway's width on the diameter scale, and a tap selecting through the map's inverse — an
 * S-break gap included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewSheetLayoutTest {

    private val inch = 25.4f

    // A phone-shaped box, 360 × 120 dp at 2.6 px/dp, laid out as ShaftDrawing lays it out.
    private val w = 936
    private val h = 312
    private val density = 2.6f
    private val padX = 16f
    private val padY = 8f

    /** The schematic sheet's outline weight at 100% line thickness (`OUTLINE_PT_BASE`). */
    private val sheetOutlinePt = 1.25f

    private val bg = 0xFFEEEEEE.toInt()
    private val ink = 0xFF202020.toInt()
    private val fill = Color(0xFF2060C0).copy(alpha = 0.5f).toArgb()

    private fun profile(spec: ShaftSpec, resolved: List<ResolvedComponent>, extentMm: Pair<Float, Float>? = null) =
        previewSheetProfileForBox(
            spec = spec, layoutSpec = spec, settings = PreviewSheetSettings(),
            leftPx = padX, topPx = padY, rightPx = w - padX, bottomPx = h - padY,
            densityPxPerPt = density, resolvedComponents = resolved, extentMm = extentMm,
        )

    private fun layout(spec: ShaftSpec, resolved: List<ResolvedComponent>, sheet: PreviewSheetProfile?) =
        ShaftLayout.compute(spec, padX, padY, w - padX, h - padY, resolvedComponents = resolved, sheet = sheet)

    private fun render(spec: ShaftSpec, resolved: List<ResolvedComponent>, layout: ShaftLayout.Result): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(bg)
        val opts = RenderOptions(
            outlineColor = ink,
            outlineWidthPx = 2f,
            bodyFillColor = fill,
            taperFillColor = fill,
            linerFillColor = fill,
        )
        CanvasDrawScope().draw(
            Density(density), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bmp)), Size(w.toFloat(), h.toFloat()),
        ) {
            with(ShaftRenderer) { draw(spec, layout, opts, resolved) }
        }
        return bmp
    }

    /** The sheet body pass's own break gaps for this layout, in canvas px. */
    private fun breakGapsPx(spec: ShaftSpec, resolved: List<ResolvedComponent>, l: ShaftLayout.Result): List<ClosedFloatingPointRange<Float>> {
        val sheet = l.sheet!!
        val p = sheet.pxPerPt
        return bodyRunBreakGapsX(
            bodies = resolved.filterIsInstance<ResolvedBody>().map { spec.bodyForPdf(it) },
            xAt = { mm -> l.xPx(mm) / p },
            rPx = { dia -> l.rPx(dia) / p },
            outline = Paint().apply { strokeWidth = 2f / p },
            truePtPerMm = sheet.diaPtPerMm,
            breakMinFracOfTrue = sheet.sBreakThresholdFrac,
            blends = bodyBlends(spec, resolved),
            keywayAvoidSpansMm = bodyKeywayProtectedSpansMm(spec),
            boltRowAvoidSpansMm = boltRowProtectedSpansMm(spec),
        ).map { (it.start * p)..(it.endInclusive * p) }
    }

    /** The gap a tap at its centre selects as [id]'s — the run the break was cut into. */
    private fun gapIn(
        id: String,
        gaps: List<ClosedFloatingPointRange<Float>>,
        resolved: List<ResolvedComponent>,
        l: ShaftLayout.Result,
    ): List<ClosedFloatingPointRange<Float>> =
        gaps.filter { previewTapHitId(resolved, l.xMmFromPx((it.start + it.endInclusive) / 2f)) == id }

    /**
     * How many S-breaks the printed schematic cuts into these runs: the same profile solve over the
     * sheet's own content width, at the sheet's outline weight.
     */
    private fun sheetBreakCount(spec: ShaftSpec, resolved: List<ResolvedComponent>): Int {
        val settings = PreviewSheetSettings()
        val fit = schematicProfileFit(
            spec, settings.pdfPrefs(), settings.heightScale, settings.linerMinFracOfTrue,
            contentLeft = 0f, contentRight = SCHEMATIC_SHEET_CONTENT_WIDTH_PT,
            budgetCapPt = PDF_PAGE_HEIGHT_PT.toFloat(),
            drawnMaxDiaMm = layout(spec, resolved, sheet = null).maxDiaMm,
        )
        return bodyRunBreakGapsX(
            bodies = resolved.filterIsInstance<ResolvedBody>().map { spec.bodyForPdf(it) },
            xAt = fit.xMap::xAt,
            rPx = { dia -> dia * 0.5f * fit.diaPtPerMm },
            outline = Paint().apply { strokeWidth = sheetOutlinePt },
            truePtPerMm = fit.diaPtPerMm,
            breakMinFracOfTrue = settings.sBreakThresholdFrac,
            blends = bodyBlends(spec, resolved),
            keywayAvoidSpansMm = bodyKeywayProtectedSpansMm(spec),
            boltRowAvoidSpansMm = boltRowProtectedSpansMm(spec),
        ).size
    }

    @Test
    fun `no sheet keeps the linear map`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val l = layout(spec, resolveComponents(spec), sheet = null)
        assertNull(l.sheet)
        listOf(0f, 1000f, 5000f, 300f * inch).forEach { mm ->
            assertEquals(l.contentLeftPx + (mm - l.minXMm) * l.pxPerMm, l.xPx(mm), 0f)
            assertEquals((mm * 0.5f) * l.pxPerMm, l.rPx(mm), 0f)
        }
    }

    @Test
    fun `the compressed map round-trips and is not linear`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val resolved = resolveComponents(spec)
        val sheet = profile(spec, resolved)!!
        val l = layout(spec, resolved, sheet)
        listOf(-6f, 0f, 10f, 45f, 100f, 200f, 274f, 290f, 300f).map { it * inch }.forEach { mm ->
            assertEquals("round-trip at $mm", mm, l.xMmFromPx(l.xPx(mm)), 0.5f)
        }
        val linear = layout(spec, resolved, sheet = null)
        // The diameter scale is the sheet's, far above the width-fitted linear scale.
        assertTrue(l.pxPerMm > 3f * linear.pxPerMm)
        // Length and diameter no longer share a scale: the long body is foreshortened.
        val bodyPxPerMm = (l.xPx(200f * inch) - l.xPx(100f * inch)) / (100f * inch)
        assertTrue("body $bodyPxPerMm vs diameter ${l.pxPerMm}", bodyPxPerMm < 0.5f * l.pxPerMm)
        // The drawn window still spans the content rect, start to end.
        assertEquals(l.contentLeftPx, l.xPx(spec.threads.first().startFromAftMm), 1f)
        assertEquals(l.contentRightPx, l.xPx(300f * inch), 1f)
    }

    @Test
    fun `the shaft takes at most the cap of the box height`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val resolved = resolveComponents(spec)
        val l = layout(spec, resolved, profile(spec, resolved))
        val contentH = l.contentBottomPx - l.contentTopPx
        assertTrue(2f * l.rPx(l.maxDiaMm) <= contentH * PREVIEW_SHEET_SHAFT_MAX_FRAC + 0.5f)
    }

    @Test
    fun `a shaft wider than its components stays inside the cap`() {
        // A new drawing with only an overall length and a shaft diameter: no stored component
        // carries the bare shaft's Ø, which sized the height off the 1 mm floor and drew the
        // shaft many times taller than the box (on-device QA). A thread narrower than the bare
        // shaft is the same case with a component present.
        val bare = ShaftSpec(overallLengthMm = 304f, autoBodyDiaMm = 200f)
        val threaded = bare.copy(
            threads = listOf(
                Threads(id = "T1", startFromAftMm = 0f, majorDiaMm = 127f, pitchMm = 6.35f, lengthMm = 152.4f),
            ),
        )
        listOf(bare, threaded).forEach { spec ->
            val resolved = resolveComponents(spec)
            val l = layout(spec, resolved, profile(spec, resolved))
            assertNotNull(l.sheet)
            assertEquals(200f, l.maxDiaMm, 0.01f)
            val contentH = l.contentBottomPx - l.contentTopPx
            val drawnPx = 2f * l.rPx(l.maxDiaMm)
            assertTrue(
                "drawn $drawnPx px vs cap ${contentH * PREVIEW_SHEET_SHAFT_MAX_FRAC}",
                drawnPx <= contentH * PREVIEW_SHEET_SHAFT_MAX_FRAC + 0.5f,
            )
        }
    }

    @Test
    fun `no overall length and a widened window fall back to the linear map`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val resolved = resolveComponents(spec)
        assertNull(profile(spec.copy(overallLengthMm = 0f), resolved))
        // A part hanging 10" past the FWD end widens the problem-mark window: linear.
        assertNull(profile(spec, resolved, extentMm = 0f to 310f * inch))
        // An overlap mark inside the drawn extent leaves the sheet layout in force.
        assertNotNull(profile(spec, resolved, extentMm = 30f * inch to 40f * inch))
    }

    @Test
    fun `a long body draws its S-break and a body opted out of compression does not`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val resolved = resolveComponents(spec)
        val l = layout(spec, resolved, profile(spec, resolved))
        val gaps = breakGapsPx(spec, resolved, l)
        // The preview is a miniature of the sheet: it breaks exactly the runs the sheet breaks —
        // the long body and, at the sheet's proportion, the short bare-shaft run between the AFT
        // taper and the liner.
        assertEquals(sheetBreakCount(spec, resolved), gaps.size)
        val gap = gapIn("B1", gaps, resolved, l).single()
        val cy = l.centerlineYPx.toInt()
        val bmp = render(spec, resolved, l)
        // The gap's centre on the centreline is bare paper; the body either side of it is filled.
        assertEquals(bg, bmp.getPixel(((gap.start + gap.endInclusive) / 2f).toInt(), cy))
        assertNotEquals(bg, bmp.getPixel((gap.start - 3f * (gap.endInclusive - gap.start)).toInt(), cy))

        val optOut = spec.copy(bodies = spec.bodies.map { it.copy(compressOnDrawing = false) })
        val optResolved = resolveComponents(optOut)
        val ol = layout(optOut, optResolved, profile(optOut, optResolved))
        val optGaps = breakGapsPx(optOut, optResolved, ol)
        assertTrue(gapIn("B1", optGaps, optResolved, ol).isEmpty())
        assertEquals(sheetBreakCount(optOut, optResolved), optGaps.size)
    }

    @Test
    fun `a keyway's width rides the diameter scale, not the axial map`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val resolved = resolveComponents(spec)
        val l = layout(spec, resolved, profile(spec, resolved))
        val taper = spec.tapers.first { it.id == "TA" }
        val axial = abs(l.xPx(taper.lengthMm) - l.xPx(0f)) / taper.lengthMm
        assertTrue("the taper is foreshortened", axial < 0.5f * l.pxPerMm)
        val bmp = render(spec, resolved, l)
        // Middle of the slot (open at the SET, 14" long): count the white void through the centreline.
        val x = l.xPx(taper.keywayLengthMm / 2f).toInt()
        val cy = l.centerlineYPx.toInt()
        var top = cy
        while (top > 0 && bmp.getPixel(x, top - 1) == 0xFFFFFFFF.toInt()) top--
        var bot = cy
        while (bot < h - 1 && bmp.getPixel(x, bot + 1) == 0xFFFFFFFF.toInt()) bot++
        val voidPx = (bot - top + 1).toFloat()
        val diaScaleWidth = taper.keywayWidthMm * l.pxPerMm
        assertEquals(diaScaleWidth, voidPx, 2f + 2f)
        assertTrue(abs(voidPx - taper.keywayWidthMm * axial) > 4f)
    }

    @Test
    fun `a tap selects through the map, the S-break gap included`() {
        val spec = PreviewSheetFixtures.longKeyedShaft()
        val resolved = resolveComponents(spec)
        val l = layout(spec, resolved, profile(spec, resolved))
        val gap = gapIn("B1", breakGapsPx(spec, resolved, l), resolved, l).single()
        assertTrue("the gap sits inside the body's drawn span", gap.start > l.xPx(60f * inch) && gap.endInclusive < l.xPx(284f * inch))
        val taperMid = (l.xPx(0f) + l.xPx(20f * inch)) / 2f
        assertEquals("TA", previewTapHitId(resolved, l.xMmFromPx(taperMid)))
        val linerMid = (l.xPx(30f * inch) + l.xPx(60f * inch)) / 2f
        assertEquals("L1", previewTapHitId(resolved, l.xMmFromPx(linerMid)))
        val fwdTaperMid = (l.xPx(284f * inch) + l.xPx(300f * inch)) / 2f
        assertEquals("TF", previewTapHitId(resolved, l.xMmFromPx(fwdTaperMid)))
    }
}
