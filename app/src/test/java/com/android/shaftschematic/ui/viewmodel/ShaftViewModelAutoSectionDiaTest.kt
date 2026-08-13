package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.withAutoSectionDia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The auto-section Ø upsert, exercised through the pure spec transform the ViewModel setter
 * delegates to (`setAutoSectionDiaMm` is a one-line `_spec.update { … }` wrapper around
 * [withAutoSectionDia]).
 *
 * [ShaftViewModel] is an `AndroidViewModel` and isn't instantiated in this JVM suite — the same
 * convention as `ShaftViewModelKeywayClockingTest` and `ShaftViewModelUndercutTest`.
 */
class ShaftViewModelAutoSectionDiaTest {

    // Mirrors ShaftViewModel.setAutoSectionDiaMm.
    private fun setAutoSectionDiaMm(spec: ShaftSpec, startMm: Float, endMm: Float, valueMm: Float) =
        spec.withAutoSectionDia(startMm, endMm, valueMm)

    @Test
    fun `a set anchors the value at the span midpoint`() {
        val result = setAutoSectionDiaMm(ShaftSpec(overallLengthMm = 1000f), 0f, 400f, 150f)

        assertEquals(listOf(AutoDiaOverride(anchorMm = 200f, diaMm = 150f)), result.autoDiaOverrides)
    }

    @Test
    fun `a second set replaces only the anchors inside that span`() {
        val start = ShaftSpec(
            overallLengthMm = 1000f,
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
            ),
        )

        val result = setAutoSectionDiaMm(start, 0f, 400f, 170f)

        assertEquals(2, result.autoDiaOverrides.size)
        assertTrue(result.autoDiaOverrides.contains(AutoDiaOverride(anchorMm = 800f, diaMm = 160f)))
        assertTrue(result.autoDiaOverrides.contains(AutoDiaOverride(anchorMm = 200f, diaMm = 170f)))
    }

    @Test
    fun `a span holding several anchors keeps exactly one after a set`() {
        val start = ShaftSpec(
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 100f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 300f, diaMm = 160f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 170f),
            ),
        )

        val result = setAutoSectionDiaMm(start, 0f, 400f, 180f)

        assertEquals(
            listOf(
                AutoDiaOverride(anchorMm = 800f, diaMm = 170f),
                AutoDiaOverride(anchorMm = 200f, diaMm = 180f),
            ),
            result.autoDiaOverrides,
        )
    }

    @Test
    fun `clearing removes the span's anchors without adding one`() {
        val start = ShaftSpec(
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
            ),
        )

        val cleared = setAutoSectionDiaMm(start, 0f, 400f, 0f)

        assertEquals(listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 160f)), cleared.autoDiaOverrides)
        assertEquals(cleared.autoDiaOverrides, setAutoSectionDiaMm(start, 0f, 400f, -5f).autoDiaOverrides)
    }

    @Test
    fun `clearing an untouched span is a no-op`() {
        val start = ShaftSpec(autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 160f)))

        assertSame(start, setAutoSectionDiaMm(start, 0f, 400f, 0f))
    }

    @Test
    fun `the typed diameter is stored verbatim`() {
        // Golden rule: a typed measurement is never rounded, snapped, or "helpfully" adjusted.
        val result = setAutoSectionDiaMm(ShaftSpec(), 0f, 400f, 101.6001f)

        assertEquals(101.6001f, result.autoDiaOverrides.single().diaMm)
    }

    @Test
    fun `the anchor interval is half-open at the fwd end`() {
        // A span's fwd boundary is the next span's start; an anchor sitting exactly on it
        // belongs to the fwd span, so setting the aft span must leave it alone.
        val start = ShaftSpec(autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 400f, diaMm = 160f)))

        val result = setAutoSectionDiaMm(start, 0f, 400f, 150f)

        assertEquals(2, result.autoDiaOverrides.size)
        assertTrue(result.autoDiaOverrides.contains(AutoDiaOverride(anchorMm = 400f, diaMm = 160f)))
    }
}
