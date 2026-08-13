# desktop — 创作工作台当前架构

状态：当前执行依据（更新于 2026-08-11）。历史切片契约只用于追溯，不代表完整能力面。

## 1. 产品与源码边界

- Renderer：`src/`（React、Zustand、页面组件与 service adapter）。
- Electron：`electron/`（主进程、preload、SQLite workspace 与迁移协调器）。
- 桌面端不依赖 `android/` 或 `archives/frozen-mobile/` 的运行时代码。
- 本地数据由主进程持有；renderer 只能经 `DesktopApi.creation` 的类型化 IPC 访问。

## 2. 核心调用链

```text
页面组件
  -> useCreationActions
  -> src/services/creation-service.ts
  -> electron/preload/index.ts
  -> electron/main/creation-ipc.ts
  -> CreationCoordinator
  -> CreationWorkspace
  -> workspace.sqlite
```

`CreationWorkspace` 是存储深模块。公开概念只有 `read`、`transact`、`watch`、`check`、`close`；SQL、事务、revision、change log、完整性检查和句柄生命周期均隐藏在 `electron/main/creation-workspace/`。

## 3. 当前存储与命令面

- 当前 schema：v8；打开时启用 WAL、外键和 FULL synchronous，并按版本顺序原子迁移。
- `read` 覆盖项目树/导航/大纲、场景正文、卡片与关系、回收站/快照、搜索/替换、统计/会话/校对、收件箱、项目包、批注、资源和项目首页。
- `transact` 覆盖项目创建、正文保存、卷章场景结构、卡片与关系、历史恢复、替换、写作会话、收件箱、项目/草稿导入、批注、资源和场景规划。
- `runStructure` 的运行时命令目录只有一个真源：`src/types/creation.ts` 的 `CREATION_RUN_COMMAND_TYPES`。它以 `Record<CreationRunCommand["type"], true>` 保证联合类型新增成员时白名单必须同步。
- 所有写命令遵守：校验 → `BEGIN IMMEDIATE` → 写入与 change log → `COMMIT` → committed 事件。失败整体回滚，不发送成功事件。

## 4. 当前关键一致性规则

### 项目首页字数

`project.home.currentChars` 与 `stats.view.words.nonWhitespace` 使用同一口径：按 Unicode 字符遍历，只排除空白；汉字、拉丁字符、标点、符号和 emoji 均计数。项目首页用 SQLite 分组聚合，只向调用方返回计数，不通过 `group_concat` 把全部 `body_json` 聚合回 JS。

### 卡片项目隔离

Zustand 卡片状态带 `cardProjectId` 所有权。切换项目会立即清空旧卡片、关系和选择；类型、卡片、关系与 loading 的异步写入都必须匹配当前项目。`CardsPage` 仍在消费端按 `projectId` 过滤，形成存储层与 UI 层双重防线。

### 收件箱转卡

`inbox.convertToCard` 在一个事务中完成条目 revision 校验、资料卡创建、条目标记 used 和 change log。Renderer 不再用“先建卡、再改收件箱”的部分成功流程；同一条目进行中禁止重复提交。

## 5. 验证入口

在仓库根执行：

```powershell
npm test -- --run
npm run build
npm run verify:creation-project-home
npm run verify:creation-inbox-convert
npm run verify:creation-planning
npm run verify:creation-cards
npm run verify:creation-journey
npm run verify:beta -- --scope=desktop
git diff --check
```

新增 workspace 查询/命令时，必须同步公开类型、IPC/preload/service adapter、对应运行时契约和 desktop Beta scope；新增项目级异步状态时，必须显式记录所属项目并覆盖迟到响应测试。
