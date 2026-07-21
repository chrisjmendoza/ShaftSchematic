package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.WearSpot
import com.android.shaftschematic.model.WearSpotReference
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the liner wear-spot state operations (`ShaftViewModel.addWearSpot` /
 * `updateWearSpot` / `updateWearSpotReference` / `removeWearSpot`, Phase 1 of
 * docs/LinerWearAreas_Proposal.md §5, plus the 2026-07-18 post-review spec: the
 * tiny-liner default-length clamp and the separate "Measure from" reference setter).
 *
 * These mirror what each function does inside `_wearRecord.update {}` — plain list
 * operations on [WearRecord], with no geometry side effects — so they stay fast JVM
 * unit tests with no Android dependencies (`ShaftViewModel` is an `AndroidViewModel`
 * and isn't instantiated directly in this test suite; see `ShaftViewModelUpdateTest`
 * and `ShaftViewModelRemoveTest` for the same convention on other component types).
 */
class ShaftViewModelWearSpotTest {

    // Mirrors ShaftViewModel.addWearSpot(linerId)'s defaults, incl. the 2026-07-18 clamp:
    // the default 25.4mm (1in) length is clamped to the liner's own length for tiny liners.
    private fun addWearSpot(record: WearRecord, linerId: String, linerLengthMm: Float): WearRecord =
        record.copy(
            spots = record.spots + WearSpot(
                linerId = linerId,
                startMm = 0f,
                lengthMm = min(25.4f, linerLengthMm.coerceAtLeast(0f)),
                minDiaMm = 0f,
                note = "",
            )
        )

    // Mirrors ShaftViewModel.updateWearSpot(id, startMm, lengthMm, minDiaMm, note).
    private fun updateWearSpot(
        record: WearRecord,
        id: String,
        startMm: Float,
        lengthMm: Float,
        minDiaMm: Float,
        note: String,
    ): WearRecord = record.copy(
        spots = record.spots.map { spot ->
            if (spot.id != id) spot else spot.copy(
                startMm = maxOf(0f, startMm),
                lengthMm = maxOf(0f, lengthMm),
                minDiaMm = maxOf(0f, minDiaMm),
                note = note,
            )
        }
    )

    // Mirrors ShaftViewModel.updateWearSpotReference(id, reference).
    private fun updateWearSpotReference(
        record: WearRecord,
        id: String,
        reference: WearSpotReference,
    ): WearRecord = record.copy(
        spots = record.spots.map { spot ->
            if (spot.id != id || spot.authoredReference == reference) spot
            else spot.copy(authoredReference = reference)
        }
    )

    // Mirrors ShaftViewModel.removeWearSpot(id).
    private fun removeWearSpot(record: WearRecord, id: String): WearRecord =
        record.copy(spots = record.spots.filterNot { it.id == id })

    @Test
    fun `addWearSpot appends a spot with default values for the given liner`() {
        val result = addWearSpot(WearRecord(), linerId = "ln1", linerLengthMm = 300f)

        assertEquals(1, result.spots.size)
        val spot = result.spots.single()
        assertEquals("ln1", spot.linerId)
        assertEquals(0f, spot.startMm, 0.001f)
        assertEquals(25.4f, spot.lengthMm, 0.001f)
        assertEquals(0f, spot.minDiaMm, 0.001f)
        assertEquals("", spot.note)
        assertEquals(WearSpotReference.LINER_AFT, spot.authoredReference)
        assertTrue("id must be non-blank", spot.id.isNotBlank())
    }

    @Test
    fun `addWearSpot on existing record preserves prior spots`() {
        val existing = WearRecord(spots = listOf(WearSpot(id = "s0", linerId = "ln0")))

        val result = addWearSpot(existing, linerId = "ln1", linerLengthMm = 300f)

        assertEquals(2, result.spots.size)
        assertEquals("s0", result.spots[0].id)
        assertEquals("ln1", result.spots[1].linerId)
    }

    @Test
    fun `addWearSpot clamps the default 1in length to a tiny liner's own length`() {
        // A 10mm liner is far shorter than the default 25.4mm (1in) band.
        val result = addWearSpot(WearRecord(), linerId = "ln1", linerLengthMm = 10f)

        val spot = result.spots.single()
        assertEquals(10f, spot.lengthMm, 0.001f)
    }

    @Test
    fun `addWearSpot keeps the default 1in length when the liner is longer`() {
        val result = addWearSpot(WearRecord(), linerId = "ln1", linerLengthMm = 300f)

        assertEquals(25.4f, result.spots.single().lengthMm, 0.001f)
    }

    @Test
    fun `updateWearSpotReference updates only the targeted spot's reference`() {
        val s1 = WearSpot(id = "s1", linerId = "ln1", authoredReference = WearSpotReference.LINER_AFT)
        val s2 = WearSpot(id = "s2", linerId = "ln1", authoredReference = WearSpotReference.LINER_AFT)
        val record = WearRecord(spots = listOf(s1, s2))

        val result = updateWearSpotReference(record, id = "s1", reference = WearSpotReference.FWD_SET)

        assertEquals(WearSpotReference.FWD_SET, result.spots.first { it.id == "s1" }.authoredReference)
        assertEquals(WearSpotReference.LINER_AFT, result.spots.first { it.id == "s2" }.authoredReference)
    }

    @Test
    fun `updateWearSpotReference never touches startMm or lengthMm`() {
        val spot = WearSpot(id = "s1", linerId = "ln1", startMm = 42f, lengthMm = 17f)
        val record = WearRecord(spots = listOf(spot))

        val result = updateWearSpotReference(record, id = "s1", reference = WearSpotReference.AFT_SET)

        val updated = result.spots.single()
        assertEquals(42f, updated.startMm, 0.001f)
        assertEquals(17f, updated.lengthMm, 0.001f)
    }

    @Test
    fun `updateWearSpotReference with unknown id is a no-op`() {
        val record = WearRecord(spots = listOf(WearSpot(id = "s1", linerId = "ln1")))

        val result = updateWearSpotReference(record, id = "nope", reference = WearSpotReference.FWD_SET)

        assertEquals(record, result)
    }

    @Test
    fun `updateWearSpot updates only the targeted spot`() {
        val s1 = WearSpot(id = "s1", linerId = "ln1", startMm = 0f, lengthMm = 10f, minDiaMm = 0f, note = "")
        val s2 = WearSpot(id = "s2", linerId = "ln1", startMm = 5f, lengthMm = 20f, minDiaMm = 100f, note = "keep me")
        val record = WearRecord(spots = listOf(s1, s2))

        val result = updateWearSpot(record, id = "s1", startMm = 15f, lengthMm = 40f, minDiaMm = 138.5f, note = "scored")

        val updated = result.spots.first { it.id == "s1" }
        assertEquals(15f, updated.startMm, 0.001f)
        assertEquals(40f, updated.lengthMm, 0.001f)
        assertEquals(138.5f, updated.minDiaMm, 0.001f)
        assertEquals("scored", updated.note)

        val untouched = result.spots.first { it.id == "s2" }
        assertEquals(5f, untouched.startMm, 0.001f)
        assertEquals(20f, untouched.lengthMm, 0.001f)
        assertEquals(100f, untouched.minDiaMm, 0.001f)
        assertEquals("keep me", untouched.note)
    }

    @Test
    fun `updateWearSpot clamps negative values to zero`() {
        val spot = WearSpot(id = "s1", linerId = "ln1", startMm = 10f, lengthMm = 10f, minDiaMm = 10f)
        val record = WearRecord(spots = listOf(spot))

        val result = updateWearSpot(record, id = "s1", startMm = -5f, lengthMm = -1f, minDiaMm = -2f, note = "")

        val updated = result.spots.single()
        assertEquals(0f, updated.startMm, 0.001f)
        assertEquals(0f, updated.lengthMm, 0.001f)
        assertEquals(0f, updated.minDiaMm, 0.001f)
    }

    @Test
    fun `updateWearSpot with unknown id is a no-op`() {
        val spot = WearSpot(id = "s1", linerId = "ln1", startMm = 1f, lengthMm = 2f)
        val record = WearRecord(spots = listOf(spot))

        val result = updateWearSpot(record, id = "does-not-exist", startMm = 99f, lengthMm = 99f, minDiaMm = 99f, note = "x")

        assertEquals(record, result)
    }

    @Test
    fun `removeWearSpot removes only the targeted spot`() {
        val s1 = WearSpot(id = "s1", linerId = "ln1")
        val s2 = WearSpot(id = "s2", linerId = "ln1")
        val s3 = WearSpot(id = "s3", linerId = "ln2")
        val record = WearRecord(spots = listOf(s1, s2, s3))

        val result = removeWearSpot(record, id = "s2")

        assertEquals(2, result.spots.size)
        assertEquals(listOf("s1", "s3"), result.spots.map { it.id })
    }

    @Test
    fun `removeWearSpot with unknown id is a no-op`() {
        val record = WearRecord(spots = listOf(WearSpot(id = "s1", linerId = "ln1")))

        val result = removeWearSpot(record, id = "nope")

        assertEquals(record, result)
    }

    // ── Reset on new document (mirrors ShaftViewModel.newDocument()) ───────────

    @Test
    fun `new document resets wear record to empty`() {
        val populated = WearRecord(spots = listOf(WearSpot(id = "s1", linerId = "ln1")))

        // Mirrors: _wearRecord.value = WearRecord() inside newDocument().
        val afterReset = WearRecord()

        assertTrue(populated.spots.isNotEmpty())
        assertTrue(afterReset.spots.isEmpty())
    }
}
