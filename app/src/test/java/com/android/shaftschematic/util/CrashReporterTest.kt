package com.android.shaftschematic.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Firebase is optional configuration, and this is the build that does not have it: no
 * `google-services.json` ships in the repo, so `FirebaseApp` never initializes and every
 * [CrashReporter] call has to be a silent no-op. A throw here would mean the breadcrumb sites —
 * which report non-fatals unconditionally — take down exactly the paths they were added to
 * explain.
 *
 * Also pins the FileProvider root behind "Share diagnostic logs": the log directory lives under
 * `filesDir`, which `res/xml/file_paths.xml` already exposes as "internal" (`files-path` at
 * `"."`). A file outside that root is a share button that always fails. The `getUriForFile`
 * round-trip itself is deliberately not asserted — under Robolectric the provider resolves no
 * roots at all, for any path, so it would only pin the harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReporterTest {

    @Test
    fun `no firebase config leaves reporting inactive and every call harmless`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        CrashReporter.init(ctx)

        assertFalse("no google-services.json in this build", CrashReporter.isActive)
        CrashReporter.log("export start")
        CrashReporter.recordNonFatal(IllegalStateException("kapow"))
    }

    @Test
    fun `the log files sit under the declared FileProvider root`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        AppLog.init(File(ctx.filesDir, "logs"))
        AppLog.i("Test", "breadcrumb")
        AppLog.flushBlocking()

        val shared = AppLog.logFiles()
        assertTrue("there is something to share", shared.isNotEmpty())
        // `res/xml/file_paths.xml` exposes files-path "." as "internal"; every log file has to
        // land under it or the share intent hands the mail app a URI it cannot open.
        val root = ctx.filesDir.canonicalPath
        assertTrue(shared.toString(), shared.all { it.canonicalPath.startsWith(root) })

        AppLog.resetForTest()
    }
}
