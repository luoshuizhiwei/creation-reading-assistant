import type { ReaderBackground } from "@/types/library";

export interface ReaderBackgroundOption {
  value: ReaderBackground;
  label: string;
  /** src/styles.css 中已有的 .reader-bg-* 类，色卡的唯一颜色来源 */
  className: string;
}

export const READER_BACKGROUNDS: ReaderBackgroundOption[] = [
  { value: "white", label: "白纸", className: "reader-bg-white" },
  { value: "warm", label: "暖纸", className: "reader-bg-warm" },
  { value: "green", label: "护眼", className: "reader-bg-green" },
  { value: "night", label: "夜间", className: "reader-bg-night" },
  { value: "amber", label: "琥珀", className: "reader-bg-amber" },
  { value: "parchment", label: "羊皮纸", className: "reader-bg-parchment" },
  { value: "beans", label: "绿豆沙", className: "reader-bg-beans" }
];
