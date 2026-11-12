package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.geom.PROFILE_MIN_SHAFT_HEIGHT_PT
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
import com.android.shaftschematic.settings.PDF_TEXT_SCALES
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DualUnitLayout
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
                // Small end at the thread, large end into the shaft — the way every real AFT taper runs.
                startDiaMm = 8.266f * inch, endDiaMm = 10.368f * inch,
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
                // Both threads relieved, on every variant below: a relief is drawn, never footer text.
                reliefLenMm = 0.375f * inch, reliefDiaMm = 5.5f * inch, reliefAtAft = false,
            ),
            Threads(
                id = "THF", startFromAftMm = 300f * inch, lengthMm = 5f * inch,
                majorDiaMm = 4.5f * inch, tpi = 6f, excludeFromOAL = true, isAftEnd = false,
                reliefLenMm = 0.25f * inch, reliefDiaMm = 4.25f * inch, reliefAtAft = true,
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
        sheetSpec: ShaftSpec = spec,
        prefs: PdfPrefs = PdfPrefs(),
        dyePen: Boolean = false,
    ): RecordingCanvas {
        val c = canvas()
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, sheetSpec,
            config = RunoutConfig(tirDirection = TirDirection.AFT, showCouplingFace = face, showDyePenTest = dyePen),
            project = project, unit = UnitSystem.INCHES,
            pdfPrefs = prefs,
            displayUnits = DisplayUnits(UnitSystem.INCHES),
            resolvedComponents = if (sheetSpec === spec) resolved else resolveComponents(sheetSpec),
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

    // ── Dual units: the sheet keeps the shaft at its height floor ─────────────
    // A dual sheet's rails and footer cost more height than a single-unit sheet's — a stacked lane
    // is two lines tall, an inline dual footer line wraps — and the consolidated sheet gives room
    // back WHOLE (stacking, then the footer's line pitch, then the secondary unit) before the
    // drawn shaft falls under the paper band's floor. Under it, the compressed spans lost their breaks
    // and printed their values through the S.E.T. extension lines, and the in-profile Ø columns
    // stood taller than the shaft.

    /** The job with a captured FWD keyway and the bolt-hole Ø line on: two more FWD footer lines, each wrapping in dual. */
    private val fullFooterSpec: ShaftSpec = spec.copy(
        tapers = spec.tapers.map { if (it.id == "T2") it.copy(keywayOffsetFromSetMm = 0.5f * inch) else it },
    )
    private val fullFooterPrefs = PdfPrefs(footerBoltHoleDia = true)

    /** The consolidated sheet in [layout] dual units (`null` = single-unit), with its solved layout. */
    private fun probed(
        layout: DualUnitLayout?,
        sheetSpec: ShaftSpec = spec,
        prefs: PdfPrefs = PdfPrefs(),
        bubbles: Boolean = true,
        wearInfo: Boolean = true,
        blank: Boolean = false,
        face: Boolean = true,
        sheetH: Float = pageH,
    ): Pair<RecordingCanvas, RunoutSheetLayout> {
        val c = RecordingCanvas(Bitmap.createBitmap(pageW.toInt(), sheetH.toInt(), Bitmap.Config.ARGB_8888))
        var probe: RunoutSheetLayout? = null
        composeRunoutPdfOnCanvas(
            c, pageW, sheetH, sheetSpec,
            config = RunoutConfig(tirDirection = TirDirection.AFT, showCouplingFace = face),
            project = project, unit = UnitSystem.INCHES,
            pdfPrefs = if (layout == null) prefs else prefs.copy(dualUnitLayout = layout),
            displayUnits = DisplayUnits(UnitSystem.INCHES, dual = layout != null),
            resolvedComponents = if (sheetSpec === spec) resolved else resolveComponents(sheetSpec),
            runoutReadings = readings, wearRecord = wear,
            blankValues = blank, consolidated = true,
            includeBubbles = bubbles, includeWearInfo = wearInfo,
            layoutProbe = { probe = it },
        )
        return c to requireNotNull(probe)
    }

    private fun assertAtFloor(name: String, layout: RunoutSheetLayout) {
        assertTrue(
            "$name: shaft drawn ${layout.shaftHeightPt} pt, under the $PROFILE_MIN_SHAFT_HEIGHT_PT pt floor",
            layout.shaftHeightPt >= PROFILE_MIN_SHAFT_HEIGHT_PT - 0.01f,
        )
    }

    @Test
    fun `dual units on the consolidated sheet print with no overlapping text`() {
        for (layout in DualUnitLayout.entries) {
            for (bubbles in listOf(true, false)) for (wearInfo in listOf(true, false)) {
                if (!bubbles && !wearInfo) continue
                for (blank in listOf(false, true)) for (face in listOf(false, true)) {
                    val name = "dual $layout bubbles=$bubbles wear=$wearInfo blank=$blank face=$face"
                    val (c, out) = probed(layout, bubbles = bubbles, wearInfo = wearInfo, blank = blank, face = face)
                    assertNoOverlap(name, c)
                    assertAtFloor(name, out)
                }
            }
        }
    }

    @Test
    fun `a sheet that affords the floor prints exactly as asked`() {
        val (_, inline) = probed(DualUnitLayout.INLINE)
        assertEquals(SheetDualMode.INLINE, inline.dualMode)
        assertEquals(0f, inline.footerTightenedPt, 0f)
        val (_, single) = probed(null)
        assertEquals(SheetDualMode.SINGLE, single.dualMode)
        assertEquals(0f, single.footerTightenedPt, 0f)
    }

    @Test
    fun `stacking that would squeeze the shaft under the floor falls back to inline for the whole sheet`() {
        // The stacked rail block leaves the readable area the old gate asked for, but not once the
        // bubble rows under the shaft are paid for: the gate counts them now.
        val (c, out) = probed(DualUnitLayout.STACKED)
        assertEquals(SheetDualMode.INLINE, out.dualMode)
        assertAtFloor("stacked fallback", out)
        assertTrue(
            "an inline sheet prints a value and its mm on one line: ${c.boxes.map { it.text }}",
            c.boxes.any { "[" in it.text && "mm" in it.text },
        )
    }

    /** The TIR line sits above the footer, and the footer's last row stays on the page. */
    private fun assertFooterClear(name: String, c: RecordingCanvas, sheetH: Float) {
        val tir = c.boxes.first { it.text.startsWith("TIR's taken") }
        val heading = c.boxes.first { it.text == FOOTER_AFT_TAPER_HEADER }
        assertTrue(
            "$name: TIR line (bottom ${tir.rect.bottom}) must sit above the footer heading (top ${heading.rect.top})",
            tir.rect.bottom <= heading.rect.top,
        )
        val lowest = c.boxes.maxOf { it.rect.bottom }
        assertTrue("$name: lowest text ${lowest} runs past the page bottom $sheetH", lowest <= sheetH)
    }

    @Test
    fun `a dual footer that would squeeze the shaft under the floor tightens its pitch and keeps both units`() {
        // The tighter pitch alone affords the floor here, so the sheet stays dual INLINE — a dual
        // document printing one unit departs further from what was asked than a tighter footer.
        for (layout in DualUnitLayout.entries) {
            val name = "full footer, $layout"
            val (c, out) = probed(layout, sheetSpec = fullFooterSpec, prefs = fullFooterPrefs)
            assertEquals(name, SheetDualMode.INLINE, out.dualMode)
            assertTrue("$name: footer should tighten: ${out.footerTightenedPt}", out.footerTightenedPt > 0f)
            assertAtFloor(name, out)
            assertNoOverlap(name, c)
            assertFooterClear(name, c, pageH)
            assertTrue(
                "$name: a dual sheet prints its mm values: ${c.boxes.map { it.text }}",
                c.boxes.any { "[" in it.text && "mm" in it.text },
            )
        }
    }

    /** A page this short leaves the full-footer job no room for dual even at the tightest pitch. */
    private val noDualPageH = 580f

    @Test
    fun `past the tightest dual footer the sheet prints the primary unit only`() {
        for (layout in DualUnitLayout.entries) {
            val name = "short page, $layout"
            val (c, out) = probed(layout, sheetSpec = fullFooterSpec, prefs = fullFooterPrefs, sheetH = noDualPageH)
            assertEquals(name, SheetDualMode.SINGLE, out.dualMode)
            assertAtFloor(name, out)
            assertNoOverlap(name, c)
            assertFooterClear(name, c, noDualPageH)
            assertTrue(
                "$name: a primary-only sheet prints no mm anywhere: ${c.boxes.filter { "mm" in it.text }.map { it.text }}",
                c.boxes.none { "mm" in it.text },
            )
        }
        // The sheet it falls back to is the one the job prints single-unit as asked.
        val (_, asked) = probed(null, sheetSpec = fullFooterSpec, prefs = fullFooterPrefs, sheetH = noDualPageH)
        val (_, fellBack) = probed(DualUnitLayout.INLINE, sheetSpec = fullFooterSpec, prefs = fullFooterPrefs, sheetH = noDualPageH)
        assertEquals(asked.shaftHeightPt, fellBack.shaftHeightPt, 0f)
        assertEquals(asked.footerBlockPt, fellBack.footerBlockPt, 0f)
    }

    @Test
    fun `a primary-only sheet still tightens its footer when it must`() {
        // Shorter still: even the single-unit footer at its printed pitch leaves the shaft under
        // the floor, so after the units fall back the footer tightens too.
        val sheetH = 550f
        val (c, out) = probed(DualUnitLayout.INLINE, sheetSpec = fullFooterSpec, prefs = fullFooterPrefs, sheetH = sheetH)
        assertEquals(SheetDualMode.SINGLE, out.dualMode)
        assertTrue("footer should tighten: ${out.footerTightenedPt}", out.footerTightenedPt > 0f)
        assertAtFloor("shorter page", out)
        assertNoOverlap("shorter page", c)
        assertFooterClear("shorter page", c, sheetH)
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

    // ── Larger sheet text (Settings → Drawing → Footer / Dimension text size) ─────

    @Test
    fun `every text size prints with no overlapping text`() {
        // Each step on its own and both together: the footer band, the rail lanes and the Ø
        // callouts are each measured from the scaled paint, so none may crowd another.
        val combos = PDF_TEXT_SCALES.flatMap { s ->
            listOf(PdfPrefs(footerTextScale = s), PdfPrefs(dimTextScale = s), PdfPrefs(footerTextScale = s, dimTextScale = s))
        }
        val failures = mutableListOf<String>()
        fun check(name: String, c: RecordingCanvas) {
            val bad = overlaps(c.boxes)
            if (bad.isNotEmpty()) failures += "$name: " + bad.joinToString("; ") { (a, b) -> "\"${a.text}\" × \"${b.text}\"" }
        }
        for (prefs in combos) {
            val tag = "footer=${prefs.footerTextScale} dim=${prefs.dimTextScale}"
            for (wearInfo in listOf(true, false)) for (blank in listOf(false, true)) {
                check(
                    "consolidated wear=$wearInfo blank=$blank $tag",
                    consolidated(
                        includeBubbles = true, includeWearInfo = wearInfo, blank = blank, face = true,
                        prefs = prefs, dyePen = true,
                    ),
                )
            }
            for (blank in listOf(false, true)) {
                val c = canvas()
                composeShaftPdfOnCanvas(
                    c, pageW, pageH, spec, UnitSystem.INCHES, project,
                    appVersion = "test", filename = "935037_Titan_PORT.pdf",
                    pdfPrefs = prefs,
                    options = PdfExportOptions(blankValues = blank),
                    resolvedComponents = resolved,
                    displayUnits = DisplayUnits(UnitSystem.INCHES),
                )
                check("schematic blank=$blank $tag", c)
            }
            // The Final tab's Original + Final sheet: two drawings and one footer on one page —
            // the tightest page a larger footer has to fit.
            val finalSpec = spec.copy(
                liners = spec.liners.map { if (it.id == "L2") it.copy(lengthMm = 30f * inch) else it },
            )
            val ba = canvas()
            composeBeforeAfterPdfOnCanvas(
                ba, pageW, pageH, spec, finalSpec, UnitSystem.INCHES, project,
                appVersion = "test", filename = "935037_Titan_PORT_Final_BeforeAfter.pdf",
                pdfPrefs = prefs,
                originalResolved = resolved, finalResolved = resolveComponents(finalSpec),
            )
            check("before/after $tag", ba)
        }
        assertTrue("${failures.size} sheet(s) overlap:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** An ordinary job: one taper and its thread at each end, short names — a footer of ~7 rows. */
    private val plainSpec: ShaftSpec = spec.copy(
        bodies = emptyList(),
        couplerBoltSlots = emptyList(),
        threads = spec.threads.map { it.copy(reliefLenMm = 0f, reliefDiaMm = 0f) },
        tapers = spec.tapers.map { it.copy(keywaySpooned = false) },
    )
    private val plainProject = ProjectInfo(customer = "Pac-Fish", vessel = "Preston", jobNumber = "935066")

    /** The footer's AFT heading — the LAST such string, since a schematic's component names come first. */
    private fun footerHeadingHeight(c: RecordingCanvas) =
        c.boxes.last { it.text == FOOTER_AFT_TAPER_HEADER }.rect.height()

    private fun plainConsolidated(prefs: PdfPrefs): RecordingCanvas {
        val c = canvas()
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, plainSpec,
            config = RunoutConfig(tirDirection = TirDirection.AFT, showCouplingFace = true, showDyePenTest = true),
            project = plainProject, unit = UnitSystem.INCHES, pdfPrefs = prefs,
            resolvedComponents = resolveComponents(plainSpec), runoutReadings = readings,
            consolidated = true, includeBubbles = true, includeWearInfo = false,
        )
        return c
    }

    /** The on-device Pac-Fish job: one taper, thread and keyway at each end — an everyday footer. */
    private val pacFish by lazy {
        com.android.shaftschematic.doc.ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText(),
        ).spec
    }

    private fun plainSchematic(prefs: PdfPrefs): RecordingCanvas {
        val c = canvas()
        composeShaftPdfOnCanvas(
            c, pageW, pageH, pacFish, UnitSystem.INCHES, plainProject,
            appVersion = "test", filename = "plain.pdf", pdfPrefs = prefs,
            resolvedComponents = resolveComponents(pacFish),
        )
        return c
    }

    @Test
    fun `an everyday schematic prints its footer at the largest size`() {
        val big = PDF_TEXT_SCALES.last()
        val base = plainSchematic(PdfPrefs())
        val c = plainSchematic(PdfPrefs(footerTextScale = big, dimTextScale = big))
        assertNoOverlap("plain schematic at ${big}x", c)
        assertTrue(
            "footer heading should grow ${big}x: ${footerHeadingHeight(base)} → ${footerHeadingHeight(c)}",
            footerHeadingHeight(c) > footerHeadingHeight(base) * (big - 0.05f),
        )
    }

    @Test
    fun `a consolidated sheet asked for the largest size prints larger and keeps the TIR line above it`() {
        // Bubbles, the coupling face lane and the rails all share this page with the footer, so it
        // may step down — but it never prints at 100% when a larger step fits, and never collides.
        val base = plainConsolidated(PdfPrefs())
        val c = plainConsolidated(PdfPrefs(footerTextScale = PDF_TEXT_SCALES.last()))
        assertNoOverlap("plain consolidated, largest footer", c)
        assertTrue(
            "footer heading should grow: ${footerHeadingHeight(base)} → ${footerHeadingHeight(c)}",
            footerHeadingHeight(c) > footerHeadingHeight(base) * 1.2f,
        )
        val tir = c.boxes.first { it.text.startsWith("TIR's taken") }
        val heading = c.boxes.first { it.text == FOOTER_AFT_TAPER_HEADER }
        assertTrue(
            "TIR line (bottom ${tir.rect.bottom}) must sit above the footer heading (top ${heading.rect.top})",
            tir.rect.bottom <= heading.rect.top,
        )
    }

    @Test
    fun `a footer too full for the largest size steps down but still prints larger`() {
        val base = consolidated(includeBubbles = true, includeWearInfo = false, blank = false, face = true, dyePen = true)
        val c = consolidated(
            includeBubbles = true, includeWearInfo = false, blank = false, face = true,
            prefs = PdfPrefs(footerTextScale = PDF_TEXT_SCALES.last()), dyePen = true,
        )
        val ratio = footerHeadingHeight(c) / footerHeadingHeight(base)
        assertTrue("stepped-down footer ratio $ratio should sit between 125% and the largest step", ratio > 1.2f && ratio < PDF_TEXT_SCALES.last() - 0.05f)
    }

    @Test
    fun `the fitted scale takes the largest step that fits`() {
        assertEquals(2f, fittedTextScale(2f) { true }, 0f)
        assertEquals(1.5f, fittedTextScale(2f) { it <= 1.5f }, 0f)
        assertEquals(1f, fittedTextScale(2f) { false }, 0f)
        assertEquals(1f, fittedTextScale(1f) { false }, 0f)
    }
}
