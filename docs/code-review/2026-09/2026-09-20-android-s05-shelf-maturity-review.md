# Android S05 书架成熟化 P0 切片审查

> 日期：2026-09-20  
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`  
> 审查对象：恢复分支 `codex/recover-worktree-20260920` 的未提交工作区差异  
> 比较命令：`git diff origin/main -- <S05 paths>`（未提交改动不能使用只比较提交的三点式）  
> 性质：只读审查；未修改产品源码，未 stage、commit 或 push

## 规格来源

- `docs/plans/android/2026-09-18-android-shelf-maturity-plan.md`，尤其 P0-1～P0-4、验收标准和 §8 实施记录；
- `AGENTS.md` 的共享脏工作区、真实设备和按切片集成约束；
- 当前产品盘点 `docs/research/2026-09-19-android-worktree-product-inventory.md`。

## 精确切片边界

S05 P0 候选包括：

- `ShelfModels.kt` 的互斥阅读状态、状态分组与计数/定位纯函数；
- `BookGrid.kt`、`ShelfScreen.kt`、`ShelfUiState.kt` 的状态 rail、新书角标与定位展示 hunk；
- `ShelfAction.kt`、`ShelfRoute.kt`、`BookActionSheet.kt` 的单书状态动作、导入结果“查看”和新书角标清除 hunk；
- `BookRepository.kt` 的 `markFinished` hunk；
- `ShelfBookActions.kt`、`ShelfViewModel.kt`、`BookViewModel.kt` 的 P0 状态动作 hunk；
- `ShelfPrefs.kt` 的新书集合；`ShelfImporter.kt` 的成功导入回调；
- 对应 status/count/repository/prefs/ViewModel JVM 测试。

不得夹带：

- `BookRepository.kt` 的搜索删除/恢复索引属于 S03；
- `ShelfImporter.kt` 的来源刷新属于 S04，批量导入节流属于性能切片；
- `ShelfViewModel.kt`、`ShelfRoute.kt`、`BookGrid.kt` 的来源更新属于 S04；
- `ShelfModels.kt` 的拼音搜索、章号提取等其它改动分别属于搜索/阅读进度切片；
- P1 用户书单一级分组、P2 position/schema、P3 编辑模式与批量状态动作不属于 P0 候选。

## Standards

### [P1] 五个共享文件同时承载 S03、S04、S05 与性能改动，禁止整文件集成

`BookRepository.kt` 同时含 S03 搜索索引删除/恢复与 S05 状态写入；`ShelfImporter.kt` 同时含 S04 来源刷新、S05 新书回调与导入节流；`ShelfViewModel.kt`、`ShelfRoute.kt`、`BookGrid.kt` 同时含来源更新和书架成熟化。按文件暂存会把已确认不能放行的 S04 行为带入 S05，也会破坏回滚和测试归因。

证据：`BookRepository.kt:238-252,325-370,480-499`；`ShelfImporter.kt:203-338,340-420,525-534`；`ShelfViewModel.kt:371-489,597-726,733`。

### [P2] P3 批量状态 API 已进入 P0 diff，但没有任何可达 UI 发送方

`ShelfAction`、Route handler、ViewModel 与 `ShelfBookActions` 已定义批量标记/取消读完；实际 `BatchActionBar` 仍只有书单、分类、标签、下载、清缓存、删除六项，没有发出两个 action。它们既不属于文档声明的 P0，又处于半接线状态，增加死接口和误判“P3 已实现”的风险。P0 候选应移除这些 hunk，或另立 P3 完整切片后连 UI、失败语义与测试一起交付。

证据：`ShelfAction.kt:65-76`；`ShelfRoute.kt:715-724`；`BatchSheet.kt:75-145`。

### [P2] 91 项定向测试全绿，但缺少四条最关键的状态旅程

现有测试验证 `markFinished` 单次写入、动作层委托、纯状态矩阵和新书集合持久化；没有测试“标记读完 → 取消读完 → 最终分类”、“旧 READABLE 动态视图”、“叠加书单/格式后徽章数 = 实际结果”、“本批导入 → 查看 → 目标可见/高亮/减少动画”。其中动作测试只断言调用了 `setReadingState(READING)`，没有检查 100% 进度下的最终 UI 状态，因而把第一项 P1 漏掉。

证据：`BookRepositoryTest.kt:322-410`；`ShelfBookActionsTest.kt:61-94`；`ShelfStatusCountsTest.kt`；`ShelfViewModelTest.kt:366-433`。

## Spec

### [P1] “取消已读完 / 恢复为在读”实际仍会被归入“已完成”

`markFinished()` 把 `progress_percent` 提升到 100；`markReading()` 和 `restoreReading()` 只把 `completion_state` 改为 READING，不降低进度。随后 `bookStatus()` 在状态不是 FINISHED/SHELVED 时仍以 `percent >= 99.5` 判为 COMPLETED。因此用户会收到“已取消读完”或“已恢复为在读”的成功消息，但书仍留在“已完成”分组；从 100% 搁置再恢复也同样失败。

应定义一个单一的“取消完成”契约：若显式状态要优先于百分比，就要让 READING 能覆盖 100%；若百分比仍代表事实，就必须在取消时恢复标记前进度（这需要保存它）或采用产品认可的降级值。不能只改字符串状态。

证据：`BookRepository.kt:329-370`；`ShelfBookActions.kt:73-96`；`ShelfModels.kt:84-97`。

### [P1] 保留 READABLE 枚举并没有保住历史动态视图，旧视图会静默变成空列表

实现保留 `ShelfStatusFilter.READABLE` 的理由是动态视图按枚举名持久化；`applySavedFilter()` 也确实能恢复 READABLE。但新的 `bookStatus()` 永远不会返回 READABLE，`filterItems()` 又仍用 `bookStatus(...) == READABLE` 过滤，所以历史“本机可读”动态视图会成功加载、没有报错、结果却永远为空。这正是注释声称要避免的静默失效。

应把“本机可读”迁移为独立可用性过滤维度，加载旧 READABLE 视图时做兼容映射；或者明确迁移/提示用户，而不是仅保留枚举名。

证据：`ShelfModels.kt:84-108,268-269`；`ShelfViewModel.kt:206-216`；`ShelfSavedViewsStore.kt:33-36`。

### [P1] 状态徽章统计全书库，叠加筛选后与点进去的实际数量不一致

`filtered` 会叠加格式、书单/分类/标签和搜索条件，`statusCounts` 却始终对完整 `shelfBooks` 计数。比如当前选中一个只有 1 本已完成书的书单，rail 仍可能显示“已完成 30”；点击后列表只有 1 本。代码注释一边声称“徽章与列表必然一致”，一边又明确忽略这些筛选，契约自相矛盾，也违反 P0 验收标准“切换后本数 = 徽章数”。

应先应用除状态以外的当前筛选得到计数基底，再按状态分桶；或者当存在叠加筛选时明确展示全局计数语义并避免宣称与当前结果一致。

证据：`ShelfUiState.kt:88-114`；`ShelfModels.kt:118-140`。

### [P1] 导入完成后的“查看”不绑定本批书籍，在筛选状态下还可能完全无响应

Snackbar 点击“查看”时从全局持久集合 `newBookIds.firstOrNull()` 取目标，而不是从刚完成的 batch 取得成功 bookId：它可能选到上一批仍未打开的书；集合更新尚未到达时还可能取到 null。即使取到本批书籍，只要当前状态/格式/书单筛选把它排除，`shelfGridIndexOfBook()` 返回 null，既不滚动、也不清除 focus 或提示原因。这个入口仍不能可靠解决“刚导入的书找不到”。

应让导入批次保留本批成功 bookId，并在“查看”时选取其中真实可见的一本；必要时先清除会排除目标的筛选，或者明确询问/提示。

证据：`ShelfRoute.kt:147-174`；`ShelfModels.kt:166-170`；`ShelfScreen.kt:117-121`。

### [P2] “查看”缺少规格要求的高亮与减少动画降级

当前只调用 `animateScrollToItem()`，没有任何目标高亮状态或 BookTile 参数；`ShelfScreen` 虽读取 `rememberReducedMotion()` 给触觉使用，却没有用它选择 `scrollToItem()`。因此开启系统减少动画后仍执行动画滚动，且普通模式下滚到目标后没有短暂视觉锚点。

证据：`ShelfScreen.kt:102-121`；全仓 `focusBookId` 仅用于滚动，不参与书卡绘制。

### [P2] “最近加入”文案没有按 P0 规格统一，三个入口仍有两套旧名称

方案明确要求把 IMPORTED 统一命名为“最近加入”并收敛重复来源；当前排序弹层写“导入时间”，工具栏和整理页写“最近导入”，仍然是同一排序三个语义入口、两套名称，且一个都不是规格要求的名称。

证据：`SortSheet.kt:47-52`；`ShelfScreen.kt:546-551`；`ShelfOrganizerRoute.kt:768-773`。

## 已确认的正向结果

- `bookStatus()` 已移除“本机可读优先”短路，显式 FINISHED 与 99.5% 阈值能进入已完成分组；
- 搜索页已传入 `completion_state`，不会只靠百分比漏掉低进度的手动完成状态；
- `markFinished()` 保留首次 `completed_at`、当前位置与 `last_read_at`，没有伪造最近阅读；
- 状态 rail 已进入书架首屏；单书长按面板按当前状态显示互斥动作；
- 新书角标由 DataStore 持久化，打开书籍时会清除单本角标；
- 主源码、Hilt 注入和测试源码实际编译通过。

## 验证证据

命令分两组运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.ui.screen.ShelfStatusFilterTest" `
  --tests "com.creationreadingassistant.ui.screen.shelf.ShelfStatusCountsTest" `
  --tests "com.creationreadingassistant.ui.screen.shelf.ShelfFilterLogicTest" `
  --tests "com.creationreadingassistant.data.repository.BookRepositoryTest" `
  --tests "com.creationreadingassistant.data.settings.ShelfPrefsTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.BookViewModelTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest"

.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfBookActionsTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfBookItemSorterTest"
```

两次均为 `BUILD SUCCESSFUL`；9 个测试类共 91 tests，0 failures、0 errors、0 skipped。该结果是混合恢复工作区的开发门禁，不是独立切片证明，也不替代 WorkBuddy 真机验收。方案 §8 的历史真机表覆盖“标记读完”和新书角标，但没有列出取消读完、旧动态视图、叠加筛选计数、减少动画或目标高亮，不能据其标题把这些未覆盖项视为通过。

汇总：Standards 3 项（最严重 P1：共享文件跨切片）；Spec 6 项（最严重 P1：取消完成失效、旧 READABLE 视图失效、计数不一致和“查看”不可靠）。
