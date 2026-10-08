import { projectViewEntries } from "@/features/navigation/registry";

/**
 * 项目级导航（规格 §4.1 第 3 条：与 app-nav.ts / search-registry.ts 合并成一条注册表）。
 *
 * 批次 AY 起本文件不再持有数据：唯一数据源是 registry.ts 的 NAV_REGISTRY，这里派生出
 * PROJECT_NAV_ITEMS 并原样 re-export ProjectView（外部 import 路径与形状都不变）。
 *
 * 概览 / 写作 / 大纲 / 全书预览 / 设定卡 / 写作统计 / 版本历史。
 * 「背景」不作为一级项目导航，作为卡片页内部二级入口；项目内不再重复
 * 应用级「收件箱」入口，全局收件箱仍允许选择目标项目转资料卡。
 * 命名与阅读侧区分：「设定卡」专指角色/地点等创作卡片，「写作统计」
 * 专指项目内字数/会话统计——避免与大纲的任务卡板、全局阅读统计混称。
 * 「全书预览」是只读通读与打印入口，与「写作」的编辑态刻意分开。
 */
export type { ProjectView } from "@/features/navigation/registry";
import type { ProjectView } from "@/features/navigation/registry";

export const PROJECT_NAV_ITEMS: Array<{ view: ProjectView; label: string }> = projectViewEntries().map((e) => ({
  view: e.view,
  label: e.label
}));
