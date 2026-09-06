package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId
import com.creationreadingassistant.ui.screen.reader.ttsDisplayLocalToSourceLocal
import com.creationreadingassistant.ui.screen.reader.ttsSentenceGlobalSourceRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TTS 设置同步：首次将持久化的引擎/音调/音量/音色/定时停止载入控制器（仅一次，避免播放中回灌导致重读）。
 */
@Composable
internal fun TtsSettingsSyncEffect(
    tts: TtsEngineHost,
    readerSettings: ReaderSettings,
) {
    var ttsSynced by remember { mutableStateOf(false) }
    LaunchedEffect(readerSettings) {
        if (!ttsSynced) {
            tts.switchEngine(TtsEngineId.fromKey(readerSettings.ttsEngine))
            tts.pitch = readerSettings.ttsPitch
            tts.volume = readerSettings.ttsVolume
            tts.voiceId = readerSettings.ttsVoiceId
            tts.setTimedStop(readerSettings.ttsTimedStopMinutes)
            ttsSynced = true
        }
    }
}

/**
 * TTS 跨会话续读：加载本书上次朗读句偏移；并把句变化持久化（含本会话续读偏移）。
 */
@Composable
internal fun TtsResumeEffect(
    tts: TtsEngineHost,
    bookId: String,
    settingsStore: SettingsStore,
    isEpub: Boolean,
    isMarkdown: Boolean,
    chapterIndex: Int,
    onResumeOffsetChanged: (Int) -> Unit,
    onResumeChapterChanged: (Int) -> Unit,
    pagerEngineOn: Boolean = false,
    pagedSource: PagedChapterSource? = null,
) {
    val bid = bookId
    // P1-B：长生命周期 onSentence 回调读取最新章号，跨章朗读不再保存旧章
    val currentChapterIndex by rememberUpdatedState(chapterIndex)
    // EPUB 分页净化投影活跃时播放文本是 display 空间；续读持久化一律回写 source 口径
    val latestPagerEngineOn by rememberUpdatedState(pagerEngineOn)
    val latestPagedSource by rememberUpdatedState(pagedSource)
    LaunchedEffect(bid) {
        val r = runCatching { settingsStore.loadTtsResume() }.getOrNull()
        onResumeOffsetChanged(if (r?.bookId == bid) r.offset else 0)
        onResumeChapterChanged(if (r?.bookId == bid) r.chapterIndex else -1)
        tts.onSentence = { start, _ ->
            val persistOffset = if (isEpub && latestPagerEngineOn) {
                ttsDisplayLocalToSourceLocal(latestPagedSource, currentChapterIndex, start)
            } else {
                start
            }
            onResumeOffsetChanged(persistOffset)
            val resumeChapter = ttsResumeChapterFor(isEpub, isMarkdown, currentChapterIndex)
            onResumeChapterChanged(resumeChapter)
            launch(Dispatchers.IO) {
                runCatching { settingsStore.saveTtsResume(bid, resumeChapter, persistOffset) }
            }
        }
    }
}

/**
 * TTS 朗读同步：朗读时把正文跟到当前句（TXT 与 EPUB 分页），与网页 ttsSyncToReader 对齐。
 */
@Composable
internal fun TtsReaderSyncEffect(
    tts: TtsEngineHost,
    showTts: Boolean,
    isTxt: Boolean,
    isMarkdown: Boolean,
    plainContent: String,
    txtStreamingDocument: PlainTextDocument?,
    visiblePlainOffset: Int,
    jumpToPlainOffset: (Int) -> Unit,
    pagerEngineOn: Boolean,
    isEpub: Boolean,
    pagedJumpTo: (Int) -> Unit,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
    pagedSource: PagedChapterSource? = null,
) {
    LaunchedEffect(tts.status, tts.currentSentenceRange) {
        if (showTts && tts.status != "idle") {
            val start = tts.currentSentenceRange.first
            val target = ttsFollowGlobalOffset(
                isTxt = isTxt,
                isMarkdown = isMarkdown,
                isEpub = isEpub,
                pagerEngineOn = pagerEngineOn,
                plainContent = plainContent,
                txtStreamingDocument = txtStreamingDocument,
                visiblePlainOffset = visiblePlainOffset,
                sentenceStart = start,
                chapterStartOffsets = chapterStartOffsets,
                chapterIndex = chapterIndex,
                pagedSource = pagedSource,
            ) ?: return@LaunchedEffect
            if (isTxt) {
                // 兼容旧路径：TXT 小文件/流式窗口统一经 jumpToPlainOffset（内部处理分页/滚动）
                jumpToPlainOffset(target)
            } else {
                pagedJumpTo(target)
            }
        }
    }
}

/**
 * 章末自动接续（听书连续朗读）：末句播完且仍有后章时翻到下一章，章正文就绪后
 * 从头继续朗读；末章播完或朗读已关闭时不动作（保持「读完即停」）。
 *
 * 实现要点：[TtsEngineHost.onFinished] 只负责翻章；重播等 [contentReady] 变为
 * true（新章加载完成）再触发，避免对加载中的空文本发起朗读。
 */
@Composable
internal fun TtsAutoNextChapterEffect(
    tts: TtsEngineHost,
    showTts: Boolean,
    chapterIndex: Int,
    chapterCount: Int,
    contentReady: Boolean,
    goToChapter: (Int) -> Unit,
    replayTts: () -> Unit,
) {
    val advancing = remember { mutableStateOf(false) }
    val latestChapterIndex by rememberUpdatedState(chapterIndex)
    val latestChapterCount by rememberUpdatedState(chapterCount)
    LaunchedEffect(tts, showTts) {
        tts.onFinished = if (showTts) {
            {
                if (latestChapterIndex < latestChapterCount - 1) {
                    advancing.value = true
                    goToChapter(latestChapterIndex + 1)
                }
            }
        } else {
            null
        }
    }
    LaunchedEffect(contentReady, chapterIndex) {
        if (advancing.value && contentReady) {
            advancing.value = false
            replayTts()
        }
    }
}

/** 跨会话续读应保存的章号：EPUB / Markdown 按章跟踪，TXT 保持无章号语义。 */
internal fun ttsResumeChapterFor(
    isEpub: Boolean,
    isMarkdown: Boolean,
    chapterIndex: Int,
): Int = if (isEpub || isMarkdown) chapterIndex else -1

/**
 * 打开 TTS 时的续读起点：TXT 沿用「始终用保存偏移」的既有语义；
 * EPUB / Markdown 的朗读文本以章为单位，只有保存章与当前章一致时才使用偏移。
 */
internal fun ttsResumeStartAt(
    isEpub: Boolean,
    isMarkdown: Boolean,
    ttsResumeChapter: Int,
    chapterIndex: Int,
    ttsResumeOffset: Int,
): Int = if ((!isEpub && !isMarkdown) || ttsResumeChapter == chapterIndex) ttsResumeOffset else 0

/**
 * 把 TTS 当前句在播放文本中的偏移映射回全书偏移；无法映射返回 null。
 * - TXT 小文件：播放文本即全文，偏移即全局；
 * - 流式 TXT：播放文本是可见位置窗口，偏移需加窗口基址；
 * - EPUB / Markdown 分页：播放文本是当前章规范文本，偏移需加章起始偏移。
 */
internal fun ttsFollowGlobalOffset(
    isTxt: Boolean,
    isMarkdown: Boolean,
    isEpub: Boolean,
    pagerEngineOn: Boolean,
    plainContent: String,
    txtStreamingDocument: PlainTextDocument?,
    visiblePlainOffset: Int,
    sentenceStart: Int,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
    pagedSource: PagedChapterSource? = null,
): Int? {
    if (isTxt && (plainContent.isNotEmpty() || txtStreamingDocument != null)) {
        val ttsTotalLen = txtStreamingDocument?.totalChars ?: plainContent.length
        val ttsBase = if (txtStreamingDocument != null) visiblePlainOffset else 0
        val globalStart = ttsBase + sentenceStart
        return if (globalStart in 0 until ttsTotalLen) globalStart else null
    }
    if (pagerEngineOn && (isEpub || isMarkdown)) {
        if (isEpub) {
            // EPUB 分页：句偏移在播放文本（display）空间，经章级投影映射回全书 source
            return ttsSentenceGlobalSourceRange(
                pagedSource, chapterStartOffsets, chapterIndex, sentenceStart to sentenceStart,
            ).first
        }
        return chapterStartOffsets.getOrElse(chapterIndex) { 0 } + sentenceStart
    }
    return null
}
