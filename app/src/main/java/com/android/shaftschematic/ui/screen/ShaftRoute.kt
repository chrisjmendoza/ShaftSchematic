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
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
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
import com.android.shaftschematic.ui.viewmodel.updateBodyShowDia
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlot
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlotReference
import com.android.shaftschematic.ui.viewmodel.updateCouplerBoltSlotShowRail
import com.android.shaftschematic.ui.viewmodel.updateLiner
import com.android.shaftschematic.ui.viewmodel.updateLinerAuthoredReference
import com.android.shaftschematic.ui.viewmodel.updateLinerLabel
import com.android.shaftschematic.ui.viewmodel.updateLinerShoulder
import com.android.shaftschematic.ui.viewmodel.updateLinerShowDia
import com.android.shaftschematic.ui.viewmodel.updateTaper
import com.android.shaftschematic.ui.viewmodel.updateTaperAuthoredReference
import com.android.shaftschematic.ui.viewmodel.updateTaperKeyway
import com.android.shaftschematic.ui.viewmodel.updateTaperLabel
import com.android.shaftschematic.ui.viewmodel.updateThread
import com.android.shaftschematic.ui.viewmodel.updateThreadLabel
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftRoute(
    vm: ShaftViewModel,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit = {},
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


    val spec            by vm.spec.collectAsState()
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
    val showOalDebugLabel by vm.showOalDebugLabel.collectAsState()
    val showOalHelperLine by vm.showOalHelperLine.collectAsState()
    val showOalInPreviewBox by vm.showOalInPreviewBox.collectAsState()
    val customer        by vm.customer.collectAsState()
    val vessel          by vm.vessel.collectAsState()
    val jobNumber       by vm.jobNumber.collectAsState()
    val shaftPosition   by vm.shaftPosition.collectAsState()
    val notes           by vm.notes.collectAsState()
    val overallIsManual by vm.overallIsManual.collectAsState()
    val resolvedComponents by vm.resolvedComponents.collectAsState()
    val selectedComponentId by vm.selectedComponentId.collectAsState()

    val showComponentDebugLabels by vm.showComponentDebugLabels.collectAsState()
    val showRenderLayoutDebugOverlay by vm.showRenderLayoutDebugOverlay.collectAsState()
    val showRenderOalMarkers by vm.showRenderOalMarkers.collectAsState()
    val showDimDebugOverlay by vm.showDimDebugOverlay.collectAsState()
    val pdfTieringMode by vm.pdfTieringMode.collectAsState()

    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()
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
        spec = spec,
        documentName = currentDocumentName,
        hasUnsavedChanges = hasUnsavedChanges,
        resolvedComponents = resolvedComponents,
        unit = unit,
        overallIsManual = overallIsManual,
        customer = customer,
        vessel = vessel,
        jobNumber = jobNumber,
        shaftPosition = shaftPosition,
        notes = notes,
        showGrid = showGrid,
        showOalDebugLabel = showOalDebugLabel,
        showOalHelperLine = showOalHelperLine,
        showOalInPreviewBox = showOalInPreviewBox,
        showComponentDebugLabels = showComponentDebugLabels,
        showRenderLayoutDebugOverlay = showRenderLayoutDebugOverlay,
        showRenderOalMarkers = showRenderOalMarkers,
        showDimDebugOverlay = showDimDebugOverlay,
        pdfTieringMode = pdfTieringMode,
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
        onSetShaftPosition = vm::setShaftPosition,
        onSetNotes = vm::setNotes,
        onSetOverallLengthRaw = vm::setOverallLength,
        onSetOverallLengthMm = vm::onSetOverallLengthMm,
        onSetOverallIsManual = vm::setOverallIsManual,
        onSelectComponentById = vm::selectComponentById,

        onAddBody   = { s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSp, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd ->
            vm.addBodyAt(s, l, d, kwW, kwD, kwL, kwO, kwEnd, kwSp, kwUnit, bAft, bFwd, bProf, bSAft, bSFwd)
        },
        onSetAutoSectionDia = vm::setAutoSectionDiaMm,
        onSetAutoBlend = { s0, s1, end, len, prof, seal -> vm.setAutoBlend(s0, s1, end, len, prof, seal) },
        onSetShowAutoBodyDia = vm::setShowAutoBodyDia,
        onAddTaper  = { s, l, sd, ed, rate, ref, kwW, kwD, kwL, kwO, kwS, kwUnit ->
            vm.addTaperAt(s, l, sd, ed, rate, ref, kwW, kwD, kwL, kwO, kwS, kwUnit)
        },
        onAddThread = { s, l, maj, p, ex, aft, desig -> vm.addThreadAt(s, l, maj, p, ex, aft, desig) },
        onAddLiner  = { s, l, od, ref, shoulders -> vm.addLinerAt(
            s, l, od, ref,
            shoulderAftLenMm = shoulders.aft?.lenMm ?: 0f,
            shoulderAftOdMm = shoulders.aft?.odMm ?: 0f,
            shoulderAftRadiusMm = shoulders.aft?.radiusMm ?: 0f,
            shoulderFwdLenMm = shoulders.fwd?.lenMm ?: 0f,
            shoulderFwdOdMm = shoulders.fwd?.odMm ?: 0f,
            shoulderFwdRadiusMm = shoulders.fwd?.radiusMm ?: 0f,
        ) },
        onAddCouplerBoltSlot = { s, dia, cnt, sp, thru, dep, ref -> vm.addCouplerBoltSlotAt(s, dia, cnt, sp, thru, dep, ref) },

        onUpdateBody   = { i, s, l, d      -> vm.updateBody(i, s, l, d) },
        onUpdateBodyShowDia = { i, show    -> vm.updateBodyShowDia(i, show) },
        onUpdateBodyBlend = { i, aft, fwd, p, sAft, sFwd -> vm.updateBodyBlend(i, aft, fwd, p, sAft, sFwd) },
        onUpdateBodyLabel = { i, label     -> vm.updateBodyLabel(i, label) },
        onUpdateBodyKeyway = { i, w, d, l, offset, end, spooned -> vm.updateBodyKeyway(i, w, d, l, offset, end, spooned) },
        onUpdateTaper  = { i, s, l, sd, ed, rate -> vm.updateTaper(i, s, l, sd, ed, rate) },
        onUpdateTaperLabel = { i, label    -> vm.updateTaperLabel(i, label) },
        onUpdateTaperKeyway = { i, w, d, l, offset, spooned -> vm.updateTaperKeyway(i, w, d, l, offset, spooned) },
        onUpdateTaperReference = { i, ref -> vm.updateTaperAuthoredReference(i, ref) },
        onUpdateThread = { i, s, l, maj, p, desig -> vm.updateThread(i, s, l, maj, p, desig) },
        onUpdateThreadLabel = { i, label   -> vm.updateThreadLabel(i, label) },
        onUpdateLiner  = { i, s, l, od     -> vm.updateLiner(i, s, l, od) },
        onUpdateLinerShowDia = { i, show   -> vm.updateLinerShowDia(i, show) },
        onUpdateLinerShoulder = { i, end, len, od, r -> vm.updateLinerShoulder(i, end, len, od, r) },
        linerShouldersEnabled = linerShouldersEnabled,
        onUpdateLinerLabel = { i, label    -> vm.updateLinerLabel(i, label) },
        onUpdateLinerReference = { i, ref  -> vm.updateLinerAuthoredReference(i, ref) },
        onUpdateCouplerBoltSlot = { i, s, dia, cnt, sp, thru, dep -> vm.updateCouplerBoltSlot(i, s, dia, cnt, sp, thru, dep) },
        onUpdateCouplerBoltSlotReference = { i, ref -> vm.updateCouplerBoltSlotReference(i, ref) },
        onUpdateCouplerBoltSlotShowRail = { i, show -> vm.updateCouplerBoltSlotShowRail(i, show) },

        onSetKeyways180Apart = vm::setKeyways180Apart,
        onSetKeyways90Apart = vm::setKeyways90Apart,
        onSetKeyways90Cw = vm::setKeyways90Cw,
        onSetThreadExcludeFromOal = vm::setThreadExcludeFromOal,
        onSetThreadEndPosition = vm::setThreadEndPosition,

        onRemoveBody   = vm::removeBody,
        onRemoveTaper  = vm::removeTaper,
        onRemoveThread = vm::removeThread,
        onRemoveLiner  = vm::removeLiner,
        onRemoveCouplerBoltSlot = vm::removeCouplerBoltSlot,

        snackbarHostState = snackbarHostState,

        onOpenSidebar = onOpenSidebar,
        onNew = onNew,
        onOpen = onOpen,
        onSave = onSave,
        onSaveAs = onSaveAs,
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
