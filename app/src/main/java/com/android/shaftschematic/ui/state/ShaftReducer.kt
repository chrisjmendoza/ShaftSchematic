// file: com/android/shaftschematic/ui/state/ShaftReducer.kt
package com.android.shaftschematic.ui.state

import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * Minimal reducer that delegates events to the ViewModel and side-effect lambdas.
 *
 * NOTE:
 * - 'onExportPdf' is kept as a lambda so the Route/Screen can decide SAF vs FileProvider.
 * - If you no longer dispatch events, you can remove this file and call VM methods directly.
 */
object ShaftReducer {

    fun handle(
        vm: ShaftViewModel,
        event: ShaftUiEvent,
        onExportPdf: () -> Unit
    ) {
        when (event) {
            is ShaftUiEvent.SetUnit -> vm.setUnit(event.unit)
            is ShaftUiEvent.ToggleGrid -> vm.setShowGrid(event.checked)
            is ShaftUiEvent.SetCustomer -> vm.setCustomer(event.value)
            is ShaftUiEvent.SetVessel -> vm.setVessel(event.value)
            is ShaftUiEvent.SetJobNumber -> vm.setJobNumber(event.value)
            is ShaftUiEvent.SetNotes -> vm.setNotes(event.value)
            is ShaftUiEvent.SetOverallLengthRaw -> vm.setOverallLength(event.value)

            ShaftUiEvent.AddComponent -> vm.addBodySegment()
            ShaftUiEvent.ExportPdf -> onExportPdf()
        }
    }
}
