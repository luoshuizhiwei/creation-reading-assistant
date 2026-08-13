import type { AppScreen } from "@/stores/app-store";

/**
 * 应用级主导航（规格 §3.1）：项目 / 收件箱 / 资料阅读 / 全局搜索 / 设置。
 * 全局搜索是动作（打开现有 SearchPanel 覆盖层），不是独立 AppScreen；
 * 用判别联合避免伪造一个没有独立页面的屏幕。阅读统计已移出主导航，
 * 功能仍可从资料阅读页与阅读器入口访问。
 */
export type AppNavItem =
  | { kind: "screen"; screen: AppScreen; label: string; hint: string }
  | { kind: "action"; action: "global-search"; label: string; hint: string };

export const APP_NAV_ITEMS: AppNavItem[] = [
  { kind: "screen", screen: "projects", label: "项目", hint: "写作" },
  { kind: "screen", screen: "inbox", label: "收件箱", hint: "待处理" },
  { kind: "screen", screen: "library", label: "资料阅读", hint: "书库" },
  { kind: "action", action: "global-search", label: "全局搜索", hint: "全文" },
  { kind: "screen", screen: "settings", label: "设置", hint: "偏好" }
];
