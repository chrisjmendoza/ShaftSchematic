package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the wear preview's render-loop rule: every input the `snapshotFlow` block builds must be
 * read from STATE inside the block. The block is created once (`LaunchedEffect(Unit)`), so a
 * plain `val` derived in the composition and captured there is the first composition's value
 * forever — every "Trace depth exaggeration" commit re-rendered the page at the stale depth
 * (on-device report). Source scan, the `SheetTextPaintSourceTest` posture.
 */
class WearRenderInputsSourceTest {

    private val wearRoute: String by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: error("Missing system property: user.dir"))
        val rel = "app/src/main/java/com/android/shaftschematic/ui/screen/WearRoute.kt"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        File(dir ?: error("$rel not found above ${System.getProperty("user.dir")}"), rel).readText()
    }

    private fun renderInputsBlock(): String {
        // The code line, not the KDoc that quotes `snapshotFlow { … }.conflate()` above it.
        val start = Regex("""(?m)^\s*snapshotFlow \{\s*$""").find(wearRoute)?.range?.first ?: -1
        assertTrue("WearRoute's render loop should build its inputs in a snapshotFlow block", start >= 0)
        val end = wearRoute.indexOf("}.conflate()", start)
        assertTrue("the snapshotFlow block should end with .conflate()", end > start)
        return wearRoute.substring(start, end)
    }

    @Test
    fun `the trace depth is resolved from state inside the render loop`() {
        val block = renderInputsBlock()
        assertFalse(
            "the render loop must not capture the composition's traceDepthFrac val",
            Regex("""traceDepthFrac\s*=\s*traceDepthFrac\b""").containsMatchIn(block),
        )
        assertTrue(
            "the render loop resolves the depth from wearRecord + the Settings default",
            block.contains("effectiveWearTraceDepthFrac(wearRecord.traceDepthFrac, wearTraceDefault)"),
        )
    }
}
