package com.creationreadingassistant.feature.reader.navigation

import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract.resolveChapteredPosition
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract.targetFromStoredLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S12-① EPUB locator 单一契约（消费侧）：reading_progress.current_location_json
 * 经 [targetFromStoredLocation] 进入回源跳转（搜索结果 / 临时检视返回 / 深链定位，
 * ReaderProgressEffects 的 pendingSourceLocatorJson 链）后的四种存量与新增形态。
 *
 * 契约语义：v2 携带全书 offset 时必须满足 `offset == chapterStart[ci] + co`，
 * 否则安全拒绝；缺省时按章元组派生（天然自洽）；无 v2 的裸 legacy 记录走全书
 * 偏移启发式回退。
 */
class StoredProgressLocatorNavigationTest {

    private val chapterStarts = List(20) { it * 1000 }

    @Test
    fun `imported locator without a global offset resolves by chapter tuple`() {
        // 新契约导入产物（S12 修复后）：v2 只有 ci/co。回源跳转在导入后立即可用，
        // 无需等待一次原生保存 —— 这是 S12-① 的用户可见修复点。
        val target = requireNotNull(
            targetFromStoredLocation(
                "epub-1",
                """{"chapter":8,"offset":321,"locator_v2":{"v":2,"ci":8,"co":321}}""",
            ),
        )

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 8321, chapterIndex = 8, chapterOffset = 321),
            resolveChapteredPosition(target, chapterStarts),
        )
    }

    @Test
    fun `chapter-start locator from a chapter switch save resolves to the chapter start`() {
        // 章变更自动保存产物（EpubRepository 缺省全局后）：精确到本章起点。
        val target = requireNotNull(
            targetFromStoredLocation(
                "epub-1",
                """{"chapter":5,"offset":0,"locator_v2":{"v":2,"ci":5,"co":0}}""",
            ),
        )

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 5000, chapterIndex = 5, chapterOffset = 0),
            resolveChapteredPosition(target, chapterStarts),
        )
    }

    @Test
    fun `legacy imported locator with a fabricated global offset is safely declined`() {
        // 存量旧导入记录（v2.offset = 章内偏移，伪造全局）：跳转被一致性校验拒绝
        // 而不是落到错误位置 —— 安全回退。等待下一次原生保存自愈（见
        // EpubRepositorySaveProgressLocatorTest）。
        val target = requireNotNull(
            targetFromStoredLocation(
                "epub-1",
                """{"chapter":8,"offset":321,"locator_v2":{"v":2,"offset":321,"ci":8,"co":321}}""",
            ),
        )
        assertNotNull(target)

        assertNull(resolveChapteredPosition(target, chapterStarts))
    }

    @Test
    fun `bare legacy progress without locator_v2 falls back to top-level fields`() {
        // 前契约时代记录（无 locator_v2）：全书偏移启发式仍按既有行为工作。
        val target = requireNotNull(
            targetFromStoredLocation("epub-1", """{"chapter":2,"offset":44}"""),
        )

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 44, chapterIndex = 0, chapterOffset = 44),
            resolveChapteredPosition(target, chapterStarts),
        )
    }

    @Test
    fun `self-consistent native locator keeps passing the invariant check`() {
        // 原生退出/防抖保存产物（真实全局 + 章元组一致）：不受本次收口影响。
        // 章起点表均匀 1000 字/章 → 第 3 章起点 3000，全局 = 3000 + 250 = 3250。
        val target = requireNotNull(
            targetFromStoredLocation(
                "epub-1",
                """{"chapter":3,"offset":250,"locator_v2":{"v":2,"offset":3250,"ci":3,"co":250}}""",
            ),
        )

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 3250, chapterIndex = 3, chapterOffset = 250),
            resolveChapteredPosition(target, chapterStarts),
        )
    }
}
