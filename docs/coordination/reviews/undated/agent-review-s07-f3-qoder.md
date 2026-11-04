# S07-F3 独立复核 — Qoder

- 复核对象：`docs/coordination/agent-results/agent-result-s07-f3-zcode.md`（Zcode / deepseek v4.1Flash）
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`；固定基线 `origin/main = 5aed5939600e491843e3c44453921bd6273a233c`（实测 `git rev-parse HEAD` 与之相同）
- 复核日期：2026-09-20
- 复核性质：只读。未 reset / checkout / clean / stash / commit / push；未操作设备；未使用 MuMu；未触碰原仓库；产品源码与测试零改动，本文件是本次唯一新增文件
- 前置确认：Zcode 报告在复核开始时已存在且写完 —— 19545 字节，mtime `2026-09-20 19:59:44`，在 19:59–20:01 区间内三次采样（间隔 6s）字节数与 mtime 完全不变，末段为完整的「## 12. 最终状态」。**[事实]**
- 方法：不接受 Zcode 的自判、能力矩阵与测试数字，全部从源码重建。两条关键链路（Markdown 持久化闭环、EPUB 偏移量纲）由我自己逐环读源码，另派两名只读子代理独立追 EPUB locator 链与选区基础设施；子代理结论与我的独立追踪互相印证后才写入本报告。

---

## Standards

### 1. 是否只用现有 canonical/source 契约，没有 substring、首次匹配或显示长度猜坐标 —— **PASS（带 P2 命名风险）**

- `MarkdownPersistentHighlights.SavedHighlight`（`MarkdownScrollHighlight.kt:243-249`）刻意不含文本字段，`intersectLocal`（`:327-348`）只做区间求交，`canonicalText` 仅用于长度钳制，没有任何 `indexOf` / 首次匹配 / 文本猜测。**[事实]**
- `unitText`（`ReaderMarkdownRender.kt:43-44`）的 `substring` 是按已存的 `canonicalRange` 切片取文本，不是用文本反推坐标。**[事实]**
- 但 `markdownUnitSourceRange`（`MarkdownScrollHighlight.kt:178-185`）返回的是 **canonical** 空间，函数名与文档写的是「源半开区间」。`MarkdownRenderUnit.sourceRange` 是另一个真实存在的空间，且被 `MarkdownOffsetMap.kt:87-95,140-141` 消费。**[事实]**
  - **[P2-3]** 命名与契约不一致：全仓没有任何类型或断言阻止后来者把 `unit.sourceRange` 与 canonical 混写。S08（章节编辑器，队列第 2 位）一旦需要 .md 原始文件坐标，这批记录会在名字写着 source 的情况下给出 canonical 数值。建议改名 `markdownUnitCanonicalRange`，或在写入 locator 处加一条「本空间为 canonical」的显式断言/守卫测试。
- `highlightSourceLength(payload, h.text.length)`（`ReaderHelpers.kt:89-96`）在 payload 缺 `source_length` 时回退到**已存文本长度**，属于显示长度猜坐标的既有兜底。F3 复用它，未新增也未消除；在整块选择下两者数值恒等。**[事实]** 归为既有债，不计入本片缺陷。

### 2. 是否复用 `MarkdownRenderModel.flatten`，没有第二套展平顺序 —— **PASS**

- 投影函数不自己展平：签名接收 `units: List<MarkdownRenderUnit>`（`MarkdownScrollHighlight.kt:265`），调用方传 `markdownUnits`，后者来自 `remember(markdownBlock.chapter) { MarkdownRenderModel.flatten(markdownBlock.chapter) }`（`ReaderContentHostMarkdownBranch.kt:84-86`）。**[事实]**
- F3 补丁里唯一的 `flatten` 命中是 `table.header + table.rows.flatten()`（`:292`），那是表格二维单元格列表压平，与文档展平无关。**[事实]** 逐行核对补丁确认没有新增第二套单元遍历顺序。

### 3. 整本解析与流式分章的 `blockGlobalBase` 是否区分正确 —— **PASS**

- `ReaderContentHostMarkdownBranch.kt:59-61`：`blocksAreGlobal = (s.markdownDocument as? MarkdownDocument)?.isWholeDocumentParse == true`，`blockGlobalBase = if (blocksAreGlobal) 0 else s.chapterBase`。整本解析时 canonicalRange 已是全书坐标，基准取 0；流式分章时块是章内局部，基准取章节全书起点。**[事实]**
- 同一基准在选区（`:169`）与持久高亮投影（`:107`）两处复用同一个 `blockGlobalBase` 变量，不是两份算式。**[事实]**
- **[P3]** `projectPersistentHighlights(blockGlobalBase: Int = 0)` 与 `RenderMarkdownUnit(blockGlobalBase: Int = 0)` 都带默认值 0。默认值恰好是「整本解析」的正确值，因此流式分章路径漏传时不会报错、只会整段错位。当前两个调用点都显式传了值，缺陷未发生；建议后续把默认值去掉，改成必填。
- **[P3]** 同一渲染函数里并存三个「基准」参数：`blockGlobalOffset = -1`（`:244`，遗留/TTS 用）、`chapterBase = 0`（`:245`）、`blockGlobalBase = 0`（`:255`，F3 新增）。分页 EPUB 路径只传 `blockGlobalOffset`（`PagedEpubContent.kt:444`）不传 `blockGlobalBase`，靠遗留适配器丢弃新区间才没出错（`ReaderMarkdownRender.kt:216-218` `{ text: String, _: IntRange -> legacy(text, unitGOff) }`）。**[事实]** 这是一处「靠丢弃参数保持正确」的脆弱设计，后续删掉那个 `_:` 就会静默写入 base=0 的错坐标。

### 4. 表格单元格是否使用自身 `canonicalRange` —— **部分 PASS**

- 渲染侧成立：`MarkdownRenderModel.kt:208-209` 单元格自己算 canonical/source，F3 的投影对表格逐单元格求交并带上 `cellCanonicalStart` 判别子（`MarkdownScrollHighlight.kt:290-304`），渲染端按 `it.cellCanonicalStart == cell.canonicalRange.first` 过滤（`ReaderMarkdownRender.kt:399,415`）。整表不会承载一个虚假的整表局部区间。**[事实]**
- 选择侧不成立：`markdownUnitIsBlockSelectable(TABLE) = false`（`MarkdownScrollHighlight.kt:206`），而 `markdownCellSourceRange`（`:195`）在产品代码里**零调用**（全仓 grep 只有定义与测试）。所以表格文字目前**根本不可选**。**[事实]**
  - **[P3-1]** 报告 §1.2/§2.2 用「表格单元格必须使用自己的范围」支撑选择能力，读者会以为单元格可选；实际只有高亮回显按单元格。`markdownCellSourceRange` 与 `markdownResolveUnitSourceRange`（`:188-192`，内含 `check()`）在产品里都是死代码，仅由单测驱动。

### 5. source 区间是否统一半开、边界与空区间是否正确 —— **PASS**

- `markdownShiftRange`（`MarkdownScrollHighlight.kt:214-221`）：`start until (last + 1 + base)`，`base < 0` / 空 range / `end <= start` 一律返回 null。**[事实]**
- `epubTextBlockSourceRange`（`ReaderContentHostEpubBranch.kt`，F3 新增）：`start until (gOff + text.length)`，`gOff < 0`、`text.isBlank()` 返回 null。**[事实]**
- 保存端提交 `range.last + 1`（`ReaderContentHostMarkdownBranch.kt:173`、`ReaderContentHostEpubBranch.kt:246`），与 `ReaderScreenState.selectedSourceEnd` 的「半开终点」文档一致（`ReaderViewModel.kt:83-84,104-108`）。**[事实]**
- 空单元格：`inlinesRange` 空列表返回 `0 until 0`（`MarkdownRenderModel.kt:230`），经 `markdownShiftRange` 的 `range.isEmpty()` 判空被拒。**[事实]** 没有把「空」当成「坐标 0」的伪锚点。

### 6. EPUB 的 gOff、章节起点、全书偏移、sourceEnd 是否有真实来源证明 —— **PASS（措辞过强，P3）**

我独立重追了这条链，Zcode 的链路**成立但被说满了**：

- `computeBlockGlobalOffsets`（`ui/screen/reader/ReaderTextIndex.kt:18`，注意不在 `feature/reader/doc/`）→ `LegacyOffsetCodec.blockOffsets`（`LegacyOffsetCodec.kt:55-64`）：`var acc = chapterBase`，逐块 `acc += len + 1`，非文本块传 null 记 -1。**[事实]** 与报告一致。
- 报告把 `ReaderDocument.kt:47` 的不变式当作「证明」。实际它**是注释，但同一接口有实现兜底**：`ReaderDocument.kt:64` `fun text(chapterIndex: Int) = blocks(...).filterIsInstance<DocBlock.Text>().joinToString("\n") { it.text }`，且 `EpubDocument` 未覆写 `text()`。**[事实]** 所以「块终点 = gOff + text.length、块间 `\n` 不计入块长」在 EPUB 侧确实由构造保证 —— 结论对，但报告把它记成「注释即证明」，证据类型写错了。
- **[P3-2]** 章节起点来自 `estimatedTextLength`（`domain/model/Epub.kt:44`，赋值处 `EpubParser.kt:777,811` = ZIP `entry.size`），是**字节估算不是字符计数**。`EpubReplacedChapterSource.kt:25-27` 明确写着「全书层偏移从来不是精确 source，章内偏移才是」。F3 的代码注释披露了「旧版混合量纲」，这点诚实；但 §1.1/§1.3 的「gOff 是可靠的整书源起点」措辞过强 —— 可靠的是**同一混合空间内的自洽往返**，不是绝对精确。
- **[P3-3]** 报告 §1.3 第 5 步用 `epubChapterEndOffset`（`ReaderScrollPosition.kt:63`）作「既有口径一致」的佐证。该函数**没有任何生产调用方**（全仓只有自身定义、注释和单测 `ReaderProgressNavigationTest.kt:105`）。用死代码佐证活代码，证据强度为 0。
- **[事实]** `EpubBlockSourceRangeTest` 用手工构造的 `DocBlock.Text` 列表，不经过真实 EPUB 解析、`chapterStartOffsets` 或 `contentText`。它证明的是算术，不是坐标来源。

### 7. 是否把 display offset 写进 locator 或数据库 —— **PASS**

- Markdown 提交的是 canonical + base 的全书坐标，EPUB 提交的是混合量纲全书坐标，两者都不是「渲染屏幕上第几个像素/第几个可见字符」。`buildMarkdownAnnotated` 里的 `shift = prefix.length`（列表符号等显示前缀）只作用于 `AnnotatedString` 局部样式区间，**没有**流向 `onSelectBlock`。**[事实]** 报告 §3.3 第 1 点自述的「初版误用 sourceRange」已修，方向正确（改为 canonical 与全仓 Markdown 持久化口径一致）。

### 8. 持久 / 搜索 / TTS 高亮是否混淆语义或颜色 —— **PASS（P3 同色）**

- 三段独立 `addStyle`：TTS `sentenceHighlightBg`、搜索 `searchHighlightBg`、持久 `highlightColors(colorKey)`（`ReaderMarkdownRender.kt:118,129,141`），坐标来源互不覆写；持久投影是纯函数，不读搜索状态，因此反例 7 由构造保证。**[事实]**
- **[P3-4]** `searchHighlightColor(paper) = paper.highlight("yellow")`（`ReaderEyeCareFocusState.kt:25`）与持久高亮的兜底色 `h.color ?: "yellow"`（`ReaderContentHostMarkdownBranch.kt:102`）解析到同一个 Color。用户显式选色时不冲突，只有 `color == null` 的旧记录会与搜索命中同色。语义可区分性有实际损失，但很窄。
- **[事实]** Markdown 滚动分支从不传 `ttsSentenceRange` / `sentenceHighlightBg`（`ReaderContentHostMarkdownBranch.kt:159-175`），所以该路径 TTS 跟读高亮本来就不存在。这是 F3 之前就有的状态，不是本片引入，也不被本片修复。

### 9. 是否影响分页 EPUB、TXT、中央点击、链接、滚动、F1–F2.4 —— **PASS（一处越界副作用，P2）**

- 分页 EPUB：`PagedEpubContent.kt` 在 F3 时间窗内未被写入（mtime 检查），其遗留契约 `onSelectBlock(block.text, gOff)`（`:411`）与适配器 `callbacks.onSelect(text, off, -1, -1)`（`ReaderContentHostEpubBranch.kt:168`）仍在，`sourceEnd` 仍为 -1。**[事实]**
- TXT：`ReaderContentHostPlainTextBranch.kt` 未被 F3 触碰；F2 系列 6 个哨兵类我独立跑过，全绿（见 Verification）。**[事实]**
- **[P2-2]** 非分页 EPUB 的 `sourceEnd` 由 -1 变真实值，**顺带翻转了「单处纠错」的可达性**：`ReaderSearchIndexCoordinator.kt:57-66` 之前因 `end < 0` 直接返回 null → `ReaderViewModel.kt:470-474` 给 `NotAnchorable`；现在返回 `(gOff, gOff+len)` → `ReaderViewModel.kt:482-487` 真的落库一条 `SaveSingleCorrection`。替换净化明确不在 F3 授权范围内，这条消费者链路既无测试也无归属声明。**[事实]** 我没有把它判成 P1，因为 `EpubReplacedChapterSource.kt:18-22` 说明 EPUB 的持久化 locator 本来就用「估算章基址 + 章内真实偏移」这套混合空间，坐标空间是自洽的；但「顺手改变另一切片的用户行为」必须记。
- 中央点击：坐标不可靠时回落 `callbacks.onToggleControls()`，与原来的中央点击语义同源，不新造第三种反馈。**[事实]** 副作用：空白块 / `gOff < 0` 的轻点，以前走 `onSelect(blank)` → `ClearSelection`，现在改为唤出控制栏 —— 行为变了但方向合理，报告未列此细节（P3）。
- 链接：滚动 EPUB 分支本来就没有链接处理（脚注弹层只在分页路径），F3 的块级 `clickable` 没有遮挡既有可点元素。**[事实]**

### 10. 不支持路径是否真正阻止伪坐标保存 —— **FAIL（见 P1-1）**

- 单元级门控是真的：不可选择的单元不挂 `clickable`（`ReaderMarkdownRender.kt:276-280`），表格/分隔线/图片块拿不到回调，`"[Table]"` 字面量伪选区已删除且被守卫钉住。**[事实]**
- 但 Markdown 路径存在**「看似可保存、实际无坐标」**，正是任务书反例 11 禁止的形态。完整链条（每一环我都亲自读过源码，非引用 Zcode）：

  1. `ReaderContentHostMarkdownBranch.kt:172-174` 提交 `callbacks.onSelect(text, range.first, -1, range.last + 1)` —— 起点写进第 2 个参数槽；
  2. `ReaderContentHost.kt:145` 该槽是 **`globalOffset`**，第 3 个才是 `rangeStart`，这里 `rangeStart = -1`；
  3. `ReaderLayerBuilders.kt:122-127` 原样转成 `SetSelectedText(text, rangeStart, globalOffset, sourceEnd)` → `ReaderViewModel.kt:371-373`；
  4. `ReaderProgressActions.kt:37-42`：`epubBook != null && globalOffset >= 0 → forEpub`；`epubBook == null && rangeStart >= 0 → forPlain`；**否则 `null`**；
  5. `ReaderContentHost.kt:178,185` 的 `when` 顺序证明：能进 Markdown 分支就必然 `epubBook == null`（EPUB 分支在前）；
  6. ⇒ Markdown 整块选择的 locator **恒为 null**；`LocatorCodec.kt:45-46` `decode(null) → null`；
  7. ⇒ F3 自己的投影在 `ReaderContentHostMarkdownBranch.kt:94` `parseLocatorOffset(h.locator_json) ?: return@mapNotNull null` 把这些记录全部丢弃。

  **[P1-1] 结论：Markdown 滚动模式「持久高亮重渲染」端到端不可达。** 用户点块 → 工具栏出现 → 选色 → 看到「已高亮」→ 记录入库（text/color/payload 齐全，`locator_json` 为空）→ 正文永远不再显示它，笔记/灵感走同一个 `computeLocatorJson` 闭包（`ReaderActions.kt:386-389`）因而同样无锚点。这既是一次**虚假成功反馈**，也让报告 §1.1 矩阵里 Markdown「持久高亮重渲染能力：F3 后具备」成为**虚假能力陈述**，并且没有关闭审查文档 §Spec 的 P2「Markdown 不重新渲染已保存高亮」。
  - 契约依据很硬：`LocatorBuilder.kt:12` 注释写明「TXT / Markdown：单章，章内偏移 == 全书偏移」并给出 `forPlain`，即 Markdown 的正确槽位是 `rangeStart`；TXT 滚动分支正是这么做的（`ReaderContentHostPlainTextBranch.kt:420-427` 传 `-1, localDisplayToGlobalSource(...), ...`）。**[事实]**
  - 修复是一处槽位交换：`callbacks.onSelect(text, -1, range.first, range.last + 1)`。
  - **[事实]** 为什么 397 项全绿没拦住：`ReaderSelectionRangeWiringGuardTest.kt:51-63` 用正则 `callbacks\.onSelect\([^)]*\)` 抓出调用文本，只断言里面**含有** `range.first` 与 `range.last + 1`，**不检查它们落在哪个参数位**。守卫把 EPUB 的正确槽位一起钉到了 Markdown 上 —— 守卫本身固化了缺陷。补一条穿过 `computeLocatorJson` 的纯函数接线用例即可锁死（该函数已是纯函数，不需要 Compose）。

### 11. 是否有清单外修改、无关格式化或夹带其他切片 —— **PASS（披露质量良好，两处记账误差）**

- F3 时间窗（19:00–20:05）内被写入的文件恰为：4 个产品文件、4 个新测试文件、Zcode 自己的报告。**[事实]** 无清单外文件、无全仓格式化。
- `PagedEpubContent.kt`、`ReaderContentHost.kt` 声明未改动 → 时间窗与 `git diff` 双重核实为真。**[事实]**
- 越界文件披露可核实：`MarkdownSelectionSource.kt` 文件不存在，全仓（含 docs）grep 除报告自述外零残留。**[事实]**
- 夹带关键词扫描：补丁新增行里只出现 TTS/搜索的**语义注释**，无 S08/编辑/页码/硬件键/WebDAV/APK 相关代码。**[事实]**
- **[P3-5]** `ReaderContentHostEpubBranch.kt` 的 after-patch 里含一行**他人**的 `tapActionPreset = settings.readerSettings.tapActionPreset`（19:06 的 before-patch 已有，属 tap-zone 工作）。它不是 F3 加的，但被算进了「F3 相关 4 个产品文件」的补丁里，见 Scope integrity。

### 12. 结构守卫与语义测试是否分开统计 —— **PASS**

- 报告把 30 个语义用例（10+9+11）与 8 个结构守卫分开列，且守卫测试自己的 KDoc（`ReaderSelectionRangeWiringGuardTest.kt:22-25`）明确声明「证明接线形状，不证明运行时语义；统计时两类必须分开计」。**[事实]** 我的 XML 复核逐类数字与之一致（见 Verification）。
- 但分开统计不等于有效：见 P1-1，形状守卫恰好把错误的参数槽位钉住了。

---

## Spec

### 独立建立的能力矩阵

判定依据是「端到端能否拿到并留住可靠半开区间」，不是「界面上能不能点出颜色」。粒度：细粒度 = 字符级拖选。

| 路径 | 用户能否选中 | 选区粒度 | 可靠 sourceStart/sourceEnd | 能否保存高亮/笔记/locator | 已保存高亮能否重渲染 | 用户可见降级是否诚实 |
|---|---|---|---|---|---|---|
| TXT 分页（基线） | 能 | 细粒度 | 能 | 能（`forPlain` / rangeStart 槽） | 能（`ReaderContentHostPagedBranch.kt:131-138`） | 基线，不适用 |
| TXT 滚动（基线） | 能 | 细粒度（`BasicTextField` 选区） | 能（`localDisplayToGlobalSource`） | 能 | 能（`ReaderContentHostPlainTextBranch.kt:280-296`） | 基线，不适用 |
| Markdown 整本滚动 | 能（点整块） | **整块** | 计算正确（base=0） | 选区入库能；**locator 恒 null** | **不能（端到端不可达，P1-1）** | **不诚实**：工具栏 +「已高亮」暗示成功 |
| Markdown 流式/分章滚动 | 能（点整块） | **整块** | 计算正确（base=chapterBase，F3 已修） | 同上 | 同上不能 | 同上 |
| EPUB 分页（基线） | 能 | 细粒度 | 起点能；`sourceEnd` 仍 -1（遗留） | 能（`forEpub`） | 能（分页投影） | 基线，未被 F3 改动 |
| 非分页 / 旧 EPUB 滚动 | 能（点整块） | **整块** | 章内精确 + 全书估算（混合量纲，代码注释已披露） | 能（`forEpub`，locator 非空） | **不能**：该分支完全没有持久高亮渲染入口 | **部分不诚实**：坐标不可靠时回落中央点击是真降级；但「从分页切滚动后已有高亮静默消失」无任何 UI 边界说明 |

关键判定：

- Markdown 一行**不能 PASS**。不是因为它只做到整块（整块 + 可靠区间 + 诚实标注按规则可以 PASS），而是因为它的保存结果缺锚点，属于反例 11 禁止的「看似可保存、实际无坐标」。
- 非分页 EPUB 一行按规则**可以 PASS**：它保存的 locator 与分页路径同空间、能往返；重渲染缺失是**已披露的范围裁剪**（报告 §5），且不改写、不删除既有记录。但按任务书「仅在报告里写不支持、产品 UI 无可感知边界，不算明确降级」，这一项的降级诚实度只能记部分分。
- 「正文没有 `SelectionContainer` 所以只能整块」这一论证我核实为**方向对、结论过强**：`SelectionContainer` 确实只出现在 AI 面板（`AiExplainResultSection.kt:134`、`ReaderAiAssistSheet.kt:525`），正文没有；但同一仓库的 TXT 滚动正文已经在用 `BasicTextField` 选区做到字符级（`ReaderContentHostPlainTextBranch.kt:410-427`）。所以「整块」是**每条路径各自的实现选择**，不是 Compose 天花板。SEAM 结论（要跨模块映射模型）仍成立，理由要改写。**[P2-4]**

### 反例逐条结论

| # | 反例 | 我的结论 | 依据 |
|---|---|---|---|
| 1 | 两个相同段落，选第二段不得落到第一段 | **PASS** | 坐标由 `canonicalRange + base` 决定，与文本无关；`MarkdownUnitSourceRangeTest:61-69` 断言两区间不相交 |
| 2 | 标题/列表/引用/代码围栏标记不得把 display 当 source | **PASS** | canonical 空间本身去标记；`selection range never uses display offset as source offset`（`:151-160`）比较源文本与 canonical 长度；`code block language label never enters the range`（`:133-140`） |
| 3 | 表格相同单元格文字不得串到另一单元格 | **投影侧 PASS / 选择侧不适用** | 投影带 `cellCanonicalStart` 判别子且渲染端真的按它过滤（`ReaderMarkdownRender.kt:399,415`）；但单元格当前不可选（P3-1），该反例在 UI 上暂时无从触发 |
| 4 | 整本与分章相同 canonicalStart 必须得到不同全书坐标 | **PASS** | `markdownShiftRange` 加 `blockGlobalBase`；两条产品调用路径分别传 0 与 `chapterBase`（`ReaderContentHostMarkdownBranch.kt:59-61,169`） |
| 5 | 高亮跨单元时两边各渲染交集 | **PASS** | `intersectLocal` 逐单元裁剪，`MarkdownPersistentHighlightProjectionTest:51-70` 覆盖 |
| 6 | stale 书/章、负坐标、空区间不得显示或保存 | **部分 PASS** | 纯函数层齐全（`:94-96,121-137`）；但生产的 stale-**章**判据恒不成立 —— 调用方把 `chapterIndex = s.chapterIndex` 填进每条记录（`ReaderContentHostMarkdownBranch.kt:99`），`highlight.chapterIndex != currentChapterIndex` 永远为假，防串纯靠几何不相交。**[P2-1]** 测试 `:108-119` 传入产品永不产生的 `7`，给出假安全感 |
| 7 | 搜索高亮与持久高亮重叠不得改写持久坐标 | **PASS（构造保证，未测）** | 投影不读搜索状态，搜索区间不回写记录；无 Compose 层用例，运行时叠加顺序未验证 |
| 8 | EPUB 两个重复段落不得靠文字搜索定位 | **PASS** | `blockOffsets` 逐块累加，`EpubBlockSourceRangeTest:64-77` 断言起点唯一、相邻区间不相交 |
| 9 | EPUB 图片/分隔线不得生成伪选区 | **PASS** | `epubBlockSourceRange` 对 `Image`/`Markdown` 恒 null，即使误传非负 gOff；`ReaderDocument.kt:64` 的 joinToString 保证非文本块不占字符 |
| 10 | 分页 EPUB 切非分页 EPUB，已有高亮应正确显示或明确不支持 | **FAIL（降级不诚实）** | `ReaderContentHostEpubBranch.kt` 全文不含 `s.highlights` / `locator_json` / `parseLocatorOffset`（grep 核实）→ 静默消失；产品 UI 无任何「此视图不显示标注」的可感知边界。数据未被删除或改写，故不是数据缺陷，是降级表达缺陷。**[P2-5]** |
| 11 | 不支持路径的长按/点击/工具栏行为必须一致，不得「看似可保存、实际无坐标」 | **FAIL** | 见 P1-1：Markdown 整块选择给出完整可保存外观与成功提示，落库记录 locator 为空 |
| 12 | 选区清除、快照消费者、翻页/切章回归仍成立 | **PASS** | F2/F2.4 六个哨兵类我独立跑：17+12+8+9+10+8 全绿；F3 未触碰 plain-text 分支与快照 reducer，风险面只在共享的 `onSelect` 契约上 |

---

## Verification

### 命令与退出码

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
  --tests "com.creationreadingassistant.ui.screen.reader.MarkdownUnitSourceRangeTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.EpubBlockSourceRangeTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.MarkdownPersistentHighlightProjectionTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionRangeWiringGuardTest" \
  --tests "com.creationreadingassistant.feature.reader.doc.MarkdownParserTest" \
  --tests "com.creationreadingassistant.feature.reader.doc.MarkdownDocumentTest" \
  --tests "com.creationreadingassistant.feature.reader.doc.MarkdownOffsetMapTest" \
  --tests "com.creationreadingassistant.feature.reader.doc.MarkdownRenderModelTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.MarkdownRenderUnitTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.MarkdownScrollHighlightTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubParserTextTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubParserMalformedCorpusTest" \
  --tests "com.creationreadingassistant.ui.viewmodel.ReaderSelectionSnapshotTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionSnapshotSemanticsTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReadingUnitDisposeWiringGuardTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReadingUnitSelectionOwnershipTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionGranularityTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionExternalActionsTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderContentHostSelectionCallbacksTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.TxtScrollChapterTargetTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderNoteSnapshotSaveTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.sheets.RuleEditorDraftTest" \
  --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest"
```

- 退出码 **0**；`BUILD SUCCESSFUL in 46s`；`GRADLE_EXIT=0`（日志 `/tmp/s07f3-qoder-gradle.log`）。**[事实]**
- 未运行全量 JVM、Lint、APK、androidTest、任何设备测试；未使用 MuMu；未修改任何测试或产品源码。

### 先解析 FQCN，再跑（防止 `--tests` 静默漏测）

26 个请求类先逐个在 `src/test` 下解析真实包名：**26 个全部 FOUND，0 个 MISSING**。`RuleEditorDraftTest` 的包是 `...ui.screen.reader.sheets`（不是 `...ui.screen.reader`），已按解析结果写全名 —— 这是 F1 那轮踩过的坑，本轮提前规避。**[事实]**

### XML 逐类统计（我自己的这一批，26 个文件全部 mtime 20:05:59）

| 测试类 | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `ui.screen.reader.MarkdownUnitSourceRangeTest` | F3 语义（新） | 10 | 0 | 0 | 0 |
| `ui.screen.reader.EpubBlockSourceRangeTest` | F3 语义（新） | 9 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownPersistentHighlightProjectionTest` | F3 语义（新） | 11 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionRangeWiringGuardTest` | F3 结构守卫（新） | 8 | 0 | 0 | 0 |
| `feature.reader.doc.MarkdownParserTest` | Markdown 链路 | 43 | 0 | 0 | 0 |
| `feature.reader.doc.MarkdownDocumentTest` | Markdown 链路 | 17 | 0 | 0 | 0 |
| `feature.reader.doc.MarkdownOffsetMapTest` | canonical↔source 映射 | 15 | 0 | 0 | 0 |
| `feature.reader.doc.MarkdownRenderModelTest` | 渲染模型 | 13 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownRenderUnitTest` | 渲染单元 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownScrollHighlightTest` | 既有 seam | 11 | 0 | 0 | 0 |
| `feature.reader.EpubParserTextTest` | EPUB 解析 | 28 | 0 | 0 | 0 |
| `feature.reader.EpubParserMalformedCorpusTest` | EPUB 畸形语料 | 11 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest` | F2.4 哨兵 | 17 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | F2.4 哨兵 | 12 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | F2.4 哨兵 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest` | F2.4 哨兵 | **9** | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2 哨兵 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionGranularityTest` | F2.2 哨兵 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionExternalActionsTest` | F1/F2 哨兵 | 14 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 哨兵 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 哨兵 | 4 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 哨兵 | 3 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | 滚动 TXT | 7 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderNoteSnapshotSaveTest` | F2.4 哨兵 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.sheets.RuleEditorDraftTest` | F2.3/2.4 | 36 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | 状态机 | 72 | 0 | 0 | 0 |
| **合计** | | **397** | **0** | **0** | **0** |

- 请求类数 26 = XML 类数 26，**零匹配：无**。`--rerun` 后结果目录只剩这 26 个文件，未混入 Zcode 全量跑的残留。**[事实]**
- 语义 / 结构分开核：F3 语义 10+9+11 = **30**，结构守卫 **8**，与报告 §4.2 一致。**[事实]**
- **[P3-6] 一处数字纠错**：报告 §7.3 把 `ReaderConsumerSnapshotWiringGuardTest` 记为 8 项，XML 实测 **9** 项。其余 17 个 Zcode 点名的类数字与我的实测**逐一相符**（含 43/17/15/13/6/11/28/11/17/12/8/10/8/14/9）。
- Zcode 声称的全量「299 类 / 2668 用例」我**未复现也未采信**（任务书禁止全量 JVM），本报告只对上面 397 项负责。

### 未验证边界（必须与绿灯一起读）

- **Compose 运行期全部未验证**：`clickable` 是否真的挂上、`remember` 键（`markdownUnits, s.highlights, s.bid, s.chapterIndex, blockGlobalBase`）在流式换章时是否漏算、LazyColumn 复用是否串高亮、`persistentHighlights` 与搜索样式叠加顺序 —— JVM 单测执行不到，全部标 **未验证**。
- **真机行为未验证**：整块点击的实际触摸目标、列表项缩进/引用竖条区域点击归属、TalkBack 下「整块选择」的可理解性、切章/翻页后的选区残留。
- 结构守卫证明的是**源码文本形状**，不是运行时语义；本报告 P1-1 恰好证明「形状正确、接线错误」是守卫测不到的类别。
- 我没有做变异测试复现（Zcode §7.4 的三次变异我未重放），因此「用例真能拦住缺陷」只在我自己论证的范围内成立。

---

## Scope integrity

### 补丁与哈希复算（我自己重新生成）

| 项 | Zcode 声称 | 我的复算 | 结论 |
|---|---|---|---|
| before-patch | `264aeb0e4be30c22d74762045442ec1415f6576f7a1aba05a2f55a51d15492dc` / 161 行 | 同值 / 161 行（mtime 19:06） | **一致** |
| after-patch | `b93d520cf121dcedbab59e2b2e1541c02a1b68591860af2ef4a9a7f1e241b9cc` / 604 行 | 我用 `git diff -- <4 文件>` 重新生成：同值 / 604 行 | **一致，且等于当前工作树** |

**[事实]** after-patch 与 20:02 时刻的工作树逐字节相同 → 报告写完之后无人再改这 4 个文件，不存在复核期并发覆盖。

### 时间顺序

`19:06` before-patch → `19:31` ReaderMarkdownRender → `19:48` EpubBranch → `19:49` MarkdownBranch → `19:50` MarkdownScrollHighlight → `19:42–19:47` 四个测试文件 → `19:57` after-patch → `19:59` 报告。顺序自洽：补丁在最后一次源码编辑之后采集。**[事实]**

### 逐文件增量：报告数字不可复现

| 文件 | 报告 §4.1 | 实测 `git diff --numstat` | 差 |
|---|---|---|---|
| `MarkdownScrollHighlight.kt` | +183 −0 | **+199 −0** | 少报 16 |
| `ReaderContentHostMarkdownBranch.kt` | +36 −1 | +36 −1 | 一致 |
| `ReaderMarkdownRender.kt` | +69 −16 | **+70 −16** | 少报 1 |
| `ReaderContentHostEpubBranch.kt` | +47 −1 | +50 −1（其中 1 行是他人的 `tapActionPreset`）→ **F3 净 +49 −1** | 少报 2 |
| 合计 | +335 −18 | **+354 −18（F3 净量）** | 少报 19 |

**[P3-7]** 增量表整体不可复现（少 19 行）。因为 after-patch 哈希可复现，这是**记账误差**而不是夹带 —— 但复核者若照抄这张表会得到错误的规模感。

### before / after 不可相减（方法问题）

**[P3-8]** 两个补丁覆盖的**文件集不同**：before-patch 只含 `ReaderContentHostEpubBranch.kt` + `content/PagedEpubContent.kt`（161 行，全是 tap-zone 工作），after-patch 含 4 个 F3 产品文件（604 行）。报告 §9 把它们并排成「161 → 604」，读者会误算成 F3 增量。我改用另一条独立路径确定 F3 净量：F3 时间窗内的文件清单 + 三个文件在 before-patch 中**完全无 hunk**（即 F3 前它们相对 HEAD 干净）→ 其当前 diff 全部归 F3；EpubBranch 扣除 1 行外来 hunk。**[事实]** 建议以后固定「同一文件集、同一命令」再取 before/after。

### 范围与越界

- `git diff --cached --name-only` → **0 个文件**；`git stash list` → **空**；`git rev-parse HEAD` = `origin/main` = 基线，未 commit / 未 push。**[事实]**
- 工作树里其余大量 `M` 文件（含 `PagedEpubContent.kt`、`ReaderLayerBuilders.kt`、F2 系列）mtime 均在 19:00 之前，属 F1–F2.4 与他人既有工作，F3 未触碰。**[事实]** 我把 F3 净增量与 F1–F2.4 既有改动分开计算，未混记。
- 原仓库 `D:\develop\Code\Codex\creation-reading-assistant`：只做了文件系统级检查（`find android/app/src -newermt "2026-09-20 18:00"`）→ **无文件被写入**。未在该目录执行任何 git 命令。**[事实]**
- 本次复核**只新增一个文件**：本路径下的 `agent-review-s07-f3-qoder.md`。Gradle 产物写入既有 `android/app/build/`（构建输出目录，非源码）。

---

## Verdict

# FAIL

存在 1 个 P1，且属于任务书明确禁止的形态：**「看似可保存、实际无坐标」+ 虚假能力陈述**。

**P1-1（阻断）** Markdown 滚动整块选择保存的高亮 / 笔记 / 灵感 **locator 恒为 null**，F3 任务 C 的持久高亮重渲染端到端不可达；报告 §1.1 与 §3 的「F3 后具备」为虚假能力陈述；审查文档 §Spec 的「Markdown 不重新渲染已保存高亮」并未因此片关闭。根因是 `onSelect` 参数槽位错置（`ReaderContentHostMarkdownBranch.kt:173` 用了 `globalOffset`，而 Markdown 的既有契约是 `LocatorBuilder.forPlain` 的 `rangeStart` 槽），且 `ReaderSelectionRangeWiringGuardTest.kt:51-63` 只锁文本形状、不锁槽位，因而 397 项全绿。修复是一处槽位交换 + 一条穿过 `computeLocatorJson` 的纯函数接线用例。

**必须一并处理的 P2（不阻断判定，但 F4 前应有归属）**

1. **P2-1** 生产路径的 stale-chapter 判据恒不成立（`ReaderContentHostMarkdownBranch.kt:99`），测试用产品永不产生的入参制造假安全感。
2. **P2-2** 非分页 EPUB 的 `sourceEnd` 改动顺带翻转「单处纠错」可达性（`ReaderSearchIndexCoordinator.kt:57-66` → `ReaderViewModel.kt:464-489`），替换净化不在本片授权内，零测试覆盖。
3. **P2-3** canonical 被命名为 source（`markdownUnitSourceRange`），与真实 `MarkdownRenderUnit.sourceRange` 并存且无守卫，S08 是队列下一步。
4. **P2-4** 「没有 SelectionContainer 所以只能整块」的论证过强 —— TXT 滚动正文已有字符级选区先例，SEAM 理由需改写为实现选择而非平台上限。
5. **P2-5** 分页 EPUB → 非分页 EPUB 的已有高亮静默消失，UI 无任何可感知边界；按任务书这不构成「明确降级」。数据未被改写，方向正确，但表达不足。

**P3（记录即可）**：增量表少 19 行（P3-7）；before/after 文件集不同、不可相减（P3-8）；`ReaderConsumerSnapshotWiringGuardTest` 应为 9 项非 8 项（P3-6）；`markdownCellSourceRange` / `markdownResolveUnitSourceRange` 产品零调用、表格单元格实际不可选（P3-1/P3-3）；用无生产调用方的 `epubChapterEndOffset` 作佐证；`ReaderDocument.kt:47` 不变式实为接口默认实现 `:64` 保证、证据类型写错；持久高亮兜底色与搜索高亮同色；`blockGlobalBase` 与 `blockGlobalOffset`/`chapterBase` 三基准并存且新参数带默认 0。

**做对的部分（独立确认，非照抄自述）**：canonical 空间选择方向正确、流式分章 base 已修、半开区间与空/负/非法坐标处理正确、图片与分隔线不产生伪选区、`"[Table]"` 字面量伪选区确已删除、表格单元格回显按 `cellCanonicalStart` 正确区分、重复文本按坐标不串块、跨单元各渲染自己的交集、结构守卫与语义用例分开统计、越界文件披露可核实为零残留、范围与基线纪律干净、397 项我独立复跑全绿。

**未验证边界（即使后续转 PASS 也必须保留）**：Compose 运行期（clickable 挂载、`remember` 键、LazyColumn 复用、样式叠加顺序）与真机行为（触摸目标、TalkBack、切章/翻页残留、字体放大）全部未验证；全量 JVM / Lint / APK / 设备测试未运行；Zcode 的 299 类 / 2668 项全量数字未复现也不采信。

**给下一片的指示性结论**：F3 不需要重做架构，需要的是（a）Markdown 槽位一行修正 + 一条端到端 locator 接线用例；（b）把 stale-chapter 判据接到记录自己的章节身份或明确删除该判据；（c）非分页 EPUB 的重渲染缺失要么补上、要么在 UI 上给出可感知边界；（d）`markdownCellSourceRange` 等死代码要么接通要么删除。是否在本片内补 (a) 之外的项，请 Codex 决定；我不擅自扩张。

未领取下一任务，等待 Codex 复核。F4、S08 及其余队列保持 HOLD。
