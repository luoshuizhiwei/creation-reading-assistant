# 阅读→笔记→回源→返回原阅读处 调用链只读预检

## 任务编号/版本
CRA-READ-NOTE-RETURN-PRECHECK-v1；2026-10-03。只读定位，交付下一轮最小实施或验收合同。

## 输入指纹
- 活动仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`（实读一致；暂存区有他人 `.codex/*` 修改，非本轮产生）
- 输入清单：`E:\creation-reading-assistant\next-round-integration-ready-v1\final-manifest.json`
  SHA-256 `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`（12026 字节；`scope: 25`，`head` 与本仓库一致）
- 只读候选：`E:\creation-reading-assistant\next-round-integration-ready-v1\android`
- **输入核对**：清单 25 路径**全部为 Sync/WebDAV/Profile/布局**（SyncRepository、JsonBridge、LocalZip*、WebDav*、Profile*、AppLayout、SettingsDensity、LayoutTokens 及其测试）。**不含**阅读笔记、回源、临时查阅、定位链的任何文件。⇒ 本旅程在当前候选中**未被改动**，现状以 HEAD 源码为准；候选清单不构成对该链的验收输入。

## 所有权与改动
- 可写工件目录：`E:\creation-reading-assistant\next-round-read-note-return-precheck-v1`（本轮仅创建目录，用于保存只读证据副本）
- 可写仓库报告：本文件 `docs/coordination/dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v1.md`
- **零产品写入**：未改产品源码、未改 `docs/handoff/current.md`、项目页、TODO 或他人报告；未 commit / push / reset / checkout
- **零构建、零设备**：未调用 Gradle、ADB、MuMu；未冻结修改输入
- 执行身份：Claude（本窗口，模型经用户切换后继续），时间 2026-10-03
- 未重做排查：确认本任务报告与 execution.json 均不存在（首次交付）

## 环境
Windows；JDK17 未启动（未构建）。全部为静态源码阅读与既有报告回读。

## 执行与结果

### 0. 旅程是否已在候选范围内
候选 25 路径不覆盖本链（见输入指纹）。因此下表基于 **HEAD 源码**核对，并标注哪些环节有既有执行证据。

### A. 三格式旅程矩阵

图例：**I**=已实现且入口可达；**E**=已有匹配版本执行证据；**S**=源码支持但未执行；**G**=具体缺口；**U**=无法确认。

| 环节 | TXT | EPUB | Markdown |
|---|---|---|---|
| 打开测试书 | **I**（流式/纯文本加载存在） | **I**（`epubBook` 分支） | **I**（`markdownDocument` 分支，`ReaderActions.goToChapter` 含 MD 章边界） |
| 选择文字 | **I** | **I** | **I**（`MarkdownBlockSelectionLocatorTest` 存在） |
| 保存高亮/批注 | **S+I**：`NoteRepository.saveHighlight/updateHighlightNote`、`saveNote`（软删+撤销） | 同左（共用） | 同左（共用） |
| 摘录/批注/来源 bookId/locator 持久化 | **S+I**：`computeLocatorJson`（`forPlain` 单章=全书偏移；`forEpub` 全局偏移+章起始）、`ReaderNoteSnapshotSaveTest` 覆盖"选区清空后仍存快照摘录与同一快照 locator" | **S+I**：`LocatorBuilder.forEpub` + `AnchorResolver`；`EpubRepositorySaveProgressLocatorTest` 存在 | **S**：与 TXT 共用 `forPlain`（`chapterIndex` 恒 0）；缺 MD 专属端到端回源用例 |
| 退出重进冷启动读回 | **S**：Room 流 + `StoredProgressLocatorNavigationTest` | **S** | **U**（未见 MD 冷启动回源用例） |
| 笔记入口可达 | **I**：「我的 → 阅读笔记」在 `ProfileRoute`；数据源 `ProfileViewModel` 聚合 `observeNotes/observeHighlights`（全量 Room 流，**不 join books 表**）⇒ 原书缺失时摘录仍可读 | 同左 | 同左 |
| 点击回源 | **I+S**：`AnnotationActions.jumpToEntry` → `reader/$bookId?highlightId={typed}`；`ReaderProgressEffects:534-621` 消费；无 locator 降级 `reader/$bookId` | **I+S**：同上 + `AnchorResolver` 定位 + `APPROXIMATE` 提示"原文可能已变化，已跳到最接近的位置"；无 locator 时降级 `chapter_title`/`progress_percent` | **S**：走 `jumpToPlainOffset`/MD 路径；**缺** MD 专属用例 |
| 临时查阅入口 | **I+S**：`inspectSourceTemporarily` → `readerTemporaryRouteForSource`（仅 source 坐标，无 locator 返回 null + Snackbar"该记录没有可定位的原文位置"） | 同左 | 同左 |
| 临时查阅不污染正常位置 | **S（有强证据）**：`canPersistNormalReadingProgress` 与位置上报 `if (initialPositionPending \|\| temporaryInspection) null`；`TemporaryReadingJourneyTest` 含"临时查阅不覆盖正常阅读进度"、"普通阅读不产生临时历史"、"重启动丢弃临时栈且不伪造位置" | 同左（共用协调器） | 同左（共用） |
| 返回原阅读处 | **S**：`resolveTemporaryReturnRoute` LIFO；末层不带 `navigationMode` 恢复普通语义；`ReaderTemporaryRouteJourneyTest`/`ReaderTemporaryBackTest` 覆盖 | 同左 | 同左 |
| **匹配版本执行证据** | **E（限定）**：`docs/qa/2026-09/2026-09-28-s12-mumu-actual-temporary-route-acceptance.md` —— Codex 复核确认有"搜索命中→阅读器→翻页→返回搜索结果"的 MuMu 截图/XML，临时查阅前后 `reading_progress` 保留同一 marker ⇒ **该搜索入口下进度保护的限定 PASS**。但：报告**未**保存 NavController route 或 `beginTemporaryInspection` 运行日志；内部调用属源码链+对照行为支持的**合理推断**，不作直接观测 | **E（部分）**：`docs/coordination/legacy-stages/2026-09/2026-09-28-s12-epub-locator-contract-result.md`：EPUB `ci/co` 契约已修（“要么真实、要么缺省，绝不伪造”）；**存量伪造全局记录**被一致性校验拒绝并提示"此阅读位置与当前正文不一致"，任意一次原生保存即自愈。属源码/契约层证据，非 UI 回源端到端 | **U**：未检索到 MD 的匹配版本设备/端到端证据 |

**关键缺口（G）汇总**
1. **MD 端到端回源无专属验证**：MD 与 TXT 共用 `forPlain`（`chapterIndex=0`），但未见 MD 的"保存→冷启动→回源→返回"用例或设备证据。不能因 TXT 通过而推定 MD 成立。
2. **真实点击回源的直接运行观测缺失**：S12 报告自述未保存 NavController route / `beginTemporaryInspection` 日志，`navigationMode=TEMPORARY` 的端到端在另一份报告中被 Codex 降级为 **NOT RUN**（`2026-09-28-s12-mumu-real-import-temporary-acceptance.md` 第 3 行复核：仅守卫与路由合约测试，未启动 ReaderRoute 的临时模式）。
3. **"我的→阅读笔记"点击回源的 MuMu 证据未检索到**：S12 证据走的是**搜索入口**，不是本旅程要求的"笔记入口查看→点击回源"入口。两者共享下游消费（`ReaderProgressEffects`），但入口可达性未在本版本设备上执行。
4. **原书缺失时的回源失败解释**：源码有明确分支（`bookId==null` → Snackbar；`resolveAnnotationTarget==null` → "无法定位该阅读笔记，记录可能已删除或类型不匹配"），但**未执行**；属 S 而非 E。

### B. 关键调用链与不变量（附源码路径、行号、SHA-256）

SHA 均对 `android/app/src/main/java/com/creationreadingassistant/` 下相对路径计算：

| 文件 | SHA-256（前16） |
|---|---|
| `feature/annotations/AnnotationModels.kt` | `a92fbb362571dbbd` |
| `feature/annotations/AnnotationNavigationTarget.kt` | `90eaaa00f423cba9` |
| `ui/navigation/ReaderTemporaryRoute.kt` | `4b577e368c0fa3e4` |
| `ui/navigation/TemporaryReadingNavigationViewModel.kt` | `c0eed88f46d2980b` |
| `ui/screen/reader/ReaderProgressEffects.kt` | `a7baa349494e328b` |
| `ui/screen/profile/ProfileRoute.kt` | `a1cad5557edb853e` |
| `data/repository/NoteRepository.kt` | `9896873c3d448602` |
| `ui/screen/reader/ReaderProgressActions.kt` | `933513e1400b852f` |
| `feature/reader/navigation/SourceNavigationContract.kt` | `82361a5223d45716` |

**不变量 1 —— 摘录与个人想法不混淆** ✅ 源码支持
`AnnotationModels.kt:31-51`：`excerpt`（原文摘录）与 `annotation`（个人批注 `note`/`body`）为独立字段，KDoc 明确"二者在 UI 上分区展示"。`:56-104` 两个 `toAnnotationEntry` 分别把 highlight.text→excerpt、highlight.note→annotation；note.excerpt→excerpt、note.body→annotation。书签（`kind=="bookmark"`）`:93` 强制 `annotation=null`。

**不变量 2 —— 来源身份与 locator 不伪造** ✅ 源码支持 + 有契约证据
- `AnnotationModels.kt:43` `hasLocator = locator != null`；`:29,37` KDoc "无 locator 的历史记录无法精确定位，只能降级打开书籍，禁止伪造偏移"。
- `AnnotationNavigationTarget.kt:36-37` `navigationTargetOrNull()`：有 locator 才携带 typed stableId，否则 null（只打开书、不伪造偏移）。
- `AnnotationNavigationTarget.kt:67-79` `resolveAnnotationTarget`：按类型前缀严格匹配表，**不跨表猜测**（`:64-65` KDoc 明示"不回退另一张表猜测命中"）；裸 rawId 保留旧"先高亮后笔记"兼容语义。
- `ProfileRoute.kt:241-246`：有 target 走 `reader/$bookId?highlightId=`，无 locator 降级 `reader/$bookId`（不拼偏移）；`bookId==null` → Snackbar"该记录未关联书籍，无法跳转"（`:234-237`）。
- `ReaderProgressEffects.kt:560-568`：`resolveAnnotationTarget==null` 时清除请求并提示"无法定位该阅读笔记，记录可能已删除或类型不匹配"。
- `ReaderTemporaryRoute.kt:44-54` `encodeSourceLocator`：`legacyOffset` 为 null 或 <0 返回 null，**绝不伪造 offset=0**（`:42,78` KDoc 明示）。
- EPUB 一致性校验：`SourceNavigationContract` 一致性校验会拒绝矛盾数据（EpubRepository.kt:391 注释、LocatorCodec.kt:18）；`AnchorResolver` 有 `EXACT/RECOVERED/APPROXIMATE` 三档置信度（`AnchorResolver.kt:3-45`）；`APPROXIMATE` 时 UI 提示"原文可能已变化，已跳到最接近的位置"（`ReaderProgressEffects.kt:601-603`）。⇒ 不会静默误跳；跳不准时会明示"最接近位置"。

**不变量 3 —— 临时查看不污染正常阅读位置** ✅ 源码支持 + JVM 覆盖
- `ReaderProgressEffects.kt:733-738` `canPersistNormalReadingProgress` = `!initialPositionPending && !temporaryInspection`；`:856-858` 位置上报在 `temporaryInspection` 时返回 `null`；`:205-206,215-218,264,335,400` 多处把 `temporaryInspection` 纳入 LaunchedEffect key 并提前返回。
- `ReaderTemporaryRoute.kt:142-151` LIFO 返回：末层不带 `navigationMode` 恢复普通语义；栈空返回 null（不伪造返回目标）。
- `TemporaryReadingNavigationViewModel.kt:59-61` + `SourceNavigationContract.kt:142-200`（`DEFAULT_MAX_TEMPORARY_HISTORY=8`）。
- 既有 JVM：`TemporaryReadingJourneyTest`（LIFO 顺序、临时不覆盖正常进度、重启动丢弃栈不伪造位置）、`ReaderTemporaryRouteJourneyTest`、`ReaderTemporaryBackTest`、`TemporaryReadingNavigationViewModelTest`、`AnnotationTemporaryInspectionEntryTest`。

**不变量 4 —— 缺失原书仍保留可读摘录** ✅ 源码支持（未执行）
`ProfileViewModel.kt:177-181` 数据源为 `bookRepository.observeNotes()/observeHighlights()` 全量 Room 流，**不 join books 表**；`NoteRepository.kt:24-31` 观察按 `bookId` 但笔记行本身独立持久。⇒ 书籍行缺失时摘录仍可查看；回源时走 `bookId==null`（Snackbar）或 `resolveAnnotationTarget==null`（"无法定位…可能已删除"）明确解释，不伪造、不崩溃。

**不变量 5 —— 冷启动能读回真实持久数据** ⚠️ 部分
- Room 持久化链存在（`saveHighlight/saveNote` → DAO upsert；`NoteRepositoryRestoreTest` 覆盖软删恢复）。
- `ReaderNoteSnapshotSaveTest` 证明"选区清空后仍存快照摘录与同一快照 locator"。
- **缺口**：未检索到本版本上"退出重进（冷启动）→ 笔记入口 → 点击回源 → 到达正确位置"的端到端执行证据（MD 尤其无）。

**格式解析/纠错映射的具体风险（仅实际依赖）**
- EPUB：`AnchorResolver` 依赖摘录指纹与章文本；正文变化 → `APPROXIMATE`/`RECOVERED` 并提示，不静默误跳。存量伪造全局偏移（历史导入）会被一致性校验拒绝并提示，属**已知且已收敛**（S12 报告第 112 行）。
- TXT/MD：`forPlain` 用 `chapterIndex=0` 单坐标空间；S12 报告登记"TXT/MD 写入点 v2 `co` 实为全书偏移而非章内值"为**已知怪癖**（今天无消费者按章元组解释 TXT/MD，功能无害）。⇒ 对本链不构成本轮阻塞，但若将来新增按章元组解释 TXT/MD 的消费者，会误读。
- 纠错映射：未在本链依赖中见到引入额外风险（`ReaderProgressEffects` 有 stale guard 与格式分派注释），**不扩大到纠错系统重审**。

### C. 下一轮最小任务合同

**结论：主要能力已存在**（入口可达、持久化、回源解析、临时查阅保护均有源码+JVM 支撑；TXT 有 S12 限定 MuMu 证据）。按任务书"若主要能力已经存在，优先提出少量联合验收，而不是重写"。

**建议最先做的一条完整旅程（三格式各一遍，最小联合验收）**：
> 导入一本中性测试书（TXT/EPUB/MD 各一）→ 阅读并选中文字 → 保存高亮+批注（或摘录笔记）→ **杀进程冷启动** → 从「我的 → 阅读笔记」查看 → 点击回源 → 到达原文 → 用临时查阅跳到别处 → 返回原阅读处 → 断言：摘录与批注分区显示、原阅读位置未被临时查阅覆盖、摘录在书缺失后仍可读。

**文件所有权**（建议）：本轮为**验收**，建议不改产品源码；仅新增/复用测试与报告由单一 owner 承担。若验收暴露缺口，再按缺口开最小修复，所有权限制在 `feature/annotations/*`、`ui/navigation/ReaderTemporaryRoute.kt`、`feature/reader/locator/*` 之内。

**依赖**：MuMu 独占（与 CRA-INTEGRATION-MUMU-ACCEPT-v1 串行，不并行占用设备）；隔离 `.qahost` 新 UID；中性测试书，不碰正式书库。

**可复用测试**（已存在，先跑）：`AnnotationNavigationTargetTest`、`AnnotationModelsTest`、`ReaderNoteSnapshotSaveTest`、`TemporaryReadingJourneyTest`、`ReaderTemporaryRouteJourneyTest`、`ReaderTemporaryBackTest`、`AnnotationTemporaryInspectionEntryTest`、`StoredProgressLocatorNavigationTest`、`MarkdownBlockSelectionLocatorTest`、`NoteRepositoryRestoreTest`。

**必要新增用例**（仅补缺口，不重写）：
1. **MD 冷启动回源**：MD 书保存带 locator 的摘录 → 冷启动 → 回源 → 断言到达 `legacyOffset` 位置（补 MD 空白，不能由 TXT 推定）。
2. **笔记入口点击回源的端到端**（instrumented，非纯函数）：从「我的 → 阅读笔记」真实点击进入 ReaderRoute，断言 `pendingHighlightId` 被消费且 `navigationMode` 不是 TEMPORARY（补 S12 缺失的直接观测）。
3. **原书缺失**：删除书籍行后打开笔记页 → 断言摘录正文仍显示；点击回源 → 断言出现"无法定位/未关联书籍"提示且不跳转。

**MuMu 验收步骤**（模拟器验收，不称实体手机）：
1. 三本中性测试书导入并各做一条高亮+批注/摘录；记录 DB 行（含 `locator_json`）。
2. 杀进程冷启动 → 「我的 → 阅读笔记」核对摘录与批注分区、章节/时间显示。
3. 点击回源 → 截图 + XML，断言到达正确位置（EPUB 用章节+偏移、TXT/MD 用全书偏移）。
4. 临时查阅跳到别处 → 返回 → 断言原阅读进度与 marker/locator 未被覆盖（前后 DB 对账）。
5. 删除其中一本书 → 重开笔记页 → 断言摘录仍可读、回源给出明确失败提示。
6. 恢复：卸载测试包、恢复主 DB/APK 对/密度/旋转/保活基线，记录中性书名。

**停止条件**：
- 三格式全部通过 ⇒ 关闭本旅程，进入排期表下一项（继续阅读卡片为新增候选，需另行版本决定，不在本合同）。
- 任一格式失败 ⇒ **只开该格式的最小修复**（ locator 生成或消费侧），不重写统一笔记层；修复后重跑该格式 + 可复用 JVM。
- 若发现回源伪造位置或临时查阅污染进度 ⇒ 立即停止并单列 P0 数据安全缺陷，优先于任何新功能。

## 原始证据
- 本轮未构建、未跑 Gradle，故无新 XML/日志。引用的**既有**证据（只读回读）：
  - `docs/qa/2026-09/2026-09-28-s12-mumu-actual-temporary-route-acceptance.md`（TXT 搜索入口临时查阅 MuMu 限定 PASS，含 Codex 复核边界）
  - `docs/coordination/legacy-stages/2026-09/2026-09-28-s12-epub-locator-contract-result.md`（EPUB ci/co 契约、一致性校验拒绝、APPROXIMATE 提示）
  - `docs/qa/2026-09/2026-09-28-s12-mumu-real-import-temporary-acceptance.md`（Codex 复核：临时模式端到端 NOT RUN，仅守卫/路由合约）
  - `docs/coordination/dispatches/2026-10/2026-10-02-next-integration-ready-v1.md`（候选 25 路径范围）
- 工件目录 `E:\creation-reading-assistant\next-round-read-note-return-precheck-v1`：保存本报告副本与输入清单 SHA 记录（只读证据，无构建产物）。

## 未覆盖项
- **未执行任何构建或设备验证**：本轮全部为静态源码核对 + 既有报告回读，**不得**标为 UI 验收通过。
- MD 端到端回源、冷启动回源、原书缺失回源提示：**源码支持但未执行**。
- 「我的 → 阅读笔记」入口点击回源的本版本设备证据：未检索到（S12 走的是搜索入口）。
- 纠错与辅助理解（词典/StarDict/AI）、全文索引工程、搜索回源之外的定位：按要求**未扩展**。
- 实体手机/MIUI、TalkBack、R8、正式签名/升级、WebDAV、正式书库：未验（沿用既有边界）。
- 候选 25 路径合并后的设备验收未做（属 integration-ready-v1 下一动作，非本任务）。

## 恢复与副作用
- 无产品源码写入、无 Git 写操作（未 commit/push/reset/checkout）、无构建、无设备操作。
- 未改 `docs/handoff/current.md`、项目页、TODO 或他人报告；未覆盖他人修改。
- 仅创建工件目录并写入本报告及其副本；输入清单与候选目录保持只读未改。
- 未记录私人书名、凭证或不必要隐私（仅中性/测试书表述）。

## 交付状态与下一步
**已交付待 Codex 核查**。本轮完成调用链只读定位与三格式矩阵，未自行实施下一任务。

建议下一步（需用户/Codex 决定后再执行）：按 §C 的最小联合验收（三格式各一遍）在 MuMu 上执行；若全部通过则关闭本旅程，否则只开失败格式的最小修复。不新增继续阅读卡片、AI 分析、分享收集、专题卡片等未进入当前实施队列的能力；不把本次结果当作全产品功能分母，不重新计算全产品百分比。
