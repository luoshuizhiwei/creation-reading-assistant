package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderUiState
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanResult
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus

/**
 * 阅读会话导航模式：普通阅读 vs 临时查阅。
 *
 * [TEMPORARY] 期间自动进度保存不得覆盖普通阅读进度，普通位置上报也被抑制；
 * 该模式由 ReaderRoute 从路由参数（`navigationMode=temporary`）解析后注入。
 */
enum class ReaderNavigationMode { NORMAL, TEMPORARY }

/**
 * ReaderScreen 数据参数封装。
 * 将 22 个参数缩减为 2 个 data class，降低 Compose 编译器生成的字节码复杂度，
 * 避免 Android ART 验证器因方法签名过大而抛出 VerifyError。
 */
data class ReaderScreenInputs(
    val bookId: String?,
    val highlightId: String?,
    /** 可选的通用 source locator 路由参数；仅描述原文位置，不是页号或显示偏移。 */
    val sourceLocatorJson: String? = null,
    /** 阅读会话导航模式（区分普通阅读与临时查阅的路由注入预留，默认普通阅读）。 */
    val navigationMode: ReaderNavigationMode = ReaderNavigationMode.NORMAL,
    val documentUiState: ReaderUiState,
    val screenState: ReaderScreenState,
    val highlights: List<HighlightEntity>,
    val notes: List<NoteEntity>,
    val inspirations: List<InspirationEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val sessions: List<ReadingSessionEntity>,
    val readChapters: List<Int>,
    val txtTocRuleIdFromVm: String,
    val chapterLoadResult: ChapterLoadResult?,
    val txtRuleScanResult: TxtRuleScanResult?,
    val txtRuleScanStatus: TxtRuleScanStatus? = null,
    val ruleSnapshot: RuleSnapshot = RuleSnapshot.empty(),
    val ruleMutationResult: RuleMutationResult? = null,
)

data class ReaderScreenCallbacks(
    val onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock>,
    val onExtractChapterText: suspend (String, Int) -> String,
    val onAction: (ReaderAction) -> Unit,
    val onDocumentAction: (ReaderAction) -> Unit,
    val onBack: () -> Unit,
    val settingsStore: SettingsStore,
    val aiClient: AiClient,
    val pageIndexStore: PageIndexStore,
    val anchorCacheStore: AnchorCacheStore,
    val pagerHealthStore: PagerHealthStore,
    val pageIndexManager: ReaderPageIndexManager,
    /** 当前精确 source 位置上报 seam；由 ReaderRoute 注入并落到临时查阅协调器 recordNormalReading。 */
    val onSourcePositionChanged: (SourceNavigationTarget) -> Unit = {},
    /** 是否存在可返回的临时目标（实时派生自协调器临时栈；普通阅读位置不算）。 */
    val hasReturnableTarget: Boolean = false,
    /** 返回阅读处：与顶栏 Back / 系统 Back 复用同一个 LIFO 返回动作。 */
    val onTemporaryReturn: () -> Unit = {},
)
