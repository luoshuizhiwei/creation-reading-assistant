package com.creationreadingassistant.feature.library

import android.net.Uri
import com.creationreadingassistant.testutil.testDataStoreContext
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRootStoreTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: LibraryRootStore

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { contentUri(firstArg()) }
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = LibraryRootStore(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkStatic(Uri::class)
    }

    @Test
    fun `saving a root publishes a normalized root`() = runTest {
        store.clearRoot()
        store.setSortMode(LibrarySortMode.NAME)
        val treeUri = contentUri("content://books.example/tree/primary%3ABooks")

        store.saveRoot(treeUri, "  我的书籍  ")

        val root = store.root.first { it?.treeUri?.toString() == treeUri.toString() }
        assertEquals("我的书籍", root?.displayName)
        assertNull(root?.lastLocationDocumentId)
        assertEquals(LibrarySortMode.NAME, root?.sortMode)
    }

    @Test
    fun `location and sort are retained for the configured root`() = runTest {
        store.clearRoot()
        val treeUri = contentUri("content://books.example/tree/primary%3ABooks")
        store.saveRoot(treeUri, "书籍")

        store.setLastLocation("primary:Books/科幻")
        store.setSortMode(LibrarySortMode.MODIFIED_DESC)

        val root = store.root.first {
            it?.lastLocationDocumentId == "primary:Books/科幻" &&
                it.sortMode == LibrarySortMode.MODIFIED_DESC
        }
        assertEquals(treeUri.toString(), root?.treeUri?.toString())
    }

    @Test
    fun `changing the root clears the old browsing location`() = runTest {
        store.clearRoot()
        store.saveRoot(contentUri("content://books.example/tree/primary%3AOld"), "旧目录")
        store.setLastLocation("primary:Old/已读")

        val replacement = contentUri("content://books.example/tree/primary%3ANew")
        store.saveRoot(replacement, "新目录")

        val root = store.root.first { it?.treeUri?.toString() == replacement.toString() }
        assertNull(root?.lastLocationDocumentId)
        assertEquals("新目录", root?.displayName)
    }

    private fun contentUri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"
        every { uri.toString() } returns value
        return uri
    }
}
