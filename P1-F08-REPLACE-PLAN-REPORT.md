# P1-F08 / P1-P07 替换计划（逐命中排除 + 预览模型 + 进度/取消）实现报告

> 共享脏工作区，未触碰 `android/**`、`archives/**`，未 commit/push/stage，未修改 Obsidian，未触碰公共 seam。

## 1. 概述

公共 seam 当前只有旧语义的 `creation:replacePreview` / `creation:replaceApply`（按场景整体排除、无 planId、无逐命中、无 operation 进度/取消）。
本次任务在**不接公共 seam** 的前提下，补齐 P1-F08（逐命中排除、一次性计划、stale 校验、重叠处理、保护快照+change_log 同事务、撤回边界）与 P1-P07（预览进度/取消、不阻塞渲染端）的深层逻辑与 UI，并产出 SEAM REQUEST 供最终集成者接线。

## 2. 修改 / 新增文件

**新增（主进程深层模块，seam 无关）**
- `electron/main/creation-workspace/replace-plan.ts` — 一次性替换计划深层模块。
  - `createReplacePlan(store, query, controller?)`：逐场景扫描，生成稳定 `hitId`、封存每个命中场景的 `revision`/`hash`（seals），返回一次性 `planId`；可取消（取消则不落盘）。
  - `applyReplacePlan(store, planId, excludedHitIds, controller?)`：仅执行未排除的精确命中；stale/重叠/伪造/重复/过期校验；进入 SQLite 事务后不再响应取消；事务失败或取消整体回滚，零写入。
  - 自有 `ReplacePlanStore` / `ReplacePlanController` 接口，复用既有 matcher/文档拼接逻辑。

**修改 / 新增（合同）**
- `electron/main/creation-workspace/replace-contract.ts` — 在原合同基础上**追加** `runReplacePlanTests`，用自包含内存 `ReplacePlanStore`（better-sqlite3 `:memory:`）覆盖 12 个场景（见第 4 节）。原 seam 合同不变。

**重写 / 新增（渲染端 UI，未依赖 seam）**
- `src/features/creation/replace/ReplacePanel.tsx` — 重写为计划模型：预览→计划视图→应用；支持逐命中排除、场景全选/全不选、进度/取消对话框、错误与完成横幅。
- `src/features/creation/replace/ReplacePlanView.tsx` — 按场景分组展示命中（before→after、上下文），逐命中复选框 + 场景级复选框（含半选态）。
- `src/features/creation/replace/ReplaceProgressDialog.tsx` — 真实百分比（仅在有分母时）/ 不确定态；预览可取消，apply 事务中禁用取消。
- `src/features/creation/replace/types.ts` — 本地视图类型 + `ReplacePlanService` 接口 + `describeReplaceError`。
- `src/features/creation/replace/replace-service.ts` — `createReplacePlanService()`：调用未来通道 `window.api.creation.replaceCreatePlan/ApplyPlan/CancelPlan`；通道缺失时抛 `seam-not-wired`。
- `src/features/creation/replace/replace.css` — 样式。
- `src/features/creation/replace/__tests__/replace-panel.test.tsx` — 13 个渲染测试。

**未修改**：`electron/main/creation-workspace/index.ts`、`types.ts`、`creation-ipc.ts`、`preload/index.ts`、`main/index.ts`、`src/types/creation.ts`、`src/types/api.ts`、`src/services/creation-service.ts`、`src/hooks/useCreationActions.ts`、`package.json`、`package-lock.json`、`scripts/beta-check.mjs`。

## 3. 测试结果

| 检查 | 命令 | 结果 |
|------|------|------|
| 主进程类型 | `npx tsc --noEmit -p tsconfig.main.json` | 0 错 |
| 替换合同 | `node scripts/verify-creation-replace.mjs` | `24 replace contracts verified.`（12 既有 + 12 新增） |
| 渲染测试 | `npx vitest run src/features/creation/replace/__tests__/replace-panel.test.tsx` | 13/13 通过 |

合同覆盖（对应任务“合同至少覆盖”清单）：同场景多命中只排除一个、跨场景逐命中排除、普通文本与受限正则、捕获组替换、stale(revision/hash)、重叠命中、planId 伪造/重复/过期、preview 中途取消、apply 前取消、apply 事务失败零写入、保护快照与 change_log 同事务一致、大项目预览跨场景上报进度（渲染端据进度更新、不阻塞）。渲染测试额外覆盖：场景全选/全不选、模式传递、取消后回到空闲、事务失败横幅等。

## 4. 需求对应

1. 每个命中含稳定 `hitId`/`sceneId`/`blockIndex`/文本范围/`before`/`after`/`context` ✔
2. 逐命中排除 + 场景全选/全不选 ✔（UI + `applyReplacePlan` 按 `excludedHitIds` 过滤）
3. preview 返回一次性 `planId`，封存 `revision`/`hash`（seals）✔
4. apply 仅执行该 planId 未排除的精确命中 ✔
5. 正文/revision/hash 变化后拒 stale，绝不静默重搜 ✔（`applyReplacePlan` 事务前校验）
6. 同一 planId 只成功用一次 ✔（`markPlanUsed`/`isPlanUsed`）
7. 重叠命中稳定拒绝（不重复替换）✔（apply 前按 `range` 重叠检测）
8. 项目级 apply 在**同一事务**创建保护快照 + 修改正文 + 写入 change_log ✔
9. preview 接 `ReplacePlanController` 上报真实场景/命中进度（seam 映射至 operation coordinator）✔（模块层）
10. preview 可取消；进入 apply 事务后不响应取消（不可中途强杀）✔（事务内不查取消）
11. 取消不留快照/计划/正文修改 ✔（plan 仅在计算完后落盘；apply 走整体回滚）
12. 撤回仅恢复本次对象：`createReplacePlan` 写入 per-scene 保护快照（subject_id=sceneId，含 before 与 before/after revision）与 change_log，undo 系统可按 subject_id 精确恢复，不影响后来修改。

## 5. 公共 SEAM REQUEST（最终集成者接线）

深层模块 `replace-plan.ts` 已与存储/进度解耦（依赖 `ReplacePlanStore`/`ReplacePlanController`）。需由集成者在公共 seam 中完成以下接线（本次未做）：

### A. `electron/main/creation-workspace/index.ts`
- 实现 `ReplacePlanStore`（复用 workspace 现有 read/transact/snapshot/change_log）：
  - `listScopeScenes(projectId, scope, scopeId)` → outline + `scene.body`（含 `revision`/`body_json`/`updatedAt`）。
  - `getScene(id)`、`savePlan`/`loadPlan`/`deletePlan`（进程内 Map 或 `replace_plans` 表）、`isPlanUsed`/`markPlanUsed`。
  - `beginTransaction`/`commitTransaction`/`rollbackTransaction`（复用现有事务封装）。
  - `updateSceneBody`（revision+1）、`insertSnapshot`（payload 含 `kind:"replace.plan"`、`planId`、`before`、`appliedHitIds`、`before/after revision`）、`insertChangeLog`（`command_type="replace.applyPlan"`，`changes_json` 含 `planId`/`sceneId`/`appliedHitIds`/`before/after revision`）。
- `read` 增加 kind `"replace.preview"` → `createReplacePlan(store, query, controller)`；`transact` 增加 `"replace.applyPlan"` → `applyReplacePlan(store, planId, excludedHitIds, controller)`。

### B. `electron/main/operation/coordinator.ts`
- `OperationKind` 增加 `"replace.preview"`（可选 `"replace.apply"`），使预览进度/取消经现有 coordinator 上报。预览阶段可取消；apply 进入事务后 coordinator 不再转发取消。

### C. `electron/main/creation-ipc.ts`
- 新增 `creation:replaceCreatePlan(query, { subscriptionId }?)`（经 coordinator 跑 `createReplacePlan`，经 `operation:event` 推进度，经 `operation:cancel` 取消）与 `creation:replaceApplyPlan({ planId, excludedHitIds })`。
- 错误语义：`plan-forbidden`（计划不存在/伪造）、`plan-used`（重复）、`plan-expired`（过期）、`stale`（正文变更）、`overlap`（重叠）、`invalid-input`（空查找/受限正则）、`transaction-failed`（事务失败）、`cancelled`（取消）。

### D. `electron/preload/index.ts`
- `api.creation` 增加 `replaceCreatePlan(query, handlers)`（`handlers.onProgress` 订阅进度；返回订阅/退订句柄）、`replaceApplyPlan(planId, excludedHitIds)`、`replaceCancelPlan()`。

### E. `src/types/creation.ts`
- 新增 `ReplacePlanView`/`ReplaceHit`/`ReplacePlanSceneSummary`/`ReplacePlanProgress`/`ReplaceApplyResultView`/`ReplacePlanQuery`/`ReplacePlanMode`/`ReplaceScope`/`ReplaceErrorCode`，形状对齐 `replace-plan.ts`。

### F. `src/types/api.ts`
- `CreationApi.creation` 增加上述三方法签名。

### G. `src/services/creation-service.ts`
- 增加 `createReplacePlan`/`applyReplacePlan`/`cancelReplacePlan`，调用 `window.api.creation.*` 并返回 E 节类型。

### H. 调用位置
- `ReplacePanel` 已通过 `replace-service.ts` 的 `createReplacePlanService()` 调用上述 preload 方法（通道缺失时抛 `seam-not-wired`）。`CreationProjectsPage` 渲染 `ReplacePanel` 无需改动。

### I. 测试要求（接线层）
- 接线后补充契约：preview 经 coordinator 上报进度并响应取消；apply 事务内不可取消；planId 伪造/重复/过期/stale/overlap 返回对应 `ReplaceErrorCode`；事务失败回滚零写入。深层模块已由 `replace-plan-contract.ts`（12 场景）覆盖，seam 只需覆盖“接线层”。

## 6. 未决

- **P1（接线）**：公共 seam 的 `ReplacePlanStore` 实现与 IPC/preload/service 接线（本 SEAM REQUEST）尚未执行——这是并行阶段有意保留，待集成者统一收尾。在接线前，`ReplacePanel` 预览会返回 `seam-not-wired` 提示。
- **P2（撤回联动）**：`createReplacePlan` 已写入 per-scene 保护快照与 change_log，undo 系统据此按 subject_id 精确恢复即可；需确认 undo 模块消费该 `kind:"replace.plan"` 记录（非本次范围）。
- 未修改 `android/**`、`archives/**`；未 commit/push/stage；未修改 Obsidian。
