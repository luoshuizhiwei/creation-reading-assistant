import type { InspirationItem, InspirationVariant } from "../../../src/types/inspiration";
import type { BookFormat, LibraryBook, ReaderBackground, ReadingProgress, ReadingSession } from "../../../src/types/library";

export type MobileSyncProvider = "local-desktop-lan" | "webdav";
export type MobileBook = LibraryBook & {
  localUri?: string;
  localFilePath?: string;
  coverDataUrl?: string;
  lastOpenedAt?: string;
};

export type MobileReadingProgress = ReadingProgress;
export type MobileReadingSession = ReadingSession;
export type MobileInspiration = InspirationItem;
export type MobileInspirationVariant = InspirationVariant;

export interface MobileNote {
  id: string;
  bookId?: string;
  inspirationId?: string;
  title: string;
  body: string;
  excerpt?: string;
  chapterTitle?: string;
  progressPercent?: number;
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
  passwordToken?: string;
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
  readerBackground: ReaderBackground;
}

export interface ImportedMobileBook {
  title: string;
  author?: string;
  format: BookFormat;
  originalFileName: string;
  content: string;
  size: number;
  contentHash: string;
  fileUri?: string;
}
