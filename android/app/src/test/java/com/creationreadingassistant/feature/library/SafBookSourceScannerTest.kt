package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafBookSourceScannerTest {

    private lateinit var resolver: ContentResolver
    private lateinit var rootUri: Uri

    @Before
    fun setUp() {
        mockkStatic(DocumentsContract::class)
        resolver = mockk()
        rootUri = uri("content://books.example/tree/primary%3ABooks")
        every { DocumentsContract.getTreeDocumentId(rootUri) } returns "primary:Books"
        every { DocumentsContract.buildDocumentUriUsingTree(rootUri, any()) } answers {
            uri("content://books.example/document/${secondArg<String>()}")
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun `directory-only tree is truncated by the visit safety limit`() = runTest {
        val folderIds = listOf("primary:Books") + (0..8).map { "primary:Books/folder-$it" }
        val childrenUris = folderIds.associateWith { documentId ->
            uri("content://books.example/children/${Uri.encode(documentId)}")
        }
        every { DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, any()) } answers {
            childrenUris.getValue(secondArg<String>())
        }
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            val index = folderIds.indexOfFirst { childrenUris.getValue(it) === firstArg<Uri>() }
            if (index in 0 until folderIds.lastIndex) {
                cursor(Row(folderIds[index + 1], "folder-$index", DocumentsContract.Document.MIME_TYPE_DIR))
            } else {
                cursor()
            }
        }

        val result = SafBookSourceScanner(
            contentResolver = resolver,
            maxVisitedEntries = 3,
            maxBookFiles = 10,
            maxDepth = 20,
        ).scan(rootUri)

        assertTrue("安全上限必须覆盖目录项，不能只统计普通文件", result.truncated)
        assertTrue("未被访问到的深层目录不得产出候选", result.bookUris.isEmpty())
    }

    @Test
    fun `candidate cap truncates before a later supported file is returned`() = runTest {
        every { DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, any()) } returns uri("content://books.example/children/root")
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(
            Row("primary:Books/one.txt", "one.txt", "text/plain"),
            Row("primary:Books/two.txt", "two.txt", "text/plain"),
            Row("primary:Books/three.txt", "three.txt", "text/plain"),
        )

        val result = SafBookSourceScanner(
            contentResolver = resolver,
            maxVisitedEntries = 10,
            maxBookFiles = 2,
            maxDepth = 1,
        ).scan(rootUri)

        assertTrue(result.truncated)
        assertFalse(result.bookUris.any { it.toString().endsWith("three.txt") })
    }

    @Test
    fun `cancelling a scan stops before the remaining provider rows are traversed`() = runTest {
        every { DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, any()) } returns uri("content://books.example/children/root")
        val rows = (1..100).map { index ->
            Row("primary:Books/$index.txt", "$index.txt", "text/plain")
        }
        lateinit var scanJob: Job
        var movedRows = 0
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursorWithMoveHook(*rows.toTypedArray()) {
            movedRows += 1
            if (movedRows == 2) scanJob.cancel()
        }

        scanJob = launch {
            SafBookSourceScanner(
                contentResolver = resolver,
                maxVisitedEntries = 200,
                maxBookFiles = 200,
                maxDepth = 1,
            ).scan(rootUri)
        }
        scanJob.join()

        assertTrue(scanJob.isCancelled)
        assertTrue("取消后不得继续遍历全部 provider 行", movedRows < rows.size)
    }

    private fun uri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        return uri
    }

    private fun cursor(vararg rows: Row): Cursor {
        return cursorWithMoveHook(*rows) {}
    }

    private fun cursorWithMoveHook(vararg rows: Row, onMoved: () -> Unit): Cursor {
        var index = -1
        return mockk {
            every { getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID) } returns 0
            every { getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME) } returns 1
            every { getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE) } returns 2
            every { moveToNext() } answers {
                index += 1
                if (index < rows.size) onMoved()
                index < rows.size
            }
            every { isNull(any<Int>()) } returns false
            every { getString(0) } answers { rows[index].documentId }
            every { getString(1) } answers { rows[index].displayName }
            every { getString(2) } answers { rows[index].mimeType }
            every { close() } returns Unit
        }
    }

    private data class Row(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
    )
}
