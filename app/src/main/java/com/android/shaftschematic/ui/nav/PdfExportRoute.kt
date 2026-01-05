package com.android.shaftschematic.ui.nav

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.Achievements
import com.android.shaftschematic.util.VerboseLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Route that exports the current shaft as a single-page PDF via SAF (“Create Document”).
 * Units are mm in the model; the composer handles unit labels.
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

    val customer by vm.customer.collectAsState()
    val vessel by vm.vessel.collectAsState()
    val jobNumber by vm.jobNumber.collectAsState()
    val shaftPosition by vm.shaftPosition.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        runCatching {
            var wrotePdf = false
            if (uri != null) {
                VerboseLog.d(VerboseLog.Category.IO, "PdfExport") { "picked uri=$uri" }
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    try {
                        // US Letter (pt): 792×612 landscape
                        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                        val page = doc.startPage(pageInfo)

                        val filename = uri.lastPathSegment ?: defaultFilename()
                        val project = ProjectInfo(
                            customer = customer,
                            vessel = vessel,
                            side = shaftPosition,
                            jobNumber = jobNumber
                        )

                        VerboseLog.d(VerboseLog.Category.PDF, "PdfExport") {
                            "writing: page=${pageInfo.pageWidth}x${pageInfo.pageHeight}pt filename=$filename"
                        }

                        val exportError = try {
                            composeShaftPdf(
                                page = page,
                                spec = vm.spec.value,
                                unit = vm.unit.value,
                                project = project,
                                appVersion = appVersionFromContext(ctx),
                                filename = filename
                            )
                            null
                        } catch (t: Throwable) {
                            // Never leave a truncated/unopenable PDF behind.
                            // Write a valid error page so the output opens and the user can see what failed.
                            val canvas = page.canvas
                            canvas.drawColor(Color.WHITE)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.BLACK
                                textSize = 14f
                            }
                            val header = "PDF export failed"
                            val detail = "${t.javaClass.simpleName}: ${t.message ?: "(no message)"}"
                            canvas.drawText(header, 48f, 96f, paint)
                            canvas.drawText(detail, 48f, 120f, paint)
                            t
                        }

                        doc.finishPage(page)
                        doc.writeTo(out)

                        wrotePdf = (exportError == null)
                        if (exportError != null) throw exportError
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
            }

            if (wrotePdf) {
                VerboseLog.i(VerboseLog.Category.PDF, "PdfExport") { "write complete" }
                vm.unlockAchievement(Achievements.Id.FIRST_PDF)
            }
        }.onFailure {
            VerboseLog.e(VerboseLog.Category.PDF, "PdfExport") { "failed: ${it.javaClass.simpleName}: ${it.message}" }
        }
        finished = true
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            launcher.launch(defaultFilename())
        }
    }

    if (finished) {
        onFinished()
    } else {
        Text("") // keep composition alive while the picker is open
    }
}

private fun defaultFilename(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "Shaft_$stamp.pdf"
}

private fun appVersionFromContext(context: Context): String {
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
