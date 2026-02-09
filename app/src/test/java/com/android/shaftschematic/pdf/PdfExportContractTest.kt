package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.android.shaftschematic.geom.computeOalWindow
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.ThreadAttachment
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAnchor
import com.android.shaftschematic.model.LinerDim
import com.android.shaftschematic.pdf.dim.SpanKind
import com.android.shaftschematic.pdf.dim.buildLinerSpans
import com.android.shaftschematic.pdf.dim.oalSpan
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.util.UnitSystem
import java.io.FileOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfExportContractTest {

    @Test
    fun PdfExport_ShortShaft_AftExcluded_FwdIncluded() {
        val unit = UnitSystem.INCHES
        val oalIn = 72.0
        val oalMm = inToMm(oalIn)
        val threadLenIn = 6.0
        val threadLenMm = inToMm(threadLenIn).toFloat()
        val spec = ShaftSpec(
            overallLengthMm = oalMm.toFloat(),
            threads = listOf(
                Threads(
                    id = "TH-AFT",
                    startFromAftMm = 0f,
                    lengthMm = threadLenMm,
                    majorDiaMm = 5f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.AFT
                ),
                Threads(
                    id = "TH-FWD",
                    startFromAftMm = (oalMm - threadLenMm).toFloat(),
                    lengthMm = threadLenMm,
                    majorDiaMm = 5f,
                    pitchMm = 2f,
                    excludeFromOAL = false,
                    endAttachment = ThreadAttachment.FWD
                )
            )
        )

        runFixture(
            name = "short_aft_excluded_fwd_included",
            spec = spec,
            unit = unit,
            expectAftThreadFooter = true,
            expectFwdThreadFooter = true,
            excludedThreadLengthsMm = listOf(threadLenMm.toDouble()),
            includedThreadLengthsMm = listOf(threadLenMm.toDouble())
        )
    }

    @Test
    fun PdfExport_LongShaft_BothThreadsExcluded_WithLiners() {
        val unit = UnitSystem.INCHES
        val oalIn = 240.0
        val oalMm = inToMm(oalIn)
        val threadLenIn = 8.0
        val threadLenMm = inToMm(threadLenIn).toFloat()
        val liners = listOf(
            Liner(id = "L1", startFromAftMm = 40f, lengthMm = 200f, odMm = 80f, endMmPhysical = 240f),
            Liner(id = "L2", startFromAftMm = 500f, lengthMm = 260f, odMm = 75f, endMmPhysical = 760f),
            Liner(id = "L3", startFromAftMm = 1200f, lengthMm = 300f, odMm = 70f, endMmPhysical = 1500f)
        )
        val spec = ShaftSpec(
            overallLengthMm = oalMm.toFloat(),
            threads = listOf(
                Threads(
                    id = "TH-AFT",
                    startFromAftMm = 0f,
                    lengthMm = threadLenMm,
                    majorDiaMm = 6f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.AFT
                ),
                Threads(
                    id = "TH-FWD",
                    startFromAftMm = (oalMm - threadLenMm).toFloat(),
                    lengthMm = threadLenMm,
                    majorDiaMm = 6f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.FWD
                )
            ),
            liners = liners
        )

        runFixture(
            name = "long_both_excluded_liners",
            spec = spec,
            unit = unit,
            expectAftThreadFooter = true,
            expectFwdThreadFooter = true,
            excludedThreadLengthsMm = listOf(threadLenMm.toDouble()),
            includedThreadLengthsMm = emptyList()
        )
    }

    @Test
    fun PdfExport_MixedThreads_TwoTapers() {
        val unit = UnitSystem.INCHES
        val oalIn = 150.0
        val oalMm = inToMm(oalIn)
        val aftThreadLenIn = 5.0
        val fwdThreadLenIn = 6.0
        val aftThreadLenMm = inToMm(aftThreadLenIn).toFloat()
        val fwdThreadLenMm = inToMm(fwdThreadLenIn).toFloat()
        val spec = ShaftSpec(
            overallLengthMm = oalMm.toFloat(),
            tapers = listOf(
                Taper(
                    id = "TAPER-AFT",
                    startFromAftMm = 0f,
                    lengthMm = inToMm(12.0).toFloat(),
                    startDiaMm = 7f,
                    endDiaMm = 6f
                ),
                Taper(
                    id = "TAPER-FWD",
                    startFromAftMm = (oalMm - inToMm(10.0)).toFloat(),
                    lengthMm = inToMm(10.0).toFloat(),
                    startDiaMm = 6f,
                    endDiaMm = 5.5f
                )
            ),
            threads = listOf(
                Threads(
                    id = "TH-AFT",
                    startFromAftMm = 0f,
                    lengthMm = aftThreadLenMm,
                    majorDiaMm = 5f,
                    pitchMm = 2f,
                    excludeFromOAL = false,
                    endAttachment = ThreadAttachment.AFT
                ),
                Threads(
                    id = "TH-FWD",
                    startFromAftMm = (oalMm - fwdThreadLenMm).toFloat(),
                    lengthMm = fwdThreadLenMm,
                    majorDiaMm = 5f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.FWD
                )
            )
        )

        runFixture(
            name = "mixed_threads_two_tapers",
            spec = spec,
            unit = unit,
            expectAftThreadFooter = true,
            expectFwdThreadFooter = true,
            excludedThreadLengthsMm = listOf(fwdThreadLenMm.toDouble()),
            includedThreadLengthsMm = listOf(aftThreadLenMm.toDouble())
        )
    }

    @Test
    fun PdfExport_Minimal_NoThreadsOrTapers() {
        val unit = UnitSystem.MILLIMETERS
        val spec = ShaftSpec(
            overallLengthMm = 500f,
            threads = emptyList(),
            tapers = emptyList(),
            liners = emptyList()
        )

        runFixture(
            name = "minimal_no_threads",
            spec = spec,
            unit = unit,
            expectAftThreadFooter = false,
            expectFwdThreadFooter = false,
            excludedThreadLengthsMm = emptyList(),
            includedThreadLengthsMm = emptyList()
        )
    }

    @Test
    fun PdfExport_DimensionOffsets_WithExcludedThreads() {
        val oalMm = inToMm(120.0)
        val aftLen = inToMm(6.0).toFloat()
        val fwdLen = inToMm(4.0).toFloat()
        val spec = ShaftSpec(
            overallLengthMm = oalMm.toFloat(),
            threads = listOf(
                Threads(
                    id = "TH-AFT",
                    startFromAftMm = 0f,
                    lengthMm = aftLen,
                    majorDiaMm = 5f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.AFT
                ),
                Threads(
                    id = "TH-FWD",
                    startFromAftMm = (oalMm - fwdLen).toFloat(),
                    lengthMm = fwdLen,
                    majorDiaMm = 5f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.FWD
                )
            )
        )

        val win = computeDimensionWindowForPdf(spec)
        assertTrue("dimensionStartOffset should be > 0", win.measureStartMm > 0.0)
        assertTrue("dimensionEndOffset should reduce end", win.measureEndMm < spec.overallLengthMm.toDouble())
    }

    private fun runFixture(
        name: String,
        spec: ShaftSpec,
        unit: UnitSystem,
        expectAftThreadFooter: Boolean,
        expectFwdThreadFooter: Boolean,
        excludedThreadLengthsMm: List<Double>,
        includedThreadLengthsMm: List<Double>,
    ) {
        val project = ProjectInfo(
            customer = "Test",
            vessel = "Test",
            side = ShaftPosition.CENTER,
            jobNumber = "TEST-1"
        )

        val tempDir = createTempDirectory(prefix = "pdf_contract_${name}_")
        try {
            val outFile = tempDir.resolve("export.pdf").toFile()
            val bitmap = Bitmap.createBitmap(792, 612, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            composeShaftPdfOnCanvas(
                canvas = canvas,
                pageWidthPt = 792,
                pageHeightPt = 612,
                spec = spec,
                unit = unit,
                project = project,
                appVersion = "test",
                filename = "export.pdf",
                pdfPrefs = PdfPrefs(),
                options = PdfExportOptions(mode = PdfExportMode.Standard),
                resolvedComponents = null
            )

            FileOutputStream(outFile).use { out ->
                out.write(buildStubPdfContainerBytes())
                out.flush()
            }

            assertTrue("PDF file should be written ($name)", outFile.exists() && outFile.length() > 0)

            assertOalLabel(spec, unit, excludedThreadLengthsMm)
            assertDimensionSpans(spec, unit, excludedThreadLengthsMm, includedThreadLengthsMm)
            assertFooterThreads(spec, unit, expectAftThreadFooter, expectFwdThreadFooter)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun assertOalLabel(
        spec: ShaftSpec,
        unit: UnitSystem,
        excludedThreadLengthsMm: List<Double>,
    ) {
        val oalMm = spec.overallLengthMm.toDouble()
        val span = oalSpan(oalMm = oalMm, unit = unit)
        val expectedOalLabel = "OAL ${formatLenDim(oalMm, unit)}"
        assertEquals(expectedOalLabel, span.labelTop)
        excludedThreadLengthsMm.forEach { exMm ->
            val reduced = (oalMm - exMm).coerceAtLeast(0.0)
            val reducedLabel = formatLenDim(reduced, unit)
            assertTrue("OAL label should not use excluded length", !span.labelTop.contains(reducedLabel))
        }
    }

    private fun assertDimensionSpans(
        spec: ShaftSpec,
        unit: UnitSystem,
        excludedThreadLengthsMm: List<Double>,
        includedThreadLengthsMm: List<Double>,
    ) {
        val win = computeOalWindow(spec)
        val spans = buildLinerSpans(
            liners = buildLinerDims(spec),
            sets = com.android.shaftschematic.geom.computeSetPositionsInMeasureSpace(win),
            unit = unit,
            measureFrom = PdfTieringMode.AUTO
        ) + buildTaperLengthSpans(spec, win, unit)

        excludedThreadLengthsMm.forEach { exMm ->
            val exLabel = formatLenDim(exMm, unit)
            assertTrue("Excluded thread length should not appear in spans", spans.none { it.labelTop.contains(exLabel) })
        }

        includedThreadLengthsMm.forEach { inMm ->
            val inLabel = formatLenDim(inMm, unit)
            assertTrue("Included threads should not be treated as excluded spans", spans.none { it.labelTop.contains(inLabel) })
        }

        assertTrue("Span kinds should remain local/datum", spans.all { it.kind == SpanKind.LOCAL || it.kind == SpanKind.DATUM })
    }

    private fun assertFooterThreads(
        spec: ShaftSpec,
        unit: UnitSystem,
        expectAft: Boolean,
        expectFwd: Boolean,
    ) {
        val cfg = FooterConfig(
            showAftThread = expectAft,
            showFwdThread = expectFwd,
            showAftTaper = spec.tapers.any { it.startFromAftMm <= 0.5f },
            showFwdTaper = spec.tapers.any { abs((it.startFromAftMm + it.lengthMm) - spec.overallLengthMm) <= 0.5f },
            showCompressionNote = false
        )
        val cols = buildFooterEndColumns(spec, unit, cfg)

        val aftThreadLines = cols.aftLines.filter { it.startsWith("Thread:") }
        val fwdThreadLines = cols.fwdLines.filter { it.startsWith("Thread:") }

        if (expectAft) {
            assertTrue("AFT footer should list a thread", aftThreadLines.isNotEmpty())
        } else {
            assertTrue("AFT footer should not list a thread", aftThreadLines.isEmpty())
        }

        if (expectFwd) {
            assertTrue("FWD footer should list a thread", fwdThreadLines.isNotEmpty())
        } else {
            assertTrue("FWD footer should not list a thread", fwdThreadLines.isEmpty())
        }

        val unitSuffix = if (unit == UnitSystem.INCHES) " in" else " mm"
        (aftThreadLines + fwdThreadLines).forEach { line ->
            assertTrue("Footer thread formatting should include TPI", line.contains("TPI"))
            assertTrue("Footer thread formatting should include unit", line.contains(unitSuffix))
            assertTrue("Footer thread formatting should include separator", line.contains("×"))
        }
    }

    private fun inToMm(inches: Double): Double = inches * 25.4

    private fun buildStubPdfContainerBytes(): ByteArray {
        val header = "%PDF-1.4\n"
        val obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        val obj2 = "2 0 obj\n<< /Type /Pages /Count 0 >>\nendobj\n"
        val xrefStart = header.length + obj1.length + obj2.length
        val xref = buildString {
            append("xref\n")
            append("0 3\n")
            append("0000000000 65535 f \n")
            append(offsetLine(header.length))
            append(offsetLine(header.length + obj1.length))
        }
        val trailer = "trailer\n<< /Size 3 /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n"
        return (header + obj1 + obj2 + xref + trailer).toByteArray(Charsets.US_ASCII)
    }

    private fun offsetLine(offset: Int): String = "%010d 00000 n \n".format(offset)

    private fun buildLinerDims(spec: ShaftSpec): List<LinerDim> = spec.liners.map { ln ->
        LinerDim(
            id = ln.id,
            anchor = LinerAnchor.AFT_SET,
            offsetFromSetMm = ln.startFromAftMm.toDouble(),
            lengthMm = ln.lengthMm.toDouble()
        )
    }
}
