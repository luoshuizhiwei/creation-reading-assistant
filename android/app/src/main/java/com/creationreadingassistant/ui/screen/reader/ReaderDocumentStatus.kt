package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

/** 文档加载与失败层。保持无卡片覆盖，避免阅读纸张中再套一个视觉容器。 */
@Composable
internal fun ReaderDocumentStatus(
    isLoading: Boolean,
    errorMessage: String?,
    foreground: Color,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(layout.pageHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(layout.contentGap),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "正在打开书籍…",
                    color = foreground.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(layout.contentGap),
            ) {
                Text(
                    text = "无法打开这本书",
                    color = foreground,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = errorMessage.orEmpty(),
                    color = foreground.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(layout.relatedGap)) {
                    TextButton(onClick = onBack) {
                        Text("返回书架")
                    }
                    Button(onClick = onRetry) {
                        Text("重新打开")
                    }
                }
            }
        }
    }
}
