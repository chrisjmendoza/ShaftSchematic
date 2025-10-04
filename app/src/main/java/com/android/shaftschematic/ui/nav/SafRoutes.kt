package com.android.shaftschematic.ui.nav

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SAF one-shot “routes” used by the nav graph.
 *
 * Contract:
 * - Exactly one public Composable for open and one for save — no overloads.
 * - UI layer only reads/writes bytes; JSON shape/versioning lives in the ViewModel.
 * - Caller navigates to this route; the Composable immediately launches the SAF
 *   contract, then calls [onFinished] (typically popBackStack) when done.
 */
@Composable
fun OpenDocumentRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val done = remember { mutableStateOf(false) }

    val opener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        runCatching {
            if (uri != null) {
                // Optional: persist read permission so future reads work without relaunch
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)

                context.contentResolver.openInputStream(uri)?.use { inp ->
                    vm.importJson(inp.bufferedReader().readText())
                }
            }
        }
        done.value = true
    }

    LaunchedEffect(Unit) { opener.launch(arrayOf("application/json")) }
    if (done.value) onFinished() else Text("") // minimal body to satisfy composition
}

@Composable
fun SaveDocumentRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val done = remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        runCatching {
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.writer().use { w -> w.write(vm.exportJson()) }
                }
            }
        }
        done.value = true
    }

    LaunchedEffect(Unit) { saver.launch(defaultFileName()) }
    if (done.value) onFinished() else Text("")
}

private fun defaultFileName(): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    return "Shaft_${sdf.format(Date())}.json"
}
