package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A spooned keyway's footer spec is followed by a reader note that the stated KW length
 * runs to the base of the spoon (where the mill cut ends). Non-spooned keyways get no note.
 */
class SpoonedKeywayFooterNoteTest {

    private val cfg = FooterConfig(
        showAftThread = false,
        showFwdThread = false,
        showAftTaper = true,
        showFwdTaper = true
    )

    private fun spoonedTaper() = Taper(
        startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 80f,
        keywayWidthMm = 25f, keywayDepthMm = 12f, keywayLengthMm = 200f,
        keywaySpooned = true,
    )

    @Test
    fun `note follows the KW line for a spooned taper keyway`() {
        val spec = ShaftSpec(overallLengthMm = 2000f, tapers = listOf(spoonedTaper()))
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val kwIdx = cols.aftLines.indexOfFirst { it.startsWith("KW: ") }
        assertTrue(kwIdx >= 0)
        assertEquals(SPOONED_KW_NOTE, cols.aftLines[kwIdx + 1])
    }

    @Test
    fun `no note for a plain taper keyway`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(spoonedTaper().copy(keywaySpooned = false)),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        assertTrue(cols.aftLines.any { it.startsWith("KW: ") })
        assertTrue(cols.aftLines.none { it == SPOONED_KW_NOTE })
    }

    @Test
    fun `note follows the Body KW line in the matching column`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(
                    startFromAftMm = 1400f, lengthMm = 600f, diaMm = 100f,
                    keywayWidthMm = 30f, keywayDepthMm = 15f, keywayLengthMm = 150f,
                    keywayEnd = LinerAuthoredReference.FWD,
                    keywaySpooned = true,
                ),
            ),
        )
        val cols = buildFooterEndColumns(spec, UnitSystem.MILLIMETERS, cfg)
        val kwIdx = cols.fwdLines.indexOfFirst { it.startsWith("Body KW: ") }
        assertTrue(kwIdx >= 0)
        assertEquals(SPOONED_KW_NOTE, cols.fwdLines[kwIdx + 1])
        assertTrue(cols.aftLines.none { it == SPOONED_KW_NOTE })
    }
}
