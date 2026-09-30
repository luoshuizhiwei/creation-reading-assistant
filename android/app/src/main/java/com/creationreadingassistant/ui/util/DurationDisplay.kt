package com.creationreadingassistant.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.creationreadingassistant.data.settings.DurationUnit

/**
 * 时长展示单位的 CompositionLocal 设施。
 *
 * 用 CompositionLocal 而非逐层透传：时长文本散布在书架 / 阅读器 / 统计 / 历史 / 首页
 * 等多个页面，各自归属不同 ViewModel；把单位塞进每个 `*UiState` 会把一个纯展示偏好
 * 污染到整条状态链上。这里在根部提供一次，子树按需取用。
 *
 * 展示位的数量与归属会随页面演进变化，**不要在本注释里写死数量**——需要盘点时用
 * `grep -rn "formatDuration(\|DurationText(" android/app/src/main/java` 现查。
 *
 * 与 `LocalLayoutTokens` / `LocalComponentSpec` 同一惯例。
 *
 * **入口粒度是「语义组件」而非「任意文本」**：时长会跟随全局显示单位；在支持的
 * 阅读时长文本或卡片代表区域长按，可快速切换小时/天。同一语义组件内重复的时长文本
 * （年度账单的 subtitle / 金句、热力图单日 tooltip、趋势柱刻度）刻意不再各自挂入口，
 * 避免 TalkBack 在同一张卡里读到多个同名操作。
 */

/**
 * 当前时长显示单位。由根部（MainActivity）读 [com.creationreadingassistant.data.settings.DurationPrefs]
 * 后提供；未提供时回落到默认 [DurationUnit.HOURS]（测试与预览友好）。
 */
val LocalDurationUnit = staticCompositionLocalOf { DurationUnit.HOURS }

/**
 * 切换时长单位（小时 ⇄ 天）。根部提供，内部写 DataStore 持久化。
 *
 * 默认空实现：未提供时（测试 / 预览）长按无副作用，不会崩溃。
 */
val LocalDurationUnitToggle = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 长按切换时长单位的无障碍语义标签。
 *
 * 这是 TalkBack 对时长文本朗读出的**唯一**操作名，也是自动化测试定位「可切换入口」
 * 的依据，故抽成常量而不是散落的字面量：文案一旦改动，语义与测试必须同步改动。
 *
 * 不设 `Role.Button`：入口在视觉与朗读上仍是「一段时长文字」，只是附带一个快捷操作，
 * 若宣称自己是按钮会让读屏用户以为点按它有导航行为。
 */
const val DURATION_UNIT_TOGGLE_LABEL: String = "切换时长显示单位"

/**
 * 长按切换时长单位的 Modifier。
 *
 * **适用范围（边界，不是可选项）**：本 Modifier 只适用于**没有父级点击 / 长按冲突的
 * 安全节点**——也就是自身及祖先链上都没有 `clickable` / `combinedClickable` /
 * `toggleable` 等手势容器，或该容器本身不再承担主交互的展示位。
 *
 * **不保证子节点不影响父级手势。** `combinedClickable` 挂在子节点上时，手势识别器会
 * 参与命中竞争，空 `onClick` 仍可能截获本该交给父级的点击；`indication = null` 只关掉
 * ripple，不改变命中行为。因此：
 *
 * - 处于**可点击卡片、列表行或已有长按容器内**的时长文本，手势必须由代表性容器统一
 *   拥有（如容器自己的 `combinedClickable`），而不是在子文本上再挂一个本 Modifier；
 * - **不得在父子两层重复挂** [durationUnitToggle]：两层各自消费手势会让父级主交互
 *   时灵时不灵，且 TalkBack 会读到两个同名操作；
 * - 需要判定"挂在这里安不安全"时，向上检查祖先链有无手势容器，不要靠猜。
 *   F3-A 选取的 HomeMetrics / SummaryGroup / CreationSection / Heatmap 汇总 /
 *   YearBill 主值正是这样的安全节点；F3-B 负责处理父级已有点击的节点。
 *
 * `indication = null` 是因为纯文本长按不需要 ripple 反馈（切换后文本立刻变化，本身
 * 就是反馈）。`onClick` 传空 lambda 是 `combinedClickable` 的必填参数：**普通点击
 * 什么也不做**，只有长按会切换单位——切换是快捷方式，不是所在组件的主交互。
 *
 * 一个语义组件只允许挂一次本 Modifier（见 [DurationText] 与统计页各区块）：同一张卡上
 * 出现两个「切换时长显示单位」会让 TalkBack 用户无从判断哪一个才是入口。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.durationUnitToggle(): Modifier {
    val toggle = LocalDurationUnitToggle.current
    return combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
        onLongClick = toggle,
        onLongClickLabel = DURATION_UNIT_TOGGLE_LABEL,
    )
}

/**
 * 时长文本：自动跟随全局单位，长按切换。
 *
 * 纯文本展示位的统一入口（`"累计 12 小时 30 分钟"` 这类整句请用
 * [formatDuration] + [durationUnitToggle]，不要用本组件拼接）。
 */
@Composable
fun DurationText(
    ms: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = formatDuration(ms, LocalDurationUnit.current),
        modifier = modifier.durationUnitToggle(),
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}
