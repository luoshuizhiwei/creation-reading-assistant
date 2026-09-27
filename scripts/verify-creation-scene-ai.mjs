import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readMainProcess } from "./lib/main-process-sources.mjs";

/**
 * Stage 4-D 场景 AI 静态门禁：
 * 续写 / 精简 / 角色一致性必须真实存在，且
 *   1) 输出只能是候选或报告，未经确认绝不写正文；
 *   2) 场景上下文必须复用既有确认边界（被排除的组不得进入提示词）；
 *   3) 不得因为引入场景动作而破坏收件箱灵感侧的既有行为。
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

const files = {
  types: read("src/types/ai.ts"),
  prompt: read("electron/main/ai-prompt.ts"),
  // AI 调用与提示词委托已按职责拆到 settings-store.ts，断言需覆盖整个主进程模块集合。
  main: readMainProcess(),
  actions: read("src/features/creation/ai/scene-ai-actions.ts"),
  margin: read("src/features/creation/editor/desk/WritingDeskMargin.tsx"),
  candidate: read("src/features/creation/ai/SceneCandidateReview.tsx"),
  report: read("src/features/creation/ai/SceneAiReport.tsx"),
  confirm: read("src/features/creation/inbox/ai-send-confirm.tsx"),
  inbox: read("src/features/creation/inbox/InboxPage.tsx")
};

const errors = [];
const requireSnippet = (label, content, snippet) => {
  if (!content.includes(snippet)) errors.push(`${label} 缺少：${snippet}`);
};
const forbidSnippet = (label, content, snippet) => {
  if (content.includes(snippet)) errors.push(`${label} 不应包含：${snippet}`);
};

// 1. 类型层：三个新动作与分派函数
for (const action of ['"continuation"', '"condensing"', '"character-consistency"']) {
  requireSnippet("src/types/ai.ts", files.types, action);
}
requireSnippet("src/types/ai.ts", files.types, "resolveAiPromptKind");
requireSnippet("src/types/ai.ts", files.types, "isSceneOnlyAiAction");
requireSnippet("src/types/ai.ts", files.types, "isKnownAiAction");

// 2. 主进程提示词：三个新动作各有指令，且未知动作被拒
requireSnippet("electron/main/ai-prompt.ts", files.prompt, 'case "continuation"');
requireSnippet("electron/main/ai-prompt.ts", files.prompt, 'case "condensing"');
requireSnippet("electron/main/ai-prompt.ts", files.prompt, 'case "character-consistency"');
requireSnippet("electron/main/ai-prompt.ts", files.prompt, "isKnownAiAction");
requireSnippet("主进程模块集合", files.main, "buildAIPromptFromModule");

// 3. 动作语义：续写=追加、检查类=只读报告
requireSnippet("scene-ai-actions.ts", files.actions, 'continuation: "续写场景"');
requireSnippet("scene-ai-actions.ts", files.actions, 'condensing: "精简场景"');
requireSnippet("scene-ai-actions.ts", files.actions, '"character-consistency"');
requireSnippet("scene-ai-actions.ts", files.actions, 'const APPEND_ACTIONS: ReadonlySet<string> = new Set(["continuation"]);');
requireSnippet(
  "scene-ai-actions.ts",
  files.actions,
  'const REPORT_ACTIONS: ReadonlySet<string> = new Set(["consistency", "character-consistency"]);'
);

// 4. 写作台：六个入口统一由动作表生成，禁止再硬编码单个按钮文案
requireSnippet("WritingDeskMargin.tsx", files.margin, "SCENE_AI_ACTION_ORDER.map");
requireSnippet("WritingDeskMargin.tsx", files.margin, "sceneAiOutputKind");
requireSnippet("WritingDeskMargin.tsx", files.margin, "sceneAiAdoptMode");
requireSnippet("WritingDeskMargin.tsx", files.margin, "buildSceneContext");
requireSnippet("WritingDeskMargin.tsx", files.margin, "sceneContext:");
forbidSnippet("WritingDeskMargin.tsx", files.margin, "> 润色场景");
forbidSnippet("WritingDeskMargin.tsx", files.margin, "> 一致性检查");

// 5. 写入正文只有一条路径，且必须先建保护快照
const saveCalls = files.margin.match(/saveSceneBody\(/g) ?? [];
if (saveCalls.length !== 1) errors.push(`WritingDeskMargin.tsx 的 saveSceneBody 调用应恰好 1 处，实际 ${saveCalls.length} 处`);
requireSnippet("WritingDeskMargin.tsx", files.margin, '"snapshot.create"');

// 6. 候选评审支持追加语义；报告侧无采纳入口
requireSnippet("SceneCandidateReview.tsx", files.candidate, 'mode === "append"');
requireSnippet("SceneCandidateReview.tsx", files.candidate, "mergeSceneBody");
forbidSnippet("SceneAiReport.tsx", files.report, "onAccept");

// 7. 上下文边界：确认框回传排除集合，调用方据此裁剪 sceneContext
requireSnippet("ai-send-confirm.tsx", files.confirm, "excluded?: ReadonlySet<string>");
requireSnippet("ai-send-confirm.tsx", files.confirm, "onConfirm(finalContent, remember, new Set(excluded))");

// 8. 回归护栏：收件箱灵感侧不携带场景上下文，走的是灵感提示词分支
forbidSnippet("InboxPage.tsx", files.inbox, "sceneContext");

if (errors.length > 0) {
  console.error("[verify-creation-scene-ai] Stage 4-D 场景 AI 约束未满足：");
  for (const item of errors) console.error(`  - ${item}`);
  process.exit(1);
}

console.log("[verify-creation-scene-ai] 场景 AI（续写/精简/角色一致性）与确认边界 verified.");
