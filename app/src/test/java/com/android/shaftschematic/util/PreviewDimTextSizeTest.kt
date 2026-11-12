package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor preview's "Dimension text size" ([PreviewDimTextSize]): stored by name, read back
 * tolerantly (anything unknown is Medium), Medium the preview's historical size.
 */
class PreviewDimTextSizeTest {

    @Test
    fun `every size round-trips by name`() {
        PreviewDimTextSize.entries.forEach { assertEquals(it, PreviewDimTextSize.fromName(it.name)) }
    }

    @Test
    fun `missing or unknown names read as Medium`() {
        assertEquals(PreviewDimTextSize.MEDIUM, PreviewDimTextSize.Default)
        listOf(null, "", "medium", "HUGE", "LARGE ").forEach {
            assertEquals("\"$it\"", PreviewDimTextSize.MEDIUM, PreviewDimTextSize.fromName(it))
        }
    }

    @Test
    fun `medium is one and the steps are ordered`() {
        assertEquals(1f, PreviewDimTextSize.MEDIUM.factor, 0f)
        assertEquals(0.8f, PreviewDimTextSize.SMALL.factor, 0f)
        assertEquals(1.25f, PreviewDimTextSize.LARGE.factor, 0f)
        assertTrue(PreviewDimTextSize.SMALL.factor < PreviewDimTextSize.MEDIUM.factor)
        assertTrue(PreviewDimTextSize.MEDIUM.factor < PreviewDimTextSize.LARGE.factor)
    }
}
