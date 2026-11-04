# 阶段 B 早期项关闭复核：S01 父表级联安全 + S02 Room 迁移链（2026-09-28，只读）

> Codex 2026-09-28 独立回读：`retry-01` 两份原始 instrumentation 尾部确为 `OK (5 tests)` 与 `OK (23 tests)`，当前两测试类 `@Test` 数亦为 5/23；当前 `DatabaseModule` 列出 1→2 至 14→15 的迁移且未配置破坏性兜底。接受 S01/S02 **所列范围内的历史设备证据和当前源码机制**，不为相同路径重复上机。注意下文以 mtime 推断“逐文件零漂移”并非字节级证明；若迁移、DAO、实体或 schema 此后更改，旧结果不能自动继续沿用。实体手机证据发生于 2026-09-23，本轮未触碰手机。

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮零源码/测试/账本改动，未运行 Gradle/ADB/MuMu；唯一写入 = 本报告。
- 输入：`AGENTS.md`、`docs/handoff/current.md`、[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)、[任务账本](../../decisions/2026-09/2026-09-22-current-task-ledger.md)、[B 窗数据库审计](../undated/agent-stage-b-database-audit.md)、[统一构建验证](../../legacy-stages/undated/agent-stage-c-unified-build-validation.md)、[数据库真机验收 retry-01](../../../qa/2026-09/2026-09-22-stage-c-database-device-acceptance.md)。
- 方法：先收集既有原始证据，再对当前脏工作树定向读码；**核心动作是漂移核对**——历史绿灯只对被测源码版本有效。

## 1. 漂移核对（本轮关键增量）

| 对象 | mtime | 相对证据轮（2026-09-23 真机验收） |
|---|---|---|
| `AppDatabase.kt` / `DatabaseModule.kt` | 2026-09-16 21:52 | 更早，无漂移 |
| `BookDao` / `InspirationDao` / `TaxonomyDao` | 2026-09-17 | 更早，无漂移 |
| `15.json` | 2026-09-16 21:54 | 更早，无漂移 |
| `AppDatabaseMigrationTest.kt` / `ParentUpsertCascadeSafetyTest.kt` | 2026-09-22 22:32 / 22:24 | 更早，无漂移 |

⇒ **9/23 真机结果适用于当前工作树**：git status 中这些文件虽显示 ` M`（相对 HEAD `5aed5939`），但其最后修改均早于验收轮——"脏工作树"不影响本次结论的适用性，不存在"历史绿灯对不上当前源码"的问题。

## 2. 原始证据回读（retry-01，真机 `c49ac6cf`，2026-09-23）

- `docs/qa/evidence/2026-09-22-stage-c-database-device-acceptance/retry-01/50-instr-ParentUpsertCascadeSafetyTest.txt`：尾行 **`OK (5 tests)`** + `INSTRUMENTATION_CODE: -1`。
- 同目录 `51-instr-AppDatabaseMigrationTest.txt`：尾行 **`OK (23 tests)`** + `INSTRUMENTATION_CODE: -1`。
- 同目录含 fresh Lint（`--rerun-tasks` 31 任务）、双 APK 构建与脚本安装原始日志；未启动主应用、未打开书籍、未清数据（报告 §S01/S02 关闭节）。

## 3. 当前源码定向核对

**S01（父表更新保留子记录）：**
- 【已确认】父表 DAO 全部 `@Upsert`：`BookDao`（4 处，KDoc `:20-38` 明令"绝不能用 REPLACE"，Upsert=ON CONFLICT DO UPDATE 不删行不触发级联）、`InspirationDao`（2）、`TaxonomyDao`（4，`:22` 注明 tags 是 CASCADE 父表）、`LibrarySourceRefDao`（1）——合计 11 个写入口。
- 【已确认】`15.json` 外键精确解析（本轮 Python 解析实体数组，非粗 grep）：**18 条 = 16 CASCADE + 2 SET NULL**（`inspirations` 1、`notes` 1；SET NULL 挂在 books 引用上）。B 窗首轮报告"14 条"已在其更正节改口为 18——**本轮验证更正成立，非误报遗留**。
- 【已确认】测试类当前 5 个 `@Test` 与设备 numtests=5 逐一对应：`upsertingBookPreservesAllChildRows`、`upsertingBooksInBatchPreservesChildRows`、`upsertingInspirationPreservesVariantsAndNoteLink`、`upsertingTaxonomyParentsPreservesJoinRows`、`softDeletePreservesChildRows`（单发/批量/灵感/分类联接/软删除五轴）。

**S02（已声明迁移链保留数据）：**
- 【已确认】`APP_DATABASE_SCHEMA_VERSION = 15`（`AppDatabase.kt:70`）；14 个 `MIGRATION_` 对象覆盖 v1→v15 **每一步**；`DatabaseModule` 仅 `addMigrations`（`:58`），**无 `fallbackToDestructiveMigration`**（无破坏性兜底）。
- 【已确认】`AppDatabaseMigrationTest` 当前 23 个 `@Test` 与设备 numtests=23 一致，逐档含：单步 1→2 … 14→15 全序列、全链 1→6/1→8/1→9/1→10/1→12/12→14、跨步 **13→15**（旧库直升，含来源引用建表与旧数据保留）与 **14→15**（删冗余 term 索引）、以及正式开库回调重建部分索引用例——**这不是"编译成功"，是真机上的真实 SQLite 迁移 + 旧数据断言**。
- 【确认】适用源码版本：被测文件与当前树逐文件无漂移（§1），证据等级可平移至当前工作树。

## 4. 可并入唯一账本的两行表

| 项 | 用户数据行为 | 当前代码证据 | 测试证据 | 结论等级 | 下一项最小动作 | 阻挡阶段 B 退出 |
|---|---|---|---|---|---|---|
| S01 父表更新保留子记录 | 书籍/灵感/分类父行的重复写入不级联删除子记录；books 删除时 inspirations/notes 走 SET NULL、其余子表 CASCADE | 11 处 `@Upsert` + KDoc 禁 REPLACE（BookDao.kt:20-38 等）；15.json 18 FK=16 CASCADE+2 SET NULL（本轮精确解析确认，B 更正成立） | 真机 5/5（retry-01 09-23，`OK (5 tests)` 原始日志可回读；被测文件零漂移） | **已有充分证据关闭（当前范围）** | 无；条件动作=任一父表 DAO/实体/schema 再改动时重跑该 5 例（漂移条款）；"任意损坏 DB/全厂商环境"为已声明范围外，不补测 | 否 |
| S02 Room 升级链保留数据 | v1…v15 逐档升级保留旧数据（含 13→15 直升与 14→15）；无破坏性兜底 | version=15 + 14 个 MIGRATION_ 全覆盖（AppDatabase.kt:70 起）；无 fallbackToDestructiveMigration | 真机 23/23（retry-01 09-23，`OK (23 tests)` 可回读；测试文件 mtime 09-22=被测版） | **已有充分证据关闭（当前范围）** | 无；条件动作=schema/迁移/DatabaseModule 改动时重跑 23 例；旁证（非迁移证据）：S14 MuMu 09-27 见 v15 正式库在 qahost 正常运行 | 否 |

## 5. 边界与误报核查声明

- 两项的"已关闭"均为**当前范围**：单台真机（MIUI/HyperOS，Android 15）+ 干净库升级路径；不覆盖任意损坏数据库、断电中断升级或全部厂商 ROM（账本既有口径，维持）。
- 本轮**未重跑**任何测试（原始结果可回读 + 零漂移，符合"不重复已有可信证据"约束）；S04/S14 各轮 JVM/集成数字未混入本表。
- 旧报告误报核查结论：B 窗首轮"最高 v14 / 14 条外键"已在 9/22 更正且与当前 schema 一致；统一构建 2,928 项为 JVM 口径，不含这两个 androidTest 类，未发现口径混用残留。
- 未 commit/push/reset/checkout；未接触真实手机、正式书库与隐私数据。落盘即停，交总指挥复核。
