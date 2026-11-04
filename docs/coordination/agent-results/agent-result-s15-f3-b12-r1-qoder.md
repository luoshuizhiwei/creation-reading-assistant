# S15-F3-B12-R1 结果报告（窗口 C / Qoder）— B1 测试渲染错组件修正 + 弃用手势 API 清理

- 任务：S15-F3-B12-R1｜上游：`agent-result-s15-f3-b12-qoder.md`（B1/B2 实现轮）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 时间：2026-09-22 13:40–14:00（本机 +08:00）
- 结论：**复核成立。B1 三条用例确实渲染了不创建 `SessionMicroIsland` 的组件，真机运行时必然找不到 `SESSION_DURATION_PILL_TAG`。已改渲染真实调用链 `BookDetailSessionsSection`，并补"pill 必须存在"的前置断言；`performGesture/GestureScope.longClick` 已换成 `performTouchInput { longClick() }`，本文件的弃用警告归零。三条门禁全部退出码 0：JVM 18 项 0 失败、`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`（上一轮被窗口 B 中间态文件卡住的这道门禁本轮已恢复）。**
- **生产实现本轮零改动**：两个生产文件哈希与 B1/B2 轮结束时逐字节相同。

---

## 一、原测试为什么永远找不到 pill

上一轮 `renderSessionSection()` 渲染的是 `BookDetailStatsSection(progress, sessions, percent)`。按 `BookDetailStatsSection.kt` 的真实结构（本轮 grep 复核，行号为当前状态）：

| 组件 | 行 | 渲染内容 | 是否创建 `SessionMicroIsland` |
|---|---|---|---|
| `internal fun BookDetailStatsSection` | `:111` | `BookDetailStatCell` 统计格（`:132` 处 `valueModifier = Modifier.durationUnitToggle()`）+ `ReadingHistoryChart` | **否** |
| `internal fun BookDetailSessionsSection` | `:168` | 「阅读记录」标题 + 条数徽标 + `displaySessions.forEach { SessionMicroIsland(...) }`（`:221-226`） | **是，唯一调用点** |
| `private fun SessionMicroIsland` | `:272` | 父行 `Surface(clickable = 展开/收起)`，其内 `Surface(Modifier.durationUnitPill(SESSION_DURATION_PILL_TAG){…})` | pill 在这里 |

也就是说 `sessions` 参数传进 `BookDetailStatsSection` 之后只被用于统计汇总（`bookDetailReadingTimeMs(…, sessions)`、趋势图），**不会**生成任何会话卡片。因此 B1 三条用例的 `onNodeWithTag(SESSION_DURATION_PILL_TAG)` 指向一棵不存在该节点的树：

- `clickingSessionDurationPillExpandsThenCollapsesDetail` 会在 `pill.assertHasClickAction()` 处抛"expected exactly 1 node but 0 found"；
- 另两条同理。

而 `:app:compileDebugAndroidTestKotlin` 只保证类型与符号存在，**不解析运行时语义树**，所以编译绿灯完全不能证明这三条有效 —— 这是上一轮我留下的真实缺陷，复核抓得对。我上一轮报告里写"从真实调用点渲染反而覆盖到父行/父子关系"，这句话本身没错，但我认错了几件：`BookDetailStatsSection` 不是那个真实调用点。

## 二、修复后的真实生产调用链

```
BookDetailSessionsSection(sessions = listOf(sessionFixture()))        ← 测试渲染这里（internal，无需放宽可见性）
└─ DetailIslandCard { SectionTitle("阅读记录") + 「共 N 条」徽标
   └─ Column { displaySessions.forEach { SessionMicroIsland(session, todayStr, yesterdayStr) } }   ← :221-226
      └─ Surface(... .clickable { itemExpanded = !itemExpanded })                                    ← 父行
         └─ Row { 日期文本 · Spacer(weight) 
                   Surface(modifier = Modifier.durationUnitPill(SESSION_DURATION_PILL_TAG)
                                    { itemExpanded = !itemExpanded })                                 ← 被测 pill（生产手势）
                   { Text(formatDuration(session.duration_ms, LocalDurationUnit.current)) }           ← 纯展示
                   进度 pill · 展开箭头 }
            └─ if (itemExpanded) Surface { Column(testTag(SESSION_DETAIL_BODY_TAG)) { 开始/结束时间 } }
```

要求逐条落实：

1. `renderSessionSection()` 改渲染 `BookDetailSessionsSection(sessions = listOf(sessionFixture()))` ✓
2. fixture 用真实字段结构（见第三节），使 `sortedSessions.isNotEmpty()` 成立、`displaySessions.take(3)` 真的产出 1 张卡片 ✓
3. `WithToggle` 现在同时提供 `LocalDurationUnit provides DurationUnit.HOURS`（确定单位）与 `LocalDurationUnitToggle provides { counters.toggles++ }`（计数）✓
4. 测试内不构造 pill / Surface / combinedClickable —— 全文只有 `CompositionLocalProvider` + 生产组件调用，被测手势唯一来自生产的 `Modifier.durationUnitPill` ✓
5. B1 三条继续验证：点击展开 → 再点击收起；长按 `toggles=1` 且详情仍不存在（另加"pill 仍在"防父行整体消失）；`assertNoClickSemanticsOnPillText` 钉住子 Text 不暴露独立手势 ✓
6. `renderSessionSection()` 在 `waitForIdle()` 之后**第一件事**就是 `onNodeWithTag(SESSION_DURATION_PILL_TAG).assertExists("渲染的生产树里必须有会话时长 pill；找不到就是渲染错了组件")` —— 三处入口（三条 B1 用例）都经过它，找错树时失败信息直接指向原因 ✓
7. B2 四条未动渲染入口（仍 `CompletedBookCard`）与业务断言，只跟着做了长按 API 的机械替换 ✓

可见性：**不需要**改动生产代码 —— `BookDetailSessionsSection` 本来就是 `internal`，androidTest 通过 AGP friend-paths 可直接调用。因此允许清单第 2 项（private → internal 的最小改动）**未使用**，`BookDetailStatsSection.kt` 本轮零写入。

## 三、session fixture 字段

```kotlin
ReadingSessionEntity(
    id = "session-1",
    book_id = "book-1",
    started_at = "2026-09-20T10:00:00",   // 驱动 sortedByDescending 与卡片日期标签
    ended_at = "2026-09-20T11:00:00",     // 展开详情里的「结束时间」
    duration_ms = 3_600_000L,             // pill 的时长来源
    progress_percent = 42f,               // 同排的进度 pill
    created_at = "2026-09-20T11:00:00",
    updated_at = "2026-09-20T11:00:00",
)
```
其余字段（`device_id` / `revision` / `payload` / `deleted_at`）走实体默认值。B2 夹具仍是 `CompletedArchiveItem(BookEntity(id,title,format,updated_at), ReadingProgressEntity(book_id,total_reading_time_ms=7_200_000,completion_state="completed",updated_at))`，本轮未改。

## 四、回调计数（修复后仍由生产组件真实触发）

| 用例 | 动作 | toggles | opens | manages | 展开状态 |
|---|---|---|---|---|---|
| B1 点 pill → 再点 | `performClick` ×2 | — | — | — | 不存在 → 显示 → 不存在 |
| B1 长按 pill | `performTouchInput { longClick() }` | **1** | — | — | 仍不存在；pill 仍显示 |
| B1 语义 | — | pill 带 `OnClick`；`OnLongClick.label == "切换时长显示单位"`；pill 内所有带 `Text` 的语义节点无 `OnClick`/`OnLongClick`（并先断言该节点非空，防空转） | | | |
| B2 点 pill | `performClick` | 0 | **1** | 0 | — |
| B2 长按 pill | `longClick` | **1** | **0** | **0** | — |
| B2 点管理按钮 | `performClick` | 0 | 0 | **1** | — |
| B2 语义 | — | 同 B1 | | | |

## 五、弃用 API 清理

```kotlin
// before
import androidx.compose.ui.test.performGesture
...performGesture { longClick() }        // GestureScope.longClick 已弃用
// after
import androidx.compose.ui.test.performTouchInput
...performTouchInput { longClick() }     // TouchInjectionScope.longClick
```

两个调用点（B1、B2 各一处）机械替换，回调断言一字未改。效果实测：本轮 `:app:compileDebugAndroidTestKotlin` 日志里 `grep -c "w: .*DurationPillGestureTest"` = **0**（上一轮该文件有 4 条弃用警告）。`longClick` 的 import 保持 `androidx.compose.ui.test.longClick`，Kotlin 按接收者类型选中 `TouchInjectionScope` 那个重载。

## 六、命令与退出码

均在 `android/` 下经 `powershell.exe -NoProfile -Command` 执行（MSYS 里 `cmd.exe /c` 会假退 0），退出码取 `$LASTEXITCODE`，日志 `Out-File -Encoding utf8 -Width 5000`。

| 命令 | 退出码 | 结果 | 日志 |
|---|---|---|---|
| `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.shelf.BookDetailStatsPolicyTest" --tests "com.creationreadingassistant.ui.util.TimeUtilsTest" --rerun` | **0** | `BUILD SUCCESSFUL in 12s`，**18 tests / 0 failures / 0 errors / 0 skipped**（`BookDetailStatsPolicyTest` 2 + `TimeUtilsTest` 16，`timestamp="2026-09-22T05:57:15"` = 13:57:15 +08:00） | `R1-tests.log`，XML 快照 `app/build/tmp/s15f3r1-evidence/` |
| `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 1m` | `R1-main.log` |
| `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 9s`，本文件 0 警告 | `R1-androidtest-1.log` |
| `git diff --check -- android/app/src/androidTest/.../DurationPillGestureTest.kt` | **0** | 无输出 | — |

上一轮被窗口 B 未跟踪文件（`ui/screen/stats/DurationUnitToggleEntryTest.kt`）卡住的 androidTest 编译门禁，本轮已恢复正常绿色 —— 期间我没有碰对方文件，只是重跑。未运行全量 JVM、Lint、APK、ADB、真机或模拟器。

## 七、文件范围与 SHA-256

| 文件（仓库根相对） | 本轮 | 本轮开始前（= B1/B2 轮结束） | 本轮结束 |
|---|---|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt` | 改（284 行，7 条 `@Test`） | `72f6749dcfd37121038dc0969aa8a4ff1b19d5b00e2e867b9d102022921c0264` | `9d17ebd89a0a342f8e14eb9fec509c540f342ef1e7a82e1a730d625989fc1286` |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailStatsSection.kt` | **未改** | `a1273c6884bc2e22468b7f396c165619ccec1bda9db7113d730bb01db23d2d4f` | 同左（逐字节一致） |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeCompletedScreen.kt` | **未改** | `4385b64ccc7b6b9b4dcee03cf4bde003050fe281ffc23507bf1d1b6122c01a87` | 同左（逐字节一致） |

本轮写入面只有那一个测试文件 + 本报告，未新增任何其他文件。`android/*.log` 与 `app/build/**` 均被 gitignore。

## 八、开始前 / 结束后的 git status --short

**开始前**：`ui/screen/shelf/` 与 `ui/screen/homearchive/` 整片因多个窗口的未提交工作长期为 `M`（含 `BookDetailStatsSection.kt`、`HomeCompletedScreen.kt`、`ShelfUtils.kt`、`HomeArchiveFormatters.kt` 等），`DurationPillGestureTest.kt` 为上一轮新增的 `??`，`docs/coordination/` 整体 `??`。

**结束后**（限定本片授权路径 + `docs/coordination/`）：

```
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailStatsSection.kt     ← 他人的未提交差异 + 上一轮本片改动（本轮未写）
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeCompletedScreen.kt  ← 同上（本轮未写）
?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt   ← 本轮唯一写入的文件
?? docs/coordination/                                                                                      ← 含本报告
```

（同目录下另有 14 个 ` M` 的 shelf/homearchive 文件属窗口 B/D 的在途改动，本片一行未动，也未清理。）状态类别与开始前完全相同：没有新增/删除版本库文件，没有把任何人的文件从 `??` 变成 `M` 或反向。

## 九、仍未闭环

1. **这 7 条 Compose 用例依旧没在设备跑过**（本片禁止 ADB/真机/模拟器）。本轮修正的是"渲染对象与存在性前置断言"，把"必然找不到节点"变成"结构上正确、断言明确"，但它仍然是未运行的断言。带设备窗口需要按第四节的表逐条验一次，特别是我推断的两点：`performTouchInput { longClick() }` 会真正走 `combinedClickable` 的长按分支且不补发点击；`LocalDurationUnit` 被测试替换后 `formatDuration` 的取值仍稳定（本用例不断言文本，所以单位不影响结果）。
2. 上一轮报告第八节 3、4 两条遗留不变：`BookDetailStatsSection.kt:132` 的统计格与 `HomeCompletedScreen.kt:246` 的头部 pill 仍是"手势在文本上"；`durationUnitPill` 仍寄居在 `ui/screen/shelf/BookDetailStatsSection.kt`，等窗口 B 的 `DurationDisplay.kt` 收口后应搬迁并让 `Modifier.durationUnitToggle` 用量归零。
3. 新增一条建议（本轮由这次翻车暴露）：pill 手势测试目前只靠"渲染 + 存在性断言"来保证测的是对的东西。若允许新增 JVM 测试，可加一条按文件路径断言"`BookDetailSessionsSection` 内出现 `SessionMicroIsland(`、且 `durationUnitPill(` 出现在其子树内"的结构守卫，让"测试渲染错组件"这类问题在 JVM 阶段就被拦下。本轮允许清单不含新 JVM 测试文件，故未做。

## 十、声明

- 未进入、未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未 `commit` / `push` / `stage` / `reset` / `checkout` / `clean` / `stash` / 全仓格式化；未清理或回滚任何人的未提交改动。
- 本轮**未修改任何生产代码**（两个生产文件哈希逐字节相同），未修改 `HomeCompletedScreen` 生产实现，未使用"private → internal"的例外许可（`BookDetailSessionsSection` 本就是 internal）。
- 未触碰窗口 B/D 文件、公共组件、`ReaderScreenTest.kt`、legacy EPUB、协调看板、handoff、Obsidian；窗口 B 的 `DurationUnitToggleEntryTest.kt` 仅读取路径与 mtime 用于解释上一轮阻断原因。
- 未运行全量 JVM、Lint、APK 构建、ADB、真机或模拟器；未杀任何进程。

**未领取下一任务**：是。S15-F3-B12-R1 到此停止，等待 Codex 复核。
