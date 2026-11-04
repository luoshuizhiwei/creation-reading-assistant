# 阅读→笔记→回源→返回 只读预检 v2（源码基线修正 + 删书语义 + 最小验收合同）

## 任务编号/版本

`CRA-READ-NOTE-RETURN-PRECHECK-v2` / v2，2026-10-03。修正 v1 的源码基线与删书语义，产出可直接执行的最小验收合同。**不重新调查全部功能**。

- **执行应用/模型**：Claude Code（桌面 Code 标签页）；实际路由标识 `hw726brokbuddy AI/global:deepseek-v4.1-flash`
- **执行标识**：`CRA-READ-NOTE-RETURN-PRECHECK-v2-20261003-0930`
- **性质**：**纯只读静态预检**。未构建、未测试、未 ADB/MuMu、未改产品、未改 current/TODO、未动 Git 状态。**下文所有结论均为静态源码结论，不含任何运行 PASS。**
- **v1 原报告保留未覆盖**：`docs/coordination/dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v1.md`

## 输入指纹

| 项 | 值 |
|---|---|
| 工作仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| HEAD（预期=实际） | `e39a78da9c62f02614207cffb36969a32492eaea` |
| 暂存区 | 空 |
| **唯一源码事实输入** | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（883 文件） |
| `final-manifest.json` SHA-256 | `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d` |
| 清单 scope / 载荷核对 | 25 / **25 路径 SHA 全等**（0 不匹配） |
| 既有交付核对 | v1 报告与 `next-round-read-note-return-precheck-v1` 工件存在；v2 目录此前不存在，本任务新建 |
| 上游补正 | `docs/coordination/reviews/2026-10/2026-10-03-next-three-deliveries-codex-review.md`（Standards §3 指出 v1 三个 SHA 取自 D 工作树） |

## 所有权与改动

- **唯一可写目录**：`E:\creation-reading-assistant\next-round-read-note-return-precheck-v2`（`execution.json`、`evidence/*.json`）
- **唯一可写仓库报告**：本文件
- **零产品写入**：未改产品源码、测试、runner、配置；未改 `docs/handoff/current.md`、项目页、TODO 或他人报告；未 commit / push / reset / checkout
- **零构建 / 零设备**：未调用 Gradle、ADB、MuMu；未占用设备
- **未读其他应用私有库**（含 Claude/Cowork）；未扩展 AI、续读卡、索引工程或任何新功能
- v1 报告与其 E 工件保持只读未改

## 环境

Windows 11 / Git Bash；Python 3.13（只读 SHA 与源码检索）。未启动 JDK/Gradle。设备未连接、未使用。

---

## 执行与结果

### §1 源码基线修正（v1 误用 D 工作树）

**方法**：以冻结 E 候选为唯一事实输入，对链上 25 个文件重算 SHA-256，并与 `git show HEAD:<path>` 逐文件比对；同时判定该路径是否属于 25 文件载荷。

| 文件（`android/app/src/main/java/com/creationreadingassistant/` 下） | E 候选 SHA-256（前16） | HEAD SHA-256（前16） | 在 25 载荷 | v1 声称 | 判定 |
|---|---|---|---|---|---|
| `ui/screen/reader/ReaderProgressEffects.kt` | `7e4b4ab884f1d61c` | `7e4b4ab884f1d61c` | 否 | `a7baa349494e328b` | **v1 错**（候选=HEAD） |
| `ui/screen/reader/ReaderProgressActions.kt` | `eaacfb9be8dda2a1` | `eaacfb9be8dda2a1` | 否 | `933513e1400b852f` | **v1 错**（候选=HEAD） |
| `ui/screen/profile/ProfileRoute.kt` | `8fde6ce2301a090b` | `99eb2aaf558b5e81` | **是** | `a1cad5557edb853e` | **v1 错**（且候选≠HEAD） |
| `ui/viewmodel/ProfileViewModel.kt` | `18b525139ff9cc7c` | `9ef25b8f4543f70d` | **是** | 未列 | 候选≠HEAD |
| `feature/annotations/AnnotationModels.kt` | `a92fbb362571dbbd` | 同 | 否 | `a92fbb362571dbbd` | OK |
| `feature/annotations/AnnotationNavigationTarget.kt` | `90eaaa00f423cba9` | 同 | 否 | `90eaaa00f423cba9` | OK |
| `ui/navigation/ReaderTemporaryRoute.kt` | `4b577e368c0fa3e4` | 同 | 否 | `4b577e368c0fa3e4` | OK |
| `ui/navigation/TemporaryReadingNavigationViewModel.kt` | `c0eed88f46d2980b` | 同 | 否 | `c0eed88f46d2980b` | OK |
| `data/repository/NoteRepository.kt` | `9896873c3d448602` | 同 | 否 | `9896873c3d448602` | OK |
| `feature/reader/navigation/SourceNavigationContract.kt` | `82361a5223d45716` | 同 | 否 | `82361a5223d45716` | OK |

**结论**：

1. v1 的 9 行 SHA 表中 **3 行错误**，且三者的错误值都既不是候选也不是 HEAD —— 属 D 工作树值，已确认不可复用。
2. 链上**只有 2 个文件受候选影响**：`ProfileRoute.kt` 与 `ProfileViewModel.kt`，二者都在 25 文件载荷内，**候选 ≠ HEAD**。其余 23 个文件候选 == HEAD。
3. 因此「该路径不在 25 载荷内」**只能推出候选==HEAD 需另行验证**，不能反过来证明；v1 的推断方式不成立，本报告改为逐文件实算。

**行号修正**（v1 行号同样来自 D 工作树，与冻结 E 不一致）：

| 关键点 | v1 行号 | **冻结 E 实际行号** |
|---|---|---|
| `canPersistNormalReadingProgress` 定义 | `733-738` | **`ReaderProgressEffects.kt:712`**（使用点 `:264`、`:328`） |
| 位置上报判空 | `856-858` | **`ReaderProgressEffects.kt:856`** |
| `pendingHighlightId` 消费块 | `534-621` | **`ReaderProgressEffects.kt:528-546`** |
| `resolveAnnotationTarget` 调用 / 未定位提示 | `560-568` | **`:538` / `:542`** |
| APPROXIMATE 提示 | `601-603` | **`:578-579`、`:625-626`、`:637-638`** |
| `jumpToEntry` / 未关联书籍提示 | `241-246` / `234-237` | **`ProfileRoute.kt:247` / `:251`** |
| 类型化路由 / 降级路由 | — | **`ProfileRoute.kt:256-261`** |
| `inspectSourceTemporarily` / 无坐标提示 | — | **`ProfileRoute.kt:267` / `:275-278`** |
| `AnnotationEntry` / `hasLocator` / 两处 `toAnnotationEntry` / `buildAnnotationEntries` | `31-51` 等 | **`AnnotationModels.kt:31` / `:43` / `:56` / `:82` / `:107`** |
| `encodeSourceLocator` / `readerTemporaryRouteForSource` / `hasTemporaryInspectionSource` / `resolveTemporaryReturnRoute` | `44-54` / — | **`ReaderTemporaryRoute.kt:44` / `:83` / `:108` / `:142`** |
| `resolveAnnotationTarget` | `67-79` | **`AnnotationNavigationTarget.kt:67`** |

### §2 真实 E 中的接线复核 —— **笔记入口不可达（最重要的结论修改）**

v1 主张的最小旅程是「我的 → 阅读笔记 → 点击回源」。在冻结 E 上逐点复核后，**该入口不存在**：

| 复核点 | 事实 | 证据 |
|---|---|---|
| `ProfileSubPage.NOTES` 是否有打开者 | **没有任何 `OpenSubPage(ProfileSubPage.NOTES)` 调用** | 全 UI 的 `OpenSubPage` 目标共 15 个：WEBDAV/TAGS/SYNC/STORAGE/SHELVES/SELECTION/READING/READER/PRIVACY/GOAL/DIAGNOSTICS/CATEGORIES/APPEARANCE/AI/ABOUT，**NOTES 不在其中** |
| 「笔记」磁贴 | **已被产品移除** | `ProfileHomeScreen.kt:189` 注释原文：「快捷导航磁贴（产品规划无独立笔记概念：原「笔记」磁贴改为灵感中心直达）」 |
| 路由是否可解析 | 可解析但**无人导航** | `AppNavigation.kt:480-484` 注册 `profile/{subPage}`；`profileSubPageFromRoute` 认 `notes`；全源码检索无任何 `profile/notes` 导航调用 |
| `jumpToEntry` 的 UI 宿主 | **唯一**：`ReadingNotesSubPage.kt:184` | 主源码中除实现 `ProfileRoute.kt:247` 外，只有该处调用 |
| `inspectSourceTemporarily` 的 UI 宿主 | **唯一**：`ReadingNotesSubPage.kt:201` | 主源码中除实现 `ProfileRoute.kt:267` 外，只有该处调用 |
| `AnnotationNotesPage` 可达性 | **仅** `ReadingNotesSubPage.kt:60`，而该 Composable 仅在 `page != READING`（即 `page == NOTES`）时渲染 | `ReadingNotesSubPage.kt:51-62`；`ProfileScreen.kt:120-121` |

**⇒ v1 的旅程按原样不可执行。** 这是 v1 的**事实性错误**（不是版本差异：`ProfileHomeScreen.kt` 与 `ReadingNotesSubPage.kt` 均等于 HEAD）。

**冻结 E 中实际可达的入口**（按动作分列，不混为一条）：

| 动作 | 可达入口 | 说明 |
|---|---|---|
| **查看摘录/批注** | 书架 → 书籍详情 `BookDetailSheet` → `BookDetailNotesSection` | **只读展示**，无回源动作；`ShelfRoute.kt:368-369` 传入按书的 notes/highlights |
| **普通回源（书内）** | 阅读器 → 笔记面板 `ReaderNotesSheet:319`（高亮）/`:346`（笔记）→ `ReaderSheetHost:394` / `:397` → `onPendingHighlightIdChange` | **仅同书内跳转**；不携带跨书语义 |
| **跨书回源（带 locator 的 typed 路由）** | **无 UI 入口** | 实现存在于 `ProfileRoute.kt:256-261`，宿主 `ReadingNotesSubPage` 不可达 |
| **临时查阅** | **无 UI 入口** | 实现与路由构造存在于 `ProfileRoute.kt:267` / `ReaderTemporaryRoute.kt:83`，宿主同上不可达 |
| 书内笔记面板的临时查阅 | **不存在** | `ReaderNotesSheet` 无任何 `inspectSource*`/`Temporary` 接线 |

**这条需要 Codex/用户裁决**：`ProfileHomeScreen.kt:189` 的注释表明移除**可能是有意的产品决定**（「产品规划无独立笔记概念」）。若是，则应把本旅程重新定义为「书架→书籍详情查看 + 书内笔记面板回源」，并把 `ReadingNotesSubPage` / 两个 actions 的跨书分支视为**已退役但仍留在源码中的死代码**；若不是有意，则是「已实现但不可达」的接线缺口。**本报告不擅自改合同，只如实列出两种解释与各自的判定依据。**

### §3 删除语义三分（修正 v1 的「不 join books」结论）

| 状态 | 实现位置 | 书行 | 笔记/高亮行 | 摘录是否仍可查 |
|---|---|---|---|---|
| **① 仅移除正文** | `BookRepository.clearBookCache` → `BookRepository.kt:291` | **保留（active）** | **不动（active）** | **是** |
| **② 软删除 / 撤销** | `applyDeletionLocked` → `BookRepository.kt:237-259`；`restoreBook` → `:543`；`restoreDeletions` → `:595` | `bookDao.softDelete` 置 `deleted_at` | 同一次操作时间戳置 `deleted_at` | **否**（被 active 流隐藏），可撤销恢复 |
| **③ 物理删除书行** | **主源码中不存在** | — | 若发生则按 FK CASCADE 消失 | 若发生则**否** |

**① 仅移除正文**（`BookRepository.kt:291`）：只改 `content_status="missing"`、`local_uri=null`、`local_content_path=null`，并清 `book_content` 的 preview/epub_json、软删 `book_files`。**书行保持 active，因此 notes/highlights 完全不受影响。**

**② 软删除与撤销**：`applyDeletionLocked` 对 book / progress / sessions / notes / highlights **全部写同一个 `deleted_at` 戳**（`BookRepository.kt:241-245`），随后 `bookTagDao.clearByBook` 等关系表是**真删**。撤销（`restoreBook:543-577`）按「`deleted_at` 等于该次操作戳」精确恢复，因此**不会复活此前其他已删除项**。DAO 层的 active 过滤是 `deleted_at IS NULL`：

- `NoteDao.observeAllActive`：`SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC`
- `HighlightDao.observeAllActive`：`SELECT * FROM highlights WHERE deleted_at IS NULL ORDER BY created_at DESC`
- 二者**均不 join books**（`InspirationDao.kt:63-97` / `:99-135`）

**③ 物理删行**：全 `app/src/main` 检索 **无 `DELETE FROM books`**；`BookDao`、`NoteDao`、`HighlightDao` 均**无 `@Delete`**；`BookDao` 的删除原语只有 `@Query("UPDATE books SET deleted_at = :ts ...") softDelete`（`BookDao.kt:108-109`）。

- schema 层确有级联：`notes.book_id → books.id ON DELETE CASCADE`（`InspirationEntities.kt:70-79`）、`highlights.book_id → books.id ON DELETE CASCADE`（`InspirationEntities.kt:111-117`）。
- **但现行产品代码路径不产生物理行删除，CASCADE 不会被触发。** 它是 schema 声明，不是可达行为（外部直接改库或将来新增硬删功能才会触发）。

**⇒ v1「不 join books 表 ⇒ 原书缺失时摘录仍可读」的推理不成立**：不 join 只证明查询不依赖 books 行，**不能证明行还在**。② 状态下 active 流会把摘录一并隐藏；③ 状态下若真发生则被 CASCADE 删除。v1 建议的验收「删除书籍行后打开笔记页 → 断言摘录正文仍显示」在 ② 下会**失败**，在 ③ 下（若可达）也会**失败**。

### §4 原书缺失验收锁定为「仅移除正文」

按任务要求「优先验证仅移除正文后摘录仍可查看与回源失败解释，不擅自改变现有删除合同」，本报告把验收状态**明确锁定为 ① 仅移除正文**，并给出失败解释的源码出处：

| 格式 | 回源时的失败解释（源码原文） | 位置 |
|---|---|---|
| TXT | 「本书暂无可阅读的正文（需重新导入或同步下载）」 | `ReaderDocumentLoader.kt:167` |
| Markdown | 「本书暂无可阅读的 Markdown 正文（需重新导入或同步下载）」 | `ReaderDocumentLoader.kt:104` |
| EPUB | 「本书缺少可读取的 EPUB 文件，请重新导入」 | `EpubRepository.kt:57` |
| 书行已软删 | 「书籍不存在或已被删除」 | `ReaderDocumentLoader.kt:58` |
| 未关联书籍 | 「该记录未关联书籍，无法跳转」 | `ProfileRoute.kt:251` |
| 有 bookId 但无法定位条目 | 「无法定位该阅读笔记，记录可能已删除或类型不匹配」 | `ReaderProgressEffects.kt:542` |

错误经 `ReaderViewModel.kt:698`（`errorMessage = error.message ?: "打开失败"`）传到 `ReaderScreen.kt:221`（`val error = runtimeError ?: documentUiState.errorMessage`）显示。

**① 状态下的完整预期**：书行 active + 笔记 active ⇒ 摘录在可达的展示面（书籍详情页）仍可查；但**回源不再可能**（正文文件已删，阅读器装载失败并给出上表对应文案）。**这与 ②/③ 是两个不同合同，不得混记。**

### §5 两入口不混（普通回源 vs 临时查阅）

| 维度 | 普通回源 `jumpToEntry` | 临时查阅 `inspectSourceTemporarily` |
|---|---|---|
| 实现 | `ProfileRoute.kt:247-263` | `ProfileRoute.kt:267-279` |
| 目标路由 | `reader/$bookId?highlightId={Uri.encode(typed)}`（有 locator）/ `reader/$bookId`（无 locator 降级，**不伪造偏移**） | `readerTemporaryRouteForSource(...)`（`ReaderTemporaryRoute.kt:83`）带 `navigationMode=temporary` |
| 是否携带 highlightId | **是**（typed stableId） | **否**（只带 source 坐标） |
| 语义 | 改变 / 延续**普通阅读位置** | 一次性可回退查阅，**不改普通位置** |
| 入口可见性判据 | 总是可用（无 locator 时降级） | `hasTemporaryInspectionSource(bookId, legacyOffset)`（`ReaderTemporaryRoute.kt:108`），不满足则**连按钮都不渲染** |
| 失败文案 | 无坐标时降级打开书籍 | 「该记录没有可定位的原文位置」（`ProfileRoute.kt:275-278`） |
| 返回 | 系统返回即回上一页 | `resolveTemporaryReturnRoute`（`ReaderTemporaryRoute.kt:142`）LIFO，末层不带 `navigationMode` 恢复普通语义 |

**两者是两条独立路径**（独立方法、独立路由、独立可见性判据、独立返回语义），v1 已区分；本报告确认该区分在冻结 E 上成立，并补齐了行号与判据出处。

**临时查阅不污染普通位置**（源码支持）：`canPersistNormalReadingProgress`（`ReaderProgressEffects.kt:712`）＝ `!initialPositionPending && !temporaryInspection`，在 `:264`、`:328` 两处落库门槛与 `:856` 上报判空共同生效。

### §6 最小完整旅程、必要断言、可复用证据与所有权建议

**前置裁决**：先由 Codex/用户就 §2 的入口问题定性。下列合同给出**两条分支**，只执行其中一条。

#### 分支 A（若「笔记页退役」是产品决定 —— 依据 `ProfileHomeScreen.kt:189` 注释）

**最小完整旅程（三格式各一遍）**：
> 导入中性测试书（TXT / EPUB / MD 各一）→ 打开书阅读 → 选中文字保存高亮（含批注）+ 保存一条摘录笔记 → **杀进程冷启动** → 书架 → 书籍详情，断言摘录与批注分区可见 → 进入阅读器 → 打开书内笔记面板 → 点击该条 → 断言跳转到原文对应位置 → （对带 source locator 的条目）**断言书内面板不提供临时查阅入口**（与 §2 接线一致）。

**必要断言**：
1. 书籍详情页的摘录 = 保存时的 `excerpt`，批注 = `annotation`，**二者分区**（`AnnotationModels.kt:56/:82`）。
2. 冷启动后仍可见（Room 持久化，非内存）。
3. 书内面板点击后到达 `legacyOffset` / `chapterIndex+charOffset` 对应位置。
4. 书内面板**没有**临时查阅按钮（冻结 E 事实）。

#### 分支 B（若「笔记页不可达」是接线缺口）

在分支 A 之外，额外要求**先恢复入口**（`ProfileSubPage.NOTES` 的打开者），再执行 v1 原意旅程：
> … → 我的 → 阅读笔记 → 跨书列表点击条目**普通回源** → 到达原文 → 对带 source locator 的条目点击**临时查阅** → 跳到他处 → 「返回阅读处」→ 断言普通阅读位置与 marker/locator **未被覆盖**。

#### 共同必须包含的一项（两分支都要）：**仅移除正文**

> 对其中一本书执行「清空离线 / 移除正文」（`clearBookCache`）→ 断言：书行仍在书架且状态为「正文未在本机」（`ShelfModels.kt:37`）→ 断言摘录**仍可查看**（书籍详情页）→ 点击打开该书 → 断言出现对应格式的失败文案（§4 表）且**不跳转、不崩溃**。

#### 明确不做的

- 不改现有删除合同（软删除 + 撤销语义保持原样）
- 不把「删除书籍行后摘录仍可读」列为验收项（§3 已证不成立）
- 不因本预检重写笔记层（未发现需要重写笔记层的已证实缺陷）

#### 可复用测试（已按冻结 E 实算存在性，修正 v1 的两个不存在项）

| v1 列出的名字 | 冻结 E 实际情况 |
|---|---|
| `AnnotationNavigationTargetTest` | **存在** `test/.../feature/annotations/` |
| `AnnotationModelsTest` | **存在** |
| `ReaderNoteSnapshotSaveTest` | **存在**（含「选区清空后仍存快照摘录与同一快照 locator」） |
| `TemporaryReadingJourneyTest` | **存在** `test/.../feature/reader/navigation/` |
| `ReaderTemporaryRouteJourneyTest` | **存在** `test/.../ui/navigation/` |
| `ReaderTemporaryBackTest` | **存在** `test/.../ui/screen/reader/` |
| `AnnotationTemporaryInspectionEntryTest` | **存在**（含「valid source builds temporary route carrying sourceLocator only」「chapter-only source without global offset degrades to null route」） |
| `NoteRepositoryRestoreTest` | **存在**（含 restore 清 `deleted_at`、对已存活/缺失行 no-op） |
| `StoredProgressLocatorNavigationTest` | **不存在**（最接近：`ReaderProgressNavigationTest`） |
| `MarkdownBlockSelectionLocatorTest` | **不存在**（最接近：`MarkdownSelectionPositionTest` / `MarkdownScrollHighlightTest`） |

**另建议纳入**（实算存在且与本链直接相关）：`ReaderTemporaryInspectionUiTest`、`ReaderTemporaryRouteTest`、`TemporaryReadingNavigationViewModelTest`、`SourceNavigationContractTest`、`LocatorCodecTest`、`NotesExportTest`。

#### 建议测试文件所有权

- **唯一 owner**：本链新增/修改的仪器与 JVM 测试；范围限定在 `app/src/androidTest/.../feature/annotations|reader|sync` 与 `app/src/test/.../feature/annotations|reader|ui/navigation` 之下
- **产品源码**：本轮**不建议**改动。若分支 B 判定为缺口，最小改动面限定为 `ui/screen/profile/ProfileHomeScreen.kt`（恢复一个入口）或 `ui/navigation/AppNavigation.kt`（新增可达路由），**不进入** `feature/annotations/*` 与 `feature/reader/locator/*`
- **设备**：MuMu 独占，与 CRA-INTEGRATION-MUMU-ACCEPT 系列串行；隔离 `.qahost` 新 UID；中性测试书；不碰正式书库

#### 停止条件

- 分支 A 三格式全通过 ⇒ 关闭本旅程
- 任一格式失败 ⇒ 只开该格式的最小修复（locator 生成或消费侧），不重写统一笔记层
- 若发现**回源伪造位置**或**临时查阅污染普通进度** ⇒ 立即停止并单列 P0 数据安全缺陷，优先于任何新功能
- 若 §2 入口问题被判为缺口且需要改产品源码 ⇒ 返回 Codex 决定所有权后再动

---

## 原始证据

本任务 E 目录：`E:\creation-reading-assistant\next-round-read-note-return-precheck-v2\`

| 文件 | 内容 |
|---|---|
| `execution.json` | 任务编号/版本、执行标识、实际模型、cwd/HEAD、输入指纹、状态 |
| `evidence/sha-recompute.json` | 25 个链上文件的 E 候选 SHA / HEAD SHA / 是否 25 载荷 / v1 声称值 / 逐项判定 |
| `evidence/wiring-and-deletion.json` | 接线可达性事实、删除语义三分、active 过滤 SQL、缺失正文文案、冻结 E 行号表、可复用测试存在性 |

引用的**既有**原件（只读回读，未改动）：

- `docs/coordination/dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v1.md`（v1，保留原件；引用须附本补正）
- `docs/coordination/reviews/2026-10/2026-10-03-next-three-deliveries-codex-review.md`（上游补正来源）
- 冻结输入 `E:\creation-reading-assistant\next-round-integration-ready-v1\`（`final-manifest.json`、`android/`）

**本轮无新 XML / 日志 / 截图**：未构建、未运行任何测试或设备操作，故**不存在运行证据**。

## 未覆盖项

- **未执行任何构建、测试或设备验证**：全部为静态源码阅读与 SHA 实算。**不得**标为 UI 验收通过。
- **§2 的入口问题未由运行证实**：结论为静态可达性分析（无调用方 + 无路由导航 + 明确注释）。可能存在本报告未检索到的动态导航（如反射、深链、外部 Intent）——已检索 `AppNavigation.kt` 与全部 `profile/*` 源码，未见；但不能穷尽运行时构造。
- **回源到达精度未验证**：`legacyOffset` / `chapterIndex+charOffset` 是否真的落到正确位置属运行验证范围。
- **临时查阅的进度保护未在本版本执行**：源码与既有 JVM 支持，但 S12 的设备证据走的是**搜索入口**且 NavController route 未被保存（见 v1 §A 与 `2026-09-28-s12-mumu-real-import-temporary-acceptance.md` 的 NOT RUN 复核）。
- **EPUB 的 `APPROXIMATE` 实际触发**未执行。
- **MD 冷启动回源**无专属用例（`forPlain` 与 TXT 共用、`chapterIndex` 恒 0）。
- 未验证 `BookDetailNotesSection` 在正文移除后的完整 UI 表现（只读代码层判定其数据源不受影响）。
- 实体手机/MIUI、TalkBack、R8、正式签名/升级、WebDAV、正式书库：未验（沿用既有边界）。
- 纠错/词典/AI/续读卡/索引工程：按要求**未扩展**。

## 恢复与副作用

- **零产品写入**：未改任何产品源码、测试、配置或文档；未改 `docs/handoff/current.md`、项目页、TODO
- **零 Git 写操作**：未 commit / push / reset / checkout / stage；HEAD 与暂存区前后一致（`e39a78da…`，暂存 0）
- **零构建、零设备**：未调用 Gradle、ADB、MuMu；未占用设备，无需设备恢复
- 未读其他应用私有记忆库；未记录私有书名、凭证或不必要隐私（仅中性表述）
- 仅新建本任务 E 目录与其两个证据 JSON，并写入本报告

## 交付状态与下一步

**已交付待 Codex 核查。** 本报告完成：源码基线修正（3 个错误 SHA + 行号表重算）、真实 E 接线复核（**发现笔记入口不可达**）、删除语义三分（**修正 v1 的「不 join books」结论**）、原书缺失验收锁定为「仅移除正文」、两入口区分确认、以及最小验收合同（两分支 + 共同项）。

**需要 Codex/用户裁决的一项**：§2 的笔记页入口问题属「有意退役」还是「接线缺口」。这决定最小旅程走分支 A 还是分支 B，也决定是否需要动产品源码。**本报告不自行判定、不改合同、不动源码。**

**建议下一步**：

1. Codex 先就 §2 定性；若判为退役，建议同时决定 `ReadingNotesSubPage` 与其两个 actions 跨书分支的去留（保留死代码 / 移除 / 恢复入口），避免长期悬空。
2. 定性后按 §6 执行最小旅程（三格式各一遍 + 共同项「仅移除正文」），MuMu 独占。
3. 未开启新功能、未追加全量历史测试、未提交、未推送、未启动下一任务。

**百分比**：本任务范围内 6 项修正/产出 **6/6 = 100%**（基线修正、接线复核、删除语义、缺失状态锁定、两入口区分、最小合同）。**这不代表该旅程已验收**：旅程验收仍为 0（未运行），且新增一项待裁决的前置问题。全产品完整分母仍未核定，不以本批局部完成代替全产品进度。
