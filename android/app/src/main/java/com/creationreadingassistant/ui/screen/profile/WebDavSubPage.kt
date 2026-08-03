package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard

// ============================== WebDAV 页 ==============================

@Composable
internal fun WebDavSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val initialUrl = state.webDavConfig?.url ?: ""
    val initialUser = state.webDavConfig?.user ?: ""
    val initialPass = state.webDavConfig?.pass ?: ""
    val backups = state.webDavBackups
    val hasSavedPass = initialPass.isNotBlank()

    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }

    LaunchedEffect(state.webDavConfig) {
        url = state.webDavConfig?.url ?: ""
        user = state.webDavConfig?.user ?: ""
        pass = state.webDavConfig?.pass ?: ""
    }

    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) onAction(ProfileAction.RefreshBackups)
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("同步目录固定为 .creation-reading-assistant/，会上传 manifest、records 和 books。WebDAV 密码 / token 仅保存在应用本地沙箱，AI Key 不参与同步。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = url, onValueChange = { url = it }, placeholder = { Text("https://example.com/dav") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    OutlinedTextField(value = user, onValueChange = { user = it }, placeholder = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, placeholder = { Text(if (hasSavedPass) "已保存密码 / token；留空则继续使用" else "密码或 token") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = textFieldColors())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(if (hasSavedPass) "已在应用本地沙箱保存 WebDAV 密码 / token；不会导出或参与同步。" else "还没有保存 WebDAV 密码 / token。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAction(ProfileAction.TestWebDav) }, enabled = url.isNotBlank()) { Icon(Icons.Filled.Wifi, contentDescription = null); Text("测试", modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.UploadBackup) }, enabled = url.isNotBlank()) { Icon(Icons.Filled.Upload, contentDescription = null); Text("上传", modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.RefreshBackups) }, enabled = url.isNotBlank()) { Icon(Icons.Filled.Refresh, contentDescription = null); Text("刷新列表", modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.ClearWebDav) }) { Text("清除凭证") }
                    }
                    Button(onClick = { onAction(ProfileAction.SaveWebDav(url.trim(), user.trim(), pass)) }, modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
                }
            }
        }

        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("远程备份列表", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${backups.size} 个文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (backups.isEmpty()) {
                        Text("暂无备份文件。点击上方「上传」或「刷新列表」获取。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            backups.take(8).forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatBytes(file.size)} · ${file.lastModified}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { onAction(ProfileAction.DownloadRestore(file.name)) }) { Text("恢复") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
