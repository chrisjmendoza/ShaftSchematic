package com.android.shaftschematic.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.android.shaftschematic.data.SettingsStore
import com.android.shaftschematic.util.AppLog
import com.android.shaftschematic.util.VerboseLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BackupMirror — the off-device auto-mirror.
 *
 * The user picks one SAF folder in Settings; from then on every saved shaft document is copied
 * there as well, under the same filename, so the off-device backup is always current without
 * anybody remembering to make one. A document deleted or renamed internally is deleted or
 * renamed in the folder too, and [mirrorAllNow] catches the folder up with everything that was
 * already saved when it was picked.
 *
 * **The mirror may never cost the operation anything.** Every hook runs *after* the internal
 * [InternalStorage] call has already returned (and only when it succeeded), fire-and-forget on
 * this object's own IO scope, and every provider call is wrapped: a revoked grant, a deleted
 * folder, a full disk or any other IO failure leaves the internal document exactly as it was and
 * never surfaces as an error the user has to dismiss. The only signal is a log line on the IO
 * channel plus [lastOutcome] / [catchUp], which Settings shows as quiet supporting text.
 *
 * **A found-revoked permission is never cleared.** The user may re-grant access to the same
 * folder (remounted card, restored cloud account); dropping the stored URI on the first failure
 * would silently turn mirroring off and it would stay off.
 *
 * Scope: saved shaft documents only. Autosave drafts live in DataStore and never reach this
 * class; templates and backup-zip restores go through [InternalStorage]'s directory-taking
 * overload, which carries no hook.
 */
object BackupMirror {

    // Only failures reach AppLog. A working mirror writes on every save, and a breadcrumb per
    // save would push everything else out of the ring; successes stay on the dev-gated
    // VerboseLog channel, where they cost nothing unless somebody asked for them.
    private const val TAG = "BackupMirror"

    /** What the last mirror attempt did. Session state — nothing here is persisted. */
    enum class Status { WROTE, RENAMED, REMOVED, FAILED }

    data class Outcome(
        val status: Status,
        val documentName: String,
        val detail: String? = null,
        /** The name the document was mirrored under before a [Status.RENAMED] outcome. */
        val previousName: String? = null,
    )

    /**
     * Progress of a [mirrorAllNow] catch-up — session state like [Outcome], and reported
     * separately from it: driving the per-save status line through forty documents would flicker
     * a line that is meant to say what the last *save* did.
     */
    data class CatchUp(
        val running: Boolean,
        val total: Int,
        val mirrored: Int,
        val failed: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serializes mirror writes. Two saves in quick succession would otherwise race for the same
     * document, and the loser could leave a half-written copy behind.
     */
    private val writeLock = Mutex()

    private val _lastOutcome = MutableStateFlow<Outcome?>(null)
    val lastOutcome: StateFlow<Outcome?> = _lastOutcome.asStateFlow()

    private val _catchUp = MutableStateFlow<CatchUp?>(null)
    val catchUp: StateFlow<CatchUp?> = _catchUp.asStateFlow()

    /**
     * Called by [InternalStorage.save] once the internal write has succeeded.
     *
     * Returns immediately: the copy is queued onto this object's IO scope so no save path ever
     * waits on a provider that may be a network mount.
     */
    fun onDocumentSaved(ctx: Context, name: String, content: String) {
        dispatch(ctx, "save") { mirrorDocument(it, name, content) }
    }

    /**
     * Called by [InternalStorage.delete] once the internal file is actually gone.
     *
     * Only on success, deliberately: a failed internal delete leaves the document live, and the
     * folder copy of a live document is a backup, not a leftover.
     */
    fun onDocumentDeleted(ctx: Context, name: String) {
        dispatch(ctx, "delete") { unmirrorDocument(it, name) }
    }

    /**
     * Called by [InternalStorage.rename] once the internal rename has succeeded — after it, so
     * the renamed content can be read back under [toName].
     */
    fun onDocumentRenamed(ctx: Context, fromName: String, toName: String) {
        dispatch(ctx, "rename") { mirrorRenamedDocument(it, fromName, toName) }
    }

    /**
     * Settings → Data → "Mirror all now": copies every saved document to the folder, catching up
     * everything that was already there when the folder was picked. Fire-and-forget like the
     * hooks; progress lands in [catchUp].
     */
    fun mirrorAllSavedDocuments(ctx: Context) {
        dispatch(ctx, "catch-up") { mirrorAllNow(it) }
    }

    /**
     * Copies [content] to the mirror folder under [name]. Suspends; safe to call directly from a
     * test. Never throws. Returns true when the copy actually landed.
     */
    suspend fun mirrorDocument(ctx: Context, name: String, content: String): Boolean {
        val app = ctx.applicationContext
        val folderUri = storedFolderUri(app)
        if (!shouldMirrorDocument(name, folderUri)) return false
        val treeUri = parseTreeUri(folderUri) ?: return false

        return writeMirrorCopy(app, treeUri, name, content).fold(
            onSuccess = {
                VerboseLog.d(VerboseLog.Category.IO, TAG) { "mirrored name=$name chars=${content.length}" }
                _lastOutcome.value = Outcome(Status.WROTE, name)
                true
            },
            onFailure = { t ->
                VerboseLog.e(VerboseLog.Category.IO, TAG) { "mirror failed name=$name: ${t.message}" }
                AppLog.e(TAG, "mirror failed name=$name", t)
                _lastOutcome.value = Outcome(Status.FAILED, name, detail = failureDetail(t))
                false
            },
        )
    }

    /**
     * Removes the folder's copy of [name]. Never throws; a folder that holds nothing by that
     * name is a silent no-op (it may predate the folder being picked, or have been tidied away
     * by hand). Returns true when a copy was actually removed.
     */
    suspend fun unmirrorDocument(ctx: Context, name: String): Boolean {
        val app = ctx.applicationContext
        val folderUri = storedFolderUri(app)
        if (!shouldMirrorDocument(name, folderUri)) return false
        val treeUri = parseTreeUri(folderUri) ?: return false

        return deleteMirrorCopy(app, treeUri, name).fold(
            onSuccess = { removed ->
                if (removed) {
                    VerboseLog.d(VerboseLog.Category.IO, TAG) { "unmirrored name=$name" }
                    _lastOutcome.value = Outcome(Status.REMOVED, name)
                } else {
                    VerboseLog.d(VerboseLog.Category.IO, TAG) { "nothing to unmirror name=$name" }
                }
                removed
            },
            onFailure = { t ->
                VerboseLog.e(VerboseLog.Category.IO, TAG) { "unmirror failed name=$name: ${t.message}" }
                AppLog.e(TAG, "unmirror failed name=$name", t)
                _lastOutcome.value = Outcome(Status.FAILED, name, detail = failureDetail(t))
                false
            },
        )
    }

    /**
     * Follows an internal rename into the folder: write the content under [toName], then drop the
     * copy under [fromName].
     *
     * **Write first, delete second.** A failed write must never be able to take the only
     * off-device copy with it, so the old name is only dropped once the new one is provably
     * there — a copy under a stale name is a far cheaper failure than no copy at all.
     *
     * Deliberately not `DocumentsContract.renameDocument`: tree-URI rename support varies by
     * provider, and a rename that silently does nothing would leave the same stale copy this
     * closes. The content is read back from internal storage under the NEW name because the
     * internal rename has already happened by the time this runs.
     */
    suspend fun mirrorRenamedDocument(ctx: Context, fromName: String, toName: String) {
        val app = ctx.applicationContext
        val folderUri = storedFolderUri(app)
        if (folderUri.isNullOrBlank()) return
        val treeUri = parseTreeUri(folderUri) ?: return

        val content = runCatching { InternalStorage.load(app, toName) }.getOrNull()
        if (content == null) {
            VerboseLog.e(VerboseLog.Category.IO, TAG) { "rename mirror skipped: $toName is not readable" }
            AppLog.e(TAG, "rename mirror skipped: $toName is not readable")
            return
        }

        if (!mirrorDocument(app, toName, content)) {
            VerboseLog.e(VerboseLog.Category.IO, TAG) {
                "rename mirror kept the copy of $fromName: $toName could not be written"
            }
            AppLog.e(TAG, "rename mirror kept the copy of $fromName: $toName could not be written")
            return
        }

        if (shouldMirrorDocument(fromName, folderUri)) {
            deleteMirrorCopy(app, treeUri, fromName).onFailure { t ->
                VerboseLog.e(VerboseLog.Category.IO, TAG) {
                    "rename mirror left a stale copy of $fromName: ${t.message}"
                }
                AppLog.e(TAG, "rename mirror left a stale copy of $fromName", t)
            }
        }
        _lastOutcome.value = Outcome(Status.RENAMED, toName, previousName = fromName)
    }

    /**
     * Copies every saved document into the folder, one at a time, reporting through [catchUp].
     *
     * This is the answer to a folder picked *after* the shafts were saved: without it the mirror
     * only ever holds what happened to be saved since. Reuses the same per-document write as a
     * save, so nothing about the copy differs from an automatic one.
     */
    suspend fun mirrorAllNow(ctx: Context): CatchUp {
        val app = ctx.applicationContext
        val folderUri = storedFolderUri(app)
        val treeUri = parseTreeUri(folderUri)
        if (folderUri.isNullOrBlank() || treeUri == null) {
            return CatchUp(running = false, total = 0, mirrored = 0, failed = 0)
                .also { _catchUp.value = it }
        }

        val names = runCatching { InternalStorage.list(app) }.getOrDefault(emptyList())
            .filter { shouldMirrorDocument(it, folderUri) }
        _catchUp.value = CatchUp(running = true, total = names.size, mirrored = 0, failed = 0)

        var mirrored = 0
        var failed = 0
        for (name in names) {
            val content = runCatching { InternalStorage.load(app, name) }.getOrNull()
            val ok = content != null &&
                writeMirrorCopy(app, treeUri, name, content)
                    .onFailure { t ->
                        VerboseLog.e(VerboseLog.Category.IO, TAG) { "catch-up failed name=$name: ${t.message}" }
                        AppLog.e(TAG, "catch-up failed name=$name", t)
                    }
                    .isSuccess
            if (ok) mirrored++ else failed++
            _catchUp.value = CatchUp(running = true, total = names.size, mirrored = mirrored, failed = failed)
        }

        VerboseLog.d(VerboseLog.Category.IO, TAG) {
            "catch-up done total=${names.size} mirrored=$mirrored failed=$failed"
        }
        return CatchUp(running = false, total = names.size, mirrored = mirrored, failed = failed)
            .also { _catchUp.value = it }
    }

    /**
     * Records the folder the user picked and takes a persistable read+write grant on it.
     * Returns false when the grant could not be taken (nothing is stored in that case — a URI
     * with no persisted permission would fail on the very next save with no way back).
     */
    suspend fun selectFolder(ctx: Context, treeUri: Uri): Boolean {
        val app = ctx.applicationContext
        val taken = runCatching {
            app.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess

        if (!taken) {
            VerboseLog.e(VerboseLog.Category.IO, TAG) { "could not persist permission for $treeUri" }
            AppLog.e(TAG, "could not persist permission for the picked mirror folder")
            return false
        }

        SettingsStore.setBackupMirrorFolderUri(app, treeUri.toString())
        _lastOutcome.value = null
        _catchUp.value = null
        return true
    }

    /** Stops mirroring: releases the persisted grant, then forgets the folder. */
    suspend fun clearFolder(ctx: Context) {
        val app = ctx.applicationContext
        val stored = runCatching { SettingsStore.getBackupMirrorFolderUri(app) }.getOrNull()
        if (stored != null) {
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        SettingsStore.setBackupMirrorFolderUri(app, null)
        _lastOutcome.value = null
        _catchUp.value = null
    }

    /**
     * The folder's own display name, or a name derived from the URI when the provider cannot be
     * asked (revoked grant, folder deleted) — the row must always name the folder it lost.
     */
    fun folderLabel(ctx: Context, uriString: String): String {
        val fallback = mirrorFolderFallbackLabel(uriString)
        val treeUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return fallback

        return runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            ctx.applicationContext.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull() ?: fallback
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Dispatch + folder resolution
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Queues one mirror operation onto this object's IO scope and returns. Reaching the failure
     * branch means even the bookkeeping threw — swallow it: the internal document is already in
     * the state the user asked for, and [what] names which hook gave up.
     */
    private fun dispatch(ctx: Context, what: String, work: suspend (Context) -> Unit) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching { work(app) }
                .onFailure { t ->
                    VerboseLog.e(VerboseLog.Category.IO, TAG) { "$what mirror dispatch failed: ${t.message}" }
                    AppLog.e(TAG, "$what mirror dispatch failed", t)
                }
        }
    }

    private suspend fun storedFolderUri(ctx: Context): String? =
        runCatching { SettingsStore.getBackupMirrorFolderUri(ctx) }.getOrNull()

    private fun parseTreeUri(folderUri: String?): Uri? =
        folderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /**
     * The write, with no status bookkeeping of its own — the caller owns the report, so the
     * catch-up can total its own run without driving the per-save status line.
     */
    private suspend fun writeMirrorCopy(
        ctx: Context,
        treeUri: Uri,
        name: String,
        content: String,
    ): Result<Unit> = writeLock.withLock { runCatching { writeIntoTree(ctx, treeUri, name, content) } }

    /** The delete, same posture as [writeMirrorCopy]. True when a copy was actually removed. */
    private suspend fun deleteMirrorCopy(
        ctx: Context,
        treeUri: Uri,
        name: String,
    ): Result<Boolean> = writeLock.withLock { runCatching { deleteFromTree(ctx, treeUri, name) } }

    // ────────────────────────────────────────────────────────────────────────────
    // Provider plumbing (the untestable shell — keep it thin)
    // ────────────────────────────────────────────────────────────────────────────

    private fun readFolderEntries(ctx: Context, treeUri: Uri, treeDocId: String): List<MirrorFolderEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val entries = mutableListOf<MirrorFolderEntry>()
        ctx.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val display = c.getString(1) ?: continue
                entries += MirrorFolderEntry(documentId = id, displayName = display)
            }
        } ?: error("mirror folder is not readable")
        return entries
    }

    private fun writeIntoTree(ctx: Context, treeUri: Uri, name: String, content: String) {
        val resolver = ctx.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val entries = readFolderEntries(ctx, treeUri, treeDocId)

        val docUri = when (val target = planMirrorWrite(entries, name)) {
            is MirrorWriteTarget.Overwrite ->
                DocumentsContract.buildDocumentUriUsingTree(treeUri, target.documentId)
            MirrorWriteTarget.Create ->
                DocumentsContract.createDocument(resolver, parentDocUri, MIRROR_DOC_MIME, name)
                    ?: error("could not create the document")
        }

        val bytes = content.toByteArray(Charsets.UTF_8)
        // "wt" truncates first. Without it a document that shrank would keep the tail of the
        // previous version and decode as corrupt.
        val stream = runCatching { resolver.openOutputStream(docUri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(docUri, "w")
            ?: error("could not open the document for writing")
        stream.use { it.write(bytes) }
    }

    /** False when the folder holds nothing by that name; throws only on a real provider failure. */
    private fun deleteFromTree(ctx: Context, treeUri: Uri, name: String): Boolean {
        val resolver = ctx.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val entries = readFolderEntries(ctx, treeUri, treeDocId)

        val target = planMirrorDelete(entries, name) as? MirrorDeleteTarget.Delete ?: return false
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, target.documentId)
        if (!DocumentsContract.deleteDocument(resolver, docUri)) {
            error("the provider refused to delete the copy")
        }
        return true
    }

    private fun failureDetail(t: Throwable): String = when (t) {
        is SecurityException -> "folder access was withdrawn"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "write failed"
    }
}
