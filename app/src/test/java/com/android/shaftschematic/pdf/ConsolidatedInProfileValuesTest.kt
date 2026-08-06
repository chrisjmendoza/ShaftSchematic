package com.android.shaftschematic.pdf

import com.android.shaftschematic.model.WearDiaReading
import com.android.shaftschematic.model.WornSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `consolidatedSheetHasInProfileValues` — the ONE predicate behind the liner-shade rule:
 * the composer suppresses the liner fill with it (sheet-white value halos would read as
 * pasted boxes over grey) and the Consolidated Output tab locks its "Liners" checkbox with
 * it. Pins that it answers exactly what the value passes draw: measured values > 0 only,
 * readings keyed to a component that still resolves, nothing at all without wear info or
 * on a blank draft.
 */
class ConsolidatedInProfileValuesTest {

    private fun section(vararg diaMm: Float) =
        WornSection(id = "worn", startFromAftMm = 100f, lengthMm = 50f, diaMm = diaMm.toList())

    private fun reading(componentId: String, diaMm: Float) =
        WearDiaReading(id = "r-$componentId", componentId = componentId, axialMm = 10f, diaMm = diaMm)

    private fun hasValues(
        wornSections: List<WornSection> = emptyList(),
        diaReadings: List<WearDiaReading> = emptyList(),
        resolvedComponentIds: Set<String> = setOf("body-1"),
        includeWearInfo: Boolean = true,
        blankValues: Boolean = false,
    ) = consolidatedSheetHasInProfileValues(
        wornSections = wornSections,
        diaReadings = diaReadings,
        resolvedComponentIds = resolvedComponentIds,
        includeWearInfo = includeWearInfo,
        blankValues = blankValues,
    )

    @Test
    fun `no wear marks at all - nothing prints inside the profile`() {
        assertFalse(hasValues())
    }

    @Test
    fun `worn section with a measured value prints`() {
        assertTrue(hasValues(wornSections = listOf(section(6.75f))))
    }

    @Test
    fun `worn section values at or below zero never print`() {
        // Placed-but-empty rule: the section still draws its boundaries, but no text.
        assertFalse(hasValues(wornSections = listOf(section(0f, -1f))))
        assertFalse(hasValues(wornSections = listOf(section())))
    }

    @Test
    fun `dia reading on a resolving component prints`() {
        assertTrue(hasValues(diaReadings = listOf(reading("body-1", 6.5f))))
    }

    @Test
    fun `orphaned dia reading never prints`() {
        // Render-layer orphan handling: the component no longer resolves, so nothing draws.
        assertFalse(hasValues(diaReadings = listOf(reading("body-gone", 6.5f))))
    }

    @Test
    fun `dia reading with no value never prints`() {
        assertFalse(hasValues(diaReadings = listOf(reading("body-1", 0f))))
    }

    @Test
    fun `wear info elected out - values never reach the sheet`() {
        assertFalse(
            hasValues(
                wornSections = listOf(section(6.75f)),
                diaReadings = listOf(reading("body-1", 6.5f)),
                includeWearInfo = false,
            )
        )
    }

    @Test
    fun `blank draft - every recorded value is blanked`() {
        assertFalse(
            hasValues(
                wornSections = listOf(section(6.75f)),
                diaReadings = listOf(reading("body-1", 6.5f)),
                blankValues = true,
            )
        )
    }
}
