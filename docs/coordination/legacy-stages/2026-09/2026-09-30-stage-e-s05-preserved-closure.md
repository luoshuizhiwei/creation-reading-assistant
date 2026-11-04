# 阶段 E：S05 完成态修复最小闭包（联合底座 → S05 候选）（2026-09-30）

> **窗口**：S05 完成态（取消读完 / 显式完成态）最小依赖闭包 —— 补齐联合候选遗漏的已验收既有修复。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`；**HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`**；暂存区空。
> **只读底座**：`E:\creation-reading-assistant\stage-e-final-joint-20260930\joint\android`（联合候选树；其 from-head 补丁 SHA `b5365d80b7f23eb9c8ce2010b14a71a0c010a7015d2e8462ffa6c94c1fc62084`，`manifest/patch-file-list.csv` 记 71 个数据行 + 表头）。
> **本窗口独占候选**：`E:\creation-reading-assistant\stage-e-s05-preserved-20260930\android`（= 联合底座复制 + S05 闭包；构建缓存 `_cache-gradle-home` 在 E 盘本目录）。
> **输入**：[联合候选与剩余缺口复核](../../reviews/2026-09/2026-09-30-stage-e-joint-gaps-codex-review.md)、[剩余资产语义证据](2026-09-30-stage-e-final-asset-evidence.md)、[S05-① 取消读完](2026-09-28-s05-cancel-finished-state-result.md)、[S05-② 跨页消费者](2026-09-28-s05-cross-page-finished-consumers-result.md)、[S05 MuMu 跨页验收](../../../qa/2026-09/2026-09-28-s05-mumu-cross-page-finished-acceptance.md)。
> **纪律**：共享仓库**唯一写入 = 本文件**。未改共享产品源码 / 测试 / 配置 / 其他窗口目录或 READY；**未操作设备**（MuMu/ADB 未使用）；**未** stage / commit / push / reset / checkout / stash；**未**删除或搬移目录；**未**触碰原仓库、正式包、正式书库。

---

## 一、结论摘要

1. **S05 完成态最小闭包已建成**：以联合候选为底座，按**逐文件/hunk 合流**（重叠文件不整文件覆盖）补入 S05-①（书架完成态契约）与 S05-②（跨页消费者）全部已验收修复，共 **30 个文件（28 改 + 2 增）/ 67 hunk / +1138 −74**。
2. **本地验证全过**：`compileDebugKotlin` + `compileDebugUnitTestKotlin` **EXIT 0**（0 error）；**S05 定向 JVM 13 套 97/97**（0 失败/错误/跳过，EXIT 0）；**G6 回归 8 套 48/48**（EXIT 0，覆盖我改动的 `StatsPage.kt`）。
3. **可复算补丁**：`s05-preserved-from-joint.patch`（30 文件 / 67 hunk，SHA-256 `3b5d41e6de159c0d0780744faf7904c572f22a40ebd97006704cb78a3ae5e15d`）；全新联合底座副本 + `git -c core.autocrlf=false apply` == 候选，**30/30 文件字节级一致**（`--check` EXIT 0）。
4. **四类语义已按任务要求保留**：① 显式完成态与百分比的关系（状态列唯一权威）；② 取消读完后书架筛选/首页/档案/统计消费者口径一致；③ 接近 100% 的估算值不静默改完成（导入器 99.4% 钳制保留、无百分比兜底）；④ 既有 B1/B2/M-BUG-03/G6 改动与测试未被覆盖（重叠文件按 hunk 合流 + G6 回归全绿）。
5. **未覆盖**：本候选**设备验收仍待统一执行**（旧 S05 MuMu 证据只说明旧版本行为，不外推本候选）；被排除的相邻批次（拼音搜索、章号、角标、状态动作卡、批量删除、共享高亮、S12/同步/AI、G6 时长）不在闭包内，见 §四。

## 二、最小依赖闭包清单（逐文件 / hunk 归属）

补丁逐文件统计（joint → 候选）：

| # | 文件 | hunk | +/− | 归属与内容 |
|---|---|---:|---:|---|
| 1 | `ui/screen/shelf/ShelfModels.kt` | 2 | +188/−14 | **S05 核心**：`bookStatus` 改为委托新 `readingStatus`（显式状态权威，移除 `p>=99.5f -> COMPLETED` 兜底与 `isDownloaded()->READABLE` 分支）；新增 `isDisplayFinished`、`ShelfStatusGroupOptions`（不含 READABLE）、`shelfStatusCounts`；`filterItems` 增加 READABLE 独立分支（保留已保存视图筛选能力）；S05 契约 KDoc |
| 2 | `ui/util/BookReadiness.kt` | 2 | +24/−2 | **S05 跨页助手**：新增 `isReadingFinished(progress)`；`formatBookProgressForCard` 增加 `completionState` 参数（「已读完」要求显式 finished） |
| 3 | `data/repository/StatsRepository.kt` | 3 | +17/−3 | 完读计数改 `it.readingState == FINISHED`；新增投影版 `StatsProgressRow.isReadingFinishedRow()` |
| 4 | `data/repository/BookRepository.kt` | 1 | +28/−0 | `setReadingState` S05 契约 KDoc；**+`markFinished` 函数（见 §五 依赖理由）** |
| 5 | `ui/viewmodel/HomeViewModel.kt` | 3 | +5/−3 | 站点 1/2：`buildContinueBooks` 排除条件与 `completed` 过滤改 `isReadingFinished`（S05 契约注释） |
| 6 | `ui/screen/home/HomeContinueModels.kt` | 4 | +7/−1 | 站点 3：`ContinueItem.completionState` 字段 + 排除条件改 `isReadingFinished` |
| 7 | `ui/screen/home/HomeContinueCards.kt` | 1 | +2/−1 | 站点 9：卡片文案传 `item.completionState` |
| 8 | `ui/screen/home/components/HomeContinueSection.kt` | 1 | +2/−1 | 站点 9：文案传 `progress?.completion_state` |
| 9 | `ui/viewmodel/HomeArchiveViewModel.kt` | 2 | +5/−4 | 站点 4：归档完成过滤改 `isReadingFinished` |
| 10 | `ui/viewmodel/MyReadingViewModel.kt` | 1 | +4/−4 | 站点 5：移除「percent≥99.5 覆盖状态为 FINISHED」 |
| 11 | `ui/viewmodel/ProfileViewModel.kt` | 2 | +3/−1 | 站点 6：完读数改 `it.isReadingFinishedRow()` + import |
| 12 | `ui/screen/stats/StatsPage.kt` | 1 | +2/−1 | 站点 8：`computeStats` 分布 `comp = state == FINISHED`（G6 时长改动保留不动） |
| 13 | `ui/screen/shelf/BookGrid.kt` | 7 | +9/−5 | 书卡：`BookTile` 增 `completionState` 必填参数 + `displayFinished`；「完」徽章/藏书印/「已读完」/进度条显隐四处的 `>=99.5f` 改 `displayFinished` |
| 14 | `ui/screen/shelf/ShelfScreen.kt` | 1 | +2/−0 | 调用点传 `completionState = progressById[book.id]?.completion_state` |
| 15 | `ui/screen/shelf/BookActionSheet.kt` | 2 | +4/−1 | 面板 `isFinished` 改 `isDisplayFinished` + 藏书印按 `isFinished` 门控 |
| 16 | `ui/screen/shelf/ShelfSelectionRoute.kt` | 4 | +7/−2 | 批量卡 `completionState` 参数 + `displayFinished` 门控「已读完」胶囊与藏书印 + 调用点传参 |
| 17 | `ui/screen/shelf/ShelfSearchRoute.kt` | 2 | +6/−2 | 搜索结果 `bookStatus(book, percent, completion_state)` + 藏书印按 `displayFinished` |
| 18 | `ui/screen/shelf/BookDetailHeaderSection.kt` | 2 | +3/−1 | 详情头图藏书印按 `isDisplayFinished(percent, completionState)` 门控 |
| 19 | `ui/screen/shelf/BookDetailSheet.kt` | 1 | +1/−0 | 调用点传 `completionState = progress?.completion_state` |
| 20 | `test/…/ui/screen/ShelfStatusFilterTest.kt` | 1 | +94/−8 | S05-① 判别套件（旧纯百分比契约 3 例改写 + 新增 2 例） |
| 21 | `test/…/ui/screen/shelf/ShelfStatusCountsTest.kt` | 1 | +210/−0 | **新增**：分组计数建模（含「取消读完计入在读、不计入已完成」与 `READABLE !in ShelfStatusGroupOptions`） |
| 22 | `test/…/data/repository/BookRepositoryTest.kt` | 7 | +315/−0 | S05-① 行为矩阵（取消读完保位置/时长/清状态、幂等、搁置/恢复）+ 既有 `setReadingState`/`markFinished` 套件 |
| 23 | `test/…/ui/screen/home/HomeContinueModelsTest.kt` | 2 | +16/−6 | S05-② 判别（真实 finished 排除 / 取消读完保留 + `completionState` 透传） |
| 24 | `test/…/ui/viewmodel/HomeViewModelTest.kt` | 2 | +23/−1 | S05-② 站点 1/2 判别 |
| 25 | `test/…/ui/viewmodel/HomeArchiveViewModelTest.kt` | 2 | +22/−3 | S05-② 站点 4 判别 |
| 26 | `test/…/ui/viewmodel/MyReadingViewModelTest.kt` | 2 | +11/−7 | S05-② 站点 5 判别（b5 = 100%+reading → 在读页签） |
| 27 | `test/…/ui/viewmodel/ProfileViewModelTest.kt` | 3 | +6/−3 | S05-② 站点 6 夹具判别（**joint 版 + 仅 S05 增量**，见 §五） |
| 28 | `test/…/ui/screen/stats/StatsComputationTest.kt` | 2 | +29/−0 | S05-② 站点 8 分布判别 |
| 29 | `test/…/ui/util/BookReadinessProgressLabelTest.kt` | 1 | +70/−0 | **新增**：`isReadingFinished` 与文案函数判别矩阵 |
| 30 | `test/…/data/repository/ReadingStatsPolicyTest.kt` | 2 | +23/−0 | S05-② 投影版 `isReadingFinishedRow` 判别（含历史值兼容） |

**数据可得性**：所有站点所用流/投影均已携带 `completion_state`（实体直通或 `StatsProgressRow` 已含），**零 DAO / 查询 / 投影 / 迁移改动，无 SEAM REQUEST**（与 S05-② 报告结论一致）。

## 三、保留的四类语义（任务点名的验证对象）

| # | 要求 | 闭包中的落实 | 判别测试 |
|---|---|---|---|
| 1 | 显式完成态与百分比的关系 | `readingStatus` 只读状态列：`FINISHED → COMPLETED`；`READING` 按 `p>0` 分在读/未读；**百分比不参与完成判定** | `ShelfStatusFilterTest`（`bookStatus(remote(), 99.5f) == READING`、`isDisplayFinished(100f,"reading") == false`、`isDisplayFinished(0f,"finished") == true`） |
| 2 | 取消读完后四处口径一致 | 书架分组/徽章/藏书印/面板 + 首页继续阅读/归档 + 档案（我的阅读）+ 统计（完读计数与分布）**全部改同一状态权威** | `ShelfStatusCountsTest`、`BookRepositoryTest`（DB 状态↔分组↔徽章绑定）、`HomeViewModelTest`、`HomeArchiveViewModelTest`、`MyReadingViewModelTest`、`ProfileViewModelTest`、`StatsComputationTest`、`ReadingStatsPolicyTest` |
| 3 | 接近 100% 的估算不静默改完成 | 保留 S12 导入器 99.4% 钳制；移除全部百分比完成兜底；「高百分比 + reading」只来自用户显式取消 | `ShelfStatusFilterTest` 的 99.5 边界用例；`BookRepositoryTest` 的取消读完矩阵 |
| 4 | 既有 B1/B2/M-BUG-03/G6 不回归 | 重叠文件（`BookGrid`/`ShelfScreen`/`BookRepository`/`StatsPage`/`BookRepositoryTest`）**按 hunk 合流**，未用旧 S05 整文件覆盖；G6 时长改动与 M-BUG-03 角标保留 | **G6 回归 8 套 48/48 EXIT 0**；编译全绿 |

## 四、明确排除的相邻改动（未夹带）

| 被排除项 | 位置 | 判据 |
|---|---|---|
| 拼音首字母多词搜索（`extractPinyinInitials` + token 匹配） | `ShelfModels.filterItems` | 属「智能搜索」批次，非完成态契约 |
| 章号标签（`extractChapterLabel` + `chapterLabel` 参数） | `ShelfModels` / `BookGrid` / `ShelfScreen` | 属书架卡信息增强批次 |
| 「新」/「可更新」角标（`isNew` / `contentUpdated`） | `BookGrid` | 属 M-BUG-03/导入批次（joint 已有其生产侧，未在闭包内扩展） |
| 阅读状态动作卡（`onMarkFinished`/`onMarkReading`/`onShelve` 三行 UI） | `BookActionSheet` | 属状态动作 UI 批次；闭包只取 `isFinished` 与藏书印门控 |
| 批量删除 `deleteBooks(ids)` | `ShelfSelectionRoute` | 属批量操作批次 |
| `buildHighlighted` 共享化 + `SearchResultRow` 更名 | `ShelfSearchRoute` | 属共享组件抽取批次 |
| 状态分组条重构（徽章计数 UI / `ShelfStatusGroupOptions` 消费） | `BookGrid` | UI 重构；闭包仅纳入**被测试要求的**数据侧 `ShelfStatusGroupOptions`/`shelfStatusCounts` |
| S12 Legado 导入、同步偏好、WebDAV、AI 测试 | `ProfileViewModel` 及其测试夹具 | 属 S12/同步批次（`ProfileViewModelTest` 因此改为 joint 版 + 仅 S05 增量） |
| G6 时长单位（`DurationUnit`/`formatDuration`/`yearBillCardValue`） | `StatsPage` 等 | 已在 joint 内；闭包不动（回归验证覆盖） |

## 五、新增内容与依赖的理由（任务要求逐项解释）

1. **`ShelfStatusCountsTest.kt`（新增，+210）**：S05-① 报告 §四 明列的关联测试（分组计数建模 + `READABLE !in ShelfStatusGroupOptions`）。其存在是 `ShelfStatusGroupOptions` / `shelfStatusCounts` 进入闭包的**依据**（测试即契约）。
2. **`BookReadinessProgressLabelTest.kt`（新增，+70）**：S05-② 报告 §四 第 16 项明列的新建测试，覆盖 `isReadingFinished` 与 `formatBookProgressForCard` 的判别矩阵。
3. **`BookRepository.markFinished`（+28 行，含 KDoc）**：**不是新功能**，而是 S05 测试套件的**编译依赖**——`BookRepositoryTest`（S05-① 报告 §四 第 11 项）含 `setReadingState`/`markFinished` 两组用例，joint 树缺该函数（joint UI 未调用，属「标记为已读完」批次的生产实现）。若不带入，则必须删改 S05 测试（不允许）。带入的是工作树既有实现（写入面语义与 S05 契约一致：抬升 percent 至 100 并写 `FINISHED`），闭包内**无 UI 调用点**（不新增功能面）。
4. **`ProfileViewModelTest` 用 joint 版 + S05 增量**（而非整文件取自工作树）：工作树版携带 S12/同步/AI 夹具（`LegadoReadingProgressImporter`、`SyncPrefsStore`、`SearchIndex*` 字段），与闭包无关且会引入未纳入的生产依赖；按「重叠文件按 hunk 合流」原则只应用 S05 三处增量（KDoc、b2 改 `finished`、新增 b4 = 100%+reading、断言注释）。
5. **`ShelfStatusGroupOptions` / READABLE 分支调整**：S05 测试**要求** `READABLE !in ShelfStatusGroupOptions`；移除 `bookStatus` 的 READABLE 分支后，`filterItems` 需保留 READABLE 的独立筛选分支（已保存动态视图仍可用）——两者成对出现，属该轮已验证范围。

## 六、验证证据（命令 / 退出码 / XML）

### 6.1 编译（E 盘独占缓存）

```
cd E:\creation-reading-assistant\stage-e-s05-preserved-20260930\android
JAVA_HOME=D:\develop\Java\jdk-17.0.14  GRADLE_USER_HOME=<候选>\..\_cache-gradle-home
gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline --max-workers=1 --console=plain
```
→ **EXIT 0**，`BUILD SUCCESSFUL`（`_evidence/compile-2.log`；首次 `compile-1.log` 的 30 个错误全部来自 `ProfileViewModelTest` 的 S12 夹具，按 §五.4 修正后归零）。

### 6.2 S05 定向 JVM（13 套，97 tests）

| 套件 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `data.repository.BookRepositoryTest` | 25 | 0 | 0 | 0 |
| `data.repository.ReadingStatsPolicyTest` | 3 | 0 | 0 | 0 |
| `ui.screen.ShelfStatusFilterTest` | 9 | 0 | 0 | 0 |
| `ui.screen.shelf.ShelfStatusCountsTest` | 12 | 0 | 0 | 0 |
| `ui.screen.shelf.ShelfFilterLogicTest` | 5 | 0 | 0 | 0 |
| `ui.screen.home.HomeContinueModelsTest` | 7 | 0 | 0 | 0 |
| `ui.screen.stats.StatsComputationTest` | 2 | 0 | 0 | 0 |
| `ui.util.BookReadinessProgressLabelTest` | 7 | 0 | 0 | 0 |
| `ui.viewmodel.BookViewModelTest` | 7 | 0 | 0 | 0 |
| `ui.viewmodel.HomeArchiveViewModelTest` | 4 | 0 | 0 | 0 |
| `ui.viewmodel.HomeViewModelTest` | 7 | 0 | 0 | 0 |
| `ui.viewmodel.MyReadingViewModelTest` | 6 | 0 | 0 | 0 |
| `ui.viewmodel.ProfileViewModelTest` | 3 | 0 | 0 | 0 |
| **合计** | **97** | **0** | **0** | **0** |

→ **EXIT 0**（`_evidence/test-s05-jvm.log` + `app/build/test-results/testDebugUnitTest/` 原始 XML）。
**口径对照**：S05-① 原跑 6 套 70/70；本候选该 6 套中的 `LegadoReadingProgressImporterTest` **不在联合底座内**（S12 时代测试文件，joint 树无此文件），其余 5 套 58/58 通过；S05-② 8 套 39/39（原报告 40，差额为夹具整理，逐套 0 失败）。**未把"少一套"写作通过**。

### 6.3 G6 回归（8 套，48 tests，验证 joint 既有改动不回归）

| 套件 | tests | 结果 |
|---|---:|---|
| `DurationPrefsTest` / `DurationUnitTest` / `TimeUtilsTest` | 8 / 2 / 13 | 全绿 |
| `YearBillHeadlineTest` / `HeatmapBuildTest` / `ComputeTodayReadingMsTest` / `StatsFileStructureTest` | 10 / 4 / 6 / 3 | 全绿 |
| `HomeReadingArchiveSectionTest` | 2 | 全绿 |
| **合计** | **48** | **0 失败/错误/跳过，EXIT 0** |

（G6 九类原 49 tests，其中 `StatsComputationTest`（1）已在 §6.2 计入 → 48+1=49 ✓）

### 6.4 可复算补丁

| 项 | 值 |
|---|---|
| 路径 | `E:\creation-reading-assistant\stage-e-s05-preserved-20260930\s05-preserved-from-joint.patch` |
| 规模 | 30 文件（28 M + 2 A）/ 67 hunk / +1138 −74 / 77,193 字符 |
| SHA-256 | `3b5d41e6de159c0d0780744faf7904c572f22a40ebd97006704cb78a3ae5e15d` |
| 复算 | 全新联合底座副本 + `git -c core.autocrlf=false apply`：`--check` **EXIT 0**；apply 后 **30/30 文件字节级一致**（`differ=0`） |

**基线声明**：本补丁的基线是**联合候选树**（`stage-e-final-joint-20260930/joint/android`），**不是 HEAD**；若要 HEAD→完整批次，需联合候选自身的 from-head 补丁（SHA `b5365d80…`）叠加本补丁，两者基线不同、不可互相冒充。

### 6.5 来源 SHA（关键来源文件，工作树）

| 文件 | SHA-256（工作树来源） |
|---|---|
| `ui/screen/shelf/ShelfModels.kt` | `ed9bb3ae63dc1a3b…` |
| `ui/util/BookReadiness.kt` | `4aaef8b7b757ece4…` |
| `data/repository/StatsRepository.kt` | `25c99c8d55a5912e…` |

（候选内 30 个文件的 SHA 全表见 `_evidence/patch.log` §touched file SHAs）

## 七、未覆盖项与边界

1. **设备验收未执行**：本窗口不操作设备（任务约束）。旧 S05 MuMu 跨页验收（2026-09-28）只证明**旧版本**行为；本候选（joint + S05 闭包）**设备验收仍待统一执行**，不得引用旧证据。
2. **`LegadoReadingProgressImporterTest` 不在本候选**：该 S12 时代测试文件不在联合底座内，其覆盖的导入器钳制语义（99.4%）在本候选以源码保留但无该套件；属 S12 闭包范围。
3. **被排除批次的测试**：拼音搜索/章号/角标/状态动作卡/批量删除/共享高亮等批次的测试未纳入（其生产改动亦未纳入），因此相关行为在本候选**不可用**——这是刻意的闭包边界，不是缺陷。
4. **`markFinished` 无 UI 调用点**：本候选带入了该函数（测试依赖）但 joint UI 未接「标记为已读完」动作；设备上无法通过 UI 触发 `markFinished`（仅测试覆盖）。
5. **联合候选自身的门禁未重跑**：本轮只跑 S05 定向 + G6 回归 + 所属编译；joint 全量 2464 tests 与 Lint/构建由集成者串行执行。
6. **`S05-②` 报告中的 40 vs 39 差额**：以本轮逐套 XML 实算为准（39），差额为夹具合并，非用例删除（无 skipped）。

## 八、纪律声明

- 共享恢复仓库内**唯一写入 = 本文件**；未改产品源码 / 测试 / 配置 / 其他窗口报告或 READY。
- **未** stage / commit / push / reset / checkout / stash；**未**操作设备（MuMu/ADB 未使用）；**未**删除或搬移目录；**未**触碰原仓库、正式包、正式书库。
- 本窗口全部写入在 `E:\creation-reading-assistant\stage-e-s05-preserved-20260930\`（候选、`_cache-gradle-home`、`_evidence`、补丁、`_verify`）。
- 未把未运行项写作通过；§七 逐条标注未覆盖；补丁基线（联合候选，非 HEAD）显式声明。
