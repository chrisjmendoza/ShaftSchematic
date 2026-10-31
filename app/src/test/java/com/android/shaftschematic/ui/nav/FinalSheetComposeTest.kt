package com.android.shaftschematic.ui.nav

import com.android.shaftschematic.ui.viewmodel.SpecTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Which sheet a schematic surface draws. The preview, its Print button, and the SAF export all
 * ask this one question, so what it pins is that bubbles and the before/after sheet are
 * FINAL-drawing elections and nothing else: an ORIGINAL surface must draw the plain schematic
 * whatever the session flags say, or the Runout tab's document would have a second, unnumbered
 * claimant. Between the two elections, bubbles win — the consolidated composer has no band mode.
 */
class FinalSheetComposeTest {

    @Test
    fun `the original schematic never carries bubbles`() {
        assertEquals(
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.ORIGINAL, finalRunoutBubbles = false, finalBeforeAfter = false),
        )
        assertEquals(
            "a stray session flag cannot reshape the original's sheet",
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.ORIGINAL, finalRunoutBubbles = true, finalBeforeAfter = false),
        )
    }

    @Test
    fun `the original schematic is never the before-after sheet`() {
        assertEquals(
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.ORIGINAL, finalRunoutBubbles = false, finalBeforeAfter = true),
        )
        assertEquals(
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.ORIGINAL, finalRunoutBubbles = true, finalBeforeAfter = true),
        )
    }

    @Test
    fun `the final drawing is the machining copy when neither election is on`() {
        assertEquals(
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.FINAL, finalRunoutBubbles = false, finalBeforeAfter = false),
        )
    }

    @Test
    fun `the final drawing prints the before-after sheet when elected`() {
        assertEquals(
            FinalSheetKind.BEFORE_AFTER,
            finalSheetKind(SpecTarget.FINAL, finalRunoutBubbles = false, finalBeforeAfter = true),
        )
    }

    @Test
    fun `bubbles win over the before-after election`() {
        assertEquals(
            FinalSheetKind.WITH_BUBBLES,
            finalSheetKind(SpecTarget.FINAL, finalRunoutBubbles = true, finalBeforeAfter = false),
        )
        assertEquals(
            FinalSheetKind.WITH_BUBBLES,
            finalSheetKind(SpecTarget.FINAL, finalRunoutBubbles = true, finalBeforeAfter = true),
        )
    }

    @Test
    fun `the filename says which of the three final sheets this is`() {
        assertEquals(FINAL_FILENAME_SUFFIX, finalDrawingSuffix(runoutBubbles = false, beforeAfter = false))
        assertEquals(FINAL_BUBBLES_FILENAME_SUFFIX, finalDrawingSuffix(runoutBubbles = true, beforeAfter = false))
        assertEquals(FINAL_BEFORE_AFTER_FILENAME_SUFFIX, finalDrawingSuffix(runoutBubbles = false, beforeAfter = true))
        assertEquals("_Final_BeforeAfter", FINAL_BEFORE_AFTER_FILENAME_SUFFIX)
    }

    @Test
    fun `the filename follows the sheet kind's precedence`() {
        assertEquals(
            "bubbles win in the name as they do on the sheet",
            FINAL_BUBBLES_FILENAME_SUFFIX,
            finalDrawingSuffix(runoutBubbles = true, beforeAfter = true),
        )
    }

    @Test
    fun `the final documents never share a name`() {
        val names = listOf(
            FINAL_FILENAME_SUFFIX,
            FINAL_BUBBLES_FILENAME_SUFFIX,
            FINAL_BEFORE_AFTER_FILENAME_SUFFIX,
            "_Final_RunoutSheet",
        )
        assertEquals(names.size, names.toSet().size)
        assertNotEquals(FINAL_FILENAME_SUFFIX, FINAL_BEFORE_AFTER_FILENAME_SUFFIX)
    }
}
