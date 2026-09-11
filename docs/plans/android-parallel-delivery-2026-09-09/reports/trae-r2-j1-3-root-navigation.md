# Trae R2-J1.3：根导航协调、临时路由与跨书返回

日期：2026-09-10
状态：**Dev verified**（仅开发门禁；未 stage、commit、push，未做真机/视觉/WorkBuddy 验收。根导航已接线，但可见「返回阅读处」控制仍归 J1.4，**不能提前称为完整用户旅程**。）

## 基线

- 分支：`main`
- HEAD：`e31db92d5493b6b705ae447aba82c52dc9d7e65f`（与 R2-J1.1 / J1.2 报告一致）
- 工作树是共享脏工作区：开工时已含其他 agent 的既有未提交改动（DAO、ReaderScreen、ReaderViewModel、大量 `android/*.xml|*.png|*.txt` 调试产物等）。本切片全程未回滚、未 clean、未 stash、未 stage、未 commit、未 push；只读 `git status` / `git rev-parse HEAD` / 限定文件的 `git diff --check`。

## 前置条件核对

R2-J1.1 的 `TemporaryReadingNavigationViewModel`（协调器）与 R2-J1.2 的 `ReaderNavigationMode` / `ReaderScreenInputs.navigationMode` / `ReaderScreenCallbacks.onSourcePositionChanged` 均已存在，接口一致，本切片直接消费：

- `TemporaryReadingNavigationViewModel`：`state: StateFlow<SourceNavigationState>`、`hasReturnableTarget`、`recordNormalReading(target?)`、`beginTemporaryInspection(destination?)`、`returnFromTemporaryInspection()`。
- `SourceNavigationContract.target(bookId, locator)` 与 `LocatorCodec.encode / decode`：构造与编解码 source locator。

未修改前两个 agent 的已拥有文件（`ReaderScreenArgs.kt`、`ReaderScreen.kt`、`ReaderProgressEffects.kt`、`ReaderSessionEffects.kt`、`ReaderChrome.kt`、`ReaderScaffold.kt`、DAO/entity/schema、`ReaderViewModel`）。

## 实际改动文件（本切片独占）

| 文件 | 动作 |
| --- | --- |
| `android/app/src/main/java/com/creationreadingassistant/ui/navigation/AppNavigation.kt` | 修改：根层 `hiltViewModel()` 持有协调器；reader route 增可选 `navigationMode` 查询参数并解析为 `ReaderNavigationMode` 传入 `ReaderRoute`。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderRoute.kt` | 修改：接收 `navigationMode` / `temporaryNavigation`；`onBack` 在临时模式走协调器 LIFO 返回；`onSourcePositionChanged` 接到 `recordNormalReading`；临时模式进入时按需 `beginTemporaryInspection`。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/navigation/ReaderTemporaryRoute.kt` | 新增：纯函数路由工具（模式解析、locator 编解码、普通/临时/返回 route、LIFO 返回解析）。 |
| `android/app/src/test/java/com/creationreadingassistant/ui/navigation/ReaderTemporaryRouteTest.kt` | 新增：9 个纯 JVM 回归（见下）。 |
| `docs/plans/android-parallel-delivery-2026-09-09/reports/trae-r2-j1-3-root-navigation.md` | 新增：本报告。 |

## 实现要点

### 1. 协调器挂在根导航作用域

`AppNavigation` 在 NavHost 外 `hiltViewModel()` 取得唯一 `TemporaryReadingNavigationViewModel`（Activity 级 ViewModelStore），经 `AppNavHost` 传入 reader route：

- 跨 reader route 存活，不绑定单个 `ReaderViewModel` 或单个 reader route；
- 进程重启即重置（新实例从空 `SourceNavigationState` 起步），普通阅读恢复仍走既有进度逻辑，不在本协调器伪造恢复；
- 只用内存，不落库、不写 SavedStateHandle。

### 2. 三态路由（普通 / 临时 / 返回）

route 仍复用 `reader/{bookId}?highlightId={highlightId}&sourceLocator={sourceLocator}`，只增一个可选 `navigationMode` 参数：

- `navigationMode` 缺省/未知/大小写不符 → `ReaderNavigationMode.NORMAL`（普通 source 定位，兼容既有语义）。
- `navigationMode=temporary` → 临时查阅。
- `sourceLocator` 仍是 `LocatorCodec` 出的 locator JSON，按既有 percent-encode（URLEncoder，与 `android.net.Uri.encode` 对 locator JSON 逐字节等价）传递；不塞页码、显示坐标，也不把临时状态塞进 `highlightId`。

纯函数在 `ReaderTemporaryRoute.kt`：

| 函数 | 语义 |
| --- | --- |
| `readerNavigationMode(raw)` | 仅 `temporary` 触发临时，其余降级普通 |
| `readerSourceRoute(target)` | 普通 source 定位（不带 navigationMode） |
| `readerTemporaryRoute(target)` | 临时查阅（带 `navigationMode=temporary`） |
| `readerReturnRoute(target)` | 返回普通阅读处（只带 sourceLocator，不带 highlightId / navigationMode） |
| `sourceTarget(bookId, json)` | 把已解码的 locator JSON 还原为 target |
| `resolveTemporaryReturnRoute(vm)` | 一次 LIFO 返回，返回应导航的 route |

### 3. 临时跳转「先交当前 source target，再前往目的地」

进入临时模式的 `ReaderRoute`，用 `sourceTarget(bookId, sourceLocatorJson)` 解出目的地，并在**目的地 ≠ 协调器当前 active** 时 `beginTemporaryInspection(destination)`（协调器内部把当前 active 推入 LIFO 返回栈）。允许跨书。

「目的地 == active」守卫的目的：返回中间层时会以 temporary 模式再次进入 reader，此时不应重复压栈。因此临时跳转入口（J1.4 或标注/搜索点击处）只需 `navigate(readerTemporaryRoute(dest))`，不要额外调用 `beginTemporaryInspection`，否则会重复压栈。

### 4. 返回由 J1.1 状态逐层 LIFO 决定，不以 NavController 数量充当历史

`ReaderRoute.onBack` 在临时模式调用 `resolveTemporaryReturnRoute`：

- 临时栈空 → 返回 null → `popBackStack()`（正常离开 reader，不伪造目标）；
- 中间层（返回后栈仍非空）→ 生成带 `navigationMode=temporary` 的 route，允许继续逐层回退；
- 最后一层（返回后栈清空）→ 生成不带 navigationMode 的 route，回到普通阅读处。

返回目标只带 `sourceLocator`，不带 `highlightId`；避免跨书临时查阅制造重复 destination 用 `launchSingleTop`，历史真源始终是 `temporaryReturnStack`（上限 8，J1.1 已约束）。

## 回归（ReaderTemporaryRouteTest，9 个）

1. `missing or unknown navigationMode resolves to normal reading` — 普通路由兼容（null/空/未知/大小写不符 → 普通）。
2. `normal source route carries only sourceLocator and keeps bookId offset` — 普通 sourceLocator 路由。
3. `temporary route parses to temporary mode and carries sourceLocator` — temporary 路由解析并触发临时模式。
4. `return route carries only sourceLocator without highlightId or navigationMode` — 返回目标只带 sourceLocator。
5. `resolving temporary return never emits highlightId` — 临时返回不带错误 highlightId。
6. `cross-book nested temporary returns LIFO keeping bookId and source locator` — 跨书逐层 LIFO，bookId + source locator 保持。
7. `same-book cross-chapter keeps same bookId with distinct source locators` — 同书跨章 bookId 一致、locator 各异。
8. `source target round-trips through the nav-arg decode` — sourceTarget 解码往返。
9. `invalid targets degrade to null without fabricating offsets` — 无效参数安全降级（不崩溃、不伪造 offset=0、空栈不伪造目标）。

## 测试命令 / 退出码 / 测试数

在 `android/` 目录执行：

```text
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.navigation.ReaderTemporaryRouteTest" --tests "com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModelTest" --tests "com.creationreadingassistant.feature.reader.navigation.SourceNavigationContractTest" --console=plain
```

- 退出码：0，`BUILD SUCCESSFUL in 1m 1s`（`:app:compileDebugKotlin` 通过；仅一条 J1 之前就存在的 `Icons.Outlined.MenuBook` deprecation 警告，与本切片无关）。
- `ReaderTemporaryRouteTest`：9 tests，0 failures，0 errors，0 skipped（本切片新增）。
- `TemporaryReadingNavigationViewModelTest`：6 tests，0 failures，0 errors（J1.1 既有）。
- `SourceNavigationContractTest`：11 tests，0 failures，0 errors（R2-N0 既有）。

合计 26 个定向 JVM 测试。限定本切片 4 个改动/新增源文件的 `git diff --check` 无尾随空白错误（AppNavigation.kt 只报告既有的 CRLF→LF 换行提示，非本切片引入）。

## 未覆盖项

- **可见「返回阅读处」控制未接**：本切片只把协调器挂到根导航、把 `onBack` 接到 LIFO 返回。可见的返回按钮（可见性 = `hasReturnableTarget`）与入口触发仍归 J1.4；不能称为完整用户旅程。
- **位置上报 call site 未落地**：`onSourcePositionChanged` 已接到 `recordNormalReading`，但 J1.2 尚未在 `ReaderProgressEffects` 内新增「可见 source 位置稳定（500ms 防抖且 `canPersistNormalReadingProgress`）→ `sourceNavigationTargetFor` → 上报」的调用点。因此协调器目前收不到普通阅读位置，`state.active` / `normalReading` 在实际运行中仍为空，直到 J1.4 补上。
- **临时跳转入口未生成**：`readerTemporaryRoute(dest)` 尚无任何标注/搜索/笔记点击处调用；入口点击处理属于其他 agent 文件 / J1.4。
- 未对 NavController back stack 做 `popUpTo` 级压缩：跨书临时查阅通过 `navigate` + `launchSingleTop` 前进，长时间多轮往返仍会累积 NavController 条目；返回正确性由协调器 8 层 LIFO 保证，back stack 形态的精简留待 J1.4 定稿可见返回控件的导航语义时一并处理。
- 未运行 lint、assemble、安装、connectedAndroidTest、真机或 WorkBuddy 验收；用户已决定统一后置。

## SEAM REQUEST

```text
SEAM REQUEST
目标文件：ReaderProgressEffects.kt / 其他 agent 的标注、搜索、笔记点击入口（J1.4 及各自 owner）
需要的接口/字段/行为：
  1. 位置上报：ReaderProgressEffects 在「可见 source 位置稳定（500ms 防抖后，且 canPersistNormalReadingProgress 为 true）」
     时调用 sourceNavigationTargetFor(...) 并经 inputs.onSourcePositionChanged 交付协调器；
     ReaderRoute 现已把该回调接到 TemporaryReadingNavigationViewModel.recordNormalReading。
  2. 临时跳转入口：点击一个「临时查阅」目标时，仅 navigate(readerTemporaryRoute(destinationTarget)) 即可；
     ReaderRoute 会在进入 temporary 模式时统一调用 beginTemporaryInspection，入口不要再自行调用，避免重复压栈。
  3. 可见返回控制（J1.4）：返回按钮可见性 = temporaryNavigation.hasReturnableTarget；
     返回动作 = ReaderRoute.onBack 已在临时模式走 resolveTemporaryReturnRoute，可见控件只需触发同一 onBack 即可。
调用位置：ReaderProgressEffects / 标注 / 搜索 / 笔记点击处理，J1.4
为什么现有接口不足：本切片职责边界是根导航 + 路由构造，不拥有 ReaderProgressEffects 与其他点击入口文件；
  上报触发时机与临时跳转入口点击处理需各自 owner 接线。
兼容方案与测试：ReaderTemporaryRoute 纯函数已纯 JVM 可测；接线后由集成者做 Activity 级跨 route 同一实例 + 上报时序 smoke。
是否阻塞本轮：否（本切片 Dev verified，可见控制与上报接线留待 J1.4）。
```