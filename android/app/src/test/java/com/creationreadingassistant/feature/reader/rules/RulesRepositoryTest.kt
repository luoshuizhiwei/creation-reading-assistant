package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.data.local.dao.ReaderCorrectionDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RulesRepository 深模块行为测试。
 *
 * 以内存 Fake ReaderTextRuleDao（MutableStateFlow 驱动、镜像 SQL 排序与
 * delete/update 的返回语义）验证：快照组装（标准恒开 + 宽松默认关）、逐书隔离、
 * 内置绑定与旧 DataStore 单选迁移、保存前校验不落库、删除边界、替换顺序稳定。
 */
class RulesRepositoryTest {

    private lateinit var dao: FakeReaderTextRuleDao
    private lateinit var correctionDao: FakeReaderCorrectionDao
    private lateinit var repo: RulesRepository

    @Before
    fun setUp() {
        dao = FakeReaderTextRuleDao()
        correctionDao = FakeReaderCorrectionDao()
        repo = RulesRepository(dao, correctionDao)
    }

    private fun entity(
        id: String,
        kind: String = RuleKind.TOC.name,
        name: String = id,
        pattern: String? = null,
        replacement: String = "",
        builtin: Boolean = false,
        enabled: Boolean = true,
        scope: RuleScope = RuleScope.GLOBAL,
        bookId: String? = null,
        position: Int = 0,
    ) = ReaderTextRuleEntity(
        id = id,
        kind = kind,
        name = name,
        pattern = pattern,
        replacement = replacement,
        builtin = builtin,
        enabled = enabled,
        scope = scope.name,
        book_id = bookId,
        position = position,
        created_at = 1L,
        updated_at = 1L,
    )

    // ── E2 单处纠错 ─────────────────────────────────────────────────────

    @Test
    fun `save single correction stores record and appends anchored rule to effective replace`() = runTest {
        val result = repo.execute(
            "b1",
            RuleCommand.SaveSingleCorrection(
                sourceStart = 100,
                sourceEnd = 104,
                findText = "错字",
                replaceText = "正字",
            ),
        )
        assertTrue(result is RuleMutationResult.Saved)
        val id = (result as RuleMutationResult.Saved).id
        assertTrue(id.startsWith("corr-"))

        val snap = repo.observe("b1").first()
        assertEquals(1, snap.corrections.size)
        val record = snap.corrections.single()
        assertEquals(100, record.sourceStart)
        assertEquals(104, record.sourceEnd)
        assertEquals("错字", record.findText)
        assertEquals("正字", record.replaceText)
        assertTrue(record.active)

        // 生效纠错以锚定规则追加：position 最大、id 前缀 correction:、pattern 为空
        val anchored = snap.effectiveReplace.single()
        assertEquals("correction:$id", anchored.id)
        assertEquals("正字", anchored.replacement)
        assertEquals(Int.MAX_VALUE, anchored.position)
        assertEquals(100, anchored.anchor?.sourceStart)
        assertEquals(104, anchored.anchor?.sourceEnd)
        assertEquals("错字", anchored.anchor?.findText)
        // 可管理规则列表不含纠错（纠错有自己的撤销语义）
        assertTrue(snap.replaceRules.isEmpty())
    }

    @Test
    fun `undo correction removes it from effective replace and restore brings it back`() = runTest {
        val saved = repo.execute(
            "b1",
            RuleCommand.SaveSingleCorrection(sourceStart = 0, sourceEnd = 2, findText = "AB", replaceText = "X"),
        ) as RuleMutationResult.Saved

        assertTrue(repo.execute("b1", RuleCommand.UndoCorrection(saved.id)) is RuleMutationResult.Success)
        var snap = repo.observe("b1").first()
        assertTrue(snap.corrections.single().undone)
        assertTrue("撤销后不再参与投影", snap.effectiveReplace.isEmpty())

        assertTrue(repo.execute("b1", RuleCommand.RestoreCorrection(saved.id)) is RuleMutationResult.Success)
        snap = repo.observe("b1").first()
        assertTrue(snap.corrections.single().active)
        assertEquals(1, snap.effectiveReplace.size)
    }

    @Test
    fun `correction validation rejects invalid anchor and no-op replacement`() = runTest {
        assertTrue(
            repo.execute(
                "b1",
                RuleCommand.SaveSingleCorrection(sourceStart = -1, sourceEnd = 3, findText = "abc", replaceText = "x"),
            ) is RuleMutationResult.NotAnchorable,
        )
        assertTrue(
            repo.execute(
                "b1",
                RuleCommand.SaveSingleCorrection(sourceStart = 3, sourceEnd = 3, findText = "abc", replaceText = "x"),
            ) is RuleMutationResult.NotAnchorable,
        )
        assertTrue(
            repo.execute(
                "b1",
                RuleCommand.SaveSingleCorrection(sourceStart = 0, sourceEnd = 3, findText = "  ", replaceText = "x"),
            ) is RuleMutationResult.NotAnchorable,
        )
        assertTrue(
            repo.execute(
                "b1",
                RuleCommand.SaveSingleCorrection(sourceStart = 0, sourceEnd = 3, findText = "abc", replaceText = "abc"),
            ) is RuleMutationResult.NotAnchorable,
        )
        // 校验失败不落库
        assertTrue(repo.observe("b1").first().corrections.isEmpty())
    }

    @Test
    fun `corrections are isolated per book`() = runTest {
        repo.execute(
            "b1",
            RuleCommand.SaveSingleCorrection(sourceStart = 0, sourceEnd = 2, findText = "AB", replaceText = "X"),
        )
        val snapB1 = repo.observe("b1").first()
        assertEquals(1, snapB1.corrections.size)
        assertEquals(1, snapB1.effectiveReplace.size)

        val snapB2 = repo.observe("b2").first()
        assertTrue(snapB2.corrections.isEmpty())
        assertTrue(snapB2.effectiveReplace.isEmpty())
    }

    @Test
    fun `undo or restore unknown or foreign correction returns not found`() = runTest {
        assertTrue(repo.execute("b1", RuleCommand.UndoCorrection("corr-missing")) is RuleMutationResult.NotFound)

        val saved = repo.execute(
            "b1",
            RuleCommand.SaveSingleCorrection(sourceStart = 0, sourceEnd = 2, findText = "AB", replaceText = "X"),
        ) as RuleMutationResult.Saved
        // 他书的纠错不可被他书撤销/恢复
        assertTrue(repo.execute("b2", RuleCommand.UndoCorrection(saved.id)) is RuleMutationResult.NotFound)
        assertTrue(repo.execute("b2", RuleCommand.RestoreCorrection(saved.id)) is RuleMutationResult.NotFound)
    }

    // ── 快照组装：标准恒开 + 宽松默认关 ──────────────────────────────────

    @Test
    fun `standard is always enabled and loose builtins default off`() = runTest {
        val snap = repo.observe("b1").first()

        assertEquals(BuiltinTocRules.seeds.size, snap.tocRules.size)
        val standard = snap.tocRules.first { it.id == BuiltinTocRules.STANDARD_ID }
        assertTrue(standard.enabled)
        assertEquals(RuleScope.GLOBAL, standard.scope)

        val loose = snap.tocRules.filter { it.id != BuiltinTocRules.STANDARD_ID }
        assertTrue("宽松内置应全部出现在可管理列表", loose.size == BuiltinTocRules.seeds.size - 1)
        assertTrue("未绑定的宽松内置默认关闭", loose.all { !it.enabled })

        assertEquals(listOf(BuiltinTocRules.STANDARD_ID), snap.effectiveToc.map { it.id })
        assertTrue(snap.replaceRules.isEmpty())
        assertTrue(snap.effectiveReplace.isEmpty())
    }

    // ── 逐书隔离 / 全局规则 ─────────────────────────────────────────────

    @Test
    fun `global rules are visible everywhere and per-book rules stay isolated`() = runTest {
        val globalToc = repo.execute(
            "b1",
            RuleCommand.SaveCustomToc(id = "g-toc", name = "全局目录", pattern = "^卷\\d+$", scope = RuleScope.GLOBAL),
        ) as RuleMutationResult.Saved
        assertEquals("g-toc", globalToc.id)
        val b1Toc = repo.execute(
            "b1",
            RuleCommand.SaveCustomToc(id = "b1-toc", name = "本书目录", pattern = "^序\\d+$", scope = RuleScope.PER_BOOK),
        ) as RuleMutationResult.Saved
        assertEquals("b1-toc", b1Toc.id)
        val globalReplace = repo.execute(
            "b1",
            RuleCommand.SaveCustomReplace(id = "g-rep", name = "全局替换", pattern = "abc", replacement = "xyz", scope = RuleScope.GLOBAL),
        ) as RuleMutationResult.Saved
        assertEquals("g-rep", globalReplace.id)
        repo.execute(
            "b1",
            RuleCommand.SaveCustomReplace(id = "b1-rep", name = "本书替换", pattern = "foo", replacement = "bar", scope = RuleScope.PER_BOOK),
        )

        val snapA = repo.observe("b1").first()
        assertEquals(setOf("g-toc", "b1-toc"), snapA.tocRules.filter { !it.builtin }.map { it.id }.toSet())
        assertEquals(setOf("g-rep", "b1-rep"), snapA.replaceRules.map { it.id }.toSet())

        val snapB = repo.observe("b2").first()
        assertEquals("别的书的 PER_BOOK 规则不得泄漏", setOf("g-toc"), snapB.tocRules.filter { !it.builtin }.map { it.id }.toSet())
        assertEquals("别的书的 PER_BOOK 规则不得泄漏", setOf("g-rep"), snapB.replaceRules.map { it.id }.toSet())
        assertEquals(listOf(BuiltinTocRules.STANDARD_ID, "g-toc"), snapB.effectiveToc.map { it.id })
    }

    @Test
    fun `per-book custom save binds the passed book id`() = runTest {
        repo.execute(
            "b1",
            RuleCommand.SaveCustomToc(id = "p1", name = "本书", pattern = "^第\\d+章$", scope = RuleScope.PER_BOOK),
        )

        val row = dao.getById("p1")!!
        assertEquals("b1", row.book_id)
        assertEquals(RuleScope.PER_BOOK.name, row.scope)
        assertTrue(repo.observe("b2").first().tocRules.none { it.id == "p1" })
    }

    @Test
    fun `save cannot hijack a per-book rule owned by another book`() = runTest {
        repo.execute(
            "b1",
            RuleCommand.SaveCustomToc(id = "p1", name = "A书", pattern = "^第\\d+章$", scope = RuleScope.PER_BOOK),
        )

        val result = repo.execute(
            "b2",
            RuleCommand.SaveCustomToc(id = "p1", name = "B书改写", pattern = "^序\\d+章$", scope = RuleScope.PER_BOOK),
        )
        assertEquals(RuleMutationResult.Rejected(listOf(RuleValidationError.ID_CONFLICT)), result)
        assertEquals("b1", dao.getById("p1")?.book_id)
    }

    // ── 内置绑定 / 旧 DataStore 单选迁移 ────────────────────────────────

    @Test
    fun `toggling a loose builtin binds only the target book`() = runTest {
        assertEquals(RuleMutationResult.Success, repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true)))

        val snapA = repo.observe("b1").first()
        assertTrue(snapA.tocRules.first { it.id == "num-dot" }.enabled)
        assertTrue(snapA.effectiveToc.any { it.id == "num-dot" })

        val snapB = repo.observe("b2").first()
        assertTrue("别的书不继承绑定", !snapB.tocRules.first { it.id == "num-dot" }.enabled)
        assertTrue(snapB.effectiveToc.none { it.id == "num-dot" })

        assertEquals(RuleMutationResult.Success, repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = false)))
        assertTrue(repo.observe("b1").first().effectiveToc.none { it.id == "num-dot" })
    }

    @Test
    fun `standard builtin cannot be toggled`() = runTest {
        val result = repo.execute("b1", RuleCommand.ToggleBuiltinToc(BuiltinTocRules.STANDARD_ID, enabled = false))
        assertEquals(RuleMutationResult.Rejected(listOf(RuleValidationError.IMMUTABLE_BUILTIN)), result)
        assertTrue(repo.observe("b1").first().tocRules.first { it.id == BuiltinTocRules.STANDARD_ID }.enabled)
    }

    @Test
    fun `legacy loose rule id binds enabled for the book`() = runTest {
        val result = repo.execute("b2", RuleCommand.MigrateLegacyTocRule("num-dot"))
        assertEquals(RuleMutationResult.Migrated("num-dot", "num-dot"), result)
        assertTrue(repo.observe("b2").first().effectiveToc.any { it.id == "num-dot" })
    }

    @Test
    fun `legacy builtin or absent value keeps standard only`() = runTest {
        assertEquals(
            RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, "builtin"),
            repo.execute("b1", RuleCommand.MigrateLegacyTocRule("builtin")),
        )
        assertEquals(
            RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, null),
            repo.execute("b1", RuleCommand.MigrateLegacyTocRule(null)),
        )
        assertEquals(listOf(BuiltinTocRules.STANDARD_ID), repo.observe("b1").first().effectiveToc.map { it.id })
    }

    @Test
    fun `unknown legacy value falls back to standard without crashing`() = runTest {
        val result = repo.execute("b1", RuleCommand.MigrateLegacyTocRule("no-such-rule"))
        assertEquals(RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, "no-such-rule"), result)
        assertEquals(listOf(BuiltinTocRules.STANDARD_ID), repo.observe("b1").first().effectiveToc.map { it.id })
    }

    @Test
    fun `legacy migration is idempotent`() = runTest {
        repo.execute("b1", RuleCommand.MigrateLegacyTocRule("num-dot"))
        repo.execute("b1", RuleCommand.MigrateLegacyTocRule("num-dot"))
        assertTrue(repo.observe("b1").first().effectiveToc.any { it.id == "num-dot" })
    }

    // ── 保存前校验：验证错误不落库 ──────────────────────────────────────

    @Test
    fun `invalid regex is rejected and not persisted`() = runTest {
        val result = repo.execute("b1", RuleCommand.SaveCustomToc(id = "bad", name = "坏规则", pattern = "(abc"))
        assertEquals(RuleMutationResult.Rejected(listOf(RuleValidationError.INVALID_REGEX)), result)
        assertNull(dao.getById("bad"))
        assertTrue(repo.observe("b1").first().tocRules.none { it.id == "bad" })
    }

    @Test
    fun `custom id colliding with a builtin is rejected and not persisted`() = runTest {
        val result = repo.execute("b1", RuleCommand.SaveCustomToc(id = "num-dot", name = "冒名", pattern = "^第\\d+章$"))
        assertEquals(RuleMutationResult.Rejected(listOf(RuleValidationError.IMMUTABLE_BUILTIN)), result)
        assertNull(dao.getById("num-dot"))
        assertTrue("内置规则仍按绑定行映射", !repo.observe("b1").first().tocRules.first { it.id == "num-dot" }.enabled)
    }

    @Test
    fun `replace validation rejects empty-matchable pattern`() = runTest {
        val result = repo.execute("b1", RuleCommand.SaveCustomReplace(id = "r-bad", name = "坏替换", pattern = "a*", replacement = "x"))
        assertEquals(RuleMutationResult.Rejected(listOf(RuleValidationError.EMPTY_MATCH)), result)
        assertNull(dao.getById("r-bad"))
    }

    // ── 删除边界 ────────────────────────────────────────────────────────

    @Test
    fun `delete builtin is impossible while delete custom works`() = runTest {
        repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true))
        assertTrue(repo.observe("b1").first().effectiveToc.any { it.id == "num-dot" })

        assertEquals(
            RuleMutationResult.Rejected(listOf(RuleValidationError.IMMUTABLE_BUILTIN)),
            repo.execute("b1", RuleCommand.DeleteCustom("num-dot")),
        )
        assertTrue("内置绑定未被删除", repo.observe("b1").first().effectiveToc.any { it.id == "num-dot" })

        repo.execute("b1", RuleCommand.SaveCustomToc(id = "c1", name = "自定义", pattern = "^第\\d+章$"))
        assertEquals(RuleMutationResult.Success, repo.execute("b1", RuleCommand.DeleteCustom("c1")))
        assertTrue(repo.observe("b1").first().tocRules.none { it.id == "c1" })

        assertEquals(RuleMutationResult.NotFound, repo.execute("b1", RuleCommand.DeleteCustom("missing")))
    }

    // ── 替换顺序 ────────────────────────────────────────────────────────

    @Test
    fun `replace rules stay in stable position order and reorder persists`() = runTest {
        dao.upsert(entity(id = "r1", kind = RuleKind.REPLACE.name, pattern = "a", replacement = "A", position = 2))
        dao.upsert(entity(id = "r2", kind = RuleKind.REPLACE.name, pattern = "b", replacement = "B", position = 0))
        dao.upsert(entity(id = "r3", kind = RuleKind.REPLACE.name, pattern = "c", replacement = "C", position = 1))

        val snap = repo.observe("b1").first()
        assertEquals(listOf("r2", "r3", "r1"), snap.replaceRules.map { it.id })
        assertEquals(listOf("r2", "r3", "r1"), snap.effectiveReplace.map { it.id })

        assertEquals(
            RuleMutationResult.Success,
            repo.execute("b1", RuleCommand.ReorderRules(RuleKind.REPLACE, listOf("r1", "r2", "r3"))),
        )
        assertEquals(listOf("r1", "r2", "r3"), repo.observe("b1").first().replaceRules.map { it.id })
    }

    @Test
    fun `reorder rejects ids outside the book manageable set`() = runTest {
        repo.execute("b1", RuleCommand.SaveCustomReplace(id = "x1", name = "x", pattern = "a", replacement = "A"))

        val result = repo.execute("b1", RuleCommand.ReorderRules(RuleKind.REPLACE, listOf("x1", "foreign")))
        assertEquals(RuleMutationResult.Rejected(listOf(RuleValidationError.ID_CONFLICT)), result)
    }

    // ── 自定义启停 / 生成 id ────────────────────────────────────────────

    @Test
    fun `toggle custom updates enabled state and missing id is not found`() = runTest {
        repo.execute("b1", RuleCommand.SaveCustomToc(id = "t1", name = "目录", pattern = "^第\\d+章$"))

        assertEquals(RuleMutationResult.Success, repo.execute("b1", RuleCommand.ToggleCustom("t1", enabled = false)))
        val snap = repo.observe("b1").first()
        assertTrue(!snap.tocRules.first { it.id == "t1" }.enabled)
        assertTrue(snap.effectiveToc.none { it.id == "t1" })

        assertEquals(RuleMutationResult.NotFound, repo.execute("b1", RuleCommand.ToggleCustom("missing", enabled = true)))
    }

    @Test
    fun `custom save without id generates a prefixed id and persists`() = runTest {
        val result = repo.execute("b1", RuleCommand.SaveCustomToc(name = "新规则", pattern = "^第\\d+章$")) as RuleMutationResult.Saved
        assertTrue(result.id.startsWith("custom-toc-"))
        assertTrue(repo.observe("b1").first().tocRules.any { it.id == result.id })
    }
}

/**
 * 内存版 [ReaderTextRuleDao]：MutableStateFlow 驱动，镜像 SQL 行为：
 * - 列表恒按 position ASC、id ASC 排序；
 * - upsert 为 REPLACE（同 id 覆盖）；
 * - deleteCustom 仅删 builtin = false；
 * - update* 返回 0/1 表示命中与否。
 */
private class FakeReaderTextRuleDao : ReaderTextRuleDao {

    private val state = MutableStateFlow<List<ReaderTextRuleEntity>>(emptyList())

    override fun observeAll(): Flow<List<ReaderTextRuleEntity>> = state

    override fun observeByKind(kind: String): Flow<List<ReaderTextRuleEntity>> =
        state.map { rows -> rows.filter { it.kind == kind } }

    override fun observeById(id: String): Flow<ReaderTextRuleEntity?> =
        state.map { rows -> rows.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): ReaderTextRuleEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ReaderTextRuleEntity) {
        state.value = (state.value.filterNot { it.id == entity.id } + entity).sortedByPosition()
    }

    override suspend fun deleteCustom(id: String): Int {
        val existing = state.value.firstOrNull { it.id == id } ?: return 0
        if (existing.builtin) return 0
        state.value = state.value.filterNot { it.id == id }
        return 1
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int {
        val idx = state.value.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        val rows = state.value.toMutableList()
        rows[idx] = rows[idx].copy(enabled = enabled, updated_at = updatedAt)
        state.value = rows.sortedByPosition()
        return 1
    }

    override suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int {
        val idx = state.value.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        val rows = state.value.toMutableList()
        rows[idx] = rows[idx].copy(position = position, updated_at = updatedAt)
        state.value = rows.sortedByPosition()
        return 1
    }

    private fun List<ReaderTextRuleEntity>.sortedByPosition(): List<ReaderTextRuleEntity> =
        sortedWith(compareBy<ReaderTextRuleEntity> { it.position }.thenBy { it.id })
}

/**
 * 内存版 [ReaderCorrectionDao]：与 FakeReaderTextRuleDao 同款语义
 * （observeForBook 按 source_start ASC、id ASC；updateStatus 返回 0/1）。
 * internal 以便同模块其他测试（单一规则选择、DocumentLoader、ReaderViewModel）复用。
 */
internal class FakeReaderCorrectionDao : ReaderCorrectionDao {

    private val state = MutableStateFlow<List<ReaderCorrectionEntity>>(emptyList())

    override fun observeForBook(bookId: String): Flow<List<ReaderCorrectionEntity>> =
        state.map { rows ->
            rows.filter { it.book_id == bookId }
                .sortedWith(compareBy<ReaderCorrectionEntity> { it.source_start }.thenBy { it.id })
        }

    override suspend fun listForBook(bookId: String): List<ReaderCorrectionEntity> =
        observeForBook(bookId).first()

    override suspend fun getById(id: String): ReaderCorrectionEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ReaderCorrectionEntity) {
        state.value = state.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun updateStatus(id: String, status: String, updatedAt: Long): Int {
        val idx = state.value.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        val rows = state.value.toMutableList()
        rows[idx] = rows[idx].copy(status = status, updated_at = updatedAt)
        state.value = rows
        return 1
    }

    override suspend fun deleteById(id: String): Int {
        val existing = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.filterNot { it.id == existing.id }
        return 1
    }

    override suspend fun deleteForBook(bookId: String): Int {
        val count = state.value.count { it.book_id == bookId }
        state.value = state.value.filterNot { it.book_id == bookId }
        return count
    }
}
