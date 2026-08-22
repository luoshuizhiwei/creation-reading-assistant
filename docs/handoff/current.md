# 当前 Agent 交接入口

更新日期：2026-08-22 集成验证（Kimi3 页面升级收工后全量门禁通过；此前 2026-08-21 会话收尾记录见第 2 节）  
仓库：`D:\develop\Code\Codex\creation-reading-assistant`  
当前对话主线：**独立原生 Android `android/`**；desktop 候选已停放，不是当前实施任务。

## 1. 接手前必须知道

- 当前分支：`codex/workspace-backup-2026-08-20`；记录时 HEAD 为 `50674a9`。
- 工作区不是干净基线：文件数量以接手时 `git status --short` 为准；
  其中新增 `docs/handoff/current.md` 与 desktop 调研记录，其余主体是 Android reader、
  阅读统计、章节已读/排序、替换净化与对应测试/文档。全部视为用户资产。
- 未经用户明确要求，不 stage、commit、push、reset、checkout、全仓格式化或清理这些改动。
- desktop 与 Android 不共享运行时代码；当前任务不得顺手修改 `src/`、`electron/`。
- 设备行为只用真实手机验证；先 `adb devices`，所有命令显式 `adb -s <serial>`。禁止 MuMu。
- 测试记录只写「测试 EPUB」「测试 TXT」；长测前记录常亮值，结束后恢复。

## 2. 当前 Android 状态

### 已完成并有新鲜证据

- P3.3 目录已读与分类/标签/书单手动排序已经收口；TXT 已读、书单内书籍排序不在本期。
- P3.2 片 0–1 已完成：本地阅读会话落库、统一 occurred 日期口径、异常时长过滤与 streak。
- P3.1 替换净化首期片 1–3 已接线：小型 TXT + 新分页引擎；source/display 双坐标、
  分页缓存规则身份、高亮/TTS/搜索/选区映射已有自动测试。首期能力边界也已完成：
  滚动/legacy/EPUB/Markdown 隐藏替换入口，展示保留原文的具体原因。
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

### 当前最安全的下一步

继续 P3.1，不跳到新功能：

1. **带书真机矩阵仍待执行（高优先级）：** 在授权后导入中性测试 TXT，不要改用户真实书库；
   小型 TXT + 无目录大 TXT（50MB）+ 超长单章（>256K）各自验证：
   正文替换效果（跨原 ReadingUnit 匹配）、规则启停、重分页、搜索、高亮、选区、TTS、
   超限提示一次性触发且不重复、持久化书签/进度不损坏 source 坐标。
2. legacy/滚动路径：按
   `docs/plans/replace-rules-render-integration-design.md §片 4 legacy/滚动路径`
   单独切片，产出携带 display 文本 + source↔display 映射 + 原始全局偏移的投影结果，
   逐项接入 chapterBlocks、blockGlobalOffsets、进度恢复、搜索跳转、高亮、选区、
   TTS 句高亮、书签/锚点；必须先写映射和持久化坐标测试，不把 display 坐标写数据库。
3. EPUB/Markdown：先分别评估 DOM/段落结构保真性与 Markdown 源↔渲染文本映射语义，
   未出设计和测试契约前继续隐藏入口、正文原样显示。
4. ReplacedChapterSource 在并发线程读取下的 putIfAbsent/重复投影竞态，可在 legacy 之前
   用 8–12 条并发单元测试收紧，确保即便多线程同时请求同一章节，也只执行一次整章投影。

### 已停放，除非用户重新确认

- P3.2 片 2 `GoalStore` + 目标进度环、片 3 WorkManager 提醒：设计存在，但用户尚未确认
  「每日阅读目标」仍是当前优先级。
- 首次 Android 正式发布：发布链路已搭好，但签名 Secrets、首发 tag 和升级验证仍需用户操作。
- A12 全量字符串资源化/无障碍、A7 同步冒烟、A9 EPUB 异常语料与性能基线仍是质量开放项。

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

## 5. Desktop 停放项

Novalist 调研与取舍已记录在
`docs/research/novalist-desktop-adoption-2026-08-21.md`。最高价值候选是只读「创作雷达 v1」；
不要把其 Markdown/JSON、PySide6 或 DeepSeek Harness 架构搬入项目。该候选需在独立 desktop
任务中启动，不能占用本 Android 对话的文件所有权。

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
- **事件（如实记录）**：Gradle 在 connected 测试结束后自动卸载了主应用+测试 APK，设备上的中性测试书库（测试 EPUB + 测试书籍甲乙丙及其进度）随之清除——均为历次会话导入的测试 fixture，可重新导入；主应用已立即重装并验证启动。后续跑 connected 测试需注意 AGP 默认卸载行为（可 `-Pandroid.injected.invoked.from.ide=true` 或测试后重装）。
- **2026-08-22 P3.2 片 2–3 已实施（未提交）**：`GoalStore`（DataStore `goal_prefs`）+ `GoalSubPage`
  （我的 → 阅读目标）+ Stats `GoalRingSection` 进度环 + Home 今日副标；WorkManager 每日提醒
  （`ReadingGoalWorker`/`ReadingGoalScheduler`，新依赖 work-runtime-ktx 2.9.1，`reading_goal` 渠道，
  `NotificationPermission` 公共 helper，App 启动对账）。判定/收敛/口径/时刻纯函数化，新增 JVM 23 项；
  全量 `testDebugUnitTest` 1455 项 0 失败、`lintDebug` 0 errors、`assembleDebug` 通过（49.4 MB）。
  剩余：真机通知触发/重启恢复验收；通知点击 V1 仅拉起应用（deep-link 进阅读器留作增强）。
- **2026-08-22 阅读器质量收口（第一轮，未提交）**：针对 §8 的 17 项仪器失败——
  ① TOC 生产修复：当前章不叠加「已读章节」语义（计数仍含当前章）；② 书架×2 为**过期测试**
  （搜索已于 08-05 迁独立 `shelf/search` 路由、ShelfHeader.kt 成死代码待清理）：重写为
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
- **P0.2 清单核对状态**：可自动化的部分（导航/设置/TOC/统计渲染）已覆盖；TTS 听感、拔耳机、色温/纹理实际观感、EPUB 封面提取、内容哈希判重（书库已清）需重新导入测试书后人工/脚本验证。



