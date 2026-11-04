package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 从**顶栏底缘**右上方垂下来的**灯绳**：坠子在绳子下端，拉一下切换日间 / 夜间模式。
 *
 * ## 出处（对齐 QQ 阅读，非自由发挥）
 *
 * 这是 QQ 阅读阅读页的既有控件，形态实测取证自 QQ 阅读真机截图
 * （2026-09-18，com.yuewen.qqreaderhm）：
 * - 绳子**从顶部菜单栏底缘垂下**，上端平齐栏底缘、被栏盖住，**钟形坠子挂在绳子下端**；
 * - 可见段（栏底缘 → 坠子底）约占屏高 12.5%，绳身:坠子 ≈ 3.5:1，
 *   绳贴右缘（中心距右缘约 17dp）；
 * - 绳身 = 螺旋线圈（浅色绳体 + 密集深色斜缝，缝向「/」），
 *   坠子 = 钟形/铅锤形（上窄下宽、圆底，**不是**尖头水滴）；
 * - 绳与坠的右缘各有一道灰色投影带（QQ 素材 `lamp_cord.png` 同有）；
 * - 自带引导文案「拉拉灯绳，可切换夜间模式哦～」；
 * - 点一下**阅读纸张与应用外壳同时切换**（退出阅读器后书架也变深，实测确认）。
 *
 * ## 显隐
 *
 * 与 QQ 一致：只在菜单唤出时出现，并且**就在顶栏的 AnimatedVisibility 节点内**
 * （由调用方放在顶栏正下方的 Column 里）——与菜单栏同一条 slide/fade 弹簧、
 * 同一帧出现、同一帧收起。绳头上移 6dp 与顶栏面板可见底缘平齐（顶栏容器自带
 * 6dp 透明内衬），视觉上就是从栏底缘长出来的一根整绳。
 *
 * @param paper 当前纸张调色板；绳身颜色随之明暗，保证在浅纸/夜读上都看得清。
 * @param isNight 当前是否夜间态，决定无障碍语义。
 * @param onToggle 点击（拉绳）回调。
 * @param pull 拉动动效进度（0 = 静止，1 = 拉到最低）。**由调用方持有**：本组件位于
 *             `AnimatedVisibility` 内，菜单每次显隐都会重建子组合，动画状态留在组件内部
 *             会在唤出菜单时被重置（表现为进入阅读器时绳子自己动一下）。
 */
@Composable
internal fun ReaderDayNightCord(
    paper: ReaderPaperPalette,
    isNight: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    pull: Float = 0f,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    // 绳身用**外壳主题主色**：用户反馈（2026-09-18，Mate 70 Pro）灯绳要随配色主题一起变色，
    // 且要与周围菜单栏成一套 —— 此前从纸张前景色派生，换配色主题时绳子不跟主题走。
    // 灯绳挂在顶栏（外壳）下，用外壳主色在语义上就是「菜单栏的一部分」。
    val ropeFill = MaterialTheme.colorScheme.primary
    // 线圈缝与描边用 onPrimary：主色底上仍读得出「浅色实体 + 深色线稿」的螺旋感。
    val ropeStroke = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
    // 右侧投影带（QQ 素材同有）：中性黑低透明度，主色无论明暗都能保住立体感。
    val ropeShadow = Color.Black.copy(alpha = if (paper.isLight) 0.18f else 0.3f)
    Box(
        modifier = modifier
            .size(width = CORD_WIDTH, height = CORD_HEIGHT)
            // 整条绳子都是热区（与 QQ 的 30×232dp 控件一致），点哪儿都能拉。
            // toggleable 而非 clickable：TalkBack 会读成「夜间模式，已开启/已关闭」的开关，
            // 而不是一个说不清当前状态的按钮。
            .toggleable(
                value = isNight,
                role = Role.Switch,
                onValueChange = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
            )
            .testTag("reader-day-night-cord"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 预留 6dp 给拉动时向下拉伸的量，避免坠子被画到界外。
            val usableHeight = size.height - 6.dp.toPx()
            val centerX = size.width / 2f
            val stretch = pull * 6.dp.toPx()

            // 造型取证自 QQ 阅读真机截图（2026-09-18，com.yuewen.qqreaderhm）：
            // - 绳身 = 螺旋线圈：浅色绳体上铺密集的深色斜缝，像弹簧/麻花；
            // - 坠子 = 钟形/铅锤形：顶部与绳同宽的小圆肩，向下外扩成宽圆底，
            //   **不是**尖头水滴；
            // - 绳与坠的右缘各有一道灰色投影带。
            // 绳身 + 坠子画成一个连续轮廓（一次填充、一次描边），避免坠子像浮在
            // 绳子之外的另一层。
            // 绳身 7dp（原 9dp）：用户反馈在 Mate 70 Pro 上偏粗、与菜单栏不成一套。
            // 收细后坠子按比例同步缩小，整体仍保持 QQ 的绳:坠 ≈ 3.5:1 造型。
            val ropeW = 7.dp.toPx()
            val halfRope = ropeW / 2f
            val bodyLeft = centerX - halfRope
            // 坠子比例取自 QQ 截图：高≈2.3 倍绳宽、底宽≈2.5 倍绳宽、颈部略宽于绳。
            val dropH = ropeW * 2.3f
            // 拉动方向：绳头固定在顶栏底缘（局部 y=0），坠子向下坠、绳身随之拉长。
            val tw = ropeW * 0.62f
            val bw = ropeW * 1.25f
            val bottomY = usableHeight + stretch
            val neckY = bottomY - dropH
            val silhouette = Path().apply {
                moveTo(bodyLeft, 0f)
                lineTo(centerX - tw, neckY)
                cubicTo(
                    centerX - tw - (bw - tw) * 0.35f, neckY + dropH * 0.28f,
                    centerX - bw, neckY + dropH * 0.45f,
                    centerX - bw, bottomY - dropH * 0.42f,
                )
                cubicTo(
                    centerX - bw, bottomY - dropH * 0.10f,
                    centerX - bw * 0.55f, bottomY,
                    centerX, bottomY,
                )
                cubicTo(
                    centerX + bw * 0.55f, bottomY,
                    centerX + bw, bottomY - dropH * 0.10f,
                    centerX + bw, bottomY - dropH * 0.42f,
                )
                cubicTo(
                    centerX + bw, neckY + dropH * 0.45f,
                    centerX + tw + (bw - tw) * 0.35f, neckY + dropH * 0.28f,
                    centerX + tw, neckY,
                )
                lineTo(bodyLeft + ropeW, 0f)
                close()
            }
            drawPath(path = silhouette, color = ropeFill)

            // 右侧投影带（QQ 素材同有）：绳身一条竖带；坠子先铺阴影、再以左移的同色圆盖出月牙。
            val dropCy = neckY + dropH * 0.62f
            clipPath(silhouette, clipOp = ClipOp.Intersect) {
                drawRect(
                    color = ropeShadow,
                    topLeft = Offset(centerX + halfRope * 0.25f, 0f),
                    size = Size(halfRope * 0.75f, neckY),
                )
                drawCircle(color = ropeShadow, radius = bw * 1.02f, center = Offset(centerX, dropCy))
                drawCircle(
                    color = ropeFill,
                    radius = bw * 1.02f,
                    center = Offset(centerX - bw * 0.5f, dropCy),
                )
            }

            // 螺旋线圈缝：粗深色斜线、间距≈绳宽，浅色段与深色缝近等宽才读得出弹簧感。
            // 裁剪矩形必须落在绳体实际位置（bodyLeft 起）——早期画在原点的版本条纹全被裁光。
            clipPath(
                Path().apply { addRect(Rect(bodyLeft - 1f, 0f, bodyLeft + ropeW + 1f, neckY + ropeW * 0.2f)) },
                clipOp = ClipOp.Intersect,
            ) {
                var seamY = -ropeW
                while (seamY < neckY + ropeW * 0.2f) {
                    // 「/」向斜缝（左下→右上），与 QQ 截图同款右旋线圈。
                    drawLine(
                        color = ropeStroke,
                        start = Offset(bodyLeft - 2f, seamY + ropeW * 0.9f),
                        end = Offset(bodyLeft + ropeW + 2f, seamY),
                        strokeWidth = ropeW * 0.4f,
                    )
                    seamY += ropeW * 0.95f
                }
            }
            drawPath(
                path = silhouette,
                color = ropeStroke,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
    }
}

/**
 * 灯绳控件宽度。QQ 阅读 `nightImage` 原为 30dp；本轮收到 24dp —— 真机反馈（Mate 70 Pro）
 * 绳身偏粗、与菜单栏不成一套，收窄控件的同时绳身从 9dp 收到 7dp。
 * 热区仍覆盖整条绳子（高度不变），点哪儿都能拉。
 */
internal val CORD_WIDTH = 24.dp
/**
 * 灯绳可见长度。取自 QQ 阅读真机截图（2026-09-18）：顶栏底缘 → 坠子底约占总屏高
 * 12.5%（354px / 2832px）；本机 904dp 屏高折算约 113dp。
 */
internal val CORD_HEIGHT = 116.dp

/**
 * 灯绳引导气泡。
 *
 * 文案与形态照抄 QQ 阅读 `nightdialog.xml` 的引导行：文字「拉拉灯绳，可切换夜间模式哦～」
 * 加一个指向灯绳的小箭头，位于灯绳**左侧**（QQ 里是 `layout_constraintEnd_toStartOf`
 * nightImage），垂直上靠近灯绳下段（`marginBottom=40dp`）。
 *
 * 灯绳是纯图形控件，没有文字说明用户不容易发现它能拉，所以首次进阅读器时提示。
 * 是否再显示由调用方持久化（见 [com.creationreadingassistant.ui.onboarding.isCordHintSeen]）：
 * 用户反馈（2026-09-18）要求只在首次使用应用时出现一次，且必须能手动关闭 —— 故气泡自带
 * 关闭按钮，不再依赖「拉一下绳子」这个隐式动作来消掉提示。
 *
 * @param paper 当前纸张调色板，气泡随之明暗。
 * @param onClose 关闭气泡回调；调用方负责持久化「已看过」，避免下次再弹。
 */
@Composable
internal fun ReaderDayNightHint(
    paper: ReaderPaperPalette,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(LocalComponentSpec.current.hintRadius),
        color = paper.panel,
        contentColor = paper.fg,
        border = BorderStroke(0.5.dp, paper.outlineVariant),
        // 阅读器内面板恒定零阴影（保护翻页帧预算），与 ReaderPanelSurface 同口径。
        shadowElevation = paper.panelElevation,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = "拉拉灯绳，可切换夜间模式哦～",
                style = MaterialTheme.typography.labelMedium,
                color = paper.fg,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            // 指向右侧灯绳的箭头（QQ 的 `ay3` 小箭头同义）。
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = paper.accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            // 关闭按钮：用户反馈原版只能靠「拉一下绳子」消掉，没有显式关闭入口。
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "关闭提示",
                tint = paper.fg.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(28.dp)
                    .toggleable(
                        value = false,
                        role = Role.Button,
                        onValueChange = { onClose() },
                    )
                    .padding(5.dp),
            )
        }
    }
}
