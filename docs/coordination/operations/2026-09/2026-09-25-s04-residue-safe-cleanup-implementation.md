# S04 残留保护清理链实现：清理离线正文不得误毁唯一残留备份

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **日期**：2026-09-25
- **依据**：`docs/code-review/2026-09-25-s04-epub-residue-ui-flow-audit.md` §2.6 / P1 / P2「清缓存静默销毁 TXT 残留」
- **性质**：离线实现 + JVM 定向测试。未运行 adb / 安装 / instrumentation / 真机操作；未触碰真实书库、E 盘备份、原始仓库；未执行 `commit / push / stage / reset / checkout / clean`。
- **所有权边界**：本窗口仅修改 `BookRepository`、`ShelfBookActions`、`StorageSubPage` 及对应数据/清理测试。例外见 §5（`ShelfViewModelTest` 一行编译适配，已作 SEAM 通告）。
- **结论先行**：两条用户可达的销毁路径（单书/批量清缓存、清空所有离线书籍）均已改为**先保护、后改 DB** 的保守清理链，定向测试 51 例 + 相关回归 35 例全部通过（EXIT 0）。

## 1. 修改清单

| 文件 | 改动 |
|---|---|
| `data/repository/BookRepository.kt` | ① `clearBookCache(id)` 返回类型 `Unit` → `BookCacheClearOutcome`，内部重排为「预检残留 → 残留保护删除文件 → 最后清空 DB 指针」；② 新增 `scanBookContentResidue` / `scanDirForResidue` 递归预检；③ `deleteBookContentFiles` 改用残留保护删除器 `deleteTreeSparingResidue`（发现标记立即中止并保留剩余内容）；④ 新增顶层 `RESIDUE_SUFFIXES`（`.backup` / `.repairing` / `.importing`）、`hasResidueSuffix`、`containsResidueRecursively`、`BookCacheClearOutcome`、`OfflineBooksClearReport`、`OfflineBooksResidueClear`（清空离线的纯文件层实现） |
| `ui/viewmodel/ShelfBookActions.kt` | `clearCacheForBooks` 逐本汇总 `BookCacheClearOutcome`，按「已清理 N 本 / 残留拒绝 N 本 / 中止 N 本 / 无法判定 N 本」拼装明确失败说明；全拒时消息不含任何成功字样。方法签名未变（`ShelfViewModel` 无需改动） |
| `ui/screen/profile/StorageSubPage.kt` | 「清空所有离线书籍」确认按钮不再直接 `books/` 整目录 `deleteRecursively()`，改走 `OfflineBooksResidueClear.clear`；残留/异常中止时经既有 `ProfileAction.ShowMessage` 通道返回明确失败说明（Snackbar 排队显示，与随后的"已清理缓存"互不覆盖）；确认弹窗文案同步补上「检测到未完成替换的残留备份的条目将被原样保留」（落实审计 P1 选项 A） |
| `data/repository/BookResidueSafeCleanupTest.kt`（新增） | 13 例：合成目录 + mock DAO |
| `ui/viewmodel/ShelfBookActionsTest.kt` | 既有清缓存消息断言适配新返回类型；新增 3 例（全拒/混合/中止消息） |
| `data/repository/BookRepositoryTest.kt` | Context 由 relaxed mock 改为真实临时目录（原因见 §4-2） |
| `ui/viewmodel/ShelfViewModelTest.kt` | **SEAM 例外**：1 行 mock 桩类型适配，见 §5 |

## 2. 保护链设计（两条路径一致）

**残留标记集合**：`.backup`（K2 崩溃窗口内可能是该书仅存的旧正文）、`.repairing`（替换中转：新源暂存或旧正文唯一副本，EPUB/TXT 两套替换流程共用）、`.importing`（EPUB 导入/新鲜源替换暂存，崩溃时可能是新源唯一副本）。后两者是审计明确的销毁对象，且按任务要求「不能可靠判定安全时拒绝」纳入保守集合。

**单书/批量清缓存**（`BookRepository.clearBookCache`）：
1. **预检**（先于一切文件删除与 DB 写入）：递归扫描 `books/<id>/` + 检查 `books/epub/<id>.<marker>`；发现残留 → `RefusedResidue`，目录不可列举等无法可靠判定 → `RefusedUnsafeScan`——两者都**不删任何文件、不写任何库表**，书架条目与本地指针原样保留；
2. **文件阶段**：`deleteTreeSparingResidue` 逐项删除；若预检后新出现标记（并发替换刚写入）或子目录不可列举，**立即中止整棵子树删除**，标记与剩余文件保留，返回 `RefusedResidue(midway=true)`，**跳过全部 DB 改动**（此时部分文件可能已删，但残留与指针未动）；非残留原因的删除失败沿用既有「尽力而为」语义（不阻塞清理）；
3. **DB 阶段**：文件阶段安全完成后，才执行既有的指针清空（`content_status=missing`、`local_uri/local_content_path=null`、payload 置空、book_file 软删）。

**清空所有离线书籍**（`OfflineBooksResidueClear.clear`，纯文件层，与既有行为一致不读不写 DB）：
- `books/<id>/` 目录内任意层级有标记、或不可列举 → **整目录保留**；
- `books/epub/` 内：标记文件保留，且**同书的 `<id>.epub` 一并保留**；其余无残留条目照常删除（原有清理功能不变），全部清空后移除 `books/` 目录壳（与原整目录删除等效）；
- 结果经 `ProfileAction.ShowMessage` 上报：「清空已部分执行：已清理 X 个离线条目；Y 个条目包含未完成替换的残留备份（可能是该书仅存的正文），已原样保留，未删除也未自动恢复，书架信息未改动」；全部被保护时为「清空已中止」；目录不可读时「已中止清空操作；未删除任何文件」。

**明确的不变量**（测试钉住）：拒绝路径上文件与 DB 指针零改动；「保留 backup 但清空 DB 指针」不存在于任何成功形态（midway 中止单独成类、消息明说）；不自动恢复、不覆盖、不删除任何标记文件。

## 3. 实际命令与退出码（均在 `android/` 目录，`--offline`）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest --tests …BookResidueSafeCleanupTest --tests …BookRepositoryTest --tests …ShelfBookActionsTest --offline`（首次） | **EXIT 1**：`compileDebugKotlin` 失败——`hasResidueSuffix` 误定义为 `File` 扩展而调用点传 `String`；修正接收者为 `String` |
| 2 | 同上（第二次） | **EXIT 1**：`ShelfViewModelTest.kt:81` 桩类型不匹配（§5 SEAM 适配）；另发现新测试 13 例全挂 + `BookRepositoryTest` 1 例失败的根因：`File(relaxed mock)` 在 mock 内部 `path` 字段为 null 时抛 NPE，被预检按「不安全」保守拒绝（该行为本身正确，问题在测试夹具） |
| 3 | 修复夹具后同上 | **EXIT 0，51 例全部通过**：BookRepositoryTest 23 + BookResidueSafeCleanupTest 13 + ShelfBookActionsTest 15 |
| 4 | `./gradlew :app:testDebugUnitTest --tests …ShelfViewModelTest --tests …ShelfSourceRefreshFeedbackTest --offline` | **EXIT 0，35 例通过**（21+14）——验证 §5 机械适配无副作用 |

两次失败均为实现/夹具调试过程，最终状态以 #3/#4 为准。`testDebugUnitTest` 已完整编译 `compileDebugKotlin` + `compileDebugUnitTestKotlin`，即本任务所需的必要编译。

**测试覆盖**（合成目录 + mock DAO，无真实数据）：TXT 残留拒绝并保留（backup+repairing+原正文）；EPUB 三种标记逐一拒绝并保留同书 `.epub`；无残留 TXT/EPUB 保持原有清理（目录删除 + 三处 DB 指针断言）；`books/<id>` 为普通文件时无法可靠判定 → 保守拒绝；保护删除器兜底（发现标记中止且保留）与干净子树完整删除；清空离线：残留条目整目录保留、EPUB 同书保护、杂项/干净条目照常清理、无残留时与原行为等效（含目录壳）、`books/` 缺失时 no-op、books 根部孤儿标记保留；批量消息：全拒无成功字样、混合逐项说明、中止单独成句。

## 4. 未覆盖边界

1. **StorageSubPage 的 Compose 接线未做 UI 测试**：对话框 onClick 是薄接线（调对象 + ShowMessage），保护逻辑本体（`OfflineBooksResidueClear`）已全覆盖；Snackbar 呈现效果（文案长度、时长）未上机验证，属审计 §2.1 既有通道特性，UI 反馈改进归 B 窗口。
2. **预检与删除之间的微秒级并发窗口**：标记在预检后、删除中新出现时，由保护删除器兜底（中止、保留标记、跳过 DB），但该窗口内部分非残留文件可能已删除——数据（残留备份）无损，指针未动。彻底消除需全局锁，超出本任务边界。
3. **「清空离线」仍是纯文件层操作**（与既有行为一致）：被清理书籍的 DB 本地指针保留原值（书卡仍显示已下载，重新打开才失败）——这是本次未改动的既有语义；被保护条目的指针保留则是本任务的强制要求。
4. **残留无出路（审计 P1）未解决**：本任务只加保护不加出口；带 `.backup`/`.repairing`/`.importing` 的书会被清缓存与清空离线持续拒绝，直到产品决策的「清理未完成替换残留」出口落地（审计 P1 选项 B/C，SEAM 归 UI/产品侧）。
5. **「清缓存」无二次确认（审计 P2）未处理**：确认弹窗属 `ShelfRoute`（B 文件）；本任务交付的是拒绝时的明确失败说明，不替代确认流。`DeletionScope.REMOVE_CONTENT` 死代码去留仍待产品决策。
6. 非残留原因的文件删除失败仍是尽力而为（空间可能未释放），与既有行为一致。
7. `OfflineBooksResidueClear` 按约定把顶层名为 `epub` 的目录按 EPUB 缓存逐文件处理；书籍 id 为生成值，与该目录名冲突理论上不可能。
8. **全部为 JVM 离线验证**，真机行为（含 qahost 沙箱）零执行，不构成任何设备侧验收。

## 5. SEAM 通告（跨边界最小适配）

**`ui/viewmodel/ShelfViewModelTest.kt`（B 窗口对应测试）1 行机械适配**：`clearBookCache` 返回类型由 `Unit` 改为 `BookCacheClearOutcome` 后，其共享 mock 桩 `coEvery { clearBookCache(any()) } returns Unit` 无法编译。本窗口仅把该行改为 `returns BookCacheClearOutcome.Cleared` 并加注释，**无任何断言/行为变化**；适配后该类 21 例全部通过（§3-#4）。B 窗口请复核此行。除此之外未触碰 `ShelfViewModel`、`ShelfRoute`、`HomeCompletedScreen`、`MyReadingRoute` 及任何其他窗口文件。

**对 B 窗口的其余请求**：无（`ShelfBookActions.clearCacheForBooks` 签名未变，`ShelfViewModel` 无需改动）。审计 P1-B/P2/P3 的 UI 侧跟进项见 §4-4/§4-5。

## 6. 审查动作自证

| 动作 | 是否执行 |
|---|---|
| 修改所有权内源码/测试 + 上述 1 行 SEAM 适配 | ✅ |
| 运行 JVM 定向单测与编译（`--offline`） | ✅ |
| adb / 安装 / instrumentation / 真机 | ❌ |
| 触碰真实书库 / E 盘备份 / 原始仓库 / 冻结归档 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
