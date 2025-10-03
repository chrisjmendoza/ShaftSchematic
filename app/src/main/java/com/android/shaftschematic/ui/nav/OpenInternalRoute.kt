// app/src/main/java/com/android/shaftschematic/ui/nav/InternalDocRoutes.kt
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * OpenInternalRoute
 *
 * Purpose
 * Lists internal JSON drawings. Tap to load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenInternalRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val files by remember { mutableStateOf(InternalStorage.list(ctx)) }

    Scaffold(topBar = { TopAppBar(title = { Text("Open drawing") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp)) {
            if (files.isEmpty()) {
                item { Text("No saved drawings yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(files) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) { InternalStorage.load(ctx, name) }
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

/**
 * SaveInternalRoute
 *
 * Purpose
 * “Save As” UI for internal JSON drawings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveInternalRoute(
    nav: NavController,
    vm: ShaftViewModel,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember {
        val default = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        mutableStateOf(TextFieldValue("Shaft_$default.json"))
    }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Save drawing") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("File name (.json)") },
                isError = error != null,
                supportingText = { if (error != null) Text(error!!) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val n = name.text.trim()
                    when {
                        n.isEmpty() -> error = "Enter a file name."
                        !n.endsWith(".json") -> error = "Name must end with .json"
                        else -> scope.launch {
                            withContext(Dispatchers.IO) { InternalStorage.save(ctx, n, vm.exportJson()) }
                            onFinished()
                        }
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = onFinished) { Text("Cancel") }
            }
        }
    }
}
