package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.PerBookOverrides
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.ReaderSettingsScope
import com.creationreadingassistant.data.settings.ReadingPreset
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 阅读设置**页级作用域说明**（D-2-B，产品裁决：页级规则句）。
 *
 * 为什么需要它：设置页里有两类字段混在一起 ——
 * - **本书可覆盖**（[ReaderOverrideKey] 的成员，D-2-A 后含两个分页引擎键）；
 * - **全局字段**（设备 / 交互类：翻页速度、点击区域、音量键、亮度、护眼、提醒……共 26 项），
 *   它们本来就应该跨书共用。
 *
 * 用户在「只改本书」作用域下改后一类时，界面此前没有任何说明（真机验收 P3-1）。
 * 这里用**一条页级说明**告知影响范围，不改任何字段的存储位置与生效语义。
 *
 * 表述方式（关键设计属性）：BOOK 作用域按**例外清单**表述 ——
 * 「除 X、Y、Z 外，本页其余设置都是全局」。因此**新增一个全局字段时这句话自动仍然正确**；
 * 只有新增**本书可覆盖**字段时才必须更新句子（守卫测试盯这个方向）。
 *
 * GLOBAL 作用域统一说明「保存为全局默认」，并明确**不会清除其他书已有的单书设置** ——
 * 修改全局默认并不会抹掉别人的 per-book 覆盖，旧说法「影响所有书」是不准确的承诺。
 *
 * ROOT 返回 null：根页顶部已有「只改本书 / 改全局默认」胶囊，再叠一条是重复噪音。
 *
 * 纯函数：不依赖 Compose 状态、不读 DataStore、不修改任何设置。
 * `when` **穷尽** [ReaderSettingsPage] 且不带 `else` —— 新增页面时编译失败，迫使维护者处理。
 */
internal fun readerSettingsScopeNotice(
    page: ReaderSettingsPage,
    scope: ReaderSettingsScope,
): String? = when (scope) {
    ReaderSettingsScope.BOOK -> when (page) {
        ReaderSettingsPage.ROOT -> null
        // 排版页 6 项全部可覆盖；高级兼容页是 D-2-A 的两个引擎键 → 都是真·本书设置。
        ReaderSettingsPage.TYPOGRAPHY,
        ReaderSettingsPage.ADVANCED,
        -> "本页设置只对当前书籍生效"

        // 例外清单必须与该页实际可覆盖字段一致（守卫测试双向对齐）。
        ReaderSettingsPage.PAGING ->
            "除阅读模式、翻页效果和屏幕方向外，本页其他设置均为全局设置，对所有书籍生效"

        ReaderSettingsPage.DISPLAY ->
            "除沉浸模式、显示进度条、自动隐藏和显示安静阅读信息外，本页其他设置均为全局设置，对所有书籍生效"

        // 护眼页 10 项全部是全局字段，没有例外。
        ReaderSettingsPage.EYE_CARE ->
            "本页均为全局设置，对所有书籍生效"
    }

    ReaderSettingsScope.GLOBAL -> when (page) {
        ReaderSettingsPage.ROOT -> null
        ReaderSettingsPage.TYPOGRAPHY,
        ReaderSettingsPage.PAGING,
        ReaderSettingsPage.DISPLAY,
        ReaderSettingsPage.EYE_CARE,
        ReaderSettingsPage.ADVANCED,
        -> "本页修改将保存为全局默认；其他书已有的单书设置不会被清除"
    }
}

/**
 * 页级作用域说明的渲染（D-2-B）。
 *
 * 形态参照 `ReaderThemeSheet` 已有的 `Info + 说明行`（该文件不改动），
 * 使用当前 MaterialTheme、不使用 Markdown、不设置会覆盖可见文字的 contentDescription
 * —— 文字本身就是可见文本，TalkBack 自然朗读，无需额外语义。
 */
@Composable
private fun ScopeNoticeRow(notice: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader-settings-scope-notice")
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            // 允许换行，不强制单行省略：小屏 / 大字号下长句必须完整可读。
            text = notice,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingsSheet(
    paper: ReaderPaperPalette,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBookInfo: () -> Unit,
    /** R3-P1：修改作用范围（本书 / 全局）。 */
    scope: ReaderSettingsScope = ReaderSettingsScope.BOOK,
    onScopeChange: (ReaderSettingsScope) -> Unit = {},
    /** R3-P1：本书显式覆盖项，用于标注「哪一项来自本书」。 */
    overrides: PerBookOverrides = emptyMap(),
    /** R3-P1：全局设置（不含本书覆盖），用于展示「跟随全局」时的实际取值。 */
    globalSettings: ReaderSettings = ReaderSettings(),
    onClearOverride: (ReaderOverrideKey) -> Unit = {},
    onClearAllOverrides: () -> Unit = {},
    onApplyPreset: (ReadingPreset) -> Unit = {},
    onResetGlobalDefaults: () -> Unit = {},
) {
    var page by remember { mutableStateOf(ReaderSettingsPage.ROOT) }
    val reducedMotion = rememberReducedMotion()
    BackHandler(enabled = page != ReaderSettingsPage.ROOT) { page = ReaderSettingsPage.ROOT }

    ReaderSheetScaffold(
        title = page.title,
        onBack = if (page == ReaderSettingsPage.ROOT) null else ({ page = ReaderSettingsPage.ROOT }),
        maxHeightFraction = 0.62f,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                }
            },
            label = "reader-settings-page",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            // D-2-B：页级作用域说明位于页面内容**之前**，进页无需滚动即可看到。
            // 用 Column + 子页面 weight(1f) 分配剩余高度：说明行按内容自适应，
            // 页面正文拿到「有限且正确」的剩余高度后仍能正常滚动（LazyColumn fillMaxSize）。
            Column(modifier = Modifier.fillMaxSize()) {
                readerSettingsScopeNotice(target, scope)?.let { notice ->
                    ScopeNoticeRow(notice)
                }
                Column(modifier = Modifier.weight(1f)) {
                    when (target) {
                        ReaderSettingsPage.ROOT -> SettingsRoot(
                            paper = paper,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onNavigate = { page = it },
                            onBookInfo = onBookInfo,
                            scope = scope,
                            onScopeChange = onScopeChange,
                            overrides = overrides,
                            globalSettings = globalSettings,
                            onClearOverride = onClearOverride,
                            onClearAllOverrides = onClearAllOverrides,
                            onApplyPreset = onApplyPreset,
                            onResetGlobalDefaults = onResetGlobalDefaults,
                        )
                        ReaderSettingsPage.TYPOGRAPHY -> TypographySettings(settings, onSettingsChange, paper)
                        ReaderSettingsPage.PAGING -> PagingSettings(settings, onSettingsChange)
                        ReaderSettingsPage.DISPLAY -> DisplaySettings(settings, onSettingsChange)
                        ReaderSettingsPage.EYE_CARE -> EyeCareSettings(settings, onSettingsChange)
                        ReaderSettingsPage.ADVANCED -> AdvancedSettings(settings, onSettingsChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    paper: ReaderPaperPalette,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onNavigate: (ReaderSettingsPage) -> Unit,
    onBookInfo: () -> Unit,
    scope: ReaderSettingsScope,
    onScopeChange: (ReaderSettingsScope) -> Unit,
    overrides: PerBookOverrides,
    globalSettings: ReaderSettings,
    onClearOverride: (ReaderOverrideKey) -> Unit,
    onClearAllOverrides: () -> Unit,
    onApplyPreset: (ReadingPreset) -> Unit,
    onResetGlobalDefaults: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    val spec = LocalComponentSpec.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 0. R3-P1 作用范围：书内调整默认只改当前这本书，可显式切换到全局
        item(key = "settings-scope") {
            SettingsScopeSection(scope = scope, onScopeChange = onScopeChange)
        }

        // 0b. R3-P1 本书覆盖明细 + 预设 + 恢复默认（紧凑折叠）
        item(key = "book-overrides") {
            BookOverrideSection(
                scope = scope,
                overrides = overrides,
                settings = settings,
                globalSettings = globalSettings,
                onClearOverride = onClearOverride,
                onClearAllOverrides = onClearAllOverrides,
                onApplyPreset = onApplyPreset,
                onResetGlobalDefaults = onResetGlobalDefaults,
            )
        }
        // 1. 快捷排版微岛：字号与加粗、行距微导轨、翻页效果微胶囊单选导轨
        item(key = "quick-typography") {
            MicroSettingsSection(
                icon = Icons.Outlined.FormatSize,
                title = "快捷排版",
                tint = MaterialTheme.colorScheme.primary,
            ) {
                QuickFontSizeRow(
                    fontSize = settings.fontSize,
                    isBold = settings.fontWeightBold,
                    onFontSizeChange = { onSettingsChange(settings.copy(fontSize = it)) },
                    onBoldChange = { onSettingsChange(settings.copy(fontWeightBold = it)) },
                    isOverridden = overrides.containsKey(ReaderOverrideKey.FONT_SIZE),
                )
                MicroSectionDivider()
                MicroOptionPillRow(
                    title = "行距比例",
                    icon = Icons.Outlined.FormatLineSpacing,
                    options = listOf(1.5f to "紧凑", 1.85f to "标准", 2.1f to "宽松"),
                    selected = nearest(settings.lineHeight, listOf(1.5f, 1.85f, 2.1f)),
                    onSelect = { onSettingsChange(settings.copy(lineHeight = it)) },
                    tint = Color(0xFF2563EB),
                    subtitle = if (overrides.containsKey(ReaderOverrideKey.LINE_HEIGHT)) "本书" else null,
                )
                MicroSectionDivider()
                MicroOptionPillRow(
                    title = "翻页效果",
                    icon = Icons.Outlined.Animation,
                    options = listOf("none" to "无", "fade" to "淡入", "slide" to "平移", "cover" to "仿真", "reveal" to "揭示"),
                    selected = settings.pageTurnEffect,
                    onSelect = {
                        onSettingsChange(settings.copy(pageTurnEffect = it))
                    },
                    tint = Color(0xFF059669),
                    subtitle = if (overrides.containsKey(ReaderOverrideKey.PAGE_TURN_EFFECT)) "本书" else null,
                )
                if (settings.pageTurnEffect in listOf("slide", "cover", "reveal")) {
                    Text(
                        text = "💡 复杂动画每帧需GPU合成多张位图，如遇掉帧或发热建议切回「无」或「淡入」。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp),
                    )
                }
                MicroSectionDivider()
                // 翻页速度：绑定 ReaderSettings.pageTurnSpeed，经 PagedReaderPageSurface 透传给
                // PageTurner(speed = ...)。它是单页满位移的基础时长（实际时长 = speed × 剩余
                // 位移比例），值越小翻页越快，故左侧标「最快」、右侧标「最慢」。
                MicroTrackSliderRow(
                    icon = Icons.Outlined.Speed,
                    title = "翻页速度",
                    value = settings.pageTurnSpeed,
                    valueLabel = "${settings.pageTurnSpeed.toInt()} ms",
                    onValueChange = { onSettingsChange(settings.copy(pageTurnSpeed = it)) },
                    valueRange = 150f..800f,
                    steps = 12,
                    minLabel = "最快",
                    maxLabel = "最慢",
                    tint = Color(0xFF059669),
                    modifier = Modifier.testTag("reader-page-turn-speed"),
                )
                // D-2-B：翻页速度是**全局**字段（不在 ReaderOverrideKey 里）。
                // 它位于「只改本书」作用域胶囊下方，容易被误认为只影响当前书 ——
                // 根页虽然已有作用域胶囊，也**不能**省掉这一行的明确说明。
                Text(
                    text = "全局设置，所有书籍共用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp)
                        .testTag("reader-page-turn-speed-global-note"),
                )
            }
        }

        // 2. 高级与偏好设置导航微岛群
        item(key = "groups") {
            MicroSettingsSection(
                icon = Icons.Outlined.Tune,
                title = "高级与偏好设置",
                tint = Color(0xFF7C3AED),
            ) {
                val entries = listOf(
                    Triple(ReaderSettingsPage.TYPOGRAPHY, "排版与版式", "字号滑块、段距、页边距与字体"),
                    Triple(ReaderSettingsPage.PAGING, "翻页与按键", "点击区域、屏幕方向与自动翻页"),
                    Triple(ReaderSettingsPage.DISPLAY, "显示与页眉页脚", "沉浸模式、页眉页脚与菜单显示"),
                    Triple(ReaderSettingsPage.EYE_CARE, "护眼与阅读提醒", "护眼滤镜、色温调节与阅读节奏"),
                    Triple(ReaderSettingsPage.ADVANCED, "排版引擎兼容", "自研分页引擎模式与中文排版"),
                )
                val icons = listOf(
                    Icons.Outlined.TextFields to Color(0xFF2563EB),
                    Icons.Outlined.TouchApp to Color(0xFF059669),
                    Icons.Outlined.Devices to Color(0xFFD97706),
                    Icons.Outlined.Visibility to Color(0xFF7C3AED),
                    Icons.Outlined.Tune to Color(0xFF0891B2),
                )
                entries.forEachIndexed { index, (target, title, subtitle) ->
                    val (icon, tint) = icons[index]
                    MicroSettingsLinkRow(
                        icon = icon,
                        tint = tint,
                        title = title,
                        subtitle = subtitle,
                        onClick = { onNavigate(target) },
                    )
                    if (index != entries.lastIndex) {
                        MicroSectionDivider()
                    }
                }
            }
        }

        // 3. 底部“查看书籍信息”微岛操作保持优雅过渡
        item(key = "book-info") {
            val bookInfoInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onBookInfo()
                },
                shape = RoundedCornerShape(spec.islandRadius),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(spec.borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceable(bookInfoInteraction),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconPedestal(
                            icon = Icons.Outlined.Info,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 28.dp,
                            iconSize = 16.dp,
                        )
                        Column {
                            Text(
                                "查看书籍信息",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "篇幅字数、已读进度与阅读时段统计",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "进入书籍信息",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
