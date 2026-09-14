// file: app/src/main/java/com/android/shaftschematic/ui/screen/FinalRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.pdf.composeRunoutPdf
import com.android.shaftschematic.ui.nav.FINAL_DRAWING_LABEL
import com.android.shaftschematic.ui.nav.blockingExportError
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.SpecTarget
import com.android.shaftschematic.ui.viewmodel.discardFinalSpec
import com.android.shaftschematic.ui.viewmodel.resetFinalSpec
import com.android.shaftschematic.ui.viewmodel.startFinalSpec
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.exportPdfFilename
import com.android.shaftschematic.util.launchPicker
import com.android.shaftschematic.util.printShaftPdfPage
import com.android.shaftschematic.util.writeShaftPdfToUri

/**
 * FinalRoute — the Final Schematic tab.
 *
 * The document's SECOND geometry: the shaft as it leaves, after the wear and undercut work
 * moved a liner, lengthened it, or shortened it. The original schematic is the record of what
 * came in and nothing on this tab changes it — the two are the before and the after.
 *
 * Two states, one tab:
 * - **No final drawing yet** — the light tab chrome over an empty state saying what this is,
 *   what it is not, and one button to start it. Nothing else: a tab that offers editing
 *   controls over a drawing that does not exist has to invent one to show them.
 * - **A final drawing exists** — the SAME editor the Schematic tab hosts, bound to the final
 *   geometry (`ShaftRoute(target = FINAL)`). The carousel, add dialogs, preview box, collision
 *   badges and warnings need no changes at all: they are presentational over whatever spec
 *   they are handed. A standing banner carries which drawing this is, plus this drawing's own
 *   actions — its blank runout sheet, Reset, and Discard.
 *
 * Outputs from here are marked `Drawing: Final` ([ProjectInfo.drawingLabel]) and take a
 * `_Final` filename, so a final sheet can never be handed out as the original.
 * See `docs/contracts/FinalSchematic.md` §3–4.
 */
@Composable
fun FinalRoute(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit = {},
    /** Quick-save the document (prompts for a name when it has never been saved). */
    onSave: () -> Unit = {},
    onNew: () -> Unit = {},
    onOpen: () -> Unit = {},
    onSaveAs: () -> Unit = {},
    onDuplicateForMate: () -> Unit = {},
    onCloseDocument: () -> Unit = {},
    /** Opens the PDF preview on the FINAL drawing (`pdfPreview?target=final`). */
    onExportFinalPdf: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDeveloperOptions: () -> Unit = {},
) {
    val finalSpec by vm.finalSpec.collectAsState()

    if (finalSpec == null) {
        FinalEmptyState(
            vm = vm,
            onOpenSidebar = onOpenSidebar,
            onSave = onSave,
        )
        return
    }

    // Dialogs and the SAF launcher are hosted here rather than inside the banner: the banner
    // is a lambda the editor's top bar invokes, and state that has to outlive a menu dismissal
    // belongs to the route.
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var blockedMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val runout = rememberFinalRunoutSheet(vm) { blockedMessage = it }

    Box(Modifier.fillMaxSize()) {
        ShaftRoute(
            vm = vm,
            target = SpecTarget.FINAL,
            editorTitle = EditorTab.FINAL.label,
            banner = {
                FinalBanner(
                    onPrintRunout = runout.print,
                    onExportRunout = runout.export,
                    onReset = { confirmReset = true },
                    onDiscard = { confirmDiscard = true },
                )
            },
            onNew = onNew,
            onOpen = onOpen,
            onSave = onSave,
            onSaveAs = onSaveAs,
            onDuplicateForMate = onDuplicateForMate,
            onCloseDocument = onCloseDocument,
            onExportPdf = onExportFinalPdf,
            onOpenSettings = onOpenSettings,
            onOpenDeveloperOptions = onOpenDeveloperOptions,
            onOpenSidebar = onOpenSidebar,
        )

        if (confirmReset) {
            ConfirmDialog(
                title = "Reset to original?",
                message = "Replace the final drawing with a fresh copy of the original " +
                    "schematic? Edits made here will be lost.",
                confirmLabel = "Reset",
                testTag = "final_reset_confirm",
                onConfirm = { confirmReset = false; vm.resetFinalSpec() },
                onDismiss = { confirmReset = false },
            )
        }

        if (confirmDiscard) {
            ConfirmDialog(
                title = "Discard final drawing?",
                message = "Remove the final drawing? The original schematic is unaffected.",
                confirmLabel = "Discard",
                testTag = "final_discard_confirm",
                onConfirm = { confirmDiscard = false; vm.discardFinalSpec() },
                onDismiss = { confirmDiscard = false },
            )
        }

        blockedMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { blockedMessage = null },
                title = { Text("Cannot print final runout sheet") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { blockedMessage = null }) { Text("OK") }
                },
            )
        }
    }
}

/**
 * The tab before there is anything to edit. Deliberately prose plus ONE action: the decision
 * being offered is whether this job gets a second drawing at all, and every control that would
 * edit it belongs to the editor that appears once it does.
 */
@Composable
private fun FinalEmptyState(
    vm: ShaftViewModel,
    onOpenSidebar: () -> Unit,
    onSave: () -> Unit,
) {
    val currentDocumentName by vm.currentDocumentName.collectAsState()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsState()

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        EditorDocumentTitle(
            documentName = currentDocumentName,
            hasUnsavedChanges = hasUnsavedChanges,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSidebar) {
                Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
            }
            Text(
                text = EditorTab.FINAL.label,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSave,
                modifier = Modifier.testTag("toolbar_save"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save")
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "The drawing the shaft leaves with. Start it as a copy of the original " +
                    "schematic, then move, extend or shorten liners after the wear and " +
                    "undercut work. The original schematic is never changed — keep it as the " +
                    "before to this after.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Prints its own schematic and a blank runout sheet, both marked Final. " +
                    "Consolidated Output keeps drawing the original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { vm.startFinalSpec() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("final_start_button"),
            ) {
                Icon(Icons.Filled.FactCheck, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start from original schematic")
            }
        }
    }
}

/**
 * The standing strip under the Final tab's top bar: which drawing this is, and the actions
 * that belong to this drawing rather than to the document. It stays visible the whole time the
 * final is being edited — the whole point of the feature is that the other drawing is still
 * there, untouched, and an editor that looks exactly like the Schematic tab needs to say so.
 *
 * The destructive pair sits behind an overflow menu, and both confirm: a reset throws away
 * every edit made here and a discard removes the drawing.
 */
@Composable
private fun FinalBanner(
    onPrintRunout: () -> Unit,
    onExportRunout: () -> Unit,
    onReset: () -> Unit,
    onDiscard: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .testTag("final_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Final drawing — the original schematic is untouched",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Final drawing actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Print final runout sheet") },
                    onClick = { menuOpen = false; onPrintRunout() },
                )
                DropdownMenuItem(
                    text = { Text("Export final runout sheet…") },
                    onClick = { menuOpen = false; onExportRunout() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Reset to original") },
                    onClick = { menuOpen = false; onReset() },
                )
                DropdownMenuItem(
                    text = { Text("Discard final drawing") },
                    onClick = { menuOpen = false; onDiscard() },
                )
            }
        }
    }
}

/** A confirm dialog for an action that throws work away. */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    testTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(testTag),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The Print / Export pair for the final drawing's runout sheet. */
private class FinalRunoutSheet(val print: () -> Unit, val export: () -> Unit)

/**
 * Builds the final drawing's **blank final measurement sheet**: the classic standalone runout
 * sheet (`consolidated = false`) over the final geometry, with NO readings, NO dragged station
 * placements, NO wear record and NO station-count overrides. Those all belong to the ORIGINAL
 * shaft — they are the inspection of what came in, keyed to that geometry — and carrying them
 * onto the final would print measurements of the old shaft on the sheet the new runouts are
 * taken on. What comes out is an empty grid of default stations, marked Final.
 *
 * The export gate runs on the final spec exactly as it does on the original; a blocked sheet
 * reports through [onBlocked] rather than writing a drawing nobody can trust.
 */
@Composable
private fun rememberFinalRunoutSheet(
    vm: ShaftViewModel,
    onBlocked: (String) -> Unit,
): FinalRunoutSheet {
    val ctx = LocalContext.current

    val finalSpec by vm.finalSpec.collectAsState()
    val finalResolved by vm.finalResolvedComponents.collectAsState()
    val unit by vm.unit.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val item by vm.item.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()
    val runoutConfig by vm.runoutConfig.collectAsState()
    val lineThicknessScale by vm.lineThicknessScale.collectAsState()
    val openAfterExport by vm.openPdfAfterExport.collectAsState()

    val filename = remember(customer, vessel, jobNumber, shaftPosition) {
        val base = DocumentNaming.suggestedBaseName(
            jobNumber = jobNumber,
            customer = customer,
            vessel = vessel,
            suffix = shaftPosition.printableLabelOrNull(),
        ) ?: "RunoutSheet"
        exportPdfFilename(base, FINAL_RUNOUT_FILENAME_SUFFIX)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val spec = finalSpec
            if (spec != null) {
                // Hardened write: a composer throw yields a valid error page, never a
                // truncated file (util/PdfSafExport.kt — one implementation for every tab).
                val wrote = writeShaftPdfToUri(ctx, uri) { page ->
                    composeRunoutPdf(
                        page = page,
                        spec = spec,
                        // Station counts start from the defaults: an override was authored
                        // against the original's components.
                        config = runoutConfig.copy(componentOverrides = emptyMap()),
                        project = ProjectInfo(
                            customer = customer, vessel = vessel, side = shaftPosition,
                            jobNumber = jobNumber, item = item,
                            drawingLabel = FINAL_DRAWING_LABEL,
                        ),
                        unit = unit,
                        displayUnits = vm.currentDisplayUnits(),
                        pdfPrefs = vm.currentPdfPrefs,
                        resolvedComponents = finalResolved,
                        lineThicknessScale = lineThicknessScale,
                        consolidated = false,
                    )
                }
                if (wrote && openAfterExport) openRunoutPdf(ctx, uri)
            }
        }
    }

    return FinalRunoutSheet(
        print = {
            val spec = finalSpec
            val blocked = spec?.let { blockingExportError(it) }
            when {
                spec == null -> Unit
                blocked != null -> onBlocked(blocked)
                else -> {
                    // Snapshot every value on the UI thread — `onWrite` runs on a binder
                    // thread and may not touch Compose state.
                    val configSnapshot = runoutConfig.copy(componentOverrides = emptyMap())
                    val projectSnapshot = ProjectInfo(
                        customer = customer, vessel = vessel, side = shaftPosition,
                        jobNumber = jobNumber, item = item,
                        drawingLabel = FINAL_DRAWING_LABEL,
                    )
                    val unitSnapshot = unit
                    val prefsSnapshot = vm.currentPdfPrefs
                    val resolvedSnapshot = finalResolved
                    val thicknessSnapshot = lineThicknessScale
                    val displayUnitsSnapshot = vm.currentDisplayUnits()
                    printShaftPdfPage(ctx, filename.removeSuffix(".pdf")) { page ->
                        composeRunoutPdf(
                            page = page,
                            spec = spec,
                            config = configSnapshot,
                            project = projectSnapshot,
                            unit = unitSnapshot,
                            displayUnits = displayUnitsSnapshot,
                            pdfPrefs = prefsSnapshot,
                            resolvedComponents = resolvedSnapshot,
                            lineThicknessScale = thicknessSnapshot,
                            consolidated = false,
                        )
                    }
                }
            }
        },
        export = {
            val spec = finalSpec
            val blocked = spec?.let { blockingExportError(it) }
            when {
                spec == null -> Unit
                blocked != null -> onBlocked(blocked)
                else -> launcher.launchPicker(filename, what = "final runout export")
            }
        },
    )
}

/**
 * Filename suffix for the banner's blank classic runout sheet: `…_Final_RunoutSheet.pdf`.
 * Deliberately NOT `_Final_Runout` — that name belongs to the final SCHEMATIC once its
 * "Runout bubbles" election is on, and two different documents sharing one filename is how the
 * second export silently replaces the first.
 */
private const val FINAL_RUNOUT_FILENAME_SUFFIX = "_Final_RunoutSheet"
