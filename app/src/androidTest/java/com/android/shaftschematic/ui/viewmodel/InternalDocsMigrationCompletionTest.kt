package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.io.InternalStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InternalDocsMigrationCompletionTest {

    @Test
    fun migration_withSkip_stillMarksComplete() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

        // Force migration to run.
        SettingsStore.setInternalDocsMigratedToShaft(app, false)

        val originalMigrator = ShaftViewModel.migrateLegacyInternalDocs
        try {
            // Simulate a legitimate skip without throwing.
            ShaftViewModel.migrateLegacyInternalDocs = {
                InternalStorage.MigrationReport(migratedCount = 0, skippedCount = 1)
            }

            // Trigger init.
            ShaftViewModel(app)

            // Expect flag to become true even with skips.
            withTimeout(5_000) {
                while (!SettingsStore.internalDocsMigratedToShaft(app)) {
                    delay(50)
                }
            }
        } finally {
            ShaftViewModel.migrateLegacyInternalDocs = originalMigrator
            // Leave it marked complete to avoid surprises across tests.
            SettingsStore.setInternalDocsMigratedToShaft(app, true)
        }
    }
}
