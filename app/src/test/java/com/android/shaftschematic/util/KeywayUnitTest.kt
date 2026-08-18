package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The keyway's own display unit — the second half of the mixed-unit case a European shaft brings
 * (the first being a metric thread's designation).
 *
 * What matters here is the FALLBACK CHAIN. A keyway with no choice of its own must behave exactly
 * as it did before this existed, or every document ever saved would shift.
 */
class KeywayUnitTest {

    private val inch = UnitSystem.INCHES
    private val mm = UnitSystem.MILLIMETERS

    @Test
    fun `a keyway with no override follows its component`() {
        val units = DisplayUnits(documentUnit = inch, overrides = mapOf("taper1" to mm))
        assertEquals(mm, units.unitFor("taper1"))
        assertEquals(mm, units.keywayUnitFor("taper1"))
    }

    @Test
    fun `a keyway with no override and no component override follows the document`() {
        val units = DisplayUnits(documentUnit = inch)
        assertEquals(inch, units.keywayUnitFor("body1"))
        assertEquals(inch, units.keywayUnitFor(null))
    }

    @Test
    fun `a metric keyway prints mm while its imperial taper stays inches`() {
        // The whole point: a European keyway on an otherwise imperial shaft, without dual units
        // and without dragging the taper's own L.E.T. / S.E.T. / Length into millimetres.
        val units = DisplayUnits(
            documentUnit = inch,
            overrides = mapOf(keywayUnitKey("taper1") to mm),
        )
        assertEquals(inch, units.unitFor("taper1"))
        assertEquals(mm, units.keywayUnitFor("taper1"))
    }

    @Test
    fun `a keyway override beats the component's own`() {
        val units = DisplayUnits(
            documentUnit = inch,
            overrides = mapOf("body1" to mm, keywayUnitKey("body1") to inch),
        )
        assertEquals(mm, units.unitFor("body1"))
        assertEquals(inch, units.keywayUnitFor("body1"))
    }

    @Test
    fun `the keyway key is derived, never collides with the component's own, and is stable`() {
        assertEquals("body1#kw", keywayUnitKey("body1"))
        // A keyway key must not read as a component override for the same id — the two live in one
        // map, and a collision would silently flip a whole component's units.
        val units = DisplayUnits(inch, mapOf(keywayUnitKey("body1") to mm))
        assertEquals(inch, units.unitFor("body1"))
    }
}
