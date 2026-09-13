package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ===================== 批量操作栏（东方纸墨微岛 Dock） =====================
@Composable
internal fun BatchActionBar(
    selectedCount: Int,
    onAddToShelf: () -> Unit,
    onSetCategory: () -> Unit,
    onTag: () -> Unit,
    onDownload: () -> Unit,
    onClearCache: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = selectedCount > 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 加入书单：淡紫底座 + Icons.Outlined.Folder
            BatchDockCapsule(
                icon = Icons.Outlined.Folder,
                text = "加入书单",
                baseColor = Color(0xFF7C3AED),
                enabled = enabled,
                onClick = onAddToShelf,
            )
            // 设置分类：天蓝底座 + Icons.Outlined.Category
            BatchDockCapsule(
                icon = Icons.Outlined.Category,
                text = "设置分类",
                baseColor = Color(0xFF0284C7),
                enabled = enabled,
                onClick = onSetCategory,
            )
            // 标签：暖橙底座 + Icons.AutoMirrored.Outlined.Label
            BatchDockCapsule(
                icon = Icons.AutoMirrored.Outlined.Label,
                text = "标签",
                baseColor = Color(0xFFD97706),
                enabled = enabled,
                onClick = onTag,
            )
            // 下载：墨绿底座 + Icons.Outlined.Download
            BatchDockCapsule(
                icon = Icons.Outlined.Download,
                text = "下载",
                baseColor = MaterialTheme.colorScheme.primary,
                enabled = enabled,
                onClick = onDownload,
            )
            // 清缓存：冷灰底座 + Icons.Outlined.DeleteOutline
            BatchDockCapsule(
                icon = Icons.Outlined.DeleteOutline,
                text = "清缓存",
                baseColor = Color(0xFF5A6C7C),
                enabled = enabled,
                onClick = onClearCache,
            )
            // 删除：珊瑚红底座 + Icons.Outlined.DeleteOutline (danger)
            BatchDockCapsule(
                icon = Icons.Outlined.DeleteOutline,
                text = "删除",
                baseColor = AppError,
                enabled = enabled,
                onClick = onDelete,
                danger = true,
            )
        }
    }
}

/** 东方纸墨微岛 Dock 圆润微胶囊按钮：独立微彩圆角底座 + 图标 + 标题 + 弹性按压与触觉反馈 */
@Composable
private fun BatchDockCapsule(
    icon: ImageVector,
    text: String,
    baseColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!reducedMotion && isPressed && enabled) 0.93f else 1.0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
        },
        label = "batchDockCapsuleScale",
    )

    val activeColor = if (danger) AppError else baseColor
    val containerBg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
        danger -> AppError.copy(alpha = 0.08f)
        else -> activeColor.copy(alpha = 0.08f)
    }
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        danger -> AppError.copy(alpha = 0.28f)
        else -> activeColor.copy(alpha = 0.25f)
    }
    val iconBg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        danger -> AppError.copy(alpha = 0.16f)
        else -> activeColor.copy(alpha = 0.15f)
    }
    val contentTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger -> AppError
        else -> activeColor
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                haptic(if (danger) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (enabled && (danger || baseColor == MaterialTheme.colorScheme.primary)) FontWeight.SemiBold else FontWeight.Medium,
                color = contentTint,
            )
        }
    }
}

// ===================== 底部弹层：批量编辑 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchSheet(
    kind: BatchSheetKind,
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRemoveTag: (String) -> Unit = {},
    onCreate: (String) -> Unit,
    showMessage: (String) -> Unit,
    selectedCount: Int = 0,
) {
    val title = when (kind) {
        BatchSheetKind.SHELF -> "加入书单"
        BatchSheetKind.CATEGORY -> "设置分类"
        BatchSheetKind.TAG -> "管理标签"
    }
    val themeColor: Color = when (kind) {
        BatchSheetKind.SHELF -> Color(0xFF7C3AED)
        BatchSheetKind.CATEGORY -> Color(0xFF0284C7)
        BatchSheetKind.TAG -> Color(0xFFD97706)
    }
    val headerIcon: ImageVector = when (kind) {
        BatchSheetKind.SHELF -> Icons.Outlined.Folder
        BatchSheetKind.CATEGORY -> Icons.Outlined.Category
        BatchSheetKind.TAG -> Icons.AutoMirrored.Outlined.Label
    }
    val items: List<Pair<String, String>> = when (kind) {
        BatchSheetKind.SHELF -> shelves.map { it.id to it.name }
        BatchSheetKind.CATEGORY -> categories.map { it.id to it.name }
        BatchSheetKind.TAG -> tags.map { it.id to it.name }
    }
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    val layout = LocalLayoutTokens.current

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 与 BookDetailSheet / FilterSheet 等其它 shelf 弹层同一宽度口径（平板/折叠屏居中，窄屏无影响）
        sheetMaxWidth = layout.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                // 水平内边距走布局令牌（同一 shell 规则），不再各自写 20dp 魔数
                .padding(horizontal = layout.pageHorizontal, vertical = 8.dp)
                .padding(bottom = 28.dp)
                // 书单/分类/标签的数量由用户决定，普通 Column 不设上限也不滚动会把屏外条目
                // 直接裁掉：既看不到也点不到（批量面板此前缺的正是这一层）。
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部标题微岛
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(themeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        headerIcon,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(AppIconSize.Small),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "应用于已选书籍",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 已选书籍微徽章
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Text(
                        "已选 $selectedCount 本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // 数据列表微岛卡片（微底座发丝描边）
            if (items.isEmpty() && !showCreate) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "暂无数据，可点击下方创建新的条目",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else if (items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        items.forEachIndexed { index, (id, name) ->
                            if (kind == BatchSheetKind.TAG) {
                                TagBatchRow(
                                    name = name,
                                    enabled = selectedCount > 0,
                                    onAdd = { onSelect(id) },
                                    onRemove = { onRemoveTag(id) },
                                )
                            } else {
                                SimpleBatchRow(
                                    name = name,
                                    icon = headerIcon,
                                    tint = themeColor,
                                    onClick = { onSelect(id) },
                                )
                            }
                            if (index < items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 新建条目区块
            if (showCreate) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("输入名称", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onCreate(newName.trim())
                                    showCreate = false
                                    newName = ""
                                    showMessage("已创建")
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("创建")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { showCreate = false; newName = "" },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("取消")
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreate = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "创建新的",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagBatchRow(
    name: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFD97706).copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        // 添加微胶囊按钮
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onAdd()
                },
        ) {
            Text(
                "添加",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 移除微胶囊按钮
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = AppError.copy(alpha = 0.08f),
            border = BorderStroke(0.6.dp, AppError.copy(alpha = 0.25f)),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onRemove()
                },
        ) {
            Text(
                "移除",
                style = MaterialTheme.typography.labelSmall,
                color = AppError,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun SimpleBatchRow(
    name: String,
    icon: ImageVector = Icons.Outlined.Folder,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(AppIconSize.Small),
        )
    }
}
