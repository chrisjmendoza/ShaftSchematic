package com.android.shaftschematic.data

import com.android.shaftschematic.model.RunoutReading
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure ring/eviction + dirty-gate logic for the autosave draft history. No Android deps.
 * See docs/Autosave_Incident_2026-07-25.md and data/DraftRing.kt.
 */
class DraftRingTest {

    private fun snap(customer: String = "acme"): AutosaveManager.SessionSnapshot =
        AutosaveManager.SessionSnapshot(
            shaftSpec = ShaftSpec(overallLengthMm = 100f),
            unitSystem = UnitSystem.INCHES,
            shaftPosition = ShaftPosition.OTHER,
            customer = customer,
            vessel = "",
            jobNumber = "",
            notes = "",
        )

    private fun entry(id: String, ts: Long): AutosaveManager.DraftEntry =
        AutosaveManager.DraftEntry(
            draftId = id,
            documentName = id,
            updatedAtEpochMs = ts,
            snapshot = snap(customer = id),
        )

    // ── Ring / eviction ──────────────────────────────────────────────────────

    @Test
    fun `insert new draft goes to the front`() {
        val start = listOf(entry("a", 10))
        val out = upsertDraft(start, entry("b", 20))
        assertEquals(listOf("b", "a"), out.map { it.draftId })
    }

    @Test
    fun `upsert existing replaces and moves it to the front`() {
        val start = listOf(entry("a", 10), entry("b", 20), entry("c", 30))
        val out = upsertDraft(start, entry("b", 99).copy(snapshot = snap(customer = "updated")))
        assertEquals(listOf("b", "a", "c"), out.map { it.draftId })
        assertEquals(3, out.size)
        assertEquals("updated", out.first().snapshot.customer)
        assertEquals(99L, out.first().updatedAtEpochMs)
    }

    @Test
    fun `default capacity is three - a fourth distinct draft evicts one`() {
        var ring = emptyList<AutosaveManager.DraftEntry>()
        ring = upsertDraft(ring, entry("a", 10))
        ring = upsertDraft(ring, entry("b", 20))
        ring = upsertDraft(ring, entry("c", 30))
        assertEquals(3, ring.size)
        ring = upsertDraft(ring, entry("d", 40))
        assertEquals(3, ring.size)
    }

    @Test
    fun `at capacity the oldest by timestamp is evicted`() {
        // Full ring, all distinct; "a" is the oldest.
        val ring = listOf(entry("c", 30), entry("b", 20), entry("a", 10))
        val out = upsertDraft(ring, entry("d", 40))
        assertEquals(setOf("d", "c", "b"), out.map { it.draftId }.toSet())
        assertFalse("oldest (a) must be evicted", out.any { it.draftId == "a" })
    }

    @Test
    fun `eviction is by timestamp not list position`() {
        // Newest-first order deliberately violated: oldest (b, ts=10) sits in the MIDDLE and
        // c (ts=50) is last. Eviction must still drop b, not the last element c.
        val ring = listOf(entry("a", 100), entry("b", 10), entry("c", 50))
        val out = upsertDraft(ring, entry("d", 200))
        assertEquals(3, out.size)
        assertFalse("oldest-by-timestamp (b) must be evicted", out.any { it.draftId == "b" })
        assertTrue(out.any { it.draftId == "c" })
        assertEquals(listOf("d", "a", "c"), out.map { it.draftId })
    }

    @Test
    fun `custom max is honored`() {
        val ring = listOf(entry("b", 20), entry("a", 10))
        val out = upsertDraft(ring, entry("c", 30), max = 2)
        assertEquals(2, out.size)
        assertEquals(listOf("c", "b"), out.map { it.draftId })
    }

    // ── Dirty gate ───────────────────────────────────────────────────────────

    @Test
    fun `equal snapshot is not written`() {
        val s = snap()
        assertFalse(shouldWriteDraft(s, s))
        assertFalse(shouldWriteDraft(s, s.copy()))
    }

    @Test
    fun `null baseline counts as dirty`() {
        assertTrue(shouldWriteDraft(snap(), null))
    }

    @Test
    fun `difference in spec is dirty`() {
        val base = snap()
        val changed = base.copy(shaftSpec = base.shaftSpec.copy(overallLengthMm = 999f))
        assertTrue(shouldWriteDraft(changed, base))
    }

    @Test
    fun `difference in metadata is dirty`() {
        val base = snap(customer = "acme")
        assertTrue(shouldWriteDraft(base.copy(customer = "other"), base))
    }

    @Test
    fun `difference in wear record is dirty`() {
        val base = snap()
        val changed = base.copy(
            wearRecord = WearRecord(spots = listOf(WearSpot(id = "s1", linerId = "l1", startMm = 1f, lengthMm = 2f)))
        )
        assertTrue(shouldWriteDraft(changed, base))
    }

    @Test
    fun `difference in runout readings is dirty`() {
        val base = snap()
        val changed = base.copy(
            runoutReadings = RunoutReadings().withReading(
                RunoutReading(componentId = "c1", stationIndex = 0, valueMm = 0.5f, highSpotHalfHours = 3)
            )
        )
        assertTrue(shouldWriteDraft(changed, base))
    }
}
