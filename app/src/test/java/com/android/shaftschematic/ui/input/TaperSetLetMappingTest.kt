package com.android.shaftschematic.ui.input

import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Test

class TaperSetLetMappingTest {

    @Test
    fun `AFT taper shows SET on left and binds to startDia`() {
        val taper = Taper(
            startFromAftMm = 0f,
            lengthMm = 200f,
            startDiaMm = 40f,
            endDiaMm = 50f
        )

        val m = taperSetLetMapping(taper, overallLengthMm = 1000f)
        assertEquals(TaperSide.AFT, m.side)
        assertEquals("S.E.T.", m.leftCode)
        assertEquals("L.E.T.", m.rightCode)
        assertEquals(TaperEndProp.START_DIA, m.leftBindsTo)
        assertEquals(TaperEndProp.END_DIA, m.rightBindsTo)
    }

    @Test
    fun `FWD taper shows LET on left and binds to startDia`() {
        val taper = Taper(
            startFromAftMm = 800f,
            lengthMm = 200f,
            startDiaMm = 50f,
            endDiaMm = 40f
        )

        val m = taperSetLetMapping(taper, overallLengthMm = 1000f)
        assertEquals(TaperSide.FWD, m.side)
        assertEquals("L.E.T.", m.leftCode)
        assertEquals("S.E.T.", m.rightCode)
        assertEquals(TaperEndProp.START_DIA, m.leftBindsTo)
        assertEquals(TaperEndProp.END_DIA, m.rightBindsTo)
    }
}
