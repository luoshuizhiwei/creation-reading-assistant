# S04 EPUB 真实 Room 测试 · 设备数据隔离边界更正

- **日期**：2026-09-25
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支 / HEAD**：`codex/recover-worktree-20260920` / `5aed593`
- **更正对象**：`android/app/src/androidTest/…/EpubReplaceRoomIsolatedTest.kt`
  与其报告 `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolated-test.md`
- **结论**：风险 1（文件隔离）**已修正**；风险 2（App 启动副作用）**BLOCKED + SEAM REQUEST**。
  **未操作真机、未重跑 instrumentation。**

---

## 0. 开工核对

| 检查 | 结果 |
|---|---|
| 目录 / 分支 / HEAD | 一致 ✓ |
| 目标报告是否已存在 | **否** → 首次撰写，非重复施工 ✓ |
| 生产文件是否被我改动 | **否** —— `EpubRepository.kt` `f3846ef7…`、`EpubParser.kt` `af223434…`、`App.kt` `4c51d25e…` 与接手时一致 ✓ |
| gradle / manifest 是否被我改动 | **否** ✓ |

---

## 1. 问题定性：三个命题必须分开

初稿把三件事混成了一句话（「绝不触碰正式库」）。它们**互相独立**，证据强度不同：

| 命题 | 修正前 | 修正后 | 依据 |
|---|---|---|---|
| **A. 测试 DAO 隔离** | ✅ 成立 | ✅ 成立 | 只用 `Room.inMemoryDatabaseBuilder`；文件内无 `Room.databaseBuilder`；运行期断言 helper 名 ≠ 正式库名 |
| **B. 测试文件隔离** | ❌ **不成立** | ✅ **成立** | 见 §2 —— 新增 `IsolatedTestContext` + `assertInsideRoot` + 反证用例 |
| **C. 目标 App 启动副作用** | ⚠️ 已披露但被淡化 | ❌ **未消除，BLOCKED** | 见 §3 —— 需改 test manifest / runner / build.gradle，超出所有权 |

**不得由 A、B 成立推断 C 成立。** A、B 是本测试能自己控制的；
C 是 instrumentation 宿主行为，本测试无论如何写都消除不掉。

---

## 2. 风险 1：文件隔离（已修正）

### 2.1 缺陷（修正前）

`newRepository()` 把 `ApplicationProvider.getApplicationContext()` 直接交给仓储。
而 `EpubRepository` / `EpubParser` 把内部副本路径**硬编码**为：

```kotlin
// EpubRepository.kt:89
val target = File(context.filesDir, "books/epub/${stored.id}.epub")
// EpubParser.kt:67
val epubDir = File(context.filesDir, "books/epub")
```

⇒ 内部副本、`.importing`、`.repairing`、`.backup` 全部落在**应用正式私有目录**
`files/books/epub/` —— 与真机上用户已导入的书**共用同一目录**。

初稿的辩护是「bookId 由含 UUID 的 Uri 派生，**不可能**与既有 id 冲突」。这条**不成立**：
`"epub_" + uri.toString().hashCode().toString(36)` 是 **32 位 hashCode**，
不同字符串碰撞在概率上完全可能（生日界约 2^16 ≈ 65536 个不同 Uri 即可期待一次碰撞）。
即便碰撞概率低，**「低概率」不等于「路径隔离」** —— 这是论证方式的问题，不只是数值问题。
且 `@After` 的清理动作**也作用在正式目录上**，一旦 id 碰撞就会删掉真实用户的书。

### 2.2 修正

**（a）新增 `IsolatedTestContext.kt`** —— `ContextWrapper` 子类，只重定向两个写入根：

```kotlin
override fun getFilesDir(): File = filesRoot.also { it.mkdirs() }  // <testRoot>/files
override fun getCacheDir(): File = cacheRoot.also { it.mkdirs() }  // <testRoot>/cache
```

仓储改为只拿到它，因此 `filesDir/books/epub/…` 解析为
`<testRoot>/files/books/epub/…`，正式目录不再出现在任何写入路径上。

**（b）覆盖范围审查（如实记录边界）** —— 经 `grep` 枚举，
`EpubRepository` / `EpubParser` 可达的 `context` 成员**只有三个**：

| 成员 | 覆盖 | 说明 |
|---|---|---|
| `filesDir` | ✅ | 内部副本 / staging / backup 的唯一写入根 |
| `cacheDir` | ✅ | `EpubParser:110` 的旧 cacheDir 兜底**读取**路径 |
| `contentResolver` | ➖ 委托真实实现 | 该路径**只读**：`openInputStream` / `query` / `openAssetFileDescriptor`；已 grep 确认无 `insert`/`update`/`delete`/`openOutputStream` |
| `getDir` / `getDatabasePath` / `getNoBackupFilesDir` / `getExternalFilesDir` | ➖ 未覆盖 | 在可达路径中**从未被调用**；若将来生产代码改用它们，包装需同步扩展（已写入 KDoc） |

**（c）路径不变量** —— `assertInsideRoot(file)`：
用 `canonicalFile`（解析 `..` 与符号链接）而非 `absoluteFile`，
要求结果等于 root 或以 `root + File.separator` 为前缀 ——
后者是为了避免 `testRoot-evil` 这种同前缀兄弟目录被误放行。
校验失败**抛异常拒绝操作**，而不是照常删除。

**（d）测试侧改造**：

- 每个用例的 `target` 都经 `assertInsideRoot` 取得，并在用前跑
  `assertInsideRootSelfCheck(target)` —— 万一 `IsolatedTestContext` 被改坏，
  用例立刻失败，而不是静默写进正式目录；
- `assertNoResidue` / 清理逻辑统一走 `epubDir = File(isolated.filesDir, "books/epub")`；
- `@After` 改为**只删测试专属根目录本身**（经 `assertInsideRoot` 复核后
  `deleteRecursively()`），不再依据 bookId 去正式目录里逐个删文件；
- 移除 `trackedBookIds`（其存在本身就是「在正式目录里按 id 删文件」的设计痕迹）。

**（e）新增反证用例 `replaceNeverWritesIntoProductionFilesDirectory`** ——
跑完一次完整「导入 V1 → 替换为 V2」后，断言
`appContext.filesDir/books/epub/` 下**不存在任何以该 bookId 命名的文件**。
这是对隔离是否真正生效的端到端反向证明（前几个用例证「在 testRoot 内」，
本用例证「不在正式目录」）。

### 2.3 路径不变量小结

| 路径 | 归属 | 谁写 | 清理 |
|---|---|---|---|
| `<testRoot>/sources/*.epub` | 测试专属 | 测试合成 | 删整个 `<testRoot>` |
| `<testRoot>/files/books/epub/<id>.epub` `…importing` `…repairing` `…backup` | 测试专属 | 被测代码（经隔离 Context） | 同上 |
| `<testRoot>/cache/...` | 测试专属 | 被测代码（经隔离 Context） | 同上 |
| `appContext.filesDir/books/epub/**` | **正式** | **本测试不写**（由反证用例钉住） | **不碰** |

---

## 3. 风险 2：目标 App 启动副作用（BLOCKED + SEAM REQUEST）

### 3.1 事实

`androidTest` 的 instrumentation 会启动**目标应用进程**，因此 `App.onCreate` 一定执行：

```kotlin
// App.kt — onCreate 内，后台线程 "cra-startup"
EntryPointAccessors.fromApplication(this, EpubRepairEntryPoint::class.java)
    .epubSizeRepairTask().startOnce()
EntryPointAccessors.fromApplication(this, GoalSchedulerEntryPoint::class.java).scheduler().start()
EntryPointAccessors.fromApplication(this, SearchIndexEntryPoint::class.java)
    .searchIndexScheduler().start()
```

其中 `EpubSizeRepairTask.startOnce()` 会驱动 **Hilt 单例 `AppDatabase`**，
即 `Room.databaseBuilder(…, AppDatabase.DB_NAME)` —— **正式库** ——
然后执行 `repairMissingLocalFileSizes()` 与 `backfillMissingEpubCovers()`。

已核实的事实（避免夸大或缩小）：这两项只做**选择性修复**
（`UPDATE books SET size=… WHERE id=? AND size<=0`；
以及为 `cover_data_url IS NULL` 的书回填封面），**不删除数据行、不做破坏性写入**。
但「不破坏」不等于「不触碰」；本报告的措辞以「确实打开了正式库并可能写入封面/尺寸字段」为准。

### 3.2 为什么不能在授权范围内消除

要阻止它，必须至少改一处，**全部超出本窗口所有权**：

| 方案 | 需要的改动 | 为何超出所有权 |
|---|---|---|
| 自定义 TestRunner / `HiltTestApplication` | 新增 `androidTest/java/.../CustomRunner.kt` + 改 `testInstrumentationRunner` | 改共享 runner 配置（`build.gradle.kts`） |
| 替换目标 Application | 新增 `androidTest/AndroidManifest.xml`（`tools:replace="android:name"`）—— **当前该文件不存在** | 新增 manifest 影响整个测试源集 |
| Hilt `@TestInstallIn` 替换 `DatabaseModule` | 新增 Hilt 测试模块 | 属测试装配改造，影响其他 androidTest |

本窗口所有权仅限：**该 androidTest 测试 + 必要的 androidTest 内辅助代码 + 报告**。
上述三项都不在其内，故**停止并提交 SEAM REQUEST**（§5）。

### 3.3 当前的正确表述

引用本测试时只能说：

> 该测试的**被测代码路径**不读写正式库（DAO 为 in-memory、文件写入被隔离到
> 测试专属目录）；但 instrumentation **宿主进程**会执行 `App.onCreate` 的后台任务，
> 其中的 `EpubSizeRepairTask` 会打开**正式** `AppDatabase` 并做选择性修复。
> 此进程级副作用**本轮未消除**。

**不得**写成「androidTest 进程绝不触碰正式库」。

---

## 4. 验证结果

工作目录 `…/creation-reading-assistant-recovery-20260919/android`，均加 `--offline`。

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin --offline` | **0** | BUILD SUCCESSFUL in 17s |
| 2 | `./gradlew :app:assembleDebugAndroidTest --offline` | **0** | BUILD SUCCESSFUL in 15s；APK 1,530,586 B，sha256 `d1845a20c192f6e0c82836a102587b6df9236f60a6a26b4a5a6570cb82a7786a` |
| 3 | `./gradlew :app:lintDebug --offline` | **0** | BUILD SUCCESSFUL in 1m 54s；**0 errors, 9 warnings**；9 条 warning 与初稿一致（既有问题），**0 条**指向 `EpubReplaceRoomIsolatedTest.kt` 或 `IsolatedTestContext.kt` |

**静态隔离证明**（可自行 grep 复核，命令与判据一并给出）：

```bash
f=android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubReplaceRoomIsolatedTest.kt
# 排除注释行后应无任何命中（exit=1）
grep -nE "Room\.databaseBuilder|creation_reading_assistant_native" "$f" | grep -vE "^[0-9]+:\s*(\*|//)"
# 代码中唯一的 builder 调用
grep -nE "inMemoryDatabaseBuilder|databaseBuilder" "$f" | grep -vE ":\s*(\*|//)"
```

实测结果：

- `Room.databaseBuilder` / 正式库名字面量：**在代码中 0 次**
  （裸 grep 会命中 3 处，但全部位于 KDoc 注释行，已用上述管道排除）；
- 代码中唯一的 builder 调用是第 107 行 `Room.inMemoryDatabaseBuilder(...)`；
- `context.filesDir` / `context.cacheDir` 在测试文件中出现 **0 次**
  （全部改走 `isolated.filesDir` / `testRoot`）；
- 测试方法数 **6**（原 5 + 反证用例 1）。

**未做**：未重跑 instrumentation、未操作真机、未 commit/push/stage/reset/checkout/clean。

**明确声明**：以上只证明**可编译、可组装、lint 干净、静态隔离成立**。
**不证明**设备上运行通过，**不证明**进程级（命题 C）数据安全。

---

## 5. SEAM REQUEST

```
请求类型：SEAM REQUEST（生产/共享配置边界）
请求方：隔离修正窗口
目标：消除 androidTest 进程启动时对正式 AppDatabase 的触碰（命题 C）

背景：
  androidTest 启动目标 App 进程 ⇒ App.onCreate 一定执行 ⇒ 其后台线程
  （App.kt onCreate，"cra-startup" 线程）经 EntryPointAccessors 构造 Hilt 单例
  AppDatabase 并执行 EpubSizeRepairTask.startOnce() /
  GoalScheduler.start() / SearchIndexScheduler.start()。
  即使测试 DAO 与测试文件均已隔离，正式库仍被打开并可能被选择性修复
  （size 补写、封面回填）。

所需最小接口（三者取一即可，按影响面从小到大）：
  A. 允许新增 androidTest 源集专属的 Hilt 测试模块（@TestInstallIn 替换
     DatabaseModule），并在该模块中把 AppDatabase 指向 in-memory；
     需要：新增文件（在 androidTest 内）—— 若认为这属于「androidTest 内辅助代码」
     则本窗口即可实施，请裁决。
  B. 允许新增 androidTest/AndroidManifest.xml，用 tools:replace 把目标
     Application 换成不执行启动任务的测试 Application；
     需要：新增 manifest（跨测试源集的共享配置）。
  C. 允许新增自定义 TestRunner 并修改 testInstrumentationRunner；
     需要：改 build.gradle.kts（共享构建配置）。

原因：
  当前所有权明确排除「共享 Gradle/runner 配置」与「生产代码」，
  且任务书要求「若要真正消除 App 启动副作用必须改这些边界，提交 SEAM REQUEST
  和最小方案，停止越界实施」。故本窗口不实施，仅登记。
  
影响与风险：
  在方案落地前，任何「androidTest 不触碰正式库」的表述都不成立。
  设备重跑应在本 SEAM 落定、或集成者明确接受该副作用后再安排。
```

---

## 6. 改动文件清单

| 文件 | 状态 | sha256 |
|---|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/IsolatedTestContext.kt` | **新建** | `ac01490d43a364780e726e484c77e3e2d472f343ce4c67614dd2b458d35ad534` |
| `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubReplaceRoomIsolatedTest.kt` | **修改**（隔离改造，5→6 方法） | `fdc48313755d0fa9bbc2dd0334cc409b7bf88c6131aef41939413a0645764f90` |
| `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolated-test.md` | **修改**（更正 §4.3/§4.4/§5/§6，新增 §8） | — |
| `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md` | **新建**（本报告） | — |

**未改动**：`EpubRepository.kt`、`EpubParser.kt`、`App.kt`、`ShelfImporter.kt`、
`app/build.gradle.kts`、`AndroidManifest.xml`、其他 agent 文件。

---

## 7. 仍未覆盖 / 遗留

1. **命题 C 未消除**（见 §3、§5）—— 这是本轮最重要的遗留项。
2. **修正后的 6 个方法未在设备上运行**。设备需集成者另行安排；
   且建议在 SEAM 落定后再跑，否则「通过」仍伴随正式库被触碰的副作用。
3. `contentResolver` 之外的 `context` 成员未覆盖：
   若将来生产代码改用 `getExternalFilesDir` / `getDir` / `getDatabasePath`，
   隔离包装需同步扩展（已在 `IsolatedTestContext` KDoc 中显式登记）。
4. 真实 SAF `content://` Uri 未覆盖（测试用 `file://`），沿袭初稿。
5. 大文件（>32 MiB）、并发替换、断电等沿袭初稿未覆盖项。
