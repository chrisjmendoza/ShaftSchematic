package com.android.shaftschematic.ui.viewmodel
import com.android.shaftschematic.settings.AppThemeMode
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
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.geom.UNDERCUT_EXAGGERATION_MAX_FRAC
import com.android.shaftschematic.geom.clampPitAcrossFrac
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.UndercutStyle
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseTaperRateText
import com.android.shaftschematic.util.parseToMm
import com.android.shaftschematic.util.VerboseLog
import android.util.Log
import com.android.shaftschematic.data.AutosaveManager
import com.android.shaftschematic.data.isDefaultSession
import com.android.shaftschematic.data.shouldWriteDraft
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
import kotlin.math.min

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
    // Draft ring state (up to 3 unsaved sessions, newest-first). See
    // docs/Autosave_Incident_2026-07-25.md.
    private val _drafts = MutableStateFlow<List<AutosaveManager.DraftEntry>>(emptyList())
    val drafts: StateFlow<List<AutosaveManager.DraftEntry>> = _drafts.asStateFlow()

    // Per-editing-session draft identity. Minted on construction, re-minted on newDocument()
    // and importJson() so working on one document can never touch another's draft entry.
    private var currentDraftId: String = UUID.randomUUID().toString()

    // Full snapshot of the last saved-to-file / freshly-loaded state — the dirty gate's
    // baseline. The autosave observer writes a draft only when the live snapshot differs.
    // A StateFlow (not a plain var) so [hasUnsavedChanges] re-evaluates the moment a save
    // reseats the baseline, not only on the next edit.
    private val _savedSnapshot = MutableStateFlow<AutosaveManager.SessionSnapshot?>(null)

    // Whether currentDraftId currently has a persisted entry in the ring. Used to remove the
    // entry exactly once on a dirty→clean transition (avoids hammering DataStore).
    private var draftPersisted: Boolean = false

    /** Build a snapshot of the current live session state (mirrors the autosave combine). */
    private fun buildCurrentSnapshot(): AutosaveManager.SessionSnapshot =
        AutosaveManager.SessionSnapshot(
            shaftSpec = _spec.value,
            unitSystem = _unit.value,
            shaftPosition = _shaftPosition.value,
            customer = _customer.value,
            vessel = _vessel.value,
            jobNumber = _jobNumber.value,
            notes = _notes.value,
            runoutConfig = _runoutConfig.value,
            unitLocked = _unitLocked.value,
            overallIsManual = _overallIsManual.value,
            wearRecord = _wearRecord.value,
            runoutReadings = _runoutReadings.value,
            undercutRecord = _undercutRecord.value,
        )

    /**
     * Restore a specific draft into the editor (StartScreen picker). The session stays "dirty"
     * (savedSnapshot is left as-is) so the draft is retained until an explicit save.
     */
    fun continueDraft(draftId: String) {
        val entry = _drafts.value.firstOrNull { it.draftId == draftId } ?: return
        restoreSnapshot(entry.snapshot)
        currentDraftId = entry.draftId
        _currentDocumentName.value = entry.documentName
        draftPersisted = true
        // Session boundary: undo starts fresh from the restored draft.
        clearEditHistory()
    }

    /**
     * Discard exactly one draft by [draftId]. If it is the current/restored session, also reset
     * the editor to a blank document (mints a fresh draft identity).
     */
    fun discardDraft(draftId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AutosaveManager.removeDraft(getApplication(), draftId) }
            val wasCurrent = draftId == currentDraftId
            _drafts.value = withContext(Dispatchers.IO) { AutosaveManager.loadDrafts(getApplication()) }
            if (wasCurrent) {
                newDocument()
            }
        }
    }

    /** Discard the current session's draft (no-arg convenience for legacy callers). */
    fun discardDraft() = discardDraft(currentDraftId)
    // ────────────────────────────────────────────────────────────────────────────
    // Unsaved-changes tracking + current document name
    // ────────────────────────────────────────────────────────────────────────────

    // Filename (with .shaft extension) of the last save/open, or null when the document
    // has never been saved. Used to enable silent quick-save without reprompting for a name.
    private val _currentDocumentName = MutableStateFlow<String?>(null)
    val currentDocumentName: StateFlow<String?> = _currentDocumentName.asStateFlow()

    fun setCurrentDocumentName(name: String?) { _currentDocumentName.value = name }

    /**
     * Reactive companion to [hasUnsavedWork]: true while the live session differs from the
     * last saved/loaded baseline. Drives the editor's document-title dirty asterisk. Updated
     * by the init-block combine; compares the same full snapshots as the autosave dirty gate.
     */
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    fun markDocumentSaved() {
        // Full-snapshot baseline shared by BOTH the autosave dirty gate and hasUnsavedWork():
        // after a real save/load the session is "clean", so the observer stops writing (and
        // removes) this session's draft, and no unsaved-changes prompt fires.
        _savedSnapshot.value = buildCurrentSnapshot()
        // Remove this session's draft-ring entry NOW. The autosave observer only reaches its
        // dirty→clean removal branch on the next combine emission, which never comes when the
        // user saves and navigates away without another edit — deferring the removal would
        // leave saved documents sitting in "Unsaved drafts" as stale "Untitled draft" rows.
        // newDocument()/importJson()
        // drop draftPersisted to false *before* calling this, so an open/new never deletes the
        // previous session's safety-net draft.
        if (draftPersisted) {
            draftPersisted = false
            val idToRemove = currentDraftId
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        AutosaveManager.removeDraft(getApplication(), idToRemove)
                    }
                    _drafts.value = withContext(Dispatchers.IO) {
                        AutosaveManager.loadDrafts(getApplication())
                    }
                } catch (_: CancellationException) {
                    // ignore
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    /**
     * Whether the session differs from the last saved/loaded state. Uses the SAME full-snapshot
     * comparison as the autosave dirty gate ([shouldWriteDraft]) so *every* tracked field —
     * spec, metadata, position, unit-lock, OAL mode, wear record, runout readings/config —
     * counts as unsaved work; a partial comparison risks missing wear/runout edits and letting
     * unsaved work slip past the dirty gate. See docs/Autosave_Incident_2026-07-25.md.
     */
    fun hasUnsavedWork(): Boolean {
        if (isSessionDefault()) return false
        return shouldWriteDraft(buildCurrentSnapshot(), _savedSnapshot.value)
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

    // Sizing-curve anchor heights (paper inches): what a 4" / 8" shaft draws by default.
    internal val _pdfCurveLoHeightIn = MutableStateFlow(PdfPrefs().curveLoHeightIn)
    val pdfCurveLoHeightIn: StateFlow<Float> = _pdfCurveLoHeightIn.asStateFlow()
    internal val _pdfCurveHiHeightIn = MutableStateFlow(PdfPrefs().curveHiHeightIn)
    val pdfCurveHiHeightIn: StateFlow<Float> = _pdfCurveHiHeightIn.asStateFlow()

    internal val _pdfExportMode = MutableStateFlow(PdfExportMode.Standard)
    val pdfExportMode: StateFlow<PdfExportMode> = _pdfExportMode.asStateFlow()

    // Blank-draft (write-in) export/print. Deliberately NOT persisted: a forgotten sticky
    // toggle would silently blank every future export. Defaults off each session.
    internal val _pdfBlankDraft = MutableStateFlow(false)
    val pdfBlankDraft: StateFlow<Boolean> = _pdfBlankDraft.asStateFlow()

    internal val _previewBlackWhiteOnly = MutableStateFlow(false)
    val previewBlackWhiteOnly: StateFlow<Boolean> = _previewBlackWhiteOnly.asStateFlow()

    // Appearance (app-wide theme; MainActivity collects these to pick the color scheme)
    internal val _themeMode = MutableStateFlow(AppThemeMode.LIGHT)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    internal val _highContrast = MutableStateFlow(false)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    // Undercut drawing style (on-screen sheet only; PDF keeps standard drawing colors)
    internal val _undercutStyle = MutableStateFlow(UndercutStyle())
    val undercutStyle: StateFlow<UndercutStyle> = _undercutStyle.asStateFlow()

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

    /**
     * "Shaft height" slider — exaggerate or shrink the drawn shaft on every drawing
     * output: schematic, runout, and consolidated sheets (one per-job value). Clamped to
     * the geom slider bounds; the composer additionally hard-caps the drawn height at 1.5"
     * and the page budget.
     */
    fun setRunoutHeightScale(scale: Float) {
        _runoutConfig.update {
            it.copy(
                heightScale = scale.coerceIn(
                    com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MIN,
                    com.android.shaftschematic.geom.PROFILE_HEIGHT_SCALE_MAX,
                )
            )
        }
    }

    /** "Keep liners proportional lengthwise" — see [RunoutConfig.linersProportional]. */
    fun setLinersProportional(proportional: Boolean) {
        _runoutConfig.update { it.copy(linersProportional = proportional) }
    }

    /** "Liner compression" slider — see [RunoutConfig.linerCompression]. */
    fun setLinerCompression(fraction: Float) {
        _runoutConfig.update { it.copy(linerCompression = fraction.coerceIn(0f, 1f)) }
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

    // ── Wear pits (the "X" markers) ───────────────────────────────────────────
    // Stored in the same reference-only [WearRecord] as wear spots (so they ride the same
    // autosave/snapshot/import paths), but keyed by *resolved component id* — a pit can sit on
    // a liner, taper, or body (explicit or auto), unlike a spot (liner-only). No geometry side
    // effects; orphan pits (component no longer resolves) are skipped at the render layer, same
    // posture as runout readings. See model/WearSpot.kt (WearPit) and geom/WearPitMath.kt.

    /**
     * Drop a new pit "X" on [componentId] at component-local [axialMm] (from the AFT edge) and
     * [acrossFrac] (0 = top outline .. 1 = bottom), with the given [size]. `acrossFrac` is
     * clamped to the interior band ([clampPitAcrossFrac]) and `axialMm` to non-negative.
     */
    fun addWearPit(componentId: String, axialMm: Float, acrossFrac: Float, size: PitSize) {
        _wearRecord.update { rec ->
            rec.copy(
                pits = rec.pits + WearPit(
                    componentId = componentId,
                    axialMm = max(0f, axialMm),
                    acrossFrac = clampPitAcrossFrac(acrossFrac),
                    size = size,
                )
            )
        }
    }

    /** Remove a pit by [id]. Confirm-free — the detail canvas removes a pit by tapping its "X". */
    fun removeWearPit(id: String) {
        _wearRecord.update { rec -> rec.copy(pits = rec.pits.filterNot { it.id == id }) }
    }

    // ── Wear diameter readings (measured-Ø callouts) ─────────────────────────
    // Same reference-only posture and storage as pits: keyed by resolved component id,
    // component-local axial position, no geometry side effects, render-layer orphans.
    // See model/WearSpot.kt (WearDiaReading) and geom/WearDiaCalloutLayout.kt.

    /**
     * Record a measured diameter [diaMm] on [componentId] at component-local [axialMm]
     * (from the AFT edge). [diaMm] is stored verbatim — user inputs are sacred; only the
     * tap-derived [axialMm] is coerced non-negative (coarse-gesture clamp).
     */
    fun addWearDiaReading(componentId: String, axialMm: Float, diaMm: Float) {
        _wearRecord.update { rec ->
            rec.copy(
                diaReadings = rec.diaReadings + WearDiaReading(
                    componentId = componentId,
                    axialMm = max(0f, axialMm),
                    diaMm = diaMm,
                )
            )
        }
    }

    /** Replace an existing reading's measured value by [id]. No-op if the id is absent. */
    fun updateWearDiaReading(id: String, diaMm: Float) {
        _wearRecord.update { rec ->
            rec.copy(diaReadings = rec.diaReadings.map { if (it.id == id) it.copy(diaMm = diaMm) else it })
        }
    }

    /** Remove a reading by [id]. Confirm-free — deleted from its edit dialog. */
    fun removeWearDiaReading(id: String) {
        _wearRecord.update { rec -> rec.copy(diaReadings = rec.diaReadings.filterNot { it.id == id }) }
    }

    // ── Worn sections (consolidated runout/wear sheet) ─────────────────────────
    // Reference-only, same posture as pits/diaReadings: plain _wearRecord updates, no
    // geometry side effects. Shaft-space canonical (no component key → no orphans).
    // See model/WornSection.kt and docs/RunoutSheet.md (Worn Sections).

    /**
     * Add a designated worn section. [diaMm] values are the machinist's typed measurements,
     * stored verbatim in list order. Returns the new id so the editor can follow the row.
     */
    fun addWornSection(
        startFromAftMm: Float,
        lengthMm: Float,
        diaMm: List<Float>,
        reference: UndercutReference,
    ): String {
        val section = WornSection(
            startFromAftMm = max(0f, startFromAftMm),
            lengthMm = max(0f, lengthMm),
            diaMm = diaMm,
            authoredReference = reference,
        )
        _wearRecord.update { rec -> rec.copy(wornSections = rec.wornSections + section) }
        return section.id
    }

    /** Replace a section's span and measured values by [id]. No-op if the id is absent. */
    fun updateWornSection(id: String, startFromAftMm: Float, lengthMm: Float, diaMm: List<Float>) {
        _wearRecord.update { rec ->
            rec.copy(wornSections = rec.wornSections.map {
                if (it.id == id) it.copy(
                    startFromAftMm = max(0f, startFromAftMm),
                    lengthMm = max(0f, lengthMm),
                    diaMm = diaMm,
                ) else it
            })
        }
    }

    /**
     * Switch which S.E.T. the Distance field displays against — display metadata only,
     * canonical position untouched (the WearSpotReference pattern).
     */
    fun updateWornSectionReference(id: String, reference: UndercutReference) {
        _wearRecord.update { rec ->
            rec.copy(wornSections = rec.wornSections.map {
                if (it.id == id) it.copy(authoredReference = reference) else it
            })
        }
    }

    /** Remove a section by [id]. Confirm-free — deleted from its edit dialog. */
    fun removeWornSection(id: String) {
        _wearRecord.update { rec -> rec.copy(wornSections = rec.wornSections.filterNot { it.id == id }) }
    }

    // ── Undercut drawing record ────────────────────────────────────────────────
    // Reference-only data, same posture as _wearRecord/_runoutReadings above: plain state
    // updates, no geometry side effects, no ensureOverall/auto-body interaction. Undercuts
    // have no component key, so there is no orphan concept here. See
    // docs/UndercutDrawing_PLAN.md §2, §6.

    private val _undercutRecord = MutableStateFlow(UndercutRecord())
    val undercutRecord: StateFlow<UndercutRecord> = _undercutRecord.asStateFlow()

    /**
     * Record a new undercut section at [startFromAftMm] (canonical shaft space) with
     * [lengthMm] and Ø unentered (0). Returns the new undercut's id so the caller can
     * immediately open its detail overlay.
     *
     * [reference]/[referenceLinerId] are the authoring reference the distance was entered
     * against — display metadata only, stored verbatim. They default to the SET-based
     * posture ([UndercutReference.AFT_SET], no liner) used by the tab's global "Add
     * undercut" button; adding from inside a liner's detail strip passes that liner's
     * `LINER_*` reference instead.
     */
    fun addUndercut(
        startFromAftMm: Float,
        lengthMm: Float,
        reference: UndercutReference = UndercutReference.AFT_SET,
        referenceLinerId: String = "",
    ): String {
        val undercut = Undercut(
            startFromAftMm = startFromAftMm,
            lengthMm = lengthMm,
            diaMm = 0f,
            authoredReference = reference,
            referenceLinerId = referenceLinerId,
        )
        _undercutRecord.update { rec -> rec.copy(undercuts = rec.undercuts + undercut) }
        return undercut.id
    }

    /**
     * Replace an existing undercut's fields by [id]. Fields are stored **verbatim** — golden
     * rule: no snap/round/derive ever rewrites a typed value, including [diaMm] (0 = placed,
     * not yet measured). No-op if the id is not found.
     */
    fun updateUndercut(id: String, startFromAftMm: Float, lengthMm: Float, diaMm: Float, note: String) {
        _undercutRecord.update { rec ->
            rec.copy(
                undercuts = rec.undercuts.map { u ->
                    if (u.id != id) u else u.copy(
                        startFromAftMm = startFromAftMm,
                        lengthMm = lengthMm,
                        diaMm = diaMm,
                        note = note,
                    )
                }
            )
        }
    }

    /**
     * Update an undercut's authored "Measure from" reference by [id]. Display-only — same
     * pattern as [updateWearSpotReference]: it never touches [Undercut.startFromAftMm],
     * only which reference point the "Distance" field re-projects against.
     *
     * [referenceLinerId] is the liner the distance converts against for the `LINER_*`
     * references; both values are stored **verbatim**. Callers pass an empty id for the
     * SET references, so switching back to a S.E.T. also drops the stale liner key.
     */
    fun updateUndercutReference(
        id: String,
        reference: UndercutReference,
        referenceLinerId: String = "",
    ) {
        _undercutRecord.update { rec ->
            rec.copy(
                undercuts = rec.undercuts.map { u ->
                    if (u.id != id ||
                        (u.authoredReference == reference && u.referenceLinerId == referenceLinerId)
                    ) u
                    else u.copy(authoredReference = reference, referenceLinerId = referenceLinerId)
                }
            )
        }
    }

    /** Remove an undercut by [id]. Confirm-free, as authored in its edit card. */
    fun removeUndercut(id: String) {
        _undercutRecord.update { rec -> rec.copy(undercuts = rec.undercuts.filterNot { it.id == id }) }
    }

    /**
     * Set this sheet's drawn-depth exaggeration ([UndercutRecord.exaggerationFrac]), clamped
     * to `0..`[UNDERCUT_EXAGGERATION_MAX_FRAC]. Display-only styling for the undercut
     * drawing: the sheet's deepest cut draws at this fraction of its local surface Ø and
     * shallower cuts scale relative to it (`normalizedNotchFloorDiaMm`). It never touches a
     * stored or printed Ø, so the golden rule is untouched — but it is per-document, so it
     * lives in the record rather than in app prefs.
     */
    fun setUndercutExaggeration(frac: Float) {
        val clamped = frac.coerceIn(0f, UNDERCUT_EXAGGERATION_MAX_FRAC)
        _undercutRecord.update { rec ->
            if (rec.exaggerationFrac == clamped) rec else rec.copy(exaggerationFrac = clamped)
        }
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
    fun gapToNextAnchorMm(positionMm: Float, minimumMm: Float = DEFAULT_ADD_GAP_MM): Float =
        gapToNextAnchorMm(_spec.value, positionMm, minimumMm)

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

    // ────────────────────────────────────────────────────────────────────────────
    // Session-scoped Undo / Redo (v2) — covers ALL drawing-state edits, not just deletes.
    //
    // A single [SessionHistory] over [EditState] snapshots (spec + wear + runout + order +
    // OAL mode). Snapshots are recorded centrally by a collector over those flows (see init),
    // with time-based coalescing living in SessionHistory (a typing burst = one undo step).
    // Undo/redo apply a restored EditState back onto the flows; the collector's re-emission
    // is a no-op because SessionHistory.record ignores states equal to its current head
    // (isRestoringHistory is a belt-and-suspenders guard around the application block).
    // History is dropped at document/session boundaries (newShaft/importJson/newDocument/
    // continueDraft and the autosave auto-restore).
    // ────────────────────────────────────────────────────────────────────────────
    private val editHistory = SessionHistory<EditState>()

    // Guards the collector while undo/redo applies a restored EditState to the flows so the
    // restore does not itself get recorded as a new edit. Single-threaded (Main), so a plain
    // flag suffices; SessionHistory's identical-state no-op is the authoritative backstop.
    private var isRestoringHistory = false

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Snapshot the current undoable slice of editor state. */
    private fun currentEditState(): EditState = EditState(
        spec = _spec.value,
        wearRecord = _wearRecord.value,
        runoutReadings = _runoutReadings.value,
        undercutRecord = _undercutRecord.value,
        componentOrder = _componentOrder.value,
        overallIsManual = _overallIsManual.value,
    )

    private fun updateHistoryFlags() {
        _canUndo.value = editHistory.canUndo
        _canRedo.value = editHistory.canRedo
    }

    /** Drop all undo/redo history. Called at every document/session boundary. */
    private fun clearEditHistory() {
        editHistory.clear()
        updateHistoryFlags()
    }

    /** Apply a restored [EditState] to every undoable flow without re-recording it. */
    private fun applyEditState(e: EditState) {
        isRestoringHistory = true
        try {
            _spec.value = e.spec
            _wearRecord.value = e.wearRecord
            _runoutReadings.value = e.runoutReadings
            _undercutRecord.value = e.undercutRecord
            _componentOrder.value = e.componentOrder
            _overallIsManual.value = e.overallIsManual
        } finally {
            isRestoringHistory = false
        }
    }

    /** Undo the most recent edit step (spec / wear / runout / order / OAL mode). */
    fun undoEdit() {
        val restored = editHistory.undo(currentEditState()) ?: return
        applyEditState(restored)
        updateHistoryFlags()
    }

    /** Redo the most recently undone edit step. */
    fun redoEdit() {
        val restored = editHistory.redo(currentEditState()) ?: return
        applyEditState(restored)
        updateHistoryFlags()
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Autosave and SettingsStore integration
    // ────────────────────────────────────────────────────────────────────────────
    init {
        // Seed the dirty-gate baseline to the current (blank) session so a pristine, untouched
        // start never writes a draft. markDocumentSaved()/importJson()/newDocument() reseat it.
        _savedSnapshot.value = buildCurrentSnapshot()

        // --- AUTOSAVE RESTORE + OBSERVER ---
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AutosaveManager.loadDrafts(getApplication())
            }
            _drafts.value = loaded
            val newest = loaded.firstOrNull()
            // Auto-restore the newest draft only into a fresh/default session.
            if (newest != null && isSessionDefault()) {
                try {
                    restoreSnapshot(newest.snapshot)
                    currentDraftId = newest.draftId
                    _currentDocumentName.value = newest.documentName
                    draftPersisted = true
                    // Session boundary: undo must not cross back into the pre-restore blank.
                    clearEditHistory()
                } catch (_: Exception) {}
            } else if (newest != null) {
                VerboseLog.d(VerboseLog.Category.IO, "Autosave") {
                    "autosave restore skipped (session already initialized)"
                }
            }
        }
        // Live full-session snapshot stream, shared by the (debounced) autosave observer and
        // the (immediate) hasUnsavedChanges dirty flag.
        @Suppress("UNCHECKED_CAST")
        // Flow.combine overload for >5 flows returns Array<Any?>
        val sessionSnapshotFlow = combine(
            spec, unit, shaftPosition, customer, vessel, jobNumber, notes,
            runoutConfig, unitLocked, overallIsManual, wearRecord, runoutReadings, undercutRecord
        ) { values: Array<Any?> ->
            check(values.size == 13) { "Autosave combine expected 13 values, got ${values.size}" }

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
            val undercuts = values[12] as UndercutRecord

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
                undercutRecord = undercuts,
            )
        }

        // Reactive dirty flag for the document title bar: same full-snapshot comparison as the
        // autosave dirty gate, but undebounced (the asterisk should track keystrokes) and also
        // recomputed when a save reseats the baseline.
        viewModelScope.launch {
            combine(sessionSnapshotFlow, _savedSnapshot) { live, saved ->
                // A factory-default session is never "unsaved work", even when the async
                // settings restore makes it differ from the seeded baseline (unit flip).
                shouldWriteDraft(live, saved) && !live.isDefaultSession()
            }.collect { _hasUnsavedChanges.value = it }
        }

        viewModelScope.launch {
            sessionSnapshotFlow
                .debounce(1500)
                .collectLatest { snapshot ->
                    try {
                        // Dirty gate: only persist a draft when the session differs from the last
                        // saved/loaded state. A freshly-loaded pristine document (savedSnapshot ==
                        // snapshot) can never overwrite an existing draft. When the state returns
                        // to clean, remove this session's draft entry exactly once.
                        // A factory-default session never writes a draft: the async settings
                        // restore flips the unit after the baseline is seeded, which would
                        // otherwise persist a phantom blank "Untitled draft" on every empty-ring
                        // launch. The else-if also removes an already-persisted phantom (restored
                        // on a later launch) the first time its debounced snapshot arrives.
                        if (shouldWriteDraft(snapshot, _savedSnapshot.value) && !snapshot.isDefaultSession()) {
                            AutosaveManager.saveDraft(
                                getApplication(),
                                AutosaveManager.DraftEntry(
                                    draftId = currentDraftId,
                                    documentName = _currentDocumentName.value,
                                    updatedAtEpochMs = System.currentTimeMillis(),
                                    snapshot = snapshot,
                                ),
                            )
                            draftPersisted = true
                            _drafts.value = AutosaveManager.loadDrafts(getApplication())
                        } else if (draftPersisted) {
                            AutosaveManager.removeDraft(getApplication(), currentDraftId)
                            draftPersisted = false
                            _drafts.value = AutosaveManager.loadDrafts(getApplication())
                        }
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

        // --- SESSION UNDO/REDO RECORDER ---
        // Central recording of every undoable edit. No debounce operator here on purpose —
        // coalescing (bursts → one step) is SessionHistory's job, driven by the wall clock.
        // The first emission seeds the history head; subsequent genuine changes become steps.
        // Restores (undo/redo) re-emit the restored state, but SessionHistory.record no-ops it
        // (equal to head) and isRestoringHistory guards the application block as well.
        viewModelScope.launch {
            @Suppress("UNCHECKED_CAST")
            // Flow.combine overload for >5 flows returns Array<Any?>
            combine(
                spec, wearRecord, runoutReadings, undercutRecord, componentOrder, overallIsManual
            ) { values: Array<Any?> ->
                check(values.size == 6) { "Edit-history combine expected 6 values, got ${values.size}" }
                EditState(
                    spec = values[0] as ShaftSpec,
                    wearRecord = values[1] as WearRecord,
                    runoutReadings = values[2] as RunoutReadings,
                    undercutRecord = values[3] as UndercutRecord,
                    componentOrder = values[4] as List<ComponentKey>,
                    overallIsManual = values[5] as Boolean,
                )
            }.collect { edit ->
                if (isRestoringHistory) return@collect
                editHistory.record(edit, System.currentTimeMillis())
                updateHistoryFlags()
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
            SettingsStore.pdfCurveLoHeightInFlow(getApplication()).collectLatest { persisted ->
                _pdfCurveLoHeightIn.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(curveLoHeightIn = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfCurveHiHeightInFlow(getApplication()).collectLatest { persisted ->
                _pdfCurveHiHeightIn.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(curveHiHeightIn = persisted) }
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
            SettingsStore.themeModeFlow(getApplication()).collectLatest { persisted ->
                _themeMode.value = persisted
            }
        }

        viewModelScope.launch {
            SettingsStore.highContrastFlow(getApplication()).collectLatest { persisted ->
                _highContrast.value = persisted
            }
        }

        viewModelScope.launch {
            SettingsStore.undercutLineArtFlow(getApplication()).collectLatest { persisted ->
                _undercutStyle.value = _undercutStyle.value.copy(lineArt = persisted)
            }
        }

        viewModelScope.launch {
            SettingsStore.undercutShadeColorFlow(getApplication()).collectLatest { persisted ->
                _undercutStyle.value = _undercutStyle.value.copy(shadeColor = persisted)
            }
        }

        viewModelScope.launch {
            SettingsStore.undercutShadeIntensityFlow(getApplication()).collectLatest { persisted ->
                _undercutStyle.value = _undercutStyle.value.copy(intensity = persisted)
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
    // Single definition of "factory-default session" — the pure predicate in DraftRing.kt,
    // shared with the autosave observer and the hasUnsavedChanges flag.
    private fun isSessionDefault(): Boolean = buildCurrentSnapshot().isDefaultSession()

    private fun restoreSnapshot(snapshot: AutosaveManager.SessionSnapshot) {
        // Session boundary: a selection carried over from the previous content would be an
        // orphaned id — no highlight. Clearing lets the carousel's seed effect reselect.
        _selectedComponentId.value = null
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
        _undercutRecord.value = snapshot.undercutRecord
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

    /**
     * Set the bare-shaft Ø used by ALL auto-body spans (mm). The shaft between explicit
     * components is one piece of stock, so a single value covers every auto span; editing
     * any auto-body card updates them all. Values ≤ 0 clear back to derived behavior.
     * Positioning of auto spans is unaffected.
     */
    fun setAutoBodyDiaMm(valueMm: Float) {
        _spec.update { it.copy(autoBodyDiaMm = max(0f, valueMm)) }
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

    /**
     * Set the drawing note that the shaft's keyways are clocked 180° apart. Enabling clears the
     * 90° note — a shaft carries at most one clocking note. Unchanged input is a no-op.
     */
    fun setKeyways180Apart(enabled: Boolean) = _spec.update { s -> s.withKeyways180Apart(enabled) }

    /**
     * Set the drawing note that the shaft's keyways are clocked 90° apart. Enabling clears the
     * 180° note — a shaft carries at most one clocking note. Unchanged input is a no-op.
     */
    fun setKeyways90Apart(enabled: Boolean) = _spec.update { s -> s.withKeyways90Apart(enabled) }

    /**
     * Set the 90° clocking direction — true = clockwise viewed from aft. Meaningful only while
     * [setKeyways90Apart] is on; the choice survives toggling the note off and back on.
     */
    fun setKeyways90Cw(cw: Boolean) = _spec.update { s -> s.withKeyways90Cw(cw) }

    /**
     * Remove a [Body] by its stable [id].
     *
     * The removed body (spec + order) is recoverable via [undoEdit] — the central session
     * history records the post-delete state, so undo restores both the spec and the row order.
     */
    fun removeBody(id: String) {
        Log.d("ShaftViewModel", "removeBody invoked for id=$id")
        var removed = false

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
            removed = true
            s.copy(
                bodies = s.bodies.toMutableList().apply { removeAt(idx) }
            )
        }

        if (removed) {
            // Remove from UI order AFTER spec update to avoid cross-state mutation
            orderRemove(id)
            ensureOverall()
            emitDeletedSnack(ComponentKind.BODY)
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
                        taperRateText = effectiveRate,
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

    /** Remove a [Taper] by id. Recoverable via [undoEdit] (spec + order restored together). */
    fun removeTaper(id: String) {
        Log.d("ShaftViewModel", "removeTaper invoked for id=$id")
        var removed = false

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
            removed = true

            val taper = s.tapers[idx]
            val afterRemoval = s.copy(tapers = s.tapers.toMutableList().apply { removeAt(idx) })
            val merge = afterRemoval.mergeBodiesAround(taper.startFromAftMm, taper.startFromAftMm + taper.lengthMm) { newId() }
            merge.removedIds.forEach { orderRemove(it) }
            merge.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }
            merge.spec
        }

        if (removed) {
            orderRemove(id)
            ensureOverall()
            emitDeletedSnack(ComponentKind.TAPER)
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

    /** Remove a [Threads] segment by id. Recoverable via [undoEdit] (spec + order together). */
    fun removeThread(id: String) {
        Log.d("ShaftViewModel", "removeThread invoked for id=$id")
        var removed = false

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
            removed = true

            val thread = s.threads[idx]
            val afterRemoval = s.copy(threads = s.threads.toMutableList().apply { removeAt(idx) })
            // Only merge bodies around in-shaft threads; excluded threads live outside the envelope.
            val merge = if (!thread.excludeFromOAL)
                afterRemoval.mergeBodiesAround(thread.startFromAftMm, thread.startFromAftMm + thread.lengthMm) { newId() }
            else BodySplitResult(afterRemoval, emptyList(), emptyList())
            merge.removedIds.forEach { orderRemove(it) }
            merge.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }
            merge.spec
        }

        if (removed) {
            // Update cross-type order, maintain coverage, and show the undo snackbar.
            orderRemove(id)
            ensureOverall()
            emitDeletedSnack(ComponentKind.THREAD)
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

    /** Remove a [Liner] by id. Recoverable via [undoEdit] (spec + order restored together). */
    fun removeLiner(id: String) {
        Log.d("ShaftViewModel", "removeLiner invoked for id=$id")
        var removed = false

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
            removed = true

            val liner = s.liners[idx]
            val afterRemoval = s.copy(liners = s.liners.toMutableList().apply { removeAt(idx) })
            val merge = afterRemoval.mergeBodiesAround(liner.startFromAftMm, liner.startFromAftMm + liner.lengthMm) { newId() }
            merge.removedIds.forEach { orderRemove(it) }
            merge.addedIds.forEach   { orderAdd(ComponentKind.BODY, it) }
            merge.spec
        }

        if (removed) {
            orderRemove(id)
            ensureOverall()
            emitDeletedSnack(ComponentKind.LINER)
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

    /** Remove a [CouplerBoltSlot] by id. Recoverable via [undoEdit] (spec + order together). */
    fun removeCouplerBoltSlot(id: String) {
        var removed = false

        _spec.update { s ->
            val idx = s.couplerBoltSlots.indexOfFirst { it.id == id }
            if (idx < 0) return@update s
            removed = true
            // No body merge needed — slots never split bodies.
            s.copy(couplerBoltSlots = s.couplerBoltSlots.toMutableList().apply { removeAt(idx) })
        }

        if (removed) {
            orderRemove(id)
            // Slots never affect OAL, so no ensureOverall() here.
            emitDeletedSnack(ComponentKind.COUPLER_BOLT_SLOT)
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
            undercutRecord = _undercutRecord.value,
        )
    )

    /**
     * Import a JSON string and replace current state.
     * Tries envelope first, then falls back to legacy (spec-only) files.
     * Seeds/repairs UI order to reflect loaded spec.
     */
    fun importJson(raw: String) {
        val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrElse { throw it }

        clearEditHistory()
        // Each open is a fresh draft identity so this document's autosave upserts its own entry
        // and cannot touch another document's draft. markDocumentSaved() below reseats the
        // dirty-gate baseline to the just-loaded state (clean → no draft until edited).
        currentDraftId = UUID.randomUUID().toString()
        draftPersisted = false
        // Session boundary: drop the previous document's selection (stale id = orphaned
        // highlight); the carousel's seed effect reselects the last row of this document.
        _selectedComponentId.value = null
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
        _undercutRecord.value = decoded.undercutRecord

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
     * - Clears undo/redo history and resets cross-type component order.
     */
    fun newDocument() {
        _editorResetNonce.update { it + 1 }
        clearEditHistory()
        // Fresh draft identity for the new blank session; markDocumentSaved() below reseats the
        // dirty-gate baseline to blank (clean → no draft until edited).
        currentDraftId = UUID.randomUUID().toString()
        draftPersisted = false

        resetSessionAddDefaults()

        // Session boundary: no selection carries into a blank document (stale id would be
        // an orphaned highlight).
        _selectedComponentId.value = null

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
        _undercutRecord.value = UndercutRecord()
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

    /**
     * Snap a raw mm position against the current spec, using anchors built from the
     * latest [ShaftSpec] and a unit-aware tolerance. This is the main entry point for
     * tap-to-add and future cursor-based snapping.
     */
    fun snapRawPositionMm(rawMm: Float): Float =
        snapRawPositionMm(rawMm, _spec.value, _unit.value)


}
