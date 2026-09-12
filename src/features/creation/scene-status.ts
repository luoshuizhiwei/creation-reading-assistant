/**
 * 场景状态展示映射（单一来源）。
 *
 * `SceneStatus` 取自 `scenes.scene_status`，与 `chapters.status` 的章节工作流状态是两种
 * 不同语义：前者描述「这一场写到什么程度」，后者描述「这一章在项目工作流里的位置」。
 * 两者不可互相复用，也不可由一方推导另一方。
 *
 * 大纲页（场景任务卡）、卡片看板（按状态分组）与统计页（场景状态分布）共用这里的标签，
 * 避免各自复制一份而在后续改动中漂移。
 */

import type { SceneStatus } from "@/types/creation";

/** 场景状态的展示顺序：待规划 → 起草中 → 修订中 → 已完成。 */
export const SCENE_STATUS_ORDER: SceneStatus[] = ["planned", "drafting", "revising", "done"];

export const SCENE_STATUS_LABELS: Record<SceneStatus, string> = {
  planned: "待规划",
  drafting: "起草中",
  revising: "修订中",
  done: "已完成"
};

/** 供 `Select` 直接消费的选项；与 `SceneStatus` 联合类型保持同步。 */
export const SCENE_STATUS_OPTIONS: Array<{ value: SceneStatus; label: string }> = SCENE_STATUS_ORDER.map(
  (value) => ({ value, label: SCENE_STATUS_LABELS[value] })
);

/** 是否为受支持的场景状态（用于收窄来自数据库/IPC 的字符串）。 */
export function isSceneStatus(value: unknown): value is SceneStatus {
  return typeof value === "string" && (SCENE_STATUS_ORDER as string[]).includes(value);
}

/**
 * 把任意来源的状态值映射为展示标签。
 *
 * 空值显示为「未设置」；未知值原样返回而不是兜底成「待规划」，这样数据异常会在界面上
 * 可见，而不是被静默掩盖。
 */
export function sceneStatusLabel(status: string | null | undefined): string {
  if (status === null || status === undefined || status === "") return "未设置";
  return SCENE_STATUS_LABELS[status as SceneStatus] ?? status;
}
