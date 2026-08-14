package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The document title strip renders on every editor tab, so its formatting is pinned here
 * rather than left to a per-screen Compose assertion.
 */
class EditorDocumentTitleTest {

    @Test
    fun `unsaved session with no filename reads as an untitled draft`() {
        assertEquals("Untitled draft", editorDocumentTitleText(null, hasUnsavedChanges = false))
    }

    @Test
    fun `dirty untitled draft carries the asterisk`() {
        assertEquals("Untitled draft *", editorDocumentTitleText(null, hasUnsavedChanges = true))
    }

    @Test
    fun `saved document shows its name with the extension stripped`() {
        assertEquals(
            "Job 4471 Shaft",
            editorDocumentTitleText("Job 4471 Shaft.shaft", hasUnsavedChanges = false),
        )
    }

    @Test
    fun `dirty saved document appends the asterisk after the stripped name`() {
        assertEquals(
            "Job 4471 Shaft *",
            editorDocumentTitleText("Job 4471 Shaft.shaft", hasUnsavedChanges = true),
        )
    }

    @Test
    fun `a name that is not extension-suffixed passes through unchanged`() {
        assertEquals("Stern tube", editorDocumentTitleText("Stern tube", hasUnsavedChanges = false))
    }
}
