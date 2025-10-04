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
# SAF Routes – JSON open/save
 **Purpose**: One-shot composables that launch system pickers to open/save JSON.
 **Contract**:
- Only two public composables: `OpenInternalRoute`, `SaveInternalRoute`.
- UI reads/writes bytes; VM owns JSON schema/versioning (importJson/exportJson).
- Launch immediately on composition; call `onFinished()` when done or canceled.
- No toasts/snackbars here; let parent route/screen handle UX.
 */

@Composable
fun OpenInternalRoute(
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
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    vm.importJson(inp.bufferedReader().readText())
                }
            }
        }
        done.value = true
    }

    LaunchedEffect(Unit) { opener.launch(arrayOf("application/json")) }
    if (done.value) onFinished() else Text("") // headless route body
}

@Composable
fun SaveInternalRoute(
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

    LaunchedEffect(Unit) { saver.launch(defaultJsonFileName()) }
    if (done.value) onFinished() else Text("")
}

/* ── helpers ─────────────────────────────────────────────────────────────── */

private fun defaultJsonFileName(): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    return "Shaft_${sdf.format(Date())}.json"
}
