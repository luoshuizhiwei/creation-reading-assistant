# TXT 重新选文件安全修复（回滚阶段分流 + computeFileHash 语义对齐）

- **执行者**：窗口 B
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`
- **日期**：2026-09-24

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| TXT 重新选文件：复制失败误删旧副本 | **已修**（阶段分流回滚） |
| computeFileHash：小文件故障被静默写成 `null` | **已修**（两个调用点分别处理） |
| 定向测试 | **39 → 45 条，全绿**；红相已验证 |
| 相邻回归（ShelfViewModelTest / EpubRepositoryReplaceTest） | **25 条全绿，零回归** |
| `EpubRepository.kt` | **未触碰**（SHA-256 与审查前一致） |

---

## 1. 改动文件与精确边界

| 文件 | 边界 |
|---|---|
| `feature/library/ShelfImporter.kt` | ① `reselectPlainText` 的 `try`/`catch`（约 `:937-1020`）：新增两个阶段标记 + 重写 catch 回滚；② `computeFileHash`（`:419-441`）：去掉 `runCatching`/`len<=0 → null`，改为「大文件策略 null / 小文件故障抛出」，并改 `private → internal` 以便定向测试；③ `refreshSourceBaseline` 的 `newContentHash` 计算（`:363-375`）：哈希失败时回落到旧基线 |
| `ui/viewmodel/ShelfImporterTest.kt` | 新增 5 条用例 + 补 `assertArrayEquals`/`assertNull`/`assertThrows`/`File`/`IOException` import |
| `docs/coordination/legacy-stages/2026-09/2026-09-24-txt-reselect-rollback-fix.md` | 本报告 |

**未触碰**：`EpubRepository.kt`、`ShelfViewModel.kt`、DAO/schema/迁移、其他窗口文件。
**无 SEAM REQUEST** —— 全部改动落在授权文件内。

### 基线哈希

| 文件 | 改前 SHA-256 | 改后 SHA-256 |
|---|---|---|
| `ShelfImporter.kt` | `91f700777f9eed8dca886d02ec0bce97c39832a66df4d06524cd69d021eedc02` | `e1605b645eac3809088eb32c90fea1a0190fdd4a46bf1244f2b8ad0f496c1c20` |
| `ShelfImporterTest.kt` | `3a95dc9524e30d63deeb1429f782671f9cffb8c7bea20f6535e6384a665d69c7` | `6615bac204c0749b8529d1dca17d11b1bdb4c46973b271cb7b3aa29cc04f7844` |
| `EpubRepository.kt`（未改） | `c98e9a3d90a1a8e53ee91617c63edd71c830869b0d853b1382014e7a11720408` | 同左（复核一致） |

---

## 2. 缺陷一：`reselectPlainText` 无条件删旧副本

### 2.1 原实现与真实缺陷（改动前，`ShelfImporter.kt:997-1005`）

```kotlin
} catch (error: Throwable) {
    internalFile.delete()          // ← 无条件
    if (backup.exists()) { …还原… }
    throw error
}
```

`backup` 只在 `internalFile.exists()` 为真、且走到「挪走旧副本」那一步时才建立（原 `:943-946`）。
因此存在一个**没有 backup 的失败窗口**：

| 失败发生在 | `internalFile` 的内容 | `backup` | 旧实现后果 |
|---|---|---|---|
| 复制 staging（`openInputStream`/`copyTo` 抛错） | **仍是用户仅存的旧正文** | 不存在 | **`internalFile.delete()` 把唯一正文删掉，且无处可还原** |
| 旧副本已挪进 backup | 已是新副本 | 存在 | 还原正确 |
| 替换后 DAO 失败 | 已是新副本 | 存在 | 还原正确 |

⇒ 第一行是**永久数据丢失**：一次复制失败（磁盘满、provider 抖动、权限撤销）就抹掉用户正文，
且数据库记录仍指向一个已不存在的路径。

### 2.2 修复：按阶段分流

新增两个阶段标记，让 catch 知道「走到哪一步」：

```kotlin
var oldCopyMovedToBackup = false   // 旧副本已挪进 backup（此时 backup 可还原）
var newCopyPromoted = false        // staging 已落位成新的 internalFile
…
if (internalFile.exists()) {
    if (!internalFile.renameTo(backup)) { internalFile.copyTo(backup, overwrite = true); internalFile.delete() }
    oldCopyMovedToBackup = true
}
if (!staging.renameTo(internalFile)) { staging.copyTo(internalFile, overwrite = true); staging.delete() }
newCopyPromoted = true
```

```kotlin
} catch (error: Throwable) {
    if (oldCopyMovedToBackup) {
        if (newCopyPromoted) internalFile.delete()     // 新副本半成品先清掉
        if (backup.exists()) { …renameTo/copyTo 还原旧副本… }
    } else if (newCopyPromoted) {
        internalFile.delete()                          // 原本无副本：只清本次落位的新副本
    }
    // else：旧副本从未被动过 —— 原样保留，什么都不删
    throw error
}
```

三种阶段各自的结果：

| 阶段 | 回滚动作 | 结果 |
|---|---|---|
| 旧副本未动（复制 staging 失败） | **不删任何东西** | 旧正文逐字节保留 ✅ |
| 已备份并替换 | 删新副本 + 从 backup 还原 | 旧正文逐字节还原 ✅ |
| 原本没有副本 | 删本次落位的新副本 | 不留半截状态 ✅ |

**未采用**「在 catch 顶部重抛 `CancellationException` 跳过回滚」的写法 ——
那会让取消场景下的半截文件永远留在磁盘上。本修复对取消同样执行阶段化回滚，语义一致。

**数据库记录**：`reselectPlainText` 的三处写库（`bookDao.upsert` / `bookContentDao.upsert` /
`bookFileDao.upsert`）都在 `bookDao.runInTransaction { }` 内，位于所有文件操作**之后**。
⇒ 文件阶段失败时根本不会走到写库；写库失败时事务整体不生效，且 catch 会把文件还原。
**原记录因此保持不变**（用例 2 断言 `runInTransaction` 抛错后旧副本逐字节还原）。

---

## 3. 缺陷二：`computeFileHash` 把小文件故障写成 `null`

### 3.1 原实现（改动前 `:419-434`）

```kotlin
val len = file.length()
if (len <= 0L || len > MAX_HASH_BYTES) return null      // 空文件与超大文件混为一谈
return runCatching { …md5… }.getOrNull()                 // I/O 故障 → null
```

`null` 的**既定含义**是「文件 >32 MiB，策略上不计算全量哈希」。
把「空文件」和「读取失败」也映射成 `null`，等于对调用方谎报「这是大文件」。

### 3.2 两个调用点都必须处理（不止一个）

`grep` 确认 `computeFileHash` 有两个调用点，**都写内容哈希字段**：

| # | 位置 | 写入目标 | 原后果 |
|---|---|---|---|
| 1 | `reselectPlainText` `:966` → `bookDao.upsert(… content_hash = newBookHash)` | `books.content_hash` | 内部副本读取失败 ⇒ 旧哈希被静默清除，**刷新仍报成功** |
| 2 | `refreshSourceBaseline` `:364` → `sourceIndex.record(… content_hash = newContentHash)` | `library_source_refs.content_hash` | 同上，且「内容是否更新」失去哈希判据 |

### 3.3 修复

**共用函数**改为与 `EpubRepository.computeFileHash` 完全同语义（两处阈值必须一致）：

```kotlin
if (len > MAX_HASH_BYTES) return null                 // 大文件：既定策略
require(len > 0L) { "内部正文副本为空，无法计算内容哈希" }
…无 runCatching，I/O 异常自然抛出…
```

**调用点 1**（`reselectPlainText`）：`computeFileHash` 抛出 ⇒ 进入 §2 的 catch ⇒
阶段化回滚 + 不写库 ⇒ 修复报失败。**旧哈希与旧正文都不动。**

**调用点 2**（`refreshSourceBaseline`）：该函数是 **best-effort** 语义（其 `record` 失败会被
`catch (_: Exception)` 吞掉，见 `:390-393`），不能因为哈希失败就让整个刷新失败。改为**三级回退**：

```kotlin
internalFile?.let { runCatching { computeFileHash(it) }.getOrNull() }   // 本地副本哈希
    ?: contentHashOrNull(uri, newSize…)                                  // 按来源 uri 重算
    ?: ref.contentHash                                                   // 保留旧基线
```

第三级是必需的：一次 I/O 抖动不该把「已知内容哈希」抹成空 —— 那与 S04-3/S04-4
「provider 观测不到有效值时保留旧基线」的既定口径一致（同函数 `:352-353` 对 size/last_modified 就这么做）。

> `contentHashOrNull`（`:1055-`）**未改**：它服务于**导入去重**（`ImportCandidate.contentHash`），
> 语义是「拿不到哈希就不参与强去重」，返回 `null` 是其正确契约，不属本缺陷。

---

## 4. 测试设计与红相证据

### 4.1 新增 5 条用例

| # | 用例 | 覆盖路径 |
|---|---|---|
| 1 | `txt repair keeps existing copy when staging copy fails` | **复制失败 + 旧副本未动 + 无 backup**（本修复的核心回归） |
| 2 | `txt repair restores previous copy when book record write fails` | 已备份并替换 → `runInTransaction` 抛错 → 逐字节还原 |
| 3 | `txt repair replaces copy and writes new content hash on success` | 正常成功：副本换新、哈希为新 MD5、临时文件清理 |
| 4 | `txt repair removes promoted copy when there was no previous copy and write fails` | 原本无副本 → 失败后不留半截副本 |
| 5 | `computeFileHash returns null only for oversized files and throws on real failures` | 大文件 null / 合法小文件 MD5 / 0 字节抛 `IllegalArgumentException` / 读取失败抛 `IOException` |

另新增第 6 条（基线哈希保护）：

| 6 | `refresh keeps previous baseline hash when local copy hashing fails` | 本地副本为 0 字节（损坏）+ 来源流不可读 ⇒ 必须保留旧基线哈希 |

> 说明：§3.3 的调用点 1 由用例 1/2/4 间接覆盖（哈希失败与写库失败走同一个 catch）；
> 调用点 2 由用例 6 直接覆盖。**两个调用点都有定向断言**，未只修一个。

### 4.2 红相（确定性复现，非推断）

对**我自己的文件**做受控临时回退 → 只跑 `ShelfImporterTest` → **立即从 `/tmp` 备份还原**：

| 回退内容 | 结果 | 命令退出码 |
|---|---|---|
| catch 还原为旧的无条件 `internalFile.delete()` | `txt repair keeps existing copy when staging copy fails` **FAILED**；45 tests, 1 failed | `1` |
| `computeFileHash` 还原为旧 `runCatching{}.getOrNull()` | `computeFileHash returns null only for oversized files…` **FAILED**；45 tests, 1 failed | `1` |

两次回退均**只命中一条**新用例，其余 44 条照常通过 ⇒ 新用例是**可证伪的定向回归**，
不是空转断言。还原后 SHA-256 与修复版一致（`e1605b64…`），已复核。

### 4.3 绿相与相邻回归

```
./gradlew.bat :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" \
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --no-parallel
```

- **退出码 `0`**（`BUILD SUCCESSFUL in 1m 5s`）
- XML 实测：`ShelfImporterTest` **45/45**、`ShelfViewModelTest` **20/20**、
  `EpubRepositoryReplaceTest` **5/5** —— 合计 **70 tests / 0 failures / 0 errors / 0 skipped**

（`ShelfImporterTest` 由改前 **39** 条增至 **45** 条，即本轮新增 **6** 条：
§4.1 表中的 5 条 + 基线哈希保护的 1 条。）

**编译**：上述命令含 `compileDebugKotlin` 与 `compileDebugUnitTestKotlin`，均成功。

---

## 5. 仍未覆盖的边界

1. **真实 Room 未覆盖。** 测试用 MockK 替身 `bookDao`/`bookContentDao`/`bookFileDao`，
   `runInTransaction` 由 `stubRunInTransactionExecuting()` 直接执行回调块。
   ⇒ **不能**声称真实 SQLite 事务、外键、触发器下的行为一致；「事务整体不生效」是**推断**，
   本轮未在真机/仪器测试验证。
2. **断电 / SIGKILL 未覆盖。** `renameTo`/`copyTo` 之间的进程猝死不在本轮可证明范围；
   修复只覆盖**进程内异常**这一级。
3. **文件系统语义未在 Android 上验证。** 测试跑在 JVM `TemporaryFolder`（真实 FS 但非
   Android 沙箱）。`renameTo` 在 Android 上的跨设备/权限行为、`filesDir` 配额、
   Scoped Storage 影响**均未验证**。
4. **`staging.renameTo` 成功但 `newCopyPromoted` 赋值前猝死**：这是进程内异常无法覆盖的窗口
   （第 2 点）。当前代码在 `renameTo` 之后立刻赋值，窗口极小但非零。
5. **`refreshSourceBaseline` 的第三级回退语义未做端到端验证**：用例 6 只断言
   「`contentHash == old_valid_hash`」，未验证真实设备上 provider 与本地副本同时不可读时的表现。
6. **未跑全量门禁**（按要求）：未跑完整单测、Lint、androidTest、APK 构建。
   `ShelfViewModelTest` / `EpubRepositoryReplaceTest` 是**相邻定向回归**，不构成全量门禁。
7. **未做变异测试**：红相是「整块回退旧实现」，不是逐行变异；`oldCopyMovedToBackup` /
   `newCopyPromoted` 的每个分支组合未逐一构造用例（覆盖了三个主要阶段，未穷尽 `renameTo` 失败回退路径）。

---

## 6. 边界声明

- **未运行** ADB / 真机 / 模拟器；未接触用户书库。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939…`。
- **未改动**其他窗口文件：本轮 `find -mmin -30` 命中的 `PendingTurnQueue*.kt`、
  任务账本与另两份报告均属**并发窗口**，非本人写入。
- 报告内**不含**真实书名、私人目录名或私有路径（测试夹具用 `a.txt` / `b.txt` / `书b1` 等中性名）。
- 唯一越界相关动作：为验证红相，**临时**回退并**立即还原**了本人所有的
  `ShelfImporter.kt`（还原后哈希已复核），未触碰他人文件。

完成后停止。
