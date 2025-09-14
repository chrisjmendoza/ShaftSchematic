package com.android.shaftschematic.ui.input

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ShaftMetaSection(
    customer: String,
    onCustomerChange: (String) -> Unit,
    vessel: String,
    onVesselChange: (String) -> Unit,
    jobNumber: String,
    onJobNumberChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Job / Notes", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = customer,
            onValueChange = onCustomerChange,
            label = { Text("Customer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = vessel,
            onValueChange = onVesselChange,
            label = { Text("Vessel") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = jobNumber,
            onValueChange = onJobNumberChange,
            label = { Text("Job Number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { if (it.length <= 2000) onNotesChange(it) },
            label = { Text("Notes (optional)") },
            placeholder = { Text("Anything extra about this shaft…") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines = 8
        )

        // Tiny helper line
        val count = notes.length
        AssistChip(
            onClick = { /* no-op */ },
            label = { Text("Notes length: $count / 2000") }
        )
    }
}
