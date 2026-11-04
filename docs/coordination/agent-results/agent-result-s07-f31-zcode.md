# S07-F3.1 结果报告（Zcode / deepseek v4.1Flash）

- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`
- 固定基线：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
- 上游复核：`docs/coordination/reviews/undated/agent-review-s07-f3-qoder.md`（结论 FAIL，P1-1 阻断）

**本轮立场：接受 Qoder 的 FAIL 事实。** F3 的 397 项绿灯与结构守卫不能反驳 P1-1 ——
守卫当时只断言 `onSelect(...)` 文本里**含有** `range.first`，从不检查它落在哪个参数槽，
因此缺陷在全绿状态下完整存在。

---

## 1. P1 根因与正确的参数契约

**根因。** `ReaderContentHost.kt:145` 的签名是
`onSelect: (text: String, globalOffset: Int, rangeStart: Int, sourceEnd: Int) -> Unit`。
F3 的 Markdown 分支写作：

```kotlin
callbacks.onSelect(text, range.first, -1, range.last + 1)   // ← 错槽
```

`computeLocatorJson`（`ReaderProgressActions.kt:31-42`）在 `epubBook == null` 时**只读
`selectedRangeStart`**：

```kotlin
epubBook != null && selectedGlobalOffset >= 0 -> LocatorBuilder.forEpub(...)
epubBook == null && selectedRangeStart >= 0   -> LocatorBuilder.forPlain(...)
else -> null
```

Markdown 把 canonical 起点塞进了 `globalOffset` 槽，`rangeStart` 恒为 `-1` ⇒ 命中
`else` ⇒ **locator 恒为 null** ⇒ 高亮/笔记/灵感三个消费者全部无锚点，持久高亮端到端不可达。

**正确契约（四个槽位，缺一不可）：**

| 槽位 | 字段 | Markdown 值 | 语义 |
|------|------|-------------|------|
| 1 | `text` | 整块可见 canonical 文本 | 写入 locator 的 excerpt 指纹 |
| 2 | `globalOffset` | **-1** | EPUB `forEpub` 起点槽；Markdown 不使用 |
| 3 | `rangeStart` | canonical 全书起点 | `forPlain` 唯一读取的槽 |
| 4 | `sourceEnd` | canonical 全书半开终点 | 供 `currentSelectionSourceRange` 锚定 |

生产接线等价于 `callbacks.onSelect(text, -1, range.first, range.last + 1)`。

**修复方式（不是改一行 + 加字符串断言）。** 新增生产唯一入口，让槽位含义**由类型固定**，
而不是靠调用方记顺序：

```kotlin
data class MarkdownBlockSelection(
    val text: String, val globalOffset: Int, val rangeStart: Int, val sourceEnd: Int)

internal fun markdownBlockSelectionArgs(
    text: String, unit: MarkdownRenderUnit, blockGlobalBase: Int,
): MarkdownBlockSelection?
```

`ReaderContentHostMarkdownBranch.kt` 的 `onSelectBlock` 现在只做一件事：调用它、判空、转发。
Jvm 测试直接调用**生产代码实际使用的这个函数**，把四字段喂给真实 `computeLocatorJson`，
再用 `LocatorCodec.decode` 解码回来。

---

## 2. 端到端 locator 测试证据（`MarkdownBlockSelectionLocatorTest`，10 项）

| 用例 | 断言要点 |
|------|----------|
| `whole document markdown selection fills the rangeStart slot not globalOffset` | `globalOffset == -1`、`rangeStart == 0`、`sourceEnd == 文本长度` |
| `streaming markdown selection adds chapterBase to rangeStart and sourceEnd` | base=1000 ⇒ `rangeStart == 1000`，`sourceEnd == 1000+len` |
| `selection result produces a decodable plain locator` | 经**真实** `computeLocatorJson` ⇒ 非 null；`LocatorCodec.decode().legacyOffset == rangeStart` |
| `streaming selection locator carries the chapter based offset` | 解码得 1000 |
| `locator text fingerprint matches the selected text` | 解码 `fp` == `LocatorCodec.fingerprint(所选文字)` |
| `snapshot built from the selection keeps all four fields consistent` | `ReaderSelectionSnapshot` 四字段与 args 逐一相等 |
| `highlight note and inspiration share one non null locator` | 三个消费者共用同一快照，locator 均非 null 且 offset 一致 |
| `swapping the canonical start back into globalOffset yields no locator` | **反向钉子**：复现 F3 错槽形态 ⇒ 断言 locator 为 null |
| `epub present still routes to the globalOffset slot` | 反向钉子：非 EPUB 文档不得走 `globalOffset` 槽 |
| `non selectable unit yields null selection args` | 分隔线产出 null |

> 写测试时踩到一个自己的坑并修正：`locator 必须携带所选文字的指纹` 最初断言
> `json.contains("原文")`。这是**想当然** —— `LocatorCodec.encode` 只写 SHA-256 前 8 字节的
> hex（`"fp":"<hex>"`），原文根本不落盘，该断言恒假。已改为比对重算指纹。

---

## 3. 真实 stale-chapter 修复（`MarkdownHighlightChapterSpanTest`，10 项）

**F3 的伪判据。** 调用方把每条 `HighlightEntity` 包成 `chapterIndex = 当前 s.chapterIndex`，
随后又用「当前章号」过滤它 —— 该谓词在生产路径**恒成立**，防串章实际只靠几何不相交。
测试传入产品永不产生的 `chapterIndex = 7` 制造假安全感。

**修复（三条任务要求逐条落实）：**

1. **不再凭空捏造记录的章身份** —— `SavedHighlight` 删除 `chapterIndex` 字段
   （反射断言 `"chapterIndex" !in declaredFields` 钉死）。理由：记录里没有真实章身份可填，
   `LocatorBuilder.forPlain` 恒写 `ci = 0`。
2. **若存在真实身份则从记录推导** —— 已核实不存在（`forPlain` 恒 0），故不引入。
3. **删除伪章过滤，改用几何判据** —— 新增 `chapterSpan: IntRange` 参数
   （当前章在全书 canonical 空间的半开区间 = `chapterBase until chapterBase + chapterLength`），
   由区间求交决定是否投影；上游 `observeHighlightsByBook(bookId)` 的 book_id 查询仍是第一道过滤。
4. **不改变 DB schema** —— 本轮零 schema 改动，零迁移。

**「高亮完全在当前章 span 之外不投影」必须真反例（非手工章号）：**

| 用例 | 结果 |
|------|------|
| `a highlight entirely before the current chapter span is not projected` | 绿 |
| `a highlight entirely after the current chapter span is not projected` | 绿 |
| `a highlight touching only the span boundary is not projected` | 绿（两侧边界） |
| `a highlight straddling the span start renders only the inside part` | 绿（只渲染章内 `[1000,1004)`） |
| `a highlight inside a unit but outside a narrow chapter span is not projected` | 绿（见下方 mutation 记录） |

### mutation 验证暴露的真实缺口（必须记录）

删除 `chapterSpan` 过滤整行后，**前四条「章外」用例全部仍然绿** —— 它们其实被
`intersectLocal` 的单元区间求交顺带挡住了，证明不了 `chapterSpan` 这个判据本身。
我据此补了唯一能区分的形态：高亮**落在某个渲染单元区间内**、但在**当前章 span 之外**
（`a highlight inside a unit but outside a narrow chapter span is not projected`）。
重跑 mutation ⇒ 该用例 FAIL ⇒ 判据现在真的被锁住。

同时补了显式降级：`an unknown chapter span degrades to unit intersection instead of dropping all`
—— 章长度未知（`charCount = 0` ⇒ 空 span）时**不**按 span 过滤，否则会静默吞掉全部高亮；
退化为「仅按渲染单元求交」并在代码注释中记录。

---

## 4. EPUB 持久高亮实现（`EpubBlockPersistentHighlightTest`，15 项）

**坐标空间已核实同一且可往返**：`epubTextBlockSourceRange` 用 `gOff + block.text.length`
（`computeBlockGlobalOffsets` → `LegacyOffsetCodec.blockOffsets` = `chapterBase + Σ(len+1)`），
而 `LocatorBuilder.forEpub` / `LocatorCodec` 保存与回读正是同一空间 ⇒ **无需 SEAM REQUEST**，
按纯函数实现。

```kotlin
internal fun epubBlockHighlightLocalRange(
    blockStart: Int, blockLength: Int, absStart: Int, absLength: Int,
): IntRange?
```

- 输入只有坐标，**没有文本字段** ⇒ 从结构上不可能做 selectedText 搜索 / indexOf / 首次匹配；
- 重复段落靠各自 `gOff` 区分，绝不串块；
- 跨两个相邻块 ⇒ 两块各自调用一次，各渲染**自己的**交集；
- `gOff < 0`（坐标未知）/ 空文本 / 负长度 / 零长度 / 非法坐标一律 null（安全忽略）；
- `Long` 中间量 ⇒ `Int.MAX_VALUE` 附近不溢出；
- 图片 / Markdown 等非文本块在上层 `epubBlockSourceRange` 已被挡掉（恒 null），本层再兜 `blockLength <= 0`。

**渲染接线**（`ReaderContentHostEpubBranch.kt`）：在 `buildSentenceHighlighted` 的**结果之上**
叠一层 `AnnotatedString`，复用**纸张高亮色** `settings.paper.highlight(key)`，
**不**复用 `searchHighlightBg`（否则与搜索命中语义互相冒充）。

**既有能力未破坏**：`BuildSentenceHighlightedTest`（6 项）全绿；TTS 句高亮、搜索命中、
`bringIntoViewRequester`、`clickable` 整块选择三条链路代码路径均未改动。
**未修改 / 删除 / 迁移任何 DB 高亮记录**（本层只跳过，不写回）。

> 边界说明：`buildSentenceHighlighted` 位于 `tts/ReaderTtsBar.kt`，**不在**本轮允许清单内，
> 故未为其增加 `persistentHighlights` 参数，改为在其结果之上叠加。这是刻意的边界遵守，
> 不是实现偷懒 —— 代价是每次多一次 `addStyle` 遍历，无语义差异。

---

## 5. 单处纠错可达性证明与裁决（`EpubSingleCorrectionReachabilityTest`，8 项）

F3 把非分页 EPUB 的 `sourceEnd` 由 -1 改为有效值，`currentSelectionSourceRange`
因此可能返回区间，理论上 `SaveSingleCorrection` 从 `NotAnchorable` 变为可执行。四条独立证明：

1. **非分页 EPUB 能否看到纠错入口** —— 否。
   `EpubChapterSource.replacementCoordinateSpace = ESTIMATED` ⇒
   `PagedReplacementAvailability.ESTIMATED_COORDINATES` ⇒
   `readerReplacementCapability(...) = Unavailable`。
2. **replacementCapability 是否已拦截不支持的 EPUB 模式** —— 是。
   `Unavailable` ⇒ `canCreateReplaceRule = false` ⇒ `selectionToolbarActions` 摘掉 `replace` 槽位。
3. **即使从其它入口进入，ViewModel 是否拒绝** —— 是，双重门控：
   `currentSelectionSourceRange` 返回 null ⇒ `RuleMutationResult.NotAnchorable`；
   且 `draft.findText != selectedText` 时同样拒绝。
4. **旧 EPUB 混合量纲能否安全改写源文本** —— **不能安全改写**（章起点来自 ZIP 字节估算）。
   但这一点**无需在 F3.1 处理**：前两条已证明该入口静态不可达。

**裁决：静态不可达 ⇒ 按「补状态测试锁定前提，不改产品行为」执行。**
已加 8 项纯函数/状态测试锁定上述前提（含 `ESTIMATED_COORDINATES` ⇒ `Unavailable`、
`replace` 槽位消失、`sourceEnd = -1` 仍返回 null、start>end 返回 null）。
**未回退 `sourceEnd`**，未改动任何产品行为。

---

## 6. canonical / source 命名与死代码清理（任务 E）

| 项 | 处理 |
|----|------|
| E-1 命名 | `markdownUnitSourceRange` → **`markdownUnitCanonicalRange`**；`markdownShiftRange` → **`markdownShiftCanonicalRange`**。KDoc 明确写入：返回的是 canonical 空间，与 `MarkdownRenderUnit.sourceRange`（解码源文本空间，被 `MarkdownOffsetMap` 消费）**不是同一空间**，勿混写 |
| E-2 死代码 | `markdownResolveUnitSourceRange`：只有测试调用 ⇒ **已删除**（测试改用 `requireNotNull(markdownUnitCanonicalRange(...))`）。`markdownCellSourceRange`/`markdownCellCanonicalRange`：无生产调用方（本轮表格单元格不可整块选择）⇒ **已删除**，不保留「只有测试在用」的假能力。其测试改为断言**生产真实行为**：整表不可选 + 单元格 canonical 起点可作为高亮落点判别符 |
| E-3 必填基准 | `RenderMarkdownUnit` 的 `blockGlobalBase` 由 `Int = 0` 改为 **`Int`（必填）**。默认值 0 正是流式分章整体静默错位的来源（不报错、不崩溃、行为用例全绿）。`RenderMarkdownChapter` 遗留路径显式传 0（其块坐标已是全书全局） |
| E-4 注释 | 删除「没有 `SelectionContainer` 所以精确选词不可能」的过强论断。改写为：整块是**本切片的产品与实现边界**；同仓 TXT 滚动正文用 `BasicTextField` 选区已做到字符级，证明没有 `SelectionContainer` 也能实现；精确选词需要专门的输入层、选区手柄与 canonical↔source 映射，属后续工作 |
| E-5 三基准接口 | **未重构**（`blockGlobalOffset` / `chapterBase` / `blockGlobalBase` 并存），按任务书留给 F4 |

---

## 7. RED/GREEN 命令、退出码与 XML 统计

**RED（先红）** —— 测试先于实现落地，`./gradlew :app:compileDebugUnitTestKotlin` 退出码 **1**：

```
e: Unresolved reference 'markdownBlockSelectionArgs'
e: Unresolved reference 'epubBlockHighlightLocalRange'
e: Cannot infer type for this parameter（MarkdownBlockSelectionLocatorTest:32）
```

**GREEN（最小改动转绿）**：实现上述生产函数与接线后，退出码 **0**。

**F3 的 Markdown / EPUB 测试 + F1–F2.4 哨兵（同一批 16 个类）：**

```
$ ./gradlew :app:testDebugUnitTest --tests "...MarkdownScrollHighlightTest" \
    --tests "...MarkdownUnitSourceRangeTest" --tests "...EpubBlockSourceRangeTest" \
    --tests "...MarkdownPersistentHighlightProjectionTest" \
    --tests "...MarkdownBlockSelectionLocatorTest" \
    --tests "...MarkdownHighlightChapterSpanTest" \
    --tests "...EpubBlockPersistentHighlightTest" \
    --tests "...ReaderSelectionRangeWiringGuardTest" \
    --tests "...EpubSingleCorrectionReachabilityTest" \
    --tests "...ReaderConsumerSnapshotWiringGuardTest" \
    --tests "...ReaderSelectionSnapshotSemanticsTest" \
    --tests "...ReaderNoteSnapshotSaveTest" --tests "...ReaderProgressSnapshotTest" \
    --tests "...ReaderReplacementCapabilityTest" \
    --tests "...ui.viewmodel.ReaderSelectionSnapshotTest" \
    --tests "...tts.BuildSentenceHighlightedTest"
BUILD SUCCESSFUL
```

| 类 | tests | fail | err | skip |
|----|------:|-----:|----:|-----:|
| EpubBlockPersistentHighlightTest | 15 | 0 | 0 | 0 |
| EpubBlockSourceRangeTest | 9 | 0 | 0 | 0 |
| EpubSingleCorrectionReachabilityTest | 8 | 0 | 0 | 0 |
| MarkdownBlockSelectionLocatorTest | 10 | 0 | 0 | 0 |
| MarkdownHighlightChapterSpanTest | 10 | 0 | 0 | 0 |
| MarkdownPersistentHighlightProjectionTest | 11 | 0 | 0 | 0 |
| MarkdownScrollHighlightTest | 11 | 0 | 0 | 0 |
| MarkdownUnitSourceRangeTest | 10 | 0 | 0 | 0 |
| ReaderConsumerSnapshotWiringGuardTest | 9 | 0 | 0 | 0 |
| ReaderNoteSnapshotSaveTest | 6 | 0 | 0 | 0 |
| ReaderProgressSnapshotTest | 1 | 0 | 0 | 0 |
| ReaderReplacementCapabilityTest | 15 | 0 | 0 | 0 |
| ReaderSelectionRangeWiringGuardTest | 9 | 0 | 0 | 0 |
| ReaderSelectionSnapshotSemanticsTest | 12 | 0 | 0 | 0 |
| ReaderSelectionSnapshotTest (viewmodel) | 17 | 0 | 0 | 0 |
| BuildSentenceHighlightedTest | 6 | 0 | 0 | 0 |
| **合计** | **156** | **0** | **0** | **0** |

**请求类数 16 == XML 类数 16，零匹配 = 0。**

reader 包全量回归（未请求，仅作旁证）：**75 个类 / 549 项，0 失败 0 错误 0 跳过**。

**三项 mutation 验证（均字节还原）：**

| # | 变异 | 结果 |
|---|------|------|
| M1 | `markdownBlockSelectionArgs` 换回 F3 错槽（`globalOffset = range.first` / `rangeStart = -1`） | **捕获**，7 项 FAIL |
| M2 | 删除 `chapterSpan` 区间求交过滤 | 首次**未捕获**（暴露测试缺口）；补区分性用例后**捕获**，1 项 FAIL |
| M3 | `epubBlockHighlightLocalRange` 去掉块裁剪 | **捕获**，8 项 FAIL |

按要求**未**运行全量 JVM / Lint / APK / androidTest / 真机。

---

## 8. 结构守卫与行为测试的分层计法

两者**分开统计**，不合并成「N 项绿灯」：

| 类别 | 数量 | 作用 |
|------|-----:|------|
| **行为测试**（验证运行时语义） | 45 | 端到端 locator、章 span 几何、EPUB 投影裁剪、纠错可达性 |
| **结构守卫**（只证明接线形状） | 9（`ReaderSelectionRangeWiringGuardTest`） | 源码级断言：四槽位顺序、`blockGlobalBase` 必传、投影复用入口、EPUB 坐标不可靠回落 `onToggleControls`、分页 EPUB 遗留契约未受影响 |

**守卫第 7 条已按要求升级**：不再只查字符串是否包含 `range.first`，改为
**按逗号切分实参、逐位置断言四个槽位**，并加反向钉子
（`globalOffset` 槽不得是 `args.rangeStart`/`args.sourceEnd`；`rangeStart` 槽不得是常量 `-1`）。

必须强调：结构守卫**不能**替代行为测试 —— F3 正是靠「只查出现与否」的守卫放过 P1-1。
本轮 P1 的实质证明来自 `MarkdownBlockSelectionLocatorTest` 的 M1 变异捕获，不来自守卫。

---

## 9. 同文件集 before/after：SHA-256、行数、numstat

**同一份 9 文件清单**（`/tmp/s07f31-filelist.txt`）+ **同一条命令**
`git diff -- <清单>`，前后各采集一次：

| | before | after |
|---|---|---|
| 采集时间 (UTC) | 2026-09-20T12:36:10Z | 2026-09-20T13:22:34Z |
| SHA-256 | `8b6da9766303cec71372cf6d73d137cb0233135b3b81650f357f708765838fd6` | `1c4bef7c147ce42afef1104b169bc00ebee1ae128df839b460d003ae1a7dbf8a` |
| 行数 | 763 | 935 |

**per-file numstat（after，同一清单）：**

```
249   0   MarkdownScrollHighlight.kt
127   2   ReaderContentHostEpubBranch.kt
 55   1   ReaderContentHostMarkdownBranch.kt
 76  16   ReaderMarkdownRender.kt
 86   3   ReaderProgressActions.kt
```

### 必须披露的采集缺陷（Qoder P3-7 同类问题，本轮仍存在）

清单里的 **4 个测试文件是 untracked**，`git diff` 根本不包含它们 ——
before / after 两份 patch 都只覆盖 5 个已跟踪的产品文件。
因此上表**不是** F3.1 的全部改动，只是已跟踪部分。未跟踪测试文件改用
「行数 + SHA-256」独立登记：

| 文件 | 行数 | SHA-256（前 16） |
|------|-----:|------------------|
| MarkdownUnitSourceRangeTest.kt | 174 | `6ec2da42060f2b5d` |
| EpubBlockSourceRangeTest.kt | 116 | `3a8338a19b384992` |
| MarkdownPersistentHighlightProjectionTest.kt | 268 | `e113bceeea2680ee` |
| ReaderSelectionRangeWiringGuardTest.kt | 209 | `5e82ede4f4f10c1a` |
| **新增** MarkdownBlockSelectionLocatorTest.kt | 206 | `8506f653ce3146f4` |
| **新增** MarkdownHighlightChapterSpanTest.kt | 239 | `f5be350d7aafb67a` |
| **新增** EpubBlockPersistentHighlightTest.kt | 173 | `ade5c784a893bff1` |
| **新增** EpubSingleCorrectionReachabilityTest.kt | 153 | `b0d6fddfb50c340b` |

**F3.1 增量 vs 既有 F3/F1–F2.4 改动的区分**：numstat 是相对 `origin/main` 的累计值，
**包含** F3 与 F1–F2.4 的既有改动，不能等同于 F3.1 增量。本轮 F3.1 自身的净增量只能由
before/after 两份 patch 的差（935 − 763 = **+172 行**）与上述 8 个测试文件的行数刻画；
未做进一步逐行切分，故**不**给出「F3.1 独有行数」的精确数字。

### 范围完整性检查

- `git diff --cached`：**空**（无 staged 内容）
- `git stash list`：**空**
- 基线：`origin/main = 5aed5939...`（未移动），HEAD 仍在同一提交
- **未**对不同文件集做并列相减（P3-8 要求）
- 越界检查：`ReaderViewModel.kt`、`ReaderSheetHost.kt`、`sheets/ReaderRulesSheet.kt`、
  `ReaderSearchIndexCoordinator.kt` 在 `git status` 中显示为已修改，但经内容核对，
  这些文件 **F3.1 标记出现次数为 0**，属其他 Agent 的既有改动，本轮**未触碰**
- **未 commit、未 push、未操作设备、未使用 MuMu**

---

## 10. 已确认事实 / 静态推断 / 未验证的运行时

**已确认（读源码或测试执行证实）：**
- `computeLocatorJson` 在 `epubBook == null` 时只读 `selectedRangeStart`（源码直读）
- Markdown 修复后 locator 非 null，`LocatorCodec.decode().legacyOffset == rangeStart`（测试执行）
- `SavedHighlight` 已无 `chapterIndex` 字段（反射断言执行）
- `chapterSpan` 过滤是「单元内但章外」的唯一拦截点（mutation M2 证实）
- `epubBlockHighlightLocalRange` 的裁剪、去重、溢出安全（15 项测试执行 + M3 捕获）
- `ESTIMATED_COORDINATES` ⇒ `Unavailable` ⇒ `replace` 槽位消失（测试执行）
- EPUB `gOff` 与 `LocatorCodec` 同一坐标空间（源码追链）
- 16 个请求类 == 16 个 XML 类，零匹配 = 0

**静态推断（读代码得出，未由运行时验证）：**
- 单处纠错「静态不可达」的结论：依赖 `replacementCoordinateSpace = ESTIMATED` 与
  `selectionToolbarActions` 的能力位传递链。链上每一环都有测试覆盖，但「整条链在真机上
  无遗漏」属推断 —— 若某条未覆盖的入口绕过工具栏直接构造命令，第 3 条门控
  （`findText` 相等 + 锚点非 null）仍在，但这一层我没在真机上走过。
- Markdown 与 EPUB 的 Compose 重组接线（`onSelectBlock` / `AnnotatedString` 叠加）在
  JVM 单测中执行不到，只有源码级守卫覆盖。

**未验证的运行时（本轮明确未做）：**
- 真机 UI：未跑 androidTest、未连设备、未截图（按约束「不得操作设备、不得使用 MuMu」）
- 全量 JVM / Lint / APK 构建：按要求未运行
- DB 迁移 / 历史高亮记录的真机回读效果：未验证（本轮零 schema 改动，理论上无影响）

---

## 11. 留给 F4 / 后续切片的未决项

| 编号 | 事项 | 归属 |
|------|------|------|
| P2-3 | 三基准接口（`blockGlobalOffset` / `chapterBase` / `blockGlobalBase`）未重构 | **F4**（任务书明令本轮不动） |
| P2-5 | 分页 → 非分页 EPUB 切换时高亮静默消失：本轮只做非分页路径的**渲染**，未处理分页高亮记录向 gOff 空间的换算 | 后续切片 |
| P3-2/P3-3 | `gOff` 相关「精确」「可靠」的过强措辞：本轮已在 EPUB 段 KDoc 收敛为「旧版混合量纲（章起点来自 ZIP 字节估算），但正是 `forEpub`/`LocatorCodec` 保存回读的同一空间」，未进一步改写其它文件 | 后续 |
| P3-6 | `ReaderConsumerSnapshotWiringGuardTest` 计为 9 而非 8：本轮实测 XML 确为 9（见第 7 节），未改 | 记录差异 |
| P3-7 | numstat 低估：本轮**仍未解决**（4 个测试文件 untracked，`git diff` 采不到）。已在第 9 节显式披露并改用具名登记，未再静默相减 | 需 `git add -N` 或改采集口径 |
| SEAM | `buildSentenceHighlighted`（`tts/ReaderTtsBar.kt`）若在后续要内建持久高亮参数，需扩展允许清单 | 后续 |
| SEAM | 精确选词：需专门输入层、选区手柄、canonical↔source 映射 | 后续（本轮明确边界外） |
| 遗留 | 「章长度未知」时退化为单元求交，属**有记录的显式降级**，非最终形态 | 后续 |

---

## 12. 最终状态

**等待 Qoder/Codex 复核**

本轮未宣布 F3 或 S07 完成。F3.1 的五项任务（A/B/C/D/E）均已完成并通过变异验证，
但 P1-1 的判定权在复核方；第 9 节的 numstat 采集缺陷与第 11 节的未决项均为开放项。
