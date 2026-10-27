package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AutoDiaOverride
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ShaftSpec.autoDiaOverrides` — per-section bare-shaft diameters for individual auto-body
 * spans. An override applies to the ONE span containing its anchor and beats both the legacy
 * shaft-wide `autoBodyDiaMm` and neighbor derivation; anchors that land elsewhere lie dormant
 * (resolve never prunes — the ViewModel's add/remove seam does, see `AutoDiaOverridePruneTest`),
 * and a section's override never moves a neighbouring section's Ø.
 */
class AutoSectionDiaOverrideTest {

    private fun resolved(spec: ShaftSpec) = resolveComponents(spec)

    private fun autoBodies(spec: ShaftSpec) =
        resolved(spec)
            .filterIsInstance<ResolvedBody>()
            .filter { it.source == ResolvedComponentSource.AUTO }
            .sortedBy { it.startMmPhysical }

    /** OAL 1000 with a liner at 400..600 — two auto spans, 0..400 and 600..1000. */
    private fun twoSectionSpec(vararg overrides: AutoDiaOverride) = ShaftSpec(
        overallLengthMm = 1000f,
        liners = listOf(Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
        autoDiaOverrides = overrides.toList(),
    )

    // ── Scope ────────────────────────────────────────────────────────────────────

    @Test
    fun `an override applies only to the span containing its anchor`() {
        val autos = autoBodies(twoSectionSpec(AutoDiaOverride(anchorMm = 200f, diaMm = 150f)))

        assertEquals(2, autos.size)
        assertEquals(150f, autos[0].diaMm)  // 0..400 — holds the anchor
        assertEquals(200f, autos[1].diaMm)  // 600..1000 — derives from the liner OD
    }

    @Test
    fun `each section can carry its own diameter`() {
        val autos = autoBodies(
            twoSectionSpec(
                AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
                AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
            )
        )

        assertEquals(listOf(150f, 160f), autos.map { it.diaMm })
    }

    // ── Precedence ───────────────────────────────────────────────────────────────

    @Test
    fun `section override beats the shaft-wide diameter, which still covers other spans`() {
        val spec = twoSectionSpec(AutoDiaOverride(anchorMm = 200f, diaMm = 150f))
            .copy(autoBodyDiaMm = 140f)

        val autos = autoBodies(spec)

        assertEquals(150f, autos[0].diaMm)  // section wins
        assertEquals(140f, autos[1].diaMm)  // no override here → shaft-wide value
    }

    @Test
    fun `with neither override a span still derives from neighbors`() {
        val autos = autoBodies(twoSectionSpec())

        assertEquals(listOf(200f, 200f), autos.map { it.diaMm })
    }

    @Test
    fun `aft-most anchor wins inside one span and the rest stay dormant`() {
        val spec = twoSectionSpec(
            AutoDiaOverride(anchorMm = 300f, diaMm = 160f),
            AutoDiaOverride(anchorMm = 100f, diaMm = 150f),
        )

        val autos = autoBodies(spec)

        assertEquals(150f, autos[0].diaMm)          // aft-most anchor
        assertEquals(2, spec.autoDiaOverrides.size) // the fwd one is dormant, not deleted
    }

    // ── Dormancy ─────────────────────────────────────────────────────────────────

    @Test
    fun `an anchor inside a component is never applied`() {
        val spec = twoSectionSpec(AutoDiaOverride(anchorMm = 500f, diaMm = 999f))

        val autos = autoBodies(spec)

        assertEquals(listOf(200f, 200f), autos.map { it.diaMm })
        assertEquals(1, spec.autoDiaOverrides.size)
    }

    @Test
    fun `an explicit body keeps its authored span and the gap beside it stays its own run`() {
        // Body 0..400 abuts the 400..500 auto gap. The body's span is authored, so the gap
        // is never absorbed into its run (an absorbed gap made a shortened body span the
        // whole run again — on-device report): the gap survives as an auto run, and the
        // override anchored inside it applies there.
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 178f)),
            liners = listOf(Liner(id = "l1", startFromAftMm = 500f, lengthMm = 200f, odMm = 220f)),
            autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 450f, diaMm = 150f)),
        )

        val bodies = resolved(spec).filterIsInstance<ResolvedBody>()

        val explicit = bodies.single { it.id == "b1" }
        assertEquals(0f, explicit.startMmPhysical)
        assertEquals(400f, explicit.endMmPhysical)
        assertEquals(178f, explicit.diaMm)
        val gapRun = bodies.single { it.startMmPhysical == 400f }
        assertEquals(500f, gapRun.endMmPhysical)
        assertEquals(150f, gapRun.diaMm)
        assertEquals(1, spec.autoDiaOverrides.size)
    }

    // ── Merge / resurrect (on-device rule: aft is authored first, so aft wins) ───

    @Test
    fun `merging two sections takes the aft override and resurrects the fwd one on a split`() {
        val separated = twoSectionSpec(
            AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
            AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
        )
        assertEquals(listOf(150f, 160f), autoBodies(separated).map { it.diaMm })

        // Delete the separating liner — the two gaps join into one span holding both anchors.
        val merged = separated.copy(liners = emptyList())
        val mergedAutos = autoBodies(merged)
        assertEquals(1, mergedAutos.size)
        assertEquals(0f to 1000f, mergedAutos[0].startMmPhysical to mergedAutos[0].endMmPhysical)
        assertEquals(150f, mergedAutos[0].diaMm)                 // aft-most wins
        assertEquals(2, merged.autoDiaOverrides.size)            // fwd one dormant, not deleted

        // Re-adding a separator splits the run again; the dormant override comes back as authored.
        val split = merged.copy(
            liners = listOf(Liner(id = "l2", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f))
        )
        assertEquals(listOf(150f, 160f), autoBodies(split).map { it.diaMm })
    }

    // ── Continuity carry (normalizeBodies) ───────────────────────────────────────

    @Test
    fun `a section override survives the diameter-continuity carry`() {
        // Without the override the 600..1000 span inherits the explicit body's 178.
        val base = ShaftSpec(
            overallLengthMm = 1000f,
            bodies = listOf(Body(id = "b1", startFromAftMm = 0f, lengthMm = 400f, diaMm = 178f)),
            liners = listOf(Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f)),
        )
        assertEquals(178f, autoBodies(base).single().diaMm)

        val overridden = base.copy(
            autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 800f, diaMm = 150f))
        )
        assertEquals(150f, autoBodies(overridden).single().diaMm)
    }

    @Test
    fun `a section override never leaks forward into the next auto run`() {
        val spec = ShaftSpec(
            overallLengthMm = 1400f,
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f),
                Liner(id = "l2", startFromAftMm = 900f, lengthMm = 200f, odMm = 200f),
            ),
            autoDiaOverrides = listOf(AutoDiaOverride(anchorMm = 200f, diaMm = 150f)),
        )

        val autos = autoBodies(spec)

        assertEquals(3, autos.size)
        assertEquals(150f, autos[0].diaMm)
        assertEquals(200f, autos[1].diaMm)
        assertEquals(200f, autos[2].diaMm)
    }

    // ── Positioning ──────────────────────────────────────────────────────────────

    @Test
    fun `overrides never move span boundaries`() {
        val base = twoSectionSpec()
        val overridden = twoSectionSpec(
            AutoDiaOverride(anchorMm = 200f, diaMm = 150f),
            AutoDiaOverride(anchorMm = 800f, diaMm = 160f),
        )
        val spans = { s: ShaftSpec -> autoBodies(s).map { it.startMmPhysical to it.endMmPhysical } }

        assertEquals(spans(base), spans(overridden))
        assertEquals(
            resolved(base).map { it.startMmPhysical to it.endMmPhysical },
            resolved(overridden).map { it.startMmPhysical to it.endMmPhysical },
        )
    }

    // ── Section isolation (on-device report: one section's edit changed the others) ──

    /**
     * Taper 0..100 (end Ø 100) | auto 100..400 | liner 400..600 (OD 200) | auto 600..1000
     * [| liner 700..800 (OD 250) | auto 800..1000 when [threeSections]]. The first auto span
     * derives from the taper's end Ø (100) and the continuity carry holds every later span at
     * it.
     */
    private fun taperLinerSpec(threeSections: Boolean = false, vararg overrides: AutoDiaOverride) =
        ShaftSpec(
            overallLengthMm = 1000f,
            tapers = listOf(
                Taper(id = "t1", startFromAftMm = 0f, lengthMm = 100f, startDiaMm = 120f, endDiaMm = 100f)
            ),
            liners = buildList {
                add(Liner(id = "l1", startFromAftMm = 400f, lengthMm = 200f, odMm = 200f))
                if (threeSections) add(Liner(id = "l2", startFromAftMm = 700f, lengthMm = 100f, odMm = 250f))
            },
            autoDiaOverrides = overrides.toList(),
        )

    @Test
    fun `without an override the continuity carry holds every section at the first one's Ø`() {
        assertEquals(listOf(100f, 100f), autoBodies(taperLinerSpec()).map { it.diaMm })
    }

    @Test
    fun `an override on the aft section leaves the fwd section unchanged`() {
        val autos = autoBodies(taperLinerSpec(false, AutoDiaOverride(anchorMm = 250f, diaMm = 120f)))

        assertEquals(listOf(120f, 100f), autos.map { it.diaMm })
    }

    @Test
    fun `an override on the fwd section leaves the aft section unchanged`() {
        val autos = autoBodies(taperLinerSpec(false, AutoDiaOverride(anchorMm = 800f, diaMm = 130f)))

        assertEquals(listOf(100f, 130f), autos.map { it.diaMm })
    }

    @Test
    fun `an override on a middle section leaves the first and last at their baseline`() {
        val baseline = autoBodies(taperLinerSpec(threeSections = true)).map { it.diaMm }
        assertEquals(listOf(100f, 100f, 100f), baseline)

        val autos = autoBodies(
            taperLinerSpec(true, AutoDiaOverride(anchorMm = 650f, diaMm = 140f))
        ).map { it.diaMm }

        assertEquals(listOf(baseline[0], 140f, baseline[2]), autos)
    }
}
