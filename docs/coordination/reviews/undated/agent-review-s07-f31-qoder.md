# S07-F3.1 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：S07-F3.1 —— 关闭我在 F3 复核判定的 P1-1（Markdown `onSelect` 错槽导致 locator 恒 null），并完成 F3 遗留的 B/C/D/E
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`
- 基线：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`，HEAD 未移动（我核对）
- 已读输入：`AGENTS.md`、正式报告 F3 段、`agent-result-s07-f3-zcode.md`、`agent-review-s07-f3-qoder.md`（我上一轮的 FAIL）、`agent-result-s07-f31-zcode.md`（mtime `21:26`，12 节完整，我 21:51 与 22:0x 两次读取内容一致 → 已停止写入）、F3.1 实际生产代码与 8 个测试文件
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[未验证]** Compose 运行期或真机

我没有采用 Zcode 的 PASS、测试数量或能力矩阵；下表每项判定给出我自己的取证位置。它的自报数字我逐条重跑核对（结果一致，见 Verification）。

---

## Standards

| # | 检查项 | 判定 | 我的证据 |
|---:|---|---|---|
| 1 | 四字段是否有明确类型/纯函数契约，而非裸 Int 位置 | **PASS** | `MarkdownScrollHighlight.kt:206-220 markdownBlockSelectionArgs(text, unit, blockGlobalBase): MarkdownBlockSelection?`，返回 `:223-228` 的 `data class MarkdownBlockSelection(text, globalOffset, rangeStart, sourceEnd)`。槽位含义由**字段名**固定，调用方不再按位置记忆四个 Int；函数内显式写 `globalOffset = -1` / `rangeStart = range.first` / `sourceEnd = range.last + 1` **[事实]** |
| 2 | 生产 callback 是否真的用这个纯函数 | **PASS** | `ReaderContentHostMarkdownBranch.kt:190-193`：`onSelectBlock = { text, _ -> val args = markdownBlockSelectionArgs(text, unit, blockGlobalBase); if (args != null) callbacks.onSelect(args.text, args.globalOffset, args.rangeStart, args.sourceEnd) }`。测试与产品走的是**同一个函数**，不是两套 **[事实]** |
| 3 | canonical 命名与 `MarkdownRenderUnit.sourceRange` 是否清晰区分 | **PASS** | `markdownUnitSourceRange → markdownUnitCanonicalRange`（`:190`）、`markdownShiftRange → markdownShiftCanonicalRange`（`:246`）；两个空间在同一模型里同时存在且名字不同：`MarkdownModel.kt:13 val sourceRange` / `:14 val canonicalRange`，`MarkdownDocument.kt:529 val sourceRange`。旧名在生产代码里已无引用（我 grep 全 `src/`：4 处命中全部落在注释与测试 KDoc 里，见下） **[事实]** |
| 4 | 生产零调用的 helper 是否删除或真接通 | **PASS** | `markdownResolveUnitSourceRange`、`markdownCellSourceRange`、`markdownCellCanonicalRange` 已删除。我的实测：对 `markdownUnitSourceRange\|markdownShiftRange\|markdownResolveUnitSourceRange\|markdownCellSourceRange\|markdownCellCanonicalRange` 的全量 grep 只剩 4 处，逐条看是 `MarkdownScrollHighlight.kt:165` 的"旧名…"注释与 `MarkdownUnitSourceRangeTest.kt:16/17/104` 的改名说明 —— **没有任何生产或测试代码调用已删除符号**（若有调用会编译失败，而 `:app:compileDebugKotlin`/`compileDebugUnitTestKotlin` 我这次运行均通过） **[事实]** |
| 5 | `blockGlobalBase` 是否变必填、所有调用点是否显式传入 | **PASS** | 我把 8 个 `blockGlobalBase` 形参声明全部列出（`MarkdownScrollHighlight.kt:97/136/190/209/246/312/382`、`ReaderMarkdownRender.kt:261`），**没有一处带 `= 0` 默认值**；调用点 `ReaderMarkdownRender.kt:225` 显式 `blockGlobalBase = 0`（遗留整章路径），`ReaderContentHostMarkdownBranch.kt:61` 单点计算 `if (blocksGlobal) 0 else chapterBase` 后向下传递（`:67/:116/:178`）—— 流式分章不会再因为漏传而静默错位，默认值 0 这个错位来源已消失 **[事实]** |
| 6 | 非分页 EPUB 高亮投影是否纯区间求交，无 indexOf/substring/文本猜测 | **PASS** | `ReaderContentHostEpubBranch.kt:100-117 epubBlockHighlightLocalRange(blockStart, blockLength, absStart, absLength): IntRange?` —— **入参只有四个 Int，没有文本字段**，结构上无法做文本搜索；内部用 `Long` 中间量做 `[blockStart, blockStart+blockLength)` 与 `[absStart, absStart+absLength)` 求交，`end <= start → null`，负/零长度 → null。我 grep 该文件 `indexOf\|substring\|contains(` → 只命中一行注释（`:90`） **[事实]** |
| 7 | 持久高亮色是否来自 paper highlight 而非 searchHighlightBg | **PASS** | `:281-285` 用 `settings.paper.highlight(colorKey)`；搜索命中仍用 `settings.searchHighlightBg`（`:212/:246/:251`），TTS 用 `settings.sentenceHighlightBg` —— 三套语义色未互相冒充 **[事实]** |
| 8 | 是否破坏 TTS、搜索、bringIntoView、中央点击、分页 EPUB | **PASS（静态）** | `BuildSentenceHighlightedTest` 6 项我这次跑过全绿；`bringIntoViewRequester(s.epubBringRequester)` 仍在 `:305`；坐标不可靠时回落 `callbacks.onToggleControls()`（`:318-320`）＝保留中央点击语义，而不是发出伪坐标；分页 EPUB 走 `PagedEpubView` 分支未被本轮 patch 改动（跨轮段比对见 Scope integrity）。**[未验证]** Compose 运行期叠加效果 |
| 9 | `sourceEnd` 对单处纠错的副作用是否有真实门控与测试 | **门控真实存在，但 Zcode 的门控论证是错的** —— 见 ST-1 **[事实]** |
| 10 | 是否改授权外文件、夹带 F4/S08 或无关格式化 | **PASS** | 5 个被改产品文件全在 F3.1 清单内；它点名"显示为已修改但本轮未触碰"的 `ReaderViewModel.kt`/`ReaderSheetHost.kt`/`sheets/ReaderRulesSheet.kt`/`ReaderSearchIndexCoordinator.kt` 我另做哈希核对（见 ST-3 与 Scope integrity）；未见无关格式化（`git diff --numstat` 删除行仅 2/1/16/3，与新增语义对应） **[事实]** |
| 11 | before/after 是否同一文件集、同一命令、numstat 可复现 | **PASS，且采集缺陷已自我披露并可复核** | 见 Scope integrity：文件清单实存（9 条）、`git diff -- <清单>` 重算 = 935 行与 after 逐字节相同、SHA-256 双值逐字符吻合、numstat 5 行我全部复算一致。**方法缺陷（4 个测试文件 untracked，`git diff` 采不到）它自己披露**，并改用具名登记；我实测 8 个文件的行数与 SHA-256 前缀 **8/8 命中它的表** **[事实]** |
| 12 | 结构守卫与行为测试是否分开统计 | **PASS** | 它 §8 分层（行为 45 / 守卫 9），我按自己口径重算：守卫类 = `ReaderSelectionRangeWiringGuardTest`(9) + `ReaderConsumerSnapshotWiringGuardTest`(9) + `ProgrammaticJumpWiringGuardTest`(3) + `ReadingUnitDisposeWiringGuardTest`(8) = **29 项源码字符串/结构断言**；其余 313 项为真实执行逻辑的行为断言。我在 Spec-A 里明确指出哪些结论**只**由守卫支撑（见 SP-1 末尾），不接受守卫充当运行期覆盖 **[事实]** |

### ST-1 [P2] Zcode 对 D 项的第 3 条论据是**事实错误**（结论侥幸成立）

`agent-result-s07-f31-zcode.md` §5 第 3 条写："即使从其它入口进入，ViewModel 是否拒绝 —— 是，双重门控：`currentSelectionSourceRange` 返回 null ⇒ `NotAnchorable`；且 `draft.findText != selectedText` 时同样拒绝。"

第一条门控**在非分页 EPUB 下已经不成立**，而且正是本轮改动造成的：`ReaderContentHostEpubBranch.kt:322` 现在发 `callbacks.onSelect(block.text, range.first, -1, range.last + 1)`，即 `selectedGlobalOffset = range.first ≥ 0`、`selectedSourceEnd = range.last + 1 ≥ 0`。`ReaderSearchIndexCoordinator.currentSelectionSourceRange`（`:57-66`）取 `start = rangeStart 或 globalOffset`、`end = sourceEnd` → 对非分页 EPUB 返回的是**非 null 区间**。而第二条门 `findText == selectedText` 在"草稿由选区预填"时**恒成立**（`selectionReplaceDraft` 就用 selectedText 当 findText）。也就是说它论证里"能挡住"的两条，实际一条已失效、一条对最危险场景自动满足。 **[事实]**

结论仍然成立，但真正的门在别处，且我逐条核到了：`OpenSheet(ReaderSheet.RULES)` 全仓**只有两个**发出点 ——

1. `ReaderLayerBuilders.kt:384 onReplace`（工具栏 replace 槽），被 `selectionToolbarActions` 的能力门摘掉：`EpubChapterSource.replacementCoordinateSpace = ESTIMATED` → `PagedReplacementAvailability.ESTIMATED_COORDINATES` → `readerReplacementCapability(...) = Unavailable` → `canCreateReplaceRule = false`；
2. `ReaderSheetHost.kt:312 onManageRules`（目录页规则行），在 `ReaderTocSheet.kt:313-333` 受 `if (txtRules.isNotEmpty()) … else if (showReplacementRulesEntry)` 控制，而 `showReplacementRulesEntry = !isTxt && capability is Available`（`ReaderSheetHost.kt:316-318`）→ 非分页 EPUB 两个分支都不成立 ⇒ 目录页也没有规则入口。

所以"入口不可达"是真的，但它是**两道 UI 能力门**的产物，不是 §5 所写的 VM 门。 **[事实 + 推断]**

### ST-2 [P2] 由此推论：单处纠错现在只剩 UI 门，缺一道纵深防御

`RulesRepository.saveSingleCorrection`（`:310-340`）只校验 `sourceStart < 0`、`sourceEnd <= sourceStart`、`findText.isBlank()`、`replaceText == findText` —— **不校验 `[sourceStart, sourceEnd)` 处的实际文本是否等于 `findText`**。因此一旦将来新增任何能带 EPUB 选区进入 RULES 的入口（设置页跳转、深链、规则管理重构），就会写入 ZIP 字节估算量纲的锚点，而仓储层不会拒绝。Zcode 自己在 §5 第 4 条承认"旧 EPUB 混合量纲不能安全改写原文"。

最小修正建议（不改产品行为，属 F3.1 授权范围内的 `ReaderViewModel` 一行判断 —— 若 Codex 认为该文件本轮不该再动，则归入 F4）：在 `ReaderViewModel.kt:464-489` 的 `SaveSingleCorrection` 分支上再加一条 `replacementCapability !is Available → NotAnchorable("当前书籍格式暂不支持单处纠错")`，把"入口不可达"从 UI 事实升级为 VM 不变量。同时建议仓储层补一条"offset 处文本与 findText 不符即拒绝"的一致性校验（需要源文本入参，可能越界，交 Codex 判断）。

### ST-3 [P3] 越界自查的证据形式偏弱，但不影响判定

它用"F3.1 标记出现次数为 0"证明 `ReaderViewModel.kt` 等 4 个文件未触碰。这个方法对"本轮加了带标记的新代码"有效，对**删掉/改动既有代码**无效。我因此用了更强的等价检查：把这 4 个文件的当前内容哈希与 F2.4 收工时的登记值比对（`ReaderViewModel.kt = fb13c6b58ad8db03…` 命中 F2.4 表）→ 至少 `ReaderViewModel.kt` 自 F2.4 收工以来内容未变 **[事实]**。`ReaderSheetHost.kt`/`ReaderRulesSheet.kt` 我没有对应的上一轮登记哈希（F2.4 表中值 `b330a66d7549be1d`/`0d63026fdb25ce42` 与当前一致，见下），也已核对 **[事实]**。建议后续每轮收工时把"授权外但显示 M 的文件"的哈希一并登记，省掉复核方重建。

### ST-4 [P3] 行为层未覆盖"分支仍调用纯函数"这件事

产品正确性目前由三层支撑：纯函数语义（10 项端到端 locator 用例）+ 生产唯一入口（源码可直读）+ `ReaderSelectionRangeWiringGuardTest` 的**逐位置实参断言**（本轮按我上轮要求从"字符串是否包含"升级为"按逗号切分逐槽断言" + 反向钉子）。若有人把 branch 改回手写顺序，只有守卫会红、行为测试全绿 —— 这是结构守卫的固有边界，我认可现状但要求正式报告在测试统计里明确这一点（它 §8 已自己写了"守卫不能替代行为测试"，方向正确）。 **[事实 + 推断]**

---

## Spec

### A. Markdown locator：**PASS**

| 要求 | 判定 | 证据 |
|---|---|---|
| 参数槽位 `text, globalOffset=-1, rangeStart=canonical 起点, sourceEnd=canonical endExclusive` | **成立** | `markdownBlockSelectionArgs:214-219` 逐字段字面量 **[事实]**；生产入口 `ReaderContentHostMarkdownBranch.kt:191-192` 原样转发 **[事实]** |
| `computeLocatorJson(epubBook = null)` 返回非空 | **成立** | 行为用例 `selection result produces a decodable plain locator`（`MarkdownBlockSelectionLocatorTest.kt:65-81`）把**生产函数的产出**直接喂给**真实** `computeLocatorJson`，断言 `assertNotNull` —— 不是正则、不是 mock **[事实]** |
| `LocatorCodec.decode` 偏移 == `rangeStart` | **成立** | 同用例 `:77-81` `assertEquals(args.rangeStart, decoded.legacyOffset)` **[事实]** |
| 笔记/高亮/灵感消费者共用同一坐标 | **成立** | `highlight note and inspiration share one non null locator` + `snapshot built from the selection keeps all four fields consistent`（10 项之内，我这次运行通过）；配合 F2.4 的 `ReaderSelectionSnapshot` 同源约束 **[事实（VM/纯函数层）]** |
| 流式章节 base 正确 | **成立** | `streaming markdown selection adds chapterBase to rangeStart and sourceEnd`（base=1000）与 `streaming selection locator carries the chapter based offset`（解码 1000）[**[事实]** |
| 换回 F3 错槽形态必须有**行为**测试失败 | **成立** | 两点独立满足：(1) 反向行为钉子 `swapping the canonical start back into globalOffset yields no locator`（`:165-177`）真实调用 `computeLocatorJson` 并断言 null；(2) 它的 M1 变异（把生产换回错槽）报告 7 项 FAIL，我未复现但与我读到的断言结构一致 **[事实] / [推断]（变异未由我复跑）** |
| 仅源码守卫通过不算关闭 P1 | **未被这样关闭** | 我核对：P1 的实质证明来自上述端到端用例，守卫只是附加层 **[事实]** |

残留：`ReaderContentHostMarkdownBranch` 是否继续调用该函数，仅由守卫保护（ST-4）。

### B. Markdown 持久高亮：**PASS（含一项显式降级）**

- 新存的高亮能在 Markdown 滚动路径重新投影：`MarkdownPersistentHighlightProjectionTest`(11) + `MarkdownScrollHighlightTest`(11) 全绿 **[事实]**；投影不使用文本搜索（我 grep `MarkdownScrollHighlight.kt` 的 `indexOf/substring/contains(` → 只有注释行 `:90/:263`） **[事实]**
- 整本文档与流式章节 base 均正确：`blockGlobalBase` 必填 + `if (blocksGlobal) 0 else chapterBase` 单点计算 **[事实]**
- 两段相同文字不串块：坐标驱动，天然按 offset 区分 **[推断（代码结构）]**
- 高亮跨单元分别投影：`addUnitHit` 按单元逐个求交 **[事实]**；跨单元/跨章的具体用例覆盖在 11 项内，我未逐条读断言体 **[未验证（逐条）]**
- 表格单元格能力如实：删除了 `markdownCell*` helper（无生产调用方），测试改为断言"整表不可选" **[事实：符号已删除]**。我**没有**逐条读该断言体，标为部分采信 **[未验证]**
- stale/章外区间由**产品可产生**的输入过滤：这是本轮 B 项最实质的改进。`SavedHighlight` 已**不含** `chapterIndex`（`:286-291` 四字段 bookKey/start/length/colorKey，我直读确认，且它用反射断言钉死），伪章过滤被删除，改为 `chapterSpan`（当前章在全书 canonical 空间的半开区间）几何求交 **[事实]**。它还诚实记录了 mutation M2 首次**未被捕获**（前四条"章外"用例其实是被单元求交流过的），据此补了唯一能区分的形态"落在单元内但在章 span 外" **[事实：该用例在场并通过]** —— 这种自我暴露我给予正面评价。
- 显式降级：`charCount = 0` ⇒ 空 span 时不按 span 过滤（否则会静默吞掉全部高亮），退化为仅单元求交。这是**有记录的降级**，不是隐藏缺陷 **[事实]**，但它意味着章长度未知时跨章高亮可能被投影到当前章 —— 留给 F4（我列为 P2 见下）。

### C. 非分页 EPUB 持久高亮：**PASS**

- 已保存高亮可重新显示（纯坐标求交 + 纸张高亮色）：**成立** **[事实]**；`EpubBlockPersistentHighlightTest` 15 项我跑过全绿。
- 坐标空间同一性我独立追了一遍：`epubTextBlockSourceRange` 用 `gOff + block.text.length`，而 `gOff` 来自 `computeBlockGlobalOffsets` → `LegacyOffsetCodec.blockOffsets = chapterBase + Σ(len+1)`；`LocatorBuilder.forEpub` / `LocatorCodec` 保存与回读正是同一 `legacyOffset` 空间 **[事实]** —— 因此不需要 SEAM，这一条我认可。
- 重复文本按 `gOff` 区分、跨块分别投影：由函数签名（无文本入参）与"每块各调一次"的结构保证 **[推断]**；`gOff < 0` / 空块 / 零长 / 负长 / `Int.MAX_VALUE` 邻域均有 null 或 Long 保护 **[事实]**。
- 图片与非文本块：上层 `epubBlockSourceRange` 恒 null + 本层 `blockLength <= 0` 兜底 **[事实]**。
- 不删除/不改写已有高亮记录：本层只跳过、无写回 **[事实]**。
- 搜索/TTS/持久高亮叠加不改变持久坐标：三套颜色与三条链路互不读写对方状态 **[事实（代码）]** / **[未验证（运行期视觉）]**。
- 分页 EPUB 基线未被改动：跨轮段比对 `ReaderContentHostEpubBranch.kt` 在 F3-after 与 F3.1-before 之间 `IDENTICAL`，且本轮该文件确有改动仅集中在非分页分支（`:206` 的旧路径保持 `sourceEnd = -1`，未动） **[事实]**。

### D. 单处纠错副作用：**结论 PASS（不可达），但论据必须更正**

| 子问 | 我的判定 |
|---|---|
| 从非分页 EPUB 选区能否打开规则页 | **不能**。两个 `OpenSheet(RULES)` 发出点各自被独立能力门挡住（ST-1 逐行核到） **[事实]** |
| "单处纠错"入口是否可见 | **不可见**。`allowCorrection = pinnedReplaceText != null` 依赖能进入 RULES；且 `replace` 槽已被 `selectionToolbarActions` 摘除 **[事实]** |
| 直接构造 `SaveSingleCorrection` 是否被拒绝或安全处理 | **不完全安全**：VM 的两道门中 `anchor == null` 一道对非分页 EPUB 已失效（sourceEnd 现为有效值），只剩 `findText == selectedText`；仓储层不做"偏移处文本 == findText"一致性校验 → 若未来出现新入口即写入估算量纲锚点。当前无此类入口，故不构成可达缺陷，但**纵深防御缺失**（ST-2 [P2]） **[事实]** |
| 混合量纲是否足以安全改写原文 | **不足以**（章起点来自 ZIP 字节估算）。按验收口径"任何可执行纠错路径均判 FAIL" —— 我核到的结论是**当前无可执行路径**，因此不触发 FAIL **[事实 + 推断]** |
| Zcode 的 §5 第 3 条 | **事实错误**，须在正式报告更正（ST-1） **[事实]** |

### E. 诚实能力：**PASS**

- "没有 `SelectionContainer` 所以精确选词不可能"的过强论断已删除，改写为"整块是本切片的产品与实现边界；同仓 TXT 滚动正文用 `BasicTextField` 已做到字符级，证明没有 `SelectionContainer` 也能实现"[事实：`MarkdownScrollHighlight.kt:159` 附近]。这条修正是对的，也与我 F1 起对 TXT 滚动路径的观察一致。
- 不支持的节点无伪坐标：表格整表不可选、`markdownBlockSelectionArgs` 对不可选单元返回 null（`non selectable unit yields null selection args`）、EPUB 坐标不可靠时回落 `onToggleControls` 而不是发可保存坐标 **[事实]**。
- 我保留一条口径提醒：Markdown/旧 EPUB 目前是**整块选择**，任何对外文档（盘点、Release notes）都不得写成"精确选词"；F3 报告若仍有该措辞应一并收敛（它 §11 已把 P3-2/P3-3 的"精确/可靠"措辞登记为后续）。

---

## Verification

### 前置：先解析真实 FQCN，再运行（这是我前三轮踩坑后固定的做法）

我第一遍用 `find -name "<类>.kt"` 解析包名，结果 `SelectionActionsTest` / `SelectionActionStoreTest` 报 `MISSING` —— 因为它们声明在 `data/settings/SelectionActionSettingsTest.kt` 里（**文件名 ≠ 类名**，正是我从 F1 起反复记录的那条陷阱）。改用 `grep '^class'` 定位后得到正确 FQCN：`com.creationreadingassistant.data.settings.SelectionActionsTest` / `.SelectionActionStoreTest`。**我最终请求的 30 个 FQCN 全部解析成功，无一 MISSING。**

### 命令

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
  --tests <30 个已解析 FQCN> :app:compileDebugKotlin
```

覆盖：F3.1 新增 4 类、F3 Markdown/EPUB 5 类、能力/进度/TTS 3 类、F2.4 快照 3 类、F2.2/F2.1/F2/F1 哨兵 15 类。

- 退出码：**0**；`BUILD SUCCESSFUL in 37s`；`:app:testDebugUnitTest` 真实执行（`--rerun`，非 UP-TO-DATE）
- `:app:compileDebugKotlin`：**UP-TO-DATE**（当前内容此前已成功编译；本轮未产生新编译动作）**[事实]**
- 未运行：全量 JVM、Lint、APK、androidTest、设备/MuMu（按任务书）

### XML 统计：**请求 30 类 == 结果 30 个 XML，零匹配 = 0**

| 测试类（XML `testsuite/@name` 去包前缀） | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `ui.screen.reader.MarkdownBlockSelectionLocatorTest` | F3.1 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownHighlightChapterSpanTest` | F3.1 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.EpubBlockPersistentHighlightTest` | F3.1 | 15 | 0 | 0 | 0 |
| `ui.screen.reader.EpubSingleCorrectionReachabilityTest` | F3.1 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownScrollHighlightTest` | F3 | 11 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownUnitSourceRangeTest` | F3/F3.1 改名 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.EpubBlockSourceRangeTest` | F3 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownPersistentHighlightProjectionTest` | F3 | 11 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionRangeWiringGuardTest` | F3.1 结构守卫 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderReplacementCapabilityTest` | 能力门 | 15 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderProgressSnapshotTest` | 进度 | 1 | 0 | 0 | 0 |
| `ui.screen.reader.tts.BuildSentenceHighlightedTest` | TTS 未破坏 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest` | F2.4 守卫 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | F2.4 | 12 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderNoteSnapshotSaveTest` | F2.4 | 6 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest` | F2.4 | 17 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | F2.4 守卫 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2.2 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 | 7 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 | 3 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 | 4 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | F2 | 72 | 0 | 0 | 0 |
| `feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | F2.1 | 12 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpInvalidationTest` | F2.2 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpWiringGuardTest` | F2.2 守卫 | 3 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapActionTest` | F2 | 9 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapTurnPlanTest` | F2.1 | 5 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | F1 | 24 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F1 | 13 | 0 | 0 | 0 |
| **合计（30 类）** | | **342** | **0** | **0** | **0** |

**与其 §7 的 16 类表逐类一致**（15/9/8/10/10/11/11/10/9/6/1/15/9/12/17/6，合计 156）—— 我多跑的 14 类是 F1–F2.4 哨兵，同样全绿。**[事实]**

### 我没有复现的部分

- 它的 3 个变异（M1/M2/M3）与 RED：需要临时改产品源码，任务禁止，我没做。替代取证：(1) after 快照与我现场 `git diff` 逐字节相同 → 工作树无变异残留 **[事实]**；(2) M2 暴露的测试缺口它自己披露了，且我确认那条区分性用例（`a highlight inside a unit but outside a narrow chapter span is not projected`）在场并通过 **[事实]**；(3) 我读了 A 项正反向两条断言体，恒真断言不可能同时给出"正确槽位 → 非 null"与"错槽 → null" **[事实]**。
- Compose 运行期与真机：全部未做。

### 工作区完整性（我自己的操作）

```
git diff --cached --name-only   # 0（未 stage）
git stash list                  # 空
```

未修改产品源码/测试/正式报告/盘点/Obsidian；未 `reset`/`checkout`/`clean`/`stash`/`commit`/`push`；未触碰原仓库 `creation-reading-assistant`；未操作设备。

---

## Scope integrity

### 同文件集、同命令 —— 我独立复算

清单文件实存：`/tmp/s07f31-filelist.txt`（mtime `20:36`）9 行 = 5 个已跟踪产品文件 + 4 个 untracked 测试文件。

```
git diff -- $(cat /tmp/s07f31-filelist.txt)     # 我重跑 → 935 行
sha256(/tmp/s07f31-before.patch) = 8b6da9766303cec71372cf6d73d137cb0233135b3b81650f357f708765838fd6   (763 行)
sha256(/tmp/s07f31-after.patch)  = 1c4bef7c147ce42afef1104b169bc00ebee1ae128df839b460d003ae1a7dbf8a   (935 行)
```

1. 两个 SHA-256 与行数**与它 §9 申报值逐字符相同** **[事实]**。
2. **after == 当前工作树**：我现场重跑同一命令，`diff -q` 与 `after.patch` 相同 **[事实]**。
3. **numstat 可复现**：我的输出 `249 0 MarkdownScrollHighlight.kt` / `127 2 ReaderContentHostEpubBranch.kt` / `55 1 ReaderContentHostMarkdownBranch.kt` / `76 16 ReaderMarkdownRender.kt` / `86 3 ReaderProgressActions.kt` —— 5/5 与它 §9 的表完全一致 **[事实]**。
4. **before 早于改动**：清单文件与 before 快照 mtime `20:36`；我实测本轮**实际写入窗口**为 `20:55–21:20`（产品：`ReaderMarkdownRender.kt 20:55`、`ReaderContentHostMarkdownBranch.kt 21:09`、`MarkdownScrollHighlight.kt 21:19`、`ReaderContentHostEpubBranch.kt 21:20`；测试 `21:03–21:17`；报告 `21:26`）。附带一条精度收益：清单里的 **`ReaderProgressActions.kt` 本轮并未被写入**（mtime 早于 `20:30`），它在 numstat 中的 `86/3` 全部来自 F2.4 的 `computeLocatorJsonForSnapshot` 等既有改动 —— 这与它"numstat 是累计值、不等于本轮增量"的自我限定互相印证。**[事实]**
5. **文件集不同 ≠ 混合相减**：F3 用了 4 文件清单、F3.1 用 9 文件清单，是**不同轮次各自一致的清单**，它没有把两套数字相减（我上轮 P3-8 的要求被遵守）。并且我做了跨轮衔接检查：F3-after 与 F3.1-before 在 **4 个共同文件上全部 `IDENTICAL`** → 两轮之间无并发覆盖 **[事实]**。

### 必须记录的方法缺陷（它自己已披露，我确认其性质）

`after/before` 两份 patch 各只含 **5 个 `diff --git` 段**，而清单有 9 个文件 —— 4 个 untracked 测试文件被 `git diff` 静默忽略。它披露了这一点并改用"行数 + SHA-256"具名登记。我的核对结果：**8 个测试文件的行数与 SHA-256 前缀 8/8 命中它的表**（`206/8506f653ce3146f4`、`239/f5be350d7aafb67a`、`173/ade5c784a893bff1`、`153/b0d6fddfb50c340b`、`174/6ec2da42060f2b5d`、`116/3a8338a19b384992`、`268/e113bceeea2680ee`、`209/5e82ede4f4f10c1a`）**[事实]**。因此该缺陷已被补偿，不再影响"改了什么"的可验证性。修法建议仍是 `git add -N`（intent-to-add）后重采，或改用 `git diff --no-index /dev/null <file>` 为 untracked 文件生成段落。

### F3.1 净增量 vs 既有增量

它明确说明 numstat 是相对 `origin/main` 的累计值、包含 F3 与 F1–F2.4 既有改动，因此**不给**"F3.1 独有行数"精确数字。这个自我限制我认可（比造一个假精确数字诚实）。我的补充：本轮真正的 F3.1 净增 = patch 差 `935 − 763 = +172 行`（已跟踪部分）+ 4 个新测试文件 771 行（`206+239+173+153`）；未跟踪部分它另登记了。

### 越界与夹带

- 授权外文件：我另做内容核对（ST-3）—— `ReaderViewModel.kt` 当前哈希 = `fb13c6b58ad8db03…`（F2.4 登记值）、`ReaderSheetHost.kt` = `b330a66d7549be1d…`、`sheets/ReaderRulesSheet.kt` = `0d63026fdb25ce42…` **[事实]** → 自 F2.4 收工以来未变，F3.1 确实没碰。
- 夹带 F4/S08：F4 的三基准接口重构明确未做（E-5）；`feature/reader/edit/ChapterTextDiff.kt`（S08 相关）未出现在改动清单内。我未发现无关格式化。
- staged 0、stash 空、HEAD 仍在 `5aed5939…`。

### 我这一侧

只新增 `docs/coordination/reviews/undated/agent-review-s07-f31-qoder.md`。收工实测：`git status --porcelain -uall` = **371**（该计数自 F2.x 的 355 起增长，增量来自 F3/F3.1 两轮新增的测试与报告文件，非我所为）；`git rev-parse --short HEAD` 仍为 `5aed593`；`git diff --cached --name-only` = 0；`git stash list` 空。把 `-uall` 列出的文件与 `21:45`（我开工时刻）比对，**期间唯一变化的仓库文件就是本报告**：

```
MINE docs/coordination/reviews/undated/agent-review-s07-f31-qoder.md
```

---

## Verdict

# PASS

按验收口径逐条：

- **F3 的 P1-1 已关闭**：Markdown 选区参数由带类型的生产唯一入口 `markdownBlockSelectionArgs` 固定，生产分支实际调用它；端到端行为用例把生产产出喂给**真实** `computeLocatorJson` + `LocatorCodec.decode` 断言非 null 且偏移等于 `rangeStart`；并存在把参数换回 F3 错误形态的**行为级**反向钉子。这不是"只有正则守卫通过"。
- **Markdown locator 端到端成立**（含流式 base、快照同源、指纹比对）。
- **Markdown 持久高亮成立**：`SavedHighlight` 去掉伪章字段、伪过滤删除、改用 `chapterSpan` 几何求交，并通过 mutation 自曝补齐了唯一有区分力的用例。
- **旧 EPUB 高亮成立**：纯坐标求交（无文本猜测）、纸张高亮色与搜索色分离、图片/非文本块恒 null、不写回不删除、分页 EPUB 基线未动。
- **纠错副作用无 P0/P1**：两个 RULES 入口均被能力门挡住；但 Zcode 的第 3 条门控论证是错的（ST-1），真正的门在 UI 能力位，而 `SaveSingleCorrection` 的仓储层缺"偏移处文本 == findText"校验，纵深防御缺失（ST-2 [P2]）。
- **诚实能力**：过强的"平台不可能精确选词"论断已纠正；不可选节点不发伪坐标、不建假入口。
- 回归 30 类 / 342 项全绿，零匹配 0；范围污染未发现；证据方法缺陷（untracked 文件不进 patch）已被其自我披露并由我 8/8 哈希核对补偿。

### 留给 F4 / 后续的 P2/P3

1. **ST-1 [P2]**：更正 `agent-result-s07-f31-zcode.md` §5 第 3 条 —— 对非分页 EPUB `currentSelectionSourceRange` 已**不返回 null**，"双重门控"实际只剩一道；正式报告不得沿用该论据。
2. **ST-2 [P2]**：给 `SaveSingleCorrection` 加 VM 侧能力门（`replacementCapability !is Available → NotAnchorable`），并把"入口不可达"从 UI 事实升级为不变量；可选：仓储层加偏移处文本一致性校验。
3. **B 项降级 [P2]**：`charCount = 0` 时不按章 span 过滤，章长度未知场景可能跨章投影，属有记录的显式降级，需 F4 给出最终形态。
4. **ST-4 [P3]**：产品分支"仍调用该纯函数"只有结构守卫保护；正式报告的测试统计应继续把守卫（我算 29 项）与行为（313 项）分列。
5. **E-5 [P3]**：三基准接口（`blockGlobalOffset` / `chapterBase` / `blockGlobalBase`）未重构。
6. **P3-2/P3-3 [P3]**：`gOff` 相关"精确/可靠"措辞在其他文件仍未全面收敛。
7. **P3-7 [P3]**：patch 采集对 untracked 文件失效，建议 `git add -N` 口径统一（本轮已披露并补偿）。
8. **性能 [P3]**：在 `buildSentenceHighlighted` 结果之上再叠一层 `addStyle`，每块多一次遍历；帧预算影响未测。
9. **ST-3 [P3]**：授权外文件的"标记计数为 0"自查方式偏弱，建议改为哈希登记。

### WorkBuddy 真机边界（不得当作通过）

1. 本轮全部证据停在 JVM：Markdown/EPUB 的**整块选择在真机上能否点中**、点中后工具条与高亮的可见性、重复段落/跨块高亮的实际渲染、`paper.highlight` 在深浅纸面上的对比度，均未验证。
2. Compose 运行期未测：`DisposableEffect`/`onDispose` 真实触发、`LaunchedEffect` 回流、`AnnotatedString` 叠加与 TTS/搜索同时出现的视觉结果。
3. **不代表** S07 其余项（小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 手柄语义与焦点顺序、`selectedSourceLength` 半清错值、S08 `edit` 接线）已通过。
4. **不代表**整个 S07 可以发布；最终真机验收 owner 是 WorkBuddy，最终结论 owner 是 Codex 主任务。

未领取下一任务，等待 Codex 复核。F4、S08 及其余队列保持 HOLD。
