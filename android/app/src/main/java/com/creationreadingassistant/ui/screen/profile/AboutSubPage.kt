package com.creationreadingassistant.ui.screen.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================== 关于 ==============================

@Composable
internal fun AboutSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("未知")
    }
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var hasUpdate by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var lastCheckAt by remember { mutableStateOf<String?>(null) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { AppLog.e("About", "打开链接失败：$url") }
    }

    fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
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
                val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.getOrNull(1) ?: ""
                val bodyRaw = Regex("\"body\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(text)?.groupValues?.getOrNull(1)
                val current = versionName.toString().trimStart('v', 'V')
                val body = bodyRaw?.let { unescapeJsonString(it).trim().takeIf { s -> s.isNotBlank() } }
                when {
                    tag.isBlank() -> "未获取到版本信息"
                    tag.trimStart('v', 'V') == current -> {
                        hasUpdate = false
                        latestVersion = null
                        releaseNotes = null
                        "已是最新版本（当前 $versionName）"
                    }
                    else -> {
                        hasUpdate = true
                        latestVersion = tag
                        releaseNotes = body
                        "发现新版本 $tag（当前 $versionName）"
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
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Book, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("创作阅读助手", style = MaterialTheme.typography.titleLarge)
                    Text("Android 端 · 本地优先 · 灵感中心特色版", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("版本：$versionName", style = MaterialTheme.typography.bodyMedium)
                    Text("支持：TXT / Markdown / EPUB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("同步：电脑局域网 / WebDAV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("开源与致谢", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("个人自用构建，不对外分发", style = MaterialTheme.typography.titleMedium)
                    Text("阅读内核为自研实现，设计上参考了 Legado（开源阅读应用，GPL-3.0）等项目。若未来对外分发，将依 GPL-3.0 要求提供完整源代码与修改说明。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (hasUpdate) {
            item {
                Card(
                    modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
                    shape = LocalComponentSpec.current.cardShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("发现新版本", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text("最新版本：$latestVersion（当前 $versionName）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        releaseNotes?.let { notes ->
                            Text(
                                notes.lines().take(12).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 12,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(onClick = { openUrl(MOBILE_RELEASES_URL) }) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Text("前往下载", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("应用更新", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("检查新版本", style = MaterialTheme.typography.titleMedium)
                    Text("发布新版后可在此检查并前往安装包下载页；Android 仍会要求你确认安装。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { if (hasUpdate) openUrl(MOBILE_RELEASES_URL) else checkUpdate() },
                        enabled = !checking,
                    ) {
                        if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text(
                            if (hasUpdate) "重新检查" else "检查更新",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (updateStatus != null) {
                        Text(updateStatus!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lastCheckAt != null) {
                        Text("上次检查：${formatDateTime(lastCheckAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
