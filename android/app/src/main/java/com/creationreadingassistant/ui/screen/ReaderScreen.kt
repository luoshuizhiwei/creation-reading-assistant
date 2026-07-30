package com.creationreadingassistant.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.ReadingUnitBuilder
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.paperPalette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.creationreadingassistant.data.settings.SettingsStore
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.creationreadingassistant.ui.components.rememberViewportImageRequest
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import com.creationreadingassistant.ui.viewmodel.ReaderUiState
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanResult
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.EpubParser
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.ui.screen.reader.RenderMarkdownChapter
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.pager.EpubChapterSource
import com.creationreadingassistant.feature.reader.pager.AutoPagingTiming
import com.creationreadingassistant.feature.reader.pager.AutoScrollAccumulator
import com.creationreadingassistant.feature.reader.pager.MarkdownChapterSource
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.PagedReaderHost
import com.creationreadingassistant.feature.reader.pager.TxtChapterSource
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.locator.AnchorConfidence
import com.creationreadingassistant.feature.reader.locator.AnchorResolver
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.eyecare.EyeCareSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import java.io.File
import java.util.Locale
import java.util.UUID
import com.creationreadingassistant.ui.screen.reader.BookIndex
import com.creationreadingassistant.ui.screen.reader.ReaderScreenInputs
import com.creationreadingassistant.ui.screen.reader.ReaderScreenCallbacks
import com.creationreadingassistant.ui.screen.reader.BookSearchResult
import com.creationreadingassistant.ui.screen.reader.buildBookIndex
import com.creationreadingassistant.ui.screen.reader.buildInspirationPayload
import com.creationreadingassistant.ui.screen.reader.blockIndexForChapterOffset
import com.creationreadingassistant.ui.screen.reader.chunkIndexForOffset
import com.creationreadingassistant.ui.screen.reader.chunkPlainText
import com.creationreadingassistant.ui.screen.reader.PlainTextChunk
import com.creationreadingassistant.ui.screen.reader.computeBlockGlobalOffsets
import com.creationreadingassistant.ui.screen.reader.computeBookSearch
import com.creationreadingassistant.ui.screen.reader.computeEpubSearch
import com.creationreadingassistant.ui.screen.reader.computeStreamingTxtSearch
import com.creationreadingassistant.ui.screen.reader.formatDuration
import com.creationreadingassistant.ui.screen.reader.nowIso
import com.creationreadingassistant.ui.screen.reader.parseLocatorOffset
import com.creationreadingassistant.ui.screen.reader.progressToChapterIndex
import com.creationreadingassistant.ui.screen.reader.unitIndexForOffset
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.screen.reader.tts.buildSentenceHighlighted
import com.creationreadingassistant.ui.screen.reader.tts.rememberTts
import com.creationreadingassistant.ui.screen.reader.tts.TtsBar
import com.creationreadingassistant.ui.screen.reader.tts.TtsSettingsSyncEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsResumeEffect
import com.creationreadingassistant.ui.screen.reader.tts.TtsReaderSyncEffect
import com.creationreadingassistant.ui.screen.reader.SelectionToolbar
import com.creationreadingassistant.ui.screen.reader.sheets.AiAssistSheet
import com.creationreadingassistant.ui.screen.reader.sheets.AiExplainSheet
import com.creationreadingassistant.ui.screen.reader.sheets.BookInfoSheet
import com.creationreadingassistant.ui.screen.reader.sheets.InspirationSheet
import com.creationreadingassistant.ui.screen.reader.sheets.NotesSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ProgressSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SearchSheet
import com.creationreadingassistant.ui.screen.reader.sheets.SettingsSheet
import com.creationreadingassistant.ui.screen.reader.sheets.ThemeSheet
import com.creationreadingassistant.ui.screen.reader.sheets.TocSheet
import com.creationreadingassistant.ui.screen.reader.ReaderPlatformEffects

/**
 * 阅读器全屏页（1:1 复刻 mobile/ 的 MobileReaderView 布局）。
 *
 * 分区：顶部栏（可收起）/ 正文区（EPUB 按章节、TXT 降级滚动文本）/ 底部 TTS 播放条 /
 * 底部弹层（目录、笔记与标注、AI 助手、AI 解读、灵感速记、设置、进度）。
 *
 * 复用：EpubParser（经 EpubRepository.openEpub）、BookRepository、BookContentDao、
 * HighlightDao / NoteDao / InspirationDao / ReadingProgressDao、TextToSpeech。
 * 不重写解析与朗读逻辑；helper 均在本文件内。
 */



// 高亮 / 纸色统一走 ReaderPaperPalette（paperPalette(readerSettings.background, appDark)），
// 不再由本文件内联硬编码；5 色批注与夜读描边严格来自设计实施稿 §4 / §4.5（已冻结）。

// TTS 控制器、媒体会话、句子切分、高亮构建、rememberTts → reader/tts/ReaderTtsController.kt / ReaderTtsBar.kt

// nowIso, progressToChapterIndex, formatDuration, buildInspirationPayload → reader/ReaderHelpers.kt

/**
 * 翻页模式（对照 web readerMode=paged）：按章整屏展示，左右边缘点击翻章，
 * 支持 fade / slide / curl 翻页动效。中间区域保留滚动与整段选中（避免与选择冲突）。
 * 说明：原生自研引擎按章解析，未做章内逐页分页（epub.js 能力），故「页」= 一章。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedEpubView(
    blocks: List<DocBlock>,
    fontSize: Float,
    lineHeight: Float,
    fontWeightBold: Boolean,
    pageMargin: Float,
    paperFg: Color,
    tapZoneMode: String,
    pageTurnEffect: String,
    chapterIndex: Int,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectBlock: (String, Int) -> Unit,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    bringRequester: BringIntoViewRequester,
) {
    val reducedMotion = rememberReducedMotion()
    val contentAlpha = remember { Animatable(1f) }
    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(chapterIndex) {
        if (reducedMotion || firstRun) {
            firstRun = false
            contentAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        // 轻量翻页淡入：单 Composition、只动 alpha，绝不复制整章组件树（守住 OOM 内存纪律）。
        contentAlpha.snapTo(0.35f)
        contentAlpha.animateTo(1f, tween(durationMillis = 240))
    }
    val haptic = rememberHaptic(reducedMotion)
    val onPrevHaptic: () -> Unit = { haptic(HapticFeedbackType.TextHandleMove); onPrev() }
    val onNextHaptic: () -> Unit = { haptic(HapticFeedbackType.TextHandleMove); onNext() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pageMargin.dp, vertical = pageMargin.dp)
                .graphicsLayer { alpha = contentAlpha.value },
        ) {
            val content: @Composable () -> Unit = {
                PagedChapterContent(
                    blocks = blocks,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeightBold = fontWeightBold,
                    paperFg = paperFg,
                    onSelectBlock = onSelectBlock,
                    blockGlobalOffsets = blockGlobalOffsets,
                    chapterBase = chapterBase,
                    ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
                    focusBlockIndex = focusBlockIndex,
                    sentenceHighlightBg = sentenceHighlightBg,
                    bringRequester = bringRequester,
                )
            }
            // 不在这里同时保留新旧整章 Composition。旧 AnimatedContent/Crossfade
            // 会在大章节翻页时让两章文本布局同时驻留，显著放大峰值内存。
            // 翻页动效后续应基于轻量截图/页面缓存实现，而不是复制整章组件树。
            @Suppress("UNUSED_VARIABLE")
            val configuredEffect = pageTurnEffect
            @Suppress("UNUSED_VARIABLE")
            val currentChapter = chapterIndex
            content()
        }
        // 点击翻页分区：three-zone=左右边缘；five-zone=再加上下边缘（对照 web tapZoneMode）
        if (tapZoneMode == "five-zone") {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(0.12f).fillMaxWidth().clickable(enabled = canPrev) { onPrevHaptic() },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (canPrev) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                }
                Row(Modifier.weight(0.76f).fillMaxWidth()) {
                    Box(
                        Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canPrev) { onPrevHaptic() },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (canPrev) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                    }
                    Spacer(Modifier.weight(0.68f))
                    Box(
                        Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canNext) { onNextHaptic() },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (canNext) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                    }
                }
                Box(
                    Modifier.weight(0.12f).fillMaxWidth().clickable(enabled = canNext) { onNextHaptic() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (canNext) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canPrev) { onPrevHaptic() },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (canPrev) Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章", tint = paperFg.copy(alpha = 0.3f))
                }
                Spacer(Modifier.weight(0.68f))
                Box(
                    Modifier.weight(0.16f).fillMaxSize().clickable(enabled = canNext) { onNextHaptic() },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (canNext) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章", tint = paperFg.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedChapterContent(
    blocks: List<DocBlock>,
    fontSize: Float,
    lineHeight: Float,
    fontWeightBold: Boolean,
    paperFg: Color,
    onSelectBlock: (String, Int) -> Unit,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    ttsSentenceRangeInChapter: Pair<Int, Int>?,
    focusBlockIndex: Int?,
    sentenceHighlightBg: Color,
    bringRequester: BringIntoViewRequester,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = blocks,
            key = { index, block ->
                when (block) {
                    is DocBlock.Text -> "text-$index"
                    is DocBlock.Image -> "image-$index-${block.path}"
                    is DocBlock.Markdown -> "markdown-$index"
                }
            },
        ) { idx, block ->
            when (block) {
                is DocBlock.Text -> {
                    val gOff = blockGlobalOffsets.getOrElse(idx) { -1 }
                    val ann = buildSentenceHighlighted(block.text, gOff, chapterBase, ttsSentenceRangeInChapter, sentenceHighlightBg)
                    Text(
                        text = ann,
                        style = TextStyle(
                            textAlign = TextAlign.Justify,
                            lineHeight = (fontSize * lineHeight).sp,
                            textIndent = if (block.isHeading) TextIndent.None else TextIndent(firstLine = (fontSize * 2).sp),
                        ),
                        fontSize = fontSize.sp,
                        fontWeight = if (block.isHeading || fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                        color = paperFg,
                        modifier = Modifier.fillMaxWidth().clickable { onSelectBlock(block.text, gOff) },
                    )
                }
                is DocBlock.Image -> {
                    val imageFile = remember(block.path) { java.io.File(block.path) }
                    val imageRequest = rememberViewportImageRequest(
                        data = imageFile,
                        cacheKey = "reader:${block.path}:${imageFile.lastModified()}",
                    )
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    )
                }
                is DocBlock.Markdown -> {
                    RenderMarkdownChapter(
                        chapter = block.chapter,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        paperFg = paperFg,
                        blockGlobalOffset = blockGlobalOffsets.getOrElse(idx) { -1 },
                        chapterBase = chapterBase,
                        ttsSentenceRange = ttsSentenceRangeInChapter,
                        sentenceHighlightBg = sentenceHighlightBg,
                        onSelectBlock = { text, gOff -> onSelectBlock(text, gOff) },
                    )
                }
            }
        }
    }
}

// BookIndex, PlainTextChunk, chunkPlainText, chunkIndexForOffset, unitIndexForOffset, buildBookIndex → reader/ReaderHelpers.kt

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    inputs: ReaderScreenInputs,
    callbacks: ReaderScreenCallbacks,
) {
    // ── 解构参数 ──
    val bookId = inputs.bookId
    val highlightId = inputs.highlightId
    val documentUiState = inputs.documentUiState
    val screenState = inputs.screenState
    val highlights = inputs.highlights
    val notes = inputs.notes
    val inspirations = inputs.inspirations
    val categories = inputs.categories
    val tags = inputs.tags
    val sessions = inputs.sessions
    val txtTocRuleIdFromVm = inputs.txtTocRuleIdFromVm
    val chapterLoadResult = inputs.chapterLoadResult
    val txtRuleScanResult = inputs.txtRuleScanResult
    val onLoadChapterBlocks = callbacks.onLoadChapterBlocks
    val onExtractChapterText = callbacks.onExtractChapterText
    val onAction = callbacks.onAction
    val onDocumentAction = callbacks.onDocumentAction
    val onBack = callbacks.onBack
    val settingsStore = callbacks.settingsStore
    val aiClient = callbacks.aiClient
    val pageIndexStore = callbacks.pageIndexStore
    val anchorCacheStore = callbacks.anchorCacheStore
    val context = LocalContext.current
    val settingsVm: SettingsViewModel = hiltViewModel()
    val readerSettings by settingsVm.reader.collectAsStateWithLifecycle()
    // 外观模式（system/light/dark）用于「跟随外观」纸张映射：浅色外壳→白纸，深色外壳→夜读。
    val appearance by settingsVm.appearance.collectAsStateWithLifecycle()
    val appDark = when (appearance.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // 阅读器纸张调色板与外壳浅/深解耦；正文与 chrome 共同跟随 paper 的 light/dark。
    val paper = paperPalette(readerSettings.background, appDark)
    val paperBg = paper.bg
    val paperFg = paper.fg
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val clipboard = LocalClipboardManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val tts = rememberTts()
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── 文档状态只从 ReaderViewModel 输入；页面不再打开 URI 或拥有文件资源。────────
    val loadedBook = documentUiState.loadedBook
    val epubContent = loadedBook?.content as? ReaderLoadedContent.Epub
    val textContent = loadedBook?.content as? ReaderLoadedContent.Text
    val markdownContent = loadedBook?.content as? ReaderLoadedContent.Markdown
    val epubBook = epubContent?.book
    val epubDocument = epubContent?.document
    val markdownDocument = markdownContent?.document
    val bookIndex = remember(epubBook) { epubBook?.let(::buildBookIndex) }
    val plainContent = textContent?.fullText.orEmpty()
    val bookTitle = loadedBook?.title ?: "未命名书籍"
    val bookAuthor = loadedBook?.author
    val bookOriginalFile = loadedBook?.originalFileName
    val bookSize = loadedBook?.sizeBytes ?: 0
    val savedPlainPercent = if (textContent != null) loadedBook.initialProgressPercent else 0f
    val savedPlainOffset = textContent?.initialAbsoluteOffset ?: markdownContent?.initialAbsoluteOffset ?: 0
    val savedEpubOffsetInChapter = epubContent?.initialOffsetInChapter ?: 0
    val savedTotalReadingMs = loadedBook?.savedReadingTimeMs ?: 0L
    val sessionStartProgress = loadedBook?.initialProgressPercent ?: 0f
    val isLoading = documentUiState.isLoading
    var runtimeError by remember(bookId) { mutableStateOf<String?>(null) }
    val error = runtimeError ?: documentUiState.errorMessage
    var chapterIndex by remember(bookId) { mutableIntStateOf(0) }
    var pendingInitialPosition by remember(bookId) { mutableStateOf(true) }

    // 预加载状态（由 ViewModel 章节加载代次管理，主线程只读，杜绝主线程 Zip I/O 导致的 ANR / OOM）
    var chapterBlocks by remember { mutableStateOf<List<DocBlock>>(emptyList()) }
    var isChapterLoading by remember { mutableStateOf(false) }

    // 流式 TXT 大文件状态（统一加载器按实际字节数分流，plainContent 为空串）
    var txtStreamingDocument by remember { mutableStateOf<PlainTextDocument?>(null) }
    var txtStreamingFileIndex by remember { mutableStateOf<TxtFileIndex?>(null) }

    LaunchedEffect(loadedBook) {
        runtimeError = null
        isChapterLoading = false
        pendingInitialPosition = loadedBook != null
        chapterIndex = epubContent?.initialChapterIndex ?: 0
        chapterBlocks = epubContent?.initialChapterBlocks.orEmpty()
        txtStreamingDocument = textContent?.streamingDocument
        txtStreamingFileIndex = textContent?.fileIndex
        // Markdown 滚动模式首章直接同步装载；分页模式由 pagedSource 按需读取
        if (markdownDocument != null) {
            chapterBlocks = markdownDocument.blocks(0)
        }
    }

    // R6：观察 ViewModel 章节加载结果
    LaunchedEffect(chapterLoadResult) {
        when (val r = chapterLoadResult) {
            is ChapterLoadResult.Loading -> {
                isChapterLoading = true
                chapterBlocks = emptyList()
            }
            is ChapterLoadResult.Loaded -> {
                isChapterLoading = false
                chapterBlocks = r.blocks
            }
            is ChapterLoadResult.Error -> {
                isChapterLoading = false
                runtimeError = r.message
            }
            null -> Unit
        }
    }

    // screenState 来自 ViewModel（不可变 data class）
    var controlsVisible by remember { mutableStateOf(screenState.controlsVisible) }
    LaunchedEffect(screenState.controlsVisible) { controlsVisible = screenState.controlsVisible }
    // 自动翻页是否运行只属于当前阅读会话；重进书籍不会擅自继续。
    var autoPagingActive by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var readerResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    var sheetOpenGuard by remember { mutableStateOf(screenState.sheetOpenGuard) }
    LaunchedEffect(screenState.sheetOpenGuard) { sheetOpenGuard = screenState.sheetOpenGuard }

    var selectedText by remember { mutableStateOf(screenState.selectedText) }
    LaunchedEffect(screenState.selectedText) { selectedText = screenState.selectedText }
    // T1：记录选区起点在本书全局文本中的偏移，用于写入 locator_json（TXT=plainContent 偏移，EPUB=block 全局偏移）
    var selectedRangeStart by remember { mutableStateOf(screenState.selectedRangeStart) }
    LaunchedEffect(screenState.selectedRangeStart) { selectedRangeStart = screenState.selectedRangeStart }
    var selectedGlobalOffset by remember { mutableStateOf(screenState.selectedGlobalOffset) }
    LaunchedEffect(screenState.selectedGlobalOffset) { selectedGlobalOffset = screenState.selectedGlobalOffset }
    var sheet by remember { mutableStateOf(screenState.sheet) }
    LaunchedEffect(screenState.sheet) { sheet = screenState.sheet }
    // 弹层打开时暂停「菜单自动隐藏」倒计时（否则调设置调到一半菜单没了）
    LaunchedEffect(sheet) {
        sheetOpenGuard = sheet != null
        onAction(
            sheet?.let(ReaderAction::OpenSheet)
                ?: ReaderAction.CloseSheet,
        )
    }
    var showTts by remember { mutableStateOf(screenState.showTts) }
    LaunchedEffect(screenState.showTts) { showTts = screenState.showTts }
    var searchQuery by remember { mutableStateOf(screenState.searchQuery) }
    LaunchedEffect(screenState.searchQuery) { searchQuery = screenState.searchQuery }
    // R5：最近浏览章节（本会话记录，置顶于目录）；R8：顶栏「更多」菜单
    var recentChapters = remember { mutableStateListOf<Int>() }
    var showReaderOverflow by remember { mutableStateOf(screenState.showReaderOverflow) }
    LaunchedEffect(screenState.showReaderOverflow) { showReaderOverflow = screenState.showReaderOverflow }
    // R3：跨会话 TTS 续读句偏移
    var ttsResumeOffset by remember { mutableStateOf(0) }
    var ttsResumeChapter by remember { mutableIntStateOf(-1) }

    // 阅读设置（来自持久化 SettingsStore，见 readerSettings）

    // 笔记对话框
    var noteOpen by remember { mutableStateOf(screenState.noteOpen) }
    LaunchedEffect(screenState.noteOpen) { noteOpen = screenState.noteOpen }
    var noteBody by remember { mutableStateOf(screenState.noteBody) }
    LaunchedEffect(screenState.noteBody) { noteBody = screenState.noteBody }
    // 高亮颜色选择
    var showColorRow by remember { mutableStateOf(screenState.showColorRow) }
    LaunchedEffect(screenState.showColorRow) { showColorRow = screenState.showColorRow }

    // 返回键按“临时层级优先”处理：先关弹层/菜单/选区，再离开阅读器。
    // 这与正文导航解耦，避免误触返回直接丢失当前阅读上下文。
    BackHandler(
        enabled = sheet != null ||
            noteOpen ||
            showReaderOverflow ||
            selectedText.isNotBlank() ||
            controlsVisible,
    ) {
        when {
            sheet != null -> sheet = null
            noteOpen -> noteOpen = false
            showReaderOverflow -> showReaderOverflow = false
            selectedText.isNotBlank() -> {
                selectedText = ""
                selectedRangeStart = -1
                selectedGlobalOffset = -1
                showColorRow = false
            }
            controlsVisible -> controlsVisible = false
        }
    }

    // 阅读提醒 / 本次阅读计时（对照 web useReaderReminders + useReaderSession）
    var activeReadingMs by remember { mutableStateOf(0L) }
    // 用可变 State 持有最新设置，避免提醒计时器因设置变化反复重建 / 捕获旧值
    val settingsRef = remember { mutableStateOf(readerSettings) }
    LaunchedEffect(readerSettings) { settingsRef.value = readerSettings }

    // TTS 高级项：首次将持久化的音调/音量/音色/定时停止载入控制器 → TtsSettingsSyncEffect
    TtsSettingsSyncEffect(tts = tts, readerSettings = readerSettings)

    val bid = bookId ?: ""
    var txtTocRuleId by remember(bid) { mutableStateOf(txtTocRuleIdFromVm) }
    LaunchedEffect(bid, txtTocRuleIdFromVm) {
        txtTocRuleId = txtTocRuleIdFromVm
    }

    // R3：跨会话 TTS 续读 → TtsResumeEffect
    TtsResumeEffect(
        tts = tts,
        bookId = bid,
        settingsStore = settingsStore,
        isEpub = epubBook != null,
        chapterIndex = chapterIndex,
        onResumeOffsetChanged = { ttsResumeOffset = it },
        onResumeChapterChanged = { ttsResumeChapter = it },
    )

    // SE4：从搜索结果跳转时携带的 highlightId（高亮或笔记），消费后置空避免重复触发
    var pendingHighlightId by remember { mutableStateOf(highlightId) }

    // 书内搜索：EPUB 不再常驻全本文本（会 OOM），改为搜索时按需逐章流式抽取（见 computeEpubSearch）；
    // 这里只暴露各章偏移与标题，供跳章 / 命中映射使用。
    val isTxt = epubBook == null && markdownDocument == null
    val chapterStartOffsets = bookIndex?.chapterStartOffsets
        ?: markdownDocument?.chapters?.map { it.startOffset }
        ?: txtStreamingDocument?.chapters?.map { it.startOffset }
        ?: emptyList()
    val chapterTitles = bookIndex?.chapterTitles
        ?: markdownDocument?.chapters?.map { it.title }
        ?: txtStreamingDocument?.chapters?.map { it.title }
        ?: emptyList()
    // 读取单元：惰性加载的元数据列表，不持有文本
    val readingUnits: List<ReadingUnit> = remember(plainContent, txtStreamingDocument, txtStreamingFileIndex) {
        when {
            txtStreamingDocument != null -> {
                ReadingUnitBuilder.buildUnits(txtStreamingDocument!!.chapters, txtStreamingFileIndex)
            }
            plainContent.isNotEmpty() -> {
                // 小文件：复用现有 chunkPlainText 的结果
                chunkPlainText(plainContent).mapIndexed { i, chunk ->
                    ReadingUnit(
                        unitIndex = i,
                        chapterIndex = 0,
                        title = "全文",
                        charStart = chunk.startOffset,
                        charCount = chunk.text.length,
                    )
                }
            }
            else -> emptyList()
        }
    }
    // 将 readingUnits 同步到 PlainTextDocument，供 unitIndexForOffset 等方法使用
    LaunchedEffect(txtStreamingDocument, readingUnits) {
        txtStreamingDocument?.readingUnits = readingUnits
    }
    // LRU 缓存：流式模式下缓存已解码的 ReadingUnit 文本（最多 5 个）
    val unitCache = remember(txtStreamingDocument) {
        com.creationreadingassistant.feature.reader.doc.ReadingUnitCache()
    }
    // 暂时保留 plainChunks 用于进度/跳转兼容（Phase C 将完全替换）
    val plainChunks: List<PlainTextChunk> = remember(plainContent, readingUnits) {
        when {
            plainContent.isNotEmpty() -> chunkPlainText(plainContent)
            readingUnits.isNotEmpty() -> {
                // 流式模式：从 readingUnits 构建轻量 chunk 列表（只含偏移和长度，不含文本）
                readingUnits.map { unit ->
                    PlainTextChunk(unit.charStart, "")
                }
            }
            else -> emptyList()
        }
    }

    // ── 自研分页引擎（pagerEngineMode=on 时 TXT/EPUB 都走真正的章内逐页翻页）──
    val pagerHealth = remember { PagerHealthStore(context.applicationContext) }
    val configuredPagerMode = if (epubBook != null) {
        readerSettings.epubPagerEngineMode
    } else {
        readerSettings.pagerEngineMode
    }
    val pagerEngineOn = readerSettings.readerMode == "paged" && when (configuredPagerMode) {
        "on" -> true
        "auto" -> !pagerHealth.isAutoDisabled
        else -> false
    }
    DisposableEffect(pagerEngineOn) {
        val guard = if (pagerEngineOn) pagerHealth.installCrashGuard() else null
        onDispose { guard?.close() }
    }
    LaunchedEffect(pagerEngineOn, isLoading, error) {
        if (pagerEngineOn && !isLoading && error == null) {
            delay(60_000)
            pagerHealth.clearAfterStableRead()
        }
    }
    // 外部跳转请求（进度条 / 目录 / 高亮定位），宿主消费后置回 null
    val pagedJumpRequest = remember { mutableStateOf<Int?>(null) }
    val pagedHardwareTurnRequest = remember { mutableStateOf<Int?>(null) }
    // 分页引擎上报的当前位置（全书字符偏移 + 百分比），-1 表示尚未上报
    var pagedAbsOffset by remember { mutableIntStateOf(-1) }
    var pagedPercent by remember { mutableFloatStateOf(0f) }

    // TXT 章节识别：此前 TXT 完全没有章节概念，目录永远是「暂未识别到目录」。
    // 只在正文变化时算一次，识别不出章节时 TxtChapterDetector 会返回单章「全文」。
    val txtChapters = remember(plainContent, txtTocRuleId, txtStreamingDocument) {
        val streamDoc = txtStreamingDocument
        if (epubBook == null && streamDoc != null) {
            streamDoc.chapters
        } else if (epubBook == null && plainContent.isNotBlank()) {
            PlainTextDocument(plainContent, txtTocRuleId).chapters
        } else {
            emptyList()
        }
    }
    val txtRulePreviews by androidx.compose.runtime.produceState(
        initialValue = emptyMap<String, List<TxtChapterDetector.Chapter>>(),
        plainContent,
        epubBook,
    ) {
        value = if (plainContent.isBlank() || epubBook != null) {
            emptyMap()
        } else {
            withContext(Dispatchers.Default) {
                TxtChapterDetector.rules.associate { rule ->
                    rule.id to TxtChapterDetector.detect(plainContent, rule.id)
                }
            }
        }
    }
    // EPUB 文档缓存与流式 TXT 临时文件均由 ReaderViewModel 独占并释放。
    val pagedSource: PagedChapterSource? = remember(
        epubBook,
        epubDocument,
        bookIndex,
        plainContent,
        txtChapters,
        txtStreamingDocument,
        markdownDocument,
    ) {
        val index = bookIndex
        val document = epubDocument
        when {
            epubBook != null && index != null && document != null ->
                EpubChapterSource(
                    titles = index.chapterTitles,
                    chapterStartOffsets = index.chapterStartOffsets,
                    totalChars = index.totalChars,
                    loadBlocks = document::blocks,
                )

            markdownDocument != null ->
                MarkdownChapterSource(markdownDocument)

            txtStreamingDocument != null && txtChapters.isNotEmpty() ->
                TxtChapterSource(txtStreamingDocument!!)

            plainContent.isNotBlank() && txtChapters.isNotEmpty() ->
                TxtChapterSource(plainContent, txtChapters)

            else -> null
        }
    }

    // 进度计算
    val epubPercent = if (epubBook != null) {
        val size = epubBook!!.chapters.size
        if (size <= 1) if (chapterIndex == 0) 100f else 0f else (chapterIndex.toFloat() / (size - 1)) * 100f
    } else 0f

    val plainListState = rememberLazyListState()

    // 当前可见字符偏移（必须在 contentText 之前计算）
    val firstPlainItem = plainListState.layoutInfo.visibleItemsInfo.firstOrNull()
    val firstPlainUnit = readingUnits.getOrNull(plainListState.firstVisibleItemIndex)
    val firstPlainFraction = if (firstPlainItem != null && firstPlainItem.size > 0) {
        (-firstPlainItem.offset).coerceAtLeast(0).toFloat() / firstPlainItem.size
    } else {
        0f
    }
    val visiblePlainOffset = when {
        // 分页引擎开启时，位置的真源是引擎上报的页首偏移，滚动列表根本不在屏上
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedAbsOffset
        firstPlainUnit != null ->
            firstPlainUnit.charStart + (firstPlainUnit.charCount * firstPlainFraction).toInt()
        else -> 0
    }

    // 正文文本（TTS / 选择用）：EPUB 取当前章节，TXT 流式取当前可见窗口，小文件取全文
    // 必须在 plainListState / pagerEngineOn / readingUnits / chapterStartOffsets / visiblePlainOffset 之后定义
    var streamingContentText by remember(txtStreamingDocument) { mutableStateOf("") }
    val streamingWindowAnchor = (visiblePlainOffset / 2_000) * 2_000
    LaunchedEffect(txtStreamingDocument, streamingWindowAnchor) {
        val document = txtStreamingDocument ?: return@LaunchedEffect
        streamingContentText = withContext(Dispatchers.IO) {
            // 流式模式：磁盘读取不得发生在组合线程。
            document.readWindowAround(streamingWindowAnchor, 0, 8000)
        }
    }
    val contentText = remember(
        epubBook,
        markdownDocument,
        chapterBlocks,
        txtStreamingDocument,
        streamingContentText,
        plainContent,
    ) {
        when {
            epubBook != null ->
                chapterBlocks.filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }
            markdownDocument != null ->
                chapterBlocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()?.chapter?.canonicalText ?: ""
            txtStreamingDocument != null -> streamingContentText
            else -> plainContent
        }
    }

    val epubListState = rememberLazyListState()
    // TXT 当前所在章：按当前可见偏移反查。必须放在 visiblePlainOffset 之后。
    val txtChapterIndex = if (txtChapters.isEmpty()) {
        0
    } else {
        txtChapters.indexOfLast { it.startOffset <= visiblePlainOffset }.coerceAtLeast(0)
    }
    // C6：滚动模式切章时正文轻淡入（翻页模式由 PagedEpubView 的 contentAlpha 负责）；尊重「减少动态效果」
    val chapterFade = remember { Animatable(1f) }
    val chapterFadeKey = if (epubBook != null) chapterIndex else txtChapterIndex
    LaunchedEffect(chapterFadeKey) {
        if (reducedMotion) {
            chapterFade.snapTo(1f)
            return@LaunchedEffect
        }
        chapterFade.snapTo(0.45f)
        chapterFade.animateTo(1f, tween(durationMillis = 220))
    }
    // 顶栏副行与 TTS、书签都用它。TXT 此前恒为空串只能显示「正文」，
    // 现在有章节识别了就跟着滚动位置走。
    val currentChapterTitle = when {
        epubBook != null -> epubBook!!.chapters.getOrNull(chapterIndex)?.title ?: ""
        markdownDocument != null -> markdownDocument.chapters.getOrNull(chapterIndex)?.title ?: ""
        else -> txtChapters.getOrNull(txtChapterIndex)?.title ?: ""
    }
    val plainPercent = when {
        markdownDocument != null && pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        plainContent.isEmpty() && txtStreamingDocument == null -> 0f
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        !plainListState.canScrollForward && plainListState.firstVisibleItemIndex > 0 -> 100f
        txtStreamingDocument != null -> {
            val total = txtStreamingDocument!!.totalChars.coerceAtLeast(1)
            (visiblePlainOffset * 100f / total).coerceIn(0f, 100f)
        }
        else -> (visiblePlainOffset * 100f / plainContent.length).coerceIn(0f, 100f)
    }
    val progressPercent = when {
        // 分页引擎的进度按全书字符偏移算（EPUB 分母为估算值，够显示与存档用）
        pagerEngineOn && pagedAbsOffset >= 0 -> pagedPercent
        epubBook != null -> epubPercent
        else -> plainPercent
    }

    // 章节内进度（0-100）：底部进度条专用，显示当前章节内的阅读位置
    val chapterProgress: Float = run {
        val offsetInChapter: Int
        val chapterLen: Int
        if (epubBook != null) {
            val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
            offsetInChapter = (if (pagerEngineOn && pagedAbsOffset >= 0) pagedAbsOffset else visiblePlainOffset) - base
            chapterLen = contentText.length.coerceAtLeast(1)
        } else if (markdownDocument != null) {
            val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
            offsetInChapter = (if (pagerEngineOn && pagedAbsOffset >= 0) pagedAbsOffset else base) - base
            chapterLen = contentText.length.coerceAtLeast(1)
        } else {
            val ch = txtChapters.getOrNull(txtChapterIndex)
            val base = ch?.startOffset ?: 0
            offsetInChapter = (if (pagerEngineOn && pagedAbsOffset >= 0) pagedAbsOffset else visiblePlainOffset) - base
            chapterLen = (ch?.charCount ?: plainContent.length).coerceAtLeast(1)
        }
        (offsetInChapter * 100f / chapterLen).coerceIn(0f, 100f)
    }

    // 阅读统计派生值（对照 web：bookReadingTimeMs / estimateBookReadingSpeed）
    val plainWordCount = remember(plainContent) { plainContent.count { !it.isWhitespace() } }
    val documentWordCount = when {
        epubBook != null -> bookIndex?.totalChars ?: 0
        markdownDocument != null -> markdownDocument.totalChars
        txtStreamingDocument != null -> txtStreamingDocument!!.totalChars
        else -> plainWordCount
    }
    val sessionReadingMs = remember(sessions) { sessions.sumOf { it.duration_ms }.coerceAtLeast(0L) }
    val savedBookReadingMs = kotlin.math.max(savedTotalReadingMs, sessionReadingMs).coerceAtLeast(0L)
    val effectiveWordCount = if (documentWordCount > 0) documentWordCount else kotlin.math.max(1, bookSize / 3)
    val currentWords = kotlin.math.max(0L, kotlin.math.round(effectiveWordCount * kotlin.math.max(0f, progressPercent - sessionStartProgress) / 100f).toLong())
    val readerSpeed: Int = run {
        val cur = if (activeReadingMs >= 10_000L && currentWords > 0)
            kotlin.math.round(currentWords / (activeReadingMs / 60_000.0)).toInt() else 0
        if (cur > 0) cur
        else if (savedBookReadingMs > 0L && progressPercent > 0f)
            kotlin.math.round((effectiveWordCount * progressPercent / 100f) / (savedBookReadingMs / 60_000.0)).toInt()
        else 300
    }
    val remainingWords = kotlin.math.max(0L, kotlin.math.round(effectiveWordCount * (1f - progressPercent / 100f)).toLong())
    val estimatedRemainingMs = if (readerSpeed > 0) kotlin.math.round(remainingWords / readerSpeed.toDouble() * 60_000.0).toLong() else 0L
    val bookmarksCount = remember(notes) { notes.count { it.kind == "bookmark" } }
    val inspirationsCount = inspirations.size

    val effectivePaperBg = paperBg

    var currentMinute by remember { mutableIntStateOf(0) }
    LaunchedEffect(readerSettings.eyeCareScheduleEnabled) {
        while (true) {
            val calendar = java.util.Calendar.getInstance()
            currentMinute = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(java.util.Calendar.MINUTE)
            delay(60_000)
        }
    }
    val eyeCareActive = readerSettings.eyeCareFilterEnabled ||
        (readerSettings.eyeCareScheduleEnabled && EyeCareSchedule.isActive(
            currentMinute,
            readerSettings.eyeCareStartMinute,
            readerSettings.eyeCareEndMinute,
        ))
    val eyeRgb = remember(readerSettings.eyeCareTemperature) {
        EyeCareSchedule.rgbForKelvin(readerSettings.eyeCareTemperature)
    }
    val eyeFilterColor = Color(eyeRgb.first, eyeRgb.second, eyeRgb.third)

    // 朗读句高亮背景色（与 TXT 保持一致）：跟随纸张强调色（§4.3 accent @0.22）。
    val sentenceHighlightBg = paper.accent.copy(alpha = 0.22f)

    // T1/T2：当前章节各渲染块在全书文本中的全局偏移；以及 TTS 当前句在章节内的定位
    val chapterBase = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
    val blockGlobalOffsets = remember(epubBook, chapterStartOffsets, chapterIndex, chapterBlocks) {
        if (epubBook != null) computeBlockGlobalOffsets(chapterBlocks, chapterBase) else emptyList()
    }
    val ttsSentenceRangeInChapter = if (showTts && tts.status != "idle" && epubBook != null && contentText.isNotBlank()) {
        tts.currentSentenceRange
    } else null
    val ttsSentenceBlockIndex = remember(blockGlobalOffsets, ttsSentenceRangeInChapter) {
        if (ttsSentenceRangeInChapter != null) {
            val s = ttsSentenceRangeInChapter.first
            var idx = -1
            for (i in blockGlobalOffsets.indices) {
                val o = blockGlobalOffsets[i]
                if (o >= 0 && o <= s) idx = i else if (o > s) break
            }
            idx
        } else null
    }
    // 滚动聚焦块：优先 TTS 当前句，否则导航精准定位（T1 跳转用）
    var navFocusBlockIndex by remember { mutableStateOf<Int?>(null) }
    val focusBlockIndex = ttsSentenceBlockIndex ?: navFocusBlockIndex
    val epubBringRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusBlockIndex) {
        if (focusBlockIndex != null) epubBringRequester.bringIntoView()
    }

    /** 依据当前选区生成 locator_json（T1）。 */
    fun computeLocatorJson(): String? = when {
        epubBook != null && selectedGlobalOffset >= 0 -> {
            val ci = chapterStartOffsets.indexOfLast { it <= selectedGlobalOffset }.coerceAtLeast(0)
            val co = selectedGlobalOffset - chapterStartOffsets.getOrElse(ci) { 0 }
            LocatorCodec.encode(selectedGlobalOffset, ci, co, selectedText)
        }
        epubBook == null && selectedRangeStart >= 0 ->
            LocatorCodec.encode(selectedRangeStart, 0, selectedRangeStart, selectedText)
        else -> null
    }

    fun showNotice(msg: String) {
        scope.launch { snackbarHost.showSnackbar(msg) }
    }

    // R6：观察 TXT 规则扫描结果（放在 showNotice / pagedJumpRequest 之后）
    var pendingTxtRuleAnchorOffset by remember { mutableIntStateOf(-1) }
    LaunchedEffect(txtRuleScanResult) {
        val r = txtRuleScanResult ?: return@LaunchedEffect
        if (r.error != null) {
            showNotice(r.error)
        } else {
            r.document?.let { txtStreamingDocument = it }
            r.fileIndex?.let { txtStreamingFileIndex = it }
            // 重建完成后再跳转，确保 readingUnits 已是新数据
            if (pendingTxtRuleAnchorOffset >= 0) {
                pagedJumpRequest.value = pendingTxtRuleAnchorOffset
                pendingTxtRuleAnchorOffset = -1
            }
        }
    }

    val autoPagingPaused = !readerResumed ||
        sheet != null ||
        noteOpen ||
        selectedText.isNotBlank() ||
        showTts ||
        isLoading ||
        error != null
    val autoPagingSupported = readerSettings.readerMode == "scroll" ||
        (pagerEngineOn && pagedSource != null)

    // 滚动模式按帧匀速推进；弹层、选区、TTS 或切后台时保留“运行中”状态但暂停计时。
    LaunchedEffect(
        autoPagingActive,
        autoPagingPaused,
        readerSettings.readerMode,
        readerSettings.autoPageSpeed,
        epubBook,
    ) {
        if (!autoPagingActive || autoPagingPaused || readerSettings.readerMode != "scroll") {
            return@LaunchedEffect
        }
        val state = if (epubBook != null) epubListState else plainListState
        val accumulator = AutoScrollAccumulator()
        var previousFrame = withFrameNanos { it }
        while (autoPagingActive) {
            val frame = withFrameNanos { it }
            val elapsed = frame - previousFrame
            previousFrame = frame
            val pixels = accumulator.consume(
                speed = readerSettings.autoPageSpeed,
                viewportHeightPx = state.layoutInfo.viewportSize.height,
                elapsedNanos = elapsed,
            )
            if (pixels <= 0) continue
            state.scrollBy(pixels.toFloat())
            if (!state.canScrollForward) {
                autoPagingActive = false
                showNotice("已读到书末")
                break
            }
        }
    }

    // 自动翻页开启时给一次确认感触感（尊重系统「减少动态效果」）
    LaunchedEffect(autoPagingActive) {
        if (autoPagingActive) haptic(HapticFeedbackType.LongPress)
    }

    fun goToChapter(i: Int) {
        val maxIndex = when {
            epubBook != null -> epubBook!!.chapters.lastIndex
            markdownDocument != null -> markdownDocument.chapters.lastIndex
            else -> return
        }
        val clamped = i.coerceIn(0, maxIndex)
        if (pagerEngineOn) {
            pagedJumpRequest.value = chapterStartOffsets.getOrElse(clamped) { 0 }
        }
        chapterIndex = clamped
        tts.stop()
        // R6：章节块加载 + 进度保存统一由 ViewModel 处理
        onAction(ReaderAction.LoadChapter(bid, clamped))
    }

    /** TXT 跳转统一入口：分页引擎开着走翻页定位，否则滚动列表。两条路都以全书字符偏移为准。 */
    fun jumpToPlainOffset(offset: Int) {
        if (pagerEngineOn) {
            pagedJumpRequest.value = offset
        } else if (readingUnits.isNotEmpty()) {
            scope.launch { plainListState.scrollToItem(unitIndexForOffset(readingUnits, offset)) }
        }
    }

    fun persistCurrentProgress() {
        if (bid.isBlank() || loadedBook == null || error != null || pendingInitialPosition) return
        if (epubBook != null) {
            val chapterOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
                val currentChapter = pagedSource?.chapterIndexFor(pagedAbsOffset) ?: chapterIndex
                pagedAbsOffset - (pagedSource?.chapterStartAbs(currentChapter) ?: chapterBase)
            } else {
                val itemIndex = epubListState.firstVisibleItemIndex
                val blockStart = blockGlobalOffsets.getOrElse(itemIndex) { chapterBase }
                val baseOffset = (blockStart - chapterBase).coerceAtLeast(0)
                val blockLength = (chapterBlocks.getOrNull(itemIndex) as? DocBlock.Text)
                    ?.text
                    ?.length
                    ?: 0
                val visibleItem = epubListState.layoutInfo.visibleItemsInfo.firstOrNull()
                val fraction = if (visibleItem != null && visibleItem.size > 0) {
                    epubListState.firstVisibleItemScrollOffset.toFloat() / visibleItem.size
                } else {
                    0f
                }
                baseOffset + (blockLength * fraction).toInt()
            }.coerceAtLeast(0)
            val currentChapter = if (pagerEngineOn && pagedAbsOffset >= 0) {
                pagedSource?.chapterIndexFor(pagedAbsOffset) ?: chapterIndex
            } else {
                chapterIndex
            }
            val globalOffset = chapterStartOffsets.getOrElse(currentChapter) { 0 } + chapterOffset
            val totalChars = bookIndex?.totalChars?.coerceAtLeast(1) ?: 1
            val percent = (globalOffset * 100f / totalChars).coerceIn(0f, 100f)
            onAction(
                ReaderAction.SaveEpubProgress(
                    bookId = bid,
                    chapterIndex = currentChapter,
                    percent = percent,
                    offsetInChapter = chapterOffset,
                )
            )
        } else {
            val absoluteOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
                pagedAbsOffset
            } else {
                visiblePlainOffset
            }.coerceAtLeast(0)
            val totalChars = when {
                markdownDocument != null -> markdownDocument.totalChars
                txtStreamingDocument != null -> txtStreamingDocument!!.totalChars
                else -> plainContent.length
            }.coerceAtLeast(1)
            val percent = (absoluteOffset * 100f / totalChars).coerceIn(0f, 100f)
            onAction(
                ReaderAction.SaveProgress(
                    ReadingProgressEntity(
                        book_id = bid,
                        progress_percent = percent,
                        completion_state = if (percent >= 99.9f) "finished" else "reading",
                        current_location_json = if (markdownDocument != null) {
                            """{"offset":$absoluteOffset,"space":"canonical"}"""
                        } else {
                            """{"offset":$absoluteOffset}"""
                        },
                        updated_at = nowIso(),
                    )
                )
            )
        }
    }

    // ── 平台 Effects（常亮、沉浸、亮度、窗口底色、音量键、自动隐藏、生命周期）────
    ReaderPlatformEffects(
        keepAwake = readerSettings.keepAwake,
        immersiveMode = readerSettings.immersiveMode,
        controlsVisible = controlsVisible,
        paperIsLight = paper.isLight,
        appDark = appDark,
        readerBrightness = readerSettings.brightness.coerceIn(5, 100),
        paperBgColor = paperBg,
        volumeKeyPaging = readerSettings.volumeKeyPaging,
        onVolumeUp = {
            if (!readerSettings.volumeKeyPaging) false
            else if (screenState.showTts && !readerSettings.volumeKeyPagingDuringTts) false
            else {
                when {
                    pagerEngineOn -> pagedHardwareTurnRequest.value = -1
                    epubBook != null || markdownDocument != null -> goToChapter(chapterIndex - 1)
                    else -> scope.launch {
                        val amount = plainListState.layoutInfo.viewportSize.height * 0.88f * -1
                        plainListState.animateScrollBy(amount)
                    }
                }
                true
            }
        },
        onVolumeDown = {
            if (!readerSettings.volumeKeyPaging) false
            else if (screenState.showTts && !readerSettings.volumeKeyPagingDuringTts) false
            else {
                when {
                    pagerEngineOn -> pagedHardwareTurnRequest.value = 1
                    epubBook != null || markdownDocument != null -> goToChapter(chapterIndex + 1)
                    else -> scope.launch {
                        val amount = plainListState.layoutInfo.viewportSize.height * 0.88f * 1
                        plainListState.animateScrollBy(amount)
                    }
                }
                true
            }
        },
        onReaderResumed = { readerResumed = it },
        onPersistProgress = ::persistCurrentProgress,
        controlsVisibleForAutoHide = controlsVisible,
        autoHideSeconds = readerSettings.autoHideSeconds,
        sheetOpenGuard = sheetOpenGuard,
        onAutoHide = { controlsVisible = false },
    )

    // R6：进度滑块跳转（TXT 定位到百分比；EPUB 跳到对应章节；分页引擎按全书偏移精确定位）
    fun seekToPercent(p: Float) {
        if (epubBook != null) {
            if (pagerEngineOn) {
                val total = bookIndex?.totalChars ?: 0
                if (total > 0) pagedJumpRequest.value = (p.coerceIn(0f, 100f) / 100f * total).toInt()
            } else {
                val sz = epubBook!!.chapters.size
                if (sz > 0) goToChapter((p / 100f * sz).toInt().coerceIn(0, sz - 1))
            }
        } else if (plainContent.isNotEmpty() || txtStreamingDocument != null) {
            val totalLen = if (txtStreamingDocument != null) txtStreamingDocument!!.totalChars else plainContent.length
            jumpToPlainOffset((p.coerceIn(0f, 100f) / 100f * totalLen).toInt())
        }
    }

    // 章节内进度跳转：将章节内百分比转换为全书绝对偏移后定位
    fun seekToChapterPercent(p: Float) {
        val clamped = p.coerceIn(0f, 100f)
        if (epubBook != null) {
            val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
            val chLen = contentText.length.coerceAtLeast(1)
            val absOffset = base + (clamped / 100f * chLen).toInt()
            if (pagerEngineOn) {
                pagedJumpRequest.value = absOffset
            } else {
                jumpToPlainOffset(absOffset)
            }
        } else {
            val ch = txtChapters.getOrNull(txtChapterIndex)
            val base = ch?.startOffset ?: 0
            val chLen = (ch?.charCount ?: plainContent.length).coerceAtLeast(1)
            val absOffset = base + (clamped / 100f * chLen).toInt()
            jumpToPlainOffset(absOffset)
        }
    }

    // 本次阅读计时（对照 web useReaderSession.activeReadingMs）
    androidx.compose.runtime.LaunchedEffect(bid) {
        while (true) {
            delay(1000)
            if (!isLoading && error == null) activeReadingMs += 1000
        }
    }

    // 阅读提醒：护眼提醒 + 阅读节奏提示（对照 web useReaderReminders）
    androidx.compose.runtime.LaunchedEffect(bid) {
        var eyeLast = 0L
        var rhythmLast = 0L
        while (true) {
            delay(1000)
            val st = settingsRef.value
            val eyeMin = st.eyeCareReminderMinutes.coerceAtLeast(1)
            val eyeThreshold = eyeMin * 60_000L
            if (activeReadingMs >= eyeLast + eyeThreshold) {
                eyeLast = (activeReadingMs / eyeThreshold) * eyeThreshold
                showNotice("已连续阅读 ${eyeMin} 分钟，建议休息一下眼睛。")
            }
            if (st.readingRhythmReminderEnabled) {
                val rMin = st.readingRhythmReminderMinutes.coerceAtLeast(1)
                val rThreshold = rMin * 60_000L
                if (activeReadingMs >= rhythmLast + rThreshold) {
                    rhythmLast = (activeReadingMs / rThreshold) * rThreshold
                    showNotice("已读 ${rMin} 分钟，注意休息。")
                }
            }
        }
    }

    // R5：文档加载完成后，用当前章节初始化「最近浏览」置顶项。
    LaunchedEffect(loadedBook, chapterIndex) {
        if (loadedBook != null && recentChapters.isEmpty()) {
            recentChapters.add(chapterIndex)
        }
    }

    // 纯文本分块滚动进度落库，500ms 防抖避免频繁写盘（分页引擎开启时由下面的翻页持久化接管）
    androidx.compose.runtime.LaunchedEffect(bid, epubBook, readingUnits, pagerEngineOn) {
        if (bid.isBlank() || epubBook != null || readingUnits.isEmpty() || pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            Triple(
                plainListState.firstVisibleItemIndex,
                plainListState.firstVisibleItemScrollOffset,
                plainListState.canScrollForward,
            )
        }
            .debounce(500)
            .collect {
                val index = plainListState.firstVisibleItemIndex
                val unit = readingUnits.getOrNull(index)
                val item = plainListState.layoutInfo.visibleItemsInfo.firstOrNull()
                val fraction = if (item != null && item.size > 0) {
                    plainListState.firstVisibleItemScrollOffset.toFloat() / item.size
                } else {
                    0f
                }
                val offset = if (unit != null) {
                    unit.charStart + (unit.charCount * fraction).toInt()
                } else {
                    0
                }
                val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length.coerceAtLeast(1)
                val percent = when {
                    !plainListState.canScrollForward && index > 0 -> 100f
                    else -> (offset * 100f / totalChars).coerceIn(0f, 100f)
                }
                onAction(ReaderAction.SaveProgress(
                    ReadingProgressEntity(
                        book_id = bid,
                        progress_percent = percent,
                        completion_state = if (percent >= 99.9f) "finished" else "reading",
                        current_location_json = if (markdownDocument != null) {
                            """{"offset":${offset.coerceAtLeast(0)},"space":"canonical"}"""
                        } else {
                            """{"offset":${offset.coerceAtLeast(0)}}"""
                        },
                        updated_at = nowIso(),
                    ),
                ))
            }
    }

    // 翻页进度落库（分页引擎侧），与滚动侧同样 500ms 防抖、同一张表同一套字段
    androidx.compose.runtime.LaunchedEffect(bid, epubBook, pagerEngineOn, pagedSource) {
        if (bid.isBlank() || !pagerEngineOn) return@LaunchedEffect
        snapshotFlow { pagedAbsOffset to pagedPercent }
            .debounce(500)
            .collect { (off, pct) ->
                if (off < 0) return@collect
                val source = pagedSource ?: return@collect
                if (epubBook != null) {
                    val ci = source.chapterIndexFor(off)
                    val chapterOffset = off - source.chapterStartAbs(ci)
                    onAction(ReaderAction.SaveEpubProgress(bid, ci, pct, chapterOffset))
                } else {
                    onAction(ReaderAction.SaveProgress(
                        ReadingProgressEntity(
                            book_id = bid,
                            progress_percent = pct,
                            completion_state = if (pct >= 99.9f) "finished" else "reading",
                            current_location_json = if (markdownDocument != null) {
                                """{"offset":${off.coerceAtLeast(0)},"space":"canonical"}"""
                            } else {
                                """{"offset":${off.coerceAtLeast(0)}}"""
                            },
                            updated_at = nowIso(),
                        ),
                    ))
                }
            }
    }

    // 纯文本恢复上次滚动位置（分页引擎自己按 initialOffset 恢复；从翻页切回滚动时接上当前页位置）
    androidx.compose.runtime.LaunchedEffect(
        readingUnits,
        savedPlainOffset,
        savedPlainPercent,
        pagerEngineOn,
        pendingInitialPosition,
    ) {
        if (pendingInitialPosition && epubBook == null && readingUnits.isNotEmpty() && !pagerEngineOn) {
            val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length
            val targetOffset = if (pagedAbsOffset >= 0) {
                pagedAbsOffset
            } else if (savedPlainOffset > 0) {
                savedPlainOffset.coerceAtMost(totalChars.coerceAtLeast(0))
            } else if (savedPlainPercent > 0f) {
                (savedPlainPercent.coerceIn(0f, 100f) / 100f * totalChars).toInt()
            } else {
                0
            }
            plainListState.scrollToItem(unitIndexForOffset(readingUnits, targetOffset))
            pendingInitialPosition = false
        }
    }

    // EPUB 滚动模式恢复到章节内的具体文本块；分页模式由 PagedTxtReaderHost 的
    // initialOffset / onPositionChanged 接管。
    androidx.compose.runtime.LaunchedEffect(
        chapterBlocks,
        savedEpubOffsetInChapter,
        pagerEngineOn,
        pendingInitialPosition,
    ) {
        if (pendingInitialPosition && epubBook != null && !pagerEngineOn && chapterBlocks.isNotEmpty()) {
            val targetGlobal = chapterBase + savedEpubOffsetInChapter.coerceAtLeast(0)
            val itemIndex = blockGlobalOffsets
                .indexOfLast { it in 0..targetGlobal }
                .coerceAtLeast(0)
            epubListState.scrollToItem(itemIndex)
            pendingInitialPosition = false
        }
    }

    // SE4：精确跳转 —— 打开阅读器并定位到该高亮/笔记所在位置（优先 locator_json 行内偏移，兜底 chapter_title / progress_percent）。
    androidx.compose.runtime.LaunchedEffect(bid, epubBook, plainContent, highlights, notes, pendingHighlightId) {
        val hid = pendingHighlightId ?: return@LaunchedEffect
        if (isLoading || error != null) return@LaunchedEffect
        val target = highlights.firstOrNull { it.id == hid }
            ?: notes.firstOrNull { it.id == hid }
            ?: run {
                pendingHighlightId = null
                return@LaunchedEffect
            }
        val hl = target as? HighlightEntity
        val nt = target as? NoteEntity
        val chapterTitle = hl?.chapter_title ?: nt?.chapter_title
        val targetProgress = hl?.progress_percent ?: nt?.progress_percent
        val locatorJson = hl?.locator_json ?: nt?.locator_json
        val decodedLocator = LocatorCodec.decode(locatorJson)
        val targetKind = if (hl != null) "highlight" else "note"
        val targetId = hl?.id ?: nt?.id.orEmpty()
        val targetExcerpt = hl?.text ?: nt?.excerpt ?: nt?.body
        if (epubBook != null) {
            val book = epubBook!!
            if (decodedLocator != null && bookIndex != null) {
                val cached = anchorCacheStore.get(targetKind, targetId, bid)
                val resolved = cached ?: run {
                    val provisionalChapter = decodedLocator.chapterIndex
                        ?.coerceIn(0, book.chapters.lastIndex)
                        ?: chapterStartOffsets.indexOfLast {
                            it <= (decodedLocator.legacyOffset ?: 0)
                        }.coerceIn(0, book.chapters.lastIndex)
                    val chapterText = onExtractChapterText(bid, provisionalChapter)
                    AnchorResolver.resolve(
                        locator = decodedLocator,
                        chapterStarts = chapterStartOffsets,
                        chapterText = chapterText,
                        excerpt = targetExcerpt,
                    ).also {
                        anchorCacheStore.save(targetKind, targetId, bid, it)
                    }
                }
                val ci = resolved.chapterIndex.coerceIn(0, book.chapters.lastIndex)
                val locOffset = chapterStartOffsets.getOrElse(ci) { 0 } + resolved.charOffset
                goToChapter(ci)
                if (resolved.confidence == AnchorConfidence.APPROXIMATE) {
                    showNotice("原文可能已变化，已跳到最接近的位置")
                }
                if (pagerEngineOn) {
                    pagedJumpRequest.value = locOffset
                    navFocusBlockIndex = null
                } else {
                    // R6：通过 ViewModel 加载目标章块，避免主线程 Zip I/O 造成 ANR
                    val blocks = onLoadChapterBlocks(bid, ci)
                    navFocusBlockIndex = blockIndexForChapterOffset(blocks, resolved.charOffset)
                }
            } else {
                val idx = if (chapterTitle != null) {
                    val exact = book.chapters.indexOfFirst { it.title == chapterTitle }.takeIf { it >= 0 }
                    exact ?: progressToChapterIndex(book, targetProgress)
                } else {
                    progressToChapterIndex(book, targetProgress)
                }
                goToChapter(idx)
                navFocusBlockIndex = null
            }
        } else if (plainContent.isNotBlank() || txtStreamingDocument != null) {
            if (decodedLocator != null) {
                if (txtStreamingDocument != null) {
                    // 流式模式：有界窗口读取替代加载整个文件
                    // TXT locator 的 charOffset 实际为全书偏移（等价于 legacyOffset）
                    val targetOffset = decodedLocator.charOffset
                        ?: decodedLocator.legacyOffset
                        ?: 0
                    val windowStart = (targetOffset - 2000).coerceAtLeast(0)
                    val windowText = txtStreamingDocument!!.readWindowAround(targetOffset, 2000, 2000)
                    if (windowText.isNotEmpty()) {
                        // 将 locator 偏移调整为窗口相对偏移，供 AnchorResolver 使用
                        val windowRelativeLocator = decodedLocator.copy(
                            charOffset = (targetOffset - windowStart).coerceAtLeast(0),
                            legacyOffset = targetOffset - windowStart,
                            chapterIndex = 0,
                        )
                        val resolved = anchorCacheStore.get(targetKind, targetId, bid) ?: AnchorResolver.resolve(
                            locator = windowRelativeLocator,
                            chapterStarts = listOf(0),
                            chapterText = windowText,
                            excerpt = targetExcerpt,
                        ).also { anchorCacheStore.save(targetKind, targetId, bid, it) }
                        // 将解析结果转回全书偏移
                        val globalOffset = windowStart + resolved.charOffset
                        jumpToPlainOffset(globalOffset)
                        if (resolved.confidence == AnchorConfidence.APPROXIMATE) {
                            showNotice("原文可能已变化，已跳到最接近的位置")
                        }
                    }
                } else if (plainContent.isNotEmpty()) {
                    val resolved = anchorCacheStore.get(targetKind, targetId, bid) ?: AnchorResolver.resolve(
                        locator = decodedLocator,
                        chapterStarts = listOf(0),
                        chapterText = plainContent,
                        excerpt = targetExcerpt,
                    ).also { anchorCacheStore.save(targetKind, targetId, bid, it) }
                    jumpToPlainOffset(resolved.charOffset)
                    if (resolved.confidence == AnchorConfidence.APPROXIMATE) {
                        showNotice("原文可能已变化，已跳到最接近的位置")
                    }
                }
            } else if (targetProgress != null) {
                val totalLen = txtStreamingDocument?.totalChars ?: plainContent.length
                jumpToPlainOffset((targetProgress.coerceIn(0f, 100f) / 100f * totalLen).toInt())
            }
        }
        pendingHighlightId = null
    }

    // 打开 TTS：从当前正文（或跨会话续读位置）开始
    fun openTts() {
        if (contentText.isBlank()) {
            showNotice("当前没有可朗读的文字。")
            return
        }
        // R1：API 33+ 运行时申请通知权限，否则锁屏媒体控制无法显示
        if (Build.VERSION.SDK_INT >= 33) {
            val act = context as? Activity
            if (act != null && ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        val resumeAt = if (epubBook == null || ttsResumeChapter == chapterIndex) ttsResumeOffset else 0
        tts.play(contentText, bookTitle, currentChapterTitle.ifBlank { "正文" }, resumeAt)
        showTts = true
    }

    // R2：朗读时把正文跟到当前句 → TtsReaderSyncEffect
    TtsReaderSyncEffect(
        tts = tts,
        showTts = showTts,
        isTxt = isTxt,
        plainContent = plainContent,
        txtStreamingDocument = txtStreamingDocument,
        visiblePlainOffset = visiblePlainOffset,
        jumpToPlainOffset = ::jumpToPlainOffset,
        pagerEngineOn = pagerEngineOn,
        isEpub = epubBook != null,
        pagedJumpTo = { pagedJumpRequest.value = it },
        chapterStartOffsets = chapterStartOffsets,
        chapterIndex = chapterIndex,
    )

    // ── 笔记对话框（已提取到 ReaderNoteDialog）──────────────────────
    if (noteOpen) {
        com.creationreadingassistant.ui.screen.reader.sheets.ReaderNoteDialog(
            selectedText = selectedText,
            noteBody = noteBody,
            onNoteBodyChange = { noteBody = it },
            onSave = {
                // 同高亮保存：先快照局部变量，避免下面同步清空后读到空串
                val snapshotText = selectedText
                val snapshotBody = noteBody
                val snapshotLocator = computeLocatorJson()
                onAction(ReaderAction.SaveNote(
                    NoteEntity(
                        id = UUID.randomUUID().toString(),
                        book_id = bid.ifBlank { null },
                        title = (snapshotBody.ifBlank { snapshotText }).take(40),
                        body = snapshotBody,
                        excerpt = snapshotText.takeIf { it.isNotBlank() },
                        chapter_title = currentChapterTitle.ifBlank { null },
                        progress_percent = progressPercent,
                        kind = "note",
                        locator_json = snapshotLocator,
                        payload = "{}",
                        created_at = nowIso(),
                        device_id = null,
                        revision = 1,
                        updated_at = nowIso(),
                        deleted_at = null,
                    ),
                ))
                noteBody = ""
                noteOpen = false
                selectedText = ""
                showNotice("已保存笔记")
            },
            onDismiss = { noteOpen = false },
        )
    }

    fun handleChromeAction(action: ReaderChromeAction) {
        when (action) {
            ReaderChromeAction.Back -> onBack()
            ReaderChromeAction.ToggleTts -> {
                if (showTts) {
                    tts.stop()
                    showTts = false
                } else {
                    autoPagingActive = false
                    openTts()
                }
            }
            is ReaderChromeAction.OpenSheet -> {
                if (action.sheet == ReaderSheet.SEARCH) searchQuery = ""
                sheet = action.sheet
            }
            ReaderChromeAction.ToggleAutoPaging -> {
                if (autoPagingActive) {
                    autoPagingActive = false
                    controlsVisible = true
                } else if (!autoPagingSupported) {
                    showNotice("左右翻页模式需先开启新分页引擎")
                } else {
                    tts.stop()
                    showTts = false
                    selectedText = ""
                    autoPagingActive = true
                    controlsVisible = false
                }
            }
            ReaderChromeAction.HideControls -> controlsVisible = false
        }
    }

    Scaffold(
        modifier = Modifier.drawWithContent {
            drawContent()
            if (eyeCareActive) {
                drawRect(
                    color = eyeFilterColor.copy(
                        alpha = readerSettings.eyeCareIntensity.coerceIn(0, 100) / 100f,
                    ),
                    blendMode = BlendMode.Multiply,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (controlsVisible) {
                ReaderTopChrome(
                    bookTitle = bookTitle,
                    formatLabel = if (epubBook != null) "EPUB" else "TXT",
                    chapterTitle = currentChapterTitle,
                    progressPercent = progressPercent,
                    paper = paper,
                    overflowExpanded = showReaderOverflow,
                    autoPagingActive = autoPagingActive,
                    onOverflowExpandedChange = { showReaderOverflow = it },
                    onAction = ::handleChromeAction,
                )
            }
        },
        bottomBar = {
            if (controlsVisible) {
                Surface(
                    color = paper.bg,
                    contentColor = paper.fg,
                    border = BorderStroke(1.dp, paper.outlineVariant),
                ) {
                    if (showTts) {
                        TtsBar(
                            paper = paper,
                            tts = tts,
                            chapterLabel = currentChapterTitle.ifBlank { "正文" },
                            onPersistTts = { p, v, id, t ->
                                settingsVm.updateReader {
                                    copy(
                                        ttsPitch = p,
                                        ttsVolume = v,
                                        ttsVoiceId = id,
                                        ttsTimedStopMinutes = t,
                                    )
                                }
                            },
                        ) {
                            tts.stop()
                            showTts = false
                        }
                    } else {
                        ReaderBottomActions(
                            onAction = ::handleChromeAction,
                            chapterProgress = chapterProgress,
                            onSeekProgress = { seekToChapterPercent(it) },
                            onPreviousChapter = { goToChapter(chapterIndex - 1) },
                            onNextChapter = { goToChapter(chapterIndex + 1) },
                            isFirstChapter = chapterIndex <= 0,
                            isLastChapter = epubBook == null || chapterIndex >= epubBook!!.chapters.lastIndex,
                            paper = paper,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!controlsVisible) {
                ReaderCollapsedControl(
                    autoPagingActive = autoPagingActive,
                    pagerEngineOn = pagerEngineOn,
                    onPauseAutoPaging = {
                        autoPagingActive = false
                        controlsVisible = true
                    },
                    onShowControls = { controlsVisible = true },
                )
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // 背景必须在 padding 之前铺：沉浸模式藏掉系统栏后，腾出的区域
                // 也要是纸色，否则那里露出的是窗口底色（黑条）
                .background(effectivePaperBg)
                .semantics {
                    if (!isLoading && !isChapterLoading && error == null && loadedBook != null) {
                        contentDescription = "阅读正文已就绪"
                    }
                }
                // 沉浸时内容延伸进了刘海区（SHORT_EDGES），正文要让开打孔摄像头那一条；
                // 纸色背景仍然铺满整屏（在 padding 之前），所以让出来的部分不是黑边
                // 始终避让挖孔区域：无论是否沉浸模式，正文都不会被刘海/打孔遮挡
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout),
        ) {
            when {
                isLoading || error != null -> ReaderDocumentStatus(
                    isLoading = isLoading,
                    errorMessage = error,
                    foreground = paperFg,
                    onBack = onBack,
                    onRetry = {
                        runtimeError = null
                        onDocumentAction(ReaderAction.Retry)
                    },
                )

                pagerEngineOn && pagedSource != null -> {
                    // 试验分页引擎：TXT 与 EPUB 共用排版/手势/高亮链路。
                    // EPUB 首版只排文字块；图片分页与四档动画仍按 P4 后续刀次推进。
                    PagedReaderHost(
                        source = pagedSource,
                        fontSizeSp = readerSettings.fontSize,
                        lineHeightMultiplier = readerSettings.lineHeight,
                        paragraphSpacing = readerSettings.paragraphSpacing,
                        pageMarginDp = readerSettings.pageMargin,
                        fontWeightBold = readerSettings.fontWeightBold,
                        showReaderInfo = readerSettings.showReaderInfo,
                        chineseTypography = readerSettings.chineseTypography,
                        tapZoneMode = readerSettings.tapZoneMode,
                        pageTurnEffect = readerSettings.pageTurnEffect,
                        textColor = paperFg,
                        headerLeft = readerSettings.headerLeft,
                        headerRight = readerSettings.headerRight,
                        footerLeft = readerSettings.footerLeft,
                        footerRight = readerSettings.footerRight,
                        bookName = bookTitle,
                        initialOffset = when {
                            pagedAbsOffset >= 0 -> pagedAbsOffset
                            epubBook != null ->
                                chapterStartOffsets.getOrElse(chapterIndex) { 0 } + savedEpubOffsetInChapter
                            visiblePlainOffset > 0 -> visiblePlainOffset
                            savedPlainOffset > 0 -> savedPlainOffset
                            else -> (savedPlainPercent.coerceIn(0f, 100f) / 100f * (txtStreamingDocument?.totalChars ?: plainContent.length)).toInt()
                        },
                        jumpRequest = pagedJumpRequest,
                        externalTurnRequest = pagedHardwareTurnRequest,
                        onPositionChanged = { off, pct ->
                            pagedAbsOffset = off
                            pagedPercent = pct
                            pendingInitialPosition = false
                            if (epubBook != null || markdownDocument != null) {
                                val ci = pagedSource?.chapterIndexFor(off) ?: chapterIndex
                                if (ci != chapterIndex) goToChapter(ci)
                            }
                        },
                        onToggleControls = { controlsVisible = !controlsVisible },
                        store = pageIndexStore,
                        contentKey = when {
                            epubBook != null -> bid
                            markdownDocument != null -> "$bid|md"
                            else -> "$bid|toc=$txtTocRuleId"
                        },
                        ttsRangeAbs = if (showTts && tts.status != "idle") {
                            if (epubBook != null) {
                                val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
                                (base + tts.currentSentenceRange.first) to
                                    (base + tts.currentSentenceRange.second)
                            } else if (markdownDocument != null) {
                                // Markdown：tts.currentSentenceRange 是章内规范文本偏移，转全书偏移
                                val base = pagedSource?.chapterStartAbs(chapterIndex) ?: 0
                                (base + tts.currentSentenceRange.first) to
                                    (base + tts.currentSentenceRange.second)
                            } else if (txtStreamingDocument != null) {
                                // 流式 TXT：tts.currentSentenceRange 是章内偏移，需加章起始偏移转全书偏移
                                val base = chapterStartOffsets.getOrElse(txtChapterIndex) { 0 }
                                (base + tts.currentSentenceRange.first) to
                                    (base + tts.currentSentenceRange.second)
                            } else {
                                tts.currentSentenceRange
                            }
                        } else {
                            null
                        },
                        onSelect = { text, absStart ->
                            selectedText = text
                            if (epubBook != null) {
                                selectedGlobalOffset = absStart
                                selectedRangeStart = -1
                            } else {
                                selectedRangeStart = absStart
                                selectedGlobalOffset = -1
                            }
                        },
                        selectionCleared = selectedText.isBlank(),
                        selectionColor = paper.accent.copy(alpha = 0.30f),
                        ttsHighlightColor = sentenceHighlightBg,
                        persistentHighlights = remember(highlights) {
                            highlights.mapNotNull { h ->
                                val start = parseLocatorOffset(h.locator_json) ?: return@mapNotNull null
                                val len = h.text.length
                                if (len <= 0) return@mapNotNull null
                                (start until start + len) to
                                    paper.highlight(h.color ?: "yellow")
                            }
                        },
                        autoPageIntervalMillis = if (autoPagingActive && !autoPagingPaused) {
                            AutoPagingTiming.pageIntervalMillis(readerSettings.autoPageSpeed)
                        } else {
                            null
                        },
                        onAutoPagingFinished = {
                            autoPagingActive = false
                            showNotice("已读到书末")
                        },
                    )
                }

                epubBook != null -> {
                    val book = epubBook!!
                    val chapter = book.chapters.getOrNull(chapterIndex)
                    if (isChapterLoading) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .semantics { contentDescription = "正在加载章节" },
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = paper.accent)
                        }
                    } else if (chapter == null || chapterBlocks.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("本章暂无可读内容。", color = paperFg)
                        }
                    } else if (readerSettings.readerMode == "paged") {
                        PagedEpubView(
                            blocks = chapterBlocks,
                            fontSize = readerSettings.fontSize,
                            lineHeight = readerSettings.lineHeight,
                            fontWeightBold = readerSettings.fontWeightBold,
                            pageMargin = readerSettings.pageMargin,
                            paperFg = paperFg,
                            tapZoneMode = readerSettings.tapZoneMode,
                            pageTurnEffect = readerSettings.pageTurnEffect,
                            chapterIndex = chapterIndex,
                            canPrev = chapterIndex > 0,
                            canNext = chapterIndex < book.chapters.lastIndex,
                            onPrev = { goToChapter(chapterIndex - 1) },
                            onNext = { goToChapter(chapterIndex + 1) },
                            onSelectBlock = { text, off -> selectedText = text; selectedGlobalOffset = off },
                            blockGlobalOffsets = blockGlobalOffsets,
                            chapterBase = chapterBase,
                            ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
                            focusBlockIndex = focusBlockIndex,
                            sentenceHighlightBg = sentenceHighlightBg,
                            bringRequester = epubBringRequester,
                        )
                    } else {
                        LazyColumn(
                            state = epubListState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = readerSettings.pageMargin.dp, vertical = readerSettings.pageMargin.dp).graphicsLayer { alpha = chapterFade.value },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(chapterBlocks) { idx, block ->
                                when (block) {
                                    is DocBlock.Text -> {
                                        val gOff = blockGlobalOffsets.getOrElse(idx) { -1 }
                                        val ann = buildSentenceHighlighted(
                                            block.text, gOff, chapterBase, ttsSentenceRangeInChapter, sentenceHighlightBg,
                                        )
                                        Text(
                                            text = ann,
                                            style = TextStyle(
                                                textAlign = TextAlign.Justify,
                                                lineHeight = (readerSettings.fontSize * readerSettings.lineHeight).sp,
                                                textIndent = if (block.isHeading) TextIndent.None else TextIndent(firstLine = (readerSettings.fontSize * 2).sp),
                                            ),
                                            fontSize = readerSettings.fontSize.sp,
                                            fontWeight = if (block.isHeading || readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                            color = paperFg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(if (idx == focusBlockIndex) Modifier.bringIntoViewRequester(epubBringRequester) else Modifier)
                                                .clickable {
                                                    selectedText = block.text
                                                    selectedGlobalOffset = gOff
                                                    selectedRangeStart = -1
                                                },
                                        )
                                    }

                                    is DocBlock.Image -> {
                                        val imageFile = remember(block.path) { java.io.File(block.path) }
                                        val imageRequest = rememberViewportImageRequest(
                                            data = imageFile,
                                            cacheKey = "reader:${block.path}:${imageFile.lastModified()}",
                                        )
                                        AsyncImage(
                                            model = imageRequest,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                        )
                                    }

                                    is DocBlock.Markdown -> {
                                        // EPUB 路径不会出现 Markdown 块
                                    }
                                }
                            }
                        }
                    }
                }

                markdownDocument != null -> {
                    val markdownBlock = chapterBlocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
                    if (isChapterLoading) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .semantics { contentDescription = "正在加载章节" },
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = paper.accent)
                        }
                    } else if (markdownBlock == null) {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("本章暂无可读内容。", color = paperFg)
                        }
                    } else {
                        LazyColumn(
                            state = epubListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = readerSettings.pageMargin.dp, vertical = readerSettings.pageMargin.dp),
                        ) {
                            item {
                                RenderMarkdownChapter(
                                    chapter = markdownBlock.chapter,
                                    fontSize = readerSettings.fontSize,
                                    lineHeight = readerSettings.lineHeight,
                                    paperFg = paperFg,
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = plainListState,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = chapterFade.value },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = readerSettings.pageMargin.dp,
                            vertical = readerSettings.pageMargin.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = readingUnits,
                            key = { _, unit -> unit.unitIndex },
                        ) { _, unit ->
                            // 惰性加载：只在 composable 组合时加载文本
                            // 流式模式使用 LRU 缓存，避免重复 IO
                            val unitText = if (txtStreamingDocument != null) {
                                unitCache.getOrLoad(unit.unitIndex) { txtStreamingDocument!!.readUnit(unit) }
                            } else {
                                remember(unit.unitIndex, plainContent) {
                                    plainContent.substring(
                                        unit.charStart,
                                        (unit.charStart + unit.charCount).coerceAtMost(plainContent.length)
                                    )
                                }
                            }
                            val ttsRange = if (showTts && tts.status != "idle" && isTxt) {
                                if (txtStreamingDocument != null) {
                                    // 流式 TXT：contentText 是窗口，sentenceRange 是窗口内偏移
                                    // 加 visiblePlainOffset 转全书偏移后再与 unit.charStart 做差
                                    val base = visiblePlainOffset
                                    (base + tts.currentSentenceRange.first) to (base + tts.currentSentenceRange.second)
                                } else {
                                    tts.currentSentenceRange
                                }
                            } else {
                                0 to 0
                            }
                            val localStart = (ttsRange.first - unit.charStart).coerceIn(0, unitText.length)
                            val localEnd = (ttsRange.second - unit.charStart).coerceIn(0, unitText.length)
                            val annotated = remember(unitText, localStart, localEnd, sentenceHighlightBg) {
                                if (localEnd > localStart) {
                                    AnnotatedString.Builder(unitText).apply {
                                        addStyle(
                                            SpanStyle(background = sentenceHighlightBg),
                                            localStart,
                                            localEnd,
                                        )
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
                                        selectedText = unitText.substring(value.selection.start, value.selection.end)
                                        selectedRangeStart = unit.charStart + value.selection.start
                                        selectedGlobalOffset = -1
                                    } else if (selectedRangeStart in unit.charStart until (unit.charStart + unitText.length)) {
                                        selectedText = ""
                                        selectedRangeStart = -1
                                    }
                                },
                                readOnly = true,
                                textStyle = TextStyle(
                                    fontSize = readerSettings.fontSize.sp,
                                    lineHeight = (readerSettings.fontSize * readerSettings.lineHeight).sp,
                                    color = paperFg,
                                    textAlign = TextAlign.Justify,
                                    fontWeight = if (readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 显示进度条：正文底部 2dp 细线（此前该开关是摆设）
            if (readerSettings.showProgressBar && !isLoading && error == null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                    color = paper.accent,
                    trackColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            }

            // 选中文字工具条（对照 web 选中工具栏）：带入场动效（尊重「减少动态效果」）
            AnimatedVisibility(
                visible = selectedText.isNotBlank(),
                enter = if (reducedMotion) fadeIn(tween(120)) else (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(160))),
                exit = if (reducedMotion) fadeOut(tween(120)) else (slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(120))),
            ) {
                SelectionToolbar(
                    paper = paper,
                    selectedText = selectedText,
                    showColorRow = showColorRow,
                    onToggleColor = { showColorRow = !showColorRow },
                    onPickColor = { color ->
                        val locator = computeLocatorJson()
                        // 先快照局部变量：下面马上把 selectedText 清空，
                        // ViewModel IO 协程晚一步才读的话高亮就存成空串。
                        val snapshotText = selectedText
                        onAction(ReaderAction.SaveHighlight(
                            HighlightEntity(
                                id = UUID.randomUUID().toString(),
                                book_id = bid,
                                text = snapshotText,
                                note = null,
                                color = color,
                                chapter_title = currentChapterTitle.ifBlank { null },
                                progress_percent = progressPercent,
                                locator_json = locator,
                                payload = "{}",
                                created_at = nowIso(),
                                device_id = null,
                                revision = 1,
                                updated_at = nowIso(),
                                deleted_at = null,
                            ),
                        ))
                        showColorRow = false
                        selectedText = ""
                        selectedGlobalOffset = -1
                        selectedRangeStart = -1
                        showNotice("已高亮")
                    },
                    onAiExplain = { sheet = ReaderSheet.AI_EXPLAIN },
                    onInspiration = { sheet = ReaderSheet.INSPIRATION },
                    onNote = { noteOpen = true },
                    onCopy = { clipboard.setText(AnnotatedString(selectedText)); showNotice("已复制") },
                    onSearch = { searchQuery = selectedText; sheet = ReaderSheet.SEARCH },
                    onClear = { selectedText = ""; showColorRow = false; selectedGlobalOffset = -1; selectedRangeStart = -1 },
                )
            }

            // 底部弹层（不新建路由，内部状态切换）
            sheet?.let { type ->
            GlassModalBottomSheet(
                onDismissRequest = { sheet = null },
                sheetState = sheetState,
                containerColor = paper.bg,
                shape = LocalComponentSpec.current.sheetShape,
                dragHandle = { SheetHandle() },
            ) {
                    when (type) {
                        ReaderSheet.TOC -> TocSheet(
                            titles = epubBook?.chapters?.map { it.title }
                                ?: txtChapters.map { it.title },
                            current = if (epubBook != null) chapterIndex else txtChapterIndex,
                            recent = recentChapters.toList(),
                            onPick = {
                                if (!recentChapters.contains(it)) {
                                    recentChapters.add(0, it)
                                    if (recentChapters.size > 5) recentChapters.removeAt(recentChapters.lastIndex)
                                }
                                if (epubBook != null) {
                                    goToChapter(it)
                                } else {
                                    // TXT 跳章 = 定位到该章起始偏移（翻页/滚动两种视图都认它）
                                    txtChapters.getOrNull(it)?.let { c -> jumpToPlainOffset(c.startOffset) }
                                }
                                sheet = null
                            },
                            txtRules = if (isTxt) TxtChapterDetector.rules else emptyList(),
                            selectedTxtRule = txtTocRuleId,
                            txtRulePreviews = txtRulePreviews,
                            onTxtRule = { ruleId ->
                                val anchorOffset = visiblePlainOffset
                                txtTocRuleId = ruleId
                                val streamingTempFilePath = textContent?.ownedTempFile?.absolutePath
                                if (txtStreamingDocument != null && streamingTempFilePath != null) {
                                    // R6：流式模式 TXT 规则扫描由 ViewModel 处理
                                    pendingTxtRuleAnchorOffset = anchorOffset
                                    onAction(ReaderAction.ScanTxtTocRule(streamingTempFilePath!!, ruleId))
                                } else {
                                    pagedJumpRequest.value = anchorOffset
                                }
                                onAction(ReaderAction.SaveTxtTocRule(bid, ruleId))
                            },
                        )

                        ReaderSheet.NOTES -> NotesSheet(
                            paper = paper,
                            highlights = highlights,
                            notes = notes,
                            inspirations = inspirations,
                            onAddBookmark = {
                                onAction(ReaderAction.AddBookmark(bid, progressPercent.toInt(), currentChapterTitle.ifBlank { "正文" }))
                                showNotice("已添加书签")
                            },
                            onDeleteHighlight = { h ->
                                onAction(ReaderAction.DeleteHighlight(h.id))
                            },
                            onDeleteNote = { n ->
                                onAction(ReaderAction.DeleteNote(n.id))
                            },
                            onChangeHighlightColor = { h, c ->
                                onAction(ReaderAction.UpdateHighlightColor(h.id, c))
                            },
                            onEditHighlightNote = { h, note ->
                                onAction(ReaderAction.UpdateHighlightNote(h.id, note))
                            },
                            onHighlightToNote = { h ->
                                onAction(ReaderAction.ConvertHighlightToNote(h.id))
                                showNotice("已转为笔记")
                            },
                            onHighlightToInspiration = { h ->
                                onAction(ReaderAction.ConvertHighlightToInspiration(h.id))
                                showNotice("已转为灵感")
                            },
                            onJumpToHighlight = { h ->
                                // R4：复用 SE4「按高亮 id 精确定位」逻辑（pendingHighlightId 驱动 LaunchedEffect）
                                pendingHighlightId = h.id
                                sheet = null
                            },
                            onExportHighlights = {
                                val sb = StringBuilder()
                                sb.appendLine("# 《${bookTitle}》书摘")
                                highlights.groupBy { it.chapter_title ?: "" }.forEach { (chapter, items) ->
                                    sb.appendLine()
                                    sb.appendLine("## ${if (chapter.isBlank()) "未分类" else chapter}")
                                    items.forEachIndexed { i, h ->
                                        sb.appendLine("${i + 1}. ${h.text}")
                                        h.note?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   批注：$it") }
                                    }
                                }
                                val intent = Intent(Intent.ACTION_SEND)
                                intent.type = "text/plain"
                                intent.putExtra(Intent.EXTRA_TITLE, "《${bookTitle}》书摘")
                                intent.putExtra(Intent.EXTRA_TEXT, sb.toString())
                                context.startActivity(Intent.createChooser(intent, "导出书摘"))
                            },
                        )

                        ReaderSheet.AI_ASSIST -> AiAssistSheet(
                            aiClient = aiClient,
                            bookTitle = bookTitle,
                            chapterTitle = currentChapterTitle,
                            contextText = selectedText.ifBlank { contentText },
                        )

                        ReaderSheet.AI_EXPLAIN -> AiExplainSheet(
                            aiClient = aiClient,
                            selectedText = selectedText,
                            bookTitle = bookTitle,
                            categories = categories,
                            tags = tags,
                            onCreateCategory = { name ->
                                val id = "mobile-category-${UUID.randomUUID()}"
                                onAction(ReaderAction.CreateCategory(name))
                                id
                            },
                            onCreateTag = { name ->
                                val id = "mobile-tag-${UUID.randomUUID()}"
                                onAction(ReaderAction.CreateTag(name))
                                id
                            },
                            onSaveInspiration = { body, tags, categoryIds ->
                                val snapshotText = selectedText
                                onAction(ReaderAction.SaveInspiration(
                                    InspirationEntity(
                                        id = UUID.randomUUID().toString(),
                                        title = "AI 解读：${snapshotText.take(24)}",
                                        body = body,
                                        type = "note",
                                        status = "inbox",
                                        source_book_id = bid.ifBlank { null },
                                        payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor),
                                        created_at = nowIso(),
                                        device_id = null,
                                        revision = 1,
                                        updated_at = nowIso(),
                                        deleted_at = null,
                                    ),
                                ))
                                sheet = null
                                showNotice("已存入灵感")
                            },
                        )

                        ReaderSheet.INSPIRATION -> InspirationSheet(
                            bookTitle = bookTitle,
                            chapterTitle = currentChapterTitle,
                            excerpt = selectedText,
                            progressPercent = progressPercent,
                            categories = categories,
                            tags = tags,
                            onCreateCategory = { name ->
                                val id = "mobile-category-${UUID.randomUUID()}"
                                onAction(ReaderAction.CreateCategory(name))
                                id
                            },
                            onCreateTag = { name ->
                                val id = "mobile-tag-${UUID.randomUUID()}"
                                onAction(ReaderAction.CreateTag(name))
                                id
                            },
                            onSave = { title, body, tags, categoryIds ->
                                val snapshotText = selectedText
                                onAction(ReaderAction.SaveInspiration(
                                    InspirationEntity(
                                        id = UUID.randomUUID().toString(),
                                        title = title,
                                        body = body,
                                        type = "note",
                                        status = "inbox",
                                        source_book_id = bid.ifBlank { null },
                                        payload = buildInspirationPayload(bid, bookTitle, currentChapterTitle, snapshotText, progressPercent, tags, categoryIds, bookAuthor = bookAuthor),
                                        created_at = nowIso(),
                                        device_id = null,
                                        revision = 1,
                                        updated_at = nowIso(),
                                        deleted_at = null,
                                    ),
                                ))
                                selectedText = ""
                                sheet = null
                                showNotice("已保存灵感，并记录来源阅读位置")
                            },
                        )

                        ReaderSheet.SETTINGS -> SettingsSheet(
                            paper = paper,
                            fontSize = readerSettings.fontSize,
                            lineHeight = readerSettings.lineHeight,
                            background = readerSettings.background,
                            bold = readerSettings.fontWeightBold,
                            brightness = readerSettings.brightness,
                            readerMode = readerSettings.readerMode,
                            pagerEngineMode = readerSettings.pagerEngineMode,
                            epubPagerEngineMode = readerSettings.epubPagerEngineMode,
                            pageTurnEffect = readerSettings.pageTurnEffect,
                            tapZoneMode = readerSettings.tapZoneMode,
                            pageMargin = readerSettings.pageMargin,
                            paragraphSpacing = readerSettings.paragraphSpacing,
                            eyeCareMin = readerSettings.eyeCareReminderMinutes,
                            eyeFilterEnabled = readerSettings.eyeCareFilterEnabled,
                            eyeTemperature = readerSettings.eyeCareTemperature,
                            eyeIntensity = readerSettings.eyeCareIntensity,
                            eyeScheduleEnabled = readerSettings.eyeCareScheduleEnabled,
                            volumeKeyPaging = readerSettings.volumeKeyPaging,
                            volumeKeyPagingDuringTts = readerSettings.volumeKeyPagingDuringTts,
                            autoPageSpeed = readerSettings.autoPageSpeed,
                            rhythmEnabled = readerSettings.readingRhythmReminderEnabled,
                            rhythmMin = readerSettings.readingRhythmReminderMinutes,
                            onFontSize = { settingsVm.updateReader { copy(fontSize = it) } },
                            onLineHeight = { settingsVm.updateReader { copy(lineHeight = it) } },
                            onBackground = { settingsVm.updateReader { copy(background = it) } },
                            onBrightness = { settingsVm.updateReader { copy(brightness = it) } },
                            onBold = { settingsVm.updateReader { copy(fontWeightBold = it) } },
                            onReaderMode = { settingsVm.updateReader { copy(readerMode = it) } },
                            onPagerEngineMode = { settingsVm.updateReader { copy(pagerEngineMode = it) } },
                            onEpubPagerEngineMode = { settingsVm.updateReader { copy(epubPagerEngineMode = it) } },
                            onPageTurnEffect = { settingsVm.updateReader { copy(pageTurnEffect = it) } },
                            onTapZoneMode = { settingsVm.updateReader { copy(tapZoneMode = it) } },
                            onPageMargin = { settingsVm.updateReader { copy(pageMargin = it) } },
                            onParagraphSpacing = { settingsVm.updateReader { copy(paragraphSpacing = it) } },
                            onEyeCareMin = { settingsVm.updateReader { copy(eyeCareReminderMinutes = it) } },
                            onEyeFilterEnabled = { settingsVm.updateReader { copy(eyeCareFilterEnabled = it) } },
                            onEyeTemperature = { settingsVm.updateReader { copy(eyeCareTemperature = it) } },
                            onEyeIntensity = { settingsVm.updateReader { copy(eyeCareIntensity = it) } },
                            onEyeScheduleEnabled = { settingsVm.updateReader { copy(eyeCareScheduleEnabled = it) } },
                            onVolumeKeyPaging = { settingsVm.updateReader { copy(volumeKeyPaging = it) } },
                            onVolumeKeyPagingDuringTts = { settingsVm.updateReader { copy(volumeKeyPagingDuringTts = it) } },
                            onAutoPageSpeed = { settingsVm.updateReader { copy(autoPageSpeed = it) } },
                            onRhythmEnabled = { settingsVm.updateReader { copy(readingRhythmReminderEnabled = it) } },
                            onRhythmMin = { settingsVm.updateReader { copy(readingRhythmReminderMinutes = it) } },
                            immersiveMode = readerSettings.immersiveMode,
                            showReaderInfo = readerSettings.showReaderInfo,
                            chineseTypography = readerSettings.chineseTypography,
                            keepAwake = readerSettings.keepAwake,
                            showProgressBar = readerSettings.showProgressBar,
                            autoHideSeconds = readerSettings.autoHideSeconds,
                            onImmersive = { settingsVm.updateReader { copy(immersiveMode = it) } },
                            onShowInfo = { settingsVm.updateReader { copy(showReaderInfo = it) } },
                            onChineseTypo = { settingsVm.updateReader { copy(chineseTypography = it) } },
                            onKeepAwake = { settingsVm.updateReader { copy(keepAwake = it) } },
                            onShowProgress = { settingsVm.updateReader { copy(showProgressBar = it) } },
                            onAutoHide = { settingsVm.updateReader { copy(autoHideSeconds = it) } },
                            headerLeft = readerSettings.headerLeft,
                            headerRight = readerSettings.headerRight,
                            footerLeft = readerSettings.footerLeft,
                            footerRight = readerSettings.footerRight,
                            onHeaderLeft = { settingsVm.updateReader { copy(headerLeft = it) } },
                            onHeaderRight = { settingsVm.updateReader { copy(headerRight = it) } },
                            onFooterLeft = { settingsVm.updateReader { copy(footerLeft = it) } },
                            onFooterRight = { settingsVm.updateReader { copy(footerRight = it) } },
                            onBookInfo = { sheet = ReaderSheet.BOOK_INFO },
                        )

                        ReaderSheet.THEME -> ThemeSheet(
                            background = readerSettings.background,
                            onBackground = { settingsVm.updateReader { copy(background = it) } },
                        )

                        ReaderSheet.PROGRESS -> ProgressSheet(
                            epubBook = epubBook,
                            chapterIndex = chapterIndex,
                            currentChapterTitle = currentChapterTitle,
                            progressPercent = progressPercent,
                            activeReadingMs = activeReadingMs,
                            readerSpeed = readerSpeed,
                            estimatedRemainingMs = estimatedRemainingMs,
                            savedBookReadingMs = savedBookReadingMs,
                            inspirationsCount = inspirationsCount,
                            bookmarksCount = bookmarksCount,
                            onChapter = { goToChapter(it) },
                            onSeekPercent = { seekToPercent(it) },
                            isTxt = isTxt,
                        )

                        ReaderSheet.SEARCH -> SearchSheet(
                            document = epubDocument,
                            txtDocument = txtStreamingDocument,
                            plainContent = plainContent,
                            chapterStartOffsets = chapterStartOffsets,
                            chapterTitles = chapterTitles,
                            totalChars = bookIndex?.totalChars
                                ?: txtStreamingDocument?.totalChars ?: 0,
                            isTxt = isTxt,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onJump = { result ->
                                if (result.chapterIndex >= 0 && epubBook != null) {
                                    goToChapter(result.chapterIndex)
                                    val globalOffset = chapterStartOffsets.getOrElse(result.chapterIndex) { 0 } +
                                        result.charOffset
                                    if (pagerEngineOn) {
                                        pagedJumpRequest.value = globalOffset
                                    } else {
                                        scope.launch {
                                            val blocks = onLoadChapterBlocks(bid, result.chapterIndex)
                                            navFocusBlockIndex = blockIndexForChapterOffset(blocks, result.charOffset)
                                        }
                                    }
                                } else if (plainContent.isNotEmpty() || txtStreamingDocument != null) {
                                    val globalOffset = chapterStartOffsets.getOrElse(result.chapterIndex.coerceAtLeast(0)) { 0 } +
                                        result.charOffset
                                    jumpToPlainOffset(globalOffset)
                                }
                                sheet = null
                            },
                        )

                        ReaderSheet.BOOK_INFO -> BookInfoSheet(
                            bookTitle = bookTitle,
                            bookAuthor = bookAuthor,
                            bookFormat = if (epubBook != null) "EPUB" else "TXT",
                            chapterCount = epubBook?.chapters?.size ?: 0,
                            wordCount = documentWordCount,
                            currentChapterTitle = currentChapterTitle,
                            progressPercent = progressPercent,
                            activeReadingMs = activeReadingMs,
                            savedReadingMs = savedBookReadingMs,
                            sessionsCount = sessions.size,
                            sourceFile = bookOriginalFile,
                            onOpenSettings = { sheet = ReaderSheet.SETTINGS },
                            onDelete = {
                                onAction(ReaderAction.DeleteBook(bid))
                                onBack()
                            },
                        )
                    }
                }
            }
        }
    }
}
