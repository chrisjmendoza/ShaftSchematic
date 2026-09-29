package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.COUPLING_PILOT_COMPONENT_ID
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.max
import kotlin.math.min

/**
 * No two strings on any printed sheet may overlap — proved on the real composers, across every
 * content combination the app can export, with a canvas that records the box of every string
 * the composers ink.
 *
 * On-device report: a Schematic + Runout consolidated sheet printed "TIR's taken looking:"
 * straight through the footer's "AFT Taper" heading. The footer's shared block had grown upward
 * past the band the consolidated composer reserved for it — the schematic sheet keeps a 1-inch
 * gap above its footer for exactly that growth, the consolidated sheet stacks its TIR line and
 * coupling face right on the band. The composer now reserves the footer's measured height
 * (`footerBlockHeightPt`); this test is the verification the report asked for, and it covers
 * every export combination so the next stacked element cannot regress the same way unnoticed.
 *
 * The fixture is the fullest footer the app prints (both tapers with keyways — so the clocking
 * note prints — a spooned keyway with its note, both threads, a body Ø list, an Item and a
 * Drawing label, long customer and vessel names that wrap) on a long shaft that compresses, with
 * readings, placements, a worn section, point readings and pits, so every pass that prints text
 * is exercised at once.
 *
 * Fractions are set INLINE for the run: a built-up fraction is several `drawText` calls whose
 * metric boxes legitimately touch, and inline is the WIDER construction, so any label collision
 * a stacked or diagonal sheet could have, this sheet has too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetTextOverlapTest {

    private class TextBox(val text: String, val rect: RectF)

    /**
     * A canvas that records the metric box of every string drawn through it, mapped through the
     * canvas's current transform so a rotated in-profile value lands where it prints.
     */
    private class RecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val boxes = mutableListOf<TextBox>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            record(text, x, y, paint)
            super.drawText(text, x, y, paint)
        }

        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            record(text.substring(start, end), x, y, paint)
            super.drawText(text, start, end, x, y, paint)
        }

        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            record(text.subSequence(start, end).toString(), x, y, paint)
            super.drawText(text, start, end, x, y, paint)
        }

        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
            record(String(text, index, count), x, y, paint)
            super.drawText(text, index, count, x, y, paint)
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
            val m = Matrix()
            @Suppress("DEPRECATION")
            getMatrix(m)
            m.mapRect(r)
            boxes += TextBox(s, r)
        }
    }

    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()
    private val inch = 25.4f

    private val spec: ShaftSpec = ShaftSpec(
        overallLengthMm = 300f * inch,
        tapers = listOf(
            Taper(
                id = "T1", startFromAftMm = 0f, lengthMm = 25.25f * inch,
                startDiaMm = 10.368f * inch, endDiaMm = 8.266f * inch,
                keywayWidthMm = 1.75f * inch, keywayDepthMm = 0.625f * inch,
                keywayLengthMm = 21.25f * inch, keywaySpooned = true,
            ),
            Taper(
                id = "T2", startFromAftMm = 284f * inch, lengthMm = 16f * inch,
                startDiaMm = 8f * inch, endDiaMm = 6.5f * inch,
                keywayWidthMm = 1.5f * inch, keywayDepthMm = 0.5f * inch, keywayLengthMm = 12f * inch,
            ),
        ),
        threads = listOf(
            Threads(
                id = "THA", startFromAftMm = -6f * inch, lengthMm = 6f * inch,
                majorDiaMm = 5.828f * inch, tpi = 4f, excludeFromOAL = true, isAftEnd = true,
            ),
            Threads(
                id = "THF", startFromAftMm = 300f * inch, lengthMm = 5f * inch,
                majorDiaMm = 4.5f * inch, tpi = 6f, excludeFromOAL = true, isAftEnd = false,
            ),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 40f * inch, lengthMm = 30f * inch, odMm = 10.99f * inch),
            Liner(id = "L2", startFromAftMm = 230f * inch, lengthMm = 34f * inch, odMm = 10.99f * inch),
        ),
        bodies = listOf(
            Body(
                id = "B1", startFromAftMm = 70f * inch, lengthMm = 160f * inch, diaMm = 10f * inch,
                label = "SKF section", showDiaOnDrawing = true,
                blendFwdMm = 2f * inch, blendFwdSeal = true, blendFwdSealLenMm = 8f * inch,
            ),
        ),
        couplerBoltSlots = listOf(
            CouplerBoltSlot(
                id = "S1", startFromAftMm = 2f * inch, holeDiaMm = 1f * inch, count = 6,
                spacingMm = 3f * inch, authoredReference = SlotAuthoredReference.FWD,
                holeStyle = BoltHoleStyle.SEAM,
            ),
            CouplerBoltSlot(
                id = "S2", startFromAftMm = 6f * inch, holeDiaMm = 0.75f * inch, count = 1,
                authoredReference = SlotAuthoredReference.FWD, holeStyle = BoltHoleStyle.CROSS,
            ),
        ),
    )

    private val project = ProjectInfo(
        customer = "Tidewater Marine Services Incorporated of Louisiana",
        vessel = "M/V Titan of the Northern Seas",
        side = ShaftPosition.PORT,
        jobNumber = "935037",
        item = "Port line shaft",
        drawingLabel = "Final",
    )

    private val readings = RunoutReadings(
        readings = listOf(
            RunoutReading("L1", 0, 0.05f * inch / 100f, 3),
            RunoutReading("L1", 1, 0.002f * inch, 15),
            RunoutReading("B1", 0, 0.004f * inch, 7),
            RunoutReading("B1", 3, 0.001f * inch, 21),
            RunoutReading("T2", 1, 0.003f * inch, 1),
            RunoutReading(COUPLING_PILOT_COMPONENT_ID, 0, 0.002f * inch, 12),
        ),
    )

    private val wear = WearRecord(
        wornSections = listOf(
            WornSection(startFromAftMm = 45f * inch, lengthMm = 6f * inch, diaMm = listOf(10.982f * inch, 10.978f * inch)),
        ),
        diaReadings = listOf(
            WearDiaReading(componentId = "L2", axialMm = 10f * inch, diaMm = 10.985f * inch),
            WearDiaReading(componentId = "B1", axialMm = 80f * inch, diaMm = 9.997f * inch),
        ),
        pits = listOf(
            WearPit(componentId = "L1", axialMm = 12f * inch, size = PitSize.LARGE),
            WearPit(componentId = "L2", axialMm = 20f * inch, size = PitSize.SMALL),
        ),
    )

    private val resolved = resolveComponents(spec)
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

    private fun canvas(): RecordingCanvas =
        RecordingCanvas(Bitmap.createBitmap(pageW.toInt(), pageH.toInt(), Bitmap.Config.ARGB_8888))

    /** Pairs of recorded strings whose boxes (each pulled in by [inset]) overlap. */
    private fun overlaps(boxes: List<TextBox>, inset: Float = 1f): List<Pair<TextBox, TextBox>> {
        val out = mutableListOf<Pair<TextBox, TextBox>>()
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i].rect
            val b = boxes[j].rect
            val ox = min(a.right, b.right) - max(a.left, b.left) - 2 * inset
            val oy = min(a.bottom, b.bottom) - max(a.top, b.top) - 2 * inset
            if (ox > 0f && oy > 0f) out += boxes[i] to boxes[j]
        }
        return out
    }

    private fun assertNoOverlap(name: String, c: RecordingCanvas) {
        // The classic runout sheet is the leanest: one header line, the OAL, the readings and
        // the TIR line — still well past a handful.
        assertTrue(
            "$name drew only ${c.boxes.size} string(s): ${c.boxes.map { it.text }}",
            c.boxes.size >= 6,
        )
        val bad = overlaps(c.boxes)
        assertTrue(
            "$name: ${bad.size} overlapping string pair(s):\n" + bad.joinToString("\n") { (a, b) ->
                "  \"${a.text}\" ${a.rect}  ×  \"${b.text}\" ${b.rect}"
            },
            bad.isEmpty(),
        )
    }

    private fun consolidated(
        includeBubbles: Boolean,
        includeWearInfo: Boolean,
        blank: Boolean,
        face: Boolean,
        dual: Boolean = false,
    ): RecordingCanvas {
        val c = canvas()
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, spec,
            config = RunoutConfig(tirDirection = TirDirection.AFT, showCouplingFace = face),
            project = project, unit = UnitSystem.INCHES,
            displayUnits = DisplayUnits(UnitSystem.INCHES, dual = dual),
            resolvedComponents = resolved,
            runoutReadings = readings, wearRecord = wear,
            blankValues = blank, consolidated = true,
            includeBubbles = includeBubbles, includeWearInfo = includeWearInfo,
        )
        return c
    }

    // ── The reported sheet ────────────────────────────────────────────────────

    @Test
    fun `schematic plus runout - the TIR line clears the footer`() {
        val c = consolidated(includeBubbles = true, includeWearInfo = false, blank = false, face = false)
        assertNoOverlap("Schematic + Runout", c)
        val tir = c.boxes.first { it.text.startsWith("TIR's taken") }
        val heading = c.boxes.first { it.text == FOOTER_AFT_TAPER_HEADER }
        assertTrue(
            "TIR line (bottom ${tir.rect.bottom}) must sit above the footer heading (top ${heading.rect.top})",
            tir.rect.bottom <= heading.rect.top,
        )
    }

    // ── Every consolidated combination ───────────────────────────────────────

    @Test
    fun `every consolidated variant prints with no overlapping text`() {
        for (bubbles in listOf(true, false)) for (wearInfo in listOf(true, false)) {
            if (!bubbles && !wearInfo) continue   // not an offered variant (schematic alone is its own sheet)
            for (blank in listOf(false, true)) for (face in listOf(false, true)) {
                val name = "consolidated bubbles=$bubbles wear=$wearInfo blank=$blank face=$face"
                assertNoOverlap(name, consolidated(bubbles, wearInfo, blank, face))
            }
        }
    }

    @Test
    fun `dual units on the consolidated sheet print with no overlapping text`() {
        assertNoOverlap(
            "consolidated all-three, dual units",
            consolidated(includeBubbles = true, includeWearInfo = true, blank = false, face = true, dual = true),
        )
    }

    // ── The classic runout sheet ──────────────────────────────────────────────

    @Test
    fun `the classic runout sheet prints with no overlapping text`() {
        for (blank in listOf(false, true)) for (face in listOf(false, true)) {
            val c = canvas()
            composeRunoutPdfOnCanvas(
                c, pageW, pageH, spec,
                config = RunoutConfig(tirDirection = TirDirection.FORWARD, showCouplingFace = face),
                project = project, unit = UnitSystem.INCHES,
                resolvedComponents = resolved, runoutReadings = readings,
                blankValues = blank, consolidated = false,
            )
            assertNoOverlap("classic runout blank=$blank face=$face", c)
        }
    }

    // ── The schematic sheet ───────────────────────────────────────────────────

    @Test
    fun `the schematic sheet prints with no overlapping text`() {
        for (blank in listOf(false, true)) for (dual in listOf(false, true)) {
            val c = canvas()
            composeShaftPdfOnCanvas(
                c, pageW, pageH, spec, UnitSystem.INCHES, project,
                appVersion = "test", filename = "935037_Titan_PORT.pdf",
                options = PdfExportOptions(blankValues = blank),
                resolvedComponents = resolved,
                displayUnits = DisplayUnits(UnitSystem.INCHES, dual = dual),
            )
            assertNoOverlap("schematic blank=$blank dual=$dual", c)
        }
    }
}
