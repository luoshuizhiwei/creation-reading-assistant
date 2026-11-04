package com.creationreadingassistant.ui.screen.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.components.CompactItemDivider
import com.creationreadingassistant.ui.components.CompactSettingRow
import com.creationreadingassistant.ui.components.CompactSettingsGroup
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== 关于我们与开源协议 ==============================

/**
 * 关于页紧凑版：原先 4 张卡（品牌大屏 / 新版动态 / 检查更新 / 技术栈与致谢）压成
 * **2 张分组卡 + 1 行卡外小字**。
 *
 * - 品牌特性徽章与技术栈徽章都是「一条信息一格」，合并为同一组信息行。
 * - 「发现新版本」独立卡与「检查更新」卡是同一语义分区（版本），合并后共用一个主按钮：
 *   原下载按钮的 onClick 与主按钮 hasUpdate 分支完全一致，删掉不丢功能。
 * - 整句说明压到一行陈述句，致谢长段落降为卡外 `DegradedNote`。
 */
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
    // 检查更新状态来自 ViewModel（决策⑥收口）：联网比对在 ProfileUpdateChecker，子页只渲染。
    val update = state.aboutUpdate

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { AppLog.e("About", "打开链接失败：$url") }
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 应用身份：格式 / 同步 / 内核 / 技术栈并列信息，一张分组卡六行
        item(key = "about-app-card") {
            CompactSettingsGroup(
                title = "创作阅读助手",
                icon = Icons.Outlined.AutoStories,
                tint = Color(0xFF0284C7),
                description = "本地优先 · 离线可读 · 自研排版",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            ) {
                AboutInfoRow("支持格式", "TXT · MD · EPUB", showDivider = false)
                AboutInfoRow("同步方式", "局域网 · WebDAV")
                AboutInfoRow("阅读内核", "自研逐页排版")
                AboutInfoRow("界面框架", "Kotlin + Compose")
                AboutInfoRow("本地存储", "Room + DataStore")
                AboutInfoRow("开源协议", "GPL-3.0")
                CompactItemDivider()
                CompactSettingRow(
                    title = "GitHub Releases",
                    subtitle = "更新日志与 APK 下载",
                    leading = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        openUrl(MOBILE_RELEASES_URL)
                    },
                    trailing = { AboutTrailing("主页") },
                )
            }
        }

        // 2. 版本与更新：新版提示 + 检查按钮 + 状态条合为一张卡
        item(key = "about-update-card") {
            CompactSettingsGroup(
                title = "版本更新",
                icon = Icons.Outlined.Update,
                tint = Color(0xFF7C3AED),
                description = "联网比对 GitHub Releases 最新版本",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            ) {
                AboutInfoRow("当前版本", "v$versionName", showDivider = false)

                if (update.hasUpdate) {
                    CompactItemDivider()
                    CompactSettingRow(
                        title = "新版本 ${update.latestTag.orEmpty()}",
                        subtitle = "点击按钮前往下载",
                    )
                }

                update.lastCheckAt?.let { checkedAt ->
                    CompactItemDivider()
                    AboutInfoRow("上次检查", formatDateTimeShort(checkedAt), showDivider = false)
                }

                // 发布说明只留前几行，长段落不在关于页展开
                if (update.hasUpdate) {
                    update.releaseNotes?.let { notes ->
                        CompactItemDivider()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                notes.lines().take(4).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                CompactItemDivider()
                Button(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        if (update.hasUpdate) {
                            openUrl(update.downloadUrl ?: MOBILE_RELEASES_URL)
                        } else {
                            onAction(ProfileAction.CheckUpdate(versionName ?: "1.0.0"))
                        }
                    },
                    enabled = !update.checking,
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                        .height(48.dp),
                ) {
                    if (update.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            if (update.hasUpdate) Icons.Outlined.Download else Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            update.checking -> "正在检查…"
                            update.hasUpdate -> "下载 ${update.latestTag}"
                            else -> "检查新版本"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                update.status?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // 3. 致谢与许可：整段说明降为卡外一行小字
        item(key = "about-license-note") {
            DegradedNote(
                text = "排版内核为自研实现，架构借鉴 Legado（GPL-3.0）并遵循其协议。",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            )
        }
    }
}

/** 分组卡内的「名称 + 取值」信息行；取值右对齐弱化，不抢标题。 */
@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
) {
    if (showDivider) CompactItemDivider()
    CompactSettingRow(
        title = label,
        trailing = { AboutTrailing(value) },
    )
}

@Composable
private fun AboutTrailing(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
