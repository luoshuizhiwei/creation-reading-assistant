# Trae R2-J1.2：普通阅读位置采集与临时查阅隔离（Reader 侧）

日期：2026-09-10
状态：**Dev verified**（仅开发门禁；未 stage、commit、push，未做真机/视觉/WorkBuddy 验收。本切片只是 Reader 侧位置语义，尚未接入根导航与可见返回按钮，**不能宣称 J1 完成**。）

## 基线

- 分支：`main`
- HEAD：`e31db92d5493b6b705ae447aba82c52dc9d7e65f`（与 R2-J1.1 报告一致）
- 工作树是共享脏工作区：开工时已含其他 agent 的既有未提交改动（DAO、ReaderRoute、AppNavigation、ReaderViewModel、大量 `android/*.xml|*.png|*.txt` 调试产物等）。本切片全程未回滚、未 clean、未 stash、未 stage、未 commit、未 push；只读 `git status` / `git rev-parse HEAD` / 限定文件的 `git diff --check`。

## 前置条件核对

R2-J1.1 的 `TemporaryReadingNavigationViewModel` 及其 `TemporaryReadingNavigationViewModelTest` 已存在于工作树，接口契约（`recordNormalReading` / `beginTemporaryInspection` / `returnFromTemporaryInspection` / `hasReturnableTarget` / `state`）可消费。本切片未复制另一套状态机，只消费 R2-N0 的 `SourceNavigationContract` 纯函数与 `ReaderLocator` / `EpubLocatorMapping`。

## 范围

只改 Android 且仅动本切片独占的 Reader 位置/会话相关文件；未碰 desktop、Room schema、DAO/entity、`ReaderRoute.kt`、`AppNavigation.kt`、`ReaderViewModel`。

| 文件 | 动作 |
| --- | --- |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreenArgs.kt` | 新增 `ReaderNavigationMode { NORMAL, TEMPORARY }`；`ReaderScreenInputs` 增 `navigationMode: ReaderNavigationMode = NORMAL`；`ReaderScreenCallbacks` 增 `onSourcePositionChanged: (SourceNavigationTarget) -> Unit = {}`（位置上报 seam）。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressEffects.kt` | 新增纯函数 `sourceNavigationTargetFor(...)` 与 `canPersistNormalReadingProgress(...)`；新增 `temporaryInspection: Boolean = false` 参数；两处自动进度落库防抖（滚动 / 翻页）的门控由「仅 `pendingInitialPosition`」升级为「`canPersistNormalReadingProgress(initialPositionPending, temporaryInspection)`」。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSessionEffects.kt` | 计算 `temporaryInspection = inputs.navigationMode == TEMPORARY` 并透传给 `ReaderProgressEffects`。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreen.kt` | 计算 `temporaryInspection`；`persistOnLeave` 在 temporary 下短路 `nav.persistCurrentProgress()`，并同时喂给会话离开持久化与 `ReaderScaffold.onPersistProgress`。 |
| `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderSourcePositionTest.kt` | 新增：8 个纯 JVM 回归（见下）。 |
| `docs/plans/android-parallel-delivery-2026-09-09/reports/trae-r2-j1-2-progress.md` | 新增：本报告。 |

## 实现要点

### 1. 精确 source 位置采集（`sourceNavigationTargetFor`）

由「当前实际可见的精确 source 坐标」构造 `SourceNavigationTarget`。签名只接收 `bookId + isEpub + chapterStartOffsets + absoluteOffset`：

- **TXT**：`absoluteOffset` 即全书 source 绝对偏移，`chapterIndex = 0`、`charOffset = absoluteOffset`。
- **EPUB**：`absoluteOffset` 为全书 source 绝对偏移，章节元组由 `EpubLocatorMapping.toChapterOffset` 从 `chapterStartOffsets` 推导（与历史 locator 同源），供后续 `resolveChapteredPosition` 校验「全局 offset 与章节元组一致」。
- **Markdown**：走 canonical 路径，`chapterIndex = 0`、`charOffset = absoluteOffset`（R2-N1 的 `ci=0/co=全局` 规则）。

页号、pager index、LazyList index、display offset、替换投影 offset 均不在参数类型中，天然无法进入状态。`absoluteOffset < 0` 或 `bookId` 空白时返回 `null`（经 `SourceNavigationContract.target` 拒绝），**绝不构造 `offset=0` 的假位置**。

### 2. 普通 / 临时隔离（`canPersistNormalReadingProgress`）

```kotlin
!initialPositionPending && !temporaryInspection
```

- `initialPositionPending=true`：route 初始跳转 / 跨章节程序化跳转期间，不算用户普通阅读，禁止落库。
- `temporaryInspection=true`：临时查阅期间自动进度保存被短路，不覆盖普通阅读进度；普通位置上报同样被抑制。

该门控覆盖三条持久化路径：

1. 纯文本/滚动自动保存（ReaderProgressEffects 滚动 effect，500ms 防抖）；
2. 分页引擎自动保存（ReaderProgressEffects 翻页 effect，500ms 防抖）；
3. 离开/暂停阅读器的主动保存（`ReaderScreen.persistOnLeave → nav.persistCurrentProgress`）。

### 3. 路由注入预留（不接线）

- `navigationMode` 是区分 normal/temporary 的最小 input，默认 `NORMAL`，后续协调器经 ReaderRoute 注入；本切片未改 `AppNavigation` / `ReaderRoute`。
- `onSourcePositionChanged` 是最小位置上报 callback，默认空实现，当前无任何调用点（见 SEAM REQUEST）。它只声明类型，真正「把当前 visible source target 交给协调器」的接线留待后续切片。
- 直接打开既有 `sourceLocator` 路由仍默认 `NORMAL`，**不**被擅自解释成临时查阅；不会破坏 R2-N1 的普通导航兼容。

## 回归（`ReaderSourcePositionTest`，8 个）

1. `txt source target carries global source offset only` —— 普通 TXT 产生正确 source target（chapterIndex 0）。
2. `epub source target maps absolute offset to verified chapter tuple` —— EPUB 绝对 offset 正确映射为校验章节元组。
3. `markdown source target follows canonical global offset rule` —— Markdown canonical 全局 offset 规则。
4. `temporary inspection suppresses normal progress persistence` —— temporary 不触发普通 SaveProgress 覆盖（对照普通阅读允许）。
5. `pending initial position suppresses normal progress persistence` —— route 初始/跨章跳转不误记。
6. `pending and temporary together still suppress persistence` —— 双条件叠加仍抑制。
7. `negative source offset produces no navigation target` —— 无效 locator（负偏移）不写入、不崩溃。
8. `blank book id produces no navigation target` —— 无效 locator（空白书 ID）不写入、不崩溃。

## 测试命令 / 退出码 / 测试数

在 `android/` 目录执行：

```text
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.ReaderSourcePositionTest" --tests "com.creationreadingassistant.ui.screen.reader.ReaderProgressNavigationTest" --tests "com.creationreadingassistant.ui.screen.reader.SearchHitNavigationTest" --tests "com.creationreadingassistant.ui.screen.reader.SearchScrollFocusRequestTest" --tests "com.creationreadingassistant.feature.reader.navigation.SourceNavigationContractTest" --tests "com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModelTest" --console=plain
```

- 退出码：0，`BUILD SUCCESSFUL in 58s`（`:app:compileDebugKotlin` 通过）。
- `ReaderSourcePositionTest`：8 tests，0 failures，0 errors，0 skipped（本切片新增）。
- `ReaderProgressNavigationTest`：7 tests，0 failures，0 errors（R2-N1 既有，回归 5 兼容）。
- `SearchHitNavigationTest`：16 tests，0 failures，0 errors（R2-N1 既有，回归 5 兼容）。
- `SearchScrollFocusRequestTest`：8 tests，0 failures，0 errors（R2-N1 既有，回归 5 兼容）。
- `SourceNavigationContractTest`：11 tests，0 failures，0 errors（R2-N0 既有）。
- `TemporaryReadingNavigationViewModelTest`：6 tests，0 failures，0 errors（R2-J1.1 既有）。

合计 56 个定向 JVM 测试。限定本切片 5 个改动/新增源文件的 `git diff --check` 无尾随空白错误。

## 未覆盖项

- **未接入根导航与可见返回按钮**：`navigationMode` / `onSourcePositionChanged` 目前是预留 seam，`ReaderRoute` 尚未把协调器（`TemporaryReadingNavigationViewModel`）绑定到这些回调；故临时查阅返回按钮不可见、跨书返回历史不可用。本切片不能宣称 J1 完成。
- **位置上报接线未落地**：`onSourcePositionChanged` 只有类型声明，无调用点；「当前可见位置 → 协调器 `recordNormalReading`」的后半段尚未实现。
- 未运行 lint、assemble、安装、connectedAndroidTest、真机或 WorkBuddy 验收；用户已决定统一后置。
- 未对 EPUB 滚动模式的可见位置做额外 source 上报（该模式当前靠离开时 `persistCurrentProgress` 持久化，不在本切片的防抖自动保存路径内）。

## SEAM REQUEST

```text
SEAM REQUEST
目标文件：ReaderRoute.kt（他人所有权）/ AppNavigation.kt（他人所有权）
需要的接口/字段/行为：
  1. ReaderRoute 构造 ReaderScreenCallbacks 时，把 onSourcePositionChanged 接到
     TemporaryReadingNavigationViewModel.recordNormalReading（普通阅读、非 initial pending、
     非 temporary 时）；navigationMode 则由调用方据「普通阅读 / 临时查阅」入口设 NORMAL 或 TEMPORARY。
  2. Reader 侧需要在「当前可见 source 位置稳定（500ms 防抖后，且 canPersistNormalReadingProgress 为 true）」
     的同一时刻调用 sourceNavigationTargetFor(...) 并把结果经 onSourcePositionChanged 交付协调器；
     本切片只提供了 sourceNavigationTargetFor 纯函数与空回调 seam，未在 ReaderProgressEffects 内新增
     该上报调用点（避免未经协调器对齐就多发事件）。
  3. 协调器 scope 需高于单个 reader route（J1.1 已提），返回按钮可见性 = hasReturnableTarget，
     返回动作 = returnFromTemporaryInspection。
调用位置：ReaderRoute / AppNavigation，后续切片实现
为什么现有接口不足：本切片职责边界是「Reader 侧位置语义」，不得修改 ReaderRoute / AppNavigation；
  接线需要协调器实例的 scope 决策与触发时机对齐，属集成者负责。
兼容方案与测试：sourceNavigationTargetFor / canPersistNormalReadingProgress 已纯 JVM 可测；
  接线后可加 Activity 级 smoke 单测验证跨 route 同一实例 + 上报时序。
是否阻塞本轮：否（本切片 Dev verified，接线留待后续切片）。
```