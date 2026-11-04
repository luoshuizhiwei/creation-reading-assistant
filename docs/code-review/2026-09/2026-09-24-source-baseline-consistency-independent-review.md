# 独立复核：来源基线一致性修复（窗口 C 交付，2026-09-24）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜HEAD `5aed5939600e491843e3c44453921bd6273a233c`（未变）
- 被审对象：`docs/coordination/legacy-stages/2026-09/2026-09-24-source-baseline-consistency-fix.md` + 当前 `ShelfImporter.kt`（mtime 13:4x 段）/ `ShelfImporterTest.kt`
- 本轮性质：**只读**。未修改任何源码/测试/总账；**未运行 Gradle、ADB、真机、模拟器**；未 reset/checkout/clean/stash/stage/commit/push。唯一写入为本报告。
- 结论标记：**[A]** 我直接读到的代码/测试事实；**[B]** 由 [A] 推出的判断（未执行）；**[C]** 转述他人产物未复现。

## 1. 放行结论

**在 JVM 范围内放行该修复。** 理由：改动是把一个"把旧版本指纹写进新版本记录"的错误值改为"未知 = null"，方向正确、范围最小（净删 1 个回退表达式 + 注释），并且有一条会在改前变红的定向用例。但放行附带两个限定：

1. **不得把它描述成端到端验证**（§3 说明测试实际证明的边界）；
2. §4 的 F1（主线程重 I/O）与 F2（跨阈值分类用陈旧 size）**与本次修复同处一个函数**，属先前存在、本轮未处理，不应被这次"绿了"掩盖。

## 2. 已证实（我独立复算，不采信 C 的自述）

**(a) `library_source_refs.content_hash` 在生产侧无读取方：成立 [A]。**
- `grep -rn contentHash main/java` 全量结果里，与 refs 相关的只有两处映射：`LibrarySourceIndex.kt:123`（字段声明）与 `:138`（`contentHash = content_hash` 读实体入内存对象）；其余 `contentHash` 命中都是 **books 表**或 `metadata.contentHash`（当次导入新算的值），例如去重 `ShelfImporter.kt:651-653` 比的是 `book.content_hash`。
- DAO 侧：`LibrarySourceRefDao.kt:18-22` 的谓词只有 `SELECT *` 与 `WHERE candidate_fingerprint = :fingerprint`；**不存在** `WHERE content_hash` 查询；`data/local/dao/` 全目录 `grep content_hash` 零命中。
- 判定层：`LibraryBrowserPolicy.kt:78-100` 的 `CONTENT_UPDATED/EXACT_SOURCE` 只用 size+last_modified，`SAME_CONTENT` 只用 `candidate_fingerprint`。`ShelfSourceUpdateChecker.kt:23-37`（`isSourceContentUpdated`）同样不碰哈希。
⇒ C 的"抹成 null 不破坏去重"论证成立；我上一轮审查里"去重会误判"的说法确系混淆两列，C 主动更正，更正方向正确。

**(b) 新语义与"内容有更新"检测契约一致：成立 [A][B]。**
③ 之后基线写作 `(本次观测 size, 逐字段保留的 mtime, null)`；下一轮 `isSourceContentUpdated` 只比较 size/last_modified ⇒ 标签判定不受 null 影响，源文件再次变化仍能被检出（size/mtime 变化即触发）。`SourceFingerprint.FULL_HASH_LIMIT_BYTES = 32L*1024*1024`（`SourceFingerprint.kt:21`）与 `ShelfImporter.MAX_HASH_BYTES = 32 * 1024 * 1024`（`:1185`）**同值**，故"①大文件策略 null"与"③小文件未知 null"确实可由 `size` 与阈值的比较互相判别，C 这一点站得住。

**(c) 未使用字符串引用相等：成立 [A]。** `grep "=== |!==" ShelfImporter.kt` 在基线函数内零命中；修复只是删掉末级 `?: ref.contentHash`（现 `:370-380`），没有引入任何"新值==旧值 ⇒ 视为回退"的判定。

**(d) 红绿叙述与代码自洽：成立 [C→A]。** 大文件用例（`ShelfImporterTest.kt:706-736`）断言 `content_hash == null` 且 `size == 33MiB`；由于 ① 分支在改动前后都返回 null，它**必然在改前也是绿的** —— 即它是策略的刻画用例而非修复的判别用例。C 在报告里正是这样写的（"大文件策略用例通过"），没有把它冒充成红→绿证据，措辞诚实。

## 3. `runInTransaction` 未执行回调，是否削弱其声称范围

两条新用例都用了 `coEvery { bookDao.runInTransaction(any()) } returns Unit`（`:672`、`:717`），即事务回调**不执行**（全类唯一执行器 `stubRunInTransactionExecuting()` 在 `:1216`，只被 4 个断言事务内 `upsert` 的用例调用）。逐项判定：

- **对其声称的范围：不构成削弱 [A]。** 两条用例的断言对象都是 `sourceIndex.record(...)` 的实参（refs 行），该调用发生在 `ShelfImporter.kt:384-400`，**在事务之外**；`books` 侧写入与否不影响被断言的值。所以"③ 写 null、size 推进、mtime 逐字段保留"这三条断言是被真实验证的。
- **对其未声称但读者可能误以为涵盖的范围：确实不覆盖 [B]。** 因为回调不执行，③ 情形下 `books.content_hash` 实际被写成什么（应为新内容 MD5，来自 `:993-1002`）**在本用例里从未发生**，"books 新值 + refs null"这一**有意的发散**没有任何用例钉住；若将来有人"顺手"把 books 的哈希复制进 refs 以恢复"看起来一致"，现有测试集只有一条 null 断言会拦它，且拦的是值而非理由。
- **触发态与生产不完全同形 [B]。** 用例靠"`bookDao.getById` 返回的 `local_content_path` 指向 0 字节文件"（`:668-671`，`findInternalFile:412-418` 要求 `exists() && isFile` 才返回）+ "第二次 `openInputStream` 返回 null" 两级注开来抵达 ③；而生产里 ③ 之前事务已成功提交，`local_content_path` 已指向刚写完的非空副本，两级同时失败的现实触发是"副本被外部清理/瞬时 I/O 故障"。⇒ 这是一条**规则级守卫**，不是端到端复现。C 报告在"未覆盖"里已承认注入模拟的局限，措辞可接受，但**没有**明说"books/refs 发散未被任何用例覆盖"，这一句缺失。

## 4. 静态风险（先前存在，本轮未被引入也未被修复；不混进"已证实问题"）

**F1｜基线重算的哈希/指纹读发生在主线程。P2（卡顿/ANR 面，非数据正确性）[A]**
`ShelfImporter.kt:357-380`：`computeCandidateFingerprint(internalFile)`、`computeFileHash(internalFile)`（`:454-470`，≤32 MiB 全量 MD5）、`contentHashOrNull(uri, …)`（`:1071`+，整条 SAF 流摘要）都**没有** `withContext(ioDispatcher)`；同函数体内 `querySourceMetadata`(`:350`) 与 `findInternalFile`(`:412`) 却显式切了 IO 线程 ⇒ 说明是遗漏而非决定。调用链：`ShelfViewModel.confirmSourceRefresh`（`:678-695`，`viewModelScope.launch` 默认 Main.immediate，for 循环内直调 `importer.refreshFromSource`，无 `withContext`）⇒ 批量刷新 N 本时最坏可让主线程连算 N 次 ≤32 MiB 摘要。
建议最小下一步（1 行）：把 `refreshSourceBaseline` 的指纹/哈希段包进 `withContext(ioDispatcher)`，或将整段并入既有 IO 块。此项归窗口 B/C 共有的 ShelfImporter 所有权，需总指挥排归属，**不在本轮**。

**F2｜provider 不报 size 时，`isLarge` 用陈旧 size 分类，跨阈值有两类后果。P2（成本/证据面，不是错误配对）[A→B]**
`:352` `newSize = observed.first ?: ref.sizeBytes` → `:360` `isLarge = isLargeFile(newSize)`。
- 陈旧值判为"小"而实际文件 >32 MiB：走 else 分支，`contentHashOrNull(uri, 陈旧小size)` 的上限判断用的是**传入的 size**（`:1071-1072` `if (size <= 0 || size > MAX_HASH_BYTES) return null`）⇒ 策略上限被绕过，对真实大文件做全量 SAF 摘要；同时 `newFingerprint = null`（`:357-362`）⇒ 丢掉大文件唯一的 `SAME_CONTENT` 证据。
- 陈旧值判为"大"而实际是小文件：哈希按策略置 null（本可廉价算得）。
可达性：需要 provider 只报 `last_modified` 不报 `_size`（`querySourceMetadata` 允许该组合，`ShelfImporterTest.kt` 的 `cursor()` 桩正是 size 有值/modified 缺失的反向组合，说明这类不对称是常态考虑对象）；且标签能出现（`isSourceContentUpdated:29-35` 在 size 缺失时仍可由 mtime 判更新）⇒ 刷新可被触发。**当前是否有真机 provider 这样报，我未验证**（本轮不碰设备）。
关键澄清：这两种后果写下的哈希**仍是对当前内容的真实摘要或 null**，不构成 §2 的"错误配对"，所以不推翻本次修复；F2 是"用陈旧观测做新决策"的独立口径问题（亦即上轮审查 R5 的同一处）。
建议最小下一步（择一）：`isLarge` 改以**实际测得的文件长度**（`internalFile?.length()`）优先、观测值次之；或 size 未知时本轮直接不推进基线（与 ④ 同口径）。两者都要配套用例。

**F3｜实体 KDoc 已与刷新行为矛盾。P3（文档债，会误导下一次改动）[A]**
`LibrarySourceRefEntity.kt:31-33` 写"`size`/`last_modified`/`content_hash` **固定为导入时的观测值**……日常观测刷新只更新 `last_seen_at`/`availability`，**不回写这三列**"，而 `refreshSourceBaseline`（`:349-410`）正是在刷新时回写这三列。同一句 KDoc 又称这三列为"「内容有更新」的比较基线"——这恰恰是"一行必须描述同一次观测"的契约来源，也就是本次修复的正当性依据。⇒ 建议由 B 更新该 KDoc（补一句"正文刷新成功时由 `refreshSourceBaseline` 整行推进"），使契约与实现一致；否则下一位读者可能把 F1/F2 当"设计如此"。

## 5. 不成立的论证（明确纠正，不随原文放行）

C 报告用"`content_hash` 列上有索引 ⇒ 迟早会有按哈希的查询"来支撑"这是 latent 陷阱"。**索引只是 schema 上的能力，不是会被读取的证据**；本轮我确认了当前无任何按哈希的谓词（§2a）。真正使"保持旧哈希"不可接受的理由应当只有两条：① 实体 KDoc 声明的三元组"同一观测"契约（§4 F3）；② 一旦未来出现读取方，写下的就是**已污染**的历史值。请据此重述该论证（属措辞问题，不影响代码结论）。

## 6. 未来设计项（不要求现在决定）

- 若 `refs.content_hash` 将来出现读取方：null 目前同时承载"不适用（大文件）"与"未知（测不到）"两义，靠 `size > 32MiB` 反推在 F2（size 陈旧）下会误判。届时需要显式三态（列或 sentinel），而不是继续靠阈值猜。
- 基线三元组的"整行原子语义"：`record()` 是 upsert 整行，语义上已是原子的；但"部分字段前进、部分字段回退"的组合规则目前散落在 4 个 `?:` 表达式里。若 F2 采用"size 未知则整行不推进"，建议把规则收进一个小对象（类似 `PendingTurnQueue` 的抽法）并给 JVM 用例，避免每次新增字段就重开一次这种讨论。

## 7. 我未运行 / 无法断言

1. **未跑任何 Gradle 任务**（只读要求）⇒ C 报告里 RED（`46 tests completed, 1 failed`）与 GREEN（`46/0`）我是**按其叙述 + 读测试代码逻辑**接受的，标注为 [C]；其中 RED 的机理我在 §2(d)/§3 里独立复核为自洽（③ 分支在旧代码下必然写 `"old_valid_hash"`，新断言必然失败）。
2. 真机行为一律未覆盖：真实 SAF provider 是否会有"报 mtime 不报 size"的（F2）、③ 的现实触发频率、F1 的实际卡顿时长。
3. EPUB 侧、翻页队列、书库：本轮未触碰，也未复核其现状。

## 8. 精确位置索引

| 主题 | 位置 |
|---|---|
| 三态哈希与末级回退（已删） | `ShelfImporter.kt:349-380`（`isLarge` `:360`；① `:368-370`；②③ `:371-380`） |
| 逐字段回退 / provider 整体不可读早退 | `:350-353`；`querySourceMetadata` 的 null 语义见其函数体 |
| 基线整行写入（事务外） | `:384-400`（`record` 失败 best-effort 吞掉，取消例外上抛） |
| refs 哈希无读取方的证据 | `LibrarySourceIndex.kt:123,138`；`LibrarySourceRefDao.kt:18-22`；`LibraryBrowserPolicy.kt:78-100`；`ShelfSourceUpdateChecker.kt:23-37`；`ShelfImporter.kt:651-653` |
| 阈值同值 | `SourceFingerprint.kt:21` 与 `ShelfImporter.kt:1185` |
| 新用例 | `ShelfImporterTest.kt:659-704`（③ 判别）、`:706-736`（① 策略刻画）；执行器 `:1216`（未被这两条使用） |
| 主线程 I/O 链 | `ShelfImporter.kt:357-380`、`:412`、`:457-470`、`:1071`；`ShelfViewModel.kt:678-695` |
| KDoc 矛盾 | `LibrarySourceRefEntity.kt:31-33` |

审查结论：**已证实问题**＝无（消费者地图、语义一致性、无 `===`、红绿自洽均通过）；**静态风险**＝F1 主线程重 I/O、F2 陈旧 size 跨阈值分类、F3 实体 KDoc 与实现相反；**测试边界**＝③ 下 books/refs 发散与真实触发态未覆盖；JVM 范围**放行**，但要求 C 报告 §1 的"索引"论证按 §5 重述。
