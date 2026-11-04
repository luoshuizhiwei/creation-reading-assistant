# S04-1 实现报告：书架来源刷新前置一次性确认与注释对齐

> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 分支：`codex/recover-worktree-20260920`  
> 基准 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`  
> 收工 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（严格遵守红线：未 commit/push/stage）  
> 报告文件：`docs/coordination/legacy-stages/undated/agent-stage-f-source-refresh-confirmation.md`  

---

## 1. 任务背景与核心修复目标

原下拉刷新链路（`TriggerRefresh` -> `viewModel.refresh` -> `syncUpdatedSources`）在检测到源文件有变动时，会未经用户确认直接串行调用 `importer.refreshFromSource` 覆盖内部正文副本，存在误刷并重置阅读进度的风险。同时 `ShelfUiState.kt` 中 `contentUpdatedBookIds` 的注释声明与直接替换正文的旧实现存在契约矛盾（C1 矛盾）。

本阶段完成了以下目标：
1. **前置一次性确认机制**：
   - 下拉刷新检测到有源文件更新的书籍（`updated.isNotEmpty()`）时，**绝不直接替换正文**。
   - 向 UI 发出一次性确认请求（通过 ViewModel 状态流 `pendingSourceRefreshIds` 与 `ShelfUiState.confirmSourceRefreshIds`，并在 `ShelfRoute` 中弹出确认对话框，展示受影响书籍数量与书名提示：“检测到 N 本书（《...》）的源文件已被修改，是否重新同步正文？同步后将重置章节与阅读进度”）。
   - 用户**未确认**或**点击取消**时，**零本正文被替换**，书卡上的「可更新」角标保持不变。
   - 用户**确认刷新**后，才触发实际的逐本刷新流程，串行调用 `importer.refreshFromSource` 并在完成后给出汇总反馈。
2. **无变动场景**：
   - 检测结果为空或无可用来源时，保持原有即时反馈（"书架已是最新..." 或 "没有可同步的来源记录..."），不弹出多余确认框。
3. **注释与文档契约纠正（消除 C1 矛盾）**：
   - 修正 `ShelfUiState.kt` 中 `contentUpdatedBookIds` 的 KDoc 注释，清晰准确地描述其行为契约：“来源文件在系统目录被改过、待用户确认后才刷新的 bookId 集合；确认前仅在书卡打「可更新」标签，不改变正文；确认后将替换内部正文副本并推进来源基线”。

---

## 2. 修改文件清单与 Diff 摘要

本次修改严格受限于授权文件清单，无任何越界修改：

| 文件路径 | 状态 | 职责与变更说明 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfUiState.kt` | `M` | 1. 纠正 `contentUpdatedBookIds` KDoc 注释，消除 C1 契约矛盾；<br>2. 新增 `val confirmSourceRefreshIds: List<String>? = null` 状态字段。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfAction.kt` | `M` | 新增 `CancelSourceRefreshConfirm` 与 `ConfirmSourceRefresh(val bookIds: List<String>)` 动作定义。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModel.kt` | `M` | 1. 增加 `pendingSourceRefreshIds: StateFlow<List<String>?>` 待确认状态流；<br>2. 重构 `refresh()`，在检测到更新时仅标记角标并置入待确认状态，不再直接调 `importer.refreshFromSource`；<br>3. 新增 `confirmSourceRefresh` 执行逐本同步并汇总反馈；<br>4. 新增 `cancelSourceRefresh` 关闭确认状态并保持角标。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfRoute.kt` | `M` | 1. 收集 `viewModel.pendingSourceRefreshIds` 并绑定到 `ShelfUiState`；<br>2. 新增 `GlassAlertDialog` 提示弹窗（显示书本数与书名，确认/取消按钮）；<br>3. 在 `handleShelfAction` 中分发确认与取消动作。 |
| `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModelTest.kt` | `M` | 1. `createVm` 支持自定义注入 `sourceIndex`、`sourceUpdateChecker`、`shelfImporter`；<br>2. 新增 4 个针对 S04-1 的完整行为覆盖测试。 |
| `docs/coordination/legacy-stages/undated/agent-stage-f-source-refresh-confirmation.md` | `新增` | 本次交接报告。 |

### 禁改文件确认
- `ShelfImporter.kt`：**未触碰**（保持上一阶段状态，本阶段零改动）
- `SearchIndexBookRefreshCoordinator.kt`：**未触碰**
- `SearchIndexRepository.kt`：**未触碰**
- `BookRepository.kt`：**未触碰**
- 数据库 schema / DAO / Entity / Migration：**未触碰**
- 任何阅读器核心引擎文件：**未触碰**
- `2026-09-22-current-task-ledger.md`：**未触碰**

---

## 3. 核心机制设计

### 3.1 状态流与生命周期
```
[下拉刷新 TriggerRefresh]
        │
        ▼
ViewModel.refresh() 
        │
        ├─ 无更新 / 无来源 ──> 直接 Snackbar 回执 ("书架已是最新..." / "没有可同步来源...")
        │
        └─ 有源文件更新 (updated.isNotEmpty())
                │
                ├─ 1. 更新角标: _contentUpdatedBookIds.value = updated
                ├─ 2. 发起确认: _pendingSourceRefreshIds.value = updated.toList()
                └─ 3. 正文替换调用: 0 次 (未确认前严禁触碰正文)
```

### 3.2 用户交互分支
- **分支 A（用户取消）**：
  - 调用 `viewModel.cancelSourceRefresh()`；
  - `_pendingSourceRefreshIds.value = null`；
  - **角标保持**：`_contentUpdatedBookIds` 不变，卡片维持「可更新」标；
  - **正文替换**：0 次。
- **分支 B（用户确认）**：
  - 调用 `viewModel.confirmSourceRefresh(refreshIds) { showMessage(it) }`；
  - 清空确认状态并启动协程；
  - 对更新书籍依次调用 `importer.refreshFromSource(bookId)`；
  - 刷新完成后重跑 `refreshSourceUpdates()` 推进角标；
  - 给出汇总反馈：“已同步 N 本书的源文件更新...”。

---

## 4. 验证命令与测试执行输出

### 4.1 编译检查
**命令**：
```powershell
.\gradlew.bat :app:compileDebugKotlin --no-parallel
```
- **退出码**：`0`
- **结果**：BUILD SUCCESSFUL（0 错误，0 新增警告）

### 4.2 单元测试（强制 fresh 执行）
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" --no-parallel --rerun-tasks
```
- **退出码**：`0`
- **执行时间**：1m 45s
- **测试结果**：`20 tests completed, 0 failed, 0 skipped`（既有 16 项 + S04-1 新增 4 项全绿）。
- **新增用例清单**：
  1. `refresh prompts confirmation when source updates detected without refreshing text`：检测到更新时触发确认状态，`importer.refreshFromSource` 调用 0 次。
  2. `cancelling source refresh clears confirmation without modifying text or updated badge`：取消确认时清空确认状态，`importer.refreshFromSource` 调用 0 次，角标集合保持。
  3. `confirming source refresh executes refresh on updated books and returns summary message`：用户确认后依次调用 `refreshFromSource` 并返回汇总文案。
  4. `refresh without source updates completes directly with feedback and without confirmation`：无更新时直接给出即时反馈，不触发确认状态，未调用 `refreshFromSource`。

### 4.3 整体回归与交叉校验
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" --no-parallel --rerun-tasks
```
- **退出码**：`0`
- **合计测试**：**55 项全部 PASS**（6 + 29 + 20）。

---

## 5. 未覆盖与未验证事项

1. **真机 UI 与对话框视觉渲染**：受红线限制未连接 ADB / 真机，对话框在真机上的弹出与交互由集成阶段串行统一验收。
2. **超大书架批量刷新并发性能**：当前设计严格遵照串行模式，单本处理避免压垮 SAF Provider 与内存。

---

## 6. 安全与规范声明

- [x] 严格遵守单一工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- [x] 未执行 `git commit`、`git push`、`git stage`、`git reset`、`git checkout`、`git clean` 或 `git stash`
- [x] 未全仓格式化，未全仓 renormalize
- [x] 未触碰或修改原始仓库
- [x] 未运行任何 ADB 命令，未操作真实手机，未使用 MuMu 模拟器
- [x] 未触碰禁止文件，无越界修改（SEAM REQUEST：无）
- [x] 完整保留工作区中其他并发 Agent 的既有未提交代码
