# S04 MuMu「真正来源刷新」核验报告

> 状态：**PASS（入口 B「真正来源刷新」已在隔离测试书上端到端执行并留下原始证据）**
> 本轮 = 执行轮，宿主本地时间 **2026-09-27 21:00–21:23（UTC+8）**；设备 = MuMu 模拟器 `emulator-5556`（**非真机**）。
> 影响范围：**只有隔离测试书 `epub_-5hwnnt` 被改动**；正式包、正式书库、真实手机均未触碰。
> 上一轮（补交轮）的 **NOT RUN** 结论保留在 §10 作为历史，**未被本轮证据覆盖或改写**。
> 报告内出现的书名/文件名一律为中性隔离样本名（「测试 EPUB S04」「Test-EPUB-S04.epub」「sample-0xx」等合成样本），不含真实藏书信息。

---

## 0. 结论先行

1. **入口 B 全流程跑通**：书架下拉刷新 → `refreshSourceUpdates` 检出 1 本 → 「可更新」角标 → 确认弹窗（**严格只列 1 本：测试 EPUB S04**）→ 点击「重新同步」→ `ShelfViewModel.confirmSourceRefresh` → `ShelfImporter.refreshFromSource` → `refreshSourceBaseline` 推进基线 → 异步 `indexRefreshCoordinator.request` 索引重建完成。
2. **判定依据是数据，不是界面提示**（强停一致快照 + 本机 SQLite 离线全表比对）：
   - `library_source_refs`（测试书行）：`size` **1101 → 1700**、`last_modified` **1790499387000 → 1790501895000**、`content_hash` **734d17ff81e5151fe2096a47523a5ad7 → cafaef499165761c7574f2e3226ee02c**、`last_seen_at` **1790499581804 → 1790514220459**。
   - `refreshSourceBaseline` 是 `library_source_refs` 的**唯一**写入方，且**只在 `refreshFromSource` 内调用**（`ShelfImporter.kt:283`）；该行发生推进 ⇒ 入口 B 确已执行。
3. **标签能清掉的闭环成立**：基线与内部正文副本哈希现已**收敛到同一值**（`cafaef49…`，MD5 口径），随后再次下拉刷新得到 `书架已是最新，没有检测到源文件更新`，且「可更新」角标消失（`grid_no_badge.png`、`ui11.xml`）。
4. **只动测试书**：前后快照全表比对，`books` 29 行中仅 1 行变化、`library_source_refs` 29 行中仅 1 行变化、`reading_progress` **15 行零变化**（书集合与逐字段均一致）；非测试书的 `search_terms` 计数**无一变化**。
5. **异步索引是「已完成」而非「已请求」**：测试书 `search_index_coverage` 由 0 行 → 2 行、`search_terms` 由 0 → 127，`indexed_at = 1790514220524`，比基线写入的 `last_seen_at = 1790514220459` **晚 65 ms**，且随后 30 s 稳定窗口与约 15 min 后的复检读数完全一致。
6. 本轮**未修改产品源码、未 commit/push/reset/checkout、未 `pm clear`**；设备密度/旋转**未做任何改动**（无「改后待恢复」项）。

---

## 1. 两个入口的合同区分（本任务的核心口径）

| | 入口 A：重新定位文件 | 入口 B：真正来源刷新（本任务） |
|---|---|---|
| 触发 | 书架书卡长按 → 「重新定位文件」 | 书架**下拉刷新** → 「可更新」角标 → **确认弹窗** → 确认 |
| 代码 | `ShelfImporter.repairFile` → `repairFileTyped` → `reselectEpub`/`reselectPlainText` | `ShelfViewModel.confirmSourceRefresh` → `ShelfImporter.refreshFromSource`（内部复用 `repairFileTyped`） |
| 内部正文 | 替换 | 替换 |
| `books` / `book_files` | 更新 | 更新 |
| **`library_source_refs` 基线** | **不写** | **推进** `size`/`last_modified`/`content_hash`（`ShelfImporter.kt:283,353`） |
| 搜索索引 | 无额外动作 | 替换成功后**额外**异步 `indexRefreshCoordinator.request(bookId)`（`ShelfImporter.kt:287`） |
| 上一轮证据 | 有（V1→V5，见 §10） | 上一轮 **无**；**本轮已补齐** |

> 口径提醒：既有 V1–V5 证据（`E:\creation-reading-assistant\codex-s04-mumu-20260927\`）**仍然只属于入口 A**，本报告**不**把它当作本任务结果；本任务结果全部来自本轮新证据目录（§8）。

---

## 2. 本轮执行步骤逐项

| # | 步骤 | 结果 | 关键证据 |
|---|---|---|---|
| 1 | `adb devices` 核对序列号与占用 | PASS：仅 `emulator-5556` 一台，无其他任务占用 | §7 |
| 2 | 保存操作前状态（主库 + WAL + SHM 强停一致快照） | PASS | `snapshots\before\manifest.json` |
| 3 | 书架下拉触发来源检查 | PASS（`ShelfViewModel.refresh` → `checkUpdatedSources`） | `ui3.xml` |
| 4 | 「可更新」角标 | PASS：**唯一 1 枚**，位于测试书卡片 | `ui5.xml`、`grid_badge.png` |
| 5 | 确认弹窗 + **范围门禁** | PASS：弹窗文案明示 **1 本**且点名 **测试 EPUB S04**，无其他书 | `ui3.xml`、`ui6.xml`、`dialog_confirm.png`、`shelf_pull.png` |
| 6 | 点击「重新同步」 | PASS（ADB `input tap` 退出码 0） | `ui7.xml` |
| 7 | `refreshFromSource` → `refreshSourceBaseline` | PASS：基线 4 字段推进 | §4 |
| 8 | 异步索引重建 | PASS：**已完成** | §6 |
| 9 | 操作后状态快照（同口径） | PASS | `snapshots\after\manifest.json` |
| 10 | 其余书/进度是否被选中 | PASS：未被选中 | §5 |
| 11 | 恢复本轮改过的界面设置 | PASS：`ui12.xml` 与操作前 `ui2.xml` **逐字节一致** | §7 |

**关键时间线（宿主本地时间 UTC+8）**

| 时刻 | 事件 |
|---|---|
| 21:00:25 | 操作前基线界面落定（`shelf_before.png` / `ui2.xml`） |
| 21:01:36 | 第 1 次下拉 → 确认弹窗出现（`shelf_pull.png` / `ui3.xml`） |
| 21:02:05 | 用「取消」关闭弹窗，回落到书架（`ui4.xml` 与 `ui2.xml` 逐字节一致；`refreshSourceUpdates` 语义：取消**不清**角标） |
| 21:03:16–21:03:17 | 切到网格视图（角标只在网格视图渲染，见 §3.2）→ `ui5.xml` / `grid_badge.png` |
| 21:03:30 | 第 2 次下拉 → 确认弹窗（`ui6.xml` / `dialog_confirm.png`） |
| 21:03:38 | 记录确认前的来源文件与私有副本 stat（`device\source-epub-stat-prefix.txt`） |
| **21:03:44.537** | **点击「重新同步」** |
| 21:03:40.371 / 21:03:40.459 / 21:03:40.524（UTC+8 本地） | 基线写入 `books.updated_at` / `library_source_refs.last_seen_at` / `search_index_coverage.indexed_at`（见 §6） |
| 21:03:48 | 结果 Snackbar「已同步 1 本书的源文件更新」（`ui7.xml`） |
| 21:07:32 | 索引状态观测落盘（`logs\index-poll.jsonl`，此时已完成） |
| 21:08:03 | 视图切回列表（`ui8.xml` 与 `ui2.xml` 逐字节一致） |
| 21:18:57 / 21:20:49 | 强停一致快照 `snapshots\after\` 写入完成 |
| 21:22:39 | 复检下拉 → 「书架已是最新，没有检测到源文件更新」、无弹窗、无角标（`ui11.xml` / `grid_no_badge.png`） |
| 21:22:49 | 终态界面复核（`ui12.xml` 与 `ui2.xml` 逐字节一致） |

---

## 3. 界面证据（UI 文本逐字 + 截图）

### 3.1 确认弹窗（范围门禁的唯一依据）

- 标题：`同步源文件更新？`
- 正文：`检测到 1 本书（测试 EPUB S04）的源文件已被修改，是否重新同步正文？同步不会主动清除已有阅读记录；但正文更新后章节会重新划分，阅读位置也可能随之变化。`
- 按钮：`取消`、`重新同步`
- 判读：**书本数 = 1**，点名书 = 隔离测试书（`books.title = 测试 EPUB S04`，`id = epub_-5hwnnt`，`original_file_name = Test-EPUB-S04.epub`）。**不含其他书、目标唯一且可证**，故门禁通过、允许点击确认。
- 两次触发（21:01:36 / 21:03:30）文案**完全一致**（`ui3.xml` 与 `ui6.xml` SHA-256 相同：`70FE02341DC604ED2E9443F91AAD96156E8778708E59FF57261E25411810126A`）。
- 截图：`E:\creation-reading-assistant\codex-s04-truerefresh-20260927\ui\dialog_confirm.png`、`…\ui\shelf_pull.png`

### 3.2 「可更新」角标 —— 附一条真实 UI 缺口

- 操作前书架处于**列表视图**；`ui2.xml`/`ui4.xml` 中**查不到** `可更新` 文本。
- 原因是代码事实：**角标只在网格视图渲染**——`BookGrid.kt:414-429`（`text = "可更新"`）位于 `if (viewMode == ShelfViewMode.GRID)` 分支内；列表分支只渲染「新」胶囊（`BookGrid.kt:575-588`，`text = "新"`）。
- 切到网格视图后立即出现且**仅 1 枚**：`可更新 [372,416][429,444]`，落在 3×N 网格的**第 1 个卡片** `[168,403][449,925]`（即按「最近导入」排序的第一本 = 测试书）。
- 该角标在**取消弹窗后仍然存在**（与 `ShelfViewModel.cancelSourceRefresh` 的注释一致：「关闭确认状态，保持书卡角标不变」），因此可在网格视图下稳定取证。
- 截图：`…\ui\grid_badge.png`（有角标） / `…\ui\grid_no_badge.png`（刷新后无角标）
- **旁证结论（非阻断项，供产品侧评估）**：在列表视图下用户看不到「可更新」角标（仍能从下拉刷新拿到确认弹窗），属显示口径不一致，不影响 S04 功能正确性。

### 3.3 结果与复检提示

| 场景 | 文案 | 来源 |
|---|---|---|
| 确认后 | `已同步 1 本书的源文件更新` | `ui7.xml`（Snackbar `[189,1837][478,1876]`），截图 `…\ui\after_confirm.png` |
| 再次下拉 | `书架已是最新，没有检测到源文件更新` | `ui11.xml`，截图 `…\ui\grid_no_badge.png` |

> 上述两条均为**成功路径**提示；本轮**未出现**任何失败提示（无「N 本刷新失败」「正文未在本机」等）。

---

## 4. 隔离测试书对账（本轮 before → after）

测试书：`epub_-5hwnnt`｜书名 `测试 EPUB S04`｜原始文件名 `Test-EPUB-S04.epub`｜来源文档 `primary:Download/Test-EPUB-S04.epub`（`com.android.externalstorage.documents`）。
隔离来源文件：`/sdcard/Download/Test-EPUB-S04.epub`，**1700 字节**，SHA-256 `639f193924b5d2921c83bd41d900e2d644705e9e6e96d1151bb7d8d8f1e6e44b`（**操作前=操作后，本轮从未写入该文件**）。

| 事实源 | 操作前 | 操作后 | 结论 |
|---|---|---|---|
| **`library_source_refs.size`** | `1101` | **`1700`** | ✅ 推进，且等于来源文件真实字节数 |
| **`library_source_refs.last_modified`** | `1790499387000` | **`1790501895000`** | ✅ 推进（provider 新观测） |
| **`library_source_refs.content_hash`** | `734d17ff81e5151fe2096a47523a5ad7` | **`cafaef499165761c7574f2e3226ee02c`** | ✅ 推进，且**等于** `books.content_hash`（两源收敛） |
| **`library_source_refs.last_seen_at`** | `1790499581804` | **`1790514220459`** | ✅ 写成本次观测时刻（UTC `2026-09-27T13:03:40.459Z`） |
| `library_source_refs.availability` | `available` | `available` | 不变 |
| `library_source_refs.candidate_fingerprint` | `NULL` | `NULL` | 不变（小文件策略：不落候选指纹） |
| `books.size` / `books.content_hash` | `1700` / `cafaef49…` | `1700` / `cafaef49…` | 不变（见下方「局限」） |
| `books.updated_at` | `2026-09-27T09:48:29.775Z` | **`2026-09-27T13:03:40.371Z`** | ✅ 推进（本轮唯一变化的 books 字段） |
| `books.local_content_path` | `…/files/books/epub/epub_-5hwnnt.epub` | 同左 | 不变 |
| `book_files.updated_at` | `2026-09-27T09:43:02.337Z` | **`2026-09-27T13:03:40.377Z`** | ✅ 推进（`book_files` 中唯一变化行） |
| 私有正文副本 `files/books/epub/epub_-5hwnnt.epub` | 1700 字节，SHA-256 `639f1939…e44b` | 1700 字节，SHA-256 `639f1939…e44b` | 不变 |
| 残留文件 `.backup`/`.repairing`/`.importing` | 三者均不存在 | 三者均不存在 | ✅ 无替换残留 |
| `reading_progress`（测试书） | `progress_percent=2.8526148796081543`、`completion_state=finished`、`current_location_json={"chapter":0,"offset":486,"locator_v2":{"v":2,"offset":486,"ci":0,"co":486}}`、`updated_at=2026-09-27T09:49:05.595Z` | **逐字段完全相同** | ✅ 阅读记录未被清除（与弹窗承诺一致） |
| `reader_page_index`（全表） | 64 行 | 64 行，**逐行一致** | 不变（正文 `content_key` 未变） |

**哈希口径说明**：`library_source_refs.content_hash` 与 `books.content_hash` 都是**内部副本的全量 MD5**（`ShelfImporter.computeFileHash` → `MessageDigest.getInstance("MD5")`，`ShelfImporter.kt:438-442`），故为 32 位十六进制；而证据里的 `.epub` 文件哈希是 **SHA-256**（64 位十六进制）。两者不可直接比较，但本轮**两处 MD5 已收敛为同一值**，即「基线 ↔ 正文事实源」一致。

**局限（必须写明，避免过度解读）**：上一轮的**入口 A** 已经把 1700 字节的来源内容复制进了内部副本（操作前 `books.size` 已是 1700、私有文件 SHA-256 已与来源文件相同），因此本轮刷新是**「同字节重拷贝 + 基线首次推进」**。本轮因此**不能**用来证明「正文内容会随来源变化而更新」这一条，只能证明入口 B 的**控制流、基线推进、角标清除与异步索引重建**。要覆盖「内容确实变了的刷新」，需要先把来源文件改成不同字节再跑一次（本轮未做，属未覆盖项，见 §9）。

---

## 5. 其余书籍与进度未被本轮选中（全表比对）

比对方法：对 `snapshots\before` 与 `snapshots\after` 两份**强停一致拷贝**（各含 `-wal`/`-shm`，本机 SQLite 只读回放）逐表、逐主键比对；脚本 `compare_snapshots.py`（结果 `snapshots\compare.json`）。

| 表 | 行数 前→后 | 变化行 | 变化对象 |
|---|---|---|---|
| `books` | 29 → 29 | **1** | 仅 `epub_-5hwnnt`（仅 `updated_at`） |
| `library_source_refs` | 29 → 29 | **1** | 仅 `epub_-5hwnnt` |
| `reading_progress` | 15 → 15 | **0** | 无（书集合相同、逐字段相同） |
| `book_files` | 29 → 29 | **1** | 仅 `epub_-5hwnnt`（仅 `updated_at`） |
| `search_index_coverage` | 42 → 44 | +2 行 | 仅 `epub_-5hwnnt`（新增 `original`/`display` 两行） |
| `search_terms` | 5712817 → 5712944 | +127 | 仅 `epub_-5hwnnt`（0 → 127，其他 20 本计数**逐一不变**，见 `snapshots\terms-by-book.json`） |
| `search_index_state` | 1 → 1 | **0** | 逐字段相同（`1|2|fd9581af-…|0|1790357454377`） |
| `reader_page_index` | 64 → 64 | **0** | 逐行相同 |
| `chapter_reads` / `reading_sessions` / `book_content` / 其余表 | 各自不变 | **0** | 逐行相同 |
| 表集合 | — | — | 前后一致（无新增/消失表） |

机器判读（`snapshots\compare.json`）：

```
only_test_book_changed.books               = true
only_test_book_changed.reading_progress    = true
only_test_book_changed.library_source_refs = true
progress_changed_books                     = []      # 15 本书 0 本变化
progress_count                             = 15 / 15
books.changed[0].pk                        = epub_-5hwnnt
library_source_refs.changed[0].pk          = epub_-5hwnnt
search_index_coverage.added                = [[epub_-5hwnnt, display], [epub_-5hwnnt, original]]
```

结论：本轮**只改动了隔离测试书 `epub_-5hwnnt`**；其余 28 本书、15 条阅读进度、其余索引与正文表**均未被选中**。

---

## 6. 异步索引重建：请求 vs 完成（不把「已请求」写成「已完成」）

**请求侧（代码事实）**：`refreshFromSource` 在基线写入成功后调用 `indexRefreshCoordinator.request(bookId)`（`ShelfImporter.kt:287`），该调用**不等待**、立即返回（`SearchIndexBookRefreshCoordinator.request` 只 `applicationScope.launch { reindexBookById(bookId) }`）。

**完成侧（数据事实）**：

| 观测项 | 操作前 | 操作后 | 说明 |
|---|---|---|---|
| `search_index_coverage`（测试书） | **0 行** | **2 行** | 新增 `('original','full',1/1, tokenizer_version=2, indexed_at=1790514220524)` 与 `('display','not_applicable',0/1, indexed_at=0, reason=no_replace_rules)` |
| `search_terms`（测试书） | **0** | **127** | 逐词条入库 |
| `search_terms`（全表） | 5712817 | 5712944 | +127，且**仅测试书**的计数变化 |
| `search_index_state` | `1|2|fd9581af-…|0|1790357454377` | 同左 | 单本重建**不**改写全局状态行（既有行为） |

**完成时间证据**：`indexed_at (1790514220524)` − `last_seen_at (1790514220459)` = **65 ms**，即索引重建在基线写入后约 65 ms 内落库完成；`indexed_at` 与 `books.updated_at (13:03:40.371Z)` 同处一次刷新事务窗口。

**等待上限与稳定性**：
- 观测脚本 `poll_index.py` 设定**等待上限 120 s**（首次尝试因宿主侧 SQL 引号问题 rc=1 无数据，属取证脚本缺陷、非应用故障；修正后重跑窗口 30 s）。
- 修正后第一个成功读数（21:07:32 落盘）即显示 `coverage=2 / total=44 / terms=5712944`，且**30 s 窗口内 6 次读数完全一致**（`logs\index-poll.jsonl`）。
- 约 15 min 后（终态复核）再次直读活动库，读数与 `snapshots\after` **完全一致**：`coverage=44`、`terms=5712944`、测试书 `original|full|1|1|1790514220524`（`logs\live-final.txt`，各查询 rc=0）。

结论：本轮异步索引 = **已请求且已完成并持久化**，不存在「只请求未完成」的悬置状态。

---

## 7. 设备、环境与设置恢复

| 项 | 值 |
|---|---|
| 测试序列号 | `emulator-5556`（`product:e3q model:SM_S9280 device:e3q transport_id:1`），**本轮唯一设备**，前后各一次 `adb devices -l` 复核一致 |
| 机型 / 系统 | `SM-S9280`，Android **12**（SDK **32**），`x86_64`，MuMu 模拟器（**非真机**） |
| 屏幕 | `Physical size: 1080x1920` |
| **密度（density）** | 操作前 **280** → 操作后 **280**（`wm density`）。**本轮未改密度**，无待恢复项 |
| **旋转（rotation）** | `user_rotation=0`（竖屏）、`accelerometer_rotation=1`。**本轮未改旋转**，无待恢复项 |
| `stay_on_while_plugged_in` | `null`（本轮未开启） |
| **截图实际布局方向** | **全部 7 张 PNG 均为竖屏 `1080x1920`**（程序化读取 PNG IHDR 校对，非目测） |
| 宿主 APK（qahost） | `com.creationreadingassistant.qahost`，versionName `0.5.1` / versionCode `3`，base.apk **51,699,420 字节**，`lastUpdateTime=2026-09-27 04:41:53`，**SHA-256 `a4669f8532055656be6341444f29eaf3a972678533e5ceb351ff34e40021a638`** |
| 正式包 `com.creationreadingassistant` | 本轮未安装、未触碰、未启动 |
| 真机 | **未连接**（全程 `adb devices` 只有 `emulator-5556`） |

**本轮唯一改过的界面设置**：书架视图模式（操作前=列表，取角标时临时切网格）。已在 21:08:03 切回列表，并复核 `ui12.xml` 与操作前 `ui2.xml` **逐字节一致**（同为 SHA-256 `5D4BE4F9A4BA33002AA80082315F9F7432BF45E8040ADCC7B9906F63B7EA53E2`）。

**ADB 命令退出码**：所有交互命令（`input tap`/`input swipe`）与读取命令（`wm`/`settings get`/`sha256sum`/`pm path`）退出码均为 **0**；唯一非 0 为取证脚本 `run-as … sqlite3 "SELECT count(*)"` 的 **rc=1**（宿主侧未预引号，被设备 `mksh` 报 `syntax error: unexpected '('`），修正传参后 rc=0 —— 属**工具链问题**，与应用行为无关。

**取证脚本自身的两次修正（如实记录）**：
1. `snap_state.py` 首次直连 `adb exec-out` 读 1.1 GB 主库时因内存缓冲超时 → 改为 `subprocess.Popen` 流式落盘 + 边写边算 SHA-256。
2. 一次 APK 哈希命令因 PowerShell 未转义 `$(…)`，本地 `pm` 被当作命令执行，得到的是**空输入哈希** `e3b0c442…b855`；该值**已作废并覆盖重算**，报告采用的重算值见上表（`device\apk-sha.txt` 现存内容仅为重算正确值）。

---

## 8. 证据清单（E 盘绝对路径）

**本轮根目录：`E:\creation-reading-assistant\codex-s04-truerefresh-20260927\`**

### 8.1 强停一致快照（§4/§5/§6 数据来源）

| 路径 | 内容 |
|---|---|
| `…\snapshots\before\creation_reading_assistant_native`（+`-wal`、`-shm`）与 `…\before\manifest.json` | 操作前主库与 WAL/SHM 一致拷贝 |
| `…\snapshots\after\creation_reading_assistant_native`（+`-wal`、`-shm`）与 `…\after\manifest.json` | 操作后（刷新+索引完成后）一致拷贝 |
| `…\snapshots\compare.json` | 全表逐行比对结果（§5 机器判读） |
| `…\snapshots\terms-by-book.json` | `search_terms` 按书计数前后对照 |

主库 / WAL 的 SHA-256（与采集时 `manifest.json` **逐字节一致**）：

| 快照 | 文件 | 字节 | SHA-256 |
|---|---|---|---|
| before | db | 1138147328 | `F0E5B9E4D6121872DB47C2277B6D5E2C89079DB67B9A518BA1A76A42D9BF077F` |
| before | `-wal` | 432632 | `D10D039420100DA0931576B8FE06887C3795D28DE186528DB65AF825BBC8F368` |
| after | db | 1138282496 | `BE2AAB24430F931BCC24243CE3DF85A690816C5A8BA643D0172E7756AF14C722` |
| after | `-wal` | 774592 | `D31827F7193A61A7E0453DCA35010DD789C1A5A71F3BA0F649A655912D685EFF` |

> 说明：`-shm` 是 WAL 的**临时共享内存索引**。离线打开拷贝做比对时 SQLite 会重建它，故 `-shm` 落盘后哈希与 `manifest.json` 采集时不同（before `14bb3167…`→`17B50D43…`，after `45cdc967…`→`1AA979DE…`）。**主库与 WAL 未变**，比对结论不受影响；`-shm` 不承载持久数据。

### 8.2 截图与 UI 层级（全部 1080×1920 竖屏）

| 文件 | SHA-256 | 用途 |
|---|---|---|
| `…\ui\shelf_before.png` | `F4985C984F486049B12AADAC108D16FA39AC73DC689620818DC366ED668DEB87` | 操作前书架（列表视图） |
| `…\ui\shelf_pull.png` | `ACCA8638AE879CF4E933BFD146877CE093E24DF5033ABD0A54677C9806532FBE` | 第 1 次下拉后的确认弹窗 |
| `…\ui\shelf_badge.png` | `7D9A64B849237773344611BFE769DD22307B64A0D1A7C08722A34328DF3FC000` | 取消后列表视图（角标不渲染，见 §3.2） |
| `…\ui\grid_badge.png` | `DD7C90A8F016BDEA8B928F256EFC61FBB89162064EFD0FE875B70A14A0A41F94` | **网格视图：唯一「可更新」角标** |
| `…\ui\dialog_confirm.png` | `DC17871BF704B70B970C6BD6712A2C1032FD1AACB519553A697C42C0590E67D4` | 第 2 次下拉的确认弹窗（点击前） |
| `…\ui\after_confirm.png` | `EC7FB2DBF27E36612DBFA2739FB1881A4C51F99BA9CB0F241270BA38166DE96D` | 成功 Snackbar |
| `…\ui\grid_no_badge.png` | `AB87B82A052ABFE448DF6249F05D82CAFE4055E61DC36648EE7B3F10ABAB9418` | 复检：无角标 + 「已是最新」 |
| `…\ui\ui1.xml` | `A0DA4197F7A3B55B35894B705A73B7629588A3ED6A1E4EC97887CBF38ADFCAC5` | 启动后首页 |
| `…\ui\ui2.xml` | `5D4BE4F9A4BA33002AA80082315F9F7432BF45E8040ADCC7B9906F63B7EA53E2` | **操作前书架基准**（与 ui4/ui8/ui12 一致） |
| `…\ui\ui3.xml` | `70FE02341DC604ED2E9443F91AAD96156E8778708E59FF57261E25411810126A` | 确认弹窗（第 1 次） |
| `…\ui\ui4.xml` | `5D4BE4F9…E2` | 取消后（= 操作前） |
| `…\ui\ui5.xml` | `83A692A1F982B1E8A1AD486EBDAC09F07363A5F9C03866A8C5F1B4933AFDD896` | **角标所在网格视图**（含 `可更新 [372,416][429,444]`） |
| `…\ui\ui6.xml` | `70FE02341DC604ED2E9443F91AAD96156E8778708E59FF57261E25411810126A` | 确认弹窗（第 2 次，与 ui3 逐字节一致） |
| `…\ui\ui7.xml` | `9F3F8F5BA50304EE78AA884B0A8570D3CFB0D28CE7CE6F9E6C22910BC97505DC` | 确认后：成功 Snackbar + 无角标 |
| `…\ui\ui8.xml` | `5D4BE4F9…E2` | 视图切回列表后 |
| `…\ui\ui9.xml` | `A0DA4197…C5` | 强制重启后首页 |
| `…\ui\ui10.xml` | `5D4BE4F9…E2` | 进入书架（列表视图） |
| `…\ui\ui11.xml` | `38439256DE725211C78555A33F3366C81E5109DDE4A2FCE620FA6FEB6DB266E9` | 复检：无角标 + 「书架已是最新…」 |
| `…\ui\ui12.xml` | `5D4BE4F9…E2` | 终态复核（= 操作前） |

### 8.3 原始日志与读数

| 文件 | 说明 |
|---|---|
| `…\logs\logcat-dialog.txt` | 弹窗阶段 `logcat -d`（82992 B，SHA-256 `68F74BE3430B2C343DB0EA72D1D92B71ACE36D3AAEB7EE5E2370D7DD1BECF669`） |
| `…\logs\logcat-confirm.txt` | 确认阶段 `logcat -d`（85951 B，`7E79D154592BCEAFC1B906A3E23466FEAFE3B9051855AD3590E54F0006913088`） |
| `…\logs\index-poll.jsonl` / `…\logs\index-poll-stdout.txt` | 索引状态轮询原始记录（30 s 窗口 6 次读数） |
| `…\logs\live-final.txt` | 终态活动库直读（含各查询 rc） |
| `…\logs\compare-stdout.txt` / `…\logs\delta-stdout.txt` / `…\logs\terms-scope-stdout.txt` | 比对脚本原始输出 |
| `…\logs\snapshot-after-stdout.txt` | 操作后快照脚本输出 |
| `…\logs\epoch-convert.txt` | 毫秒时间戳 → UTC 换算 |
| `…\logs\evidence-inventory.txt` | **本目录全部文件的字节数与 SHA-256 清单** |
| `…\device\apk-sha.txt` | 安装包 SHA-256 + `ls -l` |
| `…\device\source-epub-stat-prefix.txt` | 来源文件与私有副本操作前 stat/哈希（含操作后来源文件复算） |
| `…\docs-archive\2026-09-27-s04-mumu-true-source-refresh.PRE-RUN.md` | **改写前的旧版报告全文存档**（14357 B，NOT RUN 版） |

### 8.4 复现脚本（本轮新增）

`…\snap_state.py`（强停一致快照 + 关键行导出）、`…\poll_index.py`（索引轮询）、`…\compare_snapshots.py`（全表逐行比对）、`…\terms_scope_check.py`（按书词条计数）、`…\show_delta.py`（字段级差异打印）、`…\schema_probe.py`（表结构）。各脚本 SHA-256 见 `logs\evidence-inventory.txt`。

### 8.5 旁证（仓库内）

- `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\qa\2026-09\2026-09-27-s04-mumu-qahost-ui-reconciliation.md`（其自身标注为入口 A）
- `docs\qa\2026-09\2026-09-27-s04-mumu-qahost-instrumentation.md`
- 入口 A 的历史证据目录：`E:\creation-reading-assistant\codex-s04-mumu-20260927\`（V1–V5，**不属于本任务结果**）

---

## 9. 边界、未覆盖项与声明

**已覆盖（本轮）**：入口 B 全链路（下拉 → 检出 → 角标 → 弹窗 → 范围门禁 → 确认 → `confirmSourceRefresh` → `refreshFromSource` → 基线推进 → 异步索引重建完成 → 角标清除/复检「已是最新」）；隔离测试书 6 类事实源（`books`、`library_source_refs`、私有正文文件、`reading_progress`、`book_files`、索引四表）前后对账；其余 28 本书与 15 条进度未被选中的全表证明；密度/旋转/视图设置记录与恢复；APK、设备、来源文件、截图方向的记录。

**未覆盖 / 局限（不得外推）**：
1. **正文内容未发生字节变化**：来源与内部副本在操作前已同字节（入口 A 遗留），故本轮不证明「来源内容改变 → 正文更新」，只证明控制流与基线/索引。要覆盖需先改变来源文件字节再刷新。
2. 小文件分支（1.7 KB / 单章 EPUB，≤32 MiB → 全量 MD5）已覆盖；**>32 MiB 大文件策略**（`content_hash=NULL` + `candidate_fingerprint`）、**进程中断/替换残留**场景未覆盖。
3. `search_index_coverage.display` 行为 `not_applicable / reason=no_replace_rules`（未配置替换规则），属预期，未验证「有替换规则」时的 display 索引。
4. 未对全部 28 张表做**字段级**穷举解读（已做逐行比对，但 `search_terms` 因 571 万行仅按书计数与总量比对）。
5. 列表视图下「可更新」角标不渲染（`BookGrid.kt:414` 仅在 `GRID` 分支）——本轮如实记录为 UI 一致性问题，**未修改任何产品源码**。
6. 正式包 `com.creationreadingassistant`、正式书库、真实手机**完全未触碰**。

**声明**：本轮未修改产品源码；未 commit / push / reset / checkout；未 `pm clear`；未安装/卸载；未执行任何越权写操作（对设备的写入仅限：对**隔离测试书**触发一次来源刷新、一次视图模式切换并已还原）。工作树既有改动（用户资产）未被触碰。

---

## 10. 历史：上一轮补交轮的 NOT RUN 结论（保留，未被本轮改写）

旧版报告（全文存档见 `…\docs-archive\2026-09-27-s04-mumu-true-source-refresh.PRE-RUN.md`）结论为 **NOT RUN / BLOCKED**，判定依据：

- 当时 `library_source_refs`（测试书行）在 V1→V5 全程**逐字段不变**（`size=1101`、`content_hash=734d17ff…`、`last_modified=1790499387000`），而 `books.size` 已到 `1700`、`books.content_hash` 已变为 `cafaef49…`。
- 由于 `refreshSourceBaseline` 是 `library_source_refs` 的唯一写入方且只在 `refreshFromSource` 内调用，基线未变 ⇒ 入口 B 当时从未执行。当时全部 E 盘证据（`E:\creation-reading-assistant\codex-s04-mumu-20260927\`）对应**入口 A**。

本轮正是接着这个未完成点执行：**刷新前的 `library_source_refs` 仍是 1101 / `734d17ff…`**（本轮 before 快照实测），本轮执行后推进为 1700 / `cafaef49…`（本轮 after 快照实测）。即：旧报告的阻塞项已在本轮解除，但**旧报告的 V1–V5 证据仍只属入口 A**，两轮结论各自成立、互不替代。

---

## 11. MuMu 设备所有权

- 本轮为**唯一 MuMu 所有者**，全程对 `emulator-5556` 使用显式 `adb -s emulator-5556`。
- 设备**仍在运行且可连接**（`adb devices -l` 终态仍为单台 `emulator-5556`）；本轮**未**执行 `adb disconnect`、未关闭模拟器、未重装、未因旧端点 `127.0.0.1:16416` 失效而做任何重连/重装动作。
- 应用界面已还原到操作前状态（`ui12.xml` ≡ `ui2.xml`）；视图模式、密度、旋转均与操作前一致。
- **结论：设备尚未交还（仍处占用/可连接状态），等总指挥复核；如需交还，由集成者在确认无其他在用任务后执行断开。**

---

## 12. 独立复核（报告定稿后由本轮执行者重跑校验）

为避免「报告自证」，在报告写完后对**落盘证据与设备终态**各重跑一遍校验（只读），逐条比对报告正文：

| 被校验的结论 | 复核方式 | 结果 |
|---|---|---|
| §4 基线推进（`size 1101→1700`、`content_hash 734d17ff…→cafaef49…`、`last_seen_at→1790514220459`） | 重读 `snapshots\compare.json` + **活动库直读**（`run-as … sqlite3 -readonly`，rc=0） | ✅ 与报告逐字一致 |
| §5 只改测试书 | 重读 `compare.json`（`only_test_book_changed.*=true`、`progress_changed_books=[]`、`progress 15/15`）+ 重读 `terms-by-book.json`（`changed=["epub_-5hwnnt"]`） | ✅ 一致 |
| §6 异步索引已完成（`+2` 覆盖行、`+127` 词条、`indexed_at−last_seen_at=65 ms`） | 重读 `live-final.txt` + 活动库直读两行覆盖记录 | ✅ 一致 |
| §8.1 快照摘要 | 对 `snapshots\before|after` 的 **db 与 -wal 重新计算 SHA-256**，与 `manifest.json` 记录值比对 | ✅ 4/4 逐字节一致；`-shm` 仍按文档所述因离线打开被重建（现为 `17B50D43…` / `1AA979DE…`，与 §8.1 说明相符） |
| §8.2 界面基准 | 重新计算 `ui2.xml`/`ui4.xml`/`ui8.xml`/`ui12.xml` 与 `ui3.xml`/`ui6.xml` | ✅ 两组各自逐字节相同（`5D4BE4F9…E2` / `70FE0234…0126A`） |
| §7 设备与设置 | `adb devices -l`、`wm size/density`、`settings get system user_rotation/accelerometer_rotation`、`dumpsys package` | ✅ 仅 `emulator-5556`；`1080x1920`、`280`、`0`、`1`；versionName `0.5.1` / versionCode `3` / `lastUpdateTime=2026-09-27 04:41:53` |
| §4 来源文件未被本轮写入 | `sha256sum /sdcard/Download/Test-EPUB-S04.epub` | ✅ 仍为 `639f1939…e6e44b`，1700 字节 |
| §8.2/§8.3 证据文件齐备 | 递归列出 E 盘目录（45 个文件） | ✅ §8 引用的文件全部存在（7 PNG / 12 UI dump / 22 快照与日志 / 4 设备记录与脚本等） |

复核结论：**报告正文与原始证据一致，无夸大或缺失项；§12 本身不改变 §0–§11 的 PASS 判定。**

> 注：本段追加前报告全文 SHA-256 = `B7F9974B0A2C55FC40A3CCA490217D7FBE32FA8FC8273C8081B30835FCE62550`（29384 字节）；追加本段后该值随之变化，仅作版本参照。

## 13. Codex 总指挥复核（2026-09-27）

- 独立回读本轮 E 盘 `compare.json`：`books` 与 `library_source_refs` 各仅测试书 1 行变化，`reading_progress` 15→15 且无变化，索引 coverage +2、terms +127；两份确认弹窗 XML 哈希相同，文案只列 1 本测试 EPUB，确认后及复检文案可回读。抽查截图哈希与报告一致；设备当前为 280 dpi、旋转 0/自动旋转 1。接受 **MuMu qahost 上真正来源刷新控制流、基线推进及该书索引落库** 的限定 PASS。
- 目视 `dialog_confirm.png`：画面虽为 1080×1920 竖屏，但出现左侧导航和三列书架，是**平板式布局**。本轮不构成用户要求的 MuMu **手机布局视觉验收**；后续设备轮须临时调到手机布局，采证后恢复 280 dpi。
- 来源与私有正文在本轮操作前已同字节，故本轮未证明“新内容字节被刷新进正文”。其他书的 `search_terms` 只比较了按书计数，没有逐词条相等证明；其余 DB 行与进度的限制按上文快照结论。
- 报告中宿主点击时刻 21:03:44 与数据库时间 21:03:40 相差约 4 秒；可能是宿主/设备时钟偏差，现有证据不足以据此判定精确耗时。设备仍连接不等于执行者仍占用；下一轮开始前重新核对设备所有权即可。
