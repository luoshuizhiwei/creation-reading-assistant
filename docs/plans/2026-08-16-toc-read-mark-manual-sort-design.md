# TOC 章节已读标记 + 书单/分类/标签手动排序 — 设计

> 状态：**已完成**。2026-08-20 已完成并真机验收数据底座：schema v10、
> `chapter_reads` Entity/DAO、9→10 与 1→10 迁移、分类/标签/书单排序 DAO/Repository。
> EPUB/Markdown 首次打开与切章到达写入、删书事务清理、TOC 已读弱化/行尾点、
> 分卷与全书计数、手动清理入口均已实现；TXT 不展示已读状态。分类、标签、书单
> 管理页已接独立排序模式与相邻上移/下移。全量 JVM 1381 项、
> lint/assemble 与 androidTest 编译通过；真实手机定向执行 ChapterRead DAO 13 项、
> TOC 展示与清理交互 5 项、Repository 文件型 Room 跨重开持久化 1 项，合计 19 项
> 0 失败；排序模式 Compose 真机测试 2 项 0 失败。对应路线图 P3.3 已收口。
> 前置：P0 收口完成（涉及 reader 文件与 WIP 重叠区）；与 P3.2 无文件冲突可并行。

## 0. 关键现状发现

1. **已读章无任何持久化数据源**：`reading_progress` 只存「当前一处位置」；
   `reader_page_index` 是排版缓存（预排噪音 + TXT 换目录规则即失效 + fingerprint
   prune 只留 2 个）；`reader_anchor_cache` 只覆盖被跳转过的高亮/书签。
   `recentChapters` 是纯内存态，且只在 TOC 点选时更新（语义是「最近浏览」非「已读」）。
2. **章号变化感知点现成**：`ReaderContentHost` 翻页跨章 → `goToChapter` 是唯一
   章号写入口（UI 层 `chapterIndexState`），在此挂标记即可全覆盖（TOC 点选/翻页/
   进度跳转全部经过它）。
3. **TXT 章号随目录规则漂移**：换识别规则后 `chapter_index` 语义改变，直接存裸
   章号会错位。EPUB/Markdown 章结构稳定。
4. **分类排序半就绪**：`CategoryEntity.sort_order` 列 + DAO `ORDER BY sort_order ASC`
   已存在但零写入（全表默认 0）；`TagEntity`/`ShelfEntity` **无排序列**；
   `ShelfBookEntity.position` 闲置。全链路（FilterSheet / ShelfOrganizerRoute /
   ShelfSelectionRoute）按 DAO 顺序渲染，无二次排序 —— 改 DAO 排序即全链生效。
5. **项目无拖拽先例**；有「上移/下移按钮」重排先例（ReaderRulesSheet 的
   `RuleCommand.ReorderRules`）。

## 1. 目标与非目标

**目标**：① EPUB/Markdown 的 TOC 章节已读标记（持久化、可清理）；
② 分类/标签/书单在管理页手动排序（按钮式），书架全部消费点跟随。

**非目标**：TXT 已读标记（章号漂移，等目录规则身份体系稳定后另议）；长按拖拽
手势（无先例、成本高）；书单内书的排序（`shelf_book.position`，二期另立）；
已读标记进同步/备份（纯本地体验态）。

## 2. 分片设计

### 片 1 — 已读标记数据层（schema v10）

新表（与片 3 的排序列合并进同一个 v10 迁移包）：

```sql
CREATE TABLE IF NOT EXISTS chapter_reads (
  book_id TEXT NOT NULL,
  chapter_index INTEGER NOT NULL,
  read_at TEXT NOT NULL,
  PRIMARY KEY(book_id, chapter_index)
);
CREATE INDEX IF NOT EXISTS index_chapter_reads_book_id ON chapter_reads(book_id);
```

- DAO：`upsert`、`observeBookIds`→`observe(bookId): Flow<List<Int>>`（章号列表）、
  `clearForBook(bookId)`、`countForBook`。挂到既有 BookDao 或独立 `ChapterReadDao`
  （推荐独立，`AppDatabase` v10 + `DatabaseModule.MIGRATION_9_10` +
  `AppDatabaseMigrationTest` 补 9→10 与 1→10 全链）。
- 写入点：`ReaderViewModel` 在成功打开 EPUB/Markdown 时记录初始章，并在处理
  `ReaderAction.LoadChapter` 成功后（bid + chapterIndex）upsert 一条 `chapter_reads`
  （幂等，已存在则只刷 `read_at`）。后者是 `goToChapter` 的必经下游，覆盖切章入口；
  加上初始打开写入后，首次进入也不会漏记。
- 语义：**到达即已读**（不做读完判定 —— 翻到最后一句的精确判定成本高且口径
  争议大；legado 同样是到访语义）。
- 清理：`BookRepository.deleteBook` 事务内 `clearForBook`；TOC 头部已有
  「清除已读标记」溢出项及确认对话框，不影响阅读进度、书签或笔记。

### 片 2 — TOC 展示

- `ReaderTocEntry` 加 `isRead: Boolean = false`；`ReaderSheetHost` 的 TOC 分支把
  `observeReadChapters(bid)` 快照传入 `readerTocEntries`（仅 EPUB/Markdown 传实际值，TXT 恒 false）。
- `TocRow`：已读章标题色降为 `onSurfaceVariant`（弱化）+ 行尾小圆点；当前章样式
  不变（优先级最高）。
- 卷头行显示「已读 x/y」（`entries` 分组内统计）；TOC 头部 pill 旁加全书
  「已读 n/总」。
- `currentListPosition` 滚动定位逻辑不受影响（行数不变）。

> 2026-08-20 已实施并验收：已读 Flow 进入 `ReaderRouteUiState`，清空后计数即时归零；
> 自动滚动只监听当前章和稳定章节索引，不因已读状态变化重置目录浏览位置。

### 片 3 — 分类/标签/书单手动排序（同 v10 迁移包）

- 迁移：`ALTER TABLE tags ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0`；
  `ALTER TABLE shelves ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0`；
  `chapter_reads` 建表。为避免旧数据全部同序号导致相邻交换无效，迁移按
  `created_at, id` 为标签/书单生成唯一稠密序号；分类按既有
  `sort_order, created_at, id` 保序归一化。
- DAO：Tag/Shelf/Category 三个 DAO 的查询 ORDER BY 统一为
  `sort_order ASC, created_at ASC`（created_at 兜底稳定序）；新增
  `updateSortOrder(id, sort_order)`。
- Repository：`createTag/createShelf/createCategory` 时 `sort_order = max+1`
  （查询一次 max）；新增 `moveTag/moveShelf/moveCategory(id, delta: Int)` ——
  与相邻项交换 sort_order（避免全表重排）。
- UI：`LibrarySubPage` 使用独立「调整顺序」模式，避免普通行同时出现编辑、删除、
  展开、上移、下移五个操作；排序模式显示两位位置编号与「上移/下移」IconButton，
  首项上移、末项下移禁用。点击后 ViewModel 调 `move*`，列表顺序由 stateIn Flow 自动刷新。
- 消费面零改动（FilterSheet/Organizer/Selection 均按传入顺序渲染）。

> 2026-08-20 已实施并验收：独立排序模式由用户确认采用；普通管理操作与排序操作
> 互斥展示，减少窄屏拥挤及误删风险。三类六方向 ViewModel 转发已有 JVM 回归，
> 模式切换、命名操作和首尾边界已有真实手机 Compose 回归。

## 3. 验收

1. EPUB 翻到第 N 章后退出重进，TOC 第 N 章带已读样式；卷头/全书计数正确。
2. 「清除已读标记」后全部还原；删书后 `chapter_reads` 无残留（迁移测试 + 真机）。
3. TXT 的 TOC 无已读标记（显示层恒 false，不产生误导）。
4. 管理页上移/下移立即生效；书架 FilterSheet / Organizer / Selection 顺序同步；
   新建分类/标签/书单排到末尾。
5. Room 9→10 迁移 + 1→10 全链测试通过；升级安装（真机）不丢已有数据。
6. `testDebugUnitTest` + `lintDebug` 全绿；新增 ChapterReadDao 单测与
   move* 交换逻辑单测。

## 4. 风险与对策

- `LoadChapter` 高频（翻页跨章每次都发）→ upsert 幂等且仅跨章时发生；Room 写入
  量可忽略（一本书章数有限）。
- 大书章数多（数千章 EPUB）→ `observe` 返回 Int 列表，TOC 渲染本就全量构建
  entries，无额外复杂度。
- v10 迁移与并行开发冲突 → 迁移文件单独小提交先行合入（schema 变更是串行资源）。
