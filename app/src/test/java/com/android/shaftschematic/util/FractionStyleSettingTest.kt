package com.android.shaftschematic.util

import android.graphics.Paint
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.settings.PdfPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Settings → Drawing → "Fractions" toggle, from the pref down to the ink.
 *
 * The draw sites do not take a style parameter — they read [FractionTypography.active], a
 * process-wide mirror of `PdfPrefs.fractionStyle` written by `SettingsStore.updatePdfPrefs`.
 * That indirection is what these tests cover: a pref change that never reaches the mirror is a
 * dead toggle, and it would look exactly like a working one in the Settings UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FractionStyleSettingTest {

    /**
     * The mirror is process-wide, so a test that moves it must put it back — otherwise the
     * next test class in this JVM draws in whatever style this one left behind.
     */
    @After
    fun restoreShippedStyle() {
        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = PdfPrefs().fractionStyle) }
    }

    /**
     * One shipped construction, agreed on by the pref, the renderer's baseline and the tolerant
     * decode. Disagreement here shows up as a fresh install drawing one style while an unreadable
     * stored value falls back to another.
     */
    @Test
    fun `the shipped default is one value everywhere`() {
        assertEquals(FractionStyle.Default, PdfPrefs().fractionStyle)
        assertEquals(FractionStyle.Default, FractionTextStyle.Default.style)
        assertEquals(FractionStyle.Default, FractionStyle.fromName(null))
        assertEquals(FractionStyle.Default, FractionTypography.active.style)
    }

    @Test
    fun `updating the pref moves the renderer's active style`() {
        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.DIAGONAL) }
        assertEquals(FractionStyle.DIAGONAL, FractionTypography.active.style)

        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.INLINE) }
        assertEquals(FractionStyle.INLINE, FractionTypography.active.style)

        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.STACKED) }
        assertEquals(FractionStyle.STACKED, FractionTypography.active.style)
    }

    /**
     * A style switch installs that style's OWN preset, spacing included. Spacing is per-style
     * because the two constructions present different ink at the edges of their box; copying one
     * preset's numbers onto the other style is the regression this guards.
     */
    @Test
    fun `switching style installs that style's tuned preset`() {
        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.DIAGONAL) }
        assertEquals(FractionTextStyle.Diagonal, FractionTypography.active)

        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.STACKED) }
        assertEquals(FractionTextStyle.Stacked, FractionTypography.active)

        // The presets really do differ in spacing, and agree on the digit size.
        assertNotEquals(FractionTextStyle.Stacked.gapFrac, FractionTextStyle.Diagonal.gapFrac)
        assertEquals(
            FractionTextStyle.Stacked.digitScale,
            FractionTextStyle.Diagonal.digitScale,
            0f,
        )
    }

    /** Every style must be reachable as a preset, and carry its own enum value. */
    @Test
    fun `forStyle returns a preset matching the requested style`() {
        FractionStyle.entries.forEach { style ->
            assertEquals(style, FractionTextStyle.forStyle(style).style)
        }
    }

    /** Changing an unrelated PDF pref must not disturb the fraction style. */
    @Test
    fun `an unrelated pref edit leaves the active style alone`() {
        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.DIAGONAL) }
        SettingsStore.updatePdfPrefs { it.copy(arrowSizePt = 5f) }
        assertEquals(FractionStyle.DIAGONAL, FractionTypography.active.style)
    }

    /** The toggle is only real if an unqualified draw-site measure follows it. */
    @Test
    fun `the default measure argument follows the toggle`() {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f }

        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.STACKED) }
        val stackedWidth = p.measureRichText("2 1/2\"")

        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.DIAGONAL) }
        val diagonalWidth = p.measureRichText("2 1/2\"")

        SettingsStore.updatePdfPrefs { it.copy(fractionStyle = FractionStyle.INLINE) }
        val inlineWidth = p.measureRichText("2 1/2\"")

        assertNotEquals(stackedWidth, diagonalWidth)
        assertEquals(p.measureText("2 1/2\""), inlineWidth, 0.001f)
    }

    @Test
    fun `a persisted name decodes tolerantly`() {
        assertSame(FractionStyle.STACKED, FractionStyle.fromName("STACKED"))
        assertSame(FractionStyle.DIAGONAL, FractionStyle.fromName("DIAGONAL"))
        assertSame(FractionStyle.INLINE, FractionStyle.fromName("INLINE"))
        // Missing or unrecognised (an older build, a hand-edited store) falls back to shipped.
        assertSame(FractionStyle.Default, FractionStyle.fromName(null))
        assertSame(FractionStyle.Default, FractionStyle.fromName(""))
        assertSame(FractionStyle.Default, FractionStyle.fromName("SLANTED"))
    }

    @Test
    fun `every style has a distinct chip label`() {
        val labels = FractionStyle.entries.map { it.uiLabel() }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals("Stacked", FractionStyle.STACKED.uiLabel())
        assertEquals("Diagonal", FractionStyle.DIAGONAL.uiLabel())
        assertEquals("Plain", FractionStyle.INLINE.uiLabel())
    }
}
