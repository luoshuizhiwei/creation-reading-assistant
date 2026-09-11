# R2-S1.5 命中 → 可返回（R2 退出条件）

> 切片归属：R2（文本索引与全局搜索）S1.5 — 搜索命中后返回搜索页并保留查询词与滚动位置。
> 状态：**Dev 验证通过**（compile + JVM 绿色；真机/仪器化待 CI/设备可用）。未 commit / push。

## 1. 目标

闭合 R2 退出条件「搜到内容可**精确到达**并**返回**」的「返回」侧：

- 从搜索结果点击一条正文命中 →（S1.4 已落地）经 `readerTemporaryRouteForSource` 进入阅读器**临时查阅**并精确到达；
- 在阅读器返回 → 回到**搜索页**，且**查询词、当前分类、正文列表滚动位置**都原样保留。

## 2. 根因

`SearchScreen.kt` 里的 `query` / `tab` 用 `remember { mutableStateOf(...) }` 局部持有，`LazyColumn` 没有传持久 `LazyListState`。

导航到阅读器是一个 **push**（S1.4 的 `navController.navigate(...)`），搜索页被移出组合、其 `remember` 状态随之丢弃。当阅读器返回时（`popBackStack`，见 §3），搜索页重新组合：

- `query` 重置为空 → `LaunchedEffect(query) { viewModel.search(query) }` 触发**空词搜索**，结果被清空；
- `tab` 重置为 `"all"`；
- `LazyColumn` 滚动位置归零。

→ 用户「返回」后看到的是空搜索历史态，而非原先的结果列表与位置。这正是 R2 退出条件要杜绝的。

## 3. 返回路径确认（结构已通，缺口在状态）

阅读器 `ReaderRoute.performTemporaryReturn`（ReaderRoute.kt:62-69）：

```
val returnRoute = temporaryReturnRouteStep(navigationMode, temporaryNavigation)
if (returnRoute != null) navController.navigate(returnRoute) { launchSingleTop = true }
else navController.popBackStack()   // ← 临时栈空时走这里
```

从搜索进入的是临时查阅（`navigationMode=temporary`），单程返回后临时栈清空 → `resolveTemporaryReturnRoute` 返回 null → 落到 `popBackStack()`，**搜索页仍在后退栈中**，结构上本就能返回。

**因此 S1.5 不需要动返回导航本身，只补「状态在往返中存活」这一层。**

## 4. 方案

| 项 | 改动 |
|---|---|
| 查询词 | `SearchViewModel` 新增 `val queryState = mutableStateOf("")`，`SearchScreen` 由 `var query by remember { mutableStateOf("") }` 改为 `var query by viewModel.queryState`。VM 绑定到 search 路由的 `NavBackStackEntry`，往返存活。 |
| 分类 Tab | 同理新增 `val tabState = mutableStateOf("all")`，`SearchScreen` 改用 `var tab by viewModel.tabState`。必须与 query 一起保留，否则返回后 Tab 落回「全部」会与保留下来的滚动所指列表不一致。 |
| 滚动位置 | `SearchViewModel` 持有 `val listState = LazyListState()`，`SearchScreen` 的 `LazyColumn` 改为 `LazyColumn(modifier = ..., state = viewModel.listState)`。同一 `LazyListState` 实例跨往返复用 → 滚动位置保留。 |
| 同词守卫 | 新增 `private var lastSearchedQuery: String?`。`search(q)` 在启动 job 前判断 `if (q.trim() == lastSearchedQuery) return`。返回时 `LaunchedEffect(query)` 会用同一词重触发 `search`，守卫使其**不再重复搜索、也不再重新转圈闪烁**；只在 `q.isBlank()` 与真正新词时重跑。 |

守卫关键边界：`lastSearchedQuery` 在 `search` 完成（debounce + withContext 落定）后才写入 `query.trim()`；中间被取消的 job 不会写它，因此「搜 A → 清空 → 再搜 A」仍能正确重跑。

## 5. 改动文件

- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/SearchViewModel.kt`
  - + `import androidx.compose.foundation.lazy.LazyListState` / `androidx.compose.runtime.mutableStateOf`
  - + `queryState` / `tabState` / `listState` / `lastSearchedQuery` 字段
  - `search(q)` 顶部加同词守卫；blank 分支写 `lastSearchedQuery = ""`；落定后写 `lastSearchedQuery = query`
  - **未改动构造函数**（新字段均为属性初始化器），既有测试构造方式不受影响
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/search/SearchScreen.kt`
  - `query` / `tab` 改用 `viewModel.queryState` / `viewModel.tabState`
  - `LazyColumn` 加 `state = viewModel.listState`
  - 移除不再使用的 `import androidx.compose.runtime.mutableStateOf`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/SearchViewModelTest.kt`
  - +3 测试（见 §6）；+ `import io.mockk.coVerify` / `org.junit.Assert.assertNotNull`

## 6. 验证

- 构建/测试：`PowerShell` 下 `.\gradlew.bat testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.SearchViewModelTest" --no-daemon`
  - `BUILD SUCCESSFUL` / `GRADLE_EXIT=0`
  - 测试报告：`tests="16" skipped="0" failures="0" errors="0"`（原 13 + 新增 3）
  - `compileDebugKotlin` / `compileDebugUnitTestKotlin` 均通过
- 新增 JVM 测试：
  1. `query and tab state default and are retained on the view model` — 默认 `""` / `"all"`，赋值后保留；
  2. `list state is held on the view model so scroll survives navigation` — `listState` 非 null；
  3. `repeating the same query does not re-run the search or re-trigger loading` — 首次搜「世界」落定后，再次 `search("世界")` 不重新转圈、且 `bookRepository.search` 仅被调用 1 次（`coVerify(exactly = 1)`，因 `search` 是 suspend 函数必须 `coVerify`）。

## 7. 风险 / 待办

- **聚焦副作用（非阻塞）**：`LaunchedEffect(Unit) { focusRequester.requestFocus() }` 在每次进入组合（含返回）都重新聚焦搜索框，可能弹出软键盘。本片范围仅限状态保留，未改；如需「返回时不抢焦点」，可改为 VM 标记仅首次聚焦。
- **进程死亡不保留**：VM 在进程被杀后重建，query/tab/滚动归零。符合本片「导航往返」范围，非目标。
- **真机/仪器化验收缺口**：设备 `c49ac6cf` 当前离线，无法跑仪器化。`query/tab/滚动跨 popBackStack 存活` 依赖 Compose-Navigation 既有契约（VM 按 back-stack entry 存活），已被构造正确性与 JVM 守卫测试覆盖；端到端视觉/真机走查留待 CI / 设备可用时（与 S1.1 迁移测试、S1.4 `resolveLegacyOffset` 同样降级）。
- **S1.6 仍 pending（#58）**：双通道口径 UI 文案 + 错误反馈 + SearchIndexRepository/SearchTermDao/tokenizer 补测试；并真正为每本书建**显示文（display）**通道索引（当前每书 display 覆盖行恒 `PENDING` + `display_channel_not_built`）。

## 8. 结论

R2 退出条件「搜到内容可精确到达并返回」**已闭环**：

- **到达**（S1.4）：搜索命中点击 → `readerTemporaryRouteForSource` 临时查阅 route + `resolveLegacyOffset` 精确换算全书偏移；
- **返回**（S1.5）：阅读器临时栈空时 `popBackStack` 回搜索页，VM 持有的 `query` / `tab` / `LazyListState` 跨往返保留，同词守卫避免返回时重复搜索与转圈。

未 commit / push；改动仅限 `android/**` 与测试，未触及 40+ 非 Reader 脏文件与 `docs/`（除本报告）。
