# 替换净化规则接入正文渲染 — 接线设计

> 状态：**设计完成，未实施**（2026-08-16）。
> 实施前必须与 reader 引擎 WIP（`feature/reader/pager/`、`feature/reader/doc/` 等未提交改动）
> 协调文件所有权；本设计刻意不与该 WIP 抢同一批文件。

## 1. 现状

引擎、存储、UI 三层已齐备且各有单测：

- 引擎：`feature/reader/rules/RuleEngine.applyReplace`、`ReplaceProjection`（display↔source
  双向偏移映射）、`TextOffsetMap`、`BoundedReplaceProjector`（有界章级投影 seam）。
- 存储：`ReaderTextRuleEntity` + `RulesRepository`（GLOBAL / PER_BOOK 作用域、启停、排序）。
- UI：`ReaderRulesSheet` 增删改 / 启停 / 排序 / **替换预览**。

缺口：`RuleSnapshot.effectiveReplace` 与 `BoundedReplaceProjector` 在生产渲染路径
**零消费者** —— 规则当前只影响 RulesSheet 里的预览，不影响正文；分页缓存签名
（contentKey）也不含替换规则身份。

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

## 3. 接线方案（四片，建议按序独立交付）

### 片 1 — 装饰器 source（新文件，不碰 WIP）

`ReplacedChapterSource` 实现 `PagedChapterSource`，包装任意现有 source：

- `loadChapter(index)`：读原章 source 文本 → `BoundedReplaceProjector.project` →
  返回投影后 display 文本 + blocks；缓存该章 `BoundedReplaceResult.Exact`。
- 超大章节（> `DEFAULT_MAX_SOURCE_CHARS` 256K chars）：**按契约原样返回不投影**，
  并向 UI 上抛一次性「该章过大，暂不净化」提示；禁止任何近似投影。
- `chapterStartAbs` / `totalChars` / `chapterIndexFor` 保持 source 语义直通（进度、
  章定位不受投影影响）。

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

### 片 4 — legacy / 滚动路径（`ReaderDocumentLoader` 输出侧）

`chapterBlocks` 生成时应用同一投影并随块输出 `blockGlobalOffsets` 的映射版本；
选区 / TTS / 搜索焦点消费点同片 3 处理。若决定首期不覆盖本路径，
必须在设置与文档中**显式声明「净化仅在翻页引擎模式生效」**并在滚动模式下
隐藏规则入口，避免「时灵时不灵」的静默不一致。

### 规则变更触发重建

`RulesRepository.observe(bookId)` 已暴露快照流；在 `ReaderViewModel` 订阅处把
`effectiveReplace` 的 `ReplaceProfile.key` 并入现有文档重建触发
（TOC 规则变更重建索引的既有路径同构），规则启停 / 增删 / 排序后正文即时刷新。

## 4. 验收

1. JVM 单测：装饰器 display↔source 往返映射、超大章拒绝、blocks 与 display 文本一致。
2. 真机（须真实手机）：启用规则后正文渲染替换文本；**既有高亮不错位**；
   进度 / 锚点 / 书签恢复正确；搜索命中跳转正确；TTS 当前句高亮正确；
   关闭规则后立即还原为 source 文本。
3. 分页缓存：改字号或改规则都触发重新分页，页索引不串。
4. 超大章节出现明确的「暂不净化」提示，不白屏、不近似。

## 5. 明确不做

- 按 ReadingUnit / 段落拆开分别投影再拼接（`BoundedReplaceProjector` 契约禁止，
  跨边界正则会漏匹配）。
- 超限章节的固定 overlap 近似投影（同上，misleading）。
- 把 display 坐标写入任何持久化存储（display 永远是派生值）。
