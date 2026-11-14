package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.SheetDateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The start card's "Date on sheets" on the REAL [ShaftViewModel]: a change marks the document
 * dirty (both values ride the autosave combine, so the live snapshot sees them), the typed text
 * is stored verbatim, both survive save + reopen, and every document boundary that clears the
 * job — New and a template — puts the date back to Today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelSheetDateTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun savedVm(): ShaftViewModel {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.onSetOverallLengthMm(3000f)
        idle()
        vm.markDocumentSaved()
        idle()
        assertFalse(vm.hasUnsavedChanges.value)
        return vm
    }

    @Test
    fun `choosing a mode marks the document dirty`() {
        val vm = savedVm()

        vm.setSheetDateMode(SheetDateMode.NONE)
        idle()

        assertEquals(SheetDateMode.NONE, vm.sheetDateMode.value)
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `typing a date marks the document dirty and is stored verbatim`() {
        val vm = savedVm()

        vm.setSheetDateText(" 11/6/2026 ")
        idle()

        assertEquals(" 11/6/2026 ", vm.sheetDateText.value)
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `the date survives save and reopen`() {
        val vm = savedVm()
        vm.setSheetDateMode(SheetDateMode.CUSTOM)
        vm.setSheetDateText("11/6/2026")
        idle()

        val raw = vm.exportJson()
        val decoded = ShaftDocCodec.decode(raw)
        assertEquals(SheetDateMode.CUSTOM, decoded.sheetDateMode)
        assertEquals("11/6/2026", decoded.sheetDateText)

        val reopened = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        reopened.importJson(raw)
        idle()

        assertEquals(SheetDateMode.CUSTOM, reopened.sheetDateMode.value)
        assertEquals("11/6/2026", reopened.sheetDateText.value)
        assertFalse("a freshly opened document is clean", reopened.hasUnsavedChanges.value)
    }

    @Test
    fun `newDocument puts the date back to Today`() {
        val vm = savedVm()
        vm.setSheetDateMode(SheetDateMode.CUSTOM)
        vm.setSheetDateText("11/6/2026")
        idle()

        vm.newDocument()
        idle()

        assertEquals(SheetDateMode.TODAY, vm.sheetDateMode.value)
        assertEquals("", vm.sheetDateText.value)
    }

    @Test
    fun `a template starts the date at Today whatever the file carries`() {
        val vm = savedVm()
        val raw = ShaftDocCodec.encodeV1(
            ShaftDocCodec.ShaftDocV1(
                spec = vm.spec.value,
                sheetDateMode = SheetDateMode.CUSTOM,
                sheetDateText = "11/6/2026",
            )
        )

        vm.applyTemplate(raw)
        idle()

        assertEquals(SheetDateMode.TODAY, vm.sheetDateMode.value)
        assertEquals("", vm.sheetDateText.value)
    }
}
