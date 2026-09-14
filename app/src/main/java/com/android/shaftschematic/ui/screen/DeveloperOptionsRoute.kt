package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.BuildConfig
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.CrashReporter
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.*
import kotlinx.coroutines.launch

/** How many breadcrumbs the on-device viewer shows; the files themselves hold far more. */
private const val LOG_TAIL_LINES = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
) {
    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()
    val showOalDebugLabel by vm.showOalDebugLabel.collectAsState()
    val showOalInPreviewBox by vm.showOalInPreviewBox.collectAsState()

    val showComponentDebugLabels by vm.showComponentDebugLabels.collectAsState()
    val showRenderLayoutDebugOverlay by vm.showRenderLayoutDebugOverlay.collectAsState()
    val showRenderOalMarkers by vm.showRenderOalMarkers.collectAsState()
    val showDimDebugOverlay by vm.showDimDebugOverlay.collectAsState()
    val verboseLoggingEnabled by vm.verboseLoggingEnabled.collectAsState()

    val verboseLoggingRender by vm.verboseLoggingRender.collectAsState()
    val verboseLoggingOal by vm.verboseLoggingOal.collectAsState()
    val verboseLoggingPdf by vm.verboseLoggingPdf.collectAsState()
    val verboseLoggingIo by vm.verboseLoggingIo.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmCrash by remember { mutableStateOf(false) }
    var logTail by remember { mutableStateOf<List<String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Options") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DevSwitchRow(
                label = "Enable Developer Options",
                supporting = "Turning this off hides this screen and clears every switch below.",
                checked = devOptionsEnabled,
                onCheckedChange = {
                    if (!it) {
                        vm.setDevOptionsEnabled(false)
                        onBack()
                    }
                },
                testTag = "dev_master_switch",
            )

            HorizontalDivider()

            Text("Debug overlays", style = MaterialTheme.typography.titleMedium)

            DevSwitchRow(
                label = "OAL debug label",
                supporting = "Physical / effective / covered length under the OAL field.",
                checked = showOalDebugLabel,
                onCheckedChange = { vm.setShowOalDebugLabel(it) },
            )

            DevSwitchRow(
                label = "OAL badge in preview box",
                supporting = "Measured span badge over the top-left of the preview.",
                checked = showOalInPreviewBox,
                onCheckedChange = { vm.setShowOalInPreviewBox(it) },
            )

            DevSwitchRow(
                label = "Component debug labels",
                supporting = "Resolved id and start/end mm on every carousel card.",
                checked = showComponentDebugLabels,
                onCheckedChange = { vm.setShowComponentDebugLabels(it) },
            )

            DevSwitchRow(
                label = "Render layout overlay",
                supporting = "Layout metrics plus the preview's own zoom and pan.",
                checked = showRenderLayoutDebugOverlay,
                onCheckedChange = { vm.setShowRenderLayoutDebugOverlay(it) },
            )

            DevSwitchRow(
                label = "Render OAL markers",
                supporting = "Vertical rules at the measured OAL window's two ends.",
                checked = showRenderOalMarkers,
                onCheckedChange = { vm.setShowRenderOalMarkers(it) },
            )

            DevSwitchRow(
                label = "Dimension debug overlay",
                supporting = "Tier origin rule and the liner spans the PDF would dimension.",
                checked = showDimDebugOverlay,
                onCheckedChange = { vm.setShowDimDebugOverlay(it) },
            )

            HorizontalDivider()

            Text("Verbose logging", style = MaterialTheme.typography.titleMedium)
            Text(
                "Goes to logcat only — a device with a cable attached. Breadcrumbs a tester " +
                    "can send back are always on and live under Diagnostics below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DevSwitchRow(
                label = "Enable verbose logging",
                supporting = "Master switch — every category below is silent without it.",
                checked = verboseLoggingEnabled,
                onCheckedChange = { vm.setVerboseLoggingEnabled(it) },
            )

            // Disabled rather than hidden: a category left on stays on, and a switch that
            // vanishes when its master goes off reads as a setting that was lost.
            DevSwitchRow(
                label = "Render / layout",
                supporting = "Preview layout metrics, once per change.",
                checked = verboseLoggingRender,
                onCheckedChange = { vm.setVerboseLoggingRender(it) },
                enabled = verboseLoggingEnabled,
            )

            DevSwitchRow(
                label = "OAL / threads",
                supporting = "Measured OAL window as the preview redraws it.",
                checked = verboseLoggingOal,
                onCheckedChange = { vm.setVerboseLoggingOal(it) },
                enabled = verboseLoggingEnabled,
            )

            DevSwitchRow(
                label = "PDF export",
                supporting = "Composer and print pipeline steps.",
                checked = verboseLoggingPdf,
                onCheckedChange = { vm.setVerboseLoggingPdf(it) },
                enabled = verboseLoggingEnabled,
            )

            DevSwitchRow(
                label = "Storage / SAF",
                supporting = "Document saves, the backup mirror, and template storage.",
                checked = verboseLoggingIo,
                onCheckedChange = { vm.setVerboseLoggingIo(it) },
                enabled = verboseLoggingEnabled,
            )

            HorizontalDivider()

            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)

            ListItem(
                headlineContent = { Text("Build") },
                supportingContent = {
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • " +
                            "${BuildConfig.GIT_SHA} • ${BuildConfig.BUILD_TYPE}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("dev_build_row"),
            )

            ListItem(
                headlineContent = { Text("Crash reporting") },
                supportingContent = {
                    Text(
                        if (CrashReporter.isActive) {
                            "Active — crashes and recorded non-fatals reach Crashlytics."
                        } else {
                            "Inactive — this build shipped without a Firebase configuration."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("dev_crash_status_row"),
            )

            OutlinedButton(
                onClick = {
                    AppLog.i("DevOptions", "test non-fatal recorded")
                    CrashReporter.recordNonFatal(
                        RuntimeException("Developer Options test non-fatal")
                    )
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            if (CrashReporter.isActive) "Test non-fatal sent to Crashlytics."
                            else "Crash reporting is inactive — breadcrumb written only."
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("dev_test_nonfatal"),
            ) { Text("Record test non-fatal") }

            OutlinedButton(
                onClick = { logTail = AppLog.tail(LOG_TAIL_LINES) },
                modifier = Modifier.fillMaxWidth().testTag("dev_view_breadcrumbs"),
            ) { Text("View recent breadcrumbs") }

            // Last in the section: the one control here that ends the process.
            OutlinedButton(
                onClick = { confirmCrash = true },
                modifier = Modifier.fillMaxWidth().testTag("dev_force_crash"),
            ) { Text("Force a test crash") }

            HorizontalDivider()
        }
    }

    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            title = { Text("Crash the app?") },
            text = {
                Text(
                    "Ends the process on purpose, so the crash handler and Crashlytics can be " +
                        "verified end to end. Unsaved work in the open document is lost."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCrash = false
                        AppLog.w("DevOptions", "forced test crash")
                        throw RuntimeException("Developer Options forced test crash")
                    },
                    modifier = Modifier.testTag("dev_force_crash_confirm"),
                ) { Text("Crash") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCrash = false }) { Text("Cancel") }
            },
        )
    }

    logTail?.let { lines ->
        AlertDialog(
            onDismissRequest = { logTail = null },
            title = { Text("Recent breadcrumbs") },
            text = {
                if (lines.isEmpty()) {
                    Text("No breadcrumbs yet on this device.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        lines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { logTail = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun DevSwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(label)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
