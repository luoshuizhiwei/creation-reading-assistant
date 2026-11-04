package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState

/**
 * 路由渲染聚合状态与内部多路源数据模型。
 */
internal data class ReaderBookData(
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val inspirations: List<InspirationEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
    val readChapters: List<Int> = emptyList(),
)

internal data class ReaderRouteSources(
    val document: ReaderUiState,
    val screen: ReaderScreenState,
    val book: ReaderBookData,
    val taxonomy: ReaderTaxonomyData,
    val auxiliary: ReaderAuxiliaryState,
)

internal data class ReaderTaxonomyData(
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
)

internal data class ReaderAuxiliaryState(
    val txtTocRuleId: String = "builtin",
    val chapterLoadResult: ChapterLoadResult? = null,
    val txtRuleScanResult: TxtRuleScanResult? = null,
    val txtRuleScanStatus: TxtRuleScanStatus = TxtRuleScanStatus.Idle,
)

data class ReaderRouteUiState(
    val document: ReaderUiState = ReaderUiState(),
    val screen: ReaderScreenState = ReaderScreenState(),
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val inspirations: List<InspirationEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
    val readChapters: List<Int> = emptyList(),
    val txtTocRuleId: String = "builtin",
    val chapterLoadResult: ChapterLoadResult? = null,
    val txtRuleScanResult: TxtRuleScanResult? = null,
    val txtRuleScanStatus: TxtRuleScanStatus = TxtRuleScanStatus.Idle,
    val ruleSnapshot: RuleSnapshot = RuleSnapshot.empty(),
    val ruleMutationResult: RuleMutationResult? = null,
)
