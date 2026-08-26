package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.keywayUnitKey
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A metric keyway on an imperial shaft — the European job this exists for.
 *
 * The shop works in inches; a European shaft arrives with its keyway (and usually its thread) in
 * whole millimetres. The keyway line must print mm while everything around it, the taper's own
 * L.E.T. / S.E.T. / Length included, stays in inches — and with dual units OFF, because the point
 * is one number per dimension in the unit it was specified in.
 */
class KeywayUnitFooterTest {

    private val cfg = FooterConfig(
        showAftThread = false,
        showFwdThread = false,
        showAftTaper = true,
        showFwdTaper = true
    )

    private val inch = UnitSystem.INCHES
    private val mm = UnitSystem.MILLIMETERS

    /** A 20 x 12 x 150 mm keyway — whole millimetres, as European stock comes. */
    private val taperSpec = ShaftSpec(
        overallLengthMm = 2000f,
        tapers = listOf(
            Taper(
                id = "t1", startFromAftMm = 0f, lengthMm = 500f,
                startDiaMm = 150f, endDiaMm = 200f,
                keywayWidthMm = 20f, keywayDepthMm = 12f, keywayLengthMm = 150f,
            ),
        ),
    )

    private fun kwLine(spec: ShaftSpec, units: DisplayUnits, prefix: String): String =
        buildFooterEndColumns(spec, units.documentUnit, cfg, displayUnits = units)
            .let { it.aftLines + it.fwdLines }
            .first { it.startsWith(prefix) }

    @Test
    fun `a metric keyway prints millimetres on an imperial taper`() {
        val units = DisplayUnits(
            documentUnit = inch,
            overrides = mapOf(keywayUnitKey("t1") to mm),
        )
        val line = kwLine(taperSpec, units, "KW:")
        assertTrue("expected whole millimetres, got: $line", line.contains("20 mm"))
        assertTrue("expected whole millimetres, got: $line", line.contains("12 mm"))
        assertTrue("expected whole millimetres, got: $line", line.contains("150 mm"))
        assertTrue("the keyway line must not carry inches too: $line", !line.contains("\""))
    }

    @Test
    fun `the taper's own values stay in inches beside it`() {
        val units = DisplayUnits(
            documentUnit = inch,
            overrides = mapOf(keywayUnitKey("t1") to mm),
        )
        val cols = buildFooterEndColumns(taperSpec, inch, cfg, displayUnits = units)
        val lines = cols.aftLines + cols.fwdLines
        listOf("L.E.T.:", "S.E.T.:", "Length:").forEach { label ->
            val line = lines.first { it.startsWith(label) }
            assertTrue("$label should still be inches, got: $line", line.contains("\""))
            assertTrue("$label should not have gone metric: $line", !line.contains(" mm"))
        }
    }

    @Test
    fun `a keyway with no override still follows its component, exactly as before`() {
        val plain = DisplayUnits(documentUnit = inch)
        val overridden = DisplayUnits(documentUnit = inch, overrides = mapOf("t1" to mm))
        assertTrue(kwLine(taperSpec, plain, "KW:").contains("\""))
        assertTrue(kwLine(taperSpec, overridden, "KW:").contains(" mm"))
    }

    @Test
    fun `a body keyway takes its own unit too`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            bodies = listOf(
                Body(
                    id = "b1", startFromAftMm = 0f, lengthMm = 600f, diaMm = 180f,
                    keywayWidthMm = 20f, keywayDepthMm = 12f, keywayLengthMm = 150f,
                    keywayEnd = LinerAuthoredReference.AFT,
                ),
            ),
        )
        val units = DisplayUnits(documentUnit = inch, overrides = mapOf(keywayUnitKey("b1") to mm))
        val line = kwLine(spec, units, "Body KW:")
        assertTrue("expected millimetres, got: $line", line.contains("20 mm"))
        assertTrue("expected millimetres, got: $line", !line.contains("\""))
    }
}
