package com.android.shaftschematic.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision layer of the backup auto-mirror.
 *
 * The two rules worth pinning: a mirror write reuses the document that is already there (a
 * create would leave "Job 1 (1).shaft" behind on every save), and only saved `.shaft` documents
 * are ever mirrored.
 */
class BackupMirrorPlanTest {

    private val folder = "content://com.android.externalstorage.documents/tree/primary%3ABackups"

    // ── planMirrorWrite ──────────────────────────────────────────────────────

    @Test
    fun `empty folder creates the document`() {
        assertEquals(MirrorWriteTarget.Create, planMirrorWrite(emptyList(), "Job 1.shaft"))
    }

    @Test
    fun `existing name is overwritten in place`() {
        val entries = listOf(
            MirrorFolderEntry("primary:Backups/Other.shaft", "Other.shaft"),
            MirrorFolderEntry("primary:Backups/Job 1.shaft", "Job 1.shaft"),
        )
        assertEquals(
            MirrorWriteTarget.Overwrite("primary:Backups/Job 1.shaft"),
            planMirrorWrite(entries, "Job 1.shaft"),
        )
    }

    @Test
    fun `case-insensitive match still overwrites rather than duplicating`() {
        val entries = listOf(MirrorFolderEntry("id-1", "JOB 1.SHAFT"))
        assertEquals(MirrorWriteTarget.Overwrite("id-1"), planMirrorWrite(entries, "Job 1.shaft"))
    }

    @Test
    fun `exact match wins over a case-insensitive one`() {
        val entries = listOf(
            MirrorFolderEntry("loud", "JOB 1.SHAFT"),
            MirrorFolderEntry("exact", "Job 1.shaft"),
        )
        assertEquals(MirrorWriteTarget.Overwrite("exact"), planMirrorWrite(entries, "Job 1.shaft"))
    }

    @Test
    fun `a provider-deduplicated copy is not mistaken for the document`() {
        // "Job 1 (1).shaft" is what an unconditional create leaves behind; mirroring onto it
        // would abandon the real copy and keep growing the folder.
        val entries = listOf(MirrorFolderEntry("dup", "Job 1 (1).shaft"))
        assertEquals(MirrorWriteTarget.Create, planMirrorWrite(entries, "Job 1.shaft"))
    }

    @Test
    fun `a different document is not overwritten`() {
        val entries = listOf(MirrorFolderEntry("other", "Job 12.shaft"))
        assertEquals(MirrorWriteTarget.Create, planMirrorWrite(entries, "Job 1.shaft"))
    }

    // ── shouldMirrorDocument ─────────────────────────────────────────────────

    @Test
    fun `no folder means mirroring is off`() {
        assertFalse(shouldMirrorDocument("Job 1.shaft", null))
        assertFalse(shouldMirrorDocument("Job 1.shaft", ""))
        assertFalse(shouldMirrorDocument("Job 1.shaft", "   "))
    }

    @Test
    fun `a saved shaft document mirrors`() {
        assertTrue(shouldMirrorDocument("Job 1.shaft", folder))
        assertTrue(shouldMirrorDocument("Job 1.SHAFT", folder))
    }

    @Test
    fun `atomic-save siblings never mirror`() {
        assertFalse(shouldMirrorDocument("Job 1.shaft.tmp", folder))
        assertFalse(shouldMirrorDocument("Job 1.shaft.bak", folder))
    }

    @Test
    fun `non-document names never mirror`() {
        assertFalse(shouldMirrorDocument("Job 1.json", folder))
        assertFalse(shouldMirrorDocument("snapshot-20260825.zip", folder))
        assertFalse(shouldMirrorDocument("", folder))
        assertFalse(shouldMirrorDocument(".shaft", folder))
    }

    @Test
    fun `a name carrying a path separator never mirrors`() {
        // Every write resolves the name against the granted tree, so a separator would address a
        // document outside the folder the user picked.
        assertFalse(shouldMirrorDocument("../Job 1.shaft", folder))
        assertFalse(shouldMirrorDocument("sub/Job 1.shaft", folder))
        assertFalse(shouldMirrorDocument("sub\\Job 1.shaft", folder))
    }

    @Test
    fun `an untrimmed name never mirrors`() {
        assertFalse(shouldMirrorDocument(" Job 1.shaft", folder))
    }

    // ── mirrorFolderFallbackLabel ────────────────────────────────────────────

    @Test
    fun `fallback label names the leaf folder`() {
        assertEquals(
            "Shafts",
            mirrorFolderFallbackLabel(
                "content://com.android.externalstorage.documents/tree/primary%3ABackups%2FShafts"
            ),
        )
    }

    @Test
    fun `fallback label handles a root-level folder`() {
        assertEquals(
            "Backups",
            mirrorFolderFallbackLabel(
                "content://com.android.externalstorage.documents/tree/primary%3ABackups"
            ),
        )
    }

    @Test
    fun `fallback label degrades rather than showing an empty row`() {
        assertEquals("Selected folder", mirrorFolderFallbackLabel(""))
    }
}
