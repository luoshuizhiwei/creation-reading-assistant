# S05-① 执行结果报告：取消读完/恢复在读后书架仍归「已完成」

> **Codex 2026-09-28 独立复核：** 当前 `bookStatus` 已改为显式完成态优先、无百分比完成兜底，书架相关徽章调用 `isDisplayFinished`；Codex 串行重跑六套定向 JVM，实际 **70/70、0 失败/错误/跳过、EXIT 0**。接受**书架代码/JVM 范围**，MuMu UI 尚未验。取消后 `progress_percent=100` 残余仍被书卡显示，且报告 §7 列出的首页/统计/档案等跨页消费者仍按裸百分比判断，不能将“全应用完成态口径统一”记为通过；下一任务需独占这些消费者并保留原书籍位置/时长等数据。

- 日期：2026-09-28
- 执行窗口：S05-①（ZCode / GLM 5.3 Flash），与总指挥及其他窗口并行
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，共享工作区含大量他人未提交改动，本报告只列**本窗口的 hunk**）
- 任务来源：[`docs/coordination/reviews/undated/agent-stage-b-core-audit.md`](../../reviews/undated/agent-stage-b-core-audit.md) S05-①（`BookRepository.kt:329-344` / `:358-371` / `ShelfModels.kt:91-97`）

## 一、结论（TL;DR）

**已修复（JVM 范围）。** 采用对账文档给出的契约候选一：**显式状态列权威，移除 `bookStatus` 中 `p >= 99.5f -> COMPLETED` 的百分比兜底**。取消读完/恢复在读后，书架分组、书卡「完」徽章、封面藏书印、长按面板动作、批量选择与搜索结果口径全部回到「在读」；真实阅读位置、时长、标注等字段分毫不动。红测 8 失败 → 修复后定向 JVM 70/70 全绿（0 fail / 0 err / 0 skip）。

未做有损修复：`markFinished` 抬高的 100% 残余保留在库里，由下一次真实阅读自愈（详见第五节产品决策点）。

## 二、根因与契约裁决

### 根因链（复现确认）

1. `markFinished` 把 `progress_percent` 抬到 100（`coerceAtLeast(100f)`），真实位置保留在 `current_location_json`；
2. `setReadingState(READING)`（取消读完/恢复在读）只写 `completion_state="reading"`、清 `completed_at`，**不动百分比**；
3. 展示层 `bookStatus` 的判定顺序为 SHELVED → FINISHED → `p>=99.5` → …，残余 100% 命中百分比兜底 → 书仍留「已完成」分组；`BookGrid` 徽章、`BookCover` 藏书印、`BookActionSheet.isFinished`、`ShelfSelectionRoute`「已读完」全部按裸百分比判定，与状态列自相矛盾（面板甚至在取消后继续提供「取消读完」）。

### 裁决依据：为什么移除百分比兜底是安全的

**写入面不变量**：本应用内所有会写 `progress_percent >= 99.5` 的路径都在同一行写 `finished`：

| 写入路径 | p≥99.5 时的状态 | 证据 |
|---|---|---|
| `mergeReaderProgress`（阅读器保存，含 EpubRepository 直写经 merge） | 强制 `FINISHED`（when 首分支） | `ReadingEntities.kt:74-75` |
| `updateReadingProgress`（首页标记） | 强制 `FINISHED` | `BookRepository.kt:471-475` |
| `markFinished` | `FINISHED` + percent→100 | `BookRepository.kt:525-538` |
| Legado 导入器 | 外部估算钳制 **99.4%**，绝不静默写 FINISHED | `LegadoReadingProgressImporter.kt:318-322`（S12 落地） |
| `SyncRepository.applyProgress` | 只合并 payload/revision，**不落远端 percent/state** | `SyncRepository.kt:225-249` |

因此 `percent >= 99.5 ∧ state = reading` 在本应用内**只能**由 `setReadingState(READING)`（用户显式取消读完/恢复在读）产生——那是用户的最新意图，展示层必须尊重。对账文档候选二（取消时回落保存前进度）需要新增 pre-finish 快照字段（Room 迁移）或按 location JSON 格式感知重算（需书籍内容，不可靠），现有字段不足，故不做、不做有损修复。

数据侧 `setReadingState`/`markFinished` 行为**未改动**（仅 KDoc 更新），改动集中在展示契约。

## 三、行为矩阵

| # | 场景 | 数据库状态 | 修复前展示 | 修复后展示 |
|---|---|---|---|---|
| 1 | 30% 的书手动标记读完 | percent=100, state=finished, completed_at 写入，位置/时长不动 | 已完成组 +「完」+ 藏书印（正确） | 不变（正确） |
| 2 | **对 #1 执行取消读完** | percent=100 残余, state=reading, completed_at=null，位置/时长/标注分毫不动 | ❌ 仍留已完成组、「完」徽章、藏书印，面板仍提供「取消读完」 | ✅ 在读组，无完徽章/藏书印，书卡显示「第N章 · 100%」残余，面板提供「标记为已读完」 |
| 3 | 真实阅读读到 100%（自然读完） | merge 强制 state=finished | 已完成组 | ✅ 不变（状态分支兜住，无回归） |
| 4 | 自然读完 → 取消 → 再读一页 | saveProgress 按 locator 重算 → merge 再写 finished | — | ✅ 回到已完成（用户真实在书末，正确） |
| 5 | 搁置（对 #1 搁置） | state=shelved, percent=100 残余 | 搁置组但徽章/藏书印仍显示「完」（同族不一致） | ✅ 搁置组、无完标识（口径一致） |
| 6 | 搁置 → 恢复在读 | state=reading, percent 保留 | 若 percent≥99.5 误入已完成 | ✅ 在读组 |
| 7 | 重复取消读完（×2） | state=reading, completed_at=null（幂等） | 同 bug 重复出现 | ✅ 幂等，无漂移（测试锁定） |
| 8 | 进度 0 的书手动标记读完 | percent=100, state=finished | 已完成组 | ✅ 不变（显式状态优先于低进度） |
| 9 | 取消后重新开书真实阅读 | percent 由 locator 重算回落 | — | ✅ 残余自愈，分组/徽章/百分比三者重新一致 |

残余诚实性说明：#2 在用户下一次真实阅读前，书卡百分比会短暂显示 100%（真实值已不可恢复，见第五节）；分组与「完」标识已正确。未伪造位置、未改 99.4%。

## 四、实际改动文件（仅本窗口 hunk；这些文件在工作区还携带他人既有未提交改动）

**主代码（8 文件）**

1. `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfModels.kt`
   - `bookStatus` 移除 `p >= 99.5f -> COMPLETED` 兜底，委托新核心 `readingStatus`（状态列权威）；`book` 参数确认不参与判定（历史遗留，保留签名不动调用方）；
   - 新增共用纯函数 `isDisplayFinished(percent, completionState)`：书卡「完」徽章/「已读完」文案/藏书印/面板动作唯一口径；
   - KDoc 完整记录 S05-① 契约与写入面不变量。
2. `.../ui/screen/shelf/BookGrid.kt` — `BookTile` 新增必填参数 `completionState`；网格「完」、列表「已读完」、进度条显隐条件、网格+列表两处 `BookCover` 藏书印全部改为 `displayFinished` 驱动。
3. `.../ui/screen/shelf/ShelfScreen.kt` — `BookTile` 调用点传 `completionState = progressEntity?.completion_state`（1 行 + 注释）。
4. `.../ui/screen/shelf/BookActionSheet.kt` — `isFinished` 改用 `isDisplayFinished`（修复"取消后面板仍提供取消读完"）；本面板藏书印状态驱动。
5. `.../ui/screen/shelf/ShelfSelectionRoute.kt` — `BatchBookItemCard` 新增 `completionState` 参数；「已读完」胶囊与藏书印状态驱动；调用方传参。
6. `.../ui/screen/shelf/ShelfSearchRoute.kt` — 搜索结果藏书印与该处既有 `bookStatus` 同口径（`displayFinished`）。
7. `.../ui/screen/shelf/BookDetailHeaderSection.kt` + `BookDetailSheet.kt` — 详情头图藏书印状态驱动（新增可选参数，调用方传 `progress?.completion_state`）。
8. `.../data/repository/BookRepository.kt` — `setReadingState` / `markFinished` KDoc 更新为 S05-① 契约（行为零改动）。

**测试（3 文件）**

9. `android/app/src/test/java/com/creationreadingassistant/ui/screen/ShelfStatusFilterTest.kt` — 将 3 个锁定旧契约（纯百分比推断完成）的用例改写为新契约；新增「取消读完满进度仍在读」「完标识跟随显式状态」2 个用例。
10. `.../ui/screen/shelf/ShelfStatusCountsTest.kt` — 计数建模对齐（读满 100% 样例补 state=finished）；新增「取消读完计入在读、不计入已完成」。
11. `.../data/repository/BookRepositoryTest.kt` — 新增 2 个 S05-① 行为矩阵用例：`cancel finished keeps location time and clears completion state`（含重复取消幂等、DB 状态↔分组↔徽章绑定断言）、`shelve finished book keeps percent and restore reading regroups`（搁置/恢复矩阵）。

**BookCover.kt（`ui/components/`）未改**：藏书印语义保持 `percent != null` 时绘制，由各调用方决定传不传——避免动共享组件 API。

## 五、测试证据

### 红测（修复前）

说明：为使新增测试可编译，红测前先在 `ShelfModels.kt` 放入一个**忠实复刻现行徽章公式**（`state==FINISHED || percent>=99.5`）的临时 `isDisplayFinished` 垫片；红测所测即修复前的生产行为。实现时替换为最终委托版。

命令（`android/` 目录）：

```
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.screen.ShelfStatusFilterTest" \
  --tests "com.creationreadingassistant.ui.screen.shelf.ShelfStatusCountsTest" \
  --tests "com.creationreadingassistant.data.repository.BookRepositoryTest"
```

结果：`BUILD FAILED`，**EXIT=1**；`46 tests completed, 8 failed`。

XML 统计（`app/build/test-results/testDebugUnitTest/`）：

| 套件 | tests | failures | errors | skipped |
|---|---|---|---|---|
| ShelfStatusFilterTest | 9 | 5 | 0 | 0 |
| ShelfStatusCountsTest | 12 | 1 | 0 | 0 |
| BookRepositoryTest | 25 | 2 | 0 | 0 |

失败项即 S05-① 症状面：取消读完仍判 COMPLETED、纯百分比推断、徽章判定、分组计数、DB↔分组绑定断言。

### 绿测（修复后）

命令（`android/` 目录，追加关联套件防回归）：

```
./gradlew :app:testDebugUnitTest \
  --tests "...ShelfStatusFilterTest" --tests "...ShelfStatusCountsTest" \
  --tests "...BookRepositoryTest" --tests "...LegadoReadingProgressImporterTest" \
  --tests "...ShelfFilterLogicTest" --tests "...BookViewModelTest"
```

结果：`BUILD SUCCESSFUL`，**EXIT=0**。

XML 统计：

| 套件 | tests | failures | errors | skipped |
|---|---|---|---|---|
| ShelfStatusFilterTest | 9 | 0 | 0 | 0 |
| ShelfStatusCountsTest | 12 | 0 | 0 | 0 |
| BookRepositoryTest | 25 | 0 | 0 | 0 |
| LegadoReadingProgressImporterTest | 12 | 0 | 0 | 0 |
| ShelfFilterLogicTest | 5 | 0 | 0 | 0 |
| BookViewModelTest | 7 | 0 | 0 | 0 |
| **合计** | **70** | **0** | **0** | **0** |

全量 `testDebugUnitTest` 编译闭包已隐式验证（主源码 + 全部测试源码编译通过）；按并行纪律，完整 build / 全量 JVM / lint 归集成者串行执行。

## 六、未验范围

- **真机/MuMu UI 验收未做**（本窗口不运行 ADB；MuMu 由窗口 1 独占）。需 UI 层确认：取消读完瞬间书架分组条计数变化、书卡「完」徽章与藏书印消失、长按面板动作切换为「标记为已读完」、批量选择页「已读完」文案。
- `:app:compileDebugAndroidTestKotlin` 未运行；已静态核对 androidTest 无对改动组合项的直接引用、无「完/已读完」文案断言（grep 为空），编译层预期安全。
- 覆盖 `BookGrid`/`ShelfScreen` 的 Compose 仪器测试（如 ShelfScreenComposeTest）未在设备执行。
- JsonBridge 备份恢复路径写入的是同应用备份的原始行，未单独建用例（数据语义与源库一致）。

## 七、产品决策点（需总指挥/产品侧裁决，本轮未实施）

1. **残余 100% 的显示恢复**：取消读完 → 下次真实阅读之间，书卡百分比显示 100%（数据残余，非真实位置）。现有字段不足以无损恢复显示值：可选 (a) Room 新增 pre-finish 进度快照列（迁移 + 同步/备份链路评估）；(b) 按 `current_location_json` 格式感知重算（需书籍内容，TXT/EPUB 口径不一，不可靠）。本轮不做有损修复（不伪造位置、不改 99.4%）。
2. **同族口径未统一（归各页所有者/集成者）**：以下页面仍按裸 `percent>=99.5` 判完成，取消读完后的残余窗口内会把该书按完成处理，自愈后恢复：
   - `HomeContinueModels.kt:46` / `HomeViewModel.kt:194`（继续阅读排除）；
   - `ProfileViewModel.kt:237` / `StatsRepository.kt:204` / `StatsPage.kt:293`（完读计数）；
   - `HomeArchiveViewModel.kt:53` / `MyReadingViewModel.kt:117`；
   - `BookReadiness.kt:67` `formatBookProgressForCard`。
   如需统一，建议这些站点接入 `ShelfModels.isDisplayFinished`（需跨文件所有权协调，故未越界）。
3. **`mergeReaderProgress` 的搁置优先级**：percent≥99.5 分支先于 SHELVED，搁置书被读到书末会被静默改写为 finished，与 `bookStatus` 的「搁置优先」不完全对齐——既有行为，本轮未动，建议单独裁决。
4. **桌面端**：Android 契约变更不涉及 Electron 线；若桌面 `book-status` 存在同族百分比兜底，需桌面会话自行对账。

## 八、协作纪律遵守情况

- 未 commit / push / reset / checkout；未清理任何工作区既有改动。
- 未触碰 `SettingsStore.kt`、`App.kt`、`LocalZipBackup.kt` 及非所有权文件；无越界需求，**未提交 SEAM REQUEST**。
- 两次 Gradle 运行前均确认构建空闲（16:26 / 16:45 检查 `app/build` 3–4 分钟窗口零写入），未与他窗争用；期间其他窗口的构建活动出现时按约定先完成静态工作。
- 未运行任何 ADB/设备操作；未触碰真实书库与原始仓库；大日志未产生（测试输出在默认 Gradle 目录，未放 E 盘需要）。
