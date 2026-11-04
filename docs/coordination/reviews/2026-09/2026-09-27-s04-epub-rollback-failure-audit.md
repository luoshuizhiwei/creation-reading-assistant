# S04 EPUB 替换「回滚本身失败」路径审计（只读）

- **日期**：2026-09-27
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **范围**：只读审计。仅读 `EpubRepository.kt`、`FileOps.kt`、`EpubRepositoryReplaceTest.kt` 与旧报告
- **性质**：**静态代码审计**，未执行任何测试、未启动构建、未碰设备/模拟器/真实书库，未改任何源码或测试，未 commit / push / reset / checkout
- **结论**：旧报告 §E4 / §4-#4 的「恢复段异常会顶替原异常」**在当前源码下依然成立**（风险未消除）；另有“恢复复制正常返回但内容不完整时无校验即删备份”的**条件风险**。后者尚无自然触发或设备复现证据，不能记为已发生的数据损失。

> **2026-09-27 Codex 复核修订**：B 写完初稿后 Claude 桌面端连续两次 Server error，未能完成交回的措辞修正。以下对 DB 终态、条件性截断、现有测试覆盖及 FileOps 最小接缝的表述由 Codex 按当前源码直接修订；本报告仍是静态审计，不是运行验收。

---

## 1. 审计对象与关键行号

`android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt`
（`replaceStoredEpub`，L84-229）：

| 行 | 内容 |
|---|---|
| L96-106 | `.backup` 入口守卫：存在即拒绝并抛 `IllegalStateException` |
| L119-125 | `requireFreshSource = true` 解析 → `validatedFile`（`<id>.importing`） |
| L142-143 | `var backupReady = false` / `var promoteStarted = false` |
| L145-150 | staging 复制 + 长度 `require` |
| L152-161 | 建备份：`fileOps.renameTo(target, backup)`；失败则 `fileOps.copyTo` + `require(长度相等)`，然后 `target.delete()` |
| L163-167 | `promoteStarted = true`；`staging.renameTo(target)`，失败则 `copyTo` 覆盖 |
| L170-193 | 重新解析 target、算 `content_hash`、`bookDao.update`/`upsert` |
| L195-196 | 成功收尾：`backup.delete()`、`validatedFile.delete()` |
| **L198-219** | **`catch (error: Throwable)`：回滚（本文审计对象）** |
| L205-212 | `backupReady` 为真：`target.delete()` → `backup.renameTo(target)`，失败则 `backup.copyTo(target, overwrite=true)` → `backup.delete()` |
| L213-216 | `backupReady` 为假：清残缺 backup、删已改写的 target |
| **L219** | **`throw error`** |
| L220-228 | `finally`：`staging.delete()`、`runCatching { validatedFile.delete() }` |

`FileOps.kt`（接缝）L22-26 明确声明其范围：

> `- renameTo ← target.renameTo(backup)` / `- copyTo ← target.copyTo(backup, overwrite = true)`
> **「`staging` 的复制与提升、回滚侧的 `backup.renameTo(target)` / `backup.copyTo(target, …)` 都不经过这里」**

**这是本审计的关键事实**：回滚侧用的是 `File` 的直接方法（L208、L209），**没有任何注入接缝**。

---

## 2. 两条精确失败时序

以下 `target` = `books/epub/<id>.epub`、`backup` = `<id>.backup`、
`staging` = `<id>.repairing`、`validatedFile` = `<id>.importing`（新鲜来源副本）。

两条时序共用前置：入口守卫通过（无 `.backup`）→ 新鲜解析成功 → staging 复制成功 →
`fileOps.renameTo(target, backup)` 返回 true ⇒ **`backupReady = true`**（旧副本已安全落进 backup）
→ `promoteStarted = true` → staging 提升为 target（**target 此刻已是新字节**）。

触发异常取 `bookDao.update` 抛 `RuntimeException`（L193）——与既有用例
`restores original target bytes from backup when post-swap update fails`（测试 L347）同一注入点，
是已知可达的失败点。

### 时序 A：`backup.renameTo(target)` 失败 → `backup.copyTo(target)` 抛错

| 步骤 | 位置 | 结果 |
|---|---|---|
| 1 | L206 `target.delete()` | target 被删除；**旧字节此时只存在于 backup** |
| 2 | L207 `backup.exists()` | true |
| 3 | L208 `backup.renameTo(target)` | 返回 false（无接缝，需真实失败：跨设备/权限/目标被占用等） |
| 4 | L209 `backup.copyTo(target, overwrite = true)` | **抛 `IOException`** |
| 5 | L210 `backup.delete()` | **未执行** |
| 6 | L219 `throw error` | **永远到不了** —— 第 4 步的异常直接从 catch 块传播出去 |
| 7 | L220-228 `finally` | `staging.delete()`；`runCatching { validatedFile.delete() }` |

**最终状态**

| 对象 | 状态 | 判定 |
|---|---|---|
| `target` | 被删除后由 `copyTo` 写入部分旧字节 ⇒ **截断的旧副本**（或 0 字节/不存在，取决于 OS 缓冲与中断点） | 推测（未执行） |
| `backup` | **仍然存在且含完整旧字节**（L210 未执行） | 已证明（代码路径） |
| `staging` | `finally` 尝试删除，返回值未检查 | 已证明调用删除；实际结果未验证 |
| `validatedFile` | `finally` 在 `runCatching` 中尝试删除，失败被吞 | 已证明调用删除；实际结果未验证 |
| **DB 指针** | 在现有测试的 mock 注入点，`bookDao.update` 抛错前不执行写入，`upsert` 不会被调用。生产数据库若在写入后或提交边界抛错，最终状态须另行验证 | 测试注入点可判定；生产终态未证实 |
| **调用方所见异常** | `copyTo` 的 `IOException`，**不是**原始的 `RuntimeException("模拟数据库写失败")`；原始原因丢失 | 已证明（代码路径：`throw error` 不可达） |

> 判定依据：L219 的 `throw error` 位于 L209 之后且**无** `try/catch`/`runCatching` 包裹 L206-212（全文件
> `replaceStoredEpub` 内唯一的 `runCatching` 在 L227，只包 `validatedFile.delete()`）。
> 故第 4 步抛出后必然跳过第 6 步。**这是静态可证的，不是推测。**

### 时序 B（条件场景）：`backup.copyTo(target)` 只复制一部分后**正常返回**（不抛错）

此返回形态可由测试接缝确定性注入，以检验回滚代码缺少校验的后果；当前尚未证明 Kotlin `File.copyTo` 在普通低存储故障下会这样返回，也未验证真机自然可达性或频率。

| 步骤 | 位置 | 结果 |
|---|---|---|
| 1 | L206 `target.delete()` | target 被删除 |
| 2 | L208 `backup.renameTo(target)` | false |
| 3 | L209 `backup.copyTo(...)` | **写出部分旧字节后正常返回**（无异常） |
| 4 | L210 `backup.delete()` | **执行** —— 完整旧副本被删除 |
| 5 | L219 `throw error` | 正常抛出原始异常 |

**最终状态**

| 对象 | 状态 | 判定 |
|---|---|---|
| `target` | 在上述假设成立时，留下不完整的旧字节 | 条件推演，未执行 |
| `backup` | 在上述假设成立且 `backup.delete()` 成功时被删除；是否还有外部副本未知 | 条件推演，未执行 |
| `staging` / `validatedFile` | `finally` 尝试删除，结果未检查或被 `runCatching` 吞掉 | 已证明调用删除；实际结果未验证 |
| DB 指针 | 测试 mock 抛错前无写入；生产终态不能由此静态推断 | 测试注入点可判定；生产未证实 |
| 调用方所见异常 | 若恢复复制及删除均正常返回，则抛出原始 `error`；文件损坏后果依赖上述假设 | 条件推演 |

> **根因**：正向建备份路径有 `require(backup.length() == target.length())`（L158）作闸门，
> 而**回滚恢复路径（L208-210）没有任何长度或内容校验** —— `copyTo` 一经返回就无条件 `backup.delete()`。
> 因此在“复制不完整但正常返回”且备份删除成功的条件下，调用方只拿到原始异常，看不到恢复文件也已受损；该条件尚未在生产文件系统复现。

### 时序 A 与 B 的严重性比较

- **A**：原因被掩盖（运维/排障受损），但**完整旧字节仍在 backup 中**，可由 `.backup` 守卫或人工找回。
- **B**：若“不完整复制却正常返回”及备份删除均发生，则完整旧备份可能被主动删除；这是需要防护的条件性数据损失路径，尚非实测事故。

⇒ **B 比 A 更严重**。旧报告只描述了 A 类（「顶替原始异常」），未识别 B 类。

---

## 3. 现有测试是否真的进入这些分支

`EpubRepositoryReplaceTest.kt` 的**自有声明**（文件头 L54-57）已明确登记缺口：

> 「**本片之后仍然失去的分支覆盖（不得声称已覆盖）**：
> 「长度相同但内容不同」的损坏备份不会被 `require` 拦下（只比长度）；
> **回滚自身失败（`backup.renameTo(target)` 失败后 `backup.copyTo(target)` 再抛错）**；
> `require` 通过之后、`target.delete()` 之前进程被杀（无 fsync）。三者均未覆盖。」

逐一核对：

| 用例（测试行号） | 覆盖内容 | 是否进入回滚失败分支 |
|---|---|---|
| L347 `restores original target bytes from backup when post-swap update fails` | 后置 DB 失败 → 从 backup 完整恢复 | **否**。该用例成功恢复，走的是 `backup.renameTo(target)` **成功**分支（同目录 rename 在临时目录必然成功），从未进入 L208 的 `!renameTo` 分支 |
| L492 `falls back to copy when rename fails and completes the swap` | 正向回退复制 happy path | 否（正向，非回滚） |
| L540 `keeps target intact when fallback copy throws mid-write` | 正向回退复制抛错 | 否（正向；此时 `backupReady=false`，走 L213-216 分支） |
| L607 `rejects truncated fallback backup via length check` | 正向 L158 长度闸门 | 否（正向） |
| L662 `keeps existing target when staging copy fails before backup` | `backupReady=false` 前置失败 | 否 |

**结论**：既有后置失败用例进入 L208 并验证 `backup.renameTo(target)` **成功**恢复；没有用例进入它返回 false 后的 L209-210 回退复制分支。

原因有二，均已核实：

1. **无接缝**：回滚侧 `backup.renameTo(target)` / `backup.copyTo(target)` 是 `File` 直接调用，
   `FileOps` 明确不路由它们（FileOps.kt L25-26）。
2. **`FakeFileOps.renameResult = false` 影响不到回滚**：它只作用于 L152 的**正向** `fileOps.renameTo`；
   回滚用的是 `backup.renameTo(target)`（真文件方法），在临时目录同目录内**必然成功**。

---

## 4. 确定性注入所需的最小 seam

**结论：以当前调用路径，不改动生产代码无法经既有 `FileOps` 确定性注入回滚失败；但接口本身已有通用方法，无需新增 public API。** 理由：

- 无法让 `backup.renameTo(target)` 在受控条件下返回 false：临时目录内同目录 rename 几乎必然成功；
  强行把 `target` 预置成非空目录会先影响 L206 的 `target.delete()`，改变的是另一条分支。
- 即便 rename 失败，**也无法让 `backup.copyTo(target)` 「写出一半再抛错」或「少写若干字节后正常返回」**——
  这正是 `FileOps.kt` L18-20 当初为正向路径引入接缝的同一理由。

**最小 seam（需改生产调用点，故仅作建议、本次未实施）**：

`FileOps` 已有通用 `renameTo(source, dest)` / `copyTo(source, dest)`；把回滚侧 L208-209 的两次直接 `File` 调用改为调用既有 `fileOps` 即可。测试中的 `FakeFileOps` 按 `source == backup`、目的路径或调用次序只注入回滚失败；默认实现仍委派给原有文件操作。无需新增接口方法或改变 public API，但应同步更新 `FileOps.kt` 中“回滚侧不经过接缝”的过时注释。这是生产代码改动，本次未实施。

在 seam 就位前，这两条时序**只能用静态审计描述**，无法写成确定性回归用例。

---

## 5. 旧报告结论的重新判定

`docs/qa/2026-09/2026-09-24-overnight-s04-epub-stage-q-review.md`：

| 旧结论 | 当前判定 |
|---|---|
| §4-#4「恢复段异常会吞原始 error」（P2，L402-404） | **依然成立**。L206-212 无 `runCatching`，L209 抛出即跳过 L219 的 `throw error`。`backupReady`/`promoteStarted` 的引入**没有**改变这一点——它们只修正了「何时可以删 target」，未给恢复段加异常隔离 |
| §E4「前置 `catch (CancellationException) { throw e }` 不可采用」 | 依然成立（L217-218 注释保留该语义），不在本次范围 |
| 「恢复段用 `runCatching` 包裹、失败时仍抛原 `error`」 | 仍未实施 |

**但旧报告已过时的部分**：它基于「catch 里无条件 `target.delete()`」的旧实现。
当前 `backupReady` / `promoteStarted` 分流**已经修掉了**「用残缺 backup 覆盖完好 target」
与「backup 未就绪时误删唯一旧副本」这两个原始缺陷（L205-216），
且新增的 `.backup` 入口守卫（L96-106）杜绝了对残留的破坏性删除。
⇒ **「回滚会毁掉唯一副本」这一类风险已修复；「回滚自身失败」这一类（A/B 两条）仍在。**

---

## 6. 下一片建议（限一个最小修复 + 必要测试）

**建议修复**：收紧 `EpubRepository` L206-212 的恢复段，使其不会「顶替原异常」也不会「静默留下截断副本」：

1. 用 `runCatching` 包裹 L206-212 的恢复动作，失败时**仍抛出原始 `error`**
   （把恢复段异常作为 `suppressed` 挂上，保留排障信息）；
2. 在 `backup.delete()`（L210）**之前**校验恢复结果（如 `target.length() == backup.length()`），
   校验失败则**保留 backup**、不删除，并让原始 `error` 带上「恢复未验证」标记；
3. 保留 backup 时不要清掉它 —— 完整旧字节必须留在磁盘上供后续处理。

此修复只改 `catch` 内这一个块，不动成功路径、不动 `FileOps` 契约、不动 public API。

**必要测试（依赖 §4 的 seam）**：

1. `backup.renameTo(target)` 返回 false 且经现有 `fileOps.copyTo` 注入的恢复复制抛错 ⇒
   断言：抛出的是**原始**异常（非 copyTo 异常）、`backup` **仍在且含完整旧字节**、DB 零写入。
2. `backup.renameTo(target)` 返回 false 且通过既有 `fileOps.copyTo` 的测试桩模拟只写部分后正常返回 ⇒
   断言：`backup` **未被删除**、抛出原始异常、DB 零写入（即：不再出现「唯一副本被删」）。

**若 seam 不被接受**，这两条只能停留在静态审计，无法形成回归防线——
建议在报告中把 A/B 两条登记为**已知未覆盖风险**，而非声称已防护。

---

## 7. 已证明 vs 推测

**已证明（静态阅读可直接判定）**

- `FileOps` 接缝不覆盖回滚侧（FileOps.kt L25-26 自述 + `EpubRepository` L208-209 为 `File` 直接调用）。
- L206-212 无任何 `try`/`runCatching` 包裹；L219 `throw error` 在其之后 ⇒ 恢复段抛错必然顶替原异常。
- 回滚恢复路径**没有**长度/内容校验，L210 `backup.delete()` 在 `copyTo` 正常返回后无条件执行。
- 现有测试覆盖 L208 的成功 rename；无一覆盖 L208 返回 false 后的 L209-210 回退复制分支，测试文件头 L54-57 已登记该缺口。
- `FakeFileOps.renameResult` 只影响正向 L152，不影响回滚 L208。

**推测 / 未执行（不得以「已验证」表述）**

- 时序 A 中 `target` 最终是「部分旧字节」还是「0 字节/不存在」——取决于 OS 缓冲与中断时机，**未实测**。
- 时序 B 的“不完整复制却正常返回”能由测试桩模拟，但 Kotlin `File.copyTo` 在生产中是否自然出现该形态、以及长度校验是否能捕获所有内容损坏，均未验证。
- 真实设备上 `backup.renameTo(target)` 返回 false 的实际触发条件（跨卷、权限、SELinux 等）——**未验证**。
- 生产数据库在异常边界的最终状态及 UI 如何呈现可能受损的 target，均**未验证**。
- `File.delete()` 的布尔结果在现有回滚与 `finally` 中未检查；报告中的“删除”均须理解为尝试删除，实际文件终态需由测试或设备取证确认。

---

## 8. 剩余真机边界

- 本审计全部为 **JVM 静态分析**，未跑任何测试、未启动构建（按要求不占用共享构建）、只读回读了既有代码与报告。
- **`backup.renameTo(target)` 在真实 Android 上返回 false 的概率与条件未验证**；
  若在生产中它几乎不失败，则时序 A/B 的实际触发频率可能很低——但**一旦发生即丢数据（B 类）**，
  且当前没有任何防护或告警。
- Android 与 JVM 临时目录的文件系统语义并不等价；本轮没有证据估计 `renameTo` 失败或“复制不完整却正常返回”在真机上的概率，也不能把低存储直接推导为后一种返回形态。
- 结论**不可**替代真机验证；建议在真机低存储/跨卷场景补充一次专项验证后再定级。
