package com.android.shaftschematic.model

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.ui.resolved.autoSectionFiberglassOn
import com.android.shaftschematic.ui.resolved.withDemotedBodyFiberglass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fiberglass wrap: a draw-only flag on a stored body, and a shaft-space anchor on a bare-shaft
 * section ([AutoFiberglass], the [AutoBlend] posture). Ticking it switches on the seal areas the
 * wrap seats into; unticking leaves them. It survives a split, a promotion and a demotion, and a
 * document saved before the fields decodes unwrapped.
 */
class FiberglassModelTest {

    /** Liners at 0–200 and 600–800 on a 1000 mm shaft: bare shaft 200–600 and 800–1000. */
    private val base = ShaftSpec(
        overallLengthMm = 1000f,
        liners = listOf(
            Liner(id = "aft", startFromAftMm = 0f, lengthMm = 200f, odMm = 120f),
            Liner(id = "fwd", startFromAftMm = 600f, lengthMm = 200f, odMm = 120f),
        ),
        autoBodyDiaMm = 100f,
    )

    @Test
    fun `ticking a body seeds a seal area at each face that has none, keeping a typed one`() {
        val body = Body(lengthMm = 400f, diaMm = 100f, blendFwdSeal = true, blendFwdSealLenMm = 63.5f)

        val wrapped = body.withFiberglass(on = true, aftSealLenMm = 101.6f, fwdSealLenMm = 101.6f)

        assertTrue(wrapped.fiberglass)
        assertTrue(wrapped.blendAftSeal)
        assertEquals(101.6f, wrapped.blendAftSealLenMm)
        assertTrue(wrapped.blendFwdSeal)
        assertEquals("a seal area already on keeps its typed length", 63.5f, wrapped.blendFwdSealLenMm)
    }

    @Test
    fun `unticking a body leaves its seal areas as they are`() {
        val wrapped = Body(lengthMm = 400f).withFiberglass(true, 101.6f, 101.6f)

        val unwrapped = wrapped.withFiberglass(on = false, aftSealLenMm = 0f, fwdSealLenMm = 0f)

        assertFalse(unwrapped.fiberglass)
        assertTrue(unwrapped.blendAftSeal && unwrapped.blendFwdSeal)
        assertEquals(101.6f, unwrapped.blendAftSealLenMm)
    }

    @Test
    fun `a bare-shaft section is wrapped by an anchor at its midpoint, with its seal areas`() {
        val s = base.withAutoSectionFiberglassAndSeals(200f, 600f, on = true, aftSealLenMm = 76.2f, fwdSealLenMm = 76.2f)

        assertEquals(listOf(AutoFiberglass(400f)), s.autoFiberglass)
        assertTrue(s.autoSectionFiberglass(200f, 600f))
        assertFalse("the other section stays bare", s.autoSectionFiberglass(800f, 1000f))
        val aft = s.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!
        val fwd = s.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.FWD)!!
        assertTrue(aft.seal && fwd.seal)
        assertEquals(76.2f, aft.sealLenMm)
        assertEquals("a seal on a square face stores no blend", 0f, aft.lengthMm)
    }

    @Test
    fun `unticking a section drops its anchor and keeps its seal areas`() {
        val wrapped = base.withAutoSectionFiberglassAndSeals(200f, 600f, true, 76.2f, 76.2f)

        val unwrapped = wrapped.withAutoSectionFiberglassAndSeals(200f, 600f, false, 0f, 0f)

        assertTrue(unwrapped.autoFiberglass.isEmpty())
        assertTrue(unwrapped.autoBlends.autoBlendFor(200f, 600f, LinerAuthoredReference.AFT)!!.seal)
    }

    @Test
    fun `a no-op set returns the same spec`() {
        assertSame(base, base.withAutoSectionFiberglass(200f, 600f, on = false))
        val wrapped = base.withAutoSectionFiberglass(200f, 600f, on = true)
        assertSame(wrapped, wrapped.withAutoSectionFiberglass(200f, 600f, on = true))
    }

    @Test
    fun `a wrap stays on its section when a liner moves, and goes dormant under one`() {
        val wrapped = base.withAutoSectionFiberglass(200f, 600f, on = true)

        val moved = wrapped.copy(liners = listOf(wrapped.liners[0], wrapped.liners[1].copy(startFromAftMm = 650f)))
        assertTrue(moved.autoSectionFiberglass(200f, 650f))

        val covered = wrapped.copy(liners = wrapped.liners + Liner(id = "mid", startFromAftMm = 300f, lengthMm = 200f, odMm = 120f))
        assertEquals("never pruned", wrapped.autoFiberglass, covered.autoFiberglass)
        assertFalse(covered.autoSectionFiberglass(200f, 300f))
        assertFalse(covered.autoSectionFiberglass(500f, 600f))
    }

    @Test
    fun `promotion carries the wrap onto the new body and demotion carries it back`() {
        val wrapped = base.withAutoSectionFiberglass(200f, 600f, on = true)
        assertTrue(wrapped.autoSectionFiberglassOn(200f, 600f))
        assertFalse("a span no auto run fills exactly carries nothing", wrapped.autoSectionFiberglassOn(250f, 600f))

        // Demotion of a wrapped body: the section that fills its span takes the wrap.
        val body = Body(id = "b", startFromAftMm = 800f, lengthMm = 200f, diaMm = 100f, fiberglass = true)
        val demoted = base.withDemotedBodyFiberglass(body)
        assertTrue(demoted.autoSectionFiberglass(800f, 1000f))

        // ... and of an unwrapped one clears a dormant anchor left on that section.
        val dormant = base.withAutoSectionFiberglass(800f, 1000f, on = true)
        assertFalse(dormant.withDemotedBodyFiberglass(body.copy(fiberglass = false)).autoSectionFiberglass(800f, 1000f))
    }

    @Test
    fun `every fragment of a split body stays wrapped`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b", startFromAftMm = 0f, lengthMm = 1000f, diaMm = 100f, fiberglass = true)),
        )
        var n = 0
        val split = spec.splitBodiesAround(400f, 600f) { "frag${n++}" }

        assertTrue(split.spec.bodies.size >= 2)
        assertTrue(split.spec.bodies.all { it.fiberglass })
    }

    @Test
    fun `the fields round-trip, and an older document decodes unwrapped`() {
        val spec = base.copy(
            bodies = listOf(Body(id = "b", startFromAftMm = 800f, lengthMm = 200f, diaMm = 100f, fiberglass = true)),
            autoFiberglass = listOf(AutoFiberglass(400f), AutoFiberglass(700f)),  // the second is dormant
        )
        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))).spec
        assertEquals(spec.autoFiberglass, decoded.autoFiberglass)
        assertTrue(decoded.bodies.single().fiberglass)

        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "bodies": [ { "id": "b", "startFromAftMm": 0.0, "lengthMm": 1000.0, "diaMm": 100.0 } ]
              }
            }
        """.trimIndent()
        val old = ShaftDocCodec.decode(legacy).spec
        assertFalse(old.bodies.single().fiberglass)
        assertTrue(old.autoFiberglass.isEmpty())
    }
}
