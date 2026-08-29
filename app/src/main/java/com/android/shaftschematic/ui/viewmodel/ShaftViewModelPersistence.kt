package com.android.shaftschematic.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.android.shaftschematic.BuildConfig
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.doc.mateDuplicate
import com.android.shaftschematic.io.InternalStorage
import com.android.shaftschematic.io.ShaftBackup
import com.android.shaftschematic.model.RunoutReadings
import com.android.shaftschematic.model.RunoutStationPlacements
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.UndercutRecord
import com.android.shaftschematic.model.WearRecord
import com.android.shaftschematic.model.oalIsManualOnLoad
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ShaftViewModelPersistence — extension functions for whole-document persistence: the
 * versioned JSON envelope (export/import), the geometry-only template envelope, the
 * bundled-sample and zip backup/restore actions, and the new-document reset.
 *
 * Extracted from ShaftViewModel to keep document-boundary work grouped by concern. All
 * functions are extensions on ShaftViewModel and access internal-visibility backing fields
 * and helpers declared in the primary class file; the draft-ring and undo/redo state they
 * reseat at each boundary stays owned there.
 */

// ────────────────────────────────────────────────────────────────────────────
// Persistence — versioned JSON document (UI wires it to SAF)
// ────────────────────────────────────────────────────────────────────────────

/**
 * Re-seeds bundled samples into the internal Saved list (Settings action).
 * Safe: never overwrites existing docs; collisions create suffixed duplicates.
 */
fun ShaftViewModel.restoreSampleShafts() {
    viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()

        val report = runCatching {
            InternalStorage.seedBundledSamples(app, SettingsStore, force = true)
        }.getOrElse {
            _uiEvents.emit(UiEvent.ShowSnackbarMessage("Restore sample shafts failed"))
            return@launch
        }

        val msg = when {
            report.savedCount > 0 -> "Restored sample shafts: +${report.savedCount}"
            report.attemptedCount == 0 -> "No bundled sample shafts found"
            else -> "Sample shafts already present"
        }

        _uiEvents.emit(UiEvent.ShowSnackbarMessage(msg))
    }
}

/**
 * Writes every saved shaft into a single zip at the SAF-picked [uri]
 * (Settings → "Back up all shafts…"). Result is reported via snackbar.
 */
fun ShaftViewModel.backupAllShaftsTo(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val result = runCatching {
            val docs = InternalStorage.list(app).mapNotNull { name ->
                runCatching { name to InternalStorage.load(app, name) }.getOrNull()
            }
            app.contentResolver.openOutputStream(uri)?.use { out ->
                ShaftBackup.writeZip(
                    out = out,
                    docs = docs,
                    manifest = ShaftBackup.Manifest(
                        appVersion = BuildConfig.VERSION_NAME,
                        docFormatVersion = ShaftDocCodec.CURRENT_VERSION,
                        createdEpochMs = System.currentTimeMillis(),
                        documentCount = docs.size,
                    ),
                )
            } ?: error("Could not open the selected location")
            docs.size
        }

        val msg = result.fold(
            onSuccess = { count ->
                if (count > 0) "Backed up $count shaft${if (count == 1) "" else "s"}"
                else "Backup written, but there were no saved shafts"
            },
            onFailure = {
                VerboseLog.e(VerboseLog.Category.IO, "ShaftBackup") { "backup failed: ${it.message}" }
                AppLog.e("ShaftBackup", "backup failed", it)
                "Backup failed — could not write the file"
            },
        )
        _uiEvents.emit(UiEvent.ShowSnackbarMessage(msg))
    }
}

/**
 * Restores shafts from a backup zip at the SAF-picked [uri]
 * (Settings → "Restore from backup…"). Never overwrites: identical docs are
 * skipped, name collisions are saved as "<name> (restored)".
 */
fun ShaftViewModel.restoreShaftsFromBackup(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val result = runCatching {
            val contents = app.contentResolver.openInputStream(uri)?.use { input ->
                ShaftBackup.readZip(input)
            } ?: error("Could not open the selected file")

            if (contents.docs.isEmpty()) return@runCatching null

            ShaftBackup.restoreInto(
                dir = InternalStorage.dir(app.filesDir),
                docs = contents.docs,
            ) { raw -> runCatching { ShaftDocCodec.decode(raw) }.isSuccess }
        }

        val msg = result.fold(
            onSuccess = { report ->
                when {
                    report == null -> "No shaft files found in that backup"
                    else -> buildString {
                        val added = report.restoredCount + report.renamedCount
                        append("Restored $added shaft${if (added == 1) "" else "s"}")
                        if (report.renamedCount > 0) append(" (${report.renamedCount} renamed)")
                        if (report.skippedIdenticalCount > 0) append(", ${report.skippedIdenticalCount} already present")
                        if (report.failedCount > 0) append(", ${report.failedCount} unreadable")
                    }
                }
            },
            onFailure = {
                VerboseLog.e(VerboseLog.Category.IO, "ShaftBackup") { "restore failed: ${it.message}" }
                AppLog.e("ShaftBackup", "restore failed", it)
                "Restore failed — could not read the file"
            },
        )
        _uiEvents.emit(UiEvent.ShowSnackbarMessage(msg))
    }
}

/**
 * The current session as a document envelope — the ONE place the live state is mapped onto
 * [ShaftDocCodec.ShaftDocV1]. Both writers below build on it, so a field added to the envelope
 * cannot reach the save path while silently missing from the mate copy.
 */
private fun ShaftViewModel.currentEnvelope(): ShaftDocCodec.ShaftDocV1 = ShaftDocCodec.ShaftDocV1(
    preferredUnit = _unit.value,
    unitLocked = _unitLocked.value,
    jobNumber = _jobNumber.value,
    customer = _customer.value,
    vessel = _vessel.value,
    item = _item.value,
    shaftPosition = _shaftPosition.value,
    notes = _notes.value,
    spec = _spec.value,
    runoutConfig = _runoutConfig.value,
    wearRecord = _wearRecord.value,
    runoutReadings = _runoutReadings.value,
    runoutStationPlacements = _runoutStationPlacements.value,
    undercutRecord = _undercutRecord.value,
    unitOverrides = _unitOverrides.value,
    dualUnits = _dualUnits.value,
    // station_interval_version is stamped by encodeV1 itself — see its KDoc.
)

/** Export the current state as a JSON string (mm spec + unit metadata + runout config). */
fun ShaftViewModel.exportJson(): String = ShaftDocCodec.encodeV1(currentEnvelope())

/**
 * Encode the current drawing as its **mate's** document: this shaft's geometry and drawing
 * decisions under a new identity, with every measurement record reset. See [mateDuplicate] for
 * what travels and why the records do not.
 *
 * Read-only on the session — the caller writes the returned JSON to a new file; the open
 * document keeps its own name, identity and dirty state.
 */
fun ShaftViewModel.exportMateJson(
    jobNumber: String,
    customer: String,
    vessel: String,
    position: ShaftPosition,
): String = ShaftDocCodec.encodeV1(
    mateDuplicate(
        source = currentEnvelope(),
        jobNumber = jobNumber,
        customer = customer,
        vessel = vessel,
        position = position,
    )
)

/**
 * Encode the current drawing as a reusable **template**: geometry only.
 *
 * Everything that identifies a job or records a measurement is dropped here, at WRITE
 * time, so the stored file itself is clean — job number, customer, vessel, item, shaft
 * position, notes, and the wear / runout / undercut records. Scrubbing only on load would
 * leave a customer's name sitting in the template file, to be carried into every drawing
 * built from it (and into any copy of that file). The per-job sheet tuning in [RunoutConfig]
 * (shaft height, liner compression) resets too — it is tuned per document, not per shaft
 * family.
 *
 * The unit and unit-lock DO travel: they describe how the geometry is authored, not whose
 * job it is. Per-component unit overrides travel for the same reason (which features are
 * metric is an authoring fact); the per-job dual-display flag does not.
 */
fun ShaftViewModel.exportTemplateJson(): String = ShaftDocCodec.encodeV1(
    ShaftDocCodec.ShaftDocV1(
        preferredUnit = _unit.value,
        unitLocked = _unitLocked.value,
        spec = _spec.value,
        unitOverrides = _unitOverrides.value,
    )
)

/**
 * Start a new document from a template.
 *
 * Differs from [importJson] in two deliberate ways:
 *  - **No identity is adopted.** Job metadata and measurement records are cleared even if
 *    the file carries them (belt-and-braces against a template authored before
 *    [exportTemplateJson] scrubbed on write, or one hand-copied into the folder).
 *  - **The session starts dirty, with no filename.** [importJson] ends by marking the
 *    document saved; doing that here would leave a loaded template counting as "no unsaved
 *    work", so quitting would lose it — the draft ring only protects a session it can see
 *    as dirty, and a template-loaded session is not blank. The null filename means the
 *    first Save prompts for a name, so a template can never be overwritten by the drawing
 *    made from it.
 *
 * Component ids are kept as-is: ids never cross document boundaries (wear, runout and
 * undercut records key within one document), so two drawings from one template sharing ids
 * is harmless.
 */
fun ShaftViewModel.applyTemplate(raw: String) {
    val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrElse { throw it }

    _editorResetNonce.update { it + 1 }
    clearEditHistory()
    currentDraftId = UUID.randomUUID().toString()
    draftPersisted = false
    _selectedComponentId.value = null

    _spec.value = decoded.spec
    seedSessionAddDefaultsFromSpec(decoded.spec)

    _unitLocked.value = decoded.unitLocked
    decoded.preferredUnit?.let { setUnit(it, persist = false) }

    // A template is geometry, not a job.
    _jobNumber.value = ""
    _customer.value = ""
    _vessel.value = ""
    _item.value = ""
    _shaftPosition.value = ShaftPosition.OTHER
    _notes.value = ""
    _runoutConfig.value = RunoutConfig()
    _wearRecord.value = WearRecord()
    _runoutReadings.value = RunoutReadings()
    _runoutStationPlacements.value = RunoutStationPlacements()
    _undercutRecord.value = UndercutRecord()
    // Overrides describe authoring and travel with the template; dual is per-job.
    _unitOverrides.value = decoded.unitOverrides
    _dualUnits.value = false

    _overallIsManual.value = decoded.spec.oalIsManualOnLoad()

    // Deliberately NOT markDocumentSaved() — see the KDoc. The baseline stays where it
    // was, so the session reads as unsaved work and autosave keeps a draft of it.
    _currentDocumentName.value = null
}

/**
 * Import a JSON string and replace current state.
 * Tries envelope first, then falls back to legacy (spec-only) files.
 * Seeds/repairs UI order to reflect loaded spec.
 */
fun ShaftViewModel.importJson(raw: String) {
    val decoded = runCatching { ShaftDocCodec.decode(raw) }.getOrElse { throw it }

    clearEditHistory()
    // Opening a document is an editor boundary like New/template-apply: without the bump,
    // Compose-local editor state (pager page, field drafts, expanded sections) carries over
    // into the newly loaded document. Draft restore deliberately does NOT bump — continuing
    // a draft resumes the same editing session.
    _editorResetNonce.update { it + 1 }
    // Each open is a fresh draft identity so this document's autosave upserts its own entry
    // and cannot touch another document's draft. markDocumentSaved() below reseats the
    // dirty-gate baseline to the just-loaded state (clean → no draft until edited).
    currentDraftId = UUID.randomUUID().toString()
    draftPersisted = false
    // Session boundary: drop the previous document's selection (stale id = orphaned
    // highlight); the carousel's seed effect reselects the last row of this document.
    _selectedComponentId.value = null
    _spec.value = decoded.spec
    seedSessionAddDefaultsFromSpec(decoded.spec)

    _unitLocked.value = decoded.unitLocked
    decoded.preferredUnit?.let { setUnit(it, persist = false) }

    _jobNumber.value = decoded.jobNumber
    _customer.value = decoded.customer
    _vessel.value = decoded.vessel
    _item.value = decoded.item
    _shaftPosition.value = decoded.shaftPosition
    _notes.value = decoded.notes
    _runoutConfig.value = decoded.runoutConfig
    // Already orphan-filtered against decoded.spec.liners inside ShaftDocCodec.decode().
    _wearRecord.value = decoded.wearRecord
    _runoutReadings.value = decoded.runoutReadings
    _runoutStationPlacements.value = decoded.runoutStationPlacements
    _undercutRecord.value = decoded.undercutRecord
    _unitOverrides.value = decoded.unitOverrides
    _dualUnits.value = decoded.dualUnits

    // Derive OAL mode from the document instead of leaking the previous session's
    // flag: an authored OAL must be treated as manual, or the auto path would snap it
    // back down to the content end on open — and with it drop a leading auto span.
    // See [oalIsManualOnLoad] for the two signals.
    _overallIsManual.value = decoded.spec.oalIsManualOnLoad()

    markDocumentSaved()
}

/**
 * Reset the editor to a new blank document.
 *
 * Contract:
 * - Uses the same defaults as the app's start/new flow (blank spec, empty metadata).
 * - Clears undo/redo history and resets cross-type component order.
 */
fun ShaftViewModel.newDocument() {
    _editorResetNonce.update { it + 1 }
    clearEditHistory()
    // Fresh draft identity for the new blank session; markDocumentSaved() below reseats the
    // dirty-gate baseline to blank (clean → no draft until edited).
    currentDraftId = UUID.randomUUID().toString()
    draftPersisted = false

    resetSessionAddDefaults()

    // Session boundary: no selection carries into a blank document (stale id would be
    // an orphaned highlight).
    _selectedComponentId.value = null

    val blankSpec = ShaftSpec()
    _spec.value = blankSpec

    // Mirror envelope defaults used by the existing start/new seed path.
    _unitLocked.value = true
    setUnit(UnitSystem.INCHES, persist = false)

    _jobNumber.value = ""
    _customer.value = ""
    _vessel.value = ""
    _item.value = ""
    _shaftPosition.value = ShaftPosition.OTHER
    _runoutConfig.value = RunoutConfig()
    _wearRecord.value = WearRecord()
    _runoutReadings.value = RunoutReadings()
    _runoutStationPlacements.value = RunoutStationPlacements()
    _undercutRecord.value = UndercutRecord()
    _unitOverrides.value = emptyMap()
    _dualUnits.value = false
    _notes.value = ""
    _overallIsManual.value = false

    _currentDocumentName.value = null
    markDocumentSaved()
}
