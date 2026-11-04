# agent-next-d：S06 RTL 布局下滑杆轨道方向与拇指不一致修复报告

报告时间：2026-09-23  
工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
分支：`codex/recover-worktree-20260920`  
基线 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`

---

## 1. 旧行为的代码证据

根据 `docs/coordination/reviews/undated/agent-stage-b-core-audit.md` 中 **S06-⑦**（P2）记录：
> `CraSlider.kt:113-145` Canvas 恒从 `x=0` 画 `activeW`，未读 `LayoutDirection`；拇指由 M3 Slider 绘制（会镜像）。RTL 下拇指与已走段方向相反。CraSlider 测试零命中。

### 旧代码实证分析
在旧实现中，`CraSlider.kt` 的 `track` 绘制槽位如下：
```kotlin
// 旧版 CraSlider.kt (原第 113-146 行)
Canvas(
    modifier = Modifier
        .fillMaxWidth()
        .height(trackHeight.dp),
) {
    val r = size.height / 2f
    // 未走段打底
    drawRoundRect(color = inactiveColor, cornerRadius = CornerRadius(r, r))
    // 已走段恒从左侧 (x=0) 开始向右绘制：
    val activeW = size.width * fraction
    if (activeW > 0f) {
        drawRoundRect(
            color = activeColor,
            // 默认 topLeft 为 Offset.Zero (物理左侧 x=0)
            size = Size(maxOf(activeW, size.height), size.height),
            cornerRadius = CornerRadius(r, r),
        )
    }
    // 末端停点恒固定在物理最右侧：
    if (fraction < 1f) {
        drawCircle(
            color = stopDotColor,
            radius = size.height * 0.45f,
            center = Offset(size.width - size.height * 0.55f, size.height / 2f),
        )
    }
    // 描边恒从 x=-1.dp 开始：
    if (activeW > size.height) {
        drawRoundRect(
            color = activeColor.copy(alpha = 0.22f),
            topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
            size = Size(activeW + 2.dp.toPx(), size.height + 2.dp.toPx()),
            cornerRadius = CornerRadius(r + 1.dp.toPx(), r + 1.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
```

### 产生的反向缺陷
1. **拇指行为**：Material 3 `Slider` 内部在 RTL 布局下，会将拇指的位置进行物理镜像，即 `fraction = 0` 时拇指位于物理最右端，随着数值增大（`fraction` 趋近 1），拇指从右向左移动。
2. **轨道行为**：旧绘制逻辑未读取 `LayoutDirection`，导致已走段始终从物理最左端（`x=0`）向右生长，且停点恒停在物理最右端。
3. **视觉矛盾**：在阿语、希伯来语等 RTL 界面或开发者设置 RTL 调试时，滑杆已走过的激活轨道位于左侧，而拇指却在右侧，二者南辕北辙，已走段与数值增长方向完全相反。

---

## 2. 精确改动

改动遵循“最小绘制修复、不改动主题与全局、纯几何可测”的原则，仅独占修改 `CraSlider.kt`：

### 2.1 引入布局方向感知与几何计算抽象
将轨道的几何计算抽离为无 Compose 运行时副作用的纯函数 `computeCraSliderTrackGeometry` 及结构 `CraSliderTrackGeometry`：

```kotlin
internal data class CraSliderTrackGeometry(
    val activeTrackTopLeft: Offset,
    val activeTrackSize: Size,
    val stopDotCenter: Offset,
    val outlineTopLeft: Offset,
    val outlineSize: Size,
    val hasActiveTrack: Boolean,
    val hasStopDot: Boolean,
    val hasOutline: Boolean,
)

internal fun computeCraSliderTrackGeometry(
    totalWidth: Float,
    height: Float,
    fraction: Float,
    isRtl: Boolean,
    strokeInsetPx: Float = 1f,
): CraSliderTrackGeometry
```

### 2.2 镜像几何规则
1. **已走段（Active Track）**：
   - **LTR**：`topLeft = Offset(0f, 0f)`，`size = Size(effectiveActiveW, height)`；
   - **RTL**：`topLeft = Offset(totalWidth - effectiveActiveW, 0f)`，`size = Size(effectiveActiveW, height)`。
   - 结果：RTL 下已走段右边缘始终紧贴物理最右侧（`x=totalWidth`），左边缘为 `x=totalWidth - effectiveActiveW`，恰与 M3 Slider 拇指物理位置一致。
2. **末端停点（Stop Dot）**：
   - **LTR**：未走段末端位于最右侧，`center = Offset(totalWidth - height * 0.55f, height / 2f)`；
   - **RTL**：未走段末端位于最左侧（数值最大方向），`center = Offset(height * 0.55f, height / 2f)`。
3. **已走段外侧细描边（Outline）**：
   - **LTR**：`topLeft = Offset(-strokeInsetPx, -strokeInsetPx)`；
   - **RTL**：`topLeft = Offset(totalWidth - activeW - strokeInsetPx, -strokeInsetPx)`。
4. **短条退化保护**：
   - 当 `activeW < height` 时，退化为宽度为 `height` 的胶囊短条：LTR 靠左（`0f`），RTL 靠右（`totalWidth - height`），无负数坐标。

### 2.3 `CraSlider` Composable 接入
在 `track` 槽位中获取 `LocalLayoutDirection.current == LayoutDirection.Rtl`，将 Canvas 尺寸、fraction 与 isRtl 传入几何计算，按几何结果执行绘制。

---

## 3. LTR/RTL 测试设计

新建定向单元测试 `android/app/src/test/java/com/creationreadingassistant/ui/components/CraSliderTest.kt`，包含 6 项针对性用例：

1. `ltr track geometry starts from left and places stop dot at right`
   - 断言 LTR 下 `fraction = 0.4f` 时 `activeTrackTopLeft = (0, 0)`，宽度 400，停点在最右侧 `989f`，描边起始 `-1.5f`。保持原有 LTR 行为 100% 严格一致。
2. `rtl track geometry starts from right and places stop dot at left`
   - 断言 RTL 下 `fraction = 0.4f` 时 `activeTrackTopLeft.x = 600f`，右边缘紧贴 `1000f`，停点镜像位于最左侧 `11f`，描边起始 `598.5f`。确证与拇指（x=600）及数值向左增长完全一致。
3. `zero fraction produces no active track and preserves directional stop dot`
   - 断言 `fraction = 0f` 时无已走段，停点分别在 LTR 右侧（989f）与 RTL 左侧（11f）。
4. `full fraction covers entire track and removes stop dot`
   - 断言 `fraction = 1f` 时停点消失，LTR 与 RTL 下已走段均完整铺满（宽 1000f，起点 0f）。
5. `tiny non zero fraction degenerates to minimum pill without negative coordinates`
   - 断言 `fraction = 0.005f`（5px < 20px）时退化为 20px 短条：LTR 靠左（`x=0`），RTL 靠右（`x=980`），无负坐标变形。
6. `fraction clamp protects against out of range values`
   - 断言 `-0.5f` 与 `1.5f` 等越界值被安全 `coerceIn(0f, 1f)`，无异常穿透。

---

## 4. 静态检查结果

1. **格式与空白检查**：
   ```bash
   git diff --check HEAD -- android/app/src/main/java/com/creationreadingassistant/ui/components/CraSlider.kt android/app/src/test/java/com/creationreadingassistant/ui/components/CraSliderTest.kt
   ```
   - 退出码：`0`（零空白/格式违规）。
2. **所有权隔离**：
   - 仅触碰独占的 `CraSlider.kt` 与新增的 `CraSliderTest.kt`；
   - 未修改主题、PageTurner、书架或窗口 B/C 的任何代码；
   - 未使用任何外部试验性 API 或引入新依赖。

---

## 5. 尚未运行的验证

遵照任务纪律：
> “暂不运行 Gradle、ADB 或真机；共享构建稍后由总指挥统一安排。”

以下验证在本次会话中**明确未执行**，留待总指挥统一调度：
1. `./gradlew :app:testDebugUnitTest --tests com.creationreadingassistant.ui.components.CraSliderTest`（定向 JVM 运行）；
2. `./gradlew :app:assembleDebug`（全仓编译检查）；
3. 真实设备或带有 RTL 语言（阿拉伯语/开发人员选项强制 RTL）的真机 UI 视效核验。

---

## 6. 剩余风险评估

1. **宿主父容器未设 RTL 的局部不一致**：若调用方包裹的父组件显式强制了 `LayoutDirection.Ltr`，滑杆将继续按父容器指示以 LTR 呈现；此属 Compose 设计规范预期行为。
2. **拇指触摸热区（Touch Slop）**：M3 `Slider` 自带的无障碍和点击跳转定位完全由 Material 内部管理，本改动仅涉及自绘轨道的视觉镜像，不改变手势检测分发逻辑。
3. **微动效与粗细自定义**：若外部传入极细的 `trackHeight`（如 < 2dp），已走段退化短条仍保证不小于高度，但极细轨道在低 DPI 屏幕上需要视觉回归。
