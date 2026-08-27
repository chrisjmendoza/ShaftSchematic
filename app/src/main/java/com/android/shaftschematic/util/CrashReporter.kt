package com.android.shaftschematic.util

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * CrashReporter — the one seam between this app and Crashlytics.
 *
 * Firebase is **optional configuration**: `app/google-services.json` is not in the repo, so a
 * local build has no Firebase project and `FirebaseApp` never initializes. Every call here is a
 * no-op in that state, which is what lets breadcrumb sites report a non-fatal unconditionally
 * without each one asking whether reporting exists.
 *
 * Nothing outside this file may talk to Firebase directly — a direct `FirebaseCrashlytics`
 * call would throw on exactly the builds that have no json.
 *
 * Collection stays on in debug builds: the debug variant is what outside testers install, so a
 * debug-only opt-out would silence the only crashes anybody is going to see.
 */
object CrashReporter {

    @Volatile private var active: Boolean = false

    /** True when Firebase initialized, i.e. a `google-services.json` shipped in this build. */
    val isActive: Boolean get() = active

    /**
     * Resolved once from the Application, because the answer cannot change afterwards: Firebase's
     * auto-init ContentProvider has either run by then or there was no config for it to read.
     */
    fun init(context: Context) {
        active = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }

    /** A breadcrumb attached to the next crash report. Dropped when reporting is inactive. */
    fun log(msg: String) {
        if (!active) return
        runCatching { FirebaseCrashlytics.getInstance().log(msg) }
    }

    /**
     * Reports a caught throwable the app recovered from — the failures that never reach the
     * uncaught handler because the code handled them, and so would otherwise be invisible.
     */
    fun recordNonFatal(t: Throwable) {
        if (!active) return
        runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
    }
}
