package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT

/**
 * BackupMirrorPlan — the pure decision layer behind the backup auto-mirror.
 *
 * Android-free on purpose: SAF itself cannot be exercised on the JVM, so every decision that
 * can be made without a `ContentResolver` is made here and unit-tested, leaving [BackupMirror]
 * as a thin shell of provider calls.
 */

/**
 * MIME type a mirrored document is created under.
 *
 * `application/octet-stream` is load-bearing. A `DocumentsProvider` rewrites the filename when
 * the requested MIME type and the extension disagree, so asking for `application/json` would
 * land the copy as `Job 1.shaft.json`. `.shaft` maps to no known type, which is exactly what
 * octet-stream means, so the name survives verbatim and the mirror stays openable by this app.
 */
const val MIRROR_DOC_MIME = "application/octet-stream"

/** One document already sitting in the mirror folder, as reported by the provider. */
data class MirrorFolderEntry(
    val documentId: String,
    val displayName: String,
)

/** Where a mirror write should land. */
sealed interface MirrorWriteTarget {
    /**
     * A document with this name is already there — write over it in place.
     *
     * This branch is what keeps the folder from filling with `Job 1 (1).shaft`,
     * `Job 1 (2).shaft`: a provider's create call de-duplicates by appending a counter rather
     * than replacing, so an unconditional create would leave a new copy on every save.
     */
    data class Overwrite(val documentId: String) : MirrorWriteTarget

    /** Nothing by that name yet — create it. */
    data object Create : MirrorWriteTarget
}

/** What a mirror delete should touch. */
sealed interface MirrorDeleteTarget {
    /** The folder's copy of the document — remove it. */
    data class Delete(val documentId: String) : MirrorDeleteTarget

    /** Nothing by that name is in the folder; there is nothing to remove. */
    data object NotPresent : MirrorDeleteTarget
}

/**
 * The folder entry [displayName] refers to, or null when the folder holds nothing by that name.
 *
 * An exact match wins over a case-insensitive one: providers on case-preserving filesystems can
 * hold both `Job 1.shaft` and `JOB 1.shaft`, and the copy of a document must go back over the
 * name it was written under.
 *
 * **Write and delete resolve the name the same way, deliberately.** A delete that matched more
 * strictly than the write would leave behind exactly the copy the write had been maintaining —
 * a stale document under a name the user has already deleted or renamed away.
 */
fun findMirrorEntry(entries: List<MirrorFolderEntry>, displayName: String): MirrorFolderEntry? =
    entries.firstOrNull { it.displayName == displayName }
        ?: entries.firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }

/** Decides whether [displayName] already exists among [entries]. */
fun planMirrorWrite(entries: List<MirrorFolderEntry>, displayName: String): MirrorWriteTarget =
    findMirrorEntry(entries, displayName)
        ?.let { MirrorWriteTarget.Overwrite(it.documentId) }
        ?: MirrorWriteTarget.Create

/**
 * Decides which document a delete of [displayName] should remove from the mirror folder.
 *
 * A missing entry is [MirrorDeleteTarget.NotPresent], never an error: the folder is allowed to
 * be behind (a document saved before the folder was picked, a copy the user tidied away by
 * hand), and a delete of something that was never mirrored has nothing to answer for.
 */
fun planMirrorDelete(entries: List<MirrorFolderEntry>, displayName: String): MirrorDeleteTarget =
    findMirrorEntry(entries, displayName)
        ?.let { MirrorDeleteTarget.Delete(it.documentId) }
        ?: MirrorDeleteTarget.NotPresent

/**
 * True when [name] is a document the mirror at [folderUri] carries — the one gate on every
 * mirror operation: a save's copy, a delete's removal, a rename's pair, a catch-up's list.
 *
 * Deliberately narrow — the mirror carries **saved shaft documents only**:
 * - no folder picked (null/blank) → mirroring is off;
 * - only `.shaft` names, so a `.tmp`/`.bak` sibling of an atomic save can never be copied;
 * - one path segment only. Every write resolves the name against the picked tree, so a name
 *   carrying a separator would address a document outside the folder the user granted.
 */
fun shouldMirrorDocument(name: String, folderUri: String?): Boolean {
    if (folderUri.isNullOrBlank()) return false

    val trimmed = name.trim()
    if (trimmed != name || trimmed.isEmpty()) return false
    if (!trimmed.endsWith(SHAFT_DOT_EXT, ignoreCase = true)) return false
    if (trimmed.length == SHAFT_DOT_EXT.length) return false
    if (trimmed.contains('/') || trimmed.contains('\\')) return false

    return true
}

/**
 * A readable folder name derived from a SAF tree URI alone, for the Settings row to show before
 * (or instead of) the provider's own display name.
 *
 * Used when the provider cannot be queried — a revoked grant or a deleted folder must still
 * leave the row saying *which* folder it lost, not an opaque `content://…` string.
 */
fun mirrorFolderFallbackLabel(uriString: String): String {
    val decoded = decodePercent(uriString.substringAfterLast("/tree/", ""))
        .ifBlank { decodePercent(uriString.substringAfterLast('/')) }

    // Document ids are conventionally "<root>:<path>" (e.g. "primary:Backups/Shafts").
    val afterRoot = decoded.substringAfterLast(':').trim('/')
    val leaf = afterRoot.substringAfterLast('/')
    return leaf.ifBlank { afterRoot.ifBlank { decoded.ifBlank { "Selected folder" } } }
}

private fun decodePercent(raw: String): String {
    if (!raw.contains('%')) return raw
    return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
}
