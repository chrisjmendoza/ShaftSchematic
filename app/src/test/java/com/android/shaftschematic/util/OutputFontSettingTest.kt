package com.android.shaftschematic.util

import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.settings.DrawingProfile
import com.android.shaftschematic.settings.PdfPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Settings → Drawing → "Output font" chips, from the pref down to the ink.
 *
 * The composers do not take a typeface parameter — they build their root text paint from
 * [OutputTypography.active], a process-wide mirror of `PdfPrefs.outputFont` written by
 * `SettingsStore.updatePdfPrefs`. That indirection is what these tests cover: a pref change that
 * never reaches the mirror is a dead picker, and it would look exactly like a working one in the
 * Settings UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutputFontSettingTest {

    /**
     * The mirror is process-wide, so a test that moves it must put it back — otherwise the next
     * test class in this JVM draws in whatever face this one left behind.
     */
    @After
    fun restoreShippedFont() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = PdfPrefs().outputFont) }
    }

    /** One shipped face, agreed on by the pref, the mirror's baseline and the tolerant decode. */
    @Test
    fun `the shipped default is one value everywhere`() {
        assertSame(OutputFont.Default, PdfPrefs().outputFont)
        assertSame(OutputFont.Default, OutputFont.fromName(null))
        assertEquals(OutputFont.Default.typeface(), OutputTypography.active)
    }

    @Test
    fun `a pref change reaches the mirror`() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.SERIF) }
        assertEquals(OutputFont.SERIF.typeface(), OutputTypography.active)

        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.MONOSPACE) }
        assertEquals(OutputFont.MONOSPACE.typeface(), OutputTypography.active)

        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.STANDARD) }
        assertEquals(OutputFont.STANDARD.typeface(), OutputTypography.active)
    }

    /** Every face must resolve to something drawable, even where the device lacks the family. */
    @Test
    fun `every face resolves to a usable typeface`() {
        OutputFont.entries.forEach { font ->
            SettingsStore.updatePdfPrefs { it.copy(outputFont = font) }
            assertEquals("$font did not reach the mirror", font.typeface(), OutputTypography.active)
        }
    }

    /** Changing an unrelated PDF pref must not disturb the face. */
    @Test
    fun `an unrelated pref edit leaves the active face alone`() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.SERIF) }
        SettingsStore.updatePdfPrefs { it.copy(arrowSizePt = 5f) }
        assertEquals(OutputFont.SERIF.typeface(), OutputTypography.active)
    }

    /**
     * "Restore Drawing defaults" applies a fresh [DrawingProfile], so the face rides that one
     * path back to Standard along with the rest of the look.
     */
    @Test
    fun `applying a default drawing profile restores the shipped face`() {
        SettingsStore.updatePdfPrefs { it.copy(outputFont = OutputFont.MONOSPACE) }
        assertEquals(OutputFont.MONOSPACE.typeface(), OutputTypography.active)

        val restored = DrawingProfile().toPdfPrefs()
        SettingsStore.updatePdfPrefs { it.copy(outputFont = restored.outputFont) }

        assertSame(OutputFont.STANDARD, restored.outputFont)
        assertEquals(OutputFont.STANDARD.typeface(), OutputTypography.active)
    }

    /** A profile captures the face and hands it back unchanged. */
    @Test
    fun `a drawing profile round-trips the face`() {
        val captured = DrawingProfile.of(
            PdfPrefs(outputFont = OutputFont.CONDENSED),
            lineThicknessScale = 1f,
        )

        assertEquals("CONDENSED", captured.outputFont)
        assertSame(OutputFont.CONDENSED, captured.toPdfPrefs().outputFont)
    }
}
