# 当前交接入口

## 2026-11-17 夜间：全文索引下线 + Mate 70 闪退收尾（DSH）

**结论**：Mate 70 上"覆盖安装后约 9 秒闪退"的根因已定位并修复；用户已重装并在真机确认数据完整。

- **根因（真机实测）**：用户库 **5.17 GB**，其中 `search_terms` **1.31 亿行 / 4.9 GB**（`text_basis` 的
  display/original **各建一遍**，每行 `offsets` 仅 5–12 B，胖在行数）。库版本停在 v15，
  每次启动都要迁移；而迁移前 `DatabaseSafetyNet.snapshotIfUpgrading` 整库复制（实测 **90–143 s**）、
  开库前 `PRAGMA integrity_check` 全库扫描（数十秒）都在主线程 → 兼容容器 **9–12 s** 判无响应杀掉 →
  迁移永远跑不完 → 下次重来。
- **修复 1（`DatabaseSafetyNet`）**：库 > `MAX_SNAPSHOT_BYTES`(256 MB) 时跳过整库快照与完整性预检；
  `fallbackToDestructiveMigration` 早已移除，跳过快照不引入静默丢数据风险。
- **修复 2（搜索改按需）**：新增 `SearchIndexRepository.searchContentOnDemand()`（逐书逐章读正文子串匹配，
  复用既有分章/EPUB 解包与大小上限），`searchContent()` 改委托它；旧索引查询路径 `searchContentByIndex` 已删除。
  索引写入全部停用：`SearchIndexScheduler.start/enqueueOnce/triggerNow/enqueuePeriodic`、
  `SearchIndexBookRefreshCoordinator.request`、`ReaderSearchIndexCoordinator.applySearchIndexRefresh` 均改为 no-op。
- **修复 3（库 v17）**：`MIGRATION_16_17` 清空 `search_terms`/`search_index_state`/`search_index_coverage`
  （只 DELETE 不 DROP，保留 Room 校验与 DAO 编译；真正回收磁盘需一次性后台 VACUUM）。
- **新增：导入阅读记录**：`ReadingRecordsImporter` + 我的→存储/日志与诊断→「导入阅读记录（JSON）」。
  按 `content_hash` 匹配本地书（退化"标题+大小"），恢复 reading_progress（本地较新则保留）/reading_sessions/
  inspirations；匹配不到的书不新建、归属置空。**首版因灵感 `source_book_id` 写旧 id 触发外键约束整批回滚，已修**
  （匹配不到置空 + 单条失败不拖垮整批）。
- **数据保全**：导出 20 张表 → 2,051,198 B JSON（20 表：books 12 / reading_progress 11 / reading_sessions 2248 /
  chapter_reads 1 / inspirations 2 / library_source_refs 9）。
  电脑副本：`E:/AgentWork/CRA/mate70-startup-diagnostics-v1/phone-export/READING-RECORDS-BACKUP.json`。
- **真机闭环（用户确认）**：卸载旧装 → 装 `cra-clean-0.5.1-v4.apk`（versionCode 4，干净包、无诊断代码）→
  先导书 → 导入记录成功；只读核验首页：累计阅读 **124 小时 12 分钟 · 9 本**、续读《不死的黑蛇只想单纯的活着》25%、
  在读 8 / 已读完 1、最近灵感已回。
- **测试**：`--tests "*Search*"` 现 **全绿**；已删 3 个断言旧语义/旧机制的用例文件
  （`SearchIndexRepositorySearchTest`、`SearchIndexCancelControlTest`、`SearchIndexBookRefreshCoordinatorTest`）。
- **已删（2026-11-17 22:20）**：`ReaderSearchIndexCoordinator`（其纯函数 `currentSelectionSourceRange`
  已内联进 `ReaderViewModel`），`compileDebugKotlin` 通过。
- **已完成（2026-11-17 22:55，DSH）**：`SearchIndexScheduler`/`SearchIndexWorker`/`SearchIndexWorkerPolicy`/
  `SearchIndexBookRefreshCoordinator` 四个空转类已删除，注入点全部清零：`App.kt`（import + `SearchIndexEntryPoint` +
  后台线程 `start()`）、`ProfileViewModel`（`searchIndexRepository`/`searchIndexScheduler` 构造参数、`searchIndexProgress`、
  `rebuildSearchIndex`、`handleProfileAction`、`cancelSearchIndex`、`anchorSentence`）、`ProfileUiState` 两个动作、
  `ProfileRoute` 两个分支、`DiagnosticsSubPage` 停止索引入口 + 两个确认弹窗、`StorageSubPage` 索引卡与弹窗、
  `SearchViewModel`/`SearchScreen` 静默进度胶囊、`SearchIndexRepository`（`scheduler`、`Progress`、`progress`、
  `triggerFullRebuildNow`、`cancelIndexing`、`FullRebuildLaunch`、`CancelIndexingResult`）、`ShelfImporter` 与
  `BookRepository` 的 `indexRefreshCoordinator`。`SearchIndexWorkerPolicy` 里仍被复用的三个纯函数
  （`indexSweepResult`/`ChapterCounts`/`resumeChapterCounts`）已原样搬入 `SearchIndexRepository`。
  验证：`:app:compileDebugKotlin`、`:app:testDebugUnitTest --tests "*Search*"`（**205 项 0 失败**）、
  `:app:assembleDebug` 三条 **rc=0**；生产代码 grep 四个类名 + 两个注入名 **0 处**；APK
  `8e92672fbc51ce2666ed22ced385902cf2cab167adc27f1b32a53eb3ca5c08ea` / 52,272,852 B。
  详见 [删除报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-index-deadcode-removal.md>)。
- **已完成（2026-11-17 23:25，DSH，phase 2）**：androidTest 的 5 个已删符号引用（15 处 / 8 文件）清零 ——
  只摘掉对已删索引 API 的构造参数/import，8 个文件的 `assert*` 计数改动前后逐个相同（13/78/15/112/106/23/0/37）；
  `J1RealReaderFixtures` 运行期调度器证据清单去掉 `SearchIndexScheduler` 一项，`J1RealReaderJourneyTest` 3 行注释改写
  （等待与断言未动）。`ReaderViewModel` 的无用 `searchIndexRepository` 注入（含 3 个单测构造实参）已删。
  验证：`:app:compileDebugKotlin`、`--tests "*Search*"`（**205 项 0 失败**）、`:app:compileDebugAndroidTestKotlin`、
  `:app:assembleDebugAndroidTest` **四条 rc=0**；androidTest grep 五个符号 **0 处**；新 debug APK
  `83898578fbf5896e42d77b2fa51c8253177c4d40afba5307cedb39542d13977c` / 53,374,781 B。
  **未在设备上执行仪器测试**。详见 [清理报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-androidtest-cleanup.md>)。
- **设备验证（2026-11-18 00:50，DSH）**：MuMu 实例 1「洛水之蔚」（serial `127.0.0.1:16416`，ALN-AL00 / Android 12 / API 32）实跑
  `:app:connectedDebugAndroidTest`（合同允许的替代口径：只跑清理涉及的 7 个 acceptance 类，非全包）：**18 项 / 10 通过 / 8 失败 / 0 跳过**，两轮失败集合一致。
  分类：**本轮删除导致 0 项**；3 项缺 `craRunId` 夹具前置（`D2RelinkUserJourneyAcceptTest` ×2、`InspirationCategorySourceRetentionDeviceTest` ×1，
  setUp 即 `Root must bind craRunId`，单跑同样失败）；5 项为当前树**未归因的真实失败**（J1 journey01/02/03、MdReadingResume、MaterialGathering；
  journey02 为 SearchScreen 内容命中键 `content-<bookId>-<basis>` 自 `21c5dbe` 起的多章冲突）。未修任何断言/超时、未加 @Ignore。
  设备已恢复：AGP 自动卸载测试包、`MuMuManager control -v 1 shutdown`（`is_process_started=false`），跑前/跑后包清单一致。
  证据与未归因说明见 [设备验证报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-connected-androidtest-device.md>)，原始件在 `device-run-phase2/`。
- **搜索命中键冲突已修（2026-11-18 11:05，DSH）**：`SearchScreen` 正文命中列表 key 原为 `content-<bookId>-<basis>`，同书多章命中必撞；
  改为按数据自身定位推导（`bookId`+`textBasis`+`indexChapterIndex`+`charOffset`，完全相同重复行再追加出现序号），只改键、不动顺序/点击/高亮/分页。
  验证：`:app:compileDebugKotlin` **rc=0**、`--tests "*Search*"` **rc=0（205 项 0 失败）**、设备（MuMu 实例 1）两轮跑 `J1RealReaderJourneyTest`：
  键冲突异常 **0 次**（logcat/gradle 日志两轮均为 0），journey02 从「2.065s 抛 `Key ... already used`」变为「13.9s 跑过后段」。
  **但 journey02 仍未 pass**：现卡在 `J1RealReaderJourneyTest.kt:1047`「返回搜索页后等输入框 10s 超时」，与 journey01/03 同属返回/导航问题（上一轮登记为未归因），
  按边界未修未重试。设备已 `shutdown`（errcode 0）、无测试包残留。详见 [键冲突修复报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-search-key-fix.md>)。
- **craRunId 宿主自动绑定已补齐（2026-11-18 11:46，DSH）**：补齐 `CraIsolatedTestRunner`（及 `CraRunIdBindingListener`）在用例启动前的宿主参数绑定，彻底解除多类组合运行下的 `craRunId` 正则互斥与缺参崩溃。
  验证：`:app:compileDebugKotlin` **rc=0**、`:app:compileDebugAndroidTestKotlin` **rc=0**；MuMu 实例 1 实跑 7 个 acceptance 类（18 tests）：
  原来因缺 `craRunId` 在 `@Before` 提前抛 `Root must bind craRunId` 导致**根本未执行**的 3 项（`D2RelinkUserJourneyAcceptTest` ×2、`InspirationCategorySourceRetentionDeviceTest` ×1），**现已全部 100% 真正进入执行阶段**（耗时分别达 0.989s、0.777s、22.742s），真实结论为 UI 阶段失败（D2 找不到“更多”按钮节点，Inspiration 找不到标签输入入口，与既有 MaterialGathering 同根因）。
  原通过的 10 项全部稳定保持通过；设备已正常关机、无被测包残留。详见 [craRunId 绑定报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-crarunid-binding.md>)。
- **UI 验收用例无障碍文案锚点迁移为稳定 testTag（2026-11-18 13:03，DSH）**：消除无障碍文案脆弱耦合，生产端新增 3 个稳定 testTag（`shelf-more-actions`、`inspiration-taxonomy-toggle`、`inspiration-new-tag-input`），测试端完成定位方式迁移与灵感面板折叠卡片展开前置补齐。
  排查确认 `ReaderExternalPendingFailureProductionTest.kt:200` 等另外 3 处均指向阅读器内部「更多」，非书架按钮，严格未动。
  验证：`:app:compileDebugKotlin` **rc=0**、`:app:compileDebugAndroidTestKotlin` **rc=0**、`:app:testDebugUnitTest --tests "*Search*"` **rc=0（205 项 0 失败）**。
  MuMu 实例 1（`127.0.0.1:16416`，ALN-AL00 / API 32）实跑 3 个类（5 tests）：原 4 处阻断全部 **100% 跨越**（D2 成功执行书架批量选择/清缓存向后推进 45~57 行；灵感面板两项「标签输入入口必须存在」断言全部通过，成功走通输入与 tags/categories 表写入），原阻断彻底消除；暴露的下一阶段断言分别为清缓存后返回（D2）与面板保存可用（灵感）；设备已安全关机、无测试包残留。详见 [testTag 锚点迁移报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-testtag-anchors.md>)。
- **第三批文案漂移点修掉：D2 验收双绿灯（2026-11-18 13:25，DSH）**：生产端新增 2 个 testTag（`ReaderChrome.kt` 顶栏返回 `reader-back-btn`、`ReaderInspirationSheet.kt` 顶栏保存 `inspiration-save-btn`），测试端对应切换为 tag 定位。
  验证：`:app:compileDebugKotlin` **rc=0**、`:app:compileDebugAndroidTestKotlin` **rc=0**、`:app:testDebugUnitTest --tests "*Search*"` **rc=0（205 项 0 失败）**。
  MuMu 实例 1 实跑 3 个类（5 tests）：**D2RelinkUserJourneyAcceptTest 2 项全绿（PASS）**（8.271s 与 11.479s，原阻断彻底解除）；灵感面板保存按钮成功触发，灵感落库完全通过，两用例分别推进至退出选区清理（Inspiration）与退出离开阅读器（MaterialGathering）；通过率由 1/5 提升至 3/5；设备已安全关机、无测试包残留。详见 [第三批 testTag 锚点落地报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-testtag-anchors-2.md>)。
- **第四批测试侧「按旧文案找返回」稳定锚点迁移与退出链路走通（2026-11-18 14:10，DSH）**：测试侧残存 17 处「找返回」逐条判读完成：7 处弹层/附页内部返回（生产本身为 `"返回"`）严格保留原样，10 处阅读器顶栏返回全面迁至 `reader-back-btn`；公共支撑 `ReaderChromeExitSupport.kt` 迁至 `reader-back-btn`。
  上一轮剩余 2 个红灯的退出阻断 **100% 彻底打通**：`MaterialGatheringJourneyTest` 跨越行 1596 `必须真实离开阅读器`，成功通过 P4 退出与 P5 搜索（`mat_40_exit` 与 `mat_50_search` 均 PASS），一路推进至行 1666；`InspirationCategorySourceRetentionDeviceTest` 跨越行 255/1480 选区残留退出阻断，成功走通 P4-P7 核心逻辑 300 多行，一路推进至行 539。
  验证：`:app:compileDebugKotlin` **rc=0**、`:app:compileDebugAndroidTestKotlin` **rc=0**、`:app:testDebugUnitTest --tests "*Search*"` **rc=0（205 项 0 失败）**。
  MuMu 实例 1（`127.0.0.1:16416`）实跑 5 个类：`D2Relink...` 2 项全绿保持；新停顿点代码级深度归因完成：行 539 与行 1666 均停在「返回阅读处」，根本原因为两用例是从灵感详情页（阅读器外部）点击「查阅原文位置」进入，按 Root 174 既有架构裁决（`NestedTemporaryReaderEntryTest`），外部临时查阅因离域 `endReaderSession` 清空栈，顶栏按合同不组合「返回阅读处」按钮，用例存在断言假设漂移；设备已安全关机（errcode 0）、端口释放、无测试包残留。详见 [测试侧锚点迁移与退出走通报告](<E:/AgentWork/CRA/mate70-startup-diagnostics-v1/REPORT-testtag-anchors-3.md>)。
- **待确认（用户裁决）**：`J1RealReaderFixtures` 证据 JSON 的 `workManagerSchedulers` 少了 `SearchIndexScheduler` 一项；
  若跨轮对比需要保留历史字段名，可回退该 1 行（不影响编译与门禁）。
- **未提交**：本轮全部改动只在工作树，未 commit/push。


- **基线提交（2026-11-18 12:43）**：`6cd4ce5` chore(baseline)，135 文件 +13747/-6951，把 1377cf9 以来全部未提交改动固化为
  审查基线（含上述三阶段 + Legado 导入 + Room v17 + 阅读内核优化）。**仅本地提交，未 push**。此后审查用 `6cd4ce5..工作树`
  做对照；此前任何 "对比 HEAD" 的结论都会把历史未提交改动误判为本轮越界（Antigravity 12:40 的审查即因此产生 3 条假阳性：
  `ProfileViewModel` Legado 函数、`AppDatabase` v17、`MainActivity` 重排，均早于本轮）。
- **登记：设备端剩余红灯（2026-11-18 12:43，DSH）**：`craRunId` 绑定补齐后（见 11:46 条），原先「未执行」的 3 项已真正执行，
  **真实结论是 UI 阶段失败**（D2 找不到「更多」按钮节点、Inspiration 找不到标签输入入口），与 `MaterialGatheringJourneyTest` 同根因。
  目前仍红的一族（J1 journey01/02/03、MdReadingResume、MaterialGathering、D2×2、Inspiration×1）**均未归因**：缺改动前基线 APK
  对照，无法区分「索引按需改造引入」与更早的既有缺陷。按用户决定先登记、不逐项修。
- **审查基线提示**：后续任何代码级归因请以 `6cd4ce5` 之后的工作树为准；早于该提交的「diff HEAD」结论必然产生假阳性
  （Antigravity 2026-11-18 12:40 的审查即因此误报 3 条越界：`ProfileViewModel` Legado 函数、`AppDatabase` v17、`MainActivity` 重排）。
- **Antigravity 现状**：个人配额 2026-11-18 12:40 耗尽（17:36:29 重置），中断的是「独立代码审查」子任务，非 craRunId 实施；
  中断后 `compileDebugKotlin` + `compileDebugAndroidTestKotlin` 实测仍 rc=0。
## 2026-11-17 Mate 70 对照问题修复

当前 D 工作树候选 `8a14246e165dccc0f53eac32b6a5ef1d2bf0e7b421d3d7d007e5a3454fd4f893`，修复书架查询异常后无法重新订阅、重试超时回到骨架的问题，并提高夜读灵感占位字和字号滑杆端点对比。定向 JVM 39/39；当前包在 MuMu 实测夜读两处对比，装机 SHA 一致，基线已恢复。Mate 70 Pro 的 HDC 已接入。用户报告覆盖安装后闪退，两次实机冷启动均复现进程在约 9 秒后退出。Android 异常栈尚未取得，手机安装字节未回读。此前导出的 ZIP 为 0 B，未有可恢复备份，保持现有安装及数据。见 [修复报告](<E:/AgentWork/CRA/mate70-mobile-fixes-v1/REPORT.md>)。

保全候选 `ac7f00a9…` 补齐新书导入全文索引，定向 JVM 401/401，MuMu 证实无需手动重建、7391 首屏命中及查询返回保留，并证实系统语音不可用时未切云端。保全候选 `8d2a152…` 为 v15→v16 迁移与真实 ZIP 31/31、定向 JVM 638/638、Legado 预览保留原读位、外观第三章及深色状态栏恢复的原证。

最新在 MuMu 用旧 APK `0f8d63a8…` 实际生成合成书、位置、会话和灵感后，原地升级到 `ac7f00a9…`，书架能打开，真实 v15→v16，7 张既有表行摘要、正文 SHA 与读位一致，新增 8 张账本表。旧书的 7391 也无需手动重建便能搜到同一目标段。两份快照见修复报告，模拟器重启导致 boot_id 不同，持久基线一致。

## 2026-11-16 Mate 70 与当前候选对照（问题登记）

Mate 70 的旧安装与候选分别取证。MuMu 装机核对候选 10daa3e7…，已复现两项阻断：v15→v16 迁移遗漏旧手工索引清理，旧库启动崩溃；Legado 冲突位置默认预选，历史导入会覆盖原读位。当前源码仍可见两处。另复现全局短数字落点、统计口径、外观预览错章、深色状态栏恢复与听书自动联网提示边界。书内定位、书签回查、本书替换净化和合成 ZIP 空库恢复已有本候选正向证据。本轮另确认，外部历史账本各有 1 条数据时整包导出成功，但用该 ZIP 恢复空库后三层记录和导入批次均为 0。后续核验和二级页矩阵见 [对照报告](<E:/AgentWork/CRA/mate70-mobile-audit-v1/REPORT.md>)。Mate 70 尚无同包兼容验收；本批未实施修复。

## 阅读内核优化 v3（真机已采样）

候选 `0f8d63a810886646efe0552cad8208ec92987d2f165597299b9b5ca3895612d7`。大 Markdown 冷开章长解析不生成源跨度，手动翻页录制帧复用绘制层。定向 JVM 64/64，真机装机 SHA 一致并完成恢复。冷开索引至控制器首屏单次同夹具 5.527→4.979 秒，手动慢帧仍在。见 [报告](<E:/AgentWork/CRA/reader-kernel-device-v3/REPORT.md>)。另交 [Legado 导入方针](<E:/AgentWork/CRA/reader-kernel-device-v3/LEGADO-IMPORT-PLAN.md>)，功能未实施。

## 阅读内核优化 v2（真机已采样）

READER-KERNEL-DEVICE-v2，候选 `aba63e6874aed7d5959b4319f1d0466848a576952b5049e45c4abedf775118d2`。Markdown 索引重开命中，同一真机/9.9 MB 测试资料的索引后到控制器首屏区间，v1 约 7.4 秒，v2 冷开约 5.5 秒、重开 129 ms。手动短窗仍超过 30 ms，未确认稳定改善。定向 JVM 63/63，装机 SHA 对齐，设备已恢复。详见 [报告](<E:/AgentWork/CRA/reader-kernel-device-v2/REPORT.md>)。

## 阅读内核真机候选（本机采样已完成）

READER-KERNEL-DEVICE-v1 候选 1e639e9c… 已在 c49ac6cf 装机回读核实，完成 TXT 手动翻页、双向跨章、自动揭页提交和约 9.9 MB Markdown 首开采样。已按基线恢复设备。自动揭页短窗 p95 约 15–16 ms，手动短窗仍有超时帧，缺旧包 A/B。Markdown 索引日志到控制器首屏约 7.4 秒，区间待细分。详见 [报告](<E:/AgentWork/CRA/reader-kernel-device-v1/REPORT.md>)。

## 阅读内核四项热路径优化（源码已交付）

READER-KERNEL-HOT-PATHS-v1 完成配色与分页解耦、同章预取读取接力、翻页抓图移至空闲及 EPUB 校准查询减负。编译、测试、基准和设备暂缓。恢复验证后先查编译与分页/生命周期回归，再测主题切换、跨章、翻页帧时与长篇 EPUB 首屏。详见 [报告](<E:/AgentWork/CRA/reader-kernel-hot-paths-v1/REPORT.md>)。

## 阅读内核连续性修正（sol，源码已交付）

READER-KERNEL-SOL-CONTINUITY-v2 完成锚点与总页数解耦、反向翻页积压处理、手势生命周期、长段取消和 Markdown 跨行样式。下方只读审查是本批输入。编译、测试、设备暂缓，旧 APK 仅对应其原源码。恢复验证后先检查前缀翻页与重排锚点、跨章反向和拖拽期间邻页就绪，再测长段取消和帧时。见 [本批报告](<E:/AgentWork/CRA/reader-kernel-sol-continuity-v2/REPORT.md>)。

## 2026-11-14 阅读内核只读审查与 sol 方案（尚未实施）

已按当前工作树审查，确认五项优先工作：渐进分页锚点与最终页数解耦、等待翻页队列有界及反向取消、邻页更新与手势生命周期隔离、长段预处理取消、Markdown 跨行样式坐标与扫描优化。前两项是 continuity 的新增衔接问题，旧 492 项 JVM 结果不能覆盖。未修改产品源码，未运行测试/构建/设备。详见 [审查报告](<E:/AgentWork/CRA/reader-kernel-review-sol-v1/REPORT.md>) 与 [sol 完整实施词](<E:/AgentWork/CRA/reader-kernel-review-sol-v1/SOL-PROMPT.md>)。用户自行交给 sol，未自动派发；实施顺序 F1→F2→F3→F4→F5，继续遵守先不测试。位图和缓存预算仅列后续候选。下方 continuity 为本次审查输入，尚未据方案修正。

## 2026-11-14 阅读内核连续性扩展（Codex Root）

用户要求先不要测，继续推进移动端阅读内核源码。已落地六个生产文件中的按需装行、长原子单元复用、渐进分页中连续翻页及页位保留、后台替换投影、预取读盘与测宽隔离、Markdown 单次解析复用。对照范围和交付见 [本轮报告](<E:/AgentWork/CRA/reader-kernel-continuity-v1/REPORT.md>)。本轮静态核对，编译、单测、基准和设备运行均暂缓。待用户恢复验证后，先核对页位与连续翻页，再测帧时。下方 APK 和此前 JVM 证据仍对应上轮源码。

## 2026-11-14 阅读内核性能优化（Codex Root）

用户授权性能与流畅性升级。基线 HEAD `1377cf9`，5 生产文件 + 4 测试文件留工作树。已完成字素数组直接生成、排版/预取协作式取消、首屏宿主协程发布与过期请求过滤、EPUB 进度快照复用。原版关闭后仍排 200 段的回归已复现，新版在当前 1 段后停止；定向 JVM **492/492**，assembleDebug 与 diff-check 通过。十万字符切分分配 5,998,312→1,600,216 B；千章 EPUB 2,000 次进度读取 22.49→0.76 ms（开发机 JVM）；五十万字整章分页 63→67 ms，尚无总体提速证据。

新候选 `291bf1a0df00e709dca485f8aff09f75fd1f5fa753bef7849161ef3c79cd5e5f`，52,894,992 B，位于既有 E candidate 的 app/build/outputs/apk/debug/app-debug.apk。上一 `4232ce75…` 已在构建前按 SHA 保全。交付 [REPORT](<E:/AgentWork/CRA/reader-kernel-performance-v1/REPORT.md>)，同目录含 ready、patch、SHA 与前后测试原证。**设备性能 NOT_RUN**，下一步测中性资料冷开首屏、连续跨章与字号重排帧时，核对续读/来源返回。上轮 Markdown 坐标迁移边界风险仍需单独复现。

## 2026-11-13 Qwen4 视觉规格实施合入（ZCode 闭环）
### 设备待办补完（2026-11-13 17:25）

设备空闲后完成：`4232ce75…` 装机 SHA 回读一致；浅色零回归 **PASS**（新旧包浅色 Profile raw 像素差异 0.827% 全在状态栏 y=15..48，内容区零差异）；深色对比度 **PASS**（分类图标提亮可辨、72.5% 差异集中图标列）；恢复 EXACT MATCH。**AI 附页护眼绿/暖纸文本 NOT_RUN**（设备无中性测试书+AI 链路需配置，离线 9 档纸张实算 ≥7.3 + JVM 断言保证，不伪造）。证据 `zcode-android-visual-closure-v1/evidence/`（4 张截图+raw 差异+device-baseline/restore）。剩余仅 Root 裁决项（WebDavBackupTest 陈旧红/TalkBack 人工专项/P5-P7）与择机补 AI 附页截图。


Qwen4 两阶段交付（只读规格 → 单向 patch）已由 ZCode 审查并合入：18 文件（新增 `ui/theme/CategoryTint.kt`，17 文件颜色实参替换/等值 token 化/新增 ContrastRatioTest 断言），`PATCH_ROUNDTRIP_OK=True`、before SHA 18/18 与 D 树一致。裁决：接受 Qwen4 对 SPEC §3.3 的实算修正（AI 附页文本 `paper.accent` 暖纸 4.219<4.5 → 改 `paper.fg` ≥7.3），登记为经实算修正的规格偏差；P5/P6/P7/D5/D6 排除。硬门：构建前保全 `e4e167da…`（逐位一致）。**一次 E 树同步错位事故**（多拼一层 android/）导致 01/02 轮跑旧树作废，已修正；Qwen4 侧 NOT_RUN 的两处编译缺口已最小修复（`SearchScreen` spec→12.dp、`ContrastRatioTest` dark→darkTheme）。真实验证：定向 JVM **165/165 rc=0**（ContrastRatioTest 8 项含 4 新增断言、锁定 token 值未漂移）、`:app:assembleDebug` rc=0、**新候选 `4232ce7528c8a547602686b91745cb7f01621398ef5a636787a9d41c6caf3a53`/54,088,510B**（7/21 dex 含 CategoryTint）。设备截图/装机 **NOT_RUN**（真机前台持续 NotificationShade=用户占用，按合同停设备写入、不伪造）；浅色零回归由"浅值未变"断言机械保证。设备状态保持基线。待设备空闲补浅深截图+AI 附页护眼绿/暖纸文本核对；Root 裁决 WebDavBackupTest 陈旧红/TalkBack 人工专项/P5-P7。交付 `E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/zcode-android-visual-closure-v1/`。


## 2026-11-13 R8 书架卡刷新修复批（ZCode，用户确认"先修复"）

用户报告 0.5.1 早期构建上百小时后书架"一直显示刷新、无法加载内容且未恢复"。诊断实锤：Kotlin 主线 `refresh()` 首查询无超时 + `books` flow 无 catch/错误态/超时 → DB 层挂起时刷新圈与骨架永久卡住、用户无自救；Android 对损坏 DB 默认删库重建（数据静默丢失）。修复 10 文件（commit `c1f1270`）：`ShelfViewModel` `withTimeout(8s)`+`flatMapLatest(retry)`+`catch`+`libraryLoadFailed`/`retryLoadLibrary`；Route/Screen 8s 超时「书架加载失败+重试加载」UI；`AppDatabase.onOpen` `wal_checkpoint(TRUNCATE)`+耗时日志；`DatabaseSafetyNet.snapshotIfCorrupt` 开库前 `integrity_check` 留损坏现场快照。验证：修前红（旧 VM 编译失败）→定向 32/32 绿→全量 JVM **3850/3851**（唯一红 `WebDavBackupTest` 已实锤 HEAD 基线同样红=既存陈旧测试，与本批无关）。真机：`b5f77182…` 书架正常加载 + `onOpen checkpoint: 0,0,0`/`total=13ms` logcat 实证；故障注入损坏 DB → Android 自动重建（快照保护由 JVM 7/7 覆盖）。**最终候选=`e4e167da…`/53,031,344B**（`16-final-assemble` rc=0）；最终包真机装机 BLOCKED（HyperOS `INSTALL_FAILED_USER_RESTRICTED`，按钮变「继续安装」仍被拒）→ NOT_RUN，同源代码已有真机验证。设备恢复完成（产品缺席/`.test`在装/Download104/历史ZIP在位）。**G-R8-3**=`0a81ac04…` 输出位被覆盖且未保全（二次同类遗漏，如实登记）。待 Root：WebDavBackupTest 陈旧红裁决、TalkBack 人工专项、新提交 `c1f1270` 未 push（分支领先 origin 1）。交付：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-landing-fix-v1/`（REPORT §10、ready appendix3、shelf-hang-fix.patch 21KB、shelf-fix-shas.json、08-16 各测试/构建日志、故障注入截图与 logcat）。


## 2026-11-13 R8 收尾批（ZCode，四项全部完成）

① 新候选 `0a81ac04…` vs 旧包 `5ef408d7…` **entry 级对比 PASS**（565=565、resources/assets/libs 零差、未压缩总差 +2720B 全在 dex，2MB zip 体积差=zipflinger 波动，疑点排除）。② 定向门禁 **412/412 rc=0**（搜索族+源导航+备份+接线+新回归）。③④ **五个本地提交**：`3a478bca`(fix 3 源文件)→`cebe771`(current.md)→`04ef16f`(13 份 coordination/handoff 归档)→`2dee4c8`(2 份 TalkBack 探针测试)→`5dde584`(探针适配 MD 夹具+收尾交接)；**工作树清零、暂存 0**，stray `0` 已删（留原证）。**TalkBack instrumentation 尝试 BLOCKED**：探针已适配（第001章→第01节）+androidTest APK `b8b32e74…` 构建完成，但设备出现并行变动（产品包被并行卸载+非本批包 `local.creationReadingAssistant.mobile` 在装=用户亲自操作），按 owner 冲突停写未执行 `am instrument`；下次唯一动作见 `r8-zcode-search-landing-fix-v1/evidence/talkback-instrumentation-blocked.md`。**冻结 APK 链**：现行候选=`0a81ac04…`（离线 412 门禁+真机首屏 7391 修复验证+**远页普遍性第二数据点：查询20 首屏 46.3% 直接含 MD-20**）；`5ef408d7…`=修复前基准（已保全）；`fc600e8c…`=R8 真机 A1-A6 历史基准。**恢复缺口 R8-H1**=收尾复验批误卸 .test 包，基线内容（4b0177c9）无盘上副本，已以 ca9c4458… 重装如实登记。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-LANDING-FIX-v1 收官（ZCode，首屏偏页修复 PASS）

首屏偏页根因=搜索索引 `charOffset` 基于**原始 Markdown 源文本**，阅读器 chapterStartOffsets/分页基于 **canonicalText**（去标记）；搜索来的 `legacyOffset` 未经 `MarkdownDocument.migrateLegacyOffset` 就被 `resolveChapteredPosition` 当 canonical 偏移消费（该方法原本只服务已存进度）。最小修复 3 文件（`landing-fix.patch` 2906B）：`SourceNavigationContract.migrateTargetOffset` 纯函数 + `ReaderProgressEffects.navigateChaptered` 的 MD 分支迁移 target + 新定向回归；TXT/EPUB/普通续读不变。修前红（rc=1）→修后绿 **32/32 rc=0**；新包 `0a81ac0400142d2d8d100c9bf9e1bd9a00166f91108f409ddab8e13cf5806e8e`（51,977,940B）装机 SHA 回读逐位一致；同一夹具搜 `7391`→**未翻页首屏 59.4%、第12节 `7391` 直接可见**（旧包为 75.3% 第13/14节需翻页）=**PASS**；返回保留、恢复 EXACT MATCH（历史 ZIP 3555f331… 保留）。旧包 `5ef408d7…` 构建前已保全（逐位一致）作修复前基准；`fc600e8c…` 保持 R8 A1-A6 基准。**现行候选=`0a81ac04…`**。源码改动留工作树，未 Git 暂存/提交。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-landing-fix-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-FIRST-LANDING-v1 收官（ZCode，首屏落点 FAIL）

真机 c49ac6cf 装 `5ef408d7…` 装机 SHA 逐位一致。既有 40 段同构夹具无唯一可输入远页词，按合同新建 `fixture-first-landing.md`（3575B/`75de68ac…`，唯一词 `7391` 仅出现 1 次于第12节约第941字）；初始页 0.0% 证不可见；搜索框逐位=`7391`，正文=1「第941字附近」。**点击后未翻页未滚动的首屏=75.3%（第13/14节），不含 `7391` 与第12段；一次左翻页 59.4% 才见目标段落 → 业务 FAIL（首屏偏页）**，返回查询/结果仍保留。只读定位：941 与夹具位置自洽，偏差在 `ReaderProgressEffects.targetPercent`/`ChapterPaginator` 的"偏移→百分比→分页首屏"映射；缺 route legacyOffset / totalChars / absoluteOffset 三个观测值，建议加 instrumentation 核五值链（交 Root，未改源码）。恢复 EXACT MATCH（历史 ZIP 3555f331… 保留）。**新增登记=`5ef408d7…` 首屏精确定位存在偏页缺陷**；既有远页可翻页到达/查询84/日志/A1-A6 证据不变。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-first-landing-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-FAR-PAGE-v1 收官（ZCode，真机远页搜索定位）

真机 c49ac6cf 空闲门成立，装 `5ef408d7…` 装机 SHA 回读逐位一致；沿用既有隔离测试 MD（历史 ZIP 按精确路径保留未动）。普通打开 0.0% 证 `84v1-MD-20` 不在初始页；查询 `20`（`input text` 一次送入、提交前 dump 逐位核 `'20'` 正确；长串 `84v1-MD-20` 被 WeType 改写为首页命中已按合同弃用）→ 正文=1 "第 2268 字附近" → 点击落 50.1%（远页）→ 一次左翻页 46.3% **第20节 `【 84v1-MD-20】` 完整可见** → Back 回搜索页 `20`/结果卡保留=业务 **PASS_WITH_NOTE**（目标标记需一次翻页可见，不夸大为点击即见）。恢复逐键 EXACT MATCH（历史 ZIP sha256sum=3555f331… 保留、前台回桌面）。`5ef408d7…` 新增"真机远页搜索定位"已验范围；全标记串注入受 WeType 限制（设备输入边界），更严口径需用户手工输入该一个查询。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-far-page-v1/`。

## 2026-11-12 RDS-R8-ZCODE-LOCAL-COMMIT-v1 收官（ZCode，本地提交）

按用户精确授权完成**一次本地提交**：`dbe2bc08e4f5676ae671569a36bf0f155c24ef72`（父 `cb18a7b79ab9ec54fe2d159750ded1784dc3b5fc`，标题 `feat(android): integrate validated reader and data fixes`）。提交树恰好 45 个授权 Android 文件（A=19/B=9/C=6/E=11，排除 2 份 TalkBack 探针），45 项 SHA-256 与审查清单逐项一致，`git diff --cached --check` rc=0、暂存 blob 内容 45/45 无漂移（CRLF 按 Git 属性规则解释）。提交后核验：父提交正确、暂存区=0、工作树剩 26 项（D 组 23 文档+2 探针+根 `0`）且内容与开工指纹 0 变化（含 Root 发现的 current.md 指纹变化，留树对账）。冻结 APK 绑定不变：`5ef408d7…` 现行候选、`fc600e8c…` R8 真机 A1-A6 基准；远页定位/TalkBack 遗留沿用。**未 push/发布/构建/设备/资产清理**；本交接更新留在工作树，未纳入提交。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-local-commit-v1/`。

## 2026-11-12 RDS-R8-ZCODE-COMMIT-REVIEW-v1 收官（ZCode，只读提交审查）

只读核定提交范围：HEAD=`cb18a7b`，暂存 0，工作树 71 项（旧 67 项草案 +4 份路径迁移文档：`antigravity-next-prompt-v2/antigravity-start-prompt/continuation-playbook/next-codex`，纯 `E:/creation-reading-assistant/...`→`E:/AgentWork/CRA/...` 改写，消失 0、真状态变化 0；旧清单无 SHA 仅存在性/状态可比）。**两处修正旧草案**：① 全树实查 D `app/src`(1038) 与 `5ef408d7…` 候选源(1038) `content_diff=0` → E 组（UIX/书架/分页 5 生产文件）**必含**，排除即缺现行候选生产差量；② 逐 hunk 核 ReaderViewModel/Models/Route/Args/SheetHost 全属 A 的 InspirationSaveOutcome 信号链、符号闭包仅 A → 组1/2 可独立提交。推荐顺序=1 A阅读附页（排 2 个 TalkBack 探针）→2 B备份灵感→3 C搜索→4 E既有交互→5a 未跟踪文档→5b current.md+4 路径迁移文档（最后）；`git diff --check` rc=0、符号闭包互不相交、18 份文档无真凭证/PII（2 份本机路径待用户确认）；**所有中间提交编译状态=待验证**（本轮禁构建）。可批准范围=仅本地 commit，须 Codex Root 核定+用户批准；本轮未暂存/提交/改源码/动设备。交付：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-commit-review-v1/`（COMMIT-PLAN.md/REPORT.md/ready.json/指纹清单）。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-PHONE-HANDOFF-v1 收官（ZCode，真机补测+离线交接草案）

真机 c49ac6cf 14:18 三次采样桌面空闲→开工门成立；装 `5ef408d7…` 装机 SHA 回读逐位一致；BAD_ZIP_1 种子中性 MD，先证初始页无远页标记→搜 `84`→正文命中→点击→reader 落点经正文锚点 `【84v1-MD-01】` 实核→Back 查询/结果保留=A PASS；B 端到端（内部登记 NOT_VERIFIED）；C=时间窗 14:29:13→14:32:29 CST logcat（app pid 10318/7192 行+全 buffer 171114 行，**全程未执行 `logcat -c`**）`CRA_SEARCH_DIAG` 命中 0=PASS。远页独特查询 84v1-MD-20 因真机 WeType 注入改串=NOT_RUN（设备输入边界，已登记）。恢复逐键一致；收尾 rm 误删基线历史散份 cra-backup-missing-state.zip 后已按 SHA 同源件重推复原（3555f331… 逐位一致），全过程在 phone-restore.log 补记。**`5ef408d7…` 新增登记=真机搜索补测通过**；`fc600e8c…` 保持 R8 真机 A1-A6 历史基准。**离线提交切分草案**（`COMMIT-SPLIT-DRAFT.md`，只读）：67 项 WIP 分 5 组，组1阅读附页/组2备份灵感共享 ReaderViewModel/Models hunk 须同提交，组3搜索、组4文档、组5其他 WIP 建议不纳入；暂存/提交/推送等仍待 Codex Root 新授权。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-phone-handoff-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-DEVICE-v1 收官（ZCode，MuMu 模拟器验收）

用户授权切换设备（真机 c49ac6cf 被连续占用，HyperOS 装机弹窗被前台用户活动顶掉→`INSTALL_FAILED_USER_RESTRICTED`）。MuMu instance 1（serial `127.0.0.1:16416`，control -v 1 launch 后 start_finished）装 `5ef408d7…`（装机 SHA 回读逐位一致）；BAD_ZIP_1 种子中性 MD 夹具→搜 `84`→正文命中（第54字附近）→点击→reader 落点经**正文锚点 `【84v1-MD-01】`**实核→Back 回搜索页查询/结果保留=A PASS；B=端到端行为 PASS（内部请求登记 NOT_VERIFIED 如实限定）；C=时间窗 05:01:37→05:04:57Z logcat（app pid 5108/1270 行+全 buffer 4124 行，未清全局）`CRA_SEARCH_DIAG` 命中 0=PASS。恢复逐键一致（包缺席/boot_id/8设置/Download190/前台）。**`5ef408d7…` 登记为已通过本轮搜索模拟器补测的现行候选**；`fc600e8c…` 继续为 R8 真机 A1-A6 历史基准。真机同流程 NOT_RUN；MuMu 收尾保持运行（开工为关闭态，未擅动共享资源）。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-device-v1/`。

## 2026-11-12 RDS-R8-ZCODE-DIAG-CLEAN-FREEZE-v1 收官（ZCode，离线）

R6 裁决执行完毕：产品源码 9 条 `CRA_SEARCH_DIAG` 临时日志（含查询词/bookId/route）已全部移除（SearchIndexRepository×2/SearchScreen×5+括号恢复/ReaderRoute×2），生产源零残留；`SearchHitRankerMetadataFilterTest.kt:12` 历史原证注释按裁决保留。清理后 D→E 逐字节同步（全树 content_diff=0 绑定不变），定向 JVM **191/191 rc=0**（搜索族 184+接线 7）+ `assembleDebug` rc=0 → **新候选 `5ef408d7…`/53,957,267B 冻结**（离线验证通过，待设备补测：搜索点击定位返回主项+临时查阅链+logcat 无诊断负例）。**已验 `fc600e8c…` 二进制已于构建前保全**（`evidence/preserved-fc600e8c-app-debug.apk` 逐位一致），其真机 A1-A6 证据按保全路径继续有效。D 树 WIP 67 项（含本轮 3 文件清理）未 commit。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-diag-clean-freeze-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SOURCE-CONVERGENCE-v1 收官（ZCode，离线）

R8 后 D/E 源码收敛完成：10 文件清单+ReaderRoute 共 11 文件按 E 已验内容回补 D（逐文件判定 APPLIED，before/after/E 三方 SHA 台账与 36,219B 最小 patch 在 `E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-source-convergence-v1/`）。**绑定验证：D `android/app/src`(1040) vs 已验候选 `fc600e8c…` 源树(1038) content_diff=0、E 独有=0**——唯二 D 独有=2 个 TalkBack 探针测试（androidTest-only 不进产品 APK）；R8 的 JVM/构建/真机证据经字节等价绑定适用 D 回补结果。CRA_SEARCH_DIAG 判定=临时诊断埋点（非产品需求），随字节绑定保留（D 树 SearchIndexRepository 本就保留同族埋点且为新测试注明的根因定位原证）；统一去留留待 Root 清理批。`git diff --check` rc=0；D WIP 57→67（=回补集）；零构建零设备；冻结 APK 原位实算一致未动。

## 2026-11-12 RDS-R8-ZCODE-OVERNIGHT-v1 收官（ZCode 唯一执行者+接任协调者）

共同候选已核定并冻结：`fc600e8c…`/53,957,267B（debug，E candidate 工作区构建，装机 SHA 回读逐位一致），**取代 fa5ee9db 为现行唯一候选**（fa5ee9db 降前代只按 SHA 引用；其二进制无保全副本=G-R8-1 如实登记）。内容绑定=fa5ee9db 全部源 + reader-pages 20 文件集 + UIX 四文件 + ReaderRoute 三方合并（D↔E 29 文件分歧全分类，evidence/candidate-audit/ 含 before/after SHA 台账）。定向 JVM 191/191 rc=0。真机 A1-A6 全过（BAD_ZIP_1/2 独立空库导入三方一致、导出对 checker PASS rc=0、灵感保存落库、四入口开合、来源回跳），零 pm clear，每次安装即回读 SHA。恢复 PASS 带缺口 R8-G2（基线服务串含系统级坏引用 `mipay…Companion` 粘连，重绑后被系统归一化剪除，坏组件不可绑定亦不可字节还原；GKD 正常条目已回写一致）。报告/合同/ready：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-overnight-v1/`。待 Root：登记 fc600e8c；D↔E 收敛时按 after-sha256.txt 台账回补 E 侧 11-06 交互修复 10 文件（D 树现缺）。

## 2026-11-10 READER-PAGES-TALKBACK-v1 Root裁决

本轮执行结束，专项保持 PARTIAL。朗读日志支持 TalkBack 基础功能。目录、外观、设置、灵感的界面动作结果按普通交互证据保留，四入口逐控件聚焦、朗读及经 TalkBack 激活的完整链路待验。停止重复脚本试跑。原冻结产品包及真机主流程验收结论继续有效。

本地产品包复算为 `98b629ea…`。本轮报告声称装机哈希一致，但所引 install-identity.log 无哈希行。恢复快照可比的动画、字体与服务串相符，收尾快照首项为 1，开工旋转首项为 0，需核对命令顺序及实际值。权限、卸载与 Download 计数在收尾快照缺同口径原始输出，暂按报告自述保留，不接受逐字一致。优先补现有原证并核清旋转，免重装和重测。报告见 `E:/AgentWork/CRA/reader-pages-talkback-v1/REPORT.md`。

## 2026-11-10 READER-PAGES-DEVICE-v1 Root裁决

关闭本批真机主流程验收。产品APK实算及装机回读均为 `98b629ea…`，接受真实键盘与草稿保护、实际横屏和四入口短回归。TalkBack保持PARTIAL，四入口逐节点聚焦、朗读、激活矩阵另列待验。报告与原证在 `E:/AgentWork/CRA/reader-pages-device-v1/`。

交付校正：开工测试APK记录为 `4b0177c9…`，当前默认路径实算为 `52abd4af…`，有assembleDebugAndroidTest日志，故“未重建”只能用于已核对的产品APK身份，不接受测试包全程冻结表述。前后快照共同记录的旋转、动画、字体和服务串值一致，字段覆盖不同，恢复整体以交付报告为限。该交付差异不推翻使用冻结产品包完成的三项交互结果。旧MuMu组件证据仍绑定原测试包。

## 2026-11-10 READER-PAGES-v1 Root验收关闭（MuMu已验范围）

Root复算最终APK对及20个源码SHA，核对110份JVM XML（984项全过）、最终构建日志和6项MuMu组件测试输出。P1保存锁定通过，接受长标题展开和组件动效补验。关闭本批实施及已验范围，详见[最终复核](../coordination/2026-11-09-reader-pages-v1-root-review.md#2026-11-10-最终复核)。真机、真实IME推顶、实际横屏、TalkBack保持NOT_RUN。首轮font_scale存储值差异保留，修正轮以自身基线核对恢复。以下为历史交付记录。

## 2026-11-09 READER-PAGES-v1 集中修正交付记录

[Root复核](../coordination/2026-11-09-reader-pages-v1-root-review.md) 的P1必修项已落实：灵感保存期间**全页编辑锁定**（标题/正文readOnly、标签/分类/新建锁定、放弃确认双重守卫、保存onClick在途复查）落地于 `ReaderInspirationSheet.kt`，并以可控延迟（frozen outcome，无sleep）androidTest 模拟器实测 `ReaderInspirationPageSaveLockTest` 3/3 OK（锁定键入无效/连点单次派发/返回不开放弃确认且显示"正在保存"/失败解锁留稿/重试同id/成功回调序）。有限补验：TOC长标题长按展开 1/1 OK（101字，不跳章）；容器180/140ms动效以 mainClock 停表+captureToImage 逐帧验证 2/2 OK（入场90ms中间帧、可取消、退场中间帧、减动画0ms即终态），替代易竞态的screenrecord。**NOT_RUN如实保留**：真机（用户占用；connected测试曾触发HyperOS拒装未安装成功，已改手动仅对模拟器instrument并记录）、IME推顶视觉（MuMu IME窗口零高度）、横屏真旋转（MuMu竖屏锁定）、TalkBack、防重复设备观察。最终APK debug `98b629ea…`/androidTest `4b0177c9…`；JVM定向 984/984（最终树重跑）；lint 0e/9w。报告 `E:/AgentWork/CRA/reader-pages-v1/REPORT.md`（§3=P1修复，§7=口径更正：已实现/已运行/待验三列制；baseline证据补原始命令输出级对账；完整64位SHA×20；§9=未验项）。模拟器基线恢复且关机；未commit/push；改动清单见 ready.json。等待Root按实际已验范围复核关闭。

## 2026-11-09 READER-PAGES-v1 首轮Root复核记录（已由上方裁决替代）

Root已核四入口源码、APK对、登记SHA前缀、110份定向JVM XML合计984项全绿及3张布局截图。实现与已有模拟器证据有限接受，尚不全面关闭：灵感保存中仍可继续编辑/放弃，旧Saved无条件清稿关页存在后续输入丢失窗口，必须按 [Root复核及唯一集中修正](../coordination/2026-11-09-reader-pages-v1-root-review.md) 锁定在途修改/返回并补延迟行为测试。IME、横屏、连续动效与部分恢复证据仍有限。其他页面不重新设计、不全量重跑；原实现者ZCode沿原合同完成这一次集中修正。以下交付自报按该裁决读取。

TOC/SETTINGS/THEME/INSPIRATION 四入口已按 [实施合同](../coordination/2026-11-08-reader-pages-v1-implementation.md) 改造为 ReaderPageDialog 全屏附页并真实接线；灵感保存完成信号（Saved/Failed+seq）经 VM→ReaderScreenInputs→附页闭环。最终APK `7849269f…`(debug)/`c0c3683a…`(androidTest)；JVM定向 984/984；lint 0e/9w。**模拟器验收**（MuMu instance 1）：四入口交互/220章目录/搜索定位/位置跨进程保存/草稿确认/保存落库闭环/夜读纸/320dp/1.5字阶/减动画全过；真机因用户占用未动（零改动）；未验项（IME推顶视觉、防重复观察、横屏真旋转、TalkBack、androidTest运行）见 REPORT §6。实现期修复4缺陷（预览空文字/分类丢失/容器漏底/长按跳章）+1个落地谱系预存在的 androidTest 编译错误机械修复（SaFTree 3行）。报告 `E:/AgentWork/CRA/reader-pages-v1/REPORT.md`（含 ready.json/patch/SHA/截图/恢复原证）。模拟器基线已逐项恢复并关闭；工作树未 commit/push；改动清单见 ready.json，与既有WIP无文件交集。下批如需真机复验，从该报告§6未验项进入。

## 2026-11-08 阅读附页实施（已准备，用户转交ZCode）

用户已授权按Root确定的具体设计修改代码。新任务 [READER-PAGES-v1完整实施合同](../coordination/2026-11-08-reader-pages-v1-implementation.md) 和 [ZCode读取执行词](zcode-reader-pages-v1-prompt.md) 已准备，共7项顺序工作，覆盖TOC、THEME、SETTINGS、INSPIRATION四入口。方案为索引附页、真实正文预览调节页与摘录旁写页；单ZCode owner闭环，不自动发送、不恢复自动化。本次仅准备文档，尚未实施/构建/设备验收；UIX旧证据核清范围继续独立保留，不把新设计任务误认为旧缺陷返修。原工作树保护，构建/设备各单owner，输入以执行前实核为准。

## 2026-11-08 UIX 收尾校正（本范围最新）

UIX第七轮 `d9c5adcf…` / 52,812,949B 已由ZCode交真机节点复验。Root已解析两份原始XML，确认整理入口clickable和底栏五钮节点恢复、顶栏/scrubber描述存在；停止新增产品修改。当前裁决为 `NODE_RECOVERY_VERIFIED_BEHAVIOR_EVIDENCE_PARTIAL`，尚缺六控件实际激活及部分恢复原证链接，TalkBack未运行。按 [本次Root复核与唯一接续动作](../coordination/2026-11-08-uix-closure-root-review.md) 先复用现有证据，必要时仅补有限激活验证，不全量返工，不凭XML预设朗读通过。报告位于 `E:/AgentWork/CRA/uix-v1/closure-v1/REPORT.md`。原 [有界收尾合同](../coordination/2026-11-08-uix-bounded-closure.md) 保留。用户手动转交，手机仍单owner，自动化不恢复。

下文为11月4日起历史阶段口径，不能据其“工作清零”推断后续UIX或全产品已完成；具体历史结论仍按对应报告范围读取。

## 2026-11-04 阶段记录

**项目状态：两产品线全部标准质量门绿，可自主执行工作清零，剩余全部为用户决策项。**

- **Android（MuMu 口径）**：V0 八卡（AR01—08）全部真实闭合，总裁决 `V0_ACCEPTED_WITH_SCOPE_DEVICE`（裁决书 `E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar08/REPORT-20261103-final.md`）；JVM 3808/3808、设备批 AR03/04/05 + NB03 图测试 + NB04 SAF 文件渠道全 PASS、166+R2 恢复全绿、lint 0 errors/7 warnings。
- **桌面**：设置重构+设置搜索 WIP（20 路径未提交）**功能已完整**（tsc×3 + vitest 981/981 + 契约 54/54 + electron-vite build 全绿，审计 `next-v2/nb14-desktop-wip-audit/REPORT.md`）；打包验收**全绿**（stage1 族 2/2 + stage3/4/4b 43/43 checks，经 NB16 维护：脚本原生支持 CREATION_PACKAGED_DIRECTORY 覆盖、stage4 app.close() 挂起已修；`next-v2/nb15-desktop-packaged-acceptance/` + `nb16-stage-scripts-maintenance/`）。
- **NB 线台账**：NB01 成因调查（无法归因，保护门守护）/ NB02 裁定不跑 / NB03 闭合 / NB04 闭合 / NB05 盘点 / NB06 文档 / NB07-09 闭合 / NB10 闭合 / NB11 BLOCKED_ENVIRONMENT（MuMu 树确认按钮被导航栏遮挡，测试源带诊断保留 candidate）/ NB12-13 闭合 / NB14 审计 / NB15 PARTIAL。报告全在 `E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/`。

## Root 决策批（2026-11-04 已裁决，[决策文档](../coordination/2026-11-04-root-decision-batch.md)）

原待决事项及盘点新识别事项已由 Root 全部裁决（用户 2026-11-04 委任决策），待 ZCode 按文档执行：

1. **RD-A 桌面提交**：授权 commit 20 桌面路径 + 桌面验收脚本（全门绿证据），不 push。
2. **RD-B 文档/配置提交**：授权提交文档重组成果与交接记忆，`.gitignore` 增补排除会话工件（`dump_secrets*.xml` 等只忽略不删除），不 push。
3. **RD-C Android 源码**：授权只读盘点 + 锚点核对（6be3b01b pin、D↔candidate diff 归类）；全过才条件提交，任一不符即停报告。
4. **RD-D NB02**：维持不跑（原红保留，208 覆盖同类边界）。
5. **RD-E SAF 渠道**：产品路径保留，仪器测试挂起；NB11 裁决降格为工作假设。
6. **RD-F 巡检自动化**：维持暂停；重建需单 ID + 新鲜 prompt 快照。
7. **RD-G 证据卫生**：授权离线小批修 NB04 字节数/措辞/重钉、NB11 降格披露、ar03 撤销注记（原证只增不改）。

执行顺序 RD-A → RD-B → RD-G → RD-C；报告落 `E:/AgentWork/CRA/coordination/root-decision-batch-20261104/REPORT.md`；全部 commit 仅本地，不 push。

**RD-C 分歧裁决（2026-11-05 补）**：RD-A/B/G 已执行（commit `3751b41` / `35ed1a5`），RD-C 锚点核验证伪了原合同假设——恢复成果唯一载体是 E candidate（only-in-E 123 文件 = 207/209/NB 线成果；D 树侧 89 个差异为 10 月初独立演进，验收归属无法建立）。裁决取落地路线：[RD-C-LAND 合同](../coordination/2026-11-05-rdc-landing.md)——先 archive commit 保全 D 树分歧谱系，再整体落地 candidate 快照并以 treediff 零差异为硬门；恢复期"不回灌 D"隔离措施于收官解除（理由见合同）。落地后 D 仓库为后续源码主载体，E candidate 转为冻结已接受基线（只读参照）。

## 设备与权限（仍然有效）

- MuMu instance 1 启动/验收/各批 166+R2 恢复已获用户同意（[权限纠正](../coordination/2026-11-02-mumu-acceptance-authorization-clarification.md)）；serial/boot 由执行者 fresh 探测，禁止转向实体手机，一切设备证据只能称模拟器。
- `android-zcode` 保持 PAUSED；接任会话连续执行不是恢复该自动化。
- 无 Git 暂存提交 push PR 发布、既有资产删移、D 产品回灌、pm clear/盲恢复、正式库/私库 session DB 或记忆写入许可。WIP/DB-WAL-SHM/原红/唯一证据/冻结 APK/恢复资产保留。
- 接任 Root 由用户委任，可承担普通技术决定、最小因果修复和验收；公共 candidate/Gradle/设备各单 owner，Gradle 持 `E:/AgentWork/CRA/locks/android-gradle-exclusive.lock` 串行。
- 新业务设备批前置：单 owner、fresh 身份链、原 APK/data/userState/八设置/保护根/创建物基线及 166+R2 恢复准备；保护查询后最终 identity；已成功卸载/恢复不为补证重做。
- 判伪纪律：**0 项 instrumentation 不构成通过证据**；业务真伪三件套=stdout 时长量级对照基线、logcat start/finished instr 链、testApk SHA 三方（磁盘/冻结 manifest/capture）互核。

## 产品与实际候选（2026-11-04 实核）

D 仓库：`D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919`；唯一 candidate：`E:/AgentWork/CRA/task59-common-candidate-v1/candidate/android`。不 D 回灌、不复制工程/缓存。

| 输入 | 2026-11-04 现态 |
|---|---|
| 产品 APK | 53899310B / `c396ebe1…`（未变，接受输入） |
| androidTest APK | NB04 当批 `1555fc7f… / 10760068B`（SAF/SAFTREE 测试；其二进制未被单独保全，身份由 summary/capture/evidence-manifest 钉扎）；更早的 95e734bc 冻结包二进制保全于 `next-v2/nb04-saf-journey/artifacts/`；盘上现态已被 NB11 系列重建覆盖为 `6e2c171b… / 10768258B` |
| 打包产物 | `release-beta/win-unpacked`（NB15 构建绿）+ `release-beta-stage4-final`（NB15 构建绿） |
| 证书/runner | SHA256 `05249d78…`，`CraIsolatedTestRunner` / `com.creationreadingassistant` |

## 已有业务与恢复边界（复用，不重跑）

| 任务/批次 | 可沿用结论 |
|---|---|
| 205-v1 | 普通退出 shelf-screen 超时原 FAIL 保留（T165V1-20261030-150607）；新输入不复现不抹旧红 |
| 206-v1 | recover-only 有限关闭；原 query rc/userState/绑定等限制保留 |
| 207-r3/r4 | 源码/编译及当批 APK 对限定接受，不扩全产品 |
| 208 (162905) | 普通退出/书架重开单方法 OK；接受范围见 task208 REPORT |
| 209 A/B/D | A 图 7 项 PASS（NB03 已在冻结包复验）、B/C1 生产组合（NB03 批B 复验） |
| E151729/P5 165519/I190927/C-R2 210503 | 各自既有接受范围保留 |
| AR03/04/05 (11-03) | T165V1-20261103-114643/120753/121822，全 PASS+UNINSTALLED |
| NB03 A/B (11-04) | T165V1-20261103-234022（A：PASS+recover-only 有限收口）/235956（B：全绿） |
| NB04 (11-04) | T165V1-20261104-015548，SAF 文件渠道 PASS+UNINSTALLED |
| R2 合同 | gen10106-v1（inventory 8b2e3317、test 包 0d18、保护 71/820）；恢复优先于新 mutation |

业务 scope 根：`E:/AgentWork/CRA/task192-reader-internal-lifo-host-offline-v1/attempt-<runId>/`；NB 交付根：`E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/`。runtime：`E:/AgentWork/CRA/task209-navigation-coverage-v1/inventory-repair-r2/scripts/`（preflight_209_r2 → prepare_inputs_209_r2 --batch X → run_batch 驱动；AR02 frozen manifest 门覆盖 EP/C/I）。

## 阶段史（详情按需取，不整份加载）

- 11-02 Antigravity AG-NEXT 八卡离线交付 + Root 复核（[队列](../coordination/2026-11-02-antigravity-next-queue-v2.md)、[复核](../coordination/2026-11-02-antigravity-stage-delivery-review.md)）。
- 11-03 夜间 ZCode 接任：保护基线 18 文件恢复 → AR03/04/05 真实闭合 → AR08 终裁 V0_ACCEPTED_WITH_SCOPE_DEVICE。
- 11-04 Wave1-5：NB01-15（见头部 NB 线台账）。其 AR03-05 设备报告被审计证伪：`E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar03/RETRACTION-20261103.md`（原文件保全）。
- 11-04 收官+全面复审+NB16 打包验收闭合后，Root 决策批七项裁决落盘（[RD-20261104](../coordination/2026-11-04-root-decision-batch.md)），ZCode 执行 RD-A/B/G/C。
- 11-05 RD-C 分歧裁决：恢复成果载体实测为 E candidate；授权 archive+落地两段式入库（[RD-C-LAND](../coordination/2026-11-05-rdc-landing.md)）。

## 执行与状态入口

- state：`E:/AgentWork/CRA/coordination/android-zcode-luna-state.json`（写前核指纹合并；readySHA 实算）。
- 夜间巡检：`E:/AgentWork/CRA/coordination/zcode-night-watch-20261103/`（heartbeat+progress+各 run 日志）。
- [接手说明](next-codex.md)、[通用手册](continuation-playbook.md)、[单任务模板](next-agent-start-template.md)、[下一批规划](../coordination/2026-11-03-next-batch-proposal.md)。旧路径查 [document-map](../document-map.csv)。
