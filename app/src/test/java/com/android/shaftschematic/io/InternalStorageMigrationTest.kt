package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InternalStorageMigrationTest {

    @Test
    fun `migration renames legacy json to shaft without overwriting`() {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            // Existing new-format file.
            File(shaftsDir, "A" + SHAFT_DOT_EXT).writeText("{\"v\":1}")

            // Legacy files to migrate.
            File(shaftsDir, "A.json").writeText("{\"legacy\":true}")
            File(shaftsDir, "B.json").writeText("{\"legacy\":true}")

            val report = InternalStorage.migrateLegacyJsonToShaft(shaftsDir)

            assertEquals(2, report.migratedCount)
            assertEquals(0, report.skippedCount)

            // A.json can't become A.shaft (collision), so it becomes A (Migrated).shaft
            assertTrue(File(shaftsDir, "A" + SHAFT_DOT_EXT).exists())
            assertTrue(File(shaftsDir, "A (Migrated)" + SHAFT_DOT_EXT).exists())
            assertTrue(File(shaftsDir, "B" + SHAFT_DOT_EXT).exists())

            assertFalse(File(shaftsDir, "A.json").exists())
            assertFalse(File(shaftsDir, "B.json").exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
