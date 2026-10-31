package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.maxOuterDiaMm as largestDiaMm
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The Final tab's before/after sheet — the ORIGINAL above the FINAL on one page, through the ONE
 * schematic composer in two [SheetBand]s, under the final's footer.
 *
 * Proved on the real composers with a canvas that records every string (with its baseline) and
 * every drawing op. Fractions are set INLINE for the run so each value is one recorded string.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BeforeAfterPdfComposerTest {

    private lateinit var styleBefore: FractionStyle

    @Before
    fun setUp() {
        styleBefore = FractionTypography.active.style
        FractionTypography.setStyle(FractionStyle.INLINE)
    }

    @After
    fun tearDown() {
        FractionTypography.setStyle(styleBefore)
    }

    private class Text(val text: String, val x: Float, val baselineY: Float)

    /**
     * Records every string with its page-space baseline, and every op the schematic composer
     * draws through as a string — enough to compare two composes call for call.
     */
    private class OpRecorder(w: Float, h: Float) :
        Canvas(Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)) {

        val texts = mutableListOf<Text>()
        val ops = mutableListOf<String>()

        /** The vertical extent of every line, rectangle and path inked, in page coordinates. */
        val inkTop get() = inkYs.minOrNull() ?: Float.NaN
        val inkBottom get() = inkYs.maxOrNull() ?: Float.NaN
        private val inkYs = mutableListOf<Float>()

        /**
         * The drawn shaft's height: the vertical extent of everything stroked with the shaft's
         * outline paint (1.25 pt at 100% line weight) — rails, leaders and extension lines use the
         * thinner dimension paint, so only the profile itself counts.
         */
        val shaftHeight get() = (outlineYs.maxOrNull() ?: Float.NaN) - (outlineYs.minOrNull() ?: Float.NaN)
        private val outlineYs = mutableListOf<Float>()

        private fun outline(p: Paint, vararg ys: Float) {
            if (p.style == Paint.Style.STROKE && p.strokeWidth == OUTLINE_STROKE_PT) ys.forEach { outlineYs += it }
        }

        private fun mapPoint(x: Float, y: Float): FloatArray {
            val m = Matrix()
            @Suppress("DEPRECATION")
            getMatrix(m)
            return floatArrayOf(x, y).also { m.mapPoints(it) }
        }

        private fun mapRect(r: RectF): RectF {
            val m = Matrix()
            @Suppress("DEPRECATION")
            getMatrix(m)
            return RectF(r).also { m.mapRect(it) }
        }

        private fun paintKey(p: Paint) = "${p.style}/${p.color}/${p.strokeWidth}/${p.textSize}"

        private fun text(s: String, x: Float, y: Float, p: Paint) {
            val pt = mapPoint(x, y)
            ops += "text '$s' ${pt[0]},${pt[1]} ${p.textAlign} ${paintKey(p)}"
            if (s.isNotBlank()) texts += Text(s, pt[0], pt[1])
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            text(text, x, y, paint); super.drawText(text, x, y, paint)
        }

        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            text(text.substring(start, end), x, y, paint); super.drawText(text, start, end, x, y, paint)
        }

        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            text(text.subSequence(start, end).toString(), x, y, paint); super.drawText(text, start, end, x, y, paint)
        }

        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
            text(String(text, index, count), x, y, paint); super.drawText(text, index, count, x, y, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val a = mapPoint(startX, startY)
            val b = mapPoint(stopX, stopY)
            ops += "line ${a[0]},${a[1]} ${b[0]},${b[1]} ${paintKey(paint)}"
            inkYs += a[1]; inkYs += b[1]
            outline(paint, a[1], b[1])
            super.drawLine(startX, startY, stopX, stopY, paint)
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            val r = mapRect(RectF(left, top, right, bottom))
            ops += "rect $r ${paintKey(paint)}"
            inkYs += r.top; inkYs += r.bottom
            outline(paint, r.top, r.bottom)
            super.drawRect(left, top, right, bottom, paint)
        }

        override fun drawRect(rect: RectF, paint: Paint) {
            val r = mapRect(rect)
            ops += "rect $r ${paintKey(paint)}"
            inkYs += r.top; inkYs += r.bottom
            outline(paint, r.top, r.bottom)
            super.drawRect(rect, paint)
        }

        override fun drawPath(path: Path, paint: Paint) {
            val b = RectF()
            @Suppress("DEPRECATION")
            path.computeBounds(b, true)
            val r = mapRect(b)
            ops += "path $r ${paintKey(paint)}"
            inkYs += r.top; inkYs += r.bottom
            outline(paint, r.top, r.bottom)
            super.drawPath(path, paint)
        }

        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            val p = mapPoint(cx, cy)
            ops += "circle ${p[0]},${p[1]} r=$radius ${paintKey(paint)}"
            super.drawCircle(cx, cy, radius, paint)
        }

        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) {
            ops += "arc ${mapRect(oval)} $startAngle $sweepAngle ${paintKey(paint)}"
            super.drawArc(oval, startAngle, sweepAngle, useCenter, paint)
        }
    }

    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText(),
    )
    private val unit = doc.preferredUnit ?: UnitSystem.INCHES
    private val original: ShaftSpec = doc.spec
    private val final: ShaftSpec = movedFwdLinerFinal(original)
    private val originalResolved: List<ResolvedComponent> = resolveComponents(original)
    private val finalResolved: List<ResolvedComponent> = resolveComponents(final)

    // A Side is set so the footer's badge reads "PORT  FINAL" — the band badge is then the only
    // string that is exactly "FINAL".
    private val project = ProjectInfo(
        customer = doc.customer, vessel = doc.vessel, side = ShaftPosition.PORT,
        jobNumber = doc.jobNumber, item = doc.item, drawingLabel = "Final",
    )

    private fun beforeAfter(options: PdfExportOptions = PdfExportOptions()): OpRecorder =
        OpRecorder(pageW, pageH).also { c ->
            composeBeforeAfterPdfOnCanvas(
                c, pageW, pageH,
                originalSpec = original, finalSpec = final,
                unit = unit, project = project, appVersion = "test", filename = "test.pdf",
                options = options,
                originalResolved = originalResolved, finalResolved = finalResolved,
            )
        }

    private fun schematic(
        spec: ShaftSpec,
        resolved: List<ResolvedComponent>,
        band: SheetBand?,
        options: PdfExportOptions = PdfExportOptions(),
    ): OpRecorder =
        OpRecorder(pageW, pageH).also { c ->
            composeShaftPdfOnCanvas(
                c, pageW, pageH,
                spec = spec, unit = unit, project = project, appVersion = "test", filename = "test.pdf",
                options = options,
                resolvedComponents = resolved,
                band = band,
            )
        }

    private fun bands(options: PdfExportOptions = PdfExportOptions()): BeforeAfterBands = beforeAfterBands(
        pageW, pageH, original, final, unit, project, PdfPrefs(), options, DisplayUnits.single(unit),
        heightScale = 1f, linerMinFracOfTrue = 0f,
    )

    private fun dividerY(options: PdfExportOptions = PdfExportOptions()): Float =
        beforeAfterDividerY(pageH, footerBlock(options))

    private fun footerBlock(options: PdfExportOptions = PdfExportOptions()): Float =
        schematicFooterBlockPt(pageW, final, unit, project, PdfPrefs(), options, DisplayUnits.single(unit))

    @Test
    fun `each band carries its own badge, ORIGINAL above FINAL, at the left margin`() {
        val texts = beforeAfter().texts
        val originals = texts.filter { it.text == BEFORE_AFTER_ORIGINAL_BADGE }
        val finals = texts.filter { it.text == BEFORE_AFTER_FINAL_BADGE }

        assertEquals(1, originals.size)
        assertEquals(1, finals.size)
        assertTrue("ORIGINAL sits above FINAL", originals.single().baselineY < finals.single().baselineY)
        assertEquals(SCHEMATIC_PAGE_MARGIN_PT, originals.single().x, 0.01f)
        assertEquals(SCHEMATIC_PAGE_MARGIN_PT, finals.single().x, 0.01f)
        assertTrue("the ORIGINAL badge is in the top band", originals.single().baselineY < dividerY())
        assertTrue("the FINAL badge is in the bottom band", finals.single().baselineY > dividerY())
    }

    @Test
    fun `each band prints its own OAL rail and the footer is the final's, once`() {
        val divider = dividerY()
        val texts = beforeAfter().texts

        val oal = texts.filter { it.text.startsWith("OAL") }
        assertEquals("two OAL rails, one per drawing", 2, oal.size)
        assertEquals(1, oal.count { it.baselineY < divider })
        assertEquals(1, oal.count { it.baselineY > divider })

        val drawing = texts.filter { it.text == "Drawing: Final" }
        assertEquals("one footer for the sheet", 1, drawing.size)
        assertTrue("the footer hangs under the final", drawing.single().baselineY > divider)
    }

    @Test
    fun `everything above the divider is the original band and everything below it the final's`() {
        val divider = dividerY()
        val sheet = beforeAfter().texts

        val bands = bands()
        assertEquals(divider, bands.dividerY, 0f)
        val top = schematic(original, originalResolved, bands.original).texts
        val bottom = schematic(final, finalResolved, bands.final).texts

        assertTrue("the top band stays above the rule", top.all { it.baselineY < divider })
        assertTrue("the bottom band stays below the rule", bottom.all { it.baselineY > divider })
        assertEquals(
            top.map { it.text },
            sheet.filter { it.baselineY < divider }.map { it.text },
        )
        assertEquals(
            bottom.map { it.text },
            sheet.filter { it.baselineY > divider }.map { it.text },
        )
    }

    @Test
    fun `nothing prints on the divider rule`() {
        for (options in listOf(PdfExportOptions(), PdfExportOptions(blankValues = true))) {
            val divider = dividerY(options)
            val onRule = beforeAfter(options).texts.filter { abs(it.baselineY - divider) <= 2f }
            assertTrue("text on the rule (${options.blankValues}): ${onRule.map { it.text }}", onRule.isEmpty())
        }
    }

    /**
     * The rails are the tallest thing in a band: a band too short for them used to stack them up
     * past its top and across the rule into the drawing above. A blank draft's write-in footer
     * leaves the least room, so it is checked too.
     */
    @Test
    fun `neither drawing inks across the rule, blank draft included`() {
        for (options in listOf(PdfExportOptions(), PdfExportOptions(blankValues = true))) {
            val bands = bands(options)
            val top = schematic(original, originalResolved, bands.original, options)
            val bottom = schematic(final, finalResolved, bands.final, options)

            assertTrue(
                "the original's ink ends above the rule (blank=${options.blankValues}): ${top.inkBottom} vs ${bands.dividerY}",
                top.inkBottom <= bands.dividerY,
            )
            assertTrue(
                "the final's ink starts below the rule (blank=${options.blankValues}): ${bottom.inkTop} vs ${bands.dividerY}",
                bottom.inkTop >= bands.dividerY,
            )
            assertTrue("the original's ink stays on the page", top.inkTop >= SCHEMATIC_PAGE_MARGIN_PT)
        }
    }

    /**
     * One diameter scale for both bands: a comparison is only honest when a changed largest
     * diameter reads as a changed height. The final here has its largest-Ø liner turned down an
     * inch, so its drawn height must shrink by exactly the ratio of the two largest diameters.
     */
    @Test
    fun `both bands draw at one scale, so a changed largest diameter reads as a changed height`() {
        val maxLiner = original.liners.maxBy { it.odMm }
        assertEquals("the fixture's largest diameter is a liner", original.largestDiaMm(), maxLiner.odMm, 0.001f)
        val turned = original.copy(
            liners = original.liners.map { if (it.id == maxLiner.id) it.copy(odMm = it.odMm - 25.4f) else it },
        )
        assertTrue("turning it down lowers the shaft's largest diameter", turned.largestDiaMm() < original.largestDiaMm())

        val bands = beforeAfterBands(
            pageW, pageH, original, turned, unit, project, PdfPrefs(), PdfExportOptions(), DisplayUnits.single(unit),
            heightScale = 1f, linerMinFracOfTrue = 0f,
        )
        assertEquals(bands.original.diaPtPerMmCap!!, bands.final.diaPtPerMmCap!!, 0f)
        val top = schematic(original, originalResolved, bands.original).shaftHeight
        val bottom = schematic(turned, resolveComponents(turned), bands.final).shaftHeight

        assertTrue("a shaft is drawn: $top", top > 0f)
        assertTrue("the turned-down final draws shorter: $bottom vs $top", bottom < top)
        assertEquals(top * turned.largestDiaMm() / original.largestDiaMm(), bottom, 0.5f)
    }

    @Test
    fun `identical drawings print at identical heights`() {
        for (options in listOf(PdfExportOptions(), PdfExportOptions(blankValues = true))) {
            val bands = beforeAfterBands(
                pageW, pageH, original, original, unit, project, PdfPrefs(), options, DisplayUnits.single(unit),
                heightScale = 1f, linerMinFracOfTrue = 0f,
            )
            val top = schematic(original, originalResolved, bands.original, options).shaftHeight
            val bottom = schematic(original, originalResolved, bands.final, options).shaftHeight
            assertTrue("a shaft is drawn (blank=${options.blankValues})", top > 0f)
            assertEquals("blank=${options.blankValues}", top, bottom, 0.5f)
        }
    }

    @Test
    fun `a band covering the whole page draws exactly the full-page sheet`() {
        val page = schematic(original, originalResolved, band = null)
        val fullBand = schematic(
            original, originalResolved,
            SheetBand(SCHEMATIC_PAGE_MARGIN_PT, pageH - SCHEMATIC_PAGE_MARGIN_PT, badge = null, hostsFooter = true),
        )

        assertTrue("the sheet draws something", page.ops.size > 100)
        assertEquals(page.ops, fullBand.ops)
    }

    /** The full-page sheet's footer reserve is what the before/after sheet places its rule off. */
    @Test
    fun `the divider leaves the two drawings equal room`() {
        val footer = footerBlock()
        val divider = beforeAfterDividerY(pageH, footer)
        val topRoom = divider - SCHEMATIC_PAGE_MARGIN_PT
        val bottomRoom = (pageH - SCHEMATIC_PAGE_MARGIN_PT) - divider - footer
        assertEquals(topRoom, bottomRoom, 0.01f)
    }

    companion object {
        /** The schematic's shaft-outline stroke at 100% line weight. */
        private const val OUTLINE_STROKE_PT = 1.25f

        /** The fixture's final: its FWD-most liner moved 2" FWD, ids kept. */
        fun movedFwdLinerFinal(spec: ShaftSpec): ShaftSpec {
            val fwd = spec.liners.maxBy { it.startFromAftMm }
            return spec.copy(
                liners = spec.liners.map { if (it.id == fwd.id) it.copy(startFromAftMm = it.startFromAftMm + 50.8f) else it },
            )
        }
    }
}
