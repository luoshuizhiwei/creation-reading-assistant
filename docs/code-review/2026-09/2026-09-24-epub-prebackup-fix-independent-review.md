# EPUB 前置失败回滚修复 · 独立只读审查（2026-09-24 · 窗口 D）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`，staged **0**
- 审查对象：`docs/coordination/legacy-stages/2026-09/2026-09-24-epub-prebackup-rollback-fix.md`（窗口 B）、`EpubRepository.kt`、`EpubRepositoryReplaceTest.kt`
- 本轮只写本文件。**未修改产品或测试源码，未改总账，未接 ADB / 手机 / 用户书库**，未执行 `reset / checkout / clean / stash / stage / commit / push`。
- 被审文件当前状态与 B 报告的「改后」逐位一致（我亲自算）：
  `EpubRepository.kt` = `3bdd1267764bd5fcfd44995ea40546c750e0ae90e74ede6fcbb012f18a5d78a9`（mtime 13:29:36）；
  `EpubRepositoryReplaceTest.kt` = `86914015ed496ba6029d4ca2d6f5883a863ff4186e448e059d0c729bf7878aba`。

## 0. 裁决摘要

| 项 | 我的判定 |
|---|---|
| 缺陷一（backup 未建立时误删旧 target） | **已修，且有直接测试证据**（用例 1 的红相断言「旧 target 必须依然存在」正是失效点） |
| 缺陷二（`renameTo` 失败后退 `copyTo` 且中途截断 ⇒ 坏 backup 覆盖好文件） | **代码层面已修，但测试没有打在该形态上**：新增的长度校验 `EpubRepository.kt:116` 在用例 2 里**根本不会被执行到**（§2 逐条给证据）。⇒ 判为「修复方向正确、证据不足」，不判为缺陷 |
| 取消仍走回滚 | **已证明**（用例 3），且采纳了正确的形状（未在 catch 顶部提前重抛）；但它注入的是 **stub 抛异常**，不等于真实取消（§3 边界 B2） |
| 回滚自身失败会顶替原始 error | **仍未处理**（B 未主张修它，故不算回归；列为开放项 §3 边界 B4） |
| 开局 `backup.delete()`（`:90`）对上轮崩溃残留的影响 | **未被本轮修复覆盖，且是静态可达的数据销毁面**（§3 边界 B1，明确标注：未复现） |
| 「相邻回归 `ShelfImporterTest` 45/45 全绿」 | **该引用现已过期**：该类现为 **46** 条，我在 13:49-13:52 的独立复跑里恰好撞上其中 1 条红（属 B 的 TXT/导入侧在途工作，**与 EPUB 修复无关**；13:52 后该目录显示 46/0）。详见 §4 |

## 1. 逐路径核对（源码行号为当前 `EpubRepository.kt`，我逐条读过）

标记：`BR`=backupReady，`PS`=promoteStarted。开局动作：`:89 staging.delete()`、`:90 backup.delete()`。

| # | 失败发生在 | 状态 | catch 动作（`:163-174`） | 旧 target 结果 | 我的判定 |
|---|---|---|---|---|---|
| P1 | staging 复制抛错 `:103-105` | BR=false, PS=false | `:172` 若有 backup 则删；`:173` 因 PS=false **不删 target** | 原样存活 | ✅ 已修，有用例 |
| P2 | `:106` 长度校验失败 | BR=false, PS=false | 同 P1 | 原样存活 | ✅ 同一分支，未单独注入但逻辑同构 |
| P3 | `target.renameTo(backup)` 成功 | BR=true（旧内容此刻**只在 backup**） | — | — | ✅ 语义正确（原文件已被搬走） |
| P4 | `renameTo` 失败 → `copyTo` 抛错（用例 2 的形状：目标路径是同名目录 ⇒ **立即**抛，不写任何字节） | BR=false, PS=false | `:172` 尝试 `backup.delete()`；对**非空目录** `File.delete()` 返回 false（静默失败）；不删 target | 原样存活 | ✅ 目标行为达成；⚠ 但 `:116` 未执行、`:172` 的清理对该形状**无效且无断言** |
| P5 | `renameTo` 失败 → `copyTo` **写了一半**才抛（真实 ENOSPC 形态） | BR=false（异常在 `:115` 抛出，未及 `:116/:117`）, PS=false | `:172` 删掉残缺 backup 文件（此时是真文件，删得掉） | 原样存活 | ✅ 代码可推；❌ **无测试覆盖**（用例 2 不是这个形状） |
| P6 | `copyTo` **成功返回但内容被截断**（长度不符） | `:116 require` 拦下 ⇒ BR=false | 同 P5 | 原样存活 | ✅ 这正是 `:116` 的用途；❌ **无任何用例构造该形状**（长度校验分支未被执行） |
| P7 | `:122 staging.renameTo(target)` 失败 → `:123 copyTo` 中途失败 | BR=true, PS=true | `:164` 删半截新 target → `:165-169` 从 backup 还原 | 逐字节还原 | ✅ 逻辑正确；仅由 P8 的同类路径间接覆盖 |
| P8 | 替换完成后失败：`:128` 解析 / `:139` 哈希 / `:151` DAO | BR=true, PS=true | 同 P7 | 逐字节还原 | ✅ 用例 4（post-swap）+ 用例 3（取消）覆盖 DAO 点 |
| P9 | 原本无 target、替换后失败 | BR=false, PS=true | `:172` 无事；`:173` 删本次落位的新副本 | 不适用（原本就没有） | ✅ 与 B 表第 4 行一致；新字节仍留在 `validatedFile`（`:154` 只在成功路径删），可重试 |
| P10 | **回滚自身失败**（`:166 renameTo` 失败且 `:167 copyTo` 抛） | BR=true | 新异常从 catch 内抛出，**顶替 `error`**；`throw error`（`:177`）不再执行 | 数据可恢复（backup 仍在）但目标处于半程 | ⚠ 开放项，非本轮主张已修（§3 B4） |

**结构性结论**：把判据从 `backup.exists()` 换成 `backupReady` 是**对的**，且 `promoteStarted` 的引入修掉了「前置失败却删 target」这一类。P4/P5/P6 三态的**行为**都正确；缺的只是**证据**。

## 2. 「复制中途失败」的测试究竟注入在何处（本轮被要求核实的核心点）

读 `EpubRepositoryReplaceTest.kt:483-511` 与其注释 `:479-480`，注入方式是：

> 把 `backup` 路径预先建成**同名非空目录** ⇒ `target.renameTo(backup)` 失败、
> `target.copyTo(backup, overwrite = true)` 在 `File.copyTo` 内部「目标存在则先 `delete()`」时失败，
> 随即打开输出流 ⇒ **立即** `FileNotFoundException`（红相原文即「… (拒绝访问。)」），**一字节未写**。

由此可判定：

1. 生产码新增的 `require(backup.length() == target.length()) { "备份旧副本时文件不完整" }`（`:116`）
   在该用例里**执行不到** —— 异常在 `:115` 就抛出了。⇒ B §0 第二行「`renameTo` 失败 → `copyTo` 备份中途失败 → 残缺 backup 覆盖好文件：已修」中，
   **被测试证明的是"不可用 backup 不得覆盖好文件"（靠 BR=false 与 PS=false）**，
   而「**残缺** backup」这一具体形态（写了半截）与「长度校验」这条新逻辑**没有直接证据**。
2. 用例 2 **没有**断言 backup 残留被清理；而按该形状，`:172 backup.delete()` 对非空目录返回 false，
   残留实际存在 ⇒ 「避免下次被误用」这句注释**在测试里既未断言也不可达成**。
3. 用例 1（`:442-470`）的「backup 不得存在」断言（`:469`）是**恒真**的：该形状下 backup 从未被创建，
   `:172` 分支没被走到。⇒ 它能证明"没误删 target"（真价值，红相已证），不能证明清理逻辑。

这三点都属于**证据强度**问题，不是缺陷：我没有找到能让 P5/P6 丢数据的代码路径。

## 3. 证据不足的边界（静态登记，全部**未复现**）

- **B1｜开局 `backup.delete()`（`:90`）与上轮崩溃残留。**
  若上一进程在 `:110 renameTo(backup)` 成功之后、`:122` 提升完成之前猝死，磁盘态是
  `target 不存在` + `backup = 完好旧副本` + DB 指向 target。下一次替换在 `:90` **无条件删掉 backup**，
  而 `:109 target.exists()` 为 false ⇒ 不再建立 backup；若这一次也在 `:103-:106` 失败，则
  `BR=false, PS=false` ⇒ 不删 target（它本来就不在）⇒ 全书**没有任何内部副本**，且删除发生在失败之前。
  这不是本轮引入的（`:90` 改前就有），B 也只在 §5.2 写了「断电/SIGKILL 未覆盖」，
  没有把 `:90` 的**销毁作用**与"缺少 target 缺失 + backup 存在的恢复分支"联系起来。
  同时 `:89 staging.delete()` 会抹掉上轮已复制好的新副本（后果轻：外部来源仍在）。
  ⇒ 记为**已知未覆盖的数据销毁面**，需要 §5 T1 定性，而不是当作已发生缺陷。
- **B2｜"取消"是 stub 抛异常，不是真取消。** 用例 3 用 `coEvery { bookDao.update(any()) } throws CancellationException`
  （`:529`），因此：(a) DAO 写**没有发生**；(b) 协程作用域并未真的被取消。
  真实形态下 Room 的写可能**已经提交**、协程在恢复时才发现被取消 ⇒ 文件被回滚成旧副本，
  而 DB 里可能已写入新 `content_hash`/`size` ⇒ **元数据与正文不一致**（不是丢数据，但会让 hash 判据说谎）。
  该形态当前无覆盖，B §5.1 只泛泛说"真实 Room 未覆盖"。
- **B3｜`File.length()` 类校验的强度。** `:106`、`:116` 都只比长度；「长度相同内容不同」不会被拦（B §5.5 已自陈）。
  另外 `:116` 在 `backup` 是目录时返回 0 ⇒ 若 `target.length()` 恰为 0（不可能，`:106` 已挡）才会误判；此路不通，无风险。
- **B4｜回滚段顶替原始 error（P10）。** `:166-169` 无 `runCatching`；恢复动作自身抛错时调用方拿到的是"恢复失败"，
  真正的原始原因丢失。我上一轮审查（`2026-09-24-overnight-s04-epub-stage-q-review.md` E4）已提过，
  本轮确认**仍在**；B 未主张修它，故不算回归，但也不能算已解决。
- **B5｜Android 文件系统语义**（`renameTo` 跨目录、`filesDir` 配额、Scoped Storage）与真实 SQLite：测试在 JVM `TemporaryFolder`，B §5.1-5.3 已如实标注，我复核后**同意其自陈**，不追加为缺陷。
- **B6｜`EpubParser.parse`（`:79`，try 之外）的失败不进 catch**：这条决定了"损坏来源"类用例（既有用例 3）永远测不到回滚分支——这是 B §3.1 自己论证测试有效性时依赖的事实，我确认成立。

## 4. 我实际执行的验证（命令、退出码，以及一次现场证据被覆盖的事故）

```
$ ./gradlew --offline :app:testDebugUnitTest \
    --tests "...feature.reader.EpubRepositoryReplaceTest" \
    --tests "...ui.viewmodel.ShelfImporterTest" --no-parallel --rerun-tasks
54 tests completed, 1 failed
> Task :app:testDebugUnitTest FAILED        BUILD FAILED in 3m 30s
退出码：1
```

- 运行前我先把 B 留在共享目录里的两份 XML 连哈希抄进本文件（`88238f17…`、`4033f474…`，13:31，8/0 与 45/0）；
  运行后我抓到自己那一份 `TEST-…EpubRepositoryReplaceTest.xml` 的 `tests="8" skipped="0" failures="0" errors="0"`
  与**全部 8 条用例名**（含 3 条新增）⇒ **EPUB 侧 8/8 绿为独立复现**，被审源码在我运行前后未变（13:29:36）。
- **唯一失败是 `ShelfImporterTest > refresh with unmeasurable small-file hash records unknown hash rather than the stale one`
  （`ShelfImporterTest.kt:659`）**，属窗口 B 的 TXT/导入侧在途工作（`ShelfImporterTest.kt` mtime 13:47:54、
  `ShelfImporter.kt` 13:51:13，都在我运行期间被改），**与 EPUB 修复无关**，我没有据此判 EPUB 任何结论。
- **事故与教训（如实记录）**：我在两次读取之间，B 的并发运行**覆盖了 `test-results/testDebugUnitTest/`**——
  我第一次读到的 `ShelfImporterTest` 是 `46/1`（且失败用例名与我自有日志不一致），随后整个目录只剩
  `ShelfImporterTest.xml = 46/0`，我那份 EPUB XML 已消失。上面的正确失败归属**是从我保存在仓库外的
  运行日志里恢复的**。⇒ 若第一次读到的 XML 直接采信，我会把**错误的用例名**写进报告并误归因为 EPUB 回归。
  协调建议：并发窗口不得共用 `test-results` 目录做留证；需要留证的运行必须**当场**把 XML 复制或抄哈希到仓库外
  （本轮我就是靠外部日志救回来的）。这条与总账里已登记的「定向 `--tests` 会覆盖 XML」是同一件事，但这次
  发生在**同一次审查的中途**，严重度更高。
- 因此：**B §0/§4.2 的「`ShelfImporterTest` 45/45 全绿」现在是过期引用**（该类已 46 条，且我复跑时其中 1 条红，
  13:52 之后的目录显示 46/0，是否已被修好**不在本审查范围**，我不为别人的在途改动下结论）。

## 5. 最小补测建议（不改产品语义、不动容量；顺序按 ROI）

1. **T1｜崩溃残留恢复（针对 B1）**：预置 `target 不存在 + backup = 完好旧副本`，跑一次会在 `:103-:106` 失败的替换，
   断言 `backup` **没有被 `:90` 抹掉**（或产品改为检测到未协调 backup 时拒绝并提示）。
   这条决定 B1 是"缺陷"还是"仅理论风险"，**优先级最高**，因为它不需要新代码就能跑。
2. **T2｜真·中途截断的备份复制（针对 §2 第 1 点）**：把 `backup` 预置成一个**只读文件**（或在同目录放一个只读占位），
   使 `copyTo` 能打开但写入失败；或最小成本方案——直接对 `:116` 写一条类侧单测（构造长度不符的 backup 文件），
   断言 `BR` 保持 false 且 target 未被删。目的：让新增校验至少被执行一次。
3. **T3｜真取消（针对 B2）**：用 `runTest` + `backgroundScope` 取消调用方 job，让取消落在 `bookDao.update`
   **恢复时**，断言要么文件与 DB 同时回到旧态，要么明确记录「DAO 可能已提交」这一语义并调整断言。
4. **T4｜回滚段不吞原始异常（针对 B4）**：让 `:166/:167` 失败，断言抛出的仍是原始 `error`（需要产品在恢复段加包裹，
   属行为决策，交 Codex，不擅自动手）。
5. **T5｜backup 残留断言（针对 §2 第 2、3 点）**：用例 1/2 各补一条 `backup` 终态断言（应当不存在；若产品做不到，
   就把注释改成"尽力清理"，避免注释承诺强于代码）。

我不自行执行以上任何一条（会改测试源码，属 B 所有），也不建议把 `requeueFront` 式的类型化改动引入这里。

## 6. 边界声明

只新增本文件。未改 `EpubRepository.kt`、`EpubRepositoryReplaceTest.kt`、`ShelfImporter*.kt`、
`PageTurner.kt`、`PendingTurnQueue*.kt`、窗口 B 的报告与总账。未接 ADB / 真机 / 模拟器 / 用户书库 / 设备设置。
Gradle 仅执行了 §4 那一条定向命令（覆盖 B 已跑过的两个类，未扩大范围、未跑 lint/APK/全量）。
HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0；无 `reset / checkout / clean / stash / stage / commit / push`。
文中不含真实书名、私有目录名或日志原文；引用的失败信息仅为用例名与断言字面量。
