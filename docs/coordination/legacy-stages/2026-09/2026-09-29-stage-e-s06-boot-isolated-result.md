# 阶段 E — S06 启动主题最小候选隔离验证结果（2026-09-29）

> **窗口**：E 阶段 S06 启动主题最小候选隔离编译 + 定向 JVM（承接[启动边界报告](../../reviews/2026-09/2026-09-29-stage-e-app-startup-boundary-review.md) §十 C1–C8）。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> **分支 / HEAD 已核对**：`codex/recover-worktree-20260920` / **`513891d6a66a9b78e072ba759d34ed580d377ecf`**；`git log --oneline -3` = `513891d` → `209ec8a` → `5aed593`；**暂存区为空**（`git diff --cached --stat` 无输出）；工作树 `537`（porcelain）/ `1526`（porcelain -uall，本轮开始时）。
> **隔离根目录**：`E:\creation-reading-assistant\stage-e-s06-boot-isolated-20260929`
> **构建缓存**：`GRADLE_USER_HOME` = `<隔离根>\gradle-home`（E 盘）；`JAVA_HOME` = `D:\develop\Java\jdk-17.0.14`
> **性质**：共享仓库**唯一写入 = 本文件**。未改共享源码 / 测试 / 资源 / 清单 / 配置 / CSV / 其他窗口报告；未 stage / commit / push / reset / checkout / stash；未迁目录；未触碰原仓库与正式书库。
> **设备声明**：**本轮未连接任何设备、未运行 adb、未使用 MuMu**。因此**不能声称冷启动第一帧已在 MuMu 验收**，也不能声称任何设备行为已通过。

---

## 一、结论摘要（先说人话）

1. **候选编译通过、守门测试全绿。** 从 HEAD 精确提取的 S06 启动主题最小候选，在 E 盘隔离目录里 `:app:compileDebugKotlin` 与 `:app:compileDebugUnitTestKotlin` **BUILD SUCCESSFUL（EXITCODE=0，5m 5s）**；`SettingsStoreBootMirrorTest` 定向 JVM **13/13，0 失败 0 错误 0 跳过**。补丁可复算：全新 HEAD 导出 + 补丁 == 候选，**8/8 文件逐字节一致**。
2. **启动边界报告 §十的清单有一处会导致编译失败的偏差，已修正。** 报告写「删除 HEAD `MainActivity.kt` 59–62 四行」内联 `darkTheme when`；实际必须删 **59–63 五行**——第 63 行的 `}` 是那个 `when` 的闭合括号，只删 59–62 会留下一个游离 `}`。本轮按实际源码依赖修正，理由见 §五。
3. **候选严格不含任何被排除的族。** 对 `App.kt` / `MainActivity.kt` / `SettingsStore.kt` / `AndroidManifest.xml` 做了逐符号审计：`TAG_BOOT`、`cra-startup`、`ONBOARDING_PREFS`、`appVersion`、`heapUsedMb`、`deviceInfo`、`DurationPrefs`、`DurationUnit`、`LocalDurationUnit`、`CompositionLocalProvider`、`dispatchGenericMotionEvent`、`BatteryTipStyle`、`ReaderDayNightToggle`、`tapActionPreset`、`volumeKeyInvert`、`ttsSpeechRate`、`isCordHintSeen`、`TTS_SERVICE` 等**全部 0 命中**。
4. **一处与工作树的差异需要写清：** 本候选的 `App.kt` 把 S06 主题块插在 `AppLog.i("App","应用启动")` 之后、三项内联启动接线**之前**（报告 §十二 12.1 建议插在接线**之后**）。该块与三项接线无任何数据依赖，位置不改变 S06 行为；选择「之前」是为了让插入字节与工作树该块的相对位置一致（工作树 121–131 紧随 `AppLog.i`）。已在 §五 登记。
5. **仍然只有静态与 JVM 证据。** S06 的核心价值是「冷启动第一帧不闪色」，这只能由设备观测；本轮**零设备**，该部分仍是 **NOT RUN**。另外 `MainActivity` 与 `App.kt` 本身没有任何 JVM/仪器测试，只有编译门禁。

---

## 二、方法与证据来源

### 2.1 隔离做法（共享仓库零写入）

| 步骤 | 做法 |
|---|---|
| 基线 | `git archive --format=tar HEAD android` 只读导出（blob 内容，**纯 LF**）到 `<隔离根>\android`（候选）与 `<隔离根>\_base`（只读参照） |
| 未跟踪配置 | 复制共享仓库的 `android\local.properties`（`sdk.dir=D:\develop\Android\Sdk`，HEAD 未跟踪） |
| 提取 | 不用 `git add -p`；由 `_evidence\build_candidate.py` 以**内容锚点 + 断言**逐块插入，锚点命中不唯一或断言失败即中止 |
| 补丁 | `difflib.unified_diff` 从 `_base` vs 候选生成标准统一补丁（`a/android/...` / `b/android/...`），非交互式、可复算 |
| 校验 | 全新 HEAD 导出 → `git apply --check` → `git apply` → 逐文件 SHA-256 比对 |
| 构建 | 全部在 E 盘隔离目录；`GRADLE_USER_HOME` 指向本任务 E 盘目录 |

### 2.2 构建缓存（E 盘）

`D:\develop\env\gradle`（7.64 GB，与 `C:\Users\23254\.gradle` 同源）用 `robocopy /E /MT:16` 复制到 `<隔离根>\gradle-home`，**25,040 文件 / 7.167 GB / 0 FAILED / 18 秒**。排除了 `daemon`、`.tmp`、`kotlin-profile`、`notifications`。**本轮所有 Gradle 缓存、临时文件、日志与产物均在 E 盘**，未在 C/D 盘新建构建缓存。

### 2.3 判定口径

| 标记 | 含义 |
|---|---|
| **已验（本轮实测）** | 有本轮命令、退出码与原始 XML 支持 |
| **未验** | 本轮未执行，不得声称通过 |
| **静态** | 只读源码/清单可读出，未经运行验证 |
| **修正** | 与启动边界报告 §十/§十二 的写法不同，且给出理由与实测依据 |

---

## 三、候选清单与逐项来源（C1–C8）

> 所有片段均取自共享工作树的**对应字节区间**，锚点与区间由脚本断言；`local.properties` 不入补丁。

| # | 文件 | 取用范围（实际） | 来源锚点 | 结果 |
|---|---|---|---|---|
| C1 | `data/settings/SettingsStore.kt` | 7 个启动镜像 hunk：S1 定义体（+121 行）、S2 `appContext`（+3）、S3 `bootAppearance`/`backfill`/`bootReader`（+34）、S4 `appearance` 首值（2→4）、**S5 仅第 586 行单行**、S6 `updateAppearance` 镜像（+3）、S7 `updateReader` 纸色镜像（+1） | `TtsResume` 之后、`private val ds` 之后、`writeAiApiKey` 之后、`AppearanceSettings())` 行、`ReaderSettings())` 行、`val next = appearance.value.block()`、`val next = reader.value.block()` | 22 个阅读器设置 hunk **全部未取** |
| C2 | `MainActivity.kt` | `+import …BootMirror`、`+import …ThemeResolver`、M10（注释 + `super.onCreate` 前 `applyBootWindowTheme()`）、M11 仅删内联 `when` + M12（`ThemeResolver` 版 `darkTheme`）、M14（`applyBootWindowTheme()` 定义） | `import …SettingsStore`、HEAD 47–48 注释、HEAD 59–63 内联 `when`、`internal fun onHomeContentReady` 之前 | 保留 HEAD 58 的 `showOnboarding` 同步读与 `Box`/`OnboardingOverlay` 原样；G6/隔离/滚轮/诊断**全部未取** |
| C3 | `App.kt` | A6 注释（4 行）+ A7 `runCatching { BootMirror.themeMode … }`（7 行），共 +11 行 | `AppLog.i("App", "应用启动")` 之后 | 三项内联启动接线**原样保留**；诊断、`ONBOARDING_PREFS` 预热、`cra-startup` 线程**全部未取** |
| C4 | `res/values/colors.xml` | +7 行（2 个 `boot_window_background*` 色值 + 注释） | `<resources>` 之后 | 整 hunk（矩阵 G3） |
| C5 | `res/values/themes.xml` | 全量（注释重写 + `windowBackground` + `Theme.CreationReadingAssistant.Dark`） | — | 与工作树**逐字节一致**（矩阵 G3） |
| C6 | `res/values-night/themes.xml` | 新文件（16 行） | — | 与工作树**逐字节一致**（矩阵 G3） |
| C7 | `AndroidManifest.xml` | 仅 `android:configChanges="…\|uiMode"` 一行 | `android:exported="true"` 与 `android:theme=` 之间 | TTS_SERVICE query 与注释改动**未取**（归 G4） |
| C8 | `app/src/test/.../SettingsStoreBootMirrorTest.kt` | 新文件（256 行，13 个 `@Test`） | — | 与工作树**逐字节一致**；依赖 `testutil/TestDoubles.kt`（HEAD 已有、工作树未改） |

**候选规模**：8 个文件；补丁 33,894 字节 / 662 行；`SettingsStore.kt` 7 hunk、`MainActivity.kt` 4 hunk、`App.kt` 1 hunk、manifest 1 hunk、colors 1 hunk、themes 1 hunk、2 个新文件。

---

## 四、排除项审计（实测 0 命中）

对候选树内 `App.kt`、`MainActivity.kt`、`SettingsStore.kt`、`AndroidManifest.xml`、`themes.xml`、`colors.xml`、`values-night/themes.xml`、`SettingsStoreBootMirrorTest.kt` 做 `-SimpleMatch -CaseSensitive` 全文检索：

| 被排除族 | 代表符号 | 命中 |
|---|---|---|
| 启动诊断增强 | `appVersion`、`heapUsedMb`、`deviceInfo`、`yyyy-MM-dd`（时间戳格式） | 0 |
| 后台线程搬迁 | `TAG_BOOT`、`cra-startup` | 0 |
| `ONBOARDING_PREFS` 预热与引导异步读 | `ONBOARDING_PREFS`、`onboardingCompleted`、`LaunchedEffect` | 0 |
| G6 时长单位 | `DurationPrefs`、`DurationUnit`、`LocalDurationUnit`、`CompositionLocalProvider` | 0 |
| 阅读器设置族（22 hunk） | `BatteryTipStyle`、`customTipHeader`、`customTipFooter`、`tapActionPreset`、`volumeKeyInvert`、`ttsSpeechRate`、`headerItemToTemplate` | 0 |
| 灯绳 / 日夜纸色（矩阵 EXCLUDED） | `ReaderDayNightToggle`、`dayNightLightPaper`、`dayNightDarkPaper`、`isCordHintSeen` | 0 |
| 鼠标滚轮翻页 | `dispatchGenericMotionEvent`、`ReaderHardwareKeys.dispatchScroll` | 0 |
| TTS 包可见性（归 G4） | `TTS_SERVICE` | 0 |

**必含符号审计（全部命中）**：`internal object BootMirror`、`object ThemeResolver`、`private val bootAppearance`、`private val bootReader`、`backfillBootThemeIfNeeded`、`applyBootWindowTheme`、`Theme_CreationReadingAssistant_Dark`、`boot_window_background`、`configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode"`。

**候选 vs 共享工作树的差异**（`-U0`，方向：候选 → 工作树）已落盘 `_evidence\excluded-vs-worktree.txt`：`SettingsStore.kt` 22 个 hunk、`MainActivity.kt` 12 个 hunk、`App.kt` 8 个 hunk、`AndroidManifest.xml` 2 个 hunk；`themes.xml` 与 `values-night/themes.xml` **0 差异**；`colors.xml` 仅行尾差异（见 §五）。

---

## 五、§十静态清单的修正记录（以实际源码依赖为准）

### 5.1 【修正·会导致编译失败】`MainActivity.kt` 内联 `when` 必须删 5 行而非 4 行

- 报告写法：§六 6.3「删除 HEAD 59–62 的内联 `when`」；§十二 12.5「保留 HEAD 58 不动；删除 HEAD 59–62 四行」。
- 实测 HEAD 内容：

```
58:                 var showOnboarding by remember { mutableStateOf(!isOnboardingCompleted(this@MainActivity)) }
59:                 val darkTheme = when (appearance.themeMode) {
60:                     "dark" -> true
61:                     "light" -> false
62:                     else -> isSystemInDarkTheme()
63:                 }
```

- 报告读法来自 `git diff -U3` 的 `-58,5` 与后续 `@@ -63,0 +112,4 @@` 的算术：diff 把 HEAD 63 行的 `}` **复用**成了工作树里 `LaunchedEffect(Unit) {` 的闭合括号，因此「删除 59–62」在 diff 上是自洽的，但**直接照搬到手工编辑就会留下一个游离的 `}`**。
- **实际修正**：删除 **HEAD 59–63（5 行）**，插入工作树 112–115（4 行 `ThemeResolver.resolveAppDarkTheme(...)`）。修正后候选编译通过（§八），若只删 4 行则必然因括号不配对而编译失败。
- 归类：**修正**（不是候选范围变化）。

### 5.2 【修正·登记】`App.kt` S06 块的插入位置

- 报告 §十二 12.1：「保留 HEAD `:91–109` 原样不动，在其**之后**新增一个独立的 `runCatching`」。
- 实际：插在 `AppLog.i("App", "应用启动")`（HEAD `:90`）之后、三项接线**之前**。
- 理由：(a) 与工作树该块的相对位置一致（工作树 121–131 紧随 `AppLog.i`），插入字节与工作树完全相同；(b) 该块只读 `cra_boot_mirror` SharedPreferences 并调用 `UiModeManager.setApplicationNightMode`，与 EPUB 修复 / 目标提醒 / 搜索索引三项**无任何数据依赖**，前后位置不改变 S06 行为。
- 归类：**修正**（位置差异，语义等价，已登记）。

### 5.3 【记录·未采纳建议】悬空重复 KDoc 保留原样

- 报告 §5.2 / §十 10.2 建议「顺手删除工作树 334–341 的悬空重复 KDoc」。
- 本轮**保留**：任务要求「严格排除…其他 HOLD 改动」「不扩大到无关功能」，删除注释属未被请求的编辑，且会让 S06 块与工作树不再逐字节一致。该段是纯注释（`BootMirror` 的 KDoc 被复制两份，第一份未附着任何声明），**不改变行为**，编译通过。
- 归类：**记录**（保留建议不执行的理由）。

### 5.4 【记录·行尾规范化】`colors.xml` 取 LF

- 共享工作树的 `res/values/colors.xml` 是 **CRLF**（`git` 会提示 "CRLF will be replaced by LF"），HEAD blob 是 LF。
- 候选统一按 **LF** 写入（与 `core.autocrlf=true` 下 git 入库内容一致），因此补丁可复算、且与仓库其余文件一致。内容与工作树**逐行相同**（LF 归一化后逐字节一致）。
- 归类：**记录**。

### 5.5 【记录·数量差】补充回归里 `SettingsStoreTest` 是 11 项而非 13 项

- [S06 跟随系统主题报告](2026-09-28-s06-follow-system-theme-result.md) §6.1 记录 `SettingsStoreTest` 13 项；本候选为 **11 项**。
- 原因：工作树多出的 2 项是 `day night paper memory persists and reads back` 与 `day night paper memory rejects values from the wrong side`，引用 `ReaderDayNightToggle`（矩阵判 **EXCLUDED**），按报告 §十一 已排除。**11 = 13 − 2，与排除口径自洽**。
- 归类：**记录**（不是缺陷）。

---

## 六、补丁与可复算校验

| 项 | 值 |
|---|---|
| 补丁路径 | `E:\creation-reading-assistant\stage-e-s06-boot-isolated-20260929\patches\stage-e-s06-boot-minimal.patch` |
| 大小 / 行数 | 33,894 字节 / 662 行 |
| SHA-256 | `238274ef6aae4e7a03ceb8aea318222b3c4279fe56df8cc4b5dbe51719d8df4d` |
| `git apply --check -p1` | **EXITCODE=0**，8 个文件全部 `Checking patch … OK` |
| `git apply -p1` | **EXITCODE=0** |
| 复算比对 | 全新 `git archive HEAD android` 导出 + 补丁，与候选逐文件 SHA-256 比对：**8/8 IDENTICAL** |
| 生成方式 | `_evidence\build_candidate.py`（锚点断言 + `difflib`），脚本与日志已落盘，非交互式、可复现 |

---

## 七、编译门禁（已验）

```
Set-Location <隔离根>\android
$env:GRADLE_USER_HOME = <隔离根>\gradle-home
$env:JAVA_HOME        = D:\develop\Java\jdk-17.0.14
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --console=plain
```

| 项 | 结果 |
|---|---|
| 结果 | **BUILD SUCCESSFUL in 5m 5s** |
| 退出码 | **EXITCODE=0** |
| 任务 | `22 actionable tasks: 17 executed, 5 from cache` |
| 关键任务 | `:app:kspDebugKotlin`、`:app:compileDebugKotlin`、`:app:hiltJavaCompileDebug`、`:app:kspDebugUnitTestKotlin`、`:app:compileDebugUnitTestKotlin` 均执行成功 |
| 警告 | 仅既有告警（`Icons.Outlined.HelpOutline` 弃用、`ReaderDocumentLoaderProfileTest` 的 opt-in），**无 error** |
| 日志 | `_evidence\gradle-01-compile.log` |

**未运行**：`:app:lintDebug`、`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin`、`:app:connectedDebugAndroidTest`（候选不含 androidTest 文件；完整门禁属集成者串行执行）。

---

## 八、定向 JVM 测试（已验）

### 8.1 主证据：`SettingsStoreBootMirrorTest`

```
.\gradlew.bat :app:testDebugUnitTest --console=plain `
  --tests "com.creationreadingassistant.data.settings.SettingsStoreBootMirrorTest"
```

| 项 | 结果 |
|---|---|
| 结果 | **BUILD SUCCESSFUL in 1m 11s**；**EXITCODE=0** |
| XML | `TEST-com.creationreadingassistant.data.settings.SettingsStoreBootMirrorTest.xml` |
| 计数 | **tests=13, failures=0, errors=0, skipped=0**（time=10.293s） |
| XML SHA-256 | `e3ff922258b0b73cce85eab2a75caf2fa7ae130904550d9c27c565d61712d4ef` |
| 日志 | `_evidence\gradle-02-bootmirror-test.log`；二次重跑 `_evidence\gradle-02b-bootmirror-test-rerun.log`（同样 EXITCODE=0） |

13 项覆盖：冷启动首帧 `appearance`/`reader` 首值、全新安装无镜像、升级回填（dark / light / system）、`ThemeResolver` 六场景（设备浅色/深色、显式 light/dark、显式切回 system、未知值回退）、`BootMirror.desiredNightMode` 映射契约。

### 8.2 补充回归（相邻设置测试，非本候选范围，仅作旁证）

```
.\gradlew.bat :app:testDebugUnitTest --console=plain `
  --tests "com.creationreadingassistant.data.settings.SettingsStoreTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.SettingsViewModelTest"
```

| 套件 | tests | failures | errors | skipped | XML SHA-256 |
|---|---:|---:|---:|---:|---|
| `SettingsStoreTest` | **11** | 0 | 0 | 0 | `873ab8de0d6e5040aa3ca96ff7c1b399494e58a375d06ad7adf22e6a6275ae42` |
| `SettingsViewModelTest` | **8** | 0 | 0 | 0 | `1333bbde0f31972b6f99b5ec347a7da1a740a1eb8a9df1b02998614c194ec6f1` |
| 合计 | **19** | 0 | 0 | 0 | — |

结果 **BUILD SUCCESSFUL in 17s，EXITCODE=0**；日志 `_evidence\gradle-03-supplementary-settings-test.log`。

**本轮 JVM 合计：13（主）+ 19（补充）= 32 项，0 失败 0 错误 0 跳过。**

---

## 九、逐文件 SHA-256 与产物清单

### 9.1 候选文件（E 盘隔离候选 `android/`）

| 文件 | 候选 SHA-256 | HEAD/base SHA-256 |
|---|---|---|
| `data/settings/SettingsStore.kt` | `06517f7fd055266e2dc8ba81eb4ebca67912c5b72a84f65302a5e715c5313319` | `3adbcf618d9e3279120872430acf44e1d16305b60df1ae8012d9da68b21886e4` |
| `MainActivity.kt` | `37feedfaa1ca1855d06ca8ccfcf3c2faf4c49da595deb8b036a435af3aeb8b95` | `d026bfe35eb97b366ee521ad441a3f706f98fe1e78a63a33d65489209327145c` |
| `App.kt` | `08a517f92bc36f22f4c2be2b73810750d77445b3d14541cb95d48d11de684c7d` | `75d223fb6194f55306731c3bb0965813dc6bc3cf98cfc785cf5cf239ca84f248` |
| `AndroidManifest.xml` | `611733eca8946754ca5f89e10e4baa40ff15ddfad789ea604b663b7eed6ebcca` | `e0e721f98e725ab2d4611af4efcc713d99cd49561b2e77aaa62c1a6b31471123` |
| `res/values/colors.xml` | `9d4e153e2e1b120ebbe5a81013919afc62845a4740f0bd20225579f9e8b34bf7` | `6eab7a07f02b4e4562de0b7d1cb86bee53af8b1bf510a13be16e3840c7ee0b0f` |
| `res/values/themes.xml` | `4e4bd245664411dcad7008ad47873672def9ec1fdc4e896077d8e5c405e8a3f3` | `1013a359b5bf9e12bc89cfa8b48d805319f85d4faa1d2bc7240ebfad2db39aba` |
| `res/values-night/themes.xml` | `a915651cb4edf2c5fd09114541fcf4bd58de75014ac980b7b3959461adafe825` | （新文件） |
| `test/.../SettingsStoreBootMirrorTest.kt` | `14780e9e43d5b6079467af375020f84f68634820fe1cb7f275d7ed66aa970fdb` | （新文件） |

### 9.2 共享工作树源文件（提取来源，提取期间未变）

| 文件 | 工作树 SHA-256 | 工作树 mtime |
|---|---|---|
| `SettingsStore.kt` | `640311965fc741017c7d55df8c845da5ecc8abe234ce617156d678448adcccf7` | 2026-09-28 17:10:11 |
| `MainActivity.kt` | `bdc886c0355e37b9bdde52aa887825d7bf1ee633ed603127bfaa1d24b7da34f9` | 2026-09-28 17:10:33 |
| `App.kt` | `9859a4344f86d7ec5912e5894d30aa245c8fa108850099cf14ab0081a9f3d5f2` | 2026-09-28 17:10:48 |
| `AndroidManifest.xml` | `3f9cc34ec1e5b98213587b788c49e5e683786e0acbcf2808a6e85bc24d277ac9` | 2026-09-18 19:34:33 |
| `res/values/colors.xml` | `001ea5f9910042f5a1450a71a05fb3c38ffe2e45da44bfbf07c22a79ee6f5837`（CRLF） | 2026-09-18 19:23:10 |
| `res/values/themes.xml` | `4e4bd245664411dcad7008ad47873672def9ec1fdc4e896077d8e5c405e8a3f3` | 2026-09-18 19:22:56 |
| `res/values-night/themes.xml` | `a915651cb4edf2c5fd09114541fcf4bd58de75014ac980b7b3959461adafe825` | 2026-09-18 19:23:17 |
| `SettingsStoreBootMirrorTest.kt` | `14780e9e43d5b6079467af375020f84f68634820fe1cb7f275d7ed66aa970fdb` | 2026-09-28 17:15:41 |

### 9.3 E 盘产物清单

```
E:\creation-reading-assistant\stage-e-s06-boot-isolated-20260929\
  android\                                  候选工作树（HEAD 导出 + C1–C8）
  _base\android\                            只读 HEAD 参照树（生成补丁用）
  _verify\android\                          复算校验树（HEAD + 补丁）
  gradle-home\                              GRADLE_USER_HOME（E 盘，7.167 GB）
  patches\stage-e-s06-boot-minimal.patch    可复算补丁（33,894 B）
  _evidence\
    build_candidate.py                      提取脚本（锚点断言）
    build-candidate.log                     脚本运行日志
    commands.txt                            实际执行命令与结果
    selection-map.csv                       8 行纳选映射（族 / base SHA / 候选 SHA）
    excluded-vs-worktree.txt                候选 vs 工作树逐文件被排除 hunk
    sha256-manifest.txt                     5 节完整 SHA 清单
    gradle-01-compile.log                   编译门禁日志
    gradle-02-bootmirror-test.log           主测试日志
    gradle-02b-bootmirror-test-rerun.log    主测试二次重跑
    gradle-03-supplementary-settings-test.log  补充回归日志
    test-xml-bootmirror\                    主测试 XML（13 项）
    test-xml-supplementary\                 补充回归 XML（11 + 8 项）
```

---

## 十、未覆盖与不得外推

| 项 | 状态 | 说明 |
|---|---|---|
| **冷启动第一帧颜色** | **NOT RUN** | S06 的核心行为，只能设备观测。本轮**零设备**，不得声称已在 MuMu 或真机验收 |
| `setApplicationNightMode` 实际系统效果 | **NOT RUN** | [S06 报告](2026-09-28-s06-follow-system-theme-result.md) 顶部复核已明确：仅有 JVM 映射断言；本轮只重复了同一层级的 JVM 证据 |
| 显式切回 `system` 的清除路径 | **NOT RUN** | 依赖厂商 ROM 对 `MODE_NIGHT_AUTO` 的处理 |
| `MainActivity` / `App.kt` 的行为 | **未验** | 两者无任何 JVM/仪器测试；本轮只有编译门禁 |
| `:app:lintDebug` | **未跑** | 报告 §十一 已排除相关族；lint 属集成者串行门禁 |
| `:app:assembleDebug` / APK | **未跑** | 本轮只做编译与定向 JVM |
| `:app:compileDebugAndroidTestKotlin` | **未跑** | 候选不含 androidTest 文件 |
| 与 G6（时长）的联合态 | **未验** | 两者在 `MainActivity.kt` 同一文件的相邻 hunk；本轮**只验 S06 半边** |
| 与首批 `G0/U/G2+runner` 的联合态 | **未验** | 本轮候选 = HEAD + S06，不含首批提交内容之外的任何其他批次 |
| 全量 JVM | **未跑** | 本轮只跑 3 个定向套件；不得用「全量 3208/3208」外推本候选 |

**外推禁止**：本候选编译通过与 13+19 定向 JVM 通过，**只证明该精确候选在这三条命令下通过**，不证明：任何其他拆分组合可编译、S06 在设备上生效、或该候选可安全提交。

---

## 十一、给集成者/总指挥的三项（只提事实，不裁决）

1. **批次 3 / G3 的清单可以按本候选补齐**：实测「3 个 res 文件 + `SettingsStoreBootMirrorTest.kt`」不足以编译，必须加 `SettingsStore.kt` 的 7 个启动主题 hunk、`MainActivity.kt` 的 S06 5 处、`App.kt` 的 S06 1 处、`AndroidManifest.xml` 的 `uiMode` 1 行。**注意 `MainActivity.kt` 的内联 `when` 要删 5 行（59–63），不是 4 行。**
2. **`SettingsStore.kt` 的 HOLD 口径建议细化**：现行「29 hunks 全 HOLD」的依据只覆盖 22 个阅读器设置 hunk；本轮实测另 7 个启动主题 hunk 可与 S06 一起独立编译通过（不含任何阅读器设置改动）。
3. **S06 仍缺设备证据**：本候选只到「可编译 + JVM 绿」。若要让 S06 进入提交候选，还需一次设备观测（应用内深色 + 系统浅色下的冷启动首帧），且该观测必须由持有设备的窗口执行。

---

## 十二、纪律声明

- 共享恢复仓库内**唯一写入 = 本文件**（`docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-s06-boot-isolated-result.md`）。未改 `App.kt`、`MainActivity.kt`、`SettingsStore.kt`、`OnboardingOverlay.kt`、任何 res / manifest / 测试 / 配置 / CSV / 其他窗口报告；`android/app/src` 下**无任何文件在最近 2 小时内被修改**（实测）。
- **未**在共享仓库 stage / commit / push / reset / checkout / stash；**未**迁目录；**未**触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`、设备、MuMu / ADB、正式书库或用户资产。
- **未连接任何设备**，**未运行 adb**，**未使用 MuMu**；**不声称冷启动第一帧已在任何设备验收**。
- 本轮所有新构建缓存、临时文件、日志与产物**均在 E 盘**（`GRADLE_USER_HOME` 指向本任务 E 盘目录）。
- 与另一窗口的并发说明：本轮进行期间，共享仓库工作树新增了另一窗口的 `docs/qa/2026-09/2026-09-29-stage-e-b1-b2-library-mumu-acceptance.md`（23:24:10）；该文件不属本窗口，未被读取或修改。本候选基于 `git archive HEAD` 与提取时的 S06 源文件 SHA（§9.2 已记录），不受该并发写入影响。
- 全文只用中性标识，未记录任何真实书名。
