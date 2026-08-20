package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TaxonomyRepository 分类/标签/书单 CRUD 行为测试：
 * - 创建时 trim 名称、生成带前缀的 UUID、type 缺省为 "book"
 * - 新建 sort_order = max+1（排在现有项末尾）
 * - 上移/下移一位仅交换相邻两项 sort_order，边界安全无操作
 * - 空列表短路（不触碰 DAO）
 * - 重命名/删除/关联委托
 */
class TaxonomyRepositoryTest {

    private lateinit var tagDao: TagDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var shelfDao: ShelfDao
    private lateinit var bookTagDao: BookTagDao
    private lateinit var bookCategoryDao: BookCategoryDao
    private lateinit var shelfBookDao: ShelfBookDao
    private lateinit var repo: TaxonomyRepository

    @Before
    fun setUp() {
        tagDao = mockk {
            every { observeAllActive() } returns flowOf(emptyList())
            coEvery { getAllActive() } returns emptyList()
            coEvery { upsert(any()) } returns Unit
            coEvery { rename(any(), any(), any()) } returns Unit
            coEvery { softDelete(any(), any()) } returns Unit
            coEvery { updateSortOrders(any(), any()) } returns Unit
        }
        categoryDao = mockk {
            every { observeAllActive() } returns flowOf(emptyList())
            coEvery { getAllActive() } returns emptyList()
            coEvery { upsert(any()) } returns Unit
            coEvery { rename(any(), any(), any()) } returns Unit
            coEvery { softDelete(any(), any()) } returns Unit
            coEvery { updateCoverTone(any(), any(), any()) } returns Unit
            coEvery { updateSortOrders(any(), any()) } returns Unit
        }
        shelfDao = mockk {
            every { observeAllActive() } returns flowOf(emptyList())
            coEvery { getAllActive() } returns emptyList()
            coEvery { upsert(any()) } returns Unit
            coEvery { rename(any(), any(), any()) } returns Unit
            coEvery { softDelete(any(), any()) } returns Unit
            coEvery { updateSortOrders(any(), any()) } returns Unit
        }
        bookTagDao = mockk {
            coEvery { upsert(any()) } returns Unit
            coEvery { upsertAll(any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
            coEvery { getBookIds(any()) } returns emptyList()
        }
        bookCategoryDao = mockk {
            coEvery { replaceForBooks(any(), any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
        }
        shelfBookDao = mockk {
            coEvery { upsertAll(any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
            coEvery { getBookIds(any()) } returns emptyList()
        }
        repo = TaxonomyRepository(tagDao, categoryDao, shelfDao, bookTagDao, bookCategoryDao, shelfBookDao)
    }

    @Test
    fun `createTag trims name and uses tag prefix id`() = runTest {
        val id = repo.createTag("  悬疑  ", "  book ")

        assertTrue(id.startsWith("tag-"))
        val saved = slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals("悬疑", saved.captured.name)
        assertEquals("book", saved.captured.type)
        assertEquals(id, saved.captured.id)
    }

    @Test
    fun `createTag defaults blank type to book`() = runTest {
        repo.createTag("  悬疑  ")

        val saved = slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals("book", saved.captured.type)
    }

    @Test
    fun `createCategory and createShelf trim names`() = runTest {
        val catId = repo.createCategory("  科幻  ")
        val shelfId = repo.createShelf("  待读  ")

        assertTrue(catId.startsWith("cat-"))
        assertTrue(shelfId.startsWith("shelf-"))
        val cat = slot<CategoryEntity>()
        val shelf = slot<ShelfEntity>()
        coVerify(exactly = 1) { categoryDao.upsert(capture(cat)) }
        coVerify(exactly = 1) { shelfDao.upsert(capture(shelf)) }
        assertEquals("科幻", cat.captured.name)
        assertEquals("待读", shelf.captured.name)
    }

    // ─── 新建项目时 sort_order = max+1（排末尾） ──────────────────────

    @Test
    fun `createTag appends sort_order max plus 1`() = runTest {
        val existing = listOf(
            TagEntity(id = "t-old1", name = "旧1", sort_order = 0, updated_at = "u1", created_at = "c1"),
            TagEntity(id = "t-old2", name = "旧2", sort_order = 7, updated_at = "u2", created_at = "c2"),
        )
        coEvery { tagDao.getAllActive() } returns existing

        repo.createTag("新标签")

        val saved = slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals(8, saved.captured.sort_order) // max(7) + 1
    }

    @Test
    fun `createTag first item uses sort_order 0`() = runTest {
        // getAllActive() 已默认 emptyList；maxOfOrNull -> null -> -1，+1 = 0
        repo.createTag("首个标签")

        val saved = slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals(0, saved.captured.sort_order)
    }

    @Test
    fun `createCategory and createShelf append sort_order`() = runTest {
        val existingCats = listOf(
            CategoryEntity(id = "c-old", name = "旧分类", sort_order = 2, updated_at = "u", created_at = "c"),
        )
        val existingShelves = listOf(
            ShelfEntity(id = "s-old1", name = "旧书单1", sort_order = 0, updated_at = "u1", created_at = "c1"),
            ShelfEntity(id = "s-old2", name = "旧书单2", sort_order = 3, updated_at = "u2", created_at = "c2"),
        )
        coEvery { categoryDao.getAllActive() } returns existingCats
        coEvery { shelfDao.getAllActive() } returns existingShelves

        repo.createCategory("新分类")
        repo.createShelf("新书单")

        val cat = slot<CategoryEntity>()
        val shelf = slot<ShelfEntity>()
        coVerify(exactly = 1) { categoryDao.upsert(capture(cat)) }
        coVerify(exactly = 1) { shelfDao.upsert(capture(shelf)) }
        assertEquals(3, cat.captured.sort_order) // max(2) + 1
        assertEquals(4, shelf.captured.sort_order) // max(3) + 1
    }

    // ─── 排序：上移/下移一位只交换相邻两项 sort_order ─────────────────

    @Test
    fun `moveTagUp swaps sort_order with previous item`() = runTest {
        val tags = listOf(
            TagEntity(id = "t-A", name = "A", sort_order = 10, updated_at = "u", created_at = "t1"),
            TagEntity(id = "t-B", name = "B", sort_order = 20, updated_at = "u", created_at = "t2"),
            TagEntity(id = "t-C", name = "C", sort_order = 30, updated_at = "u", created_at = "t3"),
        )
        coEvery { tagDao.getAllActive() } returns tags

        repo.moveTagUp("t-B") // B 与 A 交换

        val updates = slot<List<Pair<String, Int>>>()
        val now = slot<String>()
        coVerify(exactly = 1) { tagDao.updateSortOrders(capture(updates), capture(now)) }
        // 期望只交换相邻两项：A 拿 B 的 sort_order(20)，B 拿 A 的 sort_order(10)
        assertEquals(2, updates.captured.size)
        assertEquals("t-A" to 20, updates.captured[0])
        assertEquals("t-B" to 10, updates.captured[1])
        assertTrue(now.captured.isNotBlank())
    }

    @Test
    fun `moveTagDown swaps sort_order with next item`() = runTest {
        val tags = listOf(
            TagEntity(id = "t-A", name = "A", sort_order = 0, updated_at = "u", created_at = "t1"),
            TagEntity(id = "t-B", name = "B", sort_order = 1, updated_at = "u", created_at = "t2"),
            TagEntity(id = "t-C", name = "C", sort_order = 2, updated_at = "u", created_at = "t3"),
        )
        coEvery { tagDao.getAllActive() } returns tags

        repo.moveTagDown("t-B") // B 与 C 交换

        val updates = slot<List<Pair<String, Int>>>()
        coVerify(exactly = 1) { tagDao.updateSortOrders(capture(updates), any()) }
        assertEquals(2, updates.captured.size)
        assertEquals("t-C" to 1, updates.captured[0])
        assertEquals("t-B" to 2, updates.captured[1])
    }

    @Test
    fun `moveCategoryUp swaps sort_order with previous item`() = runTest {
        val categories = listOf(
            CategoryEntity(id = "c-A", name = "A", sort_order = 3, updated_at = "u", created_at = "t1"),
            CategoryEntity(id = "c-B", name = "B", sort_order = 8, updated_at = "u", created_at = "t2"),
        )
        coEvery { categoryDao.getAllActive() } returns categories

        repo.moveCategoryUp("c-B")

        val updates = slot<List<Pair<String, Int>>>()
        coVerify(exactly = 1) { categoryDao.updateSortOrders(capture(updates), any()) }
        assertEquals(listOf("c-A" to 8, "c-B" to 3), updates.captured)
    }

    @Test
    fun `moveShelfDown swaps sort_order with next item`() = runTest {
        val shelves = listOf(
            ShelfEntity(id = "s-A", name = "A", sort_order = 4, updated_at = "u", created_at = "t1"),
            ShelfEntity(id = "s-B", name = "B", sort_order = 9, updated_at = "u", created_at = "t2"),
        )
        coEvery { shelfDao.getAllActive() } returns shelves

        repo.moveShelfDown("s-A")

        val updates = slot<List<Pair<String, Int>>>()
        coVerify(exactly = 1) { shelfDao.updateSortOrders(capture(updates), any()) }
        assertEquals(listOf("s-B" to 4, "s-A" to 9), updates.captured)
    }

    @Test
    fun `moveTag ordering respects sort_order ASC then created_at ASC`() = runTest {
        // sort_order 相同的两个候选项必须用 created_at 决定谁与目标相邻。
        val tagsOutOfOrderFromDao = listOf(
            TagEntity(id = "t-target", name = "目标", sort_order = 10, updated_at = "u", created_at = "2026-08-20T03:00:00Z"),
            TagEntity(id = "t-late", name = "后创建", sort_order = 5, updated_at = "u", created_at = "2026-08-20T02:00:00Z"),
            TagEntity(id = "t-early", name = "先创建", sort_order = 5, updated_at = "u", created_at = "2026-08-20T01:00:00Z"),
        )
        coEvery { tagDao.getAllActive() } returns tagsOutOfOrderFromDao

        repo.moveTagUp("t-target")

        val updates = slot<List<Pair<String, Int>>>()
        coVerify(exactly = 1) { tagDao.updateSortOrders(capture(updates), any()) }
        assertEquals(2, updates.captured.size)
        assertEquals("t-late" to 10, updates.captured[0])
        assertEquals("t-target" to 5, updates.captured[1])
    }

    // ─── 边界：首项上移 / 末项下移安全无操作 ──────────────────────────

    @Test
    fun `moveTagUp first item is no-op`() = runTest {
        val tags = listOf(
            TagEntity(id = "t-A", name = "A", sort_order = 0, updated_at = "u", created_at = "t1"),
            TagEntity(id = "t-B", name = "B", sort_order = 1, updated_at = "u", created_at = "t2"),
        )
        coEvery { tagDao.getAllActive() } returns tags

        repo.moveTagUp("t-A")

        coVerify(exactly = 0) { tagDao.updateSortOrders(any(), any()) }
    }

    @Test
    fun `moveTagDown last item is no-op`() = runTest {
        val tags = listOf(
            TagEntity(id = "t-A", name = "A", sort_order = 0, updated_at = "u", created_at = "t1"),
            TagEntity(id = "t-B", name = "B", sort_order = 1, updated_at = "u", created_at = "t2"),
        )
        coEvery { tagDao.getAllActive() } returns tags

        repo.moveTagDown("t-B")

        coVerify(exactly = 0) { tagDao.updateSortOrders(any(), any()) }
    }

    @Test
    fun `moveCategoryDown last item is no-op`() = runTest {
        val cats = listOf(
            CategoryEntity(id = "c-1", name = "仅项", sort_order = 0, updated_at = "u", created_at = "c"),
        )
        coEvery { categoryDao.getAllActive() } returns cats

        repo.moveCategoryDown("c-1")

        coVerify(exactly = 0) { categoryDao.updateSortOrders(any(), any()) }
    }

    @Test
    fun `moveShelfUp first item is no-op`() = runTest {
        val shelves = listOf(
            ShelfEntity(id = "s-1", name = "S1", sort_order = 0, updated_at = "u1", created_at = "c1"),
            ShelfEntity(id = "s-2", name = "S2", sort_order = 1, updated_at = "u2", created_at = "c2"),
        )
        coEvery { shelfDao.getAllActive() } returns shelves

        repo.moveShelfUp("s-1")

        coVerify(exactly = 0) { shelfDao.updateSortOrders(any(), any()) }
    }

    @Test
    fun `rename and tone update trim and pass non-empty timestamps`() = runTest {
        repo.renameTag("t1", "  新标签  ")
        repo.renameCategory("c1", "  新分类  ")
        repo.renameShelf("s1", "  新书单  ")
        repo.updateCategoryTone("c1", "  warm  ")

        coVerify(exactly = 1) { tagDao.rename("t1", "新标签", any()) }
        coVerify(exactly = 1) { categoryDao.rename("c1", "新分类", any()) }
        coVerify(exactly = 1) { shelfDao.rename("s1", "新书单", any()) }
        coVerify(exactly = 1) { categoryDao.updateCoverTone("c1", "warm", any()) }
    }

    @Test
    fun `delete methods soft delete with timestamps`() = runTest {
        repo.deleteTag("t1")
        repo.deleteCategory("c1")
        repo.deleteShelf("s1")

        coVerify(exactly = 1) { tagDao.softDelete("t1", any()) }
        coVerify(exactly = 1) { categoryDao.softDelete("c1", any()) }
        coVerify(exactly = 1) { shelfDao.softDelete("s1", any()) }
    }

    @Test
    fun `addTagToBooks maps to book tag entities`() = runTest {
        repo.addTagToBooks(listOf("b1", "b2"), "t9")

        val refs = slot<List<BookTagEntity>>()
        coVerify(exactly = 1) { bookTagDao.upsertAll(capture(refs)) }
        assertEquals(listOf(BookTagEntity("b1", "t9"), BookTagEntity("b2", "t9")), refs.captured)
    }

    @Test
    fun `addBooksToShelf maps to shelf book entities`() = runTest {
        repo.addBooksToShelf(listOf("b1", "b2"), "sh1")

        val refs = slot<List<ShelfBookEntity>>()
        coVerify(exactly = 1) { shelfBookDao.upsertAll(capture(refs)) }
        assertEquals(
            listOf(ShelfBookEntity("sh1", "b1"), ShelfBookEntity("sh1", "b2")),
            refs.captured,
        )
    }

    @Test
    fun `observe ids for books short circuit on empty list`() = runTest {
        assertEquals(emptyList<String>(), repo.observeTagIdsForBooks(emptyList()).first())
        assertEquals(emptyList<String>(), repo.observeCategoryIdsForBooks(emptyList()).first())
        assertEquals(emptyList<String>(), repo.observeShelfIdsForBooks(emptyList()).first())
        coVerify(exactly = 0) { bookTagDao.observeTagIdsForBooks(any()) }
        coVerify(exactly = 0) { bookCategoryDao.observeCategoryIdsForBooks(any()) }
        coVerify(exactly = 0) { shelfBookDao.observeShelfIdsForBooks(any()) }
    }

    @Test
    fun `remove tag and book from shelf delegate`() = runTest {
        repo.removeTagFromBook("b1", "t1")
        repo.removeBookFromShelf("b1", "sh1")

        coVerify(exactly = 1) { bookTagDao.remove("b1", "t1") }
        coVerify(exactly = 1) { shelfBookDao.remove("sh1", "b1") }
    }
}
