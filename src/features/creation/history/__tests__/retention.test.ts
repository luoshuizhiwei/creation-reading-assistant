import { describe, expect, it } from "vitest";
import type { SnapshotInfo } from "@/types/creation";
import {
  classifySnapshotCategory,
  SNAPSHOT_CATEGORY_LABEL,
  SNAPSHOT_RETENTION_HINT
} from "@/features/creation/history/history-models";

function snap(partial: Partial<SnapshotInfo> & Pick<SnapshotInfo, "id" | "subjectType">): SnapshotInfo {
  return {
    projectId: "p1",
    subjectId: "s1",
    reason: "",
    createdAt: "2026-08-01T00:00:00.000Z",
    ...partial
  };
}

describe("classifySnapshotCategory（渲染端，须与主进程 classifySnapshotMeta 一致）", () => {
  it("protective 前缀的 id 归类为保护快照", () => {
    expect(classifySnapshotCategory(snap({ id: "protective-abc", subjectType: "scene" }))).toBe("protected");
  });

  it("subjectType 为 structure-operation 归类为保护快照", () => {
    expect(classifySnapshotCategory(snap({ id: "snap-x", subjectType: "structure-operation" }))).toBe("protected");
  });

  it("subjectType 为 scene-autosave 归类为自动快照", () => {
    expect(classifySnapshotCategory(snap({ id: "snap-y", subjectType: "scene-autosave" }))).toBe("auto");
  });

  it("普通场景/卡片快照（用户命名里程碑）归类为里程碑", () => {
    expect(classifySnapshotCategory(snap({ id: "snap-1", subjectType: "scene" }))).toBe("milestone");
    expect(classifySnapshotCategory(snap({ id: "snap-2", subjectType: "card" }))).toBe("milestone");
    expect(classifySnapshotCategory(snap({ id: "snap-3", subjectType: "chapter" }))).toBe("milestone");
  });

  it("绝不依据 reason 文本推断类型（需求 1）", () => {
    // reason 含“自动”也不应变成 auto；reason 含“里程碑”也不影响 auto。
    const looksLikeAuto = snap({ id: "snap-1", subjectType: "scene", reason: "自动写稿" });
    expect(classifySnapshotCategory(looksLikeAuto)).toBe("milestone");

    const autoWithMilestoneReason = snap({ id: "snap-a", subjectType: "scene-autosave", reason: "这是一个里程碑" });
    expect(classifySnapshotCategory(autoWithMilestoneReason)).toBe("auto");
  });
});

describe("SNAPSHOT_CATEGORY_LABEL", () => {
  it("三种分类均有中文徽标文案", () => {
    expect(SNAPSHOT_CATEGORY_LABEL.auto).toBe("自动快照");
    expect(SNAPSHOT_CATEGORY_LABEL.milestone).toBe("里程碑");
    expect(SNAPSHOT_CATEGORY_LABEL.protected).toBe("保护快照");
  });
});

describe("SNAPSHOT_RETENTION_HINT（需求 10：展示分层留存规则）", () => {
  it("自动快照说明含 24 小时 / UTC 日 / UTC 周分层", () => {
    const hint = SNAPSHOT_RETENTION_HINT.auto;
    expect(hint).toContain("24 小时");
    expect(hint).toContain("UTC 日");
    expect(hint).toContain("UTC 周");
  });

  it("里程碑永久保留", () => {
    expect(SNAPSHOT_RETENTION_HINT.milestone).toContain("永久");
  });

  it("保护快照永久保留且不被自动清理删除", () => {
    const hint = SNAPSHOT_RETENTION_HINT.protected;
    expect(hint).toContain("永久");
    expect(hint).toContain("不会被自动清理");
  });
});
