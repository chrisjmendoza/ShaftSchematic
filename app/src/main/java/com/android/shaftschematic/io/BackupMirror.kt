package com.android.shaftschematic.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.android.shaftschematic.data.SettingsStore
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
 * anybody remembering to make one.
 *
 * **The mirror may never cost the save anything.** It is hooked *after* [InternalStorage.save]
 * has already returned, runs fire-and-forget on this object's own IO scope, and every provider
 * call is wrapped: a revoked grant, a deleted folder, a full disk or any other IO failure leaves
 * the internal save exactly as it was and never surfaces as an error the user has to dismiss.
 * The only signal is a log line on the IO channel plus [lastOutcome], which Settings shows as
 * quiet supporting text.
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

    private const val TAG = "BackupMirror"

    /** What the last mirror attempt did. Session state — nothing here is persisted. */
    enum class Status { WROTE, FAILED }

    data class Outcome(
        val status: Status,
        val documentName: String,
        val detail: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serializes mirror writes. Two saves in quick succession would otherwise race for the same
     * document, and the loser could leave a half-written copy behind.
     */
    private val writeLock = Mutex()

    private val _lastOutcome = MutableStateFlow<Outcome?>(null)
    val lastOutcome: StateFlow<Outcome?> = _lastOutcome.asStateFlow()

    /**
     * Called by [InternalStorage.save] once the internal write has succeeded.
     *
     * Returns immediately: the copy is queued onto this object's IO scope so no save path ever
     * waits on a provider that may be a network mount.
     */
    fun onDocumentSaved(ctx: Context, name: String, content: String) {
        val app = ctx.applicationContext
        scope.launch {
            runCatching { mirrorDocument(app, name, content) }
                .onFailure { t ->
                    // Reaching here means even the outcome bookkeeping threw. Swallow it: the
                    // document is already safely saved internally.
                    VerboseLog.e(VerboseLog.Category.IO, TAG) { "mirror dispatch failed: ${t.message}" }
                }
        }
    }

    /**
     * Copies [content] to the mirror folder under [name]. Suspends; safe to call directly from a
     * test or a "mirror now" action. Never throws.
     */
    suspend fun mirrorDocument(ctx: Context, name: String, content: String) {
        val app = ctx.applicationContext
        val folderUri = runCatching { SettingsStore.getBackupMirrorFolderUri(app) }.getOrNull()
        if (!shouldMirrorDocument(name, folderUri)) return
        val treeUri = runCatching { Uri.parse(folderUri!!) }.getOrNull() ?: return

        writeLock.withLock {
            val result = runCatching { writeIntoTree(app, treeUri, name, content) }
            result.fold(
                onSuccess = {
                    VerboseLog.d(VerboseLog.Category.IO, TAG) { "mirrored name=$name chars=${content.length}" }
                    _lastOutcome.value = Outcome(Status.WROTE, name)
                },
                onFailure = { t ->
                    VerboseLog.e(VerboseLog.Category.IO, TAG) { "mirror failed name=$name: ${t.message}" }
                    _lastOutcome.value = Outcome(Status.FAILED, name, detail = failureDetail(t))
                },
            )
        }
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
            return false
        }

        SettingsStore.setBackupMirrorFolderUri(app, treeUri.toString())
        _lastOutcome.value = null
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
    // Provider plumbing (the untestable shell — keep it thin)
    // ────────────────────────────────────────────────────────────────────────────

    private fun writeIntoTree(ctx: Context, treeUri: Uri, name: String, content: String) {
        val resolver = ctx.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        val entries = mutableListOf<MirrorFolderEntry>()
        resolver.query(
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

    private fun failureDetail(t: Throwable): String = when (t) {
        is SecurityException -> "folder access was withdrawn"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "write failed"
    }
}
