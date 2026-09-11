package com.creationreadingassistant.feature.library.deletion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.ceil
import kotlinx.coroutines.delay

/** 倒计时刷新间隔；比 1 秒密一些，避免显示出来的秒数比真实剩余多出一截。 */
private const val COUNTDOWN_TICK_MILLIS = 250L

/** 撤销按钮的最小可点击高度，与项目其余触控目标一致。 */
private val UndoTouchTarget = 48.dp

/**
 * 删除撤销提示条 —— 书架、书架搜索、阅读历史三个入口共用同一个组件。
 *
 * 只在 [offer] 非空时渲染，而 offer 来自内存里的凭证登记处：进程重启后它必然为空，
 * 因此不会出现「重启了还挂着失效撤销按钮」。倒计时归零时回调 [onExpired]，
 * 由宿主触发清理，提示条随即消失，用户不会点到一个已经无效的撤销。
 */
@Composable
fun DeletionUndoBar(
    offer: DeletionUndoOffer?,
    onUndo: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExpired: () -> Unit = {},
    nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    if (offer == null) return
    val haptic = rememberHaptic(rememberReducedMotion())
    var remainingSeconds by remember(offer.id, offer.expiresAtMillis) {
        mutableIntStateOf(remainingSecondsOf(offer.expiresAtMillis, nowMillis()))
    }

    LaunchedEffect(offer.id, offer.expiresAtMillis) {
        while (true) {
            val left = remainingSecondsOf(offer.expiresAtMillis, nowMillis())
            remainingSeconds = left
            if (left <= 0) {
                onExpired()
                break
            }
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }

    val undoLabel = stringResource(R.string.deletion_undo_action)
    val dismissLabel = stringResource(R.string.deletion_undo_dismiss)
    val barDescription = stringResource(R.string.deletion_undo_bar_description)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("deletion-undo-bar")
            .semantics { contentDescription = barDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.deletion_undo_message,
                    offer.bookCount,
                    remainingSeconds.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    haptic(HapticFeedbackType.LongPress)
                    onUndo(offer.id)
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = UndoTouchTarget)
                    .testTag("deletion-undo-action"),
            ) {
                Text(undoLabel, fontWeight = FontWeight.SemiBold)
            }
            IconButton(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onDismiss(offer.id)
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = UndoTouchTarget, minWidth = UndoTouchTarget)
                    .testTag("deletion-undo-dismiss"),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = dismissLabel,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun remainingSecondsOf(expiresAtMillis: Long, nowMillis: Long): Int {
    val left = expiresAtMillis - nowMillis
    if (left <= 0L) return 0
    return ceil(left / 1000.0).toInt()
}
