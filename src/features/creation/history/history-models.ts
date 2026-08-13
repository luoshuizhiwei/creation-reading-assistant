import type {
  CardSummary,
  CreationProjectNavigation,
  SnapshotInfo,
  SnapshotSubjectType,
  TrashItem
} from "@/types/creation";

/**
 * 历史页纯模型与纯函数：不依赖 React，便于直接做单元测试。
 * 页面组件只消费这些纯函数，不重写逻辑。
 */

export interface SelectableObject {
  type: SnapshotSubjectType;
  id: string;
  title: string;
}

export function listSelectableObjects(
  subjectType: SnapshotSubjectType,
  navigation: CreationProjectNavigation | null | undefined,
  cards: CardSummary[]
): SelectableObject[] {
  if (subjectType === "scene") {
    const list: SelectableObject[] = [];
    if (navigation) {
      for (const chapter of navigation.chapters) {
        for (const scene of chapter.scenes) {
          list.push({ type: "scene", id: scene.id, title: scene.title });
        }
      }
    }
    return list;
  }
  return cards.map((c) => ({ type: "card" as const, id: c.id, title: c.title }));
}

/**
 * 校验创建里程碑输入。返回 ok=true 表示可提交；ok=false 时给出可读原因。
 * 测试覆盖：空名称、空 subjectId、正常输入。
 */
export function validateMilestoneInput(input: {
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
}): { ok: true } | { ok: false; reason: string } {
  if (!input.subjectId) {
    return { ok: false, reason: "请先选择一个对象。" };
  }
  const trimmed = input.reason.trim();
  if (trimmed.length === 0) {
    return { ok: false, reason: "里程碑名称不能为空。" };
  }
  return { ok: true };
}

export function buildMilestoneCommand(input: {
  projectId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
}): {
  type: "snapshot.create";
  projectId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
} {
  return {
    type: "snapshot.create",
    projectId: input.projectId,
    subjectType: input.subjectType,
    subjectId: input.subjectId,
    reason: input.reason.trim()
  };
}

export function findSceneTitle(
  navigation: CreationProjectNavigation | null | undefined,
  sceneId: string
): string | null {
  if (!navigation) return null;
  for (const chapter of navigation.chapters) {
    for (const scene of chapter.scenes) {
      if (scene.id === sceneId) return scene.title;
    }
  }
  return null;
}

export function findCardTitle(cards: CardSummary[], cardId: string): string | null {
  const card = cards.find((c) => c.id === cardId);
  return card ? card.title : null;
}

export function snapshotSubjectTitle(
  snapshot: SnapshotInfo,
  navigation: CreationProjectNavigation | null | undefined,
  cards: CardSummary[]
): string {
  if (snapshot.subjectType === "scene") {
    const t = findSceneTitle(navigation, snapshot.subjectId);
    return t ?? "(场景已删除或未加载)";
  }
  const t = findCardTitle(cards, snapshot.subjectId);
  return t ?? "(卡片已删除或未加载)";
}

/**
 * 恢复流程编排：仅描述调用顺序，不直接执行 IPC。
 * 返回调用序列，以便测试断言顺序正确性。
 *
 * 不变量：
 * - 若 createProtection=false，返回 actions=[]（零写入）。
 * - 若保护失败，绝不在 actions 中包含 restore。
 * - 保护成功后，actions 中 restore 才出现且位于 createProtection 之后。
 */
export interface RestorePlanStep {
  kind: "create-protection" | "restore-target";
  command: object;
}

export interface RestorePlan {
  actions: RestorePlanStep[];
  protectionReason: string;
}

export function planRestoreWithProtection(input: {
  projectId: string;
  snapshot: SnapshotInfo;
  /** 测试用：是否允许创建保护（用户确认 = true，用户取消 = false） */
  userConfirmed: boolean;
  /** 测试用：保护是否会成功（仅影响错误分支说明，不改变顺序不变量） */
  protectionWillSucceed?: boolean;
}): RestorePlan {
  const snapshot = input.snapshot;
  const protectionReason = `恢复前保护：${snapshot.reason || "目标快照"}（${new Date(
    snapshot.createdAt
  ).toLocaleString("zh-CN")}）`;

  if (!input.userConfirmed) {
    // 用户取消：零写入
    return { actions: [], protectionReason };
  }

  const actions: RestorePlanStep[] = [];

  // Step 1: 先创建保护快照（无条件放入 plan；执行时若失败则后续 restore 不被调用）
  actions.push({
    kind: "create-protection",
    command: {
      type: "snapshot.create",
      projectId: input.projectId,
      subjectType: snapshot.subjectType,
      subjectId: snapshot.subjectId,
      reason: protectionReason
    }
  });

  // Step 2: 仅当保护预计成功时，restore 才出现在 plan 中；
  // 实际执行时由调用方在 protection 成功后再调用 restore。
  // 这里为了测试顺序不变量，protectionWillSucceed=true（默认）时才添加 restore。
  if (input.protectionWillSucceed !== false) {
    actions.push({
      kind: "restore-target",
      command: {
        type: "snapshot.restore",
        projectId: input.projectId,
        snapshotId: snapshot.id
      }
    });
  }

  return { actions, protectionReason };
}

/**
 * 永久删除确认对话框必须展示的字段。
 * 纯函数：只根据 TrashItem 计算显示内容，方便断言。
 */
export function buildPurgeImpact(item: TrashItem, childCount?: number): {
  entityLabel: string;
  entityName: string;
  deletedAt: string;
  childCount?: number;
  irrevocable: true;
} {
  const ENTITY_LABEL: Record<string, string> = {
    volume: "卷",
    chapter: "章",
    scene: "场景",
    card: "卡片"
  };
  return {
    entityLabel: ENTITY_LABEL[item.entity] ?? item.entity,
    entityName: item.title,
    deletedAt: new Date(item.deletedAt).toLocaleString("zh-CN"),
    childCount: typeof childCount === "number" && childCount > 0 ? childCount : undefined,
    irrevocable: true
  };
}

/**
 * 回收站条目的子节点数，仅用于展示；无法从 navigation 可靠推导时返回 undefined（不编造）。
 * 注意：回收站中的条目通常已从 navigation 移除，此函数作为保守推导入口，
 * 不保证准确性——因此 UI 层只在能明确推导时才展示 childCount。
 */
export function estimateTrashChildCount(
  _navigation: CreationProjectNavigation | null | undefined,
  _item: TrashItem
): number | undefined {
  // 严格遵守任务要求：不能推导的关系、附件影响不得编造。
  // 回收站中的卷/章在 navigation 中通常已不存在，保守返回 undefined。
  return undefined;
}

/**
 * 恢复失败的消息：保护失败 vs 恢复失败。
 * 用于断言 UI 文案区分两种失败原因。
 */
export function buildRestoreErrorMessage(phase: "protection-failed" | "restore-failed"): string {
  if (phase === "protection-failed") {
    return "无法创建恢复前保护快照，恢复已中止。当前内容未被目标快照覆盖。请稍后重试，或检查对象是否仍然存在。";
  }
  return "恢复失败。保护快照已创建并保留，当前内容未被目标快照覆盖。可从列表中选择该保护快照重新恢复。";
}
