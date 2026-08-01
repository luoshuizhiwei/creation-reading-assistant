package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow

// ============================== 隐私安全 ==============================

@Composable
internal fun PrivacySubPage(modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            Column {
                PrivacyItem(Icons.Filled.Security, "本地优先", "没有账号服务器。书籍、灵感、进度和笔记默认保存在手机本地。")
                PrivacyItem(Icons.Filled.Wifi, "同步可控", "局域网同步需要你手动连接电脑；WebDAV 需要你主动配置地址。")
                PrivacyItem(Icons.Filled.AutoAwesome, "密钥隔离", "AI Key 和 WebDAV 密码 / token 仅保存在应用本地沙箱，不参与电脑同步、WebDAV 同步或数据导出。")
                PrivacyItem(Icons.Filled.Storage, "路径隔离", "本地文件路径、readerPreview、临时 URI 等设备私有字段不会进入同步 payload。")
            }
        }
    }
}

@Composable
private fun PrivacyItem(icon: ImageVector, title: String, body: String) {
    // 视觉统一：与全页开关/菜单行一致，走 SettingRow（原 Material3 ListItem 的内边距与字号偏离规范）
    SettingRow(
        title = title,
        subtitle = body,
        leading = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    )
}
