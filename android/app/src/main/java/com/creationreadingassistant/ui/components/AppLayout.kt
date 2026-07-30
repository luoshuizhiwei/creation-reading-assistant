package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineLarge) },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(
                    horizontal = layout.pageHorizontal,
                    vertical = layout.pageVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.relatedGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        content = content,
    )
}

@Composable
fun CompactEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = layout.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .heightIn(min = layout.minimumTouchTarget)
                    .widthIn(min = 96.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
