// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftRoute.kt
package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.UiEvent
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
    onNavigateHome: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    // Collect one-shot UI events from the ViewModel (snackbars, Undo, etc.).
    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowDeletedSnack -> {
                    val label = when (event.kind) {
                        ComponentKind.BODY   -> "Body"
                        ComponentKind.TAPER  -> "Taper"
                        ComponentKind.THREAD -> "Thread"
                        ComponentKind.LINER  -> "Liner"
                    }

                    val result = snackbarHostState.showSnackbar(
                        message = "$label deleted",
                        actionLabel = "Undo",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long // ~5s-ish, depends on platform
                    )

                    if (result == SnackbarResult.ActionPerformed) {
                        vm.undoLastDelete()
                    }
                }
            }
        }
    }


    val spec            by vm.spec.collectAsState()
    val unit            by vm.unit.collectAsState()
    val unitLocked      by vm.unitLocked.collectAsState()
    val showGrid        by vm.showGrid.collectAsState()
    val previewBlackWhiteOnly by vm.previewBlackWhiteOnly.collectAsState()
    val previewOutline by vm.previewOutlineSetting.collectAsState()
    val previewBodyFill by vm.previewBodyFillSetting.collectAsState()
    val previewTaperFill by vm.previewTaperFillSetting.collectAsState()
    val previewLinerFill by vm.previewLinerFillSetting.collectAsState()
    val previewThreadFill by vm.previewThreadFillSetting.collectAsState()
    val previewThreadHatch by vm.previewThreadHatchSetting.collectAsState()
    val showComponentArrows by vm.showComponentArrows.collectAsState()
    val componentArrowWidthDp by vm.componentArrowWidthDp.collectAsState()
    val showOalDebugLabel by vm.showOalDebugLabel.collectAsState()
    val showOalHelperLine by vm.showOalHelperLine.collectAsState()
    val showOalInPreviewBox by vm.showOalInPreviewBox.collectAsState()
    val customer        by vm.customer.collectAsState()
    val vessel          by vm.vessel.collectAsState()
    val jobNumber       by vm.jobNumber.collectAsState()
    val shaftPosition   by vm.shaftPosition.collectAsState()
    val notes           by vm.notes.collectAsState()
    val overallIsManual by vm.overallIsManual.collectAsState()
    val order           by vm.componentOrder.collectAsState()

    val showComponentDebugLabels by vm.showComponentDebugLabels.collectAsState()
    val showRenderLayoutDebugOverlay by vm.showRenderLayoutDebugOverlay.collectAsState()
    val showRenderOalMarkers by vm.showRenderOalMarkers.collectAsState()

    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()
    val editorResetNonce by vm.editorResetNonce.collectAsState()

    val canUndoDeletes by vm.canUndoDeletes.collectAsState()
    val canRedoDeletes by vm.canRedoDeletes.collectAsState()

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
        unit = unit,
        unitLocked = unitLocked,
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
        showComponentArrows = showComponentArrows,
        componentArrowWidthDp = componentArrowWidthDp,

        previewOutline = previewOutline,
        previewBodyFill = previewBodyFill,
        previewTaperFill = previewTaperFill,
        previewLinerFill = previewLinerFill,
        previewThreadFill = previewThreadFill,
        previewThreadHatch = previewThreadHatch,
        previewBlackWhiteOnly = previewBlackWhiteOnly,
        componentOrder = order,

        // model updates (unchanged)
        onSetUnit = vm::setUnit,            // now used by Settings sheet
        onToggleGrid = vm::setShowGrid,     // now used by Settings sheet
        onSetCustomer = vm::setCustomer,
        onSetVessel = vm::setVessel,
        onSetJobNumber = vm::setJobNumber,
        onSetShaftPosition = vm::setShaftPosition,
        onSetNotes = vm::setNotes,
        onSetOverallLengthRaw = vm::setOverallLength,
        onSetOverallLengthMm = vm::onSetOverallLengthMm,
        onSetOverallIsManual = vm::setOverallIsManual,

        onAddBody   = { s, l, d      -> vm.addBodyAt(s, l, d) },
        onAddTaper  = { s, l, sd, ed -> vm.addTaperAt(s, l, sd, ed) },
        onAddThread = { s, l, maj, p, ex -> vm.addThreadAt(s, l, maj, p, ex) },
        onAddLiner  = { s, l, od     -> vm.addLinerAt(s, l, od) },

        onUpdateBody   = { i, s, l, d      -> vm.updateBody(i, s, l, d) },
        onUpdateTaper  = { i, s, l, sd, ed -> vm.updateTaper(i, s, l, sd, ed) },
        onUpdateTaperKeyway = { i, w, d    -> vm.updateTaperKeyway(i, w, d) },
        onUpdateThread = { i, s, l, maj, p -> vm.updateThread(i, s, l, maj, p) },
        onUpdateLiner  = { i, s, l, od     -> vm.updateLiner(i, s, l, od) },
        onUpdateLinerLabel = { i, label    -> vm.updateLinerLabel(i, label) },

        onSetThreadExcludeFromOal = vm::setThreadExcludeFromOal,

        onRemoveBody   = vm::removeBody,
        onRemoveTaper  = vm::removeTaper,
        onRemoveThread = vm::removeThread,
        onRemoveLiner  = vm::removeLiner,

        snackbarHostState = snackbarHostState,

        onNavigateHome = onNavigateHome,
        onNew = onNew,
        onOpen = onOpen,
        onSave = onSave,
        onExportPdf = onExportPdf,
        onOpenSettings = onOpenSettings,
        onSendFeedback = onSendFeedback,
        onOpenDeveloperOptions = onOpenDeveloperOptions,

        devOptionsEnabled = devOptionsEnabled,

        canUndo = canUndoDeletes,
        canRedo = canRedoDeletes,
        onUndo = vm::undoLastDelete,
        onRedo = vm::redoLastDelete,
    )
}
