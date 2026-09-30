package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel

/**
 * 阅读器内改设置的**唯一落盘入口**（R3-P1）。
 *
 * 设置面板与灯绳操作的都是**有效设置**（全局 + 本书覆盖），本次改动该落到哪一层由
 * [com.creationreadingassistant.data.settings.ReaderSettingsRouter] 按用户选择的
 * [ReaderSettingsScope] 决定：不可覆盖项永远进全局；「本书」范围下可覆盖项只写本书覆盖
 * （绝不污染全局）。
 *
 * 抽成顶层函数是因为**有两个调用方**：书内设置面板（含主题面板）与一键日/夜灯绳。
 * 若各自复制一份路由逻辑，两边迟早会分叉——灯绳改纸张时漏掉本书覆盖，就是
 * 「本书覆盖了纸张 → 拉灯绳没反应」这类只在特定用户设置下复现的 bug。
 */
internal fun applyReaderEdit(
    settingsVm: SettingsViewModel,
    bid: String,
    scope: ReaderSettingsScope,
    global: ReaderSettings,
    displayed: ReaderSettings,
    edited: ReaderSettings,
    showNotice: (String) -> Unit,
) {
    val plan = com.creationreadingassistant.data.settings.ReaderSettingsRouter.plan(
        scope = scope,
        global = global,
        displayed = displayed,
        edited = edited,
    )
    if (plan.globalDiffersFrom(global)) {
        settingsVm.updateReader { plan.global }
    }
    val hasBookScopedChange =
        plan.overridesToSet.isNotEmpty() || plan.overridesToClear.isNotEmpty()
    if (bid.isNotBlank()) {
        plan.overridesToSet.forEach { (key, value) -> settingsVm.setOverride(bid, key, value) }
        plan.overridesToClear.forEach { key -> settingsVm.clearOverride(bid, key) }
    } else if (hasBookScopedChange && scope == ReaderSettingsScope.BOOK) {
        // 「只改本书」但没有 bookId 时，覆盖项无处可写。以前这里是静默丢弃：用户看着
        // 设置已经变了，重进面板又弹回原值，且没有任何解释。宁可明确说没保存，
        // 也不要把一次未落盘的编辑伪装成成功。
        // 作用范围＝全局时不提示：那部分改动已经写进全局设置，并没有丢。
        showNotice("无法确定当前书籍，本次「只改本书」的调整未保存")
    }
}
