// file: app/src/main/java/com/android/shaftschematic/ui/screen/StartScreen.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * StartScreen
 *
 * Purpose
 * Simple hub with New, Open (internal), and Settings.
 *
 * Contract
 * - Emits navigation intents only.
 * - Does not touch ViewModel or storage directly.
 */
@Composable
fun StartScreen(
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onSendFeedback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ShaftSchematic", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New drawing") }
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open…") }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        OutlinedButton(onClick = onSendFeedback, modifier = Modifier.fillMaxWidth()) { Text("Send feedback") }
    }
}
