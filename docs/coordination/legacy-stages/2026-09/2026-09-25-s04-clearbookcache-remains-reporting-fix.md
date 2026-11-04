# S04 单书 clearBookCache："正文未删净"结果口径修复

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **日期**：2026-09-25
- **前置**：`docs/coordination/operations/2026-09/2026-09-25-s04-residue-cleanup-result-messaging-fix.md`、`docs/coordination/legacy-stages/2026-09/2026-09-25-s04-offline-clear-partial-abort-reporting-fix.md`
- **性质**：离线实现 + JVM 定向测试。未运行 adb / MuMu / 真机 / instrumentation；未执行实际清理（合成目录 + mock DAO + 测试内文件锁）；未触碰真实书库、E 盘备份、原始仓库；未 `commit / push / stage / reset / checkout / clean`。
- **所有权**：本轮修改 `BookRepository.kt`、`ShelfBookActions.kt` 及其直接相关测试（`BookResidueSafeCleanupTest`、`ShelfBookActionsTest`）。未触碰 B 的验收文件及其他窗口文件。
- **结论先行**：单书清理在"个别正文文件删除失败"时不再返回 `Cleared`，而是新的 `ClearedWithRemains`；批量消息单列"N 本正文未删净"子句，不与完整清理混同。残留保护与数据库清理边界**原样未动**。101/101 测试通过（EXIT 0）。

## 1. 审计：实际路径与数据后果

**实际路径**（逐行核对当前源码）：`clearBookCache` → ① 残留预检（拒绝路径不变）→ ② `deleteBookContentFiles`：TXT 树走 `deleteTreeSparingResidue`，**`COMPLETED_WITH_REMAINS`（个别文件删除失败）继续不拦截**；EPUB 删除前复查同书标记后直接 `epub.delete()` 且**返回值此前完全未检查**（连日志都没有）→ ③ 数据库指针清理（`content_status=missing`、`local_uri`/`local_content_path=null`、payload 置空、`book_file` 软删）→ ④ 返回 `Cleared`，调用方显示"已清理 N 本书的本地正文缓存"。

**数据后果（remains 形态）**：
- 数据库：与"正文已清"完全一致——书卡不再显示已下载、不可打开；功能上无破坏（符合用户"移除正文"的意图）。
- 磁盘：正文文件残留 → 空间未释放；文件成为 DB 不可达孤儿；`.backup`/`.repairing`/`.importing` 标记始终被 sparing 保护，不会因此销毁。
- 无数据丢失路径：文件是被保留而非误删；不会自动恢复、不会破坏残留备份。
- 结论：**保留"remains 时继续清 DB"的既有边界是最小且安全的选择**——若改为保留指针，书会显示"已下载"但正文处于半删状态，状态更混乱。问题只在第 ④ 步的口径：`Cleared` 与"已清理"隐藏了"正文未删净、空间未释放"。EPUB 删除失败此前零感知，需一并纳入。
- 是否提供"孤儿正文文件清理入口"属后续产品决策，本轮不做。

## 2. 修复设计（最小、安全）

- `deleteBookContentFiles` 返回类型 `MidwayAbortCause?` → 私有三态 `ContentFileDeletionOutcome`：`FullyCleared`（全部删净）/ `RemainsAfterAttempt`（尽力删除后仍有正文残留：TXT 树 `COMPLETED_WITH_REMAINS` 或 **EPUB 删除失败——新增检查 `epub.delete()` 返回值**）/ `Aborted(cause)`（原中止语义不变）。
- `BookCacheClearOutcome` 新增 `data object ClearedWithRemains`（指针已清空、书架状态已更新，但正文未删净）；`Cleared` 收窄为"正文已全部删除"。中止/拒绝形态与全部残留保护规则**一字未动**。
- `clearBookCache`：`Aborted` → `AbortedMidway`（拦截 DB，不变）；其余两态**都执行原 DB 清理流程**（边界不变），仅按文件阶段结果返回 `Cleared` / `ClearedWithRemains`。
- `ShelfBookActions.clearCacheForBooks`：`clearedWithRemains` 独立计数，消息单列子句——"N 本正文未删净：书架与数据库状态已更新，但仍有正文文件留在本地（可能被系统占用），空间未完全释放"；全部为 remains 时**不出现**"已清理 N 本书的本地正文缓存"句式。
- 注释同步：结果接口 KDoc 改为"完全未动 / 部分执行 / 完成（删净、未删净两形态）"；`clearBookCache`、`deleteBookContentFiles` KDoc 与关键行内注释均说明 Remains 的语义与边界。

## 3. 修改文件

| 文件 | 改动 |
|---|---|
| `data/repository/BookRepository.kt` | 新增私有 `ContentFileDeletionOutcome`（FullyCleared / RemainsAfterAttempt / Aborted）；`deleteBookContentFiles` 返回三态并新增 EPUB 删除失败检查；`BookCacheClearOutcome` 增 `ClearedWithRemains`，KDoc 重写；`clearBookCache` 按文件阶段三态返回，DB 流程与注释同步 |
| `ui/viewmodel/ShelfBookActions.kt` | `clearCacheForBooks` 增 `clearedWithRemains` 计数；消息新增"正文未删净"独立子句 |
| `data/repository/BookResidueSafeCleanupTest.kt` | +2 例（16+2+… 现 25 例）：TXT/EPUB 删除失败 → `ClearedWithRemains`、未删净文件保留、其余文件照常清理、**DB 指针照常清理**（verifyDbPointersCleared） |
| `ui/viewmodel/ShelfBookActionsTest.kt` | +2 例（现 18 例）：混合（1 本删净 + 1 本未删净）消息不混同计数；全部未删净时无完整清理句式 |

`StorageSubPage.kt` 等未触碰；`ShelfViewModelTest`（B 窗口文件的既有 SEAM 适配）本轮未动。

## 4. 实际命令与退出码（`android/` 目录，`--offline`）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest --tests …BookResidueSafeCleanupTest --tests …BookRepositoryTest --tests …ShelfBookActionsTest --tests …ShelfViewModelTest --tests …ShelfSourceRefreshFeedbackTest` | **EXIT 0，BUILD SUCCESSFUL，101/101 通过**（BookRepositoryTest 23 + BookResidueSafeCleanupTest 25 + ShelfBookActionsTest 18 + ShelfSourceRefreshFeedbackTest 14 + ShelfViewModelTest 21） |
| 2 | 同上（为两个锁文件用例补 `Assume` 平台守卫后复跑） | **EXIT 0，101/101 通过** |

**新增回归测试要点**：
- **删除失败 → 结果与文件状态一致**：以持有 `FileOutputStream` 锁定文件的方式在 Windows 上确定性模拟"正文文件被占用"（TXT 侧锁 `books/r1/book.txt`，EPUB 侧锁 `books/epub/r2.epub`）——断言返回 `ClearedWithRemains`（而非 `Cleared`）、未删净文件保留、**其余文件照常清理**（失败不扩散）；
- **数据库指针**：两个 remains 用例均复用 `verifyDbPointersCleared` 断言 `content_status=missing`、`local_uri`/`local_content_path=null`、payload 置空、`book_file` 软删——锁定"remains 不改变 DB 边界"；
- **用户消息**：混合场景"已清理 1 本…；1 本正文未删净…"且不含"已清理 2 本"；全部未删净时不含完整清理句式、含"书架与数据库状态已更新/空间未完全释放"。

## 5. 仍有的风险与边界

1. **锁文件模拟是 Windows 专属确定性手段**（Linux unlink 语义下删除会成功）——两个用例已加 `Assume`（非 Windows 优雅跳过）；本仓库离线验证在 Windows 宿主执行，两用例实际运行并通过。
2. **remains 的可见性依赖用户留意 Snackbar**：消息通道仍是书架 Snackbar（既有 Short 时长特性），文案加长后的可读性未上机验证；改用持久提示属 UI/产品决策（B 窗口范围）。
3. **remains 后的孤儿正文文件没有清理入口**（本轮不做，属产品决策）；在提供入口前，用户唯一手段仍是"清空所有离线书籍"（该路径对不可判定条目同样保守）。
4. `deleteBookContentFiles` 内非锁类 I/O 异常仍走 `runCatching` 告警分支：异常若发生在 TXT 树删除中途，删除器会以 ABORTED_UNREADABLE 中止（不变）；EPUB 删除若抛异常（而非返回 false），仍记日志后按 FullyCleared 继续——概率极低（`File.delete` 不抛异常），本轮未改变该既有行为，登记为已知残余。
5. `ClearedWithRemains` 未携带未删净文件数/路径（避免把文件名送入消息通道）；如需逐文件明细归因，属后续私有诊断功能。
6. 全部为 JVM 离线验证；本报告与测试结果不构成任何真机/设备验收结论。

## 6. 审查动作自证

| 动作 | 是否执行 |
|---|---|
| 修改所有权内文件（`BookRepository.kt`、`ShelfBookActions.kt` + 两个直接相关测试） | ✅ |
| 运行 JVM 定向单测与编译（`--offline`，无网络） | ✅ |
| adb / MuMu / 真机 / 安装 / instrumentation / 实际清理 | ❌ |
| 触碰真实书库 / E 盘备份 / 原始仓库 / B 的验收文件 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
