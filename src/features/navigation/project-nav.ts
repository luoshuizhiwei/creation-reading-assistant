/**
 * 项目级导航（规格 §3.2）：概览 / 写作 / 大纲 / 卡片 / 统计 / 版本历史。
 * 「背景」不作为一级项目导航，作为卡片页内部二级入口；项目内不再重复
 * 应用级「收件箱」入口，全局收件箱仍允许选择目标项目转资料卡。
 */
export type ProjectView = "overview" | "writing" | "outline" | "cards" | "stats" | "history";

export const PROJECT_NAV_ITEMS: Array<{ view: ProjectView; label: string }> = [
  { view: "overview", label: "概览" },
  { view: "writing", label: "写作" },
  { view: "outline", label: "大纲" },
  { view: "cards", label: "卡片" },
  { view: "stats", label: "统计" },
  { view: "history", label: "版本历史" }
];
