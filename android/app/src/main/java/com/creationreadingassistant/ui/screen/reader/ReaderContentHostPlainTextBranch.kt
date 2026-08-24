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
import com.creationreadingassistant.feature.reader.doc.UnitTextLoader
import com.creationreadingassistant.feature.reader.doc.UnitTextState
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
    // 流式 TXT：组合路径只读 UnitTextLoader 的可观察状态，阻塞 readUnit
    // 在 loader 的 IO dispatcher 上执行；文档变化时 switchDocument 作废旧请求。
    val unitTextLoader = remember { UnitTextLoader() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(txtDoc) {
        if (txtDoc != null) unitTextLoader.switchDocument(txtDoc)
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
                if (txtDoc != null) {
                    // 异步加载：Loading/Loaded/Failed 状态可观察；快速切章/换书时
                    // 旧请求被 LaunchedEffect 取消 + switchDocument 代次守卫丢弃。
                    val unitState = unitTextLoader.stateFor(txtDoc, unit.unitIndex)
                    LaunchedEffect(txtDoc, unit) {
                        unitTextLoader.load(txtDoc, unit.unitIndex) { txtDoc.readUnit(unit) }
                    }
                    DisposableEffect(txtDoc, unit.unitIndex) {
                        onDispose { unitTextLoader.release(txtDoc, unit.unitIndex) }
                    }
                    when (val st = unitState.value) {
                        is UnitTextState.Loaded -> ReaderUnitTextItem(
                            unitText = st.text,
                            unit = unit,
                            state = state,
                            callbacks = callbacks,
                            fontFamily = readerFontFamily,
                            searchHitRangeAbs = s.searchHitRangeAbs,
                            tapGate = tapGate,
                        )

                        UnitTextState.Loading -> {
                            // 最小高度占位 + 加载语义：避免内容完成时零高→全高的跳动，
                            // 高度按 3 行正文估算（与正文行高同源）。
                            ShimmerBlock(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // LazyColumn 已在 contentPadding 统一扣除页边距；
                                    // 这里不能再扣一次，否则加载中占位比真实正文窄一层，
                                    // 单元完成后会出现跨设备可见的宽度跳变。
                                    .padding(
                                        vertical = 6.dp,
                                    )
                                    .height(loadingPlaceholderHeight)
                                    .semantics { contentDescription = "正在加载正文" },
                                reducedMotion = reducedMotion,
                            )
                        }

                        is UnitTextState.Failed -> UnitTextFailedItem(
                            cause = st.cause,
                            paperFg = settings.paperFg,
                            // 仅显式点击触发重试；不靠滚走/重组碰运气
                            onRetry = {
                                scope.launch {
                                    unitTextLoader.retry(txtDoc, unit.unitIndex) {
                                        txtDoc.readUnit(unit)
                                    }
                                }
                            },
                        )
                    }
                } else {
                    // 小文件 TXT：全文已在内存，组合阶段直接切片（无 I/O）
                    val unitText = remember(unit.unitIndex, s.plainContent) {
                        s.plainContent.substring(
                            unit.charStart,
                            (unit.charStart + unit.charCount).coerceAtMost(s.plainContent.length),
                        )
                    }
                    ReaderUnitTextItem(
                        unitText = unitText,
                        unit = unit,
                        state = state,
                        callbacks = callbacks,
                        fontFamily = readerFontFamily,
                        searchHitRangeAbs = s.searchHitRangeAbs,
                        tapGate = tapGate,
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
 * 文本由调用方提供（流式模式来自 [UnitTextLoader]，小文件模式来自内存切片）。
 */
@Composable
private fun ReaderUnitTextItem(
    unitText: String,
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
    val localStart = (ttsRange.first - unit.charStart).coerceIn(0, unitText.length)
    val localEnd = (ttsRange.second - unit.charStart).coerceIn(0, unitText.length)
    val searchLocal = searchHitRangeAbs?.let {
        intersectTextRange(unit.charStart, unitText.length, it.first until it.second)
    }
    val annotated = remember(unitText, localStart, localEnd, settings.sentenceHighlightBg, settings.searchHighlightBg, searchLocal) {
        if (localEnd > localStart || searchLocal != null) {
            AnnotatedString.Builder(unitText).apply {
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
                        searchLocal.last + 1,
                    )
                }
            }.toAnnotatedString()
        } else {
            AnnotatedString(unitText)
        }
    }
    var selection by remember(unit.charStart) { mutableStateOf(TextRange.Zero) }
    BasicTextField(
        value = TextFieldValue(annotatedString = annotated, selection = selection),
        onValueChange = { value ->
            selection = value.selection
            if (value.selection != TextRange.Zero && value.selection.length > 0) {
                callbacks.onSelect(
                    unitText.substring(value.selection.start, value.selection.end),
                    -1,
                    unit.charStart + value.selection.start,
                    unit.charStart + value.selection.end,
                )
            } else if (selectionState.selectedRangeStart in unit.charStart until (unit.charStart + unitText.length)) {
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
