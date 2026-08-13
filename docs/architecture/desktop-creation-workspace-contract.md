# 桌面端创作工作台 — CreationWorkspace 模块契约（切片 2 现状）

> ⚠️ 历史快照：本文件只记录切片 2，不反映当前 schema 与能力面。当前架构见 [`desktop-creation-current.md`](desktop-creation-current.md)。
> 状态：切片 2（CreationWorkspace module）实现完成，本契约按 2026-08-09 实际实现记录。
> 本文是契约记录而非设计文档：以 `electron/main/creation-workspace/` 当前代码为准。
> 新增 query/command/能力时，必须同步更新本文件与运行时契约测试 `contract.ts`。

## 1. 结论

切片 2 的深 module 已落地：SQLite schema v1、`read` / `transact` / `watch` / `check` / `close` 最小公开接口，以及独立契约门禁 `verify:creation-workspace`（已纳入 `verify:beta`）。

当前能力面刻意很小：**一个查询（`project.tree`）、两个命令（`project.create`、`scene.updateBody`）**。IPC、UI、自动备份、旧数据迁移、项目包与编辑器均未接入本模块。

## 2. 实现文件与门禁

| 文件 | 作用 |
|---|---|
| `electron/main/creation-workspace/index.ts` | `openCreationWorkspace` 工厂 + `SqliteCreationWorkspace` 实现 |
| `electron/main/creation-workspace/types.ts` | 全部公开类型与 `CreationWorkspaceError` |
| `electron/main/creation-workspace/contract.ts` | 运行时契约测试（esbuild 打包后由 Electron 33 的 Node 执行） |
| `electron/main/creation-workspace/better-sqlite3.d.ts` | 本地最小类型声明（仅覆盖本模块用到的 API） |
| `scripts/verify-creation-workspace.mjs` | 门禁：`tsc --noEmit -p tsconfig.main.json` → esbuild bundle → `ELECTRON_RUN_AS_NODE=1` 下运行 contract.ts |
| `package.json` | `scripts.verify:creation-workspace`；`scripts/beta-check.mjs` 的 `verify:beta` 已包含该项 |

## 3. 公开 interface（与 types.ts 一致）

```ts
export interface CreationWorkspace {
  read(query: CreationReadQuery): Promise<CreationReadResult>;
  transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): () => void;
  check(): Promise<CreationIntegrityReport>;
  close(): Promise<void>;
}

export interface OpenCreationWorkspaceOptions {
  directory: string;
}

export type CreationWorkspaceErrorCode =
  | "closed" | "invalid-input" | "not-found"
  | "conflict" | "revision-mismatch" | "integrity";
```

公开类型族：`CreationReadQuery`（当前仅 `ReadProjectTreeQuery`）、`CreationCommand`（`CreateProjectCommand | UpdateSceneBodyCommand`）、`CreationProjectTree`、`CreationWatchScope`（仅可选 `projectId`）、`CreationWorkspaceEvent`、`CreationIntegrityReport`。所有 ID 均为字符串（由模块生成，格式 `project-` / `chapter-` / `scene-` + UUID）。

## 4. 打开语义与 schema v1

- `openCreationWorkspace({ directory })`：目录不存在则递归创建；打开 `directory/workspace.sqlite`；固定设置 `journal_mode=WAL`、`foreign_keys=ON`、`synchronous=FULL`。
- `user_version` 为 0 时执行初始化（`CREATE TABLE IF NOT EXISTS`，置于单个 `BEGIN IMMEDIATE ... COMMIT`），并写入 `user_version=1`；非 0 且不等于 1 时抛 `integrity`（不支持的 schema 版本）。
- `options` 为 null/undefined、`directory` 非字符串或空白 → `invalid-input`（在打开前校验）。
- `mkdir`、`new Database(...)` 构造、PRAGMA 或 schema 初始化任一失败：先关闭已打开的数据库（关闭失败也被吞掉，以保留稳定错误），统一映射 `integrity`（“无法打开创作工作区。”），错误信息不泄漏目录路径或底层错误细节；`CreationWorkspaceError`（如不支持的 schema 版本）原样抛出。

schema v1 表：

| 表 | 现状 |
|---|---|
| `workspace_meta` | 已建表但无任何写入 |
| `projects` / `chapters` / `scenes` | 有命令写入；`scenes.body_json` 存 JSON 文档 |
| `cards` / `card_relations` / `resources` / `snapshots` | 已建表与索引，但**没有任何公开命令写入** |
| `change_log` | 每次提交写入一条（`sequence` AUTOINCREMENT、`command_type`、`changes_json`、`committed_at`） |
| `scenes_fts` | FTS5 external-content（`content='scenes'`），由 `scenes_ai/ad/au` 触发器维护；**尚无公开搜索查询使用它** |

注意：当前 schema **没有 volumes 表**，章节直接归属项目；规格 4.1 的 Volume、CardType、RelationType、TextAnchor、Comment、WritingSession、PublicationRecord、InboxItem 均未实现（无表、无命令）。

## 5. read：当前支持的查询

| 查询 | 参数 | 返回 | 边界 |
|---|---|---|---|
| `project.tree` | `projectId` | `CreationProjectTree \| null` | query 为 null、`kind` 非 `project.tree`、`projectId` 非字符串或 trim 后为空 → `invalid-input`；项目不存在 → 返回 `null`（不是错误） |

- 返回项目 + 按 `sort_order, id` 排序的章节 + 各章节下按 `sort_order, id` 排序的场景；场景 `body` 为 `body_json` 的 `JSON.parse` 结果。
- 数据库读取或 `body_json` 解析失败（如损坏 JSON）→ `integrity`（固定文案“无法读取创作工作区数据。”），不泄漏底层 SQL 或 `SyntaxError` 细节。
- 尚无：项目列表、单场景/单章节查询、卡片、搜索、统计、快照、回收站、收件箱查询。

## 6. transact：当前支持的命令

| 命令 | 输入校验 | 事务效果 | 返回 | 错误映射 |
|---|---|---|---|---|
| `project.create` | `title` 必须为 `string`（typeof 运行时校验，否则 `invalid-input`）；trim 后 1–200 字符 | 一个事务内创建项目 + 章节（默认标题“第一章”，`sort_order=0`）+ 场景（默认标题“默认场景”，空 `doc`）；写 `change_log` | `projectId` / `chapterId` / `sceneId` / `sequence` | `SQLITE_CONSTRAINT` → `conflict`；其他 → `integrity` |
| `scene.updateBody` | `sceneId` 必须为 `string` 且 trim 后非空；`baseRevision` 必须为整数且 ≥1；`body` 必须为非 null 对象且 `type === "doc"`、`content` 为数组；任一不符 → `invalid-input`；`JSON.stringify(body)` 失败（如循环引用）→ `invalid-input`（无法序列化） | 事务内联查场景与所属项目；场景不存在 → `not-found`；`revision !== baseRevision` → `revision-mismatch`；成功则 `revision+1`、更新 `body_json`、写 `change_log` | `sceneId` / `revision` / `projectId` / `sequence` | 见左；其他 → `integrity` |

共同时序：`BEGIN IMMEDIATE` → 写入 → `COMMIT` → 同步投递一条 committed 事件 → 返回。任何失败路径先尝试 `ROLLBACK`（若 SQLite 已回滚则忽略），且**不产生事件、不递增 sequence**。

实现注意点（如实记录）：

- `transact` 入口先做 `command.type` 运行时白名单校验（仅允许 `project.create` 与 `scene.updateBody`）；未知或缺失的 type 一律抛 `invalid-input`，不会落入其他命令分支。
- 两个已知命令的运行时字段均显式做类型/结构校验（`typeof`、`Array.isArray`、`in` 检查），类型不符一律映射 `invalid-input`；字段校验在事务开始前完成。
- 正文校验只检查 `doc` 外壳；`content` 内块结构不校验（`CreationDocument.content: unknown[]`）。编辑器级 schema 校验不在本模块。
- 项目与章节的 `revision` 初始为 1，当前没有任何命令会更新它们；只有场景 `revision` 会随 `updateBody` 递增。
- 尚无：卷/章/场景的独立创建与改名、移动、重排、拆并、软删除、卡片与关系、快照命令、回收站、写作会话、导入命令。

## 7. watch：时序与语义（实测行为）

- 事件在 `COMMIT` 之后、`transact` 返回的 Promise resolve 之前**同步投递**（`transact` 体内无 `await`）。
- `sequence` 来自 `change_log` AUTOINCREMENT，全局单调；同一切片内提交无空洞。
- `scope` 仅支持可选 `projectId`：省略 → 收到全部事件；指定 → 只收该项目事件。当前所有命令产生的事件 `projectId` 均为项目 ID（类型允许 `null`，但尚无命令产生）。
- 注册前做运行时校验：`scope` 必须为非 null 对象、`listener` 必须为函数、`projectId` 若存在必须为非空字符串（trim 后），任一不符 → `invalid-input`（“创作工作区订阅请求无效。”）。
- 监听器抛出的异常被捕获并忽略，不影响已提交事务。
- 退订：`watch` 返回的闭包删除对应 watcher；`close()` 清空全部 watcher。
- 不重放历史事件；`check()` 不产生事件（契约测试已验证）。
- 尚无：entity / id 级 scope、收件箱域、事件合并或去重。

## 8. check：范围与明确边界

返回 `ok`、`schemaVersion`、`latestSequence`、`checkedAt`、`counts`（projects/chapters/scenes/cards/relations/resources/snapshots）与五个 section。

| section | 检查内容 |
|---|---|
| `schema` | `user_version === 1`、`journal_mode === wal`、`foreign_keys === on`、必需表齐全、`integrity_check === ok` |
| `relations` | `foreign_key_check` 无行 |
| `resources` | 无失去项目归属的资源（`LEFT JOIN projects`） |
| `indexes` | 必需索引齐全（7 个固定索引名） |
| `snapshots` | 无失去项目归属的快照 |

`ok` = 五个 section 全部通过。`check()` 只读：不写库、不触发 watch、不产生提交。执行过程中的任何非 `CreationWorkspaceError` 异常统一映射 `integrity`（固定文案“无法检查创作工作区完整性。”），不泄漏底层错误细节；`CreationWorkspaceError` 原样抛出。

明确**不覆盖**：FTS 内容一致性、资源 `sha256` / 相对路径 / 引用计数、快照 payload 可加载性、关系类型端点约束（无 RelationType 表）、软删除与回收站（未实现）。

## 9. close 与错误语义

- `close()` 幂等：清空 watcher → `wal_checkpoint(TRUNCATE)` → `database.close()`。
- checkpoint 失败被捕获并记录，不中断流程，仍继续尝试关闭数据库句柄。
- `database.close()` 原生失败 → `integrity`（“无法关闭创作工作区。”）。
- checkpoint 失败但句柄已成功关闭 → 先置 closed，再抛稳定 `integrity`（“创作工作区已关闭，但 WAL 检查点未完成。”）；此后公开方法按 closed 处理。
- 关闭后调用任何公开方法抛 `closed`。
- 全部失败均为 `CreationWorkspaceError`，code 限定为 `closed | invalid-input | not-found | conflict | revision-mismatch | integrity`；message 为固定中文文案，不含 SQL、堆栈或文件路径。
- `read` 的数据库读取失败或 `body_json` 解析失败同样映射 `integrity`，错误信息不泄漏 `SyntaxError` / SQL 细节。
- `watch` 的参数运行时校验（scope / listener / projectId）失败映射 `invalid-input`。
- 唯一“空结果而非错误”的路径：`read({ kind: "project.tree" })` 对不存在的项目返回 `null`。

## 10. 明确未接

- **无 IPC**：`src/` 与 `electron/` 其余代码对 `creation-workspace` 零引用；无 `ipcMain` / `ipcRenderer` 通道。
- **无 UI**：renderer 未消费该模块；无自动保存调度、无编辑器（Tiptap 目前仅为 spike 证据）。
- **无备份 / 迁移 / 项目包**：旧数据（`inspirations.json`、`library.json` 等）仍走原路径；无备份模块接入。
- 门禁接线：`verify:creation-workspace` 已在 `package.json`，并被 `verify:beta`（`scripts/beta-check.mjs`）调用。2026-08-09 实测输出：`11 public-interface contracts verified`。

## 11. 下一切片边界

按第一阶段规格第 9 节的切片顺序：

- **切片 3（只读迁移审计）**：读取旧数据、生成迁移计划与报告、不激活新 store。本模块当前能力足以支撑其“不触碰新 store”的定位；若迁移器需要把条目写入新 store，则须先扩展命令面。
- **切片 4（项目壳与新建闭环）**：需要新增 `read` 的**项目列表查询**（当前仅有 `project.tree` 且必须带 `projectId`）；`project.create` 已覆盖“创建项目并自动生成首章默认场景”的存储部分，UI 向导与首页属 renderer 侧工作。
- **切片 5（场景正文编辑闭环）**：需要编辑器 schema 校验接入（当前模块只校验 `doc` 外壳）、结构命令（建章/建场景、改名、移动、重排）、软删除与回收站命令、`watch` scope 扩展（entity / id 级），以及错误码扩展（如 `foreign-project`、`snapshot-required` 尚未实现）。
- 卷（Volume）实体、卡片/关系命令、快照命令、搜索查询等按规格属于后续切片，当前**不存在**。

任何扩展落地时：同步更新 `types.ts`、`index.ts`、`contract.ts` 与本契约文档，并扩展契约测试断言组。
