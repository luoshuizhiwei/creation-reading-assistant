# P1 桌面端：长任务公共接线与用户界面 — 完成报告

> 范围：备份/恢复、项目包导入/导出、资源完整性扫描四类长任务，从 WorkBuddy 已交付的后端
> operation coordinator 出发，补齐「渲染端 IPC 接线 + 进度/取消 UI + 只读扫描 UI + 测试」。
> 所有改动仅限桌面端（`electron/`、`src/`、相关 `scripts/`、`package.json`）。

## 一、修改文件清单

### 本次任务新建（未跟踪）
- `src/types/operation.ts` — 渲染端权威类型（`OperationState`/`OperationProgress`/`OperationResult`/`OperationStartRequest`/`ResourceIntegrityReport` 等，对齐 coordinator 真实形状）。
- `src/services/operation-service.ts` — 渲染端服务：`startOperation`/`getOperationState`/`cancelOperation`/`subscribeOperation`（逐字转发 preload `api.operation.*`）。
- `src/hooks/useOperation.ts` — `useOperation()`：operationId 隔离、重复启动拦截（`startingRef`）、取消、卸载仅退订不强制 kill、重试（`retry`）。
- `src/features/creation/operation/OperationProgressDialog.tsx` — 进度/取消/终态对话（真实百分比、不确定不编造百分比、提交阶段禁用取消、错误可见、资源扫描完成态内嵌面板）。
- `src/features/creation/operation/ResourceIntegrityScanPanel.tsx` — 只读资源扫描结果（分类、相对路径、无删除/自动修复入口）。
- `src/features/creation/operation/operation.css` — 上述组件样式（沿用 paper-ink / editorial-studio）。
- `src/services/__tests__/operation-service.test.ts`、`src/hooks/__tests__/use-operation.test.tsx`、`src/features/creation/operation/__tests__/operation-progress-dialog.test.tsx`、`src/features/creation/operation/__tests__/resource-integrity-scan-panel.test.tsx` — 新增定向测试。

### 本次任务修改（已跟踪）
- `electron/preload/index.ts` — `api.operation = { start, getState, cancel, subscribe }`，`subscribe` 返回 `{ unsubscribe }`，事件按 `subscriptionId` 过滤。
- `electron/main/index.ts` — 注册 `createOperationCoordinator()`、`registerOperationIpc(coordinator, orchestrator)`，`app.on("before-quit")` 调 `disposeAllOperations`。
- `src/features/settings/SettingsPage.tsx` — 「备份数据 / 恢复数据 / 扫描资源完整性」三个入口接到 `useOperation` + 对话；移除旧的 `createBackup`/`restoreBackup`/`restoreData` 直连。
- `src/features/creation/CreationProjectsPage.tsx` — 「导出项目包 / 导入项目包」接到 `useOperation` + 对话；导入完成后刷新项目列表与首页；移除旧的 `exportBundle`/`importBundle` 直连。
- `src/features/creation/__tests__/creation-projects-page.test.tsx` — 导入流程改为断言 operation 系统（启动 `{kind:"bundle.import"}`、完成触发首页重读、取消不重读）。
- `src/types/api.ts` — `DesktopApi` 增加 `operation` 字段。
- `src/types/creation.ts` — 重新导出 `ProjectBundleData`（供 IPC 层使用）。

### 后端（WorkBuddy 交付，本任务仅做最小衔接验证）
- `electron/main/operation/{types,coordinator,ipc,operations,contract}.ts`（未跟踪目录，由 WorkBuddy 提供）。

---

## 二、WorkBuddy seam 是否足够 + 最小调整

**结论：seam 基本足够，仅做了必要的最小衔接（无第二套 coordinator，无大改后端）。**

核验后端时发现并经修复的缝隙：
1. **渲染端组件相对路径错误（本次任务发现并修复）**：`OperationProgressDialog.tsx` 与 `ResourceIntegrityScanPanel.tsx` 原用 `../../types/operation`，但实际位于 `src/features/creation/operation/`，正确路径为 `../../../types/operation`。该错误若不修，渲染端 tsc 直接失败。已修正。
2. **类型对齐（前序任务已对齐，本次复核确认）**：渲染端 `OperationState.OperationProgress` 严格匹配 coordinator 真实字段（`phase/completed/total/bytesCompleted/bytesTotal/indeterminate`），不使用虚构的 `stage/percent/count/message`，从而**杜绝假进度**。
3. **coordinator 增强（前序任务最小追加，向后兼容）**：`subscribe(operationId, listener)`、`terminateAll()`、`OperationState.result`、`OperationKind."resource.scan"` —— 均为连接器必需的扩展，不破坏 WorkBuddy 既有合同测试。
4. **workspace 安全桥梁**：`runBackupRestore`/`runBundleImport` 通过可选 `withWorkspaceClosed` 包裹破坏性工作，避免恢复/导入时破坏在用的 SQLite（对齐旧实现行为）。
5. **bundle 导出形状**：`prepareBundleExport` 在 orchestrator 内 `openCreationWorkspace` 并 `workspace.read({kind:"project.bundle.export", projectId})` 取得 `ProjectBundleData`，再交给 `runBundleExport`。

---

## 三、UI 操作与取消行为

- **四类入口统一走 `useOperation` + `OperationProgressDialog`**，进度一致（备份创建/恢复、项目包导出/导入、资源扫描）。
- **真实阶段**：`PHASE_LABEL` 将 `validating/scanning/hashing/copying/database/committing/cleanup/done` 映射为中文阶段名；有总数时显示真实百分比与计数，不确定时仅显示「正在处理，请稍候…」，**绝不编造百分比**。
- **取消按钮规则**：运行中且阶段可中断时可用；进入 `database`/`committing` 等不可中断阶段时**禁用取消并显示「正在完成安全提交，此阶段不可取消，请勿关闭应用」**；终态（completed/cancelled/failed）无取消按钮，仅保留「关闭」。
- **终态区分**：completed 显示「操作已完成」、cancelled 显示「操作已取消…当前数据未被破坏」、failed 显示错误标题 + 消息 + 错误码（**错误信息常驻可见，不只在 toast**）。
- **资源完整性扫描（只读）**：清晰入口「扫描资源完整性」；完成态内嵌 `ResourceIntegrityScanPanel`，按分类（文件缺失/未引用文件/大小不符/哈希不符/相对路径不安全/符号链接逃逸/类型不符/记录冲突）汇总数量与相对路径；空结果显示「未发现资源完整性问题」；**无任何删除 / 自动修复 / 清理入口**，并显式说明。
- **防重复提交**：按钮在 `operation.isActive` 时禁用；`useOperation.start` 在异步前置阶段（`startingRef`）与运行终态前均拦截重复启动并抛「已有进行中的任务」。
- **关闭对话不破坏非中断任务**：运行中对遮罩/关闭按钮禁用；终态关闭仅清空调试态（`reset`），绝不强制 kill 提交阶段。

---

## 四、listener 生命周期

- **preload 侧**：`subscribe` 用 `ipcRenderer.on("operation:event", ...)`，并对发送端 `sender.once("destroyed")` 注册退订，发送端销毁时自动清理，**无泄漏**。
- **渲染端 hook 侧**：`subscribeOperation` 返回的 `unsubscribe` 存入 `unsubscribeRef`；终态到达或 `reset` 时清理；组件卸载（`useEffect` cleanup）调用退订——**仅退订订阅，绝不调用 `cancelOperation` 强制中止进行中的任务**（已用测试断言：卸载时 `cancelOperation` 不被调用）。
- **operationId 隔离**：渲染端监听器仅接受 `next.operationId === activeOpIdRef.current` 的事件；已完成任务的迟到事件**不会覆盖新任务的 UI**（已测试）。
- **进程退出兜底**：`app.on("before-quit")` 调 `disposeAllOperations(coordinator)`，释放所有订阅与进行中任务。

---

## 五、测试命令 / 退出码 / 数量

新增定向测试（全部通过）：
| 文件 | 命令 | 结果 |
|---|---|---|
| `operation-service.test.ts` | `npx vitest run src/services/__tests__/operation-service.test.ts` | 5/5 ✓ |
| `use-operation.test.tsx` | `npx vitest run src/hooks/__tests__/use-operation.test.tsx` | 7/7 ✓ |
| `operation-progress-dialog.test.tsx` | `npx vitest run src/features/creation/operation/__tests__/operation-progress-dialog.test.tsx` | 10/10 ✓ |
| `resource-integrity-scan-panel.test.tsx` | `npx vitest run src/features/creation/operation/__tests__/resource-integrity-scan-panel.test.tsx` | 4/4 ✓ |
| `creation-projects-page.test.tsx`（更新） | `npx vitest run src/features/creation/__tests__/creation-projects-page.test.tsx` | 11/11 ✓ |

WorkBuddy 合同 + 验证脚本（全部退出码 0）：
| 命令 | 结果 |
|---|---|
| `npm run verify:operation` | 18 operation contracts verified（退出 0） |
| `npm run verify:backup-restore` | 37 backup contracts verified（退出 0） |
| `npm run verify:auto-backup` | 26 tests passed（退出 0） |
| `npm run verify:creation-bundle` | 9 bundle contracts verified（退出 0） |
| `npm run verify:creation-bundle-files` | 17 bundle file contracts verified（退出 0） |

类型检查与构建：
- `tsc -p tsconfig.main.json` = 0 错误；`tsc -p tsconfig.renderer.json` = 0 错误；`tsc -p tsconfig.node.json` = 0 错误。
- `npm run build`（tsc ×3 + `electron-vite build`）= 退出 0，无 `error TS` / `Build failed`。

完整渲染端测试：`npx vitest run` 全绿。**仅 4 个测试被 skip**（`creation-workspace-legacy`），原因是 CI 环境 `better-sqlite3` 原生模块 Node 版本不匹配（`NODE_MODULE_VERSION 130 vs 137`），与本任务无关，且为 skip 非 fail。

`git diff --check -- . ':(exclude)android/**' ':(exclude)archives/**'` = 退出 0（唯一警告为某个**既有未修改**文件的 CRLF/LF 提示，非本次改动，未触碰）。

---

## 六、未解决 P0 / P1 / P2

- **P0：无。** 构建、三套 tsc、全部 verify 脚本、全部定向测试与整轮渲染端测试均通过，无阻断项。
- **P1（可接受，建议后续补强）**：
  - `SettingsPage` 的备份/恢复/扫描三个入口目前由 tsc + build + 共享 `operation` 组件/钩子测试覆盖，**尚未有独立的 SettingsPage 交互测试**（该页依赖较多服务，重 Mock 成本较高，留作后续）；功能路径与 CreationProjectsPage 走同一套 operation 系统，无回归。
- **P2（代码整洁，不影响正确性）**：
  - `OperationProgressDialog` 中 `operation-count` 文案分支当前不可达：当 `total>0` 且非不确定时百分比必被计算，故「X / Y」计数文本不渲染（改为始终显示百分比，仍为真进度、无假进度）。可后续简化为仅保留百分比显示。
  - `operation.css` 中 `.operation-message` 类暂未使用（coordinator 的 `OperationProgress` 无 `message` 字段），保留以备扩展。
  - 旧 `useCreationActions.exportBundle/importBundle` 仍导出但页面不再直连，保留无害。

---

## 七、约束遵守声明

- **未修改 `android/**` 与 `archives/**`**：本次改动全部位于 `electron/`、`src/`、`scripts/`、`package.json`（含新增 `verify-operation.mjs` 等）、`src/types/`，未触碰移动端或冻结归档。
- **未执行 commit / push / stage / reset / checkout / clean / 全仓格式化**：仅在工作区新增与修改文件，未改动版本库状态。
- **未修改 Obsidian**：未涉及 Obsidian Codex 相关内容。
- **未重新实现第二套 operation coordinator**：沿用 WorkBuddy 后端，仅做最小接线与类型对齐。
- 既有未提交改动（含其他 agent 的 history/cards/editor 等 P1 工作）均予保留，未做清空或 renormalize。
