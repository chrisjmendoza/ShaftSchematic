package com.android.shaftschematic.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Why both of the app's DataStores are built with a corruption handler.
 *
 * There are two, and each is a single point of failure for the whole app:
 *  - `settingsDataStore` (`data/SettingsStore.kt`) — units, theme, the whole drawing look, the
 *    backup-mirror folder, and the migration/seeding flags read during startup.
 *  - the draft ring's store (`data/AutosaveManager.kt`) — rewritten every 1.5 s of editing, so
 *    it is the file most likely to be caught mid-write, and `loadDrafts` runs from the
 *    ViewModel's `init`, inside a `viewModelScope.launch` with no handler above it.
 *
 * A truncated or garbage `.preferences_pb` is not exotic: it is what a tablet yanked off power
 * mid-write leaves behind, and a shop-floor device gets yanked off power. Without a handler
 * DataStore answers every read by throwing [CorruptionException], so the failure is not
 * "settings went back to default" — it is the app crashing on launch, permanently, with no way
 * back short of clearing app data (which also takes the drawings).
 *
 * This drives the two behaviours against a throwaway file rather than either real store: those
 * are process-wide singletons keyed by file, so a test that corrupted one would leak into every
 * other test in the JVM worker (see `SettingsStoreDialogUnitConverterTest`). The library
 * behaviour is the thing worth pinning; the app just has to opt into it, which both delegates
 * now do.
 */
class DataStoreCorruptionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val probe = booleanPreferencesKey("probe")

    /** Not a valid preferences proto — the shape a half-finished write leaves on disk. */
    private fun corruptFile(): File =
        File(tmp.newFolder(), "settings.preferences_pb").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(64) { 0xFF.toByte() })
        }

    @Test
    fun `an unguarded store throws on every read of a corrupt file`() = runBlocking {
        val file = corruptFile()
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }

        val thrown = runCatching { store.data.first() }.exceptionOrNull()

        // The exact failure a settings read would hand the Compose collector — and, on the
        // startup flags, the main thread.
        assertEquals(CorruptionException::class.java, thrown?.javaClass)
    }

    @Test
    fun `the handler both stores install degrades the same read to defaults`() = runBlocking {
        val file = corruptFile()
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { file }

        // Reads answer with defaults instead of throwing: the presets are lost, the app is not.
        assertNull(store.data.first()[probe])

        // And the replaced file is writable again, so the next preference the user sets sticks.
        store.updateData { it.toMutablePreferences().apply { set(probe, true) } }
        assertEquals(true, store.data.first()[probe])
    }
}
