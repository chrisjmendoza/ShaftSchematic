package com.android.shaftschematic.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
# InternalDocRoutes – open/save JSON *inside app storage*
 **Purpose**: UI for listing and saving JSON drawings stored **internally** (app sandbox).
 **Contract**
- No SAF here. Uses [InternalStorage] (app-private files dir).
- Emits navigation completion via [onFinished].
- ViewModel owns JSON shape/version (importJson/exportJson).
- Names are unique to avoid conflicts with SAF routes.
 */

/* ───────────────────── OPEN (from app storage) ───────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenLocalDocumentRoute(               // ← renamed (no clash with SAF)
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(listOf<String>()) }

    // Load list once on enter (keeps behavior the same as your original)
    LaunchedEffect(Unit) { files = InternalStorage.list(ctx) }

    Scaffold(topBar = { TopAppBar(title = { Text("Open drawing") }) }) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
        ) {
            if (files.isEmpty()) {
                item { Text("No saved drawings yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(files) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) {
                                        InternalStorage.load(ctx, name)
                                    }
                                    vm.importJson(text)
                                    onFinished()
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name.removeSuffix(".json"))
                        Text("Open", color = MaterialTheme.colorScheme.primary)
                    }
                    Divider()
                }
            }
        }
    }
}

/* ───────────────────── SAVE (to app storage) ───────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveLocalDocumentRoute(               // ← renamed (no clash with SAF)
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember {
        val default = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        mutableStateOf(TextFieldValue("Shaft_$default.json"))
    }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Save drawing") }) }) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("File name (.json)") },
                isError = error != null,
                supportingText = { if (error != null) Text(error!!) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val n = name.text.trim()
                    when {
                        n.isEmpty() -> error = "Enter a file name."
                        !n.endsWith(".json", ignoreCase = true) -> error = "Name must end with .json"
                        else -> scope.launch {
                            withContext(Dispatchers.IO) {
                                InternalStorage.save(ctx, n, vm.exportJson())
                            }
                            onFinished()
                        }
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = onFinished) { Text("Cancel") }
            }
        }
    }
}
