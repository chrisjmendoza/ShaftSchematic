// file: com/android/shaftschematic/ui/viewmodel/ShaftViewModel.kt
package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

/**
 * ShaftViewModel
 *
 * Single source of truth for:
 * - Shaft geometry spec (canonical millimeters)
 * - Unit system (mm/in)
 * - UI toggles (grid)
 * - Project metadata (job number, customer, vessel, notes)
 *
 * Design notes:
 * - All geometry is stored in **millimeters** inside [ShaftSpec].
 * - UI may display/edit using inches/TPI; the UI layer converts to mm before calling VM APIs.
 * - Setters are **commit-on-blur** friendly: passing an empty string for length is ignored
 *   (to avoid "0" writes while the user is still typing).
 * - All updates are **immutable** (copy + StateFlow.update) so Compose recomposes.
 */
class ShaftViewModel : ViewModel() {

    // ─────────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────────

    private val _spec = MutableStateFlow(ShaftSpec())
    /** Canonical mm-only shaft geometry/state. */
    val spec: StateFlow<ShaftSpec> = _spec.asStateFlow()

    private val _unit = MutableStateFlow(UnitSystem.MILLIMETERS)
    /** Current unit system for labels/input. */
    val unit: StateFlow<UnitSystem> = _unit.asStateFlow()

    private val _showGrid = MutableStateFlow(true)
    /** UI: preview grid toggle. */
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    // Project metadata
    private val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()

    private val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer.asStateFlow()

    private val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────────
    // Simple setters (commit-on-blur friendly)
    // ─────────────────────────────────────────────────────────────────────────────

    fun setUnit(u: UnitSystem) = _unit.update { u }
    fun setShowGrid(show: Boolean) = _showGrid.update { show }

    fun setJobNumber(v: String) = _jobNumber.update { v }
    fun setCustomer(v: String) = _customer.update { v }
    fun setVessel(v: String) = _vessel.update { v }
    fun setNotes(v: String) = _notes.update { v }

    /**
     * Commit overall length from a user string (in current unit).
     * - Empty or invalid input: ignored (no change) to honor commit-on-blur UX.
     */
    fun setOverallLength(raw: String) {
        val u = _unit.value
        val mm = parseUserNumber(raw, u) ?: return // ignore blank/invalid
        _spec.update { cur ->
            val clamped = max(0f, mm)
            val next = cur.copy(overallLengthMm = clamped)
            next
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Append component at an explicit start (mm)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Add a cylindrical body segment at [startMm] of length [lengthMm] with diameter [diaMm] (all mm). */
    fun addBodyAt(startMm: Float, lengthMm: Float, diaMm: Float) {
        val cur = _spec.value
        val next = cur.copy(
            bodies = cur.bodies + Body(startFromAftMm = startMm, lengthMm = lengthMm, diaMm = diaMm)
        )
        _spec.update { ensureOverall(next) }
    }

    /** Add a linear taper (start Ø → end Ø) at [startMm] of length [lengthMm] (all mm). */
    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) {
        val cur = _spec.value
        val next = cur.copy(
            tapers = cur.tapers + Taper(
                startFromAftMm = startMm,
                lengthMm = lengthMm,
                startDiaMm = startDiaMm,
                endDiaMm = endDiaMm
            )
        )
        _spec.update { ensureOverall(next) }
    }

    /** Add a thread segment with [majorDiaMm] and [pitchMm] (pitch is mm; UI converts from TPI when needed). */
    fun addThreadAt(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) {
        val cur = _spec.value
        val next = cur.copy(
            threads = cur.threads + ThreadSpec(
                startFromAftMm = startMm,
                lengthMm = lengthMm,
                majorDiaMm = majorDiaMm,
                pitchMm = pitchMm
            )
        )
        _spec.update { ensureOverall(next) }
    }

    /** Add a liner/sleeve with outer diameter [odMm] at [startMm] (all mm). */
    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) {
        val cur = _spec.value
        val next = cur.copy(
            liners = cur.liners + Liner(
                startFromAftMm = startMm,
                lengthMm = lengthMm,
                odMm = odMm
            )
        )
        _spec.update { ensureOverall(next) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Update existing components by index (commit-on-blur from UI lists)
    // ─────────────────────────────────────────────────────────────────────────────

    fun updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) {
        val cur = _spec.value
        if (index !in cur.bodies.indices) return
        val bodies = cur.bodies.toMutableList()
        bodies[index] = bodies[index].copy(startFromAftMm = startMm, lengthMm = lengthMm, diaMm = diaMm)
        _spec.update { ensureOverall(cur.copy(bodies = bodies)) }
    }

    fun updateTaper(index: Int, startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) {
        val cur = _spec.value
        if (index !in cur.tapers.indices) return
        val tapers = cur.tapers.toMutableList()
        tapers[index] = tapers[index].copy(
            startFromAftMm = startMm,
            lengthMm = lengthMm,
            startDiaMm = startDiaMm,
            endDiaMm = endDiaMm
        )
        _spec.update { ensureOverall(cur.copy(tapers = tapers)) }
    }

    fun updateThread(index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) {
        val cur = _spec.value
        if (index !in cur.threads.indices) return
        val threads = cur.threads.toMutableList()
        threads[index] = threads[index].copy(
            startFromAftMm = startMm,
            lengthMm = lengthMm,
            majorDiaMm = majorDiaMm,
            pitchMm = pitchMm
        )
        _spec.update { ensureOverall(cur.copy(threads = threads)) }
    }

    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) {
        val cur = _spec.value
        if (index !in cur.liners.indices) return
        val liners = cur.liners.toMutableList()
        liners[index] = liners[index].copy(startFromAftMm = startMm, lengthMm = lengthMm, odMm = odMm)
        _spec.update { ensureOverall(cur.copy(liners = liners)) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** Recompute overall = max(current overall, farthest coverage end). */
    private fun ensureOverall(s: ShaftSpec): ShaftSpec {
        val far = s.coverageEndMm()
        val nextOverall = max(s.overallLengthMm, far)
        return if (nextOverall != s.overallLengthMm) s.copy(overallLengthMm = nextOverall) else s
    }

    /** Parse a user-entered number in the current [unit]; returns mm or null for blank/invalid. */
    private fun parseUserNumber(raw: String, unit: UnitSystem): Float? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val v = t.toFloatOrNull() ?: return null
        return when (unit) {
            UnitSystem.MILLIMETERS -> v
            UnitSystem.INCHES -> v * 25.4f
        }
    }
}


