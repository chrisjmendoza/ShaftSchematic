package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.formatRunoutValue
import com.android.shaftschematic.util.parseRunoutValueInput
import com.android.shaftschematic.util.toMmOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Locale

/**
 * Field seeds are formatted for the PARSER, never for the device locale: on a comma-decimal device
 * (de, fr, es, pt-BR) a locale-following `%.4f` turns 12" into `12,0000`, which trims to `12,` —
 * text the parser refuses, so every whole-number seed opened as "Not a number", and `1,505` reads
 * as the ambiguous thousands form.
 */
class SeedLocaleTest {

    private inline fun inGermany(block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private val valuesMm = listOf(12f * 25.4f, 1.505f * 25.4f, 38.1f, 1505f, 0.5f)

    @Test
    fun `card seeds read back on a comma-decimal device`() = inGermany {
        assertEquals("12", formatDisplay(12f * 25.4f, UnitSystem.INCHES))
        for (unit in UnitSystem.values()) for (mm in valuesMm) {
            val text = formatDisplay(mm, unit)
            val back = toMmOrNull(text, unit)
            assertNotNull("'$text' ($unit) must parse", back)
            assertEquals(text, mm, back!!, 0.01f)
        }
    }

    @Test
    fun `Add dialog seeds read back on a comma-decimal device`() = inGermany {
        assertEquals("12", toDisplayString(12f * 25.4f, UnitSystem.INCHES))
        assertEquals("1.505", toDisplayString(1.505f * 25.4f, UnitSystem.INCHES))
        for (unit in UnitSystem.values()) for (mm in valuesMm) {
            val text = toDisplayString(mm, unit)
            val back = toMmOrNull(text, unit)
            assertNotNull("'$text' ($unit) must parse", back)
            assertEquals(text, mm, back!!, 0.03f)
        }
    }

    @Test
    fun `trimmed card decimals and runout values stay dotted`() = inGermany {
        assertEquals("1.5", 1.5f.fmtTrim(3))
        val tir = formatRunoutValue(0.0254f * 5f, UnitSystem.INCHES)
        assertEquals(".005", tir)
        assertEquals(0.127f, parseRunoutValueInput(tir, UnitSystem.INCHES)!!, 1e-4f)
    }
}
