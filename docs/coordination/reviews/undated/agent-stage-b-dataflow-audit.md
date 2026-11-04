# 窗口 C — stage-B 只读调查：S03 搜索索引 / S04 来源替换 / S14 备份数据流

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`
- 本轮性质：只读。未修改任何代码/账本/看板/handoff/记忆库；未运行 Gradle、ADB；未访问远端；未碰数据库；未 commit/stage/push。
- 证据等级：**A** = 当前代码直接可读到的调用链/字面量；**B** = 由 A 推断的行为结论（未执行）；**C** = 历史报告转述，本轮无法复核。文中「**静态确认**」= A 级，即调用链/字面量在当前代码里可指认；凡需并发时序、设备行为或真实 provider 才能证明的后果，一律只写「风险仍存在 / 待运行复现」，不写成运行结论。
- rev2 修订（仅改事实与归因，数据流主体结论不变）：① C01 —— 前一版误称两 API「工作树与 HEAD 均无匹配」，实为**相对 HEAD 的未提交删除、可从 HEAD 精确恢复**；② R1 —— S04-2（未调用逐书重建）与 S03-3（已调用但同步等待）**不是同一缺陷**，只共享异步调度入口；③ 文案改动由「零风险」改为「低风险且仍需测试」；④ 各条「仍存在」按 A/B 重新标注。
- 先读输入：`2026-09-22-current-task-ledger.md`、`docs/plans/android/2026-09-22-android-recovery-convergence-plan.md`、`2026-09-20-android-s03-search-review.md`、`2026-09-20-android-s04-source-update-review.md`。

## 1. S03 搜索索引：三项旧问题的调用链仍成立（运行后果逐条分级）

### S03-1 全量重建与在跑扫描的游标竞争 — 缺陷路径静态确认（P1，A）；**竞态后果属静态推断，本轮无运行复现**（B）
当前链路：搜索页胶囊 / 「存储·日志与诊断」按钮 → `SearchRepository.triggerFullRebuildNow`（`SearchIndexRepository.kt:381-394`）→ `purgeOrphanIndexRows()` → `invalidateSweepForFullRebuild()`（:356-365，**只**把 `last_scanned_book_id` 置 null）→ `scheduler.triggerNow(force = true)`（`SearchIndexScheduler.kt:51-77`，`ExistingWorkPolicy.REPLACE`）。
为什么仍判定竞态风险存在（以下 1-3 全部为**静态推断**，本轮未运行、无 WorkManager 日志或真机复现）：
1. 没有「重建代次」令牌 —— 游标是一个可被任意写入方覆盖的标量，`invalidateSweepForFullRebuild` 无法表达「从第 N 代起作废」；
2. 被 REPLACE 掉的那次尝试仍可能在被取消前继续写：`ensureIndexedIncremental` 每完成一本书就 `stateDao.set(last_scanned_book_id = book.id)`（:817-873 三处），于是**新作业的游标复位会被旧作业的下一本覆盖**，重建从中间继续而非从头；
3. `ensureIndexedIncremental:271` 的 `if (_progress.value.isRunning) return PROGRESSED` 意味着旧扫描未让出前，新作业被短路成 PROGRESSED → `Result.retry()` 空转（:385-392 的注释记录了这正是历史上「永不重建」的形态）。
本轮已收敛的一半：`force=true` 缺失导致 KEEP 静默丢弃的问题已修（`SearchIndexScheduler.kt:57-62` KDoc 明写该危害）。
最小修复：`search_index_state` 增 `build_generation` 列（或复用 `built_at` 作单调令牌），`invalidateSweepForFullRebuild` 先自增再复位游标；`processChunked` 每本落锚点前比较代次，代次不符即 `return` 让出。行为测试：① 旧扫描持有代次 G、游标被复位为 G+1 后，旧扫描不再写 `last_scanned_book_id`；② 复位后新作业从 `snapshot[0]` 起扫（断言首本 id 而非仅断言计数归零）；③ 代次不符时 `ensureIndexedIncremental` 返回 PROGRESSED 且 `isRunning` 复位为 false。

### S03-2 仅有 coverage 行的孤儿永不回收 — 静态确认（P1，A）
`purgeOrphanIndexRows`（:415-430）基准仍是 `termDao.distinctBookIds()` 单一来源；`SearchIndexCoverageDao`（`SearchTermDao.kt:153-178`）**没有** `distinctBookIds()`，只有 `all()` / `rowsForBook` / `deleteByBook`。因此「coverage 有行、term 无行」的 book_id 永远不进 `indexed` 集合 → 不进 `orphans`。该类行确实可产生（B）：`indexOneBook` 的失败/不适用路径按 `SearchCoverageState.FAILED / NOT_APPLICABLE` 写 coverage 行（`indexed_at = 0`，见 `SearchTermDao.kt:147-148`），此时可无 term 行。真机上此类行的实际数量本轮未核（:397-405 注释里的「13,953 行」是**历史实测的聚合数字**，且指 term 侧孤儿，不是本条的 coverage-only 场景）。
最小修复：`SearchIndexCoverageDao` 加 `@Query("SELECT DISTINCT book_id FROM search_index_coverage")`，`purgeOrphanIndexRows` 取两侧并集后再 `filterNot { it in active }`。行为测试：① 只插 coverage 行 + 书已从活跃集移除 → 返回 1 且两表都空；② 只插 term 行的回归仍绿；③ 活跃书的 coverage 行不受影响。注意 `distinctBookIds` 在千万行表上是索引扫描（:405-408 的既有告诫），新增一次 distinct 扫描须留在同一入口内，不得移到每次后台扫描开头。

### S03-3 撤销删除等待整本重建索引 — 等待顺序静态确认（P2，A）；体感耗时待运行复现（B）
`BookRepository.kt:485-501`：撤销路径在返回前 `withContext(Dispatchers.IO) { repo.reindexBookById(bookId) }` 同步跑完才把结果交给 UI（B：用户体感为「撤销」按钮卡住，时长与书体量相关；本轮未计时）。**注意本条与 S04-2 不同**：这里逐书重建**已经被调用**，问题只在撤销同步等待它；S04-2 是替换正文后**根本没有调用**。
最小修复：撤销先落库并立即返回，索引重建交给 `SearchIndexScheduler.triggerNow(force = true)` 或既有 per-book 队列。行为测试：① 撤销后 `_isUndoing`/加载态立即清除且未等待 reindex 回调；② 撤销返回后该书在索引中被重新可见（可在 fake 引擎下断言 reindex 被调用一次，而非断言真机耗时）。

## 2. S04 书架下拉刷新：静态确认「一次手势直接替换正文」

完整链路（A）：`ShelfScreen.kt:368 onRefresh` → `ShelfAction.TriggerRefresh`（`ShelfAction.kt:117`）→ `ShelfRoute.kt:805 viewModel.refresh { showMessage(it) }` → `ShelfViewModel.refresh:613-630` → `syncUpdatedSources:642-685`（对 `checkUpdated` 结果 **for 循环串行**调 `importer.refreshFromSource(bookId)`）→ `ShelfImporter.refreshFromSource:199-236` → `repairFileTyped:245-300` → `reselectEpub / reselectPlainText`。

- **写入确认**：无（A：静态确认，本轮未真机点击）。全链路无对话框，结果只在结束后以 Snackbar 文案（"已同步 N 本书的源文件更新…"）汇报（`ShelfViewModel.kt:665-684`）。
- **替换的是什么**：内部稳定正文事实源（booksDir 下的副本被重写），章节索引随新内容重建（B，依 `repairFileTyped` 调 `reselectPlainText/reselectEpub` 的既有语义）。
- **持久化了什么**：`library.books` 正文副本 + 书名/元数据 + `library_source_refs` 的 `size`/`last_modified` 基线（`refreshSourceBaseline:314-336`）。`content_hash`/`candidate_fingerprint` **刻意不重算**（:307-308 注释 + :325-326 传 `ref.contentHash`/`ref.candidateFingerprint`）。
- **搜索索引**：`ShelfImporter.kt` 全文对 `reindex` / `SearchIndex` 的引用数为 **0**（A）→ 结论「正文被替换后倒排索引仍指向旧文本，直到下一次全量重建才被纠正」是由该静态事实推出的（B），本轮未做搜索命中验证。
- **标注/高亮坐标**：本轮未见任何按新章节/新偏移重映射的调用（B，未逐类核对 `NoteEntity`/`HighlightEntity` 的锚点解析入口，见 §5 未验证项）。
- **契约矛盾（新增项 C1，P2，A）**：`ShelfUiState.kt:37-43` 对 `contentUpdatedBookIds` 的说明仍是「需要用户确认刷新」+「不改变正文事实源」，与 `syncUpdatedSources` 的实际行为（无需确认、直接改正文）相反。属工程侧文档漂移，不是独立产品缺陷，但会误导下一位改这块的人。

### S04-1 无确认批量替换正文 — 静态确认（P1，A）
### S04-2 替换后未触发逐书索引更新 — 静态确认（P1，A）；索引陈旧后果为推断（B）。**不是 S03-3 的同一缺陷**：本条是「没调用」，S03-3 是「调用了但同步等待」，二者只共享逐书调度入口，见 §4
### S04-3 陈旧 hash / fingerprint — 代码路径静态确认（P1，A）：`LibraryBrowserPolicy.kt:78-100` 仍以 `ref.candidateFingerprint` 判 SAME_CONTENT、以 size+时间判 CONTENT_UPDATED；`ShelfImporter` 的导入去重路径也消费 hash/fingerprint。刷新写新正文后二者不变 → 「同一本书的『同内容』判定依据是**替换前**的指纹」为静态推断（B），本轮未用真实 provider 跑过一次判定。
### S04-4 元数据抹除 — 已局部收窄（A）：`size` 现在观测不到时保留旧基线（`observed.first ?: ref.sizeBytes`，:320），但 `last_modified = observed.second` 无条件写入，仍可把已知基线写成 0/null 语义（B：其后果取决于 `checkUpdated` 对缺失值的处理，本轮未逐分支核）。
### S04-5 来源引用不入备份 — 静态确认（P1，A），见 §3
最小修复（打包，顺序见 §6）：① `syncUpdatedSources` 先只算列表、经一次对话框确认后再逐本刷新（复用 `ShelfUiState` 既有 `contentUpdatedBookIds` 作为候选集）；② `refreshFromSource` 成功后按 book 触发索引更新（调 S03 已有的 per-book 重建入口，勿新造）；③ `refreshSourceBaseline` 一并重算 `content_hash`/`candidate_fingerprint`（接受一次全文件读，或明确把「指纹待重算」置为待办标记，二选一，不留静默不一致）；④ `last_modified` 与 `size` 同口径：观测不到即不动；⑤ 同步修正 `ShelfUiState.kt:37-43` 注释。
行为测试边界：①候选非空时必须先产生一次确认请求、未确认则零本被刷新；②刷新成功后该书的重建被恰好请求一次（fake scheduler 计数）；③替换正文后旧关键词不再命中、新关键词命中；④刷新后 `candidateFingerprint` 等于新正文指纹（或待重算标记成立）；⑤provider 不可读时 `size` 与 `last_modified` 均保持旧值。

## 3. S14 备份实际覆盖面（矩阵）

三形态：本地 ZIP（`LocalZipBackup`，SAF）、WebDAV（`ProfileWebDavManager.backup`）、局域网（`SyncRepository` + `SyncOptions`）。写=落盘/上传，读=恢复/落库。

| 数据 | 本地 ZIP | WebDAV | 局域网 |
|---|---|---|---|
| 正文文件（`filesDir/books/**`） | 写✅ 读✅（解压覆盖） | ❌ 缺失 | ❌ 缺失（本轮未见按书下载正文的落盘路径，标**未知**） |
| 书籍信息 `library.books` | ✅ | ✅ | 写✅ 读✅（`syncBooks`，`SyncRepository.kt:116,330`） |
| 来源引用 `library_source_refs` | ❌ 缺失 | ❌ | ❌ |
| 进度 `reading_progress` + `chapter_reads` | ✅ | ✅ | ✅（`syncProgress` 同时门控 progress 与 sessions，:131,363） |
| 阅读会话 `reading_sessions` | ✅ | ✅ | ✅（id 键合并，`applySession:264-292`，无 (book_id,started_at) 去重 → 跨端可产生重复会话，**产品决定**） |
| 标注·灵感 `notes` / `highlights` / `inspirations` | ✅（三类都在 JSON） | ✅ | 仅 inspirations；notes/highlights ❌ |
| 规则 `reader_text_rules` / `reader_corrections` | ❌ | ❌ | ❌ |
| 书单关系 `tags/categories/shelves/book_tags/book_categories/shelf_books` | ✅ | ✅ | ❌ |
| 设置（DataStore/SharedPreferences：主题、字号、密度、时长单位、目标、AI 配置等） | ❌（导入提示已如实写明不含 AI Key / WebDAV 密码，`ProfileRoute.kt:539`） | ❌ | ❌ |
| 页面索引 / 锚点缓存 / `book_content` / `book_file` / 搜索索引 | ❌（索引可由重建恢复，属设计） | ❌ | ❌ |

补充事实（A）：JSON 快照 = `LocalExport` **13 组**（`JsonBridge.kt:55-74`），导入按 id 合并、`schemaVersion` 不符即拒绝、单事务回滚，合并策略与局域网共用 `SyncMergePolicy`。WebDAV 只写 `cra-backup-latest.json` + 时间戳归档（`ProfileWebDavManager.kt:39-49`），恢复前 `DatabaseSafetyNet.snapshotNow` 兜底（:66-73）。开源阅读导入只写进度、不碰正文/书源/设置（:87-100；页面文案 `WebDavSubPage.kt:613` 与之一致）。
新增项 **C2（P2，A）**：`StorageSubPage.kt:511` 承诺 JSON 导出含「同步账号信息」，但 `LocalExport` 无该字段，且同句又声明不导出 WebDAV 密码/token —— 提示文案内部自相矛盾且与代码不符。
新增项 **C3（P2，A）**：`ProfileRoute.kt:458,531` 的恢复/导入确认只列出「书籍、灵感、进度与会话」，实际写面覆盖 13 组（含笔记/高亮/标签/分类/书单关系/已读章节）→ 用户对「恢复会覆写笔记」缺乏预期。
最小修复：文案对齐（C2 删「同步账号信息」或补实现；C3 改为「笔记、高亮、标签、分类与书单」），属**低风险**单文件改动 —— 文案本身仍需测试或真机核对确认可达处已同步，不使用「零风险」表述。真正的覆盖面扩展（来源引用 + 规则）建议单列一片，只在 `LocalExport` 增两组并同步 `importFromString` 合并分支，ZIP/WebDAV 自动获益，局域网保持 4 类不动。
行为测试：①导出的 JSON 含新组且非空；②导入按 revision 合并（旧不覆新）；③恢复后来源索引可用（`sourceIndex.snapshot()` 命中）；④`schemaVersion` 不符仍整体拒绝；⑤C2/C3 文案改后需有测试或守卫断言「提示里列出的类别 ⊆ 实际导出/合并的组」，防止文案再次与 `LocalExport` 漂移。

## 4. 跨功能根因去重

- **R1「逐书索引更新缺少统一的异步调度入口」**：S04-2 与 S03-3 **相关但不是同一个缺陷**，根因也不同 —— S04-2 是正文替换后**根本没有调用**逐书重建（`ShelfImporter.kt` 对 `reindex`/`SearchIndex` 零引用）；S03-3 是**已经调用**了 `reindexBookById`，但撤销流程同步等待其完成才返回。二者的共同点是都落在「逐书索引调度」这一个接缝上，因此**可以共享同一个异步调度入口**（统一的 fire-and-forget per-book 请求），但需要**两处分别修复、两条独立验收**：①刷新后发出请求；②撤销不再等待请求。合并成一条会掩盖「撤销其实已经在重建」这一既有事实。不必重写同步层。
- **R2「游标/基线是可被单方覆盖的标量」**：S03-1 与 S04-3/S04-4 同形 —— 前者是旧扫描覆盖游标，后者是刷新不重算指纹、`last_modified` 无条件写。**两个**修复（代次令牌 / 基线重算），但共用同一条判据：写入方必须同时是「观测的最新值」生产者。
- **R3「回收与写面不对称」**：S03-2（只从 term 侧枚举）与 S04-5+C 类（备份只覆盖部分表）都是「以 A 表为全集处理 B 表」。**不建议合并实现**，只登记为同一审查视角，避免下轮误以为一处修复解决两件。
- 时长显示类问题与本片无关，未纳入。

## 5. C01 记录（不恢复）与未验证项

- **C01 状态（本版已修正前一版的错误结论）**：`incompleteBookIds` 与 `rowsByStates` **在当前工作树中已删除**（工作树 `git grep` 无匹配），但**在当前 HEAD `5aed5939…` 中仍然存在** —— HEAD 命中 `SearchIndexRepository.kt:475 suspend fun incompleteBookIds(`（:481 调用 `coverageDao.rowsByStates(...)`）与 `SearchTermDao.kt:175 suspend fun rowsByStates(states: List<String>): List<SearchIndexCoverageRow>`；后者声明在 `SearchTermDao.kt` **文件内部**的 `SearchIndexCoverageDao` 接口里，不是独立的 `SearchIndexCoverageDao.kt` 文件。
  ⇒ 准确描述：**当前是相对 HEAD 的未提交删除，可以从 HEAD 精确恢复；恢复决定尚未执行。**（前一版写「工作树与 HEAD 均无匹配」是把只在文件层做过的 grep 误当作 HEAD 取证，已作废。）
  `2026-09-22-cleanup-six-api-review.md`（:9,18,19,23,27,28）建议恰好恢复这两个 API，账本记录为「清理决策复核完成，修正未执行」。**本轮仅记录，未恢复，未与任何其他修复混做。**
- 运行级取证缺口（本轮约束下不运行 Gradle/ADB，故以下**只有静态证据**，不得当作运行结论）：S03-1 的游标覆盖时序（需 WorkManager 日志或双作业并发）、S03-2 真机上 coverage-only 行的实际数量、S04-2 替换后旧关键词仍能命中的实测、S04-3 陈旧指纹导致的 SAME_CONTENT 误判实例。这些条目的**代码路径已静态确认**，**后果待运行复现**。
- 未验证（无法在本轮只读约束下确认）：① 局域网同步是否有任何按书下载正文的落盘路径（矩阵相应格标**未知**）；② `ReadingProgress`/`Note`/`Highlight` 在正文替换后的坐标重映射实现；③ `checkUpdated` 对 `last_modified` 缺失值的分支行为；④ 真机上「撤销删除」的实际等待时长；⑤ S03 历史测试证据：2026-09-20 记录为 5 个测试类 / 126 项 / 0 失败（混合工作区 dev 门禁），S04 为 4 类 / 68 项 / 0 失败 —— 均为**只报聚合数字、无原始产物留存**，本轮未重跑（不运行 Gradle）。
- 越界修改：**无**。唯一写入文件为本报告。

## 6. 最小修复顺序（按「先止血、再补面、后收尾」）

1. `SearchIndexRepository.kt:415-430` + `SearchTermDao.kt:153-178`（S03-2）：coverage 侧 distinct，两行 DAO + 一处并集。改动面最小、可独立测试。
2. `ShelfImporter.kt:245-300` 写后**发出**逐书重建请求（S04-2）：一个调用点。这里确定的「异步 per-book 调度入口」形状就是第 7 条要复用的同一个接缝 —— 先固定入口，再改第二个调用方。
3. `ShelfViewModel.kt:642-685` 前置一次确认 + `ShelfUiState.kt:37-43` 注释纠正（S04-1 / C1）：UI 行为变更，须与 2 同片交付以免「有确认但索引仍旧」。
4. `SearchIndexRepository.kt:356-365,817-873` 代次令牌（S03-1 / R2）：涉 schema 或状态语义，单独一片。
5. `ShelfImporter.kt:314-336` 指纹重算 + `last_modified` 同口径（S04-3/4）：带一次额外全文件读的成本决策。
6. `JsonBridge.kt:55-74,190+` 补 `library_source_refs`/规则两组，并对齐 `StorageSubPage.kt:511`、`ProfileRoute.kt:458,531` 文案（S04-5 / C2 / C3）：文案部分低风险但仍需按 §3 第⑤项测试核对。
7. `BookRepository.kt:485-501` 撤销不再同步等待重建（S03-3）：**与第 2 条是同一接缝上的两个不同缺陷**（一个补调用、一个去等待），复用第 2 条确定的入口即可，但需独立验收「撤销立即返回」+「请求仍被发出」。

未领取下一任务。
