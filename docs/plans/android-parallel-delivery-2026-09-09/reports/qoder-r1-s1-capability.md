# qoder-r1-s1：滚动 TXT 超限能力契约与导入分支核实

- 日期：2026-09-10
- 仓库/分支：`D:/develop/Code/Codex/creation-reading-assistant`，`main`，基线 HEAD `e31db92`
- 工作方式：直接在主仓未提交的 R1 工作区上改（不新建 HEAD 工作树，否则缺少既有改动）；未 reset / checkout / reformat / stage / commit / push；未清理任何他人脏文件
- 边界：桌面端、N1/D1 文件、`docs/handoff/current.md`、数据库/schema、导航、公共 `strings` 全部未触碰；改动只落在 5 个独占文件内
- 验收状态：**未做真机验收**（任务边界），本文只给开发验证证据与 WorkBuddy 冻结后验收路径

---

## 一、目标 1：导入格式 → ReaderScreen 的真实分支（只报格式 / 对象类型 / 分支结论）

| 环节 | 代码位置 | 裁决依据 |
|---|---|---|
| `books.format` 落库 | `data/local/entity/CatalogEntities.kt:22` | Android 导入按**文件扩展名**判定：`feature/library/ShelfImporter.kt:283-284`（`substringAfterLast('.')`，`markdown→md`），白名单 `SUPPORTED_FORMATS = {epub, txt, md}`（`:711`） |
| 同步链路落库 | `data/repository/SyncRepository.kt:143` | 远端 payload 的 `format` 字符串**原样采信**（缺失时默认 `txt`），下载文件命名为 `content.<format>`（`:361`、`:377-378`），不校验字节魔数 |
| 阅读器分支唯一入口 | `ui/viewmodel/ReaderDocumentLoader.kt:59` | `when (metadata.format.lowercase())`：`"epub"` → `ReaderLoadedContent.Epub`；`"md"/"markdown"` → `Markdown`；其余 → `Text`。**没有任何文件名 / 正文嗅探** |
| EPUB 标签下若是纯文本 | `feature/reader/EpubParser.kt:100`（`ZipFile` 直接抛 `ZipException`，`try` 无 `catch`）、`:134-136`（无章即抛）、`ReaderDocumentLoader.kt:63-65` | 抛错「EPUB 中没有可阅读章节」→ 阅读器报错页，**不会**渲染成 TXT 正文 |
| ReaderScreen 取值 | `ui/screen/reader/ReaderScreen.kt:183-190` | `epubBook = (content as? Epub)?.book`，`plainContent = (content as? Text)?.fullText.orEmpty()`；EPUB 书的 `plainContent` 恒为空串，`isTxt=false` |
| 滚动 TXT source 组装门 | `ui/screen/reader/ReaderPagerEngineState.kt:268` | `epubBook != null`（或 `markdownDocument != null`、`pagerEngineOn`）时 `scrollPreparedSource` 直接为 `null` |
| 正文分支 | `ui/screen/reader/ReaderContentHost.kt:174-190` → `ReaderContentHostEpubBranch.kt:141-167` | EPUB 滚动渲染 `chapterBlocks`（`DocBlock`），只走 `EpubChapterSource` / `EpubReplacedChapterSource`；**不读** `plainContent`，与 TXT 滚动链完全隔离 |

结论：

1. **「标为 EPUB、内容像 TXT」的复现样本不会进入本次 TXT 修补路径。** `format=="epub"` 时 `ScrollingTxtChapterSource` 分支被 `ReaderPagerEngineState.kt:268` 硬性关死；替换走 `EpubReplacedChapterSource`（结构保真投影），与本轮改动的 `preparePagedReplacement` TXT 分支是两条链。
2. 若该书真能打开并显示正文，则它是**合法 zip 的 EPUB**（例如整本正文装在单章内 → 观感像 TXT）；若它不是 zip，则在 `EpubParser.kt:100` 抛错，不可能表现为 TXT 正文。两种情形都不是 TXT 投影链缺陷。
3. 因此这属于**导入/同步侧的格式标注与真实字节不一致**问题（`ShelfImporter` 只看扩展名、`SyncRepository` 原样采信远端 `format` 并按其命名落盘文件）。按任务要求**停止扩展修复**，只在 §六 提最小 SEAM REQUEST。
4. 附带核实：本地导入的书 id 是 `UUID.randomUUID()`（`ShelfImporter.kt:459`），当前仓库代码不生成 `epub_xxxxx` 形态的 id；该形态只能来自远端 payload 的 `env.id`（`SyncRepository.kt:138`）。不读取、不记录任何真实书名 / 正文 / 规则内容。

---

## 二、目标 2：能力状态契约修正

修正前的事实（Codex 报告 §2 行为矩阵里的第 3 行）：无目录大书整本坍缩为单一投影作用域时，`preparePagedReplacement` **无条件**给出 `APPLIED`，而 `ReplacedSegmentedChapterSource.loadChapter` 随后对每个 segment 判 `UnsupportedTooLarge` 并回退原文 → 「入口显示可用、正文必然原文」，用户只在滚到正文时才发现规则不生效。1:1 完整作用域路径同型：无目录时 `TxtChapterDetector` 兜底成单章「全文」（`TxtChapterDetector.kt:249`），整章超过 `BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS` 后逐章回退原文，标签仍是 `APPLIED`。

改动（全部在独占文件内）：

1. `feature/reader/pager/ReplacedChapterSource.kt`
   - `PagedReplacementAvailability` 新增两个显式降级值：`PARTIALLY_APPLIED`（部分作用域可投影）、`ALL_SCOPES_OVERSIZED`（无任何可投影作用域，正文必然原文）。既有值一字未改，非独占文件的 `when` 与断言不受影响（除 `ReaderReplacementCapability.kt` 外无人对该枚举做穷尽匹配）。
   - `preparePagedReplacement` 在**装配期**先分类再裁决：`classifyChapterScopeSpans`（1:1 路径，用坐标元数据算真实章跨度，前置条件是估算坐标已在上方被拒）与 `classifyProviderScopes`（segment 路径，逐逻辑章读 `scopeForSegment` 元数据）。
   - 不变式：`APPLIED` 只在 `ALL_EXACT` 时出现；`MIXED → PARTIALLY_APPLIED`（仍然包装装饰器，可投影章照常替换）；`NOTHING_EXACT → ALL_SCOPES_OVERSIZED`（**同样包装装饰器**，降级只由 `availability` 表达）；`chapterCount == 0 → SOURCE_UNAVAILABLE`。
   - 为什么不换成「透传 delegate」：装饰器对不可投影作用域本来就逐 segment 返回 `delegate` 原文、`projectionForChapter` 恒为 `null`、并保留每书一次的 `onUnsupportedTooLarge` 提示——渲染结果与恒等投影完全一致。换对象只会悄悄改掉「超限回调」这条既有契约（非独占的 `StreamingBoundedRegressionTest` 第 7、9 例正锁着它），而契约要修的是**宣称**，不是对象身份。
   - 分类只读元数据，**绝不触发** `ReplaceProjectionScope.Exact.loadFullChapterText()`，因此超限章不会在装配期被整章读入；一旦发现「有可投影 + 有降级」即提前返回。
2. `ui/screen/reader/ReaderReplacementCapability.kt`
   - `Available` 的语义在 KDoc 中收窄为**仅表示规则可管理**，明确禁止把 `APPLIED` 当「正文已替换」用。
   - `PARTIALLY_APPLIED` / `ALL_SCOPES_OVERSIZED` → `Available`：规则仍可查看、编辑、删除、新增（`Unavailable` 会让 `ReaderRulesSheet.kt:417/528` 直接隐藏「替换净化」tab，等于让用户以为规则丢了）。
   - 两者各自给出启动提示文案（`readerReplacementStartupNotice`），由 `ReaderScreen.kt:441-449` 既有链路一次性提示：
     - `ALL_SCOPES_OVERSIZED`：「本书正文没有可投影的作用域（整书超出替换净化可处理的长度上限），正文已保留原文；替换规则仍可继续管理，但不会对正文生效。」
     - `PARTIALLY_APPLIED`：「部分章节超出替换净化可处理的长度上限，这些章节已保留原文，其余章节按规则替换。」
3. `ui/screen/reader/ReaderPagerEngineState.kt`：仅同步两处注释，说明降级现在由 `preparePagedReplacement` 给出显式状态，`onUnsupportedTooLarge` 继续承担「渲染时逐章超限」的即时提示（`MIXED` 与 `ALL_SCOPES_OVERSIZED` 都会上抛，每书一次）。生产逻辑未改，`effectiveReplacementAvailability` 原样透传新状态。
4. `ui/screen/reader/ReaderTextIndex.kt`：只做 `:155` 的排版整理（函数签名与首行语句挤在同一行），无语义改动。

修正后行为矩阵：

| 场景 | availability（改前 → 改后） | 正文 | 规则管理 |
|---|---|---|---|
| 有目录、每章都在上限内 + 有规则 | `APPLIED`（不变） | 逐章精确投影，替换生效，坐标仍为 source | 可管理 |
| 无章小书（整书 scope < 上限）+ 有规则 | `APPLIED`（不变） | 整书单 scope 投影生效 | 可管理 |
| **无章大书（整书 scope > 上限）+ 有规则** | `APPLIED` → **`ALL_SCOPES_OVERSIZED`** | **原文**（恒等投影 + source 坐标） | **仍可管理**，启动提示明确说明不会作用于正文 |
| 一个正常章 + 一个超限章 | `APPLIED` → **`PARTIALLY_APPLIED`** | 正常章替换、超限章原文 | 可管理，启动提示点明部分章保留原文 |
| 无生效规则 | `NO_EFFECTIVE_RULES`（不变） | 原文 | 可新增 |
| 空 source / 无章节 | 新增 `chapterCount==0 → SOURCE_UNAVAILABLE` | 原文 | 隐藏入口（现状） |
| EPUB（`epubBook != null`） | `APPLIED`（本轮未改，见 §五.2） | 结构保真投影，逐章超限自行回退 | 可管理 |

---

## 三、目标 3：JVM 回归（`ScrollReplaceChainRegressionTest`，8 例全绿）

| 用例 | 锁住的事实 |
|---|---|
| `scroll chain applies replacement to every unit display text` | 装配 → 正文绑定 → unit 投影全链生效，display 增长与命中数一致，坐标空间仍为 `SOURCE` |
| `scroll chain without rules keeps original text with source coordinates` | 无规则时原文 + `NO_EFFECTIVE_RULES` |
| `real detector chapters tile the book and keep every unit inside its chapter` | **真实 `TxtChapterDetector` / `PlainTextDocument` 章节**（非假 `DocChapter`）完整铺满全文，`buildChapterAlignedPlainUnits` 每个 unit 都落在自己声明的逻辑章区间内、切块无损覆盖全文、章编号为真实值 |
| `multi chapter book larger than the bound still projects per chapter` | 整书 > 256K 但每章在上限内 → 裁决按章不按书：`APPLIED`、全部替换生效、`localDisplayToGlobalSource(0)` 仍等于 `unit.charStart`、每章都有 `Exact` 投影、零次超限回调 |
| `single whole-book scope over the bound is not applied` | 无目录超限书：`ALL_SCOPES_OVERSIZED`（≠ `APPLIED`）、source 仍是 `ReplacedSegmentedChapterSource`、逐 unit 正文等于原文且 `projectionForChapter` 为 `null`、`localDisplayToGlobalSource(0)` 仍等于 `unit.charStart`、超限回调恰好一次、能力仍可管理、启动提示非空且含「保留原文」 |
| `complete-scope single whole-book chapter over the bound is not applied` | 1:1 路径同型缺陷：真实检测器兜底的单章「全文」超限 → `ALL_SCOPES_OVERSIZED` + `ReplacedChapterSource` 仍返回整章原文、`projectionForChapter(0)` 为 `null`，并证明 `detect` 无命中时返回单章而非空列表 |
| `mixed normal and oversized chapters report partial application` | 正常章 + 超限章：`PARTIALLY_APPLIED`、正常章真替换 / 超限章保原文、两者坐标都回 source、`projectionForChapter` 分别非空与为空、超限回调恰好一次、能力 `Available`、提示含「保留原文」 |
| `applied availability is the only state claiming the body is projected` | 两个降级态都必须有启动提示，`APPLIED` 必须无提示（防止反向误报） |

---

## 四、命令、退出码、测试数（真实记录）

```bash
# 均在 android/ 目录执行；GRADLE_USER_HOME=D:/develop/env/gradle
# 日志一律重定向到文件后再单独取退出码，避免管道掩盖失败
```

| # | 命令 | 退出码 | 事实 |
|---|---|---|---|
| 1 | 定向 4 类（`ScrollReplaceChainRegressionTest` / `ReaderReplacementCapabilityTest` / `ScrollingTxtChapterSourceTest` / `PlainTextDocumentTest`） | **1** | `compileDebugKotlin` 通过，`compileDebugUnitTestKotlin` 失败：测试文件 `:83` 字符串模板后接中文被并入标识符（`Unresolved reference 'phrase依然运转'`），已改为 `${phrase}` |
| 2 | 同上（修正后） | **0** | BUILD SUCCESSFUL in 1m 35s；4 类 8 / 16 / 3 / 11 全绿（`test-results` XML 时间 02:31:16，晚于最后一次生产代码改动 02:23:33） |
| 3 | 补充回归：`feature.reader.pager.*` + `ui.screen.reader.*` + `:app:compileDebugKotlin` | **1** | 卡在 `:app:compileDebugKotlin`：`ui/screen/profile/ProfileRoute.kt:189/191` 报 `Unresolved reference 'navigationTargetOrNull'`。该文件与其引用的 `feature/annotations/AnnotationNavigationTarget.kt` **都不是本轮独占**，且正处于其他 agent 的写入过程中（前者随后在 02:34:59 又被改了一次）→ 共享工作树的并发写中间态，不是本轮改动的编译错误 |
| 4 | `:app:compileDebugKotlin` | **1** | `:app:kspDebugKotlin` 报 `Could not delete app/build/generated/ksp/debug/java/byRounds` —— 并发的 Gradle 进程占着 build 目录（当时存活 2 个 java 守护进程，00:08 与 02:27 启动）。未杀任何进程、未清 build 目录 |
| 5 | `:app:compileDebugKotlin`（重试一次） | **0** | BUILD SUCCESSFUL in 36s |
| 6 | 补充回归重跑（同 #3） | **1** | 编译中途 `FileAnalysisException: NoSuchFileException .../generated/ksp/debug/java/.../PageIndexStore_Factory.java` —— 仍有并发构建在删改 KSP 产物。改为**直接读非独占测试源码**核对连带影响，不与他人抢构建 |
| 7 | 最终合并跑：定向 4 类（按包覆盖）+ `PlainTextDocumentTest` + `:app:compileDebugKotlin` | **0** | BUILD SUCCESSFUL in 3m 19s；`:app:compileDebugKotlin` 在同一次调用内成功 |

**#6 的直接产出（重要）**：读 `feature/reader/pager` 下消费同一裁决点的非独占测试，发现 `StreamingBoundedRegressionTest` 第 7 例把 `prepared.source` 强转 `ReplacedSegmentedChapterSource`、第 9 例锁「超限回调恰好一次」，而当时 `NOTHING_EXACT` 的实现是**透传 delegate** → 必然破坏这两例（越界改他人测试不允许）。据此把降级改回「仍包装装饰器、只降级 `availability`」（见 §二.1），生产代码与回归同步调整后才有 #7 的绿。

#7 的实测总数与关键非独占类（`failures / errors / skipped` 全为 0）：

| 类 | tests | 归属 |
|---|---|---|
| `ScrollReplaceChainRegressionTest` | 8 | 独占（本轮交付） |
| `ReaderReplacementCapabilityTest` | 16 | 独占 |
| `ScrollingTxtChapterSourceTest` | 3 | 非独占 |
| `PlainTextDocumentTest` | 11 | 非独占 |
| `StreamingBoundedRegressionTest` | 9 | 非独占（超限回调 / 装饰器身份契约） |
| `ReplacedChapterSourceTest` + `ReplacedChapterSourceConcurrencyTest` | 6 + 16 | 非独占 |
| `StructuredReplacementContractTest` | 5 | 非独占（EPUB / Markdown / 估算 / 不完整四类裁决契约） |
| `StreamingCompleteChapterSourceTest` / `EpubReplacedChapterSourceTest` / `PagedChapterSourceTest` | 15 / 7 / 21 | 非独占 |
| **合计（`feature.reader.pager.*` + `ui.screen.reader.*` + `PlainTextDocumentTest`）** | **82 类 / 526 例** | **0 失败 / 0 错误 / 0 跳过** |

`#2` 的 4 类数字已被 `#7` 覆盖复核（同一轮改动之后重跑，仍为 8 / 16 / 3 / 11 全绿）。

---

## 五、未完成项与真实限制

1. **未做真机验收**（任务边界）。菜单可点、预览命中、JVM 全绿都不能替代「正文真实被替换」；契约修正后的提示与降级必须由 WorkBuddy 在手机上确认。
2. **EPUB 分支的 `APPLIED` 未收窄**：`preparePagedReplacement` 对 `EpubChapterSource` 仍无条件返回 `APPLIED`，而 `EpubReplacedChapterSource` 会在单章超限时回退原文。之所以不在本轮一并分类：EPUB 的章长度是 ZIP 解压字节**估算值**（`EpubChapterSource.chapterLengthsAreEstimated=true`、`ReaderTextIndex.kt:203-220` 注释），真实块长度只有解析该章块后才知；用估算值做「超限」判定会误降级本来能投影的章，违背本仓库「禁止近似裁决」的既有原则。留作后续刀次（需要 EPUB 块级长度元数据接口）。
3. **降级提示只在进入阅读器的 Snackbar 一次**，「替换净化」tab 内部没有常驻横幅。`ReaderReplacementCapability.Available` 是 `data object`，非独占的 `ReaderRulesSheet` 用 `is Available` 同时决定「tab 可见」与「无提示」，`Unavailable` 又会整个隐藏 tab —— 在不越界的前提下无法做到「tab 保留 + tab 内提示」，故提 SEAM REQUEST（SR-2）。
4. **装配期作用域扫描的开销**：`classifyProviderScopes` 对 segment 源是 O(分页单元数) 的元数据遍历（`ScrollingTxtChapterSource.scopeForSegment` 为 map 查找），只发生在 `remember` 的 key 变化时（换书、改规则），最坏情形是「全部可投影」的大书；带 `MIXED` 提前退出。真机上首次打开百 MB 级 TXT 的耗时应由 WorkBuddy 顺带观察（§七.5）。
5. 未处理 Codex 报告 §5 提到的既有独立缺陷（高亮列表断链、`SearchHighlightColorTest` 等待补项），不在本轮边界。
6. **验证对象是一个正在被别人同时改写的共享工作树**：本轮 7 次调用里 3 次失败全部来自他人的写入中间态与并发构建（§四 #3/#4/#6）。因此「全绿」只对 **02:45:32 那次快照**成立；冻结前集成者必须在工作树安静后重跑一次同一命令，不能沿用本轮结论。
7. 未运行 lint / 完整 build / desktop Beta（按并行交付约定归集成者串行执行）。

---

## 六、SEAM REQUEST

### SR-1：导入 / 同步侧格式标注必须与实际字节一致（不阻塞本轮）

- **目标文件**：`feature/library/ShelfImporter.kt`、`data/repository/SyncRepository.kt`（均非本次独占）
- **需要的接口 / 字段 / 行为**：
  1. 落库前按内容判定格式：zip 魔数 `PK\x03\x04` → `epub`；否则按扩展名归类到 `txt/md`。当「扩展名/payload 声称 epub 但字节非 zip」时，**不得**写入 `format=epub`，应按真实类型落库或标记 `content_status` 为需重下。
  2. `SyncRepository.kt:143` 的 `format = str(p,"format") ?: "txt"` 改为经同一校验的白名单归一（含 `markdown → md`），并保持下载文件名 `content.<真实格式>`（`:361`）。
  3. 对历史脏行提供只读诊断入口（例如 `BookDao` 查询「format 与文件魔数不一致」的行数），不自动迁移用户数据。
- **调用位置**：`ShelfImporter.kt:283-284/326-330/420/443/485/528`，`SyncRepository.kt:138-143/361/377-378`；消费方 `ReaderDocumentLoader.kt:59`
- **为什么现有接口不足**：`ReaderDocumentLoader` 只认 `metadata.format`，无嗅探兜底（这是刻意的单一真源设计），所以一旦标注错误：非 zip 会直接抛 `ZipException` 变成「打不开」，合法 zip 则被路由到 EPUB 链，永远进不了 TXT 修补链——即本轮样本的真实归属。
- **兼容方案与测试**：格式判定收敛成一个纯函数（输入扩展名 + 前 4 字节，输出 `epub/txt/md` 或 `Invalid`），导入与同步共用；JVM 用例覆盖「.epub 扩展名 + 纯文本字节」「同步 payload 声称 epub + 实际 zip」「payload 缺失 format」。旧数据不做自动迁移，只在阅读器报错文案上区分「格式标注与文件不符」。
- **是否阻塞本轮**：否（本轮 TXT 契约修正与它正交）。

### SR-2：让降级能力在「替换净化」tab 内也能常驻提示（不阻塞本轮）

- **目标文件**：`ui/screen/reader/sheets/ReaderRulesSheet.kt`（非独占）、必要时 `ui/screen/reader/ReaderScaffold.kt`
- **需要的接口 / 字段 / 行为**：`ReplacementUnavailableNotice(message)` 目前只在 `capability is Unavailable` 时渲染，而 `Unavailable` 同时会隐藏「替换净化」tab（`ReaderRulesSheet.kt:417/528`）。需要一个「可管理 + 有告警」的表达：把入参改成携带 `manageable: Boolean` 与 `bodyNotice: String?`（或新增 `Available(message)` 形态），tab 可见性只看 `manageable`，提示条只看 `bodyNotice != null`。
- **调用位置**：`ReaderSheetHost.kt:222/237`、`ReaderScaffold.kt:453`、`RulesSheet`（`ReaderRulesSheet.kt:395-567`）
- **为什么现有接口不足**：本轮契约要求「已有规则仍应可管理，但不得宣称正文已经替换」。现有二值 `Available/Unavailable` 只能二选一：`Unavailable` 保真但让用户以为规则丢了，`Available` 保住入口但把「正文未替换」只能塞进一次性 Snackbar。
- **兼容方案与测试**：保留 `Available`/`Unavailable` 两个形态并新增可选提示字段（默认 `null`）即向后兼容；`ReaderReplacementCapabilityTest` 增一例断言降级态 `manageable && bodyNotice != null`。
- **是否阻塞本轮**：否（降级事实已由 `availability` + 启动提示 + 逐章 `replaceProjectionNotice` 三处表达）。

---

## 七、给 WorkBuddy 的冻结后验收路径（真机唯一验收人）

前置：先 `adb devices` 核对序列号，之后每条设备命令都带 `-s <serial>`；不使用模拟器；只读验收，不修改用户原书。

1. **冻结并构建**：`.\gradlew.bat :app:assembleDebug`（PowerShell，`$env:GRADLE_USER_HOME="D:\develop\env\gradle"`），如实记录安装方式（`install_with_confirm.ps1` 优先，超时/MIUI 阻止才回退 `adb -s <serial> install -r <绝对路径 apk>`，回退不算 PASS）。
2. **多章 TXT 正常链（回归，不许变）**：滚动模式打开一本**有目录**的 TXT → 新增并启用一条替换规则 → 断言保存后**当前屏正文真的变化**（截图 + uiautomator 文本双证）→ 滚离再返回 / 退出重进 / 杀进程重启后仍生效 → 禁用规则正文立即还原、重新启用再次生效。
3. **能力提示与状态一致性（本轮新增断言）**：
   - 一本**无目录且整本超过投影上限**的 TXT（>256K 字符）：进入即出现「本书正文没有可投影的作用域（整书超出替换净化可处理的长度上限）…已保留原文…」提示；打开「替换净化」tab **仍能看到并编辑规则**；正文任何位置都不出现替换结果；滚动到超限内容时仍应看到既有的单章超限提示（每书一次）。
   - **不得**再出现 logcat `ScrollReplaceTrace` 中 `availability=APPLIED` 而逐 unit `exact=false` 的组合；本轮形态应为装配行 `availability=ALL_SCOPES_OVERSIZED` + 逐 unit `source=ReplacedSegmentedChapterSource, exact=false`（装饰器保留，只是不再宣称已替换）。
   - 一本**混合书**（至少一章正常 + 一章超限）：提示为「部分章节…已保留原文，其余章节按规则替换」；正常章正文替换生效、超限章保持原文；logcat 中 `availability=PARTIALLY_APPLIED`，正常章 unit `exact=true`、超限章 unit `exact=false` 且各章最多一条超限提示。
4. **坐标回归（不变式）**：在替换态下建高亮、书内搜索跳转、TTS 朗读定位、阅读进度恢复，退出重进后位置均不漂移（持久化坐标始终是 source）。
5. **装配开销观察**：记录百 MB 级无目录/有目录 TXT 首次进入阅读器的可感等待；本轮装配期新增逐逻辑章元数据扫描，若明显劣化请附 logcat 时间戳回报（不要求阈值判定）。
6. **「标为 EPUB、内容像 TXT」样本单独立项**：请只报告该书 `format` 字段值、文件是否为 zip（`adb pull` 后看前 4 字节 `PK\x03\x04`）、以及打开后的现象，**不要**试图用 TXT 规则验证它——它走的不是 TXT 链；结果用于确认 SR-1。
7. 全程 logcat 无 FATAL / ANR；结束时恢复设备 `stay_on_while_plugged_in` 为测试前的值。
