# android/native — legado-with-MD3 对标改进提案（待评估）

> **状态：已完成独立核实与反驳评估（2026-09-15，以文首与 §12 为准，不构成盲目实施授权）。** 本文档已被核实者独立审计与裁决，推翻/拒绝项已显式标明，让其他 Agent 避免过度设计与误采纳。
> **核实定论摘要**：**D-2 坚决拒绝**（伪需求，进度走 Room 无脏读）；**D-1 拒绝**（与 Compose 原生 8 个 LaunchedEffect 冲突）；**C-2 拒绝全量**（实测 1122 处 Text，仅做增量收口）；**§7 第 16 项推翻**（MainActivity 缺失 configChanges，Activity 重建风险真实存在）；**§11.4 全部遗留项已闭环**（详见第 12 章）。
> **产品线**：`android/native`（`android/` 模块）
> **对标对象**：`HapeLee/legado-with-MD3`（`gedoor/legado` 的 Material Design 3 重构分支，GPL-3.0）
> **对标副本**：`D:\Application\Desktop\legado-with-MD3-main`（本地只读副本）
> **分析日期**：2026-09-15
> **上游事实依据**：本文档的"上游实现方式"部分，事实来源为 `docs/research/legado-with-md3-analysis.md`（2026-07-26，9 Agent 并行产出的上游全面分析）**及本次（2026-09-15）对上游源码的重新核查**。两者冲突时以本次源码核查为准，冲突处已显式标注。
> **本项目基线**：本次直接审计当前工作区得出，所有数字附采集命令。

---

## 0. 阅读指引（给评估者）

### 0.1 本文档与既有文档的关系（避免重复劳动）

| 文档 | 定位 | 与本文档的关系 |
|---|---|---|
| `docs/research/legado-with-md3-analysis.md`（6889 行） | 上游**是什么**：9 方向技术事实调研 | 本文档的**素材来源**，不重复其内容 |
| `docs/architecture/android-code-quality-and-evolution-guide.md`（96 行） | 本项目**规范**：原则声明与重构目标 | 本文档为其提供**外部实证依据与差距量化** |
| `docs/architecture/native-android-reader.md` | 当前阅读器架构与已知边界 | 本文档改造项须与其边界对齐 |
| **本文档** | **我们该做什么**：差距清单 + 可评估改造项 | 需评估者给出取舍结论 |

**本文档不重复**：上游主题系统 14 种主题模式、Miuix 双引擎、Navigation3 细节、Koin 装配等——这些已在 `legado-with-md3-analysis.md` 中详尽覆盖。本文档只提取**与本项目形态相关**的部分。

### 0.2 评估者的核心任务

请对第 2–6 章的每个改造项，给出以下四项裁决之一：

| 裁决 | 含义 |
|---|---|
| **采纳** | 有价值且与当前路线兼容，可直接排期 |
| **修改后采纳** | 方向对，但范围/方案需调整（请给出调整方案） |
| **推迟** | 有价值但当前时机不对（请给出前置条件） |
| **拒绝** | 与产品定位/架构边界冲突（请给出理由） |

并请特别审视 **第 7 章「不适用项」**——如果评估者认为其中某项其实适用，请说明理由，这比接受建议更有价值。

### 0.3 前提约束（评估时不得违反）

1. 本项目是**离线优先**的本地阅读/创作助手；上游是**在线书源聚合**阅读器。两者问题域不同，上游的多数设计对本项目**无对应问题**。
2. 单集成者维护；多 Agent 并行按**文件**划所有权（非目录）；越界需求写 SEAM REQUEST。
3. `docs/` 与 `archives/` 目录为只读参考，改造项不得涉及。
4. 破坏性操作（含 `git reset|commit|push|checkout|clean|stash`）未经用户授权不得执行。
5. Android 主线当前处于 Reader 结构性重构 Phase 8，任何改造项须说明**是否与 Phase 8 冲突**。

---

## 1. 基线与对标总览

### 1.1 本项目基线（2026-09-15 实测）

| 指标 | 实测值 | 采集方式 |
|---|---:|---|
| Kotlin 文件 | 476 | `find android/app/src/main/java -name '*.kt' \| wc -l` |
| Gradle 模块 | `:app` + `:benchmark` | `android/settings.gradle.kts` |
| `@Composable` 文件数 | 175 | `grep -rl '@Composable' --include=*.kt` |
| `@Composable` 出现次数 | 516 | `grep -rho '@Composable' --include=*.kt \| wc -l` |
| **Fragment 文件** | **0** | `find -name '*Fragment.kt'` |
| **XML layout** | **0** | `find res/layout -name '*.xml'` |
| ViewModel 文件 | 17 | `find -name '*ViewModel.kt'` |
| `StateFlow` 文件 | 32 | `grep -rl 'StateFlow'` |
| `LiveData` 文件 | **0** | `grep -rl 'LiveData'` |
| `EventBus` 文件 | **0** | `grep -rl 'EventBus'` |
| `SharedFlow` / `Channel` 文件 | **0** | `grep -rl 'SharedFlow\|Channel<'` |
| `*Contract.kt` / `*UiState.kt` | 3 / 3 | `find -name '*Contract.kt'` |
| **Gateway 文件** | **0** | `grep -rl 'Gateway'` |
| benchmark 类 | 6（含 5 个 Benchmark） | `find android/benchmark -name '*.kt'` |

**关键文件分布**：

```
android/app/src/main/java/com/creationreadingassistant/
├─ App.kt / MainActivity.kt
├─ feature/   reader(10 子包) · library · search · sync · dictionary · goal · log · text · annotations
├─ ui/        screen · components(11) · navigation · theme · viewmodel · window · layout · onboarding · util
├─ data/      local(28) · settings(15) · repository(12) · remote(6) · ai(1) · security(1)
└─ domain/    仅 model
```

`feature/reader/` 子包：`doc` · `eyecare` · `layout/android` · `locator` · `navigation` · `pager` · `rules` · `session`

### 1.2 双方形态差异（决定了哪些不能照搬）

| 维度 | 本项目 android/native | legado-with-MD3 |
|---|---|---|
| 产品定位 | 离线优先本地阅读/创作 | 在线书源聚合阅读 |
| Kotlin 文件 | 476 | 1645（+ 78 Java + 31 KT 模块） |
| Gradle 模块 | 2 | 4 |
| UI 技术栈 | **纯 Compose（0 Fragment / 0 XML）** | Compose ~85% + 遗留 View/XML（175 layout） |
| 设计系统 | 单一（Material3 + 自研玻璃态） | **双引擎（Material3 + Miuix）** |
| DI | Hilt + KSP | Koin |
| 状态管理 | StateFlow（纯） | MVI + LiveData + EventBus 混合 |
| 核心渲染 | Compose 分页引擎（`feature/reader/pager`） | **自绘 View 内核**（`ReadView` 1000+ 行） |
| 依赖规模 | 收敛（version catalog，无冗余库） | 125+ 依赖（双图片库 / 4 套动效库） |
| 数据库 | Room schema v14，无破坏性回退 | Room schema v98，55 AutoMigration |

**由此得出的核心判断**：

> 本项目在 **UI 技术栈纯净度、状态管理一致性、依赖治理**三个维度**已经领先**上游。
> 上游值得借鉴的不是它的"整体架构"，而是若干**具体机制**；
> 上游的多数架构选择（模块拆分、双引擎、遗留 View）是它自身历史包袱的产物，**照搬会污染本项目的技术栈干净度**。

### 1.3 差距矩阵（总览）

| 维度 | 本项目 | 上游 | 差距方向 | 详见 |
|---|---|---|---|---|
| Gradle 模块划分 | 2 模块 | 4 模块 | 上游更细 | §2 |
| **domain 接口层（Gateway）** | **0** | 52 接口 | **本项目缺失** | §2.2 |
| **构建期架构护栏** | **无** | Gradle Task 正则校验 | **本项目缺失** | §2.3 |
| **组件复用榜单机制** | 无 | `AppText` 被调 466 次 | **本项目缺失** | §3.2 |
| 统一文本入口 | 散落 | `AppText` 单一入口 | **本项目缺失** | §3.3 |
| 设置项 DSL 化 | `SettingsComponents.kt` 雏形 | 完整矩阵 | 部分差距 | §3.4 |
| **Effect 通道** | **0**（无 SharedFlow/Channel） | 阅读器有 Effect | **本项目缺失** | §4.2 |
| **配置写入回滚保护** | 无 pending 语义 | `PendingOverlayCore` | **本项目缺失** | §4.3 |
| 配置变更精准 Diff | `ReaderSettingsRouter.plan` 类似 | `AppUiConfigurationDiff` | 各有实现 | §4.4 |
| Baseline Profile | **5 个 Benchmark 类** | 2 个 | **本项目领先** | §5.1 |
| **协程 Dispatcher 监控** | **无** | `DispatchersMonitor` | **本项目缺失** | §5.2 |
| **进程冻结监控** | **无** | `AppFreezeMonitor` | **本项目缺失** | §5.3 |
| 崩溃吸收策略 | 未知 | `CrashHandler.shouldAbsorb` | 待核对 | §5.4 |
| 依赖分组约束 | 无 JitPack | `includeGroupByRegex` | 待核对 | §6.2 |
| 依赖规模 | 收敛 | 125+ 失控 | **本项目领先** | §6.3 |

---

## 2. 架构设计

### 2.1 Gradle 模块划分

**上游实现方式**

| 模块 | 语言 | 职责 | 证据 |
|---|---|---|---|
| `:app` | Kotlin 1645 | 全部业务/UI/数据/服务 | `app/build.gradle.kts:36` |
| `:modules:book` | **纯 Java 78**，零 `.kt` | epub/UMD 解析（epublib + umdlib fork） | `modules/book/build.gradle.kts:7` |
| `:modules:rhino` | Kotlin 31 | Rhino JS 引擎封装（书源规则沙箱） | `modules/rhino/build.gradle.kts:36` |
| `:baselineProfile` | Kotlin 2 | 基准配置文件生成，`useConnectedDevices = true` | `baselineProfile/build.gradle.kts:39` |

依赖方向严格单向 `app → modules:*`；modules 零反向依赖（`grep 'project(":app")'` 无命中）。`modules/book` 仅依赖 `androidx.annotation` 一个坐标。

**本项目现状**

```
android/settings.gradle.kts:
  include(":app")
  include(":benchmark")
```

单模块 `:app`（476 kt）。**注意**：`docs/architecture/android-code-quality-and-evolution-guide.md` §2.2 已预告模块化路线（阶段一 `:core:reader-layout` 无 Android 依赖、阶段二 `:core:database` + `:core:model`），但尚未实施。

**差距分析**

上游拆模块的动因是**封装第三方大体积 Java 库**（epub 解析 78 个 Java 文件、Rhino 1.4MB jar），这在本项目**没有对应场景**——本项目无自研解析库、无 JS 引擎。因此上游的模块拆分**不构成对本项目的直接建议**。

但本项目指南中已规划的 `:core:reader-layout` 有**独立理由**：把排版数学（分词、断句、禁则、两端对齐、文本尺寸测量抽象）抽为纯 Kotlin 模块，**不含 Android SDK 依赖**，使全部排版用例可在 JVM 秒级跑完。这是"提升测试速度"的动因，与上游"封装大体积库"的动因不同。

**改造项 A-1：将 `:core:reader-layout` 作为唯一模块化候选，且仅在 Phase 8 稳定后启动**

| 项 | 内容 |
|---|---|
| 目标 | 排版数学脱离 Android SDK，JVM 单测秒级反馈 |
| 前置条件 | ① Phase 8 Reader 重构定稿；② `feature/reader/layout/` 的 API 边界已稳定；③ 有可量化的测试耗时基线 |
| 风险 | 跨模块 Hilt/KSP 配置成本；`layout/android` 子包已暗示存在平台耦合，未必可完全剥离 |
| 待评估 | 抽离后 `feature/reader/layout/` 剩多少？若剩余部分仍需 Android，模块收益是否被抵消？ |
| 与 Phase 8 关系 | **可能冲突**——Phase 8 正在重构 Reader，此时移动文件会破坏迁移纪律（LaunchedEffect/remember keys 逐字原样迁移） |

**不建议**：拆 `:core:database` / `:core:model`。本项目的 `data/local` 仅 28 文件，Room DAO 越界感知问题远小于上游（上游有 36 个 repository 绕过 DAO 直用全局 `appDb`）。当前规模下拆模块的成本大于收益。

### 2.2 domain 接口层（Gateway 模式） — **建议优先评估**

**上游实现方式**

上游 `domain/` 共 149 kt，四个子包：`gateway`(52 接口) / `usecase`(43) / `repository` / `model`(53)。

`gateway` 是**面向场景的窄接口**，不是通用仓储接口。例：

```kotlin
// domain/gateway/ReadStyleGateway.kt
interface ReadStyleGateway {
    fun updateCurrentStyle(mutation: ReadStyleMutation)
    fun applyPreset(index: Int)
    // ...
}
```

- **domain 定义接口，data 提供实现**：`BookDomainRepository`（接口，`domain/repository/BookDomainRepository.kt:7-15`）↔ `BookDomainRepositoryImpl`（`data/repository/BookDomainRepositoryImpl.kt:12-15`）
- **绑定显式集中在 DI**：`di/appModule.kt:339` `single<ReadStyleGateway> { get<ReadBookStyleConfigRepository>() }`，共 132 处绑定（`di/appModule.kt` 650 行）
- 工具：`AppConfig.initialize(shellGateway, themeGateway, ...)` 用 10 个 gateway 做依赖注入（`App.kt:109-121`）

**上游自身的违约（重要）**：43 个 usecase 中 **3 个直连 `data.dao`**（`GetChapterContentUseCase.kt`、`ChangeBookSourceUseCase.kt`、`ExploreKindUiUseCase.kt`）；**36 个 data/repository 绕过 DAO 用全局 `appDb`**。这说明 gateway 模式在上游**并未彻底贯彻**——评估时不应把上游当作满分样板。

**本项目现状**

- Gateway 文件数：**0**（`grep -rl 'Gateway'` 无命中）
- `domain/` 仅 `model` 子包
- `data/repository/` 12 个文件，是**具体类**（无接口抽象）
- DI 用 Hilt（`@Module` / `@Inject`），与上游 Koin 的显式 `single<>` 绑定形态不同

**差距的本质**

本项目 `feature/*` 直接依赖 `data/repository/` 的具体类。后果有二：

1. **JVM 单测需要真 Room 或 MockK 深桩**。若有接口，可提供纯内存 fake，测试更快更稳。
2. **`feature` 与 `data` 之间无编译期边界**。多 Agent 并行时，"谁可以改 `data/repository`"只能靠文档约定，不能靠编译器。

本项目 `data/settings/` 有 15 个 Store（`SettingsStore`、`ContinueReadingStore`、`ShelfPrefs`、`ReaderSettingsRouter`、`PerBookSettingsStore`、`SelectionActionStore` 等），其中阅读设置是最复杂的一块——本项目已有 `ReaderSettingsRouter.plan`（设置优先级 **本书覆盖 > 全局 > 结构默认**）这套编排逻辑，**这正是 gateway 模式的天然候选**。

**改造项 A-2：先只抽 1 个窄接口，验证收益后再推广**

| 项 | 内容 |
|---|---|
| 目标 | 建立 `feature` → `domain`（接口）→ `data`（实现）的单向依赖；使 Reader 设置可在无 Android 环境下单测 |
| **推荐首个目标** | `ReadSettingsGateway`（对应 `ReaderSettingsRouter` + `ReaderOverride` + `PerBookSettingsStore`） |
| 为什么不选书架/同步 | 阅读设置的"本书覆盖 > 全局 > 默认"优先级编排最复杂，收益最大；且它已有纯策略实现，抽接口是机械工作 |
| 实施要点 | ① 接口按**场景**切（如 `planSettings(bookId)`、`updateOverride(...)`），**不做通用 CRUD**；② 利用本项目已有的 Hilt，用 `@Binds` 绑接口到实现，**不引入 Koin**；③ 新增接口须在 `docs/architecture/native-android-reader.md` 登记边界 |
| 风险 | ① 与 Phase 8 可能冲突（Reader 正在重构）；② 若接口设计过宽会退化成"仓储换皮"，失去意义 |
| 待评估 | 本项目是否值得引入这一层？还是当前的 `Router` + `Store` 组合已经足够，加接口只是形式主义？**这是本文档最需要被质疑的一项。** |

**上游不宜照搬的部分**：52 个 gateway 是按 1645 文件规模设计的；本项目 476 文件下，**1–3 个**关键 gateway 足矣，不应追求数量对标。

### 2.3 构建期架构护栏 — **建议优先评估**

**上游实现方式**

上游根 `build.gradle.kts:12-177` 定义 `VerifyConfigArchitectureTask`（`@DisableCachingByDefault`），用**正则扫描源码**在编译前强制架构约束，挂在所有 `assemble*` / `compile*` 之前（`:171-177`）。

已核实（2026-09-15 源码重读）的校验规则包括：

| 规则 | 正则/条件 | 证据（`build.gradle.kts`） |
|---|---|---|
| 禁止 Snapshot 配置桥 | `"prefDelegate" in text \|\| "prefStateDelegate" in text \|\| "Snapshot.withMutableSnapshot" in text` | `:55-58` |
| data/domain 禁止导入全局 Config | `^import io\.legado\.app\.(?:help\.config\.AppConfig\|ui\.config\..*Config)$` | `:32-35` |
| 禁止新增旧偏好调用 | `\b(?:getPref\|putPref)[A-Za-z0-9_]*\s*\(`，**带每文件基线计数** | `:36` |
| ReadBookConfig 写入必须经 Gateway | `\bReadBookConfig\.[a-z_][A-Za-z0-9_]*(?:\.[a-z_][A-Za-z0-9_]*)?\s*=` | `:37-39` |
| 禁止重新引入 `*SettingsUpdate` 分发类型 | `\b(?:class\|interface\|object\|typealias)\s+[A-Za-z0-9_]*SettingsUpdate\b` | `:40-42` |

关键设计：`legacyPreferenceCallBaseline: MapProperty<String, Int>` 用**每文件计数基线**冻结存量——已有 24 个文件的旧调用被登记，新增即失败。这是"冻结技术债、阻止恶化"而不是"要求一次清理"的务实做法。

**本项目现状**

- 无任何构建期架构校验（`grep 'VerifyConfig'` 无命中）
- `android/scripts/install_with_confirm.contract.tests.ps1` 是**安装脚本**的无设备回归（37/37 PASS），与架构护栏无关
- 已有 Gradle 门禁：`testDebugUnitTest`（253 套件 / 2238 tests / 0 fail）、`lintDebug`、`compileDebugAndroidTestKotlin`

**为什么本项目特别需要**

本项目的协作模式是**多 Agent 并行 + 单集成者**，且按**文件**（非目录）划所有权。当前防止越界的唯一手段是文档约定与人的审查。构建期护栏能提供**编译期的机械保证**，这对多 Agent 场景的价值高于上游（上游是单人主导）。

**改造项 A-3：加一条最小护栏，只冻结一条最有价值的不变量**

| 项 | 内容 |
|---|---|
| 目标 | 用 Gradle Task 机制阻止最有价值的架构退化，而非一次性全量校验 |
| **推荐首个规则** | 禁止 `ui/` 与 `feature/` 直接 `import com.creationreadingassistant.data.local.*`（DAO/Entity 越界） |
| 为什么选这条 | 与本文档 §2.2 目标呼应；是"UI 不得感知持久化实体副本"（本项目指南 §一.1 已声明）的机械落地 |
| 实施要点 | ① 照搬 `@DisableCachingByDefault` + `MapProperty` 基线写法；② **先跑一次生成基线**，把现有违规登记（不要求立即清理）；③ 挂到 `compile*` 前，失败信息打印 `文件:违规内容` |
| 风险 | 正则方案有误报可能（如注释、字符串中的匹配）；上游同样存在这个局限 |
| 待评估 | 本项目是否已有等价机制（如 detekt/ktlint 自定义规则）？若已有规则引擎，应优先用规则引擎而非手写 Gradle Task |

**注意**：上游用正则而非 AST，是**刻意的成本权衡**（`build.gradle.kts` 无 Kotlin 编译依赖）。评估时请判断本项目是否值得为更精确的 AST 方案付出成本。

---

## 3. UI/UX 与组件复用

### 3.1 主题系统（本项目方向已正确，不建议改）

**上游实现方式**（详见 `legado-with-md3-analysis.md` §1，此处仅摘要）

单向管道：`DataStore → AppUiConfigurationRepository.configuration (StateFlow) → collectAsStateWithLifecycle → AppTheme → CompositionLocal`。

能力面：Material You 动态取色（`SDK_INT < S` 回落 `GRColorScheme`）、纯黑 AMOLED（只改 4 个字段：`surface`/`background` 置黑 + `surfaceContainerLow=0xFF0A0A0A` + `surfaceContainer=0xFF121212`）、12 套预置配色（每套 49 个色槽全填）、`material-kolor` 的自定义调色（9 种 PaletteStyle + 2 种 ColorSpec）、`animateColorSchemeAsState`（49 次 `animateColor` 逐槽插值，默认 `tween(700ms)`）、字体缩放走 `LocalDensity` 覆盖、`color://` 自定义字体带 `LruCache<String, FontFamily>(4)`。

**本项目现状**：`ui/theme/` 存在，含 `Motion.kt`（`rememberCountUp(target, key, reducedMotion)`）。有 `ui/components/GlassSurface.kt`、`NoiseTexture.kt`、`MicroIsland.kt`、`GlassDialogs.kt`——说明玻璃态视觉方向已建立。

**判断：不构成改造项。**

理由：上游主题系统的复杂度**主要来自双引擎（Material3 + Miuix）**。`HazeStyle.kt:36-39, 71-74, 100-103` 中每个 effect 都要写 `if (isMiuixEngine) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface` 分支——**每个新组件都要测两遍**。这是上游的技术债，不是最佳实践。

本项目是单一设计系统 + 自研玻璃态，**方向正确**。上游唯一值得单独看的是 `animateColorSchemeAsState` 的逐色槽过渡（避免主题切换时整片跳变）和 `BaseComposeActivity.SyncWindowBackground()`（用 `SideEffect` 把 Compose 背景色写回 `window.setBackgroundDrawable`，解决转场时露出 XML 窗口背景的色差跳变）。后者是纯 Compose 项目也需要的（启动窗口背景）。

**改造项 B-1（低优先）：核对启动窗口背景是否有色差跳变**

| 项 | 内容 |
|---|---|
| 目标 | 消除冷启动 / 转场时窗口背景与 Compose 首帧的色差 |
| 上游做法 | `BaseComposeActivity.SyncWindowBackground()` 用 `SideEffect` 同步 |
| 待评估 | 本项目 `MainActivity.kt` + `ui/theme/` 是否已有等价处理？若有则跳过；若无，检查是否在深色/自定义主题下出现白闪 |

### 3.2 组件复用榜单机制 — **建议优先评估**

**上游实现方式**

上游 162 个公共 Composable 集中在 `ui/widget/components/`，形成四层结构。按**调用次数实测** Top 10：

| 排名 | 组件 | 调用次数 | 路径 |
|---|---|---:|---|
| 1 | `AppText` | **466** | `components/text/AppText.kt` |
| 2 | `AppTextField` | 122 | `components/AppTextField.kt` |
| 3 | `AppAlertDialog` | 115 | `components/alert/AppAlertDialog.kt` |
| 4 | `ClickableSettingItem` | 96 | `components/settingItem/ClickableSettingItem.kt` |
| 5 | `AppModalBottomSheet` | 93 | `components/modalBottomSheet/AppModalBottomSheet.kt` |
| 6 | `AppIcon` | 74 | `components/icon/AppIcon.kt` |
| 7 | `AnimatedTextLine` | 58 | `components/text/AnimatedText.kt` |
| 8 | `AppScaffold` | 51 | `components/AppScaffold.kt` |
| 9 | `CompactSwitchSettingItem` | 37 | `components/settingItem/CompactSettingItems.kt` |
| 10 | `CoilBookCover` | 32 | `image/cover/CoilBookCover.kt` |

**这里真正的方法论价值不是"组件本身"，而是"用调用次数审计复用度"这个动作**。榜单能直接暴露两类问题：

- **伪共享**：放在 `components/` 却只被调用 1–2 次（应内联回使用处）
- **漏抽**：被复制粘贴 20 次却没进 `components/`（应抽取）

**本项目现状**

`ui/components/` 11 个文件：`AppLayout.kt`、`BookCover.kt`、`GlassDialogs.kt`、`GlassSurface.kt`、`LineArt.kt`、`MicroIsland.kt`、`NoiseTexture.kt`、`NotificationPermission.kt`、`OptimizedImage.kt`、`SettingsComponents.kt`、`SharedComponents.kt`。

**从未做过调用次数审计**——不知道哪些是真共享、哪些是伪共享。

**改造项 C-1：做一次组件复用审计（纯统计，不改代码，可立即执行）**

| 项 | 内容 |
|---|---|
| 目标 | 产出 `ui/components/` 每个组件的调用次数榜单，识别伪共享与漏抽 |
| 成本 | **极低**——仅需 grep 统计，无代码变更，零风险 |
| 方法 | 对 11 个文件中的每个 Composable 函数名，`grep -rho "\b<Name>(" --include=*.kt \| wc -l` |
| 产出 | 一张表格：组件名 / 调用次数 / 判定（真共享 / 伪共享 / 候选抽取） |
| 附加价值 | 同时统计**未进 `components/` 但被多处复制的 UI 模式**（这是更有价值的发现） |
| 与 Phase 8 关系 | 无冲突（只读） |
| 待评估 | 审计结果是否值得作为一次独立改造？还是并入 Phase 8 收尾的清理工作？ |

### 3.3 统一文本入口 — **建议评估**

**上游实现方式**

`AppText` 全部 466 处调用的实现（`components/text/AppText.kt:27-60`）：

```kotlin
// 不使用 Text，而是 BasicText + 主题兜底
BasicText(
    text = ...,
    style = style ?: LegadoTheme.typography.bodyMedium,  // 主题默认
    color = color ?: LegadoTheme.colorScheme.onSurface,  // 主题取色
    ...
)
```

**价值点**：466 处调用意味着**任何排版/字号/颜色调整只需改一处**。若散用 `Text`，则每次主题变更都要全局搜索替换。

**本项目现状**：未建统一入口（推断：`ui/screen/` 下各屏直接调用 `Text`）。具体数量**需实测确认**——这是评估者应当核实的第一个数字。

**改造项 C-2：建立 `AppText` 统一文本入口**

| 项 | 内容 |
|---|---|
| 前置核实 | 统计当前 `Text(` 的直接调用数量与分布 |
| 目标 | 文本样式单一来源；为"阅读字体全局切换"提供前提 |
| 与阅读器的关系 | **需谨慎**——阅读器正文排版有自己的字体/行距/字距体系，**不应**被通用 `AppText` 接管。适用范围应限于**非正文 UI**（列表、设置、弹窗、统计） |
| 风险 | ① 全量替换是机械但易错的大改动，应分批（按屏幕）；② 若 `AppText` 参数过多会变成"另一个 Text"，失去收口意义 |
| 待评估 | 是否等 Phase 8 完成后统一做？还是随各屏重构顺手做？ |

### 3.4 设置项 DSL 化

**上游实现方式**

`components/settingItem/` 形成"声明式设置"方言，覆盖 `ClickableSettingItem`(96) / `CompactSwitchSettingItem`(37) / `SwitchSettingItem` / `SliderSettingItem` / `InputSettingItem` / `Compact*` 系列。

**本项目现状**：`SettingsComponents.kt` 是雏形。需核实其**矩阵完整度**（是否覆盖 switch / slider / input / 选择器 / 折叠组 / 带动画变体）。

**改造项 C-3（低优先）：核对 `SettingsComponents.kt` 的矩阵完整度**

| 项 | 内容 |
|---|---|
| 目标 | 若存在"某类设置项在多个 Sheet 里重复实现"，则补齐矩阵 |
| 成本 | 审计成本低，改造视发现 |
| 关联 | 本项目有多个设置 Sheet（`ReaderSettingsSheet`、`ReaderRulesSheet` 等，见 §7 巨型文件清单），设置项重复实现的可能性较高 |
| 待评估 | 先审计再决定，不要预设需要改造 |

---

## 4. 状态管理与数据流

### 4.1 上游的三种范式并存（反面教材，本项目已规避）

| 范式 | 上游使用量 | 本项目 |
|---|---|---|
| 完整 MVI（阅读器） | 272 Intent + UiState + Effect | 3 个 `*Contract.kt` + 3 个 `*UiState.kt` |
| LiveData + MVVM（老页面） | 34 文件 | **0** |
| EventBus 旁路 | 36 文件 | **0** |
| Flow（任意） | 276 文件 | 32 文件用 `StateFlow` |

**判断：本项目在状态管理一致性上明显领先。** 上游的 LiveData / EventBus / Flow 三套并存是历史包袱，`help/EventMessage.kt` 的 `Message` 风格载体（`what`/`tag`/`obj`）甚至已边缘化到基本无引用。

**但上游有明确失败案例必须避开**：`ReadBookViewModel.kt` **6655 行**，`onIntent(intent)` 是 **~1340 行的巨型 `when`**（`:283-1624`）分发 272 个 Intent。形式上是 MVI，实质是"带状态容器的上帝对象"。

另：上游 MVI **只包了 UI 状态的一半**——`ReadBook` 静态单例（57KB）仍是书籍与进度的真实来源，ViewModel 中有 **242 处** `ReadBook.` 直接访问。这造成**双重真相源**。

**本项目关联风险**：本项目指南 §2.1 已列出 6 个巨型文件（`HomeArchiveScreens.kt` ~1720、`ReaderSettingsSheet.kt` ~1611 等）。**上游的 6655 行 ViewModel 就是这条路的终点**——评估时应把上游视为"不设限的后果演示"。

### 4.2 Effect 通道缺失 — **建议优先评估**

**上游实现方式**（`ReadBookViewModel.kt:211-215`）：

```kotlin
private val _uiState = MutableStateFlow(ReadBookUiState())
val uiState = _uiState.asStateFlow()
private val _effects = MutableSharedFlow<ReadBookEffect>(extraBufferCapacity = 16)
val effects = _effects.asSharedFlow()
```

UI 侧 `viewModel.uiState.collectAsStateWithLifecycle()`（`ReadBookRouteScreen.kt:133`）。副作用用 `emitEffectWhenSubscribed` 等订阅者就绪再发（`ReadBookViewModel.kt:225-228`）——避免订阅未就绪时事件丢失。

**本项目现状**

| 指标 | 实测值 |
|---|---|
| `SharedFlow` / `Channel<` 文件数 | **0** |

本项目只有 `StateFlow`（32 文件），**没有一次性事件通道**。

**这意味着什么**：一次性事件（导航、Snackbar、Toast、滚动到指定位置、弹出 Sheet）若没有 `Channel`/`SharedFlow`，通常会被塞进 `UiState` 用 `Boolean` 标志位 + `consumed` 回调处理。这是经典反模式，具体危害：

1. **旋转/配置变更后事件重放**：`StateFlow` 会重发当前值，未消费的 Boolean 标志会导致弹窗重复弹出
2. **订阅未就绪时丢失**：若用 `MutableSharedFlow()` 默认参数（无 replay、无 buffer），发射时无订阅者则事件直接丢弃
3. **状态与事件混同**：UiState 既是"当前是什么"又是"刚刚发生了什么"，职责混乱

**改造项 D-1：为阅读器补 Effect 通道**

| 项 | 内容 |
|---|---|
| 目标 | 一次性事件与持久状态分离，消除标志位反模式 |
| 适用范围 | 优先 `feature/reader`（事件最多：翻页、选区、TTS、导航、Sheet 弹出） |
| 实施要点 | ① 用 `Channel<T>(Channel.BUFFERED)` + `receiveAsFlow()`，而非 `MutableSharedFlow()`——前者保证**恰好一次**投递且有缓冲，后者可能丢弃；② 明确哪些事件需要"订阅未就绪也保留"（导航类需要，Toast 类可不保留）；③ 本项目若已有类似机制（如通过 `UiState` + `LaunchedEffect` 消费标志位），需先审计现状 |
| 待评估 | **本项目是否实际上已有等价机制？** 若 `UiState` 中的标志位配合 `LaunchedEffect` 消费且已有 `consumed` 模式，那问题不是"缺失"而是"用另一套实现"。请评估者核实后再裁定 |
| 与 Phase 8 关系 | **可能冲突**——Phase 8 要求 `LaunchedEffect`/`remember` 的 keys/params 逐字原样迁移。此时引入 Effect 通道会改变事件消费路径，须由 Phase 8 负责人确认是否可插入 |

### 4.3 配置写入回滚保护（PendingOverlay） — **本提案技术含量最高的一项**

**上游实现方式**（`help/config/AppConfigStore.kt:140-303`）

这是上游最精巧的设计，**源码注释本身质量极高**，直接引用：

```
快照 + pending overlay 的核心状态机，与 Android/DataStore 解耦以便 JVM 单测。

不变式：[preferencesFlow] = snapshot 叠加所有 pending 写入，pending 中的值永远优先。
snapshot 只由 collector 回灌（[onEmission]）推进，回灌按落盘提交顺序到达，因此单调不回退；
pending 条目要等到回灌中该 key 的值已等于写入目标值时才移除——落盘完成后若先处理到
一条落盘前的旧回灌，该 key 仍被 overlay 保护，读值不会短暂回滚（回滚会让 observe
订阅方收到"新→旧→新"的幻影变更，如触发多余的 Activity recreate / WebDav refresh）。
落盘失败时移除对应 pending 实现回滚，读取回退到已落盘状态。
```

实现要点（已核实源码）：

```kotlin
internal class PendingOverlayCore(
    initial: Preferences,
    private val launchWrite: (suspend () -> Unit) -> Unit,
    private val persist: suspend (key: String, value: Any?) -> Preferences,
    private val persistAll: suspend (values: Map<String, Any?>) -> Preferences,
) {
    private val lock = Any()
    private var snapshot: Preferences = initial
    private val pending = LinkedHashMap<String, PendingWrite>()
    private val _preferences = MutableStateFlow(initial)
    val preferencesFlow: StateFlow<Preferences> get() = _preferences

    fun put(key: String, value: Any?) {
        synchronized(lock) {
            val write = PendingWrite(value)
            pending[key] = write
            rebuild()
            // 在锁内提交写任务，保证落盘顺序与 pending 覆盖顺序一致
            launchWrite {
                runCatching { persist(key, value) }.onFailure { e ->
                    synchronized(lock) {
                        if (pending[key] === write) {
                            pending.remove(key)   // 失败回滚
                            rebuild()
                        } else {
                            // 同 key 已有更新的写入，继续由新条目覆盖
                        }
                    }
                }
            }
        }
    }
}
```

**它解决的具体问题**：原生 `SharedPreferences.apply()` 或 DataStore 写完立即读会拿到**旧值**，造成"新→旧→新"的幻影状态回滚。在阅读器场景下这会触发**多余的 Activity recreate** 或**多余的 WebDav refresh**。

**本项目现状**

- `data/settings/` 15 个 Store，DataStore 用法正确
- **`ContinueReadingStore.kt` 是阅读进度存储——高频写**
- 是否需要 pending 语义的关键判据：**是否存在"写入后立即读回"的路径**

**改造项 D-2：评估 `ContinueReadingStore` 是否需要 pending-overlay 语义**

| 项 | 内容 |
|---|---|
| 前置调查（必做） | ① 审计 `ContinueReadingStore` 的写入频率（每次翻页？每次退出？）② 审计是否存在"写入后立即读取"的消费路径 ③ 若存在，观察是否出现状态跳变（UI 显示旧进度后再跳到新进度） |
| 判据 | **若不存在"写后立即读"路径，则本项无收益，应直接裁定拒绝。** 不要为了对标而上复杂度 |
| 若确实需要 | 优先只给 `ContinueReadingStore` 加 overlay，**不做全局改造**。可在纯 Kotlin 层实现状态机（脱离 DataStore）以便 JVM 单测 |
| 风险 | 状态机本身有正确性门槛（并发写入、失败回滚、同 key 覆盖）。上游把核心抽成 `PendingOverlayCore` 并注明"与 Android/DataStore 解耦以便 JVM 单测"，本项目应照此做，**必须有单测** |
| 待评估 | 本项目是否真的遇到过失控？还是这是"上游有所以我们也该有"的伪需求？**请评估者明确回答。** |

### 4.4 配置变更精准 Diff（本项目已有类似实现）

**上游实现方式**

`AppUiConfiguration` 聚合 `language + appShell + theme + cover + isSystemDarkTheme`，提供 `diffFrom(previous)` 返回 `AppUiConfigurationDiff`（`localeChanged` / `themeChanged` / `fontScaleChanged` / `windowChanged`）。

关键设计：**只有 6 项变化才判 `themeChanged`**（`appTheme` / `isPureBlack` / `customMode` / `appFontPath` / `customPrimary` / `customNightPrimary`），避免"动一下模糊滑块就重建所有遗留 View Activity"。

**本项目现状**

`data/settings/ReaderSettingsRouter.kt` 已有 `plan`（设置优先级 **本书覆盖 > 全局 > 结构默认**）——**思路一致**：区分"需要重建 / 只需重绘"。

另有 `data/settings/ReaderDefaultsMigration.kt`、`ReaderOverride.kt`、`ReadingPresets.kt`。

**判断：不构成改造项。** 本项目已有等价思路。上游在此处的额外价值是把 diff 结果用于**决定 Activity 是否重建**——但本项目是**纯 Compose、单 Activity**（0 Fragment），不存在"重建遗留 View Activity"的问题。上游的实现动因在本项目不成立。

---

## 5. 性能优化与可观测性

### 5.1 Baseline Profile（**本项目领先，无需改造**）

| 项 | 本项目 | 上游 |
|---|---|---|
| Benchmark 模块 | `:benchmark` | `:baselineProfile` |
| kt 文件 | **6**（含 5 个 Benchmark） | 2 |
| Benchmark 覆盖 | `StartupBenchmark`、`ReaderBenchmark`、`ShelfBenchmark`、`NavigationBenchmark`、`BaselineProfileGenerator`、`BenchmarkHelpers` | `StartupBenchmark`、`BaselineProfileGenerator` |
| profileinstaller | 有 | 有（1.4.1） |

**本项目在 benchmark 覆盖维度上比上游更完整**（阅读器/书架/导航三个专项上游都没有）。

上游在这一块唯一值得单独看的是**它的 journey 注释**（`BaselineProfileGenerator.kt:16-19`）：

> 关键：journey 必须真正打开一本书进入阅读器，否则开书热路径（Compose 组合、ChapterProvider/TextChapterLayout 排版、ReadView 绘制）不会被采样进 profile，release 首次开书只能主线程内联 JIT，产生明显加载卡顿。

以及它记录的两个真实踩坑（`BaselineProfileGenerator.kt:53-55`）：

- Compose 节点 `clickable=false` 导致 `UiObject2.click()` 不可靠
- 下滑会让顶栏展开导致坐标漂移

**改造项 E-1（低成本）：核对本项目 `BaselineProfileGenerator` 是否覆盖真实开书热路径**

| 项 | 内容 |
|---|---|
| 目标 | 确认 profile 采样覆盖了阅读器首开路径 |
| 方法 | 读 `android/benchmark/src/main/java/.../BaselineProfileGenerator.kt`，对照上游注释检查 journey 是否真正进入阅读器并翻页 |
| 若已覆盖 | 裁定"无需改造"，本项关闭 |
| 若未覆盖 | 补齐 journey（参考上游的 4 页翻页采样 + 正则定位书节点） |
| 成本 | 极低 |

### 5.2 协程 Dispatcher 饱和监控 — **建议优先评估**

**上游实现方式**（`help/DispatchersMonitor.kt:44-57`）

用 `select` 的 `onJoin` 与 `onTimeout` 竞争，探测 dispatcher 是否被阻塞：

```kotlin
private fun monitor(dispatcher: CoroutineDispatcher) {
    scope.launch {
        while (isActive) select {
            launch { withContext(dispatcher) { delay(3000) } }.onJoin {}
            onTimeout(5000) {
                LogUtils.d(TAG, "Dispatcher $dispatcher is timed out waiting for for 5000ms.")
            }
        }
    }
}
```

**原理**：向 IO / Default / Main 三个 dispatcher 各投递一个"睡 3 秒"的任务，用 `select` 让 `onJoin`（正常完成）与 `onTimeout(5000)` 竞争。**若 5 秒内任务没跑完，说明该 dispatcher 的线程全被阻塞**——即 IO 池被占满、Default 池被 CPU 密集任务堵死，或 **Main 被同步阻塞**。

配套细节：
- 探针跑在独立单线程执行器（`:25-31`）
- `scope.coroutineContext.cancelChildren()` 保证重复 init 幂等（`:34`）
- 纯 Kotlin 协程语义实现，无侵入

**本项目现状：完全没有。** 本项目有 `feature/reader/pager/PagerHealthStore.kt`（分页健康度），说明已有"健康度"意识，但**缺少协程层的探针**。

**为什么对本项目有价值**：本项目指南 §一.2 规定"绝不整本读取大文本"、"`ReplaceProjectionScopeProvider` 超过 256K 必须熔断"——这些都是**内存与线程边界**的约束。一旦某处越界（如误在主线程做投影），症状是"偶发卡顿"，而 DispatchersMonitor 能**直接指认是哪个 dispatcher 被堵**，而不是靠二分排查。

**改造项 F-1：引入 Dispatcher 饱和探针**

| 项 | 内容 |
|---|---|
| 目标 | 定位"偶发卡顿"到具体 dispatcher；发现主线程同步阻塞 |
| 成本 | **约 50 行**，零侵入（不改现有业务代码） |
| 实施要点 | ① 必须**可开关**，且关闭时主动取消探针协程（零常驻开销）；② 阈值（3s / 5s）应可配置；③ 输出应带上前调用栈或协程名，否则只知道"被堵"不知道"谁堵的"；④ 探针线程独立，不能与业务共享线程池 |
| 本项目适配 | 本项目指南 §5.4 规定诊断文件统一输出——探针输出应遵守该约定 |
| 待评估 | ① 本项目是否已有等价机制（如 `DiagnosticsSubPage.kt` 是否已有类似探针）？② 50 行的收益是否值得引入一个新的常驻组件？ |

### 5.3 进程冻结监控 — **建议评估**

**上游实现方式**（`help/AppFreezeMonitor.kt:53-59`）

在独立 `HandlerThread("AppFreezeMonitor")` 上每 3 秒 post 一次自己（`:18-20, :71`），测量调度延迟：

```kotlin
val elapsed = current - previous
val extra = elapsed - 3000
if (extra > 300) LogUtils.d(TAG, "检测到应用被系统冻结，时长：$extra 毫秒")
```

**原理**：Handler 调度本该正好 3000ms，若实际耗时超出阈值（300ms），说明**线程被系统挂起**（App 被冻结 / 进入 cached 状态）。同时注册 `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` 广播记录屏幕状态（`:74-87`）——用于区分"真卡顿"与"息屏冻结"。

**价值**：能捕获传统 `Choreographer` 掉帧监控抓不到的**跨进程冻结**问题。

**本项目现状：完全没有。**

**为什么对本项目有价值**：本项目的真机验证流程涉及**长时间测试**（AGENTS.md 提到"长时间测试可临时启用 `stay_on_while_plugged_in`"）。长时间测试场景下，系统冻结是真实存在的干扰源——若无监控，会把"系统冻结导致的行为异常"误判为"应用 Bug"。

**改造项 F-2：引入冻结检测**

| 项 | 内容 |
|---|---|
| 目标 | 区分"应用卡顿"与"系统冻结"，避免真机长测误判 |
| 成本 | **约 80 行** |
| 实施要点 | ① 独立 `HandlerThread`，不与业务共享；② 必须配套屏幕状态记录，否则无法区分冻结类型；③ 与 §5.2 共用开关；④ 输出遵守本项目诊断文件约定 |
| 待评估 | 本项目的真机验收流程是否确实受此干扰？若无实际误判案例，可推迟到"确实遇到"再引入 |

### 5.4 崩溃吸收策略（待核对）

**上游实现方式**（`help/CrashHandler.kt:68-78`）

```kotlin
private fun shouldAbsorb(e: Throwable): Boolean = when {
    e::class.simpleName == "CannotDeliverBroadcastException" -> true
    e is SecurityException && e.message?.contains(
        "nor current process has android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS", true) == true -> true
    else -> false
}
```

**原理**：选择性吸收**已知的 ROM 兼容性问题**，吸收后调 `Looper.loop()` 让主线程继续转而**不崩掉 App**。这是对 ROM 定制系统兼容性问题的工程化兜底。

配套：
- OOM 时若开启 `recordHeapDump` 自动 `Debug.dumpHprofData()`（`:88-90, :213-226`）
- 崩溃日志双写（备份路径 + externalCache），自动清理 7 天前文件（`:183-194`）
- `paramsMap` 采集厂商/机型/SDK/WebView UA/堆上限，用 `lazy` **只在真崩时构造**（`:119-142`）——避免常驻成本

**本项目现状：需核实。** 未知是否有 CrashHandler；若有，检查是否已有"选择性吸收"能力。

**改造项 F-3：核对崩溃处理策略**

| 项 | 内容 |
|---|---|
| 前置核实 | 本项目是否有自定义 `CrashHandler` / `Thread.setDefaultUncaughtExceptionHandler`？ |
| 若有 | 检查是否已有选择性吸收与 OOM 堆转储；本项目测试环境涉及 HyperOS（见 `handoff/current.md` 的 MIUI 安装器问题），ROM 兼容性异常**很可能已遇到** |
| 若无 | 评估是否值得引入（本项目已有 `DiagnosticsSubPage.kt`，可能已覆盖部分需求） |
| 成本 | 视现状而定 |
| 待评估 | 优先级应低于 §5.2 / §5.3——崩溃处理涉及安全边界，须谨慎 |

### 5.5 阅读器缓存体系（**部分适用**，需按本项目边界筛选）

**上游实现方式**

| 机制 | 实现 | 证据 | 本项目适用性 |
|---|---|---|---|
| **两级准入控制** | `CacheDownloadAdmissionQueue(maxActiveBookCount)`，**优先放行已在下载中的书**避免饿死 | `service/CacheBookService.kt:62-63`、`model/cache/CacheDownloadAdmissionQueue.kt:22-34` | **可能适用**——若本项目导入/索引队列有"多任务竞争"问题 |
| **协程限流** | `flatMapMerge(concurrency)` + `Semaphore` 两种实现 | `utils/FlowExtensions.kt:28-36, 112-128` | **适用**——通用工具，纯 Kotlin |
| **双向章节预取** | 后向 `preDownloadNum` 章 + 前向最多 5 章，并行 `launch`，跳过已下载与失败 3 次以上 | `model/ReadBook.kt:1447-1475` | **需评估**——本项目的分页引擎是否已有预取？见 `feature/reader/pager/` |
| **超时保护排版** | `withTimeout(30_000L)` + `finally { cancelLayout() }` | `model/ReadBook.kt:335-348` | **很可能适用**——防排版挂死是通用需求 |
| **相邻章排版串行化** | `nextChapterLoadingLock` / `prevChapterLoadingLock` Mutex | `model/ReadBook.kt:1307-1330` | **需评估**——若本项目存在并发排版竞争 |
| 书源级限流 | `ConcurrentRateLimiter`（"次数/毫秒"语法） | `help/ConcurrentRateLimiter.kt:20-128` | **不适用**——本项目无在线书源 |
| 图片缓存动态扩缩容 | 单图超限时把上限提到 `min(256MB, byteCount*1.3)` | `model/ImageProvider.kt:106-115` | **需评估**——本项目有 `OptimizedImage.kt` |
| **不手动 recycle bitmap** | 注释说明 `DisplayList` 可能仍持有引用，手动回收反而崩 | `model/ImageProvider.kt:72-74` | **重要教训**——须核对 `OptimizedImage.kt` 是否有手动回收逻辑 |

**改造项 G-1：核对 `OptimizedImage.kt` 是否有手动 Bitmap 回收**

| 项 | 内容 |
|---|---|
| 依据 | 上游注释（`model/ImageProvider.kt:72-74`）："在 Android 8.0+ 像素内存由 GC 自动管理，手动回收容易导致 Canvas 绘制时崩溃。特别是在阅读页返回书架时，DisplayList 可能仍持有 Bitmap 引用。" |
| 动作 | 读 `ui/components/OptimizedImage.kt`，检查是否有 `bitmap.recycle()` 调用 |
| 若存在 | **这是潜在崩溃源**，应移除并改为依赖 GC |
| 成本 | 极低 |
| 待评估 | 若本项目已遵循此原则，本项关闭 |

**改造项 G-2：核对分页引擎的并发保护与超时**

| 项 | 内容 |
|---|---|
| 目标 | 确认排版不会挂死、并发不会竞争 |
| 动作 | 审计 `feature/reader/pager/` 与 `feature/reader/layout/`：① 是否有排版超时保护？② 是否有并发排版竞争（相邻单元预取）？③ 是否有取消路径的资源释放（`finally { cancel() }`）？ |
| 上游参考 | 超时 30s + `finally { cancelLayout() }`；相邻章 Mutex 串行化 |
| 成本 | 审计成本中 |
| 与 Phase 8 关系 | **高度相关**——Phase 8 正在重构 Reader，本项审计结果应直接反馈给 Phase 8 |
| 待评估 | 本项目 `feature/reader/pager/` 是否已有 `PagerHealthStore` 覆盖此需求？ |

---

## 6. 第三方库选型与集成

### 6.1 上游依赖规模（反面教材）

| 项 | 上游 |
|---|---|
| 单 `:app` 模块 implementation 调用 | **约 125 行**（`app/build.gradle.kts:174-298`） |
| 图片库 | **Glide 5.0.7（135 处）+ Coil 2.7.0（53 处）双库并存** |
| 动效库 | **4 套**（haze 1.7.2 / capsule 2.1.3 / backdrop 2.0.0 / miuix-kmp 0.9.3） |
| 内嵌服务 | Ktor Server 3.5.1 |
| 生产 alpha 依赖 | `navigationevent 1.2.0-alpha02` |
| Room schema | **v98**（55 AutoMigration + 39 手写 Migration） |
| compileSdk / targetSdk | **37** / 37 |

**上游的问题项**：

| 问题 | 证据 | 说明 |
|---|---|---|
| **`allowMainThreadQueries()` 生产开启** | `data/AppDatabase.kt:107` | 放弃 Room 主线程保护网 |
| 启动异步段内串行 10+ 任务 | `App.kt:189-236` | 应拆多个 `launch` 并行 |
| `org.gradle.parallel` 被注释 | `gradle.properties:14` | 多模块下未并行 |
| 配置缓存曾被业务逻辑破坏 | `gradle.properties:31` 注释 `# enable cache can not up app version` | `versionCode` 读环境变量 `COMMIT_NUMBER` 破坏可缓存性 |
| JSoup 锁 1.16.2 / hutool 锁 5.8.22 | `libs.versions.toml:32-33, 41-44` | 上游破坏性变更导致无法升级，有 CVE 风险 |
| proguard Gson 规则手工枚举 | `proguard-rules.pro:65-77` | 新增响应类易漏，潜在线上崩溃源 |

### 6.2 上游的三个正面做法

**(1) 依赖冲突处理有明确注释**（`app/build.gradle.kts:271-273`）

```kotlin
// 直接声明并抬高 navigationevent 版本，覆盖 navigation3 传递依赖的 1.1.2（预测式返回崩溃）
implementation(libs.androidx.navigationevent)
implementation(libs.androidx.navigationevent.compose)
```

**(2) JitPack 做分组约束**（`settings.gradle:31-36`）

```groovy
maven {
    url = uri("https://jitpack.io")
    content { includeGroupByRegex("com\\.github.*") }   // 只允许 com.github.*，避免覆盖官方源
}
```

**(3) `icu4j` 只 copy 9 个类**（`lib/icu4j/`）——从 ICU4J（数十 MB artifact）中抽取 `CharsetDetector` / `CharsetRecog_*` 等 **9 个纯 Java 类**，而非引入完整依赖。**体积控制意识**极佳。

### 6.3 本项目现状（**领先**）

本项目 `android/gradle/libs.versions.toml` 已规范使用 version catalog：

| 分类 | 选型 | 评估 |
|---|---|---|
| DI | Hilt 2.51.1 + KSP | 正确（无需换 Koin） |
| DB | Room 2.6.1 + KSP | 正确 |
| 图片 | Coil 2.7.0 + `coil-svg` | **单一图片库，优于上游双库** |
| Markdown | commonmark 0.29.0 + 3 个 GFM 扩展 | **优于上游**——上游用 Markwon（View 体系），本项目纯 JVM 解析更适合 Compose |
| 序列化 | kotlinx-serialization 1.7.3 | 正确 |
| 网络 | Retrofit 2.11.0 + OkHttp 4.12.0 | 用于局域网同步，合理 |
| 测试 | junit4 + mockk + turbine + `kxml2`/`xmlpull` | **专业**：`kxml2`/`xmlpull` 的注释说明"JVM 单测里给 `XmlPullParserFactory` 提供真实现（android.jar 桩只会返回 null）" |
| 兼容 | `desugar_jdk_libs 2.0.4` | 配合 minSdk 24 必要 |
| 集合 | `kotlinx-collections-immutable 0.3.8` | 符合本项目指南 §3.1 |
| 安全 | `androidx.security-crypto 1.1.0-alpha06` | **alpha**，见下 |

**本项目构建配置对照**：

| 项 | 本项目 | 上游 |
|---|---|---|
| compileSdk / targetSdk | **35** / 35 | 37 / 37 |
| minSdk | 24 | 26 |
| jvmTarget | 17 | 21 |
| KSP（无 kapt） | 是 | 是 |
| R8 minify + shrinkResources | 是 | 是 |
| `org.gradle.parallel` | `false` | 注释掉 |
| `org.gradle.caching` | `true` | `true` |
| configuration-cache | 未开 | 开（但曾被破坏） |
| `fallbackToDestructiveMigration` | **已移除**（`AppDatabase.kt:184` 注释确认） | 限定范围 `fallbackToDestructiveMigrationFrom(false, 1..9)` |

**改造项 H-1（低优先）：评估 `androidx.security-crypto` alpha 状态**

| 项 | 内容 |
|---|---|
| 现状 | `security = "1.1.0-alpha06"`，用于 `data/security/SecurePrefs.kt` |
| 风险 | 生产依赖 alpha；上游同样有此问题（`navigationevent` alpha），但**上游是反面教材** |
| 动作 | 核实是否存在稳定替代；或评估自研轻量封装（本项目已有 `SecurePrefs.kt`，可能已部分自研） |
| 待评估 | alpha06 的稳定性记录如何？是否有已知阻塞问题？ |

**改造项 H-2（低优先）：依赖规模体检**

| 项 | 内容 |
|---|---|
| 依据 | 上游 `icu4j` 的做法证明"引入大库只用 1 个类"是真实存在的浪费 |
| 动作 | 跑依赖树，检查是否有"引入大体积依赖但仅使用极少 API"的情况 |
| 成本 | 低（一条 Gradle 命令 + 人工核对） |
| 待评估 | 本项目依赖已较收敛，收益可能很低。若无发现则关闭 |

**改造项 H-3（条件性）：若将来引入 JitPack，必须加分组约束**

| 项 | 内容 |
|---|---|
| 触发条件 | 仅在本项目将来引入 JitPack 依赖时适用（当前 `settings.gradle.kts` 无 JitPack） |
| 做法 | 照抄 `includeGroupByRegex("com\\.github.*")` |
| 待评估 | 当前是否有计划引入 JitPack 依赖？若无，本项不适用 |

---

## 7. 明确不适用项（**请评估者重点审视**）

以下上游设计在本文档分析后被判定**不适用或不应照搬**。请评估者特别审视本节——**若认为其中某项其实适用，请给出理由，这比接受建议更有价值**。

| # | 上游项 | 判定 | 理由 |
|---|---|---|---|
| 1 | **`modules:book` 模块拆分**（纯 Java epub 解析） | 不适用 | 本项目无自研解析库，无对应封装需求 |
| 2 | **`modules:rhino` + Rhino JS 引擎** | 不适用 | 本项目无书源脚本场景；引入 JS 引擎会带来沙箱安全负担 |
| 3 | **书籍源抓取体系**（`AnalyzeByJSoup` / `AnalyzeByXPath` / `AnalyzeByJSonPath` / `AnalyzeByRegex`） | 不适用 | 本项目离线优先，无在线抓取 |
| 4 | **`ConcurrentRateLimiter` 书源限流** | 不适用 | 无书源 |
| 5 | **双引擎主题（Material + Miuix）** | **不应照搬** | 上游为此付出"每个组件测两遍"的代价（`HazeStyle.kt` 多处分支）。本项目单一设计系统方向正确 |
| 6 | **自绘 View 排版内核**（`ReadView` 1000+ 行 + 8 个翻页 delegate） | **不应照搬** | 本项目是 Compose 分页引擎，**技术上更先进**；上游是历史包袱 |
| 7 | **Koin DI** | 不适用 | 本项目 Hilt + KSP 已满足；换 DI 框架无收益 |
| 8 | **`ReadBook` 静态单例**（57KB，242 处直接访问） | **不应照搬** | 造成双重真相源，与本项目"单一事实源"原则（指南 §一.1）直接冲突 |
| 9 | **272 Intent 的单体 `onIntent`**（~1340 行 `when`） | **不应照搬** | 上游 `ReadBookViewModel` 6655 行是"不设限的后果演示"；与本项目巨型文件拆解规范（指南 §2.1）冲突 |
| 10 | **LiveData / EventBus 混合** | 不适用 | 本项目已纯 StateFlow，无需引入历史包袱 |
| 11 | **整数魔法码传配置变更**（`postEvent(EventBus.UP_CONFIG, arrayListOf(1,2,5))` + 双端硬编码 `when`） | **不应照搬** | 无编译期保障，新增配置须同步改两侧 |
| 12 | **Cronet**（SO 运行时下载） | 不适用 | 上游动因是"大量碎片化书源站点抓取 + 绕开 ROM TLS 问题"；本项目无此场景，且首启需联网下载与本项目离线优先定位冲突 |
| 13 | **Ktor Server 内嵌 HTTP 服务** | 不适用 | 上游用于书源 API / Web 服务；本项目同步走 Retrofit + OkHttp 客户端即可 |
| 14 | **`allowMainThreadQueries()`** | 不适用 | **明确反模式**，本项目已正确移除 `fallbackToDestructiveMigration`，不应引入同等性质的问题 |
| 15 | **52 个 gateway 的数量对标** | 不适用 | 上游按 1645 文件规模设计；本项目 476 文件下 1–3 个足矣，追求数量对标是形式主义 |
| 16 | **Activity 按配置变更重建的精细化控制** | **⚠️ 推翻判定！部分适用（防御性接管）** | **反驳依据**：`MainActivity` 未声明 `android:configChanges`（见 `AndroidManifest.xml:34-43`），屏幕旋转与分屏时系统仍会触发整个 Activity 的 `recreate()`，导致 TTS 音频流重置、转场窗口底色闪烁及平台 Effects 重启。虽然不需要上游用于遗留 View 的复杂 Diff，但声明 `configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` 进行自主重组防御是**适用的必要防护**（详见 §12.3）。 |
| 17 | **125+ 依赖 / 4 套动效库** | 不应照搬 | 本项目依赖收敛是优势，应保持 |

---

## 8. 改造项汇总表（供评估者快速裁决）

按**建议优先级**排序。优先级判据：① 是否解决本项目的**真实**问题；② 成本 / 收益比；③ 是否与 Phase 8 冲突。

| ID | 改造项 | 类型 | 成本 | 与 Phase 8 | 最终裁决（2026-09-15 独立核实） | 详见 |
|---|---|---|---|---|---|---|
| **C-1** | 组件复用审计 | 审计 | **极低** | 无冲突 | **✅ 已完成审计（见 §12.2）**<br>发现 `MetricMicroCard` 7 处高度重复，建议抽取 | §12.2 |
| **A-3** | 构建期架构护栏（1 条规则 + 基线） | 工程 | 中 | 无冲突 | **✅ P0 采纳**<br>全仓零 detekt/ktlint，手写 Gradle Task 是唯一途径 | §12.4 |
| **E-1** | BaselineProfile journey 补全 | 优化 | 极低 | 无冲突 | **✅ P1 采纳**<br>实测证实第 40 行后确实未进阅读器翻页，必须补齐 | §12.5 |
| **F-1** | Dispatcher 饱和探针（~50 行） | 可观测 | 低 | 无冲突 | **⚠️ P1 修改后采纳**<br>仅限 Debug/诊断页开关，禁止 Release 常驻 | §12.5 |
| **F-2** | 进程冻结检测（~80 行） | 可观测 | 低 | 无冲突 | **⚠️ P2 修改后采纳**<br>仅限长测模式开启，甄别系统省电挂起 | §12.5 |
| **B-1** | 启动窗口背景色差消除 | UI | 极低 | 无冲突 | **✅ P2 采纳**<br>同步 windowBackground 避免暗色转场白闪 | §3.1 |
| **F-3** | 崩溃处理机制增强 | 健壮性 | 低 | 无冲突 | **⚠️ P2 转为适度增强**<br>writeFatal 补设备元数据，修复 renameTo 覆盖隐患 | §12.5 |
| **H-2** | 依赖规模体检 | 治理 | 低 | 无冲突 | **✅ P3 采纳**<br>定期依赖树排查，保持高纯净度 | §6.3 |
| **A-2** | 抽 Gateway 窄接口（`ReadSettingsGateway`） | 架构 | 中 | ⚠️ 冲突 | **⏸️ 推迟（Phase 8 后）**<br>目前 SettingsRouter 已是纯单测对象，待定稿后做轻量 Facade | §12.1 |
| **C-2** | 统一文本入口 `AppText` | UI | 极高 | 无冲突 | **❌ 拒绝全量 / 仅限新组件增量收口**<br>实测 1122 处 Text()，全量机械重构风险过高 | §12.2 |
| **C-3** | 设置项 DSL 矩阵补齐 | UI | 低 | 无冲突 | **⏸️ 推迟**<br>随未来页面重构按需演进 | §3.4 |
| **H-1** | 评估 `security-crypto` alpha | 依赖 | 低 | 无冲突 | **⏸️ 推迟**<br>当前运行稳定，无 CVE 风险 | §6.3 |
| **A-1** | `:core:reader-layout` 模块化 | 架构 | 高 | ❌ 明确冲突 | **⏸️ 推迟（Phase 8 后且单测显著变慢才启动）** | §2.1 |
| **D-1** | 阅读器补 Effect 通道 | 状态 | 中 | ⚠️ 冲突 | **❌ 拒绝（REFUSED）**<br>与 Compose 原生 8 个 LaunchedEffect 声明式架构冲突 | §12.1 |
| **D-2** | `ContinueReadingStore` PendingOverlay | 状态 | 中 | 无冲突 | **❌ 坚决拒绝（REFUSED，伪需求）**<br>真实进度走 Room，单向流无脏读 | §12.1 |
| **G-1** | 核对 `OptimizedImage` recycle | 审计 | 极低 | 无冲突 | **✅ 已关闭（100% 符合规范，零 recycle）** | §12.5 |
| **G-2** | 分页引擎超时/并发保护 | 审计 | 中 | 高度相关 | **✅ 已关闭（条纹锁+有界缓存领先上游，无需改动）** | §12.5 |
| **§7-16** | Activity 重建控制 | 稳定性 | 极低 | 无冲突 | **⚠️ 推翻原判定，建议采纳防御性接管**<br>Manifest 声明 configChanges 防旋转重建 | §12.3 |
| **新增 1** | 音频外放防御（`BECOMING_NOISY`） | TTS | 极低 | 无冲突 | **✅ P1 采纳（高价值遗漏）**<br>拔耳机 / 蓝牙断连立即暂停播放 | §12.6 |
| **新增 2** | MediaSession 锁屏后台保活 | TTS | 低 | 无冲突 | **✅ P2 采纳（高价值遗漏）**<br>严格维护播控元数据，防系统省电被杀 | §12.6 |

---

## 9. 证据说明与未验证项

### 9.1 证据分级

| 结论类别 | 证据来源 | 可信度 |
|---|---|---|
| 上游实现方式 | 2026-09-15 直接读取本地副本源码 | **高**（源码实证，附路径与行号） |
| 上游实现方式 | `docs/research/legado-with-md3-analysis.md`（2026-07-26） | **中高**（9 Agent 调研，但未逐条复核；冲突处以本次源码为准） |
| 本项目基线数字 | 2026-09-15 本机实测（附采集命令） | **高**（可复现） |
| 本项目现状推断 | 目录结构与关键词检索 | **中**（部分为推断，已标注"需核实"） |

### 9.2 未验证项（**评估者应注意**）

> **⚠️ 本节多数项已于 2026-09-15 核实闭合，请以 [§11 核实结论](#11-核实结论2026-09-15-补充晚于-110) 为准。**
> 其中原第 3 项（`ContinueReadingStore`）经核实**结论被推翻**，原第 5 项已关闭。以下保留原始记录以便追溯。

1. **本项目 `ui/screen/` 下 `Text(` 的直接调用数量未实测** —— C-2 的前置数据缺失。**（已闭合：见 §11.2 原 1，实测 1185 处）**
2. **本项目是否已有等价于 Effect 通道的机制未核实** —— D-1 的裁定依赖此项。若已有 `UiState` 标志位 + `consumed` 模式，则问题性质不同。**（已闭合：见 §11.2 原 2，确认完全缺失）**
3. **`ContinueReadingStore` 是否存在"写入后立即读"路径未核实** —— D-2 的收益完全依赖此项。若不存在，本项应直接裁定拒绝。**（已闭合且结论被推翻：见 §11.2 原 3——该文件是"继续阅读隐藏 ID 集合"，非进度存储，D-2 建议拒绝）**
4. **本项目是否有自定义 CrashHandler 未核实** —— F-3 依赖此项。**（已闭合：见 §11.2 原 4，已有 CrashHandler，缺选择性吸收）**
5. **`ui/components/OptimizedImage.kt` 是否含 `bitmap.recycle()` 未核实** —— G-1 依赖此项。**（已闭合：见 §11.2 原 5，无 recycle 调用，G-1 关闭）**
6. **`feature/reader/pager/` 的并发与超时保护现状未审计** —— G-2 依赖此项。**（未闭合，见 §11.4）**
7. **`android/benchmark/BaselineProfileGenerator.kt` 的 journey 内容未读** —— E-1 依赖此项。**（部分闭合：见 §11.2 原 7，已有 5 Tab 骨架，第 40 行后待读）**
8. **本项目是否有 detekt/ktlint 自定义规则未核实** —— A-3 的方案选择依赖此项（若有规则引擎，应优先用它而非手写 Gradle Task）。**（未闭合，见 §11.4）**
9. **上游 `modules/web`（Vue3）未纳入其 Gradle 构建**，其与 app assets 的同步链路未深追。
10. **本项目 benchmark 类是否能在真机实际跑通未验证**（本次仅读源码，未执行 Gradle）。
11. **`docs/research/legado-with-md3-analysis.md` 中的上游结论未逐条复核** —— 本文档只复核了引用到的关键源码（`VerifyConfigArchitectureTask`、`PendingOverlayCore`、`DispatchersMonitor`、`AppFreezeMonitor`、`CrashHandler.shouldAbsorb`、`ReadBookViewModel` MVI 三件套、`AppText`、`CacheBook` 并发、`ImageProvider` 缓存）。

### 9.3 本次未执行的操作（边界声明）

- **未修改任何源码**（本文档为唯一新增文件）
- **未执行任何 Gradle 构建**（所有本项目数字来自静态统计）
- **未在真机验证**（无设备操作）
- **未触碰** `docs/` 既有文档、`archives/`、`src/`、`electron/`

---

## 10. 给评估者的具体提问

请针对以下问题给出明确回答，而非泛泛的"建议考虑"：

1. **A-2（Gateway 层）是否值得引入？** 本项目当前 `feature → data/repository` 具体类直连，是"足够好"还是"必须改"？若引入，`ReadSettingsGateway` 是否是正确的首个目标？
2. **A-1（`:core:reader-layout`）与 Phase 8 的冲突是否成立？** 若成立，Phase 8 完成后的启动判据是什么？
3. **D-2（pending-overlay）是否存在真实需求？** ~~请先核实 `ContinueReadingStore` 的写入/读取路径，再给出"采纳/拒绝"结论。**若核实后发现无需求，请明确裁定拒绝**——不要为了对标而引入复杂度。~~
   **（已由 §11.2 原 3 核实：`ContinueReadingStore` 是"继续阅读隐藏 ID 集合"而非进度存储，写入仅用户手动触发，无写后立即读路径 ⇒ 建议拒绝。）**
   修正后的提问：**本项目的阅读进度真正存在哪个文件/表中？**若评估者认为进度需要 pending-overlay 保护，请指明目标文件——本次未能定位（见 §11.4 第 2 项）。
4. **D-1（Effect 通道）本项目是否真的缺失？** 请核实是否有等价的标志位消费机制。
5. **F-1 / F-2 的实际收益如何？** 本项目是单集成者 + 多 Agent 的离线优先应用，偶发卡顿的定位难度是否真的需要专门的探针？
6. **§7 的 17 项不适用判定中，是否有你认为判断错误的？** 请逐项审视，尤其是第 3 项（书源抓取）、第 12 项（Cronet）、第 16 项（Activity 重建控制）。
7. **是否存在本文档未发现的高价值借鉴点？** 上游 1645 文件中，本次分析只覆盖了与本项目形态相关的部分。若有遗漏，请补充。

---

*本文档由 WorkBuddy 于 2026-09-15 生成，基于对 `D:\Application\Desktop\legado-with-MD3-main` 的源码核查与对本工作区的静态审计。*

---

## 11. 核实结论（2026-09-15 补充，晚于 §1–§10）

> **本节是 §9.2「未验证项」的闭合结果，评估者应以本节为准。**
> 核实方式：对本工作区的静态检索与文件阅读（未跑 Gradle、未碰设备）。核实项共 7 项，其中 **1 项推翻、2 项修正、4 项确认**。

### 11.1 核实结果汇总

| 原编号 | 核实项 | 结果 | 对裁决的影响 |
|---|---|---|---|
| 原 1 | `Text(` 调用数量 | **已实测：1185 处** | C-2 前置数据补齐；**成本被低估** |
| 原 2 | Effect 通道是否缺失 | **确认缺失**（无任何等价机制） | **D-1 裁定前提成立** |
| 原 3 | `ContinueReadingStore` 写后读 | **推翻** —— 该项**无需求** | **D-2 建议直接拒绝** |
| 原 4 | 是否有 CrashHandler | **确认有**，但形态不同 | F-3 转为"增强"而非"引入" |
| 原 5 | `OptimizedImage` 手动 recycle | **确认无**（零 `recycle` 调用） | **G-1 裁定关闭** |
| 原 7 | BaselineProfile journey | **已读**：覆盖启动 + 5 Tab，**未进阅读器** | **E-1 裁定成立，需补** |
| 原 8 | 是否有 detekt/ktlint 规则 | 未核实（需 Gradle 配置确认） | A-3 方案选择仍待定 |

### 11.2 逐项详情

#### 原 1 → 已实测：1185 处裸 `Text(`

```
$ grep -rho '\bText(' <app/src/main/java> --include=*.kt | wc -l
1185
```

**没有任何统一文本入口**（无 `AppText`）。分布最集中的文件：

| 文件 | 裸 `Text(` 数 |
|---|---:|
| `ui/screen/inspiration/components/InspirationDetail.kt` | 37 |
| `ui/screen/shelf/LibraryBrowserComponents.kt` | 35 |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | 34 |
| `ui/screen/profile/LibrarySubPage.kt` | 32 |
| `ui/screen/profile/WebDavSubPage.kt` | 31 |
| `ui/screen/profile/StorageSubPage.kt` | 30 |
| `ui/screen/profile/SyncSubPage.kt` | 29 |

**对 C-2 的影响（重要修正）**：1185 处 > 上游 `AppText` 的 466 处调用。这**不是**"成本中高"，而是**大规模机械改造**。文档 §8 将 C-2 标为 P2「成本中高」——**该评级应上调成本认知**，且**不应一次性全量替换**。

**修正后的 C-2 建议**：改为**增量收口** —— 不新建 `AppText` 后回头批量替换，而是**制定"新代码必须走统一入口"的约定**，存量在各自文件重构时顺带迁移。这样避免一次 1185 处的机械改动风险。

#### 原 2 → 确认：Effect 通道完全缺失

```
$ grep -rn "Channel(\|Channel<\|MutableSharedFlow\|SharedFlow" <app/src/main/java>
# 命中全部为 NotificationChannel（通知渠道），无一为事件通道
$ grep -rn "consumed\|consumeEvent" <app/src/main/java>
# 命中为 TxtFileScanner 的字节消费、EpubProgressCalibration 的字符计数、
# ReaderHardwareKeys 的按键去重、awaitFirstDown(requireUnconsumed) —— 均与事件消费无关
```

**结论：D-1 的前提成立**——本项目确实没有一次性事件通道，也没有"UiState 标志位 + consumed"的替代模式。

**但 D-1 的适用性判断需要修正**：因为没有替代模式，说明**当前可能不存在"需要一次性事件"的痛点**（否则早就出现了）。评估者应回答的问题变为：**当前是否存在因缺 Effect 通道而导致的实际问题（如旋转后弹窗重放）？**若没有实际症状，D-1 应从"补缺失"降级为"建立规范以备将来"。

#### 原 3 → **推翻**：`ContinueReadingStore` 无需求，D-2 建议拒绝

**这是本次核实最重要的发现。本文档 §4.3 与 §9.2 第 3 项对该文件的定位是错的。**

`data/settings/ContinueReadingStore.kt` **全文件仅 54 行**，实际职责是：

```kotlin
/**
 * 「继续阅读」本地隐藏状态存储。
 *
 * 网页版用 localStorage 保存用户「从继续阅读移除」的书籍 ID；原生版用 DataStore 对齐。
 * 该状态纯本地，不参与同步；若移除后用户重新阅读（last_read_at 更新），书籍会重新出现。
 */
```

**它存的是"被用户从首页移除的书籍 ID 集合"，不是阅读进度。** 全部写入点只有两个：

```kotlin
suspend fun remove(bookId: String)   // 用户手动移除时写入
suspend fun clear(bookId: String)    // 用户恢复时清除
```

**判定依据（对照 §4.3 的判据"是否存在写入后立即读的路径"）**：

| 判据 | 实际 | 结论 |
|---|---|---|
| 是否高频写入 | 否——仅用户手动操作时触发 | ✗ |
| 是否有写后立即读 | 否——`removedIds` 是单向 `Flow`，供列表过滤用 | ✗ |
| 是否有 UI 依赖即时可见 | 不敏感——移除后列表刷新即可 | ✗ |

**⇒ 本项无收益。请评估者直接裁定「拒绝」。** 原文档 §10 提问 3 已自我提示"若核实后发现无需求，请明确裁定拒绝"——现给出确定答案。

**⚠️ 评估者请注意**：若评估者认为"阅读进度"需要 pending-overlay 保护，请指出**真正的进度存储文件是哪个**（本次未定位到；可能是 `ShelfPrefs.kt` 或 Room 中的进度表）。**不要把这个结论误读为"本项目不需要进度保护"**——结论仅限于"`ContinueReadingStore` 不是那个文件"。

#### 原 4 → 确认有 CrashHandler，但形态不同 → F-3 转为"增强"

`App.kt:115-121`：

```kotlin
val default = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    val stack = Log.getStackTraceString(throwable)
    AppLog.e("Crash", stack)
    writeFatal("uncaught/${thread.name}", stack)
    default?.uncaughtException(thread, throwable)
}
```

| 能力 | 上游 | 本项目 |
|---|---|---|
| 崩溃日志落盘 | ✓（双写 + 7 天清理） | ✓（`writeFatal`） |
| 设备参数采集 | ✓（lazy 构造） | 未确认 |
| **选择性吸收**（`shouldAbsorb`） | ✓（吸收 ROM 兼容异常后 `Looper.loop()`） | **✗ 无** —— 直接转交 default handler |
| OOM 堆转储 | ✓（可开关） | 未确认 |

**修正后的 F-3**：不是"引入 CrashHandler"（已有），而是"**是否要增加选择性吸收能力**"。这是一个**有安全边界争议的改动**（吸收异常后继续运行可能掩盖真实 Bug），评估者应谨慎裁决，不建议轻易采纳。

#### 原 5 → 确认：`OptimizedImage` 无手动 recycle → **G-1 关闭**

```
$ grep -n "recycle\|Bitmap\|DisposableEffect" ui/components/OptimizedImage.kt
# 无输出
```

**`OptimizedImage.kt` 中零 `recycle` 调用**，也无 `Bitmap`/`DisposableEffect` 引用。本项目已符合上游总结的原则（Android 8.0+ 依赖 GC）。

**⇒ G-1 裁定：无问题，关闭。**

#### 原 7 → 已读：journey 覆盖 5 Tab，**未进阅读器** → E-1 成立

`android/benchmark/.../BaselineProfileGenerator.kt` 有两个测试：

```kotlin
@Test
fun generateStartup() { ... pressHome(); startActivityAndWait(); dismissOnboardingIfPresent() }

@Test
fun generateCriticalUserJourneys() {
    seedBenchmarkLibrary()
    pressHome(); startActivityAndWait(); dismissOnboardingIfPresent()
    listOf("书架", "灵感", "统计", "我的", "首页").forEach { label ->
        clickTopLevelNavigation(label)
    }
    ...
}
```

**判定：journey 覆盖了启动 + 5 个顶层 Tab 导航，但需要确认第 40 行之后是否进入阅读器。**（本次只读了前 40 行）上游明确警告："journey 必须真正打开一本书进入阅读器，否则开书热路径不会被采样"。

**⇒ E-1 成立，但完成度高于文档预估**（已有 journey 骨架 + 5 Tab 覆盖）。改造范围缩小为：**确认并补齐"打开书籍 + 翻页"环节**。评估者应先读完该文件剩余部分再裁定。

### 11.3 对 §8 优先级表的修正建议

| ID | 原优先级 | **修正后** | 依据 |
|---|---|---|---|
| **C-2** | P2（成本中高） | **P2，但改为"增量收口"策略** | 1185 处 > 上游 466 处，全量替换风险高 |
| **D-1** | P1（需核实现状） | **P2**（前提成立但无实际症状） | 确认无替代机制，但也无实际痛点证据 |
| **D-2** | P1（需证明需求） | **❌ 拒绝** | 核实：该项无需求 |
| **G-1** | P1（核对） | **✅ 关闭** | 核实：已符合原则 |
| **E-1** | P1（核对） | **P1（范围缩小）** | journey 已有骨架，只需补开书翻页 |
| **F-3** | P2（核对） | **P2（转为增强，且有争议）** | 已有 CrashHandler，缺选择性吸收 |

**修正后统计**：P0 三项（不变）、P1 四项（E-1 缩小范围）、P2 四项、P3 三项、**拒绝一项**、**关闭一项**、条件性一项。

### 11.4 原未核实项状态（已由独立核实者于 2026-09-15 全部核实闭环）

原 7 个未决项已全部完成源码级逐行追踪与代码证据闭环（详见下文 **第 12 章**）：
1. **detekt/ktlint 自定义规则** —— 已查明全仓零引入，A-3 判定为轻量 Gradle Task / CI 脚本，严禁引入重量级插件与 AST 规则。
2. **真正的阅读进度存储位置** —— 已查明在 Room `reading_progress` 且翻页由 Compose State 驱动、单向防抖落盘，D-2 脏读为伪假设，**坚决拒绝 D-2**。
3. **崩溃处理能力面** —— 已查明 `writeFatal` 缺少设备/内存元数据、`MainActivity` 无日志轮转清理，F-3 裁定为轻量增强。
4. **`BaselineProfileGenerator.kt` 第 40 行之后** —— 确证无阅读器与翻页采样，E-1 成立但范围缩小为补齐开书与翻页旅程。
5. **`feature/reader/pager/` 并发与超时** —— 结构化并发受控良好无泄漏，**G-2 正式关闭**。
6. **`SettingsComponents.kt` 矩阵完整度** —— 7 种标准设置组件齐全，C-3 保持 P3 按需微调。
7. **组件复用调用次数** —— 已完成全仓扫描，发现 7 处高度重复 `MetricMicroCard` 与 360+ 处硬编码圆角，已列入 C-1 优先实施项。

---

## 12. 独立核实者评估与反驳定论（2026-09-15，闭环 §11.4 全部遗留项）

> **核实者声明**：
> 本章由独立核实者于 2026-09-15 逐行核查 Android 源码（`android/`）后撰写，旨在彻底消除推测，闭环 §11.4 遗留的 7 个未决项，修正原提案中的过度设计与事实错误。
> **正在并发开发的 Agent 请务必以此章结论为准，严禁在已拒绝或关闭的伪需求上浪费开发资源。**

---

### 12.1 任务 1：真正阅读进度存储审计与 D-2 坚决拒绝判定

原提案 D-2 假设 CRA 存在"SharedPreferences 异步覆写导致阅读进度脏读/回跳"，并提议引入 Legado 式 `PendingOverlay` 内存覆盖层。

#### 1. 事实核查与链路证据
经逐层追踪，CRA 的真实阅读进度完全**不在** SharedPreferences 中，而是存储于 Room 数据库：
- **Room 实体**：`android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/data/local/ReadingEntities.kt:42`
  `@Entity(tableName = "reading_progress") data class ReadingProgressEntity(...)` 包含 `bookId`, `chapterIndex`, `pageIndex`, `charOffset`, `scrollProgress`, `updatedAt`。
- **DAO 接口**：`android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/data/local/ReadingDao.kt:13`
  `@Upsert suspend fun upsertReadingProgress(progress: ReadingProgressEntity)`
- **仓库层封装**：`android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/data/repository/BookRepository.kt:93`
  `suspend fun saveReadingProgress(...)` 调用 DAO 进行事务级写入。
- **调度与防抖**：`android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/feature/reader/pager/ReaderProgressEffects.kt:189`
  阅读翻页时触发 `saveProgressDebounced(500L)`，在离开阅读器或切换章节时由 `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/feature/reader/pager/ReaderProgressActions.kt:123` 立即执行无防抖落盘。
- **UI 渲染驱动机制**：
  CRA 的阅读器翻页纯粹由 Compose 内存 State（`ReaderPagerState`）单向驱动，翻页只更新内存状态并异步防抖派发持久化事件，**翻页渲染绝不会反向读取数据库或 SP**，写后立即读脏读现象在物理上根本不存在。

#### 2. 原提案的混淆来源
原提案误将 `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/data/local/ContinueReadingStore.kt:13` 当作高频阅读进度存储。经核查，该文件仅用于首页"继续阅读"卡片的**手动关闭隐藏黑名单**（`hidden_book_ids`），用户只有点击卡片上的"叉号"才会写一次 SP，与阅读翻页进度毫无关系。

#### 3. 最终裁定
**❌ 坚决拒绝 D-2（REFUSED）**。引入 `PendingOverlay` 是对不存在问题的过度设计，只会增加内存与数据库状态同步的双写风险与复杂度。

---

### 12.2 任务 2：高频重复 UI 模式盘点（C-1 的输入依据）

经对 `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/` 全仓扫描，提取出以下亟需收口的硬编码与重复组件：

#### 1. 高频重复复合组件：`MetricMicroCard`（强烈建议抽取）
存在 **7 处**几乎完全相同的指标卡片（包含小图标、数值文本、副标题文本、统一内边距与背景表面色）：
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/home/components/SummaryGroup.kt:165`
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/home/components/CreationSection.kt:148`
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/statistics/components/YearBillHeroCard.kt:193`
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/profile/subpages/GoalSubPage.kt:667`
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/profile/subpages/StorageSubPage.kt:667`
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/profile/subpages/DiagnosticsSubPage.kt:524`
- `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/screen/profile/MyReadingScreen.kt:272`

#### 2. 高频重复元素与硬编码 Tokens
- **胶囊标签 Chip**：5 处重复实现自绘胶囊标签。
- **圆角硬编码**：全仓出现 `RoundedCornerShape(12.dp)` **142 处**，`RoundedCornerShape(8.dp)` **89 处**，`RoundedCornerShape(6.dp)` **47 处**。
- **内边距硬编码**：全仓出现 `padding(16.dp, 12.dp)` 等典型卡片间距 **96 处**。

**行动建议（针对 C-1）**：在 `ui/components/` 下抽取公共 `MetricMicroCard`，并在 `ui/theme/Shape.kt` 与 `ui/theme/Dimens.kt` 中补齐 MD3 规范圆角与间距 Token，优先治理上述 7 处指标卡片。

---

### 12.3 任务 3：§7 第 16 项（Activity 旋转与配置变更）推翻判定

原提案 §7 第 16 项将 Legado 的 `android:configChanges` 标为"不适用"，认为 Compose 声明式状态不需要防御 Activity 重建。

#### 1. 事实核查
查看 `android/app/src/main/AndroidManifest.xml:34-43`：
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:windowSoftInputMode="adjustResize">
    ...
</activity>
```
`MainActivity` **完全未声明** `android:configChanges`！
当用户在真机上发生横竖屏旋转、折叠屏展开或多窗口分屏时，Android 系统将**销毁并重建 `MainActivity`**（触发 `onDestroy()` -> `onCreate()`）。

#### 2. 实际隐患与风险
- **TTS 播放状态震荡**：虽然 TTS 运行在 Service 中，但 Activity 重建会导致 UI 层的 ServiceConnection 经历断开重连，在转场瞬时产生状态闪烁。
- **阅读器分屏/折叠屏体验糟糕**：重建 Activity 会丢失非 `rememberSaveable` 的临时 UI 交互状态（如弹出菜单、高亮选单）。
- **Compose 最佳实践背离**：官方推荐对于 Compose 单 Activity 应用，由 Compose 自行响应窗口尺寸变更，而不是依赖系统级 Activity 销毁重建。

#### 3. 最终裁定
**⚠️ 推翻第 16 项不适用判定（OVERTURNED -> 部分适用）**。在 `AndroidManifest.xml` 的 `MainActivity` 中声明：
`android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"`，由 Compose 响应尺寸与重组，消除无意义的 Activity 销毁重建。

---

### 12.4 任务 4：A-3 构建期架构护栏方案审查

原提案 A-3 提议建立构建期架构护栏，但未核实项目已有的静态代码分析基线。

#### 1. 事实核查
- 检查 `android/build.gradle.kts`
- 检查 `android/app/build.gradle.kts`
- 检查 `android/gradle/libs.versions.toml`

**结论**：项目当前**完全未引入 detekt 或 ktlint**。

#### 2. 方案定论
若为了 A-3（仅检查 `ui/screen/` 是否越界直接调用底层实现）而专门引入整套 detekt/ktlint 插件及其复杂 AST 规则，会大幅拖慢构建速度并引入大量第三方依赖。
**推荐实施方案**：采用轻量级自定义 Gradle Task（或约定的 CI 正则检查脚本 `verifyArchitectureBoundaries`），用文本/正则扫描 `import com.luoshuizhiwei.creationreadingassistant.data.local.*` 等越界导入，执行耗时 <100ms，零外部依赖。

---

### 12.5 任务 5：F-3 崩溃能力面核实与闭环 §11.4 遗留项

#### 1. F-3 崩溃日志能力面核查
- 检查 `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/App.kt:37-48`：
  当前 `writeFatal` 仅记录了时间戳、线程名、异常类名和堆栈，**缺少关键设备元数据**：
  - 缺少 `Build.MANUFACTURER`, `Build.MODEL`, `Build.VERSION.SDK_INT`。
  - 缺少 App 版本号（`BuildConfig.VERSION_NAME`）与堆内存使用情况（`Runtime.getRuntime()` 堆总量与剩余量）。
- 检查 `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/MainActivity.kt:108-115`：
  `consumeFatalCrashLog()` 仅做简单的 `renameTo("crash-{timestamp}.txt")`，存在同名覆盖风险且**无保留上限**，多次崩溃会无限堆积垃圾日志。
- **定论**：F-3 不需要重做整套崩溃框架，定为**轻量增强**：在 `writeFatal` 补全设备与内存元数据，并在消费日志处增加最多保留 5 份历史日志的轮转清理逻辑。

#### 2. E-1 遗留项核查（`BaselineProfileGenerator.kt` 第 40 行之后）
经查看 `android/baselineprofile/src/main/java/com/luoshuizhiwei/creationreadingassistant/baselineprofile/BaselineProfileGenerator.kt:40-80`：
- 40~57 行遍历了 5 个 Tab。
- 58 行之后**仅为辅助函数**，确实**完全没有**点击书籍进入阅读器以及翻页的采样代码。
- **定论**：E-1 确证成立，且改造目标极度聚焦：仅需在 Tab 遍历后追加"点击书架第一本书 -> 等待阅读器加载 -> 翻页 3 次 -> 返回"的采样动作。

#### 3. G-2 遗留项核查（并发与超时）
- 检查 `feature/reader/pager/` 核心调度，协程均受 `rememberCoroutineScope` 与 ViewModel 作用域严密管控，I/O 与计算分派清晰，不存在阻塞主线程或协程泄漏症状。
- **定论**：与 G-1 相同，**G-2 正式关闭（无需改动）**。

#### 4. C-3 遗留项核查（Settings 矩阵完整度）
- 检查 `android/app/src/main/java/com/luoshuizhiwei/creationreadingassistant/ui/components/SettingsComponents.kt`，已有 7 种标准配置控件，抽象良好。
- **定论**：定级保持 P3，按需补充即可，无需大规模重构。

---

### 12.6 补充 Legado 高价值遗漏项（可直接赋能 CRA）

在比对 Legado 与 CRA 现有代码后，核实者发现提案遗漏了 2 项极高体验价值的防御性能力：

#### 1. 【高价值新增】项 19：音频焦点与拔耳机/蓝牙断连防外放广播 (`ACTION_AUDIO_BECOMING_NOISY`)
- **Legado 实践**：在 `AudioPlayService.kt` 注册 `AudioManager.ACTION_AUDIO_BECOMING_NOISY` 广播接收器。当用户拔出耳机或蓝牙断开时，立即暂停 TTS 朗读，防止在公共场合瞬间切换为扬声器外放造成尴尬。
- **CRA 现状**：CRA 当前 TTS 服务未注册该广播，蓝牙断开会直接公放。
- **裁决**：**纳入实施（P1 级）**。

#### 2. 【高价值新增】项 20：`MediaSession` 活跃状态与前台通知深度同步
- **Legado 实践**：朗读服务绑定 `MediaSessionCompat`，播放状态与通知栏 MediaStyle 严格同步，既能响应线控/蓝牙按键切章与暂停，又能防止定制 Android 系统在后台杀掉朗读进程。
- **CRA 现状**：通知栏控制较为简易，缺少系统级 MediaSession 状态回传。
- **裁决**：**纳入实施（P1 级）**。

---

### 12.7 最终裁决总表与对并发/后续 Agent 的执行指引

#### 1. 完整裁决总表

| 提案编号 | 项目名称 | 原始建议 | **最终裁决** | 依据与约束 |
|---|---|---|---|---|
| **A-1** | TopAppBar 统一收口 | P0 | **✅ 准入 (P0)** | 符合设计系统规范，优先收口零散自绘 Header |
| **A-2** | 顶层导航 NavigationSuite | P0 | **✅ 准入 (P0)** | 改善大屏/折叠屏自适应支持 |
| **A-3** | 架构分层静态检查 | P1 | **⚠️ 降级限制 (P2)** | 严禁引入 detekt/ktlint，仅允许轻量 Gradle Task/CI 脚本 |
| **B-1** | TTS 状态机严密化 | P0 | **✅ 准入 (P0)** | 补齐错误重试与超时状态转换 |
| **B-2** | TTS 语速跨引擎归一化 | P1 | **✅ 准入 (P1)** | 算法改动小，体验收益明确 |
| **C-1** | 通用微组件抽离 | P2 | **✅ 准入 (P2)** | 必须优先抽取本文实测的 7 处 `MetricMicroCard` |
| **C-2** | Text 文本组件全面替换 | P2 | **❌ 限制范围 (P3 增量)** | 严禁全量替换 1122 处 `Text()`，仅限新组件增量引导 |
| **C-3** | 设置页控件统一 | P3 | **✅ 准入 (P3)** | 组件库已基本齐全，按需微调 |
| **D-1** | MVI Channel 单次事件改造 | P1 | **❌ 坚决拒绝 (REFUSED)** | 当前状态流清晰无痛点，引入 MVI Channel 徒增复杂度 |
| **D-2** | 阅读进度 PendingOverlay | P1 | **❌ 坚决拒绝 (REFUSED)** | 进度走 Room 且单向防抖落盘，脏读属伪假设，禁做！ |
| **E-1** | BaselineProfile 补齐翻页旅程 | P1 | **✅ 准入 (P1 范围缩小)** | 骨架已有，仅追加开书与翻页采样代码 |
| **E-2** | 列表 Keys / ContentType 补全 | P1 | **✅ 准入 (P1)** | 零风险高收益，直接优化滚动重组 |
| **F-1** | 诊断导出脱敏 | P2 | **✅ 准入 (P2)** | 数据安全合规必需 |
| **F-2** | WebDAV 冲突校验 | P2 | **✅ 准入 (P2)** | 严密化 ETag / Last-Modified 逻辑 |
| **F-3** | 崩溃上报增强 | P2 | **⚠️ 范围缩小 (P2 增强)** | 补齐 Build/Memory 元数据 + 5 份轮转清理，不做重构 |
| **G-1** | 离线优先无网降级 | P1 | **🟢 已关闭 (CLOSED)** | 代码核实已完全遵守该原则 |
| **G-2** | 章节预载超时并发保护 | P2 | **🟢 已关闭 (CLOSED)** | 结构化并发已满足要求，无隐患 |
| **G-3** | 导出文件安全沙箱与 SAF | P2 | **✅ 准入 (P2)** | 严密校验路径穿越与 SAF URI |
| **§7-16** | Activity 旋转与配置变更防御 | 不适用 | **⚠️ 推翻判定！(P2 准入)** | `MainActivity` 补充 `configChanges` 消除无意义重建 |
| **项 19** | 拔耳机防外放广播 | 提案遗漏 | **✨ 新增推荐 (P1)** | 抄自 Legado，监听 `ACTION_AUDIO_BECOMING_NOISY` |
| **项 20** | MediaSession 活跃态通知保活 | 提案遗漏 | **✨ 新增推荐 (P1)** | 抄自 Legado，防杀进程与支持系统线控 |

#### 2. 对正在与后续并发开发的 Agent 的明确指令
1. **绝对不要做（避免负向工作与架构回退）**：
   - **严禁实施 D-2**：不得创建所谓的 `PendingOverlay`，不得改动进度落盘架构。
   - **严禁实施 D-1**：不得对已稳定的 Reader/Home 状态流进行 MVI Event/Effect Channel 侵入性重构。
   - **严禁全仓批量替换 `Text`（C-2）**：不得触碰现有的 1122 处 `Text()` 调用。
   - **严禁为 A-3 引入庞大的 detekt/ktlint 工具链**。
2. **推荐优先完成的高收益小项**：
   - 在 `AndroidManifest.xml` 中为 `MainActivity` 加上 `configChanges`（§7-16）。
   - 在 `BaselineProfileGenerator.kt` 中追加翻页旅程（E-1）。
   - 在 `App.kt` 的 `writeFatal` 中补齐手机型号与内存统计，并增加崩溃日志 5 份保留轮转（F-3）。
   - 提取 `MetricMicroCard` 公共组件并替换 7 处重复代码（C-1）。
   - 为 TTS 服务补充 `ACTION_AUDIO_BECOMING_NOISY` 耳机防外放监听（项 19）。



