// file: app/src/main/java/com/android/shaftschematic/ui/nav/AppNav.kt
package com.android.shaftschematic.ui.nav

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.shaftschematic.ui.screen.AchievementsRoute
import com.android.shaftschematic.ui.screen.AboutRoute
import com.android.shaftschematic.ui.screen.DeveloperOptionsRoute
import com.android.shaftschematic.ui.screen.PdfPreviewScreen
import com.android.shaftschematic.ui.screen.SettingsRoute
import com.android.shaftschematic.ui.screen.ShaftEditorRoute
import com.android.shaftschematic.ui.screen.StartScreen
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
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

    NavHost(navController = nav, startDestination = "start") {

        /* ───────── Start ───────── */
        composable("start") {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val unit by vm.unit.collectAsState()
            val hasDraft by vm.hasDraft.collectAsState(initial = false)
            var recentFiles by remember { mutableStateOf(listOf<Pair<String, Long>>()) }

            LaunchedEffect(Unit) {
                recentFiles = withContext(Dispatchers.IO) { InternalStorage.listWithMetadata(ctx) }
            }

            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { pad ->
                Box(Modifier.padding(pad)) {
                    StartScreen(
                        onNew = {
                            vm.newDocument()
                            nav.navigate("editor")
                        },
                        onOpen = { nav.navigate("openLocal") },
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
                        hasDraft = hasDraft,
                        onContinueDraft = { nav.navigate("editor") },
                        onDiscardDraft = { vm.discardDraft() },
                        recentFiles = recentFiles,
                        onOpenRecent = { filename ->
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { InternalStorage.load(ctx, filename) }
                                }.onSuccess { text ->
                                    vm.importJson(text)
                                    nav.navigate("editor")
                                }.onFailure {
                                    snackbarHostState.showSnackbar("Could not open file.")
                                }
                            }
                        }
                    )
                }
            }
        }

        /* ───────── Editor ───────── */
        composable("editor") {
            var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            if (pendingAction != null) {
                AlertDialog(
                    onDismissRequest = { pendingAction = null },
                    title = { Text("Unsaved changes") },
                    text = { Text("You have unsaved changes. Save before continuing?") },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingAction = null
                            nav.navigate("saveLocal")
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            val action = pendingAction
                            pendingAction = null
                            action?.invoke()
                        }) { Text("Discard") }
                    }
                )
            }

            ShaftEditorRoute(
                vm = vm,
                onNavigateHome = {
                    nav.navigate("start") {
                        launchSingleTop = true
                        popUpTo(nav.graph.startDestinationId) { inclusive = false }
                    }
                },
                onNew = {
                    if (vm.hasUnsavedWork()) pendingAction = { vm.newDocument() }
                    else vm.newDocument()
                },
                // OPEN/SAVE = internal storage
                onOpen = {
                    if (vm.hasUnsavedWork()) pendingAction = { nav.navigate("openLocal") }
                    else nav.navigate("openLocal")
                },
                onSave = { nav.navigate("saveLocal") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenDeveloperOptions = { nav.navigate("developerOptions") },
                // PDF EXPORT = show preview first, then SAF
                onExportPdf = { nav.navigate("pdfPreview") }
            )
        }

        /* ───────── Settings ───────── */
        composable("settings") {
            SettingsRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenAchievements = { nav.navigate("achievements") },
                onOpenAbout = { nav.navigate("about") },
                onOpenDeveloperOptions = { nav.navigate("developerOptions") },
            )
        }

        /* ───────── About ───────── */
        composable("about") {
            AboutRoute(vm = vm, onBack = { nav.popBackStack() })
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
            SaveLocalDocumentRoute(nav = nav, vm = vm) { nav.popBackStack() }
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
