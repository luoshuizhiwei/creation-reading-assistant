package com.creationreadingassistant.ui.screen.inspiration.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 为单一 AppScreenScaffold 提供的顶部栏配置。
 * 不直接创建 AppTopBar / Scaffold（由单一 InspirationScreen 的 AppScreenScaffold 调用）。
 */
internal data class ToolbarConfig(
    val title: String,
    val compact: Boolean,
    val titleContent: (@Composable () -> Unit)? = null,
    val navigationIcon: (@Composable () -> Unit)? = null,
    val actions: @Composable RowScope.() -> Unit = {},
    val topBarSupporting: (@Composable () -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberListToolbar(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onCloseSearch: () -> Unit,
    onNew: () -> Unit,
): ToolbarConfig = androidx.compose.runtime.remember(
    searchOpen, query,
) {
    ToolbarConfig(
        title = "灵感",
        compact = searchOpen,
        titleContent = {
            if (searchOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                "搜索标题、正文、标签或来源",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "清空输入",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCloseSearch) {
                        Text(
                            "取消",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                Text(
                    "灵感",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        letterSpacing = (-0.5).sp,
                    ),
                )
            }
        },
        actions = {
            if (!searchOpen) {
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索灵感")
                }
                IconButton(onClick = onNew) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建灵感")
                }
            }
        },
    )
}

@Composable
internal fun rememberDetailToolbar(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMore: () -> Unit,
    actionsEnabled: Boolean,
): ToolbarConfig = androidx.compose.runtime.remember(onBack, onEdit, onMore, actionsEnabled) {
    ToolbarConfig(
        title = "灵感详情",
        compact = true,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = onEdit, enabled = actionsEnabled) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑灵感")
            }
            IconButton(onClick = onMore, enabled = actionsEnabled) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多操作")
            }
        },
    )
}

@Composable
internal fun rememberEditorToolbar(
    editingExisting: Boolean,
    onBack: () -> Unit,
): ToolbarConfig = androidx.compose.runtime.remember(editingExisting, onBack) {
    ToolbarConfig(
        title = if (editingExisting) "编辑灵感" else "新建灵感",
        compact = true,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
    )
}

@Composable
internal fun placeholderSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(AppIconSize.Small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索标题、正文、标签或来源") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(2.dp))
        TextButton(onClick = onClose) { Text("取消") }
    }
}
