package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolveComponents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-section auto-body Ø persistence: envelope round-trip, and decode of documents saved
 * before `autoDiaOverrides` existed. The field is additive and defaulted (no envelope version
 * bump), so a legacy document decodes to an empty list and resolves exactly as it did before
 * the feature. Dormant anchors ride along untouched — nothing is pruned at decode.
 */
class AutoSectionDiaPersistenceTest {

    private fun docWith(spec: ShaftSpec) = ShaftDocCodec.ShaftDocV1(spec = spec)

    private fun autoDiameters(spec: ShaftSpec) =
        resolveComponents(spec)
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }
            .sortedBy { it.startMmPhysical }
            .map { it.startMmPhysical to it.diaMm }

    @Test
    fun `overrides round-trip through the envelope, dormant ones included`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
            autoDiaOverrides = listOf(
                AutoDiaOverride(anchorMm = 200f, diaMm = 101.6001f),  // verbatim, applied
                AutoDiaOverride(anchorMm = 500f, diaMm = 160f),       // dormant (under the liner)
            ),
        )

        val decoded = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(docWith(spec))).spec

        assertEquals(spec.autoDiaOverrides, decoded.autoDiaOverrides)
        assertEquals(101.6001f, decoded.autoDiaOverrides.first().diaMm)
        assertEquals(autoDiameters(spec), autoDiameters(decoded))
    }

    @Test
    fun `a document without the field decodes to an empty list and resolves as before`() {
        val legacy = """
            {
              "version": 1,
              "preferred_unit": "INCHES",
              "spec": {
                "overallLengthMm": 1000.0,
                "liners": [
                  { "id": "l1", "startFromAftMm": 400.0, "lengthMm": 200.0, "odMm": 200.0 }
                ]
              }
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacy).spec

        assertTrue(decoded.autoDiaOverrides.isEmpty())
        assertEquals(
            autoDiameters(
                ShaftSpec(
                    overallLengthMm = 1000f,
                    liners = listOf(Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
                )
            ),
            autoDiameters(decoded),
        )
    }

    @Test
    fun `a legacy spec-only document follows the same default`() {
        val legacySpecOnly = """
            {
              "overallLengthMm": 800.0,
              "autoBodyDiaMm": 150.0
            }
        """.trimIndent()

        val decoded = ShaftDocCodec.decode(legacySpecOnly).spec

        assertTrue(decoded.autoDiaOverrides.isEmpty())
        // The shaft-wide Ø still covers every span when no section overrides it.
        assertEquals(150f, decoded.autoBodyDiaMm)
        assertEquals(listOf(0f to 150f), autoDiameters(decoded))
    }
}
