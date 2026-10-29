package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.COUPLING_PILOT_COMPONENT_ID
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.PitSize
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearPit
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WornSection
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Review renders for the TODO "dual-units consolidated sheet: rail labels can overlap" bug —
 * the real consolidated composer onto a bitmap, dual units off / INLINE / STACKED, with and
 * without a grown footer. Gated: a no-op unless `DUAL_RAIL_RENDER_DIR` names the output
 * directory. `DUAL_RAIL_FIXTURES` (optional, `;`-separated `.shaft` files) adds real jobs.
 * Writes `report.txt` (overlapping string pairs, strings crossed by a horizontal rail line, and
 * the probe's reserved vs drawn rail lift) beside the PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualRailOverlapRenderTest {

    private lateinit var outDir: File
    private lateinit var styleBefore: FractionStyle
    private val report = StringBuilder()

    @Before
    fun setUp() {
        val dir = System.getenv("DUAL_RAIL_RENDER_DIR")
        assumeTrue("DUAL_RAIL_RENDER_DIR not set; dual rail renders skipped", dir != null)
        outDir = File(dir!!).apply { mkdirs() }
        styleBefore = FractionTypography.active.style
    }

    @After
    fun tearDown() {
        if (::styleBefore.isInitialized) FractionTypography.setStyle(styleBefore)
        if (::outDir.isInitialized) File(outDir, "report.txt").appendText(report.toString())
    }

    private class TextBox(val text: String, val rect: RectF)
    private class HLine(val x0: Float, val x1: Float, val y: Float)

    private class RecordingCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val boxes = mutableListOf<TextBox>()
        val hlines = mutableListOf<HLine>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            record(text, x, y, paint); super.drawText(text, x, y, paint)
        }
        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            record(text.substring(start, end), x, y, paint); super.drawText(text, start, end, x, y, paint)
        }
        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
            record(text.subSequence(start, end).toString(), x, y, paint); super.drawText(text, start, end, x, y, paint)
        }
        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
            record(String(text, index, count), x, y, paint); super.drawText(text, index, count, x, y, paint)
        }
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            if (kotlin.math.abs(startY - stopY) < 0.01f && kotlin.math.abs(stopX - startX) > 6f) {
                val pts = floatArrayOf(startX, startY, stopX, stopY)
                val m = Matrix()
                @Suppress("DEPRECATION")
                getMatrix(m)
                m.mapPoints(pts)
                hlines += HLine(min(pts[0], pts[2]), max(pts[0], pts[2]), pts[1])
            }
            super.drawLine(startX, startY, stopX, stopY, paint)
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

    // ── The SheetTextOverlapTest fixture (copied) ───────────────────────────────────────

    private val inch = 25.4f

    private val synthSpec: ShaftSpec = ShaftSpec(
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
                reliefLenMm = 0.375f * inch, reliefDiaMm = 5.5f * inch, reliefAtAft = false,
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

    private val synthProject = ProjectInfo(
        customer = "Tidewater Marine Services Incorporated of Louisiana",
        vessel = "M/V Titan of the Northern Seas",
        side = ShaftPosition.PORT,
        jobNumber = "935037",
        item = "Port line shaft",
        drawingLabel = "Final",
    )

    private val synthReadings = RunoutReadings(
        readings = listOf(
            RunoutReading("L1", 0, 0.05f * inch / 100f, 3),
            RunoutReading("L1", 1, 0.002f * inch, 15),
            RunoutReading("B1", 0, 0.004f * inch, 7),
            RunoutReading("B1", 3, 0.001f * inch, 21),
            RunoutReading("T2", 1, 0.003f * inch, 1),
            RunoutReading(COUPLING_PILOT_COMPONENT_ID, 0, 0.002f * inch, 12),
        ),
    )

    private val synthWear = WearRecord(
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

    /** The FWD thread relieved too — the SheetTextOverlapTest "grows the footer" case. */
    private val synthRelieved: ShaftSpec = synthSpec.copy(
        threads = synthSpec.threads.map {
            if (it.id == "THF") it.copy(reliefLenMm = 0.25f * inch, reliefDiaMm = 4.25f * inch, reliefAtAft = true)
            else it
        },
    )

    private class Job(
        val name: String,
        val spec: ShaftSpec,
        val project: ProjectInfo,
        val unit: UnitSystem,
        val overrides: Map<String, UnitSystem>,
        val config: RunoutConfig,
        val readings: RunoutReadings,
        val placements: RunoutStationPlacements,
        val wear: WearRecord,
    )

    private fun synthJob(name: String, spec: ShaftSpec) = Job(
        name, spec, synthProject, UnitSystem.INCHES, emptyMap(),
        RunoutConfig(tirDirection = TirDirection.AFT, showCouplingFace = true),
        synthReadings, RunoutStationPlacements(), synthWear,
    )

    /** Every thread without a relief gets one (3/8" long, major Ø − 1/4"). */
    private fun ShaftSpec.withReliefs(): ShaftSpec = copy(
        threads = threads.map {
            if (it.hasRelief) it else it.copy(reliefLenMm = 0.375f * inch, reliefDiaMm = it.majorDiaMm - 0.25f * inch)
        },
    )

    // ── Rendering ────────────────────────────────────────────────────────────────────────

    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private enum class Dual { OFF, INLINE, STACKED }

    private fun compose(c: Canvas, job: Job, dual: Dual, probe: ((RunoutSheetLayout) -> Unit)? = null) {
        val prefs = when (dual) {
            Dual.STACKED -> PdfPrefs(dualUnitLayout = DualUnitLayout.STACKED)
            else -> PdfPrefs(dualUnitLayout = DualUnitLayout.INLINE)
        }
        composeRunoutPdfOnCanvas(
            c, pageW, pageH, job.spec,
            config = job.config, project = job.project, unit = job.unit,
            displayUnits = DisplayUnits(job.unit, job.overrides, dual = dual != Dual.OFF),
            pdfPrefs = prefs,
            resolvedComponents = resolveComponents(job.spec),
            runoutReadings = job.readings, runoutStationPlacements = job.placements,
            wearRecord = job.wear,
            blankValues = false, consolidated = true,
            includeBubbles = true, includeWearInfo = true,
            layoutProbe = probe,
        )
    }

    private fun overlaps(boxes: List<TextBox>, inset: Float): List<Pair<TextBox, TextBox>> {
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

    private fun lineHits(c: RecordingCanvas, inset: Float): List<Pair<TextBox, HLine>> =
        c.boxes.flatMap { b ->
            c.hlines.filter { l ->
                l.y > b.rect.top + inset && l.y < b.rect.bottom - inset &&
                    min(l.x1, b.rect.right) - max(l.x0, b.rect.left) > 2 * inset
            }.map { b to it }
        }

    private fun renderVariant(job: Job, dual: Dual) {
        val variant = "${job.name}_dual-${dual.name.lowercase()}"
        // Detection pass at 1x with INLINE fractions (the SheetTextOverlapTest convention: a
        // built-up fraction's parts legitimately touch).
        FractionTypography.setStyle(FractionStyle.INLINE)
        var layout: RunoutSheetLayout? = null
        val det = RecordingCanvas(Bitmap.createBitmap(pageW.toInt(), pageH.toInt(), Bitmap.Config.ARGB_8888))
        compose(det, job, dual) { layout = it }
        val bad = overlaps(det.boxes, 1f)
        val hits = lineHits(det, 1f)
        report.appendLine("== $variant")
        report.appendLine("   probe: railLiftReserved=${layout?.railLiftReservedPt} railLiftDrawn=${layout?.railLiftDrawnPt} shaftH=${layout?.shaftHeightPt} footer=${layout?.footerBlockPt}")
        report.appendLine("   text-text overlaps: ${bad.size}")
        bad.forEach { (a, b) -> report.appendLine("     \"${a.text}\" ${a.rect.toShortString()}  x  \"${b.text}\" ${b.rect.toShortString()}") }
        report.appendLine("   text crossed by a horizontal line: ${hits.size}")
        hits.forEach { (b, l) -> report.appendLine("     \"${b.text}\" ${b.rect.toShortString()}  line y=${l.y} x=${l.x0}..${l.x1}") }

        // Image pass at SCALE with the shipped fraction style.
        FractionTypography.setStyle(FractionStyle.Default)
        val bmp = Bitmap.createBitmap((pageW * SCALE).toInt(), (pageH * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        compose(c, job, dual)
        save(bmp, "$variant.png")
        // Rail band: page top down to the first rail-free gap above the shaft — the rails block
        // ends at most ~45% down the page on these sheets.
        val railBottom = pageH * 0.48f
        region(job, dual, "${variant}_rails_aft.png", 10f, 10f, pageW / 2f + 20f, railBottom)
        region(job, dual, "${variant}_rails_fwd.png", pageW / 2f - 20f, 10f, pageW - 10f, railBottom)
        // A close crop round each overlap / line hit in the rail band.
        val spots = (bad.map { (a, b) -> RectF(a.rect).apply { union(b.rect) } } +
            hits.map { (b, l) -> RectF(b.rect).apply { union(l.x0.coerceAtLeast(b.rect.left - 20f), l.y) } })
            .filter { it.top < railBottom }
        spots.take(6).forEachIndexed { i, r ->
            region(job, dual, "${variant}_hit$i.png", r.left - 40f, r.top - 25f, r.right + 40f, r.bottom + 25f, scale = CROP_SCALE)
        }
    }

    private fun save(bmp: Bitmap, name: String) {
        FileOutputStream(File(outDir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Re-render page-pt rect [l,t,r,b] of the variant at [scale] (a true render, not a resample). */
    private fun region(job: Job, dual: Dual, name: String, l: Float, t: Float, r: Float, b: Float, scale: Float = RAIL_CROP_SCALE) {
        val ll = l.coerceAtLeast(0f); val tt = t.coerceAtLeast(0f)
        val rr = r.coerceAtMost(pageW); val bb = b.coerceAtMost(pageH)
        if (rr <= ll || bb <= tt) return
        val bmp = Bitmap.createBitmap(((rr - ll) * scale).toInt(), ((bb - tt) * scale).toInt(), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-ll, -tt)
        compose(c, job, dual)
        save(bmp, name)
    }

    private fun jobsFromEnv(): List<Job> {
        val list = System.getenv("DUAL_RAIL_FIXTURES") ?: return emptyList()
        return list.split(';').filter { it.isNotBlank() }.map(::File).flatMap { f ->
            if (f.isDirectory) f.listFiles { x -> x.name.endsWith(".shaft") }!!.sorted() else listOf(f)
        }.flatMap { f ->
            val d = ShaftDocCodec.decode(f.readText())
            val project = ProjectInfo(
                customer = d.customer, vessel = d.vessel, side = d.shaftPosition,
                jobNumber = d.jobNumber, item = d.item,
            )
            val base = f.nameWithoutExtension
            fun job(n: String, s: ShaftSpec) = Job(
                n, s, project, d.preferredUnit ?: UnitSystem.INCHES, d.unitOverrides,
                d.runoutConfig.copy(showCouplingFace = true),
                d.runoutReadings, d.runoutStationPlacements, d.wearRecord,
            )
            listOf(job(base, d.spec), job("${base}_relief", d.spec.withReliefs()))
        }
    }

    @Test
    fun `dual rail variants`() {
        report.appendLine("# DualRailOverlapRenderTest")
        val jobs = listOf(synthJob("synth", synthSpec), synthJob("synth_relief", synthRelieved)) + jobsFromEnv()
        for (job in jobs) for (dual in Dual.values()) renderVariant(job, dual)
    }

    private companion object {
        const val SCALE = 3f
        const val RAIL_CROP_SCALE = 6f
        const val CROP_SCALE = 8f
    }
}
