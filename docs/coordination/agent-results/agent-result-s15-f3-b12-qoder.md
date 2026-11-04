# S15-F3-B1/B2 结果报告（窗口 C / Qoder）— 两个时长 pill 的普通点击吞没与长按命中盒

- 任务：S15-F3-B1（BookDetailStatsSection / SessionMicroIsland）+ S15-F3-B2（HomeCompletedScreen / CompletedBookCard）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 12:55–13:30（本机 +08:00）
- 结论：**两处手势都移到可见 pill 容器上，由同一个 `combinedClickable` 分流普通点击与长按；pill 内文本改为纯展示并走公共 `formatDuration`。定向 JVM（18 项）与 `:app:compileDebugKotlin` 全部退出码 0；`:app:compileDebugAndroidTestKotlin` 在 13:18:46 对本片新文件编译通过，之后 4 次重跑被窗口 B 13:20 新写的未跟踪文件阻断（详见第六节），未修改对方文件。**

---

## 一、两个节点修改前后的事件矩阵

修改前的共同根因：长按切换单位由公共 `Modifier.durationUnitToggle()` 提供，它把 `combinedClickable(onClick = {}, onLongClick = toggle)` 挂在**时长文本自己**上（`DurationText` 内部就是这么写的）。于是文本块成为一个"吃点击但不做事"的节点，而 pill 的内边距又不在这个节点里。

### B1：`SessionMicroIsland`（BookDetailStatsSection.kt）

父行：`Surface(... .clickable { itemExpanded = !itemExpanded })`。

| 落点 | 修改前 | 修改后 |
|---|---|---|
| pill 内文字上·普通点击 | 子文本 `onClick = {}` 消费 → **父行收不到，不展开/不收起** | pill `onClick` → `itemExpanded` 翻转一次（与父行一致） |
| pill 内边距·普通点击 | 子文本未覆盖 → 冒泡到父行 → 展开（与文字上行为不一致） | 同上，与文字上完全一致 |
| 行内其它区域·普通点击 | 父行 clickable → 展开/收起 | 不变 |
| pill 内文字上·长按 | 切单位（命中盒仅文字） | 切单位，命中盒=整个 pill（含图标位、文字、padding） |
| pill 内边距·长按 | **无手势**（文本外） | 切单位 |
| 长按后是否再派发普通点击 | 否（combinedClickable 原生保证） | 否（同一个 combinedClickable） |

### B2：`CompletedBookCard`（HomeCompletedScreen.kt）

父卡：`SectionCard(onClick = onOpen)`；管理按钮：`Surface(onClick = onManage)`（36dp 独立节点）。

| 落点 | 修改前 | 修改后 |
|---|---|---|
| pill 文字·普通点击 | 子文本吞掉 → **不打开书** | `onOpen()` 一次（父卡不再重复触发：子层已消费） |
| pill 内边距·普通点击 | 冒泡到父卡 → 打开 | `onOpen()` 一次，与文字上一致 |
| 卡片其它区域·普通点击 | 父卡 → 打开 | 不变 |
| pill 文字·长按 | 切单位 | 切单位，命中盒=整个 pill |
| pill 内边距·长按 | 无手势 | 切单位 |
| 管理按钮 | `onManage` | 原样未动（仍 `Surface(onClick = onManage)`） |

## 二、为什么手势必须在 pill 容器层

1. **命中盒 = 可见容器**：用户看到的可点对象是那颗带底色、描边和内边距的胶囊。手势挂在文字上时，胶囊的 padding 区域既不吃点击也不响应长按，同一颗 pill 出现"点数字没反应、点边上有反应"的双重行为。
2. **吞没只能有一处出口**：`combinedClickable` 消费后父级就收不到（父行/父卡的观察者都在祖先链上，Main pass 子先于父）。留一个 `onClick = {}` 的空子节点，就等于让"文字那一小块"从父级业务里被挖走 —— 这正是本片两个缺陷本体。
3. **点击与长按必须同源**：普通点击转发父级业务动作、长按切单位，这两件事是同一个控件的两种意图，必须由**一个** `combinedClickable` 分流，才能保证"长按之后不再补发普通点击"。若继续保留两层（文字一层、容器一层），两层各自的状态机不同步，长按抬手时另一层就可能看到一次独立点击。
4. **无障碍同理**：合并后 pill 节点自己带 `OnClick` + `OnLongClick(label)`，读屏/Switch Access 面对的是一个有语义的控件，而不是"一个能点但什么都不做的文字"。

落地方式（两文件共用一份契约）：`BookDetailStatsSection.kt:63-108` 新增
`@Composable internal fun Modifier.durationUnitPill(tag: String, onClick: () -> Unit): Modifier`
= `testTag(tag)` + `combinedClickable(interactionSource, indication = LocalIndication.current, onLongClickLabel = DURATION_UNIT_PILL_LONG_PRESS_LABEL, onClick = onClick, onLongClick = LocalDurationUnitToggle.current)`；
`HomeCompletedScreen.kt` 通过 `import ...ui.screen.shelf.durationUnitPill` 复用同一个函数（该文件本来就 import 了 shelf 包的 `BookActionSheet` / `bookNotReadyLabel`，方向一致）。**没有新建生产文件**：允许清单里的第 4 个生产文件位没动用，两个 pill 的颜色/图标差异留在调用点，手势契约只有一份。

文本侧：pill 内改成普通 `Text(formatDuration(ms, LocalDurationUnit.current))`，不再使用 `DurationText`，也不再挂 `durationUnitToggle`（要求 5）。B1 调用点在 shelf 包内，`formatDuration` 走 `ShelfUtils.kt:18` 那个**委托公共实现**的转发（`= ui.util.formatDuration(...)`），未复制任何格式算法；B2 所在 `homearchive` 包没有同名函数，直接 import 公共 `ui.util.formatDuration`。

## 三、每条 Compose 测试实际渲染的生产组件

新文件：`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt`（7 条）。

| 用例 | 渲染的生产组件 | 定位方式 |
|---|---|---|
| `clickingSessionDurationPillExpandsThenCollapsesDetail` | **`BookDetailStatsSection`**（生产上层，内部渲染 private 的 `SessionMicroIsland`） | `SESSION_DURATION_PILL_TAG` / `SESSION_DETAIL_BODY_TAG` |
| `longPressingSessionDurationPillTogglesUnitWithoutExpanding` | 同上 | 同上 |
| `sessionDurationPillPublishesLongPressLabelAndStaysPlainTextInside` | 同上 | 同上 + 语义树遍历 |
| `clickingCompletedDurationPillOpensBookExactlyOnce` | **`CompletedBookCard`**（生产，internal） | `COMPLETED_DURATION_PILL_TAG` |
| `longPressingCompletedDurationPillTogglesUnitWithoutOpeningOrManaging` | 同上 | 同上 |
| `clickingManageButtonStillOnlyOpensTheManagementSheet` | 同上 | 管理按钮 `onNodeWithContentDescription("管理《测试书甲》")` |
| `completedDurationPillPublishesLongPressLabelAndStaysPlainTextInside` | 同上 | tag + 语义树 |

没有为测试放宽任何可见性：`SessionMicroIsland` 仍是 `private`，测试从真实调用点 `BookDetailStatsSection(progress = null, sessions = listOf(…), percent = 0f)` 渲染（三个参数都是普通数据，无 ViewModel/Hilt 依赖）✓ 要求 11。测试内不构造任何 clickable：唯一手势来自生产的 `durationUnitPill`；测试只提供 `LocalDurationUnitToggle`（本就是根注入的 CompositionLocal）来计数长按 ✓ 不"自证"。

## 四、click / longClick / onOpen / onManage / toggle 次数

| 用例 | 动作 | toggles | opens | manages | 展开状态 |
|---|---|---|---|---|---|
| B1 点击→再点击 | pill `performClick` ×2 | — | — | — | 不存在 → 显示 → 不存在（各一次翻转；若父行与 pill 双触发，第二次点击后仍"不存在"，断言即红） |
| B1 长按 | pill `longClick` | **1** | — | — | 详情仍不存在（未展开） |
| B1 语义 | — | `OnClick` 在 pill 容器上；`OnLongClick.label == "切换时长显示单位"`；pill 内所有带 `Text` 的语义节点均无 `OnClick`/`OnLongClick` | | | |
| B2 点 pill | `performClick` | 0 | **1** | 0 | — |
| B2 长按 pill | `longClick` | **1** | **0** | **0** | — |
| B2 点管理按钮 | `performClick` | 0 | **0** | **1** | — |
| B2 语义 | — | 同 B1（`OnClick` 在 pill、长按标签、内层文本纯展示） | | | |

「pill 长按不触发管理按钮」由 B2 长按用例的 `manages == 0` 直接钉住；「长按后不补发普通点击」由同一条用例的 `opens == 0` 钉住（`combinedClickable` 若回归成两次派发，`opens` 会变 1）。

回归防护最要紧的一条是 `assertNoClickSemanticsOnPillText()`：它遍历 pill 的语义后代，断言"有文本的节点不得带 OnClick/OnLongClick"，并且先断言 `textNodes.isNotEmpty()` 防止空转 —— 有人把 pill 里的 `Text` 换回 `DurationText`，这条立刻红，而不必等设备。

## 五、命令与退出码

均在 `android/` 下经 `powershell.exe -NoProfile -Command` 运行（MSYS 里 `cmd.exe /c` 会假退 0），退出码取 `$LASTEXITCODE`，日志 `Out-File -Encoding utf8 -Width 5000`。

| 命令 | 退出码 | 结果 | 日志 |
|---|---|---|---|
| `:app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.shelf.BookDetailStatsPolicyTest" --tests "com.creationreadingassistant.ui.util.TimeUtilsTest" --rerun` | **0** | `BUILD SUCCESSFUL in 24s` | `S15F3-tests-final2.log` |
| `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 2m 45s` | `S15F3-main-final2.log` |
| `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | 1 → **0** → 1×4 | 第 1 次 1 是**我的错**：`assertDoesNotExist` / `performLongClick` / `onNodeWithContentDescription` 按 import 引入失败（前两个在本地版本里分别是成员函数与不存在的 API）。修成 `assertDoesNotExist()` 成员调用 + `performGesture { longClick() }` + `onNodeWithContentDescription` 的 `FindersKt` import 后，13:18:46 **`BUILD SUCCESSFUL in 24s`**（退出码 0）。之后 4 次重跑（13:20、13:25、13:26、13:28）全部失败，且错误只在窗口 B 13:20 新写的未跟踪文件里，见第六节 | `S15F3-androidtest-1.log`（我错）、`S15F3-androidtest-2.log`（**绿**）、`-final/-3/-4/-5.log`（对方阻断） |
| `git diff --check -- <两个授权生产文件>` | **0** | 无输出（中途曾报我 KDoc 一处行尾空格，已修） | — |

定向 JVM 计数（快照在 gitignore 的 `android/app/build/tmp/s15f3-evidence/`，`timestamp="2026-09-22T05:27:13"` UTC = 13:27:13 +08:00）：

| 类 | tests | failures | errors | skipped | 为什么选它 |
|---|---|---|---|---|---|
| `ui.screen.shelf.BookDetailStatsPolicyTest` | 2 | 0 | 0 | 0 | B1 所属文件的纯策略测试（`bookDetailReadingTimeMs` 等） |
| `ui.util.TimeUtilsTest` | 16 | 0 | 0 | 0 | 我在 pill 里直接调用公共 `formatDuration`，这套是它的测试 |
| **合计** | **18** | **0** | **0** | **0** | |

没有拿无关用例凑数：`ui/screen/home/HomeReadingArchiveSectionTest.kt` 虽然匹配"archive"关键字，但它测的是 `HomeReadingArchiveSection` —— 既不是本片两个节点之一，也不是它们的纯逻辑，且属窗口 B 在改的文件，故**未运行**。

## 六、androidTest 编译门禁的当前状态（如实说明）

我的新测试文件确实在 13:18:46 随整个 androidTest 源集编译通过（`S15F3-androidtest-2.log`，`BUILD SUCCESSFUL in 24s`）。此后同一命令连续 4 次失败，错误全部落在**窗口 B 的新文件**里：

```
e: .../androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt:201:21 Unresolved reference ...
e: .../androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt:251:37 Unresolved reference ...
```

取证：`git status --short` 显示该文件为 `??`（未跟踪、13:20:13 起持续被改写），不在我的允许清单里，我也没有改它一行。第 3 次重跑还遇到一次 `:app:compileDebugKotlin`「Not enough memory to run compilation」—— 两个窗口同时跑 Gradle 编译时的内存争用，与代码无关。**结论：androidTest 编译对本片代码是绿的，对当前仓库 tip-of-tree 因窗口 B 在写而暂红。** 我没有为了让门禁变绿去动别人文件，也没有重试第 6 次（错误是确定性的、非竞态）。

## 七、文件范围与 SHA-256

| 文件（仓库根相对） | 本轮 | 修改前基线（`git show HEAD:` 的 blob） | 本轮结束 |
|---|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailStatsSection.kt` | 改（496 行，diff 71/4） | `28b6134d7b63547a…` | `a1273c6884bc2e22468b7f396c165619ccec1bda9db7113d730bb01db23d2d4f` |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeCompletedScreen.kt` | 改（564 行，diff 17/3） | `0f0e5f0925814ac7…` | `4385b64ccc7b6b9b4dcee03cf4bde003050fe281ffc23507bf1d1b6122c01a87` |
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt` | **新增**（271 行，7 条 `@Test`） | 不存在（`??`） | `72f6749dcfd37121038dc0969aa8a4ff1b19d5b00e2e867b9d102022921c0264` |

**归因说明（重要）**：这两个生产文件在本轮开始前就带着窗口 B 的未提交改动，`git diff` 混合了两方内容。逐 hunk 归属如下。

- `BookDetailStatsSection.kt`：**不是我的**——`import ui.util.formatRelativeTime` / `LocalDurationUnit` / `durationUnitToggle`、`:126` 的 `formatDuration(..., LocalDurationUnit.current)` 统计格、`:132` 的 `valueModifier = Modifier.durationUnitToggle()`（这两处行号在我插入常量与 `durationUnitPill` 之后从 75 / 81 下移）。**是我的**——4 条新 import、`:63-108` 的标签常量与 `durationUnitPill`（定义在 `:94`）、B1 pill 的 `modifier = durationUnitPill(…) { itemExpanded = !itemExpanded }`（`:321`）+ 内部 `DurationText` → 纯 `Text(formatDuration(…))`、详情容器 `testTag(SESSION_DETAIL_BODY_TAG)`（`:369`）。
- `HomeCompletedScreen.kt`：**不是我的**——`:246` `CompletedStatsIsland` 头部那颗 pill 的 `Text(formatReadingTime(…)) → DurationText(…)`（窗口 B 的单位切换改造，我原样保留，仍是文本级 `durationUnitToggle`，见第八节 3）、`import ui.util.DurationText` 本身。**是我的**——`import LocalDurationUnit` / `formatDuration` / `durationUnitPill`、`COMPLETED_DURATION_PILL_TAG` 常量（`:387-389`）、卡片内 pill 的 `modifier = durationUnitPill(…) { onOpen() }`（`:510`）+ `DurationText` → 纯 `Text(formatDuration(…))`（`:527`）。

未修改（禁改清单）：`DurationDisplay.kt`、`DurationPrefs.kt`、`SharedComponents.kt`、`MicroIsland.kt`、`MyReadingScreen.kt`、`HomeReadingArchiveSection.kt`、`build.gradle.kts`；另外本片约定不碰的 `ReaderScreenTest.kt`、legacy EPUB、公共 `SectionCard`/`IslandCard`、协调看板、handoff、Obsidian 均未修改。窗口 B 的 `DurationUnitToggleEntryTest.kt` 只在排障时读取过路径与 mtime，未改一行。

## 八、开始前 / 结束后的 git status --short 与遗留

**开始前**（本轮 12:55 起点的状态类别，与前几轮一致）：`android/app/src/main` 整目录因多个窗口长期为脏，本片相关的是
`M ui/screen/shelf/BookDetailStatsSection.kt`、`M ui/screen/homearchive/HomeCompletedScreen.kt`（两者都带窗口 B 的未提交时长改造）。窗口 B 当时还没有 `ui/screen/stats/DurationUnitToggleEntryTest.kt`（该文件 13:20:13 才出现）。

**结束后**（限定本片授权路径 + 窗口 B 的阻断文件）：

```
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailStatsSection.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeCompletedScreen.kt
?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt
?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt   ← 窗口 B，本片未触碰
```

状态类别变化：只有我新增的测试文件从"不存在"变成 `??`；两个生产文件仍是 `M`；没有删除、改名或恢复任何人的文件。`android/*.log` 与 `app/build/**` 被 gitignore，运行日志与 XML 快照不污染 status。

遗留：

1. **7 条 Compose 用例仍未在设备执行**（本片禁止 ADB/真机/模拟器）。需要带设备窗口验证：pill 单击展开/收起各一次、长按只切单位、`onOpen`/`onManage` 不串、`OnLongClick` 标签与"内层文本无点击语义"两条结构断言。
2. **`performGesture { longClick() }` 在本 Compose 版本已弃用**（编译有 4 条 `w:` 警告，非错误）。现代写法是 `performTouchInput { longClick() }`（`TouchInjectionScope.longClick`）。我**没有**换：换了就必须重编 androidTest，而该源集正被窗口 B 的文件卡住，届时我无法再给出"编译通过"的证据。请下一个能编译的窗口一行替换即可（两个调用点都在同一文件，`longClick`/`performGesture` 的 import 也成对存在）。
3. **同型缺陷仍在**：`CompletedStatsIsland` 头部时长 pill（`HomeCompletedScreen.kt:246`，窗口 B 在管的改造点）和 `BookDetailStatsSection` 的统计格（`:132 valueModifier = Modifier.durationUnitToggle()`）仍是"手势在文本上"的老形状。它们不属于本片两个节点，我没动；如果确认要统一，建议下一片把它们也接进 `durationUnitPill`（届时 `Modifier.durationUnitToggle` 的用量应归零，可加一条 JVM 源码守卫防回归）。
4. **`durationUnitPill` 的归属地**：现在住在 `ui/screen/shelf/BookDetailStatsSection.kt`，被 `homearchive` 反向 import。它更该住在 `ui/util/DurationDisplay.kt`（与 `durationUnitToggle` 同处），但那文件本轮归窗口 B，我没动。窗口 B 收口后建议搬迁，并顺手删掉文本级 `durationUnitToggle`。

## 九、声明

- 未进入、未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未 `commit` / `push` / `stage` / `reset` / `checkout` / `clean` / `stash` / 全仓格式化；未清理或回滚任何人的未提交改动。
- 未触碰窗口 B 的文件（含其报错文件），未修改 legacy EPUB、`ReaderScreenTest.kt`、公共 `SectionCard`/`IslandCard`、协调看板、handoff、Obsidian。
- 未运行全量 JVM、Lint、APK 构建、ADB、真机或模拟器。
- 未杀任何进程；androidTest 门禁在 4 次重跑被同一外部文件阻断后停止，未继续占用构建。

**未领取下一任务**：是。S15-F3-B1/B2 到此停止，等待 Codex 复核。
