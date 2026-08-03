package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding

/**
 * 最近灵感 Section（首页第三块）。
 *
 * 只负责自己的布局：标题 + 右侧查看全部 + 灵感列表。
 * 灵感点击回调：onOpenDetail(inspId)；"查看全部"回调：onSeeAll()。
 * 列表 item 不包裹卡片或额外分隔线——对齐"编辑式文本列表"设计。
 */
@Composable
fun HomeInspirationSection(
    inspirations: List<InspirationEntity>,
    onSeeAll: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.home_recent_inspiration),
            modifier = Modifier
                .animateEnter(180, reducedMotion)
                .testTag("inspiration-header"),
            action = {
                TextButton(
                    onClick = onSeeAll,
                    modifier = Modifier.testTag("inspiration-see-all"),
                ) {
                    Text(stringResource(R.string.home_see_all))
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp),
                    )
                }
            },
        )
        if (inspirations.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateEnter(180, reducedMotion)
                    .testTag("inspiration-empty"),
            ) {
                Text(
                    "还没有灵感，阅读时选中文字即可保存为灵感。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                // 注意：HomeScreen 有且只有一个页面级 LazyColumn；
                // 这里直接在父级 LazyColumn 的 items 块里渲染灵感 item，
                // 本 Composable 只渲染每个 item 的 Column（非嵌套 LazyColumn）。
                // 具体 item 渲染由 HomeScreen 外层 LazyColumn 调用本文件的 HomeInspirationItem。
                modifier = Modifier.testTag("inspiration-items-stub"),
            ) {
                items(inspirations, key = { "insp-item-${it.id}" }) { insp ->
                    HomeInspirationItem(
                        inspiration = insp,
                        onClick = { onOpenDetail(insp.id) },
                        enterDelayMs = 180,
                    )
                }
            }
        }
    }
}

/**
 * 单条灵感 item。作为 @Composable 函数暴露，便于 HomeScreen 外层唯一 LazyColumn
 * 直接 `items(state.recentInspirations) { ... }` 调用，避免嵌套 LazyColumn。
 */
@Composable
fun HomeInspirationItem(
    inspiration: InspirationEntity,
    onClick: () -> Unit,
    enterDelayMs: Int = 180,
) {
    val reducedMotion = rememberReducedMotion()
    Column(
        Modifier
            .fillMaxWidth()
            .animateEnter(enterDelayMs, reducedMotion)
            .clickable(onClick = onClick)
            .padding(bottom = 12.dp)
            .testTag("insp-item-${inspiration.id}"),
    ) {
        Text(
            inspiration.title.ifBlank { "无标题灵感" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (inspiration.body.isNotBlank()) {
            Text(
                inspiration.body.take(60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
