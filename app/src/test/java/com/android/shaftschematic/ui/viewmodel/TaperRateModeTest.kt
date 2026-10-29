package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The taper's Rate mode is STORED (`Taper.taperRateManual`), so a typed Manual rate survives
 * leaving and reopening the document. Before, the mode was card state seeded from the text: a
 * Manual `1:12` equal to the computed rate came back as Auto, and the next Length edit replaced
 * it with `1:13.029` (emulator QA). Drives the REAL ViewModel and codec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaperRateModeTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun vm() = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        .also { it.onSetOverallLengthMm(2000f); idle() }

    @Test
    fun `the Add dialog's Manual choice is stored on the taper`() {
        val vm = vm()
        vm.addTaperAt(startMm = 0f, lengthMm = 304.8f, startDiaMm = 100f, endDiaMm = 125.4f,
            rateText = "1:12", rateManual = true)
        idle()
        assertEquals(true, vm.spec.value.tapers.single().taperRateManual)
    }

    @Test
    fun `the mode chips store the mode, Auto syncing the text, in one undoable edit`() {
        val vm = vm()
        vm.addTaperAt(startMm = 0f, lengthMm = 304.8f, startDiaMm = 100f, endDiaMm = 125.4f, rateText = "1:10")
        idle()
        assertNull("a dialog that passes no mode leaves the legacy seeding", vm.spec.value.tapers.single().taperRateManual)

        vm.setTaperRateMode(0, manual = true)
        idle()
        assertEquals(true, vm.spec.value.tapers.single().taperRateManual)
        assertEquals("Manual keeps the text", "1:10", vm.spec.value.tapers.single().taperRateText)

        Thread.sleep(SessionHistory.COALESCE_WINDOW_MS + 200L)
        vm.setTaperRateMode(0, manual = false, syncRateText = "1:12")
        idle()
        val t = vm.spec.value.tapers.single()
        assertEquals(false, t.taperRateManual)
        assertEquals("1:12", t.taperRateText)

        vm.undoEdit()
        idle()
        assertEquals(true, vm.spec.value.tapers.single().taperRateManual)
        assertEquals("1:10", vm.spec.value.tapers.single().taperRateText)
    }

    @Test
    fun `a Manual rate survives a save and reopen`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(Taper(id = "t1", startFromAftMm = 0f, lengthMm = 304.8f, startDiaMm = 100f,
                endDiaMm = 125.4f, taperRateText = "1:12", taperRateManual = true)),
        )
        val reopened = ShaftDocCodec.decode(ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))).spec
        assertEquals(true, reopened.tapers.single().taperRateManual)
        assertEquals("1:12", reopened.tapers.single().taperRateText)
    }

    @Test
    fun `a taper saved before the field decodes with no stored mode`() {
        val spec = ShaftSpec(
            overallLengthMm = 2000f,
            tapers = listOf(Taper(id = "t1", startFromAftMm = 0f, lengthMm = 300f, startDiaMm = 100f, endDiaMm = 125f)),
        )
        val json = ShaftDocCodec.encodeV1(ShaftDocCodec.ShaftDocV1(spec = spec))
            .replace(Regex(""","taperRateManual":null"""), "")
        assertNull(ShaftDocCodec.decode(json).spec.tapers.single().taperRateManual)
    }
}
