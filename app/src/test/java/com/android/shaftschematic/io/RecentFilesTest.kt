package com.android.shaftschematic.io

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecentFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = InternalStorage.dir(tmp.root)
    }

    @Test
    fun `empty dir returns empty list`() {
        assertTrue(InternalStorage.listWithMetadata(dir).isEmpty())
    }

    @Test
    fun `single shaft file returns correct name and timestamp`() {
        val f = File(dir, "test.shaft").apply { writeText("{}") }
        val results = InternalStorage.listWithMetadata(dir)
        assertEquals(1, results.size)
        assertEquals("test.shaft", results[0].first)
        assertEquals(f.lastModified(), results[0].second)
    }

    @Test
    fun `files sorted by lastModified descending`() {
        val older = File(dir, "older.shaft").apply {
            writeText("{}")
            setLastModified(1_000_000L)
        }
        val newer = File(dir, "newer.shaft").apply {
            writeText("{}")
            setLastModified(2_000_000L)
        }
        val results = InternalStorage.listWithMetadata(dir)
        assertEquals(2, results.size)
        assertEquals("newer.shaft", results[0].first)
        assertEquals(newer.lastModified(), results[0].second)
        assertEquals("older.shaft", results[1].first)
        assertEquals(older.lastModified(), results[1].second)
    }

    @Test
    fun `non-shaft files are excluded`() {
        File(dir, "readme.txt").writeText("ignore")
        File(dir, "data.bak").writeText("ignore")
        File(dir, "valid.shaft").writeText("{}")
        val results = InternalStorage.listWithMetadata(dir)
        assertEquals(1, results.size)
        assertEquals("valid.shaft", results[0].first)
    }

    @Test
    fun `legacy json files are included`() {
        File(dir, "old.json").writeText("{}")
        val results = InternalStorage.listWithMetadata(dir)
        assertEquals(1, results.size)
        assertEquals("old.json", results[0].first)
    }

    @Test
    fun `directories are not included`() {
        File(dir, "subdir.shaft").mkdirs()
        File(dir, "real.shaft").writeText("{}")
        val results = InternalStorage.listWithMetadata(dir)
        assertEquals(1, results.size)
        assertEquals("real.shaft", results[0].first)
    }

    @Test
    fun `multiple files sorted correctly`() {
        listOf("a.shaft" to 3_000L, "b.shaft" to 1_000L, "c.shaft" to 2_000L).forEach { (name, ts) ->
            File(dir, name).apply { writeText("{}"); setLastModified(ts) }
        }
        val results = InternalStorage.listWithMetadata(dir)
        assertEquals(listOf("a.shaft", "c.shaft", "b.shaft"), results.map { it.first })
    }
}
