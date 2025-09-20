// file: com/android/shaftschematic/ui/viewmodel/ShaftViewModel.kt
package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.data.ShaftRepository
import com.android.shaftschematic.data.NoopShaftRepository
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.util.filterDecimalPermissive
import com.android.shaftschematic.util.parseToMm

class ShaftViewModel(
    private val repo: ShaftRepository = NoopShaftRepository   // <- keeps things working today
) : ViewModel() {

    private val _unit = MutableStateFlow(UnitSystem.MILLIMETERS)
    val unit: StateFlow<UnitSystem> = _unit

    private val _spec = MutableStateFlow(ShaftSpec())
    val spec: StateFlow<ShaftSpec> = _spec

    init {
        // If you don't have storage yet, this just loads an empty spec (no-op repo).
        viewModelScope.launch {
            val loaded = repo.loadSpec()
            _spec.value = ShaftSpecMigrations.ensureIds(loaded)
        }
    }

    /* ---------- Unit ---------- */
    fun setUnit(newUnit: UnitSystem) {
        // do not modify spec values; those stay in mm
        viewModelScope.launch {
            _unit.emit(newUnit) // assuming you have a MutableStateFlow<UnitSystem> _unit
        }
    }

    /* ---------- Overall ---------- */
    fun setOverallLength(raw: String) {
        val mm = parseToMm(raw, unit.value)
        viewModelScope.launch {
            _spec.emit(spec.value.copy(overallLengthMm = mm.toFloat()))
        }
    }

    /* ---------- Adders ---------- */
    fun addBodySegment() { _spec.value = _spec.value.copy(bodies = _spec.value.bodies + Body()) }
    fun addTaper()       { _spec.value = _spec.value.copy(tapers = _spec.value.tapers + Taper()) }
    fun addThread()      { _spec.value = _spec.value.copy(threads = _spec.value.threads + ThreadSpec()) }
    fun addLiner()       { _spec.value = _spec.value.copy(liners = _spec.value.liners + Liner()) }

    /* ---------- Removers by ID ---------- */
    fun removeBodyById(id: String)   { _spec.value = _spec.value.copy(bodies  = _spec.value.bodies.filterNot  { it.id == id }) }
    fun removeTaperById(id: String)  { _spec.value = _spec.value.copy(tapers  = _spec.value.tapers.filterNot  { it.id == id }) }
    fun removeThreadById(id: String) { _spec.value = _spec.value.copy(threads = _spec.value.threads.filterNot { it.id == id }) }
    fun removeLinerById(id: String)  { _spec.value = _spec.value.copy(liners  = _spec.value.liners.filterNot  { it.id == id }) }

    /* ---------- Setters by index ---------- */

    // Bodies
    fun setBody(index: Int, start: String? = null, length: String? = null, dia: String? = null) {
        val u = unit.value
        val cur = spec.value
        val list = cur.bodies.toMutableList()
        val b = list[index]
        val updated = b.copy(
            startFromAftMm = start?.let { parseToMm(it, u).toFloat() } ?: b.startFromAftMm,
            lengthMm      = length?.let { parseToMm(it, u).toFloat() } ?: b.lengthMm,
            diaMm         = dia?.let    { parseToMm(it, u).toFloat() } ?: b.diaMm
        )
        list[index] = updated
        viewModelScope.launch { _spec.emit(cur.copy(bodies = list)) }
    }

    // Tapers
    fun setTaper(index: Int, start: String? = null, length: String? = null, startDia: String? = null, endDia: String? = null) {
        val u = unit.value
        val cur = spec.value
        val list = cur.tapers.toMutableList()
        val t = list[index]
        val updated = t.copy(
            startFromAftMm = start?.let    { parseToMm(it, u).toFloat() } ?: t.startFromAftMm,
            lengthMm       = length?.let   { parseToMm(it, u).toFloat() } ?: t.lengthMm,
            startDiaMm     = startDia?.let { parseToMm(it, u).toFloat() } ?: t.startDiaMm,
            endDiaMm       = endDia?.let   { parseToMm(it, u).toFloat() } ?: t.endDiaMm,
        )
        list[index] = updated
        viewModelScope.launch { _spec.emit(cur.copy(tapers = list)) }
    }

    // Threads
    fun setThread(index: Int, start: String? = null, majorDia: String? = null, pitch: String? = null, length: String? = null) {
        val u = unit.value
        val cur = spec.value
        val list = cur.threads.toMutableList()
        val th = list[index]
        val updated = th.copy(
            startFromAftMm = start?.let    { parseToMm(it, u).toFloat() } ?: th.startFromAftMm,
            majorDiaMm     = majorDia?.let { parseToMm(it, u).toFloat() } ?: th.majorDiaMm,
            pitchMm        = pitch?.let    { parseToMm(it, u).toFloat() } ?: th.pitchMm,
            lengthMm       = length?.let   { parseToMm(it, u).toFloat() } ?: th.lengthMm,
        )
        list[index] = updated
        viewModelScope.launch { _spec.emit(cur.copy(threads = list)) }
    }

    // Liners
    fun setLiner(index: Int, start: String? = null, length: String? = null, od: String? = null) {
        val u = unit.value
        val cur = spec.value
        val list = cur.liners.toMutableList()
        val ln = list[index]
        val updated = ln.copy(
            startFromAftMm = start?.let { parseToMm(it, u).toFloat() } ?: ln.startFromAftMm,
            lengthMm       = length?.let { parseToMm(it, u).toFloat() } ?: ln.lengthMm,
            odMm           = od?.let    { parseToMm(it, u).toFloat() } ?: ln.odMm
        )
        list[index] = updated
        viewModelScope.launch { _spec.emit(cur.copy(liners = list)) }
    }

    /* ---------- Save/Load convenience (wire to UI later) ---------- */
    fun saveCurrentSpec() {
        viewModelScope.launch { repo.saveSpec(_spec.value) }
    }

    fun reloadFromStorage() {
        viewModelScope.launch {
            _spec.value = ShaftSpecMigrations.ensureIds(repo.loadSpec())
        }
    }

    /* ---------- Utility ---------- */
    fun clearAll() { _spec.value = ShaftSpec() }

    fun formatInCurrentUnit(mm: Float, decimals: Int = 3): String {
        val u = unit.value
        val value = if (u == UnitSystem.MILLIMETERS) mm.toDouble()
        else mm.toDouble() / MM_PER_IN
        return buildString {
            append("%.${decimals}f".format(value))
        }
    }

    private fun parseToMm(raw: String): Float {
        // TODO: parse & convert based on _unit.value; placeholder:
        return raw.toFloatOrNull() ?: 0f
    }
}
