import { useProjectActions } from "./creation/useProjectActions";
import { useOutlineActions } from "./creation/useOutlineActions";
import { useCardActions } from "./creation/useCardActions";
import { useInboxActions } from "./creation/useInboxActions";
import { useCreationOtherActions } from "./creation/useCreationOtherActions";
import { useSceneEditorActions } from "./creation/useSceneEditorActions";

export * from "./creation";

/**
 * useCreationActions — 创作中心聚合 Actions Hook
 *
 * 为保持向后兼容性，本 Hook 聚合了以下领域 Hook：
 * - useProjectActions: 项目 CRUD / 导入导出 / 迁移 / 写作目标
 * - useOutlineActions: 大纲 / 导航 / 目录与卷章结构
 * - useCardActions: 卡片 / 卡片类型 / 关系 / 卡片导入导出
 * - useInboxActions: 灵感收件箱 CRUD / 计数
 * - useSceneEditorActions: 场景编辑 / 保存 / 快照 / 校对 / 标注 / 实时订阅
 * - useCreationOtherActions: 搜索 / 替换计划 / 统计 / 写作会话 / 回收站 / 资源
 *
 * 新增组件推荐根据业务领域直接按需使用对应子 Hook，避免无意义的依赖耦合。
 */
export function useCreationActions() {
  const projectActions = useProjectActions();
  const outlineActions = useOutlineActions();
  const cardActions = useCardActions();
  const inboxActions = useInboxActions();
  const otherActions = useCreationOtherActions();
  const sceneActions = useSceneEditorActions({
    loadNavigation: outlineActions.loadNavigation,
    loadOutline: outlineActions.loadOutline,
    loadCards: cardActions.loadCards
  });

  return {
    ...projectActions,
    ...outlineActions,
    ...cardActions,
    ...inboxActions,
    ...otherActions,
    ...sceneActions
  };
}
