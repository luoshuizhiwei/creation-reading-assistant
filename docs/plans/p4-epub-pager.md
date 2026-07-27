# P4 实施方案：EPUB 接入自研翻页引擎 + 翻页动画

> 日期：2026-07-27
> 上位文档：[android/READER_KERNEL_SIDECAR_ZH.md](../../android/READER_KERNEL_SIDECAR_ZH.md) §6「P4 · EPUB + 翻页动效」。
> 本文的 P4 指 SIDECAR-ZH 的阶段编号。**不要**与 `android/P4_DESIGN.md`（旧一代编号，内容是统计/TTS/批注/WebDAV）混淆。
> 产出方式：4 路并行只读调研（TTS/locator 语义、排版内核扩展缝、设置与 UI 接线面、工作区动态）→ 起草 → 1 轮对抗性复核修订。
> 所有 file:line 以 2026-07-27 提交 `ddc1fb0` 后的工作区为准；android/ 侧有并行会话活跃，动手前先 `git pull --rebase` 并复核关键行号。

> 2026-07-27 接手检查点：已完成一个可回退的 **P4-c0 文字版接线**——
> `PagedChapterSource` 统一 TXT/EPUB 章文本来源，现有试验开关可让 EPUB 进入章内分页，
> 章内进度以 `{"chapter":N,"offset":M}` 超集格式恢复，目录/高亮/TTS 跳转已接通；
> 同时保留 legacy EPUB 两条渲染路径。此检查点**不等于完整 P4-c**：
> 图片块分页、独立 EPUB 开关、相邻章预排版以及 P4-d 四档动画仍按本文后续刀次待做。

---

## 0. 结论速览

P4 分四刀，每刀独立可提交、可回退：

| 刀 | 内容 | 规模 | 风险 |
|---|---|---|---|
| **P4-a** | `EpubDocument`/`DocumentCache` 接入现有渲染路径，根治同章反复解压 | ~60 行改动 | 低（无 UI 变化） |
| **P4-b** | 排版内核加「图片块」：`LayoutBlock` + `paginateBlocks` + 纯 JVM 单测 | ~350 行 + 250 行测试 | 低（不接 UI，TXT 输出锁定不变） |
| **P4-c** | `EpubPagedController` + `PagedEpubReaderHost` + ReaderScreen 第四渲染分支，独立开关默认关 | ~700 行 | 中（新分支旁挂，legacy 保留） |
| **P4-d** | `PageTurner` 翻页动画四档（none/fade/slide/cover），TXT 与 EPUB 共用 | ~300 行 | 中（真机 60fps 验收） |

三条最重要的设计决策（详见正文）：

1. **单一文本空间，不引入图片占位符。** EPUB 章文本 = 各 Text 块按 `"\n"` 拼接，与 TTS 的 `contentText`、搜索抽取、locator 章内偏移**逐字符相同**。图片不占字符，以 `anchorOffset` 挂在字符流上、以 `Page.images` 并行于 `Page.lines` 存在。绝不搞「排版用一份带占位符的文本、TTS 用另一份」——P0 刚消灭掉的双文本空间病灶不能再造一个。
2. **locator 写入必须落在旧版混合量纲上**：全局偏移 = `LegacyOffsetCodec.chapterStartOffsets(estimatedLengths)[ci]`（ZIP 字节估算的章基址）+ 章内真实字符偏移。写错量纲，历史高亮跳转与新写高亮会互相错位。
3. **动画零位图**：`PageFrame` 快照只是对已驻留 `Page` 对象的引用，双 `graphicsLayer` 只改 transform，两段式提交防一帧旧页闪回。不用截图缓存（7.25 MB/页），不复制整章组件树（EPUB 旧动效被拆除的原因，`ReaderScreen.kt:781-788`）。

---

## 1. 现状（已逐行核实）

### 1.1 EPUB 渲染路径

- `readerMode == "paged"` 走 `PagedEpubView`（`ReaderScreen.kt:741`）：**「页」= 一整章**，章内是 LazyColumn 滚动，左右点按翻的是「章」。`pageTurnEffect` 参数被刻意弃用（`ReaderScreen.kt:781-788` 两个 `@Suppress("UNUSED_VARIABLE")`），注释言明原因：旧 AnimatedContent/Crossfade 让两章组件树同驻内存。
- `readerMode == "scroll"` 走 LazyColumn 逐块渲染（`ReaderScreen.kt:1836-1878`）。
- 章内容加载：`EpubChapter.blocks` 是 property getter，**每次访问重开 ZipFile 全量重解析**（`domain/model/Epub.kt:44-47`、`EpubParser.kt:128-144`）。`goToChapter` 每次翻章解压一遍（`ReaderScreen.kt:1297-1328`，有 Job 取消 + `chapterIndex` 校验的竞态防护）；高亮/笔记跳转在同一章上**再解压一遍**算块索引（`ReaderScreen.kt:1548`）。无任何跨章缓存、无相邻章预取。
- `EpubDocument` + `DocumentCache`（LRU 4 章 + 2M 字符上限 + 每章单飞锁 + 图片 intrinsic 尺寸预读）已在 P0 写好，**至今零生产调用方**（`feature/reader/doc/EpubDocument.kt`、`DocumentCache.kt`）。

### 1.2 偏移与 locator（P4 最容易写错的地方）

- **章内偏移的真源**：`contentText` = 当前章 `EpubBlock.Text` 按 `"\n"` 拼接（`ReaderScreen.kt:1150-1154`）。TTS 喂它，`tts.currentSentenceRange` 因此是**章内真实字符偏移**（`ReaderScreen.kt:305-320, 1583`）；搜索抽取 `loadChapterText` 与渲染 `loadChapterBlocks` 已共用同一抽取器（`EpubParser.kt:146-167`），产出同一文本。
- **全局偏移是混合量纲**：章基址 = `estimatedTextLength`（ZIP 解压后**字节**数，中文偏大约 3 倍）经 `LegacyOffsetCodec.chapterStartOffsets` 累加（每章 min 1、章间 +1）；章内部分 = 真实字符。块全局偏移 = 字节章基址 + 真实字符章内偏移（`ReaderScreen.kt:707-711, 975-985`；`LegacyOffsetCodec.kt:27-68`）。该公式被 `LegacyOffsetCodecTest` 逐值锁死。
- **locator** 就是 `{"offset":N}`（`LegacyOffsetCodec.kt:71`），读端正则 `"offset"\s*:\s*(\d+)` 容忍同一 JSON 追加字段（超集双写留好了口子）。
- **反解自洽的原因**：章内真实字符偏移 ≤ 真实字符数 ≤ 估算字节数/3 < 估算章长，所以 `indexOfLast { 基址 ≤ G }` 恒定位到正确的章（SIDECAR §3.3-A 已证）。**推论：P4 写 locator 只要沿用混合量纲，与全部历史数据兼容，零迁移。**
- 高亮/笔记跳转现状：定章 → `goToChapter` → 章内偏移 → 块索引 → `BringIntoViewRequester` 滚到块（`ReaderScreen.kt:1526-1568`），粒度是块级。搜索命中只跳章级，且其章内偏移在「空白折叠后」的文本空间里（`ReaderScreen.kt:2757`），**不能**直接当 chapterText 偏移用。

### 1.3 TXT 翻页引擎（P2/P3 已交付，P4 的复用基座）

- `TxtPageSource`（段落切片、偏移可逆）→ `TxtPagedController`（整章排版状态机、跨章翻页、`PageIndexStore` 写通缓存、锚点纪律）→ `PagedTxtReaderHost`（LayoutConfig 组装、手势、`PageCanvas` 逐簇绘制、选区/TTS 高亮 underlay、页脚）。
- 动画现状：`pageTurnEffect == "fade"` 已实现——`Crossfade(tween(180))` 交叉淡化**单页画布**，`PageFrame(page, chapterText)` 快照保证跨章旧页按旧章文本绘制（`PagedTxtReaderHost.kt:245-263, 308`）。设置 UI 只暴露 none（无动画）/ fade（柔和淡入）两档（`ProfileScreen.kt:1039`、`ReaderScreen.kt:3452`）。
- `ChapterPaginator.paginate` 只吃 `List<LayoutParagraph>`，内部先逐段排行、在 `:68-74` 展平为 Item 序列、再按高度贪心填页。**图片块的最小侵入缝就在展平点。**
- 排版指纹 15 因子无图片项，`ENGINE_VERSION = 2`（`LayoutConfig.kt:58-88`）。
- Room：`AppDatabase version = 5`，`MIGRATION_4_5` 已建 `reader_page_index`（主键 content_key + chapter_index + fingerprint），**P4 零 schema 改动、零新迁移**——`fallbackToDestructiveMigration()` 还挂着（`DatabaseModule.kt:47`），任何迁移失误都是静默清库，能不动就不动。
- 线程模型：`IcuBreakOracle` 是 ThreadLocal 的，可并发；**`PaintTextRuler` 单实例不可并发**——共享 `scratch: FloatArray` 与非线程安全的 `SparseArray` 缓存（`PaintTextRuler.kt:38-39, 76`）。P4 要做相邻章预排版，**每个排版任务各建一个 ruler 实例**（构造成本仅测 129 个字符，可忽略）。

### 1.4 进度持久化缺口

EPUB 只存 `{"chapter":N}`（`EpubRepository.kt:53-69`），章内位置一关应用就丢。P4 分页后必须补章内偏移，且要用超集 JSON（旧版 `loadProgress` 的正则只读 `chapter` 字段，追加字段零风险）。

---

## 2. P4-a · EpubDocument/DocumentCache 接入渲染路径

**目标：翻章、高亮跳转、TTS 不再重复解压同一章；图片块从此自带 intrinsic 尺寸。无任何 UI 变化。**

### 2.1 改法

1. `ReaderScreen` 打开 EPUB 后构造文档对象并管好生命周期：

   ```kotlin
   val epubDoc = remember(epubBook) { epubBook?.let { EpubDocument(it) } }
   DisposableEffect(epubDoc) { onDispose { epubDoc?.close() } }
   ```

2. 三处章内容访问改走 `epubDoc.blocks(ci)`（内存命中即返回，未命中单飞解析）：
   - 首开预加载（`ReaderScreen.kt:1408`）；
   - `goToChapter` 的 IO 加载（`:1307-1308`），保留现有 Job 取消 + `chapterIndex` 校验；
   - 高亮/笔记跳转里那次独立加载（`:1548`）——接入后自然命中缓存，重复解压消失。
3. 类型适配：渲染层现在消费 `EpubBlock`，`EpubDocument.blocks` 返回 `DocBlock`。两个选项：
   - **推荐**：渲染分支改吃 `DocBlock`（`Text(text, isHeading)` / `Image(path, width, height)` 字段一一对应，改动就是把 `EpubBlock.` 前缀换掉 + `AsyncImage` 拿到宽高后可顺手按比例占位防跳动）；
   - 备选：`EpubDocument` 旁加一个 `toEpubBlock` 适配器维持现状类型。选项一多改十几行但消灭双类型，值得。
4. `contentText` / `computeBlockGlobalOffsets` 的输入随 `chapterBlocks` 类型同步微调，**拼接与偏移公式一字不动**（它们是 locator 兼容的根）。
5. **搜索这一刀不动。** `computeEpubSearch` 继续走 `loadChapterText`：它逐章扫一遍、每章只解压一次、传 `imgCacheDir = null` 不抽图。若改走 `doc.blocks()` 会带两个副作用——全书扫描把 LRU(4) 缓存冲刷一遍，且**逐章触发图片抽取落盘**（`loadChapterBlocks` 遇 `<img>` 就解出文件，`EpubParser.kt:235-245`）。搜索的章内精确跳转属 P5，届时再统一。

### 2.2 通过标准

- 同一章往返翻章（N→N+1→N），第二次进 N 章不发生 ZIP 解压（日志或断点证实 `DocumentCache` 命中）。
- 高亮跳转全程只解压目标章一次。
- TTS、选区、高亮定位、搜索行为与改前逐一相同（真机走一遍五件事）。
- `chapterBlocks` 为空 → 「本章暂无可读内容」的兜底分支仍工作。
- 换书 / 退出阅读器后 `DocumentCache.clear()` 被调用（无泄漏：缓存上限 4 章 / 2M 字符）。

---

## 3. P4-b · 排版内核加图片块

**目标：`layout/` 包学会「整块不跨页、按需缩放」的图片；TXT 路径输出逐字节不变；全部纯 JVM 单测。**

### 3.1 输入模型（`layout/` 包，禁 `import android.*` 纪律不变）

```kotlin
/** 分页输入块。图片不占字符流，anchorOffset 是它在章文本里的挂靠点。 */
sealed interface LayoutBlock {
    data class Text(val paragraph: LayoutParagraph) : LayoutBlock
    data class Image(
        /** 渲染层解码用的资源键（EPUB 里就是缓存文件路径）。layout 包只当不透明字符串。 */
        val sourceKey: String,
        /** intrinsic 尺寸（px）。≤0 表示未知，按 4:3 全宽占位盒处理。 */
        val widthPx: Float,
        val heightPx: Float,
        /** 挂靠的章内字符偏移 = 下一个文本块的起始偏移（章末图片 = 章文本长度）。 */
        val anchorOffset: Int,
    ) : LayoutBlock
}
```

### 3.2 `ChapterPaginator` 扩展

新入口 `paginateBlocks(blocks: List<LayoutBlock>, cfg, ruler, oracle): ChapterLayout`；
现有 `paginate(paragraphs)` 改为 `paginateBlocks(paragraphs.map { LayoutBlock.Text(it) })` 的委托——**纯文字输入的算术路径一行不改**。

内部改动：

1. 展平序列 `Item` 从 `(line, para, isParaEnd)` 泛化为文本项/图片项二选一；
2. **统一项高函数**（这是记账正确性的关键，三处必须同源）：
   - 文本行 = 现 `lineHeight(line, cfg)`；
   - 图片项 = 缩放后高度 `imgH = heightPx * scale`，其中
     `scale = min(1, contentWidthPx / widthPx, contentHeightPx / heightPx)`
     ——宽先钳、高再钳，保证任何图都能独占一页放下；未知尺寸按 `widthPx = contentWidthPx, heightPx = contentWidthPx * 0.75f` 的占位盒；
   - 图片项之后追加 `paragraphSpacingPx`（视同段末）。
   同一个函数喂三处：填页判断 / `used` 累加（`:87-96`）、keep-with-next 回退扣减（`:105`）、`distributeBottomSlack` 内部的 y 累加（`:149-155`）。
3. 填页判断沿用 `pageLines.isNotEmpty() && used + h > contentHeightPx` ——图片放不下就整块推到次页（**整块不跨页**，SIDECAR §6 P4 定调）；一页装不下任何东西时的「首项无条件收入」逻辑照旧，保证不死循环。
4. **顺手修既有记账小账误并写进单测**：keep-with-next 把标题推到次页时 `used -= lineHeight(...)` 漏扣了当初随 `isParaEnd` 加进去的 `paragraphSpacingPx`，导致 `slack` 偏小。该修复只影响 `lineTops`（页已在回退前封口，`pageStarts` 不变），**不 bump `ENGINE_VERSION`** 的依据见 §3.4。
5. `distributeBottomSlack` 的 `slack < lineHeightPx` 才摊的阈值不动——「下一张图放不下被推走」造成的大空底天然不会被硬摊开。图片相邻的行间 gap 照常参与均摊（量级 < 1 行高，无观感问题）。

### 3.3 页模型扩展

```kotlin
class Page(
    …现有字段…,
    /** 本页的图片，与 lines 并行。纯文字页为空表。 */
    val images: List<PlacedImage> = emptyList(),
)
/** 页内定位好的一张图。矩形是页内坐标，水平居中。 */
class PlacedImage(val sourceKey: String, val left: Float, val top: Float,
                  val width: Float, val height: Float, val anchorOffset: Int)
```

- `ChapterLayout` 不加字段。`pageStarts` 语义不变：页首**字符**偏移。
- 纯图片页（整页插图）：`startCharOffset = endCharOffset =` 首图 `anchorOffset`。`pageStarts` 因此**非严格递增**（图片页与后继文字页同 start）。这是有意选择，语义如下：
  - `pageIndexFor` 的二分取「最后一个 ≤」，命中**靠后的文字页**——`open(offset)`、位置恢复、改字号锚定都落到文字页，纯图片页只能靠 `nextPage`/`prevPage`（页号 ±1，不走二分）翻到。可接受：重开书落在插图后第一段文字，不算丢位置。
  - 换来的是**单一文本空间**：`char_count` 校验、进度、选区、TTS、locator 全部零特判。对比方案（每图占 1 个 U+FFFC 占位符）会让排版文本 ≠ `contentText`/搜索文本/locator 空间，每个边界都要做偏移映射——那是 P0 刚根治的「双文本空间」病灶，明确不走。
- `PageSelection`/`PageHitTest` 零改动（只认 `lines`）。图片自身的点击命中（后续「看大图」）在宿主层按 `PlacedImage` 矩形判断，不进内核。

### 3.4 缓存与 `ENGINE_VERSION`

- `pageStarts` 缓存键是 `(contentKey, chapterIndex, fingerprint)`，命中即信任。**是否 bump 只取决于：新代码对同一输入产出的 `pageStarts` 是否逐值相同。**
  - TXT：输入不含图片，委托路径算术不变，§3.2-4 的修复只动 `lineTops`（不落库）→ 旧缓存仍有效，**不 bump**；
  - EPUB：此前无任何分页缓存，不存在旧数据错配。
- 用金样单测锁死：对固定文本输入，`paginateBlocks(map)` 与旧 `paginate` 的 `pageStarts` 逐值相等。**若实现中不得不动纯文字路径的任何浮点累加顺序，立即 bump 到 3**（`LayoutConfig.kt:80-87` 的纪律）。
- 图片布局若引入新可调参数（如占位盒比例做成设置），该参数**必须进 fingerprint**。当前方案里缩放规则全部由既有指纹因子（宽/高）决定，无新增因子。

### 3.5 通过标准（全部纯 JVM，`./gradlew :app:testDebugUnitTest`）

1. 金样：纯文本输入下 `paginateBlocks` 与 P3 版 `paginate` 的 `pageStarts`、每页 `lines` 逐字段相等。
2. 图片恰好放不下 → 整块进次页，前页 `used` 不含它；图片页 `startCharOffset == anchorOffset`。
3. 超大图（宽高均超页）→ 缩放后独占一页，`scale` 同时满足两钳。
4. 未知尺寸（0×0）→ 4:3 占位盒，不 NaN、不除零。
5. 连续多图 → 逐页排布，无死循环；章首图 / 章末图（`anchorOffset == chapterText.length`）边界正确。
6. `pageIndexFor(图片页 start)` 命中后继文字页；`nextPage/prevPage` 可达图片页（控制器层测试，P4-c 补）。
7. keep-with-next 修复：标题被推走后 `used` 恰好扣回行高 + 段距，`distributeBottomSlack` 的 slack 非负。
8. 「标题 + 紧跟图片」：标题不孤留页底（keep-with-next 对图片项同样生效——标题的 next 是图片时把标题一起推走）。

---

## 4. P4-c · EPUB 分页控制器与宿主

**目标：EPUB 真正的章内左右翻页。旁挂第四渲染分支，独立开关默认关，legacy 两条分支永久保留。**

### 4.1 `EpubPageSource`（pager 包，纯 JVM）

```kotlin
object EpubPageSource {
    /** 章文本。必须与 ReaderDocument.text() / contentText 逐字符相同 —— 单测锁死。 */
    fun chapterTextOf(blocks: List<DocBlock>): String

    /** DocBlock 流 → LayoutBlock 流。文本块偏移按「长度 + 1」累加（与
     *  LegacyOffsetCodec.blockOffsets 同公式）；块内含 '\n' 时按行拆成多个段落，
     *  行内偏移经切片保持可逆（对齐 TxtPageSource 的偏移纪律）；
     *  isHeading → BlockRole.HEADING；图片挂 anchorOffset = 下一文本块起始偏移。 */
    fun layoutBlocksOf(blocks: List<DocBlock>): List<LayoutBlock>
}
```

与 `TxtPageSource.paragraphsOf` 的差异只有两点：标题来自 `isHeading` 标记（不做标题文本匹配）；输入是块流不是整章字符串。首尾空白的「切片收缩」纪律照搬（`TxtPageSource.kt:49-58`）——EPUB 段落偶带 `　` 缩进，同样不能 trim 后拼接。

### 4.2 `EpubPagedController`

与 `TxtPagedController` 平行的独立类（**不做强行泛化**——TXT 持全文于内存、EPUB 章级懒加载，硬揉进一个类会让两边都难读；共性已经沉在 `ChapterPaginator`/`PageSelection`/`PageIndexStore` 里）。结构对照：

| 维度 | TxtPagedController | EpubPagedController |
|---|---|---|
| 数据源 | `fullText: String` 常驻 | `doc: ReaderDocument`，章按需 `blocks(ci)`（DocumentCache 单飞） |
| 位置真源 | 全书真实字符偏移 | **(chapterIndex, 章内真实字符偏移)** 二元组 |
| 排版输入 | `TxtPageSource.paragraphsOf` | `EpubPageSource.layoutBlocksOf` → `paginateBlocks` |
| 加载线程 | 排版 Default | **blocks 在 IO、排版在 Default**，两段式 |
| ruler | 宿主单实例 | **每个排版任务新建 `PaintTextRuler`**（§1.3 线程模型） |
| 页缓存 | `PageIndexStore`，contentKey = bid | 同表同门面，contentKey = bid，`char_count` = chapterText.length 校验 |

要点：

1. `open(ci, co)` / `nextPage()` / `prevPage()` 跨章语义照搬 TXT（首页再往前 → 上一章末页）。锚点纪律照搬：重排后锚点仍在当前页区间内就**不动锚点**（`TxtPagedController.kt:69-83` 的注释是铁律）。
2. 加载纪律：新请求 cancel 旧请求 + 完成后校验章号未变（对齐 `goToChapter` 现有防护）；`isLayingOut` 期间显示转圈。
3. **相邻章预取**：当前章排版完成后，后台预取 ±1 章的 `blocks`（IO，DocumentCache 兜底容量）并预排版（Default，各自新建 ruler）。`ChapterLayout` 保留最多 3 章（prev/cur/next 的 LRU），内存 ≈ 0.5 MB/章 × 3，加 DocumentCache ≤ 2M 字符，总量可控。预排版让 P4-d 的跨章拖动有邻页可画，也让点按翻章从「解压+排版」变成纯状态切换。
4. `PageIndexStore` 写通缓存照搬；**顺手更新 `ReaderPageIndexEntity.kt:24` 的 kdoc**——旧注释写「EPUB 用缓存文件路径的 hash」，实际统一用书籍 id（与 TXT、与全屏 UI 的 bid 语义一致；同 id 重导内容变化由 `char_count` 校验兜底，TXT 已接受同等风险）。
5. 进度：`progressPercent = (章基址 + co) / totalChars`（估算量纲，但比现在的「章序号 / 章数」平滑单调）；末章末页钳 100。

### 4.3 `PagedEpubReaderHost`

以 `PagedTxtReaderHost` 为模板的平行宿主，复用 `PageCanvas`、`PageSelection`、页脚、手势、`LayoutConfig` 组装（含 `typefaceKey` 的 `"|b=$fontWeightBold"` 后缀构造——两宿主必须同构造，否则同配置不同指纹）。差异点：

1. **图片层**：`PageCanvas` 画完文字后，宿主在同一 Box 里按 `page.images` 叠加绝对定位的 `AsyncImage`（Coil 2.7.0 已在依赖里，`model = File(sourceKey)`，`Modifier.offset(left, top).size(width, height)`，`ContentScale.Fit`）。Coil 自带按目标尺寸采样解码，不会整图进内存。
2. **偏移接线**（三处，量纲各不同，接错即漂）：
   - **TTS**：EPUB 的 `tts.currentSentenceRange` 本来就是章内偏移（§1.2），宿主参数直接收章内区间 `ttsRangeInChapter`，**不要**套用 TXT 宿主「全书偏移减章基址」的换算；
   - **选区 → locator**：长按选句得章内区间后，回调上抛 `onSelect(text, globalOffset)`，其中
     `globalOffset = bookIndex.chapterStartOffsets[ci] + sent.first`（**混合量纲**）；ReaderScreen 写进 `selectedGlobalOffset`（EPUB 的 `computeLocatorJson` 只认它，写 `selectedRangeStart` 会静默丢 locator，`ReaderScreen.kt:1287-1291`）；
   - **高亮/笔记跳转**：`pendingHighlightId` 路径在 pager 开启时改为 `controller.open(ci, locOffset - 章基址)`——从块级定位升级为**字符级**，且不再需要 `navFocusBlockIndex` 那次块索引计算。
3. **TTS 跟读翻页**：句区间起点越出当前页区间 → `controller.open(ci, sentStart)`（对齐 TXT 引擎的 `jumpToPlainOffset` 跟随，`ReaderScreen.kt:1589-1596`）。
4. 目录/进度滑块/搜索跳转：都已收敛在 `goToChapter`（§1.2），pager 开启时该入口改调 `controller.open(ci, 0)`；搜索仍章级（P5 升级，空白折叠空间问题见 §1.2）。
5. 页脚：章节名 + 「第 x / N 页」（本章排版完成后有真页号，排版中退回百分比）。

### 4.4 ReaderScreen 接线与开关

1. **新设置项** `epubPagerEngineMode: String = "off"`（`"off" | "on"`），设置行文案「翻页新引擎（试验，EPUB）」，与 TXT 开关并排两处（ProfileScreen + 阅读器内 SettingsSheet）。**不复用** `pagerEngineMode`：两条引擎路径要能独立回退，一个格式出问题不连坐另一个。
2. 渲染分支序（`ReaderScreen.kt:1800` 起的 when）：

   ```
   epubBook != null && epubPagerOn && chapterBlocks 就绪 -> PagedEpubReaderHost   // 新增
   epubBook != null && readerMode == "paged"            -> PagedEpubView          // legacy 保留
   epubBook != null                                     -> LazyColumn             // legacy 保留
   pagerEngineOn && …                                   -> PagedTxtReaderHost
   else                                                 -> TXT 滚动
   ```

3. **进度持久化超集双写**：`EpubRepository.saveProgress` 增加章内偏移参数，
   `current_location_json = {"chapter":N,"offset":co}`；`loadProgress` 的旧正则只读 `chapter`，旧版本 App 读新数据不受影响；新增 `loadProgressOffset` 读 `offset`（缺省 0）。零 schema 改动、零迁移窗口。
4. 首开恢复：`epubPagerOn` 时用 `(chapter, offset)` 调 `controller.open`；关着则维持现状只用章号。
5. TTS 续读偏移（`ttsResumeOffset`）现状是章内偏移但没记章号（换章后错套，属既有 bug）——P4 顺手把它的存取键加上章号（仍是 SettingsStore 键值，无迁移），不展开做 P5 的全套 locator 改造。

### 4.5 通过标准

- **真机（小米 22081212C）截图验收**，CLAUDE.md 纪律：
  1. 打开含插图 EPUB：章内左右翻页；插图整块不跨页、水平居中、比例正确；整页大图独占一页；封面章（单图）正常。
  2. 翻到下一章首页 / 上一章末页；快速连翻不错乱（Job 取消防护）。
  3. 改字号 → 停在同一句（锚点纪律）；转屏 → 同上。
  4. 长按选句 → 高亮落库 → 从笔记列表跳回 → **字符级**定位到该句所在页。
  5. 用 P3 之前写的历史高亮跳转 → 定位正确（混合量纲兼容的实证）。
  6. TTS：句高亮矩形正确；跟读自动翻页；暂停恢复不漂。
  7. 关闭开关 → 行为与今天逐字节一致；两个引擎开关互不影响。
- 单测：`chapterTextOf(blocks) == blocks.filterIsInstance<Text>().joinToString("\n")` 锁死；`layoutBlocksOf` 的偏移可逆断言（任意段任意 k：`chapterText[para.charOffset + k] == para.text[k]`）；跨章 open/next/prev 状态机测试。
- 性能：翻章（已预取时）无感知延迟；同章往返零解压（P4-a 已验，此处回归）。

---

## 5. P4-d · PageTurner 翻页动画

**目标：四档动效（none / fade / slide / cover）对 TXT 与 EPUB 两个宿主生效，60 fps、零一帧闪回、额外内存 ≈ 0。**

### 5.1 形态

抽出 `pager/PageTurner.kt`：一个 Composable 容器，输入「当前帧 + 邻帧提供函数 + 效果档位」，输出带手势与动画的页面层。两个宿主都把「`PageCanvas` + 图片叠加层」包进它。

```kotlin
/** 一帧 = 绘制一页所需的全部引用。只是引用聚合，无位图、无拷贝。 */
class PageFrame(
    val page: ChapterPaginator.Page,
    val chapterText: String,
    val cfg: LayoutConfig,
    /** EPUB 图片层要用；TXT 恒空。 */
    val images: List<PlacedImage> = emptyList(),
)
```

- 现有 fade 的 `PageFrame(page, chapterText)`（`PagedTxtReaderHost.kt:308`）**吸收进来而不是并存**：fade 档就是 PageTurner 的一种实现分支（保留 `Crossfade(tween(180))` 的现实现，它已真机验证过）。
- 邻帧来源：章内 = `layout.pages[pageIndex ± 1]`（本来就驻留内存）；跨章 = 预排版的邻章 `ChapterLayout` 边缘页（P4-c §4.2-3）。邻章尚未排完 → 该方向禁用拖动跟随，松手后走现状的「转圈 + 切页」，**不画占位假页**。

### 5.2 slide / cover 实现

- 状态：`val dx = remember { Animatable(0f) }`；手势从「松手判方向」升级为**拖动跟随**（`detectHorizontalDragGestures` 里 `dx.snapTo(dx.value + dragAmount)`，钳在 `[-w, w]`，且越边界（无邻页方向）时钳 0）。
- 绘制：两个 `graphicsLayer` **只改 `translationX`**——
  - `slide`：当前页 `translationX = dx`，邻页 `translationX = dx ± w`（并排推移）；
  - `cover`：进入页在上层从边缘滑入覆盖（`translationX = dx ± w`），底下的旧页不动；上层左缘画 4dp 渐变阴影强化层次。`curl` 设置值映射到 cover（真卷曲需 mesh shader，SIDECAR 不做清单第 8 条），设置文案写「覆盖」。
- 松手：`abs(dx) > w/4` 或速度阈值 → `dx.animateTo(±w, tween(220))`，否则回弹 `animateTo(0)`。
- **两段式提交（SIDECAR §3.3-5 的修正，防一帧旧页闪回）**：

  ```
  animateTo(±w) 完成
    → controller.nextPage()/prevPage()          // 状态先换
    → withFrameNanos { }                        // 等新页真正上屏一帧
    → dx.snapTo(0f)                             // 再归零 transform
  ```

  顺序颠倒（先 snap 再换页）会有一帧画回旧页。
- 动画期间禁止二次手势入队（`dx.isRunning` 时忽略新拖动起手），防连滑错帧。

### 5.3 设置 UI

两处档位列表（`ProfileScreen.kt:1039`、`ReaderScreen.kt:3452`）从 none/fade 扩到四档：
「无动画 / 柔和淡入 / 平移 / 覆盖」。`SettingsStore` 值域注释里的 `curl` 保留为合法输入（映射 cover），UI 不再单列。

### 5.4 通过标准

- 真机 GPU 呈现模式分析（开发者选项）或 `dumpsys gfxinfo`：连续翻 30 页无红柱（> 16ms 帧）；TXT 与 EPUB、四档各验。
- 逐帧核验（慢动作录屏）：settle 瞬间无一帧旧页闪回；fade 档跨章旧页按旧章文本绘制（既有行为回归）。
- 拖到一半松手回弹；边界（全书首/末页）拖不动；动画中连点不错页。
- 内存：`dumpsys meminfo` 对比动画前后，无位图级增量（PageFrame 只是引用）。
- 含插图页参与 slide/cover 时图片层随 transform 同步移动（图片叠加层必须在 graphicsLayer 内侧）。

---

## 6. 明确不做（P4 边界）

| 项 | 理由 / 归属 |
|---|---|
| 行内图片（文字流中混排小图） | 需动 Clusterizer/TextRuler/LineComposer 全链路；EPUB 中文小说的图几乎全是块级。永不做，除非真书出现 |
| EPUB 富样式（粗斜体/字色/CSS） | 排版内核只认纯文本 + HEADING 角色，是既定决策 |
| 搜索章内精确跳转 | 命中偏移在空白折叠空间（`ReaderScreen.kt:2757`），需先统一搜索文本空间，属 P5 |
| 真实字符数回填 / locator v2（ci/co 超集字段） | SIDECAR P5 全套，P4 只按混合量纲写旧格式 |
| 真·卷曲 curl | mesh shader / API 31+，映射 cover（SIDECAR 不做清单） |
| 截图缓存做动画 | 7.25 MB/页，SIDECAR 不做清单 |
| 预分页全书 / 全书字数预扫 | 只排当前章 ±1（SIDECAR 教训条目） |
| 字符级拖把手选区 | P3 已定句级；精细选择的逃生口是滚动模式系统选区 |

---

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 并行会话活跃（调研当时 4 分钟前还有新提交） | 动手前 rebase；本文行号仅供定位，实现时以当时代码为准 |
| `PaintTextRuler` 并发（预排邻章） | 每排版任务新建实例，写进 `EpubPagedController` 的构造纪律（§1.3、§4.2） |
| locator 量纲写错（最隐蔽，坏的是用户数据的可回溯性） | §4.3-2 三处接线各写一条单测：往返（写 locator → 反解 → 同一句）断言 |
| `pageStarts` 非严格递增的隐含假设被后人破坏 | 在 `ChapterLayout.pageStarts` kdoc 里写明「非严格递增，图片页与后继文字页可同 start；二分语义取最后一个 ≤」 |
| `fallbackToDestructiveMigration` 还在 | P4 零 schema 改动即是对策；若未来加列，先做真实 v5 库升级验证 |
| EPUB 单章超长（整本压一个 XHTML） | DocumentCache 有 2M 字符上限兜底；排版单章 10 万字 ≈ 数百 ms，`isLayingOut` 转圈可接受，不做分片 |
| 图片文件被系统清缓存（cacheDir 下） | Coil 加载失败即空白区域；`AsyncImage` 的 error 占位画浅灰盒，不崩溃即可（重进书会重解压补回） |

---

## 8. 提交切分

按刀提交，每刀独立可回退：

1. `feat(reader): P4-a EpubDocument 接入渲染路径，同章反复解压归零`
2. `feat(reader): P4-b 排版内核图片块 —— paginateBlocks + 整块不跨页 + 纯 JVM 单测`
3. `feat(reader): P4-c EPUB 章内真翻页 —— EpubPagedController/宿主/进度超集双写（epubPagerEngineMode 开关，默认关）`
4. `feat(reader): P4-d 翻页动画四档 —— PageTurner 两段式提交，TXT/EPUB 共用`

每刀合入前跑 `./gradlew :app:testDebugUnitTest`；P4-c/P4-d 必须真机截图（CLAUDE.md 纪律）。
