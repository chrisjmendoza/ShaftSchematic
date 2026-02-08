package com.android.shaftschematic.ui.viewmodel

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.TaperOrientation
import com.android.shaftschematic.ui.resolved.DraftComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftCommitTests {

    @Test
    fun taper_draft_commit_is_lossless() {
        val draft = DraftComponent.Taper(
            id = "T1",
            startMmPhysical = 42.5f,
            lengthMm = 35.75f,
            startDiaMm = 48.25f,
            endDiaMm = 33.5f,
            orientation = TaperOrientation.AFT,
            keywayWidthMm = 3.2f,
            keywayDepthMm = 1.1f,
            keywayLengthMm = 15.5f,
            keywaySpooned = true
        )

        val taper = draftTaperToSpec(draft)

        assertEquals(draft.id, taper.id)
        assertEquals(draft.startMmPhysical, taper.startFromAftMm, 1e-4f)
        assertEquals(draft.lengthMm, taper.lengthMm, 1e-4f)
        assertEquals(draft.startDiaMm, taper.startDiaMm, 1e-4f)
        assertEquals(draft.endDiaMm, taper.endDiaMm, 1e-4f)
        assertEquals(draft.orientation, taper.orientation)
        assertEquals(draft.keywayWidthMm, taper.keywayWidthMm, 1e-4f)
        assertEquals(draft.keywayDepthMm, taper.keywayDepthMm, 1e-4f)
        assertEquals(draft.keywayLengthMm, taper.keywayLengthMm, 1e-4f)
        assertEquals(draft.keywaySpooned, taper.keywaySpooned)
    }

    @Test
    fun taper_orientation_maps_set_let_to_left_right() {
        val setMm = 24.5f
        val letMm = 36.0f

        val aftDraft = DraftComponent.Taper(
            id = "AFT",
            startMmPhysical = 10f,
            lengthMm = 40f,
            startDiaMm = setMm,
            endDiaMm = letMm,
            orientation = TaperOrientation.AFT,
            keywayWidthMm = 0f,
            keywayDepthMm = 0f,
            keywayLengthMm = 0f,
            keywaySpooned = false
        )
        val aft = draftTaperToSpec(aftDraft)

        assertEquals(setMm, aft.startDiaMm, 1e-4f)
        assertEquals(letMm, aft.endDiaMm, 1e-4f)
        assertEquals(TaperOrientation.AFT, aft.orientation)

        val fwdDraft = aftDraft.copy(id = "FWD", orientation = TaperOrientation.FWD)
        val fwd = draftTaperToSpec(fwdDraft)

        assertEquals(letMm, fwd.startDiaMm, 1e-4f)
        assertEquals(setMm, fwd.endDiaMm, 1e-4f)
        assertEquals(TaperOrientation.FWD, fwd.orientation)
    }

    @Test
    fun removing_and_readding_taper_preserves_geometry() {
        val draft = DraftComponent.Taper(
            id = "T2",
            startMmPhysical = 5f,
            lengthMm = 25f,
            startDiaMm = 18f,
            endDiaMm = 30f,
            orientation = TaperOrientation.FWD,
            keywayWidthMm = 1f,
            keywayDepthMm = 0.5f,
            keywayLengthMm = 10f,
            keywaySpooned = false
        )
        val taper = draftTaperToSpec(draft)
        val spec = ShaftSpec(tapers = listOf(taper))

        val removed = spec.copy(tapers = emptyList())
        val readded = removed.copy(tapers = listOf(taper.copy()))

        assertEquals(taper, readded.tapers.single())
    }
}
