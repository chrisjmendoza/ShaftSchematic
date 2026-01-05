package com.android.shaftschematic.data

import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStorePreviewColorsTest {

    @Test
    fun `new format uses preset and custom role`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = "CUSTOM",
            customRoleRaw = "TERTIARY",
            legacyRoleRaw = "OUTLINE",
            defaultPreset = PreviewColorPreset.STEEL,
            defaultCustomRole = PreviewColorRole.MONOCHROME
        )

        assertEquals(PreviewColorPreset.CUSTOM, s.preset)
        assertEquals(PreviewColorRole.TERTIARY, s.customRole)
    }

    @Test
    fun `new format ignores legacy role`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = "STEEL",
            customRoleRaw = "ERROR",
            legacyRoleRaw = "TERTIARY",
            defaultPreset = PreviewColorPreset.TRANSPARENT
        )

        assertEquals(PreviewColorPreset.STEEL, s.preset)
        // Even for non-CUSTOM presets we persist a role; it's simply not used for resolve().
        assertEquals(PreviewColorRole.ERROR, s.customRole)
    }

    @Test
    fun `invalid preset string falls back safely`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = "NOT_A_PRESET",
            customRoleRaw = "SECONDARY",
            legacyRoleRaw = null,
            defaultPreset = PreviewColorPreset.STAINLESS
        )

        assertEquals(PreviewColorPreset.CUSTOM, s.preset)
        assertEquals(PreviewColorRole.SECONDARY, s.customRole)
    }

    @Test
    fun `legacy role SURFACE_VARIANT maps to STAINLESS`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = null,
            customRoleRaw = null,
            legacyRoleRaw = "SURFACE_VARIANT",
            defaultPreset = PreviewColorPreset.STEEL
        )

        assertEquals(PreviewColorPreset.STAINLESS, s.preset)
    }

    @Test
    fun `legacy role OUTLINE maps to STEEL`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = null,
            customRoleRaw = null,
            legacyRoleRaw = "OUTLINE",
            defaultPreset = PreviewColorPreset.TRANSPARENT
        )

        assertEquals(PreviewColorPreset.STEEL, s.preset)
    }

    @Test
    fun `legacy role TERTIARY maps to BRONZE`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = null,
            customRoleRaw = null,
            legacyRoleRaw = "TERTIARY",
            defaultPreset = PreviewColorPreset.STEEL
        )

        assertEquals(PreviewColorPreset.BRONZE, s.preset)
    }

    @Test
    fun `legacy role TRANSPARENT maps to TRANSPARENT`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = null,
            customRoleRaw = null,
            legacyRoleRaw = "TRANSPARENT",
            defaultPreset = PreviewColorPreset.STEEL
        )

        assertEquals(PreviewColorPreset.TRANSPARENT, s.preset)
    }

    @Test
    fun `legacy invalid role falls back based on default preset`() {
        val s = SettingsStore.parsePreviewColorSettingForTest(
            presetRaw = null,
            customRoleRaw = null,
            legacyRoleRaw = "NOPE",
            defaultPreset = PreviewColorPreset.STEEL
        )

        assertEquals(PreviewColorPreset.STEEL, s.preset)
    }
}
