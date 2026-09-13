package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
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
 * P1-A：快速单规则入口必须收敛到 Room（单一状态源）——SelectSingleTocRule
 * 归一化绑定：选中规则启用、其余宽松内置禁用；builtin 只保留标准；
 * 未知 id 安全回退标准；REPLACE 规则不受影响。
 */
class RulesRepositorySingleRuleSelectionTest {

    private lateinit var dao: InMemoryRuleDao
    private lateinit var repo: RulesRepository

    @Before
    fun setUp() {
        dao = InMemoryRuleDao()
        repo = RulesRepository(dao, FakeReaderCorrectionDao())
    }

    private suspend fun enabledLooseIds(bookId: String): Set<String> =
        repo.observe(bookId).first().effectiveToc
            .filter { it.id != BuiltinTocRules.STANDARD_ID }
            .map { it.id }
            .toSet()

    @Test
    fun `selecting a loose rule enables it and disables all other loose bindings`() = runTest {
        repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true))
        repo.execute("b1", RuleCommand.ToggleBuiltinToc("bracketed", enabled = true))

        val result = repo.execute("b1", RuleCommand.SelectSingleTocRule("num-dot"))

        assertEquals(RuleMutationResult.Migrated("num-dot", null), result)
        assertEquals(setOf("num-dot"), enabledLooseIds("b1"))
        val profile = repo.observe("b1").first().effectiveTocProfile
        assertEquals("归一化后的 profile key 必须等于所选规则 id", "num-dot", profile.key)
    }

    @Test
    fun `selecting builtin keeps standard only`() = runTest {
        repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true))

        val result = repo.execute("b1", RuleCommand.SelectSingleTocRule(BuiltinTocRules.STANDARD_ID))

        assertEquals(RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, null), result)
        assertTrue(enabledLooseIds("b1").isEmpty())
        assertEquals(BuiltinTocRules.STANDARD_ID, repo.observe("b1").first().effectiveTocProfile.key)
    }

    @Test
    fun `unknown rule id safely falls back to standard`() = runTest {
        repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true))

        val result = repo.execute("b1", RuleCommand.SelectSingleTocRule("no-such-rule"))

        assertEquals(RuleMutationResult.Migrated(BuiltinTocRules.STANDARD_ID, null), result)
        assertTrue(enabledLooseIds("b1").isEmpty())
        assertEquals("已有绑定应被禁用而非删除", false, dao.getById("builtin:num-dot:b1")?.enabled)
    }

    @Test
    fun `selection is per book and never touches replace rules`() = runTest {
        repo.execute("b1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true))
        repo.execute("b2", RuleCommand.ToggleBuiltinToc("bracketed", enabled = true))
        repo.execute(
            "b1",
            RuleCommand.SaveCustomReplace(id = "r1", name = "替换", pattern = "abc", replacement = "xyz"),
        )

        repo.execute("b1", RuleCommand.SelectSingleTocRule("bracketed"))

        assertEquals("b1 只保留 bracketed", setOf("bracketed"), enabledLooseIds("b1"))
        assertEquals("b2 绑定不受影响", setOf("bracketed"), enabledLooseIds("b2"))
        val replaceRows = dao.state.value.filter { it.kind == RuleKind.REPLACE.name }
        assertEquals("REPLACE 规则必须原样保留", 1, replaceRows.size)
    }

    @Test
    fun `selecting an already-selected rule is idempotent`() = runTest {
        repo.execute("b1", RuleCommand.SelectSingleTocRule("num-dot"))
        repo.execute("b1", RuleCommand.SelectSingleTocRule("num-dot"))

        assertEquals(setOf("num-dot"), enabledLooseIds("b1"))
    }

    /** 内存版 [ReaderTextRuleDao]：镜像 SQL 排序与 upsert/update 语义。 */
    private class InMemoryRuleDao : ReaderTextRuleDao {
        val state = MutableStateFlow<List<ReaderTextRuleEntity>>(emptyList())

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
}
