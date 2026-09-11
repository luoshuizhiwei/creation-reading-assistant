# R2-S1.2 命中载荷升级：章节 + source 位置 + 上下文 + 文本基准

- 执行方：WorkBuddy（原生端接管）
- 日期：2026-09-11
- 前置：[R2-S1.1 索引覆盖状态模型 + Room v11→v12](workbuddy-r2-s1-1-index-coverage-model.md)
- 状态：**Dev-verified 通过**（编译 + 1936 JVM 测试全绿）
- 范围：仅 `android/**` + 本报告。未 commit / push / tag。

---

## 1. 需求与本片目标

README R2-S1：「命中有书籍/章节/上下文/source位置。」

S1.2 的目标：把命中从「一本书 + 一个分数」升级成**能定位、能标注、能说明覆盖情况**的载荷。

### 改前（实测）

```kotlin
data class ContentHit(
    val bookId: String,
    val score: Int,
    val previewFirstOffset: Int?,   // 在「正文预览串」里 indexOf 出的偏移
)
```

三个硬伤：
1. **没有章节** —— 只知道哪本书，不知道哪一章；
2. **偏移口径是假的** —— `previewFirstOffset` 是把整串 query 在 `reader_preview`（正文前 2 万字）里
   `indexOf` 出来的，既不是全书偏移也不是章内偏移，**绝大多数书（逐章索引的）根本对不上**；
3. **没有文本基准、没有覆盖率** —— 无法满足「两者都搜并标注来源」与「部分索引不是全文完成」。

另外：`search_terms.offsets` 列**从 v11 起一直在写，查询侧从来没读过**。
这次是它第一次被消费，命中坐标的来源因此从「猜」变成「查」。

---

## 2. 关键调研结论（决定了设计，不是拍脑袋）

| 问题 | 结论 | 出处 |
|---|---|---|
| EPUB 的 `chapterStartOffsets` 是什么空间？ | **估算空间** —— 由 ZIP 解压后字节数 `estimatedTextLength` 推算（`LegacyOffsetCodec.chapterStartOffsets`），中文约真实字符 3 倍，**不是真实字符偏移** | `EpubLocatorMapping.kt:13-33`、`LegacyOffsetCodec.kt:27` |
| TXT 各章有没有全书起始偏移？ | 有：`TxtChapterDetector.Chapter.startOffset`，但**每次打开重算**（大文件走 `TxtFileIndexCache` 磁盘缓存），无 Room 表 | `TxtChapterDetector.kt:136-142`、`TxtFileIndexCache.kt:14` |
| `reader_preview` 与正文开头的关系？ | TXT：preview 就是正文前 2 万字（`text.take(20000)`）⇒ **preview 偏移 == 全书偏移**。EPUB：导入时不写 preview | `ShelfImporter.kt:518/613` |
| 阅读器如何消费 (ci, co)？ | `SourceNavigationContract.resolveChapteredPosition`：`abs = chapterStartOffsets[ci] + co`；定位主链路在 `ReaderProgressEffects.kt:347-425` | `SourceNavigationContract.kt:82-121` |

### 由此定下的**坐标口径决策**

> **搜索命中沿用与批注/书签完全相同的坐标 —— `(阅读器章节序号, 章内字符偏移)`，
> 不在搜索侧另发明一套偏移换算。**

理由与后果：
- `SourceNavigationContract.target()` 的 `normalizedForSourceNavigation()` 本身就接受
  「只有 `chapterIndex + charOffset`、没有 `legacyOffset`」的定位符，
  所以搜索结果可以直接喂给既有导航链路，**定位精度与现有笔记/书签完全一致**；
- 反过来，如果搜索侧自己用「估算的 chapterStartOffsets + 真实章内偏移」去算全书偏移，
  等于把两个不同空间相加 —— **一定会跳错章**，而且错得不明显。

⚠️ **已知且必须承认的口径限制**：EPUB 的 `abs` 走的是估算空间，这是阅读器**既有**的行为
（现有书签/笔记同样如此），不是本次引入的。搜索侧不放大也不掩盖它：
命中始终保留 `(ci, co)` 原值，由阅读器按它自己的口径解释。

### 索引章节号 ↔ 阅读器章节号

索引侧把 `chapter_index = 0` 留给「元数据 + 正文预览」，逐章从 1 开始；阅读器章节是 0 基的。
换算统一收进 `SearchHit.readerChapterIndex`，**禁止调用方就地 `-1`**（以后改编号会静默错位）。

---

## 3. 改动内容

### 3.1 新增纯 Kotlin 命中模型（无 Android/Room 依赖，可 JVM 单测）

`feature/search/SearchHit.kt`（**新增**）

```kotlin
data class SearchHit(
    val bookId: String,
    val score: Int,
    val textBasis: SearchTextBasis,     // 原文 / 替换显示文
    val indexChapterIndex: Int,         // 0 = 元数据或预览，≥1 = 逐章
    val charOffset: Int?,               // 章内（或预览内）起始字符偏移
    val matchLength: Int?,
    val coverage: SearchCoverageState?, // 该书该基准的覆盖率
    val coverageReason: String?,
) {
    val readerChapterIndex: Int?   // 逐章命中 = indexChapterIndex - 1；预览/元数据为 null
    val isMetadataHit: Boolean     // 章号 0 且无偏移 —— 只能打开书，不能跳位置
    val isPreviewHit: Boolean      // 章号 0 但有偏移
    val hasSourcePosition: Boolean // 有章节号 + 章内偏移 ⇒ 可精确跳转
}

data class SearchHitContext(text, matchStart, matchEndExclusive, clipped)
object SearchContext { fun extract(body, offset, matchLength, radius = 40): SearchHitContext? }
```

### 3.2 新增命中挑选与 offsets 解析（纯函数）

`feature/search/SearchHitSelection.kt`（**新增**）

- `SearchOffsets.parse(csv, limit)` —— 解析 `"start:len,start:len"`。
  容错原则：**单个坏片段只跳过自己**，不让整条命中失效（宁可少一个精确偏移，
  也不能把这本书从结果里弄丢）。
- `SearchHitSelection.scoreOf(matches)` —— `Σ hits × queryTermCount`。
- `SearchHitSelection.pickCoordinate(matches)` —— 挑展示坐标，规则顺序显式可断言：
  1. **先按该章命中了几个不同的查询词降序**（多词共现的那一章才是用户想看的那段，
     比「词频最高的章」更符合直觉）；
  2. 同分取**索引章节号最小**的（书里最靠前的一处）；
  3. 章内取**偏移最小**的命中区间。

### 3.3 查询侧重写

`SearchIndexRepository.kt`

```kotlin
suspend fun searchContent(
    query: String,
    bases: Set<SearchTextBasis> = setOf(SearchTextBasis.ORIGINAL),
    limit: Int = 50,
): List<SearchHit>
```

- 返回 `List<SearchHit>` 而不是 `Map<String, ContentHit>`：同一本书在两种基准上各有命中时
  **各出一条**（这正是「两者都搜并标注来源」的口径）；
- 查询走新的 `rowsByTermForBasis(term, basis)` —— **基准进 WHERE**，
  否则 original / display 两行混在一起会让坐标和覆盖率对不上；
- 命中坐标来自 `search_terms.offsets`（首次被消费）；
- 每本书挂上 `search_index_coverage` 的真实覆盖率与原因码；
- 整体包在 `withContext(Dispatchers.IO)` 里（查询要读库）。

`ContentHit` 已删除（原本零调用方）。

### 3.4 上下文抽取

```kotlin
suspend fun contextFor(hit: SearchHit, radius: Int = 40): SearchHitContext?
```

- 预览命中 → 直接读 `reader_preview`（TXT 的 preview 即正文前缀，偏移可直接用）；
- 逐章命中 → 加载该章正文：TXT 解码后按 `splitTxtIntoChapters` 取片（与索引写入**同一套分章口径**，
  保证坐标对得上）、EPUB 走 `EpubParser.parse` + `loadChapterText`；
- **大 TXT 直接放弃**：`TXT_CONTEXT_MAX_BYTES = 8MB`。上下文只要一章，但 TXT 没有字节级章偏移缓存，
  只能先解码全文再分章；20MB 文件解码后是 ~40MB 的 UTF-16 String。
  **宁可不显示片段，也不为一片上下文冒 ANR / OOM 的风险**（项目既有红线）。
- 返回 null 时 UI 应退化为「书名 + 可跳位置」，而不是显示错位的文本。

### 3.5 DAO

`SearchTermDao.kt` 新增 `rowsByTermForBasis(term, textBasis)`。
**无需新迁移** —— 只加查询方法，表结构不变（schema 仍是 v12）。

---

## 4. 验证（真实 JUnit XML 计数）

| 项目 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | **EXIT=0**（首次失败一次，见 §6） |
| `feature.search` 三个测试类 | **32 tests / 0 fail / 0 err / 0 skip** |
| 全量 JVM | **231 suite / 1936 tests / 0 fail / 0 err / 0 skip** |

明细：`SearchCoveragePolicyTest` 10（S1.1）、`SearchHitSelectionTest` **13**（新）、`SearchContextTest` **9**（新）。

---

## 5. 改动文件清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `feature/search/SearchHit.kt` | 新增 | `SearchMatchSpan` / `SearchHit` / `SearchHitContext` / `SearchContext` |
| `feature/search/SearchHitSelection.kt` | 新增 | `SearchOffsets` / `RawTermMatch` / `SearchHitSelection` |
| `data/local/dao/SearchTermDao.kt` | 修改 | 新增 `rowsByTermForBasis` |
| `data/repository/SearchIndexRepository.kt` | 修改 | `searchContent` 重写；新增 `contextFor` / `loadChapterTextForContext`；删 `ContentHit` |
| `test/…/feature/search/SearchHitSelectionTest.kt` | 新增 | 13 项 |
| `test/…/feature/search/SearchContextTest.kt` | 新增 | 9 项 |

无新迁移、无 schema 变更。

---

## 6. 途中错误与修正（写下来防复发）

1. **编译失败一次**：`Smart cast to 'kotlin.Int' is impossible, because 'readerChapterIndex' is a
   property that has an open or custom getter`。
   原因：`SearchHit.readerChapterIndex` 是**计算属性**（自定义 getter），`if (x != null)` 后无法智能转型。
   修法：先捕获成局部 `val readerChapter = hit.readerChapterIndex` 再判空。
2. **自己写的测试里抓出两个错误断言**（已修）：
   - `assertEquals(body.length, ctx.text.length + (body.length - ctx.text.length))` —— **恒真断言**，
     典型的伪测试，改成对具体下标/子串的断言；
   - 两个边界断言算错了期望值（偏移 1 的 `matchStart` 应为 1 不是 0；
     radius 5 的窗口在 36 字正文里本来就 `clipped = true`）。
   教训重申：**边界测试必须先手算再写**，否则「全绿」毫无意义。

---

## 7. 未覆盖项 / TODO

1. **`searchContent` 依旧是死 seam** —— 本片只升级载荷，生产侧仍无调用方。
   接线到 `SearchViewModel` 是 **S1.3**。
2. **`contextFor` 无 JVM 测试**：它依赖 `BookDao` / `EpubParser` / 磁盘，
   需要 instrumentation 或 Robolectric。纯函数部分（`SearchContext.extract`）已覆盖，
   I/O 分支留给真机/仪器化验证。
3. **EPUB 定位精度受既有估算空间限制**（见 §2），非本片引入；若后续要提高 EPUB 定位精度，
   应整体改 `LegacyOffsetCodec` 的估算策略，而不是在搜索侧打补丁。
4. **`display` 基准仍未建索引** —— `searchContent` 默认只搜 `ORIGINAL`。
   「两者都搜并标注来源」的最终落地在 S1.6（配合显示文通道构建）。
5. 未做：lint、真机/视觉验收、全量打包。

## 8. SEAM REQUEST

**无。** 改动全部落在 `feature/search`（纯 Kotlin）与搜索链路的
`data/local/dao/SearchTermDao` + `data/repository/SearchIndexRepository` 内。
`SearchViewModel` / `SearchScreen` 属于 **S1.3**，本片未触碰。

---

## 9. 结论

- 「命中有书籍」✅ `bookId`
- 「有章节」✅ `indexChapterIndex` + `readerChapterIndex`（换算收在一处，禁止就地 -1）
- 「有 source 位置」✅ `charOffset` + `matchLength`，坐标来自**首次被消费的** `offsets` 列，
  口径与批注/书签一致，可直接进 `SourceNavigationContract`
- 「有上下文」✅ `contextFor()`（含大文件保护；纯函数窗口抽取有 9 项边界测试）
- 「文本基准 + 覆盖率」✅ `textBasis` / `coverage` / `coverageReason`
- R2 退出条件「搜到内容可精确到达并返回」—— **到达**的坐标与解析链路已在 S1.2 备齐，
  实际导航接线在 **S1.4**，返回在 **S1.5**。
