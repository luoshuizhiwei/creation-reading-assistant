# 阅读器选区增强设计：编辑保存、工具栏顺序、小光标与选词粒度

> 日期：2026-09-15
> 参考项目：[HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)（下称 md3）
> 状态：**S1 已实施**；S2 / S3a / S3b 待做（方案阶段）。

## 0. 决策与实施状态（2026-09-15 更新）

| # | 议题 | 决策 |
|---|---|---|
| 1 | 高频槽上限 | **5** |
| 2 | 老配置处理 | **自动升级**：仅"从未改过"（存的是 3 槽历史默认）的用户升到新默认；自定义过的原样保留 |
| 3 | 跨页选择 | 一期**不做**（范围限制，原理见 §6） |
| 4 | S3b 写回源文件 | **要做**，对齐 md3；排在 S3a 之后 |

### S1 已实施（2026-09-15）

- `MAX_PRIMARY_SLOTS` 3 → **5**。
- `ALL` 声明顺序重排为 **复制 → 浏览器 → 替换 → 编辑 → 字典 → 高亮 → 批注 → 书内搜索 → AI → 灵感 → 取消**，
  并新增 `edit` 动作定义。
- **「编辑」保持静止**：它不在 `DEFAULT_PRIMARY` 也不在 `DEFAULT_MORE`，因此不会渲染 ——
  在 S3a 编辑器接线前绝不放点了没反应的死按钮。接线后只需把它加进 `DEFAULT_PRIMARY`，
  落点已由声明顺序锁定（在「替换」与「字典」之间）。
- 新增 `resolveStoredPrimary()` 实现自动升级，`SelectionActionStore` 读路径改走它。
- 定向测试 **35 tests / 0 fail / 0 err / 0 skip**（原 29 → 35）。
- **待真机验收**：第一屏 5 个胶囊 + 固定「更多」= 6 等分，单格从约 97dp 降到约 64dp，
  「浏览器」等 3 字标签有挤压风险；若不可点则退回 4 槽、把「字典」留在溢出菜单。

### S2 已实施（2026-09-15）

**小手柄**：`LocalTextSelectionColors` 的 `handleColor` 设为 `Transparent`（**背景色原样保留**，
只藏手柄），再用 `Box(Modifier.drawWithContent)` 按 `TextLayoutResult.getCursorRect()` 在选区
起止各画一个「小圆球 + 短竖柄」（球半径 5dp、柄宽 1.6dp）。自绘与系统手柄落在同一坐标。

**长按选词**：新增不消费事件的旁听器 `readerTextFieldLongPressAnchor`（复用既有
`ReaderTapToggleGate` 那套"只旁听、不抢手势"的模式）记下长按位置；等 BasicTextField 把初始选区
报上来后，用纯函数 `wordRangeAt()` 把**首次**选区收敛到该位置的词。中文被判成整句（>8 字）时
退化为单字 —— 宁小勿大，用户拖一下就能扩选。拿不到锚点时原样保留，优雅退化到现状。

- 新增 `ReaderSelectionGranularity.kt`（纯 JVM）+ `ReaderSelectionGranularityTest.kt`（8 tests）。
- 改 `ReaderTapObservation.kt`（加旁听器）、`ReaderContentHostPlainTextBranch.kt`（接线 + 自绘）。
- 验证：`compileDebugKotlin` **BUILD SUCCESSFUL**；选区粒度测试 **8 tests / 0 fail**。

> ⚠️ **必须在真机确认的唯一假设**：系统手柄设为透明后**是否仍可拖动**。
> 本方案的交互完全依赖它（自绘小球只是视觉，不接管手势）。若真机上发现拖不动，
> 退路是给自绘手柄加自己的 `detectDragGestures`（把拖动位置经 `getOffsetForPosition` 换算成
> 偏移后改写 selection），工作量不大但需另补一轮验证。

### S3a 进展（2026-09-15）

**先确认了一件关键事实：R4 纠错链路是通的**，不必新造写入通路。
- `RulesRepository.saveSingleCorrection` 已实现（校验锚点区间 + `findText` 非空白 + 与原文不同）；
- 生效纠错以锚定规则（`id` 前缀 `correction:`、`position = Int.MAX_VALUE`）追加在普通规则**之后**，
  即"普通规则先应用、纠错按 source 锚点最后叠加"；
- `ReaderViewModel` 的 `ReaderAction.ExecuteRuleCommand` 通路会自动补 source 锚点、校验
  `draft.findText == selectedText`，并在写入成功后按 `searchIndexRefreshPlan` 刷新搜索索引。
- **编辑器要复用的是这条通路，而不是另建一条。**

**S3a-1（已完成并验证）：差异引擎**
`feature/reader/edit/ChapterTextDiff.kt`（纯 JVM）：
按行切段 → 段级 LCS 对齐 → 空隙即改动块 → 块内字符级公共前后缀裁剪。
**关键约束处理**：纠错表的落库前提是 `sourceEnd > sourceStart` 且 `findText` 非空白，
因此**纯插入必须归一化成 1 个字符的替换**（左锚点优先，左邻是空白/行首则改取右锚点），
两侧都拿不到非空白锚点的块直接丢弃——宁可不做，也不产出无法落库的脏数据。
超过 `MAX_BLOCKS = 200` 块判为 `TooLarge`，由上层提示改用替换规则。
测试 `ChapterTextDiffTest`：**13 tests / 0 fail**，核心不变式是"差异块应用回原文必须逐字还原编辑后文本"。

**S3a-2（待做）：编辑器 UI 与接线**
1. `ReaderSheet` 增 `CHAPTER_EDIT`；新建 `ChapterTextEditorSheet`（对齐 md3 截图：顶行 撤销·章节标题·保存，中部可编辑正文）。
2. `ReaderAction` 增批量写入（编辑器一次产生多块）——现有 `ExecuteRuleCommand` 只处理单条，
   需新增一条批量路径：逐块建 `SaveSingleCorrection(sourceStart = 章节source起点 + block.localStart, …)`。
3. 需要章节在全书中的 **source 起点**（阅读器已有 `content.sourceStartAbs`，需引到 ViewModel）。
4. `SelectionActions.DEFAULT_PRIMARY` 追加 `"edit"` → 第一屏正好占满 5 槽。
5. **待定**：>200 块时的用户提示文案；撤销按钮语义（仅丢弃未保存编辑，还是同时回滚已保存纠错）。

#### S3a-2 取证结论（2026-09-15，全部有源码依据）

**① `findText` 必须是显示文、锚点必须是全书 source 坐标 —— 方向相反，不能混。**
`ReplaceProjection.overlayAnchored`（`ReplaceProjection.kt:179–186`）把 source 锚点经累积映射换算到 display，
再比 `currentDisplay.substring(d0, d1) == findText`；未命中才在有界窗口内精确回查，仍无则**诚实跳过**。
所以编辑器应 diff **显示文**，再把每个块的 display 偏移经 `localDisplayToGlobalSource` 换成 source 锚点。

**② 不能复用 `ExecuteRuleCommand` 做章节编辑。**
`ReaderViewModel.kt:411–437` 对该命令会**用当前选区覆盖**命令里的锚点，并校验 `draft.findText == selectedText`；
章节编辑的锚点来自章节而非选区，走这条路会被覆盖。必须新增独立动作。

**③ 索引刷新不用另写。**
`searchIndexRefreshPlan`（`ReaderViewModel.kt:501–513`）对 `SaveSingleCorrection` 已返回
`SearchIndexRefreshPlan(global = false)`（重建本书索引）。批量保存只需在循环后**调用一次**
`applySearchIndexRefresh`，不要每条调一次（会重复重建）。

**④ ⚠️ "整章"在两种阅读模式下含义不同（会改变产品预期）。**
- 流式/滚动分支按 **ReadingUnit** 渲染（`ReaderContentHostPlainTextBranch.kt:131–135`），
  且 `ScrollingTxtChapterSource` 的 `loadChapter(index)` 索引是 **unitIndex** 而非逻辑章；
- 分页路径才有真正的章节级能力：`PagedChapterSource.loadChapterText(index)`
  （`PagedChapterSource.kt:50–52`）与 `TxtPagedController.localDisplayToGlobalSource(chapterIndex, offset)`
  （`TxtPagedController.kt:94/107/118`）；
- 模式由 `readerSettings.readerMode == "paged"` 决定（`ReaderPagerEngineState.kt:156`）。

⇒ 因此 v1 的编辑范围取**"当前渲染单元"**（分页模式下即逻辑章，滚动模式下为一个有界单元），
而不是承诺"永远是整章"；否则需要在滚动路径额外实现跨 unit 的整章聚合，属另一档工程。

**⑤ 锚点信息只存在于 UI 层，ViewModel 拿不到。**
`ScrollUnitProjection`（`ScrollUnitProjection.kt:11`，`sourceStartAbs = unit.charStart`）挂在
`ReaderContentHostState.source` 上，是 Compose 层对象；`ReaderViewModel` 中 grep 不到任何
`sourceStartAbs` / 章节文本。故与既有 `onSelect(text, globalOffset, rangeStart, sourceEnd)` 同构：
**UI 层解析好锚点后上抛，ViewModel 只负责执行**。

---

## 1. 调研结论

### 1.1 md3「编辑」功能的事实（截图 + 用户口述）

- 入口：选中文字 → 文本选择工具栏 → 「编辑」按钮。
- 形态：**整章文本编辑器**，底部弹层。顶行 = 撤销（↺）· 章节标题 · 保存（💾）；中部 = 可编辑的整章文本（保留段首缩进排版）；底部 = 复选框
  **「保存到源文件，保存后重置功能将无效」**。
- 语义是**两层修改模型**：
  - 不勾选 → 存入"修改层"，可被「重置」撤销（恢复原文）；
  - 勾选 → 直接写回**源文件**，此后「重置」功能失效（因为原文本身已变，无处可重置）。

### 1.2 md3 选区体系（Legado 血统，已实证部分 + 口述部分）

| 能力 | 状态 | 说明 |
|---|---|---|
| 长按起选 | 已实证 | `ContentTextView` 坐标定位 |
| 拖拽扩选 | 已实证 | 拖动选区边界 |
| BreakIterator 词边界 | 已实证 | 长按自动收敛到**词**，不是整段 |
| 跨页选择 | 已实证 | 选区可跨页边界 |
| 光标/手柄 | 已实证 | View 自绘**小手柄**（Legado `ContentTextView` canvas 绘制） |
| 工具栏顺序 | 用户口述 | 复制、分享、浏览器、朗读、书签、字典 |

> ⚠️ md3 是 **View 自绘选区**，CRA 是 **Compose**。md3 的小光标**不能照搬**——Compose 的选区手柄
> 大小/形状没有公开 API，可调的只有 `LocalTextSelectionColors` 的**颜色**（`handleColor`/`backgroundColor`）。

### 1.3 CRA 现状（已逐条核对源码）

| 项 | 现状 | 位置 |
|---|---|---|
| 选区载体 | `BasicTextField(readOnly = true)` 承载整页文本 | `ReaderContentHost.kt`、`ReaderContentHostPlainTextBranch.kt:296–315` |
| 工具栏 | `ReaderSelectionToolbar`，`ReaderInteractionLayer.kt:246` 触发 | 配置：`SelectionActionSettings.kt` + `SelectionActionStore.kt` + Profile `SelectionSubPage` |
| 高频槽上限 | `MAX_PRIMARY_SLOTS = 3`（硬上限，超出截断） | `SelectionActionSettings.kt:50` |
| 渲染顺序 | **恒等于 `ALL` 的声明顺序**（`enabledPrimary` 是 `Set`，按声明序过滤） | `:96` `effectivePrimary()` |
| 现有动作 | 高亮/浏览器/复制（P）、字典/添加批注/替换/书内搜索/AI 解读/记为灵感（M）、取消选择（F） | `:64–75` |
| 「编辑」动作 | **不存在** | — |
| 修改层 | **R4 锚定单条纠错已就绪**：`reader_text_corrections`（`source_start/end` 全书坐标、`find_text` 校验、`replace_text`、`status=ACTIVE/UNDONE`），注释明言"原文件永不修改，纠错只是投影层覆盖叠加，撤销即恢复原文" | `ReaderCorrectionEntity.kt` |
| 写回源文件 | **无任何通道** | — |
| 长按粒度 | 中文无空格 → `BasicTextField` 内部分词粒度过大，一选一大片 | 用户已证实 |
| 「全选」 | 只在书架批量选择（`BookAction`/`ShelfAction.SelectAllVisible`），阅读器选区无全选 | — |

---

## 2. 方案：三个独立切片

### S1 工具栏动作与默认顺序（低风险，先行）

**目标顺序（用户指定，4–6 个）：复制、浏览器、替换、编辑、字典**，其余收入「更多」。

| 改动 | 位置 | 说明 |
|---|---|---|
| 新增 `edit` 动作 def | `SelectionActions.ALL` | label「编辑」，description「编辑本章正文」 |
| **重排 `ALL` 声明顺序** | `:64–75` | 复制 → 浏览器 → 替换 → 编辑 → 字典 → 高亮 → 书内搜索 → AI 解读 → 记为灵感 → cancel(FIXED)。渲染顺序恒等于声明序，**重排声明序 = 改默认顺序**，无需把 `Set` 改成 `List`（用户已明确不需要"顺序可调"） |
| `MAX_PRIMARY_SLOTS` 3 → **5** | `:50` | 风险：小屏挤成不可点。缓解：胶囊内边距/字号微调；真机验收兜底 |
| `DEFAULT_PRIMARY` 改为 5 个 | `:77` | `{copy, browser, replace, edit, dictionary}` |
| `sanitize()` 兼容旧配置 | `:134` | 老用户已存的 3 槽配置是否自动升级为 5 槽 → **需产品决定**（建议：已显式配置过的保留，未动过的升级） |
| `SelectionActionStore` | `:63` 写入校验 | 同步上限 5 |

- **测试**：`SelectionActionSettingsTest` 更新（顺序断言、上限 5、旧配置兼容）。
- **真机**：c49ac6cf 小屏 5 胶囊可点、无截断；深浅主题。
- **seam**：DataStore（`SelectionActionStore`）、无 Room、无导航变更。

### S2 小光标 + 选词粒度（中风险）

> md3 的小光标是 View 自绘；CRA 在 Compose 下采用**方案 C：保留 `BasicTextField` 的选区逻辑，接管手柄绘制与长按手势**。

| 子项 | 做法 |
|---|---|
| 隐藏系统大手柄 | `LocalTextSelectionColors(handleColor = Color.Transparent, backgroundColor = 正常)` —— 只藏手柄，保留选区底色 |
| 自绘小手柄 | 拿 `TextLayoutResult` 的 `getSelectionPath`/`getCursorRect` 得到起止坐标，`Canvas` 画**小圆点手柄**（尺寸对齐 md3 观感，约 14–16dp） |
| 拖拽扩选 | `pointerInput` 命中手柄热区 → 拖动更新 `TextFieldValue.selection`（选区本就是受控状态，`:298–300` 已具备） |
| **长按选词（解决"直接全选"）** | 拦截长按 → `java.text.BreakIterator.getWordInstance()` 在触点坐标求词边界 → 主动 set selection 为该词。中文按词典式分词粒度收敛，不再整片吞 |
| 跨页选择 | **一期不做**（每页独立 `BasicTextField` 组件，跨组件选区是另一量级工程），作为范围限制明示 |

- **风险**：① 与搜索高亮/句子高亮/持久高亮的 `AnnotatedString` 叠加层不冲突（手柄是独立 Canvas 层）；② 翻页/章节切换时选区重置（现逻辑已按 `content.sourceStartAbs` remember，天然重置）；③ TalkBack/无障碍（自绘手柄需补语义描述）。
- **测试**：BreakIterator 中文选词 JVM 测试；选区 display 偏移 → `localDisplayToGlobalSource` 往返断言（复用既有映射，铁律不许动）。
- **真机**：长按选词、拖拽跟手、手柄大小观感、深浅主题、与高亮叠加。
- **seam**：无 Room、无导航、无同步协议；仅 `ReaderContentHost*` + `ReaderInteractionLayer`（**切片 2 Reader 所有权，需排期协调**）。

### S3 章节编辑与保存（分两步，风险分层）

#### S3a 编辑存"修改层"（低风险，**零 Room 变更**）

完全对齐 md3 截图的默认行为（不勾选复选框）：

- 工具栏「编辑」→ 底部弹层**整章编辑器**：顶行 撤销 · 章节标题 · 保存；中部可编辑文本；底部复选框（见 S3b）。
- **保存 = diff → 锚定纠错记录**：对「编辑后文本 vs 当前 original 章文本」做差异块拆分，每块生成一条
  `reader_text_corrections`（全书 source 坐标区间 + find_text 校验 + replace_text）。
  多条纠错互不漂移（都锚定 source 坐标，REPLACE 只改 display 长度），且天然可重置（`status=UNDONE`）——
  **正是 md3「重置功能」的 CRA 对应物，复用既有撤销语义**。
- 保护：差异块数超阈值（如 200）时提示"修改过大，请用替换规则或重试"。
- 编辑器编辑的文本基准：**original 通道**（纠错作用于其上的投影层，替换规则仍可叠加显示）。
- **测试**：diff→纠错块拆分的 JVM 测试（含删除/新增/跨段）；叠加投影往返（编辑后显示 = 编辑器所见）。
- **真机**：小改一处保存 → 显示生效 → 重置恢复；章节切换/重启后纠错仍生效。

#### S3b 写回源文件（**高风险，独立决定后再做**）

对齐 md3 复选框「保存到源文件，保存后重置功能将无效」：

- 目标：重写**本书私有正文副本**（`filesDir/books/<id>/` TXT/MD）——**不碰 SAF 用户原始文件**（AGENTS 红线：不得改用户源文件）。
- **代价（md3 文案已在承认）**：写回改变全书字符偏移 → 该书**进度、书签、高亮、笔记、R4 纠错、搜索索引、TTS 位置全部失真**。
- 必须配套：
  1. `SearchIndexRepository.invalidateSweepForFullRebuild()` 全库重扫（该卡片能力已存在）；
  2. 锚点数据处置策略 —— **需产品决定**：(a) 失效清空，(b) 仅保留本章之前的、之后的作废并提示，(c) 记录偏移增量重映射（工程量大）；
  3. 硬警示文案（对齐 md3）+ 二次确认；
  4. **一期仅支持 TXT/MD**；EPUB 是 zip 内 xhtml，另立项。
- **seam**：Room（索引重建）、文件系统（私有副本）、搜索索引、进度体系 —— **与切片 1/2/5 全部相邻，必须独立切片、独立验收**。

---

## 3. 依赖顺序与建议排期

```
S1 工具栏（独立，可先行）
S2 光标/选词（依赖 Reader 层空闲；与 S1 无耦合）
S3a 编辑器+修改层（依赖 S1 的 edit 动作；复用 R4，零迁移）
S3b 写回源文件（依赖 S3a；需产品决定后单独立项）
```

## 4. 需要用户决定

| # | 议题 | 建议 |
|---|---|---|
| 1 | 高频槽上限 5 还是 6 | **5**（6 个在小屏已挤） |
| 2 | 老用户旧 3 槽配置是否自动升级为 5 槽 | 未动过设置的升级，动过的保留 |
| 3 | 跨页选择一期是否要 | **不要**（成本一档，收益低） |
| 4 | S3b 是否做、锚点数据处置策略 | 先上 S3a；S3b 单独决定（建议策略 b） |

## 5. 不变式（实施时不得违反）

- `TextOffsetMap.toSource` floor 语义、`LocatorCodec` encode、进度真源=全书字符偏移 —— **不动**。
- 搜索 original/display 双通道口径不变；S3b 写回后走 `invalidateSweepForFullRebuild`，严禁据 format 推断覆盖率。
- R4 纠错"原文件永不修改"注释是 S3a 的设计根基；S3b 是它唯一被授权打破的例外，且只作用于私有副本。

## 6. 跨页选择是什么（一期为何不做）

阅读器不是"一整本书一个文本组件"，而是**按页（或按 ReadingUnit 分段）渲染，一页一个独立组件**。
CRA 每页是一个 `BasicTextField`（`ReaderContentHostPlainTextBranch`），组件内部只认识自己那一页的字符串。

- **页内选择**：手指在一页内拖动，一切都在同一个组件里，偏移也就是本页局部偏移 → 简单。
- **跨页选择**：选区从第 1 页末尾一直拖到第 2 页开头。两个页面是**两个独立组件**，
  各自有自己的选区状态和局部偏移。要支持它，必须：
  1. 把两个组件的局部偏移统一映射到**全书 source 偏移**（CRA 已有 `localDisplayToGlobalSource`，映射本身具备）；
  2. 让选区状态**提升到页面之外**的共享宿主，由它跨组件驱动两页各画一段高亮；
  3. 处理拖到屏幕边缘时的**自动翻页**、翻页后继续扩选、以及分页重排导致选区错位；
  4. 跨页高亮的绘制（每页只画落在自己范围内的那一段）。

也就是说：难点不在"算偏移"（已有），而在**把选区状态从单页组件提升到跨页宿主 + 拖拽跨边界的交互**，
这是一档独立工程，且和分页/翻页/重排耦合很深。

**Legado 能做到**，是因为它的 `ContentTextView` 本身就是自绘整段文本、选区状态天然在 View 层统一持有；
**md3 继承了这套**。CRA 是"每页一个 Compose 组件"，没有这个统一宿主。

**一期结论**：不做。绝大多数场景（查词、复制、替换、编辑）都在一句话/一段内完成，
跨页选择的收益远低于它对分页稳定性的风险。真需要时再单独立项。
