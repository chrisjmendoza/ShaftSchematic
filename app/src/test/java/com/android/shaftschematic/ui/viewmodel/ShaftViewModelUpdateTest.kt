package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.withPhysical
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max

/**
 * Tests that component update operations mutate ONLY the targeted component.
 *
 * Component positions are sacred — they must never be changed by anything other
 * than an explicit user action targeting that specific component.  These tests
 * document the invariant: updating component A must not move component B.
 *
 * The tests mirror what each updateX() function does inside _spec.update{}, so they
 * remain fast JVM unit tests with no Android dependencies while still pinning the
 * behaviour to the ViewModel contract.
 */
class ShaftViewModelUpdateTest {

    // ── Liner ────────────────────────────────────────────────────────────────

    @Test
    fun `updating liner start does not move subsequent body`() {
        val liner = Liner(id = "ln1", startFromAftMm = 0f, lengthMm = 100f, odMm = 50f, endMmPhysical = 100f)
        val body  = Body(id  = "b1",  startFromAftMm = 100f, lengthMm = 200f, diaMm = 50f)
        val spec  = ShaftSpec(bodies = listOf(body), liners = listOf(liner), overallLengthMm = 300f)

        // Simulate updateLiner: move liner start to 10mm, shorten to 80mm
        val newStart = 10f
        val newLen   = 80f
        val result = spec.copy(
            liners = spec.liners.toMutableList().also { l ->
                l[0] = liner.withPhysical(startMmPhysical = newStart, lengthMm = newLen, odMm = liner.odMm)
            }
        )

        assertEquals("body start must not change", 100f, result.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, result.bodies[0].lengthMm,       0.001f)
        assertEquals("liner start updated",          10f,  result.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length updated",         80f,  result.liners[0].lengthMm,       0.001f)
    }

    @Test
    fun `updating liner length does not move preceding body`() {
        val body  = Body(id  = "b1",  startFromAftMm = 0f,   lengthMm = 100f, diaMm = 50f)
        val liner = Liner(id = "ln1", startFromAftMm = 100f, lengthMm = 50f,  odMm  = 50f, endMmPhysical = 150f)
        val spec  = ShaftSpec(bodies = listOf(body), liners = listOf(liner), overallLengthMm = 200f)

        val result = spec.copy(
            liners = spec.liners.toMutableList().also { l ->
                l[0] = liner.withPhysical(startMmPhysical = liner.startFromAftMm, lengthMm = 80f, odMm = liner.odMm)
            }
        )

        assertEquals("body start must not change",  0f,   result.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 100f, result.bodies[0].lengthMm,        0.001f)
        assertEquals("liner length updated",        80f,  result.liners[0].lengthMm,        0.001f)
    }

    @Test
    fun `updating liner does not move a taper fwd of it`() {
        val liner = Liner(id = "ln1", startFromAftMm = 0f,   lengthMm = 100f, odMm = 50f, endMmPhysical = 100f)
        val taper = Taper(id = "t1",  startFromAftMm = 150f, lengthMm = 50f,  startDiaMm = 50f, endDiaMm = 40f)
        val spec  = ShaftSpec(liners = listOf(liner), tapers = listOf(taper), overallLengthMm = 300f)

        val result = spec.copy(
            liners = spec.liners.toMutableList().also { l ->
                l[0] = liner.withPhysical(startMmPhysical = 0f, lengthMm = 120f, odMm = liner.odMm)
            }
        )

        assertEquals("taper start must not change", 150f, result.tapers[0].startFromAftMm, 0.001f)
    }

    // ── Body ─────────────────────────────────────────────────────────────────

    @Test
    fun `updating body start does not move subsequent liner`() {
        val body  = Body(id  = "b1",  startFromAftMm = 0f,   lengthMm = 100f, diaMm = 50f)
        val liner = Liner(id = "ln1", startFromAftMm = 100f, lengthMm = 60f,  odMm  = 50f, endMmPhysical = 160f)
        val spec  = ShaftSpec(bodies = listOf(body), liners = listOf(liner), overallLengthMm = 200f)

        val result = spec.copy(
            bodies = spec.bodies.toMutableList().also { list ->
                list[0] = body.copy(startFromAftMm = 0f, lengthMm = max(0f, 80f), diaMm = max(0f, body.diaMm))
            }
        )

        assertEquals("liner start must not change", 100f, result.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length must not change", 60f, result.liners[0].lengthMm,        0.001f)
    }

    @Test
    fun `updating body does not move a taper aft of it`() {
        val taper = Taper(id = "t1", startFromAftMm = 0f,  lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        val body  = Body(id  = "b1", startFromAftMm = 100f, lengthMm = 200f, diaMm = 50f)
        val spec  = ShaftSpec(bodies = listOf(body), tapers = listOf(taper), overallLengthMm = 300f)

        val result = spec.copy(
            bodies = spec.bodies.toMutableList().also { list ->
                list[0] = body.copy(startFromAftMm = 100f, lengthMm = max(0f, 150f), diaMm = max(0f, body.diaMm))
            }
        )

        assertEquals("taper start must not change",  0f,   result.tapers[0].startFromAftMm, 0.001f)
        assertEquals("taper length must not change", 100f, result.tapers[0].lengthMm,        0.001f)
    }

    // ── Taper ────────────────────────────────────────────────────────────────

    @Test
    fun `updating taper does not move fwd body`() {
        val taper = Taper(id = "t1", startFromAftMm = 0f,   lengthMm = 100f, startDiaMm = 60f, endDiaMm = 50f)
        val body  = Body(id  = "b1", startFromAftMm = 100f, lengthMm = 200f, diaMm = 50f)
        val spec  = ShaftSpec(bodies = listOf(body), tapers = listOf(taper), overallLengthMm = 300f)

        val result = spec.copy(
            tapers = spec.tapers.toMutableList().also { list ->
                list[0] = taper.copy(
                    startFromAftMm = 0f,
                    lengthMm = max(0f, 120f),     // extended 20mm
                    startDiaMm = max(0f, 60f),
                    endDiaMm   = max(0f, 50f),
                )
            }
        )

        assertEquals("body start must not change",  100f, result.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, result.bodies[0].lengthMm,        0.001f)
    }

    @Test
    fun `updating taper does not move aft liner`() {
        val liner = Liner(id = "ln1", startFromAftMm = 0f,  lengthMm = 50f, odMm = 50f, endMmPhysical = 50f)
        val taper = Taper(id = "t1",  startFromAftMm = 50f, lengthMm = 100f, startDiaMm = 50f, endDiaMm = 40f)
        val spec  = ShaftSpec(liners = listOf(liner), tapers = listOf(taper), overallLengthMm = 200f)

        val result = spec.copy(
            tapers = spec.tapers.toMutableList().also { list ->
                list[0] = taper.copy(startFromAftMm = 60f, lengthMm = max(0f, 100f), startDiaMm = max(0f, 50f), endDiaMm = max(0f, 40f))
            }
        )

        assertEquals("liner start must not change",  0f,  result.liners[0].startFromAftMm, 0.001f)
        assertEquals("liner length must not change", 50f, result.liners[0].lengthMm,        0.001f)
    }

    // ── Thread ───────────────────────────────────────────────────────────────

    @Test
    fun `updating in-shaft thread does not move adjacent body`() {
        val thread = Threads(id = "th1", startFromAftMm = 0f,   lengthMm = 50f, majorDiaMm = 45f, pitchMm = 2f, excludeFromOAL = false)
        val body   = Body(id   = "b1",  startFromAftMm = 50f,  lengthMm = 200f, diaMm = 50f)
        val spec   = ShaftSpec(bodies = listOf(body), threads = listOf(thread), overallLengthMm = 250f)

        val newLength = max(0f, 60f)
        val effectiveStart = thread.startFromAftMm  // not excluded; use authored start
        val result = spec.copy(
            threads = spec.threads.toMutableList().also { l ->
                l[0] = thread.copy(startFromAftMm = effectiveStart, lengthMm = newLength, majorDiaMm = max(0f, 45f), pitchMm = max(0f, 2f))
            }
        )

        assertEquals("body start must not change",  50f,  result.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change", 200f, result.bodies[0].lengthMm,        0.001f)
    }

    @Test
    fun `updating excluded aft thread does not move any body`() {
        val oal    = 300f
        val thread = Threads(id = "th1", startFromAftMm = -50f, lengthMm = 50f, majorDiaMm = 45f, pitchMm = 2f, excludeFromOAL = true, isAftEnd = true)
        val body   = Body(id   = "b1",   startFromAftMm = 0f,   lengthMm = 200f, diaMm = 50f)
        val spec   = ShaftSpec(bodies = listOf(body), threads = listOf(thread), overallLengthMm = oal)

        val newLength = max(0f, 60f)
        val effectiveStart = if (thread.isAftEnd) -newLength else oal
        val result = spec.copy(
            threads = spec.threads.toMutableList().also { l ->
                l[0] = thread.copy(startFromAftMm = effectiveStart, lengthMm = newLength, majorDiaMm = max(0f, 45f), pitchMm = max(0f, 2f))
            }
        )

        assertEquals("body start must not change",    0f,   result.bodies[0].startFromAftMm, 0.001f)
        assertEquals("body length must not change",   200f, result.bodies[0].lengthMm,        0.001f)
        assertEquals("excluded thread start synced", -60f,  result.threads[0].startFromAftMm, 0.001f)
    }

    // ── Multi-component stability ─────────────────────────────────────────────

    @Test
    fun `updating one of three liners leaves the other two untouched`() {
        val ln1 = Liner(id = "ln1", startFromAftMm = 0f,   lengthMm = 100f, odMm = 50f, endMmPhysical = 100f)
        val ln2 = Liner(id = "ln2", startFromAftMm = 150f, lengthMm = 100f, odMm = 50f, endMmPhysical = 250f)
        val ln3 = Liner(id = "ln3", startFromAftMm = 300f, lengthMm = 100f, odMm = 50f, endMmPhysical = 400f)
        val spec = ShaftSpec(liners = listOf(ln1, ln2, ln3), overallLengthMm = 400f)

        // Update ln2 only
        val result = spec.copy(
            liners = spec.liners.toMutableList().also { l ->
                l[1] = ln2.withPhysical(startMmPhysical = 160f, lengthMm = 90f, odMm = ln2.odMm)
            }
        )

        assertEquals("ln1 start unchanged",  0f,   result.liners[0].startFromAftMm, 0.001f)
        assertEquals("ln1 length unchanged", 100f, result.liners[0].lengthMm,        0.001f)
        assertEquals("ln2 start updated",    160f, result.liners[1].startFromAftMm,  0.001f)
        assertEquals("ln2 length updated",    90f, result.liners[1].lengthMm,         0.001f)
        assertEquals("ln3 start unchanged",  300f, result.liners[2].startFromAftMm,  0.001f)
        assertEquals("ln3 length unchanged", 100f, result.liners[2].lengthMm,         0.001f)
    }

    @Test
    fun `updating aft liner in mixed spec leaves taper body and fwd liner positions unchanged`() {
        val aftLiner  = Liner(id = "aft",   startFromAftMm = 0f,   lengthMm = 80f,  odMm = 50f, endMmPhysical = 80f)
        val taper     = Taper(id = "tp",    startFromAftMm = 80f,  lengthMm = 50f,  startDiaMm = 50f, endDiaMm = 40f)
        val body      = Body( id = "body",  startFromAftMm = 130f, lengthMm = 100f, diaMm = 40f)
        val fwdLiner  = Liner(id = "fwd",   startFromAftMm = 230f, lengthMm = 70f,  odMm = 40f, endMmPhysical = 300f)
        val spec = ShaftSpec(
            liners = listOf(aftLiner, fwdLiner),
            tapers = listOf(taper),
            bodies = listOf(body),
            overallLengthMm = 300f
        )

        // Extend the AFT liner by 20mm — nothing else should move
        val result = spec.copy(
            liners = spec.liners.toMutableList().also { l ->
                l[0] = aftLiner.withPhysical(startMmPhysical = 0f, lengthMm = 100f, odMm = aftLiner.odMm)
            }
        )

        assertEquals("aft liner extended",          100f, result.liners[0].lengthMm,        0.001f)
        assertEquals("taper start unchanged",        80f, result.tapers[0].startFromAftMm,  0.001f)
        assertEquals("body start unchanged",        130f, result.bodies[0].startFromAftMm,  0.001f)
        assertEquals("fwd liner start unchanged",   230f, result.liners[1].startFromAftMm,  0.001f)
    }
}
