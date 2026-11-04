# CRA-RELINK-PRECHECK-v1：D2「正文缺失后安全重新关联」现状定位与最小方案

## 一、任务编号/版本

- **任务编号/版本**：`CRA-RELINK-PRECHECK-v1`
- **性质**：只读定位与最小方案。**不实施产品修改、不做设备验收、不构建、不测试。**
- **上游依据**：[N1-v3/Library-v1 核查](../../reviews/2026-10/2026-10-02-next-n1v3-library-codex-review.md) §Spec「D2 去重不等于重新关联。`ShelfImporter` 的 contentHash 相同分支直接返回 Duplicate，不换正文、不重建指针……真实重关联入口/合同仍需定向核查」；[产品总规划](../../../plans/2026-10-01-product-roadmap-realignment.md) 第 77 行 D2 合同
- **状态**：**已交付待 Codex 核查**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 / HEAD | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / **`e39a78da9c62f02614207cffb36969a32492eaea`**（实读一致，收工未变） |
| 只读候选 | `E:\creation-reading-assistant\next-round-integration-ready-v1\android` |
| 输入清单 | `E:\…\next-round-integration-ready-v1\final-manifest.json`，SHA-256 `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d` |
| 清单摘要 | HEAD 同值；25 文件 / 121 hunks / 6 测试文件 / 153 tests；`device_NOT_RUN_on_merged_input = true`；永久后缀 `.qahost` |
| 候选是否含书库文件 | **否**（25 文件里没有 ShelfImporter / LibraryBrowser*）。书库源码在候选内与基线同源：本轮实算 `ShelfImporter.kt = d6bd2396…`，与 [Library-evidence-v1](2026-10-02-next-library-evidence-v1.md) 记录的「候选 = 基线」一致 |
| 引用源码 SHA | 见 §六 |

**未读取**：`CRA-INTEGRATION-MUMU-ACCEPT-v1` 正在使用的目录与设备；未扫描全仓历史，未读其他应用私有记忆库。

## 三、所有权与改动

- **唯一仓库写入**：本文件。未修改 `current.md`、TODO、项目页或其他报告。
- **自有工件目录**：`E:\creation-reading-assistant\next-round-relink-precheck-v1\`
  - `relink-callchain.json`（调用链与缺口）
  - `relink-behavior-table.csv`（行为表）
  - `execution.json`（执行记录）
- **未改**产品源码、Git 暂存区或 HEAD；未 commit/push/reset/checkout；未触碰原损坏仓库、HOLD、桌面资产。
- **重复执行检查**：目标报告与自有目录开工时均不存在 → 本轮为首次执行。

## 四、环境

Windows 11 / Git Bash。**零 ADB、零 MuMu、零 Gradle、零构建、零测试运行、零网络**。只读解析源码与既有文档。

## 五、执行与结果

### A. 精简调用链表（入口 → 结果）

| # | 层 | 符号 | 路径:行 | 读写 | 结果 |
|---|---|---|---|---|---|
| 1 | UI 面板 | 「重新定位文件」ActionRow | `BookActionSheet.kt:226-232` | 读 `book.readiness()` | `onRepair(book)`；**该行无条件显示**（不要求 content_status=missing）；subtitle 二选一：READY→「更换本地文件并保留阅读记录」，否则→「修复缺失的本地正文」 |
| 2 | UI 路由 | `reselectLauncher`（`OpenDocument`） | `ShelfRoute.kt:118-126, 349-350, 808` | — | MIME 限 `application/epub+zip` / `text/plain` / `text/markdown`；**返回即执行，无中间确认页** |
| 3 | ViewModel | `ShelfViewModel.reselectFile` | `ShelfViewModel.kt:421-423` | — | 转发 `repairFile(bookId, uri)` |
| 3.1 | ViewModel | `BookOperationsViewModel.reselectFile` | `BookOperationsViewModel.kt:37-39, 53-55` | — | 同一路径；另有 `HomeCompletedScreen.kt:94`、`MyReadingRoute.kt:55` 两处入口 |
| 4 | 业务 | `ShelfImporter.repairFile` → `repairFileTyped` | `ShelfImporter.kt:197, 251-294` | 读 `bookDao.getById` | ① 记录不存在→「书籍记录不存在」；② **只校验格式**：`normalizedFormat(所选名) != normalizedFormat(原书 original_file_name 或 book.<format>)` → 抛「格式不一致：原书为 X，所选为 Y」；③ 分派 `reselectEpub`/`reselectPlainText`；④ 成功→「《title》正文已修复」 |
| 5.1 | 文件+DB | `reselectPlainText` | `ShelfImporter.kt:869-950` | 写 `filesDir/books/<id>/<name>`；写 `books`/`book_content`/`book_file` | `.repairing` staging → 旧文件 `.backup` → 换入；**先文件后 DB 事务**；`existing.copy(...)` 保留 id/title/revision |
| 5.2 | 文件+DB | `reselectEpub` → `EpubRepository.replaceStoredEpub` | `ShelfImporter.kt:845-867`；`EpubRepository.kt:74-150` | 写 `filesDir/books/epub/<id>.epub`；写 `books`/`book_file` | 同款 staging/backup；**会用新解析结果覆盖 title/author** |
| 6 | DB | `BookRepository.clearBookCache`（移除正文） | `BookRepository.kt:273-300`（missing 写入在 291） | 删文件 → 写 `content_status="missing"`、`local_uri/path=null` | **`size` 与 `content_hash` 保留** |
| 7 | 业务（另一条路） | `importOne` 的 duplicate 判定 | `ShelfImporter.kt:563-595` | 读 `booksProvider()` | 命中 → 任务「已存在，未重复导入」/ `ImportOutcome.Duplicate`；**不换正文、不重建指针** |
| 8 | UI（既有身份判定，**未接入重关联**） | `LibraryBrowserPolicy.shelfMatch` | `LibraryBrowserPolicy.kt:42-105`；枚举 `LibraryBrowserModels.kt:82-93` | 读 `library_source_refs` | 五级：`EXACT_SOURCE`/`CONTENT_UPDATED`/`SAME_CONTENT`/`POSSIBLE`/`NAME_ONLY`；**消费方只有** `LibraryBrowserComponents.kt:356-364, 692-704`（徽标文案）与 `LibraryBrowserRoute.kt:276`（筛选） |

### 逐问回答

**1. 用户从哪个实际入口处理「书籍记录存在、正文缺失」？**
**有 UI 可达入口**：书架卡长按 → 操作面板「重新定位文件」→ SAF 选择文件。`content_status="missing"`（由「移除正文」产生）使面板把该行副标题显示为「修复缺失的本地正文」。
**注意**：该行**无条件显示**——正文完好的书也显示，此时副标题是「更换本地文件并保留阅读记录」。所以它同时是「修复」与「替换」两个语义。
另有三条并行入口：书库归档页（`HomeCompletedScreen.kt:94`）、阅读历史页（`MyReadingRoute.kt:55`）、`BookOperationsViewModel`（详情/其他页面）。

**2. 选择相同内容文件后，是恢复原记录的关联，还是仅返回 Duplicate？**
**取决于走哪条路径，两者行为相反**：
- **导入路径**（书库/批量导入）：判重命中（`content_hash` 相同，或格式+原名+size 相同）→ `ImportOutcome.Duplicate`「已存在，未重复导入」，**不重关联**。由于「移除正文」保留了 `size` 与 `content_hash`，且 `booksProvider` 取 `observeAllActive()`（**不过滤 content_status**），missing 态的书仍在去重集合内 ⇒ **missing 书经导入路径重选同一文件必然被挡**。
- **重新定位文件路径**：格式一致即通过 → 换入正文 → DB 置 `available` 并回填指针 → **恢复原记录关联**（bookId 不变）。

**3. 不同内容、无法确定身份或格式变化时，如何拒绝、提示或另行导入？**
- **格式变化**：`repairFileTyped` 拒绝（「格式不一致」）✓
- **不同内容但同格式**：**不拒绝**——直接替换正文（见缺口 G1）
- **无法确认身份**：**没有这个分支**；`repairFileTyped` 不计算内容哈希，也不区分「同源/异源」
- **导入路径**：不同内容（size/name/hash 均不同）不判重 → 作为**新书**导入（新 bookId），这是当前唯一正确处理「内容有差异」的地方

**4. 重新关联是否保留原 bookId、阅读位置、笔记、标签及其他关联？**
**保留**。`reselectPlainText` 用 `existing.copy(...)`（id 不变），只写 `books`/`book_content`/`book_file`；`reselectEpub` 同理。全程**未触碰** `reading_progress`、`notes`、`highlights`、`book_tag`、`book_category`、`shelf_book`。
**代价**：位置与标注仍指向**旧内容**；当所选内容不同时，这些 offset/locator 会错位（D2 合同要求的「位置安全」在此未实现）。

**5. 正文路径、内容身份与数据库修改的先后顺序是什么？**
**先文件系统、后 DB**（两条格式一致）：
1. 写 staging（`.repairing`）
2. 旧文件 → `.backup`
3. staging → 正式路径（`books/<id>/<name>` 或 `books/epub/<id>.epub`）
4. 读内容生成预览
5. `bookDao.runInTransaction { books + book_content + book_file }`
6. 成功后删 `.backup`
**「内容身份」这一步不存在**——没有任何哈希计算或比对参与。

**6. 取消、复制失败、数据库失败和重复操作是否留下不一致状态？**
- **复制失败**：catch → 删除已换入文件 + 从 `.backup` 复位 → 回到操作前 ✓（静态）
- **DB 失败**：事务回滚 + 文件同样回滚 ✓（静态）
- **SAF 取消（返回 null）**：直接 return，零写入 ✓
- **执行中取消/进程被杀**：`CancellationException` 被**原样抛出**（不写失败历史），而文件换入发生在 DB 事务**之前** ⇒ 可能留下「新正文已落盘、DB 仍为旧指针或 missing」。本路径**没有** N1 那样的 journal/持久中断状态（缺口 G4）
- **重复执行**：第二次重新走 staging→backup→换入，结果与第一次一致（幂等，静态推断，无用例）

**7. 删除/撤销、普通去重与重新关联分别有哪些已有证据，哪些不能互相替代？**

| 子链 | 源码归属 | JVM 同输入证据 | 设备证据 |
|---|---|---|---|
| 删除/撤销 | ✓ `BookRepository.deleteBook*`/`restoreBook`/`restoreDeletions`、`feature/library/deletion/**` | ✓ `BookDeletionRestoreTest` 等（Library-evidence-v1 §5） | **无**专门设备旅程；`LibraryLifecycleAcceptanceTest` 的 `deleteUndoChainRealUi` 已编写但**未运行** |
| 普通去重 | ✓ `importOne:563-595` | ✓ 4 条去重用例（`ShelfImporterTest:317/335/578/591`） | 无 |
| **重新关联** | ✓ `repairFileTyped` 等 | **仅 3 条**（book missing / 格式不一致 / txt 成功）；**无**不同内容、无 missing 态重导入、无失败回滚、无关联保留断言 | **无** |

**不能互相替代**：`LibraryLifecycleAcceptanceTest` 第 83 行自述「D2 contentHash 去重 ≠ 重新关联：不新增产品实现、不把重导入 Duplicate 写成 PASS」；其 6 个方法（seed / fixtureReady / afterShelve / afterRemoveContent / deleteUndoChainRealUi / savedFilterUiChain）**无一覆盖重关联**。去重用例证明的是「重复被挡住」，不证明「重关联正确」。

**三份副本均已核**：`g1-library-build-v1`（`8f7552cd…`）、`g1-library-build-v2`（`7add5e80…`，本报告与 n1v5-g1v2 复核引用的是这一份）、`g1-fix-accept-v3`（`d59289b5…`，**正在被设备批使用**）——三份的 `重新定位`/`reselect`/`repairFile` 关键词命中数**均为 0**。v3 相对 v2 新增了 `l1ColdStartReapply` 与若干夹具辅助函数，**仍不涉及重关联**。

### B. 行为表

见 `E:\creation-reading-assistant\next-round-relink-precheck-v1\relink-behavior-table.csv`（14 行，覆盖任务要求的 7 类场景 × 两条入口）。摘要：

| 场景 | 导入路径 | 重新定位文件路径 | 证据等级 |
|---|---|---|---|
| 同内容且正文缺失 | Duplicate，不重关联 | 接受并恢复关联（bookId/进度/笔记保留） | 源码支持 |
| 同内容且正文存在 | Duplicate，不覆盖 | 直接覆盖（UI 明示「更换本地文件并保留阅读记录」） | 源码支持 + JVM 用例 |
| **不同内容** | 作新书导入（新 bookId）✓ | **同格式即接受，直接替换正文** ✗ | 源码支持；**缺口 G1** |
| **无法确认内容身份** | 视为新文件 | 无此分支，格式一致即接受 | **缺口 G4-身份** |
| 用户取消（SAF） | 零写入 | 零写入 | 源码支持 |
| 用户取消（执行中） | — | 可能「文件已换、DB 未写」 | **缺口 G4** |
| 文件/DB 失败 | 失败历史 + 部分成功 | 回滚到操作前 | 源码支持（静态）；**无回滚用例** |
| 重复执行 | 仍 Duplicate | 幂等 | 源码支持（静态） |

**静态推断未写成运行通过**：上表除标注 JVM 用例者外，均为源码可达性推断，**无同输入运行证据**。

### C. 最小修复合同（只给方案，不实施）

**结论：不需要新建「重关联数据系统」。** 现有 `repairFileTyped` + `reselectPlainText`/`reselectEpub` 已具备完整的文件换入与 DB 更新能力，缺的是**在换入之前做一次内容身份判定**，并把判定结果作为分支依据。

**C-1 建议改动文件与函数（最小面）**

| # | 文件 | 函数 | 改动要点 |
|---|---|---|---|
| 1 | `feature/library/ShelfImporter.kt` | `repairFileTyped`（251-294） | 在格式校验之后、分派之前，计算所选文件的 `contentHashOrNull(uri, size)`（复用既有私有函数，:973）并与 `existing.content_hash` 比对，得到三态：`SAME` / `DIFFERENT` / `UNKNOWN`（任一侧为空即 UNKNOWN，与既有 >32MiB 策略一致）。**不新增任何持久化结构。** |
| 2 | 同上 | `repairFileTyped` 的返回契约 | 保持既有 `RepairOutcome(ok, message)` 与裸 String 兼容；三态通过**消息**与（若需要）一个新增的 `repairFileTyped` 内部结果字段表达，不改公开签名以免动 4 个调用点 |
| 3 | 同上 | `reselectPlainText`（869-950）/ `reselectEpub` | 成功路径补写 `books.content_hash`（当前**从不更新**）与（若来源可解析）`refreshSourceBaseline`，使身份事实与来源基线跟上新内容 |
| 4 | `ui/screen/shelf/ShelfRoute.kt` | `reselectLauncher` 回调（118-126） | 若要做「先看清是否匹配」的交互，在此处插入确认步骤（**可选**，见 C-5 产品决定） |

**C-2 必须保持的不变量**

1. **bookId 不变**：继续用 `existing.copy(...)`，绝不生成新 id。
2. **关联不动**：不得触碰 `reading_progress` / `notes` / `highlights` / `book_tag` / `book_category` / `shelf_book`。
3. **先文件后 DB 的现有顺序与回滚语义不变**：`.repairing` → `.backup` → 换入 → DB 事务；失败回滚到操作前。
4. **同内容路径行为不变**：同内容（`SAME`）仍走现有换入，`bookId`/位置/笔记保留——这是 D2 合同里「相同文件可按证据恢复关联」的部分，已经正确。
5. **不做无依据的强套**：`DIFFERENT` 时**不得**沿用旧阅读位置；是否清位置属 C-5 产品决定。
6. **不新增数据系统**：不引入新的表、不引入新的持久状态文件、不引入新依赖。

**C-3 与既有接缝的关系**

| 接缝 | 关系 |
|---|---|
| **删除/撤销** | 独立。撤销走 `restoreDeletions`（同 `deleted_at` 戳），与重关联不共享状态；重关联不得复活已软删除的行 |
| **普通导入（去重）** | **互补且需对齐**。建议把 `repairFileTyped` 的 `SAME` 判定与 `importOne` 的去重口径统一到同一函数（现为两套：导入用 `content_hash` 比较，重关联无比较）。**不建议**改导入路径让 Duplicate 自动重关联——那会改变现有已验收的去重语义 |
| **备份恢复（N1）** | 独立。N1 的 `LocalZipBackup` 处理整包 DB+文件；重关联是单书单文件。**唯一共享的教训**是「先文件后 DB 的两段式需要可识别的中断状态」——但本路径按最小合同**不引入** journal；若 Codex 认为需要，应作为独立任务而非塞进本次修复 |
| **`library_source_refs`** | 建议复用 `refreshSourceBaseline`（:308），使手动重关联与 `refreshFromSource` 行为对称 |

**C-4 必要的定向测试与后续 MuMu 用例**

*JVM（候选内，最小集）*
1. `repair` 同格式**不同内容** → 断言：不进入无提示替换（或按 C-5 决定给出明确提示），且 `books.content_hash` 与正文一致
2. `repair` 同内容 → 断言：`bookId` 不变、`reading_progress`/`notes`/`tags` 行**逐字段不变**、正文为新字节
3. `repair` 内容哈希不可得（>32MiB 或 provider 不给 size）→ 断言 UNKNOWN 分支行为
4. `repair` 失败回滚 → 断言文件与 DB 均回到操作前（现有 3 条用例**未覆盖**）
5. missing 态经**导入路径**重选同一文件 → 断言当前 Duplicate 行为（把 G2 锁成已知行为，防止将来无声改变）

*MuMu（后续设备批次，需新范围授权）*
1. 移除正文 → 长按「重新定位文件」→ 选**同内容**文件 → 书架恢复可读、进度与笔记仍在
2. 同上但选**不同内容同格式**文件 → 按 C-5 决定验收「被拒绝」或「明确提示后替换」
3. 中途取消/强杀 → 重进后状态可判定（不出现「正文在但显示未在本机」）

**C-5 需要产品决定**

1. **不同内容同格式时怎么办**：(a) 直接拒绝；(b) 提示「内容与记录不一致，确认后替换正文并重置阅读位置」；(c) 提示但保留位置。**roadmap 第 77 行的现有措辞指向「不能强套位置」**，即至少要做 (b) 或 (c)。本任务**不替产品选**。
2. **是否要在选择后加匹配预览页**（roadmap 第 77 行「选择新文件并看清是否匹配、哪些记录仍保留」）。当前无此交互，属体验细化。
3. **EPUB 覆盖 title/author 的行为**是否要统一到「保留原元数据」（缺口 G6）。
4. **是否需要给重关联加持久中断状态**（缺口 G4）。按最小合同**不建议**本轮引入。

**可复用证据**：`LibraryBrowserPolicy.shelfMatch` 的五级判定（含 `SAME_CONTENT` 指纹比对）是**既有、已测**的内容身份实现，可作为 C-1 第 1 步的语义参照；但其证据强度分档（>32MiB 才有指纹）需在 UNKNOWN 分支如实继承。

## 六、原始证据

**引用源码（候选内实算 SHA-256）**

| 文件 | SHA-256 |
|---|---|
| `feature/library/ShelfImporter.kt` | `d6bd239662b21d0d805897f33a22d0c9970e318627d56051467b724981d66b90` |
| `ui/viewmodel/ShelfViewModel.kt` | `203abc5a593f88f0f9a2ff19e055060ae2959b6c0541a51da98ef2ae4728ffab` |
| `ui/viewmodel/BookOperationsViewModel.kt` | `66eb5e5412c4808ab78dd141c883923b386dd9aaad10a65524afd8b9cb3088d6` |
| `ui/screen/shelf/ShelfRoute.kt` | `30615c241493064e4a952918b0b12eccf5a3c254069f4afe26a4aae3fe72e954` |
| `ui/screen/shelf/BookActionSheet.kt` | `cbd66642c174788877eb5f4d9260c1f415747a1e7ef2a3b00ba1f942f92d9c80` |
| `ui/viewmodel/LibraryBrowserPolicy.kt` | `918f29ccc2845d74996dffe76bb2dfd22cd76490dc897151e081a4c39c2e55bf` |
| `ui/viewmodel/LibraryBrowserModels.kt` | `c6e980d12fd5e612296879f3fd4fb3ef8f6e538515bd3086fb887042d1eb7956` |
| `feature/reader/EpubRepository.kt` | `60e138dcfd93341d05fcfaf893b55c099594f5339b43c8765687745bdb987519` |
| `data/repository/BookRepository.kt` | `698172c727535325088eaa72de3abe2e4915878c7aa3c8e70ad0d108260f4078` |
| `ui/screen/shelf/ShelfModels.kt` | `d410d700e7218b7cc1061295767c5aa5411dbfddc61961ac30204d9420b7d6e2` |
| `test/…/ShelfImporterTest.kt` | `afc60d289368ea04da4e83c949ebfb950bcc04fc6c247285fa409de2083d46e9` |

**复用的既有证据**

- `LibraryLifecycleAcceptanceTest.kt`（G1-v2 androidTest）SHA-256 `7add5e80acb645d36a17b7f89ad8b6958c0e385b53f9323be7091c24422db373` —— 与 [n1v5-g1v2 复核](../../reviews/2026-10/2026-10-02-next-n1v5-g1v2-codex-review.md) 引用值一致；6 个方法**均不覆盖重关联**；**未运行**。
- `ShelfImporterTest` 的 repair 三例 + refresh 四例 + duplicate 四例（JVM，属 Library-evidence-v1 所述 2673 集同输入通过范围）。

**自有工件**：`E:\creation-reading-assistant\next-round-relink-precheck-v1\`（`relink-callchain.json`、`relink-behavior-table.csv`、`execution.json`）。

## 七、未覆盖项

| # | 项 | 说明 |
|---|---|---|
| 1 | **任何运行证据** | 本轮零构建零设备；行为表除 JVM 用例外均为**静态可达性推断**，未运行验证 |
| 2 | 真实 SAF provider 行为 | `uriFileName`/`uriSize`/`openInputStream` 在真实 DocumentsProvider（尤其网盘）下的表现未验 |
| 3 | 大文件（>32MiB）重关联 | `contentHashOrNull` 返回 null 的分支行为未验 |
| 4 | 真实进程 kill 下的重关联中断 | 缺口 G4 为静态可达，未实测 |
| 5 | 搜索结果/阅读器侧的后续影响 | 重关联后章节索引重建（`refreshFromSource` 会触发 `indexRefreshCoordinator`，**手动 `repairFile` 不会**）的差异未展开 |
| 6 | 桌面端 | 不在范围 |
| 7 | 搜索范围外是否有第四条入口 | 本结论限于 §五 所列目录；**不外推为「整个产品绝无实现」** |

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；未 commit/push/reset/checkout；未改暂存区。**唯一仓库写入 = 本文件**。
- **候选**：只读，未修改、未复制、未覆盖（`E:\…\next-round-integration-ready-v1\` 原样）。
- **进行中的 `CRA-INTEGRATION-MUMU-ACCEPT-v1`**：未 ADB、未 MuMu、未 Gradle、未触碰其目录与设备。
- **其他**：未改 `current.md`/TODO/项目页/其他报告；未读其他应用私有记忆库；未触碰原损坏仓库、HOLD、桌面资产。
- 记录只使用中性书名（「测试 TXT」「样章」等），不含凭证或私人内容。

## 九、交付状态与下一步

**已交付待 Codex 核查。**

- **入口结论**：D2 重关联**有 UI 可达入口**（长按书架卡 →「重新定位文件」），**不是完全缺失**；但导入路径的 Duplicate 不构成重关联（与 N1-v3 复核一致）。
- **核心缺口（1 个高 + 3 个中）**：G1 重关联不做内容身份校验（与 D2 合同冲突）；G2 missing 书经导入路径被去重挡死且无指路；G3 不更新 `content_hash` 与来源基线；G4 执行中取消可能留下「文件已换、DB 未写」。
- **最小方案**：不新建数据系统；在 `repairFileTyped` 内加一次三态内容判定（复用既有 `contentHashOrNull`），补写 `content_hash` 与来源基线，配套 5 条 JVM + 3 条 MuMu 用例。**有 4 项需产品决定**（不同内容如何处理、是否加匹配预览、EPUB 元数据行为、是否要持久中断状态）。
- **建议下一步**：Codex 先就 C-5 的 4 项产品决定给出结论；决定后再派实施窗口（建议限定 `ShelfImporter.kt` + 其测试两文件，与删除撤销、导入去重、N1 备份恢复保持互斥所有权）。
