# 来源基线一致性修复：`refreshSourceBaseline` 的「新 size + 旧哈希」配对（窗口 C，2026-09-24）

仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜HEAD `5aed5939600e491843e3c44453921bd6273a233c`（本轮未变）
所有权内改动：`feature/library/ShelfImporter.kt`、`src/test/…/ui/viewmodel/ShelfImporterTest.kt`、本报告。未碰 EPUB、翻页队列、书库、手机/adb、其他窗口文件；未 reset/checkout/clean/stash/stage/commit/push。

## 0. 结论

候选成立且已修复：小文件本次测不到哈希时，旧实现把**上一个版本**的哈希连同**本次**的 `size` 一起写进 `library_source_refs`。修复后的语义是「未知如实写 null，绝不沿用旧值」，且**不依赖任何字符串相等比较**来判定回退。红→绿证据完整（同一条断言在改前必红、改后全绿）。

## 1. 决定"能不能改"的前提：先做消费者地图（这一步做完才有资格选语义）

`library_source_refs.content_hash` 的**读取方：无**。证据 [A]：

| 位置 | 事实 |
|---|---|
| `grep -rn "content_hash" data/local/dao/` | 零命中：DAO 没有任何按 `content_hash` 的查询；实体只在 `LibrarySourceRefEntity.kt:38` 建了索引、`:60` 声明列 |
| `ui/viewmodel/LibraryBrowserPolicy.kt:78-100` | `SAME_CONTENT` 用 `candidate_fingerprint`；`CONTENT_UPDATED/EXACT_SOURCE` 用 `size` + `last_modified`；③ 级用 format+名称+size。**都不读 refs.content_hash** |
| `feature/library/ShelfImporter.kt:643-648` | 导入去重读的是 `book.content_hash`（**books 表**），不是 refs |
| `ui/util/BookReadiness.kt:24,39` | 读的也是 `book.content_hash` |
| `ShelfSourceUpdateChecker.kt:46-48` | 明确"只读 size/last_modified，绝不回写基线三列" |
| `ShelfImporter.kt:349-353`（修复后） | refs 的哈希由本函数**唯一写入**，`books` 侧新哈希由 `reselectPlainText:987-996` 独立落库 |

⇒ "抹成 null 会破坏去重"这个顾虑**不成立**（它怕的其实是 `books.content_hash`，那条路径不受影响）。
⇒ **同时修正我上一轮审查报告里的一个过头结论**：`docs/code-review/2026-09/2026-09-24-txt-reselect-fix-independent-review.md` R4 写过"去重会误判/会重复导入"——那是把 refs 与 books 两列混为一谈的推断，现由上面的消费者地图否证。本轮危害等级下调为**记录完整性/latent 陷阱**（列上有索引，说明迟早会有按哈希的查询），不是当前可观察的功能故障。

## 2. 选定的回退语义（三态，互斥且不靠相等判断）

| 情况 | 判据 | 写入 `content_hash` | 同行 `size/last_modified` |
|---|---|---|---|
| ① 大文件按策略不算全量哈希 | `SourceFingerprint.isLargeFile(newSize)`（>32 MiB） | `null`（**不适用**） | 照常推进（逐字段） |
| ② 小文件且本次测得 | 内部副本哈希成功，或来源 uri 重算成功 | **新哈希** | 照常推进 |
| ③ 小文件但两处都测不到（I/O 故障） | 两级尝试都返回 null | `null`（**未知**） | 照常推进 —— 它们是**独立观测到的事实**，不因哈希未知而作废 |
| ④ provider 元数据缺失（与哈希无关） | `querySourceMetadata` 单字段返回 null | 不受影响 | **逐字段保留旧观测值**；provider 整体不可读时整行不写（`:350-351` 提前 return） |

要点：
- 删除的只有末级 `?: ref.contentHash`（旧实现第 374 行）。**没有**引入 `newContentHash === ref.contentHash` 之类的判定 —— 字符串引用相等对新计算的 MD5 与 Room 读出的实例恒为 false，用它决定回退会在"新内容恰好与旧内容同哈希"时又走回错误分支，所以这条路本身是错的，我按提醒直接排除。
- ①与③同为 `null`，但**可判别**：`size > 32 MiB` 即 ①，否则即 ③（阈值 `MAX_HASH_BYTES = 32 MiB`，`ShelfImporter.kt:1179`）。因此不需要新列即可区分；若将来要求两态在数据上显式可辨（例如按哈希查询要跳过"未知"而非当作"大文件"），那需要新增列或 sentinel 值 —— 属未定设计点，本轮**没有**擅自发明。
- 为什么选"写 null"而不是"整行退回旧基线、不推进 size"：后者会让书架在下一次进架时重新报「可更新」，即 M-BUG-03 第二阶段原症状的轻量复发（标签又清不掉），而我们手上确实有已验证的 size/mtime 事实没有理由不记录。两者都不写假值，取舍依据是"是否复发已知缺陷"。

## 3. 改动清单（最小）

- `ShelfImporter.kt:353-381`：删末级 `?: ref.contentHash`；把 ①/②/③ 三态与代价论证写成分支注释；函数 KDoc（`:341-352`）由"严禁抹除已有基线"改为"逐字段回退口径 + 哈希三态"，避免注释与代码互相打脸。
- `ShelfImporterTest.kt`：
  - 替换 `refresh keeps previous baseline hash when local copy hashing fails`（旧用例把**错误配对当成期望值**断言 `content_hash == "old_valid_hash"`，等于给缺陷盖章）→ 新用例 `refresh with unmeasurable small-file hash records unknown hash rather than the stale one`：断言 `assertNotEquals("old_valid_hash", …)` + `assertNull(…)` + `size == 12`（本次观测值，与基线 999 不同）+ `last_modified == 1000`（provider 未报 ⇒ 逐字段保留）。
  - 新增 `large source file keeps policy null hash while still advancing size`：观测 size = 33 MiB ⇒ `content_hash` 必须为 null（策略）且 `size` 必须推进 ⇒ 钉住 ① 不被误当失败，也不携带旧哈希。
  - 三条 provider 缺失/不可读路径仍由既有 `refresh preserves old last_modified …`、`refresh does not update baseline when provider is completely unreadable` 覆盖，未重复造轮子。

## 4. 红绿证据

| 阶段 | 命令（在 `android/`，均 `--offline --no-parallel`） | 退出码 | 结果 |
|---|---|---|---|
| RED（产品码未改，只改测试） | `:app:testDebugUnitTest --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest` | **1** | `46 tests completed, 1 failed`；失败项 = `refresh with unmeasurable small-file hash records unknown hash rather than the stale one`，消息 `AssertionError: 测不到哈希时不得沿用旧哈希（那是上一个版本的指纹）. Actual: old_valid_hash`；**大文件策略用例通过** |
| GREEN（删末级回退 + 注释） | 同上 | **0** | BUILD SUCCESSFUL 1m5s；`tests=46 failures=0 errors=0 skipped=0`；`> Task :app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin` 均执行（主码与测试码确证可编译） |

日志：`android/app/build/tmp/c-baseline-consistency/red-before-fix.log`、`green-after-fix.log`（gitignore 内）。
RED 阶段本身就是对新断言的证伪检验：它精确命中"旧值被写进新配对"这一行为，其余 45 项不受影响 —— 说明改动定位准确，不是靠放宽断言变绿。
并发提示：RED 起跑前观察到 `app/build` 在 30 秒内有 842 个文件写入（其他窗口在构建），两轮运行都完成了，但 `test-results/` 为共享目录，对方同期运行会覆盖本 XML —— 故本报告把 XML 视为**当次快照**，复算请重跑命令。

## 5. 未覆盖 / 剩余风险

1. **③ 的另一半没有端到端用例**：真实"两级哈希都失败"只能靠故障注入模拟（0 字节副本 + 流返回 null）；真机上 SAF provider 抖动、磁盘满等能否产出同形状态未验证。本轮不涉及设备。
2. **`isLarge` 由 `newSize` 决定，而 `newSize` 可能是保留下来的旧观测值**（provider 未报 size 时）⇒ 新旧内容跨越 32 MiB 阈值时会走错分支。这是我上一轮审查的 R5，本轮**未动**（修它需要同时决定"size 未知时是否推进基线"，属另一处契约选择，且当前无消费者受害）。
3. `library_source_refs.content_hash` 现在是"只写不读"列：它是否该保留、或改成显式三态（known / not-applicable / unknown），需要一个真正的读取方出现时再定；我不预先造抽象。
4. EPUB 侧 `replaceStoredEpub` 的 2 项红（上一轮记录）仍在他人所有权内，本轮未触碰、未判定。

## 6. 交付口径

可说：**该不一致配对已被一条会红的测试固定住，语义改完后定向 JVM 46/46 通过、主码与测试码编译通过。**
不可说：真机刷新链路已通过（未跑设备）；EPUB/TXT 文件替换的崩溃残留问题（R1-R3）已由本轮解决（本轮只碰基线三元组）。
