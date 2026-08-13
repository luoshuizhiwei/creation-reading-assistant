import { beforeEach, describe, expect, it } from "vitest";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CardSummary,
  CreationDocument,
  CreationProjectNavigation,
  CreationProjectSummary,
  CreationProjectTree,
  SceneBodyView
} from "@/types/creation";

const emptyDoc: CreationDocument = { type: "doc", content: [] };

function makeTree(overrides: Partial<CreationProjectTree> = {}): CreationProjectTree {
  return {
    project: {
      id: "project-1",
      title: "测试项目",
      setup: { template: "long-form", description: "用于验收的中性测试项目", weeklyUpdateDays: [1, 3], chapterWorkflow: ["规划", "写作中", "定稿"] },
      createdAt: "2026-08-09T10:00:00.000Z",
      updatedAt: "2026-08-09T10:00:00.000Z",
      revision: 1
    },
    chapters: [
      {
        id: "chapter-1",
        projectId: "project-1",
        title: "第一章",
        sortOrder: 0,
        createdAt: "2026-08-09T10:00:00.000Z",
        updatedAt: "2026-08-09T10:00:00.000Z",
        revision: 1,
        scenes: [
          { id: "scene-1", chapterId: "chapter-1", title: "开场", sortOrder: 0, body: emptyDoc, createdAt: "2026-08-09T10:00:00.000Z", updatedAt: "2026-08-09T10:00:00.000Z", revision: 1 },
          { id: "scene-2", chapterId: "chapter-1", title: "冲突", sortOrder: 1, body: emptyDoc, createdAt: "2026-08-09T10:00:00.000Z", updatedAt: "2026-08-09T10:00:00.000Z", revision: 1 }
        ]
      },
      {
        id: "chapter-2",
        projectId: "project-1",
        title: "第二章",
        sortOrder: 1,
        createdAt: "2026-08-09T10:00:00.000Z",
        updatedAt: "2026-08-09T10:00:00.000Z",
        revision: 1,
        scenes: [{ id: "scene-3", chapterId: "chapter-2", title: "转折", sortOrder: 0, body: emptyDoc, createdAt: "2026-08-09T10:00:00.000Z", updatedAt: "2026-08-09T10:00:00.000Z", revision: 1 }]
      }
    ],
    ...overrides
  };
}

function makeNavigation(overrides: Partial<CreationProjectNavigation> = {}): CreationProjectNavigation {
  return {
    project: {
      id: "project-1",
      title: "测试项目",
      setup: { template: "long-form", description: "用于验收的中性测试项目", weeklyUpdateDays: [1, 3], chapterWorkflow: ["规划", "写作中", "定稿"] },
      createdAt: "2026-08-09T10:00:00.000Z",
      updatedAt: "2026-08-09T10:00:00.000Z",
      revision: 1
    },
    chapters: [
      {
        id: "chapter-1",
        projectId: "project-1",
        title: "第一章",
        sortOrder: 0,
        revision: 1,
        scenes: [
          { id: "scene-1", chapterId: "chapter-1", title: "开场", sortOrder: 0, revision: 1 },
          { id: "scene-2", chapterId: "chapter-1", title: "冲突", sortOrder: 1, revision: 1 }
        ]
      },
      {
        id: "chapter-2",
        projectId: "project-1",
        title: "第二章",
        sortOrder: 1,
        revision: 1,
        scenes: [{ id: "scene-3", chapterId: "chapter-2", title: "转折", sortOrder: 0, revision: 1 }]
      }
    ],
    ...overrides
  };
}

function makeSceneView(overrides: Partial<SceneBodyView> = {}): SceneBodyView {
  return {
    sceneId: "scene-1",
    revision: 1,
    body: emptyDoc,
    updatedAt: "2026-08-09T10:00:00.000Z",
    ...overrides
  };
}

function makeSummary(overrides: Partial<CreationProjectSummary> = {}): CreationProjectSummary {
  return {
    id: "existing-1",
    title: "已有项目",
    setup: { template: "serial", weeklyUpdateDays: [], chapterWorkflow: ["规划", "定稿"] },
    updatedAt: "2026-08-08T10:00:00.000Z",
    revision: 3,
    chapterCount: 4,
    sceneCount: 9,
    ...overrides
  };
}

function makeCard(projectId: string, id = `card-${projectId}`): CardSummary {
  return {
    id,
    projectId,
    kind: "character",
    title: `卡片 ${projectId}`,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-08-09T10:00:00.000Z",
    updatedAt: "2026-08-09T10:00:00.000Z",
    revision: 1
  };
}

beforeEach(() => {
  useCreationStore.setState({
    projects: [],
    selectedId: undefined,
    navigations: {},
    selectedSceneId: undefined,
    sceneViews: {},
    abnormalExit: false,
    recoveryNoticeDismissed: false,
    loading: false,
    watchConnected: false,
    leaveGuard: undefined,
    cardProjectId: undefined,
    cardTypes: [],
    relationTypes: [],
    cards: [],
    cardRelations: {},
    selectedCardId: undefined,
    cardsLoading: false
  });
});

describe("creation store", () => {
  it("刷新项目列表时不自动选中第一个项目，进入项目首页", () => {
    useCreationStore.getState().setProjects([makeSummary({ id: "a" }), makeSummary({ id: "b" })]);
    expect(useCreationStore.getState().selectedId).toBeUndefined();
  });

  it("刷新后保留仍存在的当前选择", () => {
    useCreationStore.getState().setProjects([makeSummary({ id: "a" }), makeSummary({ id: "b" })]);
    useCreationStore.getState().setSelectedId("b");
    useCreationStore.getState().setProjects([makeSummary({ id: "b" }), makeSummary({ id: "c" })]);
    expect(useCreationStore.getState().selectedId).toBe("b");
  });

  it("clears the selection when the list becomes empty", () => {
    useCreationStore.getState().setProjects([makeSummary()]);
    useCreationStore.getState().setProjects([]);
    expect(useCreationStore.getState().selectedId).toBeUndefined();
  });

  it("upserts a project with counts computed from the tree, selects it and defaults to the first scene", () => {
    useCreationStore.getState().upsertProject(makeTree());
    const state = useCreationStore.getState();
    expect(state.projects).toHaveLength(1);
    expect(state.projects[0]).toMatchObject({
      id: "project-1",
      title: "测试项目",
      chapterCount: 2,
      sceneCount: 3,
      revision: 1
    });
    expect(state.projects[0].setup.template).toBe("long-form");
    expect(state.selectedId).toBe("project-1");
    expect(state.navigations["project-1"]?.chapters).toHaveLength(2);
    expect(state.selectedSceneId).toBe("scene-1");
  });

  it("sorts projects by updatedAt descending after upsert", () => {
    useCreationStore.getState().setProjects([makeSummary({ id: "existing-1", updatedAt: "2026-08-09T09:00:00.000Z" })]);
    useCreationStore.getState().upsertProject(
      makeTree({ project: { id: "project-new", title: "新项目", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "2026-08-09T11:00:00.000Z", updatedAt: "2026-08-09T11:00:00.000Z", revision: 1 } })
    );
    expect(useCreationStore.getState().projects.map((project) => project.id)).toEqual(["project-new", "existing-1"]);
  });

  it("replaces an existing summary with the same id without duplicating", () => {
    useCreationStore.getState().upsertProject(makeTree());
    useCreationStore.getState().upsertProject(
      makeTree({ project: { id: "project-1", title: "测试项目（修订）", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "2026-08-09T12:00:00.000Z", updatedAt: "2026-08-09T12:00:00.000Z", revision: 2 } })
    );
    const state = useCreationStore.getState();
    expect(state.projects).toHaveLength(1);
    expect(state.projects[0].title).toBe("测试项目（修订）");
    expect(state.projects[0].revision).toBe(2);
  });

  it("stores a navigation and defaults to the first scene when nothing is selected", () => {
    useCreationStore.getState().setSelectedId("project-1");
    useCreationStore.getState().setNavigation("project-1", makeNavigation());
    expect(useCreationStore.getState().navigations["project-1"]?.chapters).toHaveLength(2);
    expect(useCreationStore.getState().selectedSceneId).toBe("scene-1");
  });

  it("keeps the selected scene when the refreshed navigation still contains it", () => {
    useCreationStore.getState().setSelectedId("project-1");
    useCreationStore.getState().setNavigation("project-1", makeNavigation());
    useCreationStore.getState().selectScene("scene-2");
    useCreationStore.getState().setNavigation("project-1", makeNavigation());
    expect(useCreationStore.getState().selectedSceneId).toBe("scene-2");
  });

  it("resets to the first scene when the selected scene disappears from navigation", () => {
    useCreationStore.getState().setSelectedId("project-1");
    useCreationStore.getState().setNavigation("project-1", makeNavigation());
    useCreationStore.getState().selectScene("scene-3");
    useCreationStore.getState().setNavigation(
      "project-1",
      makeNavigation({ chapters: makeNavigation().chapters.slice(0, 1) })
    );
    expect(useCreationStore.getState().selectedSceneId).toBe("scene-1");
  });

  it("selects a scene explicitly", () => {
    useCreationStore.getState().setSelectedId("project-1");
    useCreationStore.getState().selectScene("scene-2");
    expect(useCreationStore.getState().selectedSceneId).toBe("scene-2");
  });

  it("caches a scene body view", () => {
    const view = makeSceneView();
    useCreationStore.getState().setSceneView("scene-1", view);
    expect(useCreationStore.getState().sceneViews["scene-1"]).toBe(view);
  });

  it("syncs scene revision and navigation metadata after a successful save", () => {
    useCreationStore.getState().setProjects([makeSummary()]);
    useCreationStore.getState().setSelectedId("existing-1");
    useCreationStore.getState().setNavigation(
      "existing-1",
      makeNavigation({
        project: {
          id: "existing-1",
          title: "已有项目",
          setup: { template: "serial", weeklyUpdateDays: [], chapterWorkflow: ["规划", "定稿"] },
          createdAt: "2026-08-08T10:00:00.000Z",
          updatedAt: "2026-08-08T10:00:00.000Z",
          revision: 3
        }
      })
    );
    useCreationStore.getState().setSceneView("scene-1", makeSceneView({ revision: 1 }));

    useCreationStore.getState().applySceneSaveResult({
      ok: true,
      result: {
        commandType: "scene.updateBody",
        sequence: 1,
        projectId: "existing-1",
        sceneId: "scene-1",
        revision: 4,
        updatedAt: "2026-08-09T12:00:00.000Z"
      }
    }, emptyDoc);

    const state = useCreationStore.getState();
    expect(state.sceneViews["scene-1"]?.revision).toBe(4);
    const navScene = state.navigations["existing-1"]?.chapters[0]?.scenes.find((scene) => scene.id === "scene-1");
    expect(navScene?.revision).toBe(4);
    expect(state.navigations["existing-1"]?.project.updatedAt).toBe("2026-08-09T12:00:00.000Z");
    expect(state.projects[0]?.updatedAt).toBe("2026-08-09T12:00:00.000Z");
  });

  it("switches the selected scene to the first scene of the newly selected project", () => {
    useCreationStore.getState().setProjects([makeSummary({ id: "a" }), makeSummary({ id: "b" })]);
    useCreationStore.getState().setSelectedId("a");
    useCreationStore.getState().setNavigation(
      "a",
      makeNavigation({ project: { id: "a", title: "A", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "2026-08-09T10:00:00.000Z", updatedAt: "2026-08-09T10:00:00.000Z", revision: 1 } })
    );
    useCreationStore.getState().selectScene("scene-3");
    useCreationStore.getState().setSelectedId("b");
    expect(useCreationStore.getState().selectedId).toBe("b");
    expect(useCreationStore.getState().selectedSceneId).toBeUndefined();
    useCreationStore.getState().setNavigation(
      "b",
      makeNavigation({ project: { id: "b", title: "B", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "2026-08-09T10:00:00.000Z", updatedAt: "2026-08-09T10:00:00.000Z", revision: 1 } })
    );
    expect(useCreationStore.getState().selectedSceneId).toBe("scene-1");
  });

  it("tracks abnormal exit and dismisses the recovery notice once", () => {
    useCreationStore.getState().setAbnormalExit(true);
    expect(useCreationStore.getState().abnormalExit).toBe(true);
    expect(useCreationStore.getState().recoveryNoticeDismissed).toBe(false);
    useCreationStore.getState().dismissRecoveryNotice();
    expect(useCreationStore.getState().recoveryNoticeDismissed).toBe(true);
  });

  it("切换卡片项目时立即清空旧项目卡片与选择", () => {
    useCreationStore.getState().activateCardProject("a");
    useCreationStore.getState().setCards("a", [makeCard("a")]);
    useCreationStore.getState().selectCard("card-a");

    useCreationStore.getState().activateCardProject("b");

    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [],
      selectedCardId: undefined,
      cardRelations: {}
    });
  });

  it("拒绝迟到的旧项目卡片响应写入当前项目", () => {
    useCreationStore.getState().activateCardProject("a");
    useCreationStore.getState().activateCardProject("b");
    useCreationStore.getState().setCards("a", [makeCard("a")]);

    expect(useCreationStore.getState().cardProjectId).toBe("b");
    expect(useCreationStore.getState().cards).toEqual([]);
  });

  it("setSelectedId 切换项目时立即清空卡片类型/关系类型/选中/加载标记", () => {
    useCreationStore.getState().setProjects([makeSummary({ id: "a" }), makeSummary({ id: "b" })]);
    useCreationStore.getState().setSelectedId("a");
    useCreationStore.getState().activateCardProject("a");
    useCreationStore.getState().setCardTypes("a", [{ id: "t1", projectId: null, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 }]);
    useCreationStore.getState().setRelationTypes("a", []);
    useCreationStore.getState().setCards("a", [makeCard("a")]);
    useCreationStore.getState().selectCard("card-a");
    useCreationStore.getState().setCardsLoading("a", true);

    useCreationStore.getState().setSelectedId("b");

    expect(useCreationStore.getState()).toMatchObject({
      selectedId: "b",
      cardProjectId: undefined,
      cards: [],
      cardTypes: [],
      relationTypes: [],
      selectedCardId: undefined,
      cardRelations: {},
      cardsLoading: false
    });
  });

  it("setSelectedId 清空到项目首页时同样清空卡片作用域", () => {
    useCreationStore.getState().activateCardProject("a");
    useCreationStore.getState().setCards("a", [makeCard("a")]);
    useCreationStore.getState().selectCard("card-a");

    useCreationStore.getState().setSelectedId(undefined);

    expect(useCreationStore.getState()).toMatchObject({
      selectedId: undefined,
      cards: [],
      selectedCardId: undefined,
      cardsLoading: false
    });
  });
});
