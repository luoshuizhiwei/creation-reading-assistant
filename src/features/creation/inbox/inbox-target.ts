/**
 * 解析「转资料卡」的目标项目。
 * 优先级：projectId prop（限定目标）→ 用户当前选择 → 第一个可用项目。
 * 任何候选不存在或已移除时回退到第一个可用项目；全部为空返回 undefined。
 */
export function resolveTargetProject(
  projects: Array<{ id: string }>,
  projectId: string | undefined,
  currentTarget: string
): string | undefined {
  if (projectId && projects.some((project) => project.id === projectId)) return projectId;
  if (currentTarget && projects.some((project) => project.id === currentTarget)) return currentTarget;
  return projects[0]?.id;
}
