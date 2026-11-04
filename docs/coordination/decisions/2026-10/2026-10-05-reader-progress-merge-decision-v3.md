# CRA-READER-PROGRESS-MERGE-DECISION-v3 —— 阅读进度调用链与合并接缝：只读审查与精确合并方案

- 任务编号/版本：`CRA-READER-PROGRESS-MERGE-DECISION-v3`；2026-10-05。
- 执行者：Claude Code（桌面应用 Code 标签页）；客户端上报模型串 `anthropic/claude-ccr-h776f726b62756464792041492f676c6f62616c3a646565707365656b2d76342e312d666c617368`（解码为 `workbuddy AI/global:deepseek-v4.1-flash`，与任务书建议模型一致；未做界面核验）。
- 性质：**只读审查**。不实施合并、不构建、不操作设备、不改任何源码（含 D/E 产品树）。
- 状态：**已交付待 Codex 核查**。

## 一、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD（开工=收工） | `e39a78da9c62f02614207cffb36969a32492eaea`（与预期一致） |
| 暂存区 | 条目 0（与预期一致） |
| 候选（只读） | `E:\creation-reading-assistant\next-round-joint-final-v3\android` |
| 三方差异来源 | `E:\creation-reading-assistant\next-round-joint-integration-manifest-v2\evidence\diffs` |
| 分页候选（只读，未在本轮并表） | `E:\creation-reading-assistant\next-round-md-paged-chapter-fix-v1\android` |

**审查文件原始 SHA-256（三方）**：

| 文件 | HEAD | 候选（final-v3） | D 工作树 |
|---|---|---|---|
| `ui/screen/reader/ReaderProgressActions.kt` | `eaacfb9be8dda2a1…` | `e35ddf56a3891f11…` | `933513e1400b852f…` |
| `ui/screen/reader/ReaderProgressEffects.kt` | `7e4b4ab884f1d61c…` | `a59258beb9499eec…` | `a7baa349494e328b…` |

（全值见 `execution.json`。**输入无漂移**：HEAD 与暂存区与预期一致。）

## 二、所有权与改动

- 只写：`E:\creation-reading-assistant\reader-progress-merge-decision-v3\`（`evidence/`、`execution.json`）与本报告。
- D/E 产品树、旧报告、旧工件全部**只读**。无 Git 写操作（仅 `rev-parse`/`show`/`status`/`cat-file`）。未改 `current.md`/TODO/公共清单。
- 未取得两文件写入权；**拟议 patch 仅置于本任务 E 目录，未应用、未编译**。

## 三、环境

Windows / Git Bash；Python 3.13（仅 stdlib）；`diff`/`git` 只读子命令。未启动 JDK/Gradle、未连接设备、未运行任何测试。

## 四、执行与结果

### 4.1 差异方向固定与独立复算

方向固定为：**HEAD**（git HEAD 版）→ **CAND**（候选 final-v3）→ **DW**（D 工作树 WIP）；`cand2dw` 的 `-` 行属候选、`+` 行属 D。

本人**独立复算**了全部 6 份 diff（`diff -u --strip-trailing-cr`，行尾归一），与 manifest-v2 的 `evidence/diffs/` **逐字节一致（6/6）**。据此得到两条可验证的关系：

| 文件 | 关系 | 判定依据（可复算） |
|---|---|---|
| `ReaderProgressEffects.kt` | **候选为 D 的严格超集** | `cand2dw` 中 D 独有行 **7 行，全部存在于 HEAD**（D 独有新增 = **0**）；候选独有新增 87 行 |
| `ReaderProgressActions.kt` | **真合并（互补）** | D 独有新增 **50 行**、候选独有新增 **28 行** → 两侧各有对方没有的语义 |

> 这条结论直接回答上一轮的分歧：manifest-v2 对 Effects 写"采纳候选"是对的方向，但它同时称"D 门控需人工确认"；实测 **D 在 Effects 里根本没有独立改动**（其 ci 反推与 LaunchedEffect key 与候选逐字节相同，见 4.3 的 E7），所以不存在"待确认的 D 门控"。Actions 一侧，上一轮漏列的 D 独有项（`plainListState`/`readingUnits` 参数、`livePlainOffset`、`chapterForLocator` 反推 ci）经本次复算确认全部存在。

### 4.2 `ReaderProgressActions.kt` 逐 hunk 分类（7 hunk）

| # | 区域 | 候选（HEAD→CAND） | D（HEAD→DW） | 关系 | 合并动作 |
|---|---|---|---|---|---|
| A1 | imports | +`MarkdownDocument`、+`MarkdownRenderModel` | +`DocChapter` | 互补 | 三个 import 全保留 |
| A2 | `goToChapter` 签名与落点 | 无 | +`atChapterEnd: Boolean = false`；为真时落点取「下一章章首−1」 | D-only | **取 D** |
| A3 | 新函数 | 无 | `txtScrollChapterTargetOffset(direction, txtChapters, txtChapterIndex): Int?` | D-only | **取 D** |
| A4 | 新函数 | `markdownVisibleSourceOffset(chapter, chapterBase, blocksGlobal, itemIndex, fraction): Int?` | 无 | CAND-only | **取候选** |
| A5 | `persistCurrentProgress` 签名 | 无 | +`plainListState: LazyListState? = null`、+`readingUnits: List<ReadingUnit> = emptyList()` | D-only | **取 D** |
| A6 | `persistCurrentProgress` else 链 | +`else if (markdownDocument != null) { … markdownVisibleSourceOffset(…) ?: return }` | +`livePlainOffset` 计算；`else` 由 `visiblePlainOffset` 改为 `livePlainOffset` | **同区冲突（同一条 if-else 链）** | **组合**：`pager → markdown(候选) → livePlainOffset(D)` |
| A7 | locator 章号 | 无 | +`chapterForLocator` 反推；`chapterIndex` 由 `0` 改为 `chapterForLocator` | D-only | **取 D** |

> A6 是上一轮被误判的地方。上一轮写"两者都保留；改动区域在**不同函数**（cand2dw 已证）"——事实相反：**候选与 D 改的是同一个 `persistCurrentProgress` 的同一段 else 链**，`markdownVisibleSourceOffset` 虽是候选新增函数，但它的**调用点**与 D 的 `livePlainOffset` 落在同一表达式里。因此必须逐行组合，不能"各取各的"。

**A6 组合后的语义（已核）**：

```kotlin
val livePlainOffset = if (plainListState != null && readingUnits.isNotEmpty()) { … } else { visiblePlainOffset }
val absoluteOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
    pagedAbsOffset
} else if (markdownDocument != null) {           // ← 候选：Markdown 走 canonical source 偏移
    … markdownVisibleSourceOffset(…) ?: return
} else {
    livePlainOffset                              // ← D：TXT 滚动读实时列表偏移
}.coerceAtLeast(0)
```

- 分支优先级**正确**：Markdown 滚动渲染在 `epubListState`（已核 `ReaderContentHostMarkdownBranch.kt:149` 用 `s.epubListState`），TXT 滚动在 `plainListState`。`markdownDocument != null` 先短路，故 Markdown 不会被 `livePlainOffset`（读 `plainListState`）误取。
- `livePlainOffset` 被提前求值，但只在 `else` 使用；`currentPlainListOffset` 是**纯函数**（`ReaderScrollPosition.kt:17`，无副作用），故提前求值无害。
- 候选的 `?: return` 语义保留：Markdown 取不到 canonical 偏移时不落库（与候选原行为一致）。

### 4.3 `ReaderProgressEffects.kt` 逐 hunk 分类（7 hunk）与门控结论

| # | 区域 | 候选 | D | 关系 | 结论 |
|---|---|---|---|---|---|
| E1 | `switchedIntoScroll` 锚定 | Markdown 时锚 `epubListState` 三元组 | = HEAD（仅 `plainListState`） | CAND-only | 取候选 |
| E2 | 三个 Markdown `LaunchedEffect`（恢复/锚定/防抖落库，均用 `epubListState`） | 新增 | = HEAD（无） | CAND-only | 取候选 |
| E3 | 纯文本防抖守卫 | 含 `markdownDocument != null` 排除 | = HEAD（无排除） | CAND-only | 取候选 |
| E4 | `pendingInitialPosition` TXT 恢复守卫 | 含 `markdownDocument == null` | = HEAD（无） | CAND-only | 取候选 |
| E5 | SE4 源定位 | Markdown 时置 `pendingInitialPositionState=false` | = HEAD（无） | CAND-only | 取候选 |
| E6 | 注解分派 | `annotationNavigationPlan` + 穷尽 `when`（Epub/Plain/else→`applyMarkdownAnnotationPlan`） | = HEAD（`if epub / else if plain`，无 Markdown 支） | CAND-only | 取候选 |
| E7 | ci 反推 ×2 + SE4 `LaunchedEffect` key（`isLoading`/`error`/`pendingSourceLocatorJsonState`） | 存在 | 存在，**与候选逐字节相同** | 两侧相同 | 无需动作 |

**门控结论（终局，不再"待确认"）：`ReaderProgressEffects.kt` 直接采纳候选。**

依据（可复算，不靠函数名或测试名）：
1. `cand2dw` 的 D 独有行 **7 行全部存在于 HEAD** → D 在本文件**没有新增任何候选没有的语义**；候选独有新增 87 行。
2. D 的两个实际改动（ci 反推、SE4 key）在候选里**逐字节存在**（`ReaderProgressEffects.CAND.kt:362/425` 与 `:501/618`）。
3. 因此 E1–E6 属于**候选替代 HEAD 旧路径**（不是"被候选替代的旧 D 路径"，D 从未改过这些区域），不是"需保留的 D 独立行为"，也不是"需组合"。

**合并依赖**：候选的 E2/E6 依赖候选新增文件 `ui/screen/reader/ReaderAnnotationNavigation.kt`（`annotationNavigationPlan`/`applyMarkdownAnnotationPlan`/`AnnotationNavigationPlan`，D 树不存在）。该文件来源已由 Codex 核定为 `md-annotation-return-fix-v1`，合并时必须一并纳入，否则编译失败。

### 4.4 `persistCurrentProgress` 全部直接调用方

| 调用点 | 状态 | 新参数是否传入 | 结论 |
|---|---|---|---|
| `ReaderActions.kt` 的 `persistCurrentProgress` lambda（经 `ReaderScreen.persistOnLeave` → `nav.persistCurrentProgress()`） | **候选 == HEAD；D 有 WIP** | **D 显式传入** `plainListState = plainListState, readingUnits = readingUnits` | 属 **181 保留D/no-op**：合并**不得覆盖**该文件；D 版已传入，`livePlainOffset` 在"离开阅读器保存"路径生效 |
| `ReaderProgressEffects.kt` 的 Markdown 防抖落库（候选新增） | 候选新增 | 未传（候选无此参数） | 该调用 `markdownDocument != null` → 走 A6 的 Markdown 分支，`livePlainOffset` 不参与；19 个位置实参 + `onAction` 具名，合并后新参数有默认值仍可编译 |
| `test` / `androidTest` | **无调用**（grep 0 命中） | — | 无测试调用点需改 |

**默认值是否掩盖遗漏**：A5 的两个新参数有默认值（`null` / `emptyList()`），未传时 `livePlainOffset` 退化为 `visiblePlainOffset`（旧行为）。经全仓检索，**唯一传参的调用点就是"离开阅读器保存"**——即 D 的设计意图（其注释："退出保存是离开阅读器前的最后一次落库"）。分页/滚动防抖路径不经 `persistCurrentProgress` 而直接 `onAction(SaveProgress)` 并自行用 `currentPlainListOffset` 计算偏移，故**不构成遗漏**。

**绝对偏移的选择（三模式）**：分页 → `pagedAbsOffset`（A6 首分支）；Markdown 滚动 → `markdownVisibleSourceOffset(...)`（候选 canonical source 偏移）；TXT 滚动 → `livePlainOffset`（D 的实时列表偏移）。临时查阅（`temporaryInspection`）与 `pendingInitialPosition` 通过函数首部的 `canPersistLegacyScrollPosition(pendingInitialPosition)` 与调用方的 `canPersistNormalReadingProgress(...)` 双重门控短路，不会误存。

### 4.5 坐标合同

| 坐标 | 载体 | 语义 |
|---|---|---|
| 全书 offset | `"offset"`（顶层）与 `locator_v2.offset`（`globalOffset`，默认 = `legacyOffset`） | 全书字符绝对偏移 |
| 章内 offset | `locator_v2.co`（`charOffset`） | **相对章首**的偏移 |
| canonical / source | 顶层 `"space"` | Markdown 走 canonical（规范文本）空间，写 `"canonical"`；TXT 与 Markdown **共享 canonical 全局偏移规则**；EPUB 走源文本混合空间 |
| locator_v2 ci/co | `locator_v2.ci` / `locator_v2.co` | 章号 / 章内偏移 |

**既有规则（R2-N1，见共享测试 `ReaderSourcePositionTest.kt:57` 注释）**：canonical（TXT/Markdown）locator **恒 `ci=0`、`co=全局偏移`**；该规则自洽的前提是 `chapterStartOffsets[0] == 0`（`SourceNavigationContract.resolveChapteredPosition` 的一致性校验正是 `chapterStart + co == offset`）。

**消费端（已核）**：
- 续读：`parseStoredAbsoluteOffset`（`ReaderDocumentLoader.kt:345`）**只读 v2 的 `offset`，不读 ci** → ci 变化不影响续读定位。
- Markdown 恢复：`ReaderDocumentLoader.kt:115` 读顶层 `space == "canonical"`，决定是否 `migrateLegacyOffset`。
- 回源：Markdown 走 `resolveChapteredPosition(preferGlobalOffset = true)`（`ReaderProgressEffects.kt:519`）—— **忽略显式 ci/co 并跳过一致性校验**；TXT 走 `resolvePlainPosition`（只读 `legacyOffset`/`charOffset`）；EPUB 走 `preferGlobalOffset = false`（**启用**一致性校验）。

**保留既有行为 vs 另一个潜在缺陷**：

- **保留既有行为**：候选的 `chapterIndex = 0` 与 R2-N1 规则自洽；A6 的 Markdown 分支、A4 的 `markdownVisibleSourceOffset`（canonical source 偏移）、D 的 `livePlainOffset`（TXT 实时列表偏移）都是两侧各自的既有语义，合并全部保留。
- **另一个潜在缺陷（D 侧，本轮不修）**：D 在 `persistCurrentProgress`（A7）与 `ReaderProgressEffects`（E7）里写 `chapterIndex = 真实章号`，但 `charOffset` **仍是全局偏移**（`charOffset = absoluteOffset` / `off`）。按契约 `co` 应为**章内**偏移，故 ci/co 元组**内部不自洽**（ci>0 时 `chapterStartOffsets[ci] + co != offset`）。**当前不造成功能问题**（回源忽略/不读元组，续读只读 offset），但它是潜在缺陷：任何将来以 `preferGlobalOffset = false` 消费 TXT/Markdown 存储 locator 的调用方，都会因一致性校验失败而**拒绝跳转**（`resolveChapteredPosition` 返回 null → "此阅读位置与当前正文不一致"）。**按任务要求不得顺带扩展新修复**，故合并原样取 D 的 `chapterForLocator`，只登记不改。
- **另记一处 D 内部不一致（未验证）**：D 的 Effects 注释称"不能用 `pagedSource.chapterIndexFor` —— 当前章作用域 source，章数恒为 1，只会返回 0（真机验证踩过）"，但 `persistCurrentProgress` 的 `chapterForLocator` **第一优先级恰是** `pagedSource?.chapterIndexFor(absoluteOffset)`。只读核对 `PagedChapterSource` 实现（`TxtChapterSource`/`EpubChapterSource`/`MarkdownChapterSource`/`ReplacedChapterSource` 的 `chapterCount` 均为**全书章数**）与注释不符；何者在真机成立**未验证**。合并原样取 D，不改。

### 4.6 逐函数/逐 hunk 合并顺序与调用方变更清单

**合并顺序（建议，均由单一集成 owner 执行）**：

1. `ReaderProgressActions.kt`：以 **D 版为基座**（保全 D WIP），依次叠加 A1 → A4 → A6，**保留** A2/A3/A5/A7。行尾保持仓库 **LF**（候选该文件是 CRLF，直接整文件覆盖会引入 CRLF）。
2. `ReaderProgressEffects.kt`：**直接采纳候选版**（超集），行尾 LF（两侧一致）。
3. 纳入候选新增 `ui/screen/reader/ReaderAnnotationNavigation.kt`（E2/E6 编译依赖）。
4. 测试：保留 D 的 `TxtScrollChapterTargetTest.kt`；纳入候选的 `MarkdownVisibleSourceOffsetTest.kt` 与 `ReaderAnnotationNavigationTest.kt`（后者候选新增）。
5. **不得覆盖**（181 保留D/no-op）：`ReaderActions.kt`、`ReaderLayerBuilders.kt`、`ReaderScaffold.kt`、`ReaderScreen.kt` —— 它们调用 A2/A3 的新签名，故 A2/A3 必须保留，否则编译失败。

**必要调用方变更清单**：**无源码改动**。A5 的新参数有默认值；唯一实参调用点（`ReaderActions.kt`）已在 D 版按名传入；A2/A3 的新签名已被 D 版 `ReaderActions.kt`（`goToPrevChapterFn`，`atChapterEnd = true`）与 `ReaderLayerBuilders.kt`（`txtScrollChapterTargetOffset(-1/+1, …)`）使用，属"保全 D"即可。

**拟议 patch**（**未应用、未编译**）：`E:\creation-reading-assistant\reader-progress-merge-decision-v3\evidence\ReaderProgressActions.D-to-merged.patch`（D→合并，3 hunk：imports / `markdownVisibleSourceOffset` / Markdown 分支）。生成物 `evidence/ReaderProgressActions.merged.kt` 仅置于本任务 E 目录。

### 4.7 最小回归矩阵

| 旅程 | 已有测试/证据 | 合流后新增验证需求 |
|---|---|---|
| **TXT 章首/章尾导航** | D：`TxtScrollChapterTargetTest.kt`（prev/next、首末章返回 null、空表、越界，共 7 断言） | 合并后**重跑该测试**；另确认 A2 的 `atChapterEnd` 默认 `false`，既有 `goToChapter` 调用（目录点击等）落点不变 |
| **离开阅读器保存** | 共享 `ReaderSourcePositionTest.kt` 只覆盖 `reportableSourceTarget`/`sourceNavigationTargetFor`，**不覆盖 `persistCurrentProgress`**；无任何 `persistCurrentProgress` 直测 | **需新增**：TXT 滚动 on-leave 走 `livePlainOffset`；Markdown on-leave 走 Markdown 分支（不落 `livePlainOffset`）；`chapterIndex` 写入 `chapterForLocator` |
| **分页 ci** | 无直测 | **需新增**：`chapterForLocator` 三级优先级（`pagedSource` → `chapterStartOffsets` 反推 → 会话 `chapterIndex`），含 `pagedSource == null` 与空 `chapterStartOffsets` 分支 |
| **Markdown 滚动进度与回源** | 候选：`MarkdownVisibleSourceOffsetTest.kt`（4 断言）、`ReaderAnnotationNavigationTest.kt`（候选新增）、`androidTest/ReaderAnnotationPendingConsumptionTest.kt`（验收资产） | 合并后**重跑**；并验证 Markdown 防抖落库经 `persistCurrentProgress` 走 Markdown 分支（读 `epubListState`，非 `plainListState`） |

**不重跑**：已核 111 JVM、五条 MuMu 功能旅程、恢复流程——本轮不构建、不操作设备，上述矩阵是**合流后**的验证需求，不是本轮动作。

## 五、原始证据

- 本任务工件：`E:\creation-reading-assistant\reader-progress-merge-decision-v3\`
  - `evidence/hunk-classification.json`：两文件逐 hunk 分类 + 调用方 + 依赖清单。
  - `evidence/ReaderProgressActions.merged.kt`、`evidence/ReaderProgressActions.D-to-merged.patch`：**未应用、未编译**。
  - `execution.json`：执行指纹、SHA、独立复算方法与结果。
- 只读引用：`next-round-joint-integration-manifest-v2/evidence/diffs/`（6 份 diff）、候选 `next-round-joint-final-v3/android`、D 工作树。
- 被引用的生产源码（只读）：`ReaderProgressActions.kt`、`ReaderProgressEffects.kt`、`ReaderActions.kt`、`ReaderContentHostMarkdownBranch.kt`、`ReaderScrollPosition.kt`、`SourceNavigationContract.kt`、`LocatorCodec.kt`、`LocatorBuilder.kt`、`EpubLocatorMapping.kt`、`ReaderDocumentLoader.kt`、`ReaderPagerEngineState.kt`、`PagedChapterSource.kt`。

## 六、恢复与副作用

- **无设备操作**：未连接 MuMu、未安装、未启动。
- **无源码写入**：D/E 产品树零改动；拟议合并版本只在 E 目录，未应用于任何树。
- **无 Git 写**：HEAD/暂存区未变。
- **无构建**：未启动 JDK/Gradle，未产生编译产物。

## 七、未覆盖项

- **不重跑任何已核测试**（111 JVM、五条 MuMu 功能旅程、恢复批次）；本轮只读。
- `persistCurrentProgress` **无既有直测**，4.7 的"需新增"仅为需求清单，未实现。
- 4.5 的"潜在缺陷"（ci/co 元组不自洽）与"另记一处 D 内部不一致"（`pagedSource.chapterIndexFor` 语义）**均未在设备上验证**；标注为**推测/未验证**，未修。
- 未核对 `.codex/`、根文档、桌面端范围；未全量复审 semantic12 其余 10 个文件（由其他窗口负责 Profile 三文件与其余七文件）。
- 不给全产品百分比；本报告是集成决策输入，不构成验收结论。

## 八、交付状态与下一步

**已交付待 Codex 核查。**

- 两文件**全部 hunk** 已分类（Actions 7 / Effects 7），**必要调用方已覆盖**（4.4 + 4.6），**推荐方案完整保全两侧行为**：Actions 真合并（paged → markdown(候选) → livePlainOffset(D)），Effects 直接采纳候选（严格超集，D 无独有语义）。
- **无 HOLD**：本轮未发现真正不能消解的冲突。
- 每个风险已标状态：4.5 的 ci/co 元组不自洽 = **已确认的契约偏离**（当前被消费端屏蔽）；`pagedSource.chapterIndexFor` 注释与代码不符 = **未验证**。
- 下一步建议：① Codex 复核本报告与 `hunk-classification.json`；② 由单一集成 owner 按 4.6 顺序执行（含纳入 `ReaderAnnotationNavigation.kt`、保留四个 181 文件、Actions 保持 LF）；③ 合流后按 4.7 最小回归矩阵验证，其中 `persistCurrentProgress` 三项直测属**新增**需求。
- 本任务就此停止，不实施、不授权、不提交。
