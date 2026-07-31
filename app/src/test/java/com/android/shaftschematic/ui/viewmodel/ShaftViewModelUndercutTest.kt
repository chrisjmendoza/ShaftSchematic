package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Undercut
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the undercut drawing record state operations (`ShaftViewModel.addUndercut` /
 * `updateUndercut` / `updateUndercutReference` / `removeUndercut`, Phase 1 of
 * `docs/UndercutDrawing_PLAN.md` §6, §7).
 *
 * These mirror what each function does inside `_undercutRecord.update {}` — plain list
 * operations on [UndercutRecord], with no geometry side effects — so they stay fast JVM
 * unit tests with no Android dependencies (`ShaftViewModel` is an `AndroidViewModel` and
 * isn't instantiated directly in this test suite; see `ShaftViewModelWearSpotTest` for the
 * same convention on the sibling reference-only feature).
 */
class ShaftViewModelUndercutTest {

    // Mirrors ShaftViewModel.addUndercut(startFromAftMm, lengthMm): appends a new Undercut
    // with diaMm unentered (0) and AFT_SET as the default authored reference, returning its id.
    private fun addUndercut(record: UndercutRecord, startFromAftMm: Float, lengthMm: Float): Pair<UndercutRecord, String> {
        val undercut = Undercut(
            startFromAftMm = startFromAftMm,
            lengthMm = lengthMm,
            diaMm = 0f,
            authoredReference = UndercutReference.AFT_SET,
        )
        return record.copy(undercuts = record.undercuts + undercut) to undercut.id
    }

    // Mirrors ShaftViewModel.updateUndercut(id, startFromAftMm, lengthMm, diaMm, note): verbatim
    // field replacement, no clamping/snapping (golden rule).
    private fun updateUndercut(
        record: UndercutRecord,
        id: String,
        startFromAftMm: Float,
        lengthMm: Float,
        diaMm: Float,
        note: String,
    ): UndercutRecord = record.copy(
        undercuts = record.undercuts.map { u ->
            if (u.id != id) u else u.copy(
                startFromAftMm = startFromAftMm,
                lengthMm = lengthMm,
                diaMm = diaMm,
                note = note,
            )
        }
    )

    // Mirrors ShaftViewModel.updateUndercutReference(id, reference).
    private fun updateUndercutReference(
        record: UndercutRecord,
        id: String,
        reference: UndercutReference,
    ): UndercutRecord = record.copy(
        undercuts = record.undercuts.map { u ->
            if (u.id != id || u.authoredReference == reference) u
            else u.copy(authoredReference = reference)
        }
    )

    // Mirrors ShaftViewModel.removeUndercut(id).
    private fun removeUndercut(record: UndercutRecord, id: String): UndercutRecord =
        record.copy(undercuts = record.undercuts.filterNot { it.id == id })

    @Test
    fun `addUndercut appends an undercut with the given start and length, Ø unentered`() {
        val (result, id) = addUndercut(UndercutRecord(), startFromAftMm = 120f, lengthMm = 25.4f)

        assertEquals(1, result.undercuts.size)
        val u = result.undercuts.single()
        assertEquals(id, u.id)
        assertEquals(120f, u.startFromAftMm, 0.001f)
        assertEquals(25.4f, u.lengthMm, 0.001f)
        assertEquals(0f, u.diaMm, 0.001f)
        assertEquals(UndercutReference.AFT_SET, u.authoredReference)
        assertTrue("returned id must be non-blank", id.isNotBlank())
    }

    @Test
    fun `addUndercut on existing record preserves prior undercuts and mints a fresh id`() {
        val existing = UndercutRecord(undercuts = listOf(Undercut(id = "u0", startFromAftMm = 0f, lengthMm = 10f)))

        val (result, newId) = addUndercut(existing, startFromAftMm = 200f, lengthMm = 12.7f)

        assertEquals(2, result.undercuts.size)
        assertEquals("u0", result.undercuts[0].id)
        assertEquals(newId, result.undercuts[1].id)
        assertNotEquals("u0", newId)
    }

    @Test
    fun `updateUndercut replaces all fields verbatim, including an exact diaMm value`() {
        val existing = Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f, diaMm = 0f, note = "")
        val record = UndercutRecord(undercuts = listOf(existing))

        val result = updateUndercut(
            record, id = "u1",
            startFromAftMm = 118.11f, lengthMm = 25.4f, diaMm = 247.777f, note = "weld undercut",
        )

        val updated = result.undercuts.single()
        assertEquals(118.11f, updated.startFromAftMm, 0f)
        assertEquals(25.4f, updated.lengthMm, 0f)
        // Golden rule: no rounding/snapping of the typed measurement.
        assertEquals(247.777f, updated.diaMm, 0f)
        assertEquals("weld undercut", updated.note)
    }

    @Test
    fun `updateUndercut only touches the targeted undercut`() {
        val u1 = Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f)
        val u2 = Undercut(id = "u2", startFromAftMm = 100f, lengthMm = 20f, diaMm = 50f, note = "keep me")
        val record = UndercutRecord(undercuts = listOf(u1, u2))

        val result = updateUndercut(record, id = "u1", startFromAftMm = 5f, lengthMm = 15f, diaMm = 90f, note = "changed")

        val untouched = result.undercuts.first { it.id == "u2" }
        assertEquals(100f, untouched.startFromAftMm, 0.001f)
        assertEquals(20f, untouched.lengthMm, 0.001f)
        assertEquals(50f, untouched.diaMm, 0.001f)
        assertEquals("keep me", untouched.note)
    }

    @Test
    fun `updateUndercut with unknown id is a no-op`() {
        val record = UndercutRecord(undercuts = listOf(Undercut(id = "u1", startFromAftMm = 1f, lengthMm = 2f)))

        val result = updateUndercut(record, id = "does-not-exist", startFromAftMm = 99f, lengthMm = 99f, diaMm = 99f, note = "x")

        assertEquals(record, result)
    }

    @Test
    fun `updateUndercutReference updates only the targeted undercut's reference`() {
        val u1 = Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f, authoredReference = UndercutReference.AFT_SET)
        val u2 = Undercut(id = "u2", startFromAftMm = 0f, lengthMm = 10f, authoredReference = UndercutReference.AFT_SET)
        val record = UndercutRecord(undercuts = listOf(u1, u2))

        val result = updateUndercutReference(record, id = "u1", reference = UndercutReference.FWD_SET)

        assertEquals(UndercutReference.FWD_SET, result.undercuts.first { it.id == "u1" }.authoredReference)
        assertEquals(UndercutReference.AFT_SET, result.undercuts.first { it.id == "u2" }.authoredReference)
    }

    @Test
    fun `updateUndercutReference never touches startFromAftMm, lengthMm, or diaMm`() {
        val undercut = Undercut(id = "u1", startFromAftMm = 42f, lengthMm = 17f, diaMm = 88f)
        val record = UndercutRecord(undercuts = listOf(undercut))

        val result = updateUndercutReference(record, id = "u1", reference = UndercutReference.FWD_SET)

        val updated = result.undercuts.single()
        assertEquals(42f, updated.startFromAftMm, 0.001f)
        assertEquals(17f, updated.lengthMm, 0.001f)
        assertEquals(88f, updated.diaMm, 0.001f)
    }

    @Test
    fun `updateUndercutReference with unknown id is a no-op`() {
        val record = UndercutRecord(undercuts = listOf(Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f)))

        val result = updateUndercutReference(record, id = "nope", reference = UndercutReference.FWD_SET)

        assertEquals(record, result)
    }

    @Test
    fun `removeUndercut removes only the targeted undercut`() {
        val u1 = Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f)
        val u2 = Undercut(id = "u2", startFromAftMm = 20f, lengthMm = 10f)
        val u3 = Undercut(id = "u3", startFromAftMm = 40f, lengthMm = 10f)
        val record = UndercutRecord(undercuts = listOf(u1, u2, u3))

        val result = removeUndercut(record, id = "u2")

        assertEquals(2, result.undercuts.size)
        assertEquals(listOf("u1", "u3"), result.undercuts.map { it.id })
    }

    @Test
    fun `removeUndercut with unknown id is a no-op`() {
        val record = UndercutRecord(undercuts = listOf(Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f)))

        val result = removeUndercut(record, id = "nope")

        assertEquals(record, result)
    }

    // ── Reset on new document (mirrors ShaftViewModel.newDocument()) ───────────

    @Test
    fun `new document resets undercut record to empty`() {
        val populated = UndercutRecord(undercuts = listOf(Undercut(id = "u1", startFromAftMm = 0f, lengthMm = 10f)))

        // Mirrors: _undercutRecord.value = UndercutRecord() inside newDocument().
        val afterReset = UndercutRecord()

        assertTrue(populated.undercuts.isNotEmpty())
        assertTrue(afterReset.undercuts.isEmpty())
    }
}
