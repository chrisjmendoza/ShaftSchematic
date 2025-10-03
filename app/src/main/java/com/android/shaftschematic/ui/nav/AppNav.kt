// app/src/main/java/com/android/shaftschematic/ui/nav/AppNav.kt
package com.android.shaftschematic.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.shaftschematic.ui.screen.SettingsRoute
import com.android.shaftschematic.ui.screen.ShaftEditorRoute
import com.android.shaftschematic.ui.screen.StartScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

@Composable
fun AppNav(vm: ShaftViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "start") {

        composable("start") {
            StartScreen(
                onNew = {
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
                onOpen = { nav.navigate("openInternal") }, // you may add a menu item later
                onSave = { nav.navigate("saveInternal") },
                onSettings = { nav.navigate("settings") },
                onExportPdf = { nav.navigate("exportPdf") }
            )
        }

        composable("settings") { SettingsRoute(vm = vm, onBack = { nav.popBackStack() }) }

        // Internal JSON routes
        composable("openInternal") {
            OpenInternalRoute(nav = nav, vm = vm) {
                // After open, go to editor
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
