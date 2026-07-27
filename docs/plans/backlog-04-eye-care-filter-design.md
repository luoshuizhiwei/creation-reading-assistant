# 护眼色温滤镜设计稿（backlog 第 4 项备料）

> 2026-07-27 起草。状态：**备料，未实施**。系数已按公式算好并列表，
> 实施时可直接抄；验收以真机截图为准。
> 对应 `docs/plans/legado-feature-backlog.md` 第 4 项。

## 1. 目标与现状

现有「亮度」设置是一层黑色半透明遮罩（45–100 压暗），只降亮度不改色温，
夜间看白底仍是冷光。本功能给阅读界面叠一层**暖色温滤镜**（滤蓝光观感），
可调色温与强度，可按时段自动开启。

不做的：不申请系统级 WRITE_SETTINGS 改屏幕色温，只在应用内做视觉滤镜；
不做 legado 那种全局护眼（我们只盖阅读界面，别的页面用不着）。

## 2. 视觉模型：色温 → RGB 通道缩放 → 乘法混合

把目标色温换算成一个 RGB 颜色（暖色温时蓝、绿通道衰减），
再用**乘法混合**盖在内容上：每个像素的 R/G/B 分别乘以该颜色的对应通道比例。
数学上等价于对角 ColorMatrix，白色变暖白、黑色仍是纯黑
（`0 × 任何比例 = 0`，与 oled-black 背景天然兼容）。

### 色温 → RGB（Tanner Helland 近似公式，2600K–5500K 段）

`t = 色温 / 100`，输出 0–255：

```
R = 255                                      // 6600K 以下恒 255
G = 99.4708025861 × ln(t) − 161.1195681661
B = 138.5177312231 × ln(t − 10) − 305.0447927307   // 1900K 以下取 0
```

这是公开的物理拟合公式（黑体辐射色），系数是事实数据可直接用。
**锚点表**（已按上式算好，实施时可直接内置查表 + 线性插值，省掉运行时 ln）：

| 色温 | R | G | B | 观感 |
|---|---|---|---|---|
| 2600K | 255 | 163 | 79 | 烛光，很暖 |
| 3000K | 255 | 177 | 110 | 白炽灯 |
| 3400K | 255 | 190 | 135 | 暖光（**建议默认**） |
| 4000K | 255 | 206 | 166 | 中性偏暖 |
| 4500K | 255 | 218 | 187 | 微暖 |
| 5000K | 255 | 228 | 206 | 接近日光 |
| 5500K | 255 | 237 | 222 | 几乎无感（滑条上限） |

### 强度

强度 `s ∈ 0..1` 把各通道比例向 1（无滤镜）插值：
`scale' = 1 − s × (1 − scale)`。默认 60%。
（滑条两个就够：色温 2600–5500K、强度 0–100%。不要再加曲线选项。）

## 3. Compose 实现（新写，约 30 行）

在阅读器根容器上加一个绘制修饰符：

```kotlin
// warmColor = Color(r, g, b) 来自上表插值 + 强度换算，alpha 恒 1f
fun Modifier.eyeCareFilter(warmColor: Color?): Modifier =
    if (warmColor == null) this           // 关闭时零开销短路
    else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(color = warmColor, blendMode = BlendMode.Multiply)
        }
```

要点：

- `BlendMode.Multiply` 的一次 `drawRect` 就是逐通道乘法，等价 ColorMatrix，
  不需要引入任何矩阵代码；
- `CompositingStrategy.Offscreen` 必须加：把混合限制在本层内容上，
  否则 Multiply 会和窗口背景混合出错色。代价是一层全屏 saveLayer，
  现代设备可忽略；关闭滤镜时整个 Modifier 短路，零开销；
- 与亮度遮罩的先后顺序**无所谓**：两者都是逐通道标量乘法，数学上可交换，
  实施时谁在外层都行，不要为顺序纠结。

**应用位置**：`ReaderScreen` 内容根 Box（滚动模式与翻页引擎模式共同的父级），
菜单、弹层一起变暖（一致的夜读观感，也省得每个 Sheet 单独处理）。
沉浸模式下系统栏是透明的，不受影响。

## 4. 定时时段

生效条件：`手动开关 ON`，**或** `定时开关 ON 且当前时间在时段内`。
两个开关独立，语义简单，不做「手动覆盖到下个边界」那类状态机。

跨零点判定（默认 22:00–07:00 就是跨零点）：

```kotlin
fun inSchedule(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start <= end) now >= start && now < end   // 同日时段，如 19:00–23:00
    else now >= start || now < end                // 跨零点，如 22:00–07:00
```

刷新时机：进入阅读页时算一次 + 阅读中每分钟 tick 重算（阅读器本来常驻，
一个 `LaunchedEffect` 循环 `delay(60_000)` 即可）。边界上晚生效一分钟以内，可接受。

## 5. 设置项与 SettingsStore 新增 key（草案）

| key | 类型 | 默认 | 说明 |
|---|---|---|---|
| `eye_care_filter_enabled` | Boolean | false | 手动开关 |
| `eye_care_color_temp` | Int | 3400 | 色温 K，2600–5500 |
| `eye_care_intensity` | Int | 60 | 强度 %，0–100 |
| `eye_care_schedule_enabled` | Boolean | false | 定时开关 |
| `eye_care_schedule_start` | Int | 1320 | 起始，自零点的分钟数（22:00） |
| `eye_care_schedule_end` | Int | 420 | 结束，分钟数（07:00） |

UI 放在阅读器 ThemeSheet（主题/背景那页）加一个「护眼」分组；
Profile 的阅读设置子页同步露出（注意 open-loops 里记过的教训：
两处设置入口必须逐项一致）。

命名说明：现有 `reader_eye_care_minutes` 是「护眼**提醒**」（提醒休息），
与本滤镜无关，UI 文案注意区分：「护眼提醒」vs「护眼色温」。

## 6. 验收标准（真机）

1. 截图三组对比：关闭 / 3400K·60% / 2600K·100%，白底与夜间背景各一；
2. oled-black 背景 + 滤镜开：黑色区域肉眼仍是纯黑（乘法性质验证）；
3. 定时 22:00–07:00：改系统时间到 21:59 / 22:01 / 06:59 / 07:01 四点各验一次；
4. 翻页引擎模式与滚动模式都生效；菜单、目录 Sheet、TTS 面板同样变暖；
5. 开滤镜前后翻页流畅度无肉眼差异（Offscreen 层的性能验证）。

## 7. 实施量预估

SettingsStore 6 个 key + ThemeSheet UI 一个分组 + 30 行绘制修饰符 + 时段判定纯函数
（附 4 条单测：同日/跨零点 × 内/外）。合计 1–2 天，与 backlog 预估一致。
无 Room 迁移、无新依赖。
