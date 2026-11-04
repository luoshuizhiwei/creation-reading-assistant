# D 阶段 V5 重跑：反向守卫窄修与 7 条 + 62 条设备门禁（2026-09-28）

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`）
- **工件根目录**：`E:\creation-reading-assistant\codex-d-stage-v5-rerun-20260928\`（103 文件，`ARTIFACT-INDEX.txt` 索引自身 SHA-256 `929fba9ae6f5ebe0a5eacc872f2527533712d660270de2e85be95652f57bbb9a`）
- **设备**：MuMu `emulator-5556`（SM-S9280 / Android 12 / SDK 32 / x86_64），验收期 `wm density` override 420
- **唯一修改的源码文件**：`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt`（未跟踪文件）
- **纪律**：未触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`、实体手机、正式书库；未 commit / push / reset / checkout；未改产品代码或其他测试

## 0. 结论先行

| 运行 | 结果 | 退出码 |
|---|---|---|
| **定向 7 条**（`DurationPillGestureTest`） | **PASS — 7 / 0 失败 / 0 错误 / 0 跳过** | 0 |
| **完整 62 条**（V5 四类） | **PASS — 62 / 0 失败 / 0 错误 / 0 跳过** | 0 |
| B5 独立交叉验证（`am instrument`） | **OK (7 tests)** | 0 |

**修正后出现新的产品行为失败：无。** 62 条全部通过，未放宽任何断言（唯一的改动是把断言从「必然失败」改回「按提示语本意执行」，且它**放行后**才让原本从未执行的真实回归检查首次真正跑起来）。

## 1. 第 238 行守卫为何用 `assertFalse(textNodes.isNotEmpty())`

**结论：断言方向写反，写作者本意是 `assertTrue`。三条证据互相印证：**

1. **提示语自证矛盾。** 断言消息是「pill 内应当有展示文本，否则这条断言是空转」——
   「**应当有**」明确表达期望 `textNodes` **非空**；而 `assertFalse(textNodes.isNotEmpty())`
   要求它**为空**。一句话断言了与自己消息相反的命题。
2. **「空转」二字暴露了设计意图。** 这条守卫的存在目的写在消息里：防止
   `textNodes` 为空时后面的 `forEach` 循环**一个用例都不检查、静默通过**（即空转）。
   要达成这个目的，必须在**非空**时放行、`forEach` 才真的去查每个文本节点；
   写成 `assertFalse` 则恰恰相反 —— 它在非空时**直接中止**，循环永不执行。
3. **可观测后果与之一致。** 时长 pill 在生产实现里必然含展示文本，所以 `textNodes`
   恒非空，该守卫**只要执行就必然失败**，且失败点固定在这一行。D 首轮两条失败
   （`session…` / `completedDurationPill…`）完全自洽：它们是本类**仅有的两个**
   调用该辅助函数的用例，本类其余 5 条不经过它故全部通过。

**为何此前没暴露**：该文件是未跟踪新文件（`git status` 显示 `??`），D 首轮是它
**首次在设备上运行**，这个写反的守卫此前从未被触发。

## 2. 最小修正（1 处，1 个 token）

```
android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt:238
-  assertFalse("pill 内应当有展示文本，否则这条断言是空转", textNodes.isNotEmpty())
+  assertTrue("pill 内应当有展示文本，否则这条断言是空转", textNodes.isNotEmpty())
```

| 项 | 值 |
|---|---|
| 修改前文件 SHA-256 | `9d17ebd89a0a342f8e14eb9fec509c540f342ef1e7a82e1a730d625989fc1286` |
| 修改后文件 SHA-256 | `cfff5c5c8f85062e205f3ad6c32558d9f600b1f551e2dddfc87bb59ceab9110b` |
| 改动量 | 1 行 / 1 个 token；**未改消息文本、未改断言对象** |
| `assertTrue` 导入 | 文件第 30 行已有，**无需新增导入** |
| `assertFalse` 是否仍需要 | 是（第 240 行真实回归检查仍用），import 保留 |

**关键含义**：这次修正**不是**把一条会失败的断言改成会通过的断言以凑绿。
它把守卫从「恒失败」改回「按本意放行」，从而让第 239–245 行那条**真正的产品回归检查**
（pill 内带 Text 语义的后代不得自带 OnClick/OnLongClick）**首次真正执行**。
D 首轮该检查的执行次数是 **0**，本轮是 **2**（两条用例各一次）—— 这是本轮
**新取得的产品运行时证据**，此前只有源码级推断。

## 3. 未改动证明

- `git status --porcelain -- android/app/src` 中除 `DurationPillGestureTest.kt` 外，
  其余条目均为**他人既有的未提交修改**，本轮未新增、未修改、未删除。
- 全仓 `grep -rn 'assertFalse.*textNodes'` → **0 处**（确认无遗漏的同类反向守卫）。
- 产品源码（`android/app/src/main`）全量哈希已入快照 `snapshot/input-snapshot.txt`；
  其中两个被测生产组件哈希与 D 首轮一致：
  - `ui/screen/shelf/BookDetailStatsSection.kt` `a1273c68…`
  - `ui/screen/homearchive/HomeCompletedScreen.kt` `3d20d41a…`
- 构建配置哈希与 D 首轮**逐字节一致**（`build.gradle.kts` `aac93f05…`、
  `gradle.properties` `e1c184a7…`、`libs.versions.toml` `bf1f38fb…`、
  `settings.gradle.kts` `16407902…`、`build.gradle.kts` `74edcd10…`）——
  证明本轮 APK 差异**只可能来自**那一行测试改动。

## 4. 构建与安装

### B1 · qahost androidTest APK 构建 — PASS

```
cmd : ./gradlew :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain
cwd : android/
log : logs/B1-assemble-androidtest.log  SHA-256 5bac033df45c662b9c7701eecadcf43fcd22cf997fd237c65e2bc5135cc3be0e
结果: BUILD SUCCESSFUL in 8s  EXIT=0   (52 actionable tasks: 6 executed, 46 up-to-date)
```

| 产物 | SHA-256 | 字节 | 时间戳 |
|---|---|---|---|
| 本轮 `app-qahost-androidTest.apk` | `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d` | 1,664,403 | 2026-09-28 23:50:13 |
| D 首轮（对照） | `b8b3787b2b29ca5fe77e604c20af9b32b310f7956a7420837909c064e9e189e2` | 1,664,412 | 2026-09-28 23:11:36 |

**哈希不同**（预期内：测试源码改了 1 行，字节码随之变化）。
新鲜度核对：`find app/src/androidTest -newermt '<APK 时间戳>'` → **0**，
即无输入晚于产物，`compileQahostAndroidTestKotlin` 确已重编译。

### B2 · 安装 — PASS

```
log : logs/B2-install.log  SHA-256 3c319e670d00eb022ca2f435e56a3719314c99de67c46dcdf010067249d6fece
adb -s emulator-5556 install -r -t app-qahost-androidTest.apk → Success  EXIT=0
instrumentation: com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner (target=com.creationreadingassistant.qahost)
```

安装后行数仍为 **33 / 16**，主库哈希未变（`-r` 保留数据；`leaveApksInstalledAfterRun=true` 全程生效）。

## 5. 定向 7 条 — PASS（7 / 0 / 0 / 0）

```
cmd : ./gradlew :app:connectedQahostAndroidTest -PcraAndroidTestBuildType=qahost \
        -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
        -Pandroid.testInstrumentationRunnerArguments.class=com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest \
        --offline --console=plain
cwd : android/   density: 420 (Override)
log : logs/B3-targeted-7.log  SHA-256 6c24f0d24a1ca91f6e25521d39819058a97a7a4428434425945bf3b93f036286
结果: BUILD SUCCESSFUL in 17s  EXIT=0
```

XML：`xml/B3-targeted-7/TEST-emulator-5556 - 12-_app-.xml`
**SHA-256 `6076c2f5831710fbdb3eafd06888ee2620efc7d41a85de092bf89c9069c5b165`**

```xml
<testsuite name="com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest"
           tests="7" failures="0" errors="0" skipped="0" time="4.595"
           timestamp="2026-09-28T15:51:20" hostname="localhost">
```

| 用例 | 结果 |
|---|---|
| `clickingSessionDurationPillExpandsThenCollapsesDetail` | PASS |
| `longPressingSessionDurationPillTogglesUnitWithoutExpanding` | PASS |
| **`sessionDurationPillPublishesLongPressLabelAndStaysPlainTextInside`** | **PASS（原 FAIL）** |
| `clickingCompletedDurationPillOpensBookExactlyOnce` | PASS |
| `longPressingCompletedDurationPillTogglesUnitWithoutOpeningOrManaging` | PASS |
| `clickingManageButtonStillOnlyOpensTheManagementSheet` | PASS |
| **`completedDurationPillPublishesLongPressLabelAndStaysPlainTextInside`** | **PASS（原 FAIL）** |

两条原失败用例在 XML 中均为**自闭合**（`... />`，无 `<failure>` 子元素），
`<failure` 元素数 = 0。逐用例 logcat 7 份已归档。

## 6. 完整 62 条 — PASS（62 / 0 / 0 / 0）

```
cmd : ./gradlew :app:connectedQahostAndroidTest -PcraAndroidTestBuildType=qahost \
        -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
        -Pandroid.testInstrumentationRunnerArguments.class=<4 个类> --offline --console=plain
cwd : android/   density: 420 (Override)
log : logs/B4-full-62.log  SHA-256 f8082274f9c1792bc727f5407b5adfb8044c3a3359d8c45e1cc9a33493d159a8
结果: BUILD SUCCESSFUL in 36s  EXIT=0
```

XML：`xml/B4-full-62/TEST-emulator-5556 - 12-_app-.xml`
**SHA-256 `8d7443a5ae0bc884518f60e16e17ca256c93468412293d469ba0a61cfd12a897`**

```xml
<testsuite name="com.creationreadingassistant.ui.screen.ReaderScreenTest"
           tests="62" failures="0" errors="0" skipped="0" time="23.091"
           timestamp="2026-09-28T15:53:31" hostname="localhost">
```

| FQCN | 本轮实跑 | 失败 | 错误 | 跳过 |
|---|---|---|---|---|
| `com.creationreadingassistant.ui.screen.ReaderScreenTest` | 37 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest` | 10 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest` | 8 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest` | 7 | 0 | 0 | 0 |
| **合计** | **62** | **0** | **0** | **0** |

- 根元素 `testsuite name` 仍是第一个类名（AGP 8.9 + UTP 聚合 XML 的既定形状），
  故**按 `classname` 属性分组计数**，四类之和 = 62，与源码 `@Test` 数一致。
- `<testcase` 元素数 = 62，`<failure` 元素数 = **0**。逐用例 logcat 62 份已归档。
- 既有的 12 条 Compose 旁证（`PageTurnerHighSpeedTest` 8 + `CraSliderRtlRenderTest` 4）
  **未运行、未计入**（与 D 首轮口径一致）。

### B5 · 独立交叉验证（`am instrument`，不经 Gradle）— OK (7 tests)

```
cmd : adb -s emulator-5556 shell am instrument -w -r \
        -e class com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest \
        com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
log : logs/B5-am-instrument-7.log  SHA-256 7e115edee0c5b9ea5513c176d6eef1f07635774881ba69def0c5b28ec39901d8
结果: Time: 2.85   OK (7 tests)   INSTRUMENTATION_CODE: -1   ADB_EXIT=0
```

`numtests=7`、逐用例 `INSTRUMENTATION_STATUS_CODE: 0` 计 **7** 次、
断言失败码 `-2` 计 **0** 次。用于排除「UTP 包装造成的假绿」，`OK (7 tests)` 与 B3 一致。

## 7. 设备状态与数据对账

### 7.1 密度前后（`device/baseline.txt` / `device/after-restore.txt`）

| 维度 | 验收期 | 事后回读 | 判定 |
|---|---|---|---|
| wm size | `1080x1920` | `Physical size: 1080x1920` | 一致 |
| **wm density** | **420（Override）** | **`Physical density: 280`，无 override** | **已恢复** |
| accelerometer_rotation | `0`（测试期改） | `1` | **已恢复** |
| user_rotation | `0` | `0` | 一致 |
| 夜间模式 | `yes` | `yes` | 未改 |
| stay_on_while_plugged_in | `0` | `0` | 未改 |
| screen_off_timeout | `60000` | `60000` | 未改 |
| `/data/local/tmp` 中 `cra-*` | 0（基线） | **0**（脚本已删） | 已清理 |

> 夜间模式在 D 首轮登记为「会话间漂移」（D 首轮基线 `yes`，而 C 阶段 S05 报告基线 `no`）。
> 本轮**未改动该值**，前后均为 `yes`，未新增漂移；该 D1 项仍未隔离归因。

### 7.2 qahost 数据前后（`device/files-before.sha256.txt` / `files-after.sha256.txt`）

| 项 | 值 |
|---|---|
| 文件数 before / after | **75 / 75**（路径维度 diff **为空**） |
| 主库 SHA-256 before / after | `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` — **逐字节一致** |
| 行数 books / reading_progress | **33 / 16**（前后一致） |
| books 目录 | 22 个子目录 / 36 个文件（前后一致） |
| 测试书 `00000000-0000-4000-8000-000000000012` | **全程未播种**（计数 0） |

**仅 3 项变化**（均为临时/标记文件，非书籍、非设置、非用户数据）：

| 文件 | 说明 |
|---|---|
| `files/profileInstalled`（24 B） | 应用启动重写的标记文件 |
| `no_backup/androidx.work.workdb-shm` | WorkManager 临时文件 |
| `no_backup/androidx.work.workdb-wal` | WorkManager 临时文件 |

**诚实边界**：主库逐字节一致成立；但上述 3 项有变化，故**不写「整包逐字节恢复」**。

## 8. 本轮新取得的产品运行时证据

D 首轮把「pill 内文本运行时不带点击/长按语义」记为 **UNVERIFIED**（守卫先中止，
真实检查执行 0 次）。**本轮该检查首次真正执行并通过**，因此该契约现在有
**运行时证据**（MuMu qahost、420 dpi、SDK 32 范围内）：

- 已读卡 pill（`HomeCompletedScreen.kt:536`）与会话 pill（`BookDetailStatsSection.kt:321`）
  内部带 `SemanticsProperties.Text` 的后代，**均不含** `SemanticsActions.OnClick`
  **也不含** `SemanticsActions.OnLongClick`；
- 手势只挂在 pill 容器上（`Modifier.durationUnitPill` 的 `combinedClickable`），
  长按标签 `DURATION_UNIT_PILL_LONG_PRESS_LABEL` = 「切换时长显示单位」由容器发布。

## 9. 未覆盖与边界（不得外推）

| 项 | 状态 | 说明 |
|---|---|---|
| S05 三项残留 UI 场景 | **NOT RUN** | 不在本轮授权范围；需总指挥另行指派 |
| 实体手机 / MIUI | **NOT RUN** | 全程 MuMu；结论**只称模拟器范围** |
| 真实进程 kill（S14 双 rename 间杀进程） | **NOT RUN** | — |
| 正式书库 / 发布签名 | **未触碰** | 全程 qahost 隔离沙箱 |
| 整包（DB + 书籍文件）原子性 | **未验** | 沿用 C/D 首轮边界，不因本轮门禁关闭 |
| 全量 JVM / Lint / Debug+qahost 主 APK | **本轮未重跑** | 输入未变（构建配置哈希逐字节一致），沿用 D 首轮 PASS 结论；本轮只重编 androidTest |

## 10. Git 纪律

`git diff --cached --stat` 为空；未 commit / push / reset / checkout / clean / 格式化。
本轮唯一改动是未跟踪文件 `DurationPillGestureTest.kt` 的第 238 行；
他人工作树的既有修改与未跟踪文件**全部原样保留**。
