import type { MobileAppTheme } from "../../utils/mobile-helpers";

export const TAG_TYPE_LABELS = {
  book: "书籍",
  inspiration: "灵感",
  note: "笔记"
} as const;

export const APP_THEMES: Array<[MobileAppTheme, string, string]> = [
  ["system", "跟随系统", "随手机深浅色变化"],
  ["light", "浅色", "纸张感更强，适合白天"],
  ["dark", "深色", "夜间浏览设置页更安静"]
];
