package com.android.shaftschematic.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure show/dismiss coverage for the Schematic tab's spec-level warning banner.
 *
 * The logic is message-AGNOSTIC — it keys off the set, never the text — but the multi-message
 * cases still use real lines `specIssues` can emit, so a dismissal is exercised
 * against a set the banner can actually be shown with.
 */
class SpecWarningVisibilityTest {

    private val realMessage = "Body #2 is shorter than 1 mm"
    private val otherRealMessage = "Body #1 runs 4 13/16\" past the FWD end"

    @Test
    fun `banner hidden when there are no messages`() {
        assertFalse(bannerVisible(emptyList(), dismissedKey = null))
    }

    @Test
    fun `banner visible with messages and nothing dismissed yet`() {
        assertTrue(bannerVisible(listOf(realMessage), dismissedKey = null))
    }

    @Test
    fun `banner hidden once dismissed for the exact same message set`() {
        val messages = listOf("Body #2 is shorter than 1 mm")
        val key = warningSetKey(messages)
        assertFalse(bannerVisible(messages, dismissedKey = key))
    }

    @Test
    fun `banner reappears when the message set changes after dismissal`() {
        val original = listOf("Body #2 is shorter than 1 mm")
        val dismissedKey = warningSetKey(original)

        val grown = listOf("Body #3 is shorter than 1 mm")
        assertTrue(bannerVisible(grown, dismissedKey))

        val extra = original + otherRealMessage
        assertTrue(bannerVisible(extra, dismissedKey))
    }

    @Test
    fun `banner stays hidden when the same set recomputes in the same order`() {
        val messages = listOf(realMessage, otherRealMessage)
        val dismissedKey = warningSetKey(messages)
        // A fresh, structurally-identical list from a later recompute — not the same instance.
        val recomputed = listOf(realMessage, otherRealMessage)
        assertFalse(bannerVisible(recomputed, dismissedKey))
    }

    @Test
    fun `warningSetKey differs for different message ordering`() {
        val a = warningSetKey(listOf("alpha", "beta"))
        val b = warningSetKey(listOf("beta", "alpha"))
        assertNotEquals(a, b)
    }

    @Test
    fun `warningSetKey is stable for identical input`() {
        val a = warningSetKey(listOf("alpha", "beta"))
        val b = warningSetKey(listOf("alpha", "beta"))
        assertEquals(a, b)
    }

    @Test
    fun `warningSetKey does not collide across a message boundary shift`() {
        // Without a delimiter, ["ab", "c"] and ["a", "bc"] would both join to "abc".
        val a = warningSetKey(listOf("ab", "c"))
        val b = warningSetKey(listOf("a", "bc"))
        assertNotEquals(a, b)
    }
}
