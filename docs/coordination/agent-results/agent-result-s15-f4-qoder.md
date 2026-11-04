# S15-F4 结果报告（窗口 D / Qoder）— DurationPrefs 状态层真实往返测试

- 任务：S15-F4｜上游：`docs/coordination/investigations/agent-investigation-s15-qoder.md`（F4：状态层缺测）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（唯一工作仓库）
- 分支：`codex/recover-worktree-20260920`
- 时间：2026-09-22 04:44–05:05（本机 +08:00）
- 唯一源码事实：`android/app/src/main/java/com/creationreadingassistant/data/settings/DurationPrefs.kt`（只读，未改）
- 本轮写入的产品测试文件：`android/app/src/test/java/com/creationreadingassistant/data/settings/DurationPrefsTest.kt`（新增；此前不存在）
- 结论：**8 个用例、同一命令连续两次全绿（tests=8 / failures=0 / errors=0 / skipped=0，exit code 0）。未新增依赖、未改 `build.gradle.kts`、未改任何生产文件。**「每用例一个独立 DataStore 文件」这一条在现有生产设计下做不到，已按任务第 10 条如实报告 blocker（第八节），并改用了可验证等价物：类级独立目录 + 每用例显式前置写入 + 收敛式断言。

---

## 一、验证命令与结果（任务【验证】要求的原文）

```
.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.data.settings.DurationPrefsTest" --rerun
```

工作目录：`android/`。这是本轮唯一运行的 Gradle 命令；退出码用 `powershell.exe` 的 `$LASTEXITCODE` 读取（Git Bash 下 `cmd /c` 会假报 0，已知陷阱）。

| 运行 | 退出码 | 构建结果 | 执行的 task | XML 计数 | 用例总耗时 |
|---|---|---|---|---|---|
| 第 1 次全绿（run13，04:57:38Z） | `0` | `BUILD SUCCESSFUL in 18s` | `30 actionable: 4 executed`（仅 test 侧：`kspDebugUnitTestKotlin`、`compileDebugUnitTestKotlin`、`transformDebugUnitTestClassesWithAsm`、`testDebugUnitTest`） | `tests="8" skipped="0" failures="0" errors="0"` | `time="0.557"` |
| 稳定性复跑（run14，05:01:19Z） | `0` | `BUILD SUCCESSFUL in 12s` | `30 actionable: 1 executed`（只有 `testDebugUnitTest`，`--rerun` 生效） | `tests="8" skipped="0" failures="0" errors="0"` | `time="0.503"` |

- 两次运行的 XML 里 `<failure` 出现次数均为 **0**，8 个 `testcase` 名称齐全。
- run14 是真正的第二次执行，不是拷贝：XML `timestamp` 从 `2026-09-21T20:57:38` 变为 `2026-09-21T21:01:19`（UTC），耗时字段也随之从 0.557 变 0.503。
- 两次都**没有**触发主源码重编（`compileDebugKotlin` / `kspDebugKotlin` 为 `UP-TO-DATE`），因此本轮不存在"顺手替窗口 B 编译了一半的代码"这种情况；我也没有检查、恢复或评价窗口 B 的文件。
- 日志与 XML 取证副本：`%TEMP%\d02b_qa\s15f4_run13.log|.xml`、`s15f4_run14.log|.xml`（仓库外）。

`git diff --check`（对测试文件）：

```
$ git diff --check -- android/app/src/test/java/com/creationreadingassistant/data/settings/DurationPrefsTest.kt
（无输出，rc=0）
```

如实说明：该文件是 **untracked**，`git diff --check` 对 untracked 文件按定义不产生任何差异，所以"干净"这个结论不能只靠它。补了一次真正读到内容的检查：

```
$ git diff --no-index --check -- /dev/null android/app/src/test/.../DurationPrefsTest.kt
（无输出，rc=0）
```

即：无行尾空白、无空白错误。

---

## 二、契约 → 用例名映射（任务 1–10 条逐条对账）

| # | 任务契约 | 测试方法名 | 落到什么断言 |
|---|---|---|---|
| 1 | 全新 DataStore 默认 HOURS | ~~`fresh instance exposes hours as its declared default before anyone subscribes`~~ **本行原表述已被 S15-F4-R1 取代** | 原用例只断言了 `stateIn` 的**声明初值**，既不启动订阅也不证明磁盘为空 —— 即使磁盘已经是 DAYS 它也照样通过，因此**不能算「全新 DataStore 默认值」的覆盖**（原报告此处写「磁盘侧的默认由第 8 条和 `setUnit hours…` 用例共同钉住」是高估：第 8 条把磁盘写成脏值文件、并非空白盘，且它自己就是第一个创建该文件的用例）。R1 用 `a0 blank data store default is proven by a real upstream read, not by the stateIn initial value` 重新覆盖了这条契约，见 `agent-result-s15-f4-r1-qoder.md` 第一、二节 |
| 2 | `setUnit(DAYS)` 后重建实例仍读到 DAYS | `setUnit days reaches disk and is read back by newly created instances` | `awaitDiskText("days")` 证明落盘，再用 **两个**新建实例各起一个订阅者、等 `unit.value == DAYS`（初值是 HOURS，非平凡） |
| 3 | `setUnit(HOURS)` 后重建实例仍读到 HOURS | `setUnit hours reaches disk and replaces the previous value` | 先 DAYS 落盘并被写入方实例的 Flow 观察到位，再写 HOURS：磁盘 `contains("hours")` **且** `!contains("days")`（真的替换，不是追加），最后新实例读回 HOURS。该读回的期望值恰等于初值，证明力弱于第 2 条，见第八节 P3-2 |
| 4 | toggle：HOURS → DAYS | `toggle from hours returns days and persists days` | 先写 HOURS 并断言磁盘已无 `days`，`toggle()` 返回值 `== DAYS`，磁盘出现 `days`，新实例读回 DAYS |
| 5 | toggle：DAYS → HOURS | `toggle from days returns hours and persists hours` | 先写 DAYS 落盘，`toggle()` 返回 HOURS，磁盘 `days` 消失，新实例读回 HOURS |
| 6 | 连续 toggle 的返回值与最终持久值一致 | `consecutive toggles agree with the returned unit and the persisted bytes` | 从 HOURS 起连切 6 次：每次校验返回值、磁盘含新值、磁盘不含旧值（无残留双写）、且**同一实例**的 Flow 跟到该值 |
| 7 | 无活跃订阅者时 toggle 仍按持久值切换 | `toggle with no active subscriber follows the persisted value, not the stale flow value` | 写 DAYS 落盘后，先断言 `unit.value == HOURS`（把"没订阅就没加载"这个前提本身测出来，防止将来有人改坏却不报警），再要求 `toggle() == HOURS`（读持久 DAYS → 写 HOURS），再 `toggle() == DAYS` |
| 8 | 未知/脏字符串 → 安全回落 HOURS，不崩溃、不清档 | `aaa_unknown persisted value falls back to hours without crashing or wiping the file` | 手工构造可解析的 protobuf 字节（`duration_unit="Days"` + 哨兵键）落盘 → 冷启动实例得 HOURS → **字节与原写入完全一致**（证明不是"解析失败 → DataStore 默认 RecoveryStrategy 清空"）→ 写 DAYS 后哨兵仍在、脏值 `"Days"` 已被覆盖 |
| 9 | 各测试独立临时 DataStore 文件、可靠清理、不污染用户偏好 | 结构层：`createClassDir` / `destroyClassDir` + 每个用例的前置写入 | 见第三节。诚实结论：**"每个用例一个文件"在现有依赖 + 现有生产设计下不可得**，改成"每个测试类一个独立目录 + 每个用例显式前置状态"；用户真实偏好文件路径（应用 `filesDir/datastore/`）从未被任何用例指向 |
| 10 | 若现有测试依赖做不到，停止并报告 blocker，不加依赖 | 第八节 P3-1/P3-2 | 已定位到精确 blocker（`preferencesDataStore` 委托按**顶级属性**缓存进程级单例，与 Context 无关），**未**新增任何依赖，**未**改 `build.gradle.kts` |

与既有测试的边界：`DurationUnitTest.kt`（他人早前的文件，只读）已经覆盖 `DurationUnit.fromStorage` 的纯映射（含 `"Days"`、`""`、`null`）。本轮**不重复**那些纯枚举断言，只做「过一遍真实 DataStore」的状态层往返；第 8 条故意沿用同一个 `"Days"` 脏值，是为了把同一条输入从"函数返回值"升级到"落盘 → 解析 → 回落 → 再写入不破坏文件"。

---

## 三、临时 DataStore 的创建与清理（第 9 条的具体做法）

**创建**：`@BeforeClass` 里 `Files.createTempDirectory("cra-duration-prefs-test")` 生成**类级唯一**目录，预建 `datastore/` 子目录，再用 mockk 造一个 Context 替身（`filesDir` → 该目录；`applicationContext` → 自身；`getSharedPreferences`/`deleteSharedPreferences` → 复用仓库里已有的 `InMemorySharedPreferences`）。所有 `DurationPrefs` 实例都用这个 Context 构造。落盘路径因此是 `%TEMP%\cra-duration-prefs-test<随机>\datastore\duration_prefs.preferences_pb`，与用户设备上的应用私有目录无关，也**不复用**其他测试类共享的 `TestDataStoreDirs`（`%TEMP%\cra-datastore-tests`）。

**清理**：`@AfterClass` 先取消本类创建的全部真实作用域（每实例一个 `SupervisorJob()+Dispatchers.Default`，登记在同步列表里）和收集用的 `collectScope`，再 `deleteRecursively()`，并 `assertFalse(classDir.exists())` —— 清理失败会让测试变红，不是"静悄悄删掉"。

**实证（跑完之后再看，不靠推断）**：
- `ls %TEMP%\cra-duration-prefs-test*` → `No such file or directory`，无残留。
- run13/run14 之前，`%TEMP%` 下确实留着一个 **03:46 的孤儿目录**（我早前一版测试在编译阶段就被并发窗口打断、`@AfterClass` 没跑完留下的）。本轮已手工删除并复查为空；它在仓库外，不涉及用户数据。
- `ls %TEMP%\cra-datastore-tests\datastore` 仍是 7 个文件：`app_settings / continue_reading / import_history / library_root / search_history / shelf_prefs / sync_prefs`。**没有** `duration_prefs.preferences_pb` —— 说明我的用例没有把 `duration_prefs` 这个委托绑到其他测试类共享的目录上，反向也说明我没去动别人的目录。

**为什么必须类级、不能用例级**：`androidx.datastore` 的 `preferencesDataStore(name)` 委托把实例缓存在**那个顶级属性**上，第一次访问时用当时的 Context 解析出 `filesDir` 并永久绑定。所以"每个用例传一个新目录的 Context"在第二个用例起就是自欺（目录参数被忽略）。这正是我第一版（7/8 失败）踩的坑：用例级临时目录 + `@After` 删除，把委托已经绑定的目录删掉了。

---

## 四、写这份测试时被现实纠正的四个点（都留在代码注释里）

1. **`stateIn` 初值不是"已加载"**：`unit` 用 `SharingStarted.WhileSubscribed(5_000)`，没有订阅者时 `.value` / `.first()` 拿到的是 `DurationUnit.HOURS` 初值。第一版用 `unit.first()` 判"新实例读回 DAYS"，永远不可能成立（`expected:<DAYS> but was:<HOURS>`），而同一时刻磁盘断言是过的 —— 症状很明确。改法：显式起一个订阅者驱动上游，再等值到位。
2. **`runTest` 的 `withTimeout` 是虚拟时间**：第二版把等待写成 `withTimeout(5000){ unit.first{...} }`，5 s 虚拟超时在真实磁盘 IO 之前就先炸了（这就是压缩前最后一次运行的 2 个失败）。改法：真实 `Dispatchers.Default` 作用域 + 真实时间轮询（`Thread.sleep(25)`，上限 10 s）。
3. **轮询不能占住 `runTest` 的测试线程**：改成真实时间轮询后，如果轮询仍在测试主线程上 `Thread.sleep`，DataStore 内部 actor 要把结果回灌给挂在测试调度器上的续体就没人执行 —— 10 s 空等。改法：所有等待都用 `withContext(Dispatchers.IO)` 离开测试线程。
4. **`launch { flow.collect() }` 里 `collect()` 会被解析成 `Job.collect`**：`launch` 的接收者是协程作用域，无参 `collect()` 优先命中 `Job.collect(parent)`，编译报 `No value passed for parameter 'collector'`，且语义完全不是"订阅 Flow"。改成 `prefs.unit.collect { }`（带 lambda 参数才落到 `Flow<T>.collect`）。

---

## 五、git status 前后（共享脏工作区，逐条对账）

**前**（本轮开始、创建测试文件之前，`git status --short`）：与 `data/settings/` 相关的行是

```
 M app/src/test/java/com/creationreadingassistant/data/settings/PerBookSettingsStoreTest.kt
 M ...ReaderSettingsOverlayTest.kt / ReaderSettingsRouterTest.kt / ReadingPresetsTest.kt
 M ...SelectionActionSettingsTest.kt / SettingsStoreTest.kt / ShelfPrefsTest.kt
?? app/src/main/java/com/creationreadingassistant/data/settings/DurationPrefs.kt
?? app/src/test/java/com/creationreadingassistant/data/settings/DurationUnitTest.kt
?? ...ReaderDayNightToggleTest.kt / SettingsStoreBootMirrorTest.kt / SyncPrefsStoreTest.kt
```

且 `data/settings/` 目录列表（04:50）里**没有** `DurationPrefsTest.kt`。

**后**（写入 `agent-result-s15-f4-qoder.md` 之前的实测）：

```
$ git status --short | wc -l
378
$ git status --short -- <测试文件> <DurationPrefs.kt> docs/coordination/
?? android/app/src/main/java/com/creationreadingassistant/data/settings/DurationPrefs.kt
?? android/app/src/test/java/com/creationreadingassistant/data/settings/DurationPrefsTest.kt
?? docs/coordination/
$ git diff --cached --name-only | wc -l
0
```

差量归因：全仓 378 条是多个窗口并行写作的结果，**不能**当作"我造成的改动"来读。可归因于窗口 D 本轮的只有两条：新增 `DurationPrefsTest.kt`、新增本报告。其余条目在本轮开始前已存在（其他窗口继续变化中），我没有清理、也没有改它们。`docs/coordination/` 整个目录是 untracked，所以 `git status --short` 只显示目录一行 —— 这是仓库既有状态，不是我折叠的。

---

## 六、文件哈希（标识"通过的那一份"）

| 文件 | SHA-256 | 大小 | mtime |
|---|---|---|---|
| `android/app/src/test/java/com/creationreadingassistant/data/settings/DurationPrefsTest.kt` | `3dbc983cbaef3ce3b788c27861bd8873f62227d440b3b89a3ac1bfa7ebad1372` | 14561 B | 2026-09-22 04:57:06 +08:00 |
| `android/app/src/main/java/com/creationreadingassistant/data/settings/DurationPrefs.kt`（只读事实源） | `19e09dc1de955fa70c12aded64f1c607625685646c2c19aaec021be1b170df45` | 3298 B | 2026-09-15 23:41:12 +08:00 |

测试文件 mtime 早于 run13 编译时刻，因此上面这份哈希就是跑出 8/8 绿的源码；run14 前未再改动。`DurationPrefs.kt` 的 mtime 停在 09-15、且本轮前后哈希一致 —— 生产文件未被触碰。

---

## 七、契约结论

`DurationPrefs` 的行为与 KDoc 声明一致，没有发现需要生产侧修复的正确性缺陷：默认 HOURS、脏值回落、`toggle()` 重读 DataStore 从而绕开 `WhileSubscribed` 的过期 `StateFlow.value` —— 这条最容易被改坏的语义，现在由第 7 条用例把**前提和结果一起**钉住了。

## 八、是否需要生产侧改动（只报告，不越界、不改）

两条都属于**可测试性**，不是用户可见缺陷，优先级我给到 P3，且都不阻塞本轮交付：

- **P3-1（blocker 本体，任务第 10 条）**：`private val Context.durationPrefsDataStore by preferencesDataStore(name = "duration_prefs")` 是**按顶级属性缓存的进程级单例**，绑定第一次访问时那个 Context 的 `filesDir`。因此在**不改生产代码**的前提下，JVM 侧无法给"同一个属性"开出多个互相独立的 DataStore 文件 —— 现有测试依赖（junit + mockk + kotlinx-coroutines-test + turbine，无 Robolectric）不是瓶颈，瓶颈在生产设计。可选的接缝（**留给集成者决定，我没有做**）：把 `DataStore<Preferences>` 作为构造参数注入（Hilt 里加一个 `@Provides`），或加一个 `internal fun DurationPrefsForTest(context: Context, scope: CoroutineScope)` 工厂。收益：`aaa_…` 之外的用例可以真正做到用例级隔离，`@FixMethodOrder(NAME_ASCENDING)` 这个顺序耦合也能去掉。
- **P3-2（断言强度不对称）**：第 3 条的"新实例读回 HOURS"里，期望值恰好等于 `stateIn` 初值，所以单看这一句即使上游没加载也会成立；它是靠同一用例里的磁盘断言（`hours` 出现且 `days` 消失）才闭环的。反证已放在同一类里：`setUnit days…` 用同一个 helper 期望 DAYS，若上游不加载必失败，说明 helper 本身有效。要让 HOURS 读回也无歧义，同样需要 P3-1 的接缝，或一个 `suspend fun currentUnit(): DurationUnit = DurationUnit.fromStorage(ds.data.first()[KEY_DURATION_UNIT])`（直接读上游，绕开共享状态语义）。**注意**：加这个方法会改变 `DurationPrefs` 的公开面，属产品决策，不在本切片范围内。
- **不报为问题**：`WhileSubscribed(5_000)` + `stateIn` 导致无订阅者时 `.value` 不可信 —— 生产 KDoc 已明确写了这一点，且 `toggle()` 正是为此刻意重读 DataStore；这是已文档化的设计，本轮只是把它变成断言。

## 九、边界与合规声明

- 未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`；全程只在 `creation-reading-assistant-recovery-20260919` 内工作。
- 未 commit、未 push、未 stage、未 stash / reset / checkout / clean；`git diff --cached --name-only | wc -l` = 0（见第五节）。
- 未修改任何生产文件、未修改 `android/app/build.gradle.kts`、未新增任何依赖；未改协调看板 / handoff / 其他窗口的报告。
- 窗口 B 可能同时在改生产时长展示代码：我**没有检查、恢复或评价**其文件；只从 Gradle 输出读到"主编译 UP-TO-DATE"这一事实，用于说明本轮没有代其编译。早前若干次运行被并行窗口的未提交改动打断（`kspDebugKotlin` / `compileDebugKotlin` / `AiClientTest` 报错、`hiltAggregateDepsDebugUnitTest` 缺 `classes.jar`、daemon 被外部 `--stop`、`app/build/test-results` 被并发覆盖），这些都是并发工作区的正常现象，不据此评价他人代码。
- 只运行了任务允许的那一条 Gradle 命令（共 2 次：首绿 + 稳定性复跑）。未运行全量 JVM、单独的主编译、Lint、APK 打包、ADB、真机。
- 唯一新增文件：上表的测试文件 + 本报告。`%TEMP%` 里的日志/XML 副本与类级临时目录都在仓库外，后者已清理并复查为空。
- 数据隐私：全程不触碰用户真实书籍与真实偏好文件；测试只写自己创建的临时目录。

## 十、未完成事项 / 风险（交给集成者）

- 未在全量 `:app:testDebugUnitTest` 下验证与其他测试类共进程时的表现（任务禁止跑全量 JVM）。代码层面的风险已收敛：全仓库只有本类构造 `DurationPrefs`，`duration_prefs` 这个委托属性只有本类会绑定；证据是其他测试类共享目录里没有出现 `duration_prefs.preferences_pb`（第三节）。若将来有人在别的 JVM 测试里构造 `DurationPrefs`，会与本类争抢该委托的单例绑定 —— 那正是 P3-1 接缝要解决的问题。
- 时序余量：轮询上限 10 s，实测两次全量耗时 0.5 s 级；慢盘/CI 冷启动下预期仍安全，但这是"真实 IO 测试"固有的属性，不是零风险。
- 本报告完成即停，不领取下一项。
