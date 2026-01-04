// file: app/src/main/java/com/android/shaftschematic/ui/nav/AppNav.kt
package com.android.shaftschematic.ui.nav

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.shaftschematic.ui.screen.AchievementsRoute
import com.android.shaftschematic.ui.screen.AboutRoute
import com.android.shaftschematic.ui.screen.DeveloperOptionsRoute
import com.android.shaftschematic.ui.screen.SettingsRoute
import com.android.shaftschematic.ui.screen.ShaftEditorRoute
import com.android.shaftschematic.ui.screen.StartScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.FeedbackIntentFactory
import kotlinx.coroutines.launch

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

            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { pad ->
                Box(Modifier.padding(pad)) {
                    StartScreen(
                        onNew = {
                            // Seed a blank doc and go to editor (unchanged behavior)
                            vm.importJson("""{"version":1,"spec":{}}""")
                            nav.navigate("editor")
                        },
                        // OPEN = internal storage browser
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
                        }
                    )
                }
            }
        }

        /* ───────── Editor ───────── */
        composable("editor") {
            ShaftEditorRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                // OPEN/SAVE = internal storage
                onOpen = { nav.navigate("openLocal") },
                onSave = { nav.navigate("saveLocal") },
                onSettings = { nav.navigate("settings") },
                // PDF EXPORT = external SAF
                onExportPdf = { nav.navigate("exportPdf") }
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

        /* ───────── External PDF export (SAF) ─────────
           This should remain your SAF-based CreateDocument("application/pdf") route.
           It writes PDF bytes to the chosen external location.
        */
        composable("exportPdf") {
            PdfExportRoute(nav = nav, vm = vm) { nav.popBackStack() }
        }
    }
}
