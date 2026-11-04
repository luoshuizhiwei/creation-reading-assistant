# Compose 测试宿主路径只读调查（2026-09-24）

- **性质**：纯离线、只读调查。**未运行 adb、未操作手机、未跑任何设备测试、未改任何代码。**
- **触发**：`docs/qa/2026-09/2026-09-24-cd-portrait-independent-acceptance.md` 中，D-1 用例
  `started` 后 180 秒无结果；设备日志显示 `androidx.activity.ComponentActivity` 启动返回
  `result code=102`，并伴有 `Abort background activity starts`。
- **问题**：是否存在**不改手机安全设置**、**仍在真实手机上运行 Compose 断言**的测试宿主路径？
- **方法**：对**本机实际参与构建的产物**做反编译取证（`javap`）+ 仓库 manifest / 依赖核对，
  **不凭名称猜测**。凡无法由本机产物证明的，均标注为「尚需验证的前提」。

---

## 0. 结论（先给答案）

**暂无可信绕过方案。**

四个候选方案逐一核对后：三个在机制上与现有失败路径**同源**（仍是从后台发起 Activity 启动），
一个（`Robolectric`）能跑但**不满足「真实手机」**这一硬要求。

| # | 方案 | 能否避开同一限制 | 是否真实手机 | 判定 |
|---|---|---|---|---|
| A | 替换 `createComposeRule` 为 `createAndroidComposeRule(<自己的 Activity>)` | **否**（同一启动路径） | 是 | 不可行 |
| B | 预先启动宿主 Activity（先 `am start` 再跑测试） | **否**（已有反例 + 机制同源） | 是 | 不可行 |
| C | 调整测试 Activity 生命周期 / 换 `EmptyActivity` 中转 | **否**（中转本身就是被拦风格） | 是 | 不可行 |
| D | `Robolectric` / JVM 侧 | 是（根本不启 Activity） | **否** | 只能作补充门禁 |

**建议**：改用**另一台真实手机**（不受该策略约束的设备），而不是继续重试同一命令。
CI（Gradle Managed Device / Firebase Test Lab）可作为**替代门禁**，但**不能冒充**用户要求的
「真实手机验收」——除非用户明确接受。

---

## 1. 先钉死失败点：是谁在启动 Activity

### 1.1 `createComposeRule()` 就是 `createAndroidComposeRule(ComponentActivity)`

反编译 `ui-test-junit4-android-1.9.0` 的 `AndroidComposeTestRule_androidKt`：

```
public static final ComposeContentTestRule createComposeRule();
  Code:
     0: iconst_0
     2: ldc   #9  // class androidx/activity/ComponentActivity
     4: invokestatic #13 // createAndroidComposeRule:(Ljava/lang/Class;)…
     7: checkcast #15    // ComposeContentTestRule
    10: areturn
```

无参 `createComposeRule()` **硬编码**了 `androidx.activity.ComponentActivity`，
随即转调 `createAndroidComposeRule(Class)`。**两个目标测试文件都用它**（均已核对源码）：
- `CraSliderRtlRenderTest.kt:67` → `createComposeRule()`
- `PageTurnerHighSpeedTest.kt:36` → `createComposeRule()`

### 1.2 `ComponentActivity` 由 `ui-test-manifest` 提供，且已 `exported=true`

```
ui-test-manifest-1.9.0.aar → AndroidManifest.xml
  package="androidx.compose.ui.test.manifest"
  <activity android:name="androidx.activity.ComponentActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar" />
```

（该 AAR 的 `classes.jar` 为空 ⇒ 它**只贡献 manifest 声明**，不含代码。）
仓库主 `AndroidManifest.xml` 中**没有** `androidx.activity.ComponentActivity` 的声明，
也没有 `androidTest` 专用 manifest ⇒ 该 Activity 完全来自这个测试依赖，经 manifest 合并进入 test APK。
主 manifest 已确认 `android:supportsRtl="true"`（第 32 行），与本次阻塞无关。

### 1.3 实际启动链：`ActivityScenarioRule` → `ActivityScenario` → `ActivityInvoker`

反编译 `androidx.test.ext:junit:1.1.5`：

```
public final class ActivityScenarioRule<A extends Activity> extends ExternalResource {
  …
  static ActivityScenario lambda$new$0(java.lang.Class);   // ActivityScenario.launch(Class)
}
```

`ActivityScenario.launch` 内部经 `androidx.test.internal.platform.app.ActivityInvoker`
（接口在 `monitor` 1.6.1/1.7.1），实现类由 **ServiceLoader** 解析；本机实际使用的实现是
`androidx.test.core.app.InstrumentationActivityInvoker`（在 `core-1.6.1.aar`）。

### 1.4 决定性证据：`InstrumentationActivityInvoker` 的两种启动方式都属「后台启动」

**（a）先起一个中转的 `EmptyActivity`**（`startEmptyActivitySync`）：

```
// class androidx/test/core/app/InstrumentationActivityInvoker$EmptyActivity
// int 268435456                    → 0x10000000 = FLAG_ACTIVITY_NEW_TASK
// Method android/content/Context.startActivity:(Landroid/content/Intent;)V
```

即从 **`Application` 的 Context**（无 Activity 上下文）发起 `startActivity`，带 `NEW_TASK`。
`core-1.6.1` 的 manifest 里 `BootstrapActivity` / `EmptyActivity` / `EmptyFloatingActivity`
均声明 `exported="true"` 且带 `category.LAUNCHER` 的 intent-filter（priority −100）。

**（b）再启动真正的目标 Activity**（`startActivity(Intent, Bundle)`）：

```
// int 268468224                    → 0x10008000 = NEW_TASK | CLEAR_TASK
// Method android/app/Instrumentation.startActivitySync:(Landroid/content/Intent;Landroid/os/Bundle;)…
```

**交叉验证**：`268468224 = 0x10008000`，与设备日志中实际出现的
`flg=0x10008000`（历史 logcat 中共 11 处）+ `cmp=…/androidx.activity.ComponentActivity`
**完全吻合** ⇒ 日志里的那次失败，正是这条 `InstrumentationActivityInvoker` 路径，
**不是**别的启动方式。这一点排除了「换个启动方式或许能绕开」的想象空间。

> **注意口径**：日志同时出现 `MIUILOG- Permission Denied Activity` 与
> `Abort background activity starts`，说明**该设备的策略层**（MIUI）在执行拦截。
> 但 Android 自 14 起 AOSP 也有 background activity launch（BAL）限制，
> 两者的**豁免条件不同**。本轮**未能**在本机产物中定位 `result code=102` 对应的确切枚举名
> （`android.jar` 中的 `ActivityManager.START_*` 常量未以可检索形式暴露），
> 故**不断言**它是 AOSP 标准码还是厂商自定义码 —— 列为前提 P1。

---

## 2. 四个方案的逐项核对

### 方案 A：把 `createComposeRule()` 换成 `createAndroidComposeRule(某个自有 Activity)`

**设想**：既然拦的是 `androidx.activity.ComponentActivity`，换成我们自己的
`MainActivity`（已 `exported=true`、有 LAUNCHER intent-filter）是不是就不拦了？

**核对结果：机制上不能避开。**

1. 反编译显示 `createAndroidComposeRule(Class<A>)` 与 `createComposeRule()` **走同一个实现**，
   差别只是传入的 `Class` 不同 —— 启动路径仍是
   `ActivityScenarioRule` → `ActivityScenario` → `InstrumentationActivityInvoker` →
   `Instrumentation.startActivitySync`（§1.3–1.4）。
2. 被拦的是**「从后台发起 Activity 启动」这一动作类别**，由 `Intent` 的 flag
   （`NEW_TASK|CLEAR_TASK`）与**发起方进程状态**决定，**不由目标 Activity 是谁**决定。
   换成 `MainActivity` 只是改了 `cmp=`，flags 与发起方式不变。
3. `ComposeContentTestRule`（`setContent` 系列）**必须**由一个 Compose 宿主 Activity 承载；
   用 `createAndroidComposeRule(MainActivity)` 还需要 `MainActivity` 能接受
   外部 `setContent`，这通常要改产品 Activity 或新增一个测试专用 Activity ——
   **属产品/测试源码改动，本轮无权做**。

- **预计改动文件**：`PageTurnerHighSpeedTest.kt`、`CraSliderRtlRenderTest.kt`
  （+ 可能需要新增宿主 Activity 与其 manifest 声明 ⇒ 触碰主 manifest）
- **风险**：改动面大（两个用例文件 + 可能的 manifest），且**预期无效**；
  若新 Activity 未声明 `exported` 还会引入新的启动失败模式。
- **能否保持真实手机验收**：理论是，但**达不到目的**（仍被拦）。
- **尚需验证的前提**：无 —— 机制证据已足够否定。
- **判定：不可行。**

### 方案 B：预先启动宿主 Activity（先 `am start` 让 App 在前台，再跑测试）

**设想**：拦截叫「background activity starts」，那我先让 App 处于前台，是不是就不算后台启动了？

**核对结果：已有历史反例，且机制上也不成立。**

1. **历史反例（最直接）**：`docs/qa/2026-09/2026-09-22-s09b-s15-duration-gesture-device-acceptance.md`
   第 93–96 行记载 —— 那轮**已经做过这个实验**："为排除『只是没在前台』这一变量，
   我把被测 App 先切到前台（`mCurrentFocus=…MainActivity`）后重跑一次，结果相同
   （attempt 2 也 124/0 完成）"。即**前台化后仍然失败**。
2. **机制上**：`InstrumentationActivityInvoker` 并没有走「当前前台 Activity 的
   `startActivity`」，而是从 **Application Context** 或 **Instrumentation** 发起（§1.4）。
   "后台启动"判定的关键之一是**发起方是否有可见 Activity / 是否持有
   `START_ACTIVITIES_FROM_BACKGROUND` 类权限**，而 instrumentation 进程的调用形态
   不会因「被测 App 恰好在前台」而改变 —— 这两条路径是独立的。
3. 另外，`EmptyActivity` 中转（§1.4a）本身**也是一次后台启动**，它在目标 Activity
   之前就会触发拦截。

- **预计改动文件**：无（仅改调用时序），但**需要改测试执行流程/脚本**。
- **风险**：已被历史实验证伪；重复执行只会消耗设备时间并再次产生无结果记录。
- **能否保持真实手机验收**：是，但**无效**。
- **尚需验证的前提**：无 —— 已有反例。
- **判定：不可行（已有反例，不建议再试）。**

### 方案 C：调整测试 Activity 生命周期 / 改用 `EmptyActivity` 中转 / 其它生命周期技巧

**设想**：用 `ActivityScenario` 的 `moveToState`、`recreate`，或干脆只起 `EmptyActivity`
再在其上 `setContent`，能否绕过启动限制？

**核对结果：不能。**

1. 任何"先有 Activity 再操作"的技巧，**前提是 Activity 已经启动成功**；
   而失败正发生在**启动那一步**（`result code=102`）。
   `moveToState` / `recreate` 都在 Activity 已存在之后才可用，**到不了那一步**。
2. 直接起 `EmptyActivity` 也不可行：它同样由
   `Context.startActivity` + `FLAG_ACTIVITY_NEW_TASK` 发起（§1.4a），
   属**同类后台启动**；且 `EmptyActivity` 是 androidx 内部实现细节，
   不提供 `setContent` 的公开契约。
3. `createEmptyComposeRule()`（同一工厂里的另一个 API）经反编译确认：
   它构造 `AndroidComposeTestRule` 时**不传 Activity Class**，
   其场景是「Activity 已由外部提供」（典型用于 `ActivityScenario` + 手工注入），
   **本身不启动 Activity**。⇒ 它**不能替代**本例的宿主需求；
   若与 `ActivityScenarioRule` 组合使用，又回到方案 A/B 的同一启动路径。

- **预计改动文件**：两个测试文件（改 rule 与生命周期调用）。
- **风险**：改动后大概率仍卡在同一处，且会消耗一轮真机验证窗口。
- **能否保持真实手机验收**：是，但**无效**。
- **尚需验证的前提**：无。
- **判定：不可行。**

### 方案 D：`Robolectric` / 纯 JVM 侧

**核对结果：能跑，但不满足「真实手机」。**

1. **本机现状**：Gradle 缓存中**未发现任何 Robolectric 构件**
   （已对 `C:\Users\23254\.gradle\caches` 做 `iname "*robolectric*"` 检索，结果为空）；
   `android/app/build.gradle.kts` 的 `testOptions` 只有
   `unitTests.isReturnDefaultValues = true`，**无** Robolectric 相关配置；
   `libs.versions.toml` 中**无** Robolectric 条目 ⇒ **当前不可用，需新增依赖**。
2. 即便启用，它运行在 **JVM 上的模拟 Android 环境**，**不启动真实 Activity、
   不经过系统 ActivityTaskManager** ⇒ 机制上**确实能避开**该限制（这是它唯一的优点）。
3. 但它**不是真实手机**：Compose 渲染、密度、字体、手势注入、像素抓取
   （`captureToImage`）都由 Robolectric 的影子类模拟，
   与真机行为**不保证一致**。用它得出的 PASS **不能**记作真机验收结论。

- **预计改动文件**：`android/app/build.gradle.kts`（新增依赖 + 可能的 `testOptions` 配置）、
  `android/gradle/libs.versions.toml`；两个测试文件需改为 JVM 源集或另建 JVM 版本
  （`CraSliderRtlRenderTest` 用了 `captureToImage`，在 Robolectric 下的可用性**未验证**）。
- **风险**：(a) 需要**联网**拉取依赖，当前构建是 `--offline`；
  (b) 引入新的维护面与可能的行为差异；
  (c) **最大风险是被误当作真机结论** —— 必须显式标注。
- **能否保持真实手机验收**：**否。**
- **尚需验证的前提**：Robolectric 版本与 Compose 1.9.0（BOM `2024.12.01`）的兼容性、
  `captureToImage` 在 Robolectric 下是否产出可读 `PixelMap`、
  是否能离线安装（缓存无构件）。
- **判定：只能作**补充门禁**，不得冒充用户要求的真实手机验收。**

---

## 3. 推荐的最小下一步

**首选：换一台真实手机。**

理由：现有证据已把失败点收敛到**设备策略层**（对「从后台启动 Activity」的拦截），
且三条不改设备设置的软件侧路径（A/B/C）在机制上与失败路径**同源**，
其中 B 还**已有历史反例**。继续在同一台设备上重试同一命令，预期产出仍是
「`started` + 180 秒无结果」，不会带来新信息。

最小动作建议：

1. 由总指挥指定另一台不受该策略约束的真实 Android 手机。
2. 在新设备上**先跑同一个对照组**（`AppDatabaseMigrationTest`）确认通道可用
   —— 本次已证明它 23/23 全绿、1.1 秒，是**成本最低的通道探针**。
3. 对照组通过后，再跑 D 的 4 项与 C 的 5 项；**仍须**带 `-r`、逐用例独立运行、
   有界超时，并在阻塞当下抓**未截断** logcat。
4. 若新设备同样被拦，则把 C/D 的**真机门禁**改为 CI
   （Gradle Managed Device / Firebase Test Lab），并**明确向用户说明**
   这已不是「本机真实手机验收」，需其认可。

**次选（不推荐作为主路径）**：把 Robolectric 作为**补充**门禁，
用于在没有合适真机时先卡住纯逻辑回归；但**必须**与真机结论分开记录，
且不得用来关闭 C/D 的真机验收项。

---

## 4. 本次取证的事实边界（不夸大）

**已由本机产物直接证明**：
- `createComposeRule()` 硬编码 `androidx.activity.ComponentActivity`（字节码 `ldc`）。
- 该 Activity 来自 `ui-test-manifest-1.9.0`，已 `exported=true`。
- 启动链为 `ActivityScenarioRule` → `ActivityScenario` → `ActivityInvoker` →
  `Instrumentation.startActivitySync`。
- 实际实现 `InstrumentationActivityInvoker` 用 flags
  `0x10000000`（EmptyActivity 中转）与 `0x10008000`（目标 Activity），
  后者与设备日志中的 `flg=0x10008000` 吻合。
- `Robolectric` 在本机 Gradle 缓存中不存在。

**未能证明 / 属于推断**：
- `result code=102` 的确切语义（AOSP `START_*` 枚举 vs 厂商自定义码）—— 记为 **P1**。
- 该拦截究竟是 MIUI 专有策略、还是 AOSP Android 14+ BAL 限制在该设备上的表现
  —— 日志**两者特征都有**，本轮**不裁决**（记为 **P2**）。
  这**不影响**结论：无论哪种，A/B/C 三条路径都与之同源。
- 「测试线程在 `setContent` 处永久等待」—— **未取证**（未抓线程栈/ANR），
  原验收报告已就此作措辞收窄。

---

## 5. SEAM REQUEST

| ID | 需求 | 属主 | 理由 |
|---|---|---|---|
| **SR-HOST-1** | 指定另一台不受「后台启动 Activity」策略约束的真实手机，用于 C/D 的 Compose 用例验收 | 总指挥 / 用户 | 本机三条软件侧路径已证同源或已有反例；换设备是唯一能保持「真实手机」的路径 |
| **SR-HOST-2** | 若决定走 CI 替代真机（Gradle Managed Device / Firebase Test Lab），请明确书面认可「这不再是本机真实手机验收」 | 总指挥 / 用户 | 避免 CI 结果被误记为真机结论 |
| **SR-HOST-3** | 若决定引入 Robolectric 作补充门禁：`android/app/build.gradle.kts`、`android/gradle/libs.versions.toml` 属他人所有权，且需联网拉依赖（当前缓存无构件） | 集成者 + 用户（网络） | 本轮无权改构建配置；且离线环境需先解决依赖获取 |
| **SR-HOST-4** | `CraSlider` 的 `track`/`thumb` 加 `testTag`（若后续要在新设备上做 bounds 级 RTL 断言） | `CraSlider.kt` 属主 | 沿用窗口 D 已登记的 SR-1，本轮仅确认其仍有效 |

---

## 6. 边界声明

- **未运行**：任何 `adb`、任何设备命令、任何 instrumentation、任何 Gradle 任务。
- **未修改**：产品源码、测试源码、`AndroidManifest.xml`、两个目标测试文件 —— **一个字都没改**。
  仅按授权对 `docs/qa/2026-09/2026-09-24-cd-portrait-independent-acceptance.md` 作**归因措辞更正**。
- **未删除**：任何证据文件。
- **git**：未 `reset` / `checkout` / `clean` / `stash` / `stage` / `commit` / `push`。
- **解包分析**：全部在系统临时目录（`/tmp/tmp.*`）进行，**未写入仓库**。
- **其他窗口**：本报告与其他窗口共享工作区，越界需求已全部写入 §5 SEAM REQUEST，未越权执行。
