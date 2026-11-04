# 阶段 E — Reader 归属 HOLD 分析（G8 逐 hunk 依赖矩阵）

> 生成时间：2026-09-29
> 负责范围：G8 的 103 项 + `ui/screen/reader/**` 及 reader 专属组件中标 HOLD 的项
> 约束：不改窗口 1（数据/同步）或窗口 2（非 reader UI）的文件
> 上游文档：
> - [`2026-09-29-stage-e-second-round-review.md`](../../reviews/2026-09/2026-09-29-stage-e-second-round-review.md)
> - [`2026-09-29-stage-e-android-hunk-plan.md`](2026-09-29-stage-e-android-hunk-plan.md)
> - E 盘全覆盖矩阵 `stage-e-full-coverage-matrix.csv`（SHA-256 `eb289a71...`，376 行）
>
> E 盘产物：
> - 逐 hunk 依赖矩阵 `stage-e-reader-hunk-matrix.csv`（SHA-256 `50537f1ec89084489c72899b4317405a99d31caefbf4eca675458457d5c2bdd2`，36 行）
> - 跨组依赖矩阵 `stage-e-reader-cross-group-deps.csv`（SHA-256 `558c344631778b4f5324bad7de57fd4616efd8330c0b055dbd3e35bc4bf40f58`，19 行）
>
> 本文件为只读分析输出，不含任何源码/测试/配置改动。未 stage/commit/push/reset/checkout，未运行构建，未操作设备。

---

## 1. 接手范围

| 维度 | 数量 | 来源 |
|------|------|------|
| G8 总项 | 103 | 覆盖矩阵 sub_group ∈ {G8a, G8b, G8b+G8c+G8d, G8c, G8e} |
| G8a（T8 layout/pager） | 31 | 矩阵 |
| G8b（P3.1 EPUB replace） | 6 | 矩阵 |
| G8b+G8c+G8d（EpubRepository.kt 混合） | 1 | 矩阵 |
| G8c（S09 locator） | 3 | 矩阵 |
| G8e（reader UI） | 62 | 矩阵 |
| reader 专属 HOLD 项 | 42 | 矩阵 group=HOLD 且路径含 reader |
| **接手总数** | **145** | G8 103 + reader HOLD 42 |

---

## 2. EpubRepository.kt — 9 hunk 逐 hunk 归属

文件路径：`android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt`
矩阵归属：G8b+G8c+G8d（MIXED: P3.1 replace + S09 nav + S14 backup safety）

### 2.1 Hunk 明细

| Hunk | 行范围 (old→new) | 内容摘要 | Session | 子组 | 混合 |
|------|------------------|----------|---------|------|------|
| H1 | -10,6 → +10,7 | `import AppLog`（回滚诊断日志） | S14 | G8d | 否 |
| H2 | -28,6 → +29,8 | `fileOps: FileOps` 构造参数（Hilt 注入） | P3.1+S14 | G8b+G8d | **是** |
| H3 | -70,24 → +73,75 | 残留守卫 `backup.exists()` 拒绝 + `requireFreshSource=true` + `backupReady`/`promoteStarted` 状态机 | P3.1+S14 | G8b+G8d | **是** |
| H4 | -95,10 → +149,19 | `fileOps.renameTo`/`copyTo` 备份晋升 + `backupReady` 分支 | P3.1+S14 | G8b+G8d | **是** |
| H5 | -112,6 → +175,11 | `content_hash = computeFileHash(target)` 写入 BookEntity | S04 | G8b | 否 |
| H6 | -120,6 → +188,7 | catch 块重写：`backupReady` 门控 `target.delete` + 调 `restoreBackupAfterFailedPromotion` | S14 | G8d | 否 |
| H7 | -129,16 → +198,99 | `restoreBackupAfterFailedPromotion` 方法 + `contentEquals` 流式比对 + `AppLog.w` 诊断 | S14 | G8d | 否 |
| H8 | -234,7 → +386,11 | `globalOffset = absoluteOffset.takeIf { it >= 0 }`（locator v2 单一写入契约） | S09 | G8c | 否 |
| H9 | -311,6 → +467,37 | `computeFileHash` 方法 + companion object（`MAX_HASH_BYTES` + `BUFFER_BYTES`） | S04+S14 | G8b+G8d | **是** |

### 2.2 编译依赖链

```
FileOps.kt (??, G8d)
  └─→ EpubRepository.kt H2 (fileOps 构造参数)
        ├─→ H3 (fileOps.renameTo/copyTo 在备份晋升)
        ├─→ H4 (fileOps.renameTo/copyTo 分支)
        └─→ H7 (fileOps.renameTo/copyTo 在回滚恢复)

EpubParser.kt (M, G8b, 4 hunks)
  └─→ H3 (requireFreshSource=true 调用)

H9 (computeFileHash + companion object)
  ├─→ H5 (content_hash = computeFileHash(target))
  └─→ H7 (BUFFER_BYTES 常量被 contentEquals 使用)

H1 (import AppLog)
  └─→ H7 (AppLog.w 诊断调用)

LocatorBuilder.kt (M, G8c, 1 hunk)
  └─→ H8 (globalOffset: Int? 签名)

LocatorCodec.kt (M, G8c, 2 hunks)
  └─→ H8 (encode(Int?) 签名)
```

### 2.3 关键结论

1. **H8 是唯一纯 G8c hunk**。它只改 `globalOffset` 的计算方式（`takeIf` 替代 `if/else`），逻辑独立，可通过 `git add -p` 单独暂存。

2. **H2 是编译瓶颈**。`fileOps: FileOps` 是构造参数，`FileOps.kt`（untracked, G8d）必须在同一提交中添加，否则 Hilt 无法提供依赖、编译失败。这意味着 **G8b 和 G8d 不可能独立提交 EpubRepository.kt 的任何 hunk**——只要含 H2，就必须带 FileOps.kt；而 H3/H4/H7 都引用 `fileOps`，也依赖 H2。

3. **最小可编译提交**：`FileOps.kt` + `EpubParser.kt`(4 hunks) + `EpubRepository.kt` H1-H7,H9（即排除 H8）。这覆盖 P3.1 + S14 全部改动。H8 可独立拆为 G8c 提交，但需 `LocatorBuilder.kt` + `LocatorCodec.kt` 同提交。

4. **H5 依赖 H9**：`computeFileHash` 调用在 H5，方法定义在 H9。两者在同一文件内，`git add -p` 拆分时必须同暂存。

5. **H6 依赖 H7**：catch 块调用 `restoreBackupAfterFailedPromotion`，方法定义在 H7。同文件内，必须同暂存。

---

## 3. PageTurner.kt — 24 hunk 逐 hunk 归属

文件路径：`android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/PageTurner.kt`
矩阵归属：G8a（T8 page turner）

### 3.1 Hunk 明细

| Hunk | 行范围 (old→new) | 内容摘要 | Session | 子组 |
|------|------------------|----------|---------|------|
| PT1 | -69,6 → +69,11 | TurnScene 动画场景安全文档注释 | T8 | G8a |
| PT2 | -109,7 → +114,17 | `engaged` → `pendingTurns`/`turnGate`/`turnEpoch`/`scene` 状态 | T8 | G8a |
| PT3 | -121,6 → +136,12 | `recordedCurrent/Next/Prev` RecordedFrameSlot 追踪 | T8 | G8a |
| PT4 | -173,25 → +200,39 | LaunchedEffect 预渲染重写：scene + recorded slots + 5 次重试 | T8 | G8a |
| PT5 | -204,20 → +245,22 | `ensureBitmaps` 重写：recorded slot 门控 grab | T8 | G8a |
| PT6 | -226,14 → +269,15 | `settle`：`engaged`→`scene` TurnScene 创建 + `ensureBitmaps` | T8 | G8a |
| PT7 | -246,7 → +290,9 | 注释：提交后不清 scene（动画继续） | T8 | G8a |
| PT8 | -262,11 → +308,39 | `settle` 提交/清理：`dx.snapTo` + `scene=null` + `turning` in finally + `turnWithoutNeighborSnapshot` | T8 | G8a |
| PT9 | -274,41 → +348,59 | LaunchedEffect `turnRequest` 重写：`pendingTurns` 队列 + `turnGate` + `turnEpoch` | T8 | G8a |
| PT10 | -325,19 → +417,25 | 拖拽处理：`turnGate.dragging` + drag start 创建 scene | T8 | G8a |
| PT11 | -348,10 → +446,11 | drag end：`scene=null` + `turnEpoch++` | T8 | G8a |
| PT12 | -360,8 → +459,8 | 拖拽 delta 边界：`sceneNext`/`scenePrev` 替代 `nextFrame`/`previousFrame` | T8 | G8a |
| PT13 | -383,7 → +482,7 | `engaged`→`scene==null` 在 slide/cover 分支 | T8 | G8a |
| PT14 | -395,6 → +494,7 | `recordedCurrent.frame = currentFrame` 在 record 回调 | T8 | G8a |
| PT15 | -406,14 → +506,22 | CapturePage 调用：`onRecorded` + `sceneNext`/`scenePrev` | T8 | G8a |
| PT16 | -464,7 → +572,7 | `nextFrame`→`sceneNext` 在边缘 alpha | T8 | G8a |
| PT17 | -479,7 → +587,7 | `previousFrame`→`scenePrev` 在 prev alpha | T8 | G8a |
| PT18 | -490,7 → +598,7 | 同上（16dp 边缘） | T8 | G8a |
| PT19 | -504,7 → +612,7 | 同上（32dp 边缘） | T8 | G8a |
| PT20 | -544,12 → +652,90 | **最大 hunk**：cover/slide fallback 布局重写（`engaged`→`scene`），+90 行 | T8 | G8a |
| PT21 | -612,6 → +798,8 | CapturePage 文档：`onRecorded` 参数说明 | T8 | G8a |
| PT22 | -620,6 → +808,7 | CapturePage 签名：添加 `onRecorded: () -> Unit` 参数 | T8 | G8a |
| PT23 | -638,6 → +827,7 | CapturePage body：`onRecorded()` 调用在 `drawLayer` 后 | T8 | G8a |
| PT24 | -645,3 → +835,36 | 定义 `TurnScene` + `RecordedFrameSlot` + `TurnGate` private 类 | T8 | G8a |

### 3.2 编译依赖链

```
PendingTurnQueue.kt (??, G8a)
  └─→ PT2 (pendingTurns = remember { PendingTurnQueue() })
  └─→ PT9 (pendingTurns.enqueue/poll/requeueFront/clear)

PT24 (private class definitions: TurnScene, RecordedFrameSlot, TurnGate)
  ├─→ PT2 (scene: TurnScene<Frame>?; turnGate: TurnGate)
  ├─→ PT3 (recordedCurrent/Next/Prev: RecordedFrameSlot)
  └─→ PT4-PT20 (all use scene/sceneCur/sceneNext/scenePrev)

PT22 (CapturePage onRecorded parameter)
  └─→ PT23 (onRecorded() call in body)
  └─→ PT15 (CapturePage calls with onRecorded = { recordedNext.frame = sceneNext })
```

### 3.3 关键结论

1. **全部 24 hunks 均为 T8（G8a）**，无跨 session 混合。PageTurner.kt 可作为单一文件提交，无需 `git add -p` 拆 hunk。

2. **PendingTurnQueue.kt（untracked）必须同提交**。PT2 和 PT9 引用 `PendingTurnQueue` 的 `enqueue`/`poll`/`requeueFront`/`clear` API。若 PendingTurnQueue.kt 不在位，编译失败。

3. **TurnScene/RecordedFrameSlot/TurnGate 是 private 类**，定义在 PT24（文件末尾），仅 PageTurner.kt 内部使用。无需独立文件，但 PT24 必须与引用它的 PT2-PT20 同提交。

4. **最小可编译提交**：`PageTurner.kt`（全 24 hunks）+ `PendingTurnQueue.kt`。这两个文件自包含，不依赖 G8b/G8c/G8d/G8e 的任何改动。

5. **已有验证**：D gate JVM 3208/3208 覆盖 PageTurner 相关 JVM 测试。`PendingTurnQueueTest.kt`（??）和 `PageTurnerHighSpeedTest.kt`（?? androidTest）是新增测试，前者在 JVM 范围内，后者不在。

---

## 4. G8c（S09 locator）— 产品合同判定

### 4.1 改动内容

| 文件 | Hunk 数 | 改动 |
|------|---------|------|
| `LocatorBuilder.kt` | 1 | `globalOffset: Int? = legacyOffset`（可空；未知时省略） |
| `LocatorCodec.kt` | 2 | `encode(legacyOffset: Int?)` 签名 + 条件写 `"offset"` 字段 |
| `EpubRepository.kt` H8 | 1 | `globalOffset = absoluteOffset.takeIf { it >= 0 }` |
| `StoredProgressLocatorNavigationTest.kt` (??) | — | S09 导航测试 |

### 4.2 性质判定

代码注释引用 "S12-①" 为根因（Legado 导入 locator 被回源跳转一致性校验拒绝）。改动本身是 **locator v2 写入契约修复**：将 `globalOffset` 从 `Int`（默认 `legacyOffset`）改为 `Int?`（默认 `legacyOffset`，但允许 `null` 省略），避免章内偏移冒充全局偏移。

覆盖矩阵标注为 "S09 locator" / "S09 nav test"。S09 未出现在第二轮复核、`docs/handoff/current.md`、`docs/plans/legado-feature-backlog.md` 中，无产品合同证据。

### 4.3 判定

**G8c 标 HOLD**。理由：

1. 覆盖矩阵将 G8c 标为 "S09"，属候选功能标签，非已验恢复修复。
2. S09 未在任何交接文档或产品合同中获确认。
3. `LocatorCodec.encode` 签名从 `Int` 改为 `Int?` 是 **破坏性 API 变更**：所有调用方必须适配。G8e 中 `ReaderHelpers.kt`、`ReaderProgressEffects.kt`、`ReaderNotesExporter.kt`、`ReaderNotesSheet.kt` 均导入 `LocatorCodec`，若 G8c 提交而 G8e 不同步适配，编译失败。
4. `StoredProgressLocatorNavigationTest.kt`（??）是新增导航测试，其验证的跨章导航行为属候选功能范畴。

**不把 G8c 混作已验恢复修复。** 若后续获产品合同，需同提交 G8c 源码 + G8e 中所有 `LocatorCodec` 调用方适配 + `StoredProgressLocatorNavigationTest.kt`。

---

## 5. G8a–G8e 子组编译边界矩阵

### 5.1 子组内编译边界

| 子组 | 必须同提交的源码 | 必须同提交的测试 | 已有验证 | 尚未验证 |
|------|------------------|------------------|----------|----------|
| **G8a** | `PageTurner.kt`(24h) + `PendingTurnQueue.kt`(??) + 其余 9 个 M pager 文件 + `WholeBookPageModel.kt`(??) + `CustomTipPlaceholder.kt`(??) | `PendingTurnQueueTest.kt`(??) + `WholeBookPageModelTest.kt`(??) + `PageTurnerHighSpeedTest.kt`(?? androidTest) | D gate JVM 3208/3208 | androidTest 不在 JVM 范围；拆分后独立编译 |
| **G8b** | `EpubParser.kt`(4h) + `EpubRepository.kt` H1-H7,H9 + `FileOps.kt`(??) | `EpubParserFreshSourceTest.kt`(??) + `EpubRepositoryReplaceTest.kt`(??) + `EpubRepositorySaveProgressLocatorTest.kt`(??) + `EpubReplaceRoomIsolatedTest.kt`(?? androidTest) + `EpubParserInstrumentedTest.kt`(M androidTest) | S04 EPUB isolated Room 8/8（部分）；D gate JVM 3208/3208（间接） | androidTest 不在 JVM 范围；H2 fileOps 依赖 FileOps.kt |
| **G8c** | `LocatorBuilder.kt`(1h) + `LocatorCodec.kt`(2h) + `EpubRepository.kt` H8 | `StoredProgressLocatorNavigationTest.kt`(??) | **无** | **全部**：S09 产品合同未确认；LocatorCodec.encode 签名变更需 G8e 调用方适配 |
| **G8d** | `FileOps.kt`(??) + `EpubRepository.kt` H1-H7,H9（与 G8b 共享） | 同 G8b | 同 G8b | FileOps.kt 是 G8b+G8d 共享依赖 |
| **G8e** | 51 个 M reader UI 文件 + 7 个 ?? reader UI 文件 | 对应 reader UI 测试 | D gate JVM 3208/3208（JVM 部分） | 拆分后独立编译；G8e → G8a/G8c 跨组依赖 |

### 5.2 跨组依赖（G8e → G8a/G8c）

**G8e → G8a（pager）**：13 个 M 文件 + 1 个 ?? 文件导入 `feature.reader.pager` 符号。若 G8a 改变 pager 公共 API（如 `PagedChapterSource`、`PagedReaderHost`、`ReaderPagePosition` 等），G8e 必须同提交。

**G8e → G8c（locator）**：6 个 M 文件导入 `feature.reader.locator` 符号。`LocatorCodec.encode` 签名从 `Int` 改为 `Int?` 是破坏性变更，以下 G8e 文件的调用必须适配：
- `ReaderHelpers.kt` → `LocatorCodec`
- `ReaderProgressEffects.kt` → `LocatorCodec` + `LocatorBuilder`
- `ReaderProgressActions.kt` → `LocatorBuilder`
- `ReaderSheetHost.kt` → `LocatorBuilder`
- `ReaderNotesExporter.kt` → `LocatorCodec`
- `ReaderNotesSheet.kt` → `LocatorCodec`

**G8e → G8b/G8d（EpubRepository）**：无直接导入。EpubRepository 通过 viewmodel 层（`ReaderViewModel.kt`、`ReaderDocumentLoader.kt`、`ReaderChapterLoaderCoordinator.kt`）间接访问，这些文件不在 G8e 范围内（属 HOLD/viewmodel 窗口）。

### 5.3 跨组依赖（G8b/G8d → G8c）

`EpubRepository.kt` H8（G8c）与 H1-H7,H9（G8b+G8d）在同一文件内。若通过 `git add -p` 拆分 H8 为独立 G8c 提交，需确保 H8 不依赖 H1-H7,H9 的任何新增符号。经核对：
- H8 只改 `globalOffset` 赋值表达式，不引用 `fileOps`、`computeFileHash`、`restoreBackupAfterFailedPromotion` 等新增符号。
- H8 依赖 `LocatorBuilder.progressJson` 的 `globalOffset: Int?` 参数（G8c 内部）。
- **结论**：H8 可干净拆分为独立 G8c 提交，但需 `LocatorBuilder.kt` + `LocatorCodec.kt` 同提交。

---

## 6. reader 专属 HOLD 项（42 项）

以下 42 项在覆盖矩阵中 group=HOLD 且路径含 reader，归属待判定：

### 6.1 viewmodel 层（3 项）

| 文件 | 状态 | 说明 |
|------|------|------|
| `ui/viewmodel/ReaderViewModel.kt` | M | reader 主 ViewModel，导入 EpubRepository |
| `ui/viewmodel/ReaderDocumentLoader.kt` | M | 文档加载器，导入 EpubRepository + S12 Legado marker |
| `ui/viewmodel/ReaderModels.kt` | M | reader 数据模型 |

**判定**：`ReaderDocumentLoader.kt` 含 S12 Legado marker 集成（G4 hunk plan 已标），非 S12 hunk 归属待定。`ReaderViewModel.kt` 和 `ReaderModels.kt` 的改动需逐 hunk 分析。**标 HOLD**。

### 6.2 主题/设置（2 项）

| 文件 | 状态 | 说明 |
|------|------|------|
| `ui/theme/ReaderOverride.kt` | M | reader 主题覆盖 |
| `ui/theme/ReaderPaperPalette.kt` | M | reader 纸色色板 |

**判定**：主题改动可能跨 T8（pager 布局）和 reader UI 通用改动。**标 HOLD**。

### 6.3 未跟踪测试（37 项）

包括 reader selection、highlight、navigation、font、tap zone 等方面的未跟踪测试文件。这些测试的归属取决于被测源码的归属。**标 HOLD**，待被测源码归属判定后归入对应组。

---

## 7. 候选提交组与编译边界

### 7.1 G8a（T8 pager）— 可提交

**源码**（11 M + 3 ?? = 14 文件）：
- `PageTurner.kt`（24 hunks，全 T8）
- `PagedReaderHeaderFooter.kt`、`PagedReaderPageSurface.kt`、`PagedTxtReaderHost.kt`、`TxtPagedController.kt`、`PagedReaderClock.kt`、`PagedReaderControllerEffects.kt`、`PageSelection.kt`、`ReaderHardwareKeys.kt`、`ReaderTapAction.kt`（9 M）
- `PendingTurnQueue.kt`、`WholeBookPageModel.kt`、`CustomTipPlaceholder.kt`（3 ??）

**测试**：`PendingTurnQueueTest.kt`、`WholeBookPageModelTest.kt`（JVM）；`PageTurnerHighSpeedTest.kt`（androidTest，不在 JVM 3208/3208）

**编译边界**：`PageTurner.kt` + `PendingTurnQueue.kt` 必须同提交（PT2/PT9 引用 PendingTurnQueue API）。其余 pager 文件的改动需逐文件确认是否依赖 PageTurner 的新类型。

**已有验证**：D gate JVM 3208/3208（含 PageTurner 相关 JVM 测试）。

**尚未验证**：拆分后独立 `compileDebugKotlin`；androidTest `PageTurnerHighSpeedTest.kt` 未在设备执行。

### 7.2 G8b+G8d（P3.1 EPUB replace + S14 backup safety）— 可提交（合并）

**源码**（2 M + 2 ?? = 4 文件）：
- `EpubRepository.kt`（H1-H7, H9；排除 H8）
- `EpubParser.kt`（4 hunks）
- `FileOps.kt`（??，含 FileOps interface + DefaultFileOps + FileOpsModule）

**测试**：`EpubParserFreshSourceTest.kt`、`EpubRepositoryReplaceTest.kt`、`EpubRepositorySaveProgressLocatorTest.kt`（JVM）；`EpubReplaceRoomIsolatedTest.kt`、`EpubParserInstrumentedTest.kt`（androidTest）

**编译边界**：`FileOps.kt` 必须先于或同于 `EpubRepository.kt` 提交（Hilt 依赖）。`EpubParser.kt` 的 `requireFreshSource` 参数必须同提交（H3 调用）。G8b 和 G8d **不可独立提交**——H2/H3/H4/H7 同时引用 P3.1 和 S14 的符号。

**已有验证**：S04 EPUB isolated Room 8/8（部分覆盖）；D gate JVM 3208/3208（间接）。

**尚未验证**：拆分后独立编译；androidTest 未在设备执行。

### 7.3 G8c（S09 locator）— **HOLD**

**原因**：S09 产品合同未确认。`LocatorCodec.encode` 签名变更是破坏性 API 变更，需 6 个 G8e 调用方同适配。`StoredProgressLocatorNavigationTest.kt` 是候选功能测试。

**若获产品合同**，需同提交：
- `LocatorBuilder.kt`（1 hunk）+ `LocatorCodec.kt`（2 hunks）+ `EpubRepository.kt` H8
- G8e 中 6 个 `LocatorCodec`/`LocatorBuilder` 调用方适配
- `StoredProgressLocatorNavigationTest.kt`

### 7.4 G8e（reader UI 通用）— **HOLD**

**原因**：51 M + 7 ?? = 58 文件，逐 hunk 归属未完成。G8e → G8a/G8c 跨组依赖需 G8a/G8c 先判定。

**若 G8a 先提交**：G8e 中仅依赖 G8a 的文件可随后提交（13+1 个），但需确认 G8a 未改变它们的导入符号签名。

**若 G8c 获产品合同**：G8e 中 6 个 `LocatorCodec`/`LocatorBuilder` 调用方必须同 G8c 提交。

---

## 8. 阻塞点

| # | 阻塞点 | 影响子组 | 严重度 | 状态 |
|---|--------|----------|--------|------|
| R1 | `EpubRepository.kt` H2 引用 `FileOps.kt`（??），Hilt 依赖 | G8b+G8d | 高 | **已判定**：FileOps.kt 必须同提交；G8b 与 G8d 不可独立拆分 |
| R2 | `EpubRepository.kt` H5 依赖 H9（computeFileHash 定义）；H6 依赖 H7（restoreBackupAfterFailedPromotion 定义） | G8b+G8d | 中 | **已判定**：同文件内 hunk 必须同暂存 |
| R3 | `PageTurner.kt` PT2/PT9 引用 `PendingTurnQueue.kt`（??） | G8a | 高 | **已判定**：PendingTurnQueue.kt 必须同提交 |
| R4 | S09 产品合同未确认 | G8c | 高 | **HOLD**：不混作已验恢复修复 |
| R5 | `LocatorCodec.encode` 签名 `Int`→`Int?` 是破坏性变更 | G8c → G8e | 高 | **HOLD**：6 个 G8e 调用方需适配 |
| R6 | G8e 58 文件逐 hunk 归属未完成 | G8e | 高 | **HOLD**：待 G8a/G8c 判定后逐文件分析 |
| R7 | reader 专属 HOLD 42 项归属未判定 | HOLD | 中 | **HOLD**：viewmodel/主题/未跟踪测试待逐文件分析 |
| R8 | androidTest reader 测试不在 JVM 3208/3208 范围 | G8a, G8b+G8d | 中 | **已知**：D build 仅编译通过，未设备执行 |

---

## 9. 本次完成与未完成项

### 已完成

| 项 | 结果 |
|----|------|
| EpubRepository.kt 9 hunks 逐 hunk 归属 | H1-H9 全部判定：H8→G8c，其余→G8b+G8d（混合） |
| PageTurner.kt 24 hunks 逐 hunk 归属 | PT1-PT24 全部判定：均为 T8/G8a，无跨 session 混合 |
| EpubRepository.kt 编译依赖链 | FileOps.kt → H2 → H3/H4/H7；H9 → H5/H7；H1 → H7 |
| PageTurner.kt 编译依赖链 | PendingTurnQueue.kt → PT2/PT9；PT24 → PT2-PT20；PT22 → PT23/PT15 |
| G8c S09 产品合同判定 | **HOLD**：无产品合同证据，不混作已验恢复修复 |
| G8e → G8a/G8c 跨组依赖 | 13+1 文件依赖 G8a，6 文件依赖 G8c（含破坏性签名变更） |
| G8b/G8d 不可独立拆分 | H2 fileOps 构造参数是共享依赖，必须合并提交 |
| 逐 hunk 依赖矩阵 | 写入 E 盘 CSV，SHA-256 `50537f1e...`，36 行 |
| 跨组依赖矩阵 | 写入 E 盘 CSV，SHA-256 `558c3446...`，19 行 |

### 未完成（HOLD）

| 项 | 原因 | 后续需做 |
|----|------|----------|
| G8c（S09 locator）提交 | S09 产品合同未确认 | 获产品合同后同提交 G8c + G8e 调用方适配 |
| G8e 58 文件逐 hunk 归属 | 量大，依赖 G8a/G8c 先判定 | G8a 提交后逐文件分析 |
| reader 专属 HOLD 42 项 | viewmodel/主题/未跟踪测试归属待定 | 逐文件 session 归属分析 |
| G8a/G8b+G8d 拆分后独立编译 | 全量工作树 build 不证明拆分后可编译 | 仅暂存对应文件后 `compileDebugKotlin` |
| androidTest reader 测试 | 不在 JVM 3208/3208 范围 | 设备执行 `EpubReplaceRoomIsolatedTest` 等 |

---

## 10. 统计汇总

| 维度 | 数量 |
|------|------|
| 接手项 | 145（G8 103 + reader HOLD 42） |
| 已判定项 | 36（EpubRepository.kt 9 hunks + PageTurner.kt 24 hunks + LocatorBuilder.kt 1 hunk + LocatorCodec.kt 2 hunks） |
| 可提交子组 | 2（G8a、G8b+G8d） |
| 仍 HOLD 子组 | 2（G8c、G8e） |
| 仍 HOLD 项 | 109（G8c 3 + G8e 62 + reader HOLD 42 + EpubRepository.kt H8 归 G8c 1 + FileOps.kt 归 G8d 待 G8b 合并 1） |
| 阻塞点 | 8（R1-R8） |

---

> 本文件仅为只读分析输出。所有 hunk 拆分建议需在实施阶段由对应文件所有者用 `git add -p` 执行。编译边界均标"待验证"——全量工作树 build 通过不证明任一拆分后提交可独立编译。
