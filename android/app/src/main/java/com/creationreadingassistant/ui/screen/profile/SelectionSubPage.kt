package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.SelectionActionDef
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SettingSegmentedRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/** 首选词典的可读标签；未知模式回退到离线（与 sanitize 口径一致）。 */
private fun dictionaryModeLabel(mode: String): String = when (mode) {
    SelectionActions.MODE_SYSTEM -> "系统词典"
    SelectionActions.MODE_ONLINE -> "在线词典"
    else -> "内置离线词库"
}

/**
 * 选区与查词子页（R3-X1）。
 *
 * 三件事收在一页：
 * 1. **选区工具条内容与顺序** —— 高频槽（≤3）与「更多」菜单由用户决定，「取消选择」不可隐藏；
 * 2. **查询目标** —— 浏览器 / 在线词典的 URL 模板，以及首选词典三种模式；
 * 3. **离线词库** —— StarDict 词库的导入与卸载，导入后断网也能查。
 *
 * 刻意不做「拖拽排序」：动作按定义顺序渲染，用户可以决定**放哪些**，顺序稳定可预期。
 * 一个会随手拖动排序的列表，用户下次打开就找不到自己的动作在哪了。
 */
@Composable
internal fun SelectionSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val layout = LocalLayoutTokens.current
    val settings = state.selectionActions

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 常用动作（工具条第一屏）
        item(key = "selection_primary_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "常用动作（第一屏）",
                        icon = Icons.Outlined.Tune,
                        iconTint = Color(0xFF6750A4),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    Text(
                        text = "选中文字后直接铺在工具条上的动作，最多 ${SelectionActions.MAX_PRIMARY_SLOTS} 个、" +
                            "至少保留 ${SelectionActions.MIN_PRIMARY_SLOTS} 个。" +
                            "想换「搜索提供方」，把「书内搜索」或「浏览器」放进这一排即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    ActionPillGroup(
                        defs = SelectionActions.pickableDefs(),
                        selectedIds = settings.enabledPrimary,
                        // 已达上限时不能继续加；已选中的永远可取消
                        canToggle = { id ->
                            id in settings.enabledPrimary ||
                                settings.enabledPrimary.size < SelectionActions.MAX_PRIMARY_SLOTS
                        },
                        onToggle = { id, enabled -> onAction(ProfileAction.TogglePrimarySelectionAction(id, enabled)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    Text(
                        text = "当前第一屏：" + SelectionActions.effectivePrimary(settings)
                            .joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // 2. 「更多」菜单
        item(key = "selection_more_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "「更多」菜单",
                        icon = Icons.Outlined.MoreHoriz,
                        iconTint = Color(0xFF00639B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    Text(
                        text = "收在「更多」里的动作。「取消选择」固定保留、不可隐藏，否则收不起选区。" +
                            "已经放进第一屏的动作不会在这里重复出现。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    ActionPillGroup(
                        defs = SelectionActions.pickableDefs().filterNot { it.id in settings.enabledPrimary },
                        selectedIds = settings.enabledMore,
                        canToggle = { true },
                        onToggle = { id, enabled -> onAction(ProfileAction.ToggleMoreSelectionAction(id, enabled)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // 3. 搜索与查词
        item(key = "selection_query_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubPageSectionTitle(
                        title = "搜索与查词",
                        icon = Icons.Outlined.Language,
                        iconTint = Color(0xFF00897B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    UrlTemplateRow(
                        title = "浏览器搜索地址",
                        subtitle = "用 {q} 代表查词内容，必须换成你自己的搜索引擎地址",
                        value = settings.browserUrlTemplate,
                        icon = Icons.Outlined.Search,
                        onCommit = { onAction(ProfileAction.SetBrowserUrlTemplate(it)) },
                    )

                    SectionDivider()

                    SettingSegmentedRow(
                        title = "首选词典",
                        options = SelectionActions.MODES.map { it to dictionaryModeLabel(it) },
                        selected = settings.dictionaryMode,
                        subtitle = "内置离线词库断网可用；系统词典交给已安装的词典应用；在线词典走下面的网址",
                        onSelect = { onAction(ProfileAction.SetDictionaryMode(it)) },
                    )

                    if (settings.dictionaryMode == SelectionActions.MODE_OFFLINE && state.dictionaries.isEmpty()) {
                        Text(
                            text = "还没有离线词库，选区菜单点「字典」会提示你先导入词库。" +
                                "导入 StarDict 词库后即可断网查词。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }

                    SectionDivider()

                    UrlTemplateRow(
                        title = "在线词典地址",
                        subtitle = "用 {q} 代表查词内容；只在首选词典选「在线词典」时使用",
                        value = settings.dictionaryUrlTemplate,
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        onCommit = { onAction(ProfileAction.SetDictionaryUrlTemplate(it)) },
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // 4. 离线词库
        item(key = "selection_dictionary_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
                ) {
                    SubPageSectionTitle(
                        title = "离线词库",
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        iconTint = Color(0xFF6A1B9A),
                    )

                    Text(
                        text = "词库只存在应用私有目录，不联网、不上传。压缩包里每个词库需要同名三件套" +
                            "（.ifo + .idx + .dict，或 .dict.dz）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )

                    if (state.dictionaries.isEmpty()) {
                        Text(
                            text = "暂无已安装词库。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.dictionaries.forEach { dict ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dict.bookName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${dict.wordCount} 词条",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onAction(ProfileAction.UninstallDictionary(dict.baseName)) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "卸载",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onAction(ProfileAction.ImportDictionary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "导入词库（.zip）",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        text = "导入大词库会在后台解压，完成后会自动重查当前查询词。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 5. 恢复默认
        item(key = "selection_reset_section") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
                ) {
                    SubPageSectionTitle(
                        title = "重置",
                        icon = Icons.Outlined.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.error,
                    )

                    Text(
                        text = "只恢复选区动作清单、查询地址与首选词典；不会删除已导入的离线词库，" +
                            "也不影响阅读进度与笔记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )

                    OutlinedButton(
                        onClick = { onAction(ProfileAction.ResetSelectionActions) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = "恢复选区默认设置",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 动作胶囊组。
 *
 * [canToggle] 决定某个 id 此刻能否被改动；返回 false 的胶囊做 50% 变淡并吞掉点击 ——
 * 与其让用户点了没反应，不如让「已经满了」这件事直接看得见。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPillGroup(
    defs: List<SelectionActionDef>,
    selectedIds: Set<String>,
    canToggle: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        defs.forEach { def ->
            val selected = def.id in selectedIds
            val enabled = canToggle(def.id)
            SelectablePill(
                text = def.label,
                selected = selected,
                onClick = { if (enabled) onToggle(def.id, !selected) },
                modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
            )
        }
    }
}

/**
 * URL 模板输入行。
 *
 * 用**本地草稿**而不是「每敲一个字就写盘」：模板随时可以是中间态（还没敲完 `{q}`），
 * 直接落盘会被 store 拒绝，输入框又跟着被拒的值回滚，用户会看到自己打的字被吞掉。
 * 因此这里：草稿本地维护 → 「保存」按钮只在模板合法时可点 → 提交后才写盘。
 */
@Composable
private fun UrlTemplateRow(
    title: String,
    subtitle: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCommit: (String) -> Unit,
) {
    // key = value：外部值变了（重置 / 换设备恢复）时把草稿同步过来
    var draft by remember(value) { mutableStateOf(value) }
    val trimmed = draft.trim()
    val valid = SelectionActions.isValidTemplate(trimmed)
    val dirty = trimmed != value

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors(),
            isError = !valid,
            supportingText = {
                if (!valid) {
                    Text("地址必须以 http 开头，并且包含 {q} 占位符", color = MaterialTheme.colorScheme.error)
                }
            },
        )
        TextButton(
            onClick = { onCommit(trimmed) },
            enabled = valid && dirty,
        ) {
            Text("保存")
        }
    }
}
