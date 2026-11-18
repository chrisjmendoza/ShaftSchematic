package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.geom.UndercutStrip
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * "Draw to the shaft end" past its reach (on-device request, from a shop sketch): a liner section
 * with only plain shaft between it and the S.E.T. it measures to prints that end too — taper and
 * all — across an S-break, and its chain runs to the S.E.T. at the true distance.
 *
 * The last test writes review renders when `UNDERCUT_BREAK_RENDER_DIR` names a directory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutSetBreakPdfTest {

    private val inch = 25.4f
    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_end_thread_pacific_warrior.shaft")).readText(),
    )
    private val spec = doc.spec
    private val resolved = resolveComponents(spec)
    private val liners = spec.liners.sortedBy { it.startFromAftMm }
    private val fwdLiner = liners.last()
    private val midLiner = liners[1]

    private fun cut(id: String, startIn: Float, lenIn: Float, diaIn: Float) = Undercut(
        id = id, startFromAftMm = startIn * inch, lengthMm = lenIn * inch, diaMm = diaIn * inch,
        authoredReference = UndercutReference.FWD_SET,
    )

    /** Cuts on the FWD liner only: 34 5/8" of plain shaft between the liner and the FWD taper. */
    private val fwdOnly = UndercutRecord(
        undercuts = listOf(
            cut("f1", 280f, 2.75f, 8.9f),
            cut("f2", 282.75f, 1.625f, 8.8f),
            cut("f3", 284.375f, 6.875f, 8.9f),
        ),
    )

    private fun strips(record: UndercutRecord) =
        undercutSheetStrips(spec, resolved, record, blankValues = false).strips.filterIsInstance<UndercutStrip.LinerStrip>()

    private fun compose(record: UndercutRecord): SheetTextRecorder {
        val o = undercutSheetOrientation(spec, resolved, record, false)
        val c = SheetTextRecorder(o.widthPt.toFloat(), o.heightPt.toFloat())
        composeUndercutPdf(
            pageOnCanvas(c, o.widthPt, o.heightPt), spec, ProjectInfo(), UnitSystem.INCHES,
            resolvedComponents = resolved, undercutRecord = record,
        )
        return c
    }

    @Test
    fun `a far FWD liner section breaks out the plain shaft and reaches the shaft end`() {
        val strip = strips(fwdOnly).single { it.linerId == fwdLiner.id }
        val gap = assertNotNull(strip.setBreak).let { strip.setBreak!! }
        val taperStart = spec.tapers.maxOf { it.startFromAftMm }
        assertTrue("the gap starts past the liner", gap.startMm > fwdLiner.startFromAftMm + fwdLiner.lengthMm)
        assertTrue("the gap ends short of the taper", gap.endMm < taperStart)
        assertEquals("the strip draws to the shaft end", spec.overallLengthMm, strip.drawEndMm, 0.01f)
        assertEquals("the chain runs to the FWD S.E.T.", spec.overallLengthMm, strip.chainEndMm, 0.01f)
    }

    @Test
    fun `the switch off, or a liner in the way, leaves the section as it was`() {
        assertNull(strips(fwdOnly.copy(drawToSet = false)).single().setBreak)
        // The mid liner measures to an end with another liner between: never broken out.
        val midCut = cut("m1", midLiner.startFromAftMm / inch + 4f, 3f, 8.9f)
        val both = fwdOnly.copy(undercuts = fwdOnly.undercuts + midCut)
        assertNull(strips(both).single { it.linerId == midLiner.id }.setBreak)
        assertNotNull(strips(both).single { it.linerId == fwdLiner.id }.setBreak)
    }

    @Test
    fun `the broken strip prints the distance to the SET and draws the taper, with no not-to-scale note`() {
        val c = compose(fwdOnly)
        val off = compose(fwdOnly.copy(drawToSet = false))
        // The last cut's end (291 1/4") to the FWD S.E.T. at the OAL (348 3/4"): 57 1/2", on the chain.
        assertTrue(c.strings.toString(), c.strings.any { it == "57" })
        assertTrue(off.strings.none { it == "57" })
        assertTrue(c.strings.none { it.contains("Not to scale") })
        // The FWD taper's sloped surface lines are on the sheet.
        val sloped = c.lines.count { abs(it.x1 - it.x0) > 0.5f && abs(it.y1 - it.y0) > 0.5f && it.alpha == 255 }
        val slopedOff = off.lines.count { abs(it.x1 - it.x0) > 0.5f && abs(it.y1 - it.y0) > 0.5f && it.alpha == 255 }
        assertTrue("taper drawn: $sloped sloped strokes vs $slopedOff", sloped > slopedOff)
    }

    @Test
    fun `render the review sheets`() {
        val dir = System.getenv("UNDERCUT_BREAK_RENDER_DIR")
        assumeTrue("UNDERCUT_BREAK_RENDER_DIR not set; renders skipped", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        val project = ProjectInfo(customer = "Pacific Fishing", vessel = "Pacific Warrior", jobNumber = "935067", side = ShaftPosition.PORT)
        fun render(name: String, record: UndercutRecord) {
            val o = undercutSheetOrientation(spec, resolved, record, false)
            val scale = 2f
            val bmp = Bitmap.createBitmap((o.widthPt * scale).toInt(), (o.heightPt * scale).toInt(), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            composeUndercutPdf(
                pageOnCanvas(c, o.widthPt, o.heightPt), spec, project, UnitSystem.INCHES,
                resolvedComponents = resolved, undercutRecord = record,
            )
            FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        render("fwd_break", fwdOnly)
        render("fwd_no_break", fwdOnly.copy(drawToSet = false))
        render("fwd_break_from_set", fwdOnly.copy(railStyle = com.android.shaftschematic.model.UndercutRailStyle.FROM_SET))
        render("all_liners", fwdOnly.copy(undercuts = fwdOnly.undercuts + doc.undercutRecord.undercuts))
    }
}
