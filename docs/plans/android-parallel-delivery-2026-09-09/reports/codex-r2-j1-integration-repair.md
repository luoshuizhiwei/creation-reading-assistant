# R2-J1-I 临时查阅真实接线修补报告

**日期**: 2026-09-10
**执行人**: codex
**范围**: Android reader 临时查阅（TEMPORARY）**生产接线**修补 + 伪测试清理
**状态**: 代码完成、定向 JVM 测试与 `compileDebugKotlin` 通过；**未做真机 / WorkBuddy 验收**，**未 commit / push**

---

## 1. 问题与根因

Trae 的 R2-J1.1–1.5 已把「临时查阅」的**状态机、路由构造、UI 呈现**全部落地（110 个单测通过），但 J1.5 报告自己写明了缺口：

> 未覆盖项 → 5. 集成验证：**与 ReaderProgressEffects 的位置上报集成**

对应的源码事实（修补前）：

| # | 缺陷 | 位置 | 后果 |
|---|------|------|------|
| D1 | `onSourcePositionChanged` 是**死 seam**：`ReaderRoute` 把它接到 `recordNormalReading`，但 `ReaderProgressEffects` 从未调用它 | `ReaderProgressEffects.kt`（无调用点）、`ReaderRoute.kt:96` 原注释「J1.2 预留为 seam、尚未调用」 | 协调器**永远收不到普通阅读位置** → `SourceNavigationState.normalReading` 恒为 null → 临时查阅栈压入的是 `active`（同样恒 null）→ 返回栈实际上靠 `beginTemporaryInspection(destination)` 单独压栈才不至于全空，但「返回到普通阅读处」的**真源位置从未被记录**，LIFO 最底层不可靠 |
| D2 | `ReaderRoute` **未订阅** `TemporaryReadingNavigationViewModel.state`，也未向 `ReaderScreenCallbacks` 传 `hasReturnableTarget` / `onTemporaryReturn` | `ReaderRoute.kt` | `ReaderScreenCallbacks` 这两个字段一直是默认值 `false` / `{}` → 「返回阅读处」按钮**永不显示**（`ReaderChrome.kt:553` 要求 `temporaryInspection && hasReturnableTarget`）；`ReaderScreen.kt:295` 的系统 Back 临时返回分支**永不触发**；顶层 Back 虽已走 `resolveTemporaryReturnRoute`，但与按钮/系统 Back **不是同一个动作** |

即：**状态机是好的，线没接上**。J1 的纯函数测试全绿，也正是因为伪测试复述了源码分支、没有断言真实接线。

---

## 2. 修复内容（独占文件内）

### 2.1 `ui/screen/reader/ReaderProgressEffects.kt`

**(a) 新增 seam 参数**（L124-129）

```kotlin
/**
 * R2-J1-I：真实普通阅读位置上报 seam。由 ReaderRoute 注入并落到临时查阅协调器的
 * `recordNormalReading`；只在「普通阅读、初始定位完成、source 坐标有效」时被调用
 * （见 [reportableSourceTarget]），temporary 模式绝不触发，因此不会覆盖普通阅读位置。
 */
onSourcePositionChanged: (SourceNavigationTarget) -> Unit = {},
```

**(b) 两条真实、稳定的 source 位置路径各加一次上报** —— 都是既有的 500ms 防抖 `snapshotFlow` 落库 effect，与落库同点、同门槛：

- 滚动 TXT / Markdown（L204-215，滚动进度落库 effect 内）
- 分页引擎（L247-256，翻页进度落库 effect 内）

```kotlin
reportableSourceTarget(
    bookId = bid,
    isEpub = false,                 // 滚动路径恒为 TXT/Markdown（epubBook != null 已在上方守卫 return）
    chapterStartOffsets = chapterStartOffsets,
    absoluteOffset = offset,
    initialPositionPending = initialPositionPending,
    temporaryInspection = temporaryInspection,
)?.let(onSourcePositionChanged)
```

**(c) 新增纯 reducer `reportableSourceTarget`**（L628-638），作为两条路径共用的上报门槛：

```kotlin
internal fun reportableSourceTarget(
    bookId: String,
    isEpub: Boolean,
    chapterStartOffsets: List<Int>,
    absoluteOffset: Int,
    initialPositionPending: Boolean,
    temporaryInspection: Boolean,
): SourceNavigationTarget? {
    if (!canPersistNormalReadingProgress(initialPositionPending, temporaryInspection)) return null
    return sourceNavigationTargetFor(bookId, isEpub, chapterStartOffsets, absoluteOffset)
}
```

设计要点：**上报门槛与落库门槛同源**（都走 `canPersistNormalReadingProgress`），因此「上报位置」与「保存位置」不可能分叉 —— temporary 查阅与 initial pending 期间二者同时被抑制；坐标语义完全由既有 `sourceNavigationTargetFor`（L579）决定，只接受 source 绝对偏移。

### 2.2 `ui/screen/reader/ReaderSessionEffects.kt`（L320）

把 route 注入的 seam 透传给 effects 层（唯一调用点）：

```kotlin
onSourcePositionChanged = callbacks.onSourcePositionChanged,
```

### 2.3 `ui/screen/reader/ReaderRoute.kt`

**(a) 订阅协调器状态**（L33-39）

```kotlin
val temporaryNavState: SourceNavigationState? = if (temporaryNavigation != null) {
    temporaryNavigation.state.collectAsStateWithLifecycle().value
} else {
    null
}
```

**(b) 唯一 LIFO 返回动作**（L58-70）

```kotlin
val performTemporaryReturn: () -> Unit = {
    val returnRoute = temporaryReturnRouteStep(navigationMode, temporaryNavigation)
    if (returnRoute != null) {
        navController.navigate(returnRoute) { launchSingleTop = true }
    } else {
        navController.popBackStack()
    }
}
```

**(c) 三处返回入口收敛到同一闭包**（L99 / L108 / L110 / L112）

```kotlin
onBack = performTemporaryReturn,                                  // 顶栏 Back
onSourcePositionChanged = { target -> temporaryNavigation?.recordNormalReading(target) },
hasReturnableTarget = hasReturnableTemporaryTarget(temporaryNavState),  // 实时派生
onTemporaryReturn = performTemporaryReturn,                       // 返回按钮 + 系统 Back
```

消费链路核对（无需改动，只做核验）：

| 入口 | 链路 | 现在落到 |
|------|------|----------|
| 顶栏 Back | `ReaderChromeAction.Back` → `ReaderActions.kt:151` → `callbacks.onBack` | `performTemporaryReturn` |
| 「返回阅读处」按钮 | `ReaderChrome.kt:555` → `ReturnToReading` → `ReaderActions.kt:152` → `ReaderScreen.kt:454 onReturnToReading = onTemporaryReturn` | `performTemporaryReturn` |
| 系统 Back | `ReaderScreen.kt:295 temporaryInspection && hasReturnableTarget -> onTemporaryReturn()` | `performTemporaryReturn` |
| 按钮可见性 | `ReaderChrome.kt:553` ← `ReaderScaffold.kt:480 callbacks.hasReturnableTarget` | 实时协调器状态 |

**(d) 两个可测试生产函数**（L120-141）

```kotlin
internal fun temporaryReturnRouteStep(
    navigationMode: ReaderNavigationMode,
    temporaryNavigation: TemporaryReadingNavigationViewModel?,
): String? {
    if (navigationMode != ReaderNavigationMode.TEMPORARY) return null
    if (temporaryNavigation == null) return null
    return resolveTemporaryReturnRoute(temporaryNavigation)
}

internal fun hasReturnableTemporaryTarget(state: SourceNavigationState?): Boolean =
    state?.temporaryReturnStack?.isNotEmpty() == true
```

### 2.4 `ui/screen/reader/ReaderScreenArgs.kt`

只改**失实注释**（原注释写「本切片只预留、不接线」「默认空实现，后续接导航协调器」，接线后即错误）：

- `ReaderNavigationMode` 文档：`该模式由 ReaderRoute 从路由参数（navigationMode=temporary）解析后注入。`
- `onSourcePositionChanged` / `hasReturnableTarget` / `onTemporaryReturn` 文档改为描述真实语义。

### 2.5 `ui/screen/reader/ReaderScreen.kt` —— **未改动**

进独占清单，但核验后确认消费侧已完整（`callbacks.hasReturnableTarget` / `onTemporaryReturn` 已在 L271-272 读取，L287/L295/L454 已正确使用），问题纯在注入侧，因此不动它，避免引入无关 diff。

---

## 3. 既有约束保持核对

| 约束 | 保持方式 | 结果 |
|------|----------|------|
| 只有 source locator，无页码 / 显示偏移 / LazyList index | 复用未改动的 `sourceNavigationTargetFor`（只接受 `absoluteOffset`）+ `ReaderLocator`（无页码字段） | ✅ |
| temporary 模式绝不覆盖普通阅读位置 | `reportableSourceTarget` 在 `temporaryInspection=true` 时直接返回 null，seam 不被调用 | ✅ |
| 不把临时状态写进普通阅读进度 | `ReaderScreen.kt:457 persistOnLeave = if (!temporaryInspection) ...`（既有）+ 上报门控同源 | ✅ |
| 普通 sourceLocator 路由保持普通 | `readerSourceRoute` / `sourceTarget` 未改；`navigationMode` 缺省/未知仍降级 NORMAL | ✅ |
| 无有效返回目标保持原 exit-reader 行为 | `temporaryReturnRouteStep` 返回 null → `popBackStack()`；按钮不显示（`hasReturnableTarget=false`） | ✅ |
| 不依赖 NavController back stack 数量 | 返回目标唯一真源 = 协调器 `temporaryReturnStack`（`resolveTemporaryReturnRoute` 既有语义） | ✅ |
| 改动仅为独占文件 + 报告 | 见第 5 节；其余 Reader 文件为其他 agent 的**既有未提交改动**，未触碰 | ✅ |

---

## 4. 伪测试清理

### 4.1 判定标准

伪测试 = ① 断言恒真字面量（`assertTrue(true && true)`）；② 把生产代码的 `when` / `if` 表达式**复制进测试**再断言自己写的分支结果；③ 文本匹配源码。三者都不会因生产代码被改坏而失败。

### 4.2 删除的伪测试（共 21 个）

| 文件 | 原/删 | 典型伪测试 |
|------|-------|-----------|
| `ReaderTemporaryBackTest.kt` | 原 10 / 删 10 | `temporary inspection state is correctly derived from navigation mode` → `assertTrue(TEMPORARY == TEMPORARY)`；`return to reading button visibility...` → 复制 `&&`；4 个 `back handler ...` → 复制生产 `when`；`return to reading callback is invoked exactly once` → 断言测试自己定义的 lambda |
| `ReaderTemporaryInspectionUiTest.kt` | 原 13 / 删 11（保留 2 个字段透传测试） | `return button visible only when both conditions met` → `assertTrue(true && true)`；`Back priority closes sheet before temporary return` / `Back triggers temporary return...` / `Back does not trigger...` → 复制生产 `when`；`BackHandler enabled condition...` → 复制生产 `enabled` 表达式；`temporary inspection does not trigger persistOnLeave` → `assertFalse(!true)` |

### 4.3 重写后的真实测试（全部调用生产函数 / 生产 reducer）

| 文件 | 数量 | 调用的生产代码 |
|------|------|----------------|
| `ReaderTemporaryBackTest.kt`（重写） | 6 | `temporaryReturnRouteStep(...)` + 真实 `TemporaryReadingNavigationViewModel`；断言真实产出的 route 字符串与 `decodeSourceLocatorFromRoute` 解出的 offset |
| `ReaderTemporaryInspectionUiTest.kt`（重写） | 5 | `hasReturnableTemporaryTarget(...)` + 真实协调器（含「普通位置不算待返回目标」「逐层返回后转 false」），另保留 2 个 `ReaderInteractionLayerState` 字段透传测试 |
| `ReaderSourcePositionTest.kt`（扩充 8→14） | +6 | 新增 `reportableSourceTarget(...)` 门槛回归：普通通过、temporary 抑制、pending 抑制、双抑、无效坐标拒绝、EPUB 章节元组 |

关键断言示例（会因生产代码被改坏而失败）：

```kotlin
val vm = coordinatorWithNormalReading().apply { beginTemporaryInspection(target("book-b", 200)) }
val route = temporaryReturnRouteStep(ReaderNavigationMode.TEMPORARY, vm)
assertTrue(route!!.startsWith("reader/book-a?sourceLocator="))
assertFalse(route.contains("navigationMode="))
assertEquals(100, decodeSourceLocatorFromRoute(route)?.legacyOffset)
assertFalse(vm.hasReturnableTarget)   // 确认确实消费了一层 LIFO
```

---

## 5. 验证结果（真实计数）

均在 `android/` 下、`GRADLE_USER_HOME=D:\develop\env\gradle`、`--no-daemon` 执行。

| 命令 | 结果 | 真实计数（取自 JUnit XML，非日志文本） |
|------|------|--------------------------------------|
| `:app:compileDebugKotlin` | **BUILD SUCCESSFUL in 15s**（exit 0） | — |
| `:app:testDebugUnitTest --tests "…ui.screen.reader.*"` | **BUILD SUCCESSFUL**（exit 0） | 56 suites / **343 tests / 0 failures / 0 errors / 0 skipped** |
| `:app:testDebugUnitTest --tests "…ui.navigation.*" --tests "…feature.reader.navigation.*" --tests "…feature.reader.pager.*"` | **BUILD SUCCESSFUL**（exit 0） | 34 suites / **250 tests / 0 failures / 0 errors / 0 skipped** |

**本次直接相关的重点类**

| 测试类 | tests | fail | err |
|--------|-------|------|-----|
| `ui.screen.reader.ReaderSourcePositionTest` | 14 | 0 | 0 |
| `ui.screen.reader.ReaderTemporaryBackTest` | 6 | 0 | 0 |
| `ui.screen.reader.ReaderTemporaryInspectionUiTest` | 5 | 0 | 0 |
| `ui.screen.reader.ReaderProgressNavigationTest` | 7 | 0 | 0 |
| `ui.screen.reader.ReaderReplacementCapabilityTest` | 18 | 0 | 0 |
| `feature.reader.navigation.SourceNavigationContractTest` | 11 | 0 | 0 |
| `feature.reader.navigation.TemporaryReadingJourneyTest` | 11 | 0 | 0 |
| `ui.navigation.TemporaryReadingNavigationViewModelTest` | 6 | 0 | 0 |
| `ui.navigation.ReaderTemporaryRouteTest` | 9 | 0 | 0 |
| `ui.navigation.ReaderTemporaryRouteJourneyTest` | 11 | 0 | 0 |

合计 **593 tests / 0 failures / 0 errors**（两批 343 + 250，无重叠）。

> 说明：Trae J1.5 报告中的 110 tests 只覆盖 J1 定向类；本报告的两批为**包级过滤**，覆盖范围更大（reader 屏幕层全部 + navigation/pager 全部），因此计数不同属正常。

---

## 6. 未覆盖与风险

**未做（按任务要求）**

1. **真机 / WorkBuddy 验收**：未运行 `connectedAndroidTest`，未验证「返回阅读处」按钮视觉、48dp 触控、TalkBack、多层临时查阅的视觉反馈。
2. **未 commit / push**。
3. **未开始 R2-S1**。

**本切片仍存在的客观限制**

| 项 | 说明 |
|----|------|
| 接线本身无 JVM 测试覆盖 | `ReaderRoute` 是 composable + `NavHostController`，纯 JVM 无法实例化。已把**可判定逻辑**抽成纯函数 `temporaryReturnRouteStep` / `hasReturnableTemporaryTarget` / `reportableSourceTarget` 并测到；剩下的「两个 helper 是否真被 composable 调用」只能靠真机 UI 测试，属已知盲区。 |
| 分页路径 EPUB 上报的 ci/co 正确性 | 依赖 `EpubLocatorMapping.toChapterOffset` 与 `chapterStartOffsets` 一致（既有 R2-N1 语义）。若书内 `chapterStartOffsets` 与投影源不同源，上报的章节元组可能不准；**待真机在 EPUB 分页模式下用「跨章临时查阅 → 返回」验证**。 |
| `recordNormalReading` 会清空临时链 | 由既有 `SourceNavigationContract` 决定（`recording normal reading clears temporary chain` 已测）。含义：临时查阅期间若发生任何被判为「普通阅读」的上报，返回栈会被清空。当前门控保证 temporary 期间不上报，因此行为正确；但**若将来有人在 temporary 期间放开上报，返回栈会静默丢失** —— 这是设计上的单点约束，值得在后续 PR 加注释守卫。 |
| 返回后重新进入 reader 的位置恢复 | 返回最后一层走 `readerReturnRoute`（无 `navigationMode`）重新进入，位置经 `pendingSourceLocatorJsonState → pendingInitialPositionState` 恢复，恢复期间上报被抑制（符合 D1 修法）。链路依赖既有恢复逻辑，**待真机验证「返回后是否精确回到原阅读位置」。** |

---

## 7. 建议后续步骤

1. **真机验收（需授权）**：本轮修的是接线，只有真机能证明「按钮出现 / 三种 Back 一致 / 返回位置准确」。建议验收矩阵：
   - 单层：普通阅读 A → 临时查阅 B → 按钮/顶栏 Back/系统 Back 三选一 → 均回到 A 且按钮消失
   - 多层：A → B → C，逐层返回，中间层仍显示「返回阅读处」
   - 跨书：跨书临时查阅返回后回到原书原位置
   - 反例：普通阅读（无临时栈）下按钮**不显示**，Back 正常退出阅读器
2. **R2-S1**（未开始，按指示）。
3. 若后续要做**自动化**覆盖接线，需引入 Compose UI test（`createComposeRule` + fake `NavHostController`）或 Robolectric，属独立基建工作。

---

**报告完成时间**: 2026-09-10
**报告版本**: v1.0
