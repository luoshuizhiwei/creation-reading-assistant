# 替换净化规则接入正文渲染 — 接线设计

> 状态：**片 0–3 有历史证据；安全滚动 TXT 实现存在但最新真机复验 FAIL。**规则保存/预览不能证明正文替换。需以 `ScrollReplaceTrace` 定位组装、正文绑定和投影链；本设计中的“完成”仅是旧实现记录，不代表当前验收。EPUB 分页后续证据见交接第 28–29 节；Markdown 仍需结构映射契约。（2026-09-09 更正）
> 当前生产范围：小型 TXT、流式大 TXT（新分页引擎）和具备 `ScrollingTxtChapterSource` 完整逻辑章作用域的滚动 TXT；不能把本切片表述为全格式完成。

## 1. 现状

引擎、存储、UI 三层已齐备且各有单测：

- 引擎：`feature/reader/rules/RuleEngine.applyReplace`、`ReplaceProjection`（display↔source
  双向偏移映射）、`TextOffsetMap`、`BoundedReplaceProjector`（有界章级投影 seam）。
- 存储：`ReaderTextRuleEntity` + `RulesRepository`（GLOBAL / PER_BOOK 作用域、启停、排序）。
- UI：`ReaderRulesSheet` 增删改 / 启停 / 排序 / **替换预览**。

2026-08-21 后，`RuleSnapshot.effectiveReplace` 已进入分页 source 构造：小型 TXT 通过
`ReplacedChapterSource` 产生 display 文本，分页缓存身份包含 `ReplaceProfile.key`；控制器与
宿主已覆盖进度/打开、高亮、TTS 句高亮、搜索命中和选区的 source↔display 映射。新高亮在
兼容 payload 中保存 source 长度，旧记录继续回退到文本长度。

2026-08-20 会话：流式大 TXT 补全完整逻辑章节 source 接缝——PlainTextDocument 新增
`readChapterRawText`，PagedChapterSource 的 TxtChapterSource 改为按逻辑章读取原文，
`PagedChapterSource.replaceProjectionScopeIsComplete` 在 TXT + 分页引擎路径置 true，
`ReaderReplacementCapability` 从「hasStreamingDocument」判定改为「完整可投影章节 source」
判定。因此现在 TXT（无论小文件还是流式大文件）在分页引擎路径下均通过能力判定，
统一包装 `ReplacedChapterSource`。安全的滚动 TXT 则由 `ScrollingTxtChapterSource` 按
完整逻辑章投影、再按 `ReadingUnit` 有界渲染；真正 legacy/不完整作用域、EPUB、Markdown
仍被明确挡在门外。

2026-08-21 会话：三项回归修复（大文件性能 & 内存 & 能力判断虚假）：

1. **分页单元 ↔ 完整作用域拆分：** `TxtChapterSource` 保留两条生产线：
   - 小文件：`TxtChapterSource(text, chapters)` → chapterCount 为逻辑章数，
     `replaceProjectionScopeIsComplete=true`，不暴露 segmented provider。
   - 流式大文件：`TxtChapterSource.fromStreaming(doc, fileIndex)` → chapterCount
     为 ReadingUnit 有界 segment 数（单 segment ≤ PlainTextDocument.MAX_WINDOW_CHARS），
     无目录 50MB 切为 ≥100 个 segment，source 偏移连续无重复/缺口；
     同时实现 `ReplaceProjectionScopeProvider`，用 TxtFileIndex 元数据判断整章大小：
     - 逻辑章 ≤ 256K 字符 → `Exact`，按需整章读取 + 章级投影一次 +
       同一逻辑章下相邻 ReadingUnit 共享投影；
     - 逻辑章 > 256K → `UnsupportedTooLarge(actualSourceLength)`，
       直接用原 ReadingUnit 原文分页 + 一次性超限提示，**不触发整章分配**；
     - 无法证明完整作用域 → `Incomplete`，保持原文。
2. **ReplacedChapterSource 有界 LRU 缓存：** 容量 = 3（当前章 + 前 + 后）。
   无界 `mutableMap` 替换为 `BoundedLruCache(maxSize=3)`，超过后 LRU 淘汰最久未用项。
   规则 `ReplaceProfile.key` 变化后 key 不同，旧投影自动不复用；
   `UnsupportedTooLarge` 路径不缓存整章 source；synchronized 锁只保护缓存读写，
   文件 IO + 正则投影在锁外完成，避免长时间持有全局锁。
3. **UI 能力真实化：** `ReaderPagerEngineState` 新增 `replacementAvailability`：
   `PagedReplacementAvailability` 枚举（APPLIED / SOURCE_UNAVAILABLE /
   INCOMPLETE_SCOPE / ESTIMATED_COORDINATES / OVERSIZED_CURRENT_CHAPTER /
   NO_EFFECTIVE_RULES）。`ReaderSheetHost` 和 `readerReplacementCapability()`
   直接消费 availability，不再自行推断 `state.document.isTxt && state.ui.pagerEngineOn`。
   source 未构建、章节为空时返回 SOURCE_UNAVAILABLE，不会显示可用入口。

4. **TDD 覆盖（JVM 1432 项全绿 + 58 项定向）：**
   - 新增/重写 `StreamingBoundedRegressionTest` 覆盖：50MB 不分单段、
     MAX_WINDOW_CHARS 有界上限、5MB 多分片段、segment 偏移连续无缺口、
     超限不用整章、超限拼接=原文、超限回调≤1 次、
     UTF-8/UTF-16LE/GB18030 source 偏移与索引匹配。
   - 重写 `StreamingCompleteChapterSourceTest`：≤256K 跨原 ReadingUnit 正则匹配、
     投影后 source↔display 坐标往返正确；
   - 重写 `PagedChapterSourceTest`：移除"整章读取成功=正确"的破损测试，
     改为验证 segment 粒度和单次读取上限；
   - `ReplacedChapterSourceTest`：新增 LRU 容量≤3、访问第 4 章淘汰、
     规则 key 变化不复用；
   - `ReaderReplacementCapabilityTest`：消费 availability 真实路径 +
     isTxt/pagerEngineOn 派生兼容路径双入口；source 未构建/章节为空不显示可用。

2026-09-09 复核：安全的滚动 TXT 已由 `ReaderContentHostPlainTextBranch` 构造
`ScrollingTxtChapterSource`，`preparePagedReplacement` 以完整逻辑章投影一次，
`ScrollUnitProjection` 再把显示、选区、搜索、高亮与 TTS 的坐标映射回 source。该路径的
`ScrollingTxtChapterSourceTest` 同时锁定跨 ReadingUnit 正则替换和双向映射。

剩余缺口仅是**真正 legacy 或不完整投影**的 `chapterBlocks` / `blockGlobalOffsets` 路径，以及
EPUB 与 Markdown 的结构保真设计与测试契约。规则面板直接消费实际渲染路径的
`PagedReplacementAvailability`；不可证明完整 source 的路径必须隐藏替换入口并解释保留原文。

## 2. 核心难点：坐标双空间

所有持久化数据（高亮、书签、进度、锚点缓存、页索引）与搜索 / TTS / 选区全部使用
**source 坐标**（原始正文字符偏移）。把渲染文本换成投影后的 **display 文本**后，
每一处「章内偏移 ↔ 全书偏移」的换算边界都必须经过投影映射；漏掉任何一处，
已有高亮就会持久化到错误位置（数据损坏级 bug，且用户难以察觉）。

坐标消费点清单（2026-08-16 逐文件核实）：

| 路径 | 文件 | 消费点 |
|------|------|--------|
| pager 引擎 | `pager/TxtPagedController.kt` | `currentPageStartAbs`、`currentPageRangeAbs`（display→source）；`open(absOffset)`（source→display）；`progressPercent`（跟随前两者自动正确） |
| pager 宿主 | `pager/PagedTxtReaderHost.kt` | `persistentHighlights`、`ttsRangeAbs`、搜索高亮、选区矩形与上报（约 6 处 `± chapterStartAbs` 换算） |
| legacy 块视图 | `ui/screen/reader/ReaderContentHost.kt` | `chapterBlocks` / `blockGlobalOffsets` / `chapterBase` / `ttsSentenceRangeInChapter` / `searchHitRangeAbs`（数据来自 `ReaderDocumentLoader`） |
| 搜索 | `ui/screen/reader/ReaderSearchLogic.kt` + `ReaderSearchEffects.kt` | 命中跳转 `navigateTo/navigateChaptered` 走 source 偏移 |
| 锚点 | `feature/reader/locator/` | 保存 / 恢复均为 source 偏移（不直接读文件） |

另有**两条独立渲染管线**（pager 引擎模式 / legacy 块视图 + 滚动模式），
必须同批接线或显式声明作用范围。

## 3. 接线方案（四片 + 流式章节 source 片，建议按序独立交付）

### 片 0 — 流式大 TXT：拆分「有界分页 segment」与「完整逻辑章投影作用域」
#### （2026-08-20 首轮方案被推翻：把 chapterCount 改成逻辑章数会让 50MB 无目录 TXT
#### 退化为整本读取；2026-08-21 采用双轨架构通过验收）

为了让流式大 TXT 不再按 ReadingUnit / overlap 近似投影，同时**保持分页读取有界**，
引入**两条独立通道** + **窄接口 Provider** 连接：

**A. 有界分页通道（分页控制器看的"章节"不是逻辑章，是 ReadingUnit segment）：**
- `TxtChapterSource.fromStreaming(doc, fileIndex)`：chapterCount = ReadingUnits 数。
  每个 segment ≤ PlainTextDocument.MAX_WINDOW_CHARS，无目录 50MB TXT 分段数≥100。
- `PlainTextDocument.readWindow(unit)`：窗口读取（不是整章读取），单次字节分配有上限。
- `chapterStartAbs(i)`/`totalChars`：source 全局偏移连续，无重复、无缺口。

**B. 完整作用域通道（正则替换的作用域）：`ReplaceProjectionScopeProvider`。**
- `scopeForSegment(segmentIndex)`：返回 `Exact` / `UnsupportedTooLarge(actualSourceLength)` /
  `Incomplete`，只依赖 TxtFileIndex 元数据即可判定超限（无需先读整章）。
- `Exact`（逻辑章 ≤ 256K 字符）：允许按需读取整章 → `BoundedReplaceProjector.project`
  执行一次完整章级投影，相邻 ReadingUnit segment 若属于同一逻辑章
  共享同一个章级 projection（不重复投影、source↔display 映射一致）。
- `UnsupportedTooLarge`（逻辑章 > 256K 字符）：不读取整章分配大 ByteArray；
  直接回到 A 通道原文有界分页 + UI 一次性超限提示。
- `Incomplete`：无法证明完整作用域（例如编码/索引异常），保持原文。

**与小文件的兼容契约（2026-08-21 定义，不可破坏）：**
- 小文件 `TxtChapterSource(text, chapters)`：chapterCount = 逻辑章数，
  `replaceProjectionScopeIsComplete=true`，`asReplaceProjectionScopeProvider()=null`
  （不暴露 segmented 能力，继续按片 1 的"整章=整章"契约走）。
- 流式大文件 `TxtChapterSource.fromStreaming(...)`：chapterCount = ReadingUnit 数，
  `replaceProjectionScopeIsComplete=false`，`asReplaceProjectionScopeProvider()≠null`
  （旧能力位关闭，整章投影由 ReplacedSegmentedChapterSource 新生产路径处理）。

**UI 能力位（2026-08-21 推翻旧 `replaceProjectionScopeIsComplete` 派生逻辑）：**
- `PagedReplacementAvailability` 由 `ReaderPagerEngineState` / 集成层在
  实际 source 构建完成后输出，包含 6 种稳定枚举：
  `APPLIED` / `SOURCE_UNAVAILABLE` / `INCOMPLETE_SCOPE` /
  `ESTIMATED_COORDINATES` / `OVERSIZED_CURRENT_CHAPTER` / `NO_EFFECTIVE_RULES`。
- `ReaderSheetHost` 与 `readerReplacementCapability()` 只消费该枚举，
  不再重新 `state.document.isTxt && state.ui.pagerEngineOn` 反推。

**ReplacedChapterSource 有界缓存（2026-08-21 推翻旧无界 mutableMap）：**
- 容量固定为 3（当前章 + 前一章 + 后一章），`BoundedLruCache` 真正 LRU 淘汰；
- ReplaceProfile.key 变化后 key 不匹配 → 旧投影不复用；
- `UnsupportedTooLarge` 路径不缓存整章 source；
- synchronized(cache) 只保护缓存读写，`delegate.loadChapter()` / 正则投影在锁外。

**JVM TDD 覆盖矩阵（2026-08-21 绿测证据 suites=5, tests=58, 0 failures）：**
- `StreamingBoundedRegressionTest`：50MB 无目录 不分单段、
  MAX_WINDOW_CHARS 有界、5MB 多分片段、segment 偏移连续无缺口、
  超限不读整章、超限拼接=原文、超限回调≤1 次、
  UTF-8/UTF-16LE/GB18030 源偏移=TxtFileIndex.charStart。
- `StreamingCompleteChapterSourceTest`：≤256K 跨原 ReadingUnit 正则匹配、
  source↔display 坐标往返正确。
- `PagedChapterSourceTest`：移除原"整章读取成功=正确"的破损断言，
  改为 segment 粒度 + 单次读取上限校验。
- `ReplacedChapterSourceTest`：容量≤3、访问第 4 章淘汰 LRU、
  规则 key 变化不复用。
- `ReaderReplacementCapabilityTest`：消费 availability 真实路径 +
  派生兼容路径双入口；source 未构建/章节为空 → SOURCE_UNAVAILABLE。

禁止整本预载。超过 256K 的整章走 `UnsupportedTooLarge` 并向 UI 抛一次性提示，不近似替换。

### 片 1 — 装饰器 source（新文件，不碰 WIP）

`ReplacedChapterSource` 实现 `PagedChapterSource`，包装任意现有 source：

- `loadChapter(index)`：读原章 source 文本 → `BoundedReplaceProjector.project` →
  返回投影后 display 文本 + blocks；缓存该章 `BoundedReplaceResult.Exact`。
- 超大章节（> `DEFAULT_MAX_SOURCE_CHARS` 256K chars）：**按契约原样返回不投影**，
  并向 UI 上抛一次性「该章过大，暂不净化」提示；禁止任何近似投影。
- `chapterStartAbs` / `totalChars` / `chapterIndexFor` 保持 source 语义直通（进度、
  章定位不受投影影响）。
- `replaceProjectionScopeIsComplete`：未设置时默认 false；调用方只有在自己能提供
  完整逻辑章级 source 时才传 true，否则 ReplacedChapterSource 包装不会启用。

### 片 2 — pager 引擎坐标翻译（`TxtPagedController`，WIP 文件）

控制器内所有「章内偏移 ↔ 全书偏移」边界经 source 接口新增的投影查询翻译：

- `currentPageStartAbs` / `currentPageRangeAbs`：display 局部 → `Exact.localDisplayToGlobalSource`。
- `open(absOffset)`：`Exact.globalSourceToLocalDisplay` 后再选页。
- 分页缓存签名：调用方传入的 `contentKey` 拼接 `ReplaceProfile.key`
  （规则集合变化 → key 变化 → 旧页索引自动失效，机制同 `TxtTocProfile.key`）。

### 片 3 — pager 宿主渲染翻译（`PagedTxtReaderHost`，WIP 文件）

`persistentHighlights` / `ttsRangeAbs` / 搜索高亮的 `range.first - chStart` 与
选区上报的 `+ chStart` 全部改经投影映射；页内文本本身即 display 文本，
渲染无需变化。

### 片 4 — legacy / 滚动路径（原始规划，已按路径拆分）

安全的滚动 TXT 不再使用本段旧的 `chapterBlocks` 方案：它通过
`ScrollingTxtChapterSource` 提供完整逻辑章 source，再用 `ScrollUnitProjection` 分发到有界
`ReadingUnit`。选区 / TTS / 搜索 / 高亮均在该 projection 的 source 坐标中往返。
`chapterBlocks` 的真正 legacy 或无法证明完整作用域的滚动路径仍不得近似包装，必须隐藏入口并
解释保留原文，避免「时灵时不灵」的静默不一致。

### 2026-08-20 实施结果

- 片 0 流式章节 source：完成。`PlainTextDocument.readChapterRawText`、
  逻辑章级 `TxtChapterSource`、`replaceProjectionScopeIsComplete` 能力位、
  `ReaderReplacementCapability` 新判定均已通过 JVM 测试；
  `StreamingCompleteChapterSourceTest` + `PagedChapterSourceTest` 覆盖 8 个要求。
- 片 1：完成。装饰器仅接受精确坐标且 `replaceProjectionScopeIsComplete=true` 的 source；
  超过 256K 字符原样返回并只提示一次。
- 片 2：完成。控制器读写 source 坐标，分页缓存自动拼接替换 profile key。
- 片 3：完成。分页高亮/TTS 句高亮/搜索/选区统一映射；投影选区的 source 长度进入高亮 payload。
- 片 4：安全滚动 TXT 实现存在，2026-09-09 真机 FAIL，尚未完成。`ScrollingTxtChapterSource` 设计上以完整逻辑章投影一次并按
  `ReadingUnit` 渲染，跨单元替换及 source↔display 映射由 JVM 测试锁定；真正 legacy/
  不完整作用域、EPUB、Markdown 继续隐藏入口并保留原文。
- 流式 TXT：章级完整 source 完成。

### 规则变更触发重建

`RulesRepository.observe(bookId)` 已暴露快照流；在 `ReaderViewModel` 订阅处把
`effectiveReplace` 的 `ReplaceProfile.key` 并入现有文档重建触发
（TOC 规则变更重建索引的既有路径同构），规则启停 / 增删 / 排序后正文即时刷新。

## 4. 验收

1. JVM 单测：装饰器 display↔source 往返映射、超大章拒绝、blocks 与 display 文本一致、
   流式完整章节数/偏移精确/跨 ReadingUnit 边界正则/超大章拒绝。
2. 真机（须真实手机）：启用规则后正文渲染替换文本（小型 + 流式 TXT，分页引擎）；
   **既有高亮不错位**；进度 / 锚点 / 书签恢复正确；搜索命中跳转正确；
   TTS 当前句高亮正确；关闭规则后立即还原为 source 文本。
3. 分页缓存：改字号或改规则都触发重新分页，页索引不串。
4. 超大章节出现明确的「暂不净化」提示，不白屏、不近似。

2026-08-21 会话自动验收（旧 2026-08-20 数字作废）：
- JVM 定向 5 类：suites=5, tests=58, failures=0；
- JVM 全量 `:app:testDebugUnitTest`：suites=153, tests=1432, failures=0, errors=0, skipped=0；
- `:app:lintDebug` / `:app:assembleDebug` / `:app:compileDebugAndroidTestKotlin`：全部通过；
- 真机 serial `c49ac6cf`（Redmi 22081212C）：
  Debug APK 安装成功、冷启动 OK、无 FATAL/ANR；
  ReaderRulesSheetTest（Compose 替换面板）OK (6 tests)，6/6 PASS
  （numtests=6；MIUI 阻止自动前台情况下，后台 MAIN+LAUNCHER+0x10008000 保活
  即可完成；不接受 0 项当作通过）。
- 带书正文矩阵：未执行（会话不修改/不导入/不删除用户真实书库；需中性测试 TXT 后单独跑）。
- `stay_on_while_plugged_in` 原值 7，会话结束后保持 7。

## 5. 明确不做

- 按 ReadingUnit / 段落拆开分别投影再拼接（`BoundedReplaceProjector` 契约禁止，
  跨边界正则会漏匹配）。
- 超限章节的固定 overlap 近似投影（同上，misleading）。
- 把 display 坐标写入任何持久化存储（display 永远是派生值）。
- 未出 DOM/结构保真设计和测试契约前做 EPUB/Markdown 纯文本正则替换。
