package com.android.shaftschematic.io

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.template.TemplateLinerCount
import com.android.shaftschematic.template.TemplateSizeBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Template store contract: `.shaft` files in their own directory, atomic saves (delegated to
 * [InternalStorage] so there is one implementation), and browser summaries derived on scan.
 */
class TemplateStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val inch = 25.4f

    private fun templateJson(
        linerOdIn: Float? = 6f,
        linerCount: Int = 1,
        jobNumber: String = "",
        customer: String = "",
    ): String {
        val spec = ShaftSpec(
            overallLengthMm = 2400f,
            bodies = listOf(Body(startFromAftMm = 0f, lengthMm = 2400f, diaMm = 4f * inch)),
            liners = if (linerOdIn == null) emptyList() else (0 until linerCount).map { i ->
                Liner(id = "l$i", startFromAftMm = 200f + i * 600f, lengthMm = 300f, odMm = linerOdIn * inch)
            },
        )
        return ShaftDocCodec.encodeV1(
            ShaftDocCodec.ShaftDocV1(spec = spec, jobNumber = jobNumber, customer = customer)
        )
    }

    @Test
    fun `save then load round-trips a template`() {
        val dir = tmp.newFolder("templates")
        val json = templateJson()
        TemplateStorage.save(dir, "6in one liner.shaft", json)

        assertEquals(listOf("6in one liner.shaft"), TemplateStorage.list(dir))
        assertEquals(json, dir.resolve("6in one liner.shaft").readText())
    }

    @Test
    fun `save is atomic and leaves no temp residue`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "t.shaft", templateJson())
        TemplateStorage.save(dir, "t.shaft", templateJson(linerOdIn = 8f))

        assertFalse(dir.resolve("t.shaft.tmp").exists())
        // The previous version survives as a recovery copy, invisible to the listing.
        assertTrue(dir.resolve("t.shaft.bak").exists())
        assertEquals(listOf("t.shaft"), TemplateStorage.list(dir))
    }

    @Test
    fun `delete removes a template`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "t.shaft", templateJson())

        assertTrue(TemplateStorage.delete(dir, "t.shaft"))
        assertTrue(TemplateStorage.list(dir).isEmpty())
    }

    @Test
    fun `rename never overwrites an existing template`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "a.shaft", templateJson())
        TemplateStorage.save(dir, "b.shaft", templateJson())

        assertEquals(TemplateStorage.RenameResult.TARGET_EXISTS, TemplateStorage.rename(dir, "a.shaft", "b.shaft"))
        assertEquals(TemplateStorage.RenameResult.OK, TemplateStorage.rename(dir, "a.shaft", "c.shaft"))
        assertEquals(listOf("b.shaft", "c.shaft"), TemplateStorage.list(dir))
    }

    @Test
    fun `rename collision is case-insensitive`() {
        // One browser row must never fork into two case-variant files on a case-sensitive FS.
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "Foo.shaft", templateJson())
        TemplateStorage.save(dir, "bar.shaft", templateJson())

        assertEquals(TemplateStorage.RenameResult.TARGET_EXISTS, TemplateStorage.rename(dir, "bar.shaft", "foo.shaft"))
    }

    @Test
    fun `rename of a missing template reports the source, not a collision`() {
        val dir = tmp.newFolder("templates")
        assertEquals(TemplateStorage.RenameResult.SOURCE_MISSING, TemplateStorage.rename(dir, "nope.shaft", "x.shaft"))
    }

    @Test
    fun `summaries derive buckets from the stored spec`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "six.shaft", templateJson(linerOdIn = 6f, linerCount = 2))
        TemplateStorage.save(dir, "straight.shaft", templateJson(linerOdIn = null))

        val summaries = TemplateStorage.summaries(dir).associateBy { it.filename }

        assertEquals(TemplateSizeBucket.Inches(6), summaries["six.shaft"]?.sizeBucket)
        assertEquals(TemplateLinerCount.TWO, summaries["six.shaft"]?.linerCount)
        assertEquals(TemplateSizeBucket.None, summaries["straight.shaft"]?.sizeBucket)
        assertEquals(TemplateLinerCount.NONE, summaries["straight.shaft"]?.linerCount)
    }

    @Test
    fun `summaries display the name without its extension`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "6in two liners.shaft", templateJson())

        assertEquals("6in two liners", TemplateStorage.summaries(dir).single().displayName)
    }

    @Test
    fun `an undecodable template is skipped, not fatal`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "good.shaft", templateJson())
        dir.resolve("broken.shaft").writeText("{ not json at all")

        // One bad file must not empty the browser.
        assertEquals(listOf("good.shaft"), TemplateStorage.summaries(dir).map { it.filename })
    }

    @Test
    fun `non-shaft files are ignored`() {
        val dir = tmp.newFolder("templates")
        TemplateStorage.save(dir, "t.shaft", templateJson())
        dir.resolve("notes.txt").writeText("hello")

        assertEquals(listOf("t.shaft"), TemplateStorage.list(dir))
    }
}
