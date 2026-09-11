# Trae R2-J1.1：临时查阅会话协调器

日期：2026-09-10
状态：**Dev verified**（仅开发门禁；未 stage、commit、push，未做真机/视觉/WorkBuddy 验收，也不构成 R2-J1 已验收）。

## 基线

- 分支：`main`
- HEAD：`e31db92d5493b6b705ae447aba82c52dc9d7e65f`
- 工作树在开工时已含其他 agent 的既有未提交改动（DAO、ReaderRoute、ReaderScreen 等），本切片全程未回滚、未 clean、未 stash、未 stage、未 commit、未 push。

## 实际改动文件（本切片新增/占用）

| 文件 | 动作 |
| --- | --- |
| `android/app/src/main/java/com/creationreadingassistant/ui/navigation/TemporaryReadingNavigationViewModel.kt` | 新增：内存协调器 ViewModel，包装 `SourceNavigationContract` 纯函数，暴露 `state: StateFlow<SourceNavigationState>`、`hasReturnableTarget`、`recordNormalReading`、`beginTemporaryInspection`、`returnFromTemporaryInspection`。 |
| `android/app/src/test/java/com/creationreadingassistant/ui/navigation/TemporaryReadingNavigationViewModelTest.kt` | 新增：6 个纯 JVM 回归（见下）。 |
| `docs/plans/android-parallel-delivery-2026-09-09/reports/trae-r2-j1-1-session.md` | 新增：本报告。 |

`android/app/src/main/java/com/creationreadingassistant/feature/reader/navigation/SourceNavigationContract.kt` 与其测试 `SourceNavigationContractTest.kt` 已在 R2-N0（codex）存在，本切片只消费、不改动，保持 API 兼容。

## 实现要点

- 唯一状态真源为 `SourceNavigationState`（经 `state` 暴露），所有变更委托给 `SourceNavigationContract` 的纯函数；协调器自身不做坐标判定、不持有 `NavController`。
- 返回历史真源是 `SourceNavigationState.temporaryReturnStack`（LIFO，默认上限 8，`takeLast` 丢弃最旧），**不依赖 NavController back stack 数量**。
- `recordNormalReading` 是唯一更新可恢复位置并结束临时链的操作；`beginTemporaryInspection` 把当前 active（兜底 normalReading）推入返回栈、跳到 destination，不改写 `normalReading`。
- 无有效 source 坐标：方法只接受 `SourceNavigationTarget`，其构造器为 `internal`，唯一构造入口 `SourceNavigationContract.target` 拒绝空白书籍 ID、负 offset 与半截章节坐标；对 `null` 一律拒绝不进入状态。页号、LazyList index、display offset、替换投影 offset 在 `ReaderLocator` 类型中无字段，无法进入状态。
- 仅内存：不写数据库、不写 `SavedStateHandle`；新实例从空 `SourceNavigationState` 起步，临时栈消失、普通位置不伪造恢复。

## 回归（TemporaryReadingNavigationViewModelTest，6 个）

1. 普通阅读不会制造临时返回项。
2. A→临时 B→临时 C 的 LIFO 返回。
3. 普通阅读行为清空临时链。
4. 8 层上限（第 9 次查阅挤出最旧 origin）。
5. 无有效 source 坐标拒绝进入状态。
6. 新协调器实例：临时栈为空、普通位置不由该协调器伪造恢复。

## 测试命令 / 退出码 / 测试数

在 `android/` 目录执行：

```text
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModelTest" --tests "com.creationreadingassistant.feature.reader.navigation.SourceNavigationContractTest" --console=plain
```

- 退出码：0，`BUILD SUCCESSFUL in 1m 47s`（`compileDebugKotlin` 通过）。
- `TemporaryReadingNavigationViewModelTest`：6 tests，0 failures，0 errors，0 skipped。
- `SourceNavigationContractTest`：11 tests，0 failures，0 errors，0 skipped。

## 未覆盖项

- 未接入 `ReaderRoute` / `AppNavigation` / `ReaderScreen` / `ReaderProgressEffects` / `ReaderViewModel`（属于后续切片，本切片明确不碰）。
- 未做跨 route 存活的实际接线验证（需集成者在 Activity/AppNavigation 层决定 scope，见 SEAM REQUEST）。
- `restoreAfterRestart` 未在 ViewModel 层暴露（当前以「新实例即空」语义满足“进程重启后临时栈消失”；如需显式恢复入口请集成者提 SEAM）。
- 未运行 lint、assemble、安装、connectedAndroidTest、真机或 WorkBuddy 验收。

## SEAM REQUEST

```text
SEAM REQUEST
目标文件：AppNavigation 层 / ReaderRoute 的 ViewModel scope 决策点
需要的接口/字段/行为：
  1. TemporaryReadingNavigationViewModel 的实例 scope 必须高于单个 reader route（建议 Activity 级或 AppNavigation 级 ViewModelStore），
     使其跨 reader route 存活、但进程重启即重置；
  2. 集成者需决定通过 Hilt 注入（@ActivityScoped 或自定义 holder）还是手动单例持有；
  3. ReaderRoute 在阅读器真实打开并有确切 source 位置时调用 recordNormalReading；
     点击跳转/查阅目标时调用 beginTemporaryInspection(destination target)；
     返回按钮可见性 = hasReturnableTarget；返回动作 = returnFromTemporaryInspection；
  4. 普通位置持久化仍走既有 ReaderViewModel/进度链路，本协调器不落库、不参与恢复。
调用位置：ReaderRoute / AppNavigation，后续切片实现
为什么现有接口不足：本协调器是纯内存协调器，无绑定生命周期；跨 route 存活只能由调用方如何 scope 决定，
  现有 AppNavigation.kt / ReaderRoute.kt 属他人所有权，本切片不得修改。
兼容方案与测试：协调器 API 已纯 JVM 可测；接线时可新增一个 Activity 级 smoke 单测验证跨 route 同一实例。
是否阻塞本轮：否（本切片 Dev verified，接线留待后续切片）。
```