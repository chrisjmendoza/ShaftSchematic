package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.android.shaftschematic.geom.THREAD_DENSITY_DEFAULT
import com.android.shaftschematic.geom.THREAD_SLANT_DEFAULT
import com.android.shaftschematic.geom.computeOalWindow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
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
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.MM_PER_IN
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.settings.PdfTieringMode
import com.android.shaftschematic.ui.adaptive.LocalSidebarPermanent
import com.android.shaftschematic.ui.adaptive.WindowWidthClass
import com.android.shaftschematic.ui.adaptive.currentWindowWidthClass
import com.android.shaftschematic.ui.adaptive.twoPane
import com.android.shaftschematic.ui.dialog.InlineAddChooserDialog
import com.android.shaftschematic.ui.input.shouldCommitOnBlur
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
 * • Top app bar (title, undo/redo, the More menu) — the document's unit is set in Settings →
 *   Units, not here; there is no header unit selector.
 * • Preview drawing (white square; optional grid; fixed-height band)
 * • Overall length input (always user-typed; commits on ACCEPT only — the ✓ beside the
 *   field, IME Done, or a blur after the text changed; ✗ reverts and commits nothing; never
 *   per keystroke — see `docs/contracts/OverallLength.md`)
 * • Project fields (commit-on-blur / IME Done)
 * • Component carousel (edit & remove) — rows in physical order along the shaft
 * • Full-width "+ Add Component" button in the scroll column — the single add entry point
 *
 * Contract / Invariants
 * • Canonical model units are millimeters (mm) — convert only at UI edge.
 * • Carousel rows follow the resolved components' physical order (auto-bodies interleaved at
 *   their spans); there is no separate cross-type order. See `docs/contracts/ComponentsOrdering.md`.
 * • IME safety: imePadding shrinks the scroll viewport (applied before verticalScroll) so
 *   Compose auto-scrolls to keep the focused field in view.
 * • Window width is the ONE adaptive axis: COMPACT is the phone column, MEDIUM raises only the
 *   preview card's height cap, EXPANDED splits into two panes. Every branch draws the SAME
 *   content blocks, declared once below. See `docs/contracts/Adaptive.md`.
 * • No file I/O or routing here.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShaftScreen(
    resetNonce: Int,

    /**
     * Top-bar title. Defaults to the Schematic tab's; the Final Schematic tab hosts this same
     * editor over the document's other geometry and names itself here, so which drawing is
     * being edited is never in doubt.
     */
    editorTitle: String = "Shaft Editor",
    /**
     * Optional strip drawn directly under the top bar, above the editor content — the Final
     * Schematic tab's standing reminder that the original is untouched, carrying that
     * drawing's own actions. Null on the Schematic tab, which draws exactly as before.
     */
    banner: (@Composable () -> Unit)? = null,

    // State
    spec: ShaftSpec,
    /** Saved file name (with extension) of the current document, or null for an unsaved draft. */
    documentName: String? = null,
    /** True while the session differs from the last saved/loaded state (title-bar asterisk). */
    hasUnsavedChanges: Boolean = false,
    /** Tap on the document title strip — names an unsaved document, renames a saved one. */
    onTitleClick: (() -> Unit)? = null,
    resolvedComponents: List<ResolvedComponent> = emptyList(),
    unit: UnitSystem,
    customer: String,
    vessel: String,
    jobNumber: String,
    item: String,
    shaftPosition: ShaftPosition,
    notes: String,
    showGrid: Boolean,
    showOalDebugLabel: Boolean,
    showOalInPreviewBox: Boolean,
    showComponentDebugLabels: Boolean,
    showRenderLayoutDebugOverlay: Boolean,
    showRenderOalMarkers: Boolean,
    showDimDebugOverlay: Boolean = false,
    pdfTieringMode: PdfTieringMode = PdfTieringMode.AUTO,
    /** The Settings "Component names on drawing" switch — the default a card's unset name toggle follows. */
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
    /** App-wide thread-hatch slant (`PdfPrefs.threadSlant`) — the preview hatches like the sheets. */
    threadSlant: Float = THREAD_SLANT_DEFAULT,
    /** App-wide thread-hatch density (`PdfPrefs.threadDensity`) — the preview hatches like the sheets. */
    threadDensity: Float = THREAD_DENSITY_DEFAULT,
    /** PDF-shade mirror for the preview box — components the PDF will print shaded. */
    shadedComponentIds: Set<String> = emptySet(),

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
    onSelectComponentById: (String?) -> Unit,

    // Adds (all mm)
    onAddBody: (startMm: Float, lengthMm: Float, diaMm: Float,
                keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                keywayOffsetFromEndMm: Float, keywayEnd: LinerAuthoredReference,
                keywaySpooned: Boolean, keywayUnit: UnitSystem?,
                blendAftMm: Float, blendFwdMm: Float, blendProfile: BlendProfile,
                blendAftSeal: Boolean, blendFwdSeal: Boolean,
                blendAftSealLenMm: Float, blendFwdSealLenMm: Float) -> Unit,
    onSetAutoSectionDia: (spanStartMm: Float, spanEndMm: Float, diaMm: Float) -> Unit,
    /** Sets the bare-shaft Ø of EVERY auto section together (the card's "All auto sections"). */
    onSetAllAutoSectionsDia: (diaMm: Float) -> Unit,
    onSetAutoBlend: (spanStartMm: Float, spanEndMm: Float, end: LinerAuthoredReference, lengthMm: Float, profile: BlendProfile, seal: Boolean, sealLenMm: Float) -> Unit,
    onSetShowAutoBodyDia: (Boolean) -> Unit,
    onAddTaper: (startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float,
                 rateText: String, reference: LinerAuthoredReference,
                 keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                 keywayOffsetFromSetMm: Float, keywaySpooned: Boolean,
                 keywayUnit: UnitSystem?) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOAL: Boolean,
                  isAftEnd: Boolean, metricDesignation: String?,
                  reliefLenMm: Float, reliefDiaMm: Float, reliefAtAft: Boolean) -> Unit,
    onAddLiner: (Float, Float, Float, LinerAuthoredReference, LinerShoulderDraft) -> Unit,
    onAddCouplerBoltSlot: (startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float, reference: SlotAuthoredReference, holeStyle: BoltHoleStyle, clocking: BoltHoleClocking) -> Unit,

    // Updates (all mm)
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateBodyShowDia: (Int, Boolean) -> Unit,
    onUpdateBodyShowLabel: (Int, Boolean) -> Unit,
    onUpdateBodyShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyCompressOnDrawing: (Int, Boolean) -> Unit,
    onUpdateBodyBlend: (index: Int, blendAftMm: Float, blendFwdMm: Float, profile: BlendProfile, sealAft: Boolean, sealFwd: Boolean, sealAftLenMm: Float, sealFwdLenMm: Float) -> Unit,
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
    onUpdateCouplerBoltSlotStyle: (Int, BoltHoleStyle) -> Unit,
    onUpdateCouplerBoltSlotClocking: (Int, BoltHoleClocking) -> Unit,
    onUpdateCouplerBoltSlotShowRail: (Int, Boolean) -> Unit,

    onSetKeyways180Apart: (Boolean) -> Unit,
    onSetKeyways90Apart: (Boolean) -> Unit,
    onSetKeyways90Cw: (Boolean) -> Unit,
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean) -> Unit,
    onSetThreadEndPosition: (id: String, isAft: Boolean) -> Unit,
    onSetThreadRelief: (id: String, reliefLenMm: Float, reliefDiaMm: Float, reliefAtAft: Boolean) -> Unit,

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
                onClick = onTitleClick,
            )
            TopAppBar(
                // Status-bar inset is consumed by the title strip above; the default TopAppBar
                // insets would pad for it a second time.
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = editorTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    // Hamburger opens the sidebar nav drawer.
                    // Home lives inside the sidebar — not duplicated here.
                    // Where the sidebar is laid out beside the content there is nothing to
                    // open, and a button whose panel is already on screen reads as broken.
                    if (!LocalSidebarPermanent.current) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.testTag("toolbar_menu")
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                        }
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
                            .testTag("toolbar_pdf_preview_container")
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
                        // Opens the PDF preview, where Print and Export both live; it is not
                        // itself an export action.
                        IconButton(
                            onClick = onExportPdf,
                            enabled = exportPdfEnabled,
                            modifier = Modifier.testTag("toolbar_pdf_preview")
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "PDF preview")
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
            // Whose drawing this is — pinned with the bar, so scrolling the editor can
            // never scroll the reminder off.
            banner?.invoke()
            }
        },
    ) { inner ->
        val widthClass = currentWindowWidthClass()
        val previewScroll = rememberScrollState()

        // The editor's content blocks are declared ONCE here and invoked by whichever width
        // branch lays out, so the phone column and the tablet two-pane cannot drift apart.
        // They are composable lambdas rather than private composables because each would
        // otherwise have to restate this screen's parameter list — the carousel alone takes
        // fifty callbacks — and a signature copied twice is the drift this exists to prevent.
        val previewBlock: @Composable (previewMaxHeight: Dp, aspect: Float) -> Unit = { previewMaxHeight, aspect ->
            PreviewCard(
                showGrid = showGrid,
                spec = spec,
                resolvedComponents = resolvedComponents,
                unit = unit,
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
                threadSlant = threadSlant,
                threadDensity = threadDensity,
                shadedComponentIds = shadedComponentIds,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = previewMaxHeight)
                    .aspectRatio(aspect)
            )
            // NOTE: Highlight state is threaded into ShaftDrawing via PreviewCard.
        }

        // Overall length entry and its two read-only companion labels. [fieldMaxWidth] is the
        // text box's width cap — the phone's 240 dp, or none where the field should fill a
        // column the width of the control under it.
        val oalBlock: @Composable (fieldMaxWidth: Dp) -> Unit = { fieldMaxWidth ->
            OverallLengthField(
                spec = spec,
                unit = unit,
                onSetOverallLengthMm = onSetOverallLengthMm,
                onSetOverallLengthRaw = onSetOverallLengthRaw,
                fieldMaxWidth = fieldMaxWidth,
            )

            // Read-only: computed OAL in measurement space (less excluded end threads)
            val win = remember(spec.overallLengthMm, spec.threads, spec.tapers) { computeOalWindow(spec) }
            val physicalOalMm = spec.overallLengthMm.toDouble()
            val effectiveOalWindowMm = win.oalMm
            val excluded = kotlin.math.abs(effectiveOalWindowMm - physicalOalMm) > OAL_EPS_MM

            // Only meaningful when an excluded end thread makes the dimensioned span
            // differ from the physical length.
            if (excluded) {
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
        }

        // The components side comes in three blocks — the heading, the single add entry point,
        // and the carousel with the add dialogs it raises (a dialog takes no layout space, so
        // it rides with the control that opens it in either branch). The single column stacks
        // them through [componentsBlock]; the two-pane branch places them separately, the add
        // button beside the drawing and the carousel across the full width under it.
        val componentsHeader: @Composable (hint: String) -> Unit = { hint ->
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
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val addComponentButton: @Composable () -> Unit = {
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
        }

        // [peekPageWidth] non-null centres the current card at that width with its neighbours
        // peeking at the sides (the tablet carousel); [fillHeight] fills the parent's height
        // instead of the phone's fixed carousel height.
        val carouselBlock: @Composable (peekPageWidth: Dp?, fillHeight: Boolean) -> Unit = { peekPageWidth, fillHeight ->
            ComponentCarouselPager(
                spec = spec,
                resolvedComponents = resolvedComponents,
                unit = unit,
                showEdgeArrows = showComponentArrows,
                edgeArrowWidthDp = componentArrowWidthDp,
                peekPageWidth = peekPageWidth,
                fillHeight = fillHeight,
                showComponentDebugLabels = showComponentDebugLabels,
                componentTitlesDefault = componentTitlesDefault,
                componentShadeDefaults = componentShadeDefaults,
                selectedComponentId = selectedComponentId,
                // Auto-body promotion adds a plain body; keyways and blends are added
                // later via the promoted card's own fields.
                onAddBody = { s, l, d ->
                    onAddBody(
                        s, l, d, 0f, 0f, 0f, 0f, LinerAuthoredReference.AFT, false, null,
                        0f, 0f, BlendProfile.OGEE, false, false, 0f, 0f,
                    )
                },
                onSetAutoSectionDia = onSetAutoSectionDia,
                onSetAllAutoSectionsDia = onSetAllAutoSectionsDia,
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
                onUpdateCouplerBoltSlotStyle = onUpdateCouplerBoltSlotStyle,
                onUpdateCouplerBoltSlotClocking = onUpdateCouplerBoltSlotClocking,
                onUpdateCouplerBoltSlotShowRail = onUpdateCouplerBoltSlotShowRail,
                onSetKeyways180Apart = onSetKeyways180Apart,
                onSetKeyways90Apart = onSetKeyways90Apart,
                onSetKeyways90Cw = onSetKeyways90Cw,

                onSetThreadExcludeFromOal = onSetThreadExcludeFromOal,
                onSetThreadEndPosition = onSetThreadEndPosition,
                onSetThreadRelief = onSetThreadRelief,

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
                        addLengthMm = if (spec.overallLengthMm > d.startMm) {
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
                    initialStartMm = addThreadStartMm,
                    initialLengthMm = sessionAddDefaults.threadLenMm,
                    initialMajorDiaMm = sessionAddDefaults.threadMajorDiaMm,
                    initialPitchMm = sessionAddDefaults.threadPitchMm,
                    dialogUnitConverterEnabled = dialogUnitConverterEnabled,
                    onSubmit = { startMm, lengthMm, majorDiaMm, pitchMm, excludeFromOAL, isAftEnd, metricDesignation,
                                 reliefLenMm, reliefDiaMm, reliefAtAft ->
                        addThreadOpen = false
                        // IMPORTANT: argument order is start, length, majorDia, pitch, excludeFromOAL,
                        // isAftEnd, metricDesignation, then the relief (length, Ø, end). The dialog
                        // already converts TPI to pitch (or reads pitch straight off a metric
                        // designation) so this call never re-derives it. Keep this aligned with
                        // `ShaftRoute`/`ShaftViewModel.addThreadAt`.
                        onAddThread(
                            startMm,
                            lengthMm,
                            majorDiaMm,
                            pitchMm,
                            excludeFromOAL,
                            isAftEnd,
                            metricDesignation,
                            reliefLenMm,
                            reliefDiaMm,
                            reliefAtAft,
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
                    onSubmit = { startMm, holeDiaMm, count, spacingMm, through, depthMm, ref, style, clocking ->
                        addSlotOpen = false
                        onAddCouplerBoltSlot(startMm, holeDiaMm, count, spacingMm, through, depthMm, ref, style, clocking)
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
                    onSubmit = { s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, k180, k90, cw90, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd, bSAftLen, bSFwdLen ->
                        addBodyOpen = false
                        onAddBody(s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd, bSAftLen, bSFwdLen)
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
        }

        // [warningSlot] is emitted between the add button and the carousel — where the spec
        // warnings sit in one column. With two panes they move up beside the drawing and the
        // overall length they are about, and this slot stays empty.
        val componentsBlock: @Composable (warningSlot: @Composable () -> Unit) -> Unit = { warningSlot ->
            componentsHeader("Swipe to select")
            addComponentButton()
            warningSlot()
            carouselBlock(null, false)
        }

        if (projectInfoOpen) {
            // Raised from the toolbar, so it belongs to the screen rather than to either pane.
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

        if (widthClass.twoPane) {
            // A landscape tablet: the shaft is long and thin, so the drawing takes the WIDTH —
            // a band across the top, with the overall length, the add button and the warnings
            // in a column beside it — and the carousel takes the rest of the height, the
            // current card centred with its neighbours peeking at the sides. The band is sized
            // from the drawing and the side column scrolls inside it, so a long warning list
            // can never push the cards down; the carousel never scrolls the drawing away.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                // The side column is the controls' width plus the OAL field's ✓/✗ slot, which
                // stands to the right of the box so the box, the button and the warnings
                // under it share one width and one right edge.
                val sideColumnWidth = EXPANDED_SIDE_COLUMN_WIDTH_DP.dp + OAL_ACTION_SLOT_WIDTH
                val bandHeight = expandedDrawingBandHeightDp(
                    contentWidthDp = maxWidth.value - 2 * EXPANDED_CONTENT_PADDING.value,
                    aspect = EXPANDED_PREVIEW_ASPECT,
                    sideColumnWidthDp = sideColumnWidth.value,
                    floorDp = EXPANDED_PREVIEW_MIN_HEIGHT.value,
                    capDp = EXPANDED_PREVIEW_MAX_HEIGHT.value,
                ).dp
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bandHeight + EXPANDED_BAND_VERTICAL_PADDING * 2)
                            .padding(horizontal = EXPANDED_CONTENT_PADDING, vertical = EXPANDED_BAND_VERTICAL_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(EXPANDED_BAND_GAP_DP.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(EDITOR_PANE_PREVIEW_TAG),
                            contentAlignment = Alignment.Center
                        ) {
                            previewBlock(EXPANDED_PREVIEW_MAX_HEIGHT, EXPANDED_PREVIEW_ASPECT)
                        }
                        Column(
                            modifier = Modifier
                                .width(sideColumnWidth)
                                .fillMaxHeight()
                                .verticalScroll(previewScroll),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // The box fills the controls' width (its ✓/✗ slot takes the rest
                            // of the row) and the button and warnings stop at the same edge, so
                            // the three read as one block (on-device report: a 240 dp box over
                            // a wider button looked unrelated to it).
                            oalBlock(Dp.Unspecified)
                            Box(Modifier.fillMaxWidth().padding(end = OAL_ACTION_SLOT_WIDTH)) {
                                addComponentButton()
                            }
                            Box(Modifier.fillMaxWidth().padding(end = OAL_ACTION_SLOT_WIDTH)) {
                                SpecWarningBanner(spec = spec)
                            }
                        }
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(EDITOR_PANE_COMPONENTS_TAG)
                            .padding(horizontal = EXPANDED_CONTENT_PADDING)
                            // The cards hold the text fields, so the keyboard shrinks this
                            // region and the current card scrolls inside itself.
                            .imePadding()
                            .padding(top = 8.dp, bottom = 16.dp)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        componentsHeader("Tap or swipe to select")
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            // Tablet cards set their paired fields two to a row (`FieldPair`).
                            CompositionLocalProvider(LocalCardFieldsTwoUp provides true) {
                                carouselBlock(EXPANDED_CAROUSEL_PAGE_WIDTH, true)
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp)
            ) {
                // Separator (matches the divider below the preview)
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Preview. A MEDIUM window is wide enough that the phone's cap would draw the
                // shaft as a flat strip, so only the ceiling moves — ratio and floor stand.
                previewBlock(
                    if (widthClass == WindowWidthClass.MEDIUM) MEDIUM_PREVIEW_MAX_HEIGHT
                    else COMPACT_PREVIEW_MAX_HEIGHT,
                    COLUMN_PREVIEW_ASPECT,
                )

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
                    oalBlock(OAL_FIELD_MAX_WIDTH)
                    componentsBlock { SpecWarningBanner(spec = spec) }
                }
            }
        }
    }
    }
}

/** The preview card's height ceiling on a phone — the historical cap. */
private val COMPACT_PREVIEW_MAX_HEIGHT = 200.dp

/** The preview card's height ceiling in a MEDIUM window (portrait tablet, phone landscape). */
private val MEDIUM_PREVIEW_MAX_HEIGHT = 280.dp

/** The preview card's width : height in a single column — the phone's historical 3:1. */
private const val COLUMN_PREVIEW_ASPECT = 3.0f

/**
 * The preview card's width : height in the EXPANDED drawing band. Wider than the column's
 * 3:1 because the band is width-bound: the shaft draws at the same width either way, and a
 * 3:1 box there was mostly blank paper above and below a long, thin shaft — height the cards
 * under it can use.
 */
private const val EXPANDED_PREVIEW_ASPECT = 4.0f

/** The preview card's height ceiling in the EXPANDED drawing band. */
private val EXPANDED_PREVIEW_MAX_HEIGHT = 320.dp

/** The preview card's height floor — the same 120 dp floor every branch's card carries. */
private val EXPANDED_PREVIEW_MIN_HEIGHT = 120.dp

/** The editor's horizontal content padding in the EXPANDED layout, the phone column's 16 dp. */
private val EXPANDED_CONTENT_PADDING = 16.dp

/** Air above and below the drawing band. */
private val EXPANDED_BAND_VERTICAL_PADDING = 8.dp

/** The centred card's width in the EXPANDED carousel; the neighbours peek in the remainder. */
private val EXPANDED_CAROUSEL_PAGE_WIDTH = CAROUSEL_PEEK_PAGE_WIDTH_DP.dp

/** EXPANDED drawing band — the preview card, with the OAL / add / warnings column beside it. */
internal const val EDITOR_PANE_PREVIEW_TAG = "editor_pane_preview"

/** EXPANDED components region — the Components heading and the peeking carousel under the band. */
internal const val EDITOR_PANE_COMPONENTS_TAG = "editor_pane_components"

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


/**
 * The shaft's Overall Length entry field.
 *
 * Three behaviours it owns, each deliberate:
 *  • **Commit on ACCEPT, never per keystroke.** Typing only changes the local text; nothing
 *    reaches [onSetOverallLengthMm] until the value is accepted — the ✓ button, IME Done, or a
 *    blur after the text changed since focus (`shouldCommitOnBlur`, the component cards' rule).
 *    ✓ and ✗ are the explicit accept/cancel pair, shown beside the field only while the value is
 *    being modified; ✗ restores the stored length and commits nothing. A per-keystroke commit
 *    publishes every intermediate value: deleting one digit of 368.5 on the way to 367.75
 *    committed a 36″ shaft, the drawing collapsed, and re-anchoring a FWD-referenced taper
 *    through that transient length left a residue and a "past the shaft end" warning
 *    (on-device report). See `docs/contracts/ShaftScreen.md`.
 *  • **An empty field commits nothing.** Every accept path on empty text restores the stored
 *    length instead of zeroing the shaft, so clearing the field to retype cannot wipe the OAL.
 *    Unparseable text also commits nothing: ✓/Done leave it as typed (the user is mid-edit),
 *    a blur restores the stored length.
 *  • **Oversize is an error STYLE, not a rejection.** A component past the authored length is a
 *    legal, advisory state; the field is only tinted. A not-yet-typed length (0) is not
 *    oversize — the renderer draws to the coverage end until a length is authored.
 *
 * Every accept or cancel moves the focus baseline to the text it leaves behind before clearing
 * focus, so the blur that follows sees no change and cannot commit a second time.
 *
 * The text echoes what the user typed ("150 3/4" stays a fraction) and only re-derives from the
 * model while unfocused and no longer explaining the stored value — an undo, an edit from
 * elsewhere, or a reverted empty commit.
 */
@Composable
internal fun OverallLengthField(
    spec: ShaftSpec,
    unit: UnitSystem,
    onSetOverallLengthMm: (Float) -> Unit,
    onSetOverallLengthRaw: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** The text box's width cap; [Dp.Unspecified] lets it take the row's width beside the ✓/✗ slot. */
    fieldMaxWidth: Dp = OAL_FIELD_MAX_WIDTH,
) {
    var hasLenFocus by remember { mutableStateOf(false) }
    var lenTextOnFocus by remember { mutableStateOf<String?>(null) }

    val displayMm = spec.overallLengthMm
    var lengthText by remember(unit) {
        mutableStateOf(formatDisplay(displayMm, unit))
    }
    LaunchedEffect(displayMm, unit, hasLenFocus) {
        if (!hasLenFocus) {
            val parsed = toMmOrNull(lengthText, unit)
            if (parsed == null || kotlin.math.abs(parsed - displayMm) > 0.01f) {
                lengthText = formatDisplay(displayMm, unit)
            }
        }
    }

    val isOversized = spec.overallLengthMm > 0f &&
        spec.overallLengthMm < lastOccupiedEndMm(spec)

    val focusManager = LocalFocusManager.current

    // Being modified = focused AND the text differs from the on-focus baseline. The cleared
    // "0" ghost and an empty field are the same untouched state.
    val isModified = hasLenFocus &&
        oalGhostNormalized(lengthText) != oalGhostNormalized(lenTextOnFocus)

    // Restores the stored length and ends the edit. The baseline moves to the restored text
    // first, so the blur that clearFocus() triggers sees no change and commits nothing.
    fun cancelEdit() {
        val stored = formatDisplay(spec.overallLengthMm, unit)
        lengthText = stored
        lenTextOnFocus = stored
        focusManager.clearFocus()
    }

    // The ✓ / IME Done accept. Empty text restores the stored length; unparseable text stays
    // as typed with focus kept, since the user is mid-edit. A commit moves the baseline to the
    // committed text before clearing focus so the blur cannot commit it a second time.
    fun acceptEdit() {
        val t = lengthText.trim()
        if (t.isEmpty()) {
            cancelEdit()
            return
        }
        val mm = toMmOrNull(t, unit) ?: return
        onSetOverallLengthMm(mm)
        onSetOverallLengthRaw(t) // keeps the user's display text, fractions included
        lenTextOnFocus = lengthText
        focusManager.clearFocus()
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = lengthText,
            onValueChange = { input -> lengthText = input },
            label = { Text("Overall Length (${abbr(unit)})") },
            singleLine = true,
            enabled = true,
            isError = isOversized,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { acceptEdit() }),
            modifier = Modifier
                .weight(1f, fill = fieldMaxWidth == Dp.Unspecified)
                .widthIn(min = 160.dp, max = fieldMaxWidth)
                .testTag(OAL_FIELD_TAG)
                .onFocusChanged { f ->
                    val wasFocused = hasLenFocus
                    hasLenFocus = f.isFocused

                    if (!wasFocused && f.isFocused) {
                        // Baseline for the commit-on-change rule.
                        lenTextOnFocus = lengthText
                        // A not-yet-set length reads "0"; clear it so the user can
                        // type without a leading zero. Leaving without typing reverts.
                        if (lengthText.trim() == "0") lengthText = ""
                    }

                    if (wasFocused && !f.isFocused) {
                        val baseline = lenTextOnFocus
                        lenTextOnFocus = null

                        // Commit only what changed since focus (BlurCommitPolicy).
                        if (!shouldCommitOnBlur(baseline, lengthText)) return@onFocusChanged

                        val t = lengthText.trim()
                        val mm = if (t.isEmpty()) null else toMmOrNull(t, unit)
                        if (mm == null) {
                            // Empty or unparseable: nothing commits, the stored length returns.
                            lengthText = formatDisplay(spec.overallLengthMm, unit)
                        } else {
                            onSetOverallLengthMm(mm)
                            onSetOverallLengthRaw(t)
                        }
                    }
                }
        )

        // Fixed-size slot so the row does not jump when the pair appears. The buttons cannot
        // take focus: a click that pulled focus off the field would blur-commit before the
        // button's own handler ran.
        Box(
            modifier = Modifier
                .width(OAL_ACTION_SLOT_WIDTH)
                .height(OAL_ACTION_SLOT_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isModified) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { acceptEdit() },
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .testTag(OAL_ACCEPT_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Accept length",
                            tint = OAL_ACCEPT_GREEN,
                        )
                    }
                    IconButton(
                        onClick = { cancelEdit() },
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .testTag(OAL_CANCEL_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel length change",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** The cleared "0" ghost and an empty field both read as the untouched state. */
private fun oalGhostNormalized(text: String?): String? {
    val t = text?.trim() ?: return null
    return if (t == "0") "" else text
}

internal const val OAL_FIELD_TAG = "overall_length_field"
internal const val OAL_ACCEPT_TAG = "oal_accept"
internal const val OAL_CANCEL_TAG = "oal_cancel"

/** Semantic "accept" status colour for the ✓; legible on both themes. */
private val OAL_ACCEPT_GREEN = Color(0xFF2E7D32)

/** Two 48 dp icon buttons side by side. */
/** The OAL text box's width cap in a column — the phone's historical 240 dp. */
private val OAL_FIELD_MAX_WIDTH = 240.dp

private val OAL_ACTION_SLOT_WIDTH = 96.dp
private val OAL_ACTION_SLOT_HEIGHT = 48.dp

private const val OAL_EPS_MM: Double = 1e-3

internal fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) (MM_PER_IN / tpi.toDouble()).toFloat() else 0f
