// file: app/src/main/java/com/android/shaftschematic/ui/nav/AppNav.kt
package com.android.shaftschematic.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.shaftschematic.ui.screen.SettingsRoute
import com.android.shaftschematic.ui.screen.ShaftEditorRoute
import com.android.shaftschematic.ui.screen.StartScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

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
            StartScreen(
                onNew = {
                    // Seed a blank doc and go to editor (unchanged behavior)
                    vm.importJson("""{"version":1,"spec":{}}""")
                    nav.navigate("editor")
                },
                // OPEN = internal storage browser
                onOpen = { nav.navigate("openLocal") },
                onSettings = { nav.navigate("settings") }
            )
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
            SettingsRoute(vm = vm, onBack = { nav.popBackStack() })
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
