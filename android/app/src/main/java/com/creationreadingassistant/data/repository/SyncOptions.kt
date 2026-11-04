package com.creationreadingassistant.data.repository

/**
 * 冲突裁决策略（「我的 · 同步」子页的两个单选项）。
 *
 * 只在 pull 合并、且**远端与本地 revision 相等**（双方离线各改过同一条）时生效：
 * revision 不等时永远由 revision 高者胜，与策略无关。
 *
 * push 侧的冲突由服务端裁决（固定「服务端保留」），客户端策略不改变 push 结果，
 * 因此这里只约束 pull 的 [SyncRepository] 合并分支。
 */
enum class ConflictStrategy(val wire: String) {
    /** 以远端（电脑）为准：等 revision 时无条件接受远端。 */
    REMOTE_WINS("remote"),

    /** 以本地（手机）为准：等 revision 时无条件保留本地。 */
    LOCAL_WINS("local"),
    ;

    companion object {
        /**
         * UI 默认选中「以远端为准」：与服务端 push 冲突「服务端保留」同向，
         * 也与子页未操作时展示的胶囊文案一致（绝不出现「显示远端、实际较新者胜」的错位）。
         */
        val DEFAULT: ConflictStrategy = REMOTE_WINS

        /** 落盘字符串 → 策略；未知/缺失一律回退 [DEFAULT]，绝不把坏值当成有效策略。 */
        fun fromWire(value: String?): ConflictStrategy =
            entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/**
 * 一次同步运行的用户可配置项：3 个数据同步项开关 + 冲突裁决策略。
 *
 * **默认值 = 历史行为**（全类别同步、等 revision 时委托 [com.creationreadingassistant.feature.sync.SyncMergePolicy]
 * 走「较新者胜」），因此 [SyncRepository.pull] / [SyncRepository.push] 的无参调用表现完全不变。
 * 只有「我的 · 同步」子页经 ViewModel 显式构造并传入时，开关与策略才生效。
 */
data class SyncOptions(
    val syncBooks: Boolean = true,
    val syncProgress: Boolean = true,
    val syncInspirations: Boolean = true,
    /**
     * `null` = 历史「较新者胜」（委托 SyncMergePolicy，与本地 JSON / WebDAV 恢复路径共用同一纯函数）；
     * 非 null = 用户在子页显式选择的策略。刻意用可空而不是三值枚举：
     * 「较新者胜」不是 UI 选项，只是无参 API 的向后兼容默认。
     */
    val conflictStrategy: ConflictStrategy? = null,
)
