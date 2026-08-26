// file: app/src/main/java/com/android/shaftschematic/ui/nav/AppNav.kt
package com.android.shaftschematic.ui.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.shaftschematic.ui.screen.AchievementsRoute
import com.android.shaftschematic.ui.screen.AboutRoute
import com.android.shaftschematic.ui.screen.DeveloperOptionsRoute
import com.android.shaftschematic.ui.screen.HelpRoute
import com.android.shaftschematic.ui.screen.PdfPreviewScreen
import com.android.shaftschematic.ui.screen.SettingsRoute
import com.android.shaftschematic.ui.screen.ShaftEditorRoute
import com.android.shaftschematic.ui.screen.StartScreen
import com.android.shaftschematic.ui.screen.TemplatesRoute
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.applyTemplate
import com.android.shaftschematic.ui.viewmodel.exportJson
import com.android.shaftschematic.ui.viewmodel.importJson
import com.android.shaftschematic.ui.viewmodel.newDocument
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.FeedbackIntentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AppNav
 *
 * Purpose
 * Central navigation graph. Routes:
 *  - Start screen (New/Open/Settings)
 *  - Editor route (top bar actions for Save, Open, PDF export)
 *  - Settings route
 *  - Internal JSON routes (open/save inside app storage)
 *  - External PDF export (SAF)
 *
 * Contract
 * - No business logic; only navigation glue.
 * - JSON open/save go to *internal* storage routes (no SAF).
 * - PDF export goes to *external* SAF route.
 */
@Composable
fun AppNav(vm: ShaftViewModel) {
    val nav = rememberNavController()
    val appCtx = LocalContext.current
    val appScope = rememberCoroutineScope()
    val currentDocumentName by vm.currentDocumentName.collectAsState()

    // Continuation for the unsaved-changes dialog's "Save" path: when the user must first
    // name the document (saveLocal route), the intended action (New/Open) is stashed here
    // and resumed after a successful save. Cleared on cancel (unsaved work still present).
    val pendingPostSaveAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    // Hoisted unsaved-changes guard shared by EVERY session-replacing entry point (Start's
    // New/Open/Open-recent, the editor's New/Open, and Close Document). The action to run once
    // the user resolves the prompt lives here at NavHost scope so a single dialog serves all
    // routes and the saveLocal continuation (pendingPostSaveAction) works from anywhere.
    var guardedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Run [action] immediately when the session is clean, else open the unsaved-changes dialog.
    val runGuarded: (() -> Unit) -> Unit = { action ->
        if (vm.hasUnsavedWork()) guardedAction = action else action()
    }

    guardedAction?.let { action ->
        UnsavedChangesDialog(
            onSave = {
                guardedAction = null
                val docName = currentDocumentName
                if (docName != null) {
                    // Known filename → quick-save, then continue the action.
                    // Deliberately no rename offer here (the editor's own quick-save has one):
                    // this save exists to clear the way for a session-replacing action, so the
                    // snackbar would outlive the screen it belongs to and act on a document the
                    // user has already left.
                    appScope.launch {
                        withContext(Dispatchers.IO) {
                            InternalStorage.save(appCtx, docName, vm.exportJson())
                        }
                        vm.markDocumentSaved()
                        action()
                    }
                } else {
                    // Needs a name → resume the action after the save screen.
                    pendingPostSaveAction.value = action
                    nav.navigate("saveLocal")
                }
            },
            onDontSave = {
                // Proceed without saving. The draft-ring entry stays as the safety net.
                guardedAction = null
                action()
            },
            onCancel = { guardedAction = null },
        )
    }

    NavHost(navController = nav, startDestination = "start") {

        /* ───────── Start ───────── */
        composable("start") {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val unit by vm.unit.collectAsState()
            val drafts by vm.drafts.collectAsState()
            var recentFiles by remember { mutableStateOf(listOf<Pair<String, Long>>()) }

            LaunchedEffect(Unit) {
                recentFiles = withContext(Dispatchers.IO) { InternalStorage.listWithMetadata(ctx) }
            }

            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { pad ->
                Box(Modifier.padding(pad)) {
                    StartScreen(
                        onNew = {
                            runGuarded {
                                vm.newDocument()
                                nav.navigate("editor")
                            }
                        },
                        onOpen = { runGuarded { nav.navigate("openLocal") } },
                        onOpenTemplates = { nav.navigate("templates") },
                        onSettings = { nav.navigate("settings") },
                        onSendFeedback = {
                            val intent = FeedbackIntentFactory.create(
                                context = ctx,
                                screen = "Home",
                                unit = unit,
                                selectedSaveName = null,
                                attachments = emptyList()
                            )
                            try {
                                ctx.startActivity(Intent.createChooser(intent, "Send Feedback"))
                            } catch (_: ActivityNotFoundException) {
                                scope.launch { snackbarHostState.showSnackbar("No email app found.") }
                            }
                        },
                        drafts = drafts,
                        onContinueDraft = { draftId ->
                            vm.continueDraft(draftId)
                            nav.navigate("editor")
                        },
                        onDiscardDraft = { draftId -> vm.discardDraft(draftId) },
                        recentFiles = recentFiles,
                        onOpenRecent = { filename ->
                            runGuarded {
                                scope.launch {
                                    // importJson must be inside runCatching — it throws on
                                    // corrupt files and unsupported (newer) format versions.
                                    runCatching {
                                        val text = withContext(Dispatchers.IO) { InternalStorage.load(ctx, filename) }
                                        vm.importJson(text)
                                    }.onSuccess {
                                        vm.setCurrentDocumentName(filename)
                                        nav.navigate("editor")
                                    }.onFailure { e ->
                                        snackbarHostState.showSnackbar(openFailureMessage(filename, e))
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        /* ───────── Editor ───────── */
        composable("editor") {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            val editorSnackbarHostState = remember { SnackbarHostState() }

            // Project info behind the suggested filename — the same four inputs the save screen
            // suggests from, so a quick-save can tell when the saved name has fallen behind it.
            val jobNumber by vm.jobNumber.collectAsState()
            val customer by vm.customer.collectAsState()
            val vessel by vm.vessel.collectAsState()
            val shaftPosition by vm.shaftPosition.collectAsState()

            // Session-scoped dedup for the rename offer, keyed "<from>→<to>". A declined offer
            // must not reappear on every following save; later job-info edits produce a new pair
            // and are offered again.
            val offeredRenames = remember { mutableSetOf<String>() }

            val goHome: () -> Unit = {
                nav.navigate("start") {
                    launchSingleTop = true
                    popUpTo(nav.graph.startDestinationId) { inclusive = false }
                }
            }

            Box(Modifier.fillMaxSize()) {
                ShaftEditorRoute(
                    vm = vm,
                    onNavigateHome = goHome,
                    onNew = { runGuarded { vm.newDocument() } },
                    // OPEN/SAVE = internal storage
                    onOpen = { runGuarded { nav.navigate("openLocal") } },
                    onSave = {
                        val docName = currentDocumentName
                        if (docName != null) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    InternalStorage.save(ctx, docName, vm.exportJson())
                                }
                                vm.markDocumentSaved()
                                offerRenameAfterQuickSave(
                                    ctx = ctx,
                                    vm = vm,
                                    docName = docName,
                                    jobNumber = jobNumber,
                                    customer = customer,
                                    vessel = vessel,
                                    positionSuffix = shaftPosition.printableLabelOrNull(),
                                    offeredRenames = offeredRenames,
                                    snackbarHostState = editorSnackbarHostState,
                                )
                            }
                        } else {
                            nav.navigate("saveLocal")
                        }
                    },
                    onSaveAs = { nav.navigate("saveLocal") },
                    // Close = reset to a blank doc and return home. Guarded so unsaved work prompts
                    // Save/Don't save/Cancel first (the draft ring keeps the work either way).
                    onCloseDocument = {
                        runGuarded {
                            vm.newDocument()
                            goHome()
                        }
                    },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenDeveloperOptions = { nav.navigate("developerOptions") },
                    // PDF EXPORT = show preview first, then SAF
                    onExportPdf = { nav.navigate("pdfPreview") }
                )

                // The tabs own their own insets, so the host needs only the navigation bar's.
                SnackbarHost(
                    editorSnackbarHostState,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
            }
        }

        /* ───────── Templates ─────────
           Browsing is harmless, so the route itself is unguarded; CHOOSING a template
           replaces the session, so that action goes through the same unsaved-changes guard
           as New/Open. applyTemplate leaves the session dirty and unnamed on purpose — see
           its KDoc — so the draft ring protects the loaded template from the first moment.
        */
        composable("templates") {
            TemplatesRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                onUseTemplate = { raw ->
                    runGuarded {
                        vm.applyTemplate(raw)
                        nav.navigate("editor") { popUpTo("start") { inclusive = false } }
                    }
                },
            )
        }

        /* ───────── Settings ───────── */
        composable("settings") {
            SettingsRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenAchievements = { nav.navigate("achievements") },
                onOpenAbout = { nav.navigate("about") },
                onOpenHelp = { nav.navigate("help") },
                onOpenDeveloperOptions = { nav.navigate("developerOptions") },
            )
        }

        /* ───────── About ───────── */
        composable("about") {
            AboutRoute(vm = vm, onBack = { nav.popBackStack() })
        }

        /* ───────── Help & FAQ ───────── */
        composable("help") {
            HelpRoute(onBack = { nav.popBackStack() })
        }

        /* ───────── Developer Options ───────── */
        composable("developerOptions") {
            DeveloperOptionsRoute(vm = vm, onBack = { nav.popBackStack() })
        }

        /* ───────── Achievements ───────── */
        composable("achievements") {
            AchievementsRoute(vm = vm, onBack = { nav.popBackStack() })
        }

        /* ───────── Internal JSON routes (app sandbox) ─────────
           Uses InternalStorage via InternalDocRoutes.kt
           Names are distinct from SAF to avoid overload/name collisions.
        */
        composable("openLocal") {
            OpenLocalDocumentRoute(nav = nav, vm = vm) {
                // After open, ensure editor is visible
                if (!nav.popBackStack("editor", inclusive = false)) nav.navigate("editor")
            }
        }
        composable("saveLocal") {
            SaveLocalDocumentRoute(nav = nav, vm = vm) {
                nav.popBackStack()
                // Resume a stashed New/Open only if the save actually happened
                // (cancel leaves unsaved work in place → drop the continuation).
                val action = pendingPostSaveAction.value
                pendingPostSaveAction.value = null
                if (action != null && !vm.hasUnsavedWork()) action()
            }
        }

        /* ───────── PDF Preview ─────────
           Shows a full-resolution raster preview of the PDF page with pinch-to-zoom.
           The "Export PDF" action in the top bar navigates onward to the SAF route.
        */
        composable("pdfPreview") {
            PdfPreviewScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onExport = { nav.navigate("exportPdf") }
            )
        }

        /* ───────── External PDF export (SAF) ─────────
           This should remain your SAF-based CreateDocument("application/pdf") route.
           It writes PDF bytes to the chosen external location.
        */
        composable("exportPdf") {
            PdfExportRoute(nav = nav, vm = vm) { nav.popBackStack() }
        }
    }
}

/**
 * Offers a one-tap rename after a quick-save when the document's project information now
 * suggests a different filename than the one it was saved under (the first save names the
 * document; job info filled in afterwards would otherwise leave a stale name with no prompt).
 *
 * Does nothing when there is no better name to offer, when this exact from→to pair has already
 * been offered this editor session ([offeredRenames], so a declined offer cannot nag on every
 * save), or when a document already occupies the target name — the offer never overwrites, the
 * same posture as the Open screen's rename dialog.
 */
private suspend fun offerRenameAfterQuickSave(
    ctx: Context,
    vm: ShaftViewModel,
    docName: String,
    jobNumber: String,
    customer: String,
    vessel: String,
    positionSuffix: String?,
    offeredRenames: MutableSet<String>,
    snackbarHostState: SnackbarHostState,
) {
    val suggestionBase = DocumentNaming.renameSuggestionBase(
        currentDocumentName = docName,
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        positionSuffix = positionSuffix,
    ) ?: return

    val toName = InternalStorage.normalizeShaftDocName(suggestionBase) ?: return
    if (!offeredRenames.add("$docName→$toName")) return

    val targetTaken = withContext(Dispatchers.IO) { InternalStorage.exists(ctx, toName) }
    if (targetTaken) return

    val result = snackbarHostState.showSnackbar(
        message = "Saved. Rename to ‘$suggestionBase’?",
        actionLabel = "Rename",
        withDismissAction = true,
        duration = SnackbarDuration.Long,
    )
    if (result != SnackbarResult.ActionPerformed) return

    val renamed = withContext(Dispatchers.IO) { InternalStorage.rename(ctx, docName, toName) }
    if (renamed) {
        vm.setCurrentDocumentName(toName)
        snackbarHostState.showSnackbar("Renamed to ‘$suggestionBase’")
    } else {
        snackbarHostState.showSnackbar("Could not rename to ‘$suggestionBase’.")
    }
}

/**
 * Shared "Unsaved changes" prompt for every session-replacing action (New, Open, Open recent,
 * Close Document). Three choices: Save (persist then continue), Don't save (continue — the
 * autosave draft ring keeps the work as a safety net, so this is not "Discard"), Cancel (stay).
 */
@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = { Text("You have unsaved changes. Save before continuing?") },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDontSave) { Text("Don't save") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}
