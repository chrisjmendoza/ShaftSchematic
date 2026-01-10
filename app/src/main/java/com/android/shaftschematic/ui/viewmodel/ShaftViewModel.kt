package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.data.SettingsStore.UnitPref
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.model.*
import com.android.shaftschematic.model.snapForwardFrom
import com.android.shaftschematic.model.snapForwardFromOrdered
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.util.Achievements
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseToMm
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.collections.removeFirst
import kotlin.compareTo
import kotlin.math.max
import kotlin.text.clear
import kotlin.text.compareTo
import kotlin.text.get

// Internal payload used by Undo/Redo for deletes.
// Not part of the public API; safe to change as the undo feature evolves.
// Captures the exact spec + order before the delete so undo can restore
// geometry that may have been temporarily re-snapped during delete.
private sealed class LastDeleted {
    abstract val id: String
    abstract val kind: ComponentKind
    abstract val orderIndex: Int
    abstract val beforeSpec: ShaftSpec
    abstract val beforeOrder: List<ComponentKey>

    data class Body(
        val value: com.android.shaftschematic.model.Body,
        override val orderIndex: Int,
        val listIndex: Int,
        override val beforeSpec: ShaftSpec,
        override val beforeOrder: List<ComponentKey>,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.BODY
    }

    data class Taper(
        val value: com.android.shaftschematic.model.Taper,
        override val orderIndex: Int,
        val listIndex: Int,
        override val beforeSpec: ShaftSpec,
        override val beforeOrder: List<ComponentKey>,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.TAPER
    }

    data class Thread(
        val value: com.android.shaftschematic.model.Threads,
        override val orderIndex: Int,
        val listIndex: Int,
        override val beforeSpec: ShaftSpec,
        override val beforeOrder: List<ComponentKey>,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.THREAD
    }

    data class Liner(
        val value: com.android.shaftschematic.model.Liner,
        override val orderIndex: Int,
        val listIndex: Int,
        override val beforeSpec: ShaftSpec,
        override val beforeOrder: List<ComponentKey>,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.LINER
    }
}

/** Maximum number of delete steps tracked for undo/redo. */
private const val MAX_DELETE_HISTORY = 10

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

    private val _openPdfAfterExport = MutableStateFlow(false)
    val openPdfAfterExport: StateFlow<Boolean> = _openPdfAfterExport.asStateFlow()

    private val _previewBlackWhiteOnly = MutableStateFlow(false)
    val previewBlackWhiteOnly: StateFlow<Boolean> = _previewBlackWhiteOnly.asStateFlow()

    private val _previewOutlineSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.STEEL, customRole = PreviewColorRole.MONOCHROME))
    val previewOutlineSetting: StateFlow<PreviewColorSetting> = _previewOutlineSetting.asStateFlow()

    private val _previewBodyFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT))
    val previewBodyFillSetting: StateFlow<PreviewColorSetting> = _previewBodyFillSetting.asStateFlow()

    private val _previewTaperFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.STEEL, customRole = PreviewColorRole.MONOCHROME))
    val previewTaperFillSetting: StateFlow<PreviewColorSetting> = _previewTaperFillSetting.asStateFlow()

    private val _previewLinerFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.BRONZE, customRole = PreviewColorRole.TERTIARY))
    val previewLinerFillSetting: StateFlow<PreviewColorSetting> = _previewLinerFillSetting.asStateFlow()

    private val _previewThreadFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT))
    val previewThreadFillSetting: StateFlow<PreviewColorSetting> = _previewThreadFillSetting.asStateFlow()

    private val _previewThreadHatchSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.STEEL, customRole = PreviewColorRole.MONOCHROME))
    val previewThreadHatchSetting: StateFlow<PreviewColorSetting> = _previewThreadHatchSetting.asStateFlow()

    private val _showComponentArrows = MutableStateFlow(false)
    val showComponentArrows: StateFlow<Boolean> = _showComponentArrows.asStateFlow()

    private val _componentArrowWidthDp = MutableStateFlow(40)
    val componentArrowWidthDp: StateFlow<Int> = _componentArrowWidthDp.asStateFlow()

    private val _devOptionsEnabled = MutableStateFlow(false)
    val devOptionsEnabled: StateFlow<Boolean> = _devOptionsEnabled.asStateFlow()

    private val _showOalDebugLabel = MutableStateFlow(false)
    val showOalDebugLabel: StateFlow<Boolean> = _showOalDebugLabel.asStateFlow()

    private val _showOalHelperLine = MutableStateFlow(false)
    val showOalHelperLine: StateFlow<Boolean> = _showOalHelperLine.asStateFlow()

    private val _showOalInPreviewBox = MutableStateFlow(false)
    val showOalInPreviewBox: StateFlow<Boolean> = _showOalInPreviewBox.asStateFlow()

    private val _showComponentDebugLabels = MutableStateFlow(false)
    val showComponentDebugLabels: StateFlow<Boolean> = _showComponentDebugLabels.asStateFlow()

    private val _showRenderLayoutDebugOverlay = MutableStateFlow(false)
    val showRenderLayoutDebugOverlay: StateFlow<Boolean> = _showRenderLayoutDebugOverlay.asStateFlow()

    private val _showRenderOalMarkers = MutableStateFlow(false)
    val showRenderOalMarkers: StateFlow<Boolean> = _showRenderOalMarkers.asStateFlow()

    private val _verboseLoggingEnabled = MutableStateFlow(false)
    val verboseLoggingEnabled: StateFlow<Boolean> = _verboseLoggingEnabled.asStateFlow()

    private val _verboseLoggingRender = MutableStateFlow(false)
    val verboseLoggingRender: StateFlow<Boolean> = _verboseLoggingRender.asStateFlow()

    private val _verboseLoggingOal = MutableStateFlow(false)
    val verboseLoggingOal: StateFlow<Boolean> = _verboseLoggingOal.asStateFlow()

    private val _verboseLoggingPdf = MutableStateFlow(false)
    val verboseLoggingPdf: StateFlow<Boolean> = _verboseLoggingPdf.asStateFlow()

    private val _verboseLoggingIo = MutableStateFlow(false)
    val verboseLoggingIo: StateFlow<Boolean> = _verboseLoggingIo.asStateFlow()

    private fun syncVerboseLogConfig() {
        VerboseLog.configure(
            devOptionsEnabled = _devOptionsEnabled.value,
            verboseEnabled = _verboseLoggingEnabled.value,
            renderEnabled = _verboseLoggingRender.value,
            oalEnabled = _verboseLoggingOal.value,
            pdfEnabled = _verboseLoggingPdf.value,
            ioEnabled = _verboseLoggingIo.value,
        )
    }

    private val _achievementsEnabled = MutableStateFlow(false)
    val achievementsEnabled: StateFlow<Boolean> = _achievementsEnabled.asStateFlow()

    private val _unlockedAchievementIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievementIds: StateFlow<Set<String>> = _unlockedAchievementIds.asStateFlow()

    // Auto-snap keeps components end-to-end in physical order when geometry changes.
    private val _autoSnap = MutableStateFlow(true)
    val autoSnap: StateFlow<Boolean> = _autoSnap.asStateFlow()

    private val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer.asStateFlow()

    private val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel.asStateFlow()

    private val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _shaftPosition = MutableStateFlow(ShaftPosition.OTHER)
    val shaftPosition: StateFlow<ShaftPosition> = _shaftPosition.asStateFlow()

    private val _overallIsManual = MutableStateFlow(false)
    val overallIsManual: StateFlow<Boolean> = _overallIsManual.asStateFlow()
    fun setOverallIsManual(v: Boolean) { _overallIsManual.value = v }

    // Incrementing key used by the editor UI to reset Compose-local state (dialogs, focus, scroll, etc.)
    // without relocating that state into the ViewModel.
    private val _editorResetNonce = MutableStateFlow(0)
    val editorResetNonce: StateFlow<Int> = _editorResetNonce.asStateFlow()

    // Cross-type UI order (stable IDs) — source of truth for list rendering (newest-first).
    private val _componentOrder = MutableStateFlow<List<ComponentKey>>(emptyList())
    val componentOrder: StateFlow<List<ComponentKey>> = _componentOrder.asStateFlow()

    // One-shot UI events (snackbars, etc.)
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Delete / Undo / Redo history (delete-only, v1.5)
    private val deleteHistory = ArrayDeque<LastDeleted>()
    private val redoHistory = ArrayDeque<LastDeleted>()

    // Expose whether deletes can be undone/redone for UI buttons.
    private val _canUndoDeletes = MutableStateFlow(false)
    val canUndoDeletes: StateFlow<Boolean> = _canUndoDeletes.asStateFlow()

    private val _canRedoDeletes = MutableStateFlow(false)
    val canRedoDeletes: StateFlow<Boolean> = _canRedoDeletes.asStateFlow()

    private fun updateUndoRedoFlags() {
        _canUndoDeletes.value = deleteHistory.isNotEmpty()
        _canRedoDeletes.value = redoHistory.isNotEmpty()
    }

    private fun clearDeleteHistory() {
        deleteHistory.clear()
        redoHistory.clear()
        updateUndoRedoFlags()
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Settings persistence (default unit + show grid)
    // ────────────────────────────────────────────────────────────────────────────

    init {
        // One-time migration: internal saved shafts were historically `*.json`.
        // Keep them visible/openable, but prefer `*.shaft` going forward.
        viewModelScope.launch {
            val app = getApplication<Application>()
            val alreadyMigrated = runCatching { SettingsStore.internalDocsMigratedToShaft(app) }
                .getOrDefault(false)
            if (!alreadyMigrated) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        migrateLegacyInternalDocs(app)
                    }
                }.onSuccess { report ->
                    VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") {
                        "legacy migration finished: migrated=${report.migratedCount} skipped=${report.skippedCount}"
                    }
                    // Mark done as long as the migration completed without throwing.
                    // Skips can be legitimate; don't retry forever.
                    SettingsStore.setInternalDocsMigratedToShaft(app, true)
                }.onFailure {
                    // Ignore; we'll retry next launch.
                    VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") {
                        "legacy migration failed: ${it.javaClass.simpleName}: ${it.message}"
                    }
                }
            }
        }

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

        viewModelScope.launch {
            SettingsStore.openPdfAfterExportFlow(getApplication()).collectLatest { persisted ->
                _openPdfAfterExport.value = persisted
            }
        }

        viewModelScope.launch {
            SettingsStore.previewBlackWhiteOnlyFlow(getApplication()).collectLatest { persisted ->
                _previewBlackWhiteOnly.value = persisted
            }
        }

        viewModelScope.launch {
            SettingsStore.previewOutlineSettingFlow(getApplication()).collectLatest { persisted ->
                _previewOutlineSetting.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.previewBodyFillSettingFlow(getApplication()).collectLatest { persisted ->
                _previewBodyFillSetting.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.previewTaperFillSettingFlow(getApplication()).collectLatest { persisted ->
                _previewTaperFillSetting.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.previewLinerFillSettingFlow(getApplication()).collectLatest { persisted ->
                _previewLinerFillSetting.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.previewThreadFillSettingFlow(getApplication()).collectLatest { persisted ->
                _previewThreadFillSetting.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.previewThreadHatchSettingFlow(getApplication()).collectLatest { persisted ->
                _previewThreadHatchSetting.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.showComponentArrowsFlow(getApplication()).collectLatest { persisted ->
                setShowComponentArrows(persisted, persist = false)
            }
        }
        viewModelScope.launch {
            SettingsStore.componentArrowWidthDpFlow(getApplication()).collectLatest { persisted ->
                setComponentArrowWidthDp(persisted, persist = false)
            }
        }

        viewModelScope.launch {
            SettingsStore.devOptionsEnabledFlow(getApplication()).collectLatest { persisted ->
                _devOptionsEnabled.value = persisted
                syncVerboseLogConfig()
            }
        }
        viewModelScope.launch {
            SettingsStore.showOalDebugLabelFlow(getApplication()).collectLatest { persisted ->
                _showOalDebugLabel.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.showOalHelperLineFlow(getApplication()).collectLatest { persisted ->
                _showOalHelperLine.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.showOalInPreviewBoxFlow(getApplication()).collectLatest { persisted ->
                _showOalInPreviewBox.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.showComponentDebugLabelsFlow(getApplication()).collectLatest { persisted ->
                _showComponentDebugLabels.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.showRenderLayoutDebugOverlayFlow(getApplication()).collectLatest { persisted ->
                _showRenderLayoutDebugOverlay.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.showRenderOalMarkersFlow(getApplication()).collectLatest { persisted ->
                _showRenderOalMarkers.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.verboseLoggingEnabledFlow(getApplication()).collectLatest { persisted ->
                _verboseLoggingEnabled.value = persisted
                syncVerboseLogConfig()
            }
        }

        viewModelScope.launch {
            SettingsStore.verboseLoggingRenderFlow(getApplication()).collectLatest { persisted ->
                _verboseLoggingRender.value = persisted
                syncVerboseLogConfig()
            }
        }
        viewModelScope.launch {
            SettingsStore.verboseLoggingOalFlow(getApplication()).collectLatest { persisted ->
                _verboseLoggingOal.value = persisted
                syncVerboseLogConfig()
            }
        }
        viewModelScope.launch {
            SettingsStore.verboseLoggingPdfFlow(getApplication()).collectLatest { persisted ->
                _verboseLoggingPdf.value = persisted
                syncVerboseLogConfig()
            }
        }
        viewModelScope.launch {
            SettingsStore.verboseLoggingIoFlow(getApplication()).collectLatest { persisted ->
                _verboseLoggingIo.value = persisted
                syncVerboseLogConfig()
            }
        }

        viewModelScope.launch {
            SettingsStore.achievementsEnabledFlow(getApplication()).collectLatest { persisted ->
                _achievementsEnabled.value = persisted
            }
        }
        viewModelScope.launch {
            SettingsStore.unlockedAchievementIdsFlow(getApplication()).collectLatest { persisted ->
                _unlockedAchievementIds.value = persisted
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

    fun setOpenPdfAfterExport(enabled: Boolean, persist: Boolean = true) {
        _openPdfAfterExport.value = enabled
        if (persist) {
            viewModelScope.launch { SettingsStore.setOpenPdfAfterExport(getApplication(), enabled) }
        }
    }

    fun setPreviewBlackWhiteOnly(enabled: Boolean, persist: Boolean = true) {
        _previewBlackWhiteOnly.value = enabled
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewBlackWhiteOnly(getApplication(), enabled) }
        }
    }

    fun setPreviewOutlineSetting(setting: PreviewColorSetting, persist: Boolean = true) {
        _previewOutlineSetting.value = setting
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewOutlineSetting(getApplication(), setting) }
        }
    }

    fun setPreviewBodyFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
        _previewBodyFillSetting.value = setting
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewBodyFillSetting(getApplication(), setting) }
        }
    }

    fun setPreviewTaperFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
        _previewTaperFillSetting.value = setting
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewTaperFillSetting(getApplication(), setting) }
        }
    }

    fun setPreviewLinerFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
        _previewLinerFillSetting.value = setting
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewLinerFillSetting(getApplication(), setting) }
        }
    }

    fun setPreviewThreadFillSetting(setting: PreviewColorSetting, persist: Boolean = true) {
        _previewThreadFillSetting.value = setting
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewThreadFillSetting(getApplication(), setting) }
        }
    }

    fun setPreviewThreadHatchSetting(setting: PreviewColorSetting, persist: Boolean = true) {
        _previewThreadHatchSetting.value = setting
        if (persist) {
            viewModelScope.launch { SettingsStore.setPreviewThreadHatchSetting(getApplication(), setting) }
        }
    }

    fun setShowComponentArrows(show: Boolean, persist: Boolean = true) {
        _showComponentArrows.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowComponentArrows(getApplication(), show) }
        }
    }

    fun setComponentArrowWidthDp(widthDp: Int, persist: Boolean = true) {
        val clamped = widthDp.coerceIn(24, 72)
        _componentArrowWidthDp.value = clamped
        if (persist) {
            viewModelScope.launch { SettingsStore.setComponentArrowWidthDp(getApplication(), clamped) }
        }
    }
    fun setDevOptionsEnabled(enabled: Boolean, persist: Boolean = true) {
        _devOptionsEnabled.value = enabled
        syncVerboseLogConfig()
        if (persist) {
            viewModelScope.launch { SettingsStore.setDevOptionsEnabled(getApplication(), enabled) }
        }
    }

    fun setShowOalDebugLabel(show: Boolean, persist: Boolean = true) {
        _showOalDebugLabel.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowOalDebugLabel(getApplication(), show) }
        }
    }

    fun setShowOalHelperLine(show: Boolean, persist: Boolean = true) {
        _showOalHelperLine.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowOalHelperLine(getApplication(), show) }
        }
    }

    fun setShowOalInPreviewBox(show: Boolean, persist: Boolean = true) {
        _showOalInPreviewBox.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowOalInPreviewBox(getApplication(), show) }
        }
    }

    fun setShowComponentDebugLabels(show: Boolean, persist: Boolean = true) {
        _showComponentDebugLabels.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowComponentDebugLabels(getApplication(), show) }
        }
    }

    fun setShowRenderLayoutDebugOverlay(show: Boolean, persist: Boolean = true) {
        _showRenderLayoutDebugOverlay.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowRenderLayoutDebugOverlay(getApplication(), show) }
        }
    }

    fun setShowRenderOalMarkers(show: Boolean, persist: Boolean = true) {
        _showRenderOalMarkers.value = show
        if (persist) {
            viewModelScope.launch { SettingsStore.setShowRenderOalMarkers(getApplication(), show) }
        }
    }

    fun setVerboseLoggingEnabled(enabled: Boolean, persist: Boolean = true) {
        _verboseLoggingEnabled.value = enabled
        syncVerboseLogConfig()
        if (persist) {
            viewModelScope.launch { SettingsStore.setVerboseLoggingEnabled(getApplication(), enabled) }
        }
    }

    fun setVerboseLoggingRender(enabled: Boolean, persist: Boolean = true) {
        _verboseLoggingRender.value = enabled
        syncVerboseLogConfig()
        if (persist) {
            viewModelScope.launch { SettingsStore.setVerboseLoggingRender(getApplication(), enabled) }
        }
    }

    fun setVerboseLoggingOal(enabled: Boolean, persist: Boolean = true) {
        _verboseLoggingOal.value = enabled
        syncVerboseLogConfig()
        if (persist) {
            viewModelScope.launch { SettingsStore.setVerboseLoggingOal(getApplication(), enabled) }
        }
    }

    fun setVerboseLoggingPdf(enabled: Boolean, persist: Boolean = true) {
        _verboseLoggingPdf.value = enabled
        syncVerboseLogConfig()
        if (persist) {
            viewModelScope.launch { SettingsStore.setVerboseLoggingPdf(getApplication(), enabled) }
        }
    }

    fun setVerboseLoggingIo(enabled: Boolean, persist: Boolean = true) {
        _verboseLoggingIo.value = enabled
        syncVerboseLogConfig()
        if (persist) {
            viewModelScope.launch { SettingsStore.setVerboseLoggingIo(getApplication(), enabled) }
        }
    }

    fun setAchievementsEnabled(enabled: Boolean, persist: Boolean = true) {
        _achievementsEnabled.value = enabled
        if (persist) {
            viewModelScope.launch { SettingsStore.setAchievementsEnabled(getApplication(), enabled) }
        }
    }

    fun unlockAchievement(id: String) {
        if (!_achievementsEnabled.value) return
        if (_unlockedAchievementIds.value.contains(id)) return
        viewModelScope.launch {
            SettingsStore.unlockAchievement(getApplication(), id)
        }
    }

    fun unlockAchievement(definition: Achievements.Definition) {
        unlockAchievement(definition.id)
    }

    /** Enables or disables auto-snapping of components after edits/deletes. */
    fun setAutoSnap(enabled: Boolean) {
        _autoSnap.value = enabled
        // Persistence can be wired into SettingsStore later if desired.
    }

    /** Explicitly snap forward from the given anchor key, end-to-end along the chain. */
    fun snapChainFrom(anchor: ComponentKey) {
        _spec.update { base -> base.snapForwardFrom(anchor) }
    }

    /** Convenience: snap forward from a component id by looking up its kind in UI order. */
    fun snapChainFromId(id: String) {
        val key = _componentOrder.value.firstOrNull { it.id == id }
        if (key != null) snapChainFrom(key)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Client metadata (free-form)
    // ────────────────────────────────────────────────────────────────────────────

    fun setCustomer(value: String) { _customer.value = value.trim() }
    fun setVessel(value: String)   { _vessel.value = value.trim() }
    fun setJobNumber(value: String){ _jobNumber.value = value.trim() }
    fun setNotes(value: String)    { _notes.value = value }
    fun setShaftPosition(value: ShaftPosition) { _shaftPosition.value = value }

    // ────────────────────────────────────────────────────────────────────────────
    // Overall length (mm)
    // ────────────────────────────────────────────────────────────────────────────

    /** Set shaft overall length (mm). Clamped to ≥ 0. */
    fun onSetOverallLengthMm(valueMm: Float) {
        _spec.update { it.copy(overallLengthMm = max(0f, valueMm)) }
    }

    /** Parses text in current UI units and forwards to [onSetOverallLengthMm]. */
    fun setOverallLength(raw: String) {
        val mm = parseToMm(raw, _unit.value).toFloat()
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
        if (index !in s.bodies.indices) s else {
            val old = s.bodies[index]
            val startChanged = old.startFromAftMm != startMm || old.lengthMm != lengthMm

            val updatedBodies = s.bodies.toMutableList().also { list ->
                list[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    diaMm = max(0f, diaMm)
                )
            }

            val base = s.copy(bodies = updatedBodies)

            if (_autoSnap.value && startChanged) {
                base.snapForwardFrom(ComponentKey(old.id, ComponentKind.BODY))
            } else {
                base
            }
        }
    }.also { ensureOverall() }

    /**
     * Remove a [Body] by its stable [id].
     *
     * The removed component is pushed into the delete history and becomes
     * undoable (multi-step, last-in-first-out).
     */
    fun removeBody(id: String) {
        Log.d("ShaftViewModel", "removeBody invoked for id=$id")
        val specBefore = _spec.value
        val orderBefore = _componentOrder.value
        var deleted: LastDeleted.Body? = null
        var snapFromKey: ComponentKey? = null
        var snapFromOrigin = false
        val removedKey = ComponentKey(id, ComponentKind.BODY)

        _spec.update { s ->
            val idx = s.bodies.indexOfFirst { it.id == id }
            if (idx < 0) {
                Log.w(
                    "ShaftViewModel",
                    "removeBody: requested id=$id not found. current ids=${s.bodies.map { it.id }}"
                )
                // NOTE: This should never happen during normal UI usage.
                return@update s
            }

            if (_autoSnap.value) {
                snapFromKey = s.findLeftNeighbor(removedKey)
                snapFromOrigin = snapFromKey == null
            }

            val body = s.bodies[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

            deleted = LastDeleted.Body(
                value = body,
                orderIndex = orderIdx,
                listIndex = idx,
                beforeSpec = specBefore,
                beforeOrder = orderBefore
            )

            s.copy(
                bodies = s.bodies.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let { snapshot ->
            // Remove from UI order AFTER spec update to avoid cross-state mutation
            orderRemove(snapshot.id)

            // Record into undo stack; clear redo history (new branch).
            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            redoHistory.clear()

            if (_autoSnap.value) {
                when {
                    snapFromKey != null ->
                        _spec.update { spec -> spec.snapForwardFrom(snapFromKey!!) }
                    snapFromOrigin ->
                        _spec.update { spec -> spec.snapFromOrigin() }
                }
            }

            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // Tapers
    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.TAPER, id)
        s.copy(
            tapers = listOf(
                Taper(
                    id = id,
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm),
                    keywayWidthMm = 0f,
                    keywayDepthMm = 0f
                )
            ) + s.tapers
        )
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateTaper(
        index: Int,
        startMm: Float,
        lengthMm: Float,
        startDiaMm: Float,
        endDiaMm: Float
    ) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            val startChanged = old.startFromAftMm != startMm || old.lengthMm != lengthMm

            val updatedTapers = s.tapers.toMutableList().also { list ->
                list[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm),
                    keywayWidthMm = old.keywayWidthMm,
                    keywayDepthMm = old.keywayDepthMm
                )
            }

            val base = s.copy(tapers = updatedTapers)

            if (_autoSnap.value && startChanged) {
                base.snapForwardFrom(ComponentKey(old.id, ComponentKind.TAPER))
            } else {
                base
            }
        }
    }.also { ensureOverall() }

    fun updateTaperKeyway(index: Int, widthMm: Float, depthMm: Float) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            val updatedTapers = s.tapers.toMutableList().also { list ->
                list[index] = old.copy(
                    keywayWidthMm = max(0f, widthMm),
                    keywayDepthMm = max(0f, depthMm)
                )
            }
            s.copy(tapers = updatedTapers)
        }
    }


    /** Remove a [Taper] by id with multi-step delete history support. */
    fun removeTaper(id: String) {
        Log.d("ShaftViewModel", "removeTaper invoked for id=$id")
        val specBefore = _spec.value
        val orderBefore = _componentOrder.value
        var deleted: LastDeleted.Taper? = null
        var snapFromKey: ComponentKey? = null
        var snapFromOrigin = false
        val removedKey = ComponentKey(id, ComponentKind.TAPER)

        _spec.update { s ->
            val idx = s.tapers.indexOfFirst { it.id == id }
            if (idx < 0) {
                Log.w(
                    "ShaftViewModel",
                    "removeTaper: requested id=$id not found. current ids=${s.tapers.map { it.id }}"
                )
                // NOTE: This should never happen during normal UI usage.
                return@update s
            }

            if (_autoSnap.value) {
                snapFromKey = s.findLeftNeighbor(removedKey)
                snapFromOrigin = snapFromKey == null
            }

            val taper = s.tapers[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it }

            deleted = LastDeleted.Taper(
                value = taper,
                orderIndex = orderIdx,
                listIndex = idx,
                beforeSpec = specBefore,
                beforeOrder = orderBefore
            )

            s.copy(
                tapers = s.tapers.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let { snapshot ->
            orderRemove(snapshot.id)

            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            redoHistory.clear()

            if (_autoSnap.value) {
                when {
                    snapFromKey != null ->
                        _spec.update { spec -> spec.snapForwardFrom(snapFromKey!!) }
                    snapFromOrigin ->
                        _spec.update { spec -> spec.snapFromOrigin() }
                }
            }

            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
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
     *  • excludeFromOAL — when true, thread length is excluded from OAL/measure-space
     *
     * UI contract: Screen & Route pass arguments in exactly this order.
     * We also construct `Threads(...)` with named arguments to avoid pitch/major swaps.
     */
    fun addThreadAt(
        startMm: Float,
        lengthMm: Float,
        majorDiaMm: Float,
        pitchMm: Float,
        excludeFromOAL: Boolean = false
    ) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.THREAD, id)
        s.copy(
            threads = listOf(
                Threads(
                    id = id,
                    startFromAftMm = startMm,
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm),
                    lengthMm = max(0f, lengthMm),
                    excludeFromOAL = excludeFromOAL
                )
            ) + s.threads
        )
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateThread(index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        if (index !in s.threads.indices) s else {
            val old = s.threads[index]
            val startChanged = old.startFromAftMm != startMm || old.lengthMm != lengthMm

            val updatedThreads = s.threads.toMutableList().also { l ->
                l[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm)
                )
            }

            val base = s.copy(threads = updatedThreads)

            if (_autoSnap.value && startChanged) {
                base.snapForwardFrom(ComponentKey(old.id, ComponentKind.THREAD))
            } else {
                base
            }
        }
    }.also { ensureOverall() }

    fun setThreadExcludeFromOal(id: String, excludeFromOAL: Boolean) = _spec.update { s ->
        val idx = s.threads.indexOfFirst { it.id == id }
        if (idx == -1) s
        else s.copy(
            threads = s.threads.toMutableList().also { l ->
                val old = l[idx]
                l[idx] = old.copy(excludeFromOAL = excludeFromOAL)
            }
        )
    }.also { ensureOverall() }

    /** Remove a [Threads] segment by id with multi-step delete history support. */
    fun removeThread(id: String) {
        Log.d("ShaftViewModel", "removeThread invoked for id=$id")
        val specBefore = _spec.value
        val orderBefore = _componentOrder.value
        var deleted: LastDeleted.Thread? = null
        var snapFromKey: ComponentKey? = null
        var snapFromOrigin = false
        val removedKey = ComponentKey(id, ComponentKind.THREAD)

        _spec.update { s ->
            val idx = s.threads.indexOfFirst { it.id == id }
            if (idx < 0) {
                Log.w(
                    "ShaftViewModel",
                    "removeThread: requested id=$id not found. current ids=${s.threads.map { it.id }}"
                )
                // NOTE: This should never happen during normal UI usage.
                return@update s
            }

            if (_autoSnap.value) {
                snapFromKey = s.findLeftNeighbor(removedKey)
                snapFromOrigin = snapFromKey == null
            }

            val thread = s.threads[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }
                .let { if (it < 0) 0 else it }

            deleted = LastDeleted.Thread(
                value = thread,
                orderIndex = orderIdx,
                listIndex = idx,
                beforeSpec = specBefore,
                beforeOrder = orderBefore
            )

            s.copy(
                threads = s.threads.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let { snapshot ->
            // Update cross-type order
            orderRemove(snapshot.id)

            // Push into undo stack, clear redo (new branch)
            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            redoHistory.clear()

            if (_autoSnap.value) {
                when {
                    snapFromKey != null ->
                        _spec.update { spec -> spec.snapForwardFrom(snapFromKey!!) }
                    snapFromOrigin ->
                        _spec.update { spec -> spec.snapFromOrigin() }
                }
            }

            // Maintain coverage + flags and show snackbar
            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // Liners
    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        val id = newId()
        orderAdd(ComponentKind.LINER, id)
        s.copy(liners = listOf(Liner(id, startMm, max(0f, lengthMm), max(0f, odMm))) + s.liners)
    }.also { ensureOverall(); ensureOrderCoversSpec() }

    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        if (index !in s.liners.indices) s else {
            val old = s.liners[index]
            val startChanged = old.startFromAftMm != startMm || old.lengthMm != lengthMm

            val updatedLiners = s.liners.toMutableList().also { l ->
                l[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    odMm = max(0f, odMm)
                )
            }

            val base = s.copy(liners = updatedLiners)

            if (_autoSnap.value && startChanged) {
                base.snapForwardFrom(ComponentKey(old.id, ComponentKind.LINER))
            } else {
                base
            }
        }
    }.also { ensureOverall() }

    fun updateLinerLabel(index: Int, label: String?) = _spec.update { s ->
        if (index !in s.liners.indices) s else {
            val old = s.liners[index]
            val normalized = label?.trim()?.takeIf { it.isNotEmpty() }
            if (old.label == normalized) return@update s
            s.copy(
                liners = s.liners.toMutableList().also { l ->
                    l[index] = old.copy(label = normalized)
                }
            )
        }
    }

    /** Remove a [Liner] by id with multi-step delete history support. */
    fun removeLiner(id: String) {
        Log.d("ShaftViewModel", "removeLiner invoked for id=$id")
        val specBefore = _spec.value
        val orderBefore = _componentOrder.value
        var deleted: LastDeleted.Liner? = null
        var snapFromKey: ComponentKey? = null
        var snapFromOrigin = false
        val removedKey = ComponentKey(id, ComponentKind.LINER)

        _spec.update { s ->
            val idx = s.liners.indexOfFirst { it.id == id }
            if (idx < 0) {
                Log.w(
                    "ShaftViewModel",
                    "removeLiner: requested id=$id not found. current ids=${s.liners.map { it.id }}"
                )
                // NOTE: This should never happen during normal UI usage.
                return@update s
            }

            if (_autoSnap.value) {
                snapFromKey = s.findLeftNeighbor(removedKey)
                snapFromOrigin = snapFromKey == null
            }

            val liner = s.liners[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }
                .let { if (it < 0) 0 else it }

            deleted = LastDeleted.Liner(
                value = liner,
                orderIndex = orderIdx,
                listIndex = idx,
                beforeSpec = specBefore,
                beforeOrder = orderBefore
            )

            s.copy(
                liners = s.liners.toMutableList().apply { removeAt(idx) }
            )
        }

        deleted?.let { snapshot ->
            orderRemove(snapshot.id)

            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            redoHistory.clear()

            if (_autoSnap.value) {
                when {
                    snapFromKey != null ->
                        _spec.update { spec -> spec.snapForwardFrom(snapFromKey!!) }
                    snapFromOrigin ->
                        _spec.update { spec -> spec.snapFromOrigin() }
                }
            }

            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Axial snapping — keep components end-to-end in physical order
    // ────────────────────────────────────────────────────────────────────────────

    // Snapping logic now lives in the model layer (ShaftSpecExtensions.snapForwardFrom).
    // ViewModel only decides *when* to snap (autoSnap flag) and which component is the anchor.

    private fun newId(): String = UUID.randomUUID().toString()

    // ────────────────────────────────────────────────────────────────────────────
    // New document helper (choose unit, optionally lock)
    // ────────────────────────────────────────────────────────────────────────────

    /** Start a brand-new shaft using [unit] for UI; lock UI unit if [lockUnit] is true. */
    fun newShaft(unit: UnitSystem, lockUnit: Boolean = true) {
        clearDeleteHistory()
        _spec.value = ShaftSpec()
        _componentOrder.value = emptyList() // fresh doc → empty order list
        _unitLocked.value = lockUnit
        _customer.value = ""
        _vessel.value = ""
        _jobNumber.value = ""
        _shaftPosition.value = ShaftPosition.OTHER
        _notes.value = ""
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
    // ────────────────────────────────────────────────────��───────────────────────

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
        @kotlinx.serialization.SerialName("job_number")
        val jobNumber: String = "",
        val customer: String = "",
        val vessel: String = "",
        @kotlinx.serialization.SerialName("shaft_position")
        val shaftPosition: ShaftPosition = ShaftPosition.OTHER,
        val notes: String = "",
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
            jobNumber = _jobNumber.value,
            customer = _customer.value,
            vessel = _vessel.value,
            shaftPosition = _shaftPosition.value,
            notes = _notes.value,
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
                clearDeleteHistory()
                _spec.value = doc.spec
                _unitLocked.value = doc.unitLocked
                setUnit(doc.preferredUnit, persist = false)

                _jobNumber.value = doc.jobNumber
                _customer.value = doc.customer
                _vessel.value = doc.vessel
                _shaftPosition.value = doc.shaftPosition
                _notes.value = doc.notes

                // Reset order to this document's components only
                _componentOrder.value = emptyList()
                ensureOrderCoversSpec(doc.spec)

                return
            }

        // Back-compat: older files were just the spec
        runCatching { json.decodeFromString<ShaftSpec>(raw) }
            .onSuccess { legacy ->
                clearDeleteHistory()
                _spec.value = legacy
                _unitLocked.value = false // no lock info in legacy
                // unit falls back to SettingsStore default (observer in init{})

                _customer.value = ""
                _vessel.value = ""
                _jobNumber.value = ""
                _shaftPosition.value = ShaftPosition.OTHER
                _notes.value = ""

                _componentOrder.value = emptyList()
                ensureOrderCoversSpec(legacy)

                return
            }
            .onFailure { throw it }
    }

    /**
     * Reset the editor to a new blank document.
     *
     * Contract:
     * - Uses the same defaults as the app's start/new flow (blank spec, empty metadata).
     * - Clears delete undo/redo history and resets cross-type component order.
     */
    fun newDocument() {
        _editorResetNonce.update { it + 1 }
        clearDeleteHistory()

        val blankSpec = ShaftSpec()
        _spec.value = blankSpec

        // Mirror envelope defaults used by the existing start/new seed path.
        _unitLocked.value = true
        setUnit(UnitSystem.INCHES, persist = false)

        _jobNumber.value = ""
        _customer.value = ""
        _vessel.value = ""
        _shaftPosition.value = ShaftPosition.OTHER
        _notes.value = ""
        _overallIsManual.value = false

        _componentOrder.value = emptyList()
        ensureOrderCoversSpec(blankSpec)
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
     * Also drops any order entries whose ids are no longer present in the spec.
     * Needed on load/import to seed order for legacy docs or externally-edited specs.
     */
    private fun ensureOrderCoversSpec(s: ShaftSpec = _spec.value) {
        // Compute the set of ids that actually exist in the spec
        val specIds = buildSet {
            addAll(s.bodies.map { it.id })
            addAll(s.tapers.map { it.id })
            addAll(s.threads.map { it.id })
            addAll(s.liners.map { it.id })
        }

        // Start from current order, but drop any ids that no longer exist
        val cur = _componentOrder.value
            .filter { it.id in specIds }
            .toMutableList()

        val have = cur.mapTo(mutableSetOf()) { it.id }

        fun addMissing(kind: ComponentKind, ids: List<String>) {
            ids.forEach { id ->
                if (id !in have) {
                    cur += ComponentKey(id, kind)
                    have += id
                }
            }
        }

        addMissing(ComponentKind.BODY,   s.bodies.map { it.id })
        addMissing(ComponentKind.TAPER,  s.tapers.map { it.id })
        addMissing(ComponentKind.THREAD, s.threads.map { it.id })
        addMissing(ComponentKind.LINER,  s.liners.map { it.id })

        if (cur != _componentOrder.value) {
            _componentOrder.value = cur
        }
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

        // ────────────────────────────────────────────────────────────────────────────
    // Snapping helpers (unit-aware tolerance, pure mm-space)
    // ────────────────────────────────────────────────────────────────────────────

    companion object {
        /** Injectable for tests; defaults to the real internal-storage migration. */
        internal var migrateLegacyInternalDocs: suspend (Context) -> InternalStorage.MigrationReport = { ctx ->
            InternalStorage.migrateLegacyJsonToShaft(ctx)
        }

        private const val METRIC_SNAP_TOL_MM = 1.0f         // 1 mm
        private const val IMPERIAL_SNAP_TOL_IN = 0.04f      // ~0.04 in ≈ 1.016 mm
        private const val INCH_TO_MM = 25.4f
    }

    /** Current snap tolerance expressed in mm, based on the active UI unit system. */
    private fun currentSnapToleranceMm(): Float {
        return when (_unit.value) {
            UnitSystem.MILLIMETERS -> METRIC_SNAP_TOL_MM
            UnitSystem.INCHES      -> IMPERIAL_SNAP_TOL_IN * INCH_TO_MM
        }
    }

    /**
     * Snap a raw mm position against the current spec, using anchors built from the
     * latest [ShaftSpec] and a unit-aware tolerance. This is the main entry point for
     * tap-to-add and future cursor-based snapping.
     */
    fun snapRawPositionMm(rawMm: Float): Float {
        val anchors = buildSnapAnchors(_spec.value)
        val config = SnapConfig(toleranceMm = currentSnapToleranceMm())
        return snapPositionMm(rawMm, anchors, config)
    }


    /**
        * Undo the most recent delete, if any.
        *
        * Restores the entire spec + component order snapshot that existed before the delete
        * (including any geometry that may have been shifted by delete-time snapping).
     */
    fun undoLastDelete() {
        val snapshot = deleteHistory.removeLastOrNull() ?: return

        _spec.value = snapshot.beforeSpec
        _componentOrder.value = snapshot.beforeOrder.toList()

        redoHistory.addLast(snapshot)
        if (redoHistory.size > MAX_DELETE_HISTORY) {
            redoHistory.removeFirst()
        }

        ensureOverall()
        ensureOrderCoversSpec()
        updateUndoRedoFlags()
    }

    /**
     * Redo the most recent undone delete, if any.
     *
     * Delegates to the public removeX APIs so redo reuses the same snap/logging logic
     * as a normal delete (and intentionally records a fresh LastDeleted snapshot for
     * subsequent undos).
     */
    fun redoLastDelete() {
        val snapshot = redoHistory.removeLastOrNull() ?: return

        when (snapshot) {
            is LastDeleted.Body -> removeBody(snapshot.id)
            is LastDeleted.Taper -> removeTaper(snapshot.id)
            is LastDeleted.Thread -> removeThread(snapshot.id)
            is LastDeleted.Liner -> removeLiner(snapshot.id)
        }

        updateUndoRedoFlags()
    }
}
