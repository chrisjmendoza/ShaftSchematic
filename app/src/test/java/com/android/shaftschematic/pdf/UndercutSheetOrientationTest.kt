package com.android.shaftschematic.pdf

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutPageOrientation
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PdfPageOrientation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [undercutSheetOrientation] on the on-device job (fixture `undercut_polar_ranger_stbd.shaft`:
 * three liners, all five recorded cuts on the FWD liner — ONE printed section). The page every
 * caller starts comes from the same strip list the composer draws, so the count here is the
 * count on the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndercutSheetOrientationTest {

    private val doc = ShaftDocCodec.decode(
        requireNotNull(javaClass.classLoader?.getResource("fixtures/undercut_polar_ranger_stbd.shaft")).readText(),
    )
    private val spec = doc.spec
    private val resolved = resolveComponents(spec)

    /** The fixture's record plus one cut on the MID liner — the two-section sheet the shop prints. */
    private val twoSectionRecord: UndercutRecord = run {
        val mid = spec.liners.sortedBy { it.startFromAftMm }[1]
        doc.undercutRecord.copy(
            undercuts = doc.undercutRecord.undercuts + Undercut(
                id = "mid-cut",
                startFromAftMm = mid.startFromAftMm + 200f,
                lengthMm = 50.8f,
                diaMm = 250f,
                authoredReference = UndercutReference.LINER_AFT,
                referenceLinerId = mid.id,
            ),
        )
    }

    @Test
    fun `the fixture as recorded is one section and prints landscape`() {
        assertEquals(1, undercutSheetStripCount(spec, resolved, doc.undercutRecord, blankValues = false))
        assertEquals(
            PdfPageOrientation.LANDSCAPE,
            undercutSheetOrientation(spec, resolved, doc.undercutRecord, blankValues = false),
        )
    }

    @Test
    fun `two sections on AUTO print portrait`() {
        assertEquals(UndercutPageOrientation.AUTO, twoSectionRecord.pageOrientation)
        assertEquals(2, undercutSheetStripCount(spec, resolved, twoSectionRecord, blankValues = false))
        assertEquals(
            PdfPageOrientation.PORTRAIT,
            undercutSheetOrientation(spec, resolved, twoSectionRecord, blankValues = false),
        )
    }

    @Test
    fun `a forced landscape keeps two sections landscape`() {
        val forced = twoSectionRecord.copy(pageOrientation = UndercutPageOrientation.LANDSCAPE)
        assertEquals(
            PdfPageOrientation.LANDSCAPE,
            undercutSheetOrientation(spec, resolved, forced, blankValues = false),
        )
    }

    @Test
    fun `a forced portrait prints one section portrait`() {
        val forced = doc.undercutRecord.copy(pageOrientation = UndercutPageOrientation.PORTRAIT)
        assertEquals(
            PdfPageOrientation.PORTRAIT,
            undercutSheetOrientation(spec, resolved, forced, blankValues = false),
        )
    }

    @Test
    fun `a blank draft counts its started liner strips and prints portrait`() {
        // The blank template drops the record: every drawable liner gets a started strip.
        assertEquals(3, undercutSheetStripCount(spec, resolved, doc.undercutRecord, blankValues = true))
        assertEquals(
            PdfPageOrientation.PORTRAIT,
            undercutSheetOrientation(spec, resolved, doc.undercutRecord, blankValues = true),
        )
        // The job's forced shape survives the blank draft.
        val forced = doc.undercutRecord.copy(pageOrientation = UndercutPageOrientation.LANDSCAPE)
        assertEquals(
            PdfPageOrientation.LANDSCAPE,
            undercutSheetOrientation(spec, resolved, forced, blankValues = true),
        )
    }

    @Test
    fun `the counted strips are the strips the composer derives`() {
        val sheet = undercutSheetStrips(spec, resolved, twoSectionRecord, blankValues = false)
        assertEquals(2, sheet.strips.size)
        assertEquals(false, sheet.startedPage)
        val blank = undercutSheetStrips(spec, resolved, twoSectionRecord, blankValues = true)
        assertEquals(true, blank.startedPage)
        assertEquals(3, blank.strips.size)
    }
}
