package com.android.shaftschematic.ui.nav

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.VerboseLog
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
                VerboseLog.d(VerboseLog.Category.IO, "SafRoutes") { "open picked uri=$uri" }
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    val text = inp.bufferedReader().readText()
                    VerboseLog.d(VerboseLog.Category.IO, "SafRoutes") { "open read chars=${text.length}" }
                    vm.importJson(text)
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

    // If/when JSON export via SAF becomes user-facing, keep the suggested filename
    // aligned with internal saves and PDF export.
    val jobNumber by vm.jobNumber.collectAsState()
    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        runCatching {
            if (uri != null) {
                VerboseLog.d(VerboseLog.Category.IO, "SafRoutes") { "save picked uri=$uri" }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val json = vm.exportJson()
                    VerboseLog.d(VerboseLog.Category.IO, "SafRoutes") { "save write chars=${json.length}" }
                    out.writer().use { w -> w.write(json) }
                }
            }
        }
        done.value = true
    }

    LaunchedEffect(Unit) {
        saver.launch(
            defaultJsonFileName(
                jobNumber = jobNumber,
                customer = customer,
                vessel = vessel,
                shaftPosition = shaftPosition
            )
        )
    }
    if (done.value) onFinished() else Text("")
}

/* ── helpers ─────────────────────────────────────────────────────────────── */

private fun defaultJsonFileName(
    jobNumber: String,
    customer: String,
    vessel: String,
    shaftPosition: ShaftPosition
): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val suggested = DocumentNaming.suggestedBaseName(
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        suffix = shaftPosition.printableLabelOrNull()
    )
    return (suggested ?: "Shaft_$stamp") + ".json"
}
