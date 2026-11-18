package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.SheetTextGroup
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Text size per sheet group (`SheetTextGroup`): each composer prints at ITS group's sizes and no
 * other group's — the schematic at `dimTextScale` / `footerTextScale`, the consolidated sheet at the
 * runout pair, the undercut strips at `undercutDimTextScale` — and the Final tab's runout-composer
 * sheet at the schematic's.
 *
 * A sheet's "signature" is every string it inks with its text size and position, so two signatures
 * are equal only when the sheet is unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetTextGroupComposerTest {

    private val inch = 25.4f
    private val pageW = PDF_PAGE_WIDTH_PT.toFloat()
    private val pageH = PDF_PAGE_HEIGHT_PT.toFloat()

    private val spec = ShaftSpec(
        overallLengthMm = 120f * inch,
        liners = listOf(Liner(id = "L1", startFromAftMm = 30f * inch, lengthMm = 20f * inch, odMm = 7f * inch)),
        bodies = listOf(Body(id = "B1", startFromAftMm = 0f, lengthMm = 120f * inch, diaMm = 6f * inch)),
    )
    private val resolved = resolveComponents(spec)
    private val project = ProjectInfo(jobNumber = "1", customer = "Shop")

    private val big = 1.5f

    private fun signature(rec: SheetTextRecorder): List<String> =
        rec.texts.map { "${it.text}@${it.textSize}@${it.rect.left},${it.rect.top}" }

    private fun maxTextSize(rec: SheetTextRecorder): Float = rec.texts.maxOf { it.textSize }

    private fun consolidated(prefs: PdfPrefs, group: SheetTextGroup = SheetTextGroup.RUNOUT) =
        SheetTextRecorder(pageW, pageH).also { c ->
            composeRunoutPdfOnCanvas(
                c, pageW, pageH, spec,
                config = RunoutConfig(), project = project, unit = UnitSystem.INCHES,
                resolvedComponents = resolved, consolidated = true, pdfPrefs = prefs,
                textGroup = group,
            )
        }

    private fun schematic(prefs: PdfPrefs) = SheetTextRecorder(pageW, pageH).also { c ->
        composeShaftPdfOnCanvas(
            c, pageW, pageH, spec, UnitSystem.INCHES, project,
            appVersion = "test", filename = "t.pdf", pdfPrefs = prefs, resolvedComponents = resolved,
        )
    }

    private fun undercut(prefs: PdfPrefs) = SheetTextRecorder(pageW, pageH).also { c ->
        composeUndercutPdfOnCanvas(
            c, pageW.toInt(), pageH.toInt(), spec, project, UnitSystem.INCHES,
            pdfPrefs = prefs, resolvedComponents = resolved,
            undercutRecord = UndercutRecord(
                undercuts = listOf(
                    Undercut(id = "U1", startFromAftMm = 34f * inch, lengthMm = 4f * inch, diaMm = 6.75f * inch),
                    Undercut(id = "U2", startFromAftMm = 40f * inch, lengthMm = 3f * inch, diaMm = 6.5f * inch),
                ),
            ),
        )
    }

    @Test
    fun `forTextGroup maps each group's sizes and leaves the schematic's alone`() {
        val p = PdfPrefs(
            footerTextScale = 1.25f, dimTextScale = 1.5f,
            runoutFooterTextScale = 1.75f, runoutDimTextScale = 1f, undercutDimTextScale = 1.75f,
        )
        assertEquals(p, p.forTextGroup(SheetTextGroup.SCHEMATIC))
        val runout = p.forTextGroup(SheetTextGroup.RUNOUT)
        assertEquals(1f, runout.dimTextScale, 0f)
        assertEquals(1.75f, runout.footerTextScale, 0f)
        assertEquals(p.copy(dimTextScale = 1f, footerTextScale = 1.75f), runout)
        val undercut = p.forTextGroup(SheetTextGroup.UNDERCUT)
        assertEquals(1.75f, undercut.dimTextScale, 0f)
        // The undercut sheet has no footer: its footer size is left as stored.
        assertEquals(1.25f, undercut.footerTextScale, 0f)
        assertEquals(p.copy(dimTextScale = 1.75f), undercut)
    }

    @Test
    fun `the consolidated sheet prints at the runout group's sizes`() {
        val base = consolidated(PdfPrefs())
        val runoutDim = consolidated(PdfPrefs(runoutDimTextScale = big))
        val runoutFooter = consolidated(PdfPrefs(runoutFooterTextScale = big))
        assertNotEquals(signature(base), signature(runoutDim))
        assertNotEquals(signature(base), signature(runoutFooter))
        assertTrue("larger rail values", maxTextSize(runoutDim) > maxTextSize(base))
        // The schematic's and the undercut's sizes never reach it.
        val others = consolidated(
            PdfPrefs(footerTextScale = big, dimTextScale = big, undercutDimTextScale = big),
        )
        assertEquals(signature(base), signature(others))
    }

    @Test
    fun `the schematic ignores the runout and undercut sizes`() {
        val base = schematic(PdfPrefs())
        val others = schematic(
            PdfPrefs(runoutFooterTextScale = big, runoutDimTextScale = big, undercutDimTextScale = big),
        )
        assertEquals(signature(base), signature(others))
        assertNotEquals(signature(base), signature(schematic(PdfPrefs(dimTextScale = big))))
    }

    @Test
    fun `the undercut strips print at the undercut group's size only`() {
        val base = undercut(PdfPrefs())
        val own = undercut(PdfPrefs(undercutDimTextScale = big))
        assertNotEquals(signature(base), signature(own))
        assertTrue("larger strip text", maxTextSize(own) > maxTextSize(base))
        val others = undercut(
            PdfPrefs(
                footerTextScale = big, dimTextScale = big,
                runoutFooterTextScale = big, runoutDimTextScale = big,
            ),
        )
        assertEquals(signature(base), signature(others))
    }

    @Test
    fun `a Final runout-composer sheet prints at the schematic's sizes`() {
        val base = consolidated(PdfPrefs(), SheetTextGroup.SCHEMATIC)
        val schematicDim = consolidated(PdfPrefs(dimTextScale = big), SheetTextGroup.SCHEMATIC)
        assertNotEquals(signature(base), signature(schematicDim))
        val runoutOnly = consolidated(
            PdfPrefs(runoutDimTextScale = big, runoutFooterTextScale = big), SheetTextGroup.SCHEMATIC,
        )
        assertEquals(signature(base), signature(runoutOnly))
    }
}
