package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The bundled User Manual (`assets/manual/`, written by `docs/manual/build.py --web`) against
 * its sources and against Help.
 *
 * The manual is built by hand, so the failure this guards is a chapter edited and the app
 * still shipping the old page. `source.sha256` is the fingerprint `build.py` wrote; this test
 * recomputes it from `docs/manual/` the way `source_fingerprint()` does — sorted relative
 * paths, each path, byte length and bytes, text read with LF endings — and a mismatch means
 * "run `python docs/manual/build.py --web`".
 */
class ManualAssetsTest {

    private val repoRoot: File = run {
        var dir: File? = File(System.getProperty("user.dir") ?: error("Missing system property: user.dir"))
        while (dir != null && !File(dir, "docs/manual/build.py").isFile) dir = dir.parentFile
        dir ?: error("docs/manual/build.py not found above ${System.getProperty("user.dir")}")
    }
    private val manualDir = File(repoRoot, "docs/manual")
    private val assetDir = File(repoRoot, "app/src/main/assets/manual")
    private val page: String by lazy { File(assetDir, "index.html").readText() }
    private val ids: Set<String> by lazy {
        Regex("""\sid="([^"]+)"""").findAll(page).map { it.groupValues[1] }.toSet()
    }

    private fun sourceFingerprint(): String {
        val chapters = File(manualDir, "src").listFiles { f -> f.name.matches(Regex("""\d\d-.*\.html""")) }.orEmpty()
        val figures = File(manualDir, "figures").listFiles { f -> f.name.endsWith(".png") }.orEmpty()
        val files = (chapters.toList() + figures + listOf("style.css", "web.css", "build.py").map { File(manualDir, it) })
            .map { it.relativeTo(manualDir).invariantSeparatorsPath to it }
            .sortedBy { it.first }
        val md = MessageDigest.getInstance("SHA-256")
        files.forEach { (rel, file) ->
            var bytes = file.readBytes()
            if (file.extension in setOf("html", "css", "py")) {
                bytes = String(bytes, Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
            }
            md.update("$rel\n".toByteArray(Charsets.UTF_8))
            md.update("${bytes.size}\n".toByteArray(Charsets.US_ASCII))
            md.update(bytes)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `bundled manual was built from the current sources`() {
        val recorded = File(assetDir, "source.sha256").readText().trim()
        assertEquals(
            "The manual's sources changed since the in-app copy was built — run " +
                "`python docs/manual/build.py --web` and commit app/src/main/assets/manual/.",
            sourceFingerprint(),
            recorded,
        )
    }

    @Test
    fun `every Help topic's manual link names a section the manual has`() {
        val anchors = helpSections.flatMap { it.topics }.mapNotNull { t -> t.manualAnchor?.let { t.title to it } }
        assertTrue("Help topics should link into the manual", anchors.isNotEmpty())
        val broken = anchors.filter { (_, anchor) -> anchor !in ids }
        assertTrue("Help topics linking to missing manual sections: $broken", broken.isEmpty())
    }

    @Test
    fun `the Contents button's target exists`() {
        assertTrue(MANUAL_CONTENTS_ANCHOR in ids)
    }

    @Test
    fun `every cross-reference in the manual lands on a section`() {
        val targets = Regex("""href="#([^"]+)"""").findAll(page).map { it.groupValues[1] }.toSet()
        val broken = targets - ids
        assertTrue("Cross-references with no target: $broken", broken.isEmpty())
    }

    @Test
    fun `every image and stylesheet the page loads is bundled`() {
        val refs = Regex("""(?:src|href)="([^"#][^"]*)"""").findAll(page).map { it.groupValues[1] }.toList()
        assertTrue(refs.any { it.startsWith("figures/") })
        refs.forEach { ref ->
            assertFalse("The manual must load nothing from outside the app: $ref", ref.contains("://"))
            assertTrue("Missing bundled file: $ref", File(assetDir, ref).isFile)
        }
    }

    @Test
    fun `manual urls stay inside the asset folder`() {
        assertEquals(MANUAL_ASSET_URL, manualUrl(null))
        assertEquals(MANUAL_ASSET_URL, manualUrl(" "))
        assertEquals("$MANUAL_ASSET_URL#editor-oal", manualUrl("editor-oal"))
        assertTrue(isManualUrl(manualUrl("editor-oal")))
        assertTrue(isManualUrl("file:///android_asset/manual/figures/wear.webp"))
        assertFalse(isManualUrl("https://example.com/"))
        assertFalse(isManualUrl("file:///android_asset/templates/x.json"))
    }

    @Test
    fun `find summary reads as a count`() {
        assertEquals("", manualFindSummary(" ", 0, 0))
        assertEquals("No matches", manualFindSummary("bolt", 0, 0))
        assertEquals("1 of 12", manualFindSummary("bolt", 0, 12))
        assertEquals("12 of 12", manualFindSummary("bolt", 11, 12))
    }
}
