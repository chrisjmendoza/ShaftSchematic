package com.android.shaftschematic.ui.viewmodel
import com.android.shaftschematic.settings.AppThemeMode
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.pdf.PdfExportMode

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.BuildConfig
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.data.SettingsStore.UnitPref
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.io.ShaftBackup
import com.android.shaftschematic.io.TemplateStorage
import java.io.File
import com.android.shaftschematic.model.*
import com.android.shaftschematic.ui.input.TaperSide
import com.android.shaftschematic.ui.input.classifyTaperSideByMidpoint
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.FractionStyle
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.UndercutStyle
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.parseTaperRateText
import com.android.shaftschematic.util.parseToMm
import com.android.shaftschematic.util.VerboseLog
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
    // docs/archive/Autosave_Incident_2026-07-25.md.
    private val _drafts = MutableStateFlow<List<AutosaveManager.DraftEntry>>(emptyList())
    val drafts: StateFlow<List<AutosaveManager.DraftEntry>> = _drafts.asStateFlow()

    // Per-editing-session draft identity. Minted on construction, re-minted on newDocument()
    // and importJson() so working on one document can never touch another's draft entry.
    internal var currentDraftId: String = UUID.randomUUID().toString()

    // Full snapshot of the last saved-to-file / freshly-loaded state — the dirty gate's
    // baseline. The autosave observer writes a draft only when the live snapshot differs.
    // A StateFlow (not a plain var) so [hasUnsavedChanges] re-evaluates the moment a save
    // reseats the baseline, not only on the next edit.
    private val _savedSnapshot = MutableStateFlow<AutosaveManager.SessionSnapshot?>(null)

    // Whether currentDraftId currently has a persisted entry in the ring. Used to remove the
    // entry exactly once on a dirty→clean transition (avoids hammering DataStore).
    internal var draftPersisted: Boolean = false

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
            runoutStationPlacements = _runoutStationPlacements.value,
            unitOverrides = _unitOverrides.value,
            dualUnits = _dualUnits.value,
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
    internal val _currentDocumentName = MutableStateFlow<String?>(null)
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
     * unsaved work slip past the dirty gate. See docs/archive/Autosave_Incident_2026-07-25.md.
     */
    fun hasUnsavedWork(): Boolean {
        if (isSessionDefault()) return false
        return shouldWriteDraft(buildCurrentSnapshot(), _savedSnapshot.value)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Reactive state (observed by Compose)
    // ────────────────────────────────────────────────────────────────────────────

    internal val _spec = MutableStateFlow(ShaftSpec())
    val spec: StateFlow<ShaftSpec> = _spec.asStateFlow()

    internal val _unit = MutableStateFlow(UnitSystem.MILLIMETERS)
    val unit: StateFlow<UnitSystem> = _unit.asStateFlow()

    internal val _unitLocked = MutableStateFlow(false)
    val unitLocked: StateFlow<Boolean> = _unitLocked.asStateFlow()

    // Mixed units + dual display (document state, non-undoable — mirrors _runoutConfig's posture,
    // so dirtiness is derived from buildCurrentSnapshot). Display axis only; geometry stays mm.
    internal val _unitOverrides = MutableStateFlow<Map<String, UnitSystem>>(emptyMap())
    val unitOverrides: StateFlow<Map<String, UnitSystem>> = _unitOverrides.asStateFlow()
    internal val _dualUnits = MutableStateFlow(false)
    val dualUnits: StateFlow<Boolean> = _dualUnits.asStateFlow()

    /** The resolver every composer/route reads to format unit-aware strings. */
    fun currentDisplayUnits(): DisplayUnits =
        DisplayUnits(_unit.value, _unitOverrides.value, _dualUnits.value)

    /** Sets (or, with null, clears) a component's display-unit override. */
    fun setComponentUnit(componentId: String, unit: UnitSystem?) {
        if (componentId.isBlank()) return
        _unitOverrides.update { m -> if (unit == null) m - componentId else m + (componentId to unit) }
    }

    /**
     * Toggles sheet-wide inline dual-unit display. Persists to the global default so the choice
     * survives to new documents and stays in step with the Settings switch (the observer in
     * [init] mirrors it back to the session).
     */
    fun setDualUnits(on: Boolean, persist: Boolean = true) {
        _dualUnits.value = on
        if (persist) {
            viewModelScope.launch { SettingsStore.setDualUnitsDefault(getApplication(), on) }
        }
    }

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

    // Body S-break threshold: how far a body run may compress before it shows the S-break
    // pair. 0 = never on compression alone. Also a preview re-render key on every tab that
    // rasterizes with the current PdfPrefs.
    internal val _pdfSBreakThresholdFrac = MutableStateFlow(PdfPrefs().sBreakThresholdFrac)
    val pdfSBreakThresholdFrac: StateFlow<Float> = _pdfSBreakThresholdFrac.asStateFlow()

    // Default worn-profile trace exaggeration — what a document that never touched its own
    // "Trace depth exaggeration" slider draws with. Also a preview re-render key on the wear
    // document, whose trace depth follows it.
    internal val _pdfWearTraceDepthFrac = MutableStateFlow(PdfPrefs().wearTraceDepthFrac)
    val pdfWearTraceDepthFrac: StateFlow<Float> = _pdfWearTraceDepthFrac.asStateFlow()

    // Grey of a wear area's fill in the wear document's detail strips. Also a preview
    // re-render key on the wear document: the composer reads it through the PdfPrefs
    // snapshot, which is not snapshot state.
    internal val _pdfWearBandShadeFrac = MutableStateFlow(PdfPrefs().wearBandShadeFrac)
    val pdfWearBandShadeFrac: StateFlow<Float> = _pdfWearBandShadeFrac.asStateFlow()

    // How much bare shaft may sit between two components in one wear detail strip before the run
    // compresses to an S-break (canonical mm). Also a preview re-render key on the wear document:
    // the composer reads it through the PdfPrefs snapshot, which is not snapshot state.
    internal val _pdfWearJoinGapMaxMm = MutableStateFlow(PdfPrefs().wearJoinGapMaxMm)
    val pdfWearJoinGapMaxMm: StateFlow<Float> = _pdfWearJoinGapMaxMm.asStateFlow()

    // Dimension-rail arrowhead size (pt). Also a preview re-render key on every tab that
    // rasterizes with the current PdfPrefs.
    internal val _pdfArrowSizePt = MutableStateFlow(PdfPrefs().arrowSizePt)
    val pdfArrowSizePt: StateFlow<Float> = _pdfArrowSizePt.asStateFlow()

    // How a fraction is SET on every drawn surface. Also a preview re-render key on every tab
    // that rasterizes — the style itself reaches the draw sites via `FractionTypography.active`,
    // which is not snapshot state.
    internal val _pdfFractionStyle = MutableStateFlow(PdfPrefs().fractionStyle)
    val pdfFractionStyle: StateFlow<FractionStyle> = _pdfFractionStyle.asStateFlow()

    // How a dual value is SET (inline one-liner vs two-line stack). Unlike the fraction style this
    // one moves LAYOUT, so the composers take it as a parameter; the StateFlow is what lets each
    // preview name it as a re-render key.
    internal val _pdfDualUnitLayout = MutableStateFlow(PdfPrefs().dualUnitLayout)
    val pdfDualUnitLayout: StateFlow<DualUnitLayout> = _pdfDualUnitLayout.asStateFlow()

    internal val _pdfExportMode = MutableStateFlow(PdfExportMode.Standard)
    val pdfExportMode: StateFlow<PdfExportMode> = _pdfExportMode.asStateFlow()

    // Blank-draft (write-in) export/print. Deliberately NOT persisted: a forgotten sticky
    // toggle would silently blank every future export. Defaults off each session.
    internal val _pdfBlankDraft = MutableStateFlow(false)
    val pdfBlankDraft: StateFlow<Boolean> = _pdfBlankDraft.asStateFlow()

    // Whether a blank draft prints the below-shaft Ø callout leaders (ready to fill in) or
    // leaves the shaft clear to annotate freehand. Only consulted in blank mode. Session-only
    // for the same reason as _pdfBlankDraft, and so the two always reset together.
    internal val _pdfBlankDiaCallouts = MutableStateFlow(true)
    val pdfBlankDiaCallouts: StateFlow<Boolean> = _pdfBlankDiaCallouts.asStateFlow()

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

    internal val _resolvedComponents = MutableStateFlow<List<ResolvedComponent>>(emptyList())
    val resolvedComponents: StateFlow<List<ResolvedComponent>> = _resolvedComponents.asStateFlow()

    internal val _selectedComponentId = MutableStateFlow<String?>(null)
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

    internal val _customer = MutableStateFlow("")
    val customer: StateFlow<String> = _customer.asStateFlow()

    internal val _vessel = MutableStateFlow("")
    val vessel: StateFlow<String> = _vessel.asStateFlow()

    internal val _jobNumber = MutableStateFlow("")
    val jobNumber: StateFlow<String> = _jobNumber.asStateFlow()

    internal val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    internal val _shaftPosition = MutableStateFlow(ShaftPosition.OTHER)
    val shaftPosition: StateFlow<ShaftPosition> = _shaftPosition.asStateFlow()

    internal val _overallIsManual = MutableStateFlow(false)
    val overallIsManual: StateFlow<Boolean> = _overallIsManual.asStateFlow()
    fun setOverallIsManual(v: Boolean) { _overallIsManual.value = v }

    // Session-scoped "last used" add defaults (mm). Reset on new/open/import.
    private val _sessionAddDefaults = MutableStateFlow(SessionAddDefaults.initial())
    val sessionAddDefaults: StateFlow<SessionAddDefaults> = _sessionAddDefaults.asStateFlow()

    // ── Runout sheet configuration ────────────────────────────────────────────
    // Persisted alongside the spec in the .shaft file so bubble count overrides
    // and TIR direction travel with the job.

    internal val _runoutConfig = MutableStateFlow(RunoutConfig())
    val runoutConfig: StateFlow<RunoutConfig> = _runoutConfig.asStateFlow()

    // ── Runout per-station readings (bubble value + high-spot marker) ──────────
    // Reference-only data, same posture as _wearRecord below: plain state updates, no
    // geometry side effects. Keyed by (componentId, stationIndex). Both fields optional;
    // an entry with neither value nor marker is dropped by RunoutReadings.withReading.
    // See docs/archive/RunoutBubbleEditor_PLAN.md and model/RunoutReading.kt.

    internal val _runoutReadings = MutableStateFlow(RunoutReadings())
    val runoutReadings: StateFlow<RunoutReadings> = _runoutReadings.asStateFlow()

    // ── Runout station placement (dragged bubble positions) ───────────────────
    // Reference-only, same posture as _runoutReadings above. A drag pins exactly the station
    // it moved; untouched siblings stay derived, keeping their automatic behaviour (geometry
    // tracking, the sheet's drawn-even body placement). Count edits on a pinned component
    // freeze the whole current set so an insert/remove renumbers nothing under the user.
    // See model/RunoutStationPlacement.kt and geom/RunoutStationPlacementMath.kt.

    internal val _runoutStationPlacements = MutableStateFlow(RunoutStationPlacements())
    val runoutStationPlacements: StateFlow<RunoutStationPlacements> =
        _runoutStationPlacements.asStateFlow()

    // ── Liner wear inspection record ──────────────────────────────────────────
    // Persisted alongside the spec in the .shaft file, same as runoutConfig above.
    // Reference-only data: plain state updates, no geometry side effects, no
    // ensureOverall/auto-body interaction. See docs/archive/LinerWearAreas_Proposal.md §5, §7.

    internal val _wearRecord = MutableStateFlow(WearRecord())
    val wearRecord: StateFlow<WearRecord> = _wearRecord.asStateFlow()

    // ── Undercut drawing record ────────────────────────────────────────────────
    // Reference-only data, same posture as _wearRecord/_runoutReadings above: plain state
    // updates, no geometry side effects, no ensureOverall/auto-body interaction. Undercuts
    // have no component key, so there is no orphan concept here. See
    // docs/archive/UndercutDrawing_PLAN.md §2, §6.

    internal val _undercutRecord = MutableStateFlow(UndercutRecord())
    val undercutRecord: StateFlow<UndercutRecord> = _undercutRecord.asStateFlow()

    // Incrementing key used by the editor UI to reset Compose-local state (dialogs, focus, scroll, etc.)
    // without relocating that state into the ViewModel.
    internal val _editorResetNonce = MutableStateFlow(0)
    val editorResetNonce: StateFlow<Int> = _editorResetNonce.asStateFlow()

    // The carousel renders resolved components in PHYSICAL order (auto-bodies interleaved at
    // their spans), so the ViewModel keeps no cross-type display order of its own. See
    // `docs/contracts/ComponentsOrdering.md`.

    // One-shot UI events (snackbars, etc.)
    internal val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // ────────────────────────────────────────────────────────────────────────────
    // Session-scoped Undo / Redo (v2) — covers ALL drawing-state edits, not just deletes.
    //
    // A single [SessionHistory] over [EditState] snapshots (spec + wear + runout + undercuts +
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
        runoutStationPlacements = _runoutStationPlacements.value,
        stationCountOverrides = _runoutConfig.value.componentOverrides,
        undercutRecord = _undercutRecord.value,
        overallIsManual = _overallIsManual.value,
    )

    private fun updateHistoryFlags() {
        _canUndo.value = editHistory.canUndo
        _canRedo.value = editHistory.canRedo
    }

    /** Drop all undo/redo history. Called at every document/session boundary. */
    internal fun clearEditHistory() {
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
            _runoutStationPlacements.value = e.runoutStationPlacements
            // Only the override slice restores — the config's sliders/toggles are not
            // undoable state and keep whatever the user last set them to.
            _runoutConfig.update { it.copy(componentOverrides = e.stationCountOverrides) }
            _undercutRecord.value = e.undercutRecord
            _overallIsManual.value = e.overallIsManual
        } finally {
            isRestoringHistory = false
        }
    }

    /** Undo the most recent edit step (spec / wear / runout / undercuts / OAL mode). */
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
            runoutConfig, unitLocked, overallIsManual, wearRecord, runoutReadings, undercutRecord,
            runoutStationPlacements
        ) { values: Array<Any?> ->
            check(values.size == 14) { "Autosave combine expected 14 values, got ${values.size}" }

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
            val stationPlacements = values[13] as RunoutStationPlacements

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
                runoutStationPlacements = stationPlacements,
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
        @Suppress("UNCHECKED_CAST")
        // Flow.combine overload for >5 flows returns Array<Any?>. runoutConfig is observed
        // but only its override slice reaches the EditState: a slider commit re-emits here,
        // produces an identical snapshot, and SessionHistory.record no-ops it.
        viewModelScope.launch {
            combine(
                spec, wearRecord, runoutReadings, undercutRecord, overallIsManual,
                runoutStationPlacements, runoutConfig
            ) { values: Array<Any?> ->
                check(values.size == 7) { "Undo combine expected 7 values, got ${values.size}" }
                EditState(
                    spec = values[0] as ShaftSpec,
                    wearRecord = values[1] as WearRecord,
                    runoutReadings = values[2] as RunoutReadings,
                    runoutStationPlacements = values[5] as RunoutStationPlacements,
                    stationCountOverrides = (values[6] as RunoutConfig).componentOverrides,
                    undercutRecord = values[3] as UndercutRecord,
                    overallIsManual = values[4] as Boolean,
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

            // One-shot seeding: bundled starter templates into the template store, so the
            // browser has something in it the first time it opens.
            runCatching {
                TemplateStorage.seedStarterTemplatesIfNeeded(app, SettingsStore)
            }.onSuccess { report ->
                VerboseLog.d(VerboseLog.Category.IO, "TemplateStorage") {
                    "starter template seeding finished: saved=${report.savedCount} failed=${report.failedCount}"
                }
            }.onFailure {
                VerboseLog.d(VerboseLog.Category.IO, "TemplateStorage") {
                    "starter template seeding failed: ${it.javaClass.simpleName}: ${it.message}"
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
            // Global default drives the live session; a later document open (importJson) overrides
            // with the document's own stored value. persist = false avoids writing back the mirror.
            SettingsStore.dualUnitsDefaultFlow(getApplication()).collectLatest { persisted ->
                setDualUnits(persisted, persist = false)
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
            SettingsStore.pdfSBreakThresholdFracFlow(getApplication()).collectLatest { persisted ->
                _pdfSBreakThresholdFrac.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(sBreakThresholdFrac = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfArrowSizePtFlow(getApplication()).collectLatest { persisted ->
                _pdfArrowSizePt.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(arrowSizePt = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfWearTraceDepthFracFlow(getApplication()).collectLatest { persisted ->
                _pdfWearTraceDepthFrac.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(wearTraceDepthFrac = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfWearBandShadeFracFlow(getApplication()).collectLatest { persisted ->
                _pdfWearBandShadeFrac.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(wearBandShadeFrac = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfWearJoinGapMaxMmFlow(getApplication()).collectLatest { persisted ->
                _pdfWearJoinGapMaxMm.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(wearJoinGapMaxMm = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfFractionStyleFlow(getApplication()).collectLatest { persisted ->
                _pdfFractionStyle.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(fractionStyle = persisted) }
            }
        }
        viewModelScope.launch {
            SettingsStore.pdfDualUnitLayoutFlow(getApplication()).collectLatest { persisted ->
                _pdfDualUnitLayout.value = persisted
                SettingsStore.updatePdfPrefs { it.copy(dualUnitLayout = persisted) }
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
        _runoutStationPlacements.value = snapshot.runoutStationPlacements
        _undercutRecord.value = snapshot.undercutRecord
        _unitOverrides.value = snapshot.unitOverrides
        _dualUnits.value = snapshot.dualUnits
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
        val end = s.coverageEndMm()
        val minOverall = end + max(0f, minFreeMm)
        if (s.overallLengthMm < minOverall) s.withNewOal(minOverall) else s
    }

    /**
     * Set the bare-shaft Ø for the ONE auto-body span `[spanStartMm, spanEndMm)` (mm) — the
     * span whose card was edited. Values ≤ 0 clear the section back to the legacy shaft-wide
     * Ø and then to neighbor derivation; overrides anchored elsewhere are untouched, and
     * auto-span positioning is unaffected. See [withAutoSectionDia].
     */
    fun setAutoSectionDiaMm(spanStartMm: Float, spanEndMm: Float, valueMm: Float) {
        _spec.update { it.withAutoSectionDia(spanStartMm, spanEndMm, valueMm) }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Explicit snap helpers — never called automatically; only on user request
    // ────────────────────────────────────────────────────────────────────────────

    internal fun newId(): String = UUID.randomUUID().toString()

    internal fun resetSessionAddDefaults() {
        _sessionAddDefaults.value = SessionAddDefaults.initial()
    }

    internal fun seedSessionAddDefaultsFromSpec(spec: ShaftSpec) {
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

    internal fun rememberBodyDefaults(lengthMm: Float, diaMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                bodyLenMm = if (lengthMm > 0f) lengthMm else cur.bodyLenMm,
                bodyDiaMm = if (diaMm > 0f) diaMm else cur.bodyDiaMm
            )
        }
    }

    internal fun rememberLinerDefaults(lengthMm: Float, odMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                linerLenMm = if (lengthMm > 0f) lengthMm else cur.linerLenMm,
                linerOdMm = if (odMm > 0f) odMm else cur.linerOdMm
            )
        }
    }

    internal fun rememberTaperDefaults(lengthMm: Float, setDiaMm: Float, letDiaMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                taperLenMm = if (lengthMm > 0f) lengthMm else cur.taperLenMm,
                taperSetDiaMm = if (setDiaMm > 0f) setDiaMm else cur.taperSetDiaMm,
                taperLetDiaMm = if (letDiaMm > 0f) letDiaMm else cur.taperLetDiaMm
            )
        }
    }

    internal fun rememberThreadDefaults(lengthMm: Float, majorDiaMm: Float, pitchMm: Float) {
        _sessionAddDefaults.update { cur ->
            cur.copy(
                threadLenMm = if (lengthMm > 0f) lengthMm else cur.threadLenMm,
                threadMajorDiaMm = if (majorDiaMm > 0f) majorDiaMm else cur.threadMajorDiaMm,
                threadPitchMm = if (pitchMm > 0f) pitchMm else cur.threadPitchMm
            )
        }
    }

    internal fun rememberSlotDefaults(holeDiaMm: Float, spacingMm: Float, depthMm: Float, count: Int) {
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
    // Private helpers
    // ────────────────────────────────────────────────────────────────────────────

    /** Emits a deletion snackbar request for the given [ComponentKind]. */
    internal fun emitDeletedSnack(kind: ComponentKind) {
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
         * in the AFT half. Shares the UI's SET/LET labeling rule
         * ([com.android.shaftschematic.ui.input.classifyTaperSideByMidpoint]) rather than
         * restating it: mid-point ≤ OAL/2 → AFT taper (SET at start), AFT when OAL is unknown (0).
         * A second copy of the rule would let derivation and labels drift apart.
         */
        fun taperSmallEndAtStart(startMm: Float, lengthMm: Float, overallLengthMm: Float): Boolean =
            classifyTaperSideByMidpoint(startMm, lengthMm, overallLengthMm) == TaperSide.AFT

        /**
         * Parse a taper rate string into a dimensionless ratio (diameter change per length unit).
         * Supports: "1:12" → 1/12, "3/4" → 0.75, "0.0833" → 0.0833, "1" → 1/12 (legacy bare int).
         */
        fun parseRateText(text: String): Float? = parseTaperRateText(text, allowAmbiguousBareOne = true)
    }

}
