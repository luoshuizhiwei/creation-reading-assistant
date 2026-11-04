# 阶段 E：验证缺陷修正与测试隔离（2026-09-30）

> **窗口**：阶段 E 验证缺陷修正（G6 语义/选择器 + 联合 JVM 测试隔离）。
> **共享恢复仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`，HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空（开工与收工各核对一次）。
> **只读底座**：`E:\creation-reading-assistant\stage-e-final-joint-20260930\joint\android`（未改动；其 patch SHA `b5365d80b7f23eb9c8ce2010b14a71a0c010a7015d2e8462ffa6c94c1fc62084` 与既有报告一致）。
> **独占候选**：`E:\creation-reading-assistant\stage-e-verification-fix-20260930\android`（复制纯源码，排除 `build` / `.gradle` / `.kotlin`；构建缓存与日志均在本目录 `gradle-home` / `logs` / `artifacts`）。
> **唯一可写共享文件**：本报告。
> **纪律**：**未**改共享恢复仓库源码、其他窗口候选或 READY；**未** stage / commit / push / reset / checkout / stash；**未**迁移或删除目录；**未**操作设备 / MuMu / ADB / 实体手机 / 正式书库 / 原仓库。

---

## 0. 结论先行

| # | 结论 |
|---|---|
| **1** | **G6 的 3/6 失败不是「纯测试夹具缺陷」**：`HomeStat` 的透明定宽占位文本既让 `onNodeWithText` 命中 2 个同文本同坐标节点，也会被**并进卡片合并语义**，使读屏在**同一张卡里把同一个时长朗读两遍**。已按最小产品修正给占位加 `Modifier.clearAndSetSemantics { }`：**只动语义树，不动测量/布局/动画/整卡点击/唯一长按入口**。 |
| **2** | 选择器全部改为「唯一定位 + 断言唯一性」：新增 `assertDurationValueIsUnique`（`onAllNodesWithText(...).assertCountEquals(1)`）与合并语义文本计数断言；**未使用** `onAllNodes(...)[0]` 之类绕过重复节点。 |
| **3** | 新增 **≥24h 受控 Compose 契约测试**：HOURS `72 小时 0 分钟` 与 DAYS `3 天 0 小时` 在真实 UI 里可区分，且真手势长按后界面文案随之切换（不是只改偏好状态）。只用测试夹具，不读写用户数据库。 |
| **4** | `SettingsStoreBootMirrorTest` 的偶发失败**已定位到确定机制并修掉**：`appearance` 是 `stateIn(..., initialValue = 镜像值)`，**StateFlow 会合并相等的值**且 `first { 值谓词 }` 会命中初始值 —— 而镜像回填副作用在**上游 `map` 内部**，于是「等待返回了、回填还没跑」。原失败断言正是 `SettingsStoreBootMirrorTest.kt:146` 的 `hasThemeMode expected:<true> but was:<false>`。 |
| **5** | 修复 = **测试目录/实例隔离 + 资源关闭 + 确定性 await**：`TestDataStoreDirs` 改为**每次 JVM 运行一个独立根目录**并在退出时递归删除；三个 legacy 回填用例与两个冷启动用例改为**订阅上游 + 等可观测副作用**（带超时的真实时间等待，失败信息给出实际镜像状态）。**未**删除共享缓存、**未**盲目重跑、**未**改生产 DataStore。 |
| **6** | 验证：定向 JVM **16/16**；全量 JVM **2467 tests / 0 failures / 0 errors / 0 skipped（连续两次）**；`compileDebugAndroidTestKotlin` 与 qahost host/test APK 构建全部 **EXIT 0**。设备验收**本窗口未跑**，交独立设备窗口。 |

**阶段口径**：A–E 仍为 **4/5（80%）**；本窗口不裁决阶段退出，后续提交仍未授权。

---

## 1. 候选与改动清单

### 1.1 基线

| 项 | 值 |
|---|---|
| 底座 | `stage-e-final-joint-20260930/joint/android`（HEAD + 71 文件联合候选，JVM 2464 tests） |
| 复制方式 | `tar cf - --exclude=build --exclude=.gradle --exclude=.kotlin --exclude=.idea android` → 解包到本窗口目录 |
| 复制后核对 | 候选树内**无** `build` / `.gradle` / `.kotlin` 目录（`find -type d` 为空） |

### 1.2 本窗口改动（5 个文件；逐文件 before/after SHA-256）

| 文件 | 类型 | before（联合底座） | after（本候选） |
|---|---|---|---|
| `app/src/main/java/.../ui/screen/profile/ProfileHomeScreen.kt` | 改（**产品最小语义修正**） | `612c636a4586eff035bf557a0cde7df04cbd56d4ae4fa6ffe152947d246f1bea` | `e295946d697e8ef7165295f66c5ebec29d57d5cec5b08e9ec8e260b62c525de5` |
| `app/src/androidTest/.../ui/screen/profile/ProfileHomeScreenGestureTest.kt` | 改（选择器+断言） | `16bd6c2bd34d1d2eb8264b78845a65b8cde35d2c80eaac7d3d0e2b283981d92a` | `33423f352e1fcb8e1c9810ed48ad334a65d397ed7a86f0c9d177e8ac02eabab7` |
| `app/src/test/java/.../testutil/TestDoubles.kt` | 改（测试目录隔离+资源关闭） | `cb6ac37a0a9a039e046532224c741878adf50756e80a4b40f062bd732c3a65fc` | `e6a52add38621c19540256c2a178158575f23acf8dd1aa772ecfb4edc433a64e` |
| `app/src/test/java/.../data/settings/SettingsStoreBootMirrorTest.kt` | 改（确定性 await + 根因回归用例） | `14780e9e43d5b6079467af375020f84f68634820fe1cb7f275d7ed66aa970fdb` | `adb00853204627c57f6027e738e71f4816e55ce74b6516c0d36f9314eaebba8c` |
| `app/src/test/java/.../ui/screen/profile/ProfileHomeStatPlaceholderSemanticsGuardTest.kt` | **新增**（JVM 结构守卫） | 不存在 | `6c5b6b3ed505edd7661c2a69613f00191f774340ca4f75a8dc707ca25736439d` |

- 可复算补丁（底座 → 本候选）：`artifacts/verification-fix-vs-joint-base.patch`，**5 文件 / 25 hunk / 709 行**，SHA-256 `274e708bc1f691e0bf09ab80310619e8094122cf13d3836d2c2f7ba0ecded143`。
- 生产代码改动**仅 1 处**（`ProfileHomeScreen.kt` 的占位语义），其余 4 个文件都在测试源集；**未**触碰任何 DataStore / SettingsStore 生产实现。

---

## 2. 任务一：G6 语义与选择器修正

### 2.1 失败根因（`HomeStat` 的透明定宽占位）

`ProfileHomeScreen.kt` 的 `HomeStat` 在同一个 `Box(contentAlignment = Center)` 里叠了**两个 Text**：

```kotlin
Text(format(value), color = Color.Transparent, ...)                  // 隐形占位：按最终值定宽
Text(rememberCountUp(value, countUpKey, reducedMotion).let { format(it) }, ...)  // 可见 count-up 值
```

count-up 到达最终值后两者**文本完全相同、bounds 完全相同**（设备原始失败输出：两个节点都是 `l=152,t=324,r=521,b=406`，`Text = '[72 小时 0 分钟]'`）。后果有两层：

1. **自动化层**：`onNodeWithText(时长文本, useUnmergedTree = true)` 命中 2 个节点 → `fetchOneOrThrow` 抛 `Expected exactly '1' node but found '2'`，即 3 条用例（4/5/6）失败。
2. **无障碍层（真实产品缺陷）**：档案卡用 `combinedClickable` 承载整卡手势，会**合并子树语义**；透明占位因此被并进同一份朗读内容，**TalkBack 会把同一个时长朗读两遍**。所以不能仅凭「节点歧义」判定为纯测试缺陷。

### 2.2 最小产品修正

```kotlin
Text(
    format(value),
    style = valueStyle, fontWeight = FontWeight.Bold,
    color = Color.Transparent, maxLines = 1, softWrap = false,
    modifier = Modifier.clearAndSetSemantics { },   // ← 唯一改动
)
```

- **只影响语义树**：`clearAndSetSemantics` 不改变测量、布局、绘制，占位仍按最终值定宽（count-up 过程中槽位不抖动），count-up 动画与整卡 `combinedClickable`（点击=打开阅读档案、长按=切换时长单位）逐字未动。
- **为什么选它而不是 `invisibleToUser()`**：占位是**纯装饰**（不可见、只为测量），应当**没有**语义；`clearAndSetSemantics {}` 语义最直白且是稳定 API。
- **可见值不受影响**：可见 Text 不带任何 `clearAndSetSemantics`，仍是卡片合并语义里的那一次时长朗读。

### 2.3 选择器口径（唯一 + 有语义依据）

- 新增 `assertDurationValueIsUnique(unit)`：`onAllNodesWithText(文本, useUnmergedTree = true).assertCountEquals(1)`。数量不是 1 就**红**，并附注释说明「占位已被 `clearAndSetSemantics` 移出语义树，重复节点不应存在」。
- 所有交互仍走 `onNodeWithText(唯一文本)`（合并树取卡片）与 `onNodeWithText(文本, useUnmergedTree = true)`（未合并树取叶子）。
- **明确不使用** `onAllNodes(...)[0]` / `onAllNodesWithText(...)[0]`：那会把「语义树里有重复朗读源」这一真实缺陷掩盖成通过。

### 2.4 断言清单（8 条仪器用例）

| # | 用例 | 覆盖契约 |
|---|---|---|
| 1 | `click_identity_row_opens_reading_archive` | 整卡点击（身份区） |
| 2 | `click_duration_value_still_opens_reading_archive` | 整卡点击（时长区）+ **唯一性断言** |
| 3 | `click_non_gesture_stat_label_still_opens_reading_archive` | 整卡点击（原可点空白区） |
| 4 | `long_press_duration_value_toggles_exactly_once_and_does_not_navigate` | 长按恰好一次、不导航、不触发灵感 + **唯一性断言** |
| 5 | `inspiration_stat_click_goes_to_inspirations` | 灵感格保留独立入口 |
| 6 | `card_semantics_hold_the_single_toggle_and_leaf_text_holds_none` | 卡片唯一 `OnLongClick`（label=切换时长显示单位）；叶子无手势 |
| 7 | **`invisible_width_placeholder_leaves_semantics_tree_so_duration_is_announced_once`**（新） | 未合并树恰好 1 个时长节点；**卡片合并语义里时长只出现 1 次**（=不重复朗读）；可见值仍可唯一定位且承载唯一长按入口 |
| 8 | **`over_24h_hours_and_days_render_distinguishable_text_and_long_press_switches_unit`**（新） | ≥24h 时 HOURS/DAYS 文案可区分（`72 小时 0 分钟` ≠ `3 天 0 小时`）；真手势长按 → `LocalDurationUnit` 切换 → **界面文案随之变化**；切换后旧文案 0 节点、不导航 |

### 2.5 ≥24h 显示契约（夹具口径）

- 夹具：`totalMinutes = 3 * 24 * 60`（3 天），仅通过 `ProfileUiState(homeSummary = ...)` 注入，**不读写用户数据库**。
- 为什么需要它：窗口 1 的验收报告已如实登记「本设备 qahost 数据总时长不足一天，HOURS 与 DAYS 在文本渲染上**同形**，设备端无法判别单位切换」。本用例把「≥24h 时可判别 + 切换在 UI 上可见」变成可执行契约，弥补该盲区。
- 切换验证方式：`LocalDurationUnit` 接到 `mutableStateOf`，长按回调翻转它 —— 于是断言的是**真实 Compose 重组后的文案**，而不是只断言回调被调用。

### 2.6 为什么另加 JVM 结构守卫

`ProfileHomeScreenGestureTest` 是 androidTest：本窗口**只编译不运行**（设备归独立窗口）。为了让「占位必须退出语义树」这条接线在 JVM 门禁里也守得住，新增 `ProfileHomeStatPlaceholderSemanticsGuardTest`（2 用例）：

1. `invisible_width_placeholder_leaves_semantics_tree`：`HomeStat` 函数体里恰好一个透明占位 Text，且它带 `clearAndSetSemantics`；恰好一个 count-up 可见值，且它**不带**；两者都走同一个 `format` 结果。
2. `card_remains_the_single_gesture_layer`：整卡保留 `onClickLabel="查看阅读档案"` 与带 label 的长按；`HomeStat` 内不得出现 `durationUnitToggle`（父子重复挂会双触发）。

> **边界**：该守卫证明**源码形状**，不证明 Compose 运行期语义树；运行期结论以设备上的 8 条仪器用例为准。

---

## 3. 任务二：`SettingsStoreBootMirrorTest` 偶发失败

### 3.1 复现（原始证据）

**联合候选首轮（既有证据，主证据）**：`stage-e-final-joint-20260930/build/01-testDebugUnitTest.log`
（SHA-256 `aed3d7b7b46a3e35e71a3d68b82eb918b0255acee18cf106be1c031d3e8e4f84`）

```
SettingsStoreBootMirrorTest > legacy datastore default system theme marks mirror as present after first load FAILED
    java.lang.AssertionError at SettingsStoreBootMirrorTest.kt:134
2464 tests completed, 1 failed
```

**本窗口复现（E2）**：全量 JVM 运行，测试 JVM 的 `java.io.tmpdir` 指向一个**尚不存在**的目录（用独占 init script 注入，不触碰全局共享目录）：

```
2464 tests completed, 7 failed        # 日志 logs/E2-full-jvm-run1.log
```
其中 1 条是目标失败，**6 条是 `DatabaseSafetyNetTest` 的 `NoSuchFileException`（`Files.createTempDirectory`）—— 属该注入条件自身的 harness 伪影，不是候选回归**，如实登记。

目标失败的完整栈（`java.io.tmpdir` 缺失那次运行）：

```
java.lang.AssertionError: 首次加载后必须记录镜像已就绪状态 expected:<true> but was:<false>
	at ...SettingsStoreBootMirrorTest$legacy datastore default system theme...invokeSuspend(SettingsStoreBootMirrorTest.kt:146)
	at ...SettingsStoreBootMirrorTest.legacy datastore default system theme marks mirror as present after first load(SettingsStoreBootMirrorTest.kt:134)
```

（`:134` 是 `runTest` lambda 的框架帧，所以 Gradle 摘要显示 134；真正失败的断言在 **`:146`**。）

**未能复现的运行（如实登记）**：BootMirror 单类（`logs/E1-…`，**已执行**，通过）、全量套件 + 预建空目录（`logs/E3-…`，**已执行**，通过）；另有三次**未真正执行**的运行——`E5`（BootMirror 单类 + 已填充目录）与 `E6`（全量套件 + 已填充目录）为 `FROM-CACHE`、`E2b`（重复 E2 条件）为 `UP-TO-DATE`，故 E2 的复现**未被第二次执行验证**。结论：本失败**不是**「某个固定类顺序必现」，而是环境敏感的时序竞态；下表给出确定性的机制证据。

### 3.2 根因（三层，逐层有据）

1. **等待不是同步点（决定性）**：`appearance` 是 `ds.data.map { ... }.stateIn(scope, WhileSubscribed(5000), bootAppearance)`，初始值取自启动镜像。`first { it.themeMode == "system" }`（旧 `:144`）会**立刻命中初始值**并返回，而上游 `map` 里的 `backfillBootThemeIfNeeded` 可能一次都没跑。
2. **StateFlow 合并相等值**：当 DataStore 读出的 `AppearanceSettings` 恰好等于初始值时（默认主题 + 默认调色板正是这种情况），上游**不会**向下游发射。也就是说「下游收到发射」与「上游 `map` 里的回填已发生」本来就是两件事 —— 这正是「dark/light 两个同构用例从不失败、只有 `system` 那个偶发失败」的原因：`dark`/`light` 的期望值 ≠ 初始值 `system`，谓词必须等上游。
3. **共享目录/单例放大**：`TestDataStoreDirs.root` 原本是全机共享的固定目录 `%TEMP%\cra-datastore-tests`（现场实测含 7 个 `.preferences_pb`，来自多个测试类、跨多次运行），而 `preferencesDataStore` 委托把 DataStore 缓存为**进程级单例**并以**首个** Context 的 `filesDir` 落盘。于是 DataStore 首次发射的时机与内容都随「上次运行/别的测试类留下了什么」漂移，进一步放大了第 1、2 层的竞态。

**确定性证据（新回归用例）**：`appearance state flow holds the mirror value while data store holds the authoritative value`
把 DataStore 权威值设为 `dark`、抹空镜像后，断言「未订阅时 `appearance.value.themeMode == "system"`（镜像/默认）**同时** 底层 DataStore 仍是 `dark`」—— 无需任何时序，直接证明**值谓词可能命中的是初始值**；随后用「等副作用」的等待证明正确写法必然等到回填。

### 3.3 修复

**(a) 测试目录隔离 + 资源关闭**（`TestDoubles.kt`，测试基础设施）：

- `TestDataStoreDirs.root` 改为**每次 JVM 运行一个独立目录**：`cra-datastore-tests-<millis>-<uuid8>`，并在 JVM 退出时递归删除（`addShutdownHook` + `runCatching`）。→ 上一次运行的残留不可能再被本次读到；运行结束不留残渣（实测：运行后只剩旧的共享目录，本窗口的一次性目录已被删除）。
- 保留「同一次运行内所有测试共用同一个根」的约束并**写明原因**（委托单例只认首个 `filesDir`）；同时写明**不要**按测试类建目录（那只会造成「测试以为在写 A、DataStore 实际写 B」的假隔离），同运行内的跨类隔离改用「每个测试显式建立并等待自己依赖的状态」。
- 未使用「删除共享缓存」作为修复手段：旧共享目录未被本窗口改动，其它窗口不受影响。

**(b) 确定性等待**（`SettingsStoreBootMirrorTest.kt`）：

- 新增 `awaitAppearanceUpstream(store, expected = null)`：**先显式订阅上游**（`scope.launch { store.appearance.collect { } }`，只保持订阅、不依赖是否发射），再在 `Dispatchers.Default` 上以真实时间轮询「启动镜像已回填」这一**副作用**；10s 超时并给出实际 `hasThemeMode` / `themeMode`，不会静默通过。
- 三个 legacy 回填用例（`dark` / `light` / `system`）与两个冷启动用例全部改用该等待；旧注释里「`appearance.first()` 等磁盘状态加载」的说法一并更正（它等不到，StateFlow 会立刻返回初始值）。
- `tearDown` 的 `scope.cancel()` 保留并补注释：`SettingsStore.init` 的 `migrateLegacyReaderDefaultsOnce` 与 `stateIn` 的 `WhileSubscribed` 上游都在此停止，不带着 mock Context 跨测试方法存活。

**未改生产 DataStore**：`SettingsStore.kt` / `DataStore` 实现零改动；本窗口唯一的产线改动是 §2.2 的占位语义。

---

## 4. 验证矩阵（命令 / 退出码 / 计数 / SHA）

环境：`GRADLE_USER_HOME=E:\creation-reading-assistant\stage-e-verification-fix-20260930\gradle-home`、`JAVA_HOME=D:\develop\Java\jdk-17.0.14`、全部 `--offline --console=plain --max-workers=1`。

| # | 命令 | 退出码 | 结果 | 日志（SHA-256 前缀） |
|---|---|---|---|---|
| 1 | `:app:compileDebugUnitTestKotlin` | **0** | BUILD SUCCESSFUL | `00-compile-unit-test.log` `806f2c7b…` |
| 2 | 定向 `:app:testDebugUnitTest --tests SettingsStoreBootMirrorTest --tests ProfileHomeStatPlaceholderSemanticsGuardTest` | **0** | **16/16**（14 + 2），0 fail/err/skip | `11-targeted-jvm.log` `deb702d8…` |
| 3 | 全量 `:app:testDebugUnitTest`（第 1 次） | **0** | **271 suites / 2467 tests / 0 failures / 0 errors / 0 skipped** | `12-full-jvm-fixed.log` `19c8414d…` |
| 4 | 全量 `:app:testDebugUnitTest --rerun`（第 2 次） | **0** | **271 suites / 2467 tests / 0 failures / 0 errors / 0 skipped** | `16-full-jvm-run2.log` `39043df1…` |
| 5 | `:app:compileDebugAndroidTestKotlin` | **0** | `e:` 计数 **0** | `13-androidtest-compile.log` `6f3b8440…` |
| 6 | `:app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost` | **0** | 双 APK 产出 | `14-assemble-qahost.log` `c48e0fd5…` |

**测试数对照**：底座 2464 → 本候选 **2467**（+1 根因回归用例、+2 JVM 守卫），设备侧仪器用例 6 → **8**。

### 4.1 产物 SHA-256

| 产物 | SHA-256 |
|---|---|
| host APK `app/build/outputs/apk/qahost/app-qahost.apk` | `0ee60574428cc14407104c71437d13d1511e1a480558113723814987efdd920e` |
| test APK `app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk` | `81d57ab2cee75822cfc192c3cc1895775f194c766a760f8411698c717b8ab8b5` |
| 包名 / runner | host `com.creationreadingassistant.qahost`；test `com.creationreadingassistant.qahost.test`；runner `com.creationreadingassistant.testing.CraIsolatedTestRunner` |
| 底座→候选补丁 | `274e708bc1f691e0bf09ab80310619e8094122cf13d3836d2c2f7ba0ecded143` |

**dex 复核**（`artifacts/apk-dex-check/`）：
- test APK `classes6.dex` 含 `ProfileHomeScreenGestureTest`（85 处）、新用例名 `over_24h_hours_and_days_render_distinguishable_text_and_long_press_switches_unit`、`invisible_width_placeholder_leaves_semantics_tree_so_duration_is_announced_once`、辅助 `assertDurationValueIsUnique`；`classes11.dex` 含 `CraIsolatedTestRunner`。
- host APK `classes9.dex` 含 `ProfileHomeScreen` / `HomeStat`；`classes9.dex`、`classes.dex`、`classes19.dex` 均出现 `clearAndSetSemantics` 字符串（修正已进包）。

---

## 5. 未验证 / 边界（不得当作通过）

1. **设备行为未跑**：本窗口零设备操作。8 条仪器用例（含 2 条新增）**只编译入包**，未在 MuMu / 实体手机上执行；「3/6 失败已修」是**代码级 + 编译级**结论，设备 PASS 由独立设备窗口给出。
2. **TalkBack 实际播报**：设备未安装读屏服务（窗口 1 已登记）。「不重复朗读」由**语义树结构**（合并语义文本计数 = 1）+ 源码守卫证明，**不是**实际播报证据。
3. **≥24h 显示差异**：仅 Compose 契约测试覆盖；设备端现有数据不足一天，渲染差异不可见。
4. **release 变体 / 正式 applicationId / 实体手机**：未验（本轮是 qahost/debug 变体）。
5. **任务二复现的环境敏感性**：目标失败未能在「固定类顺序」下稳定复现；机制由确定性用例证明，修复以「消除对上游时序的依赖」方式根除，而非靠重跑。
6. **6 条 `DatabaseSafetyNetTest` 失败**：仅出现在我注入的「`java.io.tmpdir` 不存在」条件下，属 harness 伪影；常规全量运行（第 3、4 行）无此失败。

---

## 6. 交付物与复算

`E:\creation-reading-assistant\stage-e-verification-fix-20260930\`

| 路径 | 内容 |
|---|---|
| `android/` | 独占候选（纯源码；`build` 产物在其内） |
| `artifacts/verification-fix-vs-joint-base.patch` | 底座→候选可复算补丁（5 文件 / 25 hunk） |
| `artifacts/apk-dex-check/` | 双 APK 的 dex 复核（解出的 `classes*.dex`） |
| `logs/00…16*.log` | 编译 / 定向 / 全量×2 / androidTest 编译 / APK 构建日志 |
| `logs/E1…E6*.log` | 任务二复现实验日志（其中 `E5`/`E6` 为 `FROM-CACHE`、`E2b` 为 `UP-TO-DATE`，均**未真正执行**，已在 §3.1 如实标注） |
| `init-test-tmpdir.gradle` | 复现用 init script（把测试 JVM 的 `java.io.tmpdir` 指向私有目录，不改仓库文件） |
| `gradle-home/` | 本窗口独占 Gradle 缓存（自 `stage-e-final-joint-20260930/gradle-home` 复制） |

复算：进入 `android/`，设好 `GRADLE_USER_HOME` / `JAVA_HOME` 后依次执行 §4 的 6 条命令即可；补丁可 `diff -ruN` 重生成。

---

## 7. 纪律自证

- 共享恢复仓库内**唯一写入 = 本文件**；`HEAD` 仍 `513891d6`、暂存区空。
- 未改共享恢复仓库源码/测试/配置、其他窗口候选与 READY、来源联合树（其 patch SHA `b5365d80…` 未变）；未 stage / commit / push / reset / checkout / stash；未迁移或删除目录。
- 未操作设备 / MuMu / ADB；未触碰实体手机、正式包、正式书库、原仓库。
- 未把「编译通过」写成设备通过；未把 `FROM-CACHE`/`UP-TO-DATE` 的空跑写成执行结果；未把 harness 伪影写成候选回归；未用重跑或删缓存冒充修复。
- 全文只用中性标识，未记录任何真实书名。
