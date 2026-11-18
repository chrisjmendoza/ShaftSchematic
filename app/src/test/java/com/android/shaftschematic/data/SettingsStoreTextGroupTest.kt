package com.android.shaftschematic.data

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.android.shaftschematic.settings.PDF_TEXT_SCALE_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-group text sizes' store rules: a group key never written reads the schematic key it was
 * split from (so nothing changes on day one), and writing the schematic size first freezes every
 * unset group key at the schematic's OLD value (so the schematic never drags the others along).
 *
 * Pure — the two rules run on a bare preferences map, no DataStore.
 */
class SettingsStoreTextGroupTest {

    private val schematic = floatPreferencesKey("schematic")
    private val runout = floatPreferencesKey("runout")
    private val undercut = floatPreferencesKey("undercut")

    @Test
    fun `a missing group key reads the schematic key, then the default`() {
        assertEquals(PDF_TEXT_SCALE_DEFAULT, SettingsStore.groupTextScale(mutablePreferencesOf(), runout, schematic), 0f)
        val p = mutablePreferencesOf(schematic to 1.5f)
        assertEquals(1.5f, SettingsStore.groupTextScale(p, runout, schematic), 0f)
        p[runout] = 1.25f
        assertEquals(1.25f, SettingsStore.groupTextScale(p, runout, schematic), 0f)
    }

    @Test
    fun `a group key is clamped to the settable range`() {
        val p = mutablePreferencesOf(runout to 9f)
        assertEquals(1.75f, SettingsStore.groupTextScale(p, runout, schematic), 0f)
    }

    @Test
    fun `writing the schematic freezes unset group keys at its old value`() {
        val p = mutablePreferencesOf(schematic to 1.5f, undercut to 1.75f)
        SettingsStore.freezeTextGroupKeys(p, schematic, runout, undercut)
        p[schematic] = 1f
        assertEquals(1.5f, SettingsStore.groupTextScale(p, runout, schematic), 0f)
        // A group key already set keeps its own value.
        assertEquals(1.75f, SettingsStore.groupTextScale(p, undercut, schematic), 0f)
        assertEquals(1f, p[schematic])
    }

    @Test
    fun `a schematic key never written freezes the groups at the default it read as`() {
        val p = mutablePreferencesOf()
        SettingsStore.freezeTextGroupKeys(p, schematic, runout, undercut)
        p[schematic] = 1.5f
        assertEquals(PDF_TEXT_SCALE_DEFAULT, SettingsStore.groupTextScale(p, runout, schematic), 0f)
        assertEquals(PDF_TEXT_SCALE_DEFAULT, SettingsStore.groupTextScale(p, undercut, schematic), 0f)
    }
}
