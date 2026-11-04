# 阶段 B 窗口 D：S05/S06 书架与阅读核心旧问题对账（只读）

- 仓库/分支/HEAD：`creation-reading-assistant-recovery-20260919`｜`codex/recover-worktree-20260920`｜`5aed5939…`（未变动）
- 输入：`docs/code-review/2026-09/2026-09-20-android-s05-shelf-maturity-review.md`、`…-s06-reader-startup-library-slider-review.md`、`2026-09-22-current-task-ledger.md`、`plans/2026-09-22-android-recovery-convergence-plan.md`
- 方法：从用户入口读当前工作树代码与测试，逐条给 file:line；未运行 Gradle/ADB/设备，未改任何代码。行号为 2026-09-22 工作树快照，共享脏区可能被他窗口继续移动。
- 分类口径：仍存在（有当前代码链路）／已修复（源码+证据）／无法确认／产品决定。

## 一、S05 书架（9/20 报告 7 条）

| # | 旧问题 | 分类 | 当前证据 | 影响与最小修复 |
|---|---|---|---|---|
| S05-① | 取消读完/恢复在读后仍归「已完成」 | **仍存在（P1）** | `BookRepository.kt:329-344` `setReadingState` 只改 `completion_state`、清 `completed_at`，不动 `progress_percent`；`markFinished` 在 `:358-371` 把进度抬到 `coerceAtLeast(100f)`；`ShelfModels.kt:91-97` 判定顺序为 SHELVED→FINISHED→`p>=99.5`→… | 用户收到「已取消读完」，书仍留在已完成分组、徽章仍是「完」。需先定契约：显式 READING 是否覆盖百分比（改 `bookStatus` 顺序），或取消时回落到保存前的进度（需在 markFinished 保存原值）。**先裁决再实现** |
| S05-② | 旧 READABLE 动态视图静默空列表 | **仍存在（P1）** | `ShelfViewModel.kt:207-217` 按枚举名原样恢复 `READABLE`；`ShelfModels.kt:268-269` 用 `bookStatus(...) == READABLE` 过滤，而 `bookStatus` 永不返回 READABLE（`:110-116` 分组条也已剔除） | 历史「本机可读」视图能加载、无报错、结果恒空。最小修复：加载旧视图时把 READABLE 映射到「可用性维度」过滤（`readiness()`），或迁移+一次性提示。当前无任何兼容代码 |
| S05-③ | 状态徽章与叠加筛选后的列表不一致 | **仍存在（P2）+ 契约自相矛盾** | `ShelfUiState.kt:112-113` 计数基底是 `shelfBooks`（全库）；`ShelfScreen.kt:430-436` 分组条常驻、不随筛选隐藏；`ShelfModels.kt:121-122` 仍写「徽章数与列表长度必然一致」，而 `:124-126` 又声明不叠加搜索/格式/书单 | 叠加书单/格式/搜索时「已完成 30」点进去只有 1 本。二选一：计数改为「除状态外的当前筛选基底」，或删掉 `:121-122` 那句一致性承诺并在 UI 标注「全库计数」。属**产品决定**，但注释矛盾本身是 P2 |
| S05-④ | 导入后「查看」不绑定本批、筛选下无响应 | **仍存在（P1）** | `ShelfRoute.kt:173` `focusNewBookId = newBookIds.firstOrNull()`（全局持久集合，非本批）；`ShelfScreen.kt:117-123` 目标不在 `filtered` 时直接 `return`，**不调用 `onFocusHandled()`** | 可能滚到上一批未看的书；被筛选排除时静默无反应且 focus id 长期残留，之后改筛选会突然跳到旧书。最小修复：`ShelfImporter` 保留本批成功 bookId 列表并优先取其中可见的一本；不可见时清除会排除它的筛选或提示原因；`return` 前也要清理 focus 状态 |
| S05-⑤ | 「查看」缺高亮与减少动画降级 | **仍存在（P2）** | `ShelfScreen.kt:122` 只有 `animateScrollToItem`；`focusBookId` 仅参与滚动（全仓 grep 无绘制引用）；`rememberReducedMotion()` 只用于触觉（`:111`） | 无视觉锚点；系统「减少动画」开启时仍做动画滚动。修复：`reducedMotion` 时走 `scrollToItem`，并给目标卡一帧短暂描边 |
| S05-⑥ | 「最近加入」文案三入口两套旧名 | **仍存在（P3）** | `SortSheet.kt:49` 「导入时间」；`ShelfScreen.kt:548`、`ShelfOrganizerRoute.kt:770`、`ShelfSelectionRoute.kt:359` 「最近导入」；`ShelfUiState.kt:49` 注释写「最近加入」 | 纯文案收敛，单文件小改，不必单独验收轮 |
| S05-⑦ | 批量标记 API 半接线 | **仍存在（P2，工程风险非产品故障）** | `ShelfRoute.kt:715-724` 有 handler，`BatchSheet.kt:75-169` 六个胶囊仍无「标记读完/取消读完」；`grep BatchMark(Finished|Reading)(` 在 UI 侧**零发送方** | 死接口 + 易被误判「P3 已实现」。建议：要么补 UI 入口与失败语义，要么本轮移除两个 action |

## 二、S06 阅读核心（9/20 报告 9 条）

| # | 旧问题 | 分类 | 当前证据 | 影响与最小修复 |
|---|---|---|---|---|
| S06-① | `system` 被映射成 `MODE_NIGHT_AUTO` | **仍存在（P1）** | `SettingsStore.kt:371-377` `else -> MODE_NIGHT_AUTO`；调用点 `App.kt:124-128`（启动）与 `SettingsStore.kt:593`（改外观） | 系统明确浅色时应用仍可按时间/位置入夜。需为「跟随系统」选正确表达（不传 AUTO，或让系统派生 + 应用侧不覆盖）。测试 `MODE_NIGHT` 零命中 |
| S06-② | 升级用户启动镜像永不回填 | **仍存在（P1）** | 镜像只在 `updateAppearance` 写（`:589-601`）；`bootAppearance` 读镜像（`:420-422`），缺失即 `"system"`；`init{}` 只有 `migrateLegacyReaderDefaultsOnce()`（`:401-404`），无主题回填 | 老用户 DataStore=dark 但镜像缺失时冷启动首帧仍错色，除非重新点一次主题。最小修复：首次读到 DataStore 主题且镜像缺失时一次性回填（幂等，不阻塞启动） |
| S06-③ | 小 TXT 滚动/防抖章号写回第 1 章 | **仍存在（P1）** | `ReaderScreenDerivedState.kt:63-66` 的 `chapterStartOffsets` 回退链为 bookIndex→markdown→streaming→`emptyList()`，**不含小文件 `txtChapters`**（后者在 `:82-100` 已算出）；`ReaderProgressEffects.kt:222/274/617` 用该空表调 `EpubLocatorMapping.toChapterOffset`，`EpubLocatorMapping.kt:15-28` 空表返回 `ci=0` | 仍写「第1章」。`extractChapterLabel`（`ShelfModels.kt:286-292`）测试零命中。修复：把 `txtChapters.map { it.startOffset }` 纳入同一条真源链；测试断言最终落库实体而非中间值 |
| S06-④ | 搜索后全选/反选作用于隐藏文件 | **仍存在（P1）** | `LibraryBrowserViewModel.kt:94-131` 的 `uiState` 才按 `query` 过滤；`:282-291` `selectAllVisible()`/`invertSelection()` 用未过滤的 `core…files`；`:298-301` `selectedUris()` 同样按全量映射 | 搜到 1 个文件点「全选」会导入整个目录。修复：以当前可见 documentId 集合执行，并定义搜索词变化后隐藏选择的去留。两个函数测试零命中 |
| S06-⑤ | 动画期间连续快点被吞 | **仍存在（P1）** | `PageTurner.kt:338-341` 先 `onTurnRequestConsumed()` 再异步 `settle`；`settle` 在 `:260-261` `if (turning || direction == 0) return`；全文件无 `pending/queue` 关键字 | 第二次点按既不排队也不生效。修复：定义「最多一个待处理方向」或显式队列，并补「连点 N 次推进 N 页」状态测试 |
| S06-⑥ | 高速翻页测试只查像素 | **仍存在（P2）** | `PageTurnerHighSpeedTest.kt:91-103` 三个用例均为 `…NeverShowsABlankFrame`；`PageTurnerTest`/`StaticSurface` 亦无页号断言（`assertEquals` 计数 0/0/2） | 无「推进正确页数/内容身份」门禁，与 S06-⑤ 同源未覆盖 |
| S06-⑦ | CraSlider 轨道不随 RTL | **仍存在（P2）** | `CraSlider.kt:113-145` Canvas 恒从 `x=0` 画 `activeW`，未读 `LayoutDirection`；拇指由 M3 Slider 绘制（会镜像） | RTL 下拇指与已走段方向相反。`CraSlider` 测试零命中 |
| S06-⑧ | 「最近添加」与「修改时间」实现相同 | **产品决定（残留 P3 事实风险）** | `LibraryBrowserComponents.kt:313-316` 明确注释保留两项并说明「拷入时修改时间即拷贝时间」 | 该前提对 MTP/同步盘/保留时间戳的复制不成立。若要真「最近添加」需应用侧首次观察时间；否则建议只留「修改时间」 |
| S06-⑨ | 章号/滑杆缺真实风险测试 | **仍存在（验证缺口）** | `extractChapterLabel`、`CraSlider`、`selectAllVisible/invertSelection`、`MODE_NIGHT` 在 `app/src/test` 与 `androidTest` 均零命中 | 与 S06-③④⑤⑦ 一一对应；按第四列给的最小定向测试补即可，不需全量计划 |

## 三、已修复项（勿重复开发）

| 项 | 证据 |
|---|---|
| `bookStatus` 不再把「本机可读」放在进度前（历史 COMPLETED 死分支） | `ShelfModels.kt:73-98` + `ShelfStatusCountsTest.kt:37-62`（含 100% 归已完成、SHELVED 优先） |
| `markFinished` 保留首次 `completed_at`、当前位置，且不刷 `last_read_at` | `BookRepository.kt:346-371`；`ShelfBookActions.kt:84-89` |
| 状态分组条进入首屏、单书长按面板按当前状态给互斥动作 | `ShelfScreen.kt:430-436`；`BookActionSheet.kt:237-276` |
| 搜索页已传 `completion_state`，不只看百分比 | `ShelfModels.kt:269` 调用侧 `progressById[book.id]?.completion_state` |
| 启动镜像「首帧取存量」链路（冷启动不再闪默认值） | `SettingsStore.kt:418-439` + `SettingsStoreBootMirrorTest.kt:57/73/88` 三个用例（注意：只覆盖首值读取，未覆盖 S06-①②） |
| 翻页动画期间 tap 死锁（settle 不被自身 key 取消） | `PageTurner.kt:342-347` 注释与 `rememberCoroutineScope` 实现 |
| 目录排序不再回读滞后的 DataStore StateFlow；搜索同时过滤目录与文件 | 9/20 报告「已确认的正向结果」，本轮未见回退 |

## 四、本轮新发现（窗口 D 前缀，均与旧条目同源，不另计新缺陷）

- **D-S05-a（同源 S05-④）**：目标不可见时 `focusNewBookId` 永不清空，形成「陈旧定位 + 之后任意筛选变化就突然滚动」的二次症状。`ShelfScreen.kt:117-123`。
- **D-S05-b（同源 S05-③）**：一致性承诺与「不叠加筛选」声明同时存在于同一 KDoc（`ShelfModels.kt:121-126`），是后续实现被误当回归的诱因。
- **D-S06-a（同源 S06-③）**：`readingUnits` 已按真实逻辑章对齐（`ReaderScreenDerivedState.kt:101-108`），说明章首偏移在 UI 侧已存在、只是没接到定位真源——修复面比 9/20 描述更小。
- 与 S15 的交叉：`docs/qa/2026-09-22-s09b-s15-…` 的 D-F2（完读卡只读 `progress.total_reading_time_ms`）属 S04/S16 数据口径，本轮不并入 S05。

## 五、共享文件与可并行分组

共享热点：`ShelfModels.kt`（S05 状态/计数/定位 + S03 拼音 + S06-C 章号标签）、`ShelfViewModel.kt`（S05 + S04 来源更新）、`ShelfRoute.kt`（S05 + S04）、`SettingsStore.kt`（S06-B + S13 大量设置）、`ShelfScreen.kt`/`BookGrid.kt`（S05 + S16/S17）。

| 组 | 独占文件（hunk 级） | 依赖 | 可并行 |
|---|---|---|---|
| G1 书架状态契约 | `BookRepository.kt`（setReadingState/markFinished hunk）、`ShelfModels.kt`（bookStatus、counts 注释、filterItems READABLE 兼容）、`ShelfViewModel.kt:207-217` | 需 Codex 先裁决 S05-①③ 语义 | 与 G2/G3 并行 |
| G2 定位与查看 | `ShelfRoute.kt:153-177`、`ShelfScreen.kt:100-123`、`ShelfImporter.kt`（本批成功 id 回调） | 无 | 与 G1/G3 并行 |
| G3 启动主题 | `SettingsStore.kt:342-385,589-601`、`App.kt:124-128` | S17 主题资源 | 与 G1/G2 并行 |
| G4 章号真源 | `ReaderScreenDerivedState.kt:63-66`、`ReaderProgressEffects.kt`（三个保存点） | S12 坐标合同（勿混） | 与 G1-G3 并行 |
| G5 目录浏览选择集 | `LibraryBrowserViewModel.kt:282-301` | S04 来源、S16 页面 | 独立 |
| G6 滑杆/翻页输入 | `CraSlider.kt`、`PageTurner.kt`（队列） | S09 点击区、S17 公共组件 | 内部两半互不依赖，但都需真机 |
| 串行 | `ShelfUiState.kt`、`BookGrid.kt`、`BatchSheet.kt` 的共享 hunk | 由单一集成者落 | 不并发 |

## 六、未验证 / 无法确认

1. 全部结论来自静态代码 + 现有测试文本；**本轮未运行任何构建或设备**。9/20 两份报告的「91 tests / 18 tests 全绿」是**历史汇总数字**，其原始 XML 日志路径未在这两份报告内给出，本轮按“只有汇总、缺原始证据”处理。
2. S05-①③ 的最终用户结果（取消读完后的分组、叠加筛选下徽章 vs 列表）需要真机或行为测试确认，代码链路已指向结论但未执行。
3. S06-①②③④⑤ 的实际可见后果同理；其中 S06-⑤ 与 9/20 报告一致属「代码路径可确定」，非真机观测。
4. 未复核 S04 来源替换与 S14 备份矩阵（窗口 C 范围），也未复核 S01/S02（窗口 B 范围）。

## 七、边界声明

只新增本报告一个文件；未修改产品/测试/Gradle/脚本/账本/他人报告；未运行 Gradle、ADB、App、数据库写入或远端同步；未 stage/commit/push/checkout/clean；未进入原仓库；未读写任何独立 Agent 记忆库（本轮要求）；记录内仅使用「测试 TXT/EPUB/Markdown」等中性名称。完成后停止，不领取下一任务。
