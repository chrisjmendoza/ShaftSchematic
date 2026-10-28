package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.UnitSystem
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

    @Test
    fun `mid-shaft taper job`() {
        renderSchematic("mid_taper_schematic.png", midTaper)
        renderConsolidated("mid_taper_consolidated.png", midTaper)
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

    private fun renderSchematic(name: String, doc: ShaftDocCodec.Decoded, pdfPrefs: PdfPrefs = PdfPrefs()) {
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
        )
        save(name, bmp)
    }

    private fun renderConsolidated(name: String, doc: ShaftDocCodec.Decoded, pdfPrefs: PdfPrefs = PdfPrefs()) {
        val (bmp, c) = bitmapPage()
        composeRunoutPdfOnCanvas(
            c, PDF_PAGE_WIDTH_PT.toFloat(), PDF_PAGE_HEIGHT_PT.toFloat(),
            spec = doc.spec,
            config = doc.runoutConfig,
            project = doc.project(),
            unit = doc.preferredUnit ?: UnitSystem.INCHES,
            pdfPrefs = pdfPrefs,
            resolvedComponents = resolveComponents(doc.spec),
        )
        save(name, bmp)
    }

    private companion object {
        const val SCALE = 2f
    }
}
