package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.model.SchematicSection
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.pdf.resolveSheetSection
import com.android.shaftschematic.ui.resolved.ResolvedComponent
import com.android.shaftschematic.util.buildBodyTitleById
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.buildTaperTitleById
import com.android.shaftschematic.util.buildThreadTitleById
import com.android.shaftschematic.ui.input.nameKeyboard
import com.android.shaftschematic.ui.input.raiseKnownCodes

// ──────────────────────────────────────────────────────────────────────────────
// Section sheets — the PDF preview's sheet picker and the dialog that edits the job's
// sections. A section is a named stretch of the shaft printed alone (`SchematicSection`);
// everything here only names components, it never touches one.
// ──────────────────────────────────────────────────────────────────────────────

/** One part a section can start or end on: its stored id, its card title, where it starts. */
internal data class SectionPart(val id: String, val title: String, val startMm: Float)

/**
 * The parts of [spec] a section may run between — every taper, thread, liner and explicit body,
 * AFT → FWD, under the titles their cards carry. Bare shaft is not offered: its identity is its
 * span, which moves with every neighbour's edit, so a section could not keep hold of it; a
 * section reaches bare shaft through "AFT end" / "FWD end" and through the stub it draws past
 * its last part.
 */
internal fun sectionParts(spec: ShaftSpec): List<SectionPart> {
    val bodyTitles = buildBodyTitleById(spec)
    val taperTitles = buildTaperTitleById(spec)
    val threadTitles = buildThreadTitleById(spec)
    val linerTitles = buildLinerTitleById(spec)
    val parts = buildList {
        spec.bodies.sortedWith(compareBy({ it.startFromAftMm }, { it.id })).forEachIndexed { i, b ->
            add(SectionPart(b.id, bodyTitles[b.id] ?: "Body #${i + 1}", b.startFromAftMm))
        }
        spec.tapers.sortedWith(compareBy({ it.startFromAftMm }, { it.id })).forEachIndexed { i, t ->
            add(SectionPart(t.id, taperTitles[t.id] ?: "Taper #${i + 1}", t.startFromAftMm))
        }
        spec.threads.sortedWith(compareBy({ it.startFromAftMm }, { it.id })).forEachIndexed { i, th ->
            add(SectionPart(th.id, threadTitles[th.id] ?: "Thread #${i + 1}", th.startFromAftMm))
        }
        spec.liners.sortedWith(compareBy({ it.startFromAftMm }, { it.id })).forEachIndexed { i, ln ->
            val title = ln.label?.trim()?.ifEmpty { null } ?: linerTitles[ln.id] ?: "Liner ${i + 1}"
            add(SectionPart(ln.id, title, ln.startFromAftMm))
        }
    }
    return parts.sortedBy { it.startMm }
}

internal const val SECTION_AFT_END_LABEL = "AFT end"
internal const val SECTION_FWD_END_LABEL = "FWD end"

/** "AFT end → FWD Liner" — what a section runs between, in words; a part that is gone says so. */
internal fun sectionExtentText(section: SchematicSection, parts: List<SectionPart>): String {
    fun name(id: String?, endLabel: String) =
        if (id == null) endLabel else parts.firstOrNull { it.id == id }?.title ?: "(removed part)"
    return "${name(section.firstId, SECTION_AFT_END_LABEL)} → ${name(section.lastId, SECTION_FWD_END_LABEL)}"
}

/** The chip / list label of a section: its name, or its extent when it has none. */
internal fun sectionDisplayName(section: SchematicSection, parts: List<SectionPart>): String =
    section.name.ifBlank { sectionExtentText(section, parts) }

/**
 * The preview's sheet picker: "Whole shaft", one chip per section of this drawing, and the
 * button that opens the section editor. A section that names a part no longer on the shaft
 * cannot print, so its chip is disabled until it is edited.
 */
@Composable
internal fun SheetPickerRow(
    sections: List<SchematicSection>,
    selectedId: String?,
    spec: ShaftSpec,
    resolved: List<ResolvedComponent>,
    onSelect: (String?) -> Unit,
    onEditSections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = remember(spec) { sectionParts(spec) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun chip(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                modifier = Modifier.testTag(tag),
            )
        }
        chip("Whole shaft", selectedId == null, true, "section_whole_shaft") { onSelect(null) }
        sections.forEach { s ->
            val prints = remember(s, spec, resolved) {
                resolveSheetSection(spec, resolved, s.firstId, s.lastId) != null
            }
            chip(sectionDisplayName(s, parts), selectedId == s.id, prints, "section_chip_${s.id}") { onSelect(s.id) }
        }
        AssistChip(
            onClick = onEditSections,
            label = { Text(if (sections.isEmpty()) "Add section sheet" else "Sections…") },
            modifier = Modifier.testTag("section_sheets_button"),
        )
    }
}

/**
 * The job's sections of one drawing: add, rename, re-point, delete. [onSave] receives the id of
 * the section being edited, or null for a new one.
 */
@Composable
internal fun SectionSheetsDialog(
    sections: List<SchematicSection>,
    spec: ShaftSpec,
    resolved: List<ResolvedComponent>,
    onSave: (id: String?, name: String, firstId: String?, lastId: String?) -> Unit,
    onDelete: (id: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val parts = remember(spec) { sectionParts(spec) }
    // null = the list; otherwise the section being edited (a blank id = a new one).
    var editing by remember { mutableStateOf<SchematicSection?>(null) }

    editing?.let { draft ->
        SectionEditDialog(
            initial = draft,
            parts = parts,
            spec = spec,
            resolved = resolved,
            onSave = { name, firstId, lastId ->
                onSave(draft.id.ifEmpty { null }, name, firstId, lastId)
                editing = null
            },
            onDismiss = { editing = null },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Section sheets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Each section prints as its own sheet: the parts you pick, with a break " +
                        "where the shaft carries on. Saved with this job.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                sections.forEach { s ->
                    val prints = resolveSheetSection(spec, resolved, s.firstId, s.lastId) != null
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(sectionDisplayName(s, parts), style = MaterialTheme.typography.titleSmall)
                            if (s.name.isNotBlank()) {
                                Text(sectionExtentText(s, parts), style = MaterialTheme.typography.bodySmall)
                            }
                            if (!prints) {
                                Text(
                                    "A part it names is no longer on the shaft — edit it to pick again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(onClick = { editing = s }, modifier = Modifier.testTag("section_edit_${s.id}")) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit section")
                        }
                        IconButton(onClick = { onDelete(s.id) }, modifier = Modifier.testTag("section_delete_${s.id}")) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete section")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { editing = SchematicSection(id = "") },
                    modifier = Modifier.fillMaxWidth().testTag("section_add"),
                ) { Text("Add section") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SectionEditDialog(
    initial: SchematicSection,
    parts: List<SectionPart>,
    spec: ShaftSpec,
    resolved: List<ResolvedComponent>,
    onSave: (name: String, firstId: String?, lastId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var firstId by remember { mutableStateOf(initial.firstId) }
    var lastId by remember { mutableStateOf(initial.lastId) }
    val prints = resolveSheetSection(spec, resolved, firstId, lastId) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isEmpty()) "New section" else "Edit section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = raiseKnownCodes(it) },
                    label = { Text("Name") },
                    keyboardOptions = nameKeyboard(),
                    supportingText = { Text("Prints at the top of the sheet and names its file.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("section_name"),
                )
                PartPicker(
                    label = "From",
                    endLabel = SECTION_AFT_END_LABEL,
                    endFirst = true,
                    selectedId = firstId,
                    parts = parts,
                    tag = "section_from",
                    onPick = { firstId = it },
                )
                PartPicker(
                    label = "To",
                    endLabel = SECTION_FWD_END_LABEL,
                    endFirst = false,
                    selectedId = lastId,
                    parts = parts,
                    tag = "section_to",
                    onPick = { lastId = it },
                )
                if (!prints) {
                    Text(
                        "Pick parts that are on the shaft.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(raiseKnownCodes(name, finished = true), firstId, lastId) },
                enabled = prints,
                modifier = Modifier.testTag("section_save"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One end of a section: the shaft's own end ([endLabel], id null) or one of [parts]. */
@Composable
private fun PartPicker(
    label: String,
    endLabel: String,
    endFirst: Boolean,
    selectedId: String?,
    parts: List<SectionPart>,
    tag: String,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = if (selectedId == null) endLabel else parts.firstOrNull { it.id == selectedId }?.title ?: "(removed part)"
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
                Text(current, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                @Composable
                fun endItem() = DropdownMenuItem(
                    text = { Text(endLabel) },
                    onClick = { onPick(null); open = false },
                    modifier = Modifier.testTag("${tag}_end"),
                )
                if (endFirst) endItem()
                parts.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.title) },
                        onClick = { onPick(p.id); open = false },
                        modifier = Modifier.testTag("${tag}_${p.id}"),
                    )
                }
                if (!endFirst) endItem()
            }
        }
    }
}
