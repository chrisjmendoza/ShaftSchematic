package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The printed undercut sheet on the on-device job (fixture `undercut_end_thread_pacific_warrior.shaft`:
 * an AFT liner 18 3/8" from the AFT S.E.T. with three cuts, drawn to the shaft end; the AFT thread
 * EXCLUDED from the OAL; a keyway in the AFT taper).
 *
 * - The AFT liner strip reaches the shaft end, so it draws the thread past the taper's small end
 *   (on-device request) — and the sheet prints exactly the strings it printed without the thread:
 *   the thread is drawing only, no dimension changes.
 * - "Keyways" (`PdfPrefs.undercutKeyways`) off — the default — draws no keyway on any strip; on, the
 *   AFT taper's keyway draws on the strip that holds it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutEndThreadPdfTest {

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_end_thread_pacific_warrior.shaft")).readText(),
    )

    private fun compose(spec: ShaftSpec, prefs: PdfPrefs = PdfPrefs()): SheetTextRecorder {
        val o = undercutSheetOrientation(spec, resolveComponents(spec), doc.undercutRecord, false)
        val c = SheetTextRecorder(o.widthPt.toFloat(), o.heightPt.toFloat())
        composeUndercutPdf(
            pageOnCanvas(c, o.widthPt, o.heightPt), spec, ProjectInfo(), UnitSystem.INCHES,
            pdfPrefs = prefs,
            resolvedComponents = resolveComponents(spec),
            undercutRecord = doc.undercutRecord,
        )
        return c
    }

    private fun RecordedLine.sloped() = abs(x1 - x0) > 0.05f && abs(y1 - y0) > 0.05f

    /** The thread hatch: the sheet's only sloped strokes at the hatch paint's alpha. */
    private fun SheetTextRecorder.hatch() = lines.filter { it.sloped() && it.alpha == 160 }

    @Test
    fun `the AFT liner strip draws the excluded thread past the taper's small end`() {
        val c = compose(doc.spec)
        val hatch = c.hatch()
        assertTrue("no thread hatch on the sheet", hatch.isNotEmpty())
        // x(0): the AFT taper's small-end face — where its sloped outline starts. The only sloped
        // strokes at the outline weight are the tapers' surface lines, and this sheet's one taper
        // in a strip is the AFT one.
        val outlineW = c.lines.maxOf { it.strokeWidth }
        val xTaperStart = c.lines
            .filter { it.sloped() && it.alpha == 255 && abs(it.strokeWidth - outlineW) < 0.01f }
            .minOf { min(it.x0, it.x1) }
        assertTrue(
            "a hatch stroke starts right of x(0) ($xTaperStart)",
            hatch.all { min(it.x0, it.x1) < xTaperStart },
        )
        assertTrue("no hatch lies wholly left of x(0)", hatch.any { max(it.x0, it.x1) < xTaperStart })
    }

    @Test
    fun `the thread changes no printed value`() {
        val withThread = compose(doc.spec).strings.sorted()
        val noThread = compose(doc.spec.copy(threads = emptyList())).strings.sorted()
        assertEquals(noThread, withThread)
        assertTrue("no thread hatch without the thread", compose(doc.spec.copy(threads = emptyList())).hatch().isEmpty())
    }

    @Test
    fun `keyways draw on a strip only with the switch on`() {
        assertTrue("a keyway drew with the switch off", compose(doc.spec).arcs.isEmpty())
        assertTrue(
            "the AFT taper's keyway did not draw with the switch on",
            compose(doc.spec, PdfPrefs(undercutKeyways = true)).arcs.isNotEmpty(),
        )
    }

    @Test
    fun `the keyway switch changes no printed value`() {
        assertEquals(
            compose(doc.spec).strings,
            compose(doc.spec, PdfPrefs(undercutKeyways = true)).strings,
        )
    }
}
