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
 * ReaderScreen 数据参数封装。
 * 将 22 个参数缩减为 2 个 data class，降低 Compose 编译器生成的字节码复杂度，
 * 避免 Android ART 验证器因方法签名过大而抛出 VerifyError。
 */
data class ReaderScreenInputs(
    val bookId: String?,
    val highlightId: String?,
    val documentUiState: ReaderUiState,
    val screenState: ReaderScreenState,
    val highlights: List<HighlightEntity>,
    val notes: List<NoteEntity>,
    val inspirations: List<InspirationEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val sessions: List<ReadingSessionEntity>,
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
)
