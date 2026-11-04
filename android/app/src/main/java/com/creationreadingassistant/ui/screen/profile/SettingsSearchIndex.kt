package com.creationreadingassistant.ui.screen.profile

/**
 * 设置项搜索索引。
 *
 * ## 为什么是「显式清单」而不是从 UI 自动派生
 *
 * 设置项分散在 17 个子页、几百行 Compose 代码里，且大量项是**条件渲染**
 * （如「滤镜色温」只在开启护眼滤镜后出现）。从 UI 结构自动派生索引，
 * 需要把每个设置行的标题在组合期上报到某个注册表，代价是给每个子页注入
 * 采集逻辑、并且只能在运行时拿到结果——无法在编译期或单测里校验。
 *
 * 因此这里维护一份**显式清单**，每项由三元组构成：
 * `page（子页）+ anchor（锚点）+ title（标题文案）`，另附可选 `group`（所在卡片）
 * 与 `keywords`（同义词 / 俗称，用于「语速」也能搜到「朗读音量」这类场景）。
 *
 * ## 防漂移：靠 [SettingsSearchIndexTest] 双向锁，而不是靠人记得改
 *
 * 显式清单最大的风险是「代码动了、清单没动」。所以索引不是自由文本，
 * 而是一份**被单测反向校验的契约**，两个方向都锁：
 *
 * 1. **清单 → 源码**：每条 `anchor` 必须真的以 `settingsAnchor("<anchor>")` 出现在
 *    该子页的源码里。有人删了或改了锚点，测试立刻红。
 * 2. **源码 → 清单**：子页源码里出现的每一个 `settingsAnchor("...")` 都必须被清单收录。
 *    有人新加了锚点却忘了补索引（用户搜不到明明存在的项），测试立刻红。
 *
 * 于是「跳得到」和「搜得到」两个方向都有编译期之外的第二道闸。
 * 代价是：新增设置项时需要在两处落笔（加锚点 + 加索引条目），
 * 但漏掉任何一处都会被测试拦下，而不是静默失效。
 *
 * ## 条件渲染项的处理
 *
 * 条件项（如「滤镜色温」依赖「夜间护眼滤镜」开启）**不挂在自己身上**——
 * 它关闭时锚点不存在，跳过去会静默失败。这类项统一挂到**必然存在的父卡片锚点**
 * （`reader_night_and_display_section`），跳过去高亮整张卡片；
 * 同时把它依赖的开关（「夜间护眼滤镜」）也单独收录，用户搜「滤镜」会同时看到两项，
 * 先开开关再用细项。滚动与高亮的语义因此永远成立，不需要运行时探测。
 */

internal enum class SettingsEntryKind {
    /** 具体设置项 / 设置卡片，带可用锚点。 */
    ITEM,

    /** 子页兜底入口（没有锚点，只导航到子页顶部）。 */
    PAGE,
}

internal data class SettingsEntry(
    /** 稳定锚点：见文件头防漂移说明，必须在对应子页源码里以 `settingsAnchor(...)` 存在。 */
    val anchor: String,
    /** 用户可见标题，直接取 UI 上的原文案。 */
    val title: String,
    val page: ProfileSubPage,
    /** 所属卡片标题（用于结果副标题「阅读设置 › 排版设置」），卡片自身为 null。 */
    val group: String? = null,
    /** 同义词 / 俗称，空格分隔。让「字体大小」也能命中「字号」。 */
    val keywords: String = "",
    val kind: SettingsEntryKind = SettingsEntryKind.ITEM,
) {
    /** 结果副标题：「阅读设置 › 排版设置」。 */
    val path: String
        get() = if (group.isNullOrBlank()) subPageTitle(page) else "${subPageTitle(page)} › $group"
}

/**
 * 子页 → 承载它的源码文件名。
 *
 * 单测据此把每条索引条目对回源码做锚点存在性校验；同时它也是这份索引的
 * 「覆盖范围声明」——返回 null 表示本页不参与设置项级搜索（只有子页兜底入口）。
 */
internal val ProfileSubPage.sourceFile: String?
    get() = when (this) {
        ProfileSubPage.READER -> "ReaderSubPage.kt"
        ProfileSubPage.APPEARANCE -> "AppearanceSubPage.kt"
        ProfileSubPage.SELECTION -> "SelectionSubPage.kt"
        ProfileSubPage.GOAL -> "GoalSubPage.kt"
        ProfileSubPage.SYNC -> "SyncSubPage.kt"
        ProfileSubPage.AI -> "AiSubPage.kt"
        ProfileSubPage.STORAGE -> "StorageSubPage.kt"
        ProfileSubPage.WEBDAV -> "WebDavSubPage.kt"
        // 其余子页（LIBRARY / TAGS / CATEGORIES / SHELVES / READING / NOTES /
        // PRIVACY / ABOUT / DIAGNOSTICS）**刻意不参与设置项级搜索**，返回 null：
        // 它们是编辑器 / 工具 / 展示页（标签与书单编辑器、阅读档案、日志、关于），
        // 没有可跳转的「设置项」；页面级兜底入口（pageEntries 对 entries 全量映射）
        // 已覆盖全部子页，搜「标签」「书单」都能直接导航到对应页面。
        // 不要因为「条目级覆盖 8/17」把它们当缺口补 —— 往编辑器行上挂锚点只会制造噪音。
        else -> null
    }

internal object SettingsSearchIndex {

    // ============================== 阅读设置 ==============================

    private val readerEntries = listOf(
        SettingsEntry("reader_typography_section", "排版设置", ProfileSubPage.READER,
            keywords = "字体 排版 文字 版面"),
        SettingsEntry("reader_font_size", "字号", ProfileSubPage.READER, "排版设置",
            keywords = "字体大小 文字大小 大小 font size"),
        SettingsEntry("reader_font_family", "正文字体", ProfileSubPage.READER, "排版设置",
            keywords = "字体 自定义字体 换字体 系统字体 字型"),
        SettingsEntry("reader_line_height", "行距", ProfileSubPage.READER, "排版设置",
            keywords = "行间距 行高"),
        SettingsEntry("reader_paragraph_spacing", "段距", ProfileSubPage.READER, "排版设置",
            keywords = "段间距 段落间距"),
        SettingsEntry("reader_page_margin", "页边距", ProfileSubPage.READER, "排版设置",
            keywords = "边距 留白"),
        SettingsEntry("reader_bold", "粗体正文", ProfileSubPage.READER, "排版设置",
            keywords = "加粗 粗体 字重"),

        SettingsEntry("reader_mode_section", "阅读模式", ProfileSubPage.READER,
            keywords = "翻页 纸张 背景 主题"),
        SettingsEntry("reader_page_turn_mode", "翻页模式", ProfileSubPage.READER, "阅读模式",
            keywords = "左右翻页 上下滚动 滚动 横滑"),
        SettingsEntry("reader_page_turn_effect", "翻页动画", ProfileSubPage.READER, "阅读模式",
            keywords = "仿真 覆盖 滑动 淡入 动画"),
        SettingsEntry("reader_paper_follow", "跟随应用外观纸张", ProfileSubPage.READER, "阅读模式",
            keywords = "纸张 背景 跟随 夜间纸"),
        SettingsEntry("reader_paper_fixed", "固定阅读纸张", ProfileSubPage.READER, "阅读模式",
            keywords = "纸张 背景 护眼纸 固定"),

        SettingsEntry("reader_gesture_section", "翻页与手势", ProfileSubPage.READER,
            keywords = "手势 点击 触控 翻页"),
        SettingsEntry("reader_tap_zone", "触控区域划分", ProfileSubPage.READER, "翻页与手势",
            keywords = "三区 五区 点击区域 分区"),
        SettingsEntry("reader_volume_key", "音量键翻页", ProfileSubPage.READER, "翻页与手势",
            keywords = "音量键 按键 侧键"),
        SettingsEntry("reader_auto_page_speed", "自动翻页速度", ProfileSubPage.READER, "翻页与手势",
            keywords = "自动翻页 滚屏 速度"),

        SettingsEntry("reader_night_and_display_section", "夜读沉浸与定时", ProfileSubPage.READER,
            keywords = "护眼 夜间 夜读 定时 亮度 沉浸"),
        SettingsEntry("reader_brightness", "阅读亮度", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "亮度 跟随系统亮度 屏幕亮度"),
        SettingsEntry("reader_immersive", "全屏沉浸模式", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "沉浸 全屏 状态栏 隐藏"),
        SettingsEntry("reader_info", "安静阅读信息", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页眉 页脚 信息"),
        SettingsEntry("reader_battery_style", "电量显示方式", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "电量 电池 内显 图标 百分比"),
        SettingsEntry("reader_header_left", "页眉左侧", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页眉 页脚 自定义"),
        SettingsEntry("reader_header_center", "页眉中间", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页眉 居中 中间 自定义"),
        SettingsEntry("reader_header_right", "页眉右侧", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页眉 页脚 自定义"),
        SettingsEntry("reader_footer_left", "页脚左侧", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页脚 页眉 自定义"),
        SettingsEntry("reader_footer_center", "页脚中间", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页脚 居中 中间 自定义"),
        SettingsEntry("reader_footer_right", "页脚右侧", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "页脚 页眉 自定义"),
        SettingsEntry("reader_auto_hide", "菜单自动隐藏", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "自动隐藏 菜单 常驻"),
        SettingsEntry("reader_keep_awake", "屏幕常亮", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "常亮 不息屏 熄屏"),
        SettingsEntry("reader_progress_bar", "显示进度条", ProfileSubPage.READER, "夜读沉浸与定时",
            keywords = "进度条 进度"),

        SettingsEntry("reader_eye_care_section", "护眼滤镜", ProfileSubPage.READER,
            keywords = "护眼 滤镜 夜读 休息 定时 节奏 提醒 蓝光"),
        SettingsEntry("reader_eye_care_filter", "夜间护眼滤镜", ProfileSubPage.READER, "护眼滤镜",
            keywords = "护眼 滤镜 夜间 蓝光"),
        SettingsEntry("reader_eye_care_schedule", "每日定时自动开启", ProfileSubPage.READER, "护眼滤镜",
            keywords = "定时 护眼 自动开启 时间段"),
        // 以下 4 项受条件渲染（依赖护眼滤镜开关），锚点挂在必然存在的父卡片上：
        // 关着的时候跳过去高亮整张「护眼滤镜」卡片，用户能直接看到要开哪个开关。
        SettingsEntry("reader_eye_care_section", "滤镜色温", ProfileSubPage.READER, "护眼滤镜",
            keywords = "色温 护眼 滤镜 暖光"),
        SettingsEntry("reader_eye_care_section", "滤镜暖度强度", ProfileSubPage.READER, "护眼滤镜",
            keywords = "暖度 强度 护眼 滤镜"),
        SettingsEntry("reader_eye_care_section", "色卡预览同步滤镜", ProfileSubPage.READER, "护眼滤镜",
            keywords = "色卡 预览 护眼 滤镜"),
        SettingsEntry("reader_eye_care_section", "OLED 纯黑兼容", ProfileSubPage.READER, "护眼滤镜",
            keywords = "oled 纯黑 护眼 滤镜"),
        SettingsEntry("reader_eye_care_reminder", "护眼休息提醒", ProfileSubPage.READER, "护眼滤镜",
            keywords = "休息提醒 护眼 用眼"),
        SettingsEntry("reader_rhythm_reminder", "阅读节奏轻触提醒", ProfileSubPage.READER, "护眼滤镜",
            keywords = "节奏 触感 震动 提醒"),

        SettingsEntry("reader_engine_section", "引擎与排版兼容", ProfileSubPage.READER,
            keywords = "引擎 排版 兼容 分页"),
        SettingsEntry("reader_txt_pager", "TXT 分页引擎", ProfileSubPage.READER, "引擎与排版兼容",
            keywords = "txt 分页 引擎 纯文本"),
        SettingsEntry("reader_epub_pager", "EPUB 分页引擎", ProfileSubPage.READER, "引擎与排版兼容",
            keywords = "epub 分页 引擎"),
        SettingsEntry("reader_chinese_typography", "中文标点排版优化", ProfileSubPage.READER, "引擎与排版兼容",
            keywords = "标点 孤行 避头尾 中文排版"),
        SettingsEntry("reader_traditional_chinese", "繁体中文显示", ProfileSubPage.READER, "引擎与排版兼容",
            keywords = "繁体 简繁 转换"),

        SettingsEntry("reader_tts_section", "听书与朗读", ProfileSubPage.READER,
            keywords = "tts 朗读 听书 语音 播报"),
        SettingsEntry("reader_tts_engine", "朗读引擎", ProfileSubPage.READER, "听书与朗读",
            keywords = "tts 引擎 edge 系统朗读"),
        SettingsEntry("reader_tts_speed", "语速", ProfileSubPage.READER, "听书与朗读",
            keywords = "语速 倍速 朗读速度 快慢 speech rate 1.5倍 2倍"),
        SettingsEntry("reader_tts_pitch", "音调 (Pitch)", ProfileSubPage.READER, "听书与朗读",
            keywords = "音高 音调 语调 pitch 声音高低"),
        SettingsEntry("reader_tts_volume", "朗读音量", ProfileSubPage.READER, "听书与朗读",
            keywords = "音量 声音 大小声"),
        SettingsEntry("reader_tts_timer", "定时停止", ProfileSubPage.READER, "听书与朗读",
            keywords = "定时 停止 睡觉 关闭朗读"),
        SettingsEntry("reader_tts_voice", "音色", ProfileSubPage.READER, "听书与朗读",
            keywords = "音色 发音人 声音 语音包"),

        SettingsEntry("reader_reset_section", "重置偏好", ProfileSubPage.READER,
            keywords = "恢复默认 重置 还原 初始化"),
    )

    // ============================== 应用外观 ==============================

    private val appearanceEntries = listOf(
        SettingsEntry("appearance_mode_section", "外观模式", ProfileSubPage.APPEARANCE,
            keywords = "外观 主题 深色 夜间模式 浅色"),
        SettingsEntry("appearance_amoled", "AMOLED 纯黑", ProfileSubPage.APPEARANCE, "外观模式",
            keywords = "amoled 纯黑 省电 oled 黑色"),
        SettingsEntry("appearance_paper_texture", "纸张质感底纹", ProfileSubPage.APPEARANCE, "外观模式",
            keywords = "纸纹 质感 底纹 素笺"),
        SettingsEntry("appearance_duration_day", "时长按「天」显示", ProfileSubPage.APPEARANCE, "外观模式",
            keywords = "时长 单位 天 小时 换算"),
        SettingsEntry("appearance_palette_section", "配色主题", ProfileSubPage.APPEARANCE,
            keywords = "配色 主题 颜色 取色"),
        SettingsEntry("appearance_wallpaper_color", "跟随系统壁纸取色", ProfileSubPage.APPEARANCE, "配色主题",
            keywords = "壁纸 取色 动态配色 material you"),
        SettingsEntry("appearance_reader_paper_section", "阅读纸张", ProfileSubPage.APPEARANCE,
            keywords = "纸张 护眼纸 暖纸 夜读 白纸 墨青 阅读背景 阅读器颜色"),
    )

    // ============================== 选区与查词 ==============================

    private val selectionEntries = listOf(
        SettingsEntry("selection_primary_section", "常用动作（第一屏）", ProfileSubPage.SELECTION,
            keywords = "选区 工具条 动作 长按 复制"),
        SettingsEntry("selection_more_section", "「更多」菜单", ProfileSubPage.SELECTION,
            keywords = "更多 菜单 动作 排序"),
        SettingsEntry("selection_query_section", "搜索与查词", ProfileSubPage.SELECTION,
            keywords = "查词 搜索 词典 翻译"),
        SettingsEntry("selection_browser_url", "浏览器搜索地址", ProfileSubPage.SELECTION, "搜索与查词",
            keywords = "浏览器 搜索引擎 地址 网址"),
        SettingsEntry("selection_dict_mode", "首选词典", ProfileSubPage.SELECTION, "搜索与查词",
            keywords = "词典 离线词库 系统词典 在线词典"),
        SettingsEntry("selection_dict_url", "在线词典地址", ProfileSubPage.SELECTION, "搜索与查词",
            keywords = "在线词典 地址 网址"),
        SettingsEntry("selection_dictionary_section", "离线词库", ProfileSubPage.SELECTION,
            keywords = "词库 离线 stardict 导入 卸载 字典"),
        SettingsEntry("selection_reset_section", "重置", ProfileSubPage.SELECTION,
            keywords = "恢复默认 重置 还原"),
    )

    // ============================== 阅读目标 ==============================

    private val goalEntries = listOf(
        SettingsEntry("goal-hero-card", "每日阅读目标", ProfileSubPage.GOAL,
            keywords = "目标 每日 每天 分钟 时长 计划"),
        SettingsEntry("goal-reminder-card", "每日提醒", ProfileSubPage.GOAL,
            keywords = "提醒 通知 时间 闹钟"),
        SettingsEntry("goal-streak-card", "连续打卡", ProfileSubPage.GOAL,
            keywords = "连续 打卡 streak 记录 坚持"),
    )

    // ============================== 同步 ==============================

    private val syncEntries = listOf(
        SettingsEntry("sync-overview-card", "同步状态", ProfileSubPage.SYNC,
            keywords = "同步 配对 连接 电脑 扫码"),
        SettingsEntry("sync-items-card", "数据同步项", ProfileSubPage.SYNC,
            keywords = "同步 开关 项目 同步什么"),
        SettingsEntry("sync_books", "书架与书籍元数据", ProfileSubPage.SYNC, "数据同步项",
            keywords = "同步书籍 书架 封面 元数据"),
        SettingsEntry("sync_progress", "阅读进度与阅读历史", ProfileSubPage.SYNC, "数据同步项",
            keywords = "进度 同步 历史 续读"),
        SettingsEntry("sync_inspirations", "灵感火花与随笔批注", ProfileSubPage.SYNC, "数据同步项",
            keywords = "灵感 批注 高亮 同步"),
        SettingsEntry("sync-conflict-card", "冲突解决策略", ProfileSubPage.SYNC,
            keywords = "冲突 合并 策略 以谁为准"),
        SettingsEntry("sync-logs-card", "同步事件与详细日志", ProfileSubPage.SYNC,
            keywords = "日志 事件 记录 失败"),
    )

    // ============================== AI 助手 ==============================

    private val aiEntries = listOf(
        SettingsEntry("ai-provider-card", "AI 服务商", ProfileSubPage.AI,
            keywords = "ai 模型 服务商 接口 地址 模型名"),
        SettingsEntry("ai-key-params-card", "参数微调与安全密钥", ProfileSubPage.AI,
            keywords = "api key 密钥 温度 参数 token"),
        SettingsEntry("ai-prompt-card", "创作偏好与人设指令", ProfileSubPage.AI,
            keywords = "prompt 提示词 人设 偏好 指令"),
        SettingsEntry("ai-actions-card", "连接测试与操作", ProfileSubPage.AI,
            keywords = "测试 连接 清空 key"),
    )

    // ============================== 存储管理 ==============================

    private val storageEntries = listOf(
        SettingsEntry("storage-metrics-matrix", "存储占用与资源分布", ProfileSubPage.STORAGE,
            keywords = "存储 占用 空间 容量 大小"),
        SettingsEntry("storage-clean-cache-card", "清理缓存", ProfileSubPage.STORAGE,
            keywords = "缓存 清理 释放 空间"),
        SettingsEntry("storage-search-index-card", "搜索索引", ProfileSubPage.STORAGE,
            keywords = "索引 搜索 重建 全文"),
        SettingsEntry("storage-danger-zone-card", "危险操作", ProfileSubPage.STORAGE,
            keywords = "清空 删除 危险 离线书籍"),
        SettingsEntry("storage-json-card", "数据快照 (JSON)", ProfileSubPage.STORAGE,
            keywords = "导出 导入 备份 json 快照"),
        SettingsEntry("storage-zip-card", "整包备份 (ZIP)", ProfileSubPage.STORAGE,
            keywords = "备份 zip 整包 恢复"),
    )

    // ============================== WebDAV ==============================

    private val webDavEntries = listOf(
        SettingsEntry("webdav-config-card", "WebDAV 配置", ProfileSubPage.WEBDAV,
            keywords = "webdav 服务器 地址 账号 密码"),
        SettingsEntry("webdav-backups-card", "云端备份", ProfileSubPage.WEBDAV,
            keywords = "备份 恢复 上传 下载 云端"),
        SettingsEntry("legado-progress-import-card", "导入开源阅读记录", ProfileSubPage.WEBDAV,
            keywords = "legado 开源阅读 导入 阅读位置"),
    )

    // ============================== 子页兜底入口 ==============================

    /**
     * 子页级结果：搜「同步」能直接出「同步状态」，即使没命中任何具体项。
     * 标题与 eyebrow 一律复用 [subPageTitle] / [subPageEyebrow]，不另造映射。
     */
    private val pageEntries: List<SettingsEntry> = ProfileSubPage.entries.map { page ->
        SettingsEntry(
            anchor = "",
            title = subPageTitle(page),
            page = page,
            group = subPageEyebrow(page),
            keywords = "",
            kind = SettingsEntryKind.PAGE,
        )
    }

    val entries: List<SettingsEntry> =
        readerEntries + appearanceEntries + selectionEntries + goalEntries +
            syncEntries + aiEntries + storageEntries + webDavEntries + pageEntries

    /** 有锚点的条目（ITEM）：单测据此反向校验源码里的锚点是否都被收录。 */
    val anchoredEntries: List<SettingsEntry> = entries.filter { it.kind == SettingsEntryKind.ITEM }

    private const val SCORE_EXACT = 0
    private const val SCORE_TITLE_PREFIX = 1
    private const val SCORE_TITLE_CONTAINS = 2
    private const val SCORE_KEYWORD = 3
    private const val SCORE_PINYIN = 3  // 拼音首字母与关键词同优先级
    private const val SCORE_GROUP = 4
    private const val SCORE_PAGE_NAME = 5

    /**
     * 搜索设置项。空查询返回空列表（调用方据此回到普通菜单视图）。
     *
     * 中文没有空格分词，一律用子串匹配：`title` 包含查询词即命中，
     * 再退到 `keywords` / `group` / 子页名。排序按命中强度，不按字母。
     * 同时支持拼音首字母缩写检索（如 `ydsl` 命中「阅读设置」），
     * 复用书架已有的 [extractPinyinInitials] 纯原生算法。
     */
    fun search(query: String, limit: Int = 12): List<SettingsEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val lowered = q.lowercase()

        return entries
            .mapNotNull { entry -> score(entry, lowered)?.let { it to entry } }
            // 同分时具体项优先于子页兜底，再按标题短的优先（「字号」应压过「排版设置」）
            .sortedWith(
                compareBy<Pair<Int, SettingsEntry>> { it.first }
                    .thenBy { if (it.second.kind == SettingsEntryKind.PAGE) 1 else 0 }
                    .thenBy { it.second.title.length }
                    .thenBy { it.second.title },
            )
            .take(limit)
            .map { it.second }
    }

    private fun score(entry: SettingsEntry, lowered: String): Int? {
        val title = entry.title.lowercase()
        if (title == lowered) return SCORE_EXACT
        if (title.startsWith(lowered)) return SCORE_TITLE_PREFIX
        if (lowered in title) return SCORE_TITLE_CONTAINS
        if (entry.keywords.lowercase().split(' ').any { it.isNotBlank() && lowered in it }) {
            return SCORE_KEYWORD
        }
        // 拼音首字母匹配：「ydsl」命中「阅读设置」，「hjlj」命中「护眼滤镜」
        // 只在全字母查询时启用（避免中文搜索词误触发），且必须完整前缀匹配
        if (lowered.all { it in 'a'..'z' } && lowered.length >= 2) {
            val titleInitials = com.creationreadingassistant.ui.screen.shelf.extractPinyinInitials(entry.title)
            if (titleInitials.startsWith(lowered)) return SCORE_PINYIN
            // 关键词中每个词条的拼音首字母前缀匹配
            entry.keywords.split(' ').forEach { keyword ->
                if (keyword.isNotBlank()) {
                    val kwInitials = com.creationreadingassistant.ui.screen.shelf.extractPinyinInitials(keyword)
                    if (kwInitials.isNotEmpty() && kwInitials.startsWith(lowered)) return SCORE_PINYIN
                }
            }
        }
        entry.group?.let { if (lowered in it.lowercase()) return SCORE_GROUP }
        if (entry.kind == SettingsEntryKind.PAGE) {
            val pageTitle = subPageTitle(entry.page).lowercase()
            val eyebrow = subPageEyebrow(entry.page).lowercase()
            if (lowered in pageTitle || lowered in eyebrow) return SCORE_PAGE_NAME
            // 子页兜底：命中子页英文名（如搜 "webdav" / "ai"）
            if (lowered in entry.page.name.lowercase()) return SCORE_PAGE_NAME
        }
        return null
    }
}
