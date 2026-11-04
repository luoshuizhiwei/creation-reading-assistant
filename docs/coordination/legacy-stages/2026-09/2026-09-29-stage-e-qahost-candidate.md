# 阶段 E 候选 G2：qahost 测试基础设施 — 只读核对与候选清单（2026-09-29）

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`。未进入/未修改原仓库。
- 性质：**只读核对**。本轮唯一仓库写入 = 本报告；矩阵与 SHA 在 `E:\creation-reading-assistant\zcode-stage-e-g2-20260929\stage-e-g2-hunk-matrix.csv`（SHA-256 `2914336c1fdcbad589b5a70a7cc872b7d2e054bf6be0be173067bb83ebe595d8`）。未运行 Gradle/ADB/MuMu（窗口 1 独占构建）；未改源码、测试、公共文件或他人报告；未 stage/commit/push/reset/checkout。
- 输入：[四窗报告复核](../../reviews/2026-09/2026-09-29-stage-e-four-window-review.md)、[原 Android hunk 计划](2026-09-29-stage-e-android-hunk-plan.md) G1/G2 节、[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) §E、[D 阶段限定退出复核](../../reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md)、[D 门禁报告](../../../qa/2026-09/2026-09-28-stage-d-build-and-mumu-gates.md)、[V5 复跑报告](../../../qa/2026-09/2026-09-28-stage-d-v5-rerun.md)。
- **计数口径**：一律按 `git diff -U0` 行级 hunk 计数。原 hunk 计划把 `build.gradle.kts` 记为"4 hunks"（把 qahost build type 与 qahostImplementation 合为逻辑 hunk 4）；本轮 `-U0` 实际分出 **5 个行级 hunk**（A–E），两组计数可映射，不构成矛盾，但集成时以本矩阵的 A–E 为准。

## 1. 精确 hunk 清单（`android/app/build.gradle.kts`，当前整文件 SHA-256 `aac93f05…439606`）

| hunk | 旧行 | 新行 | 内容 | 组 | 判定 |
|---|---|---|---|---|---|
| A | — | 14–16 | `testBuildType = providers.gradleProperty("craAndroidTestBuildType").orNull ?: "debug"`（属性门控） | **G2** | 候选，独立可拆 |
| B | 22–23 | 25–30 | `versionCode 2→3`、`versionName "0.5.0"→"0.5.1"` + 0.5.1(3) 注释（指向父表 @Upsert 修复，非 Room v15） | G1 | 不属 G2 |
| C | 25 | 32 | `testInstrumentationRunner = "com.creationreadingassistant.testing.CraIsolatedTestRunner"`（defaultConfig 级） | **G2** | 候选，**必须与三个新文件同提交** |
| D | — | 105–114 | `create("qahost") { initWith(debug); applicationIdSuffix = ".qahost"; signingConfig = debug }` | **G2** | 候选，与 E 同单元 |
| E | — | 243–245 | `add("qahostImplementation", libs.androidx.compose.ui.test.manifest)` | **G2** | 候选，与 D 同单元 |

**HOLD 判定：本文件无不可安全拆开的混合块。** hunk B 与 C 在 `-U3` 下同块相邻，但 `-U0` 下是**互不重叠的不同行**，行级拆分（B→G1、C→G2）可安全实施；G2 范围 = A/C/D/E 四个行级 hunk，与 G1（仅 B）零重叠。

## 2. 三个新增测试源文件（均未跟踪 → add）

| 文件 | 依赖面（imports 全量核对） | SHA-256 |
|---|---|---|
| `androidTest/.../testing/CraIsolatedTestRunner.kt` | 仅 `android.app.Application`、`android.content.Context`、`androidx.test.runner.AndroidJUnitRunner`；**仅覆写 `newApplication`** 换入 CraIsolatedTestApplication，无参数解析/监听器/Instrumentation 代理 | `343b7fb1…a6b074` |
| `androidTest/.../testing/CraIsolatedTestApplication.kt` | 仅 `android.app.Application`；裸 Application（刻意不继承生产 `App`、不装 Hilt、不启动业务线程） | `286b5250…ac9856` |
| `androidTest/.../feature/reader/IsolatedTestContext.kt` | 仅 `android.content.Context(ContextWrapper)`、`java.io.File`；重定向 `filesDir`/`cacheDir`/`applicationContext` + `assertInsideRoot` 规范路径守卫 | `7d97e13b…f099c038` |

**额外依赖核对结论：零新增依赖。** version catalog 的 `androidx-compose-ui-test-manifest` 别名（`android/gradle/libs.versions.toml:61`）在 HEAD 既有 `debugImplementation` 行已在用，hunk E 复用之，**零 catalog 改动**；`androidx.test.runner` 为 HEAD 既有 androidTest 依赖（基线 defaultConfig 本就引用 `AndroidJUnitRunner`）。**manifest 核对：G2 零 manifest 改动**——主 `AndroidManifest.xml` 的 3 个 hunk 全部非 G2（TTS 注释/TTS_SERVICE intent 属 TTS 组、`configChanges` 属 G3）；androidTest 无自定义 manifest（instrumentation 注册由 AGP 按 defaultConfig 生成）；`testing/` 目录恰好只含上述 2 个文件；`gradle.properties` 无 `cra*` 属性（`craAndroidTestBuildType` 仅命令行传入）。

## 3. 行为语义核对（任务三问的直接回答）

1. **`testBuildType` 是否改变默认 debug androidTest？——否。** hunk A 默认值为 `"debug"`，只有显式 `-PcraAndroidTestBuildType=qahost` 时 androidTest 目标才切到 qahost（D 门禁与历次 MuMu 验收正是这样构建的）。不带属性的日常 `test`/`connected*` 行为与基线一致。
2. **`testInstrumentationRunner` 是否改变默认 debug androidTest runner？——是，必须如实登记。** 该行在 `defaultConfig`，对**所有 build type（含 debug）**生效：合入后 debug androidTest 的 runner 从 `AndroidJUnitRunner` 变为 `CraIsolatedTestRunner`，语义差异 = instrumentation 进程的 Application 换成裸 `CraIsolatedTestApplication`（`App.onCreate` 三条 `cra-startup` 接线不执行、不装 Hilt、不写系统夜间模式）。对**当前 androidTest 源集安全的前提已核实**：39 个 androidTest 文件中 0 处真实 Hilt/`EntryPointAccessors`/`createAndroidComposeRule<MainActivity>()`（grep 命中 "Hilt" 的 4 处全是 KDoc 注释，逐条读过）；已知残余（`androidx.startup` 的 `WorkManagerInitializer` 仍会建应用私有 `workdb`）在 KDoc 已登记。**这是有意的行为变更而非纯新增**，提交说明中必须写明，不能按"测试基础设施无行为影响"表述。
3. **`qahostImplementation` 的作用**：`initWith(debug)` 只拷贝构建属性、不继承 `debugImplementation`，缺 hunk E 则 Compose 测试无法在沙箱宿主内启动 `ComponentActivity`——与 D 成对，属 qahost 变体专属，不影响 debug/release。

**原子性（提交形态）**：最小原子单元为 **{C + 三个新文件}**（拆开任一侧则 `compileDebugAndroidTestKotlin` 引用悬空）、**{D + E}**（拆开则 qahost 变体可编译但 Compose 测试运行时缺 Activity）；A 独立可拆但单独提交无意义。按原 hunk 计划口径，**G2 四个 hunk + 三个文件作为单提交**是最简形态；本轮核对支持该形态，无阻碍。

## 4. D 阶段三项证据分别验证了什么（全部为全工作树组合态证据）

| 证据 | 实际内容 | 验证了 G2 的什么 | 没验证什么 |
|---|---|---|---|
| **qahost APK 构建**（D 门禁 G3） | 冻结输入（939 个构建输入哈希）下 `./gradlew :app:assembleDebug :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline` EXIT=0（日志 SHA `3547512b…39a25`，`E:\creation-reading-assistant\codex-d-stage-gates-20260928\`） | G2 hunk A/C/D/E 在**全工作树**可编译、主+测试 APK 可打包 | **拆分后单独编译**（G2-only vs 基线）；设备行为 |
| **V5 62/62**（MuMu qahost 420dpi） | 四类 Compose UI（`ReaderScreenTest`、`MyReadingTimelineRowGestureTest`、`DurationPillGestureTest`、`DurationUnitToggleEntryTest`）：首轮 60/62（测试守卫反向）→ 窄修 → 复跑全量 XML 62/0/0/0（SHA `8d7443a5…12a897`）、测试源码 SHA `cfff5c5c…b9110b` | **`CraIsolatedTestRunner`+`CraIsolatedTestApplication` 的设备行为（qahost 变体）**：Compose 测试经 `ui-test-manifest` 的 ComponentActivity 在裸 Application 宿主内跑通 | debug 变体的设备行为；生产 `MainActivity` 全链（D 复核原话"四类为 Compose/隔离夹具，不等于生产 MainActivity 全链"）；G6 测试文件本身不在 G2 |
| **EPUB Room 8/8**（`EpubReplaceRoomIsolatedTest`） | 当前 qahost 安装（主 APK `e82904cc…4ef8a`、androidTest `fdbd0190…27038d`）下 `am instrument` **OK (8 tests)**、ADB EXIT 0（日志 SHA `b3f87ec4…794d4a9`）；in-memory Room + 专属文件根；运行后 qahost 主 DB 与 49 个既有书/设置文件哈希一致 | **`IsolatedTestContext` 的设备级文件根隔离**与隔离 Room 替换路径，且对宿主既有数据零扰动 | 真实生产 DB、低存储、跨卷 rename、整包原子性（D 复核原话） |

**共同边界**：三者都验证"全工作树组合态"下的 G2，不是"G2 拆分后独立成立"的证据——这正是原 hunk 计划"编译边界：待验证"的含义，本轮零 Gradle 未补（见 §6）。

## 5. 与 G0 / G1 的不重叠证明

- **G1**：`build.gradle.kts` 仅 hunk B；G2 取 A/C/D/E，行级无交集。
- **G0**：G0 文件（`AppDatabase.kt`、`DatabaseModule.kt`、`15.json`、`SearchTermDao.kt` 索引 hunk、`AppDatabaseMigrationTest.kt` 相关 hunk）与 G2 五项资产零交集；G2 三个新文件的 imports 不引用任何 G0 文件，**G2 不依赖 G0**。
- **反向依赖需登记**：`IsolatedTestContext` 的两个代码消费者 `EpubReplaceRoomIsolatedTest.kt`（未跟踪，属 G0 组的 EPUB Room 测试）与 `EpubParserInstrumentedTest.kt`（tracked-M）在 G2 之外——即 **G2 是它们的依赖，不是反向**；这两个文件与 `LocalZipBackupPersistentRestoreTest.kt`（未跟踪，S14 组）对 `CraIsolatedTest*` 的命中全部是 KDoc 文字引用，不构成代码依赖。
- **基线闭包**：HEAD 无 `testing/` 目录（`git ls-tree` 为 0）；G2 单独落在 HEAD 基线上时，androidTest 源集 = HEAD 既有文件 + G2 三文件，编译闭包最小、自包含。

## 6. 候选清单与尚需验证的点

**G2 候选清单（建议单提交；最小原子单元见 §3）**：

1. `android/app/build.gradle.kts` hunk A（14–16）、C（32）、D（105–114）、E（243–245）——排除 hunk B（G1）。
2. 新增 `androidTest/.../testing/CraIsolatedTestRunner.kt`
3. 新增 `androidTest/.../testing/CraIsolatedTestApplication.kt`
4. 新增 `androidTest/.../feature/reader/IsolatedTestContext.kt`

无 HOLD 项：G2 范围内不存在无法安全拆开的混合块。

**尚需验证（本轮只读未做，属窗口 1 构建权）**：

1. **拆分后隔离编译**：在 E 盘隔离构建目录以干净基线 + 仅 G2 候选跑 `:app:compileDebugKotlin` + `:app:compileDebugAndroidTestKotlin` + `assembleQahostAndroidTest`（照四窗复核第 1 条的 G0 同款流程）；全工作树 build 通过不等于拆分后成立。
2. **debug 变体设备行为**：runner 换 Application 对 debug androidTest 的影响只有设计期静态核实（0 Hilt/0 MainActivity 直启），无 debug 变体设备运行证据；V5 全部在 qahost。合入后首次 debug androidTest 轮应抽 1–2 个 Compose 类冒烟并登记。
3. **与 G0/G6 的合入顺序**：G2 先行不破坏基线（§5 闭包成立），但 `EpubReplaceRoomIsolatedTest`/`EpubParserInstrumentedTest`（消费 `IsolatedTestContext`）与 V5 四类（消费 runner）须分别随 G0/G6 合入后其设备证据才继续可复现；在它们合入前，V5 62/62 与 8/8 只能作为组合态历史证据引用，不得写成 G2 独立提交后的可复跑结果。

## 7. 边界声明

- 本轮仅 `git diff`/`git ls-tree`/`git status`/grep/SHA 计算，零构建、零设备命令、零工作树写入（唯一写入 = 本报告与 E 盘矩阵）。
- 所有证据 SHA 均转引自 D 阶段报告与 E 盘原件记录，未重新执行其构建/测试；矩阵另含本轮自算的 4 个当前文件 SHA。
- 未触碰原仓库、真实手机、正式书库；未 stage/commit/push/reset/checkout；未修改他人报告与公共文件。
- 落盘即停，交总指挥复核。
