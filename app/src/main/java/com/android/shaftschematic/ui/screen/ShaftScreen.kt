package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.shaftschematic.geom.computeOalWindow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.android.shaftschematic.model.BlendProfile
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.dialog.InlineAddChooserDialog
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.viewmodel.SessionAddDefaults
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.toMmOrNull
import kotlinx.coroutines.launch

/**
 * ShaftScreen — Editor surface
 *
 * Responsibilities
 * • Header row (unit selector + grid toggle; unit selector disables when locked)
 * • Preview drawing (white square; optional grid; fixed-height band)
 * • Free-to-End badge overlay (top-start of preview; red on oversize)
 * • Overall length input (ghost “0”; commits on blur/Done; auto when not manual)
 * • Project fields (commit-on-blur / IME Done)
 * • Component carousel (edit & remove) — rows in physical order along the shaft
 * • Add-component FAB floating above IME & nav bar
 *
 * Contract / Invariants
 * • Canonical model units are millimeters (mm) — convert only at UI edge.
 * • Carousel rows follow the resolved components' physical order (auto-bodies interleaved at
 *   their spans); there is no separate cross-type order. See `docs/contracts/ComponentsOrdering.md`.
 * • IME safety: imePadding shrinks the scroll viewport (applied before verticalScroll) so
 *   Compose auto-scrolls to keep the focused field in view; FAB uses ime ∪ navigationBars insets.
 * • No file I/O or routing here.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShaftScreen(
    resetNonce: Int,

    // State
    spec: ShaftSpec,
    /** Saved file name (with extension) of the current document, or null for an unsaved draft. */
    documentName: String? = null,
    /** True while the session differs from the last saved/loaded state (title-bar asterisk). */
    hasUnsavedChanges: Boolean = false,
    resolvedComponents: List<ResolvedComponent> = emptyList(),
    unit: UnitSystem,
    overallIsManual: Boolean,
    customer: String,
    vessel: String,
    jobNumber: String,
    item: String,
    shaftPosition: ShaftPosition,
    notes: String,
    showGrid: Boolean,
    showOalDebugLabel: Boolean,
    showOalHelperLine: Boolean,
    showOalInPreviewBox: Boolean,
    showComponentDebugLabels: Boolean,
    showRenderLayoutDebugOverlay: Boolean,
    showRenderOalMarkers: Boolean,
    showDimDebugOverlay: Boolean = false,
    pdfTieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    /** The Settings "Show component titles" switch — the default a card's unset name toggle follows. */
    componentTitlesDefault: Boolean = true,
    /** The kind-level shade checkboxes — the default a card's unset shade toggle follows. */
    componentShadeDefaults: ComponentShadeDefaults = ComponentShadeDefaults(),
    showComponentArrows: Boolean,
    componentArrowWidthDp: Int,
    showHighlightSelection: Boolean = true,
    selectedComponentId: String?,

    previewOutline: PreviewColorSetting,
    previewBodyFill: PreviewColorSetting,
    previewTaperFill: PreviewColorSetting,
    previewLinerFill: PreviewColorSetting,
    previewThreadFill: PreviewColorSetting,
    previewThreadHatch: PreviewColorSetting,
    previewBlackWhiteOnly: Boolean,
    lineThicknessScale: Float = 1.0f,

    sessionAddDefaults: SessionAddDefaults,

    // Mixed per-component units (Settings → Drawing → "Per-component units"). Defaulted
    // off/empty/no-op so a caller that doesn't wire them draws every carousel card
    // identically to before the capability existed.
    perComponentUnitsEnabled: Boolean = false,
    unitOverrides: Map<String, UnitSystem> = emptyMap(),
    onSetComponentUnit: (String, UnitSystem?) -> Unit = { _, _ -> },
    /** Sets (null clears) the unit a component's KEYWAY is authored and printed in. */
    onSetKeywayUnit: (String, UnitSystem?) -> Unit = { _, _ -> },

    // Setters
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetItem: (String) -> Unit,
    onSetShaftPosition: (ShaftPosition) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,
    onSetOverallLengthMm: (Float) -> Unit,
    onSetOverallIsManual: (Boolean) -> Unit,
    onSelectComponentById: (String?) -> Unit,

    // Adds (all mm)
    onAddBody: (startMm: Float, lengthMm: Float, diaMm: Float,
                keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                keywayOffsetFromEndMm: Float, keywayEnd: LinerAuthoredReference,
                keywaySpooned: Boolean, keywayUnit: UnitSystem?,
                blendAftMm: Float, blendFwdMm: Float, blendProfile: BlendProfile,
                blendAftSeal: Boolean, blendFwdSeal: Boolean) -> Unit,
    onSetAutoSectionDia: (spanStartMm: Float, spanEndMm: Float, diaMm: Float) -> Unit,
    onSetAutoBlend: (spanStartMm: Float, spanEndMm: Float, end: LinerAuthoredReference, lengthMm: Float, profile: BlendProfile, seal: Boolean) -> Unit,
    onSetShowAutoBodyDia: (Boolean) -> Unit,
    onAddTaper: (startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float,
                 rateText: String, reference: LinerAuthoredReference,
                 keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                 keywayOffsetFromSetMm: Float, keywaySpooned: Boolean,
                 keywayUnit: UnitSystem?) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOAL: Boolean,
                  isAftEnd: Boolean, metricDesignation: String?) -> Unit,
    onAddLiner: (Float, Float, Float, LinerAuthoredReference, LinerShoulderDraft) -> Unit,
    onAddCouplerBoltSlot: (startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float, reference: SlotAuthoredReference) -> Unit,

    // Updates (all mm)
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateBodyShowDia: (Int, Boolean) -> Unit,
    onUpdateBodyShowLabel: (Int, Boolean) -> Unit,
    onUpdateBodyShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyCompressOnDrawing: (Int, Boolean) -> Unit,
    onUpdateBodyBlend: (index: Int, blendAftMm: Float, blendFwdMm: Float, profile: BlendProfile, sealAft: Boolean, sealFwd: Boolean) -> Unit,
    onUpdateBodyLabel: (Int, String?) -> Unit,
    onUpdateBodyKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromEndMm: Float, end: LinerAuthoredReference, spooned: Boolean) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperLabel: (Int, String?) -> Unit,
    onUpdateTaperShowLabel: (Int, Boolean) -> Unit,
    onUpdateTaperShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateTaperReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float, String?) -> Unit,
    onUpdateThreadLabel: (Int, String?) -> Unit,
    onUpdateThreadShowLabel: (Int, Boolean) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerShowDia: (Int, Boolean) -> Unit,
    onUpdateLinerShowLabel: (Int, Boolean) -> Unit,
    onUpdateLinerShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateLinerShoulder: (Int, LinerAuthoredReference, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    linerShouldersEnabled: Boolean = false,
    /** Settings → Drawing → "Unit converter in Add dialogs": gates the calculator icon on the five Add dialogs. */
    dialogUnitConverterEnabled: Boolean = false,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlot: (index: Int, startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float) -> Unit,
    onUpdateCouplerBoltSlotReference: (Int, SlotAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlotShowRail: (Int, Boolean) -> Unit,

    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,

    // Removes by stable id
    onRemoveBody: (String) -> Unit,
    onRemoveTaper: (String) -> Unit,
    onRemoveThread: (String) -> Unit,
    onRemoveLiner: (String) -> Unit,
    onRemoveCouplerBoltSlot: (String) -> Unit,

    // Other
    snackbarHostState: SnackbarHostState,

    // Navigation / actions (routing is owned by the Route layer)
    /**
     * Opens the sidebar navigation drawer. The Home button has been moved into the sidebar,
     * so the toolbar's left icon is now a hamburger/menu button that calls this.
     */
    onOpenSidebar: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit = {},
    /** Open "Duplicate for mate" — writes a sibling document; the session is untouched. */
    onDuplicateForMate: () -> Unit = {},
    /** Close the current document (guarded for unsaved work) and return to Start. */
    onCloseDocument: () -> Unit = {},
    onExportPdf: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,

    // Feature flags
    devOptionsEnabled: Boolean,

    // History (Undo/Redo)
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,

) {
    key(resetNonce) {
    var addThreadOpen by rememberSaveable { mutableStateOf(false) }
    var addThreadStartMm by rememberSaveable { mutableFloatStateOf(0f) }

    var addSlotOpen by rememberSaveable { mutableStateOf(false) }
    var addSlotStartMm by rememberSaveable { mutableFloatStateOf(0f) }

    // Add-chooser handoff: the chosen start/length ride here while the add dialog is open.
    var addBodyOpen by rememberSaveable { mutableStateOf(false) }
    var addLinerOpen by rememberSaveable { mutableStateOf(false) }
    var addTaperOpen by rememberSaveable { mutableStateOf(false) }
    var addStartMm by rememberSaveable { mutableFloatStateOf(0f) }
    var addLengthMm by rememberSaveable { mutableFloatStateOf(50f) }

    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    var projectInfoOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val topBarScope = rememberCoroutineScope()

    val collidingComponentIds = remember(spec) { spec.collidingIds() }
    val exportPdfGate = remember(spec, collidingComponentIds) {
        exportPdfGate(spec, collidingComponentIds)
    }
    val exportPdfEnabled = exportPdfGate.enabled
    val exportPdfDisabledMessage = exportPdfGate.disabledMessage

    // NOTE: typed field commits go to the update callbacks UNSNAPPED. Snapping the recomputed
    // start/end to component-edge anchors (±1 mm) would silently rewrite values the user just
    // typed — e.g. shortening a FWD-referenced taper by less than the tolerance moves its start
    // by under 1 mm, and a snap would pull it straight back to the old boundary, undoing the
    // edit. Snapping belongs to coarse gestures (tap-to-add) only; typed values are exact.
    // See the golden rule (user inputs are sacred) in CLAUDE.md.

    // Auto-sync overall when not manual
    LaunchedEffect(overallIsManual, spec.bodies, spec.tapers, spec.threads, spec.liners) {
        if (!overallIsManual) {
            val end = lastOccupiedEndMm(spec)
            if (end != spec.overallLengthMm) onSetOverallLengthMm(end)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Horizontal
        ),
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            // Document title strip — desktop-editor style: the current file name (or
            // "Untitled draft") with a trailing asterisk while there are unsaved changes.
            // Sits above the action bar, so it also makes the saved-vs-draft state visible.
            // Shared with the Runout/Wear/Undercut/Output tabs; this site owns the
            // status-bar inset (the TopAppBar below zeroes its own).
            EditorDocumentTitle(
                documentName = documentName,
                hasUnsavedChanges = hasUnsavedChanges,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            TopAppBar(
                // Status-bar inset is consumed by the title strip above; the default TopAppBar
                // insets would pad for it a second time.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = "Shaft Editor",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    // Hamburger opens the sidebar nav drawer.
                    // Home lives inside the sidebar — not duplicated here.
                    IconButton(
                        onClick = onOpenSidebar,
                        modifier = Modifier.testTag("toolbar_menu")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                    }
                },
                actions = {
                    HistoryMenu(
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = onUndo,
                        onRedo = onRedo
                    )

                    IconButton(
                        onClick = { projectInfoOpen = true },
                        modifier = Modifier.testTag("toolbar_project_info")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Project information")
                    }

                    IconButton(
                        onClick = onNew,
                        modifier = Modifier.testTag("toolbar_new")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New")
                    }

                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.testTag("toolbar_open")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Open")
                    }

                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.testTag("toolbar_save")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }

                    Box(
                        modifier = Modifier
                            .testTag("toolbar_export_pdf_container")
                            .then(
                                if (!exportPdfEnabled) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures {
                                            topBarScope.launch {
                                                snackbarHostState.showSnackbar(exportPdfDisabledMessage)
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        IconButton(
                            onClick = onExportPdf,
                            enabled = exportPdfEnabled,
                            modifier = Modifier.testTag("toolbar_export_pdf")
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF")
                        }
                    }

                    OverflowMenu(
                        onSaveAs = onSaveAs,
                        onDuplicateForMate = onDuplicateForMate,
                        onCloseDocument = onCloseDocument,
                        onOpenSettings = onOpenSettings,
                        onSendFeedback = onSendFeedback,
                        onOpenDeveloperOptions = onOpenDeveloperOptions,
                        showDeveloperOptions = devOptionsEnabled,
                    )
                }
            )
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp)
        ) {
            // Separator (matches the divider below the preview)
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Preview
            PreviewCard(
                showGrid = showGrid,
                spec = spec,
                resolvedComponents = resolvedComponents,
                unit = unit,
                overallIsManual = overallIsManual,
                devOptionsEnabled = devOptionsEnabled,
                showOalInPreviewBox = showOalInPreviewBox,
                highlightEnabled = showHighlightSelection,
                highlightId = selectedComponentId,
                onTapComponentId = { onSelectComponentById(it) },
                showRenderLayoutDebugOverlay = showRenderLayoutDebugOverlay,
                showRenderOalMarkers = showRenderOalMarkers,
                showDimDebugOverlay = showDimDebugOverlay,
                pdfTieringMode = pdfTieringMode,
                previewOutline = previewOutline,
                previewBodyFill = previewBodyFill,
                previewTaperFill = previewTaperFill,
                previewLinerFill = previewLinerFill,
                previewThreadFill = previewThreadFill,
                previewThreadHatch = previewThreadHatch,
                previewBlackWhiteOnly = previewBlackWhiteOnly,
                lineThicknessScale = lineThicknessScale,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp)
                    .aspectRatio(3.0f)
            )
            // NOTE: Highlight state is threaded into ShaftDrawing via PreviewCard.

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Scrollable editor content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Length (auto vs manual — always show a value)
                var hasLenFocus by remember { mutableStateOf(false) }
                var lenTextOnFocus by remember { mutableStateOf<String?>(null) }

                val effectiveOalDisplayMm = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec).oalMm.toFloat() }
                val displayMm = if (overallIsManual) spec.overallLengthMm else effectiveOalDisplayMm
                var lengthText by remember(unit, overallIsManual) {
                    mutableStateOf(formatDisplay(displayMm, unit))
                }
                // The field echoes the user's own text — a typed "150 3/4" stays a fraction
                // instead of being rewritten to "150.75" (on-device report). The model was
                // previously a remember key, so its per-keystroke echo reformatted the text
                // mid-typing. Now the display only re-derives from the model when the field
                // is unfocused AND its text no longer explains the model value (auto-mode
                // recompute, undo, an edit from elsewhere).
                LaunchedEffect(displayMm, unit, overallIsManual, hasLenFocus) {
                    if (!hasLenFocus) {
                        val parsed = toMmOrNull(lengthText, unit)
                        if (parsed == null || kotlin.math.abs(parsed - displayMm) > 0.01f) {
                            lengthText = formatDisplay(displayMm, unit)
                        }
                    }
                }

                val isOversized = spec.overallLengthMm < lastOccupiedEndMm(spec)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = lengthText,
                        onValueChange = { input ->
                            // Default to Auto until the user types.
                            if (!overallIsManual && input != lengthText) {
                                onSetOverallIsManual(true)
                            }

                            lengthText = input
                            if (overallIsManual) {
                                toMmOrNull(input, unit)?.let { mm ->
                                    onSetOverallLengthMm(mm)
                                }
                            }
                        },
                        label = { Text("Overall Length (${abbr(unit)})") },
                        singleLine = true,
                        enabled = true,
                        isError = isOversized,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            val t = lengthText.trim()
                            if (t.isEmpty()) {
                                onSetOverallIsManual(false)
                                val end = lastOccupiedEndMm(spec)
                                onSetOverallLengthMm(end)
                                val effective = computeOalWindow(spec.copy(overallLengthMm = end)).oalMm.toFloat()
                                lengthText = formatDisplay(effective, unit)
                            } else {
                                toMmOrNull(t, unit)?.let { mm ->
                                    onSetOverallLengthMm(mm)
                                    onSetOverallIsManual(true)
                                    onSetOverallLengthRaw(t) // keep user’s display text
                                }
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { f ->
                                val wasFocused = hasLenFocus
                                hasLenFocus = f.isFocused

                                if (!wasFocused && f.isFocused) {
                                    // Capture initial text so tapping the field in Auto doesn't
                                    // accidentally flip us into Manual when the user didn't edit.
                                    lenTextOnFocus = lengthText
                                    // Clear "0" so the user can type without a leading zero.
                                    if (lengthText.trim() == "0") lengthText = ""
                                }

                                if (wasFocused && !f.isFocused) {
                                    val baseline = lenTextOnFocus
                                    lenTextOnFocus = null
                                    val t = lengthText.trim()

                                    // If we were in Auto and the user didn't change anything,
                                    // don't flip into Manual.
                                    if (!overallIsManual && baseline != null && lengthText == baseline) {
                                        return@onFocusChanged
                                    }

                                    if (t.isEmpty()) {
                                        onSetOverallIsManual(false)
                                        val end = lastOccupiedEndMm(spec)
                                        onSetOverallLengthMm(end)
                                        val effective = computeOalWindow(spec.copy(overallLengthMm = end)).oalMm.toFloat()
                                        lengthText = formatDisplay(effective, unit)
                                    } else {
                                        toMmOrNull(t, unit)?.let { mm ->
                                            onSetOverallLengthMm(mm)
                                            onSetOverallIsManual(true)
                                            onSetOverallLengthRaw(t)
                                        }
                                    }
                                }
                            }
                    )

                    Spacer(Modifier.width(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !overallIsManual,
                            onClick = {
                                if (overallIsManual) {
                                    onSetOverallIsManual(false)
                                    val end = lastOccupiedEndMm(spec)
                                    onSetOverallLengthMm(end)
                                    val effective = computeOalWindow(spec.copy(overallLengthMm = end)).oalMm.toFloat()
                                    lengthText = formatDisplay(effective, unit)
                                }
                            },
                            label = { Text("Auto") }
                        )
                        FilterChip(
                            selected = overallIsManual,
                            onClick = {
                                if (!overallIsManual) {
                                    onSetOverallIsManual(true)
                                }
                            },
                            label = { Text("Manual") }
                        )
                    }
                }

                // Read-only: computed OAL in measurement space (less excluded end threads)
                val win = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec) }
                val physicalOalMm = spec.overallLengthMm.toDouble()
                val effectiveOalWindowMm = win.oalMm
                val excluded = kotlin.math.abs(effectiveOalWindowMm - physicalOalMm) > OAL_EPS_MM

                // Normally only show in Manual mode; Auto already displays effective OAL.
                // Developer option can force it on for debugging.
                if (excluded && (overallIsManual || showOalHelperLine)) {
                    Text(
                        text = "Dimensioned OAL: ${formatDisplay(effectiveOalWindowMm.toFloat(), unit)} ${abbr(unit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (showOalDebugLabel) {
                    val coveredEndMm = lastOccupiedEndMm(spec)
                    Text(
                        text = "OAL debug • physical=${formatDisplay(spec.overallLengthMm, unit)} ${abbr(unit)} • effective=${formatDisplay(effectiveOalWindowMm.toFloat(), unit)} ${abbr(unit)} • covered=${formatDisplay(coveredEndMm, unit)} ${abbr(unit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Components",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Swipe to select",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Button(
                        onClick = { chooserOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("+ Add Component")
                    }
                }

                SpecWarningBanner(spec = spec)

                ComponentCarouselPager(
                    spec = spec,
                    resolvedComponents = resolvedComponents,
                    unit = unit,
                    showEdgeArrows = showComponentArrows,
                    edgeArrowWidthDp = componentArrowWidthDp,
                    showComponentDebugLabels = showComponentDebugLabels,
                    componentTitlesDefault = componentTitlesDefault,
                    componentShadeDefaults = componentShadeDefaults,
                    selectedComponentId = selectedComponentId,
                    // Auto-body promotion adds a plain body; keyways and blends are added
                    // later via the promoted card's own fields.
                    onAddBody = { s, l, d ->
                        onAddBody(
                            s, l, d, 0f, 0f, 0f, 0f, LinerAuthoredReference.AFT, false, null,
                            0f, 0f, BlendProfile.OGEE, false, false,
                        )
                    },
                    onSetAutoSectionDia = onSetAutoSectionDia,
                    onSetAutoBlend = onSetAutoBlend,
                    onSetShowAutoBodyDia = onSetShowAutoBodyDia,
                    onUpdateBody = onUpdateBody,
                    onUpdateBodyShowDia = onUpdateBodyShowDia,
                    onUpdateBodyShowLabel = onUpdateBodyShowLabel,
                    onUpdateBodyShade = onUpdateBodyShade,
                    onUpdateBodyCompressOnDrawing = onUpdateBodyCompressOnDrawing,
                    onUpdateBodyBlend = onUpdateBodyBlend,
                    onUpdateBodyLabel = onUpdateBodyLabel,
                    onUpdateBodyKeyway = onUpdateBodyKeyway,
                    onUpdateTaper = onUpdateTaper,
                    onUpdateTaperLabel = onUpdateTaperLabel,
                    onUpdateTaperShowLabel = onUpdateTaperShowLabel,
                    onUpdateTaperShade = onUpdateTaperShade,
                    onUpdateTaperKeyway = onUpdateTaperKeyway,
                    onUpdateTaperReference = onUpdateTaperReference,
                    onUpdateThread = onUpdateThread,
                    onUpdateThreadLabel = onUpdateThreadLabel,
                    onUpdateThreadShowLabel = onUpdateThreadShowLabel,
                    onUpdateLiner = onUpdateLiner,
                    onUpdateLinerShowDia = onUpdateLinerShowDia,
                    onUpdateLinerShowLabel = onUpdateLinerShowLabel,
                    onUpdateLinerShade = onUpdateLinerShade,
                    onUpdateLinerShoulder = onUpdateLinerShoulder,
                    linerShouldersEnabled = linerShouldersEnabled,
                    onUpdateLinerLabel = onUpdateLinerLabel,
                    onUpdateLinerReference = onUpdateLinerReference,
                    onUpdateCouplerBoltSlot = onUpdateCouplerBoltSlot,
                    onUpdateCouplerBoltSlotReference = onUpdateCouplerBoltSlotReference,
                    onUpdateCouplerBoltSlotShowRail = onUpdateCouplerBoltSlotShowRail,
                    onSetKeyways180Apart = onSetKeyways180Apart,
                    onSetKeyways90Apart = onSetKeyways90Apart,
                    onSetKeyways90Cw = onSetKeyways90Cw,

                    onSetThreadExcludeFromOal = onSetThreadExcludeFromOal,
                    onSetThreadEndPosition = onSetThreadEndPosition,

                    onRemoveBody = onRemoveBody,
                    onRemoveTaper = onRemoveTaper,
                    onRemoveThread = onRemoveThread,
                    onRemoveLiner = onRemoveLiner,
                    onRemoveCouplerBoltSlot = onRemoveCouplerBoltSlot,
                    onSelectComponentById = onSelectComponentById,
                    collidingComponentIds = collidingComponentIds,
                    perComponentUnitsEnabled = perComponentUnitsEnabled,
                    unitOverrides = unitOverrides,
                    onSetComponentUnit = onSetComponentUnit,
                    onSetKeywayUnit = onSetKeywayUnit,
                )

                if (chooserOpen) {
                    val d = computeAddDefaults(spec)

                    InlineAddChooserDialog(
                        onDismiss = { chooserOpen = false },
                        onAddBody = {
                            chooserOpen = false
                            addStartMm = d.startMm
                            addLengthMm = if (overallIsManual && spec.overallLengthMm > d.startMm) {
                                spec.overallLengthMm - d.startMm
                            } else {
                                sessionAddDefaults.bodyLenMm
                            }
                            addBodyOpen = true
                        },
                        onAddLiner = {
                            chooserOpen = false
                            addStartMm = d.startMm
                            addLengthMm = sessionAddDefaults.linerLenMm
                            addLinerOpen = true
                        },
                        onAddThread = {
                            chooserOpen = false
                            addThreadStartMm = d.startMm
                            addThreadOpen = true
                        },
                        onAddTaper = {
                            chooserOpen = false
                            addStartMm = d.startMm
                            addLengthMm = sessionAddDefaults.taperLenMm
                            addTaperOpen = true
                        },
                        onAddCouplerBoltSlot = {
                            chooserOpen = false
                            addSlotStartMm = d.startMm
                            addSlotOpen = true
                        }
                    )
                }

                if (addThreadOpen) {
                    AddThreadDialog(
                        unit = unit,
                        spec = spec,
                        overallIsManual = overallIsManual,
                        initialStartMm = addThreadStartMm,
                        initialLengthMm = sessionAddDefaults.threadLenMm,
                        initialMajorDiaMm = sessionAddDefaults.threadMajorDiaMm,
                        initialPitchMm = sessionAddDefaults.threadPitchMm,
                        dialogUnitConverterEnabled = dialogUnitConverterEnabled,
                        onSubmit = { startMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL, isAftEnd, metricDesignation ->
                            addThreadOpen = false
                            // IMPORTANT: argument order is start, length, majorDia, pitch, excludeFromOAL,
                            // isAftEnd, metricDesignation. The dialog already converts TPI to pitch (or
                            // reads pitch straight off a metric designation) so this call never re-derives
                            // it. Keep this aligned with `ShaftRoute`/`ShaftViewModel.addThreadAt`.
                            onAddThread(
                                startMm,
                                lengthMm,
                                majorDiaMm,
                                pitchMm,
                                excludeFromOAL,
                                isAftEnd,
                                metricDesignation
                            )
                        },
                        onCancel = { addThreadOpen = false }
                    )
                }

                if (addSlotOpen) {
                    AddCouplerBoltSlotDialog(
                        unit = unit,
                        spec = spec,
                        initialStartMm = addSlotStartMm,
                        initialHoleDiaMm = sessionAddDefaults.slotHoleDiaMm,
                        initialCount = sessionAddDefaults.slotCount,
                        initialSpacingMm = sessionAddDefaults.slotSpacingMm,
                        initialDepthMm = sessionAddDefaults.slotDepthMm,
                        dialogUnitConverterEnabled = dialogUnitConverterEnabled,
                        onSubmit = { startMm, holeDiaMm, count, spacingMm, through, depthMm, ref ->
                            addSlotOpen = false
                            onAddCouplerBoltSlot(startMm, holeDiaMm, count, spacingMm, through, depthMm, ref)
                        },
                        onCancel = { addSlotOpen = false }
                    )
                }

                if (addBodyOpen) {
                    AddBodyDialog(
                        unit = unit,
                        spec = spec,
                        initialStartMm = addStartMm,
                        initialLengthMm = addLengthMm,
                        perComponentUnitsEnabled = perComponentUnitsEnabled,
                        dialogUnitConverterEnabled = dialogUnitConverterEnabled,
                        onSubmit = { s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, k180, k90, cw90, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd ->
                            addBodyOpen = false
                            onAddBody(s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd)
                            onSetKeyways180Apart(k180)
                            onSetKeyways90Apart(k90)
                            if (k90) onSetKeyways90Cw(cw90)
                        },
                        onCancel = { addBodyOpen = false }
                    )
                }

                if (addLinerOpen) {
                    AddLinerDialog(
                        unit = unit,
                        spec = spec,
                        overallIsManual = overallIsManual,
                        initialStartMm = addStartMm,
                        initialLengthMm = addLengthMm,
                        linerShouldersEnabled = linerShouldersEnabled,
                        dialogUnitConverterEnabled = dialogUnitConverterEnabled,
                        onSubmit = { s, l, od, ref, shoulders ->
                            addLinerOpen = false
                            onAddLiner(s, l, od, ref, shoulders)
                        },
                        onCancel = { addLinerOpen = false }
                    )
                }

                if (addTaperOpen) {
                    AddTaperDialog(
                        unit = unit,
                        spec = spec,
                        overallIsManual = overallIsManual,
                        initialStartMm = addStartMm,
                        initialLengthMm = addLengthMm,
                        perComponentUnitsEnabled = perComponentUnitsEnabled,
                        dialogUnitConverterEnabled = dialogUnitConverterEnabled,
                        onSubmit = { s, l, startDia, endDia, rate, ref, kwW, kwD, kwL, kwO, kwSpooned, k180, k90, cw90, kwUnit ->
                            addTaperOpen = false
                            onAddTaper(s, l, startDia, endDia, rate, ref, kwW, kwD, kwL, kwO, kwSpooned, kwUnit)
                            onSetKeyways180Apart(k180)
                            onSetKeyways90Apart(k90)
                            if (k90) onSetKeyways90Cw(cw90)
                        },
                        onCancel = { addTaperOpen = false }
                    )
                }

                if (projectInfoOpen) {
                    ProjectInfoBottomSheet(
                        customer = customer,
                        vessel = vessel,
                        jobNumber = jobNumber,
                        item = item,
                        shaftPosition = shaftPosition,
                        notes = notes,
                        onSetCustomer = onSetCustomer,
                        onSetVessel = onSetVessel,
                        onSetJobNumber = onSetJobNumber,
                        onSetItem = onSetItem,
                        onSetShaftPosition = onSetShaftPosition,
                        onSetNotes = onSetNotes,
                        onDismiss = { projectInfoOpen = false }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun HistoryMenu(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("toolbar_history")
        ) {
            Icon(
                imageVector = Icons.Filled.ManageHistory,
                contentDescription = "History"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Undo") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                enabled = canUndo,
                modifier = Modifier.testTag("history_undo_delete"),
                onClick = {
                    expanded = false
                    onUndo()
                }
            )
            DropdownMenuItem(
                text = { Text("Redo") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null) },
                enabled = canRedo,
                modifier = Modifier.testTag("history_redo_delete"),
                onClick = {
                    expanded = false
                    onRedo()
                }
            )
        }
    }
}

@Composable
private fun OverflowMenu(
    onSaveAs: () -> Unit,
    onDuplicateForMate: () -> Unit,
    onCloseDocument: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    showDeveloperOptions: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("toolbar_overflow")
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Save As…") },
                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                modifier = Modifier.testTag("overflow_save_as"),
                onClick = {
                    expanded = false
                    onSaveAs()
                }
            )
            DropdownMenuItem(
                text = { Text("Duplicate for mate…") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.testTag("overflow_duplicate_for_mate"),
                onClick = {
                    expanded = false
                    onDuplicateForMate()
                }
            )
            DropdownMenuItem(
                text = { Text("Close Document") },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                modifier = Modifier.testTag("overflow_close_document"),
                onClick = {
                    expanded = false
                    onCloseDocument()
                }
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.testTag("overflow_settings"),
                onClick = {
                    expanded = false
                    onOpenSettings()
                }
            )
            DropdownMenuItem(
                text = { Text("Send feedback") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.testTag("overflow_feedback"),
                onClick = {
                    expanded = false
                    onSendFeedback()
                }
            )
            if (showDeveloperOptions) {
                DropdownMenuItem(
                    text = { Text("Developer options") },
                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
                    modifier = Modifier.testTag("overflow_dev_options"),
                    onClick = {
                        expanded = false
                        onOpenDeveloperOptions()
                    }
                )
            }
        }
    }
}

@Composable
internal fun ShaftPositionDropdown(
    selected: ShaftPosition,
    onSelected: (ShaftPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember {
        listOf(ShaftPosition.PORT, ShaftPosition.STBD, ShaftPosition.CENTER, ShaftPosition.OTHER)
    }

    Column(modifier = modifier) {
        Text(
            text = "Shaft Position",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selected.uiLabel(), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Icon(Icons.Filled.ExpandMore, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.uiLabel()) },
                        onClick = {
                            onSelected(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/* ───────────────── Shared composables & helpers ───────────────── */


@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ProjectInfoBottomSheet(
    customer: String,
    vessel: String,
    jobNumber: String,
    item: String,
    shaftPosition: ShaftPosition,
    notes: String,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetItem: (String) -> Unit,
    onSetShaftPosition: (ShaftPosition) -> Unit,
    onSetNotes: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The sheet edits a DRAFT copy; nothing reaches the ViewModel until Save. Blur-commit
    // fields would lose the last field's text when the sheet closed straight from the
    // keyboard, and gave Cancel nothing to revert to.
    var draftJobNumber by rememberSaveable { mutableStateOf(jobNumber) }
    var draftCustomer by rememberSaveable { mutableStateOf(customer) }
    var draftVessel by rememberSaveable { mutableStateOf(vessel) }
    var draftItem by rememberSaveable { mutableStateOf(item) }
    var draftPosition by rememberSaveable { mutableStateOf(shaftPosition) }
    var draftNotes by rememberSaveable { mutableStateOf(notes) }

    var confirmDiscardOpen by rememberSaveable { mutableStateOf(false) }

    val dirty = draftJobNumber != jobNumber ||
        draftCustomer != customer ||
        draftVessel != vessel ||
        draftItem != item ||
        draftPosition != shaftPosition ||
        draftNotes != notes
    // The sheet state is created once, so its gate must read the LIVE dirty flag rather
    // than the value captured at creation.
    val dirtyNow = rememberUpdatedState(dirty)

    // Implicit exits (swipe down, scrim tap, back) are guarded when the draft differs;
    // blocking the settle keeps the sheet in place under the dialog, so "Keep editing"
    // costs no second animation. An explicit Cancel is NOT guarded — confirming a
    // deliberate discard is just a second prompt for the same decision.
    val gateHide = remember {
        { target: SheetValue ->
            if (target == SheetValue.Hidden && dirtyNow.value) {
                confirmDiscardOpen = true
                false
            } else true
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = gateHide
    )

    // Pushes only the changed fields, so opening and saving without an edit never marks
    // the document dirty.
    val commitDraft = {
        if (draftJobNumber != jobNumber) onSetJobNumber(draftJobNumber)
        if (draftCustomer != customer) onSetCustomer(draftCustomer)
        if (draftVessel != vessel) onSetVessel(draftVessel)
        if (draftItem != item) onSetItem(draftItem)
        if (draftPosition != shaftPosition) onSetShaftPosition(draftPosition)
        if (draftNotes != notes) onSetNotes(draftNotes)
        onDismiss()
    }

    ModalBottomSheet(
        // A clean draft dismisses straight away; a dirty one asks first. Discarding drops
        // the draft entirely — the document keeps whatever it already held, so a field
        // that started blank goes back to blank.
        onDismissRequest = { if (dirty) confirmDiscardOpen = true else onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("project_info_sheet")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .imePadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Project Information",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            DraftTextField(
                label = "Job Number",
                value = draftJobNumber,
                onValueChange = { draftJobNumber = it },
                modifier = Modifier.fillMaxWidth().testTag("project_info_job_number")
            )
            DraftTextField(
                label = "Customer",
                value = draftCustomer,
                onValueChange = { draftCustomer = it },
                modifier = Modifier.fillMaxWidth().testTag("project_info_customer")
            )
            DraftTextField(
                label = "Vessel",
                value = draftVessel,
                onValueChange = { draftVessel = it },
                modifier = Modifier.fillMaxWidth().testTag("project_info_vessel")
            )
            // Optional shaft designation ("Tail shaft", "Line shaft"); blank prints nothing.
            DraftTextField(
                label = "Item (optional)",
                value = draftItem,
                onValueChange = { draftItem = it },
                modifier = Modifier.fillMaxWidth().testTag("project_info_item")
            )
            ShaftPositionDropdown(
                selected = draftPosition,
                onSelected = { draftPosition = it },
                modifier = Modifier.fillMaxWidth()
            )
            DraftTextField(
                label = "Notes",
                value = draftNotes,
                onValueChange = { draftNotes = it },
                modifier = Modifier.fillMaxWidth().testTag("project_info_notes"),
                singleLine = false,
                minHeight = 88.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("project_info_cancel")
                ) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = commitDraft,
                    modifier = Modifier.testTag("project_info_save")
                ) { Text("Save") }
            }
        }

        if (confirmDiscardOpen) {
            AlertDialog(
                // Dismissing the dialog itself means neither — put the user back in the
                // sheet, which never went anywhere.
                onDismissRequest = { confirmDiscardOpen = false },
                title = { Text("Save changes?") },
                text = { Text("The project information was edited but not saved.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDiscardOpen = false
                            commitDraft()
                        },
                        modifier = Modifier.testTag("project_info_discard_save")
                    ) { Text("Save") }
                },
                dismissButton = {
                    // Two choices share the dismiss slot so "Keep editing" is a visible
                    // button, not only a tap-outside gesture: an accidental swipe is the
                    // whole reason this dialog exists.
                    Row {
                        TextButton(
                            onClick = { confirmDiscardOpen = false },
                            modifier = Modifier.testTag("project_info_keep_editing")
                        ) { Text("Keep editing") }
                        TextButton(
                            onClick = {
                                confirmDiscardOpen = false
                                onDismiss()
                            },
                            modifier = Modifier.testTag("project_info_discard_confirm")
                        ) { Text("Discard") }
                    }
                }
            )
        }
    }
}

/**
 * Plain draft field: text lives in the caller's state and is never pushed anywhere on
 * blur — the owning sheet decides when (and whether) it is committed.
 */
@Composable
private fun DraftTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minHeight: Dp = Dp.Unspecified
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        ),
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
    )
}


/* ───────────────── Helpers: units, parsing, badge math, defaults ───────────────── */

internal fun abbr(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

internal fun formatDisplay(valueMm: Float, unit: UnitSystem, d: Int = 3): String {
    // Decimals:
    // • Millimeters: use the requested precision (default 3).
    // • Inches: force at least 4 decimals so common fractions (1/16, 1/32) are exact
    //   when entered as decimals (0.4375, 0.3125, etc.).
    val decimals = when (unit) {
        UnitSystem.INCHES -> maxOf(d, 4)
        UnitSystem.MILLIMETERS -> d
    }

    val v = if (unit == UnitSystem.MILLIMETERS) {
        valueMm
    } else {
        (valueMm.toDouble() / MM_PER_IN).toFloat()
    }

    return "%.${decimals}f"
        .format(v)
        .trimEnd('0')
        .trimEnd('.')
        .ifEmpty { "0" }
}

/** Convenience wrapper for "mm → display" in the component cards. */
internal fun disp(mm: Float, unit: UnitSystem, d: Int = 3): String =
    formatDisplay(mm, unit, d)


private const val OAL_EPS_MM: Double = 1e-3

internal fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) (MM_PER_IN / tpi.toDouble()).toFloat() else 0f
