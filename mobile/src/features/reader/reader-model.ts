import type { MobileReaderDocument } from "../../reader/mobile-reader";
import type { MobileBook, MobileReaderSettings } from "../../types/mobile";

export type ReaderDrawerTab = "toc" | "search" | "highlights" | "bookmarks" | "notes" | "inspirations";
export type ReaderPanel = ReaderDrawerTab | "settings" | "book-info";
/** 阅读页二级弹出类型：左侧目录抽屉 / 底部设置 sheet / 底部主题 sheet / 进度气泡 / 灵感记录 sheet */
export type ReaderSheet = "toc-drawer" | "settings-sheet" | "theme-sheet" | "progress-popover" | "inspiration-sheet" | "ai-assist-sheet" | "ai-explain-sheet" | null;
export type ReaderPhase = "idle" | "opening" | "preview" | "loadingFullContent" | "ready" | "error";
export type ReaderErrorCode =
  | "missing_content"
  | "empty_content"
  | "parse_failed"
  | "read_failed"
  | "read_timeout"
  | "not_downloaded"
  | "unsupported_format";

export interface ReaderState {
  phase: ReaderPhase;
  bookId?: string;
  title?: string;
  visibleContent: string;
  fullContent?: string;
  errorCode?: ReaderErrorCode;
  errorMessage?: string;
}

export const idleReaderState: ReaderState = {
  phase: "idle",
  visibleContent: ""
};

export const defaultReaderSettings: MobileReaderSettings = {
  fontSize: 18,
  lineHeight: 1.85,
  pageMargin: 22,
  paragraphSpacing: 1.15,
  readerBackground: "warm",
  readerMode: "paged",
  fontWeight: "regular",
  tapZoneMode: "three-zone",
  showProgressBar: true,
  keepAwake: false,
  brightness: 100,
  immersiveMode: false,
  chineseTypography: false,
  eyeCareReminderMinutes: 30,
  showAIExplainButton: true,
  readingRhythmReminderMinutes: 30,
  readingRhythmReminderEnabled: true,
  highlightTTSSentence: true,
  ttsSyncToReader: true
};

export function escapeReaderHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function buildPlainTextFallbackDocument(book: MobileBook, content: string): MobileReaderDocument {
  const safeContent = content.trim() || `${book.title}\n\n正文为空。可以返回书架重新导入，或在“我的 / 同步”里重新下载正文。`;
  return {
    title: book.title,
    format: book.format,
    html: `<div class="reader-preformatted">${escapeReaderHtml(safeContent)}</div>`,
    plainText: safeContent,
    toc: [],
    wordCount: safeContent.replace(/\s/g, "").length
  };
}

export const emptyReaderDocument = (title: string, format: MobileBook["format"]): MobileReaderDocument => ({
  title,
  format,
  html: "",
  plainText: "",
  toc: [],
  wordCount: 0,
  currentTocIndex: 0,
  totalChapters: 0
});
