package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolved body fragments must carry unique ids.
 *
 * A stored body overlapped by a taper/thread/liner is trimmed into several drawn fragments.
 * Reusing the stored id for every fragment would make two carousel rows share one id — duplicate
 * `HorizontalPager` keys (a Lazy-layout key collision throws at runtime) and an ambiguous
 * resolved→stored mapping. The first fragment keeps the stored id (so wear pits, runout
 * readings and selection still resolve to the primary fragment) and later fragments get a
 * deterministic `"#2"`, `"#3"`, … suffix.
 *
 * The stored-overlap state is reachable today: [com.android.shaftschematic.model.splitBodiesAround]
 * deliberately never fragments a keyed body, so adding a liner over one leaves a stored body
 * that spans it.
 */
class BodyFragmentIdTest {

    /** A keyed body — the case that is legitimately left overlapping a later-added liner. */
    private fun keyedBody(id: String, startMm: Float, lengthMm: Float, diaMm: Float = 150f) = Body(
        id = id,
        startFromAftMm = startMm,
        lengthMm = lengthMm,
        diaMm = diaMm,
        keywayWidthMm = 12f,
        keywayDepthMm = 6f,
        keywayLengthMm = 80f,
        keywayEnd = LinerAuthoredReference.AFT,
    )

    /** Body 0..600 with a liner over 200..400 — resolves to fragments 0..200 and 400..600. */
    private fun spannedSpec() = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = listOf(keyedBody("body-1", startMm = 0f, lengthMm = 600f)),
        liners = listOf(Liner(id = "liner-1", startFromAftMm = 200f, lengthMm = 200f, odMm = 200f)),
    )

    private fun explicitBodies(resolved: List<ResolvedComponent>) = resolved
        .filterIsInstance<ResolvedBody>()
        .filter { it.source == ResolvedComponentSource.EXPLICIT }

    @Test
    fun `a body spanning a liner resolves into two fragments with unique ids`() {
        val resolved = resolveComponents(spannedSpec())
        val fragments = explicitBodies(resolved).sortedBy { it.startMmPhysical }

        assertEquals(2, fragments.size)
        assertEquals("first fragment keeps the stored id", "body-1", fragments[0].id)
        assertEquals("later fragments are suffixed", "body-1#2", fragments[1].id)
        assertEquals(2, fragments.map { it.id }.toSet().size)

        // The fragments are the drawn spans on either side of the liner.
        assertEquals(0f, fragments[0].startMmPhysical, 0.001f)
        assertEquals(200f, fragments[0].endMmPhysical, 0.001f)
        assertEquals(400f, fragments[1].startMmPhysical, 0.001f)
        assertEquals(600f, fragments[1].endMmPhysical, 0.001f)
    }

    @Test
    fun `no two resolved components share an id`() {
        // Auto spans sit alongside the explicit fragments — the whole list must be key-safe,
        // because the carousel pager keys its pages by resolved id.
        val ids = resolveComponents(spannedSpec()).map { it.id }
        assertEquals("duplicate resolved ids: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `every fragment maps back to the stored body through its base id`() {
        val spec = spannedSpec()
        val fragments = explicitBodies(resolveComponents(spec))

        assertTrue(fragments.isNotEmpty())
        fragments.forEach { frag ->
            assertEquals("body-1", resolvedBodyBaseId(frag.id))
            assertTrue(spec.bodies.any { it.id == resolvedBodyBaseId(frag.id) })
        }
    }

    @Test
    fun `an untrimmed body keeps its stored id verbatim`() {
        val spec = ShaftSpec(
            overallLengthMm = 600f,
            bodies = listOf(Body(id = "body-1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 150f)),
        )
        val body = explicitBodies(resolveComponents(spec)).single()

        assertEquals("body-1", body.id)
        assertFalse("no suffix when nothing was trimmed", body.id.contains('#'))
    }

    @Test
    fun `three fragments number sequentially`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(keyedBody("body-1", startMm = 0f, lengthMm = 900f)),
            liners = listOf(
                Liner(id = "liner-1", startFromAftMm = 200f, lengthMm = 100f, odMm = 200f),
                Liner(id = "liner-2", startFromAftMm = 500f, lengthMm = 100f, odMm = 200f),
            ),
        )
        val ids = explicitBodies(resolveComponents(spec))
            .sortedBy { it.startMmPhysical }
            .map { it.id }

        assertEquals(listOf("body-1", "body-1#2", "body-1#3"), ids)
    }

    @Test
    fun `base id extraction leaves unsuffixed ids alone`() {
        assertEquals("body-1", resolvedBodyBaseId("body-1"))
        assertEquals("auto_body_0.000_500.000", resolvedBodyBaseId("auto_body_0.000_500.000"))
        assertEquals("body-1", resolvedBodyBaseId("body-1#2"))
    }
}
