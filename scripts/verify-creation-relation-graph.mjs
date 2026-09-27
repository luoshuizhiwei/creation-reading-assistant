import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readCreationWorkspace } from "./lib/creation-workspace-sources.mjs";

/**
 * Stage 4-F 关系图静态门禁：
 * 1) 整图查询必须存在且落到实处，避免 UI 端按卡片逐个拉关系（N+1）；
 * 2) 关系是全局卡片资产：项目视图只能做引用投影，不得复制/改写关系；
 * 3) 图上不做任何写入：关系的新建与删除只在卡片详情里；
 * 4) 过滤、节点摘要、跳转、范围说明必须真实存在，而不是画一张没有信息的图。
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

// 工作区已按职责拆分为多个域模块（纯移动式重构），断言范围覆盖全部实现模块。
const WORKSPACE_SCOPE_LABEL = "electron/main/creation-workspace/*.ts";
const files = {
  query: read("src/types/creation/query.ts"),
  workspace: readCreationWorkspace(),
  ipc: read("electron/main/creation-ipc.ts"),
  preload: read("electron/preload/index.ts"),
  api: read("src/types/api.ts"),
  service: read("src/services/creation-service.ts"),
  model: read("src/features/creation/cards/relation-graph.ts"),
  view: read("src/features/creation/cards/RelationGraphView.tsx"),
  page: read("src/features/creation/cards/CardsPage.tsx"),
  shell: read("scripts/verify-creation-project-shell.mjs")
};

const errors = [];
const requireSnippet = (label, content, snippet) => {
  if (!content.includes(snippet)) errors.push(`${label} 缺少：${snippet}`);
};
const forbidSnippet = (label, content, snippet) => {
  if (content.includes(snippet)) errors.push(`${label} 不应包含：${snippet}`);
};

// 1. 数据层：整图查询贯通 类型 → workspace → IPC → preload → api → service
requireSnippet("src/types/creation/query.ts", files.query, 'kind: "relationGraph.list"');
requireSnippet("src/types/creation/query.ts", files.query, "hiddenRelationCount");
requireSnippet("src/types/creation/query.ts", files.query, "truncatedNodeCount");
requireSnippet(WORKSPACE_SCOPE_LABEL, files.workspace, "runRelationGraph");
requireSnippet(WORKSPACE_SCOPE_LABEL, files.workspace, "clampRelationGraphLimit");
requireSnippet("electron/main/creation-ipc.ts", files.ipc, 'ipcMain.handle("creation:relationGraph"');
requireSnippet("electron/preload/index.ts", files.preload, '"creation:relationGraph"');
requireSnippet("src/types/api.ts", files.api, "relationGraph:");
requireSnippet("src/services/creation-service.ts", files.service, "export async function relationGraph");
requireSnippet("scripts/verify-creation-project-shell.mjs", files.shell, '"creation:relationGraph"');

// 2. 作用域语义：项目视图是引用投影，隐藏关系必须报数
requireSnippet(WORKSPACE_SCOPE_LABEL, files.workspace, "hiddenRelationCount += 1");
requireSnippet("src/features/creation/cards/relation-graph.ts", files.model, "describeRelationGraphScope");
requireSnippet("src/features/creation/cards/RelationGraphView.tsx", files.view, "relation-graph-scope");

// 3. 图只做展示与跳转：不得出现任何关系/卡片写入调用
forbidSnippet("RelationGraphView.tsx", files.view, "runStructure");
forbidSnippet("RelationGraphView.tsx", files.view, "cardRelation.create");
forbidSnippet("RelationGraphView.tsx", files.view, "cardRelation.delete");
forbidSnippet("relation-graph.ts", files.model, "runStructure");

// 4. 过滤 / 摘要 / 跳转 / 三态齐全
requireSnippet("src/features/creation/cards/relation-graph.ts", files.model, "export function filterRelationGraph");
requireSnippet("src/features/creation/cards/relation-graph.ts", files.model, "export function layoutRelationGraph");
requireSnippet("src/features/creation/cards/relation-graph.ts", files.model, "export function relationNodeSummary");
requireSnippet("src/features/creation/cards/RelationGraphView.tsx", files.view, "onSelectCard(cardId)");
requireSnippet("src/features/creation/cards/RelationGraphView.tsx", files.view, "只看有关系的卡片");
requireSnippet("src/features/creation/cards/RelationGraphView.tsx", files.view, 'role="alert"');
requireSnippet("src/features/creation/cards/CardsPage.tsx", files.page, 'id: "graph"');
requireSnippet("src/features/creation/cards/CardsPage.tsx", files.page, 'view !== "graph"');

if (errors.length > 0) {
  console.error("[verify-creation-relation-graph] Stage 4-F 关系图约束未满足：");
  for (const item of errors) console.error(`  - ${item}`);
  process.exit(1);
}

console.log("[verify-creation-relation-graph] 卡片关系图（过滤/摘要/跳转/项目引用投影）verified.");
