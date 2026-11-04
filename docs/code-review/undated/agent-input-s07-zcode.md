# S07-Z：Android 选区全链路地图（Zcode 只读审查）

> Agent：Zcode（模型 `deepseek v4.1Flash`）
> 日期：2026-09-20
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 比较命令：`git diff origin/main -- <paths>`（工作区未提交改动，不能用三点式 diff）
> 性质：**只读审查**。未修改任何产品源码 / 资源 / Gradle / 测试源码；未运行 Gradle；未 stage / commit / push。
> 唯一写入：本文件。

---

## 0. 结论摘要

选区主链路（分页 TXT / 分页 EPUB / 滚动 TXT）是**清晰且可单测**的：状态唯一真源在 `ReaderViewModel._screenState`，display↔source 换算统一走 `PagedChapterSource`/`ScrollUnitProjection` 的投影接口，没有第二套自算偏移。

但**三种格式并不使用一致坐标，也不使用一致入口**，且存在一个会产生错误落库的选区泄漏。按严重度：

| # | 严重度 | 结论 | 性质 |
|---|---|---|---|
| Z-1 | P1 | 分页翻页后 VM 选区不清，工具条滞留新页，此时保存高亮/笔记会用**旧页坐标**落库 | 已确认（基线同样存在，非本切片引入） |
| Z-2 | P1 | Markdown 滚动模式**完全没有选区入口**（`onSelectBlock` 未接线）；EPUB 非分页模式退化为**整块点击**而非长按选句 | 已确认（基线同样存在） |
| Z-3 | P2 | 选区落在被替换规则删除的文本内时，source 区间长度为 0，高亮**静默不渲染** | 静态推断（有明确守卫链） |
| Z-4 | P2 | `edit` 动作在设置页可被用户勾选，但生产分发无该分支 → 渲染成点了没反应的死按钮 | 已确认 |
| Z-5 | P2 | 「复制」无条件提示「已复制」，忽略剪贴板真实写入结果 | 已确认（基线同样存在） |
| Z-6 | P2 | 非分页 EPUB / Markdown 滚动路径**不渲染已存高亮** | 已确认（基线同样存在） |
| Z-7 | P3 | `onClearSelectedText` 回调已声明、已构造，但无任何消费方（死接线） | 已确认（基线同样存在） |
| Z-8 | P3 | 第一屏 4 胶囊 +「更多」在窄屏上的标签截断风险，静态无法判定 | 未验证项，需真机 |
| Z-9 | P3 | `traditionalChinese`（繁体显示）在分页引擎未接线，`PageLayer` 参数恒为默认 `false` | 已确认（基线同样存在） |

**重要区分**：Z-1 / Z-2 / Z-5 / Z-6 / Z-7 / Z-9 在 `origin/main` 上以同样形态存在（已逐条用 `git show origin/main:<path>` 核对）。它们不是本切片引入的回归，但都属于选区链路的真实缺陷，应由 Codex 决定是纳入 S07 还是另立切片。

---

## 1. 范围与状态真源

### 1.1 状态真源（唯一）

`ReaderScreenState`（`ui/screen/reader/ReaderScreenState.kt`）持有全部选区状态：

| 字段 | 行 | 语义 |
|---|---|---|
| `selectedText` | 30 | 选区显示文本；**工具条可见性唯一门控**（`isNotBlank()`） |
| `selectedRangeStart` | 31 | TXT/滚动路径的 source 起点；EPUB 分页路径恒为 `-1` |
| `selectedGlobalOffset` | 32 | EPUB 分页路径的 source 起点；TXT 路径恒为 `-1` |
| `selectedSourceEnd` | 34 | 全书 source **半开终点**；旧路径为 `-1` |
| `showColorRow` | 41 | 颜色行展开态（工具条内部二级视图） |

写入只经 `ReaderViewModel` 三个 action（`ui/viewmodel/ReaderViewModel.kt`）：
- `SetSelectedText`（318–324）：四字段整体覆盖；
- `ClearSelection`（325–334）：四字段复位 + `showColorRow=false`；
- `ToggleColorRow`（339）：仅取反颜色行。

`ReaderModels.kt:148–153` 的 `SetSelectedText` 四参（`text/rangeStart/globalOffset/sourceEnd`，`sourceEnd` 默认 `-1`）是唯一入口。

**设计优点**：选区状态只有一个 owner，`ReaderScreen` 侧无本地副本；工具条可见性由状态派生而非事件驱动，不存在「状态与 UI 分叉」。

### 1.2 动作定义与门控

`data/settings/SelectionActionSettings.kt`：
- `ALL`（78–92）声明顺序即渲染顺序；`edit` 定义在 84 行，`cancel` 为 `FIXED`（91）。
- `DEFAULT_PRIMARY`（100）= `{copy, browser, replace, dictionary}`；`DEFAULT_MORE`（103）= `{highlight, note, search, ai, inspiration}`。
- `MAX_PRIMARY_SLOTS = 5`（58），`MIN_PRIMARY_SLOTS = 1`（46）。
- 读路径一律 `sanitize`（180–193），磁盘脏值不落 UI。

门控在 `ReaderSelectionToolbar.kt:106–124`（`selectionToolbarActions`）：`replace` 需 `canCreateReplaceRule`，`ai` 需 `aiConfigured`；高频槽被门控清空时回退到定义序第一个可用动作（120），保证选区永远有入口。

---

## 2. 三种格式的真实链路

### 2.1 TXT 分页 / EPUB 分页（`pagerEngineOn && pagedSource != null`）

**识别**（`feature/reader/pager/PagedReaderPageSurface.kt`）：
- 长按（316–330）：`PageSelection.offsetAt` 把触控点换成**章内 display 偏移** → `PageSelection.sentenceAround` 扩到整句 → 写入 `selRange` → `onSelect(text, globalSourceStart, globalSourceEnd)`（3 参）。
- 手柄拖动（240–312）：`PageSelection.adjustHandle` 实时钳制区间，每次移动都回调 `onSelect`（304–308）。
- 清除：双击（332–340）与单击（341–345）都调 `onSelect("", -1, -1)`；**翻页路径只置 `selRange.value = null`，不回调 `onSelect`**（见 Z-1）。

**换算**（`TxtPagedController.kt`，类名保留但已与格式无关）：
- `currentChapterLocalDisplayToGlobalSource`（117）→ `localDisplayToGlobalSource`（209）：有投影走 `projection.localDisplayToGlobalSource`，无投影走 `chapterStartAbs + local`。
- `currentChapterSourceRangeToLocalDisplay`（124）→ `globalSourceToLocalDisplay`（215）：持久化 source 区间反查 display 区间（高亮/TTS/搜索底色绘制用）。

**3 参 → 4 参适配**（`ui/screen/reader/ReaderContentHostPagedBranch.kt:118–124`）：
```
EPUB  → onSelect(text, absStart, -1, absEnd)    // 起点进 selectedGlobalOffset
TXT   → onSelect(text, -1, absStart, absEnd)    // 起点进 selectedRangeStart
```
这条分支是「同一宿主、两种落库字段」的唯一判别点，与 `computeLocatorJson`、`currentSelectionSourceRange`、`selectedSourceLength` 三处消费方的判别式一致。

**清理信号回流**：`selectionCleared = selectedText.isBlank()`（125）→ `PagedReaderControllerEffects.kt:91` 的 `LaunchedEffect(selectionCleared)` 撤掉本地 `selRange`。外部（保存高亮/规则成功/返回键）清选区后，宿主底色会跟随撤掉。

### 2.2 TXT 滚动 / 小 TXT / 纯文本（else 分支）

`ui/screen/reader/ReaderContentHostPlainTextBranch.kt`，readOnly `BasicTextField`：
- 选区由 BasicTextField 自身给出；`onValueChange`（348–377）把 `TextRange` 换算为 source 全书区间并回调 `onSelect(text, -1, srcStart, srcEnd)`。
- 长按粒度收敛（S2）：`readerTextFieldLongPressAnchor`（`ReaderTapObservation.kt:187–240`，**不消费事件**的旁听器）记下长按位置，首次选区经 `clampedInitialSelection`/`wordRangeAt`（`ReaderSelectionGranularity.kt`）收敛到词，词长 > 8 或分词异常退化为单字。
- 自绘小把手：`handleColor = Color.Transparent` 隐藏系统手柄，`drawSmallSelectionHandle`（418–444）按 `getCursorRect` 画小球 + 短竖柄。**手势仍归系统手柄**（命中区未改），自绘只是视觉。
- 外部清选区同步（312–319）：`selectionCleared` 时清本地 `selection`/`pendingWordClamp`/`longPressAnchor`。

### 2.3 EPUB 非分页（legacy）/ Markdown 滚动

**EPUB legacy**（`ReaderContentHostEpubBranch.kt` + `content/PagedEpubContent.kt`）：进入条件是 `pagerEngineOn` 为 false（`epubPagerEngineMode` 为 `off`，或 `auto` 下崩溃守卫已禁用引擎）。
- 选区入口是**整块点击**，不是长按：`ReaderContentHostEpubBranch.kt:198–200` 与 `PagedEpubContent.kt:398–413` 都是 `Modifier.clickable`，回调 `onSelectBlock(block.text, gOff)` → `callbacks.onSelect(text, off, -1, -1)`（130 行）。
- `PagedEpubContent.kt:400–409` 新增了**脚注标记拦截**：块内含 `[1]`/`【1】`/`①` 等时点击开脚注对话框而**不选中**。解释文本用 `blocks.filterIsInstance<DocBlock.Text>().firstOrNull { it.startsWith(fnRef) || it.contains("注：") || it.contains("注释：") }` 猜，可能取到与标记无关的块（启发式，非精确配对）。
- 无部分选区、无手柄、无跨行拖选。

**Markdown 滚动**（`ReaderContentHostMarkdownBranch.kt`）：`RenderMarkdownUnit` 调用（130–140）**未传 `onSelectBlock`**，而 `ReaderMarkdownRender.kt:254–346` 的点击分支全部以 `if (onSelectBlock != null)` 为条件 → **该路径零选区入口**。`RenderMarkdownChapter` 虽有该参数（163），滚动分支未使用。
> 注意：Markdown 在默认设置（`readerMode="paged"`, `pagerEngineMode="on"`）下走 2.1 的分页分支，**选区可用**；无选区仅发生在用户把阅读模式改为「上下滚动」时。

**坐标口径**：`MarkdownChapterSource.kt:19–20` 显式声明 `replacementCoordinateSpace = CANONICAL_DISPLAY`，全部偏移基于 `canonicalText`；`replacementAvailability` 对 Markdown 判为 `NON_SOURCE_COORDINATES`（`ReaderReplacementCapability.kt:47–49`），**不接投影**，因此 Markdown 不存在 display/source 分叉风险。

---

## 3. 动作矩阵（第一屏 4 槽 + 更多）

第一屏默认（`effectivePrimary`）= 复制 · 浏览器 · 替换 · 字典，末尾固定追加「更多」。

| 动作 | id | 处理方（文件:行） | 真实行为 | 是否清选区 |
|---|---|---|---|---|
| 复制 | `copy` | `ReaderLayerBuilders.kt:341` | `clipboard.copyText(...)` + `showNotice("已复制")` | 否 |
| 浏览器 | `browser` | `:342–348` → `ReaderSelectionExternalActions.kt:96/116` | `createChooser(ACTION_VIEW)` 打开查询 URL；chooser 起不来退回裸 `ACTION_VIEW` | 否 |
| 替换 | `replace` | `:358` | `OpenSheet(RULES)`，`initialReplaceText = selectedText`（`ReaderSheetHost.kt:294`） | 否（保存成功后由 VM 清，见 4.3） |
| 字典（离线） | `dictionary` | `:349–357` → `:240` | `onOpenDictionary` → 记 `dictionaryWord` + `OpenSheet(DICTIONARY)` | 否 |
| 字典（系统） | `dictionary` | `:242–255` | `ACTION_PROCESS_TEXT` chooser → 失败直接 `startActivity` → 再失败回退在线词典 URL | 否 |
| 字典（在线） | `dictionary` | `:256–268` | 模板 URL 走浏览器选择 | 否 |
| 高亮 | `highlight` | `:312–337` | `SaveHighlight` + `ClearSelection` + `showNotice("已高亮")` | **是** |
| 添加批注 | `note` | `:340` → `ReaderActions.kt:196–231` | `SaveNote` + 清 `noteBody` + 关框 + `ClearSelection` | **是** |
| 书内搜索 | `search` | `:359–361` → `ReaderSelectionToolbar.kt:127–131` | 先 `SetSearchQuery` 快照文本，再 `ClearSelection`，再开 SEARCH 面板 | **是** |
| AI 解读 | `ai` | `:338` | `OpenSheet(AI_EXPLAIN)` | 否（保存 AI 灵感时**也不清**，见下） |
| 记为灵感 | `inspiration` | `:339` | `OpenSheet(INSPIRATION)` | 保存时清（`:609`） |
| 取消选择 | `cancel` | `:362` | `ClearSelection` | **是** |

**外部文字工具 / 系统词典三通道**（`ReaderSelectionExternalActions.kt`）：
1. `queryIntentActivities(ACTION_PROCESS_TEXT)` 命中 → `createChooser`（149）；
2. 查询为空仍直接 `startActivity`，兜住包可见性过滤造成的假阴性（153）；
3. 两段都失败 → 在线词典 URL（155–161），返回 `OPENED_WEB_FALLBACK`，调用方提示「未找到词典应用，已用在线词典查询」。
`AndroidManifest.xml:14–22` 已声明 `<queries>` 的 `PROCESS_TEXT` 与 `TTS_SERVICE`，`queryIntentActivities` 不会被可见性过滤误杀。

**空选区防护**：`blankSelectionNotice`（56–57）在**任何 Intent 判定之前**拦截空选区，避免把「其实没选字」报成「未找到可用浏览器」（53–54 的注释解释了这一诊断误导）。

**查询串处理**：`normalizedSelectionQuery`（65–68）去首尾空白 + 折叠连续空白 + 按上限截断；`boundedByChars`（70–75）**不切断 UTF-16 代理对**（73 行判断高位代理），避免 emoji/生僻字被 URL 编码器静默替换成 `?`。浏览器上限 500 字符、词典 120 字符。

---

## 4. 坐标风险

### 4.1 display → source 的单调性与边界（已确认设计正确）

`feature/reader/rules/TextOffsetMap.kt` 定义 `toSource`（48）/`toDisplay`（59）为**分段线性 + floor 二分**，契约在 6–14 行逐条写明：双向单调不减、round trip floor、两端精确、越界 clamp。删除文本在 display 空间坍缩为一点，插入文本在 source 空间平铺到匹配末尾。

`PagedReaderPageSurface` 的选区两端都经同一函数换算（306–307、327–328），因此 source 区间单调、不反转。**这部分无需担心**。

### 4.2 [P2] 选区完全落在「被删除文本」内时，source 区间长度为 0 → 高亮静默不渲染

**链条**：
1. 替换规则把 source 某段删除 → display 空间该段坍缩为**一个点**（`TextOffsetMap.kt:12–13` 的显式语义）；
2. 用户选中的 display 区间若整体落在坍缩点内，`toSource(s) == toSource(e)`；
3. `ReaderContentHostPagedBranch.kt:118–124` 传出的 `absStart == absEnd`；
4. `ReaderScaffold.kt:595–600` 算出 `selectedSourceLength = end - start = 0`；
5. `ReaderHelpers.kt:83–86` `buildHighlightPayload(0)` 写入 `source_length: 0`；
6. `highlightSourceLength`（89–97）返回 0；
7. `ReaderContentHostPagedBranch.kt:131–139` 的 `if (len <= 0) return@mapNotNull null` 直接跳过 → **高亮永不绘制**；
8. 同时 `ReaderSearchIndexCoordinator.currentSelectionSourceRange`（57–66）的 `start in 0 until end` 判 false 返回 null → 单处纠错会报「当前没有可用的选区位置」（`ReaderViewModel.kt:418–422`）。

**性质**：静态推断。触发需要「替换规则删除的文本」与「用户恰好选中该坍缩点」同时成立，概率低但非零；表现是「点了高亮、提示已高亮、但页面上什么都没有」。落库记录存在（`SaveHighlight` 无校验），所以不是数据丢失，而是**不可见 + 不可锚定**。

**未验证**：没有真机/单测复现。建议 Codex 让 Qoder 用 `BoundedReplaceProjectorTest`/`ReplaceProjectionTest` 的既有夹具补一个「选区落在删除区间」的断言，或直接判为已知限制。

### 4.3 替换净化后的重投影与选区失效（设计正确）

`ReaderViewModel.kt:450–462`：规则写入**成功**（`Success`/`Saved`）后立即清选区，理由是「规则变更会使正文重分页，残留的 display 坐标选区会错位」。失败路径（`Rejected`/`NotAnchorable`/`NotFound`）**不清**，用户可重试。这是正确的处置。

单处纠错的锚点在 `execute` **之前**从当前选区回查（`ReaderViewModel.kt:417`），且校验 `draft.findText == selectedText`（426–431），避免把「改了查找内容」的请求锚到旧选区。

`ReaderReplacementCapability.kt:108–114` 记录了一段**已删除**的历史重载：它按 UI 侧标志（`isTxt`/`readerMode`/`pagerEngineOn`）反推能力，只能表达 4 种结果 —— 正是「只因开关打开就宣称可替换」的来源。生产路径已统一为消费真实 `PagedReplacementAvailability`。这是一处值得肯定的收敛。

### 4.4 流式大 TXT / 小 TXT / Markdown / EPUB 的坐标一致性

| 路径 | source 空间 | 起点字段 | 投影 |
|---|---|---|---|
| TXT 分页（流式/小） | `chapterStartAbs + local`（无投影）或投影映射 | `selectedRangeStart` | 有（`TxtChapterSource` + `preparePagedReplacement`） |
| EPUB 分页 | `LegacyOffsetCodec` 估算基准（`TxtPagedController.kt:42–43` 明确要求不自算） | `selectedGlobalOffset` | 有（`EpubReplacedChapterSource`） |
| Markdown 分页 | `canonicalText` 字符空间 | `selectedRangeStart` | **无**（`NON_SOURCE_COORDINATES`） |
| 滚动 TXT | 全书 source | `selectedRangeStart` | 有（`ScrollUnitProjection`，`ScrollUnitProjection.kt:17/21/28`） |
| 滚动 Markdown | 无选区 | — | — |
| EPUB legacy | 块全局偏移 | `selectedGlobalOffset` | **无** |

**结论**：**不一致**。四类可见路径共用「`selectedSourceEnd` 半开终点 + 起点按格式二选一」的落库契约，这点是统一的；但**选区入口**与**是否接投影**按格式分叉。特别是 EPUB legacy 与 Markdown 滚动两路既不接投影、也不渲染已存高亮（Z-6），与分页路径的行为差距较大。

---

## 5. 状态清理审计（翻页 / 滚动 / 切章 / 重载 / 面板）

### 5.1 [P1] Z-1：分页翻页不清 VM 选区

**证据**：
- `PagedReaderPageSurface.kt:499–506` 的 `onPrevious`/`onNext` **只** `selRange.value = null`，不调 `onSelect("", -1, -1)`；`requestManualReveal`（212）同样只置 `selRange.value = null`。
- 对照：单击（341–345）与双击（332–340）在撤选区时**都**调了 `onSelect("", -1, -1)`。同一文件内的两条清理路径语义不对称。
- 翻页最终走 `onGesturePageTurn` → `ReaderContentHostPagedBranch.kt:87` → `ReaderLayerBuilders.kt:118` → `ReaderAction.PageTurn`。
- `ReaderViewModel.kt:294–296` 的 `PageTurn` 分支**只**跑 `readerChromeReducer(..., PageTurn)`，**不碰任何选区字段**。
- `PagedReaderControllerEffects.kt:91` 的 `LaunchedEffect(selectionCleared)` 只在 `selectionCleared` 为 true 时才清 `selRange`；而 `selectionCleared = selectedText.isBlank()`，此时 `selectedText` 仍非空 → 本地 `selRange` 被 `onPrevious/onNext` 清掉，**VM 侧却保留**。

**后果**：
1. `ReaderInteractionLayer.kt:353` 的工具条 `visible = state.selectedText.isNotBlank()` 仍为 true → 工具条**悬浮在新页上**，且摘录条显示的仍是上一页选中的文字；
2. `computeLocatorJson()`（`ReaderProgressActions.kt:36–41`）仍按旧选区返回 locator；
3. 用户此时点「高亮」，`onPickColor`（`ReaderLayerBuilders.kt:312–337`）用旧 `selectedText` + 旧 locator 落库 → **高亮存到错误位置**（旧页的坐标，指向新页上下文）。

**唯一自发恢复点**：在新页任意单击（341–345）会清掉 VM 选区。也就是说，只要用户翻页后直接点动作而不先点一下正文，就会命中。

**性质**：`git show origin/main` 核对，基线的 `onPrevious/onNext` 同样只置 `selRange.value = null`，`PageTurn` 分支同样不碰选区 → **基线同样存在，非本切片引入**。

### 5.2 其余清理点（均正确）

| 场景 | 处置 | 位置 |
|---|---|---|
| 中央点击收起 chrome | `CenterTap` 且 `selectedText` 非空时清 `selectedText`/`selectedRangeStart` | `ReaderViewModel.kt:288–291` |
| 系统返回键 | 优先级：sheet → note → overflow → **选区** → chrome → 临时返回 | `ReaderScreen.kt:300–316` |
| 保存高亮 | 先快照文本再 `ClearSelection` | `ReaderLayerBuilders.kt:314–336` |
| 保存笔记 | 同高亮，先快照后清 | `ReaderActions.kt:206–230` |
| 书内搜索 | `SetSearchQuery` 快照 → `ClearSelection` → 开面板 | `ReaderSelectionToolbar.kt:127–131` |
| 保存灵感 | 快照 locator 后 `ClearSelection` | `ReaderLayerBuilders.kt:588–612` |
| 规则保存成功 | 清四字段 + `showColorRow` | `ReaderViewModel.kt:450–462` |
| 规则面板返回 | 有选区则 `ClearSelection` | `ReaderSheetHost.kt:297–301` |
| 自动翻页启动 | `ClearSelection` | `ReaderActions.kt:181` |
| 外部清空回流宿主 | `selectionCleared` 撤本地 `selRange`/`selection` | `PagedReaderControllerEffects.kt:91`、`ReaderContentHostPlainTextBranch.kt:312–319` |

### 5.3 切章 / 文档重载

- **切章**（目录点击 / 上一章下一章 / 音量键）：经 `goToChapter`（`ReaderProgressActions.kt:53–98`）→ `LoadChapter`；`ReaderViewModel.kt:274` 的 `LoadChapter` 分支**不清选区**。
- **切书**：`openBook`（`ReaderViewModel.kt:526–556`）在书身份变化时只重置 chrome 与 `searchQuery`（537–541），**不清选区字段**。
- **未验证**：切章/切书后选区是否残留，取决于宿主侧是否另有清理。分页宿主经 `selectionCleared` 只做「VM 已清 → 撤本地」，**方向是单向的**：VM 没清，宿主就不会清。因此切章后同样存在 Z-1 形态的滞留（工具条仍在，坐标已跨章）。这一条我**没有找到任何反向清理点**，属于 Z-1 的同一根因，合并计入 Z-1。

### 5.4 滚动路径的跨单元泄漏

`ReaderContentHostPlainTextBranch.kt:303` 的 `selection` 以 `content.sourceStartAbs` 为 remember key，每个 ReadingUnit 一份本地选区。VM 侧是**单一** `selectedText`。在单元 A 选中后滚动到单元 B，单元 A 的 `BasicTextField` 若被回收则本地选区消失，但 VM 仍非空 → 与 Z-1 同类滞留。`onValueChange` 的 else 分支（374–376）只在 `selectedRangeStart` 落在**本单元**范围内时才清空，跨单元不触发。

---

## 6. 精确 S07 文件 / hunk 边界

### 6.1 S07 核心文件（可独立成切片）

| 文件 | 状态 | 行数变化 | 说明 |
|---|---|---|---|
| `ui/screen/reader/ReaderScreenState.kt` | M | 选区字段 30–41 | S07 状态定义 |
| `ui/viewmodel/ReaderModels.kt` | M | `SetSelectedText` 148–153（`sourceEnd` 为新增） | S07 action |
| `ui/screen/reader/ReaderSelectionGranularity.kt` | **新增** | 74 | S2 选词粒度（纯 JVM） |
| `ui/screen/reader/ReaderSelectionExternalActions.kt` | M | 5 处 hunk（101/108/157/164/170） | 浏览器/词典/系统文字工具 |
| `ui/screen/reader/ReaderSelectionToolbar.kt` | M | 6 处 hunk（56/62/158/163/236/259） | 配置驱动工具条 + 令牌化 |
| `feature/reader/pager/PageSelection.kt` | M | 3 处 hunk（60/84/94） | `Handle.top/bottom` + `isHandleHit` |
| `ui/screen/reader/ReaderTapObservation.kt` | M | +55 行（新增 `readerTextFieldLongPressAnchor`） | S2 长按旁听器 |
| `ui/screen/reader/ReaderReplacementCapability.kt` | M（**已提交** `99a8c03`） | — | 能力裁决收敛 |
| `ui/screen/reader/ReaderReplaceCoordinates.kt` | M（**已提交** `84f7717`） | — | TTS display↔source |

**新增测试**：`ReaderSelectionGranularityTest.kt`、`PageSelectionHandleDragTest.kt`（+32）、`ReaderSelectionToolbarModelTest.kt`（±14）、`ReaderSelectionExternalActionsTest.kt`、`HighlightSourceLengthTest.kt`、`MarkdownSelectionPositionTest.kt`、`ScrollHighlightRangeTest.kt` 等。

### 6.2 S07 关键 hunk，但**必须 hunk 级拆分**的共享文件

| 文件 | 总变化 | S07 相关 hunk | 混入的其它切片 |
|---|---|---|---|
| `ui/screen/reader/ReaderLayerBuilders.kt` | 82 行 | `onSelect`（119–121）、`onPickColor`（312–337）、`onCopy`（341）、`onBrowser`（342–348）、`onDictionary`（349–357）、`onSearch`（359–361）、`onClearSelection`（362） | M-BUG-08 TXT 章导航（250–296）、一键日/夜（272–274） |
| `ui/screen/reader/ReaderInteractionLayer.kt` | 182 行 | 工具条节点（352–388）、`selectionActions`/`aiConfigured` 字段（88–94） | 灯绳与引导气泡（185–283）、底栏安全区（287–349） |
| `ui/screen/reader/ReaderContentHostPagedBranch.kt` | 11 行 | `onSelect` 适配（118–124）、`selectionCleared`（125）、`selectionColor`（127） | customTip 页眉页脚、`batteryTipStyle`、`tapActionPreset` |
| `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | 178 行 | 本地选区同步（312–319）、`onValueChange`（348–377）、长按锚点（390–395）、自绘手柄（418–444） | 该文件其余为结构搬运/占位 |
| `ui/screen/reader/ReaderContentHostEpubBranch.kt` | **1 行** | 无 | `tapActionPreset`（116）—— **不属 S07** |
| `feature/reader/pager/PagedTxtReaderHost.kt` | 70 行 | `PageLayer` 把手几何（437–470）、`PageCanvas` 圆角底色（526–543） | `tapActionPreset`、customTip 页眉页脚、`batteryTipStyle` |
| `feature/reader/pager/PagedReaderPageSurface.kt` | 80 行 | 手柄命中（184–187、250–310）、长按（316–330）、`selectionColor` | `tapActionPreset` |
| `feature/reader/pager/PagedReaderControllerEffects.kt` | 小 | `selectionCleared` effect（91） | — |
| `ui/screen/reader/content/PagedEpubContent.kt` | 74 行 | 脚注拦截（398–413）、`onSelectBlock` 透传（448） | `tapActionPreset`、`EpubImageViewerDialog`、`EpubFootnoteDialog` |
| `ui/viewmodel/ReaderViewModel.kt` | 442 行 | `SetSelectedText`（318–324）、`ClearSelection`（325–334）、`CenterTap` 选区清（288–291）、规则成功清选区（450–462） | `ReaderSearchIndexCoordinator` 抽取（S03）、`txtScanCoordinator`、`ReaderChapterLoaderCoordinator`、`_ruleMutationResult` 清空 |
| `data/settings/SelectionActionSettings.kt` | M | `ALL`/`DEFAULT_*`/`sanitize`/`resolveStoredPrimary` | 与设置重构（S13）共用 |
| `ui/screen/profile/SelectionSubPage.kt` | M | 选区动作配置页 | 设置页骨架（S13） |

### 6.3 不能整文件集成的原因

1. `ReaderViewModel.kt` 的 442 行变化里，**选区相关不足 40 行**，其余是协同模块抽取（S03 搜索索引）、TXT 扫描协调、章节加载协调 —— 整文件集成会把 S03 与未知切片一并带入。
2. `ReaderContentHostEpubBranch.kt` 的**唯一**一行改动是 `tapActionPreset`，与选区无关，不能算 S07。
3. `ReaderLayerBuilders.kt` / `ReaderInteractionLayer.kt` / `PagedTxtReaderHost.kt` / `PagedEpubContent.kt` 同时承载页眉页脚自定义、电量样式、日/夜灯绳、点击区预设等**至少四个其它切片**。
4. `SelectionActionSettings.kt` + `SelectionSubPage.kt` 与 S13 设置重构共享，需与设置切片协调归属。
5. `ReaderContentHostPagedBranch.kt` 的 `customTipHeader*`/`customTipFooter*`/`batteryTipStyle` 属页眉页脚切片（S10），必须与 `onSelect`/`selectionCleared` 分开取。

---

## 7. 事实 / 静态推断 / 历史证据 / 未验证项

### 7.1 已确认事实（读源码即可判定，附符号）

- F1：选区状态唯一真源为 `ReaderScreenState` 四字段 + `showColorRow`；写入仅经 `SetSelectedText`/`ClearSelection`/`ToggleColorRow`。
- F2：分页宿主 `PagedReaderPageSurface.onSelect` 是 3 参（text, sourceStart, sourceEnd）；4 参落库字段由 `ReaderContentHostPagedBranch:118–124` 按格式二选一。
- F3：`PageSelection.sentenceAround` 用中文句末集 `。！？…；\r\n` + 闭合引号跟随；CR/LF 只做段边界、不进选中文本（`PageSelection.kt:141–166`）。
- F4：翻页（`onPrevious`/`onNext`/`requestManualReveal`）只置 `selRange.value = null`，不回调 `onSelect`；单击/双击则回调 `onSelect("", -1, -1)`（`PagedReaderPageSurface.kt:212/332–345/499–506`）。
- F5：`ReaderAction.PageTurn` 分支不触碰任何选区字段（`ReaderViewModel.kt:294–296`）。
- F6：`LoadChapter` 分支不触碰选区字段（`ReaderViewModel.kt:274`）。
- F7：`openBook` 切书只重置 chrome 与 `searchQuery`，不清选区（`ReaderViewModel.kt:537–541`）。
- F8：`RenderMarkdownUnit` 的点击分支全部以 `onSelectBlock != null` 为门控，而 Markdown 滚动分支未传该参数（`ReaderContentHostMarkdownBranch.kt:130–140`）。
- F9：EPUB legacy 与 Markdown 滚动/分页渲染路径中 `highlights` 出现次数为 **0**（`grep -c` 验证）；分页与滚动 TXT 路径均渲染（`ReaderContentHostPagedBranch.kt:131–139`、`ReaderContentHostPlainTextBranch.kt:252–269`）。
- F10：`edit` 在 `SelectionActions.ALL:84` 有定义、在 `pickableDefs()`（133）中可被设置页勾选（`SelectionSubPage.kt:115/162`），但 `SelectionToolbar.onActionClick`（162–177）无 `"edit"` 分支 → `else -> Unit`；`selectionActionVisual`（372–438）也无 `"edit"` → 落入 else 灰点。
- F11：`onCopy` 丢弃 `copyText` 返回值并无条件提示「已复制」（`ReaderLayerBuilders.kt:341`）；`copyText` 对空白/异常返回 `false`（`ClipboardUtils.kt:34–53`）。
- F12：`ReaderSheetHostCallbacks.onClearSelectedText` 已声明（`ReaderSheetHost.kt:159`）与已构造（`ReaderLayerBuilders.kt:522`），**无任何消费方**。
- F13：`saveSingleCorrection` 只校验 `sourceStart >= 0 && sourceEnd > sourceStart`（`RulesRepository.kt:313–315`），不校验区间是否落在真实 source 边界内。
- F14：`buildHighlightPayload` 对 `sourceLength >= 0` 都写 `source_length`（含 0），`highlightSourceLength` 对 0 返回 0，绘制侧 `if (len <= 0) return` 跳过。
- F15：`ReaderContentHostPlainTextBranch` 的本地 `selection` 以 `content.sourceStartAbs` 为 remember key，每单元一份；VM 侧只有一份。
- F16：`PagedReaderHost` 的 `PageLayer(traditionalChinese: Boolean = false)`（374）在 `PagedReaderPageSurface.kt:437` 调用时**未传该参数**，`ReaderContentHostPagedBranch` 也未透传 `readerSettings.traditionalChinese` → 繁体显示对分页引擎无效。

### 7.2 静态推断（有明确代码链，但未运行验证）

- I1（= Z-3）：选区完全落在替换删除的坍缩区间内时，`absStart == absEnd` → `source_length: 0` → 高亮静默不绘制，且单处纠错报「没有可用的选区位置」。链条见 4.2，每步均有行号。
- I2：翻页后用户不先点正文而直接点「高亮」，会用旧页坐标落库（= Z-1 的后果）。链条 F4+F5+`ReaderInteractionLayer.kt:353`。
- I3：切章后存在与 I2 同形态的滞留（F6/F7 + 宿主单向清理）。
- I4：`PagedEpubContent.kt:400–409` 的脚注解释文本用 `firstOrNull { startsWith(fnRef) || contains("注：") }` 猜测，块序靠前者可能被误取为解释；且每次点击对 `blocks` 做一次 `filterIsInstance` 全表扫描。
- I5：`ReaderLayerBuilders.kt:564–587` 的 `onSaveAiExplainInspiration` 保存后**只 `CloseSheet`、不 `ClearSelection`**，而 `onSaveInspiration`（588–612）清了 —— 同类动作行为不一致（选区在 AI 解读保存后仍留在 VM）。

### 7.3 历史证据（基线已存在，非本切片引入）

用 `git show origin/main:<path>` 逐条核对：

- `PagedReaderPageSurface.kt` 基线 446/450 行同样是 `selRange.value = null`（无 `onSelect` 回调）→ Z-1/I2 为历史行为。
- `ReaderViewModel.kt` 基线 298–299 的 `PageTurn` 分支同样不碰选区 → Z-1 为历史行为。
- `ReaderLayerBuilders.kt` 基线 273 行 `onCopy` 与当前 341 行**逐字相同** → Z-5 为历史行为。
- `ReaderContentHostEpubBranch.kt` / `ReaderMarkdownRender.kt` 基线 `highlights` 计数同样为 0 → Z-6 为历史行为。
- `ReaderSheetHost.kt:158` / `ReaderLayerBuilders.kt:454`（基线）同样声明并构造了未被消费的 `onClearSelectedText` → Z-7 为历史行为。
- `PagedTxtReaderHost.kt` 基线同样有 `traditionalChinese` 参数（3 处）且未接线 → Z-9 为历史行为。
- `PagedEpubContent.kt` 基线 363 行是裸 `clickable { onSelectBlock(...) }`，**无** `fnRegex`/`viewingFootnote` → 脚注拦截（I4）是**本切片新增**。

### 7.4 当前未验证项（需真机或运行测试）

- U1：Z-1 在真机上的实际可复现性（翻页后工具条是否真的滞留、点高亮是否真的落错位置）。
- U2：第一屏 4 胶囊 +「更多」= 5 等分在窄屏（设计文档提到的小屏设备）上的标签截断；`SelectionCapsuleAction`（`ReaderSelectionToolbar.kt:441–484`）标签 `maxLines = 1` 且未指定 `overflow`（默认 Clip）。设计文档 §0 自己标注「必须真机确认仍可点」。
- U3：系统手柄设为 `Transparent` 后是否仍可拖动（`ReaderContentHostPlainTextBranch.kt:325–330`）。设计文档 §S2 明确标为「必须在真机确认的唯一假设」；自绘小球不接管手势，若拖不动需另加 `detectDragGestures`。
- U4：`readerTextFieldLongPressAnchor` 的长按阈值与 BasicTextField 自身选字的**先后时序**（`ReaderTapObservation.kt:207–208` 自注「需真机复核」）。
- U5：`ACTION_PROCESS_TEXT` 在无字典应用的设备上的真实回退（静态只能看到三段式逻辑正确）。
- U6：TalkBack 下的手柄语义、触摸目标与焦点顺序（本报告仅做静态阅读，未验证无障碍实际表现）。
- U7：Z-3 的触发概率与是否有真实用户路径。
- U8：`ReaderVolumeTurn`/硬件键翻页是否也走同一条不清选区的路径（静态看 `pagedHardwareTurnRequest` → `PagedReaderPageSurface` 的 `turnRequest` 通道，与手势同源，但未运行验证）。

---

## 8. 给 Codex 的复核清单

按优先级，每条给出「复核什么 / 在哪 / 期望结论」。

1. **Z-1 是否要修、归谁修**（最高优先）
   - 复核 `PagedReaderPageSurface.kt:499–506` 与 `:212`：确认 `onPrevious`/`onNext`/`requestManualReveal` 只置 `selRange` 而不回调 `onSelect`。
   - 复核 `ReaderViewModel.kt:294–296`：确认 `PageTurn` 不碰选区。
   - 判断：是纳入 S07 修（在 `onPrevious/onNext` 补 `onSelect("", -1, -1)`，或在 `PageTurn` reducer 里清选区），还是列为已知限制。**注意**：若在 `PageTurn` reducer 清选区，会同时影响「翻页立即隐藏菜单」路径之外的所有 `PageTurn` 派发点（`ReaderLayerBuilders.kt:118`、`ReaderSessionEffects.kt:239/247`），需评估对 chrome 状态机的影响。
   - 同时确认 F6/F7（切章/切书不清选区）是否属同一根因、一并处理。

2. **Z-2 三格式入口不一致是否接受**
   - 复核 `ReaderContentHostMarkdownBranch.kt:130–140` 缺 `onSelectBlock`；`ReaderContentHostEpubBranch.kt:198–200` 与 `PagedEpubContent.kt:398–413` 是整块点击。
   - 判断：EPUB legacy 的「整块选中」是否算可接受的降级（它只在崩溃守卫降级或用户手动关引擎时出现）；Markdown 滚动模式无选区是否需要产品侧确认。

3. **Z-3 零长度 source 选区**
   - 复核 `ReaderScaffold.kt:595–600` → `ReaderHelpers.kt:83–97` → `ReaderContentHostPagedBranch.kt:135` 的守卫链。
   - 建议：让 Qoder 在 `BoundedReplaceProjectorTest`/`ReplaceProjectionTest` 既有夹具上补一条「选区落在删除区间」的断言，把静态推断升为事实或证伪。

4. **Z-4 `edit` 死动作**
   - 复核 `SelectionActionSettings.kt:84`（定义）、`:133`（`pickableDefs` 含它）、`SelectionSubPage.kt:115/162`（设置页可勾选）、`ReaderSelectionToolbar.kt:162–177`（无 `edit` 分支）。
   - 判断：是从 `pickableDefs()` 里排除（等 S3a 接线后再放出），还是在设置页禁用该 pill。

5. **Z-5 复制反馈**
   - 复核 `ReaderLayerBuilders.kt:341` 与 `ClipboardUtils.kt:34–53` 的返回值语义。
   - 判断：是否改为按返回值给「已复制」/「复制失败」两种提示。

6. **Z-6 非分页路径不渲染高亮**
   - 复核 `ReaderContentHostEpubBranch.kt` / `PagedEpubContent.kt` / `ReaderMarkdownRender.kt` 中 `highlights` 计数为 0。
   - 判断：是否属于已知范围限制（用户在这两条路径存的高亮在别处能看到）。

7. **Z-7 死回调**
   - 复核 `ReaderSheetHost.kt:159` 与 `ReaderLayerBuilders.kt:522`。判断是删除还是接线。

8. **Z-9 繁体显示未接线**
   - 复核 `PagedTxtReaderHost.kt:374/388–389` 与 `PagedReaderPageSurface.kt:437` 的调用。判断归属（阅读显示切片还是 S07）。

9. **边界归属裁决**
   - `ReaderViewModel.kt`（442 行）、`ReaderLayerBuilders.kt`、`ReaderInteractionLayer.kt`、`PagedTxtReaderHost.kt`、`PagedEpubContent.kt`、`SelectionActionSettings.kt`+`SelectionSubPage.kt` 六个共享文件必须 hunk 级拆分（明细见 6.2）。
   - 明确 `ReaderContentHostEpubBranch.kt` 的唯一一行（`tapActionPreset`）**不属于 S07**。

10. **需真机补的证据**
    - U2（窄屏 5 等分标签）、U3（透明手柄可拖动）、U4（长按时序）、U6（TalkBack 手柄语义）。

---

## 9. 附录：关键符号索引

| 关注点 | 文件:行 |
|---|---|
| 选区状态定义 | `ui/screen/reader/ReaderScreenState.kt:30–41` |
| 选区 action 定义 | `ui/viewmodel/ReaderModels.kt:148–154` |
| 选区 reducer | `ui/viewmodel/ReaderViewModel.kt:318–339` |
| `PageTurn` / `LoadChapter` 不碰选区 | `ui/viewmodel/ReaderViewModel.kt:294–296` / `:274` |
| 切书只清 chrome + query | `ui/viewmodel/ReaderViewModel.kt:537–541` |
| 规则保存成功清选区 | `ui/viewmodel/ReaderViewModel.kt:450–462` |
| 分页长按选句 | `feature/reader/pager/PagedReaderPageSurface.kt:316–330` |
| 分页手柄拖动 | `feature/reader/pager/PagedReaderPageSurface.kt:240–312` |
| 翻页只清本地不回调 | `feature/reader/pager/PagedReaderPageSurface.kt:212 / 499–506` |
| 单击/双击清选区（有回调） | `feature/reader/pager/PagedReaderPageSurface.kt:332–345` |
| 3 参 → 4 参适配 | `ui/screen/reader/ReaderContentHostPagedBranch.kt:118–124` |
| display↔source 换算 | `feature/reader/pager/TxtPagedController.kt:117–129 / 209–219` |
| 页级命中与矩形 | `feature/reader/pager/PageSelection.kt:22 / 35 / 77 / 102 / 125 / 145` |
| 偏移映射契约 | `feature/reader/rules/TextOffsetMap.kt:6–14 / 48 / 59` |
| 滚动单元投影 | `feature/reader/rules/ScrollUnitProjection.kt:9–32` |
| 滚动 TXT 选区接线 | `ui/screen/reader/ReaderContentHostPlainTextBranch.kt:312–319 / 348–377 / 390–395` |
| 长按旁听器 | `ui/screen/reader/ReaderTapObservation.kt:187–240` |
| 选词粒度 | `ui/screen/reader/ReaderSelectionGranularity.kt:35–74` |
| Markdown 滚动无选区 | `ui/screen/reader/ReaderContentHostMarkdownBranch.kt:130–140` |
| EPUB legacy 整块点击 | `ui/screen/reader/ReaderContentHostEpubBranch.kt:198–200`、`content/PagedEpubContent.kt:398–413` |
| 工具条动作门控 | `ui/screen/reader/ReaderSelectionToolbar.kt:106–124` |
| 工具条动作分发 | `ui/screen/reader/ReaderSelectionToolbar.kt:162–177` |
| 工具条可见性门控 | `ui/screen/reader/ReaderInteractionLayer.kt:353` |
| 选区动作回调构造 | `ui/screen/reader/ReaderLayerBuilders.kt:312–362` |
| locator 构造 | `ui/screen/reader/ReaderProgressActions.kt:30–42` |
| 选区 source 区间回查 | `ui/viewmodel/ReaderSearchIndexCoordinator.kt:57–66` |
| 高亮 payload 长度 | `ui/screen/reader/ReaderHelpers.kt:83–97` |
| 外部动作 | `ui/screen/reader/ReaderSelectionExternalActions.kt:56–95 / 116–162` |
| 动作定义与门控数据 | `data/settings/SelectionActionSettings.kt:46–58 / 78–103 / 133–193` |
| 设置页动作选择 | `ui/screen/profile/SelectionSubPage.kt:115 / 162` |
| 剪贴板写入语义 | `ui/util/ClipboardUtils.kt:19 / 34–53` |

---

**等待 Codex 主任务复核，未领取下一片。**
