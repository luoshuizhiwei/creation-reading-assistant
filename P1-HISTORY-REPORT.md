# P1 历史 / 快照恢复 / 回收站影响 UI — 审查与收尾报告

- 范围：`src/features/creation/history/**`（仅此目录，未触碰任何禁止路径）
- 前置实现：本轮在 `history` 目录既有未提交半成品（前序 Agent）之上收尾，未重写软删除 / 恢复 / 30 天清理核心逻辑
- 依赖的 Agent 1 接口：`previewSnapshot` / `restoreSnapshotWithProtection` / `loadTrashImpact`（已确认存在，tsc 0 errors，无接口阻塞）

## 验证结果

| 项 | 命令 | 结果 |
|----|------|------|
| 单元 | `npx vitest run src/features/creation/history` | **26 passed**（history-models 18 + history-page 8） |
| 类型 | `npx tsc --noEmit -p tsconfig.renderer.json` | **0 errors** |
| 规范 | `git diff --check -- src/features/creation/history` | **exit 0**（无尾随空白 / CRLF 问题） |

> 注：测试存在 React `act()` 警告，来自页面挂载时的异步加载副作用（导航 / 卡片 / 大纲），为前序代码既有现象，不影响断言与通过率；本任务未在“无 act 警告”要求内，故未改动挂载时机。

## 修改文件

1. `src/features/creation/history/RestoreSnapshotDialog.tsx`
2. `src/features/creation/history/HistoryPage.tsx`
3. `src/features/creation/history/history-models.ts`
4. `src/features/creation/history/history-local.css`
5. `src/features/creation/history/__tests__/history-page.test.tsx`

## 恢复前保护流程（requirement 1）

1. 点击「恢复」→ `handleOpenRestore` 先调用 `previewSnapshot({ kind:"snapshot.preview", projectId, snapshotId })`。
2. 对话框展示对象级差异（`rows[].before` 当前值 vs `rows[].after` 快照值）+ `warnings` 明显高亮；不再只列快照名称。
3. `preview.canRestore === false` 时「先保护再恢复」按钮禁用，禁止提交。
4. 用户确认后**只**调用 `restoreSnapshotWithProtection({ type:"snapshot.restoreWithProtection", projectId, snapshotId, protectionReason })`；不再使用 renderer 端“先 `snapshot.create` 再 `snapshot.restore`”的非原子流程（断言 `runStructure` 未被以 `type:"snapshot.restore"` 调用）。
5. 成功：对话框**保留**，顶部展示「恢复前保护」成功提示与**保护快照 ID**（`protectionSnapshotId`）；同时刷新快照列表 + `loadNavigation` + `loadCards` + `loadOutline`（项目视图一致）。
6. 失败：保留差异与对话框，仅 `setError` 不关闭，不丢失已加载预览。

## 支持对象（requirement 2）

- 场景 / 卡片 / 章 / 卷四类均纳入快照主题。
- `SNAPSHOT_SUBJECT_LABEL` 补齐 `volume: "卷"`、`chapter: "章"`，列表与导出标题统一。
- 章 / 卷在导航树中通常缺失，`snapshotSubjectTitle` 回退到 `snapshot.reason`；权威名称由预览接口的 `preview.title` 给出并在对话框展示（已覆盖章 / 卷测试用例）。

## 删除影响展示（requirement 3）

1. 点击「永久删除」→ `handleOpenPurge` 先调用 `loadTrashImpact({ kind:"trash.impact", projectId, entity, entityId })`。
2. 对话框展示：`childVolumeCount`（卷）/ `childChapterCount`（章）/ `childSceneCount`（场景）/ `relatedCardCount`（关联卡片 / 关系）/ `resourceCount`（资源附件）/ `approxChars`（约计字数）；`warnings` 有则高亮。
3. **需输入对象名称确认**：受 `confirmation !== item.title` 约束；不匹配时删除禁用。
4. 查询失败（`loadTrashImpact` 返回 null）或名称不匹配 → 「确认永久删除」禁用，禁止删除。
5. 恢复功能（`handleRestoreTrash`）与 30 天清理行为保持不变（清理为后端保留期逻辑，本页无对应 UI，未改动）。

## UI（requirement 4）

- 延续“现代编辑出版工作室”风格，无大型圆角 Dashboard 卡片堆叠。
- 完整状态：loading（差异 / 影响加载中）、error（查询失败）、empty（无快照 / 无回收项）、conflict（`canRestore=false` 禁用 + 恢复失败保留）。
- 键盘可达性：对话框打开时 `autoFocus` 落在关闭按钮；`Esc` 在恢复进行中以外可关闭；`history-local.css` 新增 `:focus-visible` 焦点环（关闭 / 取消 / 确认 / 危险 / Tab / 列表操作 / 输入控件统一）。

## 测试覆盖（26）

- 差异加载成功（对象级 before/after + warnings）
- 差异加载失败（错误提示 + 提交禁用）
- `canRestore=false`（提交禁用）
- 安全恢复成功（原子调用 + 成功后展示保护快照 ID + 刷新导航 / 卡片 / 大纲）
- 恢复冲突 / 失败时对话框与预览保留
- 章 / 卷快照以权威预览标题展示
- 回收站影响数据展示（卷 / 章 / 场景 / 关联卡片 / 资源 / 约计字数）
- 名称不匹配禁止永久删除
- 影响查询失败禁止删除
- 原有恢复断言（先 preview 再原子恢复、未调用 `snapshot.restore`）与回收站影响展示用例均保留且未弱化

## 剩余风险

1. `TrashImpactView` 无独立“关系数”字段，当前以 `relatedCardCount`（关联卡片）作为关系代理；若后端后续新增 `relationCount`，需在类型与对话框标签同步。
2. 章 / 卷在快照**列表**中标题回退到 `reason`（权威名称仅在恢复对话框预览中显示），因导航树中常缺失该节点，属预期。
3. 成功后对话框保持打开以展示保护快照 ID，需用户手动点「关闭」；若产品希望自动关闭，需调整需求。
4. `protectionSnapshotId` 依赖后端返回；若旧版本接口返回空，对话框仍显示成功但 ID 行为为空（不报错）。
5. 测试内 `act()` 警告为挂载期异步副作用所致，非阻断，未纳入本轮清理范围。
