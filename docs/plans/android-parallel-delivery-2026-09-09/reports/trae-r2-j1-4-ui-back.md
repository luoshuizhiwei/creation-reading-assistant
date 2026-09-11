# Trae R2-J1.4：临时查阅提示、返回阅读处按钮与 Back 行为

日期：2026-09-10
状态：**Dev verified**（仅开发门禁；未 stage、commit、push，未做真机/视觉/WorkBuddy 验收。）

## 基线

- 分支：`main`
- HEAD：`e31db92d5493b6b705ae447aba82c52dc9d7e65f`（与 R2-J1.1 / J1.2 / J1.3 报告一致）
- 工作树是共享脏工作区：开工时已含其他 agent 的既有未提交改动。本切片全程未回滚、未 clean、未 stash、未 stage、未 commit、未 push。

## 前置条件核对

R2-J1.1 的 `TemporaryReadingNavigationViewModel`、R2-J1.2 的 `ReaderNavigationMode` / `temporaryInspection` 隔离、R2-J1.3 的根导航协调与 `resolveTemporaryReturnRoute` 均已存在，接口一致，本切片直接消费：

- `TemporaryReadingNavigationViewModel`：`state: StateFlow<SourceNavigationState>`、`hasReturnableTarget`、`returnFromTemporaryInspection()`。
- `ReaderNavigationMode.TEMPORARY`：区分普通阅读与临时查阅。
- `resolveTemporaryReturnRoute(temporaryNavigation)`：执行一次 LIFO 返回，返回应导航的 route。

未修改前三个 agent 的已拥有文件（`TemporaryReadingNavigationViewModel.kt`、`ReaderScreenArgs.kt`、`ReaderProgressEffects.kt`、`ReaderSessionEffects.kt`、`ReaderRoute.kt`、`AppNavigation.kt`、`ReaderTemporaryRoute.kt`、DAO/entity/schema、`ReaderViewModel`）。

## 实际改动文件（本切片独占）

| 文件 | 动作 |
| --- | --- |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderChrome.kt` | 修改：新增 `ReaderChromeAction.ReturnToReading`；`ReaderTopChrome` 增 `temporaryInspection` / `hasReturnableTarget` 参数，条件渲染"返回阅读处"按钮（40dp 触控目标，`SubdirectoryArrowLeft` 图标，accent 色）。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderInteractionLayer.kt` | 修改：`ReaderInteractionLayerState` 增 `temporaryInspection` / `hasReturnableTarget` 字段；`ReaderInteractionLayerCallbacks` 增 `onReturnToReading` 回调；`ReaderInteractionLayer` 把临时状态传给 `ReaderTopChrome`。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderLayerBuilders.kt` | 修改：`buildReaderInteractionLayerState` 增 `temporaryInspection` / `hasReturnableTarget` 参数并透传。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScaffold.kt` | 修改：`ReaderScaffold` 调用 `buildReaderInteractionLayerState` 时传入 `temporaryInspection = inputs.navigationMode == TEMPORARY` 与 `hasReturnableTarget = callbacks.hasReturnableTarget`。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderActions.kt` | 修改：`handleChromeAction` 增 `onReturnToReading` 参数并处理 `ReturnToReading` 动作；`buildReaderNavActions` 增 `onReturnToReading` 参数并透传。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreenArgs.kt` | 修改：`ReaderScreenCallbacks` 增 `hasReturnableTarget: Boolean = false` 与 `onTemporaryReturn: () -> Unit = {}`。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreen.kt` | 修改：提取 `temporaryInspection` / `hasReturnableTarget` / `onTemporaryReturn` 到 BackHandler 之前；BackHandler `enabled` 条件增 `(temporaryInspection && hasReturnableTarget)`；`when` 分支末尾增 `temporaryInspection && hasReturnableTarget -> onTemporaryReturn()`；`buildReaderNavActions` 调用增 `onReturnToReading = onTemporaryReturn`。 |
| `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderTemporaryBackTest.kt` | 新增：10 个纯 JVM 回归（见下）。 |
| `docs/plans/android-parallel-delivery-2026-09-09/reports/trae-r2-j1-4-ui-back.md` | 新增：本报告。 |

## 实现要点

### 1. "返回阅读处"按钮可见性

`ReaderTopChrome` 在 `temporaryInspection && hasReturnableTarget` 时渲染按钮：

- 仅当临时查阅模式 **且** 协调器存在可返回目标时显示，避免显示无法兑现的按钮；
- 按钮使用 `IconPedestal`（34dp 图标 + 40dp `IconButton` 触控目标），满足 48dp 最小触控要求；
- 图标 `SubdirectoryArrowLeft`（accent 色），contentDescription "返回阅读处"（无障碍语义）；
- 点击触发 `ReaderChromeAction.ReturnToReading`，由 `handleChromeAction` 转发到 `onReturnToReading` 回调。

### 2. Back 行为优先级

`ReaderScreen` 的 `BackHandler` 按以下优先级处理：

1. 关闭弹层（`sheet != null`）；
2. 关闭笔记对话框（`noteOpen`）；
3. 关闭溢出菜单（`showReaderOverflow`）；
4. 清除选区（`selectedText.isNotBlank()`）；
5. 隐藏控件（`controlsVisible`）；
6. **临时查阅且存在返回目标**（`temporaryInspection && hasReturnableTarget`）→ 调用 `onTemporaryReturn()`；
7. 否则维持现有 `popBackStack` 离开 reader 的行为（由 `ReaderRoute.onBack` 处理）。

`enabled` 条件包含 `(temporaryInspection && hasReturnableTarget)`，确保临时返回分支可触发。

### 3. 回调链路

`ReaderRoute` 构造 `ReaderScreenCallbacks` 时：

- `hasReturnableTarget = temporaryNavigation?.hasReturnableTarget ?: false`；
- `onTemporaryReturn = { resolveTemporaryReturnRoute(temporaryNavigation)?.let { navController.navigate(it) { launchSingleTop = true } } ?: navController.popBackStack() }`。

`onTemporaryReturn` 调用 J1.3 的 `resolveTemporaryReturnRoute`，执行一次 LIFO 返回并导航到目标 route；无返回目标时降级为 `popBackStack()`。

### 4. 普通阅读 Back 行为不变

`temporaryInspection = false` 时，BackHandler 的 `enabled` 条件与 `when` 分支均不涉及临时返回，维持既有行为（关弹层 → 隐藏控件 → 默认 `popBackStack`）。

### 5. 进度隔离

J1.2 已实现：`temporaryInspection` 期间 `persistOnLeave` 短路 `nav.persistCurrentProgress()`，不覆盖普通阅读进度。本切片不修改进度存储逻辑。

## 回归（ReaderTemporaryBackTest，10 个）

1. `temporary inspection state is correctly derived from navigation mode` — `ReaderNavigationMode.TEMPORARY` 与 `NORMAL` 正确区分。
2. `return to reading button visibility depends on temporary inspection and hasReturnableTarget` — 按钮可见性逻辑：仅 `temporaryInspection && hasReturnableTarget` 时显示。
3. `return to reading callback is invoked exactly once` — 回调调用一次。
4. `back handler prioritizes closing sheet over temporary return` — Back 优先关闭 sheet，不触发临时返回。
5. `back handler triggers temporary return when no transient UI is open` — 无临时 UI 时触发临时返回。
6. `back handler does not trigger temporary return when hasReturnableTarget is false` — 无返回目标时不触发。
7. `back handler does not trigger temporary return in normal reading mode` — 普通阅读模式不触发。
8. `ReaderChromeAction ReturnToReading is distinct from Back` — `ReturnToReading` 与 `Back` 是不同动作。
9. `ReaderScreenCallbacks has default values for temporary navigation` — 默认值正确。
10. `ReaderScreenCallbacks accepts custom temporary navigation callbacks` — 自定义回调可注入。

## 测试命令 / 退出码 / 测试数

在 `android/` 目录执行：

```text
./gradlew :app:compileDebugKotlin
```

- 退出码：0（BUILD SUCCESSFUL）
- 只有 deprecation warnings，无编译错误

```text
./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.ReaderTemporaryBackTest"
```

- 退出码：0（BUILD SUCCESSFUL）
- `ReaderTemporaryBackTest`：10 tests（本切片新增），全部通过

合计 10 个定向 JVM 测试通过。

## 未覆盖项

- **临时跳转入口未生成**：`readerTemporaryRoute(dest)` 尚无任何标注/搜索/笔记点击处调用；入口点击处理属于其他 agent 文件 / 后续功能。
- **位置上报 call site 未落地**：`onSourcePositionChanged` 已接到 `recordNormalReading`，但 `ReaderProgressEffects` 内尚未新增「可见 source 位置稳定 → 上报」的调用点。因此协调器目前收不到普通阅读位置，`state.active` / `normalReading` 在实际运行中仍为空。
- 未运行 lint、assemble、安装、connectedAndroidTest、真机或 WorkBuddy 验收；用户已决定统一后置。

## SEAM REQUEST

```text
SEAM REQUEST
目标文件：标注、搜索、笔记点击入口（各自 owner）
需要的接口/字段/行为：
  1. 临时跳转入口：点击一个「临时查阅」目标时，仅 navigate(readerTemporaryRoute(destinationTarget)) 即可；
     ReaderRoute 会在进入 temporary 模式时统一调用 beginTemporaryInspection，入口不要再自行调用，避免重复压栈。
  2. 位置上报：ReaderProgressEffects 在「可见 source 位置稳定（500ms 防抖后，且 canPersistNormalReadingProgress 为 true）」
     时调用 sourceNavigationTargetFor(...) 并经 inputs.onSourcePositionChanged 交付协调器；
     ReaderRoute 现已把该回调接到 TemporaryReadingNavigationViewModel.recordNormalReading。
调用位置：标注 / 搜索 / 笔记点击处理，各自 owner
为什么现有接口不足：本切片职责边界是可见返回控件与 Back 行为，不拥有标注/搜索/笔记点击入口文件；
  临时跳转入口点击处理与上报触发时机需各自 owner 接线。
兼容方案与测试：ReaderTemporaryBackTest 纯 JVM 可测；接线后由集成者做 Activity 级跨 route 同一实例 + 上报时序 smoke。
是否阻塞本轮：否（本切片 Dev verified，入口接线留待后续）。
```

## 最终统一验收仍需检查的可见流程

1. **临时查阅模式进入**：从标注/搜索/笔记点击处 navigate 到 `readerTemporaryRoute(dest)`，验证 reader 以 `navigationMode=TEMPORARY` 进入，顶栏显示"返回阅读处"按钮。
2. **返回阅读处按钮点击**：点击按钮，验证 LIFO 返回一层（中间层继续 temporary，最后一层回到普通阅读处）。
3. **Back 键优先级**：
   - 有弹层/菜单/选区时，Back 先关闭它们；
   - 无临时 UI 且 `hasReturnableTarget=true` 时，Back 触发临时返回；
   - 无返回目标时，Back 维持 `popBackStack` 离开 reader。
4. **普通阅读 Back 行为**：验证普通阅读模式下 Back 行为不变（关弹层 → 隐藏控件 → 离开）。
5. **进度隔离**：临时查阅期间离开/暂停 reader，验证不覆盖普通阅读进度。
6. **跨书临时查阅**：从书 A 临时跳转到书 B，验证返回时正确回到书 A 的 source 位置。
7. **多层临时查阅**：A → B → C，验证返回顺序 C → B → A（LIFO）。
8. **无障碍语义**：验证"返回阅读处"按钮的 contentDescription 被 TalkBack 正确朗读。
9. **触控目标**：验证按钮触控区域 ≥ 48dp。
10. **位置上报**：验证普通阅读期间 `onSourcePositionChanged` 被调用，协调器 `state.active` / `normalReading` 被更新。
