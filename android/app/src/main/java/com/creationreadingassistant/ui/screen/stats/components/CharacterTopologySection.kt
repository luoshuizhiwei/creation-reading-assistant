package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.IslandSectionHeader
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 拓扑图节点（人物角色） */
internal data class CharacterNode(
    val id: String,
    val name: String,
    val role: String,
    val mentions: Int,
    val tintColor: Color,
)

/** 拓扑图关系边 */
internal data class CharacterEdge(
    val sourceId: String,
    val targetId: String,
    val relation: String,
    val weight: Float, // 0f ~ 1f
)

/** 小说拓扑样本数据集 */
internal data class BookTopologyGraph(
    val bookTitle: String,
    val nodes: List<CharacterNode>,
    val edges: List<CharacterEdge>,
)

private val SAMPLE_GRAPHS = listOf(
    BookTopologyGraph(
        bookTitle = "三国志演义",
        nodes = listOf(
            CharacterNode("1", "刘备", "汉昭烈帝", 840, Color(0xFF2563EB)),
            CharacterNode("2", "关羽", "威震华夏", 620, Color(0xFFDC2626)),
            CharacterNode("3", "张飞", "万人莫敌", 480, Color(0xFF4B5563)),
            CharacterNode("4", "诸葛亮", "卧龙丞相", 780, Color(0xFF059669)),
            CharacterNode("5", "曹操", "魏武雄才", 810, Color(0xFF7C3AED)),
            CharacterNode("6", "孙权", "江东霸主", 510, Color(0xFFD97706)),
        ),
        edges = listOf(
            CharacterEdge("1", "2", "桃园结义", 0.95f),
            CharacterEdge("1", "3", "桃园结义", 0.90f),
            CharacterEdge("2", "3", "生死同盟", 0.85f),
            CharacterEdge("1", "4", "三顾知遇", 0.98f),
            CharacterEdge("1", "5", "天下英雄", 0.75f),
            CharacterEdge("4", "5", "赤壁相争", 0.80f),
            CharacterEdge("5", "6", "濡须鏖战", 0.65f),
            CharacterEdge("1", "6", "联盟抗曹", 0.70f),
            CharacterEdge("2", "5", "义释华容", 0.60f),
        ),
    ),
    BookTopologyGraph(
        bookTitle = "红楼梦",
        nodes = listOf(
            CharacterNode("1", "贾宝玉", "神瑛侍者", 920, Color(0xFFE11D48)),
            CharacterNode("2", "林黛玉", "绛珠仙草", 860, Color(0xFF0284C7)),
            CharacterNode("3", "薛宝钗", "蘅芜君", 750, Color(0xFFD97706)),
            CharacterNode("4", "王熙凤", "荣府管家", 680, Color(0xFF9333EA)),
            CharacterNode("5", "贾母", "史老太君", 540, Color(0xFF475569)),
        ),
        edges = listOf(
            CharacterEdge("1", "2", "木石前盟", 0.98f),
            CharacterEdge("1", "3", "金玉良缘", 0.88f),
            CharacterEdge("2", "3", "互剖心迹", 0.72f),
            CharacterEdge("1", "4", "姑侄至亲", 0.65f),
            CharacterEdge("1", "5", "宠溺至爱", 0.80f),
            CharacterEdge("4", "5", "奉承承欢", 0.78f),
        ),
    ),
)

/**
 * 小说核心角色关系拓扑微岛：
 * - 展示书籍中关键人物共现频次、角色定位与网状羁绊连线；
 * - 支持触摸节点聚焦：高亮对应人物关联网与互动频次详情；
 * - 纯 Canvas 自适应环形力拓扑排布与贝塞尔/直连线渲染。
 */
@Composable
internal fun CharacterTopologySection(
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    var selectedBookIndex by remember { mutableStateOf(0) }
    val currentGraph = SAMPLE_GRAPHS[selectedBookIndex]
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    IslandCard(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(reducedMotion = reducedMotion)
            .testTag("stats-character-topology"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 标题栏：带有图谱小图标 + 切换书目胶囊
            IslandSectionHeader(
                title = "小说人物关系拓扑",
                icon = Icons.Outlined.Hub,
                tint = scheme.tertiary,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SAMPLE_GRAPHS.forEachIndexed { index, graph ->
                            val isSelected = index == selectedBookIndex
                            Surface(
                                shape = PillShape,
                                color = if (isSelected) scheme.tertiaryContainer else scheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    spec.hairlineBorderWidth,
                                    if (isSelected) scheme.tertiary else scheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                                ),
                                modifier = Modifier.clickable {
                                    selectedBookIndex = index
                                    selectedNodeId = null
                                },
                            ) {
                                Text(
                                    text = graph.bookTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                },
            )

            // 拓扑图画板
            val nodes = currentGraph.nodes
            val edges = currentGraph.edges
            val nodeMap = remember(nodes) { nodes.associateBy { it.id } }

            // 记录节点在 Canvas 坐标系下的中心点位置，用于点击命中测试
            var nodeOffsets by remember(currentGraph) { mutableStateOf<Map<String, Offset>>(emptyMap()) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerLow.copy(alpha = 0.5f))
                    .pointerInput(currentGraph, nodeOffsets) {
                        detectTapGestures { tapOffset ->
                            // 碰撞检测：半径 32dp 内命中节点
                            val hitRadiusPx = with(density) { 30.dp.toPx() }
                            val hit = nodeOffsets.entries.firstOrNull { (_, center) ->
                                val dx = tapOffset.x - center.x
                                val dy = tapOffset.y - center.y
                                sqrt(dx * dx + dy * dy) <= hitRadiusPx
                            }
                            selectedNodeId = if (hit != null && hit.key != selectedNodeId) hit.key else null
                        }
                    },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val centerX = w / 2f
                    val centerY = h / 2f
                    val radius = (minOf(w, h) / 2f - 40.dp.toPx()).coerceAtLeast(40f)

                    // 1. 计算每个节点在圆环上的坐标
                    val count = nodes.size
                    val calculatedOffsets = mutableMapOf<String, Offset>()
                    nodes.forEachIndexed { index, node ->
                        val angle = (2 * Math.PI / count * index) - Math.PI / 2
                        val nx = centerX + radius * cos(angle).toFloat()
                        val ny = centerY + radius * sin(angle).toFloat()
                        calculatedOffsets[node.id] = Offset(nx, ny)
                    }
                    nodeOffsets = calculatedOffsets

                    // 2. 绘制边（连线）
                    edges.forEach { edge ->
                        val p1 = calculatedOffsets[edge.sourceId] ?: return@forEach
                        val p2 = calculatedOffsets[edge.targetId] ?: return@forEach

                        val isConnected = selectedNodeId == null ||
                                edge.sourceId == selectedNodeId ||
                                edge.targetId == selectedNodeId

                        val edgeAlpha = if (selectedNodeId == null) {
                            0.20f + edge.weight * 0.35f
                        } else if (isConnected) {
                            0.85f
                        } else {
                            0.06f
                        }

                        val strokeWidth = if (isConnected && selectedNodeId != null) {
                            3.5.dp.toPx()
                        } else {
                            (1f + edge.weight * 2.5f).dp.toPx()
                        }

                        drawLine(
                            color = if (isConnected && selectedNodeId != null) scheme.tertiary.copy(alpha = edgeAlpha)
                            else scheme.onSurfaceVariant.copy(alpha = edgeAlpha),
                            start = p1,
                            end = p2,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                            pathEffect = if (!isConnected) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                        )
                    }

                    // 3. 绘制节点圆圈背景与呼吸外环
                    nodes.forEach { node ->
                        val center = calculatedOffsets[node.id] ?: return@forEach
                        val isSelected = node.id == selectedNodeId
                        val isRelated = selectedNodeId != null && edges.any {
                            (it.sourceId == selectedNodeId && it.targetId == node.id) ||
                                    (it.targetId == selectedNodeId && it.sourceId == node.id)
                        }
                        val isDimmed = selectedNodeId != null && !isSelected && !isRelated

                        val nodeRadius = if (isSelected) 22.dp.toPx() else 18.dp.toPx()

                        // 外光晕
                        if (isSelected) {
                            drawCircle(
                                color = node.tintColor.copy(alpha = 0.25f),
                                radius = nodeRadius + 6.dp.toPx(),
                                center = center,
                            )
                        }

                        // 实体圆底座
                        val circleAlpha = if (isDimmed) 0.25f else 1.0f
                        drawCircle(
                            color = scheme.surfaceContainerHigh.copy(alpha = circleAlpha),
                            radius = nodeRadius,
                            center = center,
                        )

                        // 边框描边
                        drawCircle(
                            color = if (isSelected) node.tintColor else node.tintColor.copy(alpha = if (isDimmed) 0.3f else 0.8f),
                            radius = nodeRadius,
                            center = center,
                            style = Stroke(width = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx()),
                        )
                    }
                }

                // 在 Canvas 上方覆盖文字标签
                nodeOffsets.forEach { (nodeId, center) ->
                    val node = nodeMap[nodeId] ?: return@forEach
                    val isSelected = nodeId == selectedNodeId
                    val isRelated = selectedNodeId != null && edges.any {
                        (it.sourceId == selectedNodeId && it.targetId == nodeId) ||
                                (it.targetId == selectedNodeId && it.sourceId == nodeId)
                    }
                    val isDimmed = selectedNodeId != null && !isSelected && !isRelated

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = with(density) { (center.x - 28.dp.toPx()).coerceAtLeast(0f).toDp() },
                                top = with(density) { (center.y - 12.dp.toPx()).coerceAtLeast(0f).toDp() },
                            )
                            .width(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isSelected) 12.sp else 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isDimmed) scheme.onSurfaceVariant.copy(alpha = 0.35f)
                            else if (isSelected) scheme.onSurface
                            else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 选中角色详细羁绊卡
            AnimatedVisibility(
                visible = selectedNodeId != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                val selected = nodeMap[selectedNodeId]
                if (selected != null) {
                    val relatedEdges = edges.filter { it.sourceId == selected.id || it.targetId == selected.id }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scheme.surfaceContainerHigh.copy(alpha = 0.65f),
                        border = BorderStroke(spec.hairlineBorderWidth, scheme.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(selected.tintColor),
                                    )
                                    Text(
                                        text = "${selected.name}（${selected.role}）",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = scheme.onSurface,
                                    )
                                    Surface(
                                        shape = PillShape,
                                        color = scheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                    ) {
                                        Text(
                                            text = "登场 ${selected.mentions} 次",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { selectedNodeId = null },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "取消聚焦",
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // 关联关系标签
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                relatedEdges.take(4).forEach { edge ->
                                    val otherId = if (edge.sourceId == selected.id) edge.targetId else edge.sourceId
                                    val otherNode = nodeMap[otherId]
                                    if (otherNode != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = scheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                            border = BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.3f)),
                                        ) {
                                            Text(
                                                text = "${otherNode.name} · ${edge.relation}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部轻量提示
            if (selectedNodeId == null) {
                Text(
                    text = "轻按人物节点，探索共现脉络与核心交集羁绊。",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
