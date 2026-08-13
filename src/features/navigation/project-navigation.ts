import type { ProjectView } from "@/features/navigation/project-nav";

/**
 * 统一搜索/外部入口向项目页发起的结构化导航请求。
 *
 * 外部（如 SearchPanel.openResult）把意图落到这里，CreationProjectsPage
 * 在 selectedId 切换/重新挂载后用 consumeProjectNavigation 消费一次，
 * 然后清除请求，避免重渲染重复跳转。
 *
 * chapter / scene / card 都需要 projectId；scene 还需要 sceneId；
 * chapter 需要 chapterId（由项目页映射到该章节的首个场景）；
 * card 需要 cardId（由项目页选中卡片并切换到 cards 视图）。
 */
export interface ProjectNavigationTarget {
  projectId: string;
  view: ProjectView;
  chapterId?: string;
  sceneId?: string;
  cardId?: string;
}

export interface ProjectNavigationRequest {
  target: ProjectNavigationTarget;
  /** 创建时间戳，便于调试；不参与去重逻辑。 */
  createdAt: number;
}
