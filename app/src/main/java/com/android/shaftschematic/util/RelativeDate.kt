package com.android.shaftschematic.util

/**
 * RelativeDate — "how long ago" for a stored file's last-modified stamp.
 *
 * The Open screen and the Templates browser both list files the user saved, and a row that reads
 * "Today" beside one that reads "3 days ago" is the whole ordering cue the list gives. One
 * implementation, so the two screens can never drift into two vocabularies for the same age.
 *
 * Pure Kotlin — [nowMs] is a parameter rather than a call to the clock so the buckets are testable.
 */

/**
 * `Today` / `Yesterday` / `3 days ago` / `2w ago` / `5mo ago` for a file last touched at
 * [lastModifiedMs].
 *
 * Whole elapsed days, not calendar days: a stamp from 23:00 read at 00:30 still says "Today". The
 * approximation is deliberate — the row is a recency cue, not a timestamp, and an exact date is one
 * tap away in the file itself.
 */
fun relativeOpenDate(lastModifiedMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val days = ((nowMs - lastModifiedMs) / MS_PER_DAY).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}

private const val MS_PER_DAY = 1000L * 60 * 60 * 24
