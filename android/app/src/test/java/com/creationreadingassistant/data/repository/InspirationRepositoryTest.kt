package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.InspirationVariantDao
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * InspirationRepository 单测：软删除、候选版本采纳的落库细节，
 * 以及各 DAO 委托路径（承接 InspirationViewModelTest 移出的断言强度）。
 */
class InspirationRepositoryTest {

    private lateinit var inspirationDao: InspirationDao
    private lateinit var variantDao: InspirationVariantDao
    private lateinit var repository: InspirationRepository

    @Before
    fun setUp() {
        inspirationDao = mockk(relaxed = true)
        variantDao = mockk(relaxed = true)
        repository = InspirationRepository(inspirationDao, variantDao)
    }

    private fun inspiration(id: String = "i1", body: String = "原正文"): InspirationEntity =
        InspirationEntity(
            id = id,
            title = "标题",
            body = body,
            updated_at = "2026-08-01T10:00:00Z",
            created_at = "2026-08-01T09:00:00Z",
        )

    private fun variant(content: String? = "新正文"): InspirationVariantEntity =
        InspirationVariantEntity(
            id = "v1",
            inspiration_id = "i1",
            kind = "polish",
            content = content,
            created_at = "2026-08-01T12:00:00Z",
        )

    @Test
    fun `observeAllActive delegates to dao`() = runTest {
        every { inspirationDao.observeAllActive() } returns flowOf(listOf(inspiration()))

        val items = repository.observeAllActive().first()

        assertEquals(1, items.size)
        assertEquals("i1", items.first().id)
    }

    @Test
    fun `getById and upsert delegate to dao`() = runTest {
        coEvery { inspirationDao.getById("i1") } returns inspiration()
        val entity = inspiration()

        assertEquals("i1", repository.getById("i1")?.id)
        repository.upsert(entity)

        coVerify(exactly = 1) { inspirationDao.upsert(entity) }
    }

    @Test
    fun `deleteInspiration soft deletes by setting deleted_at`() = runTest {
        coEvery { inspirationDao.getById("i1") } returns inspiration()

        repository.deleteInspiration("i1")

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        assertNotNull(saved.captured.deleted_at)
        assertEquals("标题", saved.captured.title)
    }

    @Test
    fun `deleteInspiration missing id is no-op`() = runTest {
        coEvery { inspirationDao.getById("missing") } returns null

        repository.deleteInspiration("missing")

        coVerify(exactly = 0) { inspirationDao.upsert(any()) }
    }

    @Test
    fun `applyVariant replaces body and bumps updated_at`() = runTest {
        coEvery { inspirationDao.getById("i1") } returns inspiration(body = "原正文")

        repository.applyVariant("i1", variant(content = "新正文"))

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        assertEquals("新正文", saved.captured.body)
        assertEquals("2026-08-01T09:00:00Z", saved.captured.created_at)
        assertNull(saved.captured.deleted_at)
    }

    @Test
    fun `applyVariant with null content is no-op`() = runTest {
        repository.applyVariant("i1", variant(content = null))

        coVerify(exactly = 0) { inspirationDao.getById(any()) }
        coVerify(exactly = 0) { inspirationDao.upsert(any()) }
    }

    @Test
    fun `applyVariant missing inspiration is no-op`() = runTest {
        coEvery { inspirationDao.getById("missing") } returns null

        repository.applyVariant("missing", variant())

        coVerify(exactly = 0) { inspirationDao.upsert(any()) }
    }

    @Test
    fun `variant accessors delegate to variant dao`() = runTest {
        every { variantDao.observeByInspiration("i1") } returns flowOf(listOf(variant()))

        val variants = repository.observeVariants("i1").first()
        assertEquals(1, variants.size)

        val v = variant()
        repository.saveVariant(v)
        coVerify(exactly = 1) { variantDao.upsert(v) }

        repository.deleteVariant("v1")
        coVerify(exactly = 1) { variantDao.delete("v1") }
    }
}
