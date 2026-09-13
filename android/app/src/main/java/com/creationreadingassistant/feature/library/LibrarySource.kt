package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A visible, read-only entry in the user-authorized source library. */
data class LibraryEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long?,
    val isDirectory: Boolean,
    val uri: Uri,
)

enum class LibraryListingIssue {
    NONE,
    UNREADABLE,
}

/**
 * A single-folder snapshot. `truncated` means the provider had more entries than this Module is
 * willing to materialize at once; it never means the directory was completely scanned.
 */
data class LibraryListing(
    val documentId: String,
    val entries: List<LibraryEntry>,
    val truncated: Boolean,
    val issue: LibraryListingIssue,
)

/**
 * Seam for reading a configured source-library folder.
 *
 * The Interface deliberately exposes one operation: callers supply the configured root and an
 * optional child document ID, and receive a bounded snapshot. SAF provider details, cursor
 * cleanup, filtering and sort rules live in the Adapter.
 */
interface LibrarySource {
    suspend fun list(root: LibraryRoot, documentId: String? = null): LibraryListing
}

/**
 * SAF Adapter for [LibrarySource]. It lists only directories and supported reading files, never
 * follows a filesystem path, and limits a single folder snapshot to prevent a wide directory from
 * stalling the import screen.
 */
class SafLibrarySource(
    private val contentResolver: ContentResolver,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : LibrarySource {

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    override suspend fun list(root: LibraryRoot, documentId: String?): LibraryListing = withContext(Dispatchers.IO) {
        val resolvedDocumentId = documentId?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DocumentsContract.getTreeDocumentId(root.treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(root.treeUri, resolvedDocumentId)
        val entries = mutableListOf<LibraryEntry>()
        var truncated = false

        val cursor = try {
            contentResolver.query(childrenUri, PROJECTION, null, null, null)
        } catch (_: SecurityException) {
            return@withContext LibraryListing(
                documentId = resolvedDocumentId,
                entries = emptyList(),
                truncated = false,
                issue = LibraryListingIssue.UNREADABLE,
            )
        }

        if (cursor == null) {
            return@withContext LibraryListing(
                documentId = resolvedDocumentId,
                entries = emptyList(),
                truncated = false,
                issue = LibraryListingIssue.UNREADABLE,
            )
        }

        cursor.use {
            val documentIdIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val displayNameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val lastModifiedIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                if (entries.size >= maxEntries) {
                    truncated = true
                    break
                }
                val id = it.stringAt(documentIdIndex) ?: continue
                val displayName = it.stringAt(displayNameIndex).orEmpty()
                val mimeType = it.stringAt(mimeTypeIndex).orEmpty()
                val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                if (!isDirectory && !BookFileClassifier.isSupported(displayName, mimeType)) continue
                if (isDirectory && BookFileClassifier.shouldSkipName(displayName)) continue

                entries += LibraryEntry(
                    documentId = id,
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = it.positiveLongAt(sizeIndex),
                    lastModifiedMillis = it.positiveLongAt(lastModifiedIndex),
                    isDirectory = isDirectory,
                    uri = DocumentsContract.buildDocumentUriUsingTree(root.treeUri, id),
                )
            }
        }

        LibraryListing(
            documentId = resolvedDocumentId,
            entries = entries.sortedFor(root.sortMode),
            truncated = truncated,
            issue = LibraryListingIssue.NONE,
        )
    }

    private fun android.database.Cursor.stringAt(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun android.database.Cursor.positiveLongAt(index: Int): Long? =
        if (index < 0 || isNull(index)) null else getLong(index).takeIf { it >= 0 }

    private fun List<LibraryEntry>.sortedFor(sortMode: LibrarySortMode): List<LibraryEntry> {
        val nameComparator = compareBy<LibraryEntry> { it.displayName.lowercase(Locale.ROOT) }
        val fileComparator = when (sortMode) {
            LibrarySortMode.NAME -> nameComparator
            LibrarySortMode.MODIFIED_DESC -> compareByDescending<LibraryEntry> { it.lastModifiedMillis ?: Long.MIN_VALUE }
                .then(nameComparator)
            LibrarySortMode.SIZE_DESC -> compareByDescending<LibraryEntry> { it.sizeBytes ?: Long.MIN_VALUE }
                .then(nameComparator)
        }
        return sortedWith(compareByDescending<LibraryEntry> { it.isDirectory }.then(fileComparator))
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 1_000

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
