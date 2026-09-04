import type { StateCreator } from "zustand";
import type {
  CreationDocument,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSummary,
  CreationProjectTree,
  SceneBodyView,
  SceneSaveResponse
} from "@/types/creation";
import type { ProjectNavigationRequest } from "@/features/navigation/project-navigation";
import type { CardSliceState } from "./card-slice";

export interface ProjectSliceState {
  projects: CreationProjectSummary[];
  selectedId?: string;
  /** 每个项目最近一次取得的章→场景导航（不含正文；正文按场景懒读）。 */
  navigations: Record<string, CreationProjectNavigation>;
  /** 每个项目最近一次取得的卷→章→场景大纲（大纲树与卡片板共享数据）。 */
  outlines: Record<string, CreationProjectOutline>;
  /** 当前选中的场景（属于 selectedId 对应的项目）。 */
  selectedSceneId?: string;
  /** 已读取的场景正文视图缓存，键为场景 ID。 */
  sceneViews: Record<string, SceneBodyView>;
  /** 上次异常退出标记：写作台据此展示一次“已恢复已确认内容”的说明。 */
  abnormalExit: boolean;
  /** 恢复说明是否已在本会话内关闭。 */
  recoveryNoticeDismissed: boolean;
  loading: boolean;
  /** 当前项目是否已挂上 watch 订阅。 */
  watchConnected: boolean;
  /** 写作台注册的离开守卫；用于在切换桌面模块前确认脏正文已提交。 */
  leaveGuard?: () => Promise<boolean>;
  /**
   * 来自统一搜索/外部入口的项目导航请求；CreationProjectsPage 在挂载/selectedId
   * 切换后用 consumeProjectNavigation 消费一次，消费即清除，避免重复跳转。
   * 键为 projectId；同一项目只保留最后一次请求。
   */
  projectNavigationRequests: Record<string, ProjectNavigationRequest>;
  /**
   * 来自统一搜索的收件箱选中请求；InboxPage 在挂载/列表刷新后消费一次。
   * 消费即清除；目标条目不存在时 InboxPage 给出明确提示。
   */
  inboxSelectionRequest?: string;
}

export interface ProjectSliceActions {
  setProjects: (projects: CreationProjectSummary[]) => void;
  setSelectedId: (selectedId?: string) => void;
  upsertProject: (tree: CreationProjectTree) => void;
  upsertNavigation: (navigation: CreationProjectNavigation) => void;
  setNavigation: (projectId: string, navigation: CreationProjectNavigation) => void;
  setOutline: (projectId: string, outline: CreationProjectOutline) => void;
  selectScene: (sceneId: string) => void;
  setSceneView: (sceneId: string, view: SceneBodyView) => void;
  applySceneSaveResult: (result: Extract<SceneSaveResponse, { ok: true }>, body: CreationDocument) => void;
  setAbnormalExit: (abnormalExit: boolean) => void;
  dismissRecoveryNotice: () => void;
  setLoading: (loading: boolean) => void;
  setWatchConnected: (watchConnected: boolean) => void;
  setLeaveGuard: (leaveGuard?: () => Promise<boolean>) => void;
  requestProjectNavigation: (request: ProjectNavigationRequest) => void;
  consumeProjectNavigation: (projectId: string) => ProjectNavigationRequest | undefined;
  clearProjectNavigation: () => void;
  requestInboxSelection: (itemId: string) => void;
  consumeInboxSelection: () => string | undefined;
  clearInboxSelection: () => void;
}

export type ProjectSlice = ProjectSliceState & ProjectSliceActions;

function countTree(tree: CreationProjectTree): { chapterCount: number; sceneCount: number } {
  return {
    chapterCount: tree.chapters.length,
    sceneCount: tree.chapters.reduce((total, chapter) => total + chapter.scenes.length, 0)
  };
}

function firstSceneId(navigation: CreationProjectNavigation | undefined): string | undefined {
  return navigation?.chapters[0]?.scenes[0]?.id;
}

function navigationFromTree(tree: CreationProjectTree): CreationProjectNavigation {
  return {
    project: tree.project,
    chapters: tree.chapters.map((chapter) => ({
      id: chapter.id,
      projectId: chapter.projectId,
      title: chapter.title,
      sortOrder: chapter.sortOrder,
      createdAt: chapter.createdAt,
      updatedAt: chapter.updatedAt,
      revision: chapter.revision,
      scenes: chapter.scenes.map((scene) => ({
        id: scene.id,
        chapterId: scene.chapterId,
        title: scene.title,
        sortOrder: scene.sortOrder,
        createdAt: scene.createdAt,
        updatedAt: scene.updatedAt,
        revision: scene.revision
      }))
    }))
  };
}

function sceneExists(navigation: CreationProjectNavigation, sceneId: string): boolean {
  return navigation.chapters.some((chapter) => chapter.scenes.some((scene) => scene.id === sceneId));
}

export const initialProjectState: ProjectSliceState = {
  projects: [],
  selectedId: undefined,
  navigations: {},
  outlines: {},
  selectedSceneId: undefined,
  sceneViews: {},
  abnormalExit: false,
  recoveryNoticeDismissed: false,
  loading: false,
  watchConnected: false,
  leaveGuard: undefined,
  projectNavigationRequests: {},
  inboxSelectionRequest: undefined
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const createProjectSlice: StateCreator<any, [], [], ProjectSlice> = (set, get) => ({
  ...initialProjectState,
  setProjects: (projects) =>
    set((state: ProjectSliceState) => {
      // 启动进入项目首页：刷新列表不自动选中第一个项目，保留当前选择；被移除则回到首页。
      const selectedId =
        state.selectedId && projects.some((project) => project.id === state.selectedId)
          ? state.selectedId
          : undefined;
      const navigation = selectedId ? state.navigations[selectedId] : undefined;
      const selectedSceneId =
        navigation && state.selectedSceneId && sceneExists(navigation, state.selectedSceneId)
          ? state.selectedSceneId
          : firstSceneId(navigation);
      return { projects, selectedId, selectedSceneId };
    }),
  setSelectedId: (selectedId) =>
    set((state: ProjectSliceState & Partial<CardSliceState>) => {
      const resetCards =
        state.cardProjectId && state.cardProjectId !== selectedId
          ? {
              cardProjectId: undefined,
              cardTypes: [],
              relationTypes: [],
              cards: [],
              cardRelations: {},
              selectedCardId: undefined,
              cardsLoading: false
            }
          : {};
      if (!selectedId) return { selectedId: undefined, selectedSceneId: undefined, ...resetCards };
      const navigation = state.navigations[selectedId];
      return { selectedId, selectedSceneId: firstSceneId(navigation), ...resetCards };
    }),
  upsertProject: (tree) =>
    set((state: ProjectSliceState) => {
      const { chapterCount, sceneCount } = countTree(tree);
      const summary: CreationProjectSummary = {
        id: tree.project.id,
        title: tree.project.title,
        setup: tree.project.setup,
        updatedAt: tree.project.updatedAt,
        revision: tree.project.revision,
        chapterCount,
        sceneCount
      };
      const navigation = navigationFromTree(tree);
      const projects = [summary, ...state.projects.filter((project) => project.id !== summary.id)].sort((a, b) =>
        b.updatedAt.localeCompare(a.updatedAt)
      );
      return {
        projects,
        navigations: { ...state.navigations, [summary.id]: navigation },
        selectedId: summary.id,
        selectedSceneId: firstSceneId(navigation)
      };
    }),
  upsertNavigation: (navigation) =>
    set((state: ProjectSliceState) => {
      const summary: CreationProjectSummary = {
        id: navigation.project.id,
        title: navigation.project.title,
        setup: navigation.project.setup,
        updatedAt: navigation.project.updatedAt,
        revision: navigation.project.revision,
        chapterCount: navigation.chapters.length,
        sceneCount: navigation.chapters.reduce((total, chapter) => total + chapter.scenes.length, 0)
      };
      const projects = [summary, ...state.projects.filter((project) => project.id !== summary.id)].sort((a, b) =>
        b.updatedAt.localeCompare(a.updatedAt)
      );
      return {
        projects,
        navigations: { ...state.navigations, [summary.id]: navigation },
        selectedId: summary.id,
        selectedSceneId: firstSceneId(navigation)
      };
    }),
  setNavigation: (projectId, navigation) =>
    set((state: ProjectSliceState) => {
      const selectedSceneId =
        state.selectedId === projectId && state.selectedSceneId && sceneExists(navigation, state.selectedSceneId)
          ? state.selectedSceneId
          : state.selectedId === projectId
            ? firstSceneId(navigation)
            : state.selectedSceneId;
      return { navigations: { ...state.navigations, [projectId]: navigation }, selectedSceneId };
    }),
  setOutline: (projectId, outline) =>
    set((state: ProjectSliceState) => ({ outlines: { ...state.outlines, [projectId]: outline } })),
  selectScene: (selectedSceneId) => set({ selectedSceneId }),
  setSceneView: (sceneId, view) =>
    set((state: ProjectSliceState) => ({ sceneViews: { ...state.sceneViews, [sceneId]: view } })),
  applySceneSaveResult: (result, body) =>
    set((state: ProjectSliceState) => {
      const { sceneId, revision } = result.result;
      const current = state.sceneViews[sceneId];
      const sceneViews = current
        ? { ...state.sceneViews, [sceneId]: { ...current, body, revision, updatedAt: result.result.updatedAt } }
        : state.sceneViews;

      const navigations: Record<string, CreationProjectNavigation> = { ...state.navigations };
      for (const [projectId, navigation] of Object.entries(navigations)) {
        if (!navigation.chapters.some((chapter) => chapter.scenes.some((scene) => scene.id === sceneId))) continue;
        navigations[projectId] = {
          ...navigation,
          project: { ...navigation.project, updatedAt: result.result.updatedAt },
          chapters: navigation.chapters.map((chapter) =>
            chapter.scenes.some((scene) => scene.id === sceneId)
              ? {
                  ...chapter,
                  scenes: chapter.scenes.map((scene) =>
                    scene.id === sceneId ? { ...scene, revision, updatedAt: result.result.updatedAt } : scene
                  )
                }
              : chapter
          )
        };
        break;
      }

      const projects = state.projects
        .map((project) =>
          project.id === result.result.projectId ? { ...project, updatedAt: result.result.updatedAt } : project
        )
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
      return { sceneViews, navigations, projects };
    }),
  setAbnormalExit: (abnormalExit) =>
    set({ abnormalExit, recoveryNoticeDismissed: abnormalExit ? false : undefined }),
  dismissRecoveryNotice: () => set({ recoveryNoticeDismissed: true }),
  setLoading: (loading) => set({ loading }),
  setWatchConnected: (watchConnected) => set({ watchConnected }),
  setLeaveGuard: (leaveGuard) => set({ leaveGuard }),
  requestProjectNavigation: (request) =>
    set((state: ProjectSliceState) => ({
      projectNavigationRequests: {
        ...state.projectNavigationRequests,
        [request.target.projectId]: request
      }
    })),
  consumeProjectNavigation: (projectId) => {
    const req = get().projectNavigationRequests[projectId];
    if (!req) return undefined;
    set((state: ProjectSliceState) => {
      const { [projectId]: _consumed, ...rest } = state.projectNavigationRequests;
      void _consumed;
      return { projectNavigationRequests: rest };
    });
    return req;
  },
  clearProjectNavigation: () => set({ projectNavigationRequests: {} }),
  requestInboxSelection: (itemId) => set({ inboxSelectionRequest: itemId }),
  consumeInboxSelection: () => {
    const id = get().inboxSelectionRequest;
    if (!id) return undefined;
    set({ inboxSelectionRequest: undefined });
    return id;
  },
  clearInboxSelection: () => set({ inboxSelectionRequest: undefined })
});
