package com.android.shaftschematic.ui.screen

/**
 * TemplatesRoute — the template browser.
 *
 * Templates are grouped by **liner size**, then by **liner count**, then listed as cards with
 * a drawn preview of the shaft. Both grouping keys are derived from each template's stored
 * spec at scan time (`template/TemplateBuckets.kt`), so nothing here can drift out of sync
 * with the files on disk.
 *
 * Empty buckets are not shown — a browser of 27 empty rows is worse than a short list.
 *
 * Picking a template is a session-replacing action: the caller routes it through the same
 * unsaved-changes guard as New/Open before it reaches `ShaftViewModel.applyTemplate`.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.io.TemplateStorage
import com.android.shaftschematic.model.coverageEndMm
import com.android.shaftschematic.template.TemplateLinerCount
import com.android.shaftschematic.template.TemplateSizeBucket
import com.android.shaftschematic.template.sortKey
import com.android.shaftschematic.ui.drawing.compose.ShaftThumbnail
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
    /** Load this template's JSON into a new document. Guarded for unsaved work by the caller. */
    onUseTemplate: (raw: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val unit by vm.unit.collectAsState()

    var summaries by remember { mutableStateOf<List<TemplateStorage.TemplateSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadNonce by remember { mutableStateOf(0) }

    LaunchedEffect(reloadNonce) {
        summaries = withContext(Dispatchers.IO) {
            // First-run seeding is kicked off asynchronously at app startup; a browser opened
            // before it lands would read an empty store and claim "No templates yet".
            // Awaiting the (idempotent, flag-gated) seeder here closes that race and costs
            // one DataStore read on every later open.
            runCatching { TemplateStorage.seedStarterTemplatesIfNeeded(ctx, SettingsStore) }
            TemplateStorage.summaries(ctx)
        }
        loading = false
    }

    var pendingDelete by remember { mutableStateOf<TemplateStorage.TemplateSummary?>(null) }
    var pendingRename by remember { mutableStateOf<TemplateStorage.TemplateSummary?>(null) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this template?") },
            text = { Text("‘${target.displayName}’ will be removed. Drawings already made from it are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { TemplateStorage.delete(ctx, target.filename) }
                        reloadNonce++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    pendingRename?.let { target ->
        var text by remember(target.filename) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename template") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Template name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val to = TemplateStorage.normalizeTemplateName(text)
                        pendingRename = null
                        scope.launch {
                            if (to == null) {
                                snackbarHostState.showSnackbar("Enter a template name")
                                return@launch
                            }
                            val result = withContext(Dispatchers.IO) {
                                TemplateStorage.rename(ctx, target.filename, to)
                            }
                            reloadNonce++
                            when (result) {
                                TemplateStorage.RenameResult.OK -> Unit
                                TemplateStorage.RenameResult.SOURCE_MISSING ->
                                    snackbarHostState.showSnackbar("That template no longer exists")
                                TemplateStorage.RenameResult.TARGET_EXISTS ->
                                    snackbarHostState.showSnackbar("That name is already taken")
                                TemplateStorage.RenameResult.IO_ERROR ->
                                    snackbarHostState.showSnackbar("Could not rename the template")
                            }
                        }
                    },
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                loading -> Unit
                summaries.isEmpty() -> EmptyTemplatesMessage()
                else -> TemplateBrowserList(
                    summaries = summaries,
                    unit = unit,
                    onUse = { summary ->
                        scope.launch {
                            // Decode here as well as at scan time: a file replaced or
                            // truncated on disk in between would otherwise reach
                            // applyTemplate, which rethrows — a crash where a snackbar
                            // belongs.
                            val raw = withContext(Dispatchers.IO) {
                                runCatching {
                                    TemplateStorage.load(ctx, summary.filename)
                                        .also { ShaftDocCodec.decode(it) }
                                }.getOrNull()
                            }
                            if (raw == null) {
                                snackbarHostState.showSnackbar("Could not open that template")
                            } else {
                                onUseTemplate(raw)
                            }
                        }
                    },
                    onRename = { pendingRename = it },
                    onDelete = { pendingDelete = it },
                )
            }
        }
    }
}

@Composable
private fun EmptyTemplatesMessage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No templates yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Open a drawing, then Save → “Save as template…” to keep its shape here as a " +
                "starting point for the next one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The two-level accordion. Size buckets sort "No liners" first, then 4"→12", then anything
 * outside that range; counts sort by their declaration order.
 */
@Composable
private fun TemplateBrowserList(
    summaries: List<TemplateStorage.TemplateSummary>,
    unit: UnitSystem,
    onUse: (TemplateStorage.TemplateSummary) -> Unit,
    onRename: (TemplateStorage.TemplateSummary) -> Unit,
    onDelete: (TemplateStorage.TemplateSummary) -> Unit,
) {
    val bySize = remember(summaries) {
        summaries.groupBy { it.sizeBucket }.toList().sortedBy { (bucket, _) -> bucket.sortKey() }
    }
    // Only one size section is open at a time — the list stays short enough to scan.
    var openSize by remember { mutableStateOf<TemplateSizeBucket?>(null) }
    var openCount by remember { mutableStateOf<TemplateLinerCount?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        bySize.forEach { (sizeBucket, sizeGroup) ->
            item(key = "size-${sizeBucket.label}") {
                AccordionHeader(
                    label = sizeBucket.label,
                    count = sizeGroup.size,
                    expanded = openSize == sizeBucket,
                    level = 0,
                    onClick = {
                        openSize = if (openSize == sizeBucket) null else sizeBucket
                        openCount = null
                    },
                )
            }

            if (openSize == sizeBucket) {
                val byCount = sizeGroup.groupBy { it.linerCount }
                    .toList()
                    .sortedBy { (count, _) -> count.ordinal }

                byCount.forEach { (countBucket, countGroup) ->
                    item(key = "count-${sizeBucket.label}-${countBucket.name}") {
                        AccordionHeader(
                            label = countBucket.label,
                            count = countGroup.size,
                            expanded = openCount == countBucket,
                            level = 1,
                            onClick = { openCount = if (openCount == countBucket) null else countBucket },
                        )
                    }

                    if (openCount == countBucket) {
                        items(countGroup, key = { it.filename }) { summary ->
                            TemplateCard(
                                summary = summary,
                                unit = unit,
                                onUse = { onUse(summary) },
                                onRename = { onRename(summary) },
                                onDelete = { onDelete(summary) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    level: Int,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = (16 + level * 16).dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = if (level == 0) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (level == 0) HorizontalDivider()
    }
}

/**
 * One template: a drawn preview over its name and a derived caption (OAL and largest Ø), plus
 * a Rename/Delete menu. Tapping the card uses the template.
 */
@Composable
private fun TemplateCard(
    summary: TemplateStorage.TemplateSummary,
    unit: UnitSystem,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // Resolved once per card — the same pure resolve the editor runs, so the preview shows
    // auto-body fill and subtracted bodies exactly as the drawing will. The manual-OAL
    // predicate must match applyTemplate's (`> coverageEndMm + eps`): a bare `OAL > 0` treats
    // every stored auto-OAL document as manual and previews a leading auto-fill span that
    // vanishes when the template is used.
    val resolved = remember(summary.filename, summary.spec) {
        resolveComponents(
            summary.spec,
            overallIsManual = summary.spec.overallLengthMm > summary.spec.coverageEndMm() + 1e-3f,
        )
    }
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onUse)
            .testTag("template_card_${summary.filename}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(TEMPLATE_PREVIEW_ASPECT)
                .background(Color.Transparent),
        ) {
            ShaftThumbnail(
                spec = summary.spec,
                resolvedComponents = resolved,
                unit = summary.unit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    templateCaption(summary, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Template options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Use this template") },
                        onClick = { menuOpen = false; onUse() },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename…") },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete…") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Wide and short — a shaft is a long thin thing, and a square card wastes the row. */
private const val TEMPLATE_PREVIEW_ASPECT = 3.2f

/**
 * "94.5 in OAL · 6 in max Ø · 2 liners" — derived, never stored. Formatted in the app's
 * ACTIVE unit, not the template's authored one, so the caption reads in the units the user is
 * working in right now.
 */
private fun templateCaption(summary: TemplateStorage.TemplateSummary, unit: UnitSystem): String {
    val spec = summary.spec
    val parts = mutableListOf<String>()
    if (spec.overallLengthMm > 0f) {
        parts += "${formatDisplay(spec.overallLengthMm, unit)} ${abbr(unit)} OAL"
    }
    val maxDia = maxOf(
        spec.bodies.maxOfOrNull { it.diaMm } ?: 0f,
        spec.liners.maxOfOrNull { it.odMm } ?: 0f,
    )
    if (maxDia > 0f) parts += "${formatDisplay(maxDia, unit)} ${abbr(unit)} max Ø"
    parts += summary.linerCount.label
    return parts.joinToString(" · ")
}
