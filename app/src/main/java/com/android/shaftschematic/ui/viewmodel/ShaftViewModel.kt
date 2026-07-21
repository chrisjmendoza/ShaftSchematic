package com.android.shaftschematic.ui.viewmodel
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.settings.TirDirection
import com.android.shaftschematic.pdf.PdfExportMode

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.BuildConfig
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.data.SettingsStore.UnitPref
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.io.ShaftBackup
import java.io.File
import com.android.shaftschematic.model.*
import com.android.shaftschematic.model.snapForwardFrom
import com.android.shaftschematic.model.snapForwardFromOrdered
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.util.Achievements
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseTaperRateText
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

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

    data class CouplerBoltSlot(
        val value: com.android.shaftschematic.model.CouplerBoltSlot,
        override val orderIndex: Int,
        val listIndex: Int,
        override val beforeSpec: ShaftSpec,
        override val beforeOrder: List<ComponentKey>,
    ) : LastDeleted() {
        override val id: String get() = value.id
        override val kind: ComponentKind get() = ComponentKind.COUPLER_BOLT_SLOT
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
    // Unsaved-changes tracking + current document name
    // ────────────────────────────────────────────────────────────────────────────

    private var _savedSpec: ShaftSpec = ShaftSpec()
    private var _savedJobNumber: String = ""
    private var _savedCustomer: String = ""
    private var _savedVessel: String = ""
    private var _savedNotes: String = ""

    // Filename (with .shaft extension) of the last save/open, or null when the document
    // has never been saved. Used to enable silent quick-save without reprompting for a name.
    private val _currentDocumentName = MutableStateFlow<String?>(null)
    val currentDocumentName: StateFlow<String?> = _currentDocumentName.asStateFlow()

    fun setCurrentDocumentName(name: String?) { _currentDocumentName.value = name }

    fun markDocumentSaved() {
        _savedSpec = _spec.value
        _savedJobNumber = _jobNumber.value
        _savedCustomer = _customer.value
        _savedVessel = _vessel.value
        _savedNotes = _notes.value
    }

    fun hasUnsavedWork(): Boolean {
        if (isSessionDefault()) return false
        return _spec.value != _savedSpec ||
               _jobNumber.value != _savedJobNumber ||
               _customer.value != _savedCustomer ||
               _vessel.value != _savedVessel ||
               _notes.value != _savedNotes
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

    internal val _openPdfAfterExport = MutableStateFlow(false)
    val openPdfAfterExport: StateFlow<Boolean> = _openPdfAfterExport.asStateFlow()

    internal val _pdfTieringMode = MutableStateFlow(PdfTieringMode.AUTO)
    val pdfTieringMode: StateFlow<PdfTieringMode> = _pdfTieringMode.asStateFlow()

    internal val _pdfShowComponentTitles = MutableStateFlow(true)
    val pdfShowComponentTitles: StateFlow<Boolean> = _pdfShowComponentTitles.asStateFlow()

    internal val _pdfShadedBodies = MutableStateFlow(false)
    val pdfShadedBodies: StateFlow<Boolean> = _pdfShadedBodies.asStateFlow()
    internal val _pdfShadedTapers = MutableStateFlow(false)
    val pdfShadedTapers: StateFlow<Boolean> = _pdfShadedTapers.asStateFlow()
    internal val _pdfShadedLiners = MutableStateFlow(false)
    val pdfShadedLiners: StateFlow<Boolean> = _pdfShadedLiners.asStateFlow()

    internal val _pdfExportMode = MutableStateFlow(PdfExportMode.Standard)
    val pdfExportMode: StateFlow<PdfExportMode> = _pdfExportMode.asStateFlow()

    internal val _previewBlackWhiteOnly = MutableStateFlow(false)
    val previewBlackWhiteOnly: StateFlow<Boolean> = _previewBlackWhiteOnly.asStateFlow()

    internal val _lineThicknessScale = MutableStateFlow(1.0f)
    val lineThicknessScale: StateFlow<Float> = _lineThicknessScale.asStateFlow()

    internal val _previewOutlineSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.STEEL, customRole = PreviewColorRole.MONOCHROME))
    val previewOutlineSetting: StateFlow<PreviewColorSetting> = _previewOutlineSetting.asStateFlow()

    internal val _previewBodyFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT))
    val previewBodyFillSetting: StateFlow<PreviewColorSetting> = _previewBodyFillSetting.asStateFlow()

    internal val _previewTaperFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.STEEL, customRole = PreviewColorRole.MONOCHROME))
    val previewTaperFillSetting: StateFlow<PreviewColorSetting> = _previewTaperFillSetting.asStateFlow()

    internal val _previewLinerFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.BRONZE, customRole = PreviewColorRole.TERTIARY))
    val previewLinerFillSetting: StateFlow<PreviewColorSetting> = _previewLinerFillSetting.asStateFlow()

    internal val _previewThreadFillSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT))
    val previewThreadFillSetting: StateFlow<PreviewColorSetting> = _previewThreadFillSetting.asStateFlow()

    internal val _previewThreadHatchSetting = MutableStateFlow(PreviewColorSetting(preset = PreviewColorPreset.STEEL, customRole = PreviewColorRole.MONOCHROME))
    val previewThreadHatchSetting: StateFlow<PreviewColorSetting> = _previewThreadHatchSetting.asStateFlow()

    internal val _showComponentArrows = MutableStateFlow(false)
    val showComponentArrows: StateFlow<Boolean> = _showComponentArrows.asStateFlow()

    internal val _showHighlightSelection = MutableStateFlow(true)
    val showHighlightSelection: StateFlow<Boolean> = _showHighlightSelection.asStateFlow()

    internal val _componentArrowWidthDp = MutableStateFlow(40)
    val componentArrowWidthDp: StateFlow<Int> = _componentArrowWidthDp.asStateFlow()

    private val _resolvedComponents = MutableStateFlow<List<ResolvedComponent>>(emptyList())
    val resolvedComponents: StateFlow<List<ResolvedComponent>> = _resolvedComponents.asStateFlow()

    private val _selectedComponentId = MutableStateFlow<String?>(null)
    val selectedComponentId: StateFlow<String?> = _selectedComponentId.asStateFlow()

    internal val _devOptionsEnabled = MutableStateFlow(false)
    val devOptionsEnabled: StateFlow<Boolean> = _devOptionsEnabled.asStateFlow()

    internal val _showOalDebugLabel = MutableStateFlow(false)
    val showOalDebugLabel: StateFlow<Boolean> = _showOalDebugLabel.asStateFlow()

    internal val _showOalHelperLine = MutableStateFlow(false)
    val showOalHelperLine: StateFlow<Boolean> = _showOalHelperLine.asStateFlow()

    internal val _showOalInPreviewBox = MutableStateFlow(false)
    val showOalInPreviewBox: StateFlow<Boolean> = _showOalInPreviewBox.asStateFlow()

    internal val _showComponentDebugLabels = MutableStateFlow(false)
    val showComponentDebugLabels: StateFlow<Boolean> = _showComponentDebugLabels.asStateFlow()

    internal val _showRenderLayoutDebugOverlay = MutableStateFlow(false)
    val showRenderLayoutDebugOverlay: StateFlow<Boolean> = _showRenderLayoutDebugOverlay.asStateFlow()

    internal val _showRenderOalMarkers = MutableStateFlow(false)
    val showRenderOalMarkers: StateFlow<Boolean> = _showRenderOalMarkers.asStateFlow()

    internal val _verboseLoggingEnabled = MutableStateFlow(false)
    val verboseLoggingEnabled: StateFlow<Boolean> = _verboseLoggingEnabled.asStateFlow()

    internal val _verboseLoggingRender = MutableStateFlow(false)
    val verboseLoggingRender: StateFlow<Boolean> = _verboseLoggingRender.asStateFlow()

    internal val _verboseLoggingOal = MutableStateFlow(false)
    val verboseLoggingOal: StateFlow<Boolean> = _verboseLoggingOal.asStateFlow()

    internal val _verboseLoggingPdf = MutableStateFlow(false)
    val verboseLoggingPdf: StateFlow<Boolean> = _verboseLoggingPdf.asStateFlow()

    internal val _verboseLoggingIo = MutableStateFlow(false)
    val verboseLoggingIo: StateFlow<Boolean> = _verboseLoggingIo.asStateFlow()

    internal val _achievementsEnabled = MutableStateFlow(false)
    val achievementsEnabled: StateFlow<Boolean> = _achievementsEnabled.asStateFlow()

    internal val _unlockedAchievementIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievementIds: StateFlow<Set<String>> = _unlockedAchievementIds.asStateFlow()

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

    // ── Runout sheet configuration ────────────────────────────────────────────
    // Persisted alongside the spec in the .shaft file so bubble count overrides
    // and TIR direction travel with the job.

    private val _runoutConfig = MutableStateFlow(RunoutConfig())
    val runoutConfig: StateFlow<RunoutConfig> = _runoutConfig.asStateFlow()

    /**
     * Override the number of runout bubbles for a specific component.
     * Pass `count = null` to remove the override and revert to the computed default.
     * Minimum effective count is 1 for components that normally show bubbles.
     */
    fun setRunoutBubbleCount(componentId: String, count: Int?) {
        _runoutConfig.update { cfg ->
            val overrides = cfg.componentOverrides.toMutableMap()
            if (count == null) {
                overrides.remove(componentId)
            } else {
                overrides[componentId] = count.coerceAtLeast(1)
            }
            cfg.copy(componentOverrides = overrides)
        }
    }

    /** Set the TIR direction label printed at the bottom of the runout sheet. */
    fun setTirDirection(direction: TirDirection) {
        _runoutConfig.update { it.copy(tirDirection = direction) }
    }

    // ── Runout per-station readings (bubble value + high-spot marker) ──────────
    // Reference-only data, same posture as _wearRecord below: plain state updates, no
    // geometry side effects. Keyed by (componentId, stationIndex). Both fields optional;
    // an entry with neither value nor marker is dropped by RunoutReadings.withReading.
    // See docs/RunoutBubbleEditor_PLAN.md and model/RunoutReading.kt.

    private val _runoutReadings = MutableStateFlow(RunoutReadings())
    val runoutReadings: StateFlow<RunoutReadings> = _runoutReadings.asStateFlow()

    /**
     * Upsert the runout reading for a bubble identified by [componentId] + [stationIndex].
     * [valueMm] is canonical mm (UI converts from the active unit before calling);
     * [highSpotHalfHours] is a clock tick in `[0, 23]` (0 = 12 o'clock). Passing both as null
     * clears the reading (the empty entry is not stored).
     */
    fun setRunoutReading(
        componentId: String,
        stationIndex: Int,
        valueMm: Float?,
        highSpotHalfHours: Int?,
    ) {
        _runoutReadings.update { readings ->
            readings.withReading(
                RunoutReading(
                    componentId = componentId,
                    stationIndex = stationIndex,
                    valueMm = valueMm,
                    highSpotHalfHours = highSpotHalfHours?.let { ((it % 24) + 24) % 24 },
                )
            )
        }
    }

    /** Remove the runout reading for a bubble. No-op if none recorded. */
    fun clearRunoutReading(componentId: String, stationIndex: Int) {
        _runoutReadings.update { it.without(componentId, stationIndex) }
    }

    // ── Liner wear inspection record ──────────────────────────────────────────
    // Persisted alongside the spec in the .shaft file, same as runoutConfig above.
    // Reference-only data: plain state updates, no geometry side effects, no
    // ensureOverall/auto-body interaction. See docs/LinerWearAreas_Proposal.md §5, §7.

    private val _wearRecord = MutableStateFlow(WearRecord())
    val wearRecord: StateFlow<WearRecord> = _wearRecord.asStateFlow()

    /**
     * Add a new wear spot on [linerId] with sensible defaults (start 0, no reading). The
     * default length is 1in (25.4mm), clamped to the liner's own length for tiny liners so
     * the default band is never rejected by [wearSpotSpanIssue] at first render/edit.
     */
    fun addWearSpot(linerId: String) {
        val linerLengthMm = _spec.value.liners.firstOrNull { it.id == linerId }?.lengthMm ?: 25.4f
        val defaultLengthMm = min(25.4f, linerLengthMm.coerceAtLeast(0f))
        _wearRecord.update { rec ->
            rec.copy(
                spots = rec.spots + WearSpot(
                    linerId = linerId,
                    startMm = 0f,
                    lengthMm = defaultLengthMm,
                    minDiaMm = 0f,
                    note = "",
                )
            )
        }
    }

    /**
     * Update an existing wear spot's fields by [id]. No-op if the id is not found.
     *
     * [startMm]/[lengthMm] are always canonical (liner-local AFT-edge mm) — reference
     * conversion happens in the UI (`LinerWearMath.kt`'s `wearStartToCanonicalMm`) before
     * this is called, and blocking in-span validation (`wearSpotSpanIssue`) happens at the
     * `NumericInputField` layer, so a rejected entry never reaches here. See
     * [updateWearSpotReference] for the separate, geometry-free "Measure from" setter.
     */
    fun updateWearSpot(id: String, startMm: Float, lengthMm: Float, minDiaMm: Float, note: String) {
        _wearRecord.update { rec ->
            rec.copy(
                spots = rec.spots.map { spot ->
                    if (spot.id != id) spot else spot.copy(
                        startMm = max(0f, startMm),
                        lengthMm = max(0f, lengthMm),
                        minDiaMm = max(0f, minDiaMm),
                        note = note,
                    )
                }
            )
        }
    }

    /**
     * Update a wear spot's authored "Measure from" reference by [id]. Display-only — same
     * pattern as `updateLinerAuthoredReference`/`updateCouplerBoltSlotReference`: it never
     * touches [WearSpot.startMm]/[WearSpot.lengthMm], only which reference point the Start
     * field re-projects against.
     */
    fun updateWearSpotReference(id: String, reference: WearSpotReference) {
        _wearRecord.update { rec ->
            rec.copy(
                spots = rec.spots.map { spot ->
                    if (spot.id != id || spot.authoredReference == reference) spot
                    else spot.copy(authoredReference = reference)
                }
            )
        }
    }

    /** Remove a wear spot by [id]. Confirm-free, as authored in the detail-view UI. */
    fun removeWearSpot(id: String) {
        _wearRecord.update { rec -> rec.copy(spots = rec.spots.filterNot { it.id == id }) }
    }

    // Tap-to-add pending position: non-null while the user has tapped empty space and
    // has not yet confirmed or dismissed the add-at-position flow.
    private val _pendingAddPositionMm = MutableStateFlow<Float?>(null)
    val pendingAddPositionMm: StateFlow<Float?> = _pendingAddPositionMm.asStateFlow()

    /** Called by UI when the user taps empty space in the preview. Snaps and stores the position. */
    fun setTapAddPosition(rawMm: Float) {
        _pendingAddPositionMm.value = snapRawPositionMm(rawMm)
    }

    /** Clear the pending tap-add intent (called when the chooser or dialog is dismissed/confirmed). */
    fun clearPendingAddPosition() {
        _pendingAddPositionMm.value = null
    }

    /**
     * Distance from [positionMm] to the next snap anchor (component start/end or OAL boundary),
     * clamped to at least [minimumMm]. Used to prefill the length field in tap-to-add dialogs.
     */
    fun gapToNextAnchorMm(positionMm: Float, minimumMm: Float = 50f): Float {
        val anchors = buildSnapAnchors(_spec.value)
        val next = anchors.filter { it > positionMm + 0.1f }.minOrNull() ?: _spec.value.overallLengthMm
        return maxOf(next - positionMm, minimumMm)
    }

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

    // True while redoLastDelete() replays a delete through the public removeX APIs.
    // A replayed delete must not clear redoHistory — only a fresh user delete starts
    // a new branch. Single-threaded (all mutations on Main), so a plain flag suffices.
    private var isRedoing = false

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
            spec, unit, shaftPosition, customer, vessel, jobNumber, notes,
            runoutConfig, unitLocked, overallIsManual, wearRecord, runoutReadings
        ) { values: Array<Any?> ->
            check(values.size == 12) { "Autosave combine expected 12 values, got ${values.size}" }

            val s = values[0] as ShaftSpec
            val u = values[1] as UnitSystem
            val pos = values[2] as ShaftPosition
            val cust = values[3] as String
            val ves = values[4] as String
            val job = values[5] as String
            val n = values[6] as String
            val runout = values[7] as RunoutConfig
            val locked = values[8] as Boolean
            val manual = values[9] as Boolean
            val wear = values[10] as WearRecord
            val readings = values[11] as RunoutReadings

            AutosaveManager.SessionSnapshot(
                shaftSpec = s,
                unitSystem = u,
                shaftPosition = pos,
                customer = cust,
                vessel = ves,
                jobNumber = job,
                notes = n,
                runoutConfig = runout,
                unitLocked = locked,
                overallIsManual = manual,
                wearRecord = wear,
                runoutReadings = readings,
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
            combine(spec, overallIsManual) { s, isManual ->
                resolveComponents(s, isManual)
            }.collectLatest { resolved ->
                _resolvedComponents.value = resolved
            }
        }
        // --- SETTINGSSTORE FLOWS AND MIGRATIONS ---
        // Startup storage maintenance runs as ONE sequential pipeline so the
        // safety snapshot always lands before anything can rewrite saved docs:
        //   1. pre-update snapshot (zip of shafts/, once per versionCode change)
        //   2. legacy `*.json` → `*.shaft` rename migration
        //   3. versioned bundled-sample seeding (incl. ledger-guarded pruning)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            runCatching {
                val versionCode = BuildConfig.VERSION_CODE
                if (SettingsStore.getLastSnapshotVersionCode(app) != versionCode) {
                    val written = ShaftBackup.writeSnapshot(
                        shaftsDir = InternalStorage.dir(app.filesDir),
                        backupsDir = File(app.filesDir, "backups"),
                        appVersion = BuildConfig.VERSION_NAME,
                        docFormatVersion = ShaftDocCodec.CURRENT_VERSION,
                        nowMs = System.currentTimeMillis(),
                    )
                    SettingsStore.setLastSnapshotVersionCode(app, versionCode)
                    VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") {
                        "pre-update snapshot: ${written?.name ?: "nothing to snapshot"}"
                    }
                }
            }.onFailure {
                VerboseLog.d(VerboseLog.Category.IO, "InternalStorage") {
                    "pre-update snapshot failed: ${it.javaClass.simpleName}: ${it.message}"
                }
            }

            // One-time migration: internal saved shafts were historically `*.json`.
            // Keep them visible/openable, but prefer `*.shaft` going forward.
            val alreadyMigrated = runCatching { SettingsStore.internalDocsMigratedToShaft(app) }
                .getOrDefault(false)
            if (!alreadyMigrated) {
                runCatching {
                    migrateLegacyInternalDocs(app)
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

            // One-time (versioned) seeding: bundled sample shafts into internal Saved list.
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
            SettingsStore.pdfShadedBodiesFlow(getApplication()).collectLatest { persisted ->
                _pdfShadedBodies.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(shadedBodies = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfShadedTapersFlow(getApplication()).collectLatest { persisted ->
                _pdfShadedTapers.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(shadedTapers = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfShadedLinersFlow(getApplication()).collectLatest { persisted ->
                _pdfShadedLiners.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(shadedLiners = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfOalSpacingFactorFlow(getApplication()).collectLatest { persisted ->
                SettingsStore.updatePdfPrefs { it.copy(oalSpacingFactor = persisted) }
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
            SettingsStore.lineThicknessScaleFlow(getApplication()).collectLatest { persisted ->
                _lineThicknessScale.value = persisted
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
            SettingsStore.showHighlightSelectionFlow(getApplication()).collectLatest { persisted ->
                setShowHighlightSelection(persisted, persist = false)
            }
        }
        viewModelScope.launch {
            SettingsStore.componentArrowWidthDpFlow(getApplication()).collectLatest { persisted ->
                setComponentArrowWidthDp(persisted, persist = false)
            }
        }

        resetDevFlagsOnStartup()
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
            s.liners.isEmpty() &&
            s.couplerBoltSlots.isEmpty()

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
        _runoutConfig.value = snapshot.runoutConfig
        _wearRecord.value = snapshot.wearRecord
        _runoutReadings.value = snapshot.runoutReadings
        // Restore unitLocked before any defaultUnitFlow emission can overwrite the
        // draft's unit, and overallIsManual so a manually-set OAL isn't auto-resized.
        _unitLocked.value = snapshot.unitLocked
        _overallIsManual.value = snapshot.overallIsManual
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

    fun selectComponentById(componentId: String?) {
        _selectedComponentId.value = componentId
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
        _spec.update { it.withNewOal(valueMm) }
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
        if (s.overallLengthMm < minOverall) s.withNewOal(minOverall) else s
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Component add/update/remove — newest on top (all params in mm)
    // ────────────────────────────────────────────────────────────────────────────

    // Bodies
    fun addBodyAt(
        startMm: Float,
        lengthMm: Float,
        diaMm: Float,
        keywayWidthMm: Float = 0f,
        keywayDepthMm: Float = 0f,
        keywayLengthMm: Float = 0f,
        keywayOffsetFromEndMm: Float = 0f,
        keywayEnd: LinerAuthoredReference = LinerAuthoredReference.AFT,
        keywaySpooned: Boolean = false,
    ) {
        val id = newId()
        _spec.update { s ->
            orderAdd(ComponentKind.BODY, id)
            s.copy(
                bodies = listOf(
                    Body(
                        id = id,
                        startFromAftMm = startMm,
                        lengthMm = max(0f, lengthMm),
                        diaMm = max(0f, diaMm),
                        keywayWidthMm = max(0f, keywayWidthMm),
                        keywayDepthMm = max(0f, keywayDepthMm),
                        keywayLengthMm = max(0f, keywayLengthMm),
                        keywayOffsetFromEndMm = max(0f, keywayOffsetFromEndMm),
                        keywayEnd = keywayEnd,
                        keywaySpooned = keywaySpooned,
                    )
                ) + s.bodies
            )
        }
        rememberBodyDefaults(lengthMm = lengthMm, diaMm = diaMm)
        ensureOverall()
        ensureOrderCoversSpec()
        _selectedComponentId.value = id
    }

    fun updateBody(index: Int, startMm: Float, lengthMm: Float, diaMm: Float) = _spec.update { s ->
        if (index !in s.bodies.indices) s else {
            val old = s.bodies[index]
            s.copy(
                bodies = s.bodies.toMutableList().also { list ->
                    list[index] = old.copy(
                        startFromAftMm = startMm,
                        lengthMm = max(0f, lengthMm),
                        diaMm = max(0f, diaMm)
                    )
                }
            )
        }
    }.also {
        if (index in _spec.value.bodies.indices) {
            rememberBodyDefaults(lengthMm = lengthMm, diaMm = diaMm)
        }
        ensureOverall()
    }

    /** Edit a body's keyway in place (mirrors [updateTaperKeyway]). All params in mm. */
    fun updateBodyKeyway(
        index: Int,
        widthMm: Float,
        depthMm: Float,
        lengthMm: Float,
        offsetFromEndMm: Float,
        end: LinerAuthoredReference,
        spooned: Boolean,
    ) = _spec.update { s ->
        if (index !in s.bodies.indices) s else {
            val old = s.bodies[index]
            s.copy(
                bodies = s.bodies.toMutableList().also { list ->
                    list[index] = old.copy(
                        keywayWidthMm = max(0f, widthMm),
                        keywayDepthMm = max(0f, depthMm),
                        keywayLengthMm = max(0f, lengthMm),
                        keywayOffsetFromEndMm = max(0f, offsetFromEndMm),
                        keywayEnd = end,
                        keywaySpooned = spooned,
                    )
                }
            )
        }
    }

    /** Set the drawing note that the shaft's keyways are clocked 180° apart. */
    fun setKeyways180Apart(enabled: Boolean) = _spec.update { s ->
        if (s.keyways180Apart == enabled) s else s.copy(keyways180Apart = enabled)
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
            if (!isRedoing) redoHistory.clear()

            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // Tapers
    fun addTaperAt(
        startMm: Float,
        lengthMm: Float,
        startDiaMm: Float,
        endDiaMm: Float,
        rateText: String = "",
        keywayWidthMm: Float = 0f,
        keywayDepthMm: Float = 0f,
        keywayLengthMm: Float = 0f,
        keywayOffsetFromSetMm: Float = 0f,
        keywaySpooned: Boolean = false,
    ) {
        val id = newId()
        _spec.update { s ->
            val split = s.splitBodiesAround(startMm, startMm + lengthMm) { newId() }
            split.removedIds.forEach { orderRemove(it) }
            split.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }

            orderAdd(ComponentKind.TAPER, id)
            val (resolvedSet, resolvedLet) = deriveTaperDiameters(
                startDiaMm = startDiaMm, endDiaMm = endDiaMm,
                lengthMm = lengthMm, rateText = rateText,
                smallEndAtStart = taperSmallEndAtStart(startMm, lengthMm, s.overallLengthMm)
            )
            split.spec.copy(
                tapers = listOf(
                    Taper(
                        id = id,
                        startFromAftMm = startMm,
                        lengthMm = max(0f, lengthMm),
                        startDiaMm = max(0f, resolvedSet),
                        endDiaMm = max(0f, resolvedLet),
                        keywayWidthMm = max(0f, keywayWidthMm),
                        keywayDepthMm = max(0f, keywayDepthMm),
                        keywayLengthMm = max(0f, keywayLengthMm),
                        keywayOffsetFromSetMm = max(0f, keywayOffsetFromSetMm),
                        keywaySpooned = keywaySpooned,
                        taperRateText = rateText,
                    )
                ) + split.spec.tapers
            )
        }
        rememberTaperDefaults(lengthMm = lengthMm, setDiaMm = startDiaMm, letDiaMm = endDiaMm)
        ensureOverall()
        ensureOrderCoversSpec()
        _selectedComponentId.value = id
    }

    fun updateTaper(
        index: Int,
        startMm: Float,
        lengthMm: Float,
        startDiaMm: Float,
        endDiaMm: Float,
        rateText: String = "",
    ) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            val effectiveRate = rateText.ifBlank { old.taperRateText }

            val (resolvedSet, resolvedLet) = deriveTaperDiameters(
                startDiaMm = startDiaMm, endDiaMm = endDiaMm,
                lengthMm = lengthMm, rateText = effectiveRate,
                smallEndAtStart = taperSmallEndAtStart(startMm, lengthMm, s.overallLengthMm)
            )

            s.copy(
                tapers = s.tapers.toMutableList().also { list ->
                    list[index] = old.copy(
                        startFromAftMm = startMm,
                        lengthMm = max(0f, lengthMm),
                        startDiaMm = max(0f, resolvedSet),
                        endDiaMm = max(0f, resolvedLet),
                        keywayWidthMm = old.keywayWidthMm,
                        keywayDepthMm = old.keywayDepthMm,
                        keywayLengthMm = old.keywayLengthMm,
                        keywaySpooned = old.keywaySpooned,
                        taperRateText = rateText.ifBlank { old.taperRateText },
                    )
                }
            )
        }
    }.also {
        if (index in _spec.value.tapers.indices) {
            rememberTaperDefaults(lengthMm = lengthMm, setDiaMm = startDiaMm, letDiaMm = endDiaMm)
        }
        ensureOverall()
    }

    fun updateTaperKeyway(
        index: Int,
        widthMm: Float,
        depthMm: Float,
        lengthMm: Float,
        offsetFromSetMm: Float,
        spooned: Boolean,
    ) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            val updatedTapers = s.tapers.toMutableList().also { list ->
                list[index] = old.copy(
                    keywayWidthMm = max(0f, widthMm),
                    keywayDepthMm = max(0f, depthMm),
                    keywayLengthMm = max(0f, lengthMm),
                    keywayOffsetFromSetMm = max(0f, offsetFromSetMm),
                    keywaySpooned = spooned,
                )
            }
            s.copy(tapers = updatedTapers)
        }
    }


    fun updateTaperAuthoredReference(index: Int, reference: LinerAuthoredReference) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            if (old.authoredReference == reference) return@update s
            s.copy(
                tapers = s.tapers.toMutableList().also { l ->
                    l[index] = old.copy(authoredReference = reference)
                }
            )
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

            val afterRemoval = s.copy(tapers = s.tapers.toMutableList().apply { removeAt(idx) })
            val merge = afterRemoval.mergeBodiesAround(taper.startFromAftMm, taper.startFromAftMm + taper.lengthMm) { newId() }
            merge.removedIds.forEach { orderRemove(it) }
            merge.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }
            merge.spec
        }

        deleted?.let { snapshot ->
            orderRemove(snapshot.id)

            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            if (!isRedoing) redoHistory.clear()

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
        excludeFromOAL: Boolean = false,
        isAftEnd: Boolean = true
    ) {
        val id = newId()
        _spec.update { s ->
            // Excluded threads live outside the shaft envelope; they don't split in-shaft bodies.
            val split = if (!excludeFromOAL) s.splitBodiesAround(startMm, startMm + lengthMm) { newId() }
                        else BodySplitResult(s, emptyList(), emptyList())
            split.removedIds.forEach { orderRemove(it) }
            split.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }

            orderAdd(ComponentKind.THREAD, id)
            split.spec.copy(
                threads = listOf(
                    Threads(
                        id = id,
                        startFromAftMm = startMm,
                        majorDiaMm = max(0f, majorDiaMm),
                        pitchMm = max(0f, pitchMm),
                        lengthMm = max(0f, lengthMm),
                        excludeFromOAL = excludeFromOAL,
                        isAftEnd = isAftEnd
                    )
                ) + split.spec.threads
            )
        }
        rememberThreadDefaults(lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
        ensureOverall()
        ensureOrderCoversSpec()
        _selectedComponentId.value = id
    }

    fun updateThread(index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float) = _spec.update { s ->
        if (index !in s.threads.indices) s else {
            val old = s.threads[index]
            val newLength = max(0f, lengthMm)

            // For excluded threads the start position is always derived from isAftEnd + OAL,
            // never from a user-authored startMm. Use the same formula as syncExcludedThreadPositions()
            // so the position is correct inside this single _spec.update call, avoiding a transient
            // wrong position when manual OAL mode prevents ensureOverall() from re-syncing.
            val effectiveStart = if (old.excludeFromOAL) {
                if (old.isAftEnd) -newLength else s.overallLengthMm
            } else startMm

            s.copy(
                threads = s.threads.toMutableList().also { l ->
                    l[index] = old.copy(
                        startFromAftMm = effectiveStart,
                        lengthMm = newLength,
                        majorDiaMm = max(0f, majorDiaMm),
                        pitchMm = max(0f, pitchMm)
                    )
                }
            )
        }
    }.also {
        if (index in _spec.value.threads.indices) {
            rememberThreadDefaults(lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
        }
        ensureOverall()
    }

    fun setThreadExcludeFromOal(id: String, excludeFromOAL: Boolean) = _spec.update { s ->
        val idx = s.threads.indexOfFirst { it.id == id }
        if (idx == -1) s
        else s.copy(
            threads = s.threads.toMutableList().also { l ->
                val old = l[idx]
                l[idx] = old.copy(excludeFromOAL = excludeFromOAL)
            }
        ).syncExcludedThreadPositions()
    }.also { ensureOverall() }

    fun setThreadEndPosition(id: String, isAft: Boolean) = _spec.update { s ->
        val idx = s.threads.indexOfFirst { it.id == id }
        if (idx == -1) s
        else s.copy(
            threads = s.threads.toMutableList().also { l ->
                l[idx] = l[idx].copy(isAftEnd = isAft)
            }
        ).syncExcludedThreadPositions()
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

            val afterRemoval = s.copy(threads = s.threads.toMutableList().apply { removeAt(idx) })
            // Only merge bodies around in-shaft threads; excluded threads live outside the envelope.
            val merge = if (!thread.excludeFromOAL)
                afterRemoval.mergeBodiesAround(thread.startFromAftMm, thread.startFromAftMm + thread.lengthMm) { newId() }
            else BodySplitResult(afterRemoval, emptyList(), emptyList())
            merge.removedIds.forEach { orderRemove(it) }
            merge.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }
            merge.spec
        }

        deleted?.let { snapshot ->
            // Update cross-type order
            orderRemove(snapshot.id)

            // Push into undo stack, clear redo (new branch)
            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            if (!isRedoing) redoHistory.clear()

            // Maintain coverage + flags and show snackbar
            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // Liners
    fun addLinerAt(
        startMm: Float,
        lengthMm: Float,
        odMm: Float,
        reference: LinerAuthoredReference = LinerAuthoredReference.AFT,
    ) {
        val id = newId()
        _spec.update { s ->
            val len = max(0f, lengthMm)
            val split = s.splitBodiesAround(startMm, startMm + len) { newId() }
            split.removedIds.forEach { orderRemove(it) }
            split.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }

            orderAdd(ComponentKind.LINER, id)
            val od = max(0f, odMm)
            val liner = Liner(
                id = id,
                startFromAftMm = startMm,
                lengthMm = len,
                odMm = od,
                endMmPhysical = startMm + len,
                authoredReference = reference
            )
            split.spec.copy(liners = listOf(liner) + split.spec.liners)
        }
        rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
        ensureOverall()
        ensureOrderCoversSpec()
        _selectedComponentId.value = id
    }

    fun updateLiner(index: Int, startMm: Float, lengthMm: Float, odMm: Float) = _spec.update { s ->
        if (index !in s.liners.indices) s else {
            val old = s.liners[index]
            val len = max(0f, lengthMm)
            val od = max(0f, odMm)
            s.copy(
                liners = s.liners.toMutableList().also { l ->
                    l[index] = old.withPhysical(startMmPhysical = startMm, lengthMm = len, odMm = od)
                }
            )
        }
    }.also {
        if (index in _spec.value.liners.indices) {
            rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
        }
        ensureOverall()
    }

    /**
     * Apply a liner geometry change together with a negotiated adjacent-body adjustment
     * (shorten or grow), atomically. The new body geometry comes from
     * [ShaftSpec.linerBodyBoundaryAdjust]; explicit bodies are never split, so this
     * re-abuts the shared edge instead. See docs/COMPONENT_CONTRACT.md.
     */
    fun updateLinerWithBodyBoundary(
        linerIndex: Int,
        startMm: Float,
        lengthMm: Float,
        odMm: Float,
        bodyIndex: Int,
        bodyStartMm: Float,
        bodyLengthMm: Float,
    ) = _spec.update { s ->
        if (linerIndex !in s.liners.indices || bodyIndex !in s.bodies.indices) return@update s
        val len = max(0f, lengthMm)
        val od = max(0f, odMm)
        val oldLiner = s.liners[linerIndex]
        val oldBody = s.bodies[bodyIndex]
        s.copy(
            liners = s.liners.toMutableList().also {
                it[linerIndex] = oldLiner.withPhysical(startMmPhysical = startMm, lengthMm = len, odMm = od)
            },
            bodies = s.bodies.toMutableList().also {
                it[bodyIndex] = oldBody.copy(startFromAftMm = bodyStartMm, lengthMm = max(0f, bodyLengthMm))
            }
        )
    }.also {
        if (linerIndex in _spec.value.liners.indices) rememberLinerDefaults(lengthMm = lengthMm, odMm = odMm)
        ensureOverall()
    }

    fun updateLinerAuthoredReference(index: Int, reference: LinerAuthoredReference) = _spec.update { s ->
        if (index !in s.liners.indices) s else {
            val old = s.liners[index]
            if (old.authoredReference == reference) return@update s
            s.copy(
                liners = s.liners.toMutableList().also { l ->
                    l[index] = old.copy(authoredReference = reference)
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

    fun updateBodyLabel(index: Int, label: String?) = _spec.update { s ->
        if (index !in s.bodies.indices) s else {
            val old = s.bodies[index]
            val normalized = label?.trim()?.takeIf { it.isNotEmpty() }
            if (old.label == normalized) return@update s
            s.copy(
                bodies = s.bodies.toMutableList().also { l ->
                    l[index] = old.copy(label = normalized)
                }
            )
        }
    }

    fun updateTaperLabel(index: Int, label: String?) = _spec.update { s ->
        if (index !in s.tapers.indices) s else {
            val old = s.tapers[index]
            val normalized = label?.trim()?.takeIf { it.isNotEmpty() }
            if (old.label == normalized) return@update s
            s.copy(
                tapers = s.tapers.toMutableList().also { l ->
                    l[index] = old.copy(label = normalized)
                }
            )
        }
    }

    fun updateThreadLabel(index: Int, label: String?) = _spec.update { s ->
        if (index !in s.threads.indices) s else {
            val old = s.threads[index]
            val normalized = label?.trim()?.takeIf { it.isNotEmpty() }
            if (old.label == normalized) return@update s
            s.copy(
                threads = s.threads.toMutableList().also { l ->
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

            val afterRemoval = s.copy(liners = s.liners.toMutableList().apply { removeAt(idx) })
            val merge = afterRemoval.mergeBodiesAround(liner.startFromAftMm, liner.startFromAftMm + liner.lengthMm) { newId() }
            merge.removedIds.forEach { orderRemove(it) }
            merge.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }
            merge.spec
        }

        deleted?.let { snapshot ->
            orderRemove(snapshot.id)

            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            if (!isRedoing) redoHistory.clear()

            ensureOverall()
            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Coupler bolt slots — reference cutouts. No body-splitting, no OAL impact,
    // no collision. Add/update/remove mirror the other component trios.
    // ────────────────────────────────────────────────────────────────────────────

    fun addCouplerBoltSlotAt(
        startMm: Float,
        holeDiaMm: Float,
        count: Int,
        spacingMm: Float,
        through: Boolean = true,
        depthMm: Float = 0f,
        reference: SlotAuthoredReference = SlotAuthoredReference.FWD,
    ) {
        val id = newId()
        _spec.update { s ->
            orderAdd(ComponentKind.COUPLER_BOLT_SLOT, id)
            val slot = CouplerBoltSlot(
                id = id,
                startFromAftMm = max(0f, startMm),
                holeDiaMm = max(0f, holeDiaMm),
                count = count.coerceAtLeast(1),
                spacingMm = max(0f, spacingMm),
                through = through,
                depthMm = max(0f, depthMm),
                authoredReference = reference,
            )
            // Newest-on-top, like the other component lists.
            s.copy(couplerBoltSlots = listOf(slot) + s.couplerBoltSlots)
        }
        rememberSlotDefaults(holeDiaMm = holeDiaMm, spacingMm = spacingMm, depthMm = depthMm, count = count)
        // NOTE: deliberately no ensureOverall() — slots never drive OAL.
        ensureOrderCoversSpec()
        _selectedComponentId.value = id
    }

    fun updateCouplerBoltSlot(
        index: Int,
        startMm: Float,
        holeDiaMm: Float,
        count: Int,
        spacingMm: Float,
        through: Boolean,
        depthMm: Float,
    ) = _spec.update { s ->
        if (index !in s.couplerBoltSlots.indices) s else {
            val old = s.couplerBoltSlots[index]
            s.copy(
                couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                    l[index] = old.copy(
                        startFromAftMm = max(0f, startMm),
                        holeDiaMm = max(0f, holeDiaMm),
                        count = count.coerceAtLeast(1),
                        spacingMm = max(0f, spacingMm),
                        through = through,
                        depthMm = max(0f, depthMm),
                    )
                }
            )
        }
    }.also {
        if (index in _spec.value.couplerBoltSlots.indices) {
            rememberSlotDefaults(holeDiaMm = holeDiaMm, spacingMm = spacingMm, depthMm = depthMm, count = count)
        }
    }

    fun updateCouplerBoltSlotReference(index: Int, reference: SlotAuthoredReference) = _spec.update { s ->
        if (index !in s.couplerBoltSlots.indices) s else {
            val old = s.couplerBoltSlots[index]
            if (old.authoredReference == reference) return@update s
            s.copy(
                couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                    l[index] = old.copy(authoredReference = reference)
                }
            )
        }
    }

    fun updateCouplerBoltSlotShowRail(index: Int, show: Boolean) = _spec.update { s ->
        if (index !in s.couplerBoltSlots.indices) s else {
            val old = s.couplerBoltSlots[index]
            if (old.showDimensionRail == show) return@update s
            s.copy(
                couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                    l[index] = old.copy(showDimensionRail = show)
                }
            )
        }
    }

    fun updateCouplerBoltSlotLabel(index: Int, label: String?) = _spec.update { s ->
        if (index !in s.couplerBoltSlots.indices) s else {
            val old = s.couplerBoltSlots[index]
            val normalized = label?.trim()?.takeIf { it.isNotEmpty() }
            if (old.label == normalized) return@update s
            s.copy(
                couplerBoltSlots = s.couplerBoltSlots.toMutableList().also { l ->
                    l[index] = old.copy(label = normalized)
                }
            )
        }
    }

    /** Remove a [CouplerBoltSlot] by id with multi-step delete history support. */
    fun removeCouplerBoltSlot(id: String) {
        val specBefore = _spec.value
        val orderBefore = _componentOrder.value
        var deleted: LastDeleted.CouplerBoltSlot? = null

        _spec.update { s ->
            val idx = s.couplerBoltSlots.indexOfFirst { it.id == id }
            if (idx < 0) return@update s

            val slot = s.couplerBoltSlots[idx]
            val orderIdx = _componentOrder.value.indexOfFirst { it.id == id }
                .let { if (it < 0) 0 else it }

            deleted = LastDeleted.CouplerBoltSlot(
                value = slot,
                orderIndex = orderIdx,
                listIndex = idx,
                beforeSpec = specBefore,
                beforeOrder = orderBefore
            )

            // No body merge needed — slots never split bodies.
            s.copy(couplerBoltSlots = s.couplerBoltSlots.toMutableList().apply { removeAt(idx) })
        }

        deleted?.let { snapshot ->
            orderRemove(snapshot.id)

            deleteHistory.addLast(snapshot)
            if (deleteHistory.size > MAX_DELETE_HISTORY) {
                deleteHistory.removeFirst()
            }
            if (!isRedoing) redoHistory.clear()

            updateUndoRedoFlags()
            emitDeletedSnack(snapshot.kind)
        }
    }

    private fun rememberSlotDefaults(holeDiaMm: Float, spacingMm: Float, depthMm: Float, count: Int) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                slotHoleDiaMm = if (holeDiaMm > 0f) holeDiaMm else cur.slotHoleDiaMm,
                slotSpacingMm = if (spacingMm > 0f) spacingMm else cur.slotSpacingMm,
                slotDepthMm = if (depthMm > 0f) depthMm else cur.slotDepthMm,
                slotCount = if (count >= 1) count else cur.slotCount,
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Explicit snap helpers — never called automatically; only on user request
    // ────────────────────────────────────────────────────────────────────────────

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

    /**
     * Writes every saved shaft into a single zip at the SAF-picked [uri]
     * (Settings → "Back up all shafts…"). Result is reported via snackbar.
     */
    fun backupAllShaftsTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val result = runCatching {
                val docs = InternalStorage.list(app).mapNotNull { name ->
                    runCatching { name to InternalStorage.load(app, name) }.getOrNull()
                }
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    ShaftBackup.writeZip(
                        out = out,
                        docs = docs,
                        manifest = ShaftBackup.Manifest(
                            appVersion = BuildConfig.VERSION_NAME,
                            docFormatVersion = ShaftDocCodec.CURRENT_VERSION,
                            createdEpochMs = System.currentTimeMillis(),
                            documentCount = docs.size,
                        ),
                    )
                } ?: error("Could not open the selected location")
                docs.size
            }

            val msg = result.fold(
                onSuccess = { count ->
                    if (count > 0) "Backed up $count shaft${if (count == 1) "" else "s"}"
                    else "Backup written, but there were no saved shafts"
                },
                onFailure = {
                    VerboseLog.e(VerboseLog.Category.IO, "ShaftBackup") { "backup failed: ${it.message}" }
                    "Backup failed — could not write the file"
                },
            )
            _uiEvents.emit(UiEvent.ShowSnackbarMessage(msg))
        }
    }

    /**
     * Restores shafts from a backup zip at the SAF-picked [uri]
     * (Settings → "Restore from backup…"). Never overwrites: identical docs are
     * skipped, name collisions are saved as "<name> (restored)".
     */
    fun restoreShaftsFromBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val result = runCatching {
                val contents = app.contentResolver.openInputStream(uri)?.use { input ->
                    ShaftBackup.readZip(input)
                } ?: error("Could not open the selected file")

                if (contents.docs.isEmpty()) return@runCatching null

                ShaftBackup.restoreInto(
                    dir = InternalStorage.dir(app.filesDir),
                    docs = contents.docs,
                ) { raw -> runCatching { ShaftDocCodec.decode(raw) }.isSuccess }
            }

            val msg = result.fold(
                onSuccess = { report ->
                    when {
                        report == null -> "No shaft files found in that backup"
                        else -> buildString {
                            val added = report.restoredCount + report.renamedCount
                            append("Restored $added shaft${if (added == 1) "" else "s"}")
                            if (report.renamedCount > 0) append(" (${report.renamedCount} renamed)")
                            if (report.skippedIdenticalCount > 0) append(", ${report.skippedIdenticalCount} already present")
                            if (report.failedCount > 0) append(", ${report.failedCount} unreadable")
                        }
                    }
                },
                onFailure = {
                    VerboseLog.e(VerboseLog.Category.IO, "ShaftBackup") { "restore failed: ${it.message}" }
                    "Restore failed — could not read the file"
                },
            )
            _uiEvents.emit(UiEvent.ShowSnackbarMessage(msg))
        }
    }

    /** Export the current state as a JSON string (mm spec + unit metadata + runout config). */
    fun exportJson(): String = ShaftDocCodec.encodeV1(
        ShaftDocCodec.ShaftDocV1(
            preferredUnit = _unit.value,
            unitLocked = _unitLocked.value,
            jobNumber = _jobNumber.value,
            customer = _customer.value,
            vessel = _vessel.value,
            shaftPosition = _shaftPosition.value,
            notes = _notes.value,
            spec = _spec.value,
            runoutConfig = _runoutConfig.value,
            wearRecord = _wearRecord.value,
            runoutReadings = _runoutReadings.value,
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
        seedSessionAddDefaultsFromSpec(decoded.spec)

        _unitLocked.value = decoded.unitLocked
        decoded.preferredUnit?.let { setUnit(it, persist = false) }

        _jobNumber.value = decoded.jobNumber
        _customer.value = decoded.customer
        _vessel.value = decoded.vessel
        _shaftPosition.value = decoded.shaftPosition
        _notes.value = decoded.notes
        _runoutConfig.value = decoded.runoutConfig
        // Already orphan-filtered against decoded.spec.liners inside ShaftDocCodec.decode().
        _wearRecord.value = decoded.wearRecord
        _runoutReadings.value = decoded.runoutReadings

        // Derive OAL mode from the document instead of leaking the previous session's
        // flag: an authored OAL beyond the content end must be treated as manual, or
        // the auto-sync path would snap it back down to the content end on open.
        _overallIsManual.value =
            decoded.spec.overallLengthMm > coverageEndMm(decoded.spec) + 1e-3f

        // Reset order to this document's components only
        _componentOrder.value = emptyList()
        ensureOrderCoversSpec(decoded.spec)
        markDocumentSaved()
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

        val blankSpec = ShaftSpec()
        _spec.value = blankSpec

        // Mirror envelope defaults used by the existing start/new seed path.
        _unitLocked.value = true
        setUnit(UnitSystem.INCHES, persist = false)

        _jobNumber.value = ""
        _customer.value = ""
        _vessel.value = ""
        _shaftPosition.value = ShaftPosition.OTHER
        _runoutConfig.value = RunoutConfig()
        _wearRecord.value = WearRecord()
        _runoutReadings.value = RunoutReadings()
        _notes.value = ""
        _overallIsManual.value = false

        _componentOrder.value = emptyList()
        ensureOrderCoversSpec(blankSpec)
        _currentDocumentName.value = null
        markDocumentSaved()
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
        s.threads.filter { !it.excludeFromOAL }
                 .forEach { end = max(end, it.startFromAftMm + it.lengthMm) }
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
            addAll(s.couplerBoltSlots.map { it.id })
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
        addMissing(ComponentKind.COUPLER_BOLT_SLOT, s.couplerBoltSlots.map { it.id })

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

        /**
         * Derive the missing taper diameter from the taper rate.
         *
         * Parameters are the model's axial start/end diameters (AFT → FWD). Exactly which of
         * them is the Small End of Taper depends on which shaft end the taper sits at:
         * - AFT-end taper: SET faces the AFT end → SET is at the *start* (smallEndAtStart = true).
         * - FWD-end taper: SET faces the FWD end → SET is at the *end*   (smallEndAtStart = false).
         *
         * Rules:
         * - If both diameters > 0: rate is ignored.
         * - If only one is provided: the other is derived so that |start − end| = rate × length
         *   and the derived diameter respects [smallEndAtStart] (SET < LET, always).
         * - If neither > 0: returns 0f for both.
         * - Rate is parsed from [rateText] (formats: "1:12", "3/4", "0.0833", "1"). A "1:12"
         *   ratio is interpreted as (1 unit diameter change per 12 units length), so rate = 1/12.
         * - If rate text is blank or unparseable the raw diameters are returned unchanged.
         * - Derived diameters are clamped ≥ 0.
         */
        fun deriveTaperDiameters(
            startDiaMm: Float,
            endDiaMm: Float,
            lengthMm: Float,
            rateText: String,
            smallEndAtStart: Boolean = true,
        ): Pair<Float, Float> {
            // Both provided: rate is ignored per contract.
            if (startDiaMm > 0f && endDiaMm > 0f) return startDiaMm to endDiaMm

            val rate = parseRateText(rateText) ?: return startDiaMm to endDiaMm
            if (lengthMm <= 0f) return startDiaMm to endDiaMm

            // end = start + sign·delta, where sign makes the SET the smaller diameter.
            val diaDelta = rate * lengthMm
            val sign = if (smallEndAtStart) 1f else -1f

            return when {
                startDiaMm > 0f -> startDiaMm to maxOf(0f, startDiaMm + sign * diaDelta)
                endDiaMm > 0f -> maxOf(0f, endDiaMm - sign * diaDelta) to endDiaMm
                else -> 0f to 0f
            }
        }

        /**
         * True when the taper's Small End faces the AFT end of the shaft — i.e. the taper sits
         * in the AFT half. Mirrors the UI's SET/LET labeling rule
         * ([com.android.shaftschematic.ui.input.taperSetLetMapping]): mid-point ≤ OAL/2 → AFT
         * taper (SET at start). Falls back to AFT when OAL is unknown (0).
         */
        fun taperSmallEndAtStart(startMm: Float, lengthMm: Float, overallLengthMm: Float): Boolean {
            if (overallLengthMm <= 0f) return true
            return startMm + lengthMm * 0.5f <= overallLengthMm * 0.5f
        }

        /**
         * Parse a taper rate string into a dimensionless ratio (diameter change per length unit).
         * Supports: "1:12" → 1/12, "3/4" → 0.75, "0.0833" → 0.0833, "1" → 1/12 (legacy bare int).
         */
        fun parseRateText(text: String): Float? = parseTaperRateText(text, allowAmbiguousBareOne = true)
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

        isRedoing = true
        try {
            when (snapshot) {
                is LastDeleted.Body -> removeBody(snapshot.id)
                is LastDeleted.Taper -> removeTaper(snapshot.id)
                is LastDeleted.Thread -> removeThread(snapshot.id)
                is LastDeleted.Liner -> removeLiner(snapshot.id)
                is LastDeleted.CouplerBoltSlot -> removeCouplerBoltSlot(snapshot.id)
            }
        } finally {
            isRedoing = false
        }

        updateUndoRedoFlags()
    }
}
