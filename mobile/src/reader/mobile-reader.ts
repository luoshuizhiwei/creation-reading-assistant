import type { BookFormat } from "../../../src/types/library";
import { LARGE_MARKDOWN_PLAIN_TEXT_THRESHOLD, type MobileReaderDocument, type MobileReaderRenderOptions, type MobileReaderTocItem } from "./mobile-reader-types";
import { renderMarkdown } from "./mobile-reader-markdown";
import { parseEpubDocumentStructure, renderEpubDocument, extractEpubText } from "./mobile-reader-epubjs";
import { renderPlainText } from "./mobile-reader-txt";

// Barrel re-export：保持原导入路径向后兼容
export type { MobileReaderDocument, MobileReaderRenderOptions, MobileReaderTocItem } from "./mobile-reader-types";
export { renderMarkdown } from "./mobile-reader-markdown";
export {
  extractEpubText,
  parseEpubDocumentStructure,
  renderEpubDocument
} from "./mobile-reader-epubjs";
export {
  preparePlainTextSource,
  renderPlainText,
  renderPreparedPlainText
} from "./mobile-reader-txt";
export type { PreparedPlainTextSource } from "./mobile-reader-txt";
export {
  EPUB_INLINE_IMAGES_BY_DEFAULT,
  EPUB_INLINE_IMAGE_MAX_BYTES,
  TXT_MAX_RENDER_CHARS,
  TXT_VIRTUAL_CHAPTER_CHARS
} from "./mobile-reader-types";

export async function renderMobileDocument(format: BookFormat, content: string, title: string, options: MobileReaderRenderOptions = {}): Promise<MobileReaderDocument> {
  if (format === "md") {
    if (new Blob([content]).size > LARGE_MARKDOWN_PLAIN_TEXT_THRESHOLD) {
      const fallback = renderPlainText(content, title, options);
      return { ...fallback, format: "md" };
    }
    return renderMarkdown(content);
  }
  if (format === "epub") return parseEpubDocumentStructure(content, title);
  return renderPlainText(content, title, options);
}
