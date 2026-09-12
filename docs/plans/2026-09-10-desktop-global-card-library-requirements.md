# desktop — 创作助手 v2.0：全局卡片库与写作体验需求

状态：**P0、M0、阶段 1、阶段 2、阶段 3（大纲与沉浸写作 + 真实 Windows 打包验收）、阶段 4 全切片 + 集成收口均已完成；最终 Windows 打包产物为 `release-beta-stage4-final`。**
日期：2026-09-10（2026-09-12 更新）
适用产品线：Electron 桌面端（`src/`、`electron/`）
来源：用户提供的《桌面端创作助手·需求规格说明书 v2.0》；本文同时记录 2026-09-10 的当前代码和隔离 Electron 功能核验，避免把“源码存在”或“旧脚本失败”误列为产品缺失。

## 1. 适用范围与优先级

本需求将桌面端定位为面向中文小说/故事作者的、本地优先的创作工作台。作者的角色、地点和世界观设定是跨作品长期复用的资产；写作流程中应能不离开正文地查阅和修改这些资产。

本文覆盖并取代 `2026-08-09-desktop-creation-workbench-phase-1-spec.md` 中以下旧约束：

- 卡片仅属于一个项目；
- 跨项目复用只能复制；
- 自定义卡片类型和关系类型可按项目隔离。

卷、章、场景、正文、大纲和写作统计仍属于项目。未被本文明确修改的 P1 安全约束继续有效，包括本地优先、正文候选评审不直接覆盖、危险操作的保护快照、renderer 不直接操作文件系统及项目数据完整性门禁。

## 2. 已确认的目标模型

```text
世界观圣经（全局）
  └─ 卡片库
       ├─ 卡片类型（内置与自定义，均全局）
       ├─ 卡片（全局、稳定 ID）
       └─ 卡片关系（全局、有向）

创作项目（单部作品）
  ├─ 大纲：卷 > 章 > 场景
  ├─ 场景正文
  └─ 项目—卡片关联（M:N，只引用全局卡片）
```

不变量：

1. 修改全局卡片后，所有项目中的同一张卡片立即显示新内容。
2. 在项目内“移除关联”只删除项目—卡片关联，绝不删除全局卡片本体。
3. 全局卡片删除前必须二次确认，并展示被多少项目使用及受影响的关系、场景引用和附件；不得悄然留下悬空引用。
4. 新的 v10 迁移必须先创建并验证可恢复备份；任一步失败都不得激活半迁移数据库。
5. 现有卡片、关系、字段值、别名、批注引用和项目包数据均不得因迁移丢失。

## 3. 功能需求与经验证的当前实现基线

“现有”表示 2026-09-10 的源码核验结果；带有“已核验”的能力还经过隔离 Electron 的真实点击、IPC 和临时 SQLite 数据验证。它们并不等于 v2.0 增量已验收。

### 3.1 已通过的现有功能路径

隔离 profile（不接触用户真实数据）中已确认：

- 可载入演示项目；可从项目首页完整走完三步向导创建项目；默认章和场景会打开写作台。
- 场景正文可输入、`Ctrl+S` 保存，打字机与专注开关状态正确；进程重启后项目和正文仍可读回。
- 概览、大纲、设定卡、写作统计、版本历史均能打开；设定卡页可真实创建并显示新卡片。
- 本地校对面板可打开、完成首轮项目扫描并关闭，运行时未捕获 renderer 异常。
- `npm run build`、项目首页、卡片、编辑器、校对、统计、版本历史和 P1 生命周期契约均已通过。

### 3.2 已完成：UI 巡检脚本与改版界面重新对齐

`scripts/round4-capture.mjs`、`scripts/probe-round10.mjs`、`scripts/electron-smoke.mjs` 曾因界面改版后的选择器或无障碍名称未同步而错误报红，现已修复并完成新鲜复验：`npm run build`、`probe-round10` 全部通过、`electron-smoke` 12/12 通过、Round 4 浅色/深色与 1024×768 视觉巡检退出码为 0。

1. 项目首页按钮不再输出旧的 `.project-home-create-first` / `.project-home-create-demo` class；按钮仍以“新建第一个项目”“载入演示项目”实际存在。
2. 向导现在是 `role="dialog"`、名称“新建作品”并使用 `creation-wizard-dialog`，旧脚本仍等待不存在的 `.creation-wizard`。
3. 子导航已改名为“设定卡”“写作统计”，校对按钮的可访问名称为“本地校对”；旧脚本仍匹配“卡片”“统计”“校对”。

P0 已改为稳定的 role/name 或状态条件等待，并额外修复了“写作台先挂载、批注后提交”时不重读当前场景批注的真实回归。后续 UI 改版仍必须同步这些脚本，不能把旧定位失败作为产品功能损坏的证据。

| 模块 | v2.0 需求 | 当前基线 | 本轮缺口 |
|---|---|---|---|
| 世界观圣经 | 应用级“卡片库”入口；按类型筛选、列表/画廊、字段全文搜索、使用项目数、全局新建与详情 | 已实现应用级卡片库、类型筛选、标题/别名/字段搜索、使用项目账册、全局新建/编辑、全局附件/单封面、30 天回收站及项目包冲突映射；阶段 1 打包旅程已验收 | 画廊式封面展示仍未实现 |
| 卡片类型与关系 | 内置与自定义类型、关系均为全局资产 | v10 已将内置/自定义类型、关系类型、卡片与卡片关系统一为全局资产；项目通过 `project_card_links` 投影；删除影响、恢复与到期清理已实现；打包应用跨项目与删除旅程已验收 | 阶段 1 无开放缺口 |
| 写作速查 | `Ctrl+Shift+K`/工具栏打开 280px 起、最大 480px 的可调右面板；展示本场景、本项目和全局搜索结果；可内联编辑、800ms 自动保存、关联项目/加入场景 | 已实现独立索引抽屉、快捷键、拖拽宽度、本场景/本项目/全局搜索、内联编辑与错误保留、项目/场景关联、正文焦点恢复；打包版连续写作旅程已验收 | 阶段 2 无开放缺口 |
| 大纲 | 场景摘要、卷/章/全书字数汇总、场景状态、Markdown 大纲导出 | 树/卡板、拖拽、场景字数、章节字数、场景目标字数、任务卡和**章节**状态已存在 | `scenes` 无摘要和状态；没有卷/全书汇总展示与 Markdown 大纲导出。场景状态不能误用为现有章节状态 |
| 写作沉浸 | 专注模式隐藏应用左导航及所有右侧栏，保留极简状态条，ESC 退出；打字机模式；底部场景目标进度 | 已核验写作台的专注状态切换和打字机滚动开关；打字机将光标滚到 `.writing-scroll` 中央 | 要补应用壳级专注/ESC 验收、极简状态条及内联目标进度；打字机不应作为从零开发项 |
| 全书预览 | 独立只读通读页，按章节渲染并可打印 | 已有结构化成稿导出（纯文本/审阅 Markdown） | 没有应用内全书只读预览或打印版式 |
| 统计 | 过去 30 天每日新增字数折线、总目标进度、场景完成度分布 | 已核验“写作统计”页可读取；已有总目标进度、连续写作、章节状态分布和最近 14 天柱状图 | 14 天柱图要升级为 30 天趋势；场景状态分布依赖场景状态模型 |
| AI | 润色、扩写、一致性检查保留；新增续写、缩写/精简、角色一致性；结果只进入候选评审；速查面板打开的卡片纳入上下文 | 已有润色/扩写候选评审、通用一致性报告和正文/任务卡/关联卡/批注上下文包；速查中显式打开的卡片现单列为发送前可排除组；同时修复场景雷达 AI 确认弹窗误挂批注页签的问题 | 缺续写、精简及以卡片人格/动机为依据的专项一致性提示 |
| 校对 | 全书扫描、人名一致性、按位置忽略 | 已核验“本地校对”面板能完成首轮扫描；当前 `proof.query` 在未指定 `sceneId` 时已扫描全项目全部场景，并汇总问题 | UI 需明确“全书扫描”与结果总数；缺别名/疑似错拼检测及持久化的按位置忽略 |
| 关系图 | 过滤卡片类型、节点展开摘要的关系可视化 | 仅有关系管理表单 | 近期迭代，需新图布局与交互模块 |

## 4. v10 数据架构与迁移要求

### 4.1 目标表

v10 至少包含以下逻辑实体：

```sql
global_cards           -- 全局卡片，不再有 project_id
project_card_links     -- project_id + card_id 的 M:N 关联
global_card_relations  -- 全局卡片之间的有向关系
global_card_types      -- 全局内置与自定义类型
```

迁移时可保留现有表名或采用新表名，但公开 TypeScript 模型不得继续把卡片、关系或自定义类型描述为“项目私有”。`cards.list` 的 `projectId` 改为可选：省略时返回全局库，提供时返回该项目关联卡片。`card.create` 的 `projectId` 改为可选；省略时仅创建全局卡片。新增 `card.unlink { projectId, cardId }`；`cardTypes.list` 不再接收项目 ID。

### 4.2 迁移流程

1. 在关闭/排空 workspace 写操作后，创建含 SQLite 数据库、WAL/SHM 和资源目录的校验备份；备份失败即中止。
2. 用 `BEGIN IMMEDIATE` 在临时或可回滚的迁移路径中创建全局表和索引。
3. 将每张旧项目卡片保留原稳定 ID 迁到全局表，并为其原项目写入一条 `project_card_links`；复制其字段、别名、标签、修订、删除状态和时间戳。
4. 迁移自定义卡片类型、关系类型和卡片关系；内置类型/关系保持单一全局记录。所有关系端点必须存在，迁移后跑外键/逻辑完整性检查。
5. 保留场景任务卡中的视角、地点和出场卡片 ID，以及批注的 `card_id`；迁移后校验其引用仍指向存在的全局卡片。不能静默丢弃不一致记录，应在迁移报告中列出。
6. 只有计数、ID 映射、关系引用和完整性检查均通过后，才将 `PRAGMA user_version` 升为 10 并激活；失败时继续使用 v9。
7. 增加 v9→v10 正常、空库、重复/损坏关系、中途故障回滚、备份失败、迁移后重启及项目包回归契约测试。

### 4.3 关联、删除和项目包

- 项目内关联和解除关联必须幂等，并返回当前使用项目数。
- M1-F 已实现全局删除影响预览：展示使用项目数、卡片关系数、场景任务卡/批注引用数和资源影响。确认后移除全部项目关联并进入全局回收站；30 天内保留关系、场景引用、批注和资源，恢复时重建仍存在的原项目关联；到期后清理关系、场景引用并解除批注关联，资源文件经持久 GC 队列删除、失败则在下次打开工作区重试。
- `.cra` 导出只携带该项目关联卡片的**快照**及其需要的类型、关系和资源；不得导出同一世界观中未关联的卡片。
- M1-G 已实现 `.cra`/加密项目包的稳定 ID 预检：卡片字段、内容快照及全局附件均相同才自动复用；异内容必须逐卡选择保留本机、导入副本或取消，回收站中的同 ID 卡片只能导入副本或取消。副本会统一重映射场景任务卡、关系、快照、批注和附件，结果页展示源/目标 ID；数据库事务会再次校验选择，禁止静默覆盖和半写入。

### 4.4 附件与封面

M1-E 已将卡片封面和附件落入独立的 `global_card_resources`，文件路径为 `resources/cards/<cardId>/...`；每张卡片最多一个 `cover`，普通项目附件继续留在旧 `resources` 表。项目页面只读投影全局资产，不提供删除入口；全局库二次确认移除时由主进程同时删除记录和文件。资源一致性扫描与工作区备份覆盖全局文件，项目包只携带当前项目已关联卡片的全局资源快照，不携带无关卡片资产。M1-F 已补齐卡片本体的 30 天保留和到期物理文件清理：软删除期间资产继续存在，永久清理先登记 GC 队列，再安全删除卡片目录内文件。

## 5. 分阶段交付与验收

### 阶段 P0：恢复可信的 UI 验收（已完成）

1. 已用稳定 role/name 与状态条件替换旧样式 class、旧 dialog 和旧名称假设。
2. 已修复 `round4-capture`、`probe-round10`、`electron-smoke`，并恢复演示项目伏笔 chip 的批注刷新回归。
3. 已通过隔离 Electron 冒烟、搜索/跨项目卡片、阅读/写作视觉巡检及窄窗口截图验证。
4. P0 不计入 v2.0 产品功能交付；它恢复的是后续开发所需的可信验收基础。

### 阶段 0：冻结数据决策和迁移夹具

- 明确内置卡片类型的准确清单、删除可恢复性、全局封面资产和 `.cra` 合并冲突策略。
- **M0.1 已完成并复验：**用两个隔离项目的 v9 fixture 锁定项目私有卡片/自定义类型、别名、标签、字段、修订、关系、场景任务卡、批注和**附件元数据**；关闭重开后执行 `workspace.check()`，并验证项目 A 的包不泄漏项目 B 的这些记录。
- **M0.2 已完成并复验：**夹具现为 A/B 各一份确定性真实附件；资源扫描在关闭重开后零问题，项目 A 文件包仅包含 A 的附件，导入全新 workspace 后文件字节、大小、哈希和 `workspace.check()` 均成立。门禁为资源完整性 13、项目包 10、文件项目包 19、卡片 11、schema 3 与 build。
- **M0.3 已完成并复验：**v9 双项目基线已通过现有备份→恢复完整往返；备份 manifest 记录 SQLite 与 A/B 附件的大小/哈希，恢复后项目隔离、资源扫描、附件字节和 `workspace.check()` 均成立。备份恢复契约为 43 项，相关资源/卡片/schema/build 门禁均通过。
- **M0.4 已完成：**迁移 seam、现有项目作用域和四项产品输入已整理并确认，详见 [`2026-09-10-desktop-global-card-library-migration-preflight.md`](2026-09-10-desktop-global-card-library-migration-preflight.md)。
- **M1-A 已完成实现并复验：**schema v10 增加 `project_card_links`，既有卡片按稳定 ID 回填原项目关联；升级前关闭/checkpoint workspace，并在同级目录创建带逐文件哈希清单的 v9 可恢复备份；关系、场景任务卡、批注和附件引用异常会令事务整体回滚。迁移实现还会识别并先备份、再修复早期开发版 v10 的残留项目所有权结构；`card_types`、`relation_types`、`cards` 与 `card_relations` 不再以项目外键拥有全局实体。正常双项目、空库、损坏引用、失败回滚、备份失败、残留 v10 修复及删除项目后全局资产保留共 8 个迁移契约已纳入 `verify:beta`。
- **M1-B 已完成实现并复验：**公共类型、CreationWorkspace、IPC/preload、service 与 Zustand card slice 已统一支持全局 list/create、项目投影以及幂等 link/unlink；卡片返回 `linkedProjectIds` 与 `usageCount`，全局类型/关系/卡片事件会通知所有相关项目 watcher。项目仍引用卡片的场景计划、批注或资源会阻止解除关联；项目包与卡片导入导出已按关联投影读取。该切片的新鲜门禁为全局卡片 7/7、迁移 8/8、卡片 11/11、生命周期 17/17、导入 18/18、项目包 10/10、文件项目包 19/19、编辑器 5 个运行时契约与 113 个单元契约、Vitest 795/795（另 4 项因本机 Node/Electron 原生模块 ABI 不同按既有规则跳过）、build 与完整 `verify:beta` 通过，生产依赖审计为 0。M1-C–M1-G 已在此公共数据链上继续交付全局库 UI、项目关联、全局资源、删除恢复和项目包稳定 ID 冲突闭环。
- **M1-E 已完成实现并复验：**新增与项目生命周期解耦的 `global_card_resources`，全局库可添加附件和设置唯一封面，项目卡片页只读显示共享资产并禁止从项目作用域移除。公共资源 Interface 以可选 `projectId` 区分旧项目附件与全局卡片资产；主进程约束全局路径必须位于对应卡片目录。资源扫描、备份恢复和项目包文件往返均覆盖全局资产，项目包排除未关联卡片资源。新鲜门禁为资源 6/6、资源一致性 14/14、迁移 8/8、项目包 10/10、项目包文件 19/19、备份恢复 43/43、全局库 7/7、定向 UI 6/6、完整 Vitest 801/801（另 4 项按既有 ABI 条件跳过）、build、生产依赖审计 0 与完整 `verify:beta` 通过。未生成打包 EXE，因此仍不构成安装包 UI 验收。
- **M1-F 已完成实现并复验：**全局卡片删除前读取跨项目影响，确认后保存原 `project_card_links` 并软删除卡片；关系、场景任务卡、批注和全局资源在 30 天内保持可恢复。全局回收站无需项目 ID 即可恢复卡片和仍存在的原项目关联。到期清理会移除关系和场景卡片 ID、将批注 `card_id` 置空，并通过持久 `global_card_resource_gc` 队列删除物理文件；删除失败保留队列供下次打开重试。实现期间契约捕获并修复了恢复 SQL 错写 `created_at`、实际应为 `linked_at` 的事务回滚缺陷。新鲜门禁为全局库 10/10、历史 10/10、P1 生命周期 17/17、旅程 15/15、资源 6/6、资源一致性 14/14、备份 43/43、项目包文件 19/19、定向 UI 5/5、完整 Vitest 803/803（另 4 项按既有 ABI 条件跳过）、build、生产依赖审计 0 与完整 `verify:beta` 通过。未生成打包 EXE，因此仍不构成安装包 UI 验收。
- **M1-G 已完成实现并复验：**普通与加密项目包在数据库写入前执行稳定 ID 预检；同 ID 的卡片字段、内容和全局附件完全相同才复用，异内容逐卡选择保留本机、导入副本或取消。导入副本使用预先生成的新 ID，并在同一事务中重映射场景计划、关系、快照、批注和资源；保留本机不会覆盖本地内容或复制项目包中的全局附件；取消/缺少选择均零项目写入。顺带修正 operation 项目包/资源扫描误用 app data 根、应使用 `CreationWorkspace` 的路径偏差，并让加密导入走相同选择与结果展示。新鲜门禁为项目包 16/16、项目包文件 21/21、operation 25/25、全局库 10/10、历史 10/10、生命周期 17/17、资源/一致性 6/6 与 14/14、备份 43/43、旅程 15/15、完整 Vitest 804/804（另 4 项按既有 ABI 条件跳过）、build、生产依赖审计 0 与完整 `verify:beta` 通过。
- **阶段 1 打包验收已完成：**`release-beta/win-unpacked/创作阅读助手.exe` 以隔离的打包目录和用户配置启动；`verify:packaged-stage1` 17/17 覆盖真实 v9 双项目首次升级、迁移前备份、稳定 ID/项目关联、UI 创建项目、跨项目同步、解绑、删除恢复、普通与加密项目包冲突副本以及重载后项目 ID 唯一性。完整 `verify:beta:release` 同时通过 804/804 测试、build、生产依赖审计 0 和 188,818,944 字节 EXE 检查；发布目录经正规重打包确认不含验收 `data/`。

### 阶段 1：全局卡片库基础（最高优先级）

1. **M1-A 已完成：**schema v9→v10、可恢复备份、项目—卡片关联回填和迁移契约。
2. **M1-B 已完成：**改造 CreationWorkspace、IPC/preload、公共类型、service 和 Zustand card slice；提供全局 list/create、使用项目数及幂等 link/unlink。
3. **M1-C 已完成：**增加应用级“卡片库”导航与全局列表/详情；支持类型筛选、标题/别名/字段搜索、使用项目数、全局新建与编辑。隔离 Electron 冒烟已覆盖入口、字段搜索与项目使用账册。
4. **M1-D 已完成：**项目 CardsPage 现在按 `linkedProjectIds` 投影“已关联卡片”（不再错误按遗留来源 `projectId` 过滤）；可从全局卡片库搜索/关联，且解除关联需明确确认“不会删除全局卡片”，仍复用 workspace 对场景计划、批注、资源的受引用保护。
5. **M1-E 已完成：**全局附件/封面具有独立卡片所有权；项目投影不可删除共享资产；资源扫描、备份和项目包快照均已覆盖全局文件。
6. **M1-F 已完成：**全局卡片 30 天软删除、影响预览、恢复、到期引用清理和可重试物理资源 GC。
7. **M1-G 已完成：**`.cra`/加密项目包稳定 ID 同内容直接关联，异内容显式选择保留本机/导入副本/取消，并展示 ID 映射结果。
8. **已完成：**隔离 Windows 打包目录的真实 Electron 验收为 17/17，覆盖旧 v9 升级与备份、跨项目同步、关联/解除关联、删除恢复、普通/加密项目包冲突副本和重载持久性；失败/取消原子性继续由 operation 与文件层契约覆盖。

### 阶段 2：写作速查

1. **已完成：**在写作台加入独立索引抽屉，支持工具栏、`Ctrl+Shift+K` 和 280–480px 拖拽宽度。
2. **已完成：**按“本场景 / 本项目 / 全局搜索”显示卡片；可把全局卡片关联项目，并通过场景任务卡的 `castCardIds` 加入本场景出场。
3. **已完成：**卡片字段失焦后 800ms 自动保存；等待、保存中、已保存和失败状态可见，失败草稿保留；关联、加入场景和关闭面板后恢复正文焦点。
4. **已完成：**面板显式打开的卡片始终进入独立“写作速查卡片”AI 上下文组，并可在发送确认中整组排除；场景雷达 AI 弹窗已从错误的批注页签分支移出。
5. **打包验收已完成：**`verify:packaged-stage2` 在隔离的真实 Windows 打包副本中 11/11 通过，覆盖快捷键、拖拽宽度、800ms 真 IPC/SQLite 保存、全局关联/加入场景、关闭后继续输入和 AI 上下文排除；证据为 `output/playwright/packaged-stage2-log.json`、`packaged-stage2-quick-reference.png` 与 `packaged-stage2-ai-context.png`。

### 阶段 3：大纲与沉浸写作

2026-09-12 完成：SQLite 已升级至 v11；场景摘要/场景状态、卷章全书实时字数、Markdown 大纲导出、写作目标进度和壳级专注模式（ESC 退出）均已实现。开发门禁为 `npm run verify:desktop-stage3` 78/78、完整 desktop Beta 819 项通过（4 项历史 native ABI 条件跳过）、生产依赖 audit 0。真实打包验收已完成：在全新输出目录 `release-beta-stage3-final-v8/win-unpacked`（EXE 188,818,944 字节）上运行 `npm run verify:packaged-stage3`，18/18 通过；证据为 `output/playwright/packaged-stage3-log.json`、`packaged-stage3-outline.png`、`packaged-stage3-focus.png`。

打包验收期间修掉两个此前被自动化掩盖的真实缺陷：

1. **专注模式 grid 高度坍塌**（v4 截图发现）：`.desktop-root.desktop-root--focus` 的 grid 行与 `.desktop-workbench` 高度/行位置已显式约束。
2. **大纲页双栏被容器裁切、大纲树被推出可视区**（v8 截图发现）：`.outline-page-body` 未约束 grid 行高，且 `styles.css` 遗留的 `align-self: start` 让场景任务卡保持 851px 内容高度、溢出 `overflow: hidden` 的双栏容器。保存场景卡后聚焦侧栏字段时，浏览器会滚动整个容器 267px，导致大纲树、全书汇总、场景摘要与场景状态全部滚出可视区——而原有断言只检查 DOM 文本，无法发现该问题。修复为 `grid-template-rows: minmax(0, 1fr)` 加侧栏 `align-self: stretch`，并新增 3 项几何断言（关键元素必须落在双栏容器可视范围内、容器不得被整体滚动、侧栏必须内滚），验收项由 15 项增至 18 项。

两项环境阻塞已解决，均不属产品代码缺陷：

- **Electron 下载 `ETIMEDOUT 20.205.243.166:443`**：真实根因是 `@electron/get` 在缓存命中后仍会校验 `SHASUMS256.txt`，而该文件不在缓存内，于是回连 GitHub。改用 `ELECTRON_MIRROR=https://cdn.npmmirror.com/binaries/electron/` 与 `ELECTRON_CUSTOM_DIR={{ version }}`（并设 `ELECTRON_BUILDER_BINARIES_MIRROR`）后，首次下载 4–7 MB/s，后续打包直接命中缓存（`using cached artifact`）。
- **打包应用启动即 `FATAL: GPU process isn't usable. Goodbye.`**：本机装有虚拟显示适配器（GameViewer Virtual Display Adapter），Chromium 独立 GPU 进程在其下以 `exit_code=1` 反复退出；`--disable-gpu` 无效（GPU 进程本身仍会启动），非沙箱环境同样复现，因此既不是沙箱也不是产品代码问题。验收脚本改传 `--in-process-gpu` 后即可在该环境完成真实打包验收，断言口径不变。

1. **已完成：**为场景增加可编辑摘要与状态，并迁移/展示卷、章、全书字数；章节状态保留为兼容数据，不得与场景状态混淆。
2. **已完成：**添加 Markdown 大纲导出、写作区底部目标进度、壳级专注模式与 ESC 退出。
3. **已完成：**保留已经存在的打字机实现，补充边界滚动、IME 和连续章节编辑回归测试。
4. **打包验收已完成：**`verify:packaged-stage3` 在隔离的真实 Windows 打包副本中 18/18 通过，覆盖大纲树与卷/章/全书字数汇总在可视区显示、场景摘要与场景状态独立保存、Markdown 大纲导出不含正文、写作区目标进度、壳级专注隐藏应用侧栏/项目头/项目导航/写作轨道、编辑器真实可用高度与正文可见、ESC 退出专注并保留打字机开关。其中 3 项为本次新增的几何断言，用于防止「元素存在于 DOM 但被滚出可视区」再次漏检。

### 阶段 4：深度功能

1. 全书只读预览与打印。
2. 30 天字数趋势和场景状态分布。
3. AI 续写、精简和角色一致性检查，全部走候选/报告，不直接改正文。
4. 校对的别名一致性与持久化忽略。
5. 关系图可视化（近期，不阻塞前三阶段）。

## 6. 已澄清的规格冲突

下列四项已在 2026-09-10 锁定并于 M1-A–M1-G 落地，不再是阶段 1 阻塞项：

1. 内置类型沿用当前八项稳定 `kind`，不因需求文档列举不全而删除或重分类。
2. 全局卡片采用 30 天软删除；项目内移除关联不删除本体，回收站恢复原关联，到期才清理引用和资源。
3. 封面/附件归全局卡片所有，项目仅投影；项目包携带相关卡片的资源快照。
4. `.cra` 以稳定 ID 去重；卡片字段、内容和全局附件完全相同才复用，异内容由用户选择保留本机、导入副本或取消，禁止按标题或 ID 静默覆盖。

## 7. 验证门禁

在每个切片完成后至少执行对应契约；阶段 1、阶段 4 与最终集成还需运行：

```powershell
npm test
npm run build
npm run verify:creation-schema
npm run verify:creation-cards
npm run verify:creation-workspace
npm run verify:creation-search
npm run verify:creation-resource
npm run verify:creation-bundle
npm run verify:creation-p1-lifecycle
npm run verify:writing-quick-reference
npm run verify:packaged-stage2
npm run verify:stage4-preview-print
npm run verify:stage4-proof-ignore
npm run verify:stage4-scene-ai
npm run verify:stage4-scene-ai-acceptance
npm run verify:stage4-relation-graph
npm run verify:stage4-relation-graph-acceptance
npm run verify:packaged-stage3
npm run verify:packaged-stage4
npm run dist:beta:stage4
```

新增的 v10 迁移、全局卡片、项目关联、速查、全书预览、场景状态、AI、校对、关系图与打包态 smoke 应加入 `verify:beta` 与打包门禁。构建和契约通过不是最终替代品：还必须在真实 Windows 打包应用中覆盖“旧 v9 库升级、跨项目实时同步、项目内解除关联、全局删除确认、项目包导入冲突、速查面板连续写作、Stage 3 大纲与壳级专注、Stage 4 全部特性入口与 IPC 可达”流程。

阶段 2 收口证据：`verify:writing-quick-reference` 25/25（含失败草稿保留与重试）、编辑器门禁 118/118、完整 Beta 810/810、build 与生产依赖审计 0 均通过；另 4 项 legacy 测试因既有 Node/Electron `better-sqlite3` ABI 条件跳过。最终打包验收使用 `release-beta-stage2-final/win-unpacked`，原因是旧 `release-beta/win-unpacked/resources/app.asar` 被外部进程持续锁定，直接覆盖两次返回 `EBUSY`；独立输出目录的打包和 11 项验收均成功。

阶段 3 收口证据：`release-beta-stage3-final-v8/win-unpacked/创作阅读助手.exe`（188,818,944 字节）；`verify:packaged-stage3` 18/18（含大纲可视几何、场景摘要/状态独立保存、Markdown 大纲导出不含正文、壳级专注、ESC 退出保留打字机）；`verify:beta -- --scope=desktop` All checks passed；`npm audit --omit=dev` 0 漏洞；`npx vitest run` 819 passed / 4 skipped。

阶段 4 收口证据（2026-09-12）：

| 切片 | 关键交付 | 真机验收 |
|------|----------|---------|
| 4-C 统计 | `daily`（30 天趋势）+ `sceneStatusCounts`（场景状态，源 `scenes.scene_status`），无独立字数真源 | 16/16 契约 + 验证 IPC 字段 |
| 4-A 全局卡片封面 | 封面 = 挂在卡片上的资源（`role === "cover"`），经 `creation:resourceList` 读取，URL 由 `buildCardResourceUrl()` 生成 `creation-asset://card/<cardId>/<resourceId>`；主进程只读协议 + CSP 已放行 `creation-asset:`；`CardCoverImage` 覆盖详情区空/加载/失败三态；**列表缩略图**由 `CardSummary.coverResourceId`（SQL 子查询一次带出，避免 N+1）+ `CardCoverThumb` 实现 | 打包态 smoke 验证 `resourceList` 通道可达（13/13，含真实建卡）；列表缩略图真机 8/8（`naturalWidth: 1700` 证明字节真的加载成功）；契约 17/17 |
| 4-B 全书预览与打印 | `creation:readProjectPreview` + `creation:printProject`（复用 renderer 通读页，不引入第二套排版真源） | Stage 3 打包态 18/18 |
| 4-E 校对 | 位置级忽略（key=`rule#paragraphIndex#fnv1a32(normText)`）+ 别名一致性 + 疑似错拼；`scannedScenes`/`total`/`ignoredCount` 完整字段 | 真机 acceptance PASS |
| 4-D AI 场景动作 | `continuation` / `condensing` / `character-consistency` 三个新动作，全部走候选/报告；正文修改走 `appendTextToSceneBody`（document 层追加，避免 plain-text 段落丢失） | 25/25 真机 + prompt 包含/排除正确 |
| 4-F 关系图 | 整图查询（避免 N+1），按度数排序截断 + `hiddenRelationCount`；项目视图=引用投影；确定性按卡类型分簇环形布局；节点摘要 + 对端跳转 | 20/20 真机 + 16/16 契约 |

收口门禁：`release-beta-stage4-final/win-unpacked/创作阅读助手.exe`（188,818,944 字节）；`verify:packaged-stage3` 在新打包二进制上 18/18 非回归；`verify:packaged-stage4` 12/12 覆盖 A/B/C/D/E/F 全部 IPC；`npx vitest run` 963 passed / 4 skipped；`npm run verify:beta -- --scope=desktop` All checks passed；`npm audit --omit=dev` 0 漏洞；截图 `output/playwright/stage4-{scene-ai,relation-graph,packaged}/`。

打印链路专项（2026-09-12 补齐）：`verify:packaged-stage4b-print` 12/12，覆盖 asar 资源来源、`dialog.showSaveDialog` 打桩后 `printToPDF` 真实写出 402,151 字节 PDF（magic `%PDF-`）、以及主进程 spy 捕获 `webContents.print({ silent: false, printBackground: false })`。截图 `output/playwright/stage4-print-packaged/`。

更正记录（2026-09-12）：Stage 4-A 曾被记为「`coverImagePath` 字段持久化」，该字段在代码库中并不存在，且打包态断言因卡片列表为空而提前返回、产生假阳性。真实机制已在上表中更正；打包态断言同时改为先建卡再验证 `resourceList` 通道（现为 13/13，含真实建卡步骤）。

剩余/延后：无功能缺口。全部改动按约束暂留本地未提交，等用户授权。

Stage 4-A 列表封面缩略图（2026-09-12 补齐）：需求原文「全局卡片画廊必须真正显示一套封面图片」此前只做到详情区，列表/网格无缩略图。现 `CardSummary.coverResourceId` 由 `listCards()` 的 SQL 子查询一次带出（避免 N+1），依赖已有唯一索引 `idx_global_card_resources_cover`；旧库无该表时恒 null 并降级占位。新组件 `CardCoverThumb`（40px 紧凑方形，失败退化为占位图标）接入 `CardListSidebar`。验证：组件测试 9 个、契约 17/17（新增「cards.list 一次性带出封面资源 ID」场景）、真机 `scripts/card-cover-acceptance.mjs` 8/8（`naturalWidth: 1700` 证明字节经只读协议真正加载成功，破图为 0）、`vitest` 972/4 skipped、`build` EXIT=0。

## 8. 粗略工期（按当前已核验基线重估）

以下是净人日，不含等待产品决策、外部 AI 服务不稳定或发布审批时间。当前能运行的项目、编辑器、设定卡、统计、历史和校对不再重复按“从零开发”估算；全局化数据迁移仍是关键路径。

| 切片 | 估算 | 说明 |
|---|---:|---|
| P0：UI 验收脚本同步 | 1–2 人日 | 选择器/名称对齐、稳定 test id、隔离冒烟重跑 |
| 阶段 0：决策与迁移夹具 | 3–5 人日 | 备份、冲突策略、含关系/附件/批注的 fixture |
| 阶段 1：全局卡片库与 v9→v10 | 18–25 人日 | schema、可恢复迁移、IPC/store/UI、关联/删除、资源和项目包回归 |
| 阶段 2：写作速查 | 7–10 人日 | 右面板、快捷键、自动保存、场景关联、AI 上下文 |
| 阶段 3：大纲与沉浸写作 | 7–10 人日 | 场景状态/摘要、汇总、Markdown、壳级专注和回归 |
| 阶段 4：预览、趋势、AI、校对、关系图 | 13–18 人日 | 关系图可独立延后，不阻塞前四项的主要价值 |
| 集成与发布验收 | 6–9 人日 | 迁移演练、打包应用、回归、性能与失败恢复 |
| **合计** | **55–79 人日** | 单人约 3–5 个月；在数据库迁移和共享 IPC 串行集成的约束下，2–3 人并行约 2–3.5 个月 |

若第一阶段不包含全局附件/封面，或关系图改为后续独立版本，总量可降约 7–11 人日；反之若需要兼容多个已发布的 `.cra` 格式、补完整回收站或做复杂图布局，估算上限应上调。
