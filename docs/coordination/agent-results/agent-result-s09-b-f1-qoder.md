# S09-B-F1 结果报告（窗口 C / Qoder）— 修复点击热区单一事实源复核发现的两个 P2

- 任务：S09-B-F1｜上游：`docs/coordination/agent-results/agent-result-s09-b-qoder.md`（Codex 复核判 PARTIAL，两个 P2）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 03:35–04:30（本机 +08:00）
- 结论：**P2-1 已修（读屏语义回到顶/底带）；P2-2 已修（legacy EPUB 热区提取成生产复用的 `LegacyEpubTapZoneOverlay`，androidTest 用真实坐标点它并断言真实回调次数）。主 Kotlin 与 androidTest 编译门禁通过；原 S09-B 的三类定向 JVM 用例本轮共尝试 6 次全部被并行窗口的未提交改动 / 并发构建阻断在 Kotlin 编译阶段，未能重跑 —— 已给出人工复算证据，未宣称通过（见第五节）。**

---

## 一、P2-1 修复：顶/底带的读屏语义

**问题**：S09-B 为了让五分区 12% 的带高不被文字撑破，把顶/底带改成 `showLabel=false`，而 `PreviewZoneItem` 里的 `Icon(contentDescription = null)` 依然是装饰性图标，父容器也没有任何 semantics。结果是视觉用户看得到箭头，TalkBack 用户在这两个分区上读不到任何东西 —— 我用"改视觉"顺手把无障碍面削掉了。

**改法**（`TapZonePreviewCard.kt:242-246, 267`）：省略文字标签时，把动作语义交给图标。

```kotlin
val iconSize = if (showLabel) 13.dp else 10.dp
// 省略文字标签的分区（五分区顶/底带）必须由图标承担语义：否则视觉用户看得见箭头，
// TalkBack 却读不出这里是「上一页 / 下一页」。showLabel=true 时文字已在，
// 图标保持装饰性（contentDescription=null），避免同一分区被读两遍。
val iconDescription = if (showLabel) null else label
...
Icon(imageVector = icon, contentDescription = iconDescription, tint = contentColor, ...)
```

要点：
- 视觉不变（仍是无文字的窄带 + 箭头），只补语义，不回退到"带高放不下文字"的老问题。
- 有文字标签的分区（中带三区、三分区三区）图标**继续**是 `contentDescription = null`，避免同一分区被读两遍；这条也有断言（见第二节）。
- 顶/底带的 `label` 本来就是 `"上一页"` / `"下一页"`，与 `resolveReaderTapAction` 的顶=PREVIOUS、底=NEXT 一致，没有新造文案。
- 未触碰 legacy EPUB 的无障碍描述（`"上一章"/"下一章"` 原样搬进 overlay）。

## 二、P2-2 修复：让测试点在生产点击链上

### 2.1 生产侧：热区抽成可复用 Composable

`PagedEpubContent.kt` 里原来埋在 `PagedEpubView` 深处的点击分区块（三套 `weight()` + 6 个 `Box`），现已抽成同文件的 `internal fun LegacyEpubTapZoneOverlay(...)`（定义在 `:252-355`），`PagedEpubView` 在**完全相同的位置**调用它（`:238`）：

```kotlin
LegacyEpubTapZoneOverlay(
    tapZoneMode = tapZoneMode,
    tapActionPreset = tapActionPreset,
    canPrev = canPrev,
    canNext = canNext,
    onPrev = onPrevHaptic,          // 震动包装仍留在 PagedEpubView，语义不变
    onNext = onNextHaptic,
    onToggleControls = onToggleControls,
    iconTint = paperFg.copy(alpha = 0.3f),
)
```

逐行原样搬运的部分：`isLeftAdvance / isRightAdvance / leftCan / rightCan / leftAction / rightAction / leftIcon / rightIcon / leftDesc / rightDesc`、`clickable(enabled = …)`、`remember { MutableInteractionSource() }` + `indication = null` 的中央 Box、`contentDescription = "上一章"/"下一章"` 的图标。仅做两处机械替换：`onPrevHaptic()→onPrev()`、`onNextHaptic()→onNext()`（因为震动包装由调用方传入），`tint = paperFg.copy(alpha = 0.3f)` → `tint = iconTint`（同值）。外层 `readerCenterTapToToggle` 兜底仍由 `PagedEpubView` 持有，overlay 不重复挂载，事件消费顺序不变。

**为什么没有新增文件**（任务第 4 项允许但未采用）：`internal` 在整个 module 内可见，AGP 的 friend-paths 让 `androidTest` 能直接调用 `PagedEpubContent.kt` 里的 `internal` Composable —— 上一轮的 `TapZonePreviewCard`（同为 internal）已经被这样测过。留在原文件还顺带保住了 `ReaderTapZoneGeometryTest.legacyEpubAndPreviewCardReadTheSharedGeometry`：该 JVM 守卫按**文件路径**读 `PagedEpubContent.kt` 的源码断言其中出现 `ReaderTapZoneGeometry`，若把热区搬进新文件，守卫会失败，而那需要改动本轮禁止触碰的 JVM 几何测试。

证据（本轮实测）：
- `PagedEpubContent.kt` 里 8 个分区 `clickable` 全部落在 overlay 内（`:296,303,312,318,325,334,343,349`，overlay 定义在 `:266` 之后），函数外没有残留的分区点击实现；文件里 `LegacyEpubTapZoneOverlay(` 恰好出现 2 次（1 个调用 + 1 个定义）。
- 9 处 weight 全部来自共享几何：`weight(leftWeight)×2 / centerWeight×2 / rightWeight×2 / topWeight×1 / middleWeight×1 / bottomWeight×1`。

### 2.2 测试侧：真实坐标 + 真实回调计数

`ReaderScreenTest.kt` 新增第 10 组（`:424-579`，5 条用例 + 3 个辅助）。渲染的是 `LegacyEpubTapZoneOverlay` 本体，回调是测试里的计数器：

```kotlin
private class TapZoneCalls { var prev = 0; var next = 0; var toggle = 0; val total get() = prev + next + toggle }
...
LegacyEpubTapZoneOverlay(..., onPrev = { calls.prev++ }, onNext = { calls.next++ },
    onToggleControls = { calls.toggle++ }, iconTint = Color.Gray,
    modifier = Modifier.fillMaxSize().testTag(EPUB_OVERLAY_TAG))
```

命中点由 `ReaderTapZoneGeometry` 从**实测像素尺寸**换算，测试里没有第二套比例（`tapEpubZone(...)`，`:553-579`）：

| 分区名 | x（px） | y（px） |
|---|---|---|
| `left` | `centerLeftEdge(w) / 2` | `h / 2` |
| `center` | `(centerLeftEdge(w) + centerRightEdge(w)) / 2` | `h / 2` |
| `right` | `(centerRightEdge(w) + w) / 2` | `h / 2` |
| `top` | `w / 2` | `topBandBottomEdge(h) / 2` |
| `bottom` | `w / 2` | `(bottomBandTopEdge(h) + h) / 2` |

点击方式是 `node.performTouchInput { click(point) }`（`import androidx.compose.ui.test.click`）。overlay 根节点铺满 semantics root，所以"容器相对坐标"和"root 绝对坐标"重合，`click(position)` 的两种坐标约定点的是同一个点 —— 这一点写进了代码注释，避免后人误改包装层。

### 2.3 每个坐标对应的回调断言

| 用例 | 点击 → 断言（实际回调） |
|---|---|
| `legacyEpubOverlayThreeZoneStandardRoutesEachZoneToItsCallback` | left→`prev==1` 且 `next+toggle==0`；center→`toggle==1`；right→`next==1`；最后 `total==3`（无重复派发） |
| `legacyEpubOverlayFiveZoneAddsWorkingTopAndBottomBands` | top→`prev==1`；bottom→`next==1`；center→`toggle==1`；再 left、right→`prev==2`、`next==2`（中带两侧没被顶/底带挤掉） |
| `legacyEpubOverlayLeftHandPresetSwapsTheSideZones` | left→`next==1` 且 `prev==0`；right→`prev==1` 且 `next` 仍 `==1`、`toggle==0` |
| `legacyEpubOverlayIsSilentOnTheUnavailableEdge`（`canNext=false`） | right + bottom → `next==0` 且 `prev==0` 且 `toggle==0`（禁用的分区既不改判方向，也不顺手唤出控制栏） |
| `legacyEpubOverlayIsSilentOnTheFirstChapterTopBand`（`canPrev=false`） | top + left → `prev==0` 且 `next==0` |

覆盖矩阵对照任务第 6 项：three-zone standard 左/中/右 ✓；five-zone 顶/底/中 ✓（并额外带中带两侧）；非 standard 预设 = `left_hand` ✓（three-zone 与 five-zone 都有边界用例）；`canNext=false` ✓ 与 `canPrev=false` ✓。

### 2.4 为什么"接错/删掉就会红"

- **删掉某个分区的 `clickable`** → 该点什么都不会发生 → 计数器停在 0 → `assertEquals(1, calls.prev)` 失败。
- **`leftAction`/`rightAction` 接反**（或预设分支接反）→ 回调打在错误的计数器上 → `prev==1 / next==0` 变成 `prev==0 / next==1` → 失败。
- **中央 Box 的 `onToggleControls` 漏接** → `toggle` 恒 0 → 失败。
- **比例被局部改掉**（例如某侧改回 16%）→ 用共享几何算出的 left 命中点会落到中央区 → `prev` 记不上、`toggle` 变成 1 → 失败。
- 与旧占位测试的区别：断言对象是**回调被调用了几次**，不是节点"有没有 click action"，所以任何"看起来能点但什么都没接"的形态都逃不掉。

**未闭环的部分（必须说清）**：这些用例本轮只到编译门禁，**没有执行过 instrumentation**（任务第 10 项禁止操作手机；且并行窗口 D 之前在同一台机上做 D-2-B 验收）。所以上面是构造性论证，不是跑出来的证据。

另有一个我的方案没覆盖的缺口：**如果有人把 `LegacyEpubTapZoneOverlay(...)` 这一整句从 `PagedEpubView` 里删掉**，测试仍然会绿 —— 因为测试直接渲染 overlay，不渲染 `PagedEpubView`。补齐它需要一条按文件路径断言"`PagedEpubView` 里存在该调用"的 JVM 结构守卫（与 `ReaderTapZoneGeometryTest` 同款），但那要新增 JVM 测试文件，超出本轮允许清单，故记为 SEAM REQUEST（第七节 1 号）。本轮我用 grep 人工核对了这个调用点存在且唯一（`PagedEpubContent.kt:238`）。

## 三、几何唯一事实源是否被破坏

没有。`ReaderTapZoneGeometry` 仍是唯一来源，本轮**没有新增任何比例字面量**：
- overlay 的 6 段 weight 全部来自 `ReaderTapZoneGeometry.horizontalWeights / verticalWeights`；
- androidTest 的命中点全部来自 `ReaderTapZoneGeometry.centerLeftEdge / centerRightEdge / topBandBottomEdge / bottomBandTopEdge`；
- `ReaderTapAction.kt` 与三个 JVM 几何测试文件本轮**零改动**（哈希逐字节相同，见第四节）。

## 四、文件范围与哈希（本轮）

| 文件（相对仓库根，均在 `android/app/src/`） | 本轮是否改动 | 开始前 SHA-256（= S09-B 结束时） | 结束后 SHA-256 |
|---|---|---|---|
| `main/.../ui/screen/reader/content/PagedEpubContent.kt` | 是（抽 overlay） | `b86c19b4ed547cff24d375a208edcc7adc5659bb5488dce2051b5c142b320a15` | `b0f0c48dd67a9f53…` |
| `main/.../ui/screen/reader/sheets/TapZonePreviewCard.kt` | 是（图标语义） | `294039fde2685c5e5e0d221222b8bb19a88397fbed8a9ee1ba5b6574dbaff878` | `a71d857b2ce991ee…` |
| `androidTest/.../ui/screen/ReaderScreenTest.kt` | 是（+6 用例） | `da11a2f8e7b70f65f3a3aa7472f60b64f22a6dda9b3c3d5c36f4ce7ed86812be` | `03f2a97b34030dba…` |
| `main/.../feature/reader/pager/ReaderTapAction.kt` | **否** | `7f1ed408e3f24daf360259fee50470d0238dd21a26d08721dfbb00a4a9ce2db4` | 同左（未变） |
| `test/.../feature/reader/pager/ReaderTapZoneGeometryTest.kt` | **否** | `f3c098a59811e2a6bda91f3c5d7accc34339b5eb4ffb40e2699ec08d23f7d300` | 同左（未变） |
| `test/.../feature/reader/pager/ReaderTapActionTest.kt` | **否** | `edddb6d3ed139956c2b88c90c89fc913b7cdf47ba3f84efeb965001f8aaf2f22` | 同左（未变） |
| `test/.../feature/reader/pager/ReaderTapActionPresetTest.kt` | **否** | `f7a83478b0dd46e46e69cf3db9b98b15b1fb15df024f4c82c5025d7e2fb594d6` | 同左（未变） |

结束后完整 SHA-256（附录 A 汇总）。`TapZonePreviewCard.kt`、`ReaderTapActionPresetTest.kt`、`ReaderTapZoneGeometryTest.kt` 是未跟踪文件（他人创建 / 本片上轮创建），其改动不出现在 `git diff` 里 —— 归因方式同上一轮，靠哈希 + 本报告逐点说明。

改动统计（`git diff --numstat`，仅跟踪文件）：`PagedEpubContent.kt` +142/−48、`ReaderScreenTest.kt` +371/−14。其中 `PagedEpubContent.kt` 与 `ReaderScreenTest.kt` 都含本轮之前的他人/上轮未提交差异，逐 hunk 归因见上一轮报告第十二节；本轮新增的是：PagedEpubContent 的 overlay 提取（1 处调用替换 82 行内联块 + 1 个新函数）与 androidTest 第 10 组。

### 4.1 开始前 / 结束后的 `git status --short`

**开始前**：本轮起点就是上一轮结束点，7 个授权路径的状态与上一轮报告第十二节逐行相同 —— 4 个 `M`（`ReaderScreenTest.kt`、`ReaderTapAction.kt`、`PagedEpubContent.kt`、`ReaderTapActionTest.kt`）+ 3 个 `??`（`TapZonePreviewCard.kt`、`ReaderTapActionPresetTest.kt`、`ReaderTapZoneGeometryTest.kt`），`pager/` 测试目录下另有他人的 6 个 `??` 与 2 个 `M`。开始前未另存快照，但 4 个未改文件的哈希与上一轮记录逐字节一致（第四节），可作为"起点即终点"的等价证明。

**结束后**（`git status --short`，限定授权路径）：

```
 M android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt
 M android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt
 M android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/PageSelectionHandleDragTest.kt
 M android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderHardwareKeysTest.kt
 M android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionTest.kt
?? android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/CustomTipPlaceholderTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/CustomTipTemplateDescribeTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/PagedReaderSelectionInvalidationPolicyTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ProgrammaticJumpInvalidationTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ProgrammaticJumpWiringGuardTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapTurnPlanTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt
?? android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/WholeBookPageModelTest.kt
```

状态与开始前完全相同（没有新增/删除文件，没有别人的文件被我的操作改写状态；`M`/`??` 的归属见上一轮第十二节）。本轮**唯一新增的仓库文件**是这份报告。

**本轮期间其他窗口在动的文件**（`mtime` 落在 03:53–04:03，全部只读、未改）：`ui/util/TimeUtils.kt`、`ui/util/DurationDisplay.kt`、`ui/screen/home/components/HomeMetricsSection.kt`、`HomeReadingArchiveSection.kt`、`ui/screen/stats/**`（`StatsPage.kt`、`CreationSection.kt`、`HeatmapSection.kt`、`SummaryGroup.kt`、`YearBillHeroCard.kt`、`TrendSection.kt`）、`ui/screen/profile/ProfileHomeScreen.kt`、`ProfileUtils.kt`、`data/settings/DurationPrefsTest.kt`、`ui/screen/home/HomeReadingArchiveSectionTest.kt`。这批"时长展示"改动正是第五节 JVM 门禁被阻断的来源。

## 五、验证命令与结果

全部在 `android/` 目录，经 `powershell.exe -NoProfile -Command` 启动（`cmd.exe /c` 在 MSYS 下给假退出码），退出码取 `$LASTEXITCODE`，日志用 `Out-File -Encoding utf8 -Width 5000`（`Out-File` 默认 120 列会折断 Gradle 的长错误路径，导致 grep 假阴性）。

| # | 命令 | 退出码 | 结果 | 日志 | 覆盖的代码状态 |
|---|---|---|---|---|---|
| 1 | `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 2m 9s` | `F1-main-compile.log` | 含 overlay 提取 + 图标语义（生产最终态） |
| 2 | `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | 1 | `BUILD FAILED in 15s`：`ReaderScreenTest.kt:571:34 Unresolved reference 'click'` | `F1-androidtest-compile.log` | 我自己漏了 `import androidx.compose.ui.test.click` |
| 3 | 同上（补 import 后） | **0** | `BUILD SUCCESSFUL in 8s` | `F1-androidtest-compile2.log` | androidTest 最终态（27 条 `@Test`） |
| 4 | `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests …ReaderTapActionTest --tests …ReaderTapActionPresetTest --tests …ReaderTapZoneGeometryTest --rerun` | 1 | `BUILD FAILED in 10s`：`compileDebugUnitTestKotlin` 失败，全部错误在 **他人文件** `data/settings/DurationPrefsTest.kt:259-266 Unresolved reference 'eve…'` | `F1-tests.log` | 非我代码 |
| 5 | 同上（间隔 100s 重试 4 次） | 全部 1 | 4 次重试各因不同原因失败，均非我代码：他人 `HomeReadingArchiveSectionTest.kt` 的 `formatArchiveDuration` 未解析、他人 `YearBillHeroCard.kt` 的 `LocalDurationUnit` 未解析、build cache 并发写失败、Kotlin 后端 `CompilationException` 崩溃后被另一窗口的 `gradlew --stop` 中止。详见下表 | `F1-tests2/3.log`、`F1-retry-1..4.log` | 非我代码 |

**JVM 门禁结论：本轮未能重跑（BLOCKED），不宣称通过。** 共尝试 6 次（1 次初始 + 1 次紧随其后 + 4 次带 100s 间隔的重试），全部非我代码所致：

| 次序 | 失败任务 | 定位（全部是他人文件 / 工具链） |
|---|---|---|
| 1 | `compileDebugUnitTestKotlin` | `data/settings/DurationPrefsTest.kt:259-266 Unresolved reference 'eve…'` |
| 2 | `compileDebugKotlin` | `ui/screen/stats/components/YearBillHeroCard.kt:104,107,213 Unresolved reference 'LocalDurationUnit'/'DurationUnit'` |
| 3（重试 1） | `transformDebugClassesWithAsm` | `Failed to store cache entry … Could not pack tree 'classesOutputDir'`（并发写 build cache） |
| 4（重试 2） | `compileDebugUnitTestKotlin` | `ui/screen/home/HomeReadingArchiveSectionTest.kt:9-13 Unresolved reference 'formatArchiveDuration'` |
| 5（重试 3，耗时 21m51s） | `compileDebugKotlin` | Kotlin 后端崩溃 `org.jetbrains.kotlin.codegen.CompilationException: Back-end (JVM) Internal error: Couldn't transform method node: equals …`，方法所属类是他人 in-flight 的 `feature/reader/toc/*` 数据类 —— 属守护进程在并发构建下失稳，不是源码语法错误 |
| 6（重试 4） | daemon 被停 | `Gradle build daemon has been stopped: stop command received`（另一窗口执行了 `gradlew --stop`，我的构建被中止） |

截至 04:28 复核：`LocalDurationUnit` 已由并行窗口补上（阻断 2 解除），但 `formatArchiveDuration` 在整个 `app/src/main` 里仍不存在，`compileDebugUnitTestKotlin` 依旧编不过 —— 因此我没有再第 7 次重试（该失败是确定性的，不是竞态）。**全程未杀任何进程、未修改任何他人文件。**

交接提醒：重试循环残留的日志是 `android/F1-retry-1..4.log`；若后续某次自行跑绿，请以那份日志为准更新本节。

### 5.1 在阻断下能补的取证：人工复算三条 JVM 守卫谓词

因为本轮动了 `PagedEpubContent.kt`，`ReaderTapZoneGeometryTest` 的三条源码守卫**确实需要重新确认**。我用一段独立脚本（`C:\Users\23254\AppData\Local\Temp\s09b_f1_guard_check.py`，输出 `s09b-f1-guard-check.txt`）按测试原逻辑逐条重算：

```
guard1 PagedEpubContent    references ReaderTapZoneGeometry = True (count=5)
guard1 TapZonePreviewCard  references ReaderTapZoneGeometry = True (count=4)
guard2 PagedEpubContent    retired literals present = none
guard2 TapZonePreviewCard  retired literals present = none
guard3 body chars=1523 banned hits=none references geometry=True
extra  LegacyEpubTapZoneOverlay( occurrences = 2 (1 call + 1 definition)
extra  zones inside overlay: leftWeight=2 centerWeight=2 rightWeight=2 topWeight=1 middleWeight=1 bottomWeight=1
VERDICT: ALL HOLD
```

这只是"守卫的谓词成立"，**不等于测试跑过**：行为类断言（边界探针、deadzone、预设矩阵）依赖 `ReaderTapAction.kt`，而该文件本轮逐字节未变（哈希 `7f1ed408…`），其 34 条用例在 02:46 那一轮是全绿的。

## 六、待补验证（下一轮必须做）

1. `:app:testDebugUnitTest`（原三类）在并行窗口收敛后重跑一次 —— 预期 34 条全绿。
2. **真机 instrumentation**：`ReaderScreenTest` 第 9、10 组共 11 条 Compose 用例（预览卡 6 条 + EPUB overlay 5 条），验证
   - `performTouchInput { click(point) }` 的坐标约定与 overlay 铺满 root 的前提成立；
   - 中央区只回调一次 `onToggleControls`（外层 `readerCenterTapToToggle` 不双触发）；
   - 比例容差 0.05 在真机密度下通过；
   - TalkBack 实测能读到顶/底带的"上一页/下一页"。
3. 变异验证：临时删掉某个分区的 `clickable` / 交换 `leftAction`、`rightAction`，确认第 10 组用例真的变红（这是第 7 项要求的经验证据，本轮拿不到）。

## 七、SEAM REQUEST

1. **请求批准新增一个 JVM 结构守卫**（例如 `LegacyEpubTapZoneOverlayWiringGuardTest`，按文件路径断言 `PagedEpubView` 内存在 `LegacyEpubTapZoneOverlay(` 调用）。它可以关掉第四节 2.4 末尾那个"整句删掉仍然绿"的缺口；本轮新增该文件超出允许清单，所以没做。
2. **请求给时长展示/DurationPrefs 的并行改动设一个"源集必须可编译"的收口点**：`--tests` 定向门禁在整个 unit test 源集编不过时完全不可用，本轮 5 次尝试全部被中间态阻断。多窗口共用脏工作区时，建议约定"提交前至少保证 `compileDebugUnitTestKotlin` 可过"，或让门禁跑在独立 worktree（见记忆 `feedback-gate-worktree-isolation`）。
3. 上一轮遗留的接缝仍然有效：`ui/screen/reader/ReaderTapObservation.kt:51` 是自己写 `width / 3f`、`width * 2f / 3f` 的**第四份几何副本**；本轮没碰（不在允许清单内）。
4. 上一轮遗留：EPUB 的 `clickable(enabled = false)` 与引擎的"返回 NONE"仍是两套边界表达（overlay 保留了 EPUB 的做法，新增用例只是把这个差异钉住了，没有改变它）。

## 八、声明

- 未进入、未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`；全程只在 `creation-reading-assistant-recovery-20260919` 内操作。
- 未 `commit` / `push` / `stage` / `reset` / `checkout` / `clean` / `stash`，未做全仓格式化，未清理任何未提交改动。
- 只改动允许清单中的 3 个文件；`ReaderTapAction.kt` 与三个 JVM 几何测试文件逐字节未变（第四节哈希证明）。未新增生产 overlay 文件（第四节 2.1 说明了为何不必）。
- 未触碰：时长展示文件、`DurationPrefs`、`android/scripts/**`、协调看板、handoff、Obsidian、页码、硬件键、页眉页脚、全书进度、滚动阅读器、书签、数据库/DataStore/路由、无障碍结构（除 P2-1 要求的补语义外）。他人文件 `DurationPrefsTest.kt`、`HomeReadingArchiveSectionTest.kt`、`YearBillHeroCard.kt` 只读用于定位阻断原因，未改一行。
- 未操作手机、未安装 APK、未运行 ADB、未跑 Lint / 全量测试 / assemble。
- 并行改动逐行保留，`git diff --check` 对我改动的跟踪文件退出码 0。

**未领取下一任务**：是。S09-B-F1 到此停止，等待 Codex 复核。

---

## 附录 A：本轮结束时 7 个相关文件的完整 SHA-256

```
b0f0c48dd67a9f536c239dbfab086144326f43f1f17d7ff2cac551bfd4d71d25  android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/content/PagedEpubContent.kt
a71d857b2ce991ee9b4866bd5cea28eac58833f95a6595fc2f2d80d8f89769bc  android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/TapZonePreviewCard.kt
03f2a97b34030dba8f2e612aaf6b9c05c4faa27828710908e349d690e0eec405  android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt
7f1ed408e3f24daf360259fee50470d0238dd21a26d08721dfbb00a4a9ce2db4  android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderTapAction.kt
f3c098a59811e2a6bda91f3c5d7accc34339b5eb4ffb40e2699ec08d23f7d300  android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapZoneGeometryTest.kt
edddb6d3ed139956c2b88c90c89fc913b7cdf47ba3f84efeb965001f8aaf2f22  android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionTest.kt
f7a83478b0dd46e46e69cf3db9b98b15b1fb15df024f4c82c5025d7e2fb594d6  android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReaderTapActionPresetTest.kt
```

后 4 个与上一轮报告附录 A 完全相同，即"本轮未改动"的字节级证明；前 3 个相对上一轮变化，即本轮改动的字节级证明。路径为仓库根相对路径，`sha256sum -c` 请在仓库根执行（在 `android/` 下执行会因相对路径报 12 个假 `FAILED open or read`）。

## 附录 B：本轮 androidTest 用例清单（27 条 `@Test`，与上一轮 21 条对照）

| 组 | 用例 | 本轮 |
|---|---|---|
| 1–8、补充 | 状态/Action/基础渲染等 16 条 | 未改动 |
| 9 | `previewCardThreeZoneDrawsEqualThirdsForStandardPreset`、`previewCardFiveZoneUsesTwelvePercentBands`、`leftHandPreviewMirrorsThePresetActionMapping`、`bothNextThreeZonePreviewMustNotPromiseATapBackZone`、`bothNextFiveZonePreviewCanPointAtTheTopBand` | 未改动（共享的 `labelInZone` 抽成 `assertNodeInZone` 供新用例复用） |
| 9 | `previewCardFiveZoneBandsExposeTurnSemanticsToTalkBack` | **新增**（P2-1） |
| 10 | `legacyEpubOverlayThreeZoneStandardRoutesEachZoneToItsCallback`、`legacyEpubOverlayFiveZoneAddsWorkingTopAndBottomBands`、`legacyEpubOverlayLeftHandPresetSwapsTheSideZones`、`legacyEpubOverlayIsSilentOnTheUnavailableEdge`、`legacyEpubOverlayIsSilentOnTheFirstChapterTopBand` | **新增 5 条**（P2-2） |
