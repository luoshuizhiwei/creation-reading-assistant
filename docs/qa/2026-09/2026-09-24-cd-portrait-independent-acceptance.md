# C/D 批次真机独立验收报告（2026-09-24 竖屏）

- **验收角色**：WorkBuddy（独立验收，不修代码）

> **署名说明（2026-09-24）**：「WorkBuddy」是本报告**产出时的执行者署名**，保留作来源标识。
> 它**不是**当前仍在使用的独立应用或常驻服务；本仓库的后续工作由其他 agent/集成者承接。
> 引用本报告时请以文件路径与章节号为准，不要假定该署名仍代表某个活跃进程。
- **工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **验收范围（仅两组）**：
  - **C**：`PageTurnerHighSpeedTest` 新增 5 项
  - **D**：`CraSliderRtlRenderTest` 4 项
- **设备**：仅用当前连接的真实物理手机，**未使用 MuMu 或任何模拟器**，**仅竖屏**。
- **环境约束遵守**：未触碰用户书库、未运行导入或索引重建、未修改系统语言 / `force_rtl` /
  MIUI 安全设置；未执行 `pm clear`、卸载、重置应用数据、删除书籍、移动用户文件；
  设备文件**未做任何通配删除或根目录清理**。

---

## 0. 结论摘要（先说结果）

| 组 | 用例数 | 结果 |
|---|---|---|
| **C 新增 5 项** | 5 | **全部「未运行」**（未发起） |
| **D 4 项** | 4 | **1 项 BLOCKED**（`tapNearLeftEdgeYieldsHighValueInRtl`，已发起无结果）<br>**3 项「未运行」** |

**没有任何一项 PASS 或 FAIL。** 本轮以原始日志直接证明了两件事：

1. **Compose 测试宿主 Activity（`androidx.activity.ComponentActivity`）的启动遭到拦截** ——
   设备侧日志有 `Abort background activity starts`、`MIUILOG- Permission Denied Activity`、
   `result code=102`（见 §4.2b，本轮自身的时间戳）。
2. **代表用例（D-1）在 180 秒外部超时前没有产出任何完成结果** ——
   `started` 已打出，`finished` 始终未出现（见 §4.2）。

此外，同一次会话、同一台设备、同一个 test APK 上，非 Compose 的 `AppDatabaseMigrationTest`
跑出 **OK (23 tests)、1.125 秒**，说明 instrumentation 通道本身可用。

> **措辞更正（2026-09-24，证据口径收窄）**
> 本节早先写有「原因不是代码缺陷」「测试线程永久等待 Activity」等表述，**超出本轮证据所能支持的范围**，
> 现收窄如下，原文痕迹保留：
> - 「原因不是代码缺陷」→ 本轮**未取得任何一项用例的运行结果**，故**无法**对被测代码是否含缺陷
>   作出任何判断。能说的只是：日志显示 Activity 启动被拦，用例因此未跑到断言。
>   **目标产品行为（C 的翻页契约、D 的 RTL 几何）以及其他潜在缺陷，仍然全部未验证。**
> - 「测试线程永久等待」→ 该等待过程**没有**被本轮日志直接观测（未抓线程栈、未做 ANR 取证）。
>   可直接证明的范围是：`started` 之后 180 秒内**无完成结果**。
> - 「环境侧阻塞」→ 保留为**最贴合现有证据的解释**，但它是**推断**，不是已证实的根因；
>   详见 §4.2 二分判据一节。
>
> **C 的 5 项与 D 的 3 项均为「未运行」，既不得写成失败，也不得写成通过。**

> **本轮对历史悬案的处置**：09-23 报告中「MIUI 拦截」属于**跨轮类比、无本轮证据**，
> 本轮以未截断 logcat **首次取得本轮自身的实证**（见 §4.2）。这是本轮最重要的结果。

---

## 1. 只读门禁

### 1.1 Git 与源码哈希

| 项 | 值 |
|---|---|
| HEAD | `5aed5939600e491843e3c44453921bd6273a233c` |
| 分支 | `codex/recover-worktree-20260920` |
| staged 文件数 | **0** |

| 目标测试文件 | SHA-256 |
|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/pager/PageTurnerHighSpeedTest.kt` | `7e8db0f9a431dad4275adf30f374f36f4096f661795fd479dcd8a8f0c0b6287c` |
| `android/app/src/androidTest/java/com/creationreadingassistant/ui/components/CraSliderRtlRenderTest.kt` | `5017af8de3ca361145f126c48b018fc547e3a8656ef034e373d176f0a212aa46` |

后者与 `docs/qa/2026-09/2026-09-24-d-rtl-compose-test.md` §1 声明的
`5017af8de3ca361145f126c48b018fc547e3a8656ef034e373d176f0a212aa46` **逐位一致** ——
即本轮验收的正是窗口 D 交付的那份源码，未被改动。

### 1.2 设备身份

```
$ adb devices -l
List of devices attached
c49ac6cf    device product:diting model:22081212C device:diting transport_id:2
```

- **唯一连接的设备**：`c49ac6cf`，`state=device`。所有设备命令均显式 `adb -s c49ac6cf`。
- **真机判定**：`ro.hardware=qcom`（高通硬件），`ro.kernel.qemu` **为空** ⇒ **非模拟器**。
- **方向**：`mCurrentRotation=ROTATION_0`、`mDisplayRotation=ROTATION_0`、`mBounds=Rect(0,0 - 1220,2712)`、`port` ⇒ **严格竖屏**。
- **系统**：Android 15（`ro.build.version.release=15`）。

> **⚠️ 与历史报告的出入（如实记录，未处置）**：历史报告（09-23 等）记载设备为
> `Xiaomi 22011211C`，本轮 `adb devices -l` 显示 **`model:22081212C`**，
> `ro.product.model=22081212C`。两者**相差一位数字**。本轮未对该差异做推断或处置，
> 仅如实记录；建议总指挥核实是历史笔误、还是期间更换过设备。
> 门禁判定**不受影响**（真机、唯一、竖屏三项均成立）。

### 1.3 设备设置原值（只读回读，全程未改动）

| 设置 | 验收前原值 | 验收结束回读 | 是否改动 |
|---|---|---|---|
| `stay_on_while_plugged_in` | `3` | `3` | **未触碰** |
| `persist.sys.locale` | `zh-CN` | `zh-CN` | **未触碰** |
| `debug.force_rtl` | `0` | `0` | **未触碰** |
| `mCurrentRotation` | `ROTATION_0` | `ROTATION_0` | **未触碰** |

> 本轮测试总耗时短，且阻塞后即终止，**无需**调整亮屏时长，故 `stay_on_while_plugged_in`
> **一次都没有改过**。

---

## 2. 构建与安装

### 2.1 构建

| 命令（在 `android/`，均带 `--offline`） | 退出码 | 结果 |
|---|---|---|
| `./gradlew :app:assembleDebug --console=plain` | **0** | `BUILD SUCCESSFUL in 10s`；41 tasks **全部 UP-TO-DATE** |
| `./gradlew :app:assembleDebugAndroidTest --console=plain` | **0** | `BUILD SUCCESSFUL in 12s`；52 tasks，**4 executed**（`transformDebugAndroidTestClassesWithAsm` / `dexBuilderDebugAndroidTest` / `mergeProjectDexDebugAndroidTest` / `packageDebugAndroidTest`） |

| 产物 | 字节 | mtime | SHA-256 |
|---|---|---|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | 52,482,008 | 2026-09-23 22:58:59 | `acab95ba002101dd83eefa605d0d6bed7fbc93e5c273cddeb9d9a38f1c8477a8` |
| `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1,512,197 | **2026-09-24 01:51:44** | `716728d60a08b39373dbf532e47583fa8c53cbfda3b9a40addbe25fccf24d632` |

**说明两点**：

1. 主 APK 全程 UP-TO-DATE，哈希与 09-23 门禁报告一致 —— 产品源码自那时起未变，符合预期。
2. **test APK 必须重建**：仓库内旧产物是 09-23 22:59 的，早于 C（09-24 00:5x–01:0x）与
   D（09-24 01:2x）的测试源码改动。若不重建，设备上跑的将是不含 D 用例的旧包。
   本轮重建后 **1,512,197 字节（旧 1,498,298），哈希已变**。

**新包内容取证**（证明打包的确实是新用例，而非 UP-TO-DATE 假绿）：

```
$ javap -p .../CraSliderRtlRenderTest.class | grep "public final void"
  public final void tapNearLeftEdgeYieldsLowValueInLtr();
  public final void tapNearLeftEdgeYieldsHighValueInRtl();
  public final void activeTrackIsPaintedOnLeftInLtr();
  public final void activeTrackIsPaintedOnRightInRtl();
```

D 的 4 条用例**已确实打进 test APK**。

### 2.2 安装 —— 脚本原生确认成功，**无回退**

两者均使用 `android/scripts/install_with_confirm.ps1`，**均未经 ADB 回退**：

| APK | 命令 | 退出码 | RESULT |
|---|---|---|---|
| 主 APK | `pwsh -ExecutionPolicy Bypass -File android/scripts/install_with_confirm.ps1 -Serial c49ac6cf -Apk .../app-debug.apk` | **0** | **`RESULT: CONFIRM_LOOP`**（经 MIUI 安装器 UI 完成） |
| test APK | `pwsh … -Apk .../app-debug-androidTest.apk -TestApk -PackageName com.creationreadingassistant.test` | **0** | **`RESULT: CONFIRM_LOOP`**（经 MIUI 安装器 UI 完成） |

脚本日志可见完整的 MIUI 安装器交互序列：
`TAP installer control 'android:id/button3'` → `'com.miui.packageinstaller:id/checkbox'` →
`'com.miui.packageinstaller:id/second_button'`，最后
`Installed com.creationreadingassistant[.test] successfully.`

**⇒ 本轮无需也无法使用 `adb install -r` 回退；没有任何一处写成「脚本通过」的回退。**
包名复核：`pm list packages` 显示 `com.creationreadingassistant` 与
`com.creationreadingassistant.test` 均在。

原始日志（仓库外私有位置）：
`C:\Users\23254\.workbuddy-scratch\cd-acceptance-20260924\evidence\install-main.log`（1,235 B）、
`…\install-test.log`（1,087 B）。

---

## 3. 测试方法

- 所有 instrumentation 调用**均带 `-r`**（09-23 那次不带 `-r`，故无判别力 —— 见
  `docs/qa/2026-09-24-c-high-speed-test-diagnosis.md` §6.1）。
- 每条用例**独立运行**，外部有界超时 `timeout 180`，记录退出状态。
- 顺序：先跑**不依赖 Compose Activity 的既有对照用例**，再从 **D 的一条 RTL 用例**开始。

---

## 4. 执行结果

### 4.1 对照用例（非 Compose）—— **PASS，通道健康**

```
$ timeout 180 adb -s c49ac6cf shell am instrument -w -r \
    -e class com.creationreadingassistant.data.local.AppDatabaseMigrationTest \
    com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
```

- **退出码 0**，`numtests=23`，23 组 `STATUS_CODE: 1`（started）→ `STATUS_CODE: 0`（finished），
  末尾 `Time: 1.125` / **`OK (23 tests)`**。
- 原始日志：`…\evidence\control-instrument.log`（16,531 B）。

**这条对照是本轮最有价值的分界证据**：同一台设备、同一次会话、同一个 test APK、
同一个 runner，纯 Room 用例 1.1 秒全绿。⇒ instrumentation 通道、test APK 安装、
runner 注入、结果回传**全部正常**；后续阻塞**不能**归因于这些环节。

### 4.2 D-1 `tapNearLeftEdgeYieldsHighValueInRtl` —— **BLOCKED**

```
$ timeout 180 adb -s c49ac6cf shell am instrument -w -r \
    -e class com.creationreadingassistant.ui.components.CraSliderRtlRenderTest#tapNearLeftEdgeYieldsHighValueInRtl \
    com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
```

- **退出码 124** = 外部 `timeout 180` 触发（挂满 180 秒）。
- 原始输出**完整 414 字节**（`…\evidence\d1-rtl-tap.log`）：

```
INSTRUMENTATION_STATUS: class=com.creationreadingassistant.ui.components.CraSliderRtlRenderTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=
com.creationreadingassistant.ui.components.CraSliderRtlRenderTest:
INSTRUMENTATION_STATUS: test=tapNearLeftEdgeYieldsHighValueInRtl
INSTRUMENTATION_STATUS_CODE: 1
```

  **有 `STATUS_CODE: 1`（started）、无 `STATUS_CODE: 0`（finished）、无 `INSTRUMENTATION_RESULT`、
  无 `INSTRUMENTATION_CODE`、无失败栈** ⇒ 该用例**已发起但无结果**。

#### 阻塞现场的原始取证（按指令：立即保存 + 未截断 logcat + 前台状态）

| 证据 | 文件（仓库外私有位置） | 大小 |
|---|---|---|
| 未截断 logcat（`logcat -d -v time`，**不加 `-t`、不先 grep**） | `…\evidence\d1-logcat-full.txt` | **27,682,757 B** |
| `dumpsys activity activities` | `…\evidence\d1-dumpsys-activities.txt` | 30,228 B |
| 前台焦点 | `…\evidence\d1-dumpsys-focus.txt` | 161 B |

**（a）前台状态**：

```
mCurrentFocus=Window{74ed283 u0 com.miui.home/com.miui.home.launcher.Launcher}
mFocusedApp=ActivityRecord{71f49d0 u0 com.miui.home/.launcher.Launcher t9478}
```

前台仍是 MIUI 桌面，`ComponentActivity` **未起到前台**。

**（b）本轮自身的 MIUI 拦截实证**（`d1-logcat-full.txt`，时间戳 `09-24 01:54:44`，与本轮一致）：

```
171692: 01:54:44.891 D/ActivityStarterImpl(2591): MIUILOG- Permission Denied Activity :
        Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]
        flg=0x10008000 cmp=com.creationreadingassistant/androidx.activity.ComponentActivity }
        pkg : com.creationreadingassistant uid : 10949 tuid : 10085
171711: 01:54:44.900 E/ActivityTaskManager(2591): Abort background activity starts from 10949
171714: 01:54:44.903 I/ActivityTaskManager(2591): START u0 {…cmp=com.creationreadingassistant/
        androidx.activity.ComponentActivity} with LAUNCH_MULTIPLE from uid 10949 from pid 11037
        callingPackage com.creationreadingassistant (BAL_ALLOW_PERMISSION) result code=102
```

**（c）runner 侧时间线**（同一份 logcat）：

```
170588: 01:54:43.472 I/adbd(2151): in ShellService: am instrument -w -r -e class …CraSliderRtlRenderTest#tapNearLeftEdgeYieldsHighValueInRtl …
171593: 01:54:44.733 I/AndroidJUnitRunner(11037): onCreate Bundle[{class=…#tapNearLeftEdgeYieldsHighValueInRtl}]
171663: 01:54:44.806 D/TestRequestBuilder(11037): Skipping class path scanning and directly running […CraSliderRtlRenderTest]
171673: 01:54:44.819 I/TestRunner(11037): started: tapNearLeftEdgeYieldsHighValueInRtl(…)
```

即：`started` 打出后 **0.08 秒内** Activity 启动即被拦截，此后**再无** `finished`。

#### 二分判据结论（回答「环境拦截」还是「测试自己等不到」）

按 `2026-09-24-c-high-speed-test-diagnosis.md` §6 给出的判据：

> **有 started、无 finished ⇒ 卡在测试体内；连 started 都没有 ⇒ 卡在 runner/Activity 层。**

本轮实测为 **「有 started、无 finished」**，按该判据字面指向**测试体内**。但本轮**不停留在字面**，
因为同一份 logcat 里有更直接的证据：`ComponentActivity` 的 `START … result code=102`
就发生在 `started` 之后 0.08 秒内，且前台始终停在 MIUI 桌面。

⇒ 更精确的表述是：**`createComposeRule()` 启动 `ComponentActivity` 的那一步在日志中出现
`result code=102`（未成功启动），随后该用例在 180 秒内没有产出完成结果。**
这与诊断文档 §2 的 **H1 / H2 挂点**（环境侧，不受测试代码控制）相吻合，
而**不是** H3–H7（测试体内等待）—— 那 5 处在改后代码里已全部消除或变为有界。

**因此（措辞更正 2026-09-24）：**
- 本轮**不推断**为「被测代码缺陷」，也**不推断**为「测试自身无限等待」——
  两者均无直接证据：前者因无运行结果，后者因未抓线程栈/ANR 取证。
- 诊断文档 §0.2 留下的两种竞争解释（环境拦截 vs 测试体内等待），现有证据**倾向前者**
  （依据是 §4.2b 那条 Activity 启动失败日志），但**未 100% 排除后者**，
  因为 `started` 已打出、且未观测到实际等待位置。
- 「环境侧阻塞」是**最贴合证据的解释**，按此记账；但它是**推断**而非已证实的根因。
- **无论归因如何，结论不变**：D-1 无结果，C 5 项与 D 3 项未运行，目标行为全部未验证。

#### 终止处置

- 已执行 `am force-stop com.creationreadingassistant` + `am force-stop com.creationreadingassistant.test`，
  复核 `ps -A | grep creationreading` **为空** ⇒ 挂起进程已终止。
- 按指令**停止后续同类重试**：D 剩余 3 项与 C 全部 5 项**均未再发起**。

### 4.3 逐用例结果表

#### D：`CraSliderRtlRenderTest`（4 项）

| # | 用例 | 状态 | 原始输出 | 说明 |
|---|---|---|---|---|
| D-1 | `tapNearLeftEdgeYieldsHighValueInRtl` | **BLOCKED** | `d1-rtl-tap.log`（414 B，started 无 finished） | 已发起，Activity 被 MIUI 拦截 |
| D-2 | `tapNearLeftEdgeYieldsLowValueInLtr` | **未运行** | — | 按指令在同类阻塞后停止重试 |
| D-3 | `activeTrackIsPaintedOnLeftInLtr` | **未运行** | — | 同上 |
| D-4 | `activeTrackIsPaintedOnRightInRtl` | **未运行** | — | 同上 |

- 完成数 **0**，失败数 **0**，失败栈 **0**（无任何结果产出）。

#### C：`PageTurnerHighSpeedTest` 新增 5 项

| # | 用例 | 状态 | 说明 |
|---|---|---|---|
| C-1 | `singleTapAdvancesExactlyOnePage` | **未运行** | 本轮未发起（D 组首个 Compose 用例已证同类路径阻塞） |
| C-2 | `threeRapidTapsDuringAnimationAdvanceThreePages` | **未运行** | 同上 |
| C-3 | `oppositeTapReplacesUnstartedBacklog` | **未运行** | 同上 |
| C-4 | `burstBeyondCapacityIsBounded` | **未运行** | 同上 |
| C-5 | `tapsAtLastPageStillConsumeRequestsWithoutStalling` | **未运行** | 同上 |

- 完成数 **0**，失败数 **0**。
- **未把未运行的 5 项写成通过，也没有用模拟器替代。**

> 补充说明：`PageTurnerHighSpeedTest.java` 内另有 3 个既有用例
> （`slideHighSpeedTurnNeverShowsABlankFrame` / `coverHighSpeedTurnNeverShowsABlankFrame` /
> `advancingTurnNeverShowsABlankFrame`）**不在本轮范围内**，未运行。

---

## 5. 「编译成功」≠「设备通过」（严格分界）

- **编译**：两个 APK 均 `BUILD SUCCESSFUL`，退出码 0；`CraSliderRtlRenderTest` 4 个方法经
  `javap` 确认已打进 test APK。这只证明**类型正确、可打包**。
- **运行**：C 5 项 + D 4 项 = **9 项，在设备上产出结果的为 0**。
- **本报告不把任何一项记为设备通过。** 唯一在设备上跑通的 23 项是对照组
  `AppDatabaseMigrationTest`，它**不属** C/D 范围，只用作通道健康度证据。

---

## 6. 未覆盖内容（不粉饰）

1. **C 全部 5 项在设备上零覆盖**：高速翻页、三连点排队、反向替换、爆发上限、
   末页不卡死 —— 五项行为**均未在真机上被观察过**（历史上也从未跑完过）。
2. **D 的 RTL 几何与方向语义零覆盖**：LTR/RTL 的数值互补（<0.40 / >0.60）与
   自绘轨道左右互换（25%/75% 列 tint 有无）**均未取得真机结果**。
   窗口 D 交付文档中 R1–R5 的五项真机风险（tap 是否产出数值、`captureToImage` 对 Slider
   可用性、采样窗口密度敏感性等）**本轮一条也没能验证**。
3. **拇指与已走段边缘贴合**（诊断文档 R5）：不在本轮测试断言内，也未做真机目视。
4. `CraSlider` 的 JVM 几何测试 8/8（历史既有）属**纯数学性质断言**，
   **不得**据此记作真机 RTL PASS —— 与仓库既有口径一致。
5. **未做横屏**（按指令），故横屏下行为仍未知。

---

## 7. SEAM REQUEST

本轮**没有取得任何用例的运行结果**，因此**既未发现也未排除源码缺陷**，
**不提交代码缺陷类 SEAM REQUEST**。以下是**环境/流程层面**需要他人所有权才能推进的事项：

- **SR-ENV-1（设备侧阻塞，需总指挥决策）**：`ComponentActivity` 被 MIUI 以
  `result code=102` 拦截已**连续三轮**（09-22、09-23、09-24）复现。本轮虽首次拿到本轮自身的
  原始日志实证，但**仍未能让任何一个 Compose 用例跑完**。建议决策其一：
  (a) 换一台不受该策略约束的设备做 Compose 用例验收；
  (b) 把 Compose 用例的验收移到 CI（Firebase Test Lab / Gradle Managed Device）；
  (c) 明确接受"Compose instrumentation 在本机不可验"，并把 C/D 的门禁改为 JVM 侧替代。
  **本轮无权改 CI 或换设备，等待裁定。**
- **SR-ENV-2（`stay_on_while_plugged_in`，需用户/总指挥授权）**：若后续要在真机上跑完整批次
  （C 的 5 项按诊断文档 R4 估计合计 <1 分钟，D 4 项更短），需要保持亮屏。
  本轮**未改**该值（原值 `3`），也未请求授权；若后续需要，请明确授权后我再记录原值并恢复。
- **SR-DOC-1（文档一致性，属主为历史报告作者）**：设备型号不一致
  （历史记 `22011211C`，本轮实测 `22081212C`），建议核实并统一，避免后续门禁报告连锁引用错误。

---

## 8. 边界声明

- **未修改**：产品源码、测试源码、`CraSlider.kt`、`GoalSubPage.kt`、`AndroidManifest.xml`、
  `PageTurnerHighSpeedTest.kt`、`CraSliderRtlRenderTest.kt` —— **一个字都没改**。
- **git**：未 `reset` / `checkout` / `clean` / `stash` / `stage` / `commit` / `push`。
  本轮只**新增**本报告一个文件；HEAD 仍为 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0 项。
- **设备**：未切系统语言、未改 `force_rtl`、未改 MIUI 安全设置、未 `pm clear`、
  未卸载、未重置应用数据、未删除书籍、未移动用户文件、**未做任何通配删除或根目录清理**。
  唯一对设备的写操作是安装两个 APK（经脚本原生确认）与阻塞后的 `am force-stop`（终止挂起进程）。
- **未触碰用户书库**，未运行导入或索引重建。
- **隐私**：原始日志（含设备标识、进程号、包名的 27 MB logcat）全部保存在**仓库外私有位置**
  `C:\Users\23254\.workbuddy-scratch\cd-acceptance-20260924\evidence\`，**未加入仓库**。
  本报告只写脱敏摘要与中性测试名，未记录任何真实书名或私有路径。
- **未使用** MuMu 或任何模拟器；**未做横屏**。

---

## 9. 原始证据位置（全部在仓库外）

```
C:\Users\23254\.workbuddy-scratch\cd-acceptance-20260924\evidence\
├── install-main.log            1,235 B    主 APK 安装脚本日志（CONFIRM_LOOP）
├── install-test.log            1,087 B    test APK 安装脚本日志（CONFIRM_LOOP）
├── control-instrument.log     16,531 B    对照组 AppDatabaseMigrationTest，OK (23 tests)
├── d1-rtl-tap.log                414 B    D-1 原始输出（started 无 finished）
├── d1-logcat-full.txt      27,682,757 B   阻塞当下未截断 logcat
├── d1-dumpsys-activities.txt 30,228 B     dumpsys activity activities
└── d1-dumpsys-focus.txt          161 B    前台焦点（com.miui.home）
```

**未把任何原始日志写进仓库。**

---

## 10. 给 Codex 复核的要点

1. 本轮已取得**本轮自身**的 `Abort background activity starts` + `result code=102`（§4.2b），
   可直接复核行号 ⇒ 「MIUI 拦截是否为跨轮类比」这一质疑对本轮**不成立**。
2. 但请注意该证据的**边界**：它证明的是**宿主 Activity 启动失败**与**用例 180 秒内无完成结果**，
   **不能**据此推出「代码无缺陷」或「测试线程永久等待」—— 后者未取证。
3. **不能**把 C 的 5 项或 D 的 3 项写成通过 —— 它们**一项都没跑**，也不得写成失败。
4. 对照组 23/23 全绿是**通道可用**的强证据，请一并复核，它决定了阻塞的归因边界。
5. 设备型号差异（§1.2）请一并核实。
6. 关于「是否有不改手机安全设置、仍能在真机跑 Compose 断言的宿主路径」，
   见后续只读调查：`docs/qa/2026-09/2026-09-24-compose-test-host-investigation.md`。
