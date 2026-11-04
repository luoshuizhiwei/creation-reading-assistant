# 夜间结果勘误与最终快照（2026-09-24，窗口 D · 只读纠偏）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`
- HEAD（本轮 `git rev-parse HEAD` 实测）：`5aed5939600e491843e3c44453921bd6273a233c`，staged **0** 项
- 性质：**只读证据纠偏 + 总账追加**。未修改任何源码/测试，未运行 Gradle / ADB，未接触手机、用户书库与设备设置，未执行 `reset / checkout / clean / stash / stage / commit / push`。
- 写入（仅三处）：本文件、`docs/qa/2026-09/2026-09-24-overnight-s04-epub-stage-q-review.md` 末尾**追加**的勘误节（原文一字未删）、`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md` 末尾**追加**的日期快照。

## 1. 对窗口 B 独立审查的纠偏

对象：`docs/qa/2026-09/2026-09-24-overnight-s04-epub-stage-q-review.md`（署名窗口 B）。全文保留，勘误以追加节 E1–E7 表达。摘要：

| # | B 的原判断 | 复核结果 | 依据 |
|---|---|---|---|
| E1 | 报告头记 HEAD | **抄写笔误**（42 字符，第 21 位起多插 `c3`），非仓库状态差异；其 §0.1 两份文件哈希与行数本轮重算逐位一致 | `git rev-parse HEAD` 40 字符真值 |
| E2 | **#1 P1**：`target` 原不存在 ⇒ 替换后 DAO 失败 ⇒ `:138` 删掉**唯一可读副本**且**不可恢复** | **撤回原触发链，降级为「未经证实」**：`validatedFile` 与 `target` 是**两个独立文件**；catch 不删 `validatedFile`（新字节仍在），原始来源在应用之外；且「`target` 原不存在」前提下该路径本就没有可读副本可销毁 | `EpubRepository.kt:79-80`、`:85`、`:134-138` + `EpubParser.kt:57`（无 `expectedBookId` ⇒ `id = "epub_" + uri.hashCode(36)`）、`:60`、`:98` |
| E3 | （B 未提及） | 登记**更宽的真实窗口**：若异常发生在 `EpubRepository.kt:92-96`（staging 复制或 `:95` 长度校验），则 `:98` 尚未建立 backup，而 `catch` 无条件 `target.delete()` 删掉**本次未触碰、仍然完好的旧 target**且无备份可还原；`:90` 开局又主动删掉上次残留 backup。记为 **P1 候选（静态分析，未编译未运行）**。现有用例覆盖不到：用例 3 的失败注入在 try 之外（`EpubRepository.kt:79`），用例 4 注入在 `:131`（backup 已建立）。**不臆断绝无其他风险**：`renameTo` 失败回退 `copyTo` 中途失败会留下残缺 backup 并被提升为 target，同段另一损坏形态一并登记 | `EpubRepository.kt:89-101`、`:136-148`；`EpubRepositoryReplaceTest.kt:273-321`、`:325-376`（旧 target 预置于 `:341`） |
| E4 | **#4** 建议「`catch` 前加 `catch (CancellationException) { throw e }`」 | **该建议不可采用**：会让取消路径**跳过 `:137-144` 的文件回滚**，留下「新文件已换入 / DB 未更新 / backup 未清理」的错配——正是本报告要防的形态。现状 `throw error`（`:145`）本就已传播取消；若要区分取消与失败，必须把**回滚移入 `finally`**，而不是提前抛出。#4 拆分：取消分支一半**撤回**，「恢复段自身异常顶替原始 `error`」一半**保留 P2** | `EpubRepository.kt:136-148` |
| E5 | #2 #3 #5 #6 #7 #8、阈值三处一致、测试无假阳性 | 本轮未发现反证，**维持原判**；其中 #5（用例 4 覆盖缺口）被 E3 进一步强化 | 同报告 §1.2、§1.4-§2.6 |

## 2. 对窗口 C 结果的更正（覆盖窗口 D 昨晚的快照登记）

昨晚我在总账里按 **03:01:37 快照**记了「当前工作树有 C 在途的 JVM 红：`PendingTurnQueueTest` `tests="11" failures="2"`」，并据此推出「三条门禁全部早于 02:57、对当前 PageTurner 失效」。**两条都需更正**（原记录保留，不删）：

| 项 | 更正后状态 | 证据 |
|---|---|---|
| C 的队列 JVM 测试 | **`PendingTurnQueueTest` 11/11 通过，`failures="0" errors="0"`**（Codex 新鲜复跑）；我看到的 2 条红出现在 **C 的变异验证期间**，不是最终状态 | 本轮亲自回读 `android/app/build/test-results/testDebugUnitTest/TEST-…PendingTurnQueueTest.xml`，mtime **09-24 03:03**，suite 属性 `tests="11" skipped="0" failures="0" errors="0"` |
| androidTest 编译 | **`:app:compileDebugAndroidTestKotlin` 成功**（Codex 报告），且产物时间戳佐证其晚于 02:57 的 `PageTurner.kt` 改动 | `build/tmp/kotlin-classes/debugAndroidTest/…/PageTurnerHighSpeedTest.class` mtime **09-24 03:02**；androidTest 编译依赖主源码编译 ⇒ 推断主源码亦已重编（**推断**，非直接记录） |
| 昨晚的「门禁失效」结论 | 对 **01:01 / 01:51 / 22:58** 三条记录仍成立（它们确实早于 02:57），但**已被 03:02-03:03 的复跑取代**；`app-debug.apk`（09-23 22:58）与 test APK（09-24 01:51）**仍未包含** 02:57 后的 PageTurner，真机侧若要用必须重建 | 同上 + APK mtime |
| 仍不得关单 | **是**。C 新一轮修复仍在并行；11/11 是**队列纯逻辑**的 JVM 结果，**不是** 5 项 instrumentation 用例、也不是真机翻页行为 | 见 §3 |

## 3. 最终快照（不得升格为 PASS 的项，逐条给口径）

| 项 | 当前可主张 | 当前不可主张 | 证据文件 |
|---|---|---|---|
| **S04 / Stage Q** | 代码与测试形态已落地工作树（`EpubRepository.kt:329-343` 无吞异常、5 个 `@Test`）；B 的独立审查提出 2 项经我复核后一项撤回、一项新增 | **S04 = PARTIAL**。Stage Q 自身 red/green 与回归 XML 均在仓库外、工作树不可回读；E3 的 P1 候选未被任何用例覆盖；Stage Q **仍无 Codex 复核** | `agent-stage-q-s04-epub-hash-failure-correction.md`；本报告 §1；`2026-09-24-overnight-s04-epub-stage-q-review.md` E1–E7 |
| **B-1 / B-2（导入后定位）** | B 侧代码与 JVM 用例存在（`ShelfImporterTest` 39、`ShelfImportFocusTargetTest` 15） | **仍 PARTIAL**：B-1 原始记录全程未点击「查看」；B-2 的 fallback 与「按最近导入排第一」两种解释在截图/dump 上不可区分 ⇒ 不可判定。需重新真机执行取可区分证据 | `2026-09-24-b-evidence-alignment.md`；`2026-09-23-bcd-evidence-correction.md` §3 |
| **C 真机 Compose 用例（5 项）** | 队列纯逻辑 JVM 11/11；androidTest 编译成功 | **0 项取得运行结果** ⇒ 不得写 PASS 也不得写 FAIL。宿主 Activity 在 MIUI 上被拦（`result code=102`）连续三轮复现，且 `2026-09-24-compose-test-host-investigation.md` §0 =「**暂无可信绕过方案**」 | `2026-09-24-c-high-speed-test-diagnosis.md`；`2026-09-24-cd-portrait-independent-acceptance.md` §4 |
| **D 真机 Compose 用例（4 项）** | 4 条已确实打进 test APK（javap 复核）；JVM 侧几何镜像不变式 8/8 为报告口径（其 XML 已被覆盖） | **1 项 BLOCKED + 3 项未运行，0 项取得结果**；RTL 渲染未在真机证明；旧 `d_rtl_*` 截图是「未镜像」的反证 | `2026-09-24-d-rtl-compose-test.md`；`2026-09-24-d-rtl-root-cause.md`；cd 验收 §4.2 |
| **B / C 新一轮修复** | 正在并行 | **尚未验收，本轮不替它们下任何结论** | 各自窗口报告（待其交付） |

## 4. 下一轮要点（不改变本轮边界）

1. **S04**：把 Stage Q 的 red/green XML 与回归 XML 落进仓库（或重跑并当场哈希），并为 E3 补一条「旧 target 完好 + `:92-:96` 注入失败 ⇒ 断言 target 逐字节存活」的用例；`ShelfImporter.kt:419-433`（#2）由属主决定是否同批修。**不要**采纳 E4 撤回的那半条建议。
2. **C/D 真机**：先裁 SR-ENV-1（换真机 / CI / 接受本机不可验并改 JVM 替代门禁）。若维持「真实手机」要求，则该裁定是 9 项的唯一前置。
3. **门禁**：任何真机结论必须使用在 02:57 之后重建的 APK 与 test APK（当前 `app-debug.apk` 仍是 09-23 22:58）。
4. **留证纪律**：`--tests` 定向运行会覆盖 `test-results/testDebugUnitTest/` 下 XML（本轮两次实测：D 的 01:01 结果被 C 的 03:01/03:03 运行取代）。需要留证的运行必须当场复制或登记哈希。

## 5. 边界声明

未修改任何 `.kt` / manifest / Gradle / 脚本 / 他人报告正文；未运行 Gradle 与 ADB；未接手机、未改设备设置与系统语言；未 `stage / commit / push / reset / checkout / clean / stash`。本文与另两份写入文件之外无改动。文中不含真实书名、私有目录名或日志原文；被引用日志均在仓库外，本文只引用其脱敏摘要与时间戳。
