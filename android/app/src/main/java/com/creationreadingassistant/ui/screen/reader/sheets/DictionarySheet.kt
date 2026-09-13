package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.feature.dictionary.DictionaryLookupEntry
import com.creationreadingassistant.ui.components.HairlineDivider
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.viewmodel.DictionaryViewModel
import com.creationreadingassistant.ui.viewmodel.dictionaryEmptyNotice
import com.creationreadingassistant.ui.viewmodel.dictionaryStatusLine

/**
 * 阅读器内离线词典面板（R3-X1）。
 *
 * 只依赖已安装的 StarDict 词库，不联网；查不到时把「已装词库清单 + 导入入口」一并给出，
 * 让用户能自己判断是缺词库还是这个词真的不在库里。
 *
 * 五种终止态都有明确文案，且互不冒充（见 [dictionaryEmptyNotice]）：
 * 空选区 / 未装词库 / 有词库但查不到 / 词库打不开 / 查询链路失败。
 */
@Composable
internal fun DictionarySheet(
    word: String,
    onDismiss: () -> Unit,
) {
    val vm: DictionaryViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val spec = LocalComponentSpec.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.install(uri)
    }

    // 打开面板即查一次；换词也重查。空选区同样走一次 lookup，让它落到**明确**的空选区态，
    // 而不是停在「既无加载也无解释」的中间态。refreshInstalled 保证「没有词库」提示是当下真实状态。
    LaunchedEffect(word) {
        vm.refreshInstalled()
        vm.lookup(word)
    }

    // 一次性提示在面板可见期间保持可读，离开面板时清空：
    // 不在这里消费的话，「已卸载词库」会在下次打开面板时作为陈旧状态再次出现。
    DisposableEffect(Unit) {
        onDispose { vm.consumeMessage() }
    }

    val displayWord = state.word.ifBlank { word }.ifBlank { "（未选择文字）" }
    val emptyNotice = dictionaryEmptyNotice(state)

    ReaderSheetScaffold(title = "词典", onBack = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "query") {
                Surface(
                    shape = RoundedCornerShape(spec.islandRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        spec.borderWidth,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconPedestal(
                            icon = Icons.Outlined.Search,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 28.dp,
                            iconSize = 15.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayWord,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                dictionaryStatusLine(state),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.loading) {
                item(key = "loading") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            emptyNotice?.let { notice ->
                item(key = "empty") {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.results, key = { "${it.sourceName}#${it.word}#${it.content.hashCode()}" }) { hit ->
                DictionaryEntryCard(hit)
            }

            item(key = "manage") {
                HairlineDivider(modifier = Modifier.padding(vertical = 4.dp))
                DictionaryLibrarySection(
                    installed = state.installed.map { it.baseName to it.bookName },
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onUninstall = { vm.uninstall(it) },
                )
            }

            state.message?.let { message ->
                item(key = "message") {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryEntryCard(hit: DictionaryLookupEntry) {
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            spec.borderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    hit.word,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        hit.sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                hit.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DictionaryLibrarySection(
    installed: List<Pair<String, String>>,
    onImport: () -> Unit,
    onUninstall: (String) -> Unit,
) {
    val spec = LocalComponentSpec.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "离线词库",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (installed.isEmpty()) {
            Text(
                "暂无。导入的 zip 里每个 StarDict 词库需要同名的 .ifo / .idx / .dict（或 .dict.dz）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            installed.forEach { (baseName, bookName) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        bookName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = { onUninstall(baseName) },
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            spec.borderWidth,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                        ),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "卸载",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(
            onClick = onImport,
            shape = PillShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            border = BorderStroke(
                spec.borderWidth,
                MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "导入词库（.zip）",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
