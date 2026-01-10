package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InternalStorageRenameTest {

    @Test
    fun `rename moves file when source exists and target does not`() {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)
            File(shaftsDir, "A" + SHAFT_DOT_EXT).writeText("{\"name\":\"A\"}")

            val ok = InternalStorage.rename(shaftsDir, "A" + SHAFT_DOT_EXT, "B" + SHAFT_DOT_EXT)

            assertTrue(ok)
            assertFalse(File(shaftsDir, "A" + SHAFT_DOT_EXT).exists())
            assertTrue(File(shaftsDir, "B" + SHAFT_DOT_EXT).exists())
            assertEquals(listOf("B" + SHAFT_DOT_EXT), InternalStorage.list(shaftsDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `rename returns false when target already exists`() {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)
            File(shaftsDir, "A" + SHAFT_DOT_EXT).writeText("{\"name\":\"A\"}")
            File(shaftsDir, "B" + SHAFT_DOT_EXT).writeText("{\"name\":\"B\"}")

            val ok = InternalStorage.rename(shaftsDir, "A" + SHAFT_DOT_EXT, "B" + SHAFT_DOT_EXT)

            assertFalse(ok)
            assertTrue(File(shaftsDir, "A" + SHAFT_DOT_EXT).exists())
            assertTrue(File(shaftsDir, "B" + SHAFT_DOT_EXT).exists())
            assertEquals(listOf("A" + SHAFT_DOT_EXT, "B" + SHAFT_DOT_EXT), InternalStorage.list(shaftsDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `rename returns false when source missing`() {
        val filesDir = createTempDir(prefix = "shaftschematic_test_")
        try {
            val shaftsDir = InternalStorage.dir(filesDir)

            val ok = InternalStorage.rename(shaftsDir, "missing" + SHAFT_DOT_EXT, "B" + SHAFT_DOT_EXT)

            assertFalse(ok)
            assertEquals(emptyList<String>(), InternalStorage.list(shaftsDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
