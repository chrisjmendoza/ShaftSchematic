package com.android.shaftschematic.util

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The uncaught-exception handler wraps rather than replaces.
 *
 * Crashlytics installs its own default handler during Firebase's auto-init; a handler that
 * recorded the crash locally and stopped there would trade every remote crash report for one line
 * in a file nobody has asked for yet. Delegation is the whole contract, and it is invisible until
 * the day it matters — hence a test.
 */
class AppLogCrashHandlerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun detach() {
        AppLog.resetForTest()
    }

    @Test
    fun `the crash is written and then handed to the previous handler`() {
        val dir = tmp.newFolder("logs")
        AppLog.init(dir)

        var seenThrowable: Throwable? = null
        var seenThread: Thread? = null
        val previous = Thread.UncaughtExceptionHandler { thread, t ->
            seenThread = thread
            seenThrowable = t
        }

        val boom = IllegalStateException("kapow")
        AppLog.crashHandler(previous).uncaughtException(Thread.currentThread(), boom)

        assertSame("the previous handler still gets the throwable", boom, seenThrowable)
        assertSame(Thread.currentThread(), seenThread)

        // The handler flushes synchronously — a breadcrumb still queued when the process dies
        // explains nothing.
        val text = File(dir, AppLog.CURRENT_NAME).readText()
        assertTrue(text, text.contains("E/Crash: uncaught on thread"))
        assertTrue(text, text.contains("java.lang.IllegalStateException: kapow"))
    }

    @Test
    fun `no previous handler is not a second crash`() {
        val dir = tmp.newFolder("logs")
        AppLog.init(dir)

        // No delegate to call: the handler must still write its line and return quietly.
        AppLog.crashHandler(null).uncaughtException(Thread.currentThread(), IllegalStateException("kapow"))

        assertTrue(File(dir, AppLog.CURRENT_NAME).readText().contains("E/Crash: uncaught on thread"))
    }
}
