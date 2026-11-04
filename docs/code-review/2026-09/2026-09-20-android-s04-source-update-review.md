# Android S04 来源文件更新切片审查

> 日期：2026-09-20  
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`  
> 审查对象：恢复分支 `codex/recover-worktree-20260920` 的未提交工作区差异  
> 比较命令：`git diff origin/main -- <S04 paths>`（未提交改动不能使用只比较提交的三点式）  
> 性质：只读审查；未修改产品源码，未 stage、commit 或 push

## 规格来源

- `docs/plans/android/2026-09-12-android-library-folder-smart-recognition-roadmap.md` 的稳定私有副本、来源匹配与内容更新判定；
- `docs/plans/android-parallel-delivery-2026-09-09/reports/zcode-r4-library-source-index.md` 的来源引用基线和显式刷新约束；
- `docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r3-final-acceptance-2026-09-14.md` 的历史验收边界；
- 当前实现中的 `ShelfUiState` 契约：更新集合“需要用户确认刷新”，只用于展示，不改变正文事实源；
- 当前产品盘点 `docs/research/2026-09-19-android-worktree-product-inventory.md`。

## 精确切片边界

S04 候选应只包含：

- `ShelfSourceUpdateChecker.kt` 及其纯判定测试；
- `LibrarySourceIndex.kt` 中文档 URI 重建与元数据 stat 工具；
- `ShelfImporter.kt` 中按来源刷新、类型化结果与来源基线推进 hunk；
- `ShelfViewModel.kt` 中更新检测/刷新编排 hunk；
- `ShelfUiState.kt`、`ShelfRoute.kt`、`ShelfScreen.kt`、`BookGrid.kt` 中“可更新”状态透传与展示 hunk；
- `ShelfImporterTest.kt`、`ShelfViewModelTest.kt` 中对应测试。

不得夹带：

- `LibrarySource.kt` / `LibraryBrowserViewModel.kt` 的新增排序与目录搜索行为属于 S05/S06 目录浏览体验；
- `ShelfImporter.kt` 的新书角标回调、批量进度节流和任务列表优化属于 S05/性能切片；
- `ShelfViewModel.kt`、`ShelfRoute.kt`、`BookGrid.kt` 还同时包含书架状态、角标与成熟化 UI，必须按 hunk 集成；
- Room v15、搜索索引修复、阅读器、设置与同步选项均属于其他切片。

## Standards

### [P1] 四个共享文件包含多个切片，不能按整文件集成

`ShelfImporter.kt` 同时含来源刷新、新书角标回调和批量导入性能改动；`ShelfViewModel.kt`、`ShelfRoute.kt`、`BookGrid.kt` 同时承载 S04 与 S05 书架成熟化。若按文件暂存，S04 会把未独立审查的状态筛选、角标、导入性能和 UI 重构一起带入，测试失败也无法归因。

证据：`ShelfImporter.kt:203-338,340-420,525`；`ShelfViewModel.kt:597-726,733`。

### [P2] 同一条状态契约在数据层、状态层和行为层互相矛盾

`LibrarySourceRefEntity` 仍声明 size/time/hash 固定为导入基线；`ShelfUiState` 声明“需要用户确认刷新”且状态不改变正文；`ShelfImporter` 则在成功后推进 size/time 基线；`ShelfViewModel.refresh()` 又不经确认直接替换正文。这不是注释小误差，而是“这个状态究竟是提示、确认入口，还是自动同步”的产品契约尚未收敛，后续维护者无法判断哪一层才是权威。

证据：`LibrarySourceRefEntity.kt:24-32`；`ShelfUiState.kt:37-43`；`ShelfImporter.kt:217-222,300-309`；`ShelfViewModel.kt:603-625`。

### [P2] 测试通过，但没有覆盖真正危险的跨层旅程

现有测试覆盖 6 个 size/time 纯判定、来源缺失、刷新成功推进 size 和格式失败不推进；`ShelfViewModelTest` 还明确说明不断言来源更新内部行为。没有测试“下拉刷新是否先确认”、刷新后搜索索引、内容哈希/候选指纹、时间列缺失、旧授权根、刷新多个书中途失败等跨层不变量。因此 68 个定向测试全绿不能证明 S04 可发布。

证据：`ShelfSourceUpdateCheckerTest.kt`；`ShelfImporterTest.kt:483-551`；`ShelfViewModelTest.kt:164`。

## Spec

### [P1] 普通下拉刷新会无确认地批量替换正文

书卡先把变化标为“可更新”，状态契约写明“需要用户确认刷新”；但用户在书架做普通下拉刷新时，`refresh()` 直接调用 `syncUpdatedSources()`，后者遍历所有命中书并执行 `refreshFromSource()`。该调用会覆盖应用内部稳定副本。实现自己也承认章节索引和阅读坐标可能错位，并要求 UI 调用前说明，实际 UI 只有事后 Snackbar，没有事前确认。

这会把一个用户通常理解为“重新加载列表”的可逆手势，变成批量正文替换。应拆成“检测更新”和“确认更新”两步；至少逐本或批量展示受影响书籍及阅读位置风险，再由用户确认执行。

证据：`ShelfUiState.kt:37-43`；`ShelfViewModel.kt:603-625,642-680`；`ShelfImporter.kt:221-247`；`ShelfRoute.kt:803-805`。

### [P1] 正文替换后不重建全文搜索索引，搜索会继续指向旧内容

TXT/MD 与 EPUB 修复路径会替换内部文件和书籍记录，但没有调用 `SearchIndexRepository.reindexBookById()`、删除该书旧 term/coverage，或重置扫描游标。索引器的游标只前进，已经完成的书不会因 `updated_at` 改变而自动重扫。因此旧词仍可能搜到，新词搜不到，旧 offsets 还可能跳到错误位置；coverage 仍会谎报原索引已完成。

证据：`ShelfImporter.kt:244-285,754-842`；`SearchIndexRepository.kt:300-334,349-365`。全仓搜索只发现删除撤销路径调用 `reindexBookById()`，来源刷新路径没有调用。

### [P1] 刷新后仍保存旧内容哈希和旧大文件指纹，重复/移动识别会引用上一版正文

小文件首次导入会把 MD5 写入 `books.content_hash` 和来源引用；大文件使用候选指纹。来源刷新替换正文时，两者都不重算：`existing.copy(...)` 继承旧 `books.content_hash`，`refreshSourceBaseline()` 又明确保留旧 `content_hash` / `candidate_fingerprint`。但目录识别随后仍把候选指纹一致视为 SAME_CONTENT，导入去重也继续使用内容哈希。

结果是：更新后的书可能仍与旧文件被判为同内容，而更新后的文件移动/改名后又无法凭新内容证据与当前书匹配。应把“正文、BookEntity hash、source ref hash/fingerprint、size/time 新基线”作为一次一致的刷新提交；大文件可在已有复制流上增量计算，避免额外整文件读取。

证据：`ShelfImporter.kt:515-523,817-824,307-326`；`EpubRepository.kt:114-120`；`LibraryBrowserPolicy.kt:79-103`；路线 §5.6 明确把内容哈希作为更新/移动识别证据。

### [P2] 元数据缺列会永久削弱后续更新检测，同大小同时间变化也必然漏报

`refreshSourceBaseline()` 对 size 缺失会保留旧值，却把 `last_modified` 直接写成 observed.second；provider 若本次未返回时间，原有可比较基线会被擦成 null，此后只改时间的变化永远无法检测。更一般地，检测器完全不使用已有 content hash，所以内容变了但大小相同、provider 修改时间不变或粒度过粗时会返回“书架已是最新”。这与路线 §5.6 的“大小、修改时间或哈希变化”不一致。

证据：`ShelfImporter.kt:311-326`；`ShelfSourceUpdateChecker.kt:15-34`；`docs/plans/android/2026-09-12-android-library-folder-smart-recognition-roadmap.md` §5.6。

### [P2] 来源引用没有进入备份/恢复契约，恢复后该能力会静默消失

在 Android 同步/备份实现与测试中找不到 `library_source_refs` 或 `LibrarySourceRef` 的序列化、上传或恢复路径。恢复到新设备后，书籍正文和元数据即使存在，S04 也拿不到 authority/documentId 与比较基线，只能返回“没有可同步的来源记录”。这是已知产品边界，但当前 UI 把它解释成“这些书是用系统选择器导入的”，会误诊真实原因。

证据：对 `feature/sync/**`、`SyncRepository.kt`、同步测试全量检索无来源引用命中；`ShelfViewModel.kt:650-653`。

## 已确认的正向结果

- 检测器逐本串行 stat，不会并发打爆 SAF provider；循环显式检查取消；
- 元数据读取失败、授权失效和 provider 异常不会被误判为“来源丢失”；
- 正文仍先写 staging、保留 backup，文件替换失败时尝试恢复旧副本；
- 刷新失败不推进来源基线；成功/失败使用类型化结果，不依赖中文消息前缀；
- 主源码、Hilt 注入和测试源码实际编译通过。

## 验证证据

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.feature.library.ShelfSourceUpdateCheckerTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicyTest"
```

结果：`BUILD SUCCESSFUL`，4 个测试类共 68 tests，0 failures、0 errors、0 skipped。该结果是混合恢复工作区的开发门禁，不是独立切片证明，也不替代 WorkBuddy 真机验收。

汇总：Standards 3 项（最严重 P1：共享文件跨切片）；Spec 5 项（最严重 P1：下拉无确认替换正文、搜索索引失真、内容指纹失真）。
