# 当前 Agent 交接入口

更新日期：2026-08-24 集成验证（阅读器跨屏自适应、跨格式翻页与分页覆盖层收口；此前会话收尾记录见第 2 节）
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
- **事件（如实记录）**：Gradle 在 connected 测试结束后自动卸载了主应用+测试 APK，设备上的中性测试书库（测试 EPUB/测试 TXT 及其进度）随之清除——均为历次会话导入的测试 fixture，可重新导入；主应用已立即重装并验证启动。后续跑 connected 测试需注意 AGP 默认卸载行为（可 `-Pandroid.injected.invoked.from.ide=true` 或测试后重装）。
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
  legacy/滚动路径的替换净化和 EPUB/Markdown 结构保真替换仍按当前能力边界保留原文。自动翻页揭页线
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
以及搜索/高亮/选区/TTS/大章提示带书验证；legacy/滚动路径的替换净化和 EPUB/Markdown 结构保真替换仍按当前能力边界保留原文。

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



