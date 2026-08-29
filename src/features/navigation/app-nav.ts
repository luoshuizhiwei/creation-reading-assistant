import type { AppScreen } from "@/stores/app-store";

/**
 * 应用级主导航（规格 §3.1）：项目 / 收件箱 / 资料阅读 / 设置。
 * 全局搜索不设导航项：顶部搜索框与 Ctrl+K 已覆盖（2026-08-29 用户反馈去重）。
 * 阅读统计已移出主导航，功能仍可从资料阅读页与阅读器入口访问。
 */
export type AppNavItem =
  | { kind: "screen"; screen: AppScreen; label: string; hint: string }
  | { kind: "action"; action: "global-search"; label: string; hint: string };

export const APP_NAV_ITEMS: AppNavItem[] = [
  { kind: "screen", screen: "projects", label: "项目", hint: "写作" },
  { kind: "screen", screen: "inbox", label: "收件箱", hint: "待处理" },
  { kind: "screen", screen: "library", label: "资料阅读", hint: "书库" },
  { kind: "screen", screen: "settings", label: "设置", hint: "偏好" }
];
