package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * The shaft's side (PORT / STBD / CENTER) prints ONCE on every sheet, large and bold: in the upper
 * right corner of a header sheet (runout, wear, undercut) and as the footer job block's badge on a
 * footer sheet (schematic, consolidated).
 *
 * The last test writes review renders when `SIDE_BADGE_RENDER_DIR` names a directory:
 * `$env:SIDE_BADGE_RENDER_DIR = '<dir>'; .\gradlew.bat :app:testDebugUnitTest --tests "com.android.shaftschematic.pdf.SideBadgePrintTest" --rerun`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideBadgePrintTest {

    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )
    private val spec = doc.spec
    private val resolved = resolveComponents(spec)

    private val job = ProjectInfo(
        customer = "Northern Shipwright & Drydock Services",
        vessel = "F/V Polar Ranger",
        side = ShaftPosition.STBD,
        jobNumber = "24-1138",
        item = "Tailshaft",
    )

    private fun schematic(c: Canvas, project: ProjectInfo) = composeShaftPdfOnCanvas(
        c, pageW, pageH, spec, UnitSystem.INCHES, project,
        appVersion = "test", filename = "job.pdf", resolvedComponents = resolved,
    )

    private fun runout(c: Canvas, project: ProjectInfo, consolidated: Boolean) = composeRunoutPdfOnCanvas(
        c, pageW, pageH, spec,
        config = RunoutConfig(), project = project, unit = UnitSystem.INCHES,
        resolvedComponents = resolved, consolidated = consolidated,
    )

    private fun wear(c: Canvas, project: ProjectInfo) = composeWearPdfOnCanvas(
        c, pageW, pageH, spec, project, UnitSystem.INCHES,
        resolvedComponents = resolved, wearRecord = doc.wearRecord,
    )

    private fun undercut(c: Canvas, project: ProjectInfo) = composeUndercutPdfOnCanvas(
        c, pageW.toInt(), pageH.toInt(), spec, project, UnitSystem.INCHES,
        resolvedComponents = resolved, undercutRecord = doc.undercutRecord,
    )

    private val sheets: Map<String, (Canvas, ProjectInfo) -> Unit> = mapOf(
        "schematic" to ::schematic,
        "consolidated" to { c, p -> runout(c, p, consolidated = true) },
        "runout" to { c, p -> runout(c, p, consolidated = false) },
        "wear" to ::wear,
        "undercut" to ::undercut,
    )

    private fun texts(compose: (Canvas, ProjectInfo) -> Unit, project: ProjectInfo = job): List<RecordedText> =
        SheetTextRecorder(pageW, pageH).also { compose(it, project) }.texts

    @Test
    fun `every sheet prints the side exactly once, well above its body text`() {
        sheets.forEach { (name, compose) ->
            val all = texts(compose)
            val side = all.filter { it.text.contains("STBD") }
            assertEquals("$name prints the side once", 1, side.size)
            val body = all.map { it.textSize }.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            assertTrue("$name: side at ${side[0].textSize} over body text $body", side[0].textSize >= body * 1.5f)
        }
    }

    @Test
    fun `a header sheet tucks the side into the upper right corner, clear of the job line`() {
        listOf("runout", "wear", "undercut").forEach { name ->
            val all = texts(sheets.getValue(name))
            val side = all.single { it.text == "STBD" }
            assertTrue("$name: right edge at the margin", side.rect.right > pageW - 40f)
            assertTrue("$name: at the top of the page", side.rect.top < 60f)
            val jobLine = all.single { it.text.startsWith("Customer:") }
            assertFalse(jobLine.text.contains("STBD"))
            assertTrue("$name: job line stays left of the badge", jobLine.rect.right < side.rect.left)
        }
    }

    @Test
    fun `a job with no side prints no badge and reserves the footer row it always has`() {
        val none = job.copy(side = ShaftPosition.OTHER)
        sheets.forEach { (name, compose) ->
            assertTrue(name, texts(compose, none).none { it.text.contains("STBD") })
        }
        val paint = android.graphics.Paint().apply { textSize = 10f }
        val cfg = FooterConfig(showAftThread = false, showFwdThread = false, showAftTaper = false, showFwdTaper = false)
        fun rows(p: ProjectInfo) = planFooter(0f, 700f, spec, UnitSystem.INCHES, p, paint, cfg).wrappedMaxLines
        assertEquals(rows(none) + FOOTER_SIDE_BADGE_ROWS - 1, rows(job))
    }

    @Test
    fun `the header badge is sized off its header block`() {
        assertEquals(36f * HEADER_SIDE_BADGE_HEIGHT_FRAC, headerSideBadgeTextSizePt(36f), 0.001f)
    }

    @Test
    fun `render the review sheets`() {
        val dir = System.getenv("SIDE_BADGE_RENDER_DIR")
        assumeTrue("SIDE_BADGE_RENDER_DIR not set; renders skipped", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        val scale = 2f
        val long = job.copy(notes = "Partially clad taper\nFiberglass FWD mid and AFT body", drawingLabel = "Final")
        fun render(name: String, project: ProjectInfo, compose: (Canvas, ProjectInfo) -> Unit) {
            val bmp = Bitmap.createBitmap((pageW * scale).toInt(), (pageH * scale).toInt(), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            compose(c, project)
            FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        sheets.forEach { (name, compose) -> render(name, job, compose) }
        render("schematic_final_notes", long, ::schematic)
        render("schematic_port", job.copy(side = ShaftPosition.PORT), ::schematic)
    }
}
