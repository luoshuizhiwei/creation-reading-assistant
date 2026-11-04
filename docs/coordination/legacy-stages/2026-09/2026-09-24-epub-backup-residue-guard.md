# EPUB 替换入口的崩溃残留保护（2026-09-24 · 窗口 D）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- HEAD `5aed5939600e491843e3c44453921bd6273a233c`，分支 `codex/recover-worktree-20260920`，staged **0**
- 拥有并写入：`feature/reader/EpubRepository.kt`、`feature/reader/EpubRepositoryReplaceTest.kt`、本报告
- **未碰**：`ShelfImporter.kt`、任何队列、总账（`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md`）、其他窗口文件
- **未执行**：`reset / checkout / clean / stash / stage / commit / push`；未接手机 / ADB / 用户书库
- 前置依据：`docs/coordination/legacy-stages/2026-09/2026-09-24-txt-backup-crash-recovery-design.md` 的 **SR-2**（点名 `EpubRepository.kt:90` 无条件 `backup.delete()`，指出 EPUB 侧 K2/K3/K4 与 TXT 同形，要求同一份守卫同时覆盖两条路径）。本片是 SR-2 的**第一道（最小范围）**实现，不是自动恢复状态机。

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 本轮改动的实质 | `replaceStoredEpub` 开头不再无条件 `backup.delete()`；改为**入口守卫**：本次操作开始前 `.backup` 已存在 ⇒ 保留 target 与 backup、拒绝本次替换、抛出可识别错误 |
| 守卫位置 | 提到 `EpubParser.parse` **之前**（拒绝与否只取决于磁盘残留，与来源是否合法无关；也避免为一次注定被拒的替换去落临时副本 / 读大文件） |
| 拒绝时是否自动恢复 | **否**。不删、不覆盖、不恢复任何一侧，DB 零写入。自动恢复状态机属独立设计，不在本片 |
| 无残留时 | `backupReady` / `promoteStarted` 的进程内回滚语义**一字未改** |
| 定向套件结果 | **10 tests / 0 failures / 0 errors，退出码 0**（`EpubRepositoryReplaceTest`） |
| 失去的分支覆盖 | **`renameTo(backup)` 失败后退回 `copyTo(backup)`、且 `copyTo` 失败**这一分支；长度校验 `require(backup.length() == target.length())` 此前即未覆盖、现仍为缺口。二者已在测试类级文档如实登记，**未做任何弱化或绕过** |

## 1. 改动前的缺陷（为什么必须加守卫）

改动前 `replaceStoredEpub` 的第三行文件操作就是：

```kotlin
staging.delete()
backup.delete()          // ← 无条件删除
```

`.backup` 的语义是「上次替换的中间态」。进程在「旧副本已挪进 backup、新副本尚未落位」之间被杀（设计稿的 **K2**）时，backup 是磁盘上**唯一完好的正文**。旧代码在下一次替换的**第一秒**就把它删掉 —— 用户的正文连同那次未完成的替换一起消失，且没有任何东西可以还原。

「目标文件是否还在」**不能**用来区分 K2（backup 是唯一正文）与「上次已提交成功、只差清理 backup」（backup 是过期副本）。因此本轮**不做判定**，一律保留现场并拒绝。这与 TXT 侧已落地的守卫（`ShelfImporter.kt:963`）逐字同形，错误类型与消息模板也保持一致（`IllegalStateException` + 含「未完成的替换残留」）。

## 2. 改动内容

### 2.1 `EpubRepository.kt`

```kotlin
val target = File(context.filesDir, "books/epub/${stored.id}.epub")
target.parentFile?.mkdirs()
val staging = File(target.parentFile, "${stored.id}.repairing")
val backup = File(target.parentFile, "${stored.id}.backup")

// 守卫必须先于 EpubParser.parse：解析会为来源文件落一份临时副本、对大文件还要读全量
// 字节，而拒绝与否只取决于磁盘上是否已有残留，与来源是否合法无关。
if (backup.exists()) {
    throw IllegalStateException(
        "检测到上次未完成的替换残留（${backup.name}），已停止本次替换以免覆盖可能完好的旧正文；" +
            "请先在书架中确认该书内容，再重试",
    )
}

val validated = EpubParser.parse(context, selectedUri)
// …（其余不变）
staging.delete()
// 此处不再有无条件的 backup.delete()：入口守卫已保证本次操作开始时不存在残留
```

净变化：`+98 / −13`（`git diff --numstat`；含同文件内上一窗口遗留的未提交状态机改动行，非本轮新增）。

### 2.2 错误如何到达用户

`reselectEpub`（`ShelfImporter.kt:923`）直接调用 `replaceStoredEpub`，异常沿既有 `catch (e: Throwable) { val msg = e.message ?: "修复失败" }`（`:331-332`）向上传播，用户看到的就是上面那条中文消息。**本片未改 `ShelfImporter`**，也不需要改。

## 3. 测试改动

### 3.1 逐条处置（10 条，净 +1）

| # | 用例 | 处置 |
|---|---|---|
| 1 | 小文件成功后 `content_hash` = 新副本 MD5 | 保留 |
| 2 | 大文件（>32 MiB）`content_hash` 置 null | 保留 |
| 3 | 损坏文件被拒、旧 target 逐字节存活 | 保留 |
| 4 | 后置持久化失败 ⇒ 旧 target 从 backup 逐字节还原 | 保留 |
| 5 | `computeFileHash` 语义（大文件 null / 小文件异常必须抛） | 保留 |
| 6 | staging 复制阶段失败（**不预置 backup**）⇒ 旧 target 存活 | 保留（守卫放行，语义不变） |
| 7 | 协程取消仍走完整回滚 | 保留 |
| 8 | ~~`keeps existing target when backup copy fails`~~ | **转换** ⇒ `refuses and preserves target when a backup directory residue exists` |
| 9 | ~~`never adopts an incomplete backup file when backup copy throws`~~ | **转换** ⇒ `refuses residue and never promotes backup when target is missing` |
| 10 | — | **新增** ⇒ `succeeds and leaves no residue when no backup exists` |

### 3.2 转换后的三条守卫用例

- **#8 残留 = 同名非空目录（target 存在）**：抛 `IllegalStateException` 且消息含「未完成的替换残留」；旧 target 逐字节存活；**残留目录及其内容逐字节保留**（不删、不覆盖、不恢复）；`bookDao.update/upsert` 与 `readingProgressDao.upsert` 零调用；内存缓存无残留。
- **#9 残留 = 被写句柄持有的普通文件（target 缺失，即 K2 形态）**：同上拒绝语义；额外断言 **target 不得被凭空造出来**、**残留 backup 内容逐字节不变**。保留「持有句柄」这一形态还证明：即使残留被其它句柄打开，守卫也只是拒绝，不会因删除 / 移动失败抛出无关异常。
- **#10 无残留正常路径（对照组）**：不抛异常；新副本落盘；`bookDao.update` 恰 1 次且落库 id 正确；**收尾不留 backup / staging**。

> #9 的注入顺序有一处必须说明：先 `FileOutputStream(backup)` 打开句柄、**再**写入残留字节。反序（先 `writeBytes` 再开句柄）会让句柄把文件截断成 0 字节 —— 首轮运行时正是这样红的。修正后残留内容断言才成立。

## 4. 命令、退出码、测试数

```
cd D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\android
./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" --console=plain
```

| 运行 | 结果 | 退出码 |
|---|---|---|
| 首轮（#9 注入顺序有误） | `10 tests completed, 1 failed` —— `refuses residue ... target is missing` 在 `:586` 逐字节比较失败（实际比的是被句柄截断后的 0 字节） | **1** |
| 修正注入顺序后 | `BUILD SUCCESSFUL`，XML `tests="10" skipped="0" failures="0" errors="0"` | **0** |

XML 落盘：`android/app/build/test-results/testDebugUnitTest/TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`（mtime `2026-09-24 14:54`）。10 条用例名已逐条核对。

**与窗口 B 错开**：开跑前确认窗口 B 的 Gradle 构建于 `14:50:16` 结束（守护进程 `31144` 转为空闲），本轮构建 `14:52` 起跑。

## 5. 覆盖变化（含失去的分支）

### 5.1 新增覆盖

- 入口守卫本身：残留存在 ⇒ 拒绝、保留现场、DB 零写入、不自动恢复 —— **两种残留形态**（目录 / 真实文件）× **target 存在 / 缺失**。
- 守卫放行时的正常替换：无残留 ⇒ 成功且收尾无 backup / staging。
- 可识别错误：异常类型与消息片段均被断言（`IllegalStateException` + 「未完成的替换残留」）。

### 5.2 **明确失去**的分支覆盖（不得再声称覆盖）

加守卫后，`backup` 只可能是**本次操作自己建立的**；而守卫通过时若 `target` 存在，同目录 `renameTo` 几乎必然成功。因此以下分支在 JVM 单测中**不再可达**：

| 失去的分支 | 代码位置 | 原本由哪条用例覆盖 | 现状 |
|---|---|---|---|
| `renameTo(backup)` 失败 → `copyTo(backup, overwrite=true)` **中途失败** | `EpubRepository.kt` 回滚分流段的 else 支 | 旧 #8（目录形态）、旧 #9（持有句柄文件） | **不再可达**，本轮未做替代 |
| `require(backup.length() == target.length())`「备份旧副本时文件不完整」 | 同上 | **从未被覆盖**（旧 #9 曾以断言显式记录这一点） | 仍为缺口 |

- 未为了维持绿色而弱化这两处断言或绕过守卫；两条旧用例的注入形态被**转换**为守卫用例，而不是删除或放宽。
- 重新覆盖需要一个可注入的 `File` 工厂 / fs 抽象接缝（让 `renameTo` 可被强制失败），**不在本片范围**。
- 该类缺口与「本平台无 fs 注入接缝」是同一根因，与 `2026-09-24-epub-partial-backup-failure-test.md` §2 的实测结论一致。

## 6. 剩余缺口与未做的事

1. **自动恢复状态机未实现**：本片只拒绝、不判定。K2 残留会**永久停在拒绝态**，用户必须人工确认该书内容（消息已如此提示）。设计稿 §4 的 N 系列规则与 `EpubSizeRepairTask` 启动期恢复入口（**SR-1**）均未落地。
2. **用户可见提示（SR-3）未做**：错误消息会经 `ShelfImporter` 的既有 catch 冒泡展示，但没有「书架/诊断页」上的专门呈现。按设计稿 SR-3，本片只保证**不静默**。
3. **无 per-book 互斥**：同一本书被两个入口同时替换时，守卫不保证互斥（设计稿 §7.6 已列为未设计）。
4. **断电原子性**：本片不解决，与设计稿 §7 的硬边界一致。
5. **未跑全量测试 / 相邻回归**：按指令只跑 EPUB 定向 JVM 测试。`ShelfImporterTest` 是相邻回归（同样经 `replaceStoredEpub`），**本轮未运行**，其绿/红状态不由本片保证。
6. **未做真机验证**：本片为纯 JVM 逻辑改动，未安装 debug 包、未截图。

## 7. 边界声明

- 本轮只改 `EpubRepository.kt` 与 `EpubRepositoryReplaceTest.kt`，并新建本报告。
- 未改 `ShelfImporter.kt`（含其 TXT 侧守卫）、队列、总账、其他窗口文件。
- 未执行 `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0。
- 报告内不含真实书名、私人目录名或私有路径（示例一律用 `<bookId>` / `<name>` 占位）。

### 附：本轮两文件 sha256

```
e0979caa884ab687ba6747c19ae7df1b3b3d39e065c0498dc1c6e36e3038101b  EpubRepository.kt
79e345c3a9181eb31c6119d7d4733d47f45931a0826cadec9d2d92f7a6f48295  EpubRepositoryReplaceTest.kt
```

> 注：`EpubRepositoryReplaceTest.kt` 在本工作区为**未跟踪文件**（`??`），故 `git diff` 不显示其行数；哈希为上表所示工作区内容。
