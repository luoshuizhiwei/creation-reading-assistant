# G2 普通 debug runner 范围审查 · 失败路径、最小方案与未验项（2026-09-29，只读）

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；基线 HEAD `5aed5939…a233c`。
- 性质：**只读审查**。仓库内唯一写入 = 本报告；新增 E 盘实验目录 `E:\creation-reading-assistant\stage-e-g2-scratch-20260929\`（AGP DSL 可行性实验，见 §4，未产出任何 APK）。**未操作 MuMu/ADB**；未触碰窗口 1 的隔离目录与既有已验证隔离目录（`stage-e-g2-isolated-20260929` 保持原样仅作引用）；未修改产品源码、测试、`build.gradle.kts` 或任何共享配置；未安装任何正式包；未 stage/commit/push/reset/checkout。
- 输入：[交接入口](../handoff/current.md)（09-29 E 三项隔离复核条目）、[三项隔离验证复核](reviews/2026-09/2026-09-29-stage-e-three-isolation-review.md)（"下一执行边界"第 2 条即本任务）、[G2 候选报告](legacy-stages/2026-09/2026-09-29-stage-e-qahost-candidate.md)、[G2 隔离构建报告](legacy-stages/2026-09/2026-09-29-stage-e-qahost-isolated-build.md)。

## 0. 结论先行

1. **对现有套件：机械安全。** G2 把 defaultConfig 级 runner 改为 `CraIsolatedTestRunner` 后，G2 合入时点 debug 会运行的 HEAD androidTest 集合（26 文件）与当前工作树集合（39 文件）的真实代码 **0 处**依赖 `App`/Hilt/`MainActivity` 直启/WorkManager 测试 API——全部 grep 命中（4 处）均为 G2 自有文件的 KDoc 注释（§3）。"构建成功"不是依据；上面的逐类普查才是。
2. **对语义：这是静默的 debug 行为变更。** debug instrumentation 进程不再执行 `App.attachBaseContext`/`onCreate`——Hilt 图谱、应用级夜间模式、三条 `cra-startup` 后台接线、StrictMode/崩溃处理器全部跳过（§2）。这与全部 pre-G2 debug 设备证据（S01/S02 的 23/23、5/5，当时 runner 仍为 `AndroidJUnitRunner`）的运行语义不同；并**封死未来**需要 Hilt 或 `MainActivity` 的 debug 测试——失败路径真实存在且已定位（`MainActivity` 为 `@AndroidEntryPoint`，`App.kt:27` 为 `@HiltAndroidApp`，裸 Application 下启动即注入失败，§3 末）。
3. **"把 runner 限定到 qahost"不能走 buildType 覆盖**：AGP 8.6.0 Kotlin DSL 的 `BuildType` 不暴露 `testInstrumentationRunner`，实验构建在配置期即失败（`Unresolved reference`，EXIT=1，§4）。
4. **推荐最小方案：runner 自判 target 包**（§5）——只改 G2 所有权内的 `CraIsolatedTestRunner.kt` 一个文件，`build.gradle.kts` 四 hunk 原样保留；qahost 隔离不变，debug/正式包恢复 pre-G2 生产 `App` 语义。debug 变体的运行态验证仍 NOT RUN，最小冒烟与约束见 §5.3/§7。

## 1. 变化的生成配置事实（引用既有证据，未重跑）

`defaultConfig.testInstrumentationRunner = "com.creationreadingassistant.testing.CraIsolatedTestRunner"`（候选 `build.gradle.kts` 行 32）对**所有 build type** 生效。G2 隔离构建已证：不带属性构建的 debug androidTest APK，其 manifest instrumentation = `CraIsolatedTestRunner`、targetPackage = `com.creationreadingassistant`（正式包）；qahost 侧同 runner、target `.qahost`；两 APK 的 dex 均含 Runner/Application 类（`stage-e-g2-isolated-20260929/g2-input-and-build-manifest.txt`，SHA `fcd2d432…59a92`）。

## 2. 被跳过的初始化清单（源码位置）

`manifest:25 android:name=".App"`；`App.kt:27 @HiltAndroidApp`：

| 初始化 | 位置 | 对 androidTest 的实际影响（若跳过） |
|---|---|---|
| `attachBaseContext`：`AppContextHolder` 缓存（`writeFatal` 依赖，null 安全降级）、`ONBOARDING_PREFS` 预热、崩溃处理器 | `App.kt:61-86` | 无崩溃路径；崩溃日志能力缺失 |
| `onCreate`：`AppLog.debugEnabled`、StrictMode（debug）、`forceEnableAppTracing`、`super.onCreate`（**Hilt 图谱**） | `App.kt:88-119` | Hilt 图谱缺失 → 任何 `@AndroidEntryPoint` 组件启动即失败（§3 末） |
| 应用级夜间模式（`BootMirror`） | `App.kt:125-131` | instrumentation 进程内主题回退系统值；androidTest **0 处**使用应用主题类（§3），无现有断言受影响 |
| 三条 `cra-startup` 后台接线：EPUB 修复（正式库文件/DB）、目标调度（WorkManager+通知渠道）、搜索索引（WorkManager+正式库 `search_terms` 写入） | `App.kt:144-165` | fresh 测试设备上正文索引不会被启动建库；对"打开 app 搜正文"类旅程测试是行为差 |

## 3. 失败路径核查（可核查调用方；HEAD = G2 合入时点 debug 会运行的集合）

| 模式 | HEAD 集合（26 文件） | 当前工作树集合（39 文件） | 命中性质 |
|---|---|---|---|
| `createAndroidComposeRule<MainActivity>` | 0 | 1 | `CraIsolatedTestApplication.kt:39` **KDoc**（"0 处"的声明本身） |
| `EntryPointAccessors` / `as App` | 0 | 1 | 同上 KDoc（`:38`） |
| `HiltAndroidRule` / `@HiltAndroidTest` / `@CustomTestApplication` | 0 | 0 | — |
| `ActivityScenario` / `launch<MainActivity>` | 0 | 0 | — |
| WorkManager 测试 API（`WorkManager.getInstance`/`TestInitHelper` 等） | 0 | 0 | 2 处命中均为 KDoc（WorkManagerInitializer 残余登记，`CraIsolatedTestApplication.kt:31`、`EpubReplaceRoomIsolatedTest.kt:57`） |
| 应用主题类断言（`CreationReadingAssistantTheme`/`MaterialTheme`） | 0 | 0 | Compose 测试不经应用级夜间模式 |
| `AppLog.*`/DataStore 生产依赖 | 0 | 0 | DataStore 命中为注释；`LocalZipBackupPersistentRestoreTest.kt:570` 是测试自有 context 包装 |

**判定：现有套件（含 G2 合入时点与当前工作树全集）没有依赖被跳过初始化的代码——G2 不会破坏任何现有 debug 测试。** 这是对"是否破坏"的直接回答；但它依赖"测试集不再长大"这一假设：

**未来失败路径（真实、已定位）**：`MainActivity.kt:53 @AndroidEntryPoint`。Hilt 的 Activity 注入在 `onCreate` 经生成代码把 `applicationContext` 强转为 Hilt 生成的 Application 接口（由 `@HiltAndroidApp` 的 `App` 实现）。instrumentation 进程 Application 换成裸 `CraIsolatedTestApplication` 后，任何未来的 `createAndroidComposeRule<MainActivity>()` / `ActivityScenario.launch(MainActivity)` debug 测试都会在该强转处抛运行时异常——不是构建期能拦截的。

## 4. per-buildType 覆盖方案不可行（实验证据）

- 静态面：AGP 8.6.0 `gradle-api` jar 中 `com/android/build/api/dsl/BuildType.class` 与 internal `BuildType.class` 常量池均 **0 处** `testInstrumentationRunner`（defaultConfig 侧存在——hunk C 本身即证明）。
- **实验**（`E:\creation-reading-assistant\stage-e-g2-scratch-20260929\`，HEAD+G2 基线副本，仅实验改动：defaultConfig 还原 `AndroidJUnitRunner` + qahost 块内加 runner 覆盖）：
  - 命令：`gradlew.bat :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline`
  - 结果：**配置期失败**，`build.gradle.kts:111: Unresolved reference: testInstrumentationRunner`，EXIT=1（日志 `experiment-qahost-override.log`；未产出任何 APK）。
- **结论**：AGP 只从 defaultConfig(+flavor) 合并 runner，无法用 buildType 块把隔离 runner 限定到 qahost。该方案排除。

## 5. 最小、可执行的配置方案（推荐）

### 5.1 方案内容：runner 自判 target 包（唯一改动 = G2 所有权内 1 个文件）

`android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestRunner.kt` 的 `newApplication`（现 `:32-39`）改为：

```kotlin
override fun newApplication(
    cl: ClassLoader?,
    className: String?,
    context: Context?,
): Application {
    val target = context?.packageName.orEmpty()
    return if (target.endsWith(".qahost")) {
        // 沙箱宿主：注入裸隔离 Application，跳过 App.onCreate 全部副作用（S04 设计意图不变）
        super.newApplication(cl, CraIsolatedTestApplication::class.java.name, context)
    } else {
        // 正式包/其他 build type：透传清单声明的 Application，恢复 pre-G2 debug 语义
        super.newApplication(cl, className, context)
    }
}
```

- `build.gradle.kts` 四 hunk **原样保留**（hunk C 保持 defaultConfig = CraIsolatedTestRunner），G2 候选边界、与 G0/G1 的不重叠关系全部不变。
- 生成配置：两变体 manifest 与现有 aapt 证据**逐字段一致**（runner 名与 targetPackage 都不变），无需重新主张任何 manifest 断言；仅 Runner 类的字节码变化。
- 语义矩阵：debug/正式包（target 无 `.qahost` 后缀）→ 生产 `App`（Hilt 图、主题、cra-startup 全部恢复，与 S01/S02 历史设备证据同语义）；qahost → 裸 Application（V5 62/62、EPUB 8/8 的宿主行为不变）；未知的未来 build type 默认生产 `App`——**若新增沙箱类后缀需同步此判断**（登记为维护点）。

### 5.2 为什么不是其他方案

- **维持现状（G2 原样）**：机械安全证据在案（§3），若产品认为 debug androidTest 也应默认隔离可接受；但必须登记两条风险——① debug 语义静默变更（与全部 pre-G2 debug 设备证据不同语义）；② 未来任何 Hilt/MainActivity debug 测试被封死（§3 末路径）。二选一是产品/集成者裁决点，本报告推荐 5.1。
- **defaultConfig 还原且 qahost 不用隔离 runner**：qahost 沙箱内 `cra-startup` 会与测试并发（虽因 applicationId 不触正式包，但违背 S04 启动隔离设计与 V5/EPUB 既有宿主语义），不推荐。

### 5.3 分别构建两种 androidTest 变体验证（方案实施后）

1. 隔离重放（窗口 1 构建权，两条命令即上轮隔离构建原样）：
   - `gradlew.bat :app:assembleDebugAndroidTest --offline` → aapt 复核 debug androidTest manifest 仍为 `CraIsolatedTestRunner` + 正式包 target（应与 `fcd2d432…` 清单记录一致）；
   - `gradlew.bat :app:assembleQahostAndroidTest :app:assembleQahost -PcraAndroidTestBuildType=qahost --offline` → aapt 同上 + qahost 主 APK 版本号仍 0.5.0(2)。
2. 设备冒烟（窗口 1 设备权；本窗口 NOT RUN）：
   - qahost 变体重跑 1 类（如 `DurationUnitToggleEntryTest`）确认分支后隔离行为不变；
   - debug 变体抽 1–2 个 Compose 类（如 `DurationPillGestureTest`）——**只能在专用测试设备/MuMu 上安装 debug 签名包执行，不得触碰用户真实手机、正式书库，不安装生产签名正式包**；观测点：logcat 出现 `应用启动`（`AppLog.i("App",…)`，`App.kt:120`）与 CRA-Boot 行 = 生产 `App.onCreate` 真实执行（debug 语义恢复的直接证据）。

## 6. 未验项（不写 PASS）

1. **debug 变体运行态 = NOT RUN**（本窗口零 ADB/MuMu；§5.3-2 待窗口 1）。在方案 5.1 落地前，连"debug 走生产 App 分支"也只有代码级判断，无运行证据。
2. qahost 分支改动后的重冒烟 = NOT RUN（V5/EPUB 历史证据的宿主行为推定不变，但 Runner 字节码已变，需 1 类重跑确认）。
3. `context` 参数在 `newApplication` 回调中的取值（target app context）基于框架文档与现有 62/62 运行事实推断，方案 5.1 的分支判断应以冒烟 logcat 最终确认。
4. 若未来合并 G6/UI 或新功能组引入 `createAndroidComposeRule<MainActivity>` 类测试，须按 §3 末路径重新评估（方案 5.1 下 debug 已恢复生产 App，风险解除；qahost 上此类测试仍不可用——与 S04 设计一致，非缺陷）。

## 7. 边界声明

- 本轮只读 + 一个新建 E 盘实验目录（HEAD+G2 基线副本 + 实验 hunk，配置期失败、零 APK 产物）；既有已验证隔离目录与窗口 1 目录零触碰；未修改 `build.gradle.kts`、产品源码、测试或任何共享配置；未操作 MuMu/ADB；未安装任何正式包；未 stage/commit/push/reset/checkout。
- 报告不含真实书名；所有 jar 常量池/DSL 面检查基于本地 Gradle 缓存的 AGP 8.6.0 原件。
- 落盘即停，交总指挥复核。
