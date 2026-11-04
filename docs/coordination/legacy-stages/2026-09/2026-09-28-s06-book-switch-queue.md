# S06 切书清队核查：旧翻页队列是否会带入新书（2026-09-28）

- **执行角色**：S06 窄任务棒（本轮唯一 Gradle 所有者；未使用 MuMu/ADB）
- **任务书**：先沿真实组合生命周期追踪 bookId、turnRequest 与 PageTurner 内 `remember { PendingTurnQueue() }`；若切书已导致组合销毁或已有清队机制，给可核实证据并**停止，不制造修复**。
- **结论先行**：**「旧书积压翻到新书」风险不可达——切书必经导航换 NavBackStackEntry，navigation-compose 2.5.1 的 NavHost 以 `entry.id`（每实例唯一 UUID）为组合 key，旧阅读器组合（含翻页队列与 turnRequest 单槽）随切书销毁。旧行为无法复现；本轮零源码改动、零 Gradle 运行、未新建测试文件。**审查报告 §3.4 的假设（「若切书时宿主组合位置不变」）被证伪：组合位置在导航层就已换掉，不需要在 pager 层补 `key(bookId)` 或 `clear()`。

---

## 1. 结论（对任务书分支的裁决）

任务书两条分支中走**第一条**：「切书已经导致组合销毁」成立。未修复、未写回归测试（不可达场景写回归测试只会钉住一个永远不会走到的路径，属制造修复范畴）。

## 2. 证据链（全部可回读复核）

### 2.1 生产组合链（静态读码）

```
AppNavHost composable("reader/{bookId}?highlightId=…&sourceLocator=…&navigationMode=…")   AppNavigation.kt:523-556
  └─ ReaderRoute(bookId = 导航参数, viewModel = hiltViewModel())                            ReaderRoute.kt:22-29,548
       └─ ReaderScreen(bookId = inputs.bookId)                                             ReaderScreen.kt:140-145,325
            └─ ReaderContentHost（plain when，无 key 包裹）                                 ReaderContentHost.kt:165-199
                 └─ ReaderContentHostPagedBranch → PagedReaderHost(source, bookId = s.bid)  ReaderContentHostPagedBranch.kt:28-29,89
                      └─ PagedReaderPageSurface                                             PagedTxtReaderHost.kt:313
                           └─ PageTurner(…)（生产唯一调用点）                                PagedReaderPageSurface.kt:545
                                └─ remember { PendingTurnQueue() }                          PageTurner.kt:120
```

关键事实（逐行核实）：

| # | 事实 | 位置 |
|---|---|---|
| 1 | `pendingTurns = remember { PendingTurnQueue() }`，无 key | `PageTurner.kt:120` |
| 2 | 队列唯一 `clear()` 挂在翻页**效果**切换 `LaunchedEffect(normalizedEffect)` | `PageTurner.kt:350-363`（clear 在 354） |
| 3 | `turnRequest` 单槽 `remember { mutableIntStateOf(0) }`，无 key、非 saveable | `PagedTxtReaderHost.kt:195` |
| 4 | 跳转/硬件键请求槽同属 per-entry 组合的普通 `remember` | `ReaderPagerEngineState.kt:172-173` |
| 5 | `bookId` 是导航路由参数；`ReaderAction.OpenBook` 的 UI 侧唯一派发点是 `LaunchedEffect(bookId)`，喂给 per-entry ViewModel 的永远是本栈项自己的 bookId | `AppNavigation.kt:544-548`、`ReaderRoute.kt:41-43` |
| 6 | `ReaderAction` 全集中无「原地换书」动作：只有 `OpenBook` 与 `Retry`（`Retry` 重开 `requestedBookId` = 同书） | `ReaderModels.kt:102-104`、`ReaderViewModel.kt:328-329` |
| 7 | `movableContentOf` 全 main 源码零命中（排除跨组合搬运状态） | grep，见证据 03 |
| 8 | Compose `key()` 在 reader/pager 两目录零命中（命中仅为 `ReplaceProfile.key(bookId,…)` 字符串拼接函数，非组合 key） | grep，见证据 03 |
| 9 | `PendingTurnQueue` 是纯实例类（内部 `ArrayDeque`），无静态/单例状态 | `PendingTurnQueue.kt:16-55` |
| 10 | ViewModel 注释虽写「同一个 ViewModel 实例跨书复用」的防御（切书清选区/搜索词等），但该实例跨书复用在当前 UI 派发面**没有触发点**（见 #5/#6）；防御代码本身与翻页队列无关 | `ReaderViewModel.kt:664-774` |

### 2.2 导航层的组合销毁机制（AAR 字节码反汇编，非记忆引用）

navigation-compose 实际解析版本 **2.5.1**（Gradle 缓存 `D:\develop\env\gradle\caches\modules-2\files-2.1\androidx.navigation\navigation-compose\2.5.1\`，与 `AppNavigation.kt:397` 注释「navigation-compose 锁定 2.5.1」一致）。对缓存内 AAR 解包 `classes.jar` 后 `javap -c -p` 反汇编：

1. **NavHost 以 entry.id 为组合 key 渲染当前栈项**（`NavHostKt.class`，源码位 `5379L1059`）：
   `Crossfade(targetState = currentEntry.getId(): String, …)` —— targetState 即 entry.id，未传 contentKey，默认 `{ it }`。切书产生新栈项 ⇒ target 变化 ⇒ Crossfade 换内容 key，旧内容组合在过渡结束后销毁。
2. **内容经 per-entry UUID 的 SaveableStateProvider 包裹**（`NavBackStackEntryProviderKt.class`）：
   `SaveableStateHolder.SaveableStateProvider(<BackStackEntryIdViewModel.getId(): UUID>, content)` —— 只有 `rememberSaveable` 状态按 UUID 槽位存续；**普通 `remember` 状态不属于存续范围**。
3. **每个 NavBackStackEntry 实例的 id 构造时即 `UUID.randomUUID().toString()`**（navigation-common 2.5.1 `NavBackStackEntry.class`）—— 即使 `launchSingleTop` 替换栈顶（`ReaderRoute.kt:65` 临时查阅返回路径），替换产生的也是**新实例、新 UUID**，不存在「同 entry 换 bookId 参数」的复用。

三者合并 ⇒ 任何一次切书（书架/首页/灵感跳 `reader/{bookId}`、临时查阅跨书、临时查阅返回）都会让旧书的 PageTurner 组合整体销毁：`remember { PendingTurnQueue() }` 与 `remember { mutableIntStateOf(0) }` 的 turnRequest 一并消亡，新书组合从空队列、空槽起步。**不存在旧队列排到新书的通路。**

### 2.3 排除过的旁路（均无可达性）

| 旁路 | 排除依据 |
|---|---|
| `launchSingleTop` 复用栈顶 entry | 2.5.1 替换栈顶 = 新 NavBackStackEntry 实例 = 新 UUID = 新组合（§2.2-3） |
| 返回栈内旧 entry 复活时队列尚存 | entry 不可见期间组合即被销毁；复活只经 `SaveableStateProvider` 恢复 saveable 槽位，普通 `remember`（队列/turnRequest）全新 |
| 临时查阅（R2-J1）跨书 | 走 `navController.navigate(reader/{bookId})` 新栈项；`TemporaryReadingNavigationViewModel`（Activity 作用域，`AppNavigation.kt:187`）只持导航目标，不持翻页状态 |
| reader 内部动作换书 | `ReaderAction` 无换书变体；`openBook(bookId)` 的 bookId 恒等于本栈项导航参数（§2.1-#5/#6） |
| Crossfade 过渡窗口（新旧组合并存数百毫秒） | 旧书队列在窗口内排空只会打到**旧书自己的 controller/组合**（`onNext = { controller.nextPage() }` 闭包属旧组合），对新书 PageTurner（另一实例、空队列）零影响；旧 controller 随 dispose 经 `DisposableEffect { onDispose { controller.close() } }` 关闭（`PagedTxtReaderHost.kt:280-282`） |
| 进程重建/配置变化 | 整组合重建，队列全新 |

## 3. 旧行为能否复现

**不能。** 「旧书积压翻到新书」需要旧组合在切书后继续存活并持有队列；导航层的 entry.id 键化（§2.2）使该前提不成立。因此：未写回归测试、未做最小修复、未触碰「同帧单槽合并」「程序化跳转」两个已登记的独立片。

## 4. 修改文件

**零。** 三个在管文件（`PagedTxtReaderHost.kt`、`PagedReaderPageSurface.kt`、`PageTurner.kt`）与前轮 S06 的未提交改动原样保留（工作区哈希与 git 状态见证据 00；`PendingTurnQueue.kt` sha256 `4e5c87a1…5b3ff` 与审查报告 §1.1 记录逐位一致，证实本轮读到的就是同一段字节）。未执行 reset/checkout/commit/push，未动他人文件、公共类型与 Gradle 配置。

## 5. 执行的命令与退出码

本轮**无 Gradle 运行**（零源码改动，无编译/测试对象；也按任务书不重跑 MuMu 8 项）。全部核查为静态命令：

| 命令（摘要） | 用途 | 退出码 |
|---|---|---|
| `git status --short -- <四文件>` / `sha256sum <四文件>` | 在管文件状态与字节身份 | 0 |
| `grep -rn "PagedReaderHost(\|PageTurner(\|ReaderAction.OpenBook\|movableContentOf\|…"` 等 8 组 | 调用链/排除项取证 | 0（命中）或 1（无命中，逐组标注） |
| `unzip -o -q <navigation-compose-2.5.1.aar> classes.jar` ×2（compose 与 common） | 解包导航 AAR | 0 |
| `javap -c -p NavHostKt.class / NavHostKt$NavHost$4.class / NavBackStackEntryProviderKt.class / NavBackStackEntry.class` | 组合生命周期字节码取证 | 0 |

## 6. 原始证据路径

`E:\creation-reading-assistant\codex-s06-bookswitch-20260928\`

| 工件 | 内容 |
|---|---|
| `00-in-scope-files-git-status.txt` | 四文件 git 状态 + sha256（PageTurner.kt `7eca6027…`、PagedReaderPageSurface.kt `767f528e…`、PagedTxtReaderHost.kt `c03aaf2e…`、PendingTurnQueue.kt `4e5c87a1…`） |
| `01-navhost-crossfade-entryid-bytecode.txt` | NavHostKt `Crossfade(entry.id)` 字节码段 + `LocalOwnersProvider` 调用段 |
| `02-saveable-state-provider-uuid-bytecode.txt` | `SaveableStateProvider(UUID)` 字节码 + `NavBackStackEntry` 构造期 `UUID.randomUUID().toString()` 字节码 |
| `03-call-chain-greps.txt` | §2.1 全部 grep 原始输出（含无命中项与 exit 码标注） |

AAR 来源路径（未复制进工件目录，体积原因）：`D:\develop\env\gradle\caches\modules-2\files-2.1\androidx.navigation\navigation-compose\2.5.1\4164c14c3f4d585ac5d2e3e5b614d1c8e785b05c\navigation-compose-2.5.1.aar`、`…\navigation-common\2.5.1\75f03f760a3357c0656703a16dc7635b00628660\navigation-common-2.5.1.aar`。

## 7. 未覆盖边界（交总指挥裁决/登记）

1. **无设备级运行验证**：结论由静态读码 + AAR 字节码 + Compose 运行时公开语义构成；按任务书禁用 MuMu/ADB，未做「真机切书 + 留存积压」的实测复现。若总指挥认为需要设备背书，可在既有 qahost 壳上加一个「组合换帧模拟切书」的 instrumentation 用例，但按本轮证据它验证的是导航层语义而非产品 bug。
2. **同书 source 重建时队列存续**（非切书，仅登记观察）：同一 entry 内替换规则变更/TXT 目录重扫会重建 `preparedPagedSource`（`ReaderPagerEngineState.kt:203-249` 的 remember 键含 `replaceProfileKey`）→ 重建 controller，而 PageTurner 组合位置不变、队列存续；积压方向随后排到**同书**重排后的 controller 上。这与「程序化跳转/重排」已登记域相邻，不属本任务「切书」范畴，未处理。
3. **navigation-compose 版本钉定方式存疑**：catalog 中 `androidx-navigation-compose` 未写版本（`libs.versions.toml:62`），但 Gradle 缓存唯一解析到 2.5.1 且源码注释自述「锁定 2.5.1」；实际解析来源未追到声明处（可能在依赖图约束），不影响本轮结论，但值得构建负责人知悉。
4. MuMu 8 项快翻测试（已 PASS）依旧不含切书场景；本轮结论不扩大其覆盖声明，也不要求重跑。

---

**本棒停止，交总指挥复核。**
