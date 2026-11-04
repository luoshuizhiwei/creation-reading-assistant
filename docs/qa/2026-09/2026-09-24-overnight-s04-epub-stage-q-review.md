# 夜间独立审查：S04 EPUB 替换哈希与回滚（Stage Q 之后）

- **执行者**：窗口 B
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c3c44453921bd6273a233c`
- **日期**：2026-09-24（夜间）
- **性质**：**只读代码与测试审查**。未修改产品/测试源码、**未运行 Gradle**、未操作手机、
  未执行 `reset / checkout / clean / stash / stage / commit / push`。

---

## 0. 基线记录与证据分级

### 0.1 目标文件（本人亲自计算，SHA-256）

| 文件 | SHA-256 | git 状态 | 行数 |
|---|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt` | `c98e9a3d90a1a8e53ee91617c63edd71c830869b0d853b1382014e7a11720408` | ` M`（工作区既有改动） | 385 |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt` | `4767ae8c1dcf13667fd7982719ad34296be8732041ace4f6363743c1fce38ee0` | `??`（未跟踪） | 407 |

### 0.2 既有测试 XML（本人亲自核对）

**本轮工作区内 `android/app/build/test-results/testDebugUnitTest/` 只有 1 个 XML**：
`TEST-com.creationreadingassistant.ui.components.CraSliderTest.xml`（`2026-09-24 01:01:32`，窗口 D 产物）。

**`EpubRepositoryReplaceTest` 的结果 XML 在仓库内不存在**（`find` 全库无匹配）。
仅能证明它**曾被编译过**（`build/intermediates/classes/debugUnitTest/…` 与
`build/tmp/kotlin-classes/debugUnitTest/…` 下有其 `.class`，含 5 个 `@Test` 方法各自的类文件）。

⇒ 下文凡涉及该测试的**运行结果**（条数、通过与否），**全部来自 Stage Q 报告转述，本轮未复现**。

### 0.3 证据分级约定

| 标记 | 含义 |
|---|---|
| **【本人核对】** | 本轮直接读当前工作区文件 / 亲自算哈希 / 亲自 grep 得到 |
| **【Stage Q 转述】** | 引自 `agent-stage-q-s04-epub-hash-failure-correction.md`，本轮未复现运行 |
| **【未验证】** | 本轮无证据，不做判断 |

### 0.4 关于「computeFileHash 仍吞小文件异常」

**该问题在当前文件中已修复，本报告不再重复报告。**【本人核对】
`EpubRepository.kt:329-343` 的 `computeFileHash` 现在是：
`len > MAX_HASH_BYTES → return null`；`require(len > 0L)`；读取流**无** `runCatching` 包裹 ⇒ I/O 异常自然抛出。
与 Stage Q 报告 §1.1 描述一致。旧总账 Stage P 的结论**已过期**。

---

## 1. Spec 轴

### 1.1 【P1，本人核对】≤32 MiB 哈希 I/O 失败：是否必定阻止成功写库？

**结论：是 —— 在 `EpubRepository.replaceStoredEpub` 这条路径上成立。**

可复核路径：`EpubRepository.kt:119` `val newContentHash = computeFileHash(target)` 位于
`try { … }`（`:91`）之内，且在 `bookDao.update/upsert`（`:131`）**之前**。
`computeFileHash` 抛出 ⇒ 跳到 `catch`（`:136`）⇒ `throw error` ⇒ **`bookDao.update` 不执行**。

**但存在一处同类缺陷未被 Stage Q 覆盖**（见 1.2）。

### 1.2 【P2，本人核对】`ShelfImporter.computeFileHash` 仍是吞异常的旧实现，且仍在写 `books.content_hash`

这是**两个同名私有方法**的并存问题，Stage Q 只修了其中一个：

| 位置 | 行为 | 状态 |
|---|---|---|
| `EpubRepository.kt:329` | `require(len>0)` + **无** `runCatching` ⇒ 抛异常 | **已修**（Stage Q） |
| `ShelfImporter.kt:419-433` | `if (len <= 0L \|\| len > MAX) return null` + **`runCatching{…}.getOrNull()`** ⇒ 吞异常返回 `null` | **未修**（与 Stage P 报告的旧缺陷同形） |

可复核路径（两条，都写 `books.content_hash`）：
- `ShelfImporter.kt:364`：`refreshSourceBaseline` 内 `internalFile?.let { computeFileHash(it) }`
  → 写入 `library_source_refs.content_hash`（`sourceIndex.record`）。
- **`ShelfImporter.kt:966`**：`reselectPlainText` 内 `val newBookHash = computeFileHash(internalFile)`
  → `:972` `bookDao.upsert(existing.copy(… content_hash = newBookHash …))`，**直接写 `books.content_hash`**。

⇒ 对 **TXT/MD 来源刷新**（`reselectPlainText`，`ShelfImporter.kt:962-989`）：
若 ≤32 MiB 的内部副本读取时发生 I/O 故障，`computeFileHash` 返回 `null`，
该 `null` 被当作「大文件策略值」写入 `books.content_hash`，**旧哈希被静默清除且刷新仍报成功** ——
这正是 Stage Q 在 EPUB 侧修掉的同形缺陷，在 TXT/MD 侧依然存在。

**最小修复边界**：仅 `ShelfImporter.kt:419-433` 这一个私有函数（对齐 `EpubRepository` 的语义：
`len > MAX → null`；`len <= 0` 或读取失败 → 抛出）。需一并确认 `ShelfImporterTest` 中
TXT/MD 哈希用例是否依赖「失败返回 null」的旧行为（**【未验证】**本轮未跑测试）。

### 1.3 【P1，本人核对】替换后 DAO 失败：旧 EPUB 能否逐字节恢复？—— **存在真实数据丢失窗口**

Stage Q 报告 §1.1 与 §4.1 用例 4 声称「旧副本完整回滚」。**该结论在 `bookDao.update` 抛异常时成立，
但在 `target` 已被新文件替换、而 `backup` 尚未建立之前的窗口内不成立。**

看 `EpubRepository.kt:91-148` 的 try 块（行号见下）：

```
:91   try {
:92     validatedFile → staging 复制
:95     require(staging.length() == validatedFile.length() && > 0)
:98     if (target.exists() && !target.renameTo(backup)) {   ← backup 在此才建立
:99         target.copyTo(backup, overwrite = true)
:100        target.delete()                                   ← target 已删除
:102    if (!staging.renameTo(target)) { … }
```

**丢失窗口**：`:99` `target.copyTo(backup)` 成功之后、`:100` `target.delete()` 之前
（或 `renameTo` 失败回退路径中 `copyTo` 已成功但 `delete` 前）若发生异常，
跳到 `catch`（`:136`）：

```
:138    target.delete()          ← 删除 target
:139    if (backup.exists()) {   ← backup 此时已存在 ⇒ 能恢复
```

⇒ 这条路径**可以恢复**，因为 `backup` 在 `target.delete()` 之前已完整写入。**此窗口实际是安全的。**

**但真正的窗口在 `:98` 之前**：若 `target.exists()` 为 `false`（首次替换，无旧副本），
则 `:98` 整段跳过、**不建立 backup**；此时若 `:102` `staging.renameTo(target)` 之后、
`bookDao.update`（`:131`）之前抛异常，`catch` 会执行 `:138 target.delete()` ——
**把刚落盘的唯一新副本删除，且无 backup 可恢复 ⇒ 书从「有内容」变成「内容全无」**。
这在「首次替换」（`books/epub/<id>.epub` 尚不存在）或 backup 已被前次残留清理时触发。

**严重度**：P1（数据丢失，非瞬时故障）。触发条件较窄（需 target 原本不存在 + 替换后 DAO 失败），
但后果是**唯一可读副本被删且不可恢复**。

**最小修复边界**：`EpubRepository.kt:136-145` 的 `catch` 块。仅在
「`backup` 不存在**且** `target` 是由本次 staging 新建（而非由旧 target 改名而来）」时，
**不要** `target.delete()`（或改为把 staging 留作 backup）。改动限于这 10 行，不动 swap 逻辑。

> 注：`EpubRepositoryReplaceTest` 用例 4（`EpubRepositoryReplaceTest.kt:325-376`）
> 预置了**已存在**的旧 `target`（`:340` `createValidEpubFile(target, …)`），
> 因此 backup 一定存在 ⇒ **该用例覆盖不到上述窗口**。【本人核对，非运行结果】

### 1.4 【P2，本人核对】>32 MiB 清旧 hash 的边界

阈值一致性**已核对通过**（三处均为 `32 * 1024 * 1024 = 33554432`）：
- `EpubRepository.kt:346` `MAX_HASH_BYTES`
- `ShelfImporter.kt:1149` `MAX_HASH_BYTES`
- `SourceFingerprint.kt:21` `FULL_HASH_LIMIT_BYTES`

判定方向一致：`> MAX` 才走大文件分支（`EpubRepository.kt:331`、`SourceFingerprint.kt:26`）。
⇒ **恰好 33554432 字节（32 MiB）算小文件、会计算全量 MD5**，三处口径一致。**无 off-by-one。**

**但仍有两点边界风险**：

1. **`computeFileHash` 依赖 `File.length()`，与随后实际读取的字节数无二次校验**。
   `EpubRepository.kt:330` 取 `len` 判大小、`:334` 才打开流。若文件在两者之间被截断/增大，
   可能「按小文件算 MD5」却读到不同长度。`replaceStoredEpub` 路径因 `target` 是应用私有文件、
   且前面有 `:95` 的 `staging.length() == validatedFile.length()` 校验，**风险低**；
   `ShelfImporter` 的 `contentHashOrNull`（`:1035`）走 `contentResolver`，同样只信 `size` 参数。
   **严重度：P3（低）**，仅登记。
2. **>32 MiB 时 `content_hash=null` 会让「内容是否更新」失去哈希判据**，
   只能靠 `size`/`last_modified` 指纹（`SourceFingerprint` 分块指纹，`SourceFingerprint.kt:44-51`）。
   这是**既定策略**（Stage Q §1.1 亦确认），非缺陷；但意味着「大文件刷新后哈希为空」是**设计值**，
   任何下游若把 `content_hash == null` 当作「异常」都会误判。**【未验证】**下游是否有此类判据。

### 1.5 【已核对】「只定位本批」之外的 Spec 结论

- `replaceStoredEpub` **不触碰阅读记录**：`EpubRepository.kt` 中 `readingProgressDao` 仅出现在
  `:258`（`saveProgress`）、`:269`/`:277`（`loadProgress*`），**不在** `replaceStoredEpub` 路径。
  ⇒ 「id-keyed 阅读记录保留」通过「不改 `stored.id` + 不动 progress 表」实现，**成立**。
- `stored.id` 严格保留：`:111` `expectedBookId = stored.id`、`:120-130` `stored.copy(...)`
  不带 `id` 覆盖 ⇒ **成立**。

---

## 2. Standards 轴

### 2.1 【P2，本人核对】临时文件生命周期：`validatedFile` 在失败路径下可能残留

`EpubRepository.kt:134`：`if (validatedFile.absolutePath != target.absolutePath) validatedFile.delete()`
只在 **try 成功末尾**执行。`catch`（`:136-145`）**不删除** `validatedFile`。

`validatedFile` 是 `EpubParser.parse(context, selectedUri)`（`:79-80`）的产物 ——
【本人核对 `EpubParser.kt:50-95`】parse 会**先把源 Uri 复制/落盘**到
`filesDir/books/epub/<id>.epub`（`:60-92`）再校验结构（`:101` `ZipFile(epubFile)` 起）。
因此**当校验失败（如损坏文件）时，`EpubParser` 内部会 `staging.delete()`（`:93`）并抛出**，
`validatedFile` 本身通常不存在 ⇒ 残留风险主要落在「parse 成功、但后续任一阶段失败」的情形：
此时 `filesDir/books/epub/<id>.epub`（由 parse 生成的 id）会**永久残留**。

**严重度**：P2（磁盘残留，非数据损坏）。`EpubRepositoryReplaceTest` 用例 3/4 只断言
`staging`/`backup` 清理（`:318-321`、`:368-371`），**未断言 parse 产物**。

**最小修复边界**：`catch` 块内补一条与 `:134` 相同的删除（或把 `:134` 移入 `finally`）。

### 2.2 【P2，本人核对】`catch (error: Throwable)` 会吞 `CancellationException`

`EpubRepository.kt:136` 捕获 `Throwable` 并 `throw error`（`:145`）——**重新抛出本身没问题**，
但在 `catch` 内先执行了 `:137-144` 的**挂起无关但可能失败**的文件操作
（`target.delete()`、`backup.renameTo`、`copyTo`）。

两个隐患：
1. 若 `:138-144` 的恢复操作自身抛异常，**它覆盖了原始 `error`**，调用方拿到的是
   「恢复失败」而非真实原因，且 **`error` 被吞**。
2. 该仓库既有约定（见 `ShelfImporter.kt:293-296`、`:634` 等）是
   **`catch (e: CancellationException) { throw e }` 单独前置**，避免取消被当作失败处理。
   `replaceStoredEpub` **未遵循此约定** ⇒ 协程取消时会走完整回滚并抛出，语义上把「取消」当「失败」。
   【本人核对】`ShelfImporter.kt` 多处把 `CancellationException` 单独前置，此为仓库既定风格。

**严重度**：P2。最小修复边界：`catch` 前加 `catch (e: CancellationException) { throw e }`，
并让恢复段用 `runCatching` 包裹、失败时仍抛原 `error`。

### 2.3 【P3，本人核对】`require` 用于「文件不完整」属运行期校验，异常类型偏窄

`:95` `require(staging.length() == validatedFile.length() && staging.length() > 0L)`
抛 `IllegalArgumentException`。调用方 `ShelfImporter.reselectEpub`（`:905`）**无 try/catch**，
一路抛到 `repairFileTyped`（`ShelfImporter.kt:262`）的 `catch (e: Throwable)`（`:297`）⇒
最终以「修复失败：<msg>」呈现给用户。**行为可接受**，但与「I/O 故障」混在同一异常类型里，
不利于分层处理。仅登记，不建议本轮改。

### 2.4 【Standards 通过项，本人核对】测试是否吞自身断言

`EpubRepositoryReplaceTest.kt` 的三处失败断言：

- `:304` `assertTrue("…", caughtCorrupt is Exception && caughtCorrupt !is AssertionError)`
- `:360` `assertTrue("…", caughtPostSwap is RuntimeException && caughtPostSwap !is AssertionError)`
- `:393` / `:403` `assertThrows(IllegalArgumentException::class.java / IOException::class.java) { … }`

⇒ **未使用** `try { …; fail(…) } catch (Throwable)` 的假阳性写法；`assertThrows` 自带
「未抛出则失败」语义。**此项通过**，Stage Q §1.2 缺陷 1 已修复。

### 2.5 【P2，本人核对】用例 4 的 Mock 是否真正命中声称的「交错点」

声称命中点：「target 已替换为新文件 → `bookDao.update` 抛异常」（`:349-351`）。
【本人核对】**确实命中**：

- `target` 预置存在（`:340`），且 `:98` `target.renameTo(backup)` 或 `copyTo` 会建立 backup；
- `bookDao.update` 被 stub 为 `throws`（`:351`），而 `EpubRepository.kt:131`
  先调 `update` ⇒ 抛异常 ⇒ 进入 `catch` ⇒ 从 backup 恢复。

**但有两处覆盖缺口**：

1. **未断言 `bookDao.upsert` 的调用次数**。`:131` 是 `if (update == 0) upsert(updated)`；
   测试只 stub 了 `update` 抛异常，未验证 upsert 未被调用（用例 3 有 `coVerify(exactly=0){ upsert }`，
   **用例 4 没有**）。
2. **未覆盖 1.3 描述的「无 backup」窗口**（因其预置了旧 target）。

**最小修复边界**：仅测试文件 —— 用例 4 补 `coVerify(exactly = 0) { bookDao.upsert(any()) }`；
新增一条「target 原本不存在 + update 抛异常」的用例以覆盖 1.3。

### 2.6 【P3，本人核对】`mockUri` 的 `Uri.fromFile` stub 顺序依赖

`EpubRepositoryReplaceTest.kt:186-193`：先 `every { Uri.fromFile(file) } returns u`（精确匹配），
再 `every { Uri.fromFile(any()) } answers { … }`（通配）。MockK 精确匹配优先于通配，
⇒ 对 `file` 返回 `u`、对其他 File 返回新 subUri。**顺序正确**。
若将来调换顺序，通配会吞掉精确 stub — 建议加注释锁定，属可选改进。

### 2.7 【Standards 通过项】大文件用例的构造成本

`:253-257` 构造 32.5 MiB 合法 EPUB。【Stage Q 转述】该用例耗时约 10.8–11.1 s（Green/Red phase XML）。
属已知成本，非缺陷；但每次运行都要写 32 MiB 临时文件。仅登记。

---

## 3. 哪些只是 JVM 模拟、不能声称真实 Room/设备通过

【本人核对结构 + 部分转述】

| 结论 | 实际覆盖 | 不能声称 |
|---|---|---|
| `books.content_hash` 更新为新 MD5 / 大文件置 null | JVM：`bookDao` 是 **MockK**（`EpubRepositoryReplaceTest.kt:67-70`），`update` 恒返回 1 | **不能**声称真实 Room 事务、触发器、外键约束下行为一致 |
| 旧 target 逐字节从 backup 恢复 | JVM：`filesDir` 指向 `TemporaryFolder`（`:64`），真实文件系统但非 Android 沙箱 | **不能**声称 Android 存储（Scoped Storage / 配额 / 并发访问）下一致 |
| 损坏文件快速失败、不写库 | JVM：MockK 计数 | 同上；且真实 EPUB 损坏形态远多于测试里那 5 个字节 |
| `computeFileHash` 语义（大/零/不可读） | JVM：含一个**匿名子类伪造 `length()`**（`:400-402`） | 该伪造只覆盖「长度撒谎」这一条，真实 I/O 故障形态（ENOSPC、权限撤销）**未覆盖** |
| 并发/多进程 | 无任何覆盖 | **不能**声称并发安全；`memoryCache` 是**非线程安全** `LinkedHashMap`（`EpubRepository.kt:33`），多线程访问未加锁 |
| 断电 / SIGKILL 原子性 | 未覆盖 | Stage Q §5-1 已诚实声明；本报告同意，**仍不能声称** |
| 真实设备端到端 | 未运行 | 全部结论均**不得**写成真机通过 |

> `EpubRepository.kt:33` 的 `memoryCache`（`LinkedHashMap`）被 `withContext(ioDispatcher)` 内的
> 代码访问（`:107`、`:132`、`:137`），而 `cached()`（`:232`）无同步 ——
> **P3，并发下存在竞态**，仅登记（现有调用方可能都在主线程/单线程，【未验证】）。

---

## 4. 发现汇总

| # | 轴 | 严重度 | 摘要 | 位置 | 最小修复边界 |
|---|---|---|---|---|---|
| 1 | Spec | **P1** | 无 backup 时替换后 DAO 失败 ⇒ 唯一新副本被删、不可恢复 | `EpubRepository.kt:136-145` | catch 块：仅在 backup 存在或 target 非本次新建时才 delete |
| 2 | Spec | P2 | `ShelfImporter.computeFileHash` 仍吞小文件异常，仍写 `books.content_hash`（TXT/MD 刷新） | `ShelfImporter.kt:419-433`（写入点 `:966-972`） | 对齐 `EpubRepository` 语义：失败即抛 |
| 3 | Standards | P2 | 失败路径不清理 `EpubParser.parse` 落盘产物 | `EpubRepository.kt:134`（catch 未覆盖） | 移入 finally 或 catch 内补删 |
| 4 | Standards | P2 | `catch (Throwable)` 未前置 `CancellationException`；恢复段异常会吞原始 error | `EpubRepository.kt:136-145` | 前置取消分支；恢复段 `runCatching` 后仍抛原 error |
| 5 | Standards | P2 | 用例 4 未断言 `bookDao.upsert` 零调用；未覆盖「无 backup」窗口 | `EpubRepositoryReplaceTest.kt:325-376` | 补 `coVerify`；新增无-target 用例 |
| 6 | Spec | P3 | `computeFileHash` 只信 `length()`，读取前无二次校验 | `EpubRepository.kt:330-341` | 可选：读取后比对实际字节数 |
| 7 | Standards | P3 | `memoryCache` 为无锁 `LinkedHashMap`，跨线程访问有竞态 | `EpubRepository.kt:33`、`:232` | 可选：改为 `ConcurrentHashMap` 或加锁 |
| 8 | Standards | P3 | `mockUri` stub 顺序依赖无注释锁定 | `EpubRepositoryReplaceTest.kt:186-193` | 可选：加注释 |

**已核对通过、不再报告**：`computeFileHash` 吞小文件异常（已修）、阈值 32 MiB 三处一致、
测试假阳性写法（已修）、`stored.id` 保留、阅读记录未被触碰。

---

## 5. 未验证项（不得当作结论）

1. **【未验证】** 上述 8 项**均未编译或运行验证** —— 本轮未跑 Gradle，全部为静态代码路径分析。
2. **【未验证】** `EpubRepositoryReplaceTest` 的当前运行结果：仓库内**无**其 XML；
   Stage Q 报告的 5/5 通过属**转述**，且是在**本轮工作区状态之前**取得的。
3. **【未验证】** 修复 #2 是否影响 `ShelfImporterTest` 中 TXT/MD 哈希用例（未跑测试、未逐条读断言）。
4. **【未验证】** #1 的触发频率：需确认真实调用链中「target 原本不存在」是否常见
   （`ShelfImporter.reselectEpub` 只在书已存在时调用，理论上 target 应已存在 ⇒
   实际触发概率可能很低，**未量化**）。
5. **【未验证】** `memoryCache` 竞态是否在真实调用路径上可达。
6. **【未验证】** 真实 Room 行为、真实设备存储行为、并发与断电场景（见 §3）。

---

## 6. 边界声明

- **未修改**任何产品/测试源码（本轮只写本报告）。
- **未运行** Gradle、未操作手机/模拟器/ADB、未读取真实书名或用户源文件、未触碰私有备份。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939…`。
- **未改动**其他窗口文件：本轮 `find -mmin -25` 命中的 `android/` 条目均为
  **Gradle 缓存锁文件与他窗口构建产物**（如 `PageTurnerKt…class`），**非本人写入**。
- **报告内不含**真实书名、私人目录名或私有路径。
- **无 SEAM REQUEST**：所有发现均落在被审的两个文件 + 一个明确指出的
  `ShelfImporter.kt` 私有函数（#2，需属主确认是否归本轮）。

完成后停止。

---

# 勘误追加（2026-09-24，复核者：窗口 D · 只读）

以上全文为窗口 B 原始记录，**一字未删改**；本节为独立复核后的追加勘误，冲突处以本节口径为准。

 复核范围：`EpubRepository.replaceStoredEpub` 与 `EpubParser.parse` 的**真实文件路径**、
 失败注入点落在 try 的哪一段、以及两条修复建议是否会破坏回滚。全程只读，未跑 Gradle / ADB，
 未改任何源码或测试。

## E1. HEAD 笔误更正

| 项 | 值 |
|---|---|
| 本报告第 6 行原记 | `5aed5939600e49184918…`（原样为 42 字符：`5aed5939600e491843e3c3c44453921bd6273a233c`） |
| `git rev-parse HEAD` 实测 | `5aed5939600e491843e3c44453921bd6273a233c`（**40 字符**） |
| 差异 | 原串在第 **21** 位起多插了 `c3`（逐位比对首个不同位置 = 索引 21：真值 `4` vs 报告 `3`） |

⇒ 属**抄写笔误**，不是仓库状态差异：§0.1 两份文件哈希与行数（`c98e9a3d…`/385 行、`4767ae8c…`/407 行）
本轮重算**逐位一致**，基线仍然有效。第 308 行的缩写 `5aed5939…` 无歧义，不改。

## E2. §1.3（发现 #1，P1）**撤回原触发链，降级为「未经证实」**

原判断：「`target` 原本不存在 ⇒ `:98` 整段跳过、不建立 backup ⇒ DAO 失败时 `:138 target.delete()`
删掉**唯一可读副本**且**不可恢复**」。

**路径核对结果：`validatedFile` 与 `target` 是两个独立文件，原触发链不成立。**

| 事实 | 出处 |
|---|---|
| `validatedFile = File(validated.cachedEpubPath)`，而 `validated = EpubParser.parse(context, selectedUri)` **没有传 `expectedBookId`** | `EpubRepository.kt:79-80` |
| 无 `expectedBookId` 时 `id = "epub_" + uri.toString().hashCode().toString(36)`，落盘 `filesDir/books/epub/$id.epub`，`cachedPath` 即该文件 | `EpubParser.kt:57`、`:60`、`:98` |
| `target = filesDir/books/epub/${stored.id}.epub`（`stored.id` 为 Room 书 id） | `EpubRepository.kt:85` |
| ⇒ 除非重新选择的 Uri 字符串恰好复现出与 `stored.id` 同名的 hash，两者**不同名**；`:134` 的绝对路径比较也印证设计上按「不同文件」处理 | `EpubRepository.kt:134` |

由此，原结论的两处后果都不成立：

1. **「唯一副本被删且不可恢复」缺乏依据**：catch 只删 `target`，`validatedFile`（同样装着新字节的独立文件）
   在失败路径上**不被删除**（这正是本报告 §2.1 登记的残留），用户的原始来源文件也在应用之外；
   而在 `target` 本就不存在的前提下，操作前该路径上并无「可读副本」可供销毁。
2. **「首次替换时触发」未证实**：正常导入路径 `openEpub → EpubParser.parse(context, uri)` 会把
   `epub_<hash>` 作为书 id 落库（`EpubRepository.kt:39-43` + `EpubParser.kt:57`），因此常规书的
   `target` 就是当初的导入副本，本就存在。原报告 §5-4 自己也把触发概率列为【未验证】。

⇒ **发现 #1 由「P1 数据丢失」改记为「未经证实 / 措辞过强」**；§4 汇总表 #1 行按此读。
**但这不等于没有同类风险**——见 E3，真正的窗口在另一段，且比原描述更宽。

## E3. 原报告遗漏的更宽窗口：异常发生在 `:98` **之前**（backup 尚未建立）

静态路径分析（**未编译、未运行，需一条新用例定性**）：

```
:90   backup.delete()          ← 开局即清掉上次崩溃残留的 backup，恢复源被主动抹掉
:92   validatedFile.inputStream() → staging   ← 此处可抛（ENOSPC / 源文件在 :81 校验后消失或变短）
:95   require(staging.length() == validatedFile.length() && > 0)  ← 此处可抛 IllegalArgumentException
:98   if (target.exists() && !target.renameTo(backup)) { … }      ← backup 到这一行才建立
```

若异常发生在 `:92`–`:96`（即 `:98` **之前**），则 `backup` **从未建立**，而 `catch` 无条件执行
`:138 target.delete()` ——**把本次根本没有碰过、仍然完好的旧 `target` 删掉**，随后 `:139`
`if (backup.exists())` 为假、无任何东西可还原，而 `books` 行的 `local_uri` / `local_content_path`
仍指向该路径（`:125-126` 只在成功路径写）。这条路径**不需要** `target` 原不存在，
只要「旧副本存在 + 复制/长度校验失败」即可，触发面比 E2 描述宽得多。
`:146` 的 `finally { staging.delete() }` 在 catch 之后执行，不能阻止旧 target 已被删。

- 记为 **P1 候选（数据销毁，非瞬时故障）**，与 E2 的区别是前提条件与销毁对象都不同。
- **现有用例覆盖不到**：用例 3（`EpubRepositoryReplaceTest.kt:273`）把损坏来源喂给 `EpubParser.parse`，
  失败发生在 `:79`（**try 之外**），因此永远不经过 catch —— 这解释了它为何全绿；
  用例 4（`:325-376`，旧 `target` 在 `:341` 预置）把失败注入在 `:131`，此时 backup **已经**建立。
- **最小验证**（属主实现，本轮未写）：预置完好 `target` + 令 `:92`/`:95` 之间失败
  （如把 `cachedEpubPath` 指向一个在校验后被截断/删除的文件），断言 `target` 逐字节存活且 `backup` 从未参与。
- 不臆断「绝无其他风险」：`renameTo` 失败回退到 `copyTo`（`:99`）时若中途失败，`backup` 为**残缺文件**，
  catch 会删掉完好 target 再把这个残缺 backup 提升为 target —— 同一段代码的另一种损坏形态，
  本轮同样只作静态登记。

## E4. §2.2 的第二条修复建议**不可采用**：前置 `catch (CancellationException) { throw e }` 会跳过文件回滚

`replaceStoredEpub` 的 `catch (error: Throwable)`（`:136`）在重新抛出（`:145`）**之前**执行
`:137-144` 的内存缓存清理与**文件回滚**。若按建议改成「先单独捕获 `CancellationException` 并直接 `throw e`」，
取消路径将**完全绕过** `target.delete()` / `backup.renameTo(target)`，在 `:102` 之后、`:131` 之前被取消时
留下「新文件已换入、DB 未更新、backup 未清理」的错配状态——那正是本报告要防的问题。

- 现状并不吞掉取消：`throw error`（`:145`）已把 `CancellationException` 原样传播出去。
- 若确要区分「取消」与「失败」，正确形状是把**回滚移入 `finally`**（或 `try { … } finally { 回滚 }`
  后再按类型抛出），保证任何退出路径都完成文件还原；**不能**以「提前抛出」实现。
- 建议采用同一 catch 里的另一半问题仍然成立：`:140-143` 的恢复动作若自身抛异常，会**顶替**原始 `error`，
  调用方看不到真实原因。修复边界应保持为「恢复段单独包裹，失败时仍抛原 `error`」。
- ⇒ §4 汇总表 #4 拆分记：**「未前置 CancellationException」撤回为不可用建议**；「恢复段吞原始异常」保留 P2。

## E5. 本轮复核维持原判的条目

#2（`ShelfImporter.kt:419-433` 同吞异常且写入 `books.content_hash`，写入点 `:966-972`）、
#3（失败路径不清 `validatedFile`）、#5（用例 4 缺 `upsert` 零调用断言、覆盖不到无 backup 窗口）、
#6、#7、#8 与 §1.4 阈值一致性、§2.4 假阳性写法已修：**本轮复核未发现反证，口径不变。**
其中 #5 由 E3 进一步支持：不仅「无 backup 窗口」未覆盖，连异常发生在 `:98` 之前的分支也一例未测。

## E6. 本轮复核涉及的源码与文档位置

| 类型 | 位置 |
|---|---|
| 产品 | `EpubRepository.kt:39-43`、`:49-66`、`:74-149`（重点 `:79-80`、`:85-90`、`:92-98`、`:102-105`、`:107-113`、`:119`、`:125-131`、`:134`、`:136-148`）、`:329-343` |
| 产品 | `EpubParser.kt:50-98`（重点 `:57`、`:60`、`:86-96`、`:98`） |
| 测试 | `EpubRepositoryReplaceTest.kt:273-321`（用例 3）、`:325-376`（用例 4，旧 target 预置于 `:341`）、`:379-405`（用例 5） |
| 文档 | 本报告 §1.3、§2.1、§2.2、§2.5、§4、§5；`agent-stage-q-s04-epub-hash-failure-correction.md` §1.1、§3、§4.1；`2026-09-23-s04-epub-book-hash-codex-review.md`（Stage P 原裁） |
| 复核动作 | `git rev-parse HEAD`、两份文件 `sha256sum`/`wc -l`、上述区间逐行阅读。**未运行 Gradle / ADB，未修改任何文件。** |

## E7. 复核者边界

只写两份文档：本勘误节，与 `docs/coordination/legacy-stages/2026-09/2026-09-24-overnight-results-corrigendum.md`
（并只在总账末尾追加日期明确的最终快照）。未修改窗口 B 的原报告正文、未修改任何 `.kt`、
未 stage / commit / push / reset / checkout / clean / stash，未接手机。HEAD 仍
`5aed5939600e491843e3c44453921bd6273a233c`，staged 0 项。文中不含真实书名与私有路径。

