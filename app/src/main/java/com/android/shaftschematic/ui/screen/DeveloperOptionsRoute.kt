package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
) {
    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()
    val showOalDebugLabel by vm.showOalDebugLabel.collectAsState()
    val showOalHelperLine by vm.showOalHelperLine.collectAsState()

    val showComponentDebugLabels by vm.showComponentDebugLabels.collectAsState()
    val showRenderLayoutDebugOverlay by vm.showRenderLayoutDebugOverlay.collectAsState()
    val showRenderOalMarkers by vm.showRenderOalMarkers.collectAsState()
    val verboseLoggingEnabled by vm.verboseLoggingEnabled.collectAsState()

    val verboseLoggingRender by vm.verboseLoggingRender.collectAsState()
    val verboseLoggingOal by vm.verboseLoggingOal.collectAsState()
    val verboseLoggingPdf by vm.verboseLoggingPdf.collectAsState()
    val verboseLoggingIo by vm.verboseLoggingIo.collectAsState()

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
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Debug", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = devOptionsEnabled,
                    onCheckedChange = {
                        if (!it) {
                            vm.setDevOptionsEnabled(false)
                            onBack()
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable Developer Options")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = showOalDebugLabel,
                    onCheckedChange = { vm.setShowOalDebugLabel(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Show OAL Debug Label")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = showOalHelperLine,
                    onCheckedChange = { vm.setShowOalHelperLine(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Show OAL Helper Line")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = showComponentDebugLabels,
                    onCheckedChange = { vm.setShowComponentDebugLabels(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Show Component Debug Labels")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = showRenderLayoutDebugOverlay,
                    onCheckedChange = { vm.setShowRenderLayoutDebugOverlay(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Show Render Layout Debug Overlay")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = showRenderOalMarkers,
                    onCheckedChange = { vm.setShowRenderOalMarkers(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Show Render OAL Markers")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = verboseLoggingEnabled,
                    onCheckedChange = { vm.setVerboseLoggingEnabled(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Enable Verbose Logging")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = verboseLoggingRender,
                    onCheckedChange = { vm.setVerboseLoggingRender(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Verbose: Render/Layout")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = verboseLoggingOal,
                    onCheckedChange = { vm.setVerboseLoggingOal(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Verbose: OAL/Threads")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = verboseLoggingPdf,
                    onCheckedChange = { vm.setVerboseLoggingPdf(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Verbose: PDF Export")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = verboseLoggingIo,
                    onCheckedChange = { vm.setVerboseLoggingIo(it) }
                )
                Spacer(Modifier.width(8.dp))
                Text("Verbose: Storage/SAF")
            }

            HorizontalDivider()
        }
    }
}
