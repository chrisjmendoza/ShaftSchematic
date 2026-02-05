package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.AuthoredReference
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementDatumTests {

    @Test
    fun fwd_liner_with_excluded_thread_uses_measurement_datum() {
        val spec = ShaftSpec(
            overallLengthMm = 130f,
            threads = listOf(
                Threads(
                    id = "TH",
                    startFromAftMm = 120f,
                    lengthMm = 10f,
                    majorDiaMm = 30f,
                    pitchMm = 1f,
                    excludeFromOAL = true
                )
            ),
            liners = listOf(
                Liner(
                    id = "LN",
                    startFromAftMm = 100f,
                    lengthMm = 20f,
                    odMm = 50f,
                    authoredReference = LinerAuthoredReference.FWD,
                    authoredStartFromFwdMm = 0f,
                    endMmPhysical = 120f
                )
            )
        )

        val resolved = resolveExplicitComponents(spec)
        val liner = resolved.filterIsInstance<ResolvedLiner>().single { it.id == "LN" }

        assertEquals(0f, spec.liners[0].authoredStartFromFwdMm, 1e-4f)
        assertEquals(100f, liner.startMmPhysical, 1e-4f)
        assertEquals(120f, liner.endMmPhysical, 1e-4f)
    }

    @Test
    fun forward_assembly_shifts_with_datum_and_auto_body_absorbs_delta() {
        val spec = ShaftSpec(
            overallLengthMm = 130f,
            threads = listOf(
                Threads(
                    id = "TH",
                    startFromAftMm = 120f,
                    lengthMm = 10f,
                    majorDiaMm = 30f,
                    pitchMm = 1f,
                    excludeFromOAL = true,
                    authoredReference = AuthoredReference.FWD,
                    authoredStartFromFwdMm = 0f
                )
            ),
            tapers = listOf(
                Taper(
                    id = "TP",
                    startFromAftMm = 0f,
                    lengthMm = 20f,
                    startDiaMm = 30f,
                    endDiaMm = 20f,
                    authoredReference = AuthoredReference.FWD,
                    authoredStartFromFwdMm = 10f
                )
            ),
            liners = listOf(
                Liner(
                    id = "LN",
                    startFromAftMm = 60f,
                    lengthMm = 10f,
                    odMm = 40f,
                    authoredReference = LinerAuthoredReference.AFT,
                    endMmPhysical = 70f
                )
            )
        )

        val baseResolved = resolveExplicitComponents(spec)
        val baseAuto = deriveAutoBodies(130f, baseResolved).filterIsInstance<ResolvedBody>()
            .first { it.source == ResolvedComponentSource.AUTO && approx(it.startMmPhysical, 70f) && approx(it.endMmPhysical, 90f) }

        val modified = spec.copy(
            liners = listOf(spec.liners[0].copy(startFromAftMm = 50f, lengthMm = 10f, endMmPhysical = 60f))
        )
        val resolved = resolveExplicitComponents(modified)
        val thread = resolved.filterIsInstance<ResolvedThread>().single { it.id == "TH" }
        val taper = resolved.filterIsInstance<ResolvedTaper>().single { it.id == "TP" }
        val auto = deriveAutoBodies(130f, resolved).filterIsInstance<ResolvedBody>()
            .first { it.source == ResolvedComponentSource.AUTO && approx(it.startMmPhysical, 60f) && approx(it.endMmPhysical, 90f) }

        assertEquals(120f, thread.endMmPhysical, 1e-4f)
        assertEquals(110f, thread.startMmPhysical, 1e-4f)
        assertEquals(110f, taper.endMmPhysical, 1e-4f)
        assertEquals(90f, taper.startMmPhysical, 1e-4f)
        assertEquals(baseAuto.lengthMm + 10f, auto.lengthMm, 1e-4f)
    }

    @Test
    fun aft_references_unaffected_by_thread_exclusion() {
        val spec = ShaftSpec(
            overallLengthMm = 130f,
            threads = listOf(
                Threads(
                    id = "TH",
                    startFromAftMm = 120f,
                    lengthMm = 10f,
                    majorDiaMm = 30f,
                    pitchMm = 1f,
                    excludeFromOAL = false
                )
            ),
            liners = listOf(
                Liner(
                    id = "LN",
                    startFromAftMm = 40f,
                    lengthMm = 10f,
                    odMm = 40f,
                    authoredReference = LinerAuthoredReference.AFT,
                    endMmPhysical = 50f
                )
            )
        )

        val included = resolveExplicitComponents(spec)
            .filterIsInstance<ResolvedLiner>()
            .single { it.id == "LN" }

        val excludedSpec = spec.copy(
            threads = listOf(spec.threads[0].copy(excludeFromOAL = true))
        )
        val excluded = resolveExplicitComponents(excludedSpec)
            .filterIsInstance<ResolvedLiner>()
            .single { it.id == "LN" }

        assertEquals(included.startMmPhysical, excluded.startMmPhysical, 1e-4f)
        assertEquals(included.endMmPhysical, excluded.endMmPhysical, 1e-4f)
    }

    private fun approx(actual: Float, expected: Float, eps: Float = 1e-4f): Boolean =
        kotlin.math.abs(actual - expected) <= eps
}
