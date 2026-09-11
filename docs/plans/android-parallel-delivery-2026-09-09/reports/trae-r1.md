# Trae R1 报告：统一阅读笔记（N1）

- 工作树：`D:/develop/Code/Codex/cra-android-trae-r1`，分支 `codex/android-trae-r1`
- 基线 SHA：`e31db92d5493b6b705ae447aba82c52dc9d7e65f`（开工时 HEAD，未 stage/commit/push）
- 状态：**Dev complete（未真机验收，交 WorkBuddy）**

## 改动路径

主源码（均为 Trae 所有权内）：

| 文件 | 说明 |
|---|---|
| `feature/annotations/AnnotationModels.kt`（新增） | 统一条目模型 `AnnotationEntry`：`AnnotationType{HIGHLIGHT,NOTE,BOOKMARK}`、稳定 ID（`highlight:{id}` / `note:{id}` / `bookmark:{id}` 类型前缀，不同表同 ID 不冲突）、两表实体投影（摘录/批注分区、locator v2 解码、无 locator 不伪造偏移） |
| `feature/annotations/AnnotationFilters.kt`（新增） | 类型/书籍/关键词三重筛选；跨书按创建时间倒序、单书按文档序（locator chapterIndex → 章内偏移，无 locator 殿后）——**不按中文标题字典序**；通用 `groupByChapterInDocumentOrder` 章节分组 |
| `feature/annotations/AnnotationExport.kt`（新增） | 选中条目批量导出 Markdown：文档头计数、按书分组、三类型标注、摘录引用块 + 批注 + 章节来源；缺书（bookId 空或映射缺失）归并同一「未关联书籍」组；特殊字符原样保真 |
| `feature/annotations/AnnotationActions.kt`（新增） | Route→纯渲染子页的动作桥接口（`LocalAnnotationActions`，未提供时子页降级纯展示，与 LocalProfileSnackbar 同模式） |
| `ui/screen/profile/ReadingNotesSubPage.kt` | NOTES 子页重写为统一笔记页：筛选栏（类型多选/书籍下拉/关键词）、批量选择模式、条目卡片（类型徽标 + 来源书胶囊 + 章节行 + 摘录引用块 + 批注气泡 + 高亮 5 色圆点 + 定位/编辑/删除）、选择底栏（导出/分享/删除）、编辑批注对话框；READING 子页原行为不变 |
| `ui/screen/profile/ProfileRoute.kt` | 提供 `LocalAnnotationActions` 实现：回源导航（`reader/{bookId}?highlightId={rawId}`，无 locator 降级只开书）、删除后 Snackbar「撤销」（只恢复最近一批）、SAF `CreateDocument("text/markdown")` 导出（取消零数据修改）、ACTION_SEND 分享 |
| `ui/viewmodel/ProfileViewModel.kt` | `libraryState` 增收 `observeHighlights()`；`deleteAnnotationEntries`（批量软删 + 待撤销快照）、`undoDeleteAnnotationEntries`（只恢复该批）、`editAnnotationEntry`、`changeAnnotationEntryColor`；`annotationMsg`（id 单调递增的可撤销提示流） |
| `data/repository/NoteRepository.kt` | 新增 `restoreHighlight` / `restoreNote`（只清自身 `deleted_at`，不复活更早已删项；missing/未删为无操作）、`updateNoteBody`（只改 body，copy 保留 locator/章节/kind，revision+1） |
| `ui/screen/reader/sheets/ReaderNotesSheet.kt` | **保持 NotesSheet 签名不变**；列表与 `buildNotesExportMarkdown` 两处 `toSortedMap()`（中文标题字典序）替换为 `groupByChapterInDocumentOrder`（locator ci 文档序，无索引组殿后）——「我的」与书内面板同口径 |
| `res/values/strings_annotations.xml`（新增） | 全部新增文案入 Trae 独占资源文件，未触碰公共 strings.xml |

测试：

| 文件 | 说明 |
|---|---|
| `feature/annotations/AnnotationModelsTest.kt`（新增，8 测试） | 三类型投影、同 ID 跨表不冲突、空字段归一、locator v2 解码、positionOffset 回退 legacyOffset、无/坏 locator 降级不伪造偏移 |
| `feature/annotations/AnnotationFiltersTest.kt`（新增，8 测试） | 三筛选交集、空类型集合、关键词大小写/去空白/四字段、跨书时间倒序、**单书文档序（"第十章" ci=9 排在 "第二章" ci=1 之后，字典序陷阱回归）**、同位次新创建在前、章节分组文档序、无索引组按最新创建倒序殿后 |
| `feature/annotations/AnnotationExportTest.kt`（新增，6 测试） | 只导出选中集、三类型标注与章节回退、缺书归并一组、特殊字符（`*#[]<>`、换行）原样、多行摘录逐行引用前缀、文件名时间戳 |
| `data/repository/NoteRepositoryRestoreTest.kt`（新增，6 测试） | 恢复清 deleted_at+revision 递增、missing/未删无操作、updateNoteBody 保留定位字段 |
| `ui/viewmodel/ProfileViewModelTest.kt`（更新） | 构造参数补 `noteRepository`、stub `observeHighlights()`、首页断言「不物化高亮实体」 |
| `ui/screen/reader/sheets/NotesExportTest.kt`（更新） | fixture 补 locator ci，断言改为文档序（第一章 ci=0 先于第二章 ci=1，未分类殿后）；该测试属 Trae 笔记模块（测试 `buildNotesExportMarkdown`） |

## 必须完成项对照

1. **统一模型**：完成。稳定 ID 含类型前缀；不合并表，展示期投影。
2. **三类型收录 + 筛选 + 颜色 + 空态**：完成。空态区分「全库为空」（引导去阅读页划选）与「筛选无结果」（一键清除筛选）。
3. **摘录/批注分区 + 来源 + 文档序**：完成。单书列表按 locator 文档序；书内面板与导出分组同步改为文档序（替换两处 `toSortedMap`）。
4. **复用编辑/删除/导出 + 可撤销删除 + 保留定位**：完成。删除走软删，Snackbar 撤销只恢复最近一批；改色/编辑批注经仓储 copy 保留定位信息（单测锁定）。
5. **精确回源 + 无 locator 降级**：完成。有 locator → `reader/{bookId}?highlightId={rawId}`（AppNavigation 既有路由，SE4 同时按 id 查 highlights 与 notes，书签/批注同路径精确跳转）；无 locator → 显示「无定位」徽标、按钮降级为「打开书籍」；无 bookId → Snackbar 提示。**无需 SEAM REQUEST**。
6. **批量导出 Markdown**：完成。只导出选中集；SAF 建档（`阅读笔记-yyyyMMdd-HHmm.md`）与系统分享复用既有流程；launcher 回调 uri 为空即静默退出，零数据修改。
7. **数据一致性**：完成。「我的」与书内面板同源（同一 Room 表 Flow），编辑/删改/改色后 Flow 自动刷新，重启由 Room 持久化；灵感面板与入口未动。

## 验证记录（真实命令与结果）

工作目录 `android/`，SDK 经会话级 `ANDROID_HOME=D:\develop\Android\Sdk`（工作树无 local.properties，按 COMMON 只读主仓库取路径，未写文件）：

| 命令 | 退出码 | 结果 |
|---|---|---|
| `.\gradlew :app:testDebugUnitTest --tests "*Annotation*" --tests "*NoteRepository*" --tests "*ProfileViewModel*" --tests "*NotesExport*"` | 0 | 定向 47 测试全过（Annotation 22 + NoteRepository 17 + ProfileViewModel 3 + NotesExport 5） |
| `.\gradlew :app:testDebugUnitTest`（全量） | 0 | **1731 tests, 0 failures, 0 errors, 0 skipped**（212 个测试类） |
| `.\gradlew :app:lintDebug` | 0 | BUILD SUCCESSFUL（报告 `app/build/reports/lint-results-debug.html`） |
| `.\gradlew :app:assembleDebug` | 0 | BUILD SUCCESSFUL |

过程中修正的编译/测试问题：`foundation.border` 缺导入（编译红）、`AnnotationFilters.sortAnnotationEntries` 比较器链错接在 List 上（已改为 `compareBy<T>{...}.thenByDescending{...}`）、关键词测试 fixture 自身笔误。

androidTest 源无引用被改符号（grep 核对 ProfileViewModel/NoteRepository/导出函数等，零命中），未跑 instrumented 编译（按 COMMON 留给集成期串行门禁）。

## SEAM REQUEST

无。本轮全部需求用既有接口满足：
- 导航：`reader/{bookId}?highlightId=` 路由已存在（AppNavigation.kt:439），且 SE4 跳转逻辑同时查 highlights 与 notes，书签/批注复用同一路径。
- DAO：`getById` / `observeAllActive` / `observeByBook` 均已存在，未改 DAO/entity/schema。

## 未完成 / 风险

- **未真机验收**：全部为 JVM/编译证据，菜单可点与视觉细节（筛选栏、选择态、底栏、对话框）需 WorkBuddy 真机确认。
- 撤销语义：连续多次删除后撤销只恢复**最近一批**（符合「撤销只针对本次记录操作」），更早批次不复活；UI 未提供多级撤销。
- 跨书批量导出文件名固定为「阅读笔记-时间戳」（无单一书名）；单书全量导出仍走书内面板既有入口。
- `AnnotationFilterState` 筛选状态用 `rememberSaveable` 存类型/书/关键词（进程内含旋转恢复），跨进程冷启动不保留。

## WorkBuddy 验收路径建议

1. 「我的 → 阅读笔记」：分别造 高亮（含/不含批注）、普通批注、书签 三类数据，核对三类同列表展示、类型徽标与来源书/章节。
2. 筛选：类型多选（含空集）、书籍下拉、关键词（标题/摘录/批注/章节名）；「清除筛选」空态可一键回全量。
3. 排序：选单本书核对按文档序（可用「第二章/第十章」类书验证不按字典序）；跨书按时间倒序。
4. 回源：有 locator 条目点「定位」精确跳转正文；无 locator 历史条目显示「无定位」、按钮为「打开书籍」。
5. 编辑批注/改色：改后回书内笔记面板核对同步刷新（同一 Flow）；重启 app 核对持久化。
6. 删除撤销：单条与批量删除 → Snackbar「撤销」→ 条目回来；再删另一条 → 撤销只恢复最后一批。
7. 批量导出：选择 ≥2 条（跨书）→ 导出（SAF）与分享，核对 Markdown 结构（书分组/类型标注/摘录引用块/批注/时间）与特殊字符；SAF 对话框取消后无任何变化。