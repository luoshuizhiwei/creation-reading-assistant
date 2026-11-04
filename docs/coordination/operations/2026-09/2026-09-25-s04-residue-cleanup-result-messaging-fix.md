# S04 缓存清理结果与用户提示一致性修复

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **日期**：2026-09-25
- **前置**：`docs/coordination/operations/2026-09/2026-09-25-s04-residue-safe-cleanup-implementation.md`（残留保护清理链）
- **性质**：离线实现 + JVM 定向测试。未运行 adb / 真机 / MuMu / instrumentation；未执行任何实际清理（全部为临时目录合成数据）；未触碰真实书库、E 盘备份、原始仓库；未 `commit / push / stage / reset / checkout / clean`。
- **所有权**：本轮仅修改 `BookRepository.kt`、`ShelfBookActions.kt`（上一轮已在所有权内）、`StorageSubPage.kt`、`ProfileRoute.kt` 及其直接相关测试。未触碰其他窗口文件。
- **结论先行**：清理结果现在严格三态——**完全未动 / 部分执行 / 完成**，所有用户提示与实际文件/数据库状态一致；修复了"部分执行却提示未删除任何文件"的直接矛盾。

## 1. 发现并修复的不一致

| # | 问题 | 修复 |
|---|---|---|
| 1 | **`OfflineBooksResidueClear.clear` 先清理若干条目、后遇不可读取目录时返回 `abortedUnsafe=true`，StorageSubPage 据此显示"未删除任何文件"** —— 与实际（部分条目已删）矛盾 | 报告改为精确计数：`clearedEntries` / `protectedEntries` / `unreadableEntries` / `rootUnreadable`（取代粗粒度布尔 `abortedUnsafe`）；提示由 `OfflineBooksClearReport.userNotice()` 按字段组合生成，"未删除任何文件"只在 `rootUnreadable`（完全未枚举）时出现 |
| 2 | 单书 `clearBookCache` 的中途拒绝结果此前伪装成 `RefusedResidue(midway=true)`——"拒绝"与"部分执行"语义混在一起 | 新增独立形态 `AbortedMidway(cause: MidwayAbortCause)`；`RefusedResidue` 收窄为纯预检拒绝。三态：预检拒绝/无法判定=**完全未动**，`AbortedMidway`=**部分执行**（部分文件可能已删，残留标记与 DB 指针均保留），`Cleared`=**完成**。`deleteTreeSparingResidue` 返回 `TreeDeletionOutcome`（COMPLETED / ABORTED_RESIDUE / ABORTED_UNREADABLE），`deleteBookContentFiles` 返回 `MidwayAbortCause?`，中止原因不再混淆 |
| 3 | `BookCacheClearOutcome` 注释未表达三态 | KDoc 重写：明确三态定义与各自对文件/数据库状态的保证；明确"调用方必须把部分执行当作未完成呈现，不得报成功" |
| 4 | 清空离线确认后 `ClearReaderCacheNow` 又显示笼统的"已清理缓存,释放 X"，读者会误以为是离线正文清理的结果汇总 | `ProfileRoute` 两处（`ClearReaderCache` / `ClearReaderCacheNow`）消息改为"已清理阅读器临时缓存，释放 X"——明确只覆盖 cacheDir；离线正文的清空结果由前一条 `ShowMessage`（报告文案）单独、完整表达。两条消息经 Snackbar 排队依次展示 |

**保护边界未放宽**：遇到不可读取目录时 `clear()` 继续处理其余条目（各自独立判定），但不可读取条目一律原样保留并计入 `unreadableEntries`；含残留条目整条保留；删除中途发现新残留仍立即中止且跳过 DB 改动。所有放宽 none。

## 2. 现在的提示矩阵（`userNotice()` / ShelfBookActions）

| 实际状态 | 提示要点 |
|---|---|
| books/ 缺失 | "没有需要清理的离线书籍文件。"（原为静默，本轮起如实说明） |
| books/ 本身不可读/不可列举（或防御分支触发） | "无法读取本地书籍目录，清空操作未执行、未删除任何文件。" |
| 全部条目含残留，零删除 | "清空未执行：N 个条目包含未完成替换的残留备份（可能是该书仅存的正文），已原样保留…" |
| 部分清理 + 残留保留 +/或 不可读取保留 | "清空已部分执行：已清理 X 个离线条目；N 个条目包含…残留备份…已原样保留；另有 M 个条目无法读取，已按保守策略保留；书架信息未改动。" |
| 全部清理 | "已清空所有离线书籍与资源（共清理 X 个条目）。"（原为静默，本轮起如实汇总） |
| 单书预检拒绝 / 无法判定 | "N 本检测到未完成替换的残留备份，已整本保留…" / "N 本无法确认本地文件状态，已保守跳过" |
| 单书中止·新残留 | "N 本清理中止：删除过程中发现新的替换残留，备份与书架信息已保留（部分本地文件可能已被清理）" |
| 单书中止·目录不可读取 | "N 本清理中止：过程中书籍目录变为不可读取，已按保守策略停止并保留现场…" |

## 3. 修改文件

| 文件 | 改动 |
|---|---|
| `data/repository/BookRepository.kt` | `BookCacheClearOutcome` 三态化（`RefusedResidue(residueCount)` 去掉 midway 布尔；新增 `AbortedMidway(cause)` 与 `MidwayAbortCause` 枚举）；`TreeDeletionOutcome` 枚举 + `deleteTreeSparingResidue` 区分中止原因（子目录中止原因向上传播）；`deleteBookContentFiles` 返回 `MidwayAbortCause?`；`OfflineBooksClearReport` 精确计数 + `userNotice()`；`clear`/`clearEpubDir` 分别计数 unreadable，books/ 缺失返回空报告、存在但不可枚举返回 rootUnreadable |
| `ui/viewmodel/ShelfBookActions.kt` | 按 `AbortedMidway` 两种原因分别汇总与措辞（清理中止·新残留 / 清理中止·不可读取） |
| `ui/screen/profile/StorageSubPage.kt` | 确认按钮改为无条件 `ShowMessage(report.userNotice())`；删除按布尔分支的旧私有文案函数；防御分支按"完全未动"报告 |
| `ui/screen/profile/ProfileRoute.kt` | `ClearReaderCache` / `ClearReaderCacheNow` 消息精确化为"已清理阅读器临时缓存，释放 X"，并注释说明与离线正文清空结果的分工 |
| `data/repository/BookResidueSafeCleanupTest.kt` | 16 例（新增 3：`clear all reports partial execution when unsafe dir follows cleared entries`、`sparing deleter keeps later subtree untouched when marker appears mid-list`、`user notice distinguishes untouched partial and completed outcomes`；其余适配新枚举/字段） |
| `ui/viewmodel/ShelfBookActionsTest.kt` | 16 例（midway 用例拆分为两种原因各自成测，断言"部分执行不得报成功"） |

## 4. 实际命令与退出码（`android/` 目录，`--offline`）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest --tests …BookResidueSafeCleanupTest --tests …BookRepositoryTest --tests …ShelfBookActionsTest` | **EXIT 1**：`compileDebugKotlin` 失败——BookRepository.kt:279 残留旧 `midway` 实参（签名迁移遗漏），修正 |
| 2 | 同上 | **EXIT 1**：`compileDebugUnitTestKotlin` 失败——测试文件 4 处旧 `RefusedResidue` 双参构造，修正 |
| 3 | 同上 | **EXIT 1**：55 例完成、1 例失败——新回归测试的 mock `booksDir` 缺 `exists()` 桩，补桩 |
| 4 | 同上 + `--tests …ShelfViewModelTest --tests …ShelfSourceRefreshFeedbackTest` | **EXIT 0，BUILD SUCCESSFUL**：90 例全部通过（BookRepositoryTest 23 + BookResidueSafeCleanupTest 16 + ShelfBookActionsTest 16 + ShelfSourceRefreshFeedbackTest 14 + ShelfViewModelTest 21）。前 3 次失败均为调试过程，最终状态以 #4 为准 |

**新增回归测试要点**：
- **先成功、后遇不安全目录**：mock `booksDir.listFiles()` 固定条目顺序（真实干净目录 t1 + 不可枚举的 mock 目录 t2）——断言 `clearedEntries=1 && unreadableEntries=1`、t1 确实已删除、`userNotice()` 含"清空已部分执行/1 个条目无法读取"、**不含"未删除任何文件"**；
- **中途出现残留**：mock 子树 `listFiles()` 固定顺序（a.txt → x.backup → late/）——断言返回 `ABORTED_RESIDUE`、a.txt 已删（中止前的清理照常生效）、x.backup 保留、**late/ 子树原样未动**；
- **三态提示**：rootUnreadable / 空 / 完成 / 全部保留 / 部分执行五种组合的文案逐一断言。

## 5. 剩余风险与未覆盖边界

1. **clearBookCache 全管线 TOCTOU 未直接测试**：预检与删除之间出现残留的真实竞态窗口无法在 JVM 中注入，本轮以确定性顺序的删除器级测试 + AbortedMidway 接线测试组合覆盖；全管线注入接缝未添加。
2. **不可读取目录的真实文件系统模拟未做**：Windows ACL 下无法可靠构造"所有者不可列举"目录，"先成功后遇不安全"用 mock File 条目固定顺序验证生产行为（继续处理其余条目、保留不可判定条目）。
3. **StorageSubPage 的 Compose 接线仍无 UI 测试**：对话框 → `ShowMessage` 的连线是薄层；`userNotice()` 本体已全覆盖。Snackbar 呈现（长度/时长）未上机验证。
4. **部分执行下部分文件已删除**是该策略的设计内结果（保护优先于完整清理），两条中止消息均已明说"部分本地文件可能已被清理"。
5. "没有需要清理的离线书籍文件"与"已清空所有离线书籍与资源（共清理 X 个条目）"是本轮**新增**的提示（原先静默），属有意的准确化，未在真机确认呈现效果。
6. 防御性 `getOrElse → rootUnreadable` 分支：`clear()` 全程使用不抛异常的 File 操作，该分支实践中不可达；若未来实现变更引入异常，其文案按"完全未动"报告，需随实现同步复核。
7. 全部为 JVM 离线验证（合成目录 + mock），不构成任何真机验收；本窗口不宣布真机验收通过。

## 6. 审查动作自证

| 动作 | 是否执行 |
|---|---|
| 修改所有权内源码/测试（含 `ProfileRoute.kt`） | ✅ |
| 运行 JVM 定向单测与编译（`--offline`，无网络） | ✅ |
| adb / 真机 / MuMu / 安装 / instrumentation / 实际清理 | ❌ |
| 触碰真实书库 / E 盘备份 / 原始仓库 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
