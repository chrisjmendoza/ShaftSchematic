package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRailStyle
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PdfPageOrientation
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

/**
 * The composed undercut sheet's strip layout, read off where each strip's title prints (a title sits
 * at its strip's bottom, at its strip's left or right edge): a LANDSCAPE page with two sections
 * stacks them, each the full content width, one under the other (on-device request); three stack
 * too while every shaft keeps [UNDERCUT_LANDSCAPE_STACK3_MIN_SHAFT_PT], else they and four keep the
 * 2-column grid; one strip and portrait pages are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutLandscapeStackPdfTest {

    private fun inch(v: Float) = v * 25.4f
    private val margin = 36f

    private lateinit var styleBefore: FractionStyle

    @Before
    fun setUp() {
        // Plain fractions: every title is ONE drawText at its strip edge.
        styleBefore = FractionTypography.active.style
        FractionTypography.setStyle(FractionStyle.INLINE)
    }

    @After
    fun tearDown() {
        FractionTypography.setStyle(styleBefore)
    }

    private val labels = listOf("Liner A", "Liner B", "Liner C", "Liner D")

    private val spec = ShaftSpec(
        overallLengthMm = inch(240f),
        autoBodyDiaMm = inch(5f),
        tapers = listOf(
            Taper(id = "T1", startFromAftMm = 0f, lengthMm = inch(14f), startDiaMm = inch(3.833f), endDiaMm = inch(5f), taperRateText = "1:12"),
            Taper(id = "T2", startFromAftMm = inch(226f), lengthMm = inch(14f), startDiaMm = inch(5f), endDiaMm = inch(3.833f), taperRateText = "1:12"),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = inch(20f), lengthMm = inch(30f), odMm = inch(5.75f), label = labels[0]),
            Liner(id = "L2", startFromAftMm = inch(70f), lengthMm = inch(18f), odMm = inch(5.75f), label = labels[1]),
            Liner(id = "L3", startFromAftMm = inch(120f), lengthMm = inch(26f), odMm = inch(5.75f), label = labels[2]),
            Liner(id = "L4", startFromAftMm = inch(196f), lengthMm = inch(18f), odMm = inch(5.5f), label = labels[3]),
        ),
    )

    private fun cut(id: String, start: Float, len: Float, dia: Float, liner: String) = Undercut(
        id = id, startFromAftMm = inch(start), lengthMm = inch(len), diaMm = inch(dia),
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = liner,
    )

    private val busyL1 = listOf(
        cut("a1", 24f, 10f, 5.625f, "L1"),
        cut("a2", 27f, 3.25f, 5.5f, "L1"),
        cut("a3", 34f, 4.5f, 5.6875f, "L1"),
        cut("a4", 38.5f, 2.75f, 5.625f, "L1"),
    )
    private val oneL2 = listOf(cut("b1", 76f, 4f, 5.625f, "L2"))
    private val busyL3 = listOf(
        cut("c1", 123f, 5f, 5.625f, "L3"),
        cut("c2", 128f, 6.5f, 5.5625f, "L3"),
        cut("c3", 129.5f, 2f, 5.4375f, "L3"),
        cut("c4", 134.5f, 3.75f, 5.625f, "L3"),
        cut("c5", 138.25f, 2.25f, 5.6875f, "L3"),
    )
    private val oneL4 = listOf(cut("d1", 202f, 5f, 5.375f, "L4"))

    /** One title as drawn: where its strip's edge and bottom are. */
    private data class Title(val label: String, val x: Float, val y: Float, val align: Paint.Align)

    private class TitleCanvas(bmp: Bitmap, val labels: List<String>) : Canvas(bmp) {
        val titles = mutableListOf<Title>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            labels.firstOrNull { text.startsWith(it) }?.let { titles += Title(it, x, y, paint.textAlign) }
            super.drawText(text, x, y, paint)
        }
    }

    private fun titles(
        cuts: List<Undercut>,
        orientation: UndercutPageOrientation,
        railStyle: UndercutRailStyle = UndercutRailStyle.CHAIN,
    ): List<Title> {
        val record = UndercutRecord(undercuts = cuts, pageOrientation = orientation, railStyle = railStyle)
        val resolved = resolveComponents(spec)
        val page = undercutSheetOrientation(spec, resolved, record, blankValues = false)
        val w = page.widthPt
        val h = page.heightPt
        val canvas = TitleCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), labels)
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        composeUndercutPdf(
            ctor.newInstance(canvas, info), spec, ProjectInfo(), UnitSystem.INCHES,
            resolvedComponents = resolved, undercutRecord = record,
        )
        // The text fit's dry passes draw on their own canvases, so these are the printed titles.
        return canvas.titles
    }

    /** Every title sits on its strip's full-width edge: the page margin, left or right. */
    private fun assertFullWidth(ts: List<Title>, pageW: Float) {
        ts.forEach { t ->
            val edge = if (t.align == Paint.Align.RIGHT) pageW - margin else margin
            assertEquals("${t.label} on the full-width strip's edge", edge, t.x, 0.5f)
        }
    }

    private fun assertStackedTopToBottom(ts: List<Title>) {
        assertEquals(labels.filter { l -> ts.any { it.label == l } }, ts.map { it.label })
        ts.zipWithNext().forEach { (a, b) -> assertTrue("${b.label} under ${a.label}", b.y > a.y + 40f) }
    }

    @Test
    fun `two sections on a landscape page stack, each the full width`() {
        val ts = titles(oneL2 + oneL4, UndercutPageOrientation.LANDSCAPE)
        assertEquals(2, ts.size)
        assertFullWidth(ts, PdfPageOrientation.LANDSCAPE.widthPt.toFloat())
        assertStackedTopToBottom(ts)
        // Two busy sections too.
        val busy = titles(busyL1 + busyL3, UndercutPageOrientation.LANDSCAPE)
        assertEquals(2, busy.size)
        assertFullWidth(busy, PdfPageOrientation.LANDSCAPE.widthPt.toFloat())
        assertStackedTopToBottom(busy)
    }

    @Test
    fun `one landscape section and portrait pages are unchanged`() {
        val one = titles(oneL2, UndercutPageOrientation.LANDSCAPE)
        assertEquals(1, one.size)
        assertFullWidth(one, PdfPageOrientation.LANDSCAPE.widthPt.toFloat())
        val two = titles(oneL2 + oneL4, UndercutPageOrientation.PORTRAIT)
        assertFullWidth(two, PdfPageOrientation.PORTRAIT.widthPt.toFloat())
        assertStackedTopToBottom(two)
    }

    @Test
    fun `three sections stack on landscape while every shaft keeps its floor, else the grid`() {
        val landscapeW = PdfPageOrientation.LANDSCAPE.widthPt.toFloat()
        val light = titles(oneL2 + oneL4 + listOf(cut("a1", 24f, 10f, 5.625f, "L1")), UndercutPageOrientation.LANDSCAPE)
        assertEquals(3, light.size)
        assertFullWidth(light, landscapeW)
        assertStackedTopToBottom(light)
        // Two busy chained sections and a plain one still leave every shaft its floor.
        val chained = titles(busyL1 + oneL2 + busyL3, UndercutPageOrientation.LANDSCAPE)
        assertEquals(3, chained.size)
        assertFullWidth(chained, landscapeW)
        assertStackedTopToBottom(chained)
        // Three busy sections dimensioned From S.E.T. stack a baseline rail per cut over each
        // shaft: stacked they would draw flat shafts, so the sheet keeps the grid.
        val busyL2 = listOf(
            cut("b1", 72f, 3f, 5.625f, "L2"),
            cut("b2", 75f, 2.5f, 5.5f, "L2"),
            cut("b3", 77.5f, 4f, 5.625f, "L2"),
            cut("b4", 81.5f, 3f, 5.6875f, "L2"),
        )
        assertGrid(titles(busyL1 + busyL2 + busyL3, UndercutPageOrientation.LANDSCAPE, UndercutRailStyle.FROM_SET), 3)
    }

    @Test
    fun `four sections on landscape keep the grid`() {
        assertGrid(titles(busyL1 + oneL2 + busyL3 + oneL4, UndercutPageOrientation.LANDSCAPE), 4)
    }

    /** The 2-column grid: the first row's two titles share a baseline, one of them off the margins. */
    private fun assertGrid(ts: List<Title>, n: Int) {
        assertEquals(n, ts.size)
        assertEquals(ts[0].y, ts[1].y, 0.5f)
        val pageW = PdfPageOrientation.LANDSCAPE.widthPt.toFloat()
        assertTrue(
            "a grid title stands at the column split",
            ts.any { t -> t.x > margin + 1f && t.x < pageW - margin - 1f },
        )
    }
}
