package com.android.shaftschematic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShaftPositionTest {
    @Test
    fun uiLabel_matchesSpec() {
        assertEquals("PORT", ShaftPosition.PORT.uiLabel())
        assertEquals("STBD", ShaftPosition.STBD.uiLabel())
        assertEquals("Center", ShaftPosition.CENTER.uiLabel())
        assertEquals("Other", ShaftPosition.OTHER.uiLabel())
    }

    @Test
    fun printableLabelOrNull_capsAndSuppressesOther() {
        assertEquals("PORT", ShaftPosition.PORT.printableLabelOrNull())
        assertEquals("STBD", ShaftPosition.STBD.printableLabelOrNull())
        assertEquals("CENTER", ShaftPosition.CENTER.printableLabelOrNull())
        assertNull(ShaftPosition.OTHER.printableLabelOrNull())
    }
}
