# 窗口 D · 2026-09-24 · CraSlider RTL Compose 渲染测试

- 工作区：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（HEAD `5aed593`，分支 `codex/recover-worktree-20260920`）
- 本轮性质：新增一条**由测试自身注入 RTL** 的 androidTest + 离线编译取证。**未运行任何真机 / adb / 模拟器 / 系统语言或 RTL 设置操作。**
- 输入依据：`AGENTS.md`、`docs/qa/2026-09/2026-09-23-bcd-evidence-correction.md`、`docs/qa/2026-09/2026-09-24-d-rtl-root-cause.md`
- **裁决状态：4 条用例「已编译、未运行」，不声称 RTL PASS。**

## 1. 交付物

| 文件 | 动作 | sha256 | 行数 |
|---|---|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/components/CraSliderRtlRenderTest.kt` | 新建（4 条 `@Test`） | `5017af8de3ca361145f126c48b018fc547e3a8656ef034e373d176f0a212aa46` | 208 |
| `docs/qa/2026-09/2026-09-24-d-rtl-compose-test.md` | 新建（本报告） | —— | —— |

**未改动**（所有权外，逐一确认）：`CraSlider.kt`、`GoalSubPage.kt`、`AndroidManifest.xml`、`feature/reader/pager/PageTurnerHighSpeedTest.kt`。
目标目录 `androidTest/java/com/creationreadingassistant/ui/components/` 里已有的 `LayoutComponentsTest.kt`
是 **HEAD 已跟踪**文件（`git ls-files` 确认），本轮只新增文件、未触碰它；本轮新增的
`CraSliderRtlRenderTest.kt` 是该目录下唯一的未跟踪项。

## 2. 可观察断言如何成立

**本文件一次都没有调用 `computeCraSliderTrackGeometry`**，所以它不可能自证。方向由
`CompositionLocalProvider(LocalLayoutDirection provides direction)` 注入，不依赖 `debug.force_rtl`
与系统语言 —— 这正是它相对上一轮验收的价值：把「应用有没有实现 RTL」与「这台设备的强制 RTL 开关好不好使」解耦。

### 通道 1：数值 / 拇指方向（M3 那一层）

`CraSlider` 的对外出口是 `onValueChange`。渲染 `valueRange = 0f..1f`、`steps = 0` 的受控实例
（父侧 `value` 固定 0.5，不随回调改变，保证两次渲染几何一致），然后对**同一物理位置** —— 节点宽度的
20% —— 执行 `performTouchInput { click(Offset(width * 0.20f, height * 0.5f)) }`，捕获回调收到的值：

| 用例 | 断言 | 期望值来源 |
|---|---|---|
| `tapNearLeftEdgeYieldsLowValueInLtr` | `value < 0.40f` | LTR 下点靠左 ⇒ 小值 |
| `tapNearLeftEdgeYieldsHighValueInRtl` | `value > 0.60f` | RTL 下点同一位置 ⇒ 大值（与 LTR 互补） |

两条互为镜像，构成**方向判别器**：如果方向注入没生效或组件忽略方向，两次会得到几乎相同的值，
于是必有一条变红；如果组件把方向处理反了，两条同时变红。夹具里还有一条前置断言
`reported.isNotEmpty()`：若 M3 的 tap 路径根本不产出数值，就直接报「无从观察」，而不是让
数值断言以 `last()` 异常的形式含糊失败。

取值量纲核对（360dp 固定宽、M3 拇指内缩约 10dp）：`x=20%` ⇒ LTR `≈ (72−10)/(360−20) ≈ 0.18`，
RTL `≈ 0.82`，与 0.40 / 0.60 阈值之间各有 ≥0.20 的边距，不依赖像素级精度。

M3 侧依据（对**实际参与构建的** material3 **1.3.1** 产物 javap，见前一份报告 §2.3）：
`SliderKt` 读 `getLocalLayoutDirection()` → `SliderState.setRtl$material3_release(Z)`；
`SliderState.onPress-k-4lQ0M` 在 `isRtl` 时用 `totalWidth - x`；存在
`SliderKt.sliderTapModifier(...)`（类清单里的 `SliderKt$sliderTapModifier$1 / $1$1 / $1$2`）
且它在 Slider 的 modifier 链上被调用 —— 即「点轨道定位」这一手势通道在该版本确实存在。
**但这是静态取证，不是运行证据。**

### 通道 2：自绘轨道像素（本组件自己那一层）

M3 只镜像拇指，**不会**镜像 `CraSlider` 自绘的 `Canvas`（material3 无任何图层级翻转），
所以「已走段画在哪一侧」只能看像素。沿用仓库既有先例
`captureToImage().toPixelMap()`（`feature/reader/pager/PageTurnerStaticSurfaceTest.kt:60-64`）：

| 用例 | 断言（`value = 0.5`） |
|---|---|
| `activeTrackIsPaintedOnLeftInLtr` | 25% 列**有** tint 像素；75% 列**无** |
| `activeTrackIsPaintedOnRightInRtl` | 75% 列**有** tint 像素；25% 列**无** |

判定不误伤的原因（逐条都是可核对的几何/颜色事实，不是调同一个被测函数）：

- `tint` 由测试显式传入 `Color(0xFF11AAEE)`。未走段是 `onSurface.copy(alpha = 0.10)` 叠在
  surface 上的近灰混合，10% 不透明度不可能在 R/G/B 三通道同时落进 ±0.02；
- 描边是 `activeColor.copy(alpha = 0.22)`，且不透明度远低于 1，同样撞不上该饱和蓝；
- 拇指直径 20dp、圆心在中轴（`value = 0.5`），其 tint 描边离 25% / 75% 采样列各约 90dp，不会串台；
- 采样是**纵向 ±12px 邻域命中**（轨道 6dp ≈ 16px 高，居中对齐），横向位置才是被断言的量 —— 避免依赖
  轨道在 28dp 控件里的精确居中；
- 外壳 `Box(Modifier.requiredSize(360.dp, 60.dp))` 固定宽度，x 分位数才可复现（沿用
  `LayoutComponentsTest` 里 `requiredSize` 定尺的做法）。

两条用例的 4 个断言合起来是「同一组件、同一数值、两个方向、结果必须左右互换」。任何一侧不跟随方向
（例如 `track` 槽里不再读 `LocalLayoutDirection`）都会让其中一条断言变红，因此**不可能是空转**。

## 3. 实际执行的命令与退出码

```
$ ./gradlew --offline :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 16s          exit code: 0
（:app:kspDebugAndroidTestKotlin、:app:compileDebugAndroidTestKotlin 为 executed，其余 UP-TO-DATE）
```

产物取证（证明编译的确实是我这条新用例，而非 UP-TO-DATE 的假绿）：

```
$ javap -p app/build/tmp/kotlin-classes/debugAndroidTest/.../CraSliderRtlRenderTest.class
public final void tapNearLeftEdgeYieldsLowValueInLtr();
public final void tapNearLeftEdgeYieldsHighValueInRtl();
public final void activeTrackIsPaintedOnLeftInLtr();
public final void activeTrackIsPaintedOnRightInRtl();
$ javap -v … | grep -B 3 "org/junit/Test"   →  Lorg/junit/Test; 存在；@get:Rule 编为 Lorg/junit/Rule;
```

编译日志里 `warning:` 计数为 0，且无任何提及该文件的告警。

**未运行的部分（不得当作通过）**：`:app:connectedDebugAndroidTest` / `am instrument` 全部**没有执行**。
4 条用例的运行结果 = 未知。

## 4. 离线检查不到、只有真机能回答的问题

| # | 风险 | 若成立的表象 | 建议 |
|---|---|---|---|
| R1 | M3 1.3.1 的 tap 路径是否在 Compose 测试注入下真的发出 `onValueChange`（javap 只证明代码存在，不证明该手势序列触发它） | 夹具里 `reported.isNotEmpty()` 先红，信息可读 | 真机首跑优先看这条；若成立则改走「拖动 + 断言末值」的替代方案（需另行设计容差） |
| R2 | `captureToImage()` 对 Slider 语义节点的可用性（Pager 侧先例是普通 `Box` 节点） | 用例以 capture 异常失败，而非断言失败 | 真机首跑区分「异常」与「断言红」 |
| R3 | 采样窗口在不同密度设备上是否命中轨道行（±12px 覆盖 6dp 轨道，需 density ≥ 400dpi 才刚好；低密度更宽松） | 两列都「无 tint」⇒ 两个 assertTrue 先红 | 若出现，先加大 `scanRadiusPx`，**不要**先改产品 |
| R4 | MIUI 对 instrumentation 的后台 Activity 拦截（09-22 / 09-23 两轮都有现场记录） | 用例根本不产出结果（仅类名行） | 属已知环境风险；必要时按前两轮做法在阻塞当下抓完整 logcat（不加 `-t` 截断） |
| R5 | 拇指圆心与已走段边缘在 RTL 下是否严格贴合（取决于 M3 给 `track` 槽的左右留白是否对称，本轮未取证） | **本文件不覆盖**，不会红也不会绿 | 真机目视；若要断言化见 §5 SR-2 |

## 5. SEAM REQUEST（需要他人所有权才能落地的部分）

- **SR-1（属主 `CraSlider.kt`）**：若 R1 在真机成立（tap 不产出数值），最省事的替代是给自绘
  `track`/`thumb` 各加一个 `Modifier.testTag(...)`，让测试能直接读两节点的 bounds 做左右互换断言。
  这会改产品文件，本轮无权做，也未做。
- **SR-2（属主 `CraSlider.kt`）**：R5 的交界贴合断言需要知道 `track` 槽的实际左右留白；请组件属主
  确认 M3 1.3.1 是否对称留白（或直接把轨道绘制宽度做成可断言的语义/标签）。
- **SR-D1（上一份报告已登记，仍有效）**：设备侧 `getprop debug.force_rtl` / `settings get global …` /
  `dumpsys activity top | grep -iE "curConfig|mGlobalConfiguration"` 三条 witness，用来回答
  「真机上系统语言/强制 RTL 是否真的让整页镜像」。**本轮新增的这条测试不替代它** —— 它只证明应用侧
  实现，不证明设备侧通路。

## 6. 附带核对：manifest 现由他窗持有未提交改动

`git diff -- android/app/src/main/AndroidManifest.xml` 显示 **+5/−1** 的**他人**在途改动
（新增 TTS `<queries>` 意图，并**新增**了 `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode"`）。
两点影响：

1. `android:supportsRtl="true"` 仍在工作树第 32 行、且属于 HEAD 内容 —— 前一份报告对 H1 的排除不变；
2. 前一份报告 §2.1 引用的 `configChanges` 那行其实是**未提交**的（HEAD 里 Activity 上没有该属性）。
   该处对 H4 的排除按工作树现状成立，但当时未标注「这行不在 HEAD 里」，在此更正；对本轮测试无影响，
   因为方向由测试自己提供，不经过设备配置。

## 7. 边界

- 设备：零操作（无 adb、无手机、无模拟器、未切系统语言、未动设备设置、未碰用户书库）。
- Gradle：只跑了 `:app:compileDebugAndroidTestKotlin`（离线，退出码 0）。未跑全量单测、Lint、APK、connectedAndroidTest。
- git：未 stage / commit / push / reset / checkout / clean / stash；HEAD 仍 `5aed593`，staged 0 项。
- 所有权：只新增 §1 表中两个文件；未改 `CraSlider.kt` / `GoalSubPage.kt` / `AndroidManifest.xml` /
  `PageTurnerHighSpeedTest.kt`，也未改同目录的 `LayoutComponentsTest.kt`。
- 工作区共享：`git status --short` 条目数变化只由本轮新增文件构成；他窗的在途改动原样保留。
