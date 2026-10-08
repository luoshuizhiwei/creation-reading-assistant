import type { AppScreen } from "@/stores/app-store";
import { appNavEntries } from "@/features/navigation/registry";

/**
 * 应用级导航（规格 §4.1 第 3 条：与 project-nav.ts / search-registry.ts 合并成一条注册表）。
 *
 * 批次 AY 起本文件不再持有数据：唯一数据源是 registry.ts 的 NAV_REGISTRY，这里只保留
 * DesktopFrame 实际消费的**对外形状**（kind/screen + label + hint）并派生成 APP_NAV_ITEMS。
 * 之所以不把 NavEntry 直接递给组件：那份表还带 keywords/tip/section 等左栏用不到的字段，
 * 窄形状让「注册表里多一个字段」不可能影响左栏——而左栏的五项、顺序、label 正被
 * desktop-frame-nav.test.tsx 逐字断言。
 *
 * 全局搜索不设导航项：顶部搜索框与 Ctrl+K 已覆盖（2026-08-29 用户反馈去重）。
 * 阅读统计已移出主导航，功能仍可从书库页与阅读器入口访问——它在 registry.ts 里照旧登记
 * （页面提示位与检索词要用），只是不在左栏名单里。
 */
export type AppNavItem = {
  kind: "screen";
  screen: AppScreen;
  label: string;
  hint: string;
};

export const APP_NAV_ITEMS: AppNavItem[] = appNavEntries().map((e) => ({
  kind: "screen" as const,
  screen: e.screen,
  label: e.label,
  hint: e.hint
}));
