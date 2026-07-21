package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ShaftSpec.splitBodiesAround] and [ShaftSpec.mergeBodiesAround].
 *
 * Split: when a taper/thread/liner is inserted, any body whose span overlaps the
 * component is cut into left and/or right fragments. The original body ID is removed.
 *
 * Merge: when a component is removed, body fragments flanking it are merged back
 * into a single body. If the component was at a shaft boundary, the one body that
 * touches it expands to fill the freed span.
 */
class BodySplitMergeTest {

    private var counter = 0
    private fun nextId() = "gen-${++counter}"
    private fun resetIds() { counter = 0 }

    // ── splitBodiesAround ────────────────────────────────────────────────────

    @Test
    fun `split - component in middle of body produces two fragments`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val result = spec.splitBodiesAround(200f, 350f, ::nextId)

        assertEquals("original body removed", listOf("b1"), result.removedIds)
        assertEquals("two fragments added", 2, result.addedIds.size)
        assertEquals("two bodies in result", 2, result.spec.bodies.size)

        val left = result.spec.bodies.first { it.startFromAftMm == 0f }
        assertEquals("left fragment ends at compStart", 200f, left.lengthMm, 0.001f)
        assertEquals("left fragment inherits diameter", 60f, left.diaMm, 0.001f)

        val right = result.spec.bodies.first { it.startFromAftMm == 350f }
        assertEquals("right fragment starts at compEnd", 350f, right.startFromAftMm, 0.001f)
        assertEquals("right fragment length = bodyEnd - compEnd", 150f, right.lengthMm, 0.001f)
        assertEquals("right fragment inherits diameter", 60f, right.diaMm, 0.001f)
    }

    @Test
    fun `split - component at aft end produces only right fragment`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val result = spec.splitBodiesAround(0f, 150f, ::nextId)

        assertEquals(1, result.spec.bodies.size)
        assertEquals(1, result.addedIds.size)
        val frag = result.spec.bodies[0]
        assertEquals(150f, frag.startFromAftMm, 0.001f)
        assertEquals(350f, frag.lengthMm, 0.001f)
    }

    @Test
    fun `split - component at fwd end produces only left fragment`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val result = spec.splitBodiesAround(350f, 500f, ::nextId)

        assertEquals(1, result.spec.bodies.size)
        val frag = result.spec.bodies[0]
        assertEquals(0f, frag.startFromAftMm, 0.001f)
        assertEquals(350f, frag.lengthMm, 0.001f)
    }

    @Test
    fun `split - component spanning full body removes it entirely`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 100f, lengthMm = 200f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val result = spec.splitBodiesAround(100f, 300f, ::nextId)

        assertTrue("body fully consumed — no fragments", result.spec.bodies.isEmpty())
        assertEquals(listOf("b1"), result.removedIds)
        assertTrue(result.addedIds.isEmpty())
    }

    @Test
    fun `split - non-overlapping body is untouched`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 100f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val result = spec.splitBodiesAround(200f, 350f, ::nextId)

        assertEquals(1, result.spec.bodies.size)
        assertEquals("b1", result.spec.bodies[0].id)
        assertTrue("no ids removed", result.removedIds.isEmpty())
        assertTrue("no ids added",   result.addedIds.isEmpty())
    }

    @Test
    fun `split - touching endpoint is not considered overlapping`() {
        resetIds()
        // body ends at 200; component starts at 200 — they touch but don't overlap
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 200f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val result = spec.splitBodiesAround(200f, 350f, ::nextId)

        assertEquals("touching body untouched", 1, result.spec.bodies.size)
        assertEquals("b1", result.spec.bodies[0].id)
    }

    @Test
    fun `split - multiple bodies only the overlapping one is split`() {
        resetIds()
        val b1 = Body(id = "b1", startFromAftMm = 0f,   lengthMm = 200f, diaMm = 60f)
        val b2 = Body(id = "b2", startFromAftMm = 400f, lengthMm = 200f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(b1, b2))

        // Component sits inside b1 only
        val result = spec.splitBodiesAround(50f, 150f, ::nextId)

        // b1 splits into two; b2 survives intact
        assertEquals(3, result.spec.bodies.size)
        assertTrue("b2 survives", result.spec.bodies.any { it.id == "b2" })
        assertFalse("b1 removed", result.spec.bodies.any { it.id == "b1" })
        assertEquals(listOf("b1"), result.removedIds)
        assertEquals(2, result.addedIds.size)
    }

    @Test
    fun `split - roundtrip preserves total body coverage`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 600f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(body))

        val result = spec.splitBodiesAround(200f, 400f, ::nextId)

        val totalCoverage = result.spec.bodies.sumOf { it.lengthMm.toDouble() }
        // Original 600mm minus the 200mm gap = 400mm of body coverage
        assertEquals(400.0, totalCoverage, 0.001)
    }

    // ── mergeBodiesAround ────────────────────────────────────────────────────

    @Test
    fun `merge - two flanking fragments merge into one spanning body`() {
        resetIds()
        val left  = Body(id = "bl", startFromAftMm = 0f,   lengthMm = 200f, diaMm = 60f)
        val right = Body(id = "br", startFromAftMm = 350f, lengthMm = 250f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(left, right))

        val result = spec.mergeBodiesAround(200f, 350f, ::nextId)

        assertEquals("merged into one body", 1, result.spec.bodies.size)
        assertEquals("both originals removed", setOf("bl", "br"), result.removedIds.toSet())
        assertEquals(1, result.addedIds.size)

        val merged = result.spec.bodies[0]
        assertEquals("merged start = left start", 0f, merged.startFromAftMm, 0.001f)
        assertEquals("merged length spans entire gap", 600f, merged.lengthMm, 0.001f)
    }

    @Test
    fun `merge - merged body diameter is max of two fragments`() {
        resetIds()
        val left  = Body(id = "bl", startFromAftMm = 0f,   lengthMm = 200f, diaMm = 55f)
        val right = Body(id = "br", startFromAftMm = 350f, lengthMm = 100f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(left, right))

        val result = spec.mergeBodiesAround(200f, 350f, ::nextId)

        assertEquals("max diameter preserved", 60f, result.spec.bodies[0].diaMm, 0.001f)
    }

    @Test
    fun `merge - component at fwd end expands left body to fill gap`() {
        resetIds()
        // Component was at the shaft FWD end — only a left body exists
        val left = Body(id = "bl", startFromAftMm = 0f, lengthMm = 300f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(left))

        val result = spec.mergeBodiesAround(300f, 500f, ::nextId)

        assertEquals(1, result.spec.bodies.size)
        val expanded = result.spec.bodies[0]
        assertEquals("start unchanged", 0f, expanded.startFromAftMm, 0.001f)
        assertEquals("expanded to cover freed span", 500f, expanded.lengthMm, 0.001f)
    }

    @Test
    fun `merge - component at aft end expands right body to fill gap`() {
        resetIds()
        val right = Body(id = "br", startFromAftMm = 200f, lengthMm = 300f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(right))

        val result = spec.mergeBodiesAround(0f, 200f, ::nextId)

        assertEquals(1, result.spec.bodies.size)
        val expanded = result.spec.bodies[0]
        assertEquals("start moved to aft face", 0f, expanded.startFromAftMm, 0.001f)
        assertEquals("expanded to cover freed span", 500f, expanded.lengthMm, 0.001f)
    }

    @Test
    fun `merge - no flanking bodies returns spec unchanged`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 600f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(body))

        val result = spec.mergeBodiesAround(100f, 300f, ::nextId)

        assertTrue("removedIds empty", result.removedIds.isEmpty())
        assertTrue("addedIds empty",   result.addedIds.isEmpty())
        assertEquals("spec unchanged", spec.bodies, result.spec.bodies)
    }

    @Test
    fun `merge - does not merge across a component still occupying the gap`() {
        // 2026-07-21 guard: bl ends at 200, br starts at 350, but a liner still sits at
        // 250..300 between them (it had overlapped the just-removed component). Merging
        // bl..br would span the surviving liner and manufacture a long phantom body — so
        // the merge is skipped and the spec is left unchanged.
        resetIds()
        val left  = Body(id = "bl", startFromAftMm = 0f,   lengthMm = 200f, diaMm = 60f)
        val right = Body(id = "br", startFromAftMm = 350f, lengthMm = 150f, diaMm = 60f)
        val liner = Liner(id = "ln", startFromAftMm = 250f, lengthMm = 50f, odMm = 70f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(left, right), liners = listOf(liner))

        val result = spec.mergeBodiesAround(200f, 350f, ::nextId)

        assertEquals("no merge — both fragments remain", 2, result.spec.bodies.size)
        assertTrue(result.removedIds.isEmpty())
        assertTrue(result.addedIds.isEmpty())
    }

    @Test
    fun `merge - looser eps absorbs float drift from split-remove cycle`() {
        resetIds()
        // Simulate slight float drift: right body starts at 350.0003 instead of exactly 350
        val left  = Body(id = "bl", startFromAftMm = 0f,      lengthMm = 200f,   diaMm = 60f)
        val right = Body(id = "br", startFromAftMm = 350.0003f, lengthMm = 149.9997f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(left, right))

        val result = spec.mergeBodiesAround(200f, 350f, ::nextId)

        // merge eps = 0.5mm, so 0.0003mm drift must still trigger the merge
        assertEquals("drift-tolerant merge succeeded", 1, result.spec.bodies.size)
    }

    // ── Split then merge round-trip ───────────────────────────────────────────

    @Test
    fun `split then merge round-trips to original body span`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 600f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 600f, bodies = listOf(body))

        // Insert a component at 200..350
        val splitResult = spec.splitBodiesAround(200f, 350f, ::nextId)
        assertEquals(2, splitResult.spec.bodies.size)

        // Remove the component — should restore a single body 0..600
        val mergeResult = splitResult.spec.mergeBodiesAround(200f, 350f, ::nextId)

        assertEquals("single body after merge", 1, mergeResult.spec.bodies.size)
        val restored = mergeResult.spec.bodies[0]
        assertEquals("restored start", 0f, restored.startFromAftMm, 0.001f)
        assertEquals("restored length", 600f, restored.lengthMm, 0.001f)
        assertEquals("restored diameter", 60f, restored.diaMm, 0.001f)
    }

    @Test
    fun `split then merge at aft end round-trips correctly`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val split  = spec.splitBodiesAround(0f, 150f, ::nextId)
        val merged = split.spec.mergeBodiesAround(0f, 150f, ::nextId)

        val restored = merged.spec.bodies[0]
        assertEquals(0f,   restored.startFromAftMm, 0.001f)
        assertEquals(500f, restored.lengthMm,       0.001f)
    }

    @Test
    fun `split then merge at fwd end round-trips correctly`() {
        resetIds()
        val body = Body(id = "b1", startFromAftMm = 0f, lengthMm = 500f, diaMm = 60f)
        val spec = ShaftSpec(overallLengthMm = 500f, bodies = listOf(body))

        val split  = spec.splitBodiesAround(350f, 500f, ::nextId)
        val merged = split.spec.mergeBodiesAround(350f, 500f, ::nextId)

        val restored = merged.spec.bodies[0]
        assertEquals(0f,   restored.startFromAftMm, 0.001f)
        assertEquals(500f, restored.lengthMm,       0.001f)
    }
}
