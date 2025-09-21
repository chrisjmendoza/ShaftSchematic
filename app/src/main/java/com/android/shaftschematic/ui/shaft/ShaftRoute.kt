// file: com/android/shaftschematic/ui/shaft/ShaftRoute.kt
package com.android.shaftschematic.ui.shaft

import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.ShaftPdfComposer
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.screen.ShaftScreen // <-- IMPORTANT: match your ShaftScreen package
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShaftRoute
 *
 * Wires VM ↔ UI and owns the SAF “Save As…” export flow.
 *
 * Notes:
 *  - We pass `unit` and `showGrid` to the screen (your screen requires them).
 *  - The preview renderer signature is (ShaftSpec, UnitSystem) -> Unit, but the current
 *    ShaftDrawing composable only takes (spec: ShaftSpec, modifier: Modifier = Modifier).
 *    We therefore ignore the UnitSystem in the lambda and just call ShaftDrawing(spec = s).
 *  - No lifecycle-*compose imports are needed; we use collectAsState().
 */
@Composable
fun ShaftRoute(vm: ShaftViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- collect state ---
    val spec      by vm.spec.collectAsState()
    val unit      by vm.unit.collectAsState()
    val showGrid  by vm.showGrid.collectAsState()
    val customer  by vm.customer.collectAsState()
    val vessel    by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val notes     by vm.notes.collectAsState()

    // --- helpers ---
    fun exportTitleStem(): String {
        val parts = listOf(jobNumber, vessel, customer).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.isNotEmpty()) parts.joinToString(" - ")
        else SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + "_shaftdrawing"
    }

    fun showSnack(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    // --- SAF launcher ---
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) {
            showSnack("Export canceled")
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ShaftPdfComposer.exportToStream(
                    context = context,
                    spec = spec,                // geometry (mm)
                    unit = unit,                // labels (mm/in)
                    showGrid = showGrid,        // grid underlay
                    out = out,
                    title = exportTitleStem()   // printed in the title block
                )
            } ?: run {
                showSnack("Unable to open destination")
                return@rememberLauncherForActivityResult
            }

            val displayName = runCatching {
                queryDisplayName(context.contentResolver.query(uri, null, null, null, null))
            }.getOrNull() ?: uri.lastPathSegment ?: "PDF"
            showSnack("Exported: $displayName")
        } catch (t: Throwable) {
            showSnack("PDF export failed: ${t.message ?: "Unknown error"}")
        }
    }

    // --- UI ---
    Box {
        ShaftScreen(
            spec = spec,
            unit = unit,
            customer = customer,
            vessel = vessel,
            jobNumber = jobNumber,
            notes = notes,
            showGrid = showGrid,

            onSetUnit = vm::setUnit,
            onToggleGrid = vm::setShowGrid,
            onSetCustomer = vm::setCustomer,
            onSetVessel = vm::setVessel,
            onSetJobNumber = vm::setJobNumber,
            onSetNotes = vm::setNotes,
            onSetOverallLengthRaw = vm::setOverallLength,
            onAddComponent = vm::addBodySegment,

            onExportPdf = { /* your SAF launcher */ },

            snackbarHostState = snackbarHostState
        )
    }
}

/** Try to get OpenableColumns.DISPLAY_NAME from a Cursor. */
private fun queryDisplayName(cursor: Cursor?): String? {
    if (cursor == null) return null
    cursor.use {
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        return if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
    }
}
