// file: app/src/main/java/com/android/shaftschematic/ShaftSchematicApp.kt
package com.android.shaftschematic

import android.app.Application
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.CrashReporter
import java.io.File

/**
 * ShaftSchematicApp
 *
 * Purpose
 * Process-start wiring, and nothing else: the diagnostic log file, the crash-reporting probe,
 * and the uncaught-exception handler. No business logic, no file I/O beyond opening the log
 * directory — everything a screen needs still comes from the ViewModel.
 *
 * Order matters here. Firebase initializes from its own auto-init ContentProvider, which runs
 * *before* `onCreate`; installing our uncaught handler here therefore captures Crashlytics'
 * handler as the one to delegate to, and both reports survive. Installing earlier would put ours
 * underneath theirs and the local crash line would never be written.
 */
class ShaftSchematicApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppLog.init(File(filesDir, "logs"))
        CrashReporter.init(this)

        AppLog.i(TAG, "app start v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})")
        AppLog.i(TAG, "crash reporting ${if (CrashReporter.isActive) "active" else "inactive"}")

        AppLog.installCrashHandler()
    }

    private companion object {
        const val TAG = "App"
    }
}
