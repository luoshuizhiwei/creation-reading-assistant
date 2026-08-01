package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
            AppTopBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
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

/**
 * Unified page shell. Unlike the legacy [AppPageScaffold], this component owns
 * only the chrome and insets; screens remain free to use a lazy list, grid or a
 * custom canvas without receiving a second layer of padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    topBarSupportingContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        topBar = {
            Column {
                AppTopBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                )
                topBarSupportingContent?.invoke()
            }
        },
        content = content,
    )
}

@Composable
fun PageLazyColumn(
    modifier: Modifier = Modifier,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp),
    contentPadding: PaddingValues? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(LocalLayoutTokens.current.contentGap),
    content: LazyListScope.() -> Unit,
) {
    val layout = LocalLayoutTokens.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentPadding = contentPadding ?: PaddingValues(
            horizontal = layout.pageHorizontal,
            vertical = layout.pageVertical,
        ),
        verticalArrangement = verticalArrangement,
        content = content,
    )
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
            style = MaterialTheme.typography.headlineSmall,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    compact: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            titleContent?.invoke() ?: Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                maxLines = 1,
            )
        },
        modifier = modifier.height(64.dp),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        scrollBehavior = scrollBehavior,
    )
}
