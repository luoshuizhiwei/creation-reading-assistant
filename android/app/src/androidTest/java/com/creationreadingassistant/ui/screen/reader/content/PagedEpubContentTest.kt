package com.creationreadingassistant.ui.screen.reader.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.reader.doc.DocBlock
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * S2 验收 2：legacy EPUB 分页（readerMode=paged，实际 [PagedEpubView]）必须支持
 * 搜索命中聚焦滚动 —— 章节就绪后按命中块索引 scrollToItem，命中块进入视口。
 * 测试直接注入 [LazyListState]，验证 [PagedEpubView] 的 LazyColumn 使用该状态，
 * 从而 ReaderContentHost 消费 SearchScrollFocusRequest 后能以 scrollToItem 定位命中块。
 */
class PagedEpubContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @OptIn(ExperimentalFoundationApi::class)
    fun scrollToItem_movesSearchFocusBlockIntoView() {
        val listState = LazyListState()
        val targetIndex = 4
        val blocks = List(7) { i ->
            DocBlock.Text("段落${i + 1}" + "长文本内容".repeat(80))
        }
        composeRule.setContent {
            Box(Modifier.height(160.dp)) {
                PagedEpubView(
                    blocks = blocks,
                    fontSize = 16f,
                    lineHeight = 1.5f,
                    fontWeightBold = false,
                    pageMargin = 8f,
                    paperFg = Color.Black,
                    tapZoneMode = "three-zone",
                    pageTurnEffect = "none",
                    chapterIndex = 0,
                    canPrev = false,
                    canNext = true,
                    onPrev = {},
                    onNext = {},
                    onToggleControls = {},
                    onSelectBlock = { _, _ -> },
                    blockGlobalOffsets = List(7) { it * 300 },
                    chapterBase = 0,
                    ttsSentenceRangeInChapter = null,
                    focusBlockIndex = null,
                    sentenceHighlightBg = Color.Yellow,
                    searchHighlightBg = Color.Cyan,
                    bringRequester = BringIntoViewRequester(),
                    searchHitRangeAbs = null,
                    listState = listState,
                )
                // 模拟 ReaderContentHost 消费 SearchScrollFocusRequest 后的定位动作
                LaunchedEffect(Unit) { listState.scrollToItem(targetIndex) }
            }
        }

        composeRule.waitForIdle()

        assertTrue(
            "聚焦块 index=$targetIndex 必须在滚动后进入视口",
            listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex },
        )
    }
}
