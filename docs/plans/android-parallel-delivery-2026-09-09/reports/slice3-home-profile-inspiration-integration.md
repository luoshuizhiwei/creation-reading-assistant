# 切片 3：首页 / Profile / 灵感页 纵向切片收口报告

> 日期：2026-09-13
> 切片：`docs/plans/2026-09-13-android-wip-integration-plan.md` §3 表格第 3 行「首页、档案页、灵感页的组件拆分与交互」
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`（未提交共享脏工作区，~200+ 项他人 WIP 共存）
> 本轮**未** stage / commit / push / reset / checkout / clean / stash；**未**触碰 AppNavigation、Room、Reader、Shelf、Search、Sync、Security、Settings、Electron。

---

## 1. 每个页面的旧职责 → 新职责映射

### 1.1 首页（Home）

| 位置 | 旧职责（HEAD） | 新职责（当前工作区） |
|---|---|---|
| `ui/screen/HomeScreen.kt` 31 行 | 兼容入口 → `home/HomeRoute` | **不变**（未触碰） |
| `home/HomeRoute.kt` 176 行 | Route 把 ViewModel 状态**整体**搬运进 `HomeUiState`，含 `books` / `sessionsByBook` / `removedContinueIds` / `isContinueSheetOpen` | 只下传 Screen 真正渲染的 11 个字段；sheet 开关唯一来源保留在本 Route 的 `isContinueSheetOpen` |
| `home/HomeScreen.kt` 273 行 | 纯渲染（1 AppScreenScaffold + 1 PageLazyColumn + 5 个 section） | **不变**（未触碰） |
| `home/HomeUiState.kt` 60 行 | 纯 UI 状态，但含 4 个 Screen 从不读取的字段（重复状态 + 每次聚合新建 Map 实例） | 精简字段 + `@Immutable`，仅保留渲染所需 |
| `home/HomeContinueSheet.kt` 829 行 | 底部 Sheet + 排序/菜单/卡片/隐藏书/空态 + 数据构造全在一个文件 | 325 行薄壳；拆出 `HomeContinueModels.kt`（排序键 / `ContinueItem` / `buildContinueItems` / `lastReadAtFor`）、`HomeContinueCards.kt`（`ContinueListItem` / `HiddenBookCapsuleCard` / `EmptyContinueBody`）、`HomeContinueMenu.kt`（`MenuOverlay` / `MenuRow` / `ActionBookPage` / `ActionButton`） |
| `home/components/HomeMetricsSection.kt` | 4 个指标卡，`rememberCountUp(target, reducedMotion)` 旧签名 | 4 个调用点补稳定 key（`home.metrics.week/reading/completed/today`） |
| `homearchive/HomeArchiveScreens.kt` **1741 行** | 「已读完 + 灵感归档 + 灵感详情」三个页面挤在一个文件 | **删除**，拆为 `HomeArchiveFormatters.kt`(176) / `HomeCompletedScreen.kt`(550) / `HomeInspirationsScreen.kt`(451) / `HomeInspirationDetailScreen.kt`(685)；Route 由 `AppNavigation` 直接引用 |
| `ui/viewmodel/HomeViewModel.kt` | 自带私有 `BookEntity.isDisplayable()` / `hasBeenRead()`，与 `ui.util` 的同义函数**并行维护** | 改用共享 `ui.util.isBookDisplayable` / `hasBookBeenRead`，删除两份私有副本（去重，语义等价） |
| `ui/viewmodel/HomeArchiveViewModel.kt` | 已完成/灵感归档聚合流 | 改为 `ImmutableList`（与状态消费端对齐） |

### 1.2 Profile

| 位置 | 旧职责 | 新职责 |
|---|---|---|
| `ui/screen/ProfileScreen.kt` 127 行 | 薄壳 + `when(subPage)` 分派 | 129 行，新增 `SELECTION` 分支 |
| `profile/ProfileRoute.kt` | Route：副作用/导航/Snackbar/确认框 | 新增字典 ViewModel 接线、`selectionActions` 订阅、`searchIndexProgress` 订阅、字典 SAF 导入 launcher |
| `profile/ProfileUiState.kt` | 单一 `ProfileUiState` + Action 契约 | 新增 `ProfileSubPage.SELECTION`、`InstalledDictionaryRow`、`selectionActions` / `dictionaries` / `searchIndexProgress` 字段、`RebuildSearchIndex` 与 8 个选区/词库 Action |
| `profile/ReadingNotesSubPage.kt` **1289 行** | 阅读档案 + 统一笔记（筛选/多选批量/编辑弹窗）全在一个文件 | 255 行编排层；拆出 `ReadingArchiveSection.kt`(272)、`AnnotationEntryCard.kt`(426)、`AnnotationFilterSection.kt`(266)、`AnnotationBatchAndDialogs.kt`(200) |
| `profile/ProfileHomeScreen.kt` | 首页磁贴 + 菜单组 | 新增「选区与查词」入口（`selectionSummary` 副标题）；`HomeStat` 新增 `countUpKey` |
| `profile/DiagnosticsSubPage.kt` 736 行 | 日志/环境诊断 | 794 行，新增「搜索索引」卡片 + 全库重建（**带二次确认**） |
| `profile/StorageSubPage.kt` 625 行 | 存储占用/缓存/危险清理 | 743 行，新增「全文搜索倒排索引」进度卡；重建动作**本轮补上二次确认**（原先单击即触发） |
| `ui/viewmodel/ProfileViewModel.kt` | 同步/WebDAV/设置/笔记动作 | 新增 `searchIndexProgress` 流 |

### 1.3 灵感页（Inspiration）

| 位置 | 旧职责 | 新职责 |
|---|---|---|
| `ui/screen/InspirationScreen.kt` 35 行 | 兼容入口 | 37 行，新增 `onOpenRoute` 透传（临时查阅回源） |
| `inspiration/InspirationRoute.kt` | Route：page/sheet/确认框状态机 | 新增 `InspectSourceLocator`（临时查阅）、`MergeToMaterialCard`（多选合并素材卡）处理 |
| `inspiration/InspirationPage.kt` | `InspirationPage` / `InspirationSheet` / Action 契约 | 新增上述两个 Action |
| `inspiration/InspirationScreen.kt` 337 行 | 单 Scaffold + 三页分派 | 338 行，向详情传 `payload` |
| `inspiration/components/InspirationDetail.kt` 525 行 | 标题/正文/标签/来源/AI 候选 | 809 行，新增聚合摘录区、采用去向区（新增/删除）、locator 精确回源入口 |
| `inspiration/components/InspirationEditor.kt` | 新建/编辑表单 | 草稿生命周期改按条目键管理（本轮） |
| `inspiration/components/InspirationList.kt` | 列表 + 筛选 | 新增多选合并入口 |
| `ui/viewmodel/InspirationViewModel.kt` | 灵感 CRUD / AI 动作 / payload 解析 | 新增 payload 级素材操作、pending id 解析、**编辑时继承非编辑器 payload 字段**（本轮） |
| `ui/viewmodel/InspirationMaterialOps.kt`（新） | — | 纯数据操作：采用去向增删、多来源聚合、管线阶段匹配/计数（零 Android 依赖，JVM 直测） |

---

## 2. 文件清单与行为变更清单

### 2.1 所有权内 WIP 文件清单（相对 HEAD）

**已跟踪修改（23）**
`ui/screen/InspirationScreen.kt`、`ui/screen/ProfileScreen.kt`、`ui/screen/home/HomeContinueSheet.kt`、`ui/screen/home/HomeRoute.kt`、`ui/screen/home/HomeUiState.kt`、`ui/screen/home/components/HomeMetricsSection.kt`、`ui/screen/inspiration/{InspirationPage,InspirationRoute,InspirationScreen}.kt`、`ui/screen/inspiration/components/{InspirationDetail,InspirationEditor,InspirationList}.kt`、`ui/screen/profile/{DiagnosticsSubPage,ProfileHomeScreen,ProfileRoute,ProfileUiState,ReadingNotesSubPage,StorageSubPage}.kt`、`ui/viewmodel/{HomeArchiveViewModel,HomeViewModel,InspirationViewModel,ProfileViewModel}.kt`

**删除（1）**
`ui/screen/homearchive/HomeArchiveScreens.kt`（1741 行 → 4 个新文件）

**新增未跟踪（13）**
`ui/screen/home/{HomeContinueCards,HomeContinueMenu,HomeContinueModels}.kt`、`ui/screen/homearchive/{HomeArchiveFormatters,HomeCompletedScreen,HomeInspirationDetailScreen,HomeInspirationsScreen}.kt`、`ui/screen/profile/{AnnotationBatchAndDialogs,AnnotationEntryCard,AnnotationFilterSection,ReadingArchiveSection,SelectionSubPage}.kt`、`ui/viewmodel/InspirationMaterialOps.kt`

> 已逐一核对**无孤儿文件**：`HomeCompletedRoute` / `HomeInspirationsRoute` / `HomeInspirationDetailRoute` 由 `AppNavigation` 引用；`ContinueListItem` / `MenuOverlay` / `ActionBookPage` / `buildContinueItems` 由 `HomeContinueSheet` 引用；`ReadingArchiveList` / `AnnotationFilterBar` / `AnnotationSelectionBar` / `AnnotationEditDialog` / `AnnotationEntryCard` 由 `ReadingNotesSubPage` 引用；`SelectionSubPage` 由 `ProfileScreen` 引用。**未删除任何未知调用点。**

### 2.2 本轮实际行为变更（9 个文件）

| 编号 | 文件 | 变更 | 性质 |
|---|---|---|---|
| H1 | `ui/viewmodel/HomeViewModel.kt` | 删除私有 `isDisplayable` / `hasBeenRead`，改用 `ui.util` 共享谓词 | 去重；语义等价（已由 6 项 `HomeViewModelTest` 覆盖） |
| H2 | `ui/screen/home/HomeUiState.kt` + `HomeRoute.kt` | 移除 Screen 从不读取的 `books` / `sessionsByBook` / `removedContinueIds` / `isContinueSheetOpen`；`@Immutable` | 消除重复状态与无谓重组面；无 UI 变化 |
| P1 | `ui/screen/profile/ReadingNotesSubPage.kt` | `visibleSelected` 的 `remember` key 由 `selectedIds.size` 改为**选中集快照** | **修 bug**：原先「取消一项 + 勾选另一项」（数量不变）不会重算，批量导出/分享/删除会作用于陈旧选择 |
| P2 | `ui/screen/profile/StorageSubPage.kt` | 「重建索引」补二次确认弹窗（与诊断页同一文案/动作） | **修交互不一致**：同一全库重建动作原先在存储页单击即触发 |
| I1 | `ui/viewmodel/InspirationViewModel.kt` | `saveInspiration` 编辑既有条目时从旧 payload 继承 `categoryIds` / `adoptions` / `excerpts` / `mergedInto` | **修数据丢失**：原先「只改标题」会静默清空素材卡的摘录与采用记录 |
| I2 | `ui/screen/inspiration/components/InspirationDetail.kt` | `observeVariants(entity.id)` 用 `remember(entity.id)` 记忆 | **修重复订阅**：原先每次重组新建 Flow，`collectAsStateWithLifecycle` 会反复重订阅 |
| I3 | `ui/screen/inspiration/components/InspirationEditor.kt` | 草稿 `remember` 与脏判定基线统一按 `existing?.id` 为 key | **修状态串味**：原先 `existing` 由 null 变实体时不会重新预填，且脏基线不重置 |
| I4 | `ui/screen/inspiration/components/InspirationDetail.kt` | 删除重复 import `readerTemporaryRouteForSource` | 清理 |
| T1 | `test/.../InspirationViewModelTest.kt` | 新增回归测试 `saveInspiration update preserves adoptions excerpts and archive target` | 锁定 I1 |

### 2.3 明确**未**改动（避免误读）

- 产品文案语义未改（P2 的两处弹窗文案与诊断页逐字一致，非新增话术）。
- 未改 `AppNavigation`、`AppDatabase`、任何 Room schema/migration、`DatabaseModule`。
- 未改 Reader / dictionary / library / Shelf / Search / Sync / Security / Settings 源码。
- 未删任何调用点；未做全仓格式化或行尾转换（`git diff --check` 对全部所有权文件 EXIT=0，`* text=auto eol=lf` 下 diff 无行尾噪声）。

---

## 3. 定向测试及结果

命令（`android/`，`--no-daemon`，与其他 agent 构建错峰执行；执行前后均已确认无并发源码写入）：

```bash
:app:testDebugUnitTest --no-daemon --console=plain \
  --tests "…ui.viewmodel.InspirationViewModelTest" \
  --tests "…ui.viewmodel.HomeViewModelTest" \
  --tests "…ui.viewmodel.HomeArchiveViewModelTest" \
  --tests "…ui.viewmodel.InspirationMaterialOpsTest" \
  --tests "…ui.screen.inspiration.InspirationDetailStateTest" \
  --tests "…ui.screen.inspiration.InspirationFileStructureTest" \
  --tests "…ui.screen.home.HomeReadingArchiveSectionTest" \
  --tests "…ui.screen.profile.UpdateCheckTest" \
  --tests "…ui.theme.CountUpStartValueTest" \
  --tests "…data.settings.ContinueReadingStoreTest"
```

结果（最终态复跑，`BUILD SUCCESSFUL in 1m 33s`；本次 `compileDebugKotlin` 与 `compileDebugUnitTestKotlin` 均实际执行，非 UP-TO-DATE）：

| 套件 | tests | fail | err | skip |
|---|---:|---:|---:|---:|
| InspirationViewModelTest | 18 | 0 | 0 | 0 |
| UpdateCheckTest | 11 | 0 | 0 | 0 |
| InspirationMaterialOpsTest | 10 | 0 | 0 | 0 |
| HomeViewModelTest | 6 | 0 | 0 | 0 |
| CountUpStartValueTest | 5 | 0 | 0 | 0 |
| InspirationDetailStateTest | 4 | 0 | 0 | 0 |
| InspirationFileStructureTest | 4 | 0 | 0 | 0 |
| HomeArchiveViewModelTest | 3 | 0 | 0 | 0 |
| ContinueReadingStoreTest | 2 | 0 | 0 | 0 |
| HomeReadingArchiveSectionTest | 1 | 0 | 0 | 0 |
| **合计** | **10 套件** | **64** | **0** | **0** | **0** |

`InspirationViewModelTest` 由 17 → 18（新增 T1）。

附加门禁（最终态）：

```
:app:compileDebugAndroidTestKotlin   → BUILD SUCCESSFUL in 26s（2 executed, 28 up-to-date；
                                       kspDebugAndroidTestKotlin 与 compileDebugAndroidTestKotlin 均**实际执行**，
                                       在最终 main classes 上编译通过）
git diff --check -- <全部所有权文件>  → EXIT=0（无空白/行尾错误）
```

> 说明：含本切片 Compose 测试 `HomeScreenComposeTest`（沿用 `HomeUiState.Empty.copy(...)`）与
> `InspirationComposeTest` 在内的 androidTest 源集在最终态编译通过；但它们**未在设备上执行**。

**未运行**（按约束）：全量 `testDebugUnitTest`、`lintDebug`、`assembleDebug`；任何 androidTest **执行**（需真机）；APK 安装。

---

## 4. 未覆盖 UI 路径、风险、SEAM REQUEST

### 4.1 未覆盖的 UI 路径（需真机）

1. **P1 批量操作**：进入「我的 → 我的书评 / 笔记」→ 开选择模式 → 勾选 2 项 → 取消 1 项 + 另勾 1 项 → 批量导出 Markdown / 分享 / 删除。需确认操作对象 == 屏幕上高亮的集合。无自动化测试（`ReadingNotesSubPage` 的选择集是 Composable 内部状态）。
2. **P2 二次确认**：存储页「重建索引」点击应弹出确认框；取消不产生任何写入；诊断页行为应保持一致。
3. **I1 数据保持**：对一条含「采用去向 / 聚合摘录」的素材卡只改标题保存 → 详情页聚合摘录与采用去向应仍在（原先会消失）。
4. **I3 编辑器**：新建 → 保存 → 再编辑并返回；以及从列表连续编辑两条不同灵感，不应串味。
5. **I2 详情**：详情页停留时旋转/切前后台，AI 候选列表不应重复请求。
6. **H2 首页**：确认「切底部 Tab 再回来」时 4 个指标数字**不重播**（`rememberCountUp` 进程级记忆已按稳定 key 生效）；首页各区块内容与拆分前一致。
7. **空态/错误态**：灵感详情「灵感不存在」→ 自动返回列表；首页 0 数据骨架 → 5 个 section 空态。

### 4.2 风险

| 级别 | 项 | 说明 |
|---|---|---|
| 中 | **P1 的选择集与 `selectMode` 保存策略不一致** | `selectMode` 是 `rememberSaveable`、`selectedIds` 是 `remember`。进程重建后会出现「选择模式已恢复、选中集为空」。已知、非本轮引入、未修（涉及状态保存策略，超出拆分裂缝）。 |
| 中 | **首页/继续阅读存在两套排行实现** | VM `buildContinueBooks`（打分后取 top-8）与 UI `HomeContinueModels.buildContinueItems`（全量交用户排序）各自维护 `recencyScore*0.6 + freq*0.4`。谓词已在本轮统一，但打分公式仍双份。HEAD 既有，非本轮引入。 |
| 低 | `HomeViewModel` 中 `totalReadBooksCount` / `completedBooks` 的成员判定依赖 `ui.util` 共享谓词 | 本切片只读取该共享文件、未修改；若后续有人改其语义，会同时影响首页、继续阅读 Sheet 与书架。 |
| 低 | **Profile 的 SELECTION 部分依赖他人切片** | `ProfileRoute` / `ProfileHomeScreen` / `ProfileUiState` / `StorageSubPage` 引用 `data/settings/SelectionActionSettings.kt`、`feature/dictionary/**`、`ui/viewmodel/DictionaryViewModel.kt`、`SearchIndexRepository`、`SearchIndexScheduler`。本轮已逐项核对 API 面一致（`SelectionActions.effectivePrimary` / `MODE_*` / `dictionaryMode` / `InstalledDictionary(baseName,bookName,wordCount)` / `Progress(indexedBooks,totalBooks,isRunning)` / `refreshInstalled` / `consumeMessage` / `install` / `uninstall`），但这些文件正被其他 agent 修改，**任一处签名变化都会直接打断 Profile 编译**。 |
| 低 | `ProfileViewModel.searchIndexProgress` 用 `runCatching{...}.getOrDefault(...)` | 若仓储构造抛错，会静默退化为恒定 0/0 流，用户看不到失败原因。未修（属诊断可见性议题）。 |
| 低 | `aria` 类视觉细节 | `HomeInspirationSection` / `HomeCompletedSection` / `HomeContinueSection` 的 `animateEnter` 在 Lazy 项被回收后重新进入会重播入场淡入。属既有行为，非「数字动效」，未改。 |

### 4.3 SEAM REQUEST（需越界，故不自行修改）

- **SEAM-1｜`ui/util/BookReadiness.kt`**：本切片已把 `HomeViewModel` 的重复谓词收敛到该文件（只读）。若要让首页 Section、继续阅读 Sheet、书架共用**同一**成员判定，需要在该共享文件内补一条「继续阅读候选」纯函数（过滤 + 打分），并把 `HomeContinueModels.buildContinueItems` 与 `HomeViewModel.buildContinueBooks` 都改为调用它。请求归属：共享工具包所有者。
- **SEAM-2｜`ui/navigation/`（AppNavigation 等）**：本切片新增/重命名的 3 个 Route（`HomeCompletedRoute`/`HomeInspirationsRoute`/`HomeInspirationDetailRoute`）与 1 个兼容入口参数（`InspirationScreen(onOpenRoute=…)`）需要 AppNavigation 侧保持当前引用方式；本切片**未**修改该目录。若集成者要改导航路由命名，请同步这两处。
- **SEAM-3｜`ui/viewmodel/DictionaryViewModel.kt` 的 `internal` 可见性与 `ProfileUiState`**：Profile 切片直接依赖 `internal class DictionaryViewModel` 与其 `internal DictionaryUiState`。若字典切片把它改为 `public`/改包，Profile 侧需要同步；建议保持同模块 `internal`。

---

## 5. 给 WorkBuddy-QA 的独立验收提示词

> 以下提示词可直接投喂给 WorkBuddy 真机验收会话。**允许**：安装 APK、导入中性测试 EPUB/TXT、在隔离目录做读写。**禁止**：删除或修改设备上既有真实书籍/笔记/灵感数据。

```text
你是 WorkBuddy 真机验收 agent。请对 Android 原生 App（creation-reading-assistant，android/ 主线）
的「首页 / Profile / 灵感页」纵向切片做独立验收。只使用真机（先 adb devices 核对 serial，
所有命令显式 adb -s <serial>；不使用 MuMu 模拟器）。长测如需常亮，先记录 stay_on_while_plugged_in
原值并在结束时恢复。测试数据一律用「测试 TXT」「测试 EPUB」等中性名称。

背景：本次改动只涉及 ui/screen/home*、ui/screen/homearchive、ui/screen/profile、
ui/screen/inspiration 与其专属 ViewModel；不含 Reader / 书架来源目录 / Room / 搜索 / 同步 / 安装脚本。
请独立判断，不要以本切片自述的编译/单测结果替代真机证据。

需要独立验证的 7 组行为（逐项给出 PASS/FAIL 与证据）：

A. 首页状态唯一来源与数字动效
   1) 冷启动首页：4 个指标卡（本周/在读/翻越终章/今日）应各自从 0 滚到目标值一次。
   2) 切到底部「书架」Tab 再切回「首页」：4 个数字应**静止不动**（不得从 0 重播、不得先定住再猛跳）。
   3) 首页 5 个区块（累计阅读 / 继续阅读 / 本周概览 / 最近灵感 / 已阅读完成）与拆分前内容一致，
      空数据时各自显示空态；「继续阅读」为空时点空态能跳到书架。
   4) 继续阅读 Sheet：点「更多」可切排序/管理模式，隐藏书籍可恢复；打开书籍前若正文未就绪应出 Snackbar 而非白屏。

B. Profile 可达性
   点「我的」→ 逐一点开：阅读设置、选区与查词、应用外观、我的阅读、阅读目标、
   存储管理、清理缓存、标签管理、分类管理、书单管理、局域网同步、WebDAV 设置、AI 助手、
   日志与诊断、隐私安全、关于；每个子页都必须打开且顶部返回可回到「我的」首页。
   右上角「更多」菜单里的数据备份 / 隐私安全 / 关于必须可达。

C. Profile 阅读笔记（重点：批量操作正确性）
   1) 在测试 TXT 中做 ≥3 条高亮/批注/书签。
   2) 进入「我的 → 我的书评 / 笔记」，开选择模式。
   3) 勾选 A、B（此时底部计数应为 2）→ 取消 B、另勾 C（计数仍为 2）。
   4) 点批量导出 Markdown 与批量分享：导出/分享内容必须恰好是 **A 与 C**，不得包含 B。
   5) 点批量删除并确认：必须只删除 A 与 C，B 仍存在。
   6) 单条点击应能回源到阅读器对应位置；长按/编辑弹窗保存后列表即时刷新。

D. Profile 存储 / 诊断：全库重建搜索索引的确认流
   1) 「存储管理」中的「重建索引」/「重建搜索索引」按钮：单击必须先弹出确认框；
      选「取消」不得产生任何写入（观察索引进度数字不变）。
   2) 「日志与诊断」中的同名入口：确认框文案与存储页一致；确认后才开始重建，进度应为后台分步推进。
   3) 重建过程中返回首页/阅读不应卡顿。

E. 灵感：编辑不得清空素材字段（重点回归）
   1) 对任一灵感：详情页新增 1 条「采用去向」（文字）→ 记下详情页出现该记录。
   2) 用详情页「编辑」只修改标题 → 保存 → 回到详情。
   3) 期望：标题已更新，且「采用去向」记录**仍在**、聚合摘录区**仍在**。
      若采用去向消失，即为回归失败，请提供前后截图与操作录屏。

F. 灵感：列表 / 详情 / 编辑状态与返回顺序
   1) 列表 → 详情 → 编辑 → 系统返回（有未保存修改）应弹「放弃未保存修改？」；
      「继续编辑」留在编辑器，「放弃」回详情且不落盘。
   2) 系统返回优先级：弹层 → 删除确认 → 未保存确认 → 编辑器 → 详情 → 列表；到列表根再返回才退出 App。
   3) 详情页停留时切前后台 2 次再回来：AI 候选列表不应重复请求/闪动。
   4) 打开一个不存在的灵感 id（如从通知/深链）：应提示「灵感不存在」并安全回到列表，不留白屏。

G. 灵感：空态与错误态
   1) 无任何灵感时：列表显示空态文案；首页「最近灵感」显示空态提示。
   2) 断网或把 AI Key 置空后点任一 AI 动作：应提示「生成失败，请检查 AI 设置」，且**原文不得被覆盖**。

交付：逐项 PASS/FAIL 表格 + 证据（截图/录屏路径、关键 logcat 行）。
必须报告：任何 FATAL / ANR、任何与上述期望不符的行为、以及你**无法**验证的条目（不要用推测填表）。
明确声明：本次验收是否修改了设备上的真实书籍/笔记/灵感数据（应为「否」）。
```

---

## 6. 给集成者的提交前提示

- 本切片所有改动均为**未提交**工作区状态；按要求未 stage/commit。
- 提交前请确认：`git diff --check` 对本切片路径为 0；`HomeArchiveScreens.kt` 的删除与新 4 个文件必须在**同一提交**中出现，否则 `AppNavigation` 引用的 Route 会缺失。
- `HomeContinueSheet.kt` / `HomeUiState.kt` / `HomeMetricsSection.kt` / `HomeViewModel.kt` 在工作区带 CRLF（仓库 `.gitattributes` 为 `* text=auto eol=lf`），提交时不得夹带行尾转换。
- 与切片 2（Reader/字典/选区）存在**只读**依赖（见 §4.2 低风险项），若切片 2 先提交，请重跑本切片的 `compileDebugKotlin` 与上述 10 个定向套件。
