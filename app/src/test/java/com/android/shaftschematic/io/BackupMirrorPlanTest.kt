package com.android.shaftschematic.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision layer of the backup auto-mirror.
 *
 * The rules worth pinning: a mirror write reuses the document that is already there (a create
 * would leave "Job 1 (1).shaft" behind on every save), a delete finds exactly the document that
 * write had been maintaining (anything narrower leaves the stale copy this closes), and only
 * saved `.shaft` documents are ever mirrored.
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

    // ── planMirrorDelete ─────────────────────────────────────────────────────

    @Test
    fun `deleting a document removes its copy`() {
        val entries = listOf(
            MirrorFolderEntry("primary:Backups/Other.shaft", "Other.shaft"),
            MirrorFolderEntry("primary:Backups/Job 1.shaft", "Job 1.shaft"),
        )
        assertEquals(
            MirrorDeleteTarget.Delete("primary:Backups/Job 1.shaft"),
            planMirrorDelete(entries, "Job 1.shaft"),
        )
    }

    @Test
    fun `an empty folder has nothing to delete`() {
        assertEquals(MirrorDeleteTarget.NotPresent, planMirrorDelete(emptyList(), "Job 1.shaft"))
    }

    @Test
    fun `a document that was never mirrored is a no-op, not a failure`() {
        // Saved before the folder was picked, or tidied away by hand: the folder is allowed to
        // be behind, and a delete of something that is not there has nothing to answer for.
        val entries = listOf(MirrorFolderEntry("other", "Job 12.shaft"))
        assertEquals(MirrorDeleteTarget.NotPresent, planMirrorDelete(entries, "Job 1.shaft"))
    }

    @Test
    fun `a provider-deduplicated copy is not deleted for the document`() {
        val entries = listOf(MirrorFolderEntry("dup", "Job 1 (1).shaft"))
        assertEquals(MirrorDeleteTarget.NotPresent, planMirrorDelete(entries, "Job 1.shaft"))
    }

    @Test
    fun `delete finds the same document a write would have written over`() {
        // Matching more strictly on delete than on write would leave behind exactly the copy the
        // write had been maintaining — a stale document under a name the user deleted.
        val entries = listOf(MirrorFolderEntry("loud", "JOB 1.SHAFT"))
        assertEquals(MirrorWriteTarget.Overwrite("loud"), planMirrorWrite(entries, "Job 1.shaft"))
        assertEquals(MirrorDeleteTarget.Delete("loud"), planMirrorDelete(entries, "Job 1.shaft"))
    }

    @Test
    fun `delete prefers the exact name over a case-insensitive one`() {
        val entries = listOf(
            MirrorFolderEntry("loud", "JOB 1.SHAFT"),
            MirrorFolderEntry("exact", "Job 1.shaft"),
        )
        assertEquals(MirrorDeleteTarget.Delete("exact"), planMirrorDelete(entries, "Job 1.shaft"))
    }

    @Test
    fun `a rename resolves the old and the new name to different documents`() {
        // The rename path writes the new name, then deletes the old one; both halves must land on
        // their own document or the rename would eat the copy it just wrote.
        val entries = listOf(
            MirrorFolderEntry("old-id", "Job 1.shaft"),
            MirrorFolderEntry("new-id", "Job 2.shaft"),
        )
        assertEquals(MirrorWriteTarget.Overwrite("new-id"), planMirrorWrite(entries, "Job 2.shaft"))
        assertEquals(MirrorDeleteTarget.Delete("old-id"), planMirrorDelete(entries, "Job 1.shaft"))
    }

    // ── findMirrorEntry ──────────────────────────────────────────────────────

    @Test
    fun `entry lookup returns null when the folder holds nothing by that name`() {
        assertNull(findMirrorEntry(listOf(MirrorFolderEntry("other", "Job 12.shaft")), "Job 1.shaft"))
    }

    @Test
    fun `entry lookup returns the matched entry whole`() {
        val entry = MirrorFolderEntry("primary:Backups/Job 1.shaft", "Job 1.shaft")
        assertEquals(entry, findMirrorEntry(listOf(entry), "Job 1.shaft"))
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
