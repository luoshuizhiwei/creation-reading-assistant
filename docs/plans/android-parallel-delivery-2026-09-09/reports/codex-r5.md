# Codex R5 交付报告：素材回顾 / 摘录来源关联 / 多摘录素材卡 / 书架动态视图

日期：2026-09-12。执行目录：主仓库（参考基线 HEAD `a9a3bce`，脏工作区悉数保留）。
承接 [codex-r4.md](codex-r4.md)（单处纠错 / 普通纠错与高级规则 / EPUB APPLIED 收窄）之后继续推进 R5。
**与并行会话零文件冲突：R3/R1R2 闭环交付后开工，全程未执行 adb / 真机命令，未 stage / commit / push。**

## 0. 结论速览

| 项 | 结果 |
| --- | --- |
| I1 素材回顾（待整理/已整理/已采用 + 采用去向） | **实现完成**，JVM 0 失败 |
| I2 摘录来源关联（来源快照 + locator 精确回源） | **实现完成**，JVM 0 失败 |
| I2 多摘录素材卡（列表多选聚合 + 原条目归档可恢复） | **实现完成** |
| L1 书架保存筛选条件（动态视图） | **实现完成** |
| Codex 列：缺失书籍保留素材 / 导出与同步兼容 | **实现完成 + 架构验证**（见 §3.4/§3.5） |
| `:app:testDebugUnitTest` | **246 套件 / 2177 tests / 0 failures / 0 errors** |
| `:app:lintDebug` | **0 errors / 5 warnings**（均为既有告警） |
| `:app:compileDebugAndroidTestKotlin` | **PASS**（含 §1 的两处既有错误修复） |
| `:app:assembleDebug` | PASS，APK 51,395,872 B，sha256 `fdfb6df87f43d326…`（工作区快照，未装机） |

## 1. 门禁收尾（R3 报告 §5.2 移交的阻塞项）

`androidTest/data/repository/BookDeletionPersistenceTest.kt` 与 `feature/reader/session/ReadingSessionRecorderPersistenceTest.kt`
构造 `BookRepository` 缺 `context`（R2 checkpoint 21c5dbe 加了必填 `@ApplicationContext context`，两个旧测试未跟进）——
已补 `context = ApplicationProvider.getApplicationContext()`，`:app:compileDebugAndroidTestKotlin` 自此全绿。至此
[workbuddy-r3.md](workbuddy-r3.md) §0 的全部 BLOCKED 项清零。

## 2. 设计决策（为何零 schema 迁移）

R5 全部新数据走两条既有通道，**不新增 Room 表、不升 schema 版本**：

1. **灵感 payload JSON**（`inspirations.payload` 列）：`InspirationPayloadData` 扩展
   `adoptions`（采用去向）/ `excerpts`（多来源聚合）/ `mergedInto`（合并去向）/ `InspirationSourceInfo.locatorJson`（来源定位）。
   - 同步兼容：`SyncRepository` 的信封对 payload 是**不透明字符串**原样透传（push/pull 均如此），新字段天然同步；
   - 前向兼容：解析用 `ignoreUnknownKeys`，旧版本读到新字段忽略；
   - 导出兼容：payload 随实体行进任何导出/备份通道，无 schema 校验卡点。
2. **独立 DataStore**（`shelf_saved_views`）：L1 动态视图是轻量 UI 状态，不与书单/标签表（静态语义）混存。

## 3. 交付内容

### 3.1 I2 摘录来源关联 + 精确回源

- 「记为灵感 / AI 解读存灵感」落库时现在同时写入 **locator JSON**（与高亮/笔记同源的
  `computeLocatorJson`，携带全局 source 坐标）：`buildInspirationPayload(+locatorJson)` →
  `InspirationSourceInfo.locatorJson`。历史数据无此字段 → 详情入口隐藏，降级为既有「打开书籍」。
- 灵感详情「查阅原文位置」：走 R2 的临时查阅通道（`readerTemporaryRouteForSource` →
  `reader/{bookId}?sourceLocator=…&navigationMode=temporary`），与「我的 → 阅读笔记」同一判据——
  无有效全局偏移不显示入口、不伪造 offset=0；返回后回到原阅读位置（J1 语义）。
- `AppNavigation` 灵感页新增 `onOpenRoute` 直通导航；`InspirationRoute` 处理
  `InspectSourceLocator`，书籍已删除时诚实提示「来源书籍已删除，摘录内容仍保留」。

### 3.2 I2 多摘录素材卡

- 灵感中心列表新增**多选模式**（类型行「多选」开关，选中卡片高亮描边，底部操作条）；
  选 ≥2 条 →「合并为素材卡」：新卡状态「待整理」，正文按来源快照拼接，
  `payload.excerpts` 逐条保留《书名》·章节 + 摘录文本 + locator（每条可独立定位）；
  tags/categoryIds 取并集（保序去重）。
- 被合并的原始条目**归档**（不删除）并记 `payload.mergedInto = 卡id`，可从「已归档」筛选恢复——非破坏聚合。
- 素材卡详情新增「聚合摘录 · N 条来源」区块，逐条展示快照与「定位」入口。

### 3.3 I1 素材回顾 + 采用去向

- 列表页新增**回顾管线胶囊行**：未整理 / 待整理 / 已整理（=可使用+已打磨聚合筛选）/ 已采用，带实时计数；
  `statusMatchesPipeline` / `pipelineCounts` 纯函数 + 测试锁定（`organized` 是聚合筛选键，不落库）。
- 详情新增「采用去向」区块（契约 8：采用记录与原文摘录/用户想法/AI 候选分开）：
  记录**文字去向**或**链接去向**（value + 可选备注 + 时间），支持删除；
  首次记录自动把素材状态推进为「已采用」（后续记录不回退状态，去留由用户控制）。
- 校验失败（value 空白 / kind 非法）不落库并明确反馈。

### 3.4 Codex：缺失书籍保留素材

- 既有契约验证：`inspirations.source_book_id` 外键 `SET_NULL`（删书不删素材），
  摘录文本与《书名》快照在 payload 中自持——书删后素材与摘录仍完整可读；
  来源定位入口在书籍不存在时隐藏并明确提示（route 已校验 books 表）。
  `InspirationMaterialOpsTest` 锁定 payload 自持语义。

### 3.5 Codex：导出 / 同步兼容

- 架构验证：同步信封对 `inspirations.payload` 透明（`applyInspiration` 直接 `payload.toString()` 落库，
  push 侧对称）；桌面端读取 payload 用 `ignoreUnknownKeys` 口径，新字段不会造成解析失败。
- L1 动态视图在独立 DataStore 文件（`shelf_saved_views`），不进入任何实体同步范围，无导出风险。

## 4. 文件清单

主源码（修改）：`InspirationViewModel`（payload 模型扩展 + addAdoption/removeAdoption/mergeIntoMaterialCard/payloadFor）、
`InspirationPage`（InspectSourceLocator/MergeToMaterialCard 动作）、`InspirationRoute`（+onOpenRoute）、
`InspirationScreen`（payload 透传）、`InspirationDetail`（聚合摘录/采用去向/查阅原文位置）、`InspirationList`（管线胶囊 + 多选合并）、
`ReaderHelpers`（buildInspirationPayload+locatorJson）、`ReaderLayerBuilders`（SheetHost 回调加 computeLocatorJson，两个落库点快照 locator）、
`ReaderScaffold`（透传）、`ShelfViewModel`（savedFilters/saveCurrentFilter/applySavedFilter/deleteSavedFilter/hasActiveFilters）、
`ShelfRoute`（收集 + FilterSheet 接线）、`FilterSheet`（动态视图区块）、`androidTest` 两处 `context` 修复。
主源码（新增）：`ui/viewmodel/InspirationMaterialOps.kt`（纯函数）、`data/settings/ShelfSavedViewsStore.kt`。
测试（新增/扩展）：`InspirationMaterialOpsTest`（10 项：向后兼容/去向校验/聚合/管线）、`ShelfViewModelTest`（+3：动态视图套用/保存/判定）、
`InspirationViewModelTest`/`InspirationDetailStateTest`/`ShelfFilterLogicTest` 等既有套件回归全绿。

**跨轮文件接触说明**：`ReaderLayerBuilders.kt` / `ReaderScaffold.kt` 属 R3 已交付文件——本轮仅做**加性**锚点编辑
（新增带默认值的可选参数 + 两处落库点各加一行快照），未改动 R3 的任何既有行；workbuddy-r3 报告 §1 已确认其改动与本轮零交集。

## 5. 未完成项 / 下一步

- **真机验收（WorkBuddy，需设备窗口）**：R3 矩阵（workbuddy-r3.md §6 全部 TODO）+ R5 矩阵：
  摘录存灵感 → 详情「查阅原文位置」临时查阅 → 返回位置不变；多选合并素材卡 → 原条目归档可恢复 → 卡内逐条定位；
  采用去向记录 → 状态转已采用；书架动态视图保存/套用/删除 + 与静态书单语义区分；删书后素材保留。
- **A5 SEAM REQUEST（笔记入口策略）**：R1R2 闭环报告提出三选项待产品决策；本轮 R5 的灵感中心管线筛选已让
  「以状态/类型回看素材」可达，选项 B（灵感 tab 内加笔记筛选）可在下轮顺势实现，本轮未动。
- **B7 SEAM REQUEST（修复搜索按钮）**：`rebuildAll()` 零 UI 入口仍在，设置页接线留待集成窗口。
- Markdown 净化维持「不实施」（R4 报告已登记）；Robolectric 维持不引入（B9，长期 TODO）。
