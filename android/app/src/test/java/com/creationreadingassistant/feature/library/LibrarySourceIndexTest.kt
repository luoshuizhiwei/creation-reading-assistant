package com.creationreadingassistant.feature.library

import com.creationreadingassistant.data.local.dao.LibrarySourceRefDao
import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 来源索引的对账规则测试：观测刷新只更新在册引用的 last_seen_at/availability，
 * 「来源失效」只在完整（未截断）扫描且同根的前提下才允许判定；root_id 缺失的引用
 * 只在能证明归属时回填。这些规则一旦漂移，目录页会把还在树里的书误报成失效，
 * 或者反过来掩盖真实的来源失效。
 */
class LibrarySourceIndexTest {

    private val dao: LibrarySourceRefDao = mockk(relaxed = true)

    private fun ref(
        bookId: String,
        documentId: String?,
        authority: String = "com.android.externalstorage.documents",
        rootId: String? = "primary:Books",
        availability: LibrarySourceAvailability = LibrarySourceAvailability.AVAILABLE,
    ): LibrarySourceRef = LibrarySourceRef(
        bookId = bookId,
        rootId = rootId,
        providerAuthority = authority,
        documentId = documentId,
        displayName = "$bookId.txt",
        format = "txt",
        sizeBytes = 100,
        lastModifiedMillis = 1_000L,
        contentHash = null,
        candidateFingerprint = null,
        lastSeenAtMillis = 0L,
        availability = availability,
    )

    private fun index(): LibrarySourceIndex = LibrarySourceIndex(dao)

    // ===== 计划（纯函数） =====

    @Test
    fun `plan marks seen refs and never missing without allowMissing`() {
        val refs = listOf(
            ref("b1", "primary:Books/a.txt"),
            ref("b2", "primary:Books/gone.txt"),
        )

        val plan = SourceReconciliation.plan(
            refs = refs,
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = setOf("primary:Books/a.txt"),
            allowMissing = false,
        )

        assertEquals(listOf("b1"), plan.seenBookIds)
        assertTrue(plan.missingBookIds.isEmpty())
    }

    @Test
    fun `plan marks same-root absent refs as missing only for complete scans`() {
        val refs = listOf(ref("b2", "primary:Books/gone.txt"))

        val plan = SourceReconciliation.plan(
            refs = refs,
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = emptySet(),
            allowMissing = true,
        )

        assertEquals(listOf("b2"), plan.missingBookIds)
    }

    @Test
    fun `plan never concludes absence for other roots or unanchored refs`() {
        val refs = listOf(
            ref("b3", "primary:Other/x.txt", rootId = "primary:Other"),
            ref("b4", "primary:Books/y.txt", rootId = null),
            ref("b5", documentId = null),
        )

        val plan = SourceReconciliation.plan(
            refs = refs,
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = emptySet(),
            allowMissing = true,
        )

        assertTrue(plan.missingBookIds.isEmpty())
    }

    @Test
    fun `already missing refs are not re-marked`() {
        val refs = listOf(ref("b6", "primary:Books/gone.txt", availability = LibrarySourceAvailability.MISSING))

        val plan = SourceReconciliation.plan(
            refs = refs,
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = emptySet(),
            allowMissing = true,
        )

        assertTrue(plan.missingBookIds.isEmpty())
    }

    @Test
    fun `plan backfills null root ids observed under the tree`() {
        val refs = listOf(ref("b4", "primary:Books/y.txt", rootId = null))

        val plan = SourceReconciliation.plan(
            refs = refs,
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = setOf("primary:Books/y.txt"),
            allowMissing = false,
        )

        assertEquals(listOf("b4"), plan.seenBookIds)
        assertEquals(listOf("primary:Books/y.txt"), plan.backfillDocumentIds)
    }

    // ===== 索引执行（DAO 协作） =====

    @Test
    fun `markObserved refreshes seen refs without missing conclusions`() = runTest {
        coEvery { dao.getAll() } returns listOf(
            ref("b1", "primary:Books/a.txt").toEntity(),
            ref("b2", "primary:Books/gone.txt").toEntity(),
        )

        index().markObserved(
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = listOf("primary:Books/a.txt"),
            nowMillis = 5_000L,
        )

        coVerify(exactly = 1) {
            dao.updateObservation(listOf("b1"), 5_000L, "available")
        }
        coVerify(exactly = 0) { dao.updateAvailability(any(), any()) }
    }

    @Test
    fun `reconcileAfterScan with complete scan marks absent same-root refs missing`() = runTest {
        coEvery { dao.getAll() } returns listOf(
            ref("b2", "primary:Books/gone.txt").toEntity(),
        )

        index().reconcileAfterScan(
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = emptyList(),
            nowMillis = 6_000L,
            allowMissing = true,
        )

        coVerify(exactly = 1) { dao.updateAvailability(listOf("b2"), "missing") }
    }

    @Test
    fun `reconcileAfterScan after truncated scan never marks missing`() = runTest {
        coEvery { dao.getAll() } returns listOf(
            ref("b2", "primary:Books/gone.txt").toEntity(),
        )

        index().reconcileAfterScan(
            authority = "com.android.externalstorage.documents",
            rootId = "primary:Books",
            seenDocumentIds = emptyList(),
            nowMillis = 6_000L,
            allowMissing = false,
        )

        coVerify(exactly = 0) { dao.updateAvailability(any(), any()) }
    }

    private fun LibrarySourceRef.toEntity(): com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity =
        com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity(
            book_id = bookId,
            root_id = rootId,
            provider_authority = providerAuthority,
            document_id = documentId,
            display_name = displayName,
            format = format,
            size = sizeBytes,
            last_modified = lastModifiedMillis,
            content_hash = contentHash,
            candidate_fingerprint = candidateFingerprint,
            last_seen_at = lastSeenAtMillis,
            availability = availability.storageValue,
        )
}
