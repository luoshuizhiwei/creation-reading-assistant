import type { HighlightColor } from "@/types/library";

/** EPUB 高亮颜色：浮动工具栏与高亮列表共用。 */
export const HIGHLIGHT_COLORS: { value: HighlightColor; label: string; hex: string }[] = [
  { value: "yellow", label: "黄色", hex: "#fde047" },
  { value: "red", label: "红色", hex: "#fca5a5" },
  { value: "green", label: "绿色", hex: "#86efac" },
  { value: "blue", label: "蓝色", hex: "#93c5fd" },
  { value: "purple", label: "紫色", hex: "#d8b4fe" }
];

export function highlightHex(color: HighlightColor): string {
  return HIGHLIGHT_COLORS.find((c) => c.value === color)?.hex ?? "#fde047";
}
