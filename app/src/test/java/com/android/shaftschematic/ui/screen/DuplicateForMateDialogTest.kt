package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.doc.matePosition
import com.android.shaftschematic.model.ShaftPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Seeding and the Create gate of the duplicate-for-mate dialog: a mate is named after the
 * opposite side, and a copy may never land on a name that already exists.
 *
 * The dialog's two decisions are pure functions ([mateNameSeed], [mateFileNameOrNull]) rather
 * than assertions against a rendered dialog — an `AlertDialog` opens its own window, which the
 * Compose test rule under Robolectric never settles (`AppNotIdleException`), and no test in
 * this repo renders one.
 */
class DuplicateForMateDialogTest {

    private val existing = listOf("J-1 - Acme - Tidewater - PORT")

    private fun seed(
        sourceBaseName: String = "J-1 - Acme - Tidewater - PORT",
        jobNumber: String = "J-1",
        customer: String = "Acme",
        vessel: String = "Tidewater",
        position: ShaftPosition = ShaftPosition.PORT,
        existingBaseNames: Collection<String> = existing,
    ) = mateNameSeed(
        sourceBaseName = sourceBaseName,
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        matePosition = matePosition(position),
        existingBaseNames = existingBaseNames,
    )

    /* ── The seeded name ── */

    @Test
    fun `the name seeds with the opposite side`() {
        assertEquals("J-1 - Acme - Tidewater - STBD", seed())
    }

    @Test
    fun `the seed never proposes the source's own name`() {
        // The source is in the store, so a seed equal to it would be refused on Create.
        val proposed = seed()
        assertEquals(null, existing.firstOrNull { it.equals(proposed, ignoreCase = true) })
    }

    @Test
    fun `a seeded name that is already taken is numbered`() {
        assertEquals(
            "J-1 - Acme - Tidewater - STBD (2)",
            seed(existingBaseNames = existing + "J-1 - Acme - Tidewater - STBD"),
        )
    }

    @Test
    fun `with no project information the name falls back to the source`() {
        assertEquals(
            "Bare draft (mate)",
            seed(sourceBaseName = "Bare draft", jobNumber = "", customer = "", vessel = ""),
        )
    }

    @Test
    fun `a centre shaft keeps its side in the seed`() {
        assertEquals(
            "J-1 - Acme - Tidewater - CENTER",
            seed(position = ShaftPosition.CENTER),
        )
    }

    @Test
    fun `a side that prints nothing leaves the seed unsuffixed`() {
        assertEquals("J-1 - Acme - Tidewater", seed(position = ShaftPosition.OTHER))
    }

    /* ── The Create gate ── */

    @Test
    fun `a free name becomes a shaft filename`() {
        assertEquals("Mate copy.shaft", mateFileNameOrNull("Mate copy", existing))
    }

    @Test
    fun `a typed extension is normalized rather than doubled`() {
        assertEquals("Mate copy.shaft", mateFileNameOrNull("Mate copy.shaft", existing))
    }

    @Test
    fun `a blank name is refused`() {
        assertNull(mateFileNameOrNull("", existing))
        assertNull(mateFileNameOrNull("   ", existing))
    }

    @Test
    fun `an existing name is refused, ignoring case`() {
        assertNull(mateFileNameOrNull("J-1 - Acme - Tidewater - PORT", existing))
        assertNull(mateFileNameOrNull("j-1 - acme - tidewater - port", existing))
    }

    @Test
    fun `the refusal follows the normalized name, not the raw text`() {
        // "…PORT.shaft" normalizes onto the existing base, so it must be refused too.
        assertNull(mateFileNameOrNull("J-1 - Acme - Tidewater - PORT.shaft", existing))
    }

    @Test
    fun `the seeded name always passes the gate`() {
        assertEquals("J-1 - Acme - Tidewater - STBD.shaft", mateFileNameOrNull(seed(), existing))
    }
}
