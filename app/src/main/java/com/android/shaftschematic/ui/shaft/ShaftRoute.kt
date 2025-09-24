// file: com/android/shaftschematic/ui/shaft/ShaftRoute.kt
package com.android.shaftschematic.ui.shaft

import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.android.shaftschematic.pdf.ShaftPdfComposer
import com.android.shaftschematic.ui.drawing.compose.ShaftDrawing
import com.android.shaftschematic.ui.screen.ShaftScreen
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShaftRoute
 *
 * Wires ViewModel ↔ UI, hosts Snackbar state, and implements SAF “Save As…” export.
 * - File name + title block order: Job – Vessel – Customer (fallback: timestamp_shaftdrawing.pdf)
 * - FAB opens add flow in ShaftScreen; typed callbacks here append components via VM.
 * - No lifecycle-*compose dependency; uses collectAsState().
 */
@Composable
fun ShaftRoute(vm: ShaftViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- VM state ---
    val spec      by vm.spec.collectAsState()
    val unit      by vm.unit.collectAsState()
    val showGrid  by vm.showGrid.collectAsState()
    val customer  by vm.customer.collectAsState()
    val vessel    by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val notes     by vm.notes.collectAsState()

    /** Build human title/filename stem in Job → Vessel → Customer order. */
    fun exportTitleStem(): String {
        val parts = listOf(jobNumber, vessel, customer).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.isNotEmpty()) parts.joinToString(" - ")
        else SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + "_shaftdrawing"
    }

    fun showSnack(msg: String) = scope.launch { snackbarHostState.showSnackbar(msg) }

    // SAF launcher (CreateDocument → we write to returned Uri)
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
                    spec = spec,         // geometry (mm)
                    unit = unit,         // labels (mm/in)
                    showGrid = showGrid, // grid underlay
                    out = out,
                    title = exportTitleStem()
                )
            } ?: run {
                showSnack("Unable to open destination")
                return@rememberLauncherForActivityResult
            }

            // Best-effort: show the saved display name
            val displayName = runCatching {
                context.contentResolver.query(uri, null, null, null, null).use(::queryDisplayName)
            }.getOrNull() ?: uri.lastPathSegment ?: "PDF"
            showSnack("Exported: $displayName")
        } catch (t: Throwable) {
            showSnack("PDF export failed: ${t.message ?: "Unknown error"}")
        }
    }

    Box {
        ShaftScreen(
            // --- state ---
            spec = spec,
            unit = unit,
            customer = customer,
            vessel = vessel,
            jobNumber = jobNumber,
            notes = notes,
            showGrid = showGrid,

            // --- setters ---
            onSetUnit = vm::setUnit,
            onToggleGrid = vm::setShowGrid,
            onSetCustomer = vm::setCustomer,
            onSetVessel = vm::setVessel,
            onSetJobNumber = vm::setJobNumber,
            onSetNotes = vm::setNotes,
            onSetOverallLengthRaw = vm::setOverallLength,

            // --- typed add callbacks (FAB flow in screen triggers these) ---
            onAddBody = { startMm, lengthMm, diaMm ->
                vm.addBodyAt(startMm = startMm, lengthMm = lengthMm, diaMm = diaMm)
                showSnack("Body @ ${startMm.toInt()} mm: ${lengthMm.toInt()} × Ø ${diaMm.toInt()}")
            },
            onAddTaper = { startMm, lengthMm, startDiaMm, endDiaMm ->
                vm.addTaperAt(startMm = startMm, lengthMm = lengthMm, startDiaMm = startDiaMm, endDiaMm = endDiaMm)
                showSnack("Taper @ ${startMm.toInt()} mm: ${lengthMm.toInt()} (${startDiaMm.toInt()}→${endDiaMm.toInt()} mm)")
            },
            onAddThread = { startMm, lengthMm, majorDiaMm, pitchMm ->
                vm.addThreadAt(startMm = startMm, lengthMm = lengthMm, majorDiaMm = majorDiaMm, pitchMm = pitchMm)
                showSnack("Thread @ ${startMm.toInt()} mm: ${lengthMm.toInt()} mm, Ø ${majorDiaMm.toInt()} mm, pitch ${"%.2f".format(pitchMm)} mm")
            },
            onAddLiner = { startMm, lengthMm, odMm ->
                vm.addLinerAt(startMm = startMm, lengthMm = lengthMm, odMm = odMm)
                showSnack("Liner @ ${startMm.toInt()} mm: ${lengthMm.toInt()} × Ø ${odMm.toInt()}")
            },

            onUpdateBody   = { i, s, l, d -> vm.updateBody(i, s, l, d) },
            onUpdateTaper  = { i, s, l, sd, ed -> vm.updateTaper(i, s, l, sd, ed) },
            onUpdateThread = { i, s, l, maj, p -> vm.updateThread(i, s, l, maj, p) },
            onUpdateLiner  = { i, s, l, od -> vm.updateLiner(i, s, l, od) },

            // --- PDF export ---
            onExportPdf = {
                val suggested = exportTitleStem()
                    .replace(Regex("\\s+"), " ")
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .plus(".pdf")
                createPdfLauncher.launch(suggested)
            },

            // --- preview renderer injection (unit + grid-aware) ---
            renderShaft = { s, u ->
                // Fill the preview area; let the UI’s GridCanvas handle the grid
                ShaftDrawing(
                    spec = s,
                    unit = u,
                    showGrid = false,                // prevent double-grid
                )
            },

            // Snackbar host lives in the Scaffold
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
