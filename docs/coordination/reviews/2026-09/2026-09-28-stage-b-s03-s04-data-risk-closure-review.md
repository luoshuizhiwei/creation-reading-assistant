# 阶段 B 归并：S03 搜索索引/撤销竞态 + S04 EPUB 来源更新数据链（2026-09-28，只读）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮零源码/测试/账本改动，未运行 Gradle/ADB/MuMu；唯一写入 = 本报告。
- 输入：`AGENTS.md`、`docs/handoff/current.md`、[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)、[任务账本](../../decisions/2026-09/2026-09-22-current-task-ledger.md)（含 09-23/09-24/09-27 各节）、S03-1 各轮复核记录、[真实书库验收](../../../qa/2026-09/2026-09-23-s03-1-real-library-device-acceptance.md)及离线对账、[S04 MuMu qahost UI 对账](../../../qa/2026-09/2026-09-27-s04-mumu-qahost-ui-reconciliation.md)、[S04 真源刷新](../../../qa/2026-09/2026-09-27-s04-mumu-true-source-refresh.md)、[回滚最终代码门](../../legacy-stages/2026-09/2026-09-27-s04-epub-rollback-final-code-gate.md)。
- 同一性限制（先行声明）：当前 `build/test-results` 仅剩 5 份 Legado 系 XML（S12 相关轮覆盖），**S03/S04 的全部历史 JVM XML 已不可回读**；本报告的"未漂移"判断只能由 **mtime 序 + 被钉接缝仍在（内容锚）** 支撑，**字节级同一性未建立**，已在各行标注。

## 1. S03 搜索索引 / 撤销竞态 — 逐项结论

### 1.1 并发重建的代次/状态写入（S03-1）

- **当前代码事实**：【已确认】闭合结构在当前源码：`rebuildLock = Mutex()`（`SearchIndexRepository.kt:135`）、所有权 CAS ×5（`:183/205/223/238/252` coordinationState）、`rebuildLock.withLock`（`:538`）串行化重建 begin→状态重置→收尾。
- **证据日期与适用版本**：9/23 Codex fresh `--rerun-tasks` 19/19（账本"双重建修正复核"节）；09-26 02:20 存在第二轮定向重跑（本窗 09-27 亲见该 XML 存在于 test-results，含 `SearchIndexRepositoryRebuildConcurrencyTest`；**该 XML 现已被后续运行覆盖**）。适用版本：`SearchIndexRepository.kt` mtime 09-23 14:02，早于 09-26 轮。
- **原始日志路径**：9/23 XML 未留档（账本记录）；09-26 XML 已覆盖；E 盘无本轮留证。
- **已关闭范围**：代码与 JVM 范围（单重建所有权/双重建串行/旧批次 term、coverage、display、tokenizer 清理竞态的定向用例）。
- **仍未验证的最小场景**：独立真机/真实 Room 上的并发重建（账本 S03 行明示未做）；真实书库自然路径已有 9/23 WorkBuddy PASS WITH LIMITATIONS（10 full + 1 partial，约 13m45s，用户授权真实书库）作旁证。
- **处置方式**：不重跑历史；最小补验 = 一次定向重跑（19 例）并**当场登记 XML SHA**，或由 Codex 接受"9/23 fresh 记录 + 09-26 复跑观察"为过渡证据并登记该限制。

### 1.2 撤销/删除后的异步重建（S03-3）

- **当前代码事实**：【已确认】`BookRepository` 注入协调器（`:77`）、`restoreBook`/`restoreDeletions` 成功后 `indexRefreshCoordinator?.request(bookId)`（`:580`/`:656-663`，解除同步阻塞）；`SearchIndexBookRefreshCoordinator.kt` mtime 09-23 01:05。
- **证据**：9/23 fresh 29/29（BookRepositoryTest 23 + CoordinatorTest 6）；当前两类 @Test 计数 23/6 未变（本轮数过）。协调器文件早于 09-26 轮；BookRepository mtime 09-26 02:19 恰在该轮之前。
- **已关闭范围**：JVM/编译。**最小未验场景**：真实 Room/设备上的撤销→异步重建链。**处置**：同 1.1（补跑时 BookRepositorySearchIndexTest 4 例一并纳入）。

### 1.3 搜索命中与索引覆盖（S03-2）+ 旧批次覆盖新批次

- 【已确认】`rowsByStates`/`incompleteBookIds` 恢复与孤儿识别逻辑在当前源码（9/23 C01 修复轮，fresh 27/27）；旧批次不得覆盖新代次的断言面在 1.1 的 19 例内。
- 【缺口】9/26 后无任何可回读 XML；覆盖类测试的当前计数未逐一重数（仅并发 19、协调器 6、BookRepositorySearchIndex 4 已核）。
- **处置**：并入 1.1 的同一次补跑；**不阻挡**（无现役缺陷证据，结构与账本闭环一致）。

## 2. S04 EPUB 来源更新数据链 — 逐项结论

### 2.1 两个入口的当前区别（本轮源码锚定）

- 【已确认】`ShelfImporter.kt`：书架「重新定位文件」→ `repairFile`（`:242` → `repairFileTyped :296`，只对账内部正文/书籍行/进度）；书架下拉确认 → `refreshFromSource`（`:261`，内含 `refreshSourceBaseline :283/:353`，推进来源引用基线）。**9/27 MuMu 真源刷新轮已把两者的差别变成设备证据**：此前 S14 验收 §8 发现"重新定位后 books.content_hash=cafaef49 但 library_source_refs 停在 V1(734d17ff)"，真源刷新轮证明真 `refreshFromSource` 把基线推进到 `size 1101→1700、hash 734d17ff→cafaef49`、异步索引 `coverage +2 / terms +127`、`progress 15/15` 零变化、`only_test_book_changed` 全真，执行者自复核 + Codex 复核（§13）接受**限定 PASS（MuMu qahost、单测试书）**。
- **已关闭范围**：真源刷新控制流、基线推进、该书索引落库（模拟器）。**未验最小场景**：「新内容字节被刷入正文」未证（本轮来源与私有正文本就同字节）；＞32 MiB 场景 B 与观察性进程中断场景 E 未跑；其他书 `search_terms` 仅按书计数比对。
- **实体手机/正式书库**：S04 五关 4/5，第 5 关（实体手机/正式书库前后对账）**未完成且不写 PASS**（UI 对账报告 §结论）。MuMu 证据与实体机证据全程分开表述。

### 2.2 回滚保护与恢复失败链（EPUB 替换）

- **证据**：9/27 回滚安全修复 + 最终代码门：`EpubRepositoryReplaceTest` **21/21**（E 盘 XML SHA-256 `3b0730ae…`、日志 SHA 登记在案），stage-q 的"无 backup 删唯一副本"P1 触发链撤回为未证实，新 P1 候选（staging 期异常 → catch 删未触碰旧副本）由本修复覆盖 JVM 面。
- **【门后漂移】**：`EpubRepository.kt` mtime **09-28 00:59**，晚于该门；`EpubRepositoryReplaceTest.kt` mtime 09-27 11:20。**无法建立"21/21 适用于当前字节"的同一性**（无门时字节快照可比对，mtime 不能区分实质改动与 no-op touch）。**处置**：Codex 向窗口 1/2 核实 00:59 写入者与内容；重跑 21 例并留 XML SHA；在此之前不把 21/21 写为当前状态。
- 【缺口（登记项，非本轮新增）】`ShelfImporter` 无互斥、入口守卫竞态窗口（最终代码门 §5.2）与 catch 内三处未保护 delete 的 JDK 语义依赖（§5.4）保持开放，属后续切片。

### 2.3 来源引用基线 / 内部正文哈希 / 索引 / 进度坐标 的交叉一致性

- 【已确认】TXT 小文件路径 Book/source hash 一致性：V4 真机复验 PASS（9/23，Book hash 与 ref hash 同步推进等于文件 MD5；原始安装日志未独立回读已登记）。EPUB 同类一致性由 2.1/2.2 覆盖（MuMu）。
- 【缺口】**正文替换后的进度/标注坐标重映射**在 EPUB 路径无专项验收（真源刷新轮仅证明进度行未变，不证明替换后的坐标映射正确）；该面与 S12-① EPUB locator 多写入点量纲问题（B 窗 R2）同源，归 R2 契约 owner，不在本轮展开。

### 2.4 当前漂移汇总（会使旧 JVM 证据失效的改动）

| 文件 | mtime | 影响 |
|---|---|---|
| `EpubRepository.kt` | 09-28 00:59 | **晚于最终代码门**，21/21 不自动适用（见 2.2） |
| `ShelfImporter.kt` | 09-24 16:31 | 晚于 S04-1/2/3/4 JVM 收口（9/23 58/58）；`ShelfImporterTest` 当前 **50** 例（9/24 账本记 39），无 fresh 运行记录可回读 ⇒ 旧绿对本套件规模失效 |
| `ShelfViewModelTest` | 当前 21 例（账本 20/20 后 +1） | 同上，并入补跑 |
| `SearchIndexRepository.kt` / 协调器 / `BookRepository.kt` | 09-23/09-23/09-26 02:19 | 早于 09-26 轮；XML 已被覆盖，同一性受限（见 §1） |

## 3. 交总指挥的两行账本结论

| 项 | 结论 | 证据等级 | 仍缺最小验证 | 处置 | 阻挡阶段 B 退出 |
|---|---|---|---|---|---|
| **S03** 并发重建/撤销异步/索引覆盖 | 代码与 JVM 范围关闭（9/23 fresh 19/19、29/29、27/27；09-26 复跑已观察后覆盖）；真实书库自然路径 PASS WITH LIMITATIONS（9/23，10 full+1 partial） | 中高：Codex fresh 记录 + 当前接缝内容锚（rebuildLock/CAS/request 全在），**字节同一性未建立**（XML 均已被覆盖） | 一次定向重跑（并发 19 + 协调器 6 + 覆盖类）并当场登记 XML SHA；独立真机/真实 Room 并发验收仍开放 | 由共享构建所有者串行补跑；不重开实现 | **否**（无现役缺陷证据；补验为证据卫生，不是返工） |
| **S04** 来源更新数据链 | 前置确认/异步索引/基线防护/回滚保护各自 JVM 关闭；MuMu qahost 上 `repairFile` 与真 `refreshFromSource` 双入口均有限定 PASS（09-27）；实体手机/正式书库前后对账与场景 B/E 未跑（五关 4/5） | 中：MuMu 原始快照/哈希链完整（§12 自复核 + Codex §13）；但 **`EpubRepository.kt` 门后漂移（09-28 00:59）** 与 **`ShelfImporterTest` 50 例无 fresh 记录** 使两块 JVM 绿暂不适用当前字节 | ① 核实 00:59 写入者并重跑 `EpubRepositoryReplaceTest` 21 例；② 重跑 `ShelfImporterTest` 50 + `ShelfViewModelTest` 21 + 协调器 6，留 XML SHA；③ 实体机/正式书库对账与场景 B/E 另行排期 | ①② 交窗口 1/2 所有权内随下一轮门禁执行；③ 保持"不写 PASS"登记 | **否**（当前无已知现役数据缺陷；但补验未完成前，S04 的"代码与 JVM 范围收口"表述应降级为"部分套件待重验"） |

## 4. 边界声明

本轮只新增本报告；未修改产品代码/测试/Gradle/账本/他人报告；未运行 Gradle/ADB/MuMu；未接触真实手机、正式书库与隐私数据（报告无真实书名、无设备库行、无截图内容）；未进入原仓库 `D:\develop\Code\Codex\creation-reading-assistant`；窗口 1/2 源码所有权未动，`EpubRepository.kt` 09-28 00:59 漂移仅登记归属待核，不做任何写操作或推断归因。未 commit/push/reset/checkout。落盘即停，交总指挥复核。
