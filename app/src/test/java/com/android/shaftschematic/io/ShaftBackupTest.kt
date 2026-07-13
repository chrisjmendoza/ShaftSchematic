package com.android.shaftschematic.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ShaftBackupTest {

    private val manifest = ShaftBackup.Manifest(
        appVersion = "1.2.3",
        docFormatVersion = 1,
        createdEpochMs = 1_752_300_000_000L,
        documentCount = 2,
    )

    private fun zipBytes(docs: List<Pair<String, String>>): ByteArray =
        ByteArrayOutputStream().also { ShaftBackup.writeZip(it, docs, manifest) }.toByteArray()

    // Restore validation stand-in; production passes a ShaftDocCodec check.
    private val acceptJsonish: (String) -> Boolean = { it.trim().startsWith("{") }

    @Test
    fun `writeZip readZip round-trips docs and manifest`() {
        val docs = listOf(
            "Port Shaft.shaft" to """{"a":1}""",
            "Stbd Shaft.shaft" to """{"b":2}""",
        )
        val contents = ShaftBackup.readZip(ByteArrayInputStream(zipBytes(docs)))

        assertEquals(docs, contents.docs)
        assertEquals(manifest, contents.manifest)
    }

    @Test
    fun `readZip tolerates a foreign zip without manifest and ignores non-doc entries`() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("README.txt")); zip.write("hi".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("nested/dir/My Shaft.shaft")); zip.write("""{"x":1}""".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()

        val contents = ShaftBackup.readZip(ByteArrayInputStream(bytes))
        assertNull(contents.manifest)
        assertEquals(listOf("My Shaft.shaft" to """{"x":1}"""), contents.docs)
    }

    @Test
    fun `readZip reduces malicious entry paths to their basename`() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("../../evil.shaft")); zip.write("""{"x":1}""".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("..\\..\\evil2.shaft")); zip.write("""{"y":2}""".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()

        val names = ShaftBackup.readZip(ByteArrayInputStream(bytes)).docs.map { it.first }
        assertEquals(listOf("evil.shaft", "evil2.shaft"), names)
    }

    @Test
    fun `restoreInto restores fresh docs and skips identical ones`() {
        val dir = createTempDir(prefix = "shaftbackup_test_")
        try {
            val docs = listOf("A.shaft" to """{"a":1}""", "B.shaft" to """{"b":2}""")

            val first = ShaftBackup.restoreInto(dir, docs, acceptJsonish)
            assertEquals(2, first.restoredCount)
            assertEquals(0, first.renamedCount)

            // Restoring the same backup again is a clean no-op.
            val second = ShaftBackup.restoreInto(dir, docs, acceptJsonish)
            assertEquals(0, second.restoredCount)
            assertEquals(0, second.renamedCount)
            assertEquals(2, second.skippedIdenticalCount)
            assertEquals(setOf("A.shaft", "B.shaft"), dir.listFiles()!!.map { it.name }.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `restoreInto renames on collision and never overwrites`() {
        val dir = createTempDir(prefix = "shaftbackup_test_")
        try {
            File(dir, "A.shaft").writeText("""{"current":true}""")

            val report = ShaftBackup.restoreInto(dir, listOf("A.shaft" to """{"old":true}"""), acceptJsonish)

            assertEquals(0, report.restoredCount)
            assertEquals(1, report.renamedCount)
            assertEquals("""{"current":true}""", File(dir, "A.shaft").readText())
            assertEquals("""{"old":true}""", File(dir, "A (restored).shaft").readText())

            // A second differing restore takes the next suffix.
            val again = ShaftBackup.restoreInto(dir, listOf("A.shaft" to """{"older":true}"""), acceptJsonish)
            assertEquals(1, again.renamedCount)
            assertEquals("""{"older":true}""", File(dir, "A (restored 2).shaft").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `restoreInto counts invalid docs as failed and writes nothing`() {
        val dir = createTempDir(prefix = "shaftbackup_test_")
        try {
            val report = ShaftBackup.restoreInto(dir, listOf("bad.shaft" to "not json"), acceptJsonish)
            assertEquals(1, report.failedCount)
            assertEquals(0, dir.listFiles()!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `writeSnapshot zips saved docs and prunes beyond keep`() {
        val filesDir = createTempDir(prefix = "shaftbackup_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)
            File(shaftsDir, "A.shaft").writeText("""{"a":1}""")
            val backupsDir = File(filesDir, "backups")

            // Distinct timestamps → distinct snapshot names (stamp has second resolution).
            val base = 1_752_300_000_000L
            val written = (0 until 5).map { i ->
                ShaftBackup.writeSnapshot(
                    shaftsDir = shaftsDir,
                    backupsDir = backupsDir,
                    appVersion = "1.0",
                    docFormatVersion = 1,
                    nowMs = base + i * 1000L,
                    keep = 3,
                )
            }
            assertTrue(written.all { it != null })

            val remaining = backupsDir.listFiles()!!.map { it.name }.sorted()
            assertEquals(3, remaining.size)
            assertEquals(remaining, remaining.sorted())
            // Newest three survive.
            assertTrue(remaining.contains(written.last()!!.name))
            assertFalse(remaining.contains(written.first()!!.name))

            // Snapshot content round-trips.
            val contents = written.last()!!.inputStream().use { ShaftBackup.readZip(it) }
            assertEquals(listOf("A.shaft" to """{"a":1}"""), contents.docs)
            assertNotNull(contents.manifest)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `writeSnapshot returns null when there is nothing to snapshot`() {
        val filesDir = createTempDir(prefix = "shaftbackup_test_")
        try {
            val result = ShaftBackup.writeSnapshot(
                shaftsDir = InternalStorage.dir(filesDir),
                backupsDir = File(filesDir, "backups"),
                appVersion = "1.0",
                docFormatVersion = 1,
                nowMs = 1_752_300_000_000L,
            )
            assertNull(result)
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
