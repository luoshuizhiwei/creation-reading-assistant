# S07-F1 修复执行报告（Zcode）

> Agent：Zcode（模型 `deepseek v4.1Flash`）
> 日期：2026-09-20
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 任务来源：`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md` 的 Spec P1 第 1、2 项
> 本轮范围：只修「`edit` 死按钮」与「复制虚假成功」两个已确认问题
> 最终状态：**等待 Codex/Qoder 复核**

---

## 1. 修改文件清单

| 文件 | 类型 | 改动性质 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/data/settings/SelectionActionSettings.kt` | M | 新增 `DORMANT_IDS`；`pickableDefs()` 排除 dormant；注释与 S08 对齐 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderLayerBuilders.kt` | M | 新增 `readerCopyNotice()`；`onCopy` 改用 `copyText` 返回值 |
| `android/app/src/test/java/com/creationreadingassistant/data/settings/SelectionActionSettingsTest.kt` | M | 替换 1 个、新增 3 个用例（`SelectionActionsTest`） |
| `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionToolbarModelTest.kt` | M | 替换 1 个、新增 1 个用例 |
| `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderCopyNoticeTest.kt` | **新增** | 问题 B 的窄函数测试（3 用例） |
| `docs/coordination/agent-results/agent-result-s07-f1-zcode.md` | **新增** | 本报告 |

**未修改**：`ReaderSelectionToolbar.kt`（在授权清单内，但经核实不需要改动 —— 见 §2.1「为什么没有改工具栏」）。

---

## 2. 问题 A：`edit` 死按钮

### 2.1 根因

`edit` 的动作定义存在于 `SelectionActions.ALL`，且 `pickableDefs()` 只按
`group != FIXED` 过滤，所以 **`edit` 落在「可配置清单」里**：

- `SelectionSubPage.kt:115` 用 `SelectionActions.pickableDefs()` 渲染第一屏可选项；
- `SelectionSubPage.kt:162` 用 `pickableDefs().filterNot { it.id in settings.enabledPrimary }` 渲染「更多」可选项；
- `SelectionActionStore.setActionEnabled()`（`:60`）只拒绝 `FIXED` 组，因此 `edit` **可以真的被写进 DataStore**；
- 而生产分发 `ReaderSelectionToolbar.onActionClick`（`:162–177`）**没有 `"edit"` 分支**，点击落入 `else -> Unit`。

结果：用户能在设置页把「编辑」勾出来，工具栏渲染它，点击毫无反应。

**原注释声称「接线前绝不放死按钮」，但只把 `edit` 移出了 `DEFAULT_PRIMARY` / `DEFAULT_MORE`。** 默认集合本来就不含它，所以该措施完全挡不住「用户主动勾选」这条真实可达路径 —— 这正是任务指出的要点。

### 2.2 修复方式

在 `SelectionActions` 中引入显式的 **dormant（未接线）门控**，并把门控收敛到**唯一的可配置清单出口** `pickableDefs()`：

```kotlin
val DORMANT_IDS: Set<String> = setOf("edit")

fun pickableDefs(): List<SelectionActionDef> =
    ALL.filter { it.group != SelectionActionGroup.FIXED && it.id !in DORMANT_IDS }
```

这一个改动同时堵住全部四条路径，因为它们都经过 `pickableDefs()`：

| 路径 | 位置 | 效果 |
|---|---|---|
| 设置页第一屏可选项 | `SelectionSubPage.kt:115` | `edit` 不再出现 |
| 设置页「更多」可选项 | `SelectionSubPage.kt:162` | `edit` 不再出现 |
| 写盘校验 | `SelectionActionStore.setActionEnabled` 走 `byId`（仍返回定义）+ `group != FIXED` | 见下方说明 |
| 渲染清单 | `effectivePrimary` / `effectiveMore` → `pickableDefs()` | `edit` 不渲染 |
| 读盘收敛 | `sanitize` 的 `knownPickable = pickableDefs()` | 旧磁盘里的 `edit` 被丢弃 |
| 高频槽兜底 | `selectionToolbarActions` 的 `pickableDefs()...take(1)` | 不会兜底到 `edit` |

**关于写盘校验**：`setActionEnabled` 用 `SelectionActions.byId(id)` 取定义（`byId` 仍返回 `edit`，因为定义必须保留给 S08），因此该方法本身不会主动拒绝 `edit`。但这不是漏洞：设置页已不再提供 `edit` 的勾选入口，而**读路径的 `sanitize` 会把它丢掉** —— 即使有人手改 DataStore 写入 `edit`，`SelectionActionStore.settings`（`:28–41`）出口的 `sanitize` 会移除它，UI 永远拿不到含 `edit` 的配置。防御落在读出口，这是该 store 既有且一致的纪律（注释 `SelectionActionStore.kt:19–20` 明确写了「读路径一律 sanitize」）。

**为什么没有改 `ReaderSelectionToolbar.kt`**：任务要求「工具栏也不能渲染 `edit`」。工具栏的两个渲染清单来自 `selectionToolbarActions()`，而它内部完全由 `effectivePrimary` / `effectiveMore` / `pickableDefs` 驱动。门控加在数据层后，工具栏天然不再收到 `edit`，无需在 UI 层再写一遍同样的判断（重复判断反而会掩盖数据层失效）。新增的 `toolbar never renders a dormant action even when it is configured` 用例直接断言了这条端到端契约。

**为什么没有删除 `edit` 定义**：任务明确要求保留 S08 所需定义。`ALL` 中的 `SelectionActionDef("edit", ...)` 原样保留，声明位置不变，并新增用例锁定它仍位于 `replace` 与 `dictionary` 之间 —— S08 接线时只需把 id 移出 `DORMANT_IDS` 并补上分发分支。

### 2.3 新增/修改的测试

`SelectionActionsTest`（`SelectionActionSettingsTest.kt`）：

| 用例 | 断言 |
|---|---|
| `edit is dormant until the chapter editor is wired`（保留，未改） | 默认集合不含 `edit` |
| `dormant edit is neither configurable nor renderable`（**新增**） | `pickableDefs()` 不含 `edit`；即使强制塞进两个集合，`effectivePrimary` / `effectiveMore` 也不渲染 |
| `sanitize drops a stored dormant action id`（**新增**） | 旧磁盘数据里的 `edit` 被 `sanitize` 丢弃，同时不误伤 `copy` / `note` |
| `edit keeps its declared landing slot for the chapter editor slice`（**替换**原 `edit renders between replace and dictionary once it is enabled`） | `edit` 定义仍在 `ALL` 中，且紧邻 `replace` / `dictionary` |

> 原用例 `edit renders between replace and dictionary once it is enabled` 断言的是「启用后应渲染 `edit`」，这与修复目标直接冲突（修复后 `edit` 不可渲染），因此改写为只锁声明顺序。

`ReaderSelectionToolbarModelTest`：

| 用例 | 断言 |
|---|---|
| `pickable set excludes fixed and dormant actions`（**替换**原 `pickable set excludes only the fixed group`） | 可配置清单 = 全部非固定动作减去 dormant；显式断言 `cancel` 与 `edit` 都不在其中 |
| `toolbar never renders a dormant action even when it is configured`（**新增**） | 配置含 `edit` 时，工具栏高频槽与溢出菜单都不渲染 `edit`，其余动作不受影响 |

---

## 3. 问题 B：复制虚假成功

### 3.1 根因

`ReaderLayerBuilders.kt` 原实现（修复前 341 行）：

```kotlin
onCopy = { clipboard.copyText(selectedText, label = "reader_selection"); showNotice("已复制") },
```

`Clipboard.copyText`（`ClipboardUtils.kt:34–53`）的返回值就是「内容是否真的进了剪贴板」：

- 空白选区 → `copyableSelection` 返回 null → `return false`，**且不触碰剪贴板**（保留用户原有内容）；
- 系统剪贴板服务异常（`SecurityException` / `IllegalStateException` / `RuntimeException`）→ `runCatching` 降级为 `false`。

原调用点丢弃返回值并无条件提示「已复制」。用户会收到虚假成功，且不会再去重新复制 —— 等于静默丢内容。注释 `ClipboardUtils.kt:16` 本身就把这种组合称为「一次静默的数据破坏叠加一次虚假成功」，但 S07 调用点没有遵守。

### 3.2 修复方式

把提示决策提为窄的、可单测的纯函数，并接到真实返回值上：

```kotlin
internal fun readerCopyNotice(succeeded: Boolean): String =
    if (succeeded) "已复制" else "复制失败，请重试"
```

```kotlin
onCopy = {
    val copied = clipboard.copyText(selectedText, label = "reader_selection")
    showNotice(readerCopyNotice(copied))
},
```

- 成功文案保持 `"已复制"` 不变（不改动既有用户可见契约）；
- 失败文案为 `"复制失败，请重试"` —— 简短、可行动；
- 成功/失败文案必然不同（有用例锁定），用户能区分。

**范围纪律**：只改了选区工具条的 `onCopy`。`ClipboardUtils.copyText` 本身未改（其语义已正确）；AI 页面、灵感页、诊断页、书摘卡等其它剪贴板调用点（`grep` 到的 8 处）**一律未动**，按任务要求另立切片。

### 3.3 新增测试

`ReaderCopyNoticeTest`（新文件，3 用例，纯 JVM）：

| 用例 | 断言 |
|---|---|
| `a successful clipboard write reports success` | `readerCopyNotice(true) == "已复制"` |
| `a failed clipboard write never claims success` | 失败文案 ≠ `"已复制"`，且非空白（不得静默） |
| `success and failure notices are distinguishable` | 两种结果文案不同 |

设计上刻意只测这条窄函数，不挂载整个 `ReaderScreen`/Compose 树 —— 符合任务「避免为了测试挂载整个 Reader 页面」的要求。

---

## 4. 命令、退出码与测试统计

### 4.1 开始前记录（基线）

```
$ git status --short
（315 行输出；工作区既有 209 个已修改跟踪文件 + 104 个未跟踪项，全部视为用户资产，未做任何还原）

$ git rev-parse --abbrev-ref HEAD
codex/recover-worktree-20260920
$ git rev-parse HEAD
5aed5939600e491843e3c44453921bd6273a233c

$ git diff origin/main -- <本轮拥有的三个源文件>
（275 行；hunk 头：SelectionActionSettings.kt @@ -47,7 +47,15 @@ / @@ -61,24 +69,62 @@；
 ReaderLayerBuilders.kt @@ -17,6 +17,7 @@ / @@ -160,7 +161,29 @@ / @@ -175,8 +198,8 @@ /
 @@ -185,7 +208,11 @@ / @@ -220,17 +247,58 @@；
 ReaderSelectionToolbar.kt @@ -53,11 +53,14 @@ / @@ -152,10 +155,12 @@ / @@ -228,7 +233,10 @@ / @@ -248,7 +256,10 @@）
```

### 4.2 先补测试 → RED（修复前）

```
$ ./gradlew.bat :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.data.settings.SelectionActionsTest" \
    --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest"
退出码：1
SelectionActionsTest > sanitize drops a stored dormant action id FAILED
    java.lang.AssertionError at SelectionActionSettingsTest.kt:101
SelectionActionsTest > dormant edit is neither configurable nor renderable FAILED
    java.lang.AssertionError at SelectionActionSettingsTest.kt:76
ReaderSelectionToolbarModelTest > toolbar never renders a dormant action even when it is configured FAILED
    java.lang.AssertionError at ReaderSelectionToolbarModelTest.kt:147
ReaderSelectionToolbarModelTest > pickable set excludes fixed and dormant actions FAILED
    java.lang.AssertionError at ReaderSelectionToolbarModelTest.kt:123

33 tests completed, 4 failed
```

4 个新断言在未修复代码上真实失败，证明测试确实能暴露问题（而非恒真）。

### 4.3 修复后 → GREEN（强制全新执行）

```
$ ./gradlew.bat :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.data.settings.SelectionActionsTest" \
    --tests "com.creationreadingassistant.data.settings.SelectionActionStoreTest" \
    --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest" \
    --tests "com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest" \
    --rerun-tasks
退出码：0
BUILD SUCCESSFUL in 2m 41s
30 actionable tasks: 30 executed
```

XML 结果核对（`app/build/test-results/testDebugUnitTest/`）：

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `com.creationreadingassistant.data.settings.SelectionActionsTest` | 24 | 0 | 0 | 0 |
| `com.creationreadingassistant.data.settings.SelectionActionStoreTest` | 13 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest` | 9 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest` | 3 | 0 | 0 | 0 |
| **合计** | **49** | **0** | **0** | **0** |

**测试类名核对**：任务提醒 `SelectionActionSettingsTest` 是文件名、真实类名是 `SelectionActionsTest`。已按真实类名过滤，并从 XML 的 `testsuite/@name` 逐条确认目标类确实执行（上表 `name` 即为 XML 中的真实类名）。同文件内的第二个类 `SelectionActionStoreTest` 也一并显式指定并确认执行。

**新增用例执行确认**（从 XML 的 `testcase/@name` 读取，7 条）：

```
SelectionActionsTest :: edit is dormant until the chapter editor is wired
SelectionActionsTest :: dormant edit is neither configurable nor renderable
SelectionActionsTest :: sanitize drops a stored dormant action id
SelectionActionsTest :: edit keeps its declared landing slot for the chapter editor slice
ReaderSelectionToolbarModelTest :: pickable set excludes fixed and dormant actions
ReaderSelectionToolbarModelTest :: toolbar never renders a dormant action even when it is configured
ReaderCopyNoticeTest :: a successful clipboard write reports success / a failed ... / success and failure notices are distinguishable
```

### 4.4 编译检查

```
$ ./gradlew.bat :app:compileDebugKotlin
退出码：0
BUILD SUCCESSFUL in 3s   （UP-TO-DATE —— 已作为测试任务的依赖真实编译过）

$ ./gradlew.bat :app:compileDebugAndroidTestKotlin
退出码：0
BUILD SUCCESSFUL in 5s   （2 executed）
```

androidTest 编译一并跑过：`ReaderSelectionToolbarTest` 用的是组件默认参数（不经过 `pickableDefs`），不受本次门控影响；编译成功确认没有连带破坏。

### 4.5 相邻测试回归检查

额外跑了引用 `SelectionAction*` 的另外两个类，确认门控未破坏既有行为：

| 测试类 | tests | failures | errors | skipped | 退出码 |
|---|---:|---:|---:|---:|---:|
| `ui.screen.reader.ReaderSelectionExternalActionsTest` | 14 | 0 | 0 | 0 | 0 |
| `ui.viewmodel.SettingsViewModelTest` | 8 | 0 | 0 | 0 | 0 |

### 4.6 收尾检查

```
$ git diff --check -- <本轮拥有的 5 个文件>
退出码：0   （仅有一条 CRLF 提示，无空白错误）

$ git status --short
（仅下列变化属于本轮，其余 300+ 项为工作区既有用户资产，未触碰）
 M android/app/src/main/java/.../data/settings/SelectionActionSettings.kt
 M android/app/src/main/java/.../ui/screen/reader/ReaderLayerBuilders.kt
 M android/app/src/test/java/.../data/settings/SelectionActionSettingsTest.kt
 M android/app/src/test/java/.../ui/screen/reader/ReaderSelectionToolbarModelTest.kt
?? android/app/src/test/java/.../ui/screen/reader/ReaderCopyNoticeTest.kt
?? docs/coordination/agent-results/agent-result-s07-f1-zcode.md
```

> 说明：整仓 `git diff --check`（不限路径）退出码为 2，报出 7 处 `new blank line at EOF`，全部位于 `AppearanceSubPage.kt`、`ProfileHomeScreen.kt`、`ReaderTtsController.kt`、`SearchScreen.kt`、`ShelfSearchRoute.kt`、`SummaryGroup.kt`、`BenchmarkHelpers.kt` —— 均为**工作区既有改动**，不在本轮所有权范围内，未触碰、未修正。

---

## 5. 本轮实际改动（精确 delta）

相对我开工时捕获的基线，源文件只增加以下内容（未改动任何其它 hunk）：

**`SelectionActionSettings.kt`**：新增 `DORMANT_IDS` + `pickableDefs()` 增加 dormant 过滤 + 3 处注释更新（`edit` 定义处、`DEFAULT_PRIMARY`、`DEFAULT_MORE`）。

**`ReaderLayerBuilders.kt`**：新增 `readerCopyNotice()`（13 行，含 KDoc）+ `onCopy` 从 1 行改为 4 行。

未触碰的既有 S07 改动包括（同文件内，属其它 hunk，本轮一字未动）：`MAX_PRIMARY_SLOTS` 3→5、`ALL` 顺序重排、`DEFAULT_*` 新值、`LEGACY_DEFAULT_PRIMARY`、`resolveStoredPrimary`、`sanitize`；以及 `ReaderLayerBuilders.kt` 中 M-BUG-08 章导航、一键日/夜灯绳、TTS 语速等 hunk。

---

## 6. 未验证项

- **未做真机验证**。本轮的「设置页不再出现『编辑』」与「复制失败提示」只有 JVM 层证据，没有真机截图。按任务约束（不使用 MuMu、不操作真实手机）由 WorkBuddy 后续验收。
- **设置页 UI 未做 Compose 仪器测试**。新增门控断言在 `pickableDefs()` 数据层与 `selectionToolbarActions()` 渲染层，未新增 androidTest 用例挂载 `SelectionSubPage` 断言「编辑」pill 消失。数据层是唯一出口，风险低，但 UI 层直接证据缺失。
- **「复制失败」文案未经产品确认**。`"复制失败，请重试"` 是我按「明确、简短」要求拟的；任务未给指定文案。若 Codex/产品有既定措辞，需替换（改动只在 `readerCopyNotice` 一处）。
- **失败分支的真实触发未复现**。`copyText` 返回 false 的两条路径（空白选区、系统剪贴板异常）中，空白选区路径由 `copyableSelection` 的既有单测覆盖；系统异常路径需 ROM/受限环境，未复现。
- **未验证旧 DataStore 中已存在 `edit` 的真实升级路径**。`sanitize` 丢弃 `edit` 有单测（`sanitize drops a stored dormant action id`），但未在真实设备上验证「装过旧版、盘里已有 `edit`」的升级场景。
- **窄屏 5 槽拥挤**（正式报告 U2）与本次修复无关，仍未验证。

---

## 7. 合规声明

- **是否修改了授权范围以外文件**：**否**。改动仅落在授权清单内的 `SelectionActionSettings.kt`、`ReaderLayerBuilders.kt`、两个既有 JVM 测试文件，加上新增的 `ReaderCopyNoticeTest.kt` 与本报告。`ReaderSelectionToolbar.kt` 在授权清单内但经核实无需改动。未触碰翻页、切章、Markdown、EPUB、选区坐标代码，未改 Gradle / Room / 桌面端 / 正式审查报告 / 产品盘点 / Obsidian。
- **是否 stage / commit / push**：**否**。未 stage、未 commit、未 push、未创建分支或 worktree。
- **是否使用 MuMu / 真实手机**：**否**。未安装 APK，未操作设备或用户书籍数据。
- **是否 reset / checkout / clean / stash / 全仓格式化**：**否**。`git stash list` 为空；工作区既有 300+ 项修改全部原样保留。
- **SEAM REQUEST**：**无**。两个问题都能在授权文件内闭合，未遇到需要越界的情况。

---

## 8. 最终状态

**等待 Codex/Qoder 复核。**

本报告不宣称 S07 已完成，也不宣称可以发布。剩余 P1（翻页/切章旧选区残留、Markdown/旧 EPUB 选区能力不完整）与 P2 项未在本轮处理，仍在正式报告的问题清单内，需 Codex 决定后续切片顺序。真机验收仍归 WorkBuddy。
