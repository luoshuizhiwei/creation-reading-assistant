package com.creationreadingassistant.ui.screen.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.SealMark
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================== 关于我们与开源协议 ==============================

@Composable
internal fun AboutSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("1.0.0")
    }
    val scope = rememberCoroutineScope()

    var updateStatus by remember { mutableStateOf<String?>(null) }
    var hasUpdate by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var lastCheckAt by remember { mutableStateOf<String?>(null) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { AppLog.e("About", "打开链接失败：$url") }
    }

    fun checkUpdate() {
        checking = true
        updateStatus = null
        scope.launch(Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL(MOBILE_RELEASE_API_URL).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val release = UpdateCheck.latestAndroidRelease(text)
                val current = versionName.toString().trimStart('v', 'V')
                when {
                    release == null -> "未获取到版本信息"
                    UpdateCheck.compareVersions(release.version, current) <= 0 -> {
                        hasUpdate = false
                        latestVersion = null
                        releaseNotes = null
                        downloadUrl = null
                        "已是最新版本（当前 v$versionName）"
                    }
                    else -> {
                        hasUpdate = true
                        latestVersion = release.tag
                        releaseNotes = release.notes
                        downloadUrl = release.pageUrl
                        "发现新版本 ${release.tag}（当前 v$versionName）"
                    }
                }
            }.onSuccess {
                checking = false
                lastCheckAt = Instant.now().toString()
                updateStatus = it
                AppLog.event("About", "检查更新：$it")
            }.onFailure {
                checking = false
                updateStatus = "更新检查失败：${it.message}"
                AppLog.e("About", "更新检查失败：${it.message}")
            }
        }
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 顶部应用徽标与版本卡片升级为典雅纸墨微岛大屏（带东方印章 SealMark 与版本微胶囊）
        item(key = "about-brand-hero-card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion)
                    .border(
                        width = 1.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            ),
                        ),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(1.dp),
            ) {
                SectionCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 顶部徽标 + 东方印章 SealMark 并置
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }

                            // 东方印章（文人藏书印「墨卷」）
                            SealMark(
                                text = "墨卷",
                                size = 36.dp,
                                animateStamp = true,
                            )
                        }

                        // 应用标题与字距微调
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                "创作阅读助手",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    fontSize = 22.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "本地优先 · 智能伴读 · 自研逐页纸墨排版引擎",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 版本微胶囊（带发丝描边）
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                )
                                Text(
                                    "版本 $versionName",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )

                        // 特性微徽章三列导轨
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            FeatureBadge(label = "TXT / MD / EPUB", desc = "全格式自研排版")
                            FeatureBadge(label = "局域网 / WebDAV", desc = "多通道跨端同步")
                            FeatureBadge(label = "自研排版引擎", desc = "极致东方纸墨触感")
                        }
                    }
                }
            }
        }

        // 2. 新版本动态提示（若已检测到新版）
        if (hasUpdate) {
            item(key = "about-has-update-card") {
                Card(
                    modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
                    shape = LocalComponentSpec.current.cardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "发现新版本 $latestVersion",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            "当前版本：v$versionName · 建议更新以体验最新的排版优化与稳定提升",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        releaseNotes?.let { notes ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    notes.lines().take(10).joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 10,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                        Button(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                openUrl(downloadUrl ?: MOBILE_RELEASES_URL)
                            },
                            shape = PillShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("前往下载最新 APK", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }

        // 3. 检查更新微岛（带最新版本动态指示微胶囊按钮）
        item(key = "about-update-check-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubPageSectionTitle(
                        title = "版本检查与发布通道",
                        icon = Icons.Outlined.Update,
                        iconTint = Color(0xFF0284C7),
                        trailing = {
                            if (lastCheckAt != null) {
                                Surface(
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    Text(
                                        "已检查：${formatDateTimeShort(lastCheckAt)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        },
                    )

                    Text(
                        "应用发布于 GitHub 官方 Releases 仓库。点击下方按钮即可联网比对最新版本，下载安装由 Android 系统安装器确认完成。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )

                    // 检查更新微胶囊按钮（带最新版本动态指示）
                    Button(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            if (hasUpdate) openUrl(downloadUrl ?: MOBILE_RELEASES_URL) else checkUpdate()
                        },
                        enabled = !checking,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                if (hasUpdate) Icons.Outlined.Download else Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                checking -> "正在检查最新 Releases…"
                                hasUpdate -> "发现新版 $latestVersion · 点击前往下载"
                                else -> "检查新版本"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }

                    // 动态指示状态条
                    updateStatus?.let { status ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    if (hasUpdate) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = if (hasUpdate) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    status,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 开发者致谢与技术栈卡片微岛化（发丝描边、分色微图标底座）
        item(key = "about-open-source-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SubPageSectionTitle(
                        title = "技术栈与致谢",
                        icon = Icons.Outlined.Code,
                        iconTint = Color(0xFF059669),
                    )

                    // 技术栈微岛网格
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TechStackBadge(
                            title = "原生引擎",
                            desc = "Kotlin + Compose",
                            icon = Icons.Outlined.Code,
                            color = Color(0xFF1967D2),
                            modifier = Modifier.weight(1f),
                        )
                        TechStackBadge(
                            title = "持久存储",
                            desc = "Room + DataStore",
                            icon = Icons.Outlined.Shield,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.weight(1f),
                        )
                        TechStackBadge(
                            title = "开源协议",
                            desc = "GPL-3.0 守诺",
                            icon = Icons.Outlined.Favorite,
                            color = Color(0xFF059669),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    SectionDivider()

                    // 致谢说明
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "致谢与开源说明",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "本应用阅读内核为自研原生逐页排版实现，架构上借鉴了 Legado（开源阅读，GPL-3.0）等优秀开源项目的优秀实践。严格遵守 GPL-3.0 开源协议要求，恪守本地优先与隐私保护理念。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp,
                        )
                    }

                    // 开源主页与直达微胶囊
                    OutlinedButton(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            openUrl(MOBILE_RELEASES_URL)
                        },
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("访问 GitHub Releases 发布主页", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * 特性微徽章
 */
@Composable
private fun FeatureBadge(label: String, desc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 技术栈微底座徽章
 */
@Composable
private fun TechStackBadge(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
