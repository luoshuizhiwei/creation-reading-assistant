package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleDescriptor
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 规则变更与全文搜索索引刷新协同器。
 *
 * 负责解析规则命令影响范围（单书 vs 全局），并在应用级作用域触发索引重建。
 */
internal object ReaderSearchIndexCoordinator {

    /** 搜索结果刷新方案；[global] 表示该变更影响全库（GLOBAL 作用域）。 */
    data class SearchIndexRefreshPlan(val global: Boolean)

    /**
     * 判定一条规则命令是否需要刷新搜索索引、是否影响全库。
     *
     * 只有**替换（REPLACE）规则**会改变索引内容：搜索索引按内置 TOC 分章、按生效替换规则
     * 投影显示文通道，故替换规则一变旧 display 行即失真；TOC 规则不参与索引分章，不影响索引。
     *
     * 必须在 [RulesRepository.execute] **之前**调用 —— 删除类命令执行后规则行已消失，
     * 无从再查其类型 / 作用域。
     */
    suspend fun planForCommand(
        command: RuleCommand,
        rulesRepository: RulesRepository,
    ): SearchIndexRefreshPlan? = when (command) {
        is RuleCommand.SaveCustomReplace ->
            SearchIndexRefreshPlan(global = command.scope == RuleScope.GLOBAL)
        is RuleCommand.ToggleCustom -> replaceRulePlan(command.ruleId, rulesRepository)
        is RuleCommand.DeleteCustom -> replaceRulePlan(command.ruleId, rulesRepository)
        // 单处纠错只影响本书 display 文本：保存/撤销/恢复都重建本书索引。
        is RuleCommand.SaveSingleCorrection,
        is RuleCommand.UndoCorrection,
        is RuleCommand.RestoreCorrection,
        -> SearchIndexRefreshPlan(global = false)
        else -> null
    }

    /**
     * 当前选区的全书 source 半开区间；无可锚定选区返回 null。
     *
     * 分页投影路径两种来源：TXT 选区起点在 [ReaderScreenState.selectedRangeStart]，
     * EPUB 选区起点在 [ReaderScreenState.selectedGlobalOffset]；终点统一在
     * selectedSourceEnd（旧路径 -1 表示不可锚定）。
     */
    fun currentSelectionSourceRange(state: ReaderScreenState): Pair<Int, Int>? {
        val end = state.selectedSourceEnd
        if (end < 0) return null
        val start = when {
            state.selectedRangeStart >= 0 -> state.selectedRangeStart
            state.selectedGlobalOffset >= 0 -> state.selectedGlobalOffset
            else -> return null
        }
        return if (start in 0 until end) start to end else null
    }

    private suspend fun replaceRulePlan(
        ruleId: String,
        rulesRepository: RulesRepository,
    ): SearchIndexRefreshPlan? {
        val descriptor: RuleDescriptor = rulesRepository.describeRule(ruleId) ?: return null
        return if (descriptor.kind == RuleKind.REPLACE) {
            SearchIndexRefreshPlan(global = descriptor.scope == RuleScope.GLOBAL)
        } else {
            null
        }
    }

    /**
     * 执行索引刷新：PER_BOOK 立即重建当前书；GLOBAL 置脏 + 后台 worker 惰性重建全库。
     *
     * 一律放到应用级作用域，绝不在阅读器交互路径上同步重建。
     */
    fun applySearchIndexRefresh(
        bookId: String,
        plan: SearchIndexRefreshPlan,
        appScope: CoroutineScope,
        searchIndexRepository: SearchIndexRepository,
        searchIndexScheduler: SearchIndexScheduler,
    ) {
        appScope.launch {
            runCatching {
                if (plan.global) {
                    searchIndexRepository.invalidateSweepForFullRebuild()
                    searchIndexScheduler.triggerNow(force = true)
                } else {
                    searchIndexRepository.reindexBookById(bookId)
                }
            }.onFailure {
                AppLog.e(
                    "SearchIndex",
                    "规则变更后刷新索引失败（book=${bookId.take(8)}，global=${plan.global}）：" +
                        (it.message ?: it.javaClass.simpleName),
                )
            }
        }
    }
}
