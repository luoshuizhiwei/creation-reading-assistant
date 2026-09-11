# WorkBuddy 切片报告：R2 J1-I.2 —— 临时查阅真实入口

- 日期：2026-09-10
- 报告人：WorkBuddy（接收「剩余原生 Android 开发」交接）
- 工作目录：`D:/develop/Code/Codex/creation-reading-assistant`
- 切片定位：J1 的 **I.2 子切片 = 为临时查阅提供真实生产入口**。J1-I.1 已证明接线通畅但无触发器；本切片补上触发器。

---

## 1. 基线

| 项 | 值 |
|---|---|
| `pwd` | `/d/develop/Code/Codex/creation-reading-assistant` |
| 分支 | `main` |
| HEAD | `e31db92d5493b6b705ae447aba82c52dc9d7e65f`（切片期间未变） |
| `git status --short` | 327 项（切片开始时） |
| Git 写操作 | 无。未 `add` / `commit` / `push` / `reset` / `checkout` / `clean` / `stash` |

---

## 2. 切片目标

J1-I.1 审计结论：`ReaderProgressEffects → ReaderSessionEffects → ReaderRoute → 协调器` 全链已接通，但**全仓没有任何 `navigate` 携带 `sourceLocator=` 或 `navigationMode=`**，导致 `navigationMode` 恒为 `null`、`beginTemporaryInspection` 在真实 App 中永不执行 —— 临时查阅旅程整体不可达。

本切片目标：在统一笔记/批注界面提供「**临时查看来源**」入口，使该旅程可被真实触发，并满足以下约束：

1. 只有带**有效 source locator** 的条目才显示入口；
2. 入口**只做导航** `navigate(readerTemporaryRoute(target))`；
3. `beginTemporaryInspection` **只由 ReaderRoute 触发**，入口不得自行推进协调器状态；
4. **不混用** `highlightId` / `noteId` / `sourceLocator`。

---

## 3. 变更文件清单

| 文件 | 性质 | 变更 |
|---|---|---|
| `ui/navigation/ReaderTemporaryRoute.kt` | 改 | 新增 `readerTemporaryRouteForSource(...)`（由已解码 source 坐标构造临时 route）与 `hasTemporaryInspectionSource(...)`（入口可见性判据，由前者派生） |
| `feature/annotations/AnnotationActions.kt` | 改 | 接口新增 `inspectSourceTemporarily(entry)`，并以 KDOC 固化三条契约 |
| `ui/screen/profile/ProfileRoute.kt` | 改 | 实现 `inspectSourceTemporarily`：`readerTemporaryRouteForSource(...)` → `navController.navigate(route)`；新增 1 行 import |
| `ui/screen/profile/ReadingNotesSubPage.kt` | 改 | 条目卡片新增 `onInspectSource` 参数与「临时查看来源」微胶囊；调用点按 `hasTemporaryInspectionSource` 门控；新增 1 行 import |
| `res/values/strings_annotations.xml` | 改 | 新增 `annotations_action_temporary_inspect` = 「临时查看来源」 |
| `src/test/.../ui/navigation/AnnotationTemporaryInspectionEntryTest.kt` | 新增 | 10 项 JVM 回归（见 §6） |
| `docs/.../reports/workbuddy-r2-j1-i2-temporary-inspection-entry.md` | 新增 | 本报告 |

未触碰：`src/`、`electron/`、桌面脚本、`archives/`、用户诊断工件、用户书库、其他 Android 无关 WIP。

### 3.1 所有权说明（重要）

R1 文件所有权表把 `ReadingNotesSubPage.kt` / `ProfileRoute.kt` / `feature/annotations/**` / `strings_annotations.xml` 划给 Trae。本切片的事实前提：

- README 明确「R2 以后表格是负责人方向，**不能当作文件写入授权**；每轮先确认实际代码和共享 seam，再给独占路径」；
- Trae / Qoder 的独立工作树（`cra-android-trae-r1` / `cra-android-qoder-r1`）**在本机已不存在**，二者交付早已并入主仓库；
- 上述文件最近修改时间为 2026-09-10 02:36，距本切片约 21 小时，无并发写者；
- 本次交接把 `android/**` 划入我方范围，并要求「lock exclusive files → write code」。

据此按交接授权写入上述文件。**未覆盖任何他人未提交改动**（改动前逐文件核对了现有实现并保持其语义）。痕迹可在 `git diff` 中逐行复核；本切片不提交，便于回退。

---

## 4. 行为

### 4.1 用户可见流程

1. 用户正常阅读某书（普通阅读位置 P 由 `ReaderProgressEffects` 上报到协调器）。
2. 进入「我的 → 阅读笔记」，在带有效 source locator 的条目上看到新增的「**临时查看来源**」胶囊（位于「定位」右侧）。
3. 点击后进入该条目来源位置，且**可以是另一本书**（跨书查阅）。
4. 阅读器进入临时查阅模式：顶栏出现「返回阅读处」按钮。
5. 点「返回阅读处」/ 顶栏 Back / 系统 Back —— 三者收敛为同一 LIFO 动作，回到 P（普通阅读位置），临时栈清空后按钮消失。

### 4.2 三个约束的落地方式

| 约束 | 落地 |
|---|---|
| 只有有效 source locator 才显示 | 入口门控 = `hasTemporaryInspectionSource(entry.bookId, entry.legacyOffset)`，该判据**由 route 构造函数派生**，不重复实现规则 |
| 入口只做导航 | `ProfileRoute.inspectSourceTemporarily` 仅 `navController.navigate(route)`；不触碰协调器 |
| `beginTemporaryInspection` 只由 ReaderRoute 触发 | 生产调用点全仓仍**恰好 1 处**：`ReaderRoute.kt:53` |
| 不混用 highlightId/noteId/sourceLocator | 临时 route 只带 `sourceLocator=` + `navigationMode=temporary`；测试断言不含 `highlightId=` / `noteId=` |

### 4.3 语义边界（关键设计取舍）

`readerTemporaryRoute` 经 `encodeSourceLocator` 需要**全局** `legacyOffset`（用于跨渲染模式往返）。因此：

- 带全局偏移的条目（TXT 与 EPUB 的 v2 locator 常态）→ 入口可见且可跳转；
- **只有章节元组、无全局偏移的历史条目** → 入口**不显示**（判据与可跳转性一致），用户仍可用既有「定位」胶囊（无 locator 时降级为「打开书籍」）；
- 无效坐标一律返回 `null`，**绝不伪造 offset=0 的假位置**。

这一取舍避免了「按钮显示但点了没反应」，是本次刻意让「可见性」与「可跳转性」同源的原因。

---

## 5. 生产调用点接线审计

判据（沿用 J1-I.1 教训）：**纯函数单测全绿 ≠ 接线已通**，必须核实生产侧调用点存在。

| 环节 | 位置（file:line，切片后实测） |
|---|---|
| 入口渲染 | `ui/screen/profile/ReadingNotesSubPage.kt:1056-1062`（`if (onInspectSource != null)`） |
| 入口门控 | `ReadingNotesSubPage.kt:494`（`hasTemporaryInspectionSource(entry.bookId, entry.legacyOffset)`） |
| 入口动作派发 | `ReadingNotesSubPage.kt:494` → `actions?.inspectSourceTemporarily(entry)` |
| 动作桥提供 | `ProfileRoute.kt:292`（`LocalAnnotationActions provides annotationActions`） |
| 动作实现 | `ProfileRoute.kt:201-215` |
| route 构造 | `ProfileRoute.kt:203` → `readerTemporaryRouteForSource(...)`（定义于 `ui/navigation/ReaderTemporaryRoute.kt:83`） |
| 可见性判据 | `ui/navigation/ReaderTemporaryRoute.kt:108`（由 `:83` 派生） |
| 字符串资源 | `res/values/strings_annotations.xml:33`，引用处 `ReadingNotesSubPage.kt:1059` |
| 返回栈推进（唯一入口） | `ui/screen/reader/ReaderRoute.kt:53` —— 全仓生产调用点计数 = **1** |

结论：`笔记条目 → 门控 → 动作派发 → 动作桥 → route 构造 → navigate → ReaderRoute 解析 temporary → 协调器压栈 → 统一 LIFO 返回` 全链**每一个环节都有生产调用点**，无死接线。

---

## 6. 命令、退出码与新鲜测试计数

### 6.1 命令（在 `android/` 下执行）

```powershell
$env:GRADLE_USER_HOME="D:\develop\env\gradle"
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks --console=plain `
  --tests "...SourceNavigationContractTest" --tests "...TemporaryReadingNavigationViewModelTest" `
  --tests "...ReaderTemporaryRouteTest" --tests "...ReaderTemporaryRouteJourneyTest" `
  --tests "...AnnotationTemporaryInspectionEntryTest" `
  --tests "...ReaderSourcePositionTest" --tests "...ReaderTemporaryBackTest" `
  --tests "...ReaderTemporaryInspectionUiTest" --tests "...ReaderProgressNavigationTest" `
  --tests "...SearchHitNavigationTest" --tests "...SearchScrollFocusRequestTest"
```

- 退出码 **0**；`BUILD SUCCESSFUL in 1m 18s`；`30 actionable tasks: 30 executed`（非 UP-TO-DATE）。
- 该命令在**最终 on-disk 代码**上执行（见 §7.3 的还原事件说明）。

### 6.2 新鲜测试计数（来源：`app/build/test-results/testDebugUnitTest/TEST-*.xml`）

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `AnnotationTemporaryInspectionEntryTest`（**本切片新增**） | 10 | 0 | 0 | 0 |
| `SourceNavigationContractTest` | 11 | 0 | 0 | 0 |
| `ReaderTemporaryRouteJourneyTest` | 11 | 0 | 0 | 0 |
| `ReaderTemporaryRouteTest` | 9 | 0 | 0 | 0 |
| `TemporaryReadingNavigationViewModelTest` | 6 | 0 | 0 | 0 |
| `ReaderProgressNavigationTest` | 7 | 0 | 0 | 0 |
| `ReaderSourcePositionTest` | 14 | 0 | 0 | 0 |
| `ReaderTemporaryBackTest` | 6 | 0 | 0 | 0 |
| `ReaderTemporaryInspectionUiTest` | 5 | 0 | 0 | 0 |
| `SearchHitNavigationTest` | 16 | 0 | 0 | 0 |
| `SearchScrollFocusRequestTest` | 8 | 0 | 0 | 0 |
| **合计** | **103** | **0** | **0** | **0** |

比对 J1-I.1：同一命令集在未新增测试时为 93 项；本切片为 93 + 10 = **103** 项。

### 6.3 新增测试覆盖的真实行为

`AnnotationTemporaryInspectionEntryTest`（10 项，全部为行为断言，无 `assertTrue(true)` 类伪测试、无复制生产分支、无源码文本匹配）：

1. 有效 source → route 携带 `sourceLocator=` + `navigationMode=temporary`，且**不含** `highlightId=` / `noteId=`；
2. route 往返解码回原坐标（offset / ci / co）；
3. 非 ASCII `bookId` 往返保真；
4. 按 ReaderRoute 口径解析 → `TEMPORARY` 模式 + 目的地坐标与条目一致；
5. **端到端状态机**：记录普通阅读 P → 条目入口 route（**跨书**）→ `beginTemporaryInspection` → `hasReturnableTarget == true` → `resolveTemporaryReturnRoute` 回到 P 且栈空；
6. 可见性判据与 route 可构造性逐输入一致（含 null bookId / 负 offset / 缺全局偏移）；
7. 无全局偏移、半截章节坐标、空/负参数 → 一律 `null`，不伪造位置。

---

## 7. 未覆盖项与风险

1. **无设备 / 无视觉证据**：未安装、未跑 `connectedAndroidTest`、未用真机或截图。以上全部为 JVM + 编译级证据，不构成 UI 验收。
2. **Compose 层未验证**：胶囊的实际渲染、宽度在窄屏下是否挤压「编辑批注 / 删除」、点击命中，均需真机确认。左侧现有 4 个胶囊（定位 / 临时查看来源 / 编辑批注 / 删除），窄屏可能出现换行或截断 —— **建议列为真机验收首查项**。
3. **书内笔记面板未接入**：`ReaderNotesSheet.kt`（阅读器内「笔记与标注」弹层）本次未加该入口。其跳转需从 ReaderRoute 透传导航回调到宿内，属独立改动，未纳入本切片。
4. **全量门禁未重跑**：`lintDebug` / `assembleDebug` / `compileDebugAndroidTestKotlin` / 全量 JVM 未在本切片串行执行（按交接只做定向门禁）。
5. **`entry.hasLocator` 与「可跳转」的差异**：见 §4.3。这是刻意的语义收紧，不是缺陷；但若后续要让「仅章节元组」的条目也能临时查阅，需要扩展 `encodeSourceLocator` 以支持仅 ci/co 的 route，属共享契约变更，须单独立项并回归 93+ 项既有 route 测试。

### 7.3 过程风险记录（文件被外部还原）

本切片期间 `ReadingNotesSubPage.kt` 出现一次 `EBUSY: resource busy or locked`（编辑失败），随后一处已成功写入的门控表达式被**外部进程还原**为旧内容（同一文件的另外两处改动未受影响）。已重新写入并逐项复核 on-disk 内容，最终验证命令跑在修正后的代码上。建议其他 agent 在本仓库编辑该文件时注意：**每次编辑后重新读取确认**，不要假设一次写入即持久。

---

## 8. SEAM REQUEST

**无新增跨所有权请求。**

- 现有公共 API 已足够：`SourceNavigationContract.target(...)` 与 `ReaderLocator` 构造器均 public，故本切片的 route 构造器纯属便利封装，未引入新的共享契约。
- 若后续要把入口扩展到**书内笔记面板**（§7.3）或让「仅章节元组」条目可临时查阅（§7.5），那两项需要跨模块改动，届时另提 SEAM REQUEST。

---

## 9. 状态

**Dev-verified（代码 + JVM + 编译）** / **待真机验收**

- 已完成：入口在统一笔记界面真实可达；门控与可跳转性同源；入口只导航、不推进协调器；`beginTemporaryInspection` 生产调用点仍唯一；定向门禁 103 项 0 失败；`compileDebugKotlin` 当期执行成功。
- 未完成（不计入完成度）：真机/视觉验收、书内笔记面板入口、全量门禁、窄屏胶囊排布确认。
- 结论：J1 的「临时查阅」旅程**首次在生产代码中可达**；是否可用以真机验收为准。
