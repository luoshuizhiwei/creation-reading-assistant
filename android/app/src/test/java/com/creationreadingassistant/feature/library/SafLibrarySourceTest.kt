package com.creationreadingassistant.feature.library

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafLibrarySourceTest {

    private lateinit var resolver: ContentResolver
    private lateinit var rootUri: Uri
    private lateinit var childrenUri: Uri

    @Before
    fun setUp() {
        mockkStatic(DocumentsContract::class)
        resolver = mockk()
        rootUri = uri("content://books.example/tree/primary%3ABooks")
        childrenUri = uri("content://books.example/children")
        every { DocumentsContract.getTreeDocumentId(rootUri) } returns "primary:Books"
        every { DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, "primary:Books") } returns childrenUri
        every { DocumentsContract.buildDocumentUriUsingTree(rootUri, any()) } answers {
            uri("content://books.example/document/${secondArg<String>()}")
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun `directories stay first and files use the configured sort`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(
            Row("primary:Books/小说", "小说", DocumentsContract.Document.MIME_TYPE_DIR, 0, 10),
            Row("primary:Books/z.txt", "z.txt", "text/plain", 2, 30),
            Row("primary:Books/a.epub", "a.epub", "application/epub+zip", 9, 20),
            Row("primary:Books/cache.tmp", "cache.tmp", "text/plain", 5, 40),
        )

        val listing = SafLibrarySource(resolver).list(root(LibrarySortMode.SIZE_DESC))

        assertEquals(LibraryListingIssue.NONE, listing.issue)
        assertFalse(listing.truncated)
        assertEquals(listOf("小说", "a.epub", "z.txt"), listing.entries.map { it.displayName })
        assertTrue(listing.entries.first().isDirectory)
        assertEquals("primary:Books", listing.documentId)
    }

    @Test
    fun `security failure is an explicit unreadable result`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } throws SecurityException("grant revoked")

        val listing = SafLibrarySource(resolver).list(root(LibrarySortMode.NAME))

        assertEquals(LibraryListingIssue.UNREADABLE, listing.issue)
        assertTrue(listing.entries.isEmpty())
    }

    @Test
    fun `added desc sorts newest first`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(
            Row("primary:Books/old.txt", "old.txt", "text/plain", 1, 10),
            Row("primary:Books/new.epub", "new.epub", "application/epub+zip", 1, 90),
            Row("primary:Books/mid.md", "mid.md", "text/markdown", 1, 50),
        )

        val listing = SafLibrarySource(resolver).list(root(LibrarySortMode.ADDED_DESC))

        // 「最近添加」= 来源文件修改时间倒序：文件拷入目录时该时间即拷贝时间。
        assertEquals(listOf("new.epub", "mid.md", "old.txt"), listing.entries.map { it.displayName })
    }

    @Test
    fun `added desc keeps directories first and sinks unknown timestamps last`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor(
            Row("primary:Books/小说", "小说", DocumentsContract.Document.MIME_TYPE_DIR, 0, 5),
            Row("primary:Books/noTime.txt", "noTime.txt", "text/plain", 1, -1),
            Row("primary:Books/newest.epub", "newest.epub", "application/epub+zip", 1, 90),
        )

        val listing = SafLibrarySource(resolver).list(root(LibrarySortMode.ADDED_DESC))

        // 目录恒在最前；时间未知（provider 返回负数被过滤成 null）的文件沉底，
        // 不能因为 null 参与比较而排到最前。
        assertEquals(listOf("小说", "newest.epub", "noTime.txt"), listing.entries.map { it.displayName })
        assertTrue(listing.entries.first().isDirectory)
    }

    private fun root(sortMode: LibrarySortMode) = LibraryRoot(
        treeUri = rootUri,
        displayName = "书籍",
        lastLocationDocumentId = null,
        sortMode = sortMode,
    )

    private fun uri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        return uri
    }

    private fun cursor(vararg rows: Row): Cursor {
        var index = -1
        return mockk {
            every { getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID) } returns 0
            every { getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME) } returns 1
            every { getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE) } returns 2
            every { getColumnIndex(DocumentsContract.Document.COLUMN_SIZE) } returns 3
            every { getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED) } returns 4
            every { moveToNext() } answers { ++index < rows.size }
            every { isNull(any<Int>()) } returns false
            every { getString(0) } answers { rows[index].documentId }
            every { getString(1) } answers { rows[index].displayName }
            every { getString(2) } answers { rows[index].mimeType }
            every { getLong(3) } answers { rows[index].size }
            every { getLong(4) } answers { rows[index].lastModified }
            every { close() } returns Unit
        }
    }

    private data class Row(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
    )
}
