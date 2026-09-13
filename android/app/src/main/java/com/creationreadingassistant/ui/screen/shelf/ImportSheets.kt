package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.PillShape

// 东方纸墨柔和低饱和配色
internal val FormatPurple = Color(0xFF6E5675) // 黛檀（EPUB）
internal val FormatBlue = Color(0xFF3F637D)   // 霁蓝（TXT）
internal val FormatAmber = Color(0xFF9E6532)  // 暖赭（PDF）
internal val FormatGreen = Color(0xFF3B6E55)  // 松竹（MD）

internal fun formatColor(format: String): Color = when (format.uppercase()) {
    "EPUB" -> FormatPurple
    "TXT" -> FormatBlue
    "PDF" -> FormatAmber
    "MD", "MARKDOWN" -> FormatGreen
    else -> Color(0xFF5A6661)
}

// ===================== 格式与状态微胶囊 =====================
@Composable
internal fun FormatCapsule(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 10.sp,
        )
    }
}

@Composable
internal fun StatusMicroBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.35f), PillShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
