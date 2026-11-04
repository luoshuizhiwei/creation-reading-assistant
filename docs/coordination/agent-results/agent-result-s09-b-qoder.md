# S09-B 结果报告（窗口 C / Qoder）— 统一分页引擎、legacy EPUB 与设置预览的点击区域几何事实源

- 任务：S09-B｜切片名：统一点击热区几何事实源
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜比较基点：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
- 完成时间：2026-09-22 02:46（本机 +08:00）
- 结论：**几何事实源已收敛到 `ReaderTapZoneGeometry`，三处消费端全部改读它；34 个定向 JVM 用例、主 Kotlin 编译、androidTest 编译全部通过；androidTest 未执行（见第十三节）**

---

## 一、修复摘要

修复前同一个「屏幕点击区域」设置在三个地方被表达成三套不同的几何：

| 消费端 | 修复前的横向分区 | 修复前的五分区纵向 |
|---|---|---|
| 自研分页引擎 `resolveReaderTapAction` | 内联 `width / 3f`、`width * 2f / 3f`（= 33/33/33） | 内联 `height * 0.12f` / `height * 0.88f`（= 12/76/12） |
| legacy 分页 EPUB `PagedEpubContent` | `weight(0.16f) / 0.68f / 0.16f` | `0.12f / 0.76f / 0.12f` |
| 设置预览卡 `TapZonePreviewCard` | 三分区 `0.32/0.36/0.32`；五分区中带 `0.30/0.40/0.30` | `0.24 / 0.52 / 0.24` |

后果：用户在设置里看预览卡学到的热区，既不等于分页引擎的真实热区，也不等于 EPUB 分页的真实热区；同一本书在「自研分页」与「legacy EPUB 分页」之间手感不同（左右热区 16% ↔ 33%）；任何比例调整要改三处，漏一处就静默分叉（S09 盘点里的 SN-2 / G4）。

本片做了四件事：

1. 在 `feature/reader/pager/ReaderTapAction.kt` 新增 `object ReaderTapZoneGeometry`，作为横向/纵向占比的**唯一生产事实源**，并对外提供 `horizontalWeights` / `verticalWeights`（直接喂 `weight()`）与 `centerLeftEdge/centerRightEdge/topBandBottomEdge/bottomBandTopEdge`（直接喂命中判断）。
2. `resolveReaderTapAction` 内联的 4 个比例字面量全部改为读共享几何；函数体内不再对视口尺寸做任何乘法（由守卫断言）。
3. `PagedEpubContent` 的 9 处 weight 字面量（横向 `0.16f/0.68f/0.16f` 两套 = 6 处，纵向 `0.12f/0.76f/0.12f` 一套 = 3 处）改为解构共享 `Triple`；**保留**它自己的 `isLeftAdvance/isRightAdvance/leftCan/rightCan/leftAction/rightAction` 派发层，没有重写成 `pointerInput` 架构，也没动无障碍描述。
4. `TapZonePreviewCard` 的 9 个私有比例改为共享几何；顶/底带改为只画箭头图标；`both_next` 的说明文案按 `tapZoneMode` 分叉（三分区不再谎称有「顶部热区」）。

产品语义未改：三种预设的方向、中央唤出控制栏、五分区顶=上一页/底=下一页、`canPrevious=false`→`NONE`（不会改判成下一页）、边缘 deadzone 全部保持。

## 二、根因

不是笔误，是**缺少单一事实源**导致的三份并行实现：

- `ReaderTapAction.kt` 是纯函数，比例写死在 `when` 的条件里；`PagedEpubContent` 是 Compose `Box + weight()`，比例写死在 Modifier 里；预览卡又是另一套 `weight()`。三者的表达形式不同，**没有任何机制强迫它们相等**，所以每次新增一种渲染分支或重画预览，就再抄一份数字。
- 预览卡是「画给人看的示意图」，历史上按视觉好看调成 0.32/0.36/0.32 与 0.24/0.52/0.24，而不是按真实热区；S09 盘点已记录它两个真实实现都不匹配。
- 12% 这个值在引擎里是「带高」，在预览卡里被画成 24%，视觉上调高了顶/底带 —— 用户按预览点击会落在中带（唤出控制栏），与预期相反。

因此修复方式不是"把三处数字对齐"，而是**让三处引用同一个常量对象**，并用行为探针（不是只做字面量比对）证明引擎真的按它分区。

## 三、修改文件清单

| # | 文件（相对仓库根） | git 状态 | 行数 | SHA-256（前 12） |
|---|---|---|---|---|
| 1 | `android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt` | M | 120 | `7f1ed408e3f2` |
| 2 | `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt` | M | — | `b86c19b4ed54` |
| 3 | `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt` | `??`（未跟踪，非本片创建） | 318 | `294039fde268` |
| 4 | `android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt` | `??`（**本片新建**） | 197 | `f3c098a59811` |
| 5 | `android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionTest.kt` | M | — | `edddb6d3ed13` |
| 6 | `android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt` | `??`（未跟踪，非本片创建） | — | `f7a83478b0dd` |
| 7 | `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt` | M | 443 | `da11a2f8e7b7` |

完整 SHA-256 见文末附录 A。文件 3、6 是工作区里**其他 Agent 留下的未跟踪文件**，本片在其中修改了内容 —— 因为未跟踪，`git diff` 看不到这些改动（见第十二节的特别说明）。

本片为版本库新增的文件只有 1 个：`ReaderTapZoneGeometryTest.kt`。另有 13 个 Gradle 运行日志写在 `android/` 根目录，被 `android/.gitignore:17` 的 `*.log` 规则忽略、不进版本库（清单见第十四节 P3-4）。

## 四、统一几何契约

`com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometry`（`ReaderTapAction.kt:41-76`）：

| 成员 | 值 | 含义 |
|---|---|---|
| `SIDE_WIDTH` | `1f / 3f` | 左/右侧区宽度占比 |
| `CENTER_WIDTH` | `1f / 3f` | 中央区宽度占比 |
| `TOP_HEIGHT` | `0.12f` | 五分区顶部带（上一页） |
| `BOTTOM_HEIGHT` | `0.12f` | 五分区底部带（下一页） |
| `MIDDLE_HEIGHT` | `1f - TOP_HEIGHT - BOTTOM_HEIGHT` = `0.76f` | 五分区中带 |
| `horizontalWeights` | `Triple(SIDE_WIDTH, CENTER_WIDTH, SIDE_WIDTH)` | 左/中/右，直接喂 `RowScope.weight()` |
| `verticalWeights` | `Triple(TOP_HEIGHT, MIDDLE_HEIGHT, BOTTOM_HEIGHT)` | 顶/中/底，直接喂 `ColumnScope.weight()` |
| `centerLeftEdge(w)` | `w * SIDE_WIDTH` | 左区/中央区界 |
| `centerRightEdge(w)` | `w * (SIDE_WIDTH + CENTER_WIDTH)` | 中央区/右区界 |
| `topBandBottomEdge(h)` | `h * TOP_HEIGHT` | 顶带/中带界 |
| `bottomBandTopEdge(h)` | `h * (1f - BOTTOM_HEIGHT)` | 中带/底带界 |

契约由 `ReaderTapZoneGeometryTest` 断言：横向之和 = 1、纵向之和 = 1（`1e-4` 容差）、每个占比 `> 0` 且 `< 1`、侧区与中央区等宽、顶/底 = 12%、中带 = 76%。

**声明顺序约束**：该 object 写在 `resolveReaderTapAction` **之前**。原因见第九节（首轮守卫被自己的 KDoc 反向咬过一次），已在 KDoc 里注明。

## 五、行为矩阵（three-zone / five-zone × standard / left_hand / both_next）

探针坐标：left=`x 100`、center=`x 450`、right=`x 800`、top=`y 50`、bottom=`y 950`（视口 900×1000，五分区顶/底带为 y<120、y>880）。

| tapZoneMode | preset | 左区 | 中央区 | 右区 | 顶部带 | 底部带 |
|---|---|---|---|---|---|---|
| three-zone | `standard` | PREVIOUS_PAGE | TOGGLE_CONTROLS | NEXT_PAGE | 不存在 | 不存在 |
| three-zone | `left_hand` | NEXT_PAGE | TOGGLE_CONTROLS | PREVIOUS_PAGE | 不存在 | 不存在 |
| three-zone | `both_next` | NEXT_PAGE | TOGGLE_CONTROLS | NEXT_PAGE | 不存在 | 不存在 |
| five-zone | `standard` | PREVIOUS_PAGE | TOGGLE_CONTROLS | NEXT_PAGE | PREVIOUS_PAGE | NEXT_PAGE |
| five-zone | `left_hand` | NEXT_PAGE | TOGGLE_CONTROLS | PREVIOUS_PAGE | PREVIOUS_PAGE | NEXT_PAGE |
| five-zone | `both_next` | NEXT_PAGE | TOGGLE_CONTROLS | NEXT_PAGE | PREVIOUS_PAGE | NEXT_PAGE |

矩阵整体由 `ReaderTapActionPresetTest.fullPresetByZoneModeMatrix` 驱动（6 用例 × 各自 3~5 个区）；`bothNextThreeZoneHasNoTapBackPathByDesign` 额外锁死"三分区 both_next 任何位置都不得返回 PREVIOUS_PAGE"这一既有产品语义（本片**没有**新增回退手势）。legacy EPUB 的 `isLeftAdvance / isRightAdvance` 映射与上表方向一致，未改动。

## 六、三处 before → after

**1. 自研分页引擎 `ReaderTapAction.kt:98-115`**

```kotlin
// before
tapZoneMode == "five-zone" && y < height * 0.12f        -> PREVIOUS_PAGE
tapZoneMode == "five-zone" && y > height * 0.88f        -> NEXT_PAGE
x < width / 3f        -> when (preset) { ... }
x > width * 2f / 3f   -> when (preset) { ... }
// after
tapZoneMode == "five-zone" && y < ReaderTapZoneGeometry.topBandBottomEdge(height)    -> PREVIOUS_PAGE
tapZoneMode == "five-zone" && y > ReaderTapZoneGeometry.bottomBandTopEdge(height)    -> NEXT_PAGE
x < ReaderTapZoneGeometry.centerLeftEdge(width)   -> when (preset) { ... }   // when 分支原样保留
x > ReaderTapZoneGeometry.centerRightEdge(width)  -> when (preset) { ... }
```
命中范围**没有变化**（0.12/0.88 与 1/3、2/3 就是原值），变化是"值来自哪里"。

**2. legacy 分页 EPUB `PagedEpubContent.kt:239-240 + 9 处 weight**

```kotlin
// before：three-zone = weight(0.16f) / weight(0.68f) / weight(0.16f)
//         five-zone  = weight(0.12f) / weight(0.76f) / weight(0.12f)，中带内 0.16/0.68/0.16
// after
val (leftWeight, centerWeight, rightWeight) = ReaderTapZoneGeometry.horizontalWeights  // 1/3 : 1/3 : 1/3
val (topWeight, middleWeight, bottomWeight) = ReaderTapZoneGeometry.verticalWeights    // 0.12 : 0.76 : 0.12
```
**这是本片唯一改变真实手感的地方**：EPUB 分页的左右热区从各 16% 变成各 33.3%，中央唤出区从 68% 缩到 33.3%。这是任务要求的收敛目标（"最终契约：左/中/右各 1/3"），不是副作用；纵向本来已是 12/76/12，未变。派发层（`leftAction/rightAction/leftCan/rightCan`、`clickable(enabled=…)`、图标与 `contentDescription`）逐行保留。

**3. 设置预览卡 `TapZonePreviewCard.kt:72-73 + 9 处 weight**

```kotlin
// before：五分区纵向 0.24/0.52/0.24，中带横向 0.30/0.40/0.30；三分区横向 0.32/0.36/0.32
// after：横向一律 horizontalWeights(1/3,1/3,1/3)，纵向一律 verticalWeights(0.12,0.76,0.12)
```
两处必要的配套调整（都源于比例改变，不是重新设计）：
- 顶/底带改为**只画箭头图标、不画文字**（`showLabel = false`，图标 13dp→10dp）。12% 的带高在 98dp 的示意图里只有约 10dp，放 10sp 文字会在系统字体放大时被裁；箭头方向已足够表达动作，文字标签保留在中带与三分区。微屏高度 98dp、配色、圆角、动画均未改。
- 各分区容器加 `Modifier.testTag(...)`（5 个 `internal const val TAP_PREVIEW_TAG_*`）。这是给 androidTest 量**容器真实边界**用的钩子：`onNodeWithText` 拿到的是文字行盒，不是热区，用它量宽度比例是假测量。testTag 不影响视觉与无障碍语义。
- `both_next` 提示文案改为按模式分叉：
  - 三分区：`💡 已开启双向推进：屏幕左右两侧均响应下一页，回退可通过滑动手势。`（原来无论什么模式都写「或顶部热区」）
  - 五分区：`… 回退可通过滑动手势或顶部热区。`
  - `left_hand`：`💡 左手模式：点击左侧区域翻下一页，右侧区域回退上一页，专为左手单手握持优化。`（原「左侧大区域」在等宽三分区下不成立）

## 七、canPrevious / canNext 与 deadzone 语义保持

| 语义 | 保持方式 | 证据 |
|---|---|---|
| 首页点「上一页」不得伪装成成功翻页 | `requested` 计算后统一用 `if (canPrevious) requested else NONE`，本片未碰这段 | `ReaderTapActionTest.boundaryPagesReturnNoneSoNoTurnIsReported`（既有）、`ReaderTapActionPresetTest.boundaryProtectionWorksWhenPagesUnavailable`（既有） |
| 末页点「下一页」同理 | 同上 | 同上 + `unavailablePageDoesNothingInsteadOfHidingControls` |
| 五分区顶/底带同样受可用性抑制，且**不会改判成另一方向** | 新增 3 预设遍历断言：首页顶部→NONE、末页底部→NONE，并带"可用时同坐标必须真翻页"的反向对照 | `ReaderTapActionPresetTest.fiveZoneTopAndBottomRespectPageAvailabilityAcrossPresets`（本片新增） |
| 边界页中央区仍可唤出控制栏 | 未改 | `boundaryPagesStillToggleControlsInTheCentre`（既有） |
| 边缘防误触 deadzone：吞掉侧区翻页、不吞中央、不吞成另一方向 | 顺序未改（deadzone 早于分区判定），补 3 条用例把这条语义钉住 | `ReaderTapActionTest.edgeDeadzoneSuppressesSideTurns` / `edgeDeadzoneStillLetsTheCentreToggleControls` / `edgeDeadzoneAlsoSuppressesFiveZoneTopAndBottomBands`（本片新增；前两条含同坐标无 deadzone 的反向对照） |
| legacy EPUB 边界页 | `clickable(enabled = canPrev/canNext)` 原样保留 | 未改（该片不重写派发层） |

## 八、虚假 androidTest 的处理方式与理由

**采用方案 A：改写为渲染真实生产热区组件并断言动作，而非删除。**

处理对象：`ReaderScreenTest.kt` 第 9 组 `clickable_area_responds_to_click()`（HEAD 中第 206 行，已提交进基线）。它自建 `Text("点击区域").testTag("clickable").clickable { clicked = true }`，然后 `onNodeWithTag("clickable").performClick()` 断言 `clicked` —— 点的是测试自己造的按钮，不触及 `resolveReaderTapAction`、`PagedReaderPageSurface`、`PagedEpubContent`、`TapZonePreviewCard` 任何一个，属于会制造「热区已覆盖」假象的占位测试。

为什么不选 B（删除并记录）：本片正好改动了预览卡，而预览卡是**唯一有真实分区容器可量**的组件。删掉就没有任何 Compose 侧用例约束"预览画的几何 = 共享几何"，回归只能靠源码字面量守卫（弱）。因此把 1 条占位用例换成 5 条真实渲染用例：

| 新用例 | 断言 |
|---|---|
| `previewCardThreeZoneDrawsEqualThirdsForStandardPreset` | 三分区左/中/右容器宽度比 ≈ 1:1:1（旧 0.32/0.36/0.32 会让中央宽 12%）；且**不存在**顶部带 |
| `previewCardFiveZoneUsesTwelvePercentBands` | 顶/底带高中枢 ÷ 中带高 ≈ 0.12/0.76（旧 0.24/0.52≈0.46 会被抓住）；中带横向仍等宽；纵向顺序正确 |
| `leftHandPreviewMirrorsThePresetActionMapping` | 左手模式下「下一页」标签整体落在左区容器、「上一页」落在右区、「菜单」在中央区 |
| `bothNextThreeZonePreviewMustNotPromiseATapBackZone` | 三分区 both_next：两个「下一页」、零个「上一页」、零个顶部带容器、说明文字**不含**「顶部」且**必含**「滑动」 |
| `bothNextFiveZonePreviewCanPointAtTheTopBand` | 对照组：五分区确实画出有实际高度的顶部带，此时说明提「顶部」才是事实 |

写第一版时我自己踩了两个坑，都在编译/复核阶段改掉，记录以免被当成"测量正确"：
1. 最初用 `onNodeWithText("上一页")` 的 `boundsInRoot` 量分区宽度 —— 那是**文字行盒**不是热区，"上一页"(3 字) 与 "菜单"(2 字) 天然不等宽，断言会假失败/假通过。改为给容器加 `testTag` 后量容器。
2. 最初写了一条 `node.boundsInRoot.left < node.boundsInRoot.right`（恒真）和一个基于 `SemanticsPropertyKey("Text")` 的 `getText()`（取不到文本）。全部删除，改为容器边界包含判断与 `onAllNodesWithText(...).size` 计数。

**必须同时说清的限制：这 5 条用例本片只做了 `:app:compileDebugAndroidTestKotlin` 编译验证，未在真机执行 instrumentation**（并行窗口 D 正在用同一台手机做 D-2-B 验收，且本任务明令不得操作手机；历史经验是 MIUI 会拦 `am instrument` 弹窗）。用例里的容差 0.05 是按 98dp/360dp 的像素推导留的余量，未经真机跑绿。这一点在第十、十三节重复标注。

## 九、RED → GREEN 证据（含两轮变异证伪）

所有命令都在 `android/` 目录执行，形态为
`.\gradlew.bat --console=plain <task> [--tests <FQCN>…] --rerun`，
通过 `powershell.exe -NoProfile -Command` 启动（`cmd.exe /c` 在 MSYS 下会给出假退出码，见记忆条目）。退出码取自 `$LASTEXITCODE`。

| 序号 | 阶段 | 结果 | 退出码 | 说明 |
|---|---|---|---|---|
| R1 | **RED**：`--tests` 三类（实现前） | `BUILD FAILED in 14s`；`:app:compileDebugUnitTestKotlin` 失败 | **1** | 全部失败原因是 `Unresolved reference 'ReaderTapZoneGeometry'`（几何测试引用了尚不存在的生产对象）。日志 `android/RED-1.log`（UTF-16LE） |
| G1 | 实现后首轮 | `BUILD FAILED in 39s`；编译失败 | **1** | 我自己的测试夹具 bug：`mapOf("left" to 100f to 500f)` 被解析成 `Map<Pair,Float>`，`ReaderTapActionPresetTest.kt:143` 解构失败。`android/GREEN-1.log` |
| G2 | 修夹具后 | `34 tests completed, 1 failed`，`BUILD FAILED in 17s` | **1** | 失败的是 `engineDoesNotInlineItsOwnCopyOfTheRatios`：我在 `ReaderTapAction.kt` 的 KDoc 里原样复述了守卫用的分界串，`substringAfter` 从注释处就切了，共享常量自己的 `0.12f` 被算进"函数体" → 守卫把干净文件判脏。`android/GREEN-2.log` |
| G3 | 修守卫 + 修 KDoc 后 | `BUILD SUCCESSFUL in 26s`，`34 tests completed, 0 failed` | **0** | `android/GREEN-3.log` |
| **M-1** | **变异 1**（证明行为探针真在钉引擎） | `34 tests completed, 3 failed`，`BUILD FAILED in 23s` | **1** | 变异内容：① 引擎 `x < ReaderTapZoneGeometry.centerLeftEdge(width)` 改回 `x < width * 0.16f`；② 预览卡横向改回 `Triple(0.32f, 0.36f, 0.32f)`。失败集 = `horizontalBoundaryMatchesSharedGeometry`(kotlin:93)、`fiveZoneMiddleBandStillUsesTheHorizontalZones`(:119)、`retiredMagicRatiosAreGoneFromTheTwoConsumers`(:143) —— 与预期完全一致。**附带发现**：`engineDoesNotInlineItsOwnCopyOfTheRatios` 没抓到①，因为它只禁 `width / 3f` 和 `0.12f`，绕得过 `width * 0.16f`。已据此加固（禁用函数体内一切 `width *` / `height *`）。`android/MUT-1.log` |
| **M-2** | **变异 2**（证明契约值与消费端守卫都不是空断言） | `34 tests completed, 5 failed`，`BUILD FAILED in 30s` | **1** | 变异内容：① 共享常量 `SIDE_WIDTH=0.16f`、`CENTER_WIDTH=0.68f`；② `PagedEpubContent` 删掉 `ReaderTapZoneGeometry` import、两处改回字面量 `Triple(0.16f,0.68f,0.16f)` / `Triple(0.12f,0.76f,0.12f)`，并把注释里的符号名一并去掉。失败集 = `horizontalZonesAreEqualThirds`、`centerZoneIsExactlyOneThirdOfTheWidth`、`sharedGeometryIsNotTheLegacySixtyEighteenSplit`、`legacyEpubAndPreviewCardReadTheSharedGeometry`、`retiredMagicRatiosAreGoneFromTheTwoConsumers` —— 与预期一致。注意 ①：横向之和仍为 1，所以 `horizontalWeightsSumToOneWithinTolerance` **没有**失败，这是设计使然（等宽与"和为 1"是两条独立约束）。`android/MUT-2.log` |
| F | **最终绿灯**（变异全部还原后） | `BUILD SUCCESSFUL in 17s`，`34 tests completed, 0 failed` | **0** | 见第十节 |

两轮变异都已完整还原，还原后经 grep 复核：`PagedEpubContent.kt` / `TapZonePreviewCard.kt` 中已无 `0.16f/0.68f/0.24f/0.52f/0.30f/0.40f/0.32f/0.36f`、无遗留 `Triple(…字面量…)`；`SIDE_WIDTH/CENTER_WIDTH` 回到 `1f / 3f`。最终证据是第十节那一份，**没有把失败 XML 留在最后**。

**RED 的性质要说明白**：R1 是编译期 RED（静态类型语言里"引用不存在的生产对象"就是失败测试），因此三个源码守卫在 RED 阶段并未真正执行过。它们"不是空断言"这件事是靠 M-1/M-2 变异补证的，不是靠 RED。

## 十、最终测试命令、退出码、数量与 XML

三条命令依次执行（各自独立 `gradlew` 调用，退出码单独记录）：

```
1) .\gradlew.bat --console=plain :app:compileDebugKotlin --rerun
   EXITCODE=0   BUILD SUCCESSFUL in 46s    日志 android/FINAL-main-compile.log
2) .\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun
   EXITCODE=0   BUILD SUCCESSFUL in 12s    日志 android/FINAL-androidtest-compile.log
3) .\gradlew.bat --console=plain :app:testDebugUnitTest \
     --tests com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest \
     --tests com.creationreadingassistant.feature.reader.pager.ReaderTapActionPresetTest \
     --tests com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometryTest \
     --rerun
   EXITCODE=0   BUILD SUCCESSFUL in 17s    日志 android/FINAL-tests.log
```

三条命令都在**变异还原之后**执行，所以这一份就是本片最终状态（新几何类已加入 `--tests` 清单，未删除该过滤器）。

最终 XML（目录 `android/app/build/test-results/testDebugUnitTest/`，同一批次，`timestamp` 为 UTC）：

| XML 文件 | tests | failures | errors | skipped | timestamp | 文件 mtime（+08:00） |
|---|---|---|---|---|---|---|
| `TEST-com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest.xml` | 12 | 0 | 0 | 0 | 2026-09-21T18:46:22 | 2026-09-22 02:46:22 |
| `TEST-com.creationreadingassistant.feature.reader.pager.ReaderTapActionPresetTest.xml` | 9 | 0 | 0 | 0 | 2026-09-21T18:46:22 | 2026-09-22 02:46:22 |
| `TEST-com.creationreadingassistant.feature.reader.pager.ReaderTapZoneGeometryTest.xml` | 13 | 0 | 0 | 0 | 2026-09-21T18:46:22 | 2026-09-22 02:46:22 |
| **合计** | **34** | **0** | **0** | **0** | — | — |

用例数构成：`ReaderTapZoneGeometryTest` 13 条全部新写（5 条契约值 + 4 条行为探针 + 3 条源码守卫 + 1 条反同义反复）；`ReaderTapActionTest` 9→12（新增 3 条 deadzone）；`ReaderTapActionPresetTest` 6→9（新增 3 条矩阵/语义）。请求类数 3 = 实到 XML 类数 3，无 `--tests` 零命中。

## 十一、主 Kotlin 与 androidTest 编译结果

| 目标 | 最终结果 | 过程中的失败（如实记录） |
|---|---|---|
| `:app:compileDebugKotlin --rerun` | `BUILD SUCCESSFUL in 46s`，退出码 0，无 error | 无 |
| `:app:compileDebugAndroidTestKotlin --rerun` | `BUILD SUCCESSFUL in 12s`，退出码 0，无 error | 前两次失败：AT-COMPILE-1 退出码 1（11 条错误）；AT-COMPILE-2 退出码 1。根因同一个 —— `Rect.width/height` 在 Compose 里是**属性**不是函数，我按 `boundsInRoot.width()` 写成了调用。改成 `.width` / `.height` 后第三次通过。失败日志 `android/AT-COMPILE-1.log`、`AT-COMPILE-2.log` |

androidTest 只编译、**未安装、未执行**；本切片没有构建 APK，也没有触碰任何设备。

## 十二、Git 范围核对

```
git status --short -- <7 个授权路径>
 M android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt
 M android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt
 M android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionTest.kt
 ?? android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt
 ?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt
 ?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt
```

`git diff --check -- <4 个受跟踪的产品/测试文件>` → 无输出，退出码 **0**（无空白错误）。未跟踪文件不进 diff，故 `--check` 覆盖不到 3 个 `??` 文件，已用人工检查替代（缩进沿用各文件原有 4/8 空格风格，行尾 LF）。

**本片新增差异 vs 开始前既有差异（逐文件归因）**：

| 文件 | 开始前是否已脏 | 既有差异（**不是本片的**） | 本片新增 |
|---|---|---|---|
| `ReaderTapAction.kt` | 是（`git diff` 4 个 hunk，+89/−4，**四个 hunk 全是混合的**） | `object TapActionPreset`（11 行，hunk 1 内）、`preset` / `edgeDeadzonePx` 两个默认参数（hunk 2）、deadzone 早返回块（4 行，hunk 3 内）、左右区 `when (preset) { LEFT_HAND / BOTH_NEXT / else }` 的分支体（hunk 4 内的 8 行）—— 均为他人在本切片开始前留下的未提交改动 | hunk 1 内 56 行（`ReaderTapZoneGeometry` object + KDoc）、hunk 3 内 2 行顺序注释、hunk 4 内 4 行条件表达式（比例字面量 → 几何调用） |
| `PagedEpubContent.kt` | 是（19 个 hunk，+73/−16） | 8 个纯他人 hunk：`tapActionPreset` 参数行、4 个 `if (leftCan/rightCan) Icon(leftIcon/rightIcon, contentDescription = leftDesc/rightDesc…)` 预设图标接线、EPUB 图片查看器 + 注脚弹窗两个功能块；另有 1 个混合 hunk（`isLeftAdvance…rightDesc` 共 18 行里 14 行是他人的） | 10 个纯本片 hunk：1 条 import + 9 处 `weight(字面量)` → `weight(topWeight/middleWeight/leftWeight/centerWeight/rightWeight/bottomWeight)`（现位于 `:258,263,265,272,280,287,296,303,311`）；外加混合 hunk 里的 4 行（2 行注释 + 2 行解构） |
| `ReaderTapActionTest.kt` | 是（单个 +60 hunk） | `S07-F2` 段 2 条边界页用例及其注释 | `S09-B` 段 3 条 deadzone 用例及其注释 |
| `TapZonePreviewCard.kt` | **未跟踪文件**（他人创建，HEAD 里没有） | 整个文件的历史内容都是他人的 | 无法用 `git diff` 呈现；见附录 B 的 before/after 清单 |
| `ReaderTapActionPresetTest.kt` | **未跟踪文件**（他人创建） | 原有 6 条用例 | 新增 3 条用例 + `assertTrue` import |
| `ReaderTapZoneGeometryTest.kt` | 本片新建（197 行） | 无 | 全部 |
| `ReaderScreenTest.kt` | 否 —— HEAD 版本 17 条 `@Test`，diff 的 9 个 hunk 全在 import 段与第 9 组用例内 | 无他人改动 | 删 1 条占位用例、加 5 条真实渲染用例 + 3 个私有辅助函数 + import 调整；现 21 条 `@Test` |

未越界核对：本片**没有**改动书签（S09-A-F1）、页码/全书进度/`WholeBookPageModel`、音量键/媒体键/蓝牙翻页器、页眉页脚、滚动模式、D-2-B 设置文件与文案作用域、无障碍结构、EPUB 正文/图片/脚注、数据库/DataStore/路由，也没有改协调看板/handoff/TODO。工作区里其他 Agent 的改动逐行保留，未做任何 reset/checkout/stash/format/commit/push。

## 十三、未覆盖项（明确列出）

1. **androidTest 的 5 条预览卡用例未执行**。`:app:compileDebugAndroidTestKotlin` 通过 ≠ 断言成立；像素比例容差 0.05、`testTag` 在 unmerged tree 下的唯一性、以及 `setContent` 单用例单次调用的写法都还需要一次真机 instrumentation 才算验完。
2. **legacy EPUB 的真实命中区未端到端验证**。共享几何 + 纯策略测试证明了"值来自事实源"，`PagedEpubContent` 的 `Box + weight + clickable` 是否真的画出 1/3:1/3:1/3，仍无 Compose 侧断言（任务明确允许不做完整 EPUB 手势测试）。EPUB 的边界页仍靠 `clickable(enabled = …)` 吞点击，而不是像引擎那样返回 `NONE` —— 派发层差异按边界约定未动。
3. **第 4 份几何副本仍在**：`ui/screen/reader/ReaderTapObservation.kt:51` 自己写了 `x >= width / 3f && x <= width * 2f / 3f`（中央区判定，用于埋点观察）。它的值与共享几何一致，但**不是同一个来源**，改共享常量时它会静默脱节。该文件不在本片授权清单内，我没有动它。
4. **模式字符串仍是各处硬编码** `"five-zone"` / `"three-zone"`（引擎、EPUB、预览卡、`ReaderTapActionTest` 各写一遍）。本片只收敛几何，没收敛模式枚举。
5. **EPUB 自己的 preset→方向映射未与引擎合并**（`isLeftAdvance/isRightAdvance` vs 引擎的 `when (preset)`）。两处目前语义一致（第五节矩阵与第六节说明），但仍需人工同步 —— 这就是预览卡文案与真实动作可能再次分叉的老路。
6. **未运行**全量测试、Lint、`assembleDebug`、真机测试（任务禁止项）。所以其他模块是否因 `ReaderTapAction.kt` 的改动受影响，只由「主 Kotlin 全量编译通过 + androidTest 编译通过」间接保证，不代表其他既有 JVM 用例仍绿。
7. **排版引擎指纹无需 +1**：`LayoutConfig.engineVersion` 只覆盖分页/排版算法，点击热区不参与分页，所以不需要升级；本片也没有改 `ENGINE_VERSION`。
8. 源码守卫的强度上限：`legacyEpubAndPreviewCardReadTheSharedGeometry` 只验"文件里出现过这个符号"，M-2 显示一行注释就能满足它（我为了让变异真实，连注释里的符号名一起删了才失败）。真正的保护来自行为探针 + 值契约断言。

## 十四、问题分级（本片遗留）

- **P0：无。**
- **P1：无。**
- **P2（1 项）**
  - **P2-1｜EPUB 热区手感变化的验收未做。** 左右区 16%→33.3%、中央唤出区 68%→33.3% 是任务要求的收敛结果，但这是本片**唯一改变真实点击行为**的地方，且只在编译级验证过。真机上"想唤出控制栏却翻了页"或反之的投诉风险要一次真实设备验收才能定级。建议并入下一次点击区域真机验收（与未覆盖项 1、2 一起做）。
- **P3（4 项）**
  - **P3-1｜`ReaderTapObservation.kt:51` 的第四份 1/3 副本**（未覆盖项 3）。值当前一致，属静默漂移风险，不是现存缺陷。
  - **P3-2｜模式字符串四处硬编码**（未覆盖项 4）。
  - **P3-3｜预览卡顶/底带省略文字标签**（第六节第 3 点）。是 12% 带高下的取舍：五分区顶部带不再显示"上一页"三字，只留向上箭头。信息量略降，换来字体放大时不被裁。若产品认为必须显示文字，则需要单独放大示意图高度或改成带外侧标注 —— 属视觉决策，不在本片范围。
  - **P3-4｜`android/` 根目录留下 13 个 `.log` 证据文件**（`RED-1.log`、`GREEN-1.log`、`GREEN-2.log`、`GREEN-3.log`、`AT-COMPILE-1.log`、`AT-COMPILE-2.log`、`AT-COMPILE-3.log`、`MAIN-COMPILE-1.log`、`MUT-1.log`、`MUT-2.log`、`FINAL-main-compile.log`、`FINAL-androidtest-compile.log`、`FINAL-tests.log`）。已被 `android/.gitignore:17` 的 `*.log` 忽略、不进版本库；保留是为了让复核者能直接看到原始输出。核对完可删。其中 `RED-1.log`、`GREEN-1.log`、`GREEN-2.log` 是 PowerShell 5.1 `Tee-Object` / 默认 `Out-File` 写出的 **UTF-16LE**，用 `grep` 直接读会"看起来没有内容"，需 `iconv -f UTF-16LE -t UTF-8`；`AT-COMPILE-1.log` 之后的日志已改用 `Out-File -Encoding utf8 -Width 5000`（`Out-File` 默认 120 列会把错误路径折断，导致 grep 抓不到）。

## 十五、SEAM REQUEST（需要集成者/其他窗口裁决的接缝）

1. **请求授权把 `ReaderTapObservation.kt:51` 的中央判定改读 `ReaderTapZoneGeometry.centerLeftEdge/centerRightEdge`**（约 2 行，行为不变，消除第四份副本）。该文件不在本片清单内，我没动。
2. **请求把"点击区域几何"纳入设置页的可见契约测试**：目前是 JVM 值契约 + 未运行的 androidTest 像素断言两层，缺一层真机 instrumentation。建议由有设备窗口的下一片补跑 `ReaderScreenTest` 的 5 条预览卡用例（MIUI 弹窗需人工或脚本处理）。
3. **请求裁决 EPUB 派发层是否与引擎合并**（把 `isLeftAdvance/isRightAdvance/leftCan/rightCan` 换成 `resolveReaderTapAction` + `ReaderTapTurnPlan`）。这能让 legacy EPUB 免费获得 `NONE` 边界语义，但会重写整个点击层 —— 本片按边界约定明确没做，需要单独立片。
4. **提示：`ReaderTapZoneGeometry` 的声明顺序有测试约束**（必须在 `resolveReaderTapAction` 之前）。后续有人搬动这个 object 会让守卫误报；若希望去掉这个约束，把守卫的分界串从文本切割改成括号配平即可（我可以做，属另一片）。

## 十六、回传摘要

- **是否操作设备**：否。没有连接手机、没有 `adb` 调用、没有构建或安装 APK。
- **是否修改未授权文件**：否。改动限定在 7 个授权路径（3 个产品文件 + 3 个 JVM 测试 + 1 个 androidTest）；`TapZonePreviewCard.kt`、`ReaderTapActionPresetTest.kt` 两个未跟踪文件本身是他人产物，本片在其内部做了本切片必要修改（见第十二节归因）。其他 Agent 的既有改动零覆盖、零回滚。
- **验证状态**：`:app:testDebugUnitTest` 定向 3 类 34 条全绿（0 失败/0 错误/0 跳过）；`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin` 均 BUILD SUCCESSFUL，退出码 0；两轮变异（M-1、M-2）均按预测失败并已完整还原。
- **未完成/不可验证**：androidTest 5 条预览卡用例仅编译通过，未执行；legacy EPUB 真实点击手感未做端到端/真机验证（对应 P2-1）。
- **是否建议进入 Codex 独立复核**：**建议**。复核重点排序：① 第十三节未覆盖项 1/2 是否接受为"下一片补"；② P2-1 的 EPUB 热区扩大是否要在合并前做一次真机点击确认；③ 未跟踪文件的改动归因只能靠附录 B 与 SHA-256，请确认这个取证方式可接受；④ 是否批准第十五节 1 号接缝（第四份副本收口）。
- **未领取下一任务**：是。本片到此停止，等待 Codex 核验，不开始 S09-C 或其他切片。

---

## 附录 A：完整 SHA-256（复核用，仓库根执行 `sha256sum -c` 前请先 `git status` 确认这些文件未被改动）

```
7f1ed408e3f24daf360259fee50470d0238dd21a26d08721dfbb00a4a9ce2db4  android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt
b86c19b4ed547cff24d375a208edcc7adc5659bb5488dce2051b5c142b320a15  android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt
294039fde2685c5e5e0d221222b8bb19a88397fbed8a9ee1ba5b6574dbaff878  android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt
f3c098a59811e2a6bda91f3c5d7accc34339b5eb4ffb40e2699ec08d23f7d300  android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt
edddb6d3ed139956c2b88c90c89fc913b7cdf47ba3f84efeb965001f8aaf2f22  android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionTest.kt
f7a83478b0dd46e46e69cf3db9b98b15b1fb15df024f4c82c5025d7e2fb594d6  android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt
da11a2f8e7b70f65f3a3aa7472f60b64f22a6dda9b3c3d5c36f4ce7ed86812be  android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt
```

## 附录 B：`TapZonePreviewCard.kt`（未跟踪，`git diff` 看不到）逐点改动清单

| 位置 | before | after |
|---|---|---|
| import 段 | 无 `testTag`、无几何对象 | `+ androidx.compose.ui.platform.testTag`、`+ …pager.ReaderTapZoneGeometry` |
| 顶层 | — | 新增 5 个 `internal const val TAP_PREVIEW_TAG_LEFT/CENTER/RIGHT/TOP/BOTTOM`（含"给测试量容器真实边界"的说明） |
| 函数 KDoc | 只说高亮热区 | 补"占比取自 `ReaderTapZoneGeometry`，动作必须与 `resolveReaderTapAction` 一致" |
| `isFiveZone` 之后 | — | `+ val (leftWeight, centerWeight, rightWeight) = horizontalWeights`、`+ val (topWeight, middleWeight, bottomWeight) = verticalWeights` |
| 五分区纵向 | `weight(0.24f)` / `weight(0.52f)` / `weight(0.24f)` | `weight(topWeight)` / `weight(middleWeight)` / `weight(bottomWeight)` |
| 五分区中带横向 | `weight(0.30f)` / `weight(0.40f)` / `weight(0.30f)` | `weight(leftWeight)` / `weight(centerWeight)` / `weight(rightWeight)` |
| 三分区横向 | `weight(0.32f)` / `weight(0.36f)` / `weight(0.32f)` | 同上（`left/center/rightWeight`） |
| `PreviewZoneItem` | `(label, icon, isAdvance, modifier)`，图标固定 13dp，恒画文字 | `(label, icon, isAdvance, tag, modifier, showLabel=true)`；容器加 `.testTag(tag)`；`showLabel=false` 时图标 10dp 且不画文字/间隔（顶、底带用） |
| `PreviewCenterMenuZone` | `(modifier)` | `(tag, modifier)`，容器加 `.testTag(tag)` |
| 提示文案 | `when (tapActionPreset)` 三分支，`BOTH_NEXT` 恒写"或顶部热区"，`LEFT_HAND` 写"左侧大区域" | `when {}` 四分支：`BOTH_NEXT` 按 `isFiveZone` 分叉；`LEFT_HAND` 改成等宽三区的事实描述；三分区版**不含**"顶部"二字 |
| 未改动 | 98dp 微屏高度、12dp 圆角、`Color(0xFFD97706)/0xFFB45309` 琥珀配色、`animateColorAsState(tween(250))`、11sp 提示字号、`Icons.AutoMirrored.Outlined.KeyboardArrow*` 选择规则 | 同左 |
