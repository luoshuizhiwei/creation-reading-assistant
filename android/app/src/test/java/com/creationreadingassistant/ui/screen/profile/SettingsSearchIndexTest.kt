package com.creationreadingassistant.ui.screen.profile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置项搜索索引的契约测试。
 *
 * 索引是一份**显式清单**（见 [SettingsSearchIndex] 文件头），它最大的风险是「代码动了、
 * 清单没动」——用户搜到却跳不到，或搜不到明明存在的项。所以这里做**双向**校验：
 *
 * 1. 清单 → 源码：每条锚点必须真的以 `SettingsAnchor("<anchor>")` 出现在对应子页源码里。
 * 2. 源码 → 清单：子页源码里每个 `SettingsAnchor("...")` 都必须被清单收录。
 *
 * 任一侧漏改都会让测试变红，而不是线上静默失效。
 * 工作目录 = app 模块根（与既有 InspirationFileStructureTest 一致）。
 */
class SettingsSearchIndexTest {

    private val profileDir = File(
        "src/main/java/com/creationreadingassistant/ui/screen/profile",
    )

    private fun sourceOf(page: ProfileSubPage): String? =
        page.sourceFile?.let { File(profileDir, it) }?.takeIf { it.exists() }?.readText()

    /**
     * 源码里出现的所有 `SettingsAnchor("...")` 锚点。
     */
    private fun anchorsInSource(page: ProfileSubPage): Set<String> {
        val text = sourceOf(page) ?: return emptySet()
        return Regex("""SettingsAnchor\(\s*"([^"]+)"\s*\)""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()
    }

    // ============================== 方向一：清单 → 源码 ==============================

    @Test
    fun everyIndexedAnchorExistsInItsSubPageSource() {
        val missing = SettingsSearchIndex.anchoredEntries
            .filter { entry: SettingsEntry ->
                val src = sourceOf(entry.page)
                src == null || !src.contains("SettingsAnchor(\"${entry.anchor}\")")
            }
            .map { entry: SettingsEntry -> "${entry.page}::${entry.anchor} (${entry.title})" }

        assertTrue(
            "以下索引锚点在对应子页源码里找不到 SettingsAnchor(...) —— 设置项被移动/改名/删除了吗？\n" +
                missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun indexedPagesDeclareTheirSourceFile() {
        val covered = ProfileSubPage.entries.filter { it.sourceFile != null }
        assertTrue(
            "至少应覆盖阅读/外观/选区/目标/同步/AI/存储/WebDAV 八个子页，实际 ${covered.size}",
            covered.size >= 8,
        )
        covered.forEach { page ->
            assertTrue(
                "${page.sourceFile} 应存在于 ${profileDir.name}",
                File(profileDir, page.sourceFile!!).exists(),
            )
        }
    }

    @Test
    fun anchorsAreUniqueWithinTheirPage() {
        val duplicates = SettingsSearchIndex.anchoredEntries
            .groupBy { it.page to it.anchor }
            .filter { (_, entries) -> entries.size > 1 }
            // 条件渲染项允许多条索引共用一个父卡片锚点（故意设计），只要标题不同即可
            .filter { (_, entries) -> entries.distinctBy { it.title }.size != entries.size }
            .keys
        assertTrue(
            "同一子页内出现重复锚点且标题相同：$duplicates",
            duplicates.isEmpty(),
        )
    }

    // ============================== 方向二：源码 → 清单 ==============================

    @Test
    fun everyAnchorInSourceIsIndexed() {
        val orphan = mutableListOf<String>()
        ProfileSubPage.entries.forEach { page ->
            val anchoredInIndex = SettingsSearchIndex.anchoredEntries
                .filter { it.page == page }
                .map { it.anchor }
                .toSet()
            anchorsInSource(page)
                .filterNot { it in anchoredInIndex }
                .forEach { orphan += "${page.name}::$it" }
        }
        assertTrue(
            "以下源码锚点没有被搜索索引收录 —— 用户会搜不到这些设置项：\n${orphan.joinToString("\n")}",
            orphan.isEmpty(),
        )
    }

    @Test
    fun anchorsInSourceAreNotEmpty() {
        ProfileSubPage.entries.forEach { page ->
            if (page.sourceFile == null) return@forEach
            anchorsInSource(page).forEach { anchor ->
                assertTrue("${page.name} 存在空锚点", anchor.isNotBlank())
            }
        }
    }

    // ============================== 搜索行为 ==============================

    @Test
    fun searchingConcreteItemFindsItFirst() {
        val results = SettingsSearchIndex.search("字号")
        assertTrue("「字号」应有结果", results.isNotEmpty())
        val first = results.first()
        assertEquals("字号", first.title)
        assertEquals(ProfileSubPage.READER, first.page)
        assertEquals("reader_font_size", first.anchor)
        assertEquals("阅读设置 › 排版设置", first.path)
    }

    @Test
    fun searchingSynonymFindsTheItem() {
        // 「字体大小」是「字号」的俗称，靠 keywords 命中
        val results = SettingsSearchIndex.search("字体大小")
        assertTrue(
            "「字体大小」应命中「字号」，实际：${results.map { it.title }}",
            results.any { it.title == "字号" },
        )
    }

    @Test
    fun searchingPinyinInitialsFindsTheItem() {
        // 拼音首字母缩写检索：「ydsz」命中「阅读设置」，「hylj」命中「护眼滤镜」，「ys」命中「语速」
        val resYd = SettingsSearchIndex.search("ydsz")
        assertTrue("「ydsz」应命中「阅读设置」，实际：${resYd.map { it.title }}", resYd.any { it.title == "阅读设置" })

        val resHj = SettingsSearchIndex.search("hylj")
        assertTrue("「hylj」应命中「护眼滤镜」，实际：${resHj.map { it.title }}", resHj.any { it.title == "护眼滤镜" })

        val resYs = SettingsSearchIndex.search("ys")
        assertTrue("「ys」应命中「语速」，实际：${resYs.map { it.title }}", resYs.any { it.title == "语速" })
    }

    @Test
    fun searchingSubPageNameFallsBackToPageEntry() {
        val results = SettingsSearchIndex.search("同步")
        assertTrue("「同步」应有结果", results.isNotEmpty())
        assertTrue(
            "「同步」应命中同步子页或其中的设置项，实际：${results.map { it.title }}",
            results.any { it.page == ProfileSubPage.SYNC },
        )
    }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(SettingsSearchIndex.search("").isEmpty())
        assertTrue(SettingsSearchIndex.search("   ").isEmpty())
    }

    @Test
    fun nonsenseQueryReturnsNothing() {
        assertTrue(
            SettingsSearchIndex.search("zzz不存在的设置zzz").isEmpty(),
        )
    }

    @Test
    fun everyPageHasAFallbackEntry() {
        ProfileSubPage.entries.forEach { page ->
            val hit = SettingsSearchIndex.search(subPageTitle(page))
                .any { it.page == page && it.kind == SettingsEntryKind.PAGE }
            assertTrue("子页「${subPageTitle(page)}」应能被自己的名字搜到", hit)
        }
    }

    @Test
    fun resultsAreCappedAndOrdered() {
        val results = SettingsSearchIndex.search("设置", limit = 12)
        assertTrue("结果数量应受 limit 约束", results.size <= 12)
        // 完全等于查询词的排最前（如「排版设置」「阅读设置」里更短/更精确的优先）
        val font = SettingsSearchIndex.search("字号")
        assertEquals("字号", font.first().title)
    }

    @Test
    fun conditionalItemsPointAtAlwaysPresentCard() {
        // 「滤镜色温」只在护眼滤镜开启时渲染，索引里挂父卡片锚点，
        // 保证关着的时候也能跳到（高亮整张卡片，用户能看到要先开哪个开关）
        val entry = SettingsSearchIndex.anchoredEntries
            .first { it.title == "滤镜色温" }
        assertEquals("reader_eye_care_section", entry.anchor)
        val src = sourceOf(ProfileSubPage.READER)
        assertTrue(
            "父卡片锚点必须在阅读设置源码里真实存在",
            src!!.contains("SettingsAnchor(\"reader_eye_care_section\")"),
        )
    }

    @Test
    fun indexCoversExpectedVolume() {
        // 索引规模自检：低于这个数说明有子页整页漏收录
        assertTrue(
            "具体设置项应 ≥ 60 条，实际 ${SettingsSearchIndex.anchoredEntries.size}",
            SettingsSearchIndex.anchoredEntries.size >= 60,
        )
    }
}
