# Stage E — S07+S12 阅读器既有修复闭包候选

> **生成时间**：2026-09-30
> **恢复仓库 HEAD**：`513891d6a66a9b78e072ba759d34ed580d377ecf`
> **只读底座（joint tree）**：`E:\creation-reading-assistant\stage-e-final-joint-20260930\joint\android`
> **独占候选**：`E:\creation-reading-assistant\stage-e-reader-preserved-20260930\android`
> **补丁文件**：`E:\creation-reading-assistant\stage-e-reader-preserved-20260930\reader-preserved-closure.patch`（73,464 bytes，1,335 行）

---

## 1. 目标与范围

从恢复仓库工作树（unstaged）中提取 S07（选区/高亮/笔记跳转）与 S12（Legado 导入 marker 保护）的最小符号闭包，以 hunk 为单位应用到联合树底座上，形成独立候选。

### 1.1 保留的修复

| 编号 | 修复内容 | 来源 |
|------|----------|------|
| S07-F2 | VM 选区清除（LoadChapter / CenterTap / PageTurn / ClearSelection） | 工作树 `ReaderViewModel.kt` |
| S07-F2.2 | TXT 滚动单元选区归属判据 + DisposableEffect 清空 | 工作树 `ReaderContentHostPlainTextBranch.kt` |
| S07-F2.3 | 滚动单元 onValueChange 归属检查替换 | 工作树 `ReaderContentHostPlainTextBranch.kt` |
| S07-F3 | EPUB 整块选区源范围 `epubTextBlockSourceRange` | 工作树 `ReaderContentHostEpubBranch.kt` |
| S07-F3.1 | EPUB 持久高亮投影 `epubBlockHighlightLocalRange` | 工作树 `ReaderContentHostEpubBranch.kt` |
| S07-F5 | TXT 滚动选区可见性失效策略 | 工作树 `ReaderContentHostPlainTextBranch.kt` |
| S07-NoteJump | 笔记按注释类型跳转（`onJumpToNote` + typed target） | 工作树 `ReaderNotesSheet.kt` + `ReaderSheetHost.kt` |
| S12-① | Legado 导入 marker 编解码 `LegadoProgressMarker` | 工作树新文件 |
| S12-② | Legado 进度导入器 `LegadoReadingProgressImporter`（99.4% 边界、未完成/搁置状态保护） | 工作树新文件 |
| S12-③ | `LegadoMarkerSaveGuard` 单书会话保存守卫 | 工作树 `ReaderProgressEffects.kt` |
| S12-④ | `ReaderScreen.persistOnLeave` 退出守卫 | 工作树 `ReaderScreen.kt` |
| S12-⑤ | `ReaderDocumentLoader` unconvertedLegadoPosition seam | 工作树 `ReaderDocumentLoader.kt` |
| S12-⑥ | 首次近似定位提示 `temporaryInspection` | 工作树 `ReaderProgressEffects.kt` |

### 1.2 不包含的改动

- 不夹带 reader 大重写、新分页动画或无关设置功能
- 不包含 S07-F2.4（笔记快照 `selectionSnapshot`）— **BLOCKED**，见 §5
- 不包含 S07-F4.1（替换可用性同步）— 不在目标内
- 不包含 TOC `notes`/`onPickNote` 笔记跳转 — **BLOCKED**，见 §5
- 不包含 `tapActionPreset`（联合树无此符号）
- 不包含 `LocatorBuilder.kt` / `LocatorCodec.kt` 的 nullable 改动 — 非所有权文件，已绕行（见 §3.1）

---

## 2. 文件/Hunk 清单与 SHA

### 2.1 源文件 SHA 对照

SHA256 前 16 位。Recovery = 恢复仓库工作树（unstaged）；Joint = 联合树底座；Candidate = 本候选。

| # | 状态 | 文件（相对 `android/`） | Recovery SHA | Joint SHA | Candidate SHA |
|---|------|------------------------|-------------|-----------|---------------|
| 1 | NEW | `app/src/main/java/.../feature/reader/locator/LegadoProgressMarker.kt` | `CB640723A26F1A1C` | N/A | `CB640723A26F1A1C` |
| 2 | NEW | `app/src/main/java/.../feature/sync/LegadoReadingProgressImporter.kt` | `CAE99B5FF291D8A9` | N/A | `8DC2EA7EEE66F3EE` |
| 3 | MOD | `app/src/main/java/.../ui/screen/reader/ReaderContentHostEpubBranch.kt` | `BFCC6ACFC6D7EE39` | `AC72A6139211C042` | `17DACD908C603FAA` |
| 4 | MOD | `app/src/main/java/.../ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | `784199B715BCB806` | `5C44AABCE3D13CB3` | `C08A8878DA0793DC` |
| 5 | MOD | `app/src/main/java/.../ui/screen/reader/ReaderProgressEffects.kt` | `A7BAA349494E328B` | `1B36442EFEE1F0B7` | `7E4B4AB884F1D61C` |
| 6 | MOD | `app/src/main/java/.../ui/screen/reader/ReaderScreen.kt` | `5E56FD1408D14218` | `B234131746381EFF` | `28F484A344D2EC97` |
| 7 | MOD | `app/src/main/java/.../ui/screen/reader/ReaderSheetHost.kt` | `382B485458F00528` | `7E250629BC642543` | `0766D3FB3EA6B45B` |
| 8 | MOD | `app/src/main/java/.../ui/screen/reader/sheets/ReaderNotesSheet.kt` | `76046E3F70A4144C` | `593872B958EEA04F` | `D682F35C29E09FBE` |
| 9 | MOD | `app/src/main/java/.../ui/viewmodel/ReaderDocumentLoader.kt` | `18AA220C6F564A42` | `EC501EEFD307FCD5` | `18AA220C6F564A42` |
| 10 | MOD | `app/src/main/java/.../ui/viewmodel/ReaderViewModel.kt` | `19E6FB71FC5FB7EC` | `A5AA61FF872AC651` | `B0F0B46851C03EA3` |

### 2.2 Hunk 明细

#### 文件 1：`LegadoProgressMarker.kt`（NEW — S12-①）
- 直接从恢复工作树复制，无修改
- 符号：`LegadoChapterPosition` (data class)、`LegadoProgressMarker` (object: `encode`, `decode`)
- 33 行

#### 文件 2：`LegadoReadingProgressImporter.kt`（NEW — S12-②）
- 从恢复工作树复制，1 处适配修改
- **修改点**：`locationFor` EPUB 分支 — 将 `LocatorBuilder.progressJson(globalOffset = null)` 替换为 `buildString` 直接构造 JSON
- **原因**：联合树 `LocatorBuilder.progressJson` 的 `globalOffset: Int` 非空，`LocatorCodec.encode` 的 `legacyOffset: Int` 也非空；工作树将两者改为可空但那两个文件不在本候选所有权内
- **产物等价性**：`buildString` 产物与 `LocatorBuilder.progressJson(globalOffset=null)` 逐字一致 — legacy `offset` + `chapter` + `locator_v2` 无 `offset` 字段
- 符号：`LegadoReadingProgressImporter` (class: `importArchive`, `parseArchive`)、`Report`、`LegadoProgress`、`ParseResult`、`BookKey`、`MAX_IMPORTED_PERCENT = 99.4f`
- 348 行

#### 文件 3：`ReaderContentHostEpubBranch.kt`（MOD — S07-F3, S07-F3.1）
- 从恢复工作树复制，1 处删除
- **删除**：`tapActionPreset` 参数及传递（联合树无此符号）
- **新增**：`epubTextBlockSourceRange`、`epubBlockSourceRange`、`epubBlockHighlightLocalRange` 函数
- **修改**：`Text(text = ann)` → `Text(text = text)`（高亮投影叠加）；`.clickable` 内 `onSelect(block.text, gOff, -1, -1)` → `epubTextBlockSourceRange` 范围传递

#### 文件 4：`ReaderContentHostPlainTextBranch.kt`（MOD — S07-F5, S07-F2.2, S07-F2.3）
- 从联合树底座出发，按 hunk 应用
- **新增 imports**：`rememberUpdatedState`、`snapshotFlow`、`distinctUntilChanged`
- **S07-F5**：`ScrollSelectionInvalidation` enum、`scrollSelectionInvalidation` 纯函数、`ScrollSelectionInvalidationGate` 去重门、`ScrollViewportSnapshot`、`LaunchedEffect(s.plainListState)` viewport 监听
- **S07-F2.2**：`readingUnitOwnsSelection` 纯函数、`readingUnitShouldClearOnDispose`、`ReaderUnitTextItem` 内 `DisposableEffect(content.sourceStartAbs)` onDispose 清空
- **S07-F2.3**：`onValueChange` 内 `selectionState.selectedRangeStart in content.sourceStartAbs until content.sourceEndAbs` → `readingUnitOwnsSelection(...)` 调用

#### 文件 5：`ReaderProgressEffects.kt`（MOD — S12-③, S12-⑥）
- 从恢复工作树复制，2 处回退
- **回退 1**：分页路径 chapter index 修复（`chapterIndex = ...` 改动回退为联合树版本）— 非目标修复
- **回退 2**：M-BUG-10 SE4 LaunchedEffect key 变更回退为联合树版本 — 非目标修复
- **保留**：`LegadoMarkerSaveGuard` object、`LegadoMarkerSaveGuardSession` class、`legadoGuardObservation` 函数、`unconvertedLegadoPosition` seam、`temporaryInspection` 加入 debounce LaunchedEffect keys、marker arm/disarm/resetAnchor/anchorAt/observe 守卫、首次近似定位提示 `showNotice`

#### 文件 6：`ReaderScreen.kt`（MOD — S12-④）
- 从联合树底座出发，按 hunk 应用
- **修改**：`persistOnLeave` 守卫 — `if (bid.isNotBlank() && ...)` 内增加 `!LegadoMarkerSaveGuard.isArmed(bid)` 条件
- **不包含**：S07-F2.4 `selectionSnapshot`（BLOCKED）

#### 文件 7：`ReaderSheetHost.kt`（MOD — S07-NoteJump seam）
- 从联合树底座出发，按 hunk 应用
- **新增 imports**：`HighlightEntity`、`NoteEntity`、`AnnotationType`、`navigationTargetId`
- **修改**：NotesSheet wiring — `onJumpToBookmark` → `onJumpToNote`，使用 typed target（`highlightNavigationTarget` / `noteNavigationTarget`）
- **新增 private helpers**：`highlightNavigationTarget`（按 `AnnotationType.HIGHLIGHT` 过滤）、`noteNavigationTarget`（按 `AnnotationType.NOTE` 过滤）、`NOTE_KIND_BOOKMARK` 常量
- **设计**：helpers 从 `ReaderTocBookmarkList.kt`（非所有权）inline 为 private 函数，避免修改非所有权文件

#### 文件 8：`ReaderNotesSheet.kt`（MOD — S07-NoteJump）
- 从联合树底座出发，按 hunk 应用
- **修改**：`onJumpToBookmark: (String) -> Unit` → `onJumpToNote: (String) -> Unit`（参数名 + 调用站）

#### 文件 9：`ReaderDocumentLoader.kt`（MOD — S12-⑤）
- 直接从恢复工作树复制，无修改
- SHA 与 recovery 完全一致（`18AA220C6F564A42`）
- **新增**：`unconvertedLegadoPosition` 字段在 `ReaderLoadedContent.Text` 中的传递

#### 文件 10：`ReaderViewModel.kt`（MOD — S07-F2）
- 从联合树底座出发，按 hunk 应用
- **新增**：`withSelectionCleared()` 扩展函数
- **修改**：`LoadChapter` action — 选区清除（`withSelectionCleared`）
- **修改**：`CenterTap` action — 完全清除选区（替代部分清除）
- **修改**：`PageTurn` action — 选区清除
- **修改**：`ClearSelection` action — 重构为使用 `withSelectionCleared` helper

---

## 3. 补丁与可复算性

### 3.1 LocatorBuilder/LocatorCodec 绕行说明

**阻塞符号**：`LocatorBuilder.progressJson` 的 `globalOffset: Int`（联合树非空）vs `globalOffset: Int?`（工作树可空）；`LocatorCodec.encode` 的 `legacyOffset: Int`（联合树非空）vs `legacyOffset: Int?`（工作树可空）。

**调用方**：`LegadoReadingProgressImporter.locationFor` EPUB 分支传 `globalOffset = null`。

**阻塞证据**：联合树 `LocatorCodec.encode` 始终写 `"offset":` 字段；S12 需要省略该字段以避免违反 `offset == chapterStart[ci] + co` 不变式。修改 `LocatorBuilder.kt` / `LocatorCodec.kt` 不在本候选所有权内。

**绕行方案**：在 `LegadoReadingProgressImporter.locationFor` EPUB 分支用 `buildString` 直接构造 JSON，产物与 `LocatorBuilder.progressJson(globalOffset=null)` 逐字一致。已验证编译通过且 224 项 JVM 测试全绿。

### 3.2 补丁生成方法

```powershell
# 1. 创建临时 git 仓库
git init
# 2. 复制联合树 app/src/ 到临时仓库
# 3. git add -A && git commit -m "joint-base"
# 4. 用候选版本覆盖 10 个文件
# 5. git add -A
# 6. git diff --cached --unified=3 > reader-preserved-closure.patch
```

### 3.3 补丁验证（round-trip）

```powershell
# 1. 创建临时目录，复制联合树 app/src/
# 2. git init && git add -A && git commit -m "base"
# 3. git apply --whitespace=nowarn reader-preserved-closure.patch
#    → exit code 0
# 4. 逐文件 SHA256 比对候选
#    → 10/10 MATCH
# Round-trip: PASS
```

---

## 4. 编译与测试

### 4.1 编译命令与退出码

| 命令 | 退出码 |
|------|--------|
| `.\gradlew :app:compileDebugKotlin --max-workers=1 -q` | **0** |
| `.\gradlew :app:compileDebugAndroidTestKotlin --max-workers=1 -q` | **0** |
| `.\gradlew :app:compileDebugUnitTestKotlin --max-workers=1 -q` | **0** |

工作目录：`E:\creation-reading-assistant\stage-e-reader-preserved-20260930\android`

### 4.2 定向 JVM 单元测试

**命令**：
```
.\gradlew :app:testDebugUnitTest --max-workers=1 -q --tests "<class1>" --tests "<class2>" ... --tests "<class30>"
```

**退出码**：**0**

**测试结果摘要**（30 个测试类，224 项测试）：

| 测试类 | tests | fail | err | skip |
|--------|-------|------|-----|------|
| ReaderViewModelTest | 62 | 0 | 0 | 0 |
| ReaderSelectionExternalActionsTest | 14 | 0 | 0 | 0 |
| ReaderSourcePositionTest | 14 | 0 | 0 | 0 |
| ReaderReplacementCapabilityTest | 15 | 0 | 0 | 0 |
| MarkdownScrollHighlightTest | 11 | 0 | 0 | 0 |
| NoteRepositoryTest | 11 | 0 | 0 | 0 |
| AutoRevealProgressTest | 9 | 0 | 0 | 0 |
| ReaderSelectionToolbarModelTest | 8 | 0 | 0 | 0 |
| EpubProgressCalibrationTest | 8 | 0 | 0 | 0 |
| ScrollHighlightRangeTest | 7 | 0 | 0 | 0 |
| ReaderProgressNavigationTest | 7 | 0 | 0 | 0 |
| PageSelectionHandleDragTest | 7 | 0 | 0 | 0 |
| ReaderScrollPositionTest | 5 | 0 | 0 | 0 |
| ReaderTemporaryInspectionUiTest | 5 | 0 | 0 | 0 |
| NoteRepositoryRestoreTest | 6 | 0 | 0 | 0 |
| ReaderDocumentLoaderProfileTest | 6 | 0 | 0 | 0 |
| ReaderTocStateTest | 4 | 0 | 0 | 0 |
| ReaderProgressMergeTest | 3 | 0 | 0 | 0 |
| MarkdownSelectionPositionTest | 3 | 0 | 0 | 0 |
| PagedReaderControllerLifecycleTest | 3 | 0 | 0 | 0 |
| ReaderProgressSheetPolicyTest | 2 | 0 | 0 | 0 |
| ReaderScreenStateTest | 2 | 0 | 0 | 0 |
| ReaderInitialChapterStateTest | 2 | 0 | 0 | 0 |
| PagedReaderControllerProjectionTest | 2 | 0 | 0 | 0 |
| ReaderProgressRestoreTest | 2 | 0 | 0 | 0 |
| LearningProgressEstimatorTest | 2 | 0 | 0 | 0 |
| HighlightSourceLengthTest | 1 | 0 | 0 | 0 |
| ReaderProgressSnapshotTest | 1 | 0 | 0 | 0 |
| ReaderSelectionSearchPolicyTest | 1 | 0 | 0 | 0 |
| SettingsStoreTxtTocMigrationMarkerTest | 1 | 0 | 0 | 0 |
| **合计** | **224** | **0** | **0** | **0** |

XML 输出目录：`app/build/test-results/testDebugUnitTest/`（30 个 XML 文件）

### 4.3 androidTest 编译

androidTest 编译通过（exit 0）。未新增 androidTest 用例 — 现有 androidTest 不覆盖 S07 选区/高亮/笔记跳转或 S12 marker 守卫的 instrumented 路径。

---

## 5. 阻塞项

### 5.1 S07-F2.4 笔记快照（BLOCKED）

**阻塞符号**：
- `selectionSnapshot` 字段 — 定义在 `ReaderScreenState.kt`（非所有权）
- `computeLocatorJsonForSnapshot` 函数 — 定义在 `ReaderProgressActions.kt` / `ReaderActions.kt`（非所有权）

**调用方**：`ReaderScreen.kt` 的 `persistOnLeave` 需要读取 `selectionSnapshot` 构造带选区信息的进度快照。

**阻塞证据**：`ReaderScreenState.kt` 的 `ReaderScreenState` data class 不含 `selectionSnapshot` 字段；添加该字段需要修改非所有权文件，且涉及 `ReaderProgressActions.kt` 中的快照计算逻辑。

**影响**：笔记创建时不会附带选区快照到进度记录。不影响选区/高亮/笔记跳转的核心功能。

### 5.2 TOC 笔记跳转（BLOCKED）

**阻塞符号**：
- `TocNoteEntry` — 定义在 `ReaderTocBookmarkList.kt`（非所有权）
- `ReaderTocSheet.kt` 的 `notes` / `onPickNote` API — 非所有权

**调用方**：`ReaderSheetHost.kt` 需要将笔记列表传递给 TOC sheet。

**阻塞证据**：联合树 `ReaderTocSheet.kt` 不接受 `notes` 参数；添加需要修改非所有权文件的 public API。

**影响**：TOC 面板内不显示笔记列表跳转。`ReaderNotesSheet` 内的笔记跳转（S07-NoteJump）已完整实现。

---

## 6. 设备复验用例

### 6.1 旧版本设备证据（已验证）

以下功能在恢复仓库工作树版本中已在真机上验证过（证据来自 S07/S12 验收报告）：

| 用例 | 旧版本状态 | 本候选状态 |
|------|-----------|-----------|
| EPUB 整块点击选区 → 高亮创建 | ✅ 已验 | 代码等价（除 `tapActionPreset` 删除） |
| EPUB 持久高亮分页间保持 | ✅ 已验 | 代码等价 |
| TXT 滚动选区离开视口自动清空 | ✅ 已验 | 代码等价 |
| TXT 滚动单元 dispose 清空选区 | ✅ 已验 | 代码等价 |
| 笔记列表点击跳转到高亮位置 | ✅ 已验 | 代码等价（typed target inline） |
| Legado TXT 导入 → marker 保护 | ✅ 已验 | 代码等价（`buildString` 替换 `LocatorBuilder.progressJson`） |
| Legado EPUB 导入 → 章节定位 | ✅ 已验 | 代码等价（产物逐字一致） |
| 导入后首次阅读不覆盖 marker | ✅ 已验 | 代码等价 |
| 退出/暂停不覆盖 marker | ✅ 已验 | 代码等价 |
| 99.4% 边界不误标完成 | ✅ 已验 | 代码等价 |

### 6.2 新候选未验项（需真机复验）

以下项因代码路径变化或联合树底座差异，需在真机上重新验证：

| # | 用例 | 原因 | 优先级 |
|---|------|------|--------|
| 1 | EPUB 整块点击选区 → 联合树底座上的高亮创建 | 底座从 HEAD+overlay 变为联合树；`tapActionPreset` 已删除 | P0 |
| 2 | EPUB 持久高亮在联合树分页器上的投影 | `PagedEpubView` 可能与 HEAD 版本有差异 | P0 |
| 3 | TXT 滚动选区失效在联合树 `plainListState` 上的行为 | `LazyListState.layoutInfo` 行为依赖 Compose 版本 | P1 |
| 4 | 笔记跳转 typed target 在联合树 `AnnotationType` 枚举上的匹配 | `AnnotationType` 来自联合树 overlay，需确认枚举值一致 | P0 |
| 5 | `LegadoReadingProgressImporter` EPUB 分支 `buildString` 产物在真机上的定位精度 | 产物与 `LocatorBuilder.progressJson(globalOffset=null)` 逐字一致，但需真机确认 | P0 |
| 6 | `LegadoMarkerSaveGuard` 在联合树 `ReaderProgressEffects` debounce 周期内的守卫时序 | debounce 500ms 时序与联合树 `snapshotFlow` 交互需真机确认 | P1 |
| 7 | `ReaderScreen.persistOnLeave` 守卫在联合树退出流程中的触发 | 联合树退出流程可能与 HEAD 版本有差异 | P0 |
| 8 | `ReaderViewModel.withSelectionCleared` 在联合树 action 处理链中的时序 | 联合树 `onAction` 分发可能与 HEAD 版本有差异 | P1 |

### 6.3 复验命令

```bash
# 1. 确认设备
adb devices

# 2. 构建 Debug APK
cd E:\creation-reading-assistant\stage-e-reader-preserved-20260930\android
.\gradlew :app:assembleDebug

# 3. 安装到设备
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk

# 4. 按上表逐项验证，截图记录
```

---

## 7. 所有权与边界

### 7.1 本候选修改的文件（10 个）

| 文件 | 归属 | 修改方式 |
|------|------|----------|
| `LegadoProgressMarker.kt` | 新文件 | 从 recovery 工作树直接复制 |
| `LegadoReadingProgressImporter.kt` | 新文件 | 从 recovery 工作树复制 + 1 处 `buildString` 适配 |
| `ReaderContentHostEpubBranch.kt` | 主所有权 | 从 recovery 工作树复制 - `tapActionPreset` |
| `ReaderContentHostPlainTextBranch.kt` | 主所有权 | 从联合树底座 + S07-F5/F2.2/F2.3 hunk |
| `ReaderProgressEffects.kt` | 主所有权 | 从 recovery 工作树复制 - 2 处非目标回退 |
| `ReaderScreen.kt` | 主所有权 | 从联合树底座 + S12-④ hunk |
| `ReaderSheetHost.kt` | 接缝 | 从联合树底座 + S07-NoteJump hunk + inline helpers |
| `ReaderNotesSheet.kt` | 主所有权 | 从联合树底座 + S07-NoteJump rename hunk |
| `ReaderDocumentLoader.kt` | 主所有权 | 从 recovery 工作树直接复制 |
| `ReaderViewModel.kt` | 接缝 | 从联合树底座 + S07-F2 hunk |

### 7.2 未修改的非所有权文件（需集成者关注）

| 文件 | 原因 |
|------|------|
| `LocatorBuilder.kt` | `globalOffset` 非空 → 已用 `buildString` 绕行 |
| `LocatorCodec.kt` | `legacyOffset` 非空 → 已用 `buildString` 绕行 |
| `ReaderScreenState.kt` | `selectionSnapshot` 缺失 → S07-F2.4 BLOCKED |
| `ReaderProgressActions.kt` | `computeLocatorJsonForSnapshot` 缺失 → S07-F2.4 BLOCKED |
| `ReaderTocBookmarkList.kt` | `TocNoteEntry` / `highlightNavigationTarget` / `noteNavigationTarget` → 已 inline 到 `ReaderSheetHost.kt` |
| `ReaderTocSheet.kt` | `notes` / `onPickNote` API → TOC 笔记跳转 BLOCKED |

### 7.3 联合树功能保留

本候选不修改联合树的 B1/B2/G6/S14/M-BUG-03 功能文件。所有改动限上述 10 个文件。

---

## 8. 约束遵守

- ✅ 不 stage/commit/push/reset/checkout
- ✅ 不搬移或删除目录
- ✅ 不触碰原仓库、正式书库或实体手机
- ✅ 不操作 MuMu 模拟器
- ✅ 构建/缓存放独占 E 盘
- ✅ 恢复仓库只读（唯一写入：本报告）
- ✅ 共享源码只读（联合树未修改）
- ✅ HEAD 保持 `513891d6`
