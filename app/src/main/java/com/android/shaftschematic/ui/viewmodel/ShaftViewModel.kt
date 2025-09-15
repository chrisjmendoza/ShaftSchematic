package com.android.shaftschematic.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.data.*
import com.android.shaftschematic.ui.drawing.render.ReferenceEnd
import com.android.shaftschematic.util.UnitsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Units { MM, IN }

class ShaftViewModel(private val appContext: Context) : ViewModel() {

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

    // End panels (Aft/Fwd) – ready for PDF
    private val _aftLet = MutableStateFlow("")
    private val _aftSet = MutableStateFlow("")
    private val _aftTaperRate = MutableStateFlow("")
    private val _aftKeyway = MutableStateFlow("")
    private val _aftThreads = MutableStateFlow("")
    val aftLet = _aftLet.asStateFlow()
    val aftSet = _aftSet.asStateFlow()
    val aftTaperRate = _aftTaperRate.asStateFlow()
    val aftKeyway = _aftKeyway.asStateFlow()
    val aftThreads = _aftThreads.asStateFlow()

    private val _fwdLet = MutableStateFlow("")
    private val _fwdSet = MutableStateFlow("")
    private val _fwdTaperRate = MutableStateFlow("")
    private val _fwdKeyway = MutableStateFlow("")
    private val _fwdThreads = MutableStateFlow("")
    val fwdLet = _fwdLet.asStateFlow()
    val fwdSet = _fwdSet.asStateFlow()
    val fwdTaperRate = _fwdTaperRate.asStateFlow()
    val fwdKeyway = _fwdKeyway.asStateFlow()
    val fwdThreads = _fwdThreads.asStateFlow()

    /* ===================== Persist / init ===================== */

    init {
        // Load last-used units at startup
        viewModelScope.launch {
            UnitsStore.flow(appContext).collect { saved ->
                _units.value = saved
            }
        }
    }

    /* ===================== Unit helpers ===================== */

    private fun uiToMm(v: Float): Float =
        if (_units.value == Units.MM) v else v * 25.4f

    fun toUiUnits(mm: Float): Float =
        if (_units.value == Units.MM) mm else mm / 25.4f

    /* ===================== Global toggles ===================== */

    fun setUnits(u: Units) {
        _units.value = u
        // persist selection
        viewModelScope.launch { UnitsStore.save(appContext, u) }
    }
    fun setReferenceEnd(r: ReferenceEnd) { _referenceEnd.value = r }
    fun setShowGrid(show: Boolean) { _showGrid.value = show }

    /* ===================== Meta setters ===================== */

    fun setCustomer(s: String) { _customer.value = s }
    fun setVessel(s: String) { _vessel.value = s }
    fun setJobNumber(s: String) { _jobNumber.value = s }
    fun setSide(s: String) { _side.value = s }
    fun setDate(s: String) { _date.value = s }
    fun setNotes(s: String) { _notes.value = s }

    // End panels setters
    fun setAftLet(s: String) { _aftLet.value = s }
    fun setAftSet(s: String) { _aftSet.value = s }
    fun setAftTaperRate(s: String) { _aftTaperRate.value = s }
    fun setAftKeyway(s: String) { _aftKeyway.value = s }
    fun setAftThreads(s: String) { _aftThreads.value = s }

    fun setFwdLet(s: String) { _fwdLet.value = s }
    fun setFwdSet(s: String) { _fwdSet.value = s }
    fun setFwdTaperRate(s: String) { _fwdTaperRate.value = s }
    fun setFwdKeyway(s: String) { _fwdKeyway.value = s }
    fun setFwdThreads(s: String) { _fwdThreads.value = s }

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

        val newStart = startUi?.let(::uiToMm) ?: cur.startFromAftMm
        val newLen   = lenUi?.let(::uiToMm)   ?: cur.lengthMm
        val (cs, cl) = clampStartLen(newStart, newLen, _spec.value.overallLengthMm)

        val next = cur.copy(
            startFromAftMm = cs,
            lengthMm = cl,
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

        val ns = startUi?.let(::uiToMm) ?: cur.startFromAftMm
        val nl = lenUi?.let(::uiToMm) ?: cur.lengthMm
        val (cs, cl) = clampStartLen(ns, nl, _spec.value.overallLengthMm)

        val next = cur.copy(
            startFromAftMm = cs,
            lengthMm = cl,
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

        val ns = startUi?.let(::uiToMm) ?: cur.startFromAftMm
        val nl = lenUi?.let(::uiToMm) ?: cur.lengthMm
        val (cs, cl) = clampStartLen(ns, nl, _spec.value.overallLengthMm)

        val next = cur.copy(
            startFromAftMm = cs,
            lengthMm = cl,
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

    fun updateLiner(
        index: Int,
        startUi: Float? = null,
        lenUi: Float? = null,
        odUi: Float? = null
    ) {
        val s = _spec.value
        if (index !in s.liners.indices) return
        val cur = s.liners[index]

        val ns = startUi?.let(::uiToMm) ?: cur.startFromAftMm
        val nl = lenUi?.let(::uiToMm) ?: cur.lengthMm
        val (cs, cl) = clampStartLen(ns, nl, _spec.value.overallLengthMm)

        val next = cur.copy(
            startFromAftMm = cs,
            lengthMm = cl,
            odMm = odUi?.let(::uiToMm) ?: cur.odMm
        )
        _spec.value = s.copy(liners = s.liners.toMutableList().also { it[index] = next })
    }

    /* ===================== Helpers ===================== */

    private fun clampStartLen(startMm: Float, lenMm: Float, overallMm: Float): Pair<Float, Float> {
        val s = startMm.coerceIn(0f, overallMm)
        val maxLen = (overallMm - s).coerceAtLeast(0f)
        val l = lenMm.coerceIn(0f, maxLen)
        return s to l
    }
}
