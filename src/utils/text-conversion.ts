/**
 * 繁简转换工具
 *
 * 使用 opencc-js 进行简体 ↔ 繁体转换。
 * 采用动态导入避免 opencc-js 词典影响初始加载体积。
 */

import type { TextConversionMode } from "@/types/library";

let _converter: ((text: string) => string) | null = null;
let _converterKey: TextConversionMode = "none";

/**
 * 获取指定方向的转换函数（懒加载 opencc-js）。
 */
export async function getConverter(
  mode: "s2t" | "t2s"
): Promise<(text: string) => string> {
  if (_converter && _converterKey === mode) return _converter;

  const OpenCC = await import("opencc-js");
  const from = mode === "s2t" ? "cn" : "tw";
  const to = mode === "s2t" ? "tw" : "cn";
  _converter = OpenCC.Converter({ from, to });
  _converterKey = mode;
  return _converter;
}

/**
 * 同步转换文本（如果转换器已加载）。
 * 若尚未加载或 mode 为 "none"，直接返回原文。
 */
export function convertTextSync(
  text: string,
  mode: TextConversionMode
): string {
  if (mode === "none" || !_converter || _converterKey !== mode) return text;
  return _converter(text);
}
