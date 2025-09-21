// file: com/android/shaftschematic/ui/state/ShaftUiEvent.kt
package com.android.shaftschematic.ui.state

import com.android.shaftschematic.util.UnitSystem

/**
 * Minimal UI event set to keep legacy reducer call sites compiling.
 * Maps 1:1 to the callbacks in ShaftScreen / ViewModel.
 */
sealed interface ShaftUiEvent {
    data class SetUnit(val unit: UnitSystem) : ShaftUiEvent
    data class ToggleGrid(val checked: Boolean) : ShaftUiEvent
    data class SetCustomer(val value: String) : ShaftUiEvent
    data class SetVessel(val value: String) : ShaftUiEvent
    data class SetJobNumber(val value: String) : ShaftUiEvent
    data class SetNotes(val value: String) : ShaftUiEvent
    data class SetOverallLengthRaw(val value: String) : ShaftUiEvent

    object AddComponent : ShaftUiEvent
    object ExportPdf : ShaftUiEvent
}
