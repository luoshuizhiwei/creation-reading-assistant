# 独立审查：窗口 B 的 TXT 重选/刷新修复（2026-09-24）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜HEAD `5aed5939600e491843e3c44453921bd6273a233c`
- 审查对象（工作树脏改动）：`feature/library/ShelfImporter.kt`（+385 行，mtime 13:12:33）、`ui/viewmodel/ShelfImporterTest.kt`（+748 行）、以及相邻的 `feature/reader/EpubRepository.kt` / `EpubRepositoryReplaceTest.kt`（同一批修复的另一半）
- 本轮性质：**只读**。未修改任何产品或测试源码；未运行 Gradle / ADB / 真机（原因见 §1）；未 reset/checkout/clean/stash/stage/commit/push；未改总账
- 证据标记：**[A]** = 我直接读到的源码/测试/产物；**[B]** = 由 [A] 推出的行为结论（未执行）；**[C]** = 他人产物转述，我未复现

## 1. 为什么我没有跑测试（不是偷懒）

审查期间工作区**正被窗口 B 用于构建**：`tasklist` 见 4 个 `java.exe`，`app/build` 在 25 秒内被写入 5139 个文件 [A]。我若此时跑 `:app:testDebugUnitTest`，会与对方抢同一 Gradle 锁并**重写同一个 `test-results/testDebugUnitTest/` 目录**，历史上正是这样毁掉过彼此的 XML 取证。因此本轮只读取对方留下的产物，把"我自己跑"留给总指挥排期。
⇒ **结论"B 的 TXT 测试通过"我未独立验证**；我只验证了下面这份 XML 的内容与其含义。

**我读到的唯一结果产物**（`app/build/test-results/testDebugUnitTest/TEST-…EpubRepositoryReplaceTest.xml`，mtime **13:29:34**）[A][C]：

- `tests=8 failures=2 errors=0 skipped=0`
- 失败 1：`replaceStoredEpub keeps existing target when backup copy fails`
  `java.io.FileNotFoundException: C:\Users\…\books\epub\epub_backup_copy_fail.epub (拒绝访问。)`
- 失败 2：`replaceStoredEpub keeps existing target when staging copy fails before backup`
  `java.lang.AssertionError: 旧 target 必须依然存在`（对应测试第 463 行的断言）
- 该 XML 可能属于一次**未结束**的会话（目录里只有它一个），也可能是中间态；但它是当前工作区里唯一可读的运行证据。

对失败 1 的机制判断 **[B]**：测试把 `.backup` 路径造成**非空目录**（`EpubRepositoryReplaceTest.kt:487-489`）来注入失败，随后生产码的还原步 `backup.renameTo(target)` 把**目录**搬到了 target 路径上，于是 target 变成目录、后续按文件读取报 Windows「拒绝访问」。也就是说：**这条红既有"注入方式过于人为"的成分，也确实暴露了"还原来源未校验是否为常规文件"** —— 后者正是 §3 的 R1/R3 同一根因。是否要在 TXT 侧一并收口，交给 B/Codex 裁决。

## 2. 阶段状态机：TXT 路径逐路径核对

`reselectPlainText`（`ShelfImporter.kt:937-1042`）用两个阶段标记驱动回滚：`oldCopyMovedToBackup`（:952，在 :965 置位）与 `newCopyPromoted`（:953，在 :971 置位）。逐失败点走查 [A→B]：

| 失败点 | 标记态 | catch 里的动作（:1024-1034） | 结论 |
|---|---|---|---|
| 打开来源流失败 / 复制到 staging 中途抛 | `false,false` | 两个分支都不触发 → 只由 `finally` 清 staging | **正确**。旧副本逐字节存活（测试 :514 已覆盖） |
| staging 为空文件（:959） | `false,false` | 同上 | 正确 |
| 旧副本挪备份失败：`renameTo` 假且 `copyTo` 抛（:961-962） | `false,false`（:965 未执行到） | 不删 target；**但可能残留半截 `.backup`** | 无丢数据；残留见 R2 |
| `renameTo` 假、`copyTo` 成功、`internalFile.delete()` 假成功 | 置 `true` | 走还原分支 | 见 R2（备份未校验） |
| 新副本提升失败：`renameTo` 假且 `staging.copyTo` 中途抛（:967-969） | `true,false` | **不删** internalFile（半截新内容），把 backup 搬回 | 结果取决于 rename/copyTo 是否成功；见 R1、R3 |
| DAO 事务失败（:988-1016） | `true,true` | 删新副本 → backup 还原 | **正确**（测试 :538 逐字节断言，:592 覆盖"原本无副本"） |
| 回滚自身失败（:1027 的 `renameTo` 假 + :1028 `copyTo` 抛） | — | **第二个异常从 catch 内抛出，覆盖原始错误**；target 留在半截状态、`.backup` 仍在 | 见 R3 |

**没有发现"在正常失败路径下删掉唯一完好旧副本或用半截文件覆盖它"的可复现缺陷**：三处阶段判断本身是对的，B 的注释（:949-951、:1019-1023）也准确记录了旧实现"无条件 delete"的真实缺陷。剩下的问题都在**跨进程崩溃**与**非法残留**这两类异常环境里（下节）。

## 3. 静态风险（不粉饰为"已复现"，也不隐瞒）

**R1｜崩溃后残留的 `.backup` 是唯一完好副本，却在下一轮开头被无条件删除。P1（条件严格，后果是永久丢正文）**
证据：`ShelfImporter.kt:947-948` `staging.delete(); backup.delete()`（返回值皆被忽略）；`ShelfImporter.kt:416` 的候选列举**显式排除** `.repairing` / `.backup`。
链路 [B]：进程在「旧副本已挪进 `.backup`、新副本已落位、DAO 尚未提交」之间被杀 → 此刻 `.backup` 是用户唯一完好正文，而 app 任何入口都不会读它（:416）→ 用户下次点刷新，:948 直接把它删掉；随后 :961 又把当前（半截/新版）target 挪进 `.backup` 覆盖同名文件 → 旧正文彻底不可恢复。
建议最小下一步（择一，均在 B 的文件所有权内）：① 进入 :947-948 之前先做「若 `internalFile` 缺失或长度可疑且 `.backup` 存在 → 先从 `.backup` 恢复或明确报错并保留」；② 或把备份名带唯一后缀（如 `$safeName.backup-<ts>`），永不覆盖既有备份，只清理本次自己创建的。任一方案都不触碰动画/渲染。

**R2｜备份未经校验就当作可信还原来源，也没有"备份完成"检查。P2**
证据：`:961-965` 只有 `renameTo` 失败才退回 `copyTo` + `internalFile.delete()`；删原文件前**不校验** `backup.length()` 是否等于原文件长度、不校验可读；`:1026-1030` 还原时同样只用 `backup.exists()` 判断，不校验它是常规文件（§1 失败 1 就是这条的现实版）。
后果 [B]：一次"没抛异常的截断复制"（SAF provider 提前 EOF）会让 `.backup` 成为半截文件，随后被当完好副本还原 → 静默内容损坏。
建议：删原文件前加 `backup.length() == originalLength` 前置条件，不满足即抛并保持原文件不动（约 3 行）。

**R3｜回滚自身的异常覆盖真实错误，且失败后 target 状态与数据库指向不一致。P2**
证据：`:1018-1035` 的 catch 内调用 `backup.renameTo` / `copyTo`，无 `runCatching`、无异常抑制关系；`:1032-1033` 之后 `throw error`。若还原抛错，向上传播的是**还原异常**，用户看到的"修复失败：<msg>"里 `<msg>` 与真实原因无关；同时 `books` 行仍指向 target（旧内容语义），而磁盘上是半截新内容、`content_status` 未被改动。
建议：还原失败时把原始异常一并记入 `AppLog`，并在还原失败时**不要**让 target 停在半截内容（宁可退回"删除新副本 + 保留 `.backup` 并在消息里告知位置"），以便人工救回。

**R4｜`refreshSourceBaseline` 的末级回退把「新 size/mtime」与「旧 content_hash」配成一对，并与 `books.content_hash` 发散。P2（语义污染，不丢数据）**
证据：`ShelfImporter.kt:352-353` 每个字段独立回退到旧值；`:372-374` 哈希链为「内部副本哈希 → 按来源 uri 重算 → **`?: ref.contentHash`（旧内容哈希）**」；而 `:386-388` 无条件写入 `size = newSize, last_modified = newLastModified, content_hash = newContentHash`。对照 `:987-996`（同一批刷新把 `books.content_hash` 写成**新**文件 MD5）→ 末级回退发生时，`books` 侧是新哈希、`refs` 侧是旧哈希。
测试依据：`ShelfImporterTest.kt:1263-1302` 明确断言两者"必须一致"（用的是哈希成功路径）；而 `:649-684`（哈希全失败的场景）**只断言 refs 侧保留 `old_valid_hash`，既未断言同批写入的 `size` 是新的、也未断言 `books.content_hash` 是否仍一致** ⇒ 这条不变量恰好在回退分支上没人守。
影响 [B]：`library_source_refs` 变成"新 size + 旧哈希"的自相矛盾记录 → 去重/SAME_CONTENT 判定会把**旧文件**误判成与该书同内容（错误合并），对**新文件**反而判不同（重复导入）。
关于 `?: ref.contentHash` 的取舍本身，我不判它是错的：抹成 null 会丢掉判据。但**要么整条三元组（size/last_modified/content_hash）一起退回旧基线并保留「可更新」标签让用户重试，要么明确写 null 表示未知**；现在的"部分前进"是唯一不自洽的组合。建议按前者做（约 2 行改动：`newContentHash === ref.contentHash` 时同时沿用 `ref.sizeBytes/ref.lastModifiedMillis`）。

**R5｜`isLarge` 由可能陈旧的 `newSize` 决定。P3**
证据：`:352` `newSize = observed.first ?: ref.sizeBytes` → `:356` `isLarge = SourceFingerprint.isLargeFile(newSize)`。provider 不报 size 时用旧值判断"是否大文件"，而旧值对应旧内容 → 新旧跨界（40MB→5MB 或反向）时，本轮会走错哈希/指纹分支。与 R4 同源（回退值参与了新决策），可一并处理。

## 4. 测试缺口（与"已存在测试的强度"分开说）

**先给正面结论（这是我这轮重点核对的"假绿"风险）**：`ShelfImporterTest.kt:1163-1170` 的 `stubRunInTransactionExecuting()` 用 `coAnswers { (args[0] as suspend () -> Unit).invoke() }` **真正执行**了 `runInTransaction` 的回调；被 :568、:1192、:1238、:1282 四个用例调用，而这四个正是**断言事务内部 `bookDao.upsert` 参数**（含 `content_hash`）的用例 [A]。其余仍用 `returns Unit`（不执行回调）的位置（:490、:665、:715、:874、:935、:960、:1009、:1043）其断言对象都在事务之外（`sourceIndex.record`、coordinator 请求、文件字节、消息文本）⇒ **没有发现"声称验证事务内写入却因回调未执行而空转"的用例**。

**仍然存在的缺口**：

| 缺口 | 证据 | 影响 |
|---|---|---|
| TXT 阶段 2「备份失败」无用例 | `ShelfImporterTest.kt` 只有 :514(staging)、:538(DAO)、:592(无旧副本)、:560(成功)；EPUB 侧同类用例在 `EpubRepositoryReplaceTest.kt:482` 且**当前是红的** | R2/R3 无回归防线 |
| TXT 阶段 3「提升失败后回滚」无用例 | 同上；:1024-1031 分支无任何用例进入 | 半截 target 场景无人守 |
| TXT「回滚自身失败」无用例 | grep `setWritable\|readOnly` 在 `ShelfImporterTest.kt` 零命中 | 只能靠人肉读码，R3 不会被任何 CI 发现 |
| 「残留 `.backup` 被下一轮删除」无用例 | :514/:532 断言的是"本次不残留"，不是"上一轮残留怎么办" | R1 完全未覆盖 |
| `:514` 里 `coVerify(exactly = 0) { bookDao.upsert(any()) }` 是弱断言 | 该用例用默认桩 `returns Unit`，块永不执行 ⇒ 无法区分"代码没走到"与"桩没执行" | 建议该用例也上 `stubRunInTransactionExecuting()` 再断 exactly=0，才自证 |
| R4 的三元组一致性无断言 | :650 只查 refs 的 hash；:1263 只覆盖成功路径 | 末级回退一旦被触发，DB 进入自相矛盾态而 CI 仍全绿 |

## 5. 我未验证 / 无法断言的部分

1. **B 的 TXT 套件是否真的全绿**：见 §1，我没有跑，也没有对应 XML。
2. `EpubRepositoryReplaceTest` 那 2 项红的**归因**（B 代码缺陷 / Windows 注入方式不当 / 中间态快照）：需要 B 本人重跑并给出 `EpubRepository.replaceStoredEpub` 的实际还原顺序，我只给出了机制假设 [B]。
3. 真机行为一律未覆盖：SAF provider 在真实授权撤销、MTP/网盘文件、进程被 MIUI 杀掉等条件下，R1 的触发概率远高于桌面 JVM；本轮不涉及设备。
4. 与 S04 其他子项的联动（`refreshFromSource:283` 的索引重建请求、`ShelfViewModel` 的下拉确认）不在本轮审查范围内，我只确认了调用顺序是"基线推进 → 索引请求"，未评估其正确性。

## 6. 建议的最小下一步（按 ROI 排序，均在窗口 B 所有权内）

1. **R1**：进入 `:947-948` 前处理既有 `.backup`（或改用唯一后缀备份名）。这是唯一会导致**永久丢正文**的一条。
2. **让 §1 两条红变绿或改为可行注入**：明确"还原来源必须是常规文件"，并顺带收口 R2 的长度校验。
3. **R4**：把末级回退改成"三元组整体退回旧基线"，并在 `:650` 补一条 size/last_modified 的配对断言。
4. 补 R2/R3/R1 三阶段的 TXT 用例（用 mock `File` 或只读目录，需在 Windows 上验证可行性）+ 把 `:514` 的弱断言换成执行事务桩。
5. 上述都不触碰动画、手势、帧快照、位图轮换与阅读进度/书签坐标。

## 7. 边界声明

本轮只新增本报告一个文件；未修改产品码、测试码、Gradle、schema、脚本、任务账本或他人报告；未运行 Gradle/ADB/真机/模拟器；未 stage/commit/push/reset/checkout/clean；未进入原始仓库；未读写任何独立 Agent 记忆库；报告内不出现真实书名（统一"测试 TXT / 测试 EPUB"）。

审查结论口径：**TXT 两阶段回滚的主干正确，未发现正常失败路径下的删好副本/覆盖缺陷；真正的风险集中在崩溃残留（R1，P1）、未校验备份（R2）与回滚异常掩盖（R3），以及哈希回退造成的基线自相矛盾（R4）。同时窗口 B 自己的 EPUB 用例当前有 2 项红，且 TXT 的阶段 2/3/5 完全没有回归防线。**
