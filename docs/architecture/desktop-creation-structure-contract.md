# 桌面端创作工作台 — 卷章结构与大纲契约（切片 6 现状）

> 状态：切片 6（卷章与大纲）实现完成，本契约按 2026-08-10 实际实现记录。
> 本文取代 `desktop-creation-workspace-contract.md` 中关于 schema 版本与能力面的描述（该文件仍保留为切片 2 历史快照）。
> 以 `electron/main/creation-workspace/` 当前代码为准。新增查询/命令/能力时，必须同步更新本文件与运行时契约测试 `outline-contract.ts`。

## 1. 结论

切片 6 把工作台从「单一项目 + 单章 + 单场景」扩展为完整的三层卷章结构：

- schema v3：新增 `volumes` 表，`chapters` 增加 `volume_id / status / numbering_kind / custom_number / deleted_at`，`scenes` 增加 `planning_json / deleted_at`；v2→v3 原子迁移为每项目建默认卷「正文」并挂接旧章节。
- 命令面：19 个结构命令（卷/章/场景的 create / rename / reorder / move / delete，加 chapter.setStatus / setNumbering / split / merge、chapters.setStatus 批量）。
- 查询面：新增 `project.outline`，返回卷→章→场景树，含派生显示编号、章节工作流状态与每场景非空白字数。
- UI：写作台左栏升级为「大纲树 / 卡片板」双视图，共享同一 outline 数据源；结构命令经 `creation:runStructure` 通道执行，watch 事件驱动大纲免刷新实时更新。
- 门禁：`verify:creation-outline`（16 条运行时契约）纳入 `verify:beta`。

## 2. 实现文件与门禁

| 文件 | 作用 |
|---|---|
| `electron/main/creation-workspace/index.ts` | schema v3、v1→v3 迁移、project.outline、19 个结构命令 |
| `electron/main/creation-workspace/types.ts` | 结构命令/大纲查询类型（re-export 自 `src/types/creation.ts`） |
| `electron/main/creation-workspace/outline-contract.ts` | 切片 6 运行时契约测试（16 场景，含 v2→v3 迁移） |
| `electron/main/creation-workspace/contract.ts` | 切片 2 基础契约（已随 v3 更新 counts 断言） |
| `electron/main/creation-workspace/project-shell-contract.ts` | 项目壳契约（v3 schemaVersion / 默认工作流断言已同步） |
| `scripts/verify-creation-outline.mjs` | 门禁：tsc → esbuild → Electron Node 运行 outline-contract.ts |
| `electron/main/creation-ipc.ts` | `creation:readProjectOutline` / `creation:runStructure` 通道 |
| `src/features/creation/outline/OutlineTree.tsx` | 大纲树（卷→章→场景 + 全部结构操作） |
| `src/features/creation/outline/CardBoard.tsx` | 卡片板（按章节/状态分组） |
| `scripts/verify-creation-project-shell.mjs` | 通道白名单已含新 IPC 通道 |

## 3. schema v3

### 3.1 volumes 表

```sql
CREATE TABLE volumes (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1,
  deleted_at TEXT
);
```

### 3.2 chapters / scenes 新列

| 列 | 含义 |
|---|---|
| `chapters.volume_id` | 所属卷（可空，兼容无卷数据；正常迁移后非空），`ON DELETE SET NULL` |
| `chapters.status` | 工作流状态名；空字符串表示未设置，读取时派生为项目工作流第一步 |
| `chapters.numbering_kind` | `auto` / `prologue` / `extra` / `custom` |
| `chapters.custom_number` | custom 编号的文本（仅 custom 使用） |
| `chapters.deleted_at` / `scenes.deleted_at` | 软删除时间戳；查询一律排除 |
| `scenes.planning_json` | 场景规划字段 JSON（切片 6 仅建列，无命令写入） |

### 3.3 迁移链

- `initializeSchema` 直接建 v3 表（user_version 0 → 3）。
- `migrateSchemaV1ToV2` + `migrateSchemaV2ToV3` 链式：v1 库打开时两步到 v3；v2 库一步到 v3。
- v2→v3：建 volumes 表 → 为每个有章节的项目插入默认卷「正文」（id 前缀 `volume-default-`）→ 所有章节 `volume_id` 指向默认卷 → 加其余列与索引 → `user_version = 3`。迁移在一个 `BEGIN IMMEDIATE ... COMMIT` 内，失败整体回滚（`project-shell-contract` 验证失败回滚不 bump 版本）。

## 4. read：project.outline

新增查询（`kind: "project.outline"`）：

- 参数：`projectId`。
- 返回 `CreationProjectOutline | null`（项目不存在返回 null）：
  - `volumes`：非软删除卷按 `sort_order, id` 排序，各卷内 `chapters` 按序，各章内 `scenes` 按序。
  - `looseChapters`：无卷章节（迁移后通常为空），按项目内 auto 序编号。
  - 场景 `wordCount`：正文去标点/空白后的字符数（`[\p{P}\p{S}\p{Z}\s]` 移除后计数），body_json 损坏计 0。
- 编号派生：`auto` → 卷内 auto 章按序生成「第N章」；`prologue` → 「序章」；`extra` → 「番外」；`custom` → `customNumber`。
- 章节 `status` 为空时派生为 `project.setup.chapterWorkflow[0]`。

## 5. transact：19 个结构命令

统一返回 `CreationStructureResult { commandType, sequence, projectId, entityId, revision, updatedAt }`。所有命令在 `runStructureTransaction` 信封内执行（`BEGIN IMMEDIATE` → 校验/写入 → change_log → `COMMIT` → 同步投递 committed 事件 → 返回），任一失败 `ROLLBACK` 且不产生事件、不递增 sequence。

| 命令 | 语义要点 |
|---|---|
| `volume.create` | 需 `projectId` + `title`；可选 `beforeVolumeId`（省略/`null` = 卷尾） |
| `volume.rename` | `baseRevision` 校验，冲突抛 `revision-mismatch` |
| `volume.reorder` | 组内重排，主实体 revision+1 |
| `volume.delete` | 软删除；级联软删除卷下章节与场景 |
| `chapter.create` | `volumeId` 省略时自动建默认卷「正文」；`beforeChapterId` 省略 = 卷尾 |
| `chapter.rename` / `chapter.reorder` | revision 校验 / 卷内重排 |
| `chapter.move` | 跨卷移动（目标卷必须同项目）；旧卷收敛序号、新卷插入 `beforeChapterId` |
| `chapter.delete` | 软删除；级联软删除其场景 |
| `chapter.setStatus` | `status` 必须在项目 `chapterWorkflow` 内 |
| `chapter.setNumbering` | `numbering` 白名单；custom 时必填 `customNumber`（≤50 字符） |
| `chapter.split` | 按场景边界拆章：`splitSceneId`（含）及之后场景移入紧随其后的新章；原章必须至少保留一个场景 |
| `chapter.merge` | 同卷合并：源章场景追加到目标章末尾，源章软删除 |
| `chapters.setStatus` | 批量状态；全部章节必须同一项目且状态在项目工作流内 |
| `scene.create` | 需 `chapterId` + `title`；空正文 `{"type":"doc","content":[]}` |
| `scene.rename` / `scene.reorder` | revision 校验 / 章内重排 |
| `scene.move` | 跨章移动（目标章必须同项目）；旧章收敛、新章插入 |
| `scene.delete` | 软删除 |

通用约束：

- 所有 id 由模块生成（`volume-` / `chapter-` / `scene-` + UUID），稳定不可变；排序/改名/跨卷移动不改变 id。
- 输入运行时校验：id 非空字符串、标题 trim 后 1–200 字符（状态/编号文本 ≤50）、`baseRevision` 正整数；`beforeId` 必须属于同组（同项目/同卷/同章）。
- 软删除实体不可被结构命令引用（`requireVolume/Chapter/Scene` 均过滤 `deleted_at IS NULL`）。

## 6. watch 与事件

切片 6 沿用 `projectId` 级 scope。每个结构命令在 COMMIT 后同步投递一条 committed 事件，`changes` 数组列出本事务影响的所有实体（删除卷时含被级联的章节/场景）。renderer 的 `subscribeProject` 收到事件后刷新 navigation 与 outline，实现「结构命令后免刷新实时更新」。

## 7. check 完整性

`CreationIntegrityReport.counts` 新增 `volumes`。索引检查新增 `idx_volumes_project_order` 与 `idx_chapters_volume_order`。其余 section 语义不变；counts 统计表行数（含软删除）。

## 8. 门禁与验证

- `npm run verify:creation-outline`：16 条运行时契约，覆盖默认卷创建、第二卷/新章/新场景、字数统计、改名、卷重排、章节跨卷移动（含编号重派）、场景跨章移动、状态更新（有效/无效）、编号覆盖、场景/章节/卷软删除级联、revision-mismatch、跨项目拒绝、拆章/并章/批量状态、v2→v3 迁移。
- 全量 `npm run build`、`npm test`（147 用例）以及 workspace / project-shell / editor / migration-audit 门禁全部通过。
- 端到端（真实 Electron + CDP）：创建项目 → 写入正文（字数 16）→ 卷/章/场景树渲染 → 卡片板分组 → 创建新卷后约 1.5 秒内大纲树自动出现（免刷新）。

## 9. 下一切片边界

按规格 §9 切片顺序，切片 6 之后是**切片 7「卡片深 module」**（类型、字段 schema、别名、正反关系、附件、引用与批注）。当前 `cards` / `card_relations` 表虽在 schema v3 中保留，但无命令写入、无 UI；`scenes.planning_json` 已建列但无命令/查询消费。切片 6 的 `project.outline` 的场景 `wordCount` 为查询时计算，若规模性能不达标，可在切片 7 改为 `scenes.word_count` 列并在 `scene.updateBody` 时更新。
