package com.android.shaftschematic.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Atomic-save contract for [InternalStorage.save]:
 *  - content lands in the target file,
 *  - overwriting keeps the previous version as `name.bak`,
 *  - no `.tmp` residue after a successful save,
 *  - `.tmp` / `.bak` siblings never appear in [InternalStorage.list].
 */
class InternalStorageAtomicSaveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `save writes content to target`() {
        val dir = tmp.newFolder("shafts")
        InternalStorage.save(dir, "job1.shaft", "v1-content")

        assertEquals("v1-content", dir.resolve("job1.shaft").readText())
        assertFalse("no temp residue", dir.resolve("job1.shaft.tmp").exists())
    }

    @Test
    fun `overwrite keeps previous version as bak`() {
        val dir = tmp.newFolder("shafts")
        InternalStorage.save(dir, "job1.shaft", "v1-content")
        InternalStorage.save(dir, "job1.shaft", "v2-content")

        assertEquals("v2-content", dir.resolve("job1.shaft").readText())
        assertTrue("previous version preserved", dir.resolve("job1.shaft.bak").exists())
        assertEquals("v1-content", dir.resolve("job1.shaft.bak").readText())
    }

    @Test
    fun `second overwrite rolls the bak forward`() {
        val dir = tmp.newFolder("shafts")
        InternalStorage.save(dir, "job1.shaft", "v1")
        InternalStorage.save(dir, "job1.shaft", "v2")
        InternalStorage.save(dir, "job1.shaft", "v3")

        assertEquals("v3", dir.resolve("job1.shaft").readText())
        assertEquals("v2", dir.resolve("job1.shaft.bak").readText())
    }

    @Test
    fun `tmp and bak siblings are invisible to list`() {
        val dir = tmp.newFolder("shafts")
        InternalStorage.save(dir, "job1.shaft", "v1")
        InternalStorage.save(dir, "job1.shaft", "v2")
        // Simulate a crash that left a stale temp file behind.
        dir.resolve("job2.shaft.tmp").writeText("partial")

        val listed = InternalStorage.list(dir)

        assertEquals(listOf("job1.shaft"), listed)
    }
}
