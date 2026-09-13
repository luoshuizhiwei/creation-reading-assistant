package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import com.creationreadingassistant.feature.library.LibrarySourceRef
import com.creationreadingassistant.feature.library.RecognitionDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录浏览页的纯规则测试。
 *
 * 这里锁定的都是「用户会直接看到」的判定：分层判据决定一行被呈现为「已在书架（确认）」
 * 还是「疑似」（弱判定），默认勾选口径，入架页签归属，以及尺寸/时间在 metadata 缺失时
 * 的降级文案。这些规则一旦漂移，页面就会给出误导性的智能识别结论。
 */
class LibraryBrowserPolicyTest {

    // ===== 分层判定（方案 §5.6） =====

    private fun ref(
        bookId: String = "book-1",
        authority: String = "com.android.externalstorage.documents",
        documentId: String? = "primary:Books/a.txt",
        displayName: String = "a.txt",
        format: String = "txt",
        size: Long = 100,
        lastModified: Long? = 1_000L,
        contentHash: String? = null,
        fingerprint: String? = null,
        rootId: String? = "primary:Books",
    ): LibrarySourceRef = LibrarySourceRef(
        bookId = bookId,
        rootId = rootId,
        providerAuthority = authority,
        documentId = documentId,
        displayName = displayName,
        format = format,
        sizeBytes = size,
        lastModifiedMillis = lastModified,
        contentHash = contentHash,
        candidateFingerprint = fingerprint,
        lastSeenAtMillis = 0L,
        availability = LibrarySourceAvailability.AVAILABLE,
    )

    private fun match(
        authority: String? = "com.android.externalstorage.documents",
        documentId: String? = "primary:Books/a.txt",
        displayName: String = "a.txt",
        format: String? = "txt",
        sizeBytes: Long? = 100L,
        lastModifiedMillis: Long? = 1_000L,
        fingerprint: String? = null,
        refs: List<LibrarySourceRef>,
        liveBookIds: Set<String> = setOf("book-1"),
        shelfTitles: Set<String> = emptySet(),
    ): LibraryShelfMatch? = LibraryBrowserPolicy.shelfMatch(
        authority = authority,
        documentId = documentId,
        displayName = displayName,
        format = format,
        sizeBytes = sizeBytes,
        lastModifiedMillis = lastModifiedMillis,
        fingerprint = fingerprint,
        refs = refs,
        liveBookIds = liveBookIds,
        shelfTitles = shelfTitles,
    )

    @Test
    fun `exact source match confirms book on shelf`() {
        val result = match(refs = listOf(ref()))

        assertEquals(LibraryShelfMatchKind.EXACT_SOURCE, result?.kind)
        assertEquals("book-1", result?.bookId)
    }

    @Test
    fun `same document id with different size reports content updated`() {
        val result = match(sizeBytes = 200L, refs = listOf(ref()))

        assertEquals(LibraryShelfMatchKind.CONTENT_UPDATED, result?.kind)
        assertEquals("book-1", result?.bookId)
    }

    @Test
    fun `same document id with changed mtime reports content updated`() {
        val result = match(lastModifiedMillis = 2_000L, refs = listOf(ref()))

        assertEquals(LibraryShelfMatchKind.CONTENT_UPDATED, result?.kind)
    }

    @Test
    fun `unknown mtime does not fabricate an update`() {
        // provider 不提供时间时（null）不得与导入基线误判为「已变化」。
        val result = match(lastModifiedMillis = null, refs = listOf(ref(lastModified = null)))

        assertEquals(LibraryShelfMatchKind.EXACT_SOURCE, result?.kind)
    }

    @Test
    fun `fingerprint match identifies renamed or moved copy as same content`() {
        val fp = "fp1|40000000|aaaa|bbbb"
        val movedRef = ref(documentId = "primary:Elsewhere/renamed.txt", fingerprint = fp)
        val result = match(
            documentId = "primary:Books/moved.txt",
            displayName = "moved.txt",
            fingerprint = fp,
            refs = listOf(movedRef),
        )

        assertEquals(LibraryShelfMatchKind.SAME_CONTENT, result?.kind)
        assertEquals("book-1", result?.bookId)
    }

    @Test
    fun `format name and size match stays a suspicion without book id`() {
        // 不同来源位置的同名同大小文件：只能给「疑似」，绝不猜 bookId。
        val sameShape = ref(documentId = "primary:Other/b.txt", displayName = "A.txt", format = "txt", size = 100)
        val result = match(
            documentId = "primary:Books/a.txt",
            displayName = "a.txt",
            format = "txt",
            sizeBytes = 100L,
            refs = listOf(sameShape),
        )

        assertEquals(LibraryShelfMatchKind.POSSIBLE, result?.kind)
        assertNull(result?.bookId)
    }

    @Test
    fun `title-only match is the weakest suspicion`() {
        val result = match(
            authority = null,
            documentId = null,
            displayName = "远山.epub",
            format = "epub",
            refs = emptyList(),
            shelfTitles = setOf("远山"),
        )

        assertEquals(LibraryShelfMatchKind.NAME_ONLY, result?.kind)
        assertNull(result?.bookId)
    }

    @Test
    fun `refs of soft-deleted books are ignored`() {
        // 软删除的书行仍在库里，但不应再让来源文件被判定为「已在书架」。
        val result = match(refs = listOf(ref()), liveBookIds = emptySet())

        assertNull(result)
    }

    @Test
    fun `unmatched file without title hint has no match`() {
        val result = match(
            documentId = "primary:Books/other.txt",
            displayName = "other.txt",
            refs = listOf(ref()),
            shelfTitles = setOf("远山"),
        )

        assertNull(result)
    }

    @Test
    fun `exact source beats weaker evidence from other refs`() {
        val fp = "fp1|40000000|aaaa|bbbb"
        val refs = listOf(
            ref(documentId = "primary:Elsewhere/renamed.txt", fingerprint = fp),
            ref(),
        )
        val result = match(
            displayName = "a.txt",
            fingerprint = fp,
            refs = refs,
        )

        assertEquals(LibraryShelfMatchKind.EXACT_SOURCE, result?.kind)
    }

    @Test
    fun `null document id refs never exact match`() {
        val result = match(
            refs = listOf(ref(documentId = null)),
        )

        // documentId 为 null 的引用不能做精确匹配，但名+大小仍可弱判。
        assertEquals(LibraryShelfMatchKind.POSSIBLE, result?.kind)
    }

    @Test
    fun `format mismatch blocks the shape-based suspicion`() {
        val sameShape = ref(documentId = "primary:Other/b.txt", displayName = "a.txt", format = "epub", size = 100)
        val result = match(
            documentId = "primary:Books/a.txt",
            displayName = "a.txt",
            format = "txt",
            sizeBytes = 100L,
            refs = listOf(sameShape),
        )

        assertNull(result)
    }

    // ===== 弱重复判定（不得被当作事实） =====

    @Test
    fun `weak duplicate matches shelf title ignoring case and extension`() {
        val titles = setOf("远山", "测试 txt")

        assertTrue(LibraryBrowserPolicy.isWeakDuplicate("远山.EPUB", titles))
        assertTrue(LibraryBrowserPolicy.isWeakDuplicate("测试 TXT.txt", titles))
        assertTrue(LibraryBrowserPolicy.isWeakDuplicate("  远山  .epub", titles))
    }

    @Test
    fun `weak duplicate is false for unrelated or unnamed files`() {
        val titles = setOf("远山")

        assertFalse(LibraryBrowserPolicy.isWeakDuplicate("另一本书.txt", titles))
        assertFalse(LibraryBrowserPolicy.isWeakDuplicate("远山外传.txt", titles))
        assertFalse(LibraryBrowserPolicy.isWeakDuplicate("", titles))
        assertFalse(LibraryBrowserPolicy.isWeakDuplicate(".epub", titles))
        assertFalse(LibraryBrowserPolicy.isWeakDuplicate("远山.txt", emptySet()))
    }

    @Test
    fun `base name keeps names without extension`() {
        assertEquals("无扩展名文件", LibraryBrowserPolicy.baseName("无扩展名文件"))
        assertEquals("a.b", LibraryBrowserPolicy.baseName("a.b.txt"))
        assertEquals("远山", LibraryBrowserPolicy.baseName(" 远山 .epub"))
    }

    @Test
    fun `format normalization matches the source ref column`() {
        assertEquals("epub", LibraryBrowserPolicy.formatOf("测试 EPUB.epub"))
        assertEquals("txt", LibraryBrowserPolicy.formatOf("测试 TXT.TXT"))
        assertEquals("md", LibraryBrowserPolicy.formatOf("笔记.md"))
        assertEquals("md", LibraryBrowserPolicy.formatOf("笔记.markdown"))
        assertNull(LibraryBrowserPolicy.formatOf("无扩展名"))
        assertNull(LibraryBrowserPolicy.formatOf("档案.pdf"))
    }

    // ===== 分档过滤 =====

    @Test
    fun `each filter exposes only its own bucket`() {
        val decision = RecognitionDecision.RECOMMENDED

        assertTrue(LibraryBrowserPolicy.matchesFilter(decision, null, RecognitionFilter.ALL))
        assertTrue(LibraryBrowserPolicy.matchesFilter(decision, null, RecognitionFilter.RECOMMENDED))
        assertFalse(LibraryBrowserPolicy.matchesFilter(decision, null, RecognitionFilter.IN_SHELF))
        assertFalse(LibraryBrowserPolicy.matchesFilter(decision, null, RecognitionFilter.REJECTED))
    }

    @Test
    fun `recommended filter excludes confirmed and suspected shelf matches`() {
        // 无论是确认还是疑似，只要有入架证据就不出现在「推荐」页签，避免重复导入。
        assertFalse(
            LibraryBrowserPolicy.matchesFilter(
                decision = RecognitionDecision.RECOMMENDED,
                shelfMatch = LibraryShelfMatch(LibraryShelfMatchKind.EXACT_SOURCE, "book-1"),
                filter = RecognitionFilter.RECOMMENDED,
            ),
        )
        assertTrue(
            LibraryBrowserPolicy.matchesFilter(
                decision = RecognitionDecision.RECOMMENDED,
                shelfMatch = LibraryShelfMatch(LibraryShelfMatchKind.NAME_ONLY, null),
                filter = RecognitionFilter.IN_SHELF,
            ),
        )
    }

    @Test
    fun `rejected rows are never part of recommended or in-shelf buckets`() {
        assertFalse(LibraryBrowserPolicy.matchesFilter(RecognitionDecision.REJECTED, null, RecognitionFilter.RECOMMENDED))
        assertFalse(
            LibraryBrowserPolicy.matchesFilter(
                RecognitionDecision.REJECTED,
                LibraryShelfMatch(LibraryShelfMatchKind.EXACT_SOURCE, "book-1"),
                RecognitionFilter.IN_SHELF,
            ),
        )
        assertTrue(
            LibraryBrowserPolicy.matchesFilter(
                RecognitionDecision.REJECTED,
                LibraryShelfMatch(LibraryShelfMatchKind.NAME_ONLY, null),
                RecognitionFilter.REJECTED,
            ),
        )
    }

    @Test
    fun `only recommended decisions without shelf evidence are selected by default`() {
        assertTrue(LibraryBrowserPolicy.selectedByDefault(RecognitionDecision.RECOMMENDED, shelfMatch = null))
        assertFalse(LibraryBrowserPolicy.selectedByDefault(RecognitionDecision.REVIEW, shelfMatch = null))
        assertFalse(LibraryBrowserPolicy.selectedByDefault(RecognitionDecision.REJECTED, shelfMatch = null))
        // 已入架或疑似入架时，即使是推荐项也不默认勾选，避免替用户重复导入。
        assertFalse(
            LibraryBrowserPolicy.selectedByDefault(
                RecognitionDecision.RECOMMENDED,
                LibraryShelfMatch(LibraryShelfMatchKind.POSSIBLE, null),
            ),
        )
        assertFalse(
            LibraryBrowserPolicy.selectedByDefault(
                RecognitionDecision.RECOMMENDED,
                LibraryShelfMatch(LibraryShelfMatchKind.EXACT_SOURCE, "book-1"),
            ),
        )
    }

    // ===== 展示降级 =====

    @Test
    fun `size formatting degrades honestly when size is unknown`() {
        assertEquals("大小未知", LibraryBrowserPolicy.formatSize(null))
        assertEquals("大小未知", LibraryBrowserPolicy.formatSize(-1))
        assertEquals("0 B", LibraryBrowserPolicy.formatSize(0))
        assertEquals("512 B", LibraryBrowserPolicy.formatSize(512))
        assertEquals("2.0 KB", LibraryBrowserPolicy.formatSize(2_048))
        assertEquals("5.0 MB", LibraryBrowserPolicy.formatSize(5L * 1_024 * 1_024))
        assertEquals("1.50 GB", LibraryBrowserPolicy.formatSize(1_610_612_736L))
    }

    @Test
    fun `time formatting degrades honestly when timestamp is missing`() {
        assertEquals("时间未知", LibraryBrowserPolicy.formatTime(null))
        assertEquals("时间未知", LibraryBrowserPolicy.formatTime(0))
        assertEquals("时间未知", LibraryBrowserPolicy.formatTime(-5))
        // 具体小时取决于运行环境时区，只锁定格式形状，避免把 CI 时区写进断言。
        assertTrue(
            LibraryBrowserPolicy.formatTime(1_609_459_200_000L).matches(Regex("""\d{2}-\d{2} \d{2}:\d{2}""")),
        )
    }
}
