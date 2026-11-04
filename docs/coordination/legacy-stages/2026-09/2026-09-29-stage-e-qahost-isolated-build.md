# 阶段 E 候选 G2 隔离构建：HEAD + qahost 测试基建 · 编译与生成配置证据（2026-09-29）

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；基线 HEAD `5aed5939600e491843e3c44453921bd6273a233c`（android/ 子树经 `git archive HEAD` 提取）。未进入/未修改原仓库。
- 性质：**G2 候选的 E 盘隔离构建与证据采集**。仓库内唯一写入 = 本报告；全部构建/产物/日志在 `E:\creation-reading-assistant\stage-e-g2-isolated-20260929\`。**零设备操作**（MuMu/真机为窗口 1 独占，本轮未运行 ADB）；未改恢复仓库源码、测试或其他窗口文件；未 stage/commit/push/reset/checkout。
- 输入：[首批候选复核](../../reviews/2026-09/2026-09-29-stage-e-first-candidates-review.md)（§下一验收第 2 条：U 与 G2 各自做 HEAD+精确候选编译；G2 普通 debug runner 冒烟按风险执行）、[G2 候选报告](2026-09-29-stage-e-qahost-candidate.md)（矩阵 SHA `2914336c…595d8`）、[收口计划](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md) §E。
- 隔离目录：`E:\creation-reading-assistant\stage-e-g2-isolated-20260929\`（基线树、候选、补丁、日志、产物、清单；构建的 `.gradle`/`build` 目录均在其中，未触碰恢复仓库构建目录）。

## 1. 候选组成与输入核对（全部 SHA-256）

候选 = **HEAD 基线 + `build.gradle.kts` 的 U0 hunk A/C/D/E + 三个 androidTest 新文件**；排除 hunk B（版本号，属 U 组）与全部 G0 资产。

| 输入 | SHA-256 |
|---|---|
| `candidate-build.gradle.kts`（= 基线合入后的文件，与基线内同文件） | `18eed550…77190b` |
| `testing/CraIsolatedTestRunner.kt` | `343b7fb1…a6b074`（与上轮矩阵一致） |
| `testing/CraIsolatedTestApplication.kt` | `286b5250…ac9856`（同上） |
| `feature/reader/IsolatedTestContext.kt` | `7d97e13b…099c038`（同上） |
| `gradle/libs.versions.toml`、`settings.gradle.kts`、`gradle.properties`、`gradle-wrapper.properties` | `bf1f38fb…` / `16407902…` / `e1c184a7…` / `3d91f093…`（工作树与 HEAD 逐字节相同，双侧有效） |
| `local.properties`（SDK 路径，未跟踪构建输入，仅复制） | `7e780635…06aa` |

**候选构造的双向校验**（构造方法：`git apply` 因 CRLF/LF 弃用，改为程序化回退 B hunk）：

1. `candidate-build.gradle.kts` vs `git show HEAD:android/app/build.gradle.kts`（忽略行尾）：**差异 = 且仅 = hunk A/C/D/E**（testBuildType 插入、runner 替换、qahost build type、qahostImplementation），无任何其他差异；
2. `candidate-build.gradle.kts` vs 工作树文件：**差异 = 且仅 = 版本号 hunk B**（versionCode 3→2、versionName 0.5.1→0.5.0 及注释回退）。

两条合起来证明候选内容恰为 HEAD + G2 四 hunk，不带 G1/G0 的任何内容。

## 2. 两次隔离构建（命令、退出码、日志）

| # | 命令（在 `E:\…\stage-e-g2-isolated-20260929\baseline\android`） | 退出码 | 结果 |
|---|---|---|---|
| 1 | `gradlew.bat :app:assembleDebugAndroidTest --offline --console=plain`（**不带** `craAndroidTestBuildType` 属性 → `testBuildType` 走默认 `"debug"`） | **0** | `BUILD SUCCESSFUL in 3m 3s`，52 tasks（31 executed, 21 from-cache）；日志 `build1-debug-androidtest.log` |
| 2 | `gradlew.bat :app:assembleQahostAndroidTest :app:assembleQahost -PcraAndroidTestBuildType=qahost --offline --console=plain` | **0** | `BUILD SUCCESSFUL in 2m 8s`，72 tasks；日志 `build2-qahost-androidtest.log` |

两次构建为独立调用——这正是 hunk A 注释所述 AGP 约束（一次调用只产一个 build type 的 androidTest），两次各自成功证明候选在**两种 testBuildType 配置下**均可编译打包。

产物 SHA-256：

| 产物 | SHA-256 |
|---|---|
| `app-debug-androidTest.apk`（debug 变体 androidTest） | `67f533d7…a8a4d` |
| `app-qahost-androidTest.apk` | `f9021131…44e9d` |
| `app-qahost.apk`（qahost 主 APK） | `2fc8a530…ce60` |

**隔离编译边界就此关闭**（上轮报告与首批复核均记"待验证"）：HEAD + 仅 G2 候选对 debug 与 qahost 两条 androidTest 链均可编译打包，G2 不依赖 G0/G1 的任何 hunk。

## 3. defaultConfig runner 改动对普通 debug 变体的实际生成配置（硬证据）

`aapt dump xmltree`（build-tools 34.0.0）对两个 androidTest APK 的 manifest：

| APK | `instrumentation android:name` | `android:targetPackage` |
|---|---|---|
| **debug**（未带属性构建） | `com.creationreadingassistant.testing.CraIsolatedTestRunner` | `com.creationreadingassistant` |
| qahost | `com.creationreadingassistant.testing.CraIsolatedTestRunner` | `com.creationreadingassistant.qahost` |

**结论：defaultConfig 的 runner 替换对普通 debug 变体实际生效**——不带任何属性构建出的 debug androidTest APK，其 instrumentation 已是 `CraIsolatedTestRunner`（targetPackage 仍为正式包）。这正是 G2 报告与首批复核登记的行为变更：合入 G2 后，日常 debug androidTest 的 instrumentation 进程 Application 将换成裸 `CraIsolatedTestApplication`。类存在性补证：debug androidTest APK 的 `classes11.dex` 含 `testing/CraIsolatedTestRunner`（7 处引用）与 `testing/CraIsolatedTestApplication`（5 处）——manifest 声明与 dex 内容一致。

**判别性副证（B hunk 排除生效）**：`aapt dump badging app-qahost.apk` → `package: name='com.creationreadingassistant.qahost' versionCode='2' versionName='0.5.0'`——候选产物版本号停在 0.5.0(2)，证明 G2 未携带 U 组的版本号变更。

## 4. 明确未验证（NOT RUN，不外推）

1. **普通 debug 变体设备行为 = NOT RUN**。本轮零设备操作；debug androidTest APK 只完成了编译、打包、manifest/dex 证据。已生成的 debug androidTest APK 可以直接供窗口 1 做最小冒烟：`adb install -t` 主 debug APK + 该 androidTest APK 后，抽 1–2 个 Compose 类（如 `DurationPillGestureTest`）以 `-e class` 定向运行，核对 instrumentation 进程 Application 为裸隔离 Application 时测试仍通过。**不得以 qahost 62/62 或 EPUB 8/8 外推 debug 设备行为**——那两项的宿主是 qahost 沙箱（独立 applicationId），与正式包 debug 的运行环境（`App` 为清单 Application、WorkManager/夜间模式等系统级副作用面）不同。
2. **JVM 单测与 Lint 未在隔离目录运行**（本轮范围仅两种 androidTest 编译打包；G2 不改 main 源集，JVM 风险极低，如需可在同目录补跑定向 JVM）。
3. **V5 62/62、EPUB 8/8 仍属"全工作树组合态"历史证据**（G2 报告 §4 口径不变）；G6/UI 测试文件与 G0 的 EPUB 测试文件未随本候选合入，其设备结果在本候选上**不可复跑**，也不在本候选的验证主张内。

## 5. 证据清单与工件位置

`E:\creation-reading-assistant\stage-e-g2-isolated-20260929\`：

- `baseline/`（HEAD android 树 + G2 候选覆盖；构建 `.gradle`/`build` 均在其中）
- `candidate-build.gradle.kts`、`build-gradle-full-worktree.patch`、`g2-build-gradle.patch`（A/C/D/E 四 hunk；供复核重放，`git apply` 因 CRLF 不可直接用于工作树）
- `build1-debug-androidtest.log`、`build2-qahost-androidtest.log`（完整原始日志）
- 三个 APK（SHA 见 §2）
- `g2-input-and-build-manifest.txt`（输入/命令/产物/manifest 证据汇总，SHA-256 `fcd2d432f484570358a2931e6dc7bae0d6e39b4049dcb611566a0f6f3bb59a92`）

## 6. 边界声明

- 本轮 Gradle 全部在 E 盘隔离目录执行，`--offline`，使用共享依赖缓存只读；未触碰恢复仓库的构建目录、`.gradle`、源码、测试、公共文件与他人报告；未运行 ADB/MuMu；未 stage/commit/push/reset/checkout。
- 构建产物中的版本号证据（0.5.0(2)）仅用于证明 hunk 排除，不构成发布产物；qahost/debug APK 均为本地验证构建，不可发布（既有 debug 签名兜底约束不变）。
- 落盘即停，交总指挥复核。G2 的最小提交形态与剩余验证点以 [G2 候选报告](2026-09-29-stage-e-qahost-candidate.md) §3/§6 为准，本报告新增关闭项：**隔离编译边界**。
