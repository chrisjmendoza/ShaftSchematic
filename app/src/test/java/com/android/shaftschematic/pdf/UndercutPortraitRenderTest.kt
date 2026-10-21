package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.model.UndercutSetDatum
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.FractionTypography
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
 * Review renders of the undercut sheet on both page shapes — the real composer onto a bitmap at
 * [SCALE]×, on the page [undercutSheetOrientation] chooses (or a forced one). Gated: a no-op unless
 * `UNDERCUT_RENDER_DIR` names the output directory, so the normal suite skips it.
 *
 * Run (PowerShell, repo root):
 * `$env:UNDERCUT_RENDER_DIR = '<dir>'; .\gradlew.bat :app:testDebugUnitTest --tests "com.android.shaftschematic.pdf.UndercutPortraitRenderTest" --rerun`
 * (`--rerun` because the env var is not a task input.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutPortraitRenderTest {

    private val inch = 25.4f
    private fun inch(v: Float) = v * inch

    private lateinit var outDir: File
    private lateinit var styleBefore: FractionStyle

    @Before
    fun setUp() {
        val dir = System.getenv("UNDERCUT_RENDER_DIR")
        assumeTrue("UNDERCUT_RENDER_DIR not set; undercut renders skipped", dir != null)
        outDir = File(dir!!).apply { mkdirs() }
        styleBefore = FractionTypography.active.style
        FractionTypography.setStyle(FractionStyle.Default)
    }

    @After
    fun tearDown() {
        if (::styleBefore.isInitialized) FractionTypography.setStyle(styleBefore)
    }

    // ── The on-device job ─────────────────────────────────────────────────────────────────

    private val polar = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )

    private val polarProject = ProjectInfo(
        customer = "Northern Shipwright & Drydock Services",
        vessel = "F/V Polar Ranger",
        side = ShaftPosition.STBD,
        jobNumber = "24-1138",
        item = "Tailshaft",
    )

    /** The recorded FWD-liner cuts plus one on the MID liner: the two-section sheet. */
    private val polarTwoSections: UndercutRecord = run {
        val mid = polar.spec.liners.sortedBy { it.startFromAftMm }[1]
        polar.undercutRecord.copy(
            undercuts = polar.undercutRecord.undercuts + Undercut(
                id = "mid-cut",
                startFromAftMm = mid.startFromAftMm + inch(9f),
                lengthMm = inch(2.5f),
                diaMm = inch(9.875f),
                authoredReference = UndercutReference.LINER_AFT,
                referenceLinerId = mid.id,
            ),
        )
    }

    private val polarLiners = polar.spec.liners.sortedBy { it.startFromAftMm }

    /** The two-section job plus a bare-shaft cut midway between the MID and FWD liners. */
    private val polarWithBareShaft: UndercutRecord = run {
        val mid = polarLiners[1]
        val fwd = polarLiners[2]
        val gapMid = (mid.startFromAftMm + mid.lengthMm + fwd.startFromAftMm) * 0.5f
        polarTwoSections.copy(
            undercuts = polarTwoSections.undercuts + Undercut(
                id = "bare-cut",
                startFromAftMm = gapMid - inch(1f),
                lengthMm = inch(2f),
                diaMm = inch(9.5f),
                authoredReference = UndercutReference.FWD_SET,
            ),
        )
    }

    // ── A synthetic four-liner shaft: four sections, two of them busy ──────────────────────

    private val fourSpec = ShaftSpec(
        overallLengthMm = inch(240f),
        autoBodyDiaMm = inch(5f),
        tapers = listOf(
            Taper(id = "T1", startFromAftMm = 0f, lengthMm = inch(14f), startDiaMm = inch(3.833f), endDiaMm = inch(5f), taperRateText = "1:12"),
            Taper(id = "T2", startFromAftMm = inch(226f), lengthMm = inch(14f), startDiaMm = inch(5f), endDiaMm = inch(3.833f), taperRateText = "1:12"),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = inch(20f), lengthMm = inch(30f), odMm = inch(5.75f), label = "Stern tube liner"),
            Liner(id = "L2", startFromAftMm = inch(70f), lengthMm = inch(18f), odMm = inch(5.75f)),
            Liner(id = "L3", startFromAftMm = inch(120f), lengthMm = inch(26f), odMm = inch(5.75f)),
            Liner(id = "L4", startFromAftMm = inch(196f), lengthMm = inch(18f), odMm = inch(5.5f), label = "Seal liner"),
        ),
    )

    private fun cut(id: String, start: Float, len: Float, dia: Float, liner: String) = Undercut(
        id = id, startFromAftMm = inch(start), lengthMm = inch(len), diaMm = inch(dia),
        authoredReference = UndercutReference.LINER_AFT, referenceLinerId = liner,
    )

    private val fourRecord = UndercutRecord(
        undercuts = listOf(
            // L1 — busy: a relief with a deeper cut nested in it, chained to two more.
            cut("a1", 24f, 10f, 5.625f, "L1"),
            cut("a2", 27f, 3.25f, 5.5f, "L1"),
            cut("a3", 34f, 4.5f, 5.6875f, "L1"),
            cut("a4", 38.5f, 2.75f, 5.625f, "L1"),
            // L2 — one cut.
            cut("b1", 76f, 4f, 5.625f, "L2"),
            // L3 — busy: four chained cuts, one nested.
            cut("c1", 123f, 5f, 5.625f, "L3"),
            cut("c2", 128f, 6.5f, 5.5625f, "L3"),
            cut("c3", 129.5f, 2f, 5.4375f, "L3"),
            cut("c4", 134.5f, 3.75f, 5.625f, "L3"),
            cut("c5", 138.25f, 2.25f, 5.6875f, "L3"),
            // L4 — one cut.
            cut("d1", 202f, 5f, 5.375f, "L4"),
        ),
    )

    private val fourProject = ProjectInfo(
        customer = "NorthSound Marine",
        vessel = "FV Tern Point",
        side = ShaftPosition.PORT,
        jobNumber = "814201",
        item = "Tailshaft",
    )

    // ── Rendering ────────────────────────────────────────────────────────────────────────

    private fun render(
        name: String,
        spec: ShaftSpec,
        project: ProjectInfo,
        record: UndercutRecord,
        blank: Boolean = false,
        pdfPrefs: PdfPrefs = PdfPrefs(),
    ) {
        val resolved = resolveComponents(spec)
        val orientation = undercutSheetOrientation(spec, resolved, record, blank)
        val w = orientation.widthPt
        val h = orientation.heightPt
        val bmp = Bitmap.createBitmap((w * SCALE).toInt(), (h * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        composeUndercutPdf(
            pageOn(c, w, h), spec, project, UnitSystem.INCHES,
            resolvedComponents = resolved,
            undercutRecord = record,
            blankValues = blank,
            pdfPrefs = pdfPrefs,
        )
        FileOutputStream(File(outDir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A `PdfDocument.Page` over a bitmap canvas — the page constructor is private. */
    private fun pageOn(c: Canvas, w: Int, h: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(w, h, 1).create()
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(Canvas::class.java, PdfDocument.PageInfo::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(c, info)
    }

    @Test
    fun `render the review sheets`() {
        render(
            "polar_landscape.png", polar.spec, polarProject,
            polarTwoSections.copy(pageOrientation = UndercutPageOrientation.LANDSCAPE),
        )
        render("polar_portrait.png", polar.spec, polarProject, polarTwoSections)
        // "Draw to the shaft end" on a portrait page: the FWD liner strip chains on to the S.E.T.
        render("polar_portrait_draw_to_set.png", polar.spec, polarProject, polarTwoSections.copy(drawToSet = true))
        render("polar_portrait_blank.png", polar.spec, polarProject, polar.undercutRecord, blank = true)
        render("polar_as_recorded_auto.png", polar.spec, polarProject, polar.undercutRecord)
        // The same sheet with "Split cuts at liner edges" on: the overhanging AFT cut prints as two
        // short spans either side of the buried liner edge — three short values at the AFT end.
        render(
            "polar_as_recorded_split.png", polar.spec, polarProject, polar.undercutRecord,
            pdfPrefs = PdfPrefs(undercutSplitAtLinerEdge = true),
        )
        render("four_strips_portrait.png", fourSpec, fourProject, fourRecord)
        // The crowded four-section page with short chain values under a total (a 3/8" cut 1" in
        // from L1's AFT edge): each strip decides for itself whether its cell affords the lift.
        render(
            "four_strips_portrait_fallback.png", fourSpec, fourProject,
            fourRecord.copy(undercuts = fourRecord.undercuts + cut("a0", 21f, 0.375f, 5.6875f, "L1")),
        )
        render(
            "four_strips_landscape.png", fourSpec, fourProject,
            fourRecord.copy(pageOrientation = UndercutPageOrientation.LANDSCAPE),
        )
        // A fifth section: the portrait cap is four, so the fifth prints as the "+1 more" note.
        render(
            "five_strips_portrait.png",
            fourSpec.copy(
                liners = fourSpec.liners +
                    Liner(id = "L5", startFromAftMm = inch(160f), lengthMm = inch(12f), odMm = inch(5.5f)),
            ),
            fourProject,
            fourRecord.copy(undercuts = fourRecord.undercuts + cut("e1", 163f, 3f, 5.3f, "L5")),
        )
        render(
            "one_strip_portrait.png", polar.spec, polarProject,
            polar.undercutRecord.copy(pageOrientation = UndercutPageOrientation.PORTRAIT),
        )
        // ── The S.E.T. lead + section election ──
        // Every strip measures to the AFT S.E.T.: titles and leads both swing to it.
        render(
            "polar_lead_aft_forced.png", polar.spec, polarProject,
            polarTwoSections.copy(setDatum = UndercutSetDatum.AFT),
        )
        // "Draw to the shaft end": the FWD strip reaches its S.E.T. (no lead there); the MID strip
        // keeps its lead.
        render("polar_lead_draw_to_set.png", polar.spec, polarProject, polarTwoSections.copy(drawToSet = true))
        // The MID liner left off: one section, so Auto prints landscape.
        render(
            "polar_one_section.png", polar.spec, polarProject,
            polarTwoSections.copy(excludedLinerIds = listOf(polarLiners[1].id)),
        )
        // Every section left off: the note, no profile.
        render(
            "polar_none_selected.png", polar.spec, polarProject,
            polarTwoSections.copy(excludedLinerIds = polarLiners.map { it.id }),
        )
        // A bare-shaft section between the liners: its lead runs from the window edge.
        render("polar_bare_shaft.png", polar.spec, polarProject, polarWithBareShaft)
        render(
            "polar_bare_shaft_landscape.png", polar.spec, polarProject,
            polarWithBareShaft.copy(pageOrientation = UndercutPageOrientation.LANDSCAPE),
        )
    }

    private companion object {
        const val SCALE = 2f
    }
}
