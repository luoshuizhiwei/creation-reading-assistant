# S15-F3-B3 结果报告（窗口 D / Qoder）— 阅读历史行的长按语义唯一归「打开管理操作」

- 任务：S15-F3-B3｜上游：`docs/coordination/designs/agent-design-s15-f3-b-qoder.md` §4.3、`agent-result-s15-f4-r1-qoder.md`
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`｜固定基点：`5aed5939600e491843e3c44453921bd6273a233c`（HEAD 未动）
- 时间：2026-09-22 12:40–13:35（本机 +08:00）
- 本轮写入（且仅这三处）：
  1. `android/app/src/main/java/com/creationreadingassistant/ui/screen/readinghistory/MyReadingScreen.kt`
  2. `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/readinghistory/MyReadingTimelineRowGestureTest.kt`（新增）
  3. 本报告
- 结论：**行内时长 pill 已降级为纯展示（无 OnClick / 无 OnLongClick），整行保持「点击打开书籍 + 长按打开管理操作」并补上两个语义标签。** 主源码编译门禁通过（exit 0）；阅读历史既有定向 JVM 测试 6/6 通过；**`:app:compileDebugAndroidTestKotlin --rerun` 当前 exit 1，阻塞点在窗口 B 的在途文件 `ui/screen/stats/DurationUnitToggleEntryTest.kt`（3 条编译器错误全在该文件），本片新增文件在同一轮全量重编里 0 条诊断** —— 详见第六节，未声称该门禁已通过。

---

## 一、Codex 产品裁决（本轮照此实现，未自行改判）

> `TimelineBookRow` 的长按语义唯一归「管理书籍」。

裁决理由（原文五点，逐条落到实现）：
1. 整行已存在稳定的业务长按：打开管理操作 → 保留 `onLongClick = onManage`，并加 `onLongClickLabel`。
2. 同一行不同区域长按执行不同功能，视觉上不可发现且难以预测 → 移除 pill 的长按。
3. 页面顶部已有聚合时长快捷入口，全局单位仍可从外观设置切换 → 页首 `MyReadingScreen.kt:248` 的 `DurationText` **未动**，仍是本屏的切单位入口。
4. 行内已有显式管理按钮，但这不代表可以让时长 pill 抢走整行既有长按语义 → pill 去手势，管理按钮保持可用。
5. 每个语义组件最多一个长按行为 → 新增的 androidTest 直接钉住「整行子树里带长按动作的节点恰好 1 个」。

裁决后的事件契约：整行普通点击=打开书；整行长按=打开管理操作；行内时长 pill=纯展示；行内管理按钮=打开管理操作；时长仍跟随 `LocalDurationUnit`。

## 二、修改前后事件矩阵（生产代码层面）

| 位置 | 修改前 | 修改后 |
|---|---|---|
| 整行（`IslandCard` 外层 Box，:490-500） | 点击=打开书；长按=管理操作；**无 `onClickLabel`/`onLongClickLabel`**（读屏只听到"双击即可激活/长按即可激活"） | 点击=打开书（label「打开书籍」）；长按=管理操作（label「打开管理操作」）；Role 与节点结构未变 |
| 行内时长文本（:578-584） | `DurationText` → 内部 `combinedClickable(onClick = {}, onLongClick = toggle)`：落在文字上的点击**吞掉"打开该书"**，长按**吞掉"管理操作"** | 纯 `Text(formatDuration(ms, LocalDurationUnit.current))` + `testTag`：**无 OnClick、无 OnLongClick、无 toggle**，显示值继续跟随全局单位 |
| 行内管理按钮（:588-593） | `IconButton(onClick = onManage)`，无 tag | 回调与视觉不变，新增 `testTag("reading-item-manage-<id>")` |
| 页首聚合时长（:248） | `DurationText`（切单位入口） | **未改**（裁决理由 3 依赖它） |

## 三、时长 pill 的最终 semantics

- 节点内容：`Text` 自身语义（`Text` 属性 + `TestTag`），**不含** `SemanticsActions.OnClick`、不含 `SemanticsActions.OnLongClick`、不含 `Role`、不含 `OnClickLabel`。
- 因为父行 `combinedClickable` 会合并后代语义，pill 节点在**合并树**里不再是独立可激活节点；测试用 `useUnmergedTree = true` 取它自己的节点来断言上述"没有动作"。
- 视觉零改动：沿用原来的 `style = bodySmall`、`color = onSurfaceVariant`、外层 `Row(spacedBy(6.dp))` 与 `ReadingStateBadge` 的相对位置，只把组件从 `DurationText` 换成等价的 `Text`；未复制任何格式化/进位逻辑，一律走公共 `formatDuration`。

## 四、回调计数契约（新增 androidTest 的断言面）

新文件 `android/app/src/androidTest/.../ui/screen/readinghistory/MyReadingTimelineRowGestureTest.kt`，渲染的是**生产入口** `MyReadingScreen(state, onAction)`（public，参数为纯数据 + 动作回调），因此不需要把 `private` 的 `TimelineBookRow` 改成 `internal`，也没有任何自造替身组件；断言同时覆盖真实动作映射 `MyReadingAction.OpenBook` / `ManageBook`。

| 用例 | 操作 | 期望计数 |
|---|---|---|
| `rowClick_opensTheBook_andDoesNotOpenManage` | 整行 `performClick()` | `actions == [OpenBook]`；`ManageBook` 0；`toggleCount == 0` |
| `rowLongClick_opensManage_andDoesNotOpenTheBook` | 整行 `performTouchInput { longClick() }` | `actions == [ManageBook]`；`OpenBook` 0；`toggleCount == 0` |
| `manageButton_stillOpensManage` | 管理按钮 `performClick()` | `ManageBook == 1`；`OpenBook == 0` |
| `durationPill_exposesNoClickAndNoLongClick` | 读 pill 自身语义 | `contains(OnClick) == false`、`contains(OnLongClick) == false` |
| `longClickAtDurationPillCoordinates_belongsToManage_andDoesNotToggleUnit` | 在 **pill 坐标**长按 | `actions == [ManageBook]`；`toggleCount == 0`（隐藏入口若残留必红） |
| `row_exposesExactlyOneLongClick_labeledForManage` | 读整行语义 + 遍历整行子树 | 整行 `OnLongClick.label == "打开管理操作"`、`OnClick.label == "打开书籍"`；子树内带长按动作的节点 `== 1` |
| `durationText_followsHoursUnit_withoutAnyGesture` / `...DaysUnit...` | 分别提供 `LocalDurationUnit = HOURS / DAYS` | 同一节点文本 `30 小时 0 分钟` ↔ `1 天 6 小时`，且两制下 pill 都无手势 |

定位一律走稳定 tag（`reading-item-<id>` / `reading-item-duration-<id>` / `reading-item-manage-<id>`），只有断言显示值时才比对中文文本；行上方还有总览/筛选/搜索三块，`show()` 里先 `performScrollToNode(hasTestTag(rowTag))` 再断言，避免依赖具体视口高度。

## 五、为什么不修改 HomeReadingArchiveSection

按 Codex 裁决本轮**明确不改**该文件，理由是它是**导航卡**：`.clickable(role = Role.Button, onClick = 进入阅读档案)` 是它唯一的既有语义，把"长按切全局单位"塞进整张卡等于在用户不知道的情况下给一张导航卡挂上隐藏的全局开关，且它的时长是 `"累计 12 小时 0 分钟 · 5 本"` 的整句聚合文本，长按命中面会连带"· 5 本"。该卡继续只**跟随**全局单位（读 `LocalDurationUnit`），切换入口留在无父级点击冲突的时长文本上。此裁决记录于此，本轮不产生代码改动。

## 六、命令与退出码（逐条如实）

| 命令（均在 `android/` 执行） | exit code | 结果 |
|---|---|---|
| `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | `BUILD SUCCESSFUL in 46s`，`15 actionable tasks: 1 executed`（唯一 `w:` 是 `LibraryBrowserRoute.kt` 既有图标弃用告警，非本片文件） |
| `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | **1** | `BUILD FAILED`，3 条 `e:` 全在 `androidTest/.../ui/screen/stats/DurationUnitToggleEntryTest.kt`（窗口 B 的 F3-A 在途文件）。本片新增文件在同一轮**强制全量重编**里被诊断的行数 = **0**（`grep -c "MyReadingTimelineRowGestureTest" log` → 0；同一模块里我的文件与他的文件会同时报诊断，早期一轮就同时出现过两类错误，所以"0 条"是有意义的证据）。时间线：加入我的文件之前基线 `exit 0` → 我第一版少一个 `longClick` import 报错、修好后（13:0x 一轮）整门禁 `exit 0` 且我的文件带 1 条 `w: Condition is always 'true'`（第六节末的 API 陷阱）→ 按该告警改成 `contains()` 之后，门禁被窗口 B 新落地的文件挡住，本片文件 0 诊断 → 未声称该门禁当前通过 |
| `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests com.creationreadingassistant.ui.viewmodel.MyReadingViewModelTest --rerun`（相关的现有阅读历史定向 JVM 测试） | **0** | XML：`tests="6" skipped="0" failures="0" errors="0"`，`time="7.648"` |
| `git diff --check -- <生产文件> <测试文件>` | 0 | 无输出 |
| `git diff --no-index --check -- NUL <新增测试文件>` | 1 | 无任何输出行（untracked 文件"内容不同但无空白错误"的正常码；真空白错误会打印并返回 3，本轮用带行尾空白的对照文件校准过） |

未运行：全量 JVM、Lint、APK 打包、ADB、真机、模拟器。

**新增 androidTest 尚未在设备上执行过**：本轮只做到编译级验证（且被并行窗口卡住一道门禁），运行与 TalkBack 验收属真机时段（本项目 MIUI 测试机对 instrumentation 安装有限制，需可跑 Compose 测试的设备窗口）。不把"编译通过"说成"测试通过"。

取证过程中纠正的两个 API 事实（对本片是硬约束，写下来避免重犯）：
- `SemanticsConfiguration` **没有** `getOrNull(...)`（`javap androidx.compose.ui.semantics.SemanticsConfiguration` from compose-ui 1.9.0 只给出 `get` / `getOrElse` / `contains`）；
- `config[key]` 的 Kotlin 签名是**非空 `T`**，缺键时靠 `as T` 取值会抛，所以 `config[SemanticsActions.OnClick] == null` 这种写法既是编译器警告 `Condition is always 'true'`（我第一版就吃到了）也会运行时抛异常。正确形状是 `contains(key)` 判定后再取。

## 七、文件 SHA-256 与状态

| 文件 | SHA-256 | 大小 | mtime |
|---|---|---|---|
| `…/ui/screen/readinghistory/MyReadingScreen.kt`（改后） | `6a69a8165fb336457d18b339a406032b0d67cfd31968fa4d0f85e89ced0f24d1` | 27410 B | 2026-09-22 13:07:32 +08:00 |
| `…/androidTest/…/readinghistory/MyReadingTimelineRowGestureTest.kt`（新增） | `8253e598d5cdc7b868095d41eae50feb41e555d8ab308a243cc08b9d86a64d7b` | 11930 B | 2026-09-22 13:19:58 +08:00 |

生产改动共 4 处（逐条）：①  :490-500 整行 `combinedClickable` 补 `onClickLabel`/`onLongClickLabel` + 归属注释；② :578-584 时长 pill 换纯 `Text` + `testTag`；③ :588-593 管理按钮加 `testTag`；④ :55-57 新增 `LocalDurationUnit` / `formatDuration` 两个 import（`DurationText` import 仍被 :248 使用，未删）。

需要说明的归属边界：`MyReadingScreen.kt` 本轮之前已是 `M`（工作区既有未提交状态，mtime 停在 09-15 23:35），其中 `:248` 的 `DurationText` 迁移与文件尾部 `formatReadingTime` 的删除**不是本片做的**（我读到的工作区现状就已如此，我也从未改这两处）；因此本文件的 `git diff` 混合了他方既有改动与本片 4 处，无法靠 git 单独切出我的差量 —— 上表逐条列出了本片实际改动的行位，SHA 给的是当前认证内容。

## 八、开始与结束的 git status

- 开始基准：12:20（本轮动手前，同会话 F3-B-DESIGN 结束时采集）`git status --short | wc -l` = **379**，暂存 0，HEAD `5aed5939…`。
- 结束：`git status --short | wc -l` = **382**（增量为并行窗口新落地的文件，非本片），`git diff --cached --name-only | wc -l` = **0**，`?? ` 条目 148，HEAD 仍 `5aed593`。
- 本片可归因差量（显式路径核对）：
  ```
   M android/app/src/main/java/com/creationreadingassistant/ui/screen/readinghistory/MyReadingScreen.kt
  ?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/readinghistory/
  ```
  （`readinghistory` androidTest 目录为本片首次创建，目录内只有这一个文件；`docs/coordination/` 整目录 untracked，被折叠成一行，所以条目数不体现报告文件。）
- 时间戳的读法要先讲清，否则本节会被误读：本轮期间 `ui/util/DurationDisplay.kt`（13:09）、`data/settings/DurationPrefs.kt`（13:10）、`ui/screen/homearchive/HomeCompletedScreen.kt`（13:13）、`ui/screen/shelf/BookDetailStatsSection.kt`（13:22）的 mtime 都在动 —— 那是窗口 B（F3-A）与窗口 C（F3-B1/B2）在写它们自己的文件，**本片一行都没写过**。本片实际只落盘了第一节列出的三个文件；本节只报客观时间，不据此评价他窗口的改动内容。

## 九、边界与合规声明

- 未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未 commit / push / stage / stash / reset / checkout / clean，未全仓格式化；HEAD 等于固定基点，暂存区为空（第八节实测）。
- 未修改：`DurationDisplay.kt`、`DurationPrefs.kt`（窗口 B / F3-A）、`BookDetailStatsSection.kt`、`HomeCompletedScreen.kt`（窗口 C 在途）、`HomeReadingArchiveSection.kt`（裁决本轮不改）、`SharedComponents.kt`、`MicroIsland.kt`、`build.gradle.kts`、协调看板、handoff、Obsidian。对窗口 B/C 的在途文件只做了"是哪条编译器错误挡住门禁"的客观归因，未改其一字、未评价其设计。
- 未新增依赖、未改测试夹具以外的任何构建配置；测试数据是中性夹具（"测试书甲" / `测试 TXT` 格式），不含真实书名，也不触碰设备数据。
- 本轮未使用真机/模拟器；最终真实手势与 TalkBack 播报验收必须在连接的真实手机上做（禁 MuMu），本轮未执行。

## 十、遗留（交给集成者/Codex，不自行领取）

1. **门禁解锁**：需要窗口 B 修掉 `DurationUnitToggleEntryTest.kt` 的 3 条未解析引用（`onAllNodes` / 两处 `getOrNull`），`:app:compileDebugAndroidTestKotlin` 才会整体转绿。我在 13:20、13:30、13:40 前后各重试过一次（v6 带 `--rerun` 的全量重编、v7、v8），最后一次仍是同一外部文件挡住，本片交付时该门禁**未绿**；届时请重跑第二道命令并把退出码/XML 补进验收记录。
2. **真机清单（本片未验）**：长按 pill 坐标是否稳定落到整行管理（现只有编译期保证，语义树与手势仲裁需实测）、长按抬手不误触发导航、pill 命中盒是否等于可见胶囊、TalkBack 是否播报"打开管理操作/打开书籍"两个动作名、Lazy 复用下切换单位后所有可见行是否即时更新。
3. 若产品之后希望"这一行也能就地长按切单位"，那属于推翻 §一裁决 5，需要重新裁决而不是局部补丁。

完成本报告后停止，不领取下一任务。
