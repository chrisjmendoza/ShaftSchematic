package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.data.SettingsStore.UnitPref
import com.android.shaftschematic.model.*
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.max

// Internal payload used by single-step Undo.
// Not part of the public API; safe to change as the undo feature evolves.
private sealed class LastDeleted {
    abstract val id: String
    abstract val kind: ComponentKind
    abstract val orderIndex: Int

    data class Body(
        val value: com.android.shaftschematic.model.Body,
        override val orderIndex: Int,
        val listIndex: Int,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.BODY
    }

    data class Taper(
        val value: com.android.shaftschematic.model.Taper,
        override val orderIndex: Int,
        val listIndex: Int,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.TAPER
    }

    data class Thread(
        val value: com.android.shaftschematic.model.Threads,
        override val orderIndex: Int,
        val listIndex: Int,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.THREAD
    }

    data class Liner(
        val value: com.android.shaftschematic.model.Liner,
        override val orderIndex: Int,
        val listIndex: Int,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.LINER
    }
}

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
 * • Canonical storage and rendering units are **millimeters**. Convert only at the UI edge.
 * • Save/Load uses a versioned JSON envelope to remain backward compatible.
 * • Public API favored by the UI: index-based add/update/remove and newest-first UI order.
 */
class ShaftViewModel(application: Application) : AndroidViewModel(application) {

    // ────────────────────────────────────────────────────────────────────────────
    // Reactive state (observed by Compose)
    // ────────────────────────────────────────────────────────────────────────────

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

    // Cross-type UI order (stable IDs) — source of truth for list rendering (newest-first).
    private val _componentOrder = MutableStateFlow<List<ComponentKey>>(emptyList())
    val componentOrder: StateFlow<List<ComponentKey>> = _componentOrder.asStateFlow()

    // One-shot UI events (snackbars, etc.)
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Single-step deletion buffer. Only the last delete is undoable (v1).
    private var lastDeleted: LastDeleted? = null

    // ────────────────────────────────────────────────────────────────────────────
    // Settings persistence (default unit + show grid)
    // ────────────────────────────────────────────────────────────────────────────

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

    /** Toggles grid visibility in Preview (persisted in Settings). */
    fun setShowGrid(show: Boolean, persist: Boolean = true) {
        _showGrid.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowGrid(getApplication(), show) }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Client metadata (free-form)
    // ────────────────────────────────────────────────────────────────────────────

    fun setCustomer(value: String) { _customer.value = value.trim() }
    fun setVessel(value: String)   { _vessel.value = value.trim() }
    fun setJobNumber(value: String){ _jobNumber.value = value.trim() }
    fun setNotes(value: String)    { _notes.value = value }

    // ────────────────────────────────────────────────────────────────────────────
    // Overall length (mm)
    // ────────────────────────────────────────────────────────────────────────────

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

    /**
     * Ensure overall length covers all components (plus optional free space).
     * No-op when user has explicitly set overall (manual mode).
     */
    fun ensureOverall(minFreeMm: Float = 0f) = _spec.update { s ->
        if (_overallIsManual.value) return@update s
        val end = coverageEndMm(s)
        val minOverall = end + max(0f, minFreeMm)
        if (s.overallLengthMm < minOverall) s.copy(overallLengthMm = minOverall) else s
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Component add/update/remove — newest on top (all params in mm)
    // ────────────────────────────────────────────────────────────────────────────

    // Bodies
    fun addBodyAt(startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.BODY, id)
        s.copy(bodies = listOf(Body(id, startMm, max(0f, lengthMm), max(0f, diaMm))) + s.bodies)
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        if (index !in s.bodies.indices) s else s.copy(
            bodies = s.bodies.toMutableList().also { l ->
                val b = l[index]
                l[index] = b.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    diaMm = max(0f, diaMm)
                )
            }
        )
    }.also { ensureOverall() }

    /**
     * Remove a [Body] by its stable [id].
     *
     * The removed component becomes the only undoable item until replaced by another delete.
     */
    fun removeBody(id: String) {
        var deleted: LastDeleted.Body? = null

        _spec.update { s ->
            val idx = s.bodies.indexOfFirst { it.id == id }
            if (idx < 0) return@update s

            val body = s.bodies[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

            deleted = LastDeleted.Body(
                value = body,
                orderIndex = orderIdx,
                listIndex = idx
            )

            // Remove from UI order first, then from the spec list.
            orderRemove(id)

            s.copy(
                bodies = s.bodies.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let {
            lastDeleted = it
            ensureOverall()
            ensureOrderCoversSpec()
            emitDeletedSnack(it.kind)
        }
    }

    // Tapers
    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.TAPER, id)
        s.copy(
            tapers = listOf(
                Taper(id, startMm, max(0f, lengthMm), max(0f, startDiaMm), max(0f, endDiaMm))
            ) + s.tapers
        )
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateTaper(index: Int, startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
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

    /** Remove a [Taper] by id with single-step undo support. */
    fun removeTaper(id: String) {
        var deleted: LastDeleted.Taper? = null

        _spec.update { s ->
            val idx = s.tapers.indexOfFirst { it.id == id }
            if (idx < 0) return@update s

            val taper = s.tapers[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

            deleted = LastDeleted.Taper(
                value = taper,
                orderIndex = orderIdx,
                listIndex = idx
            )

            orderRemove(id)

            s.copy(
                tapers = s.tapers.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let {
            lastDeleted = it
            ensureOverall()
            ensureOrderCoversSpec()
            emitDeletedSnack(it.kind)
        }
    }

    // Threads
    /**
     * Adds a thread segment.
     *
     * Parameters (mm):
     *  • startMm — axial start from aft face
     *  • lengthMm — axial length
     *  • majorDiaMm — major diameter
     *  • pitchMm — pitch in mm (e.g., 4 TPI ⇒ 6.35 mm)
     *
     * UI contract: Screen & Route pass arguments in exactly this order
     * to prevent pitch/major swaps (see ShaftRoute.onAddThread).
     */
    fun addThreadAt(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.THREAD, id)
        s.copy(
            threads = listOf(
                Threads(id, startMm, max(0f, lengthMm), max(0f, majorDiaMm), max(0f, pitchMm))
            ) + s.threads
        )
    }.also { ensureOverall(); ensureOrderCoversSpec() }

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

    /** Remove a [Threads] segment by id with single-step undo support. */
    fun removeThread(id: String) {
        var deleted: LastDeleted.Thread? = null

        _spec.update { s ->
            val idx = s.threads.indexOfFirst { it.id == id }
            if (idx < 0) return@update s

            val thread = s.threads[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

            deleted = LastDeleted.Thread(
                value = thread,
                orderIndex = orderIdx,
                listIndex = idx
            )

            orderRemove(id)

            s.copy(
                threads = s.threads.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let {
            lastDeleted = it
            ensureOverall()
            ensureOrderCoversSpec()
            emitDeletedSnack(it.kind)
        }
    }

    // Liners
    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.LINER, id)
        s.copy(liners = listOf(Liner(id, startMm, max(0f, lengthMm), max(0f, odMm))) + s.liners)
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        if (index !in s.liners.indices) s else s.copy(
            liners = s.liners.toMutableList().also { l ->
                val ln = l[index]
                l[index] = ln.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    odMm = max(0f, odMm)
                )
            }
        )
    }.also { ensureOverall() }

    /** Remove a [Liner] by id with single-step undo support. */
    fun removeLiner(id: String) {
        var deleted: LastDeleted.Liner? = null

        _spec.update { s ->
            val idx = s.liners.indexOfFirst { it.id == id }
            if (idx < 0) return@update s

            val liner = s.liners[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

            deleted = LastDeleted.Liner(
                value = liner,
                orderIndex = orderIdx,
                listIndex = idx
            )

            orderRemove(id)

            s.copy(
                liners = s.liners.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let {
            lastDeleted = it
            ensureOverall()
            ensureOrderCoversSpec()
            emitDeletedSnack(it.kind)
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    // ────────────────────────────────────────────────────────────────────────────
    // New document helper (choose unit, optionally lock)
    // ────────────────────────────────────────────────────────────────────────────

    /** Start a brand-new shaft using [unit] for UI; lock UI unit if [lockUnit] is true. */
    fun newShaft(unit: UnitSystem, lockUnit: Boolean = true) {
        _spec.value = ShaftSpec()
        _componentOrder.value = emptyList() // fresh doc → empty order list
        _unitLocked.value = lockUnit
        setUnit(unit)
        if (lockUnit) {
            viewModelScope.launch {
                val pref = if (unit == UnitSystem.INCHES) UnitPref.INCHES else UnitPref.MILLIMETERS
                SettingsStore.setDefaultUnit(getApplication(), pref)
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Persistence — versioned JSON document (UI wires it to SAF)
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * JSON envelope (v1) that remembers the document's preferred UI unit and unitLock flag.
     * The [spec] is always stored in **mm**.
     */
    @Serializable
    private data class ShaftDocV1(
        val version: Int = 1,
        @kotlinx.serialization.SerialName("preferred_unit")
        val preferredUnit: UnitSystem = UnitSystem.INCHES,
        @kotlinx.serialization.SerialName("unit_locked")
        val unitLocked: Boolean = true,
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
     * Seeds/repairs UI order to reflect loaded spec.
     */
    fun importJson(raw: String) {
        // Try new envelope first
        runCatching { json.decodeFromString<ShaftDocV1>(raw) }
            .onSuccess { doc ->
                _spec.value = doc.spec
                _unitLocked.value = doc.unitLocked
                setUnit(doc.preferredUnit, persist = false)
                ensureOrderCoversSpec(doc.spec) // keep UI order in sync with loaded spec
                return
            }
        // Back-compat: older files were just the spec
        runCatching { json.decodeFromString<ShaftSpec>(raw) }
            .onSuccess { legacy ->
                _spec.value = legacy
                _unitLocked.value = false // no lock info in legacy
                // unit falls back to SettingsStore default (observer in init{})
                ensureOrderCoversSpec(legacy) // seed order for legacy documents
                return
            }
            .onFailure { throw it }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────────────────

    /** Last occupied end among all components (mm). */
    private fun coverageEndMm(s: ShaftSpec): Float {
        var end = 0f
        s.bodies.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.tapers.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.threads.forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.liners.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        return end
    }

    /**
     * Record a newly-created component at the top of the cross-type order.
     * Rationale: the editor list is newest-first; prepending preserves that mental model.
     */
    private fun orderAdd(kind: ComponentKind, id: String) {
        _componentOrder.update { current -> listOf(ComponentKey(id, kind)) + current }
    }

    /** Remove a component id from the cross-type order (e.g., after deletion). */
    private fun orderRemove(id: String) {
        _componentOrder.update { list -> list.filterNot { it.id == id } }
    }

    /**
     * Ensure the UI order contains every current component id (append any missing; keep sequence).
     * Needed on load/import to seed order for legacy docs or externally-edited specs.
     */
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

    // Move controls (used by screen buttons; future enhancement may expose drag-drop)
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

    /** Emits a deletion snackbar request for the given [ComponentKind]. */
    private fun emitDeletedSnack(kind: ComponentKind) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowDeletedSnack(kind))
        }
    }

    /**
     * Undo the most recent delete, if any.
     *
     * Restores both the spec list entry and the cross-type UI order at their original positions.
     * If the component is already present (id collision), this is a no-op.
     */
    fun undoLastDelete() {
        val snapshot = lastDeleted ?: return
        lastDeleted = null

        // 1) Restore into the spec
        _spec.update { s ->
            // If somehow the id already exists, do not re-add to avoid duplicates.
            if (when (snapshot) {
                    is LastDeleted.Body   -> s.bodies.any   { it.id == snapshot.id }
                    is LastDeleted.Taper  -> s.tapers.any   { it.id == snapshot.id }
                    is LastDeleted.Thread -> s.threads.any  { it.id == snapshot.id }
                    is LastDeleted.Liner  -> s.liners.any   { it.id == snapshot.id }
                }
            ) {
                return@update s
            }

            when (snapshot) {
                is LastDeleted.Body -> {
                    val list = s.bodies.toMutableList()
                    val idx = snapshot.listIndex.coerceIn(0, list.size)
                    list.add(idx, snapshot.value)
                    s.copy(bodies = list)
                }
                is LastDeleted.Taper -> {
                    val list = s.tapers.toMutableList()
                    val idx = snapshot.listIndex.coerceIn(0, list.size)
                    list.add(idx, snapshot.value)
                    s.copy(tapers = list)
                }
                is LastDeleted.Thread -> {
                    val list = s.threads.toMutableList()
                    val idx = snapshot.listIndex.coerceIn(0, list.size)
                    list.add(idx, snapshot.value)
                    s.copy(threads = list)
                }
                is LastDeleted.Liner -> {
                    val list = s.liners.toMutableList()
                    val idx = snapshot.listIndex.coerceIn(0, list.size)
                    list.add(idx, snapshot.value)
                    s.copy(liners = list)
                }
            }
        }

        // 2) Restore into the cross-type UI order
        _componentOrder.update { current ->
            if (current.any { it.id == snapshot.id }) return@update current

            val insertAt = snapshot.orderIndex.coerceIn(0, current.size)
            current.toMutableList().apply {
                add(insertAt, ComponentKey(snapshot.id, snapshot.kind))
            }
        }

        ensureOverall()
        ensureOrderCoversSpec()
    }

}
