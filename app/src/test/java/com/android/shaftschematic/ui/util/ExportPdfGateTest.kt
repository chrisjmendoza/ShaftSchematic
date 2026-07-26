package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM port of the retired EditorTopBarExportPdfTest (androidTest): the Export-PDF
 * toolbar gate — disabled with no components (with the add-a-component message),
 * enabled once one exists, disabled again on collisions.
 */
class ExportPdfGateTest {

    @Test
    fun `disabled with no components, message asks to add one`() {
        val gate = exportPdfGate(ShaftSpec(overallLengthMm = 0f), collidingIds = emptySet())
        assertFalse(gate.enabled)
        assertTrue(gate.disabledMessage.contains("add at least 1 component"))
    }

    @Test
    fun `enabled when at least one component exists`() {
        val spec = ShaftSpec(
            overallLengthMm = 50f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 10f, diaMm = 10f))
        )
        assertTrue(exportPdfGate(spec, collidingIds = emptySet()).enabled)
    }

    @Test
    fun `disabled on collisions, message asks to fix them`() {
        val spec = ShaftSpec(
            overallLengthMm = 50f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 10f, diaMm = 10f))
        )
        val gate = exportPdfGate(spec, collidingIds = setOf("some-id"))
        assertFalse(gate.enabled)
        assertEquals("Fix component collisions before exporting.", gate.disabledMessage)
    }

    @Test
    fun `reference-only slots do not enable export`() {
        val spec = ShaftSpec(
            overallLengthMm = 50f,
            couplerBoltSlots = listOf(
                CouplerBoltSlot(startFromAftMm = 5f, holeDiaMm = 5f, count = 2, spacingMm = 10f)
            )
        )
        assertFalse(exportPdfGate(spec, collidingIds = emptySet()).enabled)
    }
}
