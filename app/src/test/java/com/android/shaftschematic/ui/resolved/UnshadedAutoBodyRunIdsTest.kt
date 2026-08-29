package com.android.shaftschematic.ui.resolved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Shade in Components → Explicit bodies only": which body runs give up the shade fill.
 *
 * The decision has to be made off the RESOLVED list, because `ShaftSpec.bodyForPdf` drops the
 * source when it maps a resolved run to a drawable [com.android.shaftschematic.model.Body] —
 * the ids are the only thing that survives to the draw pass.
 */
class UnshadedAutoBodyRunIdsTest {

    private fun body(id: String, source: ResolvedComponentSource, start: Float, end: Float) =
        ResolvedBody(
            id = id,
            type = if (source == ResolvedComponentSource.AUTO) {
                ResolvedComponentType.BODY_AUTO
            } else {
                ResolvedComponentType.BODY
            },
            source = source,
            startMmPhysical = start,
            endMmPhysical = end,
            diaMm = 152.4f,
        )

    private val resolved = listOf(
        body("auto_body_0_100", ResolvedComponentSource.AUTO, 0f, 100f),
        body("b1", ResolvedComponentSource.EXPLICIT, 100f, 400f),
        body("b1#2", ResolvedComponentSource.EXPLICIT, 500f, 700f),
        body("auto_body_700_900", ResolvedComponentSource.AUTO, 700f, 900f),
        ResolvedLiner(
            id = "ln1",
            startMmPhysical = 400f,
            endMmPhysical = 500f,
            odMm = 165f,
        ),
    )

    @Test
    fun `only auto runs give up the fill`() {
        val ids = unshadedAutoBodyRunIds(resolved, shadedBodies = true, shadeExplicitBodiesOnly = true)
        assertEquals(setOf("auto_body_0_100", "auto_body_700_900"), ids)
    }

    /** Every fragment of a split explicit body keeps the shade — the run ids differ, the author's choice does not. */
    @Test
    fun `explicit fragments keep the fill`() {
        val ids = unshadedAutoBodyRunIds(resolved, shadedBodies = true, shadeExplicitBodiesOnly = true)
        assertTrue("b1" !in ids)
        assertTrue("b1#2" !in ids)
    }

    /** Subtractive only: with the narrowing off, nothing is suppressed. */
    @Test
    fun `narrowing off suppresses nothing`() {
        assertEquals(
            emptySet<String>(),
            unshadedAutoBodyRunIds(resolved, shadedBodies = true, shadeExplicitBodiesOnly = false),
        )
    }

    /** Nothing to narrow when bodies are not shaded at all — the flag must not invent a decision. */
    @Test
    fun `bodies unshaded suppresses nothing`() {
        assertEquals(
            emptySet<String>(),
            unshadedAutoBodyRunIds(resolved, shadedBodies = false, shadeExplicitBodiesOnly = true),
        )
    }

    /** No resolve pass means no auto spans exist; a spec's stored bodies are all authored. */
    @Test
    fun `no resolved list suppresses nothing`() {
        assertEquals(
            emptySet<String>(),
            unshadedAutoBodyRunIds(null, shadedBodies = true, shadeExplicitBodiesOnly = true),
        )
    }
}
