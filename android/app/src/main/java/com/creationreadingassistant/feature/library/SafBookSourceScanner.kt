package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class BookSourceScanResult(
    val bookUris: List<Uri>,
    val skippedFiles: Int,
    val unreadableFolders: Int,
    val truncated: Boolean,
)

/**
 * Traverses a user-selected SAF tree without resolving it to a filesystem path.
 *
 * The limits protect users from accidentally selecting an entire storage volume. The scanner
 * only returns supported reading files; importing and duplicate detection remain the caller's
 * responsibility.
 */
class SafBookSourceScanner(
    private val contentResolver: ContentResolver,
    private val maxVisitedEntries: Int = 2_000,
    private val maxBookFiles: Int = 500,
    private val maxDepth: Int = 16,
) {
    suspend fun scan(treeUri: Uri): BookSourceScanResult = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        val books = mutableListOf<Uri>()
        var visitedEntries = 0
        var skippedFiles = 0
        var unreadableFolders = 0
        var truncated = false

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parentId, depth) = queue.removeFirst()
            if (depth > maxDepth) {
                truncated = true
                continue
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = runCatching {
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrNull()
            if (cursor == null) {
                unreadableFolders += 1
                continue
            }
            cursor.use {
                val idIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    coroutineContext.ensureActive()
                    // A deep tree made only of folders must not bypass the traversal budget.
                    // Count every provider row before deciding whether it is a file or directory.
                    if (visitedEntries >= maxVisitedEntries || books.size >= maxBookFiles) {
                        truncated = true
                        break
                    }
                    visitedEntries += 1
                    val documentId = if (idIndex >= 0) it.getString(idIndex) else continue
                    val name = if (nameIndex >= 0) it.getString(nameIndex).orEmpty() else ""
                    val mimeType = if (mimeIndex >= 0) it.getString(mimeIndex).orEmpty() else ""
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!BookFileClassifier.shouldSkipName(name)) {
                            queue.add(documentId to depth + 1)
                        }
                        continue
                    }
                    if (BookFileClassifier.isSupported(name, mimeType)) {
                        books += DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    } else {
                        skippedFiles += 1
                    }
                }
            }
            if (truncated) break
        }

        BookSourceScanResult(
            bookUris = books.distinctBy(Uri::toString),
            skippedFiles = skippedFiles,
            unreadableFolders = unreadableFolders,
            truncated = truncated,
        )
    }
}

object BookFileClassifier {
    private val supportedExtensions = setOf("epub", "txt", "md", "markdown")
    private val ignoredNames = setOf(
        ".nomedia",
        "thumbs.db",
        "desktop.ini",
        ".ds_store",
    )

    fun isSupported(name: String, mimeType: String? = null): Boolean {
        if (shouldSkipName(name)) return false
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in supportedExtensions) return true
        return mimeType?.lowercase() in setOf(
            "application/epub+zip",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
        )
    }

    fun shouldSkipName(name: String): Boolean {
        val normalized = name.trim().lowercase()
        return normalized.isBlank() ||
            normalized.startsWith(".") ||
            normalized in ignoredNames ||
            normalized.endsWith(".part") ||
            normalized.endsWith(".tmp")
    }
}
