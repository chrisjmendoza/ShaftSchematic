package com.android.shaftschematic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.movableContentOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
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
import com.android.shaftschematic.ui.input.AcceptCancelButtons
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.ui.util.exportPdfGate
import com.android.shaftschematic.ui.util.specIssues
import com.android.shaftschematic.ui.drawing.render.PreviewRailSettings
import com.android.shaftschematic.ui.drawing.render.PreviewSheetSettings
import com.android.shaftschematic.ui.drawing.render.previewIssueMarks
import com.android.shaftschematic.ui.viewmodel.SessionAddDefaults
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.inchFieldText
import com.android.shaftschematic.util.PreviewColorSetting
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ShaftScreen — Editor surface
 *
 * Responsibilities
 * • Top app bar (title, undo/redo, the More menu) — the document's unit is set in Settings →
 *   Units, not here; there is no header unit selector.
 * • Preview drawing (white square; optional grid; fixed-height band)
 * • Component carousel (edit & remove) — the start card FIRST (overall length, shaft diameter,
 *   project information — `StartCard.kt`, `docs/contracts/OverallLength.md`), then the
 *   components in physical order along the shaft
 * • The card Save / Cancel bar above the carousel, shown only while a card field holds an
 *   unsaved value
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
    /** The sheet's dimension rails over the preview shaft, `null` = off (`PreviewDimensionRails.kt`). */
    previewDimensionRails: PreviewRailSettings? = null,
    /** The preview drawn like the schematic sheet, `null` = the linear picture (`PreviewSheetLayout.kt`). */
    previewSheetLayout: PreviewSheetSettings? = null,
    /** Opens the editor's Preview options sheet (overflow → "Preview options…"). */
    onOpenPreviewOptions: () -> Unit = {},

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
    onSetOverallLengthMm: (Float) -> Unit,
    /** The start card's shaft-wide bare-shaft Ø (mm, verbatim; ≤ 0 clears) — keeps section overrides. */
    onSetShaftDiaMm: (Float) -> Unit,
    onSelectComponentById: (String?) -> Unit,

    // Adds (all mm)
    onAddBody: (startMm: Float, lengthMm: Float, diaMm: Float,
                reference: LinerAuthoredReference,
                keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                keywayOffsetFromEndMm: Float, keywayEnd: LinerAuthoredReference,
                keywaySpooned: Boolean, keywayUnit: UnitSystem?,
                blendAftMm: Float, blendFwdMm: Float, blendProfile: BlendProfile,
                blendAftSeal: Boolean, blendFwdSeal: Boolean,
                blendAftSealLenMm: Float, blendFwdSealLenMm: Float,
                blendAftToLinerOd: Boolean, blendFwdToLinerOd: Boolean,
                blendAngles: BlendAngleDraft) -> Unit,
    onSetAutoSectionDia: (spanStartMm: Float, spanEndMm: Float, diaMm: Float) -> Unit,
    /** Sets the bare-shaft Ø of EVERY auto section together (the card's "All auto sections"). */
    onSetAllAutoSectionsDia: (diaMm: Float) -> Unit,
    onSetAutoBlend: (spanStartMm: Float, spanEndMm: Float, end: LinerAuthoredReference, lengthMm: Float, profile: BlendProfile, seal: Boolean, sealLenMm: Float, toLinerOd: Boolean) -> Unit,
    onSetAutoBlendAngle: (spanStartMm: Float, spanEndMm: Float, end: LinerAuthoredReference, angleDeg: Float?, radiusMm: Float) -> Unit = { _, _, _, _, _ -> },
    onSetShowAutoBodyDia: (Boolean) -> Unit,
    onAddTaper: (startMm: Float, lengthMm: Float, startDiaMm: Float, endDiaMm: Float,
                 rateText: String, reference: LinerAuthoredReference,
                 keywayWidthMm: Float, keywayDepthMm: Float, keywayLengthMm: Float,
                 keywayOffsetFromSetMm: Float, keywaySpooned: Boolean,
                 keywayUnit: UnitSystem?, rateManual: Boolean) -> Unit,
    onAddThread: (startMm: Float, lengthMm: Float, majorDiaMm: Float, pitchMm: Float, excludeFromOAL: Boolean,
                  isAftEnd: Boolean, metricDesignation: String?,
                  reliefLenMm: Float, reliefDiaMm: Float, reliefAtAft: Boolean) -> Unit,
    onAddLiner: (Float, Float, Float, LinerAuthoredReference, LinerShoulderDraft) -> Unit,
    onAddCouplerBoltSlot: (startMm: Float, holeDiaMm: Float, count: Int, spacingMm: Float, through: Boolean, depthMm: Float, reference: SlotAuthoredReference, holeStyle: BoltHoleStyle, clocking: BoltHoleClocking) -> Unit,

    // Updates (all mm)
    onUpdateBody: (Int, Float, Float, Float) -> Unit,
    onUpdateBodyReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateBodyShowDia: (Int, Boolean) -> Unit,
    onUpdateBodyShowLabel: (Int, Boolean) -> Unit,
    onUpdateBodyShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyCompressOnDrawing: (Int, Boolean) -> Unit,
    onUpdateBodyShowDimensionRail: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateBodyBlend: (index: Int, blendAftMm: Float, blendFwdMm: Float, profile: BlendProfile, sealAft: Boolean, sealFwd: Boolean, sealAftLenMm: Float, sealFwdLenMm: Float, toLinerOdAft: Boolean, toLinerOdFwd: Boolean) -> Unit,
    onUpdateBodyBlendAngle: (index: Int, end: LinerAuthoredReference, angleDeg: Float?, radiusMm: Float) -> Unit = { _, _, _, _ -> },
    onUpdateBodyLabel: (Int, String?) -> Unit,
    onUpdateBodyKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromEndMm: Float, end: LinerAuthoredReference, spooned: Boolean) -> Unit,
    onUpdateTaper: (Int, Float, Float, Float, Float, String) -> Unit,
    onUpdateTaperLabel: (Int, String?) -> Unit,
    onUpdateTaperShowLabel: (Int, Boolean) -> Unit,
    onUpdateTaperShade: (Int, Boolean) -> Unit = { _, _ -> },
    onUpdateTaperKeyway: (index: Int, widthMm: Float, depthMm: Float, lengthMm: Float, offsetFromSetMm: Float, spooned: Boolean) -> Unit,
    onUpdateTaperReference: (Int, LinerAuthoredReference) -> Unit,
    onUpdateTaperRateMode: (Int, Boolean, String?) -> Unit = { _, _, _ -> },
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
    onSetThreadExcludeFromOal: (id: String, excludeFromOAL: Boolean, adjustOal: Boolean) -> Unit,
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
    val scroll = rememberScrollState()
    val topBarScope = rememberCoroutineScope()

    val collidingComponentIds = remember(spec) { spec.collidingIds() }
    // The warning banner's problems, drawn on the preview where they are (the banner computes the
    // same `specIssues` list, so the two always mark and name the same parts).
    val issueMarks = remember(spec, unit) { previewIssueMarks(specIssues(spec, unit)) }
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

    val pendingCardEdits = remember { PendingCardEdits() }

    // A tap on a component in the drawing (or on a warning line, which takes the same selection
    // path) changes cards, so while a card field holds an unsaved value it asks first, like a
    // carousel swipe. Plain `remember`: the edit's lambdas are not saveable.
    var unsavedTap by remember { mutableStateOf<UnsavedTap?>(null) }
    val selectFromDrawing: (String?) -> Unit = { id ->
        val pending = pendingCardEdits.current
        if (pending == null || id == selectedComponentId) onSelectComponentById(id)
        else unsavedTap = UnsavedTap(pending, id)
    }
    unsavedTap?.let { tap ->
        UnsavedCardEditDialog(
            edit = tap.edit,
            onSave = {
                unsavedTap = null
                // A refused value stays on its card with its notice: the change is not made.
                if (tap.edit.accept()) onSelectComponentById(tap.targetId)
            },
            onDiscard = {
                unsavedTap = null
                tap.edit.cancel()
                onSelectComponentById(tap.targetId)
            },
            onKeepEditing = { unsavedTap = null },
        )
    }

    CompositionLocalProvider(LocalPendingCardEdits provides pendingCardEdits) {
    Scaffold(
        // The content insets below are horizontal only (each branch pads its own bottom), so
        // the Scaffold places the snackbar on the window's bottom edge; the host pads for the
        // navigation bar itself, or the gesture handle draws across the message (on-device
        // report).
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                ),
            )
        },
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
                    // A COMPACT bar prints no title: the action icons leave it a couple of
                    // characters, so it printed as ".." (on-device report), and the document
                    // title strip above already names the screen.
                    if (currentWindowWidthClass() != WindowWidthClass.COMPACT) {
                        Text(
                            text = editorTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(EDITOR_BAR_TITLE_TAG),
                        )
                    }
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

                    // The project information lives on the start card: this goes there, through
                    // the selection path, so a card holding an unsaved value asks first.
                    IconButton(
                        onClick = { selectFromDrawing(null) },
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
                        onOpenPreviewOptions = onOpenPreviewOptions,
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
        // The MEDIUM column's measurements behind its carousel height, px: the scroll viewport,
        // the whole scrolled content, and the carousel's own share of that content.
        var mediumViewportPx by remember { mutableIntStateOf(0) }
        var mediumContentPx by remember { mutableIntStateOf(0) }
        var mediumCarouselPx by remember { mutableIntStateOf(0) }
        // The EXPANDED side column's content height, px — what the drawing band grows to.
        var expandedSideColumnContentPx by remember { mutableIntStateOf(0) }

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
                onTapComponentId = { selectFromDrawing(it) },
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
                issueMarks = issueMarks,
                dimensionRails = previewDimensionRails,
                sheetLayout = previewSheetLayout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = previewMaxHeight)
                    .aspectRatio(aspect)
            )
            // NOTE: Highlight state is threaded into ShaftDrawing via PreviewCard.
        }

        // The start card — the carousel's first page in every branch: overall length, shaft
        // diameter and project information, bound to this editor's drawing.
        val startCard: @Composable (outerPaddingHorizontal: Dp) -> Unit = { outerPaddingHorizontal ->
            ShaftStartCard(
                spec = spec,
                unit = unit,
                customer = customer,
                vessel = vessel,
                jobNumber = jobNumber,
                item = item,
                shaftPosition = shaftPosition,
                notes = notes,
                onSetOverallLengthMm = onSetOverallLengthMm,
                onSetShaftDiaMm = onSetShaftDiaMm,
                onSetCustomer = onSetCustomer,
                onSetVessel = onSetVessel,
                onSetJobNumber = onSetJobNumber,
                onSetItem = onSetItem,
                onSetShaftPosition = onSetShaftPosition,
                onSetNotes = onSetNotes,
                showOalDebugLabel = showOalDebugLabel,
                outerPaddingHorizontal = outerPaddingHorizontal,
            )
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
        val carouselContent: @Composable (peekPageWidth: Dp?, fillHeight: Boolean) -> Unit = { peekPageWidth, fillHeight ->
            ComponentCarouselPager(
                spec = spec,
                resolvedComponents = resolvedComponents,
                unit = unit,
                showEdgeArrows = showComponentArrows,
                edgeArrowWidthDp = componentArrowWidthDp,
                peekPageWidth = peekPageWidth,
                fillHeight = fillHeight,
                startCard = startCard,
                showComponentDebugLabels = showComponentDebugLabels,
                componentTitlesDefault = componentTitlesDefault,
                componentShadeDefaults = componentShadeDefaults,
                selectedComponentId = selectedComponentId,
                // Auto-body promotion adds a plain body; keyways and blends are added
                // later via the promoted card's own fields.
                onAddBody = { s, l, d ->
                    onAddBody(
                        s, l, d, LinerAuthoredReference.AFT, 0f, 0f, 0f, 0f, LinerAuthoredReference.AFT, false, null,
                        0f, 0f, BlendProfile.OGEE, false, false, 0f, 0f, false, false, BlendAngleDraft(),
                    )
                },
                onSetAutoSectionDia = onSetAutoSectionDia,
                onSetAllAutoSectionsDia = onSetAllAutoSectionsDia,
                onSetAutoBlend = onSetAutoBlend,
                onSetAutoBlendAngle = onSetAutoBlendAngle,
                onSetShowAutoBodyDia = onSetShowAutoBodyDia,
                onUpdateBody = onUpdateBody,
                onUpdateBodyReference = onUpdateBodyReference,
                onUpdateBodyShowDia = onUpdateBodyShowDia,
                onUpdateBodyShowLabel = onUpdateBodyShowLabel,
                onUpdateBodyShade = onUpdateBodyShade,
                onUpdateBodyCompressOnDrawing = onUpdateBodyCompressOnDrawing,
                onUpdateBodyShowDimensionRail = onUpdateBodyShowDimensionRail,
                onUpdateBodyBlend = onUpdateBodyBlend,
                onUpdateBodyBlendAngle = onUpdateBodyBlendAngle,
                onUpdateBodyLabel = onUpdateBodyLabel,
                onUpdateBodyKeyway = onUpdateBodyKeyway,
                onUpdateTaper = onUpdateTaper,
                onUpdateTaperLabel = onUpdateTaperLabel,
                onUpdateTaperShowLabel = onUpdateTaperShowLabel,
                onUpdateTaperShade = onUpdateTaperShade,
                onUpdateTaperKeyway = onUpdateTaperKeyway,
                onUpdateTaperReference = onUpdateTaperReference,
                onUpdateTaperRateMode = onUpdateTaperRateMode,
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
                        // A Start moved onto a bare-shaft span opens sized to that span; a
                        // body to the OAL from there would run over the components FWD of it.
                        addLengthMm = when {
                            d.bareSpanEndMm != null -> d.bareSpanEndMm - d.startMm
                            spec.overallLengthMm > d.startMm -> spec.overallLengthMm - d.startMm
                            else -> sessionAddDefaults.bodyLenMm
                        }
                        addBodyOpen = true
                    },
                    onAddLiner = {
                        chooserOpen = false
                        // Seeded where the dialog's own length FITS, so it does not open on an
                        // overlap while free shaft exists (addStartSeed).
                        addStartMm = addStartSeed(spec, sessionAddDefaults.linerLenMm).startMm
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
                        addStartMm = addStartSeed(spec, sessionAddDefaults.taperLenMm).startMm
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
                    onSubmit = { s, l, d, ref, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, k180, k90, cw90, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd, bSAftLen, bSFwdLen, bOdAft, bOdFwd, bAngles ->
                        addBodyOpen = false
                        onAddBody(s, l, d, ref, kwW, kwD, kwL, kwO, kwEnd, kwSpooned, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd, bSAftLen, bSFwdLen, bOdAft, bOdFwd, bAngles)
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
                    onSubmit = { s, l, startDia, endDia, rate, ref, kwW, kwD, kwL, kwO, kwSpooned, k180, k90, cw90, kwUnit, rateManual ->
                        addTaperOpen = false
                        onAddTaper(s, l, startDia, endDia, rate, ref, kwW, kwD, kwL, kwO, kwSpooned, kwUnit, rateManual)
                        onSetKeyways180Apart(k180)
                        onSetKeyways90Apart(k90)
                        if (k90) onSetKeyways90Cw(cw90)
                    },
                    onCancel = { addTaperOpen = false }
                )
            }
        }

        // The carousel (with the add dialogs it raises) and the warning banner are MOVABLE
        // content, because a rotation across the EXPANDED boundary moves them between the two
        // width branches. `rememberSaveable` keys its values by composition path, and movable
        // content roots that path at itself rather than at the branch hosting it — so a card's
        // typed text and scroll, an add dialog's fields and a dismissed warning keep their saved
        // state across the move instead of resetting, and no stale copy is saved again to come
        // back on a later rotation.
        // Rooting the path at the movable content also takes it OUT of the editor's
        // `key(resetNonce)`, so each block re-enters that key inside itself: without it a value
        // saved while the editor was off screen (the Open screen) came back into the next
        // document — the start card opened scrolled to wherever the last document's card had
        // been, its title cut off (on-device QA).
        val latestCarouselContent by rememberUpdatedState(carouselContent)
        val carouselBlock: @Composable (peekPageWidth: Dp?, fillHeight: Boolean) -> Unit = remember {
            movableContentOf { peekPageWidth: Dp?, fillHeight: Boolean ->
                key(resetNonce) { latestCarouselContent(peekPageWidth, fillHeight) }
            }
        }
        val latestSpec by rememberUpdatedState(spec)
        val latestUnit by rememberUpdatedState(unit)
        val latestSelectComponent by rememberUpdatedState(selectFromDrawing)
        val warningBanner: @Composable () -> Unit = remember {
            movableContentOf {
                key(resetNonce) {
                    // A line's tap takes the preview's selection path, so the carousel follows it.
                    SpecWarningBanner(
                        spec = latestSpec,
                        unit = latestUnit,
                        onSelectComponent = { id -> latestSelectComponent(id) },
                    )
                }
            }
        }

        // [warningSlot] is emitted between the add button and the carousel — where the spec
        // warnings sit in one column. With two panes they move up beside the drawing and the
        // overall length they are about, and this slot stays empty. [carouselHeight] null is the
        // phone's fixed-height carousel; a height fills a box that tall (a MEDIUM window).
        val componentsBlock: @Composable (warningSlot: @Composable () -> Unit, carouselHeight: Dp?) -> Unit = { warningSlot, carouselHeight ->
            componentsHeader("Swipe to select")
            addComponentButton()
            warningSlot()
            if (carouselHeight == null) {
                carouselBlock(null, false)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(carouselHeight)
                        .onSizeChanged { mediumCarouselPx = it.height }
                        .testTag(EDITOR_MEDIUM_CAROUSEL_TAG)
                ) {
                    carouselBlock(null, true)
                }
            }
        }

        if (widthClass.twoPane) {
            // A landscape tablet: the shaft is long and thin, so the drawing takes the WIDTH —
            // a band across the top, with the card Save bar (while a field holds an unsaved
            // value), the add button and the warnings in a column beside it — and the carousel
            // takes the rest of the height, the
            // current card centred with its neighbours peeking at the sides. The band is as
            // tall as the taller of the drawing and the side column, up to the preview's cap;
            // past the cap the side column scrolls inside the band (a fade at its foot says
            // so), so a long warning list can never push the cards below the cap. With the
            // keyboard up the band yields height so the focused card keeps its field in view;
            // the carousel never scrolls the drawing away.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
                // The side column is the controls' width plus the Save bar's ✓/✗ slot, which
                // stands to the right of the bar's text.
                val sideColumnWidth = EXPANDED_SIDE_COLUMN_WIDTH_DP.dp + SAVE_BAR_ACTION_SLOT_WIDTH
                val density = LocalDensity.current
                val drawingBand = expandedDrawingBandHeightDp(
                    contentWidthDp = maxWidth.value - 2 * EXPANDED_CONTENT_PADDING.value,
                    aspect = EXPANDED_PREVIEW_ASPECT,
                    sideColumnWidthDp = sideColumnWidth.value,
                    floorDp = EXPANDED_PREVIEW_MIN_HEIGHT.value,
                    capDp = EXPANDED_PREVIEW_MAX_HEIGHT.value,
                )
                val sideColumnContentDp = with(density) { expandedSideColumnContentPx.toDp() }.value
                val bandPadding = EXPANDED_BAND_VERTICAL_PADDING.value * 2
                val bandOuter = expandedBandHeightUnderImeDp(
                    bandDp = expandedBandHeightDp(
                        drawingDp = drawingBand,
                        sideColumnDp = sideColumnContentDp,
                        capDp = EXPANDED_PREVIEW_MAX_HEIGHT.value,
                    ) + bandPadding,
                    // The two dividers framing the band take their share of the viewport.
                    viewportDp = maxHeight.value - 2 * EXPANDED_BAND_DIVIDER_DP,
                    imeDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }.value,
                    carouselMinDp = EXPANDED_CAROUSEL_MIN_HEIGHT_DP,
                    bandMinDp = EXPANDED_PREVIEW_MIN_HEIGHT.value + bandPadding,
                )
                val bandInner = (bandOuter - bandPadding).coerceAtLeast(0f)
                val fadeColor = MaterialTheme.colorScheme.background
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bandOuter.dp)
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
                            // The preview fills its width at a fixed shape; held to the width
                            // that shape takes at the band's height, a band shortened for the
                            // keyboard draws a smaller shaft rather than a squashed box.
                            Box(
                                Modifier.widthIn(
                                    max = expandedPreviewMaxWidthDp(
                                        bandInnerDp = bandInner,
                                        aspect = EXPANDED_PREVIEW_ASPECT,
                                        capDp = EXPANDED_PREVIEW_MAX_HEIGHT.value,
                                    ).dp
                                )
                            ) {
                                previewBlock(EXPANDED_PREVIEW_MAX_HEIGHT, EXPANDED_PREVIEW_ASPECT)
                            }
                        }
                        Column(
                            modifier = Modifier
                                .width(sideColumnWidth)
                                .fillMaxHeight()
                                .drawWithContent {
                                    drawContent()
                                    if (previewScroll.canScrollForward) {
                                        val fade = EXPANDED_SIDE_COLUMN_FADE_DP.dp.toPx()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                                                startY = size.height - fade,
                                                endY = size.height,
                                            ),
                                            topLeft = Offset(0f, size.height - fade),
                                            size = Size(size.width, fade),
                                        )
                                    }
                                }
                                .verticalScroll(previewScroll)
                                // Measured past the scroll's minimum height, so the band can
                                // read the column's own content height and grow to it.
                                .wrapContentHeight(Alignment.Top)
                                .onSizeChanged { expandedSideColumnContentPx = it.height },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Nothing while every card field is settled; the bar while one
                            // holds an unsaved value, beside the drawing and above the cards.
                            CardSaveSlot()
                            addComponentButton()
                            warningBanner()
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

                // A MEDIUM window is a taller column, and the phone's fixed carousel height left
                // the card clipped above an empty quarter of the screen (on-device report): there
                // the carousel takes whatever height the scrolling column has left under the
                // controls above it, measured off this column (`mediumCarouselHeight`). The
                // viewport is read OUTSIDE the IME padding, so the keyboard scrolls the column
                // rather than shrinking the card under the focused field.
                val medium = widthClass == WindowWidthClass.MEDIUM
                val mediumCarouselDp = if (
                    medium && mediumViewportPx > 0 && mediumContentPx > 0 && mediumCarouselPx > 0
                ) {
                    with(LocalDensity.current) {
                        mediumCarouselHeight(
                            viewportHeight = mediumViewportPx.toFloat(),
                            contentHeight = mediumContentPx.toFloat(),
                            carouselHeight = mediumCarouselPx.toFloat(),
                            floor = CAROUSEL_HEIGHT.toPx(),
                        ).toDp()
                    }
                } else {
                    CAROUSEL_HEIGHT
                }

                // The card Save / Cancel bar stands ABOVE the scrolling content, so it is on screen
                // whatever the column has scrolled to while a field holds an unsaved value
                // (on-device report: inside the column the bar scrolled above the keyboard together
                // with the focused field, leaving Save no more visible than the button it
                // replaced). With every field settled the slot takes no room at all.
                CardSaveSlot(Modifier.padding(bottom = 12.dp))

                // Scrollable editor content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (medium) Modifier.onSizeChanged { mediumViewportPx = it.height } else Modifier)
                        .imePadding()
                        .verticalScroll(scroll)
                        // The scroll keeps the viewport's minimum height on its content, so the
                        // content is measured past a relaxed minimum or it never reads shorter
                        // than the viewport — the case the fill exists for.
                        .then(
                            if (medium) {
                                Modifier
                                    .wrapContentHeight(Alignment.Top)
                                    .onSizeChanged { mediumContentPx = it.height }
                            } else {
                                Modifier
                            }
                        )
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    componentsBlock({ warningBanner() }, if (medium) mediumCarouselDp else null)
                }
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

/** Thickness of each `HorizontalDivider` framing the drawing band, dp (Material's 1 dp). */
private const val EXPANDED_BAND_DIVIDER_DP = 1f

/** The centred card's width in the EXPANDED carousel; the neighbours peek in the remainder. */
private val EXPANDED_CAROUSEL_PAGE_WIDTH = CAROUSEL_PEEK_PAGE_WIDTH_DP.dp

/** EXPANDED drawing band — the preview card, with the OAL / add / warnings column beside it. */
internal const val EDITOR_PANE_PREVIEW_TAG = "editor_pane_preview"

/** EXPANDED components region — the Components heading and the peeking carousel under the band. */
internal const val EDITOR_PANE_COMPONENTS_TAG = "editor_pane_components"

/** MEDIUM components column — the box the carousel fills, sized to the column's leftover height. */
internal const val EDITOR_MEDIUM_CAROUSEL_TAG = "editor_medium_carousel"

/** The action bar's screen title ("Shaft Editor") — composed on MEDIUM and EXPANDED only. */
internal const val EDITOR_BAR_TITLE_TAG = "editor_bar_title"

/**
 * The History (Undo / Redo) action over the ONE document history. Shared with the Final tab's
 * empty state, so a discarded final drawing can be undone where it was discarded.
 */
@Composable
internal fun HistoryMenu(
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
    onOpenPreviewOptions: () -> Unit = {},
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
            // The preview box's own switches and the shared look settings it reads, one tap from
            // the drawing they change (on-device request). On the Schematic and Final tabs alike.
            DropdownMenuItem(
                text = { Text("Preview options…") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.testTag("overflow_preview_options"),
                onClick = {
                    expanded = false
                    onOpenPreviewOptions()
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

/* ───────────────── Helpers: units, parsing, badge math, defaults ───────────────── */

internal fun abbr(unit: UnitSystem) = if (unit == UnitSystem.MILLIMETERS) "mm" else "in"

internal fun formatDisplay(valueMm: Float, unit: UnitSystem, d: Int = 3): String {
    // Inches read through the ONE inch-field formatter: a value on the 1/32 grid shows as the
    // fraction it was typed as (or its exact decimal under Settings → Units "Inch fields show:
    // Decimals"), never as a rounded decimal to convert back by eye (on-device report). Off the
    // grid, at least 4 decimals.
    if (unit == UnitSystem.INCHES) {
        return inchFieldText(valueMm.toDouble() / MM_PER_IN, decimalPlaces = maxOf(d, 4))
    }

    // Locale.US whatever the device locale: this text seeds the number fields, and a comma-decimal
    // locale's `12,0000` trims to `12,`, which the parser refuses.
    // A value that rounds to zero at this precision prints "0", never "-0": a FWD-referenced
    // component flush with the FWD end recovers its distance as a negative zero or a hair
    // below it, and "-0" in a Start field reads as a negative position (on-device report).
    val text = "%.${d}f"
        .format(Locale.US, valueMm)
        .trimEnd('0')
        .trimEnd('.')
        .ifEmpty { "0" }
    return if (text == "-0") "0" else text
}

/** Convenience wrapper for "mm → display" in the component cards. */
internal fun disp(mm: Float, unit: UnitSystem, d: Int = 3): String =
    formatDisplay(mm, unit, d)


/** The system font scale above which the OAL field's label shortens to "OAL (in)". */
internal const val OAL_SHORT_LABEL_FONT_SCALE = 1.3f

/** The OAL field's label: "Overall Length (in)", or "OAL (in)" at a large system font. */
internal fun oalFieldLabel(unit: UnitSystem, fontScale: Float): String =
    if (fontScale > OAL_SHORT_LABEL_FONT_SCALE) "OAL (${abbr(unit)})"
    else "Overall Length (${abbr(unit)})"

/**
 * The card Save / Cancel bar's slot above the carousel: NOTHING while every card field is
 * settled, and the bar while one holds an unsaved value (on-device ruling: a Save button at the
 * end of a long card was hidden; this slot is always on screen above the cards). [modifier]
 * applies to the bar only, so spacing passed here goes with it.
 *
 * The bar grows in and shrinks out ([SAVE_BAR_ANIM_MS], height and fade together), so the cards
 * and the drawing band below it slide rather than jump the moment a field turns dirty (on-device
 * request). Reserving the height instead would hold room open over every settled card — the
 * standing row the start card replaced — and an overlay would cover the top of the scrolled card,
 * where the focused field often sits. While it shrinks out the bar still shows the edit it held,
 * and its buttons do nothing: that edit has already been answered.
 */
@Composable
internal fun CardSaveSlot(modifier: Modifier = Modifier) {
    val pending = LocalPendingCardEdits.current?.current
    // The edit the bar last showed — what it keeps showing while it animates out. A plain holder,
    // not state: it is only ever read in the composition that wrote it.
    val lastShown = remember { arrayOfNulls<PendingCardEdit>(1) }
    if (pending != null) lastShown[0] = pending
    val shown = pending ?: lastShown[0]
    AnimatedVisibility(
        visible = pending != null,
        enter = expandVertically(tween(SAVE_BAR_ANIM_MS), expandFrom = Alignment.Top) +
            fadeIn(tween(SAVE_BAR_ANIM_MS)),
        exit = shrinkVertically(tween(SAVE_BAR_ANIM_MS), shrinkTowards = Alignment.Top) +
            fadeOut(tween(SAVE_BAR_ANIM_MS)),
    ) {
        if (shown != null) CardSaveBar(shown, live = pending != null, modifier = modifier)
    }
}

/**
 * The card Save / Cancel bar: "Unsaved change" over the waiting field and its card, and the green
 * ✓ / red ✗ in a fixed action slot at its end. ✓ and ✗ are the field's own answers
 * ([PendingCardEdit.accept] / [PendingCardEdit.cancel] — the IME Done commit and the revert to the
 * stored value), never a second commit pipeline.
 */
@Composable
private fun CardSaveBar(edit: PendingCardEdit, live: Boolean, modifier: Modifier) {
    val field = fieldLabelWithoutUnit(edit.fieldLabel)
    Row(
        modifier = modifier.fillMaxWidth().testTag(CARD_SAVE_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = SAVE_BAR_TEXT_MIN_HEIGHT),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Unsaved change",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                pendingEditName(edit),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(SAVE_BAR_ACTION_SLOT_WIDTH)
                .height(SAVE_BAR_ACTION_SLOT_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            AcceptCancelButtons(
                onAccept = { if (live) edit.accept() },
                onCancel = { if (live) edit.cancel() },
                acceptDescription = "Save $field",
                cancelDescription = "Cancel $field change",
                acceptTag = CARD_SAVE_TAG,
                cancelTag = CARD_CANCEL_TAG,
            )
        }
    }
}

/** The start card's Overall Length field — the tag the OAL row's field carried, kept for tests. */
internal const val OAL_FIELD_TAG = "overall_length_field"
internal const val CARD_SAVE_BAR_TAG = "card_save_bar"
internal const val CARD_SAVE_TAG = "card_save"
internal const val CARD_CANCEL_TAG = "card_cancel"

/** A tap on the drawing waiting on the "Save changes?" answer: the held edit and the tapped component. */
private data class UnsavedTap(val edit: PendingCardEdit, val targetId: String?)

/** How long the Save bar takes to grow in or shrink out, ms — a slide, not a jump. */
private const val SAVE_BAR_ANIM_MS = 180

/** The Save bar's text block height: the 56 dp of a text field plus the 8 dp its floating label stands above it. */
private val SAVE_BAR_TEXT_MIN_HEIGHT = 64.dp

/** The Save bar's ✓ / ✗ slot — two 48 dp icon buttons side by side. */
private val SAVE_BAR_ACTION_SLOT_WIDTH = 96.dp
private val SAVE_BAR_ACTION_SLOT_HEIGHT = 48.dp

internal fun tpiToPitchMm(tpi: Float): Float = if (tpi > 0f) (MM_PER_IN / tpi.toDouble()).toFloat() else 0f
