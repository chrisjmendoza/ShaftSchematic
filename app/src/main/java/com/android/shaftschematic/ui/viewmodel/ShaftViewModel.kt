package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.data.SettingsStore.UnitPref
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * File: ShaftViewModel.kt
 * Layer: ViewModel
 *
 * Purpose
 * Own the current [ShaftSpec] (canonical **mm**) and editor UI state, provide mutation
 * helpers, and persist app settings (default unit + grid). JSON save/load remembers
 * the shaft’s preferred unit and whether unit selection is locked for that document.
 *
 * Contract
 * - Canonical storage and rendering units are **millimeters**. Conversions only at the UI edge.
 * - Save/Load uses a versioned JSON envelope to remain backward compatible.
 * - Public API from previous versions is preserved (adds, updates, removes, flows).
 */
class ShaftViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ─────────────────────────────────────────────────────────────────────────────
    // Reactive state (observed by Compose)
    // ─────────────────────────────────────────────────────────────────────────────

    private val _spec = MutableStateFlow(ShaftSpec())
    val spec: StateFlow<ShaftSpec> = _spec.asStateFlow()

    private val _unit = MutableStateFlow(UnitSystem.MILLIMETERS)
    val unit: StateFlow<UnitSystem> = _unit.asStateFlow()

    private val _unitLocked = MutableStateFlow(false)
    val unitLocked: StateFlow<Boolean> = _unitLocked.asStateFlow()

    private val _showGrid = MutableStateFlow(false)
    val showGrid: StateFlow<Boolean> = _showGrid.asStateFlow()

    private val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer.asStateFlow()

    private val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel.asStateFlow()

    private val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _overallIsManual = MutableStateFlow(false)
    val overallIsManual: StateFlow<Boolean> = _overallIsManual.asStateFlow()
    fun setOverallIsManual(v: Boolean) { _overallIsManual.value = v }

    // NEW: cross-type UI order (stable IDs)
    private val _componentOrder = MutableStateFlow<List<ComponentKey>>(emptyList())
    val componentOrder = _componentOrder.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────────
    // Settings persistence (default unit + show grid)
    // ─────────────────────────────────────────────────────────────────────────────

    init {
        // Observe persisted defaults. Apply only when doc isn't unit-locked.
        viewModelScope.launch {
            SettingsStore.defaultUnitFlow(getApplication()).collectLatest { pref ->
                if (!_unitLocked.value) {
                    val u = if (pref == UnitPref.INCHES) UnitSystem.INCHES else UnitSystem.MILLIMETERS
                    setUnit(u, persist = false) // avoid ping-ponging DataStore
                }
            }
        }
        viewModelScope.launch {
            SettingsStore.showGridFlow(getApplication()).collectLatest { persisted ->
                setShowGrid(persisted, persist = false)
            }
        }
    }

    /** Sets the UI unit (preview/labels only). Model remains canonical mm. */
    fun setUnit(newUnit: UnitSystem, persist: Boolean = true) {
        if (newUnit != _unit.value) _unit.value = newUnit
        if (persist && !_unitLocked.value) {
            viewModelScope.launch {
                val pref = if (newUnit == UnitSystem.INCHES) UnitPref.INCHES else UnitPref.MILLIMETERS
                SettingsStore.setDefaultUnit(getApplication(), pref)
            }
        }
    }

    /** Convenience toggle for quick unit flips (still respects [unitLocked]). */
    fun toggleUnit() {
        if (_unitLocked.value) return
        setUnit(
            if (_unit.value == UnitSystem.MILLIMETERS) UnitSystem.INCHES else UnitSystem.MILLIMETERS
        )
    }

    /** Toggles grid visibility in Preview (persisted in Settings). */
    fun setShowGrid(show: Boolean, persist: Boolean = true) {
        _showGrid.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowGrid(getApplication(), show) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Client metadata (free-form)
    // ─────────────────────────────────────────────────────────────────────────────

    fun setCustomer(value: String) { _customer.value = value.trim() }
    fun setVessel(value: String)   { _vessel.value = value.trim() }
    fun setJobNumber(value: String){ _jobNumber.value = value.trim() }
    fun setNotes(value: String)    { _notes.value = value }

    // ─────────────────────────────────────────────────────────────────────────────
    // Overall length (mm)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Set shaft overall length (mm). Clamped to ≥ 0. */
    fun onSetOverallLengthMm(valueMm: Float) {
        _spec.update { it.copy(overallLengthMm = max(0f, valueMm)) }
    }

    /** Parses text in current UI units and forwards to [onSetOverallLengthMm]. */
    fun setOverallLength(raw: String) {
        val v = raw.replace(",", "").trim().toFloatOrNull() ?: 0f
        val mm = if (_unit.value == UnitSystem.INCHES) v * 25.4f else v
        onSetOverallLengthMm(mm)
    }

    /** Ensure overall length covers all components plus optional free space. */
    fun ensureOverall(minFreeMm: Float = 0f) = _spec.update { s ->
        if (_overallIsManual.value) return@update s
        val end = coverageEndMm(s)
        val minOverall = end + max(0f, minFreeMm)
        if (s.overallLengthMm < minOverall) s.copy(overallLengthMm = minOverall) else s
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Component add/update/remove — newest on top
    // (All params in mm; UI converts at the edge)
    // ─────────────────────────────────────────────────────────────────────────────

    // Bodies
    fun addBody(body: Body) = _spec.update { it.copy(bodies = listOf(body) + it.bodies) }
    fun addBodyAt(startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        val id = newId() // you already have this; keep your actual method
        orderAdd(ComponentKind.BODY, id)
        s.copy(bodies = listOf(Body(id, startMm, max(0f, lengthMm), max(0f, diaMm))) + s.bodies)
    }.also { ensureOverall(); ensureOrderCoversSpec() }
    fun updateBody(id: String, updater: (Body) -> Body) = _spec.update { s ->
        s.copy(bodies = s.bodies.map { if (it.id == id) updater(it) else it })
    }
    fun updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        if (index !in s.bodies.indices) s else s.copy(
            bodies = s.bodies.toMutableList().also { l ->
                val b = l[index]
                l[index] = b.copy(startFromAftMm = startMm, lengthMm = max(0f, lengthMm), diaMm = max(0f, diaMm))
            }
        )
    }.also { ensureOverall() }
    fun removeBodyAt(index: Int) = _spec.update { s ->
        if (index !in s.bodies.indices) s else s.copy(bodies = s.bodies.toMutableList().apply { removeAt(index) })
    }
    fun removeBody(index: Int) {
        _spec.update { s ->
            if (index !in s.bodies.indices) s
            else {
                orderRemove(s.bodies[index].id)
                s.copy(bodies = s.bodies.toMutableList().apply { removeAt(index) })
            }
        }
        ensureOverall(); ensureOrderCoversSpec()
    }

    // Tapers
    fun addTaper(taper: Taper) = _spec.update { it.copy(tapers = listOf(taper) + it.tapers) }
    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.TAPER, id)
        s.copy(tapers = listOf(Taper(id, startMm, max(0f, lengthMm), max(0f, startDiaMm), max(0f, endDiaMm))) + s.tapers)
    }.also { ensureOverall(); ensureOrderCoversSpec() }
    fun updateTaper(id: String, updater: (Taper) -> Taper) = _spec.update { s ->
        s.copy(tapers = s.tapers.map { if (it.id == id) updater(it) else it })
    }
    fun updateTaper(index: Int, startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        Log.d("ShaftVM", "updateTaper[$index] start=$startMm len=$lengthMm set=$startDiaMm let=$endDiaMm")
        if (index !in s.tapers.indices) s else s.copy(
            tapers = s.tapers.toMutableList().also { l ->
                val t = l[index]
                l[index] = t.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm)
                )
            }
        )
    }.also { ensureOverall() }
    fun removeTaperAt(index: Int) = _spec.update { s ->
        if (index !in s.tapers.indices) s else s.copy(tapers = s.tapers.toMutableList().apply { removeAt(index) })
    }
    fun removeTaper(index: Int) {
        _spec.update { s ->
            if (index !in s.tapers.indices) s
            else {
                orderRemove(s.tapers[index].id)
                s.copy(tapers = s.tapers.toMutableList().apply { removeAt(index) })
            }
        }
        ensureOverall(); ensureOrderCoversSpec()
    }

    // Threads
    fun addThread(thread: ThreadSpec) = _spec.update { it.copy(threads = listOf(thread) + it.threads) }
    fun addThreadAt(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.THREAD, id)
        s.copy(threads = listOf(ThreadSpec(id, startMm, max(0f, lengthMm), max(0f, majorDiaMm), max(0f, pitchMm))) + s.threads)
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateThread(id: String, updater: (ThreadSpec) -> ThreadSpec) = _spec.update { s ->
        s.copy(threads = s.threads.map { if (it.id == id) updater(it) else it })
    }
    fun updateThread(index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        if (index !in s.threads.indices) s else s.copy(
            threads = s.threads.toMutableList().also { l ->
                val th = l[index]
                l[index] = th.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm)
                )
            }
        )
    }.also { ensureOverall() }
    fun removeThreadAt(index: Int) = _spec.update { s ->
        if (index !in s.threads.indices) s else s.copy(threads = s.threads.toMutableList().apply { removeAt(index) })
    }
    fun removeThread(index: Int) {
        _spec.update { s ->
            if (index !in s.threads.indices) s
            else {
                orderRemove(s.threads[index].id)
                s.copy(threads = s.threads.toMutableList().apply { removeAt(index) })
            }
        }
        ensureOverall(); ensureOrderCoversSpec()
    }

    // Liners
    fun addLiner(liner: Liner) = _spec.update { it.copy(liners = listOf(liner) + it.liners) }
    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.LINER, id)
        s.copy(liners = listOf(Liner(id, startMm, max(0f, lengthMm), max(0f, odMm))) + s.liners)
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateLiner(id: String, updater: (Liner) -> Liner) = _spec.update { s ->
        s.copy(liners = s.liners.map { if (it.id == id) updater(it) else it })
    }
    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        if (index !in s.liners.indices) s else s.copy(
            liners = s.liners.toMutableList().also { l ->
                val ln = l[index]
                l[index] = ln.copy(startFromAftMm = startMm, lengthMm = max(0f, lengthMm), odMm = max(0f, odMm))
            }
        )
    }.also { ensureOverall() }
    fun removeLinerAt(index: Int) = _spec.update { s ->
        if (index !in s.liners.indices) s else s.copy(liners = s.liners.toMutableList().apply { removeAt(index) })
    }
    fun removeLiner(index: Int) {
        _spec.update { s ->
            if (index !in s.liners.indices) s
            else {
                orderRemove(s.liners[index].id)
                s.copy(liners = s.liners.toMutableList().apply { removeAt(index) })
            }
        }
        ensureOverall(); ensureOrderCoversSpec()
    }

    private fun newId(): String = UUID.randomUUID().toString()

    // ─────────────────────────────────────────────────────────────────────────────
    // New document helper (choose unit, optionally lock)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Start a brand-new shaft using [unit] for UI; lock UI unit if [lockUnit] is true. */
    fun newShaft(unit: UnitSystem, lockUnit: Boolean = true) {
        _spec.value = ShaftSpec() // your canonical blank spec (mm)
        _unitLocked.value = lockUnit
        setUnit(unit) // also updates default setting unless locked; we also persist below
        if (lockUnit) {
            viewModelScope.launch {
                val pref = if (unit == UnitSystem.INCHES) UnitPref.INCHES else UnitPref.MILLIMETERS
                SettingsStore.setDefaultUnit(getApplication(), pref)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Persistence — versioned JSON document (UI wires it to SAF)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * JSON envelope (v1) that remembers the document's preferred UI unit and unitLock flag.
     * The [spec] is always stored in **mm**.
     */
    @Serializable
    private data class ShaftDocV1(
        val version: Int = 1,
        @kotlinx.serialization.SerialName("preferred_unit") val preferredUnit: UnitSystem = UnitSystem.INCHES,
        @kotlinx.serialization.SerialName("unit_locked") val unitLocked: Boolean = true,
        val spec: ShaftSpec
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Export the current state as a JSON string (mm spec + unit metadata). */
    fun exportJson(): String = json.encodeToString(
        ShaftDocV1(
            preferredUnit = _unit.value,
            unitLocked = _unitLocked.value,
            spec = _spec.value
        )
    )

    /**
     * Import a JSON string and replace current state.
     * Tries envelope first, then falls back to legacy (spec-only) files.
     */
    fun importJson(raw: String) {
        // Try new envelope first
        runCatching { json.decodeFromString<ShaftDocV1>(raw) }
            .onSuccess { doc ->
                _spec.value = doc.spec
                _unitLocked.value = doc.unitLocked
                setUnit(doc.preferredUnit, persist = false)
                return
            }

        // Back-compat: older files were just the spec
        runCatching { json.decodeFromString<ShaftSpec>(raw) }
            .onSuccess { legacy ->
                _spec.value = legacy
                _unitLocked.value = false // no lock info in legacy
                // unit falls back to SettingsStore default (observer in init{})
                return
            }
            .onFailure { throw it }
    }

    // --- Dev stubs kept for local testing (filesystem). Prefer SAF in UI.
    fun saveToFile(dir: File, name: String) {
        val f = File(dir, "$name.json")
        f.writeText(exportJson())
    }
    fun loadFromFile(dir: File, name: String) {
        val f = File(dir, "$name.json")
        if (f.exists()) runCatching { importJson(f.readText()) }
    }



    // ─────────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** Last occupied end among all components (mm). */
    private fun coverageEndMm(s: ShaftSpec): Float {
        var end = 0f
        s.bodies.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.tapers.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.threads.forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.liners.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        return end
    }

    private fun orderAdd(kind: ComponentKind, id: String) {
        _componentOrder.update { it + ComponentKey(id, kind) } // append at end
    }

    private fun orderRemove(id: String) {
        _componentOrder.update { list -> list.filterNot { it.id == id } }
    }

    /** Ensure order contains every current component id (append any missing; keep sequence). */
    private fun ensureOrderCoversSpec(s: ShaftSpec = _spec.value) {
        val cur = _componentOrder.value.toMutableList()
        val have = cur.mapTo(mutableSetOf()) { it.id }
        fun addMissing(kind: ComponentKind, ids: List<String>) {
            ids.forEach { if (it !in have) cur += ComponentKey(it, kind) }
        }
        addMissing(ComponentKind.BODY,   s.bodies.map { it.id })
        addMissing(ComponentKind.TAPER,  s.tapers.map { it.id })
        addMissing(ComponentKind.THREAD, s.threads.map { it.id })
        addMissing(ComponentKind.LINER,  s.liners.map { it.id })
        if (cur != _componentOrder.value) _componentOrder.value = cur
    }

    // Public move helpers (used by the screen)
    fun moveComponentUp(id: String)   = moveComponent(id, -1)
    fun moveComponentDown(id: String) = moveComponent(id, +1)
    private fun moveComponent(id: String, delta: Int) {
        _componentOrder.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) return@update list
            val j = (i + delta).coerceIn(0, list.lastIndex)
            if (i == j) return@update list
            val m = list.toMutableList()
            val item = m.removeAt(i)
            m.add(j, item)
            m
        }
    }

}
