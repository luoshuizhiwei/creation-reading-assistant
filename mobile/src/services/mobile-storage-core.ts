import type { BookFormat, LibraryBook } from "../../../src/types/library";
import { openMobileDatabase } from "../storage/mobile-database";
import type {
  MobileBook,
  MobileCategory,
  MobileHighlight,
  MobileInspiration,
  MobileNote,
  MobileReaderSettings,
  MobileReadingProgress,
  MobileReadingSession,
  MobileShelf,
  MobileSnapshot,
  MobileTag,
  SyncAccount
} from "../types/mobile";
import { defaultReaderSettings } from "../features/reader/reader-model";
import { addMobileLog } from "./mobile-logger";

const STORAGE_KEY = "creation-reading-assistant-mobile-snapshot";
const MIGRATION_KEY = "creation-reading-assistant-mobile-sqlite-migrated";
const DEVICE_KEY = "creation-reading-assistant-mobile-device-id";
const READER_SETTINGS_KEY = "creation-reading-assistant-mobile-reader-settings";
export const BOOK_CONTENT_STORAGE_KEY_PREFIX = "creation-reading-assistant-mobile-book-content:";

export type { MobileSnapshot } from "../types/mobile";

const SUPPORTED_MOBILE_BOOK_EXTENSIONS = new Set(["txt", "md", "markdown", "epub"]);
const SUPPORTED_MOBILE_BOOK_FORMATS = new Set<BookFormat>(["txt", "md", "epub"]);
const MOBILE_READER_PREVIEW_CHARS = 64 * 1024;

export const emptySnapshot = (): MobileSnapshot => ({
  inspirations: [],
  books: [],
  progress: [],
  sessions: [],
  notes: [],
  highlights: [],
  tags: [],
  categories: [],
  shelves: [],
  syncAccounts: [],
  updatedAt: new Date().toISOString()
});

export function nowIso(): string {
  return new Date().toISOString();
}

export function sanitizeSyncAccount(account: SyncAccount): SyncAccount {
  const { passwordToken: _passwordToken, password: _password, ...safeAccount } = account as SyncAccount & {
    passwordToken?: string;
    password?: string;
  };
  return safeAccount;
}

function hasLocalContentPath(book: MobileBook): boolean {
  return Boolean(book.localContentPath || book.localFilePath || book.filePath?.startsWith("books/"));
}

function limitReaderPreview(value?: string): string | undefined {
  const normalized = value?.trim();
  if (!normalized) return undefined;
  return normalized.slice(0, MOBILE_READER_PREVIEW_CHARS);
}

function normalizeMobileBook(book: MobileBook): MobileBook {
  const hasLocalContent = hasLocalContentPath(book);
  const origin = book.origin ?? (hasLocalContent ? "local_import" : "sync_placeholder");
  const contentStatus = book.contentStatus ?? (hasLocalContent ? "available" : "missing");
  const localContentPath = book.localContentPath ?? book.localFilePath ?? (book.filePath?.startsWith("books/") ? book.filePath : undefined);
  return {
    ...book,
    origin,
    contentStatus,
    localContentPath,
    readerPreview: limitReaderPreview(book.readerPreview)
  };
}

function isLocalOnlyBookPath(path?: string): boolean {
  return Boolean(path && (/^[a-z]+:\/\//i.test(path) || /^[A-Za-z]:[\\/]/.test(path) || path.startsWith("/") || path.startsWith("books/")));
}

export function sanitizeMobileBookForSync(book: MobileBook): MobileBook {
  const {
    readerPreview: _readerPreview,
    coverDataUrl: _coverDataUrl,
    localUri: _localUri,
    localFilePath: _localFilePath,
    localContentPath: _localContentPath,
    ...syncBook
  } = book;
  return {
    ...syncBook,
    filePath: isLocalOnlyBookPath(book.filePath) ? (book.originalFileName ?? `${book.id}.${book.format}`) : book.filePath
  };
}

export function getBaseFileName(fileName?: string): string {
  return (fileName ?? "").trim().split(/[\\/]/).pop() ?? "";
}

export function getMobileBookFileExtension(fileName?: string): string {
  const baseName = getBaseFileName(fileName);
  const dotIndex = baseName.lastIndexOf(".");
  return dotIndex > 0 ? baseName.slice(dotIndex + 1).toLowerCase() : "";
}

export function isSupportedMobileBookFileName(fileName?: string): boolean {
  const baseName = getBaseFileName(fileName);
  if (!baseName || baseName.startsWith(".")) return false;
  return SUPPORTED_MOBILE_BOOK_EXTENSIONS.has(getMobileBookFileExtension(baseName));
}

function isValidMobileBookRecord(book: MobileBook): boolean {
  if (!SUPPORTED_MOBILE_BOOK_FORMATS.has(book.format)) return false;
  const fileName = book.originalFileName ?? book.originalFilePath ?? book.originalPath ?? `${book.title}.${book.format}`;
  return isSupportedMobileBookFileName(fileName);
}

export function getStoredBookFileName(book: MobileBook): string {
  return isSupportedMobileBookFileName(book.originalFileName) && book.originalFileName ? book.originalFileName : `${book.id}.${book.format}`;
}

function cleanBookScopedData<T extends { bookId?: string }>(items: T[], validBookIds: Set<string>): T[] {
  return items.filter((item) => !item.bookId || validBookIds.has(item.bookId));
}

export function getMobileDeviceId(): string {
  const saved = localStorage.getItem(DEVICE_KEY);
  if (saved) return saved;
  const deviceId = `android-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
  localStorage.setItem(DEVICE_KEY, deviceId);
  return deviceId;
}

export function loadMobileReaderSettings(): MobileReaderSettings {
  const raw = localStorage.getItem(READER_SETTINGS_KEY);
  if (!raw) return defaultReaderSettings;
  try {
    const parsed = JSON.parse(raw) as Partial<MobileReaderSettings>;
    return {
      fontSize: typeof parsed.fontSize === "number" ? parsed.fontSize : defaultReaderSettings.fontSize,
      lineHeight: typeof parsed.lineHeight === "number" ? parsed.lineHeight : defaultReaderSettings.lineHeight,
      pageMargin: typeof parsed.pageMargin === "number" ? parsed.pageMargin : defaultReaderSettings.pageMargin,
      paragraphSpacing: typeof parsed.paragraphSpacing === "number" ? parsed.paragraphSpacing : defaultReaderSettings.paragraphSpacing,
      readerBackground: parsed.readerBackground ?? defaultReaderSettings.readerBackground,
      readerMode: parsed.readerMode === "scroll" || parsed.readerMode === "paged" ? parsed.readerMode : defaultReaderSettings.readerMode,
      fontWeight: parsed.fontWeight === "regular" || parsed.fontWeight === "bold" ? parsed.fontWeight : defaultReaderSettings.fontWeight,
      tapZoneMode: parsed.tapZoneMode === "three-zone" || parsed.tapZoneMode === "five-zone" ? parsed.tapZoneMode : defaultReaderSettings.tapZoneMode,
      showProgressBar: typeof parsed.showProgressBar === "boolean" ? parsed.showProgressBar : defaultReaderSettings.showProgressBar,
      keepAwake: typeof parsed.keepAwake === "boolean" ? parsed.keepAwake : defaultReaderSettings.keepAwake,
      brightness: typeof parsed.brightness === "number" ? parsed.brightness : defaultReaderSettings.brightness,
      immersiveMode: typeof parsed.immersiveMode === "boolean" ? parsed.immersiveMode : defaultReaderSettings.immersiveMode,
      chineseTypography: typeof parsed.chineseTypography === "boolean" ? parsed.chineseTypography : defaultReaderSettings.chineseTypography,
      pageTurnEffect: parsed.pageTurnEffect === "fade" || parsed.pageTurnEffect === "none" ? parsed.pageTurnEffect : defaultReaderSettings.pageTurnEffect,
      showReaderInfo: typeof parsed.showReaderInfo === "boolean" ? parsed.showReaderInfo : defaultReaderSettings.showReaderInfo,
      autoHideControlsSeconds: typeof parsed.autoHideControlsSeconds === "number"
        ? Math.min(10, Math.max(0, parsed.autoHideControlsSeconds))
        : defaultReaderSettings.autoHideControlsSeconds,
      eyeCareReminderMinutes: typeof parsed.eyeCareReminderMinutes === "number" ? parsed.eyeCareReminderMinutes : defaultReaderSettings.eyeCareReminderMinutes,
      showAIExplainButton: typeof parsed.showAIExplainButton === "boolean" ? parsed.showAIExplainButton : defaultReaderSettings.showAIExplainButton,
      readingRhythmReminderMinutes: typeof parsed.readingRhythmReminderMinutes === "number" ? parsed.readingRhythmReminderMinutes : defaultReaderSettings.readingRhythmReminderMinutes,
      readingRhythmReminderEnabled: typeof parsed.readingRhythmReminderEnabled === "boolean" ? parsed.readingRhythmReminderEnabled : defaultReaderSettings.readingRhythmReminderEnabled,
      highlightTTSSentence: typeof parsed.highlightTTSSentence === "boolean" ? parsed.highlightTTSSentence : defaultReaderSettings.highlightTTSSentence,
      ttsSyncToReader: typeof parsed.ttsSyncToReader === "boolean" ? parsed.ttsSyncToReader : defaultReaderSettings.ttsSyncToReader
    };
  } catch {
    return defaultReaderSettings;
  }
}

export function saveMobileReaderSettings(settings: MobileReaderSettings): void {
  try {
    localStorage.setItem(READER_SETTINGS_KEY, JSON.stringify(settings));
  } catch {
    // 存储失败时静默降级，避免阻塞阅读设置交互
  }
}

export function normalizeMobileSnapshot(input?: Partial<MobileSnapshot>): MobileSnapshot {
  const fallback = emptySnapshot();
  const books = Array.isArray(input?.books)
    ? input.books.filter((book): book is MobileBook => Boolean(book) && isValidMobileBookRecord(book)).map(normalizeMobileBook)
    : [];
  const validBookIds = new Set(books.map((book) => book.id));
  return {
    inspirations: Array.isArray(input?.inspirations) ? input.inspirations : [],
    books,
    progress: Array.isArray(input?.progress) ? input.progress.filter((item) => validBookIds.has(item.bookId)) : [],
    sessions: Array.isArray(input?.sessions) ? input.sessions.filter((item) => validBookIds.has(item.bookId)) : [],
    notes: Array.isArray(input?.notes) ? cleanBookScopedData(input.notes, validBookIds) : [],
    highlights: Array.isArray(input?.highlights) ? cleanBookScopedData(input.highlights, validBookIds) : [],
    tags: Array.isArray(input?.tags) ? input.tags : [],
    categories: Array.isArray(input?.categories) ? input.categories : [],
    shelves: Array.isArray(input?.shelves) ? input.shelves.map((shelf) => ({ ...shelf, bookIds: shelf.bookIds.filter((bookId) => validBookIds.has(bookId)) })) : [],
    syncAccounts: Array.isArray(input?.syncAccounts) ? input.syncAccounts.map((item) => sanitizeSyncAccount(item)) : [],
    updatedAt: typeof input?.updatedAt === "string" ? input.updatedAt : fallback.updatedAt
  };
}

async function readSnapshotFromLocalStorage(): Promise<MobileSnapshot> {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return emptySnapshot();
  try {
    return normalizeMobileSnapshot(JSON.parse(raw) as Partial<MobileSnapshot>);
  } catch {
    return emptySnapshot();
  }
}

async function readTablePayloads<T>(table: string): Promise<T[]> {
  const database = await openMobileDatabase().catch(() => undefined);
  if (!database) return [];
  const rows = await database.query<{ payload: string }>(`SELECT payload FROM ${table} WHERE deleted_at IS NULL ORDER BY updated_at DESC`);
  return rows
    .map((row) => {
      try {
        return JSON.parse(row.payload) as T;
      } catch {
        return undefined;
      }
    })
    .filter((row): row is T => Boolean(row));
}

async function upsertJson(table: string, idColumn: string, id: string, payload: object, extra: Record<string, string | undefined> = {}): Promise<void> {
  const database = await openMobileDatabase().catch(() => undefined);
  if (!database) return;
  const columns = [idColumn, "payload", "updated_at", "deleted_at", ...Object.keys(extra)];
  const values = [id, JSON.stringify(payload), "updatedAt" in payload ? String((payload as { updatedAt?: string }).updatedAt ?? nowIso()) : nowIso(), (payload as { deletedAt?: string }).deletedAt, ...Object.values(extra)];
  const placeholders = columns.map(() => "?").join(", ");
  const updates = columns
    .filter((column) => column !== idColumn)
    .map((column) => `${column}=excluded.${column}`)
    .join(", ");
  await database.run(`INSERT INTO ${table} (${columns.join(", ")}) VALUES (${placeholders}) ON CONFLICT(${idColumn}) DO UPDATE SET ${updates}`, values);
}

async function markMissingRowsDeleted(table: string, idColumn: string, activeIds: string[], deletedAt: string): Promise<void> {
  const database = await openMobileDatabase().catch(() => undefined);
  if (!database) return;
  if (!activeIds.length) {
    await database.run(`UPDATE ${table} SET deleted_at = ? WHERE deleted_at IS NULL`, [deletedAt]);
    return;
  }
  const placeholders = activeIds.map(() => "?").join(", ");
  await database.run(
    `UPDATE ${table} SET deleted_at = ? WHERE deleted_at IS NULL AND ${idColumn} NOT IN (${placeholders})`,
    [deletedAt, ...activeIds]
  );
}

export type MobileSnapshotSection = "books" | "inspirations" | "progress" | "sessions" | "notes" | "highlights" | "tags" | "categories" | "shelves" | "syncAccounts";

const ALL_SNAPSHOT_SECTIONS: MobileSnapshotSection[] = [
  "books", "inspirations", "progress", "sessions", "notes", "highlights", "tags", "categories", "shelves", "syncAccounts"
];

async function mirrorSnapshotToSQLite(snapshot: MobileSnapshot, selectedSections: MobileSnapshotSection[] = ALL_SNAPSHOT_SECTIONS): Promise<void> {
  const sections = new Set(selectedSections);
  const tasks: Array<() => Promise<void>> = [];
  if (sections.has("books")) tasks.push(
    ...snapshot.books.map((book) => () =>
      upsertJson("books", "id", book.id, book, {
        title: book.title,
        content_hash: book.contentHash
      })
    ),
    ...snapshot.books.map((book) => () =>
      upsertJson("book_files", "book_id", book.id, book, {
        file_name: book.originalFileName ?? `${book.id}.${book.format}`,
        format: book.format,
        content_hash: book.contentHash,
        size: String(book.size),
        local_uri: book.localUri
      })
    )
  );
  if (sections.has("inspirations")) tasks.push(
    ...snapshot.inspirations.map((item) => () =>
      upsertJson("inspirations", "id", item.id, item, {
        title: item.title,
        source_book_id: item.source?.bookId ?? item.sourceBookId
      })
    )
  );
  if (sections.has("progress")) tasks.push(...snapshot.progress.map((item) => () => upsertJson("reading_progress", "book_id", item.bookId, item)));
  if (sections.has("sessions")) tasks.push(
    ...snapshot.sessions.map((item) => () =>
      upsertJson("reading_sessions", "id", item.id, item, {
        book_id: item.bookId
      })
    )
  );
  if (sections.has("notes")) tasks.push(
    ...snapshot.notes.map((item) => () =>
      upsertJson("notes", "id", item.id, item, {
        book_id: item.bookId,
        inspiration_id: item.inspirationId
      })
    )
  );
  if (sections.has("highlights")) tasks.push(
    ...snapshot.highlights.map((item) => () =>
      upsertJson("highlights", "id", item.id, item, {
        book_id: item.bookId
      })
    )
  );
  if (sections.has("tags")) tasks.push(...snapshot.tags.map((item) => () => upsertJson("tags", "id", item.id, item, { name: item.name })));
  if (sections.has("categories")) tasks.push(...snapshot.categories.map((item) => () => upsertJson("categories", "id", item.id, item, { name: item.name })));
  if (sections.has("shelves")) tasks.push(...snapshot.shelves.map((item) => () => upsertJson("shelves", "id", item.id, item, { name: item.name })));
  if (sections.has("syncAccounts")) tasks.push(
    ...snapshot.syncAccounts.map((item) => () =>
      upsertJson("sync_accounts", "id", sanitizeSyncAccount(item).id, sanitizeSyncAccount(item), {
        provider: item.provider
      })
    )
  );
  // Capacitor SQLite 的单连接不适合 Promise.all 并发写入。按顺序执行可以避免
  // Android WebView 高频保存时出现 database locked / 部分成功、部分失败。
  for (const task of tasks) await task();

  const deletedAt = nowIso();
  const tombstoneTasks: Array<() => Promise<void>> = [];
  if (sections.has("books")) tombstoneTasks.push(
    () => markMissingRowsDeleted("books", "id", snapshot.books.map((book) => book.id), deletedAt),
    () => markMissingRowsDeleted("book_files", "book_id", snapshot.books.map((book) => book.id), deletedAt)
  );
  if (sections.has("inspirations")) tombstoneTasks.push(() => markMissingRowsDeleted("inspirations", "id", snapshot.inspirations.map((item) => item.id), deletedAt));
  if (sections.has("progress")) tombstoneTasks.push(() => markMissingRowsDeleted("reading_progress", "book_id", snapshot.progress.map((item) => item.bookId), deletedAt));
  if (sections.has("sessions")) tombstoneTasks.push(() => markMissingRowsDeleted("reading_sessions", "id", snapshot.sessions.map((item) => item.id), deletedAt));
  if (sections.has("notes")) tombstoneTasks.push(() => markMissingRowsDeleted("notes", "id", snapshot.notes.map((item) => item.id), deletedAt));
  if (sections.has("highlights")) tombstoneTasks.push(() => markMissingRowsDeleted("highlights", "id", snapshot.highlights.map((item) => item.id), deletedAt));
  if (sections.has("tags")) tombstoneTasks.push(() => markMissingRowsDeleted("tags", "id", snapshot.tags.map((item) => item.id), deletedAt));
  if (sections.has("categories")) tombstoneTasks.push(() => markMissingRowsDeleted("categories", "id", snapshot.categories.map((item) => item.id), deletedAt));
  if (sections.has("shelves")) tombstoneTasks.push(() => markMissingRowsDeleted("shelves", "id", snapshot.shelves.map((item) => item.id), deletedAt));
  if (sections.has("syncAccounts")) tombstoneTasks.push(() => markMissingRowsDeleted("sync_accounts", "id", snapshot.syncAccounts.map((item) => sanitizeSyncAccount(item).id), deletedAt));
  for (const task of tombstoneTasks) await task();
}

export async function migrateLegacySnapshot(): Promise<MobileSnapshot> {
  const snapshot = await readSnapshotFromLocalStorage();
  if (localStorage.getItem(MIGRATION_KEY) !== "done") {
    await mirrorSnapshotToSQLite(snapshot);
    localStorage.setItem(MIGRATION_KEY, "done");
  }
  return snapshot;
}

export async function initializeMobileStorage(): Promise<MobileSnapshot> {
  const snapshot = await migrateLegacySnapshot();
  const sqliteSnapshot = normalizeMobileSnapshot({
    inspirations: await readTablePayloads<MobileInspiration>("inspirations"),
    books: await readTablePayloads<MobileBook>("books"),
    progress: await readTablePayloads<MobileReadingProgress>("reading_progress"),
    sessions: await readTablePayloads<MobileReadingSession>("reading_sessions"),
    notes: await readTablePayloads<MobileNote>("notes"),
    highlights: await readTablePayloads<MobileHighlight>("highlights"),
    tags: await readTablePayloads<MobileTag>("tags"),
    categories: await readTablePayloads<MobileCategory>("categories"),
    shelves: await readTablePayloads<MobileShelf>("shelves"),
    syncAccounts: await readTablePayloads<SyncAccount>("sync_accounts"),
    updatedAt: snapshot.updatedAt
  });
  if (
    sqliteSnapshot.inspirations.length ||
    sqliteSnapshot.books.length ||
    sqliteSnapshot.progress.length ||
    sqliteSnapshot.sessions.length ||
    sqliteSnapshot.notes.length ||
    sqliteSnapshot.highlights.length ||
    sqliteSnapshot.tags.length ||
    sqliteSnapshot.categories.length ||
    sqliteSnapshot.shelves.length ||
    sqliteSnapshot.syncAccounts.length
  ) {
    return {
      ...snapshot,
      ...sqliteSnapshot,
      syncAccounts: sqliteSnapshot.syncAccounts.length ? sqliteSnapshot.syncAccounts : snapshot.syncAccounts,
      tags: sqliteSnapshot.tags.length ? sqliteSnapshot.tags : snapshot.tags,
      categories: sqliteSnapshot.categories.length ? sqliteSnapshot.categories : snapshot.categories,
      shelves: sqliteSnapshot.shelves.length ? sqliteSnapshot.shelves : snapshot.shelves
    };
  }
  return snapshot;
}

export async function loadMobileSnapshot(): Promise<MobileSnapshot> {
  return initializeMobileStorage();
}

let snapshotWriteQueue: Promise<void> = Promise.resolve();

export async function saveMobileSnapshot(snapshot: MobileSnapshot, selectedSections?: MobileSnapshotSection[]): Promise<void> {
  const normalized = normalizeMobileSnapshot({ ...snapshot, updatedAt: nowIso() });
  const write = snapshotWriteQueue.then(async () => {
    try {
      await mirrorSnapshotToSQLite(normalized, selectedSections);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      addMobileLog("error", "本地存储", detail, { code: "SQLITE_WRITE_FAILED" });
      throw error;
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
    } catch {
      // SQLite is the source of truth after migration. If WebView localStorage is full,
      // do not roll back a successful import or sync write.
    }
  });
  snapshotWriteQueue = write.catch(() => undefined);
  return write;
}

// 保留 LibraryBook 导入以供 reading 子模块类型兼容（避免循环依赖时类型丢失）
export type { LibraryBook };
