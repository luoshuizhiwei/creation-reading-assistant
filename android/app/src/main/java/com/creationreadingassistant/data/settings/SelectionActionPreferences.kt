package com.creationreadingassistant.data.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * 选区动作与查词配置的**最小消费面**，由 Reader 侧（[SelectionActionStore] 的拥有者）维护。
 *
 * 存在理由：Profile 的「选区与查词」子页只需要读一份快照、再把用户意图派发回来。若让消费侧
 * 直接注入 [SelectionActionStore]，或依赖整个 `SettingsViewModel`，就会在 Reader 之外长出第二份
 * 默认值 / sanitize / URL 校验 / 动作排序与过滤，或者让消费侧触达与选区无关的设置面。
 * 因此这里只暴露快照与六个命令；槽位上下限、固定组不可隐藏、模板必须含 `{q}`、词典模式白名单
 * 等全部裁决仍唯一地留在 [SelectionActionStore] 内，消费侧无法绕过，也无需复制。
 */
interface SelectionActionPreferences {
    /** 当前生效的选区设置快照（已由仓储 sanitize）。 */
    val settings: StateFlow<SelectionActionSettings>

    /**
     * 在 [target] 分组内启用 / 停用某个动作。
     *
     * 是否真的写入由仓储裁决：未知 id、固定组、会让高频槽变空或超出上限的请求都会被拒绝并保持原值。
     */
    fun setActionEnabled(id: String, enabled: Boolean, target: SelectionActionGroup)

    /** 设置浏览器查询模板；非法模板不落盘。 */
    fun setBrowserUrlTemplate(template: String)

    /** 设置在线词典查询模板；非法模板不落盘。 */
    fun setDictionaryUrlTemplate(template: String)

    /** 设置首选词典模式；不在白名单内的模式不落盘。 */
    fun setDictionaryMode(mode: String)

    /** 恢复默认（动作清单 + 两个模板 + 词典模式）。 */
    fun resetToDefaults()
}
