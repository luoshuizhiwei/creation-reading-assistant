# 窗口 D · 2026-09-24 · CraSlider RTL 离线根因调查

- 工作区：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（HEAD `5aed593`，分支 `codex/recover-worktree-20260920`）
- 本轮性质：**离线根因调查**。未连接手机、未运行 `adb`、未使用模拟器、未触碰用户书库与设备设置、未做任何 git 写操作。
- 输入依据：`AGENTS.md`、`docs/qa/2026-09/2026-09-23-bcd-evidence-correction.md`（下称「更正报告」）、`docs/qa/evidence/2026-09-23-bcd-portrait/{commands.log,screenshot-manifest.md}`
- **裁决：本轮不把任何旧截图记作 RTL PASS。** `d_rtl_craslider.png` / `d_rtl_reader_chrome.png` 的内容区与 LTR 图逐像素相同（更正报告 §4.2 差分），它们证明的是「当时没有镜像」，不是「镜像正确」。

## 1. 结论先说

1. **`CraSlider` 自身的 RTL 几何没有缺陷可证** —— 我用确定性 JVM 测试把它升级成了**可执行的性质断言**：101 个 fraction 取样点上，RTL 几何是 LTR 几何关于轨道中轴的**精确镜像**，且始终不越出画布。8 条用例全绿。因此**没有做生产代码改动**（`CraSlider.kt` 一个字节未动）。
2. **上一轮「强制 RTL 未镜像」的原因不在这个滑杆里**，也不在应用的 RTL 声明里：现场证据显示**整个页面的组合层方向仍然是 Ltr**，那是一个 `Row` 级别的事实，任何滑杆内部数学都影响不到它。
3. 最可能但**本轮无法离线证实**的根因是：**验收用的开关根本没有抵达框架读取的那一层**（详见 §4 H6）。证据链只做过 `settings put/get global debug.force_rtl`，从未 `getprop`，也从未抓过任何带 `ldrtl/ldltr` 的 Configuration 输出 —— 所谓「回读为 1」只证明了 Settings 那一行，没有证明应用实际拿到的方向。**此点不作结论，只列为下一步设备验证项。**

## 2. 已核对的证据

### 2.1 声明与方向入口（排除「应用没开 RTL」）

| 检查项 | 命令/位置 | 结果 |
|---|---|---|
| `supportsRtl` | `android/app/src/main/AndroidManifest.xml:32` | `android:supportsRtl="true"`，全仓仅此一份 manifest |
| 源码里钉死方向 | `grep -rn "setLayoutDirection\|LAYOUT_DIRECTION\|createConfigurationContext\|updateConfiguration\|setLocale\|LocaleList\|AppCompatDelegate" app/src/main` | **零命中**（`App.attachBaseContext` 只 `super` + 缓存） |
| 资源/主题里钉死方向 | `grep -rn "layoutDirection\|textDirection" app/src/main --include=*.xml` | **零命中** |
| `LocalLayoutDirection` 是「消费」而非「覆盖」 | `AppLayout.kt:110/184`、`CraSlider.kt:103` | 三处都是 `.current` 读取后参与计算，无任何 `CompositionLocalProvider(LocalLayoutDirection provides …)` |
| Activity 方向变更是否会被吞 | `AndroidManifest.xml:39` `configChanges="orientation\|screenSize\|screenLayout\|smallestScreenSize\|uiMode"` | **不含** `layoutDirection` → 方向变化会重建 Activity，不构成拦截 |

入口链完整：`Resources.configuration.layoutDirection` → `View` 解析 → `ComposeView` → `LocalLayoutDirection`。应用侧没有断点。

### 2.2 现场证据说明「未镜像」发生在 Row 级别，不在滑杆里

上一轮 force_rtl 窗口内的原始 dump（更正报告 §4.2 引用的 `current_dump.xml`，23:37:15）里，与滑杆同页、**不含任何滑杆代码**的两个 `Row` 是：

- `GoalSubPage.kt:352-359` 刻度标签 `Row(SpaceBetween)`：`0 分钟 (关闭)` x=83、`90 分钟` x=585、`180 分钟` x=1018；
- `GoalSubPage.kt:306-330` 预设档位 `Row(spacedBy)`：`关闭 / 15 分 / … / 90 分` x 由 125 递增到 1018。

`Row` 的水平顺序完全由 `LocalLayoutDirection` 决定（RTL 下自动反向，无需任何应用侧镜像代码）。**两者仍自左向右递增 ⇒ 该次运行的组合方向是 Ltr。** 这条推论与滑杆数学无关，因此把「未镜像」归因给 `CraSlider` 不成立。

### 2.3 M3 Slider 在 RTL 下怎么做（决定「手写镜像是否会被二次翻转」）

对**实际参与构建的产物**取证（不是读文档猜版本）：`~/.gradle/caches/…/material3-release-runtime.jar`（material3 **1.3.1**）

```
SliderKt.class      : getLocalLayoutDirection() … LayoutDirection.Rtl → SliderState.setRtl$material3_release(Z)
SliderState.class   : onPress-k-4lQ0M:  isRtl ? (getTotalWidth() - Offset.getX(ptr)) : getX(ptr)
SliderKt.class      : grep graphicsLayer|scaleX|Mirror|reverseLayout  → 0 命中
```

即 M3 用**算术**镜像拇指，不做图层级翻转。所以 `CraSlider` 在 `track` 槽里自己判断 `isRtl` 并手动镜像填充方向，是**必需且方向一致**的做法，不存在「M3 已经镜像、我们又翻一次」的双重镜像缺陷。

### 2.4 确定性几何测试（本轮唯一代码产出）

`CraSliderTest.kt` 新增 2 条：

1. `rtl geometry is the exact mirror of ltr across the whole fraction range` —— fraction 0.00…1.00 共 101 点，逐点断言：三个绘制标志同真同假、`activeTrackSize`/`outlineSize`/`stopDotCenter.y` 与方向无关、三段水平区间满足 `rtl.x == W − (ltr.x + ltr.width)`。把既有逐点断言升级成**整区间性质**约束。
2. `rtl drawing stays inside the canvas for every fraction` —— RTL 下已走段区间必须落在 `[0, W]`，停点圆（半径 `height×0.45`）不得画出左右边界。`Canvas` 不裁剪越界绘制，越界即毛刺。
   两条都用 `withAssertContext(fraction)` 包一层，失败信息带上是哪一个 fraction。

手工锚点（与测试同结论）：W=1000、h=20、inset=1.5、f=0.4 ⇒ LTR 已走段 `[0,400]` / RTL `[600,1000]`；停点 `989` ↔ `11`；描边 `[−1.5,401.5]` ↔ `[598.5,1001.5]`。f=0 与 f=1 端点同样精确闭合。

**运行记录（本轮真实执行，非静态推断）：**

```
$ ./gradlew --offline :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.ui.components.CraSliderTest" --rerun
BUILD SUCCESSFUL in 1m 3s
exit code: 0
```

产物 `android/app/build/test-results/testDebugUnitTest/TEST-…CraSliderTest.xml`：
`tests="8" skipped="0" failures="0" errors="0"`，8 个 `testcase` 名齐全（含本轮新增 2 条）。

诚实标注：本轮**没有**做「故意改坏 `CraSlider.kt` 看新用例变红」的变异实验 —— 那需要在共享工作区里瞬时破坏生产代码，若窗口 B/C 恰在此时编译会追一个假故障。新用例的非空转性由上述逐点推导支撑；同时必须说明，它抓到的多数单侧偏移**既有的 RTL 逐点用例也会抓到**，其独有价值在 96 个未取样 fraction 的扫描、跨向标志/尺寸一致性以及画布内包含性。

## 3. 代码改动清单

| 文件 | 状态 | sha256 | 说明 |
|---|---|---|---|
| `ui/components/CraSlider.kt` | **未改动** | `678cc5e9118bca0b2410f9765c90089a66889b694fbfb216f40bbb0ec9c6b9b4` | 无缺陷证据 → 不做猜测性修改 |
| `ui/components/CraSliderTest.kt` | 修改（+2 用例，未新增依赖） | `acc79af47bdd456f381a046648a7df9aa4aa117e7bf899e508feb3a758c4fa49` | 本轮唯一代码产出 |
| `docs/qa/2026-09/2026-09-24-d-rtl-root-cause.md` | 新建 | —— | 本报告 |

注意：这两个 Kotlin 文件在 HEAD 里是 **未跟踪状态（`??`）** —— 它们本身就是更早 D 批次尚未提交的产物，不是本轮从已提交版本改出来的。`git diff --cached` 为 0 项，HEAD 仍 `5aed593`；未 `add/commit/push/reset/checkout/clean/stash`。工作区另有其他窗口的改动，本轮未触碰。
副产物：定向测试运行写入了 `android/app/build/`（git 已忽略），可能让该目录相对他轮的构建态前进。

## 4. 排除过的假设

| # | 假设 | 判定 | 依据 |
|---|---|---|---|
| H1 | 漏声明 `supportsRtl` | **排除** | `AndroidManifest.xml:32` 明确为 `true`（本轮重新核对，非沿用他人结论） |
| H2 | 应用某处把方向钉成 Ltr（主题/View/Context/locale 覆盖） | **排除** | §2.1 四组 grep 全零命中；`LocalLayoutDirection` 只被读、未被 provide |
| H3 | `CraSlider` 的 RTL 数学不对称（撕裂来源） | **离线排除** | §2.4 镜像不变式 + 包含性用例全绿；§2.3 证明不存在与 M3 的双重镜像 |
| H4 | Activity 未随方向重建导致旧配置残留 | **排除** | `configChanges` 不含 `layoutDirection`（会重建）；且上一轮做过 `am force-stop` + `am start` |
| H5 | 「未镜像」是滑杆特例，页面其余部分其实已镜像 | **排除** | `Row` 级证据未反向（§2.2），且 LTR/RTL 两张原图内容区差分 0.0000% |
| H6 | `settings put global debug.force_rtl 1` 没有抵达框架实际读取的载体（Settings 行 vs 系统属性 / MIUI 忽略 debug 属性） | **未证实，列为首要待验** | 上一轮对该键的全部操作都是 `settings put/get global`（`commands.log:25` 预读 `0`、`131-132` 置 `1` 并回读 `1`、`152-153` 复原 `0`），**从未** `getprop debug.force_rtl`，也从未抓过任何 `curConfig/mGlobalConfiguration`。同页预读还显示设备 locale 为 `zh-CN`（LTR，`commands.log:22`），即**除了这个开关，没有别的东西能让方向变 RTL** —— 所以「回读 1」只证明了 Settings 那一行，没证明应用拿到的方向。本轮尝试离线确认框架读取点：`curl` 到 raw.githubusercontent 失败（网络阻断）、WebFetch 该 `Resources.java` 返回 NOT FOUND（大文件截断，不能当反证）、官方 RTL 文档页无该命令、GitHub 代码搜索对该仓库返回 0 —— **AOSP 侧机制本轮未证实，故 H6 只作为假设登记，不写成结论** |

## 5. SEAM REQUEST（根因不在我所有的文件里，需他人落地）

**SR-D1｜RTL 生效性的设备侧权威 witness（属主：真机验收脚本 / commands.log 模板）**
下一次竖屏验收在 RTL 窗口内**必须同时**采集三条，缺一不可：

```
adb -s <serial> shell getprop debug.force_rtl              # 系统属性载体
adb -s <serial> shell settings get global debug.force_rtl  # Settings 载体
adb -s <serial> shell dumpsys activity top | grep -iE "curConfig|mGlobalConfiguration"
                                                            # 应用真正拿到的方向（看 ldltr / ldrtl）
```

第三条是**唯一**能证明「方向确实进了这个进程的配置」的证据。若前两条不一致，或第三条出现 `ldltr`，则 H6 直接成立，**与 `CraSlider` 无关，滑杆无需改一行**。另需一条**不依赖 force_rtl 的正向镜像 witness**：把系统语言切到阿语/希伯来语（走 locale 派生方向）后，确认 §2.2 那两个 `Row` 的节点顺序发生反向 —— 这能把「MIUI 是否支持强制 RTL」和「应用是否支持镜像」彻底解耦。

**SR-D2｜Compose 层 RTL 渲染测试（属主：`androidTest`，非我所有权）**
本仓 `androidTest/` 里 `grep LayoutDirection` 与 `grep CraSlider` **均为 0 命中** —— 现役没有任何「真的进入 RTL 方向渲染」的测试。建议新增一条 androidTest：`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` 下渲染真实 `CraSlider`，断言拇指节点的 x 与轨道已走段方向同侧（这才是把 §2.3 的产物取证变成运行时事实的地方）。我没有在 JVM 侧伪造这条 —— 它需要设备/Robolectric 环境，硬凑会写成自证。

**SR-D3（可选）｜应用内方向可见性（属主：设置页/关于页）**
若 SR-D1 之后仍未定，考虑在诊断信息里加一行只读的「当前布局方向」（读 `LocalLayoutDirection.current`）。仅为缩短排查回路，非必需。

## 6. 仍需真实竖屏设备验证的项目

1. **H6**：三条 witness（`getprop` / `settings get` / `dumpsys activity top` 的 `ldrtl|ldltr`）在 force_rtl 窗口内的实际取值。
2. **应用能否镜像**：locale 切阿语/希伯来语后 `Row` 顺序是否反向（不依赖 debug 属性）。
3. **拇指与已走段边缘在 RTL 下是否贴合**：`computeCraSliderTrackGeometry` 只在**画布自身**坐标系里镜像，而 M3 拇指按容器宽镜像 —— 两者同一中轴的前提是 M3 对 `track` 槽左右对称留白。这一点离线只能取到「M3 无图层翻转 + 算术镜像」，**留白对称性未取证**，需真机目视（LTR 已在上轮验过视觉正常）。
4. **滑动方向语义**：更正报告 §4.4 指出上一轮「滑动操作与轨道延伸无任何反向撕裂」的说法**没有对应的滑动动作记录**（只做过 tap）—— RTL 下左右拖动是否让数值向正确方向变化仍未实测。
5. 阅读器 `ReaderChrome` 底栏与全书进度条在 RTL 下的镜像（同样只有未镜像的截图）。

## 7. 本轮边界

- 设备：零操作（无 adb、无手机、无模拟器、无用户书库、无设备设置）。
- Gradle：只跑了 brief 允许的**定向** JVM 测试一条命令，`--offline`，退出码 0。未跑全量单测、未跑 Lint、未构建 APK。
- git：未 stage / commit / push / reset / checkout / clean / stash；HEAD 与 staged 未变。
- 所有权：只写 `CraSliderTest.kt` 与本报告；`CraSlider.kt` 主动保持原样；越界部分以 §5 SEAM REQUEST 形式提出，未动他人文件。
