package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.android.shaftschematic.data.*
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Units { MM, IN }

class ShaftViewModel : ViewModel() {

    /* ===================== State ===================== */

    private val _spec = MutableStateFlow(
        ShaftSpecMm(
            overallLengthMm = 0f,
            bodies = emptyList(),
            tapers = emptyList(),
            threads = emptyList(),
            liners = emptyList(),
            aftTaper = null,
            forwardTaper = null
        )
    )
    val spec: StateFlow<ShaftSpecMm> = _spec.asStateFlow()

    private val _units = MutableStateFlow(Units.MM)
    val units: StateFlow<Units> = _units.asStateFlow()

    private val _referenceEnd = MutableStateFlow(ReferenceEnd.AFT)
    val referenceEnd: StateFlow<ReferenceEnd> = _referenceEnd.asStateFlow()

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    // Meta (Job/Notes)
    private val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer.asStateFlow()

    private val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel.asStateFlow()

    private val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()

    private val _side = MutableStateFlow("")
    val side: StateFlow<String> = _side.asStateFlow()

    private val _date = MutableStateFlow("")
    val date: StateFlow<String> = _date.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    /* ===================== Unit helpers ===================== */

    private fun uiToMm(v: Float): Float =
        if (_units.value == Units.MM) v else v * 25.4f

    fun toUiUnits(mm: Float): Float =
        if (_units.value == Units.MM) mm else mm / 25.4f

    /* ===================== Global toggles ===================== */

    fun setUnits(u: Units) { _units.value = u }
    fun setReferenceEnd(r: ReferenceEnd) { _referenceEnd.value = r }
    fun setShowGrid(show: Boolean) { _showGrid.value = show }

    /* ===================== Meta setters ===================== */

    fun setCustomer(s: String) { _customer.value = s }
    fun setVessel(s: String) { _vessel.value = s }
    fun setJobNumber(s: String) { _jobNumber.value = s }
    fun setSide(s: String) { _side.value = s }
    fun setDate(s: String) { _date.value = s }
    fun setNotes(s: String) { _notes.value = s }

    /* ===================== Shaft core ===================== */

    /** Expects UI-units; stored internally in mm. */
    fun setOverallLength(vUi: Float) {
        val s = _spec.value
        _spec.value = s.copy(overallLengthMm = uiToMm(vUi).coerceAtLeast(0f))
    }

    /* ===================== Bodies ===================== */

    fun addBody() {
        val s = _spec.value
        _spec.value = s.copy(
            bodies = s.bodies + BodySegmentSpec(
                startFromAftMm = 0f,
                lengthMm = 0f,
                diaMm = 0f,
                compressed = false,
                compressionFactor = 0.5f
            )
        )
    }

    fun removeBody(index: Int) {
        val s = _spec.value
        if (index !in s.bodies.indices) return
        _spec.value = s.copy(bodies = s.bodies.toMutableList().apply { removeAt(index) })
    }

    /** UI-units in, mm stored. */
    fun updateBody(
        index: Int,
        startUi: Float? = null,
        lenUi: Float? = null,
        diaUi: Float? = null,
        compressed: Boolean? = null,
        factor: Float? = null
    ) {
        val s = _spec.value
        if (index !in s.bodies.indices) return
        val cur = s.bodies[index]
        val next = cur.copy(
            startFromAftMm = startUi?.let(::uiToMm) ?: cur.startFromAftMm,
            lengthMm = lenUi?.let(::uiToMm) ?: cur.lengthMm,
            diaMm = diaUi?.let(::uiToMm) ?: cur.diaMm,
            compressed = compressed ?: cur.compressed,
            compressionFactor = factor ?: cur.compressionFactor
        )
        _spec.value = s.copy(bodies = s.bodies.toMutableList().also { it[index] = next })
    }

    /* ===================== Tapers ===================== */

    fun addTaper() {
        val s = _spec.value
        _spec.value = s.copy(
            tapers = s.tapers + TaperSpec(
                startFromAftMm = 0f,
                lengthMm = 0f,
                startDiaMm = 0f,
                endDiaMm = 0f
            )
        )
    }

    fun removeTaper(index: Int) {
        val s = _spec.value
        if (index !in s.tapers.indices) return
        _spec.value = s.copy(tapers = s.tapers.toMutableList().apply { removeAt(index) })
    }

    /** UI-units in, mm stored. */
    fun updateTaper(
        index: Int,
        startUi: Float? = null,
        lenUi: Float? = null,
        startDiaUi: Float? = null,
        endDiaUi: Float? = null
    ) {
        val s = _spec.value
        if (index !in s.tapers.indices) return
        val cur = s.tapers[index]
        val next = cur.copy(
            startFromAftMm = startUi?.let(::uiToMm) ?: cur.startFromAftMm,
            lengthMm = lenUi?.let(::uiToMm) ?: cur.lengthMm,
            startDiaMm = startDiaUi?.let(::uiToMm) ?: cur.startDiaMm,
            endDiaMm = endDiaUi?.let(::uiToMm) ?: cur.endDiaMm
        )
        _spec.value = s.copy(tapers = s.tapers.toMutableList().also { it[index] = next })
    }

    /* ===================== Threads ===================== */

    fun addThread() {
        val s = _spec.value
        _spec.value = s.copy(
            threads = s.threads + ThreadSpec(
                startFromAftMm = 0f,
                lengthMm = 0f,
                majorDiaMm = 0f,
                pitchMm = 0f,
                endLabel = ""
            )
        )
    }

    fun removeThread(index: Int) {
        val s = _spec.value
        if (index !in s.threads.indices) return
        _spec.value = s.copy(threads = s.threads.toMutableList().apply { removeAt(index) })
    }

    /** UI-units in, mm stored. */
    fun updateThread(
        index: Int,
        startUi: Float? = null,
        lenUi: Float? = null,
        majorDiaUi: Float? = null,
        pitchUi: Float? = null,
        endLabel: String? = null
    ) {
        val s = _spec.value
        if (index !in s.threads.indices) return
        val cur = s.threads[index]
        val next = cur.copy(
            startFromAftMm = startUi?.let(::uiToMm) ?: cur.startFromAftMm,
            lengthMm = lenUi?.let(::uiToMm) ?: cur.lengthMm,
            majorDiaMm = majorDiaUi?.let(::uiToMm) ?: cur.majorDiaMm,
            pitchMm = pitchUi?.let(::uiToMm) ?: cur.pitchMm,
            endLabel = endLabel ?: cur.endLabel
        )
        _spec.value = s.copy(threads = s.threads.toMutableList().also { it[index] = next })
    }

    /* ===================== Liners ===================== */

    fun addLiner() {
        val s = _spec.value
        _spec.value = s.copy(
            liners = s.liners + LinerSpec(
                startFromAftMm = 0f,
                lengthMm = 0f,
                odMm = 0f
            )
        )
    }

    fun removeLiner(index: Int) {
        val s = _spec.value
        if (index !in s.liners.indices) return
        _spec.value = s.copy(liners = s.liners.toMutableList().apply { removeAt(index) })
    }

    /** UI-units in, mm stored. */
    fun updateLiner(
        index: Int,
        startUi: Float? = null,
        lenUi: Float? = null,
        odUi: Float? = null
    ) {
        val s = _spec.value
        if (index !in s.liners.indices) return
        val cur = s.liners[index]
        val next = cur.copy(
            startFromAftMm = startUi?.let(::uiToMm) ?: cur.startFromAftMm,
            lengthMm = lenUi?.let(::uiToMm) ?: cur.lengthMm,
            odMm = odUi?.let(::uiToMm) ?: cur.odMm
        )
        _spec.value = s.copy(liners = s.liners.toMutableList().also { it[index] = next })
    }
}
