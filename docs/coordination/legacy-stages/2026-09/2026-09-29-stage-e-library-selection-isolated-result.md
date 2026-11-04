# 阶段 E 库浏览器搜索/选择边界隔离验证报告

> **执行时间**：2026-09-29T21:33+08:00  
> **基线仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> **分支**：`codex/recover-worktree-20260920`  
> **HEAD 基线**：`513891d6a66a9b78e072ba759d34ed580d377ecf`（包含首批 G0+U `209ec8a` 与 G2+runner `513891d`）  
> **隔离工作目录**：`E:\creation-reading-assistant\stage-e-library-selection-isolated-20260929\`  
> **并行边界**：与 B2 cache-clear 窗口并行，独占 `LibraryBrowserViewModel.kt` 的搜索/选择边界修复部分与 `LibraryBrowserSelectionTest.kt`；绝对未修改或纳入 B2 窗口的任何文件。  
> **操作性质**：独立目录隔离构建与定向 JVM 验证；共享恢复仓库未暂存、未提交、未修改任何已有工作树文件；未操作 ADB、MuMu、真实手机或原始仓库。

---

## 一、候选定位与 Hunk 安全拆分核实

### 1.1 实际 Hunk 与调用关系核实

`LibraryBrowserViewModel.kt` 在工作树中共有 16 个 `-U0` hunk，经源码与调用方核实，清晰分为两组：

1. **组 (a) 排序切换滞后修复**（H103 排序族，涉及 `sortModeOverride`、`loadDirectory` 显式传参 `sortMode` 等 9 处改动）：
   - 该组属于排序逻辑，与 `ADDED_DESC`（“最近添加”排序项，Q1 待决议题）紧密相关。
2. **组 (b) 搜索与选择边界修复**（本次验证的**唯一纳入目标**，共 8 处改动）：
   - `uiState` 谓词 `matches: (LibraryEntry) -> Boolean`：搜索时**同时过滤文件夹（`directories`）与文件（`files`）**，修复此前搜词时文件夹全部残留、用户误以为搜索失效的问题。
   - `setQuery(value: String)`：当实际搜索词改变时（`snapshot.query.trim() != value.trim()`），**强制清空已选集合 `selection = emptySet()`**，防止旧搜索结果下的选择残留带入新的导入操作。
   - `selectAllVisible()`：将 `selection` 直接赋值为 `visibleFileDocumentIds()`，**严格限制为当前过滤结果集内的可见文件**，不再使用全量目录文件 `files`。
   - `invertSelection()`：反选定义收敛为 `visibleFileDocumentIds() - snapshot.selection`，**仅在当前可见搜索结果内翻转**，避免搜索后一次反选误选整个目录的隐藏文件。
   - `selectedUris()`：双重过滤 `it.documentId in visible && it.documentId in snapshot.selection`，作为导入边界的最后一道防线，**即使内部存在残留选择，也绝不交出当前过滤结果之外的 URI**。
   - 私有辅助方法 `visibleFileDocumentIds()`：基于 `query.trim()` 精确镜像 `uiState` 的可见性谓词。

### 1.2 严格排除与零污染证明

- **与 `ADDED_DESC`、“最近添加”排序项安全分离**：
  - 组 (b) 搜索/选择边界修复完全基于文件名过滤与集合运算，**零引用 `ADDED_DESC`、零引用 `sortModeOverride`、零修改 `loadDirectory` 签名**。
  - 严格排除 `LibraryBrowserSortTest.kt`（该测试在 `:97/:102/:107` 显式依赖未决的 `LibrarySortMode.ADDED_DESC`，仍需产品决策）。
  - 两组 hunk 在代码中物理不相交，可 100% 安全解耦。
- **与 B2 窗口零重叠**：
  - 严格排除 B2 的 `ShelfBookActions.kt`、`ShelfBookActionsTest.kt`、`BookRepository.kt` B2 hunks、`BookResidueSafeCleanupTest.kt` 等所有相关文件。
  - 隔离候选内涉及文件仅限于 `LibraryBrowserViewModel.kt`（搜索/选择部分）与 `LibraryBrowserSelectionTest.kt`。

---

## 二、隔离候选文件清单与 SHA-256

隔离工作目录：`E:\creation-reading-assistant\stage-e-library-selection-isolated-20260929\`

| 状态 | 相对路径 | 字节大小 | SHA-256 校验和 | 内容摘要 |
|---|---|---|---|---|
| **M** | `android/app/src/main/java/.../ui/viewmodel/LibraryBrowserViewModel.kt` | 23,150 | `5B1349BF258134F0A13FDA220ECAE59A2FDC51C637DAD7DE7CA348FF4A6A3D1D` | 仅纳入组 (b) 搜索与选择边界修复（8 处 hunk） |
| **??** | `android/app/src/test/java/.../ui/viewmodel/LibraryBrowserSelectionTest.kt` | 6,589 | `3626B156F460CE4E04050BD3E4FEB17CDC917E645B7934D5C25A45676AD8A5AE` | 搜索/选择边界 4 项核心测试（未跟踪新文件） |
| **补丁** | `stage-e-library-selection-isolated.patch` | 4,432 | `C225DAF845E0C17288C386FFC5180FFB73ABA1ECB7635EB659B2A1924414696F` | 隔离候选相对 HEAD 的精确 diff 补丁 |

---

## 三、隔离构建与测试验证结果

所有命令均在隔离目录 `E:\creation-reading-assistant\stage-e-library-selection-isolated-20260929\android\` 下执行。

### 3.1 编译阶段验证

| 验证任务 | 执行命令 | 耗时 | 退出码 | 结果判定 | 任务日志路径 |
|---|---|---|---|---|---|
| **主源码编译** | `.\gradlew.bat :app:compileDebugKotlin --no-daemon` | 2m 44s | 0 | **SUCCESS** | `task-551.log` |
| **单元测试编译** | `.\gradlew.bat :app:compileDebugUnitTestKotlin --no-daemon` | 2m 3s | 0 | **SUCCESS** | `task-555.log` |

### 3.2 定向 JVM 测试执行

#### 第 1 轮：LibraryBrowserSelectionTest 定向用例
- **命令**：`.\gradlew.bat :app:testDebugUnitTest --tests "*LibraryBrowserSelectionTest*" --no-daemon`
- **耗时**：58s
- **退出码**：0（`BUILD SUCCESSFUL`）
- **日志**：`task-559.log`
- **XML 报告**：`TEST-com.creationreadingassistant.ui.viewmodel.LibraryBrowserSelectionTest.xml`
- **测试结果明细（4 / 4 全部 PASS）**：

| 测试用例方法名 | 判定状态 | 耗时 | 验证的具体行为与安全边界 |
|---|---|---|---|
| `select all after search imports only matching files` | **PASS** | 1.18s | 搜索后点击「全选」，`selectedUris()` 仅包含匹配项，绝不把当前被搜索过滤隐藏的文件交给导入器 |
| `inverting a search selection never selects hidden files` | **PASS** | 1.15s | 搜索后「反选」只在当前可见匹配结果集内翻转，隐藏文件绝对不会被勾选 |
| `changing query clears selections from the previous result set` | **PASS** | 1.34s | **关键防御**：修改搜索词后，上一轮结果集的勾选项立刻失效（`selection` 清空），避免误导入不可见文件 |
| `import boundary excludes a stale hidden selection` | **PASS** | 1.44s | **导入边界防线**：即使内部状态偶发残留非可见文件的 documentId，最终 `selectedUris()` 也会强力过滤剔除 |

#### 第 2 轮：库浏览器相关策略扩展回归
- **命令**：`.\gradlew.bat :app:testDebugUnitTest --tests "*LibraryBrowserPolicyTest*" --tests "*LibraryRootStoreTest*" --tests "*LibraryBrowserSelectionTest*" --no-daemon`
- **耗时**：36s
- **退出码**：0（`BUILD SUCCESSFUL`）
- **日志**：`task-569.log`
- **扩展验证目标与风险说明**：
  - `LibraryBrowserPolicyTest`（22 tests / 22 PASS）：验证文件后缀识别、来源匹配、指纹去重等核心业务策略未受搜索过滤改动影响。
  - `LibraryRootStoreTest`（3 tests / 3 PASS）：验证根目录 DataStore 读取与基础排序模式存储未受干扰。
  - **总计**：**29 / 29 PASS，0 FAIL，0 ERROR**。

---

## 四、结论与交付物路径

1. **拆分可行性结论**：
   - `LibraryBrowserViewModel.kt` 的搜索/选择边界修复部分与“最近添加”排序（`ADDED_DESC`）在语义和代码层面**完全正交、可独立安全拆分**。
   - 4 项核心测试证明：搜索条件改变后，选择状态绝不会错误指向当前过滤结果之外的项目，导入边界防线严密。
2. **交付物绝对路径**：
   - 隔离候选目录：`E:\creation-reading-assistant\stage-e-library-selection-isolated-20260929\`
   - 候选补丁文件：`E:\creation-reading-assistant\stage-e-library-selection-isolated-20260929\stage-e-library-selection-isolated.patch`
   - 测试 XML 目录：`E:\creation-reading-assistant\stage-e-library-selection-isolated-20260929\android\app\build\test-results\testDebugUnitTest\`
   - 本报告路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\legacy-stages\2026-09\2026-09-29-stage-e-library-selection-isolated-result.md`
