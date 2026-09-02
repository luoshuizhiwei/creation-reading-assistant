package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentLoader
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentState
import com.creationreadingassistant.feature.reader.pager.ScrollingTxtChapterSource
import com.creationreadingassistant.feature.reader.pager.loadScrollUnitContent
import com.creationreadingassistant.feature.reader.pager.preparePagedReplacement
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection
import com.creationreadingassistant.ui.theme.ShimmerBlock
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * ReaderContentHost 分支 4：TXT 流式/纯文本（else 分支，LazyColumn + 读取单元）。
 *
 * 从 [ReaderContentHost] 的 `when` 分支纯结构搬运而来，异步加载 effect 与
 * 释放时序逐字保留。
 */
@Suppress("LongMethod")
@Composable
internal fun ReaderContentHostPlainTextBranch(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    readerFontFamily: FontFamily,
) {
    val settings = state.settings
    val s = state.source
    val txtDoc = s.txtStreamingDocument
    val reducedMotion = rememberReducedMotion()
    // 加载占位高度：按 3 行正文估算（与正文行高同源），sp → dp 需经当前密度
    val loadingPlaceholderHeight = with(LocalDensity.current) {
        (settings.readerSettings.fontSize * settings.readerSettings.lineHeight * 3).sp.toDp()
    }
    val scope = rememberCoroutineScope()
    // 滚动 TXT 的显示文本必须来自完整逻辑章投影。ReadingUnit 仅作为有界渲染
    // 单元，所有选择、搜索、高亮和 TTS 的持久化坐标都由 projection 映射回 source。
    val scrollSource = remember(
        s.bid,
        txtDoc,
        s.plainContent,
        s.readingUnits,
        s.replaceRules,
    ) {
        val delegate = if (txtDoc != null) {
            ScrollingTxtChapterSource.fromDocument(txtDoc)
        } else {
            ScrollingTxtChapterSource.fromText(s.plainContent, s.readingUnits)
        }
        if (s.bid.isBlank()) {
            delegate
        } else {
            preparePagedReplacement(
                delegate = delegate,
                bookId = s.bid,
                rules = s.replaceRules,
            ).source
        }
    }
    val unitContentLoader = remember { ScrollUnitContentLoader() }
    LaunchedEffect(scrollSource) {
        unitContentLoader.switchSource(scrollSource)
    }
    // 单次轻点手势仲裁门：readOnly 文本域观察器按下时 claim，父级抬起时
    // consumeClaimIfAny；正文轻点走子路径，父层只负责空白/边距，避免双切换。
    val tapGate = remember { ReaderTapToggleGate() }
    // 中央点击统一唤出/隐藏（父级 tap observation，不 consume）：TXT 滚动分支的
    // 重试点击（UnitTextFailedItem）优先消费；readOnly 文本域内的中央短按由
    // 子观察器（readerTextFieldCenterTapToToggle）负责，父层保留空白/边距路径。
    Box(
        Modifier
            .fillMaxSize()
            .readerCenterTapToToggle(
                onCenterTap = { callbacks.onToggleControls() },
                gate = tapGate,
            ),
    ) {
        LazyColumn(
            state = s.plainListState,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = s.chapterFade.value },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = settings.readerSettings.pageMargin.dp,
                vertical = settings.readerSettings.pageMargin.dp,
            ),
        ) {
            itemsIndexed(
                items = s.readingUnits,
                key = { _, unit -> unit.unitIndex },
            ) { _, unit ->
                val unitState = unitContentLoader.stateFor(scrollSource, unit.unitIndex)
                LaunchedEffect(scrollSource, unit) {
                    unitContentLoader.load(scrollSource, unit.unitIndex) {
                        scrollSource.loadScrollUnitContent(unit)
                    }
                }
                DisposableEffect(scrollSource, unit.unitIndex) {
                    onDispose { unitContentLoader.release(scrollSource, unit.unitIndex) }
                }
                when (val loaded = unitState.value) {
                    is ScrollUnitContentState.Loaded -> ReaderUnitTextItem(
                        content = loaded.content,
                        unit = unit,
                        state = state,
                        callbacks = callbacks,
                        fontFamily = readerFontFamily,
                        searchHitRangeAbs = s.searchHitRangeAbs,
                        tapGate = tapGate,
                    )

                    ScrollUnitContentState.Loading -> ShimmerBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(loadingPlaceholderHeight)
                            .semantics { contentDescription = "正在加载正文" },
                        reducedMotion = reducedMotion,
                    )

                    is ScrollUnitContentState.Failed -> UnitTextFailedItem(
                        cause = loaded.cause,
                        paperFg = settings.paperFg,
                        onRetry = {
                            scope.launch {
                                unitContentLoader.retry(scrollSource, unit.unitIndex) {
                                    scrollSource.loadScrollUnitContent(unit)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 读取单元失败项：原因摘要 + 显式「点击重试」，带可访问语义。 */
@Composable
private fun UnitTextFailedItem(
    cause: Throwable,
    paperFg: Color,
    onRetry: () -> Unit,
) {
    val summary = cause.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetry)
            .semantics { contentDescription = "内容加载失败：$summary，点击重试" }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "内容加载失败：$summary",
            style = MaterialTheme.typography.bodySmall,
            color = paperFg,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "点击重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 滚动 TXT 单条 ReadingUnit 的纯渲染（TTS 高亮 + 选区 + BasicTextField）。
 * 文本和坐标映射由 [ScrollUnitContentLoader] 在 IO 线程加载。display 仅用于
 * 渲染；持久化定位始终映射回 source 全局坐标。
 */
@Composable
private fun ReaderUnitTextItem(
    content: ScrollUnitProjection,
    unit: ReadingUnit,
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    fontFamily: FontFamily,
    searchHitRangeAbs: Pair<Int, Int>?,
    tapGate: ReaderTapToggleGate,
) {
    val settings = state.settings
    val selectionState = state.selection
    val paging = state.paging
    val s = state.source
    val effectiveText = content.displayText
    val effectiveLength = effectiveText.length
    val ttsRange = if (s.showTts && s.tts.status != "idle" && s.isTxt) {
        if (s.txtStreamingDocument != null) {
            // 流式 TXT：contentText 是窗口，sentenceRange 是窗口内偏移
            // 加 visiblePlainOffset 转全书偏移后再与 unit.charStart 做差
            val base = paging.visiblePlainOffset
            (base + s.tts.currentSentenceRange.first) to (base + s.tts.currentSentenceRange.second)
        } else {
            s.tts.currentSentenceRange
        }
    } else {
        0 to 0
    }
    val localStart = content.globalSourceToLocalDisplay(ttsRange.first)
    val localEnd = content.globalSourceToLocalDisplay(ttsRange.second)
    val searchLocal = searchHitRangeAbs?.let { abs ->
        intersectTextRange(unit.charStart, unit.charCount, abs.first until abs.second)
            ?.let { content.globalSourceRangeToLocalDisplay(it.first, it.last + 1) }
    }
    val persistentHighlightSpans = remember(s.highlights, content, settings.paper) {
        s.highlights.mapNotNull { highlight ->
            val start = parseLocatorOffset(highlight.locator_json) ?: return@mapNotNull null
            val length = highlightSourceLength(highlight.payload, highlight.text.length)
            if (length <= 0) return@mapNotNull null
            val end = (start.toLong() + length.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val intersectionStart = maxOf(start, content.sourceStartAbs)
            val intersectionEnd = minOf(end, content.sourceEndAbs)
            if (intersectionEnd <= intersectionStart) return@mapNotNull null
            val displayRange = content.globalSourceRangeToLocalDisplay(intersectionStart, intersectionEnd)
            if (displayRange.second <= displayRange.first) return@mapNotNull null
            Triple(
                displayRange.first,
                displayRange.second,
                settings.paper.highlight(highlight.color ?: "yellow"),
            )
        }
    }
    val annotated = remember(
        effectiveText,
        localStart,
        localEnd,
        settings.sentenceHighlightBg,
        settings.searchHighlightBg,
        searchLocal,
        persistentHighlightSpans,
    ) {
        if (localEnd > localStart || searchLocal != null || persistentHighlightSpans.isNotEmpty()) {
            AnnotatedString.Builder(effectiveText).apply {
                persistentHighlightSpans.forEach { (start, end, color) ->
                    addStyle(SpanStyle(background = color), start, end)
                }
                if (localEnd > localStart) {
                    addStyle(
                        SpanStyle(background = settings.sentenceHighlightBg),
                        localStart,
                        localEnd,
                    )
                }
                if (searchLocal != null) {
                    addStyle(
                        SpanStyle(background = settings.searchHighlightBg),
                        searchLocal.first,
                        searchLocal.second,
                    )
                }
            }.toAnnotatedString()
        } else {
            AnnotatedString(effectiveText)
        }
    }
    var selection by remember(content.sourceStartAbs) { mutableStateOf(TextRange.Zero) }
    BasicTextField(
        value = TextFieldValue(annotatedString = annotated, selection = selection),
        onValueChange = { value ->
            selection = value.selection
            if (value.selection != TextRange.Zero && value.selection.length > 0) {
                val selectionStart = minOf(value.selection.start, value.selection.end)
                    .coerceIn(0, effectiveLength)
                val selectionEnd = maxOf(value.selection.start, value.selection.end)
                    .coerceIn(0, effectiveLength)
                callbacks.onSelect(
                    effectiveText.substring(selectionStart, selectionEnd),
                    -1,
                    content.localDisplayToGlobalSource(selectionStart),
                    content.localDisplayToGlobalSource(selectionEnd),
                )
            } else if (selectionState.selectedRangeStart in content.sourceStartAbs until content.sourceEndAbs) {
                callbacks.onSelect("", -1, -1, -1)
            }
        },
        readOnly = true,
        textStyle = TextStyle(
            fontSize = settings.readerSettings.fontSize.sp,
            lineHeight = (settings.readerSettings.fontSize * settings.readerSettings.lineHeight).sp,
            color = settings.paperFg,
            textAlign = TextAlign.Justify,
            fontWeight = if (settings.readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = fontFamily,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .readerTextFieldCenterTapToToggle(
                onCenterTap = { callbacks.onToggleControls() },
                gate = tapGate,
            ),
    )
}
