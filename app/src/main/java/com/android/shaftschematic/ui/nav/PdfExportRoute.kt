package com.android.shaftschematic.ui.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.pdf.PdfExportOptions
import com.android.shaftschematic.ui.order.ComponentKind
import com.android.shaftschematic.ui.util.startOverlapErrorMm
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.unlockAchievement
import com.android.shaftschematic.util.Achievements
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.util.buildOpenPdfIntent
import com.android.shaftschematic.util.PDF_PAGE_HEIGHT_PT
import com.android.shaftschematic.util.PDF_PAGE_WIDTH_PT
import com.android.shaftschematic.util.VerboseLog
import com.android.shaftschematic.util.writeShaftPdfToUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Route that exports the current shaft as a single-page PDF via SAF (“Create Document”).
 *
 * Notes for contributors
 * - Units: model geometry is always mm; the PDF composer handles unit labels/formatting.
 * - Robustness: the write goes through `util/PdfSafExport.writeShaftPdfToUri` — the one
 *   hardened SAF path shared by every export surface. A composer throw still writes a
 *   valid PDF carrying an error page, never a truncated/unopenable file, and returns
 *   false so the success-only follow-ups (achievement, auto-open) are skipped.
 * - UX: the export flow may optionally open the resulting document in an external PDF
 *   viewer after a successful write; this is separate from PDF content/styling.
 */
@Composable
fun PdfExportRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    var launched by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var blockingErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val openAfterExport by vm.openPdfAfterExport.collectAsState()
    val pdfExportMode by vm.pdfExportMode.collectAsState()
    val pdfBlankDraft by vm.pdfBlankDraft.collectAsState()
    val pdfBlankDiaCallouts by vm.pdfBlankDiaCallouts.collectAsState()

    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            VerboseLog.d(VerboseLog.Category.IO, "PdfExport") { "picked uri=$uri" }

            val filename = uri.lastPathSegment
                ?: defaultFilename(
                    jobNumber = jobNumber,
                    customer = customer,
                    vessel = vessel,
                    shaftPosition = shaftPosition,
                    blankDraft = pdfBlankDraft
                )
            val project = ProjectInfo(
                customer = customer,
                vessel = vessel,
                side = shaftPosition,
                jobNumber = jobNumber
            )

            VerboseLog.d(VerboseLog.Category.PDF, "PdfExport") {
                "writing: page=${PDF_PAGE_WIDTH_PT}x${PDF_PAGE_HEIGHT_PT}pt filename=$filename"
            }

            // Hardened write: a composer throw yields a valid error page, never a
            // truncated file (util/PdfSafExport.kt — one implementation for every tab).
            val wrote = writeShaftPdfToUri(ctx, uri) { page ->
                composeShaftPdf(
                    page = page,
                    spec = vm.spec.value,
                    unit = vm.unit.value,
                    project = project,
                    appVersion = appVersionFromContext(ctx),
                    filename = filename,
                    pdfPrefs = vm.currentPdfPrefs,
                    options = PdfExportOptions(
                        mode = pdfExportMode,
                        blankValues = pdfBlankDraft,
                        blankDiaCallouts = pdfBlankDiaCallouts,
                    ),
                    resolvedComponents = vm.resolvedComponents.value,
                    lineThicknessScale = vm.lineThicknessScale.value,
                    heightScale = vm.runoutConfig.value.heightScale,
                    linerMinFracOfTrue = vm.runoutConfig.value.linerMinFracOfTrue,
                    displayUnits = vm.currentDisplayUnits(),
                )
            }

            if (wrote) {
                VerboseLog.i(VerboseLog.Category.PDF, "PdfExport") { "write complete" }
                vm.unlockAchievement(Achievements.Id.FIRST_PDF)

                if (openAfterExport) openPdf(ctx, uri)
            } else {
                VerboseLog.e(VerboseLog.Category.PDF, "PdfExport") { "failed: error page written to $uri" }
            }
        }
        finished = true
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            val error = blockingExportError(vm.spec.value)
            if (error != null) {
                blockingErrorMessage = error
            } else {
                launcher.launch(
                    defaultFilename(
                        jobNumber = jobNumber,
                        customer = customer,
                        vessel = vessel,
                        shaftPosition = shaftPosition,
                        blankDraft = pdfBlankDraft
                    )
                )
            }
        }
    }

    blockingErrorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { finished = true },
            title = { Text("Cannot export PDF") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { finished = true }) { Text("OK") }
            }
        )
    }

    if (finished) {
        onFinished()
    } else if (blockingErrorMessage == null) {
        Text("") // keep composition alive while the picker is open
    }
}

/**
 * The single export gate: returns the reason this spec may not be exported, or `null` when it
 * may. Every checked condition reuses the validation the carousel already shows on a card, so a
 * spec that exports is exactly a spec with no blocking badge.
 */
internal fun blockingExportError(spec: ShaftSpec): String? {
    // Excluded threads sit outside the shaft envelope (negative or OAL+ start); skip them.
    // `startOverlapErrorMm` contributes the start ≥ 0 guard, same-kind overlaps, and the
    // thread-at-a-shaft-end rule; for tapers it contributes only the start guard (tapers
    // have no collision group there).
    spec.threads.filter { !it.excludeFromOAL }.forEach { th ->
        startOverlapErrorMm(spec, th.id, ComponentKind.THREAD, th.lengthMm, th.startFromAftMm)
            ?.let { return it }
    }
    spec.liners.forEach { ln ->
        startOverlapErrorMm(spec, ln.id, ComponentKind.LINER, ln.lengthMm, ln.startFromAftMm)
            ?.let { return it }
    }
    // `startFromAftMm` is canonical mm from the AFT face for every taper; `authoredReference`
    // is display metadata and never moves the stored position.
    spec.tapers.forEach { t ->
        startOverlapErrorMm(spec, t.id, ComponentKind.TAPER, t.lengthMm, t.startFromAftMm)
            ?.let { return it }
    }
    // Every sacred overlap pair (Taper↔Taper/Thread/Liner, Thread↔Thread/Liner, Liner↔Liner)
    // gates through `collidingIds()` — the very predicate behind every card's blocking badge
    // and the toolbar `exportPdfGate()`, so the gate and both of those surfaces cannot
    // disagree. `startOverlapErrorMm` compares each kind only against its own kind, so the
    // cross-kind pairs (Thread↔Liner included) MUST come from this pass — dropping it would
    // let a cross-kind overlap export. Bodies are fillers, never collision participants: a
    // taper or liner crossing a stored body span is normal (the resolve layer trims the drawn
    // body around it), and `collidingIds()` already excludes bodies — so a taper over a body
    // must not, and does not, block export.
    if (spec.collidingIds().isNotEmpty()) return "Overlaps another component"
    return null
}

private fun openPdf(context: Context, uri: Uri) {
    val intent = buildOpenPdfIntent(context, uri)

    // Proactively grant read permission to all potential handlers.
    val handlers = context.packageManager.queryIntentActivities(intent, 0)
    handlers.forEach { ri ->
        val pkg = ri.activityInfo?.packageName ?: return@forEach
        runCatching {
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    } catch (_: ActivityNotFoundException) {
        // No PDF viewer installed.
    }
}

private fun defaultFilename(
    jobNumber: String,
    customer: String,
    vessel: String,
    shaftPosition: ShaftPosition,
    blankDraft: Boolean = false
): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val suggested = DocumentNaming.suggestedBaseName(
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        suffix = shaftPosition.printableLabelOrNull()
    )
    val blankSuffix = if (blankDraft) "_BlankDraft" else ""
    return (suggested ?: "Shaft_$stamp") + blankSuffix + ".pdf"
}

/**
 * App version string stamped into the schematic PDF footer. Shared with the Runout tab's
 * Schematic output so both surfaces print the same footer.
 */
internal fun appVersionFromContext(context: Context): String {
    return try {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "0"
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName ?: "0"
        }
    } catch (_: Throwable) {
        "0"
    }
}
