package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.ui.viewmodel.SpecTarget
import com.android.shaftschematic.ui.nav.FinalSheetKind
import com.android.shaftschematic.ui.nav.finalSheetKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Final preview's one sheet choice is a reading of the two session elections, so choosing a
 * sheet and reading the choice back must agree with the sheet `finalSheetKind` then draws.
 */
class FinalSheetChoiceTest {

    @Test
    fun `every choice reads back as itself`() {
        FinalSheetChoice.entries.forEach { c ->
            val (fullLength, beforeAfter) = finalSheetElections(c)
            assertEquals(c, finalSheetChoice(fullLength, beforeAfter))
        }
    }

    @Test
    fun `every choice draws the sheet it names`() {
        val expected = mapOf(
            FinalSheetChoice.FULL_LENGTH to FinalSheetKind.FULL_LENGTH,
            FinalSheetChoice.BEFORE_AFTER to FinalSheetKind.BEFORE_AFTER,
            FinalSheetChoice.FINAL_ONLY to FinalSheetKind.PLAIN,
        )
        expected.forEach { (c, kind) ->
            val (fullLength, beforeAfter) = finalSheetElections(c)
            assertEquals(kind, finalSheetKind(SpecTarget.FINAL, false, beforeAfter, fullLength))
        }
    }

    @Test
    fun `full length wins while both elections are on`() {
        assertEquals(FinalSheetChoice.FULL_LENGTH, finalSheetChoice(finalFullLength = true, finalBeforeAfter = true))
        assertEquals(FinalSheetChoice.FULL_LENGTH, finalSheetChoice(finalFullLength = true, finalBeforeAfter = false))
    }
}
