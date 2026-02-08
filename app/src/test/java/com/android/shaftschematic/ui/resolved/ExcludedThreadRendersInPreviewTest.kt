package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.ThreadAttachment
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.ui.presentation.PresentationComponent
import com.android.shaftschematic.ui.presentation.PresentationComponentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedThreadRendersInPreviewTest {

    @Test
    fun excluded_thread_resolves_and_surfaces_in_presentation_components() {
        val spec = ShaftSpec(
            overallLengthMm = 200f,
            threads = listOf(
                Threads(
                    id = "TH",
                    startFromAftMm = 80f,
                    lengthMm = 20f,
                    majorDiaMm = 40f,
                    pitchMm = 2f,
                    excludeFromOAL = true,
                    endAttachment = ThreadAttachment.AFT
                )
            )
        )

        val resolved = resolveComponents(spec)
        val threads = resolved.filterIsInstance<ResolvedThread>()

        assertEquals(1, threads.size)
        assertEquals(0f, threads[0].startMmPhysical, 1e-4f)
        assertEquals(20f, threads[0].endMmPhysical, 1e-4f)
        assertTrue("resolved thread should have non-zero span", threads[0].lengthMm > 0f)

        val presentation = PresentationComponent.fromResolved(
            resolved.filter { it.source != ResolvedComponentSource.DRAFT },
            emptyMap()
        )

        assertTrue(
            "presentation components should include an excluded thread",
            presentation.any { it.kind == PresentationComponentKind.THREAD }
        )
    }
}
