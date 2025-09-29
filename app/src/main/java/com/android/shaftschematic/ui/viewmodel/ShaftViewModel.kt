package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * File: ShaftViewModel.kt
 * Layer: ViewModel
 * Purpose: Hold the current ShaftSpec (in mm), expose it as reactive StateFlow for UI,
 *          and provide mutation methods that enforce the mm-only contract.
 *
 * Responsibilities
 *  • Single source of truth: all shaft geometry is stored in mm.
 *  • Expose StateFlow<ShaftSpec> for Compose to observe.
 *  • Provide mutation methods for overall length and component updates.
 *  • Persist/restore ShaftSpec snapshots (tiny save/load stubs for dev/test).
 *
 * Invariants
 *  • All stored geometry is millimeters only.
 *  • UI does unit conversions at the edges (toMmOrNull, formatDisplay).
 *  • Renderer/Layout read only mm values.
 *
 * TODO
 *  • Expand save/load into a proper repository (names, lists, overwrite guards).
 *  • Add undo/redo history.
 *  • Design fuller app navigation (shaft generator is one tool of many).
 *  • Add shaft template maker (generic shafts for quick edit).
 *  • Add Settings page (grid size, shaft coloring, PDF export options).
 */
class ShaftViewModel : ViewModel() {

    // Backing state flow, immutable to observers
    private val _spec = MutableStateFlow(ShaftSpec())
    val spec: StateFlow<ShaftSpec> = _spec

    // UI prefs and project metadata (ShaftRoute consumes these)
    private val _unit = MutableStateFlow(UnitSystem.MILLIMETERS)
    val unit: StateFlow<UnitSystem> = _unit

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid

    private val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer

    private val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel

    private val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes


    // ---- Overall Length ----

    /**
     * Set shaft overall length in mm. Clamped to ≥ 0.
     */
    fun onSetOverallLengthMm(valueMm: Float) {
        _spec.update { cur ->
            cur.copy(overallLengthMm = max(0f, valueMm))
        }
    }

    // ---- Bodies ----

    fun addBody(body: Body) = _spec.update { it.copy(bodies = listOf(body) + it.bodies) }

    fun updateBody(id: String, updater: (Body) -> Body) = _spec.update { s ->
        s.copy(bodies = s.bodies.map { if (it.id == id) updater(it) else it })
    }

    // ---- Tapers ----

    fun addTaper(taper: Taper) = _spec.update { it.copy(tapers = listOf(taper) + it.tapers) }

    fun updateTaper(id: String, updater: (Taper) -> Taper) = _spec.update { s ->
        s.copy(tapers = s.tapers.map { if (it.id == id) updater(it) else it })
    }

    // ---- Threads ----

    fun addThread(thread: ThreadSpec) = _spec.update { it.copy(threads = listOf(thread) + it.threads) }

    fun updateThread(id: String, updater: (ThreadSpec) -> ThreadSpec) = _spec.update { s ->
        s.copy(threads = s.threads.map { if (it.id == id) updater(it) else it })
    }

    // ---- Liners ----

    fun addLiner(liner: Liner) = _spec.update { it.copy(liners = listOf(liner) + it.liners) }

    fun updateLiner(id: String, updater: (Liner) -> Liner) = _spec.update { s ->
        s.copy(liners = s.liners.map { if (it.id == id) updater(it) else it })
    }

    // ---- Utilities ----

    /**
     * Ensure overall length is at least as long as the last occupied end + [minFreeMm].
     * Useful after adding components.
     */
    fun ensureOverall(minFreeMm: Float = 0f) = _spec.update { s ->
        val end = s.coverageEndMm()
        val minOverall = end + max(0f, minFreeMm)
        if (s.overallLengthMm < minOverall) s.copy(overallLengthMm = minOverall) else s
    }

    // ShaftRoute -> setters
    fun setUnit(u: UnitSystem) { _unit.value = u }
    fun setShowGrid(b: Boolean) { _showGrid.value = b }

    fun setCustomer(s: String) { _customer.value = s }
    fun setVessel(s: String)   { _vessel.value = s }
    fun setJobNumber(s: String){ _jobNumber.value = s }
    fun setNotes(s: String)    { _notes.value = s }

    fun setOverallLength(raw: String) {
        val v = raw.replace(",", "").trim().toFloatOrNull() ?: 0f
        val mm = if (_unit.value == UnitSystem.INCHES) v * 25.4f else v
        onSetOverallLengthMm(mm)
    }

    // Adds (prepend so newest appears first; match your prior UX)
    fun addBodyAt(startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        s.copy(
            bodies = listOf(
                Body(
                    id = newId(),
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    diaMm = max(0f, diaMm)
                )
            ) + s.bodies
        )
    }.also { ensureOverall() }

    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        s.copy(
            tapers = listOf(
                Taper(
                    id = newId(),
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm)
                )
            ) + s.tapers
        )
    }.also { ensureOverall() }

    fun addThreadAt(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        s.copy(
            threads = listOf(
                ThreadSpec(
                    id = newId(),
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm)
                )
            ) + s.threads
        )
    }.also { ensureOverall() }

    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        s.copy(
            liners = listOf(
                Liner(
                    id = newId(),
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    odMm = max(0f, odMm)
                )
            ) + s.liners
        )
    }.also { ensureOverall() }

    fun updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        if (index !in s.bodies.indices) s else s.copy(
            bodies = s.bodies.toMutableList().also { list ->
                val b = list[index]
                list[index] = b.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    diaMm = max(0f, diaMm)
                )
            }
        )
    }.also { ensureOverall() }

    fun updateTaper(index: Int, startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        if (index !in s.tapers.indices) s else s.copy(
            tapers = s.tapers.toMutableList().also { list ->
                val t = list[index]
                list[index] = t.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm)
                )
            }
        )
    }.also { ensureOverall() }

    fun updateThread(index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        if (index !in s.threads.indices) s else s.copy(
            threads = s.threads.toMutableList().also { list ->
                val th = list[index]
                list[index] = th.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm)
                )
            }
        )
    }.also { ensureOverall() }

    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        if (index !in s.liners.indices) s else s.copy(
            liners = s.liners.toMutableList().also { list ->
                val ln = list[index]
                list[index] = ln.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    odMm = max(0f, odMm)
                )
            }
        )
    }.also { ensureOverall() }

    private fun newId(): String = UUID.randomUUID().toString()


    // ---- Tiny Save/Load Stubs ----

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Save the current spec to a JSON file in [dir].
     */
    fun saveToFile(dir: File, name: String) {
        val f = File(dir, "$name.json")
        f.writeText(json.encodeToString(_spec.value))
    }

    /**
     * Load a spec from JSON file in [dir], replace current state.
     */
    fun loadFromFile(dir: File, name: String) {
        val f = File(dir, "$name.json")
        if (f.exists()) {
            runCatching {
                json.decodeFromString<ShaftSpec>(f.readText())
            }.onSuccess { loaded ->
                _spec.value = loaded
            }
        }
    }
}
