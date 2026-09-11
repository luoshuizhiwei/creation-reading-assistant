# R2-S1.3 全文检索接线（修死 seam）+ 搜索状态反馈

- 执行方：WorkBuddy（原生端接管）
- 日期：2026-09-11
- 前置：[S1.1 覆盖模型 + Room v12](workbuddy-r2-s1-1-index-coverage-model.md)、
  [S1.2 命中载荷升级](workbuddy-r2-s1-2-hit-payload-upgrade.md)
- 状态：**Dev-verified 通过**（编译 + 1942 JVM 测试全绿）
- 范围：仅 `android/**` + 本报告。未 commit / push / tag。

---

## 1. 本片解决什么

`SearchIndexRepository.searchContent` 从 v11 落地起就**零生产调用方** —— 索引一直在后台建，
但没有任何一行 UI 代码读过它。全局搜索页实际只做 `BookRepository.search()`（元数据 LIKE），
即：

> 用户搜一个只出现在正文里的词，什么都搜不到，而界面不会给出任何解释。

S1.3 把这条 seam 接上，并把「当前结果有多全」如实告诉用户。

---

## 2. 改动内容

### 2.1 `SearchViewModel`：消费全文索引

- 注入 `SearchIndexRepository`（构造参数插在 `noteRepository` 之后、`historyStore` 之前）；
- 搜索时并行取 `searchContent(query)`，与既有的元数据/灵感/笔记/高亮合并；
- **索引失败只降级**：`runCatching { … }.getOrDefault(emptyList())`。
  索引是派生数据，它挂了绝不能让元数据搜索一起失效（有测试断言这点）；
- `SearchResults` 新增三个字段：

| 字段 | 用途 |
|---|---|
| `contentHits: List<SearchHit>` | 正文命中，按分降序，携带章节/偏移/文本基准/覆盖率 |
| `booksAlsoMatchedInContent: Set<String>` | 元数据命中里「同时也在正文命中」的 bookId —— 「全部」页去重用 |
| `notice: SearchNotice?` | 完整性反馈 |

### 2.2 完整性反馈（不假装结果就是全部）

```kotlin
enum class SearchNoticeKind { INDEX_NOT_BUILT, COVERAGE_INCOMPLETE }
data class SearchNotice(kind, affectedBooks)
```

- `countIndexedBooks() == 0` → `INDEX_NOT_BUILT`（"全文索引尚未建立，当前只搜到了书名、作者等元数据"）
- 命中的书里有**已知非全量**的 → `COVERAGE_INCOMPLETE`（"有 N 本书只索引了部分内容…"）

关键取舍：**`coverage == null`（尚未得知）不计入未完成**，否则会虚报。
覆盖率一律读 S1.1 落库的 `search_index_coverage`，**不按 format 或命中数推断**。

### 2.3 `SearchScreen`：新「正文」分类 + 去重 + 来源标注

- 新增 **「正文」** 分类胶囊（`SearchContentColor`），渲染 `contentHits`；
- **「全部」页去重**：同时有正文命中的书，只显示正文行（信息更丰富：带位置与覆盖说明），
  书目行在「书籍」页仍全部可见；
- **每条正文命中都标注来源**：`原文 · 第 N 章` —— 这是「两者都搜并标注来源」口径的落地；
  即便当前只建了原文通道也照标，否则将来显示文通道上线时用户无从分辨；
- 副标题为 `第 N 字附近 · 仅索引部分章节` 这类**不撒谎的替代信息**
  （真正的带高亮上下文片段要按行懒加载章节文本，属于后续接线）；
- 顶部提示条 `SearchNoticeBanner`（只说明、不给按钮 —— 索引构建由后台 worker 负责，
  在搜索页放「立即重建」会诱使触发重 I/O）。

### 2.4 顺带修掉一个既有 UX 缺陷

空态判定原用 `totalHits`（**不分 tab** 的总数）。切到一个没有结果的分类时，
既不显示空态、也不显示任何行 —— 列表空白但看不出为什么。
现在按当前 tab 计算 `visibleHits`。

---

## 3. 自查抓出的一个 bug（已修 + 已加回归测试）

`neededIds` 最初写成 `(笔记/高亮的 bookId) + (contentIds - metadataIds)`，
**只取差集**。后果：一本书若**同时**命中元数据与正文，在「全部」页里它只以正文行出现
（书目行被去重掉），但 `bookTitles` 里没有它 —— 标题退化成显示**裸 bookId**。

修法：`neededIds` 包含**全部** `contentIds`。多几个 id 走一次 IN 查询，代价可忽略。

回归测试：`content rows keep their title even when the book also matched metadata`。

---

## 4. 验证（真实 JUnit XML 计数）

| 项目 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | **EXIT=0** |
| `:app:compileDebugAndroidTestKotlin` | **EXIT=0** |
| `SearchViewModelTest` | **10 tests / 0 fail**（原 4 + 新 6） |
| 全量 JVM | **231 suite / 1942 tests / 0 fail / 0 err / 0 skip** |

新增测试：
1. 正文命中与书名一起返回
2. 索引未建时报 `INDEX_NOT_BUILT`
3. 覆盖未完成**只统计已知非全量**（含 `coverage == null` 不得虚报）
4. 双命中的书被标记出来供去重
5. 双命中的书仍能取到书名（§3 回归）
6. 索引抛异常时降级为「无正文命中」，元数据搜索不受影响

> MockK 注意点：`searchContent` 有默认参数，mock 必须写成
> `coEvery { repo.searchContent(any(), any(), any()) }`（三个 `any()`），
> 只写一个 `any()` 匹配不到默认参数合成后的调用。

---

## 5. 改动文件清单

| 文件 | 说明 |
|---|---|
| `ui/viewmodel/SearchViewModel.kt` | 注入索引仓储；合并结果；`SearchNotice` / `SearchNoticeKind`；`buildNotice()` |
| `ui/screen/search/SearchScreen.kt` | 「正文」分类；去重；来源标注；提示条；按 tab 的空态判定 |
| `test/…/ui/viewmodel/SearchViewModelTest.kt` | 适配新构造参数 + 6 项新测试 |

无 schema 变更、无迁移。

---

## 6. 未覆盖项 / TODO

1. **正文命中的点击仍只打开书**（`reader/${bookId}`），未带 source 定位 —— 这是 **S1.4**。
   本片刻意不做：S1.4 要一并处理「临时查阅 vs 普通导航」的语义选择。
2. **上下文片段仍是坐标描述，不是真实原文片段**。`contextFor()` 已就绪（S1.2），
   但要按行懒加载（suspend + I/O），需要 `LaunchedEffect` + 每行 state，
   与 S1.4 的跳转一起做更合适。
3. **真机/视觉验收未做**（设备 `c49ac6cf` 处于 offline，与 S1.1 同因）。
4. 未做：lint、全量打包。
5. 「两者都搜」当前实际只有原文通道有索引；显示文通道索引构建属后续片，
   届时把 `searchContent` 的 `bases` 传 `setOf(ORIGINAL, DISPLAY)` 即可，UI 已会标注。

## 7. SEAM REQUEST

**无。** 改动落在 `ui/viewmodel/SearchViewModel` 与 `ui/screen/search/SearchScreen`，
这两个文件本就属于搜索链路；`feature/reader/**` 与 `ui/navigation/**` 未触碰（留给 S1.4）。

---

## 8. 结论

- 死 seam 已接：**全文索引第一次被搜索 UI 真正消费**。
- 搜不到不再是「静默什么都没有」：索引没建会明说，覆盖不全会明说。
- 每条正文命中都带来源标注与覆盖说明，为「两者都搜并标注来源」的最终口径铺好路。
- R2 退出条件「搜到内容可精确到达并返回」—— 结果已能**出现**，下一步 S1.4 让它**精确到达**。
