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

// ---------------------------------------------------------------------------
// 资料摘录 (Excerpt) — 桌面端阅读器收敛为「资料阅读与摘录」后的数据结构。
// 直接摘录到全局收件箱或项目资料卡，不再强制先创建旧灵感。
// ---------------------------------------------------------------------------

/**
 * 资料摘录来源快照。跨 TXT / Markdown / EPUB 三种格式统一描述，
 * 保留每种格式可用的定位字段（href / cfi / charOffset 等），
 * 便于集成端映射到 InboxCreateCommand 或 CardCreateCommand.content。
 */
export interface ExcerptSourceSnapshot {
  bookId: ID;
  bookTitle: string;
  bookAuthor?: string;
  format: BookFormat;
  /** 当前所在章节标题（EPUB 取 toc 匹配，TXT/MD 取当前 heading） */
  chapterTitle?: string;
  /** 0-1 之间的进度比例 */
  progressPercent?: number;
  /** 人类可读的位置标签，如「第三章 · 42% 附近」 */
  locationLabel: string;
  /** 已选中的正文文本（已 trim 且截断到上限） */
  excerpt: string;
  /** EPUB / Markdown 章节 href */
  href?: string;
  /** EPUB CFI 定位 */
  cfi?: string;
  /** TXT/MD 在正文中的字符偏移 */
  charOffset?: number;
  charLength?: number;
  /** TXT/MD 滚动位置，兼容旧 ReadingLocation.scroll */
  scrollTop?: number;
  createdAt: ISODateString;
}

export type ExcerptTargetKind = "inbox" | "projectCard";

/** 摘录目标：全局收件箱或某个项目的资料卡。 */
export type ExcerptTarget =
  | { kind: "inbox" }
  | { kind: "projectCard"; projectId: string };

/** 摘录操作结果。success=false 时必须给出 error，不得假成功。 */
export interface ExcerptResult {
  success: boolean;
  /** 成功时返回创建的收件箱条目 ID 或资料卡 ID */
  itemId?: string;
  /** 失败原因 */
  error?: string;
}

/**
 * 资料摘录目的地接口。由集成端注入实际 IPC 实现；
 * library 内只依赖此接口，不直接调用共享 IPC。
 * 测试中注入 mock 实现即可验证摘录流程。
 */
export interface ReaderExcerptDestination {
  /** 列出可选的目标项目（供摘录选择器渲染）。 */
  listProjects(): Promise<Array<{ id: string; title: string }>>;
  /** 摘录到全局收件箱：保存完整选文和来源。 */
  saveToInbox(source: ExcerptSourceSnapshot): Promise<ExcerptResult>;
  /** 摘录为指定项目的资料卡：保存 bookId、格式、章节、进度、href/cfi/offset 等定位。 */
  saveToProjectCard(projectId: string, source: ExcerptSourceSnapshot): Promise<ExcerptResult>;
}
