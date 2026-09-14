package com.android.shaftschematic.ui.nav

import com.android.shaftschematic.ui.viewmodel.SpecTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The PDF preview/export routes carry their drawing as an OPTIONAL route argument. Two things
 * are load-bearing: the round trip (the preview forwards its own target to the export, so a
 * spelling that does not survive it would silently export the original from a final preview),
 * and the fallback — anything unrecognised, a missing argument included, resolves to the
 * ORIGINAL, the drawing that always exists. That is what keeps every existing
 * `navigate("pdfPreview")` working unchanged.
 */
class SpecTargetArgTest {

    @Test
    fun `every target round-trips through its route argument`() {
        SpecTarget.entries.forEach { target ->
            assertEquals(target, specTargetFromArg(targetArg(target)))
        }
    }

    @Test
    fun `a missing or unrecognised argument is the original drawing`() {
        assertEquals(SpecTarget.ORIGINAL, specTargetFromArg(null))
        assertEquals(SpecTarget.ORIGINAL, specTargetFromArg(""))
        assertEquals(SpecTarget.ORIGINAL, specTargetFromArg("Final"))
        assertEquals(SpecTarget.ORIGINAL, specTargetFromArg("nonsense"))
    }
}
