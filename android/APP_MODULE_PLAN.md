# 创作阅读助手 · 原生移动端（Android）完整页面结构与功能体系规划

> 范围说明：本文档是对「创作阅读助手」原生 Android 端的**完整架构与功能蓝图规划**，覆盖一级/二级页面、阅读器核心功能、辅助模块、导航逻辑、模块间交互与可维护架构。技术栈（Kotlin/Compose/Hilt/Room）保持不变，UI 布局以 `mobile/`（网页版）为对齐基准，视觉采用「墨韵·素笺」设计系统。
>
> 文档定位：**规划**（Plan），不含实现代码；落地时按本蓝图逐模块推进。

---

## 一、整体架构（Architecture）

### 1.1 技术栈与分层

| 层 | 技术 | 职责 |
|---|---|---|
| UI 层 | Jetpack Compose + Material3 | 声明式界面、屏幕组合、交互反馈 |
| 状态/VM 层 | ViewModel + StateFlow + Hilt | 持有 UI 状态、调度用例、生命周期安全 |
| 领域/仓库层 | Repository（接口） | 聚合数据源、暴露挂起函数/流、主线程安全 |
| 数据层 | Room（DAO + Entity）+ DataStore | 本地持久化（离线优先）、设置项 |
| 远程层 | Retrofit + OkHttp + Kotlin 串行化 | 桌面端/WebDAV 同步、配对握手 |
| 能力层 | EpubParser / TTS 引擎 / ML Kit / CameraX | 解析、朗读、识别、扫描 |

### 1.2 分层数据流

```
UI (Composable)
   │  collectAsStateWithLifecycle / 事件回调
   ▼
ViewModel (StateFlow<UiState>)  ◀── 注入 Repository
   │  viewModelScope + Dispatchers.IO
   ▼
Repository（聚合 Room + Remote）
   ├── Room DAO  ──► Entity（本地单一事实源，离线优先）
   └── Remote API ──► 仅在联网/同步时写入或回写
```

**原则**：UI 永远只与 ViewModel 的状态流交互；数据变更先落本地、再择机同步；远程失败不影响本地可用。

### 1.3 可维护性设计准则

- **单一事实源**：每个领域实体只有一处 Room 表，Repository 是唯一读写入口。
- **屏幕即函数**：每个页面 = 一个 `@Composable` 顶层函数 + 对应 `*ViewModel`，不跨屏持有状态。
- **二级页面用内部状态**：同 Tab 内的子页/详情/编辑器用 `var currentSubPage` 或 `ModalBottomSheet` 内部切换，**不新增导航路由**（避免导航图膨胀）。仅「跨 Tab 跳转」「全屏阅读器」「二维码配对」才用路由。
- **设计令牌集中**：颜色/圆角/阴影/纸纹全部来自 `Theme.kt` + Compose `MaterialTheme`，禁止散落硬编码色值。

---

## 二、信息架构总览（完整页面树）

```
AppNavigation (NavHost, 单一导航图)
├── 一级页面（底部 5 Tab，默认进 首页）
│   ├── 首页  home
│   │   └── （自包含卡片，点击多跳转其它 Tab 或阅读器，无独立二级路由）
│   ├── 书架  shelf
│   │   ├── 搜索态（同级覆盖）
│   │   ├── 多选态（同级覆盖）
│   │   ├── 排序/筛选 BottomSheet
│   │   ├── 书籍操作 BottomSheet
│   │   ├── 书籍详情 BottomSheet（BookDetailSheet）
│   │   ├── 批量标签管理 BottomSheet（BatchTagManager）
│   │   └── 导入历史 BottomSheet
│   ├── 灵感  inspiration
│   │   ├── 列表态（默认）
│   │   ├── 详情态（InspirationDetailPanel，内部切换）
│   │   └── 编辑/新建态（Editor，内部切换）
│   ├── 统计  stats
│   │   └── 周期切换（周/月/年/总计，内部 state，无新路由）
│   └── 我的  profile
│       └── 子页（内部 state 切换，不新建路由）：
│           ├── 同步（配对码 / 局域网发现 / WebDAV 配置）
│           ├── 外观（主题 / 纸张质感）
│           ├── 阅读设置（字体 / 行距 / 翻页 / TTS 默认）
│           ├── AI 设置（启用 / Key / 模型 / 提示词）
│           ├── 书单管理
│           ├── 分类管理
│           ├── 阅读与笔记
│           ├── 关于与隐私
│           └── 诊断
├── 全屏覆盖层（不显示底栏）
│   ├── 阅读器  reader/{bookId}
│   │   ├── 顶栏（可收起）：返回 / 书名+章节 / 目录 / TTS / 笔记 / 设置
│   │   ├── 正文区：EPUB 章节 / 纯文本 / Markdown（降级）
│   │   ├── 底部 TTS 播放条
│   │   └── 底部弹层（ModalBottomSheet / AnimatedVisibility）：
│   │       ├── 目录/章节列表（TOC）
│   │       ├── 笔记与标注列表
│   │       ├── AI 助手面板（提问/总结/解释选中）
│   │       ├── AI 解释选中文本面板
│   │       ├── 灵感速记面板（选中文本 → 新建灵感）
│   │       └── 阅读器设置面板（字体/行距/主题/翻页）
│   └── 二维码配对  pairing（扫描桌面端配对码）
└── （未来扩展）设置/登录等独立路由按需追加
```

---

## 三、一级页面规划（布局与导航逻辑）

### 3.1 首页（Home）
- **地位**：默认落地页，信息聚合入口。
- **布局分区（自上而下）**：
  1. 阅读概览双卡（今日已读时长 / 本周目标进度）。
  2. 继续阅读（横滑卡：封面 + 书名 + 进度条 + 续读按钮 → 跳 `reader/{bookId}`）。
  3. 统计网格（本周新增 / 在读 / 已读完 / 今日阅读）。
  4. 最近灵感（最近 3 条 → 跳灵感 Tab）。
  5. 已阅读完成（横滑卡）。
- **导航逻辑**：续读卡、统计卡、灵感卡均为「跳转锚点」，把流量分发到对应 Tab / 阅读器。

### 3.2 书架（Shelf）
- **布局**：
  1. 顶部 header：标题 + 搜索 / 导入(＋) / 更多(⋯)；搜索态切换为输入框 + 取消。
  2. 导入队列浮动卡（有活跃任务时显示）。
  3. 工具栏：N 本书 + 排序选择 + 筛选（书单/分类/标签 chip）+ 网格/列表切换。
  4. 书封网格（3 列）或列表视图（BookTile：封面/标题/作者/进度/状态徽章）。
  5. 多选态：全选/清空 + 底部批量栏（加书单/分类/标签/下载/清缓存/删除）。
  6. 书籍操作 / 详情 BottomSheet；导入历史 BottomSheet；空状态。
- **导航逻辑**：点书 → `reader/{bookId}`；导入(＋) → 文件选择器/从电脑下载；更多(⋯) → 排序/筛选/视图切换。

### 3.3 灵感（Inspiration）
- **三态内部切换**（不新建路由）：列表 → 详情 → 编辑/新建。
- **列表态**：类型筛选 rail（全部 + 各类型带计数）+ 排序弹层 + 卡片列表（类型标签/时间/标题/摘要/来源/标签）+ 三态空态。
- **详情态**：标题/正文/来源/标签/类型状态 + 返回/编辑/更多（查看/复制/查看来源/删除）。
- **编辑态**：标题/类型/状态/正文/标签/来源 + 未保存退出确认。
- **导航逻辑**：来自阅读器「灵感速记」的新建 → 预填来源与选中原文。

### 3.4 统计（Stats）
- **布局**：周期 tabs（周/月/年/总计）+ 周期翻页(‹/›，总计禁翻) + 概要四卡（时长/天数/读过/已读完）+ 连续阅读卡 + 趋势柱状图（自绘，无图表库）+ 书籍状态条 + 阅读与创作四宫格 + 空态。
- **导航逻辑**：无二级路由，全部周期切换为内部 state；只读展示。

### 3.5 我的（Profile）
- **主页面**：分组卡片列表（同步卡 / 外观 / 阅读设置 / AI 设置 / 书单管理 / 分类管理 / 阅读与笔记 / 关于隐私 / 诊断）。
- **子页**：点击条目 → 内部 state 切到对应子页（顶部返回箭头 + 标题 + 保存）。共 9 个子页，均不新建路由。
- **导航逻辑**：同步卡「配对」→ 路由 `pairing`；其余子页内部切换。

### 3.6 阅读器（Reader，特殊地位）
- 非 Tab，点书后**全屏覆盖**；路由 `reader/{bookId}`；退出返回原 Tab。详见第四节。

---

## 四、阅读器核心功能体系（重点模块）

### 4.1 页面结构（全屏 Scaffold）
- 顶栏（可收起）：返回 ← / 书名(+章节) / 目录 ☰ / TTS / 笔记标注 / 设置 ⚙。
- 正文区：复用电商 `EpubParser` 取章节与内容；纯文本/Markdown 降级为滚动文本。
- 底部 TTS 播放条：播放/暂停、进度、语速、关闭（复用现有 TTS 引擎）。
- 底部弹层（见第二节树）：TOC / 笔记标注 / AI 助手 / AI 解释 / 灵感速记 / 设置。

### 4.2 核心功能清单（你点名的部分 + 配套）

| 功能 | 实现要点 | 数据落点 |
|---|---|---|
| **翻页** | EPUB 章节上/下章切换；滚动/分页两种模式（设置可选）；进度实时写 `BookProgressEntity` | Reading 库 |
| **书签** | 章节内任意位置插入书签；书签列表弹层；点击跳转 | `BookProgressEntity` / 书签子表 |
| **字体调节** | 字号、行距、页宽、字体族；实时预览；设置持久化（DataStore） | ReaderSettings |
| **夜间模式** | 阅读器独立主题：跟随系统 / 强制亮 / 强制暗；纸色背景随主题切换 | ReaderSettings + Theme |
| **TTS 朗读** | 复用现有 `TextToSpeech` 封装；播放条控制；语速/暂停/续读；退出保持进度 | Reading 库 |
| **笔记** | 选中文本 → 新建笔记（NoteEntity）；列表增删改查 | `NoteEntity` |
| **标注/高亮** | 选中文本 → 高亮（HighlightEntity）；与笔记并列管理 | `HighlightEntity` |
| **目录 TOC** | 解析章节树，跳章；显示当前章 | EpubParser |
| **进度** | 自动保存阅读位置、时长、已读百分比；驱动首页「继续阅读」与统计 | `BookProgressEntity` + 会话表 |
| **AI 助手** | 提问/总结/解释选中文本；调用 AI 设置（Key/模型/提示词） | AI 设置仓库 |
| **灵感速记** | 选中文本一键新建灵感，预填来源与原文摘录 | `InspirationEntity` |
| **阅读器设置** | 字体/行距/主题/翻页方式集中面板 | ReaderSettings |

### 4.3 交互逻辑
- 选中文本 → 弹出操作条（笔记 / 高亮 / AI 解释 / 灵感速记），形成「读—标—记—想」闭环。
- 退出阅读器时自动落盘进度，下次从首页「继续阅读」无缝续读。
- 所有弹层用 `ModalBottomSheet` 统一管理，避免叠加冲突。

---

## 五、辅助功能模块

| 模块 | 职责 | 现状 / 规划 |
|---|---|---|
| **同步** | WebDAV / 局域网 / 配对码 三种方式；与桌面端双向同步书籍、进度、笔记、灵感 | 远程层已搭（SyncApi/SyncRepository/SyncConfigStore），需补 UI 写回与冲突策略 |
| **导入** | 本地文件导入（EPUB/TXT/MD）、从电脑下载、导入历史 | 文件导入走 SAF；从电脑依赖局域网同步 |
| **AI 助手** | 阅读中总结/解释/问答；灵感辅助创作 | UI 已就位，需接后端或本地模型 |
| **全局搜索** | 跨书籍/灵感/笔记检索（原版为覆盖层） | 规划为全局 Overlay 路由 |
| **设置体系** | 外观/阅读/AI 三类设置持久化 | 当前为会话态，需落地 DataStore 仓库 |

---

## 六、导航逻辑与模块间交互

### 6.1 导航结构
- 单一 `NavHost`，`startDestination = home`。
- 5 个 `TopLevelRoute` 构成底栏；`reader/{bookId}` 与 `pairing` 为覆盖层（不显示底栏）。
- 二级页面**全部内部 state 切换**，不污染导航图。

### 6.2 关键交互流（保证流畅）
1. **点书读**：书架/首页续读卡 → `navController.navigate("reader/{bookId}")` → 全屏，底栏消失 → 返回原 Tab。
2. **读中速记**：阅读器选中文本 → 灵感速记面板 → 写 `InspirationEntity` → 灵感 Tab 即时可见（共享 Repository）。
3. **同步刷新**：我的 → 同步 → 配对/WebDAV → 成功 → 书架/灵感/统计通过同一 Repository 流自动刷新（StateFlow 驱动）。
4. **设置生效**：阅读设置改字体/夜间 → `ReaderSettings` 持久化 → 阅读器 Compose 重组即时反映。

### 6.3 状态共享
- 跨 Tab 数据一致性靠 **Repository 单一事实源 + StateFlow 广播**，而非跨屏传参。
- 阅读进度变更后，首页「继续阅读」与统计页通过各自 ViewModel 订阅同一流自动更新。

---

## 七、数据架构

### 7.1 实体（已存在，对应 `data/local/entity`）
- `CatalogEntities` → 书籍 `BookEntity`
- `ReadingEntities` → 正文 `BookContentEntity`、进度 `BookProgressEntity`、笔记 `NoteEntity`、高亮 `HighlightEntity`、阅读会话
- `InspirationEntities` → `InspirationEntity`
- `SyncEntities` → 同步状态/配对信息
- `TaxonomyEntities` → 分类/标签/书单

### 7.2 Repository 层（已存在）
- `BookRepository`（书籍/进度/封面/导入）
- `StatsRepository`（统计指标计算）
- `SyncRepository`（同步编排）
- `InspirationRepository`（灵感 CRUD，建议显式化）
- 各 `*ViewModel` 仅依赖 Repository，不直接碰 DAO。

### 7.3 设置持久化（规划补齐）
- 新增 `SettingsRepository` + DataStore：
  - `AppearanceSettings`（主题模式、纸张质感）
  - `ReaderSettings`（字号、行距、翻页、夜间、TTS 默认）
  - `AISettings`（启用、Key、模型、提示词）
- 现状：外观/阅读/AI 设置多为会话态，**需落地 DataStore 才能跨重启保持**。

---

## 八、性能与体验优化

| 维度 | 目标 | 手段 |
|---|---|---|
| 启动 | 冷启 < 3s | 懒加载非首屏 Tab、Hilt 图轻量化、避免 `Application` 重初始化 |
| 内存 | 核心 < 100MB | 图片用 Coil 约束尺寸；列表 `LazyColumn` + `key`；阅读器大文本分页加载 |
| 电量 | < 5%/h 活跃 | TTS/同步批处理；后台任务用 `WorkManager` |
| 离线优先 | 无网可用 | 本地 Room 单一事实源；同步择机回写 |
| 流畅度 | 60fps | 列表 `animateItemPlacement`；弹层用 `AnimatedVisibility`；避免重组抖动 |

---

## 九、设计系统「墨韵·素笺」

集中定义于 `Theme.kt`，对齐 `mobile/` 的 CSS 原值：

| 语义 | 亮色 | 暗色 |
|---|---|---|
| 纸底 paper | `#faf8f2` | `#141311` |
| 墨字 ink | `#1a1917` | `#f3efe6` |
| 靛青印章（强调） | `#3a5670` | 同族加深 |
|  Success / Warning / Error | 语义色独立定义 | 同步适配 |

- 圆角/阴影走 `--md3-shape-*` / `--app-soft-shadow*` 等价 Compose 令牌。
- 纸纹：在原 Compose 主题基础上叠加轻量噪点纹理（参考 `mobile/` 的 `paper-grain.css` 思路，用 `Modifier.drawWithContent` 实现，不重复造多层 `::before`）。
- **禁止散落硬编码色值**，全部走 `MaterialTheme.colorScheme.*`。

---

## 十、演进路线（把降级项补成真功能）

当前已交付「布局对齐」版；以下为后续补齐项，按依赖排序：

1. **设置持久化**：新增 `SettingsRepository` + DataStore，落实外观/阅读/AI 设置跨重启保存。
2. **分类/标签/书单写回**：书架批量操作从「仅展示」升级为真实写库 + 同步。
3. **同步闭环**：WebDAV/局域网配对 UI 写回 `SyncConfigStore`，实现双向同步与冲突策略。
4. **AI 后端接入**：阅读器 AI 助手/AI 解释接真实接口或本地模型，移除占位。
5. **从电脑下载**：依赖局域网同步能力打通。
6. **全局搜索 Overlay**：跨书籍/灵感/笔记检索。
7. **测试体系**：按 android-native-dev 规范补单元测试 + Compose UI 测试，目标崩溃率 < 1.09%、ANR < 0.47%。

---

## 附：落地节奏建议

- **阶段 A（已完成）**：导航骨架 + 首页 + 5 屏布局对齐 + 墨韵主题 + 完整打包。
- **阶段 B（建议下一步）**：设置持久化（#1）→ 分类标签写回（#2）→ 同步闭环（#3），让 App 从「能看」变「能用」。
- **阶段 C（增强）**：AI 后端（#4）→ 下载（#5）→ 搜索（#6）→ 测试（#7）。

> 本蓝图与现有 `mobile/` 网页版布局保持一致，原生端仅替换底层实现；后续若网页版改版，以本蓝图为对齐基准同步更新。
