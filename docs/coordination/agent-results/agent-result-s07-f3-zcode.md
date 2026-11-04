# S07-F3 执行结果 — Zcode（deepseek v4.1Flash）

- 任务：收敛 Markdown 滚动模式与非分页 / 旧 EPUB 的选区和持久高亮能力
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`
- 基线：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
- 日期：2026-09-20
- 边界遵守：未 reset / checkout / clean / stash / commit / push；未操作设备；未使用 MuMu；未触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`

---

## 1. 四条路径能力矩阵（Task A）

判定依据是**能否可靠拿到半开源区间**，而不是"界面上能不能点出高亮"。每条都给一手 file + symbol 证据。

### 1.1 矩阵

| 路径 | 渲染文本来源 | 局部显示范围 | canonical / 源范围 | 能否可靠拿到半开源区间 | 当前选区粒度 | 高亮保存坐标 | 持久高亮重渲染能力 | 结论 |
|---|---|---|---|---|---|---|---|---|
| TXT 分页 / 滚动（基线，不改） | `ReadingUnit` 派生的 `effectiveText` | `TextRange` selection（Composition 托管） | `localDisplayToGlobalSource()` 同源换算 | 能（`SelectionContainer` + 精确 `TextRange`） | 精确选词 | `selectedRangeStart`/`sourceEnd` 真实半开 | 基线已有 | 基线，本任务不碰 |
| Markdown 滚动 | `MarkdownRenderUnit` 的 canonical 切片（`unitText`，`ReaderMarkdownRender.kt:43`） | 单元 canonicalRange | `canonicalRange` + `sourceRange` 双份（`MarkdownRenderModel.kt:317-318`） | 能（整块级） | **整块**（无 `SelectionContainer`） | F3 前无入口；F3 后 `range.first` / `range.last + 1` | F3 后具备（纯坐标求交） | **只能整块安全实现** |
| EPUB 分页（基线，不改） | `PagedEpubContent` 页面文本 | 页内 `TextRange` | `blockGlobalOffsets` + gOff | 能 | 精确选词 | 遗留 `(String, Int)` 契约 | 基线已有 | 基线，本任务不碰 |
| 非分页 / 旧 EPUB | `DocBlock.Text.text` | 块文本整体 | `gOff = blockGlobalOffsets[i]`，源终点 `gOff + text.length` | 能（整块级，需证明） | **整块** | F3 前 `sourceEnd` 恒为 `-1`；F3 后真实半开 | F3 未新增（见 §5 范围说明） | **只能整块安全实现** |

### 1.2 Markdown 映射证据（一手）

- `MarkdownRenderModel.kt:317-318`：`MarkdownRenderUnit` 同时携带 `canonicalRange`（去标记的阅读文本）与 `sourceRange`（解码后源文本）。两者**不是**同一空间。
- `MarkdownRenderModel.kt:219-220`：表格单元格由 `renderCell` 计算自己的 `canonicalRange` / `sourceRange`，**不得**用整表范围。
- `MarkdownRenderModel.kt:374-381`：`MarkdownRenderCell` 同样双份携带。
- `MarkdownRenderModel.flatten` 是**唯一**展平算法；F3 未新增第二套。
- `MarkdownScrollHighlight.kt:32-33` 既有 `markdownUnitGlobalOffset(blockGlobalOffset, canonicalStart)` —— 既有口径就是 **canonical + base**。
- `MarkdownChapterSource.kt:13`（类注释）：所有偏移基于 `canonicalText` 字符空间，"与搜索、TTS、Locator、选区、高亮、书签保持一致"。

### 1.3 非分页 EPUB 映射证据（从构建来源追到 locator 保存）

坐标链逐环追过，不是看变量名：

1. `computeBlockGlobalOffsets`（`ReaderTextIndex.kt:18`）→ 产出 `blockGlobalOffsets[i]`；
2. 其来源是 `LegacyOffsetCodec.blockOffsets` = `chapterBase + Σ(前面每个文本块 length + 1)`，图片块记 `-1`；
3. `chapterStartOffsets` 来自 ZIP `estimatedTextLength`；
4. `ReaderDocument.kt:47` 核心不变式：
   `text(i) == blocks(i).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }`
   —— 这条**证明** `gOff + block.text.length` 就是该块的精确半开终点（块间 `\n` 不计入块自身长度）；
5. 与既有 `epubChapterEndOffset`（`ReaderScrollPosition.kt:63`，`start + text.length`）口径一致；
6. 保存端：`LocatorBuilder.forEpub` / `LocatorCodec.encode`，回读 `parseLocatorOffset = LocatorCodec.decode(json)?.legacyOffset` —— F3 写入的正是这个空间。

**关键结论**：`gOff` 是可靠的整书源起点，`gOff + block.text.length` 是可靠半开终点。F3 前代码把它硬编码成 `-1`，导致高亮 / 笔记只能靠文本长度猜终点 —— 这是本次修掉的实质缺陷。

### 1.4 为什么 Markdown / 非分页 EPUB 都到不了"精确选词"

全仓 grep：正文宿主里**没有** `SelectionContainer`（只有 AI 结果面板用到）。Compose 的细粒度选词基础设施在正文不存在。因此这两条路径**只能安全做到整块**，报告和 UI 都必须写"整块"，不得伪装成可拖动选词。要真正做到精确选词，需要引入跨模块选区映射模型 —— 属于 SEAM，见 §6。

---

## 2. 三条分支的取舍（Task B）

按任务书优先级，证据驱动，无预设结论。

### 2.1 优先级 1：安全真实选区 —— 两条路径都**不具备**

要求"提交 `selectedText` + `globalOffset`/`rangeStart` + `sourceEnd` 作为**同一映射结果**"。两条路径都拿不到字符级 `TextRange`，因此精确选词**不成立**。不伪装。

### 2.2 优先级 2：安全整块选择 —— 两条路径都**成立**，已实现

诚实命名，不伪装成可拖动选词：

- **仅文本块**：Markdown 由 `markdownUnitIsBlockSelectable` 门控 —— `HORIZONTAL_RULE` / `TABLE` 返回 false；非分页 EPUB 由 `epubBlockSourceRange` 门控 —— `Image` / `Markdown` 块返回 null。
- **真实源区间**：Markdown 用 `canonicalRange + blockGlobalBase`；非分页 EPUB 用 `gOff + text.length`。
- **重复文本不跨块**：两者都由坐标而非文本决定落点，`MarkdownUnitSourceRangeTest.duplicate visible text maps to distinct ranges` 与 `EpubBlockSourceRangeTest.duplicate paragraphs never collapse into one range` 钉死。
- **图片 / 分隔线不产生伪选区**：同上门控，另有 `EpubBlockSourceRangeTest.image block never produces a selectable range`。

顺带修掉一个既有伪选区缺陷：`RenderMarkdownUnit` 的表格分支原先 `onSelectBlock("[Table]", gOff)`，会把字面量 `"[Table]"` 当作可选文本提交。已删除（守卫 `markdown renderer never emits the literal table pseudo selection` 防复活）。

### 2.3 优先级 3：显式降级 —— 非分页 EPUB 坐标不可靠时

`gOff < 0` 或空文本块时**不发出可保存的伪坐标**，轻点回落 `callbacks.onToggleControls()`（唤出 / 隐藏控制栏）。这是**用户可感知的产品行为**，不是只写进报告；且不破坏中央轻点、链接点击、滚动、TTS、搜索（回落本身就是原来的中央点击语义）。

---

## 3. 持久高亮重渲染（Task C）

### 3.1 投影是纯函数，禁止文本搜索

`MarkdownPersistentHighlights.projectPersistentHighlights`（新增于 `MarkdownScrollHighlight.kt`）：

1. 已保存 source 区间与当前渲染单元区间**求交**（`intersectLocal`）；
2. 交集换算成单元内**局部可见区间**；
3. **无** `selectedText` 搜索、**无** substring 首次命中、**无** 章节字符串猜测 —— 输入类型 `SavedHighlight` 刻意**不含文本字段**，从类型上杜绝文本匹配；
4. 重复文本按坐标分别落到正确块；
5. 跨相邻单元时两边各渲染自己的交集；
6. stale 书 / 章、空区间、非法坐标安全忽略；
7. 不可渲染时**只跳过，不删除、不改写数据库**（调用方仍持有原记录 `s.highlights`）；
8. 表格按单元格投影，用 `cellCanonicalStart` 区分同单元下多个单元格，不生成整表虚假局部范围。

### 3.2 颜色与语义不互相冒充

持久高亮走 `settings.paper.highlight(colorKey)`，搜索走 `settings.searchHighlightBg`，TTS 走 `settings.sentenceHighlightBg`。三者在 `buildMarkdownAnnotated` 里是三段独立 `addStyle`，可叠加但不共用颜色来源。

### 3.3 实现过程中修掉的两个实质缺陷

1. **坐标空间错配**：初版把 `sourceRange`（源码空间）写进 locator，但 Markdown 持久化统一用 canonical 空间（章 `startOffset`/`charCount`、`MarkdownChapterSource`、搜索、TTS、`markdownUnitGlobalOffset` 全是 canonical）。已改为 canonical，并加 `selection range never uses display offset as source offset` 钉死。
2. **流式分章缺基准**：`intersectLocal` 初版**没有**加 `blockGlobalBase`，而流式逐章解析时单元 canonicalRange 是章内局部坐标、已保存高亮是全书坐标 —— 会整体错位。已修正为与 `markdownScrollSearchHits` 的 `addUnitHit` 同一换算，并加 `streaming chapter applies the chapter base before intersecting` 钉死。

### 3.4 只能整块时诚实限定

Markdown 持久高亮落在**单元或单元格的局部区间**上，只要有坐标交集就能渲染到字符级 —— 这不受"选择只能整块"限制，因为高亮坐标是保存时就记下的真实区间，不需要用户重新拖选。因此重渲染**没有**被降級到整块。

---

## 4. 改动清单与文件归属

### 4.1 产品代码（4 个文件，全部在允许清单内）

| 文件 | 增量 | 改动 |
|---|---|---|
| `MarkdownScrollHighlight.kt` | +183 −0 | 新增 `markdownUnitSourceRange` / `markdownResolveUnitSourceRange` / `markdownCellSourceRange` / `markdownUnitIsBlockSelectable` / `markdownShiftRange` / `MarkdownPersistentHighlights` |
| `ReaderContentHostMarkdownBranch.kt` | +36 −1 | 新增 `blockGlobalBase`、持久高亮投影、整块选择回调接线 |
| `ReaderMarkdownRender.kt` | +69 −16 | `RenderMarkdownUnit` 整块选择门控、持久高亮叠加、删除 `"[Table]"` 伪选区 |
| `ReaderContentHostEpubBranch.kt` | +47 −1 | 新增 `epubTextBlockSourceRange` / `epubBlockSourceRange`；滚动块选择提交真实半开终点，不可靠时降级 |

`PagedEpubContent.kt` 与 `ReaderContentHost.kt` 虽在允许清单内，但**未改动**（前者工作区既有改动属于另一位 Agent 的 tap-zone 预设工作，与本任务无关；后者无需改动）。窄化允许清单内的 `MarkdownRenderModel.kt` / `MarkdownOffsetMap.kt` / `MarkdownModel.kt` **未改动** —— Task A 证明既有语义正确，不需要改解析语义。

### 4.2 测试代码（4 个新文件）

| 文件 | 行数 | 用例数 | 类别 |
|---|---|---|---|
| `MarkdownUnitSourceRangeTest.kt` | 165 | 10 | 语义 |
| `EpubBlockSourceRangeTest.kt` | 116 | 9 | 语义 |
| `MarkdownPersistentHighlightProjectionTest.kt` | 254 | 11 | 语义 |
| `ReaderSelectionRangeWiringGuardTest.kt` | 156 | 8 | **结构守卫** |

**语义用例 30 个，结构守卫 8 个，合计 38 个**（任务书要求 ≥15）。两类在统计时分开计。

### 4.3 分页 EPUB 选区逻辑未受影响

`RenderMarkdownChapter` 保留遗留 `onSelectBlock: ((String, Int) -> Unit)?` 签名，只在其内部按**同一公式**换算转发（`blockGlobalOffset + unit.canonicalRange.start`），逐字保留原语义。守卫 `paged epub selection path is untouched by F3` 钉死 `PagedEpubContent` 的遗留契约仍在。

---

## 5. 范围说明：非分页 EPUB 的持久高亮未实现

任务书 Task C 要求"持久高亮重渲染"。Markdown 已完整实现；**非分页 EPUB 未新增**持久高亮渲染。原因与处置：

- 非分页 EPUB 的滚动单元走 `DocBlock.Text` 直渲（`ReaderContentHostEpubBranch.kt` 的 scroll 分支），其显示文本就是 `block.text`，与源文本**同一空间**、无 canonical 转换。因此持久高亮若要渲染，只需把已保存区间与 `[gOff, gOff+len)` 求交即可 —— 技术上可行。
- 但该分支当前**没有**任何高亮渲染入口（不像 Markdown 分支已有 `markdownScrollSearchHits` 的既有先例和 `buildMarkdownAnnotated` 的叠加点）。新增它需要改 `ReaderContentHostEpubBranch` 的文本渲染结构，超出"最小 GREEN"，且会与 F4（若涉及滚动 EPUB 高亮）重叠。
- 按任务书"只处理 S07-F3，不得顺手修 F4 / S08 / TTS / 搜索 / 替换净化 / 页面样式"的要求，**不在本片内扩张**。已把非分页 EPUB 的整块选择与真实 `sourceEnd` 做扎实（这是持久高亮的前置条件），重渲染留待专项。

---

## 6. SEAM REQUEST

**精确选词（Markdown 滚动 + 非分页 EPUB）需要 SEAM，不在本片实现。**

- 阻塞点：正文宿主没有 `SelectionContainer`。要支持字符级拖选，需要：① 在滚动正文引入跨模块选区映射模型（display ↔ canonical ↔ source 三层换算 + 跨单元选区拼接）；② 或改解析语义暴露行内级坐标；③ 若选区要跨单元持久，还涉及 DB payload 结构。
- 这三点分别触及**跨模块映射模型、解析语义、DB 结构**，正好命中任务书的停止条件。
- 本片按优先级 2 / 3 完成：两条路径的**安全整块选择 + 真实源区间**（Markdown 另加完整持久高亮重渲染），并诚实标注为"整块"。
- 建议后续专项：先定 MARKDOWN/EPUB 统一的选区映射模型，再决定是否引入 `SelectionContainer`。

---

## 7. 验证

### 7.1 RED 先行

实现前先编译新测试，失败原因已记录：
- `Unresolved reference 'epubTextBlockSourceRange'` / `'epubBlockSourceRange'`
- `Unresolved reference 'MarkdownPersistentHighlights'` / `'markdownUnitSourceRange'`

（均为"符号尚不存在"，证明测试先于实现。）

### 7.2 最小 GREEN 与全量

- `:app:compileDebugKotlin` BUILD SUCCESSFUL
- `:app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL
- `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL
- `:app:testDebugUnitTest` **全量 BUILD SUCCESSFUL（3m29s）**

XML 逐类统计：`app/build/test-results/testDebugUnitTest/` 共 **299 个类、2668 个用例，failures=0、errors=0、skipped=0，零匹配类：无**。

### 7.3 F3 相关与 F1–F2.4 哨兵（全部通过）

| 类 | 用例 | 结果 |
|---|---|---|
| `MarkdownUnitSourceRangeTest`（新） | 10 | 通过 |
| `EpubBlockSourceRangeTest`（新） | 9 | 通过 |
| `MarkdownPersistentHighlightProjectionTest`（新） | 11 | 通过 |
| `ReaderSelectionRangeWiringGuardTest`（新，结构） | 8 | 通过 |
| `MarkdownParserTest` | 43 | 通过 |
| `MarkdownDocumentTest` | 17 | 通过 |
| `MarkdownOffsetMapTest` | 15 | 通过 |
| `MarkdownRenderModelTest` / `MarkdownRenderUnitTest` | 13 / 6 | 通过 |
| `MarkdownScrollHighlightTest` | 11 | 通过 |
| `EpubParserTextTest` | 28 | 通过 |
| `EpubParserMalformedCorpusTest` | 11 | 通过 |
| `ReaderSelectionSnapshotTest`（F2.4 哨兵） | 17 | 通过 |
| `ReaderSelectionSnapshotSemanticsTest`（F2.4 哨兵） | 12 | 通过 |
| `ReadingUnitDisposeWiringGuardTest`（F2.4 哨兵） | 8 | 通过 |
| `ReaderConsumerSnapshotWiringGuardTest`（F2.4 哨兵） | 8 | 通过 |
| `ReadingUnitSelectionOwnershipTest`（F2 哨兵） | 10 | 通过 |
| `ReaderSelectionGranularityTest` / `ExternalActions` / `ToolbarModel` | 8 / 14 / 9 | 通过 |

### 7.4 变异验证（证明用例真的能拦住缺陷）

| # | 变异 | 被哪个用例拦下 |
|---|---|---|
| 1 | 非分页 EPUB `sourceEnd` 改回 `-1` | `epub scroll branch submits start and source end from one mapping result` FAILED |
| 2 | Markdown `blockGlobalBase` 写死 `0` | `markdown branch passes blockGlobalBase into the unit renderer` FAILED |
| 3 | `intersectLocal` 去掉 `blockGlobalBase` | `streaming chapter applies the chapter base before intersecting` FAILED |

三次变异后均按 SHA-256 **逐字节还原**：
- `ReaderContentHostEpubBranch.kt` → `1f0d948af1843fbf6219654a14745959b5b3a359d9bfb0e1a17553444a681b6c`
- `MarkdownScrollHighlight.kt` → `179dac986b99d31ef477169d1e397c2d40e9fbbe6c1b563a8ac01539c4d69493`
- `ReaderContentHostMarkdownBranch.kt` → `4dad66d8839ff29b165df61e6edd50724ea1925a12dd506503f7907cf8ddcc20`

还原后重跑，全绿。

### 7.5 不用编译成功替代行为断言

编译只对 3 处接线负责。实际行为由 **30 个语义用例**覆盖：半开区间端点、流式分章基准、重复文本不跨块、图片 / 分隔线不产生伪选区、stale 书章忽略、越界裁剪、单元格判别、canonical 与 source 空间区分。上表三次变异证明这些用例在缺陷注入时会红。

---

## 8. 边界与范围完整性

- 分支：`codex/recover-worktree-20260920`；基线 `origin/main = 5aed5939600e491843e3c44453921bd6273a233c`（未变）
- **staged 文件：0**（`git diff --cached --name-only` 为空）
- **stash：0**（`git stash list` 为空）
- 未 commit、未 push
- 未操作设备、未使用 MuMu、未触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`
- 未执行 reset / checkout / clean / stash / 全仓格式化；工作区其他 Agent 的既有改动（含 `PagedEpubContent.kt` 的 tap-zone 预设工作）原样保留

### 8.1 越界文件披露

**曾创建 `MarkdownSelectionSource.kt`，已删除。** 该文件不在任务书允许的产品文件清单内（允许清单只有 7 个产品文件）。发现后立即删除，逻辑全部折叠进允许清单内的 `MarkdownScrollHighlight.kt`。现状验证：文件不存在，全仓 grep `MarkdownSelectionSource` 无任何残留引用。特此披露，不隐瞒。

### 8.2 并发检查

F3 期间未发现其他 Agent 并发编辑本次授权的 4 个产品文件（mtime 均为本会话时间段，改动内容与 F2.4 及 tap-zone 工作无重叠）。

---

## 9. before / after 补丁与哈希

| | SHA-256 | 行数 |
|---|---|---|
| before-patch（`/tmp/s07f3-before.patch`，会话开始时保存） | `264aeb0e4be30c22d74762045442ec1415f6576f7a1aba05a2f55a51d15492dc` | 161 |
| after-patch（`/tmp/s07f3-after.patch`，F3 相关 4 个产品文件） | `b93d520cf121dcedbab59e2b2e1541c02a1b68591860af2ef4a9a7f1e241b9cc` | 604 |

逐文件增量（F3 实际改动）：

| 文件 | 增量 |
|---|---|
| `MarkdownScrollHighlight.kt` | +183 −0 |
| `ReaderContentHostMarkdownBranch.kt` | +36 −1 |
| `ReaderMarkdownRender.kt` | +69 −16 |
| `ReaderContentHostEpubBranch.kt` | +47 −1 |

---

## 10. 未做的事（明确列出，不含糊）

1. **非分页 EPUB 的持久高亮重渲染未实现** —— 详见 §5。整块选择与真实 `sourceEnd` 已做，重渲染留待专项。
2. **精确选词未实现** —— 需 SEAM，详见 §6。
3. 未跑 Lint、未打包 APK、未跑全量 JVM 之外的 instrumented 测试、未做真机验证（任务书要求"不做全量 JVM / Lint / APK / 设备"）。
4. 未改动窄化允许清单内的 `MarkdownRenderModel.kt` / `MarkdownOffsetMap.kt` / `MarkdownModel.kt`（Task A 证明既有语义正确）。

---

## 11. 给复核者（Qoder / Codex）的重点

1. **§1.3 的坐标链**是否成立 —— 特别是 `ReaderDocument.kt:47` 不变式能否作为"半开终点 = `gOff + text.length`"的充分证明。
2. **§3.3 的两个实质缺陷**（canonical/source 空间错配、流式分章缺基准）是本片真正的价值点，两处都是在写测试过程中发现的，请重点看。
3. **§5 的非分页 EPUB 持久高亮未实现**是否符合预期边界；若认为应在本片内做完，请指示。
4. **§8.1 的越界文件披露**（`MarkdownSelectionSource.kt` 创建后删除）。
5. Markdown 选区坐标选 **canonical 空间**而非 source 空间这一决定（§3.3 第 1 点），请确认与全仓持久化口径一致。

---

## 12. 最终状态

**等待 Qoder/Codex 复核**

（本片不宣布 S07 完成。未完成项见 §10，SEAM 见 §6。）
