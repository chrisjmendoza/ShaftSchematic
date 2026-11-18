package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.withAutoSectionFiberglassAndSeals
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
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

/**
 * A port tailshaft from the shop's CAD drawings (353 1/2" OAL, three liners, fiberglass between
 * them, an AFT taper and thread, a FWD taper behind a short 9.070" body) with every bare-shaft
 * section between the liners wrapped, plus the explicit 2" body before the FWD taper left bare.
 * Shared with the gated review render.
 */
internal object FiberglassShaftFixture {
    private const val IN = 25.4f
    fun spec(): ShaftSpec {
        val base = ShaftSpec(
            overallLengthMm = 353.5f * IN,
            threads = listOf(
                Threads(id = "th", startFromAftMm = 0f, lengthMm = 5.5f * IN, majorDiaMm = 5.5f * IN, pitchMm = IN / 4f),
            ),
            tapers = listOf(
                Taper(id = "aftTaper", startFromAftMm = 5.5f * IN, lengthMm = 18.75f * IN, startDiaMm = 7.313f * IN, endDiaMm = 8.875f * IN),
                Taper(id = "fwdTaper", startFromAftMm = 337.684f * IN, lengthMm = 15.816f * IN, startDiaMm = 9.070f * IN, endDiaMm = 7.752f * IN),
            ),
            liners = listOf(
                Liner(id = "aftLiner", startFromAftMm = 24.75f * IN, lengthMm = 39.25f * IN, odMm = 9.25f * IN),
                Liner(id = "midLiner", startFromAftMm = 187.5f * IN, lengthMm = 21f * IN, odMm = 9.25f * IN),
                Liner(id = "fwdLiner", startFromAftMm = 285.75f * IN, lengthMm = 20f * IN, odMm = 9.25f * IN),
            ),
            bodies = listOf(
                Body(id = "coupling", startFromAftMm = 335.684f * IN, lengthMm = 2f * IN, diaMm = 9.070f * IN),
            ),
            autoBodyDiaMm = 9f * IN,
        )
        val seal = 4f * IN
        return base
            .withAutoSectionFiberglassAndSeals(64f * IN, 187.5f * IN, true, seal, seal)
            .withAutoSectionFiberglassAndSeals(208.5f * IN, 285.75f * IN, true, seal, seal)
            .withAutoSectionFiberglassAndSeals(305.75f * IN, 335.684f * IN, true, seal, seal)
    }
}

/**
 * The Final tab's Full length sheet: the whole shaft unbroken above the final schematic, with
 * every wrapped section filled in the fiberglass colour and labelled FIBERGLASS — the label being
 * what carries it on a black-and-white printer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullLengthPdfComposerTest {

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

    private fun compose(spec: ShaftSpec): SheetTextRecorder {
        val c = SheetTextRecorder(PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat())
        composeFullLengthPdfOnCanvas(
            c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
            spec = spec, unit = UnitSystem.INCHES,
            project = ProjectInfo(customer = "Test", drawingLabel = "Final"),
            appVersion = "test", filename = "t.pdf", pdfPrefs = PdfPrefs(),
            resolvedComponents = resolveComponents(spec),
        )
        return c
    }

    @Test
    fun `every wrapped section is filled and labelled in the full-length band`() {
        val spec = FiberglassShaftFixture.spec()
        val c = compose(spec)
        val band = fullLengthBands(PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat()).fullLength

        val fills = c.rects.filter { it.color == FIBERGLASS_FILL_COLOR && it.rect.bottom <= band.bottom }
        val sections = fiberglassBodyRuns(spec, resolveComponents(spec))
        assertEquals(3, sections.size)
        assertTrue("one fill per section at least", fills.size >= sections.size)

        val labels = c.texts.filter { it.text == FIBERGLASS_LABEL }
        assertEquals("one FIBERGLASS per wrapped section", 3, labels.size)
        assertTrue("all in the full-length band", labels.all { it.rect.bottom <= band.bottom })
        assertTrue("the legend prints", c.strings.contains("Fiberglass"))
        assertTrue(c.strings.contains("FULL LENGTH"))
    }

    @Test
    fun `the full-length band spans the whole content width at one linear scale`() {
        val spec = FiberglassShaftFixture.spec()
        val c = compose(spec)
        val band = fullLengthBands(PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat()).fullLength
        val sections = fiberglassBodyRuns(spec, resolveComponents(spec)).sortedBy { it.startMmPhysical }
        val fills = c.rects.filter { it.color == FIBERGLASS_FILL_COLOR && it.rect.height() > 20f }
            .sortedBy { it.rect.left }
        val xPerMm = band.width() / spec.overallLengthMm
        sections.zip(fills).forEach { (s, f) ->
            assertEquals(band.left + s.startMmPhysical * xPerMm, f.rect.left, 0.5f)
            assertEquals(band.left + s.endMmPhysical * xPerMm, f.rect.right, 0.5f)
        }
    }

    @Test
    fun `a thread excluded from the OAL stays inside the band`() {
        // The band maps the DRAWN extent: an excluded AFT thread sits before 0, and a map fitted
        // to the OAL alone would draw it off the band's left end.
        val threadLen = 5.5f * 25.4f
        val base = FiberglassShaftFixture.spec()
        val spec = base.copy(
            threads = base.threads.map {
                it.copy(startFromAftMm = -threadLen, excludeFromOAL = true, isAftEnd = true)
            },
        )
        val c = compose(spec)
        val band = fullLengthBands(PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat()).fullLength
        val xPerMm = band.width() / (spec.overallLengthMm + threadLen)
        val sections = fiberglassBodyRuns(spec, resolveComponents(spec)).sortedBy { it.startMmPhysical }
        val fills = c.rects.filter { it.color == FIBERGLASS_FILL_COLOR && it.rect.height() > 20f }
            .sortedBy { it.rect.left }
        assertEquals(sections.size, fills.size)
        sections.zip(fills).forEach { (s, f) ->
            assertEquals(band.left + (s.startMmPhysical + threadLen) * xPerMm, f.rect.left, 0.5f)
            assertEquals(band.left + (s.endMmPhysical + threadLen) * xPerMm, f.rect.right, 0.5f)
        }
    }

    @Test
    fun `a shaft with no fiberglass prints no label and no legend`() {
        val c = compose(FiberglassShaftFixture.spec().copy(autoFiberglass = emptyList()))
        assertTrue(c.texts.none { it.text == FIBERGLASS_LABEL })
        assertTrue(c.strings.none { it == "Fiberglass" })
        assertTrue(c.rects.none { it.color == FIBERGLASS_FILL_COLOR })
    }

    @Test
    fun `the schematic band under it still carries the final footer`() {
        val c = compose(FiberglassShaftFixture.spec())
        assertTrue(c.strings.any { it.contains("Final") })
    }

    @Test
    fun `a label too wide for its section goes above, and two such labels never share a row`() {
        val runs = listOf(
            FiberglassLabelRun(100f, 300f, 40f),   // roomy → inside
            FiberglassLabelRun(310f, 330f, 40f),   // narrow → row 0
            FiberglassLabelRun(332f, 350f, 40f),   // narrow, right beside it → row 1
        )
        val p = planFiberglassLabels(runs, labelWidthPt = 40f, labelHeightPt = 7f, rowCount = 2, contentLeft = 0f, contentRight = 800f)
        assertEquals(-1, p[0].row)
        assertEquals(0, p[1].row)
        assertEquals(1, p[2].row)
    }

    @Test
    fun `a label is never dropped, even with every row taken`() {
        val runs = List(4) { i -> FiberglassLabelRun(300f + i, 302f + i, 40f) }
        val p = planFiberglassLabels(runs, 40f, 7f, rowCount = 2, contentLeft = 0f, contentRight = 800f)
        assertEquals(4, p.size)
        assertTrue(p.all { it.row in 0..1 })
    }

    @Test
    fun `the drawn diameter is enlarged to read on a long shaft and capped on a short one`() {
        // 9" on a 353 1/2" shaft over ~720 pt is about 18 pt true — enlarged to the floor.
        assertEquals(FULL_LENGTH_SHAFT_MIN_PT, fullLengthDrawnDiaPt(228.6f, 720f / 8979f), 0.01f)
        // A stubby shaft whose true height is huge stops at the band's room.
        assertEquals(FULL_LENGTH_SHAFT_MAX_PT, fullLengthDrawnDiaPt(228.6f, 1f), 0.01f)
    }
}
