package com.android.shaftschematic.manual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.geom.collectRunoutStations
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.COUPLING_PILOT_COMPONENT_ID
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WornSection
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.pdf.composeRunoutPdfOnCanvas
import com.android.shaftschematic.pdf.composeShaftPdfOnCanvas
import com.android.shaftschematic.pdf.composeUndercutPdf
import com.android.shaftschematic.pdf.composeWearPdf
import com.android.shaftschematic.pdf.undercutSheetOrientation
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.runoutComponentSpans
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the user manual's figures from the REAL PDF composers onto bitmaps and saves them as
 * PNGs. Gated: a no-op unless `MANUAL_FIGURES_DIR` names the output directory, so the normal
 * suite skips it.
 *
 * Regenerate (PowerShell, repo root):
 * `$env:MANUAL_FIGURES_DIR = 'D:\Dev\ShaftSchematic\docs\manual\figures'; .\gradlew --no-daemon :app:testDebugUnitTest --tests "com.android.shaftschematic.manual.ManualFiguresTest" --rerun`
 * (`--rerun` because the env var is not a task input: without it Gradle can call the test task
 * up to date and write nothing.)
 *
 * Every sheet is drawn at [SCALE]× the 792 × 612 pt page (close-up crops at [CROP_SCALE]×). Crop
 * rectangles are in page points and were measured off the rendered sheets; a fixture change can
 * move a feature out of its crop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManualFiguresTest {

    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()
    private val inch = 25.4f
    private fun inch(v: Float) = v * inch

    private lateinit var outDir: File
    private lateinit var styleBefore: FractionStyle

    @Before
    fun setUp() {
        val dir = System.getenv("MANUAL_FIGURES_DIR")
        assumeTrue("MANUAL_FIGURES_DIR not set; manual figures skipped", dir != null)
        outDir = File(dir!!).apply { mkdirs() }
        styleBefore = FractionTypography.active.style
        FractionTypography.setStyle(FractionStyle.Default)
    }

    @After
    fun tearDown() {
        if (::styleBefore.isInitialized) FractionTypography.setStyle(styleBefore)
    }

    // ── Main fixture: a 5" trawler tailshaft, 14 ft between the taper ends ──────────────────

    private val mainSpec = ShaftSpec(
        overallLengthMm = inch(168f),
        autoBodyDiaMm = inch(5f),
        threads = listOf(
            Threads(
                id = "THA", startFromAftMm = inch(-4f), lengthMm = inch(4f),
                majorDiaMm = inch(3.5f), tpi = 4f, pitchMm = inch / 4f,
                excludeFromOAL = true, isAftEnd = true,
            ),
        ),
        tapers = listOf(
            Taper(
                id = "T1", startFromAftMm = 0f, lengthMm = inch(14f),
                startDiaMm = inch(3.833f), endDiaMm = inch(5f),
                keywayWidthMm = inch(1.25f), keywayDepthMm = inch(0.4375f), keywayLengthMm = inch(11.5f),
                taperRateText = "1:12",
            ),
            Taper(
                id = "T2", startFromAftMm = inch(154f), lengthMm = inch(14f),
                startDiaMm = inch(5f), endDiaMm = inch(3.833f),
                keywayWidthMm = inch(1.25f), keywayDepthMm = inch(0.4375f), keywayLengthMm = inch(11.5f),
                taperRateText = "1:12",
            ),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = inch(22f), lengthMm = inch(30f), odMm = inch(5.75f), label = "Stern tube liner"),
            Liner(id = "L2", startFromAftMm = inch(128f), lengthMm = inch(16f), odMm = inch(5.5f), label = "Seal liner"),
        ),
    )

    private val mainProject = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = ShaftPosition.PORT,
        jobNumber = "814201",
        item = "Tailshaft",
    )

    private val mainResolved = resolveComponents(mainSpec)

    /** The after-drawing: the seal liner was renewed 2" longer, extending AFT. */
    private val finalSpec = mainSpec.copy(
        liners = mainSpec.liners.map {
            if (it.id == "L2") it.copy(startFromAftMm = inch(126f), lengthMm = inch(18f)) else it
        },
    )

    /**
     * TIR readings on every station but two, built from the stations the sheet itself derives
     * (so no reading lands on a station that does not exist).
     */
    private fun mainReadings(): RunoutReadings {
        val stations = collectRunoutStations(runoutComponentSpans(mainResolved), emptyMap(), { it })
        val values = listOf(0.002f, 0.001f, 0.003f, 0.002f, 0.004f, 0.001f, 0.002f, 0.003f, 0.001f, 0.002f, 0.003f, 0.002f, 0.001f)
        val clocks = listOf(3, 15, 7, 21, 1, 12, 18, 9, 5, 22, 14, 2, 10)
        val skip = setOf(4, 9)   // two stations left unread, as on a real sheet mid-job
        val out = stations.mapIndexedNotNull { i, st ->
            if (i in skip) null
            else RunoutReading(st.componentId, st.stationIndex, inch(values[i % values.size]), clocks[i % clocks.size])
        }
        return RunoutReadings(out + RunoutReading(COUPLING_PILOT_COMPONENT_ID, 0, inch(0.001f), 6))
    }

    private val mainWear = WearRecord(
        wornSections = listOf(
            WornSection(startFromAftMm = inch(30f), lengthMm = inch(12f), diaMm = listOf(inch(5.738f), inch(5.734f), inch(5.741f))),
        ),
        diaReadings = listOf(
            WearDiaReading(componentId = "L1", axialMm = inch(2f), diaMm = inch(5.747f)),
            WearDiaReading(componentId = "L2", axialMm = inch(8f), diaMm = inch(5.494f)),
        ),
        pits = listOf(
            WearPit(componentId = "L1", axialMm = inch(25f), acrossFrac = 0.35f, size = PitSize.LARGE),
            WearPit(componentId = "L1", axialMm = inch(27.5f), acrossFrac = 0.65f, size = PitSize.SMALL),
        ),
    )

    // ── Feature fixture: a 6" line shaft with every drawing feature on one sheet ────────────

    private val featureSpec = ShaftSpec(
        overallLengthMm = inch(180f),
        autoBodyDiaMm = inch(6f),
        threads = listOf(
            Threads(
                id = "THA", startFromAftMm = inch(-6f), lengthMm = inch(6f),
                majorDiaMm = inch(4.5f), tpi = 4f, pitchMm = inch / 4f,
                excludeFromOAL = true, isAftEnd = true,
                reliefLenMm = inch(0.75f), reliefDiaMm = inch(4.0f), reliefAtAft = false,
            ),
        ),
        tapers = listOf(
            Taper(
                id = "T1", startFromAftMm = 0f, lengthMm = inch(18f),
                startDiaMm = inch(4.5f), endDiaMm = inch(6f),
                keywayWidthMm = inch(1.5f), keywayDepthMm = inch(0.5f), keywayLengthMm = inch(14f),
                keywaySpooned = true, taperRateText = "1:12",
            ),
        ),
        liners = listOf(
            Liner(
                id = "L1", startFromAftMm = inch(24f), lengthMm = inch(20f), odMm = inch(8f),
                label = "Stern tube liner",
                shoulderAftLenMm = inch(1.25f), shoulderAftOdMm = inch(7.5f), shoulderAftRadiusMm = inch(0.25f),
                shoulderFwdLenMm = inch(1.25f), shoulderFwdOdMm = inch(7.5f), shoulderFwdRadiusMm = inch(0.25f),
            ),
            Liner(id = "L2", startFromAftMm = inch(112f), lengthMm = inch(16f), odMm = inch(8f), label = "Seal liner"),
        ),
        bodies = listOf(
            Body(
                id = "B1", startFromAftMm = inch(92f), lengthMm = inch(10f), diaMm = inch(6.5f),
                label = "Seal journal", compressOnDrawing = false, showDiaOnDrawing = true,
                blendAftMm = inch(1.25f), blendFwdMm = inch(1.25f),
                blendFwdSeal = true, blendFwdSealLenMm = inch(3.5f),
            ),
            Body(
                id = "B2", startFromAftMm = inch(128f), lengthMm = inch(42f), diaMm = inch(6f),
                label = "Intermediate",
                keywayWidthMm = inch(1.5f), keywayDepthMm = inch(0.5f), keywayLengthMm = inch(6f),
                keywayEnd = LinerAuthoredReference.AFT, keywayOffsetFromEndMm = inch(4f),
            ),
            // Opted out of compression (the app's default for a new explicit body) so the bolt
            // holes print at their true spacing: hole circles are sized on the diameter scale,
            // and inside a compressed run their axial spacing shrinks until they overlap.
            Body(
                id = "B3", startFromAftMm = inch(170f), lengthMm = inch(10f), diaMm = inch(6f),
                label = "Coupling end", compressOnDrawing = false,
            ),
        ),
        couplerBoltSlots = listOf(
            CouplerBoltSlot(
                id = "S1", startFromAftMm = inch(172f), holeDiaMm = inch(1f), count = 3,
                spacingMm = inch(3f), authoredReference = SlotAuthoredReference.FWD,
                holeStyle = BoltHoleStyle.SEAM,
            ),
            CouplerBoltSlot(
                id = "S2", startFromAftMm = inch(164f), holeDiaMm = inch(0.875f), count = 1,
                authoredReference = SlotAuthoredReference.FWD,
                holeStyle = BoltHoleStyle.CROSS, clocking = BoltHoleClocking.DEG_90,
            ),
        ),
    )

    private val featureProject = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "MV Halyard Point",
        side = ShaftPosition.STBD,
        jobNumber = "814377",
        item = "Line shaft",
    )

    // ── Rendering helpers ────────────────────────────────────────────────────────────────

    private fun sheet(
        scale: Float = SCALE,
        w: Float = pageW,
        h: Float = pageH,
        draw: (Canvas) -> Unit,
    ): Bitmap {
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        draw(c)
        return bmp
    }

    /**
     * A `PdfDocument.Page` wrapping a bitmap canvas, for the composers that only take a page
     * (wear, undercut). The page constructor is private; `PageInfo.Builder` is public.
     */
    private fun pageOn(c: Canvas, w: Float = pageW, h: Float = pageH): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), 1).create()
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(c, info)
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(outDir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Crop a page-point rectangle (left, top, right, bottom) out of a sheet drawn at [scale]×. */
    private fun crop(src: Bitmap, name: String, l: Float, t: Float, r: Float, b: Float, scale: Float = SCALE) {
        val x = (l * scale).toInt().coerceIn(0, src.width - 1)
        val y = (t * scale).toInt().coerceIn(0, src.height - 1)
        val w = ((r - l) * scale).toInt().coerceAtMost(src.width - x)
        val h = ((b - t) * scale).toInt().coerceAtMost(src.height - y)
        save(Bitmap.createBitmap(src, x, y, w, h), name)
    }

    private fun schematic(
        spec: ShaftSpec,
        project: ProjectInfo,
        options: PdfExportOptions = PdfExportOptions(),
        scale: Float = SCALE,
    ): Bitmap =
        sheet(scale) { c ->
            composeShaftPdfOnCanvas(
                c, pageW, pageH, spec, UnitSystem.INCHES, project,
                appVersion = "1.0", filename = "${project.jobNumber}.pdf",
                options = options,
                resolvedComponents = resolveComponents(spec),
                displayUnits = DisplayUnits.single(UnitSystem.INCHES),
            )
        }

    private val runoutConfig = RunoutConfig(tirDirection = TirDirection.FORWARD, showCouplingFace = true)

    private fun runout(blank: Boolean, scale: Float = SCALE): Bitmap = sheet(scale) { c ->
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, mainSpec,
            config = runoutConfig, project = mainProject, unit = UnitSystem.INCHES,
            resolvedComponents = mainResolved, runoutReadings = mainReadings(),
            blankValues = blank, consolidated = false,
        )
    }

    // ── Figures ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `render manual figures`() {
        val main = schematic(mainSpec, mainProject)
        save(main, "schematic.png")
        crop(main, "schematic-rails.png", 14f, 205f, 764f, 370f)
        crop(main, "schematic-footer.png", 28f, 435f, 764f, 570f)
        // Cover art: drawing + rails + name labels, no footer; ink bounds plus ~40 px at 3×.
        crop(main, "cover-shaft.png", 12f, 202.667f, 769.667f, 427f)

        save(schematic(mainSpec, mainProject, PdfExportOptions(blankValues = true)), "schematic-blank.png")
        save(schematic(finalSpec, mainProject.copy(drawingLabel = "Final")), "schematic-final.png")

        val classic = runout(blank = false)
        save(classic, "runout-classic.png")
        crop(classic, "runout-bubbles.png", 4f, 222f, 390f, 410f)
        crop(runout(blank = false, scale = CROP_SCALE), "runout-coupling-face.png", 650f, 470f, 772f, 582f, CROP_SCALE)
        save(runout(blank = true), "runout-blank.png")

        save(
            sheet { c ->
                composeRunoutPdfOnCanvas(
                    c, pageW, pageH, mainSpec,
                    config = runoutConfig, project = mainProject, unit = UnitSystem.INCHES,
                    resolvedComponents = mainResolved, runoutReadings = mainReadings(), wearRecord = mainWear,
                    consolidated = true, includeBubbles = true, includeWearInfo = true,
                )
            },
            "consolidated.png",
        )

        save(
            sheet { c ->
                composeWearPdf(
                    pageOn(c), mainSpec, mainProject, UnitSystem.INCHES,
                    resolvedComponents = mainResolved,
                    wearRecord = WearRecord(
                        spots = listOf(WearSpot(id = "W1", linerId = "L1", startMm = inch(18f), lengthMm = inch(8f))),
                        pits = listOf(
                            WearPit(componentId = "L1", axialMm = inch(20f), acrossFrac = 0.35f, size = PitSize.LARGE),
                            WearPit(componentId = "L1", axialMm = inch(23f), acrossFrac = 0.6f, size = PitSize.SMALL),
                            WearPit(componentId = "L1", axialMm = inch(24.5f), acrossFrac = 0.3f, size = PitSize.SMALL),
                        ),
                        diaReadings = listOf(
                            WearDiaReading(componentId = "L1", axialMm = inch(4f), diaMm = inch(5.748f)),
                            WearDiaReading(componentId = "L1", axialMm = inch(19f), diaMm = inch(5.738f)),
                            WearDiaReading(componentId = "L1", axialMm = inch(22f), diaMm = inch(5.731f)),
                            WearDiaReading(componentId = "L1", axialMm = inch(25f), diaMm = inch(5.736f)),
                            WearDiaReading(componentId = "L2", axialMm = inch(8f), diaMm = inch(5.496f)),
                        ),
                        dyePenResult = DyePenResult.FAIL,
                    ),
                )
            },
            "wear.png",
        )

        // The undercut sheet resolves its own page: this two-liner fixture is the portrait sheet.
        val undercutRecord = UndercutRecord(
            undercuts = listOf(
                Undercut(
                    id = "U1", startFromAftMm = inch(39f), lengthMm = inch(6f), diaMm = inch(5.625f),
                    authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L1",
                ),
                Undercut(
                    id = "U2", startFromAftMm = inch(131f), lengthMm = inch(8f), diaMm = inch(5.44f),
                    authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L2",
                ),
                Undercut(
                    id = "U3", startFromAftMm = inch(133.5f), lengthMm = inch(3f), diaMm = inch(5.375f),
                    authoredReference = UndercutReference.LINER_AFT, referenceLinerId = "L2",
                ),
            ),
        )
        val undercutPage = undercutSheetOrientation(mainSpec, mainResolved, undercutRecord, blankValues = false)
        val undercutW = undercutPage.widthPt.toFloat()
        val undercutH = undercutPage.heightPt.toFloat()
        save(
            sheet(w = undercutW, h = undercutH) { c ->
                composeUndercutPdf(
                    pageOn(c, undercutW, undercutH), mainSpec, mainProject, UnitSystem.INCHES,
                    resolvedComponents = mainResolved,
                    undercutRecord = undercutRecord,
                )
            },
            "undercut.png",
        )

        save(schematic(featureSpec, featureProject), "schematic-features.png")
        // Close-ups come off a sharper render of the same sheet, so they hold up when enlarged.
        val features = schematic(featureSpec, featureProject, scale = CROP_SCALE)
        crop(features, "feature-keyway-spooned.png", 52f, 240f, 146f, 318f, CROP_SCALE)
        crop(features, "feature-thread-relief.png", 24f, 240f, 100f, 318f, CROP_SCALE)
        crop(features, "feature-liner-shoulder.png", 168f, 235f, 243f, 322f, CROP_SCALE)
        crop(features, "feature-sbreak.png", 220f, 240f, 320f, 318f, CROP_SCALE)
        crop(features, "feature-blend-seal.png", 310f, 240f, 430f, 352f, CROP_SCALE)
        crop(features, "feature-keyway-captured.png", 508f, 240f, 622f, 318f, CROP_SCALE)
        crop(features, "feature-bolt-holes.png", 590f, 232f, 764f, 332f, CROP_SCALE)
    }

    private companion object {
        const val SCALE = 3f
        /** Render scale for the small close-up crops. */
        const val CROP_SCALE = 8f
    }
}
