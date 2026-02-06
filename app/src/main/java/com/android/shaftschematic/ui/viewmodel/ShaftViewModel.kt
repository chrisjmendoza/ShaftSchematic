package com.android.shaftschematic.ui.viewmodel
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.pdf.PdfExportMode

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.data.SettingsStore.UnitPref
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.model.*
import com.android.shaftschematic.geom.computeMeasurementDatums
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.presentation.PresentationComponent
import com.android.shaftschematic.util.Achievements
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.DraftComponent
import com.android.shaftschematic.ui.resolved.ResolvedComponentSource
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseToMm
import com.android.shaftschematic.util.VerboseLog
import android.util.Log
import com.android.shaftschematic.data.AutosaveManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

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
        /** Returns the current PDF export preferences (PdfPrefs) from SettingsStore. */
        val currentPdfPrefs: PdfPrefs
            get() = SettingsStore.pdfPrefs
    // Autosave restore state
    private val _didRestoreAutosave = MutableStateFlow(false)
    val didRestoreAutosave: StateFlow<Boolean> = _didRestoreAutosave.asStateFlow()
    fun consumeDidRestoreAutosave() { _didRestoreAutosave.value = false }

    // Draft availability state
    private val _hasDraft = MutableStateFlow(false)
    val hasDraft: StateFlow<Boolean> = _hasDraft.asStateFlow()

    /**
     * Discard the current draft: clears autosave, resets VM to blank doc, and updates draft flags.
     */
    fun discardDraft() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AutosaveManager.clear(getApplication())
            }
            _hasDraft.value = false
            _didRestoreAutosave.value = false
            newDocument()
        }
    }
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

    private val _pdfTieringMode = MutableStateFlow(PdfTieringMode.AUTO)
    val pdfTieringMode: StateFlow<PdfTieringMode> = _pdfTieringMode.asStateFlow()

    private val _pdfShowComponentTitles = MutableStateFlow(true)
    val pdfShowComponentTitles: StateFlow<Boolean> = _pdfShowComponentTitles.asStateFlow()

    private val _pdfExportMode = MutableStateFlow(PdfExportMode.Standard)
    val pdfExportMode: StateFlow<PdfExportMode> = _pdfExportMode.asStateFlow()

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

    private val _resolvedComponents = MutableStateFlow<List<ResolvedComponent>>(emptyList())
    val resolvedComponents: StateFlow<List<ResolvedComponent>> = _resolvedComponents.asStateFlow()

    private val _presentationComponents = MutableStateFlow<List<PresentationComponent>>(emptyList())
    val presentationComponents: StateFlow<List<PresentationComponent>> = _presentationComponents.asStateFlow()

    private val _draftComponent = MutableStateFlow<DraftComponent?>(null)
    val draftComponent: StateFlow<DraftComponent?> = _draftComponent.asStateFlow()

    private val _isDraftEditorOpen = MutableStateFlow(false)
    val isDraftEditorOpen: StateFlow<Boolean> = _isDraftEditorOpen.asStateFlow()

    val activeDraftType: StateFlow<ComponentKind?> = draftComponent
        .map { draft ->
            when (draft) {
                is DraftComponent.Body -> ComponentKind.BODY
                is DraftComponent.Taper -> ComponentKind.TAPER
                is DraftComponent.Thread -> ComponentKind.THREAD
                is DraftComponent.Liner -> ComponentKind.LINER
                null -> null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _selectedPresentationId = MutableStateFlow<String?>(null)
    val selectedPresentationId: StateFlow<String?> = _selectedPresentationId.asStateFlow()

    val highlightedResolvedComponents: StateFlow<Set<String>> = combine(
        presentationComponents,
        selectedPresentationId
    ) { presentations, selectedId ->
        if (selectedId == null) return@combine emptySet()

        val presentation = presentations.firstOrNull { it.id == selectedId }
            ?: return@combine emptySet()

        presentation.resolvedParts
            .map { it.id }
            .toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet()
    )

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

    // Session-scoped "last used" add defaults (mm). Reset on new/open/import.
    private val _sessionAddDefaults = MutableStateFlow(SessionAddDefaults.initial())
    val sessionAddDefaults: StateFlow<SessionAddDefaults> = _sessionAddDefaults.asStateFlow()

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
    // Autosave and SettingsStore integration
    // ────────────────────────────────────────────────────────────────────────────
    init {
        // --- AUTOSAVE RESTORE + OBSERVER ---
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                AutosaveManager.restore(getApplication())
            }
            if (restored != null) {
                _hasDraft.value = true
                _didRestoreAutosave.value = true
            }
            if (restored != null && isSessionDefault()) {
                try {
                    restoreSnapshot(restored)
                } catch (_: Exception) {}
            } else if (restored != null) {
                VerboseLog.d(VerboseLog.Category.IO, "Autosave") {
                    "autosave restore skipped (session already initialized)"
                }
            }
        }
        viewModelScope.launch {
        @Suppress("UNCHECKED_CAST")
        // Flow.combine overload for >5 flows returns Array<Any?>
        combine(
            spec, unit, shaftPosition, customer, vessel, jobNumber, notes
        ) { values: Array<Any?> ->
            check(values.size == 7) { "Autosave combine expected 7 values, got ${values.size}" }

            val s = values[0] as ShaftSpec
            val u = values[1] as UnitSystem
            val pos = values[2] as ShaftPosition
            val cust = values[3] as String
            val ves = values[4] as String
            val job = values[5] as String
            val n = values[6] as String

            AutosaveManager.SessionSnapshot(
                shaftSpec = s,
                unitSystem = u,
                shaftPosition = pos,
                customer = cust,
                vessel = ves,
                jobNumber = job,
                notes = n
            )
        }
                .debounce(1500)
                .collectLatest { snapshot ->
                    try {
                        AutosaveManager.autosave(getApplication(), snapshot)
                    } catch (_: CancellationException) {
                        // ignore
                    } catch (_: Exception) {
                        // ignore
                    }
                }
        }

        viewModelScope.launch {
            combine(spec, overallIsManual, draftComponent) { s, isManual, draft ->
                resolveComponents(s, isManual, draft)
            }.collectLatest { resolved ->
                _resolvedComponents.value = resolved
            }
        }

        viewModelScope.launch {
            combine(resolvedComponents, spec) { resolved, currentSpec ->
                val committed = resolved.filter { it.source != ResolvedComponentSource.DRAFT }
                PresentationComponent.fromResolved(committed, currentSpec.autoBodyOverrides)
            }.collectLatest { presentation ->
                _presentationComponents.value = presentation
            }
        }
        // --- SETTINGSSTORE FLOWS AND MIGRATIONS ---
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

        // One-time (versioned) seeding: bundled sample shafts into internal Saved list.
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            runCatching {
                InternalStorage.seedBundledSamplesIfNeeded(app, SettingsStore)
            }.onSuccess { report ->
                VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") {
                    "sample seeding finished: attempted=${report.attemptedCount} saved=${report.savedCount} failed=${report.failedCount}"
                }
            }.onFailure {
                VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") {
                    "sample seeding failed: ${it.javaClass.simpleName}: ${it.message}"
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
            SettingsStore.pdfTieringModeFlow(getApplication()).collectLatest { persisted ->
                _pdfTieringMode.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(tieringMode = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfShowComponentTitlesFlow(getApplication()).collectLatest { persisted ->
                _pdfShowComponentTitles.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(showComponentTitles = persisted) }
            }
        }

        viewModelScope.launch {
            SettingsStore.pdfExportModeFlow(getApplication()).collectLatest { persisted ->
                _pdfExportMode.value = persisted
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

    // --- Autosave helpers: must be class members, not inside init ---
    private fun isSessionDefault(): Boolean {
        val s = _spec.value
        val specEmpty =
            s.overallLengthMm == 0f &&
            s.bodies.isEmpty() &&
            s.tapers.isEmpty() &&
            s.threads.isEmpty() &&
            s.liners.isEmpty()

        return specEmpty &&
            _shaftPosition.value == ShaftPosition.OTHER &&
            _customer.value.isBlank() &&
            _vessel.value.isBlank() &&
            _jobNumber.value.isBlank() &&
            _notes.value.isBlank()
    }

    private fun restoreSnapshot(snapshot: AutosaveManager.SessionSnapshot) {
        _spec.value = snapshot.shaftSpec
        _unit.value = snapshot.unitSystem
        _shaftPosition.value = snapshot.shaftPosition
        _customer.value = snapshot.customer
        _vessel.value = snapshot.vessel
        _jobNumber.value = snapshot.jobNumber
        _notes.value = snapshot.notes
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

    fun setPdfTieringMode(mode: PdfTieringMode, persist: Boolean = true) {
        _pdfTieringMode.value = mode
        SettingsStore.updatePdfPrefs { it.copy(tieringMode = mode) }
        if (persist) {
            viewModelScope.launch { SettingsStore.setPdfTieringMode(getApplication(), mode) }
        }
    }

    fun setPdfShowComponentTitles(show: Boolean, persist: Boolean = true) {
        _pdfShowComponentTitles.value = show
        SettingsStore.updatePdfPrefs { it.copy(showComponentTitles = show) }
        if (persist) {
            viewModelScope.launch { SettingsStore.setPdfShowComponentTitles(getApplication(), show) }
        }
    }

    fun selectPresentationById(presentationId: String?) {
        _selectedPresentationId.value = presentationId
    }

    fun setPdfExportMode(mode: PdfExportMode, persist: Boolean = true) {
        _pdfExportMode.value = mode
        if (persist) {
            viewModelScope.launch { SettingsStore.setPdfExportMode(getApplication(), mode) }
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
        // Auto-snapping is intentionally disabled in mutation paths to preserve sacred inputs.
        // Persistence can be wired into SettingsStore later if desired.
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
    @Suppress("UNUSED_PARAMETER")
    fun ensureOverall(minFreeMm: Float = 0f) {
        // Intentionally no-op: OAL must never change unless the user edits it directly.
        // (minFreeMm retained to preserve call sites without mutating authored inputs.)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Component add/update/remove — newest on top (all params in mm)
    // ────────────────────────────────────────────────────────────────────────────

    fun beginDraftBody(startMm: Float, lengthMm: Float, diaMm: Float) {
        _draftComponent.value = DraftComponent.Body(
            id = newId(),
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            diaMm = max(0f, diaMm)
        )
        _isDraftEditorOpen.value = true
    }

    fun beginDraftTaper(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) {
        _draftComponent.value = DraftComponent.Taper(
            id = newId(),
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            startDiaMm = max(0f, startDiaMm),
            endDiaMm = max(0f, endDiaMm),
            keywayWidthMm = 0f,
            keywayDepthMm = 0f,
            keywayLengthMm = 0f,
            keywaySpooned = false
        )
        _isDraftEditorOpen.value = true
    }

    fun beginDraftThread(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOal: Boolean) {
        _draftComponent.value = DraftComponent.Thread(
            id = newId(),
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            majorDiaMm = max(0f, majorDiaMm),
            pitchMm = max(0f, pitchMm),
            excludeFromOal = excludeFromOal
        )
        _isDraftEditorOpen.value = true
    }

    fun beginDraftLiner(startMm: Float, lengthMm: Float, odMm: Float) {
        _draftComponent.value = DraftComponent.Liner(
            id = newId(),
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            odMm = max(0f, odMm)
        )
        _isDraftEditorOpen.value = true
    }

    fun updateDraftBody(startMm: Float, lengthMm: Float, diaMm: Float) {
        val draft = _draftComponent.value as? DraftComponent.Body ?: return
        _draftComponent.value = draft.copy(
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            diaMm = max(0f, diaMm)
        )
    }

    fun updateDraftTaper(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) {
        val draft = _draftComponent.value as? DraftComponent.Taper ?: return
        _draftComponent.value = draft.copy(
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            startDiaMm = max(0f, startDiaMm),
            endDiaMm = max(0f, endDiaMm)
        )
    }

    fun updateDraftTaperKeyway(widthMm: Float, depthMm: Float, lengthMm: Float, spooned: Boolean) {
        val draft = _draftComponent.value as? DraftComponent.Taper ?: return
        _draftComponent.value = draft.copy(
            keywayWidthMm = max(0f, widthMm),
            keywayDepthMm = max(0f, depthMm),
            keywayLengthMm = max(0f, lengthMm),
            keywaySpooned = spooned
        )
    }

    fun updateDraftThread(startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOal: Boolean) {
        val draft = _draftComponent.value as? DraftComponent.Thread ?: return
        _draftComponent.value = draft.copy(
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            majorDiaMm = max(0f, majorDiaMm),
            pitchMm = max(0f, pitchMm),
            excludeFromOal = excludeFromOal
        )
    }

    fun updateDraftLiner(startMm: Float, lengthMm: Float, odMm: Float) {
        val draft = _draftComponent.value as? DraftComponent.Liner ?: return
        _draftComponent.value = draft.copy(
            startMmPhysical = startMm,
            lengthMm = max(0f, lengthMm),
            odMm = max(0f, odMm)
        )
    }

    fun cancelDraftComponent() {
        _draftComponent.value = null
        _isDraftEditorOpen.value = false
    }

    fun commitDraftComponent() {
        when (val draft = _draftComponent.value) {
            is DraftComponent.Body -> addBodyInternal(draft.id, draft.startMmPhysical, draft.lengthMm, draft.diaMm)
            is DraftComponent.Taper -> addTaperInternal(
                draft.id,
                draft.startMmPhysical,
                draft.lengthMm,
                draft.startDiaMm,
                draft.endDiaMm,
                draft.keywayWidthMm,
                draft.keywayDepthMm,
                draft.keywayLengthMm,
                draft.keywaySpooned
            )
            is DraftComponent.Thread -> addThreadInternal(draft.id, draft.startMmPhysical, draft.lengthMm, draft.majorDiaMm, draft.pitchMm, draft.excludeFromOal)
            is DraftComponent.Liner -> addLinerInternal(draft.id, draft.startMmPhysical, draft.lengthMm, draft.odMm)
            null -> return
        }
        _selectedPresentationId.value = _draftComponent.value?.id
        _draftComponent.value = null
        _isDraftEditorOpen.value = false
    }

    fun updateAutoBodyOverride(key: AutoBodyKey, override: AutoBodyOverride) = _spec.update { s ->
        val map = s.autoBodyOverrides.toMutableMap()
        map[key.stableId()] = override
        s.copy(autoBodyOverrides = map)
    }

    fun removeAutoBodyOverride(key: AutoBodyKey) = _spec.update { s ->
        if (!s.autoBodyOverrides.containsKey(key.stableId())) return@update s
        val map = s.autoBodyOverrides.toMutableMap()
        map.remove(key.stableId())
        s.copy(autoBodyOverrides = map)
    }

    // Bodies
    private fun addBodyInternal(id: String, startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        orderAdd(ComponentKind.BODY, id)
        s.copy(bodies = listOf(Body(id, startMm, max(0f, lengthMm), max(0f, diaMm))) + s.bodies)
    }.also {
        rememberBodyDefaults(lengthMm = lengthMm, diaMm = diaMm)
        ensureOrderCoversSpec()
    }

    fun addBodyAt(startMm: Float, lengthMm: Float, diaMm: Float) =
        addBodyInternal(newId(), startMm, lengthMm, diaMm)

    fun updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        if (index !in s.bodies.indices) s else {
            val old = s.bodies[index]

            val updatedBodies = s.bodies.toMutableList().also { list ->
                list[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    diaMm = max(0f, diaMm)
                )
            }

            // NOTE: Auto-snapping removed. Editing a body must not mutate any other component.
            s.copy(bodies = updatedBodies)
        }
    }.also {
        if (index in _spec.value.bodies.indices) {
            rememberBodyDefaults(lengthMm = lengthMm, diaMm = diaMm)
        }
    }

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
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // Tapers
    private fun addTaperInternal(
        startId: String,
        startMm: Float,
        lengthMm: Float,
        startDiaMm: Float,
        endDiaMm: Float,
        keywayWidthMm: Float = 0f,
        keywayDepthMm: Float = 0f,
        keywayLengthMm: Float = 0f,
        keywaySpooned: Boolean = false,
    ) = _spec.update { s ->
        orderAdd(ComponentKind.TAPER, startId)
        s.copy(
            tapers = listOf(
                Taper(
                    id = startId,
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm),
                    keywayWidthMm = max(0f, keywayWidthMm),
                    keywayDepthMm = max(0f, keywayDepthMm),
                    keywayLengthMm = max(0f, keywayLengthMm),
                    keywaySpooned = keywaySpooned,
                )
            ) + s.tapers
        )
    }.also {
        rememberTaperDefaults(lengthMm = lengthMm, setDiaMm = startDiaMm, letDiaMm = endDiaMm)
        ensureOrderCoversSpec()
    }

    fun addTaperAt(startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float) =
        addTaperInternal(newId(), startMm, lengthMm, startDiaMm, endDiaMm)

    fun updateTaper(
        index: Int,
        startMm: Float,
        lengthMm: Float,
        startDiaMm: Float,
        endDiaMm: Float
    ) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]

            val updatedTapers = s.tapers.toMutableList().also { list ->
                list[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    startDiaMm = max(0f, startDiaMm),
                    endDiaMm = max(0f, endDiaMm),
                    keywayWidthMm = old.keywayWidthMm,
                    keywayDepthMm = old.keywayDepthMm,
                    keywayLengthMm = old.keywayLengthMm,
                    keywaySpooned = old.keywaySpooned,
                )
            }

            val base = s.copy(tapers = updatedTapers)

            // NOTE: Auto-snapping removed. Editing a taper must not mutate any other component.
            base
        }
    }.also {
        if (index in _spec.value.tapers.indices) {
            rememberTaperDefaults(lengthMm = lengthMm, setDiaMm = startDiaMm, letDiaMm = endDiaMm)
        }
    }

    fun updateTaperKeyway(index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, spooned: Boolean) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            val updatedTapers = s.tapers.toMutableList().also { list ->
                list[index] = old.copy(
                    keywayWidthMm = max(0f, widthMm),
                    keywayDepthMm = max(0f, depthMm),
                    keywayLengthMm = max(0f, lengthMm),
                    keywaySpooned = spooned,
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
    ) = addThreadInternal(newId(), startMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL)

    private fun addThreadInternal(
        id: String,
        startMm: Float,
        lengthMm: Float,
        majorDiaMm: Float,
        pitchMm: Float,
        excludeFromOAL: Boolean
    ) = _spec.update { s ->
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
    }.also {
        rememberThreadDefaults(lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
        ensureOrderCoversSpec()
    }

    fun updateThread(index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        if (index !in s.threads.indices) s else {
            val old = s.threads[index]

            val updatedThreads = s.threads.toMutableList().also { l ->
                l[index] = old.copy(
                    startFromAftMm = startMm,
                    lengthMm = max(0f, lengthMm),
                    majorDiaMm = max(0f, majorDiaMm),
                    pitchMm = max(0f, pitchMm)
                )
            }

            // NOTE: Auto-snapping removed. Editing a thread must not mutate any other component.
            s.copy(threads = updatedThreads)
        }
    }.also {
        if (index in _spec.value.threads.indices) {
            rememberThreadDefaults(lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
        }
    }

    fun setThreadExcludeFromOal(id: String, excludeFromOAL: Boolean) = _spec.update { s ->
        val idx = s.threads.indexOfFirst { it.id == id }
        if (idx == -1) s
        else s.copy(
            threads = s.threads.toMutableList().also { l ->
                val old = l[idx]
                l[idx] = old.copy(excludeFromOAL = excludeFromOAL)
            }
        )
    }

    /** Remove a [Threads] segment by id with multi-step delete history support. */
    fun removeThread(id: String) {
        Log.d("ShaftViewModel", "removeThread invoked for id=$id")
        val specBefore = _spec.value
        val orderBefore = _componentOrder.value
        var deleted: LastDeleted.Thread? = null

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

            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // Liners
    fun addLinerAt(startMm: Float, lengthMm: Float, odMm: Float) =
        addLinerInternal(newId(), startMm, lengthMm, odMm)

    private fun addLinerInternal(id: String, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        orderAdd(ComponentKind.LINER, id)
        val len = max(0f, lengthMm)
        val od = max(0f, odMm)
        val liner = Liner(
            id = id,
            startFromAftMm = startMm,
            lengthMm = len,
            odMm = od,
            endMmPhysical = startMm + len,
            authoredReference = LinerAuthoredReference.AFT
        )
        s.copy(liners = listOf(liner) + s.liners)
    }.also {
        rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
        ensureOrderCoversSpec()
    }

    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        if (index !in s.liners.indices) s else {
            val old = s.liners[index]
            val len = max(0f, lengthMm)
            val od = max(0f, odMm)
            val mfd = computeMeasurementDatums(s).measurementForwardMm.toFloat()
            val authoredStartFromFwdMm = if (old.authoredReference == LinerAuthoredReference.FWD) {
                (mfd - (startMm + len)).coerceAtLeast(0f)
            } else {
                old.authoredStartFromFwdMm
            }

            val updatedLiners = s.liners.toMutableList().also { l ->
                l[index] = old.withPhysical(startMmPhysical = startMm, lengthMm = len, odMm = od)
                    .copy(authoredStartFromFwdMm = authoredStartFromFwdMm)
            }

            // NOTE: Auto-snapping removed. Editing a liner must not mutate any other component.
            s.copy(liners = updatedLiners)
        }
    }.also {
        if (index in _spec.value.liners.indices) {
            rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
        }
    }

    fun updateLinerAuthoredReference(index: Int, reference: LinerAuthoredReference) = _spec.update { s ->
        if (index !in s.liners.indices) s else {
            val old = s.liners[index]
            if (old.authoredReference == reference) return@update s
            val mfd = computeMeasurementDatums(s).measurementForwardMm.toFloat()
            val authoredStartFromFwdMm = if (reference == LinerAuthoredReference.FWD) {
                (mfd - (old.startFromAftMm + old.lengthMm)).coerceAtLeast(0f)
            } else {
                old.authoredStartFromFwdMm
            }
            s.copy(
                liners = s.liners.toMutableList().also { l ->
                    l[index] = old.copy(
                        authoredReference = reference,
                        authoredStartFromFwdMm = authoredStartFromFwdMm
                    )
                }
            )
        }
    }

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
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Axial snapping removed — auto bodies absorb gaps in resolved space
    // ────────────────────────────────────────────────────────────────────────────

    private fun newId(): String = UUID.randomUUID().toString()

    // ────────────────────────────────────────────────────────────────────────────
    // New document helper (choose unit, optionally lock)
    // ────────────────────────────────────────────────────────────────────────────

    /** Start a brand-new shaft using [unit] for UI; lock UI unit if [lockUnit] is true. */
    fun newShaft(unit: UnitSystem, lockUnit: Boolean = true) {
        clearDeleteHistory()
        _spec.value = ShaftSpec()
        _draftComponent.value = null
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
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Re-seeds bundled samples into the internal Saved list (Settings action).
     * Safe: never overwrites existing docs; collisions create suffixed duplicates.
     */
    fun restoreSampleShafts() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            val report = runCatching {
                InternalStorage.seedBundledSamples(app, SettingsStore, force = true)
            }.getOrElse {
                _uiEvents.emit(UiEvent.ShowSnackbarMessage("Restore sample shafts failed"))
                return@launch
            }

            val msg = when {
                report.savedCount > 0 -> "Restored sample shafts: +${report.savedCount}"
                report.attemptedCount == 0 -> "No bundled sample shafts found"
                else -> "Sample shafts already present"
            }

            _uiEvents.emit(UiEvent.ShowSnackbarMessage(msg))
        }
    }

    /** Export the current state as a JSON string (mm spec + unit metadata). */
    fun exportJson(): String = ShaftDocCodec.encodeV1(
        ShaftDocCodec.ShaftDocV1(
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
        val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrElse { throw it }

        clearDeleteHistory()
        _spec.value = decoded.spec
        _draftComponent.value = null
        seedSessionAddDefaultsFromSpec(decoded.spec)

        _unitLocked.value = decoded.unitLocked
        decoded.preferredUnit?.let { setUnit(it, persist = false) }

        _jobNumber.value = decoded.jobNumber
        _customer.value = decoded.customer
        _vessel.value = decoded.vessel
        _shaftPosition.value = decoded.shaftPosition
        _notes.value = decoded.notes

        // Reset order to this document's components only
        _componentOrder.value = emptyList()
        ensureOrderCoversSpec(decoded.spec)
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

        resetSessionAddDefaults()

        _draftComponent.value = null

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

    private fun resetSessionAddDefaults() {
        _sessionAddDefaults.value = SessionAddDefaults.initial()
    }

    private fun seedSessionAddDefaultsFromSpec(spec: ShaftSpec) {
        val base = SessionAddDefaults.initial()
        val newestBody = spec.bodies.firstOrNull { it.lengthMm > 0f || it.diaMm > 0f }
        val newestLiner = spec.liners.firstOrNull { it.lengthMm > 0f || it.odMm > 0f }
        val newestTaper = spec.tapers.firstOrNull { it.lengthMm > 0f || it.startDiaMm > 0f || it.endDiaMm > 0f }
        val newestThread = spec.threads.firstOrNull { it.lengthMm > 0f || it.majorDiaMm > 0f || it.pitchMm > 0f }

        _sessionAddDefaults.value = base.copy(
            bodyLenMm = newestBody?.lengthMm?.takeIf { it > 0f } ?: base.bodyLenMm,
            bodyDiaMm = newestBody?.diaMm?.takeIf { it > 0f } ?: base.bodyDiaMm,

            linerLenMm = newestLiner?.lengthMm?.takeIf { it > 0f } ?: base.linerLenMm,
            linerOdMm = newestLiner?.odMm?.takeIf { it > 0f } ?: base.linerOdMm,

            taperLenMm = newestTaper?.lengthMm?.takeIf { it > 0f } ?: base.taperLenMm,
            taperSetDiaMm = newestTaper?.startDiaMm?.takeIf { it > 0f } ?: base.taperSetDiaMm,
            taperLetDiaMm = newestTaper?.endDiaMm?.takeIf { it > 0f } ?: base.taperLetDiaMm,

            threadLenMm = newestThread?.lengthMm?.takeIf { it > 0f } ?: base.threadLenMm,
            threadMajorDiaMm = newestThread?.majorDiaMm?.takeIf { it > 0f } ?: base.threadMajorDiaMm,
            threadPitchMm = newestThread?.pitchMm?.takeIf { it > 0f } ?: base.threadPitchMm,
        )
    }

    private fun rememberBodyDefaults(lengthMm: Float, diaMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                bodyLenMm = if (lengthMm > 0f) lengthMm else cur.bodyLenMm,
                bodyDiaMm = if (diaMm > 0f) diaMm else cur.bodyDiaMm
            )
        }
    }

    private fun rememberLinerDefaults(lengthMm: Float, odMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                linerLenMm = if (lengthMm > 0f) lengthMm else cur.linerLenMm,
                linerOdMm = if (odMm > 0f) odMm else cur.linerOdMm
            )
        }
    }

    private fun rememberTaperDefaults(lengthMm: Float, setDiaMm: Float, letDiaMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                taperLenMm = if (lengthMm > 0f) lengthMm else cur.taperLenMm,
                taperSetDiaMm = if (setDiaMm > 0f) setDiaMm else cur.taperSetDiaMm,
                taperLetDiaMm = if (letDiaMm > 0f) letDiaMm else cur.taperLetDiaMm
            )
        }
    }

    private fun rememberThreadDefaults(lengthMm: Float, majorDiaMm: Float, pitchMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                threadLenMm = if (lengthMm > 0f) lengthMm else cur.threadLenMm,
                threadMajorDiaMm = if (majorDiaMm > 0f) majorDiaMm else cur.threadMajorDiaMm,
                threadPitchMm = if (pitchMm > 0f) pitchMm else cur.threadPitchMm
            )
        }
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
