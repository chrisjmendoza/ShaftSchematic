package com.android.shaftschematic.util

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * AppLog — the always-on breadcrumb file.
 *
 * Purpose
 * The app ships to outside testers, whose devices are the only place a failure is visible. A
 * tester can say "the export did nothing"; only the file this writes can say which branch it
 * took. It is therefore **not** gated by Developer Options (that is [VerboseLog]'s job, and its
 * logcat lines are unreachable on someone else's phone) — evidence has to already be on disk by
 * the time anybody thinks to ask for it. Settings → Data → "Share diagnostic logs" mails these
 * files out.
 *
 * PRIVACY RULE — breadcrumbs record **events**, never content.
 * A line may say what happened (an export started, a save failed, an import threw) and carry an
 * exception. It may **never** carry a document field value, a geometry number, a diameter, a
 * reading, or any other thing the user typed into a drawing. Document NAMES are allowed: the
 * person sharing the log owns them, and a failure is unreadable without knowing which document
 * it happened to. When in doubt, log the event and leave the value out.
 *
 * Ring
 * Lines append to `log.0.txt`; once a line would push it past [DEFAULT_MAX_FILE_BYTES], the file
 * becomes `log.1.txt` (overwriting whatever was there) and a fresh `log.0.txt` starts. Two files,
 * so the cap on disk is twice that — bounded without a cleanup task, and the older half survives
 * long enough to hold the run *before* the one that broke.
 *
 * Failure posture
 * Every path swallows its own errors and every call before [init] is a no-op. Logging exists to
 * explain a failure; it may never become one.
 */
object AppLog {

    /** The live file. Rotation renames it to [PREVIOUS_NAME]. */
    const val CURRENT_NAME: String = "log.0.txt"

    /** The rotated-out file — one generation of history, overwritten by the next rotation. */
    const val PREVIOUS_NAME: String = "log.1.txt"

    /** Per-file cap; total on-disk cost is twice this, since the ring keeps two files. */
    const val DEFAULT_MAX_FILE_BYTES: Long = 256L * 1024L

    @Volatile private var logDir: File? = null
    @Volatile private var maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES
    @Volatile private var writer: ExecutorService? = null

    /**
     * Points the logger at [logDir], creating it if needed. Called once from the Application.
     * A second call re-points it (the tests' use); calls made before the first one are dropped.
     *
     * [maxFileBytes] is injectable purely so a test can drive rotation without writing a
     * quarter-megabyte of breadcrumbs.
     */
    fun init(logDir: File, maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES) {
        runCatching { logDir.mkdirs() }
        this.maxFileBytes = maxFileBytes.coerceAtLeast(1L)
        this.logDir = logDir
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor { r ->
                Thread(r, "AppLog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
            }
        }
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun w(tag: String, msg: String) = write("W", tag, msg, null)

    fun e(tag: String, msg: String) = write("E", tag, msg, null)

    /** Error breadcrumb with the full stack trace appended under the message. */
    fun e(tag: String, msg: String, t: Throwable?) = write("E", tag, msg, t)

    /**
     * The log files that currently hold something, newest first — what "Share diagnostic logs"
     * attaches. Empty before [init], and empty on a device that has produced no breadcrumbs.
     */
    fun logFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return listOf(File(dir, CURRENT_NAME), File(dir, PREVIOUS_NAME))
            .filter { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) }
    }

    /**
     * Blocks until every queued line has been written, up to [timeoutMs].
     *
     * The crash handler needs this: the process is about to die, and a breadcrumb still sitting
     * on the writer thread's queue explains nothing.
     */
    fun flushBlocking(timeoutMs: Long = 1_000L) {
        val exec = writer ?: return
        val done = CountDownLatch(1)
        val queued = runCatching { exec.execute { done.countDown() } }.isSuccess
        if (!queued) return
        runCatching { done.await(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    /**
     * Installs the process's uncaught-exception handler: write the crash to the file, flush it,
     * then hand the throwable to whatever handler was already installed.
     *
     * **Delegation is the point.** Crashlytics installs its own handler during Firebase's
     * auto-init; replacing it without calling through would trade every remote crash report for
     * one local line. Install after that auto-init has run — i.e. from `Application.onCreate`.
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // Idempotent: wrapping our own handler again would only stack duplicate lines in front
        // of the same delegate.
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(crashHandler(previous))
    }

    /** The handler [installCrashHandler] installs, exposed so a test can drive it directly. */
    internal fun crashHandler(
        previous: Thread.UncaughtExceptionHandler?,
    ): Thread.UncaughtExceptionHandler = CrashHandler(previous)

    private class CrashHandler(
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, t: Throwable) {
            runCatching {
                write("E", TAG_CRASH, "uncaught on thread '${thread.name}'", t)
                flushBlocking()
            }
            previous?.uncaughtException(thread, t)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Pure seams
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Whether appending [incomingBytes] to a file already holding [currentBytes] must rotate
     * first. An empty file never rotates — a single line larger than the cap has to land
     * somewhere, and rotating it into an empty file would just lose it.
     */
    internal fun shouldRotate(currentBytes: Long, incomingBytes: Long, maxBytes: Long): Boolean =
        currentBytes > 0L && currentBytes + incomingBytes > maxBytes

    /** One line: local timestamp, level, tag, message. */
    internal fun formatLine(atMs: Long, level: String, tag: String, msg: String): String =
        "${TIMESTAMP.get()!!.format(Date(atMs))} $level/$tag: $msg"

    /** The stack trace as it is appended under an error line. */
    internal fun throwableText(t: Throwable): String = StringWriter().also { sw ->
        PrintWriter(sw).use { t.printStackTrace(it) }
    }.toString().trimEnd()

    // ────────────────────────────────────────────────────────────────────────────
    // Internals
    // ────────────────────────────────────────────────────────────────────────────

    private const val TAG_CRASH = "Crash"

    /**
     * Drops the sink, putting the logger back in its pre-[init] no-op state. Test seam: the
     * object is process-wide, so the "before init" contract is otherwise unreachable once any
     * test in the JVM has pointed it at a directory.
     */
    internal fun resetForTest() {
        logDir = null
    }

    private val TIMESTAMP = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        mirrorToLogcat(level, tag, msg, t)

        val exec = writer ?: return
        val text = buildString {
            append(formatLine(System.currentTimeMillis(), level, tag, msg))
            if (t != null) {
                append('\n')
                append(throwableText(t))
            }
            append('\n')
        }
        runCatching { exec.execute { appendLine(text) } }
    }

    /** Cheap local convenience. Under a plain JVM test `android.util.Log` throws; ignore it. */
    private fun mirrorToLogcat(level: String, tag: String, msg: String, t: Throwable?) {
        runCatching {
            when (level) {
                "W" -> if (t == null) Log.w(tag, msg) else Log.w(tag, msg, t)
                "E" -> if (t == null) Log.e(tag, msg) else Log.e(tag, msg, t)
                else -> if (t == null) Log.i(tag, msg) else Log.i(tag, msg, t)
            }
        }
    }

    /** Runs on the writer thread only. Rotates first when the line would overflow the cap. */
    private fun appendLine(text: String) {
        val dir = logDir ?: return
        runCatching {
            val current = File(dir, CURRENT_NAME)
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (shouldRotate(current.length(), bytes.size.toLong(), maxFileBytes)) {
                rotate(dir, current)
            }
            current.appendBytes(bytes)
        }
    }

    private fun rotate(dir: File, current: File) {
        val previous = File(dir, PREVIOUS_NAME)
        runCatching { if (previous.exists()) previous.delete() }
        if (!runCatching { current.renameTo(previous) }.getOrDefault(false)) {
            // Rename refused (unusual): drop the live file rather than let it grow unbounded.
            runCatching { current.delete() }
        }
    }
}
