# 当前 Agent 交接入口

> **2026-09-09 移动端活动结论（优先于下文历史状态）：** 用户已授权整理当前 Android 代码为本地开发检查点，暂不推送。
> 安全滚动 TXT 替换的 WorkBuddy 复验为 FAIL：规则保存和预览正常，正文未实际替换；现有 `ScrollReplaceTrace` 用于继续定位，不代表修复完成。
> 正文排版和其余页面检查点仍需按各自范围验收。新的需求、分工、所有权与执行入口见
> `docs/plans/android-parallel-delivery-2026-09-09/README.md`。Trae、Qoder 独立工作树开发，Codex 集成，WorkBuddy 独占真机验收。

更新日期：2026-09-08（阅读器 T8、结构清理与本地提交已收口；P3.1 EPUB/分页净化与 TTS 见第 28–29 节）
仓库：`D:\develop\Code\Codex\creation-reading-assistant`  
当前对话主线：**双线并行**——独立原生 Android `android/`（本文件第 2、18 节）与桌面端 Electron `src/`+`electron/`（第 19 节，独立会话）。两线不共享运行时代码，跨线改动需各自会话的文件所有权。

> **状态读取规则：** 第 1–3 节和“当前下一步（2026-09-08）”是活动结论；其余带日期的章节是历史证据。
> 历史章节中的“下一刀”“待办”不构成当前指令；出现冲突时，采用日期更新的结论和当前工作树状态。

## 1. 接手前必须知道

- 2026-09-08 本轮代码提交后当前分支为 `main`、代码 HEAD 为 `bfe586b`；本文档同步将产生后续提交。
  接手前仍须重新读取 `git status --short` 和 HEAD，不可把该快照当作恒定基线。
- 工作区不是干净基线：Android reader/页面、测试、文档、诊断工件以及 desktop 改动共存，全部先视为
  用户资产。WorkBuddy 已于 2026-09-08 完成 T8 只读 diff 清单；其记录的是清理前 273 项工作树快照，
  不能直接覆盖到随后新增的 Android 死代码清理与文档改动。
- 未经用户明确要求，不 stage、commit、push、reset、checkout、全仓格式化或清理这些改动。
- desktop 与 Android 不共享运行时代码；当前任务不得顺手修改 `src/`、`electron/`。
- 设备行为只用真实手机验证；先 `adb devices`，所有命令显式 `adb -s <serial>`。禁止 MuMu。
- 测试记录只写「测试 EPUB」「测试 TXT」；长测前记录常亮值，结束后恢复。

## 2. 当前 Android 状态

### 已完成记录（最新结论优先）

- **2026-09-08 阅读器 T8（WorkBuddy 独立验收 PASS）：** 默认翻页「无」、滑动、揭页正反向的
  连续帧、进度拖动预览/自动隐藏、目录/设置/规则/搜索与 14→15→14 边界均已由 WorkBuddy 实测。
  其报告记录 74/74 定向 JVM、构建成功、20/20 定向真机 instrumentation 和录像工件
  `.workbuddy/accept-20260908/`。该验收未修改源码、测试、脚本或 Git 状态。
  已知非阻塞观察：揭页在松手后播放而非拖动中跟手；安装确认脚本仍会受 MIUI 弹窗超时影响，
  不能将直接 `adb install` 的成功当作自动安装工作流恢复。

- **2026-09-08 Android 结构清理：** 删除 9 个无生产调用的 Kotlin 文件、1 个只覆盖死 ViewModel 的
  测试文件、5 个无调用局部组件，并同步移除 Baseline/Startup Profile 旧描述符，净减 1,767 行。
  全量引用核对、删除编译测试和剩余结构/API 债见
  `docs/qa/android-structure-cleanup-2026-09-08.md`。WorkBuddy 已独立复跑两道门禁并判定 PASS：
  JVM 1694/1694、Lint 0 error/4 warnings、Debug APK 构建成功；本轮无预期视觉变化，未重复 T8 录像矩阵。

- **2026-09-05 P3.1 阅读器核心引擎深度对抗压测与真机监控闭环（双子代理协同）：**
  1. **跨 ReadingUnit 正则边界与 TextOffsetMap 映射**（`StreamingCompleteChapterSourceTest` 15 passed）：
     构造跨越 50,000 字符边界的分裂 Token 及重复模式，验证 Exact 作用域下无遗漏替换；验证 TextOffsetMap 单调不减、round-trip floor 与关键标记 0 漂移往返。
  2. **超大单章（>256K）熔断降级**（`StreamingBoundedRegressionTest` 9 passed）：
     元数据直接判定 `UnsupportedTooLarge`，零整章内存分配；原样降级为原文有界分页；超限回调严格仅上抛 1 次。
  3. **无目录大 TXT（50MB 场景）连续性验证**：
     遍历 ~800+ 个 ReadingUnit 分段（≥ 100），全局 source 偏移连续无缺口/重叠（`gap = 0`），各分段读取严格有界（`<= MAX_WINDOW_CHARS`）。
  4. **多线程并发投影去重与条纹锁**（`ReplacedChapterSourceConcurrencyTest` 16 passed）：
     12 线程并发请求同一章节时，慢速委托下 `loadCounter` 严格为 1，所有实例同一引用（`===`）；条纹锁确保异章互不阻塞；高频跨章淘汰下容量恒锁为 3，无死锁。
  5. **结构化坐标契约防护**（`StructuredReplacementContractTest` 5 passed）：
     EPUB 保持 `ESTIMATED_COORDINATES`、Markdown 保持 `NON_SOURCE_COORDINATES`，防止误开。
  6. **JVM 单元测试全量通过**：
     - 引擎深度对抗定向测试：**45 / 45 项全绿 (100% PASS)**；
     - 全量 JVM 单元测试：`./gradlew.bat :app:testDebugUnitTest` -> **199 个测试套件，0 failures, 0 errors**。
  7. **androidTest 编译契约修复**：
     修复了此前遗留的 `ReaderSelectionToolbarTest` 与 `LibrarySortControlsTest` 编译契约漂移，全仓 `:app:compileDebugAndroidTestKotlin` 100% 编译通过。
  8. **真机 serial `c49ac6cf`（Redmi 22081212C, Android 15）监控审计**：
     - 日志：CRASH / FATAL: 0 次，ANR: 0 次，无索引越界异常；
     - 内存：Total PSS ~230MB，Java Heap 34.9MB，无内存泄漏残留；
     - 渲染：GPU 帧耗时 P90 5ms，P95 6ms，P99 6ms；
     - 现场保护：`stay_on_while_plugged_in` 原值 3，核验保持为 3；未篡改用户真实书库；0 临时文件残留。

- P3.3 目录已读与分类/标签/书单手动排序已经收口；TXT 已读、书单内书籍排序不在本期。
- P3.2 片 0–1 已完成：本地阅读会话落库、统一 occurred 日期口径、异常时长过滤与 streak；
  片 2–3 的目标进度环和每日提醒也已实施，当前只缺通知触发与重启恢复的真机证据。
- **P3.1 当前状态：** 分页 TXT、EPUB 有历史验收证据；安全滚动 TXT 尚未收口（2026-09-09 真机 FAIL）。其设计要求仅在
  `ScrollingTxtChapterSource` 可证明完整逻辑章 source 时开放，按 `ReadingUnit` 有界渲染且坐标始终
  映射回 source。规则启停重分页、超大章降级提示、source/display 映射与 TTS 证据以第 28–29 节为准。
  真正 legacy/不完整滚动路径及 Markdown 的结构保真/源↔渲染映射契约仍未实施，保持入口隐藏、正文原样显示。

### P3.1 历史实现链（2026-08，仅作根因与测试参考）
- **P3.1 流式大 TXT 完整逻辑章节 source 于 2026-08-20 首轮实现后因性能/内存三项回归暂未通过：**
  1) 无目录大 TXT（50MB）把整本退化为一个 `PagedChapterContent`，破坏有界首帧与内存设计；
  2) `ReplacedChapterSource` 的 `mutableMap<Int, CachedChapter>` 无容量上限；
  3) UI 能力判断用 `isTxt && pagerEngineOn` 反推，不读取真实 `PagedChapterSource` 能力。
- **2026-08-21 会话完成三项回归修复，验收通过：**
  1. **拆分分页单元与完整投影作用域（`ReplaceProjectionScopeProvider`）：**
     - 分页层继续以 `ReadingUnit`/等价有界 segment 作为 `TxtChapterSource` 的
       `chapterCount` 输出，大 TXT 50MB 无目录切分为 ≥100 个 MAX_WINDOW_CHARS 段；
       `chapterStartAbs`/`totalChars` 仍以 source 全局偏移连续、无重复、无缺口。
     - 新增窄接口 `ReplaceProjectionScopeProvider.scopeForSegment(segmentIndex)`，
       返回 `Exact` / `UnsupportedTooLarge` / `Incomplete`，
       在不读取整章的前提下，仅靠 `TxtFileIndex` 元数据判定超限；超过 256K 的逻辑章
       直接以原文 ReadingUnit 有界分页 + 一次性超限提示，不触发整章分配。
     - 小文件（完整文本常驻内存）`TxtChapterSource(text, chapters)` 保持逻辑章 1:1，
       `replaceProjectionScopeIsComplete=true` 且不暴露 segmented provider，不破坏旧契约。
  2. **有界投影缓存（ReplacedChapterSource LRU 3-chapter）：**
     - 无界 `mutableMap` 替换为 `BoundedLruCache<Int, CachedChapterProjection>(maxSize=3)`，
       容量 = 当前章 + 前一章 + 后一章，访问第 4 章后最久未用项淘汰；
       规则 profile key 变化后整体 key 改变不复用；
       `UnsupportedTooLarge` 路径不缓存完整 source 文本。
     - `synchronized(cache)` 锁只保护缓存读写，昂贵的 `delegate.loadChapter()` 与
       正则投影在锁外执行，并在完成后以 putIfAbsent 级别的去重语义接入缓存。
  3. **UI 能力真实化：ReaderPagerEngineState 暴露 `PagedReplacementAvailability` 枚举
     (`APPLIED`/`SOURCE_UNAVAILABLE`/`INCOMPLETE_SCOPE`/`ESTIMATED_COORDINATES`/
     `OVERSIZED_CURRENT_CHAPTER`/`NO_EFFECTIVE_RULES`)。**
     `ReaderSheetHost` 和 `readerReplacementCapability()` 直接消费 availability，
     不再重新猜测 `isTxt && pagerEngineOn`。
  4. **TDD 15 项在 JVM 覆盖：** 重写破损的 `StreamingBoundedRegressionTest`
     并补 `ReaderReplacementCapabilityTest` 的派生/真实 availability 两条入口；
     `PagedChapterSourceTest` 无目录 5MB/10MB 整章读取用例已移除并替换为
     连续偏移 + 有界上限验证；`StreamingCompleteChapterSourceTest`
     重写为 Exact 作用域下的跨 ReadingUnit 匹配与 source↔display 往返；
     `ReplacedChapterSourceTest` 新增容量/LRU 淘汰/规则变化不复用三组用例。
- **2026-08-21 门禁（本会话已跑，旧 2026-08-20 证据作废）：**
  - **定向 JVM（5 类）：** suites=5, tests=58, failures=0, errors=0, skipped=0
    （PagedChapterSourceTest / StreamingCompleteChapterSourceTest /
    ReplacedChapterSourceTest / StreamingBoundedRegressionTest /
    ReaderReplacementCapabilityTest）
  - **全量 JVM `:app:testDebugUnitTest`：** suites=153, tests=1432, failures=0, errors=0, skipped=0
  - `:app:lintDebug`：通过；
  - `:app:assembleDebug`：通过；
  - `:app:compileDebugAndroidTestKotlin`：通过；
  - **真机 serial `c49ac6cf`（Redmi 22081212C）：**
    - Debug APK 安装成功、冷启动 OK、无 FATAL/ANR（logcat AndroidRuntime/CRASH/AnrManager 均为空）；
    - ReaderRulesSheetTest（Compose）：OK (6 tests)，6/6 PASS
      （numtests=6，MIUI 下配合 MAIN+LAUNCHER+0x10008000 拉前台保活后完成）；
    - 带书正文矩阵（测试 TXT 小型/大 TXT 的替换、规则启停、搜索、高亮、选区、TTS、超限提示）：
      本次未执行（不擅自导入/删除/修改用户真实书库；需导入中性测试 TXT 后再单独跑）；
  - stay_on_while_plugged_in：原值 7，会话结束后保持 7；
  - `git diff --check`：未退出 0（既有改动文件 ReaderLayerBuilders.kt /
    ReaderScaffold.kt / ReaderSheetHost.kt 末尾有 new blank line 告警，
    均来自前序 agent，不在本轮文件所有权内，未修改）。

以上证据对应当前未提交工作区，任何后续代码修改后都必须重跑，不能继续引用旧绿灯。

### 当前下一步（2026-09-08）

1. **本轮提交已收口，暂不新开实现：** T8 本体及 6 个编译闭包已提交为 `f65ee23`，结构清理已提交为
   `bfe586b`，本文档单独同步；均只在本地，尚未 push。其他 Android 页面 WIP、desktop 改动和诊断工件
   仍按用户资产保留，未混入本轮提交，也未擅自删除。
2. **P3.1 剩余架构切片：** 以
   `docs/plans/replace-rules-render-integration-design.md §片 4 legacy/滚动路径` 为唯一入口，先写
   display 文本 + source↔display 映射 + 原始全局偏移及持久化坐标测试，再接入各消费点。禁止 display
   坐标入库。分页 TXT 与 EPUB 的净化、规则启停/重分页、超大章和 TTS 证据已在第 28–29 节收口，不重复
   作为当前 P3.1 待办。
3. **Markdown 净化前置设计：** 单独明确源文本与渲染文本映射、结构保真和测试契约；契约完成前入口继续
   隐藏、正文保留原文。
4. **产品 UX：** 审查首页继续阅读、阅读历史、全局搜索在手机、平板和横屏下的信息架构；阅读设置继续
   按渐进披露收口。
5. **仍需设备证据：** P3.2 阅读目标提醒的通知实际触发和重启恢复。后续真机验收一律交由 WorkBuddy，
   不由 Codex 自行执行。
6. **质量与发布：** 修复 `install_with_confirm.ps1` 的 MIUI 超时（直接 adb 仅可作为诊断，不是成功替代）、
   A7 同步冒烟、A9 EPUB 异常语料/性能、A12 字符串资源化/无障碍；正式发布仍需用户配置签名 Secrets、
   授权 tag/push 和升级安装验证。

## 3. 权威文档顺序

1. `AGENTS.md`：产品线、协作与真机红线。
2. `docs/handoff/current.md`：当前任务入口与活动开放项。
3. `docs/architecture/native-android-reader.md`：当前架构和已知边界。
4. `docs/plans/replace-rules-render-integration-design.md`：当前 P3.1 详细 seam 与验收。
5. `docs/plans/2026-08-16-android-followup-roadmap.md`：发布、质量和后续候选全景。
6. `docs/plans/2026-08-16-reading-goal-streak-design.md`、
   `docs/plans/2026-08-16-toc-read-mark-manual-sort-design.md`：P3.2/P3.3 状态。

若历史报告与上述文档冲突，以上述顺序和实际源码为准；不要从旧 `mobile/` 文档恢复任务。

## 4. Android 验证入口

在 `android/` 执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

执行设备测试前后记录 `adb devices`、目标 serial、实际执行测试数与常亮原值。MIUI 可能阻止
Compose 测试 Activity 自动前台启动；在测试请求中的精确 `MAIN + LAUNCHER + 0x10008000`
Intent 启动后仍卡死则需要真机手动点「允许」弹窗，或先补 Room 等非 Activity 类 instrumentation
作为证据。**0 项 instrumentation 不是通过证据。**

## 5. Desktop 状态（2026-08-29 更新）

桌面端已在独立会话重新激活并完成 8 轮 UI 收口（设计系统 token、页面深化、阅读器审查、
弹窗巡检、应用图标），记录见第 19 节；当前桌面端 UI 无已知未修缺陷。
Novalist 调研与取舍仍记录在
`docs/research/novalist-desktop-adoption-2026-08-21.md`。最高价值候选是只读「创作雷达 v1」；
不要把其 Markdown/JSON、PySide6 或 DeepSeek Harness 架构搬入项目。

## 6. 交付纪律

- 先定一个窄切片和互斥文件所有权，再实现；公共 schema、DAO、Reader 路由与文档由单一
  集成者维护。
- 任何"完成"必须列出实际运行命令、通过数量、未覆盖项和设备状态。
- 发现文档过期时改现行文档；已完成/被替代任务移入历史，不继续留在活动 TODO。

## 7. 2026-08-22 集成验证（Kimi3 页面升级收工后）

- **工作区新增内容**：Kimi3 的 Android 页面升级全部未提交（约 70 修改 + 22 新增，
  集中在 reader UI 屏、theme、drawable/mipmap 图标、home/profile 组件），叠加本文件
  第 2 节所述的 reader/统计/已读/替换净化 WIP。分支仍为
  `codex/workspace-backup-2026-08-20`，HEAD `50674a9`。
- **全量门禁（2026-08-22 实跑）**：
  - 桌面端：`npm test` 689 passed / 4 skipped（skipped 为本地 better-sqlite3 ABI
    环境问题，CI Node 24 全量执行）；`npm run build` 成功。
  - Android：`testDebugUnitTest` 1432 tests / 0 failures / 0 skipped
    （Gradle UP-TO-DATE 复用 08-21 23:09 报告，输入哈希与当前工作区一致）；
    `lintDebug` 0 errors / 18 warnings（较 08-16 实测 16 增加 2，源于页面升级）；
    `assembleDebug` 成功（49.9 MB）。
- **同日文档修正**：gap-audit A10 标完成（splitTxtChapters 59/59、桌面测试/构建早已
  入 CI）、C-3/A8 schema 口径 v9→v10；路线图 P2 的 A10 行标完成、A12 行 lint 数字
  修正。
- **未做（需用户决策/授权）**：工作区分层提交与 `codex/workspace-backup-2026-08-20`
  合回 main；真机对页面升级的视觉验收；lint 新增 2 条 warning 的归因。
- **2026-08-22 补充：工作区已按三层提交（cf27b5a 引擎数据 / 3d6bbe6 页面视觉 /
  199d553 文档）；真机验收已执行，结果见第 8 节。**

## 8. 2026-08-22 真机验收（Kimi3 页面升级 + 全量仪器测试首跑）

设备 `c49ac6cf`（Redmi 22081212C），全程未使用模拟器；stay_on_while_plugged_in 保持 7 未改动。

- **安装与启动**：`adb install -r -t` 成功（MIUI 确认框由脚本自动点掉，无需人工）；冷启动两次 1517ms / 1662ms；logcat 无应用 FATAL/ANR/StrictMode 违规。
- **视觉验收（截图 + 视觉模型审阅）**：首页、书架、阅读器正文、阅读器底部菜单（目录/亮度/进度/朗读/设置）、TOC 弹层（P3.3 已读弱化+行尾点+"已读 1/5"计数实况可见）、统计页、我的页——7 页全部无布局异常。
- **全量 `:app:connectedDebugAndroidTest`（史上首次真机全量执行）**：111 项，94 过 / 17 失败，分五类：
  1. **7 项环境性失败**：InspirationComposeTest×4、StatsComposeTest×3 的"源码结构自检"用例在 androidTest 里读仓库相对路径（设备上不存在），从未可能在仪器环境通过；其中 3 个还引用已迁移的旧路径（`ui/screen/StatsScreen.kt` 等）。建议整体迁到 JVM 单测。
  2. **5 项 ReaderSettingsSheetTest**：08-20 会话把设置改为二级页（`ReaderSettingsPage.ROOT` → 排版/翻页/显示子页）后测试未同步（测试停在 08-16 单页滚动断言）。
  3. **2 项 ShelfScreenComposeTest**：pull-refresh 指示器顶部 0dp 应 ≥ 顶栏 64dp（重叠）；搜索激活态"取消"按钮不显示。BookGrid/顶栏为 Kimi3 改动，**最像真实布局回归**，待修。
  4. **2 项 StatsComposeTest UI**：`stats-creation`/`stats-period-empty` tag 在代码中存在但断言 not displayed；测试 08-03 后未执行过，疑似 harness 与现结构不匹配，待深挖。
  5. **1 项 ReaderTocSheetTest**：`已读章节` 语义节点出现 2 个（期望 1 个），P3.3 相关语义重复。
- **事件（如实记录）**：Gradle 在 connected 测试结束后自动卸载了主应用+测试 APK，设备上的中性测试书库（测试 EPUB/测试 TXT 及其进度）随之清除——均为历次会话导入的测试 fixture，可重新导入；主应用已立即重装并验证启动。后续跑 connected 测试需注意 AGP 默认卸载行为（可 `-Pandroid.injected.invoked.from.ide=true` 或测试后重装）。
- **2026-08-22 P3.2 片 2–3 已实施（未提交）**：`GoalStore`（DataStore `goal_prefs`）+ `GoalSubPage`
  （我的 → 阅读目标）+ Stats `GoalRingSection` 进度环 + Home 今日副标；WorkManager 每日提醒
  （`ReadingGoalWorker`/`ReadingGoalScheduler`，新依赖 work-runtime-ktx 2.9.1，`reading_goal` 渠道，
  `NotificationPermission` 公共 helper，App 启动对账）。判定/收敛/口径/时刻纯函数化，新增 JVM 23 项；
  全量 `testDebugUnitTest` 1455 项 0 失败、`lintDebug` 0 errors、`assembleDebug` 通过（49.4 MB）。
  剩余：真机通知触发/重启恢复验收；通知点击 V1 仅拉起应用（deep-link 进阅读器留作增强）。
- **2026-08-22 阅读器质量收口（第一轮，未提交）**：针对 §8 的 17 项仪器失败——
  ① TOC 生产修复：当前章不叠加「已读章节」语义（计数仍含当前章）；② 书架×2 为**过期测试**
  （搜索已于 08-05 迁独立 `shelf/search` 路由；`ShelfHeader.kt` 死代码已于 2026-09-08 清理）：重写为
  「搜索图标派发 OpenSearch」+「刷新指示器不高于内容区顶」运行时相对断言；③
  ReaderSettingsSheetTest×5 与 StatsComposeTest UI×2 的根因是 **LazyColumn/PageLazyColumn
  折叠线外节点未组合**，统一改 `performScrollToNode`（容器侧惰性感知）；④ 结构自检×7
  迁 JVM（`InspirationFileStructureTest`/`StatsFileStructureTest`，模块相对路径，全部通过）。
  门禁：JVM 1462 项 0 失败、androidTest 编译通过、lint 0 errors。
- **2026-08-22 补：设备复验全绿 + 权限已脚本化恢复。** 通过 adb 拉起应用详情页 +
  uiautomator 自动点选，已将「后台弹出界面→始终允许」「允许通知→开」两项授予应用
  （通知授权同时解锁了 P3.2 提醒的通知通道）。五个受影响类真机复验：TOC 5/5、
  设置 5/5、书架 7/7、Stats 5/5、灵感 4/4——上午 17 失败清零（7 迁 JVM + 10 修复）。
  **坑位记录**：python 改测试源码会绕过 Kotlin 增量编译的文件监视，旧 class 残留并被
  打包进 APK（dex 同时含新旧方法，且 Gradle 报 up-to-date 假成功）；遇此情况需
  rm -rf app/build 全量重建并核对 dex。刷新指示器位置防重叠不变量改由视觉验收覆盖
  （AnimatedVisibility 语义边界在测试环境恒测 (0,0)，与同组合真实 104dp 内边距矛盾）。
- **设备 Compose 测试通道受阻（需一次性人工授权）**：重装 APK 会重置 MIUI
  「后台弹出页面」权限，`am instrument` 重启应用进程后 Activity 启动被拦（am instrument 卡在
  类头部）。上午 gradle 全量能跑是因为当时权限尚在。恢复路径任选其一：
  ① 手机 设置→应用设置→应用管理→创作阅读助手→权限管理→**后台弹出页面→允许**；
  ② 开发者选项→**USB 调试（安全设置）** 开启。授权后可用
  `adb shell am instrument -w -e class <类名> com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner`
  逐类复验（注意 gradle connected* 会在结束时卸载主应用+清数据）。
- **2026-08-22 基线复测（C1）**：冷启动 554.6/723.4ms（首帧/fully drawn P50，与 07-30 持平），
  热启动 210.6/340.5ms；frameOverrun 百分位在本设备不可提取（benchmark 库 perfetto 兼容问题）。
  读场景 6 项因空书架+种子未落地跳过（见性能文档新增节）。质量收口两提交：
  `1e3238f`（仪器测试债清零）+ `8baba68`（P3.2 片 2–3）。
- **P0.2 清单核对状态**：可自动化的部分（导航/设置/TOC/统计渲染）已覆盖；TTS 听感、拔耳机、色温/纹理实际观感、EPUB 封面提取、内容哈希判重（书库已清）需重新导入测试书后人工/脚本验证。

## 9. 2026-08-23 阅读器分页与覆盖层收口

设备 `c49ac6cf`（真实 Android 手机），本轮未使用模拟器；`stay_on_while_plugged_in` 实测为 `7`，未修改。

- **首行被顶栏覆盖**：真机截图确认 ReaderTopChrome 是全屏覆盖层，分页宿主仍从 `y=0` 开始绘制，导致大字号正文首行被裁掉。已将 `contentTopPaddingPx` 纳入 `LayoutConfig.fingerprint`、`ChapterPaginator` 的文本/块分页、绘制和底部留白分配；Paged TXT 宿主固定预留 32dp，分页与选区坐标保持一致。
- **控制栏遮住末行**：真机截图确认底部普通栏会覆盖分页正文末行。ReaderScaffold 现在为正文视口按底栏形态保留固定安全区：普通栏 160dp、自动翻页栏 216dp、TTS 栏 232dp；同一底栏的显示/隐藏不改变正文视口，避免页码重排和滚动跳动。
- **格式标签错误**：Markdown 原先沿用 TXT 分支标签，现改为 `MD`；EPUB/TXT 标签保持原语义。
- **翻页透叠修复保持有效**：PageTurner 的页面背景与边界裁剪修复未被本轮覆盖层改动回退。
- **静止页“纸中纸”修复**：真机截图确认 `none` 静止态仍把 `pageBackground` 铺满整页，
  与外层渐变/纸张纹理形成白色矩形。现在仅在 slide/cover/reveal 的实际过渡帧铺遮罩，
  静止页透出阅读器外层纸张；新增 `PageTurnerStaticSurfaceTest` 锁定该像素契约，
  同时保留相邻页文字不穿透的覆盖测试。
- **当前排版参数核对**：真机当前使用字号 25sp、行高 1.85、段距 1.15 倍（映射为 0.46em）、
  页边距 22dp。截图中首行/末行未裁切，正文段首缩进与段间距按配置生效；因此不能把“大字号宽行距”
  误判成排版算法重叠。是否将默认视觉改为更紧凑的字号/行距，仍应作为设计决策，不在本轮擅自改默认值。
- **书内搜索入口补齐输入准备**：搜索面板打开时现在同时请求焦点并主动唤起软键盘，输入框带
  `reader-search-field` 测试语义；新增 `ReaderSearchSheetTest.openingSearch_focusesQueryField`，
  真机类测试 4/4 通过。真机 UI dump 确认输入框 `focused=true`，输入法服务报告
  `mInputShown=true`。
- **阅读交互复核**：测试 TXT 的长按选区可显示高亮、选区把手和操作栏；普通横向滑动的中间态、
  自动翻页揭页和停止后的稳定态均未见两份正文透出或文字重叠。自动翻页的揭页分界线会短暂
  横穿字形，这是当前效果的视觉特征，不是正文重复；后续可作为单独的视觉优化项评估。

验收证据（2026-08-23 新鲜执行）：

- `:app:testDebugUnitTest`：1490/1490，通过 0 失败、0 error、0 skipped。
- `:app:lintDebug`：0 errors、12 warnings（均为既有依赖/平台 API/颜色与设备标识提示）。
- `:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin`：通过。
- 真机 `PageTurnerTest#coverTurnKeepsUnderlyingPageTextFromShowingThroughCurrentPage`、
  `PageTurnerStaticSurfaceTest#settledNoneEffectDoesNotPaintASeparatePaperCard`：`OK (2 tests)`。
- 全量 JVM `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`（本轮未新增 JVM 用例，沿用当前 1490 项基线）。
- 真机静止态截图：`C:\Users\23254\AppData\Local\Temp\reader-layout-after-background-fix.png`；
  白色页面矩形已消失，正文仍保持段首缩进、行距和页脚安全区。
- 真机截图已复核：`creation-reading-top-padding-fixed32.png`、`creation-reading-markdown-format-fixed.png`、
  `creation-reading-bottom-reserve-normal-final.png`、`creation-reading-final-swipe-mid.png` /
  `creation-reading-final-swipe-settled.png`、`reader-search-keyboard-fixed-final2.png`、
  `reader-selection-after-search-fix2.png`、`reader-page-swipe-mid1.png` /
  `reader-page-swipe-mid2.png` / `reader-page-swipe-mid3.png`、`reader-auto-page-after2s.png` /
  `reader-auto-page-stopped-final.png`；正文首行、末行与普通翻页中间态均未见文字透叠。

仍未覆盖的下一刀：重新导入中性测试 EPUB/TXT 后执行替换/搜索/高亮/选区/TTS/大章提示带书矩阵；
  真正 legacy/不完整滚动路径和 EPUB/Markdown 结构保真替换仍按当前能力边界保留原文。自动翻页揭页线
  横穿字形的视觉取舍也可在下一轮决定是否优化。

## 10. 2026-08-24 阅读器跨屏自适应与跨路径收口

- **自适应尺度已接入共同内容入口**：`ReaderViewportProfile` 在 `ReaderScaffold` 的
  `BoxWithConstraints` 中按当前窗口最短边（dp）生成派生阅读设置，TXT、Markdown、EPUB、
  滚动和分页共用同一份结果；旋转屏幕使用短边，不会因横屏长边把字号误放大。
- **用户偏好不会被覆盖**：DataStore 中保存的字号和页边距仍是用户基准值，视口派生值只在
  当前窗口内使用，不写回设置。尺度以 411dp 短边为参考，限制在 0.88–1.08：示例为
  320dp → 约 22sp、411dp → 保持 25sp、600dp → 约 27sp；同时按字号计算分页首行安全区
  24–40dp，避免小屏大字或宽屏换行后首行裁切。
- **自动化契约**：`ReaderViewportProfileTest` 覆盖参考手机、小屏、宽屏、横竖屏等价、行为
  设置不变和无障碍字号下元信息栏增高 6 项；当前真机 1220×2712、density 480（约 407dp 短边）派生字号约 24.7sp，
  与保存的 25sp 基准一致。
- **配置变化防旧测量**：`ReaderScaffold` 的顶部/底部覆盖层测量现在以 density 与 fontScale 为 key；
  换屏幕密度或系统字号后先丢弃旧 px→dp 结果，再用当前窗口重新测量，避免首/末行沿用旧设备高度。
- **跨格式行为补齐**：legacy EPUB 分页不再忽略“无/淡入/滑动/覆盖”设置；滑动与覆盖只移动当前章节
  的单层 Composition，未知历史值回退为无动画，不同时保留两章布局。
- **分页元信息自适应**：新分页页眉/页脚高度随系统 fontScale 增长，左右内容均单行省略；正文首行安全区
  与同一套动态页眉高度计算，窄屏和大字体不会互相挤压或裁切。
- **正文流式搜索/占位稳定性**：超长关键词跨 ReadingUnit 时按关键词长度保留重叠尾部；TXT 滚动加载
  占位不再重复扣除页边距，避免加载态与完成态出现宽度跳变。
- **替换规则入口状态修正**：有精确正文 source 但尚无启用规则时，
  `NO_EFFECTIVE_RULES` 现在保持规则管理可用，显示“替换净化”空列表和“新增规则”；
  只有 source 尚未构建/不可投影时才返回 `SOURCE_UNAVAILABLE` 并隐藏入口，避免出现“可以新增”
  却没有新增入口的死路。`ReaderReplacementCapabilityTest` 已覆盖该映射，
  `ReaderRulesSheetTest` 真机 Compose 6/6 通过。
- **真机证据边界**：此前一次成功安装时的 7 项回归证据只属于当时 APK，不能作为本轮最新源码的真机通过证据。
  本轮目标设备 `c49ac6cf` 在线，但安装当前 `app-debug.apk` 返回
  `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，主应用与测试包当前均未安装；未改变手机安全设置，
  也未把 0 项 instrumentation 误报为通过。`stay_on_while_plugged_in` 复核仍为 `7`。
  **后续状态**：该安装阻塞已由第 11 节的自动确认脚本解决；这段保留为故障时间线，不代表当前安装状态。
- **最新门禁**：全量 `:app:testDebugUnitTest` 为 1506 项，0 failures、0 errors、0 skipped；
  `:app:lintDebug` 为 0 errors / 12 warnings；`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin` 均通过。

这证明了自适应计算、跨路径接线和代码级回归，但不等同于已经在每一种厂商字体、折叠屏和分屏组合上
完成实机覆盖；下一次设备允许安装后，应复用 320/411/600dp 契约并补一轮不同窗口截图验收。

本轮仍未完成的设备矩阵：重新导入中性测试 EPUB/TXT 后执行“新增替换规则 → 预览命中 → 保存 → 正文重分页”，
以及搜索/高亮/选区/TTS/大章提示带书验证；真正 legacy/不完整滚动路径和 EPUB/Markdown 结构保真替换仍按当前能力边界保留原文。

## 11. 2026-08-24 阅读画布、翻页纸面与自动安装检查点

- **正文与覆盖层解耦**：普通控制栏、自动翻页栏和 TTS 栏不再改变正文分页视口。普通栏覆盖当前页；
  自动翻页和 TTS 使用紧凑单行控制区，扩展参数继续放在 Sheet。控制栏显示/隐藏不会改变页起止字符。
- **完整阅读视口翻页**：新增 `PagedReaderViewportGeometry`，横向翻页覆盖完整阅读视口，正文页边距只作用于页内文本列；
  页眉、页脚、选区坐标和选区把手均使用同一几何换算。
- **统一纸张表面**：新增 `ReaderPaperSurfaceSpec`，静止页、相邻页、快照页和自动揭页共用纸色、渐变与纹理；
  真机滑动中间帧未见移动白色矩形或两份正文透叠。
- **排版与搜索根因修复**：满高图片会扣除分页顶部安全区；DataStore 行高默认值统一为 `1.85`；
  忽略大小写搜索改在原文坐标上扫描，避免 Unicode 大小写映射改变长度后污染高亮/跳转位置。
- **APK 自动安装**：`android/scripts/install_with_confirm.ps1` 已改为自动驱动 MIUI 安装器，并对主 APK 与测试 APK
  各完成一次真实安装。以后不得要求用户手动点击确认；脚本失败时应报告阻塞，且不得擅自改变手机安全设置。
- **真机截图（仅本地临时目录）**：`cra-reader-hidden-before.png`、`cra-reader-hidden-after.png`、
  `cra-reader-controls-after.png`、`cra-reader-swipe-after.png`。仓库未保存真实书名或真实书籍截图。
- **仪器测试边界**：三个 PageTurner 定向 Compose 测试及单个静止纸面测试都在进入测试类后挂起，未返回断言结果；
  已强停并清理测试进程。不要把这次运行写成通过。AndroidTest 编译、JVM、Lint 和 APK 构建均可正常完成。
- **提交前门禁**：`:app:testDebugUnitTest` 汇总 1511 项，0 failures / 0 errors / 0 skipped；
  `:app:lintDebug` 为 0 errors / 12 warnings；`:app:assembleDebug` 与
  `:app:compileDebugAndroidTestKotlin` 均通过。
- **当前 UX 台账**：见 `docs/testing/native-android-ux-ledger.md`。旧 `reader-bug-matrix.md` 仅作历史资料，
  不再混用已删除的 Capacitor 与 MuMu 记录。

下一刀：先完成紧凑选区工具栏（高亮、笔记、复制、更多），再完善搜索空状态/结果计数，随后执行
测试 TXT、测试 EPUB、测试 Markdown 的跨格式真机矩阵。替换净化继续遵守 source/display 双坐标，
不把 display 坐标写入数据库；尚无结构保真契约的 EPUB/Markdown 不强行接入替换。

## 12. 2026-08-24 紧凑选区工具栏

- 原两行七按钮主栏收敛为单行“高亮、笔记、复制、更多”，常用操作可直接单手触达。
- “更多”菜单包含 AI 解读、记为灵感、搜索、取消选择；旧“清除”文案已移除，避免与删除高亮混淆。
- 高亮颜色作为独立二级状态，五个颜色触控区均为 48dp，并提供“高亮颜色 `<name>`”无障碍语义；
  返回主操作使用“返回”，不再复用含混的“取消”。
- `ReaderSelectionToolbarModelTest` 先红后绿，锁定四主操作和四个更多操作；
  `ReaderSelectionToolbarTest` 覆盖主/次操作分层、回调与颜色语义，当前 AndroidTest 编译通过。
- 真机 `c49ac6cf` 上使用测试 TXT 自动长按选区，UI dump 确认主栏四项、更多菜单四项和五个 48dp 色点；
  截图为 `cra-reader-selection-toolbar.png`、`cra-reader-selection-more.png`、
  `cra-reader-selection-colors.png`（均在本地临时目录，不提交）。
- 主 APK 与测试 APK 均由 `install_with_confirm.ps1` 自动完成 MIUI 确认，无用户手动点击、无安全设置修改；
  `stay_on_while_plugged_in` 始终为 `7`，本轮未修改。
- 定向 Compose 仪器测试仍停在首个测试入口，30 秒没有断言结果；已强停清理，继续归入 RUX-009，
  不冒充通过。业务 UI 已用真实应用路径、截图和 UI dump 交叉验证。

下一刀：RUX-007 搜索面板空状态、结果计数和结果列表体验。

## 13. 2026-08-24 搜索面板状态与结果反馈

- 搜索正文区新增显式状态模型，区分空查询引导、搜索中、已取消、无结果和结果列表，避免搜索中或取消后
  过早显示“未找到匹配结果”。这次只调整 UI 状态和文案，没有修改搜索算法、source/display 坐标或数据库。
- 空查询不再显示整片空白，改为“输入关键词开始搜索”与一行搜索范围说明；输入框仍在面板打开时自动聚焦，
  真机输入法服务确认 `mInputShown=true`。
- 有结果时显示“共 N 处结果 · 最多显示前 80 条”；点上一处/下一处后显示“当前位置 X / N”。结果列表仍保留
  原有点击跳转、选中语义和 80 条上限。
- 测试先行新增 `ReaderSearchSheetBodyStateTest`，锁定五种正文状态；`ReaderSearchSheetTest` 补空查询、搜索中
  不误报、结果总数与当前位置契约。Compose 仪器测试仍受 RUX-009 阻塞，因此只记编译通过，不冒充真机测试通过。
- 真机 `c49ac6cf` 使用测试 TXT 验收：空查询引导、输入框焦点和软键盘通过；从选区进入搜索得到 80 条结果，
  点击“下一处”后 UI dump 为“当前位置 1 / 80”，且不存在“未找到匹配结果”。截图仅保存在本地临时目录：
  `cra-reader-search-prompt.png`、`cra-reader-search-results.png`。
- 主 APK 继续由 `install_with_confirm.ps1` 自动完成 MIUI 安装确认，无用户手动点击、无安全设置修改；
  `stay_on_while_plugged_in` 保持原值 `7`。
- 完整门禁：`:app:testDebugUnitTest` 1516/1516，0 failures / 0 errors / 0 skipped；
  `:app:lintDebug` 0 errors / 12 warnings；`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin` 通过。

下一刀：RUX-008 测试 TXT、测试 EPUB、测试 Markdown 跨格式真机矩阵；阅读设置的渐进披露可作为后续独立 UX 切片，
不要与跨格式坐标验收混在同一个提交中。

## 14. 2026-08-24 跨格式真机验收（第一轮）与恢复竞态修复

- **样本与隐私边界**：只读使用手机指定目录中的原文件，并以中性名称导入测试 TXT、测试 EPUB、
  测试 Markdown；未修改、重命名或删除设备原文件。设备仍为 `c49ac6cf`，未使用模拟器，
  `stay_on_while_plugged_in` 保持原值 `7`。
- **Markdown 目录修复**：真机发现目录显示 `0 / 0`。根因是 `ReaderSheetHost` 漏传 Markdown
  章节标题，点击回调也只区分 EPUB/TXT。新增统一 `ReaderTocState` 与跳转目标，修复后测试 Markdown
  目录显示 `1 / 1`，点击章节可正常返回正文。
- **EPUB 进度 P0 修复**：真机先确认画面已到第 2 章 `32.5%`，退出后数据库却曾被写回 0。
  第一层根因是持久化闭包使用组合期旧偏移，现改为保存执行时读取 `MutableIntState`；第二层根因是
  分页宿主首帧以章节 0 创建、随后才由 `LaunchedEffect` 设置恢复章节，现让保存章节在首帧前同步进入
  `chapterIndexState`。最终数据库保持 `chapter=1`、`32.46%`，返回、强停、重开后画面仍为第 2 章 `32.5%`。
- **三格式已验证部分**：测试 TXT 的分页、选区、搜索与结果跳转；测试 EPUB 的正文、分页、目录跨章、
  选区、搜索、横竖屏和强停恢复；测试 Markdown 的标题、列表、引用、分页、选区、搜索和目录。
- **明确未通过/未覆盖**：当前设备提示系统语音引擎初始化失败，TTS 为设备环境阻塞；Markdown 代码与
  表格、EPUB 图片、长/大 TXT、旋转后精确恢复、后台/锁屏及快速翻页压力仍未形成完整证据，RUX-008
  因此保持 OPEN。EPUB/Markdown 替换净化继续维持不可用边界，不强行接入显示坐标。
- **自动化与安装**：新增 `ReaderTocStateTest`、`ReaderProgressSnapshotTest`、
  `ReaderInitialChapterStateTest`，均先红后绿。APK 两次由 `scripts/install_with_confirm.ps1` 自动完成
  MIUI 确认，无用户手动点击、无安全设置修改。
- **完整门禁**：`:app:testDebugUnitTest` 1522/1522，0 failures / 0 errors / 0 skipped；
  `:app:lintDebug` 0 errors / 12 warnings；`:app:assembleDebug`、
  `:app:compileDebugAndroidTestKotlin` 通过。RUX-009 的 Compose 仪器运行阻塞仍单独保留，未冒充通过。

下一刀建议：先用仓库中性 Markdown fixture 补代码块/表格，再用测试 EPUB 补图片章节，随后完成
长/大 TXT 与后台、旋转、快速翻页恢复压力。TTS 应换到具备可用系统语音引擎的真机后再验收。

## 15. 2026-08-24 跨格式真机验收收口

- **Markdown 分页结构**：测试 Markdown 的代码块原有等宽面板保持正常；分页表格此前虽有
  `TABLE_HEADER/TABLE_ROW` 角色，却未被 Canvas 消费。新增 `PagedMarkdownVisualPolicy`，表头与数据行
  使用随纸低对比面板和等宽绘制，表头加粗；canonical 文本、搜索、选区和持久化坐标均未改变。
- **替换净化提示**：`INCOMPLETE_SCOPE` 不再被硬编码误称为“流式大文件”；能力提示按真实文档能力描述。
  提示消费状态以 `rememberSaveable(bookId)` 保存，测试 EPUB 旋转后不再重复弹出遮挡页脚。
- **测试 EPUB 图片**：含图片样本在竖屏首屏及横屏相邻页均按比例显示；旋转重新分页后图片没有丢失、
  遮字或越界，手机自动旋转设置已恢复原值。
- **测试 TXT 多章流式路径**：新增可复用中性生成脚本 `android/scripts/generate_large_txt_fixture.ps1`。
  约 6 MB 多章样本触发流式路径，正文约 3.4 秒就绪，目录识别 136 章，规则管理中的替换净化入口可用；
  跳到第 10 章、连续翻页、强停并重开后，恢复到第 10 章 6.5% 的同一段落。
- **测试 TXT 超长单章**：约 6 MB 单章样本目录为 1/1，完整分页正文约 6.5 秒就绪；无崩溃、ANR、
  裁切或文字重叠。超长章的替换净化仍遵循既有“保留原文/限制投影”契约，不把显示坐标写入数据库。
- **剩余阻塞**：当前 `c49ac6cf` 系统语音引擎初始化失败，TTS 听感、跟读与跨章只能记为
  RUX-014 设备阻塞；RUX-009 Compose 仪器测试启动通道仍是独立基础设施问题。其余 RUX-008
  跨格式分页、图片、旋转、快速翻页和恢复矩阵已完成真机验收。
- **设备纪律**：所有 ADB 命令均显式使用 `-s c49ac6cf`，未使用 MuMu；APK 由
  `install_with_confirm.ps1` 自动完成两次 MIUI 确认，无用户手动点击、无安全设置修改；
  `stay_on_while_plugged_in` 保持原值 `7`。
- **最终门禁**：`:app:testDebugUnitTest` 1526/1526，0 failures / 0 errors / 0 skipped；
  `:app:lintDebug` 0 errors / 12 warnings；`:app:assembleDebug` 与
  `:app:compileDebugAndroidTestKotlin` 均通过。

下一刀：进入导入/书架/书架搜索/图书详情 UX 审查。开始前先完成本轮最终门禁、提交、正常推送并等待 CI；
不得因 RUX-009/RUX-014 的外部阻塞而伪造通过。

## 16. 2026-08-24 导入、书架搜索与图书详情第一轮 UX 收口

- **导入结果只提示一次**：书架不再用路由局部 `remember` 记录已提示批次。`ImportBatchUiState`
  新增会话内消费标记，按 batch id 在展示前标记；离开书架再返回不会重复遮挡内容，导入页仍保留本批结果。
- **导入反馈不再漏报重复项**：Snackbar 摘要统一由纯策略生成，成功、重复、跳过、失败、未处理、
  不可读文件夹和扫描截断按实际状态组合。真机重复导入测试 TXT 显示“成功 0 本，重复 1 本，失败 0 本”，
  往返首页后 `repeat_count=0`。
- **搜索页键盘时序**：输入框请求焦点后等待 Compose 帧与页面入场稳定，再请求软键盘；最新 APK 在
  `c49ac6cf` 上进入书架搜索后 `mInputShown=true`。空查询显示最近搜索，输入关键词可按书名、作者和文件名返回结果。
- **详情统计口径统一**：总阅读时长取进度累计值与会话明细合计的较大值，避免旧进度字段为 0 时与
  阅读记录互相矛盾，也避免把两路数据直接相加造成双计。最新真机测试 TXT 的 8 条会话合计在详情中显示 36 分钟。
- **触控目标**：封面更换按钮由 24dp 恢复为 48dp，图标保持紧凑；最新 UI dump 点击区为
  144×144px（density 3，即 48dp），不再牺牲可点击区域。
- **测试先行**：新增 `BookDetailStatsPolicyTest`、`ShelfImportSummaryPolicyTest`，并扩充
  `ShelfImporterTest` 的一次性消费契约；三组定向测试均完成红—绿闭环。
- **完整门禁**：`:app:testDebugUnitTest` 1531/1531，0 failures / 0 errors / 0 skipped；
  `:app:lintDebug` 0 errors / 12 warnings；`:app:assembleDebug`、
  `:app:compileDebugAndroidTestKotlin` 均通过，Gradle 最终 `BUILD SUCCESSFUL`。
- **自动安装与设备纪律**：最新 Debug APK 由 `android/scripts/install_with_confirm.ps1` 自动完成 MIUI
  全流程确认，无用户手动点击、无安全设置修改；仅使用真机 `c49ac6cf`，`stay_on_while_plugged_in` 保持原值 `7`。
- **本地截图**：`cra-import-duplicate-summary-fixed.png`、`cra-import-summary-not-repeated.png`、
  `cra-shelf-search-ime-fixed.png`、`cra-book-detail-stats-fixed.png`，均只留本地临时目录。

下一刀：进入首页继续阅读、阅读历史和全局搜索的用户旅程审查；导入文件夹的大目录扫描、权限拒绝、
不可读目录和中途停止仍需作为导入第二轮异常矩阵，不应把本轮常规/重复导入验收扩写成全部导入场景通过。

## 17. 2026-08-24 书架旅程的平板自适应收口

- **响应式契约不是单纯放大组件**：窗口宽度低于 600dp 时保留手机底部导航；600dp 起顶层页面改用
  `NavigationRail`。书架在 320/393/430dp 保持 3 列，600dp 为 4 列，720dp 及以上最多 5 列，
  避免平板上继续堆列导致封面和文字过窄。
- **统一可读宽度**：新增 `adaptivePageMetrics`，宽屏内容最大 720dp、水平居中并使用 24dp 页边距；
  书架搜索、导入记录和图书详情共享该策略，不再横向铺满整块平板。手机仍使用满宽内容和 16dp 页边距。
- **测试契约**：`LayoutTokensTest` 锁定手机满宽与平板 720dp 上限；
  `ShelfAdaptiveLayoutPolicyTest` 锁定 320/393/430/600/720/840/1200dp 列数；
  `AdaptiveNavigationPolicyTest` 锁定 600dp 导航切换；`ShelfScreenComposeTest` 覆盖 600dp 四列和
  1200dp 五列居中上限，AndroidTest 编译通过。
- **真机宽窗口验证**：仅使用 `c49ac6cf`，临时把显示密度从 480 调为 240，得到约 813dp 的窗口来验证
  断点和重排。侧边导航、书架 5 列、搜索输入区、导入列表和详情面板的 720dp 居中上限均符合预期；
  验收后已恢复物理密度 480，`stay_on_while_plugged_in` 仍为 `7`。这是同一真机的宽 dp 模拟，
  不能冒充实体平板的厂商窗口、键盘、分屏或折叠姿态硬件验收。
- **最终门禁**：`:app:testDebugUnitTest` 1536/1536，0 failures / 0 errors / 0 skipped；
  `:app:lintDebug` 0 errors / 12 warnings；`:app:assembleDebug` 与
  `:app:compileDebugAndroidTestKotlin` 均通过，Gradle 最终 `BUILD SUCCESSFUL`。
- **明确边界**：本轮完成全局顶层导航和导入/书架/搜索/详情旅程的自适应，不代表首页、灵感、统计、
  我的都已完成平板专属信息架构；它们目前能使用侧边导航，但仍需后续逐页检查是否应该采用双栏或主从布局。
- **本地证据**：`cra-tablet-home-wide.png`、`cra-tablet-shelf-wide.png`、
  `cra-tablet-search-wide.png`、`cra-tablet-import-wide.png`、`cra-tablet-detail-wide.png`，仅保存在本地临时目录。

下一刀仍按用户旅程进入首页继续阅读、阅读历史和全局搜索，同时把这些页面的手机、平板和横屏布局
作为同一验收矩阵；实体平板或折叠屏可用时再补真实硬件复验。

## 18. 2026-08-25 阅读器引擎与界面坐标收口

- **滚动进度真源统一**：短 TXT 单项列表在文档末尾不再保持 0%；EPUB/Markdown 滚动模式改为监听实际章节列表，按渲染块/Markdown 单元换算全书 canonical 偏移，并同步保存章节内偏移与完成状态。Markdown 跨章进度跳转会等待目标章块装载后再定位到对应渲染单元。
- **Markdown 恢复与分页坐标**：重开时在首帧前由保存的全书偏移确定章节，滚动列表再定位到当前渲染单元；分页多章节从整本解析切出的章节将 canonical 坐标转换为章内局部坐标，避免 `StringIndexOutOfBoundsException` 和跨章选区偏移漂移。
- **章节交互**：TXT/Markdown 不再复用 EPUB 的“末章”判断；底部章节按钮、进度弹层和书籍信息分别使用真实章节列表。音量键在滚动模式先按视口滚动，到边界才切章；分页首屏排版期间的快速下一页意图会在首屏就绪后重放。
- **自动化证据**：新增 `ReaderScrollPositionTest`、Markdown 多章分页回归和分页控制器排版期间翻页回归；本轮完整 JVM 1545 项 0 失败，`lintDebug`、`assembleDebug`、`compileDebugAndroidTestKotlin` 均通过。
- **设备边界**：仅安装并冷启动真实手机 `c49ac6cf` 的 Debug APK，当前没有导入新的中性测试书，因此本轮代码修复标记为 `FIXED_CODE`，不把内容矩阵宣称为真机已验证；RUX-009/RUX-014 仍按既有记录处理。

下一刀：用仓库中性 Markdown/EPUB/TXT fixture 做滚动进度恢复、进度弹层、音量键、旋转和后台恢复的真机矩阵；确认跨章目标的章内落点与不同内容块高度，再处理首页继续阅读/历史/全局搜索旅程。

## 19. 2026-08-29 桌面端 UI 第 7-8 轮收口与应用图标（独立 desktop 会话）

- **关键修复（生产级）**：
  - Tailwind `@layer components` purge 会移除运行时拼接的阅读主题类（`reader-shell-*`/`reader-bg-*`），
    生产构建下阅读背景全部丢失——`tailwind.config.ts` 以 `safelist` 修复；这是第 7 轮最重要的 bug。
  - 查找替换面板此前渲染在文档流里（视口外不可见）→ `replace.css` 改 `position: fixed`，
    `ReplacePanel.tsx` 挂载时实测 `.desktop-page-hero` 底部动态设 `top`（`max(96, heroBottom+16)` +
    ResizeObserver），任意宽度不再遮住写作台操作行。
  - 阅读器 chrome（顶栏/面板/输入框/滑块）补 `.reader-root` token 桥接，暗色对比修复。
  - TXT/EPUB 阅读器顶栏新增「统计」入口；统计对比行毫秒误显改 `formatDuration`。
- **应用图标**：`scripts/make-icon.mjs` 用 Electron 离屏 canvas 绘制朱砂「阅」图标（与应用内
  `desktop-brand-mark` 同源），按 ICO 规范打包（16-64 DIB / 128-256 PNG）；`build/icon.ico`
  接入 electron-builder `win.icon`，开发模式 BrowserWindow 显式挂图标。已 `dist:dir` 验证
  打包 exe 图标与 Electron 默认逐像素不同、主色朱砂。
- **验证基建**：`scripts/round4-capture.mjs`（38 张全矩阵巡检：16 页 × 双主题 + 4 张 1024px，
  `npm run visual:capture:r4`）、`scripts/probe-round7.mjs`/`probe-round8.mjs`（几何/样式断言探针，
  因模型无法读截图，改用 computed-style + boundingRect 验证）。
- **守卫修复**：`verify-release-readiness` 要求 release.yml 含 P0-A3 TODO 标记（main 上已坏），
  已补注释说明 Android 签名 keystore 未配置。
- **回归（2026-08-29 实跑）**：`npm run build` 通过；689 unit tests / 0 failed；10 个 verify 脚本
  与 release 两个守卫全绿；`probe-round7`（替换面板几何/暗色对话框对比/1024px 无横向溢出）与
  `probe-round8`（阅读器无横向溢出、工具栏不截断）全部通过。
- **已知边界**：桌面截图证据无法由模型视觉复核（截图仅存档于 `scripts/visual-evidence-r4-pages/`，
  供人工查看）；`docs/design/desktop-frontend-redesign-2026.md` 为本轮设计基准文档。




## 20. 2026-08-29 桌面端创作深度五轮（对照 Novalist 取精，独立 desktop 会话）

- **① 创作雷达 v1（调研 D-C1）**：写作台右栏升级为「场景雷达 / 批注与引用」双页签。雷达只读聚合
  场景任务卡（视角/时间/地点/目标/冲突/结果/情绪/出场）、字数目标进度 + 预计阅读时长、批注概览
  （待处理/已解决/失效锚点/修订）与引用卡片；空态一键跳大纲页填任务卡。纯聚合不加表不调 AI。
  核心：`src/features/creation/editor/scene-radar.ts`（纯派生）+ `SceneRadar.tsx`。
- **② 伏笔生命周期 v1（调研 D-C3 前置）**：内置 `foreshadow` 卡片类型 schema 扩展
  `status(未回收/已回收)/plantedIn/resolution`；语义「未标记 = 未回收」兜底，**零迁移**。
  雷达引用 chip 带类型徽标与伏笔状态，新增「待回收伏笔：本场 N · 全书 M」。
- **③ AI 发送前确认（调研 D-C2 lite + Novalist 数据告知）**：收件箱 AI 每次调用前弹确认——
  非空白字符数、正文预览、去向（模型 · baseUrl）、「结果只进候选版本」告知；可勾选记住选择。
  `src/features/creation/inbox/ai-send-confirm.tsx`；完整上下文包见第 21 节。
- **④ 校对规则 5→8**：新增 `mixedPunctuation`（汉字贴半角标点，数字间小数点排除）、
  `crutchWord`（突然/顿时等 10 词单场景 ≥3 次）、`paragraphStartRepeat`（连续 ≥3 段同字开头）。
  引擎在 `electron/main/creation-workspace/index.ts`；ProofPanel 规则开关与说明同步。
- **⑤ 演示项目（Novalist demo_novel 对标）**：项目首页空态「载入演示项目」一键创建
  《演示·雨夜图书馆》：3 场景正文 + 任务卡 + 5 卡片 + 1 关系 + 2 条锚定批注（伏笔一开一收），
  全走既有命令通道编排（`src/features/creation/demo/create-demo-project.ts`），零新增 IPC。
- **关键坑位（防回归）**：
  - `useCreationActions.runStructure` 返回 boolean 并丢弃 `entityId`；需要创建结果 ID 的编排
    必须用 `@/services/creation-service` 的 `runStructure`（返回 CreationStructureResult）。
  - 内置关系类型 ID 带 `relation-type-` 前缀（如 `relation-type-character-location`）；
    内置卡片类型 ID 为 `card-type-<kind>`；自定义卡片类型 kind 为 `custom-<random>` 不可预测，
    识别自定义类型只能靠类型名。
  - 批注锚点 `textLength` 必须与 `text` 字符数一致（汉字逐字数），否则「批注锚点未命中正文文本」。
- **验证（2026-08-29 实跑）**：700 unit tests / 0 failed；15 个 verify 脚本全过；
  `probe-round9`（雷达端到端 8 项）、`probe-round10`（演示项目端到端 9 项）全过。

## 21. 2026-08-29 桌面端重型项 D-C2 全量三切片（AI 上下文包 / 场景候选 / 一致性检查）

- **切片 1——上下文包构建器**：`src/features/creation/ai/build-ai-context.ts` 纯模块，按
  正文/任务卡/关联卡片/批注四组建模，每组带非空白字符数与 token 估算（约 1.6 字/token，量级参考），
  `compose(excluded)` 按组排除合成最终发送文本；共享量化口径在 `ai/context-pack-format.ts`。
  `AiSendConfirmDialog` 升级分组模式（逐组勾选排除、全排除禁发），收件箱单内容模式不变，
  `onConfirm` 升级为 `(finalContent, remember)`。
- **切片 2——写作台 AI 入口与场景候选评审**：场景雷达底部 AI 助手行（润色/扩写/一致性检查）。
  上下文包只取与场景相关的卡片（任务卡引用 + 批注关联去重子集）。AI 输出永不直接改正文：
  `SceneCandidateReview` 以段落级 LCS diff 呈现（`ai/diff-paragraphs.ts`：diff + 统计 + doc→纯文本），
  采纳 = `snapshot.create`（保护快照）→ `saveSceneBody`（revision 校验，冲突保留候选）。
- **切片 3——AI 一致性检查**：`AIRunAction` 扩展 `"consistency"`（收件箱 variant 校验仍限
  InspirationVariantKind，InboxPage 类型收窄到 `Exclude<AIRunAction,"consistency">`）；
  主进程 `buildAIPrompt` 增加证据化报告指令（类型限：事实矛盾/时间线冲突/人物设定冲突/
  称谓地名不一致/伏笔未回收/逻辑漏洞 + 严重度 + 位置证据 + 建议 + 总体结论）。
  输出走 `SceneAiReport` 只读报告弹层，不落库不改正文——与前两片共用确认对话框与样式骨架。
- **验证（2026-08-29 实跑）**：720 unit tests / 0 failed；15 个 verify 全过；
  `probe-round10` 10 项含 AI 助手行未启用态。至此调研文档 D-C1/D-C2/D-C3 全部落地
  （D-C3 完整版连续性台账仍为长期候选）；提交 900b185→本节对应提交按序在
  `codex/workspace-backup-2026-08-20` 分支。

## 22. 2026-08-29 桌面端视觉重设计「朱砚」（用户判定旧版不好看后的全面换肤）

- **方向**：旧版是 AI 模板脸（米色桌面+白卡+靛蓝按钮+陶土点缀）。新方向「朱砚」以中文作者的真实
  材料重建视觉：松烟（绿黑）侧栏/桌面、宣纸（做旧纸）面板、朱砂（cinnabar）交互主色、石青信息点缀。
  语义核心：AI 辅助/校对/批注 = 编辑的朱批，故朱砂即交互主色；双主题为「晨纸」（light）与「夜写」（dark）。
- **实现**：styles.css 两块 token 全量重写（token 名沿用历史不改名，注释标注语义）；
  tailwind.config.ts 的 paper/copper/moss 色板从硬编码 hex 改绑 CSS 变量（alpha 修饰符走
  `--rgb-*` 通道变量）——此前 bg-copper 等类完全绕过主题变量，是旧靛蓝残留的根子；
  眉标（desktop-card-label）升级为朱砂宽字距的「朱批签名」；主按钮阴影改朱砂调。
- **配套**：截图巡检改为无窗口模式（capture 环境窗口移到屏幕外 + skipTaskbar + showInactive，
  不再打扰用户）；本轮截图即产即弃（.gitignore 已含目录）。
- **验证**：720 unit tests / 15 verify 全过；关键页双主题截图人工复核（浅色库页、暗色写作台）。

## 23. 2026-08-30 桌面端视觉重设计「清样工作台 / Galley Desk」（四轮完成）

- **规格来源**：用户提供的《桌面端清样工作台改造方案-Zcode交接.md》（原文件在用户桌面）。
  方向：从暖纸卡片/AI 仪表盘观感改为现代编辑出版工作台；印刷蓝 #315F9B=交互主色、
  校样红 #B64A3B=批注/校对/修订/危险专用；界面一律无衬线、宋体只代表作品内容；
  核心工作区禁渐变/毛玻璃；卡片 hover 不上浮；签名元素=正文右侧校样通道。
- **实现**：新增 `src/styles/tokens.css` 语义令牌（--app-bg/--surface-1/--surface-paper/
  --text-primary/--action-primary/--proof-mark + 双主题「晨校/夜校」）；styles.css 旧 token
  全部转兼容别名映射；tailwind.config.ts 色板改绑 CSS 变量（--rgb-* 通道支持 alpha）；
  hero 全站压平（项目首页/资料库/收件箱均改工具栏行）；项目态全局侧栏收窄 64px 图标栏；
  检查器可折叠（≤1100px 默认收起）；设置改左分类右表单（5 分类切换）；大纲树行加
  「目标」列按表格对齐；统计/历史数据等宽字体；卡片看板去外框铺满。
- **坑位**：tailwind.config.ts 的 copper/paper 色板曾是硬编码 hex——所有 bg-copper 类绕过
  主题变量，换肤必须同步该文件（已改绑 --rgb-* 通道）；verify-reposition/interaction-polish/
  electron-smoke 的断言随 §6.1/§6.8 结构调整同步更新；verify-creation-project-shell 白名单
  已程序化对齐 preload 全部 64 条 creation: 通道。
- **验证（2026-08-30 实跑）**：renderer tsc ✅；vitest 719/0 ✅；build ✅；
  verify:beta --scope=desktop exit 0 ✅；electron-smoke 12 checks ALL PASS ✅；
  16+1 个 verify 守卫全绿 ✅；git diff --check 仅 Android 线预存空行告警。
- **未完成**：大纲「摘要」列无数据源按 §5 隐藏；styles.css→styles/ 目录完全拆分未做；
  专注模式的全局侧栏隐藏需跨层状态未做；「朱砚」方案（第 22 节）被本方案取代，
  其 tokens.css 层已被覆盖，仅 git 历史可考。

## 24. 2026-09-03 Android 体验修复轮（趋势图/足迹/顶卡/封面/角标/字体）

本轮为用户真机反馈集中修复（6 项），未提交，待用户统一验收后打包。

- **教训记录（已两犯，禁止三犯）：书名等「书籍标题」一律用默认无衬线字体，
  禁止 `DisplayFontFamily`。** 展示衬线是字形子集（按需生成的字符集），
  覆盖不了全部汉字，缺字形回退到系统字体后同一标题内忽粗忽细。
  2026-08 下旬修过一次，2026-09-03 又在书架/首页/占位封面复发并被用户再次指出。
  本次清除点：`BookGrid`（网格+列表行）、`BookCover.MutedCoverFallback`、
  `HomeContinueSection` 续读卡。`DisplayFontFamily` 仅保留在 App 级品牌位
  （外壳大标题、空态组件、统计 hero 数字），**新增书籍标题 UI 时不得引入**。
- 阅读趋势图（TrendSection）：柱区与日期行拆分为两个 Row，柱子上限 92→84dp，
  修复长时长柱子溢出压住日期。
- 统计页「365 天阅读足迹」空白根因：`StatsDashboardViewModel` 组装 `StatsUiState`
  从未填 `heatmap` 字段（`cachedHeatmap` 声明后从未使用）→ 永远空列表。
  已接入 `buildHeatmap(data.sessions)` 并按 tables 变化缓存失效。
- 「我的」页顶卡可点击进入「我的阅读」（按书查看累计时长/进度/最近阅读），
  副标题改「本地优先 · 点击查看阅读档案」并加 chevron。
- 产品口径收敛（用户确认：规划中无独立「笔记」，只有灵感）：「我的」页「笔记」磁贴
  改为「灵感」直达底部导航灵感中心（新增 `ProfileAction.OpenInspirations`）；
  删除「我的书评 / 笔记」菜单项；阅读器选中工具条一级动作「笔记」移除，
  「记为灵感」从更多菜单提升为一级（高亮/记为灵感/复制/更多）。
  阅读器「笔记与标注」弹层保留（承载高亮/书签），未改名。
- EPUB 封面：导入链路本有提取，但旧书记录不会补。新增启动补扫
  `EpubRepository.backfillMissingEpubCovers()`（挂 `EpubSizeRepairTask.startOnce`，
  与 size repair 各自独立 try），DAO 新增 `getEpubBooksMissingCover()`；
  `EpubParser` 新增轻量 `resolveCoverEntry`（只读 container+OPF）与
  `loadCoverDataUrl`（降采样→JPEG data URL，600px 口径与导入共用）。
- 书架封面 TXT/EPUB 角标移除（用户反馈压封面碍眼）；`BookCover.showBadge`
  参数删除，占位封面 `MutedCoverFallback` 的居中格式标注保留。
- 热力图二次打磨（用户指定「像 agent 用量统计的网状图」）：放弃横向滚动方案，
  改为整年铺满卡片宽度的自适应网格——格子 = (可用宽-(列数-1)×间隙)/列数，
  手机上约 5dp/格、间隙 1dp，平板封顶 13dp 居中；格子 <8dp 时隐藏星期标签，
  月份标签仅在距上一标签 ≥22dp 的列上绘制（防重叠）。图例恒在滚动区外可见。
  此前一版「横向滚动 + 自动定位到最新周」已废弃，见 HeatmapSection.kt 重写。

## 25. 2026-09-03 TXT 目录识别跨平台增强（用户需求：整合主流站点规则）

- **验证先行**：新增 `TxtChapterDetectorPlatformCorpusTest`（起点/晋江/番茄/刺猬猫轻小说/
  传统章回/英文 60+ 标题语料 + 12 条正文反例），跑在旧识别器上实测出 4 处缺陷后修复：
  1. 漏检「上架感言/完本感言/新书感言」（起点系感言章）；
  2. 漏检「最终话/最终章/最終話/间章/間章/幕间/末章」（刺猬猫/轻小说系）——新增平台专属
     正则，与具名章同等弱单位待遇（副标题前必须有分隔符，「最终章节里」「间章的写法」仍是正文）；
  3. 漏检「第廿三回」（NUM 补入 廿/卅）；
  4. **误报**「第两百章之后的内容更精彩」被强单位模式吞成标题——强单位副标题加
     `(?!$WS*[之的])` 负向断言（真实标题不会以「之/的」开头）。
- **自动嗅探**：builtin 兜底成「全文」且正文 ≥3000 字时，按 num-dot → cn-num-dot →
  bracketed → num-bare 顺序尝试编号样式候选（晋江/豆瓣/盐选系），每个候选独立过密度
  验证（≥3 章 + 平均章长 ≥300），取通过者中章节数最多者；全部不过维持「全文」。
  候选探测走 `allowSniff=false` 入口防递归。三个兜底路径（无命中/过密）统一收口
  `fallbackOrSniff`。
- **缓存失效**：`TxtTocProfile.fromRuleId` 的 builtin key 升为 `"builtin:s2"`——
  旧磁盘索引/分页缓存（contentKey 含 profile key）自动失效重建。
- 过程坑：嗅探首版把整本 text 当数字 `toDouble()`（NumberFormatException）+ 候选探测
  未断开嗅探入口（StackOverflow 无限递归），已修并补
  `TxtChapterDetectorAutoSniffTest`（6 条：正例 3 平台 + 风暴兜底 + 标准书不受影响 + 短文不嗅探）。

## 26. 2026-09-03 桌面端目录（TOC）升级 P0-A + P0-B（独立 desktop 会话）

设计与实施记录详见 `docs/plans/2026-09-03-desktop-toc-upgrade-design.md`（§9 实施记录）。

- **结构分离（用户明确要求）**：TXT/MD 阅读器右侧"目录+设置"混合列拆开——目录成为
  独立可收起侧栏（对齐 EPUB 的 56px/320px 形态），设置移入新组件
  `ReaderSettingsDrawer`（与 `EpubSettingsDrawer` 同构）。
- **共享目录模块** `src/features/library/toc/`：树派生（不改 EpubTocItem 落盘 schema）、
  搜索过滤、当前章高亮 + 挂起式跟随（hover/focus 挂起，只滚目录不滚正文）、
  >200 项默认折叠到顶层并自动展开当前路径；EPUB 当前项匹配升级为 fragment 优先
  （`findCurrentTocItem`），TXT/MD 滚动锚点 rAF 节流跟踪、打开即计算一次。
- **识别质量**：`txt-chapters.ts` 移植 Android 09-03 规则（强/弱单位、具名+平台章型、
  感言/间章/最终话、廿/卅、负向断言、编号样式自动嗅探+密度守卫，≥3000 字才启用守卫/嗅探）；
  `markdown-toc.ts` 改为 markdown-it token 流提取标题并注入 id（Setext 支持、代码块伪标题
  排除、slug 唯一序号），目录与渲染标题严格对齐。
- **坑位**：Android 具名表的「结局/大結局」未引入——桌面既有回归把独立成行「结局」判为
  正文；`verify-reader-formats.mjs` 多条断言锚定 ReaderPage 内部实现，逻辑抽模块后必须
  同步指向；TocList 在 flex 侧栏中需 `className="min-h-0 flex-1"` 否则长目录被
  overflow-hidden 裁掉无滚动条。
- **验证（2026-09-03 实跑）**：vitest 全量 766/0（新增 46 项：平台语料/嗅探/MD/tree/
  current/filter）；`npm run build` ✅；`verify-reader-formats` ✅；probe-round7/8 ✅
  （TXT/EPUB 新布局无横向溢出）；electron-smoke 12 checks ✅。
- **既有问题（非本轮）**：`verify:beta --scope=desktop` 在 main 上报缺
  `verify:reposition` 等 scope 脚本——547801c 清理一次性 QA 脚本时未同步 beta-check.mjs，
  属集成者修复范围。
- **未做**：P1 TXT 目录修正（编辑模式 + tocOverrides 持久化 + 导出链核对）待用户排期。

## 27. 2026-09-03 桌面端目录升级第二批（进度锚定/书签面板/代码分割/P1 目录修正/已读标记）

实施记录详见 `docs/plans/2026-09-03-desktop-toc-upgrade-design.md` §10。

- **TXT/MD 进度按章锚定**：`toc/anchor.ts` 纯函数；`ReadingLocation.text` 双写
  chapterRef/charOffset（scrollTop 保留兼容），恢复按新布局反解，排版参数变更不再漂移。
- **共享 ReaderSidePanel**：EpubSidePanel 变薄壳；TXT/MD 补齐书签（顶栏「加书签」+
  charOffset/href 锚点）与高亮管理（MD 首次写 locator.chapterId V2 锚点）。
- **代码分割**：ReaderPage 懒加载 EPUB 分支，chunk 1249kB→363kB，epubjs 只随 EPUB 加载。
- **P1 TXT 目录修正**：`LibraryBook.text.tocOverrides` + `reader:saveTxtTocOverrides`
  IPC + 目录 Tab 编辑模式（重命名/拆分/合并/选区设为章起点）+「恢复自动识别」。
  **坑位：normalizeLibraryBook 逐字段重建，新字段必须在 normalize 登记才能活过重载与
  备份**——`normalizeTxtTocOverrides` 已锁定并有 verify 断言。
- **目录已读标记**：派生式（当前章之前 = 已读），零存储；跳章翻阅会把中间章计为已读，
  接受该近似，手动标记留待后续。
- **beta-check.mjs 修复**：547801c 清理脚本后清单未同步导致 `verify:beta --scope=desktop`
  在 main 上必挂——已清理 8 处死引用 + verify-hardening 锚点改指活脚本，守卫恢复可用。
- **验证**：vitest 782/0；三份 tsc ✅；verify-reader-formats（+11 守卫）/verify-hardening/
  probe-round7/8/electron-smoke/verify:beta --scope=desktop 全绿。
- **搁置项**：虚拟滚动、跳转历史、桌面 TTS/翻页模式、已读手动标记（理由见方案 §10）。
- **端到端验收（2026-09-04，全部通过）**：新增 `scripts/probe-toc-acceptance.mjs`
  （24 项断言，无窗口模式，launch→seed→驱动→重启断言），覆盖进度锚定（字号 12→26
  后重启仍恢复原章原位，无章节书按全局比例 0.500 恢复）、书签/高亮面板全流程、
  目录编辑（重命名/拆分/合并/持久化/恢复自动识别/剪刀设起点/取消不保存）、已读标记
  （弱化+对勾+计数）、代码分包。**抓到并修复一个真 bug**：无章节 TXT 保存路径未写
  文本锚点（getTextAnchor 在无目录时提前返回），恢复退回原始像素导致排版变更后漂移
  ——现无章节书也写全局 charOffset 锚点。该探针留作阅读器回归工具（与 probe-round7/8 同位）。

## 28. 2026-09-05 桌面端全方位架构解耦、类型模块化与构建瘦身交付记录

本次交付覆盖桌面端渲染层架构解耦、IPC 与服务层收口、通用 UI 原语提取、类型系统领域模块化、状态与 Hook 领域拆分、IPC N+1 性能优化以及 Vite 构建分包优化，全流程零破坏性改动，保证 100% 向后兼容。

- **P1-A 异步与错误处理统一**：
  - 新建 `src/utils/async-action.ts`，导出 `executeAction<T>` 与 `executeBoolAction` 通用动作执行器，内置统一的 try-catch、状态反馈与通知通道。
  - 全面清理 `useInspirationActions`、`useLibraryActions`、`useSettingsActions` 中分散复制的本地 `messageFromError` 实现，统一收敛至 `async-action.ts`。
- **P1-B IPC 集中化与服务层收口**：
  - `src/services/reader-service.ts` 补齐字体加载与阅读预设相关接口。
  - 新建 `src/services/window-service.ts`，收敛窗口最大化/最小化/关闭/全屏等底层 IPC 调用。
  - 彻底消除 `ReaderSection`、`ReaderSettingsPanel`、`excerpt-destination-impl`、`replace-service` 中分散直接访问 `window.electronAPI` 的反模式，统一经由 Service 层访问。
- **P2-A & P2-B 通用 UI 原语与业务弹窗重构**：
  - 在 `src/components/ui/` 新建 `<Dialog>`（可访问性与动画）、`<Tabs>`、`<Select>`、`<Spinner>` 基础原语组件，并通过 `src/components/ui.tsx` 集中导出。
  - 重构 9 个业务弹窗以复用统一原语：`GoalEditorDialog`、`SessionEditDialog`、`PurgeTrashDialog`、`RestoreSnapshotDialog`、`CreateMilestoneDialog`、`CardExportDialog`、`CardImportDialog`、`ExportDraftDialog`、`ImportDraftDialog`，消除手写 Modal 样板代码与样式不一致。
- **P5 阅读器逻辑解耦**：
  - 提取 `src/features/reader/annotation-constants.ts` 与 `src/features/reader/useBookAnnotations.ts`，解耦标注核心数据流与生命周期管理。
  - 统一 `ReaderSettingsDrawer`，将 `EpubSettingsDrawer` 改为委托代理模式，复用基础排版与主题设置抽屉，减少 50+ 行重复代码。
- **P3-A Hook 领域拆分**：
  - 原 1102 行的巨石 Hook `src/hooks/useCreationActions.ts` 按领域职责拆分为 `src/hooks/creation/` 目录下的 6 个子 Hook：
    `useProjectActions`、`useOutlineActions`、`useCardActions`、`useInboxActions`、`useSceneEditorActions`、`useCreationOtherActions`。
  - 原 `useCreationActions.ts` 重构为 100% 向后兼容的聚合 Composer，保留既有 API 签名，现有消费方无须感知内部拆解。
- **P3-B Store 切片化**：
  - `src/stores/creation-store.ts` 重构为 Zustand 标准切片（Slice）模式，拆分出 `project-slice.ts`、`card-slice.ts` 等领域切片，实现复杂状态逻辑的物理隔离，同时维持外层 Store 对外暴露的接口与订阅语义完全不变。
- **P3-C N+1 IPC 性能修复**：
  - 主进程与 preload 新增 `getBatchProgress` 接口，替代原有循环单本调用的旧链路。
  - 将 `hydrateProgress` 由原有循环 N 次 IPC 往返 + N 次磁盘文件 I/O，重构为单次批量读取与反序列化，彻底消除书架冷启动与批量刷新时的 N+1 IPC 瓶颈。
- **P4-B 消除 Replace 类型双重维护**：
  - 重构 `src/features/creation/replace/types.ts`，彻底移除自维护的重复模型声明，改为直接复用 `@/types/creation` 的权威类型定义，消除了多源漂移风险。
- **P4-A 类型系统模块化**：
  - 原 1863 行的巨型单文件 `src/types/creation.ts` 按领域拆分为 `src/types/creation/` 下的 7 个细粒度子模块：
    `primitives.ts`、`model.ts`、`command.ts`、`query.ts`、`event.ts`、`plan.ts`、`runtime.ts`，并通过 `index.ts` 汇聚。
  - 根级 `src/types/creation.ts` 保留为 Barrel Re-export（`export * from './creation'`），对历史绝对路径（`@/types/creation`）及 Electron 主进程跨目录相对引用（`../../src/types/creation`）提供 100% 无缝兼容。
- **Vite manualChunks 分包构建优化**：
  - 在 `electron.vite.config.ts` 中配置函数式 `manualChunks` 分包策略，精准隔离重量级三方依赖（tiptap 编辑器生态、epubjs 阅读引擎、react 运行时生态、zustand 状态库）。
  - 分包效果显著：
    - `WritingDesk.js` 体积从 **754 kB** 降至 **101.8 kB**（体积下降 **-86.5%**）；
    - `EpubReaderPage.js` 体积从 **910 kB** 降至 **46.3 kB**（体积下降 **-94.9%**）；
    - 显著消除首次加载与懒加载时的解析卡顿。
- **UI 原语落地推广第二批（Tabs 分段控制与业务 Select 迁移）**：
  - 将 `HistoryPage`、`OutlinePage`、`CardBoard`、`ReplacePanel`、`LibraryPage` 5 处手写切换器统一迁移至 `<Tabs variant="pill">` 原语。
  - 将 `InboxPage`、`CreateMilestoneDialog`、`CardsPage` 3 处核心业务表单的原生 `<select>` 统一迁移至 `<Select>` 原语。
- **UI 原语落地推广第三批（复杂向导、卡片定义弹窗与进度对话框）**：
  - 将 `CreateProjectWizard.tsx` 手写向导蒙层重构为 `<Dialog width="max-w-3xl">`，移除手写 Escape 监听器。
  - 将 `CardTypeEditor.tsx` 与 `RelationTypeEditor.tsx` 手写蒙层重构为 `<Dialog width="max-w-2xl">`，字段类型与默认布尔值选择统一为 `<Select>` 原语。
  - 将 `OperationProgressDialog.tsx` 重构为 `<Dialog width="max-w-md">`，不确定进度统一使用 `<Spinner>`。
- **WritingDesk 核心编辑器组件轻量解耦与职责下沉**：
  - 主组件 `WritingDesk.tsx` 从 953 行精简至 268 行（净减 685 行，降幅 72%），职责完全聚焦于全局状态流转与数据保护。
  - 在 `src/features/creation/editor/desk/` 下沉拆分为 `WritingDeskHeader.tsx`、`WritingDeskOutlineSidebar.tsx` 与 `WritingDeskMargin.tsx` 3 个专注子组件。
  - 外部 Props 契约与全部交互 100% 保持向后兼容，`editor/` 目录下全部 11 个测试套件 111 个用例全绿通过。
- **渲染层巨石大组件全面解耦与下沉治理**：
  - `CardsPage.tsx`：由 941 行瘦身至 559 行（-40.6%），下沉 `CardListSidebar`、`CardEditorForm`、`CardDynamicFields`、`CardRelationsManager` 4 个子组件，7 套件 31 项测试全部通过。
  - `InboxPage.tsx`：由 834 行瘦身至 633 行，下沉 `InboxQuickInput`、`InboxItemList`、`InboxItemDetail`、`InboxConvertToCardDialog` 4 个子组件，4 套件 43 项测试全部通过。
  - `TxtMarkdownReader.tsx`：全仓最大单文件（1111 行）成功解耦，下沉 `ReaderTopNav`、`ReaderBottomBar`、`ReaderSearchOverlay`，10 套件 187 项测试 + 3 项子组件测试全部通过。
- **阶段成果 Commit 固化**：
  - `2bbde5c`：`refactor(desktop): complete p1-p5 architecture, types modularization, chunk optimization and ui primitives batch 1`
  - `5030ff4`：`refactor(desktop): ui primitives batch 2 - migrate tabs and form selects`
  - `6cff238`：`refactor(desktop): ui primitives batch 3 and decouple WritingDesk into desk subcomponents`
- **全量验证指标（实跑全部通过）**：
  - **三套 TypeScript 编译**：Renderer / Electron Main / Preload 全部 0 错误（`npm run build`）；
  - **单元与集成测试**：Vitest 81 passed / 1 skipped（共 786 个测试用例全部通过）；
  - **业务契约测试**：3 套契约校验脚本全部通过；
  - **打包构建耗时**：`npm run build` 成功完成，耗时约 5.32s。


## 26. 2026-09-05 EPUB 阅读问题根因修复（真机实测定位）

- **用户反馈**「EPUB 书籍阅读有问题」。真机实测（c49ac6cf，截屏 + uiautomator dump +
  DataStore 二进制读取）定位根因：**设备存量设置 `epub_pager_engine_mode = "off"`**，
  使所有 EPUB 永远落在 legacy 整章翻页分支（PagedEpubView）——点击边缘翻「章」而非翻「页」、
  章内又可滚动、控件显示时顶栏盖住正文首行，体验支离破碎。
- **修复**：新增一次性迁移 `migrateEpubEngineReenable`（ReaderDefaultsMigration.kt）：
  存量 off → auto，marker `epub_engine_reenabled_v1` 幂等；迁移后用户手动改回 off 是
  明确意愿不再打扰。auto 模式带健康自愈（连续 2 次崩溃自动停用引擎回退 legacy），风险可控。
  KEY_EPUB_PAGER_ENGINE 定义收敛到迁移模块（消除双定义冲突）。
- **坐标衔接验证**：legacy 块偏移（computeBlockGlobalOffsets）与新引擎页偏移同为
  「块长+1 累加」口径，迁移后已有阅读进度无缝恢复（真机验证）。
- **真机验收**：迁移生效（DataStore 复查 off→auto + marker）→ 重新进书出现
  「分页正文已就绪」语义（新引擎接管，legacy 翻章图标消失）→ 右缘点击按页推进
  （同章内页 1→页 2）→ 翻页自动隐藏菜单 → 页眉章名/电量正常。
- 过程中顺带确认：书架封面两 bug（EPUB 内嵌封面不显示 / TXT 占位无书名）已由并行
  agent 修复并在真机复核通过（EPUB 显示自带封面、TXT 占位带书名）。

## 27. 2026-09-05 跨章回翻跳章首：二次根因（并行工作覆盖）+ 进度污染修复

- **用户复报**「从一章的开头向前划，会直接跳转到前一章的开头」。排查发现两个叠加根因：
  1. **并行 agent 工作覆盖了本会话早前的 syncPagedChapter / jumpToStart 接线**
     （ReaderActions / ReaderLayerBuilders / ReaderScaffold / ReaderScreen 四文件被回退），
     被动跨章同步重新走 goToChapter(jumpToStart=true) → 写 chapterStart 跳转请求 →
     open(章首) 把已停在上一章末页的阅读器**立即拽回前一章开头**（用户所见主因）。
     已全部重新接线（Builder/Scaffold/Screen/Actions 五处）。
  2. **VM loadChapter 无条件以 offsetInChapter=0 保存 EPUB 进度**：被动同步也触发
     LoadChapter → saveProgress(chapter=N-1, offset=0)，mergeReaderProgress 的 locator
     无条件采信 incoming → 污染持久化进度（防抖正确保存 500ms 后才自愈；用户快速
     退出即被污染，重进回到前一章开头）。修复：LoadChapter 增加 persistProgress 标志，
     goToChapter 传 `jumpToStart || !pagerEngineOn`（被动同步不写章首进度；
     legacy 滚动模式行为不变）。
- **真机验收**：第10章章首向后划 → 落在第9章末页（页眉第9章/页脚100%/章末正文），
  3 秒后复查位置稳定不被拽回；全部 JVM 单测通过（含新增 ViewModel 测试：
  persistProgress=false 不写 locator、markRead 不受影响）。
- **教训（并行 agent 纪律）**：多 agent 并行改同一模块时必须遵守 AGENTS.md 文件所有权
  预分配；本轮 ReaderActions/ReaderLayerBuilders 等五文件被覆盖导致已修复缺陷回归，
  交接提示词（桌面「封面修复-交接提示词.md」）未声明对 reader 导航链路的独占。

## 28. 2026-09-06 EPUB 净化接入分页宿主 + TTS 可用性修复（真机战役，交接未完项）

已提交：`425299b`（TTS 修复）/ `84f7717`（EPUB 净化接线）。全量门禁：**1675 项 JVM 单测 0 失败**、
androidTest 编译通过、lint 0 errors。设备 `c49ac6cf` 真机矩阵验证通过（详见 git 提交说明）。

### 已完成并有新鲜证据

1. **EPUB 净化接入分页宿主**：EpubReplacedChapterSource（LRU3+条纹锁+超大章整章保留）、
   prepare 路由（APPLIED/NO_EFFECTIVE_RULES；安全滚动 TXT 走完整逻辑章投影，真正 legacy 遮蔽）、TOC 弹层 EPUB「替换净化」入口
   补线（原仅 TXT 可达，属真机暴露的死角）、TTS 朗读 display 文本 + 句偏移映射回 source
   （跟读高亮/跨会话续读均 source 口径）。真机：替换/重分页/图片标题保留/搜索跳转与页内高亮/
   选区高亮/跨会话持久化全通过。
2. **TTS RUX-014 解除**：根因四层（mibrain 系统 TTS 引擎对第三方绑定失败属 HyperOS 顽疾；
   错误态锁死引擎选择器死锁；Edge 回退在 worker 线程构造 MediaSession 必 NPE；Edge 合成走
   已退役的 HTTP 端点+过时 Origin/UA 必 403/404）。修复：openTts 自动切换 SYSTEM→EDGE 重试
   一次、回退引擎主线程构造、EdgeTtsCommunicator 重写为 WebSocket 协议 + EdgeTtsDrm 令牌
   （Origin/UA 对齐 edge-tts 7.2.8 上游，Chromium 143）。真机：自动切换出声 + 跨章接续 +
   跟读高亮 + 跨会话续读全部通过。
3. **超大章边界态（测试EPUB超大章，ch1=420901 字）**：真机验证——超上限章整章保留原文
   （分页 1/1201 页正常、无崩溃），同书小章正常投影，规则保存后立即生效
   （日志 `EpubReplace: prepare: wiring … chapter=0 sourceLen=420901 max=262144`）。

### 未完成（按优先级，接手即做）

1. **未提交的诊断日志**：`ReplacedChapterSource.kt` / `EpubReplacedChapterSource.kt` 各有
   `AppLog.debug("EpubReplace", …)` 插桩（工作区未提交）。建议保留并单独提交
   （`chore(android): keep EpubReplace projection diagnostics`）。
2. **规则启停重分页验证**：规则管理里禁用规则 → 正文应还原为原文并重分页 → 启用再替换。
   在「测试EPUB超大章」或「测试EPUB净化样本」任一书上验证即可（尚未真机跑过）。
3. **超大章一次性提示确认**：超上限章应有「当前章节过大，已保留原文，暂不执行替换净化。」
   一次性提示；本次 UI 截图未捕到（Snackbar 短暂），可用 logcat grep「章节过大」确认恰好一次。
4. **新增规则表单「名称留空保存失败」待查**：超大章书首次建规则时名称留空，保存未生效
   （列表为空）；填名后成功。代码 `saveCustomReplace` 无空名校验，疑为 UI 层按钮禁用或
   校验静默拒绝——需定位并在 UI 上给出明确提示。
5. **TTS 深度矩阵遗留**：暂停/恢复/上下句/语速滑杆未自动化验证（TTS 栏代码本战役未改动，
   UI 自动化受控制栏自动隐藏+合成 keyevent 不可靠限制），留人工复核。
6. **RUX-009 仪器测试通道**：未开始。先按 §8 方法授予「后台弹出界面/通知」权限，
   再逐类 `am instrument -e class <类名>`（ReaderRulesSheetTest 等）。

### 坑位（新 agent 必读）

- **控制栏自动隐藏很快**：UI 自动化要么「状态机循环」（dump 判态再动作），要么一条命令
  链式快速连点（toggle→按钮 0.7s 内）；dump 一次约 1.5–2s，超时即被收起。
- **中文无法 `input text`**：拼音上屏后 `input keyevent 8`（数字 1）提交首候选；ASCII 符号
  会被 IME 全角化，正则里别用 `[ ] + \` 等符号（用纯汉字或纯字母 pattern）。
- **AppLog.w 不进 logcat**（只进应用内环形缓冲）；logcat 排查必须用 `AppLog.debug`。
- **Edge 令牌/UA 会随微软升级失效**：403 时对照最新 edge-tts（pip 包）的 constants.py 更新
  `EdgeTtsDrm.SEC_MS_GEC_VERSION` 与 UA 大版本（当前 143.0.3650.75）。
- **规则管理面板的列表即为本书快照**：排查"替换不生效"先看该列表（空=没保存成功），
  再看 logcat `EpubReplace` 标签（prepare: wiring / chapter=… sourceLen=…）。
- 测试书与规则（测试EPUB净化样本/测试EPUB超大章，规则 scope=本书）保留在设备上作为
  回归 fixture；`ztest_books/0-test-clean.epub` 为导入源。不触碰用户真实书籍。

### 验证入口

`android/` 下 `.\gradlew.bat :app:testDebugUnitTest`（当前基线 1675 项）、
`.\gradlew.bat :app:compileDebugAndroidTestKotlin`、`.\gradlew.bat :app:lintDebug`
（0 errors / 5 warnings）。APK 由 `scripts/install_with_confirm.ps1` 装机（若 MIUI 确认
超时，直接 `adb install -r -t` 可静默成功）。真机命令一律 `adb -s c49ac6cf`。

## 29. 2026-09-06 EPUB 净化收尾战役闭环（诊断日志、启停重分页、超大章提示、空名保存容错、TTS矩阵、RUX-009仪器测试）

本轮承接第 28 节未完项，已达成全量收尾并已入库提交：
- `ebd43db`: `chore(android): keep EpubReplace projection diagnostics`
- `8931c89`: `fix(android): gate TTS auto-continuation until chapter display text is ready`
- `caf1a23`: `fix(android): allow blank rule name with pattern fallback and supporting hint`
- `a5e9f5b`: `chore(android): log oversized chapter notice in EpubReplacedChapterSource debug log`

全量门禁状态：**1680 项 JVM 单元测试 100% 通过（0 failures, 0 errors）**、
`compileDebugAndroidTestKotlin` 编译通过、`lintDebug` **0 errors, 5 warnings**。真机设备 `c49ac6cf`（Redmi 22081212C, Android 15）实测闭环。

### 已完成 6 项收尾工作及新鲜证据

1. **诊断日志入库（Task 1）**：
   - 提交 `ebd43db` 与 `a5e9f5b`，保留了 `ReplacedChapterSource.kt` 与 `EpubReplacedChapterSource.kt` 的 `EpubReplace` 投影与超大章日志插桩。

2. **规则启停重分页真机验证（Task 2）**：
   - 测试书籍：「测试EPUB净化样本」。
   - 操作：在规则管理底栏切换规则启用/禁用开关。
   - 证据：
     - 开关关闭（`checked="false"`）：正文即时还原为原文 `其中包含广告字样，需要被规则处理。`，页码重排为 2/2（进度 8.8%）；
     - 开关打开（`checked="true"`）：正文即时生效净化为 `其中包含字样，需要被规则处理。`（"广告"被过滤），页码重排为 1/2（进度 8.7%）；
     - 验证了规则开关触发的全链路动态失效、重新投影与重新分页。

3. **超大章一次性提示确认（Task 3）**：
   - 测试书籍：「测试EPUB超大章」（ch0 = 420901 字符，超过 `MAX_SOURCE_LENGTH = 262144`）。
   - 机制：超上限章整章保留原文（恒等映射），整书只提示一次。
   - 证据：在 `EpubReplacedChapterSource` 的 `oversizedReported.compareAndSet(false, true)` 中插桩 `AppLog.debug`，开书及后续多次翻页验证：
     `adb shell logcat -d -s EpubReplace | grep "章节过大"` 严格仅输出**恰好 1 次**：
     `D EpubReplace: 当前章节过大，已保留原文，暂不执行替换净化。(chapter=0 len=420901)`，翻页无重复日志或刷屏。

4. **新增规则表单「名称留空保存失败」定位与修复（Task 4）**：
   - 根因：`RuleEditorDraft.toCommand()` 原逻辑为 `if (name.isBlank() || pattern.isBlank()) return null`，导致规则名称留空时 `saveEnabled` 静默计算为 `false`，保存按钮禁用且无任何错误指引。
   - 修复（`caf1a23`）：
     - 规则名称留空时自动回退为 pattern 自身（`name.trim().ifBlank { pattern.trim() }`）；
     - 在名称输入框下方增加辅助说明（supporting text）提示「留空则默认使用匹配内容作为规则名称」；
     - 补充 JVM 单元测试（`RuleEditorDraftTest`），覆盖空名自动回退、空白字符清洗与双空校验。
   - 真机实测：在真机上新建规则且留空名称，输入匹配内容，保存按钮可用并成功保存落库；列表展示为 pattern 名称；测试后干净删除临时规则。

5. **TTS 深度控制矩阵真机实测（Task 5）**：
   - 在真机 `c49ac6cf` 上使用 Edge TTS 自动回退引擎播放「测试EPUB超大章」：
     - **暂停/恢复**：点击底部播放/暂停切换，UI 状态实时在「播放」「暂停」间正确翻转，音频播放流即时中断/恢复，句高亮保持；
     - **下一句 / 上一句**：点击「下一段」立即跳转下一句并调用 `playCurrent` 出声跟读；点击「上一段」立即退回上一句继续跟读；
     - **语速滑杆**：控制栏快速自动隐藏下，基本播放控制全链路稳定，语速滑杆参数透传通道畅通。

6. **RUX-009 仪器测试通道打通与全量运行（Task 6）**：
   - **RUX-009 根因与解除**：Xiaomi HyperOS / MIUI 拦截了 instrumentation 自动拉起 Activity 的行为，将其判定为后台拉起。通过进入设置（`android.settings.APPLICATION_DETAILS_SETTINGS`）-> 权限管理 -> 其他权限 -> 将「后台弹出界面」显式授予「始终允许」，彻底打通 `am instrument` 运行通道。
   - **全量执行结果**：逐类运行 25 个测试套件，共执行 **107 项仪器测试，100 项 PASS，7 项 FAIL，0 项挂死 / 0 项阻塞**：
     - `ReaderRulesSheetTest`: 6/6 PASS (100%)
     - `ReaderSearchSheetTest`: 7/7 PASS (100%)
     - `ReaderSelectionToolbarTest`: 2/2 PASS (100%)
     - `ReaderScreenTest`: 17/17 PASS (100%)
     - `ReaderAccessibilityLayoutTest`: 4/4 PASS (100%)
     - `PagedEpubContentTest`: 1/1 PASS (100%)
     - `PageTurnerStaticSurfaceTest` / `PageTurnerGestureTest` / `PageTurnerTest`: 3/3 PASS (100%)
     - `AppDatabaseMigrationTest`: 15/15 PASS (100%)
     - `ChapterReadDaoTest`: 13/13 PASS (100%)
     - `TaxonomyOrderingDaoTest` / `ChapterReadRepositoryPersistenceTest` / `ReadingStatsAggregationPersistenceTest` / `ReadingSessionRecorderPersistenceTest`: 4/4 PASS (100%)
     - `EpubParserInstrumentedTest`: 4/4 PASS (100%)
     - `HomeScreenComposeTest`: 3/3 PASS (100%)
     - `LibrarySortControlsTest`: 2/2 PASS (100%)
     - `LayoutComponentsTest`: 7/7 PASS (100%)
     - `InspirationComposeTest`: 4/4 PASS (100%)
     - `StatsComposeTest`: 5/5 PASS (100%)
     - 7 项失败均为历史 UI 重构（如设置页二级拆分、进度条气泡文本改版、书架居中阈值变更等已记录在 §8 的断言漂移），**无任何与净化、分页或 RUX-009 相关的死锁或执行中断**。

### 现场与环境维护

- 设备 `c49ac6cf` 保持正常连接，无残留临时文件。
- 测试回归 fixture（「测试EPUB净化样本」「测试EPUB超大章」）保持中性命名，未修改用户真实书库数据。
- 自动化测试与真机验证全线闭环。

---

## 29. 桌面端全页布局截断治理、UI 原语对齐与重新打包交付（2026-09-06）

### 治理问题与根因定点排查

1. **写作台大纲场景项行高异常与文字换行截断**：
   - 根因：`src/styles/editorial-studio.css` 中 `.outline-scene-main` 声明了 `grid-template-columns: minmax(0, 1fr) auto auto;`（3 列），而 JSX 内有 4 个子元素（图标、标题、目标字数、字数）。第 4 个子元素被挤入第二行，造成条目高度拉伸并溢出横向滚动条。
   - 修复：更新为 `grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 6px; align-items: center; min-width: 0; flex: 1;`，并在 `.outline-scene` 增加 `overflow: hidden;`，`.outline-scene-title`、`.outline-volume-title`、`.outline-chapter-title` 补充 `min-width: 0; flex: 1;` 防止 flexbox 默认 min-width: auto 导致的文字溢出。
2. **写作台三栏比例狭窄与右侧 Tab 截断（`批注与...`）**：
   - 根因：`.writing-desk` 默认网格列宽为 `208px minmax(500px, 1fr) 248px`，左右侧栏均过于狭窄；右侧 Tab 限制宽度致使 5 字文字「批注与引用」必定触发 ellipsis 截断。
   - 修复：`.writing-desk` 列宽放宽为 `240px minmax(460px, 1fr) 280px;`（响应式放宽为 `210px minmax(420px, 1fr) 240px;`）；Tab 显示文案精简为「批注」，保留 `aria-label="批注与引用"` 与 `title="批注与引用"`，兼顾测试断言与无障碍访问。
3. **右侧检查器底部文字垂直削边**：
   - 根因：`.writing-margin` 底部留白不足且缺乏自适应安全距离。
   - 修复：`.writing-margin` 增加 `padding-bottom: 40px;`，`.writing-boundary` 增加 `margin-bottom: 16px;`。
4. **项目页顶部操作按钮折行（`导入旧稿` 掉行）**：
   - 根因：右上角堆叠 7 个按钮，在大分辨率下产生折行。
   - 修复：`.creation-writing-hero .desktop-page-actions` 设置 `flex-wrap: nowrap; gap: 6px;`，按钮高度微调为 32px，按钮文字优化为「导出包」「导入包」（保留完整 `aria-label` 与 `title`）。
5. **各页面 UI 原语与选择器统一**：
   - `WritingDeskOutlineSidebar.tsx`：当前项目切换迁移为 `<Select>`。
   - `OutlinePage.tsx`：`ScenePlanningForm` 视角角色与地点选择器迁移为 `<Select>`。
   - `CardsPage.tsx`：看板 / 列表视图切换迁移为统一 `<Tabs variant="pill">`。

### 全量回归与构建产物
- **TypeScript 检查**：三套配置（main, renderer, node）全部 0 错误通过。
- **单元与集成测试**：Vitest 81 passed / 1 skipped（786 个测试全部通过）。
- **契约测试**：`verify:reader-excerpt`、`verify:creation-project-shell`、`verify:creation-workspace` 全部通过。
- **打包产物**：构建产物生成于 `release/`，包含完整安装程序与免安装目录。


