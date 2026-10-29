package com.android.shaftschematic.pdf

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.settings.DrawingProfile
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.PdfPageOrientation
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Settings → Drawing → "Units note on sheets" (`PdfPrefs.showUnitsNote`, on-device request): every
 * composer prints "Units in inches" / "Units in mm" — the DOCUMENT unit — in the bottom margin's
 * right corner while it is on, and nothing while it is off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnitsNoteTest {

    private val inch = 25.4f
    private val margin = 36f

    private val spec = ShaftSpec(
        overallLengthMm = 200f * inch,
        tapers = listOf(
            Taper(
                id = "T1", startFromAftMm = 0f, lengthMm = 20f * inch, startDiaMm = 10f * inch, endDiaMm = 8.5f * inch,
                keywayWidthMm = 2f * inch, keywayDepthMm = 0.75f * inch, keywayLengthMm = 14f * inch,
            ),
            Taper(
                id = "T2", startFromAftMm = 185f * inch, lengthMm = 15f * inch, startDiaMm = 9f * inch, endDiaMm = 10f * inch,
                keywayWidthMm = 2f * inch, keywayDepthMm = 0.75f * inch, keywayLengthMm = 10f * inch,
            ),
        ),
        threads = listOf(
            Threads(
                id = "TH", startFromAftMm = 200f * inch, lengthMm = 5f * inch,
                majorDiaMm = 6f * inch, tpi = 4f, excludeFromOAL = true, isAftEnd = false,
            ),
        ),
        liners = listOf(Liner(id = "L1", startFromAftMm = 40f * inch, lengthMm = 30f * inch, odMm = 11f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 70f * inch, lengthMm = 115f * inch, diaMm = 10f * inch)),
    )
    private val resolved = resolveComponents(spec)
    private val project = ProjectInfo(vessel = "Titan", jobNumber = "935038")
    private val wear = WearRecord(
        diaReadings = listOf(WearDiaReading(componentId = "L1", axialMm = 10f * inch, diaMm = 10.98f * inch)),
    )
    private val cut = Undercut(id = "U1", startFromAftMm = 45f * inch, lengthMm = 4f * inch, diaMm = 10.8f * inch)

    private fun prefs(on: Boolean) = PdfPrefs(showUnitsNote = on)

    // ── Each sheet, composed on a recording canvas ─────────────────────────────

    private class Sheet(val name: String, val w: Float, val h: Float, val c: SheetTextRecorder)

    private fun schematic(on: Boolean, unit: UnitSystem = UnitSystem.INCHES, final: Boolean = false): Sheet {
        val w = PDF_PAGE_WIDTH_PT.toFloat(); val h = PDF_PAGE_HEIGHT_PT.toFloat()
        val c = SheetTextRecorder(w, h)
        composeShaftPdfOnCanvas(
            c, w, h, spec, unit, if (final) project.copy(drawingLabel = "Final") else project,
            appVersion = "test", filename = "units_note", pdfPrefs = prefs(on), resolvedComponents = resolved,
            displayUnits = DisplayUnits(unit),
        )
        return Sheet(if (final) "final schematic" else "schematic", w, h, c)
    }

    private fun runout(on: Boolean, consolidated: Boolean): Sheet {
        val w = PDF_PAGE_WIDTH_PT.toFloat(); val h = PDF_PAGE_HEIGHT_PT.toFloat()
        val c = SheetTextRecorder(w, h)
        composeRunoutPdfOnCanvas(
            c, w, h, spec, config = RunoutConfig(showCouplingFace = true), project = project,
            unit = UnitSystem.INCHES, pdfPrefs = prefs(on), resolvedComponents = resolved,
            wearRecord = wear, consolidated = consolidated,
        )
        return Sheet(if (consolidated) "consolidated" else "classic runout", w, h, c)
    }

    private fun wearSheet(on: Boolean): Sheet {
        val w = PDF_PAGE_WIDTH_PT.toFloat(); val h = PDF_PAGE_HEIGHT_PT.toFloat()
        val c = SheetTextRecorder(w, h)
        composeWearPdf(
            page = pageOnCanvas(c, PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT), spec = spec, project = project,
            unit = UnitSystem.INCHES, pdfPrefs = prefs(on), resolvedComponents = resolved, wearRecord = wear,
        )
        return Sheet("wear", w, h, c)
    }

    private fun undercutSheet(on: Boolean, record: UndercutRecord, name: String): Sheet {
        val o = undercutSheetOrientation(spec, resolved, record, false)
        val w = o.widthPt.toFloat(); val h = o.heightPt.toFloat()
        val c = SheetTextRecorder(w, h)
        composeUndercutPdf(
            pageOnCanvas(c, o.widthPt, o.heightPt), spec, project, UnitSystem.INCHES,
            pdfPrefs = prefs(on), resolvedComponents = resolved, undercutRecord = record,
        )
        return Sheet("$name (${o.name})", w, h, c)
    }

    private fun allSheets(on: Boolean): List<Sheet> = listOf(
        schematic(on),
        schematic(on, final = true),
        runout(on, consolidated = false),
        runout(on, consolidated = true),
        wearSheet(on),
        undercutSheet(on, UndercutRecord(undercuts = listOf(cut), pageOrientation = UndercutPageOrientation.LANDSCAPE), "undercut"),
        undercutSheet(on, UndercutRecord(undercuts = listOf(cut), pageOrientation = UndercutPageOrientation.PORTRAIT), "undercut"),
        undercutSheet(on, UndercutRecord(undercuts = listOf(cut), excludedLinerIds = listOf("L1")), "undercut, no sections"),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test fun `the pref is off by default and the profile captures and restores it`() {
        assertFalse(PdfPrefs().showUnitsNote)
        assertFalse(DrawingProfile().toPdfPrefs().showUnitsNote)
        assertTrue(DrawingProfile.of(PdfPrefs(showUnitsNote = true), lineThicknessScale = 1f).toPdfPrefs().showUnitsNote)
    }

    @Test fun `the setting round-trips through the settings store`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        SettingsStore.setPdfShowUnitsNote(ctx, true)
        assertTrue(SettingsStore.pdfShowUnitsNoteFlow(ctx).first())
        SettingsStore.setPdfShowUnitsNote(ctx, false)
        assertFalse(SettingsStore.pdfShowUnitsNoteFlow(ctx).first())
    }

    @Test fun `the note names the document unit`() {
        assertEquals("Units in inches", unitsNoteText(UnitSystem.INCHES))
        assertEquals("Units in mm", unitsNoteText(UnitSystem.MILLIMETERS))
        assertTrue(unitsNoteText(UnitSystem.MILLIMETERS) in schematic(on = true, unit = UnitSystem.MILLIMETERS).c.strings)
    }

    @Test fun `every sheet prints the note in its bottom-right margin when on`() {
        for (s in allSheets(on = true)) {
            val notes = s.c.texts.filter { it.text == "Units in inches" }
            assertEquals("${s.name}: one note expected, drew ${s.c.strings}", 1, notes.size)
            val r = notes.single().rect
            assertTrue("${s.name}: note top ${r.top} rises above the margin line", r.top >= s.h - margin - 0.5f)
            assertTrue("${s.name}: note bottom ${r.bottom} is in the unprintable edge", r.bottom <= s.h - UNITS_NOTE_EDGE_CLEAR_PT + 0.5f)
            assertTrue("${s.name}: note not right-aligned at the margin (${r.right})", abs(r.right - (s.w - margin)) < 1f)
            val clash = s.c.texts.filter { it !== notes.single() && RectF.intersects(it.rect, r) }
            assertTrue("${s.name}: note collides with ${clash.map { it.text }}", clash.isEmpty())
        }
    }

    @Test fun `no sheet prints the note when off`() {
        for (s in allSheets(on = false)) {
            assertTrue("${s.name} printed a units note while off", s.c.strings.none { it.startsWith("Units in") })
        }
    }
}
