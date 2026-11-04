# D 阶段集中构建与 MuMu 设备门禁报告（2026-09-28）

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`）
- **工件根目录**：`E:\creation-reading-assistant\codex-d-stage-gates-20260928\`（434 个文件，含 `ARTIFACT-INDEX.txt` 全量 SHA-256 + 字节数索引，该索引自身 SHA-256 `858901d9c7c7220ee4dda9d121259a10b9874ad281c4009d65a86040dc9f2bf8`）
- **设备**：MuMu 模拟器 `emulator-5556`（SM-S9280 / Android 12 / SDK 32 / x86_64）
- **所有权**：本轮唯一 Gradle 构建者与唯一 MuMu/ADB 执行者；唯一报告 = 本文件
- **纪律**：未进入/未修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`；未触碰实体手机与正式书库；未执行 commit/push/reset/checkout；未清理他人工作树修改；未改动任何产品源码或测试源码

## 0. 结论先行

**构建门禁全绿；设备门禁停在 V5 失败项，未继续后续场景。**

| 门禁 | 结果 | 退出码 |
|---|---|---|
| G1 全量 JVM 单元测试 | **PASS** — 333 套件 / 3208 tests / 0 failures / 0 errors / 0 skipped | 0 |
| G1-a V4 EPUB 回滚定向 | **PASS** — 21 / 0 / 0 / 0，本轮重新锚定 XML 哈希 | 0 |
| G2 Lint | **PASS** — 0 errors / 9 warnings | 0 |
| G3 APK 构建（Debug / qahost / qahost androidTest） | **PASS** | 0 |
| G3-b Debug 变体 androidTest APK | **PASS** | 0 |
| G4 安装 + 数据保全核对 | **PASS** — 设备侧 APK 哈希与宿主一致，DB 逐字节未变 | 0 |
| G5 V5 62 条 Compose UI 测试 | **FAIL** — 62 条全部执行，**60 PASS / 2 FAIL** | 1 |
| G5-b 失败复现确认 | **确认可复现**（2/2 失败，同一断言行） | 0 |
| S05 三项残留 UI 场景 | **NOT RUN** — 按指令停在失败门禁，未继续 | — |

失败是**测试侧断言写反**，不是产品缺陷；产品侧源码契约成立（§5）。按要求**停在失败门禁**，未为求全绿而改产品代码。

## 1. 候选输入快照（冻结）

- `snapshot/git-status.txt`（SHA `7999858a642b8e84ef1143468a69aede62885f11057c64b1cd63ff7e5b1cb366`）：`git status --porcelain` 共 **541 项** = **260 项已修改（` M`）+ 281 项未跟踪（`??`）**，**暂存区为空**（快照内 `^M `/`^A ` 计数为 0，且 `git diff --cached --stat` 无输出）。既有未提交修改全部原样保留。
- `snapshot/build-inputs-sha256.txt`（SHA `b1ac963918f69c52abbef391872fa1b1018e96c647700081e45ccd906ad57454`）：`android/` 下 **939** 个构建输入逐文件 SHA-256（Gradle 脚本/版本目录/main/test/androidTest/schemas，排除 `build/`、`.gradle/`）。
- `snapshot/build-env.txt`：Gradle 8.9、Kotlin 1.9.23、Launcher/Daemon JVM 17.0.14（`D:\develop\Java\jdk-17.0.14`）、`GRADLE_USER_HOME=D:/develop/env/gradle`。

关键输入哈希（与 C 阶段交叉核对）：

| 输入 | SHA-256 |
|---|---|
| `feature/sync/LocalZipBackup.kt` | `ab78a16e8e34c50a59ada31040278569baaa013f2c1eba6054a675b89997514c`（与 C 阶段退出复核所记**一致**） |
| `feature/reader/EpubRepository.kt` | `4adb16e3181eec2a957d19f6a8e0fc743acf0d9c4f2a11d3e261a5f9f438dbb8` |
| `test/.../EpubRepositoryReplaceTest.kt` | `6b254a751589598f66836d3d4401c38d8532b296e06c04c9185e7ebb2da03dc0` |
| `androidTest/.../LocalZipBackupPersistentRestoreTest.kt` | `ede20f8022ab67bd67c74c5885ca57484c2bf67d4ee41007a2637fad6d078bf0` |
| `app/build.gradle.kts` | `aac93f05a51d801759bfd0a6678a1267f5d34dfb2b64132c0bf4fbeb63439606` |
| `android/gradle.properties` | `e1c184a761255b22ff9428255d8ea6bf9e8a6547820235e055f7b84cb989f4f1` |
| `android/gradle/libs.versions.toml` | `bf1f38fb4a33ceda37f6e23f9bc856887f57c8d52983cac7313d14d871127af4` |
| `android/settings.gradle.kts` | `16407902f65390b4e51a9d38161295fc6346124ca51face06d3973f68136c8a6` |
| `android/build.gradle.kts` | `74edcd10aea399999bf65baac94bd609f9c0ef208bd98bd5421f94619d7eddb2` |

## 2. 门禁逐条原始结果

### G1 · 全量 JVM 单元测试 — PASS

```
cmd : ./gradlew :app:testDebugUnitTest --offline --console=plain
cwd : android/
log : logs/G1-jvm.log   SHA-256 cf20c8066f79176e7331874eaea3e02ed56fcb30baf6be9f760125bd928ed5bd
结果: BUILD SUCCESSFUL in 7m 50s   EXIT=0
     30 actionable tasks: 1 executed, 29 up-to-date
```

由 333 个 XML 实算（`snapshot/G1-summary.txt`，SHA `f2bc983cf5ef55bca086ef3ebb8404b2f93c887bca83becd0160a3ac2971ea11`）：**suiten=333 tests=3208 failures=0 errors=0 skipped=0**。全部 XML 原件在 `xml/G1/`。

新鲜度证据：333 个 XML 的时间戳**全部落在 `2026-09-28T14:49`–`14:56`（UTC）单一 7 分钟窗口内**，无任何早于本轮的历史残留；`hostname` 唯一为 `LUOSHUIZHIWEI`。这佐证运行前已清空 `test-results`、本轮为实跑而非复用旧产物。

### G1-a · V4 EPUB 回滚定向（本轮重新锚定）— PASS

同一次 G1 运行产出的该类 XML（不沿用任何历史哈希）：

| 项 | 值 |
|---|---|
| 类 | `com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest` |
| XML | `xml/G1/TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml` |
| XML SHA-256 | `9062c0d8bb58fa6bc7297f6891a28312beac1354bdb30c95d409d911bb38b7c4` |
| 计数 | `tests="21" skipped="0" failures="0" errors="0"`，`time="3.745"` |
| 内部时间戳 / 主机 | `2026-09-28T14:49:37`（UTC）/ `LUOSHUIZHIWEI` |
| 绑定输入 | `EpubRepository.kt 4adb16e3…` + `EpubRepositoryReplaceTest.kt 6b254a75…` |
| 运行命令 | 见 G1（全量运行，该类在其中实跑；`test-results` 目录运行前已清空以强制重跑） |

**本轮哈希 `9062c0d8…` 既不等于原门禁自报的 `3b0730ae…`，也不等于 C 阶段后次回读的 `75fa140c…`** —— 与「APK/测试 XML 不可复现」的既有结论一致；本报告只以本轮实算值为 D 阶段锚点，不引用历史值。

### G2 · Lint — PASS

```
cmd : ./gradlew :app:lintDebug --offline --console=plain
log : logs/G2-lint.log   SHA-256 9b82c715a14b70441237d082acf19fe0e55701c9226afd9948d52c344f49862e
结果: BUILD SUCCESSFUL in 9m 37s   EXIT=0
```

`xml/G2-lint-results-debug.xml`（SHA `94356dddc59a7c9e846673a1bf0cf9f1d303e5f453fdcd16d85ad6b4105aad4f`）：**issue 9 条，全部 Warning，0 Error** — `GradleDependency` 3、`ObsoleteLintCustomCheck` 3、`DefaultLocale` 2、`UnusedResources` 1。

> 与历史基线「0 errors / 4 warnings」的差异仅为 warning 条数（构建依赖版本、自定义 lint check 过时等既有类别），**无新增 error**。

### G3 · APK 构建 — PASS

```
cmd : ./gradlew :app:assembleDebug :app:assembleQahost :app:assembleQahostAndroidTest \
        -PcraAndroidTestBuildType=qahost --offline --console=plain
log : logs/G3-assemble.log   SHA-256 3547512b4ebca8c0bb39530b1904e43e87b876774fd5d64134534d1100d39a25
结果: BUILD SUCCESSFUL in 1m 7s   EXIT=0
```

| 产物 | SHA-256 | 字节 | 时间戳 |
|---|---|---|---|
| `app-debug.apk` | `b436b764eaa708746ceb82aa3e8e71b3edb529896d597a2d4829138af637a8f2` | 51,715,796 | 23:11:20（本轮 `packageDebug` 实执行） |
| `app-qahost.apk` | `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a` | 52,601,998 | 22:20:54（UP-TO-DATE） |
| `app-qahost-androidTest.apk` | `b8b3787b2b29ca5fe77e604c20af9b32b310f7956a7420837909c064e9e189e2` | 1,664,412 | 23:11:36（`compileQahostAndroidTestKotlin` + `packageQahostAndroidTest` 本轮实执行） |

**输入新鲜度核对（预对账 §2.1 要求）**：
- `find android/app/src -newermt '2026-09-28 22:20:54'` → **空**（无源码输入晚于 qahost APK，故 UP-TO-DATE 合法）；
- `find android/app/src/androidTest -newermt '2026-09-28 23:11:36'` → **空**（测试输入未变）。
- **测试 APK 哈希 `b8b3787b…` 与 C 阶段 S05 报告所记测试 APK 逐字节一致**，交叉印证 androidTest 输入自那时未变。

### G3-b · Debug 变体 androidTest APK — PASS

```
cmd : ./gradlew :app:assembleDebugAndroidTest --offline --console=plain
log : logs/G3b-debug-androidtest.log   SHA-256 68bb002bb623c7ec855aacee096a3746e146ac41e94aafd375c25881af8db995
结果: BUILD SUCCESSFUL in 14s   EXIT=0
产物: app-debug-androidTest.apk  f89b4c7876db5dccdaf27aee26d34749f7b0d42511b1e73f7943ab0f52eb768b  1,585,489 B  时间戳 2026-09-28 23:23:21
```

> 该产物此前为 2026-09-25 的陈旧文件 `fb27ecdccc55dfa850486a0c7ab52581694b9fe6c97948026eac0e79c9186daa`（1,563,407 B），本轮重建刷新。它不参与本轮设备执行（设备用 qahost 变体）。
> 归档处理：E 盘 `apk/app-debug-androidTest.apk` 原为上述陈旧件，已改名为 `apk/app-debug-androidTest.STALE-20260925.apk` 留档，新产物写入 `apk/app-debug-androidTest.apk`（`f89b4c78…`）。两个文件均在 `ARTIFACT-INDEX.txt` 中。

### G4 · 安装与数据保全 — PASS

```
log : logs/G4-install.log   SHA-256 be0a60a5f1d74d05f514e5f7c67868b87d61759ede52757c8c5555f3e762babf
adb -s emulator-5556 install -r -t app-qahost.apk           → Success  EXIT=0
adb -s emulator-5556 install -r -t app-qahost-androidTest.apk → Success  EXIT=0
```

| 核对项 | 基线 | 安装后 | 判定 |
|---|---|---|---|
| 设备侧 qahost APK SHA-256 | 宿主构建 `e82904cc…` | 拉回实算 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a` | **一致** |
| 主 DB SHA-256 | `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` | 同值 | **未变** |
| 行数（books / reading_progress） | 33 / 16 | 33 / 16 | **未变** |

安装方式为 `-r`（保留数据）；执行前已做设备侧 tar 冷备（`/data/local/tmp/cra-d-stage-backup/qahost-data.tar.gz`，214,309,102 B，本地副本 `device/qahost-data-before.tar.gz` SHA `5fa97208529b38890192551bcf874ab219fcdea93e58a250b1cff1d28ae7f586`），收尾时已确认无需回写并删除设备侧副本。

### G5 · V5 62 条 Compose UI 测试 — **FAIL（60 PASS / 2 FAIL）**

```
cmd : ./gradlew :app:connectedQahostAndroidTest -PcraAndroidTestBuildType=qahost \
        -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
        -Pandroid.testInstrumentationRunnerArguments.class=<4 个类> --offline --console=plain
cwd : android/
density: 420（Override，手机竖屏）
log : logs/G5-v5-compose.log   SHA-256 723a9bca6b0b6e17a333f544872e21ffbce5aee3c706e1098b8e1afbc10f21ee
结果: BUILD FAILED in 44s   EXIT=1
```

**FQCN 与测试数（先按源码核实，再以 XML 实跑确认）**——四类包声明与目录不完全对应，以包声明为准：

| FQCN | 源码 @Test | 实跑 tests | fail | err | skip |
|---|---|---|---|---|---|
| `com.creationreadingassistant.ui.screen.ReaderScreenTest` | 37 | 37 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest` | 10 | 10 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest` | 7 | 7 | **2** | 0 | 0 |
| `com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest` | 8 | 8 | 0 | 0 | 0 |
| **合计** | **62** | **62** | **2** | 0 | 0 |

> 说明：`DurationUnitToggleEntryTest.kt` 物理位于 `.../ui/screen/stats/`，但包声明为 `...ui.screen.stats.components`，**协调文档所记 FQCN 正确**，不能按目录改名。

- runner：`com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`（`pm list instrumentation` 实测）。
- 四个类均无 `@RunWith`，走 instrumentation runner + `createComposeRule()`。
- **62 条是本次唯一实跑集合**；既有的 12 条 Compose 旁证（`PageTurnerHighSpeedTest` 8 + `CraSliderRtlRenderTest` 4）**未计入、也未运行**。
- 产物：`xml/G5/TEST-emulator-5556 - 12-_app-.xml`，**XML SHA-256 `3e9b447316a1ad6a1e589f91e38946e4acaa555ace4814613564a8b8d7b01d44`**；逐用例原始 UTP logcat 与 `test-result.pb` 在 `xml/G5/utp-raw/`（共 62 份 per-test logcat）。

### G5-b · 失败复现确认 — 可复现

```
cmd : adb -s emulator-5556 shell am instrument -w -r -e class <两条用例> \
        com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
log : logs/G5b-repro-two-tests.log   SHA-256 63c915e81f09ba7288c80fcaedb9efc9d48a30694cd397bc3755ba4c0509f198
结果: Tests run: 2,  Failures: 2   INSTRUMENTATION_CODE: -1   ADB EXIT=0
```

两次运行（UTP 与 am instrument）失败位置与消息完全一致 → **非偶发**。

## 3. 失败的最小定位

两条失败用例（同一根因）：

| 用例 | 断言点 |
|---|---|
| `DurationPillGestureTest#sessionDurationPillPublishesLongPressLabelAndStaysPlainTextInside` | `DurationPillGestureTest.kt:238`（经 :101 调用） |
| `DurationPillGestureTest#completedDurationPillPublishesLongPressLabelAndStaysPlainTextInside` | `DurationPillGestureTest.kt:238`（经 :155 调用） |

失败消息：`java.lang.AssertionError: pill 内应当有展示文本，否则这条断言是空转`

**根因（测试侧，非产品侧）** —— `assertNoClickSemanticsOnPillText` 的守卫断言写反：

```kotlin
// DurationPillGestureTest.kt:237-245（现状）
val textNodes = collectDescendants(pill).filter { it.config.contains(SemanticsProperties.Text) }
assertFalse("pill 内应当有展示文本，否则这条断言是空转", textNodes.isNotEmpty())   // ← 第 238 行
textNodes.forEach { node ->
    assertFalse(
        "pill 内的文本必须是纯展示，不能自带点击/长按语义：${node.config}",
        node.config.contains(SemanticsActions.OnClick) ||
            node.config.contains(SemanticsActions.OnLongClick),
    )
}
```

- 断言消息说的是「pill 内**应当有**展示文本，否则这条断言是空转」，即**期望 textNodes 非空**；但代码用的是 `assertFalse(textNodes.isNotEmpty())`，**期望 textNodes 为空**。二者互斥。
- pill 在生产实现里必然含展示文本，所以该守卫**只要执行就必然失败**，第 239-245 行的真实回归检查（文本不得自带点击语义）**永远走不到**。
- 该文件是**未跟踪新文件**（`git status` 显示 `?? .../shelf/DurationPillGestureTest.kt`），此前从未在任何设备上运行过（C 阶段记录：62 条 0 项运行），所以这个写反的断言此前从未被触发。
- 两条失败用例是**本类中仅有的两个**调用该辅助函数的用例，与「本类另外 5 条通过」完全自洽。

## 4. 产品侧只读核对（用于判断是否属产品缺陷）

失败不指向产品缺陷。只读源码证据：

| 消费端 | 位置 | 手势挂载 |
|---|---|---|
| 已读卡片时长 pill | `ui/screen/homearchive/HomeCompletedScreen.kt:536`（`Modifier.durationUnitPill(COMPLETED_DURATION_PILL_TAG) { onOpen() }`） | 仅在 **Surface 容器**上 |
| 会话时长 pill | `ui/screen/shelf/BookDetailStatsSection.kt:321`（`Modifier.durationUnitPill(SESSION_DURATION_PILL_TAG) { … }`） | 仅在 **Surface 容器**上 |

两处 pill 内部均为**纯展示 `Text(formatDuration(...))`，未挂 `durationUnitToggle`**（`HomeCompletedScreen.kt:549-556`、`BookDetailStatsSection.kt:330-336`，均带注释「纯展示文本：不再自己挂 durationUnitToggle，否则又会吞掉 pill 的点击」）。`durationUnitPill` 定义在 `BookDetailStatsSection.kt:94`，唯一手势是容器上的 `combinedClickable`。

**诚实口径**：以上是**源码级**证据。由于守卫断言在真实检查之前就中止，**「pill 内文本运行时确实不带点击/长按语义」这一条在本轮没有取得运行时证据，记为 UNVERIFIED**，不得据源码推断写成 PASS。

## 5. 建议的窄修（供总指挥分派，本轮未执行）

| 项 | 内容 |
|---|---|
| 文件 | `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt` |
| 现状 | 未跟踪新文件（`??`），无历史提交，不属任何已交付切片 |
| 改动 | 第 238 行 `assertFalse(...)` → `assertTrue(...)`（一行；`assertTrue` 已在第 30 行导入） |
| 影响面 | 仅该测试类；不动任何产品源码、不动 Gradle/配置 |
| 验收 | 修后重跑该类 7 条（或全 62 条）应 62/0/0/0；届时第 239-245 行的真实回归检查才会首次真正执行，其结论才是产品的运行时证据 |
| 所有权建议 | 归**该测试文件的原始作者窗口**（S15-F3-B1/B2 时长 pill 切片）；本窗口不越界改他人文件 |

**未采取的行动**：没有为了让门禁变绿而修改该断言或产品代码。

## 6. 设备状态与数据前后对账

### 6.1 基线（`device/baseline.txt`，SHA `f64fd321ec090526d644eb187cfb4ec2bb6c017fe83378df478efd83001e2d85`）

`1080x1920` / `Physical density: 280`（**无 override**）/ `user_rotation=0` / `accelerometer_rotation=1` / **`Night mode: yes`** / `stay_on_while_plugged_in=0` / `screen_off_timeout=60000`

> **基线观察（登记，不判缺陷）**：D 阶段开始时设备夜间模式为 **yes**，而 C 阶段 S05 报告的基线为 **no**。该变化发生在两次会话之间，与 C 阶段登记的 D1「S06 E1 全局夜间模式意外翻转、归因未隔离」同类。本轮**未切换主题、未改该值**。

### 6.2 验收期设置与恢复（`device/after-restore.txt`，SHA `d87cea91565e20495cab8f44c3f1475017e183d9761e541637663d90f944726f`）

| 维度 | 验收期 | 事后回读 | 判定 |
|---|---|---|---|
| wm size | `1080x1920` | `Physical size: 1080x1920` | 恢复 |
| wm density | `420`（Override） | `Physical density: 280`（`wm density reset`，无 override） | **恢复** |
| user_rotation | `0` | `0` | 恢复 |
| accelerometer_rotation | `0`（测试期改） | `1` | **恢复** |
| 夜间模式 | `yes` | `yes` | 未改 |
| stay_on_while_plugged_in | `0` | `0` | 未改 |
| screen_off_timeout | `60000` | `60000` | 未改 |
| `/data/local/tmp` 中转 | 冷备 + 哈希脚本 | `grep -c 'cra-'` = **0** | 已清理 |

### 6.3 应用数据前后对账

`device/files-before.sha256.txt`（56 文件，SHA `70ea9dc482ecc41d074305cd807d4206d71dc8e612f1fe496edbc16378159fa0`）与 `device/files-after.sha256.txt`（54 文件，SHA `aa9c660c3884ef20050c4990a45b62ef0d21828ba166495c853f05590670b557`）逐行 diff（`device/after-diff.txt`）：

| 文件 | 变化 | 说明 |
|---|---|---|
| `databases/creation_reading_assistant_native`（1.13 GB 主库） | **不在 diff 中** | **逐字节一致** |
| `databases/…-shm` / `…-wal` | 事后不存在 | 应用停止后 SQLite 清理，属预期 |
| `files/profileInstalled`（24 B） | `31cfa46b…` → `e346f746…` | 应用启动时重写的标记文件（非书籍、非设置） |
| `no_backup/androidx.work.workdb-shm` / `-wal` | 内容变化 | WorkManager 临时文件 |
| `shared_prefs/*`、`files/datastore/*`、`files/books/**`（36 文件）、`no_backup` 其余 | **不在 diff 中** | 逐字节一致 |

- **books 目录项 22 个、书籍文件 36 个，前后一致**；DB 行数 33 / 16 前后一致；测试书 `00000000-0000-4000-8000-000000000012` **全程未播种、不存在**。
- **未清除、未覆盖 qahost 既有数据**：`leaveApksInstalledAfterRun=true` 生效，包与数据全程保留（G5 失败后实测 DB 哈希仍为 `7bd076c6…`、行数 33/16、文件 53 个）。
- 诚实边界：主库**字节一致**成立；但 `-shm/-wal` 与 `profileInstalled`、WorkManager 临时文件有变化，故**不写「整包逐字节恢复」**，只写「主库逐字节一致 + 上述 4 类临时/标记文件变化」。

## 7. 未覆盖与边界（不得外推）

| 项 | 状态 | 说明 |
|---|---|---|
| S05 三项残留 UI 场景（首页「最近读完」横排逐卡、真实读到书末自动 finished、取消读完后再阅读百分比自愈） | **NOT RUN** | 按指令「停在失败门禁」未继续；需总指挥裁决后再排 |
| 「pill 内文本无点击语义」运行时证据 | **UNVERIFIED** | 守卫断言先中止，真实检查未执行（§4） |
| 实体手机 / MIUI | **NOT RUN** | 全程 MuMu 模拟器；`AGENTS.md` 原本要求真机，本轮按用户对 MuMu 的既有授权执行，**结论只称模拟器范围** |
| 真实进程 kill（S14 双 rename 间杀进程） | **NOT RUN** | 本轮未做 kill 注入 |
| 正式书库 / 发布签名 | **未触碰** | 全程 qahost 隔离沙箱 |
| 整包 DB+文件原子性 | **未验** | 不写「原子已修复」；C 阶段既有边界照旧 |
| 桌面端 / 原仓库 | **未触碰** | — |

## 8. 附：本轮未做任何 Git 写操作

`git diff --cached --stat` 全程为空；未 commit / push / reset / checkout / clean / 格式化；他人工作树的 **260 项修改 + 281 项未跟踪**原样保留（含本轮读取过的 `docs/qa/2026-09-28-s1{2,4}-mumu-*.md`）。
