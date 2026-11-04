# EPUB 替换前置失败回滚修复（backup 就绪判据）

- **执行者**：窗口 B
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`
- **日期**：2026-09-24

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 前置失败（backup 未建立）误删旧 target | **已修**（`backupReady` 阶段判据） |
| `renameTo` 失败 → `copyTo` 备份中途失败 → 残缺 backup 覆盖好文件 | **已修**（复制后长度校验 + 未就绪不动 target） |
| 取消时仍执行必要回滚 | **保留**（未在 catch 顶部重抛 `CancellationException`） |
| 定向测试 | **5 → 8 条，全绿**；红相已验证（恰好 2 条新用例失败） |
| 相邻回归 `ShelfImporterTest` | **45/45 全绿，零回归** |

---

## 1. 基线记录

### 1.1 目标文件（改前 SHA-256）

| 文件 | 改前 | 改后 |
|---|---|---|
| `feature/reader/EpubRepository.kt` | `c98e9a3d90a1a8e53ee91617c63edd71c830869b0d853b1382014e7a11720408` | `3bdd1267764bd5fcfd44995ea40546c750e0ae90e74ede6fcbb012f18a5d78a9` |
| `feature/reader/EpubRepositoryReplaceTest.kt` | `4767ae8c1dcf13667fd7982719ad34296be8732041ace4f6363743c1fce38ee0` | `86914015ed496ba6029d4ca2d6f5883a863ff4186e448e059d0c729bf7878aba` |

（改前值与上一轮夜间审查报告 §0.1 记录一致 —— 即**本轮从审查所指的同一状态起步**。）

### 1.2 本轮改动文件

| 文件 | 性质 |
|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt` | 修改（仅 `replaceStoredEpub`） |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt` | 追加 3 条用例 |
| `docs/coordination/legacy-stages/2026-09/2026-09-24-epub-prebackup-rollback-fix.md` | 本报告 |

**未触碰**：`ShelfImporter.kt`（SHA-256 仍为上一轮的 `e1605b64…`）、`PageTurner.kt`、
`PendingTurnQueue*.kt`（属并发窗口，mtime 12:59 早于本轮）、总账、其他窗口文件。
**无 SEAM REQUEST** —— 改动全部落在授权文件内。

---

## 2. 缺陷与修复

### 2.1 缺陷一：backup 未建立时无条件删 target

改前 `:136-145`：

```kotlin
} catch (error: Throwable) {
    memoryCache.remove(stored.id)
    target.delete()                       // ← 无条件
    if (backup.exists()) { …还原… }        // ← 此时为 false，什么都不做
    throw error
}
```

`backup` 只在 `:98` 的 `target.exists() && !target.renameTo(backup)` 分支里才被创建。
因此**在它之前**失败（`staging` 复制、`:95` 长度校验）时：
`target` 仍是完好的旧副本、`backup` 不存在 ⇒ `target.delete()` 删掉用户**仅存**的正文，
且没有任何东西可以还原。

### 2.2 缺陷二：残缺 backup 覆盖完好旧文件

同一 `:98-101`：`renameTo` 失败后退回 `target.copyTo(backup, overwrite = true)`，
**复制完不校验**就 `target.delete()`。若 `copyTo` 中途失败（磁盘满等），
`backup` 是残缺文件而 `target` 已被删；catch 再把残缺 backup 搬成 target ⇒ 好文件被坏文件替换。
更糟的是 `copyTo` 抛出的第二个异常会**覆盖原始错误**（见 §4 红相证据）。

### 2.3 修复：以 `backupReady` 而非 `backup.exists()` 作为判据

`EpubRepository.kt:100-125`（生产代码，本轮全部改动集中于此 + catch）：

```kotlin
var backupReady = false      // 旧副本已**完整**落进 backup
var promoteStarted = false   // 已开始改写 target
try {
    …staging 复制 + 长度校验…
    if (target.exists()) {
        if (target.renameTo(backup)) {
            backupReady = true
        } else {
            target.copyTo(backup, overwrite = true)
            require(backup.length() == target.length()) { "备份旧副本时文件不完整" }  // ← 新增校验
            backupReady = true
            target.delete()                                                          // ← 校验通过才删
        }
    }
    promoteStarted = true
    …staging → target…
```

catch（`:155-176`）：

```kotlin
if (backupReady) {
    if (promoteStarted) target.delete()
    if (backup.exists()) { …renameTo/copyTo 还原… }
} else {
    if (backup.exists()) backup.delete()   // 清掉残缺备份，避免下次被误用
    if (promoteStarted) target.delete()
}
throw error
```

各阶段的回滚结果：

| 失败发生在 | `backupReady` | `promoteStarted` | 回滚动作 | 旧 target |
|---|---|---|---|---|
| staging 复制 / 长度校验 | false | false | 删残缺 backup（若有） | **逐字节存活** ✅ |
| 备份复制中途失败 | false | false | 删残缺 backup | **逐字节存活** ✅ |
| 已替换、DAO 失败 / 解析失败 / 哈希失败 | true | true | 删新 target + 从 backup 还原 | **逐字节还原** ✅ |
| 原本无 target、替换后失败 | false | true | 删本次落位的新副本 | 不适用（原本就没有） |

**关于取消**：catch 内**未**在顶部直接重抛 `CancellationException`。取消会走与其它异常**完全相同**的
阶段化回滚后再原样抛出。理由：若为「不吞取消」而提前 `throw e` 跳过回滚，已改写的 `target`
会带着半截新副本留下，反而破坏原本完好的旧副本。用例
`replaceStoredEpub still rolls back the file when the caller is cancelled` 锁住该行为。

---

## 3. 测试设计

### 3.1 新增 3 条用例（`EpubRepositoryReplaceTest.kt`）

| # | 用例 | 失败注入点（**均在 `EpubParser.parse` 的 try 之外注入不到的位置**） | 断言 |
|---|---|---|---|
| 1 | `replaceStoredEpub keeps existing target when staging copy fails before backup` | 把 `staging` 路径先建成**同名非空目录** ⇒ `staging.delete()` 失效、`staging.outputStream()` 抛 `FileNotFoundException`。发生在 backup 建立**之前** | 异常向上抛；旧 target **逐字节存活**；`bookDao.update/upsert` **零调用**；无 backup 残留 |
| 2 | `replaceStoredEpub keeps existing target when backup copy fails` | 把 `backup` 路径先建成**同名非空目录** ⇒ `renameTo` 失败、`copyTo` 立即抛错，backup 处于「存在但不可用」 | 异常向上抛；**不得用不可用的 backup 覆盖完好的旧 target**（逐字节比对）；数据库零调用 |
| 3 | `replaceStoredEpub still rolls back the file when the caller is cancelled` | `bookDao.update` 抛 `CancellationException`（发生在 target 已被替换**之后**） | 取消向上传播；旧 target **逐字节还原**；内存缓存无残留 |

**为何满足「不能只在 `EpubParser.parse` 的 try 外注入失败」**：`parse` 的调用（`:79`）在 `try`
**之前**，从那里注入根本进不了 catch。上述 3 条分别把失败打在
`try` 内部的**前置阶段**（用例 1）、**备份阶段**（用例 2）、**替换后阶段**（用例 3），
覆盖了三条不同的代码路径，且用例 1/2 是旧实现会丢数据的真实窗口。

用例 1/2 共用 `seedExistingTarget()` 预置「已存在且完好」的旧内部副本 —— 这是缺陷成立的必要前提
（旧实现正是把它删掉）。

### 3.2 既有 5 条未改

`updates books content_hash … for small files`、`clears content_hash to null for large files over 32MiB`、
`rejects corrupt file …`、`restores original target bytes from backup when post-swap update fails`、
`computeFileHash fails on invalid small file instead of returning null` —— 一行未改，全部继续通过。

---

## 4. 红相 / 绿相证据

### 4.1 红相（受控临时回退本人文件 → 跑 → 立即从 `/tmp` 还原）

回退内容：把 `try`/`catch` 还原为改前的旧逻辑（含 `var backupReady/promoteStarted` 声明一并移除）。

```
$ ./gradlew.bat :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" --no-parallel
```

- **退出码 `1`**（`BUILD FAILED in 1m 17s`）
- **`8 tests completed, 2 failed`** —— **恰好**是本轮新增的前两条：

| 失败用例 | 原始失败信息（XML `<failure>`） | 说明 |
|---|---|---|
| `…keeps existing target when staging copy fails before backup` | `java.lang.AssertionError: 旧 target 必须依然存在` | **直接证明旧实现把完好的旧副本删掉了** |
| `…keeps existing target when backup copy fails` | `java.io.FileNotFoundException: …\epub_backup_copy_fail.epub (拒绝访问。)` | 证明旧实现先删 target、再试图用不可用 backup 还原，**且第二个异常覆盖了原始错误** |

第 3 条（取消）在红相中**仍通过** —— 该路径旧实现本来就能还原（backup 已存在）。
它是一条**防回归护栏**，锁住「将来有人为了不吞取消而在 catch 顶部提前重抛」这一改动。

还原后 SHA-256 复核：`3bdd1267764bd5fcfd44995ea40546c750e0ae90e74ede6fcbb012f18a5d78a9`（与修复版一致）。

### 4.2 绿相

```
$ ./gradlew.bat :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
    --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" \
    --no-parallel
```

- **退出码 `0`**（`BUILD SUCCESSFUL in 1m 20s`）
- XML 实测：

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `EpubRepositoryReplaceTest` | **8** | 0 | 0 | 0 |
| `ShelfImporterTest` | **45** | 0 | 0 | 0 |
| **合计** | **53** | **0** | **0** | **0** |

- `EpubRepositoryReplaceTest` 由改前 **5** 条增至 **8** 条（+3）。
- 编译：命令含 `compileDebugKotlin` 与 `compileDebugUnitTestKotlin`，均成功
  （仅有 `ReaderDocumentLoaderProfileTest.kt` 的既有 opt-in 警告，与本轮无关）。

---

## 5. 仍未覆盖的边界

1. **真实 Room 未覆盖。** `bookDao` 为 MockK 替身（`update` 恒返回 1）。测试断言的是
   「`update`/`upsert` 零调用」，**不等于**真实 SQLite 事务语义 —— 本轮路径本身只有单条 DAO 写，
   但真实约束/触发器/并发下的一致性**未验证**。
2. **断电 / SIGKILL 未覆盖。** `renameTo`、`copyTo`、`delete` 之间进程猝死不在可证明范围；
   本轮只覆盖**进程内异常与取消**这一级。特别地，`backupReady = true` 与 `target.delete()`
   之间的猝死窗口仍存在（极窄但非零）。
3. **文件系统语义未在 Android 上验证。** 测试跑在 JVM `TemporaryFolder`（真实 FS 但非 Android 沙箱）。
   Android 上 `renameTo` 的跨目录/权限行为、`filesDir` 配额、Scoped Storage 影响**均未验证**。
4. **用例 1/2 用「同名非空目录」制造失败**，模拟的是 `openOutputStream`/`copyTo` 失败这一类 I/O 错误；
   其它形态（ENOSPC 写到一半、权限中途撤销）**未逐一构造**。
5. **`require(backup.length() == target.length())` 只比对长度**，不比对内容；
   「长度相同但内容损坏」的备份理论上不会被拦下（未构造该用例）。
6. **未跑全量门禁**（按要求）：未跑完整单测、Lint、androidTest、APK 构建。
   `ShelfImporterTest` 是**相邻定向回归**（它同样调用 `replaceStoredEpub`），不构成全量门禁。
7. **未做逐行变异测试**：红相是整块回退旧实现，`backupReady`/`promoteStarted` 的
   `renameTo` 失败回退分支未与其它分支组合穷尽。

---

## 6. 边界声明

- **未运行** ADB / 真机 / 模拟器；未接触用户书库。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939…`。
- **未修改** `ShelfImporter.kt`、`PageTurner.kt`、`PendingTurnQueue*.kt`、总账或其它窗口文件；
  本轮 `find -mmin -40` 命中的其余条目 mtime 均早于本轮（12:59 / 13:12），属并发窗口。
- 报告内**不含**真实书名、私人目录名或私有路径（夹具用 `new.epub` / `旧版内部副本` 等中性名）。
- 唯一越界相关动作：为验证红相，**临时**回退并**立即还原**了本人所有的 `EpubRepository.kt`
  （还原后 SHA-256 已复核一致），未触碰他人文件。

完成后停止。
