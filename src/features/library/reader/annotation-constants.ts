import type { HighlightColor } from "@/types/library";

/** 高亮色值表（半透明 fill） */
export const HIGHLIGHT_COLOR_FILL: Record<HighlightColor, string> = {
  yellow: "rgba(255, 235, 59, 0.4)",
  red: "rgba(244, 67, 54, 0.3)",
  green: "rgba(76, 175, 80, 0.3)",
  blue: "rgba(33, 150, 243, 0.3)",
  purple: "rgba(156, 39, 176, 0.3)"
};

/** HTML mark 标签 style 属性高亮背景 */
export const HIGHLIGHT_MARK_STYLES: Record<HighlightColor, string> = {
  yellow: `background:${HIGHLIGHT_COLOR_FILL.yellow}`,
  red: `background:${HIGHLIGHT_COLOR_FILL.red}`,
  green: `background:${HIGHLIGHT_COLOR_FILL.green}`,
  blue: `background:${HIGHLIGHT_COLOR_FILL.blue}`,
  purple: `background:${HIGHLIGHT_COLOR_FILL.purple}`
};
