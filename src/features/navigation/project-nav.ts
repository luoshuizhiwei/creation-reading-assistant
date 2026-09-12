/**
 * 项目级导航（规格 §3.2）：概览 / 写作 / 大纲 / 全书预览 / 设定卡 / 写作统计 / 版本历史。
 * 「背景」不作为一级项目导航，作为卡片页内部二级入口；项目内不再重复
 * 应用级「收件箱」入口，全局收件箱仍允许选择目标项目转资料卡。
 * 命名与阅读侧区分：「设定卡」专指角色/地点等创作卡片，「写作统计」
 * 专指项目内字数/会话统计——避免与大纲的任务卡板、全局阅读统计混称。
 * 「全书预览」是只读通读与打印入口，与「写作」的编辑态刻意分开。
 */
export type ProjectView = "overview" | "writing" | "outline" | "preview" | "cards" | "stats" | "history";

export const PROJECT_NAV_ITEMS: Array<{ view: ProjectView; label: string }> = [
  { view: "overview", label: "概览" },
  { view: "writing", label: "写作" },
  { view: "outline", label: "大纲" },
  { view: "preview", label: "全书预览" },
  { view: "cards", label: "设定卡" },
  { view: "stats", label: "写作统计" },
  { view: "history", label: "版本历史" }
];
