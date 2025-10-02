package com.android.shaftschematic.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.android.shaftschematic.model.*
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * File: ShaftViewModel.kt
 * Layer: ViewModel
 * Purpose: Own the current [ShaftSpec] (canonical mm), expose it as reactive state for the UI,
 * and provide mutation + persistence helpers that respect the app contract.
 *
 * Contract highlights
 * • Canonical units are millimeters; conversions happen at the UI edge.
 * • Newest-on-top lists for components.
 * • ViewModel exposes StateFlow only; it does not perform rendering or View work.
 * • Save/Load uses a versioned JSON document to preserve forward/backward compatibility.
 */
class ShaftViewModel : ViewModel() {

    // ─────────────────────────────────────────────────────────────────────────────
    // Public state (observed by Compose)
    // ─────────────────────────────────────────────────────────────────────────────

    private val _spec = MutableStateFlow(ShaftSpec())
    val spec: StateFlow<ShaftSpec> = _spec

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

    // ─────────────────────────────────────────────────────────────────────────────
// UI setters (commit-on-blur, unit-agnostic)
// ─────────────────────────────────────────────────────────────────────────────

    /** Sets the UI unit (preview/labels only). Model remains canonical mm. */
    fun setUnit(value: UnitSystem) {
        _unit.value = value
    }

    /** Toggles grid visibility in Preview. */
    fun setShowGrid(show: Boolean) {
        _showGrid.value = show
    }

    /** Client metadata (footer + file metadata). */
    fun setCustomer(value: String) { _customer.value = value.trim() }
    fun setVessel(value: String)   { _vessel.value = value.trim() }
    fun setJobNumber(value: String){ _jobNumber.value = value.trim() }
    fun setNotes(value: String)    { _notes.value = value }


    // ─────────────────────────────────────────────────────────────────────────────
    // Overall length
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
        val end = coverageEndMm(s)
        val minOverall = end + max(0f, minFreeMm)
        if (s.overallLengthMm < minOverall) s.copy(overallLengthMm = minOverall) else s
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Component add/update/remove — newest on top
    // ─────────────────────────────────────────────────────────────────────────────

    // Bodies
    fun addBody(body: Body) = _spec.update { it.copy(bodies = listOf(body) + it.bodies) }
    fun addBodyAt(startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        s.copy(bodies = listOf(Body(newId(), startMm, max(0f, lengthMm), max(0f, diaMm))) + s.bodies)
    }.also { ensureOverall() }
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

    // Tapers
    fun addTaper(taper: Taper) = _spec.update { it.copy(tapers = listOf(taper) + it.tapers) }
    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        s.copy(tapers = listOf(Taper(newId(), startMm, max(0f, lengthMm), max(0f, startDiaMm), max(0f, endDiaMm))) + s.tapers)
    }.also { ensureOverall() }
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

    // Threads
    fun addThread(thread: ThreadSpec) = _spec.update { it.copy(threads = listOf(thread) + it.threads) }
    fun addThreadAt(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        s.copy(threads = listOf(ThreadSpec(newId(), startMm, max(0f, lengthMm), max(0f, majorDiaMm), max(0f, pitchMm))) + s.threads)
    }.also { ensureOverall() }
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

    // Liners
    fun addLiner(liner: Liner) = _spec.update { it.copy(liners = listOf(liner) + it.liners) }
    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        s.copy(liners = listOf(Liner(newId(), startMm, max(0f, lengthMm), max(0f, odMm))) + s.liners)
    }.also { ensureOverall() }
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

    private fun newId(): String = UUID.randomUUID().toString()

    // ─────────────────────────────────────────────────────────────────────────────
    // Persistence — versioned JSON document (UI wires it to SAF)
    // ─────────────────────────────────────────────────────────────────────────────

    @Serializable
    data class ShaftDocument(
        val version: Int = 1,
        val spec: ShaftSpec,
        val meta: Meta = Meta()
    ) {
        @Serializable
        data class Meta(
            val createdAtEpochMs: Long = System.currentTimeMillis(),
            val notes: String = ""
        )
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Export the current state as a JSON string. Use UI's SAF to write it. */
    fun exportJson(): String = json.encodeToString(ShaftDocument(spec = _spec.value))

    /** Import a JSON string (from SAF) and replace current spec. */
    fun importJson(text: String) {
        val doc = json.decodeFromString<ShaftDocument>(text)
        _spec.value = doc.spec
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
        s.bodies.forEach   { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.tapers.forEach   { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.threads.forEach  { end = max(end, it.startFromAftMm + it.lengthMm) }
        s.liners.forEach   { end = max(end, it.startFromAftMm + it.lengthMm) }
        return end
    }
}
