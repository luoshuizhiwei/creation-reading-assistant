package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec

// ===================== 底部弹层：书籍操作 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookActionSheet(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
    onDismiss: () -> Unit,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
    onRepair: (BookEntity) -> Unit,
    onOpenDetail: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val readiness = book.readiness()
    val percent = progressFor(progressById, book.id)
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
                // 本面板共四层（元数据 / 主行动 / 管理 / 删除），大字号或小屏下可能超出可用高度；
                // 缺滚动会让「删除书籍」落在屏外且无法触达。
                .verticalScroll(rememberScrollState()),
        ) {
            // 书籍元数据微岛卡片
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 封面：8dp 圆角、微书脊立体阴影
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 2.dp,
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                        modifier = Modifier.size(52.dp, 72.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            BookCover(
                                book = book,
                                percent = percent,
                                modifier = Modifier.fillMaxSize(),
                                fallback = { ShelfCoverFallback(book) },
                            )
                            // 微书脊阴影
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.20f),
                                                Color.Black.copy(alpha = 0.05f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            book.author?.takeIf { it.isNotBlank() } ?: "作者未知",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 准备度与格式微胶囊。
                        // 就绪态只在「下方主行动按钮没说同一件事」时才并列——按钮标题已表达
                        // 可读（继续阅读）或需下载（下载正文），重复陈述只会挤占这一行的信息量。
                        // 格式胶囊按契约保留：格式信息在此属低干扰呈现。
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!readinessStatedByAction(readiness, book.content_status)) {
                                val tone = toneColor(readiness.tone)
                                Surface(
                                    color = tone.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, tone.copy(alpha = 0.25f)),
                                ) {
                                    Text(
                                        readiness.label,
                                        color = tone,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    book.format.uppercase(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 第一层：核心高频动作（继续阅读 / 下载正文）
            if (readiness.tone == ReadinessTone.READY) {
                ActionRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "继续阅读",
                    subtitle = "从上次保存的位置打开",
                    primary = true,
                    onClick = { onContinue(book) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (readiness.tone == ReadinessTone.CLOUD) {
                ActionRow(
                    icon = Icons.Outlined.Download,
                    title = "下载正文",
                    subtitle = "保存到本机后离线阅读",
                    primary = true,
                    onClick = { onDownload(book) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 第二层：常规管理操作微岛卡片
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    ActionRow(
                        icon = Icons.Outlined.Refresh,
                        title = "重新定位文件",
                        subtitle = if (readiness.tone == ReadinessTone.READY) "更换本地文件并保留阅读记录" else "修复缺失的本地正文",
                        onClick = { onRepair(book) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    )
                    ActionRow(
                        icon = Icons.Outlined.Info,
                        title = "书籍详情与管理",
                        subtitle = "编辑信息、封面、分类和书单",
                        onClick = { onOpenDetail(book.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第三层：危险操作（柔和红调微岛）
            ActionRow(
                icon = Icons.Outlined.Delete,
                title = "删除书籍",
                // 与详情面板的删除入口共用同一常量：删除动作移除的是书籍资料与阅读数据，
                // 磁盘上的内部正文副本由「移除正文」单独回收，这里不得声称已删「本机正文」。
                subtitle = DELETE_BOOK_SELF_DESCRIPTION,
                danger = true,
                onClick = { onDelete(book.id) },
            )
        }
    }
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        danger -> AppError
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor = when {
        danger -> AppError.copy(alpha = 0.06f)
        primary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    val borderColor = when {
        danger -> AppError.copy(alpha = 0.20f)
        primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = if (primary || danger) BorderStroke(1.dp, borderColor) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 微彩图标底座（28-36dp，圆角矩形 8-10dp，alpha 0.12f）
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(AppIconSize.Medium),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (danger) AppError else if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danger) AppError.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (danger) AppError.copy(alpha = 0.40f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                modifier = Modifier.size(AppIconSize.Small),
            )
        }
    }
}
