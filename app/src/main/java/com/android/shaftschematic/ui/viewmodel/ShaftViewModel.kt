package com.android.shaftschematic.ui.screen

import androidx.lifecycle.ViewModel
import com.android.shaftschematic.data.*
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShaftViewModel : ViewModel() {

    /* -------------------- Spec state (single source of truth) -------------------- */
    private val _spec = MutableStateFlow(exampleSpec())
    val spec: StateFlow<ShaftSpecMm> = _spec.asStateFlow()

    /* -------------------- Render/UI state -------------------- */
    private val _referenceEnd = MutableStateFlow(ReferenceEnd.AFT)
    val referenceEnd: StateFlow<ReferenceEnd> = _referenceEnd.asStateFlow()

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    /* -------------------- Meta fields (kept separate from data model) -------------------- */
    private val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer.asStateFlow()

    private val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel.asStateFlow()

    private val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    /* -------------------- Mutators -------------------- */

    fun setSpec(newSpec: ShaftSpecMm) { _spec.value = newSpec }

    fun setReferenceEnd(end: ReferenceEnd) { _referenceEnd.value = end }

    fun setShowGrid(show: Boolean) { _showGrid.value = show }

    fun setCustomer(v: String) { _customer.value = v }
    fun setVessel(v: String) { _vessel.value = v }
    fun setJobNumber(v: String) { _jobNumber.value = v }
    fun setNotes(v: String) { _notes.value = v }

    /* -------------------- Example data to start with -------------------- */
    private fun exampleSpec(): ShaftSpecMm {
        return ShaftSpecMm(
            overallLengthMm = 1800f,
            // optional singletons
            aftThread = ThreadSpec(startFromAftMm = 0f, lengthMm = 60f, majorDiaMm = 30f),
            aftTaper = TaperSpec(startFromAftMm = 60f, lengthMm = 120f, startDiaMm = 30f, endDiaMm = 25f),
            forwardTaper = TaperSpec(startFromAftMm = 600f, lengthMm = 100f, startDiaMm = 28f, endDiaMm = 24f),
            // lists
            liners = listOf(
                LinerSpec(startFromAftMm = 280f, lengthMm = 80f, odMm = 35f),
                LinerSpec(startFromAftMm = 800f, lengthMm = 90f, odMm = 36f),
                LinerSpec(startFromAftMm = 1200f, lengthMm = 70f, odMm = 34f)
            ),
            bodies = listOf(
                BodySegmentSpec(startFromAftMm = 180f, lengthMm = 300f, diaMm = 40f, compressed = true, compressionFactor = 0.45f),
                BodySegmentSpec(startFromAftMm = 480f, lengthMm = 600f, diaMm = 38f, compressed = true, compressionFactor = 0.35f),
                BodySegmentSpec(startFromAftMm = 1080f, lengthMm = 700f, diaMm = 36f, compressed = true, compressionFactor = 0.50f)
            )
        )
    }
}
