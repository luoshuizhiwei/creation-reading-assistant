/**
 * 全项目替换计划（P1-F08 / P1-P07）—— 独立的深层模块，不依赖公共 seam。
 *
 * 设计要点：
 *  - preview 计算一次性 planId，并封存每个命中场景的正文 revision / 哈希（seals）。
 *  - 每个命中拥有稳定 hitId、sceneId、blockIndex、文本范围、before/after 与上下文。
 *  - apply 仅执行该 planId 中未排除的精确命中；正文/revision/哈希变化后拒绝 stale plan，
 *    绝不静默重新搜索；同一 planId 只能成功使用一次。
 *  - 重叠命中稳定拒绝（不重复替换）。
 *  - 项目级 apply 在「同一 SQLite 事务」内创建保护快照、修改正文并写入 change_log；
 *    事务失败或取消则整体回滚，不留下任何快照 / 计划 / 正文修改。
 *  - 进度与取消通过 ReplacePlanController 上报；进入 apply 事务后不再检查取消（不可中途强杀）。
 *
 * 该模块通过 ReplacePlanStore 接口与具体存储解耦；公共 seam（creation-workspace/index.ts）
 * 之后实现该接口并接线到 operation coordinator，见任务交付的 SEAM REQUEST。
 */
import { createHash, randomUUID } from "node:crypto";
import type { CreationDocument } from "../../../src/types/creation";
import type {
  ReplaceApplyOutcome,
  ReplaceHit,
  ReplacePlan,
  ReplacePlanMode,
  ReplacePlanQuery,
  ReplacePlanSceneSeal,
  ReplacePlanSceneSummary,
  ReplacePlanScope,
  ReplaceTextRange
} from "../../../src/types/creation";

// 兼容 re-export：供主进程内部模块按原路径（./replace-plan）引用这些 seam 类型。
export type {
  ReplaceApplyOutcome,
  ReplaceHit,
  ReplacePlan,
  ReplacePlanMode,
  ReplacePlanQuery,
  ReplacePlanSceneSeal,
  ReplacePlanSceneSummary,
  ReplacePlanScope,
  ReplaceTextRange
} from "../../../src/types/creation";

// ---- 常量（与既有 replace 实现保持一致） ----
const DEFAULT_REPLACE_PREVIEW_LIMIT = 200;
const MAX_REPLACE_PREVIEW_LIMIT = 1000;
const MAX_REGEX_LENGTH = 200;
const REGEX_FORBIDDEN_PATTERN = /\(\?[=<!]|\\[1-9]/;
const REPLACE_PLAN_TTL_MS = 30 * 60 * 1000;
const CHANGE_LOG_COMMAND_TYPE = "replace.applyPlan";

// ---- 公共类型 ----

export type ReplacePlanPhase = "scanning" | "planning";

export interface ReplacePlanProgress {
  phase: ReplacePlanPhase;
  completedScenes: number;
  totalScenes: number;
  completedHits: number;
  totalHits: number;
}

export interface ReplacePlanController {
  isCancellationRequested(): boolean;
  throwIfCancelled(): void;
  reportProgress(progress: ReplacePlanProgress): void;
}

export type ReplacePlanErrorCode =
  | "invalid-input"
  | "not-found"
  | "stale"
  | "overlap"
  | "plan-used"
  | "plan-forbidden"
  | "plan-expired"
  | "cancelled"
  | "transaction-failed";

export class ReplacePlanError extends Error {
  readonly code: ReplacePlanErrorCode;

  constructor(code: ReplacePlanErrorCode, message: string) {
    super(message);
    this.name = "ReplacePlanError";
    this.code = code;
  }
}

export interface ReplacePlanSceneRow {
  id: string;
  projectId: string;
  chapterId: string;
  chapterTitle: string;
  title: string;
  bodyJson: string;
  revision: number;
  updatedAt: string;
}

/**
 * 存储抽象：由公共 seam（index.ts）实现。深层模块只依赖此接口，便于独立测试。
 */
export interface ReplacePlanStore {
  listScopeScenes(projectId: string, scope: ReplacePlanScope, scopeId?: string): ReplacePlanSceneRow[];
  getScene(id: string): { bodyJson: string; revision: number; updatedAt: string } | undefined;
  savePlan(plan: ReplacePlan): void;
  loadPlan(planId: string): ReplacePlan | undefined;
  deletePlan(planId: string): void;
  isPlanUsed(planId: string): boolean;
  markPlanUsed(planId: string): void;
  beginTransaction(): void;
  commitTransaction(): void;
  rollbackTransaction(): void;
  updateSceneBody(id: string, bodyJson: string, newRevision: number, timestamp: string): void;
  insertSnapshot(id: string, projectId: string, subjectId: string, payloadJson: string, createdAt: string): void;
  insertChangeLog(projectId: string, commandType: string, changesJson: string, committedAt: string): number;
}

// ---- 内部工具 ----
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function hashBody(bodyJson: string): string {
  return createHash("sha256").update(bodyJson).digest("hex");
}

function clampLimit(limit: number | undefined): number {
  const fallback = limit === undefined ? DEFAULT_REPLACE_PREVIEW_LIMIT : limit;
  if (!Number.isFinite(fallback)) return DEFAULT_REPLACE_PREVIEW_LIMIT;
  return Math.min(MAX_REPLACE_PREVIEW_LIMIT, Math.max(1, Math.floor(fallback)));
}

function compileReplaceRegex(source: string): RegExp {
  if (source.length > MAX_REGEX_LENGTH) {
    throw new ReplacePlanError("invalid-input", `正则长度不能超过 ${MAX_REGEX_LENGTH} 个字符。`);
  }
  if (REGEX_FORBIDDEN_PATTERN.test(source)) {
    throw new ReplacePlanError("invalid-input", "受限正则不支持环视断言或后向引用。");
  }
  try {
    return new RegExp(source, "g");
  } catch {
    throw new ReplacePlanError("invalid-input", "正则表达式无效。");
  }
}

function findPlainMatches(find: string, text: string): Array<{ start: number; text: string }> {
  const out: Array<{ start: number; text: string }> = [];
  if (!find) return out;
  let index = 0;
  while ((index = text.indexOf(find, index)) >= 0) {
    out.push({ start: index, text: find });
    index += find.length;
  }
  return out;
}

function findRegexMatches(regex: RegExp, text: string): Array<{ start: number; text: string }> {
  const out: Array<{ start: number; text: string }> = [];
  regex.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    out.push({ start: match.index, text: match[0] });
    if (match[0].length === 0) regex.lastIndex += 1;
  }
  return out;
}

function makeContext(concat: string, start: number, end: number, radius = 16): string {
  const s = Math.max(0, start - radius);
  const e = Math.min(concat.length, end + radius);
  const before = s > 0 ? "…" : "";
  const after = e < concat.length ? "…" : "";
  return `${before}${concat.slice(s, e)}${after}`;
}

function makeHitId(sceneId: string, blockIndex: number, nodeStart: number, localStart: number, before: string): string {
  const seed = `${sceneId}|${blockIndex}|${nodeStart}|${localStart}|${before}`;
  return createHash("sha1").update(seed).digest("hex").slice(0, 16);
}

interface WalkNode {
  blockIndex: number;
  node: Record<string, unknown>;
  concatStart: number;
  concatEnd: number;
}

/**
 * 遍历正文文档，复刻既有 extractSceneText 的拼接规则（块间 "\n\n"，sceneBreak 为 "　　"），
 * 同时记录每个文本节点的拼接偏移，用于把命中范围映射回文档节点。
 */
function walkDocument(doc: CreationDocument | null): { concat: string; nodes: WalkNode[] } {
  const blocks = Array.isArray(doc?.content) ? doc.content : [];
  const nodes: WalkNode[] = [];
  let concat = "";
  blocks.forEach((block, blockIndex) => {
    if (!isRecord(block)) return;
    if (blockIndex > 0) concat += "\n\n";
    if (block.type === "sceneBreak") {
      concat += "　　";
      return;
    }
    if (!Array.isArray(block.content)) return;
    const collect = (nodeList: unknown[]): void => {
      for (const node of nodeList) {
        if (!isRecord(node)) continue;
        if (node.type === "text" && typeof node.text === "string") {
          const start = concat.length;
          nodes.push({ blockIndex, node, concatStart: start, concatEnd: start + node.text.length });
          concat += node.text;
        } else if (Array.isArray(node.content)) {
          collect(node.content);
        }
      }
    };
    collect(block.content);
  });
  return { concat, nodes };
}

function computeAfter(mode: ReplacePlanMode, before: string, find: string, replaceWith: string, regex?: RegExp): string {
  if (mode === "regex" && regex) return before.replace(regex, replaceWith);
  return replaceWith;
}

function buildSceneHits(
  bodyJson: string,
  find: string,
  replaceWith: string,
  mode: ReplacePlanMode,
  sceneId: string,
  limit: number
): { hits: ReplaceHit[]; truncated: boolean } {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return { hits: [], truncated: false };
  }
  const walk = walkDocument(document);
  const hits: ReplaceHit[] = [];
  let truncated = false;
  const regex = mode === "regex" ? compileReplaceRegex(find) : undefined;
  for (const nodeRef of walk.nodes) {
    const nodeText = typeof nodeRef.node.text === "string" ? nodeRef.node.text : "";
    const matches = mode === "regex" && regex ? findRegexMatches(regex, nodeText) : findPlainMatches(find, nodeText);
    for (const match of matches) {
      if (hits.length >= limit) {
        truncated = true;
        break;
      }
      const before = match.text;
      const after = computeAfter(mode, before, find, replaceWith, regex);
      const start = nodeRef.concatStart + match.start;
      const end = start + before.length;
      hits.push({
        hitId: makeHitId(sceneId, nodeRef.blockIndex, nodeRef.concatStart, match.start, before),
        sceneId,
        blockIndex: nodeRef.blockIndex,
        range: { start, end },
        before,
        after,
        context: makeContext(walk.concat, start, end)
      });
    }
    if (truncated) break;
  }
  return { hits, truncated };
}

/**
 * 把精确命中应用回正文文档。每个命中都位于单一文本节点内（按节点本地匹配得到），
 * 故对每个节点按右→左顺序应用编辑即可保持偏移有效，绝不重复替换。
 */
function applyReplacement(bodyJson: string, hits: ReplaceHit[]): string {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return bodyJson;
  }
  const working = JSON.parse(JSON.stringify(document)) as CreationDocument;
  const walk = walkDocument(working);
  const editsByNode = new Map<WalkNode, Array<{ localStart: number; localEnd: number; after: string }>>();
  for (const hit of hits) {
    const node = walk.nodes.find((n) => hit.range.start >= n.concatStart && hit.range.end <= n.concatEnd);
    if (!node) continue; // 防御：跨节点命中（生成阶段不会发生）跳过
    const localStart = hit.range.start - node.concatStart;
    const localEnd = hit.range.end - node.concatStart;
    const list = editsByNode.get(node) ?? [];
    list.push({ localStart, localEnd, after: hit.after });
    editsByNode.set(node, list);
  }
  for (const [node, edits] of editsByNode) {
    edits.sort((a, b) => b.localStart - a.localStart);
    let text = typeof node.node.text === "string" ? node.node.text : "";
    for (const edit of edits) {
      text = text.slice(0, edit.localStart) + edit.after + text.slice(edit.localEnd);
    }
    node.node.text = text;
  }
  return JSON.stringify(working);
}

// ---- 主流程 ----
export async function createReplacePlan(
  store: ReplacePlanStore,
  query: ReplacePlanQuery,
  controller?: ReplacePlanController
): Promise<ReplacePlan> {
  if (!query.projectId) throw new ReplacePlanError("invalid-input", "缺少 projectId。");
  if (!query.find) throw new ReplacePlanError("invalid-input", "查找内容不能为空。");
  if (query.mode === "regex") compileReplaceRegex(query.find); // 预热，非法正则在此抛 invalid-input

  const limit = clampLimit(query.limit);
  const scenes = store.listScopeScenes(query.projectId, query.scope, query.scopeId);
  const hits: ReplaceHit[] = [];
  const seals: Record<string, ReplacePlanSceneSeal> = {};
  const sceneSummaries: ReplacePlanSceneSummary[] = [];
  let truncated = false;
  let remaining = limit;

  controller?.reportProgress({
    phase: "scanning",
    completedScenes: 0,
    totalScenes: scenes.length,
    completedHits: 0,
    totalHits: 0
  });

  for (let i = 0; i < scenes.length; i++) {
    controller?.throwIfCancelled();
    const scene = scenes[i];
    const built = buildSceneHits(scene.bodyJson, query.find, query.replaceWith, query.mode, scene.id, remaining);
    if (built.hits.length > 0) {
      for (const hit of built.hits) hits.push(hit);
      seals[scene.id] = { sceneId: scene.id, revision: scene.revision, hash: hashBody(scene.bodyJson) };
      sceneSummaries.push({
        sceneId: scene.id,
        chapterId: scene.chapterId,
        chapterTitle: scene.chapterTitle,
        title: scene.title,
        hitCount: built.hits.length
      });
      remaining -= built.hits.length;
      if (remaining <= 0) truncated = true;
    }
    controller?.reportProgress({
      phase: "scanning",
      completedScenes: i + 1,
      totalScenes: scenes.length,
      completedHits: hits.length,
      totalHits: hits.length
    });
    if (controller) await Promise.resolve(); // 让出事件循环，支持取消与渲染端不阻塞
    if (remaining <= 0) break;
  }

  controller?.throwIfCancelled(); // 保存前的最后一道取消闸门：取消则不留下任何计划

  const now = Date.now();
  const plan: ReplacePlan = {
    planId: randomUUID(),
    projectId: query.projectId,
    scope: query.scope,
    scopeId: query.scopeId,
    find: query.find,
    replaceWith: query.replaceWith,
    mode: query.mode,
    scenes: sceneSummaries,
    hits,
    totalHits: hits.length,
    limit,
    truncated,
    seals,
    sealedAt: new Date(now).toISOString(),
    expiresAt: new Date(now + REPLACE_PLAN_TTL_MS).toISOString(),
    contentHash: hashBody(
      JSON.stringify({ find: query.find, replaceWith: query.replaceWith, mode: query.mode, scope: query.scope, hits })
    )
  };

  controller?.reportProgress({
    phase: "planning",
    completedScenes: scenes.length,
    totalScenes: scenes.length,
    completedHits: hits.length,
    totalHits: hits.length
  });

  store.savePlan(plan); // 计算完成后才落盘；此前取消则无 plan、无快照、无正文修改
  return plan;
}

export async function applyReplacePlan(
  store: ReplacePlanStore,
  planId: string,
  excludedHitIds: string[],
  controller?: ReplacePlanController
): Promise<ReplaceApplyOutcome> {
  controller?.throwIfCancelled();

  const plan = store.loadPlan(planId);
  if (!plan) throw new ReplacePlanError("plan-forbidden", "替换计划不存在或已失效（planId 无效）。");
  if (store.isPlanUsed(planId)) throw new ReplacePlanError("plan-used", "该替换计划已使用，不能重复应用。");
  if (Date.now() > new Date(plan.expiresAt).getTime()) {
    throw new ReplacePlanError("plan-expired", "替换计划已过期，请重新预览。");
  }

  const excluded = new Set(excludedHitIds);
  const byScene = new Map<string, ReplaceHit[]>();
  for (const hit of plan.hits) {
    if (excluded.has(hit.hitId)) continue;
    const list = byScene.get(hit.sceneId) ?? [];
    list.push(hit);
    byScene.set(hit.sceneId, list);
  }

  // 事务开始前：新鲜度 + 重叠校验；任何失败都不开启事务，零写入。
  for (const [sceneId, sceneHits] of byScene) {
    const current = store.getScene(sceneId);
    const seal = plan.seals[sceneId];
    if (!current || !seal) {
      throw new ReplacePlanError("stale", `场景 ${sceneId} 已不可用，计划已失效。`);
    }
    if (current.revision !== seal.revision || hashBody(current.bodyJson) !== seal.hash) {
      throw new ReplacePlanError("stale", "场景正文自预览后已变更，计划已失效，请重新预览。");
    }
    const sorted = [...sceneHits].sort((a, b) => a.range.start - b.range.start);
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].range.start < sorted[i - 1].range.end) {
        throw new ReplacePlanError("overlap", "存在重叠的替换命中，已拒绝应用以避免重复替换。");
      }
    }
  }

  controller?.throwIfCancelled();

  const snapshotIds: string[] = [];
  const modifiedSceneIds: string[] = [];
  let appliedHitCount = 0;
  let lastSequence = 0;

  store.beginTransaction();
  try {
    const now = new Date().toISOString();
    for (const [sceneId, sceneHits] of byScene) {
      const current = store.getScene(sceneId);
      if (!current) {
        store.rollbackTransaction();
        throw new ReplacePlanError("stale", `场景 ${sceneId} 在应用前不可用。`);
      }
      const newBody = applyReplacement(current.bodyJson, sceneHits);
      const newRevision = current.revision + 1;
      const snapId = randomUUID();
      store.insertSnapshot(
        snapId,
        plan.projectId,
        sceneId,
        JSON.stringify({
          kind: "replace.plan",
          planId,
          sceneId,
          before: current.bodyJson,
          appliedHitIds: sceneHits.map((h) => h.hitId),
          beforeRevision: current.revision,
          afterRevision: newRevision
        }),
        now
      );
      snapshotIds.push(snapId);
      store.updateSceneBody(sceneId, newBody, newRevision, now);
      modifiedSceneIds.push(sceneId);
      appliedHitCount += sceneHits.length;
      lastSequence = store.insertChangeLog(
        plan.projectId,
        CHANGE_LOG_COMMAND_TYPE,
        JSON.stringify({
          planId,
          sceneId,
          appliedHitIds: sceneHits.map((h) => h.hitId),
          beforeRevision: current.revision,
          afterRevision: newRevision
        }),
        now
      );
    }
    store.markPlanUsed(planId);
    store.commitTransaction();
    return {
      planId,
      sequence: lastSequence,
      appliedHitCount,
      modifiedSceneIds,
      snapshotIds,
      committedAt: new Date().toISOString()
    };
  } catch (error) {
    store.rollbackTransaction();
    store.deletePlan(planId); // 失败未成功使用，删除计划以便可重试（除非已被外部标记）
    if (error instanceof ReplacePlanError) throw error;
    throw new ReplacePlanError("transaction-failed", `应用替换失败：${(error as Error).message}`);
  }
}
