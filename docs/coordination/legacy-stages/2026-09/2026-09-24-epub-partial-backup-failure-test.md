# EPUB 备份失败路径的定向证据补强（2026-09-24 · 窗口 D）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`，staged **0**
- 拥有并写入：`EpubRepositoryReplaceTest.kt`（+1 条用例）、本报告。**`EpubRepository.kt` 净变化为零**（做过一次受控变异并已还原，哈希见 §4.3）。
- 未碰：`ShelfImporter*`、总账、其他窗口文件；未接手机 / ADB / 用户书库；未执行 `reset / checkout / clean / stash / stage / commit / push`。
- 前置阅读：`docs/code-review/2026-09/2026-09-24-epub-prebackup-fix-independent-review.md`（我上一轮的审查，指出「同名非空目录」用例在复制开始前就抛错，未执行 `EpubRepository.kt:116` 的备份长度校验）。

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 本轮新增的确定性用例 | `replaceStoredEpub never adopts an incomplete backup file when backup copy throws` —— **backup 以「真实存在但内容不完整」的普通文件形态**出现，证明旧 target 逐字节存活、DB 零调用、不完整副本**从未被提升** |
| 是否执行到 `:116` 长度校验分支 | **没有**。用例把这个事实作为断言写进测试（`reachedLengthCheck` 必须为 false），而不是假装覆盖 |
| 真·中途写入部分字节的注入 | **本平台无接缝情况下不可确定实现**（§2 实测 + §5 推演）。按指令**未**用另一个提前抛错用例冒充 |
| 新用例是否有牙 | **有**：把 catch 还原成 Stage Q 之前的旧语义后，该用例变红，失败信息是 `expected.length=1467 actual.length=0`（旧语义把 0 字节 backup 提升成了 target） |
| 定向套件结果 | 修复态 **9 tests / 0 failures / 0 errors，退出码 0**；变异态 **9 / 3 failures，退出码 1** |

## 1. 为什么要换注入形态

`EpubRepository.kt:115-118`：

```kotlin
target.copyTo(backup, overwrite = true)
require(backup.length() == target.length()) { "备份旧副本时文件不完整" }
backupReady = true
target.delete()
```

现有用例把 `backup` 预置成**非空目录**：`renameTo` 返回 false ⇒ 进入 `:115`；但 `copyTo` 在**打开目标流之前**就抛 `FileAlreadyExistsException`，`:116` 从未求值 ⇒ 「长度校验」这条新逻辑**没有任何执行证据**。

## 2. 实测：`File.copyTo(overwrite = true)` 在各种目标形态下到底怎么走什么字节（JDK 17 / NTFS / 项目 kotlin-stdlib）

用一次性探针实测（探针已在还原时删除，未留在仓库里），源固定 300 000 字节：

| 目标形态 | copyTo 结果 | 调用后目标状态 | `renameTo` 到该目标 |
|---|---|---|---|
| 非空目录 | `FileAlreadyExistsException` | 仍是目录，`length()==0` | false |
| **空目录** | **成功** | 普通文件，300 000 字节（目录被覆盖删掉） | false |
| 已存在的普通文件（无句柄） | 成功 | 300 000 字节 | false |
| **已存在的普通文件，且被其它句柄持有** | `FileAlreadyExistsException` | **普通文件，`length()==0`** | false |
| 只读普通文件 | 成功 | 300 000 字节 | false |

两条决定性读数：

1. 只有「**被持有的普通文件**」这一形态能同时满足：① `renameTo` 失败从而进入 `:115`；② 失败后磁盘上留下的是一个**真实文件**（不是目录），其 `length()` 就是**不完整**的证据。⇒ 这正是 `:116` 与 catch 里 `backupReady=false` 分支要处理的那类副本，也是 `backup.exists()` 会误判的那类。
2. 所有可稳定制造的失败都是**写 0 字节前就抛**（0 或目录），**没有任何形态能确定性地留下"写了一半"的文件**（见 §5）。

## 3. 新增用例（`EpubRepositoryReplaceTest.kt`，唯一新增测试）

`replaceStoredEpub never adopts an incomplete backup file when backup copy throws`：

1. `seedExistingTarget()` 预置完好旧 target，并记下原始字节；
2. 把 `backup` 预置成**已存在且被测试持有句柄的普通文件**；
3. 走真实 `repository.replaceStoredEpub(...)`；
4. 断言：
   - 异常向上传播（`caught is Exception && caught !is AssertionError`）；
   - **`assertArrayEquals(oldBytes, target.readBytes())`** —— 旧 target 逐字节存活，没有被删除，也没有被不完整副本覆盖；
   - **`backup.length() < oldBytes.size`** —— 那份副本从未写完整，且它存在过（真实文件形态）；
   - `bookDao.update` / `upsert` **零调用**（DB 未更新）；
   - **明确记录分支归属**：`reachedLengthCheck = caught is IllegalArgumentException && message 含 "备份旧副本时文件不完整"`，并断言其为 **false**，失败信息里带上实际异常类型与消息。含义：本用例**不**声明覆盖 `:116`；若将来有人把注入做实使该分支被执行，这条断言会红，逼迫更新文档 —— 防止证据边界被静默抹掉。

## 4. 命令与证据（全部 `--offline --no-parallel`，只跑 EPUB 定向类）

### 4.1 绿相

```
$ ./gradlew --offline :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" --no-parallel
BUILD SUCCESSFUL in 41s          退出码：0
XML: tests="9" skipped="0" failures="0" errors="0"     (sha256 前缀 7b19e18de6a441ff)
```

9 条用例名全部回读到，含新增一条：`… staging copy fails before backup` / `… backup copy fails` / **`… never adopts an incomplete backup file when backup copy throws`** / `… post-swap update fails` / `… caller is cancelled` / `updates books content_hash …` / `clears content_hash to null …` / `rejects corrupt file …` / `computeFileHash fails …`。

### 4.2 红相（受控变异：把 catch 还原成 Stage Q 之前的旧语义）

```
$ （同 4.1 命令）        退出码：1        BUILD FAILED in 16s
XML: tests="9" failures="3" errors="0"
```

| 变红的用例 | 失败信息（脱敏摘录） |
|---|---|
| `… staging copy fails before backup` | `AssertionError: 旧 target 必须依然存在` |
| `… backup copy fails` | `FileNotFoundException: …\epub_backup_copy_fail.epub` |
| **`… never adopts an incomplete backup file …`（本轮新增）** | `assertArrayEquals: array lengths differed, expected.length=1467 actual.length=0` |

⇒ 新用例确实能抓到真实缺陷，而且是**最坏形态**：旧语义把 0 字节的不完整 backup 提升成 target，用户正文变成 0 字节。这条是目录形态用例（前两条）证明不到的。

### 4.3 还原完整性

变异后还原：`EpubRepository.kt` sha256 = `3bdd1267764bd5fcfd44995ea40546c750e0ae90e74ede6fcbb012f18a5d78a9`，与变异前基线逐位一致 ⇒ **本轮对生产码净变化为零**。
测试文件当前 sha256 = `d66318034b927c2af880a3900e7b45e41aec48d54d210f5d97038e9cb30b2710`（基线 `86914015…` + 1 条用例）。
日志留在仓库外：`/tmp/d-epub-probe.log`、`/tmp/d-epub-mutation.log`、`/tmp/d-epub-green.log`。

## 5. 仍未证明的边界（不粉饰）

1. **`:116` 备份长度校验分支：无执行证据。** 本轮所有可稳定制造的失败都在校验之前抛出。
2. **真·"写出部分字节后失败"未注入。** 需要的形态是"目标可创建、写到一半失败"：
   - 持有句柄 ⇒ 目标连创建/删除都失败（写 0 字节）；
   - 只读 / 无句柄普通文件 ⇒ 直接成功；
   - 目录 ⇒ 提前抛错；
   - 唯一能产生"部分字节"的手段是**并发截断源文件**，但那会先破坏"旧 target 逐字节存活"这条断言的对象本身（源就是被保护对象），并且是竞态 ⇒ 会变成时灵时不灵或静默通过的测试。**因此没有采用，也没有用近似测试冒充。**
3. **catch 里 `:172 backup.delete()` 的清理效果不可稳定证明**：本轮形态下该删除因句柄被持有而失败（这正是"best-effort 清理"的真实限制），而目录形态同样删不掉。⇒ 注释里「避免下次被误当成可用备份」目前是**意图**，不是被证明的性质。
4. 真实 Room 语义、Android 沙箱文件系统、`renameTo` 在 Android 上的行为、断电/SIGKILL：与上一轮审查结论相同，仍全部未覆盖；跨进程恢复设计按指令**不在本轮展开**。

## 6. 可行接缝方案（决定权在属主/Codex，本轮未实施）

**S2（推荐，最小且不改行为）** —— 把"复制"这一步变成可注入的默认参数，签名保持兼容：

```kotlin
// EpubRepository.kt
internal suspend fun replaceStoredEpub(
    stored: BookEntity, selectedUri: Uri, selectedFileName: String,
    copyBackup: (from: File, to: File) -> Unit = { f, t -> f.copyTo(t, overwrite = true) },
) { … :115 改为 copyBackup(target, backup) … }
```

- 测试可用 `copyBackup = { f, t -> t.writeBytes(ByteArray(16)); throw IOException("模拟 ENOSPC") }` ⇒ **确定性留下 16 字节的部分副本**；再用 `{ f, t -> f.copyTo(t); t.setLength(f.length / 2) }`（正常返回但结果短）⇒ **让 `:116` 被执行并断言抛出 `IllegalArgumentException("备份旧副本时文件不完整")` + target 逐字节存活**。
- 代价：一个带默认值的参数（`internal` ⇒ `ShelfImporter` 调用点不需改动，Hilt 不需改动），生产默认路径与现状逐字一致。
- 收益：同时补上 §5.1 / §5.2 / §5.3 三条缺口，并把"清理是 best-effort"变成可断言的性质。

**为什么不选别的路**：
- S1（把 `:115-116` 抽成 `internal` 纯函数直接单测）：测的是代码副本的调用序列，不等于生产路径经过它 —— 属于我上一轮就反对的自证式覆盖。
- S3（把长度校验换成内容哈希校验）：改变产品语义与成本模型，超出"补证据"范围。
- 竞态式 truncate 线程：不可重复，且破坏断言对象（§5.2）。

## 7. 下一轮该做什么（不改变本轮边界）

1. 由 `EpubRepository.kt` 属主决定是否接受 S2；接受则补 2 条用例（部分字节 + 长度不符），并顺手为 `:172` 的清理写出**真实可达**的断言。
2. `ShelfImporter.kt` 的同类缺口（`computeFileHash` 吞异常、缺少阶段判据）属 B 的在途工作，本轮未触碰、未评价。
3. 开局 `backup.delete()` 与跨进程残留恢复：按指令本轮明确**不展开**。

## 8. 边界声明

本轮 Gradle 只跑了 EPUB 定向类（4 次：探针 2 次、变异 1 次、绿相 1 次），未跑全量单测 / Lint / androidTest / APK；未接手机与设备设置；未改总账与他人文件；HEAD 与 staged 未变。文中夹具名均为 `epub_*` 中性标识，不含真实书名与私有路径（临时目录前缀已省略）。
