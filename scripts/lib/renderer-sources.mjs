import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..", "..");

/**
 * 阅读器渲染层实现模块清单。
 *
 * 与 scripts/lib/main-process-sources.mjs 同一约定：守卫脚本要校验的是「阅读器必须具备
 * 某项实现」，而不是「某段代码必须写在某个页面文件里」。EPUB / TXT·Markdown 阅读页已按
 * 职责拆分为引擎层与渲染层（纯移动式重构），因此文本断言必须覆盖整个模块集合，
 * 否则任何一次正常拆分都会让守卫误报。新增渲染模块时请同步登记到此处。
 */
export const READER_RENDERER_FILES = [
  "src/features/library/EpubReaderPage.tsx",
  "src/features/library/epub-reader/epub-engine.ts",
  "src/features/library/epub-reader/EpubSidePanel.tsx",
  "src/features/library/reader/TxtMarkdownReader.tsx",
  "src/features/library/reader/txt-markdown-render.tsx",
  "src/features/library/ReaderSidePanel.tsx"
];

export function listReaderRendererFiles() {
  return READER_RENDERER_FILES.map((file) => path.join(root, file));
}

export function readReaderRenderers() {
  return listReaderRendererFiles()
    .map((file) => readFileSync(file, "utf8"))
    .join("\n");
}
