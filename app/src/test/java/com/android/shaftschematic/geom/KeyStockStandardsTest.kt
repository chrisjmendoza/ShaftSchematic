package com.android.shaftschematic.geom

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The standard key-stock tables behind the keyway "Standard size…" picker.
 *
 * Two properties hold for both tables, and a wrong row is otherwise invisible on a drawing: the
 * widths climb with shaft Ø, and the Ø ranges tile without gap or overlap, so every shaft inside
 * the covered span gets exactly one suggestion. The rest pins the depth convention (SHAFT keyseat
 * depth, not key height) and the "over … to and including" bound.
 */
class KeyStockStandardsTest {

    @Test
    fun `tables are monotonic in width and contiguous in shaft range`() {
        listOf(KEY_STOCK_ANSI_IN, KEY_STOCK_DIN_MM).forEach { table ->
            table.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "width must climb: ${a.label} → ${b.label}",
                    b.widthMm > a.widthMm,
                )
                assertEquals(
                    "ranges must tile: ${a.label} → ${b.label}",
                    a.shaftDiaMaxMm.toDouble(),
                    b.shaftDiaMinMm.toDouble(),
                    1e-4,
                )
            }
            table.forEach {
                assertTrue("${it.label}: depth must be positive", it.depthMm > 0f)
                assertTrue("${it.label}: depth is a keyseat, not a key", it.depthMm < it.widthMm)
            }
        }
    }

    @Test
    fun `inch table is selected by the keyway unit`() {
        assertEquals(KEY_STOCK_ANSI_IN, keyStockTable(UnitSystem.INCHES))
        assertEquals(KEY_STOCK_DIN_MM, keyStockTable(UnitSystem.MILLIMETERS))
    }

    @Test
    fun `a one inch shaft takes a quarter inch key`() {
        val s = suggestedKeyStock(keyStockTable(UnitSystem.INCHES), 25.4f)
        assertNotNull(s)
        assertEquals("1/4", s!!.label)
        assertEquals(6.35, s.widthMm.toDouble(), 1e-3)
        assertEquals(3.175, s.depthMm.toDouble(), 1e-3)
    }

    @Test
    fun `a five inch shaft takes a one and a quarter key`() {
        val s = suggestedKeyStock(keyStockTable(UnitSystem.INCHES), 5f * 25.4f)
        assertEquals("1 1/4", s?.label)
    }

    @Test
    fun `an eight inch shaft takes a rectangular key`() {
        val s = suggestedKeyStock(keyStockTable(UnitSystem.INCHES), 8f * 25.4f)
        assertNotNull(s)
        assertEquals("2 × 1 1/2", s!!.label)
        // Half the 1 1/2" key height — the depth cut into the shaft, not the stock.
        assertEquals(19.05, s.depthMm.toDouble(), 1e-3)
    }

    @Test
    fun `a twenty five millimetre shaft takes an eight by seven key`() {
        val s = suggestedKeyStock(keyStockTable(UnitSystem.MILLIMETERS), 25f)
        assertNotNull(s)
        assertEquals("8 × 7", s!!.label)
        assertEquals(8.0, s.widthMm.toDouble(), 1e-4)
        assertEquals(4.0, s.depthMm.toDouble(), 1e-4)
    }

    @Test
    fun `the upper bound of a range belongs to that range`() {
        // 200 mm is "up to 200", not "over 200" — the next row starts above it.
        val s = suggestedKeyStock(keyStockTable(UnitSystem.MILLIMETERS), 200f)
        assertEquals("45 × 25", s?.label)
    }

    @Test
    fun `a shaft outside every range suggests nothing`() {
        assertNull(suggestedKeyStock(keyStockTable(UnitSystem.MILLIMETERS), 3f))
        assertNull(suggestedKeyStock(keyStockTable(UnitSystem.MILLIMETERS), 900f))
        assertNull(suggestedKeyStock(keyStockTable(UnitSystem.INCHES), 1f))
    }
}
