import { useCallback, useEffect, useState } from "react";
import { chooseFont, getInstalledFonts } from "@/services/reader-service";

/** 去掉字体文件扩展名，得到 font-family 名 */
export function fontFileNameToFamily(fileName: string): string {
  return fileName.replace(/\.[^.]+$/, "");
}

/**
 * 注入 @font-face 使导入的字体在本会话内可用。
 * 全应用唯一一份实现（此前 ReaderSection 与 ReaderSettingsPanel 各复制了一份）。
 */
export function injectFontFace(fontName: string, filePath: string): void {
  const styleId = `font-${fontName}`;
  let styleEl = document.getElementById(styleId) as HTMLStyleElement | null;
  if (!styleEl) {
    styleEl = document.createElement("style");
    styleEl.id = styleId;
    document.head.appendChild(styleEl);
  }
  styleEl.textContent = `@font-face { font-family: '${fontName}'; src: url('file://${filePath}'); }`;
}

/** 弹出系统文件对话框选择字体文件，成功后注入 @font-face 并返回字体名 */
export async function importFontFile(): Promise<string | null> {
  const result = await chooseFont();
  if (!result) return null;
  const fontName = fontFileNameToFamily(result.fileName);
  injectFontFace(fontName, result.filePath);
  return fontName;
}

/** 已安装字体列表 hook，导入后调用 refresh 重新拉取 */
export function useInstalledFonts() {
  const [fonts, setFonts] = useState<string[]>([]);

  const refresh = useCallback(async () => {
    try {
      setFonts(await getInstalledFonts());
    } catch {
      /* 字体列表不可用时静默降级为仅系统默认 */
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { fonts, refresh };
}
