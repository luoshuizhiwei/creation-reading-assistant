import type { HighlightColor } from "@/types/library";

/**
 * 高亮色值表（半透明 fill）
 *
 * BD-111：正文高亮的墨色跟着纸走（.txt-hl { color: inherit }），文字可读性取决于
 * 「纸墨 vs 这层 tint」的对比，两个方向都要守住：
 *  · 深色纸（夜）：浅色墨压在偏亮的 tint 上——原先黄 0.4 抬亮本地到 4.06:1；
 *  · 暖色纸（琥珀/羊皮）：偏浅的暖墨（如 #5c4a2a）压在偏暗的 tint 上——紫 0.26 又掉到 4.39:1。
 * 结论：alpha 越低，tint 越贴回纸底（深纸更暗、浅纸更亮），两头的墨色都离 tint 更远。
 * 统一降到 0.18，仍是一条清晰的彩色高亮带，实测七种纸 × 五色最坏一档 ≥4.5。
 */
export const HIGHLIGHT_COLOR_FILL: Record<HighlightColor, string> = {
  yellow: "rgba(255, 235, 59, 0.18)",
  red: "rgba(244, 67, 54, 0.18)",
  green: "rgba(76, 175, 80, 0.18)",
  blue: "rgba(33, 150, 243, 0.18)",
  purple: "rgba(156, 39, 176, 0.18)"
};

/** HTML mark 标签 style 属性高亮背景 */
export const HIGHLIGHT_MARK_STYLES: Record<HighlightColor, string> = {
  yellow: `background:${HIGHLIGHT_COLOR_FILL.yellow}`,
  red: `background:${HIGHLIGHT_COLOR_FILL.red}`,
  green: `background:${HIGHLIGHT_COLOR_FILL.green}`,
  blue: `background:${HIGHLIGHT_COLOR_FILL.blue}`,
  purple: `background:${HIGHLIGHT_COLOR_FILL.purple}`
};
