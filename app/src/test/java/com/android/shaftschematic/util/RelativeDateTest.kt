package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The recency label the Open screen and the Templates browser both put on a stored file's row.
 * The buckets are whole elapsed days, so the boundaries are what needs pinning.
 */
class RelativeDateTest {

    private val day = 1000L * 60 * 60 * 24
    private val now = 1_700_000_000_000L

    private fun ago(ms: Long) = relativeOpenDate(now - ms, now)

    @Test
    fun `under a day reads as today`() {
        assertEquals("Today", ago(0))
        assertEquals("Today", ago(day - 1))
    }

    @Test
    fun `exactly one day reads as yesterday`() {
        assertEquals("Yesterday", ago(day))
        assertEquals("Yesterday", ago(2 * day - 1))
    }

    @Test
    fun `two to six days count in days`() {
        assertEquals("2 days ago", ago(2 * day))
        assertEquals("6 days ago", ago(6 * day))
    }

    @Test
    fun `a week or more counts in weeks up to a month`() {
        assertEquals("1w ago", ago(7 * day))
        assertEquals("1w ago", ago(13 * day))
        assertEquals("2w ago", ago(14 * day))
        assertEquals("4w ago", ago(29 * day))
    }

    @Test
    fun `a month or more counts in months`() {
        assertEquals("1mo ago", ago(30 * day))
        assertEquals("2mo ago", ago(60 * day))
    }

    @Test
    fun `a stamp in the future reads as today rather than a negative age`() {
        // Clock skew and restored backups both produce these; "-1 days ago" is not a thing.
        assertEquals("Today", relativeOpenDate(now + 5 * day, now))
    }
}
