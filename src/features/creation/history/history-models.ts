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
  if (snapshot.subjectType === "card") {
    const t = findCardTitle(cards, snapshot.subjectId);
    return t ?? "(卡片已删除或未加载)";
  }
  // 章/卷通常已不在导航树中，列表回退到快照名称；权威名称由预览接口给出。
  return snapshot.subjectType === "chapter"
    ? snapshot.reason || "(章)"
    : snapshot.reason || "(卷)";
}

/** 原子恢复确认结果：成功时回传保护快照 ID，供 UI 展示。 */
export interface RestoreSnapshotConfirmResult {
  ok: boolean;
  error?: string | null;
  /** 后端在恢复前自动创建的保护快照 ID，成功时必须回传以便 UI 展示。 */
  protectionSnapshotId?: string;
}

/** 原子恢复命令中用于命名恢复前保护快照的可读原因。 */
export function buildSnapshotProtectionReason(snapshot: SnapshotInfo): string {
  return `恢复前保护：${snapshot.reason || "目标快照"}（${new Date(
    snapshot.createdAt
  ).toLocaleString("zh-CN")}）`;
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

/**
 * 快照分类（渲染端）：必须与主进程 classifySnapshotMeta 保持一致（需求 1）。
 * 只依据系统受控字段（id 前缀 + subjectType），绝不读取 reason 文本。
 * 注意：运行时的 subjectType 是快照表的原始 subject_type（如 "scene-autosave" /
 * "structure-operation"），可能超出 SnapshotSubjectType 的收窄联合；此处按字符串处理。
 */
export type SnapshotCategory = "auto" | "milestone" | "protected";

export function classifySnapshotCategory(snapshot: SnapshotInfo): SnapshotCategory {
  const subjectType = snapshot.subjectType as string;
  if (snapshot.id.startsWith("protective") || subjectType === "structure-operation") {
    return "protected";
  }
  if (subjectType === "scene-autosave") {
    return "auto";
  }
  return "milestone";
}

/** 分类徽标文案。 */
export const SNAPSHOT_CATEGORY_LABEL: Record<SnapshotCategory, string> = {
  auto: "自动快照",
  milestone: "里程碑",
  protected: "保护快照"
};

/** 分类对应的留存规则说明，供历史 UI 展示（需求 10：展示分层留存规则）。 */
export const SNAPSHOT_RETENTION_HINT: Record<SnapshotCategory, string> = {
  auto: "自动快照按分层留存：创建后 24 小时内全部保留；超过 24 小时每 UTC 日保留最新一份；超过 30 天每 UTC 周保留最新一份。",
  milestone: "命名里程碑永久保留。",
  protected: "保护快照（重组 / 替换 / 恢复前创建）永久保留，不会被自动清理删除。"
};
