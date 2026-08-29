import { beforeEach, describe, expect, it, vi } from "vitest";
import { createDemoProject } from "@/features/creation/demo/create-demo-project";
import * as creationService from "@/services/creation-service";
import type { CreationProjectNavigation } from "@/types/creation";

function fakeNavigation(): CreationProjectNavigation {
  return {
    project: { id: "p-demo" } as CreationProjectNavigation["project"],
    chapters: [
      {
        id: "c1",
        projectId: "p-demo",
        title: "第一章",
        sortOrder: 0,
        createdAt: "",
        updatedAt: "",
        revision: 1,
        scenes: [{ id: "s1", chapterId: "c1", title: "场景1", sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 }]
      }
    ]
  };
}

const commands: Array<Record<string, unknown>> = [];
const bodies: Array<{ sceneId: string; baseRevision: number }> = [];

vi.mock("@/services/creation-service", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/creation-service")>();
  return {
    ...actual,
    annotationCreate: vi.fn(async () => ({ ok: true })),
    runStructure: vi.fn(async (command: Record<string, unknown>) => {
      commands.push(command);
      const creates = commands.filter((cmd) => cmd.type === "scene.create" || cmd.type === "card.create");
      if (command.type === "scene.create" || command.type === "card.create") {
        return { entityId: `e${creates.length}` };
      }
      return {};
    })
  };
});

beforeEach(() => {
  commands.length = 0;
  bodies.length = 0;
  vi.mocked(creationService.runStructure).mockClear();
});

function makeActions() {
  return {
    createProject: vi.fn(async () => fakeNavigation()),
    saveSceneBody: vi.fn(async (sceneId: string, baseRevision: number) => {
      bodies.push({ sceneId, baseRevision });
      return { ok: true };
    })
  };
}

describe("createDemoProject", () => {
  it("按序编排：建场景 → 建卡（含伏笔生命周期）→ 关系 → 正文 → 任务卡", async () => {
    const projectId = await createDemoProject(makeActions());

    expect(projectId).toBe("p-demo");
    // 2 个补充场景
    const sceneCreates = commands.filter((cmd) => cmd.type === "scene.create");
    expect(sceneCreates.length).toBe(2);
    // 5 张卡：2 角色 + 1 地点 + 2 伏笔
    const cardCreates = commands.filter((cmd) => cmd.type === "card.create");
    expect(cardCreates.length).toBe(5);
    const foreshadows = cardCreates.filter((cmd) => cmd.kind === "foreshadow");
    expect(foreshadows.length).toBe(2);
    expect(foreshadows.some((cmd) => (cmd.fields as Record<string, unknown>).status === "未回收")).toBe(true);
    expect(foreshadows.some((cmd) => (cmd.fields as Record<string, unknown>).status === "已回收")).toBe(true);
    // 卡片先于任务卡（planning 引用卡片 ID）：e1/e2 是补充场景，e3 林晚、e5 图书馆
    const firstPlanningIndex = commands.findIndex((cmd) => cmd.type === "scene.updatePlanning");
    const lastCardIndex = commands.map((cmd) => cmd.type).lastIndexOf("card.create");
    expect(lastCardIndex).toBeLessThan(firstPlanningIndex);
    const firstPlanning = commands.find((cmd) => cmd.type === "scene.updatePlanning")!.planning as Record<string, unknown>;
    expect(firstPlanning.perspectiveCardId).toBe("e3");
    expect(firstPlanning.locationCardId).toBe("e5");
    expect(firstPlanning.targetWords).toBe(1200);
    // 内置关系类型
    const relation = commands.find((cmd) => cmd.type === "cardRelation.create");
    expect(relation?.relationTypeId).toBe("relation-type-character-location");
    expect(relation?.fromCardId).toBe("e3");
    // 3 次正文保存走独立通道，baseRevision 均为 1
    expect(bodies.length).toBe(3);
    expect(bodies.every((body) => body.baseRevision === 1)).toBe(true);
    // 3 次任务卡
    expect(commands.filter((cmd) => cmd.type === "scene.updatePlanning").length).toBe(3);
    // 2 条批注（伏笔锚点）：未回收 + 已回收
    expect(vi.mocked(creationService.annotationCreate).mock.calls.length).toBe(2);
    const kinds = vi.mocked(creationService.annotationCreate).mock.calls.map((call) => call[0].status);
    expect(kinds).toEqual(["open", "resolved"]);
  });

  it("createProject 失败返回 undefined，不发起任何命令", async () => {
    const actions = makeActions();
    actions.createProject.mockResolvedValue(undefined);
    const projectId = await createDemoProject(actions);
    expect(projectId).toBeUndefined();
    expect(commands.length).toBe(0);
    expect(actions.saveSceneBody).not.toHaveBeenCalled();
  });
});
