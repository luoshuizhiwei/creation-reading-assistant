# 原生端阅读器内核优化方案

> 日期：2026-07-26
> 范围：`android/`（Kotlin + Compose + Hilt + Room）
> 依据：6 个智能体并行调研 + 1 轮对抗性复核（复核推翻了其中若干条初判，本文只保留经核实的结论）

---

## 一、结论先行

**建议：不要把 `mobile/android/legado-reader-core` 接进原生端，自己写分页内核，把 legado 当行为规格读而不是当依赖用。**

两条决定性理由：

1. **接进来会让整个原生 APK 变成 GPL-3.0 衍生作品。** 该模块 16 个 `.kt` 全部带 `SPDX-License-Identifier: GPL-3.0-only`。模块隔离带来的是「可审计」，不是「许可证隔离」——`docs/architecture/legado-reader-adoption.md` 第 2 节已经承认早期的"纯净室"方案不复存在。而 `android/` 与仓库根目录**都没有 LICENSE 文件**，`ProfileScreen.kt:1747` 却已经对外宣称「Android 端按 GPL-3.0 发布」。这个不一致本身就该先解决。

2. **接进来也拿不到你以为会拿到的东西。** 「复用 legado 内核 = 得到 legado 的中文排版」不成立：`ZhLayout.kt`（233 行，避头尾算法）在整个仓库只有它自己的类声明一处命中，**是死代码**；模块实际断行用的是 `TextPaginator.kt:143-183` 自己写的简化规则。付出 GPL 代价却没换来核心资产。

顺带说明它真正有价值的部分：`EpubReaderDocument`（懒加载 + 4 章 LRU + 预取，最成熟）、`TextMeasure`（三级宽度缓存）、`TextChapterDetector`（TXT 章节识别）。这些**思路**值得照着写一遍，代码不要复制。

---

## 二、现状诊断（均经复核）

### 2.1 最根本的一条：没有分页

`ReaderScreen.kt:735-739` 的注释自己写明了：「未做章内逐页分页，故『页』= 一章」。`readerMode == "paged"` 分支内部仍是 `LazyColumn`（`PagedChapterContent:858`），左右点击调的是 `goToChapter(±1)` —— **翻的是章，不是页**。而 `readerMode` 的默认值就是 `"paged"`（`SettingsStore.kt:101`），也就是说默认用户走的就是这条假分页路径。

连锁后果：
- EPUB 进度是章节整数比（`ReaderScreen.kt:1109-1112`），一章内读到哪里完全不反映在进度上
- 翻页动效因内存问题被禁用（`ReaderScreen.kt:785-788` 两个 `@Suppress("UNUSED_VARIABLE")` 死变量），设置项形同虚设
- 全书字符偏移用 **ZIP 解压字节数**估算（`Epub.kt:38-42`），中文书失真 5–10 倍，高亮/笔记的 locator 是近似量

### 2.2 结构：3465 行单文件，MVVM 被完全绕过

`ReaderScreen.kt` 3465 行 / 167KB，同时是阅读引擎 + UI + TTS 播放器 + 媒体通知 + 笔记 CRUD + 搜索 + AI 客户端 + 设置面板。`TtsController`（206 行）和 `TtsMediaSession`（142 行）两个服务级类直接写在 Screen 文件里。

而 `ReaderViewModel`（177 行）是**完整的死代码**：`ReaderScreen.kt:995` 用 `@Suppress("unused")` 注入后，全文件 `viewModel.` 零命中。它已经实现了 7 个 StateFlow 和 `openFile/goToChapter/loadText/saveNote`，与 Screen 里那批 `remember { mutableStateOf }` 一一对应，纯属重复实现。

可复核的散落度指标：`remember` 89 处、`mutableStateOf` 家族 75 处、`LaunchedEffect` 19 个、**`derivedStateOf` 0 个**。

### 2.3 七个设置项是纯装饰

逐项核实通过：`paragraphSpacing`、`chineseTypography`、`immersiveMode`、`showReaderInfo`、`showProgressBar`、`autoHideSeconds` 只出现在设置面板的形参与开关上；`pageTurnEffect` 在 `ReaderScreen.kt:785-788` 显式丢弃；`keepAwake` 对应的 `FLAG_KEEP_SCREEN_ON` 全域 0 命中。

段间距三处硬编码 `Arrangement.spacedBy(8.dp)`；`pageMargin` 只在 paged 分支生效，滚动与 TXT 分支硬编码 16.dp。

### 2.4 默认模式下定位功能是死的

`PagedChapterContent` 声明了 `focusBlockIndex` 与 `bringRequester` 两个参数（`:854`、`:856`），**函数体从未引用**。唯一挂载点在滚动分支 `:1699`。后果：默认 paged 模式下，TTS 不跟随滚动，从搜索/笔记「跳转」到高亮只能到章、到不了段。

### 2.5 每帧重组的真正元凶

复核订正了初判：`contentText` 的 `filterIsInstance + joinToString` 实际是 1 Hz 触发（计时器），不是每帧。**真正的每帧开销**是 `ReaderScreen.kt:1086/1088` 对 `observeAllActive()` 拿到的**全库**笔记/灵感做未 remember 的线性过滤，配合 `:1115-1116` 裸读 `layoutInfo`（无 `derivedStateOf`）造成的重组，等于滚动时每帧扫全表。

---

## 三、本轮已修复（与内核选型无关，无论走哪条路都必须修）

这几条是用真实复现程序确认的正确性缺陷，已改完并有测试锁定。

| 缺陷 | 后果 | 修法 |
|---|---|---|
| `loadChapterText` 标签扫描器把 `/` 当非字母丢弃，`</head>` 退出条件**永不成立** | **EPUB 全书搜索对任何书任何关键词恒返回 0 条**；>2MB 单章打开是空白页 | 引入 `tagNameEnded` 显式封口，`/` 仅在首位有效 |
| `<head profile="x">` 时标签名拼成 `headprofilex`，随后 `</head>` 反而命中开标签分支 | 正文同样丢失，并额外泄漏 `<title>` 为正文 | 同上（空白即封口） |
| 渲染路径 `decodeEntities` 只认 8 个命名实体 + 十进制 | `&ldquo;` `&rdquo;` `&mdash;` `&hellip;` `&#x201C;` **在阅读界面原样显示**（十进制反而正常） | 与流式路径的 `decodeEntity` 合并为同一张表 |
| 数值实体用 `Int.toChar()` 截断到 16 位 | `&#128512;`(😀) 变 U+F600 私用区方块 | 改用 `Character.toChars`，排除代理项区间 |
| `<br />`（斜杠前有空格）用等值比较匹配不上 | 诗歌、书信粘连成一整段 | 改为正则 `<br\b[^>]*/?\s*>` |
| `resolvePath` 不做 percent-decode | href 含空格或非 ASCII 时 `getEntry()` 返回 null，整章空白 | 加 `URLDecoder.decode`，先保护 `+` 字面量 |
| `loadChapterText` 无体积闸门 | 修完上面的 bug 后会**立刻解锁** OOM 路径（此前被空串掩盖） | 加 400 万字符上限，与扫描器修复同批落地 |
| 11 个正则在每轮标签重新编译 | 一章一万标签 ≈ 6 万次 `Pattern.compile` | 全部提为常量 |

验证：`EpubParserTextTest` 8 个用例 + 既有 10 个用例全绿（`OK (18 tests)`），`assembleDebug` BUILD SUCCESSFUL。

> **历史备注（已作废）**：写这段时单测不能用 Gradle 跑，项目路径含中文导致测试 worker 加载不了任何测试类，
> 上述结果是把编译产物复制到 ASCII 路径后用 `java -cp … JUnitCore` 得到的。
> 2026-07-27 项目改名为 `creation-reading-assistant` 后该故障已根治，现在直接
> `./gradlew :app:testDebugUnitTest` 即可，不要再走绕行方案。

---

## 四、三条路线对比

### 方案 A：接入 `legado-reader-core`

| | |
|---|---|
| 工作量 | 中。技术上无硬阻塞：模块不声明 repositories、空 manifest、无 res/、无 `R.`/`BuildConfig` 引用；只需改写 5 处 `rootProject.ext.*` 依赖 |
| 收益 | 拿到成熟的 `EpubReaderDocument` 懒加载与 `TextMeasure` |
| **代价** | **整个 APK 变 GPL-3.0**；拿不到 ZhLayout（死代码）；两个 View 是 Canvas 分页，与现有 LazyColumn 滚动范式冲突；模块外还有 1443 行 `NativeReaderActivity.java` 的装配逻辑要全部 Compose 重写；模块当前有 11 个文件 +534/-121 的未提交改动，不是冻结版本 |
| 判定 | **不推荐** |

### 方案 B：自研 Compose 分页内核

| | |
|---|---|
| 工作量 | 核心算法约 800–1200 行 Kotlin + 单测 |
| 收益 | 真分页、真进度、翻页动效、完全自主、无 GPL 负担 |
| 代价 | 需要自己实现中文禁则；历史 locator 需迁移 |
| 判定 | **推荐**（前提是分页确实是产品需求） |

### 方案 A2：换成 Readium Kotlin Toolkit 的解析层

`Epub.kt:8-11` 当初记的「后续可把 EpubParser 换成 Readium」这条路，已实地查证：

| 事项 | 查证结果 |
|---|---|
| 许可证 | **BSD 3-Clause**（`LICENSE` 原文确认）。与 GPL 完全不同，无传染性问题 |
| 模块 | `readium-shared` / `readium-streamer` / `readium-navigator` / `readium-opds` / `readium-lcp`，Maven Central 独立坐标 |
| 解析层能否独立用 | **能**。`readium/streamer/build.gradle.kts` 只 `api(project(":readium:readium-shared"))` + timber/koi/coroutines，不依赖 navigator |
| **分页能力在哪** | **在 WebView 里**。`EpubNavigatorFragment.kt` 有 38 处 WebView 引用，`EpubNavigatorViewModel.kt` 6 处。README 特性表里 EPUB reflow 的「Pagination ✅」指的就是它 |
| 文本抽取 API | `ContentService` / `publication.content()`，但官方文档首行即标注 **「⚠️ 仍是实验性，实现不完整」** |
| 版本兼容 | 3.0.0 需 Kotlin 1.9.24 / compileSdk 34 / Gradle 8.6 —— 与本项目（Kotlin 1.9.25 / compileSdk 34）几乎正好对上；但 3.1.2+ 需 compileSdk 36 + Kotlin 2.1.21+，3.2.0 需 Kotlin 2.3.20，等于全项目工具链升级 |
| 其他 | 需要 core library desugaring（项目已开启 ✓）；minSdk 23，本项目 24 ✓；LCP DRM 需向 EDRLab 商务申请私有 `liblcp` 二进制，不是开箱可用 |

**结论：不换。** 关键在于——它**能**解决的（DRM 检测、fixed-layout、media overlays、OPDS、PDF）都是当前用户感觉不到的；它**不能**解决的恰恰是用户唯一真正感觉到的分页问题（锁在 WebView 里，与「纯原生」约束正面冲突）；而它用来替代 `EpubParser` 的那个文本抽取 API，官方自己标着实验性且不完整。

同时迁移不便宜：`EpubBook` / `EpubChapter` / `EpubBlock` 要换成 `Publication` / `Link` / `Locator`，`EpubRepository`、`ReaderScreen`、以及已落库的 locator 全要跟着动。

### 方案 C：不做分页，把滚动流做好

| | |
|---|---|
| 工作量 | 小。修完第三节 + 让 7 个设置项真正生效 + 拆文件 |
| 收益 | 立刻可见的体验修复，风险最低 |
| 代价 | 永远没有真正的翻页、页码、翻页动效；进度精度受限 |
| 判定 | 如果产品上认定「滚动阅读就够了」，这是**理性选择** —— 那就把 `readerMode` 的「左右翻页」选项去掉，别留一个名不副实的开关 |

---

## 五、自研分页内核的技术要点（方案 B 的坑）

调研阶段踩过的坑，动手前必须知道：

**测宽只能走 `TextPaint.getTextWidths()`。** 调研初稿推荐「用 `TextMeasurer` 只取字符 advance 不用它断行」是自相矛盾的：反编译确认 `TextLayout.getBoundingBox(int)` 首条指令就是 `getLineForOffset`，必须先有 `StaticLayout`。Compose 不存在「不做布局就拿到字符宽度」的 API。平台的 `TextPaint.getTextWidths()` 才是唯一主路 —— 这也正是 legado `TextMeasure` 的做法，且是平台 API，无 GPL 问题。

**中文禁则必须自己实现。** `LineBreak.Strictness` 只在 API 33+ 生效（字节码确认只有 `StaticLayoutFactory33` 调 `setLineBreakConfig`），项目 minSdk 24，Android 12 及以下完全空转。`TextAlign.Justify` 是 inter-word 实现，靠拉伸空格，对无空格的中文正文基本无效（inter-character 需 API 34 且 Compose 1.7 未暴露）。

**不要共享 `TextMeasurer`。** `TextLayoutCache` 唯一字段是个非 synchronized 的 `LruCache`，在后台分页协程与 UI 线程 `drawText` 之间共享是数据竞争。且它内置 LRU 只有 8 条、key 是整段 `AnnotatedString`，承担不了章级缓存。

**只缓存 `pageStarts: IntArray`，绝不缓存 `TextLayoutResult`。** 一章 60 页仅 240 字节。章级 LRU 可沿用既有结论：4 章 / 200 万字符 / 当前章优先保留。

**行高必须钉死。** `lineHeight` + `LineHeightStyle(Trim.None)` + `includeFontPadding=false`，否则每页首末行的 half-leading 被裁，翻页出现像素抖动。当前 `ReaderScreen.kt:877-881` 只设了 `lineHeight`。

**分页结果不可跨设备复现。** 项目无自带字体（`assets/` 与 `res/font` 都不存在），全走系统 CJK 回退；Android 12+ 用户换系统字体会在运行中改变排版。缓存 key 必须含 typeface 标识，位置恢复必须用 **字符偏移 anchor** 而不是页号。

**「最小切口只换两个 Composable」是低估。** 图片在 Canvas 方案下没有槽位（现在是 `LazyColumn` 的 `AsyncImage` item）；Canvas 没有文本选择（现 TXT 用 `BasicTextField`）；scroll 分支会分叉；而且偏移基准从 ZIP 字节数迁到真实字符偏移，会让**全部历史 locator 失准**，需要迁移策略。

**`TextIndent` 是 ParagraphStyle 级的**，跨页续段会被错误缩进两格，需要以 indent=0 重排剩余部分。

---

## 六、建议的推进顺序

**第 0 阶段（已完成）**：修掉第三节那批正确性缺陷。与选型无关，先落袋。

**第 1 阶段（建议紧接着做，不依赖任何选型决策）**
1. 把 `TtsController` / `TtsMediaSession` 从 `ReaderScreen.kt` 迁出到 `feature/reader/tts/`
2. 让 `ReaderViewModel` 真正接管状态，删掉 Screen 里重复的那批 `mutableStateOf`；顺手统一进度量纲（现在 VM 存 0..1、Screen 存 0..100，同一个 `saveProgress` 消费）
3. 修每帧重组：`:1086/1088` 的全库过滤加 `remember`，`:1115-1116` 的 `layoutInfo` 裸读改 `derivedStateOf`
4. 给 `PagedChapterContent` 补上 `focusBlockIndex` / `bringRequester` 的挂载，让默认模式下的 TTS 跟随和高亮跳转活过来
5. 7 个装饰性设置项：**要么实现，要么从 UI 删掉**。留着比没有更糟
6. TXT 章节识别（照 `TextChapterDetector` 的思路自己写，4 组正则），让 TXT 有目录、能跳章

第 1 阶段做完，阅读体验的提升已经相当明显，而且完全没有 GPL 与架构风险。

**第 2 阶段（需要你先做产品决策）**：分页内核。

决策点只有一个 —— **「左右翻页」是不是必须的产品能力？**

- 是 → 走方案 B，按第五节的要点实现。建议顺序：先做纯文本分页器 + 单测（可在 JVM 跑，用 `TextPaint` 的话需要 Robolectric 或插桩测试），再接 Compose Canvas 渲染，最后做翻页动效
- 否 → 走方案 C，把「左右翻页」选项从设置里去掉，专心打磨滚动阅读

我的倾向是**走方案 B**：`readerMode` 默认值本来就是 `"paged"`，说明产品意图一直是翻页；而且进度精度、locator 精度、翻页动效三件事都卡在同一个缺口上，一次做掉比逐个绕开划算。

---

## 六之二、「要不要换内核」的正式结论

**不需要换。当前不存在一个值得换的更好内核。**

理由收敛成三条：

1. **你感觉到的问题全在渲染层。** 没有真分页、进度是章节整数比、翻页动效被禁、翻页翻的是章 —— 这四条都出自 `ReaderScreen.kt` 的 `LazyColumn` 滚动流。换任何解析库，这四条一条都不会好转，因为没有哪个解析库负责把字排到屏幕上。
2. **两个候选各自出局**：`legado-reader-core` 是 GPL-3.0 会传染整个 APK，且它的 `ZhLayout` 在仓库里是死代码，付出许可证代价换不到中文排版；Readium 许可证干净、解析层也确实能独立用，但它的分页锁在 WebView navigator 里，与「纯原生」约束正面冲突，而它的文本抽取 API 官方标注实验性且不完整。
3. **自研解析层在本轮修复后已经够用**。第三节那 8 个缺陷修掉之后，对「中文 TXT / EPUB 小说」这个具体场景，剩下的缺口（CSS 样式、脚注链接、SVG 封面、fixed-layout）对纯文字小说影响很小。

**要做的不是「换内核」，是「补一个你现在根本没有的分页层」**（第五、六节）。

### 这个结论在什么条件下失效

出现下列任何一条，就应重新评估 Readium：

- 要支持 **PDF**：自研 PDF 排版不现实，这是 Readium 的强项
- 要支持 **有声书 / media overlays / 漫画 CBZ**
- 要支持 **fixed-layout EPUB**（绘本、杂志、技术书）：固定布局自研成本极高
- 要支持 **LCP DRM 的商店内容**（需另向 EDRLab 申请 `liblcp`）
- **放弃「不要 WebView」的约束**：一旦放弃，Readium 整体接入立刻变成最优解，自研分页也就不必做了
- 用户开始抱怨 EPUB 的 **CSS 样式丢失**（粗体/斜体/表格/ruby）：这说明用户在读技术书而非小说，产品定位变了

反过来说：只要产品还是「读中文小说 + 创作辅助」且坚持纯原生渲染，自研就是这个约束组合下的正解。

---

## 七、必须顺带处理的合规问题

与内核选型无关，但已经是既成事实的不一致：

- `android/` 与仓库根目录都没有 LICENSE 文件
- `ProfileScreen.kt:1747` 已经对外声明「Android 端按 GPL-3.0 发布」并提供源码下载入口
- 该文案写的是「移动端包含基于 Legado / 阅读 Sigma 固定版本适配的本地阅读内核」，与 `UPSTREAM.md` 记录的上游 `Luoyacheng/legado-E` 命名不一致，**且原生端目前根本没有包含该内核**

也就是说：现在的声明既不准确、也无 LICENSE 文件支撑。无论最终选哪条路，这段文案都要按实际情况改写。若走方案 B（不引入 GPL 代码），原生端反而可以不受 GPL 约束 —— 那这段声明就该整体撤掉或改写。

另外 `legado-reader-core` 里 vendored 的 67 个 `me.ag2s.epublib` Java 文件**全部没有 license header**，只有模块级 LICENSE 兜底。这一条影响的是现有的 Capacitor 端分发，建议一并补齐。
