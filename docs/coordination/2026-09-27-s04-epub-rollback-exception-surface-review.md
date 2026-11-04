# S04 EPUB 替换回滚保护 · 异常面独立审查

- **日期**：2026-09-27
- **审查角色**：S04 独立代码审查（只读审查，不修改生产代码与测试）
- **工作区**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（已核实，与任务要求一致）
- **审查对象**：`docs/coordination/legacy-stages/2026-09/2026-09-27-s04-epub-rollback-safety-fix.md`（下称「修复报告」）、`EpubRepository.kt`、`FileOps.kt`、`EpubRepositoryReplaceTest.kt` 及既有定向测试 XML
- **约束执行情况**：生产代码、测试文件零修改；未执行 commit/push/reset/checkout；未跑全量构建；未重跑 `:app:testDebugUnitTest`（避免覆盖修复报告 §4.4 引用的 XML 证据，改用哈希核对）；未触碰真机与书库。全部验证工件在 E 盘（§7）。
- **本报告是本轮唯一写入仓库的文件。**

---

## 0. 核心结论（TL;DR）

1. **回滚失败信息不会从异常链上彻底丢失，但调用方可见表面随环境漂移，且当前没有任何生产消费方读取它。**
   协程栈回溯恢复（stack-trace recovery）在**单测环境是激活的**（实测 `testDebugUnitTest` 的 worker JVM 带 `-ea`，而恢复开关默认跟随断言标志）；恢复激活时异常确实被复制、**副本的 suppressed 数组为空**（修复报告 §2.1 观察到的现象机制属实）——但**副本以原实例为 cause**，回滚失败信息仍可经 `caught.cause.suppressed` 取回。生产 Android（断言关闭）恢复机制完全关闭，调用方拿到的是**同一实例**，suppressed 直接可见。
2. **修复报告 §2.1 与测试注释中「caught.suppressed 为空」「identityHashCode 不同」两项实测，在等价复现环境（同版本协程 1.10.2 + runTest + Unconfined + 真实 MockK 1.13.12 + `-ea`）中无法复现**：该配置下捕获的正是原实例。首次运行失败的真正成因无法定论（原始日志已被覆盖）——这是本审查唯一的未决疑点，但不影响测试断言本身的有效性（见 §3.3）。
3. **catch 块内未受 `runCatching` 保护的 `target.delete()` / `backup.delete()` 在 Android 上不可达「顶替主异常」路径**（`File.delete()` 失败返回 false 而非抛出，应用进程无 SecurityManager）；删除返回 false 的真实后果是「自愈恢复」或「残缺备份残留导致下次替换被守卫拒绝」，均不丢数据但**零日志**。
4. **修复报告 §1.1 / `EpubRepository.kt` L238「由调用方/用户提示层决定后续处置」的结论与现有证据不一致**：测试断言只证明生产代码**写入**了 suppressed，不证明调用方**可见**；恢复激活时信息在 cause 链深层（无文档说明）；唯一调用方 `ShelfImporter.repairFileTyped` 只读 `e.message`；恢复失败全程无日志。

---

## 1. 审查点 (1)：DAO 主异常挂 suppressed 后，协程恢复是否使调用方失去回滚失败信息

### 1.1 机制与门控（官方源码，版本与项目一致）

项目协程版本 **1.10.2**（`android/gradle/libs.versions.toml` L14；单测运行时类路径解析收敛到 1.10.2，含 mockk 传递的 1.6.4/1.7.3/1.8.0 请求全部升级，见 §7 依赖解析记录）。

从 Maven Central 下载的 1.10.2 官方 sources（`kotlinx-coroutines-core-jvm-1.10.2-sources.jar`，存 E 盘）确认：

| 事实 | 位置 |
|---|---|
| `RECOVER_STACK_TRACES = DEBUG && systemProp("kotlinx.coroutines.stacktrace.recovery", true)` | `jvmMain/Debug.kt` |
| `DEBUG` 未设置（无 `kotlinx.coroutines.debug` 属性）时取 `"auto"` ⇒ **跟随 JVM 断言开关**：`CoroutineId::class.java.desiredAssertionStatus()` | 同上 |
| 恢复文档明示「Works only on JVM with enabled debug-mode」 | `commonMain/internal/StackTraceRecovery.common.kt` |
| 复制构造：对 `RuntimeException` 优先走 `(String, Throwable)` 反射构造器 `newInstance(e.message, e)` —— **cause 即原实例本身** | `jvmMain/internal/ExceptionsConstructor.kt` |
| **恢复全程无任何 `addSuppressed` 调用**（三个恢复相关源码文件均确认）⇒ 副本不携带 suppressed | `jvmMain/internal/StackTraceRecovery.kt`、`ExceptionsConstructor.kt` |

### 1.2 环境实证（探针 + 等价复现，命令与退出码见 §7）

- **单测环境恢复激活**：init 脚本只读探针（不执行测试）输出
  `S04PROBE enableAssertions=true`、`allJvmArgs=[..., -ea]`、systemProperties 无任何 `kotlinx.coroutines.*` ⇒ 单测 worker 内 `DEBUG=true → RECOVER_STACK_TRACES=true`。
- **生产环境恢复关闭**：Android 应用进程断言默认关闭，仓库无 `kotlinx.coroutines.debug` 属性设置（已 grep `gradle.properties` / 构建脚本 / 源码），release 另有 R8 剥除 ⇒ `RECOVER_STACK_TRACES=false`，`withContext` 边界原样透传异常。
- **等价复现矩阵**（协程 1.10.2，场景 B/F/G/G 用真实 Dispatchers.IO；场景 E 用真实 MockK 1.13.12 复刻 `seedRestoreFailure` 的 DAO 挂起桩注入）：

| 场景 | 配置 | 无 `-ea`（生产同构） | 有 `-ea`（测试 worker 同构） |
|---|---|---|---|
| A2/E | **runTest 外层 + Unconfined 内层**（= 项目单测真实配置，E 含真实 MockK） | 同一实例，suppressed 直接可见 | **仍是同一实例**（不复制） |
| B | `withContext(IO)` 内再 `withContext(IO)`（= 生产 `ShelfImporter.repairFileTyped` L305 包裹 `replaceStoredEpub` L88 的真实路径） | 同一实例 | **复制**：caught.suppressed 为空，**caught.cause === 原实例**，`caught.cause.suppressed` 含回滚失败信息 |
| F/G | runBlocking → IO（含先挂起再抛出） | 同一实例 | 复制（同 B） |
| D | 主异常为 CancellationException（取消路径） | 同一实例 | 同一实例 |
| C | 主异常自带 cause（跨边界） | cause 保留 | 同一实例时 cause 保留 |

（完整输出：`E:\creation-reading-assistant\codex-s04-review-20260927\run1-no-ea.log`、`run2-ea.log`）

### 1.3 对审查问题的直接回答

- **「调用方实际捕获的异常是否失去回滚失败信息？」**——分态：
  - 生产（恢复关闭）：**不失去**。caught === 原实例，`caught.suppressed` 直接可见（RUN-1 全场景实证）。
  - 恢复激活（单测及未来任何「跨拦截器 + 断言开启」的环境）：**直接 suppressed 数组会丢**（副本为空），**但 cause 链不丢**——副本的 cause 就是挂了 suppressed 的原实例（官方构造器语义 + 实测 `causeIsOriginal=true`），`caught.cause.suppressed` 可取回。
- **结论**：**不存在任何实测/源码可达的「信息完全丢失」路径**；真实的风险形态是「表面漂移 + 下沉一层且无文档 + 无消费方」，而非丢失（见 §3、§5）。
- **对修复报告 §2.1 的复核**：其现象描述（副本不带 suppressed）在恢复激活路径上机制属实；但其断言「单测里 caught.suppressed 为空」「identityHashCode 不同」（`EpubRepositoryReplaceTest.kt` L595-599、L1145-1148）**在单测自身配置（runTest + Unconfined）下无法复现**——该配置即使 `-ea` 也不触发复制。首次运行失败的成因存疑（原始日志被覆盖，不可考）。此疑点**不影响测试有效性**，见 §3.3。

---

## 2. 审查点 (2)：catch 中 `target.delete()` 异常与 backup 不存在的处理

逐条判定（行号均为 `EpubRepository.kt` 当前工作区版本）：

| 路径 | 行号 | 判定 |
|---|---|---|
| 恢复段整体在 `runCatching` 内：`renameTo` / `copyTo` / `contentEquals` / `backup.delete()` 任何异常挂 suppressed、不顶替主异常 | L249-258 | **已确认安全**（与测试场景 A/B 一致） |
| catch 内 `memoryCache.remove` | L199 | 不抛（LinkedHashMap），安全 |
| catch 内 `target.delete()`（promoteStarted 时） | L206 | **抛出路径不可达**：`File.delete()` JDK 语义为失败返回 false，唯一理论抛出 `SecurityException` 需要 SecurityManager，Android 应用进程没有 ⇒ 「delete 抛出并顶替主异常（且跳过 L207 恢复与 L214 `throw error`）」为**假设性风险**，非真实可达。**但结构上 L206 位于 catch 入口与恢复/重抛之间且无保护**，是修复不变式 1（原异常不被顶替）的一个未覆盖缺口 |
| catch 内 `target.delete()` 返回 false（删除失败，真实可达） | L206 | 恢复段 `renameTo(backup→target)` 因 dest 存在返回 false → 退回 `copyTo(overwrite=true)` 覆盖 ⇒ **内容自愈**，不静默丢失 |
| catch 内 `backup.delete()`（backupReady=false 分支，残缺备份清理）返回 false | L209 | 残缺 backup 残留 ⇒ 下次替换被入口守卫拒绝（L96-106，可识别错误、不自动采用）⇒ 不丢数据，但该书后续替换被**砖住**，且**无任何日志**记录残留成因（真实可达，低频） |
| finally `staging.delete()` | L216 | 同 L206 分析：抛出不可达；失败残留 `.repairing` 会在下次替换开头 L130 自清 |
| finally `validatedFile.delete()` | L222 | 已在 `runCatching` 内 ✓ |
| `restoreBackupAfterFailedPromotion` 开头 `if (!backup.exists()) return` | L248 | **当前流下不可达**：`backupReady=true` ⇒ catch 时 backup 必在盘上（唯一删除点 L195 在成功路径，且 L195→L197 之间无可抛点）。若未来可达且 promoteStarted=true，L206 已删 target ⇒ **target/backup 双失**。属不变式脆弱点，非现实缺陷 |
| 恢复复制成功但 `backup.delete()` 返回 false | L255 | 内容核对已通过，backup 完整残留 ⇒ 下次替换被守卫拒绝（同 L209 后果，无日志） |

辅助判定：

- `FileOps`（`FileOps.kt` L33-47）：`DefaultFileOps.renameTo` = `File.renameTo`（失败返回 false，不抛）；`copyTo` = `kotlin.io.copyTo(overwrite=true)`（**可抛 IOException**——这正是恢复失败的真实来源，已全部落入 L249 的 `runCatching`）。接口未新增方法、实现与报告 §1.2 描述一致（逐字核对无出入）。
- **范围外观察（登记，不计入本修复）**：`ShelfImporter` 无 Mutex/互斥（grep 证实）；两次并发 `replaceStoredEpub` 可同时通过入口守卫，交错可能删掉对方刚建立的 backup。属既有并发设计问题，与本次改动无关，建议另行登记。

---

## 3. 审查点 (3)：修复报告结论与证据的一致性

### 3.1 一致的部分

- 修复报告 §5「恢复段任何异常不再顶替主异常，主异常类型与消息原样冒泡」——与代码路径（L249-258、L214）及测试断言一致，**成立**。
- 修复报告 §1.2/§6 对 `FileOps.kt` 的改动描述（仅注释、零 API 变化）——与 diff 逐字一致，**成立**。
- §4.4 证据：测试 XML（`tests="20" failures="0" errors="0"`）与 SHA-256 `262e0119…`、日志 SHA-256 `a2945081…` 均与报告引用**一致**（本次独立重算核实）。

### 3.2 不一致 / 过度声称的部分

- **`EpubRepository.kt` L238「把失败原因挂为 suppressed，由调用方/用户提示层决定后续处置」与修复报告 §1.1 同义表述**：证据不支持「调用方可决策」：
  1. 测试断言写在**注入实例**（`daoError.suppressed`，L1149-1153、L1194-1198）上——它证明的是生产代码**写入**成功，不是调用方**可见**性；
  2. 恢复激活环境下调用方拿到的副本 suppressed 为空，信息在 `caught.cause.suppressed`（深层、无任何文档说明该契约）；
  3. 唯一生产调用方 `ShelfImporter.repairFileTyped`（L324-331）只读 `e.message`，**从不读 suppressed**——「决策」没有消费方；
  4. 恢复失败**零日志**（`EpubRepository.kt` 全文无任何日志调用；仓库有现成 `AppLog` 设施但未接入）——生产可诊断性为零。
- **§2.1 的叙述不完整且部分不可复现**：「副本不携带 suppressed」机制属实（§1.1），但 (a) 遗漏「副本以原实例为 cause，信息仍在链上」这半个事实；(b) 其对单测环境生效的前提（恢复激活）与其实测配置（runTest+Unconfined 不触发复制）矛盾，无法复现（§1.2）。由此得出的「断言只能写在注入实例上」**理由**有瑕疵——但**结论**恰好稳健（该位置在复制态=原实例仍在链上、非复制态=原实例即 caught，两态都成立），测试本身不需要改。

### 3.3 对测试的建议性评价（不改测试文件）

`EpubRepositoryReplaceTest.kt` 现有断言在两种恢复态下都通过、都有效，无需回改。若希望未来把「调用方可见性」也钉住，见 §6 建议 4。

---

## 4. 风险登记

| 编号 | 等级 | 内容 |
|---|---|---|
| R2-1 | **中** | 恢复失败对用户与开发者**不可观测**：无日志、无消费方、表面随环境漂移。数据不丢（backup 保留 + 入口守卫），但用户只见 DAO 错误消息，磁盘实态（半恢复 target + 完整 backup）无人告知；下次替换被守卫拒绝时用户才知道「有残留」，却无从得知成因 |
| R3-1 | 低 | catch 内 L206/L209/L216 三处 delete 依赖「`File.delete()` 不抛只返 false」的 JDK 语义；若未来换成可抛 I/O 异常的封装（如 `Files.delete`），L206 抛出将顶替主异常并跳过恢复，破坏修复不变式 1 |
| R3-2 | 低 | L248 早退 + L206 先删 target 的次序构成「双失」理论窗口（当前不可达） |
| R3-3 | 低 | L255/L209 的 `backup.delete()` 静默 false ⇒ 残留砖住后续替换且无日志 |
| 范围外 | 登记 | 并发替换无互斥，守卫存在竞态窗口（既有设计，非本次改动引入） |

---

## 5. 已确认事实清单（含证据行号）

1. `replaceStoredEpub` 整体包在 `withContext(ioDispatcher)`（L88）；生产调用链 `BookOperationsViewModel.reselectFile` → `ShelfImporter.repairFileTyped` → **已在 `withContext(ioDispatcher=Dispatchers.IO)` 内**调用 `reselectEpub` → `replaceStoredEpub`（ShelfImporter.kt L305/L316/L923；CoroutineScopeModule.kt L64）。
2. 恢复段抽为 `restoreBackupAfterFailedPromotion`（L247-259）+ `contentEquals`（L262-278）+ `BUFFER_BYTES`（L477-478）；catch 末尾 `throw error`（L214）；取消路径先回滚后重抛（L212-213）——与修复报告 §1.1 描述一致。
3. 单测 worker JVM `enableAssertions=true`（`-ea`），无 `kotlinx.coroutines.*` 系统属性；单测类路径协程 core 解析为 1.10.2（探针与依赖解析记录，§7）。
4. 协程 1.10.2 恢复机制：门控 `DEBUG(auto→-ea) && 默认true`；复制不带 suppressed；**副本 cause = 原实例**；源码无 `addSuppressed`（§1.1 表）。
5. 复现矩阵（§1.2）：生产同构（无 `-ea`）下全场景同实例、suppressed 直接可见；`-ea` 下 runTest+Unconfined（含真实 MockK）仍同实例，IO 边界配置复制但 cause 链保信息。
6. `EpubRepository.kt`/`FileOps.kt` 无任何日志调用；`AppLog` 设施存在（`feature/log/AppLog.kt`，提供 `w/warn/error`）未接入。
7. 未修改任何仓库生产/测试文件；git 工作区状态与审查开始时一致（仅本报告为新增文件）。

---

## 6. 最小修正与测试建议

1. **（P2，一行级）恢复失败接入日志**：`restoreBackupAfterFailedPromotion` 的 `onFailure` 中，除 `addSuppressed` 外调用 `AppLog.w("EpubRepository", "回滚恢复失败：${restoreFailure.message}，backup 已保留（${backup.name}）")`。不改 API、不改异常语义，直接消除 R2-1 的「零可诊断性」。
2. **（P2，文档对齐）修正 L237-239 与修复报告 §1.1 的契约表述**：suppressed 为尽力而为的附加信息；跨 `withContext` 且协程恢复开启（断言开启/`kotlinx.coroutines.debug` 非关）时，信息位于 `caught.cause.suppressed`；当前调用层的可靠判据是磁盘残留（入口守卫 L96-106），不是异常对象。
3. **（P3，可选加固）** 将 L206/L209/L216 三处 delete 纳入 `runCatching`（或在 catch 注释中写明「依赖 `File.delete()` 失败返回 false 而非抛出」的 JDK 语义前提），把不变式 1 的覆盖范围与恢复段对齐。
4. **（P3，可选测试）新增一条面向调用方的断言**（新用例，不动既有用例）：
   `assertTrue(caught.suppressed.toList() + caught.cause?.suppressed.orEmpty() 包含恢复失败)` —— 在两种恢复态下都成立；
   可再加弱身份断言 `caught === daoError || caught.cause === daoError`（链保持）。**不要**做 `caught === daoError` 强身份断言（未来协程版本/派发器调整会翻转）。不建议为「钉住当前 runTest+Unconfined 不复制」单独断言 `caught.suppressed` 非空。
5. **（P3，可选加固）** L248 早退分支保留亦可，但建议在注释中写明「backupReady=true 时 backup 必在」这一前提及其失效后果（双失），防止未来有人调整 L206 次序。

---

## 7. 验证记录（命令、退出码、E 盘工件）

所有验证工件位于 `E:\creation-reading-assistant\codex-s04-review-20260927\`（本轮新建，均不在仓库内）：

| 工件 | 用途 |
|---|---|
| `VerifySuppressed.kt` / `out\` | 定向验证程序（复刻 catch 语义：主异常挂 suppressed 后重抛）及编译产物；协程 jar 与项目解析版本一致（1.10.2），MockK 1.13.12 与项目一致 |
| `run1-no-ea.log` / `run2-ea.log` | 两种断言开关下的 7 场景异常面输出（A/A2/B/C/D/E/F/G） |
| `ea-probe.gradle` / `ea-probe.log` | 只读 init 脚本探针：`testDebugUnitTest` 的 `enableAssertions=true`、`allJvmArgs=[…, -ea]`；不执行测试 |
| `deps-unit-test.log` | `:app:dependencies --configuration debugUnitTestRuntimeClasspath --offline`：coroutines-core → **1.10.2** |
| `coroutines-sources-1.10.2.jar` / `src\` | 官方 sources（Debug.kt / StackTraceRecovery.kt / ExceptionsConstructor.kt / StackTraceRecovery.common.kt），门控与复制语义出处 |

命令与退出码（均在记录于上述日志的前提下执行）：

```bash
# 1. 编译验证程序（Kotlin 2.0.21 embeddable，Gradle 缓存 jar，-cp 含 stdlib/coroutines/test/mockk/reflect）
#    COMPILE_EXIT=0
# 2. RUN-1（无 -ea，生产同构）  java -cp "out;<cache jars>" VerifySuppressedKt   → EXIT=0
# 3. RUN-2（-ea，测试 worker 同构） java -ea … VerifySuppressedKt               → EXIT=0
# 4. 探针  ./gradlew :app:help --offline --init-script ea-probe.gradle          → 最终 EXIT=0
#    （第一次运行 EXIT=1：探针脚本自身 Groovy 取 jvmArgs 的语法错误，关键输出已打出；修正后复跑 EXIT=0，如实记录）
# 5. 依赖解析  ./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath --offline → EXIT=0
# 6. 证据哈希核对：
#    XML  SHA-256 = 262e011978f33af48810e6f6474662498d30ebf1f5af30934f8dc7701946aacd  ← 与修复报告 §4.4 一致
#    日志 SHA-256 = a29450813934de590733a2e052f0f096b1e44273b504592356b174871159819b  ← 与修复报告 §4.4 一致
#    XML 结果   = tests="20" skipped="0" failures="0" errors="0"
```

**未执行** `:app:testDebugUnitTest`（保护修复报告 §4.4 引用的 XML 证据不被覆盖）；探针与依赖解析均为只读 Gradle 调用（`help`/`dependencies`），不触发编译。

---

## 8. 未验证边界（不夸大）

1. **修复报告首次运行 6 失败的原始现场不可考**（日志被覆盖）。「caught.suppressed 为空」「identityHashCode 不同」在等价复现环境（协程 1.10.2 + runTest + Unconfined + 真实 MockK + `-ea`）中未复现；其成因存在几种可能（当时断言目标不同、worker 环境差异、或观察记录有误），**本审查无法定论**。该疑点不影响现有测试断言的有效性（§3.3）。
2. **真机生产态的 `desiredAssertionStatus()`** 为静态推断（Android 默认关闭断言 + 仓库无开启代码），未在真机实测；R8/release 形态下 `Debug.kt` 的 assumenosideeffects 剥除亦未实测。
3. **并发替换竞态的真实可达性**：仅确认 ShelfImporter 无互斥，未追至 UI 层确认能否实际并发触发两次 repair。
4. **协程未来升级**：若某版本恢复复制开始携带 suppressed，caught.suppressed 将直接可见——现有测试与 §6 建议 4 的断言均不受影响；但本报告基于 1.10.2 的结论需届时复核。
5. 本审查全部为 JVM/静态验证，**不替代真机验证**；真机低存储/跨卷场景下恢复失败时 `.backup` 残留与用户提示，仍按修复报告 §5 的建议另行专项验证。

---

## 9. 结论

回滚保护的**数据安全性目标已达成**（主异常不顶替、完整 backup 只在验证通过后删除、失败保留现场），修复方向与实现质量良好。需要修正的是**可观测性与文档契约**：恢复失败目前对调用方与开发者双重静默，而修复报告与代码注释声称的「由调用方/用户提示层决定后续处置」尚无支撑路径。建议按 §6 的 1、2 两项做最小补强；§6 的 3、4、5 为低成本加固，可并入后续切片。
