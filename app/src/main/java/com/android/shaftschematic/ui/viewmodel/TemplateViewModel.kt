// file: ui/viewmodel/TemplateViewModel.kt
package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.normalized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * TemplateViewModel
 *
 * Manages an in-memory shaft spec for the blank-template builder.
 * All values are notional mm (the OAL is fixed at TEMPLATE_OAL_MM and never
 * shown to the user). Sliders control proportions; the printed PDF uses
 * BlankTemplate mode so dimension values appear as write-here blanks.
 */

const val TEMPLATE_OAL_MM = 600f
private const val DEFAULT_DIA_MM = 80f

enum class TemplateComponentType { BODY, TAPER, THREAD, LINER }

class TemplateViewModel : ViewModel() {

    private val _spec = MutableStateFlow(
        ShaftSpec(
            overallLengthMm = TEMPLATE_OAL_MM,
            bodies = listOf(
                Body(startFromAftMm = 0f, lengthMm = TEMPLATE_OAL_MM, diaMm = DEFAULT_DIA_MM)
            )
        )
    )
    val spec: StateFlow<ShaftSpec> = _spec.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    val selectedType: StateFlow<TemplateComponentType?> = combine(spec, selectedId) { s, id ->
        if (id == null) null
        else when {
            s.bodies.any { it.id == id }  -> TemplateComponentType.BODY
            s.tapers.any { it.id == id }  -> TemplateComponentType.TAPER
            s.threads.any { it.id == id } -> TemplateComponentType.THREAD
            s.liners.any { it.id == id }  -> TemplateComponentType.LINER
            else                          -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── Add ──────────────────────────────────────────────────────────

    fun addBody() {
        val id = UUID.randomUUID().toString()
        mutate { it.copy(bodies = it.bodies + Body(id = id, startFromAftMm = 0f, lengthMm = 150f, diaMm = DEFAULT_DIA_MM)) }
        _selectedId.value = id
    }

    fun addThread() {
        val id = UUID.randomUUID().toString()
        mutate { it.copy(threads = it.threads + Threads(id = id, startFromAftMm = 0f, lengthMm = 60f, majorDiaMm = 75f)) }
        _selectedId.value = id
    }

    fun addTaper() {
        val id = UUID.randomUUID().toString()
        mutate { it.copy(tapers = it.tapers + Taper(id = id, startFromAftMm = 0f, lengthMm = 60f, startDiaMm = DEFAULT_DIA_MM, endDiaMm = 60f)) }
        _selectedId.value = id
    }

    fun addLiner() {
        val id = UUID.randomUUID().toString()
        mutate {
            it.copy(liners = it.liners + Liner(id = id, startFromAftMm = 0f, lengthMm = 100f, odMm = 95f).normalized())
        }
        _selectedId.value = id
    }

    // ── Selection ────────────────────────────────────────────────────

    fun onTapComponent(id: String) {
        val s = _spec.value
        val exists = s.bodies.any { it.id == id } || s.tapers.any { it.id == id } ||
            s.threads.any { it.id == id } || s.liners.any { it.id == id }
        _selectedId.value = if (exists) id else null
    }

    fun clearSelection() { _selectedId.value = null }

    // ── Body updates ─────────────────────────────────────────────────

    fun updateBodyLength(id: String, lengthMm: Float) =
        mutate { it.copy(bodies = it.bodies.map { b -> if (b.id == id) b.copy(lengthMm = lengthMm) else b }) }

    fun updateBodyDia(id: String, diaMm: Float) =
        mutate { it.copy(bodies = it.bodies.map { b -> if (b.id == id) b.copy(diaMm = diaMm) else b }) }

    fun updateBodyPosition(id: String, startMm: Float) =
        mutate { it.copy(bodies = it.bodies.map { b -> if (b.id == id) b.copy(startFromAftMm = startMm) else b }) }

    // ── Thread updates ───────────────────────────────────────────────

    fun updateThreadLength(id: String, lengthMm: Float) =
        mutate { it.copy(threads = it.threads.map { t -> if (t.id == id) t.copy(lengthMm = lengthMm) else t }) }

    fun updateThreadDia(id: String, diaMm: Float) =
        mutate { it.copy(threads = it.threads.map { t -> if (t.id == id) t.copy(majorDiaMm = diaMm) else t }) }

    fun updateThreadPosition(id: String, startMm: Float) =
        mutate { it.copy(threads = it.threads.map { t -> if (t.id == id) t.copy(startFromAftMm = startMm) else t }) }

    // ── Taper updates ────────────────────────────────────────────────

    fun updateTaperLength(id: String, lengthMm: Float) =
        mutate { it.copy(tapers = it.tapers.map { t -> if (t.id == id) t.copy(lengthMm = lengthMm) else t }) }

    fun updateTaperStartDia(id: String, diaMm: Float) =
        mutate { it.copy(tapers = it.tapers.map { t -> if (t.id == id) t.copy(startDiaMm = diaMm) else t }) }

    fun updateTaperEndDia(id: String, diaMm: Float) =
        mutate { it.copy(tapers = it.tapers.map { t -> if (t.id == id) t.copy(endDiaMm = diaMm) else t }) }

    fun updateTaperPosition(id: String, startMm: Float) =
        mutate { it.copy(tapers = it.tapers.map { t -> if (t.id == id) t.copy(startFromAftMm = startMm) else t }) }

    // ── Liner updates ────────────────────────────────────────────────

    fun updateLinerLength(id: String, lengthMm: Float) =
        mutate {
            it.copy(liners = it.liners.map { l ->
                if (l.id == id) l.copy(lengthMm = lengthMm, endMmPhysical = l.startFromAftMm + lengthMm) else l
            })
        }

    fun updateLinerOd(id: String, odMm: Float) =
        mutate { it.copy(liners = it.liners.map { l -> if (l.id == id) l.copy(odMm = odMm) else l }) }

    fun updateLinerPosition(id: String, startMm: Float) =
        mutate {
            it.copy(liners = it.liners.map { l ->
                if (l.id == id) l.copy(startFromAftMm = startMm, endMmPhysical = startMm + l.lengthMm) else l
            })
        }

    // ── Remove ───────────────────────────────────────────────────────

    fun removeSelected() {
        val id = _selectedId.value ?: return
        mutate { s ->
            s.copy(
                bodies  = s.bodies.filter { it.id != id },
                tapers  = s.tapers.filter { it.id != id },
                threads = s.threads.filter { it.id != id },
                liners  = s.liners.filter { it.id != id },
            )
        }
        _selectedId.value = null
    }

    // ── Internal ─────────────────────────────────────────────────────

    private inline fun mutate(transform: (ShaftSpec) -> ShaftSpec) {
        _spec.value = transform(_spec.value)
    }
}
