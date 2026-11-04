# S04 仓储层 AbortedMidway 定向补测

- **日期**：2026-09-27
- **工作区**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **范围**：仅 `android/app/src/test/java/com/creationreadingassistant/data/repository/BookResidueSafeCleanupTest.kt` + 本报告
- **结论**：**已实现并跑通**（定向 JVM：`BUILD SUCCESSFUL`，该类 31 个用例、0 failures、0 errors）
- **合规性**：未改生产源码、共享配置或其他测试文件；未 commit / push / reset / checkout；未碰模拟器、真实手机或书库

---

## 0. 修订记录

| 轮次 | 内容 |
|---|---|
| 初版 | 新增 2 个 AbortedMidway 用例，跑通（31 / 0 / 0 / 0） |
| **本次（定向审阅修正）** | 审阅指出：两个用例的 `unmockFilePhase()` 写在最后一行，而本类只有 `@Before`、无 `@After`——若仓储调用或断言抛错，顶层静态 mock 会遗留并污染同类/同进程后续用例。**已改为 try/finally 保证解除**；断言与真实预检接缝不变。定向重跑通过（31 / 0 / 0 / 0，退出码 0）。 |

本次仅修改 `BookResidueSafeCleanupTest.kt` 中这两个用例的控制流，**未**改生产代码、其他测试、类级生命周期（未新增 `@After`）或共享配置。

---

## 1. 背景与目标

`BookRepository.clearBookCache` 的四种结果里，`AbortedMidway`（**部分执行**：预检通过、文件删除开始后遇新残留/位置不可判定而中止，残留与 DB 指针均保留）此前**没有仓储层的直接用例**；既有覆盖只到 `deleteTreeSparingResidue`（删除器自身）与 `OfflineBooksResidueClear`（清空离线的计数/文案）。

本次目标：补仓储层映射的直接用例——

1. 预检为 `Clean`；
2. 文件阶段中途遇新残留而返回 `AbortedMidway`；
3. 断言残留与未触及文件保留、且 `books` / `book_contents` / `book_files` 的 upsert **均为 0**。

明确**不**用「只测 `deleteTreeSparingResidue`」来冒充仓储映射覆盖。

---

## 2. 生产代码事实（阅读所得）

`BookRepository.kt` 相关位置：

| 位置 | 内容 |
|---|---|
| `clearBookCache`（L281） | 预检 → `deleteBookContentFiles` → 若结果为 `Aborted` 则 `return AbortedMidway(cause)`（L293-295）；否则写三张表（L298-301） |
| `deleteBookContentFiles`（L330，`private`） | 按四处位置清理；`clearDirSparingResidue` 折叠 `ABORTED_RESIDUE`→`MidwayAbortCause.RESIDUE_APPEARED`、`ABORTED_UNREADABLE`→`LOCATION_UNREADABLE` |
| `scanBookContentResidue`（L409，`private`） | 预检三处位置；发现残留→`Found`，不可列举→`Unsafe` |
| `deleteTreeSparingResidue`（L1039，`internal`，顶层函数） | 删除器：遇残留/不可列举立即中止 |
| `MidwayAbortCause`（L1003） | `RESIDUE_APPEARED`、`LOCATION_UNREADABLE` |

**关键约束**：预检（`scanBookContentResidue`）与文件阶段（`deleteBookContentFiles`）扫描**完全相同的三处位置**
（`books/<id>/`、`books/epub/<id>.<marker>`、`books/epub/<id>/`），两次扫描之间**没有可挂测试钩的中间点**。

---

## 3. 为什么必须注入，以及接缝选择

### 3.1 真实文件系统下无法确定性触发

「预检判定 Clean」与「同一位置已存在真实残留文件」在真实文件系统上**互斥**：若残留文件真实存在，预检就会命中 `Found` 而直接返回 `RefusedResidue`，根本走不到文件阶段。

`AbortedMidway` 在生产中只能经 **TOCTOU 窗口**触发（预检后、删除中新出现残留，或目录在中途变得不可列举）。用真实文件 + 时序去撞这个窗口**不是确定性用例**，会 flaky。

> 既有 `clear all counts partial residue abort ...` 用例之所以能确定性模拟 TOCTOU，是因为它 mock 了 `File.name`（`returnsMany listOf("innocent.txt", "x.backup")`）——
> 即**预检与删除阶段读取的是 mock 的两次不同返回值**。该手法在 `OfflineBooksResidueClear` 路径可行（它把 `File` 直接传给删除器）。
> 但 `clearBookCache` 路径下，`scanBookContentResidue` 与 `deleteBookContentFiles` 都各自用 `File(context.filesDir, "books/$id")` **重新构造** File 对象，
> 测试无法通过替换传入的 File 实例来让两个阶段看到不同状态。

### 3.2 选定的接缝：桩掉 `deleteTreeSparingResidue`

```kotlin
private fun abortFilePhase(outcome: TreeDeletionOutcome): TreeDeletionResult {
    val result = TreeDeletionResult(outcome, deletedAny = true)
    mockkStatic(::deleteTreeSparingResidue)
    every { deleteTreeSparingResidue(any()) } returns result
    return result
}
```

理由：

- 它是**文件阶段唯一的删除入口**，也是 `ContentFileDeletionOutcome.Aborted` 的唯一来源；
- 位于文件阶段**内部**，因此预检仍然走真实代码路径（真实构造 `books/<id>/`、真实 `listFiles`、真实判定 `Clean`）——
  用例确实证明了「预检 Clean 后才进入文件阶段」；
- 断言的是**仓储层映射**：`Aborted` → `AbortedMidway(cause)`，以及 `if (fileOutcome is Aborted) return` 对三张表 upsert 的整段拦截。

**这不是测删除器**：删除器自身的保留语义由本文件既有的 `sparing deleter ...` 用例覆盖（含「中止后不得继续删除后续子树」）。
本例的 `assertTrue(dir.exists())` 依赖的是被桩掉的删除器**不执行删除**，故它验证的是「仓储未继续推进到写库」，而非删除器的保留语义——这一点在用例注释中已写明，避免误读为删除器覆盖。

---

## 4. 新增用例

| 用例 | 断言 |
|---|---|
| `clearBookCache aborts midway when residue appears after clean precheck` | 结果为 `AbortedMidway(RESIDUE_APPEARED)`；目录与其中正文原样保留；三张表 upsert 均 0 |
| `clearBookCache aborts midway when content location becomes unreadable` | 结果为 `AbortedMidway(LOCATION_UNREADABLE)`；目录保留；三张表 upsert 均 0 |

两个用例都用 **try/finally** 包裹，保证 `unmockFilePhase()` 一定执行：

```kotlin
try {
    abortFilePhase(TreeDeletionOutcome.ABORTED_RESIDUE)
    val outcome = repository.clearBookCache("mid1")
    assertEquals(BookCacheClearOutcome.AbortedMidway(MidwayAbortCause.RESIDUE_APPEARED), outcome)
    ...
} finally {
    unmockFilePhase()
}
```

> **为什么必须 try/finally**：`mockkStatic(::deleteTreeSparingResidue)` 是**顶层静态 mock**，
> 而本类只有 `@Before`、**没有 `@After`**。初版把 `unmockFilePhase()` 写在用例最后一行——
> 若仓储调用或任一断言抛错，该静态 mock 就会遗留下来，污染同类后续用例乃至同进程其他测试。
> 改为 finally 后，无论用例成功、断言失败还是抛异常，解除都必然执行。
> 本次**未**新增 `@After`（只按审阅要求改这两个用例，不改动类级生命周期，避免影响他人既有结构）。

辅助：

- `abortFilePhase(outcome)` / `unmockFilePhase()` —— 注入与解除
- `verifyDbUntouched()` —— 三张表 `coVerify(exactly = 0) { …upsert(any()) }`

新增 import：`io.mockk.mockkStatic`、`io.mockk.unmockkStatic`（均为测试内使用）。

> 未引入任何对生产代码的改动，也未新增测试专用的生产可见性（如把 `private` 放宽为 `internal`）。

---

## 5. 运行记录

### 5.1 命令

```bash
cd D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android
export GRADLE_USER_HOME=D:/develop/env/gradle
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.data.repository.BookResidueSafeCleanupTest" \
  --init-script E:/creation-reading-assistant/codex-s04-build-20260925/redirect-build.gradle \
  --offline \
  > E:/creation-reading-assistant/codex-s04-build-20260925/test-aborted-midway.log 2>&1
```

- **退出码**：`0`（首次实现运行 `0`；try/finally 修正后重跑同样 `0`）
- `--init-script` 用的是既有 E 盘 `redirect-build.gradle`（把 `buildDirectory` 指向
  `E:/creation-reading-assistant/codex-s04-build-20260925/project-builds`），未新建或修改重定向配置
- `--offline`：复用既有依赖缓存，避免网络
- 只跑了该类，**未做全量构建**

### 5.2 产物（E 盘）

| 产物 | 路径 |
|---|---|
| 日志 | `E:\creation-reading-assistant\codex-s04-build-20260925\test-aborted-midway.log` |
| 测试 XML | `E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\test-results\testDebugUnitTest\TEST-com.creationreadingassistant.data.repository.BookResidueSafeCleanupTest.xml` |

### 5.3 XML 结果（修正后重跑）

```
tests="31" skipped="0" failures="0" errors="0"
```

两个用例均出现在 XML 中：

- `clearBookCache aborts midway when residue appears after clean precheck`
- `clearBookCache aborts midway when content location becomes unreadable`

`<failure>` / `<error>` 标签计数：**0**。XML 时间戳 `09-27 02:51`（本次重跑产物，非上一轮残留）。

### 5.4 一次失败的尝试（如实记录）

首次运行我把 `GRADLE_USER_HOME` 误设为 `E:/.../codex-s04-build-20260925/gradle-home`（该目录只有空的 `wrapper/dists`），
导致 Gradle 重新下载发行包与 `kotlin-compiler-embeddable`，最终因 `github.com:443` 连接超时 **BUILD FAILED**。
改用环境里既有的 `D:/develop/env/gradle` 并加 `--offline` 后成功。该失败与被测代码无关，属命令配置错误。

---

## 6. 覆盖边界（不夸大）

- **已覆盖**：`clearBookCache` 中 `ContentFileDeletionOutcome.Aborted → AbortedMidway` 的仓储层映射，
  以及该分支对 `books` / `book_contents` / `book_files` upsert 的拦截，两个 cause 各一例。
- **未覆盖 / 依赖注入**：
  - 「预检后新出现残留」的**真实 TOCTOU 时序**未由真实文件系统证明（见 §3.1，属确定性不可得）；
    本例用桩让文件阶段返回中止结果，证明的是**仓储拿到 Aborted 之后的行为**。
  - 中止时「已删除部分不恢复」在**仓储层**未验证（该语义属删除器，由既有 `sparing deleter ...` 用例覆盖；
    本例删除器被桩掉，故不作此断言）。
  - `AbortedMidway` 在 UI 层的呈现（`ShelfBookActions` L175 起）未纳入本次测试。
- **JVM 单元测试**：不涉及真机、模拟器、真实书库。

---

## 7. 文件清单

| 文件 | 状态 |
|---|---|
| `android/app/src/test/java/com/creationreadingassistant/data/repository/BookResidueSafeCleanupTest.kt` | 修改（新增 2 个用例 + 2 个辅助函数 + 2 个 import；本次修正：两用例改用 try/finally 解除静态 mock） |
| `docs/coordination/legacy-stages/2026-09/2026-09-27-s04-aborted-midway-test.md` | 新增（本报告，已含修正记录） |

生产源码、其他测试文件、共享配置：**均未改动**。工作区中他人的既有改动（git 中大量 `M` 条目）原样保留。

本轮改动落在文件 L323-345（RESIDUE_APPEARED 用例）与 L350-363（LOCATION_UNREADABLE 用例）；
`@Before`（L50）未动，未新增 `@After`。git 中我的文件仍为未跟踪（`??`）状态，未 commit。
