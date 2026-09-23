// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftRoute.kt
package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import com.android.shaftschematic.model.BlendProfile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.resolved.shadedComponentIds
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.SpecTarget
import com.android.shaftschematic.ui.viewmodel.UiEvent
import com.android.shaftschematic.ui.viewmodel.addBodyAt
import com.android.shaftschematic.ui.viewmodel.addCouplerBoltSlotAt
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.addTaperAt
import com.android.shaftschematic.ui.viewmodel.addThreadAt
import com.android.shaftschematic.ui.viewmodel.removeBody
import com.android.shaftschematic.ui.viewmodel.removeCouplerBoltSlot
import com.android.shaftschematic.ui.viewmodel.removeLiner
import com.android.shaftschematic.ui.viewmodel.removeTaper
import com.android.shaftschematic.ui.viewmodel.removeThread
import com.android.shaftschematic.ui.viewmodel.setAutoBlend
import com.android.shaftschematic.ui.viewmodel.setKeywayUnit
import com.android.shaftschematic.ui.viewmodel.setKeyways180Apart
import com.android.shaftschematic.ui.viewmodel.setKeyways90Apart
import com.android.shaftschematic.ui.viewmodel.setKeyways90Cw
import com.android.shaftschematic.ui.viewmodel.setShowAutoBodyDia
import com.android.shaftschematic.ui.viewmodel.setThreadEndPosition
import com.android.shaftschematic.ui.viewmodel.setThreadExcludeFromOal
import com.android.shaftschematic.ui.viewmodel.updateBody
import com.android.shaftschematic.ui.viewmodel.updateBodyBlend
import com.android.shaftschematic.ui.viewmodel.updateBodyKeyway
import com.android.shaftschematic.ui.viewmodel.updateBodyLabel
import com.android.shaftschematic.ui.viewmodel.updateBodyCompressOnDrawing
import com.android.shaftschematic.ui.viewmodel.updateBodyShowDia
import com.android.shaftschematic.ui.viewmodel.updateBodyShade
import com.android.shaftschematic.ui.viewmodel.updateBodyShowLabel
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlot
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlotReference
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlotShowRail
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlotClocking
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlotStyle
import com.android.shaftschematic.ui.viewmodel.updateLiner
import com.android.shaftschematic.ui.viewmodel.updateLinerAuthoredReference
import com.android.shaftschematic.ui.viewmodel.updateLinerLabel
import com.android.shaftschematic.ui.viewmodel.updateLinerShoulder
import com.android.shaftschematic.ui.viewmodel.updateLinerShowDia
import com.android.shaftschematic.ui.viewmodel.updateLinerShade
import com.android.shaftschematic.ui.viewmodel.updateLinerShowLabel
import com.android.shaftschematic.ui.viewmodel.updateTaper
import com.android.shaftschematic.ui.viewmodel.updateTaperAuthoredReference
import com.android.shaftschematic.ui.viewmodel.updateTaperKeyway
import com.android.shaftschematic.ui.viewmodel.updateTaperLabel
import com.android.shaftschematic.ui.viewmodel.updateTaperShade
import com.android.shaftschematic.ui.viewmodel.updateTaperShowLabel
import com.android.shaftschematic.ui.viewmodel.updateThread
import com.android.shaftschematic.ui.viewmodel.updateThreadLabel
import com.android.shaftschematic.ui.viewmodel.updateThreadShowLabel
import com.android.shaftschematic.util.FeedbackIntentFactory
import kotlinx.coroutines.launch

/**
 * ShaftRoute
 *
 * Purpose
 * Bind [ShaftViewModel] state to [ShaftScreen] and host the Snackbar state.
 *
 * Contract
 * - No I/O or PDF. Pure binding layer.
 * - Model stays mm; UI converts only for display/input.
 * - ONE editor, two geometries: [target] names which of the document's specs this instance
 *   reads and writes. The Schematic tab binds the original, the Final Schematic tab the
 *   final, and every callback below carries the target so an edit can only reach the drawing
 *   its tab named. Nothing here reads a "current target" from the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftRoute(
    vm: ShaftViewModel,
    /**
     * Which of the document's two drawings this editor edits. [SpecTarget.FINAL] is only ever
     * passed by `FinalRoute`, and only while a final drawing exists — a FINAL write with none
     * is a ViewModel no-op, so a stray one cannot conjure a second geometry.
     */
    target: SpecTarget = SpecTarget.ORIGINAL,
    /** Top-bar title, so the Final tab names its own drawing. */
    editorTitle: String = "Shaft Editor",
    /** Strip under the top bar — the Final tab's standing reminder and its own actions. */
    banner: (@Composable () -> Unit)? = null,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit = {},
    /** Tap on the document title strip — names an unsaved document, renames a saved one. */
    onTitleClick: (() -> Unit)? = null,
    /** Open "Duplicate for mate" — writes a sibling document; the session is untouched. */
    onDuplicateForMate: () -> Unit = {},
    /** Close the current document (guarded for unsaved work) and return to Start. */
    onCloseDocument: () -> Unit = {},
    onExportPdf: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    /** Opens the sidebar nav drawer — wired to the toolbar hamburger button. */
    onOpenSidebar: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    // Collect one-shot UI events from the ViewModel (snackbars, Undo, etc.).
    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbarMessage -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short,
                    )
                }
                is UiEvent.ShowDeletedSnack -> {
                    val label = when (event.kind) {
                        ComponentKind.BODY   -> "Body"
                        ComponentKind.TAPER  -> "Taper"
                        ComponentKind.THREAD -> "Thread"
                        ComponentKind.LINER  -> "Liner"
                        ComponentKind.COUPLER_BOLT_SLOT -> "Coupler bolt slot"
                    }

                    val result = snackbarHostState.showSnackbar(
                        message = "$label deleted",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long // ~5s-ish, depends on platform
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoEdit()
                    }
                }
            }
        }
    }


    // The geometry this editor is bound to. `FinalRoute` only renders with a final drawing
    // present, so the blank fallback is unreachable there; it exists so a null flow can never
    // crash a composition mid-discard.
    val originalSpec    by vm.spec.collectAsState()
    val finalSpec       by vm.finalSpec.collectAsState()
    val spec = when (target) {
        SpecTarget.ORIGINAL -> originalSpec
        SpecTarget.FINAL -> finalSpec ?: ShaftSpec()
    }
    val unit            by vm.unit.collectAsState()
    val showGrid        by vm.showGrid.collectAsState()
    val previewBlackWhiteOnly by vm.previewBlackWhiteOnly.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val previewOutline by vm.previewOutlineSetting.collectAsState()
    val previewBodyFill by vm.previewBodyFillSetting.collectAsState()
    val previewTaperFill by vm.previewTaperFillSetting.collectAsState()
    val previewLinerFill by vm.previewLinerFillSetting.collectAsState()
    val previewThreadFill by vm.previewThreadFillSetting.collectAsState()
    val previewThreadHatch by vm.previewThreadHatchSetting.collectAsState()
    val showComponentArrows by vm.showComponentArrows.collectAsState()
    val componentArrowWidthDp by vm.componentArrowWidthDp.collectAsState()
    val showHighlightSelection by vm.showHighlightSelection.collectAsState()
    // Every debug overlay is ANDed with the master switch HERE, once, so turning Developer
    // Options off clears the drawing immediately. The stored sub-flags are cleared on the next
    // start (`resetDevSubFlagsIfDisabled`), which is too late to be the only gate: without this
    // an overlay left on survives in the current session on a screen that can no longer reach
    // the switch that turns it off.
    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()
    val showOalDebugLabel by vm.showOalDebugLabel.collectAsState()
    val showOalInPreviewBox by vm.showOalInPreviewBox.collectAsState()
    val customer        by vm.customer.collectAsState()
    val vessel          by vm.vessel.collectAsState()
    val jobNumber       by vm.jobNumber.collectAsState()
    val item            by vm.item.collectAsState()
    val shaftPosition   by vm.shaftPosition.collectAsState()
    val notes           by vm.notes.collectAsState()
    val originalResolved by vm.resolvedComponents.collectAsState()
    val finalResolved by vm.finalResolvedComponents.collectAsState()
    val resolvedComponents = when (target) {
        SpecTarget.ORIGINAL -> originalResolved
        SpecTarget.FINAL -> finalResolved
    }
    val selectedComponentId by vm.selectedComponentId.collectAsState()

    val showComponentDebugLabels by vm.showComponentDebugLabels.collectAsState()
    val showRenderLayoutDebugOverlay by vm.showRenderLayoutDebugOverlay.collectAsState()
    val showRenderOalMarkers by vm.showRenderOalMarkers.collectAsState()
    val showDimDebugOverlay by vm.showDimDebugOverlay.collectAsState()
    val pdfTieringMode by vm.pdfTieringMode.collectAsState()
    val pdfShowComponentTitles by vm.pdfShowComponentTitles.collectAsState()
    // The kind-level shade checkboxes, as the DEFAULT each card's unset per-component shade
    // toggle displays. `shadeExplicitBodiesOnly` is deliberately absent: it narrows AUTO runs
    // only, and every card carrying that toggle is an explicit component.
    val pdfShadedBodies by vm.pdfShadedBodies.collectAsState()
    val pdfShadedTapers by vm.pdfShadedTapers.collectAsState()
    val pdfShadedLiners by vm.pdfShadedLiners.collectAsState()
    val pdfShadeExplicitBodiesOnly by vm.pdfShadeExplicitBodiesOnly.collectAsState()
    // PDF-shade mirror for the preview box: the same effective decision the composers make,
    // so the box shows what will print shaded the moment a card toggle or checkbox changes.
    val previewShadedIds = remember(
        spec, resolvedComponents,
        pdfShadedBodies, pdfShadedTapers, pdfShadedLiners, pdfShadeExplicitBodiesOnly,
    ) {
        shadedComponentIds(
            spec, resolvedComponents,
            shadedBodies = pdfShadedBodies,
            shadedTapers = pdfShadedTapers,
            shadedLiners = pdfShadedLiners,
            shadeExplicitBodiesOnly = pdfShadeExplicitBodiesOnly,
        )
    }

    val editorResetNonce by vm.editorResetNonce.collectAsState()

    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()

    val currentDocumentName by vm.currentDocumentName.collectAsState()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsState()

    val sessionAddDefaults by vm.sessionAddDefaults.collectAsState()

    // Mixed per-component units: the capability flag lives in Settings (device-wide), the
    // overrides map is per-document ViewModel state (same posture as `unit` itself).
    val perComponentUnitsEnabled by SettingsStore.perComponentUnitsFlow(ctx).collectAsState(initial = false)
    val unitOverrides by vm.unitOverrides.collectAsState()
    // Liner shoulders: same capability posture — the gate hides the authoring UI only;
    // a liner already carrying shoulders keeps its controls (decided in the carousel).
    val linerShouldersEnabled by SettingsStore.linerShouldersEnabledFlow(ctx).collectAsState(initial = false)
    // Add-dialog unit converter icon: gates only the title-row launcher on the five Add
    // dialogs. The sidebar Tools entry is unconditional and reads nothing from this flag.
    val dialogUnitConverterEnabled by SettingsStore.dialogUnitConverterEnabledFlow(ctx).collectAsState(initial = false)

    val onSendFeedback: () -> Unit = {
        val intent = FeedbackIntentFactory.create(
            context = ctx,
            screen = "Editor",
            unit = unit,
            selectedSaveName = null,
            attachments = emptyList()
        )
        try {
            ctx.startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("No email app found.") }
        }
    }

    ShaftScreen(
        resetNonce = editorResetNonce,
        editorTitle = editorTitle,
        banner = banner,
        spec = spec,
        documentName = currentDocumentName,
        hasUnsavedChanges = hasUnsavedChanges,
        onTitleClick = onTitleClick,
        resolvedComponents = resolvedComponents,
        unit = unit,
        customer = customer,
        vessel = vessel,
        jobNumber = jobNumber,
        item = item,
        shaftPosition = shaftPosition,
        notes = notes,
        showGrid = showGrid,
        showOalDebugLabel = devOptionsEnabled && showOalDebugLabel,
        showOalInPreviewBox = devOptionsEnabled && showOalInPreviewBox,
        showComponentDebugLabels = devOptionsEnabled && showComponentDebugLabels,
        showRenderLayoutDebugOverlay = devOptionsEnabled && showRenderLayoutDebugOverlay,
        showRenderOalMarkers = devOptionsEnabled && showRenderOalMarkers,
        showDimDebugOverlay = devOptionsEnabled && showDimDebugOverlay,
        pdfTieringMode = pdfTieringMode,
        componentTitlesDefault = pdfShowComponentTitles,
        shadedComponentIds = previewShadedIds,
        componentShadeDefaults = ComponentShadeDefaults(
            bodies = pdfShadedBodies, tapers = pdfShadedTapers, liners = pdfShadedLiners,
        ),
        showComponentArrows = showComponentArrows,
        componentArrowWidthDp = componentArrowWidthDp,
        showHighlightSelection = showHighlightSelection,
        selectedComponentId = selectedComponentId,

        previewOutline = previewOutline,
        previewBodyFill = previewBodyFill,
        previewTaperFill = previewTaperFill,
        previewLinerFill = previewLinerFill,
        previewThreadFill = previewThreadFill,
        previewThreadHatch = previewThreadHatch,
        previewBlackWhiteOnly = previewBlackWhiteOnly,
        lineThicknessScale = lineThicknessScale,

        // model updates (unchanged)
        onSetCustomer = vm::setCustomer,
        onSetVessel = vm::setVessel,
        onSetJobNumber = vm::setJobNumber,
        onSetItem = vm::setItem,
        onSetShaftPosition = vm::setShaftPosition,
        onSetNotes = vm::setNotes,
        // Every mutation below names its [target] explicitly. A callable reference
        // (`vm::removeBody`) would take the ORIGINAL default and silently edit the wrong
        // drawing on the Final tab, so each one is a lambda that threads it through.
        onSetOverallLengthRaw = { raw -> vm.setOverallLength(raw, target) },
        onSetOverallLengthMm = { mm -> vm.onSetOverallLengthMm(mm, target) },
        onSelectComponentById = vm::selectComponentById,

        onAddBody   = { s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSp, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd ->
            vm.addBodyAt(s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSp, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd, target)
        },
        onSetAutoSectionDia = { s0, s1, d -> vm.setAutoSectionDiaMm(s0, s1, d, target) },
        onSetAutoBlend = { s0, s1, end, len, prof, seal -> vm.setAutoBlend(s0, s1, end, len, prof, seal, target) },
        onSetShowAutoBodyDia = { show -> vm.setShowAutoBodyDia(show, target) },
        onAddTaper  = { s, l, sd, ed, rate, ref, kwW, kwD, kwL, kwO, kwS, kwUnit ->
            vm.addTaperAt(s, l, sd, ed, rate, ref, kwW, kwD, kwL, kwO, kwS, kwUnit, target)
        },
        onAddThread = { s, l, maj, p, ex, aft, desig -> vm.addThreadAt(s, l, maj, p, ex, aft, desig, target) },
        onAddLiner  = { s, l, od, ref, shoulders -> vm.addLinerAt(
            s, l, od, ref,
            shoulderAftLenMm = shoulders.aft?.lenMm ?: 0f,
            shoulderAftOdMm = shoulders.aft?.odMm ?: 0f,
            shoulderAftRadiusMm = shoulders.aft?.radiusMm ?: 0f,
            shoulderFwdLenMm = shoulders.fwd?.lenMm ?: 0f,
            shoulderFwdOdMm = shoulders.fwd?.odMm ?: 0f,
            shoulderFwdRadiusMm = shoulders.fwd?.radiusMm ?: 0f,
            target = target,
        ) },
        onAddCouplerBoltSlot = { s, dia, cnt, sp, thru, dep, ref, style, clocking -> vm.addCouplerBoltSlotAt(s, dia, cnt, sp, thru, dep, ref, style, clocking, target) },

        onUpdateBody   = { i, s, l, d      -> vm.updateBody(i, s, l, d, target) },
        onUpdateBodyShowDia = { i, show    -> vm.updateBodyShowDia(i, show, target) },
        onUpdateBodyShowLabel = { i, show  -> vm.updateBodyShowLabel(i, show, target) },
        onUpdateBodyShade = { i, shade -> vm.updateBodyShade(i, shade, target) },
        onUpdateBodyCompressOnDrawing = { i, on -> vm.updateBodyCompressOnDrawing(i, on, target) },
        onUpdateBodyBlend = { i, aft, fwd, p, sAft, sFwd -> vm.updateBodyBlend(i, aft, fwd, p, sAft, sFwd, target) },
        onUpdateBodyLabel = { i, label     -> vm.updateBodyLabel(i, label, target) },
        onUpdateBodyKeyway = { i, w, d, l, offset, end, spooned -> vm.updateBodyKeyway(i, w, d, l, offset, end, spooned, target) },
        onUpdateTaper  = { i, s, l, sd, ed, rate -> vm.updateTaper(i, s, l, sd, ed, rate, target) },
        onUpdateTaperLabel = { i, label    -> vm.updateTaperLabel(i, label, target) },
        onUpdateTaperShowLabel = { i, show -> vm.updateTaperShowLabel(i, show, target) },
        onUpdateTaperShade = { i, shade -> vm.updateTaperShade(i, shade, target) },
        onUpdateTaperKeyway = { i, w, d, l, offset, spooned -> vm.updateTaperKeyway(i, w, d, l, offset, spooned, target) },
        onUpdateTaperReference = { i, ref -> vm.updateTaperAuthoredReference(i, ref, target) },
        onUpdateThread = { i, s, l, maj, p, desig -> vm.updateThread(i, s, l, maj, p, desig, target) },
        onUpdateThreadLabel = { i, label   -> vm.updateThreadLabel(i, label, target) },
        onUpdateThreadShowLabel = { i, show -> vm.updateThreadShowLabel(i, show, target) },
        onUpdateLiner  = { i, s, l, od     -> vm.updateLiner(i, s, l, od, target) },
        onUpdateLinerShowDia = { i, show   -> vm.updateLinerShowDia(i, show, target) },
        onUpdateLinerShowLabel = { i, show -> vm.updateLinerShowLabel(i, show, target) },
        onUpdateLinerShade = { i, shade -> vm.updateLinerShade(i, shade, target) },
        onUpdateLinerShoulder = { i, end, len, od, r -> vm.updateLinerShoulder(i, end, len, od, r, target) },
        linerShouldersEnabled = linerShouldersEnabled,
        dialogUnitConverterEnabled = dialogUnitConverterEnabled,
        onUpdateLinerLabel = { i, label    -> vm.updateLinerLabel(i, label, target) },
        onUpdateLinerReference = { i, ref  -> vm.updateLinerAuthoredReference(i, ref, target) },
        onUpdateCouplerBoltSlot = { i, s, dia, cnt, sp, thru, dep -> vm.updateCouplerBoltSlot(i, s, dia, cnt, sp, thru, dep, target) },
        onUpdateCouplerBoltSlotReference = { i, ref -> vm.updateCouplerBoltSlotReference(i, ref, target) },
        onUpdateCouplerBoltSlotStyle = { i, style -> vm.updateCouplerBoltSlotStyle(i, style, target) },
        onUpdateCouplerBoltSlotClocking = { i, clocking -> vm.updateCouplerBoltSlotClocking(i, clocking, target) },
        onUpdateCouplerBoltSlotShowRail = { i, show -> vm.updateCouplerBoltSlotShowRail(i, show, target) },

        onSetKeyways180Apart = { on -> vm.setKeyways180Apart(on, target) },
        onSetKeyways90Apart = { on -> vm.setKeyways90Apart(on, target) },
        onSetKeyways90Cw = { cw -> vm.setKeyways90Cw(cw, target) },
        onSetThreadExcludeFromOal = { id, ex -> vm.setThreadExcludeFromOal(id, ex, target) },
        onSetThreadEndPosition = { id, isAft -> vm.setThreadEndPosition(id, isAft, target) },

        onRemoveBody   = { id -> vm.removeBody(id, target) },
        onRemoveTaper  = { id -> vm.removeTaper(id, target) },
        onRemoveThread = { id -> vm.removeThread(id, target) },
        onRemoveLiner  = { id -> vm.removeLiner(id, target) },
        onRemoveCouplerBoltSlot = { id -> vm.removeCouplerBoltSlot(id, target) },

        snackbarHostState = snackbarHostState,

        onOpenSidebar = onOpenSidebar,
        onNew = onNew,
        onOpen = onOpen,
        onSave = onSave,
        onSaveAs = onSaveAs,
        onDuplicateForMate = onDuplicateForMate,
        onCloseDocument = onCloseDocument,
        onExportPdf = onExportPdf,
        onOpenSettings = onOpenSettings,
        onSendFeedback = onSendFeedback,
        onOpenDeveloperOptions = onOpenDeveloperOptions,

        devOptionsEnabled = devOptionsEnabled,

        canUndo = canUndo,
        canRedo = canRedo,
        onUndo = vm::undoEdit,
        onRedo = vm::redoEdit,

        sessionAddDefaults = sessionAddDefaults,

        perComponentUnitsEnabled = perComponentUnitsEnabled,
        unitOverrides = unitOverrides,
        onSetComponentUnit = vm::setComponentUnit,
        onSetKeywayUnit = vm::setKeywayUnit,
    )
}
