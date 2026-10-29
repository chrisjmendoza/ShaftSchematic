package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.UndercutReference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The wear deletes' snackbar Undo — the `restoreUndercut` pattern: the exact object back at its
 * old index, a no-op when it is already there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShaftViewModelWearRestoreTest {

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private data class Item(val id: String)

    @Test
    fun `restored items land at their old indices in ascending order`() {
        val list = listOf(Item("b"), Item("d"))
        val back = list.withRestored(
            listOf(IndexedValue(2, Item("c")), IndexedValue(0, Item("a"))),
        ) { it.id }
        assertEquals(listOf("a", "b", "c", "d"), back.map { it.id })
    }

    @Test
    fun `an item already present is not restored twice`() {
        val list = listOf(Item("a"), Item("b"))
        assertEquals(list, list.withRestored(listOf(IndexedValue(0, Item("a")))) { it.id })
    }

    @Test
    fun `an index past the end appends`() {
        val back = listOf(Item("a")).withRestored(listOf(IndexedValue(5, Item("z")))) { it.id }
        assertEquals(listOf("a", "z"), back.map { it.id })
    }

    @Test
    fun `a deleted worn section comes back verbatim at its index`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.addWornSection(100f, 50f, listOf(253.6031f), UndercutReference.AFT_SET)
        val middle = vm.addWornSection(300f, 40f, listOf(250.0f, 249.9f), UndercutReference.FWD_SET)
        vm.addWornSection(500f, 30f, emptyList(), UndercutReference.AFT_SET)
        idle()
        val before = vm.wearRecord.value.wornSections
        val removed = before[1]
        assertEquals(middle, removed.id)

        vm.removeWornSection(middle)
        assertEquals(2, vm.wearRecord.value.wornSections.size)
        vm.restoreWornSection(removed, 1)
        assertEquals(before, vm.wearRecord.value.wornSections)
        vm.restoreWornSection(removed, 1)
        assertEquals("a second restore is a no-op", before, vm.wearRecord.value.wornSections)
    }
}
