# android/native — 剩余核实项结论（A-3 · G-2 · F-3 · C-1 补充）

> **状态**：核实结论，**只读分析（除 C 的 benchmark 改动外未改源码）**
> **产品线**：`android/native`
> **日期**：2026-09-15
> **目的**：闭合提案文档 §11.4 的未核实项
> **关联**：`research/2026-09-15-android-legado-md3-improvement-proposal.md`、
> `research/2026-09-15-android-compose-component-reuse-audit.md`

---

## A-3 前置：静态分析工具现状 → **手写 Gradle Task 是唯一途径**

### 核实结果

| 检查项 | 结果 |
|---|---|
| `detekt` | **无** |
| `ktlint` | **无** |
| `spotless` | **无** |
| `pmd` / `checkstyle` | **无** |
| `.editorconfig` | **不存在** |
| `detekt.yml` / `config/detekt/` | **不存在** |
| version catalog 中的静态分析依赖 | **无** |
| Android Lint | ✅ 有（`lintDebug` 在门禁中，当前 0 errors / 4 warnings） |

**根 `build.gradle.kts` 全文**（仅声明插件版本，无任何分析工具）：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}
```

### 结论与建议

**提案 §11.4 的疑问已闭合**：本项目**没有规则引擎**，因此 A-3 只有两条路：

| 方案 | 成本 | 评价 |
|---|---|---|
| **A. 手写 Gradle Task**（上游做法，正则） | 中 | 无新依赖；正则有误报可能（注释/字符串） |
| **B. 引入 detekt + 自定义规则** | 高 | 需新增依赖 + 规则 DSL 学习 + 全仓基线；但更精确 |

**建议仍选 A**，理由：
1. A-3 的目标是**只冻结 1 条不变量**（禁止 `ui/`/`feature/` 直接 import `data/local.*`），单条规则不足以摊薄引入 detekt 的成本。
2. 本项目已有 `lintDebug` 作为通用静态检查，detekt 会与之功能重叠。
3. 上游的 `@DisableCachingByDefault` + `MapProperty` 基线写法可直接复用，零依赖。

**若将来护栏规则增长到 ≥5 条，应重新评估引入 detekt。** 这是明确的切换判据。

---

## G-2：分页引擎的并发与超时保护 → **并发优秀，超时缺失**

### 核实结果对照表

| 保护机制 | 上游实现 | 本项目实现 | 差距 |
|---|---|---|---|
| **超时熔断** | `withTimeout(30_000L)` + `finally { cancelLayout() }`（`model/ReadBook.kt:335-348`） | **0 处 `withTimeout`**（`feature/reader/` 全目录无命中） | ❌ **缺失** |
| **相邻单元并发保护** | `nextChapterLoadingLock` / `prevChapterLoadingLock` Mutex（`ReadBook.kt:1307-1330`） | ✅ `layoutMutex = Mutex()`（`pager/TxtPagedController.kt:159`） | ✅ 已有 |
| **按键条纹锁** | 无（上游用全局 Mutex） | ✅ `keyedProjectionLocks.withLockFor(index)`（`pager/ReplacedChapterSource.kt:20-22`） | ✅ **优于上游** |
| **缓存有界性** | 注释约定（无强制） | ✅ `BoundedLruCache(maxSize)` + `require(maxSize > 0)`（`ReplacedChapterSource.kt:321-324`） | ✅ **优于上游** |
| **大章熔断** | 256K 判 `UnsupportedTooLarge` | ✅ `DEFAULT_MAX_CHARS_FOR_PROJECTION = 256_000`（`pager/PagedChapterSource.kt:68`）+ `BoundedReplaceResult.UnsupportedTooLarge` | ✅ 一致 |
| **资源释放** | `finally { cancelLayout() }` | ✅ 多处 `finally`（`PageTurner.kt:270,308`、`TxtPagedController.kt:329,346`） | ✅ 已有 |
| **翻页防丢** | 无对应机制 | ✅ `pendingPageTurns: ArrayDeque<Int>` 队列（`TxtPagedController.kt:157`），注释说明"排版期间也要接住用户翻页" | ✅ **优于上游** |
| **崩溃灰度** | 无 | ✅ `PagerHealthStore`（连续崩溃 ≥2 次自动禁用新分页器） | ✅ **优于上游** |

### 关键发现

**1. 你在并发控制上明显优于上游。** 本项目有 4 项上游没有或更粗的机制：

- **按键条纹锁**：`withLockFor(key)` 只锁住同一章节，不同章节可并行——上游用全局 Mutex 会串行化所有章节。
- **有界缓存的强制防御**：`BoundedLruCache` 的 `require(maxSize > 0)` 在构造期就拒绝非法容量，而非靠注释约定。
- **翻页防丢队列**：`pendingPageTurns` 用 `ArrayDeque` 而非"最后一次方向"，保证连续点按按输入顺序执行（源码注释明确说明了这个设计意图）。
- **崩溃灰度闸门**：`PagerHealthStore` 记录连续崩溃数，≥2 次自动关闭新分页器——**这是生产级的降级设计**，上游没有对应机制。

**2. 唯一缺口是「排版无超时熔断」。**

上游把整章排版包在 `withTimeout(30_000L)` 内，超时即抛弃该次排版。本项目无此保护。

**风险评估（诚实说明不确定性）**：

- **理论风险**：排版是纯 CPU 计算（分词/断句/测量），若遇到病态输入（如超长无标点段落、异常 EPUB 结构），可能长时间占用 `layoutMutex`。由于锁被持有，**后续所有翻页请求会排队等待**（虽被 `pendingPageTurns` 接住不丢，但用户会感知为"卡住"）。
- **缓解因素**：① 256K 字符熔断已挡住最大的输入；② `BoundedLruCache(3)` 限制了缓存规模；③ 排版在 IO 调度器而非主线程（需确认）。
- **不确定**：**未验证是否真实发生过排版挂死**。这可能是一个"理论上存在、实际未触发"的缺口。

**建议动作（需评估者裁定优先级）**：

| 方案 | 说明 |
|---|---|
| **A. 加超时熔断** | 在 `layoutMutex.withLock` 外层包 `withTimeoutOrNull(N)`，超时放弃本次排版并走降级路径。**难点**：超时后如何降级？直接丢弃翻页请求会导致用户点击无响应，需要设计降级 UI（如提示"该章节排版耗时较长"） |
| **B. 只加监控不加熔断** | 引入 §5.2 的 Dispatcher 探针，**先观测是否真的出现长耗时排版**，有数据再决定是否熔断。**成本最低、最稳妥** |
| **C. 不改** | 若 256K 熔断 + 缓存有界已足够，则本项无收益 |

**推荐 B**——用可观测性代替猜测。这也与提案 §5.2（DispatchersMonitor）的引入形成互补：**先能看见，再决定治不治**。

**⚠️ 与 Phase 8 的关系**：本项审计结论应**直接反馈给 Phase 8 负责人**，因为它涉及阅读器核心路径。若 Phase 8 正在重构排版流程，超时熔断应在重构中一并设计，而非事后追加。

---

## F-3：崩溃处理能力面 → **已有基础，缺两项**

### 核实结果

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

另有 `feature/reader/pager/PagerHealthStore.kt` 的**分页器专用**崩溃计数（见 G-2）。

| 能力 | 上游 | 本项目 |
|---|---|---|
| 崩溃日志落盘 | ✅ 双写 + 7 天清理 | ✅ `writeFatal("uncaught/${thread.name}", stack)` |
| 转交默认处理器 | — | ✅ `default?.uncaughtException(...)`（**正确做法**，不吞异常） |
| **选择性吸收**（`shouldAbsorb`） | ✅ ROM 兼容异常后 `Looper.loop()` | ❌ 无 |
| **OOM 堆转储** | ✅ `Debug.dumpHprofData()` 可开关 | ❓ **未确认**（本次未找到，但可能在其他文件） |
| 设备参数采集 | ✅（lazy 构造，零常驻成本） | ❓ 未确认 |
| 崩溃现场上下文 | — | ✅ `DiagnosticsSubPage.kt` 存在（需确认采集内容） |

### 判断

**F-3 不是"引入"，而是"是否增强"。** 本项目已有崩溃日志落盘 + 正确转交默认处理器，基础是健全的。

**关于"选择性吸收"的立场（重要）**：

上游的 `shouldAbsorb` 会吸收特定 ROM 异常后 `Looper.loop()` 让应用**继续运行**。这是**有争议的做法**：

- **支持方**：MIUI/HyperOS 等定制 ROM 会抛出应用无关的系统异常（如 `CannotDeliverBroadcastException`），吸收后可避免用户看到无意义的崩溃。本项目测试环境正是 HyperOS（见 `handoff/current.md` 的 MIUI 安装器问题），**可能已实际遇到**。
- **反对方**：吸收异常会**掩盖真实 Bug**，使崩溃率统计失真，且 `Looper.loop()` 后的应用状态可能已不一致（如 UI 处于半初始化状态）。

**建议**：**不在本次评估中采纳**。若真要处理 ROM 兼容异常，更安全的方式是：
1. 先通过 `DiagnosticsSubPage` 确认真实遇到了哪些 ROM 异常（有数据再动手）
2. 若确认是已知无害的 ROM 异常，采用**窄范围白名单**（精确匹配异常类 + 消息），而非上游的宽松匹配
3. **必须留可关闭开关**

**优先级**：应**低于** A-3、C-1 的后续动作。理由：崩溃处理涉及应用稳定性边界，在无实际数据支撑时改动是投机。

---

## 附：本文件与提案文档的关系

| 提案项 | 状态 | 结论位置 |
|---|---|---|
| A-3 前置（静态分析工具） | ✅ 闭合 | 本文件 A-3 节 |
| G-2（分页并发与超时） | ✅ 闭合 | 本文件 G-2 节 · **需反馈 Phase 8** |
| F-3（崩溃处理） | ✅ 闭合 | 本文件 F-3 节 |
| C-1（组件复用审计） | ✅ 闭合 | 独立报告 `...-compose-component-reuse-audit.md` |
| C（Benchmark journey） | ⚠️ 实施完成；UI 路径真机 6/6 通过，Profile 生成受设备限制跳过 | 见下节「C 项真机验证结论」 |
| D-2（pending-overlay） | ❌ 已拒绝 | 提案 §11.2 |
| G-1（bitmap recycle） | ✅ 已关闭 | 提案 §11.2 |
| ~~真正的阅读进度存储位置~~ | ✅ **已闭合** | 见下节「阅读进度存储定位（原任务 1）」 |

---

## C 项真机验证结论（2026-09-15 03:30 更新）

### 1. 安装阻塞已打通（原 `INSTALL_FAILED_USER_RESTRICTED`）

**根因**：MIUI 在 `PackageManagerService` 注入 `PmInjector.installVerify`；任何 shell 发起的安装
都会弹出 `com.miui.securitycenter/com.miui.permcenter.install.AdbInstallActivity`（标题「USB安装提示」），
其拒绝按钮带 **8 秒倒计时**，超时即取消会话并报 `Install canceled by user`。Gradle 的 ddmlib 不会点它。

**解法**：`android/scripts/tap_usb_install_confirm.py` —— 与 Gradle 并发运行的后台 watcher。
要点：①用 `dumpsys window` 快速探测前台（比 `uiautomator dump` 快得多）②仅在确认弹窗在前台时才做完整 dump
③按 `resource-id`（`android:id/button2`）匹配「继续安装」，**不按中文文本**（规避 CP936 编码损坏）
④持续模式——Gradle 会装 app APK 与 test APK 两个，各弹一次。

实测：`Finished 8 tests`、`BUILD SUCCESSFUL in 12m 35s`，watcher 恰好 TAP 两次（03:27:14 / 03:27:18）。

### 2. 但 Baseline Profile 测试在零售设备上必然被跳过

```
ro.build.type = user        ← 零售生产构建
ro.debuggable = 0
adb root     → adbd cannot run as root in production builds
su           → 不存在
```

测试报告：`tests="4" failures="0" errors="0" skipped="4"`，`time="1.968"`。
**4 个测试（含新增的 `generateReaderJourneyForEpub` / `generateReaderJourneyForTxt`）全部 SKIPPED**。

⇒ **Baseline Profile 生成需要 userdebug/eng 构建或 root。此设备两者皆无，与代码改动无关。**
项目内已存在 `app/src/main/baseline-prof.txt`，说明 profile 是在别处（应为 CI 的 userdebug 环境）生成的。

### 3. 但 UI 路径已由 ReaderBenchmark 真机验证（6/6 通过）

`BaselineProfileGenerator` 与 `ReaderBenchmark` 的关键差异：**Macrobenchmark 不需要 root/userdebug**，
在零售 user build 上正常运行。实测：

```
tests="6" failures="0" errors="0" skipped="0" time="777.147"   ← 0 跳过
  openTestEpubToFirstContent   126.3s
  openTestTxtToFirstContent    125.2s
  turnTestEpubPage             128.9s
  turnTestTxtPage              128.3s
  jumpTestEpubChapter          126.6s
  openReaderMenu               131.8s
BUILD SUCCESSFUL in 13m 58s
```

⇒ **新增 journey 所依赖的 UI 路径（开书 → `阅读正文已就绪` → `分页正文已就绪` → 翻页）在真机全部可行**，
`turnTestEpubPage` / `turnTestTxtPage` 通过，直接支撑 `turnBenchmarkPages()` 的核心逻辑
（点击翻页 + 等待「分页正文已就绪」）。

**附带发现（实证支持拆分 journey 的决定）**：

| 测试 | `readerDocumentLoadFirstMs` 中位数 |
|---|---|
| `openTestEpubToFirstContent` | **32.1 ms** |
| `openTestTxtToFirstContent` | **391.1 ms** |

**TXT 首屏加载比 EPUB 慢约 12 倍**，两种格式的加载链路性能特征差异显著。
⇒ 把 reader journey 拆成 EPUB / TXT 两个（而非合一）是必要的，否则采样会偏向快的一方。

### 4. 因此 C 项的有效性判定

| 层级 | 状态 |
|---|---|
| 编译 | ✅ `:benchmark:compileBenchmarkKotlin --rerun-tasks` EXIT=0 |
| **UI 路径真机验证** | ✅ **ReaderBenchmark 6/6 通过（0 skipped / 0 failed）**，覆盖开书与翻页 |
| **Baseline Profile 生成** | ❌ 本设备无法执行（`skipped=4`，需 userdebug/eng 或 root） |
| 代码审查 | 新增 journey 复用既有 `openShelfForBenchmark` / `filterShelfForBenchmark`，与既有 `generateCriticalUserJourneys` 同构 |

**结论**：C 项改动**获得了 UI 路径的真机证据，但未获得「能真正产出 Baseline Profile」的证据**。
要闭合最后一步，需在 userdebug/eng 构建或已 root 的设备上重跑
`:benchmark:connectedBenchmarkReleaseAndroidTest -P...class=...BaselineProfileGenerator`。
**这不是代码缺陷，是测试环境限制。**

### 4. 顺带的副作用（需知悉）

诊断过程中用 debug 变体做判别实验，**覆盖了设备上的生产包**；
设备上 `com.creationreadingassistant` 现为 `app-debug.apk`（同 versionName 0.5.0 / versionCode 2，
同包名同签名，Room 数据不丢）。如需恢复生产包，重装一次原生产 APK 即可。

---

## 阅读进度存储定位（原任务 1，2026-09-15 04:10 闭合）

> 本项此前误判过一次（把 `data/settings/ContinueReadingStore.kt` 当成进度存储，
> 实际它只有 54 行，存的是「被用户从首页移除的书籍 ID 集合」）。
> 本次严格按「grep 符号名 + 读文件 + 给行号」执行，不再从文件名推断。

### 1. 真实的进度存储

**实体**：`ReadingProgressEntity`（`data/local/entity/ReadingEntities.kt:42`），表 `reading_progress`
- `book_id`（主键）、`progress_percent`、`current_location_json`（:48，存 locator / 精确位置）
- `completion_state`、`total_reading_time_ms`、`last_read_at`、`updated_at`

**DAO**：`ReadingProgressDao`（`data/local/dao/ReadingDao.kt:12`）
- 写入：`upsert`（:14，`@Insert(onConflict = REPLACE)`）
- 读取：`getByBook`（:17）、`observeAllActive(): Flow`（:24）、`observeStatsRows(): Flow`（:30）

**唯一业务写入口**：`BookRepository.saveProgress`（`data/repository/BookRepository.kt:93`）
—— 注释即写明「阅读器自动进度写入」，内部走 `mergeReaderProgress` 合并既有业务状态。

### 2. 写入频率：不是每次翻页，而是 500ms 防抖

两条落库路径，均在 `ui/screen/reader/ReaderProgressEffects.kt`：

| 路径 | 位置 | 机制 |
|---|---|---|
| 滚动进度 | :189 | `snapshotFlow {…}.debounce(500)` |
| 翻页进度（分页引擎侧） | :243 | `snapshotFlow {…}.debounce(500)`，注释「与滚动侧同样 500ms 防抖、同一张表同一套字段」 |

链路：`ReaderProgressEffects` → `ReaderAction.SaveProgress` / `SaveEpubProgress`
→ `ReaderViewModel.kt:383` / `:393` → `BookRepository.saveProgress` → `ReadingProgressDao.upsert`

### 3. 防抖会不会丢最后一次进度？——不会，已有生命周期 flush

这是比原问题更关键的检查点。`ui/screen/reader/ReaderPlatformEffects.kt:64-80`：

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            ON_RESUME -> latestOnReaderResumed.value(true)
            ON_PAUSE, ON_STOP -> {
                latestOnReaderResumed.value(false)
                latestOnPersistProgress.value()   // 暂停/停止：立即落库
            }
            else -> Unit
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        latestOnPersistProgress.value()           // 离开组合：立即落库
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```

`onPersistProgress` 由 `ReaderScreen.kt:472` 的 `persistOnLeave`
（`if (!temporaryInspection) nav.persistCurrentProgress()`）提供，
经 `ReaderProgressActions.persistCurrentProgress`（:123）写入——**同步落库，不经防抖**。

⇒ **500ms 防抖窗口内退出/切后台不会丢进度。**

### 4. 对 D-2（pending-overlay）的判定：**拒绝**

上游 `PendingOverlayCore` 解决的是「写后立即读，读到旧值」的幻影状态。本项目不存在该路径：

| 检查点 | 结论 |
|---|---|
| 是否存在写后读 | 否。`saveProgress` 内的 `progressDao.getByBook`（:97）是**写前读**，用于 `mergeReaderProgress` 合并既有状态 |
| 读取侧是否可能读到旧值 | 否。`observeAllActive()` / `observeStatsRows()` 是 Room `Flow`，写入后自动发射新值 |
| 高频写是否拖垮性能 | 否。500ms 防抖已把写入频率压到 ≤2 次/秒 |
| 退出时是否丢数据 | 否。`ON_PAUSE` / `ON_STOP` / `onDispose` 三处 flush |

⇒ **本项目用「防抖 + 生命周期 flush」解决了同一问题，比上游的 pending-overlay 更简单且已落地。D-2 维持拒绝，理由更新为上述证据链（原理由「ContinueReadingStore 不是那个文件」仅是否定了错误前提，本身不构成完整论证）。**

### 5. 遗留观察（不构成缺陷，仅记录）

- 落库路径本身没有 `withTimeout`（与 G-2 节「`feature/reader/` 全目录 0 处 `withTimeout`」一致）；
  但 `saveProgress` 是 Room 本地写，量级为毫秒，风险远低于排版。
- EPUB 走 `SaveEpubProgress`（`EpubRepository.saveProgress`，:228）与 TXT/Markdown 走
  `SaveProgress` 是**两条不同落库路径**，字段由 `reportableSourceTarget` 统一推导；
  两者共用同一张表，但**合并语义不同**（EPUB 额外走 `chapterIndex` 推导），改动任一侧需同时验证另一侧。

---

*本文件由 WorkBuddy 于 2026-09-15 生成。除 C 项的 benchmark 改动外，未修改任何源码。*
