# S04 EPUB 替换链路 · 真实 Room 隔离回归测试

- **日期**：2026-09-25
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed5939600e491843e3c44453921bd6273a233c`
- **前置**：`docs/coordination/legacy-stages/2026-09/2026-09-24-s04-epub-fresh-source-fix.md`（修复窗口）；
  `docs/qa/2026-09/2026-09-24-s04-stage-a-evidence-audit.md`（审计窗口）
- **状态**：测试**可编译、已组装进 androidTest APK**；**未在设备上运行**（设备由 D 窗口独占）。
  本报告不把编译成功写成「设备测试通过」。

---

## 0. 开工核对

| 检查 | 结果 |
|---|---|
| 目录 | `/d/develop/Code/Codex/creation-reading-assistant-recovery-20260919` ✓ |
| 分支 | `codex/recover-worktree-20260920` ✓ |
| HEAD | `5aed593…`（与其他 S04 报告一致）✓ |
| 目标报告已存在？ | **否**（本文件为首次撰写）✓ |

---

## 1. 任务动机（审计窗口指出的缺口）

审计报告（`2026-09-24-s04-stage-a-evidence-audit.md` §4）确认：
JVM 版 `EpubRepositoryReplaceTest` 中 DAO 是 **MockK 假对象**，因此三件事
**从未被证明**：

1. `bookDao.update` 在真实 SQLite 上是否触发外键级联（进度行是否物理存活）；
2. `content_hash` / `size` 是否真实写入 SQL UPDATE 的最终列值；
3. 替换失败时真实 Room 是否存在部分写入残留。

同时确认 `androidTest` 中**没有任何** `replaceStoredEpub` 测试（`grep` 无命中）。
本任务补上这一层。

---

## 2. 文件清单

| 文件 | 状态 | sha256 |
|---|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubReplaceRoomIsolatedTest.kt` | **新建** | 初稿 `52faf6a8…`；**现行** `fdc48313755d0fa9bbc2dd0334cc409b7bf88c6131aef41939413a0645764f90`（隔离修正后，6 个方法） |
| `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/IsolatedTestContext.kt` | **新建**（隔离修正新增） | `ac01490d43a364780e726e484c77e3e2d472f343ce4c67614dd2b458d35ad534` |
| `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolated-test.md` | 新建（本报告） | — |

**未改动任何其他文件**：`EpubRepository.kt`（`f3846ef7…`）与 `EpubParser.kt`
（`af223434…`）的哈希与修复窗口交接时完全一致；未提交 SEAM REQUEST（不需要 ——
`EpubRepository` 构造函数是 `@Inject` 公开构造，测试直接以真实参数表构造，
无需任何生产代码配合）。

---

## 3. 测试结构：5 个方法，对应 3 项契约

### 3.1 契约 1 —— 同一来源 URI 的 V1→V2

**`sameUriV1ToV2ReplaceReadsFreshBytesAndKeepsBookIdAndProgress`**

构造方式刻意复刻 S04 根因现场：`openEpub` **不传** `expectedBookId` ⇒ bookId 按
Uri 派生 ⇒ 派生 id == bookId ⇒ 旧实现的「文件已存在就直接沿用」早退必然命中。

断言清单（每条都是真实 Room / 真实字节，不是返回值）：

| # | 断言 | 证明什么 |
|---|---|---|
| 1 | 返回值 `title == "V2全新标题"` | 替换结果来自 V2 |
| 2 | `target.readBytes()` 与 V2 来源 `assertArrayEquals` **逐字节相同**；含 `V2MARKER`、不含 `V1MARKER` | 最终内部正文确实是新字节（§9-1 已修） |
| 3 | `books` 行：`id` 不变、`size == target.length()`、`content_hash ==` 测试独立计算的 MD5（`MessageDigest` 直算，不调 `computeFileHash`）、`local_content_path == target`、`content_status == "available"`、`countActive() == 1` | DB 元数据与实际新文件一致，不新增/软删行 |
| 4 | `reading_progress` 行仍在，`progress_percent == 42.5f`、`current_location_json` 与替换前**逐字段相同** | **真实 Room 的 UPDATE 不触发级联删除**（JVM mock 证明不了） |
| 5 | `.importing` / `.repairing` / `.backup` 全部不存在 | 无操作残留 |
| 6 | `repository.cached(id).title == "V2全新标题"` | 进程内解析缓存已刷新（否则阅读器可能继续显示旧标题） |

### 3.2 契约 2 —— 无效 EPUB

**`invalidEpubReplaceIsRejectedAndPreservesEverything`**

来源写入 5 字节垃圾数据（复制成功、`ZipFile` 必然抛错 —— 走「先落盘后校验」的
解析阶段失败面）：

| # | 断言 | 证明什么 |
|---|---|---|
| 1 | 抛非 AssertionError 的普通异常 | 损坏来源被拒绝 |
| 2 | 旧内部正文 `assertArrayEquals` **逐字节不变** | 旧正文未被破坏 |
| 3 | `books` 行**整行数据类相等**（含 `updated_at` / `content_hash` / `size`） | 真实 Room 无任何推进 |
| 4 | `reading_progress` 行整行相等 | 进度未推进 |
| 5 | `.importing` / `.repairing` / `.backup` 全不存在 | 不留孤儿副本（§9-4） |

### 3.3 契约 3a —— 来源读取失败

**`unreadableSourceIsRejectedWithoutTouchingTarget`**

来源 Uri 指向不存在的文件：

| # | 断言 | 证明什么 |
|---|---|---|
| 1 | 抛 `IllegalStateException` 且消息**逐字**等于「无法读取所选 EPUB 来源，请重新选择文件」 | 新鲜来源模式**绝不**退回「复用旧副本」兜底（那会把旧内容伪装成新来源） |
| 2-4 | 同 3.2 的 2-4 | 旧正文 / books / 进度全部不动 |

### 3.4 契约 3b —— 后置数据库写入失败

**`postSwapDatabaseWriteFailureRollsBackFileAndKeepsOldRow`**

注入方式：`FailingUpdateBookDao` —— `BookDao by real` **类委托**，全部方法转发
真实 DAO，仅 `failUpdates = true` 期间对 `update` 抛 `IOException`。注入点正是
`replaceStoredEpub` 成功路径中**唯一**的 DAO 写入（swap 完成后的那次 update）；
导入（`existing == null` 走 upsert）与进度写入不受影响。

| # | 断言 | 证明什么 |
|---|---|---|
| 1 | 抛出注入的 `IOException`（类型 + 消息匹配） | 注入真的生效、异常未被吞 |
| 2 | target `assertArrayEquals` **逐字节恢复为 V1** | 文件从 backup 回滚，不留半截 V2 |
| 3 | `books` 行保持替换前整行原样 | 真实 Room 无部分写入（失败的 UPDATE 不落任何列） |
| 4 | `reading_progress` 行保持原样 | 进度不受回滚影响 |
| 5 | 无 `.backup` / `.repairing` / `.importing` 残留 | backup 已在回滚中消耗 |

### 3.5 隔离性自证

**`databaseUnderTestIsInMemoryAndNeverTheProductionDatabase`**

`database.openHelper.databaseName != AppDatabase.DB_NAME` —— 运行期钉住测试库
不是正式库（in-memory 库的 helper 名为 null）。

---

## 4. 真实 / 替身边界（引用结论时必须注明）

### 4.1 真实的部分

| 组件 | 说明 |
|---|---|
| **Room + SQLite** | `Room.inMemoryDatabaseBuilder`；`books` / `reading_progress` 全部经真实 DAO、真实 SQL、真实外键（`reading_progress.book_id → books.id ON DELETE CASCADE`） |
| **`EpubParser` 全链路** | 真实 `ZipFile` / container.xml / OPF / spine 解析，无 mock |
| **`ContentResolver`** | 真实 `context.contentResolver.openInputStream` 读 `file://` Uri（与真机导入同一代码路径） |
| **文件系统** | `filesDir/books/epub/` 下 target / `.importing` / `.repairing` / `.backup` 的字节级断言在真实磁盘上 |
| **MD5 交叉核对** | 测试用 `MessageDigest` 独立算 MD5，不调 `computeFileHash` |

### 4.2 替身 / 注入（仅两处，均不改生产语义）

| 替身 | 理由 | 等价性保障 |
|---|---|---|
| `DirectFileOps` | 生产 `DefaultFileOps` 是 `private`，测试无法引用 | 与生产实现逐字同义的两行转发 |
| `FailingUpdateBookDao` | 制造「后置写库失败」 | `BookDao by real` 类委托，只有 `update` 一个方法在开关期间抛错 |

### 4.3 **必须知道的边界：androidTest 进程仍会触碰正式库文件（非本测试所为）**

> ⚠️ **2026-09-25 更正**：本节初稿混淆了三个不同的命题。正确表述见
> `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md`。
> 简述：**测试 DAO 隔离**（成立）、**测试文件隔离**（初稿时不成立，已修正为成立）、
> **目标 App 启动副作用**（不成立、未消除，已 BLOCKED + SEAM REQUEST）。
> 其中「测试文件隔离」在初稿时**并不成立** —— 内部副本写在应用正式
> `files/books/epub/` 下，仅靠「UUID 派生 id 不碰撞」作论据，不构成路径隔离。

androidTest 进程启动时会实例化应用 `App` 类（`@HiltAndroidApp`），其 `onCreate`
的后台线程（`App.kt:150-160`）经 `EntryPointAccessors` 构造 **Hilt 单例
`AppDatabase`** —— 即 `Room.databaseBuilder(…, "creation_reading_assistant_native")`
—— 并执行：

- `EpubSizeRepairTask.startOnce()` → `repairMissingLocalFileSizes()` +
  `backfillMissingEpubCovers()`（只做 `UPDATE … WHERE size <= 0` /
  `cover_data_url IS NULL` 的**选择性修复**，不动正常行）；
- GoalScheduler / SearchIndexScheduler（WorkManager 排期）。

**含义与限定**：

1. 这不是本测试的代码路径；测试自身**没有**任何 `Room.databaseBuilder` 调用。
2. 该行为对**本仓库所有现存 androidTest 一视同仁**（`ChapterReadDaoTest` 等
   7 个既有 in-memory 测试同样如此），不是本测试引入的新风险。
3. 但「androidTest 进程绝不触碰正式库」这句话**不成立**，且**本轮未消除**。
   消除它需要 test manifest / 自定义 runner / build.gradle 改动 ——
   全部超出本窗口所有权，已提交 SEAM REQUEST 并标为 BLOCKED。

### 4.4 其他未覆盖（诚实登记）

| 项 | 原因 |
|---|---|
| **设备上实际运行** | 设备由 D 窗口独占，本窗口不操作真机。5 个方法**在本报告初稿时为 NOT RUN**；后经用户批准于 2026-09-25 执行（见 §5），但那一轮用的是**修正隔离前的旧测试版本**，结论已被 §8 取代 —— 详见 §8 |
| 真实 SAF `content://` Uri | 测试用 `file://` Uri（`Uri.fromFile`）。`content://` 的权限失效路径只在 3.3 用「文件不存在」近似，真实 SAF 权限撤销行为未覆盖 |
| 并发替换 | 未测（D7 沿袭未覆盖） |
| `INSERT OR REPLACE` 级联删除缺陷 | 未直接测（那是 `BookDao.upsert` 已文档化的历史坑，`replaceStoredEpub` 走 update/upsert 不触发；但值得未来补一条「upsert 不级联」的独立测试） |
| 大文件（>32 MiB）`content_hash` 置 null | 未覆盖（合成 EPUB 远小于 32 MiB） |
| 断电 / 进程被杀 | 结构性不可测（沿袭审计 D4） |

---

## 5. 原始运行记录

工作目录 `…/creation-reading-assistant-recovery-20260919/android`，均加 `--offline`。

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin --offline` | **0** | BUILD SUCCESSFUL in 18s |
| 2 | `./gradlew :app:assembleDebugAndroidTest --offline` | **0** | BUILD SUCCESSFUL in 15s；APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 1,528,171 B，sha256 `1b4faaf48f481f14b96a9f9ef97786a6f0b0e6553bee1f9e0d23d164bcacd122`（含清理修复后的最终版） |
| 3 | `./gradlew :app:lintDebug --offline` | **0** | BUILD SUCCESSFUL in 1m 28s（复跑）；`lint-results-debug.txt`：**0 errors, 9 warnings**，9 条 warning 全部与本项目既有问题相关（Compose lint registry API 版本、默认 Locale、依赖版本提示、未用资源），**0 条**指向 `EpubReplaceRoomIsolatedTest.kt` |
| 4 | `./gradlew :app:testDebugUnitTest --tests "…EpubRepositoryReplaceTest" --tests "…EpubParserFreshSourceTest" --offline` | **0** | BUILD SUCCESSFUL（确认我的改动未破坏既有 JVM EPUB 测试） |

**测试方法数：5**（`grep -c "@Test"`，初稿时）。

**设备运行：已于 2026-09-25 在真机执行并全部通过 —— 但用的是修正隔离前的旧版本。**

> ⚠️ **该结论不得再作为当前实现的证据。** 那一轮跑的是把内部副本写进
> 应用正式 `files/books/epub/` 的版本（隔离未生效）。执行记录如下，仅作历史留痕：

设备 `c49ac6cf`（Redmi K50 Ultra，22081212C），`stay_on_while_plugged_in=3` 全程未改。
测试 APK 重装至与本地一致（sha256 `1b4faaf4…`）后执行：

```bash
adb -s c49ac6cf shell am instrument -w -e class \
  com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest \
  com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
```

原始输出：

```
com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest:.....
Time: 0.199
OK (5 tests)
```

退出码 **0**，5/5 通过。**但该版本存在 §4.3 / §6 所述的隔离缺陷**：
「通过」不代表路径隔离成立，也不代表进程级数据安全成立。
修正后的版本（6 个方法）**尚未在设备上运行**，需集成者另行安排，详见 §8。

> 注：instrumentation 组件名是 `com.creationreadingassistant.test/…`
> （非 `…debug.test`，后者会报 `Unable to find instrumentation info`）。

---

## 6. 测试自身的清理纪律（**本节已被 §8 取代，保留以记录更正过程**）

> ⚠️ **2026-09-25 更正**：以下描述的是修正**前**的行为，其核心论据不成立。
> 「bookId 含 UUID ⇒ 不可能碰撞 ⇒ 清理安全」是**概率性**论据，
> 不是路径隔离；且清理当时作用于应用**正式** `files/books/epub/` 目录。
> 现行实现见 `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md`。

修正前的（有缺陷的）描述：

- 来源 EPUB 只写入 `cacheDir/s04-epub-room-isolated-<UUID>/`（每测试唯一），
  `@After` 整目录 `deleteRecursively()`；
- ~~bookId 由含 UUID 的 Uri 派生，**不可能**与真机既有书籍 id 冲突~~
  —— **撤回**：32 位 `hashCode` 无碰撞保证，不得以此代替路径隔离；
- ~~`@After` 只删**已登记 id** 对应的 4 个文件名……~~
  —— **撤回**：删除目标位于应用正式目录，不是测试专属目录；
- in-memory 库 `database.close()` 即消失（**此条仍然成立**）。

---

## 8. 2026-09-25 隔离更正（本报告的现行结论）

完整说明见 `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md`。摘要：

**两个被发现的风险及处置**：

| # | 风险 | 处置 |
|---|---|---|
| 1 | 内部副本写在应用正式 `files/books/epub/`；以「UUID 无碰撞」充当隔离论据；清理作用于正式目录 | ✅ **已修正**：新增 `IsolatedTestContext`，仓储 `filesDir`/`cacheDir` 重定向到唯一测试根目录；写入/清理前经 `assertInsideRoot` 校验绝对路径；新增反证用例 `replaceNeverWritesIntoProductionFilesDirectory` |
| 2 | instrumentation 启动目标 App → `App.onCreate` 后台任务构造**正式** AppDatabase 并跑修复/调度 | ❌ **未消除，BLOCKED**：需改 test manifest / 自定义 runner / build.gradle，超出本窗口所有权 → 已提交 SEAM REQUEST |

**三个命题的正确区分**（不得混同）：

- **A. 测试 DAO 隔离** —— ✅ 成立（in-memory Room）。
- **B. 测试文件隔离** —— ✅ **修正后**成立（此前不成立）。
- **C. 目标 App 启动副作用** —— ❌ 不成立、未消除、本轮 BLOCKED。

**现行状态**：6 个测试方法，`compileDebugAndroidTestKotlin` 与
`assembleDebugAndroidTest` 均 exit 0（APK sha256 `d1845a20…`），lint 0 errors。
**修正后的版本尚未在设备上运行**；「可编译」不等于「设备数据安全已证明」，
也不等于「设备测试通过」。

---

## 7. 遗留 / 建议

1. **D 窗口运行本测试**是判定契约成立与否的唯一途径；建议连同
   `EpubParserInstrumentedTest` 一起跑（同 APK 内）。
2. 若 MIUI 阻断 instrument 仍存在，可退而用「手工 UI 操作 + adb 只读取证」
   （阶段 A 的方式），但那样本测试的 5 条断言只能当**设计核对清单**用。
3. 未来值得补：「`upsert` 不触发级联删除」的独立 DAO 级测试
   （把 `BookDao.upsert` KDoc 里记录的真机事故钉成回归测试）。
