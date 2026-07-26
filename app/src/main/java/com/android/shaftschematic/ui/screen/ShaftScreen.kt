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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.android.shaftschematic.doc.stripShaftDocExtension
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.ui.dialog.InlineAddChooserDialog
import com.android.shaftschematic.ui.order.ComponentKey
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.viewmodel.SessionAddDefaults
import com.android.shaftschematic.ui.viewmodel.buildSnapAnchors
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.PreviewColorSetting
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
 * • Component carousel (edit & remove) — honors cross-type ID order
 * • Add-component FAB floating above IME & nav bar
 *
 * Contract / Invariants
 * • Canonical model units are millimeters (mm) — convert only at UI edge.
 * • No geometry-based resorting. When provided, UI renders strictly by componentOrder (IDs).
 * • IME safety: scroll area uses imePadding; FAB uses ime ∪ navigationBars insets.
 * • No file I/O or routing here.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShaftScreen(
    resetNonce: Int,
    // Ordering (from VM via Route)
    componentOrder: List<ComponentKey> = emptyList(),

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
    shaftPosition: ShaftPosition,
    notes: String,
    showGrid: Boolean,
    showOalDebugLabel: Boolean,
    showOalHelperLine: Boolean,
    showOalInPreviewBox: Boolean,
    showComponentDebugLabels: Boolean,
    showRenderLayoutDebugOverlay: Boolean,
    showRenderOalMarkers: Boolean,
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

    // Setters
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetShaftPosition: (ShaftPosition) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,
    onSetOverallLengthMm: (Float) -> Unit,
    onSetOverallIsManual: (Boolean) -> Unit,
    onSelectComponentById: (String?) -> Unit,

    // Tap-to-add pipeline
    pendingAddPositionMm: Float? = null,
    pendingAddGapMm: Float = 50f,
    onTapAtRawMm: (Float) -> Unit = {},
    onClearPendingAddPosition: () -> Unit = {},

    // Adds (all mm)
    onAddBody: (startMm: Float, lengthMm: Float, diaMm: Float,
                keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                keywayOffsetFromEndMm: Float, keywayEnd: LinerAuthoredReference,
                keywaySpooned: Boolean) -> Unit,
    onSetAutoBodyDia: (Float) -> Unit,
    onAddTaper: (Float, Float, Float, Float, String, Float, Float, Float, Float, Boolean) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOAL: Boolean, isAftEnd: Boolean) -> Unit,
    onAddLiner: (Float, Float, Float, LinerAuthoredReference) -> Unit,
    onAddCouplerBoltSlot: (startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float, reference: SlotAuthoredReference) -> Unit,

    // Updates (all mm)
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateBodyLabel: (Int, String?) -> Unit,
    onUpdateBodyKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromEndMm: Float, end: LinerAuthoredReference, spooned: Boolean) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperLabel: (Int, String?) -> Unit,
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateTaperReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateThread: (Int, Float, Float, Float, Float) -> Unit,
    onUpdateThreadLabel: (Int, String?) -> Unit,
    onUpdateLiner: (Int, Float, Float, Float) -> Unit,
    onUpdateLinerLabel: (Int, String?) -> Unit,
    onUpdateLinerReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlot: (index: Int, startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float) -> Unit,
    onUpdateCouplerBoltSlotReference: (Int, SlotAuthoredReference) -> Unit,
    onUpdateCouplerBoltSlotShowRail: (Int, Boolean) -> Unit,

    onSetKeyways180Apart: (Boolean) -> Unit,
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

    // Tap-to-add: after chooser selection, position is captured here while the add dialog is open
    var tapAddBodyOpen by rememberSaveable { mutableStateOf(false) }
    var tapAddLinerOpen by rememberSaveable { mutableStateOf(false) }
    var tapAddTaperOpen by rememberSaveable { mutableStateOf(false) }
    var tapAddStartMm by rememberSaveable { mutableFloatStateOf(0f) }
    var tapAddGapMm by rememberSaveable { mutableFloatStateOf(50f) }

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

    val snapAnchors = remember(spec.overallLengthMm, spec.bodies, spec.tapers, spec.threads, spec.liners) { buildSnapAnchors(spec) }

    val snappedBodyUpdater = remember(snapAnchors, onUpdateBody) {
        { index: Int, startMm: Float, lengthMm: Float, diaMm: Float ->
            applySnappedBodyUpdate(
                onUpdate = onUpdateBody,
                index = index,
                rawStartMm = startMm,
                rawEndMm = startMm + lengthMm,
                diaMm = diaMm,
                anchors = snapAnchors
            )
        }
    }

    val snappedTaperUpdater = remember(snapAnchors, onUpdateTaper) {
        { index: Int, startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float, rateText: String ->
            applySnappedTaperUpdate(
                onUpdate = onUpdateTaper,
                index = index,
                rawStartMm = startMm,
                rawEndMm = startMm + lengthMm,
                startDiaMm = startDiaMm,
                endDiaMm = endDiaMm,
                rateText = rateText,
                anchors = snapAnchors
            )
        }
    }

    val snappedThreadUpdater = remember(snapAnchors, onUpdateThread) {
        { index: Int, startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float ->
            applySnappedThreadUpdate(
                onUpdate = onUpdateThread,
                index = index,
                rawStartMm = startMm,
                rawEndMm = startMm + lengthMm,
                majorDiaMm = majorDiaMm,
                pitchMm = pitchMm,
                anchors = snapAnchors
            )
        }
    }

    val snappedLinerUpdater = remember(snapAnchors, onUpdateLiner) {
        { index: Int, startMm: Float, lengthMm: Float, odMm: Float ->
            applySnappedLinerUpdate(
                onUpdate = onUpdateLiner,
                index = index,
                rawStartMm = startMm,
                rawEndMm = startMm + lengthMm,
                odMm = odMm,
                anchors = snapAnchors
            )
        }
    }

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
            Text(
                text = buildString {
                    append(documentName?.let(::stripShaftDocExtension) ?: "Untitled draft")
                    if (hasUnsavedChanges) append(" *")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("editor_document_title"),
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
                onTapAtMm = onTapAtRawMm,
                showRenderLayoutDebugOverlay = showRenderLayoutDebugOverlay,
                showRenderOalMarkers = showRenderOalMarkers,
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
                    .verticalScroll(scroll)
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Length (auto vs manual — always show a value)
                var hasLenFocus by remember { mutableStateOf(false) }
                var lenTextOnFocus by remember { mutableStateOf<String?>(null) }

                val effectiveOalDisplayMm = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec).oalMm.toFloat() }
                val displayMm = if (overallIsManual) spec.overallLengthMm else effectiveOalDisplayMm
                var lengthText by remember(unit, displayMm, overallIsManual) {
                    mutableStateOf(formatDisplay(displayMm, unit))
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

                ComponentCarouselPager(
                    spec = spec,
                    resolvedComponents = resolvedComponents,
                    unit = unit,
                    componentOrder = componentOrder,
                    showEdgeArrows = showComponentArrows,
                    edgeArrowWidthDp = componentArrowWidthDp,
                    showComponentDebugLabels = showComponentDebugLabels,
                    selectedComponentId = selectedComponentId,
                    // Auto-body promotion adds a plain body; keyways are added later
                    // via the promoted card's keyway fields.
                    onAddBody = { s, l, d ->
                        onAddBody(s, l, d, 0f, 0f, 0f, 0f, LinerAuthoredReference.AFT, false)
                    },
                    onSetAutoBodyDia = onSetAutoBodyDia,
                    onUpdateBody = snappedBodyUpdater,
                    onUpdateBodyLabel = onUpdateBodyLabel,
                    onUpdateBodyKeyway = onUpdateBodyKeyway,
                    onUpdateTaper = snappedTaperUpdater,
                    onUpdateTaperLabel = onUpdateTaperLabel,
                    onUpdateTaperKeyway = onUpdateTaperKeyway,
                    onUpdateTaperReference = onUpdateTaperReference,
                    onUpdateThread = snappedThreadUpdater,
                    onUpdateThreadLabel = onUpdateThreadLabel,
                    onUpdateLiner = snappedLinerUpdater,
                    onUpdateLinerLabel = onUpdateLinerLabel,
                    onUpdateLinerReference = onUpdateLinerReference,
                    onUpdateCouplerBoltSlot = onUpdateCouplerBoltSlot,
                    onUpdateCouplerBoltSlotReference = onUpdateCouplerBoltSlotReference,
                    onUpdateCouplerBoltSlotShowRail = onUpdateCouplerBoltSlotShowRail,
                    onSetKeyways180Apart = onSetKeyways180Apart,

                    onSetThreadExcludeFromOal = onSetThreadExcludeFromOal,
                    onSetThreadEndPosition = onSetThreadEndPosition,

                    onRemoveBody = onRemoveBody,
                    onRemoveTaper = onRemoveTaper,
                    onRemoveThread = onRemoveThread,
                    onRemoveLiner = onRemoveLiner,
                    onRemoveCouplerBoltSlot = onRemoveCouplerBoltSlot,
                    onSelectComponentById = onSelectComponentById,
                    collidingComponentIds = collidingComponentIds,
                )

                if (chooserOpen) {
                    val d = computeAddDefaults(spec)

                    InlineAddChooserDialog(
                        onDismiss = { chooserOpen = false },
                        onAddBody = {
                            chooserOpen = false
                            tapAddStartMm = d.startMm
                            tapAddGapMm = if (overallIsManual && spec.overallLengthMm > d.startMm) {
                                spec.overallLengthMm - d.startMm
                            } else {
                                sessionAddDefaults.bodyLenMm
                            }
                            tapAddBodyOpen = true
                        },
                        onAddLiner = {
                            chooserOpen = false
                            tapAddStartMm = d.startMm
                            tapAddGapMm = sessionAddDefaults.linerLenMm
                            tapAddLinerOpen = true
                        },
                        onAddThread = {
                            chooserOpen = false
                            addThreadStartMm = d.startMm
                            addThreadOpen = true
                        },
                        onAddTaper = {
                            chooserOpen = false
                            tapAddStartMm = d.startMm
                            tapAddGapMm = sessionAddDefaults.taperLenMm
                            tapAddTaperOpen = true
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
                        onSubmit = { startMm, lengthMm, majorDiaMm, tpi, excludeFromOAL, isAftEnd ->
                            addThreadOpen = false
                            // IMPORTANT: argument order is start, length, majorDia, pitch, excludeFromOAL.
                            // Keep this aligned with `ShaftRoute`/`ShaftViewModel.addThreadAt` to avoid
                            // pitch/major swaps.
                            onAddThread(
                                startMm,
                                lengthMm,
                                majorDiaMm,
                                tpiToPitchMm(tpi),
                                excludeFromOAL,
                                isAftEnd
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
                        onSubmit = { startMm, holeDiaMm, count, spacingMm, through, depthMm, ref ->
                            addSlotOpen = false
                            onAddCouplerBoltSlot(startMm, holeDiaMm, count, spacingMm, through, depthMm, ref)
                        },
                        onCancel = { addSlotOpen = false }
                    )
                }

                // Tap-to-add chooser — shown when the user taps empty space in the preview.
                // Position is already snapped by the ViewModel before we receive it.
                val tapPosition = pendingAddPositionMm
                if (tapPosition != null) {
                    InlineAddChooserDialog(
                        onDismiss = { onClearPendingAddPosition() },
                        onAddBody = {
                            tapAddStartMm = tapPosition
                            tapAddGapMm = pendingAddGapMm
                            onClearPendingAddPosition()
                            tapAddBodyOpen = true
                        },
                        onAddLiner = {
                            tapAddStartMm = tapPosition
                            tapAddGapMm = pendingAddGapMm
                            onClearPendingAddPosition()
                            tapAddLinerOpen = true
                        },
                        onAddTaper = {
                            tapAddStartMm = tapPosition
                            tapAddGapMm = pendingAddGapMm
                            onClearPendingAddPosition()
                            tapAddTaperOpen = true
                        },
                        onAddThread = {
                            addThreadStartMm = tapPosition
                            onClearPendingAddPosition()
                            addThreadOpen = true
                        },
                        onAddCouplerBoltSlot = {
                            addSlotStartMm = tapPosition
                            onClearPendingAddPosition()
                            addSlotOpen = true
                        },
                    )
                }

                if (tapAddBodyOpen) {
                    AddBodyDialog(
                        unit = unit,
                        spec = spec,
                        initialStartMm = tapAddStartMm,
                        initialLengthMm = tapAddGapMm,
                        onSubmit = { s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, opposed ->
                            tapAddBodyOpen = false
                            onAddBody(s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSpooned)
                            onSetKeyways180Apart(opposed)
                        },
                        onCancel = { tapAddBodyOpen = false }
                    )
                }

                if (tapAddLinerOpen) {
                    AddLinerDialog(
                        unit = unit,
                        spec = spec,
                        overallIsManual = overallIsManual,
                        initialStartMm = tapAddStartMm,
                        initialLengthMm = tapAddGapMm,
                        onSubmit = { s, l, od, ref ->
                            tapAddLinerOpen = false
                            onAddLiner(s, l, od, ref)
                        },
                        onCancel = { tapAddLinerOpen = false }
                    )
                }

                if (tapAddTaperOpen) {
                    AddTaperDialog(
                        unit = unit,
                        spec = spec,
                        overallIsManual = overallIsManual,
                        initialStartMm = tapAddStartMm,
                        initialLengthMm = tapAddGapMm,
                        onSubmit = { s, l, setDia, letDia, rate, kwW, kwD, kwL, kwO, kwSpooned, opposed ->
                            tapAddTaperOpen = false
                            onAddTaper(s, l, setDia, letDia, rate, kwW, kwD, kwL, kwO, kwSpooned)
                            onSetKeyways180Apart(opposed)
                        },
                        onCancel = { tapAddTaperOpen = false }
                    )
                }

                if (projectInfoOpen) {
                    ProjectInfoBottomSheet(
                        customer = customer,
                        vessel = vessel,
                        jobNumber = jobNumber,
                        shaftPosition = shaftPosition,
                        notes = notes,
                        onSetCustomer = onSetCustomer,
                        onSetVessel = onSetVessel,
                        onSetJobNumber = onSetJobNumber,
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
private fun ShaftPositionDropdown(
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
private fun ProjectInfoBottomSheet(
    customer: String,
    vessel: String,
    jobNumber: String,
    shaftPosition: ShaftPosition,
    notes: String,
    onSetCustomer: (String) -> Unit,
    onSetVessel: (String) -> Unit,
    onSetJobNumber: (String) -> Unit,
    onSetShaftPosition: (ShaftPosition) -> Unit,
    onSetNotes: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            CommitTextField("Job Number", jobNumber, onSetJobNumber, Modifier.fillMaxWidth())
            CommitTextField("Customer", customer, onSetCustomer, Modifier.fillMaxWidth())
            CommitTextField("Vessel", vessel, onSetVessel, Modifier.fillMaxWidth())
            ShaftPositionDropdown(
                selected = shaftPosition,
                onSelected = onSetShaftPosition,
                modifier = Modifier.fillMaxWidth()
            )
            CommitTextField(
                label = "Notes",
                initial = notes,
                onCommit = onSetNotes,
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minHeight = 88.dp
            )
        }
    }
}

@Composable
private fun CommitTextField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    minHeight: Dp = Dp.Unspecified
) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = modifier
            .let { if (minHeight != Dp.Unspecified) it.heightIn(min = minHeight) else it }
            .onFocusChanged { f -> if (!f.isFocused) onCommit(text) }
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


internal fun toMmOrNull(text: String, unit: UnitSystem): Float? {
    val t = text.trim(); if (t.isEmpty()) return null
    val num = parseFractionOrDecimal(t) ?: return null
    return if (unit == UnitSystem.MILLIMETERS) num else (num.toDouble() * MM_PER_IN).toFloat()
}


/** Accepts "12", "3/4", "15 1/2", "1.5", or "1:12" (tolerates trailing unit suffixes). */
internal fun parseFractionOrDecimal(input: String): Float? {
    var t = input.replace(",", "").trim(); if (t.isEmpty()) return null

    // Strip trailing unit-ish suffixes like "in", "mm", or quotes.
    run {
        val allowed = "0123456789./:+- "
        var end = t.length - 1
        while (end >= 0 && !allowed.contains(t[end])) end--
        t = if (end >= 0) t.substring(0, end + 1).trim() else ""
        t = t.replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
    }

    // Mixed fraction: W N/D
    val parts = t.split(' ').filter { it.isNotBlank() }
    if (parts.size == 2 && parts[1].contains('/')) {
        val whole = parts[0].toFloatOrNull() ?: return null
        val slash = parts[1].indexOf('/')
        val a = parts[1].substring(0, slash).trim().toFloatOrNull() ?: return null
        val b = parts[1].substring(slash + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        val frac = a / b
        return if (whole < 0f) whole - frac else whole + frac
    }

    val colon = t.indexOf(':')
    if (colon >= 0) {
        val a = t.substring(0, colon).trim().toFloatOrNull() ?: return null
        val b = t.substring(colon + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    val slash = t.indexOf('/')
    if (slash >= 0) {
        val a = t.substring(0, slash).trim().toFloatOrNull() ?: return null
        val b = t.substring(slash + 1).trim().toFloatOrNull() ?: return null
        if (b == 0f) return null
        return a / b
    }
    return t.toFloatOrNull()
}

private const val OAL_EPS_MM: Double = 1e-3

internal fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) (MM_PER_IN / tpi.toDouble()).toFloat() else 0f
