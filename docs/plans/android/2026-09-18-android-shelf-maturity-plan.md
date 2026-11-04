# 书架页面成熟化方案（对标起点 / 番茄 / QQ阅读）

> **P0 已实现并真机验证通过（2026-09-18）。** 本文第 0–2 节是诊断与设计依据，第 3 节的 P0 已落地，
> P1–P3 仍为待评审方案。实现口径与实测结果见文末「§8 P0 实施记录」。
>
> 起因（用户原话）：
> ① 「对于读完的书籍，无法将它划分到读完的分组中」——书架页面不够成熟；
> ② 「新导入的页面应该放在书籍的前面，不然容易找不到」（学自起点）。
> 调查日期：2026-09-18。所有引用行号基于当日工作树（Room v15）。

---

## 0. 诊断：为什么"读完的书无法划分到读完分组"

结论先行：**书架并不缺"分组"容器，缺的是"读完"这个状态在书架页根本没被识别，以及识别出来之后没有一条点得到的入口。**

三个层次的原因，按重要性排序：

### 0.1 根因：`bookStatus()` 把"读完"判定短路了
`android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfModels.kt:68`

```kotlin
internal fun bookStatus(book, percent, completionState): ShelfStatusFilter {
    val p = percent.coerceIn(0f, 100f)
    return when {
        fromStorage(completionState) == SHELVED -> SHELVED
        book.isDownloaded() -> READABLE          // ← 命中这里就再也不会往下走
        p >= 99.5f -> COMPLETED                  // ← 死分支
        p > 0f -> READING
        else -> UNREAD
    }
}
```

`isDownloaded()` → `hasLocalBookSource()`
（`feature/reader/BookLocalAvailability.kt:12`）对 `content://` 开头的 SAF Uri **直接返回 `true`**：

```kotlin
val uriReadableOrDelegated = local_uri?.takeIf { it.isNotBlank() }?.let { uri ->
    if (uri.startsWith("file://", true)) { ... } else { true }   // content:// 一律放行
} ?: false
```

而 `ShelfImporter` 在导入时**总会**写 `local_uri = uri.toString()`
（`feature/library/ShelfImporter.kt:651 / 675 / 717 / 731 / 810 / 828`）。

**两者相乘的结果：凡是本机导入的书，一律被判为「本机可读」，`p >= 99.5f → COMPLETED` 永远不执行。** 用户读完一本书，进度 100%，书架仍然认为它是"本机可读"。

这解释了用户观察到的全部现象——不是不能分组，是书架**根本不知道这本书读完了**。

### 0.2 关键澄清：数据层是对的，坏的只有书架页

这一点必须说清楚，否则容易修错地方。

阅读器写进度走 `BookRepository.saveProgress`（`BookRepository.kt:103`），
内部调用 `mergeReaderProgress`（`ReadingEntities.kt:67`），其状态判定是：

```kotlin
val state = when {
    percent >= 99.5f -> FINISHED                              // ← 读到 100% 就写 finished
    existing?.readingState == SHELVED -> SHELVED
    existing?.readingState == FINISHED -> FINISHED
    else -> READING
}
```

**所以一本书读到 100%，数据库里 `reading_progress.completion_state` 确实已经是 `"finished"`，
`completed_at` 也已写入。** 数据是完整的、正确的。

坏掉的是书架页的**读侧**：`bookStatus()` 既被 `READABLE` 短路、又不看 `FINISHED`，
于是这份正确的数据在书架上被完全忽略。这也解释了为什么"我的阅读"页能正确分组
（`MyReadingViewModel.kt:117` 显式判 `FINISHED`）而书架页不能 —— 同一个事实，两个页面两套口径。

**结论：这是纯粹的 UI 判定与入口问题，不涉及数据修复、不需要回填历史数据。** 修 `bookStatus()` 后，
用户**已有的**读完的书会立刻正确归类，无需任何数据迁移。这是 P0 风险极低的根本原因。

### 0.3 同一函数的第二个缺口：手动"标记读完"书架不认

`bookStatus()` 只读 `completion_state` 的 `SHELVED` 值，**完全没有判 `FINISHED`**。
而"标记为已读完"这条能力是存在的：`BookRepository.setReadingState(bookId, FINISHED)`
（`data/repository/BookRepository.kt:329`）、`BookViewModel.markRead`（`ui/viewmodel/BookViewModel.kt:59`），
入口在首页继续阅读菜单（`ui/screen/home/HomeContinueSheet.kt:125`）。

**用户手动标记了读完 → 数据落库了 → 书架页看不见。** 这正是用户说的"无法将它划分到读完的分组"。

### 0.4 入口缺口：`StatusRail` 是孤儿代码

`ui/screen/shelf/BookGrid.kt:166` 定义了完整的状态筛选条（全部/在读/已完成/未开始/本机可读），
`ShelfStatusFilterOptions`（`BookGrid.kt:156`）是它的私有数据源。

- **`StatusRail` 在 `app/src/` 下没有任何调用点**（只在 `baseline-prof.txt:18059` 的编译产物索引、
  `SharedComponents.kt:161` 的注释、`ShelfScreen.kt:327` 的结构注释里出现，均非调用）。
`ShelfAction.UpdateStatusFilter`（`ShelfAction.kt:37`）与 Route 分支（`ShelfRoute.kt:622`）都在，**只差一个发送方**。

结果：状态筛选唯一的可达路径是「书架标题 ▾ → 书架整理 → 阅读状态 → 全屏单选页」——
四跳，且"书架整理"这个入口名完全不像筛选入口。用户不可能找到。

### 0.5 附带发现（本次调查新发现，均需在实施时一并处理）

| # | 发现 | 位置 | 影响 |
|---|------|------|------|
| A | `ShelfBookEntity.position` 是死字段：定义了 `position: Int = 0`，但 `getBookIds` 查询无 `ORDER BY`，全仓无任何读写 | `TaxonomyEntities.kt:120`、`TaxonomyDao.kt:225` | 书单内手动排序不存在 |
| B | `ShelfStatusFilterTest` 把 0.1 的 bug 锁成了断言 | `app/src/test/.../ShelfStatusFilterTest.kt:14` | `assertEquals(READABLE, bookStatus(downloaded(), 100f))` —— 测试保护了错误行为 |
| C | 排序默认值其实**已经是**导入时间倒序 | `data/settings/ShelfPrefs.kt:37`（`DEFAULT_SORT_MODE = "imported"`，注释标 M-FEAT-01） | 用户仍找不到新书 → 说明是**已持久化的旧偏好**在覆盖默认值 |
| D | `ShelfPrefsTest` 写的是共享 tmpdir，不是每测试隔离 | `testutil/TestDoubles.kt:118`（`java.io.tmpdir/cra-datastore-tests`） | 测试间会互相污染排序偏好，改默认值时要留意顺序耦合 |
| E | 同一次"标记读完"有两个语义不同的实现 | `BookViewModel.markRead`（改进度为 100 + 刷新 `last_read_at`）vs `setReadingState`（只改状态） | 前者会让未读过的书跳到"最近阅读"榜首，属副作用 |
| F | 两条进度写入路径语义不一致：`updateReadingProgress` 不保留 FINISHED，`mergeReaderProgress` 保留 | `BookRepository.kt:301` vs `ReadingEntities.kt:67` | **已核实**：阅读器自动进度走 `BookRepository.saveProgress` → `mergeReaderProgress`（`BookRepository.kt:103-112`），数据层**是正确的**；`updateReadingProgress` 只被 `BookViewModel.markRead/markUnread` 使用（`BookViewModel.kt:60,64`）。故 F 不影响 P0-1 的正确性，但仍应在 P0-3 统一口径 |

---

## 1. 对标：起点 / 番茄 / QQ阅读 的共同范式

三家成熟阅读应用的书架，在以下 7 点上高度一致（这是"成熟"的实际含义，也是本方案的取舍依据）：

1. **顶部分组条（横向 tab）常驻首屏**：最左固定「全部」，其后是**系统智能分组**（在读 / 未读 / 已读完 / 搁置），再后是**用户自建分组**。点一下即切换，不需要进任何二级页。
2. **系统分组自动算，用户分组手动管**：系统分组由阅读状态与进度推导，不可删、不可重命名；用户分组可增删改名排序。
3. **长按 → 多选 → 批量移入分组**：分组操作的主路径是"多选后批量移动"，不是逐本进详情页。
4. **新加入的书在最前**：默认按加入时间倒序，并配「新」角标；导入完成后视图会带用户看到新书。
5. **编辑模式**：可拖动排序、可置顶。
6. **封面角标**：进度百分比或「完」字，一眼可辨状态。
7. **分组带计数**：分组 tab 上显示「已读完 30」这类数量，便于判断该不该点。

对照本仓库现状：**2、3、6 已具备**（书单/分类/标签三套组织 + 批量面板 + 进度角标），
**1、4、5、7 缺失或不达预期**。所以本方案的重心是"把已有的能力提到台面上"，而不是"再造一套分组"。

---

## 2. 总体设计决定

### 决定 1：不新增独立"分组"实体，把已有书单 + 状态筛选提升为一级导航

理由：
- 已存在三套组织机制：`ShelfEntity`（书单，`taxonomy` 表）、`CategoryEntity`（分类）、`TagEntity`（标签），
  外加连接表 `ShelfBookEntity` / `BookCategoryEntity` / `BookTagEntity`，DAO 与 UI（`ShelfOrganizerRoute`、
  `BatchSheet`、`BookDetailManagementSection`、`LibrarySubPage`）全部完整可用。
- 再引入第四个"分组"概念会与"书单"语义重叠，代价是 **schema 迁移 + 同步契约 + JsonBridge 备份导出** 三处联动改动。
- 用户抱怨的实质是"读完这个状态书架不认、也点不到"，不是"缺少一个分组容器"。

### 决定 2：把"本机可读"从状态分类里拿出来，降级为正交标记

`READABLE` 与 `COMPLETED` 是**两个独立维度**：能不能离线读 ≠ 读没读完。
现在的实现让前者吃掉了后者，这就是 0.1 的 bug。修正后：
- **状态分类**（互斥）：在读 / 未读 / 已完成 / 搁置 —— 由进度与 `completion_state` 决定。
- **本机可读**（正交）：作为书卡上的一枚胶囊/角标呈现（现有 `readiness()` 已经在做这件事，
  见 `ShelfModels.kt:35`，书卡上已有"可离线阅读"胶囊）。

即：`READABLE` 从 `ShelfStatusFilter` 的**筛选条**里移除，但 `readiness()` 的展示保留不变。

### 决定 3：状态语义变更必须同时改测试，且要在方案里显式声明

`ShelfStatusFilterTest:14` 的断言锁住了错误行为。实施者若只改源码，测试会红；
若以为测试权威而回退源码，bug 就永远修不掉。**必须显式改这条断言**，并在提交信息里说明原因。

---

## 3. 分期实施

### P0 —— 最小可用，直接解决用户的两点抱怨（建议优先落地）

#### P0-1 修正读完态判定（`bookStatus`）

新口径（**顺序即语义，不可调换**）：

```
1. completion_state == SHELVED   → 搁置
2. completion_state == FINISHED  → 已完成      ← 新增：尊重用户手动标记
3. percent >= 99.5f              → 已完成      ← 现在能真正到达
4. percent > 0f                  → 在读
5. 否则                          → 未读
```

同步改动：
- `ShelfStatusFilterTest`：`bookStatus(downloaded(), 100f)` 期望值 `READABLE` → `COMPLETED`；
  补 `completion_state == "finished"` 但进度很低的用例（期望 `COMPLETED`）；
  补 `SHELVED` 优先级仍高于 `FINISHED` 的用例（两者同时成立时按搁置算，与现有 `when` 首分支一致）。
- `ShelfSearchRoute.kt:442` 也调用 `bookStatus`（**不传 `completionState`**，第三参用默认值 `null`），
  修正后会一并生效。这里需顺带把 `progressById[book.id]?.completion_state` 传进去，
  否则搜索页对"手动标记读完但进度低"的书仍判错 —— 这是与书架**不一致**的漏点，实施时不要漏。
- `ShelfStatusFilter.READABLE` 枚举值**保留**（`ShelfSavedViewsStore` 按枚举名持久化已保存视图，
  删枚举值会让历史动态视图静默失效），只是不再出现在筛选条 UI 上。

不改 schema，零迁移。

#### P0-2 把状态筛选提到书架首屏

复活 `StatusRail`（`BookGrid.kt:166`，实现已存在），插入 `ShelfScreen.kt` 的紧凑工具栏区
（`ShelfCompactToolbar`，`ShelfScreen.kt:504` 附近），选项改为：
**全部 / 在读 / 未读 / 已完成 / 搁置**（去掉「本机可读」，理由见决定 2）。

- 发送方：`onSelect = { onAction(ShelfAction.UpdateStatusFilter(it)) }` —— action 与 Route 分支都已存在。
- 计数徽章（对齐范式第 7 点）：在每个 tab 上显示数量，数据来自 `state.library` + `progressById`，
  用与 `filtered` 相同的口径计算，避免"计数与实际结果不一致"这类经典问题。
- 该 rail 与 P1 的分组条**同源实现**，P1 只是往同一个横向容器里追加用户书单。

#### P0-3 长按菜单补状态动作

`BookActionSheet`（`ui/screen/shelf/BookActionSheet.kt`）第二层"常规管理操作微岛卡片"内新增两行：

- **标记为已读完** / **取消已读完**（按当前状态二选一显示）
- **搁置本书** / **恢复为在读**（按当前状态二选一显示）

底层已就绪：`ShelfViewModel.shelveBook`（`ShelfViewModel.kt:451`）、`restoreReading`（`:455`）、
`ShelfBookActions.shelveBook`（`ShelfBookActions.kt:65`）、`restoreReading`（`:73`）全部存在，
**只差 `FINISHED` 这一个方向**。

需要新增：
- `ShelfBookActions.markFinished(id)` / `markReading(id)`（与既有 `shelveBook`/`restoreReading` 同构，
  返回结果消息字符串，供 snackbar）。
- `BookRepository.markFinished(bookId)`：见下面的语义约定。
- `ShelfAction.MarkFinished(bookId)` / `MarkReading(bookId)` + `ShelfRoute` 分支。

**`markFinished` 的语义约定（重要，涉及发现 E）**：

现有两个实现语义不一致，必须统一：
- `BookViewModel.markRead` → `updateReadingProgress(id, 100f, "finished")`：会把 `last_read_at` 刷成当前时间，
  **导致一本从未读过的书跳到"最近阅读"榜首**。
- `BookRepository.setReadingState(FINISHED)`：只改状态与 `completed_at`，不碰进度与 `last_read_at`。

建议新增 `BookRepository.markFinished(bookId)`，口径为：
- `completion_state = "finished"`
- `completed_at = 已有值 ?: now`（保留首次完成时间）
- `progress_percent = max(当前值, 100f)`（只升不降）
- **不动 `last_read_at`，不动 `current_location_json`**（保留真实阅读位置，便于日后回看）

并把 `BookViewModel.markRead` 迁移到同一方法，消除"同一动作在两处行为不同"。
`markReading`（取消读完）走既有 `setReadingState(READING)`，它会清空 `completed_at`
（`BookRepository.kt:337-345` 已实现），无需改动。

**为什么不用 `updateReadingProgress`**：它会经 `requestedState` 分支把状态写成传入值，
且 `ReadingEntities.kt:67` 的 `mergeReaderProgress` 与它语义不同（后者保留 FINISHED/SHELVED）。
两条路径并存会让"用户显式标记"在不同入口被静默覆盖，因此新动作一律走 `setReadingState` 系。

#### P0-4 新导入的书置顶（用户第 2 点）

**先澄清现状**：默认排序**已经是**「导入时间倒序」——`ShelfPrefs.kt:37` 的
`DEFAULT_SORT_MODE = "imported"`，注释明确写了 M-FEAT-01（新书 `last_read_at` 为空，
按"最近阅读"倒序会沉底）。`ShelfViewModel.kt:710` 的兜底同样是 `IMPORTED`。

所以用户"新书找不到"的原因不是默认值错了，而是**两件事**：
1. 用户很可能手动切过排序（或设备上残留了引入该默认值之前的偏好），
   `ShelfPrefs` 一旦被写入就永久覆盖默认值，且没有任何提示让用户切回来；
2. 即便按导入时间倒序，"整表按加入时间排"与"新书置顶"的**感知不同**——
   用户看不出哪几本是刚加的，列表也没有任何视觉锚点。

因此 P0-4 采用三条组合措施（对齐起点"新加入的书插到最前 + 新角标"）：

**(a) 「新」角标 + 已读即消**
- `ShelfViewModel` 在导入成功回调里收集 bookId，维护"未查看的新书"集合。
- 该集合持久化到 `ShelfPrefs`（新增一个 `shelf_new_book_ids` 键，StringSet 或分隔符串，
  与既有 `recentSearches` 同风格），**跨重启保留**，直到用户点开该书才移除。
- 书卡复用现有 TopEnd 徽章位渲染「新」（`BookGrid.kt:380` 的 `contentUpdated` 徽章已是这个位置，
  注意两者不能同时占用同一角——建议「新」优先，或错开 TopEnd/TopStart）。
- 网格态与列表态都要有（列表态现有 `contentUpdated` 用小圆点，`BookGrid.kt:499`）。

**(b) 导入完成后一键定位到新书**
- 现在导入批次结束时只弹 snackbar（`ShelfRoute.kt` 对 `importBatch` 的 `LaunchedEffect`，
  文案来自 `importBatchSnackbarMessage`）。
- 给该 snackbar 增加一个 **「查看」action**：点击后把列表滚动到第一本新书位置并短暂高亮。
- 滚动与高亮的实现可直接复用本轮「设置项搜索」已落地的模式
  （`SettingsFocusController.reveal()` 的半屏步进 + `SettingsAnchor` 的琥珀色高亮 + 自动消退），
  书架是 `LazyVerticalGrid` 而非 `LazyColumn`，需改为 `LazyGridState.scrollToItem(index)`，
  终止条件仍用"目标已组合"而非固定下标（列表会因筛选/导入变化而位移）。
- 尊重 `rememberReducedMotion`（降级为瞬时定位、无高亮动画）。

**(c) 文案与默认值**
- 排序面板中 `IMPORTED` 的文案由「导入时间」改为 **「最近加入」**（对齐起点用词）。
  该文案在**四处重复实现**，必须同改：`SortSheet.kt:49`（`SORT_OPTIONS`）、
  `ShelfScreen.kt:510`（`ShelfCompactToolbar` 内的 `when`）、`ShelfOrganizerRoute.kt:770`（`private fun sortLabel`）、
  `ShelfSelectionRoute.kt:359`（选项列表）。建议顺手把两处 `sortLabel` 函数收敛为单一来源，
  否则同一排序会出现三个名字。
- 保留 `DEFAULT_SORT_MODE = "imported"` 不变。

**不做**：跨排序模式强制把新书钉在最前。用户显式选了「书名」排序时，
把新书插到最前会破坏字典序语义，反而制造新的"为什么这本书在这儿"的困惑。
(a)+(b) 已足以解决"找不到"。

---

### P1 —— 分组条升级为一级导航（对齐范式 1、7）

在书架首屏顶部（紧凑工具栏之上）加一条**分组条**，与 P0-2 的 `StatusRail` 同源，横向滚动：

```
[全部 128] [在读 12] [未读 80] [已读完 30] [搁置 6] │ [科幻 20] [待读清单 15] [+ 管理]
 ←────────── 系统智能分组（自动算，不可删）──────────→   ←── 用户书单（来自 shelves）──→
```

- 系统分组点击 → 设 `statusFilter`；用户书单点击 → 设 `selectedShelfId`。
  两者在 `ShelfSessionState` 里是**不同字段**，可同时生效（现有 `filterItems` 会取交集）。
  这里需要一个产品决定：点用户书单时是否清空 `statusFilter`。
  **建议清空**——用户的心智模型是"切换分组"，不是"叠加筛选"，叠加会让人以为书单里的书丢了。
- 末位「管理」→ 现有 `ShelfOrganizerRoute`（书架整理页），无需新页面。
- 保留 `ShelfCompactToolbar` 的筛选摘要文案，作为"叠加了高级筛选"的唯一提示（如「全部 · 2 项筛选」）。
- 用户书单为空时，分组条只显示系统分组，不显示分隔符与空槽位。

**不改 schema**：直接消费 `ShelfViewModel.shelves`（`ShelfViewModel.kt:259`，已 `observeShelves()`）。

### P2 —— 书单内手动排序（对齐范式 5）

激活死字段 `ShelfBookEntity.position`（见发现 A）：

- `TaxonomyDao.kt:225` 的 `getBookIds` 加 `ORDER BY position ASC, book_id ASC`。
- 新增批量 `updatePositions(updates: List<Pair<bookId, Int>>)`，事务内落库（参照
  `data/local/dao/ReaderTextRuleDao.kt:52-61` 的 `updatePosition` + `@Transaction updatePositions` 既有实现）。
- 书单详情页支持拖动排序与置顶。

**⚠️ 迁移风险（必须显式处理）**：`position` 当前**恒为 0**，直接加 `ORDER BY position` 会让
所有书单内的顺序突变为 `book_id` 字典序——用户会认为书单被"打乱"了。
必须在同一迁移里按现有顺序回填 `position`（现有顺序 = 无 `ORDER BY` 时 SQLite 的返回顺序，
即 rowid 插入序，可用 `SELECT rowid, book_id FROM shelf_book ORDER BY rowid` 逐行赋 0,1,2…）。

**Room version 15 → 16**，同时需要：
- `app/schemas/16.json` 快照 + `AppDatabaseMigrationTest` 用例。

**备份与同步的实测结论（已核实，取代初稿的"待确认"）：**

- **备份已覆盖，无需改动**：`feature/sync/JsonBridge.kt` 的 `LocalExport` 已含
  `shelfBooks: List<ShelfBookEntity>`（`:73`），导出走 `shelfBookDao.getAllActive()`（`:151`），
  恢复时与 `bookTags` / `bookCategories` / `chapterReads` **整表重建**
  （`:242-249`：`clearAll()` 后 `upsertAll()`）。`ShelfBookEntity` 是 `@Serializable` 且 `position`
  是构造参数，因此 `position` **已经**随备份往返。P2 不需要扩展 JsonBridge。
- **局域网同步不涉及**：`data/repository/SyncRepository.kt` 只同步
  `books` / `inspirations` / `reading_progress` / `reading_sessions`
  （import 列表 `:3-8`，各 `syncXxx` 方法），**完全不碰分类学三件套与三张连接表**。
  因此 `shelf_book.position` 不存在"两端各自排序互相覆盖"的合并冲突，
  初稿担心的同步契约缺口**不成立**。书单与分组是纯本机数据。
- **唯一的真实风险因此收敛为**：`position` 全为 0 导致的顺序突变（见下），
  以及迁移回填本身。P2 的风险等级由"中高"下调为"中"，但迁移回填仍不可省。

**因此 P2 建议单独排期**，不要与 P0/P1 混在一个提交里——它引入了本方案唯一的 schema 变更。

### P3 —— 书架编辑模式（对齐范式 3、5）

- 工具栏增加明确的「编辑」入口进入多选（现状是"更多 → 批量选择"，两跳且入口不显眼）。
- 现有批量 dock（`BatchSheet.kt:75` 的 `BatchActionBar`）已有 加入书单 / 设置分类 / 标签 / 下载 / 清缓存 / 删除，
  补两枚：**标记读完** / **标记在读**（批量调用 P0-3 的新方法）。
- 长按行为保持现状（弹 `BookActionSheet`），不改为"长按直接进多选"——
  起点是长按进多选，但本仓库的长按面板承载了就绪态、重新定位文件等独有信息，
  改掉会丢功能。用显式「编辑」按钮替代。

---

## 4. 影响面清单

| 层 | P0 | P1 | P2 |
|----|----|----|----|
| schema | 无 | 无 | **15→16 + position 回填** |
| 同步契约 | 无 | 无 | **无**（SyncRepository 不含分类学表，已核实） |
| 备份（JsonBridge） | 无 | 无 | **无**（`shelfBooks` 含 `position`，已核实） |
| 受影响测试 | `ShelfStatusFilterTest`（必改断言） | `ShelfFilterLogicTest`（分组条交互） | `AppDatabaseMigrationTest`、书单排序测试 |
| 真机验收 | 必做（见 §5） | 必做 | 必做 |

**P0 全部不触碰数据库与同步契约，是"低风险、直接解决用户抱怨"的一期。**

---

## 5. 验收标准

### P0 真机验收（在已连接的真实手机上，先 `adb devices` 核对，禁用 MuMu）

1. **读完态识别**：导入一本测试 TXT → 阅读至 100%（或长按"标记为已读完"）→ 书架「已完成」分组/筛选下**必须能看到它**。
2. **手动标记**：长按一本从未读过的书 → 「标记为已读完」→ 切到「已完成」分组可见；
   且该书**不得**出现在「最近阅读」排序的榜首（验证 `last_read_at` 未被污染，即发现 E 已修复）。
3. **取消读完**：对同一本执行「取消已读完」→ 回到「在读」或「未读」分组，`completed_at` 被清空。
4. **搁置**：长按 → 「搁置本书」→ 出现在「搁置」分组；再次打开该书弹出"恢复阅读状态？"对话框
   （现有逻辑，`ShelfRoute.kt:404`）。
5. **正交标记**：一本已读完且本机可读的书，仍应显示"可离线阅读"胶囊，**但**状态分类是「已完成」
   （验证决定 2 生效）。
6. **筛选条可见性**：书架首屏一眼可见状态分组条，计数与实际结果一致（切换后数出来的本数 = 徽章数）。
7. **新书置顶**：导入 3 本新书 → 按「最近加入」排序下它们在列表最前、带「新」角标；
   点开其中一本 → 角标消失；杀掉进程重开 → 另两本的角标仍在。
8. **导入后定位**：导入完成 snackbar 点「查看」→ 列表滚到第一本新书并高亮；
   开启系统"减少动画"后重复 → 瞬时定位、无高亮动画。
9. **回归**：书单/分类/标签筛选、批量加入书单、删除与撤销、下拉刷新均不回归。

### 门禁

```bash
cd android
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest      # 当前基线 2456 tests
./gradlew :app:lintDebug
./gradlew :app:compileDebugAndroidTestKotlin
```

新增 JVM 单测（P0）：
- `bookStatus` 新口径全矩阵：`FINISHED` 状态、`SHELVED` 优先于 `FINISHED`、进度 99.5 边界、
  本机可读但未读 / 已读完（验证 `READABLE` 不再吃掉状态）。
- `markFinished` 语义：只升进度、保留 `completed_at` 首次值、**不动 `last_read_at`**。
- 「新书」集合的增删与持久化往返（`ShelfPrefs` 新键）。
- 分组条计数与实际筛选结果一致（防"徽章数 ≠ 列表数"）。

---

## 6. 明确不做

| 不做 | 理由 |
|------|------|
| 新增独立"分组"实体 | 与书单语义重叠；代价是 schema + 迁移 + 同步 + 备份四处联动 |
| 自动把读完的书移出书架 | 用户可能想保留；起点/番茄也不这么做，只在分组里归类 |
| 删除 `ShelfStatusFilter.READABLE` 枚举值 | `ShelfSavedViewsStore` 按枚举名持久化，删除会让历史动态视图静默失效 |
| 跨排序模式强制新书置顶 | 破坏用户显式选择的排序语义（如"书名"字典序） |
| 改长按行为为"直接进多选" | 现有长按面板承载就绪态与"重新定位文件"，改掉会丢功能 |
| 把「导入时间」排序改名为「最近加入」时只改一处 | 有四处重复的 `sortLabel`（`SortSheet.kt:49`、`ShelfScreen.kt:508`、`ShelfOrganizerRoute.kt:768`、`ShelfSelectionRoute.kt:360`），漏改会造成同一排序三个名字 |

---

## 7. 建议排期

| 期 | 内容 | 风险 | 依赖 |
|----|------|------|------|
| **P0** | 读完态修正 + 筛选条上首屏 + 长按状态动作 + 新书置顶 | 低（无 schema/同步变更） | 无 |
| **P1** | 分组条（系统分组 + 用户书单）一级导航 | 低 | P0-2 的 rail 容器 |
| **P2** | 书单内拖动排序（激活 `position`） | **中高**（schema 迁移 + 位置回填 + 同步契约缺口） | 需先决议 `shelf_book.position` 的同步合并策略 |
| **P3** | 书架编辑模式 + 批量状态动作 | 低 | P0-3 |

**建议先做 P0**：它以零 schema 代价直接消除用户提出的两个具体痛点，
且其中 P0-1（`bookStatus` 短路）是"读完无法归类"的真正根因——不修它，后面做多少分组 UI 都没用。

---

## 8. P0 实施记录（2026-09-18 已完成）

### 8.1 实际改动清单

| 文件 | 改动 |
|------|------|
| `ui/screen/shelf/ShelfModels.kt` | `bookStatus()` 重写判定顺序（搁置 → 显式已完成 → 进度 99.5 → 在读 → 未读），移除 `isDownloaded()` 短路；新增 `ShelfStatusGroupOptions`、`shelfStatusCounts()`、`shelfLeadingItemCount()`、`shelfGridIndexOfBook()` |
| `ui/screen/shelf/BookGrid.kt` | `StatusRail` 复活并支持计数徽章、改用共享的分组与文案来源；`BookTile` 新增 `isNew` 参数并在网格/列表两态渲染「新」角标 |
| `ui/screen/shelf/ShelfScreen.kt` | 首屏插入状态分组条；网格 state 提升为 `rememberLazyGridState()` 并新增 `focusBookId` 滚动定位 |
| `ui/screen/shelf/ShelfUiState.kt` | 新增 `newBookIds`、`statusCounts` |
| `ui/screen/shelf/ShelfAction.kt` | 新增 `MarkFinished` / `MarkReading` / `ShelveBook` / `BatchMarkFinished` / `BatchMarkReading` |
| `ui/screen/shelf/ShelfRoute.kt` | 接线上述动作；导入完成 snackbar 增加「查看」action 并触发定位；打开书籍时清除「新」角标 |
| `ui/screen/shelf/BookActionSheet.kt` | 新增阅读状态卡片（标记读完 / 取消读完 / 搁置 / 恢复在读），按当前状态二选一显示 |
| `ui/screen/shelf/ShelfSearchRoute.kt` | 搜索页 `bookStatus` 补传 `completion_state`，与书架口径一致 |
| `data/repository/BookRepository.kt` | 新增 `markFinished(bookId)`：进度只升不降、保留首次 `completed_at`、**不刷新 `last_read_at`**、保留阅读位置 |
| `ui/viewmodel/ShelfBookActions.kt` | 新增 `markFinished` / `markReading` / 两个批量版本 |
| `ui/viewmodel/ShelfViewModel.kt` | 暴露 `newBookIds`、`clearNewBookId`；新增四个状态动作方法；导入成功回调登记新书 |
| `ui/viewmodel/BookViewModel.kt` | `markRead` 改走 `markFinished`，消除"同一动作两处行为不同" |
| `data/settings/ShelfPrefs.kt` | 新增 `newBookIds` 流与 `addNewBookIds` / `clearNewBookId` / `clearAllNewBookIds`（持久化，上限 200） |
| `feature/library/ShelfImporter.kt` | 新增 `onBookImported` 回调，仅在 `ImportOutcome.Success` 时触发 |

### 8.2 实施中发现的额外问题（方案初稿未预见）

1. **`BookActionSheet` 被三处复用**（书架 / 首页已读完归档 / 我的阅读）。
   新按钮若用「空默认回调」，后两处会渲染出点了没反应的死按钮。
   改为**可空回调 + 整卡不渲染**：不渲染比渲染一个死按钮诚实。
2. **`BookViewModel.markRead` 与 `Repository.setReadingState` 语义不一致**（发现 E）。
   已统一到新增的 `markFinished`，并让 `BookViewModelTest` 断言"不再调用 `updateReadingProgress`"。
3. **列表态需要可读角标**：网格封面角标位已被「可更新」占用，
   列表态改用标题右侧的文字胶囊，避免与封面上表示「可更新」的圆点语义混淆。
4. **`shelfLeadingItemCount` 必须与 `ShelfContent` 的 item 顺序严格对应**。
   下标错位只表现为"滚到隔壁那本"，真机极难归因，因此抽成纯函数并由单测锁定。

### 8.3 门禁结果

- `compileDebugKotlin` ✓
- `testDebugUnitTest` ✓ **2507 tests / 0 fail / 0 err / 0 skip**（基线 2456 + 新增 51）
- `compileDebugAndroidTestKotlin` ✓
- `lintDebug`：**我改动的文件零告警**。报告中的 3 个 error 位于
  `MainActivity.kt:197/199` 与 `ReaderSubPage.kt:717`，均为**其他 agent 的文件**，与本轮无关。
- `assembleRelease` ✓

新增测试：`ShelfStatusFilterTest`（重写，8 例）、`ShelfStatusCountsTest`（新建，11 例）、
`BookRepositoryTest` 的 `markFinished` 5 例、`ShelfPrefsTest` 的「新书集合」4 例。

### 8.4 真机验证（Redmi `c49ac6cf`，release APK）

| 验收项 | 结果 |
|--------|------|
| 状态分组条在书架首屏常驻 | ✓ 显示「全部 3 / 在读 / 未开始 2 / 已完成 1 / 搁置」 |
| 长按面板出现「标记为已读完」「搁置本书」 | ✓ |
| 标记读完后归入「已完成」分组 | ✓ 徽章变「已完成 1」，书卡出现「完」角标，点进分组可见该书 |
| 「已完成」分组可点选并正确筛选 | ✓（标记前为 0 本，标记后为 1 本） |
| 新导入的书排在列表最前 | ✓ 两本测试 TXT 排在读完的书之前 |
| 「新」角标 | ✓ 两本新书均带角标，读完的书无角标 |
| 打开书籍后角标消除 | ✓ 仅被打开那本的角标消失 |
| 角标跨进程持久化 | ✓ `force-stop` 重开后未读的那本角标仍在 |
| 导入完成 snackbar 带「查看」入口 | ✓「导入完成：成功 2 本，失败 0 本」+「查看」 |
| 分组计数与实际结果一致 | ✓ 全部 3 = 未开始 2 + 已完成 1 |

未在真机覆盖（受 release 包不可 `run-as` 限制）：`last_read_at` 未被污染这一条
由 `BookRepositoryTest` 的 `markFinished does not touch last_read_at` 在 JVM 层锁定。
验证用测试文件与临时产物已从设备与仓库清除。

### 8.5 遗留

- P1（分组条纳入用户书单）、P2（`position` 激活）、P3（编辑模式）仍为待评审方案。
- 工作区中 `shelf/` 下多个文件在本次改动前已有其他 agent 的未提交修改，
  本轮的改动以精确 Edit 叠加，未回退任何既有内容。集成者拆分提交时需按 hunk 处理。
