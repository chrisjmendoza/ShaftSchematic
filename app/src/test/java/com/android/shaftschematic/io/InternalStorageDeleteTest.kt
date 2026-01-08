package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InternalStorageDeleteTest {

    @Test
    fun `delete removes only the targeted saved shaft file`() {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            // Given N saved shafts
            val names = listOf("A" + SHAFT_DOT_EXT, "B.json", "C" + SHAFT_DOT_EXT)
            names.forEach { name ->
                File(shaftsDir, name).writeText("{\"name\":\"$name\"}")
            }
            assertEquals(names.sorted(), InternalStorage.list(shaftsDir))

            // When deleting one
            val deleted = InternalStorage.delete(shaftsDir, "B.json")

            // Then only that file is removed
            assertTrue(deleted)
            assertFalse(File(shaftsDir, "B.json").exists())
            assertTrue(File(shaftsDir, "A" + SHAFT_DOT_EXT).exists())
            assertTrue(File(shaftsDir, "C" + SHAFT_DOT_EXT).exists())

            // And the list decrements with the correct item removed
            assertEquals(listOf("A" + SHAFT_DOT_EXT, "C" + SHAFT_DOT_EXT), InternalStorage.list(shaftsDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `delete returns false when file does not exist and leaves others intact`() {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)
            File(shaftsDir, "A" + SHAFT_DOT_EXT).writeText("{}")

            val deleted = InternalStorage.delete(shaftsDir, "missing" + SHAFT_DOT_EXT)

            assertFalse(deleted)
            assertTrue(File(shaftsDir, "A" + SHAFT_DOT_EXT).exists())
            assertEquals(listOf("A" + SHAFT_DOT_EXT), InternalStorage.list(shaftsDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
