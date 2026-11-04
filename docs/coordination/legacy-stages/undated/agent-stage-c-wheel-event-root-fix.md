# 鼠标滚轮事件入口根因修复（窗口 C）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`；HEAD：`5aed5939600e491843e3c44453921bd6273a233c`
- 日期：2026-09-22
- 范围：仅修正滚轮事件入口；**不处理数据库测试或真机验收**

---

## 1. 修改前根因

`MainActivity` 覆写了 AndroidX **受限**的 `ComponentActivity.dispatchKeyEvent`，并在
`KeyEvent.action` 上比较 `MotionEvent.ACTION_SCROLL`：

```kotlin
// 修改前
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == MotionEvent.ACTION_SCROLL && ReaderHardwareKeys.dispatch(event)) return true
    return super.dispatchKeyEvent(event)
}
```

这个写法有两重错误，同一个分支同时踩中：

1. **Lint 红灯**：`dispatchKeyEvent` 是 AndroidX 受限 API，跨 library group 调用报
   `RestrictedApi`（`referenced groupId=androidx.core … from groupId=CreationReadingAssistant`）。
2. **功能上永远不成立**：`KeyEvent.action` 的取值域只有 `ACTION_DOWN` / `ACTION_UP` /
   `ACTION_MULTIPLE`（已用 `javap` 对 `android.jar` 的 `android.view.KeyEvent` 核实：
   **该类根本没有 `action` 之外的轴相关方法，也没有 `getAxisValue`**）。
   真实鼠标滚轮是 **MotionEvent**，走的是 `ACTION_SCROLL`，**不会进入 `dispatchKeyEvent`**。
   因此该分支既触发受限 API 告警，又**从未接收到任何真实滚轮事件**。

配套地，`ReaderHardwareKeys.dispatch(KeyEvent)` 内还留了一段为这个不可能分支服务的
`KeyEvent#getAxisValue` **反射**：反射目标在公开 `android.jar` 里不存在
（`javap` 核实 `android.view.KeyEvent` 无 `getAxisValue`），取不到就 `return false` ——
是一条**必定失败、且永远不会被触发**的死路径。

**修复前基线**（窗口 C 既有证据
`docs/qa/evidence/2026-09-22-stage-c-database-device-acceptance/10-lint.log`）：
`Lint found 3 errors, 9 warnings`，首个 error 位于 `MainActivity.kt:226`，
3 个 error 全部指向同一函数；因 `abortOnError = true`，`lintDebug` 直接 FAIL。

---

## 2. 修改后的事件流

两条路径彻底分开，各走自己的公开入口：

```
键盘：KeyEvent（DOWN / UP / MULTIPLE）
      → Activity.onKeyDown / onKeyUp            （保留，未改）
      → ReaderHardwareKeys.dispatch(event)
      → dispatch(action, keyCode, repeatCount)  （按键序列语义不变）

滚轮：MotionEvent（ACTION_SCROLL）
      → Activity.dispatchGenericMotionEvent(event)   ← 公开 API，替换受限入口
      → event.getAxisValue(MotionEvent.AXIS_VSCROLL)
      → ReaderHardwareKeys.dispatchScroll(vscroll)
      → triggerDirection(±1)
```

**行为合同**（与要求逐条一致）：

- 只处理 `MotionEvent.ACTION_SCROLL`；
- 垂直轴直接读 `AXIS_VSCROLL`，不再有任何反射；
- `ReaderHardwareKeys` 消费时返回 `true`；
- 未消费、零轴、或非滚轮事件一律 `return super.dispatchGenericMotionEvent(event)`；
- 保留现有 `onKeyDown` / `onKeyUp`，继续处理音量键、PAGE、SPACE、DPAD；
- **不再覆写 `dispatchKeyEvent`**（已确认全 `main` 源集无残留）。

---

## 3. 修改文件和精确范围

| 文件 | 范围 |
|---|---|
| `android/app/src/main/java/com/creationreadingassistant/MainActivity.kt` | 仅替换第 224–229 行的 `dispatchKeyEvent` 覆写为 `dispatchGenericMotionEvent` 覆写（含其 3 行注释）。`onKeyDown` / `onKeyUp`（第 214–222 行）**未改动** |
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReaderHardwareKeys.kt` | ① 删除 `dispatch(event: KeyEvent)` 内的 `ACTION_SCROLL` 分支与 `KeyEvent#getAxisValue` 反射，函数收缩为单行 `= dispatch(event.action, event.keyCode, event.repeatCount)`；② 删除因此产生的 `import android.view.MotionEvent`（`KeyEvent` import 保留，仍被 `directionOf`/`dispatch` 使用）；③ 类头 KDoc 中"鼠标滚轮走 KeyEvent"的表述改为"由 Activity 的 `dispatchGenericMotionEvent` 以 `AXIS_VSCROLL` 轴值调用" |
| `android/app/src/test/.../ReaderHardwareKeysTest.kt` | **未修改**（见下） |

**保留未动的部分**（按要求）：`dispatchScroll` 的正/负方向映射本轮不改；
`consumedKeys` 按键序列语义不改；媒体按钮桥 `triggerDirection` 不改。

### 测试为何未改

现有 `ReaderHardwareKeysTest` 已完整覆盖：键盘 DOWN/UP/repeat、PAGE/SPACE/DPAD、
`dispatchScroll` 正值/负值/零值、滚轮不登记按键序列 —— 共 **9 条**。
本轮删除的是 `dispatch(KeyEvent)` 里的**死分支**（原本就没有任何测试经过它），
不产生新的可测行为差异。为避免为数量重复添加同类测试，且避免引入 Robolectric
（`dispatchGenericMotionEvent` 属 Activity 层面，纯 JVM 测不到），**保留现有测试不动**。

---

## 4. 验证命令、退出码与结果

按顺序执行，均用 `--no-parallel`：

### ① 目标单元测试

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.reader.pager.ReaderHardwareKeysTest" --no-parallel
```

- **退出码：`0`**，`BUILD SUCCESSFUL in 1m 1s`
- `ReaderHardwareKeysTest` 实际测试数量：**9**（`tests="9"`，`skipped="0"`、
  `failures="0"`、`errors="0"`，取自
  `app/build/test-results/testDebugUnitTest/TEST-…ReaderHardwareKeysTest.xml`）

### ② Kotlin 编译

```bash
.\gradlew.bat :app:compileDebugKotlin --no-parallel
```

- **退出码：`0`**，`BUILD SUCCESSFUL in 3s`（`compileDebugKotlin` 由 ① 先行完成，此处 UP-TO-DATE）

### ③ Lint 门禁

```bash
.\gradlew.bat :app:lintDebug --no-parallel
```

- **退出码：`0`**，`BUILD SUCCESSFUL in 4m 51s`
- **Lint 结果：0 errors、9 warnings**
  （`app/build/reports/lint-results-debug.xml`：9 条全部 `severity="Warning"`；
  分布 `ObsoleteLintCustomCheck` 3、`GradleDependency` 3、`DefaultLocale` 2、`UnusedResources` 1）
- **`RestrictedApi` 计数：0** —— **三个 RestrictedApi 错误已全部消失**

**前后对比**：

| | errors | warnings | RestrictedApi |
|---|---|---|---|
| 修复前（既有基线） | 3 | 9 | 3 |
| 修复后（本轮实测） | **0** | 9 | **0** |

剩余的 9 条 warning 与修复前的 9 条**同类同量**，均为既有的
`ObsoleteLintCustomCheck`（Compose/lifecycle 的 lint 注册表版本）、`GradleDependency`、
`DefaultLocale`、`UnusedResources`，**与本次改动无关**，且不构成 `abortOnError` 阻断。

### 未采用的绕过手段（逐条确认未使用）

`grep` 核实两个修改文件中**不含** `@SuppressLint`、lint baseline；
`android/app/build.gradle.kts:139` 的 **`abortOnError = true` 保持不变**；
未修改任何 AndroidX 版本；未删除滚轮功能（滚轮改走正确的公开入口后功能得到保留）。

---

## 5. 尚未验证项

- **真实鼠标滚轮方向未验证。** `dispatchScroll` 的正/负 → 下/上一页映射本轮**未改动**，
  JVM 测试只证明"正值调 direction=1、负值调 direction=-1"这一**纯逻辑映射**，
  **不证明**真机上鼠标物理滚动方向与 `AXIS_VSCROLL` 符号的对应关系符合预期。
- **真机行为未验证。** 未操作手机、未运行 ADB、未安装 APK、未运行 `assemble`。
  本次三条命令全部是 **JVM 单元测试 + Kotlin 编译 + Lint 静态检查**，
  **不构成本功能在真机上的验收**。
- **`dispatchGenericMotionEvent` 的实际派发未实测。** 仅通过 `javap` 核实它是
  `android.app.Activity` 的 **public** 方法（`dispatchKeyEvent` 同样 public 但被 AndroidX
  标为受限），以及滚轮事件在 Android 上经此路径分发属框架既定行为 —— 未经设备验证。
- **非滚轮 generic motion 未实测。** 例如触控板 hover、手写笔 hover 等也会经
  `dispatchGenericMotionEvent`；按合同它们因 `action != ACTION_SCROLL` 会交回 `super`，
  但**未在设备上确认**交回后行为无回归。
- **未做数据库相关任何验证**（本轮明确排除，属窗口 C 另一条线）。

> 说明：JVM 测试通过、Kotlin 编译通过、Lint 通过，**都不能写成**"鼠标滚轮功能已通过"
> 或"真机滚轮翻页已验收"。这三者只证明代码可编译、纯逻辑映射正确、静态门禁无告警。

---

## 6. 是否修改范围外文件

**未修改范围外文件。** 本轮只改了上述 2 个源文件 + 新建本报告。

明确未触碰：数据库 / DAO / 迁移 / schema / 数据库测试；`ReaderScreenDerivedState`、
小 TXT 测试；`PageTurner`、点击区、媒体按钮（`ReaderMediaButtonBridge`）、Compose UI；
Gradle 与 Lint 配置、安装脚本；其他窗口报告与任务账本。

**Git 状态**：分支 `codex/recover-worktree-20260920`、HEAD `5aed5939…` 未变；
**未** stage、commit、push、checkout、reset、clean。
（`git diff` 中 `MainActivity.kt` 相对 HEAD 还含其他窗口的既有改动，
本轮仅改动其中的滚轮入口那一段。）

---

## 7. 下一步

真机鼠标滚轮的方向与手感验收属**独立硬件验收**，需单独立项：
装包后连接鼠标实测滚动方向，再决定是否调整 `dispatchScroll` 的符号映射。
本轮到此停止，不打包、不安装、不重跑数据库真机验收。
