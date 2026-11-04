package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometry
import com.creationreadingassistant.feature.reader.pager.TapActionPreset

/**
 * 预览卡各分区的 testTag —— 给 UI 测试用来量**真实分区容器**的尺寸。
 *
 * 测试若改用 `onNodeWithText` 拿到的节点，量到的是文字行盒而不是热区，
 * 比例断言会失效；所以这里显式暴露容器。仅测试钩子，不影响视觉与无障碍语义。
 */
internal const val TAP_PREVIEW_TAG_LEFT = "tap-preview-left"
internal const val TAP_PREVIEW_TAG_CENTER = "tap-preview-center"
internal const val TAP_PREVIEW_TAG_RIGHT = "tap-preview-right"
internal const val TAP_PREVIEW_TAG_TOP = "tap-preview-top"
internal const val TAP_PREVIEW_TAG_BOTTOM = "tap-preview-bottom"

/**
 * 屏幕点击热区交互可视化微卡片。
 *
 * 模拟手机屏幕外观，实时根据 [tapZoneMode]（三分区 / 五分区）
 * 与 [tapActionPreset]（标准 / 左手模式 / 双向推进）高亮并标注各个触控热区的分发动作。
 *
 * 分区占比一律取自 [ReaderTapZoneGeometry]，与真实点击热区（自研分页引擎、legacy 分页
 * EPUB）同一事实源；本文件不得再写自己的比例。各区的**动作**也必须与
 * `resolveReaderTapAction` 的分发结果一致，包括下方的提示文案。
 */
@Composable
internal fun TapZonePreviewCard(
    tapZoneMode: String,
    tapActionPreset: String,
    modifier: Modifier = Modifier,
) {
    val isFiveZone = tapZoneMode == "five-zone"
    // 横向左/中/右 = 1/3 : 1/3 : 1/3；五分区纵向顶/中/底 = 12% : 76% : 12%。
    val (leftWeight, centerWeight, rightWeight) = ReaderTapZoneGeometry.horizontalWeights
    val (topWeight, middleWeight, bottomWeight) = ReaderTapZoneGeometry.verticalWeights
    val isLeftNext = tapActionPreset == TapActionPreset.LEFT_HAND || tapActionPreset == TapActionPreset.BOTH_NEXT
    val isRightNext = tapActionPreset != TapActionPreset.LEFT_HAND

    val leftLabel = if (isLeftNext) "下一页" else "上一页"
    val leftIcon = if (isLeftNext) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    val leftIsNext = isLeftNext

    val rightLabel = if (isRightNext) "下一页" else "上一页"
    val rightIcon = if (isRightNext) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    val rightIsNext = isRightNext

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        // 拟真手机微屏幕容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(4.dp),
        ) {
            if (isFiveZone) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // 顶部扩展区：上一页。12% 的带高只放得下图标，文字标签省略以免随字体缩放被裁；
                    // 「上一页」改由图标的 contentDescription 承载，TalkBack 仍能读到（S09-B-F1）。
                    PreviewZoneItem(
                        label = "上一页",
                        icon = Icons.Outlined.KeyboardArrowUp,
                        isAdvance = false,
                        showLabel = false,
                        tag = TAP_PREVIEW_TAG_TOP,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(topWeight),
                    )

                    // 中间水平三段：仍按横向 1/3 : 1/3 : 1/3 切分
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(middleWeight),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        PreviewZoneItem(
                            label = leftLabel,
                            icon = leftIcon,
                            isAdvance = leftIsNext,
                            tag = TAP_PREVIEW_TAG_LEFT,
                            modifier = Modifier
                                .weight(leftWeight)
                                .fillMaxHeight(),
                        )
                        PreviewCenterMenuZone(
                            tag = TAP_PREVIEW_TAG_CENTER,
                            modifier = Modifier
                                .weight(centerWeight)
                                .fillMaxHeight(),
                        )
                        PreviewZoneItem(
                            label = rightLabel,
                            icon = rightIcon,
                            isAdvance = rightIsNext,
                            tag = TAP_PREVIEW_TAG_RIGHT,
                            modifier = Modifier
                                .weight(rightWeight)
                                .fillMaxHeight(),
                        )
                    }

                    // 底部扩展区：下一页
                    PreviewZoneItem(
                        label = "下一页",
                        icon = Icons.Outlined.KeyboardArrowDown,
                        isAdvance = true,
                        showLabel = false,
                        tag = TAP_PREVIEW_TAG_BOTTOM,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(bottomWeight),
                    )
                }
            } else {
                // 三分区模式：左右与中央，各占 1/3
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    PreviewZoneItem(
                        label = leftLabel,
                        icon = leftIcon,
                        isAdvance = leftIsNext,
                        tag = TAP_PREVIEW_TAG_LEFT,
                        modifier = Modifier
                            .weight(leftWeight)
                            .fillMaxHeight(),
                    )
                    PreviewCenterMenuZone(
                        tag = TAP_PREVIEW_TAG_CENTER,
                        modifier = Modifier
                            .weight(centerWeight)
                            .fillMaxHeight(),
                    )
                    PreviewZoneItem(
                        label = rightLabel,
                        icon = rightIcon,
                        isAdvance = rightIsNext,
                        tag = TAP_PREVIEW_TAG_RIGHT,
                        modifier = Modifier
                            .weight(rightWeight)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        // 提示文案必须与真实分发动作一致：三分区没有顶部翻页带，就不能承诺「顶部热区」可回退。
        val hintText = when {
            tapActionPreset == TapActionPreset.LEFT_HAND ->
                "💡 左手模式：点击左侧区域翻下一页，右侧区域回退上一页，专为左手单手握持优化。"
            tapActionPreset == TapActionPreset.BOTH_NEXT && isFiveZone ->
                "💡 已开启双向推进：屏幕左右两侧均响应下一页，回退可通过滑动手势或顶部热区。"
            tapActionPreset == TapActionPreset.BOTH_NEXT ->
                "💡 已开启双向推进：屏幕左右两侧均响应下一页，回退可通过滑动手势。"
            else -> "💡 标准模式：点击屏幕左侧回退上一页，点击右侧翻至下一页。"
        }
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun PreviewZoneItem(
    label: String,
    icon: ImageVector,
    isAdvance: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    /** 五分区顶/底带只有 12% 高度，放文字会随系统字体缩放被裁，所以只画箭头。 */
    showLabel: Boolean = true,
) {
    // 下一页用温润的琥珀/主色淡底，上一页用次级背景
    val targetBg = if (isAdvance) {
        Color(0xFFD97706).copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val animatedBg by animateColorAsState(targetBg, tween(250), label = "zoneBg")
    val contentColor = if (isAdvance) {
        Color(0xFFB45309)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconSize = if (showLabel) 13.dp else 10.dp
    // 省略文字标签的分区（五分区顶/底带）必须由图标承担语义：否则视觉用户看得见箭头，
    // TalkBack 却读不出这里是「上一页 / 下一页」。showLabel=true 时文字已在，
    // 图标保持装饰性（contentDescription=null），避免同一分区被读两遍。
    val iconDescription = if (showLabel) null else label

    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(6.dp))
            .background(animatedBg)
            .border(
                width = 0.5.dp,
                color = contentColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor,
                modifier = Modifier.width(iconSize).height(iconSize),
            )
            if (showLabel) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (isAdvance) FontWeight.Medium else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PreviewCenterMenuZone(
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(12.dp).height(12.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "菜单",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}
