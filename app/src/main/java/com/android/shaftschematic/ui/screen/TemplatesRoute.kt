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
 * A non-blank search REPLACES the accordion with a flat list (`template/TemplateSearch.kt`):
 * filtering inside the buckets would hide matches in collapsed sections, which is the one thing a
 * search must not do. The sort chips order that flat list and the cards inside each open count
 * section — the grouping levels above are structure, not an ordering choice.
 *
 * The store is re-scanned when the route RESUMES as well as on first composition: a template saved
 * from the editor while this screen sat on the back stack would otherwise never appear.
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.io.TemplateStorage
import com.android.shaftschematic.model.oalIsManualOnLoad
import com.android.shaftschematic.template.TemplateSortColumn
import com.android.shaftschematic.template.TemplateSortDir
import com.android.shaftschematic.template.filterAndSortTemplates
import com.android.shaftschematic.template.sortKey
import com.android.shaftschematic.template.sortTemplates
import com.android.shaftschematic.template.templateDescriptor
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.DocumentNaming
import com.android.shaftschematic.ui.drawing.compose.ShaftThumbnail
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.relativeOpenDate
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
        val scan = withContext(Dispatchers.IO) {
            // First-run seeding is kicked off asynchronously at app startup; a browser opened
            // before it lands would read an empty store and claim "No templates yet".
            // Awaiting the (idempotent, flag-gated) seeder here closes that race and costs
            // one DataStore read on every later open.
            val seed = runCatching { TemplateStorage.seedStarterTemplatesIfNeeded(ctx, SettingsStore) }
                .getOrNull()
            seed to TemplateStorage.summaries(ctx)
        }
        summaries = scan.second
        loading = false

        // A starter that could not be seeded is silent otherwise: the browser simply opens with
        // fewer templates than the build ships, which reads as "that's all there is". The count
        // is an event, never content — the privacy rule in `util/AppLog.kt`.
        val failed = scan.first?.failedCount ?: 0
        if (failed > 0) {
            AppLog.e("Templates", "starter seeding failed for $failed asset(s)")
            snackbarHostState.showSnackbar(
                "$failed starter template${if (failed == 1) "" else "s"} could not be seeded"
            )
        }
    }

    // Refresh when the browser comes back to the front. A template saved from the editor while
    // this route sat on the back stack would otherwise never appear — the scan is keyed on
    // [reloadNonce], and nothing bumped it on the way back. The very first resume is skipped:
    // the initial scan above is already running, and a second one would decode the whole store
    // twice on every open.
    var firstResume by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (firstResume) firstResume = false else reloadNonce++
        onPauseOrDispose { }
    }

    var pendingDelete by remember { mutableStateOf<TemplateStorage.TemplateSummary?>(null) }
    var pendingRename by remember { mutableStateOf<TemplateStorage.TemplateSummary?>(null) }

    // Search + sort, the Open screen's controls and interaction exactly — the two screens that
    // manage stored files behave the same way.
    var searchQuery by remember { mutableStateOf("") }
    var sortColumn by remember { mutableStateOf(TemplateSortColumn.DATE) }
    var sortDir by remember { mutableStateOf(TemplateSortDir.DESC) }

    // Derived once per (store, unit): a card's caption is what the query matches on as well as
    // what it prints, so both come from the one call. The unit is the ACTIVE one, per the
    // unit-edge rule — searching "6\"" finds what the card is showing.
    val descriptors = remember(summaries, unit) {
        summaries.associate { it.filename to templateDescriptor(it.spec, unit) }
    }
    val searching = searchQuery.isNotBlank()
    val searchResults = remember(summaries, descriptors, searchQuery, sortColumn, sortDir) {
        if (!searching) emptyList()
        else filterAndSortTemplates(
            items = summaries,
            query = searchQuery,
            column = sortColumn,
            dir = sortDir,
            displayName = { it.displayName },
            descriptor = { descriptors[it.filename].orEmpty() },
            updatedAtEpochMs = { it.updatedAtEpochMs },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this template?") },
            text = { Text("‘${target.displayName}’ will be removed. Drawings already made from it are not affected.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("template_delete_confirm"),
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            withContext(Dispatchers.IO) { TemplateStorage.delete(ctx, target.filename) }
                            reloadNonce++
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    pendingRename?.let { target ->
        // Seeded fully selected, the Open screen's shape: a rename usually replaces the name
        // outright, and a caret parked at the end makes the user clear it by hand first.
        var value by remember(target.filename) {
            mutableStateOf(
                TextFieldValue(target.displayName, selection = TextRange(0, target.displayName.length))
            )
        }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename template") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text("Template name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value.text.isNotBlank(),
                    modifier = Modifier.testTag("template_rename_confirm"),
                    onClick = {
                        // Same sanitizer documents get: `*?:` typed here would otherwise ride
                        // straight into a filename that the Open screen would never have made.
                        val to = TemplateStorage.normalizeTemplateName(
                            DocumentNaming.sanitizePart(value.text)
                        )
                        // Renaming a template to the name it already has is a no-op, not a
                        // collision — the store would report TARGET_EXISTS against itself.
                        if (to != null && to.equals(target.filename, ignoreCase = true)) {
                            pendingRename = null
                            return@TextButton
                        }
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
        val onUse: (TemplateStorage.TemplateSummary) -> Unit = { summary ->
            scope.launch {
                // Decode here as well as at scan time: a file replaced or truncated on disk
                // in between would otherwise reach applyTemplate, which rethrows — a crash
                // where a snackbar belongs.
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
        }

        Column(Modifier.padding(pad).fillMaxSize()) {
            // The header is hidden while there is nothing to search: a filter over an empty
            // store is furniture, and the empty-state message is the whole screen's message.
            if (!loading && summaries.isNotEmpty()) {
                TemplateSearchSortHeader(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    sortColumn = sortColumn,
                    sortDir = sortDir,
                    onSortColumn = { column ->
                        if (column == sortColumn) {
                            sortDir = if (sortDir == TemplateSortDir.ASC) TemplateSortDir.DESC
                                      else TemplateSortDir.ASC
                        } else {
                            sortColumn = column
                            // Newest first for dates, A→Z for names: each column's useful end.
                            sortDir = if (column == TemplateSortColumn.DATE) TemplateSortDir.DESC
                                      else TemplateSortDir.ASC
                        }
                    },
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    // The first scan decodes every template, so a store of any size spends a
                    // visible moment here; a blank screen reads as "no templates" until it
                    // suddenly isn't.
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    summaries.isEmpty() -> EmptyTemplatesMessage()

                    // A search REPLACES the accordion with a flat list. Filtering inside the
                    // buckets would hide every match that happens to sit in a collapsed
                    // section, which is the opposite of what a search is for.
                    searching -> TemplateSearchResults(
                        results = searchResults,
                        query = searchQuery,
                        descriptors = descriptors,
                        onUse = onUse,
                        onRename = { pendingRename = it },
                        onDelete = { pendingDelete = it },
                    )

                    else -> TemplateBrowserList(
                        summaries = summaries,
                        descriptors = descriptors,
                        sortColumn = sortColumn,
                        sortDir = sortDir,
                        onUse = onUse,
                        onRename = { pendingRename = it },
                        onDelete = { pendingDelete = it },
                    )
                }
            }
        }
    }
}

/**
 * Search field over sort chips, the Open screen's header down to the interaction: tapping the
 * ACTIVE column toggles its direction, tapping the other switches to it at that column's useful
 * end. The chips order both the flat search results and the cards inside each open count section,
 * so the setting means one thing wherever the list is being read.
 */
@Composable
private fun TemplateSearchSortHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    sortColumn: TemplateSortColumn,
    sortDir: TemplateSortDir,
    onSortColumn: (TemplateSortColumn) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search templates…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().testTag("template_search_field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TemplateSortChip(
                column = TemplateSortColumn.NAME,
                label = "Name",
                sortColumn = sortColumn,
                sortDir = sortDir,
                onClick = onSortColumn,
            )
            TemplateSortChip(
                column = TemplateSortColumn.DATE,
                label = "Date",
                sortColumn = sortColumn,
                sortDir = sortDir,
                onClick = onSortColumn,
            )
        }
    }
}

@Composable
private fun TemplateSortChip(
    column: TemplateSortColumn,
    label: String,
    sortColumn: TemplateSortColumn,
    sortDir: TemplateSortDir,
    onClick: (TemplateSortColumn) -> Unit,
) {
    val selected = sortColumn == column
    FilterChip(
        selected = selected,
        onClick = { onClick(column) },
        label = {
            Text(if (selected) "$label ${if (sortDir == TemplateSortDir.ASC) "↑" else "↓"}" else label)
        },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
        modifier = Modifier.testTag("template_sort_${column.name.lowercase()}"),
    )
}

/**
 * The flat list a non-blank query shows. No buckets: a match hidden inside a collapsed section is
 * a match the user cannot see, and this screen's whole job is putting candidates side by side.
 */
@Composable
private fun TemplateSearchResults(
    results: List<TemplateStorage.TemplateSummary>,
    query: String,
    descriptors: Map<String, String>,
    onUse: (TemplateStorage.TemplateSummary) -> Unit,
    onRename: (TemplateStorage.TemplateSummary) -> Unit,
    onDelete: (TemplateStorage.TemplateSummary) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            "No templates match “$query”.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp).testTag("template_search_empty"),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.filename }) { summary ->
            TemplateCard(
                summary = summary,
                descriptor = descriptors[summary.filename].orEmpty(),
                onUse = { onUse(summary) },
                onRename = { onRename(summary) },
                onDelete = { onDelete(summary) },
            )
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
 *
 * **Sections open independently.** Comparing two candidates is the whole job of this screen, and
 * an exclusive accordion makes that impossible the moment they live in different buckets — opening
 * the second closed the first. Nothing is expanded on arrival, so the list still opens short.
 *
 * Open state is keyed by **string**, which is what lets it be `rememberSaveable` (a rotation used
 * to collapse everything the user had just opened). A count section's key composes its parent's
 * label in, so "3 liners" open under 6" does not silently open "3 liners" under 8" as well.
 */
@Composable
private fun TemplateBrowserList(
    summaries: List<TemplateStorage.TemplateSummary>,
    descriptors: Map<String, String>,
    sortColumn: TemplateSortColumn,
    sortDir: TemplateSortDir,
    onUse: (TemplateStorage.TemplateSummary) -> Unit,
    onRename: (TemplateStorage.TemplateSummary) -> Unit,
    onDelete: (TemplateStorage.TemplateSummary) -> Unit,
) {
    val bySize = remember(summaries) {
        summaries.groupBy { it.sizeBucket }.toList().sortedBy { (bucket, _) -> bucket.sortKey() }
    }
    var openSizes by rememberSaveable { mutableStateOf(setOf<String>()) }
    var openCounts by rememberSaveable { mutableStateOf(setOf<String>()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        bySize.forEach { (sizeBucket, sizeGroup) ->
            val sizeKey = sizeBucket.label
            val sizeOpen = sizeKey in openSizes

            item(key = "size-$sizeKey") {
                AccordionHeader(
                    label = sizeBucket.label,
                    count = sizeGroup.size,
                    expanded = sizeOpen,
                    level = 0,
                    testTag = "template_bucket_$sizeKey",
                    onClick = { openSizes = openSizes.toggle(sizeKey) },
                )
            }

            if (sizeOpen) {
                val byCount = sizeGroup.groupBy { it.linerCount }
                    .toList()
                    .sortedBy { (count, _) -> count.ordinal }

                byCount.forEach { (countBucket, countGroup) ->
                    val countKey = "$sizeKey|${countBucket.name}"
                    val countOpen = countKey in openCounts

                    item(key = "count-$countKey") {
                        AccordionHeader(
                            label = countBucket.label,
                            count = countGroup.size,
                            expanded = countOpen,
                            level = 1,
                            testTag = "template_count_${sizeKey}_${countBucket.name}",
                            onClick = { openCounts = openCounts.toggle(countKey) },
                        )
                    }

                    if (countOpen) {
                        // The sort chips order cards INSIDE a count group; the two grouping
                        // levels above are the browser's structure, not an ordering choice.
                        val ordered = sortTemplates(
                            items = countGroup,
                            column = sortColumn,
                            dir = sortDir,
                            displayName = { it.displayName },
                            updatedAtEpochMs = { it.updatedAtEpochMs },
                        )
                        items(ordered, key = { it.filename }) { summary ->
                            TemplateCard(
                                summary = summary,
                                descriptor = descriptors[summary.filename].orEmpty(),
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

/** Adds [key] when absent, removes it when present. */
private fun Set<String>.toggle(key: String): Set<String> =
    if (key in this) this - key else this + key

@Composable
private fun AccordionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    level: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(start = (16 + level * 16).dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The chevron SWAPS rather than rotating — the old arrow pointed down open or shut,
            // so the only cue that a section was expanded was the content under it.
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
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
 * One template: a drawn preview over its name and its derived descriptor (OAL, largest Ø, liner
 * count and where those liners sit), plus a Rename/Delete menu. Tapping the card uses the template.
 *
 * The descriptor is what tells two same-bucket templates apart when the names do not — see
 * `template/TemplateDescriptor.kt`.
 */
@Composable
private fun TemplateCard(
    summary: TemplateStorage.TemplateSummary,
    descriptor: String,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // Resolved once per card — the same pure resolve the editor runs, so the preview shows
    // auto-body fill and subtracted bodies exactly as the drawing will. The manual-OAL decision
    // comes from the shared [oalIsManualOnLoad] so it cannot drift from applyTemplate's: a
    // predicate of its own here previews auto-fill spans that appear or vanish when the
    // template is used.
    val resolved = remember(summary.filename, summary.spec) {
        resolveComponents(summary.spec, overallIsManual = summary.spec.oalIsManualOnLoad())
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
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        summary.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Same relative wording the Open screen's rows carry (one implementation,
                    // `util/RelativeDate.kt`) — the two file lists age their rows alike.
                    Text(
                        relativeOpenDate(summary.updatedAtEpochMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    descriptor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Template options")
                }
                // Icons and label wording follow the Open screen's row menu — one menu idiom for
                // the two screens that manage stored files.
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Use this template") },
                        onClick = { menuOpen = false; onUse() },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuOpen = false; onRename() },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** Wide and short — a shaft is a long thin thing, and a square card wastes the row. */
private const val TEMPLATE_PREVIEW_ASPECT = 3.2f
