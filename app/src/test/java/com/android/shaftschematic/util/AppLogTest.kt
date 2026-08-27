package com.android.shaftschematic.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The always-on breadcrumb file: what a tester's device can still tell us after the fact.
 *
 * Two properties carry the feature. The ring must stay bounded — a log that grows without limit
 * on somebody else's phone is a bug shipped to a stranger — and a call made before the
 * Application has run must be silent rather than fatal, because the whole point of this object
 * is to survive the failures it records.
 */
class AppLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * [AppLog] is process-wide, so a test that points it somewhere must put it back — otherwise
     * the next class in this JVM writes into a deleted temp folder.
     */
    @After
    fun detach() {
        AppLog.resetForTest()
    }

    private fun ringText(dir: File): String =
        listOf(AppLog.CURRENT_NAME, AppLog.PREVIOUS_NAME)
            .map { File(dir, it) }
            .filter { it.isFile }
            .joinToString("\n") { it.readText() }

    @Test
    fun `a call before init writes nothing and does not throw`() {
        AppLog.resetForTest()

        AppLog.i("Tag", "before init")
        AppLog.e("Tag", "before init", IllegalStateException("kapow"))
        AppLog.flushBlocking()

        assertTrue("nothing to share before init", AppLog.logFiles().isEmpty())
    }

    @Test
    fun `a line lands in the current file with its level and tag`() {
        val dir = tmp.newFolder("logs")
        AppLog.init(dir)

        AppLog.i("Export", "export start")
        AppLog.w("Export", "export slow")
        AppLog.flushBlocking()

        val text = File(dir, AppLog.CURRENT_NAME).readText()
        assertTrue(text, text.contains("I/Export: export start"))
        assertTrue(text, text.contains("W/Export: export slow"))
        assertEquals(1, AppLog.logFiles().size)
    }

    @Test
    fun `the ring keeps two files and drops the oldest lines`() {
        val dir = tmp.newFolder("logs")
        // Small enough that a handful of lines forces several rotations; the cap is injectable
        // exactly so this does not have to write half a megabyte.
        AppLog.init(dir, maxFileBytes = 200L)

        repeat(60) { AppLog.i("Ring", "line $it") }
        AppLog.flushBlocking()

        val files = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf(AppLog.CURRENT_NAME, AppLog.PREVIOUS_NAME), files)

        val text = ringText(dir)
        assertTrue("the newest line survives", text.contains("line 59"))
        assertFalse("the oldest line is gone", text.contains("line 0\n"))
        assertTrue("each file stays near the cap", dir.listFiles()!!.all { it.length() <= 400L })
    }

    @Test
    fun `an empty file never rotates`() {
        // A single line longer than the cap has to land somewhere; rotating an empty file
        // would simply lose it.
        assertFalse(AppLog.shouldRotate(currentBytes = 0L, incomingBytes = 900L, maxBytes = 200L))
        assertFalse(AppLog.shouldRotate(currentBytes = 100L, incomingBytes = 50L, maxBytes = 200L))
        assertTrue(AppLog.shouldRotate(currentBytes = 180L, incomingBytes = 50L, maxBytes = 200L))
    }

    @Test
    fun `an error line carries the exception class and a stack frame`() {
        val dir = tmp.newFolder("logs")
        AppLog.init(dir)

        AppLog.e("Export", "composer threw", IllegalStateException("kapow"))
        AppLog.flushBlocking()

        val text = File(dir, AppLog.CURRENT_NAME).readText()
        assertTrue(text, text.contains("E/Export: composer threw"))
        assertTrue(text, text.contains("java.lang.IllegalStateException: kapow"))
        assertTrue("the trace names the frame that threw", text.contains("AppLogTest"))
    }

    @Test
    fun `a formatted line leads with a sortable timestamp`() {
        val line = AppLog.formatLine(atMs = 0L, level = "I", tag = "Tag", msg = "message")

        assertTrue(line, line.endsWith(" I/Tag: message"))
        assertTrue(line, Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} ").containsMatchIn(line))
    }
}
