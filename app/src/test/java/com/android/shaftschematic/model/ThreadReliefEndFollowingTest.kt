package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins [withReliefEndFollowing]: a thread relief still sitting at the end
 * [suggestedThreadReliefAtAft] chose follows the thread to its new end when the thread changes
 * position, and a relief deliberately put at the other end stays put.
 */
class ThreadReliefEndFollowingTest {

    private val oal = 3000f

    private fun excluded(isAftEnd: Boolean, reliefAtAft: Boolean, reliefLen: Float = 20f) = Threads(
        startFromAftMm = if (isAftEnd) -100f else oal,
        majorDiaMm = 100f, pitchMm = 6f, lengthMm = 100f,
        excludeFromOAL = true, isAftEnd = isAftEnd,
        reliefLenMm = reliefLen, reliefDiaMm = if (reliefLen > 0f) 90f else 0f, reliefAtAft = reliefAtAft,
    )

    @Test
    fun `a relief at the inboard end follows a thread that changes ends`() {
        // AFT-end thread: inboard is its FWD end (reliefAtAft = false).
        val before = excluded(isAftEnd = true, reliefAtAft = false)
        val moved = before.copy(isAftEnd = false, startFromAftMm = oal)
        assertEquals(true, moved.withReliefEndFollowing(before, oal).reliefAtAft)
        // And back again.
        val back = moved.withReliefEndFollowing(before, oal).let { m ->
            m.copy(isAftEnd = true, startFromAftMm = -100f).withReliefEndFollowing(m, oal)
        }
        assertEquals(false, back.reliefAtAft)
    }

    @Test
    fun `a relief deliberately at the outboard end stays where it was put`() {
        val before = excluded(isAftEnd = true, reliefAtAft = true)
        val moved = before.copy(isAftEnd = false, startFromAftMm = oal)
        assertSame(moved, moved.withReliefEndFollowing(before, oal))
    }

    @Test
    fun `a thread with no relief is returned unchanged`() {
        val before = excluded(isAftEnd = true, reliefAtAft = false, reliefLen = 0f)
        val moved = before.copy(isAftEnd = false, startFromAftMm = oal)
        assertSame(moved, moved.withReliefEndFollowing(before, oal))
    }

    @Test
    fun `including an AFT-end thread in the OAL keeps its relief inboard`() {
        // Excluded AFT thread → included at start 0: the suggestion is FWD either way, so nothing moves.
        val before = excluded(isAftEnd = true, reliefAtAft = false)
        val included = before.copy(excludeFromOAL = false, startFromAftMm = 0f)
        assertSame(included, included.withReliefEndFollowing(before, oal))
    }

    @Test
    fun `length and diameter never change`() {
        val before = excluded(isAftEnd = true, reliefAtAft = false)
        val after = before.copy(isAftEnd = false, startFromAftMm = oal).withReliefEndFollowing(before, oal)
        assertEquals(20f, after.reliefLenMm, 0f)
        assertEquals(90f, after.reliefDiaMm, 0f)
    }
}
