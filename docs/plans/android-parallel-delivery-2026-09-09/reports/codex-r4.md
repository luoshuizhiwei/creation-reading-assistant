# Codex R4 交付报告：单处纠错 / 普通纠错与高级规则 / 各格式安全映射收口

日期：2026-09-11。执行目录：主仓库 `D:/develop/Code/Codex/creation-reading-assistant`（工作区参考基线 HEAD `a9a3bce`，脏工作区按用户资产保留）。
**与 R3（WorkBuddy 正在实施：设置两级 / 选区动作 / 词典）全程并行，未触碰 R3 所有权内任何文件，未执行任何 adb / 真机命令。**

## 一、本轮范围与实现内容

R4 三列任务（E1 界面 / E2 记录与执行 / 各格式安全映射与一致性）由本会话一并实现：

### 1. E2 单处纠错（新增能力）

- **锚点模型**：`RuleModels.CorrectionAnchor(sourceStart, sourceEnd, findText)`——全书 source 坐标（半开区间）+ 保存时该区间在 display 空间的文本（用户所见）。`ReplaceRule` 新增可空 `anchor` 字段（默认 null，历史构造零破坏）。
- **持久化**：新表 `reader_text_corrections`（v12→v13 迁移 + `app/schemas/...13.json` 快照 + `AppDatabaseMigrationTest.migrate_12_to_13_*`）。实体含 status（ACTIVE/UNDONE），撤销=翻状态保留历史，恢复可逆；`book_id` 外键 CASCADE。
- **执行语义（投影层叠加）**：`ReplaceProjection.projectScoped(sourceText, rules, bookId, scopeSourceBase)`——普通正则规则先按既有链应用；锚定纠错最后叠加：锚点按作用域基址局部化 → floor 映射换算到当前 display → **内容校验**（display 子串逐字等于 findText）→ 精确拼接。floor 端点在变长规则后存在插入/删除边界歧义时，于 16 字符有界窗口内按 findText **精确匹配就近定位**——内容校验始终严格，任何漂移/越界/重叠冲突一律诚实跳过，绝不近似替换。
- **撤销**：`UndoCorrection` / `RestoreCorrection` 命令；撤销后 profile key 变化 → 投影缓存/分页/搜索身份自动失效，正文回落原文。
- **消费点一致性**：纠错骑在 `RuleSnapshot.effectiveReplace` 内（id 前缀 `correction:`、position=Int.MAX_VALUE），因此 TXT 分页（1:1 与流式 segmented）、EPUB 结构保真投影、滚动路径（ScrollUnitProjection）、TTS、高亮/选区坐标映射**全部自动一致**，无需各自接线。
- **搜索 display 通道**：`DisplayChannelIndexer` 正文投影改走 `projectScoped`，`IndexUnit` 新增 `chapterSourceStart`（TXT=detecter 真实章起点 / EPUB=LegacyOffsetCodec 估算基址 / 预览与元数据=0）；`SearchOffsetResolver` 回放侧同口径按章基址组合投影。索引侧与 resolve 侧同源同口径，纠错后搜索 display 基准与阅读器渲染一致。

### 2. E1 普通纠错与高级规则界面（ReaderRulesSheet）

- **普通/高级双模式**：普通模式=「查找文本/替换为」两个纯文本框（`Regex.escape` 自动转义，用户零正则知识）；高级模式=既有正则编辑。`pattern` 是单一事实源，`simpleFind` 为 UI 镜像（`withSimpleFind` 同步转义、切回普通模式经 `unescapeRegexEscapeOrNull` 反解，仅纯字面量规则可回到普通模式）。
- **替换目标选择器**：单处纠错 / 本书替换 / 全局规则三选。选区入口（选中文字→替换）默认**单处纠错**；手动新增无选区上下文时纠错项不出现并说明原因。
- **前后对照**：规则草稿保留既有全书预览节选；单处纠错预览直接给出「查找文本 → 替换文本」对照（命中固定为这一处）。
- **应用反馈分离**：保存成功反馈 =「已保存」+ 由真实能力裁决派生的正文状态子句（`Available(bodyNotice)` 追加降级说明 / `Unavailable` 追加保留原文说明），不再把「保存成功」表述成「正文已应用」。新增 `RuleMutationResult.NotAnchorable(reason)`（无选区锚点 / 校验拒绝时的明确反馈）。
- **纠错记录区**：替换净化 tab 内列出单处纠错（原文/替换/生效中/已撤销），支持撤销与恢复。

### 3. 各格式安全映射收口

- **EPUB `APPLIED` 收窄（R1 遗留缺陷 B.3）**：`preparePagedReplacement` 不再对 EPUB 无条件给 `APPLIED`。章长只有 ZIP 字节估算上界（charLength ≤ byteBound），据此单向判定：上界未超限的章保证可精确投影（零解析成本）；上界超限的候选章在装配期解析真实长度验证（预算 16 章 / 4MB 字节估算），分类出 APPLIED / PARTIALLY_APPLIED / ALL_SCOPES_OVERSIZED；预算外诚实降级为新枚举值 `UNVERIFIED_CHAPTER_LENGTHS`（规则仍可管理，渲染层逐章精确裁决）。`maxSourceLength` 现在转发给 `EpubReplacedChapterSource`，装配期分类与渲染期裁决使用同一上限（修复了本轮实现中发现的两处口径不一致）。
- **Markdown**：按 R1R2 缺陷清单 B.8 的第二选项，本轮**明确不实施** Markdown 净化（保持 `NON_SOURCE_COORDINATES` 门控与入口隐藏），状态已在本文档登记，不再是悬空 TODO。
- **规则失效缓存**：既有 `ReplaceProfile.key` 机制扩展覆盖锚点三字段；无锚点规则的 key 与历史版本逐字节一致（既有分页缓存/搜索索引身份不失效）。纠错增删/撤销改变 key → LRU 投影缓存与搜索 display 索引自动失效。

## 二、文件清单（全部在替换净化/投影/搜索索引/规则 UI 领地，R3 零接触）

主源码（修改）：`RuleModels` `RuleEngine` `ReplaceProfile` `ReplaceProjection` `BoundedReplaceProjector` `EpubReplaceProjector` `ReplacedChapterSource`（含 prepare 分类）`EpubReplacedChapterSource` `RulesRepository` `ReaderViewModel` `ReaderRulesSheet` `ReaderReplacementCapability` `SearchTokenAggregator`（IndexUnit/DisplayChannelIndexer）`SearchOffsetResolver` `SearchIndexRepository` `AppDatabase`（v13）`DatabaseModule`。
主源码（新增）：`data/local/entity/ReaderCorrectionEntity.kt`、`data/local/dao/ReaderCorrectionDao.kt`。
测试（新增）：`CorrectionProjectionTest`（14 项）。测试（扩展）：`RulesRepositoryTest`（+5，共享 `FakeReaderCorrectionDao`）、`RuleEditorDraftTest`（+11）、`EpubReplaceProjectorTest`（+3）、`ReplacedChapterSourceTest`（+4：TXT 链纠错 + EPUB 可用性 3 态）、`SearchOffsetResolverTest`（+2）、`AppDatabaseMigrationTest`（+12→13）。
接线最小化说明：**未修改** `ReaderSheetHost` / `ReaderScreen` / `ReaderLayerBuilders` 等 R3 正在改动的宿主文件——纠错锚点由 `ReaderViewModel` 在命令执行前从既有选区状态回查（判别式与 `ReaderScaffold`/`ReaderProgressActions` 既有消费点一致），快照经 `RuleSnapshot` 扩展字段（带默认值）向后兼容穿透。

## 三、门禁与验证（真实结果）

| 命令（android/） | 结果 |
|---|---|
| `:app:testDebugUnitTest`（全量） | **BUILD SUCCESSFUL：245 套件 / 2164 tests / 0 failures / 0 errors / 0 skipped** |
| `:app:compileDebugAndroidTestKotlin` | 本轮新增代码编译通过；另有 **2 处本轮之前已存在的错误**（见 §四.1） |

- 定向子集复跑：`feature.reader.rules.*` / `feature.reader.pager.*` / `feature.search.*` / `ui.screen.reader.sheets.*` / `ui.screen.reader.*` / `ui.viewmodel.ReaderViewModelTest` / `ReaderDocumentLoaderProfileTest` / `data.repository.*` 全绿（含上述新增用例）。
- 真机验收：按分工不在本轮执行，全部 E1/E2 与 EPUB 可用性矩阵待冻结集成后由 WorkBuddy 验收。
- 已知取舍（诚实登记）：纠错跨块（EPUB）不生效并跳过；纠错窗口定位在「16 字符内存在两处相同查找文本」的极端情形可能选中邻近同文位置（内容恒精确，仅位置歧义）；Markdown 净化维持不实施。

## 四、SEAM REQUEST / 集成者注意

1. **既有 androidTest 编译错误（非本轮引入，HEAD `21c5dbe` 起即存在）**：
   `androidTest/.../data/repository/BookDeletionPersistenceTest.kt:62` 与 `feature/reader/session/ReadingSessionRecorderPersistenceTest.kt:63` 构造 `BookRepository` 缺 `context` 参数（R2 checkpoint 给 `BookRepository` 加了 `@ApplicationContext context`，两个仪器测试未跟进）。修复=两处调用补 `context = ApplicationProvider.getApplicationContext()`（或等价）。归属删除/会话切片 owner，本轮未越界代改。
2. **schema**：`app/schemas/...13.json` 已由 KSP 生成并入库待提交；迁移测试 `migrate_12_to_13_adds_reader_text_corrections` 需在设备可用时随 androidTest 门禁执行。
3. **搜索索引**：纠错保存/撤销/恢复走既有 `SearchIndexRefreshPlan`（PER_BOOK → `reindexBookById`）；全局规则路径不变。
4. **并行安全**：本轮所有编辑文件与 R3 边界（`data/settings/**`、`ReaderSettingsSheet`、`SettingsViewModel`、`ReaderSelectionToolbar`、`ReaderSelectionExternalActions`、`feature/dictionary/**` 等）及 WorkBuddy 正在改动的宿主文件零交集；`RuleCommand`/`RuleMutationResult`/`PagedReplacementAvailability` 的新增变体已核查全部外部消费点（仅 `is` 检查与具体构造，无跨文件穷尽 when）。

## 五、未完成项 / 下一步

- 真机矩阵（WorkBuddy）：单处纠错保存→正文应用→撤销还原（TXT/EPUB 各一）；普通/高级模式切换与前后对照；保存反馈的降级文案；EPUB 超大章书（测试EPUB超大章）现在应显示「部分章节…需载入后确认」而非无声 APPLIED；纠错后搜索（display 基准）命中与精确到达。
- 桌面端 reader 索引 display 反查边界（R1R2 遗留 #7）与 `EPUB 双通道` 覆盖率补齐不在本轮。
