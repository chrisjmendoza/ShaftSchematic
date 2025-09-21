package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.ThreadSpec
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseToMm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

/**
 * ShaftViewModel
 *
 * Single source of truth for a shaft specification:
 * - Stores ALL dimensions in millimeters (canonical).
 * - Exposes unit selection for UI formatting only.
 * - Provides "clean" formatting (integers if whole, trimmed decimals otherwise).
 * - Auto-places newly added components at the current coverage end.
 * - Derives "Next start" and "Remaining to end" values for UI hints.
 *
 * NOTE:
 *  - Parsing from raw user text -> mm is delegated to util/Parsing.kt (parseToMm).
 *  - There is NO parseToMm() in this ViewModel anymore (safe to remove).
 *  - UI-only metadata (customer/vessel/job/notes) and preview flags (grid) live
 *    here as simple flows to keep MainActivity minimal and avoid scattering state.
 */
// -----------------------------------------------------------------------------
// #0. UI-only fields (do NOT affect canonical geometry)
//    These are kept here so the UI can read/write them without another store.
//    They are intentionally not part of ShaftSpec.
// -----------------------------------------------------------------------------
private const val DEFAULT_OVERALL_LENGTH_MM = 300f

class ShaftViewModel : ViewModel() {

    /** Selected unit system for display (does NOT change stored mm). */
    private val _unit = MutableStateFlow(UnitSystem.MILLIMETERS)
    val unit: StateFlow<UnitSystem> = _unit.asStateFlow()

    /** Canonical shaft spec (all values in mm). */
    private val _spec = MutableStateFlow(
        ShaftSpec(
            overallLengthMm = DEFAULT_OVERALL_LENGTH_MM,
            bodies = emptyList(),
            tapers = emptyList(),
            threads = emptyList(),
            liners = emptyList()
        )
    )
    val spec: StateFlow<ShaftSpec> = _spec.asStateFlow()

    // UI metadata and preview toggles (not part of ShaftSpec by design)
    private val _customer = MutableStateFlow("")
    private val _vessel = MutableStateFlow("")
    private val _jobNumber = MutableStateFlow("")
    private val _notes = MutableStateFlow("")
    private val _showGrid = MutableStateFlow(false)

    /** Read-only views for the UI layer */
    val customer: StateFlow<String> = _customer.asStateFlow()
    val vessel: StateFlow<String> = _vessel.asStateFlow()
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()
    val notes: StateFlow<String> = _notes.asStateFlow()
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    // -------------------------------------------------------------------------
    // #2. Formatting helpers (display-only)
    // -------------------------------------------------------------------------

    /**
     * Formats a millimeter value for display in the current unit.
     * - Whole numbers => "12"
     * - Decimals trim trailing zeros => "12.340" -> "12.34"
     * - Capped to [maxDecimals] to avoid noisy long decimals (default 3)
     */
    fun formatInCurrentUnit(mm: Float, maxDecimals: Int = 3): String {
        val value =
            if (unit.value == UnitSystem.MILLIMETERS) mm.toDouble() else mm.toDouble() / MM_PER_IN
        return trimZeros(value, maxDecimals)
    }

    /** Double overload (handy for preview/PDF or when you already have Double). */
    fun formatInCurrentUnitD(mm: Double, maxDecimals: Int = 3): String {
        val value = if (unit.value == UnitSystem.MILLIMETERS) mm else mm / MM_PER_IN
        return trimZeros(value, maxDecimals)
    }

    private fun trimZeros(value: Double, maxDecimals: Int): String {
        val s = String.format(Locale.US, "%.${maxDecimals}f", value)
        return s.trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    // -------------------------------------------------------------------------
    // #3. Coverage & placement (derived flows)
    // -------------------------------------------------------------------------

    private fun segmentEnd(startMm: Float, lengthMm: Float): Float = startMm + lengthMm

    private fun coverageEndMmOf(s: ShaftSpec): Float {
        var end = 0f
        s.bodies.forEach { end = max(end, segmentEnd(it.startFromAftMm, it.lengthMm)) }
        s.tapers.forEach { end = max(end, segmentEnd(it.startFromAftMm, it.lengthMm)) }
        s.threads.forEach { end = max(end, segmentEnd(it.startFromAftMm, it.lengthMm)) }
        s.liners.forEach { end = max(end, segmentEnd(it.startFromAftMm, it.lengthMm)) }
        return end
    }

    /** Position where the next added component should start (in mm). */
    val coverageEndMm: StateFlow<Float> = spec
        .map { coverageEndMmOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    /** Remaining length to end of shaft (in mm), floored at 0. */
    val remainingToEndMm: StateFlow<Float> = spec
        .map { s -> (s.overallLengthMm - coverageEndMmOf(s)).coerceAtLeast(0f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // -------------------------------------------------------------------------
    // #4. Unit & general commands
    // -------------------------------------------------------------------------

    /** Switches the display unit. Stored mm values remain unchanged. */
    fun setUnit(newUnit: UnitSystem) {
        viewModelScope.launch { _unit.emit(newUnit) }
    }

    /** Clears the spec to a fresh shaft with default overall length. */
    fun clearAll() {
        viewModelScope.launch {
            _spec.emit(
                ShaftSpec(
                    overallLengthMm = DEFAULT_OVERALL_LENGTH_MM,
                    bodies = emptyList(),
                    tapers = emptyList(),
                    threads = emptyList(),
                    liners = emptyList()
                )
            )
            // UI-only bits also reset to "empty"
            _customer.emit("")
            _vessel.emit("")
            _jobNumber.emit("")
            _notes.emit("")
            _showGrid.emit(false)
        }
    }

    // -------------------------------------------------------------------------
    // #5a. Canonical setters (raw text -> parse to mm -> update spec)
    // -------------------------------------------------------------------------

    /** Overall length (raw text in current unit). */
    fun setOverallLength(raw: String) {
        val mm = parseToMm(raw, unit.value).toFloat()
        _spec.update { it.copy(overallLengthMm = mm) }
    }

    /** Update a Body by index. Only non-null fields are updated. */
    fun setBody(index: Int, start: String? = null, length: String? = null, dia: String? = null) {
        val u = unit.value
        _spec.update { prev ->
            val list = prev.bodies.toMutableList()
            val b = list.getOrNull(index) ?: return@update prev
            val updated = b.copy(
                startFromAftMm = start?.let { parseToMm(it, u).toFloat() } ?: b.startFromAftMm,
                lengthMm = length?.let { parseToMm(it, u).toFloat() } ?: b.lengthMm,
                diaMm = dia?.let { parseToMm(it, u).toFloat() } ?: b.diaMm
            )
            list[index] = updated
            prev.copy(bodies = list)
        }
    }

    /** Update a Taper by index. */
    fun setTaper(
        index: Int,
        start: String? = null,
        length: String? = null,
        startDia: String? = null,
        endDia: String? = null
    ) {
        val u = unit.value
        _spec.update { prev ->
            val list = prev.tapers.toMutableList()
            val t = list.getOrNull(index) ?: return@update prev
            val updated = t.copy(
                startFromAftMm = start?.let { parseToMm(it, u).toFloat() } ?: t.startFromAftMm,
                lengthMm = length?.let { parseToMm(it, u).toFloat() } ?: t.lengthMm,
                startDiaMm = startDia?.let { parseToMm(it, u).toFloat() } ?: t.startDiaMm,
                endDiaMm = endDia?.let { parseToMm(it, u).toFloat() } ?: t.endDiaMm
            )
            list[index] = updated
            prev.copy(tapers = list)
        }
    }

    /** Update a Thread by index. */
    fun setThread(
        index: Int,
        start: String? = null,
        majorDia: String? = null,
        pitch: String? = null,
        length: String? = null
    ) {
        val u = unit.value
        _spec.update { prev ->
            val list = prev.threads.toMutableList()
            val th = list.getOrNull(index) ?: return@update prev
            val updated = th.copy(
                startFromAftMm = start?.let { parseToMm(it, u).toFloat() } ?: th.startFromAftMm,
                majorDiaMm = majorDia?.let { parseToMm(it, u).toFloat() } ?: th.majorDiaMm,
                pitchMm = pitch?.let { parseToMm(it, u).toFloat() } ?: th.pitchMm,
                lengthMm = length?.let { parseToMm(it, u).toFloat() } ?: th.lengthMm
            )
            list[index] = updated
            prev.copy(threads = list)
        }
    }

    /** Update a Liner by index. */
    fun setLiner(index: Int, start: String? = null, length: String? = null, od: String? = null) {
        val u = unit.value
        _spec.update { prev ->
            val list = prev.liners.toMutableList()
            val ln = list.getOrNull(index) ?: return@update prev
            val updated = ln.copy(
                startFromAftMm = start?.let { parseToMm(it, u).toFloat() } ?: ln.startFromAftMm,
                lengthMm = length?.let { parseToMm(it, u).toFloat() } ?: ln.lengthMm,
                odMm = od?.let { parseToMm(it, u).toFloat() } ?: ln.odMm
            )
            list[index] = updated
            prev.copy(liners = list)
        }
    }

    // -------------------------------------------------------------------------
    // #5b. UI-only setters (metadata & preview flags)
    // -------------------------------------------------------------------------

    fun setCustomer(text: String) {
        _customer.value = text
    }

    fun setVessel(text: String) {
        _vessel.value = text
    }

    fun setJobNumber(text: String) {
        _jobNumber.value = text
    }

    fun setNotes(text: String) {
        _notes.value = text
    }

    fun setShowGrid(enabled: Boolean) {
        _showGrid.value = enabled
    }

    // -------------------------------------------------------------------------
    // #6. Removers (by id)
    // -------------------------------------------------------------------------

    fun removeBodyById(id: String) {
        _spec.update { it.copy(bodies = it.bodies.filterNot { b -> b.id == id }) }
    }

    fun removeTaperById(id: String) {
        _spec.update { it.copy(tapers = it.tapers.filterNot { t -> t.id == id }) }
    }

    fun removeThreadById(id: String) {
        _spec.update { it.copy(threads = it.threads.filterNot { th -> th.id == id }) }
    }

    fun removeLinerById(id: String) {
        _spec.update { it.copy(liners = it.liners.filterNot { ln -> ln.id == id }) }
    }

    // -------------------------------------------------------------------------
    // #7. Adders (auto-place at current coverage end)
    // -------------------------------------------------------------------------

    /** Append a Body at the current tail using last known diameter (or 25 mm) and 100 mm length. */
    fun addBodySegment() {
        val cur = _spec.value

        // Compute tail (max end across all components)
        val lastEnd = listOfNotNull(
            cur.bodies.maxOfOrNull { it.startFromAftMm + it.lengthMm },
            cur.tapers.maxOfOrNull { it.startFromAftMm + it.lengthMm },
            cur.liners.maxOfOrNull { it.startFromAftMm + it.lengthMm },
            cur.threads.maxOfOrNull { it.startFromAftMm + it.lengthMm },
        ).maxOrNull() ?: 0f

        // Heuristic for diameter: prefer last body, then taper end, else 25 mm default
        val baseDia = cur.bodies.lastOrNull()?.diaMm
            ?: cur.tapers.lastOrNull()?.endDiaMm
            ?: 25f

        val newLen = 100f // mm
        val newBody = Body(
            startFromAftMm = lastEnd,
            lengthMm = newLen,
            diaMm = baseDia
        )

        val newOverall = max(cur.overallLengthMm, lastEnd + newLen)

        _spec.update {
            it.copy(
                overallLengthMm = newOverall,
                bodies = it.bodies + newBody
            )
        }
    }

    /** Adds a Taper at the end of current coverage. Seeds diameters from last Body. */
    fun addTaper() {
        val s = spec.value
        val start = coverageEndMmOf(s)
        val baseDia = s.bodies.lastOrNull()?.diaMm ?: 25.4f
        val newItem = Taper(
            startFromAftMm = start,
            lengthMm = 25f,
            startDiaMm = baseDia,
            endDiaMm = (baseDia * 0.9f).coerceAtLeast(1f)
        )
        viewModelScope.launch { _spec.emit(s.copy(tapers = s.tapers + newItem)) }
    }

    /** Adds a Thread at the end of current coverage. Seeds majorDia from last Body. */
    fun addThread() {
        val s = spec.value
        val start = coverageEndMmOf(s)
        val major = s.bodies.lastOrNull()?.diaMm ?: 25.4f
        val newItem = ThreadSpec(
            startFromAftMm = start,
            majorDiaMm = major,
            pitchMm = 2f,
            lengthMm = 12f
        )
        viewModelScope.launch { _spec.emit(s.copy(threads = s.threads + newItem)) }
    }

    /** Adds a Liner at the end of current coverage. Seeds OD from last Body. */
    fun addLiner() {
        val s = spec.value
        val start = coverageEndMmOf(s)
        val od = s.bodies.lastOrNull()?.diaMm ?: 30f
        val newItem = Liner(
            startFromAftMm = start,
            lengthMm = 30f,
            odMm = od
        )
        viewModelScope.launch { _spec.emit(s.copy(liners = s.liners + newItem)) }
    }
}
