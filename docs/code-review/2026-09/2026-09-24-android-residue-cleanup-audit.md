# Android 正文备份残留清理路径 · 只读审计（2026-09-24）

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（staged 0；本轮未做任何 git 写操作）
- **范围**：仅 `android/` 中与**书籍正文替换**相关的 `.backup` / `.repairing` / `.importing` 文件
- **本轮只写本文件**。未改源码、测试、总账、协调文档；未运行 Gradle / ADB / 真机 / 模拟器；未接触用户书库或设备设置；
  未执行 `reset / checkout / clean / stash / stage / commit / push`。

## 0. 快照，以及一次必须自曝的读取事故（请先读这一节）

本仓库有**多个窗口在并发改写**同一批文件。我在 14:20–14:55 期间观察到 `ShelfImporter.kt` 的 mtime
至少变化 5 次（13:51:13 / 14:45:25 / 14:50:52 / 14:52:06 / 14:52:18 / 14:52:44）。

**权威快照：`2026-09-24 14:54:26`（+0800），哈希与内容在同一条命令内读取（原子读取）**

| 文件 | `sha256` 前 16 位 | mtime | 入口守卫 |
|---|---|---|---|
| `feature/reader/EpubRepository.kt` | `e0979caa884ab687` | 14:47:01 | ✅ 在（`:94-99`） |
| `feature/library/ShelfImporter.kt` | `d1b36c5395e76005` | 14:52:44 | ✅ 在（`:962-968`） |
| `data/repository/BookRepository.kt` | `875e2c628accdaaf` | 2026-09-23 02:03:27 | — |
| `feature/sync/LocalZipBackup.kt` | `71591e8e33c61b33` | 2026-09-19 23:58:15 | — |
| `feature/reader/EpubParser.kt` | `11912d49b9a3756b` | 2026-09-19 23:58:15 | — |
| `ui/screen/profile/StorageSubPage.kt` | `3ed18f117d95f24e` | 2026-09-23 02:45:11 | — |

### ⚠️ 事故：我一度把「中途状态」当成了「最终状态」，本节予以撤回

我在 14:45–14:52 之间做了几次**非原子**读取（先 `sed` 看内容、后单独 `sha256sum` 取哈希，或反之）。
其中**两次**，我读到 `ShelfImporter.kt` 的入口是：

```kotlin
staging.delete()
backup.delete()      // 无条件
```

据此我在本报告的第一稿里把「TXT 入口缺守卫」判成了**当前可达的数据销毁路径（P1）**。

**该判定已撤回。** 权威原子读取（哈希 `d1b36c5395e76005`，mtime 14:52:44）显示：

- 入口**有**守卫，位于 `ShelfImporter.kt:962-968`，条件是 `if (backup.exists()) throw IllegalStateException(...)`；
- 全文件中 `backup.delete()` **只剩两处**：`:1036`（成功收尾）与 `:1048`（回滚内部），**入口处没有**；
- `staging.delete()` 在 `:953`，位于守卫**之前** —— 无害（理由见 §5）。

**根因与教训**：并发窗口正在高频重写这两个文件，而我用「读内容」与「读哈希」两条独立命令拼接证据，
两者之间文件可能已经变了；更糟的是编辑器写盘可能不是原子的，读者可能看到混合状态。
**这正是任务书警告的「避免把中途状态当最终状态」——我踩了一次，如实登记。**
⇒ 本报告**只采信 §0 表格那一次原子快照**；我对任何「守卫是否存在」的历史断言不再持有立场。

---

## 1. 三个后缀的语义（先把命名讲清，否则后面全是误报）

正文替换是「先备份旧文件、再提升新文件」的流程，三者是同一流程的三个临时物：

| 后缀 | 构造点（快照行号） | 语义 | 是否可能是唯一副本 |
|---|---|---|---|
| `<name>.repairing` / `<id>.repairing` | `ShelfImporter.kt:951`、`EpubRepository.kt:89` | **staging**：本次所选来源文件的副本，尚未提升 | **否**。来源在用户手上 / 外部 Uri 仍可取 |
| `<name>.backup` / `<id>.backup` | `ShelfImporter.kt:952`、`EpubRepository.kt:90` | **backup**：旧正文被改名后的存放处 | **是**（仅一个窗口，见下） |
| `<id>.importing` | `EpubParser.kt:63` | staging：从 SAF / fallback 复制来源文件的过程文件 | **否**。同 staging |

**`.backup` 只在唯一一个窗口里是「仅存副本」**：

```
target 已被改名成 backup  →  新副本还没落位成 target
```

此窗口内磁盘上是 `target 缺失 + backup = 完好旧正文 + DB 仍指向 target`。
若进程在此被 `SIGKILL` / 掉电杀死，**backup 就是用户仅存的那份正文**。
这是本审计唯一真正关心的状态。

**结构性事实（重要）**：全仓库**没有任何生产恢复入口**会把残留 backup 还原回 target。
`EpubSizeRepairTask`（唯一的进程启动期一次性任务）只做 EPUB 的 size 修复与封面补扫，不扫残留。
⇒ 残留一旦产生，要么被某个清理点删掉，要么**永久滞留**；**不存在自动恢复**。
（设计稿 `docs/coordination/legacy-stages/2026-09/2026-09-24-txt-backup-crash-recovery-design.md` 的 N 系列规则与 §E5 明确写了
「自动恢复状态机不在本轮实现」，与本审计观察一致。）

---

## 2. 生产调用点总清单

「当前可达」= 用户能通过 UI 走到；「跨进程」= 是否可能碰上**上次进程**留下的残留。

| # | 位置（快照行号） | 动作 | 后缀 | 当前可达 | 跨进程 | 判定 |
|---|---|---|---|---|---|---|
| A1 | `EpubRepository.kt:94-99` | `backup.exists()` ⇒ **抛错、保留现场** | `.backup` | 是（EPUB 重新选文件） | 是 | ✅ 正确守卫 |
| A2 | `EpubRepository.kt:191` | `if (backup.exists()) backup.delete()` | `.backup` | 是 | **否**（A1 已挡） | ✅ 无害 |
| A3 | `EpubRepository.kt:172` | 成功路径 `backup.delete()` | `.backup` | 是 | 否 | ✅ 正确收尾 |
| A4 | `EpubRepository.kt:184-188` | 回滚：`backup.renameTo(target)` / `copyTo`+`delete` | `.backup` | 是 | 否（本次自己建的） | ✅ 正确回滚 |
| A5 | `EpubRepository.kt:107` | 入口 `staging.delete()` | `.repairing` | 是 | 是 | ✅ 无害（§5） |
| A6 | `EpubRepository.kt:198` | `finally { staging.delete() }` | `.repairing` | 是 | 是 | ✅ 无害 |
| B1 | `ShelfImporter.kt:962-968` | `backup.exists()` ⇒ **抛错、保留现场** | `.backup` | 是（TXT/MD 重新选文件） | 是 | ✅ 正确守卫 |
| B2 | `ShelfImporter.kt:953` | 入口 `staging.delete()` | `.repairing` | 是 | 是 | ✅ 无害 |
| B3 | `ShelfImporter.kt:1043-1050` | 回滚：`backup.renameTo(internalFile)` / `copyTo`+`delete` | `.backup` | 是 | 否 | ✅ 正确回滚 |
| B4 | `ShelfImporter.kt:1036` | 成功路径 `backup.delete()` | `.backup` | 是 | 否 | ✅ 正确收尾 |
| B5 | `ShelfImporter.kt:1056` | `finally { staging.delete() }` | `.repairing` | 是 | 是 | ✅ 无害 |
| C1 | `EpubParser.kt:64` | 入口 `staging.delete()` | `.importing` | 是（打开未缓存的 EPUB） | 是 | ✅ 无害 |
| C2 | `EpubParser.kt:91` | `staging.copyTo` 成功后 `staging.delete()` | `.importing` | 是 | 是 | ✅ 无害 |
| C3 | `EpubParser.kt:94` | `catch { staging.delete(); throw }` | `.importing` | 是 | 是 | ✅ 无害 |
| D1 | `BookRepository.kt:287-291` | `deleteRecursively()` 整目录 + `<id>.epub` | 全部（含 `.backup`/`.repairing`） | 是（「移除正文」/批量清缓存） | 是 | ⚠️ **P1**（§3.1） |
| D2 | `StorageSubPage.kt:646-647` | `File(filesDir,"books").deleteRecursively()` | 全部，**所有书** | 是（「清空所有离线书籍」） | 是 | ⚠️ **P2**（§3.2） |
| E1 | `LocalZipBackup.kt:110-122` | 逐文件 `dst.outputStream()` **覆盖** `books/**` | 全部（覆盖而非删除） | 是（ZIP 整包恢复） | 是 | ⚠️ **P3**（§3.3） |
| — | **无** | **恢复残留 backup → target** | `.backup` | — | — | ❌ **不存在**（§1） |

**`findInternalFile`（`ShelfImporter.kt:412-427`）** 在 `:422` 显式跳过 `.repairing` / `.backup`：
这是**正确**的 —— 残留不会被当成可用正文进阅读器。**建议保持现状，不要改动。**

---

## 3. 当前可达路径的判定

### 3.1 P1 · 两道守卫把「销毁」转成了「死胡同」（**本轮唯一的实质发现**）

**这不是数据销毁，是可用性缺陷 —— 但它比销毁更难被发现。**

两道守卫（`EpubRepository.kt:94-99`、`ShelfImporter.kt:962-968`）的行为是：
**检测到同名 `.backup` 就抛错、保留现场、不写 DB**。这是**正确**的（设计稿 §E2.2 的 N2/N3/N6 正是要求
「无法判定 ⇒ 保留 + 报错」，而 N1「正向恢复」需 `matchesDB(B) == true`，**本轮未实现**）。

问题出在**报错文案的可操作性**上。两道守卫的文案都是：

> 检测到上次未完成的替换残留（`<name>.backup`），已停止本次替换以免覆盖可能完好的旧正文；
> **请先在书架中确认该书内容，再重试**

而**在书架里确认内容，并不会让 `.backup` 文件消失**。于是：

```
用户重试  →  守卫再次命中  →  同样的错误  →  无限循环
```

**全仓库不存在任何「清除残留」的用户入口。** 我把所有能删到 `.backup` 的路径查全了：

| 能删残留的路径 | 是否可用 |
|---|---|
| 该书「移除正文」→ `BookRepository.kt:289 deleteRecursively()` | 可用，但**连带销毁正文**，且需先经历 P1 的删除 |
| 「清空所有离线书籍」→ `StorageSubPage.kt:647` | 可用，但**销毁所有书** |
| `ShelfImporter.kt:422` 的 `endsWith(".backup")` | **只是跳过，不删除** |
| 其余 `deleteRecursively`（`StarDictImporter`、`ProfileUtils.kt:52`） | 词典目录 / `cacheDir`，**碰不到** `books/` |
| **任何恢复例程** | **不存在** |

**最小复现状态**：

```
filesDir/books/<bookId>/<name>.backup  ← 完好旧正文（上次替换崩在「旧已挪走、新未落位」）
filesDir/books/<bookId>/<name>         ← 不存在
DB: books 行仍指向 books/<bookId>/<name>
```

**动作**：用户点「修复 / 重新选文件」并选中**同名**文件 → 守卫命中 → 报错。
**再次尝试** → 守卫再命中 → **永远失败**。

**后果**：用户被永久卡住，且系统给出的唯一"出路"是销毁那份可能完好的旧正文。
这是「用守卫换安全」的代价 —— 守卫本身没做错，**缺的是 N1（正向恢复）与 N7/N8 之外的
一条「用户可见的残留处置路径」**（设计稿 §6.2 的 SR-1 / SR-3 正是为此提的 SEAM REQUEST，
**至今未实现**）。

**优先级：中**。不是数据销毁（守卫确实保住了现场），但会让用户无法继续使用该书，
且会把用户**推向**销毁路径（P1/P2）。**修复方向**（三选一，交决策方）：
(a) 实现 N1 正向恢复（需 `matchesDB`，受 32 MiB 限制）；
(b) 在存储/诊断页列出残留并提供「保留 backup 为正文」「丢弃 backup」两个明确动作（对应 SR-3）；
(c) 最低成本：把文案改成可执行的（例如明确指出残留文件的完整路径，并说明需要外部文件管理器处理），
    至少不让用户以为「去书架看一眼」能解决问题。

### 3.2 P2 · 「移除正文」整目录删除（`BookRepository.kt:287-291`）

**可达**：`ui/viewmodel/ShelfBookActions.kt:155` → `ids.forEach { repository.clearBookCache(it) }`（批量清缓存 / 移除正文）。
**动作**：`File(filesDir,"books/$bookId").deleteRecursively()` + `books/epub/<id>.epub`，全程 `runCatching`，失败只写日志。

**判定**：**有意的用户操作，且顺序正确** —— `clearBookCache` 先把 DB 指针置空
（`content_status="missing"`、`local_content_path=null`，`:272`），**再**删文件，
所以不存在「DB 仍指向 target 而文件被删」的矛盾。它在**不检查残留状态**的前提下销毁残留，
但这与用户此刻的意图（「我不要这本书的本地正文了」）一致。

**优先级：低。** 记录它的真正理由是 §3.1 —— 它是守卫给用户的**唯一出路**，而它同时销毁那份 backup。

### 3.3 P3 · 「清空所有离线书籍」整根目录删除（`StorageSubPage.kt:646-647`）

**动作**：`File(context.filesDir, "books").deleteRecursively()`，**一次删掉所有书**的正文目录与 `books/epub/`。
有二次确认对话框（文案明确「清理本地下载的书籍正文文件并释放空间……确认继续？」），`runCatching` 包裹。

**判定**：与 P2 同性质，**范围全局**。**优先级：低**（有意操作 + 二次确认）。
记录理由同 P2：这是**唯一**一条「一次抹掉所有书全部残留」的路径。

### 3.4 P4 · ZIP 整包恢复的逐文件覆盖（`LocalZipBackup.kt:110-122`）

**动作**：`importZip` 先入库（`:105`，`jsonBridge.importFromString` 在**事务内**），
**再**遍历 `tmp/books/**` 逐文件 `dst.outputStream()` 覆盖 `filesDir/books/**`（`:113-121`）。

**判定**：**不是「删除」但会「覆盖」**，且被明确自陈为**非原子**（`:107-109` 的注释承认
「失败时不影响已入库数据」）。这与设计稿 §E1.1 列出的「部分数据恢复」成因**完全吻合** ——
设计稿把它列为「T 事后丢失」的可能成因之一（属**风险枚举**，未取证）。

具体到本审计：`exportZip` 的 `walkTopDown`（`:54-58`）**不过滤后缀**，
所以导出的 ZIP **确实**含有 `.backup` / `.repairing`；恢复时会用 ZIP 里的同名 entry **覆盖**磁盘残留。
若 ZIP 是旧快照，覆盖后残留变成过期内容；若 ZIP 不含该 entry，旧残留**保留**。
两种结果都不会销毁仅存副本（ZIP 本身就是一份副本），但会让**磁盘残留的语义更不可判定** ——
恰是 §3.1 守卫**无法**区分的那种状态。

**优先级：低。** 属 ZIP 恢复的独立设计面，**本轮不建议改动**。

---

## 4. 跨进程静态风险（无生产触发路径，登记备查，全部未复现）

### B-1 · 回滚段自身失败会顶替原始 error

`EpubRepository.kt:184-189` 与 `ShelfImporter.kt:1043-1050` 的恢复动作（`renameTo` 失败退 `copyTo`）
**没有** `runCatching`：恢复动作自身抛错时，调用方拿到的是「恢复失败」，**原始原因丢失**。
数据可恢复（backup 仍在磁盘上）但目标处于半程。
**既有评审 `2026-09-24-epub-prebackup-fix-independent-review.md` §3 B4 已登记**，本轮确认**仍在**。
**未复现**。优先级：**低**（可诊断性缺陷，不是数据销毁）。

### B-2 · 无 `fsync`、无崩溃标记

所有 rename / copy 序列都没有显式 `fsync`，也没有「提交完成」的持久化标记。
设计稿 §7 已完整列出该硬边界（掉电时「已 close 未落盘」与「DB 已提交、文件未落盘」都可能）。
本轮**不重复**，仅确认其描述与当前源码一致。**不属于本轮可修范围**。

### B-3 · 守卫的**判定依据**本身不可靠（承 §3.1）

守卫只用 `backup.exists()` 这一个事实就拒绝。但设计稿 §E2.2 已经论证：
`exists()` **无法区分** K2（仅存副本）/ K3（未提交）/ K4（已提交的过期副本）三态。
所以守卫**既可能过度保守**（K4 时本可安全清理却卡住用户），**也不可能安全放行**。
这是设计上的**有意取舍**（宁可卡住不销毁），本报告**不**把它当缺陷，
但它决定了 §3.1 的「死胡同」是**结构性**的，不是文案没写好。

### 范围外相邻观察（一句话，不计入判定）

`EpubRepository.kt:101` 的 `EpubParser.parse(context, selectedUri)` 未传 `expectedBookId`，
因此 `validatedFile` 落在 `books/epub/epub_<hash>.epub`（**不带**本审计关心的三个后缀）。
成功路径 `:173` 会删掉它；**失败路径不删** ⇒ 每次失败的 EPUB 替换会**泄漏**一份来源副本。
与本审计范围无关（无后缀），但同属「残留累积」，建议另开条目跟踪。

---

## 5. 判定为「无害临时文件清理」的部分（不要报成问题）

| 位置 | 为什么无害 |
|---|---|
| `EpubRepository.kt:107`、`ShelfImporter.kt:953`、`EpubParser.kt:64` | 删的是 **staging（`.repairing` / `.importing`）**。它永远是「本次所选来源文件」的未完成副本，来源仍在用户手上或外部 Uri 可取；**删掉不丢任何用户数据**。设计稿 §3.3 也明确保留这条无条件删除。**注意**：`ShelfImporter.kt:953` 位于守卫（`:962`）**之前**，这**不**构成风险 —— 它删的是本次的 staging，与残留 `.backup` 无关。 |
| `EpubRepository.kt:198`、`ShelfImporter.kt:1056` | `finally` 里的 staging 收尾清理，同上。 |
| `EpubRepository.kt:172`、`ShelfImporter.kt:1036` | **成功路径**的 `backup.delete()`：此刻 DB 已提交、target 已是新正文，backup 是**本次自己建立的过期副本**，删除是正确的收尾。 |
| `EpubRepository.kt:184-188`、`ShelfImporter.kt:1043-1050` | **进程内异常回滚**：把本次自己建立的 backup 还原回 target。`backupReady` / `oldCopyMovedToBackup` 分流已由上一轮修好，本次复核**语义正确**。 |
| `EpubRepository.kt:191` | 形式上是 `backup.delete()`，但**只有 `backupReady == false` 时执行**，而 `backupReady == false` 意味着 backup 是**本次自己复制中途失败留下的残缺文件**（`:134-135` 的 `require` 失败路径）。由于 A1 守卫已保证入口无残留，此处**碰不到跨进程残留** ⇒ 当前无风险。 |

**关于「`.repairing` 能否成为唯一副本」的反驳**：设计稿 §E2.2 的 N5 场景里，
T 存在 + B 存在 + DB 匹配 B ⇒「先删 T 再把 B 恢复为 T」。该设计**不涉及** `.repairing`。
`.repairing` 在任何路径上都只是「本次来源的副本」，**不承载**旧正文的唯一性。
故上表对 staging 的判定成立。

---

## 6. 最小复现状态汇总（均不需设备）

| 编号 | 前置磁盘 / DB 状态 | 触发动作 | 期望 | 当前（§0 快照） |
|---|---|---|---|---|
| **R-P1a** | `books/<id>/<name>.backup` = 完好旧正文；`<name>` 缺失；DB 指向 `<name>` | TXT「重新选文件」，选中**同名**文件 | 拒绝、保留 target 与 backup | ✅ 已达成（`:962-968`） |
| **R-P1b** | 同上 | 同上，**再次重试** | —— | ❌ **永远失败**：无清除入口，文案指向「书架确认」不解决问题（§3.1） |
| **R-P1c** | 同上 | 「移除正文」 | 用户意图即删 | 删除（与意图一致，但这是守卫给的唯一出路） |
| **R-A1** | `books/epub/<id>.backup` 存在 | EPUB「重新选文件」 | 拒绝、保留 target 与 backup | ✅ 已达成（`:94-99`） |
| **R-P4** | 残留 + 旧 ZIP 快照 | ZIP 整包恢复 | 覆盖/保留，不销毁唯一副本 | 覆盖（自陈非原子，§3.4） |

R-P1a 与 R-A1 是**同一形态的两条路径**，**两条当前都已守住**。

---

## 7. 优先级建议

1. **P1（中）· 守卫造成的死胡同（§3.1）。** 本轮**唯一**的实质发现。
   不是数据销毁，是**用户无法继续使用该书**，且唯一出路是销毁那份可能完好的正文。
   建议动作（任选，需决策）：实现 N1 正向恢复 / 加用户可见的残留处置入口 / 至少把文案改成可执行的。
   **不建议**为了「让用户能继续」而删掉守卫 —— 那会退回真正的数据销毁。
2. **P2（低）· 「移除正文」与「清空所有离线书籍」销毁残留（§3.2 / §3.3）。** 与用户意图一致，**不建议**改行为。
3. **P3（低）· ZIP 整包恢复的逐文件覆盖（§3.4）。** 自陈非原子，属独立设计面，本轮不动。
4. **P4（登记）· 回滚段顶替原始 error（B-1）、无 `fsync`（B-2）。** 已在既有评审/设计稿登记，本轮无新增。
5. **登记** · `validatedFile` 失败路径泄漏（§4 末），无后缀、范围外，建议另开条目。

**不建议**把 §5 表格里的任何一条（staging 清理、成功收尾、进程内回滚、`:191`）改掉 —— 它们是正确的。
**特别不建议**把 `ShelfImporter.kt:422` 的 `endsWith(".backup")` 跳过改成处理 —— 那会让残留进阅读器。

---

## 8. 边界声明

- 只新增本文件。未修改 `EpubRepository.kt`、`ShelfImporter.kt`、`BookRepository.kt`、`LocalZipBackup.kt`、
  `EpubParser.kt`、`StorageSubPage.kt`、任何测试、B/D 的报告或总账。
- **未运行** Gradle / ADB / 真机 / 模拟器；未接触用户书库、设备设置或 `stay_on_while_plugged_in`。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0。
- 本报告**不含**真实书名、私人目录名或日志原文；示例一律用 `<id>` / `<name>` / `<bookId>` 占位。
- **全部结论绑定 §0 的哈希与 14:54:26 的原子快照。** 被审文件在审计期间由其他窗口并发高频改写，
  §0 已如实登记一次由此引发的错误判定及其撤回。**14:54 之后的变化不在本报告覆盖范围内，
  不得据本报告对 B/D 的最终交付下结论。**
- 若需复核，请用**一条命令同时**取哈希与内容（本报告 §0 的做法），
  不要把两次独立读取拼接成证据。
