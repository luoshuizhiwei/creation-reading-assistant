# Android 正文残留集成门禁 · 报告（2026-09-24）

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **HEAD**：`5aed5939600e491843e3c44453921bd6273a233c`（全程未变）
- **角色**：只做集成验证与报告。**未修改产品源码或测试源码**，未 commit / push / stage / reset / checkout / clean。
- **未安装、未启动应用；未接真机、未碰用户书库。**

## 0. 等待 B 完成的判定依据

任务要求「先等待窗口 B 完成提示文案及定向测试，确认其改动已落盘后再开始」。我的确认过程：

| 检查项 | 结果 |
|---|---|
| B 的改动是否已落盘 | ✅ 生产码 `ShelfImporter.kt` / `EpubRepository.kt` mtime `15:22:46`；测试 `ShelfImporterTest.kt` `15:18:03`、`EpubRepositoryReplaceTest.kt` `15:18:14` |
| 提示文案是否已改成新形态 | ✅ 两处守卫均改为「检测到**未完成的**替换残留……**重复重试不会自动恢复该备份**」，并附注释说明「不得暗示确认内容后再重试即可、不得引导用户删书或清备份」 |
| 定向测试是否与新文案同步 | ✅ 断言已更新为 `contains("未完成的替换残留")` + `contains("重复重试不会自动恢复")` |
| B 是否已停手 | ✅ 开始门禁前 5 分钟内 `android/app/src` 无任何写入；Gradle daemon 最后一次构建结束于 `15:22:46`，此后无构建活动 |
| 是否有其他窗口在跑 Gradle | ✅ 无。3 个 `java.exe` 中 2 个是**空闲**的 Kotlin 编译守护进程（`kotlin-compiler-embeddable`），1 个是空闲 Gradle daemon（pid 38692）。我在**单个串行会话**里依次执行 4 项，未与其他窗口并发 |

**门禁期间输入未被改动（事后校验）**：

| 文件 | 门禁开始基线 | 门禁结束后 | 一致 |
|---|---|---|---|
| `feature/library/ShelfImporter.kt` | `de1ea2bc2523ce56` | `de1ea2bc2523ce56` | ✅ |
| `feature/reader/EpubRepository.kt` | `dd52027f877aa566` | `dd52027f877aa566` | ✅ |
| `android/app/src` 与 `android/benchmark/src` | — | 门禁时段内**零写入** | ✅ |

⇒ **4 项门禁的输入是同一次源码状态**，结论可归因到 B 的 `de1ea2bc…` / `dd52027f…` 这一版。

---

## 1. 门禁结果总览

| # | 项 | 原始命令 | 退出码 | 判定 |
|---|---|---|---|---|
| 1 | 单元测试 | `./gradlew :app:testDebugUnitTest --offline` | **0** | ✅ **PASS** |
| 2 | Lint | `./gradlew :app:lintDebug --offline` | **0** | ✅ **PASS**（0 errors / 9 warnings） |
| 3 | 主 APK | `./gradlew :app:assembleDebug --offline` | **0** | ✅ **PASS** |
| 4 | 测试 APK | `./gradlew :app:assembleDebugAndroidTest --offline` | **0** | ✅ **PASS** |
| 5 | 空白检查 | `git diff --check` | **2** | ❌ **FAIL**（见 §5，与 B 的改动无关） |

**四项 Gradle 门禁全部通过；`git diff --check` 未通过。** 详见 §5 的归因。

### 1.1 单元测试（第 1 项）

- 命令：`./gradlew :app:testDebugUnitTest --offline`
- 退出码：**0**，`BUILD SUCCESSFUL in 5m 28s`（15:40:36 → 15:46:04）
- 测试 XML 汇总（`app/build/test-results/testDebugUnitTest/`，323 个 suite，**复制于 15:51:31**）：

```
TOTAL tests=3048  failures=0  errors=0  skipped=0
SUITES=323
FAILING SUITES: none
```

**B 的残留相关用例（逐条确认已执行且通过）**：

| 测试类 | 用例数 | 残留相关用例 |
|---|---|---|
| `EpubRepositoryReplaceTest` | 10（0 失败） | ✅ `refuses and preserves target when a backup directory residue exists`<br>✅ `refuses residue and never promotes backup when target is missing`<br>✅ `succeeds and leaves no residue when no backup exists`<br>✅ `keeps existing target when staging copy fails before backup`<br>✅ `restores original target bytes from backup when post-swap update fails` |
| `ShelfImporterTest` | 50（0 失败） | ✅ `txt repair stops when a same-name residue exists under a newly chosen file name`<br>✅ `txt repair stops and keeps residue when backup exists and target is missing`<br>✅ `txt repair stops and keeps both files when target and backup both exist`<br>✅ `txt repair succeeds normally when no residue exists` |

⇒ 上一轮审计里「守卫会否让用户卡死」所对应的**文案修复**，其定向断言（「重复重试不会自动恢复」）**已在本轮真实执行并通过**。

### 1.2 Lint（第 2 项）

- 命令：`./gradlew :app:lintDebug --offline`
- 退出码：**0**，`BUILD SUCCESSFUL in 5m 11s`（15:51:40 → 15:56:51）
- 报告：`app/build/reports/lint-results-debug.{xml,txt,html}`（复制于 15:57:38）

```
TOTAL issues = 9
  Error   = 0
  Warning = 9
```

**9 条警告全部为既有 / 环境性，无一条涉及残留改动**：

| 数量 | id | 内容 |
|---|---|---|
| 3 | `ObsoleteLintCustomCheck` | androidx compose/lifecycle 的 lint.jar 需要更新的 lint 版本（**环境**，非本项目代码） |
| 3 | `GradleDependency` | `libs.versions.toml:32` 的 `androidxTestExtJunit` 可升 1.1.5 → 1.2.1 |
| 2 | `DefaultLocale` | `ReaderSubPage.kt:846,859` TTS 语速/音高 `String.format("%.1f", …)` 未指定 Locale |
| 1 | `UnusedResources` | `strings_annotations.xml:37` `annotations_chapter_fallback` 未使用 |

### 1.3 主 APK（第 3 项）

- 命令：`./gradlew :app:assembleDebug --offline`
- 退出码：**0**，`BUILD SUCCESSFUL in 35s`（15:57:48 → 15:58:23）

| 项 | 值 |
|---|---|
| 路径 | `android/app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | **52,482,746 字节**（约 50.05 MiB） |
| 构建时间 | 2026-09-24 15:58:21（**本次构建产物**） |
| `sha256` | `f3c85e0a6e1e0fc0a7a0e95a9b5546ff65581f405e2f349a6a099bb70df27f7b` |

### 1.4 测试 APK（第 4 项）

- 命令：`./gradlew :app:assembleDebugAndroidTest --offline`
- 退出码：**0**，`BUILD SUCCESSFUL in 11s`（16:00:56 → 16:01:07）

| 项 | 值 |
|---|---|
| 路径 | `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| 大小 | **1,512,197 字节**（约 1.44 MiB） |
| 文件 mtime | 2026-09-24 **01:51:44** |
| `sha256` | `716728d60a08b39373dbf532e47583fa8c53cbfda3b9a40addbe25fccf24d632` |

> **⚠️ 必须如实说明（不影响本项 PASS，但影响证据强度）**：本次运行中
> `:app:packageDebugAndroidTest` 与 `:app:assembleDebugAndroidTest` 均为 **`UP-TO-DATE`**，
> 只有 `:app:compileDebugAndroidTestKotlin` 真正执行（`52 actionable tasks: 3 executed, 49 up-to-date`）。
> 因此该 APK 的**文件内容来自 01:51:44 的那次打包**，而非 16:01 重建 —— 16:01 这次只证明
> **androidTest 源码可编译、且打包任务在输入未变时判定为最新**。
> 之所以 UP-TO-DATE 合理：`androidTest` 源文件最新 mtime 为 `01:27`，且 B 本轮只改了
> `main/` 与 `test/`，未触碰 `androidTest/`。若需一份「本轮产物」的测试 APK，
> 需带 `--rerun-tasks` 重跑（会覆盖其它窗口的留证，**本轮未做**）。

---

## 2. 通过 / 未通过的边界（务必分清）

| 声明 | 本轮状态 |
|---|---|
| JVM 单元测试通过（3048 用例，0 失败） | ✅ **已验证** |
| Lint 通过（0 errors） | ✅ **已验证** |
| Debug 主 APK 可构建 | ✅ **已验证** |
| AndroidTest APK 可构建 | ✅ **已验证**（打包为 UP-TO-DATE，见 §1.4） |
| 工作区空白检查干净 | ❌ **未通过**（§5） |
| **真实 Room 行为已验证** | ⛔ **本轮未执行 —— 不得写成 PASS** |
| **真机 / 模拟器行为已验证** | ⛔ **本轮未执行 —— 不得写成 PASS** |
| 残留守卫在真实崩溃/掉电场景下有效 | ⛔ **本轮未执行** —— JVM 测试注入的是受控异常与预置文件，不等于真实 `SIGKILL`/掉电 |

`EpubRepositoryReplaceTest` / `ShelfImporterTest` 均运行在 JVM + `TemporaryFolder`，
**未使用真实 Room、未使用 Android 文件系统语义**。测试通过**不能**推出
「崩溃残留恢复在设备上成立」。本轮**不**做该声明。

---

## 3. 未通过项：`git diff --check`（退出码 2）

- 命令：`git diff --check`（同时跑了限 `android/` 的版本）
- 退出码：**全仓 = 2，限 `android/` = 2**
- 计数：**598 处空白错误** = 294 `trailing whitespace` + 5 `new blank line at EOF`（× 2 段统计 = 598）；另有 **58 条 CRLF 提示**（非错误）

**精确归因 —— 仅 6 个文件，全部是门禁前就存在的工作区状态**：

| 错误数 | 文件 | mtime | 是否 B 本轮所改 |
|---|---|---|---|
| **294** | `android/app/src/test/java/.../data/settings/ReaderSettingsOverlayTest.kt` | 2026-09-18 02:26:52 | ❌ 否 |
| 1 | `android/app/src/main/java/.../ui/screen/profile/AppearanceSubPage.kt` | 2026-09-19 19:13:27 | ❌ 否 |
| 1 | `android/app/src/main/java/.../ui/screen/reader/tts/ReaderTtsController.kt` | 2026-09-18 16:22:38 | ❌ 否 |
| 1 | `android/app/src/main/java/.../ui/screen/search/SearchScreen.kt` | 2026-09-17 00:57:37 | ❌ 否 |
| 1 | `android/app/src/main/java/.../ui/screen/shelf/ShelfSearchRoute.kt` | 2026-09-18 16:08:37 | ❌ 否 |
| 1 | `android/benchmark/src/main/java/.../benchmark/BenchmarkHelpers.kt` | 2026-09-15 01:35:21 | ❌ 否 |

**关键判定**：

1. **B 本轮改动的 4 个文件（`ShelfImporter.kt`、`EpubRepository.kt`、`ShelfImporterTest.kt`、`EpubRepositoryReplaceTest.kt`）无一涉事**（已单独 grep 确认，命中为空）。
2. 6 个涉事文件的 mtime 全在 **2026-09-15 ~ 09-19**，早于本轮工作（B 的改动在 09-24 15:18–15:22）。
3. ⇒ **`git diff --check` 的失败是工作区既有的历史状态，不是本次残留改动的回归**。
4. 按「不修改或清理工作区既有改动」的约束，我**没有**去修这些空白字符。这些文件属于别的窗口/更早的工作，清理它们超出本轮授权。

**这一项按门禁字面要求仍是「未通过」**，我如实记为 FAIL，并给出上述归因供决策方判断是否需要另行清理。

---

## 4. 复现命令与原始日志位置

四项 Gradle 均带 `--offline`，在 `android/` 目录下串行执行，无并发：

```bash
./gradlew :app:testDebugUnitTest --offline
```

```bash
./gradlew :app:lintDebug --offline
```

```bash
./gradlew :app:assembleDebug --offline
```

```bash
./gradlew :app:assembleDebugAndroidTest --offline
```

原始日志与证据已**复制到仓库外**（`/tmp/residue-gate/`），避免并发窗口覆盖 `build/test-results` 造成留证丢失
（既有评审 `2026-09-24-epub-prebackup-fix-independent-review.md` §4 记录过真实事故）：

| 文件 | 内容 |
|---|---|
| `00b-android-baseline.txt` | `android/` 下 217 个改动文件的哈希基线 |
| `01-test.log` / `01-test-summary.txt` / `TEST-*.xml`（323 份） | 第 1 项原始日志与 XML 汇总 |
| `02-lint.log` / `02-lint-summary.txt` / `lint-results-debug.xml` | 第 2 项原始日志与报告 |
| `03-assembleDebug.log` / `03-apk.txt` | 第 3 项原始日志与 APK 记录 |
| `04-assembleDebugAndroidTest.log` / `04-apk.txt` | 第 4 项原始日志与 APK 记录 |
| `05-diffcheck.log` | 第 5 项原始日志 |

---

## 5. 结论

1. **四项 Gradle 门禁全部 PASS**：单元测试 3048/3048 全绿、Lint 0 errors、主 APK 与测试 APK 均构建成功。
   门禁输入在全程未被改动，结论可归因到 `ShelfImporter.kt` = `de1ea2bc…`、`EpubRepository.kt` = `dd52027f…`。
2. **B 的提示文案修复已由定向测试实证**：`重复重试不会自动恢复` 这一断言在
   `ShelfImporterTest` 与 `EpubRepositoryReplaceTest` 中均**真实执行并通过**。
3. **`git diff --check` 未通过（退出码 2，598 处空白错误）**，但**归因清楚**：仅 6 个文件，
   mtime 全在 09-15~09-19，**与 B 本轮改动无关**；按约束未去清理。
4. **不得据此报告声称「真实 Room / 真机行为已验证」** —— 本轮未执行，JVM 测试不等于设备行为。
5. 未安装、未启动应用，未接真机，未碰用户书库；未 commit / push / stage / reset / checkout / clean；
   除本报告外未新增或修改任何文件。
