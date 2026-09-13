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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

// ===================== 底部弹层：从电脑下载 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBooksSheet(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    listBooks: suspend () -> List<SyncContract.BookFileManifest>,
) {
    var books by remember { mutableStateOf<List<SyncContract.BookFileManifest>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    // 失败与「列表为空」必须区分：此前 listBooks() 抛错会让 loading 永久卡在 true
    // （或直接把异常抛出协程），而空态文案又写成「暂无可下载书籍」，等于把探测失败
    // 说成「电脑端没有书」。文案只陈述事实，不断言失败原因。
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic(rememberReducedMotion())
    val layout = LocalLayoutTokens.current

    fun load() {
        scope.launch {
            loading = true
            loadError = null
            runCatching { listBooks() }
                .onSuccess { books = it }
                .onFailure {
                    books = emptyList()
                    loadError = "未获取到电脑端书籍列表。"
                }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = layout.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        // 电脑端书库可能很长，内容超出屏幕时必须可滚动，否则下方条目不可达。
        Column(
            modifier = Modifier
                // 水平内边距走布局令牌（同一 shell 规则），不再各自写 18dp 魔数
                .padding(horizontal = layout.pageHorizontal, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 顶部标题微岛
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("从电脑导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "局域网快速同步正文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 刷新按钮微胶囊
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        load()
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            // 紧邻可见文字「刷新」：装饰性图标不再重复描述，避免 TalkBack 读成「刷新 刷新」
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "刷新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            val error = loadError
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (error != null) {
                // 探测失败：只陈述「没拿到列表」这一事实，不推断原因（不写成网络错误），
                // 并给出可重复的操作，避免与「电脑端确实没有书」混为一谈。
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "可确认电脑端同步目录后再重新探测。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                load()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "重新探测连接",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            } else if (books.isEmpty()) {
                // 空状态：精致东方纸墨微岛卡片（配网络连接状态微指示与重试引导）
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineArtBook(modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "暂无可下载书籍",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 网络连接状态微指示
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFD97706).copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, Color(0xFFD97706).copy(alpha = 0.25f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFFD97706)),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "当前没有可用书籍",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD97706),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "可检查电脑端同步目录是否包含书籍，再重新探测。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // 重新探测重试引导微胶囊
                        Surface(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                load()
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "重新探测连接",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            } else {
                // 书籍列表：封面微缩占位图与下载状态高光指示
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        books.forEachIndexed { index, book ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                )
                            }
                            DesktopBookRowItem(
                                book = book,
                                onDownload = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onDownload(book.bookId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 可下载书籍列表项：封面微缩占位图与下载状态高光指示 */
@Composable
internal fun DesktopBookRowItem(
    book: SyncContract.BookFileManifest,
    onDownload: () -> Unit,
) {
    val fmtColor = formatColor(book.format)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        // 封面微缩占位图
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = fmtColor.copy(alpha = 0.12f),
            border = BorderStroke(0.6.dp, fmtColor.copy(alpha = 0.30f)),
            modifier = Modifier.size(width = 34.dp, height = 46.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 书脊阴影
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = book.format.uppercase().take(4),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fmtColor,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = book.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatCapsule(book.format.uppercase(), fmtColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    formatBytes(book.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 下载状态高光指示微胶囊
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Download,
                    // 紧邻可见文字「下载」：装饰性图标不再重复描述
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "下载",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
