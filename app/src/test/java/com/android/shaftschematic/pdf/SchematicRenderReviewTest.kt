package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.WearAreaStyle
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.geom.effectiveWearTraceDepthFrac
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
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
 * Review renders of the schematic and the consolidated sheet for the fixture jobs — the real
 * composers onto a bitmap at [SCALE]×. Gated: a no-op unless `SCHEMATIC_RENDER_DIR` names the
 * output directory, so the normal suite skips it. The undercut sheet has its own
 * ([UndercutPortraitRenderTest]).
 *
 * Run (PowerShell, repo root):
 * `$env:SCHEMATIC_RENDER_DIR = '<dir>'; .\gradlew.bat :app:testDebugUnitTest --tests "com.android.shaftschematic.pdf.SchematicRenderReviewTest" --rerun`
 * (`--rerun` because the env var is not a task input.)
 *
 * `SCHEMATIC_RENDER_FIXTURES` (optional, `;`-separated `.shaft` files or directories of them) adds
 * the full document set for each job — schematic, consolidated (as saved, Schematic + Runout, and
 * both as blank drafts), classic runout, wear and undercut — named `<job>_<sheet>.png`.
 * `SCHEMATIC_RENDER_UNITS_NOTE` (optional, any value) renders those job sheets with the
 * "Units in …" note on (`PdfPrefs.showUnitsNote`), to inspect each sheet's lower-right corner.
 * `SCHEMATIC_RENDER_WEAR_STROKES` (optional, any value) renders them with the consolidated sheet's
 * wear areas as vertical strokes (`PdfPrefs.wearAreaStyle` Lines) instead of the default grey.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SchematicRenderReviewTest {

    private lateinit var outDir: File
    private lateinit var styleBefore: FractionStyle

    @Before
    fun setUp() {
        val dir = System.getenv("SCHEMATIC_RENDER_DIR")
        assumeTrue("SCHEMATIC_RENDER_DIR not set; schematic renders skipped", dir != null)
        outDir = File(dir!!).apply { mkdirs() }
        styleBefore = FractionTypography.active.style
        FractionTypography.setStyle(FractionStyle.Default)
    }

    @After
    fun tearDown() {
        if (::styleBefore.isInitialized) FractionTypography.setStyle(styleBefore)
    }

    /** The on-device job with a step-down taper mid-shaft, ahead of the FWD liner. */
    private val midTaper = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText(),
    )

    private fun ShaftDocCodec.Decoded.project() = ProjectInfo(
        customer = customer,
        vessel = vessel,
        side = shaftPosition,
        jobNumber = jobNumber,
        item = item,
    )

    /**
     * Blend by angle: the hand sketch's 9.750" liner stepping down to a 9.030" body through a 45°
     * chamfer with a 3/8" R at its foot — to the liner OD, and from the default seat.
     */
    @Test
    fun `blend by angle callouts`() {
        val inch = 25.4f
        fun spec(toLinerOd: Boolean) = com.android.shaftschematic.model.ShaftSpec(
            overallLengthMm = 44.5f * inch,
            liners = listOf(
                com.android.shaftschematic.model.Liner(
                    id = "LN", startFromAftMm = 0f, lengthMm = 30.5f * inch, odMm = 9.75f * inch,
                    endMmPhysical = 30.5f * inch,
                ),
            ),
            bodies = listOf(
                com.android.shaftschematic.model.Body(
                    id = "B1", startFromAftMm = 30.5f * inch, lengthMm = 14f * inch, diaMm = 9.03f * inch,
                    showDiaOnDrawing = true,
                    blendAftMm = 1f * inch, blendAftAngleDeg = 45f, blendAftRadiusMm = 0.375f * inch,
                    blendAftToLinerOd = toLinerOd,
                ),
            ),
            autoBodyDiaMm = 9.03f * inch,
        )
        for ((name, s) in listOf("blend_angle_liner_od.png" to spec(true), "blend_angle_seat.png" to spec(false))) {
            val (bmp, c) = bitmapPage()
            composeShaftPdfOnCanvas(
                c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
                spec = s, unit = UnitSystem.INCHES, project = ProjectInfo(customer = "Curtin Marine"),
                appVersion = "review", filename = name, pdfPrefs = PdfPrefs(),
                resolvedComponents = resolveComponents(s),
            )
            save(name, bmp)
        }
    }

    @Test
    fun `blend angle callout beside a liner diameter`() {
        val doc = ShaftDocCodec.decode(
            requireNotNull(javaClass.classLoader?.getResource("fixtures/blend_angle_kelley_c.shaft")).readText(),
        )
        renderSchematic("blend_angle_kelley_c_schematic.png", doc)
        // A wide face at a large size pushes the blend's value past the content edge, so it hangs
        // LEFT of its leader — under the liner, where the liner's Ø leader drops (on-device
        // report: the Ø leader printed through the blend's value).
        com.android.shaftschematic.util.BundledFonts.init(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
        )
        val before = com.android.shaftschematic.data.SettingsStore.pdfPrefs.outputFont
        try {
            for (font in com.android.shaftschematic.util.OutputFont.entries) {
                com.android.shaftschematic.data.SettingsStore.updatePdfPrefs { it.copy(outputFont = font) }
                renderSchematic(
                    "blend_angle_kelley_c_${font.name.lowercase()}_schematic.png", doc,
                    PdfPrefs(dimTextScale = 1.75f, showComponentTitles = false),
                    linerMinFracOfTrue = 1f,
                )
                // The bare-shaft Ø callouts switched on as well, the section beside the FWD
                // taper machined to 9": four callouts share the stretch between the FWD liner and
                // the FWD taper (on-device report).
                // … and to 9.030", whose wider value no longer fits beside the taper.
                for ((tag, diaMm) in listOf("body_dia" to 228.6f, "body_dia_9030" to 229.362f))
                for (pct in listOf(125, 175)) {
                    val repaired = doc.spec.copy(
                        showAutoBodyDia = true,
                        autoDiaOverrides = doc.spec.autoDiaOverrides.map {
                            if (it.anchorMm > 7000f) it.copy(diaMm = diaMm) else it
                        },
                    )
                    renderSchematic(
                        "blend_angle_kelley_c_${font.name.lowercase()}_${tag}_${pct}_schematic.png",
                        doc.copy(spec = repaired),
                        PdfPrefs(dimTextScale = pct / 100f, showComponentTitles = false),
                        linerMinFracOfTrue = 1f,
                    )
                }
            }
        } finally {
            com.android.shaftschematic.data.SettingsStore.updatePdfPrefs { it.copy(outputFont = before) }
        }
    }

    /**
     * Large print: every fixture job with BOTH sheet text sizes at their largest, the names and
     * the bare-shaft Ø callouts on — the most a reader can ask of the below-shaft band.
     */
    @Test
    fun `large print`() {
        val prefs = PdfPrefs(
            footerTextScale = com.android.shaftschematic.settings.PDF_TEXT_SCALE_MAX,
            dimTextScale = com.android.shaftschematic.settings.PDF_TEXT_SCALE_MAX,
            showComponentTitles = true,
        )
        for (name in listOf(
            "mid_taper_pac_fish", "runout_auto_body_polar_ranger", "undercut_polar_ranger_stbd",
            "blend_angle_kelley_c",
        )) {
            val doc = ShaftDocCodec.decode(
                requireNotNull(javaClass.classLoader?.getResource("fixtures/$name.shaft")).readText(),
            )
            val withBodyDia = doc.copy(spec = doc.spec.copy(showAutoBodyDia = true))
            renderSchematic("large_${name}_schematic.png", withBodyDia, prefs)
            if (name == "mid_taper_pac_fish") {
                renderBeforeAfter(
                    "large_${name}_before_after.png", doc,
                    BeforeAfterPdfComposerTest.movedFwdLinerFinal(doc.spec), pdfPrefs = prefs,
                )
            }
            renderConsolidated("large_${name}_consolidated.png", withBodyDia, pdfPrefs = prefs)
        }
    }

    @Test
    fun `mid-shaft taper job`() {
        renderSchematic("mid_taper_schematic.png", midTaper)
        renderConsolidated("mid_taper_consolidated.png", midTaper)
    }

    /**
     * The Final tab's before/after sheet: the job as it came in above the same job with its FWD
     * liner moved 2" FWD — at the default height, at twice it, and as a blank draft.
     */
    @Test
    fun `before-after sheet`() {
        val final = BeforeAfterPdfComposerTest.movedFwdLinerFinal(midTaper.spec)
        renderBeforeAfter("mid_taper_before_after.png", midTaper, final)
        renderBeforeAfter("mid_taper_before_after_tall.png", midTaper, final, heightScale = 2f)
        renderBeforeAfter(
            "mid_taper_before_after_blank.png", midTaper, final,
            options = PdfExportOptions(blankValues = true),
        )
    }

    private fun renderBeforeAfter(
        name: String,
        doc: ShaftDocCodec.Decoded,
        finalSpec: com.android.shaftschematic.model.ShaftSpec,
        heightScale: Float = 1f,
        options: PdfExportOptions = PdfExportOptions(),
        pdfPrefs: PdfPrefs = PdfPrefs(),
    ) {
        val (bmp, c) = bitmapPage()
        composeBeforeAfterPdfOnCanvas(
            c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
            originalSpec = doc.spec,
            finalSpec = finalSpec,
            unit = doc.preferredUnit ?: UnitSystem.INCHES,
            project = doc.project().copy(drawingLabel = "Final"),
            appVersion = "review",
            filename = name,
            options = options,
            originalResolved = resolveComponents(doc.spec),
            finalResolved = resolveComponents(finalSpec),
            heightScale = heightScale,
            pdfPrefs = pdfPrefs,
        )
        save(name, bmp)
    }

    /** The same job with a wear band and three measured stations on the FWD liner. */
    @Test
    fun `wear sheet traces the main profile at both ends of the slider`() {
        val fwdLiner = midTaper.spec.liners.maxBy { it.startFromAftMm }
        val record = WearRecord(
            spots = listOf(WearSpot(linerId = fwdLiner.id, startMm = 200f, lengthMm = 400f)),
            diaReadings = listOf(
                WearDiaReading(componentId = fwdLiner.id, axialMm = 250f, diaMm = fwdLiner.odMm - 1f),
                WearDiaReading(componentId = fwdLiner.id, axialMm = 400f, diaMm = fwdLiner.odMm - 6f),
                WearDiaReading(componentId = fwdLiner.id, axialMm = 550f, diaMm = fwdLiner.odMm - 2f),
            ),
        )
        renderWear("wear_trace_05.png", midTaper, record, traceDepthFrac = 0.05f)
        renderWear("wear_trace_25.png", midTaper, record, traceDepthFrac = 0.25f)
    }

    /**
     * The same job with a wear area on its longest bare-shaft run (elected onto a detail strip
     * beside the liners) and one on the FWD liner: the wear sheet, and the consolidated sheet in
     * the Grey and Both wear-area styles.
     */
    @Test
    fun `wear areas on a body`() {
        val resolved = resolveComponents(midTaper.spec)
        val run = resolved.filterIsInstance<com.android.shaftschematic.ui.resolved.ResolvedBody>()
            .maxBy { it.endMmPhysical - it.startMmPhysical }
        val runLen = run.endMmPhysical - run.startMmPhysical
        val fwdLiner = midTaper.spec.liners.maxBy { it.startFromAftMm }
        val record = WearRecord(
            spots = listOf(
                WearSpot(linerId = run.id, startMm = runLen * 0.15f, lengthMm = runLen * 0.12f),
                WearSpot(linerId = run.id, startMm = runLen * 0.70f, lengthMm = runLen * 0.10f),
                WearSpot(linerId = fwdLiner.id, startMm = 200f, lengthMm = 400f),
            ),
            stripComponentIds = midTaper.spec.liners.map { it.id } + run.id,
        )
        renderWear("wear_body_area.png", midTaper, record, traceDepthFrac = 0.25f)
        renderConsolidated("consolidated_body_area_grey.png", midTaper, wearRecord = record)
        renderConsolidated(
            "consolidated_body_area_both.png", midTaper,
            pdfPrefs = PdfPrefs(wearAreaStyle = WearAreaStyle.BOTH), wearRecord = record,
        )
    }

    private fun renderWear(name: String, doc: ShaftDocCodec.Decoded, record: WearRecord, traceDepthFrac: Float) {
        val (bmp, c) = bitmapPage()
        composeWearPdf(
            page = pageOn(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
            spec = doc.spec,
            project = doc.project(),
            unit = doc.preferredUnit ?: UnitSystem.INCHES,
            resolvedComponents = resolveComponents(doc.spec),
            wearRecord = record,
            traceDepthFrac = traceDepthFrac,
        )
        save(name, bmp)
    }

    /** A `PdfDocument.Page` over a bitmap canvas — the page constructor is private. */
    private fun pageOn(c: Canvas, w: Int, h: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(c, info)
    }

    // ── Rendering ────────────────────────────────────────────────────────────────────────

    private fun bitmapPage(): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(
            (PDF_PAGE_WIDTH_PT * SCALE).toInt(), (PDF_PAGE_HEIGHT_PT * SCALE).toInt(), Bitmap.Config.ARGB_8888,
        )
        bmp.eraseColor(Color.WHITE)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        return bmp to c
    }

    private fun save(name: String, bmp: Bitmap) {
        FileOutputStream(File(outDir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderSchematic(
        name: String,
        doc: ShaftDocCodec.Decoded,
        pdfPrefs: PdfPrefs = PdfPrefs(),
        linerMinFracOfTrue: Float = 0f,
    ) {
        val (bmp, c) = bitmapPage()
        composeShaftPdfOnCanvas(
            c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
            spec = doc.spec,
            unit = doc.preferredUnit ?: UnitSystem.INCHES,
            project = doc.project(),
            appVersion = "review",
            filename = name,
            pdfPrefs = pdfPrefs,
            resolvedComponents = resolveComponents(doc.spec),
            linerMinFracOfTrue = linerMinFracOfTrue,
        )
        save(name, bmp)
    }

    private fun renderConsolidated(
        name: String,
        doc: ShaftDocCodec.Decoded,
        pdfPrefs: PdfPrefs = PdfPrefs(),
        config: RunoutConfig = doc.runoutConfig,
        wearRecord: WearRecord = doc.wearRecord,
    ) {
        val (bmp, c) = bitmapPage()
        composeRunoutPdfOnCanvas(
            c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
            spec = doc.spec,
            config = config,
            project = doc.project(),
            unit = doc.preferredUnit ?: UnitSystem.INCHES,
            pdfPrefs = pdfPrefs,
            resolvedComponents = resolveComponents(doc.spec),
            wearRecord = wearRecord,
        )
        save(name, bmp)
    }

    /** The consolidated sheet with the dye pen row elected and PASS marked in-app. */
    @Test
    fun `consolidated sheet prints the dye pen row`() {
        renderConsolidated(
            "consolidated_dye_pen.png", midTaper,
            config = midTaper.runoutConfig.copy(showDyePenTest = true, showCouplingFace = true),
            wearRecord = midTaper.wearRecord.copy(dyePenResult = DyePenResult.PASS),
        )
    }

    /** "Footer text size" / "Dimension text size": the job at the shipped size and at the largest step. */
    @Test
    fun `sheet text sizes`() {
        val config = midTaper.runoutConfig.copy(showDyePenTest = true, showCouplingFace = true)
        for (s in com.android.shaftschematic.settings.PDF_TEXT_SCALES) {
            val pct = (s * 100).toInt()
            val prefs = PdfPrefs(footerTextScale = s, dimTextScale = s)
            renderConsolidated("text_${pct}_consolidated.png", midTaper, pdfPrefs = prefs, config = config)
            renderConsolidated(
                "text_${pct}_consolidated_no_face.png", midTaper, pdfPrefs = prefs,
                config = config.copy(showCouplingFace = false),
            )
            renderSchematic("text_${pct}_schematic.png", midTaper, pdfPrefs = prefs)
        }
    }

    /** Settings → Drawing → "Output font": the job in each readable face, footer at 150%. */
    @Test
    fun `readable output fonts`() {
        com.android.shaftschematic.util.BundledFonts.init(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
        )
        val before = com.android.shaftschematic.data.SettingsStore.pdfPrefs.outputFont
        try {
            for (font in com.android.shaftschematic.util.OutputFont.entries) {
                com.android.shaftschematic.data.SettingsStore.updatePdfPrefs { it.copy(outputFont = font) }
                val prefs = PdfPrefs(footerTextScale = 1.5f, dimTextScale = 1.5f)
                renderConsolidated(
                    "font_${font.name.lowercase()}_consolidated.png", midTaper, pdfPrefs = prefs,
                    config = midTaper.runoutConfig.copy(showDyePenTest = true),
                )
                renderSchematic("font_${font.name.lowercase()}_schematic.png", midTaper, pdfPrefs = prefs)
            }
        } finally {
            com.android.shaftschematic.data.SettingsStore.updatePdfPrefs { it.copy(outputFont = before) }
        }
    }

    /** Every sheet of every job named by `SCHEMATIC_RENDER_FIXTURES`. */
    @Test
    fun `fixture jobs render every document`() {
        val list = System.getenv("SCHEMATIC_RENDER_FIXTURES")
        assumeTrue("SCHEMATIC_RENDER_FIXTURES not set; job renders skipped", list != null)
        val files = list!!.split(';').filter { it.isNotBlank() }.map(::File).flatMap { f ->
            if (f.isDirectory) f.listFiles { x -> x.name.endsWith(".shaft") }!!.sorted() else listOf(f)
        }
        for (f in files) renderJob(f.nameWithoutExtension, ShaftDocCodec.decode(f.readText()))
    }

    private fun renderJob(job: String, doc: ShaftDocCodec.Decoded) {
        val unit = doc.preferredUnit ?: UnitSystem.INCHES
        val units = DisplayUnits(unit, doc.unitOverrides, doc.dualUnits)
        val resolved = resolveComponents(doc.spec)
        val prefs = PdfPrefs(
            showUnitsNote = System.getenv("SCHEMATIC_RENDER_UNITS_NOTE") != null,
            wearAreaStyle = if (System.getenv("SCHEMATIC_RENDER_WEAR_STROKES") == null) WearAreaStyle.SHADE
            else WearAreaStyle.LINES,
        )
        fun consolidated(name: String, bubbles: Boolean, wear: Boolean, blank: Boolean, classic: Boolean = false) {
            val (bmp, c) = bitmapPage()
            composeRunoutPdfOnCanvas(
                c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
                spec = doc.spec, config = doc.runoutConfig, project = doc.project(), unit = unit,
                displayUnits = units, pdfPrefs = prefs, resolvedComponents = resolved,
                runoutReadings = doc.runoutReadings, runoutStationPlacements = doc.runoutStationPlacements,
                wearRecord = if (classic) WearRecord() else doc.wearRecord,
                blankValues = blank, consolidated = !classic,
                includeBubbles = bubbles, includeWearInfo = wear,
            )
            save("${job}_$name.png", bmp)
        }
        run {
            val (bmp, c) = bitmapPage()
            composeShaftPdfOnCanvas(
                c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
                spec = doc.spec, unit = unit, project = doc.project(), appVersion = "review",
                filename = job, pdfPrefs = prefs, resolvedComponents = resolved,
                heightScale = doc.runoutConfig.heightScale,
                linerMinFracOfTrue = doc.runoutConfig.linerMinFracOfTrue,
                displayUnits = units,
            )
            save("${job}_schematic.png", bmp)
        }
        consolidated("consolidated", bubbles = true, wear = true, blank = false)
        consolidated("consolidated_sr", bubbles = true, wear = false, blank = false)
        consolidated("consolidated_blank", bubbles = true, wear = true, blank = true)
        consolidated("consolidated_sr_blank", bubbles = true, wear = false, blank = true)
        consolidated("runout", bubbles = true, wear = false, blank = false, classic = true)
        run {
            val (bmp, c) = bitmapPage()
            composeWearPdf(
                page = pageOn(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT),
                spec = doc.spec, project = doc.project(), unit = unit, displayUnits = units,
                pdfPrefs = prefs, resolvedComponents = resolved, wearRecord = doc.wearRecord,
                traceDepthFrac = effectiveWearTraceDepthFrac(doc.wearRecord.traceDepthFrac, prefs.wearTraceDepthFrac),
                heightScale = doc.runoutConfig.heightScale,
            )
            save("${job}_wear.png", bmp)
        }
        run {
            val o = undercutSheetOrientation(doc.spec, resolved, doc.undercutRecord, false)
            val bmp = Bitmap.createBitmap((o.widthPt * SCALE).toInt(), (o.heightPt * SCALE).toInt(), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val c = Canvas(bmp)
            c.scale(SCALE, SCALE)
            composeUndercutPdf(
                pageOn(c, o.widthPt, o.heightPt), doc.spec, doc.project(), unit,
                displayUnits = units, pdfPrefs = prefs, resolvedComponents = resolved,
                undercutRecord = doc.undercutRecord,
            )
            save("${job}_undercuts.png", bmp)
        }
    }

    private companion object {
        const val SCALE = 2f
    }
}
