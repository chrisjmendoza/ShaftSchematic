package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared rename dialog's decision rules, used by BOTH the Open screen's file menu and the
 * editor's title-strip tap. Pinning them here is what keeps the two surfaces identical.
 *
 * These are the outcomes reached before any storage is touched — a blank name is refused and a
 * name that comes back to the current one is a plain dismissal, neither of them a disk write.
 * The dialog itself is a thin shell over this: [RenameOutcome.Blank] → `onError`,
 * [RenameOutcome.Unchanged] → `onDismiss`, [RenameOutcome.Proceed] → the rename attempt, whose
 * own refusal to overwrite lives in `InternalStorage.rename` and is covered by
 * `InternalStorageRenameTest`.
 */
class RenameShaftDocumentDialogTest {

    private val from = "Job 4471 Shaft$SHAFT_DOT_EXT"

    @Test
    fun `an empty name is refused`() {
        assertEquals(RenameOutcome.Blank, renameOutcomeFor(from, ""))
    }

    @Test
    fun `a whitespace-only name is refused`() {
        assertEquals(RenameOutcome.Blank, renameOutcomeFor(from, "   "))
        assertEquals(RenameOutcome.Blank, renameOutcomeFor(from, "\t\n "))
    }

    @Test
    fun `a name that reduces to nothing usable is refused`() {
        // Dots survive sanitizing but leave the normalizer no filename to build.
        assertEquals(RenameOutcome.Blank, renameOutcomeFor(from, "..."))
    }

    @Test
    fun `the blank refusal carries the message the callers show`() {
        // The dialog reports exactly this string; a caller's snackbar quotes it verbatim.
        assertEquals("Name cannot be blank.", RENAME_BLANK_NAME_MESSAGE)
    }

    @Test
    fun `the unchanged name is not a rename`() {
        assertEquals(RenameOutcome.Unchanged, renameOutcomeFor(from, "Job 4471 Shaft"))
    }

    @Test
    fun `retyping the name with its extension is still unchanged`() {
        assertEquals(RenameOutcome.Unchanged, renameOutcomeFor(from, "Job 4471 Shaft$SHAFT_DOT_EXT"))
    }

    @Test
    fun `a differently-cased name is the same name`() {
        // The store treats these as one file, so this is a no-op rather than a failed rename.
        assertEquals(RenameOutcome.Unchanged, renameOutcomeFor(from, "job 4471 shaft"))
    }

    @Test
    fun `surrounding whitespace does not make a new name`() {
        assertEquals(RenameOutcome.Unchanged, renameOutcomeFor(from, "  Job 4471 Shaft  "))
    }

    @Test
    fun `a different name proceeds, normalized to a document filename`() {
        assertEquals(
            RenameOutcome.Proceed("Job 4471 Stbd$SHAFT_DOT_EXT"),
            renameOutcomeFor(from, "Job 4471 Stbd"),
        )
    }

    @Test
    fun `path separators are sanitized out rather than escaping the store`() {
        // A name is ONE path segment: every store does File(dir, name), so a traversal in the
        // typed text would resolve outside the document directory.
        val outcome = renameOutcomeFor(from, "../other/Job 9")
        assertEquals(RenameOutcome.Proceed(".._other_Job 9$SHAFT_DOT_EXT"), outcome)
    }
}
