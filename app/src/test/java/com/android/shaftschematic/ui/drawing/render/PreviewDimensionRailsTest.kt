package com.android.shaftschematic.ui.drawing.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.pdf.composeShaftPdfOnCanvas
import com.android.shaftschematic.pdf.formatLenDimDualLabel
import com.android.shaftschematic.pdf.schematicRailSpans
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The editor preview's dimension rails (`PreviewDimensionRails.kt`): the schematic sheet's own
 * spans, planned by the one rail planner and inked by the one rail renderer over the preview's
 * LINEAR x map — so every value matches the sheet and every span end lands on the edge the box
 * draws — inside the box, above the shaft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewDimensionRailsTest {

    private lateinit var styleBefore: FractionStyle

    @Before
    fun setUp() {
        styleBefore = FractionTypography.active.style
        // Plain fractions keep each value one drawText string, so the sheet's and the box's
        // values compare as strings (the SheetTextOverlapTest convention).
        FractionTypography.setStyle(FractionStyle.INLINE)
    }

    @After
    fun tearDown() {
        FractionTypography.setStyle(styleBefore)
    }

    private val inch = 25.4f

    /** AFT and FWD end tapers, excluded threads off both faces, two liners, an end body, a bolt row with its rail on. */
    private val spec = ShaftSpec(
        overallLengthMm = 300f * inch,
        tapers = listOf(
            Taper(id = "T1", startFromAftMm = 0f, lengthMm = 25.25f * inch, startDiaMm = 10.368f * inch, endDiaMm = 8.266f * inch),
            Taper(id = "T2", startFromAftMm = 284f * inch, lengthMm = 16f * inch, startDiaMm = 8f * inch, endDiaMm = 6.5f * inch),
        ),
        threads = listOf(
            Threads(id = "THA", startFromAftMm = -6f * inch, lengthMm = 6f * inch, majorDiaMm = 5.828f * inch, tpi = 4f, excludeFromOAL = true, isAftEnd = true),
            Threads(id = "THF", startFromAftMm = 300f * inch, lengthMm = 5f * inch, majorDiaMm = 4.5f * inch, tpi = 6f, excludeFromOAL = true, isAftEnd = false),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 40f * inch, lengthMm = 30.5f * inch, odMm = 10.99f * inch),
            Liner(id = "L2", startFromAftMm = 230f * inch, lengthMm = 34f * inch, odMm = 10.99f * inch),
        ),
        bodies = listOf(
            Body(id = "B1", startFromAftMm = 70.5f * inch, lengthMm = 159.5f * inch, diaMm = 10f * inch, showDimensionRail = true),
        ),
        couplerBoltSlots = listOf(
            CouplerBoltSlot(
                id = "S1", startFromAftMm = 2f * inch, holeDiaMm = 1f * inch, count = 4, spacingMm = 3f * inch,
                authoredReference = SlotAuthoredReference.FWD, showDimensionRail = true,
            ),
        ),
    )

    /** One liner printing metric, inline dual — the display axis the sheet resolves per component. */
    private val units = DisplayUnits(UnitSystem.INCHES, mapOf("L2" to UnitSystem.MILLIMETERS), dual = true)
    private val settings = PreviewRailSettings(displayUnits = units, tieringMode = PdfTieringMode.AUTO)

    private val pxPerPt = 2.75f

    private fun layoutFor(w: Float, h: Float, s: ShaftSpec = spec): (Float) -> ShaftLayout.Result {
        val resolved = resolveComponents(s)
        return { reserve ->
            ShaftLayout.compute(
                spec = s, leftPx = 16f, topPx = 8f + reserve, rightPx = w - 16f, bottomPx = h - 8f,
                resolvedComponents = resolved,
            )
        }
    }

    private fun plan(w: Float, h: Float, s: ShaftSpec = spec): PreviewRailPlan = planPreviewRails(
        rails = previewRailSpans(s, settings)!!,
        settings = settings,
        boxTopPx = 0f,
        boxHeightPx = h,
        pxPerPt = pxPerPt,
        railColor = Color.DKGRAY,
        haloColor = Color.LTGRAY,
        layoutFor = layoutFor(w, h, s),
    )

    private class Line(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    /** Records text boxes and lines in device px (the canvas matrix applied). */
    private class RecordingCanvas(w: Int, h: Int) : Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)) {
        val texts = mutableListOf<Pair<String, RectF>>()
        val lines = mutableListOf<Line>()

        private fun matrix(): Matrix = Matrix().also {
            @Suppress("DEPRECATION")
            getMatrix(it)
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            record(text, x, y, paint); super.drawText(text, x, y, paint)
        }
        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            record(text.substring(start, end), x, y, paint); super.drawText(text, start, end, x, y, paint)
        }
        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            record(text.subSequence(start, end).toString(), x, y, paint); super.drawText(text, start, end, x, y, paint)
        }
        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
            record(String(text, index, count), x, y, paint); super.drawText(text, index, count, x, y, paint)
        }
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val p = floatArrayOf(startX, startY, stopX, stopY)
            matrix().mapPoints(p)
            lines += Line(p[0], p[1], p[2], p[3])
            super.drawLine(startX, startY, stopX, stopY, paint)
        }

        private fun record(s: String, x: Float, y: Float, paint: Paint) {
            if (s.isBlank()) return
            val w = paint.measureText(s)
            val left = when (paint.textAlign) {
                Paint.Align.CENTER -> x - w / 2f
                Paint.Align.RIGHT -> x - w
                else -> x
            }
            val fm = paint.fontMetrics
            val r = RectF(left, y + fm.ascent, left + w, y + fm.descent)
            matrix().mapRect(r)
            texts += s to r
        }
    }

    @Test
    fun `the preview draws the schematic sheet's spans, tiers and values`() {
        val preview = previewRailSpans(spec, settings)!!
        val sheet = schematicRailSpans(spec, UnitSystem.INCHES, units, PdfPrefs(tieringMode = PdfTieringMode.AUTO))
        assertEquals(sheet.assignments, preview.assignments)
        assertEquals(sheet.oalDimSpan, preview.oalDimSpan)
        assertTrue("fixture prints component rails", preview.assignments.size >= 6)

        // The values the box inks are the values the sheet prints — same formatter, same units.
        val box = RecordingCanvas(1200, 400)
        val p = plan(1200f, 400f)
        assertEquals("a roomy box keeps every rail", preview.assignments.maxOf { it.rail } + 1, p.keptRails)
        p.draw(box)
        val page = RecordingCanvas(PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT)
        composeShaftPdfOnCanvas(
            page, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(), spec, UnitSystem.INCHES,
            ProjectInfo(), appVersion = "test", filename = "t",
            resolvedComponents = resolveComponents(spec), displayUnits = units,
        )
        val boxValues = box.texts.map { it.first }
        val pageValues = page.texts.map { it.first }.toSet()
        assertTrue("the box prints values", boxValues.size >= preview.assignments.size + 1)
        boxValues.forEach { v -> assertTrue("\"$v\" is not on the sheet", v in pageValues) }
        assertTrue("the OAL value is in the box", boxValues.any { it.startsWith("OAL ") })
        val l2Length = formatLenDimDualLabel((34f * inch).toDouble(), UnitSystem.MILLIMETERS, dual = true).primary
        assertTrue("L2's metric override reaches the box", boxValues.any { it.contains(l2Length) })
    }

    @Test
    fun `span ends land on the component edges the preview draws`() {
        val p = plan(1200f, 400f)
        val layout = p.layout
        // Excluded threads widen the window past both faces, so a face is NOT the content edge.
        assertTrue(layout.minXMm < 0f && layout.maxXMm > spec.overallLengthMm)
        val box = RecordingCanvas(1200, 400)
        p.draw(box)
        val verticals = box.lines.filter { abs(it.x0 - it.x1) < 0.01f && abs(it.y0 - it.y1) > 1f }.map { it.x0 }
        fun hasExtensionAt(mm: Float) = verticals.any { abs(it - layout.xPx(mm)) < 0.05f }
        // Liner edges, the AFT and FWD S.E.T. faces (the OAL rail with excluded threads), the
        // body's stored span and the bolt row's FWD face.
        listOf(40f, 70.5f, 230f, 264f, 0f, 300f).forEach { inches ->
            assertTrue("no extension line at ${inches}\"", hasExtensionAt(inches * inch))
        }
        // Every extension line foots above the shaft, never on or below it.
        val ext = box.lines.filter { abs(it.x0 - it.x1) < 0.01f && abs(it.y0 - it.y1) > 1f }
        ext.forEach { assertTrue(max(it.y0, it.y1) < layout.shaftTopPx) }
    }

    @Test
    fun `rails stay above the shaft, and inside a box with room for them`() {
        // The rails never reach the shaft at any size. They stay under the box top only where the
        // box has the room: a short box lets them run off its top (panned into view) rather than
        // shrinking the shaft or dropping a rail.
        for ((w, h) in listOf(1200f to 400f, 1080f to 360f, 1600f to 300f)) {
            val p = plan(w, h)
            val box = RecordingCanvas(w.toInt(), h.toInt())
            p.draw(box)
            val shaftTop = p.layout.shaftTopPx
            box.texts.forEach { (s, r) -> assertTrue("\"$s\" reaches the shaft in ${w}x$h", r.bottom < shaftTop) }
            box.lines.forEach { l -> assertTrue("a line reaches the shaft in ${w}x$h", max(l.y0, l.y1) < shaftTop) }
        }
        val roomy = plan(1200f, 600f)
        val box = RecordingCanvas(1200, 600)
        roomy.draw(box)
        box.texts.forEach { (s, r) -> assertTrue("\"$s\" above the box top", r.top >= 0f) }
        box.lines.forEach { l -> assertTrue("a line above the box top", min(l.y0, l.y1) >= 0f) }
    }

    @Test
    fun `a short box still draws every rail, running off its top`() {
        // The preview is a picture of the sheet's rails, not a fitted layout (on-device ruling):
        // nothing is dropped, and what does not fit is reached by panning.
        val rails = previewRailSpans(spec, settings)!!
        val full = rails.assignments.maxOf { it.rail } + 1
        val p = plan(1200f, 110f)
        assertEquals("no rail dropped", full, p.keptRails)
        assertEquals(rails.assignments.map { it.span } + rails.oalDimSpan, p.spans)
        val box = RecordingCanvas(1200, 110)
        p.draw(box)
        assertTrue("the OAL rail draws", box.texts.any { it.first.startsWith("OAL ") })
        assertTrue("the rails run above the box top", box.texts.any { it.second.top < 0f })
    }

    @Test
    fun `the rails reserve room only when the shaft's headroom is short`() {
        // A thin, long shaft in a tall box has room above it already: nothing reserved, so the
        // box lays out exactly as it does with the rails off.
        val p = plan(1200f, 600f)
        assertEquals(0f, p.reservePx, 0f)
        assertEquals(layoutFor(1200f, 600f)(0f), p.layout)
    }

    @Test
    fun `a phone box keeps every rail while it has room above the shaft`() {
        // The emulator case: a ~130 dp phone box (2.75 px/dp), a thin shaft, and a MID liner
        // located from the FWD S.E.T. whose datum rail stair-steps over the FWD liner — four rail
        // rows. A cap on the reservation dropped that rail while the box had room to spare; the
        // shaft sits low instead and every rail draws.
        val phone = spec.copy(
            liners = spec.liners + Liner(id = "L3", startFromAftMm = 150f * inch, lengthMm = 14f * inch, odMm = 10.99f * inch),
            bodies = emptyList(),
            couplerBoltSlots = emptyList(),
        )
        val rails = previewRailSpans(phone, settings)!!
        val full = rails.assignments.maxOf { it.rail } + 1
        assertTrue("fixture stacks at least three numbered rails, got $full", full >= 3)
        val w = 1130f
        val h = 130f * pxPerPt
        val p = plan(w, h, phone)
        assertEquals("no rail dropped", full, p.keptRails)
        val box = RecordingCanvas(w.toInt(), h.toInt())
        p.draw(box)
        val shaftTop = p.layout.shaftTopPx
        box.texts.forEach { (s, r) ->
            assertTrue("\"$s\" above the box top", r.top >= 0f)
            assertTrue("\"$s\" reaches the shaft", r.bottom < shaftTop)
        }
        // The shaft keeps its width-fitted size and its bottom padding.
        val base = layoutFor(w, h, phone)(0f)
        assertEquals(base.pxPerMm, p.layout.pxPerMm, 1e-4f)
        assertTrue(p.layout.centerlineYPx + p.layout.rPx(p.layout.maxDiaMm) <= h - 8f)
    }

    @Test
    fun `two values at one height keep a text height of air`() {
        // The emulator case: a 16 1/2" FWD taper and a bolt row 9 17/32" off the FWD face — both
        // values float over short spans at the FWD end and one steps beside the other; at the
        // sheet's few points of side clearance the two read as one string in the small preview.
        val fwdSet = 283.5f * inch
        val crowded = ShaftSpec(
            overallLengthMm = 300f * inch,
            tapers = listOf(
                Taper(id = "TA", startFromAftMm = 0f, lengthMm = 20f * inch, startDiaMm = 10f * inch, endDiaMm = 8.5f * inch),
                Taper(id = "TF", startFromAftMm = fwdSet, lengthMm = 16.5f * inch, startDiaMm = 8.5f * inch, endDiaMm = 7f * inch),
            ),
            liners = listOf(
                Liner(id = "LA", startFromAftMm = 30f * inch, lengthMm = 30f * inch, odMm = 10.5f * inch),
                Liner(id = "LM", startFromAftMm = 140f * inch, lengthMm = 20f * inch, odMm = 10.5f * inch),
                Liner(id = "LF", startFromAftMm = (300f - 40.53125f - 30f) * inch, lengthMm = 30f * inch, odMm = 10.5f * inch),
            ),
            couplerBoltSlots = listOf(
                CouplerBoltSlot(
                    id = "SF", startFromAftMm = (300f - 9.53125f) * inch, holeDiaMm = 1f * inch, count = 1,
                    authoredReference = SlotAuthoredReference.FWD, showDimensionRail = true,
                ),
            ),
            autoBodyDiaMm = 10f * inch,
        )
        val single = PreviewRailSettings(displayUnits = DisplayUnits(UnitSystem.INCHES))
        for ((w, h) in listOf(1130f to 130f * pxPerPt, 1600f to 400f)) {
            val p = planPreviewRails(
                rails = previewRailSpans(crowded, single)!!, settings = single,
                boxTopPx = 0f, boxHeightPx = h, pxPerPt = pxPerPt,
                railColor = Color.DKGRAY, haloColor = Color.LTGRAY,
                layoutFor = layoutFor(w, h, crowded),
            )
            val box = RecordingCanvas(w.toInt(), h.toInt())
            p.draw(box)
            val textH = box.texts.maxOf { it.second.height() }
            val r = box.texts.map { it.second }
            for (i in r.indices) for (j in i + 1 until r.size) {
                val a = r[i]
                val b = r[j]
                val sameHeight = min(a.bottom, b.bottom) - max(a.top, b.top) > 0f
                if (!sameHeight) continue
                val gap = max(a.left, b.left) - min(a.right, b.right)
                assertTrue(
                    "\"${box.texts[i].first}\" and \"${box.texts[j].first}\" ${gap}px apart (< text height $textH) in ${w}x$h",
                    gap >= textH * 0.9f,
                )
            }
        }
    }

    @Test
    fun `the reset-view button shows only away from the default view`() {
        assertFalse(com.android.shaftschematic.ui.drawing.compose.isPreviewViewMoved(1f, 0f, 0f))
        assertFalse(com.android.shaftschematic.ui.drawing.compose.isPreviewViewMoved(1.0001f, 0.2f, -0.2f))
        assertTrue(com.android.shaftschematic.ui.drawing.compose.isPreviewViewMoved(1.5f, 0f, 0f))
        assertTrue(com.android.shaftschematic.ui.drawing.compose.isPreviewViewMoved(1f, 30f, 0f))
        assertTrue(com.android.shaftschematic.ui.drawing.compose.isPreviewViewMoved(1f, 0f, -12f))
    }

    @Test
    fun `no overall length draws no rails`() {
        assertNull(previewRailSpans(spec.copy(overallLengthMm = 0f), settings))
    }

    @Test
    fun `render options keep the rails off by default`() {
        assertFalse(RenderOptions().showDimensionRails)
    }
}
