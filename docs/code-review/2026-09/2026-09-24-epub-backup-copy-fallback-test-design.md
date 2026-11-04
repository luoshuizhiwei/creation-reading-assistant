# EPUB `renameTo(backup)` 失败后 `copyTo(backup)` 回退分支 · 测试接缝设计（2026-09-24）

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920` · **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（staged 0）
- **本轮只写本文件**。未改源码、测试、总账或其他窗口文件；**未运行 Gradle / ADB / 真机**；未执行 `reset / checkout / clean / stash / stage / commit / push`。
- **本报告全部为静态阅读与设计推理，不含任何已执行结果。** 凡标注「未验证」者，均不得当作已验证结论引用。

## 0. 快照与行号漂移警告（先读）

**权威快照：`2026-09-24 16:25:56`（+0800），哈希与锚点在同一条命令内读取（原子读取）**

| 文件 | `sha256` | mtime |
|---|---|---|
| `feature/reader/EpubRepository.kt` | `c5495fc5021205ae87906e8adfb579794ec0dac24633d6ab624145355a0cb097` | 16:22:58 |
| `feature/reader/EpubRepositoryReplaceTest.kt` | `be6d243ce3c3c15c881cfcd95bafbe7f6c4d06ddae9666e406ec8bb54b6e8745` | 16:25:21 |

**⚠️ 本仓库有窗口在实时改写这两个文件**（我在 16:22–16:26 期间观察到 `EpubRepository.kt` 与 `EpubRepositoryReplaceTest.kt` 各变化多次，行号漂移）。因此：

1. 本报告的**行号绑定上述哈希**；若行号对不上，请以**引用的代码/断言原文**为准（我在每处都附了原文）。
2. 我在 16:25:43–16:25:56 的原子核对中确认：**生产码构造函数仍是 4 个参数、全仓无 `FileOps`/`fileOps`、测试仍是 10 条用例**。⇒ 本报告的设计前提（§0 结论、§3 接缝必要性、§5 覆盖缺口）**不因这次漂移而改变**。
3. **与本次任务无关但值得登记的旁证**：`EpubRepository.kt:98-102` 的守卫文案在这轮被改了一次（`16:22:58`），把原先的「原有文件与备份均已保留」改成更准确的
   「**旧正文若仍存在，则未被本次操作修改，残留备份已保留**」——理由是 `target` 缺失时并不存在「原有文件」，旧说法会让用户误以为旧正文还在原处。该改动**不在本报告范围**（属入口守卫文案，非回退分支），此处仅作为「文件确实在动」的证据。

---

## 1. 结论摘要

| 项 | 判定 |
|---|---|
| 回退分支（`renameTo` 失败 → `copyTo`）的**正确性** | **静态看是正确的**：任何在 `copyTo` / `require` 阶段的失败都落在 `backupReady == false`，旧 `target` 原样保留。**我没有找到能在这里丢数据的代码路径。** |
| 回退分支的**覆盖** | **完全未覆盖**。现测试只能走到「`copyTo` 因同名目录**立即抛错、一字节未写**」这一种形态；`require(backup.length() == target.length())`（`:140`）**从未被执行过** |
| 回退分支的**可达性** | 在生产中**真实可达**（跨挂载点、FS 拒绝 rename、并发句柄等）。它不是死代码 —— 一段真实可达却零覆盖的回滚逻辑 |
| 本次任务要求的「确定性注入」 | **必须新增接缝**。现有注入手法（同名非空目录）只能制造「`copyTo` 抛错」，**无法**制造「部分复制」；且它已被入口守卫（`:94`）挡住 |
| 推荐接缝 | 注入一个 `FileOps`，只路由 `renameTo` 与 `copyTo` 两个调用。**注意 Dagger 约束**，见 §3.3 |

**一句话**：这段回退分支不是「有 bug 待修」，而是「有逻辑零覆盖、且其中一条子分支（长度校验）在测试里根本走不到」。本轮给出把它变成可确定性覆盖的最小接缝。

---

## 2. 该分支的状态机（行号基于快照 `c5495fc5…`）

### 2.1 变量

| 变量 | 位置 | 含义 |
|---|---|---|
| `target` | `:87` | 旧正文的正式路径 `filesDir/books/epub/<id>.epub` |
| `staging` | `:89` | 本次新副本的暂存 `<id>.repairing` |
| `backup` | `:90` | 旧正文的备份 `<id>.backup` |
| `backupReady` | `:124` | **「旧副本已安全落进 backup」** —— 只有它为 true 才允许删/改 `target` |
| `promoteStarted` | `:125` | **「已开始改写 target」** |

### 2.2 备份建立段（本报告焦点）

```
:133  if (target.exists()) {
:134      if (target.renameTo(backup)) {
:135          backupReady = true                    ← 快路径：同目录 rename
:136      } else {
:139          target.copyTo(backup, overwrite = true)   ← 回退：逐字节复制
:140          require(backup.length() == target.length()) { "备份旧副本时文件不完整" }
:141          backupReady = true
:142          target.delete()                   ← 只有校验通过才允许删旧文件
:143      }
:144  }
:145  promoteStarted = true
```

**四个状态，以及每个状态下的不变量：**

| 状态 | 进入点 | `backup` | `target` | `backupReady` | 不变量 |
|---|---|---|---|---|---|
| **S0** 未开始 | `:133` 前 | 不存在（守卫 `:94` 已保证） | 完好旧副本 | false | target 是唯一副本 |
| **S1** rename 成功 | `:135` 后 | **完好旧副本** | **不存在** | **true** | backup 是唯一副本 |
| **S2** 回退复制中 | `:139`–`:140` 之间 | 可能是**残缺**文件 | 完好旧副本 | false | **target 仍是唯一完好副本** |
| **S3** 回退完成 | `:141`–`:142` 后 | 完好旧副本 | 不存在 | true | 同 S1 |
| **S4** 提升中 | `:145` 后 | 完好旧副本 | 正在被改写 | true | backup 是回退依据 |

### 2.3 各失败点落到的回滚分支

```
catch (:180 附近)
  if (backupReady) {                       (:187)
      if (promoteStarted) target.delete()  (:188)
      if (backup.exists()) {               (:189)
          if (!backup.renameTo(target)) {  (:190)
              backup.copyTo(target, overwrite = true)  (:191)
              backup.delete()              (:192)
          }
      }
  } else {                                 (:195)
      if (backup.exists()) backup.delete() (:196)   ← 清掉残缺 backup
      if (promoteStarted) target.delete()  (:197)   ← 此处 promoteStarted 必为 false
  }
  throw error                              (:201)
finally { staging.delete() }               (:203)
```

| 失败发生在 | `backupReady` | `promoteStarted` | `:196` 是否删 backup | `:197` 是否删 target | **旧 target 结局** |
|---|---|---|---|---|---|
| **F1** `:139 copyTo` **中途抛错** | false | false | 是（删残缺 backup） | **否** | ✅ **原样保留** |
| **F2** `:139 copyTo` **正常返回但内容被截断** | false（异常在 `:140` 抛出） | false | 是 | **否** | ✅ **原样保留** |
| **F3** `:134 renameTo` 失败 + `:139 copyTo` **成功** | true（`:141`） | 视后续而定 | — | — | ✅ 已走 S3，旧副本在 backup 里 |
| **F4** `:140` 之后、`:142` 之前进程被杀 | false（内存态丢失） | false | 下次由入口守卫接管 | — | ✅ target 尚在 |
| **F5** `:190` 起回滚自身失败 | true | — | — | — | ⚠️ 见 §6.4（既有开放项） |

**关键结构事实**：**F1/F2 两条路径下 `target` 从不被删除**，因为 `:142 target.delete()` 只在 `:140 require` 通过之后才执行。这是回退分支最重要的一条不变量，也是新测试要钉死的东西。

---

## 3. 现有测试到底覆盖了什么

`EpubRepositoryReplaceTest.kt` 共 **10 条用例**（快照 `be6d243c…`）。与本节相关的：

| 用例（行号 @ `be6d243c…`） | 注入手法 | 实际走到哪里 | 是否进入 `:136-143` 回退分支 |
|---|---|---|---|
| `...keeps existing target when staging copy fails before backup`（`:457`） | `staging` 预置成**同名非空目录** | 在 `staging.outputStream()`（`:126` 附近）就抛 `FileNotFoundException` | ❌ **否**（更早） |
| `...refuses and preserves target when a backup directory residue exists`（`:499`） | `backup` 预置成**同名非空目录** | 在 `:94` 入口守卫被拒 | ❌ **否**（守卫拦下） |
| `...refuses residue and never promotes backup when target is missing`（`:602`） | `backup` = 持有写句柄的普通文件 | 在 `:94` 入口守卫被拒 | ❌ **否**（守卫拦下） |
| `...succeeds and leaves no residue when no backup exists`（`:668`） | 无残留 | 走快路径 `:134 renameTo` **成功** | ❌ **否**（`else` 分支未进） |
| `...restores original target bytes from backup when post-swap update fails`（`:340`） | `bookDao.update` 抛错 | 走快路径 → 失败后 `:187 backupReady==true` 回滚 | ❌ **否**（回滚侧，非建立侧） |
| `...still rolls back the file when the caller is cancelled`（`:563`） | 同上 + 取消 | 同 F5 的回滚侧 | ❌ **否** |
| 其余 4 条（hash 语义 / 大文件 / 损坏源 / `computeFileHash`） | — | 与本分支无关 | ❌ **否** |

### 3.1 因此：**没有任何一条用例进入过 `:136-143`**

- `:136 else` 从未进入 ⇒ `:139 target.copyTo(backup, …)` 从未执行。
- `:140 require(backup.length() == target.length())` **从未执行**。
- `:141 backupReady = true`（回退路径）从未执行。
- `:142 target.delete()` 从未执行。

### 3.2 哪些断言「看起来在测回退」，其实没有

| 断言（位置 @ `be6d243c…`） | 为什么没有真正进入回退分支 |
|---|---|
| `:335-336`「staging 文件必须不存在」「backup 文件必须不存在」 | 该用例失败在 staging 阶段，**backup 从未被创建**。`:196` 的 `if (backup.exists())` 求值为 false，分支未走。**这两条是恒真的**（同类问题在既有评审 `2026-09-24-epub-prebackup-fix-independent-review.md` §2 第 3 点已指出）。 |
| `:385-386`「staging / backup 必须不存在」 | 同上（post-swap 用例的收尾断言，走的是 `:187` 分支，非建立侧）。 |
| `:484`「backup 不得存在」 | 前置失败时 backup 从未建立，恒真。 |
| `:543`「残留 backup 必须仍然存在」 | 这是**入口守卫**的合同（`:94-102`），与 `:136-143` 无关。 |
| `:646-648`「长度校验分支未被本用例执行（已知缺口）」 | **这是一条诚实的负断言**，作者主动声明该分支未被覆盖 —— 它恰恰**证明**了 §3.1 的结论，而不是覆盖它。 |
| 类级文档「`renameTo` 失败退回复制」与长度校验两条分支不会被执行 | 类级文档**已经如实登记了这个缺口**。本报告不是「发现一个被隐瞒的缺口」，而是**为已登记的缺口给出可落地的接缝设计**。 |

### 3.3 顺带澄清：旧实现曾「覆盖」过它，但覆盖的是另一种形态

`refuses and preserves target when a backup directory residue exists` 的注释（`:490-496` 附近）说明：该用例**此前**用「同名非空目录」触发过 `renameTo` 失败 / `copyTo` 抛错，从而**声称**覆盖了「备份复制中途失败」。但那个形态下：

- `target.renameTo(backup)` 失败（因为 `backup` 是目录）✅
- `target.copyTo(backup, overwrite = true)` 在 `File.copyTo` 内部「目标存在则先 `delete()`」时失败 ⇒ **立即抛 `FileNotFoundException`，一字节未写**

⇒ 它证明的是「**不可用的 backup 不得覆盖好文件**」，**不是**「**残缺**的 backup 不得覆盖好文件」。这两者不是一回事：前者靠 `backupReady=false`，后者靠 `:140` 的长度校验。**后者从未被任何用例执行。**

---

## 4. 最小、可确定性注入的接缝设计

### 4.1 为什么现有手法不够

| 想要注入的失败 | 现有手法能否做到 | 说明 |
|---|---|---|
| `renameTo(backup)` 失败 | ⚠️ 能（同名非空目录） | 但**已被入口守卫 `:94` 拦住**，测试到不了 `:134` |
| `copyTo(backup)` **立即抛错、零字节** | ⚠️ 能（同上） | 同样被守卫拦住；即使绕过，也只能测「不可用 backup」 |
| `copyTo(backup)` **写了一半再抛错** | ❌ **不能** | 需要控制写入过程 |
| `copyTo(backup)` **正常返回但内容截断** | ❌ **不能** | 需要让「写入成功」与「内容完整」解耦 |

⇒ 要覆盖 F1/F2，**必须**有一个能控制 `copyTo` 行为的接缝。

### 4.2 接缝形状（推荐）

在 `com.creationreadingassistant.feature.reader` 包内新增一个**极小**的接口，只暴露本分支需要的两个操作：

```kotlin
internal interface FileOps {
    /** 与 java.io.File.renameTo 同义；返回是否成功。 */
    fun renameTo(source: File, dest: File): Boolean
    /** 与 kotlin.io.copyTo(overwrite = true) 同义。 */
    fun copyTo(source: File, dest: File)
}

internal class DefaultFileOps : FileOps {
    override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
    override fun copyTo(source: File, dest: File) {
        source.copyTo(dest, overwrite = true)
    }
}
```

**只路由 `:134` 与 `:139` 两处**（最小改动）：

```kotlin
// :134
if (fileOps.renameTo(target, backup)) {
    backupReady = true
} else {
    // :139
    fileOps.copyTo(target, backup)
    require(backup.length() == target.length()) { "备份旧副本时文件不完整" }
    backupReady = true
    target.delete()
}
```

> 可选对称扩展：把回滚侧的 `:190 backup.renameTo(target)` 与 `:191 backup.copyTo(target, …)` 也一并路由。
> 那样可额外覆盖「**回滚自身复制失败**」（F5 / §6.4 的既有开放项）。**本轮不强制**，保持最小。

### 4.3 ⚠️ Dagger 约束（必须处理，否则编译期失败）

`EpubRepository` 是 `@Singleton class ... @Inject constructor(...)`（`:26-31`）。**Dagger 不会使用 Kotlin 的默认参数值** —— 若只给新参数加默认值而不提供绑定，Hilt 会在编译期报
「cannot be provided without an @Provides-annotated method」。

**推荐做法（零 Hilt 模块改动）**：让 `FileOps` 的实现自身可注入：

```kotlin
@Singleton
internal class DefaultFileOps @Inject constructor() : FileOps
```

然后 `EpubRepository` 的构造函数新增一个普通参数 `private val fileOps: FileOps`。Dagger 能自行构造 `DefaultFileOps`（它有 `@Inject constructor`），**无需新增或修改任何 Hilt 模块**。

**已核实**：`EpubRepository(` 的显式构造点在**全仓库只有一处** —— 测试 `EpubRepositoryReplaceTest.kt:90`（生产侧由 Hilt 注入）。测试用的是**具名参数**，新增参数后该处只需补一个 `fileOps = fakeFileOps` 即可，不会波及其它文件。

### 4.4 测试侧假实现

```kotlin
private class FakeFileOps : FileOps {
    var renameResult: Boolean = true
    var renameCalls = 0
    var copyCalls = 0
    /** 复制行为：默认真实复制；可替换为「写一半抛错」或「写一部分后正常返回」。 */
    var copyBehavior: (File, File) -> Unit = { s, d -> s.copyTo(d, overwrite = true) }

    override fun renameTo(source: File, dest: File): Boolean {
        renameCalls++
        return if (renameResult) source.renameTo(dest) else false
    }
    override fun copyTo(source: File, dest: File) {
        copyCalls++
        copyBehavior(source, dest)
    }
}
```

三个注入形态（全部确定性，无时序依赖、无 FS 竞态）：

| 形态 | `renameResult` | `copyBehavior` |
|---|---|---|
| 回退成功（G1） | `false` | 真实 `copyTo` |
| **中途抛错**（R1） | `false` | 写前 N 字节 → `throw IOException("模拟复制中断")` |
| **截断返回**（R2） | `false` | 只写 `source.length() - 1` 字节，**正常返回** |

`copyCalls` / `renameCalls` 是**「确实进入了回退分支」的证明**，用于防止测试因更早的失败而**假绿**（见 §6.3）。

---

## 5. 生产代码改动边界

| # | 文件 | 改动 | 行数 |
|---|---|---|---|
| 1 | **新增** `feature/reader/FileOps.kt`（或并入 `EpubRepository.kt` 顶层） | `internal interface FileOps` + `@Singleton internal class DefaultFileOps @Inject constructor()` | ~15 行 |
| 2 | `feature/reader/EpubRepository.kt` | 构造函数新增 `private val fileOps: FileOps` 参数 | +1 行（`:26-31`） |
| 3 | `feature/reader/EpubRepository.kt` | `:134` `target.renameTo(backup)` → `fileOps.renameTo(target, backup)` | 1 行 |
| 4 | `feature/reader/EpubRepository.kt` | `:139` `target.copyTo(backup, overwrite = true)` → `fileOps.copyTo(target, backup)` | 1 行 |
| 5 | `EpubRepositoryReplaceTest.kt` | 构造调用补 `fileOps = fakeFileOps`；新增 3 条用例 | 见 §6 |

**明确不改**（这些是正确行为，接缝不得改变其语义）：

- `:94-102` 入口守卫、`:124-125` 两个标记、`:140` 的 `require`、`:142 target.delete()` 的位置、`:180-201` 回滚分流、`:202-204 finally staging.delete()`。
- `:190-191` 回滚侧（除非采用 §4.2 的可选扩展）。
- **不得**把 `require` 换成「尽力而为的清理」—— `:140` 是「残缺 backup 不得被采纳」的唯一闸门。

**行为等价性**：`DefaultFileOps.copyTo` 必须与 `File.copyTo(dest, overwrite = true)` **逐字同义**（含「目标存在则先删」语义）。`renameTo` 必须直接转发。否则接缝本身会引入行为漂移 —— 这也是 §6 的 G1 要钉住的东西。

---

## 6. 要新增的红绿测试

以下 3 条**全部需要 §4 的接缝**才能写。每条给出：构造、断言、以及**它在当前代码上的颜色**。

### 6.1 G1（绿，新覆盖）· `renameTo` 失败 + `copyTo` 成功 → 替换正常完成

**为什么需要**：这是回退分支的**happy path**，生产真实可达，**零覆盖**。

| 项 | 内容 |
|---|---|
| 构造 | 预置完好旧 `target`；`fake.renameResult = false`；`copyBehavior` = 真实复制 |
| 断言 1 | 替换成功返回，`result.id == bookId` |
| 断言 2 | `fake.renameCalls == 1`、`fake.copyCalls == 1`（**证明确实走了回退**） |
| 断言 3 | `target` 内容 == **新**副本字节；`backup` 不存在；`staging` 不存在 |
| 断言 4 | `bookDao.update` 恰好 1 次 |
| 颜色 | **当前代码无法写**（无接缝）。接缝落地后应为**绿**。 |

### 6.2 R1（红 → 绿）· `copyTo` 写一半后抛错 → 旧 target 逐字节存活

**为什么需要**：F1 形态。这是「备份复制中途失败」的**真实**形态（ENOSPC / 被中断）。

| 项 | 内容 |
|---|---|
| 构造 | 预置完好旧 `target`（记 `oldBytes`）；`fake.renameResult = false`；`copyBehavior` = 写 `oldBytes.size / 2` 字节后 `throw IOException("模拟复制中断")` |
| 断言 1 | 抛出的是注入的 `IOException`（**不是** `AssertionError`） |
| 断言 2 | `fake.copyCalls == 1`（确实进入回退） |
| 断言 3 | **`target.exists()` 且 `target.readBytes()` == `oldBytes`**（逐字节，最关键） |
| 断言 4 | **`backup` 不存在**（`:196` 清掉了残缺备份，避免下次被误用） |
| 断言 5 | `staging` 不存在（`:203`） |
| 断言 6 | `bookDao.update` / `upsert` 各 0 次；`repository.cached(bookId) == null` |
| 颜色 | **当前代码 RED**：现有手法（同名目录）下 `copyTo` 零字节即抛，`backup` 是**目录**而非「写了一半的文件」——断言 4「backup 不存在」在目录形态下也不成立（`File.delete()` 对非空目录返回 false）。接缝落地后转**绿**。 |

### 6.3 R2（红 → 绿）· `copyTo` 返回但内容截断 → 长度校验必须拦下

**为什么需要**：F2 形态，**唯一**能执行 `:140` 的路径。这正是类级文档与用例 `:646-648` 主动登记的那条缺口。

| 项 | 内容 |
|---|---|
| 构造 | 预置完好旧 `target`；`fake.renameResult = false`；`copyBehavior` = 只写 `oldBytes.size - 1` 字节，**正常返回** |
| 断言 1 | 抛出 `IllegalArgumentException`，**且 message 含 `"备份旧副本时文件不完整"`**（证明 `:140` 被执行） |
| 断言 2 | `fake.copyCalls == 1` |
| 断言 3 | **`target.readBytes()` == `oldBytes`**（截断的 backup **未**被采纳） |
| 断言 4 | `backup` 不存在；`staging` 不存在 |
| 断言 5 | `bookDao.update` / `upsert` 各 0 次 |
| 颜色 | **当前代码 RED**：无法让 `copyTo` 「返回但截断」；现有手法下 `:140` 永不执行（用例 `:646-648` 已用负断言记录了这一点）。接缝落地后转**绿**。 |

### 6.4 防假绿（必须加，否则 R1/R2 可能因更早的失败而「意外通过」）

每条新用例都**必须**包含「确实进入了目标分支」的正向证据，否则断言 3 可能因为失败发生在 staging 阶段而**恒真**——这正是 §3.2 指出的旧断言问题：

- `assertTrue(fake.renameCalls >= 1)` —— 证明走到 `:134`
- `assertTrue(fake.copyCalls == 1)` —— 证明走到 `:139`
- R2 额外：`assertTrue(caught.message!!.contains("备份旧副本时文件不完整"))` —— 证明 `:140` 执行
- 反例护栏：`assertTrue(caught !is AssertionError)`，避免 `require` 的 `IllegalArgumentException` 与 JUnit 断言混淆

**建议**：把这套「进入分支」的证明做成 `FakeFileOps` 的调用计数 + 断言，而不是靠消息文本猜测。

---

## 7. 如何保证失败后旧 target 与 backup 不被误删

### 7.1 旧 target：靠 `:140` 的**位置**

不变量：**`target` 的唯一删除点 `:142` 位于 `:140 require` 之后**。
⇒ 只要 `copyTo` 抛错（F1）或 `require` 失败（F2），`target.delete()` **不可能**执行，`target` 必然逐字节完好。

**测试如何钉死**：R1/R2 的断言 3 用 `assertArrayEquals(oldBytes, target.readBytes())` —— 逐字节比较，而非仅 `exists()`。这一点很关键：`:197 if (promoteStarted) target.delete()` 在本分支恒为 false（`promoteStarted` 在 `:145` 才置 true），但**测试不应依赖读者去推这一步**，而应直接用字节断言把结果钉住。

### 7.2 backup：在 F1/F2 下**应当**被删除

澄清一个容易反向理解的点：F1/F2 时 `backup` 是**残缺文件**，而 `target` 是完好副本 ⇒ **`backup` 不是唯一副本**，`:196 backup.delete()` 删除它是**正确**的（避免下次启动被误当成可用备份；`:184-186` 的注释正是此意）。

⇒ 因此 R1/R2 对 backup 的断言应当是 **`backup` 不存在**，而**不是**「backup 必须保留」。
「backup 必须保留」只适用于**它承载唯一副本**的场景 —— 即 `backupReady == true`（`:187-194`），已由 `...restores original target bytes from backup when post-swap update fails`（`:340`）覆盖。

### 7.3 三重防线（现状静态成立，需测试固化）

| 防线 | 位置 | 作用 |
|---|---|---|
| ① `backupReady` 门 | `:187` / `:195` | 未就绪 ⇒ **绝不**碰 target |
| ② `require` 长度校验 | `:140` | 残缺 backup 不得被采纳为「已就绪」 |
| ③ `target.delete()` 的位置 | `:142` | 只在 ② 通过后才删旧文件 |

新测试要做的就是**把 ①②③ 从「读代码可推」变成「跑测试可证」**。

### 7.4 本轮**不**声称解决的边界

1. **回滚自身失败（F5）**：`:190` 的 `backup.renameTo(target)` 失败后 `:191 backup.copyTo(target)` 若再抛错，异常会**顶替**原始 `error`（`:201` 的 `throw error` 不再执行）。数据可恢复（backup 仍在盘上）但目标处于半程。**既有评审 `2026-09-24-epub-prebackup-fix-independent-review.md` §3 B4 已登记，本轮确认仍在。** 若采纳 §4.2 的可选对称扩展，可为之补一条测试。
2. **`:140` 之后、`:142` 之前进程被杀（F4）**：此时 `target` 尚在、`backup` 残缺、内存标记丢失。下次运行由入口守卫（`:94`）接管 —— 但守卫**只拒绝、不恢复**，会把用户卡住（见 `2026-09-24-android-residue-cleanup-audit.md` §3.1）。属崩溃残留状态机的独立设计面。
3. **无 `fsync`**：`copyTo` 完成后目录项未必落盘，掉电时「已返回但未持久化」可能出现。设计稿 `docs/coordination/legacy-stages/2026-09/2026-09-24-txt-backup-crash-recovery-design.md` §7-3 已列此硬边界。
4. **长度校验的强度**：`:140` 只比长度，「长度相同内容不同」不会被拦（既有评审 §3 B3 已登记）。R2 只能证明「截断被拦」，**不能**证明「内容不符被拦」。

---

## 8. 未验证 / 硬边界（不得当作已验证）

1. **本轮未运行任何测试**，未跑 Gradle。§6 的三条用例是**设计**，不是**结果**；`R1`/`R2` 的「当前为红」是**静态推理**（依据：`:140` 在现有注入手法下不可达，且用例 `:646-648` 的负断言已记录该缺口），**未实测**。
2. **`FileOps` 接缝的 Hilt 可行性未编译验证**。§4.3 的结论基于「Dagger 不采用 Kotlin 默认参数」这一通用规则与「`EpubRepository(` 全仓仅一处显式构造」的 grep 结果，**未实际编译**。
3. **回退分支在生产中的触发频率未取证**。§1 称其「真实可达」（跨挂载点 / FS 拒绝 rename），这是**基于文件系统语义的推断**，未在任何设备上观测到实际发生。
4. **`DefaultFileOps` 与 `File.copyTo(overwrite = true)` 的语义等价性未验证**（G1 是设计来钉它的，但本轮未跑）。
5. **并发**：同一本书被两个入口同时替换时，本分支无 per-book 互斥（设计稿 §7-6 已列）。

---

## 9. 边界声明

- 只新增本文件。未修改 `EpubRepository.kt`、`EpubRepositoryReplaceTest.kt`、任何其它源码/测试/总账/其他窗口文件。
- **未运行** Gradle / ADB / 真机 / 模拟器；未接触用户书库或设备设置。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0。
- 本报告**不含**真实书名、私人目录名或日志原文；示例一律用 `<id>` / `<name>` / `bookId` 占位。
- 结论绑定 §0 的两个哈希（`c5495fc5…` / `be6d243c…`）与 16:25:56 快照；该仓库有并发窗口在**实时改写**这两个文件（我在 16:22–16:26 观察到多次变化与行号漂移），
  16:26 之后的变化不在覆盖范围内。**若行号对不上，请以每处引用的代码/断言原文为准。**
