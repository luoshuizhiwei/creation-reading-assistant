# Stage C 第一轮：小 TXT 保存进度时章号恒回落为「第 1 章」

- 执行人：Qoder
- 日期：2026-09-22
- 工作区：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`）
- **状态：尚未编译、尚未运行测试。** 本轮按约束只做静态检查与 diff 检查，未运行任何 Gradle / ADB / App / 真机命令。
- 上游依据：`docs/coordination/reviews/undated/agent-stage-b-core-audit.md`（窗口 D 对账确认该缺陷仍在现役代码）、`docs/coordination/legacy-stages/2026-09/2026-09-22-stage-b-investigation-prompts.md`

## 1. 缺陷与根因链（改动前）

小 TXT（正文常驻 `plainContent`、未走流式文档）滚动阅读时，书架进度卡永远显示「第1章」，与实际阅读位置无关：

1. `ReaderScreenDerivedState.kt` 的 `chapterStartOffsets` 回退链止于 `emptyList()`，只有 bookIndex / Markdown / 流式 TXT 三档；
2. 小 TXT 的章节识别结果 `txtChapters` 在同一函数里**已经算出来了**（第四档源在场，却没被接进回退链）；
3. `bookIndex` 只在 EPUB 下非空（`ReaderScreen.kt:215` `remember(epubBook) { epubBook?.let(::buildBookIndex) }`），所以小 TXT 命中第 1 步的空表；
4. 三个落库点用这张空表调 `EpubLocatorMapping.toChapterOffset`，该函数对空表恒返回 `ci = 0`（`EpubLocatorMapping.kt:16-28`）；
5. `LocatorBuilder.progressJson` 把 `"ci":0` 写进 `current_location_json`，`ShelfModels.kt:extractChapterLabel` 读回后渲染「第1章」。

修复面比 9/20 的描述更小：正文侧 `readingUnits` 早已按真实逻辑章切块（`buildChapterAlignedPlainUnits`），分页 TXT 也早已用 `TxtChapterSource(plainContent, txtChapters)`（`ReaderPagerEngineState.kt:233-234`），二者与 `txtChapters` 同属 `plainContent` 字符量纲 —— 接上第四档不引入新坐标系。

## 2. 本轮改动

| 文件 | 动作 | sha256 | 行/字节 |
|------|------|--------|---------|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreenDerivedState.kt` | 修改（+67 / -14） | `9129e9fabb98e4bfcfeea58a1fce02343be3d74c3958d2f5189f76b3adc05037` | 173 行 / 9587 字节 |
| `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderDerivedChapterMetadataTest.kt` | 新建（9 条 JVM 用例） | `1253fb6d88185f31a93bf06f52de411928e333729491e4f96100ee08c5620ab1` | 220 行 / 9206 字节 |

主文件改动只有三处：

1. 新增 `internal data class ChapterMetadata(startOffsets, titles)` 与 `internal fun selectChapterMetadata(bookIndex, markdownChapters, txtStreamingChapters, smallTxtChapters)` —— 纯函数、无 Compose 依赖，JVM 可直测；偏移与标题**一次选定同一个源**（原来是两条各自独立的 `?:` 链，同源只靠写得一样来保证）；
2. 组合层把选择结果 `remember(bookIndex, markdownDocument, txtStreamingDocument, txtChapters)` 缓存，位置移到 `txtChapters` 之后；
3. `ReaderDerivedState` 的字段与所有调用方签名**未改**，`rememberReaderDerivedState` 参数列表未改。

为什么需要 `remember`（不是省算力）：`chapterStartOffsets` 同时是 `LaunchedEffect` 的 key（`ReaderProgressEffects.kt:347` 的通用 source 路由定位）和 `remember` 的 key（`ReaderEyeCareFocusState.kt:90`）。旧代码在小 TXT 下返回 `emptyList()` 单例，天然身份稳定；改成 `txtChapters.map {}` 后每次重组都会产出新列表实例，虽然 Compose 的 key 比较走 `equals`（内容相同不会重启 effect），但让带 500 ms 防抖的进度落库依赖"内容恰好相等"不如给它稳定身份。三份 document 的 `chapters` 都是实例内 `val`（`PlainTextDocument.kt:137`、`MarkdownDocument.kt:29`），按实例身份缓存不会读到旧章表。

## 3. 章节来源优先级（新）

| 档 | 源 | 判据 | 量纲 |
|----|----|------|------|
| 1 | `bookIndex`（EPUB 全书索引） | 实例在场 | ZIP 估算字符偏移，与历史 locator 同源 |
| 2 | `markdownDocument.chapters` | 实例在场 | 规范阅读文本字符偏移 |
| 3 | `txtStreamingDocument.chapters` | 实例在场 | 流式 TXT 字符偏移 |
| 4 | `txtChapters`（**本轮新增**） | 非空即有值，识别失败自然为空 | `plainContent` 字符偏移 |
| 5 | 空表 | —— | —— |

1–3 档按**源是否在场**裁决，不按列表是否为空：文档在场但章表为空是解析器的诚实结论，继续下探会把不同量纲的偏移掺进来。第 4 档用现成的 `txtChapters`，而 `txtChapters` 在 `epubBook != null` 时恒为 `emptyList()`（其 `remember` 两个分支都要求 `epubBook == null`），所以 EPUB 不可能被 TXT 章表污染。

## 4. 行为差异面与消费点审查

唯一改变输出的路径是「`bookIndex == null && markdownDocument == null && txtStreamingDocument == null` 且小 TXT 识别出章节」：`chapterStartOffsets` / `chapterTitles` 由空表变为真实章表。EPUB / Markdown / 流式 TXT 三档优先级与改前逐值一致。

已逐个读过 `chapterStartOffsets` / `chapterTitles` 的消费点，确认不是把空表当哨兵用：

| 消费点 | 结论 |
|--------|------|
| `ReaderProgressEffects.kt:222`（滚动 500 ms 防抖落库） | 目标缺陷点，`ci` 由恒 0 变真实章号 |
| `ReaderProgressEffects.kt:274`（分页 500 ms 防抖落库，TXT 分支） | 同上；注释本就写明「用全书 chapterStartOffsets 反推」 |
| `ReaderProgressEffects.kt:617`（`sourceNavigationTargetFor`） | 非 EPUB 走 `chapterIndex = 0` 分支，不受影响 |
| `ReaderProgressEffects.kt:492/498/506`（高亮/笔记锚点解析） | 整块在 `epubBook != null` 内，不受影响 |
| `ReaderProgressActions.kt:288-296`（退出保存） | 那里已写过「小 TXT 分页下它是空列表」的绕行注释；新表非空后优先命中 `takeIf { isNotEmpty() }` 分支，与 `pagedSource` 反推同结论 |
| `ReaderProgressComputations.kt:250/254`（章节内进度） | EPUB / Markdown 分支专用；TXT 走 `txtChapters.getOrNull(txtChapterIndex)`，未动 |
| `ReaderProgressActions.kt:103-107`（`goToChapter`） | `else -> return` 对 TXT 提前返回，未动 |
| `ReaderProgressActions.kt:356/391`（按百分比 / 章内百分比跳转） | 用 `bookIndex?` 或 `txtChapters`，未动 |
| `ReaderContentHostPagedBranch.kt:59/100/109` | 分别在 `epubBook != null`、EPUB TTS、`txtStreamingDocument != null` 分支内，未动 |
| `ReaderSearchLogic.kt:80-85`（书内搜索章标签） | `isTxt` 显式短路为「全文」，未动 |
| `ReaderScreenDerivedState` → `ReaderSheetHost` 目录 | 目录标题此前已用 `txtChapters.map { it.title }`（`ReaderLayerBuilders.kt:442`），新表与之一致，属去分叉而非改口径 |
| `ShelfModels.extractChapterLabel` | 只读 `"ci"`，无写侧耦合 |

无测试或脚本按源码文本断言旧的三档链（`grep ReaderScreenDerivedState` 只命中历史评审文档；`grep chapterStartOffsets scripts/` 为空）。

## 5. 新增测试（9 条，全部 JVM 单测，无新依赖）

`ReaderDerivedChapterMetadataTest`：

1. `small txt chapters supply both offsets and titles` —— 要求项 1（小 TXT 多章的偏移 + 标题）
2. `offsets and titles never come from different sources` —— 同源不变式；每章标题写死自己的起始偏移（`"md@30"`），跨源混搭当场失败（只比 size 抓不到等长混搭）
3. `epub book index outranks every txt chapter source` —— 要求项 2
4. `markdown source keeps precedence over streaming and small txt` —— 要求项 3
5. `streaming txt source keeps precedence over small txt chapters` —— 要求项 3
6. `present but empty markdown chapter table does not fall through to small txt` —— 钉住「按源在场裁决」的语义
7. `chapter detection failure on small txt degrades honestly to empty metadata` —— 要求项 4
8. `offset inside the second small txt chapter resolves to chapter index one` —— 要求项 5（真实 `EpubLocatorMapping`，含 0 / 499 / 500 / 700 / 1300 边界与章内偏移 `co`）
9. `shelf chapter label follows the saved locator instead of pinning chapter one` —— 端到端串真实链路：选择器 → `toChapterOffset` → `LocatorBuilder.progressJson` → `extractChapterLabel`，同一条链路对空表给出「第1章」，把修复前的症状钉成断言

有牙性：把第 4 档摘掉（退回 `?: emptyList()`）会让 1 / 8 / 9 三条变红；改动 1–3 档顺序会让 3 / 4 / 5 / 6 变红。这些用例驱动生产函数，没有自点自测的常量比常量。

## 6. 尚未验证的编译 / 运行风险（按可疑度排序）

1. **`rememberReaderDerivedState` 组合层接线没有测试覆盖。** JVM 测不到 `remember` key、第四档参数 `smallTxtChapters = txtChapters` 是否真被传进去。位置错序是**编译错误**（`txtChapters` 使用早于声明），但「漏传第四档参数」只会静默退回旧行为 —— 靠第 5 节的用例覆盖不到，需真机验。
2. `selectChapterMetadata` / `ChapterMetadata` / `chapterMetadataOf` 是新模块内顶层声明。已 `grep` 确认全仓无同名（`ChapterMetadata(` 只命中我这两个文件），但重名与 KDoc `[...]` 引用可达性由编译器裁决。
3. 测试跨包访问 `internal fun com.creationreadingassistant.ui.screen.shelf.extractChapterLabel`。先例充分（`ShelfStatusFilterTest` 从同一文件 import `bookStatus` 且现役绿），但这是本测试里唯一没有直接先例的一点：若 `ShelfModelsKt`（含 `@Composable toneColor`）在 JVM 上类加载出问题，会红在这一条。
4. 测试构造 `internal data class BookIndex(...)`。先例：`ReaderProgressNavigationTest.kt:53`。低风险。
5. `DocChapter` / `ReadingProgressEntity` 具名参数按当前声明写（`DocChapter` 的 `charCountIsEstimated` 走默认值；`ReadingProgressEntity` 只传 `book_id` / `updated_at` / `current_location_json`）。
6. 仓库无 detekt / ktlint 配置（`find .editorconfig` / `detekt*.yml` 均空），故未做行宽对齐；最长新增行为测试第 76 行 100 列，主文件最长行 123 字节是**未改动**的既有 `AppLog.debug`。
7. **未跑过的门禁（全部留给统一构建）**：`:app:compileDebugKotlin`、`:app:testDebugUnitTest --tests "...ReaderDerivedChapterMetadataTest"`、全量 JVM、`:app:lintDebug`、`:app:assembleDebug`、真机。

## 7. 边界与 git 状态

- 只改了第 2 节列出的两个文件（1 改 + 1 新建），加本报告共 3 个写入动作。**未越界修改** B / C / D 三窗口的任何文件，未改测试夹具、Gradle、脚本、其他报告。
- `git status --short` 从 384 项增至 386 项，差值正是这两个代码文件；`ReaderScreenDerivedState.kt` 改动前与 HEAD 一致（diff blob `314c398` 为 HEAD 版本），本轮没有覆盖他人的未提交改动。注意 `docs/coordination/` **整目录未被跟踪**，git 把它折叠成单条 `?? docs/coordination/`，因此本报告（以及窗口 D 的对账报告）不进这个计数。
- staged 0 项；HEAD 仍为 `5aed5939`。未 stage / commit / push / checkout / reset / clean。
- 静态检查：`git diff --check`（跟踪文件）rc=0；新文件用 `git diff --no-index --check NUL <file>` rc=1 且输出零空白告警（rc=1 表示"两文件有差异"，非空白错误）；两文件均无 Tab / CR / 尾随空白；括号与花括号配平。

## 8. 交接给统一构建后的验收点

1. `:app:compileDebugKotlin` → `:app:testDebugUnitTest --tests "*ReaderDerivedChapterMetadataTest*"` 先跑定向，再进全量 JVM（关注是否牵动 `ReaderProgressNavigationTest` 等既有进度用例）。
2. 真机（竖屏，与窗口 D 约定一致）：小 TXT 读到第 2 章之后 → 退出 → 书架进度卡应显示「第2章」而非「第1章」；滚动与分页两种模式各验一次。
3. 回归面：EPUB 与 Markdown 各一本，书架「第 N 章」与目录章号一致；EPUB 续读定位不漂（第 1 档优先级未动，但这是唯一能证伪"掺进 TXT 量纲"的验法）。
4. 明确**不能**由本轮结论代替的：防抖落库时序、Compose 重数与 `remember` 身份稳定性（第 6.1 条）。
