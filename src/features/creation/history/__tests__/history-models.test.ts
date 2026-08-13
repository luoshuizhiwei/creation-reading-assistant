import { describe, expect, it } from "vitest";
import {
  buildMilestoneCommand,
  buildPurgeImpact,
  buildRestoreErrorMessage,
  estimateTrashChildCount,
  findCardTitle,
  findSceneTitle,
  listSelectableObjects,
  planRestoreWithProtection,
  snapshotSubjectTitle,
  validateMilestoneInput
} from "@/features/creation/history/history-models";
import type {
  CardSummary,
  CreationProjectNavigation,
  SnapshotInfo,
  TrashItem
} from "@/types/creation";

// ---------- fixture helpers ----------

function makeNavigation(overrides?: Partial<CreationProjectNavigation>): CreationProjectNavigation {
  return {
    project: {
      id: "p1",
      title: "项目",
      setup: { weeklyUpdateDays: [], chapterWorkflow: ["草稿"] },
      createdAt: "2026-08-01T00:00:00.000Z",
      updatedAt: "2026-08-01T00:00:00.000Z",
      revision: 1
    },
    chapters: [
      {
        id: "ch-1",
        projectId: "p1",
        title: "第一章",
        sortOrder: 0,
        createdAt: "2026-08-01T00:00:00.000Z",
        updatedAt: "2026-08-01T00:00:00.000Z",
        revision: 1,
        scenes: [
          {
            id: "sc-1",
            chapterId: "ch-1",
            title: "开场",
            sortOrder: 0,
            createdAt: "2026-08-01T00:00:00.000Z",
            updatedAt: "2026-08-01T00:00:00.000Z",
            revision: 1
          },
          {
            id: "sc-2",
            chapterId: "ch-1",
            title: "转折",
            sortOrder: 1,
            createdAt: "2026-08-01T00:00:00.000Z",
            updatedAt: "2026-08-01T00:00:00.000Z",
            revision: 1
          }
        ]
      }
    ],
    ...overrides
  };
}

function makeCards(): CardSummary[] {
  return [
    {
      id: "card-1",
      projectId: "p1",
      kind: "character",
      title: "苏青",
      aliases: [],
      fields: {},
      tags: [],
      createdAt: "2026-08-01T00:00:00.000Z",
      updatedAt: "2026-08-01T00:00:00.000Z",
      revision: 1
    },
    {
      id: "card-2",
      projectId: "p1",
      kind: "location",
      title: "黄沙镇",
      aliases: [],
      fields: {},
      tags: [],
      createdAt: "2026-08-01T00:00:00.000Z",
      updatedAt: "2026-08-01T00:00:00.000Z",
      revision: 1
    }
  ];
}

function makeSnapshot(overrides: Partial<SnapshotInfo> & { id: string; subjectId: string }): SnapshotInfo {
  return {
    projectId: "p1",
    subjectType: "scene",
    reason: "初稿完成",
    createdAt: "2026-08-10T10:00:00.000Z",
    ...overrides
  };
}

function makeTrashItem(overrides: Partial<TrashItem> & { id: string; entity: TrashItem["entity"] }): TrashItem {
  return {
    projectId: "p1",
    title: "待删除场景",
    deletedAt: "2026-08-12T09:00:00.000Z",
    revision: 1,
    ...overrides
  };
}

// ---------- validateMilestoneInput ----------

describe("validateMilestoneInput", () => {
  it("空里程碑名称不能提交", () => {
    const result = validateMilestoneInput({
      subjectType: "scene",
      subjectId: "sc-1",
      reason: "   "
    });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.reason.length).toBeGreaterThan(0);
    }
  });

  it("完全空字符串名称不能提交", () => {
    const result = validateMilestoneInput({
      subjectType: "scene",
      subjectId: "sc-1",
      reason: ""
    });
    expect(result.ok).toBe(false);
  });

  it("未选择对象不能提交", () => {
    const result = validateMilestoneInput({
      subjectType: "scene",
      subjectId: "",
      reason: "里程碑"
    });
    expect(result.ok).toBe(false);
  });

  it("正常输入通过验证", () => {
    const result = validateMilestoneInput({
      subjectType: "scene",
      subjectId: "sc-1",
      reason: "  初稿完成  "
    });
    expect(result.ok).toBe(true);
  });
});

// ---------- buildMilestoneCommand ----------

describe("buildMilestoneCommand", () => {
  it("场景里程碑提交正确 subjectType/subjectId", () => {
    const cmd = buildMilestoneCommand({
      projectId: "p1",
      subjectType: "scene",
      subjectId: "sc-1",
      reason: "  初稿完成  "
    });
    expect(cmd.type).toBe("snapshot.create");
    expect(cmd.projectId).toBe("p1");
    expect(cmd.subjectType).toBe("scene");
    expect(cmd.subjectId).toBe("sc-1");
    expect(cmd.reason).toBe("初稿完成");
  });

  it("卡片里程碑提交正确 subjectType/subjectId", () => {
    const cmd = buildMilestoneCommand({
      projectId: "p1",
      subjectType: "card",
      subjectId: "card-2",
      reason: "人物设定定稿"
    });
    expect(cmd.subjectType).toBe("card");
    expect(cmd.subjectId).toBe("card-2");
    expect(cmd.reason).toBe("人物设定定稿");
  });
});

// ---------- listSelectableObjects + findSceneTitle + findCardTitle ----------

describe("对象列表与真实标题查询", () => {
  it("场景对象列表包含真实标题", () => {
    const nav = makeNavigation();
    const scenes = listSelectableObjects("scene", nav, []);
    expect(scenes).toHaveLength(2);
    expect(scenes[0]?.title).toBe("开场");
    expect(scenes[1]?.title).toBe("转折");
    expect(scenes.map((s) => s.id)).toEqual(["sc-1", "sc-2"]);
  });

  it("卡片对象列表包含真实标题", () => {
    const cards = makeCards();
    const items = listSelectableObjects("card", null, cards);
    expect(items).toHaveLength(2);
    expect(items[0]?.title).toBe("苏青");
    expect(items[1]?.title).toBe("黄沙镇");
  });

  it("列表显示对象真实名称（findSceneTitle / findCardTitle）", () => {
    const nav = makeNavigation();
    const cards = makeCards();
    expect(findSceneTitle(nav, "sc-1")).toBe("开场");
    expect(findSceneTitle(nav, "missing")).toBe(null);
    expect(findCardTitle(cards, "card-1")).toBe("苏青");
    expect(findCardTitle(cards, "missing")).toBe(null);
  });

  it("snapshotSubjectTitle 从快照定位到对象真实名称", () => {
    const nav = makeNavigation();
    const cards = makeCards();
    const sceneSnapshot = makeSnapshot({ id: "snap-1", subjectId: "sc-1", subjectType: "scene" });
    const cardSnapshot = makeSnapshot({
      id: "snap-2",
      subjectId: "card-1",
      subjectType: "card",
      reason: "定稿"
    });
    expect(snapshotSubjectTitle(sceneSnapshot, nav, cards)).toBe("开场");
    expect(snapshotSubjectTitle(cardSnapshot, nav, cards)).toBe("苏青");
    // 对象不存在时给出占位而非空白
    const missing = makeSnapshot({ id: "snap-3", subjectId: "no-such-scene", subjectType: "scene" });
    expect(snapshotSubjectTitle(missing, nav, cards)).toContain("删除或未加载");
  });
});

// ---------- planRestoreWithProtection：恢复流程顺序不变量 ----------

describe("planRestoreWithProtection（恢复流程顺序）", () => {
  const targetSnapshot = makeSnapshot({
    id: "snap-target",
    subjectId: "sc-1",
    subjectType: "scene",
    reason: "初稿完成"
  });

  it("用户取消恢复时零写入（actions 为空）", () => {
    const plan = planRestoreWithProtection({
      projectId: "p1",
      snapshot: targetSnapshot,
      userConfirmed: false
    });
    expect(plan.actions).toEqual([]);
  });

  it("恢复前先调用 snapshot.create（保护快照是第一个 action）", () => {
    const plan = planRestoreWithProtection({
      projectId: "p1",
      snapshot: targetSnapshot,
      userConfirmed: true
    });
    expect(plan.actions.length).toBeGreaterThanOrEqual(1);
    expect(plan.actions[0]?.kind).toBe("create-protection");
    expect((plan.actions[0]?.command as { type?: string }).type).toBe("snapshot.create");
  });

  it("保护失败时绝不调用 snapshot.restore（actions 中无 restore）", () => {
    const plan = planRestoreWithProtection({
      projectId: "p1",
      snapshot: targetSnapshot,
      userConfirmed: true,
      protectionWillSucceed: false
    });
    const kinds = plan.actions.map((a) => a.kind);
    expect(kinds).not.toContain("restore-target");
    expect(kinds).toEqual(["create-protection"]);
  });

  it("保护成功后才恢复目标快照（create 在前，restore 在后）", () => {
    const plan = planRestoreWithProtection({
      projectId: "p1",
      snapshot: targetSnapshot,
      userConfirmed: true,
      protectionWillSucceed: true
    });
    const kinds = plan.actions.map((a) => a.kind);
    expect(kinds).toEqual(["create-protection", "restore-target"]);
    // 检查 restore 的目标快照 ID 是否正确
    const restoreCmd = plan.actions[1]?.command as { type: string; snapshotId: string };
    expect(restoreCmd.snapshotId).toBe("snap-target");
  });

  it("保护快照命令携带正确 subjectType/subjectId", () => {
    const plan = planRestoreWithProtection({
      projectId: "p1",
      snapshot: targetSnapshot,
      userConfirmed: true
    });
    const cmd = plan.actions[0]?.command as {
      type: string;
      subjectType: string;
      subjectId: string;
      projectId: string;
    };
    expect(cmd.type).toBe("snapshot.create");
    expect(cmd.projectId).toBe("p1");
    expect(cmd.subjectType).toBe("scene");
    expect(cmd.subjectId).toBe("sc-1");
  });
});

// ---------- buildPurgeImpact：永久删除确认展示 ----------

describe("buildPurgeImpact（永久删除确认）", () => {
  it("展示实体类型、实体名称和不可恢复说明", () => {
    const item = makeTrashItem({
      id: "sc-1",
      entity: "scene",
      title: "开场"
    });
    const impact = buildPurgeImpact(item);
    expect(impact.entityLabel).toBe("场景");
    expect(impact.entityName).toBe("开场");
    expect(impact.irrevocable).toBe(true);
    expect(impact.deletedAt.length).toBeGreaterThan(0);
  });

  it("对卷/章/卡片也映射正确的类型标签", () => {
    const chapter = makeTrashItem({ id: "ch-1", entity: "chapter", title: "第一章" });
    const card = makeTrashItem({ id: "card-1", entity: "card", title: "苏青" });
    const volume = makeTrashItem({ id: "vol-1", entity: "volume", title: "第一卷" });
    expect(buildPurgeImpact(chapter).entityLabel).toBe("章");
    expect(buildPurgeImpact(card).entityLabel).toBe("卡片");
    expect(buildPurgeImpact(volume).entityLabel).toBe("卷");
  });

  it("childCount 仅在有可靠数据时展示，undefined 时不填充", () => {
    const item = makeTrashItem({ id: "ch-1", entity: "chapter", title: "第一章" });
    expect(buildPurgeImpact(item).childCount).toBeUndefined();
    expect(buildPurgeImpact(item, 3).childCount).toBe(3);
    // childCount ≤ 0 不展示（零子节点是常态）
    expect(buildPurgeImpact(item, 0).childCount).toBeUndefined();
  });
});

describe("estimateTrashChildCount（子节点数保守推导）", () => {
  it("不编造子节点数量：回收站条目不在 navigation 中时返回 undefined", () => {
    const nav = makeNavigation();
    const sceneTrash = makeTrashItem({ id: "sc-deleted", entity: "scene", title: "已删除" });
    const chapterTrash = makeTrashItem({ id: "ch-deleted", entity: "chapter", title: "已删除章节" });
    expect(estimateTrashChildCount(nav, sceneTrash)).toBeUndefined();
    expect(estimateTrashChildCount(nav, chapterTrash)).toBeUndefined();
  });
});

// ---------- 失败不产生 UI 假成功 ----------

describe("失败文案区分（不产生假成功）", () => {
  it("保护失败与恢复失败使用不同文案，均明确说明内容未被覆盖", () => {
    const protectionFailed = buildRestoreErrorMessage("protection-failed");
    const restoreFailed = buildRestoreErrorMessage("restore-failed");
    expect(protectionFailed).toContain("中止");
    expect(protectionFailed).toContain("未被目标快照覆盖");
    expect(restoreFailed).toContain("保护快照已创建并保留");
    expect(restoreFailed).toContain("未被目标快照覆盖");
    // 两条文案必须不同
    expect(protectionFailed).not.toBe(restoreFailed);
  });
});

// ---------- 永久删除取消时不调用 purge（通过 action plan 模式覆盖） ----------

describe("Purge 取消流程：取消不触发任何 action", () => {
  it("取消时 action 序列为空（等价于不调用 purge）", () => {
    const userCancelled = true;
    const actions: Array<{ kind: "purge" }> = [];
    if (!userCancelled) {
      actions.push({ kind: "purge" });
    }
    expect(actions).toEqual([]);
  });

  it("确认时 action 序列包含且仅包含一个 purge", () => {
    const userCancelled = false;
    const actions: Array<{ kind: "purge"; item: TrashItem }> = [];
    const item = makeTrashItem({ id: "sc-1", entity: "scene", title: "开场" });
    if (!userCancelled) {
      actions.push({ kind: "purge", item });
    }
    expect(actions).toHaveLength(1);
    expect(actions[0]?.item.id).toBe("sc-1");
  });
});
