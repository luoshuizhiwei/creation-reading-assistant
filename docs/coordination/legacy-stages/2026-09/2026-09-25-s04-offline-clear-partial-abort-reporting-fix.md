# S04 离线书籍清理：部分删除后中止的误报边界修复

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **日期**：2026-09-25
- **前置**：`docs/coordination/operations/2026-09/2026-09-25-s04-residue-cleanup-result-messaging-fix.md`（本轮先读该报告，随后**逐行核对了实际源码**，确认下述问题在代码中真实存在，非仅凭报告推断）
- **性质**：离线实现 + JVM 定向测试。未运行 adb / 真机 / MuMu / instrumentation；未执行实际清理（全部为临时目录合成数据与 mock）；未触碰真实书库、E 盘备份、原始仓库；未 `commit / push / stage / reset / checkout / clean`。
- **所有权**：本轮实际修改 `BookRepository.kt` 及其测试；所有权内的 `StorageSubPage.kt`、`ShelfBookActions.kt` **经核对本轮无需修改**（它们消费的 `userNotice()` / `BookCacheClearOutcome` 形状保持兼容）。未越界。
- **结论先行**：`clear()`/`clearEpubDir` 现在能区分**完全未动 / 目录内部已部分删除后中止 / 完整清理**（外加"删除后仍有文件未删"形态），用户提示与实际文件状态一致；两例点名的确定性回归测试落地；97/97 测试通过（EXIT 0）。

## 1. 源码核对发现的问题（两处）

1. **部分删除后中止被误报为"未动"**：`deleteTreeSparingResidue` 先删除目录内若干文件、随后返回 `ABORTED_RESIDUE`/`ABORTED_UNREADABLE`；`clear()`/`clearEpubDir` 把这类目录一律计入 `protectedEntries`/`unreadableEntries`。若没有其他完整清理的条目，`clearedEntries=0`，`userNotice()` 输出"清空未执行""未删除""已原样保留"——与"目录内部文件已被删除"矛盾。
2. **普通文件删除失败可虚报"已清空所有"**：删除器忽略单个 `child.delete()` 失败并恒返 `COMPLETED`；该目录被计入 `clearedEntries`，在"其余全为 0"时触发"已清空所有离线书籍与资源"。顶层普通文件删除失败则完全不计入任何桶——若它是唯一条目，报告反称"没有需要清理的离线书籍文件"。

## 2. 修复设计（保持残留保护与数据库边界，不扩大删除范围）

- **`deleteTreeSparingResidue` → 返回 `TreeDeletionResult(outcome, deletedAny)`**：`deletedAny` 标记中止前是否已实际删除过文件。枚举新增 `COMPLETED_WITH_REMAINS`（全部尝试删除但存在未删净文件——不中止、不扩大重试，仅如实标记）；子目录的该形态继续向上传递而**不再中断其余内容的清理**；中止类结果仍立即传播。目录壳（空目录）删除失败不计入——内容已清除。
- **报告新增三个计数字段**：`partialResidueEntries` / `partialUnreadableEntries`（目录内部已部分删除后中止，按原因区分）/ `remainsEntries`（尝试删除后仍有文件未删，非保护原因）。`countTreeDeletion` 按 `outcome + deletedAny` 归桶：**零删除即中止 → 整条保留（protected/unreadable，"原样保留"文案如实）**；有删除后中止 → 部分执行。
- **`userNotice()` 组合式重写**：头部三态——零实际删除且无 remains → "清空未执行"；零实际删除但有 remains → "清空未完成"；否则 → "清空已部分执行"；各非零桶逐项展开（含"该条目部分文件已被清理，残留备份与剩余文件均原样保留"专用句式）。"已清空所有离线书籍与资源"仅在**六个计数中只有 clearedEntries 非零**时出现；"未删除任何文件"仅出现在 `rootUnreadable`（完全未枚举）。
- **注释同步**：`OfflineBooksClearReport` KDoc 写明六字段与三态的映射及 partial* 的专指含义；`BookCacheClearOutcome.Cleared` 如实说明"个别文件可能因占用未删净（既有尽力而为语义）"；`deleteBookContentFiles` 注明 `COMPLETED_WITH_REMAINS` 不拦截 DB（沿用既有尽力而为边界，仅空间未完全释放）。
- **删除边界与 DB 边界未变**：含残留条目整条保留；中止仍立即停止后续删除并跳过 DB；普通文件删除失败不重试、不扩大删除范围。

## 3. 修改文件

| 文件 | 改动 |
|---|---|
| `data/repository/BookRepository.kt` | ① `TreeDeletionOutcome` 增 `COMPLETED_WITH_REMAINS`；② 新增 `TreeDeletionResult(outcome, deletedAny)`，`deleteTreeSparingResidue` 重构（local `walk`，失败/已删双标志，中止传播规则修正）；③ `deleteBookContentFiles` 适配新返回类型（remains 不拦截 DB）；④ `OfflineBooksClearReport` 增 `partialResidueEntries`/`partialUnreadableEntries`/`remainsEntries`，`userNotice()` 重写；⑤ `OfflineBooksResidueClear` 改用 `ClearCounts` 共享计数器（clear/clearEpubDir 共用），顶层与 epub 文件删除失败计入 remains；⑥ 相关 KDoc 全部同步 |
| `data/repository/BookResidueSafeCleanupTest.kt` | 16 → 23 例（详见 §4） |

`StorageSubPage.kt`、`ShelfBookActions.kt` 本轮零修改（消费接口兼容）；`ProfileRoute.kt`、`ShelfViewModelTest.kt` 等其他窗口文件未触碰。

## 4. 新增/调整测试（BookResidueSafeCleanupTest 22 → 23 例）

**两例点名回归（确定性 TOCTOU 模拟）**：
1. `clear all counts partial residue abort when first dir partially deleted and others untouched`——mock 固定遍历顺序 + `name` 用 `returnsMany` 两段返回（预检时"innocent.txt"、删除时"x.backup"）：断言 `partialResidueEntries=1`、`protectedEntries=1`、`clearedEntries=0`、目录内真实文件已被删除（部分执行的实据）、提示含"清空已部分执行/发现新的替换残留/部分文件已被清理"、**不含**"清空未执行""未删除任何文件""已清空所有"。
2. `clear all counts partial unreadable abort when first dir partially deleted and others untouched`——子目录 `listFiles()` 用 `returnsMany`（预检返回空数组、删除阶段返回 null）：断言 `partialUnreadableEntries=1`、`unreadableEntries=1`、真实文件已删、提示含"遇到不可读取的目录、已中止/部分文件已被清理"、不含未执行/未删除/已清空所有。

**配套边界测试**：
3. `clear all counts zero-deletion residue abort as untouched protected entry`——首个子项即标记（零删除即中止）→ 计入"整条保留"而非部分执行，提示"清空未执行"（该形态下如实）；
4. `clear all does not claim fully cleared when file deletion fails`——顶层文件删除失败 → `remainsEntries=1`、`clearedEntries=0`，提示"清空未完成/未能完全删除"，不含"已清空所有/没有需要清理"；
5. `sparing deleter reports untouched abort when nothing was deleted before abort`——删除器级：标记首现/根不可列举 → `(ABORTED_*, deletedAny=false)`；
6. `sparing deleter reports completed with remains when file delete fails`——文件删除失败 → `COMPLETED_WITH_REMAINS`；
7. `user notice reports directory partial aborts distinctly from untouched entries`——partialResidue/partialUnreadable/remainsOnly 三种报告形态的文案逐一断言；既有 mid-list 中止测试补 `deletedAny=true` 断言。

## 5. 实际命令与退出码（`android/` 目录，`--offline`）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest --tests …BookResidueSafeCleanupTest --tests …BookRepositoryTest --tests …ShelfBookActionsTest --tests …ShelfViewModelTest --tests …ShelfSourceRefreshFeedbackTest` | **EXIT 1**：`compileDebugKotlin` 失败——新 KDoc 中 `partial*/remains` 的 `*/` 提前终止注释块，改写措辞 |
| 2 | 同上 | **EXIT 1**：`deleteBookContentFiles` 的 `when` 以 `TreeDeletionResult` 为 subject 与枚举分支比较，类型不符；改为对 `.outcome` 分支 |
| 3 | 同上 | **EXIT 1**：96 例完成、1 例失败——`walk()` 漏算删除失败标志且子目录 remains 被误作中止传播；补 `anyDeleteFailed` 并修正传播 |
| 4 | 同上 | **EXIT 0**：96/96 通过 |
| 5 | 同上（补零删除中止接线测试与 Cleared KDoc 后复跑） | **EXIT 0，BUILD SUCCESSFUL：97/97 通过**（BookRepositoryTest 23 + BookResidueSafeCleanupTest 23 + ShelfBookActionsTest 16 + ShelfSourceRefreshFeedbackTest 14 + ShelfViewModelTest 21）。前 4 次失败均为调试过程，最终状态以 #5 为准 |

## 6. 仍未覆盖的边界

1. **真实文件系统的不可读取目录未模拟**（Windows ACL 无法可靠构造），"先成功/先部分删除、后遇不可读取"经 mockk `returnsMany` 确定性模拟；其验证的是生产代码的分支行为，不是 OS 层行为。
2. **`clearBookCache` 单书路径对 remains 不单独呈现**：`COMPLETED_WITH_REMAINS` 沿用既有尽力而为语义继续清 DB（已注明），`BookCacheClearOutcome` 未新增形态——单书消息"已清理 N 本"在该形态下仍有轻微乐观，属本轮明确保留的既有边界（如需区分属产品决策）。
3. **`clearEpubDir` 的"目录内部部分删除后中止"未单测**：epub 子目录走与顶层相同的 `countTreeDeletion` 归桶逻辑（顶层已测），epub 专属分支（同书保护、标记文件）另有既有用例覆盖。
4. **零删除中止的"unreadable 形态"未在 clear() 层单测**（deleter 层已测 `(ABORTED_UNREADABLE, false)`，clear 层 residue 形态已测，unreadable 形态的归桶走同一 `countTreeDeletion` 分支）。
5. `userNotice()` 文案为用户可见字符串变更，未经真机呈现验证；StorageSubPage 的 Compose 接线仍无 UI 测试。
6. 全部为 JVM 离线验证；**本报告与测试结果不构成任何真机/设备验收结论**。

## 7. 审查动作自证

| 动作 | 是否执行 |
|---|---|
| 先读前置报告并逐行核对实际源码（非仅依报告） | ✅ |
| 修改所有权内文件（本轮实际仅 `BookRepository.kt` + 其测试） | ✅ |
| 运行 JVM 定向单测与编译（`--offline`） | ✅ |
| adb / 真机 / MuMu / 安装 / instrumentation / 实际清理 | ❌ |
| 触碰真实书库 / E 盘备份 / 原始仓库 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
