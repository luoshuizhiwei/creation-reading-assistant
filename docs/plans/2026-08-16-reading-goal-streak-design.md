# 阅读目标 + streak 打卡 + 提醒通知 — 设计

> 状态：**片 0–1 已实施并验收；片 2–3 待决策/实施**（2026-08-20）。对应路线图 P3.2。
> 前置：P0 收口与真机验证完成；实施可与 P3.1（替换规则接线）并行（文件面不重叠）。

## 0. 关键现状发现（决定本设计的前提）

1. **历史缺口（片 0 已修复）—— `reading_sessions` 本地零写入**：此前 `sessionDao.upsert` 的调用方只有同步拉取
   （`SyncRepository.applySession`）、备份导入（`JsonBridge`）与软删恢复，本地阅读只写
   `reading_progress`。由于 Stats/Home 聚合 `reading_sessions`，纯本地时长当时无法进入统计。
   现在 Reader 已通过 `ReadingSessionRecorder` 写入本地会话。
2. **历史口径分裂（片 1 已修复）**：此前 Repository streak 按 `created_at`，统计页按
   `COALESCE(started_at, created_at)`，Home 与 Stats 今日窗口也各用一套字段。现在阅读日统一为
   occurred 口径，连续阅读算法与 24 小时异常上限集中在 `ReadingStatsPolicy`，SQL 聚合与统计页均排除
   非正数和超过 24 小时的异常会话。
3. **无后台调度基础设施**：项目无 WorkManager 依赖、无 AlarmManager 先例、无静态
   receiver；唯一 NotificationChannel 是 TTS 播放（`tts_playback`，懒创建）。
   `POST_NOTIFICATIONS` 权限已声明且 TTS 路径有运行时申请先例。
4. DataStore 独立 Store 惯例成熟（`ShelfPrefs`/`ContinueReadingStore` 等六处先例）。

## 1. 目标与非目标

**目标**：每日阅读目标（分钟）+ Stats/Home 进度环 + 连续打卡（streak 已算好）+
每日固定时刻提醒通知。

**非目标**：周/年目标、社交分享打卡、补打卡、提醒自定义文案、多时段提醒。

## 2. 分片设计（四片，独立交付）

### 片 0 — 本地会话写入（数据地基，先行）

> **已于 2026-08-20 完成。** 实现采用单一 `ReadingSessionRecorder` 状态机：
> 前台、正文就绪且无错误时才计时；时长用 `SystemClock.elapsedRealtime()`，墙上时间仅生成
> ISO 时间戳；30 秒门槛、5 分钟滚动切段、24 小时异常上限、切书隔离与重复暂停去重均有 JVM 测试。

在阅读器生命周期落一条会话记录，字段对齐同步信封（`revision=1`、`device_id`
本机 id、`payload` 存提升列 JSON），确保后续能被 push 同步：

- 写入点：`ReaderPlatformEffects` 的 ON_PAUSE / ON_STOP / onDispose 兜底链路
  （`persistCurrentProgress` 旁挂一个 `persistReadingSession`），以及在 Reader 内
  每满 5 分钟滚动切段（长会话拆多条，避免中途崩溃全丢）。
- 会话时长来源：内存 `activeReadingMsState`（每秒 +1000，条件 `!isLoading &&
  error==null`，已存在）。`started_at` 取会话首帧时间（进入阅读器或上次切段点），
  `ended_at` 取落库时刻，`duration_ms` 取本段增量。
- 丢弃规则：`duration_ms < 30s` 的尾段不落库（防碎片）；单段 >24h 视为异常截断。
- **不改动** `reading_progress.total_reading_time_ms`（保持现状，历史累计继续取
  `max(total_reading_time_ms, sessions 求和)` 的既有逻辑）。

验收：本地阅读 30 分钟（真机，可临时调短切段阈值）后，Stats 今日/趋势图出现数据、
Home「今日阅读」非零；无阅读时不产生记录；切书/退出重进不丢已计时长。

实施验收（2026-08-20）：全量 JVM 145 套件 / 1387 项、Lint、Debug APK、AndroidTest
编译均通过；真实手机 `c49ac6cf` 以临时内存数据库执行 1 项端到端测试，确认 30 秒会话
经 Recorder → Repository → Room 后，可被总时长和 Home 今日口径查询到。设备没有测试书籍，
因此未擅自导入书籍做 UI 长读；测试未写入用户书库或生产数据库。

### 片 1 — 口径统一

> **已于 2026-08-20 完成。** 阅读日统一为 `COALESCE(started_at, created_at)`；
> `ReadingStatsPolicy` 成为 Repository 与 Stats 页共用的连续阅读/时长有效性策略。

- 「阅读日」统一为 `COALESCE(started_at, created_at)`（与 `StatsPage.computeStreak`
  一致）；`StatsRepository.streakDays` 与 Home/Stats 今日时长全部切到 occurred 口径。
- 旧 `created_at` SQL（`sumCreatedDurationBetween/Since`）保留但不再被 UI 消费
  （同步侧如有依赖另行核对），迁移期在代码注释标注口径。

验收：Home 今日与 Stats 今日同为 occurred 口径；streak 两处实现合一。实现时将纯算法下沉为
`ReadingStatsPolicy`，避免数据层反向依赖 UI 层。真实手机 Room 测试覆盖 started/created 跨日、
started 为空回退、零时长与超过 24 小时异常记录；全量 JVM 147 套件 / 1392 项、Lint、Debug APK
和 AndroidTest 编译均通过，真机口径聚合与 Recorder 持久化 2 项通过，测试未写入用户书库。

### 片 2 — 目标设置与进度环

- 新 `GoalStore`（DataStore `"goal_prefs"`，按 `ImportHistoryStore` 模板）：
  `dailyMinutes: Int = 0`（0=关闭；15/30/60/90/自定义 5-600）、
  `reminderEnabled: Boolean = false`、`reminderMinuteOfDay: Int = 21*60`。
- UI：`ProfileSubPage` 加 `GOAL` 枚举值 + 新子页 `GoalSubPage`
  （目标时长分段选择 + 自定义步进、提醒开关 + 时刻选择、当前 streak 展示）。
- 进度环：Stats 页 `period-tabs` 与 `summary` 之间新增 `item(key="goal")`，
  组件 `components/GoalRingSection.kt`（今日已读 x 分钟 / 目标 y 分钟环形进度 +
  当前连续天数 + 达成态）；`StatsDashboardViewModel` 增加
  `todayMs combine GoalStore` 窄投影。Home 的 `HomeMetricsSection`「今日」格
  追加 `/ 目标分钟` 副标（目标关闭时不显示）。
- 打卡语义：当日累计 ≥ 目标即达成（以片 1 口径），无需手动打卡操作。

验收：目标达成当天进度环满格并有达成态；跨零点后归零重计；关闭目标后 UI 全部隐藏。

### 片 3 — 每日提醒通知

- **调度选型：引入 `androidx.work:work-runtime-ktx`**（标准库、电池友好、免自研
  boot receiver）。这是本功能唯一新依赖；替代方案（AlarmManager+RECEIVE_BOOT_
  COMPLETED 自研）维护成本更高，不取。
- `ReadingGoalWorker`（PeriodicWorkRequest，每日一次，计算下个提醒时刻入链）：
  到点检查 —— 目标开启 && 今日未达成 && 通知权限已授予 → 发通知；否则静默重排。
- 通知：新 Channel `reading_goal`（IMPORTANCE_DEFAULT，App 启动时注册，不放
  `tts_playback`）；标题「今天的阅读目标还差 X 分钟」；点击 deep-link 打开阅读器
  继续读最近的书。达成当天不再提醒；应用正在前台阅读时不提醒。
- 权限：抽取 TTS 路径的 `POST_NOTIFICATIONS` 申请逻辑为公共 helper 复用
  （API 33+，requestCode 区分）。
- 调度注册点：GoalStore 首次开启提醒时 enqueueUniquePeriodicWork（REPLACE），
  关闭时 cancelUniqueWork；App 启动时对账（防止被系统清除后失联）。

验收（真机）：设定 2 分钟后的提醒按时触发；前台阅读中不触发；达成日不触发；
重启后调度恢复。

## 3. 依赖与工作量

| 片 | 依赖 | 预估 |
|----|------|------|
| 0 会话写入 | 无（既有链路挂点） | 0.5–1 天 + 真机验证 |
| 1 口径统一 | 片 0 | 0.5 天 |
| 2 目标+进度环 | 片 1 | 1–1.5 天 |
| 3 提醒通知 | 片 2；新增 work-runtime-ktx 依赖 | 1–1.5 天 + 真机验证 |

合计 3.5–4.5 天。片 0/1 可单独先行合入（即使不做目标功能，也修复了本地统计盲区）。

## 4. 风险与对策

- 会话写入落在高频生命周期回调里 → 仅在 ON_PAUSE/STOP/dispose 与 5 分钟切段点写，
  平时零 IO；写失败不阻断退出（runCatching + AppLog）。
- 同步兼容：新会话进入 push 流（`getDeleted`/active 查询已含 sessions），需在
  ProfileSyncEngine 联调一次「手机读 → 桌面拉」冒烟（并入 A7 真机冒烟清单）。
- WorkManager 与现有 `EpubSizeRepairTask` 进程内任务风格差异 → Worker 只做
  「检查+发通知+重排」，不承载业务逻辑。

## 5. 验收总表

1. 本地阅读进统计（今日/趋势/streak/Home）。
2. 目标进度环正确归零/达成；关闭目标无残留 UI。
3. 提醒按点触发、前台静默、达成静默、重启恢复（真机）。
4. `testDebugUnitTest` + `lintDebug` 全绿；新增：GoalStore 单测、口径统一单测、
   Worker 的「达成/未达成/无权限」三分支单测（WorkManager 测试 artifact）。
