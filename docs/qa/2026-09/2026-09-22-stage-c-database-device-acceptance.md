# stage-C 数据库真机验收（窗口 C 独立执行）— 在门禁 1 被 lint 阻断，真机部分未执行

> **状态更新（2026-09-23 00:07）**：下面的第一次记录（22:xx）因 Lint 3 个 `RestrictedApi` 错误而停在门禁 1，**原样保留、不覆盖**。根因已由该改动归属方修复，**retry-01（23:55–00:07）已完成全部门禁、两次安装与两个数据库类的真机验收，结论见文末「第二次验收 / retry-01」章节**。

## 第一次执行记录（2026-09-22 22:47–23:23，历史保留）

## 结论（先说边界）

1. `:app:lintDebug` **FAIL**（3 errors / 9 warnings，exit 1）→ 按本轮指令「任一步失败立即停止后续安装和真机测试」，**未执行** `assembleDebug`、`assembleDebugAndroidTest`、任何安装、任何 `am instrument`。
2. 因此 `ParentUpsertCascadeSafetyTest`（5 个 @Test）与 `AppDatabaseMigrationTest`（23 个 @Test）本轮**没有在手机上运行过**，其真机结论是 **未执行 / BLOCKED**，既不是 PASS 也不是业务 FAIL。
3. 设备与用户数据**零改动**：未安装、未 `pm clear`、未打开书架或阅读器、未触碰任何书籍、未改权限、未改 stay-awake、未改屏幕方向。前后两次现场读数逐行相同（见 §6）。
4. 唯一被确认的**可复现新事实**：lint 的 3 个 error 全部来自工作区**未提交**的阅读器导航/滚轮改动，与 HEAD 无关，也与窗口 B 的数据库片、窗口 D 的小 TXT 片无关（证据见 §4）。

## 1. 前置快照

- 时间：2026-09-22 23:14:16（快照）；23:15:30 起跑门禁
- 分支：`codex/recover-worktree-20260920`；HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（收尾复核仍为此值）
- `git status --porcelain=v1 --untracked-files=all`：**1159 行**（收尾 1164 行，差值全部是本轮证据文件，见 §8）
- ADB：首次 `adb devices -l` 返回 `c49ac6cf unauthorized`。自行处理：`adb kill-server` + `adb start-server` 后同一 serial 变为 `device product:diting model:22081212C`（主机 adb 1.0.41 / 37.0.0）。**未要求用户点击、未改任何安全设置**；现场确认后所有命令均显式 `-s c49ac6cf`。
- 设备：型号 `22081212C`，Android 15（SDK 35），MIUI V816，serial `c49ac6cf`（未记录其他私人信息）

## 2. 测试前设备基线（证据 `00-device-before.txt`）

| 项 | 值 |
|---|---|
| `pm path com.creationreadingassistant` | 已安装（`/data/app/~~DwdgVq8QrB…/base.apk`） |
| `pm path com.creationreadingassistant.test` | **测试前已存在**（`/data/app/~~XhWZpSq1Es…/base.apk`） |
| `POST_NOTIFICATIONS` | `granted=false` |
| `stay_on_while_plugged_in` | `3` |
| `accelerometer_rotation` / `user_rotation` | `0` / `0` |
| 前台窗口 | `com.android.systemui/.usb.UsbDebuggingActivity`（即 ADB 授权弹窗，随后自行消失） |

## 3. 串行门禁

| # | 命令（在 `android/`） | 起止 | 退出码 | 结果 |
|---|---|---|---|---|
| 1 | `.\gradlew.bat :app:lintDebug --no-parallel` | 23:15:30 → 23:21:45 | **1** | BUILD FAILED in 6m 14s；`31 actionable tasks: 9 executed`；**3 errors / 9 warnings** |
| 2 | `.\gradlew.bat :app:assembleDebug --no-parallel` | — | **未执行** | 因门禁 1 失败而停止 |
| 3 | `.\gradlew.bat :app:assembleDebugAndroidTest --no-parallel` | — | **未执行** | 同上 |

### 首个根因（也是全部 3 个 error）

同一条规则 `RestrictedApi`，三处命中同一函数：

```
app/src/main/java/com/creationreadingassistant/MainActivity.kt:226: Error:
  ComponentActivity.dispatchKeyEvent can only be called from within the same library group prefix
  (referenced groupId=androidx.core with prefix androidx from groupId=CreationReadingAssistant) [RestrictedApi]
MainActivity.kt:228  同一条（两次命中：override 声明与 super 调用）
```

触发代码（工作树现状）：

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == MotionEvent.ACTION_SCROLL && ReaderHardwareKeys.dispatch(event)) return true
    return super.dispatchKeyEvent(event)      // ← androidx ComponentActivity 的受限成员
}
```

构建设置为 `android/app/build.gradle.kts:138` 的 `lint { abortOnError = true }`，且仓库**不存在 lint baseline**（`android/app/lint-baseline.xml`、`android/lint-baseline.xml` 均无）→ 3 个 error 直接把 `lintDebug` 判失败。

### 9 个 warning 分类（非阻断）

- 3 × `ObsoleteLintCustomCheck`：Gradle 转换缓存里 androidx compose-runtime / compose-ui / lifecycle-livedata-core 的 `lint.jar` 要求更高 lint API（**工具链版本问题，不指向本仓库代码**）
- 2 × `DefaultLocale`：`ui/screen/profile/ReaderSubPage.kt:846,859`
- 3 × `GradleDependency`：`gradle/libs.versions.toml:32` `androidx.test.ext:junit` 1.1.5 → 可用 1.2.1（三个变体各报一次）
- 1 × `UnusedResources`：`main/res/values/strings_annotations.xml:37` 的 `R.string.annotations_chapter_fallback`

## 4. 归因：谁的改动

| 观察 | 数值 |
|---|---|
| `MainActivity.kt` 工作树 | 246 行，含 `override fun dispatchKeyEvent` |
| `MainActivity.kt` **HEAD** | **145 行，完全没有 `dispatchKeyEvent`**（对 `git show HEAD:` 的输出做匹配，命中数为 0） |
| 未提交差异 | `+109 / -8` |
| `feature/reader/pager/ReaderHardwareKeys.kt` | 同为**未提交修改**（该文件最后一次提交 `169577e feat(android): strengthen reader navigation and scroll state`） |

⇒ 3 个 lint error 由工作区在飞的「阅读器导航 / 鼠标滚轮」改动引入；**HEAD 不含该代码**，窗口 B（数据库/DAO）与窗口 D（小 TXT 章节元数据）的文件均不在报错路径上。
诚实标注证据级别：这是**源码级对比推断**（HEAD 里没有那段代码），我**没有**对 HEAD 单独跑过一次 lint 来证明「HEAD 全绿」——本轮不允许 checkout，且共享工作区不能换基线。

## 5. 因此本轮无法交付的核对项

- 未构建 APK ⇒ 第 3 步要求的 `app-debug.apk` / `app-debug-androidTest.apk` 的**绝对路径、大小、mtime、SHA-256 全部不存在**，不予填写（不从旧构建目录取数冒充本轮产物）。
- 未安装 ⇒ 两次 `install_with_confirm.ps1` 的退出码 / `RESULT` / 是否 `CONFIRM_LOOP` / 是否 `FALLBACK_DIRECT_INSTALL` 均**未产生**。
- 未 `pm list instrumentation` ⇒ 本轮没有现场核对 main/test application id 与 runner（预期值 `com.creationreadingassistant` / `com.creationreadingassistant.test` / `androidx.test.runner.AndroidJUnitRunner` 仅作为下一轮的待验假设，本轮未证实）。
- 已做的静态核对（只读，供下一轮对照，**不等于运行结果**）：`ParentUpsertCascadeSafetyTest.kt` 源码 `@Test` = **5**（该文件目前是**未跟踪新文件**，即只存在于工作区）、`AppDatabaseMigrationTest.kt` 源码 `@Test` = **23**（未提交修改）。与任务书预期一致。

## 6. 设备边界与恢复证据

- 门禁 1 失败后我**没有**触碰设备状态：全程仅执行了只读 `getprop` / `pm path` / `dumpsys package` / `settings get`。
- 收尾读数（证据 `20-device-after-abort.txt`，23:23:05）与 §2 基线**逐项相同**：主包与测试包路径字节一致、`POST_NOTIFICATIONS: granted=false`、`stay_on_while_plugged_in=3`、rotation `0/0`。
- 因此：`stay_on_while_plugged_in` 无需写回（从未改过，原值 3 保持）；`POST_NOTIFICATIONS` 无需 revoke（测试前 false、现在仍 false；注意 `install_with_confirm.ps1` 内**没有** `pm grant`，其通知权限副作用来自它点击 MIUI 安装弹窗里的勾选框，本轮未跑到那一步）；屏幕方向未改；测试包测试前既已存在，故**不做卸载**（卸载反而会改变设备既有状态）。
- 未执行 `pm clear`、未卸载主应用、未打开书架/阅读器、未读取或记录任何真实书名（本报告只用「测试 TXT / 测试 EPUB」类中性称呼，本轮实际连书籍都未访问）。
- 未使用 MuMu 或任何模拟器；未做横屏测试。

## 7. 逐项结论

| 项 | 结论 |
|---|---|
| `lintDebug` | **FAIL**（3 errors，`abortOnError=true`，根因 `MainActivity.kt:226/228` RestrictedApi，来自工作区未提交的滚轮/按键改动） |
| `assembleDebug` | **未执行**（被门禁 1 阻断） |
| `assembleDebugAndroidTest` | **未执行** |
| APK 产物核对 | **未执行** |
| 两次安装脚本 | **未执行** |
| `ParentUpsertCascadeSafetyTest`（5） | **BLOCKED / 未运行** |
| `AppDatabaseMigrationTest`（23） | **BLOCKED / 未运行** |
| 设备/数据零改动 | **PASS**（前后读数逐行相同） |
| 小 TXT 真机可见效果 | **未覆盖**（本轮任务书本就排除；且从未构建安装包） |
| 上一轮已建立的 JVM 侧结论 | 不重复计入：本轮未跑单测；22:5x 那轮记录的主代码编译、androidTest 源码编译与 JVM 2928 项通过仍属既有事实，但**与 lint 不矛盾**（lint 失败不影响 Kotlin 编译） |

## 8. 范围外文件

未修改任何产品代码、测试代码、Gradle、schema、脚本或任务账本；未 stage/commit/push/checkout/reset/clean；未进入原始仓库。
`git status -s -uall` 前后差 5 行，逐条均为本轮允许新建的证据文件：`00-device-before.txt`、`10-lint.time`、`11-lint-report.txt`、`20-device-after-abort.txt`、`30-git-status-after.txt`（另加 `10-lint.txt` 与 `git-status-before.txt`）。
一个需要知会后人的细节：证据目录里的 `10-lint.log` **被根 `.gitignore:22` 的 `*.log` 规则忽略**，因此不出现在 git status 中；我已复制同名 `10-lint.txt` 使日志对后续窗口可见。磁盘上日志完整（4178 字节，含 `BUILD FAILED in 6m 14s`）。

## 9. 交给 lint 归属方的候选处置（我未实施，也不认领）

按代价从小到大，任选其一都需 owner 判断，因为它们改变的是**门禁语义**而非缺陷本身：

1. 把滚轮截获点从 `Activity.dispatchKeyEvent` 挪到未被 androidx 标记受限的回调上（代价：需重新确认 `ACTION_SCROLL` 在哪个回调一定可见 —— 现有注释声明只有 `dispatchKeyEvent` 能看到它，所以此项**未必可行**，须先用真机事件日志证实）。
2. 对这两行加针对性 `@SuppressLint("RestrictedApi")` 或 `lintOptions` 精确抑制（代价：把「跨 library group 调用受限 API」的风险显式化，需说明为何安全）。
3. 引入/补写 lint baseline 并在 baseline 里只登记这三处（代价：baseline 会吞掉后续同类回归）。

测试边界（无论选哪条）：`androidTest` 侧无现成用例能覆盖「鼠标滚轮进 `dispatchKeyEvent`」，JVM 侧 `ReaderHardwareKeysTest` 只测桥接逻辑不测 Activity 回调链 —— 真正的回归防线目前只有 `lintDebug` 本身，这也是不建议直接放宽门禁的理由。

## 10. 下一轮需要重跑的最小闭环

`lintDebug`（先由 owner 处理归属）→ `assembleDebug` → `assembleDebugAndroidTest` → 产物 SHA-256 → `install_with_confirm.ps1` 两次（判据：exit 0 + `RESULT: CONFIRM_LOOP`）→ `pm list instrumentation` 核对 → 两个类分别 `am instrument`（各自 ≤10 分钟超时；判据：completed=all、failures=0、errors=0、出现 `OK (N tests)`、`INSTRUMENTATION_CODE: -1`、N 与 5 / 23 一致）。
参考既有约束：这台 MIUI 机上**非 UI 的 instrumented 用例可跑**，`createComposeRule` 类会无限挂起；本轮两个类都是数据库类、不启动 Activity，理论上落在「可跑」一侧，但**本轮没有证据**，不得提前下结论。

未领取下一任务，不认领 lint 修复。

---

# 第二次验收 / retry-01（2026-09-22 23:55 → 2026-09-23 00:07）

## R0. 一句话结论

Lint **0 errors**（自己强制重跑取得）→ 两个 APK 构建 → 两份 APK 均以 `RESULT: CONFIRM_LOOP` 装上 → **`ParentUpsertCascadeSafetyTest` 5/5 PASS、`AppDatabaseMigrationTest` 23/23 PASS**。用户主数据库未被写入、主应用未启动、书架/阅读器未打开、无 `pm clear`。权限与 stay-awake 已回到现场原值。

## R1. 修复形态（只读观察，非本轮改动）

`MainActivity.kt` 已不再覆写 `dispatchKeyEvent`：现为

```kotlin
override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_SCROLL &&
        ReaderHardwareKeys.dispatchScroll(event.getAxisValue(MotionEvent.AXIS_VSCROLL))) return true
    return super.dispatchGenericMotionEvent(event)
}
```

即改用框架侧 `dispatchGenericMotionEvent`（非 androidx 受限成员）+ `ReaderHardwareKeys.dispatchScroll` 纯逻辑入口。文件 mtime 23:36:08、`ReaderHardwareKeys.kt` 23:36:53，均早于本轮起跑时刻。

## R2. 现场基线（retry-01）

- 23:55:26 记录：分支 `codex/recover-worktree-20260920`、HEAD `5aed5939600e491843e3c44453921bd6273a233c`、`git status -s -uall` = **1168 行**
- 设备：`adb devices -l` → `c49ac6cf device product:diting model:22081212C`（本轮**直接在线**，未再出现 unauthorized）；Android 15 / SDK 35 / MIUI V816
- 测试前：主包与测试包**均已存在**（`pm path` 两条都有）、`POST_NOTIFICATIONS granted=false`、`stay_on_while_plugged_in=3`、`accelerometer_rotation=0`、`user_rotation=0`、`pm list instrumentation` 已有 `com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner (target=com.creationreadingassistant)`
- 全程所有 ADB 命令显式 `-s c49ac6cf`；**未启用也未修改 stay-awake**（原值 3 已含 USB 供电常亮，无需临时改动 → 无恢复负担）；未改屏幕方向

## R3. 门禁命令与退出码

| # | 命令（在 `android/`） | 起止 | 退出码 | 结果 |
|---|---|---|---|---|
| 1a | `:app:lintDebug --no-parallel --rerun` | 23:55:50 → 23:55:54 | 0 | **不作为验收依据**（见下） |
| 1b | `:app:lintAnalyzeDebug :app:lintReportDebug :app:lintDebug --no-parallel --rerun` | 23:56:37 → 23:56:42 | 0 | **不作为验收依据**（同上） |
| 1c | `:app:lintDebug --no-parallel --rerun-tasks` | 23:57:13 → 00:03:07 | **0** | BUILD SUCCESSFUL 5m54s，**31 actionable tasks: 31 executed**，`lintAnalyzeDebug`/`lintReportDebug` 均为实际执行 → **0 errors, 9 warnings，`RestrictedApi` 命中 0** |
| 2 | `:app:assembleDebug --no-parallel` | 00:03:33 → 00:04:02 | **0** | BUILD SUCCESSFUL 28s |
| 3 | `:app:assembleDebugAndroidTest --no-parallel` | 00:04:02 → 00:04:11 | **0** | BUILD SUCCESSFUL 8s |

**必须记下的取证细节**：`--rerun` 对 AGP 的 `lintAnalyzeDebug` / `lintReportDebug` **无效**（两次都报 `UP-TO-DATE`，4 秒"通过"，报告文件仍是别人在 23:43:46 生成的那份）。我没有把它当成自己的 fresh Lint 结论，改用 `--rerun-tasks` 强制全图重跑（31/31 executed、报告 mtime 00:03:06）后才认定门禁 1 通过。**推论：本仓库用 `--rerun` 跑 lint 会给出假绿灯，后续窗口应使用 `--rerun-tasks`。**

9 个 warning 分类（均非阻断，本轮不处置）：3 × `ObsoleteLintCustomCheck`（Gradle 缓存内 androidx compose-runtime / compose-ui / lifecycle-livedata-core 的 `lint.jar` 需要更高 lint API，属工具链）、2 × `DefaultLocale`（`ReaderSubPage.kt:846,859`）、3 × `GradleDependency`（`libs.versions.toml:32` junit 1.1.5→1.2.1，三变体各一次）、1 × `UnusedResources`（`strings_annotations.xml:37`）。

## R4. 构建产物（本轮实测，未沿用旧值）

| 产物 | 绝对路径 | 大小 | mtime | SHA-256 |
|---|---|---|---|---|
| 主 APK | `D:\…\android\app\build\outputs\apk\debug\app-debug.apk` | 52,481,252 B | 2026-09-23 00:04:00 | `2b3d1a7f8ce1f7aff67face3ddbb62827cffaa88ba486eb0bcb58610bcee8412` |
| 测试 APK | `D:\…\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk` | 1,512,193 B | 2026-09-23 00:04:09 | `fdadb6e8e95932c84cc2e2fc7a6c73b019a6355ed8e65a70e867f132cefb255b` |

从**实际 APK** 的 manifest 读出（`aapt2 dump badging` / `dump xmltree`，build-tools 34.0.0）：

- main application id：`com.creationreadingassistant`（versionCode 3，versionName 0.5.1）
- test application id：`com.creationreadingassistant.test`
- instrumentation runner：`androidx.test.runner.AndroidJUnitRunner`（`AndroidManifest.xml` line 9 的 `instrumentation/name`）
- instrumentation targetPackage：`com.creationreadingassistant`
- 与设备侧 `pm list instrumentation` 完全一致 → 预期值本轮**已被证实**（第一次记录里只能列为假设）

## R5. 安装（两次都是脚本自动确认，无需人工点击）

| APK | 命令 | 起止 | 退出码 | RESULT | FALLBACK? |
|---|---|---|---|---|---|
| 主 APK | `powershell -File .\scripts\install_with_confirm.ps1 -Apk …app-debug.apk -Serial c49ac6cf -PackageName com.creationreadingassistant -TimeoutSeconds 120` | 00:04:44 → 00:05:09 | **0** | `CONFIRM_LOOP (installed through the MIUI installer UI)` + `Installed com.creationreadingassistant successfully.` | 否 |
| 测试 APK | 同上，`-Apk …app-debug-androidTest.apk -PackageName com.creationreadingassistant.test -TestApk` | 00:05:29 → 00:05:44 | **0** | `CONFIRM_LOOP (installed through the MIUI installer UI)` | 否 |

- 两次都未超时、未被 MIUI 阻断、**没有走 `adb install -r` 回退**，因此可如实写"安装脚本通过"。
- 脚本点击序列：`android:id/button3`(351,2522) → `com.miui.packageinstaller:id/checkbox`(610,2008) → `…:id/second_button`(610,2356)。
- **副作用复现并归因**：`POST_NOTIFICATIONS` 由 `false` → `true`。脚本内**没有任何 `pm grant`**（已 grep 证实），翻转来自它点掉 MIUI 安装弹窗里的通知勾选框 → 属脚本确认循环的既有副作用，不是坐标误点。收尾已按 §R8 处理。
- 安装后核对：两条 `pm path` 都指向**新的**安装目录（主包 `~~8eUQO2w3…`、测试包 `~~bKchEGMC…`，与测试前目录不同 → 确为本次产物），且 `pm list instrumentation` 输出 runner 与 target 均匹配。

## R6. 两个数据库测试类（分别运行，不合并）

| 类 | 命令 | 起止 | 退出码 | numtests | started | completed | failures/errors | 汇总 | INSTRUMENTATION_CODE |
|---|---|---|---|---|---|---|---|---|---|
| `ParentUpsertCascadeSafetyTest` | `adb -s c49ac6cf shell am instrument -w -r -e class …ParentUpsertCascadeSafetyTest com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner` | 00:06:07 → 00:06:09 | **0** | 5 | 5 | 5 | 0 / 0 | **`OK (5 tests)`**，Time 0.214s | `-1`（正常完成） |
| `AppDatabaseMigrationTest` | 同上，`-e class …AppDatabaseMigrationTest` | 00:06:36 → 00:06:39 | **0** | 23 | 23 | 23 | 0 / 0 | **`OK (23 tests)`**，Time 1.112s | `-1`（正常完成） |

- 数量与源码预期一致：5 / 23（源码 `@Test` 计数分别在 `retry-01` 前后复核，文件 mtime 22:24:10、22:32:26，本轮未改）。
- 无 `INSTRUMENTATION_STATUS_CODE: -2`（失败块）出现，输出中无 `failures=`/`Error:` 段。
- 两个类各自远小于 10 分钟上限，无挂起、无需强停测试进程、无需授予任何 MIUI 权限。
- 5 个用例名：`upsertingInspirationPreservesVariantsAndNoteLink`、`upsertingBooksInBatchPreservesChildRows`、`upsertingBookPreservesAllChildRows`、`upsertingTaxonomyParentsPreservesJoinRows`、`softDeletePreservesChildRows`。迁移类最后完成的是 `migrate_12_to_13_adds_reader_text_corrections`（`current=23`）。

## R7. 数据与设备边界（本轮实际做了什么）

- **未**打开书架或阅读器、**未**启动主应用、**未**导入/打开/删除/改名任何书籍、**未** `pm clear`、**未**改 DataStore、**未**做横屏、**未**用模拟器。
- 用户数据存活的元数据证据（`run-as … ls -l databases/`，只看名字与时间戳，**不读内容、不记录书名**）：
  - `creation_reading_assistant_native` 1,124,143,104 B，mtime **2026-09-22 21:56** —— 本轮全程（23:55–00:07）未变；
  - `…-wal` mtime 同为 **21:56** → WAL 里**没有新提交写入**；
  - `…-shm` mtime 变成 00:06：`-shm` 只是打开时刷新的共享内存副文件，**instrumentation 运行在宿主进程内**，因此被 touch；不构成数据变更，也不与 `-wal` 未变矛盾；
  - 新增 `migration-test`(311,296 B) 与 `migration-test-journal`，mtime 00:06 —— 这是 `AppDatabaseMigrationTest` 自己的**独立测试库**，与主库隔离。**我刻意未删除**（删除属于继续改应用数据目录），在此登记其存在。
- 屏幕方向：`accelerometer_rotation=0`、`user_rotation=0`，测试前后逐字相同。

## R8. 收尾恢复证据

| 项 | 测试前 | 安装后 | 收尾动作 | 收尾回读 |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` | `granted=false` | `granted=true`（安装弹窗勾选框） | `adb -s c49ac6cf shell pm revoke com.creationreadingassistant android.permission.POST_NOTIFICATIONS`，rc=0 | **`granted=false`** ✅ |
| `stay_on_while_plugged_in` | `3` | 未触碰 | 无需恢复 | **`3`** ✅ |
| rotation | `0` / `0` | 未触碰 | 无需恢复 | **`0` / `0`** ✅ |
| 测试包 | 测试前已存在 | 已升级到本次产物 | **按约定不卸载** | `pm path` 存在 ✅ |
| 主应用 | 已存在 | 已升级到本次产物 | 不卸载、不清数据 | `pm path` 存在 ✅ |

## R9. 逐项结论（retry-01）

| 项 | 结论 |
|---|---|
| fresh Lint（`--rerun-tasks`） | **PASS**（0 errors，RestrictedApi 0，exit 0，31/31 executed） |
| `assembleDebug` | **PASS**（exit 0） |
| `assembleDebugAndroidTest` | **PASS**（exit 0） |
| APK manifest 身份核对 | **PASS**（main/test/target/runner 四项均实测匹配） |
| 主 APK 安装 | **PASS**（脚本 exit 0 + `CONFIRM_LOOP`，无回退） |
| 测试 APK 安装 | **PASS**（同上） |
| `ParentUpsertCascadeSafetyTest` | **PASS**（5/5 completed，0 failures，`OK (5 tests)`，`INSTRUMENTATION_CODE: -1`） |
| `AppDatabaseMigrationTest` | **PASS**（23/23 completed，0 failures，`OK (23 tests)`，`INSTRUMENTATION_CODE: -1`） |
| 权限 / stay-awake / 方向恢复 | **PASS** |
| 用户数据未被清除、书籍未被访问 | **PASS**（主库 size+mtime 未变、WAL 未变、未启动应用） |
| 小 TXT 真机可见效果 | **未覆盖**（本轮明确不做） |
| 鼠标滚轮硬件行为 | **未覆盖** —— 修复只让 lint 与编译通过，`dispatchGenericMotionEvent` 是否真能在设备上滚动正文，本轮**没有任何证据** |
| Compose UI 类 androidTest | **未运行**（这台 MIUI 机上已知会挂起，见既有记录；本轮不宣称其结果） |
| Lint 9 个 warning | **未处置**（非阻断项，登记在 R3） |

## R10. 范围外文件与证据

- 未修改任何产品代码、测试代码、Gradle、schema、脚本或任务账本；未 stage/commit/push/checkout/reset/clean；未进入原始仓库；未删构建缓存。
- `git status -s -uall`：1168 → 1191 行，逐行比对后**差异全部落在** `docs/qa/evidence/2026-09-22-stage-c-database-device-acceptance/retry-01/` 内（本轮允许新增），工作树其余条目与开始完全一致；HEAD 与分支未变。
- 本轮报告本身新增 1 处（在原文件追加 retry-01 章节，第一次记录未删）。
- 证据文件：`00-git-status-before.txt`、`01-device-before.txt`、`10-lint.*`、`11-fresh-lint.*`、`12-rerun-tasks-lint.*`、`13-assembleDebug.*`、`14-assembleAndroidTest.*`、`40-install-main.*`、`41-install-test.*`、`42-post-install-verify.txt`、`50-instr-ParentUpsertCascadeSafetyTest.txt`、`51-instr-AppDatabaseMigrationTest.txt`、`60-cleanup-final.txt`、`61-data-survival.txt`、`70-git-status-after.txt`（注意根 `.gitignore` 的 `*.log` 规则，日志一律用 `.txt` 存）。

未领取下一任务，不认领任何修复。
