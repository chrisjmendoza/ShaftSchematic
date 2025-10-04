package com.android.shaftschematic.testutil

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.android.shaftschematic.data.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Clears the Settings DataStore before each instrumented test run.
 * Use with:  @get:Rule val clearData = ClearDataStoreRule()
 */
class ClearDataStoreRule : ExternalResource() {
    private lateinit var appContext: Context

    override fun before() {
        // Instrumentation context is only available in androidTest
        appContext = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        runBlocking {
            appContext.settingsDataStore.edit { it.clear() }
        }
    }
}
