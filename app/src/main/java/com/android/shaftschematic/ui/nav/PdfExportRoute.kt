// app/src/main/java/com/android/shaftschematic/ui/nav/PdfExportRoute.kt
package com.android.shaftschematic.ui.nav

import android.content.Context
import android.content.pm.PackageManager
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
import com.android.shaftschematic.pdf.composeShaftPdf
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Route that exports the current shaft as a single-page PDF via SAF (“Create Document”).
 *
 * Units: **mm** (millimeters) in the underlying model. The composer handles unit labels.
 *
 * Behavior:
 * - Launches the system save dialog once.
 * - If a URI is chosen, writes a Letter-landscape page via [composeShaftPdf].
 * - Calls [onFinished] after success or cancel so the caller can navigate away.
 *
 * See also: `/docs/PdfExportRoute.md` for the longer contract and future options.
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

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        runCatching {
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = PdfDocument()
                    try {
                        // US Letter landscape: 792×612 pt (1 pt = 1/72")
                        val pageInfo = PdfDocument.PageInfo.Builder(792, 612, 1).create()
                        val page = doc.startPage(pageInfo)

                        val filename = uri.lastPathSegment ?: defaultFilename()
                        val project = ProjectInfo(customer = "", vessel = "", jobNumber = "")

                        composeShaftPdf(
                            page = page,
                            spec = vm.spec.value,
                            unit = vm.unit.value,
                            project = project,
                            appVersion = appVersionFromContext(ctx),
                            filename = filename
                        )

                        doc.finishPage(page)
                        doc.writeTo(out)
                    } finally {
                        try { out.flush() } catch (_: Throwable) {}
                        doc.close()
                    }
                }
            }
        }.onFailure {
            // Optional: surface a snackbar or log.
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

/** Default export filename suggestion. */
private fun defaultFilename(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "Shaft_$stamp.pdf"
}

/** Obtain app versionName without BuildConfig coupling. */
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
