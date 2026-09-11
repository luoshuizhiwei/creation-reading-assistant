# WorkBuddy 切片报告：R2 J1-I.1 —— 临时查阅新鲜验证与实际接线审计

- 日期：2026-09-10
- 报告人：WorkBuddy（接收「剩余原生 Android 开发」交接）
- 工作目录：`D:/develop/Code/Codex/creation-reading-assistant`
- 切片定位：J1（返回阅读处 / 跳转历史 / 临时查阅）的 **I.1 子切片 = 新鲜门禁 + 生产调用点接线审计**，不改产品行为。

---

## 1. 基线

| 项 | 值 |
|---|---|
| `pwd` | `/d/develop/Code/Codex/creation-reading-assistant` |
| 分支 | `main` |
| HEAD | `e31db92d5493b6b705ae447aba82c52dc9d7e65f` |
| `git status --short` 条目数 | 327（共享脏工作区，含本轮之前及他人 WIP） |
| 记录时刻 | 2026-09-10 23:59 +0800 |
| 外来 Gradle/Java 进程 | 无。抓取时存在 2 个 `java.exe`（32388 / 6828），均为本切片自身启动的 Gradle/Kotlin daemon，非其他 agent 孤儿进程 |
| Git 写操作 | 无。未 `add` / `commit` / `push` / `reset` / `checkout` / `clean` / `stash` |

**WIP 基线确认**：327 项脏文件涵盖 Android reader/页面 WIP、文档、诊断工件与 desktop 改动，全部按用户资产保留。本切片未修改任何产品文件（纯验证 + 审计），因此不引入新的脏文件差异。

---

## 2. 本切片目标与范围

J1-I.1 只做两件事：

1. **新鲜门禁**：以 `--rerun-tasks` 强制重跑，取得不依赖 UP-TO-DATE 缓存的当期证据；覆盖 10 个指定测试类 + `:app:compileDebugKotlin`。
2. **实际接线审计**：确认「临时查阅」旅程的生产侧调用点真实存在，而不是仅有纯函数与全绿单测。

约束：串行 Gradle；只跑定向 JVM 与编译；不安装、不 connectedAndroidTest、不动设备、不用 MuMu；只用 source 坐标；不改用户书库、不记录真实书名。

---

## 3. 变更文件清单

**本切片未修改任何产品源码 / 测试 / 资源文件。**

- 只读：`android/app/src/main/java/com/creationreadingassistant/**` 的接线路径（见 §5）。
- 新增：本报告文件。
- 临时产物：`android/wb-j1i1-verify.log`（Gradle 原始日志，未入库；非产品文件）。

被审计的接线本体由**上一片 R2-J1-I** 落地（报告：`reports/codex-r2-j1-integration-repair.md`）。J1-I.1 对其做独立复验与调用点核查。

---

## 4. 命令、退出码与新鲜测试计数

### 4.1 命令（在 `android/` 下执行）

```powershell
$env:GRADLE_USER_HOME="D:\develop\env\gradle"
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --rerun-tasks --console=plain `
  --tests "com.creationreadingassistant.feature.reader.navigation.SourceNavigationContractTest" `
  --tests "com.creationreadingassistant.ui.navigation.TemporaryReadingNavigationViewModelTest" `
  --tests "com.creationreadingassistant.ui.navigation.ReaderTemporaryRouteTest" `
  --tests "com.creationreadingassistant.ui.navigation.ReaderTemporaryRouteJourneyTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSourcePositionTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderTemporaryBackTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderTemporaryInspectionUiTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderProgressNavigationTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.SearchHitNavigationTest" `
  --tests "com.creationreadingassistant.ui.screen.reader.SearchScrollFocusRequestTest"
```

- 退出码：**0**；`BUILD SUCCESSFUL in 3m 2s`；`30 actionable tasks: 30 executed`
- 关键点：`30 executed`（非 UP-TO-DATE）证明 `compileDebugKotlin` 与全部测试为**当期真实执行**。

> 过程记录（非阻塞）：首次以 bash `*>` 重定向触发了一个假失败——bash 把 `*` 做了文件名 glob，`APP_MODULE_PLAN.md` 等被当作 Gradle 任务传入，报 `Task 'APP_MODULE_PLAN.md' not found`（13s 失败，非编译失败）。改用 `> log 2>&1` 后正常。记录以备他人复用同一日志写法时避坑。

### 4.2 新鲜测试计数（来源：`app/build/test-results/testDebugUnitTest/TEST-*.xml`）

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `feature.reader.navigation.SourceNavigationContractTest` | 11 | 0 | 0 | 0 |
| `ui.navigation.ReaderTemporaryRouteJourneyTest` | 11 | 0 | 0 | 0 |
| `ui.navigation.ReaderTemporaryRouteTest` | 9 | 0 | 0 | 0 |
| `ui.navigation.TemporaryReadingNavigationViewModelTest` | 6 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderProgressNavigationTest` | 7 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSourcePositionTest` | 14 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderTemporaryBackTest` | 6 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderTemporaryInspectionUiTest` | 5 | 0 | 0 | 0 |
| `ui.screen.reader.SearchHitNavigationTest` | 16 | 0 | 0 | 0 |
| `ui.screen.reader.SearchScrollFocusRequestTest` | 8 | 0 | 0 | 0 |
| **合计** | **93** | **0** | **0** | **0** |

10 个类全部产出 XML，计数与文件名一一对应；`compileDebugKotlin` 任务在执行清单内。

---

## 5. 实际接线审计（本切片核心）

审计判据：**生产侧调用点是否存在**。纯函数单测全绿不构成接线证据（该教训已记录在 R2-J1-I 报告中）。

### 5.1 D1 —— source 位置上报 seam：已接通

| 环节 | 位置 | 证据 |
|---|---|---|
| 参数声明 | `ui/screen/reader/ReaderProgressEffects.kt:129` | `onSourcePositionChanged: (SourceNavigationTarget) -> Unit = {}` |
| 上报闸门（纯 reducer） | `ReaderProgressEffects.kt` `reportableSourceTarget(...)`，调用点 `:215`（滚动 TXT/Markdown 500ms 防抖）与 `:256`（分页） | `)?.let(onSourcePositionChanged)` |
| 透传 | `ui/screen/reader/ReaderSessionEffects.kt:320` | `onSourcePositionChanged = callbacks.onSourcePositionChanged` |
| 注入 | `ui/screen/reader/ReaderRoute.kt:108` | `onSourcePositionChanged = { target -> temporaryNavigation?.recordNormalReading(target) }` |
| 落到协调器 | `ui/navigation/TemporaryReadingNavigationViewModel.kt:50-52` → `SourceNavigationContract.recordNormalReading` | 链尾存在 |

结论：`ReaderProgressEffects → ReaderSessionEffects → ReaderRoute → TemporaryReadingNavigationViewModel → SourceNavigationContract` 全链**无断点**。上报仍受 `canPersistNormalReadingProgress(...)` 同源门槛（普通阅读 + 非 initial pending + 有效 source 坐标），临时查阅模式不写普通阅读位置。

### 5.2 D2 —— ReaderRoute 订阅与三入口收敛：已接通

| 环节 | 位置 | 证据 |
|---|---|---|
| 订阅协调器状态 | `ReaderRoute.kt:35-39` | `temporaryNavigation.state.collectAsStateWithLifecycle().value` |
| 返回栈可见性（实时派生） | `ReaderRoute.kt:110` + `:140-141` | `hasReturnableTarget = hasReturnableTemporaryTarget(temporaryNavState)`（`temporaryReturnStack.isNotEmpty()`） |
| 统一 LIFO 返回动作 | `ReaderRoute.kt:62-69` + `:127-134` | `performTemporaryReturn` → `temporaryReturnRouteStep(...)` → `resolveTemporaryReturnRoute(...)`；未命中则 `popBackStack()` |
| 三入口同一动作 | `ReaderRoute.kt:99`（`onBack`）、`:112`（`onTemporaryReturn`）、`ReaderScreen.kt:295` / `:454` | 顶栏 Back、系统 Back、返回按钮收敛到 `performTemporaryReturn` |
| 消费点 | `ReaderScreen.kt:271-272, 287, 295, 454`；`ReaderScaffold.kt:479-480`；`ReaderChrome.kt:492, 553`；`ReaderInteractionLayer.kt:61, 171`；`ReaderLayerBuilders.kt:155, 178` | 全链消费存在 |

结论：返回可见性与返回动作同源派生，缺省（协调器未注入 / 普通阅读 / 临时栈空）时维持原「离开阅读器」行为，不伪造目标。

### 5.3 需求 7（不依赖 NavController back stack 数量）：构造性成立

- 依赖解析：`gradle/libs.versions.toml` 中 navigation-compose 未固定版本 → 实际解析 **2.5.1**（agp 8.6.0 / kotlin 2.0.21 / composeBom 2024.12.01）。
- 字节码核对（`javap` 反编译 navigation-runtime-2.5.1）：`launchSingleTop` 分支先比较 destination **id**，再以 `NavBackStackEntry(..., newArgs)` + `addLast` **替换栈顶条目**。阅读器所有 route 共用同一 destination `reader/{bookId}`，故替换后 **back stack 深度恒定**。
- 因此「普通翻页 / 临时跳转不堆积导航历史」由框架层保证，不依赖任何自维护计数。

### 5.4 缺口（→ J1-I.2）

**`readerTemporaryRoute` / `readerSourceRoute` / `readerReturnRoute` 在生产代码中零调用点。**

- 全仓 `navigate("reader/...")` 调用点（`AppNavigation.kt:457`、`HomeArchiveScreens.kt:239/258/307/321`、`ProfileRoute.kt:192/194/326`、`HomeRoute.kt:164`、`SearchScreen.kt:355/392/411`、`HomeContinueSheet.kt:213`、`MyReadingRoute.kt:65/122`、`ShelfRoute.kt:304/336/394/605/615/684`、`ShelfSearchRoute.kt:415`）全部只带 `bookId` 或 `highlightId=`，**无一携带 `sourceLocator=` 或 `navigationMode=`**。
- `ProfileRoute.kt:192` 的批注/笔记跳转走的是 `highlightId=`（普通模式），不是 `sourceLocator=`。
- 后果：`reader/{bookId}?...&navigationMode={navigationMode}` 的 `navigationMode` 恒为 `null` → `readerNavigationMode(null)` 恒为 `NORMAL` → `ReaderRoute.kt:49-56` 的 `beginTemporaryInspection` **在真实 App 中永不执行**，临时查阅旅程整体不可达。

即：**接线已通，但没有触发器。** 这正是 J1-I.2 要补的缺口。

---

## 6. 未覆盖项与风险

1. **无设备/视觉证据**：本切片不含安装、`connectedAndroidTest`、真机或截图；所有结论止于源码 + JVM + 编译。设备行为列为 TODO。
2. **无 instrumentation 证据**：Compose 层（返回按钮真实可见性、pill 点击）未在设备上验证。
3. **临时查阅旅程仍不可达**（§5.4）——在 J1-I.2 完成前，D1/D2 的接线属「已接但无入口」，不可对外宣称功能可用。
4. **`ReaderNotesSheet`（书内笔记面板）**未纳入本次审计的通路（其跳转语义与统一笔记页不同），留待 J1-I.2 决策。
5. 全量 JVM / Lint / assembleDebug / androidTest 编译**未在本次以 `--rerun-tasks` 重跑**（按交接只做定向门禁）；它们属集成后串行门禁。

---

## 7. SEAM REQUEST

无。本切片为只读验证与审计，未依赖任何跨所有权改动。

---

## 8. 状态

**Dev-verified（验证部分）** / **Blocked（功能可达性部分）**

- 已验证：10 类 93 项 JVM 测试在 `--rerun-tasks` 下 0 失败；`compileDebugKotlin` 当期执行成功；D1/D2 生产调用点全部存在；需求 7 由 navigation 2.5.1 `launchSingleTop` 构造性满足。
- 阻塞：临时查阅旅程无生产入口，需 J1-I.2 提供真实入口后方可判定该旅程可用。

**下一步**：J1-I.2 —— 提供真实的临时查阅入口（统一笔记/批注「临时查看来源」）。
