# S06-⑦ RTL 布局下滑杆轨道/拇指/停点方向一致性 · 当前状态核对（2026-09-27，窗口 D · 只读）

- 报告时间：2026-09-27。工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；HEAD `5aed5939600e491843e3c44453921bd6273a233c`（与账本一致）。
- 核对性质：**只读**。未运行 Gradle / ADB / MuMu，未操作任何设备；未触碰 `CraSlider.kt`、`CraSliderTest.kt`、`CraSliderRtlRenderTest.kt`、主题与公共组件；无 git 写操作。本轮唯一写入 = 本报告。
- 实际模型：GLM 5.3 Flash（`account:zai-start-plan/GLM-5.3-Flash`）。全程可用、无错误，未触发 DeepSeek V4.1 Flash 备选。
- 输入依据：仓库 `AGENTS.md`、[2026-09-22-current-task-ledger.md](../../decisions/2026-09/2026-09-22-current-task-ledger.md)、[agent-next-d-s06-rtl-slider.md](../../dispatches/undated/agent-next-d-s06-rtl-slider.md)、[2026-09-24-d-rtl-root-cause.md](../../../qa/2026-09/2026-09-24-d-rtl-root-cause.md)、[2026-09-24-d-rtl-compose-test.md](../../../qa/2026-09/2026-09-24-d-rtl-compose-test.md)。历史报告仅作线索；以下结论以**当前文件哈希、关键行号与原始证据的一手核对**为准。

## 0. 结论一览

| 维度 | 判定 |
|---|---|
| 代码：激活段/停点/描边与 M3 拇指的方向、数值比例一致 | **可确认**（同一 `fraction`、同一 `isRtl` 分支；三文件与 09-24 留档哈希逐字节一致，零漂移） |
| 测试：端点/中间值/窄激活段/越界/RTL 镜像/画布包含 | **可确认覆盖内容**；09-24 真实运行 8/8，但 XML 已被 09-26 02:20 共享批次覆盖，当前工作树不可回读 |
| 设备/视觉：RTL 下真实渲染 | **未验证**。09-23 `d_rtl_*` 截图经本轮亲手目视确认为整页 LTR、无镜像，维持「不得记 RTL 视觉 PASS」 |
| 拇指圆心与激活段边缘的像素贴合 | **未验证**（M3 对 track 槽左右留白对称性未取证，既有 SR-2 / 账本③） |
| androidTest `CraSliderRtlRenderTest`（4 条） | 已编译（09-24 exit 0）、**从未运行** |

## 1. 当前代码可确认

### 1.1 文件身份与零漂移

| 文件 | git 状态 | sha256（本轮重算） | 与 09-24 报告留档 | mtime |
|---|---|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/components/CraSlider.kt` | `??` 未跟踪 | `678cc5e9118bca0b2410f9765c90089a66889b694fbfb216f40bbb0ec9c6b9b4` | 一致 | 09-23 22:12 |
| `android/app/src/test/java/com/creationreadingassistant/ui/components/CraSliderTest.kt` | `??` 未跟踪 | `acc79af47bdd456f381a046648a7df9aa4aa117e7bf899e508feb3a758c4fa49` | 一致 | 09-24 00:59 |
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/components/CraSliderRtlRenderTest.kt` | `??` 未跟踪 | `5017af8de3ca361145f126c48b018fc547e3a8656ef034e373d176f0a212aa46` | 一致（4 条 `@Test`） | 09-24 |

→ 09-24 之后三个文件零漂移，本轮按当前内容逐行复核，可独立背书（非沿用旧报告）。

### 1.2 方向入口唯一、无覆盖、无钉死（本轮一手重扫）

- 轨道方向唯一来源：`CraSlider.kt:103-104`，track 槽内 `LocalLayoutDirection.current == LayoutDirection.Rtl`。
- 本轮一手 grep 重扫（与 09-24 报告 §2.1 结论一致）：`android/app/src/main` 的 Kotlin 源码中 `setLayoutDirection / LAYOUT_DIRECTION / createConfigurationContext / updateConfiguration / setLocale / LocaleList / AppCompatDelegate` **零命中**；`res/**/*.xml` 中 `layoutDirection / textDirection` **零命中**；`CompositionLocalProvider` 对 `LocalLayoutDirection` 的覆盖 **零命中** —— 应用侧没有任何断点或覆盖。
- `android/app/src/main/AndroidManifest.xml:32`：`android:supportsRtl="true"`。

### 1.3 同一数值比例

- `CraSlider.kt:111-112`：`fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)`（`span` 有 `1e-6f` 下限保护）；该 `value`/`valueRange` 就是传给 M3 `Slider` 的同一组参数（`CraSlider.kt:82-88`）→ **轨道激活段、停点、描边与拇指共用同一个 fraction、同一条比例映射**，不存在第二条数值来源。
- M3 侧拇指为算术镜像（09-24 报告 §2.3 对实际参与构建的 material3 **1.3.1** 产物 javap：`SliderState.onPress` 在 `isRtl` 时取 `totalWidth - x`；无 `graphicsLayer/scaleX` 图层级翻转）→ `CraSlider` 在 track 槽自行镜像与 M3 拇指是**同向互补**，不存在双重镜像。

### 1.4 三段几何的镜像一致性（`computeCraSliderTrackGeometry`，`CraSlider.kt:186-230`）

| 元素 | LTR | RTL | 行号 |
|---|---|---|---|
| 激活段 | `[0, effW]` | `[W - effW, W]`（右缘恒贴 W） | :198-203 |
| 停点圆心 | `(W - 0.55h, h/2)` | `(0.55h, h/2)`；显隐只随 `fraction < 1`（与方向无关） | :205-210 |
| 描边 | 左起 `-inset`，宽 `activeW + 2·inset` | `W - activeW - inset`，同宽（镜像精确闭合） | :212-218 |
| 退化保护 | `activeW < h` 时短条取 `effW = h`，LTR 靠左 / RTL 靠右，无负坐标 | 同左 | :194-196 |
| 绘制 | 停点半径 `0.45h`，圆心取几何结果 | | :144-145 |

锚点复核（与 09-24 报告 §2.4 相符）：W=1000、h=20、inset=1.5、f=0.4 ⇒ 激活段 LTR `[0,400]` / RTL `[600,1000]`；停点 `989 ↔ 11`；描边 `[-1.5, 401.5] ↔ [598.5, 1001.5]`；f=0 与 f=1 端点精确闭合。

### 1.5 代码层证明不了的事

「拇指圆心与激活段边缘像素级贴合」取决于 M3 给 track 槽的左右留白是否对称（09-24 R5 / SR-2 / 账本③）。代码审查只能确认**值域同比例 + 画布坐标系内自镜像**；M3 拇指行程若带内缩，交界在中间 fraction 可有极小错位——这只能设备目视或断言化（SR-2）解决。

### 1.6 P3 备注（非缺陷，仅登记）

`strokeInsetPx` 默认值 `1f`（`CraSlider.kt:191`）量纲是**像素**，而生产调用点恒传 `1.dp.toPx()`（:129）；JVM 测试传显式浮点。当前无实际影响；若未来新调用点省略该参数，内缩会退化为 1px 而非 1dp。属主 `CraSlider.kt`，本轮不改。

## 2. 测试可确认（`CraSliderTest.kt`，8 条 `@Test`）

### 2.1 覆盖矩阵（用例行号：24 / 54 / 88 / 113 / 138 / 165 / 196 / 241）

| # | 用例 | 端点 | 中间值 | 窄宽度 | RTL |
|---|---|---|---|---|---|
| 1 | `ltr track geometry starts from left and places stop dot at right`（:25） | — | f=0.4 逐点 | — | — |
| 2 | `rtl track geometry starts from right and places stop dot at left`（:55） | — | f=0.4 逐点（含右缘贴 W） | — | ✅ |
| 3 | `zero fraction produces no active track and preserves directional stop dot`（:89） | **f=0**（LTR+RTL） | — | — | ✅ |
| 4 | `full fraction covers entire track and removes stop dot`（:114） | **f=1**（LTR+RTL） | — | — | ✅ |
| 5 | `tiny non zero fraction degenerates to minimum pill without negative coordinates`（:139） | — | f=0.005 | **激活段(5px) < 轨道高(20px) 退化短条**，LTR+RTL、无负坐标 | ✅ |
| 6 | `fraction clamp protects against out of range values`（:166） | 越界 **-0.5 / 1.5** 夹紧 | — | — | ✅ |
| 7 | `rtl geometry is the exact mirror of ltr across the whole fraction range`（:197） | 0 与 1 含内 | **f∈[0,1] 步长 1% 共 101 点**：三绘制标志同真同假、尺寸方向无关、三段水平区间逐点镜像 `rtl.x == W − (ltr.x + ltr.width)` | 含退化区间 | ✅ |
| 8 | `rtl drawing stays inside the canvas for every fraction`（:242） | 同上 | 101 点：激活段与停点圆（半径 0.45h）不越 `[0, W]` | — | ✅（RTL 专项） |

- **端点**：0 与 1，均 LTR+RTL 双向 ✅。**中间值**：0.4 逐点 + 101 点全区间扫描 ✅。**窄宽度**：激活段窄于轨道高的退化已覆盖 ✅；**「总宽度极窄」（totalWidth 本身很小）未单列**——现有测试全部用 W=1000。
- **测试不覆盖（如实登记）**：M3 拇指实际 x 位置（JVM 纯几何测试无 Compose 运行时，拇指一致性由 1.3 的 javap 静态取证 + 未运行的 androidTest 承担）；真机 RTL 渲染；`steps > 0` 外观；交界贴合；滑动语义。

### 2.2 运行证据现状

- 09-24 报告记录过**真实定向运行**：`--offline :app:testDebugUnitTest --tests …CraSliderTest --rerun`，BUILD SUCCESSFUL、exit 0，XML `tests="8" failures="0" errors="0" skipped="0"`。
- 本轮查验当前 `android/app/build/test-results/testDebugUnitTest/`：已是 **09-26 02:20 批次**（17 份 XML：`BookRepository*` / `Shelf*` / `SearchIndex*` 等），**无 `CraSliderTest.xml`**（binary 目录不可回读明细）。
- ⇒ **8/8 目前只有报告口径，工作树内不可回读**。与账本 09-24 对「共享构建目录会覆盖 JVM 结果 XML」的登记同型；今后需留证的定向运行必须当场复制/哈希 XML。

## 3. 视觉未验证（含旧截图一手核对）

### 3.1 旧 `d_rtl_*` 截图判定：维持「未镜像反证」，不得写 RTL 视觉 PASS

- 开关证据链：`docs/qa/evidence/2026-09-23-bcd-portrait/commands.log:25,131-132,152-153` —— 对 `debug.force_rtl` 的全部操作只有 `settings get/put global`；**全程无 `getprop`、无任何 `ldrtl/ldltr` 配置输出**（H6「开关未抵达框架读取层」仍只是未证实假设）。「回读为 1」只证明 Settings 那一行，不证明应用拿到的方向。
- 本轮亲手目视 `docs/qa/evidence/2026-09-23-bcd-portrait/redacted/d_rtl_craslider.png`（23:37:37，`force_rtl=1` 窗口内）：返回箭头居左、「0 分钟 (关闭) → 90 分钟 → 180 分钟」自左向右、预设档位 关闭→90 自左向右、精细调节滑杆激活段自**左侧**生长、停点在**右**端 —— **整页 LTR 次序，无任何镜像**。
- 本轮亲手目视并 2x 裁剪放大 `redacted/d_rtl_reader_chrome.png`（23:37:54）：顶栏返回箭头/标题居左、正文自左向右、底栏 目录→听书→灵感→外观→设置 自左向右、全书进度滑块（`ReaderChrome.kt:375` 的 `CraSlider`，`valueRange = 0f..100f`）激活段自左侧生长、拇指在右端（≈100%；停点按 `fraction < 1` 规则隐藏/消失，与代码一致）。缩略目视曾疑轨道左端有附加小圆，裁剪放大后确认为激活段圆头端帽，非额外元素。
- 「内容区与 LTR 差分 0.0000%」为更正报告（`2026-09-23-bcd-evidence-correction.md` §4.2）的历史像素测定，本轮未重跑差分；本轮以亲手目视 + 裁剪放大独立佐证同一结论。
- ⇒ 结论口径与 09-24 账本完全一致：这两张图证明的是**当时未镜像**，不是「镜像正确」；且因方向是否抵达进程未证，**也不构成**「force_rtl 已生效但应用未镜像」的定论。**RTL 视觉结论维持：未验证。**

### 3.2 全部仍开放的视觉/设备项

1. **H6 三 witness**（`getprop debug.force_rtl` / `settings get global …` / `dumpsys activity top` 的 `curConfig` 中 `ldrtl|ldltr`）——第三条是唯一能证明方向进入应用进程的证据。
2. **locale 正向镜像 witness**：系统语言切阿语/希伯来语后，页面级 `Row`（刻度标签、预设档位）是否反向（不依赖 debug 属性，能把「系统能否强制 RTL」与「应用能否镜像」解耦）。
3. **交界贴合**：拇指圆心 vs 激活段边缘（M3 track 槽留白对称性，SR-2）。
4. **RTL 滑动语义**：09-23 只有 tap 记录，无 drag；左右拖动数值变化方向未实测。
5. `ReaderChrome` 底栏 / 全书进度条在 RTL 下的镜像（现存截图均为未镜像状态）。
6. `CraSliderRtlRenderTest` 4 条（测试自注入 `LocalLayoutDirection`，不依赖设备设置）：已编译（09-24 `compileDebugAndroidTestKotlin` exit 0，javap 取证 4 个用例在产物内），`connectedDebugAndroidTest` **从未执行**；真机 instrumentation 受 MIUI 拦截（SR-ENV-1 未裁定）。

## 4. 最小下一步：MuMu 最小可区分验证轮计划（仅交计划，本棒未操作设备）

> 授权口径：仓库 `AGENTS.md` 默认「UI/设备行为只在真机验证、不使用 MuMu」；09-27 账本增量记录**用户明确允许 MuMu 继续设备验证**，本轮任务书亦指定 MuMu。按更近授权执行并在此留痕；如总指挥认为需再确认，本计划可原样搬到真机执行（witness 命令完全相同）。

### 4.1 设置基线（动手前记录 = 恢复锚）

先 `adb devices` 固定 serial，所有命令 `adb -s <serial>`：

1. `getprop ro.product.model`、`ro.build.version.release`（MuMu 身份与 Android 版本，决定有无「强制 RTL」开发者开关）。
2. `getprop persist.sys.locale`、`getprop ro.product.locale`（预计 `zh-CN`）。
3. `settings get global debug.force_rtl`（预计 0）。
4. `settings list global | grep -iE "rtl|locale"` —— 不预设键名，取该镜像上 RTL 相关键的实际清单（顺手即是对 H6 的补证）。
5. `settings get system stay_on_while_plugged_in`（若临时启用，记录原值）。
6. 应用侧：只进入设置/阅读器界面，不开书、不导入、不触碰书库数据。

### 4.2 方向注入（两条独立通路，分开做、分开截图）

- **主通路（不依赖 debug 属性）**：系统设置 UI 切语言到 **العربية（ar）或 עברית（he）**，走 locale 派生方向。不要只改 `system_locales` 单键（不同镜像生效层级不一）；改完必须回读 `getprop persist.sys.locale` 确认已变。
- **辅通路（复现旧缺陷条件 + H6 补证）**：开发者选项「强制 RTL 布局方向」（若该镜像有）或 `settings put global debug.force_rtl 1`；无论哪条，**当场采齐 SR-D1 三 witness**：
  1. `getprop debug.force_rtl`（系统属性载体——历史上从未采过）；
  2. `settings get global debug.force_rtl`（Settings 载体）；
  3. `dumpsys activity top | grep -iE "curConfig|mGlobalConfiguration"` —— 看 `ldrtl`/`ldltr`，**唯一**能证明方向进入应用进程的证据。

### 4.3 方向确认方法（三层证据链，缺一不得判 PASS）

1. **进程级**：第 3 条 witness 出现 `ldrtl`（或 locale witness：`persist.sys.locale` 为 ar/he 且系统设置首页明显镜像）。
2. **Row 级**：阅读目标页刻度标签（「0 分钟 (关闭)」/「180 分钟」）与预设档位顺序必须与 09-23 的 LTR 记录（x=83→1018，见 `screenshot-manifest.md:158`）**反向**。
3. **滑杆级**：精细调节滑杆在低/高两档下，激活段紧贴**右**缘、随值向**左**生长、停点在**左**端。

任何一环只拿到「`settings put` 回读 1」而无 ldrtl/locale 证据 ⇒ 维持「未验证」，不得写 RTL PASS。

### 4.4 截图与差分（最小可区分集）

- **LTR 基线**：切回原 locale 后，同一页面、同两档值（低≈20%、高≈80%）各一张（**同轮新拍**，不复用旧图）。
- **RTL**：同页面同两档值各一张 + 系统设置首页一张（系统镜像 witness）。
- 每张同窗保存 witness 文本输出（`getprop` / `settings list` grep / `dumpsys` grep）。
- 判定：RTL 与 LTR 同值图做逐像素差分（沿用 09-23 更正报告 §4.2 的差分做法）——**差分为 0 ⇒ 未镜像 ⇒ 不 PASS**；差分非零且方向符合 4.3-3 ⇒ 方可记「RTL 视觉 PASS（MuMu 范围）」。
- 可选加分项（不阻塞）：MuMu 上若 instrumentation 可运行，跑 `CraSliderRtlRenderTest` 4 条（注意 09-24 报告 R1–R3 风险；SR-1/SR-2 涉及产品文件，属主不在本棒）。

### 4.5 恢复步骤

1. 语言切回 `zh-CN`（Settings UI），`getprop persist.sys.locale` 回读确认。
2. 若动过 force_rtl 类键：按 4.1-4 记录的原值恢复（原为 0 则 `settings put global debug.force_rtl 0`，或 `settings delete global <key>`），`settings get` 回读确认。
3. 重启应用（`am force-stop` + 启动）确认回到 LTR；`stay_on_while_plugged_in` 若动过恢复原值。
4. 不 `pm clear`、不删书、不改进度；证据落 `docs/qa/evidence/<日期>-s06-rtl-mumu/`，沿用 `d_*` 命名与脱敏规则，报告与截图说明只写中性名称。

## 5. SEAM REQUEST

**本轮无新增跨文件需求。** 既有 SR-D1（设备侧 witness 模板）、SR-D2（androidTest RTL 渲染——已由 `CraSliderRtlRenderTest.kt` 落文件但未运行）、SR-1/SR-2（属主 `CraSlider.kt` 的 testTag / track 槽留白断言）维持原状，属主与内容以 09-24 两份报告为准。本轮全部结论都在 D 既有所有权文件与既有证据范围内。

## 6. 边界声明

- 只读核对：未运行 Gradle / ADB / MuMu，未操作设备；唯一写入 = 本报告。
- 未触碰 `CraSlider.kt`、`CraSliderTest.kt`、`CraSliderRtlRenderTest.kt`（三者哈希与 09-24 留档比对无漂移）、主题、公共组件及任何他窗在途改动。
- 无 git 写操作：未 add / commit / push / reset / checkout / clean / stash；HEAD 仍 `5aed5939…`；三个 Kotlin 文件保持未跟踪（`??`）。
- 分析用临时裁剪图仅写系统 Temp（`d_rtl_chrome_crop.png`），未进仓库；截图描述遵守中性命名，不含真实书名与私有路径。
