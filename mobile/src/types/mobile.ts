import type { InspirationItem, InspirationVariant } from "../../../src/types/inspiration";
import type { BookFormat, HighlightColor, HighlightItem, LibraryBook, ReaderBackground, ReadingProgress, ReadingSession } from "../../../src/types/library";

export type MobileSyncProvider = "local-desktop-lan" | "webdav";
export type BookOrigin = "local_import" | "sync_placeholder" | "sync_downloaded";
export type BookContentStatus = "available" | "missing" | "downloading" | "failed";
export type MobileBook = LibraryBook & {
  localUri?: string;
  localFilePath?: string;
  localContentPath?: string;
  origin?: BookOrigin;
  contentStatus?: BookContentStatus;
  coverDataUrl?: string;
  lastOpenedAt?: string;
  categoryIds?: string[];
  tagNames?: string[];
  readerPreview?: string;
};

export type MobileReadingProgress = ReadingProgress;
export type MobileReadingSession = ReadingSession;
export type MobileInspiration = InspirationItem;
export type MobileInspirationVariant = InspirationVariant;
export type MobileHighlightColor = HighlightColor;
export type MobileHighlight = HighlightItem;

/** 移动端阅读背景主题：继承桌面值并扩展移动端专属护眼主题 */
export type MobileReaderBackground = ReaderBackground | "warm-yellow" | "green-bean" | "oled-black";

export interface MobileNote {
  id: string;
  bookId?: string;
  inspirationId?: string;
  title: string;
  body: string;
  excerpt?: string;
  chapterTitle?: string;
  progressPercent?: number;
  kind?: "bookmark" | "note";
  createdAt: string;
  updatedAt: string;
  revision: number;
  deviceId: string;
  deletedAt?: string;
}

export interface MobileTag {
  id: string;
  name: string;
  color?: string;
  type: "book" | "inspiration" | "note";
  createdAt: string;
  updatedAt: string;
  revision: number;
  deviceId: string;
  deletedAt?: string;
}

export interface MobileCategory {
  id: string;
  name: string;
  parentId?: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
  deviceId: string;
  deletedAt?: string;
}

export interface MobileShelf {
  id: string;
  name: string;
  bookIds: string[];
  createdAt: string;
  updatedAt: string;
  revision: number;
  deviceId: string;
  deletedAt?: string;
}

export interface SyncAccount {
  id: string;
  provider: MobileSyncProvider;
  name: string;
  endpoint?: string;
  username?: string;
  enabled: boolean;
  lastSyncAt?: string;
  createdAt: string;
  updatedAt: string;
  revision: number;
  deviceId: string;
  deletedAt?: string;
}

export interface WebDavSyncState {
  accountId: string;
  remoteRoot: ".creation-reading-assistant/";
  manifestPath: ".creation-reading-assistant/manifest.json";
  lastRemoteRevision?: number;
  lastUploadAt?: string;
  lastDownloadAt?: string;
  lastError?: string;
}

export interface MobileSnapshot {
  inspirations: MobileInspiration[];
  books: MobileBook[];
  progress: MobileReadingProgress[];
  sessions: MobileReadingSession[];
  notes: MobileNote[];
  highlights: MobileHighlight[];
  tags: MobileTag[];
  categories: MobileCategory[];
  shelves: MobileShelf[];
  syncAccounts: SyncAccount[];
  updatedAt: string;
}

export interface MobileReaderSettings {
  fontSize: number;
  lineHeight: number;
  pageMargin: number;
  paragraphSpacing: number;
  readerBackground: MobileReaderBackground;
  readerMode: "scroll" | "paged";
  fontWeight: "regular" | "bold";
  tapZoneMode: "three-zone" | "five-zone";
  showProgressBar: boolean;
  keepAwake: boolean;
  brightness: number;
  /** 沉浸模式：正文单击切换控件显示/隐藏，无交互 3.5s 后自动淡出 */
  immersiveMode?: boolean;
  /** 中文排版优化：首行缩进 2 字符、标点悬挂微调 */
  chineseTypography?: boolean;
  /** 护眼提醒间隔（分钟），0 表示关闭 */
  eyeCareReminderMinutes?: number;
  /** 是否显示选中文字工具栏里的“AI 解读”按钮 */
  showAIExplainButton?: boolean;
  /** 阅读节奏提示间隔（分钟），0 表示关闭 */
  readingRhythmReminderMinutes?: number;
  /** 是否开启阅读节奏提示 */
  readingRhythmReminderEnabled?: boolean;
  /** TTS 朗读时是否在正文中高亮当前句/段 */
  highlightTTSSentence?: boolean;
  /** 是否在 TTS 与文字阅读之间同步进度 */
  ttsSyncToReader?: boolean;
}

export interface ImportedMobileBook {
  title: string;
  author?: string;
  format: BookFormat;
  originalFileName: string;
  content: string;
  size: number;
  contentHash: string;
  readerPreview?: string;
  fileUri?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverDataUrl?: string;
  epubToc?: Array<{ id: string; title: string; href?: string; level: number; index?: number }>;
  epubTotalChapters?: number;
}

/** 扩展共享 ReadingLocation，增加段落内/页面内精确偏移，不修改桌面端类型文件 */
declare module "../../../src/types/library" {
  interface ReadingLocation {
    /** 当前段落内的滚动比例（0-1），滚动模式精确恢复用 */
    scrollRatioInParagraph?: number;
    /** 当前段落序号 */
    paragraphIndex?: number;
    /** 页面内元素偏移比例（0-1），分页模式精确恢复用 */
    pageRatio?: number;
  }
}
