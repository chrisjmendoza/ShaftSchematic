package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.DyePenResult
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.settings.PDF_TEXT_SCALE_MAX
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The consolidated sheet's "Spark test: PASS / FAIL" row: off by default, printed after the dye pen
 * row in the same lane when elected, on the same baseline, never past the content edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SparkTestRowTest {

    private val inch = 25.4f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val spec = ShaftSpec(
        overallLengthMm = 120f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 30f * inch, lengthMm = 20f * inch, odMm = 7f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 120f * inch, diaMm = 6f * inch)),
    )
    private val resolved = resolveComponents(spec)

    private fun consolidated(config: RunoutConfig, wear: WearRecord = WearRecord(), prefs: PdfPrefs = PdfPrefs()) =
        SheetTextRecorder(pageW, pageH).also { c ->
            composeRunoutPdfOnCanvas(
                c, pageW, pageH, spec,
                config = config, project = ProjectInfo(jobNumber = "1"), unit = UnitSystem.INCHES,
                resolvedComponents = resolved, consolidated = true, wearRecord = wear, pdfPrefs = prefs,
            )
        }

    @Test
    fun `no spark test row by default`() {
        assertFalse(consolidated(RunoutConfig(showDyePenTest = true)).strings.any { it.contains("Spark test") })
    }

    @Test
    fun `the spark test row follows the dye pen row on its baseline`() {
        val rec = consolidated(
            RunoutConfig(showDyePenTest = true, showSparkTest = true),
            WearRecord(dyePenResult = DyePenResult.PASS, sparkTestResult = DyePenResult.FAIL),
        )
        val dye = rec.texts.single { it.text == "Dye pen test:" }
        val spark = rec.texts.single { it.text == SPARK_TEST_LABEL }
        assertEquals(dye.rect.bottom, spark.rect.bottom, 0.01f)
        assertTrue("spark after dye pen", spark.rect.left > dye.rect.right)
        val fails = rec.texts.filter { it.text == "FAIL" }
        assertEquals(2, fails.size)
        assertTrue("inside the page", fails.maxOf { it.rect.right } < pageW)
    }

    @Test
    fun `the spark test row prints alone when the dye pen is off`() {
        val rec = consolidated(RunoutConfig(showSparkTest = true))
        assertTrue(rec.strings.contains(SPARK_TEST_LABEL))
        assertFalse(rec.strings.contains("Dye pen test:"))
    }

    @Test
    fun `a pair too wide for the lane puts the spark row on its own line under the dye pen row`() {
        val rec = consolidated(
            RunoutConfig(showDyePenTest = true, showSparkTest = true, showCouplingFace = true),
            prefs = PdfPrefs(footerTextScale = PDF_TEXT_SCALE_MAX),
        )
        val dye = rec.texts.single { it.text == "Dye pen test:" }
        val spark = rec.texts.single { it.text == SPARK_TEST_LABEL }
        assertTrue("spark row below the dye pen row", spark.rect.top >= dye.rect.bottom)
        assertEquals(dye.rect.left, spark.rect.left, 0.5f)
    }
}
