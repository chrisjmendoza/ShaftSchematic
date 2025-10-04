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
 * Central navigation graph. Wires start, editor, settings, and our SAF helper routes.
 *
 * Contract
 * - No business logic here; only navigation.
 * - Save/Open/PDF routes are thin wrappers that call VM (export/import) and SAF.
 * - Start destination remains "start".
 */
@Composable
fun AppNav(vm: ShaftViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "start") {

        composable("start") {
            StartScreen(
                onNew = {
                    // Keep current behavior (no unit prompt here yet).
                    // We navigate to editor after seeding with a blank.
                    vm.importJson("""{"version":1,"spec":{}}""")
                    nav.navigate("editor")
                },
                onOpen = { nav.navigate("openInternal") },
                onSettings = { nav.navigate("settings") }
            )
        }

        composable("editor") {
            ShaftEditorRoute(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate("openInternal") },
                onSave = { nav.navigate("saveInternal") },
                onSettings = { nav.navigate("settings") },
                onExportPdf = { nav.navigate("exportPdf") }
            )
        }

        composable("settings") {
            SettingsRoute(vm = vm, onBack = { nav.popBackStack() })
        }

        // Internal JSON routes (must exist in your project)
        composable("openInternal") {
            OpenInternalRoute(nav = nav, vm = vm) {
                // After open, ensure editor is on top
                if (!nav.popBackStack("editor", inclusive = false)) nav.navigate("editor")
            }
        }
        composable("saveInternal") {
            SaveInternalRoute(nav = nav, vm = vm) { nav.popBackStack() }
        }

        // PDF export (SAF)
        composable("exportPdf") {
            PdfExportRoute(nav = nav, vm = vm) { nav.popBackStack() }
        }
    }
}
