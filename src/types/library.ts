import type { ID, ISODateString } from "./common";

export type BookFormat = "txt" | "md" | "epub";
export type AppThemeMode = "light" | "dark" | "system";
export type ReaderBackground = "white" | "warm" | "green" | "night" | "amber" | "parchment" | "beans";
export type EpubStyleMode = "publisher" | "unified";

export interface EpubTocItem {
  id: ID;
  label: string;
  href: string;
  level: number;
}

export interface EpubBookMetadata {
  author?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverPath?: string;
  toc?: EpubTocItem[];
  searchIndexedAt?: ISODateString;
  searchIndexPath?: string;
}

export interface EpubSearchIndexItem {
  id: ID;
  title: string;
  href: string;
  text: string;
}

export interface EpubSearchIndex {
  version: 1;
  bookId: ID;
  updatedAt: ISODateString;
  items: EpubSearchIndexItem[];
}

/** 高亮颜色 */
export type HighlightColor = "yellow" | "red" | "green" | "blue" | "purple";

/** 跨内核稳定阅读锚点。旧 ReadingLocation 字段继续保留用于兼容。 */
export interface ReaderLocatorV2 {
  version: 2;
  bookId: ID;
  format: "txt" | "markdown" | "epub";
  progression?: number;
  chapterId?: string;
  href?: string;
  fragment?: string;
  textOffset?: number;
  paragraphIndex?: number;
  epub?: {
    cfi?: string;
    position?: number;
    totalProgression?: number;
  };
  updatedAt: number;
}

/** 高亮标注项 */
export interface HighlightItem {
  id: ID;
  bookId: ID;
  /** EPUB: CFI range; TXT/MD: 字符偏移 */
  cfiRange?: string;
  charOffset?: number;
  charLength?: number;
  text: string;           // 高亮的文本内容
  color: HighlightColor;
  note?: string;          // 可选批注
  chapterTitle?: string;  // 所在章节标题
  progressPercent?: number; // 阅读进度百分比，用于跳回原文
  /** 新阅读内核使用的稳定锚点；旧字段仍双写以兼容历史版本。 */
  locator?: ReaderLocatorV2;
  createdAt: ISODateString;
  updatedAt: ISODateString;
}

/** 书签项 */
export interface BookmarkItem {
  id: ID;
  bookId: ID;
  label: string;          // 书签名称（可选）
  /** EPUB 定位 */
  cfi?: string;
  href?: string;
  /** TXT/MD 定位 */
  scrollTop?: number;
  progressPercent?: number;
  chapterTitle?: string;
  createdAt: ISODateString;
}

export type ReadingLocationMode = "scroll" | "text-anchor" | "epub-cfi" | "page";
export type ReadingLocationPrecision = "exact" | "estimated";

export interface LibraryBook {
  id: ID;
  title: string;
  filePath: string;
  originalPath?: string;
  originalFileName?: string;
  originalFilePath?: string;
  format: BookFormat;
  importedAt: ISODateString;
  updatedAt: ISODateString;
  size: number;
  contentHash?: string;
  duplicateIndex?: number;
  importLabel?: string;
  author?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverPath?: string;
  epub?: EpubBookMetadata;
  revision: number;
  deviceId: ID;
  deletedAt?: ISODateString;
}

export interface ReadingLocation {
  format: BookFormat;
  mode: ReadingLocationMode;
  progressPercent: number;
  precision: ReadingLocationPrecision;
  scroll?: {
    scrollTop: number;
    scrollHeight: number;
    containerHeight: number;
  };
  text?: {
    charOffset?: number;
    chapterRef?: string;
    headingPath?: string[];
    anchorText?: string;
  };
  epub?: {
    cfi?: string;
    href?: string;
    spineIndex?: number;
    chapterRef?: string;
  };
  page?: {
    pageIndex: number;
    pageCount?: number;
  };
  sourceVersion?: {
    fileSize?: number;
    modifiedAt?: ISODateString;
    contentHash?: string;
  };
  updatedAt: ISODateString;
}

export interface ReadingProgress {
  bookId: ID;
  filePath: string;
  format: BookFormat;
  currentLocation: ReadingLocation;
  progressPercent: number;
  lastReadAt: ISODateString;
  totalReadingTimeMs: number;
  lastSessionId?: ID;
  completionState: "unread" | "reading" | "completed";
  completedAt?: ISODateString;
  revision: number;
  deviceId: ID;
  deletedAt?: ISODateString;
  updatedAt: ISODateString;
}

export interface ReaderTrackingSettings {
  trackReadingSessions: boolean;
  idleTimeoutMs: number;
  progressSaveIntervalMs: number;
  sessionHeartbeatMs: number;
  sessionPersistIntervalMs: number;
  maxPausedBeforeNewSessionMs: number;
  endSessionOnBookSwitch: boolean;
  recordRecentReads: boolean;
  showReadingStatsCards: boolean;
}

export interface ReaderPreset {
  id: string;
  name: string;
  fontSize: number;
  lineHeight: number;
  pageMargin: number;
  paragraphSpacing: number;
  letterSpacing: number;
  readerBackground: ReaderBackground;
  fontFamily?: string;
}

export type TextConversionMode = "none" | "s2t" | "t2s";

export interface ReaderSettings {
  fontSize: number;
  lineHeight: number;
  paragraphSpacing: number;
  letterSpacing: number;
  pageMargin: number;
  appTheme: AppThemeMode;
  readerBackground: ReaderBackground;
  epubStyleMode: EpubStyleMode;
  /** @deprecated Kept only to normalize older settings files. */
  theme?: "light" | "dark";
  textConversion: TextConversionMode;
  restoreLastPosition: boolean;
  readingMode: "scroll";
  tracking: ReaderTrackingSettings;
  presets?: ReaderPreset[];
  fontFamily?: string;
}

export interface ReaderBookPayload {
  book: LibraryBook;
  content: string;
  progress?: ReadingProgress;
  settings: ReaderSettings;
}

export interface ReaderEpubPayload {
  book: LibraryBook;
  epubUrl: string;
  toc: EpubTocItem[];
  targetHref?: string;
  progress?: ReadingProgress;
  settings: ReaderSettings;
}

export interface SaveProgressInput {
  bookId: ID;
  location: ReadingLocation;
}

export interface StartReadingSessionInput {
  bookId: ID;
  location: ReadingLocation;
  source: "manualOpen" | "restore" | "switchBook";
}

export interface UpdateReadingSessionInput {
  sessionId: ID;
  location?: ReadingLocation;
  activeDeltaMs?: number;
  idleDeltaMs?: number;
  status?: "active" | "paused";
  pauseReason?: "idle" | "window-blur" | "leave-reader";
}

export interface EndReadingSessionInput {
  sessionId: ID;
  location?: ReadingLocation;
  activeDeltaMs?: number;
  idleDeltaMs?: number;
  endReason: "leave-reader" | "switch-book" | "window-close" | "idle-timeout" | "crash-recovered";
}

export interface GetReadingSessionsInput {
  bookId?: ID;
  limit?: number;
  includeActive?: boolean;
}

export interface RecoverReadingSessionsResult {
  recoveredCount: number;
  sessions: ReadingSession[];
}

export interface ReadingSession {
  id: ID;
  bookId: ID;
  filePath: string;
  format: BookFormat;
  startAt: ISODateString;
  endAt?: ISODateString;
  durationMs: number;
  activeDurationMs: number;
  idleDurationMs: number;
  wallDurationMs: number;
  startLocation: ReadingLocation;
  endLocation?: ReadingLocation;
  dateKey: string;
  dailyActiveMs?: Record<string, number>;
  status: "active" | "paused" | "ended" | "recovered";
  source: "manualOpen" | "restore" | "switchBook";
  pauseReason?: "idle" | "window-blur" | "leave-reader";
  endReason?: "leave-reader" | "switch-book" | "window-close" | "idle-timeout" | "crash-recovered";
  revision: number;
  deviceId: ID;
  deletedAt?: ISODateString;
  createdAt: ISODateString;
  updatedAt: ISODateString;
  lastPersistAt: ISODateString;
}

export interface ReadingStatsSummary {
  todayDurationMs: number;
  last7DaysDurationMs: number;
  last30DaysDurationMs: number;
  totalDurationMs: number;
  byBook: Array<{
    bookId: ID;
    title: string;
    format: BookFormat;
    totalDurationMs: number;
    lastReadAt?: ISODateString;
    progressPercent?: number;
  }>;
  recentBooks: Array<{
    bookId: ID;
    title: string;
    format: BookFormat;
    lastReadAt: ISODateString;
    progressPercent: number;
    totalDurationMs: number;
  }>;
  recentSessions: ReadingSession[];
  readingDaysCount: number;
  averageSessionDurationMs: number;
  daily: Array<{
    dateKey: string;
    durationMs: number;
  }>;
}

export interface ReaderActivityState {
  isReaderPageActive: boolean;
  isWindowFocused: boolean;
  isUserActive: boolean;
  lastInteractionAt: number;
  idleSince?: number;
  activeSessionId?: ID;
  isTracking: boolean;
  lastTickAt?: number;
  lastPersistAt?: number;
  lastLocation?: ReadingLocation;
}
