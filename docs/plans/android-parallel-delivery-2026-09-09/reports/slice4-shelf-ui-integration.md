# 切片 4：Shelf UI 收口（书籍详情 / 导入面板 / 筛选 / 书架共用组件）交付报告

> 日期：2026-09-13
> 范围：`ui/screen/shelf/` 内 11 个所有权文件（BookDetail 系列 5、导入系列 4、FilterSheet、ShelfSharedComponents）
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`（共享脏工作区，多 agent 并行）
> 本轮**未** stage / commit / push / reset / checkout / clean / stash；**未**触碰任何禁改文件（见 §6 取证）。
> 依赖阅读：`AGENTS.md`、`docs/handoff/current.md`、`docs/plans/2026-09-13-android-r3-slice1-integration-manifest.md`、`reports/codex-r4.md`（ReaderCorrection R4 报告）。

---

## 1. 文件职责映射（HEAD → 当前）

### 1.1 BookDetail 拆分（1 → 5）

| 文件 | 行数 | 职责 |
|---|---:|---|
| `BookDetailSheet.kt` | 1294 → **160** | 仅编排：`GlassModalBottomSheet` 壳 + `BoxWithConstraints` + `adaptivePageMetrics` 内边距 + 11 个区块的顺序与间距 |
| `BookDetailHeaderSection.kt`（新） | **445** | `DetailIslandCard` / `SectionTitle` / `InfoRow`（本包共用小件）+ `BookDetailHeaderSection`（元数据/封面/编辑）+ `BookDetailCtaSection`（继续阅读 / 下载） |
| `BookDetailStatsSection.kt`（新） | **429** | `BookDetailStatsSection`（4 列统计微岛）+ `BookDetailSessionsSection`（阅读记录，默认折叠 3 条） |
| `BookDetailNotesSection.kt`（新） | **142** | `ExpandableRow` + `BookDetailNotesSection`（书签/笔记/高亮）+ `BookDetailInspirationsSection` |
| `BookDetailManagementSection.kt`（新） | **227** | `ChipRow` + `BookDetailFileInfoSection` / `ShelvesSection` / `CategoriesSection` / `TagsSection` / `DeleteSection` |

### 1.2 导入面板拆分（1 → 4）

| 文件 | 行数 | 职责 |
|---|---:|---|
| `ImportSheets.kt` | 914 → **69** | 仅共享小件：`FormatPurple/Blue/Amber/Green`、`formatColor()`、`FormatCapsule`、`StatusMicroBadge` |
| `ImportHistorySheet.kt`（新） | **393** | 导入历史 + 本次队列 + 批次总结卡（进度/徽章/截断与不可读目录告警/重试失败项/知道了） |
| `ImportSourceSheet.kt`（新） | **160** | 导入来源选择（我的书籍目录 / 从系统选择文件 / 从电脑导入）+ `ImportSourceRow` |
| `DesktopBooksSheet.kt`（新） | **390** | 从电脑下载：探测列表、空态、下载行 `DesktopBookRowItem` |

### 1.3 其它

| 文件 | 行数 | 职责变化 |
|---|---:|---|
| `FilterSheet.kt` | 294 → **387** | 新增 L1「动态视图（保存的筛选）」区（保存/套用/删除）；`FilterChipRow` / `MultiFilterChipRow` / `FilterCapsuleChip` 保留 |
| `ShelfSharedComponents.kt` | 31 → **32** | 仅 +1 常量 `SHELF_LIBRARY_ROUTE`；`BackButton` / `OrganizerDivider` / `statusLabel` 不变 |

---

## 2. 行为保持清单（验证方法与结论）

### 2.1 文案零丢失（机器比对）

| 页面 | HEAD 中文串 | 当前中文串 | 丢失 | 新增 |
|---|---:|---:|---:|---|
| BookDetail（5 文件合计） | 61 | 61 | **0** | **0** |
| Import（4 文件合计） | 40 | 42 | 6 | 8 |

- BookDetail：**逐字一致**，拆分未改任何提示、空态、按钮文案。
- Import 的 6 条「丢失」全部是 R3 的来源目录口径更新，**不是功能入口丢失**：
  旧「扫描文件夹 / 一次选择一本或多本本地文件 / 包含子文件夹，自动跳过其他非支持文件」→
  新「我的书籍目录 / 从系统选择文件 / 在 App 内浏览、智能识别和批量导入」；另新增「从电脑导入」「获取桌面端已同步的书籍」。
- 结论：**导入入口数量与语义未减**（3 个来源入口：目录 / 系统文件 / 电脑），措辞更贴近 R3 的 SAF 树浏览实现。

### 2.2 结构等价（机器比对 HEAD vs 当前）

对 BookDetail 五文件合计与 Import 四文件合计分别统计以下指纹，**全部相等**：

`@Composable` 函数集合、`GlassModalBottomSheet` 数、`GlassAlertDialog`/`AlertDialog` 数、`contentDescription` 数、`remember`/`rememberSaveable`/`LaunchedEffect` 数、`LazyColumn/LazyRow` 数、`items(` 数、`clickable` 数、`IconButton` 数、`InputChip` 数、`SelectablePill` 数、`verticalScroll` 数。

`@Composable` 集合「HEAD-only = ∅ / NEW = ∅」→ **没有函数在拆分中丢失，也没有新造未接线函数**。

### 2.3 引用完整性

- `BookDetailCtaSection` / `SessionsSection` / `InspirationsSection` / `FileInfoSection` / `ShelvesSection` / `CategoriesSection` / `TagsSection` / `DeleteSection` 八个区块**全部有定义且被 `BookDetailSheet` 调用**，无孤儿、无跨文件循环。
- `ImportSourceRow` / `FormatCapsule` / `StatusMicroBadge` / `FilterChipRow` / `MultiFilterChipRow` / `FilterCapsuleChip` 调用点齐全。
- `BookDetailSheet` 唯一调用点 `ShelfRoute.kt:322`（**未修改**，签名未变）；`FilterSheet` 唯一调用点 `ShelfRoute.kt:369`，`savedViews` / `onSaveCurrent` / `onApplySaved` / `onDeleteSaved` **均已接线** → 「动态视图」不是空壳 UI。
- `ImportHistorySheet` / `ImportSourceSheet` / `DesktopBooksSheet` 由 `ShelfScreen` / `ShelfImportRoute` 调用（**未修改**），参数签名未变。

### 2.4 确认流 / 错误态 / 空态 / 返回行为

| 项 | 状态 |
|---|---|
| 删除确认 + 撤销 | 走 `requestDelete` → `feature/library/deletion`（`DeletionCopy` + `DeletionUndoBar`），本切片未改链路；`BookDetailDeleteSection` 只负责入口与自述（见 §3 R1） |
| 移除书单/分类/标签 | `onRemoveShelf` / `onRemoveCategory` / `onRemoveTag` 回调与 Snackbar 由 `ShelfRoute` 提供，未改 |
| 空态 | BookDetail：书单/分类/标签/笔记/灵感/阅读记录空态齐全（逐项核对）；Import：无记录空态 + 批次总结并存；Desktop：空态 + （本轮新增）失败态 |
| 返回行为 | 全部 sheet 仍为 `GlassModalBottomSheet(onDismissRequest = onDismiss)`，`BookDetailSheet` 的 `dismissDetailDestination` 未动 |
| 展开/折叠 | 阅读记录默认 3 条 + 展开全部（有界）；书签/笔记/高亮/灵感为 `ExpandableRow`（展开态本轮加了稳定 key） |

---

## 3. 本轮实际改动（6 文件）

| 编号 | 文件 | 改动 | 类别 |
|---|---|---|---|
| **R1** | `BookDetailManagementSection.kt` | 删除入口副标题：`同时移除本机正文、进度、书签和笔记` → `移除书籍资料、进度、书签和笔记；不删除本地正文文件` | **语义纠错（要求 4）** |
| **R2** | `BookDetailNotesSection.kt` | `ExpandableRow` 增加必填 `key`，展开态改 `remember(key)`；4 个调用点用实体主键 `bookmark-${id}` / `note-${id}` / `highlight-${id}` / `insp-${id}` | 状态归位（要求 5） |
| **R3** | `ImportHistorySheet.kt` | 内容区补 `verticalScroll`；补 `sheetMaxWidth = contentMaxWidth`；水平内边距改布局令牌 | 长列表可达性 + inset 统一（要求 2/5） |
| **R4** | `DesktopBooksSheet.kt` | ① `listBooks()` 包 `runCatching` + 独立失败态（中性文案 + 重新探测）；② 列表区补 `verticalScroll`；③ 补 `sheetMaxWidth` + 令牌内边距；④ 两处与可见文字重复的图标 `contentDescription` 置 `null` | 错误态 + 长列表 + a11y（要求 1/2/3/5） |
| **R5** | `ImportSourceSheet.kt` | 补 `sheetMaxWidth` + 令牌内边距 | inset 统一（要求 2） |
| **R6** | `FilterSheet.kt` | 补 `sheetMaxWidth` + 令牌内边距 | inset 统一（要求 2） |

### R1 的事实依据（要求 4 的核心）

```
BookRepository.applyDeletionLocked(snapshot)   // DELETE_BOOK 唯一写入路径
  ├─ bookDao.softDelete(id, now)
  ├─ progressDao / sessionDao / noteDao / highlightDao  → 软删（deleted_at）
  ├─ bookContentDao.upsert(content.copy(reader_preview = null, epub_json = null))
  ├─ bookFileDao.upsert(file.copy(deleted_at = now))
  └─ bookTagDao/bookCategoryDao/shelfBookDao.clearByBook + chapterReadDao.clearForBook
  → 全程在 Room 事务内，**没有任何 File/deleteRecursively 调用**

BookRepository.clearBookCache(id)              // 「移除正文」（REMOVE_CONTENT）路径
  └─ deleteBookContentFiles(id)
       ├─ File(filesDir, "books/$id").deleteRecursively()
       └─ File(filesDir, "books/epub/$id.epub").delete()
```

- 因此「删除整本资料」**不释放磁盘上的内部正文副本**，只有「移除正文」才释放。
- 与权威文案口径一致：`strings_deletion.xml` 的 `deletion_scope_delete_book_body` 只列「书籍资料、阅读进度、阅读记录、笔记、高亮，以及分类、标签、书单关联和已读章节」，**不含正文**；`DeletionCopy.kt` 头注释明确警告「移除正文只丢本地正文缓存、删除整本资料才连阅读数据与关联一起移除，混着说会直接误导用户对数据留存的判断」。
- 原副标题正属于被警告的「混着说」。**只改描述，未改任何私有副本生命周期策略。**

### 要求 3（导入历史 / 来源文案中性）的逐条核查

| 文案 | 判定 |
|---|---|
| 「文件较多，已按安全上限停止扫描」 | ✅ 准确（对应 `batch.truncated`），未暗示网络错误 |
| 「N 个子文件夹无法读取」 | ✅ 中性（只陈述不可读），未断言原因 |
| 「共解析 N 本，成功入库 M 本」「成功/重复/跳过/失败/未处理」 | ✅ 与批次真实计数一一对应 |
| 「还没有导入记录」 | ✅ 空态，无联网暗示 |
| 「从电脑导入 / 局域网快速同步正文」 | ✅ 与实际 LAN 同步路径一致，未暗示删除用户外部文件 |
| 「暂无可下载书籍 / 当前没有可用书籍 / 可检查电脑端同步目录」 | ✅ **仅在探测成功后列表为空时显示**（R4 引入独立失败态后成立） |
| 「未获取到电脑端书籍列表。／可确认电脑端同步目录后再重新探测。」 | ✅ 新增失败态：只陈述事实，不断言失败原因（不写「网络错误」） |

全部文案**无一处**提及删除用户外部文件；R3 manifest §3.7 的「删书不删来源文件」未被反向描述。

---

## 4. 视觉风险

| 级别 | 项 | 说明 |
|---|---|---|
| 中 | **平板上 sheet 宽度内边距仍不齐** | `BookDetailSheet` 用 `adaptivePageMetrics(...).horizontalPadding`（窄 16 / 宽 24）；本轮把其余 4 个 sheet 统一为 `pageHorizontal`（16）。手机上已一致；平板上 BookDetail=24、其余=16。要彻底一致需给每个 sheet 套 `BoxWithConstraints`（结构性改动，且属**可见视觉变化**），本轮未做。 |
| 中 | **发丝边框令牌漂移（既有，跨切片）** | `ComponentSpec.hairlineBorderWidth = 0.6dp` / `hairlineAlpha = 0.35f`；而 `DetailIslandCard` 写死 `0.8dp / 0.40f`，`ImportHistorySheet` 用 `0.5dp`、`FormatCapsule` 用 `0.5dp / 0.35f`、`DesktopBooksRowItem` 用 `0.6/0.8dp`。统一会**改变可见边框粗细**，需视觉 owner 一次性收口 + 真机验收，本轮未动。 |
| 低 | `ImportHistorySheet` 由「不可滚」变「可滚」 | 短内容时 sheet 高度不变；内容超过一屏时 sheet 占满可用高度（这是让屏外记录可达的必要代价）。 |
| 低 | `DesktopBooksSheet` 新增失败态 | 新版式复用空态微岛卡片结构（圆角 16dp / 发丝 0.8dp / `outlineVariant`），与同 sheet 空态一致。 |
| 低 | 触摸区域偏小（既有） | `BookDetail*` chip 尾部移除按钮 `IconButton(Modifier.size(18.dp))`；`FilterCapsuleChip` 高约 30dp。均低于 48dp 建议值。改动会影响既有 chip 视觉，**本轮未改**，列为待办。 |
| 低 | `CompactChip` 语义角色缺失（既有） | `FilterCapsuleChip` 用 `Surface + Modifier.clickable`，无 `role = Role.RadioButton`/`Checkbox`；TalkBack 只能读为普通可点项。本轮未改（需与视觉/无障碍 owner 一起定）。 |

---

## 5. 测试结果

命令（`android/`，`--no-daemon`；执行前后均确认无并发源码写入、构建产物静默）：

```bash
# 1) 定向 JVM
:app:testDebugUnitTest --no-daemon --console=plain \
  --tests "com.creationreadingassistant.ui.screen.shelf.*" \
  --tests "com.creationreadingassistant.ui.screen.ShelfStatusFilterTest" \
  --tests "com.creationreadingassistant.data.settings.ImportHistoryStoreTest" \
  --tests "com.creationreadingassistant.data.settings.ShelfPrefsTest" \
  --tests "com.creationreadingassistant.feature.library.deletion.*"
# 2) androidTest 编译门禁
:app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

结果：

| 套件 | tests | fail | err | skip |
|---|---:|---:|---:|---:|
| DeletionUndoCredentialTest | 16 | 0 | 0 | 0 |
| BookDeletionRestoreTest | 13 | 0 | 0 | 0 |
| BookDeletionCoordinatorRestoreTest | 12 | 0 | 0 | 0 |
| ShelfBookDeletionDelegationTest | 7 | 0 | 0 | 0 |
| ShelfFilterLogicTest | 5 | 0 | 0 | 0 |
| ImportHistoryStoreTest | 3 | 0 | 0 | 0 |
| ShelfPrefsTest | 3 | 0 | 0 | 0 |
| ShelfAdaptiveLayoutPolicyTest | 3 | 0 | 0 | 0 |
| BookDetailStatsPolicyTest | 2 | 0 | 0 | 0 |
| ShelfImportSummaryPolicyTest | 2 | 0 | 0 | 0 |
| ShelfStatusFilterTest | 1 | 0 | 0 | 0 |
| **合计** | **11 套件** | **67** | **0** | **0** | **0** |

- `:app:testDebugUnitTest` → `BUILD SUCCESSFUL`（最终源码态下 `compileDebugKotlin` 与 `testDebugUnitTest` 均为 UP-TO-DATE ⇒ 结论对应当前源码）
- `:app:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`（`kspDebugAndroidTestKotlin` + 编译实际执行）
- `git diff --check -- <全部 11 个所有权文件>` → **EXIT=0**

**未运行**（按约束）：全量 `testDebugUnitTest`、`lintDebug`、`assembleDebug`；任何 instrumented **执行**；APK 安装。

> 过程记录（并发纪律）：第一次尝试时另一 agent 正在跑**全量** `:app:testDebugUnitTest`，我的过滤运行在 `transformDebugUnitTestClassesWithAsm` 报 `New files were found. This might happen because a process is still writing to the target directory.` 而失败；等其产物静默（约 1 分钟后 0 写入）后重跑成功。**该失败与源码无关，不是代码缺陷。**

---

## 6. 未覆盖项

1. **本切片 6 个 sheet 无任何自动化测试引用**（`BookDetailSheet` 全文 0 个 `testTag`），长列表滚动、失败态、空态、返回行为只能真机验证。
2. 平板 / 折叠屏下的 sheet 宽度与内边距（见 §4 中风险）。
3. TalkBack 实际朗读顺序与重复朗读（本轮只做了「图标与可见文字重复」的静态修正）。
4. 从电脑导入的真实失败路径（需要电脑端不同步 / 关闭服务的场景）。
5. 100 条满额导入历史的滚动与性能表现（仅在真机可构造）。

---

## 7. SEAM REQUEST（需越界，故未自行修改）

- **S1｜`ui/screen/shelf/BookActionSheet.kt:232`（非本切片所有权）**：同样写「同时移除本机正文和阅读数据」，与 R1 是同一处事实错误（删除不删磁盘正文）。请归属 owner 用同一口径修正（建议「移除书籍资料与阅读数据；不删除本地正文文件」）。本切片**未**跨文件修改。
- **S2｜`feature/library/deletion/`（`DeletionCopy.kt` + `strings_deletion.xml`）**：删除三档口径的唯一权威。本轮只让 UI 自述向它对齐，**未**改任何文案资源。另：删除后磁盘私有副本保留、仅由「移除正文」回收，属**独立产品决策**（是否需要回收站 / 保留期 / 空间统计），本切片不介入。
- **S3｜`ui/theme/ComponentSpec.kt`**：发丝令牌（`0.6dp / 0.35f`）与 shelf 弹层普遍硬编码（`0.5–0.8dp / 0.30–0.40f`）不一致。需视觉 owner 统一；跨切片，且属可见视觉变化，需真机验收。
- **S4｜`ui/layout/LayoutTokens.kt`**：目前无「sheet 专用水平内边距」令牌，本轮统一复用 `pageHorizontal`(16dp)。若产品希望弹层内边距与页面内边距分离，建议新增 `sheetHorizontal` 并回改 5 个 sheet。
- **S5｜共享 schema 顺序（来自 R3 manifest §4，仅提醒）**：`reader_text_corrections`（v12→v13，ReaderCorrection）与 `library_source_refs`（v13→v14，R3）必须由同一集成者在单一数据库版本序列中处理；本切片零 Room 改动，不受影响，但提交排序时请勿拆分。

---

## 8. 给 WorkBuddy-QA 的验收提示词

> 可直接投喂真机验收会话。**允许**安装 APK、导入中性测试文件（「测试 TXT」「测试 EPUB」）、在隔离目录读写。**禁止**删除或修改设备上既有真实书籍 / 笔记 / 灵感 / 已授权目录中的文件。

```text
你是 WorkBuddy 真机验收 agent。请对 Android 原生 App（creation-reading-assistant，android/ 主线）
的「书架 UI 收口」切片做独立验收。只用真机（先 adb devices 核对 serial，所有命令显式
adb -s <serial>；不使用 MuMu 模拟器）。长测如需常亮先记录 stay_on_while_plugged_in 原值并在
结束时恢复。测试数据一律用「测试 TXT」「测试 EPUB」等中性名称。

背景：本切片只涉及 ui/screen/shelf/ 的 11 个文件（BookDetail 系列、导入系列、FilterSheet、
ShelfSharedComponents）。不含 ShelfImporter / ShelfViewModel / ShelfRoute / ShelfImportRoute /
LibraryBrowser* / Room schema / Reader / 首页 / Profile / 灵感 / 搜索 / 同步 / 安装脚本。
请独立判断，不要以本切片自述的编译与单测结果替代真机证据。

需要独立验证的 8 组行为（逐项给出 PASS / FAIL 与证据）：

A. 书籍详情：操作与确认流不丢
   1) 书架点任一本书进详情：应依次出现 元数据卡 / 继续阅读(或下载) / 阅读统计 / 阅读记录 /
      书签与笔记 / 灵感 / 文件信息 / 所在书单 / 所属分类 / 书籍标签 / 删除本书 —— 一一对照，
      不得少任何一块。
   2) 逐项操作：改标题作者简介、换封面（相册）、文字封面、重置封面、加入/移出书单、加/移分类、
      加/移标签，操作后均有反馈且详情刷新。
   3) 阅读记录：默认只列最近 3 条，点「展开」后显示全部；记录为空时有空态文案。
   4) 书签/笔记/高亮/灵感：各点开一条展开再收起，展开态必须跟着「那一条」走。
      重点：先展开第 2 条 -> 删除第 1 条（或在别处新增一条）-> 回到详情，
      确认展开态没有错位到相邻条目上。
   5) 系统返回 / 点遮罩 / 下拉：均应关闭详情并回到书架原位置，不丢筛选与滚动位置。

B. 删除语义（重点：文案必须与事实一致）
   1) 详情底部「删除本书」的自述应显示「移除书籍资料、进度、书签和笔记；不删除本地正文文件」。
   2) 点它应弹出统一确认框（标题「删除整本资料？」），正文只列资料与阅读数据、**不得**声称
      删除「本机正文」，并给出撤销秒数；确认后书架移除该书，Undo 条出现。
   3) 点撤销：书应恢复（含进度/笔记/标签/分类/书单关联/已读章节）；若提示「正文缓存需重新
      打开书籍后重建」，属预期。
   4) 对照组：用「移除正文」确认框应明确只释放本地正文缓存并提示不可从本地还原；
      两者说法必须能区分开，不能被说成同一件事。

C. 导入来源与导入历史（重点：文案中性）
   1) 书架 → 导入：来源面板应有三个入口（我的书籍目录 / 从系统选择文件 / 从电脑导入），
      文案不得暗示会删除你的外部文件。
   2) 导入历史面板：制造一次含「重复」「跳过」「失败」的导入，核对徽章数字与历史条目一致。
   3) 空态：没有任何导入记录时显示「还没有导入记录」。
   4) 长列表可达性（本切片新修）：连续导入足量文件使历史条目明显超过一屏（可用同一批
      「测试 TXT」重复操作；不必到 100 条），然后**向上滑动面板**：最下方的历史条目必须能
      滚到并点开，不得被裁掉且无法触达。

D. 从电脑导入：失败态与空态必须区分（本切片新修）
   1) 电脑端不同步 / 服务不可用 / 不在同一网络时打开「从电脑导入」：不得卡在转圈不动；
      应显示「未获取到电脑端书籍列表。」+「可确认电脑端同步目录后再重新探测。」+ 重试按钮，
      且**不得**出现「暂无可下载书籍」这类把探测失败说成「电脑端没有书」的文案。
   2) 电脑端正常但同步目录确实为空：应显示空态「暂无可下载书籍」。
   3) 点「重新探测连接」：恢复后列表应出现在同一面板内，且**长列表可滚动**到底部条目。

E. 筛选与动态视图
   1) 筛选面板：格式（EPUB/TXT/Markdown）、书单、分类、标签（多选 + 「全部」）三组均可选，
      选中态有勾与高亮；点「全部」清空该组。
   2) 动态视图：有筛选生效时输入名称点「保存」应成功；保存后点该视图应一键套用；点「删除」
      应删除。无筛选生效时「保存」应为不可用态。
   3) 选择项很多时筛选面板整体可上下滚动，最后一组（标签）可选到。

F. 视觉一致性
   1) 手机竖屏：分别打开 书籍详情 / 导入来源 / 导入历史 / 从电脑导入 / 筛选 / 继续阅读管理，
      顶部圆角与拖拽把手样式应一致（同一 `sheetShape` + 同一 handle）；
      左右内边距视觉一致（不应出现某个面板明显更窄或更宽）。
   2) 平板或折叠屏展开（若有设备）：各底部面板宽度应一致且居中。
      ⚠️ 已知残留：书籍详情面板在宽屏会使用更宽的内边距，其余面板不会——若观察到该差异，
      请如实记录为「已知残留」，不计为回归。
   3) 删除入口与「移除正文」入口的危险色语义：删除为红，移除正文应有警示但不得与删除同色同措辞。

G. 无障碍（TalkBack）
   1) 打开 TalkBack：底部面板标题、来源三入口、删除入口、批量按钮应能正确朗读且不出现同一
      标签连续朗读两遍（如同一按钮读成「刷新 刷新」「下载 下载」）。
   2) 书签/笔记/高亮的可点条目应被朗读为可点项；展开箭头为装饰性图标，不应单独朗读。

H. 稳定性
   1) 上述流程中不得出现 FATAL / ANR；不得出现面板卡死、转圈不复位。
   2) 反复开关详情面板 10 次、开关筛选面板 10 次、开关导入历史 5 次，无崩溃、无内存异常增长。

交付：逐项 PASS/FAIL 表格 + 证据（截图/录屏路径、关键 logcat 行）。必须报告任何 FATAL / ANR、
任何与期望不符的行为、以及你**无法**验证的条目（不要用推测填表）。
明确声明：本次验收是否修改了设备上真实书籍 / 笔记 / 灵感数据，以及是否修改了已授权目录中的
用户文件（均应为「否」）。
```

---

## 9. 提交前提示（给集成者）

- 本切片全部改动**未提交**；`BookDetailSheet.kt` 与 `ImportSheets.kt` 的大幅删减与 7 个新文件必须在**同一提交**中出现，否则 `ShelfRoute` 引用的 `BookDetailSheet` / `FilterSheet` 与 `ShelfScreen` 引用的三个导入 sheet 会缺失。
- `FilterSheet.kt` 的 387 行中含「动态视图」新功能，与 R3 无关，可随本切片提交；但 `ShelfRoute.kt` 已在工作区被改（非本切片所有权）以传入 `savedViews` 等参数，提交时请确认该调用点同批入库。
- CRLF 提示：`ImportSheets.kt`、`ShelfSharedComponents.kt` 在工作区带 CRLF（`.gitattributes` = `* text=auto eol=lf`），提交时不得夹带行尾转换。
- 禁改区证据（mtime，均早于本轮开工 15:47）：`ShelfImporter.kt` 09-12 23:40、`ShelfViewModel.kt` 09-12 00:50、`ShelfRoute.kt` 09-12 21:36、`ShelfImportRoute.kt` 09-12 21:36、`LibraryBrowserRoute.kt` / `LibraryBrowserComponents.kt` 09-12 23:45、`AppDatabase.kt` 09-12 23:36、`AppNavigation.kt` 09-12 21:35 —— 本切片零触碰。
