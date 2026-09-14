package com.android.shaftschematic.ui.nav

import com.android.shaftschematic.ui.viewmodel.SpecTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which sheet a schematic surface draws. The preview, its Print button, and the SAF export all
 * ask this one question, so what it pins is that bubbles are a FINAL-drawing election and
 * nothing else: an ORIGINAL surface must draw the plain schematic whatever the session flag
 * says, or the Runout tab's document would have a second, unnumbered claimant.
 */
class FinalSheetComposeTest {

    @Test
    fun `the original schematic never carries bubbles`() {
        assertEquals(
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.ORIGINAL, finalRunoutBubbles = false),
        )
        assertEquals(
            "a stray session flag cannot reshape the original's sheet",
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.ORIGINAL, finalRunoutBubbles = true),
        )
    }

    @Test
    fun `the final drawing is the machining copy until bubbles are elected`() {
        assertEquals(
            FinalSheetKind.PLAIN,
            finalSheetKind(SpecTarget.FINAL, finalRunoutBubbles = false),
        )
        assertEquals(
            FinalSheetKind.WITH_BUBBLES,
            finalSheetKind(SpecTarget.FINAL, finalRunoutBubbles = true),
        )
    }

    @Test
    fun `the filename says which of the two final sheets this is`() {
        assertEquals(FINAL_FILENAME_SUFFIX, finalDrawingSuffix(runoutBubbles = false))
        assertEquals(FINAL_BUBBLES_FILENAME_SUFFIX, finalDrawingSuffix(runoutBubbles = true))
    }
}
