package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX
import com.android.shaftschematic.geom.PROFILE_MAX_SHAFT_HEIGHT_PT
import com.android.shaftschematic.geom.PROFILE_MIN_SHAFT_HEIGHT_PT
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The consolidated sheet's vertical solve on a long, big shaft with every runout element elected
 * — the sheet that collapsed (on-device report): a 368 1/2" Ø11" shaft, blank draft, bubbles
 * and the coupling face, printed 0.2" tall across 40% of the page. Pins the height floor, the
 * full-width profile and an honest rail reservation on the real composer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunoutSheetLayoutTest {

    private val inch = 25.4f
    private val margin = 36f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val spec = ShaftSpec(
        overallLengthMm = 368.5f * inch,
        autoBodyDiaMm = 10.5f * inch,
        tapers = listOf(
            Taper(
                id = "TA", startFromAftMm = 0f, lengthMm = 25.1875f * inch,
                startDiaMm = 8.269f * inch, endDiaMm = 10.368f * inch,
                keywayWidthMm = 1.75f * inch, keywayDepthMm = 0.75f * inch,
                keywayLengthMm = 21.5f * inch, keywaySpooned = true,
            ),
            Taper(
                id = "TF", startFromAftMm = 353.75f * inch, lengthMm = 14.75f * inch,
                startDiaMm = 10.357f * inch, endDiaMm = 9.129f * inch,
                keywayWidthMm = 1.75f * inch, keywayDepthMm = 0.6875f * inch,
                keywayLengthMm = 12f * inch, keywaySpooned = true,
            ),
        ),
        threads = listOf(
            Threads(
                id = "TH", startFromAftMm = -5.8125f * inch, lengthMm = 5.8125f * inch,
                majorDiaMm = 5.25f * inch, tpi = 4f, excludeFromOAL = true, isAftEnd = true,
            ),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 25.5625f * inch, lengthMm = 52.375f * inch, odMm = 11f * inch),
            Liner(id = "L2", startFromAftMm = 133f * inch, lengthMm = 50.625f * inch, odMm = 11f * inch),
            Liner(id = "L3", startFromAftMm = 258.5f * inch, lengthMm = 18f * inch, odMm = 11f * inch),
        ),
    )

    private val project = ProjectInfo(customer = "C", vessel = "V", side = ShaftPosition.STBD, jobNumber = "1")

    /** Records every straight line the composer strokes. */
    private class LineCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val lines = mutableListOf<FloatArray>()
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            lines += floatArrayOf(startX, startY, stopX, stopY)
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
    }

    private fun layout(
        blank: Boolean,
        includeWearInfo: Boolean,
        consolidated: Boolean = true,
        canvas: Canvas = Canvas(Bitmap.createBitmap(pageW.toInt(), pageH.toInt(), Bitmap.Config.ARGB_8888)),
        heightScale: Float = 1f,
        spec: ShaftSpec = this.spec,
    ): RunoutSheetLayout {
        var out: RunoutSheetLayout? = null
        composeRunoutPdfOnCanvas(
            canvas, pageW, pageH,
            spec = spec,
            config = RunoutConfig(showCouplingFace = true, heightScale = heightScale),
            project = project,
            unit = UnitSystem.INCHES,
            resolvedComponents = resolveComponents(spec),
            blankValues = blank,
            consolidated = consolidated,
            includeBubbles = true,
            includeWearInfo = includeWearInfo,
            layoutProbe = { out = it },
        )
        return requireNotNull(out)
    }

    @Test
    fun `a blank bubble sheet keeps the shaft at the height floor`() {
        for (wear in listOf(false, true)) {
            val l = layout(blank = true, includeWearInfo = wear)
            assertTrue(
                "wear=$wear: drawn ${l.shaftHeightPt} pt, under the ${PROFILE_MIN_SHAFT_HEIGHT_PT} pt floor",
                l.shaftHeightPt >= PROFILE_MIN_SHAFT_HEIGHT_PT - 0.01f,
            )
        }
    }

    @Test
    fun `a blank bubble sheet spans the content width`() {
        val l = layout(blank = true, includeWearInfo = false)
        assertEquals("profile starts at the content left", margin, l.profileLeftX, 0.5f)
        assertEquals("profile ends at the content right", pageW - margin, l.profileRightX, 0.5f)
    }

    @Test
    fun `the blank footer gives up handwriting pitch before the shaft drops under the floor`() {
        val blank = layout(blank = true, includeWearInfo = false)
        assertTrue("the write-in footer tightened", blank.footerBlockPt < 200f)
        // The printed sheet has room to spare and keeps its reservation untouched.
        val printed = layout(blank = false, includeWearInfo = false)
        assertTrue(printed.shaftHeightPt > blank.shaftHeightPt)
    }

    @Test
    fun `the rail block reserves at least the lift the drawn rails take`() {
        for (blank in listOf(false, true)) {
            val l = layout(blank = blank, includeWearInfo = false)
            assertTrue(
                "blank=$blank: reserved ${l.railLiftReservedPt} pt, drawn rails lift ${l.railLiftDrawnPt} pt",
                l.railLiftReservedPt >= l.railLiftDrawnPt,
            )
        }
    }

    /**
     * The classic sheet's OAL arrow ends `(aft x, fwd x)`: the long horizontal stroke whose ends
     * carry witness lines rising past it.
     */
    private fun classicOalEnds(blank: Boolean, spec: ShaftSpec = this.spec): Pair<RunoutSheetLayout, Pair<Float, Float>> {
        val c = LineCanvas(Bitmap.createBitmap(pageW.toInt(), pageH.toInt(), Bitmap.Config.ARGB_8888))
        val l = layout(blank = blank, includeWearInfo = false, consolidated = false, canvas = c, spec = spec)
        val long = c.lines.filter { it[1] == it[3] && kotlin.math.abs(it[2] - it[0]) > 20f }
        val vertical = c.lines.filter { it[0] == it[2] }
        fun witnessed(x: Float, y: Float) =
            vertical.any { kotlin.math.abs(it[0] - x) < 0.5f && minOf(it[1], it[3]) < y && maxOf(it[1], it[3]) > y }
        val row = long.groupBy { it[1] }.values.first { r ->
            witnessed(r.minOf { minOf(it[0], it[2]) }, r[0][1]) && witnessed(r.maxOf { maxOf(it[0], it[2]) }, r[0][1])
        }
        return l to (row.minOf { minOf(it[0], it[2]) } to row.maxOf { maxOf(it[0], it[2]) })
    }

    @Test
    fun `the classic runout sheet's OAL runs face to face, not from the excluded thread`() {
        for (blank in listOf(false, true)) {
            val (l, ends) = classicOalEnds(blank)
            assertTrue("premise: the excluded thread widens the drawing", l.aftSetX - l.profileLeftX > 5f)
            assertEquals("blank=$blank: OAL starts at the AFT S.E.T. face", l.aftSetX, ends.first, 0.5f)
            assertEquals("blank=$blank: OAL ends at the FWD S.E.T. face", l.fwdSetX, ends.second, 0.5f)
        }
    }

    /**
     * A 100" shaft: tapers [4", 14"] and [86", 96"] (or flush to the ends without threads), a
     * liner mid-shaft, and 4" end threads INCLUDED in the OAL where asked.
     */
    private fun threadedSpec(aftThread: Boolean, fwdThread: Boolean): ShaftSpec {
        val aftTaperStart = if (aftThread) 4f else 0f
        val fwdTaperEnd = if (fwdThread) 96f else 100f
        return ShaftSpec(
            overallLengthMm = 100f * inch,
            autoBodyDiaMm = 6f * inch,
            tapers = listOf(
                Taper(
                    id = "TA", startFromAftMm = aftTaperStart * inch, lengthMm = 10f * inch,
                    startDiaMm = 5f * inch, endDiaMm = 6f * inch,
                ),
                Taper(
                    id = "TF", startFromAftMm = (fwdTaperEnd - 10f) * inch, lengthMm = 10f * inch,
                    startDiaMm = 6f * inch, endDiaMm = 5f * inch,
                ),
            ),
            threads = buildList {
                if (aftThread) add(
                    Threads(id = "THA", startFromAftMm = 0f, lengthMm = 4f * inch, majorDiaMm = 4f * inch, tpi = 4f, isAftEnd = true)
                )
                if (fwdThread) add(
                    Threads(id = "THF", startFromAftMm = 96f * inch, lengthMm = 4f * inch, majorDiaMm = 4f * inch, tpi = 4f, isAftEnd = false)
                )
            },
            liners = listOf(Liner(id = "L", startFromAftMm = 40f * inch, lengthMm = 20f * inch, odMm = 6.5f * inch)),
        )
    }

    @Test
    fun `the classic runout sheet's OAL takes in an included end thread`() {
        for (blank in listOf(false, true)) {
            val (l, ends) = classicOalEnds(blank, threadedSpec(aftThread = true, fwdThread = true))
            assertTrue("premise: the S.E.T. sits in from the AFT face", l.aftSetX - l.profileLeftX > 5f)
            assertTrue("premise: the S.E.T. sits in from the FWD face", l.profileRightX - l.fwdSetX > 5f)
            assertEquals("blank=$blank: OAL starts at the AFT shaft face", l.profileLeftX, ends.first, 0.5f)
            assertEquals("blank=$blank: OAL ends at the FWD shaft face", l.profileRightX, ends.second, 0.5f)
        }
    }

    @Test
    fun `the classic runout sheet's OAL treats each end on its own`() {
        val (l, ends) = classicOalEnds(blank = false, threadedSpec(aftThread = true, fwdThread = false))
        assertEquals("the included AFT thread is in the OAL", l.profileLeftX, ends.first, 0.5f)
        assertEquals("no FWD thread: the FWD S.E.T.", l.fwdSetX, ends.second, 0.5f)
    }

    @Test
    fun `the classic runout sheet's OAL on a shaft with no threads runs S_E_T_ to S_E_T_`() {
        val (l, ends) = classicOalEnds(blank = false, threadedSpec(aftThread = false, fwdThread = false))
        assertEquals(l.aftSetX, ends.first, 0.5f)
        assertEquals(l.fwdSetX, ends.second, 0.5f)
        assertEquals("the S.E.T.s are the shaft faces here", l.profileLeftX, l.aftSetX, 0.5f)
    }

    @Test
    fun `the height slider's cap is what the sheet draws at the top of the track`() {
        // A bubble sheet on a long Ø11" shaft: the page budget, not the 1 1/2" band, is the cap.
        val cap = runoutSheetMaxShaftHeightPt(
            spec, RunoutConfig(showCouplingFace = true), project, UnitSystem.INCHES,
            resolvedComponents = resolveComponents(spec),
            includeWearInfo = false,
        )
        assertTrue("premise: the budget binds below the band ($cap pt)", cap < PROFILE_MAX_SHAFT_HEIGHT_PT - 1f)
        val atTop = layout(blank = false, includeWearInfo = false, heightScale = PROFILE_HEIGHT_SCALE_MAX)
        assertEquals(atTop.shaftHeightPt, cap, 0.01f)
        val atStandard = layout(blank = false, includeWearInfo = false)
        assertTrue("the standard height never reads above the cap", atStandard.shaftHeightPt <= cap + 0.01f)
    }
}
