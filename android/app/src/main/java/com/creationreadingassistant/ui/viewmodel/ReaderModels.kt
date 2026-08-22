package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.ui.screen.reader.ReaderSheet

/**
 * 阅读器状态 / 动作 / 结果模型。
 *
 * 从 [ReaderDocumentLoader] 文件拆出：文档加载器只保留 IO 与资源所有权职责，
 * 这些与加载器无关的 UI 状态模型集中在此文件。
 */

data class ReaderUiState(
    val requestedBookId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loadedBook: ReaderLoadedBook? = null,
) {
    val isReady: Boolean
        get() = loadedBook != null && !isLoading && errorMessage == null
}

// ── 规则：当前书快照 + 最近一次写入反馈 ────────────────────────
data class RuleUiState(
    val snapshot: RuleSnapshot = RuleSnapshot.empty(),
    val mutationResult: RuleMutationResult? = null,
)

// ── 章节加载结果 ─────────────────────────────────────────────
sealed interface ChapterLoadResult {
    data class Loading(val bookId: String, val chapterIndex: Int) : ChapterLoadResult
    data class Loaded(val bookId: String, val chapterIndex: Int, val blocks: List<DocBlock>) : ChapterLoadResult
    data class Error(val bookId: String, val chapterIndex: Int, val message: String) : ChapterLoadResult
}

// ── TXT 规则扫描结果 ─────────────────────────────────────────
data class TxtRuleScanResult(
    val bookId: String,
    val ruleId: String,
    val requestId: Long,
    val fileIndex: TxtFileIndex? = null,
    val document: PlainTextDocument? = null,
    val error: String? = null,
)

// ── TXT 规则扫描状态（UI 进度 / 取消 / 完成章数）───────────────
sealed interface TxtRuleScanStatus {
    /** 无扫描。 */
    data object Idle : TxtRuleScanStatus

    /** 扫描中；[progress] 为 0..1，未知总大小时为 null（indeterminate）。 */
    data class Running(val bookId: String, val ruleId: String, val progress: Float?) : TxtRuleScanStatus

    /** 扫描完成；[chapterCount] 为真实识别章数。 */
    data class Completed(val bookId: String, val ruleId: String, val chapterCount: Int) : TxtRuleScanStatus

    /** 用户显式取消；不发布结果，原 document/fileIndex 保持不变。 */
    data class Cancelled(val bookId: String, val ruleId: String) : TxtRuleScanStatus

    /** 扫描失败；[message] 可展示给用户。 */
    data class Failed(val bookId: String, val ruleId: String, val message: String) : TxtRuleScanStatus
}

/** 状态是否属于当前书会话（跨书状态不得用于当前书 UI）。 */
fun TxtRuleScanStatus.matchesSession(bookId: String): Boolean = when (this) {
    TxtRuleScanStatus.Idle -> true
    is TxtRuleScanStatus.Running -> this.bookId == bookId
    is TxtRuleScanStatus.Completed -> this.bookId == bookId
    is TxtRuleScanStatus.Cancelled -> this.bookId == bookId
    is TxtRuleScanStatus.Failed -> this.bookId == bookId
}

/**
 * 换规则前记录的「书 + 规则 + 锚点偏移」；只有与扫描结果书/规则一致的请求才能消费它，
 * 防止旧扫描把阅读位置跳到另一本书或另一个规则请求的锚点（P1-A）。
 */
data class PendingTxtRuleAnchor(
    val bookId: String,
    val ruleId: String,
    val offset: Int,
)

/** 扫描结果是否属于当前书籍会话（跨书迟到结果不得应用到新书状态）。 */
fun TxtRuleScanResult.matchesSession(bookId: String): Boolean = this.bookId == bookId

/** 扫描结果是否可以消费给定的 pending anchor（书 + 规则必须一致）。 */
fun TxtRuleScanResult.canConsumeAnchor(pending: PendingTxtRuleAnchor?): Boolean =
    pending != null && pending.bookId == bookId && pending.ruleId == ruleId

sealed interface ReaderAction {
    // ── 文档协调 ──
    data class OpenBook(val bookId: String) : ReaderAction
    data object Retry : ReaderAction
    data class LoadChapter(val bookId: String, val chapterIndex: Int) : ReaderAction
    /**
     * 死路径（任务 #15 小清理）：生产代码零派发，TOC 规则切换已改走
     * [com.creationreadingassistant.ui.viewmodel.RuleCommand]（ExecuteRuleCommand）+
     * [RescanTxtToc]。仅保留实现与测试驱动（扫描状态机的取消/竞争/进度语义
     * 仍由本入口覆盖），不得新增调用方。
     */
    @Deprecated(
        message = "生产零派发：TOC 规则扫描请走 ExecuteRuleCommand + RescanTxtToc",
        replaceWith = ReplaceWith("RescanTxtToc(bookId, filePath, profileKeyHint)"),
    )
    data class ScanTxtTocRule(val bookId: String, val filePath: String, val ruleId: String) : ReaderAction
    /**
     * 规则写入成功后按当前 Room 生效规则重新识别流式 TXT（P1-A）。
     * [profileKeyHint] 是 UI 从快照推导的身份提示，VM 以 Room 权威快照复核，
     * 不一致即丢弃（避免竞态）。重建后的位置恢复走 UI 侧的
     * [PendingTxtRuleAnchor]（当前 source 绝对偏移）+ pagedJumpRequest。
     */
    data class RescanTxtToc(
        val bookId: String,
        val filePath: String,
        val profileKeyHint: String,
    ) : ReaderAction
    data object CancelTxtTocScan : ReaderAction

    // ── UI 状态变更 ──
    data class ToggleControls(val visible: Boolean? = null) : ReaderAction
    /** 翻页后立即隐藏菜单（无论 autoHideSeconds 是否 0）；走 ReaderChromeReducer.PageTurn。 */
    data object PageTurn : ReaderAction
    /** 自动隐藏倒计时到点；走 ReaderChromeReducer.AutoHideElapsed，携带当时的秒数（0=不隐藏）。 */
    data class AutoHideElapsed(val autoHideSeconds: Int) : ReaderAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderAction
    data object CloseSheet : ReaderAction
    data class SetSelectedText(
        val text: String,
        val rangeStart: Int,
        val globalOffset: Int,
        val sourceEnd: Int = -1,
    ) : ReaderAction
    data object ClearSelection : ReaderAction
    data class SetShowTts(val show: Boolean) : ReaderAction
    data class SetSearchQuery(val query: String) : ReaderAction
    data class SetShowOverflow(val show: Boolean) : ReaderAction
    data class SetNoteOpen(val open: Boolean) : ReaderAction
    data class SetNoteBody(val body: String) : ReaderAction
    data object ToggleColorRow : ReaderAction

    // ── 数据写入 ──
    data class SaveHighlight(val highlight: HighlightEntity) : ReaderAction
    data class DeleteHighlight(val highlightId: String) : ReaderAction
    data class UpdateHighlightColor(val highlightId: String, val color: String) : ReaderAction
    data class UpdateHighlightNote(val highlightId: String, val note: String) : ReaderAction
    data class SaveNote(val note: NoteEntity) : ReaderAction
    data class DeleteNote(val noteId: String) : ReaderAction
    /**
     * 新增书签。统一携带 v2 locator（[locatorJson]）与绝对偏移 / 章节内偏移，
     * 使书签与高亮、笔记、搜索跳转共用同一套 locator 解析（SE4）。
     * [offset] 保留为 [progress_percent] 兜底值（兼容性 / 无 locator 旧书签）。
     */
    data class AddBookmark(
        val bookId: String,
        val offset: Int,
        val title: String,
        val absOffset: Int = -1,
        val chapterIndex: Int = -1,
        val charOffset: Int = -1,
        val locatorJson: String? = null,
    ) : ReaderAction
    data class ConvertHighlightToNote(val highlightId: String) : ReaderAction
    data class ConvertHighlightToInspiration(val highlightId: String) : ReaderAction
    data class SaveInspiration(val inspiration: InspirationEntity) : ReaderAction
    data class CreateCategory(val name: String) : ReaderAction
    data class CreateTag(val name: String) : ReaderAction
    data class SaveProgress(val progress: ReadingProgressEntity) : ReaderAction
    data class UpdateReadingActivity(
        val bookId: String,
        val active: Boolean,
        val progressPercent: Float?,
    ) : ReaderAction
    data class SaveEpubProgress(
        val bookId: String,
        val chapterIndex: Int,
        val percent: Float,
        val offsetInChapter: Int = 0,
        val absoluteOffset: Int = -1,
    ) : ReaderAction
    data class DeleteBook(val bookId: String) : ReaderAction
    data class ClearChapterReads(val bookId: String) : ReaderAction

    // ── 规则（RulesRepository）──
    /** 执行规则写入命令；结果经 Repository 校验/迁移后发布到 ruleMutationResult。 */
    data class ExecuteRuleCommand(val bookId: String, val command: RuleCommand) : ReaderAction

    /** 清除最近一次规则写入反馈（UI 已消费后调用）。 */
    data object ClearRuleMutationResult : ReaderAction

    // ── 设置 ──
    data class LoadTxtTocRule(val bookId: String) : ReaderAction
    data class SaveTxtTocRule(val bookId: String, val ruleId: String) : ReaderAction
    data class SaveTtsResume(val bookId: String, val chapterIndex: Int, val offset: Int) : ReaderAction
}
